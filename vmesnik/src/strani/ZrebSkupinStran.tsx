/* Natisljivi listki skupin: vsaka skupina s križno tabelo in razporedom po
   kolih, listki zloženi na liste A4 brez robov. Kot sodniški listki je stran
   namenoma zunaj skupne postavitve (brez navigacije), da je natis čist, in jo
   tiska brskalnik (window.print) - zaledje ne rabi ničesar svojega, vse je že
   v mreži dogodka.

   Dve poti, ista vsebina:
   - /dogodki/:id/zreb-skupin  - ena kategorija,
   - /turnirji/:id/zreb-skupin - vse kategorije turnirja, ki imajo izžrebane
     skupine, ena za drugo (listek nosi kategorijo, zato si lahko delijo list). */
import { useMemo, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { MrezaDto, TurnirDto } from '../api/tipi'
import { ZrebOkvir } from '../komponente/ZrebOkvir'
import { ListiSkupin } from '../komponente/ZrebSkupinTisk'
import { sklonListov, sklonSkupin } from '../pomozno/oblikovanje'
import {
  imaZrebanoSkupine,
  listkiSkupin,
  type NacinListka,
  zloziNaListe,
} from '../pomozno/zrebSkupin'

export function ZrebSkupinDogodkaStran() {
  const { id } = useParams()
  const idDogodka = Number(id)

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja, datum in kraj za glavo listka; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })

  return (
    <ListkiZreba
      nazaj={`/dogodki/${idDogodka}`}
      nazajOznaka="← Nazaj na dogodek"
      naslov={mreza.data ? `Žreb skupin · ${mreza.data.dogodek.ime}` : null}
      turnir={turnir.data}
      nalaganje={mreza.isPending}
      napaka={mreza.error}
      mreze={mreza.data ? [mreza.data] : []}
    />
  )
}

export function ZrebSkupinTurnirjaStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)

  const turnir = useQuery({
    queryKey: ['turnir', idTurnirja],
    queryFn: () => turnirjiApi.najdi(idTurnirja),
  })
  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja),
  })

  /* Mreža vsakega dogodka pod istim ključem kot stran dogodka, da si odgovor
     delita. Na list pridejo samo dogodki, ki jih žreb skupin zadeva. */
  const zaTisk = (dogodki.data ?? []).filter(imaZrebanoSkupine)
  const mreze = useQueries({
    queries: zaTisk.map((dogodek) => ({
      queryKey: ['dogodek', dogodek.id],
      queryFn: () => dogodkiApi.mreza(dogodek.id),
    })),
  })

  return (
    <ListkiZreba
      nazaj={`/turnirji/${idTurnirja}`}
      nazajOznaka="← Nazaj na turnir"
      naslov={turnir.data ? `Žreb skupin · ${turnir.data.ime}` : null}
      turnir={turnir.data}
      nalaganje={turnir.isPending || dogodki.isPending || mreze.some((m) => m.isPending)}
      napaka={turnir.error ?? dogodki.error ?? mreze.find((m) => m.error)?.error ?? null}
      mreze={mreze.flatMap((m) => (m.data ? [m.data] : []))}
    />
  )
}

/* Kaj nosi listek pod tabelo; izbira velja za ta natis. Samodejno: do šest
   igralcev razpored z nizi, večja skupina samo pare - po kategorijah, zato se
   izbira ne shranjuje. */
const SAMODEJNO = 'samodejno'
const NACINI: { vrednost: NacinListka | typeof SAMODEJNO; oznaka: string }[] = [
  { vrednost: SAMODEJNO, oznaka: 'samodejno po velikosti skupine' },
  { vrednost: 'nizi', oznaka: 'razpored z nizi' },
  { vrednost: 'razpored', oznaka: 'samo razpored parov' },
]

function ListkiZreba({
  nazaj,
  nazajOznaka,
  naslov,
  turnir,
  nalaganje,
  napaka,
  mreze,
}: {
  nazaj: string
  nazajOznaka: string
  /* Naslov zavihka. */
  naslov: string | null
  turnir: TurnirDto | undefined
  nalaganje: boolean
  napaka: Error | null
  mreze: MrezaDto[]
}) {
  const [izbranNacin, izberiNacin] = useState<NacinListka | null>(null)
  const listki = useMemo(
    () =>
      listkiSkupin(
        mreze.filter((mreza) => mreza.skupine.length > 0),
        turnir,
        izbranNacin,
      ),
    [mreze, turnir, izbranNacin],
  )
  const listi = useMemo(() => zloziNaListe(listki), [listki])
  const steviloSkupin = new Set(listki.map((listek) => listek.skupina.id)).size

  return (
    <ZrebOkvir
      nazaj={nazaj}
      nazajOznaka={nazajOznaka}
      naslov={naslov}
      nalaganje={nalaganje}
      napaka={napaka}
      stevec={`${steviloSkupin} ${sklonSkupin(steviloSkupin)} · ${listi.length} ${sklonListov(listi.length)}`}
      steviloZaTisk={steviloSkupin}
      prazno="Ni skupin za tisk. Listki skupin se natisnejo, ko je žreb izveden in tekmovanje teče."
      razred="zreb-stran--skupine"
      orodja={
        <label className="listki-orodja__izbira listki-orodja__izbira--zraven">
          Pod tabelo:
          <select
            value={izbranNacin ?? SAMODEJNO}
            onChange={(d) =>
              izberiNacin(d.target.value === SAMODEJNO ? null : (d.target.value as NacinListka))
            }
          >
            {NACINI.map((n) => (
              <option key={n.vrednost} value={n.vrednost}>
                {n.oznaka}
              </option>
            ))}
          </select>
        </label>
      }
      navodilo={
        <>
          Natisni na A4 v merilu 100 %, brez robov ter brez glave in noge. Vsaka skupina je svoj
          listek čez vso širino lista; list razreži po vodoravnih črtkanih črtah.
        </>
      }
    >
      <ListiSkupin listi={listi} logotip={turnir?.logotip ?? null} />
    </ZrebOkvir>
  )
}
