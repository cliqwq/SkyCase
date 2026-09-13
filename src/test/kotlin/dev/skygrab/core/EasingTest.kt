package dev.skygrab.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class EasingTest {
    @Test fun endpoints() { assertEquals(0f, Easing.outCubic(0f)); assertEquals(1f, Easing.outCubic(1f)) }
    @Test fun monotone() { var last = -1f; for (i in 0..100) { val v = Easing.outCubic(i / 100f); assertTrue(v >= last); last = v } }
    @Test fun clamps() { assertEquals(1f, Easing.outCubic(2f)); assertEquals(0f, Easing.outCubic(-1f)) }
}
