package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbRequest
import java.nio.ByteBuffer

/** Unisoc (and some other) kernels reject 64 KB UsbRequest queues; try 16 KB, then 4 KB. */
internal object UsbBulkQueue {
    fun sizes(preferredBytes: Int): List<Int> =
        (listOf(preferredBytes) + listOf(16 * 1024, 4 * 1024).filter { it < preferredBytes }).distinct()

    fun queue(request: UsbRequest, preferredBytes: Int, onRetry: (Int) -> Unit = {}): ByteBuffer? {
        for (size in sizes(preferredBytes)) {
            val buffer = ByteBuffer.allocateDirect(size)
            if (request.queue(buffer)) {
                if (size != preferredBytes) onRetry(size)
                return buffer
            }
        }
        return null
    }
}
