#!/bin/bash
# Vklop test.turnirko-nt.si v produkcijskem Caddyju. To je EDINI korak, ki se
# dotakne produkcije: Caddy se za nekaj sekund znova zazene (stran takrat kaze
# stran Vzdrzevanje).
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/vklopi-caddy.sh
#
# Predpogoji (skripta jih preveri):
#   - DNS: zapis A "test" ze kaze na ta streznik. Caddy certifikat dobi od Let's
#     Encrypt samo, ce DNS ze velja; neuspeli poskusi pa se stejejo v omejitev
#     (5 neuspehov na uro), zato ne smemo hiteti.
#   - v .env je TEST_ZETON (ga ustvari nastavi.sh);
#   - Caddyfile je veljaven: preveri se v zacasnem vsebniku PREDEN se Caddy
#     znova zazene. Ce je neveljaven, se ne zgodi nic.
#
# Zakaj znova zagnati in ne "caddy reload": Caddyfile je v vsebnik priklopljen
# kot ena datoteka, ki jo git pull zamenja z novo; vsebnik pa vidi se staro,
# dokler se ne zazene znova. Ista past kot pri statistiki.

set -euo pipefail

PROD="${TURNIRKO_MAPA:-/srv/turnirko}"
HOST=test.turnirko-nt.si

cd "$PROD"

ZETON=$(grep '^TEST_ZETON=' .env | head -1 | cut -d= -f2-)
[ -n "$ZETON" ] || { echo "NAPAKA: v $PROD/.env ni TEST_ZETON. Najprej: bash skripte/test/nastavi.sh" >&2; exit 1; }

grep -q "^$HOST" Caddyfile || { echo "NAPAKA: v Caddyfile ni bloka $HOST (git pull --autostash?)." >&2; exit 1; }
grep -q 'TEST_ZETON' compose.yaml || { echo "NAPAKA: compose.yaml ne prenese TEST_ZETON v Caddy (git pull --autostash?)." >&2; exit 1; }

echo "1/3 DNS za $HOST"
NASLOV=$(getent hosts "$HOST" | awk '{print $1}' | head -1 || true)
[ -n "$NASLOV" ] || { echo "NAPAKA: $HOST se ne razresi. Dodaj zapis A pri ponudniku domene in poskusi znova." >&2; exit 1; }
MOJ=$(curl -4 -fs --max-time 5 https://api.ipify.org 2>/dev/null || true)
echo "    $HOST -> $NASLOV (ta streznik: ${MOJ:-neznano})"
if [ -n "$MOJ" ] && [ "$NASLOV" != "$MOJ" ]; then
	echo "NAPAKA: DNS kaze drugam. Pocakaj, da se zapis razsiri, in poskusi znova." >&2
	exit 1
fi

echo "2/3 preverjam Caddyfile (zacasen vsebnik, produkcije ne dotakne)"
docker run --rm -e "TEST_ZETON=$ZETON" -v "$PROD/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2 \
	caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile

echo "3/3 znova zazenem Caddy (samo ta vsebnik)"
docker compose up -d --no-deps --force-recreate caddy
sleep 5
docker compose ps caddy

echo
echo "Konec. Povezava za prvi vstop (odpri jo enkrat na vsaki napravi):"
echo "  https://$HOST/odpri/$ZETON"
echo "Pri prvem obisku Caddy nekaj sekund pridobiva certifikat."
