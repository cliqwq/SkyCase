package dev.skycase.core
import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
import dev.skycase.screen.CaseScreen
import net.minecraft.client.Minecraft
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent

object RevealQueue {
    // Invariant: `queue` and `playing` are mutated only on the client (render) thread — submit()
    // hops onto it via mc.execute{} before touching either, and pump() only ever runs there too
    // (called from enqueue() and from onTick, both on the render thread). This avoids a data race
    // between callers of submit() on arbitrary threads (e.g. a chat event thread) and the tick
    // that drains the queue.
    private val queue = ArrayDeque<Reveal>()
    private var playing = false

    // C1: CaseScreen.finish() runs inside the OUTGOING screen's own onClose()/removed() — i.e. still
    // on the client thread, still inside Minecraft's own teardown call stack for that screen.
    // BlockableEventLoop.execute()/submit() (net.minecraft.util.thread.BlockableEventLoop, confirmed
    // via javap on 26.2's minecraft-client.jar) both gate on `scheduleExecutables()`, which is exactly
    // `!isSameThread()`: when already on the client thread they run the task INLINE via doRunTask(),
    // synchronously, right there in the teardown call stack. There is no enqueue-only method on
    // Minecraft/BlockableEventLoop (no `tell`) reachable from the client thread itself, so nested
    // mc.setScreenAndShow() for the next reveal cannot be deferred that way. Instead onDone() only
    // flips `pending`; the actual pump() runs from the next TickEvent, safely outside any screen's
    // teardown call stack.
    @Volatile private var pending = false

    fun submit(r: Reveal) {
        if (!SkyCaseConfig.data.enabled) { ChatGuard.emitNow(r.heldChat); return }
        val safe = if (r.pool.isEmpty()) r.copy(pool = listOf(r.winner)) else r
        Minecraft.getInstance().execute { enqueue(safe) }
    }

    private fun enqueue(r: Reveal) {
        queue.addLast(r); pump()
    }

    @Subscription
    fun onTick(event: TickEvent) {
        if (pending) { pending = false; pump() }
    }

    private fun pump() {
        if (playing) return
        val mc = Minecraft.getInstance()
        if (mc.gui.screen() is CaseScreen) { pending = true; return } // re-arm: still showing, try again next tick
        val next = queue.removeFirstOrNull() ?: return
        playing = true
        next.heldChat.forEach(ChatGuard::hold)
        // I2 (chest): deliberately NOT restoring whatever screen (e.g. an open dungeon/Kuudra chest
        // GUI) was showing before the reveal. AbstractContainerScreen.removed() (which
        // setScreenAndShow(CaseScreen(...)) triggers on it here) calls menu.removed(player) on the
        // way out -- confirmed via javap on AbstractContainerScreen.class -- which drops the cursor
        // stack and tears down the menu's state. Re-showing that same screen instance afterwards
        // would present an already-invalidated menu, so the chest GUI simply stays closed once a
        // reveal has taken over, same as any other vanilla screen replacement.
        mc.setScreenAndShow(CaseScreen(next, SkyCaseConfig.data.durationMs) { playing = false; pending = true; next.onFinished?.invoke() })
    }
}
