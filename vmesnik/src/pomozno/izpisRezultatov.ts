/* Izpis rezultatov kategorije za tisk: iz mreže dogodka sestavi liste A4 s končno
   razvrstitvijo, skupinskim in izločilnim delom. Organizator izbere dele, vse skupaj
   je en dokument. Brez izrisa, da pravila lista (kaj gre na kateri list) živijo na
   enem mestu.

   Višina se izračuna in ne izmeri (kot pri listkih skupin): vse vrstice imajo stalno
   višino v mm, besedilo se ne lomi, zato je jasno, koliko listov bo, še preden se kaj
   izriše; iste številke postavi izris v spremenljivke sloga, da se ne moreta raziti.

   Zaledje ne rabi ničesar svojega, vse je že v MrezaDto. */
import type { DogodekDto, MrezaDto, SvZrebDto, TekmaDto, TurnirDto } from '../api/tipi'
import { OZNAKE_SISTEM_KRATKO } from '../api/tipi'
import { odsekiZreba, type Odsek } from '../komponente/SvDrevo'
import {
  listiOstalih,
  razdeliNaStopnice,
  udelezenciRazvrstitve,
  type UdelezenecRazvrstitve,
  type VrsticaTiska,
} from './koncnaRazvrstitev'
import { oblikujObdobje, sklonEkip, sklonIgralcev, sklonParov } from './oblikovanje'
import { jeSvRegija, listiSvr, type ListSvr } from './izpisSvr'
import { opisRazpona } from './svRegija'
import { imaMrezo, sklopiZrebMreze, type ZrebMrezeList } from './zrebMreze'
import {
  oznakaKategorije,
  sklopiZrebSkupin,
  sklopKroznega,
  tiskTekma,
  type TiskKolo,
  type TiskSklop,
  type TiskSkupina,
} from './zrebSkupin'

export type DelIzpisa = 'razvrstitev' | 'skupine' | 'mreza'

export const NASLOVI_DELOV: Record<DelIzpisa, string> = {
  razvrstitev: 'Končna razvrstitev',
  skupine: 'Skupinski del',
  mreza: 'Izločilni del',
}

/* Vrstni red delov v dokumentu je vedno ta, ne glede na vrstni red izbire. */
export const VRSTNI_RED_DELOV: DelIzpisa[] = ['razvrstitev', 'skupine', 'mreza']

export type Usmeritev = 'pokoncni' | 'lezeci'

/* Slog izpisa: Turnirkov (privzet) ali SVR, ki posnema Excelove liste SV regije; SVR
   obstaja samo za dogodke SV regije (pomozno/izpisSvr). */
export type SlogIzpisa = 'turnirko' | 'svr'

/* Ali ima dogodek slog SVR: sistem SV regija z nivoji. */
export function imaSlogSvr(mreza: MrezaDto): boolean {
  return jeSvRegija(mreza)
}

/* ---------- Mere lista (mm) ----------
   Stran nima robov (@page margin 0, zato brskalnik ne doda svoje glave in noge),
   rob je odmik lista in je dovolj velik, da ga tiskalnik ne odreže. */
export const MERE_LISTA = {
  pokoncni: { sirina: 210, visina: 297, zgoraj: 10, stran: 12, spodaj: 9 },
  lezeci: { sirina: 297, visina: 210, zgoraj: 9, stran: 10, spodaj: 8 },
  glava: 25,
  noga: 5,
  pokrovitelji: 12.5,
  /* Razmik glava-vsebina in vsebina-noga skupaj. */
  razmik: 4,
} as const

export function sirinaVsebine(usmeritev: Usmeritev): number {
  const list = MERE_LISTA[usmeritev]
  return list.sirina - 2 * list.stran
}

export function visinaVsebine(usmeritev: Usmeritev, sPokrovitelji: boolean): number {
  const list = MERE_LISTA[usmeritev]
  return (
    list.visina - list.zgoraj - list.spodaj - MERE_LISTA.glava - MERE_LISTA.noga - MERE_LISTA.razmik
    - (sPokrovitelji ? MERE_LISTA.pokrovitelji : 0)
  )
}

/* ---------- Glava lista ---------- */

export interface GlavaIzpisa {
  naslov: string
  lastnosti: string
  /* Datum in kraj; datuma ni, kadar ga ime turnirja že nosi (uvoz). */
  kdajKje: string | null
}

