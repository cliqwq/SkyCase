package dev.skycase.core

/** Pure text parsing for the item name embedded in a chat line. No MC deps -> unit-testable.
 * Pet lines are matched FIRST ("PET DROP! <name> (...)") so a pet name never falls through to the
 * generic "(...)" capture, which would otherwise grab the trailing "(+100% Magic Find)" parenthetical
 * and leave the caller with no name match on the pet itself. */
object ChatParse {
    private val petName = Regex("PET DROP! (.+?) \\(")
    private val genericParen = Regex("\\(([^)]+)\\)")
    private val caughtSuffix = Regex("You caught an? (.+?) (?:BRONZE|SILVER|GOLD|DIAMOND)!")
    private val countPrefix = Regex("^\\d+x ")

    fun itemNamedIn(text: String): String? {
        val raw = petName.find(text)?.groupValues?.get(1)
            ?: genericParen.find(text)?.groupValues?.get(1)
            ?: caughtSuffix.find(text)?.groupValues?.get(1)
            ?: return null
        return countPrefix.replace(raw.trim(), "").trim()
    }

    // Same tier suffix as RarityGate's trophy regex (SkyHanni TrophyFishMessages.kt:37), but
    // capturing the fish name too instead of only testing gold/diamond.
    private val trophyCatch = Regex("TROPHY FISH! You caught an? (.+?) (BRONZE|SILVER|GOLD|DIAMOND)!")

    /** Fish name + tier word ("BRONZE"/"SILVER"/"GOLD"/"DIAMOND") from a trophy fish catch line, or null. */
    fun trophy(text: String): Pair<String, String>? {
        val m = trophyCatch.find(text) ?: return null
        return m.groupValues[1].trim() to m.groupValues[2]
    }

    // SkyHanni HoppityEggsManager.kt:83-85 ("rabbit.found"), colour codes already stripped from `text`:
    // "HOPPITY'S HUNT You found <name> (<RARITY>)!" -- sent just before the "NEW RABBIT!" line.
    private val rabbitFoundLine = Regex("HOPPITY'S HUNT You found (.+?) \\((.+?)\\)!")

    /** Rabbit name + rarity word ("COMMON".."DIVINE") from a Hoppity "You found" receipt, or null. */
    fun rabbitFound(text: String): Pair<String, String>? {
        val m = rabbitFoundLine.find(text) ?: return null
        return m.groupValues[1].trim() to m.groupValues[2].trim()
    }

    // SkyHanni RareDropMessages.kt:52 colour code -> rarity name, reused here for the pure part of
    // PET DROP parsing (colour -> rarity string; the caller converts to SkyBlockRarity, no MC dep here).
    private val petDropLine = Regex("(?:§.)*PET DROP! (?:§.)*§(?<c>.)(?:§.)*(?<name>[^§]+)")
    private val colourToRarity = mapOf(
        "f" to "COMMON", "a" to "UNCOMMON", "9" to "RARE", "5" to "EPIC",
        "6" to "LEGENDARY", "d" to "MYTHIC", "b" to "DIVINE",
    )

    /** Pet name + rarity name (e.g. "LEGENDARY") from a coloured "PET DROP! <name> (...)" line, or null. */
    fun petDrop(coloredText: String): Pair<String, String>? {
        val m = petDropLine.find(coloredText) ?: return null
        val rarity = colourToRarity[m.groups["c"]!!.value] ?: return null
        return m.groups["name"]!!.value.trim() to rarity
    }

    // SkyHanni DragonFightAPI.kt ("DRAGON DOWN!" receipt): "§r§f" + 27 spaces + "§r§6§l<TYPE> DRAGON DOWN!§r".
    // Colour codes matter here (the 27-space run only appears in the coloured line), so this matches
    // event.coloredText like petDrop does, not the stripped text.
    private val dragonDownLine = Regex("(?:§r)?§f +§r§6§l(?<type>[A-Z ]+?) DRAGON DOWN!(?:§r)?") // Hypixel centres the banner: leading space count varies per dragon name (SkyHanni DragonFeatures REGEX-TESTs: 27 for OLD, 22 for PROTECTOR)

    /** Dragon type key ("OLD"/"PROTECTOR"/"WISE"/"UNSTABLE"/"STRONG"/"YOUNG"/"SUPERIOR") from a
     * "<TYPE> DRAGON DOWN!" receipt, or null. Matches [LootPools.dragon]'s pool keys directly. */
    fun dragonDown(coloredText: String): String? =
        dragonDownLine.find(coloredText)?.groups?.get("type")?.value?.trim()?.uppercase()
}
