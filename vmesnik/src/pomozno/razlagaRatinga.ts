/* Čista logika javne razlage ratinga (/o-ratingu): oblikovanje, sestava K iz
   številk pravil, geometrija grafov in odločitve, ki jih stran razloži.

   Načelo iz PRODUCT.md: strežnik odloča, vmesnik prikazuje. Zato tu NI
   obrazca ratinga — spremembe po tekmi in rating po prvem dnevu računa
   strežnik (`razlagaRatingaApi`). Tu je samo to, česar strežnik ne pozna ali
   mu ni treba vedeti: kako se številke narišejo, kako se besedilo sestavi in
   kateri od strežniških izidov ustreza številki, ki jo je gledalec prepisal
   iz zapisnika. Edini izračun je K po številu tekem (korak 03): vsota osnove
   in pribitkov iz pravil, ki jo drži test `kPoTekmahSledSestaviIzPravil`. */
import type { PravilaRatingaDto, RavenTekmovanja, Spol } from '../api/tipi'
import { sklonTekem } from './oblikovanje'

/* Najnižji in najvišji rating, ki ga preizkus sprejme (isti meji kot pri
   postavitvenem ratingu v zaledju). */
export const NAJMANJ = 100
export const NAJVEC = 3000
export const NAJVEC_TEKEM_DNE = 12

/* Izhodišče, kadar sidra za starost ni (prazna tabela v razvojni bazi). */
export const PRIVZETO_IZHODISCE = 1000

export const RAVNI: RavenTekmovanja[] = ['URADNO', 'KLUBSKO', 'REKREATIVNO']

export const KRATKO_RAVEN: Record<RavenTekmovanja, string> = {
  URADNO: 'Uradno',
  KLUBSKO: 'Klubsko',
  REKREATIVNO: 'Rekreativno',
  NE_STEJE: '–',
}

export const PRIMERI_RAVNI: Record<RavenTekmovanja, string> = {
  URADNO: 'turnirji in lige NTZS',
  KLUBSKO: 'npr. Savinja liga',
  REKREATIVNO: 'rekreativni turnirji in lige',
  NE_STEJE: '',
}

/* Kratka imena korakov: kazalo ob strani in trak napredka na telefonu. */
export const IMENA_KORAKOV = [
  'Napoved',
  'Sprememba',
  'K',
  'Teža',
  'Preizkus tekme',
  'Razloži spremembo',
  'Prvi dan',
  'Ko ne igraš',
  'Lestvica',
  'Zakaj …?',
]

/* Stara sidra strani (pred prenovo v deset korakov) so kazala na razdelka
   z opisnima imenoma; povezave z drugih strani in zaznamki ostanejo veljavni. */
export const VZDEVKI_SIDER: Record<string, string> = {
  'prvi-dan': 'k7',
  lestvica: 'k9',
}

/* ---------- Oblikovanje ---------- */

/* Pravi minus (−) namesto vezaja; ±0 pri ničli, ker »+0« obljublja pridobitev. */
export function sPredznakom(v: number): string {
  if (v === 0) return '±0'
  return v > 0 ? `+${v}` : `−${Math.abs(v)}`
}

export function decimalno(v: number): string {
  return v.toFixed(2).replace('.', ',')
}

export function odstotekTeze(teza: number): string {
  return `${Math.round(teza * 100)} %`
}

export function veljavenRating(v: string): boolean {
  return v !== '' && Number(v) >= NAJMANJ && Number(v) <= NAJVEC
}

/* ---------- K po številu tekem ---------- */

export function kZa(p: PravilaRatingaDto, tekem: number, vrnitev: boolean): number {
  return (
    p.kOsnovni
    + (tekem < p.pragUstaljen ? p.pribitekNeustaljen : 0)
    + (tekem < p.pragNovinec ? p.pribitekNovinec : 0)
    + (vrnitev ? p.pribitekVrnitev : 0)
  )
}

/* Največji možni K (novinec po vrnitvi): višina stolpca = K / največji K. */
export function kNajvec(p: PravilaRatingaDto): number {
  return p.kOsnovni + p.pribitekNeustaljen + p.pribitekNovinec + p.pribitekVrnitev
}

/* ---------- Krivulja napovedi (korak 01) ---------- */

/* Platno krivulje je 1200 × 380 enot; razlika −600…+600 leži na x = razlika +
   600, verjetnost pa na y = 20 + (1 − p) × 320 (vodoravne črte pri p = 0, 0,5
   in 1). */
