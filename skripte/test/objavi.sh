#!/bin/bash
# Objava na TESTNO okolje: potegne vejo "test", zgradi sliko, zazene test in
# preveri, da deluje. Produkcije ne dotakne.
#
# Uporaba (na strezniku):   bash /srv/turnirko-test/skripte/test/objavi.sh
#
# Po uspehu je commit zapisan v /srv/turnirko-test/.zadnja-objava; to je
# "preizkusen commit", ki ga skripte/test/v-produkcijo.sh pusti v produkcijo.
# Test je odlagalisce in ne delovna mapa: lokalne spremembe v njej se ob objavi
# prepisejo s stanjem z GitHuba.
#
# Migracije Flyway tecejo ob zagonu testne aplikacije nad (osvezeno) kopijo
# produkcijske baze: ce migracija na pravih podatkih odpove, se pokaze tukaj,
# ne v produkciji. Ce se je shema v testu pokvarila, jo vrne
# skripte/test/osvezi-bazo.sh (spet kopija produkcije).

set -euo pipefail

TEST="${TURNIRKO_TEST_MAPA:-/srv/turnirko-test}"
VEJA="${TURNIRKO_TEST_VEJA:-test}"

dc() { docker compose -p turnirko-test -f compose.test.yaml "$@"; }

glavno() {
	cd "$TEST"

	echo "1/4 koda z GitHuba (veja $VEJA)"
	git fetch -q origin "$VEJA"
	git checkout -q -f -B "$VEJA" "origin/$VEJA"
	git log -1 --format='    %h  %s'
	SHA=$(git rev-parse HEAD)

	echo "2/4 gradnja in zagon (okoli 2 minuti; produkcija tece naprej)"
	# Streznik ima ~3,8 GB pomnilnika in ga s produkcijo deli. Gradnja (Maven +
	# Node) potrebuje ~1 GB, tekoca testna aplikacija ~0,6 GB: skupaj bi
	# OOM-killer lahko ubil PRODUKCIJSKI proces. Zato se test-app za cas gradnje
	# ustavi, in ce je prostega pomnilnika se vedno premalo, se gradnja ne zacne.
	dc stop test-app || true
	PROST=$(free -m | awk 'NR==2 {print $7}')
	echo "    prost pomnilnik pred gradnjo: $PROST MB"
	if [ "$PROST" -lt 900 ]; then
		echo "NAPAKA: prostega pomnilnika je manj kot 900 MB; gradnja bi lahko ogrozila produkcijo. Ne gradim." >&2
		exit 1
	fi
	dc build -q
	dc up -d --remove-orphans

	echo "3/4 cakam, da je aplikacija zdrava (migracije tecejo ob zagonu)"
	CID=$(dc ps -q test-app)
	STANJE="?"
	for _ in $(seq 1 72); do
		STANJE=$(docker inspect -f '{{.State.Health.Status}}' "$CID" 2>/dev/null || echo "?")
		[ "$STANJE" = healthy ] && break
		sleep 5
	done
	if [ "$STANJE" != healthy ]; then
		echo "NAPAKA: test-app po 6 minutah ni zdrav (stanje: $STANJE). Zadnje vrstice dnevnika:" >&2
		dc logs --tail 60 test-app >&2
		exit 1
	fi

	echo "4/4 preverba"
	echo "    zadnje migracije:"
	dc exec -T test-baza psql -U postgres -d turnirko -At \
		-c "SELECT '      V' || version || '  ' || CASE WHEN success THEN 'OK' ELSE 'NAPAKA' END FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3" || true
	NAPAK=$(dc logs --since 10m test-app 2>&1 | grep -c ' ERROR ' || true)
	echo "    napak (ERROR) v dnevniku zadnjih 10 min: $NAPAK"
	if [ "$NAPAK" != "0" ]; then
		dc logs --since 10m test-app 2>&1 | grep ' ERROR ' | head -5
		# Commit z napakami ob zagonu ni "preizkusen": v-produkcijo.sh ga ne sme
		# spustiti naprej samo zato, ker se je aplikacija zagnala.
		if [ "${TURNIRKO_TEST_KLJUB_NAPAKAM:-0}" != 1 ]; then
			echo "NAPAKA: aplikacija tece, a dnevnik ima napake; commit NI oznacen kot preizkusen." >&2
			echo "        Ce so napake pricakovane: TURNIRKO_TEST_KLJUB_NAPAKAM=1 bash $TEST/skripte/test/objavi.sh" >&2
			exit 1
		fi
	fi

	printf '%s\n%s\n' "$SHA" "$(date '+%F %T')" > "$TEST/.zadnja-objava"
	echo
	echo "TEST TECE NA COMMITU $(git rev-parse --short HEAD). Odpri https://test.turnirko-nt.si"
	echo "Ko je pregled v redu, commit gre v produkcijo: git push origin $SHA:main, nato skripte/test/v-produkcijo.sh"
}

# Skripta se po checkoutu lahko spremeni sama (je v repozitoriju): zato se po
# potegu kode zazene ZNOVA, nova razlicica, in ne nadaljuje s staro.
if [ "${1:-}" != "--nadaljuj" ]; then
	cd "$TEST"
	git fetch -q origin "$VEJA"
	git checkout -q -f -B "$VEJA" "origin/$VEJA"
	exec bash "$TEST/skripte/test/objavi.sh" --nadaljuj
fi

glavno
exit 0
