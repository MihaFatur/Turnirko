/* Izbor lig, ki jih uporabnik spremlja — ena sama pot za vse strani.

   Izbor je osebna nastavitev računa in živi na strežniku (`/domov/moje-lige`).
   Gost ga nima, zato mu namesto izbora pripada spomin njegovega brskalnika
   (zadnje ogledane lige). Kavelj je skupen, ker isto stanje berejo domača
   stran, okno za urejanje izbora in stran lige — preklop pa mora biti povsod
   enak, vključno z optimistično posodobitvijo. */
import { useCallback, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { domovApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ogledaneLige } from './ogledaneLige'

/* Preklop ene lige; "spremljam" je stanje PRED klikom. */
interface Preklop {
  id: number
  spremljam: boolean
}

/* Zahteve preklopa gredo ena za drugo. SQLite prenese enega pisca naenkrat -
   dva hkratna zapisa strežnik vrne kot 500 - v oknu za izbor pa gledalec
   odkljuka več lig v sekundi. Vrsta je modulska in ne v kavlju: okno za izbor
   in domača stran sta oba na zaslonu in vsak svoj klic kavlja, pisec baze pa
   je vseeno en sam.

   (Vgrajeni "scope" TanStack Queryja tu ne pomaga: če se prva zahteva konča,
   preden druga pride do svojega premora, se znak za nadaljevanje izgubi in
   vrsta obstane.) */
let vrsta: Promise<unknown> = Promise.resolve()

function vVrsto<T>(opravilo: () => Promise<T>): Promise<T> {
  const naVrsti = vrsta.then(opravilo, opravilo)
  /* Napaka ene zahteve ne sme podreti vrste za naslednje. */
  vrsta = naVrsti.catch(() => undefined)
  return naVrsti
}

export interface SpremljanjeLig {
  /* Izbor je last računa; gost ga nima in mu ga ni mogoče ponuditi. */
  jePrijavljen: boolean
  /* Id-ji lig, ki jih sklop "Moje lige" pokaže. */
  spremljane: number[]
  /* Izbor še potuje s strežnika (gost ga nima, zato je zanj vedno false). */
  nalaganje: boolean
  spremljam: (idLiga: number) => boolean
  /* Preklopi spremljanje. Gostu vrne false in ne stori nič — klicatelj naj
     takrat ponudi prijavo. */
  preklopi: (idLiga: number) => boolean
  /* Napaka zadnjega preklopa (izbor se je medtem že povrnil na stanje
     strežnika), da jo stran lahko pokaže. */
  napaka: unknown
}

export function useSpremljanjeLig(): SpremljanjeLig {
  const { uporabnik } = useAvtentikacija()
  const odjemalec = useQueryClient()

  const mojeLige = useQuery({
    queryKey: ['moje-lige'],
    queryFn: domovApi.mojeLige,
    enabled: uporabnik !== null,
  })

  /* Gostov spomin preberemo enkrat in ne ob vsakem izrisu: novo polje ob
     vsakem izrisu bi bilo nov ključ poizvedbe povzetkov in ta bi se osveževala
     brez konca. */
  const ogledane = useMemo(() => ogledaneLige(), [uporabnik])
  const spremljane = uporabnik ? (mojeLige.data ?? []) : ogledane

  /* Optimistična posodobitev: kvadratek se prevesi takoj (v vrsto gre samo
     klic strežniku), ob napaki se izbor povrne na zadnje potrjeno stanje
     strežnika. */
  const preklop = useMutation<unknown, unknown, Preklop, { prejsnje: number[] }>({
    mutationFn: ({ id, spremljam }) =>
      vVrsto<unknown>(() =>
        spremljam ? domovApi.nehajSpremljati(id) : domovApi.spremljaj(id),
      ),
    onMutate: ({ id, spremljam }) => {
      /* Tekoča osvežitev ne sme povoziti optimističnega stanja. Preklica NE
         čakamo: predpomnilnik mora dobiti novo vrednost še v istem odzivu na
         klik, ker prav iz njega naslednji klik prebere, ali ligo že
         spremljam. */
      void odjemalec.cancelQueries({ queryKey: ['moje-lige'] })
      const prejsnje = odjemalec.getQueryData<number[]>(['moje-lige']) ?? []
      odjemalec.setQueryData<number[]>(
        ['moje-lige'],
        spremljam
          ? prejsnje.filter((v) => v !== id)
          : /* Dvojnik bi bil dvakratna vrstica na domači strani (isti ključ):
               dva hitra klika na isto ligo prebereta isto staro stanje. */
            [...new Set([...prejsnje, id])],
      )
      return { prejsnje }
    },
    onError: (_napaka, _vnos, kontekst) => {
      if (kontekst) odjemalec.setQueryData(['moje-lige'], kontekst.prejsnje)
    },
    onSettled: () => odjemalec.invalidateQueries({ queryKey: ['moje-lige'] }),
  })

  const preklopi = useCallback(
    (idLiga: number) => {
      if (!uporabnik) return false
      const prejsnje = odjemalec.getQueryData<number[]>(['moje-lige']) ?? []
      preklop.mutate({ id: idLiga, spremljam: prejsnje.includes(idLiga) })
      return true
    },
    /* Namenoma beremo predpomnilnik in ne "spremljane": med hitrimi zaporednimi
       kliki je predpomnilnik že posodobljen, zaprta vrednost iz izrisa pa še ne. */
    [uporabnik, odjemalec, preklop],
  )

  return {
    jePrijavljen: uporabnik !== null,
    spremljane,
    nalaganje: uporabnik !== null && mojeLige.isPending,
    spremljam: (idLiga) => spremljane.includes(idLiga),
    preklopi,
    napaka: preklop.error,
  }
}
