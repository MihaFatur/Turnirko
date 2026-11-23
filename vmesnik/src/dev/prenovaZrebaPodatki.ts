/* Stalni vzorčni podatki za posnetke prenove »Popravi skupine«, »Popravi mrežo« in
   »Listki za tisk« (design_handoff_skupine_mreza_listki/prototipi/podatki.js, prepisano
   dobesedno): 48 izmišljenih igralcev, 12 skupin po 4, izločilna mreža 64 s 16 prostimi
   mesti in 72 listkov skupinskega dela. Samo za razvoj (harness /dev/prenova-zreba). */
import type {
  DogodekDto,
  MrezaDto,
  PrijavaDto,
  SkupinaDto,
  TekmaDto,
  Udelezenec,
} from '../api/tipi'

const IMENA: [string, string][] = [
  ['Žiga', 'Kos'], ['Rok', 'Pirc'], ['Urban', 'Turk'], ['Peter', 'Jerman'], ['Grega', 'Perko'], ['Nejc', 'Sever'],
  ['Gašper', 'Pungartnik'], ['Klemen', 'Vidmar'], ['Andrej', 'Mlakar'], ['Dejan', 'Petek'], ['Jure', 'Zupančič'], ['Jan', 'Hočevar'],
  ['Aljaž', 'Tratnik'], ['Simon', 'Drofenik'], ['Luka', 'Golob'], ['Anže', 'Kovačič'], ['Blaž', 'Rozman'], ['David', 'Bizjak'],
  ['Tadej', 'Kocjančič'], ['Matej', 'Podgoršek'], ['Tilen', 'Kovač'], ['Domen', 'Kralj'], ['Marko', 'Lah'], ['Vid', 'Potočnik'],
  ['Žiga', 'Kolar'], ['Rok', 'Rebernik'], ['Urban', 'Fras'], ['Peter', 'Novak'], ['Grega', 'Božič'], ['Nejc', 'Kavčič'],
  ['Gašper', 'Šuštar'], ['Klemen', 'Čeh'], ['Andrej', 'Cerar'], ['Dejan', 'Lesjak'], ['Jure', 'Horvat'], ['Jan', 'Korošec'],
  ['Aljaž', 'Zajc'], ['Simon', 'Breznik'], ['Luka', 'Žnidar'], ['Anže', 'Jelen'], ['Blaž', 'Bračko'], ['David', 'Zupan'],
  ['Tadej', 'Hribar'], ['Matej', 'Oblak'], ['Tilen', 'Medved'], ['Domen', 'Vodopivec'], ['Marko', 'Mavrič'], ['Vid', 'Ribič'],
]
const KLUBI = [
  'NTK Gorica', 'TTK Velenje', 'KNT Radlje', 'ŠD Sežana', 'NTK Celje', 'KNT Kajuh',
  'NTK Ptuj', 'NTK Savinja', 'NTK Ormož', 'NTK Krško', 'NTK Maribor', 'NTK Žalec',
]

export const TURNIR = 'Jesenski turnir NTK Celje'
export const DOGODEK = 'Moški posamezno'
export const ST_SKUPIN = 12

export interface Igralec {
  id: number
  ime: string
  priimek: string
  klub: string
  rating: number
  /* Mesto na jakostni lestvici (1 = najmočnejši). */
  jakost: number
}

export interface MestoVSkupini {
  skupina: number | null
  pozicija: number
}

export const IGRALCI: Igralec[] = IMENA.map(([ime, priimek], i) => ({
  id: i + 1,
  ime,
  priimek,
  klub: '',
  rating: 2186 - i * 15 - (i % 3) * 4,
  jakost: i + 1,
}))

/* Kačasti razpored: boben p (0..3) gre v skupine 1..12 oz. 12..1. */
function kacasto(k = ST_SKUPIN): Record<number, MestoVSkupini> {
  const mesta: Record<number, MestoVSkupini> = {}
  IGRALCI.forEach((p, i) => {
    const boben = Math.floor(i / k)
    const j = i % k
    const skupina = boben % 2 === 0 ? j : k - 1 - j
    mesta[p.id] = boben < 4 ? { skupina, pozicija: boben + 1 } : { skupina: null, pozicija: 0 }
  })
  return mesta
}

/* Trenutne skupine (po žrebu): kačasto + nekaj zamenjav, kot bi jih dal žreb. */
export function trenutneSkupine(): Record<number, MestoVSkupini> {
  const m = kacasto()
  const zamenjaj = (a: number, b: number) => {
    const t = m[a]
    m[a] = m[b]
    m[b] = t
  }
  zamenjaj(14, 17)
  zamenjaj(27, 30)
  zamenjaj(38, 44)
  zamenjaj(20, 22)
  return m
}

/* Klubi so izpeljani iz trenutnih skupin: v vsaki skupini različni, razen ene namerne
   kolizije (skupina 7). */
{
  const m = trenutneSkupine()
  IGRALCI.forEach((p) => {
    const x = m[p.id]
    p.klub = KLUBI[((x.skupina ?? 0) * 5 + x.pozicija * 3) % 12]
  })
  const s7 = IGRALCI.filter((p) => m[p.id].skupina === 6).sort(
    (a, b) => m[a.id].pozicija - m[b.id].pozicija,
  )
  s7[3].klub = s7[1].klub
}

