package dev.skycase.core

import com.google.common.collect.HashMultimap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import java.util.UUID

/**
 * Builds cosmetic Hoppity rabbit heads: a `PLAYER_HEAD` [ItemStack] carrying the rabbit's real skin
 * texture (vendored from SkyHanni-REPO, see README credits) plus a rarity-coloured custom name.
 *
 * 26.2 API (confirmed via `javap` on the loom `minecraft-merged.jar`, named mappings):
 * `net.minecraft.core.component.DataComponents.PROFILE : DataComponentType<ResolvableProfile>`,
 * and `ResolvableProfile` has no public constructor taking a texture directly -- the only public
 * entry point is `ResolvableProfile.createResolved(GameProfile): ResolvableProfile` (a `GameProfile`
 * already fully resolved, so it round-trips with no network call). `GameProfile`
 * (com.mojang.authlib.GameProfile, authlib 9.0.75) is `record GameProfile(UUID, String, PropertyMap)`;
 * `PropertyMap` (`class PropertyMap extends ForwardingMultimap<String, Property>`) exposes the
 * ordinary `Multimap.put` so a "textures" property (`Property(String, String)`, the base64 skin blob
 * as its `value`) can be added directly.
 */
object RabbitHeads {
    private data class TextureEntry(val rabbits: List<String>, val base64: String)

    // rarity name (upper) -> its texture entries, as vendored from SkyHanni-REPO's
    // HoppityRabbitTextures.json ({"textures": {"<rarity lower>": [{"rabbits": [...], "texture_value_b64": ...}]}})
    private val texturesByRarity: Map<String, List<TextureEntry>> by lazy {
        val root = resourceJson("hoppity_textures.json")["textures"].asJsonObject
        root.entrySet().associate { (rarity, arr) ->
            rarity.uppercase() to arr.asJsonArray.map { el ->
                val o = el.asJsonObject
                TextureEntry(o["rabbits"].asJsonArray.map { it.asString }, o["texture_value_b64"].asString)
            }
        }
    }

    // rabbit name (lowercase) -> base64 texture, flattened from [texturesByRarity] for O(1) lookup
    // by the name a chat line names -- multiple names can share one texture entry (reused skins).
    private val textureByName: Map<String, String> by lazy {
        buildMap {
            texturesByRarity.values.flatten().forEach { entry ->
                entry.rabbits.forEach { name -> put(name.lowercase(), entry.base64) }
            }
        }
    }

    private fun resourceJson(name: String): JsonObject {
        val stream = RabbitHeads::class.java.getResourceAsStream("/skycase/pools/$name")
            ?: error("missing bundled resource /skycase/pools/$name")
        return stream.bufferedReader().use { JsonParser.parseReader(it) }.asJsonObject
    }

    /** Rabbit names that have a known texture, grouped by rarity (upper-snake, e.g. "LEGENDARY"). */
    fun namesByRarity(): Map<String, List<String>> = texturesByRarity.mapValues { (_, entries) -> entries.flatMap { it.rabbits } }

    /** Base64 skin texture for a rabbit name (case-insensitive), or null if unknown. */
    fun textureOf(name: String): String? = textureByName[name.lowercase()]

    /** Builds the head stack for a rabbit name + rarity, given its base64 skin texture. Pure MC-item
     * construction -- no repo/network access. */
    fun head(name: String, rarity: SkyBlockRarity, base64: String): ItemStack {
        val properties = PropertyMap(HashMultimap.create())
        properties.put("textures", Property("textures", base64))
        val profile = GameProfile(UUID.randomUUID(), name, properties)
        return ItemStack(Items.PLAYER_HEAD).apply {
            set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
            set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rarity.color))))
        }
    }

    /** Winner for a "you found <name> (<rarity>)" + "NEW RABBIT!" pair. Null when the name has no
     * known texture (not in the vendored SkyHanni-REPO data) or the rarity word doesn't parse --
     * caller falls back to the generic path. */
    fun forFound(name: String, rarityName: String): ItemStack? {
        val base64 = textureOf(name) ?: return null
        val rarity = runCatching { SkyBlockRarity.valueOf(rarityName.uppercase()) }.getOrNull() ?: return null
        return head(name, rarity, base64)
    }
}
