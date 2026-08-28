/* Skupna postavitev vseh strani. Na namizju je to masthead z navigacijo,
   kontekst uporabnika desno in prostor za vsebino; na telefonu lepljiva glava
   in spodnja vrstica.

   Masthead je nosilni vzorec sistema: logotip 24 px display 800 levo,
   navigacija 15 px na sredini, kontekst v mono desno; pod vsem tanka 1 px in
   nato polna 3 px črta (nosi ju .glava__crta).

   Zakaj mreža in ne vrsta: navigacija ostane en sam element - podvojena bi jo
   bralnik zaslona bral dvakrat.

   Telefon (≤ 640 px) ima drugo navigacijo: vodoravno drsna vrstica zavihkov v
   glavi je polovico postavk skrivala pred očmi, zato jo zamenja SPODNJA
   VRSTICA (Domov · Turnirji · Lige · Lestvica · Več) - palec do nje pride brez
   drsenja. Glava nad vsebino nosi kontekst (nazaj, dejanja urejevalca), strani
   pa vanjo vlagajo svoje skozi GlavaTelefona. »Več« je predal z urejevalskimi
   stranmi; gost in igralec vidita štiri postavke, ker ostalih nimata.

   Postavke spodnje vrstice nosijo IKONO in ne besede - namerna izjema od
   hišnega pravila (glej IkoneNavigacije.tsx). Oznaka ostane v drevesu za
   bralnik zaslona, kje si pa oko prebere iz modre poteze, ki ob zamenjavi
   zavihka zdrsne nad novi stolpec. */
