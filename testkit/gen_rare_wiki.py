"""Rebuilds the slayer + diana sections of rare_drops.json from hypixelskyblock.minecraft.wiki.
Slayer: every item named in a boss page's Drops section (uniform weight; filler only, the real drop is the winner).
Catacombs section is left as-is (NEU rngscore, MIT)."""
import json, os, re, urllib.request, urllib.parse

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "skycase", "pools")
API = "https://hypixelskyblock.minecraft.wiki/api.php?action=parse&prop=wikitext&format=json&page="
UA = {"User-Agent": "SkyCase-pools/0.1 (github.com/cliqwq/SkyCase)"}
BOSSES = {"Revenant Horror": "Revenant_Horror", "Tarantula Broodfather": "Tarantula_Broodfather", "Sven Packmaster": "Sven_Packmaster",
          "Voidgloom Seraph": "Voidgloom_Seraph", "Inferno Demonlord": "Inferno_Demonlord", "Riftstalker Bloodfiend": "Riftstalker_Bloodfiend"}
ITEM = re.compile(r"\{\{(?:Item|Slot)\|([^|}]+)")
DROP = re.compile(r"\|\s*drop\s*=\s*([^|}\n]+)")
SKIP = {"Enchanted Book Bundle"}


def fetch(page):
    req = urllib.request.Request(API + urllib.parse.quote(page), headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)["parse"]["wikitext"]["*"]


def drops_section(text):
    m = re.search(r"==\s*Drops\s*==(.*?)(\n==[^=]|\Z)", text, re.S)
    return m.group(1) if m else ""


def names(section):
    out = []
    for n in ITEM.findall(section) + DROP.findall(section):
        n = n.split(",")[0].strip()
        n = re.sub(r"\s*\(.*\)$", "", n)
        if not n or n in SKIP or n.lower().startswith("enchanted book bundle"):
            continue
        if n not in out:
            out.append(n)
    return out


# Diana: hypixelskyblock.minecraft.wiki/Mythological_Ritual (2026-09-13), approximate %; guaranteed claw/dye rows down-weighted.
DIANA = {"Griffin Feather": 60, "Mythos Fragment": 8, "Braided Griffin Feather": 1, "Ancient Claw": 20, "Enchanted Ancient Claw": 10,
         "Enchanted Gold": 10, "Hilt of Revelations": 2, "Crochet Tiger Plushie": 1, "Washed-up Souvenir": 1, "Cretan Urn": 1,
         "Antique Remedies": 1, "Dwarf Turtle Shelmet": 1, "Daedalus Stick": 1, "Minos Relic": 1, "Brain Food": 1, "Chimera I": 2,
         "Fateful Stinger": 1, "Manti-core": 1, "Crown of Greed": 2, "Shimmering Wool": 1, "Mythological Dye": 1}


def main():
    path = os.path.join(ROOT, "rare_drops.json")
    data = json.load(open(path, encoding="utf-8"))
    data["_source_slayer"] = "hypixelskyblock.minecraft.wiki boss pages, Drops sections (2026-09-13); uniform weights"
    data["_source_diana"] = "hypixelskyblock.minecraft.wiki/Mythological_Ritual (2026-09-13); uniform weights (flat list)"
    slayer = {}
    for boss, page in BOSSES.items():
        ns = names(drops_section(fetch(page)))
        slayer[boss] = ["name:" + n for n in ns]
        print(boss, len(ns), ns[:6])
    data["slayer"] = slayer
    data["diana"] = ["name:" + n for n in DIANA]
    json.dump(data, open(path, "w", encoding="utf-8"), indent=1)


if __name__ == "__main__":
    main()
