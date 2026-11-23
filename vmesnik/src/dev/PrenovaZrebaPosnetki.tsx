/* Prikazni harness za posnetke prenove »Popravi skupine«, »Popravi mrežo« in »Listki za tisk«
   (design_handoff_skupine_mreza_listki, razdelek 3). Samo za razvoj: /dev/prenova-zreba,
   naloži se leno in samo, kadar import.meta.env.DEV (glej App.tsx). Brez prijave in omrežja;
   podatki so fiksni (prenovaZrebaPodatki.ts, prepis podatki.js iz predaje), shranjevanje
   in predlog strežnika pa nadomestijo lokalne funkcije.

   Parametri:
     zaslon=skupine|mreza|listki
     naprava=namizje|telefon   okno mora biti res široko 1280 oz. 390 px: telefonska
                               postavitev sledi medijski poizvedbi (640 px)
     stanje=…
       skupine:  popravek | izbran | spremembe | napaka | rocni-vpis
       mreza:    popravek | rocna-mreza | odlagalisce | napaka | iskanje
       listki:   nic | delno | vrzel | potrditev
   Stanje se pripravi s pravimi kliki in vlečenjem v pravem vmesniku (kot bi jih naredil
   organizator), ne z vstavljenim stanjem: posnetek tako preveri tudi odzivnost. */
import { useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

import { dogodkiApi } from '../api/zahteve'
import type { IzlocilniPredlogDto, SkupinePredlogDto } from '../api/tipi'
import { RocnaMrezaOkno, type Udelezenec } from '../komponente/RocnaMrezaOkno'
import { shraniRazvrstitev } from '../pomozno/listki'
import { ListkiPogled } from '../strani/ListkiStran'
import { RocniSkupineOkno } from '../komponente/RocniSkupineOkno'
import {
  DOGODEK,
  IGRALCI,
  ST_SKUPIN,
  TURNIR,
  mrezaListkov,
  prijaveDto,
  skupineDto,
  trenutneSkupine,
  zacetnaMreza,
} from './prenovaZrebaPodatki'

/* Strežnika tu ni: predlog in shranjevanje sta lokalna. */
dogodkiApi.predlogSkupin = async (): Promise<SkupinePredlogDto> => ({
  skupine: Array.from({ length: ST_SKUPIN }, () => []),
  rezerve: [],
})
/* Klici, ki bi šli na strežnik, se zapišejo v window.__klici (za preverjanje odziva). */
const klici: unknown[][] = []
;(window as unknown as { __klici: unknown[][] }).__klici = klici
dogodkiApi.rocniSkupine = async (id, skupine) => {
  klici.push(['rocniSkupine', id, skupine])
  return {} as never
}
dogodkiApi.izlocilniPredlog = async (): Promise<IzlocilniPredlogDto> => ({ udelezenci: [], idjiPoMestih: [] })
dogodkiApi.izlocilniMesta = async () => ({}) as never
dogodkiApi.oznaciListke = async (id, idji, natisnjeno) => {
  klici.push(['oznaciListke', id, idji, natisnjeno])
  return {} as never
}

const pocakaj = (ms: number) => new Promise((r) => window.setTimeout(r, ms))

/* Pravi klik na element z danim besedilom (po vrsti pojavitve); harness tako pripravi stanja
   kot uporabnik. React posodobi izris šele, ko se klic konča: naslednji klik mora počakati. */
async function klikni(izbirnik: string, besedilo: string, n = 0): Promise<boolean> {
  const zadetki = Array.from(document.querySelectorAll<HTMLElement>(izbirnik)).filter((e) =>
    (e.getAttribute('aria-label') ?? e.textContent ?? '').includes(besedilo),
  )
  zadetki[n]?.click()
  await pocakaj(80)
  return !!zadetki[n]
}

/* Vlečenje z miško (HTML5): z izvirnega elementa na ciljnega, kot bi ga naredila miška. */
async function povleci(izvor: Element | null, cilj: Element | null) {
  if (!izvor || !cilj) return
  const prenos = new DataTransfer()
  const poslji = (tarca: Element, vrsta: string) =>
    tarca.dispatchEvent(new DragEvent(vrsta, { bubbles: true, cancelable: true, dataTransfer: prenos }))
  poslji(izvor, 'dragstart')
  await pocakaj(80)
  poslji(cilj, 'dragover')
  poslji(cilj, 'drop')
  poslji(izvor, 'dragend')
  await pocakaj(80)
}

const izvedena = new Set<string>()
/* StrictMode zažene učinke dvakrat; priprava stanja pa se mora zgoditi samo enkrat (zato brez
   čiščenja časovnika: prvi zagon bi ga preklical, drugi bi ga preskočil). */
function useEnkrat(kljuc: string, delo: () => void | Promise<void>) {
  useEffect(() => {
    if (izvedena.has(kljuc)) return
    izvedena.add(kljuc)
    window.setTimeout(delo, 400)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kljuc])
}

function Skupine({ stanje }: { stanje: string }) {
  const vpis = stanje === 'rocni-vpis'
  const top = stanje === 'top'
  const prijave = top
    ? /* Razširitev prototipa: format TOP, 3 skupine po 8, ostali so rezerve. */
      prijaveDto(null).map((p, i) =>
        i < 24
          ? { ...p, idSkupina: 100 + Math.floor(i / 8), mestoVSkupini: (i % 8) + 1 }
          : { ...p, status: 'REZERVA' as const },
      )
    : prijaveDto(vpis ? null : trenutneSkupine())
  useEnkrat(`skupine-${stanje}-${window.innerWidth}`, async () => {
    const vrstica = '.rocne-skupine__vrstica'
    if (stanje === 'izbran') await klikni(vrstica, 'Pirc')
    if (stanje === 'spremembe') {
      /* id 7 in 20, nato 30 in 41 (prototip: demo = spremembe) */
      const ime = (id: number) => IGRALCI[id - 1].priimek
      await klikni(vrstica, ime(7))
      await klikni(vrstica, ime(20))
      await klikni(vrstica, ime(30))
      await klikni(vrstica, ime(41))
    }
    if (stanje === 'napaka') await klikni('.rocne-skupine__koracnik-gumb', 'več')
  })
  return (
    <RocniSkupineOkno
      idDogodka={1}
      sistem={top ? 'SKUPINE' : 'SKUPINE_IZLOCILNI'}
      nadnaslov={`${DOGODEK} · ${IGRALCI.length} igralcev`}
      prijave={prijave}
      skupine={vpis ? [] : top ? skupineDto().slice(0, 3) : skupineDto()}
      steviloSkupin={top ? 3 : ST_SKUPIN}
      velikostSkupine={top ? 8 : 4}
      onZapri={() => {}}
      onShranjeno={() => {}}
    />
  )
}

const mestoMreze = (n: number) => document.querySelector(`.rocna-mreza__mesto[aria-label^="Mesto ${n}:"]`)

function Mreza({ stanje }: { stanje: string }) {
  const popravek = stanje !== 'rocna-mreza'
  const poId = new Map<number, Udelezenec>(
    IGRALCI.map((p) => [
      p.id,
      {
        ime: `${p.ime} ${p.priimek}`,
        imeLahko: p.ime,
        priimek: p.priimek,
        uvrstitev: '',
        klub: p.klub,
        rating: p.rating,
        stNosilca: p.jakost,
      },
    ]),
  )
  useEnkrat(`mreza-${stanje}-${window.innerWidth}`, async () => {
    if (stanje === 'odlagalisce') {
      await povleci(mestoMreze(11), document.querySelector('.rocna-mreza__odlagalisce'))
    }
    if (stanje === 'napaka') {
      /* mesto 1 zamenja prosto mesto 63 v 4. četrtini: izberi, preklopi četrtino, zamenjaj */
      await klikni('.rocna-mreza__mesto', 'Mesto 1:')
      await klikni('.rocna-mreza__cetrtina', '4. četrtina')
      await klikni('.rocna-mreza__mesto', 'Mesto 63:')
      await klikni('.rocna-mreza__cetrtina', '1. četrtina')
    }
    if (stanje === 'iskanje') await klikni('.rocna-mreza__mesto', 'Mesto 3:')
  })
  return (
    <RocnaMrezaOkno
      naslov={popravek ? 'Popravi mrežo' : 'Ročna mreža'}
      podnaslov={DOGODEK}
      popravek={popravek}
      poId={poId}
      predlog={async () => ({ udelezenci: IGRALCI.map((p) => p.id), idjiPoMestih: zacetnaMreza() })}
      shrani={async (mesta) => {
        klici.push(['izlocilniMesta', mesta])
        return {}
      }}
      onZapri={() => {}}
      onShranjeno={() => {}}
    />
  )
}

/* Okno za tisk v harnessu ni potrebno: window.print bi v brezglavem Chromu obvisel. */
window.print = () => {}
/* Prototip pokaže vrstni red »po kolih«. */
shraniRazvrstitev('kola')

function Listki({ stanje }: { stanje: string }) {
  const mreza = useMemo(() => {
    const m = mrezaListkov(stanje === 'nic' || stanje === 'vrzel' ? 'nic' : 'delno')
    if (stanje !== 'izlocilni') return m
    /* Razširitev prototipa (ki izločilnega dela nima): osem tekem 1/8 finala in tekma za 3. mesto. */
    const glavne = Array.from({ length: 8 }, (_, i) => ({
      ...m.tekme[0],
      id: 9001 + i,
      faza: 'GLAVNI' as const,
      idSkupina: null,
      kolo: 1,
      pozicija: i + 1,
      miza: i + 1,
      listekNatisnjenOb: null,
      udelezenec1: m.tekme[i * 2].udelezenec1,
      udelezenec2: m.tekme[i * 2 + 1].udelezenec2,
    }))
    const tolazilna = { ...glavne[0], id: 9100, faza: 'TOLAZILNI' as const, miza: 3 }
    return { ...m, tekme: [...m.tekme, ...glavne, tolazilna] }
  }, [stanje])
  useEnkrat(`listki-${stanje}-${window.innerWidth}`, async () => {
    if (stanje === 'vrzel') {
      /* 1. kolo skupin 1–7: skupinam 8–12 odklikaj ploščici 1. kola */
      const vrste = Array.from(document.querySelectorAll('.listki-tabela__vrsta'))
      for (const vrsta of vrste.slice(7, 12)) {
        const celica = vrsta.querySelector('.listki-tabela__celica')
        for (const gumb of Array.from(celica?.querySelectorAll<HTMLElement>('button') ?? [])) {
          gumb.click()
          await pocakaj(60)
        }
      }
    }
    if (stanje === 'potrditev') await klikni('.listki-ploskev__natisni', 'Natisni')
  })
  return (
    <ListkiPogled
      idDogodka={1}
      mreza={mreza}
      imeTurnirja={TURNIR}
      logotipTurnirja={null}
      smemOznacevati
      kode={undefined}
      sodnikiSlikajo
      kodeNalaganje={false}
      kodeNapaka={false}
      branjeNaVoljo
      cakaTurnir={false}
    />
  )
}

export default function PrenovaZrebaPosnetki() {
  const [iskanje] = useSearchParams()
  const zaslon = iskanje.get('zaslon') ?? 'skupine'
  const stanje = iskanje.get('stanje') ?? ''
  if (zaslon === 'listki') return <Listki key={iskanje.toString()} stanje={stanje || 'nic'} />
  if (zaslon === 'mreza') return <Mreza key={iskanje.toString()} stanje={stanje || 'popravek'} />
  return <Skupine key={iskanje.toString()} stanje={stanje || 'popravek'} />
}
