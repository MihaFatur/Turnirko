/* Izpis rezultatov v slogu SVR: liste sestavi tako, kot jih je do zdaj vodila Excelova
   tabela SV regije (1. in 5. OT SV regije): na list dve skupini s križno tabelo in
   razporedom po kolih, nato list žreba za vsa mesta (glavna pot 1.-8., poti za 3., 5.,
   7. mesto ...). Samo za dogodke SV regije, pokončni A4, črno-belo z rdečimi točkami.

   Vse stoji na istih številkah kot vzorca (PDF, LibreOffice): koordinate so v točkah
   (pt), list je 595,28 x 841,89 pt in izris ga postavi v SVG z istim viewBox, zato je
   1 enota = 1 pt in se mere ujemajo s papirjem, tudi pri črtah 0,4 pt, ki jih HTML ne
   zna narisati (brskalnik jih zaokroži na slikovno piko). Model vrne samo besedila,
   črte in polnila; izris jih preslika v SVG.

   Dve strani vzorca imata različno merilo (Excel jih je prilagodil širini lista):
   skupine imajo pisavo 8,39 pt, žrebi 6,4 pt. Obe merili sta tu ohranjeni. */
import type { MrezaDto, SkupinaDto, SvZrebDto, TekmaDto } from '../api/tipi'
import { imePrijave, imeUdelezenca } from '../api/tipi'
import { OZNAKE_IZIDA, sklopiZrebSkupin, type TiskCelica, type TiskKolo, type TiskSkupina } from './zrebSkupin'

export const LIST_SVR = { sirina: 595.28, visina: 841.89 } as const

/* ---------- Oblika izhoda ---------- */

export interface SvrBesedilo {
  x: number
  /* Črta pisave (baseline), ne vrh besedila. */
  y: number
  besedilo: string
  velikost: number
  krepko?: boolean
  rdece?: boolean
  sidro?: 'start' | 'middle' | 'end'
}

export interface SvrCrta {
  x1: number
  y1: number
  x2: number
  y2: number
  debelina: number
}

export interface SvrPolnilo {
  x: number
  y: number
  sirina: number
  visina: number
}

export interface ListSvr {
  kljuc: string
  vrsta: 'svr'
  usmeritev: 'pokoncni'
  oznaka: string
  del: 'skupine' | 'mreza'
  besedila: SvrBesedilo[]
  crte: SvrCrta[]
  polnila: SvrPolnilo[]
  /* Pomanjšanje celotnega lista (1 = kot vzorec): potrebno samo, kadar se pod vsebino
     doda noga s pokrovitelji ali ko je žreb širši od lista. */
  merilo: number
}

/* ---------- Širina besedila ----------
   Imena iz tabele ali žreba se ne smejo prelivati v sosednji stolpec. Pisava je Arial
   (Liberation Sans ima enake širine): širine znakov v tisočinkah višine pisave, krepko
   je za ~5 % širše (številke ne). Zadošča za odločitev, kdaj ime zmanjšati. */

const SIRINE_ZNAKOV: Record<string, number> = {
  ' ': 278, '!': 278, '(': 333, ')': 333, ',': 278, '-': 333, '.': 278, '/': 278, ':': 278,
  '0': 556, '1': 556, '2': 556, '3': 556, '4': 556, '5': 556, '6': 556, '7': 556, '8': 556, '9': 556,
  a: 556, b: 556, c: 500, d: 556, e: 556, f: 278, g: 556, h: 556, i: 222, j: 222, k: 500, l: 222, m: 833,
  n: 556, o: 556, p: 556, q: 556, r: 333, s: 500, t: 278, u: 556, v: 500, w: 722, x: 500, y: 500, z: 500,
  A: 667, B: 667, C: 722, D: 722, E: 667, F: 611, G: 778, H: 722, I: 278, J: 500, K: 667, L: 556, M: 833,
  N: 722, O: 778, P: 667, Q: 778, R: 722, S: 667, T: 611, U: 722, V: 667, W: 944, X: 667, Y: 667, Z: 611,
  'đ': 556, 'Đ': 722,
}

export function sirinaBesedila(besedilo: string, velikost: number, krepko = false): number {
  let vsota = 0
  for (const znak of besedilo) {
    const osnova = znak in SIRINE_ZNAKOV ? znak : znak.normalize('NFD').charAt(0)
    const sirina = (SIRINE_ZNAKOV[osnova] ?? 556) / 1000
    vsota += krepko && !/[\d ]/.test(znak) ? sirina * 1.05 : sirina
  }
  return vsota * velikost
}

/* Velikost, pri kateri besedilo gre v prostor; najmanj `najmanjse` osnovne velikosti
   (manjše od tega se ne bere, zato se dolgo ime raje prelije, kot da izgine). */
