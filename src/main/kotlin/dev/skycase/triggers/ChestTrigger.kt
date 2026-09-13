package dev.skycase.triggers
import dev.skycase.config.SkyCaseConfig
import dev.skycase.core.*
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.area.dungeon.DungeonAPI
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.LoreDataTypes
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.get

object ChestTrigger {
    private var lastContainerId = -1

    // Skyblocker ChestValue.java:64-70 -- the chest's Kuudra-tier key item, longest/most-specific
    // prefix first so "Hot" doesn't shadow "Burning"/"Fiery"/"Infernal".
    private val kuudraTierByKeyPrefix = listOf(
        "Infernal" to "INFERNAL", "Fiery" to "FIERY", "Burning" to "BURNING", "Hot" to "HOT",
    )

    /** BASIC when there's no key item at all (Free Chest) or the key is the plain "Kuudra Key". */
    private fun kuudraTier(items: List<ItemStack>): String {
        val keyName = items.map { it.hoverName.string }.firstOrNull { it.endsWith("Kuudra Key") } ?: return "BASIC"
        return kuudraTierByKeyPrefix.firstOrNull { keyName.startsWith(it.first) }?.second ?: "BASIC"
    }

    @Subscription
    fun onClose(event: ContainerCloseEvent) {
        lastContainerId = -1
    }

    @Subscription
    fun onContainer(event: ContainerInitializedEvent) {
        val kind = ChestTitles.kind(event.title) ?: return
        val cfg = SkyCaseConfig.data
        val enabled = when (kind) {
            ChestKind.KUUDRA_FREE, ChestKind.KUUDRA_PAID -> cfg.kuudraChests
            else -> cfg.dungeonChests
        }
        if (!enabled) return
        // Hypixel issues a fresh containerId every time a chest is opened server-side, so re-opening
        // the same physical chest after closing it gets a new id here and replays the reveal (accepted).
        val id = event.screen.menu.containerId
        if (id == lastContainerId) return          // same chest re-rendered
        lastContainerId = id

        val items = event.itemStacks.filter { !it.isEmpty && it.get(LoreDataTypes.RARITY) != null }
        if (items.isEmpty()) return
        val winner = items.maxByOrNull { it.get(LoreDataTypes.RARITY)!!.ordinal } ?: return

        val pool = when (kind) {
            ChestKind.KUUDRA_FREE, ChestKind.KUUDRA_PAID -> LootPools.kuudra(kuudraTier(event.itemStacks))
            else -> DungeonAPI.dungeonFloor?.name?.let { floor -> LootPools.dungeonChest(floor, kind) }
        } ?: items
        RevealQueue.submit(Reveal(pool, winner))
    }
}
