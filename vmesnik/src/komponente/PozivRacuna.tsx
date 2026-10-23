/* Poziv k računu za gosta (odločitev lastnika 23. 10. 2026, DESIGN.md 5f).

   Gost po 30 s na strani ob naslednjem prehodu na drugo stran dobi okno, ki
   pove, kaj mu račun da: prvi mesec Premium brez plačila in brez kartice.
   Okno se pokaže šele, ko je nova stran izrisana - klik se vedno izvede.
   Izhod je vedno mogoč (»Ne zdaj«, ✕, Escape, klik ob okno); vsak izid začne
   premor (pomozno/pozivRacuna.ts). Na telefonu je okno spodnji list, zgornji
   del strani ostane viden.

   Poziv dobi samo skupina »okno« poskusa (pomozno/statistika.ts): druga
   polovica gostov ga ne vidi, da se da izmeriti, ali okno ljudi pripelje do
   računa ali jih odžene. */
import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  ZAMIK_PO_PREHODU_MS,
  dovoljCasa,
  izkljucenaPot,
  jePrimernTrenutek,
  jeVPremoru,
  oznakaStrani,
  pozivPokazan,
  predogledIzNaslova,
  zabeleziPrikaz,
  zabeleziZaprtje,
} from '../pomozno/pozivRacuna'
import { oznaciSejo, skupinaPoziva, zabelezi } from '../pomozno/statistika'
import { useUvodKoncan } from '../pomozno/uvod'
import { ModalnoOkno } from './ModalnoOkno'

type Izid = 'registracija' | 'prijava' | 'ne-zdaj' | 'zaprto'

export function PozivRacuna({
  zaseden,
  onRegistracija,
  onPrijava,
}: {
  /* Drugo okno gosta (oglas Premium, registracija, prijava) je odprto. */
  zaseden: boolean
  onRegistracija: () => void
  onPrijava: () => void
}) {
  const { uporabnik, nalaganje } = useAvtentikacija()
  const uvodKoncan = useUvodKoncan()
  const lokacija = useLocation()
  const prejsnjaPot = useRef(lokacija.pathname)
  const predogledPokazan = useRef(false)
  const [odprt, nastaviOdprt] = useState(false)
  const [stran, nastaviStran] = useState('')

  const gost = !nalaganje && uporabnik === null

  /* Seja v statistiki nosi skupino poskusa (tudi pri prijavljenem: vrnjeni
     obiskovalec, ki se je medtem registriral, ostane v svoji skupini). */
  useEffect(() => {
    oznaciSejo()
  }, [])

  function pokazi(pot: string) {
    zabeleziPrikaz()
    nastaviStran(oznakaStrani(pot))
    nastaviOdprt(true)
    zabelezi('poziv-prikazan', { stran: oznakaStrani(pot) })
  }

  /* Pregled po naslovu (`?poziv`) počaka samo, da je znano, da gleda gost. */
  useEffect(() => {
    if (!gost || !uvodKoncan || predogledPokazan.current || !predogledIzNaslova()) return
    predogledPokazan.current = true
    pokazi(lokacija.pathname)
  }, [gost, uvodKoncan, lokacija.pathname])

  useEffect(() => {
    const pot = lokacija.pathname
    if (pot === prejsnjaPot.current) return
    prejsnjaPot.current = pot
    if (!gost || !uvodKoncan || zaseden || odprt) return
    if (skupinaPoziva() !== 'okno' || pozivPokazan() || jeVPremoru() || !dovoljCasa()) return
    if (izkljucenaPot(pot)) return
    const casovnik = setTimeout(() => {
      // trenutek se je lahko vmes spremenil (odprt meni, skrit zavihek)
      if (jePrimernTrenutek()) pokazi(pot)
    }, ZAMIK_PO_PREHODU_MS)
    return () => clearTimeout(casovnik)
  }, [lokacija.pathname, gost, uvodKoncan, zaseden, odprt])

  /* Prijava med odprtim pozivom (drug zavihek) - okno ni več smiselno. */
  useEffect(() => {
    if (odprt && !nalaganje && uporabnik !== null) nastaviOdprt(false)
  }, [odprt, nalaganje, uporabnik])

  function zapri(izid: Izid) {
    zabeleziZaprtje()
    nastaviOdprt(false)
    zabelezi('poziv-izid', { izid, stran })
    if (izid === 'registracija') onRegistracija()
    if (izid === 'prijava') onPrijava()
  }

  if (!odprt) return null

  return (
    <ModalnoOkno nadnaslov="Turnirko" naslov="Račun igralca je brezplačen" spodaj onZapri={() => zapri('zaprto')}>
      <p className="poziv__besedilo">
        Z računom je profil tvoj. Prvi mesec dobiš Premium brez plačila: formo, nasprotnike, napoved tekme ter
        nize in točke. Vidiš jih samo ti.
      </p>
      <div className="registracija__kolofon registracija__kolofon--rob-zgoraj">
        {[
          { oznaka: 'Račun', vrednost: '0 €' },
          { oznaka: 'Premium', vrednost: 'Prvi mesec brez plačila' },
          { oznaka: 'Kartica', vrednost: 'Ni potrebna' },
        ].map((v) => (
          <div className="registracija__kolofon-vrstica" key={v.oznaka}>
            <span className="registracija__kolofon-oznaka">{v.oznaka}</span>
            <span className="registracija__kolofon-vrednost">{v.vrednost}</span>
          </div>
        ))}
      </div>
      <div className="poziv__gumbi">
        <button type="button" className="gumb gumb--glavni" autoFocus onClick={() => zapri('registracija')}>
          Ustvari račun
        </button>
        <button type="button" className="gumb" onClick={() => zapri('ne-zdaj')}>
          Ne zdaj
        </button>
      </div>
      <button type="button" className="povezava-gumb poziv__prijava" onClick={() => zapri('prijava')}>
        Že imaš račun? Prijava
      </button>
    </ModalnoOkno>
  )
}