function vProstor(besedilo: string, velikost: number, prostor: number, krepko = false, najmanjse = 0.66): number {
  const sirina = sirinaBesedila(besedilo, velikost, krepko)
  if (sirina <= prostor) return velikost
  return Math.max(velikost * najmanjse, (velikost * prostor) / sirina)
}

const r2 = (x: number) => Math.round(x * 100) / 100

/* ---------- Oznake ---------- */

const RIMSKE = ['', 'I.', 'II.', 'III.', 'IV.', 'V.', 'VI.', 'VII.', 'VIII.', 'IX.']

/* »I. SKUPINA« je nivo SV regije (v kodi `nivo`); do devetega ga razpozna vsak, naprej
   ostane arabska številka. */
export function naslovNivoja(nivo: number): string {
  return `${RIMSKE[nivo] ?? `${nivo}.`} SKUPINA`
}

/* »1A« je skupina A prvega nivoja; vzorec jo piše »1/A skupina«. */
function naslovSkupine(oznaka: string): string {
  const razclenjena = /^(\d+)([A-Za-z]+)$/.exec(oznaka)
  return razclenjena ? `${razclenjena[1]}/${razclenjena[2]} skupina` : `Skupina ${oznaka}`
}

export function jeSvRegija(mreza: MrezaDto): boolean {
  return mreza.dogodek.sistemTekmovanja === 'SV_REGIJA' && mreza.svRegija !== null
}

/* ---------- Skupine ---------- */

/* Mere iz strani 1 vzorca (1. OT SV regije), v pt. Vrstice tabele so v vzorcu 22,6 oz.
   25,4 (Excelova višina vrstice je bila drugačna pri prvi in drugi skupini lista); vzeta
   je sredina, da je celoten list enako visok kot v vzorcu. */
const SK = {
  naslovX: 83.56,
  naslovY: 62.9,
  podnaslovY: 91.9,
  naslovVelikost: 14.01,
  prvaSkupinaY: 122.6,
  imeSkupineX: 79.81,
  imeSkupineVelikost: 9.8,
  /* Od vrstice z imenom skupine do zgornje črte tabele. */
  doTabele: 12.8,
  levo: 81.63,
  sirinaRanga: 14.56,
  sirinaImena: 123.44,
  sirinaCelice: 23.33,
  sirinaSkorja: 58.42,
  sirinaTock: 25.67,
  sirinaMesta: 25.68,
  visinaGlave: 14,
  visinaVrstice: 24,
  debelinaCrt: 0.53,
  pisava: 8.39,
  pisavaIzida: 9.8,
  pisavaVsote: 11.19,
  /* Siva polja na diagonali: malo znotraj črt, kot v vzorcu. */
  vzorecVznotraj: 0.27,
  /* Razpored: od spodnje črte tabele do naslova prvega kola, od naslova do prve tekme,
     med tekmami, od zadnje tekme do naslova naslednjega kola. */
  doKola: 18,
  doTekme: 23,
  medTekmama: 11.9,
  doNaslednjegaKola: 23.8,
  /* Od zadnje vrstice skupine do imena naslednje. */
  medSkupinama: 38.5,
  /* Najnižja črta pisave zadnje tekme na listu. */
  najnizjaVrstica: 776,
  /* Stolpci razporeda: vzporedno s skorom, točkami in mestom v tabeli. */
  imeLevoX: 97.86,
  dvopicjeX: 221.3,
  imeDesnoX: 230.8,
  kolo: { x: 85.72 },
  setiSredinaX: [358.8, 384.3, 410],
  tockeX: 441.83,
  pisavaTockPoNizih: 7.2,
} as const

interface Izhod {
  besedila: SvrBesedilo[]
  crte: SvrCrta[]
  polnila: SvrPolnilo[]
}

function novIzhod(): Izhod {
  return { besedila: [], crte: [], polnila: [] }
}

interface SvrClan {
  /* »Ime Priimek (Klub)«; kluba ni pri ekipi in pri igralcu brez kluba. */
  napis: string
}

/* Kar skupina kaže: križna tabela in razpored; vse že prešteto. */
interface SvrSkupina {
  naslov: string
  clani: SvrClan[]
  celice: (TiskCelica | null)[][]
  izkupicki: TiskSkupina['izkupicki']
  kola: TiskKolo[]
}

/* Vzorec piše klub kot kraj ali znamko (»Savinja«, »Tempo«), brez predpone »NTK«/»NTD«;
   kar po odstranitvi ne ostane vsaj tri znake, se ne skrajša. */
function klubSvr(klub: string): string {
  const kratko = klub.replace(/^(NTK|NTD)[\s-]+/i, '').trim()
  return kratko.length >= 3 ? kratko : klub
}

function napisClana(ime: string, klub: string | null): string {
  return klub ? `${ime} (${klubSvr(klub)})` : ime
}

