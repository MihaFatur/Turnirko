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
    <table className="tabela lestvica">
      <thead>
        <tr>
          <th className="lestvica__mesto">#</th>
          <th>Igralec</th>
          <th className="lestvica__stevilka" title="Odigrane tekme">Od.</th>
          <th className="lestvica__stevilka" title="Zmage">Z</th>
          <th className="lestvica__stevilka" title="Porazi">P</th>
          <th className="lestvica__stevilka" title="Dobljeni : izgubljeni nizi">Nizi</th>
          <th className="lestvica__stevilka" title="Razlika nizov">±</th>
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
                <Link to={`/igralci/${vrstica.idIgralca}/profil`}>
                  <strong>{vrstica.polnoIme}</strong>
                </Link>
                {vrstica.klub && <span className="lestvica__klub"> {vrstica.klub}</span>}
              </td>
              <td className="lestvica__stevilka">{vrstica.odigrane}</td>
              <td className="lestvica__stevilka">{vrstica.zmage}</td>
              <td className="lestvica__stevilka">{vrstica.porazi}</td>
              <td className="lestvica__stevilka">
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
