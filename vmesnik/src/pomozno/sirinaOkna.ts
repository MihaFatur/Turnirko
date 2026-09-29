/* Prelomna točka namizne postavitve organizatorskega pregleda (1024 px).

   Pregled ima DVE drevesi, ne le dva sloga: namizna stran ima pas sezone,
   gumba za ustvarjanje in dva stolpca, telefonska pa krajše vrstice in en sam
   seznam sezone (predaja design_handoff_organizatorski_pregled). Zato izbiro
   drevesa nosi JS, mere in barve pa CSS. Pod 640 px stoji poleg tega druga
   glava in spodnja vrstica (Postavitev, useTelefon) - to je neodvisno od te
   točke. */
import { useEffect, useState } from 'react'

export const POIZVEDBA_NAMIZJE = '(min-width: 1024px)'

export function useNamizje(): boolean {
  const [namizje, nastavi] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(POIZVEDBA_NAMIZJE).matches,
  )

  useEffect(() => {
    const poizvedba = window.matchMedia(POIZVEDBA_NAMIZJE)
    const obSpremembi = (dogodek: MediaQueryListEvent) => nastavi(dogodek.matches)
    nastavi(poizvedba.matches)
    poizvedba.addEventListener('change', obSpremembi)
    return () => poizvedba.removeEventListener('change', obSpremembi)
  }, [])

  return namizje
}
