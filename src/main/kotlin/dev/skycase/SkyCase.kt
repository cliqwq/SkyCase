package dev.skycase

import com.mojang.brigadier.arguments.StringArgumentType
import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
import dev.skycase.core.CaseOverlay
import dev.skycase.core.Reveal
import dev.skycase.core.RevealQueue
import dev.skycase.triggers.ChatTrigger
import dev.skycase.triggers.ChestTrigger
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

    private val modules: List<Any> = listOf(ChatGuard, ChestTrigger, ChatTrigger, RevealQueue, CaseOverlay)

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
                    // exists because server chat cannot be injected locally; works anywhere -- feeds a
                    // synthetic line straight into the SkyblockAPI chat pipeline as if the server had sent it.
                    ClientCommands.literal("chat").then(
                        ClientCommands.argument("line", StringArgumentType.greedyString()).executes { ctx ->
                            val line = StringArgumentType.getString(ctx, "line")
                            ChatReceivedEvent.Pre(Component.literal(line)).post(SkyBlockAPI.eventBus)
                            1
                        }
                    )
                )
            )
        }
    }
}
