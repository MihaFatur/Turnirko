/* Logika strani »Dostopi« (administrator): stanje računa, relativni časi,
   iskanje, filtri, razvrščanje, skupine po črki priimka in pregled stanja.
   Vse izpeljano iz seznama računov, ki je že ves na klientu (200+).

   Stanje računa ni polje, ampak izpeljanka (po vrstnem redu pogojev):
   čakajoč račun s potrjeno e-pošto (in skrbnikom, če je potreben) je »Čaka« —
   to je zahteva, ki jo mora administrator obdelati; čakajoč račun brez kode
   je »Brez kode« oz. »Čaka skrbnika« in se izbriše sam, zato admina ne
   zaposluje. */
import type { NarocninaRacuna, Paket, RacunIgralcaDto, Vloga } from '../api/tipi'
import { OZNAKE_PAKET, OZNAKE_VLOGA } from '../api/tipi'
import { besedeIskanja, ustrezaBesedam } from './iskanje'

/* ---------- Stanje računa ---------- */

export type KljucStanja = 'caka' | 'brezKode' | 'izklopljen' | 'zavrnjen' | 'potrjen'

/* Zahteva, ki jo admin lahko obdela: naslov potrjen, skrbnik (če je
   potreben) tudi. */
export function jeCakajoca(r: RacunIgralcaDto): boolean {
  return r.status === 'CAKA' && r.emailPotrjen && r.skrbnikPotrjen !== false
}

export function stanjeRacuna(r: RacunIgralcaDto): { kljuc: KljucStanja; besedilo: string } {
  if (r.status === 'CAKA') {
    if (jeCakajoca(r)) return { kljuc: 'caka', besedilo: 'Čaka' }
    return {
      kljuc: 'brezKode',
      besedilo: r.emailPotrjen ? 'Čaka skrbnika' : 'Brez kode',
    }
  }
  if (r.status === 'ZAVRNJEN') return { kljuc: 'zavrnjen', besedilo: 'Zavrnjen' }
  if (!r.aktiven) return { kljuc: 'izklopljen', besedilo: 'Izklopljen' }
  return { kljuc: 'potrjen', besedilo: 'Potrjen' }
}

/* Račun brez imena (administrator, ki ga nihče ni vpisal) se bere po naslovu. */
export function imeRacuna(r: RacunIgralcaDto): string {
  const ime = [r.prijavljenoIme, r.prijavljeniPriimek].filter(Boolean).join(' ')
  return ime || r.email
}

export function priimekRacuna(r: RacunIgralcaDto): string {
  return r.prijavljeniPriimek || r.email
}

export function oznakaVloge(vloga: Vloga): string {
  return OZNAKE_VLOGA[vloga]
}

/* ---------- Časi ---------- */

const MS_NA_DAN = 864e5

export function dniOd(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / MS_NA_DAN))
}

export function dniDo(iso: string): number {
  return Math.round((new Date(iso).getTime() - Date.now()) / MS_NA_DAN)
}

/* »danes«, »včeraj«, »pred 5 d«, »pred 3 t«, »pred 4 m«. */
export function rel(iso: string | null): string {
  if (!iso) return 'nikoli'
  const n = dniOd(iso)
  if (n === 0) return 'danes'
  if (n === 1) return 'včeraj'
  if (n < 14) return `pred ${n} d`
  if (n < 60) return `pred ${Math.round(n / 7)} t`
  return `pred ${Math.round(n / 30)} m`
}

/* »9. 11. 2026« (brez ničel); neveljavno ali manjkajoče = »—«. */
export function datum(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : `${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}`
}

/* »9. 11. 2026 08:31«. */
export function ura(iso: string): string {
  const d = new Date(iso)
  const dvomestno = (n: number) => String(n).padStart(2, '0')
  return `${datum(iso)} ${dvomestno(d.getHours())}:${dvomestno(d.getMinutes())}`
}

/* ---------- Naročnina ---------- */

/* Ime paketa brez oznake vloge (»Organizator Plus« → »Plus«, kot v
   registraciji). Organizator brez zapisa nima paketa; igralec brez zapisa je
   brezplačen. */
export function imeNarocnine(n: NarocninaRacuna): string {
  if (n.paket === null) return 'Brez paketa'
  if (n.paket === 'BREZPLACNO') return 'Brezplačno'
  return OZNAKE_PAKET[n.paket].replace(/^(Igralec|Organizator) /, '')
}

/* Stanje, kadar ga zapis pove: cikel in datum konca obdobja pri aktivni,
   preklicana (do konca obdobja še velja) in potekla (ne velja več) sta
   različni stvari. Brezplačen paket stanja nima. */
export function stanjeNarocnine(n: NarocninaRacuna): string | null {
  if (n.paket === null || n.paket === 'BREZPLACNO' || n.status === null) return null
  const datumDo = n.obdobjeDo ? `do ${datum(n.obdobjeDo)}` : null
  switch (n.status) {
    case 'AKTIVNA':
      return [n.ciklus === 'LETNO' ? 'letno' : 'mesečno', datumDo].filter(Boolean).join(' ')
    case 'PREKLICANA':
      return n.veljavna ? ['preklicana', datumDo].filter(Boolean).join(' ') : 'potekla'
    case 'ZAPADLA':
      return 'zapadla'
    case 'CAKA_PLACILO':
      return 'čaka plačilo'
    case 'PREIZKUS':
      return n.veljavna ? ['preizkus', datumDo].filter(Boolean).join(' ') : 'preizkus potekel'
  }
}

