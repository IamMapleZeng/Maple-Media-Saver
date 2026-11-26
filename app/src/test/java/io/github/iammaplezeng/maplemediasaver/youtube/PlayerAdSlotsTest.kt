package io.github.iammaplezeng.maplemediasaver.youtube

import org.junit.Assert.*
import org.junit.Test

class PlayerAdSlotsTest {
    class Player(@JvmField var n: List<String>, val video: String, val formats: List<String>) {
        fun toBuilder() = Builder(this)
    }
    open class BaseBuilder(original: Player) {
        @JvmField var instance = original
        protected fun copyOnWrite() { instance = Player(instance.n, instance.video, instance.formats) }
        fun build() = instance
    }
    class Builder(original: Player) : BaseBuilder(original)

    @Test fun removesOnlyAdSlotsFromCopyAndPreservesOriginal() {
        val original = Player(listOf("ad-slot"), "video", listOf("720p", "1080p"))
        val filtered = PlayerAdSlots.remove(original, emptyList<String>()) as Player
        assertNotSame(original, filtered)
        assertEquals(listOf("ad-slot"), original.n)
        assertTrue(filtered.n.isEmpty())
        assertEquals(original.video, filtered.video)
        assertSame(original.formats, filtered.formats)
    }
    @Test fun leavesAdFreePlayerUntouched() {
        val player = Player(emptyList(), "video", listOf("360p"))
        assertSame(player, PlayerAdSlots.remove(player, emptyList<String>()))
    }
}
