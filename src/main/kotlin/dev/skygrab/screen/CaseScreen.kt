package dev.skygrab.screen

import dev.skygrab.chat.ChatGuard
import dev.skygrab.core.Easing
import dev.skygrab.core.Reveal
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/**
 * CS:GO-style case-scroll reveal. Purely cosmetic: never clicks, buys, or rerolls anything.
 *
 * 26.2 note: `Screen` no longer has `render(...)`; it uses an extract-render-state model, so the
 * draw entry point is `extractRenderState(GuiGraphicsExtractor, Int, Int, Float)`. `GuiGraphicsExtractor`
 * has no bare `translate`/`scale` methods either — matrix ops go through `graphics.pose()`
 * (a JOML `Matrix3x2fStack`) via `pushMatrix()`/`translate(x, y)`/`scale(sx, sy)`/`popMatrix()`.
 * Item drawing is `graphics.item(itemStack, x, y)`; text is `graphics.text(font, component, x, y, color)`
 * (there is no `drawString`).
 */
class CaseScreen(private val reveal: Reveal, private val durationMs: Int, private val onDone: () -> Unit) :
    Screen(Component.literal("SkyGrab Reveal")) {

    private companion object {
        const val STRIP_LEN = 40
        const val WINNER_INDEX = 32
        const val CARD = 24
        const val SCALE = 2
    }

    private val strip: List<ItemStack> = List(STRIP_LEN) { i -> if (i == WINNER_INDEX) reveal.winner else reveal.pool.random() }
    private val jitter = Random.nextInt(-(CARD * SCALE / 2 - 1), CARD * SCALE / 2) // gold line can land anywhere across the winner card
    private val start = System.currentTimeMillis()
    private var lastTick = -1
    private var finished = false

    override fun init() {
        ChatGuard.active = true
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        val elapsed = System.currentTimeMillis() - start
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
            onClose()
        }
    }

    /**
     * Single idempotent teardown path. `onClose()` (ESC / natural finish calling onClose()) and
     * `removed()` (fires whenever this screen stops being the active screen for ANY reason —
     * disconnect, another mod calling setScreenAndShow, a kick, etc. — onClose() is NOT guaranteed
     * to run in those cases) both funnel here so ChatGuard.release()/onDone() run exactly once,
     * regardless of which path tore the screen down. Without this, a non-onClose teardown would
     * never flip RevealQueue.playing back to false and the queue would wedge forever.
     */
    private fun finish() {
        if (finished) return
        finished = true
        ChatGuard.release()
        onDone()
    }

    override fun onClose() {
        finish()
        super.onClose()
    }

    override fun removed() {
        finish()
        super.removed()
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true
}
