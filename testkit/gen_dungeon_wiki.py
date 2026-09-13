"""Builds dungeon_chests.json from hypixelskyblock.minecraft.wiki loot pages (MediaWiki API, wikitext).
weight = average per-run chance (no bonuses) x100; guaranteed rows (>=99%) skipped; books -> "Enchanted Book"."""
import json, os, re, sys, urllib.request, urllib.parse

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "skycase", "pools")
API = "https://hypixelskyblock.minecraft.wiki/api.php?action=parse&prop=wikitext&format=json&page="
UA = {"User-Agent": "SkyCase-pools/0.1 (github.com/qtclover/SkyCase)"}
ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII"]
CHESTS = ["wood", "gold", "diamond", "emerald", "obsidian", "bedrock"]

ENTRY = re.compile(r"\{\{Catacombs Chest Loot Table Entry(.*?)\n\}\}", re.S)
FIELD = re.compile(r"\|\s*(\w+)\s*=\s*(.*)")
CHANCE = re.compile(r"\{\{Chance\|([\d.]+)%")


def fetch(page):
    req = urllib.request.Request(API + urllib.parse.quote(page), headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        d = json.load(r)
    if "parse" not in d:
        return None
    return d["parse"]["wikitext"]["*"]


def clean_name(entry):
    e = re.sub(r"\[\[([^|\]]*\|)?([^\]]*)\]\]", r"\2", entry).strip()
    if e.startswith("Enchanted Book"):
        return "Enchanted Book"
    e = re.sub(r"&[^&]*&", "", e).strip()  # strip "&Bank 1&"-style qualifiers
    return e


def parse(text):
    out = {c: {} for c in CHESTS}
    for block in ENTRY.findall(text):
        f = {}
        for line in block.splitlines():
            m = FIELD.match(line.strip())
            if m:
                f[m.group(1)] = m.group(2).strip()
        chest = f.get("chest", "").lower()
        if chest not in out:
            continue
        m = CHANCE.search(f.get("average_chance_no_bonuses", ""))
        if not m:
            continue
        pct = float(m.group(1))
        if pct >= 99 or pct <= 0:
            continue
        name = clean_name(f.get("entry", ""))
        if not name:
            continue
        out[chest][name] = out[chest].get(name, 0) + pct
    # cap any single entry (merged books mostly) at 20% of the chest so the strip isn't wall-to-wall books
    res = {}
    for c, d in out.items():
        if not d:
            continue
        total = sum(d.values())
        res[c] = [{"name": n, "weight": max(1, int(round(min(p, total * 0.2) * 100)))} for n, p in d.items()]
    return res


def main():
    result = {"_source": "https://hypixelskyblock.minecraft.wiki The_Catacombs_-_Floor_N/Loot + /Master_Mode_Loot (2026-09-13); weight = avg per-run chance (no bonuses) x100; guaranteed rows dropped; all books as Enchanted Book"}
    for i, r in enumerate(ROMAN, 1):
        for key, page in ((f"F{i}", f"The_Catacombs_-_Floor_{r}/Loot"), (f"M{i}", f"The_Catacombs_-_Floor_{r}/Master_Mode_Loot")):
            text = fetch(page)
            if not text:
                print("MISSING", key, page, file=sys.stderr)
                continue
            result[key] = parse(text)
            print(key, {c: len(v) for c, v in result[key].items()})
    json.dump(result, open(os.path.join(ROOT, "dungeon_chests.json"), "w", encoding="utf-8"), indent=1)


if __name__ == "__main__":
    main()
