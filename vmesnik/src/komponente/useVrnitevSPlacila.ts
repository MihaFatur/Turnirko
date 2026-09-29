/* Vrnitev s Stripe Checkouta na stran »Naročnina« (`?stanje=uspeh|preklic`),
   skupna igralcu in organizatorju.

   Parameter se takoj pobriše, da osvežitev strani sporočila ne ponovi. Ob
   uspehu naročnina včasih še ni zapisana (webhook je počasnejši od
   preusmeritve), zato se nekajkrat povpraša znova, ne pa v neskončnost. */
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import type { NarocninaDto } from '../api/tipi'
import { narocninaApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

export interface Obvestilo {
  besedilo: string
  /* Zelen trak (uspeh) ali nevtralen (npr. preklicano plačilo). */
  uspeh: boolean
  /* Zabeležena sprememba se da umakniti, preklic ali obnova ne. */
  razveljavi?: boolean
}

/* Stripe potrdi plačilo prek webhooka, ki lahko pride nekaj sekund za
   vrnitvijo brskalnika. */
const POSKUSI_PO_VRNITVI = 8
const ODMIK_POSKUSA_MS = 1500

export interface BesedilaVrnitve {
  /* Dokler naročnina še ni zapisana. */
  caka: string
  /* Ko je naročnina aktivna. Mora biti stabilna (modulska) funkcija: gre v odvisnosti učinka. */
  uspeh: (narocnina: NarocninaDto) => string
}

export function useVrnitevSPlacila(besedila: BesedilaVrnitve): Obvestilo | null {
  const { osvezi } = useAvtentikacija()
  const odjemalec = useQueryClient()
  const [iskalniNiz, nastaviIskalniNiz] = useSearchParams()
  const vrnitev = iskalniNiz.get('stanje')
  const [obvestilo, nastaviObvestilo] = useState<Obvestilo | null>(null)
  const { caka, uspeh } = besedila

  useEffect(() => {
    if (vrnitev !== 'uspeh' && vrnitev !== 'preklic') return
    nastaviIskalniNiz({}, { replace: true })

    if (vrnitev === 'preklic') {
      nastaviObvestilo({ besedilo: 'Plačilo je bilo preklicano. Nič ti nismo zaračunali.', uspeh: false })
      return
    }

    nastaviObvestilo({ besedilo: caka, uspeh: true })
    let opusceno = false
    let poskus = 0
    const povprasaj = async () => {
      if (opusceno) return
      try {
        const n = await narocninaApi.pregled()
        odjemalec.setQueryData(['narocnina'], n)
        if (n.aktivna) {
          void osvezi()
          // meje paketa so se spremenile: nadzorna plošča jih bere znova
          void odjemalec.invalidateQueries({ queryKey: ['organizator-pregled'] })
          nastaviObvestilo({ besedilo: uspeh(n), uspeh: true })
          return
        }
      } catch {
        /* povezava je pobegnila - poskusimo znova, dokler jih je */
      }
      poskus += 1
      if (poskus < POSKUSI_PO_VRNITVI) setTimeout(povprasaj, ODMIK_POSKUSA_MS)
    }
    void povprasaj()
    return () => {
      opusceno = true
    }
  }, [vrnitev, nastaviIskalniNiz, odjemalec, osvezi, caka, uspeh])

  return obvestilo
}
