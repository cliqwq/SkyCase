package dev.skycase.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class RarityGateTest {
    @Test fun dropTiers() {
        assertEquals(DropTier.RARE, RarityGate.dropTier("RARE DROP! (Revenant Viscera) (+5% Magic Find)"))
        assertEquals(DropTier.VERY_RARE, RarityGate.dropTier("VERY RARE DROP!  (Revenant Catalyst)"))
        assertEquals(DropTier.CRAZY_RARE, RarityGate.dropTier("CRAZY RARE DROP! (Shadow Warp)"))
        assertEquals(DropTier.PRAY_RNGESUS, RarityGate.dropTier("PRAY TO RNGESUS DROP! (Necron's Handle)"))
        assertNull(RarityGate.dropTier("You dug out a Griffin Burrow!"))
    }
    @Test fun pets() {
        assertTrue(RarityGate.isRarePet("§6§lPET DROP! §r§6Golden Dragon §r§b(+100% ✯ Magic Find)"))
        assertTrue(RarityGate.isRarePet("§d§lPET DROP! §r§dSquid"))
        assertFalse(RarityGate.isRarePet("§9§lPET DROP! §r§9Rock"))
    }
    @Test fun gifts() {
        assertTrue(RarityGate.isRareGift("SANTA TIER! +500 Enchanting XP gift with paysley!"))
        assertTrue(RarityGate.isRareGift("PARTY TIER! +1 North Star"))
        assertFalse(RarityGate.isRareGift("COMMON! +500 Enchanting XP gift with paysley!"))
        assertFalse(RarityGate.isRareGift("RARE! +2,000 Coins gift with x!"))
    }
    @Test fun trophy() {
        assertTrue(RarityGate.isRareTrophy("TROPHY FISH! You caught a Sulphur Skitter DIAMOND!"))
        assertFalse(RarityGate.isRareTrophy("TROPHY FISH! You caught a Sulphur Skitter BRONZE!"))
    }
    @Test fun rabbit() {
        assertTrue(RarityGate.isNewRabbit("NEW RABBIT! +5 Chocolate and +0.1x Chocolate per second!"))
        assertFalse(RarityGate.isNewRabbit("HOPPITY'S HUNT You found a Chocolate Lunch Egg!"))
    }
    @Test fun corpse() {
        assertTrue(RarityGate.corpseStart("  VANGUARD CORPSE LOOT!"))
        assertTrue(RarityGate.corpseStart("  UMBER CORPSE LOOT! "))
        assertFalse(RarityGate.corpseStart("CORPSE LOOT!"))
        assertTrue(RarityGate.corpseEnd("▬".repeat(64)))
        assertEquals("Glacite Jewel" to 3, RarityGate.corpseItem("  Glacite Jewel x3"))
        assertEquals("Shattered Pendant" to 1, RarityGate.corpseItem("  Shattered Pendant"))
    }
    @Test fun corpseType() {
        assertEquals("UMBER", RarityGate.corpseType("  UMBER CORPSE LOOT!"))
        assertEquals("VANGUARD", RarityGate.corpseType("  VANGUARD CORPSE LOOT!"))
        assertEquals("LAPIS", RarityGate.corpseType("  LAPIS CORPSE LOOT! "))
        assertNull(RarityGate.corpseType("CORPSE LOOT!"))
        assertNull(RarityGate.corpseType("  Glacite Jewel x3"))
    }
}
