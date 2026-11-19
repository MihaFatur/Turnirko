/* Izris bloka vsebine članka — isti v predalu in na strani Pomoč. */
import type { Blok, Clanek } from './tipi'
import { Besedilo } from './Besedilo'
import { Shema } from './sheme'

/* ID razdelka v DOM-u: predpona loči predal od strani, kadar sta odprta
   hkrati (predal nad stranjo Pomoč), sicer bi skok na razdelek našel napačnega. */
export function idRazdelka(predpona: string, razdelek: string): string {
  return `${predpona}-${razdelek}`
}

function IzrisBloka({ blok, predpona }: { blok: Blok; predpona: string }) {
  switch (blok.tip) {
    case 'odstavek':
      return (
        <p className="pomoc-odstavek">
          <Besedilo besedilo={blok.besedilo} />
        </p>
      )
    case 'razdelek':
      return (
        <h3 className="pomoc-razdelek" id={idRazdelka(predpona, blok.id)} tabIndex={-1}>
          {blok.naslov}
        </h3>
      )
    case 'koraki':
      return (
        <ol className="pomoc-koraki">
          {blok.postavke.map((postavka, i) => (
            <li key={i} className="pomoc-koraki__korak">
              <span className="pomoc-koraki__st" aria-hidden="true">
                {i + 1}
              </span>
              <span>
                <Besedilo besedilo={postavka} />
              </span>
            </li>
          ))}
        </ol>
      )
    case 'seznam':
      return (
        <ul className="pomoc-seznam">
          {blok.postavke.map((postavka, i) => (
            <li key={i}>
              <Besedilo besedilo={postavka} />
            </li>
          ))}
        </ul>
      )
    case 'shema':
      return (
        <figure className="pomoc-shema">
          <Shema kljuc={blok.shema} />
          {blok.opis && <figcaption className="pomoc-shema__opis">{blok.opis}</figcaption>}
        </figure>
      )
    case 'pazi':
    case 'nasvet':
      return (
        <p className={'pomoc-opomba pomoc-opomba--' + blok.tip}>
          <span className="pomoc-opomba__oznaka">{blok.tip === 'pazi' ? 'Pazi' : 'Nasvet'}</span>
          <span>
            <Besedilo besedilo={blok.besedilo} />
          </span>
        </p>
      )
    case 'kje':
      return (
        <p className="pomoc-kje">
          <span className="pomoc-kje__oznaka">Kje</span>
          {blok.pot.map((korak, i) => (
            <span key={i} className="pomoc-kje__del">
              {i > 0 && (
                <span className="pomoc-kje__puscica" aria-hidden="true">
                  →
                </span>
              )}
              <span className="pomoc-kje__korak">{korak}</span>
            </span>
          ))}
        </p>
      )
    case 'tabela':
      return (
        <div className="tabela-ovoj pomoc-tabela-ovoj">
          <table className="pomoc-tabela">
            <thead>
              <tr>
                {blok.glava.map((celica, i) => (
                  <th key={i} scope="col">
                    {celica}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {blok.vrstice.map((vrstica, i) => (
                <tr key={i}>
                  {vrstica.map((celica, j) =>
                    j === 0 ? (
                      <th key={j} scope="row">
                        <Besedilo besedilo={celica} />
                      </th>
                    ) : (
                      <td key={j}>
                        <Besedilo besedilo={celica} />
                      </td>
                    ),
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
  }
}

export function VsebinaClanka({ clanek, predpona }: { clanek: Clanek; predpona: string }) {
  return (
    <div className="pomoc-vsebina">
      {clanek.vsebina.map((blok, i) => (
        <IzrisBloka key={i} blok={blok} predpona={predpona} />
      ))}
    </div>
  )
}
