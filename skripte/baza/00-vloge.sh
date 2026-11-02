#!/bin/bash
# Prva nastavitev baze Turnirko (PostgreSQL).
#
# Docker slika postgres to skripto zazene SAMO ob prvem zagonu, ko je zvezek
# prazen (mapa /docker-entrypoint-initdb.d). Pri kasnejsih zagonih je ne
# izvede vec - gesel vlog zato ni mogoce zamenjati s spremembo .env, glej
# docs/OBJAVA.md, razdelek "Zamenjava gesla baze".
#
# Ustvari dve vlogi:
#   turnirko_lastnik  lastnik sheme. Z njo tece Flyway (migracije) in ukazi
#                     "uvoz", "popravek" in "prenos". Sme vse v bazi turnirko.
#   turnirko_app      s to tece aplikacija. Sme samo SELECT, INSERT, UPDATE,
#                     DELETE. Ce kdo vdre v aplikacijo, ne more zbrisati tabel
#                     ne spremeniti sheme.
# Vloga turnirko_app ima se tri omejitve, da ena pozabljena poizvedba ne
# ustavi celega turnirja:
#   statement_timeout                    30 s  - poizvedba, ki tece predolgo
#   lock_timeout                          5 s  - cakanje na zaklep
#   idle_in_transaction_session_timeout  60 s  - odprta transakcija brez dela
#
# Skrbnik "postgres" ostane za vzdrzevanje (docker compose exec baza ...).

set -eo pipefail

: "${TURNIRKO_BAZA_LASTNIK_GESLO:?manjka TURNIRKO_BAZA_LASTNIK_GESLO}"
: "${TURNIRKO_BAZA_APP_GESLO:?manjka TURNIRKO_BAZA_APP_GESLO}"

# Razvrscanje mora biti po kodnih tockah (builtin C.UTF-8), kot ga je imel
# SQLite; slovenski vrstni red ureja Java. Drugacna baza bi tiho spremenila
# vrstni red povsod, kjer ga poizvedbe privzemajo - zato raje ne zazeni.
IZVEDENO=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -At \
	-c "SELECT datlocprovider || ':' || datlocale FROM pg_database WHERE datname = current_database()")
if [ "$IZVEDENO" != "b:C.UTF-8" ]; then
	echo "NAPAKA: baza $POSTGRES_DB ima razvrscanje '$IZVEDENO', pricakovano 'b:C.UTF-8' (builtin C.UTF-8)." >&2
	echo "Preveri POSTGRES_INITDB_ARGS v compose.yaml in zvezek turnirko_baza pred prvim zagonom izprazni." >&2
	exit 1
fi

# Gesli gresta v psql kot spremenljivki (:'ime' ju ubezi), nikoli v besedilo
# stavka - tako posebni znaki v geslu ne morejo pokvariti ukaza.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
	-v lastnik_geslo="$TURNIRKO_BAZA_LASTNIK_GESLO" \
	-v app_geslo="$TURNIRKO_BAZA_APP_GESLO" <<'SQL'
CREATE ROLE turnirko_lastnik LOGIN PASSWORD :'lastnik_geslo';
CREATE ROLE turnirko_app LOGIN PASSWORD :'app_geslo';

ALTER DATABASE turnirko OWNER TO turnirko_lastnik;
REVOKE ALL ON DATABASE turnirko FROM PUBLIC;
GRANT CONNECT ON DATABASE turnirko TO turnirko_app;

ALTER ROLE turnirko_app SET statement_timeout = '30s';
ALTER ROLE turnirko_app SET lock_timeout = '5s';
ALTER ROLE turnirko_app SET idle_in_transaction_session_timeout = '60s';

-- Statistika poizvedb (skripte/baza-stanje.sh). Ustvari jo skrbnik; bere jo
-- skrbnik, aplikacija je ne potrebuje.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Shemo "public" lasti lastnik; aplikacija jo sme samo uporabljati.
ALTER SCHEMA public OWNER TO turnirko_lastnik;
GRANT USAGE ON SCHEMA public TO turnirko_app;

-- Vse, kar Flyway ustvari kot turnirko_lastnik, takoj dobi pravice aplikacije.
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public
	GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO turnirko_app;
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public
	GRANT USAGE, SELECT ON SEQUENCES TO turnirko_app;
SQL

echo "Baza turnirko: vlogi turnirko_lastnik in turnirko_app ustvarjeni."
