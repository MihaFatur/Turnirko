/* Seznam lig + ustvarjanje nove lige (prilagodljiva konfiguracija).
   Obrazec s pravili je skupen z urejanjem — glej LigaObrazecOkno. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import { OZNAKE_FORMAT } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'

export function LigeStran() {
  const odjemalec = useQueryClient()
  const { jeAdmin } = useAvtentikacija()
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  return (
    <section>
      <div className="naslovna-vrstica">
        <h1>Lige</h1>
        {jeAdmin && (
          <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
            + Nova liga
          </button>
        )}
      </div>

      <SporociloNapake napaka={lige.error} />
      {lige.isPending && <p className="obvestilo">Nalaganje …</p>}

      {lige.data && lige.data.length === 0 && (
        <p className="obvestilo">Ni še nobene lige. Ustvari prvo z gumbom »+ Nova liga«.</p>
      )}

      {lige.data && lige.data.length > 0 && (
        <div className="kartice">
          {lige.data.map((liga) => (
            <Link to={`/lige/${liga.id}`} className="kartica" key={liga.id}>
              <div className="kartica__glava">
                <h2>{liga.ime}</h2>
                <ZnackaStatusa status={liga.status} />
              </div>
              <p className="kartica__podrobnost">
                {liga.sezona ? `Sezona ${liga.sezona} · ` : ''}
                {OZNAKE_FORMAT[liga.formatSrecanja]}
              </p>
              <p className="kartica__podrobnost">
                {liga.steviloEkip} {ekipTekst(liga.steviloEkip)} · {liga.dvokrozno ? 'dvokrožno' : 'enokrožno'}
              </p>
            </Link>
          ))}
        </div>
      )}

      {odprtObrazec && (
        <LigaObrazecOkno
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['lige'] })}
        />
      )}
    </section>
  )
}

function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
}
