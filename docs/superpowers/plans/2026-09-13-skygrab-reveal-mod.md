# SkyGrab Reveal Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fabric client mod for Hypixel SkyBlock that replays rewards the player already received as a CS:GO-style case-scroll animation, with chat hidden while it plays.

**Architecture:** Triggers (GUI-title or chat-regex) build a `Reveal` (item pool + winner) and hand it to `RevealQueue`, which opens `CaseScreen` one at a time and tells `ChatGuard` to hide the chat HUD and hold the spoiler line until the animation ends. Rarity gating is pure string functions on Hypixel's own chat labels — no price lookups, no server interaction, no automation.

**Tech Stack:** Fabric Loader ≥0.16.10, Minecraft 26.2 (Stonecutter, 26.1.2 second target), Kotlin 2.4 + fabric-language-kotlin, SkyblockAPI 4.2.x (`tech.thatgravyboat:skyblock-api`, MIT, maven.teamresourceful.com), Gson (bundled with MC) for config, JUnit 5 for pure logic.

**Spec:** this file, section "Spec" below (research: `C:\Users\addicteddd\.claude\plans\your-job-you-bubbly-kitten.md` + session findings 2026-09-13).

## Global Constraints

- Purely cosmetic. Never click, never buy, never reroll, never send packets, never read prices. Hypixel rule applied: "Aesthetic Modifications … without modifying gameplay"; "anything which automates any player gameplay action is strictly disallowed" (support.hypixel.net article 6472550754962).
- Chat is never dropped. Chat HUD draw is cancelled while a reveal plays; the trigger line is re-shown afterwards.
- Reference SkyOcean *code* only (MIT per `LICENSE.md` §1). Never copy SkyOcean textures/assets (ARR per §2). Never look at Ev-Hoang/Bedrock-Chest-Case-Opening (all rights reserved).
- Package `dev.skygrab`, mod id `skygrab`. Licence: MIT.
- One screen style for MVP (`CaseScreen`). Slot-machine style is out of scope.
- Every regex is copied verbatim from the receipts in the Spec; do not "improve" them.

---

## Spec

### Trigger modes

| Mode | Triggers | Gate |
|---|---|---|
| ALWAYS | Dungeon chest (Wood/Gold/Diamond/Emerald/Obsidian/Bedrock, opened in dungeon or Croesus); Kuudra Free/Paid Chest; Mineshaft corpse loot (Lapis/Tungsten/Umber/Vanguard) | none |
| RARE_ONLY | Pet drop; generic drop line; Winter gift; Trophy fish; Hoppity new rabbit | see gate table |

### Gate table (Hypixel's own labels; receipts = SkyHanni HEAD 2026-09-13 unless noted)

| Trigger | Fires when | Receipt |
|---|---|---|
| Generic drop | prefix is `VERY RARE DROP!`, `CRAZY RARE DROP!`, `PRAY TO RNGESUS DROP!` (plain `RARE DROP!` is junk-tier, SkyHanni filters it) | `features/garden/tracker/RngDropEnum.kt:4-7`, `features/chat/ChatFilter.kt:189-196` |
| Pet drop | `PET DROP!` and pet name colour `§6` (LEGENDARY) or `§d` (MYTHIC) | `features/chat/RareDropMessages.kt:52` |
| Winter gift | `SANTA TIER!` or `PARTY TIER!` | `features/gifting/GiftProfitTracker.kt:44-52` |
| Trophy fish | rarity `GOLD` or `DIAMOND` | `features/fishing/trophy/TrophyFishMessages.kt:37` |
| Hoppity | `NEW RABBIT!` | `features/event/hoppity/HoppityEggsManager.kt:95` |
| Dungeon chest titles | `Wood Chest`, `Wood`, … `Bedrock Chest`, `Bedrock` | Skyblocker `skyblock/ChestValue.java:59` |
| Kuudra chest titles | `Free Chest`, `Free Chest Chest`, `Paid Chest`, `Paid Chest Chest` | Skyblocker `ChestValue.java:63` |
| Corpse loot block | start ` +(?:LAPIS\|TUNGSTEN\|UMBER\|VANGUARD) CORPSE LOOT! ?`, item lines ` +(?<item>.+?)(?: x(?<amount>[\d,]+)\|$)`, end `▬{64}` | SkyOcean `features/gambling/vanguard/VanguardGambling.kt:59-61` (Vanguard only; other three share the format) |

### Chat guard

- While `CaseScreen` is open: cancel `RenderHudElementEvent` when `element == HudElement.CHAT` (SkyOcean `SlotMachineSpinner.kt:66-71`).
- Chat-triggered reveals: cancel the trigger line(s) in `ChatReceivedEvent.Pre`, store them, re-add to chat via `Minecraft.getInstance().gui.chat.addMessage(component)` when the screen closes or is aborted.
- ESC aborts: screen closes, chat returns, held lines flush.

### Winner rule (no prices)

- GUI chests: first item whose lore rarity line is highest (`SkyBlockRarity` from SkyblockAPI `api/data/SkyBlockRarity.kt`); ties → first slot.
- Chat triggers: the item named in the line (pool = the other items in the same loot block, or a fixed filler list of 20 SkyBlock item ids).

