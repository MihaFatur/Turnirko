/* Natisljivi uraden ekipni zapisnik (NTZS) za eno srecanje lige.

   En list na srecanje: mreza vseh posamicnih tekem z imeni, sodnik ga izpolni
   z roko za mizo. Pot je zunaj skupne postavitve (brez navigacije), tiska
   brskalnik (window.print), brez zaledja in nove sheme. Predlogo (1. SNTL oz.
   2./3. SNTL) doloca liga, na tej strani pa jo je mogoce zacasno preklopiti za
   ta natis. */
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { ligeApi, srecanjaApi } from '../api/zahteve'
import type { PredlogaLige } from '../api/tipi'
import { OZNAKE_PREDLOGA_LIGE } from '../api/tipi'
import { ZapisnikEkipnegaDvoboja } from '../komponente/ZapisnikEkipnegaDvoboja'
import { SporociloNapake } from '../komponente/SporociloNapake'

export function ListkiSrecanjaStran() {
  const { id } = useParams()
  const idSrecanje = Number(id)

  const podrobno = useQuery({
    queryKey: ['srecanje', idSrecanje],
    queryFn: () => srecanjaApi.podrobno(idSrecanje),
  })

  /* Ime, sezona in privzeta predloga so na ligi; srecanje pozna le njen id. */
  const idLiga = podrobno.data?.srecanje.idLiga
  const liga = useQuery({
    queryKey: ['liga', idLiga],
    queryFn: () => ligeApi.najdi(idLiga!),
    enabled: idLiga !== undefined,
  })

  /* null = uporabi privzeto predlogo lige; sicer zacasna izbira za ta natis. */
  const [varianta, nastaviVarianto] = useState<PredlogaLige | null>(null)

  if (podrobno.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.error || !podrobno.data) return <SporociloNapake napaka={podrobno.error} />

  const izbrana: PredlogaLige = varianta ?? liga.data?.predlogaListka ?? 'SNTL_23'
  const srecanje = podrobno.data.srecanje

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/srecanja/${idSrecanje}`} className="povezava-nazaj">
          ← Nazaj na srečanje
        </Link>
        <label className="listki-orodja__izbira">
          Predloga:
          <select value={izbrana} onChange={(d) => nastaviVarianto(d.target.value as PredlogaLige)}>
            {(Object.keys(OZNAKE_PREDLOGA_LIGE) as PredlogaLige[]).map((p) => (
              <option key={p} value={p}>{OZNAKE_PREDLOGA_LIGE[p]}</option>
            ))}
          </select>
        </label>
        <button className="gumb gumb--glavni" onClick={() => window.print()}>
          Natisni
        </button>
      </div>

      <div className="listki-stran listki-stran--zapisnik">
        {/* Naslov je samo za zaslon: natisne se uraden obrazec, ki ima svojo
            glavo in ne prenese dodatkov nad njo. */}
        <header className="listki-naslov zaslon-samo">
          <h1>
            {srecanje.domaci} : {srecanje.gost}
          </h1>
          <p>
            {liga.data ? `${liga.data.ime} · ` : ''}
            {srecanje.kolo}. kolo
          </p>
        </header>

        <ZapisnikEkipnegaDvoboja podrobno={podrobno.data} liga={liga.data} varianta={izbrana} />
      </div>
    </>
  )
}
