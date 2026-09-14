package dev.skycase.triggers
import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
import dev.skycase.core.*
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.thatgravyboat.skyblockapi.api.area.dungeon.DungeonAPI
import tech.thatgravyboat.skyblockapi.api.area.slayer.SlayerAPI
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.GenericDataTypes
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.LoreDataTypes
import net.minecraft.world.entity.item.ItemEntity
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.location.IslandChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.location.ServerDisconnectEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo
import tech.thatgravyboat.skyblockapi.utils.extentions.get
import java.util.UUID

/**
 * Corpse loot blocks (VANGUARD/LAPIS/TUNGSTEN/UMBER CORPSE LOOT!) ALWAYS reveal when cfg.corpses is on,
 * regardless of rarity. Everything else (pets/drops/gifts/trophy fish/hoppity) is RARE_ONLY, gated by
 * ChatGates.shouldFire. Held chat lines are always re-shown -- via RevealQueue (normal path), the
 * safety-flush re-show below (corpse block abandoned/too long), or the screen-open skip's emitNow --
 * chat is never silently dropped.
 */
object ChatTrigger {
    // Controller ruling: a corpse block that never reaches its "▬"*64 terminator (e.g. player disconnects
    // mid-block, a chat line is eaten by another mod) must not swallow chat forever.
    private const val CORPSE_MAX_LINES = 40
    private const val CORPSE_TIMEOUT_MS = 10_000L

    // Hoppity's "You found <name> (<rarity>)!" receipt arrives a tick or two before "NEW RABBIT!" --
    // held here so the pair can be joined into one real winner+pool. Same abandoned-block risk as a
    // corpse block (the "NEW RABBIT!" line could in principle never arrive), so it gets the same
    // TickEvent safety-flush treatment below instead of its own timer machinery.
    private const val RABBIT_PENDING_TIMEOUT_MS = 5_000L

    // Dragon fight: 12s post-"DOWN!" inventory-watch window (Task 12 brief). Not a corpse/rabbit-style
    // held block -- the "DOWN!" line is never a spoiler, so it's never cancelled and there's nothing to
    // re-show; only the eventual reveal (if anything was collected) goes through submitOrSkip.
    private const val DRAGON_WINDOW_MS = 12_000L
    private var dragonType: String? = null
    private var dragonStartedAt = 0L
    private var dragonSnapshot: Map<String, Int> = emptyMap()
    private val dragonCollected = mutableListOf<Pair<ItemStack, Int>>() // stack to DragonLoot.rank (or Int.MAX_VALUE for the pet)

    // Task 13: floor-drop item entities seen near the player during the open dragon window, hidden
    // client-side (HiddenEntities) as soon as they're first spotted so the player never sees the drop
    // before the reveal names it. First ranked entity seen arms a 40-tick gather delay (DRAGON_GATHER_
    // DELAY_TICKS) -- ticks, not wall-clock ms, since it's counted off TickEvent itself -- after which
    // the reveal fires immediately using the best-ranked entity seen so far, without waiting for pickup
    // or the 12s inventory-watch window below (that window stays as the fallback for the case where no
    // item entity was ever seen, e.g. it despawned/was out of range before this scan caught it).
    private const val DRAGON_GATHER_DELAY_TICKS = 40
    private const val DRAGON_ENTITY_RANGE = 48.0
    private val dragonItemsSeen = LinkedHashMap<UUID, Pair<ItemStack, Int>>() // uuid -> (stack, rank)
    private var dragonGatherDeadlineTick = -1L
    private var dragonTick = 0L

