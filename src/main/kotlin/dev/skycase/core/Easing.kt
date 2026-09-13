package dev.skycase.core
object Easing {
    fun outCubic(t: Float): Float { val c = t.coerceIn(0f, 1f); val u = 1f - c; return 1f - u * u * u }
}
