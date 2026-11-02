#!/bin/bash
# Skupne funkcije skript za preklop s SQLite na PostgreSQL (docs/PREKLOP.md).
# Ni za neposreden zagon; skripte jo preberejo z `. "$(dirname "$0")/lib.sh"`.

MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"
cd "$MAPA" || { echo "Mape $MAPA ni (nastavi TURNIRKO_MAPA)" >&2; exit 1; }

# Vse, kar skripte zapisejo, gre tudi v dnevnik preklopa: po vsem skupaj je to
# edini dokaz, kaj se je zgodilo in kako dolgo je trajalo.
POT_DNEVNIK="${TURNIRKO_DNEVNIK_PREKLOPA:-$MAPA/dnevniki/preklop}"
mkdir -p "$POT_DNEVNIK"
DNEVNIK="$POT_DNEVNIK/preklop-$(date +%Y%m%d).log"

zapis() { printf '%s  %s\n' "$(date '+%H:%M:%S')" "$*" | tee -a "$DNEVNIK"; }

napaka() {
	zapis "NAPAKA: $*" >&2
	exit 1
}

# Zahteva izrecen DA: pritisk na Enter ne sme sprozit nepovratnega koraka.
potrdi() {
	local odgovor
	read -r -p "$1 [vpisi DA]: " odgovor
	[ "$odgovor" = "DA" ] || { echo "Preklicano."; exit 1; }
}

T_ZACETEK=$(date +%s)
T_KORAK=$T_ZACETEK
korak() {
	konec_koraka
	KORAK="$1"
	T_KORAK=$(date +%s)
	zapis ">>> $1"
}
konec_koraka() {
	if [ -n "${KORAK:-}" ]; then
		zapis "    [$KORAK: $(( $(date +%s) - T_KORAK )) s]"
		CASI+=("$(printf '%5d s  %s' "$(( $(date +%s) - T_KORAK ))" "$KORAK")")
	fi
	KORAK=""
}
CASI=()
povzetek_casov() {
	konec_koraka
	zapis "--- Casi korakov (skupaj $(( $(date +%s) - T_ZACETEK )) s) ---"
	local v
	for v in "${CASI[@]}"; do zapis "$v"; done
}

# Vrednost iz .env brez izvajanja datoteke (vsebuje vrednosti s presledki in
# znaki < >, ki jih lupina ne razume).
iz_env() { grep -E "^$1=" "$MAPA/.env" | tail -n 1 | cut -d= -f2-; }

# Javni naslov strani za posnetke API-ja.
OSNOVA="${TURNIRKO_OSNOVA:-$(iz_env TURNIRKO_JAVNI_NASLOV)}"
OSNOVA="${OSNOVA:-https://turnirko-nt.si}"

# Pocaka, da vsebnik postane "healthy" (healthcheck iz compose.yaml).
DOCKER_COMPOSE=(docker compose)
caka_zdrav() {
	local storitev="$1" omejitev="${2:-180}" id stanje i
	for ((i = 0; i < omejitev; i += 3)); do
		id=$("${DOCKER_COMPOSE[@]}" ps -q "$storitev" 2> /dev/null || true)
		if [ -n "$id" ]; then
			stanje=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2> /dev/null || true)
			[ "$stanje" = "healthy" ] && return 0
		fi
		sleep 3
	done
	return 1
}
