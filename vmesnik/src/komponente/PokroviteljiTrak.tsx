/* Pokrovitelji na dnu strani lige ali turnirja (V56).

   Trak je kolofon, ne oglas: pod vsebino, brez gibanja in brez prostora, ki bi
   ga jedel vsebini. Logotip je edino, po čemer se pokrovitelj spozna, zato ima
   alt z imenom. Glavni stoji prvi in je večji; velikost je po enaki ploščini
   (meraLogotipa), da širok napis in kvadraten grb tehtata enako. */
import { usePokrovitelje } from '../pomozno/pokrovitelji'
import { meraLogotipa, urlSlike } from '../pomozno/slike'

const MERA = {
  navadni: { ploscina: 56 * 56, sirina: 140, visina: 56 },
  glavni: { ploscina: 80 * 80, sirina: 200, visina: 80 },
} as const

export function PokroviteljiTrak({ vrsta, id }: { vrsta: 'liga' | 'turnir'; id: number }) {
  const seznam = usePokrovitelje(vrsta, id)
  if (seznam.length === 0) return null

  return (
    <aside className="pokrovitelji-trak" aria-label="Pokrovitelji">
      <span className="oznaka-polja">{seznam.length === 1 ? 'Pokrovitelj' : 'Pokrovitelji'}</span>
      <ul className="pokrovitelji-trak__seznam">
        {seznam.map((p) => {
          const m = MERA[p.glavni ? 'glavni' : 'navadni']
          const slika = (
            <img
              src={urlSlike(p.slika, 'ZASLON')}
              alt={p.ime}
              {...meraLogotipa(p.slika, m.ploscina, m.sirina, m.visina)}
              decoding="async"
              loading="lazy"
            />
          )
          return (
            <li key={`${p.slika.id}-${p.ime}`} className="pokrovitelji-trak__pokrovitelj">
              {p.povezava ? (
                <a href={p.povezava} target="_blank" rel="noopener noreferrer nofollow sponsored">
                  {slika}
                </a>
              ) : (
                slika
              )}
            </li>
          )
        })}
      </ul>
    </aside>
  )
}
