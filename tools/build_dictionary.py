#!/usr/bin/env python3
"""Build app/src/main/assets/dictionary.db from Princeton WordNet 3.0 dict files.

Usage: python3 -I tools/build_dictionary.py <wordnet-dict-dir> <out.db>
Source: https://raw.githubusercontent.com/nltk/nltk_data/gh-pages/packages/corpora/wordnet.zip
Schema: e(w TEXT PRIMARY KEY, s TEXT) WITHOUT ROWID
  w = lowercase headword (spaces for phrases)
  s = senses, one per line: "<pos>\t<definition>" (pos: noun/verb/adjective/adverb)
Max MAX_PER_POS senses per part of speech, in WordNet frequency order.
"""
import os, re, sqlite3, sys

POS = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb"}
MAX_PER_POS = 3
SUPPLEMENT = {  # custom entries not in WordNet
    "bayanihan": "noun · Filipino\tThe spirit of a community working together to help one of its members, without expecting payment.",
}

def load_glosses(d, f):
    g = {}
    with open(os.path.join(d, "data." + f), encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition(" | ")
            off = head.split(" ", 1)[0]
            gloss = gloss.strip()
            m = re.search(r';\s+"', gloss)  # drop usage examples
            if m:
                gloss = gloss[:m.start()]
            g[off] = gloss.rstrip("; ").strip()
    return g

def main(d, out):
    if os.path.exists(out):
        os.remove(out)
    words = {}
    for f, label in POS.items():
        gl = load_glosses(d, f)
        with open(os.path.join(d, "index." + f), encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("  "):
                    continue
                p = line.split()
                lemma = p[0].lower().replace("_", " ")
                if not re.fullmatch(r"[a-z]+(?: [a-z]+){0,2}", lemma):
                    continue
                n_ptr = int(p[3])
                offs = p[6 + n_ptr:]
                defs = [gl[o] for o in offs if o in gl][:MAX_PER_POS]
                words.setdefault(lemma, []).extend(label + "\t" + x for x in defs)
    for k, v in SUPPLEMENT.items():
        words[k] = [v]
    db = sqlite3.connect(out)
    db.execute("CREATE TABLE e(w TEXT PRIMARY KEY, s TEXT NOT NULL) WITHOUT ROWID")
    db.executemany("INSERT INTO e VALUES(?,?)", ((k, "\n".join(v)) for k, v in words.items() if v))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(len(words), "entries,", os.path.getsize(out), "bytes")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
