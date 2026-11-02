#!/bin/bash
# Posnetek javnega API-ja za dim-test ob preklopu na PostgreSQL.
#
# Uporaba:
#   posnetek-api.sh posnetek <mapa> [osnova]     shrani ~50 odgovorov
#   posnetek-api.sh primerjaj <mapa1> <mapa2>    primerja dva posnetka
#
# Osnova je privzeto https://turnirko-nt.si (lahko tudi http://localhost:8080
# znotraj streznika). Za vsako pot zapise status in SHA-256 telesa odgovora;
# primerjava je natanko do bajta - isti podatki morajo dati isti odgovor, ne
# glede na to, ali jih streze SQLite ali PostgreSQL. Isto delo opravi
# skripta primerjaj-api.mjs na razvojnem racunalniku nad 17.000 potmi; tu je
# majhen vzorec, ki gre brez Node na strezniku.
#
# Poti uporabljajo id-je, ki ob preklopu ostanejo isti (prenos jih ohrani).
# Odgovori, ki se spreminjajo s casom (domaca stran, seznam turnirjev po
# datumu), se ne razlikujejo, dokler se med posnetkoma ne spremeni dan.

set -uo pipefail

POTI=(
	/api/v1/kraji
	/api/v1/klubi
	/api/v1/lestvica
	/api/v1/igralci
	/api/v1/lige
	/api/v1/turnirji
	/api/v1/zadnje-tekme
	/api/v1/rating/pravila
	/api/v1/domov/lige
	"/api/v1/koledar?od=2026-09-01&do=2026-09-30"
	"/api/v1/koledar?od=2026-10-01&do=2026-10-31"
	/api/v1/lige/229
	/api/v1/lige/277
	/api/v1/lige/379
	/api/v1/lige/456
	/api/v1/lige/277/lestvica
	/api/v1/lige/379/lestvica
	/api/v1/lige/277/srecanja
	/api/v1/lige/379/srecanja
	/api/v1/lige/277/ekipe
	/api/v1/lige/277/lestvica-igralcev
	/api/v1/lige/277/statistika
	/api/v1/turnirji/1
	/api/v1/turnirji/194
	/api/v1/turnirji/386
	/api/v1/turnirji/194/statistika
	/api/v1/turnirji/386/dogodki
	/api/v1/dogodki/531
	/api/v1/dogodki/1009
	/api/v1/dogodki/1487
	/api/v1/srecanja/5870
	/api/v1/srecanja/9935
	/api/v1/srecanja/13823
	/api/v1/igralci/2
	/api/v1/igralci/2/profil
	/api/v1/igralci/61/profil
	/api/v1/igralci/34/profil
	/api/v1/igralci/989/profil
	/api/v1/igralci/1989/profil
	"/api/v1/dvoboj?prvi=2&drugi=61"
)

ukaz="${1:-}"

case "$ukaz" in
	posnetek)
		IZHOD="${2:?mapa za posnetek}"
		OSNOVA="${3:-https://turnirko-nt.si}"
		mkdir -p "$IZHOD"
		: > "$IZHOD/kazalo.txt"
		for pot in "${POTI[@]}"; do
			ime=$(printf '%s' "$pot" | sha1sum | cut -c1-12)
			koda=$(curl -s -m 60 -o "$IZHOD/$ime.json" -w '%{http_code}' "$OSNOVA$pot") || koda=000
			printf '%s\t%s\t%s\n' "$pot" "$koda" "$(sha256sum "$IZHOD/$ime.json" | cut -d' ' -f1)" >> "$IZHOD/kazalo.txt"
		done
		NAPAK=$(awk -F'\t' '$2 != 200' "$IZHOD/kazalo.txt" | wc -l)
		echo "Posnetek: ${#POTI[@]} poti v $IZHOD (odgovorov, ki niso 200: $NAPAK)"
		[ "$NAPAK" -eq 0 ] || awk -F'\t' '$2 != 200 { print "  " $2 "  " $1 }' "$IZHOD/kazalo.txt"
		;;
	primerjaj)
		A="${2:?prvi posnetek}"
		B="${3:?drugi posnetek}"
		if diff -q "$A/kazalo.txt" "$B/kazalo.txt" > /dev/null; then
			echo "ENAKO: vseh $(wc -l < "$A/kazalo.txt") odgovorov se ujema do bajta."
		else
			echo "RAZLIKE (pot, status, SHA-256 prvega proti drugemu):"
			join -t $'\t' -j 1 <(sort "$A/kazalo.txt") <(sort "$B/kazalo.txt") \
				| awk -F'\t' '$2 != $4 || $3 != $5 { print "  " $1 "\t" $2 "/" $4 }'
			echo "Podrobno: diff $A/<datoteka>.json $B/<datoteka>.json (ime = sha1 poti)."
			exit 1
		fi
		;;
	*)
		echo "Uporaba: $0 posnetek <mapa> [osnova] | primerjaj <mapa1> <mapa2>" >&2
		exit 2
		;;
esac
