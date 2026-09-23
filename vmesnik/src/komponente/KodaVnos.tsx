/* Šestmestna koda kot 6 ločenih okenc namesto enega dolgega polja: vpis
   številke premakne fokus na naslednje okence, izbris iz praznega pa na
   prejšnje. Prilepljena koda (iz maila ali samodejnega izpolnjevanja
   brskalnika) se razporedi čez vsa okenca naenkrat. Naslednje okence se sme
   izpolniti šele, ko je prejšnje izpolnjeno - to prepreči vrzeli, zaradi
   katerih bi bila vidna koda drugačna od tiste, ki jo prebere klicatelj. */
import { useEffect, useRef, type ClipboardEvent, type KeyboardEvent } from 'react'

const DOLZINA = 6

export function KodaVnos({
  vrednost,
  naSpremembo,
  naDokoncano,
  disabled = false,
  autoFocus = false,
}: {
  vrednost: string
  naSpremembo: (v: string) => void
  /* Klic, ko je vpisanih vseh 6 številk - izpuščen tam, kjer koda ni edino
     polje koraka (npr. pozabljeno geslo, kjer sledi še novo geslo). */
  naDokoncano?: (v: string) => void
  disabled?: boolean
  autoFocus?: boolean
}) {
  const polja = useRef<(HTMLInputElement | null)[]>([])
  const prejsnjaDolzina = useRef(vrednost.length)

  useEffect(() => {
    if (autoFocus) polja.current[0]?.focus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /* Okence, ki ravno postane naslednja prosta frontira, je ob klicu iz
     dogodka onChange/onPaste v izrisanem DOM-u še vedno "disabled" (React se
     še ni ponovno izrisal) - klic .focus() nanj bi bil brez učinka. Premik
     fokusa zato čaka na učinek, ki teče PO izrisu z novo vrednostjo. */
  useEffect(() => {
    const prejsnja = prejsnjaDolzina.current
    prejsnjaDolzina.current = vrednost.length
    if (vrednost.length > prejsnja && vrednost.length < DOLZINA) {
      polja.current[vrednost.length]?.focus()
    }
  }, [vrednost])

  function nastavi(nova: string) {
    naSpremembo(nova)
    if (nova.length === DOLZINA) naDokoncano?.(nova)
  }

  function obVnosu(mesto: number, novoBesedilo: string) {
    const cifre = novoBesedilo.replace(/\D/g, '')
    if (cifre.length > 1) {
      // Samodejno izpolnjevanje brskalnika kodo vpiše naenkrat v eno okence.
      nastavi(cifre.slice(0, DOLZINA))
      return
    }
    if (!cifre) {
      nastavi(vrednost.slice(0, mesto) + vrednost.slice(mesto + 1))
      return
    }
    nastavi((vrednost.slice(0, mesto) + cifre + vrednost.slice(mesto + 1)).slice(0, DOLZINA))
  }

  function obTipki(mesto: number, dogodek: KeyboardEvent<HTMLInputElement>) {
    if (dogodek.key === 'Backspace' && !vrednost[mesto] && mesto > 0) {
      dogodek.preventDefault()
      nastavi(vrednost.slice(0, mesto - 1) + vrednost.slice(mesto))
      polja.current[mesto - 1]?.focus()
    } else if (dogodek.key === 'ArrowLeft' && mesto > 0) {
      dogodek.preventDefault()
      polja.current[mesto - 1]?.focus()
    } else if (dogodek.key === 'ArrowRight' && mesto < DOLZINA - 1) {
      dogodek.preventDefault()
      polja.current[mesto + 1]?.focus()
    }
  }

  function obPrilepljanju(mesto: number, dogodek: ClipboardEvent<HTMLInputElement>) {
    const cifre = dogodek.clipboardData.getData('text').replace(/\D/g, '')
    if (!cifre) return
    dogodek.preventDefault()
    nastavi((vrednost.slice(0, mesto) + cifre).slice(0, DOLZINA))
  }

  return (
    <div className="koda-polja">
      {Array.from({ length: DOLZINA }, (_, mesto) => (
        <input
          key={mesto}
          ref={(el) => {
            polja.current[mesto] = el
          }}
          className="koda-polja__polje"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]*"
          maxLength={1}
          value={vrednost[mesto] ?? ''}
          disabled={disabled || mesto > vrednost.length}
          onChange={(d) => obVnosu(mesto, d.target.value)}
          onKeyDown={(d) => obTipki(mesto, d)}
          onPaste={(d) => obPrilepljanju(mesto, d)}
          onFocus={(d) => d.target.select()}
          aria-label={`${mesto + 1}. številka kode`}
        />
      ))}
    </div>
  )
}
