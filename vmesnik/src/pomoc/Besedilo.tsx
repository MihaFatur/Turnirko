/* Besedilo pomoči z dvema oznakama: **krepko** (ime gumba ali polja, kot ga
   organizator vidi) in [besedilo](clanek#razdelek) (skok na drug članek).

   Kam skok pelje, odloči vsebnik: v predalu ostane v predalu (organizator ne
   zapusti zaslona, s katerega je prišel), na strani Pomoč gre na naslov
   /pomoc/clanek#razdelek, da deluje tudi »Nazaj« brskalnika. */
import { createContext, useContext, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { MestoVPomoci } from './PomocKontekst'

export const SkokPomociKontekst = createContext<((mesto: MestoVPomoci) => void) | null>(null)

export function potDoClanka(mesto: MestoVPomoci): string {
  return `/pomoc/${mesto.clanek}${mesto.razdelek ? `#${mesto.razdelek}` : ''}`
}

export function razberiCilj(cilj: string): MestoVPomoci {
  const [clanek, razdelek] = cilj.split('#')
  return { clanek, razdelek: razdelek || undefined }
}

const VZOREC = /\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)]+)\)/g

export function Besedilo({ besedilo }: { besedilo: string }) {
  const skok = useContext(SkokPomociKontekst)
  const deli: ReactNode[] = []
  let zadnji = 0
  for (const zadetek of besedilo.matchAll(VZOREC)) {
    const mesto = zadetek.index ?? 0
    if (mesto > zadnji) deli.push(besedilo.slice(zadnji, mesto))
    if (zadetek[1] !== undefined) {
      deli.push(<strong key={mesto}>{zadetek[1]}</strong>)
    } else {
      const cilj = razberiCilj(zadetek[3])
      deli.push(
        skok ? (
          <a
            key={mesto}
            href={potDoClanka(cilj)}
            className="pomoc-povezava"
            onClick={(dogodek) => {
              /* Srednji klik in Ctrl+klik naj odpreta celo stran v novem zavihku. */
              if (dogodek.button !== 0 || dogodek.ctrlKey || dogodek.metaKey || dogodek.shiftKey) return
              dogodek.preventDefault()
              skok(cilj)
            }}
          >
            {zadetek[2]}
          </a>
        ) : (
          <Link key={mesto} to={potDoClanka(cilj)} className="pomoc-povezava">
            {zadetek[2]}
          </Link>
        ),
      )
    }
    zadnji = mesto + zadetek[0].length
  }
  if (zadnji < besedilo.length) deli.push(besedilo.slice(zadnji))
  return <>{deli}</>
}
