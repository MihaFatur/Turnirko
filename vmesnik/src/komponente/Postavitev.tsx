/* Skupna postavitev vseh strani: masthead z navigacijo, kontekst uporabnika
   desno in prostor za vsebino. Nekatere povezave (Igralci, Šifranti) vidi samo
   prijavljeni administrator - gost ima le bralne poglede.

   Masthead je nosilni vzorec sistema: logotip 24 px display 800 levo,
   navigacija 15 px na sredini, kontekst v mono desno; pod vsem tanka 1 px in
   nato polna 3 px črta (3 px doda CSS prek .glava::after). */
import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { onlineManager } from '@tanstack/react-query'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { UporabniskiMeni } from './UporabniskiMeni'

interface Povezava {
  pot: string
  oznaka: string
  samoAdmin?: boolean
  /* Vidi administrator ali organizator (npr. sifrant igralcev - organizator
     sme dodati novega igralca). */
  samoUrejevalec?: boolean
  samoIgralec?: boolean
}

const povezave: Povezava[] = [
  { pot: '/', oznaka: 'Domov' },
  { pot: '/turnirji', oznaka: 'Turnirji' },
  { pot: '/lige', oznaka: 'Lige' },
  { pot: '/lestvica', oznaka: 'Lestvica' },
  { pot: '/dvoboj', oznaka: '1 na 1' },
  { pot: '/moj-profil', oznaka: 'Moj profil', samoIgralec: true },
  { pot: '/igralci', oznaka: 'Igralci', samoUrejevalec: true },
  { pot: '/racuni', oznaka: 'Dostopi', samoAdmin: true },
  { pot: '/sifranti', oznaka: 'Šifranti', samoAdmin: true },
]

/* Wi-Fi v telovadnici pada. Ce brskalnik ve, da je brez povezave, to povemo -
   sicer stara lestvica na zaslonu izgleda kot sveza.

   Vir resnice je namenoma onlineManager TanStack Queryja in ne navigator.onLine:
   isti manager zaustavlja poizvedbe, zato trak in podatki nikoli ne trdita
   vsak svojega. */
function useJePovezan(): boolean {
  const [povezan, nastaviPovezan] = useState(() => onlineManager.isOnline())
  useEffect(() => onlineManager.subscribe(nastaviPovezan), [])
  return povezan
}

export function Postavitev() {
  const { uporabnik, jeAdmin, jeOrganizator } = useAvtentikacija()
  const povezan = useJePovezan()

  /* Povezavo do profila vidi vsak prijavljen igralec - tudi tisti, ki še
     čaka na potrditev; tam mu stran pojasni, zakaj profila še ni. */
  const jePrijavljenIgralec = uporabnik?.vloga === 'IGRALEC'
  const vidne = povezave.filter(
    (p) =>
      (!p.samoAdmin || jeAdmin) &&
      (!p.samoUrejevalec || jeAdmin || jeOrganizator) &&
      (!p.samoIgralec || jePrijavljenIgralec),
  )

  return (
    <div className="postavitev">
      <header className="glava">
        <div className="glava__vsebina">
          <NavLink to="/" className="glava__logotip">
            <ZnakTurnirko />
            Turnirko
          </NavLink>
          <nav className="glava__navigacija">
            {vidne.map((povezava) => (
              <NavLink
                key={povezava.pot}
                to={povezava.pot}
                end={povezava.pot === '/'}
                className={({ isActive }) =>
                  'glava__povezava' + (isActive ? ' glava__povezava--aktivna' : '')
                }
              >
                {povezava.oznaka}
              </NavLink>
            ))}
          </nav>

          <UporabniskiMeni />
        </div>
      </header>

      {!povezan && (
        <div className="brez-povezave" role="status">
          Ni povezave{' '}
          <span className="brez-povezave__pojasnilo">
            — prikazano je zadnje stanje, ki ga je naprava uspela naložiti.
          </span>
        </div>
      )}

      <main className="vsebina">
        <Outlet />
      </main>
    </div>
  )
}

/* Edina ikona v vmesniku: lopar v glavni (modri) barvi in žogica v poudarku
   (zeleni). Barve nosi CSS, da se ujemata z obema temama. */
export function ZnakTurnirko({ velikost = 24 }: { velikost?: number }) {
  return (
    <svg
      className="logo-znak"
      width={velikost}
      height={velikost}
      viewBox="0 0 28 28"
      aria-hidden="true"
    >
      <path
        className="logo-znak__rocaj"
        d="M14 25 L18 21"
        strokeWidth="3.2"
        strokeLinecap="round"
        fill="none"
      />
      <circle className="logo-znak__lopar" cx="12" cy="12" r="8.5" />
      <circle className="logo-znak__zogica" cx="21" cy="8" r="3.4" />
    </svg>
  )
}
