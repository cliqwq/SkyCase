# SkyCase

A Fabric client mod for Hypixel SkyBlock that replays rewards you already received as a CS:GO-style case-scroll animation. Purely cosmetic: replays rewards you already received, hides the chat HUD while the animation plays and re-shows every held line afterwards, never clicks, buys, rerolls, reads prices or touches packets.

## Features

### Trigger Modes

| Mode | Triggers | Gate |
|---|---|---|
| **ALWAYS** | Dungeon chest (Wood/Gold/Diamond/Emerald/Obsidian/Bedrock, opened in dungeon or Croesus); Kuudra Free/Paid Chest; Mineshaft corpse loot (Lapis/Tungsten/Umber/Vanguard) | none |
| **RARE_ONLY** | Pet drop; generic drop line; Winter gift; Trophy fish; Hoppity new rabbit | Pets: LEGENDARY (§6) or MYTHIC (§d); Drops: VERY RARE, CRAZY RARE, or PRAY TO RNGESUS; Gifts: SANTA TIER or PARTY TIER; Trophy fish: GOLD or DIAMOND; Hoppity: NEW RABBIT! |
| **ALWAYS** | Dragon fight "`<TYPE> DRAGON DOWN!`" | none -- opens a 12s inventory-watch window instead of a rarity check; only reveals if something was actually picked up; floor drops are hidden until the reveal finishes (client-side render skip) |
| **ALWAYS** | Scatha kill in Crystal Hollows | none -- pre-roll starts once the Scatha's own name-tag health drops to ≤15% of max; hotbar/held item/non-mod sounds are hidden (client-side render skip) from pre-roll until the reveal finishes; winner = pet/Bran drop if one arrives within 3s of the kill, else a Fine Topaz Gemstone fallback when `scathaEveryKill` is on, else no reveal |

### Animation
- Dark overlay with a scrolling strip of 40 items (winner at center)
- Easing: out-cubic acceleration from 0–1 over configurable duration
- Tick sound on each card crossing the center line
- Winner card scales up 1→3x at the end
- Chat HUD hidden during animation, all held spoiler lines re-shown afterwards
- ESC aborts the animation and returns chat

## Configuration