function udelezencevNaDogodku(mreza: MrezaDto): string {
  const n = mreza.prijave.filter((p) => p.status !== 'ODJAVLJEN' && p.status !== 'REZERVA').length
  const { disciplina } = mreza.dogodek
  return `${n} ${disciplina === 'EKIPNO' ? sklonEkip(n) : disciplina === 'DVOJICE' ? sklonParov(n) : sklonIgralcev(n)}`
}

/* Kategorija, disciplina in sistem v eni vrstici. */
function lastnostiDogodka(dogodek: DogodekDto): string {
  const sistem = OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja]
  return [
    oznakaKategorije(dogodek),
    dogodek.disciplina === 'EKIPNO' ? 'Ekipno' : dogodek.disciplina === 'DVOJICE' ? 'Dvojice' : 'Posamezno',
    dogodek.sistemTekmovanja === 'IZLOCILNI' || dogodek.sistemTekmovanja === 'KROZNI'
      ? `${sistem} sistem`
      : sistem,
  ].join(' · ')
}

export function glavaIzpisa(mreza: MrezaDto, turnir: TurnirDto | undefined): GlavaIzpisa {
  const datum = turnir ? oblikujObdobje(turnir.datumZacetka, turnir.datumKonca) : ''
  const kraj = turnir ? [turnir.kraj?.ime, turnir.dvorana].filter(Boolean).join(', ') : ''
  const ime = turnir?.ime ?? mreza.dogodek.ime
  return {
    naslov: ime,
    lastnosti: `${lastnostiDogodka(mreza.dogodek)} · ${udelezencevNaDogodku(mreza)}`,
    kdajKje: [datum && !ime.includes(datum) ? datum : null, kraj || null].filter(Boolean).join(' · ') || null,
  }
}

/* ---------- Kateri deli ima dogodek ---------- */

function sklopiSkupin(mreza: MrezaDto): TiskSklop[] {
  return mreza.dogodek.sistemTekmovanja === 'KROZNI' ? sklopKroznega(mreza) : sklopiZrebSkupin(mreza)
}

/* Žrebi SV regije, ki imajo tekme (žreb z enim igralcem jih nima). */
function zrebiSv(mreza: MrezaDto): { zreb: SvZrebDto; nivo: number; nivojev: number; tekme: TekmaDto[] }[] {
  const nivoji = mreza.svRegija?.nivoji ?? []
  return nivoji.flatMap((nivo) =>
    nivo.zrebi.flatMap((zreb) => {
      const tekme = mreza.tekme.filter((t) => zreb.id !== null && t.idZreb === zreb.id)
      return tekme.length > 0 ? [{ zreb, nivo: nivo.nivo, nivojev: nivoji.length, tekme }] : []
    }),
  )
}

export function deliDogodka(mreza: MrezaDto): DelIzpisa[] {
  const deli: DelIzpisa[] = []
  if (mreza.dogodek.dejanskiStatus === 'ZAKLJUCEN' && udelezenciRazvrstitve(mreza).length > 0) {
    deli.push('razvrstitev')
  }
  if (sklopiSkupin(mreza).length > 0) deli.push('skupine')
  const imaIzlocilni = mreza.svRegija
    ? zrebiSv(mreza).length > 0
    : mreza.tekme.some((t) => t.faza !== 'SKUPINA')
  if (imaIzlocilni) deli.push('mreza')
  return deli
}

/* Stikalo »Točke po nizih« ima smisel samo, kadar so kje v izbranih delih vpisane. V
   slogu SVR so točke po nizih samo v razporedu skupin (stolpec »Točke«); žreb jih nima. */
export function imaVpisaneTocke(mreza: MrezaDto, deli: DelIzpisa[], slog: SlogIzpisa = 'turnirko'): boolean {
  return mreza.tekme.some(
    (t) =>
      t.nizi.length > 0
      && (t.faza === 'SKUPINA'
        ? deli.includes('skupine')
        : slog === 'turnirko' && deli.includes('mreza')),
  )
}

/* ---------- Končna razvrstitev ----------
   Razvrstitev mora iti na en list, kadar je le mogoče, in nikoli na več kot dva, kadar
   je le mogoče (zahteva lastnika). Zato tri gostote: najbolj prostorna, pri kateri gre
   vse na en list; če ne gre niti pri najgostejši, najbolj prostorna, pri kateri gre na
   dva. Vrh (mesta 1.-3.) stoji samo na prvem listu. */

