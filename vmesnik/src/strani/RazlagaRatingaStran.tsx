/* Javna razlaga Turnirko ratinga (/o-ratingu).

   Za koga: igralec, ki je po enem porazu izgubil 113 točk, rekreativec, ki se
   ne najde na lestvici, mladinec, ki je zmagal in dobil tri točke. Številka
   brez razlage je videti kot napaka ali naklonjenost. Stran zato pove, kako
   rating nastane, in to v desetih korakih: vsak gradi na prejšnjem in ima svoj
   preizkus (komponente/RazlagaKoraki).

   Tu je okvir: glava s kolofonom pravil, kazalo korakov ob strani (od 1100 px),
   trak napredka v lepljivi glavi telefona in opazovalec, ki ve, kateri korak
   gledalec bere in katere je že videl (ob prvem prikazu se sprožijo animacije).

   Vse številke izračuna strežnik po istih razredih kot obračun
   (RazlagaRatingaStoritev) - vmesnik ne podvaja obrazca, test v zaledju pa
   drži, da preizkus prvega dne da isto kot pravi obračun. Številke pravil (K,
   teže, odbitki, sidra) pridejo s strežnika: ob umeritvi se spremenijo in
   razlaga mora govoriti o pravilu, po katerem obračun teče. */
import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { razlagaRatingaApi } from '../api/zahteve'
import type { PravilaRatingaDto } from '../api/tipi'
import { GlavaNaslov } from '../komponente/GlavaTelefona'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { RazlagaKoraki } from '../komponente/RazlagaKoraki'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import {
  IMENA_KORAKOV,
  VZDEVKI_SIDER,
  kZa,
} from '../pomozno/razlagaRatinga'
import { useTelefon } from '../pomozno/telefon'

const STEVILO_KORAKOV = IMENA_KORAKOV.length

/* Kateri korak gledalec bere (vrstica v pasu 25–45 % višine okna) in katere je
   že videl. Opazuje samo, kar je v drevesu: koraki se izrišejo šele s pravili. */
function useOpazovalecKorakov(omogoceno: boolean) {
  const koren = useRef<HTMLDivElement>(null)
  const [aktiven, nastaviAktivnega] = useState(1)
  const [videni, nastaviVidene] = useState<ReadonlySet<number>>(new Set())

  useEffect(() => {
    if (!omogoceno || !koren.current || typeof IntersectionObserver === 'undefined') return
    const opazovalec = new IntersectionObserver(
      (vnosi) => {
        for (const vnos of vnosi) {
          if (!vnos.isIntersecting) continue
          const korak = Number((vnos.target as HTMLElement).dataset.korak)
          nastaviAktivnega(korak)
          nastaviVidene((prej) => (prej.has(korak) ? prej : new Set(prej).add(korak)))
        }
      },
      { rootMargin: '-25% 0px -55% 0px' },
    )
    koren.current.querySelectorAll('[data-korak]').forEach((el) => opazovalec.observe(el))
    return () => opazovalec.disconnect()
  }, [omogoceno])

  return { koren, aktiven, videni }
}

