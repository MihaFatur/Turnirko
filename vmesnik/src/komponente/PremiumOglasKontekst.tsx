/* Ponudnik oglasa »Igralec Premium«: en sam oglas za celotno aplikacijo.

   Oglas sproži klik na zaklenjeno funkcijo (spremljanje in urejanje lig, zasebna
   statistika); klicatelj pokliče `odpri(kontekst)`, ponudnik izbere različico
   in izriše oglas čez celo stran. Okno registracije in prijave živi TU in ne v
   oglasu: oglas ob kliku na »Ustvari račun« izgine, okno pa mora ostati.

   Zakaj en ponudnik in ne oglas v vsaki strani: sprožilcev je več (domača
   stran, stran lige, profil, kavelj `useSpremljanjeLig`), oglas pa je vedno
   isti, zato mora biti odprt največ enkrat naenkrat in izbira različice na
   enem mestu. */
import {
  type ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import type { CiklusPlacila } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  RAZLICICE,
  nakljucnaRazlicica,
  type KontekstOglasa,
  type RazlicicaOglasa,
} from '../pomozno/premiumOglas'
import { PremiumOglas } from './PremiumOglas'
import { PrijavaOkno } from './PrijavaOkno'

interface Vrednost {
  /* Pokaže oglas za dano funkcijo; ob vsakem odprtju se naključno izbere ena od
     treh različic. */
  odpri: (kontekst: KontekstOglasa) => void
}

const PremiumOglasKontekst = createContext<Vrednost>({ odpri: () => {} })

export function usePremiumOglas(): Vrednost {
  return useContext(PremiumOglasKontekst)
}

interface OdprtOglas {
  kontekst: KontekstOglasa
  razlicica: RazlicicaOglasa
}

/* Okno za gosta: registracija na koraku »Paket« z izbranim ciklom, ali prijava. */
type OknoGosta = { nacin: 'registracija'; ciklus: CiklusPlacila } | { nacin: 'prijava' }

/* Razvojni pregled brez klika: `?oglas=1a` (ali 1b, 1c), po želji
   `&kontekst=lige`. Enkrat na nalaganje strani, kot `?maskota`. */
function predogledIzNaslova(): OdprtOglas | null {
  if (typeof window === 'undefined') return null
  const parametri = new URLSearchParams(window.location.search)
  const razlicica = parametri.get('oglas')
  if (!razlicica) return null
  const izbrana = RAZLICICE.find((r) => r === razlicica)
  if (!izbrana) return null
  return { kontekst: parametri.get('kontekst') === 'lige' ? 'lige' : 'statistika', razlicica: izbrana }
}

export function PremiumOglasPonudnik({ children }: { children: ReactNode }) {
  const { nalaganje } = useAvtentikacija()
  const [oglas, nastaviOglas] = useState<OdprtOglas | null>(null)
  const [oknoGosta, nastaviOknoGosta] = useState<OknoGosta | null>(null)
  const predogledPokazan = useRef(false)

  const odpri = useCallback((kontekst: KontekstOglasa) => {
    nastaviOglas((zdaj) => zdaj ?? { kontekst, razlicica: nakljucnaRazlicica() })
  }, [])

  /* Pregled po naslovu počaka, da je znano, kdo gleda (gost ali igralec):
     oglas gostu in igralcu ponuja drugo dejanje. */
  useEffect(() => {
    if (nalaganje || predogledPokazan.current) return
    const predogled = predogledIzNaslova()
    if (!predogled) return
    predogledPokazan.current = true
    nastaviOglas(predogled)
  }, [nalaganje])

  const zapri = useCallback(() => nastaviOglas(null), [])

  const vrednost = useMemo<Vrednost>(() => ({ odpri }), [odpri])

  return (
    <PremiumOglasKontekst.Provider value={vrednost}>
      {children}
      {oglas && (
        <PremiumOglas
          kontekst={oglas.kontekst}
          razlicica={oglas.razlicica}
          onZapri={zapri}
          onUstvariRacun={(ciklus) => {
            nastaviOglas(null)
            nastaviOknoGosta({ nacin: 'registracija', ciklus })
          }}
          onPrijava={() => {
            nastaviOglas(null)
            nastaviOknoGosta({ nacin: 'prijava' })
          }}
        />
      )}
      {oknoGosta && (
        <PrijavaOkno
          zacetniNacin={oknoGosta.nacin}
          naPaketu={oknoGosta.nacin === 'registracija'}
          zacetniCiklus={oknoGosta.nacin === 'registracija' ? oknoGosta.ciklus : undefined}
          onZapri={() => nastaviOknoGosta(null)}
        />
      )}
    </PremiumOglasKontekst.Provider>
  )
}
