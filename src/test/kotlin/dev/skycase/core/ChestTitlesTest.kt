package dev.skycase.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ChestTitlesTest {
    @Test fun dungeon() {
        assertEquals(ChestKind.BEDROCK, ChestTitles.kind("Bedrock Chest"))
        assertEquals(ChestKind.BEDROCK, ChestTitles.kind("Bedrock"))
        assertEquals(ChestKind.WOOD, ChestTitles.kind("Wood Chest"))
    }
    @Test fun kuudra() {
        assertEquals(ChestKind.KUUDRA_FREE, ChestTitles.kind("Free Chest"))
        assertEquals(ChestKind.KUUDRA_FREE, ChestTitles.kind("Free Chest Chest"))
        assertEquals(ChestKind.KUUDRA_PAID, ChestTitles.kind("Paid Chest Chest"))
    }
    @Test fun other() { assertNull(ChestTitles.kind("Croesus")); assertNull(ChestTitles.kind("Ender Chest")) }
}
