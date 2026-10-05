package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.network.WifiP2pChannelAdvice.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiP2pChannelAdviceTest {
    @Test
    fun `dfs station wifi cannot be shared with wifi direct`() {
        val advice = WifiP2pChannelAdvice.advise(5280, WifiP2pChannels.AUTO)
        assertEquals(Kind.STATION_UNSHARABLE, advice?.kind)
        assertEquals(56, advice?.stationChannel)
    }

    @Test
    fun `a sharable station channel needs no warning`() {
        assertNull(WifiP2pChannelAdvice.advise(2462, WifiP2pChannels.AUTO))
        assertNull(WifiP2pChannelAdvice.advise(5180, WifiP2pChannels.AUTO))
        assertNull(WifiP2pChannelAdvice.advise(null, WifiP2pChannels.AUTO))
    }

    @Test
    fun `a manual channel that differs from the station is a mismatch`() {
        val advice = WifiP2pChannelAdvice.advise(2462, 1)
        assertEquals(Kind.MANUAL_MISMATCH, advice?.kind)
        assertEquals(11, advice?.stationChannel)
    }

    @Test
    fun `multi-channel concurrency is the station and group on different frequencies`() {
        assertTrue(WifiP2pChannelAdvice.multiChannel(5280, 2462))
        assertFalse(WifiP2pChannelAdvice.multiChannel(2462, 2462))
        assertFalse(WifiP2pChannelAdvice.multiChannel(null, 2462))
    }
}