export type ImeGostote = 'prostorno' | 'srednje' | 'gosto' | 'najgosteje'

interface GostotaRazvrstitve {
  ime: ImeGostote
  vrh: number
  vrstica: number
}

const GOSTOTE: GostotaRazvrstitve[] = [
  { ime: 'prostorno', vrh: 14, vrstica: 7.4 },
  { ime: 'srednje', vrh: 12, vrstica: 6 },
  { ime: 'gosto', vrh: 10.5, vrstica: 5 },
  /* Samo, da seznam ne gre na tretji list; en list je pri tej pisavi predrag. */
  { ime: 'najgosteje', vrh: 10.5, vrstica: 4.6 },
]

/* Razmik med vrhom in seznamom ostalih. */
const RAZMIK_POD_VRHOM_MM = 4

export interface ListRazvrstitve {
  kljuc: string
  vrsta: 'razvrstitev'
  usmeritev: Usmeritev
  oznaka: string
  gostota: GostotaRazvrstitve
  vrh: UdelezenecRazvrstitve[]
  stolpci: VrsticaTiska[][]
  stolpcev: number
  /* Širina stolpca z mestom po najdaljši oznaki (»129.–160.«). */
  sirinaMesta: number
}

function vrsticNaStolpec(g: GostotaRazvrstitve, visina: number, vrhov: number, prvi: boolean): number {
  const zaVrh = prvi && vrhov > 0 ? vrhov * g.vrh + RAZMIK_POD_VRHOM_MM : 0
  return Math.floor((visina - zaVrh) / g.vrstica)
}

function listovPriGostoti(ostalih: number, g: GostotaRazvrstitve, visina: number, vrhov: number, stolpcev: number) {
  const prvi = vrsticNaStolpec(g, visina, vrhov, true) * stolpcev
  if (ostalih <= prvi) return 1
  return 1 + Math.ceil((ostalih - prvi) / (vrsticNaStolpec(g, visina, vrhov, false) * stolpcev))
}

export function listiRazvrstitve(
  udelezenci: UdelezenecRazvrstitve[],
  stolpcev: number,
  visina: number,
): ListRazvrstitve[] {
  const { vrh, ostali } = razdeliNaStopnice(udelezenci)
  const listov = (g: GostotaRazvrstitve) => listovPriGostoti(ostali.length, g, visina, vrh.length, stolpcev)
  const gostota =
    GOSTOTE.find((g) => g.ime !== 'najgosteje' && listov(g) === 1)
    ?? GOSTOTE.find((g) => listov(g) === 2)
    ?? GOSTOTE[GOSTOTE.length - 1]

  const listi = listiOstalih(
    ostali,
    (indeks) => vrsticNaStolpec(gostota, visina, vrh.length, indeks === 0),
    stolpcev,
  )
  const najdaljsa = Math.max(...udelezenci.map((u) => u.oznaka.length))
  const sirinaMesta = Math.max(11, Math.ceil(najdaljsa * (gostota.ime === 'prostorno' ? 2.15 : 1.8)))
  return (listi.length > 0 ? listi : [[]]).map((stolpci, i) => ({
    kljuc: `razvrstitev-${i}`,
    vrsta: 'razvrstitev',
    usmeritev: 'pokoncni',
    oznaka: NASLOVI_DELOV.razvrstitev,
    gostota,
    vrh: i === 0 ? vrh : [],
    stolpci,
    stolpcev,
    sirinaMesta,
  }))
}

/* ---------- Skupinski del ---------- */

/* Mere v mm; izris jih postavi v iste spremenljivke kot listek skupine (slog križne
   tabele in razporeda je skupen), a so nižje: izpisa nihče ne izpolnjuje na roke. */
export const MERE_SKUPIN = {
  sklop: 7,
  krizGlava: 6.5,
  krizVrstica: 5,
  krizObroba: 1,
  razmik: 1.2,
  seznamGlava: 3.6,
  seznamVrstica: 3.8,
  nadaljevanje: 5,
  medSkupinami: 3.4,
} as const

export type BlokSkupine =
  | { vrsta: 'sklop'; kljuc: string; naslov: string }
  | {
      vrsta: 'skupina'
      kljuc: string
      skupina: TiskSkupina
      /* Križna tabela je na prvem delu; nadaljevanje velike skupine nosi samo tekme. */
      kriznaTabela: boolean
      kola: TiskKolo[]
      del: number
      delov: number
    }
  /* Tekme izločilnega dela, ki jih ni mogoče narisati kot mrežo: samo seznam. */
  | { vrsta: 'tekme'; kljuc: string; skupina: TiskSkupina; kola: TiskKolo[] }

