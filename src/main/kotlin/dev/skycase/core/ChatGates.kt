package dev.skycase.core
import dev.skycase.config.SkyCaseConfig

/** Pure gate decision for RARE_ONLY chat singles (pets/drops/gifts/trophy/hoppity). No MC deps -> unit-testable. */
object ChatGates {
    fun shouldFire(text: String, coloredText: String, cfg: SkyCaseConfig.Data): Boolean = when {
        !cfg.enabled -> false
        cfg.pets && RarityGate.isRarePet(coloredText) -> true
        // dedicated mob pets fire at ANY rarity on their own toggle (Scatha RARE/EPIC, Baby Yeti COMMON, Ender Dragon EPIC)
        isSpecialPet(coloredText, cfg) -> true
        cfg.drops && (RarityGate.dropTier(text)?.let { it >= cfg.minDropTier } == true) -> true
        cfg.gifts && RarityGate.isRareGift(text) -> true
        cfg.trophyFish && RarityGate.isRareTrophy(text) -> true
        cfg.hoppity && RarityGate.isNewRabbit(text) -> true
        else -> false
    }

    fun isSpecialPet(coloredText: String, cfg: SkyCaseConfig.Data): Boolean {
        val name = ChatParse.petDrop(coloredText)?.first ?: return false
        return (cfg.scatha && name == "Scatha") || (cfg.yeti && name == "Baby Yeti") || (cfg.dragons && name == "Ender Dragon")
    }
}
