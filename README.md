# SkyGrab

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

Edit `config/skygrab.json` to customize:

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enable all SkyGrab reveals |
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

- `/skygrab test` — Play a demo reveal with 5 sample items
- `/skygrab chat <line>` — Feed a chat line directly to the trigger engine (for testing; Hypixel-only because real chat paste is blocked)

## Requirements

- **Fabric Loader** ≥0.19.2
- **Minecraft** 26.2 (26.1.2: not yet — ChatGuard uses 26.2-only Hud/chat API)
- **fabric-language-kotlin** ≥1.13.12
- **Fabric API** (bundled)
- **SkyblockAPI** 4.2.x (any version)

## Building

```bash
./gradlew :26.2:build
```

Jar is placed in:
- `versions/26.2/build/libs/skygrab-0.1.0+26.2.jar`

## Credits

- Case-scroll animation math re-implemented from SkyOcean (MIT code)
- All art is original
- Trigger regexes follow SkyHanni/Skyblocker/SkyOcean sources

## Licence

MIT
