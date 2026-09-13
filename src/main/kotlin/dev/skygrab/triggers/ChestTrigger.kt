package dev.skygrab.triggers
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.core.*
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.LoreDataTypes
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.get

object ChestTrigger {
    private var lastContainerId = -1

    @Subscription
    fun onClose(event: ContainerCloseEvent) {
        lastContainerId = -1
    }

    @Subscription
    fun onContainer(event: ContainerInitializedEvent) {
        val kind = ChestTitles.kind(event.title) ?: return
        val cfg = SkyGrabConfig.data
        val enabled = when (kind) {
            ChestKind.KUUDRA_FREE, ChestKind.KUUDRA_PAID -> cfg.kuudraChests
            else -> cfg.dungeonChests
        }
        if (!enabled) return
        val id = event.screen.menu.containerId
        if (id == lastContainerId) return          // same chest re-rendered
        lastContainerId = id

        val items = event.itemStacks.filter { !it.isEmpty && it.get(LoreDataTypes.RARITY) != null }
        if (items.isEmpty()) return
        val winner = items.maxByOrNull { it.get(LoreDataTypes.RARITY)!!.ordinal } ?: return
        RevealQueue.submit(Reveal(items, winner))
    }
}
