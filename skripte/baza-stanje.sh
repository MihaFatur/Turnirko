#!/bin/bash
# Dnevni pregled stanja po preklopu na PostgreSQL ("teden opazovanja",
# docs/PRENOVA-BAZE.md, razdelek 7.3). Poganjaj ga rocno ali iz crona, na
# primer ob 8. uri:
#     0 8 * * * /srv/turnirko/skripte/baza-stanje.sh >> /srv/turnirko/dnevniki/stanje.log 2>&1
#
# Pokaze: starost zadnjih kopij, velikost baze, najpocasnejse poizvedbe,
# stevilo pocasnih poizvedb in zaklepov v zadnjih 24 urah ter stevilo napak
# 409 (sociasna sprememba) in 5xx v dnevniku Caddyja.

set -uo pipefail

MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"
KAM="${TURNIRKO_KOPIJE:-$MAPA/kopije}"
cd "$MAPA" || exit 1

baza() { docker compose exec -T baza "$@"; }

echo "=== $(date '+%F %T') stanje baze ==="

echo
echo "--- Kopije (starost zadnje; nocna naj bo mlajsa od 26 ur, urna od 2 uri med 7. in 23.) ---"
for vrsta in ura noc mesec; do
	zadnja=$(ls -1t "$KAM/$vrsta"/turnirko-*.dump 2> /dev/null | head -n 1 || true)
	if [ -n "$zadnja" ]; then
		starost_min=$(( ($(date +%s) - $(stat -c %Y "$zadnja")) / 60 ))
		printf '%-6s %s  (%d min, %s KB)\n' "$vrsta" "$(basename "$zadnja")" "$starost_min" "$(du -k "$zadnja" | cut -f1)"
	else
		printf '%-6s NI KOPIJE\n' "$vrsta"
	fi
done

echo
echo "--- Velikost baze in povezave ---"
baza psql -U postgres -d turnirko -At -F ' | ' -c "
	SELECT pg_size_pretty(pg_database_size('turnirko')) AS velikost,
	       (SELECT count(*) FROM pg_stat_activity WHERE datname = 'turnirko') AS povezav,
	       (SELECT count(*) FROM pg_stat_activity WHERE datname = 'turnirko' AND state = 'idle in transaction') AS odprtih_transakcij"

echo
echo "--- 10 najpocasnejsih poizvedb po skupnem casu (pg_stat_statements od zadnje ponastavitve) ---"
baza psql -U postgres -d turnirko -c "
	SELECT calls, round(total_exec_time::numeric) AS skupaj_ms, round(mean_exec_time::numeric, 1) AS povprecje_ms,
	       round(max_exec_time::numeric) AS najvec_ms, left(regexp_replace(query, '\s+', ' ', 'g'), 90) AS poizvedba
	FROM pg_stat_statements
	WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'turnirko')
	ORDER BY total_exec_time DESC LIMIT 10"

echo
echo "--- Dnevnik baze v zadnjih 24 urah ---"
dnevnik=$(docker compose logs --since 24h --no-log-prefix baza 2>&1 || true)
printf 'pocasne poizvedbe (> 250 ms): %s\n' "$(printf '%s\n' "$dnevnik" | grep -c 'duration:' || true)"
printf 'cakanja na zaklep (> 1 s):    %s\n' "$(printf '%s\n' "$dnevnik" | grep -c 'still waiting for' || true)"
printf 'zastoji (deadlock):           %s\n' "$(printf '%s\n' "$dnevnik" | grep -c 'deadlock detected' || true)"
printf 'napake (ERROR/FATAL):         %s\n' "$(printf '%s\n' "$dnevnik" | grep -cE ' (ERROR|FATAL):' || true)"

echo
echo "--- Aplikacija v zadnjih 24 urah ---"
if [ -f dnevniki/dostop.log ]; then
	danes=$(date -d '24 hours ago' +%s)
	# Dnevnik Caddyja je JSON z vrstico na zahtevo; "ts" so sekunde od 1970.
	awk -v od="$danes" '
		match($0, /"ts":[0-9.]+/) { ts = substr($0, RSTART + 5, RLENGTH - 5) + 0; if (ts < od) next }
		match($0, /"status":[0-9]+/) {
			s = substr($0, RSTART + 9, RLENGTH - 9)
			vse++
			if (s == 409) k409++
			if (s >= 500) k5xx++
		}
		END { printf "zahtev: %d, 409 (hkratna sprememba): %d, 5xx: %d\n", vse, k409, k5xx }' dnevniki/dostop.log
fi
docker compose logs --since 24h --no-log-prefix app 2>&1 | grep -c ' ERROR ' | sed 's/^/napake ERROR v dnevniku aplikacije: /'