export function RazlagaRatingaStran() {
  useNaslovStrani('Kako se računa Turnirko rating')
  const pravila = useQuery({ queryKey: ['rating-pravila'], queryFn: razlagaRatingaApi.pravila })
  const lokacija = useLocation()
  const jeTelefon = useTelefon()
  const { koren, aktiven, videni } = useOpazovalecKorakov(!!pravila.data)

  /* Povezava z drugih strani pride s sidrom (#k7, stara #prvi-dan, #lestvica).
     Router do njega sam ne podrsa, koraki pa se izrišejo šele s pravili.
     Skok je takojšen: na tej strani je gladko drsenje (kazalo), ob njem pa
     bi se cilj, izračunan na začetku, ob nalaganju izračunov nad njim
     (korak 05 dobi rezultat) premaknil, preden drsenje pride do konca. */
  useEffect(() => {
    if (!lokacija.hash || !pravila.data) return
    const id = lokacija.hash.slice(1)
    document
      .getElementById(VZDEVKI_SIDER[id] ?? id)
      ?.scrollIntoView({ behavior: 'instant' as ScrollBehavior })
  }, [lokacija.hash, pravila.data])

  return (
    <section className="razlaga" ref={koren}>
      {jeTelefon && pravila.data && (
        <GlavaNaslov>
          <TrakNapredka aktiven={aktiven} />
        </GlavaNaslov>
      )}

      <div className="razlaga__glava">
        <div className="razlaga__glava-levo">
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Turnirko rating</span>
            <span className="naslov-strani__glavni">Kako deluje</span>
          </h1>
          <p className="uvod">
            Ena številka, ki pove, kako močno igraš v primerjavi z ostalimi. Računa se samo iz zmag
            in porazov. Spodaj je pravilo v desetih korakih; vsak gradi na prejšnjem in vsakega lahko
            preizkusiš s svojimi številkami.
          </p>
        </div>
        {pravila.data && <Kolofon p={pravila.data} />}
      </div>

      <NapakaPoizvedbe poizvedba={pravila} kaj="pravil ratinga" />
      {pravila.isPending && <p className="obvestilo">Nalaganje …</p>}

      {pravila.data && (
        <div className="razlaga__telo">
          <nav className="razlaga__kazalo" aria-label="Koraki">
            <span className="razlaga__kazalo-glava">Koraki</span>
            {IMENA_KORAKOV.map((naslov, i) => {
              const st = i + 1
              return (
                <a
                  key={st}
                  href={`#k${st}`}
                  className={
                    'razlaga__kazalo-povezava'
                    + (st === aktiven
                      ? ' razlaga__kazalo-povezava--aktiven'
                      : st < aktiven
                        ? ' razlaga__kazalo-povezava--mimo'
                        : '')
                  }
                  aria-current={st === aktiven ? 'step' : undefined}
                >
                  <span className="razlaga__kazalo-st">{String(st).padStart(2, '0')}</span>
                  <span>{naslov}</span>
                </a>
              )
            })}
          </nav>
          <div className="razlaga__koraki">
            <RazlagaKoraki p={pravila.data} videni={videni} />
          </div>
        </div>
      )}
    </section>
  )
}

/* Kolofon pravil: pet vrstic, vse iz pravil strežnika. */
function Kolofon({ p }: { p: PravilaRatingaDto }) {
  const vrstice = [
    { oznaka: 'Osnovni K', vrednost: String(p.kOsnovni) },
    { oznaka: `Novinec, pod ${p.pragNovinec} tekem`, vrednost: `K ${kZa(p, 0, false)}` },
    {
      oznaka: 'Teža uradno · klub · rekr.',
      vrednost: `${p.ravni.map((r) => Math.round(r.teza * 100)).join(' · ')} %`,
    },
    { oznaka: 'Odbitek za odsotnost', vrednost: `največ −${Math.max(...p.odbitki.map((o) => o.skupaj))}` },
    { oznaka: 'Spodnja meja', vrednost: String(p.spodnjaMeja) },
  ]
  return (
    <div className="kolofon razlaga__kolofon">
      {vrstice.map((v) => (
        <div key={v.oznaka} className="kolofon__vrstica">
          <span className="kolofon__oznaka">{v.oznaka}</span>
          <span className="kolofon__vrednost">{v.vrednost}</span>
        </div>
      ))}
    </div>
  )
}

/* Trak napredka v lepljivi glavi telefona: »03/10«, ime koraka, palica. */
function TrakNapredka({ aktiven }: { aktiven: number }) {
  const korak = Math.min(STEVILO_KORAKOV, Math.max(1, aktiven))
  return (
    <div className="razlaga__trak">
      <span className="razlaga__trak-st">{String(korak).padStart(2, '0')}/{STEVILO_KORAKOV}</span>
      <span className="razlaga__trak-ime">{IMENA_KORAKOV[korak - 1]}</span>
      <span className="razlaga__trak-palica" aria-hidden="true">
        <span
          className="razlaga__trak-polnilo"
          style={{ width: `${(korak / STEVILO_KORAKOV) * 100}%` }}
        />
      </span>
    </div>
  )
}
