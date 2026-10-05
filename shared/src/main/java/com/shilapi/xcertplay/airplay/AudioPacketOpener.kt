package com.shilapi.xcertplay.airplay

import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Opens CarPlay audio datagrams (12-byte RTP header, ciphertext, 16-byte tag, 8-byte nonce) straight
 * into a plain RTP packet. One instance belongs to one receive thread; it reuses its cipher and scratch
 * buffers so the per-packet path allocates only the returned packet.
 */
class AudioPacketOpener(key: ByteArray) {
    private val keyParameter = KeyParameter(key)
    private val cipher = ChaCha20Poly1305()
    private val aad = ByteArray(AAD_LEN)
    private val nonce = ByteArray(NONCE_SIZE)

    /** The RTP header followed by the decrypted payload; throws when the tag does not verify. */
    fun open(wire: ByteArray, length: Int = wire.size): ByteArray {
        require(length >= RTP_HEADER_LEN + TAIL_LEN) { "audio packet shorter than RTP header plus tail" }
        val sealedEnd = length - NONCE_LEN
        val sealedLength = sealedEnd - RTP_HEADER_LEN
        wire.copyInto(aad, 0, 4, RTP_HEADER_LEN)
        nonce.fill(0, 0, 4)
        wire.copyInto(nonce, 4, sealedEnd, length)
        cipher.init(false, AEADParameters(keyParameter, MAC_BITS, nonce, aad))
        val rtp = ByteArray(RTP_HEADER_LEN + cipher.getOutputSize(sealedLength))
        wire.copyInto(rtp, 0, 0, RTP_HEADER_LEN)
        val processed = cipher.processBytes(wire, RTP_HEADER_LEN, sealedLength, rtp, RTP_HEADER_LEN)
        val total = RTP_HEADER_LEN + processed + cipher.doFinal(rtp, RTP_HEADER_LEN + processed)
        return if (total == rtp.size) rtp else rtp.copyOf(total)
    }

    companion object {
        const val RTP_HEADER_LEN = 12
        const val TAG_LEN = 16
        const val NONCE_LEN = 8
        const val TAIL_LEN = TAG_LEN + NONCE_LEN
        private const val AAD_LEN = 8
        private const val NONCE_SIZE = 12
        private const val MAC_BITS = 128
    }
}
