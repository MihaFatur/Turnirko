/* Testni podatki in pravila za posnetke strani Mize (dev/MizePosnetki.tsx).
   Prepis prototip/mize-podatki.js iz predaje design_handoff_mize_tloris: 20 miz, miza 10
   izklopljena, ekipno srečanje na mizah 11 + 12, 23 pripravljenih tekem, ura strežnika
   10:42, ista imena in minute. Stanje je navaden objekt; dejanja vrnejo novo stanje.
   MizeTurnirjaDto se sestavi iz stanja (vDto), tako kot ga sestavi strežnik. */
import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, OsebaMizeDto, TekmaDto, TekmaMizeDto, Udelezenec } from '../api/tipi'

export interface Tk {
  id: number
  a: string
  b: string
  kat: string
  del: string
  razlogi: string[]
  ekipno: boolean
}

interface TekmaNaMizi extends Tk {
  ob: number
  sodnik: string | null
  miza: number
  miza2: number | null
}

interface MizaStanje {
  st: number
  izklopljena: boolean
  tekma: TekmaNaMizi | null
  prostaOd: number | null
  cakajociSodnik: string | null
}

export interface Klic {
  st: number
  miza2: number | null
  imena: string
  opis: string
  sodnik: string | null
  ob: number
}

export interface Stanje {
  imeTurnirja: string
  steviloMiz: number | null
  porazenecSodi: boolean
  pocitekMin: number
  /* Minute od polnoči (10:42 = 642). */
  ura: number
  mize: MizaStanje[]
  vrsta: Tk[]
  rezerva: Tk[]
  klici: Klic[]
  /* Ročni vrstni red vrste (id-ji tekem); prazen ali brez = samodejni. */
  rocniRed?: number[]
}

const K = (id: number, a: string, b: string, kat: string, del: string, razlogi: string[] = [], ekipno = false): Tk => ({
  id, a, b, kat, del, razlogi, ekipno,
})

export const URA0 = 642 // 10:42

type VrstaIgre = [number, Tk, number, string | null, number?]

const IGRA: VrstaIgre[] = [
  [1, K(101, 'Nejc Vrhovnik', 'Tim Zupan', 'Člani', 'Skupina 3'), 12, 'Rok Golob'],
  [2, K(102, 'Matic Potočnik', 'Žan Kos', 'Člani', 'Skupina 1'), 21, 'Gal Mlakar'],
  [4, K(103, 'Sara Bajc', 'Eva Pirc', 'Članice', 'Skupina A'), 8, 'Nika Leban'],
  [5, K(104, 'Erik Kavčič', 'Maks Petek', 'Kadeti', 'Skupina 2'), 34, 'Vid Rozman'],
  [6, K(105, 'Urban Hribar', 'Tilen Žagar', 'Člani', 'Skupina 2'), 15, 'Jan Kralj'],
  [8, K(106, 'Filip Bizjak', 'Lovro Medved', 'Mladinci', '1/8 finala'), 5, 'Žiga Kuhar'],
  [9, K(107, 'Domen Sever', 'Blaž Jereb', 'Člani', 'Skupina 3'), 27, null],
  [11, K(108, 'NTK Savinja', 'ŠD Ptuj', 'Ekipe', 'Skupina B', [], true), 41, null, 12],
  [13, K(109, 'Zala Furlan', 'Lara Hočevar', 'Članice', 'Skupina B'), 18, 'Maja Klemenčič'],
  [14, K(110, 'Kokalj / Logar', 'Rebec / Fras', 'Dvojice', 'četrtfinale'), 11, null],
  [16, K(111, 'Leon Majcen', 'Oskar Rems', 'Kadeti', 'Skupina 3'), 3, 'Tadej Mihelič'],
  [17, K(112, 'Aleš Vrečko', 'Martin Zorko', 'Člani', 'Skupina 4'), 23, 'Peter Čuk'],
  [19, K(113, 'Matej Hren', 'Rok Ferk', 'Mladinci', '1/8 finala'), 9, null],
  [20, K(114, 'Jaka Ribič', 'Svit Lah', 'Kadeti', 'Skupina 2'), 14, 'Gregor Štern'],
]

