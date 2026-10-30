/* Profilne slike igralcev (V56): naslov, poizvedba za več igralcev hkrati in
   izrez kvadrata v brskalniku.

   Slika se kaže na treh mestih - glava profila, 1 na 1 in stopničke - in
   nikjer drugje (lestvica, seznami, mreža, tisk): pri nekaj odstotkih igralcev
   s sliko bi bil vsak seznam lisast, telefon v dvorani pa bi nalagal desetine
   slik. Kjer stoji več igralcev hkrati (1 na 1, stopničke), okvir za sliko
   dobijo VSI, kakor hitro ga ima eden - sicer bi se imena zamaknila. */
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { profilneSlikeApi } from '../api/zahteve'

export type VelikostProfilneSlike = 'VELIKA' | 'MALA'

/* VELIKA 480 px (glava profila), MALA 160 px (1 na 1, stopničke). */
export function urlProfilneSlike(zgostitev: string, velikost: VelikostProfilneSlike): string {
  return `/api/v1/slike/profil/${zgostitev}?velikost=${velikost}`
}

/* Javne slike danih igralcev: id igralca -> zgostitev. Igralca brez javne slike
   v zemljevidu ni. Ena zahteva za vse (stopničke, par v 1 na 1). */
export function useSlikeIgralcev(idji: (number | null | undefined)[]): Map<number, string> {
  const urejeni = [...new Set(idji.filter((id): id is number => typeof id === 'number'))].sort((a, b) => a - b)
  const slike = useQuery({
    queryKey: ['slike-igralcev', urejeni],
    queryFn: () => profilneSlikeApi.vidne(urejeni),
    enabled: urejeni.length > 0,
    staleTime: 5 * 60 * 1000,
  })
  return useMemo(
    () => new Map((slike.data ?? []).map((s) => [s.idIgralec, s.zgostitev])),
    [slike.data],
  )
}

/* Stranica, ki gre na strežnik; ta jo zmanjša na 480 in 160 px. Večja bi bila
   samo počasnejše nalaganje v dvorani. */
export const STRANICA_IZREZA = 960

/* Izrez kvadrata iz slike (x, y, stranica v pikah izvirnika) v JPEG. */
export async function izrezi(
  slika: ImageBitmap,
  x: number,
  y: number,
  stranica: number,
): Promise<Blob> {
  const izhod = Math.min(STRANICA_IZREZA, Math.round(stranica))
  const platno = document.createElement('canvas')
  platno.width = izhod
  platno.height = izhod
  const ctx = platno.getContext('2d')
  if (!ctx) throw new Error('Brskalnik ne zna risati na platno.')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, izhod, izhod)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(slika, x, y, stranica, stranica, 0, 0, izhod, izhod)
  return new Promise<Blob>((koncano, napaka) =>
    platno.toBlob(
      (b) => (b ? koncano(b) : napaka(new Error('Slike ni bilo mogoče zapisati.'))),
      'image/jpeg',
      0.9,
    ),
  )
}
