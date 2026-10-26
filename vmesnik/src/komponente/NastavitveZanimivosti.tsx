/* Organizatorjev obrazec na zavihku »Zanimivosti«: kateri razdelki so gledalcem
   vidni.

   Kljukica pomeni »vidno gledalcem« (privzeto so vsi), zato je skrit razdelek
   odkljukan. Obrazec je zložen, da ne odriva samih zanimivosti, vsak preklop pa
   se shrani takoj. Zavihek pod njim kaže natanko to, kar vidi gledalec: strežnik
   skritih razdelkov v izpisu sploh ne pošlje. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, turnirjiApi } from '../api/zahteve'
import type { PostavkaZanimivosti } from '../api/tipi'
import { NASLOVI_RAZDELKOV } from '../pomozno/zanimivosti'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  vrsta: 'turnir' | 'liga'
  id: number
}

export function NastavitveZanimivosti({ vrsta, id }: Lastnosti) {
  const odjemalec = useQueryClient()
  const [odprto, nastaviOdprto] = useState(false)
  const kljuc = [vrsta, id, 'zanimivosti-nastavitve']

  const nastavitve = useQuery({
    queryKey: kljuc,
    queryFn: () =>
      vrsta === 'turnir'
        ? turnirjiApi.zanimivostiNastavitve(id)
        : ligeApi.zanimivostiNastavitve(id),
  })

  const shrani = useMutation({
    mutationFn: (skrite: PostavkaZanimivosti[]) =>
      vrsta === 'turnir'
        ? turnirjiApi.shraniZanimivostiNastavitve(id, skrite)
        : ligeApi.shraniZanimivostiNastavitve(id, skrite),
    onSuccess: (odgovor) => {
      odjemalec.setQueryData(kljuc, odgovor)
      /* Zavihek se mora izrisati znova - brez pravkar skritega razdelka. */
      odjemalec.invalidateQueries({ queryKey: [vrsta, id, 'statistika'] })
    },
  })

  const razdelki = nastavitve.data?.razdelki ?? []
  const skrite = nastavitve.data?.skrite ?? []

  function preklopi(razdelek: PostavkaZanimivosti) {
    shrani.mutate(
      skrite.includes(razdelek)
        ? skrite.filter((r) => r !== razdelek)
        : [...skrite, razdelek],
    )
  }

  const povzetek =
    skrite.length === 0 ? 'vse vidno' : `skritih: ${skrite.length} od ${razdelki.length}`

  return (
    <details
      className="zanimivosti-nastavitve"
      open={odprto}
      onToggle={(e) => nastaviOdprto(e.currentTarget.open)}
    >
      <summary className="zanimivosti-nastavitve__glava">
        <span className="zanimivosti-nastavitve__naslov">Kaj vidijo ostali</span>
        <span className="zanimivosti-nastavitve__meta">
          {povzetek}{' '}
          <span aria-hidden="true">{odprto ? '▴' : '▾'}</span>
        </span>
      </summary>

      <p className="zanimivosti-nastavitve__pojasnilo">
        Odkljukan razdelek gledalcu ni viden. Podatki ostanejo, zato ga lahko kadarkoli
        spet vklopite.
      </p>
      <NapakaPoizvedbe poizvedba={nastavitve} kaj="nastavitev" />
      <SporociloNapake napaka={shrani.error} />

      <div className="zanimivosti-nastavitve__seznam">
        {razdelki.map((razdelek) => (
          <label key={razdelek} className="obrazec__polje obrazec__polje--stikalo">
            <input
              type="checkbox"
              checked={!skrite.includes(razdelek)}
              disabled={shrani.isPending}
              onChange={() => preklopi(razdelek)}
            />
            <span>{NASLOVI_RAZDELKOV[razdelek]}</span>
          </label>
        ))}
      </div>
    </details>
  )
}