const VRSTA: Tk[] = [
  K(201, 'Anže Vidmar', 'Lan Kolar', 'Člani', 'Skupina 4', ['do konca skupine še 2 kola', 'Vidmar čaka 18 min']),
  K(202, 'Nik Oblak', 'Jakob Zajc', 'Kadeti', 'Skupina 1', ['zadnje kolo skupine']),
  K(203, 'Tina Žnidaršič', 'Pia Ambrožič', 'Članice', 'Skupina A', ['do konca skupine še 3 kola']),
  K(204, 'Gašper Rebolj', 'Andraž Vovk', 'Mladinci', '1/8 finala', ['zmagovalec gre v četrtfinale']),
  K(205, 'Žan Kos', 'Luka Krajnc', 'Člani', 'Skupina 1'),
  K(206, 'Ema Gregorič', 'Ajda Kastelic', 'Članice', 'Skupina B', ['do konca skupine še 3 kola']),
  K(207, 'Bor Kovač', 'Jan Horvat', 'Člani', 'Skupina 4', ['do konca skupine še 2 kola']),
  K(208, 'Sara Bajc', 'Teja Bregar', 'Članice', 'Skupina A'),
  K(209, 'Simon Jug', 'Andrej Šuštar', 'Člani', 'Skupina 2', ['zadnja tekma pred 12 min']),
  K(210, 'Ožbolt / Pečnik', 'Vidic / Gorjup', 'Dvojice', 'četrtfinale', ['mreža čaka na to tekmo']),
  K(211, 'Gal Mlakar', 'Boštjan Glavič', 'Člani', 'Skupina 1'),
  K(212, 'Neža Car', 'Vesna Rot', 'Članice', 'Skupina A', ['do konca skupine še 3 kola']),
  K(213, 'NTK Celje', 'ŠD Kajuh', 'Ekipe', 'Skupina A', ['srečanje na dveh mizah'], true),
  K(214, 'Erik Kavčič', 'Jaka Ribič', 'Kadeti', 'Skupina 2'),
  K(215, 'Hana Rajh', 'Lucija Pavlič', 'Članice', 'Skupina B', ['do konca skupine še 3 kola']),
  K(216, 'Tadej Mihelič', 'Oskar Rems', 'Kadeti', 'Skupina 3'),
  K(217, 'Luka Krajnc', 'Matic Potočnik', 'Člani', 'Skupina 1'),
  K(218, 'David Bezjak', 'Nace Pintar', 'Mladinci', '1/8 finala', ['zmagovalec gre v četrtfinale']),
  K(219, 'Ula Zorman', 'Ines Kunc', 'Članice', 'Skupina A', ['do konca skupine še 3 kola']),
  K(220, 'Martin Zorko', 'Bor Kovač', 'Člani', 'Skupina 4'),
  K(221, 'Žiga Kuhar', 'Rene Brglez', 'Mladinci', '1/8 finala'),
  K(222, 'Svit Lah', 'Maks Petek', 'Kadeti', 'Skupina 2'),
  K(223, 'Jan Kralj', 'Simon Jug', 'Člani', 'Skupina 2'),
]

const REZERVA: Tk[] = [
  K(301, 'Anže Vidmar', 'Bor Kovač', 'Člani', 'Skupina 4', ['do konca skupine še 1 kolo']),
  K(302, 'Nik Oblak', 'Rene Kolenc', 'Kadeti', 'Skupina 1', ['zadnje kolo skupine']),
  K(303, 'Eva Pirc', 'Neža Car', 'Članice', 'Skupina A', ['do konca skupine še 2 kola']),
  K(304, 'Tim Zupan', 'Blaž Jereb', 'Člani', 'Skupina 3', ['do konca skupine še 2 kola']),
  K(305, 'Urban Hribar', 'Simon Jug', 'Člani', 'Skupina 2', ['do konca skupine še 1 kolo']),
  K(306, 'Lara Hočevar', 'Ema Gregorič', 'Članice', 'Skupina B', ['do konca skupine še 2 kola']),
]

