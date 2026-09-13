package dev.skygrab.core
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

enum class DropTier { UNCOMMON, RARE, VERY_RARE, CRAZY_RARE, PRAY_RNGESUS }
enum class ChestKind { WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK, KUUDRA_FREE, KUUDRA_PAID }

data class Reveal(
    val pool: List<ItemStack>,
    val winner: ItemStack,
    val heldChat: List<Component> = emptyList(),
)