function pripraviSvrSkupino(skupina: TiskSkupina, dto: SkupinaDto | undefined): SvrSkupina {
  return {
    naslov: dto ? naslovSkupine(dto.oznaka) : skupina.naslov,
    clani: skupina.clani.map((c) => ({ napis: napisClana(c.ime, c.klub) })),
    celice: skupina.izidi,
    izkupicki: skupina.izkupicki,
    kola: skupina.kola,
  }
}

/* Višina skupine do zadnje vrstice razporeda (črta pisave zadnje tekme), od vrstice z
   imenom skupine. */
function visinaSkupine(skupina: SvrSkupina): number {
  const tabela = SK.doTabele + SK.visinaGlave + skupina.clani.length * SK.visinaVrstice
  if (skupina.kola.length === 0) return tabela
  const kola = skupina.kola.reduce(
    (vsota, kolo) => vsota + SK.doTekme + Math.max(0, kolo.tekme.length - 1) * SK.medTekmama,
    0,
  )
  return tabela + SK.doKola + kola + (skupina.kola.length - 1) * SK.doNaslednjegaKola
}

/* Žrebi in tabele so v vzorcu v LiberationSans (Arial); izris poda družino. */
function narisiSkupino(skupina: SvrSkupina, T: number, sTockami: boolean, izhod: Izhod): void {
  const n = skupina.clani.length
  const zgoraj = T + SK.doTabele
  const spodaj = zgoraj + SK.visinaGlave + n * SK.visinaVrstice

  const x = [SK.levo, SK.levo + SK.sirinaRanga]
  x.push(x[1] + SK.sirinaImena)
  for (let i = 0; i < n; i++) x.push(x[x.length - 1] + SK.sirinaCelice)
  x.push(x[x.length - 1] + SK.sirinaSkorja)
  x.push(x[x.length - 1] + SK.sirinaTock)
  x.push(x[x.length - 1] + SK.sirinaMesta)
  const levo = x[0]
  const desno = x[x.length - 1]
  const celicaOd = x[2]
  const skorOd = x[2 + n]
  const tockeOd = x[3 + n]
  const mestoOd = x[4 + n]

  izhod.besedila.push({
    x: SK.imeSkupineX, y: T, besedilo: skupina.naslov, velikost: SK.imeSkupineVelikost, krepko: true,
  })

  /* Črte tabele. */
  const crta = (x1: number, y1: number, x2: number, y2: number) =>
    izhod.crte.push({ x1: r2(x1), y1: r2(y1), x2: r2(x2), y2: r2(y2), debelina: SK.debelinaCrt })
  for (let i = 0; i <= n + 1; i++) {
    const y = i === 0 ? zgoraj : zgoraj + SK.visinaGlave + (i - 1) * SK.visinaVrstice
    crta(levo, y, desno, y)
  }
  crta(levo, spodaj, desno, spodaj)
  for (const xs of x) crta(xs, zgoraj, xs, spodaj)

  /* Glava. */
  const glavaY = zgoraj + 10.3
  const glava = (besedilo: string, od: number, doX: number) =>
    izhod.besedila.push({
      x: r2((od + doX) / 2), y: r2(glavaY), besedilo, velikost: SK.pisava, sidro: 'middle',
    })
  glava('igralec', x[1], x[2])
  for (let i = 0; i < n; i++) glava(`${i + 1}.`, x[2 + i], x[3 + i])
  glava('skor', skorOd, tockeOd)
  glava('točke', tockeOd, mestoOd)
  glava('mesto', mestoOd, desno)

  /* Vrstice igralcev. */
  skupina.clani.forEach((clan, i) => {
    const vrhVrstice = zgoraj + SK.visinaGlave + i * SK.visinaVrstice
    const dnoVrstice = vrhVrstice + SK.visinaVrstice
    const sredina = vrhVrstice + SK.visinaVrstice / 2

    izhod.besedila.push({
      x: r2((x[0] + x[1]) / 2), y: r2(sredina + 2.75), besedilo: `${i + 1}.`, velikost: SK.pisava, sidro: 'middle',
    })
    const prostorImena = SK.sirinaImena - 4
    izhod.besedila.push({
      x: r2(x[1] + 1.67),
      y: r2(vrhVrstice + 8),
      besedilo: clan.napis,
      velikost: r2(vProstor(clan.napis, SK.pisava, prostorImena)),
    })

    /* Celice: siva diagonala, sicer rdeče točke zgoraj in nizi spodaj. */
    skupina.celice[i]?.forEach((celica, j) => {
      const levoCelice = celicaOd + j * SK.sirinaCelice
      if (i === j) {
        izhod.polnila.push({
          x: r2(levoCelice + SK.vzorecVznotraj),
          y: r2(vrhVrstice + SK.vzorecVznotraj),
          sirina: r2(SK.sirinaCelice - 2 * SK.vzorecVznotraj),
          visina: r2(SK.visinaVrstice - 2 * SK.vzorecVznotraj),
        })
        return
      }
      if (!celica) return
      const sredinaX = r2(levoCelice + SK.sirinaCelice / 2)
      izhod.besedila.push({
        x: sredinaX, y: r2(vrhVrstice + 8.4), besedilo: celica.zmaga ? '2' : '0', velikost: SK.pisava,
        rdece: true, sidro: 'middle',
      })
      const [a, b] = celica.nizi.split('/')
      /* Tekma, ki ni bila odigrana do konca, nosi namesto nizov oznako (»b. b.«). */
      const izid = celica.oznaka && celica.oznaka !== OZNAKE_IZIDA.PREDAJA ? celica.oznaka : `${a} : ${b}`
      izhod.besedila.push({
        x: sredinaX, y: r2(dnoVrstice - 2.3), besedilo: izid, velikost: SK.pisavaIzida, krepko: true, sidro: 'middle',
      })
    })

    /* Seštevek: samo za odigrano skupino (kot na listkih). */
    const izkupicek = skupina.izkupicki[i]
    if (izkupicek) {
      const [za, proti] = izkupicek.nizi.split('/')
      const sredinaSkorja = (skorOd + tockeOd) / 2
      const osnova = r2(sredina + 3.75)
      izhod.besedila.push(
        { x: r2(sredinaSkorja - 13), y: osnova, besedilo: za, velikost: SK.pisavaVsote, sidro: 'end' },
        { x: r2(sredinaSkorja + 0.2), y: osnova, besedilo: ':', velikost: SK.pisavaVsote, sidro: 'middle' },
        { x: r2(sredinaSkorja + 14.2), y: osnova, besedilo: proti, velikost: SK.pisavaVsote, sidro: 'start' },
        {
          x: r2((tockeOd + mestoOd) / 2), y: osnova, besedilo: String(izkupicek.zmage * 2),
          velikost: SK.pisavaVsote, krepko: true, rdece: true, sidro: 'middle',
        },
      )
      if (izkupicek.mesto !== null) {
        izhod.besedila.push({
          x: r2((mestoOd + desno) / 2), y: osnova, besedilo: String(izkupicek.mesto),
          velikost: SK.pisavaVsote, krepko: true, sidro: 'middle',
        })
      }
    }
  })

  /* Razpored po kolih. */
  let y = spodaj + SK.doKola
  skupina.kola.forEach((kolo, k) => {
    const pari = kolo.tekme.map((t) => t.par.join('-')).join(', ')
    izhod.besedila.push({
      x: SK.kolo.x, y: r2(y), besedilo: `${k + 1}. Kolo (${pari})`, velikost: SK.pisava, krepko: true,
    })
    if (k === 0) {
      izhod.besedila.push(
        { x: SK.setiSredinaX[1], y: r2(y), besedilo: 'Seti', velikost: SK.pisava, krepko: true, sidro: 'middle' },
        { x: SK.tockeX, y: r2(y), besedilo: 'Točke', velikost: SK.pisava, krepko: true },
      )
    }
    y += SK.doTekme
    kolo.tekme.forEach((tekma, t) => {
      if (t > 0) y += SK.medTekmama
      const prvi = skupina.clani[tekma.par[0] - 1]?.napis ?? tekma.ime1
      const drugi = skupina.clani[tekma.par[1] - 1]?.napis ?? tekma.ime2
      izhod.besedila.push(
        {
          x: SK.imeLevoX, y: r2(y), besedilo: prvi,
          velikost: r2(vProstor(prvi, SK.pisava, SK.dvopicjeX - SK.imeLevoX - 3)),
        },
        { x: SK.dvopicjeX, y: r2(y), besedilo: ':', velikost: SK.pisava },
        {
          x: SK.imeDesnoX, y: r2(y), besedilo: drugi,
          velikost: r2(vProstor(drugi, SK.pisava, SK.setiSredinaX[0] - 12 - SK.imeDesnoX)),
        },
      )
      if (tekma.izid) {
        const [a, b] = tekma.izid.nizi.split('/')
        izhod.besedila.push(
          { x: SK.setiSredinaX[0], y: r2(y), besedilo: a, velikost: SK.pisava, krepko: true, sidro: 'middle' },
          { x: SK.setiSredinaX[1], y: r2(y), besedilo: ':', velikost: SK.pisava, krepko: true, sidro: 'middle' },
          { x: SK.setiSredinaX[2], y: r2(y), besedilo: b, velikost: SK.pisava, krepko: true, sidro: 'middle' },
        )
        /* Stolpec »Točke« je v vzorcu prazen; tu nosi točke po nizih, kadar so vpisane. */
        if (sTockami && tekma.tocke.length > 0) {
          const tocke = tekma.tocke.join(' ')
          izhod.besedila.push({
            x: SK.tockeX, y: r2(y), besedilo: tocke,
            velikost: r2(vProstor(tocke, SK.pisavaTockPoNizih, LIST_SVR.sirina - 36 - SK.tockeX)),
          })
        }
      }
    })
    y += SK.doNaslednjegaKola
  })
}

