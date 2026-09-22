/* Vnos točk po nizih (11:7, 9:11 …) — ista komponenta za turnirsko in ligaško
   tekmo, ker so pravila niza povsod ista (v zaledju jih čuva NiziPravila).
   Vnos je povsod neobvezen: brez njega se shrani samo izid v nizih.

   Komponenta stanja ne hrani — vrstice so v obrazcu, ki jo uporablja, ker ta
   ve, iz katerega izida izhaja njihovo število.

   Vpis je narejen za prepis papirnatega zapisnika z eno roko na številčnici:
   polje se ob dokončani številki samo premakne naprej, po mreži pa se da
   hoditi s puščicami. Priimka v glavi povesta, čigav je stolpec — brez njiju
   je »11 : 7« brez pomena, dokler se vnašalec ne spomni, kdo je bil prvi. */
import { useRef, type KeyboardEvent } from 'react'

import type { NizVnos } from '../api/tipi'
import { StevilskoPolje } from './StevilskoPolje'

/* Ena vrstica vnosa: točki sta besedili, ker je prazno polje veljavno stanje
   med tipkanjem (Number('') bi bil 0). */
export interface VrsticaNiza {
  tocke1: string
  tocke2: string
}

/* Toliko praznih vrstic, kolikor nizov je bilo pri danem izidu odigranih. */
export function vrsticeZaIzid(prejsnje: VrsticaNiza[], steviloNizov: number): VrsticaNiza[] {
  const nove = prejsnje.slice(0, steviloNizov)
  while (nove.length < steviloNizov) nove.push({ tocke1: '', tocke2: '' })
  return nove
}

/* Ali je posamezen niz veljaven namiznoteniški rezultat (kot v zaledju):
   do 11 z razliko vsaj 2, pri podaljšku (nad 11) razlika natanko 2. */
export function veljavenNiz(tocke1: number, tocke2: number): boolean {
  const vec = Math.max(tocke1, tocke2)
  const manj = Math.min(tocke1, tocke2)
  return manj >= 0 && ((vec === 11 && manj <= 9) || (vec > 11 && vec - manj === 2))
}

/* Preveri nize po istih pravilih kot zaledje, da uporabnik napako vidi takoj,
   brez klica strežnika. Vrne sporočilo napake ali null. */
export function preveriNize(
  nizi: NizVnos[],
  dobljeni1: number,
  dobljeni2: number,
  zaZmago: number,
): string | null {
  let steti1 = 0
  let steti2 = 0
  for (let i = 0; i < nizi.length; i++) {
    const { tocke1, tocke2 } = nizi[i]
    if (tocke1 === tocke2) return `${i + 1}. niz ne more biti neodločen.`
    if (!veljavenNiz(tocke1, tocke2)) {
      return `${i + 1}. niz (${tocke1}:${tocke2}) ni veljaven namiznoteniški rezultat.`
    }
    // tekma se konča v trenutku odločitve — noben niz se ne igra po tem
    if (steti1 === zaZmago || steti2 === zaZmago) {
      return `Tekma je bila odločena že po ${i} nizih (${steti1}:${steti2}), zato se ${
        i + 1
      }. niz ne bi igral. Popravi rezultat ali nize.`
    }
    if (tocke1 > tocke2) steti1++
    else steti2++
  }
  if (steti1 !== dobljeni1 || steti2 !== dobljeni2) {
    return `Točke po nizih dajo ${steti1}:${steti2}, izbran pa je rezultat ${dobljeni1}:${dobljeni2}.`
  }
  return null
}

/* Ali vpisana številka ne more več zrasti in se sme premakniti v naslednje
   okence. Dvomestna ne more (niz nad 99 točk ne obstaja), enomestna pa samo
   tedaj, kadar se z njo ne more začeti dvomestna: to je vse razen ena
   (10–19). Ničla je enako varna — noben rezultat se ne piše »05«.

   Dvomestni rezultati se z 2–9 sicer začnejo (22:20 je veljaven podaljšek), a
   so tako redki, da je premik naprej pri stotih vpisih prihranek, pri enem pa
   klik nazaj oz. puščica levo. */
function dokoncana(vrednost: string): boolean {
  if (vrednost.length >= 2) return true
  return vrednost.length === 1 && vrednost !== '1'
}

