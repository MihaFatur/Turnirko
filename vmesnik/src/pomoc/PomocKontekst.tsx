/* Stanje pomoči za vso aplikacijo: kdo jo vidi, ali so gumbi »?« vklopljeni,
   kateri oblaček je odprt in kaj kaže predal.

   Ponudnik stoji nad vsemi potmi (tudi nad stranmi za tisk, ki so zunaj
   Postavitve), zato gumb »?« deluje povsod in predal ostane odprt, ko se
   organizator premakne na drugo stran.

   Tipko Escape lovi ponudnik sam, v fazi zajema (capture) na oknu: ModalnoOkno
   posluša Escape v fazi mehurčka in bi se sicer zaprlo skupaj z oblačkom, ki
   je bil odprt v njem. Najprej se zapre oblaček, nato predal; okna pod njima
   ostanejo. */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { PredalPomoci } from './PredalPomoci'

export interface MestoVPomoci {
  clanek: string
  razdelek?: string
}

interface Pomoc {
  /* Admin ali potrjen organizator (odločitev lastnika: gledalec pomoči ne vidi). */
  vidna: boolean
  jeAdmin: boolean
  namigiVklopljeni: boolean
  nastaviNamige: (vklopljeni: boolean) => void
  odprtNamig: string | null
  nastaviOdprtNamig: (kljuc: string | null) => void
  predal: MestoVPomoci[]
  odpriVPredalu: (mesto: MestoVPomoci, nadaljuj?: boolean) => void
  nazajVPredalu: () => void
  zapriPredal: () => void
}

const PomocKontekst = createContext<Pomoc | null>(null)

const KLJUC_NAMIGOV = 'turnirko-namigi'

/* Shramba je udobje (izkušen organizator gumbov ne želi), ne pravilo: v
   zasebnem oknu ali ob zaklenjeni shrambi so gumbi pač vklopljeni. */
function preberiNamige(): boolean {
  try {
    return window.localStorage.getItem(KLJUC_NAMIGOV) !== '0'
  } catch {
    return true
  }
}

function zapisiNamige(vklopljeni: boolean) {
  try {
    if (vklopljeni) window.localStorage.removeItem(KLJUC_NAMIGOV)
    else window.localStorage.setItem(KLJUC_NAMIGOV, '0')
  } catch {
    /* brez shrambe velja samo za to sejo */
  }
}

export function PomocPonudnik({ children }: { children: ReactNode }) {
  const { jeAdmin, jeOrganizator } = useAvtentikacija()
  const vidna = jeAdmin || jeOrganizator
  const [namigiVklopljeni, nastaviNamigeStanje] = useState(preberiNamige)
  const [odprtNamig, nastaviOdprtNamig] = useState<string | null>(null)
  /* Sklad: povezava v predalu doda mesto, »Nazaj« ga vzame. */
  const [predal, nastaviPredal] = useState<MestoVPomoci[]>([])

  const nastaviNamige = useCallback((vklopljeni: boolean) => {
    zapisiNamige(vklopljeni)
    nastaviNamigeStanje(vklopljeni)
    if (!vklopljeni) nastaviOdprtNamig(null)
  }, [])

  const odpriVPredalu = useCallback((mesto: MestoVPomoci, nadaljuj = false) => {
    nastaviOdprtNamig(null)
    nastaviPredal((prej) => (nadaljuj ? [...prej, mesto] : [mesto]))
  }, [])

  const nazajVPredalu = useCallback(() => nastaviPredal((prej) => prej.slice(0, -1)), [])
  const zapriPredal = useCallback(() => nastaviPredal([]), [])

  const predalOdprt = predal.length > 0
  useEffect(() => {
    if (odprtNamig === null && !predalOdprt) return
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key !== 'Escape') return
      dogodek.stopPropagation()
      if (odprtNamig !== null) nastaviOdprtNamig(null)
      else nastaviPredal([])
    }
    window.addEventListener('keydown', obTipki, true)
    return () => window.removeEventListener('keydown', obTipki, true)
  }, [odprtNamig, predalOdprt])

  /* Odjava sredi branja: pomoč izgine skupaj s pravicami. */
  useEffect(() => {
    if (!vidna) {
      nastaviPredal([])
      nastaviOdprtNamig(null)
    }
  }, [vidna])

  const vrednost = useMemo<Pomoc>(
    () => ({
      vidna,
      jeAdmin,
      namigiVklopljeni,
      nastaviNamige,
      odprtNamig,
      nastaviOdprtNamig,
      predal,
      odpriVPredalu,
      nazajVPredalu,
      zapriPredal,
    }),
    [vidna, jeAdmin, namigiVklopljeni, nastaviNamige, odprtNamig, predal, odpriVPredalu, nazajVPredalu, zapriPredal],
  )

  return (
    <PomocKontekst.Provider value={vrednost}>
      {children}
      {vidna && predalOdprt && <PredalPomoci />}
    </PomocKontekst.Provider>
  )
}

/* Gumb »?« v komponenti, ki se izriše tudi zunaj ponudnika (testi, razvojni
   prikazi), se takrat preprosto ne pokaže. */
export function usePomocNeobvezno(): Pomoc | null {
  return useContext(PomocKontekst)
}

export function usePomoc(): Pomoc {
  const pomoc = useContext(PomocKontekst)
  if (!pomoc) throw new Error('usePomoc brez PomocPonudnik')
  return pomoc
}