export interface ListSkupin {
  kljuc: string
  vrsta: 'skupine'
  usmeritev: Usmeritev
  oznaka: string
  bloki: BlokSkupine[]
  ekipno: boolean
}

function visinaKrizne(clanov: number): number {
  return MERE_SKUPIN.krizGlava + clanov * MERE_SKUPIN.krizVrstica + MERE_SKUPIN.krizObroba
}

function visinaRazporeda(kola: TiskKolo[]): number {
  if (kola.length === 0) return 0
  const tekem = kola.reduce((vsota, kolo) => vsota + kolo.tekme.length, 0)
  return MERE_SKUPIN.seznamGlava + tekem * MERE_SKUPIN.seznamVrstica
}

function visinaBloka(clanov: number, kola: TiskKolo[], kriznaTabela: boolean): number {
  return kriznaTabela
    ? visinaKrizne(clanov) + (kola.length > 0 ? MERE_SKUPIN.razmik + visinaRazporeda(kola) : 0)
    : MERE_SKUPIN.nadaljevanje + visinaRazporeda(kola)
}

/* Skupina gre na list cela; samo tista, ki ne gre na cel list (velika krožna skupina),
   se nadaljuje na naslednjem, in to med koli, ne sredi kola. */
function deliSkupine(skupina: TiskSkupina, sTockami: boolean, visina: number) {
  const zTockami = sTockami && skupina.kola.some((k) => k.tekme.some((t) => t.tocke.length > 0))
  const clanov = skupina.clani.length
  let preostala = zTockami ? skupina.kola : []
  let kriznaTabela = true
  const deli: { kriznaTabela: boolean; kola: TiskKolo[] }[] = []
  while (deli.length === 0 || preostala.length > 0) {
    let koliko = preostala.length
    while (koliko > 1 && visinaBloka(clanov, preostala.slice(0, koliko), kriznaTabela) > visina) koliko--
    deli.push({ kriznaTabela, kola: preostala.slice(0, koliko) })
    preostala = preostala.slice(koliko)
    kriznaTabela = false
  }
  return deli
}

/* Zlaganje blokov na liste po vrsti; bloki enega klica ostanejo skupaj. */
function zlagalnik(visina: number) {
  const bloki: BlokSkupine[][] = [[]]
  let zasedeno = 0
  return {
    bloki,
    dodaj(noviBloki: BlokSkupine[], visinaNovih: number) {
      let razmik = bloki[bloki.length - 1].length > 0 ? MERE_SKUPIN.medSkupinami : 0
      if (zasedeno + razmik + visinaNovih > visina && bloki[bloki.length - 1].length > 0) {
        bloki.push([])
        zasedeno = 0
        razmik = 0
      }
      bloki[bloki.length - 1].push(...noviBloki)
      zasedeno += razmik + visinaNovih
    },
  }
}

function listiSkupin(mreza: MrezaDto, visina: number, sTockami: boolean): ListSkupin[] {
  const { bloki, dodaj } = zlagalnik(visina)

  for (const sklop of sklopiSkupin(mreza)) {
    let naslovSklopa = sklop.naslov
    for (const skupina of sklop.skupine) {
      const zaNaslov = naslovSklopa ? MERE_SKUPIN.sklop : 0
      const deli = deliSkupine(skupina, sTockami, visina - zaNaslov)
      deli.forEach((del, i) => {
        const blok: BlokSkupine = {
          vrsta: 'skupina',
          kljuc: `${skupina.id}-${i}`,
          skupina,
          kriznaTabela: del.kriznaTabela,
          kola: del.kola,
          del: i + 1,
          delov: deli.length,
        }
        const visinaDela = visinaBloka(skupina.clani.length, del.kola, del.kriznaTabela)
        if (naslovSklopa) {
          dodaj([{ vrsta: 'sklop', kljuc: `sklop-${sklop.kljuc}`, naslov: naslovSklopa }, blok], zaNaslov + visinaDela)
          naslovSklopa = null
        } else {
          dodaj([blok], visinaDela)
        }
      })
    }
  }

  return bloki.map((vsebina, i) => ({
    kljuc: `skupine-${i}`,
    vrsta: 'skupine',
    usmeritev: 'pokoncni',
    oznaka: NASLOVI_DELOV.skupine,
    bloki: vsebina,
    ekipno: mreza.dogodek.disciplina === 'EKIPNO',
  }))
}

