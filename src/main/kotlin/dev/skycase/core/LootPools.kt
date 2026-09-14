package dev.skycase.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.skycase.SkyCase
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.item.MissingItemModel
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishTier
import tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishType
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockPetsRepo

/**
 * Real loot pools per source, loaded once from the four JSON files under `/skycase/pools/`
 * (vendored data, see README credits). Ids are resolved to [ItemStack] lazily -- only when a pool is actually
 * requested, since [SkyBlockItemsRepo] needs the item repo to be populated -- and cached per pool.
 *
 * Id resolution: `SkyBlockItemsRepo.getItemStack(id)` (the same call `ChatTrigger.itemByName` uses,
 * `RepoItemCache<String>.getItemStack` under the hood) expects the NEU-style upper-snake key
 * (e.g. "RECOMBOBULATOR_3000"). The vendored JSON carries both that form and SkyOcean's
 * "item:recombobulator_3000" form, so [normalize] strips a leading "item:"/"enchantment:" segment
 * and upper-cases the rest before resolving. Ids that still don't resolve (e.g. `enchantment:`
 * entries -- SkyBlockItemsRepo only serves items, not enchant books) are dropped silently; each
 * pool logs one WARN the first time it is built, listing everything it dropped.
 */
object LootPools {
    private const val MAX_STACKS = 200

    private data class Entry(val id: String, val weight: Int)

    private fun resourceJson(name: String): JsonObject {
        val stream = LootPools::class.java.getResourceAsStream("/skycase/pools/$name")
            ?: error("missing bundled resource /skycase/pools/$name")
        return stream.bufferedReader().use { JsonParser.parseReader(it) }.asJsonObject
    }

    private fun JsonArray.toEntries(): List<Entry> = map { el ->
        val o = el.asJsonObject
        // rows carry either a repo id ("item:foo" / "FOO") or a display name ("name": "Aurora Helmet");
        // names are prefixed so resolve() knows to look them up by name
        val key = o["id"]?.asString ?: ("name:" + o["name"].asString)
        Entry(key, o["weight"].asInt)
    }

    // raw pools, keyed exactly as they appear in the JSON (floor/type/tier/boss names)
    private val dungeonChests: Map<String, Map<String, List<Entry>>> by lazy {
        resourceJson("dungeon_chests.json").entrySet().filter { !it.key.startsWith("_") }.associate { (floor, v) ->
            floor to v.asJsonObject.entrySet().associate { (rarity, arr) -> rarity to arr.asJsonArray.toEntries() }
        }
    }
    private val corpsePools: Map<String, List<Entry>> by lazy {
        resourceJson("corpses.json").entrySet().filter { !it.key.startsWith("_") }
            .associate { (type, arr) -> type to arr.asJsonArray.toEntries() }
    }
    private val kuudraPools: Map<String, List<Entry>> by lazy {
        resourceJson("kuudra.json").entrySet().filter { !it.key.startsWith("_") }
            .associate { (tier, arr) -> tier to arr.asJsonArray.toEntries() }
    }
    private val rareDrops: JsonObject by lazy { resourceJson("rare_drops.json") }
    private val slayerDrops: Map<String, List<String>> by lazy {
        rareDrops["slayer"].asJsonObject.entrySet().associate { (boss, arr) -> boss to arr.asJsonArray.map { it.asString } }
    }
    private val catacombsDrops: Map<String, List<String>> by lazy {
        rareDrops["catacombs"].asJsonObject.entrySet().associate { (floor, arr) -> floor to arr.asJsonArray.map { it.asString } }
    }
    private val dianaDrops: List<String> by lazy { rareDrops["diana"].asJsonArray.map { it.asString } }

    // keyed exactly as they appear in dragon.json's "types" object (PROTECTOR/OLD/WISE/UNSTABLE/STRONG/YOUNG/SUPERIOR)
    private val dragonPools: Map<String, List<Entry>> by lazy {
        resourceJson("dragon.json")["types"].asJsonObject.entrySet().associate { (type, arr) -> type to arr.asJsonArray.toEntries() }
    }
    private val scathaJson: JsonObject by lazy { resourceJson("scatha.json") }
    private val yetiJson: JsonObject by lazy { resourceJson("yeti.json") }

    private val resolveCache = HashMap<String, ItemStack?>()
    private val poolCache = HashMap<String, List<ItemStack>>()
    private val warnedFor = HashSet<String>()

    /** `item:foo` / `enchantment:foo:1` (SkyOcean) -> "FOO"; already-bare NEU ids pass through upper-cased. */
    private fun normalize(id: String): String {
        val afterPrefix = if (":" in id) id.substringAfter(':').substringBefore(':') else id
        return afterPrefix.uppercase()
    }

