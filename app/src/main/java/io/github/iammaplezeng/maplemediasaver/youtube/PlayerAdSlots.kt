package io.github.iammaplezeng.maplemediasaver.youtube

/** Only the verified PlayerResponse.n ad-slot list is replaced; the original message stays intact. */
internal object PlayerAdSlots {
    fun remove(player: Any, empty: Any): Any {
        val slots = player.javaClass.getDeclaredField("n").apply { isAccessible = true }
        if ((slots.get(player) as? Collection<*>)?.isNotEmpty() != true) return player
        return copyWithoutSlots(player, empty)
    }
    fun copyWithoutSlots(player: Any, empty: Any): Any {
        val slots = player.javaClass.getDeclaredField("n").apply { isAccessible = true }
        val builder = player.javaClass.getMethod("toBuilder").invoke(player) ?: return player
        generateSequence(builder.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredMethod("copyOnWrite") }.getOrNull() }
            .apply { isAccessible = true }.invoke(builder)
        val instanceField = generateSequence(builder.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredField("instance") }.getOrNull() }
            .apply { isAccessible = true }
        val copy = instanceField.get(builder) ?: return player
        check(copy !== player)
        slots.set(copy, empty)
        return builder.javaClass.getMethod("build").invoke(builder) ?: player
    }
}
