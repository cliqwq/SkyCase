# SkyCase

A Fabric client mod for Hypixel SkyBlock that replays rewards you already received as a CS:GO-style case-scroll animation. Purely cosmetic: replays rewards you already received, hides the chat HUD while the animation plays and re-shows every held line afterwards, never clicks, buys, rerolls, reads prices or touches packets.

## Features

### Trigger Modes

| Mode | Triggers | Gate |
|---|---|---|
| **ALWAYS** | Dungeon chest (Wood/Gold/Diamond/Emerald/Obsidian/Bedrock, opened in dungeon or Croesus); Kuudra Free/Paid Chest; Mineshaft corpse loot (Lapis/Tungsten/Umber/Vanguard) | none |
| **RARE_ONLY** | Pet drop; generic drop line; Winter gift; Trophy fish; Hoppity new rabbit | Pets: LEGENDARY (§6) or MYTHIC (§d); Drops: VERY RARE, CRAZY RARE, or PRAY TO RNGESUS; Gifts: SANTA TIER or PARTY TIER; Trophy fish: GOLD or DIAMOND; Hoppity: NEW RABBIT! |

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

## Commands

- `/skycase test` — Play a demo reveal with 5 sample items
- `/skycase chat <line>` — Feed a chat line directly to the trigger engine (exists because server chat cannot be injected locally; it works anywhere)

## Requirements

- **Fabric Loader** ≥0.19.2
- **Minecraft** 26.2 (26.1.2: not yet — ChatGuard uses 26.2-only Hud/chat API)
- **fabric-language-kotlin** ≥1.13.12
- **Fabric API** — required, install separately
- **SkyblockAPI** 4.2.x (any version)

## Heads-up

Reveals open a `Screen`, which blocks player movement for the duration of the animation:
`durationMs` (default 4000ms) plus a fixed 1.2s hold on the winner. ESC aborts the reveal
instantly and returns control. If you play in dangerous areas (mobs, fall damage, PvP), consider
lowering `durationMs` or disabling `corpses`/`drops` there — SkyCase will not move or protect you
while a reveal plays.

## Building

```bash
./gradlew :26.2:build
```

Jar is placed in:
- `versions/26.2/build/libs/skycase-0.1.0+26.2.jar`

## Credits

- Case-scroll animation math re-implemented from SkyOcean (MIT code)
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
  - Regenerate `dungeon_chests.json`/`corpses.json`/`kuudra.json`/`rare_drops.json` with `py testkit/gen.py <SkyOcean-clone-dir> <fetched-json-dir>` (see the script's docstring for the `gh api` fetch commands); `trophy_fish.json`/`hoppity_textures.json`/`hoppity_rabbits.json` were fetched/hand-authored directly (2026-09-13), see task11-report.md

## Licence

MIT
