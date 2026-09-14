package dev.skycase.core

import dev.skycase.SkyCase
import net.minecraft.core.component.DataComponents
import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
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

    // C1: CaseOverlay.finish() runs inside the animation's own draw() call (task 14: HUD/screen render
    // callback, still on the client thread). Deferring the next pump() straight out of that call stack
    // (nested CaseOverlay.start() for the next reveal, mid-render) is asking for trouble, so onDone()
    // only flips `pending`; the actual pump() runs from the next TickEvent instead.
    @Volatile private var pending = false

    fun submit(r: Reveal) {
        if (!SkyCaseConfig.data.enabled) { ChatGuard.emitNow(r.heldChat); return }
        val shown = r.copy(pool = r.pool.map(LootPools::renderable), winner = LootPools.renderable(r.winner)) // strip unloaded Hypixel item models
        val safe = if (shown.pool.isEmpty()) shown.copy(pool = listOf(shown.winner)) else shown
        SkyCase.LOGGER.info(
            "reveal: winner='{}' item={} model={} pool={} (models stripped: {})",
            safe.winner.hoverName.string, safe.winner.item, safe.winner.get(DataComponents.ITEM_MODEL),
            safe.pool.size, r.pool.count { it.get(DataComponents.ITEM_MODEL) != null } - safe.pool.count { it.get(DataComponents.ITEM_MODEL) != null },
        )
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
        if (CaseOverlay.active) { pending = true; return } // re-arm: still playing, try again next tick
        val next = queue.removeFirstOrNull() ?: return
        playing = true
        next.heldChat.forEach(ChatGuard::hold)
        // Task 14 (chest): the overlay draws OVER whatever screen (e.g. an open dungeon/Kuudra chest
        // GUI) was showing before the reveal -- it is never replaced, so the chest stays open and is
        // clickable again the instant the overlay finishes. No restore logic needed.
        CaseOverlay.start(next, SkyCaseConfig.data.durationMs) { playing = false; pending = true; next.onFinished?.invoke() }
    }
}
