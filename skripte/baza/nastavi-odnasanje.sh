#!/bin/bash
# Nastavi SIFRIRANO odnasanje kopij s streznika (rclone: Backblaze B2 + plast crypt)
# in zapise TURNIRKO_ODNASANJE v .env. Zazene se enkrat, na strezniku, interaktivno:
#
#     ssh -t root@streznik "cd /srv/turnirko && skripte/baza/nastavi-odnasanje.sh <ime-vedra>"
#
# Application Key ID in Application Key vpises ti (skrito, nikamor se ne izpisuje).
# Gesli sifrirne plasti (vsebina in imena datotek) skripta ustvari sama; shranjeni
# sta SAMO v rclone.conf. Brez njiju kopij v oblaku ni mogoce prebrati, zato po
# koncu SKOPIRAJ CELO datoteko rclone.conf v upravitelj gesel (ukaz izpise skripta).
#
# Predpogoj: v B2 sta ustvarjena zasebno vedro in kljuc, omejen na to vedro
# (Read and Write). Kljuc omejen na vedro vedra ne more ustvariti, zato je
# nastavljen no_check_bucket. hard_delete: brisanje res izbrise datoteko in ne
# samo skrije zadnje verzije (sicer bi se stare kopije kopicile in se placevale).

set -euo pipefail

VEDRO="${1:?uporaba: $0 <ime-vedra-B2>}"
MAPA="${TURNIRKO_MAPA:-/srv/turnirko}"

command -v rclone > /dev/null || { echo "rclone ni namescen (apt install -y rclone)"; exit 1; }
[ -f "$MAPA/.env" ] || { echo "Ni $MAPA/.env"; exit 1; }

# Pregled obstojecih nastavitev brez cevovoda (grep -q + pipefail = SIGPIPE).
OBSTOJECE=$(rclone listremotes)
case "$OBSTOJECE" in
	*b2:* | *kopije:*)
		echo "Nastavitev 'b2' ali 'kopije' ze obstaja. Ce jo res zelis nadomestiti, najprej"
		echo "SKOPIRAJ rclone.conf (sicer izgubis gesli sifriranja): rclone config file"
		echo "nato: rclone config delete b2; rclone config delete kopije"
		exit 1
		;;
esac

read -r -p "Application Key ID (B2): " ID
read -r -s -p "Application Key (B2, skrito): " KLJUC
echo
# Presledki in znaki za novo vrstico se radi prilepijo zraven.
ID=${ID//[[:space:]]/}
KLJUC=${KLJUC//[[:space:]]/}
# Dolzina in zacetek, ne vsebina: pokaze, ali se je lepljenje posrecilo.
# Pravi keyID ima 25 znakov (zacne se z 003), applicationKey 31 (zacne se s K003).
echo "  keyID: ${#ID} znakov (pricakovano 25); applicationKey: ${#KLJUC} znakov, zacne se s '${KLJUC:0:4}' (pricakovano 31, 'K003')"
[ -n "$ID" ] && [ -n "$KLJUC" ] || { echo "ID in kljuc sta obvezna."; exit 1; }

rclone config create b2 b2 account "$ID" key "$KLJUC" hard_delete true no_check_bucket true --non-interactive > /dev/null
echo "Preverjam povezavo z vedrom $VEDRO ..."
rclone lsd "b2:$VEDRO" > /dev/null || { echo "NAPAKA: vedra ni mogoce odpreti (ime vedra, kljuc, pravice?)"; rclone config delete b2; exit 1; }

GESLO1=$(openssl rand -base64 32)
GESLO2=$(openssl rand -base64 32)
rclone config create kopije crypt remote "b2:$VEDRO" filename_encryption standard directory_name_encryption true \
	password "$GESLO1" password2 "$GESLO2" --obscure --non-interactive > /dev/null
unset GESLO1 GESLO2 KLJUC

echo "Preizkus: zapisem in preberem datoteko prek sifrirne plasti ..."
printf 'preskus %s\n' "$(date '+%F %T')" | rclone rcat "kopije:turnirko/preskus.txt"
echo "  prebrano: $(rclone cat "kopije:turnirko/preskus.txt")"
echo "  imena v vedru (morajo biti nerazumljiva, brez besede 'turnirko'):"
rclone lsf "b2:$VEDRO" --recursive | sed -n '1,3p' | sed 's/^/    /'
rclone delete "kopije:turnirko/preskus.txt"

if grep -q '^TURNIRKO_ODNASANJE=' "$MAPA/.env"; then
	echo "V .env je TURNIRKO_ODNASANJE ze nastavljen (ni spremenjen)."
else
	printf '\nTURNIRKO_ODNASANJE=kopije:turnirko\n' >> "$MAPA/.env"
	echo "V .env dodan: TURNIRKO_ODNASANJE=kopije:turnirko"
fi

cat << NAVODILO

GOTOVO. Se dvoje:
  1. SKOPIRAJ rclone.conf v upravitelj gesel (brez nje kopij ne moves prebrati):
       datoteka: $(rclone config file | tail -n 1)
       ukaz (na svojem racunalniku): ssh root@streznik "cat $(rclone config file | tail -n 1)"
  2. Zazeni prvo kopijo in preveri, da je odnesena:
       skripte/varnostna-kopija.sh rocno
       rclone ls kopije:turnirko
NAVODILO
