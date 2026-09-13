package dev.skygrab.core
import dev.skygrab.config.SkyGrabConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChatGatesTest {
    private val cfg = SkyGrabConfig.Data()

    @Test fun dropBelowMinTierDoesNotFire() {
        assertFalse(ChatGates.shouldFire("RARE DROP! (Foul Flesh)", "RARE DROP! (Foul Flesh)", cfg.copy(minDropTier = DropTier.VERY_RARE)))
    }

    @Test fun dropAtOrAboveMinTierFires() {
        assertTrue(ChatGates.shouldFire("CRAZY RARE DROP! (Shadow Warp)", "CRAZY RARE DROP! (Shadow Warp)", cfg.copy(minDropTier = DropTier.VERY_RARE)))
    }

    @Test fun commonPetDoesNotFire() {
        assertFalse(ChatGates.shouldFire("PET DROP! Rock", "§9§lPET DROP! §r§9Rock", cfg))
    }

    @Test fun legendaryPetFires() {
        assertTrue(ChatGates.shouldFire("PET DROP! Golden Dragon", "§6§lPET DROP! §r§6Golden Dragon", cfg))
    }

    @Test fun santaGiftFires() {
        assertTrue(ChatGates.shouldFire("SANTA TIER! +500 Enchanting XP gift with paysley!", "SANTA TIER! +500 Enchanting XP gift with paysley!", cfg))
    }

    @Test fun commonGiftDoesNotFire() {
        assertFalse(ChatGates.shouldFire("COMMON! +500 Enchanting XP gift with paysley!", "COMMON! +500 Enchanting XP gift with paysley!", cfg))
    }

    @Test fun disabledModToggleNeverFires() {
        assertFalse(ChatGates.shouldFire("CRAZY RARE DROP! (Shadow Warp)", "CRAZY RARE DROP! (Shadow Warp)", cfg.copy(enabled = false)))
    }

    @Test fun perFeatureTogglesGateTheirOwnFeature() {
        assertFalse(ChatGates.shouldFire("PET DROP! Golden Dragon", "§6§lPET DROP! §r§6Golden Dragon", cfg.copy(pets = false)))
        assertFalse(ChatGates.shouldFire("CRAZY RARE DROP! (Shadow Warp)", "CRAZY RARE DROP! (Shadow Warp)", cfg.copy(drops = false)))
        assertFalse(ChatGates.shouldFire("SANTA TIER! gift", "SANTA TIER! gift", cfg.copy(gifts = false)))
        assertFalse(ChatGates.shouldFire("TROPHY FISH! You caught a Sulphur Skitter DIAMOND!", "TROPHY FISH! You caught a Sulphur Skitter DIAMOND!", cfg.copy(trophyFish = false)))
        assertFalse(ChatGates.shouldFire("NEW RABBIT! +5 Chocolate", "NEW RABBIT! +5 Chocolate", cfg.copy(hoppity = false)))
    }
}
