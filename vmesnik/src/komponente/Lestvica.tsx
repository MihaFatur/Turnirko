/* Lestvica skupine ali kroznega sistema: mesto, igralec, odigrane,
   zmage/porazi, nizi in razlika. Ime igralca vodi na njegov profil.

   Pri ekipnem dogodku je vrstica ekipa: profila nima, stolpec »Nizi« pa so
   dobljene in izgubljene posamične tekme srečanj (izid ekipne tekme je
   število dobljenih tekem, tako kot je izid posamične tekme število nizov). */
import { Link } from 'react-router-dom'

import type { VrsticaLestviceDto } from '../api/tipi'

interface Lastnosti {
  vrstice: VrsticaLestviceDto[]
  /* Koliko najboljših napreduje (obarva vrstice napredovanja). */
  napreduje?: number
  /* Strnjena različica za odprto skupino: šest stolpcev je tam preveč,
     ostane le, kar odloča o napredovanju (mesto, ime, Z, P, nizi).
     Odigrane so vsota Z + P, razliko pa bralec izpelje iz nizov. */
  strnjena?: boolean
  ekipno?: boolean
}

export function Lestvica({ vrstice, napreduje, strnjena = false, ekipno = false }: Lastnosti) {
  if (vrstice.length === 0) {
    return <p className="obvestilo">Ni še udeležencev.</p>
  }
  /* Kjer je odločil krog, stolpca »Nizi« in »±« vrstnega reda ne pojasnita:
     to je izkupiček v CELI skupini, krog pa razsodijo samo tekme MED
     izenačenimi. Drugouvrščeni ima tam lahko slabšo razliko od
     tretjeuvrščenega — brez opombe je videti kot napaka izpisa. */
  const jeKrog = vrstice.some((v) => v.krog !== null)
  return (
    <>
      <table className={'tabela' + (strnjena ? ' lestvica--skupina' : '')}>
        <caption className="samo-za-bralnik">
          {ekipno
            ? 'Lestvica skupine: mesto, ekipa, izkupiček in razlika posamičnih tekem'
            : 'Lestvica skupine: mesto, igralec, izkupiček in razlika nizov'}
        </caption>
        <thead>
          <tr>
            <th scope="col" className="lestvica__mesto">#</th>
            <th scope="col">{ekipno ? 'Ekipa' : 'Igralec'}</th>
            {!strnjena && (
              <th scope="col" className="lestvica__stevilka lestvica__odigrane" title="Odigrane tekme">Od.</th>
            )}
            <th scope="col" className="lestvica__stevilka" title="Zmage">Z</th>
            {/* Stolpec porazov na telefonu odpade, zato ima glava isto oznako
                stolpca kot celice - barvo nosi samo celica. */}
            <th scope="col" className="lestvica__stevilka lestvica__stolpec-p" title="Porazi">P</th>
            <th
              scope="col"
              className="lestvica__stevilka lestvica__nizi"
              title={ekipno ? 'Dobljene : izgubljene posamične tekme' : 'Dobljeni : izgubljeni nizi'}
            >
              {ekipno ? 'Tekme' : 'Nizi'}
            </th>
            {!strnjena && (
              <th scope="col" className="lestvica__stevilka" title={ekipno ? 'Razlika tekem' : 'Razlika nizov'}>
                ±
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {vrstice.map((vrstica, indeks) => {
            const mesto = vrstica.mesto ?? indeks + 1
            const napreduje_ = napreduje !== undefined && mesto <= napreduje
            const razlika = vrstica.niziZa - vrstica.niziProti
            return (
              <tr key={vrstica.idPrijave} className={napreduje_ ? 'lestvica__vrstica--napreduje' : ''}>
                <td className="lestvica__mesto">
                  {mesto}
                  {/* Znamenje ima svojo stalno sirino, ko je v tabeli sploh
                      kaksen krog - sicer bi se stevilke mest, ki so desno
                      poravnane, med vrsticami razsle. Stopinja je znamenje in
                      ne podatek, zato bralnik zaslona dobi besedo, ki jo
                      opomba pod tabelo pojasni. */}
                  {jeKrog && (
                    <span className="lestvica__krog" aria-hidden="true">
                      {vrstica.krog !== null ? '°' : ''}
                    </span>
                  )}
                  {vrstica.krog !== null && <span className="samo-za-bralnik"> (krog)</span>}
                </td>
                <td>
                  {vrstica.idIgralca !== null ? (
                    <Link to={`/igralci/${vrstica.idIgralca}/profil`} className="lestvica__ime">
                      {vrstica.polnoIme}
                    </Link>
                  ) : (
                    <span className="lestvica__ime">{vrstica.polnoIme}</span>
                  )}
                  {vrstica.klub && !imeVsebujeKlub(vrstica) && (
                    <span className="lestvica__klub"> {vrstica.klub}</span>
                  )}
                </td>
                {!strnjena && (
                  <td className="lestvica__stevilka lestvica__odigrane">{vrstica.odigrane}</td>
                )}
                <td className="lestvica__stevilka lestvica__zmage">{vrstica.zmage}</td>
                <td className="lestvica__stevilka lestvica__porazi lestvica__stolpec-p">
                  {vrstica.porazi}
                </td>
                <td className="lestvica__stevilka lestvica__nizi">
                  {vrstica.niziZa}:{vrstica.niziProti}
                </td>
                {!strnjena && (
                  <td className="lestvica__stevilka">
                    {razlika > 0 ? `+${razlika}` : razlika}
                  </td>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
      {jeKrog && (
        <p className="lestvica__opomba lestvica__opomba--krog">
          ° krog — mesta določa izkupiček med izenačenimi, ne skupna razlika{' '}
          {ekipno ? 'tekem' : 'nizov'}
        </p>
      )}
    </>
  )
}

/* Klubska ekipa se imenuje po klubu (»NTK Gorica 2«) - klub ob imenu bi ga
   samo ponovil. */
function imeVsebujeKlub(vrstica: VrsticaLestviceDto): boolean {
  return (
    vrstica.klub !== null &&
    vrstica.polnoIme.toLocaleLowerCase('sl').startsWith(vrstica.klub.toLocaleLowerCase('sl'))
  )
}
