/* Maskota »Igralec Premium«: ob naključnih trenutkih se v glavi pojavi lik s
   tablo (ali letalo z zastavico), se malo poigra in izgine. Vsak nastop je
   kombinacija PRIZORA (kaj lik počne) in NAPISA (kaj piše tabla); izbirata ju
   vreči (pomozno/vrecaMaskote.ts), da se vsak prizor in vsak napis pokaže
   enako pogosto.

   To je NAMERNA IZJEMA od DESIGN.md (razdelek 5b): odločitev lastnika, sep
   2026. Meje izjeme so tam in tu: gost in igralec brez Premium, vsak nastop
   najdlje 5 s, redko in samo ob primernem trenutku (urnikMaskote.ts), brez
   gibanja pri `prefers-reduced-motion`. Igra na namizju in na telefonu: platno
   je čez celo glavo (namizni masthead ali 56 px visoka vrstica telefona; glej
   pomozno/umestitevMaskote.ts), na telefonu pa oznaka uporabnika (»Gost ·
   prijava«) med nastopom izgine, ker lik stoji v njenem prostoru.

   Maskota je DEKORATIVNA bližnjica z miško ali prstom: `aria-hidden` in
   `tabIndex={-1}`. Dostopna pot do istega cilja ostane v uporabniškem meniju
   (»Naročnina«) — zato ni treba, da bi ob naključnem trenutku ugrabila fokus
   tipkovnici ali bralniku zaslona.

   Gostu klik odpre registracijo naravnost na koraku paketa, z izbranim
   Igralcem Premium za eno leto (ne /narocnina, ki gosta pošlje nazaj): Premium
   je račun igralca, zato je prvi korak ustvariti račun. Prijavljen igralec brez
   Premium gre na /narocnina. */
import { type CSSProperties, useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { NAPISI, besediloNapisa, cenaPremium, izmeriNapis } from '../pomozno/napisiMaskote'
import { prizoriZaNapravo } from '../pomozno/prizoriMaskote'
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
import { izmeriGlavo, sestaviNastop, type Nastop } from '../pomozno/umestitevMaskote'
import { izberiPar } from '../pomozno/vrecaMaskote'
import { PlatnoMaskote } from './MaskotaPrizori'
import { PrijavaOkno } from './PrijavaOkno'

/* Kdo maskoto sploh sme videti: kdor lahko Premium kupi (igralec) ali ga bo
   moral najprej ustvariti račun (gost). Organizator in admin imata svoje
   pakete oz. nobenega, na turnirski dan pa maskota urejevalcu samo stoji na
   poti. Na strani naročnine je oglas odveč. */
function useSmeVideti(): boolean {
  const { uporabnik, jePremium, nalaganje } = useAvtentikacija()
  const { pathname } = useLocation()

  if (nalaganje) return false
  if (pathname.startsWith('/narocnina')) return false
  return uporabnik === null || (uporabnik.vloga === 'IGRALEC' && !jePremium)
}

export function Maskota() {
  const smeVideti = useSmeVideti()
  const { uporabnik } = useAvtentikacija()
  const [nastop, nastaviNastop] = useState<Nastop | null>(null)
  const [registracija, nastaviRegistracijo] = useState(false)
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

    /* Izmera napisov je asinhrona (nalaganje pisave): do njenega konca se je
       lahko stran zamenjala ali gledalec odjavil. */
    let preklicano = false
    let casovnik = window.setTimeout(poskusi, zamik)

    async function poskusi() {
      if (!jePrimernTrenutek() || !izmeriGlavo()) {
        casovnik = window.setTimeout(poskusi, PONOVNI_POSKUS_MS)
        return
      }

      /* Vsi napisi naenkrat: od širine napisa je odvisno, kateri prizori se v
         glavo prilegajo, izbira pa je vsak prizor z vsakim napisom. */
      const cena = cenaPremium(uporabnikRef.current)
      const napisi = NAPISI.map((predloga) => besediloNapisa(predloga, cena))
      const sirine = await Promise.all(napisi.map(izmeriNapis))
      if (preklicano) return

      /* Do konca izmere se je glava lahko spremenila (zasuk, meni). */
      const glava = izmeriGlavo()
      if (!glava || !jePrimernTrenutek()) {
        casovnik = window.setTimeout(poskusi, PONOVNI_POSKUS_MS)
        return
      }

      const prizori = prizoriZaNapravo(glava.naprava)
      const sestavi = (id: string, predloga: string) => {
        const prizor = prizori.find((p) => p.id === id)
        const i = NAPISI.indexOf(predloga)
        return prizor && i >= 0 ? sestaviNastop(prizor, napisi[i], sirine[i], glava) : null
      }

      let izbran: Nastop | null
      if (predogled) {
        /* Neznan prizor v naslovu (npr. odstranjena »lestev«) = katerikoli. */
        const zeljeni = prizori.filter((p) => p.id === predogled.prizor)
        const zeljenNapis = predogled.napis === null ? undefined : NAPISI[predogled.napis - 1]
        const predloge = zeljenNapis ? [zeljenNapis] : NAPISI
        const kandidati = (zeljeni.length > 0 ? zeljeni : prizori)
          .flatMap((p) => predloge.map((n) => sestavi(p.id, n)))
          .filter((n): n is Nastop => n !== null)
        izbran = kandidati.length > 0 ? izberiNakljucno(kandidati, null) : null
      } else {
        const par = izberiPar(
          glava.naprava,
          prizori.map((p) => p.id),
          NAPISI,
          (id, predloga) => sestavi(id, predloga) !== null,
        )
        izbran = par ? sestavi(par.prizor, par.napis) : null
      }

      if (!izbran) {
        casovnik = window.setTimeout(poskusi, PONOVNI_POSKUS_MS)
        return
      }
      if (predogled) predogledPokazan.current = true
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
        className: 'maskota',
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
