/* Natisljivi ekipni zapisniki vseh pripravljenih tekem ekipnega dogodka.

   Ekipna tekma turnirja je srecanje, zato je vsak list isti uradni obrazec
   NTZS kot pri ligi (ZapisnikEkipnegaDvoboja): imeni ekip sta ze natisnjeni,
   postavo in igralce posamicnih tekem / dvojic vpise ekipa sama. Podatke bere
   iz poizvedbe srecanja, ki jo deli s stranjo srecanja, zato so ze vnesene
   postave in izidi natisnjeni, prazno pa ostane to, kar se ni znano.

   Privzeto dobi vsak zapisnik svoj list. Organizator lahko izbere tudi 2 ali 3
   zapisnike na list (manj papirja, kadar je tekem veliko): obrazec se takrat
   zgosti, nato pa pomanjsa natanko toliko, da se v svojo rezo prilega. Merilo
   se IZMERI (visina obrazca je odvisna od formata: pri SNTL je vrstic deset,
   pri DSI tri), ne ugiba. */
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { useQueries } from '@tanstack/react-query'

import { srecanjaApi } from '../api/zahteve'
import type { DogodekDto, SlikaDto, SrecanjePodrobnoDto } from '../api/tipi'
import { SIRINA_LEZECEGA_LISTA_PX, useMeriloLista } from '../pomozno/meriloLista'
import {
  type IzbiraObrazca,
  type OblikaZapisnika,
  jeZaBralnik,
  preberiOblikoZapisnika,
  privzetaOblika,
  shraniOblikoZapisnika,
} from '../pomozno/oblikaZapisnika'
import { sklonListovTiska, type PripravljenListek } from '../pomozno/listki'
import { EkipniZapisnikTurnirko } from './EkipniZapisnikTurnirko'
import type { Izbira, IzbiraRazvrstitve } from './IzbiraListkov'
import { IzbirnikObrazca } from './IzbirnikObrazca'
import { ListkiPloskev } from './ListkiPloskev'
import { SporociloNapake } from './SporociloNapake'
import { ZapisnikEkipnegaDvoboja } from './ZapisnikEkipnegaDvoboja'

/* Uporabna visina lista A4 (297 mm) znotraj robov 10 mm, z 1 mm rezerve: tisk
   v zaokrozevanju sicer prelije na prazen list. */
const VISINA_LISTA_MM = 276
const RAZMIK_MED_ZAPISNIKI_MM = 3
/* Sirina uporabne povrsine lista (A4 210 mm brez robov); obrazec se meri pri
   tej sirini, ne pri sirini okna. */
const SIRINA_LISTA_MM = 190
const PX_NA_MM = 96 / 25.4

type NaList = 1 | 2 | 3
const KLJUC_SHRAMBE = 'turnirko-zapisnikov-na-list'

/* Zadnja izbira ostane v brskalniku (organizator ponovno natisne ob vsakem
   kolu); brez shrambe stran dela s privzetkom 1. */
function preberiIzbiro(): NaList {
  try {
    const v = Number(window.localStorage.getItem(KLJUC_SHRAMBE))
    return v === 2 || v === 3 ? v : 1
  } catch {
    return 1
  }
}