/* ---------- Okvir tekme: višina vrstic in pisava iz prostora, ki ga tekma ima ---------- */

export interface MerilaOkvirja {
  visinaStrani: number
  pisava: number
}

/* Točke stojijo v tanki vrstici pod okvirjem, zato imena z njimi dobijo manj prostora. */
export function merilaOkvirja(vrstica: number, zVrsticoTock: boolean): MerilaOkvirja {
  const visinaStrani = Math.min(5.2, (vrstica - (zVrsticoTock ? 2.9 : 0.8)) / 2)
  return { visinaStrani, pisava: Math.min(10.5, Math.round(visinaStrani * 23.3) / 10) }
}

/* ---------- Izločilni del: mreža ---------- */

export interface ListMreze {
  kljuc: string
  vrsta: 'mreza'
  usmeritev: Usmeritev
  oznaka: string
  list: ZrebMrezeList
  vrstica: number
  merila: MerilaOkvirja
  /* Širina stolpcev v mm; prvi nosi še klub, zadnji je stolpec zmagovalca. */
  stolpci: number[]
  sKlubom: boolean
  sTockami: boolean
}

const VISINA_NASLOVOV_KOL_MM = 5
const SIRINA_ZMAGOVALCA_MM = 46
const DODATEK_ZA_KLUB_MM = 18
const NAJVECJI_STOLPEC_MM = 60

function listiMreze(
  mreza: MrezaDto,
  visina: number,
  sTockami: boolean,
  faza: 'GLAVNI' | 'TOLAZILNI',
): ListMreze[] {
  const sklop = sklopiZrebMreze(mreza, faza)
  if (sklop === null) return []
  const zTockami = sTockami && imaVpisaneTocke(mreza, ['mreza'])
  const ekipno = mreza.dogodek.disciplina === 'EKIPNO'
  return sklop.listi.map((list) => {
    const vrstica = Math.min(16, (visina - VISINA_NASLOVOV_KOL_MM) / list.vrstic)
    /* Klub stoji samo tam, kjer igralec v mrežo vstopi: v prvem kolu lista. Naprej se
       isti ljudje samo ponavljajo in prostor je potreben za točke. */
    const sKlubom = !list.nadaljevanje && !ekipno
    const kol = list.kola.length
    const prostor = sirinaVsebine('lezeci') - SIRINA_ZMAGOVALCA_MM - (sKlubom ? DODATEK_ZA_KLUB_MM : 0)
    const stolpec = Math.min(NAJVECJI_STOLPEC_MM, prostor / kol)
    return {
      kljuc: `mreza-${faza}-${list.kljuc}`,
      vrsta: 'mreza',
      usmeritev: 'lezeci',
      oznaka: [faza === 'GLAVNI' ? NASLOVI_DELOV.mreza : 'Tolažilna mreža', list.del].filter(Boolean).join(' · '),
      list,
      vrstica,
      merila: merilaOkvirja(vrstica, zTockami),
      stolpci: [
        ...list.kola.map((_, i) => stolpec + (i === 0 && sKlubom ? DODATEK_ZA_KLUB_MM : 0)),
        SIRINA_ZMAGOVALCA_MM,
      ],
      sKlubom,
      sTockami: zTockami,
    }
  })
}

/* ---------- Izločilni del: tekme, ki niso mreža ----------
   Uvoženi dogodki imajo tudi nepolne mreže in tekme za razvrstitev v tolažilni fazi
   (»za 5.-8. mesto«). Drevesa se iz njih ne da pošteno narisati, rezultat pa mora na
   izpis, zato gredo v seznam po kolih. Kar je narisano v mreži, tu ne stoji. */

function vrstniSeznamTekem(
  naslov: string,
  tekme: TekmaDto[],
  sTockami: boolean,
  privzetoNizov: number,
  ekipno: boolean,
): TiskSkupina {
  const igrane = tekme.filter((t) => t.udelezenec1 !== null && t.udelezenec2 !== null)
  const zTockami = sTockami && !ekipno && igrane.some((t) => t.nizi.length > 0)
  const kola = [...new Set(igrane.map((t) => t.kolo))]
    .sort((a, b) => a - b)
    .map((kolo): TiskKolo => ({
      kolo,
      tekme: igrane
        .filter((t) => t.kolo === kolo)
        .sort((a, b) => a.pozicija - b.pozicija)
        .map((t) => tiskTekma(t, [0, 0])),
      prosti: [],
    }))
  return {
    id: 0,
    naslov,
    clani: [],
    izidi: [],
    izkupicki: [],
    kola,
    steviloNizov: zTockami ? Math.max(privzetoNizov, ...igrane.map((t) => t.nizi.length)) : 0,
  }
}

