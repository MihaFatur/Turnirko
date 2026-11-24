/* Prikazni harness za posnetke prenove strani Mize (design_handoff_mize_tloris, razdelek
   »Obvezno: primerjava s posnetki«). Samo za razvoj: /dev/mize/:id (stran v skupni
   postavitvi, z glavo) in /dev/mize-zaslon/:id (zaslon za igralce), naložita se leno in samo,
   kadar import.meta.env.DEV (glej App.tsx).

   Prava stran se izriše s pravimi komponentami; strežnika ni: mizeApi, tekmeApi in žreb
   dogodka nadomesti stanje v pomnilniku (mizePodatki.ts, prepis prototip/mize-podatki.js).
   Brez prijave: glava (masthead) hoče organizatorja, zato posnetek pred nalaganjem
   odgovori na /api/v1/auth/me (skripta za zajem, ne ta datoteka).

   Parametri:
     stanje=…     namizje: v-teku | vlecenje | okno-poklici | okno-izid | uredi-dvorano |
                  po-klicu | vse-zasedene | prvi-zagon
                  telefon: prosta-miza | zasedena-miza | vnos-izida | vse-zasedene | prvi-zagon
                  zaslon:  v-teku | po-klicu
   Stanje se pripravi s pravimi kliki in vlečenjem (kot bi jih naredil organizator), ne z
   vstavljenim stanjem: posnetek tako preveri tudi odzivnost. Širina okna odloča o postavitvi
   (telefon ≤ 640 px). */
import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import type { DogodekDto, MrezaDto, TekmaDto } from '../api/tipi'
import { dogodkiApi, mizeApi, tekmeApi, turnirjiApi } from '../api/zahteve'
import { MizeStran } from '../strani/MizeStran'
import { MizeZaslonStran } from '../strani/MizeZaslonStran'
import {
  nastavi,
  poklici,
  preklici,
  premakni,
  sprostiMizo7,
  tekmaDto,
  vDto,
  vnesiIzid,
  vseOsebe,
  zacetnoStanje,
  type Scenarij,
  type Stanje,
} from './mizePodatki'

interface DevOkno {
  __klici: unknown[][]
  __tisk?: { listkov: number; mize: string[] }
}

const okno = window as unknown as DevOkno
okno.__klici = []

/* Stanje dvorane, skupno strani in zaslonu. */
const drzava: { s: Stanje } = { s: zacetnoStanje() }

const naMizi = (idTekme: number) => drzava.s.mize.find((m) => m.tekma?.id === idTekme)

mizeApi.pregled = async () => vDto(drzava.s)
mizeApi.osebe = async () => vseOsebe()
mizeApi.nastavi = async (_id, v) => {
  okno.__klici.push(['nastavi', v])
  drzava.s = nastavi(drzava.s, {
    steviloMiz: v.steviloMiz,
    izklopljene: v.izklopljeneMize,
    porazenecSodi: v.porazenecSodi,
    pocitekMin: v.pocitekMin,
  })
}
mizeApi.vrstniRed = async (_id, idjiTekem) => {
  okno.__klici.push(['vrstniRed', idjiTekem])
  drzava.s = { ...drzava.s, rocniRed: idjiTekem }
}
mizeApi.poklici = async (idTekme, v) => {
  okno.__klici.push(['poklici', idTekme, v])
  const sodnik = v.idSodnika === null ? null : (vseOsebe().find((o) => o.id === v.idSodnika)?.ime ?? null)
  const trenutna = naMizi(idTekme)
  if (trenutna?.tekma) {
    if (v.miza === trenutna.tekma.miza) {
      drzava.s = { ...drzava.s, mize: drzava.s.mize.map((m) => (m.tekma?.id === idTekme ? { ...m, tekma: { ...m.tekma!, sodnik } } : m)) }
    } else {
      drzava.s = premakni(drzava.s, trenutna.tekma.miza, v.miza, sodnik)
    }
  } else {
    drzava.s = poklici(drzava.s, idTekme, v.miza, { sodnik, miza2: v.miza2 })
  }
}
mizeApi.preklici = async (idTekme) => {
  okno.__klici.push(['preklici', idTekme])
  const m = naMizi(idTekme)
  if (m?.tekma) drzava.s = preklici(drzava.s, m.tekma.miza)
}
tekmeApi.vnesiRezultat = async (id, v) => {
  okno.__klici.push(['vnesiRezultat', id, v])
  const m = naMizi(id)
  const prvi = v.dobljeniNizi1 ?? 0
  const drugi = v.dobljeniNizi2 ?? 0
  if (m?.tekma) drzava.s = vnesiIzid(drzava.s, m.tekma.miza, prvi > drugi ? 'a' : 'b')
  return { dobljeniNizi1: prvi, dobljeniNizi2: drugi } as unknown as TekmaDto
}

