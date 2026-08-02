/* Seznam lig + ustvarjanje nove lige (prilagodljiva konfiguracija).
   Obrazec s pravili je skupen z urejanjem — glej LigaObrazecOkno. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import { OZNAKE_FORMAT, OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { ZnackaStatusa } from '../komponente/Znacka'

export function LigeStran() {
  const odjemalec = useQueryClient()
  const { smeUstvarjati } = useAvtentikacija()
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  return (
    <section>
      <div className="stran-glava stran-glava--dejanja">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Ekipna tekmovanja</span>
            <span className="naslov-strani__glavni">Lige</span>
          </h1>
          <p className="uvod">
            Ekipe, kader in razpored srečanj. Posamične tekme iz lig se lahko štejejo v
            klubski ELO.
          </p>
        </div>
        {smeUstvarjati && (
          <div className="naslovna-vrstica__desno">
            <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
              + Nova liga
            </button>
          </div>
        )}
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Vse lige</h2>
          {lige.data && lige.data.length > 0 && (
            <span className="sekcija__meta">
              {lige.data.length} {ligTekst(lige.data.length)}
            </span>
          )}
        </div>

        <NapakaPoizvedbe poizvedba={lige} kaj="lig" />
        {lige.isPending && <p className="obvestilo">Nalaganje …</p>}

        {lige.data && lige.data.length === 0 && (
          <p className="obvestilo">Ni še nobene lige. Ustvari prvo z gumbom »+ Nova liga«.</p>
        )}

        {lige.data && lige.data.length > 0 && (
          <div className="kartice">
            {lige.data.map((liga) => (
              <Link
                to={`/lige/${liga.id}`}
                className={`kartica kartica--liga kartica--${liga.status}`}
                key={liga.id}
              >
                <span className="kartica__glava">
                  <span className="kartica__ime">{liga.ime}</span>
                  <span className="kartica__podrobnost">
                    {[
                      liga.sezona ? `sezona ${liga.sezona}` : null,
                      OZNAKE_SPOL_KATEGORIJA[liga.spolKategorija].toLowerCase(),
                      liga.dvokrozno ? 'dvokrožno' : 'enokrožno',
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </span>
                <span className="vrstica__pod">
                  {liga.steviloEkip} {ekipTekst(liga.steviloEkip)}
                </span>
                <span className="vrstica__mono">{OZNAKE_FORMAT[liga.formatSrecanja]}</span>
                <span className="vrstica__mono">
                  {liga.stNapreduje > 0 || liga.stIzpade > 0
                    ? `${liga.stNapreduje} ↑ · ${liga.stIzpade} ↓`
                    : `na ${liga.steviloNizov} nizov`}
                </span>
                <ZnackaStatusa status={liga.status} />
              </Link>
            ))}
          </div>
        )}
      </div>

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

function ligTekst(n: number): string {
  if (n === 1) return 'liga'
  if (n === 2) return 'ligi'
  if (n === 3 || n === 4) return 'lige'
  return 'lig'
}
