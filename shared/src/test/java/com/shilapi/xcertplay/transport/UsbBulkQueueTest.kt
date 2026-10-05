package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbBulkQueueTest {
    @Test
    fun `a 64 kb request falls back through 16 kb then 4 kb`() {
        assertEquals(listOf(65_536, 16_384, 4_096), UsbBulkQueue.sizes(65_536))
    }

    @Test
    fun `a 32 kb ncm request skips the already-smaller 16 kb once it matches`() {
        assertEquals(listOf(32_768, 16_384, 4_096), UsbBulkQueue.sizes(32 * 1024))
        assertEquals(listOf(4_096), UsbBulkQueue.sizes(4_096))
    }
}
