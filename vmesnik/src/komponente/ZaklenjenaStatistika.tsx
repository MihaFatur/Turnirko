/* Zaklenjen odsek pod grafom napredka na profilu igralca: pokaže, da pod grafom
   obstaja še zasebna statistika (forma, nasprotniki, napoved tekme, nizi in
   točke, razrezi), ki jo odpre račun Igralec Premium.

   Predogled je IZMIŠLJEN in zamegljen - nikoli ne nosi pravih številk igralca,
   sicer bi zamegljena statistika še vedno uhajala skozi DOM. Skrit je za
   bralnike zaslona (`aria-hidden`) in za fokus (`inert`); besedilo čez njega pove
   isto, kar vidi oko.

   Navijač zasebne statistike ne odpre sam: odpre jo samo povezava z družino za
   igralca do 21 let (DruzinaStoritev), zato ga besedilo omeni le pri mladincu. */
import type { StarostniPas } from '../api/tipi'

const ODRASLI: (StarostniPas | null | undefined)[] = ['CLANI', 'VETERANI']

/* Z = zmaga, P = poraz; samo okras predogleda. */
const TRAK = ['Z', 'Z', 'P', 'Z', 'Z', 'Z', 'P', 'Z', 'P', 'Z']

const KAZALNIKI: { oznaka: string; vrednost: string }[] = [
  { oznaka: 'Dobljeni nizi', vrednost: '64 %' },
  { oznaka: 'Tesne končnice', vrednost: '11 : 7' },
  { oznaka: 'Najdaljši niz zmag', vrednost: '9' },
]

const NASPROTNIKI: { stolpec: number; vrednost: string }[] = [
  { stolpec: 62, vrednost: '3 : 1' },
  { stolpec: 48, vrednost: '2 : 2' },
  { stolpec: 36, vrednost: '1 : 3' },
]

export function ZaklenjenaStatistika({
  lastnik,
  pas,
  naOdkleni,
}: {
  /* Gleda igralec sam (prijavljen, brez Premium): gumb ga pelje k nadgradnji. */
  lastnik: boolean
  /* Starostni pas igralca z lestvice; prazno, če ga lestvica ne pozna. */
  pas: StarostniPas | null | undefined
  naOdkleni: () => void
}) {
  /* Pas, ki ga lestvica ne pozna, ne obljubi ničesar: povezava z družino velja
     samo za igralca do 21 let in strežnik pas preveri sam. */
  const mladinec = pas != null && !ODRASLI.includes(pas)

  return (
    <section className="zaklenjeno" aria-labelledby="zaklenjeno-naslov">
      <div className="naslovna-vrstica">
        <h2 id="zaklenjeno-naslov">Zasebna statistika</h2>
        <span className="plosca__zasebno">Igralec Premium</span>
      </div>

      <div className="zaklenjeno__okvir">
        <div className="zaklenjeno__predogled" aria-hidden="true" inert>
          <div className="zaklenjeno__stolpec">
            <div className="zaklenjeno__trak">
              {TRAK.map((znak, i) => (
                <span
                  key={i}
                  className={'zaklenjeno__znak ' + (znak === 'Z' ? 'zaklenjeno__znak--z' : 'zaklenjeno__znak--p')}
                >
                  {znak}
                </span>
              ))}
            </div>
            <div className="zaklenjeno__kazalniki">
              {KAZALNIKI.map((k) => (
                <div className="zaklenjeno__kazalnik" key={k.oznaka}>
                  <span className="zaklenjeno__oznaka">{k.oznaka}</span>
                  <span className="zaklenjeno__vrednost">{k.vrednost}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="zaklenjeno__stolpec">
            <div className="zaklenjeno__oznaka">Nasprotniki</div>
            {NASPROTNIKI.map((n, i) => (
              <div className="zaklenjeno__vrstica" key={i}>
                <span className="zaklenjeno__ime" style={{ width: `${n.stolpec}%` }} />
                <span className="zaklenjeno__vrednost">{n.vrednost}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="zaklenjeno__zavesa">
          <div className="zaklenjeno__besedilo">
            <span className="zaklenjeno__nadnaslov">Zaklenjeno · dodatna statistika</span>
            <p className="zaklenjeno__glavno">
              {lastnik
                ? 'Svojo formo, nasprotnike, nize in točke odkleneš z računom Igralec Premium.'
                : 'Dodatno statistiko tega igralca odklene račun Igralec Premium.'}
            </p>
            <p className="zaklenjeno__opis">
              Forma, nasprotniki, napoved tekme, nizi in točke ter razrezi.
              {!lastnik && ' Vidi jo igralec sam.'}
              {mladinec && ' Pri igralcu do 21 let jo lahko odpre tudi starš s paketom Navijač.'}
            </p>
            <button type="button" className="gumb gumb--glavni" onClick={naOdkleni}>
              {lastnik ? 'Nadgradi na Premium' : 'Poglej, kaj vsebuje'}
            </button>
          </div>
        </div>
      </div>
    </section>
  )
}
