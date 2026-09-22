/* Polje za vpis števila — samo vpis, brez drsenja in brez puščic.

   Zakaj ne `type="number"`: kolesce miške nad ostrenim poljem tiho spremeni
   vpisano številko. Organizator, ki med vnosom rezultata podrsa po zapisniku,
   dobi namesto 11 karkoli — in tega ne opazi, ker se je premaknila stran, ne
   kazalec. Isto naredita puščici gor/dol, ti pa sta pri točkah nizov potrebni
   za premik med okenci (glej TockeNizov), zato ju polje ne sme požreti.

   Številčno tipkovnico na telefonu prinese `inputMode`, ne `type`, zato se z
   besedilnim poljem ne izgubi nič. Vpis je filtriran: kar ni števka (oz. pri
   `decimalno` ločilo), se sploh ne zapiše — nesmiseln znak tako ne pride niti
   v stanje obrazca niti v `Number()`, ki bi iz njega naredil NaN. */
import { forwardRef, type InputHTMLAttributes } from 'react'

type PodedovaneLastnosti = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type' | 'value' | 'onChange' | 'inputMode' | 'min' | 'max'
>

interface Lastnosti extends PodedovaneLastnosti {
  vrednost: string
  naSpremembo: (vrednost: string) => void
  /* Dovoli decimalno ločilo (npr. prijavnina 7,5 €). Vejica se zapiše kot
     pika, ker Number() pozna samo pike. */
  decimalno?: boolean
  /* Zgornja meja: vpis, ki bi jo presegel, se zavrne. Spodnje meje med
     tipkanjem ni mogoče uveljaviti (pot do 1000 vodi čez 1). */
  najvec?: number
}

/* Vrne očiščen vpis ali null, kadar ga je treba zavrniti. */
function ocisti(besedilo: string, decimalno: boolean, najvec?: number): string | null {
  const vpis = besedilo.trim()
  if (vpis === '') return ''
  const kandidat = decimalno ? vpis.replace(',', '.') : vpis
  const vzorec = decimalno ? /^\d*([.]\d*)?$/ : /^\d+$/
  if (!vzorec.test(kandidat)) return null
  if (najvec !== undefined && Number.isFinite(Number(kandidat)) && Number(kandidat) > najvec) {
    return null
  }
  return kandidat
}

export const StevilskoPolje = forwardRef<HTMLInputElement, Lastnosti>(function StevilskoPolje(
  { vrednost, naSpremembo, decimalno = false, najvec, ...ostalo },
  sklic,
) {
  return (
    <input
      {...ostalo}
      ref={sklic}
      type="text"
      inputMode={decimalno ? 'decimal' : 'numeric'}
      autoComplete="off"
      value={vrednost}
      onChange={(dogodek) => {
        const ocisceno = ocisti(dogodek.target.value, decimalno, najvec)
        if (ocisceno !== null) naSpremembo(ocisceno)
      }}
    />
  )
})
