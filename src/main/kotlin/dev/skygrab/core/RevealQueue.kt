package dev.skygrab.core
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.screen.CaseScreen
import net.minecraft.client.Minecraft

object RevealQueue {
    private val queue = ArrayDeque<Reveal>()
    private var playing = false

    fun submit(r: Reveal) {
        if (!SkyGrabConfig.data.enabled) return
        val safe = if (r.pool.isEmpty()) r.copy(pool = listOf(r.winner)) else r
        queue.addLast(safe); pump()
    }
    private fun pump() {
        if (playing) return
        val next = queue.removeFirstOrNull() ?: return
        playing = true
        val mc = Minecraft.getInstance()
        next.heldChat.forEach(dev.skygrab.chat.ChatGuard::hold)
        mc.execute { mc.setScreenAndShow(CaseScreen(next, SkyGrabConfig.data.durationMs) { playing = false; pump() }) }
    }
}