function listiTekem(mreza: MrezaDto, visina: number, sTockami: boolean): ListSkupin[] {
  const glavne = mreza.tekme.filter((t) => t.faza === 'GLAVNI')
  const tolazilne = mreza.tekme.filter((t) => t.faza === 'TOLAZILNI')
  const tretjeVMrezi = sklopiZrebMreze(mreza)?.listi.some((list) => list.tretje !== null) ?? false
  const tolazilnaVMrezi = tolazilne.length > 1 && imaMrezo(mreza, 'TOLAZILNI')
  const ekipno = mreza.dogodek.disciplina === 'EKIPNO'
  const naSeznam = [
    !imaMrezo(mreza, 'GLAVNI') ? { naslov: 'Izločilne tekme', tekme: glavne } : null,
    !tretjeVMrezi && !tolazilnaVMrezi ? { naslov: 'Tekme za razvrstitev', tekme: tolazilne } : null,
  ].filter((s): s is { naslov: string; tekme: TekmaDto[] } => s !== null && s.tekme.length > 0)
  if (naSeznam.length === 0) return []

  const { bloki, dodaj } = zlagalnik(visina)
  for (const { naslov, tekme } of naSeznam) {
    const seznam = vrstniSeznamTekem(naslov, tekme, sTockami, mreza.dogodek.privzetoSteviloNizov, ekipno)
    let preostala = seznam.kola
    let del = 0
    while (preostala.length > 0) {
      let koliko = preostala.length
      const visinaDela = (n: number) => MERE_SKUPIN.sklop + visinaRazporeda(preostala.slice(0, n))
      while (koliko > 1 && visinaDela(koliko) > visina) koliko--
      dodaj(
        [{ vrsta: 'tekme', kljuc: `${naslov}-${del}`, skupina: seznam, kola: preostala.slice(0, koliko) }],
        visinaDela(koliko),
      )
      preostala = preostala.slice(koliko)
      del++
    }
  }
  return bloki.map((vsebina, i) => ({
    kljuc: `tekme-${i}`,
    vrsta: 'skupine',
    usmeritev: 'pokoncni',
    oznaka: NASLOVI_DELOV.mreza,
    bloki: vsebina,
    ekipno,
  }))
}

/* ---------- Izločilni del: žrebi SV regije ----------
   Žreb za vsa mesta je več poti (glavna 1.-8., poraženci 5.-8., samostojne tekme). Na
   ležeči list gredo v dva pasu: najširša pot levo, ožje desno ob njej; kar desno ne gre,
   pride pod levo. Tako gre žreb 8 igralcev dvakrat na list in žreb 16 cel na en list. */

export const MERE_SV = {
  naslovZreba: 6.5,
  naslovOdseka: 4.4,
  glavaStolpcev: 3.8,
  medOdseki: 2.5,
  medPasovoma: 6,
  medZrebi: 4.5,
  spoj: 4,
  klub: 14,
} as const

/* Vrstica žreba je največ tako visoka: več je pri majhnih žrebih samo prazen prostor,
   in pri tej gresta dva žreba po 8 igralcev na list tudi s pokrovitelji. */
const NAJVISJA_VRSTICA_SV_MM = 10.2

export interface OdsekNaListu {
  odsek: Odsek
  glavni: boolean
  sirina: number
  visina: number
}

export interface BlokZreba {
  kljuc: string
  naslov: string
  zreb: SvZrebDto
  levo: OdsekNaListu[]
  desno: OdsekNaListu[]
  sirinaLevo: number
  visina: number
  vrstica: number
  stolpec: number
  uvrstitev: number
  merila: MerilaOkvirja
  sKlubom: boolean
  sTockami: boolean
}

export interface ListSv {
  kljuc: string
  vrsta: 'sv'
  usmeritev: Usmeritev
  oznaka: string
  zrebi: BlokZreba[]
}

