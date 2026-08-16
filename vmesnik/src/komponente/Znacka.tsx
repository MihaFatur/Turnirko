/* Barvna znacka statusa turnirja/dogodka. */
import type { StatusTekmovanja } from '../api/tipi'
import { OZNAKE_STATUS_TEKMOVANJA } from '../api/tipi'

export function ZnackaStatusa({ status }: { status: StatusTekmovanja }) {
  return (
    <span className={`znacka znacka--${status}`}>
      {OZNAKE_STATUS_TEKMOVANJA[status]}
    </span>
  )
}

/* Status na koncu enovrsticne vrstice na telefonu.

   Polna ploskev je rezervirana za "V teku" - to je edini status, ki gledalca
   pripelje v vrstico. Priprava in zaključen sta barvni mono oznaki: pas
   polnih znack cez cel seznam bi vlekel oko z imen, ki jih gledalec bere. */
export function StatusMobi({ status }: { status: StatusTekmovanja }) {
  if (status === 'V_TEKU') {
    return (
      <span className="znacka znacka--V_TEKU znacka--drobna">
        {OZNAKE_STATUS_TEKMOVANJA.V_TEKU}
      </span>
    )
  }
  return (
    <span className={`status-mobi status-mobi--${status}`}>
      {OZNAKE_STATUS_TEKMOVANJA[status]}
    </span>
  )
}

/* Znacka ob naslovu strani na telefonu (za odtenek nizja od tiste v vrstici). */
export function ZnackaVNaslovu({ status }: { status: StatusTekmovanja }) {
  return (
    <span className={`znacka znacka--${status} znacka--v-naslovu`}>
      {OZNAKE_STATUS_TEKMOVANJA[status]}
    </span>
  )
}
