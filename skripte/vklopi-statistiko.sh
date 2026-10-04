#!/bin/sh
# Prva namestitev statistike obiska (Umami) na strezniku.
#
# Pozeni v mapi projekta PO "git pull":
#     cd /srv/turnirko && sudo sh ./skripte/vklopi-statistiko.sh
#
# Kaj naredi:
#   1. preveri, da zapis DNS za statistika.turnirko-nt.si ze kaze na ta
#      streznik - sicer Caddy ne dobi certifikata;
#   2. v .env doda skrivnosti za Umami, ce jih se ni (nakljucne, nikjer
#      izpisane) - brez njih docker compose ne zazene nicesar vec;
#   3. preveri, da je dovolj prostega pomnilnika - Umami in njegova baza
#      skupaj porabita okoli 400 MB. Ce pomnilnika zmanjka, sistem ustavi
#      najvecji proces, in to bi bila aplikacija;
#   4. zazene Umami z bazo, Caddy pa ustvari znova, da prebere nov Caddyfile.
#      Aplikacije (storitev "app") se ne dotakne.
#
# Varno jo je pognati veckrat.

set -eu
cd "$(dirname "$0")/.."

DOMENA=statistika.turnirko-nt.si

# --- 1. DNS -------------------------------------------------------------
statistika_ip=$(getent ahostsv4 "$DOMENA" | awk 'NR == 1 { print $1 }')
glavna_ip=$(getent ahostsv4 turnirko-nt.si | awk 'NR == 1 { print $1 }')
if [ -z "$statistika_ip" ]; then
	echo "NAPAKA: $DOMENA se nima zapisa DNS (ali ta se ni viden)."
	echo "        Pri gostitelju domene dodaj zapis A:  statistika -> $glavna_ip"
	echo "        in poskusi znova cez nekaj minut."
	exit 1
fi
if [ "$statistika_ip" != "$glavna_ip" ]; then
	echo "NAPAKA: $DOMENA kaze na $statistika_ip, turnirko-nt.si pa na $glavna_ip."
	echo "        Zapis A za statistika mora kazati na $glavna_ip."
	exit 1
fi

# --- 2. Skrivnosti v .env -----------------------------------------------
# Heksadecimalni zapis, ker je geslo baze del naslova postgresql://... in
# znaki / + = iz base64 bi ga pokvarili.
if [ -n "$(tail -c 1 .env)" ]; then
	echo >> .env
fi
dodaj_skrivnost() {
	# Vrstica, prepisana iz .env.primer, ni skrivnost.
	sed -i "/^$1=tu-vpisi/d" .env
	if ! grep -q "^$1=" .env; then
		printf '%s=%s\n' "$1" "$(openssl rand -hex "$2")" >> .env
		echo "V .env dodan $1."
	fi
}
dodaj_skrivnost UMAMI_BAZA_GESLO 24
dodaj_skrivnost UMAMI_APP_SECRET 32
dodaj_skrivnost UMAMI_2FA_KLJUC 32

# --- 3. Pomnilnik -------------------------------------------------------
# Ob ponovnem zagonu skripte Umami ze tece in pomnilnik ze porablja.
if [ -z "$(docker compose ps -q --status running umami 2>/dev/null)" ]; then
	na_voljo=$(free -m | awk '/^Mem:/ { print $7 }')
	if [ "$na_voljo" -lt 600 ]; then
		echo "NAPAKA: prostega pomnilnika je samo $na_voljo MB; Umami z bazo rabi okoli 400 MB,"
		echo "        brez rezerve pa bi sistem ob pomanjkanju ustavil aplikacijo."
		echo "        Pri ponudniku streznika izberi paket z vec pomnilnika."
		exit 1
	fi
fi

# --- 4. Zagon -----------------------------------------------------------
docker compose up -d umami-baza umami
# Caddyfile je priklopljen kot posamezna datoteka. "git pull" jo zamenja z
# novo, tekoci vsebnik pa se naprej vidi staro - zato ga ustvarimo znova.
# Stran je pri tem nekaj sekund nedosegljiva.
docker compose up -d --force-recreate caddy

# --- 5. Preverba --------------------------------------------------------
echo "Cakam, da se Umami zazene in Caddy pridobi certifikat (najvec 2 minuti) ..."
poskus=0
until curl -fs -o /dev/null "https://$DOMENA/api/heartbeat"; do
	poskus=$((poskus + 1))
	if [ "$poskus" -ge 24 ]; then
		echo "NAPAKA: https://$DOMENA se po 2 minutah se ne odziva. Poglej:"
		echo "        docker compose logs --tail 50 umami caddy"
		exit 1
	fi
	sleep 5
done

echo
echo "Statistika tece: https://$DOMENA"
echo
echo "TAKOJ se prijavi (uporabnik: admin, geslo: umami) in zamenjaj geslo -"
echo "dokler je privzeto, se lahko prijavi vsak. Nato dodaj spletno stran"
echo "(ime Turnirko, domena turnirko-nt.si) in si zapisi njen Website ID."
