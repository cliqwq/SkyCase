package dev.skycase.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DragonLootTest {
    @Test fun priorityOrderPerBrief() {
        // pet > Horn/Claw/AOTD/Scale/Scroll/Dye > armor piece > Fragment > Enchanted Ender Pearl >
        // Ender Pearl > Dragon Essence (pet itself is ranked by the caller, not DragonLoot).
        val special = DragonLoot.rank("Dragon Horn")
        val armor = DragonLoot.rank("Superior Dragon Helmet")
        val fragment = DragonLoot.rank("Dragon Fragment")
        val enchantedPearl = DragonLoot.rank("Enchanted Ender Pearl")
        val pearl = DragonLoot.rank("Ender Pearl")
        val essence = DragonLoot.rank("Dragon Essence")

        assertTrue(special > armor, "special > armor")
        assertTrue(armor > fragment, "armor > fragment")
        assertTrue(fragment > enchantedPearl, "fragment > enchanted pearl")
        assertTrue(enchantedPearl > pearl, "enchanted pearl > pearl")
        assertTrue(pearl > essence, "pearl > essence")
        assertTrue(essence > 0, "essence still ranks above unknown items")
    }

    @Test fun specialTierNamesAllRankEqual() {
        val names = listOf("Dragon Horn", "Dragon Claw", "Aspect of the Dragons", "Dragon Scale", "Travel Scroll to Dragon's Nest", "Pearlescent Dye")
        val ranks = names.map(DragonLoot::rank).distinct()
        assertEquals(1, ranks.size, "all special-tier names should rank equal: $names -> ${names.map(DragonLoot::rank)}")
    }

    @Test fun armorPiecesAcrossTypesRankEqual() {
        val ranks = listOf("Old Dragon Helmet", "Superior Dragon Chestplate", "Young Dragon Leggings", "Protector Dragon Boots").map(DragonLoot::rank).distinct()
        assertEquals(1, ranks.size)
    }

    @Test fun unknownNameRanksZero() {
        assertEquals(0, DragonLoot.rank("Diamond"))
        assertEquals(0, DragonLoot.rank("Ender Dragon")) // the pet's own name -- caller ranks it separately
    }
}
