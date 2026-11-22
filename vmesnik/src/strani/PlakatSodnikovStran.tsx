/* Plakat za sodnike (S2): list A4 za ob mize s kodo QR, ki odpre stran za
   sodnika (/s), in tremi koraki. Stran je zunaj skupne postavitve, da je natis
   čist (kot listki). Plakat ne nosi nič skrivnega: pravico pošiljanja da šele
   koda na posameznem listku. */
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { turnirjiApi } from '../api/zahteve'
import { ZnakTurnirko } from '../komponente/Postavitev'
import { potQr } from '../pomozno/kodaListka'
import { useNaslovStrani } from '../pomozno/naslovStrani'

export function PlakatSodnikovStran() {
  const { id } = useParams()
  const turnir = useQuery({ queryKey: ['turnir', Number(id)], queryFn: () => turnirjiApi.najdi(Number(id)) })
  useNaslovStrani('Plakat za sodnike')
  const naslov = `${window.location.origin}/s`
  const { velikost, pot } = potQr(naslov, false)

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <button type="button" className="gumb gumb--glavni" onClick={() => window.print()}>
          Natisni plakat
        </button>
      </div>
      <article className="plakat-sodnikov">
        <header className="plakat-sodnikov__glava">
          <span className="plakat-sodnikov__znamka">
            <ZnakTurnirko velikost={40} />
            Turnirko
          </span>
          {turnir.data && <span className="plakat-sodnikov__turnir">{turnir.data.ime}</span>}
        </header>
        <h1 className="plakat-sodnikov__naslov">Sodnik? Izid pošlji s telefonom.</h1>
        <svg className="plakat-sodnikov__qr" viewBox={`-2 -2 ${velikost + 4} ${velikost + 4}`}
          shapeRendering="crispEdges" aria-label={naslov} role="img">
          <rect x={-2} y={-2} width={velikost + 4} height={velikost + 4} fill="#fff" />
          <path d={pot} fill="#000" />
        </svg>
        <p className="plakat-sodnikov__naslov-strani">{naslov.replace(/^https?:\/\//, '')}</p>
        <ol className="plakat-sodnikov__koraki">
          <li>Po tekmi s telefonom skeniraj to kodo (ali odpri naslov zgoraj).</li>
          <li>Fotografiraj izpolnjen listek od zgoraj – cel, s kodo v desnem kotu.</li>
          <li>Preveri točke in pritisni <strong>Potrdi izid</strong>. Listek nato oddaj na pultu.</li>
        </ol>
        <p className="plakat-sodnikov__drobno">
          Prijava ni potrebna. Brez telefona ali signala listek kot doslej oddaj na pultu.
        </p>
      </article>
    </>
  )
}
