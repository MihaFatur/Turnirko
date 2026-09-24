/* Maskota »Igralec Premium«: ob naključnih trenutkih se v glavi, levo od
   oznake uporabnika, pojavi lik s tablo, malo poskakuje in izgine. Vsak
   nastop je naključna kombinacija PRIZORA (kaj lik počne) in NAPISA (kaj piše
   tabla), zato je različnih nastopov prizorov krat napisov.

   To je NAMERNA IZJEMA od DESIGN.md (razdelek 5b): odločitev lastnika, sep
   2026. Meje izjeme so tam in tu: samo namizje, samo gost in igralec brez
   Premium, vsak nastop najdlje 5 s, redko in samo ob primernem trenutku
   (urnikMaskote.ts), brez gibanja pri `prefers-reduced-motion`.

   Maskota je DEKORATIVNA bližnjica z miško: `aria-hidden` in `tabIndex={-1}`.
   Dostopna pot do istega cilja ostane v uporabniškem meniju (»Naročnina«) —
   zato ni treba, da bi ob naključnem trenutku ugrabila fokus tipkovnici ali
   bralniku zaslona.

   Gostu klik odpre registracijo naravnost na koraku paketa, z izbranim
   Igralcem Premium za eno leto (ne /narocnina, ki gosta pošlje nazaj): Premium
   je račun igralca, zato je prvi korak ustvariti račun. Prijavljen igralec brez
   Premium gre na /narocnina. */