import { type ComponentType, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { onlineManager, useQuery } from '@tanstack/react-query'

import { igralciApi, racuniApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { useTelefon } from '../pomozno/telefon'
import { GlavaTelefonaKontekst, type Nazaj } from './GlavaTelefona'
import {
  IkonaDomov,
  IkonaLestvica,
  IkonaLige,
  IkonaTurnirji,
  IkonaVec,
} from './IkoneNavigacije'
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
  /* Poti /dvoboj tu namenoma ni: podrobna primerjava dveh igralcev ni
     cilj obiska, ampak nadaljevanje - odpre se s pripomocka "1 na 1" na
     domaci strani, ki s sabo prinese ze izbrani par (?prvi=&drugi=).
     V navigaciji bi bila prazna vstopna tocka brez izbranih igralcev. */
  { pot: '/moj-profil', oznaka: 'Moj profil', samoIgralec: true },
  { pot: '/igralci', oznaka: 'Igralci', samoUrejevalec: true },
  { pot: '/racuni', oznaka: 'Dostopi', samoAdmin: true },
  { pot: '/sifranti', oznaka: 'Šifranti', samoAdmin: true },
]

/* Prve stiri postavke spodnje vrstice so iste za vse - to so poti, po katerih
   pride gledalec do tekmovanja. Peta ("Vec") je predal in ne povezava. */
const spodnjePovezave: Povezava[] = povezave.slice(0, 4)

/* Ikona postavke stoji ob poti in ne v Povezavi: ikone imajo samo stiri
   postavke spodnje vrstice, namizna navigacija je besedilna in bi polje
   nosila prazno. */
const spodnjeIkone: Record<string, ComponentType> = {
  '/': IkonaDomov,
  '/turnirji': IkonaTurnirji,
  '/lige': IkonaLige,
  '/lestvica': IkonaLestvica,
}

/* Podstrani, ki v spodnji vrstici pripadajo sklopu, a nimajo njegove poti:
   kategorija je del turnirja, srecanje del lige. Brez tega bi gledalec ob
   koraku v kategorijo ostal brez oznacene postavke in ne bi vedel, kje je. */
const PODPOTI: Record<string, string[]> = {
  '/turnirji': ['/dogodki'],
  '/lige': ['/srecanja'],
}

function jeVSklopu(pot: string, naslov: string): boolean {
  if (pot === '/') return naslov === '/'
  if (naslov === pot || naslov.startsWith(pot + '/')) return true
  return (PODPOTI[pot] ?? []).some((p) => naslov === p || naslov.startsWith(p + '/'))
}

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
  const jeTelefon = useTelefon()
  const { pathname: naslov } = useLocation()

  /* Kar stran vloži v lepljivo glavo. Povezava nazaj je navaden podatek,
     dejanja in zavihki pa cela drevesa - ta gredo skozi portal, zato glava
     hrani le ciljne elemente. */
  const [nazaj, nastaviNazaj] = useState<Nazaj | null>(null)
  const [ciljDejanj, nastaviCiljDejanj] = useState<HTMLElement | null>(null)
  const [ciljNaslova, nastaviCiljNaslova] = useState<HTMLElement | null>(null)
  const [ciljZavihkov, nastaviCiljZavihkov] = useState<HTMLElement | null>(null)
  const [odprtVec, nastaviOdprtVec] = useState(false)

  const glava = useMemo(
    () => ({ nastaviNazaj, ciljDejanj, ciljNaslova, ciljZavihkov }),
    [ciljDejanj, ciljNaslova, ciljZavihkov],
  )

  /* Povezavo do profila vidi vsak prijavljen igralec - tudi tisti, ki še
     čaka na potrditev; tam mu stran pojasni, zakaj profila še ni. */
  const jePrijavljenIgralec = uporabnik?.vloga === 'IGRALEC'
  const jeUrejevalec = jeAdmin || jeOrganizator
  const vidne = povezave.filter(
    (p) =>
      (!p.samoAdmin || jeAdmin) &&
      (!p.samoUrejevalec || jeAdmin || jeOrganizator) &&
      (!p.samoIgralec || jePrijavljenIgralec),
  )

  /* Predal se zapre ob odhodu s telefonske sirine - sicer bi na namizju obvisel
     zastor brez vrstice, ki ga je odprla. */
  useEffect(() => {
    if (!jeTelefon) nastaviOdprtVec(false)
  }, [jeTelefon])

  /* Kateri stolpec spodnje vrstice nosi modro potezo. Odprt predal prevlada
     (kot pri ozadju postavke): dokler je "Več" odprt, si v njem in ne na
     strani pod njim. -1 pomeni, da pot ni v nobenem sklopu (npr. moj profil,
     šifranti). */
  const indeksAktivne = odprtVec
    ? spodnjePovezave.length
    : spodnjePovezave.findIndex((p) => jeVSklopu(p.pot, naslov))

  /* Poteza se ob odhodu s sklopa samo skrije, ostane pa tam, kjer je bila:
     če bi skočila na prvi stolpec, bi ob vrnitvi zdrsnila z napačnega konca
     vrstice in gledalcu pokazala pot, po kateri ni šel. */
  const zadnjiIndeks = useRef(0)
  if (indeksAktivne >= 0) zadnjiIndeks.current = indeksAktivne

  return (
    <GlavaTelefonaKontekst.Provider value={glava}>
      <div className="postavitev">
        {jeTelefon ? (
          <header className="glava-telefon">
            <div className="glava-telefon__vrsta">
              {nazaj ? (
                <NavLink to={nazaj.pot} className="glava-telefon__nazaj">
                  <span className="glava-telefon__puscica" aria-hidden="true">
                    ←
                  </span>
                  <span className="glava-telefon__oznaka">{nazaj.oznaka}</span>
                </NavLink>
              ) : (
                <NavLink to="/" className="glava__logotip">
                  <ZnakTurnirko velikost={22} />
                  Turnirko
                </NavLink>
              )}

              {/* Kontekst uporabnika stoji na vstopnih zaslonih. Na podstrani
                  je v 390 px pasu ob puščici nazaj in glavnem dejanju
                  urejevalca zanj prostora ni - kdo si, pa se prebere eno
                  potezo nazaj. Podstran brez glavnega dejanja (liga) ga sme
                  obdržati in to pove z useNazaj(..., sKontekstom). */}
              <div className="glava-telefon__desno">
                <div className="glava-telefon__dejanja" ref={nastaviCiljDejanj} />
                {(!nazaj || nazaj.sKontekstom) && <UporabniskiMeni />}
              </div>
            </div>

            <div className="glava-telefon__crta" />
            <div ref={nastaviCiljNaslova} />
            <div ref={nastaviCiljZavihkov} />
          </header>
        ) : (
          <header className="glava">
            <NavLink to="/" className="glava__logotip">
              <ZnakTurnirko />
              Turnirko
            </NavLink>

            <UporabniskiMeni />

            <div className="glava__crta" />

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
          </header>
        )}

        {!povezan && (
          <div className="brez-povezave" role="status">
            Ni povezave{' '}
            <span className="brez-povezave__pojasnilo">
              — prikazano je zadnje stanje, ki ga je naprava uspela naložiti.
            </span>
          </div>
        )}

        <main className={'vsebina' + (jeTelefon ? ' vsebina--telefon' : '')}>
          <Outlet />
        </main>

        {jeTelefon && (
          <nav
            className={
              'spodnja-vrstica' + (jeUrejevalec ? '' : ' spodnja-vrstica--stiri')
            }
            aria-label="Glavna navigacija"
          >
            {/* Poteza je en sam element in ne rob aktivne postavke - le tako
                lahko zdrsne s stolpca na stolpec namesto da bi preskočila. */}
            <span
              className={
                'spodnja-vrstica__poteza' +
                (indeksAktivne < 0 ? ' spodnja-vrstica__poteza--skrita' : '')
              }
              aria-hidden="true"
              style={{ transform: `translateX(${zadnjiIndeks.current * 100}%)` }}
            />

            {spodnjePovezave.map((povezava, indeks) => {
              const Ikona = spodnjeIkone[povezava.pot]
              return (
                <NavLink
                  key={povezava.pot}
                  to={povezava.pot}
                  end={povezava.pot === '/'}
                  className={
                    'spodnja-vrstica__postavka' +
                    (indeksAktivne === indeks
                      ? ' spodnja-vrstica__postavka--aktivna'
                      : '')
                  }
                >
                  <span className="spodnja-vrstica__ikona">
                    <Ikona />
                  </span>
                  {/* Ikona sama nima imena; oznaka ostane v drevesu, da jo
                      bralnik zaslona in glasovno krmiljenje najdeta. */}
                  <span className="samo-za-bralnik">{povezava.oznaka}</span>
                </NavLink>
              )
            })}

            {jeUrejevalec && (
              <button
                type="button"
                className={
                  'spodnja-vrstica__postavka' +
                  (odprtVec ? ' spodnja-vrstica__postavka--aktivna' : '')
                }
                aria-haspopup="dialog"
                aria-expanded={odprtVec}
                onClick={() => nastaviOdprtVec((prej) => !prej)}
              >
                <span className="spodnja-vrstica__ikona">
                  <IkonaVec />
                </span>
                <span className="samo-za-bralnik">Več</span>
              </button>
            )}
          </nav>
        )}

        {jeTelefon && odprtVec && (
          <PredalVec jeAdmin={jeAdmin} onZapri={() => nastaviOdprtVec(false)} />
        )}
      </div>
    </GlavaTelefonaKontekst.Provider>
  )
}

