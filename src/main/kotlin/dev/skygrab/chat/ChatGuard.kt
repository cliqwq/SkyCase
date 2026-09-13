package dev.skygrab.chat
import dev.skygrab.SkyGrab
import dev.skygrab.config.SkyGrabConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud
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

    /** Re-add held lines to chat history. Pre-cancelled lines are restored.
     * Minecraft.gui is a non-null Hud field, so the cast should never fail; the try/catch is defensive.
     * If the cast fails (should not happen), logs a WARN with the count of lost lines and clears the queue. */
    fun release() {
        active = false
        val hud = try {
            (Minecraft.getInstance().gui as Hud)
        } catch (e: Exception) {
            // Defensive: Minecraft.gui is non-null Hud, so this should never execute.
            // If it does, lines are lost; log so the user knows restoration failed.
            SkyGrab.LOGGER.warn("Cannot access Hud; ${held.size} held chat lines will not be re-shown", e)
            held.clear()
            return
        }
        val count = held.size
        while (held.isNotEmpty()) {
            hud.chat.addClientSystemMessage(held.removeFirst())
        }
        if (count > 0) SkyGrab.LOGGER.debug("Re-emitted $count held chat lines")
    }

    @Subscription
    fun onHud(event: RenderHudElementEvent) {
        if (active && SkyGrabConfig.data.hideChat && event.element == HudElement.CHAT) event.cancel()
    }
}