const kopija = <T,>(s: T): T => structuredClone(s)

export function ura(min: number): string {
  const h = Math.floor(min / 60) % 24
  const m = ((min % 60) + 60) % 60
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
}

export function osebe(t: Tk): string[] {
  if (t.ekipno) return [t.a, t.b]
  return [t.a, t.b].flatMap((x) => x.split(' / '))
}

export function priimek(ime: string): string {
  if (ime.includes(' / ') || /^(NTK|ŠD|NK) /.test(ime)) return ime
  const d = ime.trim().split(' ')
  return d[d.length - 1]
}

export const kratko = (t: Tk) => priimek(t.a) + ' – ' + priimek(t.b)
export const imena = (t: Tk) => t.a + ' – ' + t.b
export const opis = (t: Tk) => t.kat + ' · ' + t.del

function prazneMize(n: number, od = 1): MizaStanje[] {
  const r: MizaStanje[] = []
  for (let st = od; st <= n; st++) r.push({ st, izklopljena: false, tekma: null, prostaOd: null, cakajociSodnik: null })
  return r
}

export type Scenarij = 'v-teku' | 'vse-zasedene' | 'prvi-zagon'

/* Pravkar sproščena miza 7 (v posnetkih zelena): stanje se začne z zasedeno mizo, nato jo
   harness sprosti, da stran vidi prehod in jo poudari 30 s, kot v pravem delovanju. */
export const TEKMA_PRED_SPROSTITVIJO: Tk = K(999, 'Prvi Igralec', 'Drugi Igralec', 'Člani', 'Skupina 3')

export function zacetnoStanje(scenarij: Scenarij = 'v-teku', zZasedeno7 = false): Stanje {
  let s: Stanje = {
    imeTurnirja: 'Odprto prvenstvo Celja 2026',
    steviloMiz: 20,
    porazenecSodi: true,
    pocitekMin: 5,
    ura: URA0,
    mize: prazneMize(20),
    vrsta: VRSTA.map((k) => ({ ...k })),
    rezerva: REZERVA.map((k) => ({ ...k })),
    klici: [],
  }
  s.mize[9].izklopljena = true
  for (const [st, t, min, sodnik, miza2] of IGRA) {
    const tk: TekmaNaMizi = { ...t, ob: URA0 - min, sodnik: sodnik || null, miza: st, miza2: miza2 || null }
    s.mize[st - 1].tekma = tk
    if (miza2) s.mize[miza2 - 1].tekma = tk
  }
  s.mize[2].prostaOd = URA0 - 2
  s.mize[2].cakajociSodnik = 'Jure Božič'
  s.mize[6].prostaOd = URA0
  s.mize[6].cakajociSodnik = 'Aljaž Košir'
  s.mize[14].prostaOd = URA0 - 6
  if (zZasedeno7 && scenarij === 'v-teku') {
    s.mize[6].tekma = { ...TEKMA_PRED_SPROSTITVIJO, ob: URA0 - 20, sodnik: null, miza: 7, miza2: null }
    s.mize[6].prostaOd = null
    s.mize[6].cakajociSodnik = null
  }
  s.klici = IGRA.map(([st, t, min, sodnik, miza2]) => ({
    st, miza2: miza2 || null, imena: imena(t), opis: opis(t), sodnik: sodnik || null, ob: URA0 - min,
  })).sort((a, b) => b.ob - a.ob)

  if (scenarij === 'vse-zasedene') {
    for (const [st, k] of predlogi(s)) s = poklici(s, k.id, st)
  }
  if (scenarij === 'prvi-zagon') {
    s.steviloMiz = null
    s.mize = []
    s.klici = []
  }
  return s
}