export const KRIVULJA = { sirina: 1200, visina: 380, najvecRazlika: 600, korak: 10 }

export const krivX = (razlika: number) =>
  ((razlika + KRIVULJA.najvecRazlika) / (2 * KRIVULJA.najvecRazlika)) * KRIVULJA.sirina
export const krivY = (p: number) => 20 + (1 - p) * 320

export interface Napovedi {
  /* Verjetnost zmage boljšega pri dani razliki; razlike so na 10 točk. */
  verjetnost: (razlika: number) => number
  seznam: PravilaRatingaDto['napovedi']
}

export function napovedi(p: PravilaRatingaDto): Napovedi {
  const poRazliki = new Map(p.napovedi.map((n) => [n.razlika, n.verjetnost]))
  return {
    verjetnost: (razlika) => poRazliki.get(razlika) ?? 0.5,
    seznam: p.napovedi,
  }
}

export function potKrivulje(n: Napovedi): string {
  return n.seznam
    .map((t, i) => `${i === 0 ? 'M' : 'L'}${krivX(t.razlika).toFixed(1)} ${krivY(t.verjetnost).toFixed(1)}`)
    .join(' ')
}

export interface StolpecKrivulje {
  razlika: number
  x: number
  y: number
  visina: number
  barva: 'izbran' | 'levo' | 'desno'
}

/* 60 stolpcev za razlike −590, −570 … +590: napoved za vsakih 20 točk. */
export function stolpciKrivulje(n: Napovedi, izbrana: number): StolpecKrivulje[] {
  return Array.from({ length: 60 }, (_, i) => {
    const razlika = -590 + i * 20
    const visina = n.verjetnost(razlika) * 320
    return {
      razlika,
      x: krivX(razlika) - 7,
      y: 340 - visina,
      visina,
      barva: Math.abs(razlika - izbrana) <= 10 ? 'izbran' : razlika < izbrana ? 'levo' : 'desno',
    }
  })
}

export const OZNAKE_OSI_X = [-600, -400, -200, 0, 200, 400, 600]

/* ---------- Primer v zapisniku (korak 06) ---------- */

/* Sprememba iz zapisnika: »−15«, »-15«, »+34« in »34«; vse drugo ni število. */
export function preberiSpremembo(vpis: string): number | null {
  const cisto = vpis.replace('−', '-').replace(/\s/g, '')
  return /^[+-]?\d+$/.test(cisto) ? parseInt(cisto, 10) : null
}

export function jeVpisSpremembe(vpis: string): boolean {
  return /^[+\-−]?\d{0,4}$/.test(vpis.trim())
}

/* ---------- Prvi dan novinca (korak 07) ---------- */

export function sidroZa(p: PravilaRatingaDto, spol: Spol, starost: number): number {
  return (
    p.sidra.find((s) => s.spol === spol && s.starost === starost)?.vrednost ?? PRIVZETO_IZHODISCE
  )
}

export interface GrafDneva {
  vrednosti: number[]
  sestevek: number[]
  tocke: { x: number; y: number; vrednost: number; naprej: 'izhodisce' | 'porast' | 'padec' }[]
  potUvrstitve: string
  potSestevka: string
  mrezaY: number[]
}

/* Graf 720 × 260: os x enakomerno od izhodišča do zadnje tekme (x 40–700), os
   y samodejno (najmanjša in največja vrednost obeh nizov ± 40, y 16–236). */
export function grafDneva(izhodisce: number, koraki: { rating: number; sprememba: number; ratingSestevek: number }[]): GrafDneva {
  const vrednosti = [izhodisce, ...koraki.map((k) => k.rating)]
  const sestevek = [izhodisce, ...koraki.map((k) => k.ratingSestevek)]
  const vse = [...vrednosti, ...sestevek]
  const nizko = Math.min(...vse) - 40
  const visoko = Math.max(...vse) + 40
  const n = Math.max(1, vrednosti.length - 1)
  const dx = (i: number) => 40 + (i / n) * 660
  const dy = (v: number) => 16 + (1 - (v - nizko) / (visoko - nizko)) * 220
  const niz = (v: number[]) => v.map((x, i) => `${dx(i).toFixed(1)},${dy(x).toFixed(1)}`).join(' ')
  return {
    vrednosti,
    sestevek,
    tocke: vrednosti.map((v, i) => ({
      x: dx(i),
      y: dy(v),
      vrednost: v,
      naprej: i === 0 ? 'izhodisce' : koraki[i - 1].sprememba >= 0 ? 'porast' : 'padec',
    })),
    potUvrstitve: niz(vrednosti),
    potSestevka: niz(sestevek),
    mrezaY: [0.2, 0.4, 0.6, 0.8].map((f) => 16 + f * 220),
  }
}

