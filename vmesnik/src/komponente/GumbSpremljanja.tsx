/* Preklop "spremljam to ligo" — en sam videz na vseh mestih.

   Pomen nosi kvadratek (poln zelen = spremljam, prazen z obrobo = ne), ker je
   isti znak že v sklopu "Moje lige" na domači strani; ikone so prepovedane.
   Trije slogi so ista naprava v treh okvirjih:
   - "kvadratek" sam stoji ob imenu lige (vrstica sklopa, vrstica izbora),
   - "gumb" je vrstica dejanj na namizju (kvadratek + oznaka),
   - "glava" je stisnjena različica za lepljivo glavo telefona (36 px visoka,
     prst dobi svojih 44 px prek nevidnega ::after v slogu). */
type Slog = 'kvadratek' | 'gumb' | 'glava'

interface Lastnosti {
  /* Ime lige gre v aria-label: bralnik zaslona mora vedeti, KATERO ligo
     preklaplja, ker je kvadratkov na strani več. */
  ime: string
  spremljam: boolean
  naPreklop: () => void
  slog?: Slog
}

export function GumbSpremljanja({ ime, spremljam, naPreklop, slog = 'gumb' }: Lastnosti) {
  const oznaka = spremljam ? `Nehaj spremljati ${ime}` : `Spremljaj ${ime}`
  const kvadratek = <span className={'kljukica' + (spremljam ? ' kljukica--polna' : '')} />

  if (slog === 'kvadratek') {
    return (
      <button
        type="button"
        className="kljukica-gumb"
        aria-pressed={spremljam}
        aria-label={oznaka}
        onClick={naPreklop}
      >
        {kvadratek}
      </button>
    )
  }

  return (
    <button
      type="button"
      className={slog === 'glava' ? 'spremljanje spremljanje--glava' : 'spremljanje'}
      aria-pressed={spremljam}
      aria-label={oznaka}
      onClick={naPreklop}
    >
      {kvadratek}
      <span>{spremljam ? 'Spremljam' : 'Spremljaj'}</span>
    </button>
  )
}
