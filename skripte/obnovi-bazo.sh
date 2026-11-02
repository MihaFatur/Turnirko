#!/bin/bash
# Obnova PRODUKCIJSKE baze iz kopije (pg_dump).
#
# Uporaba:   obnovi-bazo.sh kopije/noc/turnirko-20261101-032000.dump
#
# POZOR: obstojeco bazo turnirko ZBRISE in jo zamenja s kopijo. Vse, kar je
# bilo vneseno po kopiji, je izgubljeno. Zato skripta najprej naredi se
# kopijo trenutnega stanja (kopije/pred-obnovo/), ce se baza sploh odziva,
# in pred brisanjem vpraša za potrditev.
#
# Obnovitve brez zamenjave (preverjanje, ali je kopija dobra) ne dela ta
# skripta, ampak vaja-obnove.sh.
#
# Zaporedje: aplikacija se ustavi -> nova prazna baza -> pg_restore ->
# pravice vlog -> aplikacija se zazene (Flyway preveri, da je shema pravih
# razlicic).

set -euo pipefail

MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"
KAM="${TURNIRKO_KOPIJE:-$MAPA/kopije}"
KOPIJA="${1:-}"

if [ -z "$KOPIJA" ] || [ ! -f "$KOPIJA" ]; then
	echo "Uporaba: $0 <datoteka.dump>   (najdes jih v $KAM/noc, ura, mesec, rocno)" >&2
	exit 2
fi

cd "$MAPA"
baza() { docker compose exec -T baza "$@"; }

echo "Obnavljam iz: $KOPIJA ($(du -h "$KOPIJA" | cut -f1))"
echo "Baza turnirko bo ZBRISANA in nadomescena s to kopijo."
read -r -p "Za nadaljevanje vpisi OBNOVI: " POTRDITEV
[ "$POTRDITEV" = "OBNOVI" ] || { echo "Preklicano."; exit 1; }

# Preden se karkoli zbrise, mora biti kopija berljiva.
baza pg_restore -l < "$KOPIJA" | grep -q 'TABLE DATA' || { echo "NAPAKA: kopija se ne da prebrati." >&2; exit 1; }

echo "1/5 ustavljam aplikacijo"
docker compose stop app

echo "2/5 kopija trenutnega stanja (ce se baza odziva)"
mkdir -p "$KAM/pred-obnovo"
PRED="$KAM/pred-obnovo/turnirko-$(date +%Y%m%d-%H%M%S).dump"
if baza pg_dump -U postgres -d turnirko -Fc > "$PRED.delno" 2> /dev/null; then
	mv "$PRED.delno" "$PRED"
	echo "    shranjeno: $PRED"
else
	rm -f "$PRED.delno"
	echo "    baze ni bilo mogoce prebrati (kopije trenutnega stanja ni)"
fi

echo "3/5 nova prazna baza"
baza psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS turnirko WITH (FORCE)"
baza psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE turnirko OWNER turnirko_lastnik ENCODING 'UTF8' LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8' TEMPLATE template0"

echo "4/5 pg_restore"
baza pg_restore -U postgres -d turnirko --exit-on-error < "$KOPIJA"

echo "5/5 pravice vlog"
baza psql -U postgres -d turnirko -v ON_ERROR_STOP=1 <<'SQL'
REVOKE ALL ON DATABASE turnirko FROM PUBLIC;
GRANT CONNECT ON DATABASE turnirko TO turnirko_app;
GRANT USAGE ON SCHEMA public TO turnirko_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO turnirko_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO turnirko_app;
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO turnirko_app;
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO turnirko_app;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SQL

echo "Zaganjam aplikacijo"
docker compose up -d app
echo "Konec. Preveri: docker compose logs -f app  (iskati 'Started TurnirkoAplikacija')"
