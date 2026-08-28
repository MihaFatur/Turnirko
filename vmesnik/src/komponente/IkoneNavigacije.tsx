/* Ikone spodnje navigacijske vrstice na telefonu.

   Namerna izjema od hišnega pravila »edina ikona v vmesniku je logotip«
   (DESIGN.md, razdelek 5, točka 9), omejena SAMO na spodnjo vrstico: pas je
   ozek, palec ga bere v pol sekunde, pet mono oznak pa je pri 390 px že
   stiskalo »LESTVICA« na rob stolpca. Nikjer drugje (glava, gumbi, meniji,
   predal »Več«) ikon ni.

   Poti so iz zbirke Lucide (ISC licenca), nespremenjene; prilagojena je le
   debelina poteze (2 -> 1.75), ker so ikone risane pri 22 px in bi bile sicer
   pretemne ob 11 px črti nad vrstico.

   Vgrajene so kot React komponente in ne kot <img>, ker morajo dedovati
   currentColor - barvo mirnega in aktivnega stanja nosi postavka. */
import type { ReactNode } from 'react'

/* Skupno ogrodje: vse ikone imajo isto mrezo (24), velikost (22 px) in
   potezo, zato jih loci samo pot. */
function Ikona({ children }: { children: ReactNode }) {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

/* Lucide "house" */
export function IkonaDomov() {
  return (
    <Ikona>
      <path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8" />
      <path d="M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
    </Ikona>
  )
}

/* Lucide "trophy" */
export function IkonaTurnirji() {
  return (
    <Ikona>
      <path d="M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2" />
      <path d="M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2" />
      <path d="M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3" />
      <path d="M4 22h16" />
      <path d="M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z" />
      <path d="M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3" />
    </Ikona>
  )
}

/* Lucide "shield" - liga je ekipno tekmovanje, grb je njen znak. */
export function IkonaLige() {
  return (
    <Ikona>
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
    </Ikona>
  )
}

/* Lucide "chart-no-axes-column" - tri palice, isti zapis kot logotip. */
export function IkonaLestvica() {
  return (
    <Ikona>
      <path d="M5 21v-6" />
      <path d="M12 21V3" />
      <path d="M19 21V9" />
    </Ikona>
  )
}

/* Lucide "ellipsis" - tri pike so ustaljeni znak za predal z ostalim. */
export function IkonaVec() {
  return (
    <Ikona>
      <circle cx="12" cy="12" r="1" />
      <circle cx="19" cy="12" r="1" />
      <circle cx="5" cy="12" r="1" />
    </Ikona>
  )
}
