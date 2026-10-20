/* Okvir strani za tisk žreba (listki skupin, izločilna mreža): orodna vrstica
   na zaslonu (nazaj, števec, »Natisni«) in list(i) pod njo. Stran je namenoma
   zunaj skupne postavitve (brez navigacije), da je natis čist; tiska brskalnik
   (window.print) - zaledje ne rabi ničesar svojega.

   List (pokončni 210 mm, ležeči 297 mm) je širši od telefona, zato ga ožji
   zaslon pomanjša (CSS zoom prek --zreb-merilo); pri tisku se pomanjšanje
   izključi. */
import { type CSSProperties, type ReactNode, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { SporociloNapake } from './SporociloNapake'
import { useNaslovStrani } from '../pomozno/naslovStrani'

/* A4 pri 96 dpi: pokončno 210 mm = 793,7 px, ležeče 297 mm = 1122,5 px. */
const SIRINA_POKONCNEGA_LISTA_PX = 794
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
  razred,
  orodja,
  navodilo,
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
  /* Dodaten razred strani (oblika lista, @page). */
  razred?: string
  /* Izbira ob števcu (npr. kaj nosi listek). */
  orodja?: ReactNode
  /* Navodilo za tisk nad listi; samo na zaslonu. */
  navodilo?: ReactNode
  children: ReactNode
}) {
  useNaslovStrani(naslov)

  const [okvir, nastaviOkvir] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)
  useEffect(() => {
    if (!okvir) return
    const sirinaLista = lezeci ? SIRINA_LEZECEGA_LISTA_PX : SIRINA_POKONCNEGA_LISTA_PX
    const izmeri = () => {
      const slog = getComputedStyle(okvir)
      const notranja = okvir.clientWidth - parseFloat(slog.paddingLeft) - parseFloat(slog.paddingRight)
      nastaviMerilo(Math.min(1, notranja / sirinaLista))
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
        {orodja}
        <button
          className="gumb gumb--glavni"
          disabled={steviloZaTisk === 0}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div
        className={
          'listki-stran zreb-stran' + (lezeci ? ' zreb-stran--lezeci' : '') + (razred ? ` ${razred}` : '')
        }
        ref={nastaviOkvir}
        style={merilo < 1 ? ({ '--zreb-merilo': merilo } as CSSProperties) : undefined}
      >
        {steviloZaTisk === 0 ? (
          <p className="obvestilo zaslon-samo">{prazno}</p>
        ) : (
          <>
            {navodilo && <p className="zreb-stran__navodilo zaslon-samo">{navodilo}</p>}
            {children}
          </>
        )}
      </div>
    </>
  )
}
