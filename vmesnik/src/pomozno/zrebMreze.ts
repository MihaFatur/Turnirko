/* Žreb izločilnega dela za tisk (vrata dvorane): iz mreže dogodka sestavi
   liste, na katerih je mreža narisana kot na papirju - stolpec za vsako kolo,
   tekme med njimi povezane s črtami. Brez izrisa, da pravila lista (koliko
   tekem gre na stran, kje je katera tekma) živijo na enem mestu.

   Mreža je polno binarno drevo (ZrebStoritev.zgradiIzlocilnoMrezo): v kolu k
   je 2^(K-k) tekem s pozicijami 1..2^(K-k), tekma (k, p) pa napreduje v
   (k+1, ceil(p/2)). Zato je mesto tekme na listu računsko: vrstica in razpon
   vrstic, v katerih je tekma centrirana med svojima dvema virovoma.

   Podatki so isti kot na strani dogodka (MrezaDto), zaledje za tisk ne
   potrebuje ničesar svojega. */
import type { DogodekDto, MrezaDto, SistemTekmovanja, TekmaDto } from '../api/tipi'
import { imeUdelezenca } from '../api/tipi'
import { imeKola, imeKolaKratko } from './oblikovanje'
import { OZNAKE_IZIDA } from './zrebSkupin'

/* Sistemi z izločilno mrežo: čist izločilni in skupine, po katerih sledi mreža.
   SV regija ima svoje drevo za vsa mesta (več žrebov na nivo), skupine za mesta
   pa mreže nimajo - oboje je drug list. */
const SISTEMI_Z_MREZO: ReadonlySet<SistemTekmovanja> = new Set(['IZLOCILNI', 'SKUPINE_IZLOCILNI'])

/* Kolikor kol gre na en list: pri 16 tekmah v prvem kolu je tekma še visoka
   ~9,5 mm (dve vrstici imen). Večja mreža se razreže na poddrevesa po 16 tekem
   prvega kola, preostala kola gredo na končni list. */
const KOL_NA_LIST = 5

/* Ali je dogodek vreden pogleda na mrežo: sistem ima izločilni del in dogodek
   teče. Zaključen dogodek z vrat odpade, pred žrebom mreže še ni. Mreža pri
   skupinah nastane šele po skupinah, zato je to le kandidat - ali mreža res
   obstaja, pove imaMrezoZaTisk. */
export function jeKandidatZaTiskMreze(dogodek: DogodekDto): boolean {
  return SISTEMI_Z_MREZO.has(dogodek.sistemTekmovanja) && dogodek.dejanskiStatus === 'V_TEKU'
}

/* Tekme glavne mreže po kolih (indeks 0 = 1. kolo), po poziciji. null, kadar
   mreža ni polno drevo - takega lista ne znamo pošteno narisati (uvožen
   dogodek z drugačno obliko), zato ga raje ne ponudimo. */
function kolaGlavneMreze(tekme: TekmaDto[]): TekmaDto[][] | null {
  const glavne = tekme.filter((t) => t.faza === 'GLAVNI')
  if (glavne.length === 0) return null
  const stKol = Math.max(...glavne.map((t) => t.kolo))
  const kola: TekmaDto[][] = []
  for (let kolo = 1; kolo <= stKol; kolo++) {
    const vKolu = glavne.filter((t) => t.kolo === kolo).sort((a, b) => a.pozicija - b.pozicija)
    const pricakovano = 2 ** (stKol - kolo)
    if (vKolu.length !== pricakovano || vKolu.some((t, i) => t.pozicija !== i + 1)) return null
    kola.push(vKolu)
  }
  return kola
}

export function imaMrezoZaTisk(mreza: MrezaDto): boolean {
  return jeKandidatZaTiskMreze(mreza.dogodek) && kolaGlavneMreze(mreza.tekme) !== null
}

/* Ena stran tekme. Brez imena je mesto še prazno: list ga pusti kot črto, v
   katero organizator vpiše zmagovalca. */
