#!/bin/bash
# Objava v PRODUKCIJO, a samo kode, ki je ze tekla na testu.
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/v-produkcijo.sh
#
# Potek celotne poti kode (docs/TESTNO-OKOLJE.md):
#   1. commit + push na vejo "test"
#   2. skripte/test/objavi.sh          -> test tece na tem commitu; pregled
#   3. ko je pregled v redu: git push origin <commit>:main (na racunalniku)
#   4. ta skripta                      -> produkcija potegne main
#
# Varovalka: skripta zavrne objavo, ce "main" vsebuje karkoli, kar test ni
# zgradil (npr. nekdo je pushal na main mimo testa). Dovoljen je main, ki je
# enak preizkusenemu commitu ali njegov prednik.
#
# Ce objava prinese migracijo, se pred njo naredi rocna varnostna kopija
# (migracije so enosmerne). Ce se spremeni Caddyfile, se po preverbi veljavnosti
# Caddy znova zazene (vsebnik sicer vidi staro datoteko).

set -euo pipefail

PROD="${TURNIRKO_MAPA:-/srv/turnirko}"
TEST="${TURNIRKO_TEST_MAPA:-/srv/turnirko-test}"

glavno() {
	cd "$PROD"

	[ -f "$TEST/.zadnja-objava" ] || { echo "NAPAKA: test se ni bil objavljen (bash $TEST/skripte/test/objavi.sh)." >&2; exit 1; }
	PREIZKUSEN=$(head -1 "$TEST/.zadnja-objava")

	git fetch -q origin main test
	GLAVA=$(git rev-parse origin/main)
	git cat-file -e "${PREIZKUSEN}^{commit}" 2> /dev/null || {
		echo "NAPAKA: preizkusenega commita ($PREIZKUSEN) ni na GitHubu; veja test je bila prepisana." >&2
		exit 1
	}

	echo "preizkusen na testu: $(git log -1 --format='%h  %s' "$PREIZKUSEN")"
	echo "main (kar bo produkcija dobila): $(git log -1 --format='%h  %s' "$GLAVA")"

	if ! git merge-base --is-ancestor "$GLAVA" "$PREIZKUSEN"; then
		echo "NAPAKA: main vsebuje commite, ki jih test ni zgradil. Objavo ustavljam." >&2
		echo "        Popravek: preizkusi main na testu (veja test naj ga vsebuje) ali ga popravi." >&2
		exit 1
	fi

	if [ "$(git rev-parse HEAD)" = "$GLAVA" ]; then
		echo "Produkcija je ze na tem commitu. Ni kaj objaviti."
		exit 0
	fi

	echo "kar se spremeni:"
	git log --format='    %h  %s' HEAD.."$GLAVA"

	# Seznam datotek v spremenljivki, ne v cevi: "grep -q" konca ob prvem zadetku
	# in bi ob pipefail pustil git diff s SIGPIPE (past iz varnostne kopije).
	SPREMENJENO=$(git diff --name-only HEAD "$GLAVA")
	if grep -q 'db/migration/' <<< "$SPREMENJENO"; then
		echo "objava vsebuje MIGRACIJO: najprej rocna varnostna kopija"
		bash skripte/varnostna-kopija.sh rocno
	fi
	CADDY=0
	if grep -qx 'Caddyfile' <<< "$SPREMENJENO"; then CADDY=1; fi

	echo "git pull"
	git pull -q --autostash

	if [ "$CADDY" = 1 ]; then
		# Preverba PREDEN docker compose up: compose bi ob spremembi compose.yaml
		# znova zagnal tudi Caddy, ta pa se s pokvarjeno datoteko ne bi zagnal
		# in bi stran obstala. Tako se pokvarjen Caddyfile ustavi tukaj.
		echo "Caddyfile se je spremenil: preverjam veljavnost"
		ZETON=$(grep '^TEST_ZETON=' .env | head -1 | cut -d= -f2- || true)
		docker run --rm -e "TEST_ZETON=$ZETON" -v "$PROD/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2 \
			caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
	fi

	echo "gradnja in zagon (aplikacija je ~20 s nedosegljiva)"
	docker compose up -d --build

	if [ "$CADDY" = 1 ]; then
		echo "znova zazenem Caddy (vsebnik vidi staro datoteko, dokler se ne zazene znova)"
		docker compose up -d --no-deps --force-recreate caddy
	fi

	echo
	docker compose ps
	echo
	echo "Preveri se: bash skripte/preklop/po-preklopu.sh 10 (napake v dnevnikih), https://turnirko-nt.si"
}

glavno
exit 0
