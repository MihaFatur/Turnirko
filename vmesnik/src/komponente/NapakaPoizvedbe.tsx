/* Napaka pri BRANJU podatkov (za napake obrazcev je SporociloNapake).

   Zakaj svoja komponenta: ko poizvedba pade, stran brez tega prikaze prazno
   stanje ("Ni tekem.") - torej lazi. Prazen seznam in prekinjena povezava sta
   dve razlicni stvari; uporabnik v dvorani s slabim signalom mora videti,
   katera od njiju je, in imeti gumb, da poskusi znova. */
import { NapakaStreznika, opisNapake } from '../api/odjemalec'

interface Lastnosti {
  /* Del rezultata poizvedbe (TanStack Query). */
  poizvedba: {
    error: unknown
    isFetching: boolean
    refetch: () => unknown
  }
  /* Kaj se ni nalozilo - vstavi se v pojasnilo ("lestvice", "tekem" ...). */
  kaj?: string
}

/* Pot naprej glede na vzrok. Zaledje vraca slovenski opis, mi dodamo, kaj
   lahko clovek ukrene. */
function pojasnilo(napaka: unknown, kaj: string): string {
  if (napaka instanceof NapakaStreznika) {
    if (napaka.stanje === 401 || napaka.stanje === 403) {
      return 'Za ta pogled nimaš pravic. Prijavi se ali se vrni na javni del.'
    }
    if (napaka.stanje === 404) return 'Zapisa ni (več). Morda je bil izbrisan.'
    if (napaka.stanje >= 500) return 'Napaka je na strežniku, ne pri tebi. Poskusi čez trenutek.'
    return 'Poskusi znova; če se ponovi, sporoči organizatorju.'
  }
  return `Prikaz ${kaj} ni uspel. Preveri povezavo in poskusi znova.`
}

export function NapakaPoizvedbe({ poizvedba, kaj = 'podatkov' }: Lastnosti) {
  if (!poizvedba.error) return null
  return (
    <div className="napaka" role="alert">
      <div className="napaka__vrsta">
        <span>
          {opisNapake(poizvedba.error)}
          <span className="napaka__pojasnilo">{pojasnilo(poizvedba.error, kaj)}</span>
        </span>
        <button
          type="button"
          className="gumb gumb--majhen"
          onClick={() => poizvedba.refetch()}
          disabled={poizvedba.isFetching}
        >
          {poizvedba.isFetching ? 'Poskušam …' : 'Poskusi znova'}
        </button>
      </div>
    </div>
  )
}
