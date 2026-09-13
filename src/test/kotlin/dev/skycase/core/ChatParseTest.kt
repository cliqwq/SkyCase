package dev.skycase.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ChatParseTest {
    @Test fun petLineDoesNotFallBackToParenthetical() {
        assertEquals("Golden Dragon", ChatParse.itemNamedIn("PET DROP! Golden Dragon (+100% ✯ Magic Find)"))
    }
    @Test fun genericDropParenthetical() {
        assertEquals("Wax Stick", ChatParse.itemNamedIn("CRAZY RARE DROP! (Wax Stick)"))
    }
    @Test fun countPrefixOnlyStrippedAtStart() {
        // "x " inside the name itself (not a leading count) must survive.
        assertEquals("Fox Egg", ChatParse.itemNamedIn("(Fox Egg)"))
        assertEquals("Enchanted Diamond", ChatParse.itemNamedIn("(3x Enchanted Diamond)"))
    }
    @Test fun noMatch() {
        assertNull(ChatParse.itemNamedIn("You dug out a Griffin Burrow!"))
    }

    @Test fun trophyParsesFishAndTier() {
        assertEquals("Sulphur Skitter" to "DIAMOND", ChatParse.trophy("TROPHY FISH! You caught a Sulphur Skitter DIAMOND!"))
        assertEquals("Obfuscated-1" to "GOLD", ChatParse.trophy("TROPHY FISH! You caught an Obfuscated-1 GOLD!"))
        assertNull(ChatParse.trophy("You dug out a Griffin Burrow!"))
    }

    @Test fun rabbitFoundParsesNameAndRarity() {
        assertEquals("Arnie" to "COMMON", ChatParse.rabbitFound("HOPPITY'S HUNT You found Arnie (COMMON)!"))
        assertEquals("Aurora" to "DIVINE", ChatParse.rabbitFound("HOPPITY'S HUNT You found Aurora (DIVINE)!"))
        assertNull(ChatParse.rabbitFound("NEW RABBIT! +5 Chocolate"))
    }

    @Test fun petDropMapsColourToRarity() {
        assertEquals("Golden Dragon" to "LEGENDARY", ChatParse.petDrop("§6§lPET DROP! §r§6Golden Dragon §r§b(+100% ✳ Magic Find)"))
        assertEquals("Squid" to "MYTHIC", ChatParse.petDrop("§d§lPET DROP! §r§dSquid"))
        assertEquals("Rock" to "RARE", ChatParse.petDrop("§9§lPET DROP! §r§9Rock"))
        assertNull(ChatParse.petDrop("You dug out a Griffin Burrow!"))
    }
}
