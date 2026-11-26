/* Zapisniki celega kola za bralnik (V79): organizator jih natisne pred kolom in
   razdeli ekipam (lastnik 26. 11. 2026), po kolu jih fotografira ali skenira na
   strani Branje zapisnikov. Vsako srečanje kola, ki še ni končano, dobi svoj
   ležeči A4. Oblika: samodejna (vrstice tekem, kadar gre kader nanje, sicer
   tabela kadra) ali izbrana za vse liste kola.

   Stran je zunaj skupne postavitve (tisk brez navigacije), ?kolo= izbere kolo
   (privzeto naslednje kolo lige). */
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import type { OblikaZapisnikaBralnika } from '../api/tipi'
import { ligeApi } from '../api/zahteve'
import { ListiZapisnikovZaBralnik } from '../komponente/ListiZapisnikovZaBralnik'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { vKolih } from '../pomozno/prestavitve'

const OBLIKE: { vrednost: OblikaZapisnikaBralnika | ''; oznaka: string }[] = [
  { vrednost: '', oznaka: 'Samodejna (vrstice, pri večjem kadru tabela)' },
  { vrednost: 'VRSTICE', oznaka: 'Vrstica tekme' },
  { vrednost: 'KADER', oznaka: 'Tabela kadra' },
]

function srecanj(n: number): string {
  const o = n % 100
  return o === 1 ? 'srečanje' : o === 2 ? 'srečanji' : o === 3 || o === 4 ? 'srečanja' : 'srečanj'
}

export function ZapisnikiKolaStran() {
  const { id } = useParams()
  const idLiga = Number(id)
  const [parametri, nastaviParametre] = useSearchParams()

  const liga = useQuery({ queryKey: ['liga', idLiga], queryFn: () => ligeApi.najdi(idLiga) })
  const srecanja = useQuery({ queryKey: ['srecanja', idLiga], queryFn: () => ligeApi.srecanja(idLiga) })

  if (liga.isPending || srecanja.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (liga.error || !liga.data) return <SporociloNapake napaka={liga.error} />
  if (srecanja.error || !srecanja.data) return <SporociloNapake napaka={srecanja.error} />

  const redna = vKolih(srecanja.data.filter((s) => s.idSerija == null))
  const kola = [...new Set(redna.map((s) => s.kolo))].sort((a, b) => a - b)
  const izKoraka = Number(parametri.get('kolo'))
  // oblika je v naslovu strani (?oblika=VRSTICE|KADER), da se izbira ohrani ob osvežitvi
  const izNaslova = parametri.get('oblika')
  const oblika: OblikaZapisnikaBralnika | '' = izNaslova === 'VRSTICE' || izNaslova === 'KADER' ? izNaslova : ''
  const nastavi = (kljuc: string, vrednost: string) => {
    const novi = new URLSearchParams(parametri)
    if (vrednost) novi.set(kljuc, vrednost)
    else novi.delete(kljuc)
    nastaviParametre(novi, { replace: true })
  }
  const kolo = kola.includes(izKoraka) ? izKoraka : (liga.data.naslednje?.kolo ?? kola[0] ?? 1)
  const vKolu = redna.filter((s) => s.kolo === kolo && s.status !== 'KONCANO')

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/lige/${idLiga}`} className="povezava-nazaj">
          ← Nazaj na ligo
        </Link>
        <label className="listki-orodja__izbira">
          Kolo:
          <select value={kolo} onChange={(d) => nastavi('kolo', d.target.value)}>
            {kola.map((k) => (
              <option key={k} value={k}>
                {k}. kolo
              </option>
            ))}
          </select>
        </label>
        <label className="listki-orodja__izbira">
          Oblika:
          <select value={oblika} onChange={(d) => nastavi('oblika', d.target.value)}>
            {OBLIKE.map((o) => (
              <option key={o.vrednost} value={o.vrednost}>
                {o.oznaka}
              </option>
            ))}
          </select>
        </label>
        <Link to="/zapisniki/branje" className="gumb">
          Branje zapisnikov
        </Link>
        <button className="gumb gumb--glavni" onClick={() => window.print()} disabled={vKolu.length === 0}>
          Natisni
        </button>
      </div>

      <div className="listki-stran listki-stran--bralnik">
        <header className="listki-naslov zaslon-samo">
          <h1>
            {liga.data.ime} · {kolo}. kolo
          </h1>
          <p>
            Zapisniki za bralnik: {vKolu.length} {srecanj(vKolu.length)}. Po kolu jih
            fotografiraj ali skeniraj na strani Branje zapisnikov.
          </p>
        </header>
        <ListiZapisnikovZaBralnik srecanja={vKolu.map((s) => s.id)} oblika={oblika === '' ? null : oblika} />
      </div>
    </>
  )
}
