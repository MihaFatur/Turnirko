/* Lepljiva glava telefona in mesta, kamor stran vanjo vloži svoje.

   Na telefonu glava ni vec masthead s citalno navigacijo (to vlogo je prevzela
   spodnja vrstica), ampak 56 px visok pas s kontekstom: levo logotip ali
   povezava nazaj, desno dejanja urejevalca in prozilnik uporabnika. Pod debelo
   crto lahko stran doda se svoj naslov in zavihke (stran kategorije), ki morajo
   ostati na zaslonu tudi med drsenjem.

   Vsebina je odvisna od strani, glava pa zivi v Postavitvi - zato stran svoje
   dele odda skozi portale (GlavaDejanja, GlavaNaslov, GlavaZavihki) oz. skozi
   useNazaj. Portal in ne podvojen izris: dejanje se izrise natanko enkrat, zato
   ga bralnik zaslona prebere enkrat.

   Ciljev ni na namizju - tam portali vrnejo null in stran svoja dejanja izrise
   na obicajnem mestu. */
import {
  createContext,
  useContext,
  useEffect,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'

/* Povezava nazaj v levem kotu glave ("← TURNIRJI"). */
export interface Nazaj {
  pot: string
  oznaka: string
  /* Ali ob puscici ostane se kontekst uporabnika. Privzeto ne: podstran ima
     v 390 px pasu poleg glavnega dejanja ("+ Dogodek", "Zreb") in menija
     zanj premalo prostora. Stran, ki glavnega dejanja nima (liga), ga sme
     obdrzati - gledalec tam ostane dlje kot eno potezo. */
  sKontekstom?: boolean
}

interface Vrednost {
  nastaviNazaj: (nazaj: Nazaj | null) => void
  ciljDejanj: HTMLElement | null
  ciljNaslova: HTMLElement | null
  ciljZavihkov: HTMLElement | null
}

const PRAZNA: Vrednost = {
  nastaviNazaj: () => {},
  ciljDejanj: null,
  ciljNaslova: null,
  ciljZavihkov: null,
}

export const GlavaTelefonaKontekst = createContext<Vrednost>(PRAZNA)

function useGlava(): Vrednost {
  return useContext(GlavaTelefonaKontekst)
}

/* Stran pove, kam vodi puscica nazaj. Ob odhodu s strani se oznaka pobrise,
   sicer bi jo naslednja stran podedovala. */
export function useNazaj(pot: string, oznaka: string, sKontekstom = false) {
  const { nastaviNazaj } = useGlava()
  useEffect(() => {
    nastaviNazaj({ pot, oznaka, sKontekstom })
    return () => nastaviNazaj(null)
  }, [nastaviNazaj, pot, oznaka, sKontekstom])
}

export function GlavaDejanja({ children }: { children: ReactNode }) {
  const { ciljDejanj } = useGlava()
  return ciljDejanj ? createPortal(children, ciljDejanj) : null
}

export function GlavaNaslov({ children }: { children: ReactNode }) {
  const { ciljNaslova } = useGlava()
  return ciljNaslova ? createPortal(children, ciljNaslova) : null
}

export function GlavaZavihki({ children }: { children: ReactNode }) {
  const { ciljZavihkov } = useGlava()
  return ciljZavihkov ? createPortal(children, ciljZavihkov) : null
}
