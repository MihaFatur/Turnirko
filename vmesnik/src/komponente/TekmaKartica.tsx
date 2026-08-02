/* Kartica ene tekme v mrezi: oba udelezenca, rezultat in stanje.
   Klik je mogoc samo, ko je rezultat smiselno vnesti (oba igralca znana,
   tekma se ni koncana) - ostalo prepreci ze zaledje, a ne ponujamo klika. */
import type { TekmaDto, Udelezenec } from '../api/tipi'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  tekma: TekmaDto
  naKlik?: (tekma: TekmaDto) => void
}

/* Kratka oznaka posebnega izida ob rezultatu. */
const OZNAKA_POSEBNEGA_IZIDA: Record<string, string> = {
  PROSTO: 'prosto',
  BREZ_BOJA: 'b. b.',
  PREDAJA: 'predaja',
  DISKVALIFIKACIJA: 'diskv.',
}

export function TekmaKartica({ tekma, naKlik }: Lastnosti) {
  const klikljiva =
    naKlik !== undefined &&
    (tekma.status === 'PRIPRAVLJENA' || tekma.status === 'V_IGRI')

  const razredi = ['tekma', `tekma--${tekma.status}`]
  if (klikljiva) razredi.push('tekma--klikljiva')

  const oznakaIzida =
    tekma.status === 'KONCANA' && tekma.izidTip && tekma.izidTip !== 'IGRANO'
      ? OZNAKA_POSEBNEGA_IZIDA[tekma.izidTip]
      : null

  return (
    <div
      className={razredi.join(' ')}
      onClick={klikljiva ? () => naKlik(tekma) : undefined}
      title={klikljiva ? 'Klikni za vnos rezultata' : undefined}
    >
      <Stran
        udelezenec={tekma.udelezenec1}
        nizi={tekma.dobljeniNizi1}
        spremembaElo={tekma.spremembaElo1}
        ratingPred={tekma.ratingPred1}
        tekma={tekma}
      />
      <Stran
        udelezenec={tekma.udelezenec2}
        nizi={tekma.dobljeniNizi2}
        spremembaElo={tekma.spremembaElo2}
        ratingPred={tekma.ratingPred2}
        tekma={tekma}
      />
      {oznakaIzida && <div className="tekma__izid">{oznakaIzida}</div>}
    </div>
  )
}

function Stran({
  udelezenec,
  nizi,
  spremembaElo,
  ratingPred,
  tekma,
}: {
  udelezenec: Udelezenec | null
  nizi: number
  spremembaElo: number | null
  ratingPred: number | null
  tekma: TekmaDto
}) {
  /* Prazna stran: pri prostem prehodu "prosto", sicer igralec se ni znan. */
  if (!udelezenec) {
    return (
      <div className="tekma__stran tekma__stran--prazna">
        <span className="tekma__ime">
          {tekma.izidTip === 'PROSTO' ? '— prosto —' : 'še ni znan'}
        </span>
      </div>
    )
  }

  /* Zmagovalec je krepek v polnem crnilu, porazenec pobledi - tako je izid
     razberljiv tudi brez branja stevilk. */
  const koncana = tekma.status === 'KONCANA' && tekma.izidTip !== 'PROSTO'
  const jeZmagovalec = koncana && tekma.idZmagovalcaPrijave === udelezenec.idPrijave
  const jePorazenec = koncana && !jeZmagovalec && tekma.idZmagovalcaPrijave !== null

  return (
    <div
      className={
        'tekma__stran' +
        (jeZmagovalec ? ' tekma__stran--zmagovalec' : '') +
        (jePorazenec ? ' tekma__stran--porazenec' : '')
      }
    >
      <span className="tekma__ime">
        <span className="tekma__ime-vrsta">
          {udelezenec.polnoIme}
          {ratingPred !== null && <span className="tekma__rating">{ratingPred}</span>}
        </span>
        {udelezenec.klub && <span className="tekma__klub">{udelezenec.klub}</span>}
      </span>
      <span className="tekma__desno">
        <SpremembaElo vrednost={spremembaElo} />
        {/* Pri prostem prehodu rezultata ni - 0:0 bi samo begal. */}
        <span className="tekma__nizi">
          {tekma.status === 'KONCANA' && tekma.izidTip !== 'PROSTO' ? nizi : ''}
        </span>
      </span>
    </div>
  )
}
