"""Regenerates kuudra.json and corpses.json from the hypixelskyblock.minecraft.wiki tables (read 2026-09-13).
weight = drop% x 100 (integers). Rows are keyed by display name; LootPools resolves names at runtime."""
import json, re, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "skycase", "pools")


def W(p):
    return max(1, int(round(p * 100)))


armor = [f"{s} {p}" for s in ["Aurora", "Crimson", "Fervor", "Hollow", "Terror"] for p in ["Helmet", "Chestplate", "Leggings", "Boots"]]
molten = ["Molten Necklace", "Molten Cloak", "Molten Belt", "Molten Bracelet"]
vit = ["Vivacious", "Hardened", "Vampiric", "Strong"]

K = {}
K["BASIC"] = {**{a: 4.31 for a in armor}, "Aurora Staff": 1.05, "Hollow Wand": 1.05, **{m: 1.20 for m in molten}, "Tentacle Dye": 0.01,
              "Wheel of Fate": .53, "Fatal Tempo I": .03, "Inferno I": .03, **{f"{v} Vitality I": 21.87 for v in vit},
              "Bezal Shard": 6.76 + 11.93, "Kuudra Teeth": 100, "Crimson Essence": 100, "Kraken Shard": 100}
K["HOT"] = {**{a: 4.06 for a in armor}, "Aurora Staff": .99, "Hollow Wand": .99, **{m: 1.27 for m in molten}, "Tentacle Dye": 0.01,
            "Wheel of Fate": 1.19, "Fatal Tempo I": .13, "Inferno I": .13, **{f"{v} Vitality II": 19.71 for v in vit},
            "Bezal Shard": 6.36 + 10.75, "Magma Slug Shard": 5.30 + 8.96, "Kuudra Teeth": 100, "Crimson Essence": 100, "Kraken Shard": 100}
K["BURNING"] = {**{a: 3.43 for a in armor}, "Aurora Staff": .84, "Hollow Wand": .84, "Burning Kuudra Core": .12, "Mandraa": .96,
                **{m: 1.19 for m in molten}, "Wheel of Fate": 1.25, "Fatal Tempo I": .16, "Inferno I": .16,
                **{f"{v} Vitality III": 12.81 for v in vit}, "Bezal Shard": 5.38, "Magma Slug Shard": 4.48, "Kada Knight Shard": 3.64,
                "Wither Spectre Shard": 3.64, "Matcho Shard": 3.64, "Lava Flame Shard": 2.99, "Kuudra Teeth": 100, "Crimson Essence": 100, "Kraken Shard": 100}
K["FIERY"] = {**{a: 3.09 for a in armor}, "Aurora Staff": .75, "Hollow Wand": .75, "Burning Kuudra Core": .32, "Mandraa": 1.08,
              **{m: 1.18 for m in molten}, "Wheel of Fate": 1.40, "Heavy Pearl": 6.35, "Fatal Tempo I": .18, "Inferno I": .18,
              **{f"{v} Vitality IV": 10.47 for v in vit}, "Bezal Shard": 4.84, "Magma Slug Shard": 4.03, "Kada Knight Shard": 3.28,
              "Wither Spectre Shard": 3.28, "Matcho Shard": 3.28, "Lava Flame Shard": 2.69, "Fire Eel Shard": 2.04, "Flare Shard": 2.04,
              "Barbarian Duke X Shard": 2.04, "Hellwisp Shard": 1.67, "XYZ Shard": 1.34,
              "Kuudra Teeth": 6.35 + .63 + 100, "Crimson Essence": 6.35 + .63 + 100, "Kraken Shard": 100}
inf1 = {"Ananke Shard": .05, "Hellstorm Wand": .1, "Tormentor": .1, "Ananke Feather": .36, "Burning Kuudra Core": .51, "Daemon Shard": .51,
        "Lord Jawbus Shard": .51, "Moltenfish Shard": .51, "Cinderbat Shard": .67, "Taurus Shard": .67, "Hollow Wand": .72, "Aurora Staff": .72,
        "Mandraa": 1.23, **{m: 1.23 for m in molten}, "XYZ Shard": 1.29, "Hellwisp Shard": 1.59, "Barbarian Duke X Shard": 1.95,
        "Fire Eel Shard": 1.95, "Flare Shard": 1.95, "Lava Flame Shard": 2.57, **{a: 2.96 for a in armor}, "Kada Knight Shard": 3.14,
        "Matcho Shard": 3.14, "Wither Spectre Shard": 3.14, "Magma Slug Shard": 3.86, "Bezal Shard": 4.63}
