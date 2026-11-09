/* Prikazni harness za izpis v slogu SVR (samo razvoj): /dev/izpis-svr. Podatki so
   prepisani iz vzorca (1. OT SV Regije: skupine 1/A-1/D in žreba I. skupine) in iz
   4. skupine s prostimi prehodi (»bye«), da se izpis primerja z Excelovim listom kos za
   kosom. Brez prijave in omrežja.

   Parametri:
     oblikovanje=0   brez znamke, kraja in noge (list kot vzorec)
     tocke=0         brez točk po nizih v stolpcu »Točke«
     deli=skupine|zreb   samo del izpisa */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import type { MrezaDto, TurnirDto } from '../api/tipi'
import { IzpisListi } from '../komponente/IzpisListi'
import { glavaIzpisa, sestaviIzpis, type DelIzpisa } from '../pomozno/izpisRezultatov'
import { IzpisStran } from '../strani/IzpisStran'

type Igralec = [ime: string, klub: string]
/* [igralec a, igralec b (1-based v skupini), nizi a, nizi b, točke po nizih iz vidika a]. */
type Rez = [number, number, number, number, string?]

interface SkupinaPodatki {
  oznaka: string
  nivo: number
  clani: Igralec[]
  kola: Rez[][]
  mesta: number[]
}

const SKUPINE: SkupinaPodatki[] = [
  {
    oznaka: '1A', nivo: 1,
    clani: [['Bor Markovič', 'Savinja'], ['Enej Simić', 'Tempo'], ['Erik Haslinger', 'Cirkovce'], ['Filip Firar', 'Maribor']],
    kola: [
      [[1, 4, 3, 0, '11:7 11:5 11:9'], [2, 3, 0, 3]],
      [[4, 3, 1, 3], [1, 2, 3, 1]],
      [[2, 4, 3, 1], [3, 1, 1, 3]],
    ],
    mesta: [1, 3, 2, 4],
  },
  {
    oznaka: '1B', nivo: 1,
    clani: [['Anže Safošnik', 'Cirkovce'], ['Ali Šabani', 'Maribor'], ['Bine Štrancar', 'Tempo'], ['Filip Golnar', 'Sobota']],
    kola: [
      [[1, 4, 3, 1], [2, 3, 3, 0]],
      [[4, 3, 0, 3], [1, 2, 3, 0]],
      [[2, 4, 1, 3], [3, 1, 1, 3]],
    ],
    mesta: [1, 2, 3, 4],
  },
  {
    oznaka: '1C', nivo: 1,
    clani: [['Urh Novak', 'Cirkovce'], ['Maj Indi Valencia Pavlič', 'Maribor'], ['Domen Ropoša', 'Sobota'], ['Taj Žagar', 'Savinja']],
    kola: [
      [[1, 4, 3, 2], [2, 3, 2, 3]],
      [[4, 3, 1, 3], [1, 2, 3, 2]],
      [[2, 4, 2, 3], [3, 1, 2, 3]],
    ],
    mesta: [1, 4, 2, 3],
  },
  {
    oznaka: '1D', nivo: 1,
    clani: [['Amadej Strgar', 'Tempo'], ['Lena Murata', 'Cirkovce'], ['Lovro Hozjan', 'Savinja'], ['Nejc Rečnik', 'Maribor']],
    kola: [
      [[1, 4, 2, 3], [2, 3, 0, 3]],
      [[4, 3, 0, 3], [1, 2, 3, 0]],
      [[2, 4, 0, 3], [3, 1, 3, 1]],
    ],
    mesta: [3, 4, 1, 2],
  },
  {
    /* Skupina treh (4/F iz vzorca ima enega igralca manj). */
    oznaka: '4F', nivo: 4,
    clani: [['Zala Šteharnik', 'Fužinar'], ['Pia Benko', 'Ljutomer'], ['Florijan Gabrovec', 'Maribor']],
    kola: [[[1, 2, 3, 1]], [[3, 1, 3, 0]], [[2, 3, 0, 3]]],
    mesta: [2, 3, 1],
  },
]

