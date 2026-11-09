/* List izpisa v slogu SVR (pomozno/izpisSvr): en SVG za cel list. Viewbox je list v točkah
   (595,28 x 841,89), zato so koordinate iz modela že mere na papirju; SVG ne zaokroža črt
   na slikovne pike, kot jih HTML, in tanke črte vzorca (0,4 in 0,53 pt) ostanejo tanke.
   Črno-belo, samo rdeče točke v križni tabeli, kot v vzorcu. */
import { LIST_SVR, type ListSvr } from '../pomozno/izpisSvr'

const SIVA = '#969696'
const RDECA = '#ff0000'

export function IzpisSvrPlatno({ list }: { list: ListSvr }) {
  return (
    <svg
      className="izpis-svr"
      viewBox={`0 0 ${LIST_SVR.sirina} ${LIST_SVR.visina}`}
      preserveAspectRatio="xMinYMin meet"
    >
      <g transform={list.merilo < 1 ? `scale(${list.merilo})` : undefined}>
        {list.polnila.map((p, i) => (
          <rect key={`p${i}`} x={p.x} y={p.y} width={p.sirina} height={p.visina} fill={SIVA} />
        ))}
        {list.crte.map((c, i) => (
          <line key={`c${i}`} x1={c.x1} y1={c.y1} x2={c.x2} y2={c.y2} stroke="#000" strokeWidth={c.debelina} />
        ))}
        {list.besedila.map((b, i) => (
          <text
            key={`b${i}`}
            x={b.x}
            y={b.y}
            fontSize={b.velikost}
            fontWeight={b.krepko ? 700 : 400}
            textAnchor={b.sidro ?? 'start'}
            fill={b.rdece ? RDECA : '#000'}
          >
            {b.besedilo}
          </text>
        ))}
      </g>
    </svg>
  )
}
