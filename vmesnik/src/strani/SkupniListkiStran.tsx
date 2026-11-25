/* En tisk listkov vseh kategorij (/turnirji/:id/listki; skupne mize, V78).

   Lastnik 25. 11. 2026: v isti dvorani igrata dva turnirja (U15 in U21, vsak z moškimi in
   ženskami) na istih mizah, najprej 1. kolo U15 moških, nato U15 žensk, U21 moških in U21
   žensk, nato 2. kolo … Organizator zato listke tiska za vse kategorije naenkrat: »po
   kolih« da 1. kolo vseh kategorij v vrstnem redu, ki ga je nastavil na strani Mize (enak
   kot predlog tekem), »po kategorijah« pa kategorijo za kategorijo.

   Stran je ista delovna ploskev kot listki ene kategorije (ListkiPloskev): tabela ima
   vrstice po kategoriji in skupini, sled tiska se zapiše na dogodek vsakega listka. Ekipni
   dogodki imajo zapisnike in prazni listki kategorijo, zato ostaneta na strani posamezne
   kategorije. Samo za organizatorja (kategorije in njihov vrstni red da stran Mize). */
import { Fragment, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQueries, useQuery } from '@tanstack/react-query'

import type { KategorijaMizDto, MrezaDto } from '../api/tipi'
import { dogodkiApi, mizeApi } from '../api/zahteve'
import { useIzbiraListkov } from '../komponente/IzbiraListkov'
import { HrbetListka, Listek } from '../komponente/Listek'
import { ListkiPloskev } from '../komponente/ListkiPloskev'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import {
  LISTKOV_NA_LIST,
  listkiVsehKategorij,
  preberiRazvrstitev,
  shraniRazvrstitev,
  type RazvrstitevListkov,
} from '../pomozno/listki'
import { useBranjeListkovNaVoljo } from '../pomozno/useBranjeListkov'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { podatkiListka } from './ListkiStran'

export function SkupniListkiStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  useNaslovStrani('Listki vseh kategorij')

  const pregled = useQuery({
    queryKey: ['turnir', idTurnirja, 'mize'],
    queryFn: () => mizeApi.pregled(idTurnirja),
    staleTime: 30_000,
  })
  const turnirji = pregled.data?.skupne.turnirji ?? []
  /* Posamične kategorije v teku, v vrstnem redu kola. */
  const kategorije = useMemo(
    () => (pregled.data?.skupne.kategorije ?? []).filter((k) => k.status === 'V_TEKU' && !k.ekipno),
    [pregled.data],
  )

  const mreze = useQueries({
    queries: kategorije.map((k) => ({
      queryKey: ['dogodek', k.idDogodka],
      queryFn: () => dogodkiApi.mreza(k.idDogodka),
    })),
  })
  /* Kode QR s skrivnostjo (S1) po kategorijah; ključ so pripravljene tekme, kot na strani
     listkov kategorije. */
  const kode = useQueries({
    queries: kategorije.map((k, i) => ({
      queryKey: ['kode-listkov', k.idDogodka, mreze[i]?.data?.tekme.map((t) => t.id).join(',') ?? ''],
      queryFn: () => dogodkiApi.kodeListkov(k.idDogodka),
      enabled: mreze[i]?.data !== undefined,
    })),
  })
  const branjeNaVoljo = useBranjeListkovNaVoljo()

  if (pregled.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (!pregled.data) return <NapakaPoizvedbe poizvedba={pregled} kaj="kategorij" />
  if (mreze.some((m) => m.isPending)) return <p className="obvestilo">Nalaganje …</p>
  const napaka = mreze.find((m) => m.error)
  if (napaka) return <NapakaPoizvedbe poizvedba={napaka} kaj="žrebov" />

  return (
    <SkupniListkiPogled
      idTurnirja={idTurnirja}
      nadnaslov={turnirji.map((t) => t.ime).join(' + ')}
      kategorije={kategorije.map((k, i) => ({ kategorija: k, mreza: mreze[i].data! }))}
      povezane={turnirji.length > 1}
      kode={Object.assign({}, ...kode.map((k) => k.data?.kode ?? {}))}
      sodnikiSlikajo={kode.some((k) => !!k.data?.sodnikiListki)}
      kodeNalaganje={kode.some((k) => k.isLoading)}
      kodeNapaka={kode.some((k) => !!k.error)}
      branjeNaVoljo={branjeNaVoljo}
    />
  )
}

