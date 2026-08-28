/* Mreža meseca, ključ pod njo in vrstica vnosa — skupno za sklop na domači
   strani in za stran celotnega koledarja. Obe morata brati enako: dan, ki je
   na domači strani moder, mora biti moder tudi v celotnem koledarju.

   Zakaj mreža in ne seznam: vprašanje »kdaj je naslednji turnir« je vprašanje
   o dnevu v tednu (»naslednjo soboto«) in ne o datumu. Seznam datumov nanj
   odgovori šele po branju.

   Barva NI okras in tudi ne identiteta: moder pas je turnir, zelen ligaško
   kolo (glej tonVnosa v pomozno/koledar.ts). Ker sta tona dva in stalna, ju
   pojasni ključ pod mrežo in ne legenda z imeni — imena nosi seznam ob mreži.

   Zakaj mreža iz div-ov in ne <table>: večdnevni turnir je EN pas čez dneve
   (`grid-column: N / span M`), tabela pa čez celice ne zna risati. Bralnik
   zaslona dobi isto branje kot prej prek role="table"/"row"/"cell". */
import type { CSSProperties, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { KoledarVnosDto } from '../api/tipi'
import { ZnackaStatusa } from './Znacka'
import {
  DNEVI_V_TEDNU,
  celiceMeseca,
  imeMeseca,
  jeVikend,
  kljucTekmovanja,
  poDnevih,
  premakniMesec,
  stevilkaDneva,
  tonVnosa,
  trakoviTedna,
  type Mesec,
  type Trak,
} from '../pomozno/koledar'
import { datumskiBlok, oblikujObdobjeKratko, oblikujTermin } from '../pomozno/oblikovanje'

/* Ton kot slog: pas v mreži in levi rob vrstice ga bereta iz iste
   spremenljivke. Nevtralnega črnila ni več — vsak vnos ima ton, ker ton
   pomeni vrsto tekmovanja. */
export function slogTona(ton: 1 | 2): CSSProperties {
  return { '--ton-vnosa': `var(--barva-ton-${ton})` } as CSSProperties
}

/* Pot do tekmovanja: turnir oz. liga. */
export function potVnosa(vnos: KoledarVnosDto): string {
  return vnos.vrsta === 'TURNIR' ? `/turnirji/${vnos.id}` : `/lige/${vnos.id}`
}

/* Katere postavke nosi ključ pod mrežo. Cel koledar na namizju ima prostor za
   vse tri, sklop na domači strani in telefon za dve — tam »Odigrano« odpade,
   ker je motnost brez primerjave ob sebi tako ali tako neberljiva. */
export type OblikaKljuca = 'poln' | 'dvodelni' | 'kratek'

export function MrezaMeseca({
  mesec,
  vnosi,
  danes,
  izbraniDan,
  naDan,
  naMesec,
  naDanes,
  desno,
  kljuc = 'dvodelni',
  najvecPasov = 4,
  sStevcem = true,
  sklop = false,
}: {
  mesec: Mesec
  /* Vnosi, ki se dotikajo tega meseca; komponenta jih sama razbije po dnevih. */
  vnosi: KoledarVnosDto[]
  danes: string
  izbraniDan?: string | null
  /* Brez tega dnevi niso klikljivi (mreža je le pregled). */
  naDan?: (dan: string) => void
  /* Brez tega ni puščic za listanje — sklop na domači strani jih nima, ker je
     tam mesec vedno tekoči in listanje pripada celotnemu koledarju. */
  naMesec?: (m: Mesec) => void
  /* Pot nazaj v tekoči mesec; stran jo poda samo, kadar gledalec ni v njem.
     Brez nje se iz sezone 2013/14 vrne le s puščico, stokrat. */
  naDanes?: () => void
  /* Desni konec glave meseca — v sklopu na domači strani števec tekmovanj. */
  desno?: ReactNode
  kljuc?: OblikaKljuca
  /* Koliko pasov trakov gre v celico, preden se ostalo prešteje v »+N«. */
  najvecPasov?: number
  /* Ali se »+N« izriše. V kompaktni celici (58–60 px) zanj pod trakovi ni
     prostora — trije pasovi sežejo do njenega dna in števec bi se z njimi
     prekril. Tam je mreža pregled: dan je gumb in njegova tekmovanja izpiše
     seznam ob njej oz. celoten koledar. */
  sStevcem?: boolean
  /* Kompaktna mreža sklopa na domači strani. */
  sklop?: boolean
}) {
  const dnevi = poDnevih(vnosi)
  const celice = celiceMeseca(mesec)
  const tedni: (string | null)[][] = []
  for (let i = 0; i < celice.length; i += 7) tedni.push(celice.slice(i, i + 7))

  return (
    <div className={'koledar' + (sklop ? ' koledar--sklop' : '')}>
      <div className="koledar__glava">
        {naMesec && (
          <button
            type="button"
            className="koledar__krmar"
            onClick={() => naMesec(premakniMesec(mesec, -1))}
            aria-label="Prejšnji mesec"
          >
            ←
          </button>
        )}
        <span className="koledar__mesec">{imeMeseca(mesec)}</span>
        {naDanes && (
          <button type="button" className="povezava-gumb koledar__danes" onClick={naDanes}>
            Danes
          </button>
        )}
        {desno && <span className="koledar__stevec">{desno}</span>}
        {naMesec && (
          <button
            type="button"
            className="koledar__krmar"
            onClick={() => naMesec(premakniMesec(mesec, 1))}
            aria-label="Naslednji mesec"
          >
            →
          </button>
        )}
      </div>

      <div className="koledar__mreza" role="table" aria-label={imeMeseca(mesec)}>
        <div className="koledar__imena" role="row">
          {DNEVI_V_TEDNU.map((dan) => (
            <span key={dan} role="columnheader" className="koledar__ime-dneva">
              {dan}
            </span>
          ))}
        </div>
        {tedni.map((teden, i) => (
          <Teden
            key={i}
            teden={teden}
            dnevi={dnevi}
            vnosi={vnosi}
            danes={danes}
            izbraniDan={izbraniDan}
            naDan={naDan}
            najvecPasov={najvecPasov}
            sStevcem={sStevcem}
          />
        ))}
      </div>

      {/* Ključ pod prazno mrežo bi razlagal barve, ki jih ni. */}
      {vnosi.length > 0 && <KljucKoledarja oblika={kljuc} />}
    </div>
  )
}

/* En teden: sedem celic in nad njimi pas trakov.

   Trakovi so ločena, absolutno pozicionirana mreža sedmih stolpcev in ne
   vsebina celic — samo tako gre večdnevni turnir čez dneve kot en element.
   Celice pod njim ostanejo zadetkovna površina; trak jo prestreže sam
   (pointer-events), da ima svoj title. */
function Teden({
  teden,
  dnevi,
  vnosi,
  danes,
  izbraniDan,
  naDan,
  najvecPasov,
  sStevcem,
}: {
  teden: (string | null)[]
  dnevi: Map<string, KoledarVnosDto[]>
  vnosi: KoledarVnosDto[]
  danes: string
  izbraniDan?: string | null
  naDan?: (dan: string) => void
  najvecPasov: number
  sStevcem: boolean
}) {
  const { trakovi, skriti } = trakoviTedna(teden, vnosi, najvecPasov)

  return (
    <div className="koledar__teden" role="row">
      {teden.map((dan, j) => (
        <Celica
          key={dan ?? `prazna-${j}`}
          dan={dan}
          vnosi={dan ? dnevi.get(dan) ?? [] : []}
          skritih={dan && sStevcem ? skriti.get(dan) ?? 0 : 0}
          danes={danes}
          izbran={dan !== null && dan === izbraniDan}
          naDan={naDan}
        />
      ))}
      {/* Pasovi so slika dneva, ne njegov zapis: imena tekmovanj nosita
          aria-label celice in seznam ob mreži, zato tu bralnika ne ponavljamo. */}
      <div className="koledar__trakovi" aria-hidden="true">
        {trakovi.map((trak) => (
          <TrakTedna key={kljucTraku(trak)} trak={trak} danes={danes} />
        ))}
      </div>
    </div>
  )
}

function kljucTraku(trak: Trak): string {
  return `${kljucTekmovanja(trak.vnos)}|${trak.vnos.datum}|${trak.vnos.kolo ?? ''}|${trak.stolpec}`
}

function TrakTedna({ trak, danes }: { trak: Trak; danes: string }) {
  const pretekel = trak.vnos.datumKonca < danes
  return (
    <span
      className={'koledar__trak' + (pretekel ? ' koledar__trak--pretekel' : '')}
      title={trak.vnos.ime}
      style={{
        ...slogTona(tonVnosa(trak.vnos)),
        gridColumn: `${trak.stolpec} / span ${trak.razpon}`,
        gridRow: `${trak.pas + 1} / span 1`,
      }}
    />
  )
}

function Celica({
  dan,
  vnosi,
  skritih,
  danes,
  izbran,
  naDan,
}: {
  dan: string | null
  vnosi: KoledarVnosDto[]
  skritih: number
  danes: string
  izbran: boolean
  naDan?: (dan: string) => void
}) {
  if (dan === null) {
    /* Dan pred prvim v mesecu in za zadnjim: brez črt in brez dejanja, da
       mesec dobi obliko in ne prazne celice v fokusnem zaporedju. */
    return <div role="cell" className="koledar__celica koledar__celica--prazna" />
  }

  const razredi = [
    'koledar__celica',
    jeVikend(dan) ? 'koledar__celica--vikend' : '',
    izbran ? 'koledar__celica--izbran' : '',
  ]
    .filter(Boolean)
    .join(' ')

  const vsebina = (
    <>
      <span
        className={'koledar__stevilka' + (dan === danes ? ' koledar__stevilka--danes' : '')}
      >
        {stevilkaDneva(dan)}
      </span>
      {skritih > 0 && <span className="koledar__vec">+{skritih}</span>}
    </>
  )

  if (vnosi.length === 0 || !naDan) {
    return (
      <div role="cell" className={razredi}>
        <span className="koledar__dan">{vsebina}</span>
      </div>
    )
  }

  return (
    <div role="cell" className={razredi}>
      <button
        type="button"
        className="koledar__dan koledar__dan--klik"
        onClick={() => naDan(dan)}
        aria-pressed={izbran}
        aria-label={`${stevilkaDneva(dan)}. — ${vnosi.map((v) => v.ime).join(', ')}`}
      >
        {vsebina}
      </button>
    </div>
  )
}

/* Ključ pod mrežo: kaj pomeni kateri ton.

   Nadomešča prejšnjo legendo z imeni tekmovanj. Ta je rasla s pogledom (en
   mesec uvožene zgodovine ima tudi šestnajst tekmovanj) in je zavzela več
   prostora kot mreža sama; ključ ima vedno dve ali tri postavke.

   DESIGN.md, razdelek 2, pogoj 3 (»barva ni nikoli edini nosilec podatka«)
   je izpolnjen dvakrat: ključ stoji pod vsako mrežo, seznam ob njej pa vsako
   tekmovanje izpiše z imenom. */
export function KljucKoledarja({ oblika }: { oblika: OblikaKljuca }) {
  return (
    <ul className={'koledar-kljuc' + (oblika === 'poln' ? ' koledar-kljuc--poln' : '')}>
      <li className="koledar-kljuc__postavka">
        <span className="koledar-kljuc__ploskev" style={slogTona(1)} aria-hidden="true" />
        Turnir
      </li>
      <li className="koledar-kljuc__postavka">
        <span className="koledar-kljuc__ploskev" style={slogTona(2)} aria-hidden="true" />
        {oblika === 'kratek' ? 'Kolo' : 'Ligaško kolo'}
      </li>
      {oblika === 'poln' && (
        <li className="koledar-kljuc__postavka">
          <span
            className="koledar-kljuc__ploskev koledar-kljuc__ploskev--odigrano"
            style={slogTona(1)}
            aria-hidden="true"
          />
          Odigrano
        </li>
      )}
    </ul>
  )
}

/* Vrstica enega vnosa: datumski blok, ime in mono meta. Uporabljata jo sklop
   »Naslednje« na domači strani in izpis izbranega dneva v celotnem koledarju.
   Ton nosi levi rob — isti vzorec kot označena vrstica drugod v sistemu.

   Odigrano je bledo (0,62): brez tega prihajajoče in odigrano izgledata enako
   in gledalec mora vsako vrstico prebrati, preden ve, katera ga zadeva. */
export function VrsticaKoledarja({
  vnos,
  danes,
  dan,
  sPari = false,
}: {
  vnos: KoledarVnosDto
  danes: string
  /* Dan, pod katerim vrstica stoji. Večdnevni turnir se v seznamu po dnevih
     upravičeno pojavi pri vsakem svojem dnevu in datumski blok mora takrat
     kazati TISTI dan — »3. okt« pod naslovom »nedelja, 4. oktobra« je laž.
     Brez tega (sklop »Naslednje«) obvelja začetek vnosa. */
  dan?: string
  /* Pari kola pod vrstico; samo v celotnem koledarju, kjer je prostor. */
  sPari?: boolean
}) {
  const blok = datumskiBlok(dan ?? vnos.datum)
  const odigran = vnos.datumKonca < danes
  return (
    <div
      className={'koledar-vnos' + (odigran ? ' koledar-vnos--odigran' : '')}
      style={slogTona(tonVnosa(vnos))}
    >
      <Link to={potVnosa(vnos)} className="koledar-vnos__vrstica">
        <span className="koledar-vnos__datum">
          <span className="koledar-vnos__dan">{blok.dan}</span>
          <span className="koledar-vnos__mesec">{blok.mesec}</span>
        </span>
        <span className="koledar-vnos__telo">
          <span className="koledar-vnos__ime">{vnos.ime}</span>
          <span className="koledar-vnos__meta">{opisVnosa(vnos)}</span>
        </span>
        <ZnackaStatusa status={vnos.status} />
      </Link>

      {sPari && vnos.srecanja.length > 0 && (
        <ul className="koledar-vnos__pari">
          {vnos.srecanja.map((par) => (
            <li key={par.idSrecanje}>
              <Link to={`/srecanja/${par.idSrecanje}`} className="koledar-vnos__par">
                <span className="koledar-vnos__ekipa">{par.domaci}</span>
                <span className="koledar-vnos__proti">–</span>
                <span className="koledar-vnos__ekipa">{par.gost}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* Mono podnapis vrstice: pri ligi kolo in termin z uro, pri turnirju obdobje
   in prizorišče. Prazni deli odpadejo — pika brez vrednosti je šum. */
export function opisVnosa(vnos: KoledarVnosDto): string {
  const deli: string[] = []
  if (vnos.vrsta === 'LIGA') {
    if (vnos.kolo !== null) deli.push(`${vnos.kolo}. kolo`)
    const termin = oblikujTermin(vnos.zacetek)
    if (termin) deli.push(termin)
    if (vnos.srecanja.length > 0) {
      deli.push(`${vnos.srecanja.length} ${sklonSrecanj(vnos.srecanja.length)}`)
    }
    if (vnos.sezona) deli.push(vnos.sezona)
  } else {
    const obdobje = oblikujObdobjeKratko(vnos.datum, vnos.datumKonca)
    if (obdobje) deli.push(obdobje)
    if (vnos.kraj) deli.push(vnos.kraj)
    if (vnos.dvorana && vnos.dvorana !== vnos.kraj) deli.push(vnos.dvorana)
  }
  return deli.join(' · ')
}

/* »1 srečanje, 2 srečanji, 3 srečanja, 5 srečanj«. */
export function sklonSrecanj(n: number): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'srečanj'
  const mod10 = n % 10
  if (mod10 === 1) return 'srečanje'
  if (mod10 === 2) return 'srečanji'
  if (mod10 === 3 || mod10 === 4) return 'srečanja'
  return 'srečanj'
}
