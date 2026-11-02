#!/bin/bash
# GENERALKA preklopa: ves postopek na sveži kopiji produkcije, v loceni
# postavitvi (docker compose projekt "generalka", brez Caddyja), z merjenjem
# casa vsakega koraka. Delujoca stran se ne dotakne: kopijo SQLite naredi
# ukaz .backup (varen tudi med delovanjem), baza PostgreSQL je v svojem
# zvezku, aplikacija pa poslusa samo na 127.0.0.1:18080.
#
#     sudo skripte/preklop/generalka.sh
#
# Preklop naj se zgodi sele, ko druga generalka na strezniku gre od zacetka do
# konca brez rocnega posega (docs/PREKLOP.md). Cas korakov 3-6 je predvideni
# izpad preklopa.

set -uo pipefail
. "$(dirname "$0")/lib.sh"

DOCKER_COMPOSE=(docker compose -p generalka -f "$MAPA/compose.yaml" -f "$MAPA/compose.generalka.yaml")
OSNOVA_G="http://127.0.0.1:18080"
MAPA_G="$MAPA/generalka"

echo "GENERALKA PREKLOPA (produkcija ostane nedotaknjena)"
if [ -n "$("${DOCKER_COMPOSE[@]}" ps -q 2> /dev/null)" ]; then
	echo "Projekt 'generalka' ze obstaja. Pospravi ga: ${DOCKER_COMPOSE[*]} down -v"
	exit 1
fi

korak "1/8 Kopija zivega SQLite v generalka/podatki"
rm -rf "$MAPA_G"
mkdir -p "$MAPA_G/podatki"
command -v sqlite3 > /dev/null || napaka "manjka sqlite3 (sudo apt install -y sqlite3)"
sqlite3 -readonly "$MAPA/podatki/turnirko.db" ".backup '$MAPA_G/podatki/turnirko.db'" || napaka "kopija SQLite ni uspela"
[ "$(sqlite3 -readonly "$MAPA_G/podatki/turnirko.db" 'PRAGMA integrity_check;')" = "ok" ] || napaka "kopija ni prestala integrity_check"
chown -R 10001:10001 "$MAPA_G/podatki"
zapis "kopija: $(du -h "$MAPA_G/podatki/turnirko.db" | cut -f1)"

korak "2/8 Posnetek API-ja produkcije ($OSNOVA)"
"$(dirname "$0")/posnetek-api.sh" posnetek "$MAPA_G/posnetek-produkcija" "$OSNOVA" || napaka "posnetek produkcije ni uspel"

korak "3/8 Baza PostgreSQL (nov prazen zvezek, vloge)"
"${DOCKER_COMPOSE[@]}" up -d baza || napaka "baza se ni zagnala"
caka_zdrav baza 120 || napaka "baza ne postane zdrava: ${DOCKER_COMPOSE[*]} logs baza"

korak "4/8 Prenos podatkov (Flyway + prenos)"
"${DOCKER_COMPOSE[@]}" run --rm -T --no-deps \
	-e SPRING_PROFILES_ACTIVE=splet,prenos \
	-e TURNIRKO_BAZA_UPORABNIK=turnirko_lastnik \
	-e TURNIRKO_BAZA_GESLO="$(iz_env TURNIRKO_BAZA_LASTNIK_GESLO)" \
	app --turnirko.prenos.sqlite=/podatki/turnirko.db --turnirko.prenos.porocilo=/podatki/prenos-porocilo.md
RC=$?
cp "$MAPA_G/podatki/prenos-porocilo.md" "$POT_DNEVNIK/generalka-prenos-porocilo-$(date +%Y%m%d-%H%M).md" 2> /dev/null || true
[ "$RC" -eq 0 ] || napaka "prenos ni zelen (koda $RC): glej $MAPA_G/podatki/prenos-porocilo.md"

korak "5/8 Zagon aplikacije na PostgreSQL"
"${DOCKER_COMPOSE[@]}" up -d --no-deps app || napaka "aplikacije ni bilo mogoce zagnati"
caka_zdrav app 240 || napaka "aplikacija ne postane zdrava: ${DOCKER_COMPOSE[*]} logs app"

korak "6/8 Posnetek API-ja generalke ($OSNOVA_G)"
"$(dirname "$0")/posnetek-api.sh" posnetek "$MAPA_G/posnetek-generalka" "$OSNOVA_G" || napaka "posnetek generalke ni uspel"

korak "7/8 Primerjava posnetkov"
if "$(dirname "$0")/posnetek-api.sh" primerjaj "$MAPA_G/posnetek-produkcija" "$MAPA_G/posnetek-generalka" | tee -a "$DNEVNIK"; then
	REZULTAT="ENAKO"
else
	REZULTAT="RAZLIKE (glej zgoraj)"
fi

korak "8/8 Pospravljanje"
povzetek_casov
zapis "REZULTAT GENERALKE: $REZULTAT"
if [ -t 0 ]; then
	read -r -p "Izbrisem projekt generalka (vsebnika, zvezek, kopijo)? [vpisi DA]: " ODGOVOR
	if [ "$ODGOVOR" = "DA" ]; then
		"${DOCKER_COMPOSE[@]}" down -v
		rm -rf "$MAPA_G"
		zapis "pospravljeno"
	else
		zapis "ostane: ${DOCKER_COMPOSE[*]} down -v && rm -rf generalka"
	fi
fi
[ "$REZULTAT" = "ENAKO" ]
