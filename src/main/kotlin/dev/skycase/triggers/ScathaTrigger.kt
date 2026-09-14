package dev.skycase.triggers

import dev.skycase.chat.ChatGuard
import dev.skycase.config.SkyCaseConfig
import dev.skycase.core.HideSet
import dev.skycase.core.LootPools
import dev.skycase.core.Reveal
import dev.skycase.core.RevealQueue
import dev.skycase.core.ScathaTag
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.GenericDataTypes
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.entity.EntityRemovedEvent
import tech.thatgravyboat.skyblockapi.api.events.location.IslandChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.location.ServerDisconnectEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.PlayerInventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.api.location.SkyBlockIsland
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo
import tech.thatgravyboat.skyblockapi.utils.extentions.get
import java.util.UUID

/**
 * Task 15: Scatha kill reveal (design from NamelessJu/Scatha-Pro -- ideas only, no code reused).
 *
 * Pre-roll starts the moment a Scatha name-tag entity's health readout drops to/below
 * [PRE_ROLL_HP_FRACTION] of its max (Scatha-Pro itself keys off the fixed "1❤"; a *fraction* is used
 * here instead since Scatha's max health isn't constant across floors, so a fixed HP cutoff would
 * either never fire on a high-HP Scatha or fire far too early on a low-HP one -- 15% keeps the hidden
 * window short while still comfortably covering the last-hit swing). From pre-roll through the reveal
 * finishing (or a safety timeout), the hotbar/held item/non-mod sounds are hidden via [HideSet] and
 * chat is delayed via [ChatGuard.active] -- purely cosmetic, nothing here touches packets, pickup, or
 * input, and no container screen is ever closed.
 */
object ScathaTrigger {
    // Scatha-Pro's own trigger is the literal "1❤" tag; a threshold survives Scatha's per-floor max
    // HP scaling instead of assuming a fixed number.
    private const val PRE_ROLL_HP_FRACTION = 0.15

    // Kill = the tracked name-tag entity is removed OR its own readout hits 0 within this window of
    // pre-roll; no kill within it (Scatha fled render distance, wasn't actually the one that died,
    // etc.) abandons the hide with no reveal.
    private const val KILL_WINDOW_MS = 10_000L

    // Absolute safety cap on the whole hidden window (pre-roll start -> unhide), regardless of state.
    private const val SAFETY_MS = 15_000L

    // Post-kill inventory-watch window, same shape as ChatTrigger's dragon-fight DRAGON_WINDOW_MS but
    // much shorter since a Scatha drop lands in the inventory near-instantly on kill.
    private const val RESULT_WINDOW_MS = 3_000L

    private const val RANK_PET = 3
    private const val RANK_BRAN = 2
    private const val RANK_GEMSTONE = 1

    private val gemstoneNames = setOf(
        "Fine Topaz Gemstone", "Fine Amethyst Gemstone", "Fine Jade Gemstone", "Fine Amber Gemstone", "Fine Sapphire Gemstone",
    )

    private var trackedUuid: UUID? = null
    private var preRollAt = 0L
    private var killed = false
    private var killedAt = 0L
    private var snapshot: Map<String, Int> = emptyMap()
    private var heldChat = mutableListOf<Component>()
    private val collected = mutableListOf<Pair<ItemStack, Int>>()

    /** True from pre-roll until the reveal resolves (or is abandoned) -- [ChatTrigger] checks this
     * to redirect a "PET DROP! Scatha" line here instead of letting it fire its own separate reveal. */
    val armed: Boolean get() = trackedUuid != null

    /** Hands a held chat component (currently just the Scatha pet-drop line, see [ChatTrigger.onChat])
     * to this reveal's held-chat list, re-shown together with everything else once it resolves. */
    fun hold(c: Component) {
        heldChat.add(c)
    }

    @Subscription
    fun onTick(event: TickEvent) {
        val cfg = SkyCaseConfig.data
        if (!cfg.enabled || !cfg.scatha) {
            if (trackedUuid != null) abandon()
            return
        }
        // Crystal Hollows only on SkyBlock; off SkyBlock (singleplayer test kit) the gate is open
        if (LocationAPI.isOnSkyBlock && LocationAPI.island != SkyBlockIsland.CRYSTAL_HOLLOWS) {
            if (trackedUuid != null) abandon()
            return
        }

        val tracked = trackedUuid
        if (tracked == null) {
            scanForPreRoll()
            return
        }

        val now = System.currentTimeMillis()
        if (!killed) {
            checkTrackedHealth(tracked)
            if (!killed && now - preRollAt >= KILL_WINDOW_MS) {
                abandon()
                return
            }
        }
        if (killed && now - killedAt >= RESULT_WINDOW_MS) {
            resolveResult()
            return
        }
        if (now - preRollAt >= SAFETY_MS) abandon()
    }

