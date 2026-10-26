/* Logotipi lig, ki jih ima Turnirkov zapisnik v glavi. Liga je lahko brez
   logotipa (večina) - takrat glava nosi samo znamko Turnirka.

   Logotip pripada ligi in ne obrazcu, zato ga izbere ime lige: liga nima polja
   za sliko, kar je za zdaj ena sama liga, pa ne upraviči nove sheme. Samo
   lige - ekipna tekma turnirja logotipa nikoli nima (klub »Savinja« bi sicer
   dal svoj logotip vsakemu turnirju, na katerem igra). */
import savinja from '../assets/liga-savinja.png'
import { zaIskanje } from './iskanje'

export interface LogotipLige {
  slika: string
  /* Besedilo za bralnik zaslona in za primer, da se slika ne naloži. */
  ime: string
}

const LOGOTIPI: { ujemanje: RegExp; logotip: LogotipLige }[] = [
  { ujemanje: /\bsavinj/, logotip: { slika: savinja, ime: 'Liga Savinja' } },
]

export function logotipLige(imeLige: string): LogotipLige | null {
  const ime = zaIskanje(imeLige)
  return LOGOTIPI.find((l) => l.ujemanje.test(ime))?.logotip ?? null
}
