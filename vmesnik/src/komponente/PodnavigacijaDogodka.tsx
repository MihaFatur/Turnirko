/* Lepljiv pas pod glavo strani dogodka: gumbi pogleda levo, povzetek desno.

   Pas se ne odloca po sistemu tekmovanja, ampak po tem, kaj dogodek DEJANSKO
   ima (skupine, izlocilne tekme, prijave). Zato nov sistem tekmovanja doda
   svoj pogled brez posega v to komponento, dogodek s sistemom, ki ga
   podnavigacija ne pozna, pa se izrise brez napake - le z gumbi, ki mu
   pripadajo. */

export type PogledDogodka = 'skupine' | 'mreza' | 'udelezenci'

export interface PogledGumb {
  kljuc: PogledDogodka
  oznaka: string
}

interface Lastnosti {
  pogledi: PogledGumb[]
  izbrani: PogledDogodka
  naIzbiro: (pogled: PogledDogodka) => void
  /* Mono povzetek desno, npr. "25 skupin · odigranih 78 / 150". */
  povzetek: string
}

export function PodnavigacijaDogodka({ pogledi, izbrani, naIzbiro, povzetek }: Lastnosti) {
  /* En sam pogled ni izbira - pas bi bil samo crta z enim gumbom. */
  if (pogledi.length < 2 && !povzetek) return null

  return (
    <div className="podnavigacija">
      <div className="izbirnik">
        {pogledi.map((pogled) => (
          <button
            type="button"
            key={pogled.kljuc}
            className={
              'izbirnik__gumb' + (izbrani === pogled.kljuc ? ' izbirnik__gumb--aktiven' : '')
            }
            aria-pressed={izbrani === pogled.kljuc}
            onClick={() => naIzbiro(pogled.kljuc)}
          >
            {pogled.oznaka}
          </button>
        ))}
      </div>
      {povzetek && <span className="podnavigacija__povzetek">{povzetek}</span>}
    </div>
  )
}
