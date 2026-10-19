/* Okvir strani za tisk žreba (skupin, izločilne mreže): orodna vrstica na
   zaslonu (nazaj, števec, »Natisni«) in list(i) pod njo. Stran je namenoma
   zunaj skupne postavitve (brez navigacije), da je natis čist; tiska brskalnik
   (window.print) - zaledje ne rabi ničesar svojega.

   Ležeči list (mreža) je širši od večine zaslonov, zato ga ožji zaslon
   pomanjša (CSS zoom prek --zreb-merilo); pri tisku se pomanjšanje izključi. */
import { type CSSProperties, type ReactNode, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { SporociloNapake } from './SporociloNapake'
import { useNaslovStrani } from '../pomozno/naslovStrani'

/* A4 ležeče = 297 mm = 1122,5 px pri 96 dpi. */
const SIRINA_LEZECEGA_LISTA_PX = 1123

export function ZrebOkvir({
  nazaj,
  nazajOznaka,
  naslov,
  nalaganje,
  napaka,
  stevec,
  steviloZaTisk,
  prazno,
  lezeci = false,
  children,
}: {
  nazaj: string
  nazajOznaka: string
  /* Naslov zavihka; brskalnik ga natisne v glavo lista. */
  naslov: string | null
  nalaganje: boolean
  napaka: Error | null
  /* Napis ob gumbu (»4 skupine«). */
  stevec: string
  /* Koliko je za tisk; pri 0 je gumb onemogočen in stran pove, zakaj. */
  steviloZaTisk: number
  prazno: string
  lezeci?: boolean
  children: ReactNode
}) {
  useNaslovStrani(naslov)

  const [okvir, nastaviOkvir] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)
  useEffect(() => {
    if (!okvir || !lezeci) return
    const izmeri = () => {
      const slog = getComputedStyle(okvir)
      const notranja = okvir.clientWidth - parseFloat(slog.paddingLeft) - parseFloat(slog.paddingRight)
      nastaviMerilo(Math.min(1, notranja / SIRINA_LEZECEGA_LISTA_PX))
    }
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(okvir)
    return () => opazovalec.disconnect()
  }, [okvir, lezeci])

  if (nalaganje) return <p className="obvestilo">Nalaganje …</p>
  if (napaka) return <SporociloNapake napaka={napaka} />

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={nazaj} className="povezava-nazaj">
          {nazajOznaka}
        </Link>
        <span className="listki-orodja__stevec">{stevec}</span>
        <button
          className="gumb gumb--glavni"
          disabled={steviloZaTisk === 0}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div
        className={'listki-stran zreb-stran' + (lezeci ? ' zreb-stran--lezeci' : '')}
        ref={nastaviOkvir}
        style={lezeci && merilo < 1 ? ({ '--zreb-merilo': merilo } as CSSProperties) : undefined}
      >
        {steviloZaTisk === 0 ? <p className="obvestilo zaslon-samo">{prazno}</p> : children}
      </div>
    </>
  )
}