/* Sprosti mizo 7 (konec dummy tekme): prosta od zdaj, sodnik čaka. */
export function sprostiMizo7(s0: Stanje): Stanje {
  const s = kopija(s0)
  s.mize[6].tekma = null
  s.mize[6].prostaOd = URA0
  s.mize[6].cakajociSodnik = 'Aljaž Košir'
  return s
}

/* Kdo zdaj igra ali sodi: ime → miza in vloga. */
function zasedeni(s: Stanje): Map<string, { st: number; vloga: string }> {
  const z = new Map<string, { st: number; vloga: string }>()
  for (const m of s.mize) {
    const t = m.tekma
    if (!t || t.miza !== m.st) continue
    osebe(t).forEach((o) => z.set(o, { st: m.st, vloga: 'igra' }))
    if (t.sodnik) z.set(t.sodnik, { st: m.st, vloga: 'sodi' })
  }
  return z
}

interface KandidatStanje extends Tk {
  zaseden: boolean
  razlogiZasedenosti: string[]
}

export function vrsta(s: Stanje): KandidatStanje[] {
  const z = zasedeni(s)
  return s.vrsta.map((k) => {
    const r = osebe(k)
      .filter((o) => z.has(o))
      .map((o) => {
        const x = z.get(o)!
        return priimek(o) + ' ' + x.vloga + ' na mizi ' + x.st
      })
    return { ...k, zaseden: r.length > 0, razlogiZasedenosti: r }
  })
}

export const prosteMize = (s: Stanje) => s.mize.filter((m) => !m.izklopljena && !m.tekma)

/* Predlog za vsako prosto mizo: [miza, kandidat]. Ekipnih ne predlaga (rabijo dve mizi). */
export function predlogi(s: Stanje): Map<number, KandidatStanje> {
  const v = vrsta(s).filter((k) => !k.zaseden && !k.ekipno)
  const zasedenoVKrogu = new Set<string>()
  const r = new Map<number, KandidatStanje>()
  for (const m of prosteMize(s)) {
    const k = v.find((x) => ![...r.values()].includes(x) && osebe(x).every((o) => !zasedenoVKrogu.has(o)))
    if (!k) continue
    r.set(m.st, k)
    osebe(k).forEach((o) => zasedenoVKrogu.add(o))
  }
  return r
}

function privzetiSodnik(m: MizaStanje | undefined, k: Tk): string | null {
  const s = m && m.cakajociSodnik
  if (!s || osebe(k).includes(s)) return null
  return s
}

export function poklici(s0: Stanje, id: number, st: number, o: { sodnik?: string | null; miza2?: number | null } = {}): Stanje {
  const s = kopija(s0)
  const i = s.vrsta.findIndex((k) => k.id === id)
  if (i < 0) return s0
  const k = s.vrsta.splice(i, 1)[0]
  const m = s.mize[st - 1]
  if (!m || m.tekma || m.izklopljena) return s0
  const sodnik = o.sodnik !== undefined ? o.sodnik || null : privzetiSodnik(m, k)
  const t: TekmaNaMizi = { ...k, ob: s.ura, sodnik, miza: st, miza2: o.miza2 || null }
  for (const n of [st, t.miza2]) {
    if (!n) continue
    const x = s.mize[n - 1]
    x.tekma = t
    x.cakajociSodnik = null
  }
  s.klici.unshift({ st, miza2: t.miza2, imena: imena(t), opis: opis(t), sodnik, ob: s.ura })
  return s
}

export function vnesiIzid(s0: Stanje, st: number, zmagovalec: 'a' | 'b'): Stanje {
  const s = kopija(s0)
  const t = s.mize[st - 1]?.tekma
  if (!t) return s0
  const porazenec = zmagovalec === 'a' ? t.b : t.a
  for (const m of s.mize) {
    if (!m.tekma || m.tekma.id !== t.id) continue
    m.tekma = null
    m.prostaOd = s.ura
    m.cakajociSodnik = m.st === t.miza && s.porazenecSodi && !t.ekipno && !porazenec.includes(' / ') ? porazenec : null
  }
  if (s.rezerva.length) s.vrsta.push(s.rezerva.shift()!)
  return s
}