/* Izločilna mreža 64: 16 nosilcev na standardnih mestih, ob njih prosta mesta (null). */
export const MESTA_NOSILCEV = [1, 64, 33, 32, 17, 48, 49, 16, 9, 56, 41, 24, 25, 40, 57, 8]
export function zacetnaMreza(): (number | null)[] {
  const mesta: (number | null | undefined)[] = Array(64).fill(undefined)
  MESTA_NOSILCEV.forEach((s, i) => {
    mesta[s - 1] = IGRALCI[i].id
    mesta[s % 2 === 1 ? s : s - 2] = null
  })
  const ostali = IGRALCI.slice(16).map((p) => p.id)
  /* psevdo-naključen, a stalen vrstni red */
  const red = ostali
    .map((id, i) => ({ id, k: (i * 37 + 11) % 97 }))
    .sort((a, b) => a.k - b.k)
    .map((x) => x.id)
  let n = 0
  for (let i = 0; i < 64; i++) if (mesta[i] === undefined) mesta[i] = red[n++]
  return mesta as (number | null)[]
}

/* Krožni razpored skupine s štirimi (Berger): kolo -> pari pozicij. */
const KOLA_SKUPINE = [
  [[1, 4], [2, 3]],
  [[4, 3], [1, 2]],
  [[2, 4], [3, 1]],
]

/* Skupine kot v odgovoru strežnika (oznaka »1« … »12«, stopnja 1). */
export function skupineDto(): SkupinaDto[] {
  return Array.from({ length: ST_SKUPIN }, (_, g) => ({
    id: 100 + g,
    oznaka: String(g + 1),
    stopnja: 1,
    ime: null,
    prvoMesto: null,
    nivo: 1,
    lestvica: [],
    krogi: [],
  }))
}

/* Prijave po stanju skupin: stNosilca je pozicija v skupini, kot jo izpelje strežnik. */
export function prijaveDto(skupine: Record<number, MestoVSkupini> | null): PrijavaDto[] {
  return IGRALCI.map((p) => {
    const m = skupine ? skupine[p.id] : null
    return {
      id: p.id,
      idIgralca: p.id,
      polnoIme: `${p.ime} ${p.priimek}`,
      ime: p.ime,
      priimek: p.priimek,
      klub: p.klub,
      idIgralca2: null,
      polnoIme2: null,
      ime2: null,
      priimek2: null,
      klub2: null,
      status: 'PRIJAVLJEN',
      stNosilca: p.jakost,
      ratingObZrebu: p.rating,
      ratingObZrebu2: null,
      koncnoMesto: null,
      rating: p.rating,
      rating2: null,
      idSkupina: m && m.skupina !== null ? 100 + m.skupina : null,
      mestoVSkupini: m && m.skupina !== null ? m.pozicija : null,
      idEkipa: null,
      kader: [],
    } as PrijavaDto
  })
}

function udelezenecDto(p: Igralec): Udelezenec {
  return {
    idPrijave: p.id,
    polnoIme: `${p.ime} ${p.priimek}`,
    klub: p.klub,
    polnoIme2: null,
    klub2: null,
    priimek: p.priimek,
    priimek2: null,
  }
}

/* Mreža dogodka z 72 listki skupinskega dela (12 skupin × 3 kola × 2 tekmi).
   natisnjeno: »nic« ali »delno« (1. kolo in 2. kolo skupin 1–4 že natisnjena). */
export function mrezaListkov(natisnjeno: 'nic' | 'delno'): MrezaDto {
  const m = trenutneSkupine()
  const poSkupini: Igralec[][] = Array.from({ length: ST_SKUPIN }, () => [])
  IGRALCI.forEach((p) => {
    const x = m[p.id]
    if (x.skupina !== null) poSkupini[x.skupina][x.pozicija - 1] = p
  })
  const danes = new Date()
  const cas = (ura: string) => {
    const dd = (n: number) => String(n).padStart(2, '0')
    return `${danes.getFullYear()}-${dd(danes.getMonth() + 1)}-${dd(danes.getDate())}T${ura}:00`
  }
  const tekme: TekmaDto[] = []
  let id = 4101
  poSkupini.forEach((clani, g) => {
    KOLA_SKUPINE.forEach((pari, k) => {
      pari.forEach(([a, b], t) => {
        const natis = natisnjeno === 'delno' && (k === 0 || (k === 1 && g < 4))
        tekme.push({
          id: id++,
          faza: 'SKUPINA',
          idSkupina: 100 + g,
          kolo: k + 1,
          pozicija: t + 1,
          status: 'PRIPRAVLJENA',
          izidTip: null,
          steviloNizov: 5,
          udelezenec1: udelezenecDto(clani[a - 1]),
          udelezenec2: udelezenecDto(clani[b - 1]),
          dobljeniNizi1: 0,
          dobljeniNizi2: 0,
          nizi: [],
          idZmagovalcaPrijave: null,
          miza: ((g * 2 + t) % 16) + 1,
          spremembaElo1: null,
          spremembaElo2: null,
          ratingPred1: null,
          ratingPred2: null,
          idSrecanje: null,
          idPrenesena: null,
          idZreb: null,
          razponOd: null,
          razponDo: null,
          idIzvorZmagovalca1: null,
          idIzvorZmagovalca2: null,
          listekNatisnjenOb: natis ? cas(k === 0 ? '09:12' : '10:05') : null,
        })
      })
    })
  })
  return {
    dogodek: {
      id: 1,
      idTurnir: 1,
      ime: DOGODEK,
      disciplina: 'POSAMEZNO',
      spolKategorija: 'MOSKI',
      starostnaKategorija: null,
      sistemTekmovanja: 'SKUPINE_IZLOCILNI',
      privzetoSteviloNizov: 5,
      steviloSkupin: ST_SKUPIN,
      velikostSkupine: 4,
      status: 'V_TEKU',
      dejanskiStatus: 'V_TEKU',
      steviloPrijav: IGRALCI.length,
      vir: null,
    } as unknown as DogodekDto,
    prijave: prijaveDto(m),
    tekme,
    skupine: skupineDto(),
    lestvica: [],
    krogi: [],
    izbor: null,
    svRegija: null,
  }
}
