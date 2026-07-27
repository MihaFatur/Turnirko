/* Skupna postavitev vseh strani: glava z navigacijo, prijava/odjava in
   prostor za vsebino. Nekatere povezave (Igralci, Šifranti) vidi samo
   prijavljeni administrator - gost ima le bralne poglede. */
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { PrijavaOkno } from './PrijavaOkno'

interface Povezava {
  pot: string
  oznaka: string
  samoAdmin?: boolean
  samoIgralec?: boolean
}

const povezave: Povezava[] = [
  { pot: '/', oznaka: 'Domov' },
  { pot: '/turnirji', oznaka: 'Turnirji' },
  { pot: '/lige', oznaka: 'Lige' },
  { pot: '/lestvica', oznaka: 'Lestvica' },
  { pot: '/dvoboj', oznaka: '1 na 1' },
  { pot: '/moj-profil', oznaka: 'Moj profil', samoIgralec: true },
  { pot: '/igralci', oznaka: 'Igralci', samoAdmin: true },
  { pot: '/racuni', oznaka: 'Dostopi', samoAdmin: true },
  { pot: '/sifranti', oznaka: 'Šifranti', samoAdmin: true },
]

export function Postavitev() {
  const { uporabnik, jeAdmin, odjava } = useAvtentikacija()
  const [prijavaOdprta, nastaviPrijavaOdprta] = useState(false)

  /* Povezavo do profila vidi vsak prijavljen igralec - tudi tisti, ki še
     čaka na potrditev; tam mu stran pojasni, zakaj profila še ni. */
  const jePrijavljenIgralec = uporabnik?.vloga === 'IGRALEC'
  const vidne = povezave.filter(
    (p) => (!p.samoAdmin || jeAdmin) && (!p.samoIgralec || jePrijavljenIgralec),
  )

  return (
    <div className="postavitev">
      <header className="glava">
        <div className="glava__vsebina">
          <NavLink to="/" className="glava__logotip">
            {/* Lasten znak: lopar (currentColor) + oranzna zogica kot iskra. */}
            <svg className="logo-znak" width="27" height="27" viewBox="0 0 28 28" aria-hidden="true">
              <path d="M14 25 L18 21" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" />
              <circle cx="12" cy="12" r="8.5" fill="currentColor" />
              <circle className="logo-znak__zogica" cx="21" cy="8" r="3.4" />
            </svg>
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

          <div className="glava__uporabnik">
            {uporabnik ? (
              <>
                <span
                  className="glava__oznaka-vloge"
                  title={jeAdmin ? 'Prijavljen administrator' : 'Prijavljen igralec'}
                >
                  {jeAdmin ? '👤' : '🏓'} {uporabnik.imeIgralca ?? uporabnik.uporabniskoIme}
                </span>
                <button className="gumb gumb--majhen" onClick={odjava}>
                  Odjava
                </button>
              </>
            ) : (
              <button
                className="gumb gumb--majhen gumb--glavni"
                onClick={() => nastaviPrijavaOdprta(true)}
              >
                Prijava
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="vsebina">
        <Outlet />
      </main>

      {prijavaOdprta && <PrijavaOkno onZapri={() => nastaviPrijavaOdprta(false)} />}
    </div>
  )
}
