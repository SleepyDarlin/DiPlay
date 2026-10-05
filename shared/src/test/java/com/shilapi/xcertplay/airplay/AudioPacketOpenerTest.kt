package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPacketOpenerTest {
    @Test
    fun `opens a wire datagram into a plain rtp packet`() {
        val key = ByteArray(32) { it.toByte() }
        val header = byteArrayOf(
            0x80.toByte(), 96, 0x00, 0x07,
            0x00, 0x00, 0x04, 0x00,
            0x11, 0x22, 0x33, 0x44,
        )
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val nonce64 = ByteArray(8) { 9 }
        val nonce = ByteArray(12).also { nonce64.copyInto(it, 4) }
        val aad = header.copyOfRange(4, 12)
        val sealed = AirPlayCrypto.chachaSeal(key, nonce, payload, aad)
        val wire = header + sealed + nonce64
        val rtp = AudioPacketOpener(key).open(wire)
        assertEquals(header.size + payload.size, rtp.size)
        assertArrayEquals(header + payload, rtp)
    }

    @Test
    fun `reuses the opener across packets`() {
        val key = ByteArray(32) { 7 }
        val opener = AudioPacketOpener(key)
        repeat(3) { index ->
            val header = ByteArray(12).also { it[3] = index.toByte(); it[7] = (index * 16).toByte() }
            val payload = byteArrayOf(index.toByte(), 0x55)
            val nonce64 = ByteArray(8) { index.toByte() }
            val nonce = ByteArray(12).also { nonce64.copyInto(it, 4) }
            val sealed = AirPlayCrypto.chachaSeal(key, nonce, payload, header.copyOfRange(4, 12))
            assertArrayEquals(header + payload, opener.open(header + sealed + nonce64))
        }
    }
}
