package dev.skycase.core

import dev.skycase.SkyCase
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** Dev diagnostics: `/skycase pool corpse VANGUARD` | `kuudra HOT` | `trophy GOLD` | `dungeon F7 bedrock`. */
object PoolDebug {
    fun dump(spec: String) {
        val parts = spec.trim().split(" ")
        val pool: List<ItemStack>? = when (parts[0].lowercase()) {
            "corpse" -> LootPools.corpse(parts.getOrElse(1) { "VANGUARD" }.uppercase())
            "kuudra" -> LootPools.kuudra(parts.getOrElse(1) { "BASIC" }.uppercase())
            "trophy" -> LootPools.trophy(parts.getOrElse(1) { "GOLD" }.uppercase())
            "dungeon" -> LootPools.dungeonChest(parts.getOrElse(1) { "F7" }.uppercase(), ChestKind.valueOf(parts.getOrElse(2) { "bedrock" }.uppercase()))
            "scatha" -> LootPools.scatha()
            "yeti" -> LootPools.yeti()
            else -> null
        }
        if (pool == null) { SkyCase.LOGGER.info("pool {}: null", spec); return }
        val mc = Minecraft.getInstance()
        val state = ItemStackRenderState()
        val distinct = pool.distinctBy { it.hoverName.string }
        SkyCase.LOGGER.info("pool {}: {} stacks, {} distinct", spec, pool.size, distinct.size)
        for (st in distinct) {
            state.clear()
            mc.itemModelResolver.updateForTopItem(state, st, ItemDisplayContext.GUI, mc.level, null, 0)
            val profile = st.get(DataComponents.PROFILE)
            SkyCase.LOGGER.info(
                "  '{}' item={} model={} profile={} textures={} renderEmpty={}",
                st.hoverName.string, st.item, st.get(DataComponents.ITEM_MODEL),
                profile?.let { it.name().orElse("<no name>") },
                profile?.partialProfile()?.properties?.get("textures")?.size,
                state.isEmpty,
            )
        }
    }
}
