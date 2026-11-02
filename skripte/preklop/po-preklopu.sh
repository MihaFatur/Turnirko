#!/bin/bash
# Pregled stanja takoj po preklopu na PostgreSQL. Samo bere, nic ne spremeni.
#
#     skripte/preklop/po-preklopu.sh [minut]     (privzeto 20 minut dnevnika)
#
# Izpise: napake in opozorila v dnevniku aplikacije in baze, z vlogo se je
# povezala aplikacija, zaporedja (identity), ki bi zaostajala za najvecjim id-jem
# v tabeli (nova vrstica bi trcila ob obstojeco), in stevilo vrstic glavnih tabel.

set -uo pipefail
. "$(dirname "$0")/lib.sh"

MINUT="${1:-20}"

echo "=== Dnevnik aplikacije (zadnjih $MINUT min): ERROR/WARN ==="
docker compose logs --no-color --since "${MINUT}m" app 2> /dev/null | grep -E ' (ERROR|WARN) ' | cut -c1-260 | tail -n 25
echo "(stevilo ERROR: $(docker compose logs --no-color --since "${MINUT}m" app 2> /dev/null | grep -c ' ERROR '))"

echo
echo "=== Dnevnik baze (zadnjih $MINUT min): ERROR/FATAL ==="
docker compose logs --no-color --since "${MINUT}m" baza 2> /dev/null | grep -E 'ERROR|FATAL' | cut -c1-260 | tail -n 25
echo "(stevilo ERROR/FATAL: $(docker compose logs --no-color --since "${MINUT}m" baza 2> /dev/null | grep -c -E 'ERROR|FATAL'))"

echo
echo "=== Povezave na bazo po vlogah ==="
docker compose exec -T baza psql -U postgres -d turnirko -At -c "SELECT coalesce(usename, '(sistem)') || ': ' || count(*) FROM pg_stat_activity WHERE datname = 'turnirko' GROUP BY usename ORDER BY 1"

echo
echo "=== Zaporedja, ki zaostajajo za najvecjim id-jem (mora biti prazno) ==="
docker compose exec -T baza psql -U postgres -d turnirko -At << 'SQL'
SELECT format(
  'SELECT %L AS tabela, %L AS stolpec, (SELECT max(%I) FROM %I) AS najvecji, (SELECT last_value FROM %s) AS zaporedje WHERE (SELECT max(%I) FROM %I) > (SELECT last_value FROM %s)',
  c.relname, a.attname, a.attname, c.relname, pg_get_serial_sequence(quote_ident(c.relname), a.attname),
  a.attname, c.relname, pg_get_serial_sequence(quote_ident(c.relname), a.attname))
FROM pg_class c
JOIN pg_attribute a ON a.attrelid = c.oid
WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace
  AND a.attnum > 0 AND NOT a.attisdropped
  AND pg_get_serial_sequence(quote_ident(c.relname), a.attname) IS NOT NULL
ORDER BY c.relname
\gexec
SQL
echo "(konec preverbe zaporedij)"

echo
echo "=== Vrstice glavnih tabel ==="
docker compose exec -T baza psql -U postgres -d turnirko -At -F ': ' -c "
SELECT 'igralec', count(*) FROM igralec UNION ALL
SELECT 'turnir', count(*) FROM turnir UNION ALL
SELECT 'liga', count(*) FROM liga UNION ALL
SELECT 'tekma', count(*) FROM tekma UNION ALL
SELECT 'tekma_srecanja', count(*) FROM tekma_srecanja UNION ALL
SELECT 'rating_zgodovina', count(*) FROM rating_zgodovina UNION ALL
SELECT 'uporabnik', count(*) FROM uporabnik"
