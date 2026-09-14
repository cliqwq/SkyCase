package dev.skycase.core

import com.mojang.blaze3d.platform.InputConstants
import dev.skycase.chat.ChatGuard
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.render.HudElement
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudElementEvent
import tech.thatgravyboat.skyblockapi.api.events.render.RenderScreenBackgroundEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenKeyPressedEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenMouseClickEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenMouseReleasedEvent
import kotlin.random.Random

/**
 * CS:GO-style case-scroll reveal, task 14: HUD overlay instead of a `Screen` -- the player keeps
 * moving/fighting/opening menus while it plays. Same animation, same maths as the old `CaseScreen`.
 *
 * Two draw sites, same `draw()`:
 * - no GUI open: `RenderHudElementEvent` (`HudElement.HOTBAR` -- renders every frame the HUD is up,
 *   and is the world-visible case; skipped whenever a screen IS open so the other site below owns it).
 * - a screen is open (chest triggers, or the player opens one mid-world-reveal): `RenderScreenBackgroundEvent`,
 *   cancelled so the screen's own background doesn't show through, same pattern SkyOcean's
 *   `DungeonGamblingRenderer`/`DungeonGambling` use for their dungeon-chest gambling overlay -- click/key
 *   events are also cancelled (`ScreenMouseClickEvent.Pre`/`ScreenMouseReleasedEvent.Pre`/
 *   `ScreenKeyPressedEvent.Pre`) so the chest underneath can't be clicked through the roll. ESC is the one
 *   key NOT swallowed -- it aborts the overlay immediately instead, letting the screen's own ESC handling
 *   (closing the chest) run right after.
 */
object CaseOverlay {
    private const val STRIP_LEN = 40
    private const val WINNER_INDEX = 32
    private const val CARD = 24
    private const val SCALE = 2

    private var current: Reveal? = null
    private var strip: List<ItemStack> = emptyList()
    private var jitter = 0
    private var startedAt = 0L
    private var durationMs = 0
    private var lastTick = -1
    private var finished = false
    private var onDone: (() -> Unit)? = null

    val active: Boolean get() = current != null

    fun start(reveal: Reveal, durationMs: Int, onDone: () -> Unit) {
        current = reveal
        this.durationMs = durationMs
        this.onDone = onDone
        strip = List(STRIP_LEN) { i -> if (i == WINNER_INDEX) reveal.winner else reveal.pool.random() }
        jitter = Random.nextInt(-(CARD * SCALE / 2 - 1), CARD * SCALE / 2) // gold line can land anywhere across the winner card
        startedAt = System.currentTimeMillis()
        lastTick = -1
        finished = false
        ChatGuard.active = true
    }

    /** Chest-GUI ESC path: finish right now instead of riding out the 1.2s tail. */
    fun abort() = finish()

    private fun draw(graphics: GuiGraphicsExtractor) {
        val reveal = current ?: return
        val elapsed = System.currentTimeMillis() - startedAt
        val p = Easing.outCubic(elapsed / durationMs.toFloat())
        // winner pop starts only once the strip has stopped (raw time, not eased p), grows over 400 ms
        val pop = ((elapsed - durationMs) / 400f).coerceIn(0f, 1f)
        val cw = (CARD * SCALE).toFloat()
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        graphics.fill(0, 0, w, h, 0x80000000.toInt())

        val offset = WINNER_INDEX * cw * p + jitter
        val originX = w / 2f - offset - cw / 2f
        val y = h / 2f - cw / 2f

        val crossed = (offset / cw).toInt()
        if (crossed > lastTick) {
            lastTick = crossed
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 2f))
        }

        val font = Minecraft.getInstance().font
        fun drawCard(i: Int) {
            val x = originX + i * cw
            if (x < -cw || x > w) return
            val s = if (i == WINNER_INDEX && pop > 0f) Mth.lerp(Easing.outCubic(pop), 1f, 3f) else 1f
            val cx = x + cw / 2f
            val cy = y + cw / 2f
            graphics.pose().pushMatrix()
            graphics.pose().translate(cx, cy)
            graphics.pose().scale(SCALE * s, SCALE * s)
            // card box: 22x22 around the 16x16 item (unscaled units), 1px border, dark fill
            val border = if (i == WINNER_INDEX && pop > 0f) 0xFFFFD700.toInt() else 0xFF5A5A5A.toInt()
            graphics.fill(-11, -11, 11, 11, border)
            graphics.fill(-10, -10, 10, 10, 0xE0141414.toInt())
            graphics.item(strip[i], -8, -8)
            graphics.pose().popMatrix()
        }
        for (i in 0 until STRIP_LEN) if (i != WINNER_INDEX) drawCard(i)
        drawCard(WINNER_INDEX) // last → the popped winner renders over its neighbours
        if (pop == 0f) graphics.fill(w / 2 - 1, (y - 6).toInt(), w / 2 + 1, (y + cw + 6).toInt(), 0xFFFFD700.toInt())

        if (p >= 1f) {
            graphics.text(font, reveal.winner.hoverName, w / 2 - font.width(reveal.winner.hoverName) / 2, (y + cw + 12).toInt(), 0xFFFFFF)
        }
        if (elapsed >= durationMs + 1200 && !finished) {
            finish()
        }
    }

    /** Single idempotent teardown path -- natural finish (the +1200ms tail in `draw()`) and `abort()`
     * (chest-GUI ESC) both funnel here so `ChatGuard.release()`/`onDone()` run exactly once. */
    private fun finish() {
        if (finished) return
        finished = true
        val cb = onDone
        current = null
        strip = emptyList()
        onDone = null
        ChatGuard.release()
        cb?.invoke()
    }

    // --- world: no screen open ---
    @Subscription
    fun onHud(event: RenderHudElementEvent) {
        if (!active) return
        if (event.element != HudElement.HOTBAR) return // renders every frame the HUD is up; draw once per frame
        if (Minecraft.getInstance().gui.screen() != null) return // a screen is open -- onScreenBackground owns the draw
        event.graphics?.let(::draw)
    }

    // --- a screen is open (chest triggers, or the player opened one mid-world-reveal) ---
    @Subscription
    fun onScreenBackground(event: RenderScreenBackgroundEvent) {
        if (!active) return
        event.cancel()
        draw(event.graphics)
    }

    @Subscription
    fun onMouseClick(event: ScreenMouseClickEvent.Pre) {
        if (active) event.cancel()
    }

    @Subscription
    fun onMouseReleased(event: ScreenMouseReleasedEvent.Pre) {
        if (active) event.cancel()
    }

    @Subscription
    fun onKeyPressed(event: ScreenKeyPressedEvent.Pre) {
        if (!active) return
        if (event.key == InputConstants.KEY_ESCAPE) { abort(); return } // not swallowed -- closing the chest is fine
        event.cancel()
    }
}
