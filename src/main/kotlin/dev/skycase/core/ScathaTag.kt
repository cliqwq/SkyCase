package dev.skycase.core

/**
 * Parses a Scatha name-tag entity's custom name into (current hp, max hp).
 *
 * Format confirmed against Hypixel's standard mob/boss nametag shape (SkyHanni's
 * `data/mob/MobFilter.kt`, `@Language("RegExp")`-annotated regex with REGEX-TEST fixtures, e.g.
 * `"[Lv200] Arachne 20,000/20,000"`, `"[Lv500] Arachne 100k/100k"`, using
 * `[\d\/BMk.,${SkyblockStat.HEALTH.hypixelIcon}]+` for the health readout) -- Scatha (a Crystal
 * Hollows/Dwarven Mines mob, no dedicated SkyblockAPI entity data) follows the same
 * `[Lv<level>] Scatha <hp>/<max>❤` shape scraped straight off the armour-stand name tag.
 */
object ScathaTag {
    private val regex = Regex("""\[Lv\d+]\s*Scatha\s+(?<hp>[\d,.]+[kKmM]?)/(?<max>[\d,.]+[kKmM]?)❤?""")

    /** Null when [name] doesn't match a Scatha health readout. */
    fun parse(name: String): Pair<Double, Double>? {
        val m = regex.find(name) ?: return null
        val hp = number(m.groups["hp"]!!.value) ?: return null
        val max = number(m.groups["max"]!!.value) ?: return null
        return hp to max
    }

    /** "1,234" -> 1234.0, "1.2k" -> 1200.0, "2.5M" -> 2_500_000.0. */
    private fun number(raw: String): Double? {
        val noCommas = raw.replace(",", "")
        val mult = when (noCommas.lastOrNull()?.lowercaseChar()) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            else -> 1.0
        }
        val digits = if (mult != 1.0) noCommas.dropLast(1) else noCommas
        return digits.toDoubleOrNull()?.times(mult)
    }
}
