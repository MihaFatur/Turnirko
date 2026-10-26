/* Pomanjšanje lista za tisk na ožjem zaslonu. List (npr. ležeči A4, 297 mm) je
   širši od telefona, zato ga zaslon pomanjša s CSS zoomom prek spremenljivke
   --merilo-lista; pri tisku se pomanjšanje izključi (slog.css, @media print).
   Merilo je razmerje med širino okvira (brez odmikov) in širino lista, nikoli
   večje od 1. */
import { type CSSProperties, useEffect, useState } from 'react'

/* A4 pri 96 dpi: pokončno 210 mm = 793,7 px, ležeče 297 mm = 1122,5 px. */
export const SIRINA_LEZECEGA_LISTA_PX = 1123

export function useMeriloLista(sirinaListaPx: number): {
  /* Ref okvira, v katerem stojijo listi. */
  okvir: (el: HTMLDivElement | null) => void
  slog: CSSProperties | undefined
} {
  const [element, nastaviElement] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)

  useEffect(() => {
    if (!element) return
    const izmeri = () => {
      const slog = getComputedStyle(element)
      const notranja = element.clientWidth - parseFloat(slog.paddingLeft) - parseFloat(slog.paddingRight)
      nastaviMerilo(Math.min(1, notranja / sirinaListaPx))
    }
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(element)
    return () => opazovalec.disconnect()
  }, [element, sirinaListaPx])

  return {
    okvir: nastaviElement,
    slog: merilo < 1 ? ({ '--merilo-lista': merilo } as CSSProperties) : undefined,
  }
}