/* Žreb: osem mest od vrha (null = prosto), rezultati po parih v vrstnem redu iger. */
interface ZrebPodatki {
  nivo: number
  prvoMesto: number
  mesta: (string | null)[]
  /* [zmagovalec, poraženec, nizi zmagovalca, nizi poraženca]; sestavi par iz imen. */
  izidi: [string, string, number, number][]
}

const ZREBI: ZrebPodatki[] = [
  {
    nivo: 1, prvoMesto: 1,
    mesta: ['Bor Markovič', 'Ali Šabani', 'Urh Novak', 'Nejc Rečnik', 'Domen Ropoša', 'Lovro Hozjan', 'Erik Haslinger', 'Anže Safošnik'],
    izidi: [
      ['Bor Markovič', 'Ali Šabani', 3, 1], ['Urh Novak', 'Nejc Rečnik', 3, 0], ['Domen Ropoša', 'Lovro Hozjan', 3, 2], ['Anže Safošnik', 'Erik Haslinger', 3, 2],
      ['Bor Markovič', 'Urh Novak', 3, 0], ['Anže Safošnik', 'Domen Ropoša', 3, 1],
      ['Anže Safošnik', 'Bor Markovič', 3, 1],
      ['Domen Ropoša', 'Urh Novak', 3, 1],
      ['Nejc Rečnik', 'Ali Šabani', 3, 2], ['Lovro Hozjan', 'Erik Haslinger', 3, 0],
      ['Lovro Hozjan', 'Nejc Rečnik', 3, 1],
      ['Erik Haslinger', 'Ali Šabani', 3, 1],
    ],
  },
  {
    nivo: 1, prvoMesto: 9,
    mesta: ['Enej Simić', 'Filip Golnar', 'Taj Žagar', 'Lena Murata', 'Maj Indi Valencia Pavlič', 'Amadej Strgar', 'Filip Firar', 'Bine Štrancar'],
    izidi: [
      ['Enej Simić', 'Filip Golnar', 3, 1], ['Taj Žagar', 'Lena Murata', 3, 1], ['Amadej Strgar', 'Maj Indi Valencia Pavlič', 3, 2], ['Filip Firar', 'Bine Štrancar', 3, 0],
      ['Enej Simić', 'Taj Žagar', 3, 1], ['Amadej Strgar', 'Filip Firar', 3, 1],
      ['Amadej Strgar', 'Enej Simić', 3, 1],
      ['Taj Žagar', 'Filip Firar', 3, 1],
      ['Filip Golnar', 'Lena Murata', 3, 0], ['Bine Štrancar', 'Maj Indi Valencia Pavlič', 3, 1],
      ['Filip Golnar', 'Bine Štrancar', 3, 2],
      ['Maj Indi Valencia Pavlič', 'Lena Murata', 3, 2],
    ],
  },
  {
    /* Osem mest s prostimi prehodi, kot 4B. skupina vzorca. */
    nivo: 4, prvoMesto: 1,
    mesta: ['Luka Mlasko', null, null, null, 'Trina Mikša', 'Patrik Bokan', 'Julija Knuplež', 'Pia Benko'],
    izidi: [
      ['Trina Mikša', 'Patrik Bokan', 3, 0], ['Julija Knuplež', 'Pia Benko', 3, 1],
      ['Trina Mikša', 'Julija Knuplež', 3, 1],
      ['Trina Mikša', 'Luka Mlasko', 3, 2],
      ['Pia Benko', 'Patrik Bokan', 3, 1],
    ],
  },
]

/* Žreb s toliko mesti (16, 32) in tremi prostimi prehodi: tekme odigra po istem drevesu
   kot zaledje (zmagovalci naprej, poraženci v pot za slabša mesta), boljši (nižja številka)
   vedno zmaga. Za preverjanje lomljenja na liste in širine. */