export interface MrezaStran {
  ime: string | null
  /* Kdo pride v prazno mesto, kadar vir ni na istem listu (»Zmagovalec 2. dela«). */
  napis: string | null
  /* Prosti prehod: nasprotnika ni. */
  prost: boolean
  /* Dobljeni nizi; prazni, dokler tekma ni odigrana. */
  nizi: number | null
  zmagovalec: boolean
}

export interface MrezaTekmaTisk {
  id: number
  /* Prva vrstica tekme in število vrstic, ki jih zaseda (mreža lista je CSS grid). */
  vrstica: number
  razpon: number
  strani: [MrezaStran, MrezaStran]
  /* »b. b.«, »predaja«; prazna pri odigrani tekmi. */
  oznaka: string | null
  /* Črta iz tekme vodi k zgornjemu oz. spodnjemu viru naslednje tekme; null pri
     zadnji tekmi lista. */
  smer: 'zgoraj' | 'spodaj' | null
}

export interface MrezaKoloTisk {
  kolo: number
  naslov: string
  tekme: MrezaTekmaTisk[]
}

export interface ZrebMrezeList {
  kljuc: string
  /* »1. del od 2« pri mreži, ki ne gre na en list; sicer null. */
  del: string | null
  /* Vrstic mreže = tekem prvega stolpca; od tega je odvisna višina vrstice. */
  vrstic: number
  kola: MrezaKoloTisk[]
  prvak: { naslov: string; ime: string | null }
  /* Tekma za 3. mesto; samo na listu, ki ima finale. */
  tretje: MrezaTekmaTisk | null
  /* Prvi stolpec je nadaljevanje (vire nosijo drugi listi). */
  nadaljevanje: boolean
}

export interface ZrebMrezeDogodka {
  /* Koliko udeležencev je v mreži (brez praznih mest in prostih prehodov). */
  steviloUdelezencev: number
  listi: ZrebMrezeList[]
}

interface Okno {
  kljuc: string
  del: string | null
  kolaOd: number
  kolaDo: number
  /* Prva pozicija tekme v kolu k, ki še sodi na list. */
  pozOd: (kolo: number) => number
  /* Številka poddrevesa (prvi list pri razrezu), sicer null. */
  poddrevo: number | null
  nadaljevanje: boolean
}

function stran(tekma: TekmaDto, ktera: 1 | 2, napis: string | null): MrezaStran {
  const udelezenec = ktera === 1 ? tekma.udelezenec1 : tekma.udelezenec2
  const koncana = tekma.status === 'KONCANA' && tekma.izidTip !== 'PROSTO'
  return {
    ime: imeUdelezenca(udelezenec),
    napis: udelezenec ? null : napis,
    prost: tekma.izidTip === 'PROSTO' && !udelezenec,
    nizi: koncana ? (ktera === 1 ? tekma.dobljeniNizi1 : tekma.dobljeniNizi2) : null,
    zmagovalec: koncana && udelezenec !== null && tekma.idZmagovalcaPrijave === udelezenec.idPrijave,
  }
}

function oznakaIzida(tekma: TekmaDto): string | null {
  if (tekma.status !== 'KONCANA' || !tekma.izidTip) return null
  return OZNAKE_IZIDA[tekma.izidTip] ?? null
}

/* Okna lista: mreža do 5 kol gre na en list; večja se razreže na poddrevesa po
   5 kol (vsako ima 16 tekem prvega kola) in končni list s preostalimi koli. */
function oknaMreze(stKol: number): Okno[] {
  if (stKol <= KOL_NA_LIST) {
    return [
      { kljuc: 'mreza', del: null, kolaOd: 1, kolaDo: stKol, pozOd: () => 1, poddrevo: null, nadaljevanje: false },
    ]
  }
  const stPoddreves = 2 ** (stKol - KOL_NA_LIST)
  const okna: Okno[] = []
  for (let s = 1; s <= stPoddreves; s++) {
    okna.push({
      kljuc: `del-${s}`,
      del: `${s}. del od ${stPoddreves}`,
      kolaOd: 1,
      kolaDo: KOL_NA_LIST,
      pozOd: (kolo) => (s - 1) * 2 ** (KOL_NA_LIST - kolo) + 1,
      poddrevo: s,
      nadaljevanje: false,
    })
  }
  okna.push({
    kljuc: 'koncnica',
    del: 'finalni del',
    kolaOd: KOL_NA_LIST + 1,
    kolaDo: stKol,
    pozOd: () => 1,
    poddrevo: null,
    nadaljevanje: true,
  })
  return okna
}

