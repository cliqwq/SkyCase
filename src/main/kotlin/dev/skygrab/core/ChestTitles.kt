package dev.skygrab.core
object ChestTitles {
    // Titles per Skyblocker ChestValue.java:59,63 (Hypixel sometimes doubles "Chest").
    private val dungeon = mapOf(
        "Wood" to ChestKind.WOOD, "Gold" to ChestKind.GOLD, "Diamond" to ChestKind.DIAMOND,
        "Emerald" to ChestKind.EMERALD, "Obsidian" to ChestKind.OBSIDIAN, "Bedrock" to ChestKind.BEDROCK,
    )
    fun kind(title: String): ChestKind? {
        val t = title.trim()
        when (t) {
            "Free Chest", "Free Chest Chest" -> return ChestKind.KUUDRA_FREE
            "Paid Chest", "Paid Chest Chest" -> return ChestKind.KUUDRA_PAID
        }
        val base = t.removeSuffix(" Chest")
        return dungeon[base]
    }
}
