/* Vnos točk po nizih (11:7, 9:11 …) — ista komponenta za turnirsko in ligaško
   tekmo, ker so pravila niza povsod ista (v zaledju jih čuva NiziPravila).
   Vnos je povsod neobvezen: brez njega se shrani samo izid v nizih.

   Komponenta stanja ne hrani — vrstice so v obrazcu, ki jo uporablja, ker ta
   ve, iz katerega izida izhaja njihovo število. */
import type { NizVnos } from '../api/tipi'

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

export function TockeNizov({
  vrstice,
  nastaviVrstice,
}: {
  vrstice: VrsticaNiza[]
  nastaviVrstice: (posodobi: (prejsnje: VrsticaNiza[]) => VrsticaNiza[]) => void
}) {
  const spremeni = (indeks: number, stran: 'tocke1' | 'tocke2', vrednost: string) =>
    nastaviVrstice((prejsnje) =>
      prejsnje.map((vrstica, i) => (i === indeks ? { ...vrstica, [stran]: vrednost } : vrstica)),
    )

  return (
    <div className="obrazec__nizi">
      {vrstice.map((niz, indeks) => (
        <div className="obrazec__niz" key={indeks}>
          <span className="obrazec__niz-oznaka">{indeks + 1}. niz</span>
          <input
            type="number"
            min={0}
            max={99}
            placeholder="11"
            value={niz.tocke1}
            onChange={(dogodek) => spremeni(indeks, 'tocke1', dogodek.target.value)}
          />
          <span>:</span>
          <input
            type="number"
            min={0}
            max={99}
            placeholder="7"
            value={niz.tocke2}
            onChange={(dogodek) => spremeni(indeks, 'tocke2', dogodek.target.value)}
          />
        </div>
      ))}
    </div>
  )
}