/* Naročnina v eni vrstici na telefonu: rjasto, če poteče v 30 dneh ali ne
   velja več. */
export function narocninaZahtevaPozornost(n: NarocninaRacuna): boolean {
  return !!(
    n.paket &&
    n.paket !== 'BREZPLACNO' &&
    n.obdobjeDo &&
    (!n.veljavna || dniDo(n.obdobjeDo) <= 30)
  )
}

/* Razvrščanje po naročnini: brez paketa < Brezplačno < Premium < Basic < Plus < Pro. */
const RED_PAKETOV: Record<string, number> = {
  BREZPLACNO: 1,
  PREMIUM: 2,
  ORGANIZATOR_BASIC: 3,
  ORGANIZATOR_PLUS: 4,
  ORGANIZATOR_PRO: 5,
}

function redPaketa(r: RacunIgralcaDto): number {
  return r.narocnina.paket === null ? 0 : RED_PAKETOV[r.narocnina.paket]
}

/* ---------- Iskanje, filtri, razvrščanje ---------- */

export type FilterVloge = 'vsi' | Vloga
export type FilterStanja = 'vsi' | KljucStanja
export type FilterPaketa = 'vsi' | 'BREZ' | Paket
export type Razvrstitev = 'ime' | 'prijava' | 'nar' | 'stanje'

export interface FilterRacunov {
  iskanje: string
  vloga: FilterVloge
  stanje: FilterStanja
  paket: FilterPaketa
  samodejno: boolean
}

export const PRAZNI_FILTRI: FilterRacunov = {
  iskanje: '',
  vloga: 'vsi',
  stanje: 'vsi',
  paket: 'vsi',
  samodejno: false,
}

export const MOZNOSTI_PAKETA: [FilterPaketa, string][] = [
  ['vsi', 'Vsi paketi'],
  ['BREZPLACNO', 'Brezplačno'],
  ['PREMIUM', 'Premium'],
  ['ORGANIZATOR_BASIC', 'Basic'],
  ['ORGANIZATOR_PLUS', 'Plus'],
  ['ORGANIZATOR_PRO', 'Pro'],
  ['BREZ', 'Brez paketa'],
]

export const MOZNOSTI_RAZVRSTITVE: [Razvrstitev, string][] = [
  ['ime', 'Priimek A–Ž'],
  ['prijava', 'Zadnja prijava'],
  ['nar', 'Naročnina'],
  ['stanje', 'Stanje'],
]

export const MOZNOSTI_VLOGE: [FilterVloge, string][] = [
  ['vsi', 'Vsi'],
  ['IGRALEC', 'Igralci'],
  ['NAVIJAC', 'Navijači'],
  ['ORGANIZATOR', 'Organizatorji'],
  ['ADMIN', 'Admin'],
]

export const MOZNOSTI_STANJA: [FilterStanja, string][] = [
  ['vsi', 'Vsi'],
  ['caka', 'Čaka'],
  ['potrjen', 'Potrjeni'],
  ['izklopljen', 'Izklopljeni'],
  ['zavrnjen', 'Zavrnjeni'],
  ['brezKode', 'Brez kode'],
]

export function steviloAktivnihFiltrov(f: FilterRacunov): number {
  return (
    (f.vloga !== 'vsi' ? 1 : 0) +
    (f.stanje !== 'vsi' ? 1 : 0) +
    (f.paket !== 'vsi' ? 1 : 0) +
    (f.samodejno ? 1 : 0)
  )
}

/* Besede (ločene s presledki) se morajo vse pojaviti, brez šumnikov in
   velikih črk, v naslovu, imenu, igralcu, klubu in navedenem klubu. */
function iskalnoBesedilo(r: RacunIgralcaDto): string {
  return [
    r.email,
    r.prijavljenoIme ?? '',
    r.prijavljeniPriimek ?? '',
    r.imeIgralca ?? '',
    r.klub ?? '',
    r.klubZelja ?? '',
  ].join(' ')
}

export function ujemaIskanju(r: RacunIgralcaDto, besede: string[]): boolean {
  return ustrezaBesedam(iskalnoBesedilo(r), besede)
}

export function ujemaFiltrom(r: RacunIgralcaDto, f: FilterRacunov, besede: string[]): boolean {
  return (
    ujemaIskanju(r, besede) &&
    (f.vloga === 'vsi' || r.vloga === f.vloga) &&
    (f.stanje === 'vsi' || stanjeRacuna(r).kljuc === f.stanje) &&
    (f.paket === 'vsi' || (r.narocnina.paket === null ? 'BREZ' : r.narocnina.paket) === f.paket) &&
    (!f.samodejno || (r.virPovezave === 'SAMODEJNO' && r.idIgralec !== null))
  )
}

