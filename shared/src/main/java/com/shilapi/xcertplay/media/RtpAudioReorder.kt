package com.shilapi.xcertplay.media

/**
 * Puts one RTP audio stream back in sequence order and reports packets that never arrived, so the
 * renderer can fill the hole with silence instead of shortening the music. Out-of-order packets wait
 * for at most [holdPackets] later packets or [holdNanos]; a jump larger than [resyncPackets] is a new
 * run of the stream rather than loss and passes through without concealment. Not thread-safe.
 */
class RtpAudioReorder<T>(
    private val holdPackets: Int = 4,
    private val holdNanos: Long = 120_000_000L,
    private val maxConcealSamples: Int = 48_000 / 4,
    private val resyncPackets: Int = 64,
) {
    sealed interface Out<out T>
    data class Packet<T>(val item: T, val timestamp: Int) : Out<T>
    /** [samples] of silence belong immediately before the packet stamped [beforeTimestamp]. */
    data class Gap(val missingPackets: Int, val samples: Int, val beforeTimestamp: Int) : Out<Nothing>

    private class Held<T>(val sequence: Int, val timestamp: Int, val item: T, val arrivedNs: Long)

    private val held = ArrayList<Held<T>>(holdPackets + 1)
    private var expected = -1
    private var lastSequence = -1
    private var lastTimestamp = 0
    private var samplesPerPacket = 0

    var lostPackets = 0L
        private set
    var latePackets = 0L
        private set
    var reorderedPackets = 0L
        private set
    var concealedSamples = 0L
        private set
    var resyncs = 0L
        private set

    fun push(sequence: Int, timestamp: Int, item: T, nowNs: Long): List<Out<T>> {
        val out = ArrayList<Out<T>>(2)
        val seq = sequence and 0xffff
        if (expected < 0) {
            release(seq, timestamp, item, out)
            return out
        }
        val delta = seqDelta(seq, expected)
        when {
            delta < -resyncPackets || delta > resyncPackets -> {
                resyncs++
                while (held.isNotEmpty()) held.removeAt(0).let { release(it.sequence, it.timestamp, it.item, out) }
                lastSequence = -1
                release(seq, timestamp, item, out)
                return out
            }
            delta < 0 -> latePackets++
            delta == 0 -> {
                release(seq, timestamp, item, out)
                releaseContiguous(out)
            }
            held.none { it.sequence == seq } -> {
                reorderedPackets++
                val index = held.indexOfFirst { seqDelta(it.sequence, expected) > delta }
                held.add(if (index < 0) held.size else index, Held(seq, timestamp, item, nowNs))
                while (held.size > holdPackets) releaseEarliestAcrossGap(out)
            }
        }
        out += flushStale(nowNs)
        return out
    }

    /** Gives up on missing packets that held packets have waited for longer than [holdNanos]. */
    fun flushStale(nowNs: Long): List<Out<T>> {
        if (held.isEmpty() || nowNs - held[0].arrivedNs < holdNanos) return emptyList()
        val out = ArrayList<Out<T>>(2)
        while (held.isNotEmpty() && nowNs - held[0].arrivedNs >= holdNanos) releaseEarliestAcrossGap(out)
        return out
    }

    private fun releaseEarliestAcrossGap(out: MutableList<Out<T>>) {
        val first = held.removeAt(0)
        val missing = seqDelta(first.sequence, expected)
        lostPackets += missing
        val perPacket = samplesPerPacket
        if (perPacket > 0) {
            val measured = first.timestamp - lastTimestamp - perPacket
            val samples = (if (measured in 1..missing * perPacket * 2) measured else missing * perPacket)
                .coerceAtMost(maxConcealSamples)
            if (samples > 0) {
                concealedSamples += samples
                out += Gap(missing, samples, first.timestamp)
            }
        }
        lastSequence = -1
        release(first.sequence, first.timestamp, first.item, out)
        releaseContiguous(out)
    }

    private fun releaseContiguous(out: MutableList<Out<T>>) {
        while (held.isNotEmpty() && held[0].sequence == expected) {
            held.removeAt(0).let { release(it.sequence, it.timestamp, it.item, out) }
        }
    }

    private fun release(seq: Int, timestamp: Int, item: T, out: MutableList<Out<T>>) {
        if (lastSequence >= 0 && seqDelta(seq, lastSequence) == 1) {
            val step = timestamp - lastTimestamp
            if (step in 1..MAX_SAMPLES_PER_PACKET) samplesPerPacket = step
        }
        lastSequence = seq
        lastTimestamp = timestamp
        expected = (seq + 1) and 0xffff
        out += Packet(item, timestamp)
    }

    private companion object {
        const val MAX_SAMPLES_PER_PACKET = 8_192

        fun seqDelta(a: Int, b: Int): Int = ((a - b + 32_768) and 0xffff) - 32_768
    }
}
