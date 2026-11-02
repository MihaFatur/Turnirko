#!/bin/bash
# Pokaze, KAJ se razlikuje v odgovoru API-ja za eno pot, ki jo je primerjava
# posnetkov (posnetek-api.sh primerjaj) oznacila kot razliko. Ne spremeni nicesar.
#
# Uporaba:
#   skripte/preklop/razlika-posnetkov.sh [--tiho] <pot> [mapa1] [mapa2]
#   skripte/preklop/razlika-posnetkov.sh /api/v1/lestvica
#
# Privzeta posnetka sta tista iz generalke (produkcija proti PostgreSQL).
# Datoteka posnetka se imenuje po prvih 12 znakih SHA-1 zgoscenke poti.
#
# Seznami, ki so MNOZICE (vrstni red ne nosi pomena), se primerjajo kot
# mnozice: SQLite jih vrne v vrstnem redu zapisa, PostgreSQL po ORDER BY, vsebina
# pa je ista. Sem sodi samo `idjiLig` (id-ji lig igralca). Vsak drug seznam, tudi
# seznam stevil (npr. potek ratinga), se primerja po mestih: tam je vrstni red
# podatek.
#
# --tiho: nic ne izpise; izhodna koda 0 = odgovora sta enaka (po tem pravilu),
# 1 = pravi razliki, 2 = napaka (datoteke ni, ni python3).

set -uo pipefail

TIHO=0
if [ "${1:-}" = "--tiho" ]; then
	TIHO=1
	shift
fi
POT="${1:?pot, npr. /api/v1/lestvica}"
A="${2:-/srv/turnirko/generalka/posnetek-produkcija}"
B="${3:-/srv/turnirko/generalka/posnetek-generalka}"

IME=$(printf '%s' "$POT" | sha1sum | cut -c1-12)
for mapa in "$A" "$B"; do
	[ -f "$mapa/$IME.json" ] || { [ "$TIHO" -eq 1 ] || echo "Datoteke $mapa/$IME.json ni."; exit 2; }
done
[ "$TIHO" -eq 1 ] || ls -l "$A/$IME.json" "$B/$IME.json"
if cmp -s "$A/$IME.json" "$B/$IME.json"; then
	[ "$TIHO" -eq 1 ] || echo "Datoteki sta enaki."
	exit 0
fi

command -v python3 > /dev/null || { [ "$TIHO" -eq 1 ] || echo "python3 ni namescen; primerjaj rocno: diff $A/$IME.json $B/$IME.json"; exit 2; }
python3 - "$TIHO" "$A/$IME.json" "$B/$IME.json" << 'PY'
import json
import sys
from collections import Counter

tiho = sys.argv[1] == "1"
a = json.load(open(sys.argv[2], encoding="utf-8"))
b = json.load(open(sys.argv[3], encoding="utf-8"))
NABORI = {"idjiLig"}
razlike = []
preurejeni = 0


def oznaka(element, i):
    if isinstance(element, dict):
        for k in ("id", "idIgralec", "idIgralca", "idLiga", "idTurnir"):
            if k in element:
                return "[%d:%s=%s]" % (i, k, element[k])
    return "[%d]" % i


def primerjaj(x, y, pot, kljuc):
    global preurejeni
    if type(x) is not type(y):
        razlike.append((pot, x, y))
    elif isinstance(x, dict):
        for k in sorted(set(x) | set(y)):
            if k not in x or k not in y:
                razlike.append((pot + "." + k, x.get(k, "<manjka>"), y.get(k, "<manjka>")))
            else:
                primerjaj(x[k], y[k], pot + "." + k, k)
    elif isinstance(x, list):
        if kljuc in NABORI:
            if Counter(map(json.dumps, x)) != Counter(map(json.dumps, y)):
                razlike.append((pot, x, y))
            elif x != y:
                preurejeni += 1
            return
        if len(x) != len(y):
            razlike.append((pot + ".dolzina", len(x), len(y)))
        for i, (p, q) in enumerate(zip(x, y)):
            primerjaj(p, q, pot + oznaka(p, i), None)
    elif x != y:
        razlike.append((pot, x, y))


primerjaj(a, b, "", None)
if not tiho:
    print("Pravih razlik: %d; seznamov z isto vsebino v drugem vrstnem redu (%s): %d"
          % (len(razlike), ", ".join(sorted(NABORI)), preurejeni))
    for pot, x, y in razlike[:25]:
        print("  %s\n      produkcija: %.160s\n      drugi:      %.160s" % (pot or "(koren)", x, y))
sys.exit(1 if razlike else 0)
PY
