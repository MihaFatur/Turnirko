/* Podatki celozaslonskega oglasa »Igralec Premium« (komponente/PremiumOglas.tsx):
   besedila po kontekstu, primerjava paketov in izračun cen.

   Besedila so prepisana dobesedno iz predaje (design_handoff_premium_popup,
   objekt KONTEKST in PRIMERJAVA v prototipu). Cene se NE vpisujejo: izračunajo
   se iz cenika v api/tipi.ts, ki zrcali CenikStoritev na zaledju. */
import { CENA_PREMIUM_LETNO, CENA_PREMIUM_MESECNO, type CiklusPlacila } from '../api/tipi'
import { sklonIgralcev } from './oblikovanje'

/* Kaj je oglas sprožilo: klik na zaklenjeno funkcijo. */
export type KontekstOglasa = 'statistika' | 'lige'

/* Tri različice iste ponudbe: Zavesa, Ključavnica, Končni izid. */
export type RazlicicaOglasa = '1a' | '1b' | '1c'

export const RAZLICICE: readonly RazlicicaOglasa[] = ['1a', '1b', '1c']

/* Vsakič, ko se oglas odpre, se izbere ena od treh različic, vsaka z enako
   verjetnostjo (predaja: »Math.random(); enaka verjetnost 1/3«). Vreča (glej
   maskoto) tu ni potrebna: oglas se pokaže ob kliku, ne po urniku, in gledalec
   ga vidi enkrat ali dvakrat. */
export function nakljucnaRazlicica(): RazlicicaOglasa {
  return RAZLICICE[Math.floor(Math.random() * RAZLICICE.length)]
}

export interface BesedilaKonteksta {
  /* Oznaka v glavi (namizje). */
  oznaka: string
  /* Kratka oznaka: trak predogleda, oznaka na telefonu, poudarek v primerjavi. */
  kratko: string
  /* Vrstica primerjalne tabele, ki ustreza kontekstu (poudarjena). */
  vrstica: string
  /* 1a: kratek naslov (namizje) in daljši (telefon). */
  aKratko: string
  aNad: string
  aGlavni: string
  znacke: string[]
  /* 1b: naslov in uvod. */
  bNad: string
  bGlavni: string
  uvod: string
  /* Naslov predogleda (1a) in njegove vrstice; vrednosti so zamegljene. */
  naslov: string
  predogled: { a: string; b: string }[]
}

export const KONTEKSTI: Record<KontekstOglasa, BesedilaKonteksta> = {
  lige: {
    oznaka: 'Zaklenjeno · Spremljanje lig',
    kratko: 'Spremljanje lig',
    vrstica: 'Spremljanje lig na domači strani',
    aKratko: 'Tvoje lige. Prve.',
    aNad: 'Zavesa gor —',
    aGlavni: 'tvoje lige na prvi strani.',
    znacke: ['Lige na domači', 'Forma', 'Napovedi'],
    bNad: 'Odkleni',
    bGlavni: 'spremljanje lig.',
    uvod: 'Kola izbranih lig se pokažejo na domači strani — brez iskanja po seznamu. Uredi, katere lige spremljaš.',
    naslov: 'Moje lige',
    predogled: [
      { a: '1. SNTL · moški', b: 'Kolo 6 · sob' },
      { a: '2. liga vzhod', b: 'Kolo 6 · sob' },
      { a: 'Ženska liga', b: 'Kolo 4 · ned' },
      { a: 'Veteranska liga', b: 'Kolo 3 · sre' },
    ],
  },
  statistika: {
    oznaka: 'Zaklenjeno · Zasebna statistika',
    kratko: 'Zasebna statistika',
    vrstica: 'Nizi, točke in razrezi',
    aKratko: 'Vse tvoje številke.',
    aNad: 'Zavesa gor —',
    aGlavni: 'tvoja statistika brez skrivnosti.',
    znacke: ['Nizi', 'Forma', 'Napovedi'],
    bNad: 'Odkleni',
    bGlavni: 'svojo statistiko.',
    uvod: 'Delež dobljenih nizov, tesne končnice in razrez po sistemu, kategoriji in sezoni. Vidiš jo samo ti.',
    naslov: 'Zasebna statistika',
    predogled: [
      { a: 'Dobljeni nizi', b: '64 %' },
      { a: 'Tesne končnice', b: '11 : 7' },
      { a: 'Forma · zadnjih 10', b: '7 – 3' },
      { a: 'Najtežji nasprotnik', b: '2 : 5' },
    ],
  },
}

/* Primerjava paketov v »Končnem izidu« (1c): [ime, ali ga ima tudi Free]. */
export const PRIMERJAVA: readonly [string, boolean][] = [
  ['Rezultati, lestvice, koledar', true],
  ['Prijava z lastnim računom', true],
  ['Forma in nasprotniki', false],
  ['Napoved tekme', false],
  ['Nizi, točke in razrezi', false],
  ['Spremljanje lig na domači strani', false],
]

