package dev.skygrab

import com.mojang.brigadier.arguments.StringArgumentType
import dev.skygrab.chat.ChatGuard
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.core.Reveal
import dev.skygrab.core.RevealQueue
import dev.skygrab.triggers.ChatTrigger
import dev.skygrab.triggers.ChestTrigger
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

object SkyGrab : ClientModInitializer {
    const val MOD_ID = "skygrab"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)
    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private val modules: List<Any> = listOf(ChatGuard, ChestTrigger, ChatTrigger, RevealQueue)

    override fun onInitializeClient() {
        SkyGrabConfig.load()
        modules.forEach { SkyBlockAPI.eventBus.register(it) }
        registerCommands()
        LOGGER.info("SkyGrab loaded")
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skygrab").then(
                    ClientCommands.literal("test").executes {
                        val items = listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.NETHERITE_INGOT, Items.ENCHANTED_BOOK)
                            .map { ItemStack(it) }
                        RevealQueue.submit(Reveal(items, ItemStack(Items.NETHER_STAR)))
                        1
                    }
                ).then(
                    // Hypixel-only: can't paste real chat locally, so this feeds a synthetic line straight
                    // into the SkyblockAPI chat pipeline as if the server had sent it.
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
