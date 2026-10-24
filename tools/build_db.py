#!/usr/bin/env python3
"""CSV (+ images) -> SQLite.
Usage: python tools/build_db.py [--out db] [--images dir] curated.csv other.csv ...
CSV columns: mg,en,pos,definition_mg,definition_en,example_mg,example_en,image,verified
`image` = file name inside data/images/ (e.g. akoho.webp). Missing files are skipped with a warning.
"""
import argparse, csv, sqlite3, unicodedata, os

ap = argparse.ArgumentParser()
ap.add_argument("csvs", nargs="*", default=["data/words.csv"],
                help="CSV files, in priority order (first wins on duplicates)")
ap.add_argument("--out", default="app/src/main/assets/dictionary.db")
ap.add_argument("--images", default="data/images")
args = ap.parse_args()
OUT, IMG = args.out, args.images


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
  pos TEXT,
  definition_mg TEXT, definition_en TEXT,
  example_mg TEXT, example_en TEXT,
  image BLOB,
  verified INTEGER DEFAULT 0,
  source TEXT DEFAULT 'curated'
);
CREATE INDEX idx_mg ON entries(mg_norm);
CREATE INDEX idx_en ON entries(en_norm);
""")

seen, n, imgs = set(), 0, 0
for SRC in args.csvs:
    if not os.path.isfile(SRC):
        print(f"WARN missing CSV skipped: {SRC}")
        continue
    with open(SRC, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            g = lambda k: (r.get(k) or "").strip()
            mg, en = g("mg"), g("en")
            if not mg or not en:
                continue
            key = (norm(mg), norm(en))
            if key in seen:
                continue
            seen.add(key)
            blob = None
            if g("image"):
                path = os.path.join(IMG, g("image"))
                if os.path.isfile(path):
                    with open(path, "rb") as fi:
                        blob = fi.read()
                    imgs += 1
                    if len(blob) > 150_000:
                        print(f"WARN large image ({len(blob)//1024} KB): {g('image')}")
                else:
                    print(f"WARN image not found: {path}")
            db.execute(
                "INSERT INTO entries(mg,mg_norm,en,en_norm,pos,definition_mg,definition_en,"
                "example_mg,example_en,image,verified,source) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (mg, norm(mg), en, norm(en), g("pos"), g("definition_mg"), g("definition_en"),
                 g("example_mg"), g("example_en"), blob, 1 if g("verified") == "1" else 0,
                 g("source") or "curated"),
            )
            n += 1
db.commit()
db.execute("VACUUM")
db.close()
print(f"OK: {n} entries, {imgs} images -> {OUT} ({os.path.getsize(OUT)//1024} KB)")
