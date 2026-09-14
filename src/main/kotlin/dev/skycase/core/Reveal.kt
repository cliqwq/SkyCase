package dev.skycase.core
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

enum class DropTier { UNCOMMON, RARE, VERY_RARE, CRAZY_RARE, PRAY_RNGESUS }
enum class ChestKind { WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK, KUUDRA_FREE, KUUDRA_PAID }

data class Reveal(
    val pool: List<ItemStack>,
    val winner: ItemStack,
    val heldChat: List<Component> = emptyList(),
    // Task 13: fired once, after the CaseScreen that plays this reveal tears down -- lets a caller
    // (dragon floor-drop hiding) clean up state that must outlive submit() but not the reveal itself.
    val onFinished: (() -> Unit)? = null,
)
