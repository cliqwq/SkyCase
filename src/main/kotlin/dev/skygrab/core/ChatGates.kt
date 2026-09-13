package dev.skygrab.core
import dev.skygrab.config.SkyGrabConfig

/** Pure gate decision for RARE_ONLY chat singles (pets/drops/gifts/trophy/hoppity). No MC deps -> unit-testable. */
object ChatGates {
    fun shouldFire(text: String, coloredText: String, cfg: SkyGrabConfig.Data): Boolean = when {
        !cfg.enabled -> false
        cfg.pets && RarityGate.isRarePet(coloredText) -> true
        cfg.drops && (RarityGate.dropTier(text)?.let { it >= cfg.minDropTier } == true) -> true
        cfg.gifts && RarityGate.isRareGift(text) -> true
        cfg.trophyFish && RarityGate.isRareTrophy(text) -> true
        cfg.hoppity && RarityGate.isNewRabbit(text) -> true
        else -> false
    }
}
