#!/bin/bash
# Izpise zadnja "poslana" sporocila testnega okolja, vkljucno s potrditvenimi
# kodami (registracija, pozabljeno geslo). Testna aplikacija pravih mailov ne
# poslje: vse gre v njen dnevnik (turnirko.posta.nacin=dnevnik).
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/koda.sh [koliko-minut]
# Privzeto zadnjih 30 minut.

set -euo pipefail

TEST="${TURNIRKO_TEST_MAPA:-/srv/turnirko-test}"
MINUT="${1:-30}"

docker compose -p turnirko-test --project-directory "$TEST" -f "$TEST/compose.test.yaml" \
	logs --no-log-prefix --since "${MINUT}m" test-app 2>&1 \
	| grep -A 12 'E-posta (ni poslana)' || echo "V zadnjih $MINUT minutah ni bilo nobenega sporocila."
