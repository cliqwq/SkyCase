package dev.skygrab

import dev.skygrab.chat.ChatGuard
import dev.skygrab.config.SkyGrabConfig
import net.fabricmc.api.ClientModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI

object SkyGrab : ClientModInitializer {
    const val MOD_ID = "skygrab"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)
    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private val modules: List<Any> = listOf(ChatGuard)

    override fun onInitializeClient() {
        SkyGrabConfig.load()
        modules.forEach { SkyBlockAPI.eventBus.register(it) }
        LOGGER.info("SkyGrab loaded")
    }
}
