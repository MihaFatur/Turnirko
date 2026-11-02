/* Logotip lige ali turnirja v glavi strani (V54).

   Logotip ni ikona namesto besede (DESIGN.md, točka 9): ime tekmovanja stoji
   tik ob njem, zato je slika za bralnik zaslona okras (alt=""). Velikost je po
   enaki ploščini (meraLogotipa), da širok napis in kvadraten grb na strani
   tehtata enako. Prostor je rezerviran z merama, zato se glava ob nalaganju
   slike ne premakne. */
import type { SlikaDto } from '../api/tipi'
import { meraLogotipa, urlSlike } from '../pomozno/slike'

/* Ploščina in meje po mestu: na namizju ob 80 px naslovu, na telefonu ob
   32 px naslovu v lepljivi glavi (tam prostor nosi ime lige). */
const MERE = {
  glava: { ploscina: 104 * 104, sirina: 280, visina: 120 },
  telefon: { ploscina: 44 * 44, sirina: 88, visina: 48 },
} as const

interface Lastnosti {
  logotip: SlikaDto | null
  mesto: keyof typeof MERE
}

export function LogotipTekmovanja({ logotip, mesto }: Lastnosti) {
  if (!logotip) return null
  const m = MERE[mesto]
  const mera = meraLogotipa(logotip, m.ploscina, m.sirina, m.visina)
  return (
    <img
      className={'logotip-tekmovanja logotip-tekmovanja--' + mesto}
      src={urlSlike(logotip, 'ZASLON')}
      width={mera.width}
      height={mera.height}
      alt=""
      decoding="async"
    />
  )
}
