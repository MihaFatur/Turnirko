#!/bin/bash
# Sprosti prostor, ki ga Docker zasede z gradnjami: predpomnilnik gradnje in
# stare slike. Vsaka objava (test in produkcija) pusti za sabo sloje prejsnje
# slike; novembra 2026 jih je bilo 19 GB + 19 GB na disku z 38 GB (88 % poln).
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/vzdrzevanje/sprosti-disk.sh
#
# Cesa se NE dotakne:
#   - zvezkov (volumes): tam je baza. Ukaza "docker volume prune" tukaj ni in
#     ga nikoli ne dodajaj.
#   - slik, ki jih uporablja kateri koli vsebnik (tudi ustavljen);
#   - slik, mlajsih od 7 dni: med njimi je turnirko-app:prejsnja, pot nazaj po
#     zadnji objavi (skripte/test/vrni-prejsnjo.sh).
#
# Posledica: prva gradnja po ciscenju traja dlje (Maven in npm odvisnosti se
# prenesejo znova), nato spet obicajno.

set -euo pipefail

prosto() { df -Pm / | awk 'NR == 2 { print $4 }'; }

PREJ=$(prosto)
echo "prosto pred ciscenjem: $PREJ MB"
docker system df
echo
echo "1/2 predpomnilnik gradnje"
docker builder prune -af
echo
echo "2/2 slike brez vsebnika, starejse od 7 dni"
docker image prune -af --filter "until=168h"
echo
POTEM=$(prosto)
echo "prosto po ciscenju: $POTEM MB (sprosceno $((POTEM - PREJ)) MB)"
df -h / | tail -n 1
