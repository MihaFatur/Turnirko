#!/bin/bash
# PREKLOP produkcije s SQLite na PostgreSQL (docs/PREKLOP.md, docs/PRENOVA-BAZE.md
# razdelek 7). Poganja ga lastnik na strezniku, ob dogovorjeni uri, ko ni kol
# lig ne turnirjev. Traja ~30 minut (generalka pokaze tocen cas).
#
#     sudo skripte/preklop/preklop.sh
#
# Predpogoji (docs/PREKLOP.md, "Dan prej"): koda je ze potegnjena (git pull),
# .env ima gesla baze, slika aplikacije je ze zgrajena, pred-preklopom.sh je
# zelen, oznaka sqlite-zadnja v repozitoriju kaze na zadnjo razlicico za SQLite.
#
# Zaporedje:
#   1  pregled predpogojev (pred-preklopom.sh --hitro)
#   2  posnetek API-ja PRED preklopom (za primerjavo)
#   3  stara slika aplikacije se shrani (povratna pot)
#   4  aplikacija se ustavi: pisanje je ustavljeno, Caddy kaze stran "Vzdrzevanje"
#   5  kopija SQLite = POVRATNA TOCKA
#   6  Flyway ustvari shemo, ukaz prenos prenese podatke; porocilo mora biti zeleno
#   7  zagon nove aplikacije
#   8  dim-test: posnetek API-ja PO preklopu mora biti enak posnetku PRED
#   9  prva rocna preverba, nato potrditev - od tu naprej NI vrnitve
#
# Do vkljucno koraka 8 je povratek ena skripta: skripte/preklop/nazaj.sh. Po
# koraku 9 nastajajo zapisi samo v PostgreSQL, napake se popravljajo naprej.

set -uo pipefail
. "$(dirname "$0")/lib.sh"

ZIG=$(date +%Y%m%d-%H%M%S)
POSNETEK_PRED="$POT_DNEVNIK/posnetek-pred-$ZIG"
POSNETEK_PO="$POT_DNEVNIK/posnetek-po-$ZIG"
KOPIJA="$MAPA/kopije/sqlite-pred-preklopom-$ZIG.db"

echo "PREKLOP NA POSTGRESQL"
echo "Aplikacija bo ustavljena ~30 minut. Obvestilo organizatorjem mora biti poslano."
potrdi "Je ura primerna (ni kol lig, ni turnirja) in so organizatorji obvesceni?"

korak "1/9 Predpogoji"
"$(dirname "$0")/pred-preklopom.sh" --hitro || napaka "predpogoji niso izpolnjeni - nic ni spremenjeno, aplikacija deluje"

korak "2/9 Posnetek API-ja pred preklopom ($OSNOVA)"
"$(dirname "$0")/posnetek-api.sh" posnetek "$POSNETEK_PRED" "$OSNOVA" || napaka "posnetka ni bilo mogoce narediti"

korak "3/9 Shranim staro sliko aplikacije (povratna pot)"
ID_STARE=$(docker compose ps -q app)
[ -n "$ID_STARE" ] || napaka "aplikacija ne tece - preklop mora iti iz delujocega stanja"
STARA_SLIKA=$(docker inspect -f '{{.Image}}' "$ID_STARE")
# Slika, iz katere tece vsebnik, ni nujno se v shrambi: ko pred-preklopom.sh
# zgradi novo sliko z istim imenom, stara izgubi ime in jo shramba (containerd)
# odstrani, vsebnik pa tece naprej. Zato po vrsti: ime po stevilki, stanje
# delujocega vsebnika, gradnja iz oznake zadnje razlicice za SQLite.
if docker tag "$STARA_SLIKA" turnirko-app:pred-preklopom 2> /dev/null; then
	zapis "stara slika: $STARA_SLIKA -> turnirko-app:pred-preklopom"
elif docker commit --pause=false "$ID_STARE" turnirko-app:pred-preklopom > /dev/null 2>&1; then
	zapis "stare slike ni vec v shrambi: shranjeno stanje delujocega vsebnika -> turnirko-app:pred-preklopom"
else
	zapis "commit ni uspel: gradim staro sliko iz oznake sqlite-zadnja (nekaj minut; aplikacija tece)"
	MAPA_STARA=$(mktemp -d)
	git archive sqlite-zadnja | tar -x -C "$MAPA_STARA" && docker build -q -t turnirko-app:pred-preklopom "$MAPA_STARA" > /dev/null
	RC_STARA=$?
	rm -rf "$MAPA_STARA"
	[ "$RC_STARA" -eq 0 ] || napaka "stare slike ni bilo mogoce shraniti (aplikacija tece, nic ni spremenjeno)"
fi
# Povratna pot je vredna toliko, kolikor se slika zazene: preveri jo PRED ustavitvijo.
docker run --rm --entrypoint java turnirko-app:pred-preklopom -version > /dev/null 2>&1 || napaka "shranjena stara slika se ne zazene (aplikacija tece, nic ni spremenjeno)"
zapis "shranjena slika se zazene (java -version ok)"

korak "4/9 Ustavim aplikacijo (od tu naprej gledalci vidijo stran Vzdrzevanje)"
docker compose stop app || napaka "aplikacije ni bilo mogoce ustaviti"

