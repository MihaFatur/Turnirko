/* Kontekst uporabnika v desnem kotu masthead-a. V novem oblikovnem sistemu
   ikon ni: prožilnik je mono oznaka (vloga in klub oz. »GOST«), ki odpre
   spustni meni. Gost dobi možnost prijave in registracije, prijavljen
   uporabnik pa svojo identiteto, povezavo do profila in odjavo. Meni se zapre
   ob kliku zunaj njega ali ob tipki Escape. */
import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { OZNAKE_VLOGA } from '../api/tipi'
import { PrijavaOkno } from './PrijavaOkno'

type PrijavaNacin = 'prijava' | 'registracija'

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

  /* Oznaka konteksta: vloga in (pri organizatorju) klub — enako kot na
     maketah »SODNIK · NTK SAVINJA«. */
  const oznaka = uporabnik
    ? [OZNAKE_VLOGA[uporabnik.vloga], uporabnik.klub].filter(Boolean).join(' · ')
    : 'Gost · prijava'

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
        onClick={() => nastaviOdprt((v) => !v)}
      >
        {oznaka}
      </button>

      {odprt && (
        <div className="uporabnik-meni" role="menu">
          {uporabnik ? (
            <>
              <div className="uporabnik-meni__glava">
                <span className="uporabnik-meni__ime">
                  {uporabnik.imeIgralca ?? uporabnik.uporabniskoIme}
                </span>
                <span className="uporabnik-meni__vloga">
                  {[OZNAKE_VLOGA[uporabnik.vloga], uporabnik.klub].filter(Boolean).join(' · ')}
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
              <p className="uporabnik-meni__namig">
                Ogleduješ kot gost — vsa vsebina je vidna brez prijave.
              </p>
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
                Ustvari račun
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