/* ---------- Dokument ---------- */

interface Stran {
  izhod: Izhod
  desnoDo: number
  spodajDo: number
}

function novaStran(naslov: string, nivo: number, glava: 'skupine' | 'zreb'): Stran {
  const izhod = novIzhod()
  if (glava === 'skupine') {
    izhod.besedila.push(
      { x: SK.naslovX, y: SK.naslovY, besedilo: naslov, velikost: SK.naslovVelikost, krepko: true },
      { x: SK.naslovX, y: SK.podnaslovY, besedilo: naslovNivoja(nivo), velikost: SK.naslovVelikost, krepko: true },
    )
  } else {
    izhod.besedila.push(
      { x: ZR.naslovX, y: ZR.naslovY, besedilo: naslov, velikost: ZR.naslovVelikost, krepko: true },
      { x: ZR.oznakaX, y: ZR.podnaslovY, besedilo: naslovNivoja(nivo), velikost: ZR.podnaslovVelikost, krepko: true },
    )
  }
  return { izhod, desnoDo: 0, spodajDo: 0 }
}

/* Pomanjšanje, pri katerem vsebina ostane nad nogo in znotraj lista. */
function merilo(stran: Stran, spodnjaMeja: number): number {
  const desnaMeja = LIST_SVR.sirina - 30
  const m = Math.min(1, spodnjaMeja / Math.max(1, stran.spodajDo), desnaMeja / Math.max(1, stran.desnoDo))
  return Math.round(m * 1000) / 1000
}