inf2 = {"Kuudra Teeth": .06 + .58 + 5.83, "Crimson Essence": .06 + .58 + 5.83, "Ananke Shard": .06, "Inferno I": .22, "Fatal Tempo I": .22,
        "Heavy Pearl": .58 + 5.83, "Lord Jawbus Shard": .58, "Daemon Shard": .58, "Moltenfish Shard": .58, "Taurus Shard": .76,
        "Cinderbat Shard": .76, "Dusty Travel Scroll": .87, "XYZ Shard": 1.46, "Kuudra Mandible": 1.75, "Hellwisp Shard": 1.81,
        "Fire Eel Shard": 2.21, "Flare Shard": 2.21, "Barbarian Duke X Shard": 2.21, "Wheel of Fate": 2.68, "Lava Flame Shard": 2.91,
        "Kada Knight Shard": 3.56, "Wither Spectre Shard": 3.56, "Matcho Shard": 3.56, "Magma Slug Shard": 4.37, "Bezal Shard": 5.25}
inf = {}
for d in (inf1, inf2):
    for k, v in d.items():
        inf[k] = inf.get(k, 0) + v
inf["Kuudra Teeth"] += 100
inf["Crimson Essence"] += 100
inf["Kraken Shard"] = 100
K["INFERNAL"] = inf

out = {"_source": "https://hypixelskyblock.minecraft.wiki/Kuudra Paid Chest tables (2026-09-13); weight = drop% x100; resolved by display name"}
for t, d in K.items():
    out[t] = [{"name": n, "weight": W(w)} for n, w in d.items()]
json.dump(out, open(os.path.join(ROOT, "kuudra.json"), "w", encoding="utf-8"), indent=1)

