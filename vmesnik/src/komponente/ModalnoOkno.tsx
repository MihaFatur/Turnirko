/* Splosno modalno okno; zapre se s klikom na zastor, gumb x ali Escape. */
import { useEffect, type ReactNode } from 'react'

interface Lastnosti {
  naslov: string
  onZapri: () => void
  children: ReactNode
}

export function ModalnoOkno({ naslov, onZapri, children }: Lastnosti) {
  useEffect(() => {
    const obEscape = (dogodek: KeyboardEvent) => {
      if (dogodek.key === 'Escape') onZapri()
    }
    window.addEventListener('keydown', obEscape)
    return () => window.removeEventListener('keydown', obEscape)
  }, [onZapri])

  return (
    <div className="modal__zastor" onClick={onZapri}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={naslov}
        onClick={(dogodek) => dogodek.stopPropagation()}
      >
        <div className="modal__glava">
          <h2>{naslov}</h2>
          <button type="button" className="modal__zapri" onClick={onZapri} aria-label="Zapri">
            ×
          </button>
        </div>
        <div className="modal__telo">{children}</div>
      </div>
    </div>
  )
}
