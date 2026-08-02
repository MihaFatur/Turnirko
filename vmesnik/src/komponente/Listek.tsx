/* En natisljivi sodniski listek posamicne (turnirske) tekme.

   Listek je zapisnik na papirju: glava (miza in faza), dve vrstici igralcev z
   imenom v display 800 in klubom v mono, mreza praznih polj za vpis nizov ter
   noga z opombo o stevilu nizov in podpisu sodnika. Sodnik ga izpolni z roko
   za mizo, rezultat pa glavni sodnik pozneje vnese prek obicajnega vnosa.
   Listek je namenoma crno-bel ne glede na temo (natis na papir), zato tu ne
   uporabljamo temo odvisnih spremenljivk. */

/* Ena stran listka (igralec ali dvojica). */
interface StranListka {
  /* Ime igralca; pri dvojicah oba, zdruzena z " / ". */
  ime: string
  /* Klub (turnir) ali ekipa (liga); prazno pusti desno stran vrstice prazno. */
  podnaslov: string | null
}

export interface ListekPodatki {
  /* Desna oznaka v glavi: faza/kolo (turnir) ali oznaka tekme (liga). */
  oznaka: string
  steviloNizov: number
  /* Stevilka mize; null izpise prazno crto za rocni vpis. */
  miza: number | null
  stran1: StranListka
  stran2: StranListka
}

export function Listek({ podatki }: { podatki: ListekPodatki }) {
  /* Toliko praznih polj, kolikor je najvec mogocih nizov (najboljsi od N). */
  const nizi = Array.from({ length: podatki.steviloNizov }, (_, i) => i + 1)

  return (
    <article className="listek">
      <div className="listek__glava">
        {/* Kadar miza se ni dodeljena, pusti crto za rocni vpis za mizo. */}
        <span>Miza {podatki.miza !== null ? podatki.miza : <span className="listek__vpis" />}</span>
        <span>{podatki.oznaka}</span>
      </div>

      <div className="listek__igralci">
        <VrsticaIgralca stran={podatki.stran1} />
        <VrsticaIgralca stran={podatki.stran2} />
      </div>

      <div className="listek__nizi">
        {nizi.map((n) => (
          <span key={n} className="listek__niz-polje" />
        ))}
      </div>

      <div className="listek__noga">
        <span>Najboljši od {podatki.steviloNizov} nizov</span>
        <span>Podpis sodnika</span>
      </div>
    </article>
  )
}

/* Vrstica igralca: ime levo v display 800, klub/ekipa desno v mono, spodaj
   crta. Kadar kluba ni, ostane desna stran prazna — crta vrstico vseeno
   zakljuci, zato nadomestnega znaka ne izpisujemo. */
function VrsticaIgralca({ stran }: { stran: StranListka }) {
  return (
    <div className="listek__igralec">
      <span className="listek__ime">{stran.ime}</span>
      {stran.podnaslov ? <span className="listek__klub">{stran.podnaslov}</span> : null}
    </div>
  )
}
