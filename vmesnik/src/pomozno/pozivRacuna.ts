/* Pravila poziva k računu (komponente/PozivRacuna.tsx; odločitev lastnika
   23. 10. 2026, DESIGN.md 5f).

   Večina obiskovalcev pride pogledat izide lige in odide, računa pa si ne
   ustvari, ker ga za ogled ne potrebuje. Poziv pokaže, kaj račun da (prvi mesec
   Premium brez plačila), a gledalca najprej pusti gledati: pojavi se šele po
   30 s na strani in šele ob NASLEDNJEM PREHODU NA DRUGO STRAN, ko se ta že
   izriše - klik, ki ga je gledalec naredil, se vedno izvede (okno ga ne
   »ujame«). Pravila so zbrana tu, ločeno od izrisa, kot pri maskoti
   (urnikMaskote.ts). */

/* Koliko časa na strani, preden se poziv sme pokazati (od nalaganja). */
export const PO_MS = 30_000

/* Zamik po prehodu, da je nova stran izrisana, preden jo okno delno pokrije. */
export const ZAMIK_PO_PREHODU_MS = 700

/* Premor po zaprtju: po prvem en teden, po vsakem naslednjem en mesec. */
export const PREMOR_PRVIC_DNI = 7
export const PREMOR_POTEM_DNI = 30

const DAN_MS = 24 * 60 * 60 * 1000
const KLJUC = 'turnirko-poziv-racuna'
const KLJUC_SEJE = 'turnirko-poziv-v-seji'

/* Strani, kjer poziva ni: nakup in registracija imata svoj tok, pogoji so
   branje pred odločitvijo, upravne strani gost itak ne vidi, strani za tisk pa
   so zunaj skupne postavitve. */
const IZKLJUCENE = [
  /^\/narocnina/,
  /^\/registracija/,
  /^\/pogoji/,
  /^\/odjava-obvestil/,
  /^\/moj-profil/,
  /^\/racuni/,
  /^\/igralci$/,
  /^\/sifranti/,
  /^\/uvoz/,
  /^\/rating/,
  /^\/dev\//,
  /\/listki$/,
  /\/zreb-(skupin|mrez)e?$/,
]

export function izkljucenaPot(pot: string): boolean {
  return IZKLJUCENE.some((vzorec) => vzorec.test(pot))
}

interface Zapis {
  zadnjic: number
  stevilo: number
}

function preberi(): Zapis | null {
  try {
    const surovo = localStorage.getItem(KLJUC)
    if (!surovo) return null
    const z = JSON.parse(surovo) as Partial<Zapis>
    if (typeof z.zadnjic !== 'number' || typeof z.stevilo !== 'number') return null
    return { zadnjic: z.zadnjic, stevilo: z.stevilo }
  } catch {
    return null
  }
}

/* Ali je poziv po zadnjem zaprtju še v premoru. */
export function jeVPremoru(zdaj = Date.now()): boolean {
  const z = preberi()
  if (!z) return false
  const premor = (z.stevilo <= 1 ? PREMOR_PRVIC_DNI : PREMOR_POTEM_DNI) * DAN_MS
  return zdaj - z.zadnjic < premor
}

/* Vsak izid poziva (zaprtje, »Ne zdaj«, »Ustvari račun«, »Prijava«) začne
   premor: kdor je registracijo začel in jo opustil, poziva ne dobi takoj spet. */
export function zabeleziZaprtje(zdaj = Date.now()) {
  const z = preberi()
  try {
    localStorage.setItem(KLJUC, JSON.stringify({ zadnjic: zdaj, stevilo: (z?.stevilo ?? 0) + 1 }))
  } catch {
    /* brez shrambe ostane meja ena na sejo (spodaj) */
  }
}

/* Največ en poziv na sejo zavihka; modul ujame tudi brskalnik brez shrambe. */
let pokazanVModulu = false

export function zabeleziPrikaz() {
  pokazanVModulu = true
  try {
    sessionStorage.setItem(KLJUC_SEJE, '1')
  } catch {
    /* kot zgoraj */
  }
}

/* Ali je bil poziv v tej seji že pokazan - po njem maskota do konca seje ne
   nastopi (dva oglasa zapored bi bila šum). */
export function pozivPokazan(): boolean {
  if (pokazanVModulu) return true
  try {
    return sessionStorage.getItem(KLJUC_SEJE) === '1'
  } catch {
    return false
  }
}

/* Ali je od nalaganja strani minilo dovolj časa. */
export function dovoljCasa(): boolean {
  return performance.now() >= PO_MS
}

/* Ali je trenutek primeren: zavihek viden, nobeno okno, predal ali meni ni
   odprt (isti pogoji kot pri maskoti, jePrimernTrenutek). */
export function jePrimernTrenutek(): boolean {
  if (document.hidden) return false
  if (document.getElementById('koren')?.hasAttribute('inert')) return false
  if (document.querySelector('.uporabnik-meni')) return false
  return true
}

/* `?poziv` v naslovu pokaže poziv takoj (pregled brez čakanja, kot `?maskota`
   in `?oglas`); velja samo za gosta. */
export function predogledIzNaslova(): boolean {
  if (typeof window === 'undefined') return false
  return new URLSearchParams(window.location.search).has('poziv')
}

/* Kratka oznaka strani za statistiko (prvi del poti, brez id-jev). */
export function oznakaStrani(pot: string): string {
  const del = pot.split('/').filter(Boolean)[0]
  return del ?? 'domov'
}
