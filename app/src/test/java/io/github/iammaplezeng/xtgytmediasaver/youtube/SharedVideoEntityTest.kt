package io.github.iammaplezeng.xtgytmediasaver.youtube

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SharedVideoEntityTest {
    private fun encoded(vararg bytes: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.map(Int::toByte).toByteArray())
    @Test fun decodesNativeVideoEntity() {
        val bytes = byteArrayOf(10, 11) + "abcdefgh_12".toByteArray()
        assertEquals("https://www.youtube.com/watch?v=abcdefgh_12", SharedVideoEntity.videoLink(Base64.getUrlEncoder().encodeToString(bytes)))
    }
    @Test fun rejectsOtherEntityTypesAndMalformedPayloads() {
        listOf("", "%%%", encoded(10, 20, 65), encoded(18, 1, 65), encoded(10, 1, 65), encoded(15))
            .forEach { assertNull(SharedVideoEntity.videoLink(it)) }
    }
    @Test fun acceptsRealSizedShareMetadataWithoutTreatingItAsVideoData() {
        // Device schema: field 1 = 11-byte video ID, field 12 = 6291 bytes, field 20 = varint.
        val bytes = byteArrayOf(10, 11) + "abcdefgh_12".toByteArray() +
            byteArrayOf(98, 147.toByte(), 49) + ByteArray(6291) + byteArrayOf(160.toByte(), 1, 1)
        val entity = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(entity.length > 4096)
        assertEquals("https://www.youtube.com/watch?v=abcdefgh_12", SharedVideoEntity.videoLink(entity))
    }
    @Test fun rejectsUnboundedEntities() { assertNull(SharedVideoEntity.videoLink("A".repeat(65_537))) }
}
