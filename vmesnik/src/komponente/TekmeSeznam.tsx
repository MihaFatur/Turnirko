/* Seznam tekem (krozni sistem, skupine): vsaka vrstica prikaze oba
   igralca in rezultat; ce je vnos smiseln in gre za administratorja,
   je vrstica klikljiva za vnos rezultata. */
import type { TekmaDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
}

export function TekmeSeznam({ tekme, naKlikTekme }: Lastnosti) {
  if (tekme.length === 0) {
    return <p className="obvestilo">Ni tekem.</p>
  }
  return (
    <ul className="tekme-seznam">
      {tekme.map((tekma) => (
        <Vrstica key={tekma.id} tekma={tekma} naKlik={naKlikTekme} />
      ))}
    </ul>
  )
}

function Vrstica({ tekma, naKlik }: { tekma: TekmaDto; naKlik?: (t: TekmaDto) => void }) {
  const koncana = tekma.status === 'KONCANA'
  const klikljiva =
    naKlik !== undefined && (tekma.status === 'PRIPRAVLJENA' || tekma.status === 'V_IGRI')

  const ime1 = tekma.udelezenec1?.polnoIme ?? '—'
  const ime2 = tekma.udelezenec2?.polnoIme ?? '—'
  const zmagovalec1 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec1?.idPrijave
  const zmagovalec2 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec2?.idPrijave

  const posebni =
    koncana && tekma.izidTip && tekma.izidTip !== 'IGRANO' ? OZNAKE_IZID[tekma.izidTip] : null

  return (
    <li
      className={'tekme-seznam__vrstica' + (klikljiva ? ' tekme-seznam__vrstica--klikljiva' : '')}
      onClick={klikljiva ? () => naKlik!(tekma) : undefined}
      title={klikljiva ? 'Klikni za vnos rezultata' : undefined}
    >
      <span className={'tekme-seznam__igralec' + (zmagovalec1 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {ime1}
        {tekma.ratingPred1 !== null && <span className="tekme-seznam__rating">{tekma.ratingPred1}</span>}
        {koncana && <SpremembaElo vrednost={tekma.spremembaElo1} />}
      </span>
      <span className="tekme-seznam__rezultat">
        {koncana ? `${tekma.dobljeniNizi1} : ${tekma.dobljeniNizi2}` : klikljiva ? 'vnesi' : '–'}
      </span>
      <span className={'tekme-seznam__igralec' + (zmagovalec2 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {koncana && <SpremembaElo vrednost={tekma.spremembaElo2} />}
        {tekma.ratingPred2 !== null && <span className="tekme-seznam__rating">{tekma.ratingPred2}</span>}
        {ime2}
      </span>
      {posebni && <span className="tekme-seznam__izid">{posebni}</span>}
    </li>
  )
}
