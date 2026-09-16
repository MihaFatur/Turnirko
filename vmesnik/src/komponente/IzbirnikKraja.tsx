/* Kraj kot polje obrazca (`IskalniIzbirnik` v vlogi vrednosti). Šifrant
   poštnih številk ima nekaj sto krajev; vpiše se ime ali poštna številka
   (»3000« najde Celje). Prazno polje pomeni, da kraj ni podan. */
import { useMemo } from 'react'

import type { KrajDto } from '../api/tipi'
import { IskalniIzbirnik } from './IskalniIzbirnik'

export function IzbirnikKraja({
  kraji,
  izbrano,
  naSpremembo,
}: {
  kraji: KrajDto[]
  izbrano: number | null
  naSpremembo: (postnaSt: number | null) => void
}) {
  const moznosti = useMemo(
    () => kraji.map((k) => ({ id: k.postnaSt, ime: `${k.postnaSt} ${k.ime}` })),
    [kraji],
  )

  return (
    <IskalniIzbirnik
      vObrazcu
      oznaka="Kraj"
      namig="Vpiši kraj ali poštno številko"
      moznosti={moznosti}
      izbrano={izbrano}
      naIzbiro={naSpremembo}
      naPraznjenje={() => naSpremembo(null)}
    />
  )
}
