package dev.skycase

import com.mojang.brigadier.arguments.StringArgumentType
import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
import dev.skycase.core.CaseOverlay
import dev.skycase.core.HideSet
import dev.skycase.core.Reveal
import dev.skycase.core.RevealQueue
import dev.skycase.triggers.ChatTrigger
import dev.skycase.triggers.ChestTrigger
import dev.skycase.triggers.ScathaTrigger
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent

object SkyCase : ClientModInitializer {
    const val MOD_ID = "skycase"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)
    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private val modules: List<Any> = listOf(ChatGuard, ChestTrigger, ChatTrigger, RevealQueue, CaseOverlay, HideSet, ScathaTrigger)

    override fun onInitializeClient() {
        SkyCaseConfig.load()
        modules.forEach { SkyBlockAPI.eventBus.register(it) }
        registerCommands()
        LOGGER.info("SkyCase loaded")
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skycase").then(
                    ClientCommands.literal("test").executes {
                        val items = listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.NETHERITE_INGOT, Items.ENCHANTED_BOOK)
                            .map { ItemStack(it) }
                        RevealQueue.submit(Reveal(items, ItemStack(Items.NETHER_STAR)))
                        1
                    }
                ).then(
                    // dev: /skycase pool <corpse|kuudra|trophy|dungeon|scatha|yeti> <key> → logs every distinct stack + whether it renders
                    ClientCommands.literal("pool").then(
                        ClientCommands.argument("spec", StringArgumentType.greedyString()).executes { ctx ->
                            if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
                                dev.skycase.core.PoolDebug.dump(StringArgumentType.getString(ctx, "spec"))
                            }
                            1
                        }
                    )
                ).then(
                    // exists because server chat cannot be injected locally; works anywhere -- feeds a
                    // synthetic line straight into the SkyblockAPI chat pipeline as if the server had sent it.
                    // dev: /skycase pool <corpse|kuudra|trophy> <key> → logs every distinct stack + whether it renders
                    ClientCommands.literal("chat").then(
                        ClientCommands.argument("line", StringArgumentType.greedyString()).executes { ctx ->
                            // the chat box strips '§' on input: accept '&' colour codes instead
                            val line = StringArgumentType.getString(ctx, "line").replace('&', '§')
                            val event = ChatReceivedEvent.Pre(Component.literal(line))
                            val cancelled = event.post(SkyBlockAPI.eventBus)
                            if (!cancelled) net.minecraft.client.Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(Component.literal(line))
                            1
                        }
                    )
                )
            )
        }
    }
}