korak "5/9 Kopija SQLite (POVRATNA TOCKA)"
mkdir -p "$MAPA/kopije"
command -v sqlite3 > /dev/null || napaka "manjka sqlite3"
sqlite3 -readonly "$MAPA/podatki/turnirko.db" ".backup '$KOPIJA'" || napaka "kopija SQLite ni uspela"
[ "$(sqlite3 -readonly "$KOPIJA" 'PRAGMA integrity_check;')" = "ok" ] || napaka "kopija SQLite ni prestala integrity_check"
chmod 600 "$KOPIJA"
zapis "kopija: $KOPIJA ($(du -h "$KOPIJA" | cut -f1)) - shrani jo se izven streznika (scp), tri mesece"
# Prenos bere to kopijo (ne zivega SQLite): ta nima ne WAL ne zaklepov.
cp "$KOPIJA" "$MAPA/podatki/preklop-izvor.db"
chown 10001:10001 "$MAPA/podatki/preklop-izvor.db"

korak "6/9 Prenos podatkov (Flyway ustvari shemo, nato prenos z vlogo turnirko_lastnik)"
docker compose up -d baza > /dev/null || napaka "baza se ni zagnala"
caka_zdrav baza 90 || napaka "baza ne postane zdrava"
docker compose run --rm -T --no-deps \
	-e SPRING_PROFILES_ACTIVE=splet,prenos \
	-e TURNIRKO_BAZA_UPORABNIK=turnirko_lastnik \
	-e TURNIRKO_BAZA_GESLO="$(iz_env TURNIRKO_BAZA_LASTNIK_GESLO)" \
	app --turnirko.prenos.sqlite=/podatki/preklop-izvor.db --turnirko.prenos.porocilo=/podatki/prenos-porocilo.md
RC=$?
cp "$MAPA/podatki/prenos-porocilo.md" "$POT_DNEVNIK/prenos-porocilo-$ZIG.md" 2> /dev/null || true
if [ "$RC" -ne 0 ]; then
	zapis "PRENOS NI ZELEN (koda $RC). V PostgreSQL ni spremenjeno nic (ena transakcija)."
	zapis "Poglej porocilo: $POT_DNEVNIK/prenos-porocilo-$ZIG.md"
	zapis "Povratek: sudo skripte/preklop/nazaj.sh (aplikacija spet na SQLite)"
	exit 1
fi
zapis "prenos je ZELEN (porocilo: $POT_DNEVNIK/prenos-porocilo-$ZIG.md)"

korak "7/9 Zagon nove aplikacije (na PostgreSQL)"
docker compose up -d app || napaka "aplikacije ni bilo mogoce zagnati (povratek: skripte/preklop/nazaj.sh)"
caka_zdrav app 240 || napaka "aplikacija ne postane zdrava v 4 minutah: docker compose logs app (povratek: skripte/preklop/nazaj.sh)"

korak "8/9 Dim-test: odgovori API-ja morajo biti enaki kot pred preklopom"
"$(dirname "$0")/posnetek-api.sh" posnetek "$POSNETEK_PO" "$OSNOVA" || zapis "OPOZORILO: posnetek po preklopu ni popoln"
if "$(dirname "$0")/posnetek-api.sh" primerjaj "$POSNETEK_PRED" "$POSNETEK_PO" | tee -a "$DNEVNIK"; then
	zapis "dim-test: ENAKO"
else
	zapis "dim-test: RAZLIKE. Razlike v odgovorih, ki so se vmes res spremenili (npr. datum), so v redu;"
	zapis "vse ostalo je znak za povratek: sudo skripte/preklop/nazaj.sh"
	potrdi "Razlike so pojasnjene in nadaljujem?"
fi

korak "9/9 Prva rocna preverba"
cat << NAVODILO

  Odpri $OSNOVA in preveri:
    1. prijava kot admin deluje,
    2. na TESTNEM turnirju vnesi rezultat tekme,
    3. na profilu igralca se je rating spremenil (obracun deluje),
    4. lestvica in domaca stran se naloziti brez napake.

  POZOR: po potrditvi spodaj NI vec vrnitve. Vnosi, nastali v PostgreSQL, se
  v SQLite ne prenesejo nazaj.

NAVODILO
if [ -t 0 ]; then
	potrdi "Preverba je uspela in preklop POTRJUJEM"
	mv "$MAPA/podatki/turnirko.db" "$MAPA/podatki/turnirko.db.pred-preklopom-$ZIG" 2> /dev/null || true
	rm -f "$MAPA/podatki/turnirko.db-wal" "$MAPA/podatki/turnirko.db-shm" "$MAPA/podatki/preklop-izvor.db"
	zapis "stara baza SQLite preimenovana v podatki/turnirko.db.pred-preklopom-$ZIG (hrani 3 mesece)"
	zapis "PREKLOP POTRJEN. Naslednje: varnostne kopije v cron (OBJAVA.md razdelek 7), teden opazovanja (skripte/baza-stanje.sh)."
else
	zapis "Skripta ni v terminalu: potrditve ni. Staro bazo pusti, dokler preverba ni opravljena."
fi
povzetek_casov
