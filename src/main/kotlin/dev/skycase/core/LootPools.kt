package dev.skycase.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.skycase.SkyCase
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo

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
        resourceJson("dungeon_chests.json").entrySet().associate { (floor, v) ->
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
        val result = if (stack != null && !stack.isEmpty) stack else null
        if (result != null) resolveCache[id] = result
        return result
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
            ChestKind.OBSIDIAN -> "obsidian"
            ChestKind.BEDROCK -> "bedrock"
            else -> return null
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
}
