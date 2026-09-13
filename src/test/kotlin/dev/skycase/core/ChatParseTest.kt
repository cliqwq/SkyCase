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
}
