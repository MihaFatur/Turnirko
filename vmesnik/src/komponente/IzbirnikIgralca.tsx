/* Izbira igralca iz šifranta z vpisom imena (`IskalniIzbirnik` v vlogi
   dejanja): semafor »Ena na ena«, napoved tekme, kader ekipe v ligi in na
   dogodku.

   Ob imenu stoji klub: brez njega soimenjakov, ki jih je v šifrantu cele
   države precej, ni mogoče ločiti. */
import { useMemo } from 'react'

import type { IgralecDto } from '../api/tipi'
import { IskalniIzbirnik } from './IskalniIzbirnik'

export function IzbirnikIgralca({
  oznaka,
  vidnaOznaka = false,
  namig = 'Vpiši ime',
  igralci,
  izkljuci,
  naSpremembo,
}: {
  oznaka: string
  vidnaOznaka?: boolean
  namig?: string
  igralci: IgralecDto[]
  /* Igralec, ki ga ni mogoče izbrati (nasprotna stran oz. lastnik profila):
     sam s sabo se nihče ne primerja. */
  izkljuci: number | ''
  naSpremembo: (id: number) => void
}) {
  const moznosti = useMemo(
    () =>
      igralci
        .filter((i) => i.id !== izkljuci)
        .map((i) => ({ id: i.id, ime: `${i.ime} ${i.priimek}`, podrobnost: i.klub?.ime ?? null })),
    [igralci, izkljuci],
  )

  return (
    <IskalniIzbirnik
      oznaka={oznaka}
      vidnaOznaka={vidnaOznaka}
      namig={namig}
      moznosti={moznosti}
      naIzbiro={naSpremembo}
    />
  )
}
