#!/bin/sh
# Ustvari Stripov webhook za Turnirko in njegovo skrivnost zapise v .env.
#
# Pozeni v mapi projekta, ko je v .env ze TURNIRKO_STRIPE_TAJNI_KLJUC
# (sandbox sk_test_... ali zivi sk_live_...):
#     cd /srv/turnirko && sudo sh ./skripte/stripe-webhook.sh
#
# Zakaj skripta in ne nadzorna plosca: plosca za webhook ponudi samo privzeto
# ali najnovejso razlicico API-ja, knjiznica stripe-java 29.4.0 pa dogodke
# bere v obliki 2025-07-30.basil (Java je strogo tipizirana in Stripe sam
# odsvetuje neujemanje). Drugo razlicico je mogoce izbrati samo prek API-ja.
#
# Kaj naredi:
#   1. prebere kljuc iz .env (nikjer ga ne izpise, curl ga dobi prek stdin,
#      ne v ukazni vrstici, ki je vidna v seznamu procesov);
#   2. pogleda, ali za ta naslov v tem nacinu (sandbox / zivo) webhook ze
#      obstaja - ce je to tisti iz .env (TURNIRKO_STRIPE_WEBHOOK_ID), konca;
#   3. sicer ustvari webhook s stirimi dogodki, ki jih Turnirko obdela, in v
#      .env zapise njegovo skrivnost (whsec_..., Stripe jo pokaze samo ob
#      nastanku) in id;
#   4. znova ustvari vsebnik aplikacije, da prebere nov .env (navaden
#      "restart" sprememb v .env ne prebere).
#
# Varno jo je pognati veckrat. Ob preklopu iz sandboxa v zivo zamenjaj kljuc
# v .env in jo pozeni znova: za zivi nacin ustvari svoj webhook.

set -eu
cd "$(dirname "$0")/.."

NASLOV=https://turnirko-nt.si/api/v1/placila/webhook
RAZLICICA=2025-07-30.basil

beri_env() {
	sed -n "s/^$1=//p" .env | tail -n 1 | tr -d '\r" '
}

kljuc=$(beri_env TURNIRKO_STRIPE_TAJNI_KLJUC)
case "$kljuc" in
	sk_live_*|rk_live_*) nacin="zivi nacin" ;;
	sk_test_*|rk_test_*) nacin="sandbox (testni nacin)" ;;
	*)
		echo "NAPAKA: v .env ni TURNIRKO_STRIPE_TAJNI_KLJUC (sk_live_... ali sk_test_...)."
		exit 1
		;;
esac
echo "Kljuc v .env: $nacin."

# Klic Stripa; izpise telo odgovora, v zadnji vrstici pa kodo HTTP.
stripe() {
	pot=$1
	shift
	printf 'user = "%s:"\n' "$kljuc" \
		| curl -sS -K - -w '\n%{http_code}' "https://api.stripe.com/v1/$pot" "$@"
}

koda() { printf '%s\n' "$1" | tail -n 1; }
telo() { printf '%s\n' "$1" | sed '$d'; }

napaka_stripa() {
	echo "NAPAKA: Stripe je vrnil $(koda "$1"):"
	telo "$1" | sed -n 's/.*"message": "\(.*\)".*/        \1/p'
	exit 1
}

# --- 1. Ali webhook za ta naslov ze obstaja -----------------------------
odgovor=$(stripe "webhook_endpoints?limit=100")
[ "$(koda "$odgovor")" = 200 ] || napaka_stripa "$odgovor"
# Stripe izpise vsako polje v svoji vrstici; "id" stoji pred "url".
obstojeci=$(telo "$odgovor" | awk -v iskani="\"url\": \"$NASLOV\"" '
	/"id": "we_/ { id = $2; gsub(/[",]/, "", id) }
	index($0, iskani) { print id }')
znani=$(beri_env TURNIRKO_STRIPE_WEBHOOK_ID)

if [ -n "$obstojeci" ]; then
	for id in $obstojeci; do
		if [ "$id" = "$znani" ]; then
			echo "Webhook $id ze obstaja in je zapisan v .env - nic za narediti."
			exit 0
		fi
	done
	echo "NAPAKA: za $NASLOV v tem nacinu ze obstaja webhook ($obstojeci), ki ga .env"
	echo "        ne pozna (npr. ustvarjen v nadzorni plosci). Njegove skrivnosti ni"
	echo "        mogoce prebrati znova. Izbrisi ga v Stripu (Workbench -> Webhooks)"
	echo "        in pozeni skripto znova."
	exit 1
fi

# --- 2. Nov webhook -----------------------------------------------------
odgovor=$(stripe webhook_endpoints \
	--data-urlencode "url=$NASLOV" \
	-d "api_version=$RAZLICICA" \
	--data-urlencode "description=Turnirko: narocnine" \
	-d "enabled_events[]=checkout.session.completed" \
	-d "enabled_events[]=customer.subscription.updated" \
	-d "enabled_events[]=customer.subscription.deleted" \
	-d "enabled_events[]=invoice.payment_failed")
[ "$(koda "$odgovor")" = 200 ] || napaka_stripa "$odgovor"
id=$(telo "$odgovor" | sed -n 's/.*"id": "\(we_[^"]*\)".*/\1/p' | head -n 1)
skrivnost=$(telo "$odgovor" | sed -n 's/.*"secret": "\(whsec_[^"]*\)".*/\1/p')
if [ -z "$id" ] || [ -z "$skrivnost" ]; then
	echo "NAPAKA: Stripe je webhook ustvaril, a v odgovoru ni id-ja ali skrivnosti."
	echo "        Izbrisi ga v Stripu (Workbench -> Webhooks) in pozeni skripto znova."
	exit 1
fi

# --- 3. Zapis v .env ----------------------------------------------------
if [ -n "$(tail -c 1 .env)" ]; then
	echo >> .env
fi
nastavi() {
	sed -i "/^$1=/d" .env
	printf '%s=%s\n' "$1" "$2" >> .env
}
nastavi TURNIRKO_STRIPE_WEBHOOK_SKRIVNOST "$skrivnost"
nastavi TURNIRKO_STRIPE_WEBHOOK_ID "$id"
echo "Webhook $id ustvarjen (razlicica $RAZLICICA), skrivnost zapisana v .env."

# --- 4. Aplikacija prebere nov .env -------------------------------------
docker compose up -d app
if [ "$(docker compose exec -T app printenv TURNIRKO_STRIPE_WEBHOOK_SKRIVNOST | cut -c1-6)" = whsec_ ]; then
	echo "Aplikacija tece z novo skrivnostjo."
else
	echo "OPOZORILO: aplikacija skrivnosti se ne vidi. Poglej: docker compose logs --tail 50 app"
	exit 1
fi
