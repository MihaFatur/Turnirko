/* Prelomna tocka mobilne postavitve.

   Vecino razlik med telefonom in namizjem nosi CSS. Nekaj pa jih CSS ne more
   nositi: na telefonu je vrstica turnirja DRUGA vsebina (obdobje in kraj v eni
   mono vrstici namesto dveh stolpcev), dejanja strani se preselijo v lepljivo
   glavo, seznam pa dobi zavihek, ki ga na namizju ni. Zato ista prelomna tocka
   (640 px, ista kot v slog.css) obstaja tudi v JS - a samo za IZBIRO DREVESA,
   nikoli za mere in barve. */
import { useEffect, useState } from 'react'

export const POIZVEDBA_TELEFON = '(max-width: 640px)'

export function useTelefon(): boolean {
  const [telefon, nastavi] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(POIZVEDBA_TELEFON).matches,
  )

  useEffect(() => {
    const poizvedba = window.matchMedia(POIZVEDBA_TELEFON)
    const obSpremembi = (dogodek: MediaQueryListEvent) => nastavi(dogodek.matches)
    /* Sirina se je lahko spremenila med prvim izrisom in ucinkom (zasuk
       naprave, odprta razvijalska orodja) - zato tudi tu enkrat preberemo. */
    nastavi(poizvedba.matches)
    poizvedba.addEventListener('change', obSpremembi)
    return () => poizvedba.removeEventListener('change', obSpremembi)
  }, [])

  return telefon
}
