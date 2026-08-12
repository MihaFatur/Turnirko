#!/bin/sh
# Varnostna kopija baze Turnirko.
#
# ZAKAJ NE "cp": SQLite med delovanjem pise v datoteko po delih. Navadna
# kopija med pisanjem ujame bazo na pol posodobljeno in taksna kopija se
# pogosto sploh ne odpre - napako pa opazis sele takrat, ko kopijo rabis.
# Ukaz ".backup" naredi kopijo, ki je dosledna tudi med pisanjem.
#
# Namestitev na strezniku (mapa projekta, npr. /srv/turnirko):
#     chmod +x skripte/varnostna-kopija.sh
#     sudo apt install sqlite3
#     crontab -e
# in dodaj vrstico (vsak dan ob 3:20 zjutraj):
#     20 3 * * * /srv/turnirko/skripte/varnostna-kopija.sh >> /srv/turnirko/dnevniki/kopije.log 2>&1
#
# POMEMBNO: kopije na istem strezniku niso varnostne kopije. Ce strezhnik
# odpove ali ga kdo prevzame, gredo z njim. Glej razdelek "ODNASANJE" spodaj.

set -eu

# --- Nastavitve ---------------------------------------------------------
BAZA="${TURNIRKO_BAZA:-/srv/turnirko/podatki/turnirko.db}"
KAM="${TURNIRKO_KOPIJE:-/srv/turnirko/kopije}"
# Koliko dni kopij hranimo. Turnirska sezona je dolga; mesec dni je razumno
# izhodisce, ker se napaka v podatkih (npr. napacno vnesen rezultat, ki je
# pokvaril rating) lahko opazi sele cez nekaj tednov.
HRANI_DNI="${TURNIRKO_HRANI_DNI:-30}"

ZIG=$(date +%Y%m%d-%H%M%S)
CILJ="$KAM/turnirko-$ZIG.db"

# --- Kopiranje ----------------------------------------------------------
mkdir -p "$KAM"

if [ ! -f "$BAZA" ]; then
	echo "$(date '+%F %T')  NAPAKA: baze ni na $BAZA"
	exit 1
fi

# .backup zna brati bazo, ki je v rabi; ne ustavljaj aplikacije.
sqlite3 "$BAZA" ".backup '$CILJ'"

# Preverba, da kopija ni okvarjena. Kopija, ki je nihce ni pogledal, je
# samo obcutek varnosti - ne varnost.
if ! sqlite3 "$CILJ" "PRAGMA integrity_check;" | grep -q '^ok$'; then
	echo "$(date '+%F %T')  NAPAKA: kopija $CILJ ni prestala preverbe"
	rm -f "$CILJ"
	exit 1
fi

gzip -f "$CILJ"
CILJ="$CILJ.gz"

# Baza vsebuje osebne podatke igralcev, zato kopija ni za javnost.
chmod 600 "$CILJ"

# --- Ciscenje starih ----------------------------------------------------
find "$KAM" -name 'turnirko-*.db.gz' -type f -mtime +"$HRANI_DNI" -delete

echo "$(date '+%F %T')  kopija: $CILJ ($(du -h "$CILJ" | cut -f1))"

# --- ODNASANJE S STREZNIKA ----------------------------------------------
# Odkomentiraj eno od moznosti, ko jo nastavis. Brez tega koraka si
# zavarovan pred pomoto (izbrisan turnir), NE pa pred izgubo streznika.
#
# a) na svoj racunalnik - to pozeni DOMA, ne na strezniku:
#      scp streznik:/srv/turnirko/kopije/turnirko-*.db.gz ~/kopije-turnirko/
#
# b) v oblacno shrambo prek rclone (nastavis z "rclone config"):
#      rclone copy "$CILJ" oblak:turnirko-kopije/
#
# Ce hranis kopije v EU oblaku, pazi: gre za osebne podatke igralcev.