function velikZreb(velikost: number): ZrebPodatki {
  const mesta: (string | null)[] = Array.from({ length: velikost }, (_, i) => `Igralec ${String(i + 1).padStart(2, '0')}`)
  for (const i of [1, 2, 5]) mesta[i] = null
  const jakost = (ime: string) => Number(ime.slice(-2))
  const izidi: ZrebPodatki['izidi'] = []
  const razdeli = (sloji: (string | null)[]) => {
    if (sloji.length === 1) return
    const zmagovalci: (string | null)[] = []
    const porazenci: (string | null)[] = []
    for (let i = 0; i < sloji.length; i += 2) {
      const [a, b] = [sloji[i], sloji[i + 1]]
      if (a === null || b === null) {
        zmagovalci.push(a ?? b)
        porazenci.push(null)
      } else {
        const [z, p] = jakost(a) < jakost(b) ? [a, b] : [b, a]
        izidi.push([z, p, 3, i % 3])
        zmagovalci.push(z)
        porazenci.push(p)
      }
    }
    razdeli(zmagovalci)
    razdeli(porazenci)
  }
  razdeli(mesta)
  return { nivo: 1, prvoMesto: 1, mesta, izidi }
}

function sestaviMrezo(velikost: number | null = null): MrezaDto {
  const SK = velikost ? [] : SKUPINE
  const ZR = velikost ? [velikZreb(velikost)] : ZREBI
  const prijave = new Map<string, { id: number; ime: string; klub: string }>()
  const prijava = (ime: string, klub = '') => {
    let p = prijave.get(ime)
    if (!p) {
      p = { id: prijave.size + 1, ime, klub }
      prijave.set(ime, p)
    }
    return p
  }
  const udelezenec = (ime: string) => {
    const p = prijava(ime)
    return { idPrijave: p.id, polnoIme: p.ime, klub: p.klub, polnoIme2: null, klub2: null, priimek: p.ime.split(' ').slice(-1)[0], priimek2: null }
  }

  let idTekme = 1
  const tekme: Record<string, unknown>[] = []
  const skupine = SK.map((s, i) => {
    const idSkupine = i + 1
    s.clani.forEach(([ime, klub]) => prijava(ime, klub))
    const clani = s.clani.map(([ime]) => prijava(ime))
    const zmage = clani.map(() => 0)
    const niziZa = clani.map(() => 0)
    const niziProti = clani.map(() => 0)
    s.kola.forEach((kolo, k) =>
      kolo.forEach(([a, b, sa, sb, tocke], poz) => {
        const pa = clani[a - 1]
        const pb = clani[b - 1]
        const zmagalA = sa > sb
        zmage[(zmagalA ? a : b) - 1]++
        niziZa[a - 1] += sa
        niziProti[a - 1] += sb
        niziZa[b - 1] += sb
        niziProti[b - 1] += sa
        tekme.push({
          id: idTekme++, faza: 'SKUPINA', idSkupina: idSkupine, kolo: k + 1, pozicija: poz + 1, status: 'KONCANA',
          izidTip: 'IGRANO', steviloNizov: 5, udelezenec1: udelezenec(pa.ime), udelezenec2: udelezenec(pb.ime),
          dobljeniNizi1: sa, dobljeniNizi2: sb,
          nizi: (tocke ?? '').split(' ').filter(Boolean).map((n, z) => {
            const [t1, t2] = n.split(':').map(Number)
            return { zaporednaSt: z + 1, tocke1: t1, tocke2: t2 }
          }),
          idZmagovalcaPrijave: zmagalA ? pa.id : pb.id, idPrenesena: null, idZreb: null,
        })
      }),
    )
    return {
      id: idSkupine, oznaka: s.oznaka, stopnja: 1, ime: null, prvoMesto: null, nivo: s.nivo, krogi: [],
      lestvica: clani.map((c, j) => ({
        idPrijave: c.id, idIgralca: c.id, polnoIme: c.ime, klub: c.klub, odigrane: s.kola.length,
        zmage: zmage[j], porazi: s.kola.length - zmage[j], niziZa: niziZa[j], niziProti: niziProti[j],
        mesto: s.mesta[j], krog: null,
      })),
    }
  })

  const nivoji = [...new Set([...SK.map((s) => s.nivo), ...ZR.map((z) => z.nivo)])].sort().map((nivo) => ({
    nivo,
    zrebi: ZR.filter((z) => z.nivo === nivo).map((z, indeks) => {
      const idZreba = 100 * nivo + indeks + 1
      const razpored = z.mesta.map((ime) => (ime === null ? null : prijava(ime).id))
      z.izidi.forEach(([zm, po, nz, np], poz) => {
        tekme.push({
          id: idTekme++, faza: 'GLAVNI', idSkupina: null, kolo: 1, pozicija: poz + 1, status: 'KONCANA', izidTip: 'IGRANO',
          steviloNizov: 5, udelezenec1: udelezenec(zm), udelezenec2: udelezenec(po),
          dobljeniNizi1: nz, dobljeniNizi2: np, nizi: [], idZmagovalcaPrijave: prijava(zm).id, idPrenesena: null,
          idZreb: idZreba, razponOd: z.prvoMesto, razponDo: z.prvoMesto + z.mesta.length - 1,
        })
      })
      return {
        id: idZreba, indeks, faza: 'GLAVNI', ime: indeks === 0 ? 'Glavni žreb' : 'Tolažilni žreb', prvoMesto: z.prvoMesto,
        zadnjeMesto: z.prvoMesto + z.mesta.length - 1, stUdelezencev: z.mesta.filter(Boolean).length, velikostMreze: z.mesta.length, zgrajen: true,
        rocni: false, uredljiv: false, razpored, viri: [],
      }
    }),
  }))

  return {
    dogodek: {
      id: 1, ime: 'Člani', sistemTekmovanja: 'SV_REGIJA', disciplina: 'POSAMICNO', spolKategorija: 'KDORKOLI',
      starostnaKategorija: null, privzetoSteviloNizov: 5, dejanskiStatus: 'ZAKLJUCEN', status: 'ZAKLJUCEN', idTurnir: 1,
    },
    prijave: [...prijave.values()].map((p) => ({
      id: p.id, polnoIme: p.ime, polnoIme2: null, klub: p.klub, klub2: null, stNosilca: p.id, status: 'PRIJAVLJEN',
    })),
    tekme,
    skupine,
    lestvica: [],
    krogi: [],
    izbor: null,
    svRegija: { nivoji },
  } as unknown as MrezaDto
}