function SkupniListkiPogled({
  idTurnirja,
  nadnaslov,
  kategorije,
  povezane,
  kode,
  sodnikiSlikajo,
  kodeNalaganje,
  kodeNapaka,
  branjeNaVoljo,
}: {
  idTurnirja: number
  nadnaslov: string
  kategorije: { kategorija: KategorijaMizDto; mreza: MrezaDto }[]
  povezane: boolean
  kode: Record<string, string>
  sodnikiSlikajo: boolean
  kodeNalaganje: boolean
  kodeNapaka: boolean
  branjeNaVoljo: boolean
}) {
  const [razvrstitev, nastaviRazvrstitevStanje] = useState<RazvrstitevListkov>(preberiRazvrstitev)
  const nastaviRazvrstitev = (r: RazvrstitevListkov) => {
    nastaviRazvrstitevStanje(r)
    shraniRazvrstitev(r)
  }

  /* Ime kategorije v tabeli: enako imenovane kategorije dveh turnirjev dobijo ime turnirja. */
  const naslovi = useMemo(() => {
    const imen = new Map<string, number>()
    for (const { kategorija: k } of kategorije) imen.set(k.ime, (imen.get(k.ime) ?? 0) + 1)
    return new Map(
      kategorije.map(({ kategorija: k }) => [
        k.idDogodka,
        povezane && (imen.get(k.ime) ?? 0) > 1 ? `${k.ime} · ${k.imeTurnirja}` : k.ime,
      ]),
    )
  }, [kategorije, povezane])

  const listki = useMemo(
    () =>
      listkiVsehKategorij(
        kategorije.map(({ kategorija: k, mreza }) => ({
          idDogodka: k.idDogodka,
          naslov: naslovi.get(k.idDogodka) ?? k.ime,
          mreza,
        })),
        razvrstitev,
      ),
    [kategorije, naslovi, razvrstitev],
  )
  const izbira = useIzbiraListkov(null, listki, true)
  const [hrbet, nastaviHrbet] = useState(true)

  /* Za vsak listek: dogodek (kategorija na listku) in turnir (glava listka). */
  const poDogodku = new Map(kategorije.map((k) => [k.kategorija.idDogodka, k]))
  const izbranih = izbira.izbraniListki.length
  const manjkaKoda = izbira.izbraniListki.some((l) => !kode[String(l.tekma.id)])
  const zHrbtom = izbranih > 0 && sodnikiSlikajo && hrbet
  const naslovSodnika = `${window.location.origin}/s`

  const listkiZaTisk = izbira.izbraniListki.flatMap((l) => {
    const k = l.kategorija ? poDogodku.get(l.kategorija.idDogodka) : undefined
    if (!k) return []
    return [{
      kljuc: `tekma-${l.tekma.id}`,
      podatki: podatkiListka(l, k.kategorija.imeTurnirja, k.mreza.dogodek, kode[String(l.tekma.id)] ?? null),
    }]
  })
  const listi: (typeof listkiZaTisk)[] = []
  for (let i = 0; i < listkiZaTisk.length; i += LISTKOV_NA_LIST) listi.push(listkiZaTisk.slice(i, i + LISTKOV_NA_LIST))

  return (
    <>
      <ListkiPloskev
        vrsta="listek"
        nadnaslov={nadnaslov}
        naslov="Listki vseh kategorij"
        povezavaNazaj={`/turnirji/${idTurnirja}/mize`}
        nazaj="Mize"
        listki={listki}
        izbira={izbira}
        razvrstitev={kategorije.length > 0 ? { vrednost: razvrstitev, nastavi: nastaviRazvrstitev, poKategorijah: true } : undefined}
        hrbet={sodnikiSlikajo ? { vklopljen: hrbet, preklopi: () => nastaviHrbet(!hrbet) } : undefined}
        opozorilo={
          (kodeNapaka || (manjkaKoda && !kodeNalaganje)) && izbranih > 0 ? (
            <p className="obvestilo obvestilo--opozorilo">
              Kod za sodnike ni bilo mogoče naložiti. Listke lahko natisneš, a izid z njih bo
              mogoče poslati samo s fotografijo na pultu, ne s telefonom sodnika.
            </p>
          ) : undefined
        }
        opomba={
          <p className="listki-ploskev__opomba">
            Kategorije so v vrstnem redu, ki ga nastaviš na strani Mize (Uredi dvorano → Turnirji in
            kategorije). Prazne listke in zapisnike ekipnih kategorij natisneš na strani posamezne
            kategorije.
            {!sodnikiSlikajo && branjeNaVoljo
              ? ' Ko na strani Branje listkov vklopiš »Sodniki slikajo listke«, dobi vsak list na hrbtu kodo za sodnika.'
              : ''}
          </p>
        }
        navodilo={zHrbtom ? 'A4 obojestransko · 100 % · brez robov' : 'A4 · 100 % · brez robov'}
        tiskCaka={izbranih > 0 && kodeNalaganje}
        onNatisni={() => izbira.natisni()}
        onPotrdi={izbira.potrdi}
      />

      {/* Listi A4 so samo za tisk (@media print), kot na strani listkov kategorije. */}
      <div className="listki-stran listki-stran--listki">
        <div className="listki-listi">
          {listi.map((list, i) => (
            <Fragment key={i}>
              <section className="listki-list" aria-label={`List ${i + 1} od ${listi.length}`}>
                {list.map((l) => (
                  <Listek key={l.kljuc} podatki={l.podatki} />
                ))}
                {Array.from({ length: LISTKOV_NA_LIST - list.length }, (_, j) => (
                  <div key={`prazno-${j}`} className="listek listek--prazen" aria-hidden="true" />
                ))}
              </section>
              {zHrbtom && (
                <section className="listki-list listki-list--hrbet" aria-label={`Hrbtna stran lista ${i + 1}`}>
                  {Array.from({ length: LISTKOV_NA_LIST }, (_, j) => (
                    <HrbetListka key={j} naslov={naslovSodnika} />
                  ))}
                </section>
              )}
            </Fragment>
          ))}
        </div>
      </div>
    </>
  )
}
