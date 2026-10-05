package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtpAudioReorderTest {
    @Test
    fun `in-order packets pass through`() {
        val reorder = RtpAudioReorder<Int>()
        assertEquals(listOf(RtpAudioReorder.Packet(1, 0)), reorder.push(1, 0, 1, 0))
        assertEquals(listOf(RtpAudioReorder.Packet(2, 1024)), reorder.push(2, 1024, 2, 1))
        assertEquals(0, reorder.lostPackets)
    }

    @Test
    fun `late packets are counted and dropped`() {
        val reorder = RtpAudioReorder<Int>()
        reorder.push(1, 0, 1, 0)
        reorder.push(2, 1024, 2, 1)
        assertTrue(reorder.push(1, 0, 1, 2).isEmpty())
        assertEquals(1, reorder.latePackets)
    }

    @Test
    fun `out-of-order packets wait then release in sequence`() {
        val reorder = RtpAudioReorder<Int>()
        reorder.push(1, 0, 1, 0)
        val held = reorder.push(3, 2048, 3, 1)
        assertTrue(held.isEmpty())
        val released = reorder.push(2, 1024, 2, 2)
        assertEquals(
            listOf(RtpAudioReorder.Packet(2, 1024), RtpAudioReorder.Packet(3, 2048)),
            released,
        )
        assertEquals(1, reorder.reorderedPackets)
        assertEquals(0, reorder.lostPackets)
    }

    @Test
    fun `a hole past the hold window becomes silence`() {
        val reorder = RtpAudioReorder<Int>(holdPackets = 8, holdNanos = 10)
        reorder.push(1, 0, 1, 0)
        reorder.push(2, 1024, 2, 1)
        assertTrue(reorder.push(4, 3072, 4, 2).isEmpty())
        val flushed = reorder.flushStale(20)
        assertEquals(
            listOf(RtpAudioReorder.Gap(1, 1024, 3072), RtpAudioReorder.Packet(4, 3072)),
            flushed,
        )
        assertEquals(1, reorder.lostPackets)
        assertEquals(1024, reorder.concealedSamples)
    }

    @Test
    fun `sequence numbers wrap from 65535 to 0`() {
        val reorder = RtpAudioReorder<Int>()
        reorder.push(65535, 0, 65535, 0)
        assertEquals(listOf(RtpAudioReorder.Packet(0, 1024)), reorder.push(0, 1024, 0, 1))
        assertEquals(0, reorder.lostPackets)
        assertEquals(0, reorder.resyncs)
    }

    @Test
    fun `a large jump is a new run not a multi-second hole`() {
        val reorder = RtpAudioReorder<Int>(resyncPackets = 8)
        reorder.push(1, 0, 1, 0)
        val out = reorder.push(40, 40_960, 40, 1)
        assertEquals(listOf(RtpAudioReorder.Packet(40, 40_960)), out)
        assertEquals(1, reorder.resyncs)
        assertEquals(0, reorder.lostPackets)
    }
}
