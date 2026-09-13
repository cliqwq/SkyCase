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

    /** Re-add all held lines to local chat history in order. No messages are dropped—pre-cancelled
     * lines that were intercepted are restored here. If Hud is unavailable (null), logs a WARN and
     * discards the message count so the user knows restoration was incomplete. */
    fun release() {
        active = false
        val hud = try {
            (Minecraft.getInstance().gui as Hud)
        } catch (e: Exception) {
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
