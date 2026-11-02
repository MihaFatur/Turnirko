#!/bin/bash
# Varnostna kopija baze Turnirko (PostgreSQL).
#
# Uporaba:   varnostna-kopija.sh ura | noc | rocno
#
# Kaj naredi: iz vsebnika "baza" potegne pg_dump v zgoscenem zapisu (-Fc),
# preveri, da se kopija da prebrati, in jo odnese na drug racunalnik (rclone).
#
# ZAKAJ pg_dump in NE kopija mape: PostgreSQL med delovanjem pise po dolocenih
# delih diska hkrati. Kopija mape med pisanjem ujame bazo na pol - taksna
# kopija se lahko ne zazene, napako pa opazis sele takrat, ko kopijo rabis.
# pg_dump bere bazo kot ena dosledna transakcija, zato je kopija tocno taka,
# kot je baza bila ob zacetku, tudi ce vmes nekdo vnasa rezultate.
#
# Tri vrste kopij (vsaka v svoji podmapi mape kopije/):
#   ura    vsako uro med 7. in 23. uro; hrani se 48 ur
#   noc    vsako noc ob 3.20; hrani se 30 dni. Prvi dan v mesecu se kopija
#          shrani tudi v mesec/ (hrani se 12 mesecev)
#   rocno  pred vecjo posodobitvijo; hrani se 30 dni
# Najslabsi primer ob izgubi streznika: izgubljena ura vnosov (ti so tudi na
# papirnatih listkih).
#
# Namestitev na strezniku (mapa projekta, npr. /srv/turnirko):
#     chmod +x skripte/*.sh skripte/baza/*.sh
#     crontab -e
# in dodaj vrstici:
#     0 7-23 * * * /srv/turnirko/skripte/varnostna-kopija.sh ura >> /srv/turnirko/dnevniki/kopije.log 2>&1
#     20 3 * * *   /srv/turnirko/skripte/varnostna-kopija.sh noc >> /srv/turnirko/dnevniki/kopije.log 2>&1
#
# ODNASANJE: kopije na istem strezniku niso varnostne kopije - ce streznik
# odpove ali ga kdo prevzame, gredo z njim. V .env nastavi
#     TURNIRKO_ODNASANJE=oblak:turnirko-kopije
# (ime nastavitve rclone in mapa v shrambi; glej docs/OBJAVA.md, razdelek 7).
# Kopija vsebuje osebne podatke igralcev (datume rojstva): na shrambi naj bo
# SIFRIRANA (rclone crypt), v EU.

set -euo pipefail

VRSTA="${1:-rocno}"
MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"
KAM="${TURNIRKO_KOPIJE:-$MAPA/kopije}"

case "$VRSTA" in
	ura)   HRANI_MIN=$((48 * 60)) ;;
	noc)   HRANI_MIN=$((30 * 24 * 60)) ;;
	rocno) HRANI_MIN=$((30 * 24 * 60)) ;;
	*)
		echo "Uporaba: $0 ura | noc | rocno" >&2
		exit 2
		;;
esac

zapis() { echo "$(date '+%F %T')  $VRSTA  $*"; }

# Kopija, ki je ni bilo mogoce narediti, mora glasno propasti (cron to
# zapise v dnevnik); molk bi pomenil, da kopij ni, ti pa mislis, da so.
napaka() {
	zapis "NAPAKA: $*" >&2
	exit 1
}

baza() { docker compose --project-directory "$MAPA" exec -T baza "$@"; }

# Samo vrednost TURNIRKO_ODNASANJE: .env vsebuje tudi vrednosti s presledki,
# ki jih lupina ne bi razumela, zato datoteke ne preberemo vse.
ODNASANJE="${TURNIRKO_ODNASANJE:-}"
if [ -z "$ODNASANJE" ] && [ -f "$MAPA/.env" ]; then
	ODNASANJE=$(grep -E '^TURNIRKO_ODNASANJE=' "$MAPA/.env" | tail -n 1 | cut -d= -f2- || true)
fi

mkdir -p "$KAM/$VRSTA"

# Prostor: kopija je ~20 MB, a poln disk bi ustavil tudi bazo.
PROSTO_MB=$(df -Pm "$KAM" | awk 'NR == 2 { print $4 }')
if [ "${PROSTO_MB:-0}" -lt 1024 ]; then
	napaka "na disku je manj kot 1 GB prostora ($PROSTO_MB MB)"
fi

ZIG=$(date +%Y%m%d-%H%M%S)
CILJ="$KAM/$VRSTA/turnirko-$ZIG.dump"

