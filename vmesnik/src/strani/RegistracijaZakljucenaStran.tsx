/* Povratna stran po Stripe Checkoutu (placana registracija).

   Racun ob uspehu se NE obstaja takoj: nastane sele, ko Stripe webhook
   potrdi placilo (PlacilaStoritev na zaledju) - to lahko traja nekaj sekund
   po preusmeritvi sem. Ce je RegistracijaTok pred preusmeritvijo shranil
   stanje toka (glej pomozno/registracijaSeja.ts), se registracijsko okno
   odpre naravnost na koraku "koda" (design_handoff_onboarding, korak 7) -
   sicer (drug brskalnik/zavihek, pocisceno sessionStorage, preklic) stran
   pove samo, kaj sledi. */
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { PrijavaOkno } from '../komponente/PrijavaOkno'
import { preberiInPobrisiStanjePlacila } from '../pomozno/registracijaSeja'

export function RegistracijaZakljucenaStran() {
  const [iskanje] = useSearchParams()
  const navigate = useNavigate()
  const uspeh = iskanje.get('stanje') !== 'preklic'
  /* Prebere in TAKOJ pobriše - tudi ob preklicu, da ne obvisi za naslednji
     obisk te strani. Enkraten useState namesto klica v izrisu, ker branje
     sessionStorage ni cisto (in ker izbris sme steti samo enkrat). */
  const [stanje] = useState(() => preberiInPobrisiStanjePlacila())

  if (uspeh && stanje) {
    return (
      <PrijavaOkno
        zacetniNacin="registracija"
        obnovljenoStanjePlacila={stanje}
        onZapri={() => navigate('/')}
      />
    )
  }

  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Registracija</span>
            <span className="naslov-strani__glavni">
              {uspeh ? 'Plačilo sprejeto' : 'Plačilo preklicano'}
            </span>
          </h1>
          <p className="uvod">
            {uspeh
              ? 'Stripe je potrdil plačilo. Račun se ustvarja in čez trenutek dobiš ' +
                'šestmestno kodo za potrditev e-pošte - z njo dokončaš prijavo.'
              : 'Plačilo ni bilo dokončano in račun zato ni nastal. Poskusi znova: v ' +
                'desnem zgornjem kotu odpri »Gost · prijava« in izberi »Ustvari nov račun«.'}
          </p>
        </div>
      </div>
      <div className="stran-glava__dejanja">
        <Link to="/" className="gumb gumb--glavni">
          Na pregled
        </Link>
      </div>
    </section>
  )
}