const TURNIR = {
  id: 1, ime: '1. OT SV Regije', datumZacetka: '2026-10-11', datumKonca: null, kraj: { ime: 'Maribor' }, dvorana: null,
  logotip: null,
} as unknown as TurnirDto

/* Prava stran izpisa (/dev/izpis-svr-stran/1) z vnaprej napolnjenim predpomnilnikom:
   preveri izbiro sloga, kljukice in naslov brez zaledja. */
export function IzpisSvrStran() {
  const [odjemalec] = useState(() => {
    const o = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } })
    o.setQueryData(['dogodek', 1], sestaviMrezo())
    o.setQueryData(['turnir', 1], TURNIR)
    o.setQueryData(['pokrovitelji', 'turnir', 1], [])
    return o
  })
  return (
    <QueryClientProvider client={odjemalec}>
      <IzpisStran />
    </QueryClientProvider>
  )
}

export default function IzpisSvrPosnetki() {
  const [iskanje] = useSearchParams()
  const oblikovanje = iskanje.get('oblikovanje') !== '0'
  const sTockami = iskanje.get('tocke') !== '0'
  const mreza = sestaviMrezo(Number(iskanje.get('velik')) || null)
  const turnir = TURNIR
  const glava = glavaIzpisa(mreza, turnir)
  const deli: DelIzpisa[] =
    iskanje.get('deli') === 'skupine' ? ['skupine'] : iskanje.get('deli') === 'zreb' ? ['mreza'] : ['skupine', 'mreza']
  const listi = sestaviIzpis(mreza, deli, sTockami, false, 'svr', oblikovanje, glava.naslov)
  return (
    <div className="zreb-stran zreb-stran--izpis">
      <IzpisListi listi={listi} glava={glava} logotip={null} pokrovitelji={[]} oblikovanje={oblikovanje} />
    </div>
  )
}
