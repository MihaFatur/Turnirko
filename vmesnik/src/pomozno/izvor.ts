/* Od kod je gledalec prišel na stran - za povezavo »← …«, ki pelje tja, od
   koder je prišel, in ne vedno na isto mesto. Profil je imel stalno
   »← Lestvica«, čeprav igralec na profil najpogosteje pride iz lige ali
   zapisnika srečanja; povezava ga je odpeljala na lestvico, kjer ni bil.

   Zapis je vezan na ključ lokacije (React Router ga hrani v history.state):
   ob vsakem premiku NAPREJ (PUSH) si nova lokacija zapomni prejšnjo. Korak
   nazaj in naprej v brskalniku zapisa ne spremenita - stran, na katero se
   gledalec vrne, ima še vedno svoj izvor. Zapisi so v sessionStorage, zato
   izvor preživi tudi osvežitev strani (ključ lokacije ostane isti). */
import type { QueryClient } from '@tanstack/react-query'
import { useLocation, useNavigationType } from 'react-router-dom'

import type { LigaDto, MrezaDto, ProfilDto, SrecanjePodrobnoDto, TurnirDto } from '../api/tipi'

const KLJUC_SHRAMBE = 'turnirko-izvori'
/* Dovolj za vsako realno sejo; starejši zapisi odpadejo, da shramba ne raste. */
const NAJVEC_ZAPISOV = 100
/* Daljše ime (turnirji NTZS imajo kategorijo in kraj v imenu) bi povezavo
   prelomilo v tri vrstice. */
const NAJDALJSA_OZNAKA = 36

let izvori: Record<string, string> = preberi()
let zadnja: { kljuc: string; pot: string } | null = null

function preberi(): Record<string, string> {
  try {
    const zapis = JSON.parse(sessionStorage.getItem(KLJUC_SHRAMBE) ?? '{}')
    return zapis && typeof zapis === 'object' ? zapis : {}
  } catch {
    return {}
  }
}

function shrani() {
  const kljuci = Object.keys(izvori)
  for (const k of kljuci.slice(0, Math.max(0, kljuci.length - NAJVEC_ZAPISOV))) delete izvori[k]
  try {
    sessionStorage.setItem(KLJUC_SHRAMBE, JSON.stringify(izvori))
  } catch {
    /* Zasebno okno ali polna shramba: izvor velja do osvežitve. */
  }
}

/* Kliče jo Postavitev - v izrisu in ne v učinku: stran pod njo se izriše v
   istem prehodu in mora izvor poznati že takrat (učinek bi tekel šele za
   njo). Zapis je vezan na ključ lokacije, zato je dvojni izris (StrictMode)
   neškodljiv. */
export function useBelezenjeIzvora(): void {
  const lokacija = useLocation()
  const tip = useNavigationType()
  if (zadnja?.kljuc === lokacija.key) return
  if (zadnja && tip === 'PUSH') {
    izvori = { ...izvori, [lokacija.key]: zadnja.pot }
    shrani()
  }
  zadnja = { kljuc: lokacija.key, pot: lokacija.pathname + lokacija.search }
}

/* Pot (z ?parametri), s katere je gledalec prišel na to stran, ali null, če
   je prišel od zunaj (povezava v sporočilu, zaznamek, nov zavihek). Takrat
   je prejšnji vnos v zgodovini tuja stran ali ga ni - korak nazaj bi
   aplikacijo zapustil. */
export function useIzvor(): string | null {
  const lokacija = useLocation()
  return izvori[lokacija.key] ?? null
}

/* Kako se imenuje stran na poti - za napis povezave nazaj. Ime lige,
   turnirja ali igralca vzame iz že naloženih podatkov (gledalec je stran
   pravkar gledal, zato so v predpomnilniku); brez njih splošna beseda. */
export function oznakaPoti(pot: string, odjemalec: QueryClient): string {
  const [pathname] = pot.split('?')
  const [prvi, drugi] = pathname.split('/').filter(Boolean)
  const id = Number(drugi)
  const ime = <T,>(kljuc: unknown[], izberi: (d: T) => string | undefined) => {
    const podatki = odjemalec.getQueryData<T>(kljuc)
    return podatki ? izberi(podatki) : undefined
  }
  let oznaka: string
  switch (prvi) {
    case undefined:
      oznaka = 'Domača stran'
      break
    case 'lestvica':
      oznaka = 'Lestvica'
      break
    case 'lige':
      oznaka = drugi ? (ime<LigaDto>(['liga', id], (l) => l.ime) ?? 'Liga') : 'Lige'
      break
    case 'turnirji':
      oznaka = drugi ? (ime<TurnirDto>(['turnir', id], (t) => t.ime) ?? 'Turnir') : 'Turnirji'
      break
    case 'dogodki':
      oznaka = ime<MrezaDto>(['dogodek', id], (m) => m.dogodek.ime) ?? 'Kategorija'
      break
    case 'srecanja':
      oznaka =
        ime<SrecanjePodrobnoDto>(['srecanje', id], (p) => `${p.srecanje.domaci} – ${p.srecanje.gost}`)
        ?? 'Srečanje'
      break
    case 'igralci':
      oznaka = ime<ProfilDto>(['profil', id], (p) => p.glava.polnoIme) ?? 'Profil'
      break
    case 'koledar':
      oznaka = 'Koledar'
      break
    case 'dvoboj':
      oznaka = '1 na 1'
      break
    case 'moj-profil':
      oznaka = 'Moj profil'
      break
    case 'o-ratingu':
      oznaka = 'O ratingu'
      break
    default:
      oznaka = 'Nazaj'
  }
  return oznaka.length > NAJDALJSA_OZNAKA
    ? oznaka.slice(0, NAJDALJSA_OZNAKA - 1).trimEnd() + '…'
    : oznaka
}
