/* Tisk listov vadnice števk (/vadnica/tisk?n=10, samo admin).

   Vsak list dobi svoj naključni id (koda QR in oznaka V-…): en list je en pisec,
   zato se iz istega lista nikoli ne učimo in preizkušamo hkrati. Id-je izbere
   brskalnik (pomozno/vadnica.ts), strežnik jih spozna šele ob fotografiji, zato
   tisk ne potrebuje strežnika in list z enega okolja velja tudi na drugem.
   Ponovni obisk strani da nove id-je; že natisnjeni listi ostanejo veljavni.

   Stran je zunaj skupne postavitve (brez navigacije), da je natis čist, kot pri
   sodniških listkih (ListkiStran). */
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ListVadnice } from '../komponente/ListVadnice'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { NAJVEC_LISTOV, noviIdjiListov } from '../pomozno/vadnica'

/* Širina lista A4 na zaslonu (210 mm pri 96 dpi): ožji zaslon ga pomanjša. */
const SIRINA_LISTA_PX = (210 / 25.4) * 96

function steviloListov(vnos: string | null): number {
  const n = Number(vnos)
  return Number.isInteger(n) && n >= 1 ? Math.min(n, NAJVEC_LISTOV) : 10
}

export function VadnicaTiskStran() {
  useNaslovStrani('Vadnica števk – tisk')
  const [parametri, nastaviParametre] = useSearchParams()
  const n = steviloListov(parametri.get('n'))
  const [vnos, nastaviVnos] = useState(String(n))
  /* Id-ji se izberejo enkrat na število listov (ob vsakem izrisu bi se koda
     menjala); ob spremembi števila se izberejo znova že med izrisom. */
  const [listi, nastaviListe] = useState(() => ({ n, idji: noviIdjiListov(n) }))
  if (listi.n !== n) nastaviListe({ n, idji: noviIdjiListov(n) })
  const idji = listi.idji

  const [okvir, nastaviOkvir] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)
  useEffect(() => {
    if (!okvir) return
    const izmeri = () => nastaviMerilo(Math.min(1, okvir.clientWidth / SIRINA_LISTA_PX))
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(okvir)
    return () => opazovalec.disconnect()
  }, [okvir])

  const potrdiStevilo = () => {
    const novo = steviloListov(vnos)
    nastaviVnos(String(novo))
    nastaviParametre({ n: String(novo) }, { replace: true })
  }

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to="/vadnica" className="povezava-nazaj">
          ← Vadnica števk
        </Link>
        <label className="vadnica-tisk__stevilo">
          Listov
          <input
            type="number"
            inputMode="numeric"
            min={1}
            max={NAJVEC_LISTOV}
            value={vnos}
            onChange={(e) => nastaviVnos(e.target.value)}
            onBlur={potrdiStevilo}
            onKeyDown={(e) => e.key === 'Enter' && potrdiStevilo()}
          />
        </label>
        <button type="button" className="gumb" onClick={() => nastaviListe({ n, idji: noviIdjiListov(n) })}>
          Nove kode
        </button>
        <button type="button" className="gumb gumb--glavni listki-orodja__desno" onClick={() => window.print()}>
          Natisni ({n})
        </button>
      </div>

      <div className="listki-stran listki-stran--vadnica">
        <header className="listki-naslov zaslon-samo">
          <h1>Vadnica števk</h1>
          <p className="listki-naslov__navodilo">
            Natisni na A4 v merilu 100 %, brez robov ter brez glave in noge. Vsak list
            izpolni ena oseba s kemičnim svinčnikom; vsak list ima svojo kodo, zato listov
            ne fotokopiraj. Izpolnjene fotografiraj na strani Vadnica števk.
          </p>
        </header>

        <h2 className="listki-predogled zaslon-samo">Predogled natisa</h2>
        <div className="listki-listi" ref={nastaviOkvir}>
          {idji.map((id) => (
            <ListVadnice key={id} idLista={id} merilo={merilo} />
          ))}
        </div>
      </div>
    </>
  )
}
