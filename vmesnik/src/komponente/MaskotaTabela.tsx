/* Lik v tabeli paketov (registracija, korak »Paket«): ob prvem prikazu koraka
   pade z vrha tabele, skače po vrsticah navzdol in ob vsaki funkciji, ki jo
   doda Premium, pokaže nanjo (okvir okoli celice Premium); nato se potopi
   skozi spodnji rob. Igra ga isti lik in isti predvajalnik kot maskoto v glavi
   (MaskotaPrizori.tsx, animatorMaskote.ts); prizor je v pomozno/prizorTabela.ts.

   To je del IZJEME od DESIGN.md, razdelek 5b: samo namizje, en nastop na odprto
   okno, pod 5 s, nikoli ob `prefers-reduced-motion`, dekorativno (`aria-hidden`,
   brez fokusa in klikov — platno ne ujame nobenega klika). Če se prizor ne
   prilega tabeli (preozko okno), ga ni: `obKoncu` pride takoj. */
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'

import { predvajaj } from '../pomozno/animatorMaskote'
import { izmeriTabelo, prizorTabela, type IzmeraTabele } from '../pomozno/prizorTabela'
import { MERILO_LIKA } from '../pomozno/umestitevMaskote'
import { Lik } from './MaskotaPrizori'

/* Preden lik pade, gledalec pogleda tabelo: prvi vtis je vsebina, ne gibanje. */
const ZAMIK_MS = 700
/* Varovalka, če konec animacije ne pride (zavihek v ozadju): platno se odstrani
   samo, sicer bi obviselo nad tabelo. */
const REZERVA_MS = 1500

export function MaskotaTabela({ obKoncu }: { obKoncu: () => void }) {
  const ovoj = useRef<HTMLDivElement>(null)
  const platno = useRef<SVGSVGElement>(null)
  const [izmera, nastaviIzmero] = useState<IzmeraTabele | null>(null)
  const obKoncuRef = useRef(obKoncu)
  useEffect(() => {
    obKoncuRef.current = obKoncu
  })
  const prizor = useMemo(() => (izmera ? prizorTabela(izmera) : null), [izmera])

  /* Izmera: šele po zamiku in ko so pisave naložene (širina besedila vrstic je
     odvisna od njih); ob skritem zavihku počaka, da se vrne. */
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      obKoncuRef.current()
      return
    }
    let preklicano = false

    function izmeri() {
      if (preklicano) return
      if (document.hidden) {
        document.addEventListener('visibilitychange', izmeri, { once: true })
        return
      }
      const tabela = ovoj.current?.parentElement
      const m = tabela ? izmeriTabelo(tabela) : null
      if (m) nastaviIzmero(m)
      else obKoncuRef.current()
    }

    const casovnik = window.setTimeout(() => void document.fonts.ready.then(izmeri), ZAMIK_MS)
    return () => {
      preklicano = true
      window.clearTimeout(casovnik)
      document.removeEventListener('visibilitychange', izmeri)
    }
  }, [])

  /* Predvajanje pred prvim slikanjem (kot PlatnoMaskote): sicer bi lik za
     trenutek obstal v mirovni legi na vrhu tabele. */
  useLayoutEffect(() => {
    if (!prizor || !platno.current) return
    const seja = predvajaj(platno.current, prizor, undefined)
    void seja.konec.then(() => obKoncuRef.current())
    const varovalka = window.setTimeout(() => obKoncuRef.current(), prizor.trajanjeMs + REZERVA_MS)
    return () => {
      window.clearTimeout(varovalka)
      seja.ustavi()
    }
  }, [prizor])

  return (
    <div ref={ovoj} className="maskota-tabela" aria-hidden="true">
      {izmera && (
        <svg
          ref={platno}
          className="maskota__platno"
          viewBox={`0 0 ${izmera.sirina} ${izmera.visina}`}
          width={izmera.sirina}
          height={izmera.visina}
          focusable="false"
        >
          <rect
            data-del="okvir"
            className="maskota-tabela__okvir"
            x={izmera.okvir.x}
            y="0"
            width={izmera.okvir.sirina}
            height={izmera.okvir.visina}
            opacity="0"
          />
          <g
            transform={`translate(${izmera.x} ${izmera.vrstice[0].tla}) scale(${MERILO_LIKA}) translate(-88 -64)`}
          >
            <Lik />
          </g>
        </svg>
      )}
    </div>
  )
}
