/* Izbirnik »Obrazec« na straneh za tisk ekipnega zapisnika: uradni obrazec NTZS
   ali Turnirkov ležeči zapisnik. Pri ligi je uradni obrazec predloga lige
   (1. SNTL, 2./3. SNTL), pri ekipni tekmi turnirja ga je en sam. Izbirnik le
   pove izbiro; stanje in shranjevanje vodi stran (pomozno/oblikaZapisnika). */
import type { PredlogaLige } from '../api/tipi'
import { OZNAKE_PREDLOGA_LIGE } from '../api/tipi'
import {
  type IzbiraObrazca,
  type OblikaZapisnika,
  OZNAKA_OBRAZCA_NTZS,
  OZNAKA_OBRAZCA_TURNIRKO,
} from '../pomozno/oblikaZapisnika'

const PREDLOGE: PredlogaLige[] = ['SNTL_1', 'SNTL_23']

export function IzbirnikObrazca({
  oblika,
  predloga,
  onIzbira,
  razred = 'listki-orodja__izbira',
}: {
  oblika: OblikaZapisnika
  /* Izbrana predloga lige; null pri ekipni tekmi turnirja (brez lige). */
  predloga: PredlogaLige | null
  onIzbira: (izbira: IzbiraObrazca) => void
  razred?: string
}) {
  const vrednost: IzbiraObrazca = oblika === 'TURNIRKO' ? 'TURNIRKO' : (predloga ?? 'NTZS')

  return (
    <label className={razred}>
      Obrazec:
      <select value={vrednost} onChange={(d) => onIzbira(d.target.value as IzbiraObrazca)}>
        {predloga ? (
          PREDLOGE.map((p) => (
            <option key={p} value={p}>
              {OZNAKE_PREDLOGA_LIGE[p]}
            </option>
          ))
        ) : (
          <option value="NTZS">{OZNAKA_OBRAZCA_NTZS}</option>
        )}
        <option value="TURNIRKO">{OZNAKA_OBRAZCA_TURNIRKO}</option>
      </select>
    </label>
  )
}
