/* Splosno modalno okno; zapre se s klikom na zastor, gumb za zapiranje ali Escape.
   Izrise se prek portala v document.body, da "position: fixed" zastora vedno
   meri na okno in ne na morebitnega prednika s transform/filter/backdrop-filter
   (npr. glava aplikacije), ki bi sicer postal referencni okvir in okno stlacil. */
import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Lastnosti {
  naslov: string
  /* Mono nadnaslov nad naslovom (npr. "Turnirko" nad "Prijava"). */
  nadnaslov?: string
  /* Nepovratno dejanje: crta pod naslovom je rjasta namesto crne. */
  nevarno?: boolean
  /* Sirse okno za obrazce z vec stolpci. */
  siroko?: boolean
  onZapri: () => void
  children: ReactNode
}

export function ModalnoOkno({
  naslov,
  nadnaslov,
  nevarno,
  siroko,
  onZapri,
  children,
}: Lastnosti) {
  const okvir = useRef<HTMLDivElement>(null)

  /* Sprozilec zabelezimo ze med izrisom, ne v ucinku: React polje z "autoFocus"
     fokusira ob vgradnji, torej PRED ucinki - takrat je aktivni element ze
     znotraj okna in gumba, s katerim je bilo okno odprto, ne bi bilo vec mogoce
     najti. */
  const sprozilec = useRef<HTMLElement | null>(null)
  if (sprozilec.current === null && typeof document !== 'undefined') {
    sprozilec.current = document.activeElement as HTMLElement | null
  }

  useEffect(() => {
    const obEscape = (dogodek: KeyboardEvent) => {
      if (dogodek.key === 'Escape') onZapri()
    }
    window.addEventListener('keydown', obEscape)
    return () => window.removeEventListener('keydown', obEscape)
  }, [onZapri])

  /* Okno mora zadrzati tipkovnico in pogled. Ozadje dobi "inert" (izpade iz
     zaporedja s tabulatorjem in iz bralnika zaslona), telo neha drseti, ob
     zaprtju pa se fokus vrne na gumb, s katerim je bilo okno odprto - sicer
     uporabnik tipkovnice pristane na zacetku strani. */
  useEffect(() => {
    const koren = document.getElementById('koren')
    const prejsnjiOverflow = document.body.style.overflow
    koren?.setAttribute('inert', '')
    document.body.style.overflow = 'hidden'

    const vsebina = okvir.current
    if (vsebina && !vsebina.contains(document.activeElement)) {
      const prvi = vsebina.querySelector<HTMLElement>(
        'input:not([type="hidden"]), select, textarea, button, a[href]',
      )
      ;(prvi ?? vsebina).focus()
    }

    return () => {
      koren?.removeAttribute('inert')
      document.body.style.overflow = prejsnjiOverflow
      /* Fokus vrnemo takoj (ne v requestAnimationFrame): v zavihku, ki ni v
         ospredju, se sliki ne izrisujeta in rAF se ne bi sprozil, okno pa se
         lahko zapre tudi takrat. Ce sprozilca ni vec (npr. meni se je zaprl),
         fokus ostane na telesu dokumenta. */
      const nazaj = sprozilec.current
      if (nazaj?.isConnected) nazaj.focus()
    }
  }, [])

  const razredi =
    'modal' + (siroko ? ' modal--siroko' : '') + (nevarno ? ' modal--nevarno' : '')

  return createPortal(
    <div className="modal__zastor" onClick={onZapri}>
      <div
        ref={okvir}
        tabIndex={-1}
        className={razredi}
        role="dialog"
        aria-modal="true"
        aria-label={naslov}
        onClick={(dogodek) => dogodek.stopPropagation()}
      >
        <div className="modal__glava">
          <h2>
            {nadnaslov && <span className="modal__nad">{nadnaslov}</span>}
            {naslov}
          </h2>
          {/* Ikon v vmesniku ni; pomen kriza nosi aria-label. */}
          <button type="button" className="modal__zapri" onClick={onZapri} aria-label="Zapri">
            ✕
          </button>
        </div>
        <div className="modal__telo">{children}</div>
      </div>
    </div>,
    document.body,
  )
}
