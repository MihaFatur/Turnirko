/* Odjava od obvestil po kolu iz povezave v e-pošti (/odjava-obvestil?z=…).

   Obvestila se ne izklopijo že ob odprtju strani: varnostni pregledovalniki
   pošte povezave odprejo sami in bi prejemnika odjavili brez njegove vednosti.
   Zato je dejanje gumb (POST z žetonom). Odjemalci pošte, ki znajo odjavo z
   enim klikom (glava List-Unsubscribe), pošljejo POST na isti naslov API-ja
   sami in te strani ne vidijo. Prijava ni potrebna: žeton ne odpre ničesar
   razen izklopa obvestil. */
import { useMutation } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'

import { obvestilaApi } from '../api/zahteve'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { useNaslovStrani } from '../pomozno/naslovStrani'

export function OdjavaObvestilStran() {
  useNaslovStrani('Odjava od obvestil')
  const [parametri] = useSearchParams()
  const zeton = parametri.get('z')
  const odjava = useMutation({ mutationFn: obvestilaApi.odjava })

  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Obvestila po kolu</span>
            <span className="naslov-strani__glavni">
              {odjava.isSuccess ? 'Obvestila so izklopljena' : 'Odjava od obvestil'}
            </span>
          </h1>
          {!zeton ? (
            <p className="uvod">
              Povezava je nepopolna. Obvestila izklopiš tudi v meniju računa (»Obvestila«).
            </p>
          ) : odjava.isSuccess ? (
            <p className="uvod">
              Po kolih ti ne pošiljamo več e-pošte. Lige, ki jih spremljaš, ostanejo na domači
              strani; obvestila znova vklopiš v meniju računa (»Obvestila«).
            </p>
          ) : (
            <p className="uvod">
              Po vsakem odigranem kolu ti pošljemo izide, lestvico in tvoje tekme. Če jih ne želiš
              več, jih izklopi tukaj. Spremljanje lig ostane.
            </p>
          )}
          <SporociloNapake napaka={odjava.error} />
        </div>
      </div>
      <div className="stran-glava__dejanja">
        {zeton && !odjava.isSuccess && (
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={odjava.isPending}
            onClick={() => odjava.mutate(zeton)}
          >
            {odjava.isPending ? 'Odjavljam …' : 'Izklopi obvestila'}
          </button>
        )}
        <Link to="/" className="gumb">
          Na pregled
        </Link>
      </div>
    </section>
  )
}