    // Only successful resolutions are cached. A miss is NOT cached: the repo may simply not be
    // populated yet (loaded async / not synced when this pool is first requested), and caching a
    // permanent `null` here would wrongly and forever drop an id that would resolve fine moments
    // later -- same reasoning as poolCache below.
    private fun resolve(id: String): ItemStack? {
        resolveCache[id]?.let { return it }
        val stack = if (id.startsWith("name:")) {
            val name = id.removePrefix("name:")
            SkyBlockItemsRepo.getIdByName(name)?.let { SkyBlockItemsRepo.getItemStack(it) }
        } else {
            SkyBlockItemsRepo.getItemStack(normalize(id))
        }
        val result = if (stack != null && !stack.isEmpty) renderable(stack) else null
        if (result != null) resolveCache[id] = result
        return result
    }

    /** Hypixel items carry an `item_model` that only exists in the server resource pack. When that
     * model is not loaded (singleplayer, or before the pack applies) the item draws as nothing/magenta:
     * drop the component so the base vanilla item renders instead. Pure client-side cosmetics. */
    fun renderable(stack: ItemStack): ItemStack {
        val modelId = stack.get(DataComponents.ITEM_MODEL) ?: return stack
        val mm = Minecraft.getInstance().modelManager
        if (mm.getItemModel(modelId) !is MissingItemModel) return stack
        // 1.21.4+: the item's default model IS the ITEM_MODEL component; removing it leaves nothing to
        // draw. Point it at the base item's own model (its registry id) instead.
        return stack.copy().apply { set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item)) }
    }

    /** Pure: repeats each item roughly proportional to its weight, total clamped to [max].
     * Scales all weights down so they sum to (at most) [max], rounds each to the nearest integer
     * >=1, then -- if rounding pushed the total slightly over -- trims one copy at a time from
     * whichever item currently has the highest count, never below 1. No JSON/repo access -> safe
     * to unit-test directly. */
    internal fun <T> expandWeighted(items: List<Pair<T, Int>>, max: Int = MAX_STACKS): List<T> {
        if (items.isEmpty()) return emptyList()
        val totalWeight = items.sumOf { it.second.coerceAtLeast(1) }.toDouble()
        val scale = minOf(1.0, max / totalWeight)
        val counts = items.map { (item, weight) -> item to maxOf(1, Math.round(weight.coerceAtLeast(1) * scale).toInt()) }.toMutableList()

        var total = counts.sumOf { it.second }
        while (total > max) {
            val maxIdx = counts.indices.maxByOrNull { counts[it].second } ?: break
            if (counts[maxIdx].second <= 1) break // every item is already at the floor of 1 -- can't shrink further
            counts[maxIdx] = counts[maxIdx].first to counts[maxIdx].second - 1
            total--
        }

        val out = ArrayList<T>(total)
        for ((item, count) in counts) repeat(count) { out.add(item) }
        return out
    }

    /** Returns null (never an empty list) when every id fails to resolve, and does NOT cache that
     * outcome in [poolCache] -- the repo not being ready yet is indistinguishable from "no real
     * pool", so a miss must stay retryable on the next call instead of wrongly caching "no pool"
     * forever and permanently starving callers' `?: items`/filler fallback. */
    private fun buildPool(key: String, entries: List<Entry>): List<ItemStack>? {
        poolCache[key]?.let { return it }
        val dropped = ArrayList<String>()
        val resolved = entries.mapNotNull { e ->
            val stack = resolve(e.id)
            if (stack == null) dropped.add(e.id)
            stack?.let { it to e.weight }
        }
        warnDropped(key, dropped)
        if (resolved.isEmpty()) return null
        return expandWeighted(resolved).also { poolCache[key] = it }
    }

    /** See [buildPool] -- same "never cache an empty/all-dropped result" rule. */
    private fun buildPoolFlat(key: String, ids: List<String>): List<ItemStack>? {
        poolCache[key]?.let { return it }
        val dropped = ArrayList<String>()
        val resolved = ids.mapNotNull { id ->
            val stack = resolve(id)
            if (stack == null) dropped.add(id)
            stack
        }
        warnDropped(key, dropped)
        if (resolved.isEmpty()) return null
        return resolved.take(MAX_STACKS).also { poolCache[key] = it }
    }

    private fun warnDropped(key: String, dropped: List<String>) {
        if (dropped.isEmpty() || !warnedFor.add(key)) return
        SkyCase.LOGGER.warn("LootPools: $key dropped ${dropped.size} unresolved id(s): $dropped")
    }

    fun dungeonChest(floor: String, chest: ChestKind): List<ItemStack>? {
        val rarity = when (chest) {
            ChestKind.WOOD -> "wood"
            ChestKind.GOLD -> "gold"
            ChestKind.DIAMOND -> "diamond"
            ChestKind.EMERALD -> "emerald"
            ChestKind.OBSIDIAN -> "obsidian"
            ChestKind.BEDROCK -> "bedrock"
            else -> return null // Kuudra kinds use kuudra()
        }
        val entries = dungeonChests[floor]?.get(rarity) ?: return null
        return buildPool("dungeon:$floor:$rarity", entries)
    }

    fun corpse(type: String): List<ItemStack>? {
        val entries = corpsePools[type] ?: return null
        return buildPool("corpse:$type", entries)
    }

    fun kuudra(tier: String): List<ItemStack>? {
        val entries = kuudraPools[tier] ?: return null
        return buildPool("kuudra:$tier", entries)
    }

    fun slayer(boss: String): List<ItemStack>? {
        val ids = slayerDrops[boss] ?: return null
        return buildPoolFlat("slayer:$boss", ids)
    }

    fun catacombs(floor: String): List<ItemStack>? {
        val ids = catacombsDrops[floor] ?: return null
        return buildPoolFlat("catacombs:$floor", ids)
    }

    fun diana(): List<ItemStack>? {
        if (dianaDrops.isEmpty()) return null
        return buildPoolFlat("diana", dianaDrops)
    }

    /** Real loot pool for an Ender Dragon fight, by dragon type key ("OLD"/"PROTECTOR"/"WISE"/
     * "UNSTABLE"/"STRONG"/"YOUNG"/"SUPERIOR" -- see [ChatParse.dragonDown]). The Ender Dragon pet
     * itself isn't in here (mob-drop pets are only added to [pets]' own-species pools); a dragon-fight
     * pet win still reveals against this armor/fragment/pearl pool per the brief. */
    fun dragon(type: String): List<ItemStack>? {
        val entries = dragonPools[type] ?: return null
        return buildPool("dragon:$type", entries)
    }

    fun scatha(): List<ItemStack>? = mobDropPool("scatha", scathaJson, "SCATHA")
    fun yeti(): List<ItemStack>? = mobDropPool("yeti", yetiJson, "BABY_YETI")

    /** [json]'s "items" array (same name/weight rows [buildPool] takes) plus its "pets" map
     * (rarity name -> weight), each rarity resolved to a [petId] pet stack via [SkyBlockPetsRepo] at
     * level 1 -- same "don't cache a starved result" rule as [buildPool] (a repo not yet populated
     * must stay retryable, not get permanently cached as "no pool"). */
    private fun mobDropPool(key: String, json: JsonObject, petId: String): List<ItemStack>? {
        poolCache[key]?.let { return it }
        val dropped = ArrayList<String>()
        val itemPairs = json["items"].asJsonArray.toEntries().mapNotNull { e ->
            val stack = resolve(e.id)
            if (stack == null) dropped.add(e.id)
            stack?.let { it to e.weight }
        }
        warnDropped(key, dropped)
        val petPairs = json["pets"].asJsonObject.entrySet().mapNotNull { (rarityName, weight) ->
            val rarity = runCatching { SkyBlockRarity.valueOf(rarityName) }.getOrNull() ?: return@mapNotNull null
            if (SkyBlockPetsRepo.get(petId) == null) return@mapNotNull null
            SkyBlockPetsRepo.getItemStack { this.id = petId; this.rarity = rarity; this.level = 1 }?.let { it to weight.asInt }
        }
        val all = itemPairs + petPairs
        if (all.isEmpty()) return null
        return expandWeighted(all).also { poolCache[key] = it }
    }

    // --- Trophy fish: tech.thatgravyboat.skyblockapi.api.area.isle.trophyfish.TrophyFishType/Tier
    // build the fish-head ItemStack directly (confirmed via javap on the 4.2.22-26.2 api jar and the
    // SkyblockAPI HEAD source of TrophyFishType.kt) -- no SkyBlockItemsRepo id lookup needed at all,
    // so there's no id-resolution fallback path for trophy fish. ---

    /** Winner for a trophy fish catch: [fish] must match [TrophyFishType.getDisplayName] exactly
     * ("Sulphur Skitter", "Obfuscated-1", ...) and [tier] one of "BRONZE"/"SILVER"/"GOLD"/"DIAMOND".
     * Null (caller falls back to Nether Star) when either doesn't resolve. */
    fun trophyWinner(fish: String, tier: String): ItemStack? {
        val type = TrophyFishType.getByDisplayName(fish) ?: return null
        val t = runCatching { TrophyFishTier.valueOf(tier.uppercase()) }.getOrNull() ?: return null
        return type.getItem(t)
    }

    /** All 18 trophy fish at the same [tier]. */
    fun trophy(tier: String): List<ItemStack>? {
        val t = runCatching { TrophyFishTier.valueOf(tier.uppercase()) }.getOrNull() ?: return null
        return TrophyFishType.entries.map { it.getItem(t) }
    }

    // --- Pets: tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockPetsRepo (this version's real
    // name for what the brief called RepoPetsAPI -- confirmed via the SkyblockAPI HEAD source of
    // SkyBlockPetsRepo.kt, which literally does `data.tiers()[key.rarity.name]`, i.e. the repo's
    // per-pet tier map is keyed by SkyBlockRarity.name). `SkyBlockPetsRepo.get(id)` is the "does this
    // pet exist" check (brief's getPetInfo); getItemStack takes a `Query.() -> Unit` builder. ---

    private val mobDropPets = listOf("ENDER_DRAGON", "BABY_YETI", "SCATHA", "LOCH_EMPEROR") // hypixelskyblock.minecraft.wiki/Pet 2026-09-13

    /** Winner for a PET DROP line: null (Nether Star fallback) when [id] isn't a known pet. */
    fun petWinner(id: String, rarity: SkyBlockRarity): ItemStack? {
        SkyBlockPetsRepo.get(id) ?: return null
        return SkyBlockPetsRepo.getItemStack { this.id = id; this.rarity = rarity; this.level = 1 }
    }

    /** The same pet at every rarity it exists in, plus the LEGENDARY mob-drop pets, uniform weight. */
    fun pets(id: String, rarity: SkyBlockRarity): List<ItemStack>? {
        poolCache["pet:$id"]?.let { return it }
        val data = SkyBlockPetsRepo.get(id) ?: return null
        val sameSpecies = data.tiers().keys.mapNotNull { key -> runCatching { SkyBlockRarity.valueOf(key) }.getOrNull() }.distinct()
            .mapNotNull { r -> SkyBlockPetsRepo.getItemStack { this.id = id; this.rarity = r; this.level = 1 } }
        val mobDrops = mobDropPets.mapNotNull { mid ->
            if (SkyBlockPetsRepo.get(mid) == null) return@mapNotNull null
            SkyBlockPetsRepo.getItemStack { this.id = mid; this.rarity = SkyBlockRarity.LEGENDARY; this.level = 1 }
        }
        val all = (sameSpecies + mobDrops).map { it to 1 }
        // Same "don't cache a starved result" rule as buildPool/buildPoolFlat: an empty result here
        // means the repo wasn't ready yet, not "this pet truly has no pool" -- must stay retryable.
        if (all.isEmpty()) return null
        return expandWeighted(all).also { poolCache["pet:$id"] = it }
    }

    // --- Hoppity rabbits: see RabbitHeads.kt for head construction; textures vendored from
    // SkyHanni-REPO's HoppityRabbitTextures.json (README credits). ---

    // Rarity weights per the brief (hypixelskyblock.minecraft.wiki rabbit drop rates, 2026-09-13).
    private val rabbitRarityWeight = linkedMapOf(
        "COMMON" to 40, "UNCOMMON" to 25, "RARE" to 15, "EPIC" to 10, "LEGENDARY" to 6, "MYTHIC" to 3, "DIVINE" to 1,
    )
    private const val RABBITS_PER_RARITY = 10

    /** Weighted pool of rabbit heads across all rarities: up to [RABBITS_PER_RARITY] names per
     * rarity (there are up to 224 per rarity -- capping keeps this cheap and, after
     * [expandWeighted]'s scaling, the final pool still lands at/under [MAX_STACKS]). */
    fun rabbits(): List<ItemStack>? {
        poolCache["rabbits"]?.let { return it }
        val names = RabbitHeads.namesByRarity()
        val entries = rabbitRarityWeight.flatMap { (rarityName, weight) ->
            val rarity = runCatching { SkyBlockRarity.valueOf(rarityName) }.getOrNull() ?: return@flatMap emptyList()
            (names[rarityName] ?: emptyList()).take(RABBITS_PER_RARITY).mapNotNull { name ->
                val base64 = RabbitHeads.textureOf(name) ?: return@mapNotNull null
                RabbitHeads.head(name, rarity, base64) to weight
            }
        }
        if (entries.isEmpty()) return null
        return expandWeighted(entries).also { poolCache["rabbits"] = it }
    }
}
