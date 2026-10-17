/* Žreb skupin za tisk (vrata dvorane): iz mreže dogodka sestavi to, kar gre na
   list - skupine z igralci in tekme po kolih. Brez izrisa, da pravila lista
   (kdo je prost, kakšen je vrstni red) živijo na enem mestu.

   Podatki so isti kot na strani dogodka (MrezaDto), zaledje za tisk ne
   potrebuje ničesar svojega. */
import type {
  DogodekDto,
  IzidTekme,
  MrezaDto,
  PrijavaDto,
  SistemTekmovanja,
  SkupinaDto,
  TekmaDto,
} from '../api/tipi'
import { imePrijave, imeUdelezenca } from '../api/tipi'
import { kratekKlub } from './oblikovanje'

/* Sistemi, ki imajo skupine; krožni sistem je ena sama lestvica brez skupin. */
const SISTEMI_S_SKUPINAMI: ReadonlySet<SistemTekmovanja> = new Set([
  'SKUPINE_IZLOCILNI',
  'SKUPINE',
  'SKUPINE_ZA_MESTA',
  'SV_REGIJA',
])

/* Ali ima dogodek skupine, ki jih je smiselno obesiti na vrata: žreb je
   narejen in tekmovanje teče. Pripravo sodi pod žreb (skupin še ni), zaključen
   dogodek pa na vratih ni več potreben. Gleda DEJANSKI status, ker so skupine
   izžrebane že pred datumom, ko gledalcu status pove »V pripravi«. */
export function imaZrebanoSkupine(dogodek: DogodekDto): boolean {
  return SISTEMI_S_SKUPINAMI.has(dogodek.sistemTekmovanja) && dogodek.dejanskiStatus === 'V_TEKU'
}

export interface TiskClan {
  idPrijave: number
  ime: string
  klub: string | null
}

/* Izid odigrane tekme: nizi (»3 : 1«) in oznaka, kadar ni bila odigrana do
   konca (»b. b.«, »predaja«). */
export interface TiskIzid {
  nizi: string
  oznaka: string | null
}

export interface TiskTekma {
  id: number
  ime1: string
  ime2: string
  /* Prazen, dokler tekma ni odigrana. */
  izid: TiskIzid | null
  miza: number | null
}

export interface TiskKolo {
  kolo: number
  tekme: TiskTekma[]
  /* Člani, ki v tem kolu ne igrajo (liho število članov). */
  prosti: string[]
}

export interface TiskSkupina {
  id: number
  naslov: string
  clani: TiskClan[]
  kola: TiskKolo[]
  steviloTekem: number
  /* Vsaj ena tekma ima dodeljeno mizo; šele takrat list nameni stolpec zanjo. */
  imaMize: boolean
}

/* Skupine pod skupnim naslovom (predtekmovanje, finalne skupine, nivo). */
export interface TiskSklop {
  kljuc: string
  naslov: string | null
  skupine: TiskSkupina[]
}

/* Kratka oznaka izida, kadar tekma ni bila odigrana do konca; enaka besedila
   kot na kartici tekme. */
const OZNAKE_IZIDA: Partial<Record<IzidTekme, string>> = {
  BREZ_BOJA: 'b. b.',
  PREDAJA: 'predaja',
  DISKVALIFIKACIJA: 'diskv.',
}

function izidTekme(tekma: TekmaDto): TiskIzid | null {
  if (tekma.status !== 'KONCANA') return null
  return {
    nizi: `${tekma.dobljeniNizi1} : ${tekma.dobljeniNizi2}`,
    oznaka: (tekma.izidTip && OZNAKE_IZIDA[tekma.izidTip]) || null,
  }
}

/* Klub člana: pri dvojicah oba, če sta različna; pri ekipi tak, kot je
   zapisan (kratkega imena ekip ne tvorimo). */
function klubClana(prijava: PrijavaDto | undefined, klubLestvice: string | null, ekipno: boolean) {
  if (ekipno) return klubLestvice
  const kluba = [prijava?.klub ?? klubLestvice, prijava?.klub2]
    .filter((k): k is string => !!k)
    .map(kratekKlub)
  const brezPodvojitev = kluba.filter((k, i) => kluba.indexOf(k) === i)
  return brezPodvojitev.length > 0 ? brezPodvojitev.join(' / ') : null
}

