#!/usr/bin/env python3
"""CSV -> SQLite. Usage: python tools/build_db.py [csv] [out.db]"""
import csv, sqlite3, sys, unicodedata, os

SRC = sys.argv[1] if len(sys.argv) > 1 else "data/words.csv"
OUT = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/dictionary.db"

def norm(s: str) -> str:
    s = unicodedata.normalize("NFD", s.strip().lower())
    return "".join(c for c in s if unicodedata.category(c) != "Mn")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
if os.path.exists(OUT):
    os.remove(OUT)

db = sqlite3.connect(OUT)
db.executescript("""
CREATE TABLE entries(
  id INTEGER PRIMARY KEY,
  mg TEXT NOT NULL, mg_norm TEXT NOT NULL,
  en TEXT NOT NULL, en_norm TEXT NOT NULL,
  pos TEXT, example_mg TEXT, example_en TEXT
);
CREATE INDEX idx_mg ON entries(mg_norm);
CREATE INDEX idx_en ON entries(en_norm);
""")

seen, n = set(), 0
with open(SRC, encoding="utf-8", newline="") as f:
    for r in csv.DictReader(f):
        mg, en = (r.get("mg") or "").strip(), (r.get("en") or "").strip()
        if not mg or not en:
            continue
        key = (norm(mg), norm(en), (r.get("pos") or "").strip())
        if key in seen:
            continue
        seen.add(key)
        db.execute(
            "INSERT INTO entries(mg,mg_norm,en,en_norm,pos,example_mg,example_en) VALUES(?,?,?,?,?,?,?)",
            (mg, norm(mg), en, norm(en), r.get("pos"), r.get("example_mg"), r.get("example_en")),
        )
        n += 1
db.commit()
db.execute("VACUUM")
db.close()
print(f"OK: {n} entries -> {OUT}")