import { type CSSProperties, useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { NAPISI, besediloNapisa, cenaPremium, izmeriNapis } from '../pomozno/napisiMaskote'
import { PRIZORI } from '../pomozno/prizoriMaskote'
import { useTelefon } from '../pomozno/telefon'
import {
  NAJVEC_NASTOPOV_NA_NALAGANJE,
  PONOVNI_POSKUS_MS,
  PRVI_NASTOP_MS,
  ZAMIK_MED_NASTOPI_MS,
  izberiNakljucno,
  jePrimernTrenutek,
  nakljucniZamik,
  predogledIzNaslova,
  steviloNastopov,
  zabeleziNastop,
} from '../pomozno/urnikMaskote'
import { PlatnoMaskote, sestaviNastop, type Nastop } from './MaskotaPrizori'
import { PrijavaOkno } from './PrijavaOkno'

/* Kdo maskoto sploh sme videti: kdor lahko Premium kupi (igralec) ali ga bo
   moral najprej ustvariti račun (gost). Organizator in admin imata svoje
   pakete oz. nobenega, na turnirski dan pa maskota urejevalcu samo stoji na
   poti. Na strani naročnine je oglas odveč. */
function useSmeVideti(): boolean {
  const { uporabnik, jePremium, nalaganje } = useAvtentikacija()
  /* Glava telefona je 56 px in stisnjena med logotip in ime (najprej samo
     namizje — odločitev lastnika). */
  const jeTelefon = useTelefon()
  const { pathname } = useLocation()

  if (nalaganje || jeTelefon) return false
  if (pathname.startsWith('/narocnina')) return false
  return uporabnik === null || (uporabnik.vloga === 'IGRALEC' && !jePremium)
}

export function Maskota() {
  const smeVideti = useSmeVideti()
  const { uporabnik } = useAvtentikacija()
  const [nastop, nastaviNastop] = useState<Nastop | null>(null)
  const [registracija, nastaviRegistracijo] = useState(false)
  const zadnjiPrizor = useRef<Nastop['prizor'] | null>(null)
  const zadnjiNapis = useRef<string | null>(null)
  const predogledPokazan = useRef(false)
  /* Cena v napisu je odvisna od gledalca; urnik teče v časovniku, ki ne sme
     zamrzniti starega računa. */
  const uporabnikRef = useRef(uporabnik)
  useEffect(() => {
    uporabnikRef.current = uporabnik
  })

  /* Urnik: en časovnik naenkrat. Teče, dokler je maskota dovoljena in nič ne
     igra; nastop ga ustavi, konec nastopa ga zažene znova. */
  useEffect(() => {
    if (!smeVideti) {
      nastaviNastop(null)
      return
    }
    if (nastop) return

    /* Predogled iz naslova (?maskota) velja enkrat na nalaganje strani in
       mimo meje na nalaganje — sicer bi po treh ogledih prizor ne pokazal več. */
    const predogled = predogledPokazan.current ? null : predogledIzNaslova()
    if (!predogled && steviloNastopov() >= NAJVEC_NASTOPOV_NA_NALAGANJE) return

    const zamik = predogled
      ? 1200
      : nakljucniZamik(steviloNastopov() === 0 ? PRVI_NASTOP_MS : ZAMIK_MED_NASTOPI_MS)

    /* Izmera napisa je asinhrona (nalaganje pisave): do njenega konca se je
       lahko stran zamenjala ali gledalec odjavil. */
    let preklicano = false
    let casovnik = window.setTimeout(poskusi, zamik)

    async function poskusi() {
      if (!jePrimernTrenutek()) {
        casovnik = window.setTimeout(poskusi, PONOVNI_POSKUS_MS)
        return
      }

      /* Najprej napis (od njega je odvisna širina table in s tem, kateri
         prizori se v glavo sploh prilegajo), nato prizor med tistimi, ki se. */
      const predloga =
        (predogled?.napis ? NAPISI[predogled.napis - 1] : undefined) ??
        izberiNakljucno(NAPISI, zadnjiNapis.current)
      const napis = besediloNapisa(predloga, cenaPremium(uporabnikRef.current))
      const sirinaBesedila = await izmeriNapis(napis)
      if (preklicano) return

      const zeljen = PRIZORI.find((p) => p.id === predogled?.prizor)
      const kandidati = (zeljen ? [zeljen] : PRIZORI)
        .map((p) => sestaviNastop(p, napis, sirinaBesedila))
        .filter((n): n is Nastop => n !== null)
      if (!jePrimernTrenutek() || kandidati.length === 0) {
        casovnik = window.setTimeout(poskusi, PONOVNI_POSKUS_MS)
        return
      }

      const izbran = izberiNakljucno(
        kandidati,
        kandidati.find((k) => k.prizor === zadnjiPrizor.current) ?? null,
      )
      if (predogled) predogledPokazan.current = true
      zadnjiPrizor.current = izbran.prizor
      zadnjiNapis.current = predloga
      zabeleziNastop()
      nastaviNastop(izbran)
    }

    return () => {
      preklicano = true
      window.clearTimeout(casovnik)
    }
  }, [smeVideti, nastop])

  /* Varovalka: konec prizora ne pride, če se animacija ne izvede (zavihek v
     ozadju, `display: none` od zunaj). Brez nje bi maskota obvisela v drevesu
     in urnik se ne bi nikoli več zagnal. */
  useEffect(() => {
    if (!nastop) return
    const casovnik = window.setTimeout(() => nastaviNastop(null), nastop.prizor.trajanjeMs + 1500)
    return () => window.clearTimeout(casovnik)
  }, [nastop])

  const obKoncu = useCallback(() => nastaviNastop(null), [])

  const skupno = nastop
    ? {
        /* Platno čez glavo (letalo, lestev) ima svoj razred: stoji v kotu glave
           in ne ob oznaki uporabnika. */
        className: 'maskota' + (nastop.umestitev.vrsta === 'glava' ? ' maskota--glava' : ''),
        'aria-hidden': true as const,
        tabIndex: -1,
        style: {
          width: nastop.umestitev.sirina,
          height: nastop.umestitev.visina,
        } as CSSProperties,
      }
    : null

  return (
    <>
      {nastop && skupno && (
        uporabnik ? (
          <Link to="/narocnina" {...skupno}>
            <PlatnoMaskote nastop={nastop} obKoncu={obKoncu} />
          </Link>
        ) : (
          <button
            type="button"
            {...skupno}
            onClick={() => {
              nastaviNastop(null)
              nastaviRegistracijo(true)
            }}
          >
            <PlatnoMaskote nastop={nastop} obKoncu={obKoncu} />
          </button>
        )
      )}

      {/* Okno živi v gostitelju in ne v nastopu: nastop izgine, ko ga gost
          klikne, okno pa mora ostati. */}
      {registracija && (
        <PrijavaOkno zacetniNacin="registracija" naPaketu onZapri={() => nastaviRegistracijo(false)} />
      )}
    </>
  )
}
