package dev.skycase.core
object RarityGate {
    // Prefixes per SkyHanni RngDropEnum.kt:4-7 + ChatFilter.kt:194 ("VERY RARE DROP!").
    private val tierPrefix = listOf(
        "PRAY TO RNGESUS DROP!" to DropTier.PRAY_RNGESUS,
        "CRAZY RARE DROP!" to DropTier.CRAZY_RARE,
        "VERY RARE DROP!" to DropTier.VERY_RARE,
        "RARE DROP!" to DropTier.RARE,
        "UNCOMMON DROP!" to DropTier.UNCOMMON,
    )
    fun dropTier(text: String): DropTier? = tierPrefix.firstOrNull { text.trimStart().startsWith(it.first) }?.second

    // SkyHanni RareDropMessages.kt:52 — colour code after "PET DROP! " is the pet rarity.
    private val pet = Regex("(?:§.)*PET DROP! (?:§.)*§(?<c>.)")
    fun isRarePet(coloredText: String): Boolean = pet.find(coloredText)?.groups?.get("c")?.value in setOf("6", "d")

    // SkyHanni GiftProfitTracker.kt:52
    private val gift = Regex("^(?:SANTA|PARTY) TIER!")
    fun isRareGift(text: String) = gift.containsMatchIn(text.trim())

    // SkyHanni TrophyFishMessages.kt:37
    private val trophy = Regex("TROPHY FISH! You caught an? .+ (GOLD|DIAMOND)!")
    fun isRareTrophy(text: String) = trophy.containsMatchIn(text)

    // SkyHanni HoppityEggsManager.kt:95
    fun isNewRabbit(text: String) = text.trim().startsWith("NEW RABBIT!")

    // SkyOcean VanguardGambling.kt:59-61, widened to all four corpse types.
    private val corpseStartRx = Regex(" +(?:LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT! ?")
    private val corpseTypeRx = Regex(" +(?<type>LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT! ?")
    private val corpseItemRx = Regex(" +(?<item>.+?)(?: x(?<amount>[\\d,]+)|$)")
    private val corpseEndRx = Regex("▬{64}")
    fun corpseStart(text: String) = corpseStartRx.matches(text)
    /** The corpse type ("VANGUARD"/"LAPIS"/"TUNGSTEN"/"UMBER") named in a corpseStart line, or null. */
    fun corpseType(text: String): String? = corpseTypeRx.matchEntire(text)?.groups?.get("type")?.value
    fun corpseEnd(text: String) = corpseEndRx.containsMatchIn(text)
    fun corpseItem(text: String): Pair<String, Int>? {
        val m = corpseItemRx.matchEntire(text) ?: return null
        val amount = m.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
        return m.groups["item"]!!.value to amount
    }
}