export function preklici(s0: Stanje, st: number): Stanje {
  const s = kopija(s0)
  const t = s.mize[st - 1]?.tekma
  if (!t) return s0
  for (const m of s.mize) if (m.tekma && m.tekma.id === t.id) m.tekma = null
  const { ob: _ob, sodnik: _sodnik, miza: _miza, miza2: _miza2, ...k } = t
  void _ob; void _sodnik; void _miza; void _miza2
  s.vrsta.unshift(k)
  s.klici = s.klici.filter((c) => c.imena !== imena(t))
  return s
}

export function premakni(s0: Stanje, st: number, nova: number, sodnik?: string | null): Stanje {
  const s = kopija(s0)
  const t = s.mize[st - 1]?.tekma
  const cilj = s.mize[nova - 1]
  if (!t || !cilj || cilj.tekma || cilj.izklopljena) return s0
  s.mize[st - 1].tekma = null
  s.mize[st - 1].prostaOd = s.ura
  if (t.miza2 === st) t.miza2 = nova
  else t.miza = nova
  if (sodnik !== undefined) t.sodnik = sodnik
  cilj.tekma = t
  cilj.cakajociSodnik = null
  if (t.miza2 && t.miza2 !== nova) s.mize[t.miza2 - 1].tekma = t
  return s
}

export function nastavi(
  s0: Stanje,
  v: { steviloMiz: number | null; izklopljene: number[]; porazenecSodi: boolean; pocitekMin: number },
): Stanje {
  const s = kopija(s0)
  const n = Math.max(1, Math.min(99, v.steviloMiz ?? 1))
  if (s.mize.length < n) s.mize = s.mize.concat(prazneMize(n, s.mize.length + 1))
  else s.mize = s.mize.slice(0, n)
  s.steviloMiz = v.steviloMiz === null ? null : n
  s.porazenecSodi = v.porazenecSodi
  s.pocitekMin = v.pocitekMin
  s.mize.forEach((m) => {
    if (!m.tekma) m.izklopljena = v.izklopljene.includes(m.st)
  })
  return s
}

/* ---------- MizeTurnirjaDto ---------- */

/* Stalni id-ji oseb po imenu (id-ji sodnikov in `osebe` tekme). */
const idOsebe = (() => {
  const zemljevid = new Map<string, number>()
  return (ime: string): number => {
    if (!zemljevid.has(ime)) zemljevid.set(ime, 1000 + zemljevid.size)
    return zemljevid.get(ime)!
  }
})()

export function osebaDto(ime: string): OsebaMizeDto {
  return { id: idOsebe(ime), ime }
}

/* Vse osebe turnirja za izbiro sodnika (igralci iz vseh tekem in obeh sodnikov). */
export function vseOsebe(): OsebaMizeDto[] {
  const imena_ = new Set<string>()
  for (const k of [...VRSTA, ...REZERVA, ...IGRA.map((x) => x[1])]) osebe(k).forEach((o) => imena_.add(o))
  for (const [, , , sodnik] of IGRA) if (sodnik) imena_.add(sodnik)
  imena_.add('Jure Božič')
  imena_.add('Aljaž Košir')
  return [...imena_].filter((i) => !/^(NTK|ŠD|NK) /.test(i)).sort((a, b) => a.localeCompare(b, 'sl')).map(osebaDto)
}

function udelezenec(ime: string, idPrijave: number): Udelezenec {
  if (ime.includes(' / ')) {
    const [a, b] = ime.split(' / ')
    return { idPrijave, polnoIme: a, klub: null, polnoIme2: b, klub2: null, priimek: a, priimek2: b }
  }
  return { idPrijave, polnoIme: ime, klub: null, polnoIme2: null, klub2: null, priimek: priimek(ime), priimek2: null }
}

function cas(min: number): string {
  return `2026-11-24T${ura(min)}:00`
}

