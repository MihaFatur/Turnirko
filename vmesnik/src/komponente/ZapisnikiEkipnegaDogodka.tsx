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
import { Link } from 'react-router-dom'
import { useQueries } from '@tanstack/react-query'

import { srecanjaApi } from '../api/zahteve'
import type { DogodekDto, SrecanjePodrobnoDto } from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { SIRINA_LEZECEGA_LISTA_PX, useMeriloLista } from '../pomozno/meriloLista'
import {
  type IzbiraObrazca,
  type OblikaZapisnika,
  preberiOblikoZapisnika,
  privzetaOblika,
  shraniOblikoZapisnika,
} from '../pomozno/oblikaZapisnika'
import { sklonZapisnikov } from '../pomozno/oblikovanje'
import { EkipniZapisnikTurnirko } from './EkipniZapisnikTurnirko'
import { IzbirnikObrazca } from './IzbirnikObrazca'
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
  imeTurnirja,
  idjiSrecanj,
}: {
  dogodek: DogodekDto
  imeTurnirja: string | null
  /* Srecanja tekem, za katere ima smisel natisniti zapisnik, v vrstnem redu
     tiska. */
  idjiSrecanj: number[]
}) {
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
  const oblika: OblikaZapisnika = izbranaOblika ?? privzetaOblika(dogodek.formatSrecanja)
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

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/dogodki/${dogodek.id}`} className="povezava-nazaj">
          ← Nazaj na dogodek
        </Link>
        <span className="listki-orodja__stevec">
          {idjiSrecanj.length} {sklonZapisnikov(idjiSrecanj.length)}
        </span>
        <IzbirnikObrazca
          oblika={oblika}
          predloga={null}
          onIzbira={izberiObliko}
          razred="listki-orodja__izbira listki-orodja__izbira--zraven"
        />
        {!lezeci && (
          <label className="listki-orodja__izbira listki-orodja__izbira--zraven">
            Na list:
            <select value={naList} onChange={(d) => izberi(Number(d.target.value) as NaList)}>
              <option value={1}>1 zapisnik</option>
              <option value={2}>2 zapisnika</option>
              <option value={3}>3 zapisniki</option>
            </select>
          </label>
        )}
        <button
          className="gumb gumb--glavni"
          disabled={idjiSrecanj.length === 0 || !nalozeno}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div
        className={'listki-stran listki-stran--zapisnik' + (lezeci ? ' listki-stran--ekipni' : '')}
        ref={merilo.okvir}
        style={lezeci ? merilo.slog : undefined}
      >
        {/* Naslov je samo za zaslon: vsak list ima svojo glavo obrazca. */}
        <header className="listki-naslov zaslon-samo">
          <h1>
            {imeTurnirja ? `${imeTurnirja} — ` : ''}
            {dogodek.ime}
          </h1>
          <p>
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
          </p>
          {!lezeci && naList > 1 && nalozeno && (
            <p className={najmanjse < 0.7 ? 'listki-naslov__opozorilo' : undefined}>
              {najmanjse > 0.995
                ? 'Obrazec je v naravni velikosti.'
                : `Obrazec je pomanjšan na ${Math.round(najmanjse * 100)} %.`}
              {najmanjse < 0.7 && ' Pri tem formatu je pisanje z roko že tesno — poskusi z manj zapisniki na list.'}
            </p>
          )}
        </header>

        {napaka ? (
          <SporociloNapake napaka={napaka} />
        ) : idjiSrecanj.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih ekipnih tekem za tiskanje. Zapisnik se ustvari za tekmo, kjer
            sta znani obe ekipi in še ni odigrana.
          </p>
        ) : !nalozeno ? (
          <p className="obvestilo zaslon-samo">Nalaganje …</p>
        ) : lezeci ? (
          podatki.map((p) => <EkipniZapisnikTurnirko key={p.srecanje.id} podrobno={p} liga={undefined} />)
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
