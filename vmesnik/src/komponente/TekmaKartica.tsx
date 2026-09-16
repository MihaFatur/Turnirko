/* Kartica ene tekme v mrezi: oba udelezenca, rezultat in stanje.
   Katera tekma se na klik odzove in kaj klik pomeni (vnos rezultata ali
   zapisnik ekipnega srecanja), pove stran s KlikTekme - ostalo prepreci ze
   zaledje, a ne ponujamo klika.

   Vsaka polovica kartice nosi id prijave. Prijava je za dogodek ena sama,
   zato je ista v vseh kolih - prehod miske nad imenom osvetli vse pojavitve
   istega igralca in pot skozi mrezo se prebere brez klika. */
import type { KeyboardEvent, PointerEvent } from 'react'

import type { TekmaDto, Udelezenec } from '../api/tipi'
import { SpremembaRatinga } from './SpremembaRatinga'

/* Kaj pomeni klik na tekmo. Odloci stran, ker samo ona ve, kdo gleda:
   organizatorju klik odpre vnos rezultata, koncana tekma z vpisanimi tockami
   vsakemu gledalcu okno z nizi, pri ekipnem dogodku pa zapisnik srecanja
   (postava in posamicne tekme so javne). Kartica
   in vrstica seznama zato ne ugibata po stanju tekme, katera se odzove. */
export interface KlikTekme {
  klikljiva: (tekma: TekmaDto) => boolean
  naKlik: (tekma: TekmaDto) => void
  /* Namig ob prehodu miske, npr. »Klikni za vnos rezultata«. */
  namig: (tekma: TekmaDto) => string
}

interface Lastnosti {
  tekma: TekmaDto
  klik?: KlikTekme
  osvetljenaPrijava?: number | null
  naOsvetlitev?: (idPrijave: number | null) => void
}

/* Klub pod imenom. Posameznik ima svojega, par pa samo tedaj, kadar sta
   igralca iz istega kluba - mešan par bi sicer prilepil dve vrstici besedila
   pod dve vrstici imen in kartica bi se podvojila. Klubska ekipa se imenuje
   po klubu (»NTK Gorica 2«), zato bi klub pod njo samo ponovil ime. */
export function klubZaIzpis(udelezenec: Udelezenec): string | null {
  const klub = !udelezenec.polnoIme2
    ? udelezenec.klub
    : udelezenec.klub && udelezenec.klub === udelezenec.klub2 ? udelezenec.klub : null
  if (klub && udelezenec.polnoIme.toLocaleLowerCase('sl').startsWith(klub.toLocaleLowerCase('sl'))) {
    return null
  }
  return klub
}

/* Kratka oznaka posebnega izida ob rezultatu. */
const OZNAKA_POSEBNEGA_IZIDA: Record<string, string> = {
  PROSTO: 'prosto',
  BREZ_BOJA: 'b. b.',
  PREDAJA: 'predaja',
  DISKVALIFIKACIJA: 'diskv.',
}

/* Klikljiva tekma se sproži tudi s tipkovnico, kot gumb: Enter ali preslednica. */
export function obTipki(dogodek: KeyboardEvent, naKlik: () => void) {
  if (dogodek.key === 'Enter' || dogodek.key === ' ') {
    dogodek.preventDefault()
    naKlik()
  }
}

export function TekmaKartica({ tekma, klik, osvetljenaPrijava, naOsvetlitev }: Lastnosti) {
  const klikljiva = klik !== undefined && klik.klikljiva(tekma)

  const razredi = ['tekma', `tekma--${tekma.status}`]
  if (klikljiva) razredi.push('tekma--klikljiva')

  const oznakaIzida =
    tekma.status === 'KONCANA' && tekma.izidTip && tekma.izidTip !== 'IGRANO'
      ? OZNAKA_POSEBNEGA_IZIDA[tekma.izidTip]
      : null

  return (
    <div
      className={razredi.join(' ')}
      onClick={klikljiva ? () => klik.naKlik(tekma) : undefined}
      onKeyDown={klikljiva ? (d) => obTipki(d, () => klik.naKlik(tekma)) : undefined}
      role={klikljiva ? 'button' : undefined}
      tabIndex={klikljiva ? 0 : undefined}
      title={klikljiva ? klik.namig(tekma) : undefined}
    >
      <Stran
        udelezenec={tekma.udelezenec1}
        nizi={tekma.dobljeniNizi1}
        spremembaRatinga={tekma.spremembaElo1}
        tekma={tekma}
        osvetljenaPrijava={osvetljenaPrijava}
        naOsvetlitev={naOsvetlitev}
      />
      <Stran
        udelezenec={tekma.udelezenec2}
        nizi={tekma.dobljeniNizi2}
        spremembaRatinga={tekma.spremembaElo2}
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
  spremembaRatinga,
  tekma,
  osvetljenaPrijava,
  naOsvetlitev,
}: {
  udelezenec: Udelezenec | null
  nizi: number
  spremembaRatinga: number | null
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
          v dve vrstici.

          Par dobi drugo vrstico namesto ene dolge z ločilom: »Novak Ana /
          Zajc Eva« se v 240 px prelomi na poljubnem mestu in ni več razvidno,
          kje se prvo ime konča. Klub se pri paru izpiše samo, kadar je
          skupen - dva različna kluba bi kartico podvojila v višino. */}
      <span className="tekma__ime">
        <span className="tekma__ime-vrsta">{udelezenec.polnoIme}</span>
        {udelezenec.polnoIme2 && (
          <span className="tekma__ime-vrsta">{udelezenec.polnoIme2}</span>
        )}
        {klubZaIzpis(udelezenec) && (
          <span className="tekma__klub">{klubZaIzpis(udelezenec)}</span>
        )}
      </span>
      <span className="tekma__desno">
        <SpremembaRatinga vrednost={spremembaRatinga} />
        {/* Pri prostem prehodu rezultata ni - 0:0 bi samo begal. */}
        <span className="tekma__nizi">
          {tekma.status === 'KONCANA' && tekma.izidTip !== 'PROSTO' ? nizi : ''}
        </span>
      </span>
    </div>
  )
}
