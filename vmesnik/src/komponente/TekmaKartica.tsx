/* Kartica ene tekme v mrezi: oba udelezenca, rezultat in stanje.
   Klik je mogoc samo, ko je rezultat smiselno vnesti (oba igralca znana,
   tekma se ni koncana) - ostalo prepreci ze zaledje, a ne ponujamo klika.

   Vsaka polovica kartice nosi id prijave. Prijava je za dogodek ena sama,
   zato je ista v vseh kolih - prehod miske nad imenom osvetli vse pojavitve
   istega igralca in pot skozi mrezo se prebere brez klika. */
import type { PointerEvent } from 'react'

import type { TekmaDto, Udelezenec } from '../api/tipi'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  tekma: TekmaDto
  naKlik?: (tekma: TekmaDto) => void
  osvetljenaPrijava?: number | null
  naOsvetlitev?: (idPrijave: number | null) => void
}

/* Kratka oznaka posebnega izida ob rezultatu. */
const OZNAKA_POSEBNEGA_IZIDA: Record<string, string> = {
  PROSTO: 'prosto',
  BREZ_BOJA: 'b. b.',
  PREDAJA: 'predaja',
  DISKVALIFIKACIJA: 'diskv.',
}

export function TekmaKartica({ tekma, naKlik, osvetljenaPrijava, naOsvetlitev }: Lastnosti) {
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
        tekma={tekma}
        osvetljenaPrijava={osvetljenaPrijava}
        naOsvetlitev={naOsvetlitev}
      />
      <Stran
        udelezenec={tekma.udelezenec2}
        nizi={tekma.dobljeniNizi2}
        spremembaElo={tekma.spremembaElo2}
        tekma={tekma}
        osvetljenaPrijava={osvetljenaPrijava}
        naOsvetlitev={naOsvetlitev}
      />
      {oznakaIzida && <div className="tekma__izid">{oznakaIzida}</div>}
    </div>
  )
}

function Stran({
  udelezenec,
  nizi,
  spremembaElo,
  tekma,
  osvetljenaPrijava,
  naOsvetlitev,
}: {
  udelezenec: Udelezenec | null
  nizi: number
  spremembaElo: number | null
  tekma: TekmaDto
  osvetljenaPrijava?: number | null
  naOsvetlitev?: (idPrijave: number | null) => void
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
  const osvetljena = osvetljenaPrijava === udelezenec.idPrijave

  /* Z misko osvetli prehod, na dotik pa dotik (in drug dotik ga odstrani) -
     dotik ne poslje mouseleave, zato bi osvetlitev sicer obtičala. */
  function obKazalcu(dogodek: PointerEvent<HTMLDivElement>, vstop: boolean) {
    if (!naOsvetlitev || dogodek.pointerType !== 'mouse') return
    naOsvetlitev(vstop ? udelezenec!.idPrijave : null)
  }

  function obDotiku(dogodek: PointerEvent<HTMLDivElement>) {
    if (!naOsvetlitev || dogodek.pointerType === 'mouse') return
    naOsvetlitev(osvetljena ? null : udelezenec!.idPrijave)
  }

  return (
    <div
      className={
        'tekma__stran' +
        (jeZmagovalec ? ' tekma__stran--zmagovalec' : '') +
        (jePorazenec ? ' tekma__stran--porazenec' : '') +
        (osvetljena ? ' tekma__stran--osvetljena' : '')
      }
      onPointerEnter={(d) => obKazalcu(d, true)}
      onPointerLeave={(d) => obKazalcu(d, false)}
      onPointerDown={obDotiku}
    >
      {/* Kartica v mrezi je siroka 240 px: ime, klub in nizi. Rating pred
          tekmo je tu odvec (bere se na profilu igralca) in bi ime prelomil
          v dve vrstici. */}
      <span className="tekma__ime">
        <span className="tekma__ime-vrsta">{udelezenec.polnoIme}</span>
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
