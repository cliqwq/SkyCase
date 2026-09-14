# SkyCase

**Cosmetic case-opening reveals for Hypixel SkyBlock.** When you get a reward you already earned — a dungeon chest, a Kuudra chest, a corpse, a rare drop, a pet, a trophy fish, a dragon fight, a Scatha kill — SkyCase plays a short CS:GO-style card roll that lands on your actual reward, then gets out of the way.

Purely cosmetic: it replays rewards you already received, hides the chat HUD while the animation plays and re-shows every held line afterwards, never clicks, buys, rerolls, reads prices or touches packets.

## Triggers

| Always | Rare only |
|---|---|
| Dungeon reward chests (Wood → Bedrock, F1–M7) | Pet drops (LEGENDARY / MYTHIC) |
| Kuudra Free / Paid chests (all tiers) | Drops labelled VERY RARE or better |
| Mineshaft corpses (Lapis / Tungsten / Umber / Vanguard) | SANTA / PARTY tier gifts |
| Ender Dragon fights (floor drops hidden until the roll ends) | GOLD / DIAMOND trophy fish |
| Scatha kills (hotbar, hand and sounds hidden during the roll) | New Hoppity rabbits |
| | Scatha / Baby Yeti / Ender Dragon pets at any rarity |

Each roll shows the real loot pool of that source (from the community wiki), with your reward as the winner.

## Requirements
Fabric Loader 0.19.2+, Fabric API, Fabric Language Kotlin, SkyblockAPI. Minecraft 26.2. Client-side only.

## Config
`config/skycase.json` — per-trigger toggles, roll duration, chat hiding, minimum drop tier.

## Notes
Alpha: Scatha detection and dragon drop hiding have not yet been verified on Hypixel. Report issues on GitHub.