CORPSE_ROWS = {
    "LAPIS": """Flawed Onyx Gemstone (20)|5.97;Flawed Onyx Gemstone (40)|2.99;Fine Onyx Gemstone|1.49;Fine Onyx Gemstone (2)|0.75;
Flawed Peridot Gemstone (20)|5.97;Flawed Peridot Gemstone (40)|2.99;Fine Peridot Gemstone|1.49;Fine Peridot Gemstone (2)|0.75;
Flawed Citrine Gemstone (20)|5.97;Flawed Citrine Gemstone (40)|2.99;Fine Citrine Gemstone|1.49;Fine Citrine Gemstone (2)|0.75;
Flawed Aquamarine Gemstone (20)|5.97;Flawed Aquamarine Gemstone (40)|2.99;Fine Aquamarine Gemstone|1.49;Fine Aquamarine Gemstone (2)|0.75;
Suspicious Scrap|2.99;Goblin Egg|4.78;Goblin Egg (2)|2.39;Goblin Egg (4)|1.19;Green Goblin Egg|4.18;Green Goblin Egg (2)|2.09;Green Goblin Egg (4)|1.04;
Yellow Goblin Egg|4.18;Yellow Goblin Egg (2)|2.09;Yellow Goblin Egg (4)|1.04;Red Goblin Egg|4.18;Red Goblin Egg (2)|2.09;Red Goblin Egg (4)|1.04;
Enchanted Umber|2.99;Enchanted Umber (2)|1.49;Enchanted Umber (4)|0.75;Enchanted Tungsten|2.99;Enchanted Tungsten (2)|1.49;Enchanted Tungsten (4)|0.75;
Enchanted Glacite|2.99;Enchanted Glacite (2)|1.49;Enchanted Glacite (4)|0.75;Refined Umber|0.15;Refined Tungsten|0.15;Glacite Amalgamation|0.15;
Umber Key|0.15;Tungsten Key|0.15;Glacite Jewel|2.99;Glacite Jewel (2)|1.49;Bejeweled Handle|0.6;Bejeweled Handle (2)|0.3;Bejeweled Handle (4)|0.15""",
    "UMBER": """Fine Onyx Gemstone (4)|10.7;Fine Onyx Gemstone (8)|5.35;Fine Onyx Gemstone (16)|2.68;Flawless Onyx Gemstone|0.38;
Fine Peridot Gemstone (4)|10.7;Fine Peridot Gemstone (8)|5.35;Fine Peridot Gemstone (16)|2.68;Flawless Peridot Gemstone|0.38;
Fine Citrine Gemstone (4)|10.7;Fine Citrine Gemstone (8)|5.35;Fine Citrine Gemstone (16)|2.68;Flawless Citrine Gemstone|0.38;
Fine Aquamarine Gemstone (4)|10.7;Fine Aquamarine Gemstone (8)|5.35;Fine Aquamarine Gemstone (16)|2.68;Flawless Aquamarine Gemstone|0.38;
Suspicious Scrap (2)|10.7;Suspicious Scrap (4)|5.35;Ice Cold I|1.22;Blue Goblin Egg|1.22;Refined Umber|0.31;Refined Tungsten|0.31;Glacite Amalgamation|0.31;
Umber Plate|0.08;Tungsten Plate|0.08;Umber Key|0.15;Tungsten Key|0.15;Mithril Plate|0.15;Frozen Scute|0.06;Dwarven O's Metallic Minis|0.31;
Skeleton Key|0.06;Caged Wisp|0.03;Opal Crystal|0.61;Onyx Crystal|0.61;Peridot Crystal|0.61;Citrine Crystal|0.61;Aquamarine Crystal|0.61""",
    "VANGUARD": """Flawless Onyx Gemstone|4.55;Flawless Onyx Gemstone (2)|2.28;Flawless Citrine Gemstone|4.55;Flawless Citrine Gemstone (2)|2.28;
Flawless Peridot Gemstone|4.55;Flawless Peridot Gemstone (2)|2.28;Flawless Aquamarine Gemstone|4.55;Flawless Aquamarine Gemstone (2)|2.28;
Dwarven O's Metallic Minis|3.41;Suspicious Scrap (8)|4.55;Suspicious Scrap (16)|2.28;Ice Cold I|4.55;Blue Goblin Egg|4.55;Blue Goblin Egg (2)|2.28;
Refined Umber|4.55;Refined Umber (2)|2.28;Refined Tungsten|4.55;Refined Tungsten (2)|2.28;Glacite Amalgamation|4.55;Glacite Amalgamation (2)|2.28;
Glacite Amalgamation (4)|1.14;Mithril Plate|1.71;Umber Plate|0.85;Tungsten Plate|0.85;Umber Key|2.28;Umber Key (2)|1.14;Umber Key (4)|0.57;
Tungsten Key|2.28;Tungsten Key (2)|1.14;Tungsten Key (4)|0.57;Skeleton Key|0.28;Frozen Scute|0.28;Caged Wisp|0.28;Shattered Locket|0.14;
Opal Crystal|3.41;Onyx Crystal|3.41;Peridot Crystal|3.41;Citrine Crystal|3.41;Aquamarine Crystal|3.41""",
}


def rows(s):
    d = {}
    for cell in s.replace("\n", "").split(";"):
        item, pct = cell.split("|")
        item = re.sub(r"\s*\(\d+\)$", "", item.strip())
        d[item] = d.get(item, 0) + float(pct)
    return d


C = {"_source": "https://hypixelskyblock.minecraft.wiki/Corpse per-roll % (2026-09-13), quantity variants merged; weight = % x100; TUNGSTEN = wiki's shared Umber/Tungsten table"}
for t, s in CORPSE_ROWS.items():
    C[t] = [{"name": n, "weight": W(w)} for n, w in rows(s).items()]
C["TUNGSTEN"] = C["UMBER"]
json.dump(C, open(os.path.join(ROOT, "corpses.json"), "w", encoding="utf-8"), indent=1)
print({k: len(v) for k, v in out.items() if k != "_source"}, {k: len(v) for k, v in C.items() if k != "_source"})
