/* Kontekst uporabnika v desnem kotu masthead-a. V novem oblikovnem sistemu
   ikon ni: prožilnik je mono oznaka (ime osebe oz. »Gost · prijava«).
   Gostu oznaka odpre okno za prijavo naravnost - spustni meni z dvema
   postavkama bi bil samo korak več, registracija pa je gumb v oknu samem.
   Prijavljen uporabnik dobi spustni meni s svojo identiteto, povezavo do
   profila in odjavo. Račun, ki naslova (ali skrbnika) še ni potrdil, dobi
   postavko »Vpiši kodo«. Meni se zapre ob kliku zunaj njega ali ob tipki
   Escape. */
import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { OZNAKE_VLOGA } from '../api/tipi'
import { PrijavaOkno } from './PrijavaOkno'

type PrijavaNacin = 'prijava' | 'koda'

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

  /* Račun brez potrjenega naslova ali skrbnikove kode: kar manjka, je koda. */
  const cakaKodo =
    uporabnik !== null
    && uporabnik.vloga !== 'ADMIN'
    && (!uporabnik.emailPotrjen || uporabnik.potrebnaKodaSkrbnika)

  /* Oznaka konteksta je IME osebe, ne njena vloga: v kotu zapisnika stoji,
     kdo ga vodi (»NEJC VRHOVNIK«). Vloga in klub sta v glavi spustnega
     menija, kjer je zanju prostor. Račun brez povezanega igralca (admin,
     organizator) se predstavi s prijavnim imenom. */
  const oznaka = uporabnik
    ? (uporabnik.imeIgralca ?? uporabnik.uporabniskoIme)
    : 'Gost · prijava'

  function odpriPrijavo(nacin: PrijavaNacin) {
    nastaviOdprt(false)
    nastaviPrijavaNacin(nacin)
  }

  /* Gost meni nima kaj pokazati: prijava je edino, kar mu oznaka lahko da. */
  function obKliku() {
    if (uporabnik) nastaviOdprt((v) => !v)
    else odpriPrijavo('prijava')
  }

  return (
    <div className="glava__uporabnik" ref={ovoj}>
      <button
        type="button"
        className={'uporabnik-gumb' + (uporabnik ? ' uporabnik-gumb--prijavljen' : '')}
        aria-haspopup={uporabnik ? 'menu' : 'dialog'}
        aria-expanded={uporabnik ? odprt : undefined}
        onClick={obKliku}
      >
        {oznaka}
      </button>

      {odprt && uporabnik && (
        <div className="uporabnik-meni" role="menu">
          <div className="uporabnik-meni__glava">
            <span className="uporabnik-meni__ime">
              {uporabnik.imeIgralca ?? uporabnik.uporabniskoIme}
            </span>
            <span className="uporabnik-meni__vloga">
              {[
                OZNAKE_VLOGA[uporabnik.vloga],
                uporabnik.klub,
                cakaKodo
                  ? uporabnik.emailPotrjen
                    ? 'čaka kodo skrbnika'
                    : 'e-pošta ni potrjena'
                  : null,
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
          </div>

          {cakaKodo && (
            <button
              type="button"
              role="menuitem"
              className="uporabnik-meni__postavka"
              onClick={() => odpriPrijavo('koda')}
            >
              Vpiši kodo
            </button>
          )}

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

          {uporabnik.vloga !== 'ADMIN' && (
            <NavLink
              to="/narocnina"
              role="menuitem"
              className="uporabnik-meni__postavka"
              onClick={() => nastaviOdprt(false)}
            >
              Naročnina
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
        </div>
      )}

      {prijavaNacin && (
        <PrijavaOkno
          zacetniNacin={prijavaNacin}
          email={prijavaNacin === 'koda' ? (uporabnik?.uporabniskoIme ?? null) : null}
          kodaSkrbnika={prijavaNacin === 'koda' && uporabnik?.emailPotrjen === true}
          onZapri={() => nastaviPrijavaNacin(null)}
        />
      )}
    </div>
  )
}
