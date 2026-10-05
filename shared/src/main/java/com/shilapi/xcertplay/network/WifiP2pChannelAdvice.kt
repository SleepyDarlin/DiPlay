package com.shilapi.xcertplay.network

/**
 * Single-radio tablets serve a home Wi-Fi network and the Wi-Fi Direct group by hopping between
 * their channels. Each hop pauses CarPlay traffic, which lands as audio gaps longer than any buffer.
 */
object WifiP2pChannelAdvice {
    enum class Kind {
        /** The station sits on a DFS or other channel Wi-Fi Direct cannot share. */
        STATION_UNSHARABLE,
        /** A manual channel differs from a station channel that Auto would have shared. */
        MANUAL_MISMATCH,
    }

    data class Advice(val kind: Kind, val stationMHz: Int, val stationChannel: Int)

    /** Null when the tablet is not on Wi-Fi or Wi-Fi Direct can share the station's channel. */
    fun advise(stationMHz: Int?, preferredChannel: Int): Advice? {
        if (stationMHz == null || stationMHz <= 0) return null
        val stationChannel = channelOf(stationMHz)
        val sharable = P2pStartupRecovery.rememberedFrequency(stationMHz) != null
        val manual = WifiP2pChannels.frequencyMhz(preferredChannel)
        return when {
            !sharable -> Advice(Kind.STATION_UNSHARABLE, stationMHz, stationChannel)
            manual != null && manual != stationMHz -> Advice(Kind.MANUAL_MISMATCH, stationMHz, stationChannel)
            else -> null
        }
    }

    /** True when the station and the group run on different channels of the same radio. */
    fun multiChannel(stationMHz: Int?, groupMHz: Int?): Boolean =
        stationMHz != null && stationMHz > 0 && groupMHz != null && groupMHz > 0 && stationMHz != groupMHz

    fun channelOf(frequencyMHz: Int): Int = when (frequencyMHz) {
        2484 -> 14
        in 2412..2472 -> (frequencyMHz - 2407) / 5
        in 5000..5900 -> (frequencyMHz - 5000) / 5
        in 5955..7115 -> (frequencyMHz - 5950) / 5
        else -> -1
    }
}
