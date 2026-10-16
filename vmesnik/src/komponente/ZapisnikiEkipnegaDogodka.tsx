/* Natisljivi ekipni zapisniki vseh pripravljenih tekem ekipnega dogodka.

   Ekipna tekma turnirja je srecanje, zato je vsak list isti uradni obrazec
   NTZS kot pri ligi (ZapisnikEkipnegaDvoboja): imeni ekip sta ze natisnjeni,
   postavo in igralce posamicnih tekem / dvojic vpise ekipa sama. Vsak zapisnik
   dobi svoj list. Podatke bere iz poizvedbe srecanja, ki jo deli s stranjo
   srecanja, zato so ze vnesene postave in izidi natisnjeni, prazno pa ostane
   to, kar se ni znano. */
import { Link } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'

import { srecanjaApi } from '../api/zahteve'
import type { DogodekDto } from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { sklonZapisnikov } from '../pomozno/oblikovanje'
import { SporociloNapake } from './SporociloNapake'
import { ZapisnikEkipnegaDvoboja } from './ZapisnikEkipnegaDvoboja'

export function ZapisnikiEkipnegaDogodka({
  dogodek,
  imeTurnirja,
  idjiSrecanj,
}: {
  dogodek: DogodekDto
  imeTurnirja: string | null
  /* Srecanja tekem, za katere ima smisel natisniti zapisnik, v vrstnem redu
     tiska. */
  idjiSrecanj: number[]
}) {
  const srecanja = useQueries({
    queries: idjiSrecanj.map((id) => ({
      queryKey: ['srecanje', id],
      queryFn: () => srecanjaApi.podrobno(id),
    })),
  })

  const napaka = srecanja.find((q) => q.error)?.error
  const nalozeno = srecanja.every((q) => q.data !== undefined)

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/dogodki/${dogodek.id}`} className="povezava-nazaj">
          ← Nazaj na dogodek
        </Link>
        <span className="listki-orodja__stevec">
          {idjiSrecanj.length} {sklonZapisnikov(idjiSrecanj.length)}
        </span>
        <button
          className="gumb gumb--glavni"
          disabled={idjiSrecanj.length === 0 || !nalozeno}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div className="listki-stran listki-stran--zapisnik">
        {/* Naslov je samo za zaslon: vsak list ima svojo glavo obrazca. */}
        <header className="listki-naslov zaslon-samo">
          <h1>
            {imeTurnirja ? `${imeTurnirja} — ` : ''}
            {dogodek.ime}
          </h1>
          <p>
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
          </p>
        </header>

        {napaka ? (
          <SporociloNapake napaka={napaka} />
        ) : idjiSrecanj.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih ekipnih tekem za tiskanje. Zapisnik se ustvari za tekmo, kjer
            sta znani obe ekipi in še ni odigrana.
          </p>
        ) : !nalozeno ? (
          <p className="obvestilo zaslon-samo">Nalaganje …</p>
        ) : (
          srecanja.map(
            (q, i) =>
              q.data && (
                <div key={idjiSrecanj[i]} className="zapisnik-stran">
                  <ZapisnikEkipnegaDvoboja podrobno={q.data} liga={undefined} varianta={null} />
                </div>
              ),
          )
        )}
      </div>
    </>
  )
}
