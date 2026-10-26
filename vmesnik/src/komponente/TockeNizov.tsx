/* Vnos točk po nizih (11:7, 9:11 …) — ista komponenta za turnirsko in ligaško
   tekmo, ker so pravila niza povsod ista (v zaledju jih čuva NiziPravila).
   Vnos je povsod neobvezen: brez njega se shrani samo izid v nizih.

   Komponenta stanja ne hrani — vrstice so v obrazcu, ki jo uporablja, ker ta
   ve, iz katerega izida izhaja njihovo število.

   Vpis je narejen za prepis papirnatega zapisnika z eno roko na številčnici:
   drugi stolpec se napolni sam (zmagovalec ima 11, pri 10 pa 12), vrstica pa se
   po zaključenem nizu premakne naprej. Po mreži se da hoditi s puščicami, tudi
   ko je polje samodejno napolnjeno. Priimka v glavi povesta, čigav je stolpec —
   brez njiju je »11 : 7« brez pomena, dokler se vnašalec ne spomni, kdo je bil prvi. */
import { useEffect, useRef, type KeyboardEvent, type RefObject } from 'react'

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

/* Drugi stolpec niza, ko je vpisan prvi, kadar je izid edini mogoč:
   - 2–9 (ali 0) pomeni, da je nasprotnik zmagal z 11;
   - 10 pomeni zmago z 12, ker zmaga šele z dvema točkama razlike;
   - 1 je lahko začetek 10, zato se 11 zapiše samo v prazen stolpec; prejšnja
     vrednost ostane, da brisanje 11 v 1 ne prepiše drugega stolpca;
   - dvomestni vpis 11 in več pomeni, da je zmagal ta stolpec, zato se ostanek 11
     iz prejšnjega samodejnega vpisa izbriše (drugi ne more imeti 11). */
function drugaTocka(vpis: string, drugo: string): string {
  if (vpis === '') return drugo
  if (vpis === '1') return drugo === '' ? '11' : drugo
  if (vpis === '10') return '12'
  if (vpis.length === 1) return '11'
  return drugo === '11' ? '' : drugo
}

/* Drugi igralec ima samodejno preostanek nizov, ko je vpisan prvi (in obratno):
   zmagovalec ima vedno toliko nizov, kolikor jih je treba za zmago, izgubivši manj.
   Če je vpisan zmagovalec (zaZmago), drugi ne more imeti istega; če ga ima, je to
   ostanek prejšnjega samodejnega vpisa in se izbriše. */
export function dopolniDrugi(vpisan: string, drugi: string, zaZmago: number): string {
  if (vpisan === '') return drugi
  if (Number(vpisan) < zaZmago) return String(zaZmago)
  return drugi === String(zaZmago) ? '' : drugi
}

/* Fokus na polje z označeno vrednostjo, da nova številka prepiše staro. */
export function fokusiraj(polje: HTMLInputElement) {
  polje.focus()
  polje.select()
}

/* Sklici polj nizov in točk za premik s puščicami ter samodejni fokus. Vrstice točk
   se pojavijo šele v prikazu, ki ga sproži vpis nizov, zato fokusa po vpisu ni mogoče
   dati kar v obravnavalniku: obrazec označi `naFokusTock`, učinek pa ga po prikazu
   pobere in fokusira prvo točko. */
export function useSkliciNizov() {
  const sklicPrvi = useRef<HTMLInputElement>(null)
  const sklicDrugi = useRef<HTMLInputElement>(null)
  const sklicTock = useRef<(HTMLInputElement | null)[]>([])
  const naFokusTock = useRef(false)
  useEffect(() => {
    if (!naFokusTock.current) return
    naFokusTock.current = false
    const prvo = sklicTock.current[0]
    if (prvo) fokusiraj(prvo)
  })

  /* Puščice med nizoma levo/desno, dol pa na točke nad istim stolpcem. Vpisano ostane. */
  function obTipkiNizov(dogodek: KeyboardEvent<HTMLInputElement>, stolpec: 0 | 1) {
    let cilj: HTMLInputElement | null = null
    switch (dogodek.key) {
      case 'ArrowLeft':
        if (stolpec === 1) cilj = sklicPrvi.current
        break
      case 'ArrowRight':
        if (stolpec === 0) cilj = sklicDrugi.current
        break
      case 'ArrowDown':
        cilj = sklicTock.current[stolpec] ?? null
        break
      default:
        break
    }
    if (!cilj) return
    dogodek.preventDefault()
    fokusiraj(cilj)
  }

  /* Puščica gor z zgornje vrstice točk se vrne na nize nad istim stolpcem. */
  function naVrh(stolpec: 0 | 1) {
    const cilj = stolpec === 0 ? sklicPrvi.current : sklicDrugi.current
    if (cilj) fokusiraj(cilj)
  }

  return { sklicPrvi, sklicDrugi, sklicTock, naFokusTock, obTipkiNizov, naVrh }
}