export function tekmaDto(t: Tk, dodatno: { miza?: number | null; miza2?: number | null; ob?: number | null; sodnik?: string | null } = {}): TekmaMizeDto {
  const naMizi = dodatno.miza != null
  const tekma = {
    id: t.id,
    faza: t.del.startsWith('Skupina') ? 'SKUPINA' : 'GLAVNI',
    idSkupina: null,
    kolo: 1,
    pozicija: 1,
    status: naMizi ? 'V_IGRI' : 'PRIPRAVLJENA',
    izidTip: null,
    steviloNizov: 5,
    udelezenec1: udelezenec(t.a, t.id * 2),
    udelezenec2: udelezenec(t.b, t.id * 2 + 1),
    dobljeniNizi1: 0,
    dobljeniNizi2: 0,
    nizi: [],
    idZmagovalcaPrijave: null,
    miza: dodatno.miza ?? null,
    spremembaElo1: null,
    spremembaElo2: null,
    ratingPred1: null,
    ratingPred2: null,
    idSrecanje: t.ekipno ? 9000 + t.id : null,
    idPrenesena: null,
    idZreb: null,
    razponOd: null,
    razponDo: null,
    idIzvorZmagovalca1: null,
    idIzvorZmagovalca2: null,
    listekNatisnjenOb: null,
  } as unknown as TekmaDto
  return {
    tekma,
    idDogodka: 7,
    imeDogodka: t.kat,
    del: t.del,
    ekipno: t.ekipno,
    idSrecanje: tekma.idSrecanje,
    miza: dodatno.miza ?? null,
    miza2: dodatno.miza2 ?? null,
    poklicanaOb: dodatno.ob != null ? cas(dodatno.ob) : null,
    sodnik: dodatno.sodnik ? osebaDto(dodatno.sodnik) : null,
    osebe: osebe(t).map(idOsebe),
    idTurnir: 1,
    imeTurnirja: 'Jesenski turnir',
  }
}

export function vDto(s: Stanje): MizeTurnirjaDto {
  /* Ročni red kot na strežniku: tekme s seznama naprej v njegovem redu, ostale za njimi. */
  const red = s.rocniRed ?? []
  const mesto = (id: number) => (red.includes(id) ? red.indexOf(id) : Number.MAX_SAFE_INTEGER)
  const kand = [...vrsta(s)].sort((a, b) => mesto(a.id) - mesto(b.id))
  const pred = predlogi(s)
  const mize: MizaDto[] = s.mize.map((m) => ({
    stevilka: m.st,
    izklopljena: m.izklopljena,
    tekma: m.tekma
      ? tekmaDto(m.tekma, { miza: m.tekma.miza, miza2: m.tekma.miza2, ob: m.tekma.ob, sodnik: m.tekma.sodnik })
      : null,
    prostaOd: m.prostaOd === null ? null : cas(m.prostaOd),
    cakajociSodnik: m.cakajociSodnik ? osebaDto(m.cakajociSodnik) : null,
    predlogi: pred.has(m.st) ? [pred.get(m.st)!.id] : [],
  }))
  const kandidati: KandidatMizeDto[] = kand.map((k) => ({
    tekma: tekmaDto(k),
    zaseden: k.zaseden,
    razlogi: k.zaseden ? k.razlogiZasedenosti : k.razlogi,
  }))
  return {
    idTurnir: 1,
    imeTurnirja: s.imeTurnirja,
    steviloMiz: s.steviloMiz,
    izklopljeneMize: s.mize.filter((m) => m.izklopljena).map((m) => m.st),
    porazenecSodi: s.porazenecSodi,
    pocitekMin: s.pocitekMin,
    dogodkiVTeku: true,
    casStreznika: cas(s.ura),
    mize,
    kandidati,
    rocniVrstniRed: kandidati.some((k) => red.includes(k.tekma.tekma.id)),
    skupne: {
      turnirji: [{ id: 1, ime: s.imeTurnirja, datumZacetka: null }],
      kategorije: [],
      redNastavljen: false,
    },
  }
}