/* Meje v pt: kje se lahko konča vsebina lista. */
export interface MejeSvr {
  /* Spodnja meja vsebine; pri listu s pokrovitelji višje (noga je daljša). */
  spodaj: number
}

function skupineNivoja(
  mreza: MrezaDto,
  nivo: number,
  skupine: TiskSkupina[],
  naslov: string,
  sTockami: boolean,
  meje: MejeSvr,
): ListSvr[] {
  const stran = (): Stran => novaStran(naslov, nivo, 'skupine')
  const strani: Stran[] = []
  let tekoca = stran()
  let imaBlok = false
  let T = SK.prvaSkupinaY

  for (const skupina of skupine) {
    const svr = pripraviSvrSkupino(skupina, mreza.skupine.find((s) => s.id === skupina.id))
    const visina = visinaSkupine(svr)
    if (imaBlok && T + visina > SK.najnizjaVrstica) {
      strani.push(tekoca)
      tekoca = stran()
      imaBlok = false
      T = SK.prvaSkupinaY
    }
    narisiSkupino(svr, T, sTockami, tekoca.izhod)
    tekoca.desnoDo = Math.max(tekoca.desnoDo, SK.levo + SK.sirinaRanga + SK.sirinaImena + svr.clani.length * SK.sirinaCelice
      + SK.sirinaSkorja + SK.sirinaTock + SK.sirinaMesta + 4)
    tekoca.spodajDo = Math.max(tekoca.spodajDo, T + visina + 6)
    imaBlok = true
    T += visina + SK.medSkupinama
  }
  if (imaBlok) strani.push(tekoca)

  return strani.map((s, i) => ({
    kljuc: `svr-skupine-${nivo}-${i}`,
    vrsta: 'svr',
    usmeritev: 'pokoncni',
    oznaka: `${naslovNivoja(nivo)} · skupine`,
    del: 'skupine',
    ...s.izhod,
    merilo: merilo(s, meje.spodaj),
  }))
}

/* ---------- Žrebi ----------
   Mere iz strani 3 vzorca (žreb 1. skupine, pisava 6,4 pt). Žreb za vsa mesta je več
   poti: glavna (1.-8.), poraženci vsakega kola na svoji (3.-4., 5.-8., 7.-8.). Vsaka
   pot je navaden izločilni žreb: igralci na črtah prvega stolpca, zmagovalec vsakega
   para na črti naslednjega, z izidom za imenom. Poraženec stoji v stolpcu kola, ki ga
   je izgubil (3. mesto torej v drugem stolpcu, 5. mesto v prvem). */