    // ponytail: filler pool when the chat line names only one item
    private val filler by lazy { listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.ENDER_PEARL, Items.BLAZE_ROD).map(::ItemStack) }

    private var corpse: MutableList<Component>? = null
    private val corpseItems = mutableListOf<ItemStack>()
    private var corpseLines = 0
    private var corpseStartedAt = 0L
    private var corpseType: String? = null

    // The rabbit-name/rarity pair plus the ORIGINAL held "you found" component -- kept local (not
    // pushed into ChatGuard's queue yet) so that when "NEW RABBIT!" arrives, both lines travel
    // together through the exact same held-chat path (submitOrSkip's `held` list) as everything
    // else; splitting it across ChatGuard.hold() early and a local list later would let the
    // screen-open skip (which only emitNow()s the `held` list it's given) leave this one behind.
    private var pendingRabbit: Pair<String, String>? = null
    private var pendingRabbitLine: Component? = null
    private var pendingRabbitAt = 0L

    @Subscription
    fun onChat(event: ChatReceivedEvent.Pre) {
        val cfg = SkyCaseConfig.data
        if (!cfg.enabled) return
        val text = event.text

        // --- Dragon fight "<TYPE> DRAGON DOWN!" (ALWAYS): never a spoiler, never cancelled -- opens a
        // 12s inventory-watch window instead (see onDragonInventoryChange/onTick). Falls through to the
        // rest of onChat same as any other unmatched line. ---
        if (cfg.dragons) {
            ChatParse.dragonDown(event.coloredText)?.let { type ->
                dragonType = type
                dragonStartedAt = System.currentTimeMillis()
                dragonSnapshot = snapshotInventory()
                dragonCollected.clear()
                dragonItemsSeen.clear()
                dragonGatherDeadlineTick = -1L
            }
        }

        // --- Scatha kill reveal (task 15): a pre-roll/roll is already in progress (ScathaTrigger.armed)
        // -- its own "PET DROP! Scatha" line, if it arrives, is captured directly into that reveal's
        // held chat instead of firing a second, separate reveal through the RARE_ONLY path below. ---
        if (cfg.scatha && ScathaTrigger.armed) {
            ChatParse.petDrop(event.coloredText)?.let { (name, _) ->
                if (name == "Scatha") {
                    event.cancel()
                    ScathaTrigger.hold(event.component)
                    return
                }
            }
        }

        // --- corpse block (ALWAYS) ---
        if (cfg.corpses) {
            if (RarityGate.corpseStart(text)) {
                // A block was already open (its terminator never arrived) -- close it out first so its
                // held lines are submitted/re-shown instead of being overwritten and lost.
                corpse?.let { endCorpse(it) }
                corpse = mutableListOf(event.component)
                corpseItems.clear()
                corpseLines = 1
                corpseStartedAt = System.currentTimeMillis()
                corpseType = RarityGate.corpseType(text)
                event.cancel()
                return
            }
            corpse?.let { held ->
                held += event.component
                corpseLines++
                event.cancel()
                RarityGate.corpseItem(text)?.let { (name, amount) -> stackFor(name, amount)?.let(corpseItems::add) }
                if (RarityGate.corpseEnd(text)) {
                    endCorpse(held)
                    return
                }
                if (corpseLines >= CORPSE_MAX_LINES || System.currentTimeMillis() - corpseStartedAt >= CORPSE_TIMEOUT_MS) {
                    endCorpse(held)
                }
                return
            }
        }

        // --- Hoppity "You found <name> (<rarity>)!" receipt: always held (never shown as plain chat)
        // so it can be paired with the "NEW RABBIT!" line that follows; released untouched by the
        // TickEvent safety-flush below if that pairing never completes. ---
        if (cfg.hoppity) {
            ChatParse.rabbitFound(text)?.let { found ->
                // A duplicate egg (no "NEW RABBIT!" in between -- common) would otherwise overwrite
                // the still-pending previous line and lose it silently. emitNow, not hold+release:
                // this can run mid-reveal (another feature's CaseScreen open), and release() would
                // wrongly flip ChatGuard.active off and unhide chat under it.
                pendingRabbitLine?.let { old -> ChatGuard.emitNow(listOf(old)) }
                pendingRabbit = found
                pendingRabbitLine = event.component
                pendingRabbitAt = System.currentTimeMillis()
                event.cancel()
                return
            }
        }

        // --- RARE_ONLY singles ---
        if (!ChatGates.shouldFire(text, event.coloredText, cfg)) return
        // an open dragon window reveals the Ender Dragon pet itself (inventory watch); don't double-fire
        if (dragonType != null && ChatParse.petDrop(event.coloredText)?.first == "Ender Dragon") return
        event.cancel()
        val extraHeld = takePendingRabbitLine(text)
        val (winner, pool) = resolveRareDrop(text, event.coloredText)
        submitOrSkip(pool, winner, listOfNotNull(extraHeld, event.component))
    }

    /** If [text] is "NEW RABBIT!" and a "you found" line is still pending (within the 5s window),
     * consumes and returns its held component so the caller can carry it through the SAME held-chat
     * path as the "NEW RABBIT!" line itself -- see the comment on [pendingRabbitLine]. */
    private fun takePendingRabbitLine(text: String): Component? {
        if (!RarityGate.isNewRabbit(text)) return null
        val fresh = pendingRabbit != null && System.currentTimeMillis() - pendingRabbitAt < RABBIT_PENDING_TIMEOUT_MS
        val line = pendingRabbitLine.takeIf { fresh }
        if (!fresh) { pendingRabbit = null; pendingRabbitLine = null }
        return line
    }

    /** Real winner + pool for a RARE_ONLY single, by source: trophy fish, pet drop, then a paired
     * Hoppity rabbit reveal; the existing generic name-in-text guess (with filler/catacombs/slayer/
     * diana pool and Nether Star fallback) for everything else (gifts, generic rare drops, or any of
     * the above when its real API doesn't resolve). */
    private fun resolveRareDrop(text: String, coloredText: String): Pair<ItemStack, List<ItemStack>> {
        ChatParse.trophy(text)?.let { (fish, tier) ->
            val winner = LootPools.trophyWinner(fish, tier)
            if (winner != null) return winner to (LootPools.trophy(tier) ?: listOf(winner))
        }
        ChatParse.petDrop(coloredText)?.let { (name, rarityName) ->
            val rarity = runCatching { SkyBlockRarity.valueOf(rarityName) }.getOrNull()
            if (rarity != null) {
                val id = name.uppercase().replace(' ', '_')
                val winner = LootPools.petWinner(id, rarity)
                if (winner != null) {
                    val cfg = SkyCaseConfig.data
                    val pool = when {
                        id == "SCATHA" && cfg.scatha -> LootPools.scatha()
                        id == "BABY_YETI" && cfg.yeti -> LootPools.yeti()
                        // Ender Dragon pet: use the fight's pool; the open DOWN! window reveals it itself
                        id == "ENDER_DRAGON" && cfg.dragons -> LootPools.dragon(dragonType ?: "SUPERIOR")
                        else -> LootPools.pets(id, rarity)
                    }
                    return winner to (pool ?: listOf(winner))
                }
            }
        }
        if (RarityGate.isNewRabbit(text) && pendingRabbit != null) {
            val (name, rarityName) = pendingRabbit!!
            pendingRabbit = null
            pendingRabbitLine = null
            val winner = RabbitHeads.forFound(name, rarityName)
            if (winner != null) return winner to (LootPools.rabbits() ?: listOf(winner))
        }
        val winner = itemNamedIn(text) ?: ItemStack(Items.NETHER_STAR).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal(text.take(40)))
        }
        return winner to rareDropPool(text)
    }

    /** Real loot pool for the current source, by priority: catacombs floor, then slayer boss,
     * then a diana "dug out" burrow drop; existing filler when none apply. */
    private fun rareDropPool(text: String): List<ItemStack> {
        DungeonAPI.dungeonFloor?.name?.let { floor -> LootPools.catacombs(floor)?.let { return it } }
        // SlayerAPI.type can stay set to the last slayer fought for a while after the quest ends
        // (no "quest active" flag is checked here) -- accepted: a rare drop line arriving in that
        // window is still overwhelmingly likely to be from that same slayer.
        SlayerAPI.type?.displayName?.let { boss -> LootPools.slayer(boss)?.let { return it } }
        if ("dug out" in text) LootPools.diana()?.let { return it }
        return filler
    }

    // Proactive half of the 10s safety flush: the check in onChat only runs when another chat line
    // arrives, so a corpse block with no more lines coming (disconnect, silent stretch) would otherwise
    // sit open forever. TickEvent fires every client tick regardless of chat activity, so poll it here too.
    @Subscription
    fun onTick(event: TickEvent) {
        corpse?.let { held ->
            if (System.currentTimeMillis() - corpseStartedAt >= CORPSE_TIMEOUT_MS) endCorpse(held)
        }
        // Same abandoned-block safety net as the corpse one above, for the rabbit-found line held
        // while waiting for "NEW RABBIT!": if that line never arrives (e.g. a duplicate egg find, the
        // Hoppity event ending, or the egg turning out not to be a new rabbit for some other reason),
        // show it untouched instead of holding it forever. emitNow, not hold+release: this can run
        // while an unrelated reveal is mid-animation (CaseScreen open, ChatGuard.active true), and
        // release() would wrongly flip `active` off and unhide chat under it.
        if (pendingRabbit != null && System.currentTimeMillis() - pendingRabbitAt >= RABBIT_PENDING_TIMEOUT_MS) {
            pendingRabbit = null
            pendingRabbitLine?.let { ChatGuard.emitNow(listOf(it)) }
            pendingRabbitLine = null
        }
        // Dragon fight: while the window is open, scan for floor-drop item entities every tick (hides
        // them the instant they're seen) and check whether the 40-tick gather delay has elapsed.
        dragonType?.let { type ->
            dragonTick++
            scanDragonItemEntities()
            if (dragonGatherDeadlineTick in 0..dragonTick) {
                val winner = dragonItemsSeen.values.maxByOrNull { it.second }?.first
                closeDragonWindow()
                if (winner != null) {
                    submitOrSkip(LootPools.dragon(type) ?: listOf(winner), winner, emptyList()) { HiddenEntities.clear() }
                } else {
                    HiddenEntities.clear()
                }
                return
            }
        }

        // Dragon fight window close: 12s after "DOWN!" with no gathered item entity, fall back to
        // whatever the inventory watch collected (winner = highest DragonLoot.rank/pet), or nothing at
        // all if the player never picked anything up.
        dragonType?.let { type ->
            if (System.currentTimeMillis() - dragonStartedAt < DRAGON_WINDOW_MS) return@let
            val collected = dragonCollected.toList()
            closeDragonWindow()
            val winner = collected.maxByOrNull { it.second }?.first
            if (winner != null) {
                submitOrSkip(LootPools.dragon(type) ?: listOf(winner), winner, emptyList()) { HiddenEntities.clear() }
            } else {
                HiddenEntities.clear()
            }
        }
    }

    /** Scans nearby rendered item entities for Dragon-pool drops (same rank/pet test as
     * [onDragonInventoryChange]), hiding each the first time it's seen and arming the 40-tick gather
     * delay on the first one found. Render-side only -- never touches the entity or its stack. */
    private fun scanDragonItemEntities() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        for (entity in level.entitiesForRendering()) {
            if (entity !is ItemEntity) continue
            val uuid = entity.uuid
            if (dragonItemsSeen.containsKey(uuid)) continue
            if (entity.distanceToSqr(player) > DRAGON_ENTITY_RANGE * DRAGON_ENTITY_RANGE) continue
            val stack = entity.item
            if (stack.isEmpty) continue
            val name = stack.hoverName.string
            val petId = stack.get(GenericDataTypes.PET_DATA)?.id
            val rank = if (petId == "ENDER_DRAGON") Int.MAX_VALUE else DragonLoot.rank(name)
            if (rank == 0) continue
            dragonItemsSeen[uuid] = stack.copy() to rank
            HiddenEntities.hidden.add(uuid)
            if (dragonGatherDeadlineTick < 0) dragonGatherDeadlineTick = dragonTick + DRAGON_GATHER_DELAY_TICKS
        }
    }

    /** Resets all dragon-window state (both the inventory-watch fallback and the item-entity gather
     * path) without touching [HiddenEntities] -- callers decide separately whether/when to unhide
     * (immediately on abort, or via the reveal's onFinished once it plays). */
    private fun closeDragonWindow() {
        dragonType = null
        dragonSnapshot = emptyMap()
        dragonCollected.clear()
        dragonItemsSeen.clear()
        dragonGatherDeadlineTick = -1L
        dragonTick = 0L
    }

    // Task 13: a dragon window abandoned mid-fight (world change / disconnect) must not leave floor
    // drops hidden forever with nothing left to trigger the unhide.
    @Subscription
    fun onIslandChange(event: IslandChangeEvent) {
        if (dragonType == null) return
        closeDragonWindow()
        HiddenEntities.clear()
    }

    @Subscription
    fun onServerDisconnect(event: ServerDisconnectEvent) {
        if (dragonType == null) return
        closeDragonWindow()
        HiddenEntities.clear()
    }

    // Every inventory slot change is reported here regardless of whether a dragon window is open
    // (SkyblockAPI has no way to subscribe conditionally) -- the dragonType == null check below is the
    // actual gate, so this is a no-op outside the 12s window.
    @Subscription
    fun onDragonInventoryChange(event: PlayerInventoryChangeEvent) {
        val type = dragonType ?: return
        val stack = event.item
        if (stack.isEmpty) return
        val name = stack.hoverName.string
        val petId = stack.get(GenericDataTypes.PET_DATA)?.id
        // pet > Horn/Claw/AOTD/Scale/Scroll/Dye > armor > Fragment > Enchanted Ender Pearl > Ender
        // Pearl > Dragon Essence (DragonLoot.rank) -- rank 0 means "not a Dragon-pool item", ignored.
        val rank = if (petId == "ENDER_DRAGON") Int.MAX_VALUE else DragonLoot.rank(name)
        if (rank == 0) return
        // Dedupe against stacks already present before DOWN!: only an increase over what's already been
        // attributed to this window counts as a genuine pickup (a re-stack/reorder of the same items
        // triggers this event too but leaves the inventory-wide count of that name unchanged).
        val total = snapshotInventory()[name] ?: 0
        if (total <= (dragonSnapshot[name] ?: 0)) return
        dragonSnapshot = dragonSnapshot + (name to total)
        dragonCollected.add(stack.copy() to rank)
    }

    /** Inventory-wide count per stripped display name, main player inventory only (armor pieces land
     * there on pickup, not auto-equipped) -- used both for the DOWN! baseline and every recount after. */
    private fun snapshotInventory(): Map<String, Int> {
        val player = Minecraft.getInstance().player ?: return emptyMap()
        val counts = HashMap<String, Int>()
        for (stack in player.inventory) if (!stack.isEmpty) counts.merge(stack.hoverName.string, stack.count, Int::plus)
        return counts
    }

    /** Ends a corpse block and resets state. Submits a reveal when items were collected (winner =
     * highest rarity); when nothing was collected -- terminator seen on an empty block, a new
     * corpseStart/timeout closing out a stale block, or the line/time cap hit mid-block -- the held
     * lines are re-shown untouched instead. Either way nothing is submitted without items, and chat
     * is never lost. */
    private fun endCorpse(held: List<Component>) {
        val items = corpseItems.toList()
        val type = corpseType
        corpse = null; corpseItems.clear(); corpseLines = 0; corpseStartedAt = 0L; corpseType = null
        val winner = items.maxByOrNull { rarityOf(it) }
        if (winner != null) {
            val pool = type?.let(LootPools::corpse) ?: items
            submitOrSkip(pool, winner, held)
        } else reshow(held)
    }

    // I2 (chat, controller ruling): if a screen is already open (any screen -- a chest GUI, an
    // inventory, another mod's UI) at the moment a single/corpse reveal would be submitted, don't
    // steal it. Emit the held lines immediately instead of queueing an animation over whatever's on
    // screen. The corpse block itself still collects lines/items unconditionally; only the final
    // submit is gated on screen state. Task 14: the reveal is a HUD/screen overlay now, not a screen
    // of its own, so there's no "our own reveal screen" case to exempt any more -- any open screen skips.
    private fun submitOrSkip(pool: List<ItemStack>, winner: ItemStack, held: List<Component>, onFinished: (() -> Unit)? = null) {
        val screen = Minecraft.getInstance().gui.screen()
        // the chat box is not "another GUI": lines land while typing (and /skycase chat runs with it open)
        if (screen != null && screen !is net.minecraft.client.gui.screens.ChatScreen) {
            ChatGuard.emitNow(held)
            // no CaseScreen will play to fire Reveal.onFinished later -- run cleanup (e.g. unhide) now
            onFinished?.invoke()
            return
        }
        RevealQueue.submit(Reveal(pool, winner, held, onFinished))
    }

    private fun reshow(held: List<Component>) {
        held.forEach(ChatGuard::hold)
        ChatGuard.release()
    }

    private fun rarityOf(s: ItemStack) = s.get(LoreDataTypes.RARITY)?.ordinal ?: -1

    private fun stackFor(name: String, amount: Int): ItemStack? = itemByName(name)?.copyWithCount(amount)

    private fun itemByName(name: String): ItemStack? {
        val id = SkyBlockItemsRepo.getIdByName(name) ?: name.uppercase().replace(' ', '_')
        return SkyBlockItemsRepo.getItemStack(id)
    }

    private fun itemNamedIn(text: String): ItemStack? = ChatParse.itemNamedIn(text)?.let(::itemByName)
}
