"""Regenerates src/main/resources/skycase/pools/*.json from vendored source data.

Not run by the build -- a throwaway-but-kept authoring script (task 10 brief). Inputs:
  - SkyOcean clone (MIT): src/repo/dungeon_chests.json, src/repo/vanguard.jsonc
  - SkyHanni-REPO (MIT), fetched via `gh api`: constants/SlayerProfitTrackerItems.json, constants/DianaDrops.json
  - NotEnoughUpdates-REPO (MIT), fetched via `gh api`: constants/rngscore.json

Usage: py testkit/gen.py <path-to-SkyOcean-clone> <path-to-fetched-json-dir>
The fetched-json-dir must contain SlayerProfitTrackerItems.json, DianaDrops.json, rngscore.json
(fetch with: gh api repos/<owner>/<repo>/contents/constants/<file> --jq .content | base64 -d > out.json)
"""
import json
import re
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/skycase/pools"


def strip_jsonc_comments(text: str) -> str:
    # vanguard.jsonc only uses trailing "// ..." line comments; no strings contain "//".
    return re.sub(r"//.*", "", text)


def gen_dungeon_chests(skyocean_dir: Path):
    src = skyocean_dir / "src/repo/dungeon_chests.json"
    data = json.loads(src.read_text(encoding="utf-8"))
    (OUT / "dungeon_chests.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"dungeon_chests.json: {len(data)} floors, copied verbatim from {src}")


VALUABLES = {"item:dye_frostbitten", "item:shattered_pendant", "item:caged_wisp"}
TYPE_PLATE_KEY_WEIGHTS = {"UMBER": 30, "TUNGSTEN": 30}  # weight of the *_plate; *_key is always 140


def gen_corpses(skyocean_dir: Path):
    src = skyocean_dir / "src/repo/vanguard.jsonc"
    raw = strip_jsonc_comments(src.read_text(encoding="utf-8"))
    vanguard_json = json.loads(raw)
    items: dict = vanguard_json["items"]

    vanguard_pool = [{"id": k, "weight": w} for k, w in items.items()]

    # base pool shared by the three mineral corpses: Vanguard's items minus the Vanguard-only
    # valuables and minus the umber/tungsten plate+key (each type gets its own below).
    base = [
        {"id": k, "weight": w}
        for k, w in items.items()
        if k not in VALUABLES and k not in ("item:umber_plate", "item:umber_key", "item:tungsten_plate", "item:tungsten_key")
    ]

    def with_plate_key(type_name: str) -> list:
        low = type_name.lower()
        return base + [
            {"id": f"item:{low}_plate", "weight": 30},
            {"id": f"item:{low}_key", "weight": 140},
        ]

    out = {
        "_note": "weights assumed for LAPIS/TUNGSTEN/UMBER (hand-authored from the VANGUARD list)",
        "VANGUARD": vanguard_pool,
        "LAPIS": with_plate_key("LAPIS"),
        "TUNGSTEN": with_plate_key("TUNGSTEN"),
        "UMBER": with_plate_key("UMBER"),
    }
    (OUT / "corpses.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    print(f"corpses.json: VANGUARD={len(vanguard_pool)} entries (from {src}), LAPIS/TUNGSTEN/UMBER hand-authored")


def gen_kuudra():
    # Hand-authored; ids in SkyOcean's "item:foo" form. Weights assumed -- flat/plausible, not measured.
    # Every id is validated against SkyBlockItemsRepo at runtime by LootPools; unresolved ids are
    # dropped silently (one WARN listing them).
    common = [
        {"id": "item:crimson_essence", "weight": 40},
        {"id": "item:kuudra_teeth", "weight": 25},
        {"id": "item:heavy_pearl", "weight": 15},
        {"id": "item:attribute_shard_mana_pool", "weight": 8},
        {"id": "item:attribute_shard_veteran", "weight": 8},
        {"id": "item:attribute_shard_warrior", "weight": 8},
    ]
    armor_pieces = lambda prefix, weight: [
        {"id": f"item:{prefix}_helmet", "weight": weight},
        {"id": f"item:{prefix}_chestplate", "weight": weight},
        {"id": f"item:{prefix}_leggings", "weight": weight},
        {"id": f"item:{prefix}_boots", "weight": weight},
    ]
    pools = {
        "BASIC": common + armor_pieces("crimson", 10),
        "HOT": common + armor_pieces("crimson", 10) + armor_pieces("hollow", 6),
        "BURNING": common + armor_pieces("hollow", 8) + armor_pieces("aurora", 5),
        "FIERY": common + armor_pieces("aurora", 6) + armor_pieces("terror", 4),
        "INFERNAL": common + armor_pieces("terror", 5) + armor_pieces("fervor", 3) + [
            {"id": "item:kuudra_follower_artifact", "weight": 2},
            {"id": "item:enrager", "weight": 3},
            {"id": "item:ferocious_mana", "weight": 1},
            {"id": "item:hollow_wand", "weight": 1},
            {"id": "item:ragnarock_axe", "weight": 1},
        ],
    }
    out = {"_note": "weights assumed (hand-authored); armor-tier mapping is a plausible guess, not measured drop rates"}
    out.update(pools)
    (OUT / "kuudra.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    print("kuudra.json: hand-authored, 5 tiers")


BOOK_VARIANT_RX = re.compile(r";\d+$")


def clean_ids(ids: list) -> list:
    """Drop `;N` enchant-book variants and SKYBLOCK_COIN; NEU ids are already resolvable as-is."""
    out = []
    for i in ids:
        if i == "SKYBLOCK_COIN":
            continue
        if BOOK_VARIANT_RX.search(i):
            continue
        out.append(i)
    return out


def gen_rare_drops(fetch_dir: Path):
    slayer_src = json.loads((fetch_dir / "SlayerProfitTrackerItems.json").read_text(encoding="utf-8"))
    diana_src = json.loads((fetch_dir / "DianaDrops.json").read_text(encoding="utf-8"))
    neu_src = json.loads((fetch_dir / "rngscore.json").read_text(encoding="utf-8"))

    slayer = {boss: clean_ids(ids) for boss, ids in slayer_src["slayers"].items()}
    catacombs = {floor: clean_ids(list(items.keys())) for floor, items in neu_src["catacombs"].items()}
    diana = clean_ids(diana_src["diana_drops"])

    out = {"slayer": slayer, "catacombs": catacombs, "diana": diana}
    (OUT / "rare_drops.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    print(f"rare_drops.json: slayer bosses={len(slayer)}, catacombs floors={len(catacombs)}, diana={len(diana)}")


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    skyocean_dir = Path(sys.argv[1])
    fetch_dir = Path(sys.argv[2])
    OUT.mkdir(parents=True, exist_ok=True)
    gen_dungeon_chests(skyocean_dir)
    gen_corpses(skyocean_dir)
    gen_kuudra()
    gen_rare_drops(fetch_dir)


if __name__ == "__main__":
    main()
