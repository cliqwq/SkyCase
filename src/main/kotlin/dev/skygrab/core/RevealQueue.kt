package dev.skygrab.core
import dev.skygrab.chat.ChatGuard
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.screen.CaseScreen
import net.minecraft.client.Minecraft

object RevealQueue {
    // Invariant: `queue` and `playing` are mutated only on the client (render) thread — submit()
    // hops onto it via mc.execute{} before touching either, and pump() only ever runs there too
    // (called from enqueue() and from the onDone callback, which CaseScreen invokes from the render
    // thread). This avoids a data race between callers of submit() on arbitrary threads (e.g. a chat
    // event thread) and onDone firing on the render thread.
    private val queue = ArrayDeque<Reveal>()
    private var playing = false

    fun submit(r: Reveal) {
        if (!SkyGrabConfig.data.enabled) return
        val safe = if (r.pool.isEmpty()) r.copy(pool = listOf(r.winner)) else r
        Minecraft.getInstance().execute { enqueue(safe) }
    }
    private fun enqueue(r: Reveal) {
        queue.addLast(r); pump()
    }
    private fun pump() {
        if (playing) return
        val next = queue.removeFirstOrNull() ?: return
        playing = true
        val mc = Minecraft.getInstance()
        next.heldChat.forEach(ChatGuard::hold)
        mc.setScreenAndShow(CaseScreen(next, SkyGrabConfig.data.durationMs) { playing = false; pump() })
    }
}
