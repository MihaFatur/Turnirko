/* En natisljivi listek (zapisnik) tekme.

   Skupna predstavitev za turnirske (posamične) in ligaške (ekipne) tekme:
   obe strani napolnita normalizirane podatke (ListekPodatki), slog in
   @media print pa drži slog.css. Listek je namenoma črno-bel ne glede na
   temo — natis na papir. */

/* Ena stran listka (igralec ali dvojica). */
interface StranListka {
  /* Ime igralca; pri dvojicah oba, združena z " / ". */
  ime: string
  /* Klub (turnir) ali ekipa (liga); prazno pusti vrstico prazno. */
  podnaslov: string | null
}

export interface ListekPodatki {
  /* Leva oznaka v glavi: faza/kolo (turnir) ali oznaka tekme (liga). */
  oznaka: string
  steviloNizov: number
  /* Številka mize; null izpiše prazno črto za ročni vpis. */
  miza: number | null
  stran1: StranListka
  stran2: StranListka
}

export function Listek({ podatki }: { podatki: ListekPodatki }) {
  const stolpci = Array.from({ length: podatki.steviloNizov }, (_, i) => i + 1)

  return (
    <article className="listek">
      <div className="listek__glava">
        <span className="listek__faza">{podatki.oznaka}</span>
        <span className="listek__meta">
          na {podatki.steviloNizov} nizov · Miza{' '}
          <span className="listek__vpis">{podatki.miza ?? ''}</span>
        </span>
      </div>

      <table className="listek__tabela">
        <thead>
          <tr>
            <th className="listek__igralec-glava">Igralec</th>
            {stolpci.map((n) => (
              <th key={n}>{n}</th>
            ))}
            <th className="listek__nizi-glava">Nizi</th>
          </tr>
        </thead>
        <tbody>
          <Vrsta stran={podatki.stran1} stolpci={stolpci} />
          <Vrsta stran={podatki.stran2} stolpci={stolpci} />
        </tbody>
      </table>

      <div className="listek__noga">
        <span>
          Zmagovalec: <span className="listek__crta" />
        </span>
        <span>
          Sodnik: <span className="listek__crta listek__crta--kratka" />
        </span>
      </div>
    </article>
  )
}

function Vrsta({ stran, stolpci }: { stran: StranListka; stolpci: number[] }) {
  return (
    <tr>
      <td className="listek__igralec">
        <span className="listek__ime">{stran.ime}</span>
        {/* Nedeljivi presledek ohrani višino vrstice, ko podnaslova ni. */}
        <span className="listek__klub">{stran.podnaslov ?? ' '}</span>
      </td>
      {stolpci.map((n) => (
        <td key={n} className="listek__celica" />
      ))}
      <td className="listek__nizi-celica" />
    </tr>
  )
}
