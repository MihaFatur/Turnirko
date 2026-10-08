/* Naslov strani v zavihku brskalnika, zgodovini in zaznamkih. Prej je imela
   vsaka stran isti naslov (»Turnirko – namizni tenis …«), zato so bili
   zavihki lige, turnirja in profila nerazločljivi, deljenje iz brskalnika pa
   je poslalo napačno ime.

   Zapis »<naslov> – Turnirko« je isti, kot ga strežnik vstavi v HTML za
   predogled povezave (zaledje: splet/PredogledStrani.PRIPONA) - stran, ki jo
   odpre WhatsApp, in stran v zavihku se imenujeta enako. */
import { useEffect } from 'react'

const PRIPONA = ' – Turnirko'

/* null/undefined = podatki se še nalagajo; naslov ostane, kakršen je. Ob
   odhodu s strani se vrne prejšnji (osnovni) naslov. */
export function useNaslovStrani(naslov: string | null | undefined): void {
  useEffect(() => {
    if (!naslov) return
    const prej = document.title
    document.title = naslov + PRIPONA
    return () => {
      document.title = prej
    }
  }, [naslov])
}

/* Ime lige s sezono v eni vrsti - kot v predogledu (PredogledStoritev.imeLige). */
export function imeLigeSSezono(ime: string, sezona: string | null | undefined): string {
  return sezona && sezona.trim() ? `${ime} · ${sezona}` : ime
}
