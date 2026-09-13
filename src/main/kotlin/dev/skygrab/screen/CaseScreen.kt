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
    private val jitter = Random.nextInt(-6, 7)
    private val start = System.currentTimeMillis()
    private var lastTick = -1
    private var finished = false

    override fun init() {
        ChatGuard.active = true
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        val elapsed = System.currentTimeMillis() - start
        val p = Easing.outCubic(elapsed / durationMs.toFloat())
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

        for (i in 0 until STRIP_LEN) {
            val x = originX + i * cw
            if (x < -cw || x > w) continue
            val s = if (i == WINNER_INDEX && p >= 0.96f) Mth.lerp((p - 0.96f) / 0.04f, 1f, 3f) else 1f
            val cx = x + cw / 2f
            val cy = y + cw / 2f
            graphics.pose().pushMatrix()
            graphics.pose().translate(cx, cy)
            graphics.pose().scale(SCALE * s, SCALE * s)
            graphics.item(strip[i], -8, -8)
            graphics.pose().popMatrix()
        }
        graphics.fill(w / 2 - 1, (y - 6).toInt(), w / 2 + 1, (y + cw + 6).toInt(), 0xFFFFD700.toInt())

        if (p >= 1f) {
            graphics.text(font, reveal.winner.hoverName, w / 2 - font.width(reveal.winner.hoverName) / 2, (y + cw + 12).toInt(), 0xFFFFFF)
        }
        if (elapsed >= durationMs + 1200 && !finished) {
            finished = true
            onClose()
        }
    }

    override fun onClose() {
        ChatGuard.release()
        onDone()
        super.onClose()
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true
}