/* ---------- Ko ne igraš (korak 08) ---------- */

export const NAJVEC_MESECEV = 30

/* Skupni odbitek po toliko mesecih brez tekme: zadnja stopnja, ki je že
   zapadla. */
export function odbitekZa(p: PravilaRatingaDto, mesecev: number): number {
  return [...p.odbitki]
    .sort((a, b) => a.mesecev - b.mesecev)
    .reduce((odbitek, o) => (mesecev >= o.mesecev ? o.skupaj : odbitek), 0)
}

export interface Mejnik {
  mesecev: number
  vrednost: string
  opis: string
  skrit: boolean
}

/* Mejniki nad in pod ravnilom: stopnje odbitka, vrnitev (K + pribitek) in
   skritje z javne lestvice, po mesecih. Zadnja stopnja odbitka je »konec«. */
export function mejniki(p: PravilaRatingaDto): Mejnik[] {
  const stopnje = [...p.odbitki].sort((a, b) => a.mesecev - b.mesecev)
  const iz = stopnje.map<Mejnik>((o, i) => ({
    mesecev: o.mesecev,
    vrednost: `−${o.skupaj}`,
    opis:
      i === stopnje.length - 1
        ? 'konec'
        : o.mesecev === p.mesecevZaVrnitev
          ? `vrnitev K+${p.pribitekVrnitev}`
          : 'odbitek',
    skrit: false,
  }))
  return [...iz, { mesecev: p.mesecevDoSkritja, vrednost: 'skrit', opis: 'z lestvice', skrit: true }].sort(
    (a, b) => a.mesecev - b.mesecev,
  )
}

/* ---------- Na kateri lestvici si (korak 09) ---------- */

export type TekmeNaLestvici = 'nic' | 'malo' | 'dovolj'

export const LESTVICE = [
  { oznaka: 'Lestvica', ime: 'Moški tekmovalci' },
  { oznaka: 'Lestvica', ime: 'Ženske tekmovalke' },
  { oznaka: 'Lestvica', ime: 'Moški rekreativci' },
  { oznaka: 'Lestvica', ime: 'Ženske rekreativke' },
  { oznaka: 'Zunaj', ime: 'Ni na lestvici' },
]

export interface OdlocitevLestvice {
  /* Indeks v LESTVICE; zadnji je »Ni na lestvici«. */
  izbrana: number
  razlog: string
  imaKategorije: boolean
}

export function odlocitevLestvice(
  p: PravilaRatingaDto,
  spol: 'M' | 'Z',
  tekme: TekmeNaLestvici,
  imaRating: boolean,
  zadnjaPod18: boolean,
): OdlocitevLestvice {
  const zunaj = LESTVICE.length - 1
  if (!imaRating) {
    return { izbrana: zunaj, razlog: 'Ni na lestvici: nobena tekma še ni štela v rating.', imaKategorije: false }
  }
  if (!zadnjaPod18) {
    return {
      izbrana: zunaj,
      razlog: `Ni na lestvici: ${p.mesecevDoSkritja} mesecev ni igral. Rating mu ostane na profilu; ko spet zaigra, se vrne.`,
      imaKategorije: false,
    }
  }
  const rekreativec = tekme !== 'dovolj'
  return {
    izbrana: (spol === 'M' ? 0 : 1) + (rekreativec ? 2 : 0),
    razlog: rekreativec
      ? `Rekreativec: na uradnem ali klubskem tekmovanju še ni odigral ${p.pragRekreativca} tekem. Ko jih odigra, gre med tekmovalce in tam ostane.`
      : `Tekmovalec: odigral je vsaj ${p.pragRekreativca} ${sklonTekem(p.pragRekreativca)} na uradnem ali klubskem tekmovanju.`,
    imaKategorije: !rekreativec,
  }
}

/* Kategorije, v katerih igralca vidiš: mladinski pasovi, če je mlajši od
   meje, člani vedno, veterani od meje naprej. */
