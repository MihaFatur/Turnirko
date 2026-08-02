/* Osvezevanje podatkov med tekmovanjem.

   Gledalec brez prijave je uporabnik st. 1 in njegovo prvo opravilo je
   "spremljam turnir v zivo" (PRODUCT.md). Brez tega bi moral rocno osvezevati
   stran, da bi videl nov rezultat. Osvezujemo SAMO takrat, ko tekmovanje res
   tece - zakljucen turnir se ne spreminja, poizvedovanje pa bi po nepotrebnem
   jemalo baterijo in prenos v dvorani s slabim signalom. */

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
