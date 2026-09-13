package dev.skygrab.chat
import dev.skygrab.config.SkyGrabConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.render.HudElement
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudElementEvent

object ChatGuard {
    @Volatile var active = false
    private val held = ArrayDeque<Component>()

    fun hold(c: Component) { held.addLast(c) }

    /** Re-add held lines to local chat history. Nothing was ever dropped: Pre-cancelled lines are re-shown here. */
    fun release() {
        active = false
        val hud = Minecraft.getInstance().gui as? Hud
        val chat = hud?.chat
        while (held.isNotEmpty() && chat != null) {
            chat.addClientSystemMessage(held.removeFirst())
        }
    }

    @Subscription
    fun onHud(event: RenderHudElementEvent) {
        if (active && SkyGrabConfig.data.hideChat && event.element == HudElement.CHAT) event.cancel()
    }
}
