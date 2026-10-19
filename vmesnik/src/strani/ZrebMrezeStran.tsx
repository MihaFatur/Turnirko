/* Natisljiv žreb izločilnega dela za vrata dvorane: mreža z imeni in izidi,
   narisana kot na papirju (ležeči A4). Zasnova kot žreb skupin: stran je zunaj
   skupne postavitve, tiska jo brskalnik (window.print), zaledje ne rabi
   ničesar svojega - vse je že v mreži dogodka.

   Dve poti, ista vsebina:
   - /dogodki/:id/zreb-mreze  - ena kategorija,
   - /turnirji/:id/zreb-mreze - vse kategorije turnirja, ki imajo izločilno
     mrežo, vsaka na svojem listu (na vrata gre žreb cele dvorane). */
import { useQueries, useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { MrezaDto, TurnirDto } from '../api/tipi'
import { ZrebMrezeDogodka } from '../komponente/ZrebMrezeTisk'
import { ZrebOkvir } from '../komponente/ZrebOkvir'
import { sklonListov } from '../pomozno/oblikovanje'
import { imaMrezoZaTisk, jeKandidatZaTiskMreze, sklopiZrebMreze } from '../pomozno/zrebMreze'

export function ZrebMrezeDogodkaStran() {
  const { id } = useParams()
  const idDogodka = Number(id)

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja, datum in kraj za glavo lista; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })

  return (
    <ListMreze
      nazaj={`/dogodki/${idDogodka}`}
      nazajOznaka="← Nazaj na dogodek"
      naslov={mreza.data ? `Izločilni del · ${mreza.data.dogodek.ime}` : null}
      turnir={turnir.data}
      nalaganje={mreza.isPending}
      napaka={mreza.error}
      mreze={mreza.data ? [mreza.data] : []}
    />
  )
}

export function ZrebMrezeTurnirjaStran() {
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
     delita. Naložimo samo dogodke, ki izločilni del sploh imajo. */
  const kandidati = (dogodki.data ?? []).filter(jeKandidatZaTiskMreze)
  const mreze = useQueries({
    queries: kandidati.map((dogodek) => ({
      queryKey: ['dogodek', dogodek.id],
      queryFn: () => dogodkiApi.mreza(dogodek.id),
    })),
  })

  return (
    <ListMreze
      nazaj={`/turnirji/${idTurnirja}`}
      nazajOznaka="← Nazaj na turnir"
      naslov={turnir.data ? `Izločilni del · ${turnir.data.ime}` : null}
      turnir={turnir.data}
      nalaganje={turnir.isPending || dogodki.isPending || mreze.some((m) => m.isPending)}
      napaka={turnir.error ?? dogodki.error ?? mreze.find((m) => m.error)?.error ?? null}
      mreze={mreze.flatMap((m) => (m.data ? [m.data] : []))}
    />
  )
}

function ListMreze({
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
  naslov: string | null
  turnir: TurnirDto | undefined
  nalaganje: boolean
  napaka: Error | null
  mreze: MrezaDto[]
}) {
  const zaTisk = mreze.filter(imaMrezoZaTisk)
  const steviloListov = zaTisk.reduce((vsota, m) => vsota + (sklopiZrebMreze(m)?.listi.length ?? 0), 0)

  return (
    <ZrebOkvir
      nazaj={nazaj}
      nazajOznaka={nazajOznaka}
      naslov={naslov}
      nalaganje={nalaganje}
      napaka={napaka}
      stevec={`${steviloListov} ${sklonListov(steviloListov)}`}
      steviloZaTisk={steviloListov}
      prazno="Ni izločilne mreže za tisk. Izločilni del se natisne, ko je izžreban in tekmovanje teče."
      lezeci
    >
      {zaTisk.map((mreza) => (
        <ZrebMrezeDogodka key={mreza.dogodek.id} mreza={mreza} turnir={turnir} />
      ))}
    </ZrebOkvir>
  )
}