export { besedeIskanja }

const primerjajPoImenu = (a: RacunIgralcaDto, b: RacunIgralcaDto) =>
  priimekRacuna(a).localeCompare(priimekRacuna(b), 'sl') ||
  (a.prijavljenoIme ?? '').localeCompare(b.prijavljenoIme ?? '', 'sl')

const REDNOST_STANJA: Record<KljucStanja, number> = {
  caka: 0,
  brezKode: 1,
  izklopljen: 2,
  zavrnjen: 3,
  potrjen: 4,
}

/* Razvrstitev v naraščajoči smeri; »Zadnja prijava« se v seznamu obrne
   (najnovejša prva, brez prijave zadnji) — smer določi klicatelj. */
export function primerjalnik(
  razvrstitev: Razvrstitev,
): (a: RacunIgralcaDto, b: RacunIgralcaDto) => number {
  switch (razvrstitev) {
    case 'ime':
      return primerjajPoImenu
    case 'nar':
      return (a, b) => redPaketa(a) - redPaketa(b) || primerjajPoImenu(a, b)
    case 'stanje':
      return (a, b) =>
        REDNOST_STANJA[stanjeRacuna(a).kljuc] - REDNOST_STANJA[stanjeRacuna(b).kljuc] ||
        primerjajPoImenu(a, b)
    case 'prijava':
      return (a, b) =>
        (a.zadnjaPrijava ?? '').localeCompare(b.zadnjaPrijava ?? '') || primerjajPoImenu(a, b)
  }
}

export function smerRazvrstitve(razvrstitev: Razvrstitev): 1 | -1 {
  return razvrstitev === 'prijava' ? -1 : 1
}

/* ---------- Abecedno kazalo ---------- */

export const CRKE_KAZALA = [
  'A', 'B', 'C', 'Č', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'R', 'S',
  'Š', 'T', 'U', 'V', 'Z', 'Ž',
] as const

export interface SkupinaRacunov {
  crka: string
  racuni: RacunIgralcaDto[]
}

export function skupinePoCrki(urejeni: RacunIgralcaDto[]): SkupinaRacunov[] {
  const skupine = new Map<string, RacunIgralcaDto[]>()
  for (const r of urejeni) {
    const crka = (priimekRacuna(r)[0] ?? '#').toLocaleUpperCase('sl')
    const obstojeca = skupine.get(crka)
    if (obstojeca) obstojeca.push(r)
    else skupine.set(crka, [r])
  }
  return Array.from(skupine, ([crka, racuni]) => ({ crka, racuni }))
}

/* ---------- Pregled stanja ---------- */

export interface VrsticaPregleda {
  oznaka: string
  n: number
  /* Delež vseh računov, zaokrožen na cel odstotek (širina palice). */
  delez: string
}

export interface SkupinaPregleda {
  naslov: string
  vrstice: VrsticaPregleda[]
}

export function pregledStanja(vsi: RacunIgralcaDto[]): SkupinaPregleda[] {
  const vseh = vsi.length || 1
  const vrstica = (oznaka: string, jeTak: (r: RacunIgralcaDto) => boolean): VrsticaPregleda => {
    const n = vsi.filter(jeTak).length
    return { oznaka, n, delez: `${Math.round((n / vseh) * 100)}%` }
  }
  return [
    {
      naslov: 'Vloge',
      vrstice: [
        vrstica('Igralci', (r) => r.vloga === 'IGRALEC'),
        vrstica('Navijači', (r) => r.vloga === 'NAVIJAC'),
        vrstica('Organizatorji', (r) => r.vloga === 'ORGANIZATOR'),
        vrstica('Administratorji', (r) => r.vloga === 'ADMIN'),
      ],
    },
    {
      naslov: 'Naročnine',
      vrstice: [
        vrstica('Brezplačno', (r) => r.narocnina.paket === 'BREZPLACNO'),
        vrstica('Premium', (r) => r.narocnina.paket === 'PREMIUM'),
        vrstica('Organizatorski paketi', (r) => !!r.narocnina.paket?.startsWith('ORGANIZATOR')),
        vrstica('Organizator brez paketa', (r) => r.vloga === 'ORGANIZATOR' && r.narocnina.paket === null),
      ],
    },
    {
      naslov: 'Povezave z igralcem',
      vrstice: [
        vrstica('Samodejno', (r) => r.virPovezave === 'SAMODEJNO' && r.idIgralec !== null),
        vrstica('Ročno (administrator)', (r) => r.virPovezave === 'ADMIN' && r.idIgralec !== null),
        vrstica('Brez povezave', (r) => r.vloga === 'IGRALEC' && r.idIgralec === null),
      ],
    },
    {
      naslov: 'Prijave v 30 dneh',
      vrstice: [
        vrstica('Aktivni', (r) => !!r.zadnjaPrijava && dniOd(r.zadnjaPrijava) <= 30),
        vrstica(
          'Neaktivni',
          (r) => r.status === 'POTRJEN' && (!r.zadnjaPrijava || dniOd(r.zadnjaPrijava) > 30),
        ),
      ],
    },
  ]
}