Edit `config/skycase.json` to customize:

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enable all SkyCase reveals |
| `durationMs` | integer | `4000` | Animation duration in milliseconds |
| `hideHud` | boolean | `true` | Hide the whole HUD (hotbar, scoreboard, health/mana, boss bar, action bar) while a reveal plays |
| `hideChat` | boolean | `true` | Hide chat HUD while animation plays |
| `dungeonChests` | boolean | `true` | Reveal dungeon chests |
| `kuudraChests` | boolean | `true` | Reveal Kuudra chests |
| `corpses` | boolean | `true` | Reveal corpse loot blocks |
| `pets` | boolean | `true` | Reveal pet drops (LEGENDARY/MYTHIC only) |
| `drops` | boolean | `true` | Reveal rare generic drops |
| `minDropTier` | enum | `VERY_RARE` | Minimum rarity for generic drops (UNCOMMON, RARE, VERY_RARE, CRAZY_RARE, PRAY_RNGESUS) |
| `gifts` | boolean | `true` | Reveal winter gifts (SANTA/PARTY tier) |
| `trophyFish` | boolean | `true` | Reveal trophy fish (GOLD/DIAMOND) |
| `hoppity` | boolean | `true` | Reveal Hoppity new rabbits |
| `dragons` | boolean | `true` | Reveal Ender Dragon fight loot (Protector/Old/Wise/Unstable/Strong/Young/Superior) |
| `scatha` | boolean | `true` | Use the Scatha pool for a Scatha pet drop (still gated by `pets`'s LEGENDARY/MYTHIC rarity check) |
| `yeti` | boolean | `true` | Use the Baby Yeti pool for a Baby Yeti pet drop (still gated by `pets`'s LEGENDARY/MYTHIC rarity check) |
| `scathaEveryKill` | boolean | `true` | Reveal every Scatha kill in Crystal Hollows (gemstone/fallback winner too), not just ones that drop the Scatha pet or Dwarven O's Block Bran |

## Commands

- `/skycase test` — Play a demo reveal with 5 sample items
- `/skycase chat <line>` — Feed a chat line directly to the trigger engine (exists because server chat cannot be injected locally; it works anywhere)

## Requirements

- **Fabric Loader** ≥0.19.2
- **Minecraft** 26.2 (26.1.2: not yet — ChatGuard uses 26.2-only Hud/chat API)
- **fabric-language-kotlin** ≥1.13.12
- **Fabric API** — required, install separately
- **SkyblockAPI** 4.2.22 — bundled inside the jar, nothing to install

## Heads-up

Reveals draw as a HUD overlay, not a `Screen`: the strip runs for `durationMs` (default 4000ms)
plus a fixed 1.2s hold on the winner, and you keep moving, fighting, and opening menus the whole
time. If a container GUI is open (a chest trigger, or you open one mid-reveal), the chest stays
open underneath the overlay and clicks/keys are blocked until the roll finishes, so you can't click
through it -- ESC still closes the chest immediately, it just isn't swallowed by the overlay first.
SkyCase will not move or protect you while a reveal plays, so if you're in a dangerous area (mobs,
fall damage, PvP) consider lowering `durationMs` or disabling `corpses`/`drops` there.

## Building

```bash
./gradlew :26.2:build
```

Jar is placed in:
- `versions/26.2/build/libs/skycase-0.1.0+26.2.jar`

## Credits

- Case-scroll animation math re-implemented from SkyOcean (MIT code)
- Scatha reveal design inspired by Scatha-Pro (NamelessJu); no code reused
- All art is original
- Trigger regexes follow SkyHanni/Skyblocker/SkyOcean sources
- Loot pool data (`src/main/resources/skycase/pools/`):
  - `dungeon_chests.json` — all six chests, F1–F7 + M1–M7, from https://hypixelskyblock.minecraft.wiki The_Catacombs_-_Floor_N/Loot (+ Master_Mode_Loot); weight = average per-run chance × 100, guaranteed essence rows dropped, any single row capped at 20 %
  - `corpses.json` — per-roll % from https://hypixelskyblock.minecraft.wiki/Corpse (LAPIS, UMBER=TUNGSTEN, VANGUARD), weight = % × 100
  - `kuudra.json` — Paid Chest tables per tier from https://hypixelskyblock.minecraft.wiki/Kuudra, weight = % × 100
  - `rare_drops.json` — slayer: item sets from the six boss pages on hypixelskyblock.minecraft.wiki (uniform); catacombs: NotEnoughUpdates-REPO `constants/rngscore.json` (MIT); diana: hypixelskyblock.minecraft.wiki/Mythological_Ritual (uniform)
  - `trophy_fish.json` — the 18 fish names from https://hypixelskyblock.minecraft.wiki/Trophy_Fish; winner/pool items themselves come straight from SkyblockAPI's `TrophyFishType`/`TrophyFishTier`, no id lookup needed
  - `hoppity_textures.json` — vendored from hannibal002/SkyHanni-REPO `constants/HoppityRabbitTextures.json` (MIT): rabbit skin textures (base64), keyed by rarity
  - `hoppity_rabbits.json` — vendored from NotEnoughUpdates/NotEnoughUpdates-REPO `constants/hoppity.json` (MIT): rabbit names by rarity, title-cased from the source's snake_case
  - Pet drop pool/winner: SkyblockAPI's `SkyBlockPetsRepo` (`hypixelskyblock.minecraft.wiki/Pet` for the LEGENDARY mob-drop pets added to every pet's own pool: ENDER_DRAGON, BABY_YETI, SCATHA, LOCH_EMPEROR)
  - `dragon.json` — per-type (Protector/Old/Wise/Unstable/Strong/Young/Superior) loot from https://hypixelskyblock.minecraft.wiki/Dragon (2026-09-13); the wiki gives relative "quality" ranks, not measured drop %, so weight is an approximate rank-based guess, not a real percentage
  - `scatha.json`/`yeti.json` — non-pet drops from https://hypixelskyblock.minecraft.wiki/Scatha and /Yeti (2026-09-13), weight = the same rank-based guess as `dragon.json`; pet-drop rows use the wiki's exact per-kill % (Scatha RARE 0.24%/EPIC 0.12%/LEGENDARY 0.04%, Yeti COMMON 0.02%) × 100, resolved to a SCATHA/BABY_YETI pet stack via `SkyBlockPetsRepo` at that rarity
  - Regenerate `dungeon_chests.json`/`corpses.json`/`kuudra.json`/`rare_drops.json` with `py testkit/gen.py <SkyOcean-clone-dir> <fetched-json-dir>` (see the script's docstring for the `gh api` fetch commands); `trophy_fish.json`/`hoppity_textures.json`/`hoppity_rabbits.json`/`dragon.json`/`scatha.json`/`yeti.json` were fetched/hand-authored directly (2026-09-13), see task11-report.md/task12-report.md

## Licence

MIT
