/* Števka 28 × 28 iz zaledja (base64, 0 = papir) na platnu: črnilo temno na
   belem. Velikost na zaslonu določi CSS (image-rendering: pixelated). */
import { useEffect, useRef } from 'react'

import { slikaStevke } from '../pomozno/vadnica'

export function SlikaStevke({ slika, className }: { slika: string; className?: string }) {
  const platno = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const risba = platno.current?.getContext('2d')
    if (risba) risba.putImageData(new ImageData(slikaStevke(slika), 28, 28), 0, 0)
  }, [slika])
  return <canvas ref={platno} className={className} width={28} height={28} aria-hidden="true" />
}
