package dev.skycase.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScathaTagTest {
    @Test fun parsesPlainIntegerReadout() {
        assertEquals(50.0 to 100.0, ScathaTag.parse("[Lv50] Scatha 50/100❤"))
    }

    @Test fun parsesKiloSuffix() {
        assertEquals(1200.0 to 8000.0, ScathaTag.parse("[Lv50] Scatha 1.2k/8000❤"))
    }

    @Test fun parsesMegaSuffixAndCommas() {
        assertEquals(2_500_000.0 to 2_500_000.0, ScathaTag.parse("[Lv50] Scatha 2.5M/2.5M❤"))
        assertEquals(1_234.0 to 8_000.0, ScathaTag.parse("[Lv50] Scatha 1,234/8,000❤"))
    }

    @Test fun nonScathaNameDoesNotMatch() {
        assertNull(ScathaTag.parse("[Lv50] Baby Yeti 50/100❤"))
        assertNull(ScathaTag.parse("Scatha"))
    }
}