export function TockeNizov({
  vrstice,
  nastaviVrstice,
  priimek1,
  priimek2,
}: {
  vrstice: VrsticaNiza[]
  nastaviVrstice: (posodobi: (prejsnje: VrsticaNiza[]) => VrsticaNiza[]) => void
  /* Čigav je stolpec — priimek, ker je glava ozka kot polje pod njo. */
  priimek1: string
  priimek2: string
}) {
  /* Polja v enem samem seznamu (niz × stran), da je premik »naprej« povsod
     isti korak: konec vrstice se nadaljuje v naslednji vrstici. */
  const polja = useRef<(HTMLInputElement | null)[]>([])

  const naMesto = (mesto: number) => {
    const polje = polja.current[mesto]
    if (!polje) return false
    polje.focus()
    polje.select()
    return true
  }

  const spremeni = (mesto: number, vrednost: string) => {
    const indeks = Math.floor(mesto / 2)
    const stran = mesto % 2 === 0 ? 'tocke1' : 'tocke2'
    nastaviVrstice((prejsnje) =>
      prejsnje.map((vrstica, i) => (i === indeks ? { ...vrstica, [stran]: vrednost } : vrstica)),
    )
    if (dokoncana(vrednost)) naMesto(mesto + 1)
  }

  /* Puščice hodijo po mreži: levo/desno med stranema (in čez konec vrstice),
     gor/dol po istem stolpcu. Levo in desno skočita samo z ROBA vpisa, da
     ostane mogoče postaviti kazalec sredi dvomestne številke; pri označeni
     celi vrednosti (tako pride polje ob samodejnem premiku) sta oba roba
     hkrati, zato skok stopi takoj in ne šele po razveljavitvi izbire. */
  const obTipki = (dogodek: KeyboardEvent<HTMLInputElement>, mesto: number) => {
    const polje = dogodek.currentTarget
    const naZacetku = polje.selectionStart === 0
    const naKoncu = polje.selectionEnd === polje.value.length

    switch (dogodek.key) {
      case 'ArrowRight':
        if (naKoncu && naMesto(mesto + 1)) dogodek.preventDefault()
        break
      case 'ArrowLeft':
        if (naZacetku && naMesto(mesto - 1)) dogodek.preventDefault()
        break
      case 'ArrowDown':
        if (naMesto(mesto + 2)) dogodek.preventDefault()
        break
      case 'ArrowUp':
        if (naMesto(mesto - 2)) dogodek.preventDefault()
        break
      case 'Backspace':
        // prazno polje nima česa brisati - vrzi nazaj v prejšnje
        if (polje.value === '' && naMesto(mesto - 1)) dogodek.preventDefault()
        break
      default:
        break
    }
  }

  return (
    <div className="obrazec__nizi">
      <span className="obrazec__niz-oznaka obrazec__niz-glava" aria-hidden="true" />
      <span className="obrazec__niz-glava">{priimek1}</span>
      <span aria-hidden="true" />
      <span className="obrazec__niz-glava">{priimek2}</span>

      {/* Vrstica niza je samo ovoj za ključ — stolpce določa mreža okrog nje
          (display: contents), da priimka v glavi stojita nad svojim poljem. */}
      {vrstice.map((niz, indeks) => (
        <div className="obrazec__niz-vrsta" key={indeks}>
          <span className="obrazec__niz-oznaka">{indeks + 1}. niz</span>
          <StevilskoPolje
            ref={(polje) => {
              polja.current[indeks * 2] = polje
            }}
            najvec={99}
            placeholder="11"
            aria-label={`${indeks + 1}. niz — ${priimek1}`}
            vrednost={niz.tocke1}
            naSpremembo={(vrednost) => spremeni(indeks * 2, vrednost)}
            onKeyDown={(dogodek) => obTipki(dogodek, indeks * 2)}
          />
          <span>:</span>
          <StevilskoPolje
            ref={(polje) => {
              polja.current[indeks * 2 + 1] = polje
            }}
            najvec={99}
            placeholder="7"
            aria-label={`${indeks + 1}. niz — ${priimek2}`}
            vrednost={niz.tocke2}
            naSpremembo={(vrednost) => spremeni(indeks * 2 + 1, vrednost)}
            onKeyDown={(dogodek) => obTipki(dogodek, indeks * 2 + 1)}
          />
        </div>
      ))}
    </div>
  )
}