export function kategorijeIgralca(
  p: PravilaRatingaDto,
  starost: number,
): { oznaka: string; aktivna: boolean }[] {
  return [
    ...p.kategorije.map((k) => ({ oznaka: k.pas as string, aktivna: starost < k.mlajsiOd })),
    { oznaka: 'Člani', aktivna: true },
    { oznaka: 'Veterani', aktivna: starost >= p.veteraniOd },
  ]
}

/* ---------- Zakaj …? (korak 10) ---------- */

export interface Vprasanje {
  q: string
  a: string
  href: string
  povezava: string
}

/* Besedila so iz maket; številke pravil se vstavijo iz pravil, da odgovor ne
   zastara ob naslednji umeritvi. */
export function vprasanja(p: PravilaRatingaDto): Vprasanje[] {
  const teza = (r: RavenTekmovanja) => odstotekTeze(p.ravni.find((x) => x.raven === r)?.teza ?? 1)
  return [
    {
      q: 'Zakaj se rating v enem večeru spusti za več kot 100 točk?',
      a: 'Najverjetneje je bil to prvi dan igranja. Takrat se rating po vsaki tekmi izračuna znova iz vseh izidov dneva, zato se številka v zapisniku premakne za stotine točk.',
      href: '#k7',
      povezava: 'Korak 07 →',
    },
    {
      q: 'Zakaj zmaga prinese samo nekaj točk?',
      a: 'Ker je bila zmaga pričakovana. Proti igralcu z veliko nižjim ratingom je možnost zmage velika, zato je razlika med doseženim in pričakovanim majhna. Poraz proti istemu igralcu bi vzel veliko.',
      href: '#k1',
      povezava: 'Korak 01 →',
    },
    {
      q: 'Zakaj 3 : 0 in 3 : 2 prineseta isto?',
      a: 'Šteje, kdo je zmagal, ne kako. Merjenje na pravih tekmah je pokazalo, da izid v nizih napovedi ne izboljša.',
      href: '#k2',
      povezava: 'Korak 02 →',
    },
    {
      q: 'Zakaj ena stran dobi več, kot druga izgubi?',
      a: 'Ker imata različen K. Novinec ali igralec po vrnitvi ima večji K, zato se premakne bolj. Pri enakem K je vsota obeh sprememb natanko nič.',
      href: '#k3',
      povezava: 'Korak 03 →',
    },
    {
      q: 'Zakaj je zmaga v Savinja ligi vredna manj kot na turnirju NTZS?',
      a: `Vsako tekmovanje ima težo: uradno ${teza('URADNO')}, klubsko ${teza('KLUBSKO')}, rekreativno ${teza('REKREATIVNO')}.`,
      href: '#k4',
      povezava: 'Korak 04 →',
    },
    {
      q: 'Zakaj se rating spremeni brez tekme?',
      a: 'Dva razloga: odbitek za odsotnost in popravek. Če organizator za nazaj popravi rezultat ali vpiše starejše kolo, se rating od dneva te tekme izračuna znova — tvoja tekma je vplivala na vse poznejše.',
      href: '#k8',
      povezava: 'Korak 08 →',
    },
    {
      q: 'Zakaj mesto na lestvici pade brez tekme?',
      a: 'Mesto je primerjava: če igralci pod tabo zmagujejo, te prehitijo — tudi brez tvoje tekme. Puščica ob imenu kaže premik mest v zadnjih 30 dneh.',
      href: '#k9',
      povezava: 'Korak 09 →',
    },
    {
      q: 'Zakaj ima nekdo rating z napisanim virom?',
      a: 'Nekateri igralci pri nas odigrajo le nekaj tekem na leto, ker igrajo v tujini. Njihovo moč administrator prepiše z zunanje lestvice (npr. ITTF) — vir in pojasnilo sta vedno javno izpisana na profilu.',
      href: '#k6',
      povezava: 'Korak 06 →',
    },
    {
      q: 'Ali je to uradna jakostna lestvica NTZS?',
      a: 'Ne. Turnirko rating je lastna ocena Turnirka iz vseh tekem v bazi. Uradne jakostne lestvice vodi Namiznoteniška zveza Slovenije.',
      href: '#k9',
      povezava: 'Lestvica →',
    },
    {
      q: 'Kako vemo, da so številke prave?',
      a: 'K in teže niso ugibanje: izmerjene so na desettisočih tekem iz zgodovine NTZS tako, da rating vsako tekmo najprej napove in se šele nato posodobi. Izbrane so vrednosti, ki izide napovedo najbolje.',
      href: '#k3',
      povezava: 'Korak 03 →',
    },
  ]
}