function pripraviSkupino(mreza: MrezaDto, skupina: SkupinaDto): TiskSkupina {
  const ekipno = mreza.dogodek.disciplina === 'EKIPNO'
  const prijavePoId = new Map(mreza.prijave.map((p) => [p.id, p]))

  /* Člani predtekmovalne skupine so našteti po jakosti (nosilec skupine
     prvi), kakor jih je žreb razporedil; lestvica sama bi jih med tekmovanjem
     premetavala po izidih. Skupine za mesta (višja stopnja) nimajo žrebanih
     članov, zato ostane vrstni red lestvice. */
  const vrstice = skupina.lestvica.map((vrstica, indeks) => ({
    vrstica,
    indeks,
    prijava: prijavePoId.get(vrstica.idPrijave),
  }))
  if (skupina.stopnja === 1) {
    const nosilec = (p: PrijavaDto | undefined) => p?.stNosilca ?? Number.MAX_SAFE_INTEGER
    vrstice.sort((a, b) => nosilec(a.prijava) - nosilec(b.prijava) || a.indeks - b.indeks)
  }
  const clani: TiskClan[] = vrstice.map(({ vrstica, prijava }) => ({
    idPrijave: vrstica.idPrijave,
    ime: prijava ? imePrijave(prijava) : vrstica.polnoIme,
    klub: klubClana(prijava, vrstica.klub, ekipno),
  }))

  const tekme = mreza.tekme.filter(
    (t) =>
      t.faza === 'SKUPINA' &&
      t.idSkupina === skupina.id &&
      t.udelezenec1 !== null &&
      t.udelezenec2 !== null,
  )
  const kola = [...new Set(tekme.map((t) => t.kolo))]
    .sort((a, b) => a - b)
    .map((kolo): TiskKolo => {
      const vKolu = tekme.filter((t) => t.kolo === kolo).sort((a, b) => a.pozicija - b.pozicija)
      const igra = new Set(
        vKolu.flatMap((t) => [t.udelezenec1!.idPrijave, t.udelezenec2!.idPrijave]),
      )
      return {
        kolo,
        tekme: vKolu.map((t) => ({
          id: t.id,
          ime1: imeUdelezenca(t.udelezenec1) ?? '',
          ime2: imeUdelezenca(t.udelezenec2) ?? '',
          izid: izidTekme(t),
          miza: t.miza,
        })),
        prosti: clani.filter((c) => !igra.has(c.idPrijave)).map((c) => c.ime),
      }
    })

  return {
    id: skupina.id,
    naslov: skupina.ime ?? `Skupina ${skupina.oznaka}`,
    clani,
    kola,
    steviloTekem: tekme.length,
    imaMize: tekme.some((t) => t.miza !== null),
  }
}

const poOznaki = (a: SkupinaDto, b: SkupinaDto) =>
  a.oznaka.localeCompare(b.oznaka, 'sl', { numeric: true })

/* Skupine dogodka v sklopih, kot jih razvrsti stran dogodka: predtekmovanje,
   nato skupine, ki odločajo mesta; SV regija po nivojih. Naslov sklopa je
   samo tam, kjer jih je več (en sam sklop ne potrebuje naslova). */
export function sklopiZrebSkupin(mreza: MrezaDto): TiskSklop[] {
  const skupine = mreza.skupine
  if (skupine.length === 0) return []

  if (mreza.dogodek.sistemTekmovanja === 'SV_REGIJA') {
    return [...new Set(skupine.map((s) => s.nivo))]
      .sort((a, b) => a - b)
      .map((nivo) => ({
        kljuc: `nivo-${nivo}`,
        naslov: `Nivo ${nivo}`,
        skupine: skupine
          .filter((s) => s.nivo === nivo)
          .sort(poOznaki)
          .map((s) => pripraviSkupino(mreza, s)),
      }))
  }

  /* Skupine za mesta gredo pod en naslov ne glede na stopnjo (vir ima vsako
     za svojo fazo, igrajo pa se vzporedno) - enako kot na strani dogodka. */
  const zaMesta = skupine
    .filter((s) => s.stopnja > 1 && s.prvoMesto !== null)
    .sort((a, b) => (a.prvoMesto ?? 0) - (b.prvoMesto ?? 0))
  const ostale = skupine.filter((s) => !zaMesta.includes(s))
  const sklopi = [...new Set(ostale.map((s) => s.stopnja))]
    .sort((a, b) => a - b)
    .map((stopnja) => ({
      kljuc: String(stopnja),
      naslov: stopnja === 1 ? 'Predtekmovanje' : `${stopnja}. stopnja`,
      skupine: ostale.filter((s) => s.stopnja === stopnja).sort(poOznaki),
    }))
  if (zaMesta.length > 0) {
    sklopi.push({ kljuc: 'mesta', naslov: 'Finalne skupine', skupine: zaMesta })
  } else if (sklopi.length > 1) {
    sklopi[sklopi.length - 1].naslov = 'Finalne skupine'
  }

  return sklopi.map((sklop) => ({
    kljuc: sklop.kljuc,
    naslov: sklopi.length > 1 ? sklop.naslov : null,
    skupine: sklop.skupine.map((s) => pripraviSkupino(mreza, s)),
  }))
}

export function steviloSkupinVSklopih(sklopi: TiskSklop[]): number {
  return sklopi.reduce((vsota, sklop) => vsota + sklop.skupine.length, 0)
}
