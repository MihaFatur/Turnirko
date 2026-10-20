/* Datumska računica za termine kol: skupna za okno »Termini kol« in za polje v
   obrazcu nove lige, da predogled in razpored računata isti datum.

   Računamo v UTC, ker bi lokalni čas ob prehodu na poletni/zimski čas datum
   premaknil za dan (enako kot prestej v TerminiOkno pred prenovo). */

const DNEVI = ['ned', 'pon', 'tor', 'sre', 'čet', 'pet', 'sob'] as const

/* Datum + n dni, oboje kot "YYYY-MM-DD". */
export function prestej(datum: string, dni: number): string {
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return datum
  const cas = new Date(Date.UTC(leto, mesec - 1, dan))
  cas.setUTCDate(cas.getUTCDate() + dni)
  return cas.toISOString().slice(0, 10)
}

/* Kratica dneva v tednu (»sob«) za datum "YYYY-MM-DD"; prazno, če datuma ni. */
export function danKratica(datum: string): string {
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return ''
  return DNEVI[new Date(Date.UTC(leto, mesec - 1, dan)).getUTCDay()]
}

/* Kratek zapis datuma za pregled (»3. 10.«) — brez letnice in brez ničel. */
export function kratkoDatum(datum: string): string {
  const [, mesec, dan] = datum.split('-').map(Number)
  if (!mesec || !dan) return ''
  return `${dan}. ${mesec}.`
}

/* Razlika v dneh med dvema datumoma (b − a). */
export function razlikaDni(a: string, b: string): number {
  const [la, ma, da] = a.split('-').map(Number)
  const [lb, mb, db] = b.split('-').map(Number)
  return Math.round((Date.UTC(lb, mb - 1, db) - Date.UTC(la, ma - 1, da)) / 86400000)
}

/* Najpogostejša vrednost (modus); pri izenačenju prva, ki se pojavi. Prazno
   seznam vrne null, kar pomeni »ni podatka, ni primerjave«. */
export function najpogostejsa<T>(vrednosti: T[]): T | null {
  const stevci = new Map<T, number>()
  for (const v of vrednosti) stevci.set(v, (stevci.get(v) ?? 0) + 1)
  let najboljsa: T | null = null
  let najvec = 0
  for (const [v, n] of stevci) {
    if (n > najvec) {
      najboljsa = v
      najvec = n
    }
  }
  return najboljsa
}

/* Sklanjanje po številu s tremi oblikami (ena, dve, tri–štiri) in množino za
   vse ostalo. Pravilo sledi zadnjima dvema številkama, ker 21 srečanje, 11
   srečanj in 12 srečanj niso isti sklon. */
export function sklon(n: number, ena: string, dve: string, tri: string, mnozina: string): string {
  const k = Math.abs(n) % 100
  const zadnja = k % 10
  if (k !== 11 && zadnja === 1) return ena
  if (k !== 12 && zadnja === 2) return dve
  if (k !== 13 && k !== 14 && (zadnja === 3 || zadnja === 4)) return tri
  return mnozina
}

export const kolTekst = (n: number): string => sklon(n, 'kolo', 'kola', 'kola', 'kol')

export const srecanjTekst = (n: number): string => sklon(n, 'srečanje', 'srečanji', 'srečanja', 'srečanj')

export const prestavitevTekst = (n: number): string =>
  sklon(n, 'prestavitev', 'prestavitvi', 'prestavitve', 'prestavitev')
