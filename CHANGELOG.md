# Changelog

## 0.1.0 — initial release (alpha)

Cosmetic case-opening reveals for rewards you already received in Hypixel SkyBlock.

- Reveal overlay (HUD, you keep moving): CS:GO-style card strip, winner pop, chat hidden while it plays and re-shown after.
- ALWAYS triggers: dungeon reward chests (Wood→Bedrock, all floors), Kuudra Free/Paid chests, Mineshaft corpses, Dragon fights (floor drops hidden until the roll ends), Scatha kills (pre-roll hides hotbar/hand/sounds).
- RARE_ONLY triggers: pet drops (LEGENDARY/MYTHIC), drops ≥ VERY RARE, SANTA/PARTY gifts, GOLD/DIAMOND trophy fish, new Hoppity rabbits; Scatha / Baby Yeti / Ender Dragon pets at any rarity.
- Real loot pools from hypixelskyblock.minecraft.wiki (dungeon chests, Kuudra, corpses, slayer, Diana, Scatha, Yeti, Ender Dragon), trophy fish, rabbit heads, pets.
- Config: `config/skycase.json`. Commands: `/skycase test`, `/skycase chat <line>`.

Known limitations: Scatha name-tag detection and dragon drop hiding are untested on Hypixel; items whose models are not shipped by SkyblockAPI render as their base item outside Hypixel.
