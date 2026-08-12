/* Skupina kot zlozljiva vrstica.

   Pri 100 prijavljenih je skupin 25. Ce bi bile vse odprte, bi bil zaslon
   1500 vrstic dolg in sodnik bi po njem samo drsel; zato je skupina vrstica,
   odprta pa je vedno najvec ena. Vrstica je gumb, ker odpira vsebino - tako
   dela tudi s tipkovnico in z bralnikom zaslona.

   Komponenta ne ve nicesar o sistemu tekmovanja: ne predpostavlja ne stevila
   igralcev ne izlocilnega dela, zato jo lahko uporabi vsak nov sistem. */
import type { ReactNode } from 'react'

import { sklonIgralcev } from '../pomozno/oblikovanje'

interface Lastnosti {
  oznaka: string
  steviloIgralcev: number
  odprta: boolean
  naPreklop: () => void
  /* Vsebina odprte skupine (lestvica in tekme po kolih). */
  children: ReactNode
}

export function SkupinaVrstica({
  oznaka,
  steviloIgralcev,
  odprta,
  naPreklop,
  children,
}: Lastnosti) {
  const idVsebine = `skupina-${oznaka}`
  return (
    <div>
      <button
        type="button"
        className={'skupina-vrstica' + (odprta ? ' skupina-vrstica--odprta' : '')}
        aria-expanded={odprta}
        aria-controls={idVsebine}
        onClick={naPreklop}
      >
        <span className="skupina-vrstica__oznaka">{oznaka}</span>
        <span className="skupina-vrstica__igralci">
          {steviloIgralcev} {sklonIgralcev(steviloIgralcev)}
        </span>
        <span className="skupina-vrstica__gumb">{odprta ? 'zapri' : 'odpri'}</span>
      </button>
      {odprta && (
        <div className="skupina-vsebina" id={idVsebine}>
          <div className="skupina-vsebina__stolpci">{children}</div>
        </div>
      )}
    </div>
  )
}
