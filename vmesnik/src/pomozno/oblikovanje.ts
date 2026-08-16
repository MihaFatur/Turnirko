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

const DNEVI = ['ned', 'pon', 'tor', 'sre', 'čet', 'pet', 'sob']

/* Termin kola iz ISO datuma-časa: "ned, 12. okt · 18.00".

   Dan v tednu je spredaj, ker gledalec kolo išče po dnevu ("naslednjo nedeljo")
   in ne po datumu. Ura 00:00 pomeni, da je organizator ni vpisal — takrat
   odpade, ne izpiše se "0.00". Letnica ni v izpisu: sezona stoji v nadnaslovu,
   v vrstici kola pa je odveč. */
export function oblikujTermin(iso: string | null | undefined): string {
  if (!iso) return ''
  const [d, t] = iso.split('T')
  const [leto, mesec, dan] = d.split('-').map(Number)
  if (!leto || !mesec || !dan) return ''
  /* UTC, da poletni/zimski čas datuma ne premakne za dan nazaj. */
  const dnevVTednu = DNEVI[new Date(Date.UTC(leto, mesec - 1, dan)).getUTCDay()] ?? ''
  const datum = `${dnevVTednu}, ${dan}. ${KRATKI_MESECI[mesec - 1] ?? ''}`
  const ura = t ? t.slice(0, 5) : ''
  return ura && ura !== '00:00' ? `${datum} · ${ura.replace(':', '.')}` : datum
}

/* Obdobje turnirja: en datum, "od - do" ali prazen niz. */
export function oblikujObdobje(zacetek: string | null, konec: string | null): string {
  const od = oblikujDatum(zacetek)
  const dokler = oblikujDatum(konec)
  if (od && dokler && od !== dokler) return `${od} – ${dokler}`
  return od || dokler
}

/* Obdobje v mono vrstici telefona: "5. 9.", "13.–14. 8.", "30. 8. – 1. 9."
   Kadar sta datuma v istem mesecu, se ponovi samo dan - v vrstico gresta
   obdobje IN kraj, za dva cela datuma pa prostora ni. Letnica je privzeto
   odvec (seznam je tekoc), na strani turnirja pa jo zahtevamo izrecno. */
export function oblikujObdobjeKratko(
  zacetek: string | null,
  konec: string | null,
  zLetom = false,
): string {
  const od = razstaviDatum(zacetek)
  const dokler = razstaviDatum(konec)
  const letnica = (leto: number) => (zLetom ? ` ${leto}` : '')

  if (od && dokler && (od.leto !== dokler.leto || od.mesec !== dokler.mesec || od.dan !== dokler.dan)) {
    if (od.leto === dokler.leto && od.mesec === dokler.mesec) {
      return `${od.dan}.–${dokler.dan}. ${od.mesec}.${letnica(od.leto)}`
    }
    const prvi = `${od.dan}. ${od.mesec}.${od.leto !== dokler.leto ? ` ${od.leto}` : ''}`
    return `${prvi} – ${dokler.dan}. ${dokler.mesec}.${letnica(dokler.leto)}`
  }

  const en = od ?? dokler
  return en ? `${en.dan}. ${en.mesec}.${letnica(en.leto)}` : ''
}

function razstaviDatum(datum: string | null): { leto: number; mesec: number; dan: number } | null {
  if (!datum) return null
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return null
  return { leto, mesec, dan }
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

/* Sezona, ki ji pripada datum: "2025-10-04" -> "2025/26", "2026-04-19" ->
   "2025/26". Turnir sezone ne nosi kot polje (nosi jo liga), filter nad
   seznamom pa jo potrebuje - sicer bi gledalec 326 uvozenih turnirjev lahko
   locil samo po posameznem datumu.

   Rez je 1. julij: uvozena zgodovina NTZS se zacne najprej sredi septembra in
   konca najkasneje sredi junija, zato skozi rez ne pade nobeno tekmovanje.
   Brez datuma sezone ni - turnir tak preprosto ni v nobeni skupini. */
export function sezonaIzDatuma(datum: string | null | undefined): string | null {
  const razstavljen = razstaviDatum(datum ?? null)
  if (!razstavljen) return null
  const zacetna = razstavljen.mesec >= 7 ? razstavljen.leto : razstavljen.leto - 1
  return `${zacetna}/${String((zacetna + 1) % 100).padStart(2, '0')}`
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

/* "12 prijav" - v mono vrstici telefona je krajse od "12 prijavljenih". */
export function sklonPrijav(n: number): string {
  return sklon(n, 'prijava', 'prijavi', 'prijave', 'prijav')
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