### SkyblockAPI surface used (verified against `SkyblockAPI/SkyblockAPI` HEAD 2026-09-13)

- `SkyBlockAPI.eventBus.register(obj)`; listener methods annotated `@Subscription` (pattern: SkyOcean `SkyOcean.kt:59`).
- `ChatReceivedEvent.Pre(component)` — `text` (stripped), `coloredText`, `cancel()` (`api/events/chat/ChatReceivedEvent.kt:10-17`).
- `ContainerInitializedEvent(itemStacks, screen)` — `title` (`api/events/screen/ContainerInitializedEvent.kt:12-18`).
- `RenderHudElementEvent(element, graphics)` + `HudElement.CHAT` (`api/events/render/RenderHudElementEvent.kt:7-12`).
- `RenderScreenBackgroundEvent(screen, graphics)`, cancellable (`api/events/render/RenderScreenEvent.kt:11`).
- Graphics type `net.minecraft.client.gui.GuiGraphicsExtractor` with `fill`, `drawString`, `translate`, `scale`, `guiWidth()`, `guiHeight()` (SkyOcean `DungeonGamblingRenderer.kt:80-100`).

---

## File Structure

```
SkyGrab/
  build.gradle.kts, settings.gradle.kts, gradle.properties, stonecutter.gradle.kts
  gradle/libs.versions.toml
  versions/26.2/gradle.properties, versions/26.1.2/gradle.properties
  src/main/resources/fabric.mod.json
  src/main/resources/assets/skygrab/textures/gui/case_frame.png   (own art, 256x64)
  src/main/kotlin/dev/skygrab/
    SkyGrab.kt                 entrypoint; registers modules
    config/SkyGrabConfig.kt    JSON config (Gson), toggles + durations
    core/Reveal.kt             data class + DropTier enum
    core/RarityGate.kt         pure string gates (tested)
    core/ChestTitles.kt        title → ChestKind (tested)
    core/Easing.kt             easeOutCubic (tested)
    core/RevealQueue.kt        one reveal at a time; opens screen
    chat/ChatGuard.kt          HUD hide + hold/re-emit lines
    screen/CaseScreen.kt       the animation
    triggers/ChestTrigger.kt   ContainerInitializedEvent → Reveal
    triggers/ChatTrigger.kt    ChatReceivedEvent.Pre → Reveal
  src/test/kotlin/dev/skygrab/core/
    RarityGateTest.kt, ChestTitlesTest.kt, EasingTest.kt
  LICENSE (MIT), README.md
```

---

### Task 1: Project skeleton that builds and loads

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `stonecutter.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `versions/26.2/gradle.properties`, `versions/26.1.2/gradle.properties`, `src/main/resources/fabric.mod.json`, `src/main/kotlin/dev/skygrab/SkyGrab.kt`, `LICENSE`

**Interfaces:**
- Produces: `object SkyGrab { const val MOD_ID = "skygrab"; val LOGGER: Logger; fun id(path: String): Identifier }`

- [ ] **Step 1: Copy build layout from SkyOcean (MIT code)**

Clone reference: `git clone --depth 1 https://github.com/meowdding/SkyOcean /tmp/skyocean`. Copy `settings.gradle.kts`, `stonecutter.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `versions/26.2/gradle.properties`, `versions/26.1/gradle.properties` (rename dir to `26.1.2`), `gradlew*`, `gradle/wrapper`. Delete from `build.gradle.kts` every dependency line that is not one of: fabric-loader, fabric-language-kotlin, skyblockapi, kotlin plugin, loom, stonecutter. Delete meowdding-lib, ktmodules, ktcodecs, resources, auto-mixins, resourceful-config, detekt blocks.

Keep in `gradle/libs.versions.toml` (values from SkyOcean HEAD 2026-09-12):

```toml
[versions]
fabric-loader = "0.18.3"
fabric-language-kotlin = "1.13.12+kotlin.2.4.0"
kotlin = "2.4.0"
skyblockapi = "4.2.22"
fabric-loom = "1.15-SNAPSHOT"

[libraries]
fabric-loader = { module = "net.fabricmc:fabric-loader", version.ref = "fabric-loader" }
fabric-language-kotlin = { module = "net.fabricmc:fabric-language-kotlin", version.ref = "fabric-language-kotlin" }
skyblockapi = { module = "tech.thatgravyboat:skyblock-api", version.ref = "skyblockapi" }
```

Confirm the maven exists in `settings.gradle.kts`: `maven("https://maven.teamresourceful.com/repository/maven-public/")`.

- [ ] **Step 2: gradle.properties**

```properties
version=0.1.0
mod_id=skygrab
maven_group=dev.skygrab
org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true
```

- [ ] **Step 3: fabric.mod.json**

