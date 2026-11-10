#!/bin/bash
# Prva nastavitev TESTNEGA okolja na strezniku. Zazenes jo enkrat; ponovni
# zagon nicesar ne prepise (obstojeci .env in zeton ostaneta).
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/nastavi.sh
#
# Kaj naredi:
#   1. klonira repozitorij v /srv/turnirko-test (veja "test");
#   2. tam ustvari .env z NOVIMI nakljucnimi gesli (razlicnimi od produkcijskih:
#      tudi ce bi se test po napaki povezal na produkcijsko bazo, prijava ne bi
#      uspela) in z nastavitvama, zaradi katerih se compose v tej mapi nikoli
#      ne more zamenjati s produkcijskim;
#   3. pripravi mapo podatki za uporabnika aplikacije (10001);
#   4. v produkcijski .env doda TEST_ZETON (skrivnost za povezavo do testa).
# Produkcijskih vsebnikov ne dotakne. Caddy se nastavi loceno, ko DNS kaze na
# streznik: skripte/test/vklopi-caddy.sh.

set -euo pipefail

PROD="${TURNIRKO_MAPA:-/srv/turnirko}"
TEST="${TURNIRKO_TEST_MAPA:-/srv/turnirko-test}"
VEJA="${TURNIRKO_TEST_VEJA:-test}"

[ -f "$PROD/.env" ] || { echo "NAPAKA: $PROD/.env ne obstaja (si na pravem strezniku?)." >&2; exit 1; }

echo "1/4 klon repozitorija v $TEST (veja $VEJA)"
if [ -d "$TEST/.git" ]; then
	echo "    ze obstaja, preskocim"
else
	git clone --branch "$VEJA" "$(git -C "$PROD" remote get-url origin)" "$TEST"
fi

echo "2/4 $TEST/.env"
if [ -f "$TEST/.env" ]; then
	echo "    ze obstaja, ne prepisujem"
else
	(
		umask 077
		cat > "$TEST/.env" <<ENV
# Testno okolje. Ustvaril skripte/test/nastavi.sh; gesla so NOVA in razlicna od
# produkcijskih. Te datoteke ne kopiraj v produkcijo in obratno.
#
# Ti dve vrstici poskrbita, da "docker compose" v tej mapi vedno pomeni TESTNO
# okolje (compose.test.yaml), nikoli produkcijskega compose.yaml.
COMPOSE_FILE=compose.test.yaml
COMPOSE_PROJECT_NAME=turnirko-test

TURNIRKO_ADMIN_GESLO=$(openssl rand -hex 24)
TURNIRKO_BAZA_ADMIN_GESLO=$(openssl rand -hex 24)
TURNIRKO_BAZA_LASTNIK_GESLO=$(openssl rand -hex 24)
TURNIRKO_BAZA_APP_GESLO=$(openssl rand -hex 24)
ENV
	)
	echo "    ustvarjen (gesla so nakljucna)"
fi

echo "3/4 mapa podatki"
mkdir -p "$TEST/podatki"
chown -R 10001:10001 "$TEST/podatki"

echo "4/4 TEST_ZETON v $PROD/.env"
if grep -q '^TEST_ZETON=.' "$PROD/.env"; then
	echo "    ze obstaja, ne spreminjam"
else
	# Stara vrstica brez vrednosti (ce je kdo pustil) se odstrani; datoteka naj
	# se konca z novo vrstico, preden se doda nova.
	sed -i '/^TEST_ZETON=$/d' "$PROD/.env"
	[ -z "$(tail -c1 "$PROD/.env")" ] || echo >> "$PROD/.env"
	echo "TEST_ZETON=$(openssl rand -hex 24)" >> "$PROD/.env"
	echo "    dodan"
fi

echo
echo "Konec. Naslednji koraki:"
echo "  - DNS: zapis A  test  ->  $(curl -4 -fs --max-time 5 https://api.ipify.org 2>/dev/null || echo '<IP streznika>')"
echo "  - bash $PROD/skripte/test/objavi.sh          (zgradi in zazene test)"
echo "  - bash $PROD/skripte/test/osvezi-bazo.sh     (kopija produkcijske baze v test)"
echo "  - bash $PROD/skripte/test/vklopi-caddy.sh    (ko DNS ze kaze na streznik)"
