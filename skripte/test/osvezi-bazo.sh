#!/bin/bash
# Prepise TESTNO bazo s sveze kopijo PRODUKCIJSKE baze.
#
# Uporaba (na strezniku):   bash /srv/turnirko/skripte/test/osvezi-bazo.sh
#
# Zakaj: nova koda in migracije se preizkusijo na pravih podatkih. Po vsaki
# osvezitvi ima test shemo takega, kot je produkcija; migracije, ki jih prinese
# nova koda, stekejo ob zagonu testne aplikacije (skripta jo zazene).
#
# Smer je ENA: produkcija -> test. Produkcijska baza se samo bere (pg_dump je
# ena dosledna transakcija, vnosi med kopiranjem niso moteni). Izbrise se samo
# baza "turnirko" v vsebniku test-baza. Kopija gre po cevi neposredno med
# vsebnikoma, na disku ne ostane nobena datoteka.
#
# Pozor: baza vsebuje osebne podatke igralcev. Test jih drzi na istem
# strezniku kot produkcija in je zaklenjen (skrita povezava); posta in
# placila so v njem ugasnjeni. Za cisto test okolje glej docs/TESTNO-OKOLJE.md.

set -euo pipefail

PROD="${TURNIRKO_MAPA:-/srv/turnirko}"
TEST="${TURNIRKO_TEST_MAPA:-/srv/turnirko-test}"

# Vedno izrecno: projekt in datoteka, nikoli "kar je v tej mapi".
prod() { docker compose -p turnirko --project-directory "$PROD" -f "$PROD/compose.yaml" "$@"; }
test_() { docker compose -p turnirko-test --project-directory "$TEST" -f "$TEST/compose.test.yaml" "$@"; }

[ -f "$TEST/compose.test.yaml" ] || { echo "NAPAKA: testno okolje ni nastavljeno (bash $PROD/skripte/test/nastavi.sh)." >&2; exit 1; }

# Varovalka: cilj mora biti res testna baza.
TESTNA=$(test_ ps -q test-baza)
[ -n "$TESTNA" ] || { test_ up -d test-baza; sleep 15; TESTNA=$(test_ ps -q test-baza); }
PROJEKT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$TESTNA")
[ "$PROJEKT" = "turnirko-test" ] || { echo "NAPAKA: cilj ni testna baza (projekt: $PROJEKT). Ustavljam." >&2; exit 1; }

echo "1/5 ustavljam testno aplikacijo"
test_ stop test-app

echo "2/5 nova prazna testna baza"
test_ exec -T test-baza psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS turnirko WITH (FORCE)"
test_ exec -T test-baza psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE turnirko OWNER turnirko_lastnik ENCODING 'UTF8' LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8' TEMPLATE template0"

echo "3/5 kopiram produkcijo v test (pg_dump | pg_restore)"
prod exec -T baza pg_dump -U postgres -d turnirko -Fc | test_ exec -T test-baza pg_restore -U postgres -d turnirko --exit-on-error

echo "4/5 pravice vlog"
test_ exec -T test-baza psql -U postgres -d turnirko -v ON_ERROR_STOP=1 <<'SQL'
REVOKE ALL ON DATABASE turnirko FROM PUBLIC;
GRANT CONNECT ON DATABASE turnirko TO turnirko_app;
GRANT USAGE ON SCHEMA public TO turnirko_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO turnirko_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO turnirko_app;
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO turnirko_app;
ALTER DEFAULT PRIVILEGES FOR ROLE turnirko_lastnik IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO turnirko_app;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SQL

echo "5/5 zaganjam testno aplikacijo (Flyway izvede morebitne nove migracije)"
test_ up -d test-app

echo
echo "Testna baza je kopija produkcije z zdaj. Vrstic v tabeli igralec:"
test_ exec -T test-baza psql -U postgres -d turnirko -At -c "SELECT count(*) FROM igralec"
echo "Stanje preveri z: bash $TEST/skripte/test/objavi.sh (ponovno zgradi in preveri) ali docker compose -p turnirko-test ps"
