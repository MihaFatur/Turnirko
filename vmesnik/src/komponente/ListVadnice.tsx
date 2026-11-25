/* En natisljiv list vadnice števk (A4 pokončno, 210 × 297 mm).

   Oseba v vsako okence prepiše številko z začetka vrstice, kot jo piše na
   sodniški listek. Admin izpolnjen list fotografira na strani Vadnica števk in
   Turnirko iz njega dobi označene števke za učenje branja listkov.

   Oznake v vogalih, koda QR in okenca stojijo na STALNIH mestih (mm), ki jih
   pozna zaledje (si.turnirko.branje.GeometrijaVadnice) - zato je vse
   postavljeno absolutno, v mm, iz istih številk (pomozno/vadnica.ts). List je
   črno-bel ne glede na temo (natis na papir). Polja za ime ni (V4). */
import { ZnakTurnirko } from './Postavitev'
import { GEOMETRIJA as G, STOLPCEV, VREDNOSTI, oznakaLista, qrVadnice } from '../pomozno/vadnica'

const mm = (v: number) => `${v}mm`
const SIRINA = 210
const VISINA = 297
/* Debelina črte okenca (mm). */
const CRTA = 0.45

/* merilo: pomanjšanje za ozek zaslon (CSS zoom); pri tisku ga slog izključi. */
export function ListVadnice({ idLista, merilo = 1 }: { idLista: number; merilo?: number }) {
  const { velikost, pot } = qrVadnice(idLista)
  const polovica = G.stranicaOznake / 2

  return (
    <article
      className="vadnica-list"
      aria-label={`List vadnice ${oznakaLista(idLista)}`}
      style={merilo < 1 ? { zoom: merilo } : undefined}
    >
      {/* Oznake za poravnavo fotografije: polni črni kvadrati 6 × 6 mm. */}
      {[G.zgorajLevo, G.spodajLevo, G.spodajDesno].map(([x, y]) => (
        <svg
          key={`${x}-${y}`}
          className="vadnica-list__kot"
          style={{ left: mm(x - polovica), top: mm(y - polovica) }}
          viewBox="0 0 1 1"
          aria-hidden="true"
        >
          <rect width="1" height="1" />
        </svg>
      ))}

      <svg
        className="vadnica-list__qr"
        style={{ left: mm(G.qrX), top: mm(G.qrY), width: mm(G.qrStranica), height: mm(G.qrStranica) }}
        viewBox={`0 0 ${velikost} ${velikost}`}
        shapeRendering="crispEdges"
        aria-hidden="true"
      >
        <path d={pot} />
      </svg>
      <span
        className="vadnica-list__oznaka"
        style={{ left: mm(G.qrX - 4), top: mm(G.qrY + G.qrStranica + 1.5), width: mm(G.qrStranica + 8) }}
      >
        {oznakaLista(idLista)}
      </span>

      <header className="vadnica-list__glava">
        <span className="vadnica-list__znamka">
          <ZnakTurnirko velikost={18} />
          Turnirko
        </span>
        <h2 className="vadnica-list__naslov">Vadnica števk</h2>
        <p className="vadnica-list__navodilo">
          V vsako okence napiši številko z začetka vrstice, tako kot jo pišeš na sodniški
          listek: s kemičnim svinčnikom, v svoji pisavi, brez lepšanja. Dvomestno število
          (10, 11, 12, 13) napiši v eno okence. List izpolni ena oseba; imena ne piši.
        </p>
      </header>

      {VREDNOSTI.map((vrednost, i) => (
        <span
          key={i}
          className="vadnica-list__vzorec"
          style={{ top: mm(G.okenceY + i * G.korakY), height: mm(G.okenceVisina) }}
        >
          {vrednost}
        </span>
      ))}
      {/* Okenca so vektorska risba v mm: obroba CSS bi se pri tisku zaokrožila na
          celo piko zaslona (0,4 mm → 0,26 mm), tanka črta pa se na fotografiji
          raztrga (CeliceVadnice). Zunanji rob črte je natanko na mestu okenca. */}
      <svg className="vadnica-list__okenca" viewBox={`0 0 ${SIRINA} ${VISINA}`} aria-hidden="true">
        {VREDNOSTI.flatMap((_, i) =>
          Array.from({ length: STOLPCEV }, (_, j) => (
            <rect
              key={`${i}-${j}`}
              x={G.okenceX + j * G.korakX + CRTA / 2}
              y={G.okenceY + i * G.korakY + CRTA / 2}
              width={G.okenceSirina - CRTA}
              height={G.okenceVisina - CRTA}
            />
          )),
        )}
      </svg>

      <footer className="vadnica-list__noga">
        Napisane številke Turnirko uporabi samo za učenje branja sodniških listkov. Hvala!
      </footer>
    </article>
  )
}
