/* Uporabniski meni v desnem zgornjem kotu glave: ikona osebe, ki odpre
   spustni meni. Gost dobi moznost prijave in registracije, prijavljen
   uporabnik pa svojo identiteto, povezavo do profila in odjavo. Meni se
   zapre ob kliku zunaj njega ali ob tipki Escape. */
import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { OZNAKE_VLOGA } from '../api/tipi'
import { PrijavaOkno } from './PrijavaOkno'

type PrijavaNacin = 'prijava' | 'registracija'

/* Silhueta osebe v currentColor, da se barva ujema s temo in stanjem gumba. */
function IkonaOseba({ velikost = 22 }: { velikost?: number }) {
  return (
    <svg width={velikost} height={velikost} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.75" fill="currentColor" />
      <path d="M5 19.5c0-3.6 3.1-6 7-6s7 2.4 7 6z" fill="currentColor" />
    </svg>
  )
}

export function UporabniskiMeni() {
  const { uporabnik, odjava } = useAvtentikacija()
  const [odprt, nastaviOdprt] = useState(false)
  const [prijavaNacin, nastaviPrijavaNacin] = useState<PrijavaNacin | null>(null)
  const ovoj = useRef<HTMLDivElement>(null)

  /* Zapri odprt meni ob kliku zunaj njega ali ob tipki Escape. */
  useEffect(() => {
    if (!odprt) return
    function obKliku(dogodek: MouseEvent) {
      if (ovoj.current && !ovoj.current.contains(dogodek.target as Node)) nastaviOdprt(false)
    }
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key === 'Escape') nastaviOdprt(false)
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprt])

  /* Povezavo do profila vidi vsak prijavljen igralec (tudi tak, ki se caka na
     potrditev); tam mu stran pojasni, zakaj profila se ni. */
  const jePrijavljenIgralec = uporabnik?.vloga === 'IGRALEC'

  function odpriPrijavo(nacin: PrijavaNacin) {
    nastaviOdprt(false)
    nastaviPrijavaNacin(nacin)
  }

  return (
    <div className="glava__uporabnik" ref={ovoj}>
      <button
        type="button"
        className={'uporabnik-gumb' + (uporabnik ? ' uporabnik-gumb--prijavljen' : '')}
        aria-haspopup="menu"
        aria-expanded={odprt}
        aria-label={uporabnik ? 'Uporabniski meni' : 'Prijava'}
        onClick={() => nastaviOdprt((v) => !v)}
      >
        <IkonaOseba />
      </button>

      {odprt && (
        <div className="uporabnik-meni" role="menu">
          {uporabnik ? (
            <>
              <div className="uporabnik-meni__glava">
                <span className="uporabnik-meni__avatar">
                  <IkonaOseba velikost={20} />
                </span>
                <span className="uporabnik-meni__oseba">
                  <span className="uporabnik-meni__ime">
                    {uporabnik.imeIgralca ?? uporabnik.uporabniskoIme}
                  </span>
                  <span className="uporabnik-meni__vloga">
                    {OZNAKE_VLOGA[uporabnik.vloga]}
                  </span>
                </span>
              </div>

              {jePrijavljenIgralec && (
                <NavLink
                  to="/moj-profil"
                  role="menuitem"
                  className="uporabnik-meni__postavka"
                  onClick={() => nastaviOdprt(false)}
                >
                  Moj profil
                </NavLink>
              )}

              <button
                type="button"
                role="menuitem"
                className="uporabnik-meni__postavka uporabnik-meni__postavka--nevaren"
                onClick={() => {
                  nastaviOdprt(false)
                  odjava()
                }}
              >
                Odjava
              </button>
            </>
          ) : (
            <>
              <p className="uporabnik-meni__namig">Ogledujes kot gost — vsebina je vidna brez prijave.</p>
              <button
                type="button"
                role="menuitem"
                className="uporabnik-meni__postavka"
                onClick={() => odpriPrijavo('prijava')}
              >
                Prijava
              </button>
              <button
                type="button"
                role="menuitem"
                className="uporabnik-meni__postavka"
                onClick={() => odpriPrijavo('registracija')}
              >
                Ustvari racun
              </button>
            </>
          )}
        </div>
      )}

      {prijavaNacin && (
        <PrijavaOkno zacetniNacin={prijavaNacin} onZapri={() => nastaviPrijavaNacin(null)} />
      )}
    </div>
  )
}
