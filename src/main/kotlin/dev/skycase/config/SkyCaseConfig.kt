package dev.skycase.config
import com.google.gson.GsonBuilder
import dev.skycase.SkyCase
import dev.skycase.core.DropTier
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.*

object SkyCaseConfig {
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
    private val path = FabricLoader.getInstance().configDir.resolve("skycase.json")
    var data = Data(); private set

    fun load() {
        data = runCatching { if (path.exists()) gson.fromJson(path.readText(), Data::class.java) else Data() }
            .getOrElse { SkyCase.LOGGER.warn("bad config, using defaults", it); Data() }
        // Gson bypasses the constructor default when a key is absent/null in the JSON, so a Kotlin
        // non-null field can still come back null at runtime -- guard and clamp before anything reads it.
        @Suppress("SENSELESS_COMPARISON")
        if (data.minDropTier == null) data.minDropTier = DropTier.VERY_RARE
        if (data.durationMs < 500) data.durationMs = 500
        save()
    }
    fun save() {
        try {
            path.parent.createDirectories()
            path.writeText(gson.toJson(data))
        } catch (e: Exception) {
            SkyCase.LOGGER.warn("Failed to save config", e)
        }
    }
}
