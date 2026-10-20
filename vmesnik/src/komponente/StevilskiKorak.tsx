/* Majhno število z gumboma [−] in [+] (srečanja na kolo, napredovanja, izpadi).

   Zakaj ne `<select>` in ne `type="number"`: vrednosti so od 0 do 10, zato je
   gumb hitrejši od odpiranja seznama, polje pa se vpisuje kot vsako drugo
   številko (StevilskoPolje ignorira kolesce miške in puščice).

   Meje se ne izvajajo med tipkanjem, ampak se vrednost le stisne v [najmanj,
   najvec]: gumb na meji ostane aktiven, ker je uporabnik pogosto ravno tam, da
   vidi, kaj se zgodi. Prazno polje ob odhodu pomeni »najmanj«. */
import { useState } from 'react'

import { StevilskoPolje } from './StevilskoPolje'

interface Lastnosti {
  vrednost: number
  naSpremembo: (vrednost: number) => void
  najmanj?: number
  najvec?: number
  /* Ime polja za bralnik zaslona (aria-label vpisa). */
  oznaka: string
  oznakaManj: string
  oznakaVec: string
}

export function StevilskiKorak({
  vrednost,
  naSpremembo,
  najmanj = 0,
  najvec = 10,
  oznaka,
  oznakaManj,
  oznakaVec,
}: Lastnosti) {
  /* Med tipkanjem je prikaz besedilo, ki ga je uporabnik vpisal; sicer sledi
     vrednosti. Null pomeni »ne tipkamo«, da vrednost od zunaj vedno velja. */
  const [vpis, nastaviVpis] = useState<string | null>(null)
  const prikaz = vpis ?? String(vrednost)

  const stisni = (n: number) => Math.min(najvec, Math.max(najmanj, n))

  function naVpis(besedilo: string) {
    if (besedilo === '') {
      nastaviVpis('')
      return
    }
    const v = stisni(Number(besedilo))
    nastaviVpis(String(v))
    naSpremembo(v)
  }

  function naIzhodu() {
    nastaviVpis(null)
    if (prikaz === '') naSpremembo(najmanj)
  }

  /* Osnova je prikazana vrednost, ne zadnja poslana: po praznem polju bi se
     gumb sicer obnašal, kot da je vrednost še stara. */
  function premakni(korak: number) {
    const osnova = prikaz === '' ? najmanj : Number(prikaz)
    const v = stisni(osnova + korak)
    nastaviVpis(null)
    naSpremembo(v)
  }

  return (
    <div className="korak">
      <button type="button" className="korak__gumb" aria-label={oznakaManj}
        onClick={() => premakni(-1)}>
        −
      </button>
      <StevilskoPolje
        className="korak__vnos"
        aria-label={oznaka}
        vrednost={prikaz}
        naSpremembo={naVpis}
        onBlur={naIzhodu}
      />
      <button type="button" className="korak__gumb" aria-label={oznakaVec}
        onClick={() => premakni(1)}>
        +
      </button>
    </div>
  )
}
