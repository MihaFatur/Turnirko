/* Lige, ki jih je administrator postavil na domačo stran (največ dve).

   Vzporednica kavlja spremljaneLige, a drug pojem: tam je izbor OSEBNA
   nastavitev računa, tu pa uredniška odločitev zveze — katero tekmovanje stoji
   na vhodni strani. Zato ne živi v svoji tabeli: zastavica je last lige
   (`LigaDto.naDomaci`), zato jo bere kar seznam lig, ki ga stran itak naloži,
   preklop pa gre na `PUT/DELETE /lige/{id}/na-domaci` in ga strežnik dovoli
   samo adminu.

   Mejo dveh lig varuje strežnik (LigaStoritev.nastaviNaDomaci); okno jo pozna
   samo zato, da tretje kljukice sploh ne ponudi. */
import { useCallback, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto } from '../api/tipi'
import { vVrsto } from './vrstaZahtev'

/* Isto število kot LigaStoritev.LIG_NA_DOMACI. */
export const LIG_NA_DOMACI = 2

/* Preklop ene lige; "naDomaci" je stanje PRED klikom. */
interface Preklop {
  id: number
  naDomaci: boolean
}

export interface LigeNaDomaci {
  /* Id-ji lig, ki so na domači strani, v vrstnem redu strežnika. */
  izbrane: number[]
  nalaganje: boolean
  preklopi: (idLiga: number) => void
  /* Napaka zadnjega preklopa (izbor se je medtem že povrnil na stanje
     strežnika), da jo okno lahko pokaže. */
  napaka: unknown
}

export function useLigeNaDomaci(): LigeNaDomaci {
  const odjemalec = useQueryClient()
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })

  const izbrane = useMemo(
    () => (lige.data ?? []).filter((l) => l.naDomaci).map((l) => l.id),
    [lige.data],
  )

  /* Optimistična posodobitev: kvadratek se prevesi takoj, ob napaki (npr.
     tretja liga) se seznam povrne na zadnje potrjeno stanje strežnika. */
  const preklop = useMutation<unknown, unknown, Preklop, { prejsnje: LigaDto[] | undefined }>({
    mutationFn: ({ id, naDomaci }) =>
      vVrsto<unknown>(() => (naDomaci ? ligeApi.zDomace(id) : ligeApi.naDomaco(id))),
    onMutate: ({ id, naDomaci }) => {
      /* Preklica NE čakamo: predpomnilnik mora dobiti novo vrednost še v istem
         odzivu na klik (isto pravilo kot pri spremljanih ligah). */
      void odjemalec.cancelQueries({ queryKey: ['lige'] })
      const prejsnje = odjemalec.getQueryData<LigaDto[]>(['lige'])
      odjemalec.setQueryData<LigaDto[]>(['lige'], (staro) =>
        staro?.map((l) => (l.id === id ? { ...l, naDomaci: !naDomaci } : l)),
      )
      return { prejsnje }
    },
    onError: (_napaka, _vnos, kontekst) => {
      if (kontekst?.prejsnje) odjemalec.setQueryData(['lige'], kontekst.prejsnje)
    },
    onSettled: () => {
      void odjemalec.invalidateQueries({ queryKey: ['lige'] })
      /* Sklop na domači strani bere povzetke po svoji poti — brez tega bi
         admin videl novo kljukico in staro ligo. */
      void odjemalec.invalidateQueries({ queryKey: ['domov-lige'] })
    },
  })

  const preklopi = useCallback(
    (idLiga: number) => {
      /* Namenoma beremo predpomnilnik in ne "izbrane": med hitrimi zaporednimi
         kliki je predpomnilnik že posodobljen, zaprta vrednost iz izrisa pa ne. */
      const zdaj = odjemalec.getQueryData<LigaDto[]>(['lige'])
      const liga = zdaj?.find((l) => l.id === idLiga)
      preklop.mutate({ id: idLiga, naDomaci: liga?.naDomaci ?? false })
    },
    [odjemalec, preklop],
  )

  return { izbrane, nalaganje: lige.isPending, preklopi, napaka: preklop.error }
}
