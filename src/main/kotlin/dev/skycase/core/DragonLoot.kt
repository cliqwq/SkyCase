package dev.skycase.core

/** Pure winner-priority ranking for a Dragon fight's 12s inventory-watch window (Task 12 brief).
 * Higher [rank] wins; 0 means "not a Dragon-pool item" -- the caller (ChatTrigger) ignores it,
 * which is also how unrelated inventory churn (dungeon loot, other mob drops) gets filtered out
 * without needing per-type pool membership checks. No MC/repo deps -> unit-testable.
 *
 * The Ender Dragon pet itself is NOT ranked here: [ChatTrigger] detects it via the picked-up
 * stack's `GenericDataTypes.PET_DATA` id and short-circuits to the highest rank before ever
 * consulting this table, per the brief's "pet > everything else" priority. */
object DragonLoot {
    // Winner priority per brief: pet (handled by caller) > Horn/Claw/AOTD/Scale/Scroll/Dye >
    // armor piece > Dragon Fragment > Enchanted Ender Pearl > Ender Pearl > Dragon Essence.
    private val specialNames = setOf(
        "Dragon Horn", "Dragon Claw", "Aspect of the Dragons", "Dragon Scale",
        "Travel Scroll to Dragon's Nest", "Pearlescent Dye",
    )
    private val armorSuffixes = listOf("Helmet", "Chestplate", "Leggings", "Boots")

    fun rank(name: String): Int = when {
        name in specialNames -> 6
        armorSuffixes.any { name.endsWith(it) } && "Dragon" in name -> 5
        name.endsWith("Dragon Fragment") -> 4 // per-type: "Superior Dragon Fragment"
        name == "Enchanted Ender Pearl" -> 3
        name == "Ender Pearl" -> 2
        name == "Dragon Essence" -> 1
        else -> 0
    }
}