const ZR = {
  naslovX: 122.14,
  naslovY: 71.2,
  naslovVelikost: 19.09,
  oznakaX: 94.45,
  podnaslovY: 98.7,
  podnaslovVelikost: 13.81,
  /* Leva črta prvega stolpca; stolpec je širok toliko, črta pa malo več, da se stikata. */
  x0: 120.88,
  stolpec: 72.24,
  dolzinaCrte: 73,
  debelina: 0.4,
  /* Razmik črt prvega stolpca. */
  vrstica: 18,
  prvaCrta: 128.05,
  pisava: 6.4,
  /* Pisava stoji tolikšno nad črto. */
  nadCrto: 2.35,
  odmikImena: 1.26,
  sredinaStolpca: 36.6,
  /* Napis »N. mesto«: v stolpcu desno od zadnje črte, tolikšno nad njo. */
  mestoNadCrto: 11.3,
  /* Razmik med potmi: za glavno potjo je tak, med ostalimi in med žrebi večji. */
  poGlavni: 18,
  medPotmi: 27,
  medZrebi: 27,
  /* Spodnja meja prve črte poti; pot, ki jo preseže, gre na naslednji list. */
  najnizjaCrta: 772,
} as const

type Igralec = { id: number; ime: string }
type Mesto = { vrsta: 'igralec'; igralec: Igralec } | { vrsta: 'bye' } | { vrsta: 'neznano' }
const BYE: Mesto = { vrsta: 'bye' }
const NEZNANO: Mesto = { vrsta: 'neznano' }

interface Izid {
  zmagovalec: Mesto
  porazenec: Mesto
  /* »3:1« z vidika zmagovalca; prazno pri prostem prehodu in nedokončani tekmi. */
  izid: string | null
}

function kljucPara(a: number, b: number): string {
  return a < b ? `${a}-${b}` : `${b}-${a}`
}

function izidTekme(tekma: TekmaDto, zmagovalecJePrvi: boolean): string {
  const oznaka = tekma.izidTip ? OZNAKE_IZIDA[tekma.izidTip] : undefined
  if (oznaka && tekma.izidTip !== 'PREDAJA') return oznaka
  const [za, proti] = zmagovalecJePrvi
    ? [tekma.dobljeniNizi1, tekma.dobljeniNizi2]
    : [tekma.dobljeniNizi2, tekma.dobljeniNizi1]
  return `${za}:${proti}`
}

/* Par na črtah: dva igralca (izid iz tekme), igralec in prost prehod (igralec gre naprej brez
   izida), ali še neznan nasprotnik (tekma še ni odločena). */
function odigraj(a: Mesto, b: Mesto, poParih: Map<string, TekmaDto>): Izid {
  if (a.vrsta === 'igralec' && b.vrsta === 'igralec') {
    const tekma = poParih.get(kljucPara(a.igralec.id, b.igralec.id))
    if (!tekma || tekma.status !== 'KONCANA' || tekma.idZmagovalcaPrijave === null) {
      return { zmagovalec: NEZNANO, porazenec: NEZNANO, izid: null }
    }
    const prviZmaga = tekma.idZmagovalcaPrijave === a.igralec.id
    const prvi = tekma.udelezenec1?.idPrijave === a.igralec.id
    /* Dobljeni nizi so z vidika udeleženca 1 tekme; a je lahko tudi njen drugi. */
    return {
      zmagovalec: prviZmaga ? a : b,
      porazenec: prviZmaga ? b : a,
      izid: izidTekme(tekma, prviZmaga === prvi),
    }
  }
  if (a.vrsta === 'bye' && b.vrsta === 'bye') return { zmagovalec: BYE, porazenec: BYE, izid: null }
  if (a.vrsta === 'bye') return { zmagovalec: b, porazenec: BYE, izid: null }
  if (b.vrsta === 'bye') return { zmagovalec: a, porazenec: BYE, izid: null }
  return { zmagovalec: NEZNANO, porazenec: NEZNANO, izid: null }
}

interface Pot {
  /* Prvo mesto, ki ga pot odloča (glavna pot žreba: prvo mesto žreba). */
  od: number
  /* Stolpec prve črte: poraženec stoji v stolpcu kola, ki ga je izgubil. */
  stolpec0: number
  /* vhodi[r]: mesta na črtah stolpca stolpec0 + r; zadnji je zmagovalec poti. */
  vhodi: Mesto[][]
  /* izidi[r][j]: izid para, iz katerega je nastalo vhodi[r + 1][j]. */
  izidi: (string | null)[][]
}

/* Vse poti žreba: glavna in iz poražencev vsakega kola rekurzivno naslednje. Poraženec
   kola r je v poti zaporedja svojega para, prost prehod pa pusti prazno mesto - zato se
   tolažilne poti ujemajo z žrebom do mesta natančno, tudi kadar je v žrebu manj igralcev. */