export function ZapisnikiEkipnegaDogodka({
  dogodek,
  nadnaslov,
  povezavaNazaj,
  logotipTurnirja,
  listki,
  izbira,
  razvrstitev,
}: {
  dogodek: DogodekDto
  /* »Turnir · Dogodek« v nadnaslovu črne glave in povezava nazaj na dogodek. */
  nadnaslov: string
  povezavaNazaj: string
  /* Logotip turnirja (V54) za glavo Turnirkovega zapisnika. */
  logotipTurnirja: SlikaDto | null
  /* Pripravljene tekme s srecanjem v vrstnem redu tiska. */
  listki: PripravljenListek[]
  /* Izbira tekem za tisk in sled tiska (skupna posamicnim listkom). */
  izbira: Izbira
  /* Po skupinah ali po kolih (samo pri vec skupinah). */
  razvrstitev?: IzbiraRazvrstitve
}) {
  /* Srecanja izbranih tekem, v vrstnem redu tiska. Tekma brez srecanja zapisnika
     nima (ListkiStran jo ze izlocil). */
  const idjiSrecanj = izbira.izbraniListki.flatMap((l) => (l.tekma.idSrecanje !== null ? [l.tekma.idSrecanje] : []))
  const srecanja = useQueries({
    queries: idjiSrecanj.map((id) => ({
      queryKey: ['srecanje', id],
      queryFn: () => srecanjaApi.podrobno(id),
    })),
  })

  const [naList, nastaviNaList] = useState<NaList>(preberiIzbiro)
  const izberi = (v: NaList) => {
    nastaviNaList(v)
    try {
      window.localStorage.setItem(KLJUC_SHRAMBE, String(v))
    } catch {
      /* izbira velja za to stran tudi brez shrambe */
    }
  }

  /* Turnirkov zapisnik je ležeči A4 in ga je en na list (list je že cel
     zapisnik), zato izbira »Na list« velja samo za uradni obrazec NTZS. */
  const [izbranaOblika, nastaviObliko] = useState<OblikaZapisnika | null>(preberiOblikoZapisnika)
  // zapisnik za bralnik je samo za ligo (V79): shranjena izbira z lige tu ne velja
  const oblika: OblikaZapisnika =
    (jeZaBralnik(izbranaOblika) ? null : izbranaOblika) ?? privzetaOblika(dogodek.formatSrecanja)
  const lezeci = oblika === 'TURNIRKO'
  const izberiObliko = (izbira: IzbiraObrazca) => {
    const nova: OblikaZapisnika = izbira === 'TURNIRKO' ? 'TURNIRKO' : 'NTZS'
    nastaviObliko(nova)
    shraniOblikoZapisnika(nova)
  }
  const merilo = useMeriloLista(SIRINA_LEZECEGA_LISTA_PX)

  /* Merilo vsake reze po id-ju srecanja; najmanjse pove, ali je obrazec ze
     tako majhen, da ga ni vec prijetno izpolnjevati z roko. */
  const [merila, nastaviMerila] = useState<Record<number, number>>({})
  const poroci = (id: number, merilo: number) =>
    nastaviMerila((m) => (Math.abs((m[id] ?? 1) - merilo) < 0.005 && id in m ? m : { ...m, [id]: merilo }))
  const najmanjse = naList === 1 ? 1 : Math.min(1, ...idjiSrecanj.map((id) => merila[id] ?? 1))

  const napaka = srecanja.find((q) => q.error)?.error
  const nalozeno = srecanja.every((q) => q.data !== undefined)
  const podatki = srecanja.flatMap((q) => (q.data ? [q.data] : []))

  const listov = lezeci ? idjiSrecanj.length : Math.ceil(idjiSrecanj.length / naList)
  const pomanjsano = !lezeci && naList > 1 && nalozeno
    ? najmanjse > 0.995
      ? 'Obrazec je v naravni velikosti.'
      : `Obrazec je pomanjšan na ${Math.round(najmanjse * 100)} %.`
        + (najmanjse < 0.7 ? ' Pri tem formatu je pisanje z roko že tesno: poskusi z manj zapisniki na list.' : '')
    : null

  return (
    <>
      <ListkiPloskev
        vrsta="zapisnik"
        nadnaslov={nadnaslov}
        naslov="Zapisniki za tiskanje"
        povezavaNazaj={povezavaNazaj}
        listki={listki}
        izbira={izbira}
        razvrstitev={razvrstitev}
        meta={`${listov} ${sklonListovTiska(listov)} A4`}
        nastavitve={
          <div className="listki-ploskev__razdelek">
            <IzbirnikObrazca
              oblika={oblika}
              predloga={null}
              onIzbira={izberiObliko}
              razred="listki-ploskev__nastavitev"
            />
            {!lezeci && (
              <label className="listki-ploskev__nastavitev">
                Na list:
                <select value={naList} onChange={(d) => izberi(Number(d.target.value) as NaList)}>
                  <option value={1}>1 zapisnik</option>
                  <option value={2}>2 zapisnika</option>
                  <option value={3}>3 zapisniki</option>
                </select>
              </label>
            )}
            {pomanjsano && (
              <span className={'listki-ploskev__navodilo' + (najmanjse < 0.7 ? ' listki-ploskev__navodilo--opozorilo' : '')}>
                {pomanjsano}
              </span>
            )}
          </div>
        }
        navodilo={lezeci ? 'A4 ležeče · 100 % · robovi 10 mm' : 'A4 pokončno · 100 % · robovi 10 mm'}
        tiskCaka={idjiSrecanj.length > 0 && !nalozeno}
        onNatisni={izbira.natisni}
        onPotrdi={izbira.potrdi}
        opozorilo={napaka ? <SporociloNapake napaka={napaka} /> : undefined}
      />

      {/* Zapisniki so samo za tisk (@media print); na zaslonu ostanejo v postavitvi, a nevidni:
          obrazec za 2 ali 3 na list se pomanjša po izmerjeni višini. */}
      <div
        className={'listki-stran listki-stran--zapisnik listki-stran--skrit' + (lezeci ? ' listki-stran--ekipni' : '')}
        ref={merilo.okvir}
        style={lezeci ? merilo.slog : undefined}
      >
        {napaka || idjiSrecanj.length === 0 || !nalozeno ? null : lezeci ? (
          podatki.map((p) => (
            <EkipniZapisnikTurnirko key={p.srecanje.id} podrobno={p} liga={undefined} logotip={logotipTurnirja} />
          ))
        ) : naList === 1 ? (
          podatki.map((p) => (
            <div key={p.srecanje.id} className="zapisnik-stran">
              <ZapisnikEkipnegaDvoboja podrobno={p} liga={undefined} varianta={null} />
            </div>
          ))
        ) : (
          razdeli(podatki, naList).map((list) => (
            <div
              key={list[0].srecanje.id}
              className="zapisnik-list"
              style={{ '--rez': `${visinaReze(naList)}mm` } as CSSProperties}
            >
              {list.map((p) => (
                <ZapisnikVRezi key={p.srecanje.id} id={p.srecanje.id} naList={naList} podatki={p} onMerilo={poroci}>
                  <ZapisnikEkipnegaDvoboja podrobno={p} liga={undefined} varianta={null} gosto />
                </ZapisnikVRezi>
              ))}
            </div>
          ))
        )}
      </div>
    </>
  )
}

