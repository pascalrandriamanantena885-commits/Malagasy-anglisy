#!/usr/bin/env python3
"""kaikki.org Malagasy JSONL (from English Wiktionary, CC BY-SA) -> CSV.
Usage: python tools/import_kaikki.py in.jsonl out.csv
Glosses are English only; definition_mg stays empty (to be filled / verified by hand).
"""
import csv, json, sys

SRC, OUT = sys.argv[1], sys.argv[2]
SKIP_POS = {"prefix", "suffix", "circumfix", "infix", "character", "affix"}
POS = {"intj": "interj", "num": "numeral", "name": "proper noun", "conj": "conj",
       "prep": "prep", "pron": "pron", "det": "det"}
SKIP_START = ("alternative form of", "alternative spelling of", "obsolete", "misspelling of",
              "eye dialect", "form of")

def short(g: str) -> str:
    en = g.split(";")[0].strip()
    if len(en) > 60:
        en = en[:60].rsplit(" ", 1)[0]
    return en

rows, seen, per = [], set(), {}
with open(SRC, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("lang_code") != "mg":
            continue
        word, pos = (e.get("word") or "").strip(), e.get("pos") or ""
        if not word or pos in SKIP_POS:
            continue
        for s in e.get("senses", []):
            if s.get("form_of") or s.get("alt_of"):
                continue
            gl = s.get("glosses") or []
            if not gl:
                continue
            g = gl[-1].strip()
            if not g or g.lower().startswith(SKIP_START):
                continue
            en = short(g)
            key = (word.lower(), en.lower(), pos)
            if key in seen or per.get((word.lower(), pos), 0) >= 4:
                continue
            seen.add(key)
            per[(word.lower(), pos)] = per.get((word.lower(), pos), 0) + 1
            exmg = exen = ""
            for ex in s.get("examples", []) or []:
                if ex.get("text") and ex.get("english"):
                    exmg, exen = ex["text"].strip(), ex["english"].strip()
                    break
            rows.append([word, en, POS.get(pos, pos), "", g, exmg, exen, "", "0", "wiktionary"])

rows.sort(key=lambda r: r[0].lower())
with open(OUT, "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f)
    w.writerow(["mg", "en", "pos", "definition_mg", "definition_en", "example_mg",
                "example_en", "image", "verified", "source"])
    w.writerows(rows)
print(f"OK: {len(rows)} senses -> {OUT}")