/* Žreb dogodka za listek ob klicu: samo tekme, ki jih listek potrebuje. */
dogodkiApi.mreza = async () => {
  const tekme = [
    ...drzava.s.vrsta.map((k) => tekmaDto(k).tekma),
    ...drzava.s.mize.flatMap((m) => (m.tekma && m.tekma.miza === m.st ? [tekmaDto(m.tekma, { miza: m.st }).tekma] : [])),
  ]
  const skupine = [...new Set([...drzava.s.vrsta, ...drzava.s.mize.flatMap((m) => (m.tekma ? [m.tekma] : []))].map((k) => k.del))]
  return {
    dogodek: { id: 7, ime: 'Člani', spolKategorija: 'KDORKOLI', starostnaKategorija: null, disciplina: 'POSAMEZNO', privzetoSteviloNizov: 5, idTurnir: 1, vir: null },
    tekme: tekme.map((t) => ({ ...t, idSkupina: 1 })),
    skupine: skupine.map((_, i) => ({ id: i + 1, oznaka: String(i + 1) })),
    svRegija: null,
  } as unknown as MrezaDto
}
dogodkiApi.kodeListkov = async () => ({ sodnikiListki: null, kode: {} }) as never
/* Kategorije v teku za gumb »Listki za tiskanje« (?listki=1 = ena kategorija, povezava). */
turnirjiApi.dogodki = async () =>
  (new URLSearchParams(window.location.search).get('listki') === '1'
    ? [{ id: 7, ime: 'Člani', disciplina: 'POSAMICNO', dejanskiStatus: 'V_TEKU' }]
    : [
        { id: 7, ime: 'Člani', disciplina: 'POSAMICNO', dejanskiStatus: 'V_TEKU' },
        { id: 8, ime: 'Članice', disciplina: 'POSAMICNO', dejanskiStatus: 'V_TEKU' },
        { id: 9, ime: 'Ekipe', disciplina: 'EKIPNO', dejanskiStatus: 'V_TEKU' },
      ]) as unknown as DogodekDto[]

/* Tiska tu ni: zapiše se, koliko listkov in s katero mizo bi šlo na list. Z ?tisk=ostani se
   vsebnik po »tisku« ohrani v kopiji (preverba tiskalnega pogleda z emulacijo medija). */
window.print = () => {
  const vsebnik = document.querySelector('.mize-tisk')
  okno.__tisk = {
    listkov: document.querySelectorAll('.mize-tisk .listek:not(.listek--prazen)').length,
    mize: [...document.querySelectorAll('.mize-tisk .listek__miza-okence')].map((e) => e.textContent ?? ''),
  }
  if (vsebnik && new URLSearchParams(window.location.search).get('tisk') === 'ostani') {
    const kopija = vsebnik.cloneNode(true) as HTMLElement
    kopija.id = 'tisk-kopija'
    document.body.appendChild(kopija)
  }
}

const pocakaj = (ms: number) => new Promise((r) => window.setTimeout(r, ms))

/* Pravi klik na element z danim besedilom ali oznako (po vrsti pojavitve). */
async function klikni(izbirnik: string, besedilo: string, n = 0): Promise<boolean> {
  const zadetki = Array.from(document.querySelectorAll<HTMLElement>(izbirnik)).filter((e) =>
    (e.getAttribute('aria-label') ?? e.textContent ?? '').includes(besedilo),
  )
  zadetki[n]?.click()
  await pocakaj(150)
  return !!zadetki[n]
}