/* Visina ene reze: list brez razmikov med zapisniki, deljen na enake dele. */
function visinaReze(naList: NaList): number {
  return (VISINA_LISTA_MM - (naList - 1) * RAZMIK_MED_ZAPISNIKI_MM) / naList
}

function razdeli<T>(seznam: T[], velikost: number): T[][] {
  const liste: T[][] = []
  for (let i = 0; i < seznam.length; i += velikost) liste.push(seznam.slice(i, i + velikost))
  return liste
}

/* Reza lista: obrazec pomanjsa (CSS zoom, ki spremeni tudi postavitev, zato
   obrazec napolni sirino reze) natanko toliko, da se prilega visini reze.
   Meri pri tiskarski sirini (190 mm) in ob nalozenih pisavah, ker od njiju
   zavisi visina glave in postave; nikoli ne povecuje. */
function ZapisnikVRezi({
  id,
  naList,
  podatki,
  onMerilo,
  children,
}: {
  id: number
  naList: NaList
  podatki: SrecanjePodrobnoDto
  onMerilo: (id: number, merilo: number) => void
  children: ReactNode
}) {
  const reza = useRef<HTMLDivElement>(null)

  /* Obrazec je pred merjenjem na celo sirino in zoom 1, po njem zoom nastavljen
     imperativno: React stanje bi pomenilo se en izris na obrazec. */
  const prilagodi = () => {
    const obrazec = reza.current?.firstElementChild
    if (!(obrazec instanceof HTMLElement)) return
    obrazec.style.zoom = '1'
    obrazec.style.width = `${SIRINA_LISTA_MM}mm`
    const naravna = obrazec.offsetHeight
    obrazec.style.width = ''
    /* 1,5 % rezerve: pomanjsan obrazec se na papirju zaokrozi navzgor in bi
       sicer spodnji rob (obroba) odrezala reza. */
    const merilo = Math.min(1, (visinaReze(naList) * PX_NA_MM * 0.985) / naravna)
    obrazec.style.zoom = String(merilo)
    onMerilo(id, merilo)
  }

  useLayoutEffect(prilagodi, [podatki, naList])
  /* Pisave pridejo po prvem izrisu; visina obrazca se z njimi spremeni. */
  useEffect(() => {
    let aktivno = true
    document.fonts?.ready.then(() => aktivno && prilagodi())
    return () => {
      aktivno = false
    }
  }, [podatki, naList])

  return (
    <div ref={reza} className="zapisnik-rez">
      {children}
    </div>
  )
}
