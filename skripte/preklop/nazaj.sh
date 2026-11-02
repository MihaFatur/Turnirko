#!/bin/bash
# Povratek na SQLite po neuspelem preklopu. Velja SAMO do potrditve v koraku 9
# preklop.sh (dokler stare baze podatki/turnirko.db ni preimenovala skripta).
# Zapisov, nastalih v PostgreSQL po preklopu, ta pot ne ohrani.
#
#     sudo skripte/preklop/nazaj.sh
#
# Kaj naredi:
#   1  ustavi novo aplikacijo
#   2  iz repozitorija vzame compose.yaml zadnje razlicice za SQLite
#      (oznaka sqlite-zadnja) kot compose.staro.yaml
#   3  staro sliko (turnirko-app:pred-preklopom, shranjeno v koraku 3 preklopa)
#      oznaci kot turnirko-app:latest
#   4  zazene staro aplikacijo nad nespremenjeno datoteko SQLite
# Vsebnik baza ostane (ne moti); izprazni ga pred novim poskusom (pred-preklopom.sh pove kako).

set -uo pipefail
. "$(dirname "$0")/lib.sh"

OZNAKA="${TURNIRKO_STARA_OZNAKA:-sqlite-zadnja}"

[ -f "$MAPA/podatki/turnirko.db" ] || napaka "podatki/turnirko.db ni vec na mestu - preklop je bil potrjen, povratka ni (obnova: podatki/turnirko.db.pred-preklopom-*)"
docker image inspect turnirko-app:pred-preklopom > /dev/null 2>&1 || napaka "stare slike turnirko-app:pred-preklopom ni (preklop.sh jo shrani v koraku 3)"
git rev-parse --verify --quiet "$OZNAKA" > /dev/null || napaka "oznake '$OZNAKA' ni v repozitoriju (git tag; git fetch --tags)"

potrdi "Vrnem aplikacijo na SQLite? Zapisi, nastali v PostgreSQL, bodo izgubljeni."

korak "1/4 Ustavim novo aplikacijo"
docker compose stop app || true

korak "2/4 compose za staro razlicico ($OZNAKA)"
git show "$OZNAKA:compose.yaml" > "$MAPA/compose.staro.yaml" || napaka "compose.yaml iz oznake ni bilo mogoce prebrati"

korak "3/4 Stara slika"
docker tag turnirko-app:pred-preklopom turnirko-app:latest || napaka "slike ni bilo mogoce oznaciti"

korak "4/4 Zagon stare aplikacije (SQLite)"
# --no-build: uporabi sliko iz koraka 3. Brez --remove-orphans: vsebnik baza
# ni v staremu compose, a ga ne smemo odstraniti.
docker compose -f "$MAPA/compose.staro.yaml" --project-directory "$MAPA" up -d --no-build --no-deps app || napaka "stare aplikacije ni bilo mogoce zagnati"
caka_zdrav app 240 || napaka "stara aplikacija ne postane zdrava: docker compose -f compose.staro.yaml logs app"

povzetek_casov
zapis "POVRNJENO. Stran spet deluje na SQLite. Preveri $OSNOVA in vzrok napake pred novim poskusom."
zapis "Ko bos znova poskusal: rm compose.staro.yaml; docker image rm turnirko-app:pred-preklopom"
