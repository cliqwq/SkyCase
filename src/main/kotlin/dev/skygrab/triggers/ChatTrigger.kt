package dev.skygrab.triggers
import dev.skygrab.chat.ChatGuard
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.core.*
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.thatgravyboat.skyblockapi.api.datatype.defaults.LoreDataTypes
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.time.TickEvent
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo
import tech.thatgravyboat.skyblockapi.utils.extentions.get

/**
 * Corpse loot blocks (VANGUARD/LAPIS/TUNGSTEN/UMBER CORPSE LOOT!) ALWAYS reveal when cfg.corpses is on,
 * regardless of rarity. Everything else (pets/drops/gifts/trophy fish/hoppity) is RARE_ONLY, gated by
 * ChatGates.shouldFire. Held chat lines are always re-shown -- via RevealQueue (normal path) or the
 * safety-flush re-show below (corpse block abandoned/too long) -- chat is never silently dropped.
 */
object ChatTrigger {
    // Controller ruling: a corpse block that never reaches its "▬"*64 terminator (e.g. player disconnects
    // mid-block, a chat line is eaten by another mod) must not swallow chat forever.
    private const val CORPSE_MAX_LINES = 40
    private const val CORPSE_TIMEOUT_MS = 10_000L

    // ponytail: filler pool when the chat line names only one item
    private val filler by lazy { listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.ENDER_PEARL, Items.BLAZE_ROD).map(::ItemStack) }

    private var corpse: MutableList<Component>? = null
    private val corpseItems = mutableListOf<ItemStack>()
    private var corpseLines = 0
    private var corpseStartedAt = 0L

    @Subscription
    fun onChat(event: ChatReceivedEvent.Pre) {
        val cfg = SkyGrabConfig.data
        if (!cfg.enabled) return
        val text = event.text

        // --- corpse block (ALWAYS) ---
        if (cfg.corpses) {
            if (RarityGate.corpseStart(text)) {
                // A block was already open (its terminator never arrived) -- close it out first so its
                // held lines are submitted/re-shown instead of being overwritten and lost.
                corpse?.let { endCorpse(it, force = true) }
                corpse = mutableListOf(event.component)
                corpseItems.clear()
                corpseLines = 1
                corpseStartedAt = System.currentTimeMillis()
                event.cancel()
                return
            }
            corpse?.let { held ->
                held += event.component
                corpseLines++
                event.cancel()
                RarityGate.corpseItem(text)?.let { (name, amount) -> stackFor(name, amount)?.let(corpseItems::add) }
                if (RarityGate.corpseEnd(text)) {
                    endCorpse(held, force = true)
                    return
                }
                if (corpseLines >= CORPSE_MAX_LINES || System.currentTimeMillis() - corpseStartedAt >= CORPSE_TIMEOUT_MS) {
                    endCorpse(held, force = false)
                }
                return
            }
        }

        // --- RARE_ONLY singles ---
        if (!ChatGates.shouldFire(text, event.coloredText, cfg)) return
        val winner = itemNamedIn(text) ?: ItemStack(Items.NETHER_STAR).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal(text.take(40)))
        }
        event.cancel()
        RevealQueue.submit(Reveal(filler, winner, listOf(event.component)))
    }

    // Proactive half of the 10s safety flush: the check in onChat only runs when another chat line
    // arrives, so a corpse block with no more lines coming (disconnect, silent stretch) would otherwise
    // sit open forever. TickEvent fires every client tick regardless of chat activity, so poll it here too.
    @Subscription
    fun onTick(event: TickEvent) {
        val held = corpse ?: return
        if (System.currentTimeMillis() - corpseStartedAt >= CORPSE_TIMEOUT_MS) {
            endCorpse(held, force = true)
        }
    }

    /** Ends a corpse block and resets state. Submits a reveal when items were collected (winner = highest
     * rarity); when nothing was collected -- whether force=true (terminator seen on an empty block, or a
     * new corpseStart/timeout closing out a stale block) or force=false (line/time cap hit mid-block) --
     * the held lines are re-shown untouched instead. `force` only affects whether an empty collection is
     * still treated as "the block legitimately ended" vs. "abandoned"; either way nothing is submitted
     * without items, and chat is never lost. */
    private fun endCorpse(held: List<Component>, force: Boolean) {
        val items = corpseItems.toList()
        corpse = null; corpseItems.clear(); corpseLines = 0; corpseStartedAt = 0L
        if (force || items.isNotEmpty()) {
            val winner = items.maxByOrNull { rarityOf(it) } ?: run { reshow(held); return }
            RevealQueue.submit(Reveal(items, winner, held))
        } else {
            reshow(held)
        }
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

    private fun itemNamedIn(text: String): ItemStack? {
        val m = Regex("\\(([^)]+)\\)").find(text) ?: Regex("You caught an? (.+?) (?:BRONZE|SILVER|GOLD|DIAMOND)!").find(text) ?: return null
        return itemByName(m.groupValues[1].substringAfter("x ").trim())
    }
}
