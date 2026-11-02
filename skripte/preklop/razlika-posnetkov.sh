#!/bin/bash
# Pokaze, KAJ se razlikuje v odgovoru API-ja za eno pot, ki jo je primerjava
# posnetkov (posnetek-api.sh primerjaj) oznacila kot razliko. Ne spremeni nicesar.
#
# Uporaba:
#   skripte/preklop/razlika-posnetkov.sh <pot> [mapa1] [mapa2]
#   skripte/preklop/razlika-posnetkov.sh /api/v1/lestvica
#
# Privzeta posnetka sta tista iz generalke (produkcija proti PostgreSQL).
# Datoteka posnetka se imenuje po prvih 12 znakih SHA-1 zgoscenke poti.

set -uo pipefail

POT="${1:?pot, npr. /api/v1/lestvica}"
A="${2:-/srv/turnirko/generalka/posnetek-produkcija}"
B="${3:-/srv/turnirko/generalka/posnetek-generalka}"

IME=$(printf '%s' "$POT" | sha1sum | cut -c1-12)
for mapa in "$A" "$B"; do
	[ -f "$mapa/$IME.json" ] || { echo "Datoteke $mapa/$IME.json ni."; exit 1; }
done
ls -l "$A/$IME.json" "$B/$IME.json"
if cmp -s "$A/$IME.json" "$B/$IME.json"; then
	echo "Datoteki sta enaki."
	exit 0
fi

command -v python3 > /dev/null || { echo "python3 ni namescen; primerjaj rocno: diff $A/$IME.json $B/$IME.json"; exit 1; }
python3 - "$A/$IME.json" "$B/$IME.json" << 'PY'
import json
import sys

a = json.load(open(sys.argv[1], encoding="utf-8"))
b = json.load(open(sys.argv[2], encoding="utf-8"))
razlike = []


def oznaka(element, i):
    if isinstance(element, dict):
        for k in ("id", "idIgralec", "idIgralca", "idLiga", "idTurnir"):
            if k in element:
                return "[%d:%s=%s]" % (i, k, element[k])
    return "[%d]" % i


def primerjaj(x, y, pot):
    if type(x) is not type(y):
        razlike.append((pot, x, y))
    elif isinstance(x, dict):
        for k in sorted(set(x) | set(y)):
            if k not in x or k not in y:
                razlike.append((pot + "." + k, x.get(k, "<manjka>"), y.get(k, "<manjka>")))
            else:
                primerjaj(x[k], y[k], pot + "." + k)
    elif isinstance(x, list):
        if len(x) != len(y):
            razlike.append((pot + ".dolzina", len(x), len(y)))
        for i, (p, q) in enumerate(zip(x, y)):
            primerjaj(p, q, pot + oznaka(p, i))
    elif x != y:
        razlike.append((pot, x, y))


primerjaj(a, b, "")
print("Razlik: %d" % len(razlike))
for pot, x, y in razlike[:25]:
    print("  %s\n      produkcija: %.160s\n      drugi:      %.160s" % (pot or "(koren)", x, y))
PY