```json
{
  "schemaVersion": 1,
  "id": "skygrab",
  "version": "${version}",
  "name": "SkyGrab",
  "description": "Cosmetic reveal animations for rewards you already got in Hypixel SkyBlock.",
  "license": "MIT",
  "environment": "client",
  "entrypoints": { "client": [ { "adapter": "kotlin", "value": "dev.skygrab.SkyGrab" } ] },
  "depends": {
    "fabricloader": ">=0.16.10",
    "minecraft": "${minecraft_range}",
    "fabric-language-kotlin": ">=1.13.12",
    "skyblock-api": "*"
  }
}
```

(`${minecraft_range}` expansion: keep SkyOcean's `processResources` block that fills it from the Stonecutter node — `build.gradle.kts`, search `minecraft_range`.) Confirm SkyblockAPI's mod id by reading its `fabric.mod.json` in the downloaded jar (`unzip -p ~/.gradle/caches/**/skyblock-api-*.jar fabric.mod.json | grep '"id"'`) and fix the `depends` key if it differs.

- [ ] **Step 4: Entrypoint**

```kotlin
package dev.skygrab

import net.fabricmc.api.ClientModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI

object SkyGrab : ClientModInitializer {
    const val MOD_ID = "skygrab"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)
    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    private val modules: List<Any> = emptyList() // filled in later tasks

    override fun onInitializeClient() {
        modules.forEach { SkyBlockAPI.eventBus.register(it) }
        LOGGER.info("SkyGrab loaded")
    }
}
```

If `Identifier.fromNamespaceAndPath` does not exist in 26.2 mappings, grep genSources: `./gradlew :26.2:genSources` then `grep -rn "fun.*Identifier\|static Identifier" build/…/Identifier.java` and use the factory found there.

- [ ] **Step 5: Build + run once**

Run: `./gradlew :26.2:build` → Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew :26.2:runClient` → log contains `SkyGrab loaded`.

- [ ] **Step 6: Commit**

```bash
git init && git add -A && git commit -m "chore: fabric+kotlin+skyblockapi skeleton for 26.2/26.1.2"
```

---

### Task 2: Pure core — Reveal, DropTier, Easing, ChestTitles, RarityGate (TDD)

**Files:**
- Create: `src/main/kotlin/dev/skygrab/core/Reveal.kt`, `core/Easing.kt`, `core/ChestTitles.kt`, `core/RarityGate.kt`
- Test: `src/test/kotlin/dev/skygrab/core/EasingTest.kt`, `ChestTitlesTest.kt`, `RarityGateTest.kt`
- Modify: `build.gradle.kts` (add `testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")`, `tasks.test { useJUnitPlatform() }`)

**Interfaces:**
- Produces:
  - `enum class DropTier { UNCOMMON, RARE, VERY_RARE, CRAZY_RARE, PRAY_RNGESUS }`
  - `enum class ChestKind { WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK, KUUDRA_FREE, KUUDRA_PAID }`
  - `data class Reveal(val pool: List<ItemStack>, val winner: ItemStack, val heldChat: List<Component>)`
  - `object Easing { fun outCubic(t: Float): Float }`
  - `object ChestTitles { fun kind(title: String): ChestKind? }`
  - `object RarityGate { fun dropTier(text: String): DropTier?; fun isRarePet(coloredText: String): Boolean; fun isRareGift(text: String): Boolean; fun isRareTrophy(text: String): Boolean; fun isNewRabbit(text: String): Boolean; fun corpseStart(text: String): Boolean; fun corpseEnd(text: String): Boolean; fun corpseItem(text: String): Pair<String, Int>? }`

- [ ] **Step 1: Failing tests**

```kotlin
// EasingTest.kt
package dev.skygrab.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class EasingTest {
    @Test fun endpoints() { assertEquals(0f, Easing.outCubic(0f)); assertEquals(1f, Easing.outCubic(1f)) }
    @Test fun monotone() { var last = -1f; for (i in 0..100) { val v = Easing.outCubic(i / 100f); assertTrue(v >= last); last = v } }
    @Test fun clamps() { assertEquals(1f, Easing.outCubic(2f)); assertEquals(0f, Easing.outCubic(-1f)) }
}
```

```kotlin
// ChestTitlesTest.kt
package dev.skygrab.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ChestTitlesTest {
    @Test fun dungeon() {
        assertEquals(ChestKind.BEDROCK, ChestTitles.kind("Bedrock Chest"))
        assertEquals(ChestKind.BEDROCK, ChestTitles.kind("Bedrock"))
        assertEquals(ChestKind.WOOD, ChestTitles.kind("Wood Chest"))
    }
    @Test fun kuudra() {
        assertEquals(ChestKind.KUUDRA_FREE, ChestTitles.kind("Free Chest"))
        assertEquals(ChestKind.KUUDRA_FREE, ChestTitles.kind("Free Chest Chest"))
        assertEquals(ChestKind.KUUDRA_PAID, ChestTitles.kind("Paid Chest Chest"))
    }
    @Test fun other() { assertNull(ChestTitles.kind("Croesus")); assertNull(ChestTitles.kind("Ender Chest")) }
}
```

```kotlin
// RarityGateTest.kt
package dev.skygrab.core
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class RarityGateTest {
    @Test fun dropTiers() {
        assertEquals(DropTier.RARE, RarityGate.dropTier("RARE DROP! (Revenant Viscera) (+5% Magic Find)"))
        assertEquals(DropTier.VERY_RARE, RarityGate.dropTier("VERY RARE DROP!  (Revenant Catalyst)"))
        assertEquals(DropTier.CRAZY_RARE, RarityGate.dropTier("CRAZY RARE DROP! (Shadow Warp)"))
        assertEquals(DropTier.PRAY_RNGESUS, RarityGate.dropTier("PRAY TO RNGESUS DROP! (Necron's Handle)"))
        assertNull(RarityGate.dropTier("You dug out a Griffin Burrow!"))
    }
    @Test fun pets() {
        assertTrue(RarityGate.isRarePet("§6§lPET DROP! §r§6Golden Dragon §r§b(+100% ✯ Magic Find)"))
        assertTrue(RarityGate.isRarePet("§d§lPET DROP! §r§dSquid"))
        assertFalse(RarityGate.isRarePet("§9§lPET DROP! §r§9Rock"))
    }
    @Test fun gifts() {
        assertTrue(RarityGate.isRareGift("SANTA TIER! +500 Enchanting XP gift with paysley!"))
        assertTrue(RarityGate.isRareGift("PARTY TIER! +1 North Star"))
        assertFalse(RarityGate.isRareGift("COMMON! +500 Enchanting XP gift with paysley!"))
        assertFalse(RarityGate.isRareGift("RARE! +2,000 Coins gift with x!"))
    }
    @Test fun trophy() {
        assertTrue(RarityGate.isRareTrophy("TROPHY FISH! You caught a Sulphur Skitter DIAMOND!"))
        assertFalse(RarityGate.isRareTrophy("TROPHY FISH! You caught a Sulphur Skitter BRONZE!"))
    }
    @Test fun rabbit() {
        assertTrue(RarityGate.isNewRabbit("NEW RABBIT! +5 Chocolate and +0.1x Chocolate per second!"))
        assertFalse(RarityGate.isNewRabbit("HOPPITY'S HUNT You found a Chocolate Lunch Egg!"))
    }
    @Test fun corpse() {
        assertTrue(RarityGate.corpseStart("  VANGUARD CORPSE LOOT!"))
        assertTrue(RarityGate.corpseStart("  UMBER CORPSE LOOT! "))
        assertFalse(RarityGate.corpseStart("CORPSE LOOT!"))
        assertTrue(RarityGate.corpseEnd("▬".repeat(64)))
        assertEquals("Glacite Jewel" to 3, RarityGate.corpseItem("  Glacite Jewel x3"))
        assertEquals("Shattered Pendant" to 1, RarityGate.corpseItem("  Shattered Pendant"))
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew :26.2:test` → Expected: FAIL, unresolved references.

- [ ] **Step 3: Implement**

```kotlin
// Reveal.kt
package dev.skygrab.core
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

enum class DropTier { UNCOMMON, RARE, VERY_RARE, CRAZY_RARE, PRAY_RNGESUS }
enum class ChestKind { WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK, KUUDRA_FREE, KUUDRA_PAID }

data class Reveal(
    val pool: List<ItemStack>,
    val winner: ItemStack,
    val heldChat: List<Component> = emptyList(),
)
```

```kotlin
// Easing.kt
package dev.skygrab.core
object Easing {
    fun outCubic(t: Float): Float { val c = t.coerceIn(0f, 1f); val u = 1f - c; return 1f - u * u * u }
}
```

```kotlin
// ChestTitles.kt
package dev.skygrab.core
object ChestTitles {
    // Titles per Skyblocker ChestValue.java:59,63 (Hypixel sometimes doubles "Chest").
    private val dungeon = mapOf(
        "Wood" to ChestKind.WOOD, "Gold" to ChestKind.GOLD, "Diamond" to ChestKind.DIAMOND,
        "Emerald" to ChestKind.EMERALD, "Obsidian" to ChestKind.OBSIDIAN, "Bedrock" to ChestKind.BEDROCK,
    )
    fun kind(title: String): ChestKind? {
        val t = title.trim()
        when (t) {
            "Free Chest", "Free Chest Chest" -> return ChestKind.KUUDRA_FREE
            "Paid Chest", "Paid Chest Chest" -> return ChestKind.KUUDRA_PAID
        }
        val base = t.removeSuffix(" Chest")
        return dungeon[base]
    }
}
```

```kotlin
// RarityGate.kt
package dev.skygrab.core
object RarityGate {
    // Prefixes per SkyHanni RngDropEnum.kt:4-7 + ChatFilter.kt:194 ("VERY RARE DROP!").
    private val tierPrefix = listOf(
        "PRAY TO RNGESUS DROP!" to DropTier.PRAY_RNGESUS,
        "CRAZY RARE DROP!" to DropTier.CRAZY_RARE,
        "VERY RARE DROP!" to DropTier.VERY_RARE,
        "RARE DROP!" to DropTier.RARE,
        "UNCOMMON DROP!" to DropTier.UNCOMMON,
    )
    fun dropTier(text: String): DropTier? = tierPrefix.firstOrNull { text.trimStart().startsWith(it.first) }?.second

    // SkyHanni RareDropMessages.kt:52 — colour code after "PET DROP! " is the pet rarity.
    private val pet = Regex("(?:§.)*PET DROP! (?:§.)*§(?<c>.)")
    fun isRarePet(coloredText: String): Boolean = pet.find(coloredText)?.groups?.get("c")?.value in setOf("6", "d")

    // SkyHanni GiftProfitTracker.kt:52
    private val gift = Regex("^(?:SANTA|PARTY) TIER!")
    fun isRareGift(text: String) = gift.containsMatchIn(text.trim())

    // SkyHanni TrophyFishMessages.kt:37
    private val trophy = Regex("TROPHY FISH! You caught an? .+ (GOLD|DIAMOND)!")
    fun isRareTrophy(text: String) = trophy.containsMatchIn(text)

    // SkyHanni HoppityEggsManager.kt:95
    fun isNewRabbit(text: String) = text.trim().startsWith("NEW RABBIT!")

    // SkyOcean VanguardGambling.kt:59-61, widened to all four corpse types.
    private val corpseStartRx = Regex(" +(?:LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT! ?")
    private val corpseItemRx = Regex(" +(?<item>.+?)(?: x(?<amount>[\\d,]+)|$)")
    private val corpseEndRx = Regex("▬{64}")
    fun corpseStart(text: String) = corpseStartRx.matches(text)
    fun corpseEnd(text: String) = corpseEndRx.containsMatchIn(text)
    fun corpseItem(text: String): Pair<String, Int>? {
        val m = corpseItemRx.matchEntire(text) ?: return null
        val amount = m.groups["amount"]?.value?.replace(",", "")?.toIntOrNull() ?: 1
        return m.groups["item"]!!.value to amount
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :26.2:test` → Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src build.gradle.kts && git commit -m "feat(core): reveal model, easing, chest titles, rarity gates with tests"
```

---

### Task 3: Config

**Files:**
- Create: `src/main/kotlin/dev/skygrab/config/SkyGrabConfig.kt`

**Interfaces:**
- Produces: `object SkyGrabConfig { val data: Data; fun load(); fun save() }` with
  `data class Data(var enabled: Boolean = true, var durationMs: Int = 4000, var hideChat: Boolean = true, var dungeonChests: Boolean = true, var kuudraChests: Boolean = true, var corpses: Boolean = true, var pets: Boolean = true, var drops: Boolean = true, var minDropTier: DropTier = DropTier.VERY_RARE, var gifts: Boolean = true, var trophyFish: Boolean = true, var hoppity: Boolean = true)`

- [ ] **Step 1: Implement (Gson, file `config/skygrab.json`)**

```kotlin
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
```

- [ ] **Step 2: Call `SkyGrabConfig.load()` first line of `onInitializeClient` in `SkyGrab.kt`.**

- [ ] **Step 3: Run client once; verify `config/skygrab.json` exists with the 12 keys.**

Run: `./gradlew :26.2:runClient`, then `cat run/config/skygrab.json`.

- [ ] **Step 4: Commit** — `git commit -am "feat: json config"`

Skipped: config GUI. Add ModMenu + a screen only if users ask; `// ponytail: edit the json`.

---

### Task 4: ChatGuard — hide chat HUD while a reveal plays, hold/re-emit lines

**Files:**
- Create: `src/main/kotlin/dev/skygrab/chat/ChatGuard.kt`

**Interfaces:**
- Consumes: `SkyGrabConfig.data.hideChat`
- Produces: `object ChatGuard { var active: Boolean; fun hold(c: Component); fun release() }`

- [ ] **Step 1: Implement**

```kotlin
package dev.skygrab.chat
import dev.skygrab.config.SkyGrabConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.render.HudElement
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudElementEvent

object ChatGuard {
    @Volatile var active = false
    private val held = ArrayDeque<Component>()

    fun hold(c: Component) { held.addLast(c) }

    /** Re-add held lines to local chat history. Nothing was ever dropped: Pre-cancelled lines are re-shown here. */
    fun release() {
        active = false
        val chat = Minecraft.getInstance().gui.chat
        while (held.isNotEmpty()) chat.addMessage(held.removeFirst())
    }

    @Subscription
    fun onHud(event: RenderHudElementEvent) {
        if (active && SkyGrabConfig.data.hideChat && event.element == HudElement.CHAT) event.cancel()
    }
}
```

`event.cancel()` is the SkyblockAPI cancellable call used in SkyOcean `SlotMachineSpinner.kt:69`. If `RenderHudElementEvent` lacks `cancel()` in 4.2.22, grep `api/events/render/RenderHudElementEvent.kt` in the jar sources for the cancellable interface name and use its method.

- [ ] **Step 2: Register** — add `ChatGuard` to `modules` in `SkyGrab.kt`.

- [ ] **Step 3: Manual check** — in `runClient`, run `/skygrab test` (added in Task 6) later; for now compile only: `./gradlew :26.2:build` PASS.

- [ ] **Step 4: Commit** — `git commit -am "feat: chat guard (hud hide + hold/re-emit)"`

---

### Task 5: CaseScreen — the animation

**Files:**
- Create: `src/main/kotlin/dev/skygrab/screen/CaseScreen.kt`, `src/main/resources/assets/skygrab/textures/gui/case_frame.png` (own art: 256×64, dark frame with a 2px gold centre line)

**Interfaces:**
- Consumes: `Reveal`, `Easing.outCubic`, `ChatGuard`
- Produces: `class CaseScreen(reveal: Reveal, durationMs: Int, onDone: () -> Unit) : Screen`

Design (numbers from SkyOcean `DungeonGamblingRenderer.kt:80-131`, re-implemented): strip of `STRIP_LEN = 40` items; winner at index `WINNER_INDEX = 32`; card width 24px × scale 2; progress `p = Easing.outCubic(elapsed/duration)`; offset `= WINNER_INDEX * CARD_W * p + jitter` where `jitter ∈ [-6, 6]` px fixed per reveal; tick sound `SoundEvents.ITEM_PICKUP` pitch 2f each time a new card crosses the centre line; at `p ≥ 0.96` scale winner card `lerp((p-0.96)/0.04, 1, 3)`; when `elapsed ≥ duration + 1200ms` close.

- [ ] **Step 1: Find the 26.2 Screen render entry + item draw call**

Run: `./gradlew :26.2:genSources`, then
`grep -n "fun render\|void render\|extractRenderState" $(find ~/.gradle -name 'Screen.java' -path '*client/gui/screens*' | head -1)` and
`grep -n "renderItem\|renderFakeItem" $(find ~/.gradle -name 'GuiGraphicsExtractor.java' | head -1)`.
Write the two names you found into the code below where marked `<RENDER>` and `<ITEM>`. SkyOcean uses `GuiGraphicsExtractor` for `fill/drawString/translate/scale/guiWidth/guiHeight` (`DungeonGamblingRenderer.kt:80-100`), so those names are known-good.

- [ ] **Step 2: Implement**

```kotlin
package dev.skygrab.screen
import dev.skygrab.SkyGrab
import dev.skygrab.chat.ChatGuard
import dev.skygrab.core.Easing
import dev.skygrab.core.Reveal
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

class CaseScreen(private val reveal: Reveal, private val durationMs: Int, private val onDone: () -> Unit) :
    Screen(Component.literal("SkyGrab Reveal")) {

    private companion object { const val STRIP_LEN = 40; const val WINNER_INDEX = 32; const val CARD = 24; const val SCALE = 2 }
    private val strip: List<ItemStack> = List(STRIP_LEN) { i -> if (i == WINNER_INDEX) reveal.winner else reveal.pool.random() }
    private val jitter = Random.nextInt(-6, 7)
    private val start = System.currentTimeMillis()
    private var lastTick = -1
    private var finished = false

    override fun init() { ChatGuard.active = true }

    override fun <RENDER>(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        val elapsed = System.currentTimeMillis() - start
        val p = Easing.outCubic(elapsed / durationMs.toFloat())
        val cw = CARD * SCALE
        val w = graphics.guiWidth(); val h = graphics.guiHeight()
        graphics.fill(0, 0, w, h, 0x80000000.toInt())

        val offset = WINNER_INDEX * cw * p + jitter
        val originX = w / 2 - offset - cw / 2
        val y = h / 2 - cw / 2

        val crossed = (offset / cw).toInt()
        if (crossed > lastTick) { lastTick = crossed
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 2f)) }

        for (i in 0 until STRIP_LEN) {
            val x = originX + i * cw
            if (x < -cw || x > w) continue
            val s = if (i == WINNER_INDEX && p >= 0.96f) Mth.lerp((p - 0.96f) / 0.04f, 1f, 3f) else 1f
            graphics.translate(x.toFloat() + cw / 2, y.toFloat() + cw / 2)
            graphics.scale(SCALE * s, SCALE * s)
            graphics.<ITEM>(strip[i], -8, -8)
            graphics.scale(1f / (SCALE * s), 1f / (SCALE * s))
            graphics.translate(-(x.toFloat() + cw / 2), -(y.toFloat() + cw / 2))
        }
        graphics.fill(w / 2 - 1, y - 6, w / 2 + 1, y + cw + 6, 0xFFFFD700.toInt())

        if (p >= 1f) graphics.drawString(font, reveal.winner.hoverName, w / 2 - font.width(reveal.winner.hoverName) / 2, y + cw + 12, 0xFFFFFF)
        if (elapsed >= durationMs + 1200 && !finished) { finished = true; onClose() }
    }

    override fun onClose() { ChatGuard.release(); onDone(); super.onClose() }
    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true
}
```

If `translate`/`scale` are not paired push/pop in 26.2 (SkyOcean uses `translated {}`/`scaled {}` helpers), wrap the per-card block with whatever matrix push/pop `GuiGraphicsExtractor` exposes (`grep -n "pose\|push\|pop" GuiGraphicsExtractor.java`).

- [ ] **Step 3: Compile** — `./gradlew :26.2:build` PASS.

- [ ] **Step 4: Commit** — `git commit -am "feat: case-scroll reveal screen"`

Skipped: sprite frame texture usage, particles. Add after first playtest.

---

### Task 6: RevealQueue + `/skygrab test` command

**Files:**
- Create: `src/main/kotlin/dev/skygrab/core/RevealQueue.kt`
- Modify: `SkyGrab.kt` (register)

**Interfaces:**
- Consumes: `CaseScreen`, `SkyGrabConfig`
- Produces: `object RevealQueue { fun submit(r: Reveal) }`

- [ ] **Step 1: Implement**

```kotlin
package dev.skygrab.core
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.screen.CaseScreen
import net.minecraft.client.Minecraft

object RevealQueue {
    private val queue = ArrayDeque<Reveal>()
    private var playing = false

    fun submit(r: Reveal) {
        if (!SkyGrabConfig.data.enabled) return
        queue.addLast(r); pump()
    }
    private fun pump() {
        if (playing) return
        val next = queue.removeFirstOrNull() ?: return
        playing = true
        val mc = Minecraft.getInstance()
        next.heldChat.forEach(dev.skygrab.chat.ChatGuard::hold)
        mc.execute { mc.setScreen(CaseScreen(next, SkyGrabConfig.data.durationMs) { playing = false; pump() }) }
    }
}
```

- [ ] **Step 2: Test command (Fabric API client commands)**

Add to `build.gradle.kts`: `modImplementation("net.fabricmc.fabric-api:fabric-api:<version for 26.2 from https://fabricmc.net/develop>")`, `fabric-api` to `depends`. In `SkyGrab.onInitializeClient`:

```kotlin
ClientCommandRegistrationCallback.EVENT.register { d, _ ->
    d.register(ClientCommandManager.literal("skygrab").then(ClientCommandManager.literal("test").executes {
        val items = listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.NETHERITE_INGOT, Items.ENCHANTED_BOOK).map { ItemStack(it) }
        RevealQueue.submit(Reveal(items, ItemStack(Items.NETHER_STAR)))
        1
    }))
}
```

- [ ] **Step 3: Run** — `./gradlew :26.2:runClient`, type `/skygrab test`. Expected: dark overlay, strip scrolls right→left, ticks, lands on Nether Star, scales up, name shown, closes ~5.2 s; chat HUD hidden during, back after; ESC aborts and chat returns.

- [ ] **Step 4: Commit** — `git commit -am "feat: reveal queue + /skygrab test"`

---

### Task 7: ChestTrigger (ALWAYS mode)

**Files:**
- Create: `src/main/kotlin/dev/skygrab/triggers/ChestTrigger.kt`
- Modify: `SkyGrab.kt` (register)

**Interfaces:**
- Consumes: `ChestTitles.kind`, `RevealQueue.submit`, SkyblockAPI `ContainerInitializedEvent`, `SkyBlockRarity`
- Produces: nothing

- [ ] **Step 1: Implement**

```kotlin
package dev.skygrab.triggers
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.core.*
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerInitializedEvent
import tech.thatgravyboat.skyblockapi.utils.extentions.getRarity   // confirm exact ext name: grep -rn "fun ItemStack.getRarity\|SkyBlockRarity" in skyblock-api sources

object ChestTrigger {
    private var lastContainerId = -1

    @Subscription
    fun onContainer(event: ContainerInitializedEvent) {
        val kind = ChestTitles.kind(event.title) ?: return
        val cfg = SkyGrabConfig.data
        val enabled = when (kind) {
            ChestKind.KUUDRA_FREE, ChestKind.KUUDRA_PAID -> cfg.kuudraChests
            else -> cfg.dungeonChests
        }
        if (!enabled) return
        val id = event.screen.menu.containerId
        if (id == lastContainerId) return          // same chest re-rendered
        lastContainerId = id

        val items = event.itemStacks.filter { !it.isEmpty && it.getRarity() != null }
        if (items.isEmpty()) return
        val winner = items.maxByOrNull { it.getRarity()!!.ordinal } ?: return
        RevealQueue.submit(Reveal(items, winner))
    }
}
```

Winner = highest lore rarity (SkyBlockRarity ordinal), ties → first. No price lookup by design.

- [ ] **Step 2: Manual test** — in a dungeon run or Croesus, open a Wood chest: reveal plays once; re-open same chest: no replay. Kuudra Free Chest: plays.

- [ ] **Step 3: Commit** — `git commit -am "feat: dungeon + kuudra chest trigger"`

---

### Task 8: ChatTrigger (corpses ALWAYS, rest RARE_ONLY, hold spoiler lines)

**Files:**
- Create: `src/main/kotlin/dev/skygrab/triggers/ChatTrigger.kt`
- Modify: `SkyGrab.kt` (register)

**Interfaces:**
- Consumes: `RarityGate.*`, `RevealQueue.submit`, `ChatReceivedEvent.Pre`
- Produces: nothing

- [ ] **Step 1: Implement**

```kotlin
package dev.skygrab.triggers
import dev.skygrab.config.SkyGrabConfig
import dev.skygrab.core.*
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.remote.RepoItemsAPI   // confirm: grep -rn "object RepoItemsAPI\|fun getItem" skyblock-api sources

object ChatTrigger {
    // ponytail: filler pool when the chat line names only one item
    private val filler by lazy { listOf(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.ENDER_PEARL, Items.BLAZE_ROD).map(::ItemStack) }

    private var corpse: MutableList<Component>? = null
    private var corpseItems = mutableListOf<ItemStack>()

    @Subscription
    fun onChat(event: ChatReceivedEvent.Pre) {
        val cfg = SkyGrabConfig.data
        if (!cfg.enabled) return
        val text = event.text

        // --- corpse block (ALWAYS) ---
        if (cfg.corpses) {
            if (RarityGate.corpseStart(text)) { corpse = mutableListOf(event.component); corpseItems.clear(); event.cancel(); return }
            corpse?.let { held ->
                held += event.component; event.cancel()
                RarityGate.corpseItem(text)?.let { (name, amount) -> stackFor(name, amount)?.let(corpseItems::add) }
                if (RarityGate.corpseEnd(text)) {
                    val items = corpseItems.toList(); corpse = null
                    val winner = items.maxByOrNull { rarityOf(it) } ?: return
                    RevealQueue.submit(Reveal(items, winner, held))
                }
                return
            }
        }

        // --- RARE_ONLY singles ---
        val fire = when {
            cfg.pets && RarityGate.isRarePet(event.coloredText) -> true
            cfg.drops && (RarityGate.dropTier(text)?.let { it >= cfg.minDropTier } == true) -> true
            cfg.gifts && RarityGate.isRareGift(text) -> true
            cfg.trophyFish && RarityGate.isRareTrophy(text) -> true
            cfg.hoppity && RarityGate.isNewRabbit(text) -> true
            else -> false
        }
        if (!fire) return
        val winner = itemNamedIn(text) ?: ItemStack(Items.NETHER_STAR).apply { set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(text.take(40))) }
        event.cancel()
        RevealQueue.submit(Reveal(filler, winner, listOf(event.component)))
    }

    private fun rarityOf(s: ItemStack) = s.getRarity()?.ordinal ?: -1
    private fun stackFor(name: String, amount: Int): ItemStack? = RepoItemsAPI.getItemByName(name)?.copyWithCount(amount)
    private fun itemNamedIn(text: String): ItemStack? {
        val m = Regex("\\(([^)]+)\\)").find(text) ?: Regex("You caught an? (.+?) (?:BRONZE|SILVER|GOLD|DIAMOND)!").find(text) ?: return null
        return RepoItemsAPI.getItemByName(m.groupValues[1].substringAfter("x ").trim())
    }
}
```

Two SkyblockAPI names to confirm at implementation time (grep the sources jar): the item-by-name lookup (`RepoItemsAPI` in `api/remote/`) and the `ItemStack.getRarity()` extension. If lookup by display name is absent, fall back to `ItemStack(Items.NETHER_STAR)` with the custom name — the reveal still plays.

- [ ] **Step 2: Manual test** — paste test lines via a local chat echo is not possible on Hypixel; instead add to the `/skygrab test` command a `chat <line>` subcommand that posts `ChatReceivedEvent.Pre(Component.literal(line))` to `SkyBlockAPI.eventBus` and verify: `"  VANGUARD CORPSE LOOT!"` + `"  Shattered Pendant"` + `"▬"*64` → reveal; `"RARE DROP! (Foul Flesh)"` → nothing; `"CRAZY RARE DROP! (Shadow Warp)"` → reveal and the line appears in chat only after the screen closes.

- [ ] **Step 3: Commit** — `git commit -am "feat: chat triggers (corpses always, rare-only singles) with held spoiler lines"`

---

### Task 9: README + Modrinth-ready jar

**Files:**
- Create: `README.md`; Modify: `build.gradle.kts` (jar name `skygrab-<version>+<mc>.jar`)

- [ ] **Step 1: README** — what it does, the ALWAYS / RARE_ONLY table from the Spec, the policy sentence ("purely cosmetic; hides chat HUD while playing, never drops or automates anything"), config keys, credits: "case-scroll math re-implemented from SkyOcean (MIT)". No gif yet.
- [ ] **Step 2: `./gradlew build` for both versions** → jars in `versions/26.2/build/libs` and `versions/26.1.2/build/libs`.
- [ ] **Step 3: Commit + tag** — `git commit -am "docs: readme" && git tag v0.1.0`

---

## Self-review

- Spec coverage: ALWAYS triggers → Task 7 (chests), Task 8 (corpses). RARE_ONLY → Task 8 with gates from Task 2. Chat guard → Task 4 + used in 5/6/8. Winner rule (no prices) → Tasks 7/8. Config → Task 3. ESC abort → Task 5 `shouldCloseOnEsc` + `onClose`. Slot style: explicitly out of scope.
- Placeholders: two `<RENDER>`/`<ITEM>` and two SkyblockAPI names are marked with an exact grep to resolve them — not "TBD", but they are the only unverified identifiers in the plan.
- Type consistency: `Reveal(pool, winner, heldChat)` used identically in 6/7/8; `ChatGuard.hold/release/active` in 4/5/6; `RarityGate` names match tests.