export function sklopiZrebMreze(mreza: MrezaDto): ZrebMrezeDogodka | null {
  const kola = kolaGlavneMreze(mreza.tekme)
  if (kola === null) return null
  const stKol = kola.length

  const udelezenci = new Set<number>()
  for (const t of kola[0]) {
    for (const u of [t.udelezenec1, t.udelezenec2]) if (u) udelezenci.add(u.idPrijave)
  }

  /* Tekma za 3. mesto: samo ena tolažilna tekma ob vključeni nastavitvi (uvožena
     tolažilna mreža z več tekmami ni del tega lista). */
  const tolazilne = mreza.tekme.filter((t) => t.faza === 'TOLAZILNI')
  const zaTretje =
    mreza.dogodek.tekmaZaTretjeMesto && tolazilne.length === 1 && stKol >= 2 ? tolazilne[0] : null
  const napisPorazenca = `Poraženec ${imeKolaKratko(stKol - 1, stKol)}`

  const listi = oknaMreze(stKol).map((okno): ZrebMrezeList => {
    const kolaLista: MrezaKoloTisk[] = []
    for (let kolo = okno.kolaOd; kolo <= okno.kolaDo; kolo++) {
      const od = okno.pozOd(kolo)
      const stTekem = 2 ** (okno.kolaDo - kolo)
      const razpon = 2 ** (kolo - okno.kolaOd)
      kolaLista.push({
        kolo,
        naslov: imeKola(kolo, stKol),
        tekme: kola[kolo - 1]
          .filter((t) => t.pozicija >= od && t.pozicija < od + stTekem)
          .map((t): MrezaTekmaTisk => {
            /* Prvi stolpec nadaljevanja ima vire na drugih listih: napiše, od kod
               zmagovalec pride (kolo 5 ima po eno tekmo na poddrevo, njena
               pozicija je zato številka poddrevesa). */
            const napis = (ktera: 1 | 2) =>
              okno.nadaljevanje && kolo === okno.kolaOd
                ? `Zmagovalec ${2 * t.pozicija - 2 + ktera}. dela`
                : null
            return {
              id: t.id,
              vrstica: (t.pozicija - od) * razpon + 1,
              razpon,
              strani: [stran(t, 1, napis(1)), stran(t, 2, napis(2))],
              oznaka: oznakaIzida(t),
              smer: kolo < okno.kolaDo ? (t.pozicija % 2 === 1 ? 'zgoraj' : 'spodaj') : null,
            }
          }),
      })
    }

    const zadnja = kolaLista[kolaLista.length - 1].tekme[0]
    const zadnjaTekma = kola[okno.kolaDo - 1].find((t) => t.id === zadnja.id)!
    const zmagovalecUdelezenec = [zadnjaTekma.udelezenec1, zadnjaTekma.udelezenec2].find(
      (u) => u && zadnjaTekma.idZmagovalcaPrijave === u.idPrijave,
    )
    const imaFinale = okno.kolaDo === stKol

    return {
      kljuc: okno.kljuc,
      del: okno.del,
      vrstic: 2 ** (okno.kolaDo - okno.kolaOd),
      kola: kolaLista,
      prvak: {
        naslov: okno.poddrevo !== null ? `Zmagovalec ${okno.poddrevo}. dela` : 'Zmagovalec',
        ime:
          zadnjaTekma.status === 'KONCANA' && zmagovalecUdelezenec
            ? imeUdelezenca(zmagovalecUdelezenec)
            : null,
      },
      tretje:
        imaFinale && zaTretje
          ? {
              id: zaTretje.id,
              vrstica: 1,
              razpon: 1,
              strani: [stran(zaTretje, 1, napisPorazenca), stran(zaTretje, 2, napisPorazenca)],
              oznaka: oznakaIzida(zaTretje),
              smer: null,
            }
          : null,
      nadaljevanje: okno.nadaljevanje,
    }
  })

  return { steviloUdelezencev: udelezenci.size, listi }
}
