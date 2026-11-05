/* Spustni meni za izbiro ene vrednosti (pogled »Udeleženci«: klub, razvrstitev).

   Lasten, ne nativni <select>: plošča ima ob vsaki postavki še število, izbrana
   postavka je označena z modro črto, videz pa mora biti isti v vseh brskalnikih.
   Odpre se lahko samo en meni naenkrat - to ureja stran, ki hrani, kateri je
   odprt (`odprt`, `naPreklop`, `naZapri`). Zapre se z izbiro, s klikom zunaj in
   z Escape; s tipkovnico se po postavkah hodi s puščicama, izbere z Enter.

   Oznaka stoji na namizju nad gumbom, na telefonu v gumbu nad vrednostjo -
   zato je v DOM dvakrat in CSS pokaže eno. */
import { useEffect, useId, useRef, type KeyboardEvent } from 'react'

import type { MoznostMenija } from '../pomozno/udelezenci'

interface Lastnosti<T extends string | null> {
  oznaka: string
  moznosti: MoznostMenija<T>[]
  izbrano: T
  odprt: boolean
  naPreklop: () => void
  naZapri: () => void
  naIzbiro: (vrednost: T) => void
  /* Na koncu vrstice se plošča poravna z desnim robom gumba, da ne uide z zaslona. */
  poravnava?: 'levo' | 'desno'
}

export function MeniIzbire<T extends string | null>({
  oznaka,
  moznosti,
  izbrano,
  odprt,
  naPreklop,
  naZapri,
  naIzbiro,
  poravnava = 'levo',
}: Lastnosti<T>) {
  const koren = useRef<HTMLDivElement>(null)
  const gumb = useRef<HTMLButtonElement>(null)
  const postavke = useRef<(HTMLDivElement | null)[]>([])
  const idPlosce = useId()
  const zapri = useRef(naZapri)
  zapri.current = naZapri

  const izbranIndeks = Math.max(
    0,
    moznosti.findIndex((m) => m.vrednost === izbrano),
  )
  const trenutna = moznosti[izbranIndeks]

  /* Ob odprtju gre fokus na izbrano postavko; tipkovnica tako nadaljuje tam, kjer
     je bralec. */
  useEffect(() => {
    if (odprt) postavke.current[izbranIndeks]?.focus()
    // izbranIndeks se ob odprtju ne spreminja, naslednjič se bere sproti
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [odprt])

  useEffect(() => {
    if (!odprt) return
    const obKlikuZunaj = (dogodek: PointerEvent) => {
      if (koren.current && !koren.current.contains(dogodek.target as Node)) zapri.current()
    }
    document.addEventListener('pointerdown', obKlikuZunaj)
    return () => document.removeEventListener('pointerdown', obKlikuZunaj)
  }, [odprt])

  const izberi = (indeks: number) => {
    naIzbiro(moznosti[indeks].vrednost)
    gumb.current?.focus()
  }

  const obTipkiGumba = (dogodek: KeyboardEvent<HTMLButtonElement>) => {
    if (!odprt && (dogodek.key === 'ArrowDown' || dogodek.key === 'ArrowUp')) {
      dogodek.preventDefault()
      naPreklop()
    }
  }

  const obTipkiPostavke = (dogodek: KeyboardEvent<HTMLDivElement>, indeks: number) => {
    const n = moznosti.length
    const naIndeks = (k: number) => {
      dogodek.preventDefault()
      postavke.current[(k + n) % n]?.focus()
    }
    switch (dogodek.key) {
      case 'ArrowDown':
        naIndeks(indeks + 1)
        break
      case 'ArrowUp':
        naIndeks(indeks - 1)
        break
      case 'Home':
        naIndeks(0)
        break
      case 'End':
        naIndeks(n - 1)
        break
      case 'Enter':
      case ' ':
        dogodek.preventDefault()
        izberi(indeks)
        break
      case 'Escape':
        dogodek.preventDefault()
        naZapri()
        gumb.current?.focus()
        break
      case 'Tab':
        naZapri()
        break
    }
  }

  return (
    <div
      className={'meni-izbire' + (poravnava === 'desno' ? ' meni-izbire--desno' : '')}
      ref={koren}
    >
      <span className="meni-izbire__oznaka meni-izbire__oznaka--zunaj">{oznaka}</span>
      <button
        type="button"
        ref={gumb}
        className="meni-izbire__gumb"
        aria-haspopup="listbox"
        aria-expanded={odprt}
        aria-controls={odprt ? idPlosce : undefined}
        aria-label={`${oznaka}: ${trenutna.ime}`}
        onClick={naPreklop}
        onKeyDown={obTipkiGumba}
      >
        <span className="meni-izbire__besedilo">
          <span className="meni-izbire__oznaka meni-izbire__oznaka--znotraj">{oznaka}</span>
          <span className="meni-izbire__vrednost">{trenutna.ime}</span>
        </span>
        <span className="meni-izbire__puscica" aria-hidden="true">
          ▾
        </span>
      </button>
      {odprt && (
        <div className="meni-izbire__plosca" id={idPlosce} role="listbox" aria-label={oznaka}>
          {moznosti.map((moznost, indeks) => {
            const jeIzbrana = indeks === izbranIndeks
            return (
              <div
                key={moznost.vrednost ?? '*'}
                ref={(element) => {
                  postavke.current[indeks] = element
                }}
                className={'meni-izbire__postavka' + (jeIzbrana ? ' meni-izbire__postavka--izbrana' : '')}
                role="option"
                aria-selected={jeIzbrana}
                tabIndex={-1}
                onClick={() => izberi(indeks)}
                onKeyDown={(dogodek) => obTipkiPostavke(dogodek, indeks)}
              >
                <span>{moznost.ime}</span>
                <span className="meni-izbire__stevilo">{moznost.stevilo}</span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
