#!/bin/bash
# Vaja obnove: ali se zadnja kopija res da obnoviti?
#
# Uporaba:   vaja-obnove.sh [datoteka.dump]      (brez argumenta: zadnja nocna)
#
# Kopija, ki je nihce ni obnovil, ni kopija. Skripta kopijo obnovi v
# ZACASEN vsebnik (produkcijska baza ostane nedotaknjena), prestej vrstice
# vsake tabele in jih primerja s stetjem, zapisanim ob kopiji
# (datoteka.dump.stevilo). Na koncu vsebnik zbrise.
#
# Poganjaj jo vsak mesec (crontab):
#     30 4 2 * * /srv/turnirko/skripte/vaja-obnove.sh >> /srv/turnirko/dnevniki/vaja-obnove.log 2>&1
# Izhodna koda 0 pomeni, da je obnova uspela in se stevila ujemajo.

set -euo pipefail

MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"
KAM="${TURNIRKO_KOPIJE:-$MAPA/kopije}"
IME="turnirko-vaja-$$"

zapis() { echo "$(date '+%F %T')  vaja-obnove  $*"; }
napaka() {
	zapis "NAPAKA: $*" >&2
	exit 1
}

KOPIJA="${1:-}"
if [ -z "$KOPIJA" ]; then
	KOPIJA=$(ls -1t "$KAM"/noc/turnirko-*.dump 2> /dev/null | head -n 1 || true)
fi
[ -n "$KOPIJA" ] && [ -f "$KOPIJA" ] || napaka "kopije ni (iskano v $KAM/noc)"
STEVILO="$KOPIJA.stevilo"
[ -f "$STEVILO" ] || napaka "ob kopiji ni zapisanega stetja vrstic ($STEVILO) - uporabi nocno ali rocno kopijo"

# Ista slika kot produkcijska baza: obnova na drugi razlicici ne dokazuje nicesar.
SLIKA=$(grep -E '^[[:space:]]+image: postgres:18' "$MAPA/compose.yaml" | head -n 1 | awk '{ print $2 }')
[ -n "$SLIKA" ] || napaka "slike baze ni v $MAPA/compose.yaml"

pocisti() { docker rm -f "$IME" > /dev/null 2>&1 || true; }
trap pocisti EXIT

zapis "obnavljam $KOPIJA v zacasnem vsebniku ($SLIKA)"
docker run -d --name "$IME" -e POSTGRES_PASSWORD=vaja -e POSTGRES_DB=turnirko \
	-e POSTGRES_INITDB_ARGS="--encoding=UTF8 --locale=C.UTF-8 --locale-provider=builtin --builtin-locale=C.UTF-8" \
	"$SLIKA" > /dev/null

for _ in $(seq 1 60); do
	docker exec "$IME" pg_isready -U postgres -d turnirko > /dev/null 2>&1 && break
	sleep 1
done
docker exec "$IME" pg_isready -U postgres -d turnirko > /dev/null || napaka "zacasna baza se ni zagnala"

# --no-owner in --no-privileges: vlog lastnik/app v zacasni bazi ni, podatki
# pa so enaki. Kopija je v produkciji narejena kot postgres, zato ni razlike.
T0=$(date +%s)
docker exec -i "$IME" pg_restore -U postgres -d turnirko --no-owner --no-privileges --exit-on-error < "$KOPIJA" \
	|| napaka "pg_restore je odpovedal - kopija se NE da obnoviti"
zapis "obnova je trajala $(($(date +%s) - T0)) s"

docker exec "$IME" psql -U postgres -d turnirko -At -F $'\t' -c "
	SELECT table_name,
	       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text
	FROM information_schema.tables
	WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
	ORDER BY table_name" > "/tmp/vaja-$$.stevilo"
trap 'pocisti; rm -f "/tmp/vaja-$$.stevilo"' EXIT

if diff -u "$STEVILO" "/tmp/vaja-$$.stevilo"; then
	VRSTIC=$(awk -F'\t' '{ s += $2 } END { print s }' "$STEVILO")
	zapis "OK: obnovljenih $(wc -l < "$STEVILO") tabel, skupaj $VRSTIC vrstic, vsa stetja se ujemajo"
else
	napaka "stevila vrstic po obnovi se ne ujemajo s stetjem ob kopiji (razlike zgoraj)"
fi