function poti(zreb: SvZrebDto, glavna: Mesto[], poParih: Map<string, TekmaDto>): Pot[] {
  const vse: Pot[] = []
  const dodaj = (od: number, stolpec0: number, listi: Mesto[]) => {
    const vhodi: Mesto[][] = [listi]
    const izidi: (string | null)[][] = []
    const porazenci: Mesto[][] = []
    while (vhodi[vhodi.length - 1].length > 1) {
      const trenutni = vhodi[vhodi.length - 1]
      const naprej: Mesto[] = []
      const izidiKola: (string | null)[] = []
      const poraz: Mesto[] = []
      for (let j = 0; j < trenutni.length; j += 2) {
        const par = odigraj(trenutni[j], trenutni[j + 1], poParih)
        naprej.push(par.zmagovalec)
        poraz.push(par.porazenec)
        izidiKola.push(par.izid)
      }
      vhodi.push(naprej)
      izidi.push(izidiKola)
      porazenci.push(poraz)
    }
    if (listi.every((m) => m.vrsta === 'bye')) return
    vse.push({ od, stolpec0, vhodi, izidi })
    porazenci.forEach((poraz, r) => {
      const velikost = listi.length / 2 ** (r + 1)
      if (velikost >= 2) dodaj(od + velikost, stolpec0 + r, poraz)
    })
  }
  dodaj(zreb.prvoMesto, 0, glavna)
  return vse.sort((a, b) => a.od - b.od)
}

function potenca2(n: number): number {
  let p = 2
  while (p < n) p *= 2
  return p
}

function besediloMesta(mesto: Mesto, izid: string | null): string {
  if (mesto.vrsta === 'bye') return 'bye'
  if (mesto.vrsta === 'neznano') return ''
  return izid ? `${mesto.igralec.ime} (${izid})` : mesto.igralec.ime
}

/* Črta pot do zadnje vrstice; vrne y zadnje črte prvega stolpca in desni rob. */
function narisiPot(
  pot: Pot,
  y0: number,
  oznake: (string | null)[] | null,
  izhod: Izhod,
): { zadnjaCrta: number; desnoDo: number } {
  const k = pot.vhodi.length - 1
  const razmik = ZR.vrstica * 2 ** pot.stolpec0
  const ys: number[][] = [pot.vhodi[0].map((_, i) => y0 + i * razmik)]
  for (let r = 0; r < k; r++) {
    const prejsnji = ys[r]
    const naprej: number[] = []
    for (let j = 0; j < prejsnji.length; j += 2) naprej.push((prejsnji[j] + prejsnji[j + 1]) / 2)
    ys.push(naprej)
  }
  const xStolpca = (stolpec: number) => ZR.x0 + stolpec * ZR.stolpec

  for (let r = 0; r <= k; r++) {
    const stolpec = pot.stolpec0 + r
    pot.vhodi[r].forEach((mesto, j) => {
      const y = ys[r][j]
      izhod.crte.push({
        x1: r2(xStolpca(stolpec)), y1: r2(y), x2: r2(xStolpca(stolpec) + ZR.dolzinaCrte), y2: r2(y),
        debelina: ZR.debelina,
      })
      const besedilo = besediloMesta(mesto, r > 0 ? (pot.izidi[r - 1][j] ?? null) : null)
      if (besedilo) {
        const prostor = ZR.dolzinaCrte - 4
        const velikost = r2(vProstor(besedilo, ZR.pisava, prostor, false, 0.8))
        izhod.besedila.push(
          stolpec === 0
            ? { x: r2(xStolpca(0) + ZR.odmikImena), y: r2(y - ZR.nadCrto), besedilo, velikost }
            : {
                x: r2(xStolpca(stolpec) + ZR.sredinaStolpca), y: r2(y - ZR.nadCrto), besedilo, velikost,
                sidro: 'middle',
              },
        )
      }
      /* Oznaka, od kod igralec prihaja (»A1«), samo pri prvem stolpcu glavne poti. */
      if (r === 0 && oznake?.[j]) {
        izhod.besedila.push({ x: ZR.oznakaX, y: r2(y - ZR.nadCrto), besedilo: oznake[j] as string, velikost: ZR.pisava })
      }
    })
  }

  /* Navpičnica, ki veže par, stoji na desnem robu črt stolpca. */
  for (let r = 0; r < k; r++) {
    const x = xStolpca(pot.stolpec0 + r + 1) + 0.28
    for (let j = 0; j < ys[r].length; j += 2) {
      izhod.crte.push({ x1: r2(x), y1: r2(ys[r][j]), x2: r2(x), y2: r2(ys[r][j + 1]), debelina: ZR.debelina })
    }
  }

  const stolpecMesta = pot.stolpec0 + k + 1
  izhod.besedila.push({
    x: r2(xStolpca(stolpecMesta) + ZR.sredinaStolpca + 0.9),
    y: r2(ys[k][0] - ZR.mestoNadCrto),
    besedilo: `${pot.od}. mesto`,
    velikost: ZR.pisava,
    sidro: 'middle',
  })

  return {
    zadnjaCrta: ys[0][ys[0].length - 1],
    desnoDo: xStolpca(stolpecMesta) + ZR.sredinaStolpca + 22,
  }
}

