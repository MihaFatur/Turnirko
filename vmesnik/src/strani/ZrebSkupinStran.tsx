/* Natisljiv žreb skupin za vrata dvorane: vse skupine z igralci in tekmami po
   kolih. Kot listki je stran namenoma zunaj skupne postavitve (brez
   navigacije), da je natis čist, in jo tiska brskalnik (window.print) -
   zaledje ne rabi ničesar svojega, vse je že v mreži dogodka.

   Dve poti, ista vsebina:
   - /dogodki/:id/zreb-skupin  - ena kategorija,
   - /turnirji/:id/zreb-skupin - vse kategorije turnirja, ki imajo izžrebane
     skupine, vsaka na svoji strani (na vrata gre žreb cele dvorane). */
import { useQueries, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { MrezaDto, TurnirDto } from '../api/tipi'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZrebSkupinDogodka } from '../komponente/ZrebSkupinTisk'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { sklonSkupin } from '../pomozno/oblikovanje'
import {
  imaZrebanoSkupine,
  sklopiZrebSkupin,
  steviloSkupinVSklopih,
} from '../pomozno/zrebSkupin'

export function ZrebSkupinDogodkaStran() {
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
    <ListZreba
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
    <ListZreba
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

function ListZreba({
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
  /* Naslov zavihka; brskalnik ga natisne v glavo lista. */
  naslov: string | null
  turnir: TurnirDto | undefined
  nalaganje: boolean
  napaka: Error | null
  mreze: MrezaDto[]
}) {
  useNaslovStrani(naslov)

  if (nalaganje) return <p className="obvestilo">Nalaganje …</p>
  if (napaka) return <SporociloNapake napaka={napaka} />

  const steviloSkupin = mreze.reduce(
    (vsota, mreza) => vsota + steviloSkupinVSklopih(sklopiZrebSkupin(mreza)),
    0,
  )

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={nazaj} className="povezava-nazaj">
          {nazajOznaka}
        </Link>
        <span className="listki-orodja__stevec">
          {steviloSkupin} {sklonSkupin(steviloSkupin)}
        </span>
        <button
          className="gumb gumb--glavni"
          disabled={steviloSkupin === 0}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div className="listki-stran zreb-stran">
        {steviloSkupin === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni skupin za tisk. Žreb skupin se natisne, ko je izveden in tekmovanje teče.
          </p>
        ) : (
          mreze
            .filter((mreza) => mreza.skupine.length > 0)
            .map((mreza) => (
              <ZrebSkupinDogodka key={mreza.dogodek.id} mreza={mreza} turnir={turnir} />
            ))
        )}
      </div>
    </>
  )
}
