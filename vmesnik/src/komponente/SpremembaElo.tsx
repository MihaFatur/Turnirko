/* Majhna značka spremembe klubskega ELO ob tekmi: zelena za pridobljene
   točke (+16), rdeča za izgubljene (−16). Če vrednosti ni (tekma še ni
   obračunana ali gre za prosti prehod), se ne izriše nič. */
import type { ReactElement } from 'react'

interface Lastnosti {
  vrednost: number | null | undefined
}

export function SpremembaElo({ vrednost }: Lastnosti): ReactElement | null {
  if (vrednost === null || vrednost === undefined) return null

  const smer = vrednost > 0 ? 'poz' : vrednost < 0 ? 'neg' : 'nic'
  /* Pravi minus (−) namesto vezaja za lepši izpis negativne vrednosti. */
  const besedilo = vrednost > 0 ? `+${vrednost}` : vrednost < 0 ? `−${Math.abs(vrednost)}` : '±0'

  return (
    <span className={`sprememba-elo sprememba-elo--${smer}`} title="Sprememba ELO ob tej tekmi">
      {besedilo}
    </span>
  )
}
