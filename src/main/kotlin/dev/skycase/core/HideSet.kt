package dev.skycase.core

import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.render.HudElement
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudElementEvent

/**
 * Task 15: three flags flipped by [dev.skycase.triggers.ScathaTrigger] for the Scatha pre-roll/roll
 * window -- hotbar (consulted here), held item and non-mod sounds (consulted by the two Java mixins,
 * `ItemInHandRendererMixin`/`SoundManagerMixin`). Purely cosmetic: nothing here touches packets,
 * pickup, or input.
 */
object HideSet {
    var hud = false
    var hand = false
    var mute = false

    // HudMixin (SkyblockAPI 26.2) posts RenderHudElementEvent(HOTBAR) via
    // `!new RenderHudElementEvent(HudElement.HOTBAR, graphics).post(bus)` guarding the call to the
    // vanilla `extractItemHotbar` -- i.e. post() runs every subscriber (in priority order, lower
    // first per Subscription.kt) before returning, and CaseOverlay's own onHud subscriber (default
    // priority 0) draws its overlay *inside* that same call by checking `active`, not by cancelling.
    // Subscription.receiveCancelled defaults to false, so a subscriber never runs at all once an
    // earlier (lower-priority) one has cancelled the event -- cancelling here BEFORE CaseOverlay's
    // handler would silently skip its draw call the moment a reveal starts playing mid pre-roll hide.
    // Priority LOW (100000, see Subscription.kt: "lower priority will be called first") runs this
    // after CaseOverlay instead, so the overlay always gets to draw first; only then do we cancel to
    // stop the vanilla hotbar/held-item icons from rendering underneath (or, if no overlay is up yet
    // during plain pre-roll, to hide the hotbar with nothing drawn in its place at all).
    @Subscription(priority = Subscription.LOW)
    fun onHud(event: RenderHudElementEvent) {
        if (hud && event.element == HudElement.HOTBAR) event.cancel()
    }
}
