#!/bin/bash
# Pot nazaj po neuspeli objavi v produkcijo: znova zazene SLIKO, ki je tekla
# pred zadnjo objavo (v-produkcijo.sh jo oznaci kot turnirko-app:prejsnja).
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/vrni-prejsnjo.sh
#
# Kdaj to NI pot nazaj: ce je zadnja objava prinesla migracijo. Migracije so
# enosmerne - stara koda nove sheme ne pozna (Flyway zagon zavrne), zato je
# takrat pot nazaj popravek z novo objavo ali obnova kopije (docs/OBJAVA.md,
# "Obnova v sili"). Skripta v tem primeru ne naredi nicesar.
#
# Skripta vrne samo tekoco sliko. Koda v mapi in veja main ostaneta na novem
# commitu: napako popravi z novim commitom prek testa (ali z "git revert"),
# naslednja objava pa spet zgradi sliko iz tega, kar je na main.

set -euo pipefail

PROD="${TURNIRKO_MAPA:-/srv/turnirko}"
cd "$PROD"

[ -f .prejsnja-objava ] || { echo "NAPAKA: zapisa o prejsnji objavi ni (.prejsnja-objava)." >&2; exit 1; }
PREJSNJI=$(sed -n 1p .prejsnja-objava)
MIGRACIJA=$(sed -n 2p .prejsnja-objava)
KDAJ=$(sed -n 3p .prejsnja-objava)

if [ "$MIGRACIJA" = 1 ]; then
	echo "NAPAKA: zadnja objava ($KDAJ) je prinesla migracijo; stara slika z novo shemo ne tece." >&2
	echo "        Pot nazaj je popravek z novo objavo ali obnova kopije (docs/OBJAVA.md, Obnova v sili)." >&2
	exit 1
fi
docker image inspect turnirko-app:prejsnja > /dev/null 2>&1 || {
	echo "NAPAKA: slike turnirko-app:prejsnja ni (pobrisana ali objava je ni oznacila)." >&2
	exit 1
}

echo "vracam sliko izpred objave $KDAJ (commit ${PREJSNJI:0:7})"
docker image tag turnirko-app:latest turnirko-app:neuspela
docker image tag turnirko-app:prejsnja turnirko-app:latest
docker compose up -d --no-build --no-deps --force-recreate app

echo "cakam, da je aplikacija zdrava"
CID=$(docker compose ps -q app)
STANJE="?"
for _ in $(seq 1 60); do
	STANJE=$(docker inspect -f '{{.State.Health.Status}}' "$CID" 2> /dev/null || echo "?")
	[ "$STANJE" = healthy ] && break
	sleep 5
done
if [ "$STANJE" != healthy ]; then
	echo "NAPAKA: tudi prejsnja slika po 5 minutah ni zdrava (stanje: $STANJE). Zadnje vrstice dnevnika:" >&2
	docker compose logs --tail 60 app >&2 || true
	exit 1
fi

echo
docker compose ps
echo
echo "Tece prejsnja slika (commit ${PREJSNJI:0:7}). Neuspela je shranjena kot turnirko-app:neuspela."
echo "Koda v mapi je se na novem commitu: napako popravi z novim commitom prek testa."
exit 0
