/* Pomozne funkcije za oblikovanje vrednosti za prikaz. */

/* "2026-04-12" -> "12. 4. 2026"; null/undefined -> prazen niz. */
export function oblikujDatum(datum: string | null | undefined): string {
  if (!datum) return ''
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return datum
  return `${dan}. ${mesec}. ${leto}`
}

/* "2026-04-12" -> "12. 4."; letnica odpade, kadar stoji datum v vrstici stanja
   ("Naslednje kolo 21. 2.") in je sezona ze zapisana v nadnaslovu. */
export function oblikujDanMesec(datum: string | null | undefined): string {
  if (!datum) return ''
  const [, mesec, dan] = datum.split('-').map(Number)
  if (!mesec || !dan) return datum
  return `${dan}. ${mesec}.`
}

/* "2026-08-04" -> "4. avg"; kratka oblika za mono vrstice, kjer je prostora
   za dva podatka in ne za cel datum (seznam medsebojnih tekem, naslednje kolo).
   Velike crke doda slog, ne ta funkcija. */
export function oblikujDanKratekMesec(datum: string | null | undefined): string {
  if (!datum) return ''
  const [, mesec, dan] = datum.split('-').map(Number)
  if (!mesec || !dan) return datum
  return `${dan}. ${KRATKI_MESECI[mesec - 1] ?? ''}`
}

const KRATKI_MESECI = [
  'jan', 'feb', 'mar', 'apr', 'maj', 'jun',
  'jul', 'avg', 'sep', 'okt', 'nov', 'dec',
]

/* Obdobje turnirja: en datum, "od - do" ali prazen niz. */
export function oblikujObdobje(zacetek: string | null, konec: string | null): string {
  const od = oblikujDatum(zacetek)
  const dokler = oblikujDatum(konec)
  if (od && dokler && od !== dokler) return `${od} – ${dokler}`
  return od || dokler
}

/* Datumski blok v seznamu (dan nad kratico meseca). Brez datuma vrne crtico,
   da vrstica ohrani visino in poravnavo s sosednjimi. */
const MESECI = ['JAN', 'FEB', 'MAR', 'APR', 'MAJ', 'JUN', 'JUL', 'AVG', 'SEP', 'OKT', 'NOV', 'DEC']

export function datumskiBlok(datum: string | null): { dan: string; mesec: string } {
  if (!datum) return { dan: '—', mesec: '' }
  const [, mesec, dan] = datum.split('-').map(Number)
  if (!mesec || !dan) return { dan: '—', mesec: '' }
  return { dan: String(dan).padStart(2, '0'), mesec: MESECI[mesec - 1] ?? '' }
}

/* "2026-04" -> "APR"; oznaka stolpca v grafu mesecnega izkupicka. */
export function oznakaMeseca(mesec: string): string {
  const st = Number(mesec.split('-')[1])
  return MESECI[st - 1] ?? mesec
}

/* Letnica rojstva iz datuma "YYYY-MM-DD". */
export function letnica(datum: string | null | undefined): string {
  if (!datum) return ''
  return datum.slice(0, 4)
}

/* Slovnicno pravilno sklanjanje besede "listek" ob stevilu (1 listek,
   2 listka, 3/4 listki, 5+ listkov; upostevamo dvomestne izjeme 11-14). */
export function sklonListkov(n: number): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'listkov'
  const mod10 = n % 10
  if (mod10 === 1) return 'listek'
  if (mod10 === 2) return 'listka'
  if (mod10 === 3 || mod10 === 4) return 'listki'
  return 'listkov'
}

/* Slovnicno pravilno sklanjanje besede "tekma" ob stevilu (1 tekma,
   2 tekmi, 3/4 tekme, 5+ tekem; upostevamo dvomestne izjeme 11-14). */
export function sklonTekem(n: number): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'tekem'
  const mod10 = n % 10
  if (mod10 === 1) return 'tekma'
  if (mod10 === 2) return 'tekmi'
  if (mod10 === 3 || mod10 === 4) return 'tekme'
  return 'tekem'
}

/* Sklanjanje po stevilu za besede, ki se ravnajo po vzorcu "1 x, 2 xa,
   3/4 xi, 5+ xov"; dvomestne izjeme 11-14 gredo v zadnjo obliko. */
function sklon(n: number, ena: string, dve: string, tri: string, vec: string): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return vec
  const mod10 = n % 10
  if (mod10 === 1) return ena
  if (mod10 === 2) return dve
  if (mod10 === 3 || mod10 === 4) return tri
  return vec
}

export function sklonIgralcev(n: number): string {
  return sklon(n, 'igralec', 'igralca', 'igralci', 'igralcev')
}

export function sklonSkupin(n: number): string {
  return sklon(n, 'skupina', 'skupini', 'skupine', 'skupin')
}

export function sklonDogodkov(n: number): string {
  return sklon(n, 'dogodek', 'dogodka', 'dogodki', 'dogodkov')
}

export function sklonPrijavljenih(n: number): string {
  return sklon(n, 'prijavljen', 'prijavljena', 'prijavljeni', 'prijavljenih')
}

export function sklonTekmovanj(n: number): string {
  return sklon(n, 'tekmovanje', 'tekmovanji', 'tekmovanja', 'tekmovanj')
}

export function sklonZmag(n: number): string {
  return sklon(n, 'zmaga', 'zmagi', 'zmage', 'zmag')
}

export function sklonTock(n: number): string {
  return sklon(n, 'točka', 'točki', 'točke', 'točk')
}

/* Mestnik: "v 1 mesecu", "v 9 mesecih". */
export function sklonMesecih(n: number): string {
  return n % 100 !== 11 && n % 10 === 1 ? 'mesecu' : 'mesecih'
}

/* "na 3 nize", "na 5 nizov" - stevilo nizov je vedno 3, 5 ali 7, a sklon
   se med njimi razlikuje. */
export function sklonNizov(n: number): string {
  return sklon(n, 'niz', 'niza', 'nize', 'nizov')
}

/* Ime kola izlocilne mreze. Kolo z enim parom je finale, z dvema
   polfinale itd.; zgodnja kola se imenujejo po delezu ("1/16 finala"), da se
   naslov stolpca ujema z oznako na gumbu krmarja. */
export function imeKola(kolo: number, zadnjeKolo: number): string {
  const steviloTekemVKolu = 2 ** (zadnjeKolo - kolo)
  switch (steviloTekemVKolu) {
    case 1: return 'Finale'
    case 2: return 'Polfinale'
    case 4: return 'Četrtfinale'
    case 8: return 'Osmina finala'
    default: return `1/${steviloTekemVKolu} finala`
  }
}

/* Kratka oznaka kola za gumb krmarja mreze: F, PF, ČF, 1/8, 1/16 ...
   Gumbi stojijo v eni vrsti, zato "Osmina finala" ni mogoca. */
export function imeKolaKratko(kolo: number, zadnjeKolo: number): string {
  const steviloTekemVKolu = 2 ** (zadnjeKolo - kolo)
  switch (steviloTekemVKolu) {
    case 1: return 'F'
    case 2: return 'PF'
    case 4: return 'ČF'
    default: return `1/${steviloTekemVKolu}`
  }
}