function sirineStolpcevSv(stolpcev: number): { stolpec: number; uvrstitev: number } {
  if (stolpcev <= 2) return { stolpec: 40, uvrstitev: 30 }
  if (stolpcev === 3) return { stolpec: 36, uvrstitev: 28 }
  if (stolpcev === 4) return { stolpec: 33, uvrstitev: 28 }
  if (stolpcev === 5) return { stolpec: 30, uvrstitev: 26 }
  return { stolpec: 27, uvrstitev: 24 }
}

/* Poti enega žreba v bloke; pot, ki ne gre več na list, začne nov blok. */
function blokiZreba(
  podatki: { zreb: SvZrebDto; nivo: number; nivojev: number; tekme: TekmaDto[] },
  visina: number,
  sTockami: boolean,
  sKlubom: boolean,
): BlokZreba[] {
  const { zreb, nivo, nivojev, tekme } = podatki
  const odseki = odsekiZreba(tekme, zreb)
  const zTockami = sTockami && tekme.some((t) => t.nizi.length > 0)
  const kolicina = Math.max(...odseki.map((o) => o.stolpci.length))
  const { stolpec, uvrstitev } = sirineStolpcevSv(kolicina)
  const najvisji = Math.max(...odseki.map((o) => o.visina))
  const vrstica = Math.min(
    NAJVISJA_VRSTICA_SV_MM,
    (visina - MERE_SV.naslovZreba - MERE_SV.naslovOdseka - MERE_SV.glavaStolpcev) / najvisji,
  )
  const naVolji = sirinaVsebine('lezeci')
  const zaVsebino = visina - MERE_SV.naslovZreba

  let cakajo: OdsekNaListu[] = odseki.map((odsek) => {
    const glavni = odsek.od === zreb.prvoMesto
    const n = odsek.stolpci.length
    return {
      odsek,
      glavni,
      sirina: (glavni && sKlubom ? MERE_SV.klub : 0) + n * stolpec + uvrstitev,
      visina: MERE_SV.naslovOdseka + (n > 1 ? MERE_SV.glavaStolpcev : 0) + odsek.visina * vrstica,
    }
  })

  const osnova = [
    nivojev > 1 ? `Skupina ${nivo}` : null,
    zreb.ime,
    opisRazpona(zreb.prvoMesto, zreb.zadnjeMesto),
  ]
    .filter(Boolean)
    .join(' · ')

  const bloki: BlokZreba[] = []
  while (cakajo.length > 0) {
    const sirinaLevo = Math.max(...cakajo.map((o) => o.sirina))
    const sirinaDesno = naVolji - sirinaLevo - MERE_SV.medPasovoma
    const levo: OdsekNaListu[] = []
    const desno: OdsekNaListu[] = []
    let visinaLevo = 0
    let visinaDesno = 0
    const naprej: OdsekNaListu[] = []
    /* Najširša pot je vedno prva v levem pasu, sicer bi žreb razpadel po ožjih. */
    const prva = cakajo.find((o) => o.sirina === sirinaLevo)!
    for (const o of cakajo) {
      const razmikL = levo.length > 0 ? MERE_SV.medOdseki : 0
      const razmikD = desno.length > 0 ? MERE_SV.medOdseki : 0
      if (o === prva) {
        levo.push(o)
        visinaLevo += o.visina
      } else if (o.sirina <= sirinaDesno && visinaDesno + razmikD + o.visina <= zaVsebino) {
        desno.push(o)
        visinaDesno += razmikD + o.visina
      } else if (levo.length > 0 && visinaLevo + razmikL + o.visina <= zaVsebino) {
        levo.push(o)
        visinaLevo += razmikL + o.visina
      } else {
        naprej.push(o)
      }
    }
    bloki.push({
      kljuc: `${zreb.id}-${bloki.length}`,
      naslov: bloki.length > 0 ? `${osnova} · nadaljevanje` : osnova,
      zreb,
      levo,
      desno,
      sirinaLevo,
      visina: MERE_SV.naslovZreba + Math.max(visinaLevo, visinaDesno),
      vrstica,
      stolpec,
      uvrstitev,
      merila: merilaOkvirja(vrstica, zTockami),
      sKlubom,
      sTockami: zTockami,
    })
    cakajo = naprej
  }
  return bloki
}