/* Izid tekme Free : Premium v 1c; števec šteje po točkah v tem vrstnem redu
   (P = Premium, F = Free), zato se konča pri 2 : 6. */
export const TOCKE_IZIDA: readonly ('P' | 'F')[] = ['P', 'F', 'P', 'P', 'F', 'P', 'P', 'P']
export const KONCNI_IZID = { free: 2, premium: 6 } as const

/* Znesek z nedeljivim presledkom pred »€«, da se številka in znak ne ločita. */
export function eur(znesek: number): string {
  return znesek.toLocaleString('sl-SI', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €'
}

export interface CeneOglasa {
  cikel: CiklusPlacila
  letno: boolean
  /* Cena izbranega cikla po izbranem pasu. */
  cenaN: number
  cena: string
  enota: string
  /* Dvanajst mesečnih plačil, prečrtano ob letni ceni. */
  sidro: string
  prihranek: string
  naTeden: string
  mesecna: string
  /* Cena DRUGEGA pasu (mlajši od 21) za isti cikel: besedilo stikala. */
  drugaCena: string
  cenaOdMesecno: string
  /* Koliko prihrani letni paket (za povezavo pri mesečnem izboru). */
  prihranekLetni: string
}

/* Pas se v ponudbi izbere s stikalom (`u21`), pri prijavljenem igralcu z znano
   starostjo pa ga določi račun (glej PremiumOglas: stikalo se tam ne pokaže).
   Kar tu preberemo, je isti cenik kot v RegistracijaTok in NarocninaIgralec. */
export function izracunajCene(cikel: CiklusPlacila, u21: boolean): CeneOglasa {
  const pas = u21 ? 'mlajsi' : 'starejsi'
  const letno = cikel === 'LETNO'
  const cenik = letno ? CENA_PREMIUM_LETNO : CENA_PREMIUM_MESECNO
  const cenaN = cenik[pas]
  const sidroN = CENA_PREMIUM_MESECNO[pas] * 12
  const tedenN = letno ? cenaN / 52 : (cenaN * 12) / 52
  return {
    cikel,
    letno,
    cenaN,
    cena: eur(cenaN),
    enota: letno ? 'na leto' : 'na mesec',
    sidro: eur(sidroN),
    prihranek: eur(sidroN - CENA_PREMIUM_LETNO[pas]),
    naTeden: eur(tedenN),
    mesecna: eur(CENA_PREMIUM_MESECNO[pas]),
    drugaCena: eur(cenik.mlajsi),
    cenaOdMesecno: eur(CENA_PREMIUM_MESECNO.mlajsi),
    prihranekLetni: eur(sidroN - CENA_PREMIUM_LETNO[pas]),
  }
}

/* Socialni dokaz: »412 igralcev iz tvojega kluba že ima Premium«. Število pride
   z zaledja (GET /premium/dokaz), brez števila vrstice sploh ni. Glagol se
   ujema s števnikom (5 igralcev že ima, 3 igralci že imajo, 2 igralca že
   imata), zato ga sestavimo tu in ne z eno samo besedno zvezo. */
export interface BesedilaDokaza {
  krepko: string
  rep: string
}

function osebaGlagola(n: number, ena: string, dve: string, tri: string, vec: string): string {
  const ostanek = n % 100
  if (ostanek === 1) return ena
  if (ostanek === 2) return dve
  if (ostanek === 3 || ostanek === 4) return tri
  return vec
}

/* `sIgra`: »že igra s Premium.« (1c) namesto »že ima Premium« (1a, 1b). */
export function besediloDokaza(
  stevilo: number,
  izKluba: boolean,
  sIgra: boolean,
): BesedilaDokaza {
  const krepko = `${stevilo} ${sklonIgralcev(stevilo)}${izKluba ? ' iz tvojega kluba' : ''}`
  if (sIgra) {
    const glagol = osebaGlagola(stevilo, 'igra', 'igrata', 'igrajo', 'igra')
    return { krepko, rep: ` že ${glagol} s Premium.` }
  }
  const glagol = osebaGlagola(stevilo, 'ima', 'imata', 'imajo', 'ima')
  return { krepko, rep: ` že ${glagol} Premium` }
}

/* Začetnice v kvadratkih (»MK«, »AŽ«, »TP«) v predaji so samo primer in pravih
   imen ne pokažemo: Premium igralca je osebna odločitev in njegovo ime ne sme
   priti do gledalca. Kvadratki so zato brez črk, zadnji nosi število ostalih. */
export function kvadratkiDokaza(stevilo: number): { prazni: number; ostalo: number | null } {
  const prazni = Math.min(stevilo, 3)
  return { prazni, ostalo: stevilo > 3 ? stevilo - 3 : null }
}
