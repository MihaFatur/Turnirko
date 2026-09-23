/* Povratna stran po Stripe Checkoutu (placana registracija).

   Racun ob uspehu se NE obstaja takoj: nastane sele, ko Stripe webhook
   potrdi placilo (PlacilaStoritev na zaledju) - to lahko traja nekaj sekund
   po preusmeritvi sem, zato stran ne poskusa prijave, ampak samo pove, kaj
   sledi (koda po e-posti) oz. da je bilo placilo preklicano. */
import { Link, useSearchParams } from 'react-router-dom'

export function RegistracijaZakljucenaStran() {
  const [iskanje] = useSearchParams()
  const uspeh = iskanje.get('stanje') !== 'preklic'

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
              : 'Plačilo ni bilo dokončano in račun zato ni nastal. Poskusi znova iz menija ' +
                '»Ustvari račun« v desnem zgornjem kotu.'}
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
