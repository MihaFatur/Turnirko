/* Lestvica skupine ali kroznega sistema: mesto, igralec, odigrane,
   zmage/porazi, nizi in razlika. Ime igralca vodi na njegov profil. */
import { Link } from 'react-router-dom'

import type { VrsticaLestviceDto } from '../api/tipi'

interface Lastnosti {
  vrstice: VrsticaLestviceDto[]
  /* Koliko najboljših napreduje (obarva vrstice napredovanja). */
  napreduje?: number
}

export function Lestvica({ vrstice, napreduje }: Lastnosti) {
  if (vrstice.length === 0) {
    return <p className="obvestilo">Ni še udeležencev.</p>
  }
  return (
    <table className="tabela">
      <caption className="samo-za-bralnik">Lestvica skupine: mesto, igralec, izkupiček in razlika nizov</caption>
      <thead>
        <tr>
          <th scope="col" className="lestvica__mesto">#</th>
          <th scope="col">Igralec</th>
          <th scope="col" className="lestvica__stevilka lestvica__odigrane" title="Odigrane tekme">Od.</th>
          <th scope="col" className="lestvica__stevilka" title="Zmage">Z</th>
          <th scope="col" className="lestvica__stevilka" title="Porazi">P</th>
          <th scope="col" className="lestvica__stevilka lestvica__nizi" title="Dobljeni : izgubljeni nizi">
            Nizi
          </th>
          <th scope="col" className="lestvica__stevilka" title="Razlika nizov">±</th>
        </tr>
      </thead>
      <tbody>
        {vrstice.map((vrstica, indeks) => {
          const mesto = vrstica.mesto ?? indeks + 1
          const napreduje_ = napreduje !== undefined && mesto <= napreduje
          const razlika = vrstica.niziZa - vrstica.niziProti
          return (
            <tr key={vrstica.idPrijave} className={napreduje_ ? 'lestvica__vrstica--napreduje' : ''}>
              <td className="lestvica__mesto">{mesto}</td>
              <td>
                <Link to={`/igralci/${vrstica.idIgralca}/profil`} className="lestvica__ime">
                  {vrstica.polnoIme}
                </Link>
                {vrstica.klub && <span className="lestvica__klub"> {vrstica.klub}</span>}
              </td>
              <td className="lestvica__stevilka lestvica__odigrane">{vrstica.odigrane}</td>
              <td className="lestvica__stevilka lestvica__zmage">{vrstica.zmage}</td>
              <td className="lestvica__stevilka lestvica__porazi">{vrstica.porazi}</td>
              <td className="lestvica__stevilka lestvica__nizi">
                {vrstica.niziZa}:{vrstica.niziProti}
              </td>
              <td className="lestvica__stevilka">
                {razlika > 0 ? `+${razlika}` : razlika}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
