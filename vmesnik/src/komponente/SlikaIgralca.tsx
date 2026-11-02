/* Profilna slika igralca v kvadratnem okvirju (V56) - kot fotografija na
   licenci: radij 0, 1 px črta črnila. Brez slike je v istem okvirju obris
   glave in ramen, da okvir drži mesto (1 na 1, stopničke): imena se ne
   zamaknejo, kadar ima sliko le eden.

   Slika je okras (alt=""): ime igralca stoji vedno ob njej. */
import { urlProfilneSlike, type VelikostProfilneSlike } from '../pomozno/profilneSlike'

interface Lastnosti {
  zgostitev: string | null | undefined
  velikost: VelikostProfilneSlike
  /* Stranica okvirja v px. */
  mera: number
  /* Dodaten razred za postavitev (npr. ob naslovu profila). */
  razred?: string
}

export function SlikaIgralca({ zgostitev, velikost, mera, razred }: Lastnosti) {
  const razredi = 'slika-igralca' + (razred ? ' ' + razred : '')
  if (zgostitev) {
    return (
      <img
        className={razredi}
        src={urlProfilneSlike(zgostitev, velikost)}
        width={mera}
        height={mera}
        alt=""
        decoding="async"
      />
    )
  }
  return (
    <span
      className={razredi + ' slika-igralca--prazna'}
      style={{ width: mera, height: mera }}
      aria-hidden="true"
    >
      <svg viewBox="0 0 40 40" width={Math.round(mera * 0.6)} height={Math.round(mera * 0.6)}>
        <circle cx="20" cy="13" r="7.5" />
        <path d="M6 36c0-7.7 6.3-14 14-14s14 6.3 14 14" />
      </svg>
    </span>
  )
}