# --- Kopiranje ---------------------------------------------------------
# Najprej v zacasno datoteko: pol zapisana kopija ne sme izgledati kot prava
# (in je cistilec ne sme prestevati med dobre).
umask 077
if ! baza pg_dump -U postgres -d turnirko -Fc > "$CILJ.delno"; then
	rm -f "$CILJ.delno"
	napaka "pg_dump je odpovedal"
fi

# Preverba: kopija mora imeti kazalo in podatke tabel. Prazna ali okvarjena
# datoteka je brez vrednosti, zato se izbrise, ne obdrzi.
# Brez cevovoda `pg_restore | grep -q`: grep -q se konca ob prvem zadetku, pg_restore
# dobi SIGPIPE in zaradi pipefail bi dobra kopija propadla.
if ! baza pg_restore -l < "$CILJ.delno" > "$CILJ.kazalo" 2> "$CILJ.kazalo.napaka" || ! grep -q 'TABLE DATA' "$CILJ.kazalo"; then
	zapis "pg_restore -l: $(head -c 300 "$CILJ.kazalo.napaka" 2> /dev/null | tr '\n' ' ')" >&2
	rm -f "$CILJ.delno" "$CILJ.kazalo" "$CILJ.kazalo.napaka"
	napaka "kopija ni prestala preverbe (pg_restore -l)"
fi
rm -f "$CILJ.kazalo" "$CILJ.kazalo.napaka"
mv "$CILJ.delno" "$CILJ"

# Stevilo vrstic po tabelah ob kopiji: vaja obnove (vaja-obnove.sh) po njem
# preveri, da obnovljena baza ni okrnjena. Zapisano takoj po kopiji; pri
# nocni kopiji vmes ni vnosov.
if [ "$VRSTA" != "ura" ]; then
	baza psql -U postgres -d turnirko -At -F $'\t' -c "
		SELECT table_name,
		       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text
		FROM information_schema.tables
		WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
		ORDER BY table_name" > "$CILJ.stevilo" || napaka "stetja vrstic ni bilo mogoce zapisati"
fi

KB=$(du -k "$CILJ" | cut -f1)
zapis "kopija: $CILJ (${KB} KB)"

# --- Mesecna kopija ------------------------------------------------------
if [ "$VRSTA" = "noc" ] && [ "$(date +%d)" = "01" ]; then
	mkdir -p "$KAM/mesec"
	cp -p "$CILJ" "$KAM/mesec/turnirko-$(date +%Y%m).dump"
	cp -p "$CILJ.stevilo" "$KAM/mesec/turnirko-$(date +%Y%m).dump.stevilo"
	find "$KAM/mesec" -name 'turnirko-*.dump*' -type f -mtime +366 -delete
	zapis "mesecna kopija: $KAM/mesec/turnirko-$(date +%Y%m).dump"
fi

# --- Cistilec starih -----------------------------------------------------
find "$KAM/$VRSTA" -name 'turnirko-*.dump*' -type f -mmin +"$HRANI_MIN" -delete

# --- Odnasanje s streznika -----------------------------------------------
if [ -n "$ODNASANJE" ]; then
	command -v rclone > /dev/null || napaka "TURNIRKO_ODNASANJE je nastavljen, rclone pa ni namescen"
	rclone copyto "$CILJ" "$ODNASANJE/$VRSTA/$(basename "$CILJ")" || napaka "odnasanje kopije ni uspelo"
	[ ! -f "$CILJ.stevilo" ] || rclone copyto "$CILJ.stevilo" "$ODNASANJE/$VRSTA/$(basename "$CILJ").stevilo" || napaka "odnasanje stetja ni uspelo"
	if [ "$VRSTA" = "noc" ] && [ "$(date +%d)" = "01" ]; then
		rclone copyto "$CILJ" "$ODNASANJE/mesec/turnirko-$(date +%Y%m).dump" || napaka "odnasanje mesecne kopije ni uspelo"
	fi
	# Stare kopije v shrambi pospravi enako kot na strezniku.
	rclone delete "$ODNASANJE/$VRSTA" --min-age "$((HRANI_MIN / 60))h" || zapis "OPOZORILO: cistilec v shrambi ni uspel"
	[ "$VRSTA" != "noc" ] || rclone delete "$ODNASANJE/mesec" --min-age "$((366 * 24))h" || true
	zapis "odneseno: $ODNASANJE/$VRSTA/"
else
	zapis "OPOZORILO: odnasanje ni nastavljeno (TURNIRKO_ODNASANJE) - kopija je samo na tem strezniku"
fi
