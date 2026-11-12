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
	MIGRACIJA=0
	if grep -q 'db/migration/' <<< "$SPREMENJENO"; then MIGRACIJA=1; fi
	# Sprememba compose.yaml lahko znova ustvari tudi vsebnik baze, zato kopija
	# tudi takrat, ne le ob migraciji.
	if [ "$MIGRACIJA" = 1 ] || grep -qx 'compose.yaml' <<< "$SPREMENJENO"; then
		echo "objava vsebuje MIGRACIJO ali spremembo compose.yaml: najprej rocna varnostna kopija"
		bash skripte/varnostna-kopija.sh rocno
	fi
	CADDY=0
	if grep -qx 'Caddyfile' <<< "$SPREMENJENO"; then CADDY=1; fi
	PREJSNJI=$(git rev-parse HEAD)

	# Enaka varovalka kot v objavi.sh: gradnja (Maven + Node) potrebuje ~1 GB,
	# streznik pa ima ~3,8 GB in ga produkcija deli s testom. Test-app se za cas
	# gradnje ustavi (ob izhodu iz skripte se zazene znova), in ce je prostega
	# pomnilnika se vedno premalo, se gradnja ne zacne - OOM-killer bi sicer
	# lahko ubil produkcijski proces.
	if [ -f "$TEST/compose.test.yaml" ]; then
		(cd "$TEST" && docker compose -p turnirko-test -f compose.test.yaml stop test-app) > /dev/null 2>&1 || true
		TEST_USTAVLJEN=1
	fi
	PROST=$(free -m | awk 'NR==2 {print $7}')
	echo "prost pomnilnik pred gradnjo: $PROST MB"
	if [ "$PROST" -lt 900 ]; then
		echo "NAPAKA: prostega pomnilnika je manj kot 900 MB; gradnja bi lahko ogrozila produkcijo. Ne gradim." >&2
		exit 1
	fi

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

	# Pot nazaj: slika, ki tece zdaj, dobi oznako "prejsnja" (vrni-prejsnjo.sh).
	# Zapis pove tudi, ali je objava prinesla migracijo - takrat stara koda z
	# novo shemo ne tece in je pot nazaj obnova kopije, ne stara slika.
	if docker image inspect turnirko-app:latest > /dev/null 2>&1; then
		docker image tag turnirko-app:latest turnirko-app:prejsnja
	fi
	printf '%s\n%s\n%s\n' "$PREJSNJI" "$MIGRACIJA" "$(date '+%F %T')" > "$PROD/.prejsnja-objava"

	# Gradnja LOCENO od zagona: ce gradnja odpove, tekoca aplikacija ostane
	# nedotaknjena (set -e ustavi skripto pred zagonom).
	echo "gradnja (produkcija tece naprej, okoli 2 minuti)"
	docker compose build -q app
	echo "zagon (aplikacija je ~20 s nedosegljiva)"
	docker compose up -d

	echo "cakam, da je aplikacija zdrava (migracije tecejo ob zagonu)"
	CID=$(docker compose ps -q app)
	STANJE="?"
	for _ in $(seq 1 60); do
		STANJE=$(docker inspect -f '{{.State.Health.Status}}' "$CID" 2> /dev/null || echo "?")
		[ "$STANJE" = healthy ] && break
		sleep 5
	done
	if [ "$STANJE" != healthy ]; then
		echo "NAPAKA: aplikacija po 5 minutah ni zdrava (stanje: $STANJE). Zadnje vrstice dnevnika:" >&2
		docker compose logs --tail 60 app >&2 || true
		echo >&2
		if [ "$MIGRACIJA" = 1 ]; then
			echo "Objava je prinesla migracijo: pot nazaj je obnova kopije (docs/OBJAVA.md, Obnova v sili)." >&2
		else
			echo "Pot nazaj na prejsnjo sliko: bash skripte/test/vrni-prejsnjo.sh" >&2
		fi
		exit 1
	fi

	if [ "$CADDY" = 1 ]; then
		echo "znova zazenem Caddy (vsebnik vidi staro datoteko, dokler se ne zazene znova)"
		docker compose up -d --no-deps --force-recreate caddy
	fi

	echo
	docker compose ps
	NAPAK=$(docker compose logs --since 5m app 2>&1 | grep -c ' ERROR ' || true)
	echo
	echo "aplikacija je zdrava; napak (ERROR) v dnevniku zadnjih 5 min: $NAPAK"
	echo "Preveri se: bash skripte/preklop/po-preklopu.sh 10 (napake v dnevnikih), https://turnirko-nt.si"
}

# Test-app, ustavljen za cas gradnje, se zazene znova ne glede na izid.
TEST_USTAVLJEN=0
trap 'if [ "$TEST_USTAVLJEN" = 1 ]; then (cd "$TEST" && docker compose -p turnirko-test -f compose.test.yaml start test-app) > /dev/null 2>&1 || true; fi' EXIT

glavno
exit 0
