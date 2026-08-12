/* Zadnje ogledane lige gosta.

   Sklop "Moje lige" na domači strani je izbor prijavljenega uporabnika in
   živi na strežniku. Gost računa nima, zato bi sklop ostal prazen — namesto
   izbora mu pokažemo lige, ki si jih je nazadnje ogledal. Zapisuje jih
   njegov brskalnik (localStorage), strežnik o njih ne ve nič. */

const KLJUC = 'turnirko-ogledane-lige'

/* Koliko lig si zapomnimo. Sklop pokaže dve do tri; daljši spomin bi na vrh
   potiskal ligo, ki jo je gost odprl pomotoma pred tedni. */
const NAJVEC = 5

export function ogledaneLige(): number[] {
  try {
    const shranjeno = localStorage.getItem(KLJUC)
    if (!shranjeno) return []
    const razclenjeno: unknown = JSON.parse(shranjeno)
    if (!Array.isArray(razclenjeno)) return []
    return razclenjeno.filter((v): v is number => Number.isInteger(v)).slice(0, NAJVEC)
  } catch {
    /* Zasebni način brskanja ali pokvarjen zapis — spomina pač ni. */
    return []
  }
}

/* Zabeleži ogled lige; najnovejša gre na vrh, podvojitve odpadejo. */
export function zabeleziOgledLige(idLiga: number) {
  try {
    const brez = ogledaneLige().filter((id) => id !== idLiga)
    localStorage.setItem(KLJUC, JSON.stringify([idLiga, ...brez].slice(0, NAJVEC)))
  } catch {
    /* Brez shrambe sklop pokaže lige v teku — to ni napaka, ki bi jo bilo
       vredno pokazati gledalcu. */
  }
}
