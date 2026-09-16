/* Klub kot polje obrazca (`IskalniIzbirnik` v vlogi vrednosti). Klubov je v
   registru po uvozu NTZS sedemdeset in več, zato spustni seznam ni šel.
   Prazno polje pomeni »brez kluba« - kjer je klub obvezen, to pove gumb, ki
   ostane ugasnjen. Išče se po imenu in kratici. */
import { useMemo } from 'react'

import type { KlubDto } from '../api/tipi'
import { IskalniIzbirnik } from './IskalniIzbirnik'

export function IzbirnikKluba({
  oznaka = 'Klub',
  namig = 'Vpiši ime kluba',
  klubi,
  izbrano,
  naSpremembo,
}: {
  oznaka?: string
  namig?: string
  klubi: KlubDto[]
  izbrano: number | null
  naSpremembo: (id: number | null) => void
}) {
  const moznosti = useMemo(
    () => klubi.map((k) => ({ id: k.id, ime: k.ime, iskalno: `${k.ime} ${k.kratica ?? ''}` })),
    [klubi],
  )

  return (
    <IskalniIzbirnik
      vObrazcu
      oznaka={oznaka}
      namig={namig}
      moznosti={moznosti}
      izbrano={izbrano}
      naIzbiro={naSpremembo}
      naPraznjenje={() => naSpremembo(null)}
    />
  )
}
