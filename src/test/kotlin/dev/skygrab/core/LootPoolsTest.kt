package dev.skygrab.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LootPoolsTest {
    // --- pure weighted expansion: no JSON/repo access, safe outside a Minecraft runtime ---
    @Test fun expandWeightedKeepsProportions() {
        val out = LootPools.expandWeighted(listOf("a" to 1, "b" to 3))
        assertEquals(4, out.size)
        assertEquals(1, out.count { it == "a" })
        assertEquals(3, out.count { it == "b" })
    }

    @Test fun expandWeightedClampsToMax() {
        val out = LootPools.expandWeighted(listOf("a" to 500), max = 200)
        assertEquals(200, out.size)
    }

    // --- unknown keys resolve to null purely via map lookup, before any repo access ---
    @Test fun unknownKeysReturnNull() {
        assertNull(LootPools.dungeonChest("F99", ChestKind.OBSIDIAN))
        assertNull(LootPools.dungeonChest("F1", ChestKind.WOOD)) // only obsidian/bedrock are stocked
        assertNull(LootPools.corpse("MITHRIL"))
        assertNull(LootPools.kuudra("NIGHTMARE"))
        assertNull(LootPools.slayer("Some Boss"))
        assertNull(LootPools.catacombs("F99"))
    }

    // --- bundled resources parse and aren't empty (no repo access -- reads the raw JSON directly) ---
    private fun resource(name: String) =
        LootPools::class.java.getResourceAsStream("/skygrab/pools/$name")!!.bufferedReader().use { JsonParser.parseReader(it) }

    @Test fun dungeonChestsJsonParses() {
        val obj = resource("dungeon_chests.json").asJsonObject
        assertTrue(obj.size() > 0)
        assertTrue(obj["F1"].asJsonObject["obsidian"].asJsonArray.size() > 0)
    }

    @Test fun corpsesJsonParses() {
        val obj = resource("corpses.json").asJsonObject
        for (type in listOf("VANGUARD", "LAPIS", "TUNGSTEN", "UMBER")) {
            assertTrue(obj[type].asJsonArray.size() > 0, "$type should be non-empty")
        }
    }

    @Test fun kuudraJsonParses() {
        val obj = resource("kuudra.json").asJsonObject
        for (tier in listOf("BASIC", "HOT", "BURNING", "FIERY", "INFERNAL")) {
            assertTrue(obj[tier].asJsonArray.size() > 0, "$tier should be non-empty")
        }
    }

    @Test fun rareDropsJsonParses() {
        val obj = resource("rare_drops.json").asJsonObject
        assertTrue(obj["slayer"].asJsonObject.size() > 0)
        assertTrue(obj["catacombs"].asJsonObject.size() > 0)
        assertTrue(obj["diana"].asJsonArray.size() > 0)
    }
}
