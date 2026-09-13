package dev.skygrab.config
import com.google.gson.GsonBuilder
import dev.skygrab.SkyGrab
import dev.skygrab.core.DropTier
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.*

object SkyGrabConfig {
    data class Data(
        var enabled: Boolean = true,
        var durationMs: Int = 4000,
        var hideChat: Boolean = true,
        var dungeonChests: Boolean = true,
        var kuudraChests: Boolean = true,
        var corpses: Boolean = true,
        var pets: Boolean = true,
        var drops: Boolean = true,
        var minDropTier: DropTier = DropTier.VERY_RARE,
        var gifts: Boolean = true,
        var trophyFish: Boolean = true,
        var hoppity: Boolean = true,
    )
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("skygrab.json")
    var data = Data(); private set

    fun load() {
        data = runCatching { if (path.exists()) gson.fromJson(path.readText(), Data::class.java) else Data() }
            .getOrElse { SkyGrab.LOGGER.warn("bad config, using defaults", it); Data() }
        save()
    }
    fun save() { path.parent.createDirectories(); path.writeText(gson.toJson(data)) }
}
