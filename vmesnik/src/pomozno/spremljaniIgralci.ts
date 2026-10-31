/* Izbor igralcev, ki jih račun spremlja — ena pot za profil in domačo stran.

   Izbor je osebna nastavitev računa in živi na strežniku
   (`/domov/moji-igralci`), kot spremljane lige. Gost ga nima: gumb »Spremljaj«
   mu odpre kratko registracijo (glej `preklopi`). Za razliko od lig preklop NI
   optimističen: meja števila je pravilo paketa (strežnik ob doseženi meji
   vrne 409 s sporočilom, koga račun že spremlja) in kvadratek, ki bi se
   prevesil in nato vrnil, je slabši od kvadratka, ki počaka na odgovor.
   Zapisi gredo skozi isto modulsko vrsto kot spremljane lige — pisec baze je
   en sam. */
import { useCallback } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { domovApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { vVrsto } from './vrstaZahtev'

export interface SpremljanjeIgralcev {
  jePrijavljen: boolean
  /* Id-ji igralcev, ki jih račun spremlja (gostu prazno). */
  spremljani: number[]
  /* Izbor še potuje s strežnika. */
  nalaganje: boolean
  spremljam: (idIgralec: number) => boolean
  /* Preklopi spremljanje. Gostu vrne false in ne stori nič — klicatelj naj
     takrat ponudi račun. */
  preklopi: (idIgralec: number) => boolean
  /* Preklop teče (gumb je do odgovora ugasnjen). */
  zaposlen: boolean
  /* Napaka zadnjega preklopa, npr. dosežena meja. */
  napaka: unknown
  /* Pozabi napako zadnjega preklopa (stran se je preselila k drugemu igralcu). */
  ponastaviNapako: () => void
}

export function useSpremljanjeIgralcev(): SpremljanjeIgralcev {
  const { uporabnik } = useAvtentikacija()
  const odjemalec = useQueryClient()

  const moji = useQuery({
    queryKey: ['moji-igralci'],
    queryFn: domovApi.mojiIgralci,
    enabled: uporabnik !== null,
  })
  const spremljani = uporabnik ? (moji.data ?? []) : []

  const preklop = useMutation<unknown, unknown, { id: number; spremljam: boolean }>({
    mutationFn: ({ id, spremljam }) =>
      vVrsto<unknown>(() =>
        spremljam ? domovApi.nehajSpremljatiIgralca(id) : domovApi.spremljajIgralca(id),
      ),
    onSettled: () => {
      void odjemalec.invalidateQueries({ queryKey: ['moji-igralci'] })
      void odjemalec.invalidateQueries({ queryKey: ['moji-igralci-povzetki'] })
    },
  })

  const preklopi = useCallback(
    (idIgralec: number) => {
      if (!uporabnik) return false
      const trenutni = odjemalec.getQueryData<number[]>(['moji-igralci']) ?? []
      preklop.mutate({ id: idIgralec, spremljam: trenutni.includes(idIgralec) })
      return true
    },
    [uporabnik, odjemalec, preklop],
  )

  return {
    jePrijavljen: uporabnik !== null,
    spremljani,
    nalaganje: uporabnik !== null && moji.isPending,
    spremljam: (idIgralec) => spremljani.includes(idIgralec),
    preklopi,
    zaposlen: preklop.isPending,
    napaka: preklop.error,
    ponastaviNapako: preklop.reset,
  }
}
