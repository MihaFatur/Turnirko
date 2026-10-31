/* Ponudnik oglasa »Igralec Premium«: en sam oglas za celotno aplikacijo.

   Oglas sproži klik na zaklenjeno funkcijo (zasebna statistika; spremljanje lig
   je od 23. 10. 2026 brezplačno); klicatelj pokliče `odpri(kontekst)`, ponudnik izbere različico
   in izriše oglas čez celo stran. Okno registracije in prijave živi TU in ne v
   oglasu: oglas ob kliku na »Ustvari račun« izgine, okno pa mora ostati.

   Zakaj en ponudnik in ne oglas v vsaki strani: sprožilcev je lahko več (zdaj
   obvestilo na lastnem profilu), oglas pa je vedno
   isti, zato mora biti odprt največ enkrat naenkrat in izbira različice na
   enem mestu.

   Tu živi tudi poziv k računu (PozivRacuna.tsx) in pot do registracije za
   druga mesta (vrstica »To si ti?« na profilu, »Spremljaj« gosta na strani lige): vsa okna gosta so v enem
   ponudniku, zato se nikoli ne odpreta dve naenkrat. */
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
import type { IzvorRegistracije } from '../pomozno/statistika'
import { useUvodKoncan } from '../pomozno/uvod'
import { PozivRacuna } from './PozivRacuna'
import { PremiumOglas } from './PremiumOglas'
import { PrijavaOkno } from './PrijavaOkno'
import type { IgralecZaSpremljanje, LigaZaSpremljanje, PredizbranIgralec } from './RegistracijaTok'

/* Registracija gosta z drugega mesta kot oglas: kratka pot (brez korakov
   »Vloga« in »Paket«: igralec, brezplačno s preizkusom) in po želji igralec,
   s katerega profila je gost prišel (vpisano ime in klub). */
export interface MoznostiRegistracije {
  izvor: IzvorRegistracije
  predizbran?: PredizbranIgralec
  liga?: LigaZaSpremljanje
  /* Igralec, ob katerem je gost kliknil »Spremljaj« (profil); po prijavi ga račun spremlja. */
  igralec?: IgralecZaSpremljanje
}

interface Vrednost {
  /* Pokaže oglas za dano funkcijo; ob vsakem odprtju se naključno izbere ena od
     treh različic. */
  odpri: (kontekst: KontekstOglasa) => void
  odpriRegistracijo: (moznosti: MoznostiRegistracije) => void
  odpriPrijavo: () => void
}

const PremiumOglasKontekst = createContext<Vrednost>({
  odpri: () => {},
  odpriRegistracijo: () => {},
  odpriPrijavo: () => {},
})

export function usePremiumOglas(): Vrednost {
  return useContext(PremiumOglasKontekst)
}

interface OdprtOglas {
  kontekst: KontekstOglasa
  razlicica: RazlicicaOglasa
}

/* Okno za gosta: registracija na koraku »Paket« z izbranim ciklom (oglas),
   kratka registracija (poziv, profil) ali prijava. */
type OknoGosta =
  | { nacin: 'registracija'; ciklus: CiklusPlacila }
  | { nacin: 'kratka'; moznosti: MoznostiRegistracije }
  | { nacin: 'prijava' }

/* Razvojni pregled brez klika: `?oglas=1a` (ali 1b, 1c). Enkrat na
   nalaganje strani, kot `?maskota`. */
function predogledIzNaslova(): OdprtOglas | null {
  if (typeof window === 'undefined') return null
  const parametri = new URLSearchParams(window.location.search)
  const razlicica = parametri.get('oglas')
  if (!razlicica) return null
  const izbrana = RAZLICICE.find((r) => r === razlicica)
  if (!izbrana) return null
  return { kontekst: 'statistika', razlicica: izbrana }
}

export function PremiumOglasPonudnik({ children }: { children: ReactNode }) {
  const { nalaganje } = useAvtentikacija()
  /* Med uvodno animacijo se oglas ne pokaže; odprt (predogled ali klik po
     pristanku logotipa) počaka na njen konec. */
  const uvodKoncan = useUvodKoncan()
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

  const odpriRegistracijo = useCallback((moznosti: MoznostiRegistracije) => {
    nastaviOknoGosta({ nacin: 'kratka', moznosti })
  }, [])
  const odpriPrijavo = useCallback(() => nastaviOknoGosta({ nacin: 'prijava' }), [])

  const vrednost = useMemo<Vrednost>(
    () => ({ odpri, odpriRegistracijo, odpriPrijavo }),
    [odpri, odpriRegistracijo, odpriPrijavo],
  )

  return (
    <PremiumOglasKontekst.Provider value={vrednost}>
      {children}
      {oglas && uvodKoncan && (
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
          zacetniNacin={oknoGosta.nacin === 'prijava' ? 'prijava' : 'registracija'}
          naPaketu={oknoGosta.nacin === 'registracija'}
          zacetniCiklus={oknoGosta.nacin === 'registracija' ? oknoGosta.ciklus : undefined}
          kratko={oknoGosta.nacin === 'kratka'}
          izvor={oknoGosta.nacin === 'kratka' ? oknoGosta.moznosti.izvor : 'oglas'}
          predizbran={oknoGosta.nacin === 'kratka' ? oknoGosta.moznosti.predizbran : undefined}
          liga={oknoGosta.nacin === 'kratka' ? oknoGosta.moznosti.liga : undefined}
          igralec={oknoGosta.nacin === 'kratka' ? oknoGosta.moznosti.igralec : undefined}
          onZapri={() => nastaviOknoGosta(null)}
        />
      )}
      <PozivRacuna
        zaseden={oglas !== null || oknoGosta !== null}
        onRegistracija={() => odpriRegistracijo({ izvor: 'poziv' })}
        onPrijava={odpriPrijavo}
      />
    </PremiumOglasKontekst.Provider>
  )
}