function zrebiNivoja(
  mreza: MrezaDto,
  nivo: number,
  zrebi: SvZrebDto[],
  naslov: string,
  meje: MejeSvr,
): ListSvr[] {
  const imena = new Map(mreza.prijave.map((p) => [p.id, imePrijave(p)]))
  for (const t of mreza.tekme) {
    for (const u of [t.udelezenec1, t.udelezenec2]) {
      if (u && !imena.has(u.idPrijave)) imena.set(u.idPrijave, imeUdelezenca(u) ?? '')
    }
  }
  /* »A1«: črka skupine in mesto v njej, po katerem je igralec prišel v žreb. */
  const izvor = new Map<number, string>()
  for (const skupina of mreza.skupine.filter((s) => s.nivo === nivo)) {
    const crka = skupina.oznaka.replace(/^\d+/, '')
    for (const vrstica of skupina.lestvica) izvor.set(vrstica.idPrijave, `${crka}${vrstica.mesto ?? ''}`)
  }

  const strani: Stran[] = []
  let stran: Stran | null = null
  let prejsnjaCrta = 0
  let prejsnjaJeGlavna = false

  for (const zreb of zrebi) {
    if (zreb.id === null || zreb.razpored.length === 0) continue
    const tekme = mreza.tekme.filter((t) => t.idZreb === zreb.id)
    if (tekme.length === 0) continue
    const poParih = new Map<string, TekmaDto>()
    for (const t of tekme) {
      if (t.udelezenec1 && t.udelezenec2) poParih.set(kljucPara(t.udelezenec1.idPrijave, t.udelezenec2.idPrijave), t)
    }
    const velikost = potenca2(zreb.razpored.length)
    const glavna: Mesto[] = Array.from({ length: velikost }, (_, i): Mesto => {
      const id = zreb.razpored[i] ?? null
      return id === null ? BYE : { vrsta: 'igralec', igralec: { id, ime: imena.get(id) ?? '' } }
    })
    const oznake = glavna.map((m) => (m.vrsta === 'igralec' ? (izvor.get(m.igralec.id) ?? null) : null))

    let prvaNaZrebu = true
    for (const pot of poti(zreb, glavna, poParih)) {
      const glavnaPot = pot.od === zreb.prvoMesto
      const visina = (pot.vhodi[0].length - 1) * ZR.vrstica * 2 ** pot.stolpec0
      /* Za glavno potjo je razmik manjši, med ostalimi in med žrebi večji; pot, ki ne gre več
         na list, začne nov list z istim naslovom. */
      const razmik = prvaNaZrebu ? ZR.medZrebi : prejsnjaJeGlavna ? ZR.poGlavni : ZR.medPotmi
      let y0 = stran === null ? ZR.prvaCrta : prejsnjaCrta + razmik
      if (stran === null || y0 + visina > ZR.najnizjaCrta) {
        stran = novaStran(naslov, nivo, 'zreb')
        strani.push(stran)
        y0 = ZR.prvaCrta
      }
      const { zadnjaCrta, desnoDo } = narisiPot(pot, y0, glavnaPot ? oznake : null, stran.izhod)
      stran.desnoDo = Math.max(stran.desnoDo, desnoDo)
      stran.spodajDo = Math.max(stran.spodajDo, zadnjaCrta + 8)
      prejsnjaCrta = zadnjaCrta
      prejsnjaJeGlavna = glavnaPot
      prvaNaZrebu = false
    }
  }

  return strani.map((s, i) => ({
    kljuc: `svr-zreb-${nivo}-${i}`,
    vrsta: 'svr',
    usmeritev: 'pokoncni',
    oznaka: `${naslovNivoja(nivo)} · žreb`,
    del: 'mreza',
    ...s.izhod,
    merilo: merilo(s, meje.spodaj),
  }))
}

/* ---------- Vstopna točka ---------- */

export interface DeliSvr {
  skupine: boolean
  mreza: boolean
}

/* Listi po nivojih, kot v vzorcu: skupine nivoja, nato njegovi žrebi. Naslov je ime
   turnirja (v vzorcu »1. OT SV Regije«). */
export function listiSvr(
  mreza: MrezaDto,
  deli: DeliSvr,
  naslov: string,
  sTockami: boolean,
  meje: MejeSvr,
): ListSvr[] {
  if (!jeSvRegija(mreza) || !mreza.svRegija) return []
  const sklopi = deli.skupine ? sklopiZrebSkupin(mreza) : []
  return mreza.svRegija.nivoji.flatMap((nivo) => {
    const skupine = sklopi.find((s) => s.kljuc === `nivo-${nivo.nivo}`)?.skupine ?? []
    return [
      ...(deli.skupine ? skupineNivoja(mreza, nivo.nivo, skupine, naslov, sTockami, meje) : []),
      ...(deli.mreza ? zrebiNivoja(mreza, nivo.nivo, nivo.zrebi, naslov, meje) : []),
    ]
  })
}
