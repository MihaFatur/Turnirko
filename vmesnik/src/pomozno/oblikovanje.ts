/* Pomozne funkcije za oblikovanje vrednosti za prikaz. */

/* "2026-04-12" -> "12. 4. 2026"; null/undefined -> prazen niz. */
export function oblikujDatum(datum: string | null | undefined): string {
  if (!datum) return ''
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return datum
  return `${dan}. ${mesec}. ${leto}`
}

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

/* Ime kola izlocilne mreze. Kolo z enim parom je finale, z dvema
   polfinale itd.; zgodnja kola dobijo zaporedno stevilko. */
export function imeKola(kolo: number, zadnjeKolo: number): string {
  const steviloTekemVKolu = 2 ** (zadnjeKolo - kolo)
  switch (steviloTekemVKolu) {
    case 1: return 'Finale'
    case 2: return 'Polfinale'
    case 4: return 'Četrtfinale'
    case 8: return 'Osmina finala'
    default: return `${kolo}. kolo`
  }
}