    private fun scanForPreRoll() {
        val level = Minecraft.getInstance().level ?: return
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand) continue
            val name = entity.customName?.string ?: continue
            val (hp, max) = ScathaTag.parse(name) ?: continue
            if (max <= 0.0 || hp / max > PRE_ROLL_HP_FRACTION) continue
            startPreRoll(entity.uuid)
            return
        }
    }

    private fun startPreRoll(uuid: UUID) {
        trackedUuid = uuid
        preRollAt = System.currentTimeMillis()
        killed = false
        killedAt = 0L
        heldChat = mutableListOf()
        collected.clear()
        snapshot = snapshotInventory()
        HideSet.hud = true
        HideSet.hand = true
        HideSet.mute = true
        ChatGuard.active = true
    }

    private fun checkTrackedHealth(uuid: UUID) {
        val level = Minecraft.getInstance().level ?: return
        val entity = level.entitiesForRendering().firstOrNull { it.uuid == uuid } as? ArmorStand ?: return
        val name = entity.customName?.string ?: return
        val (hp, _) = ScathaTag.parse(name) ?: return
        if (hp <= 0.0) onKill()
    }

    @Subscription
    fun onEntityRemoved(event: EntityRemovedEvent) {
        if (event.entity.uuid == trackedUuid) onKill()
    }

    private fun onKill() {
        if (killed) return
        killed = true
        killedAt = System.currentTimeMillis()
    }

    @Subscription
    fun onInventoryChange(event: PlayerInventoryChangeEvent) {
        if (trackedUuid == null || !killed) return
        val stack = event.item
        if (stack.isEmpty) return
        val rank = rankOf(stack)
        if (rank == 0) return
        val name = stack.hoverName.string
        val total = snapshotInventory()[name] ?: 0
        if (total <= (snapshot[name] ?: 0)) return // re-stack/reorder, not a new pickup
        snapshot = snapshot + (name to total)
        collected.add(stack.copy() to rank)
    }

    /** pet (SCATHA) > Dwarven O's Block Bran > gemstone; 0 = not a Scatha-pool drop, ignored. */
    private fun rankOf(stack: ItemStack): Int {
        val petId = stack.get(GenericDataTypes.PET_DATA)?.id
        if (petId == "SCATHA") return RANK_PET
        val name = stack.hoverName.string
        if (name == "Dwarven O's Block Bran") return RANK_BRAN
        if (name in gemstoneNames) return RANK_GEMSTONE
        return 0
    }

    /** Winner by priority: best pet/Bran seen (always reveals) > best gemstone seen (only if
     * `scathaEveryKill`) > `Fine Topaz Gemstone` fallback (100% per-kill drop per the wiki -- covers
     * the [RESULT_WINDOW_MS] watch missing a genuine drop, e.g. a full inventory) when
     * `scathaEveryKill` and nothing else arrived. `!scathaEveryKill` with no pet/Bran seen means no
     * reveal at all, per the brief ("false = only pet/Bran kills reveal"). */
    private fun resolveResult() {
        val cfg = SkyCaseConfig.data
        val held = heldChat.toList()
        val col = collected.toList()
        clearState()

        val petOrBran = col.filter { it.second >= RANK_BRAN }.maxByOrNull { it.second }?.first
        val winner = petOrBran ?: if (cfg.scathaEveryKill) {
            col.filter { it.second == RANK_GEMSTONE }.maxByOrNull { it.second }?.first ?: fallbackTopaz()
        } else {
            null
        }

        if (winner == null) {
            unhide()
            reshow(held)
            return
        }
        RevealQueue.submit(Reveal(LootPools.scatha() ?: listOf(winner), winner, held) { unhide() })
    }

    private fun fallbackTopaz(): ItemStack? {
        val id = SkyBlockItemsRepo.getIdByName("Fine Topaz Gemstone") ?: "FINE_TOPAZ_GEMSTONE"
        return SkyBlockItemsRepo.getItemStack(id)
    }

    /** No kill within [KILL_WINDOW_MS], the 15s safety cap, an island change, or a disconnect --
     * unhide immediately and re-show whatever chat was held, same "never silently drop chat" rule
     * every other trigger in this mod follows. */
    private fun abandon() {
        val held = heldChat.toList()
        clearState()
        unhide()
        reshow(held)
    }

    private fun clearState() {
        trackedUuid = null
        killed = false
        killedAt = 0L
        collected.clear()
    }

    private fun unhide() {
        HideSet.hud = false
        HideSet.hand = false
        HideSet.mute = false
    }

    private fun reshow(held: List<Component>) {
        held.forEach(ChatGuard::hold)
        ChatGuard.release()
    }

    // Task 13-style abandoned-window safety net: leaving Crystal Hollows or disconnecting mid pre-roll/
    // roll must not leave the hotbar/hand/sounds hidden forever with nothing left to trigger the unhide.
    @Subscription
    fun onIslandChange(event: IslandChangeEvent) {
        if (trackedUuid != null) abandon()
    }

    @Subscription
    fun onServerDisconnect(event: ServerDisconnectEvent) {
        if (trackedUuid != null) abandon()
    }

    private fun snapshotInventory(): Map<String, Int> {
        val player = Minecraft.getInstance().player ?: return emptyMap()
        val counts = HashMap<String, Int>()
        for (stack in player.inventory) if (!stack.isEmpty) counts.merge(stack.hoverName.string, stack.count, Int::plus)
        return counts
    }
}
