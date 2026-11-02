#!/bin/bash
# Pregled pred preklopom na PostgreSQL. Nic ne spremeni v delujoci strani
# (ne ustavi aplikacije, ne dotakne se SQLite); pripravi vse, kar je mogoce
# pripraviti vnaprej. Poganjaj ga dan prej in se enkrat tik pred preklopom.
#
# Uporaba:   skripte/preklop/pred-preklopom.sh [--hitro]
#   --hitro  brez prenosa slik in prevoda aplikacije (za ponovni pregled)
#
# Izhodna koda 0 = vse pripravljeno; sicer skripta napise, kaj manjka.

set -uo pipefail
. "$(dirname "$0")/lib.sh"

HITRO=0
[ "${1:-}" = "--hitro" ] && HITRO=1
NAPAK=0
tezava() {
	zapis "TEZAVA: $*"
	NAPAK=$((NAPAK + 1))
}
ok() { zapis "ok: $*"; }

korak "Okolje"
docker info > /dev/null 2>&1 && ok "docker deluje" || tezava "docker ni dosegljiv (sudo? skupina docker?)"
docker compose version > /dev/null 2>&1 && ok "$(docker compose version --short 2> /dev/null | sed 's/^/docker compose /')" || tezava "docker compose ni namescen"
command -v sqlite3 > /dev/null && ok "sqlite3 je namescen" || tezava "manjka sqlite3 (sudo apt install -y sqlite3) - potreben za kopijo stare baze"
PROSTO_GB=$(df -Pk "$MAPA" | awk 'NR == 2 { printf "%d", $4 / 1048576 }')
[ "$PROSTO_GB" -ge 5 ] && ok "na disku je ${PROSTO_GB} GB prostora" || tezava "na disku je samo ${PROSTO_GB} GB (potrebnih vsaj 5)"
POMNILNIK_MB=$(awk '/MemTotal/ { printf "%d", $2 / 1024 }' /proc/meminfo)
zapis "pomnilnik streznika: ${POMNILNIK_MB} MB (priporoceno 4 GB; pri 2 GB v .env prepolovi BAZA_SHARED_BUFFERS, BAZA_CACHE, BAZA_DELO)"

korak ".env in compose"
for spremenljivka in TURNIRKO_BAZA_ADMIN_GESLO TURNIRKO_BAZA_LASTNIK_GESLO TURNIRKO_BAZA_APP_GESLO; do
	vrednost=$(iz_env "$spremenljivka")
	case "$vrednost" in
		"") tezava "$spremenljivka ni nastavljen v .env" ;;
		tu-vpisi*) tezava "$spremenljivka je se vzorec iz .env.primer" ;;
		*) ok "$spremenljivka je nastavljen" ;;
	esac
done
docker compose config -q 2> /dev/null && ok "compose.yaml je veljaven" || tezava "docker compose config javlja napako (zazeni brez -q)"

korak "Stara baza SQLite"
SQLITE="$MAPA/podatki/turnirko.db"
if [ -f "$SQLITE" ]; then
	ok "baza obstaja ($(du -h "$SQLITE" | cut -f1))"
	[ "$(sqlite3 -readonly "$SQLITE" 'PRAGMA integrity_check;' 2> /dev/null)" = "ok" ] && ok "integrity_check: ok" || tezava "integrity_check SQLite ni ok"
	V_SQLITE=$(sqlite3 -readonly "$SQLITE" "SELECT max(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1;" 2> /dev/null)
	V_KODA=$(ls zaledje/src/main/resources/db/migration/postgresql/ 2> /dev/null | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p' | sort -n | tail -n 1)
	if [ -n "$V_SQLITE" ] && [ "$V_SQLITE" = "$V_KODA" ]; then
		ok "shema SQLite (V$V_SQLITE) se ujema z zadnjo migracijo PostgreSQL (V$V_KODA)"
	else
		tezava "SQLite je na V${V_SQLITE:-?}, koda za PostgreSQL pa na V${V_KODA:-?}: vsaka migracija mora imeti dvojnico v obeh mapah, sicer prenos ne gre"
	fi
else
	tezava "baze $SQLITE ni"
fi

korak "Baza PostgreSQL"
if [ "$HITRO" -eq 0 ]; then
	docker compose pull baza 2>&1 | tail -n 2 | sed 's/^/    /'
fi
docker compose up -d baza > /dev/null 2>&1 || tezava "baza se ni zagnala (docker compose logs baza)"
if caka_zdrav baza 90; then
	ok "baza je zdrava"
	VLOGE=$(docker compose exec -T baza psql -U postgres -d turnirko -At -c "SELECT string_agg(rolname, ',' ORDER BY rolname) FROM pg_roles WHERE rolname LIKE 'turnirko_%'" 2> /dev/null || true)
	[ "$VLOGE" = "turnirko_app,turnirko_lastnik" ] && ok "vlogi turnirko_lastnik in turnirko_app obstajata" || tezava "vlog ni ('$VLOGE'): zvezek turnirko_baza je bil ustvarjen brez skripte 00-vloge.sh"
	TABEL=$(docker compose exec -T baza psql -U postgres -d turnirko -At -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'" 2> /dev/null || echo "?")
	[ "$TABEL" = "0" ] && ok "baza je prazna (brez tabel)" || tezava "baza ni prazna ($TABEL tabel): prenos gre samo v prazno bazo (izprazni z: docker compose down baza && docker volume rm \$(docker volume ls -q | grep turnirko_baza))"
else
	tezava "baza ne postane zdrava v 90 s (docker compose logs baza)"
fi

korak "Caddy (stran Vzdrzevanje)"
if docker compose ps -q caddy > /dev/null 2>&1 && [ -n "$(docker compose ps -q caddy)" ]; then
	docker compose exec -T caddy caddy validate --config /etc/caddy/Caddyfile > /dev/null 2>&1 && ok "Caddyfile je veljaven" || tezava "Caddyfile ni veljaven (docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile)"
	docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile > /dev/null 2>&1 && ok "Caddy je ponovno prebral nastavitve (stran Vzdrzevanje je aktivna)" || tezava "Caddy ni sprejel nove nastavitve"
else
	tezava "vsebnik caddy ne tece"
fi

if [ "$HITRO" -eq 0 ]; then
	korak "Prevod nove slike aplikacije (traja nekaj minut; stran med tem deluje)"
	if docker compose build app > "$POT_DNEVNIK/build.log" 2>&1; then
		ok "slika aplikacije zgrajena"
	else
		tezava "prevod slike ni uspel (glej $POT_DNEVNIK/build.log)"
	fi
fi

korak "Povzetek"
if [ "$NAPAK" -eq 0 ]; then
	zapis "VSE PRIPRAVLJENO. Naslednji korak: skripte/preklop/preklop.sh (ob dogovorjeni uri)."
	exit 0
fi
zapis "$NAPAK tezav - odpravi jih in ponovi."
exit 1