export function TockeNizov({
  vrstice,
  nastaviVrstice,
  priimek1,
  priimek2,
  sklicPolj,
  naVrh,
}: {
  vrstice: VrsticaNiza[]
  nastaviVrstice: (posodobi: (prejsnje: VrsticaNiza[]) => VrsticaNiza[]) => void
  /* Čigav je stolpec — priimek, ker je glava ozka kot polje pod njo. */
  priimek1: string
  priimek2: string
  /* Obrazec lahko drži sklic na polja, če mora nanje fokusirati od zunaj (puščica
     dol iz polj nizov, samodejni prehod po vpisu nizov). */
  sklicPolj?: RefObject<(HTMLInputElement | null)[]>
  /* Puščica gor z zgornje vrstice gre v obrazcu na polje nad istim stolpcem. */
  naVrh?: (stolpec: 0 | 1) => void
}) {
  /* Polja v enem samem seznamu (niz × stran), da je premik »naprej« povsod
     isti korak: konec vrstice se nadaljuje v naslednji vrstici. */
  const notranjaPolja = useRef<(HTMLInputElement | null)[]>([])
  const polja = sklicPolj ?? notranjaPolja

  const naMesto = (mesto: number) => {
    const polje = polja.current[mesto]
    if (!polje) return false
    fokusiraj(polje)
    return true
  }

  /* Vpis ene številke: drugi stolpec se napolni po pravilu (glej drugaTocka), nato
     fokus. Vpis 1 ostane v polju, ker lahko sledi še ena števka; dvomestni vpis 11
     in več skoči v drugi stolpec, enomestni konec niza pa v naslednjo vrstico. */
  const spremeni = (mesto: number, vpis: string) => {
    const indeks = Math.floor(mesto / 2)
    const stran = mesto % 2 === 0 ? 'tocke1' : 'tocke2'
    const drugaStran = mesto % 2 === 0 ? 'tocke2' : 'tocke1'
    nastaviVrstice((prejsnje) =>
      prejsnje.map((vrstica, i) =>
        i === indeks
          ? { ...vrstica, [stran]: vpis, [drugaStran]: drugaTocka(vpis, vrstica[drugaStran]) }
          : vrstica,
      ),
    )
    if (vpis === '' || vpis === '1') return
    if (vpis.length >= 2 && vpis !== '10') naMesto(mesto + 1)
    // konec niza: levi stolpec naslednje vrstice; pri zadnji vrstici fokus ostane
    else naMesto((indeks + 1) * 2)
  }

  /* Puščice hodijo po mreži: levo/desno med stranema (in čez konec vrstice),
     gor/dol po istem stolpcu. Enomestna številka (tudi samodejno vpisana) nima
     kazalca sredi besede, zato puščica takoj gre; pri dvomestni se levo in desno
     skočita samo z roba vpisa, da ostane mogoče postaviti kazalec sredi številke. */
  const obTipki = (dogodek: KeyboardEvent<HTMLInputElement>, mesto: number) => {
    const polje = dogodek.currentTarget
    const enomestno = polje.value.length <= 1
    const naZacetku = enomestno || polje.selectionStart === 0
    const naKoncu = enomestno || polje.selectionEnd === polje.value.length

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
        if (mesto < 2 && naVrh) {
          naVrh(mesto % 2 === 0 ? 0 : 1)
          dogodek.preventDefault()
        } else if (naMesto(mesto - 2)) {
          dogodek.preventDefault()
        }
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