/* Predal »Več«: strani, ki jih rabi samo urejevalec. Dvigne se nad spodnjo
   vrstico, zastor pod njim zatemni vsebino. Ob vsaki postavki stoji kontekst
   (koliko igralcev, koliko dostopov čaka) - brez njega je predal le seznam
   imen in urejevalec mora vsako stran odpreti, da izve, ali ga kaj čaka.

   Vzorec zadrževanja tipkovnice je isti kot pri ModalnoOkno: izris prek
   portala v <body> (da "position: fixed" meri na okno), ozadje dobi "inert",
   fokus gre v predal in se ob zaprtju vrne na prožilnik. */
function PredalVec({ jeAdmin, onZapri }: { jeAdmin: boolean; onZapri: () => void }) {
  /* Števci se naložijo šele ob odprtju predala (poizvedbi sta vezani na
     njegovo življenje) - gledalec seznama igralcev nikoli ne prenese. */
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  const racuni = useQuery({
    queryKey: ['racuni'],
    queryFn: racuniApi.seznam,
    enabled: jeAdmin,
  })
  const cakajo = racuni.data?.filter((r) => r.status === 'CAKA').length ?? 0

  const okvir = useRef<HTMLDivElement>(null)
  const sprozilec = useRef<HTMLElement | null>(null)
  if (sprozilec.current === null && typeof document !== 'undefined') {
    sprozilec.current = document.activeElement as HTMLElement | null
  }

  useEffect(() => {
    const obTipki = (dogodek: KeyboardEvent) => {
      if (dogodek.key === 'Escape') onZapri()
    }
    window.addEventListener('keydown', obTipki)
    return () => window.removeEventListener('keydown', obTipki)
  }, [onZapri])

  useEffect(() => {
    const koren = document.getElementById('koren')
    const prejsnjiOverflow = document.body.style.overflow
    koren?.setAttribute('inert', '')
    document.body.style.overflow = 'hidden'
    okvir.current?.focus()

    return () => {
      koren?.removeAttribute('inert')
      document.body.style.overflow = prejsnjiOverflow
      const nazaj = sprozilec.current
      if (nazaj?.isConnected) nazaj.focus()
    }
  }, [])

  return createPortal(
    <>
      <div className="predal__zastor" onClick={onZapri} />
      <div
        ref={okvir}
        tabIndex={-1}
        className="predal"
        role="dialog"
        aria-modal="true"
        aria-label="Več"
      >
        <div className="predal__glava">
          <h2>Več</h2>
          <button type="button" className="predal__zapri" onClick={onZapri}>
            Zapri
          </button>
        </div>

        <div className="predal__postavke">
          <NavLink to="/igralci" className="predal__postavka" onClick={onZapri}>
            Igralci
            <span className="predal__kontekst">{igralci.data?.length ?? '—'}</span>
          </NavLink>

          {jeAdmin && (
            <NavLink to="/racuni" className="predal__postavka" onClick={onZapri}>
              Dostopi
              <span
                className={
                  'predal__kontekst' + (cakajo > 0 ? ' predal__kontekst--nujno' : '')
                }
              >
                {cakajo > 0 ? `${cakajo} čakajo` : (racuni.data?.length ?? '—')}
              </span>
            </NavLink>
          )}

          {jeAdmin && (
            <NavLink to="/sifranti" className="predal__postavka" onClick={onZapri}>
              Šifranti
              <span className="predal__kontekst">Kraji, klubi</span>
            </NavLink>
          )}
        </div>

        <p className="predal__opomba">Moj profil in odjava sta v meniju zgoraj desno</p>
      </div>
    </>,
    document.body,
  )
}

/* Edina ikona v vmesniku: štiri poteze zapisnika. Prvi dve sta črnilo, tretja
   glavna (modra), četrta poudarek (zelena) - odločilni niz. Barve nosi CSS, da
   se ujemata obe temi in tisk. */
export function ZnakTurnirko({ velikost = 24 }: { velikost?: number }) {
  return (
    <svg
      className="logo-znak"
      width={velikost}
      height={velikost}
      viewBox="0 0 28 28"
      aria-hidden="true"
    >
      <rect className="logo-znak__poteza" x="1.5" y="4" width="3" height="20" />
      <rect className="logo-znak__poteza" x="8.5" y="4" width="3" height="20" />
      <rect
        className="logo-znak__poteza logo-znak__poteza--glavna"
        x="15.5"
        y="4"
        width="3"
        height="20"
      />
      <rect
        className="logo-znak__poteza logo-znak__poteza--izid"
        x="22.5"
        y="4"
        width="3"
        height="20"
      />
    </svg>
  )
}