function listiSv(mreza: MrezaDto, visina: number, sTockami: boolean): ListSv[] {
  const sKlubom = mreza.dogodek.disciplina !== 'EKIPNO'
  const listi: BlokZreba[][] = []
  let zasedeno = visina
  for (const podatki of zrebiSv(mreza)) {
    for (const blok of blokiZreba(podatki, visina, sTockami, sKlubom)) {
      const razmik = zasedeno > 0 && zasedeno < visina ? MERE_SV.medZrebi : 0
      if (listi.length === 0 || zasedeno + razmik + blok.visina > visina) {
        listi.push([blok])
        zasedeno = blok.visina
      } else {
        listi[listi.length - 1].push(blok)
        zasedeno += razmik + blok.visina
      }
    }
  }
  return listi.map((zrebi, i) => ({
    kljuc: `sv-${i}`,
    vrsta: 'sv',
    usmeritev: 'lezeci',
    oznaka: NASLOVI_DELOV.mreza,
    zrebi,
  }))
}

/* ---------- Dokument ---------- */

export type ListIzpisa = ListRazvrstitve | ListSkupin | ListMreze | ListSv | ListSvr

/* Par ima dve dolgi imeni in ekipa lahko dolgo ime kluba (»Namiznoteniški klub Tempo
   Velenje 1«): v polovico lista ne gresta. Posameznik gre vedno. */
const NAJDALJSE_IME_V_STOLPCU = 30

function stolpcevRazvrstitve(dogodek: DogodekDto, udelezenci: UdelezenecRazvrstitve[]): number {
  if (dogodek.disciplina === 'DVOJICE') return 1
  if (dogodek.disciplina !== 'EKIPNO') return 2
  return udelezenci.some((u) => u.priimek.length > NAJDALJSE_IME_V_STOLPCU) ? 1 : 2
}

/* Spodnja meja vsebine lista SVR v pt: pri čistem listu (kot vzorec) skoraj do roba, ob
   dodatnem oblikovanju nad nogo (pokrovitelji, številka lista). */
const PT_NA_MM = 72 / 25.4

function spodnjaMejaSvr(sPokrovitelji: boolean, sOblikovanjem: boolean): number {
  const rezervaMm = sOblikovanjem
    ? MERE_LISTA.pokoncni.spodaj + MERE_LISTA.noga + (sPokrovitelji ? MERE_LISTA.pokrovitelji : 0) + 2
    : 14
  return (MERE_LISTA.pokoncni.visina - rezervaMm) * PT_NA_MM
}

/* Naslov (ime turnirja) potrebuje samo slog SVR: tam je v velikem naslovu lista. */
export function sestaviIzpis(
  mreza: MrezaDto,
  izbrani: DelIzpisa[],
  sTockami: boolean,
  sPokrovitelji: boolean,
  slog: SlogIzpisa = 'turnirko',
  sOblikovanjem = true,
  naslov = '',
): ListIzpisa[] {
  const pokoncni = visinaVsebine('pokoncni', sPokrovitelji)
  const lezeci = visinaVsebine('lezeci', sPokrovitelji)
  const razvrstitev = () => {
    const udelezenci = udelezenciRazvrstitve(mreza)
    return listiRazvrstitve(udelezenci, stolpcevRazvrstitve(mreza.dogodek, udelezenci), pokoncni)
  }

  /* Slog SVR: razvrstitev ostane Turnirkov list, skupine in žrebi so po nivojih kot v Excelu. */
  if (slog === 'svr' && imaSlogSvr(mreza)) {
    return [
      ...(izbrani.includes('razvrstitev') ? razvrstitev() : []),
      ...listiSvr(
        mreza,
        { skupine: izbrani.includes('skupine'), mreza: izbrani.includes('mreza') },
        naslov || mreza.dogodek.ime,
        sTockami,
        { spodaj: spodnjaMejaSvr(sPokrovitelji, sOblikovanjem) },
      ),
    ]
  }

  return VRSTNI_RED_DELOV.filter((del) => izbrani.includes(del)).flatMap((del): ListIzpisa[] => {
    if (del === 'razvrstitev') return razvrstitev()
    if (del === 'skupine') return listiSkupin(mreza, pokoncni, sTockami)
    if (mreza.svRegija) return listiSv(mreza, lezeci, sTockami)
    const tolazilnih = mreza.tekme.filter((t) => t.faza === 'TOLAZILNI').length
    return [
      ...listiMreze(mreza, lezeci, sTockami, 'GLAVNI'),
      ...(tolazilnih > 1 ? listiMreze(mreza, lezeci, sTockami, 'TOLAZILNI') : []),
      ...listiTekem(mreza, pokoncni, sTockami),
    ]
  })
}
