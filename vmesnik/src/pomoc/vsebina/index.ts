/* Register vseh člankov pomoči in poizvedbe nad njim. Vrstni red v CLANKI je
   vrstni red v kazalu znotraj poglavja. */
import type { Blok, Clanek, IdPoglavja, VsebinaNamiga } from '../tipi'
import { ADMIN } from './admin'
import { LIGA } from './liga'
import { POGLAVJA } from './poglavja'
import { REZULTATI } from './rezultati'
import { TEKMOVANJE } from './tekmovanje'
import { TISK } from './tisk'
import { TURNIR } from './turnir'
import { VODICI } from './vodici'
import { ZACETEK } from './zacetek'

export { POGLAVJA }

export const CLANKI: Clanek[] = [
  ...VODICI,
  ...ZACETEK,
  ...TURNIR,
  ...REZULTATI,
  ...LIGA,
  ...TISK,
  ...TEKMOVANJE,
  ...ADMIN,
]

const PO_ID = new Map(CLANKI.map((c) => [c.id, c]))

export function najdiClanek(id: string): Clanek | undefined {
  return PO_ID.get(id)
}

/* Admin vidi vse, organizator vse razen poglavja za admina. */
export function smeVideti(clanek: Clanek, jeAdmin: boolean): boolean {
  return jeAdmin || !clanek.samoAdmin
}

export function clankiPoglavja(poglavje: IdPoglavja, jeAdmin: boolean): Clanek[] {
  return CLANKI.filter((c) => c.poglavje === poglavje && smeVideti(c, jeAdmin))
}

export type Razdelek = Extract<Blok, { tip: 'razdelek' }>

export function razdelkiClanka(clanek: Clanek): Razdelek[] {
  return clanek.vsebina.filter((b): b is Razdelek => b.tip === 'razdelek')
}

/* Oblaček ob gumbu »?«: razdelek s svojim kratkim besedilom ima prednost,
   sicer velja povzetek članka. Naslov oblačka je naslov razdelka, kadar gumb
   kaže nanj — gumb stoji ob tisti funkciji, ne ob celem področju. */
export function vsebinaNamiga(idClanka: string, idRazdelka?: string): VsebinaNamiga | null {
  const clanek = PO_ID.get(idClanka)
  if (!clanek) return null
  const razdelek = idRazdelka
    ? razdelkiClanka(clanek).find((r) => r.id === idRazdelka)
    : undefined
  if (razdelek?.kratko) {
    /* Shema članka bi ob ozki funkciji kazala celo področje, zato razdelek
       nosi samo svojo. */
    return { naslov: razdelek.naslov, besedilo: razdelek.kratko, shema: razdelek.shemaNamiga }
  }
  return { naslov: clanek.naslov, besedilo: clanek.kratko, shema: clanek.shemaNamiga }
}

/* Sosednja članka v istem poglavju (za »Naprej« na dnu strani). */
export function sosedaClanka(clanek: Clanek, jeAdmin: boolean): { prej?: Clanek; naprej?: Clanek } {
  const seznam = clankiPoglavja(clanek.poglavje, jeAdmin)
  const i = seznam.findIndex((c) => c.id === clanek.id)
  return { prej: i > 0 ? seznam[i - 1] : undefined, naprej: seznam[i + 1] }
}
