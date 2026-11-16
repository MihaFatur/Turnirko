/* Osvezevanje podatkov med tekmovanjem.

   Gledalec brez prijave je uporabnik st. 1 in njegovo prvo opravilo je
   "spremljam turnir v zivo" (PRODUCT.md). Brez tega bi moral rocno osvezevati
   stran, da bi videl nov rezultat. Osvezujemo SAMO takrat, ko tekmovanje res
   tece - zakljucen turnir se ne spreminja, poizvedovanje pa bi po nepotrebnem
   jemalo baterijo in prenos v dvorani s slabim signalom. */

import { NapakaStreznika } from '../api/odjemalec'

/* 15 s je kompromis: rezultat tekme se vpise vsakih nekaj minut, hkrati pa
   telefon ne posilja zahtev pogosteje, kot clovek pogleda v zaslon. */
export const INTERVAL_V_ZIVO = 15_000

/* Statusi, ob katerih se podatki se spreminjajo (turnir, dogodek, liga,
   srecanje, tekma). Vse drugo je priprava ali zgodovina. */
const V_TEKU = new Set(['V_TEKU', 'POTEKA', 'V_IGRI', 'PRIPRAVLJENA'])

export function jeVZivo(status?: string | null): boolean {
  return status !== undefined && status !== null && V_TEKU.has(status)
}

/* Vrednost za TanStack Query `refetchInterval`: interval, dokler tekmovanje
   tece, sicer false (brez samodejnega osvezevanja). */
export function intervalOsvezevanja(status?: string | null): number | false {
  return jeVZivo(status) ? INTERVAL_V_ZIVO : false
}

/* "18:42" - ura zadnjega uspesnega odgovora streznika. Gledalec mora vedeti,
   kako sveza je stevilka, ki jo gleda. */
export function uraOsvezitve(casMs: number | undefined): string | null {
  if (!casMs) return null
  return new Date(casMs).toLocaleTimeString('sl-SI', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

/* ---------- Kaj stran izriše glede na stanje svoje glavne poizvedbe ---------- */

/* Del rezultata poizvedbe (TanStack Query), ki ga pravilo bere. */
export interface StanjePoizvedbe {
  data: unknown
  error: unknown
  isLoading: boolean
  isPaused: boolean
}

/* Napaka, ki je ponovni prenos ne odpravi: zapisa ni več (404, 410) ali
   gledalec do njega nima pravice (401, 403). Izpad povezave (napaka brez
   odgovora), 5xx, 408 in 429 so prehodni. */
export function jeTrajnaNapaka(napaka: unknown): boolean {
  return (
    napaka instanceof NapakaStreznika
    && napaka.stanje >= 400
    && napaka.stanje < 500
    && napaka.stanje !== 408
    && napaka.stanje !== 429
  )
}

export type PrikazStrani = 'vsebina' | 'nalaganje' | 'brez-povezave' | 'napaka'

/* Stran dogodka, turnirja, lige in srečanja: kaj pokazati.

   PODATKI, KI SO ŽE NA ZASLONU, IMAJO PREDNOST pred prehodno napako in pred
   izpadom povezave. TanStack Query ob neuspeli osvežitvi podatke obdrži,
   nastavi pa `error` (ob izpadu `isPaused`); stran je prej najprej gledala
   ta dva in gledalcu ob enem neuspelem prenosu zamenjala rezultate z napako.
   Zdaj rezultati ostanejo, napako pove OpozoriloOsvezitve nad njimi.

   Celostranski nadomestek ostane, kadar podatkov ni (prvo nalaganje) in ob
   trajni napaki: izbrisanega dogodka ne kažemo naprej, kot da še obstaja.

   'vsebina' pomeni »izriši stran«; ali podatki res so, preveri stran sama
   (odgovor brez zapisa je »Tega dogodka ni (več)«). */
export function prikazStrani(poizvedba: StanjePoizvedbe): PrikazStrani {
  if (poizvedba.data != null && !jeTrajnaNapaka(poizvedba.error)) return 'vsebina'
  /* isLoading, ne isPending: ustavljena poizvedba (brez povezave) ali napaka
     ne smeta obviseti v večnem »Nalaganje …«. */
  if (poizvedba.isLoading) return 'nalaganje'
  if (poizvedba.isPaused) return 'brez-povezave'
  if (poizvedba.error) return 'napaka'
  return 'vsebina'
}

/* Ali stran kaže podatke, ki jih zadnja osvežitev ni uspela obnoviti. Ob
   izpadu povezave (`isPaused`) to že pove trak »Ni povezave« v postavitvi,
   zato drugega opozorila ni. */
export function osvezitevNiUspela(
  poizvedba: Pick<StanjePoizvedbe, 'data' | 'error' | 'isPaused'>,
): boolean {
  return (
    poizvedba.data != null
    && poizvedba.error != null
    && !poizvedba.isPaused
    && !jeTrajnaNapaka(poizvedba.error)
  )
}
