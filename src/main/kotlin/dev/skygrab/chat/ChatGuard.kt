package dev.skygrab.chat
import dev.skygrab.SkyGrab
import dev.skygrab.config.SkyGrabConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.render.HudElement
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudElementEvent
import java.util.concurrent.ConcurrentLinkedDeque

object ChatGuard {
    @Volatile var active = false
    /** Held messages: thread-safe queue for chat lines intercepted while reveal plays.
     * Both hold() (from chat event on render thread) and release() (from animation end on render thread)
     * use this concurrently; ConcurrentLinkedDeque ensures no messages are silently dropped. */
    private val held = ConcurrentLinkedDeque<Component>()

    /** Queue a chat message to re-emit later. Thread-safe. */
    fun hold(c: Component) { held.addLast(c) }

    /** Re-add held lines to chat history and clear `active`. Pre-cancelled lines are restored.
     * `Minecraft.gui` is `Gui`, a wrapper that owns the (non-null) `Hud` -- write through
     * `gui.hud.chat`, the same path emitNow() uses. */
    fun release() {
        active = false
        val count = held.size
        while (held.isNotEmpty()) {
            Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(held.removeFirst())
        }
        if (count > 0) SkyGrab.LOGGER.debug("Re-emitted $count held chat lines")
    }

    /** Write lines straight to chat, bypassing the held queue entirely and without touching
     * `active`. For callers that need an immediate, non-animated emit -- ChatTrigger.reshow()
     * (would otherwise have to drain the whole ChatGuard deque and unhide chat mid-animation),
     * ChatTrigger's screen-open skip, and RevealQueue.submit() when the mod is disabled. */
    fun emitNow(lines: List<Component>) {
        val chat = Minecraft.getInstance().gui.hud.chat
        lines.forEach(chat::addClientSystemMessage)
    }

    @Subscription
    fun onHud(event: RenderHudElementEvent) {
        if (active && SkyGrabConfig.data.hideChat && event.element == HudElement.CHAT) event.cancel()
    }
}
