#!/usr/bin/env bash
# Izrise ikone aplikacije in sliko za predogled povezave v vmesnik/public/.
# Pozeni iz korena projekta (mapa turnirko), ko se spremeni logotip:
#     bash skripte/ikone/naredi.sh
# Potrebuje Chrome (ali Edge) - pot lahko podas v spremenljivki BRSKALNIK.
# Pisave za predogled pridejo iz vmesnik/node_modules (npm install).
set -euo pipefail

koren="$(cd "$(dirname "$0")/../.." && pwd)"
izhod="$koren/vmesnik/public"
vir="$koren/skripte/ikone"
brskalnik="${BRSKALNIK:-/c/Program Files/Google/Chrome/Application/chrome.exe}"

# Pot do datoteke kot URL file:// (na Windows z drive letter).
url() {
  local pot="$1"
  if command -v cygpath > /dev/null; then pot="$(cygpath -m "$pot")"; fi
  echo "file:///${pot#/}"
}

# Pisave iz node_modules so za stran na file:// tuji izvor in jih Chrome
# zavrne (tudi z --allow-file-access-from-files), zato jih pred izrisom
# vstavimo v kopijo strani kot data: URI.
zacasna="$(mktemp -d)"
trap 'rm -rf "$zacasna"' EXIT
vstavi_pisave() { # vir, izhod
  node -e '
    const fs = require("fs"), pot = require("path")
    const [vir, izhod] = process.argv.slice(1)
    const html = fs.readFileSync(vir, "utf8").replace(/url\(\x27([^\x27]+\.woff2)\x27\)/g, (_, rel) =>
      "url(data:font/woff2;base64," + fs.readFileSync(pot.resolve(pot.dirname(vir), rel)).toString("base64") + ")")
    fs.writeFileSync(izhod, html)
  ' "$1" "$2"
}

# Brezglavi Chrome okna, ozjega od ~500 px, ne naredi (posnetek ostane
# prazen). Zato se stran vedno izrise v oknu "okno" x "okno" CSS px, posnetek
# pa pomanjsa faktor zaslona - 192 px ikona je 512 px stran pri 0,375.
izrisi() { # vir, izhod, sirina, visina, okno (CSS px; privzeto sirina)
  local okno="${5:-$3}"
  local faktor
  faktor="$(awk "BEGIN { print $3 / $okno }")"
  local visina_okna
  visina_okna="$(awk "BEGIN { print int($4 / $faktor + 0.5) }")"
  "$brskalnik" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor="$faktor" --window-size="$okno,$visina_okna" \
    --virtual-time-budget=3000 \
    --screenshot="$2" "$(url "$1")" > /dev/null 2>&1
  echo "  $(basename "$2") ($3 x $4)"
}

echo "Izris v $izhod:"
izrisi "$vir/ikona.html" "$izhod/ikona-192.png" 192 192 512
izrisi "$vir/ikona.html" "$izhod/ikona-512.png" 512 512
izrisi "$vir/ikona.html" "$izhod/apple-touch-icon.png" 180 180 512
vstavi_pisave "$vir/predogled.html" "$zacasna/predogled.html"
izrisi "$zacasna/predogled.html" "$izhod/predogled.png" 1200 630