/* Vlečenje z miško (HTML5): z izvirnega elementa na ciljnega; z `spusti = false` ostane nad ciljem. */
async function povleci(izvor: Element | null, cilj: Element | null, spusti: boolean) {
  if (!izvor || !cilj) return
  const prenos = new DataTransfer()
  const poslji = (tarca: Element, vrsta: string) =>
    tarca.dispatchEvent(new DragEvent(vrsta, { bubbles: true, cancelable: true, dataTransfer: prenos }))
  poslji(izvor, 'dragstart')
  await pocakaj(200)
  poslji(cilj, 'dragover')
  await pocakaj(150)
  if (spusti) {
    poslji(cilj, 'drop')
    poslji(izvor, 'dragend')
    await pocakaj(200)
  }
}

const izvedena = new Set<string>()
/* StrictMode zažene učinke dvakrat; priprava stanja pa se mora zgoditi samo enkrat. */
function useEnkrat(kljuc: string, delo: () => void | Promise<void>) {
  useEffect(() => {
    if (izvedena.has(kljuc)) return
    izvedena.add(kljuc)
    window.setTimeout(delo, 500)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kljuc])
}

function scenarijZaStanje(stanje: string): Scenarij {
  if (stanje === 'vse-zasedene') return 'vse-zasedene'
  if (stanje === 'prvi-zagon') return 'prvi-zagon'
  return 'v-teku'
}

/* Začetno stanje po parametru; izvede se pred prvim izrisom, ker stran takoj bere strežnik. */
function pripraviStanje(stanje: string, zSproscenoMizo: boolean) {
  const kljuc = '__mize_stanje'
  const w = window as unknown as Record<string, string | undefined>
  if (w[kljuc] === stanje) return
  w[kljuc] = stanje
  const scenarij = scenarijZaStanje(stanje)
  drzava.s = zacetnoStanje(scenarij, zSproscenoMizo && scenarij === 'v-teku')
}

export default function MizePosnetki() {
  const [iskanje] = useSearchParams()
  const stanje = iskanje.get('stanje') ?? 'v-teku'
  const odjemalec = useQueryClient()
  pripraviStanje(stanje, true)

  useEnkrat(`mize-${stanje}-${window.innerWidth}`, async () => {
    const osvezi = async () => {
      await odjemalec.invalidateQueries({ queryKey: ['turnir'] })
      await pocakaj(300)
    }
    /* Miza 7 je pravkar sproščena: stran jo poudari 30 s, ko vidi prehod. */
    if (scenarijZaStanje(stanje) === 'v-teku') {
      drzava.s = sprostiMizo7(drzava.s)
      await osvezi()
    }
    if (stanje === 'vlecenje') {
      await povleci(
        document.querySelector('.mize__kandidat'),
        document.querySelector('[aria-label^="Miza 3,"] .mize__pravokotnik'),
        false,
      )
    }
    if (stanje === 'okno-poklici') await klikni('[role="button"]', 'Miza 3, prosta')
    if (stanje === 'okno-izid') {
      await klikni('[role="button"]', 'Miza 5,')
      await klikni('.mize-okno__izid', 'Erik Kavčič zmaga 3:1')
    }
    if (stanje === 'uredi-dvorano') await klikni('button', 'Uredi dvorano')
    if (stanje === 'po-klicu') {
      await povleci(
        document.querySelector('.mize__kandidat'),
        document.querySelector('[aria-label^="Miza 3,"] .mize__pravokotnik'),
        true,
      )
      await osvezi()
    }
    /* Telefon: tap na ploščico, nato kar zahteva stanje. */
    if (stanje === 'zasedena-miza') await klikni('.mize-tel__ploscica', 'Miza 5,')
    if (stanje === 'vnos-izida') {
      await klikni('.mize-tel__ploscica', 'Miza 5,')
      await klikni('.mize-tel__gumb', 'Vnesi izid')
      await klikni('.mize-okno__izid', 'Maks Petek zmaga 3:2')
    }
  })

  return <MizeStran />
}

/* Zaslon za igralce: isto stanje, brez glave aplikacije. */
export function MizeZaslonPosnetki() {
  const [iskanje] = useSearchParams()
  const stanje = iskanje.get('stanje') ?? 'v-teku'
  pripraviStanje(`zaslon-${stanje}`, false)
  /* Klic se pripravi PRED prvim izrisom, da posnetek ne ujame vmesnega stanja. */
  if (stanje === 'po-klicu' && !drzava.s.klici.some((c) => c.st === 3 && c.imena.startsWith('Anže'))) {
    drzava.s = poklici(drzava.s, 201, 3)
  }
  return <MizeZaslonStran />
}
