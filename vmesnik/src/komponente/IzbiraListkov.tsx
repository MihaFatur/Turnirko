/* Izbira, katere listke (ekipne zapisnike) tekem natisniti, in sled tiska (stanje in logika;
   izris je v ListkiPloskev, prenova 23. 11. 2026).

   Organizator listke natisne ze med igro, rezultat pa se vnese sele pozneje.
   Zato Turnirko pomni, katere listke je organizator ze natisnil: privzeta
   izbira je kolo (ali vrstica izlocilnega dela), ki je na vrsti, natisnjeni pa so
   v tabeli sivi, da jih ni treba iskati med ostalimi. Izbiro je mogoce zozati na
   skupino, kolo ali posamezen listek.

   Brskalnik ne pove, ali je tisk res stekel (okno za tisk se da preklicati),
   zato se tekme oznacijo kot natisnjene sele, ko organizator po tisku to
   potrdi. Oznaka je samo opomnik: tekme ne spremeni, listek se da natisniti
   znova, oznako pa odstrani povezava »Odznaci natisnjene«. */
import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi } from '../api/zahteve'
import { idjiNaVrsti, type PripravljenListek, type RazvrstitevListkov } from '../pomozno/listki'
import { sklonListkov, sklonZapisnikov } from '../pomozno/oblikovanje'

/* Listek posamicne tekme ali ekipni zapisnik: razlikujeta se samo besedi. */
export type VrstaListka = 'listek' | 'zapisnik'

export const BESEDILA: Record<
  VrstaListka,
  { sklon: (n: number) => string; mnozina: string; tozilnik: string }
> = {
  listek: { sklon: sklonListkov, mnozina: 'listkov', tozilnik: 'listke' },
  zapisnik: { sklon: sklonZapisnikov, mnozina: 'zapisnikov', tozilnik: 'zapisnike' },
}

export interface Izbira {
  /* Id-ji izbranih tekem (samo tiste, ki jih seznam se pozna). */
  izbrani: ReadonlySet<number>
  izbraniListki: PripravljenListek[]
  nastavi: (idji: number[]) => void
  /* Doda ali odstrani tekme iz izbire. */
  preklopi: (idji: number[], vklopljeno: boolean) => void
  /* Natisne izbrano in nato vpraša, ali je bil tisk res opravljen. */
  natisni: () => void
  /* Tekme, za katere cakamo potrditev tiska. */
  caka: number[] | null
  potrdi: (natisnil: boolean) => void
  odznaci: (idji: number[]) => void
  oznacuje: boolean
  napaka: Error | null
  smemOznacevati: boolean
}

/* Izbira vrstnega reda skupinskega dela; stran jo da samo, kadar ima smisel
   (vsaj dve skupini s pripravljenimi tekmami). */
export interface IzbiraRazvrstitve {
  vrednost: RazvrstitevListkov
  nastavi: (r: RazvrstitevListkov) => void
}

export function useIzbiraListkov(
  idDogodka: number,
  listki: PripravljenListek[],
  /* Sled tiska smeta pisati samo organizator in admin; gost listke natisne brez nje. */
  smemOznacevati: boolean,
): Izbira {
  const odjemalec = useQueryClient()
  /* null = privzeto: nenatisnjeni listki enote, ki je na vrsti. Ko organizator karkoli
     spremeni, je izbira izrecna. Tekma, ki je medtem odpadla (odigrana), iz izbire izgine. */
  const [izrecna, nastaviIzrecno] = useState<ReadonlySet<number> | null>(null)
  const [caka, nastaviCaka] = useState<number[] | null>(null)

  const izbrani = useMemo(() => {
    const veljavni = new Set(listki.map((l) => l.tekma.id))
    const osnova = izrecna ?? idjiNaVrsti(listki)
    return new Set([...osnova].filter((id) => veljavni.has(id)))
  }, [izrecna, listki])
  const izbraniListki = useMemo(() => listki.filter((l) => izbrani.has(l.tekma.id)), [listki, izbrani])

  const oznaci = useMutation({
    mutationFn: (v: { idji: number[]; natisnjeno: boolean }) =>
      dogodkiApi.oznaciListke(idDogodka, v.idji, v.natisnjeno),
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['dogodek', idDogodka] }),
  })

  const nastavi = (idji: number[]) => nastaviIzrecno(new Set(idji))
  const preklopi = (idji: number[], vklopljeno: boolean) => {
    const nova = new Set(izbrani)
    for (const id of idji) {
      if (vklopljeno) nova.add(id)
      else nova.delete(id)
    }
    nastaviIzrecno(nova)
  }

  const natisni = () => {
    const idji = izbraniListki.map((l) => l.tekma.id)
    /* V vecini brskalnikov klic pocaka, da se okno za tisk zapre. */
    window.print()
    if (smemOznacevati && idji.length > 0) nastaviCaka(idji)
  }

  const potrdi = (natisnil: boolean) => {
    const idji = caka
    nastaviCaka(null)
    /* Po potrditvi se izbira sama prestavi na naslednje kolo z nenatisnjenimi. */
    if (natisnil && idji) {
      oznaci.mutate({ idji, natisnjeno: true }, { onSuccess: () => nastaviIzrecno(null) })
    }
  }

  return {
    izbrani,
    izbraniListki,
    nastavi,
    preklopi,
    natisni,
    caka,
    potrdi,
    odznaci: (idji) => oznaci.mutate({ idji, natisnjeno: false }),
    oznacuje: oznaci.isPending,
    napaka: oznaci.error,
    smemOznacevati,
  }
}
