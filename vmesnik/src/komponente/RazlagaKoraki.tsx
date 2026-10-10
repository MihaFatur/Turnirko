/* Deset korakov javne razlage ratinga (/o-ratingu): zgodba, v kateri vsak
   korak gradi na prejšnjem in ima svoj preizkus. Okvir strani (glava, kazalo,
   trak napredka, opazovalec korakov) je v strani sami; tu so samo koraki.

   Predloga: design_handoff_razlaga_ratinga (smer »1a Zapisnik«, korak 01 in 10
   iz smeri »1c Instrument«). Prototip izračune dela v brskalniku, ker nima
   strežnika; tu jih dela strežnik (`razlagaRatingaApi`), vmesnik le prikazuje:
   - korak 01: krivulja iz `pravila.napovedi` (−600…+600 po 10, decimalno),
   - koraki 02–04: ista zmaga pri razliki iz krivulje (rating 1000 + razlika
     proti 1000), s K in težo iz korakov 03 in 04,
   - korak 05: preizkus tekme, korak 06: štirje izračuni (K 48/58/68/78) in
     iskanje številke iz zapisnika med njimi,
   - korak 07: prvi dan novinca (`prviDan`), korak 08–09: pravila iz `pravila`.
   Ob vlečenju se klici zakasnijo (~120 ms), do odgovora ostane prejšnji
   (`placeholderData`), da številke ne utripajo.

   Animacije ob prvem prikazu (stolpci krivulje, stolpci K, palice teže,
   maskota) se sprožijo, ko sekcija prvič pride v okno: `videni` pove stran. */
import {
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from 'react'
import { Link } from 'react-router-dom'
import { useQueries, useQuery } from '@tanstack/react-query'

import { razlagaRatingaApi } from '../api/zahteve'
import type { PravilaRatingaDto, RavenTekmovanja } from '../api/tipi'
import {
  KRATKO_RAVEN,
  KRIVULJA,
  LESTVICE,
  NAJMANJ,
  NAJVEC,
  NAJVEC_MESECEV,
  NAJVEC_TEKEM_DNE,
  OZNAKE_OSI_X,
  PRIMERI_RAVNI,
  RAVNI,
  decimalno,
  grafDneva,
  jeVpisSpremembe,
  kNajvec,
  kZa,
  kategorijeIgralca,
  krivX,
  krivY,
  mejniki,
  napovedi,
  odbitekZa,
  odlocitevLestvice,
  odstotekTeze,
  potKrivulje,
  preberiSpremembo,
  sPredznakom,
  sidroZa,
  stolpciKrivulje,
  veljavenRating,
  vprasanja,
  type TekmeNaLestvici,
} from '../pomozno/razlagaRatinga'
import { useTelefon } from '../pomozno/telefon'
import { useZakasnjeno } from '../pomozno/useZakasnjeno'
import { LikSTablo } from './MaskotaPrizori'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { StevilskoPolje } from './StevilskoPolje'

/* Rating igralca iz primera v korakih 06 in 08 (izmišljen igralec). */
const PRIMER_RATING = 1342
const PRIMER_NASPROTNIK = 1484

/* Izidi v nizih iz koraka 02: na spremembo ne vplivajo, zato je izbira le
   dokaz — številka pod njo se ne premakne. */
const IZIDI_NIZOV = ['3 : 0', '3 : 1', '3 : 2', 'Predaja']

interface Moznost<T> {
  vrednost: T
  napis: string
}

function Izbirnik<T extends string | boolean>({
  moznosti,
  izbrana,
  naIzbiro,
  razred = '',
}: {
  moznosti: Moznost<T>[]
  izbrana: T
  naIzbiro: (v: T) => void
  razred?: string
}) {
  return (
    <div className="izbirnik razlaga__izbirnik">
      {moznosti.map((m) => (
        <button
          key={String(m.vrednost)}
          type="button"
          className={
            'izbirnik__gumb' + (razred ? ` ${razred}` : '') + (izbrana === m.vrednost ? ' izbirnik__gumb--aktiven' : '')
          }
          aria-pressed={izbrana === m.vrednost}
          onClick={() => naIzbiro(m.vrednost)}
        >
          {m.napis}
        </button>
      ))}
    </div>
  )
}

const MOZNOSTI_RAVNI: Moznost<RavenTekmovanja>[] = RAVNI.map((r) => ({
  vrednost: r,
  napis: KRATKO_RAVEN[r],
}))

function Korak({ st, naslov, children }: { st: number; naslov: string; children: ReactNode }) {
  const id = `k${st}`
  return (
    <section id={id} data-korak={st} className="razlaga__korak" aria-labelledby={`${id}-naslov`}>
      <div className="naslovna-vrstica razlaga__glava-koraka">
        <h2 id={`${id}-naslov`} className="razlaga__naslov-koraka">
          {naslov}
        </h2>
        <span className="sekcija__meta">Korak {String(st).padStart(2, '0')}</span>
      </div>
      {children}
    </section>
  )
}

export function RazlagaKoraki({ p, videni }: { p: PravilaRatingaDto; videni: ReadonlySet<number> }) {
  const [razlika, nastaviRazliko] = useState(200)
  const [nizi, nastaviNize] = useState(IZIDI_NIZOV[1])
  const [kN, nastaviKN] = useState(4)
  const [kVrn, nastaviKVrn] = useState(false)

  const vid = (k: number) => videni.has(k)
  const krivulja = napovedi(p)
  const verjetnost = krivulja.verjetnost(razlika)

  /* Ista zmaga pri razliki iz krivulje, ki jo računa strežnik: ustaljen
     igralec proti ustaljenemu (korak 02) in igralec s K iz koraka 03 (koraka
     03 in 04). */
  const zakasnjenaRazlika = useZakasnjeno(razlika)
  const zakasnjenKN = useZakasnjeno(kN)
  const primer02 = useQuery({
    queryKey: [
      'rating-izracun',
      {
        rating: 1000 + zakasnjenaRazlika,
        nasprotnik: 1000,
        tekem: p.pragUstaljen,
        tekemNasprotnika: p.pragUstaljen,
        vrnitev: false,
        vrnitevNasprotnika: false,
      },
    ],
    queryFn: () =>
      razlagaRatingaApi.izracun({
        rating: 1000 + zakasnjenaRazlika,
        nasprotnik: 1000,
        tekem: p.pragUstaljen,
        tekemNasprotnika: p.pragUstaljen,
        vrnitev: false,
        vrnitevNasprotnika: false,
      }),
    placeholderData: (prej) => prej,
  })
  const primerK = useQuery({
    queryKey: [
      'rating-izracun',
      {
        rating: 1000 + zakasnjenaRazlika,
        nasprotnik: 1000,
        tekem: zakasnjenKN,
        tekemNasprotnika: p.pragUstaljen,
        vrnitev: kVrn,
        vrnitevNasprotnika: false,
      },
    ],
    queryFn: () =>
      razlagaRatingaApi.izracun({
        rating: 1000 + zakasnjenaRazlika,
        nasprotnik: 1000,
        tekem: zakasnjenKN,
        tekemNasprotnika: p.pragUstaljen,
        vrnitev: kVrn,
        vrnitevNasprotnika: false,
      }),
    placeholderData: (prej) => prej,
  })

  const uradno02 = primer02.data?.ravni.find((r) => r.raven === 'URADNO')
  const zmaga02 = uradno02 ? sPredznakom(uradno02.zmaga.sprememba) : '—'
  const poraz02 = uradno02 ? sPredznakom(uradno02.poraz.sprememba) : '—'
  const nasprotnik02 = uradno02 ? sPredznakom(uradno02.zmaga.spremembaNasprotnika) : '—'

  return (
    <>
      <Korak1 p={p} razlika={razlika} naRazliko={nastaviRazliko} vidno={vid(1)} />
      <Korak2
        p={p}
        razlika={razlika}
        verjetnost={verjetnost}
        zmaga={zmaga02}
        poraz={poraz02}
        nasprotnik={nasprotnik02}
        nizi={nizi}
        naNize={nastaviNize}
        vidno={vid(2)}
      />
      <Korak3
        p={p}
        kN={kN}
        naKN={nastaviKN}
        kVrn={kVrn}
        naKVrn={nastaviKVrn}
        primer={primerK.data?.ravni.find((r) => r.raven === 'URADNO')?.zmaga.sprememba}
        vidno={vid(3)}
      />
      <Korak4 p={p} primerK={primerK.data} vidno={vid(4)} />
      <Korak5 p={p} />
      <Korak6 p={p} />
      <Korak7 p={p} vidno={vid(7)} />
      <Korak8 p={p} />
      <Korak9 p={p} />
      <Korak10 p={p} vidno={vid(10)} />
    </>
  )
}

/* ---------- 01 · Napoved pred tekmo ---------- */

function Korak1({
  p,
  razlika,
  naRazliko,
  vidno,
}: {
  p: PravilaRatingaDto
  razlika: number
  naRazliko: (r: number) => void
  vidno: boolean
}) {
  const jeTelefon = useTelefon()
  const n = napovedi(p)
  const verjetnost = n.verjetnost(razlika)
  const odstotek = Math.round(verjetnost * 100)
  const vlecem = useRef(false)

  const izKazalca = (e: PointerEvent<SVGSVGElement>) => {
    const okvir = e.currentTarget.getBoundingClientRect()
    const x = ((e.clientX - okvir.left) / okvir.width) * KRIVULJA.sirina
    const r = Math.round((x - KRIVULJA.najvecRazlika) / KRIVULJA.korak) * KRIVULJA.korak
    naRazliko(Math.max(-KRIVULJA.najvecRazlika, Math.min(KRIVULJA.najvecRazlika, r)))
  }
  const obTipki = (e: KeyboardEvent<SVGSVGElement>) => {
    const d = e.key === 'ArrowRight' ? KRIVULJA.korak : e.key === 'ArrowLeft' ? -KRIVULJA.korak : 0
    if (!d) return
    e.preventDefault()
    naRazliko(Math.max(-KRIVULJA.najvecRazlika, Math.min(KRIVULJA.najvecRazlika, razlika + d)))
  }

  const tx = krivX(razlika)
  const ty = krivY(verjetnost)
  const razlikaNapis = razlika === 0 ? '0' : sPredznakom(razlika)
  const p200 = Math.round(n.verjetnost(200) * 100)
  const p400 = Math.round(n.verjetnost(400) * 100)
  /* Na telefonu samo −400, 0, +400: sedem oznak v 350 px se prekrije. */
  const oznake = OZNAKE_OSI_X.filter((_, i) => !jeTelefon || i % 2 === 1 || i === 3)

  return (
    <Korak st={1} naslov="Napoved pred tekmo">
      <p className="razlaga__odstavek">
        Preden se tekma začne, razlika ratingov napove, kolikšna je možnost zmage. Pri enakem
        ratingu je 50 : 50; vsakih nekaj sto točk razlike napoved nagne k boljšemu. Povleci po
        krivulji.
      </p>

      <div className="razlaga__odcitek">
        <div>
          <div className="razlaga__odstotek">
            {odstotek}
            <span className="razlaga__odstotek-znak"> %</span>
          </div>
          <div className="razlaga__oznaka-pod">Tvoja možnost</div>
        </div>
        <div className="razlaga__razlika">
          <div className="razlaga__razlika-vrednost">{razlikaNapis}</div>
          <div className="razlaga__oznaka-pod">Točk razlike</div>
        </div>
      </div>

      <div className="razlaga__graf">
        <svg
          className="razlaga__krivulja"
          viewBox={`0 0 ${KRIVULJA.sirina} ${KRIVULJA.visina}`}
          tabIndex={0}
          role="slider"
          aria-label="Razlika ratingov"
          aria-valuemin={-KRIVULJA.najvecRazlika}
          aria-valuemax={KRIVULJA.najvecRazlika}
          aria-valuenow={razlika}
          aria-valuetext={`${razlikaNapis} točk, možnost zmage ${odstotek} %`}
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture?.(e.pointerId)
            vlecem.current = true
            izKazalca(e)
          }}
          onPointerMove={(e) => {
            if (vlecem.current) izKazalca(e)
          }}
          onPointerUp={() => {
            vlecem.current = false
          }}
          onPointerCancel={() => {
            vlecem.current = false
          }}
          onKeyDown={obTipki}
        >
          <rect x="0" y="0" width={KRIVULJA.sirina} height={KRIVULJA.visina} fill="var(--barva-papir)" />
          {[
            [0, 'var(--barva-polnilo)'],
            [0.5, 'var(--barva-crta)'],
            [1, 'var(--barva-crnilo)'],
          ].map(([v, barva]) => (
            <line
              key={String(v)}
              x1="0"
              x2={KRIVULJA.sirina}
              y1={krivY(Number(v))}
              y2={krivY(Number(v))}
              stroke={String(barva)}
              strokeWidth="1"
            />
          ))}
          {stolpciKrivulje(n, razlika).map((s) => (
            <rect
              key={s.razlika}
              className={
                'razlaga__stolpec razlaga__stolpec--' + s.barva + (vidno ? ' razlaga__stolpec--viden' : '')
              }
              x={s.x}
              y={s.y}
              width="14"
              height={s.visina}
            />
          ))}
          <path d={potKrivulje(n)} fill="none" stroke="var(--barva-crnilo)" strokeWidth="4" />
          <line x1={tx} x2={tx} y1="0" y2="360" stroke="var(--barva-glavna)" strokeWidth="2" />
          <rect
            x={tx - 12}
            y={ty - 12}
            width="24"
            height="24"
            fill="var(--barva-glavna-polna)"
            stroke="var(--barva-papir)"
            strokeWidth="3"
          />
        </svg>
        {oznake.map((d) => (
          <span
            key={d}
            className="razlaga__os-oznaka"
            style={{ left: `${(krivX(d) / KRIVULJA.sirina) * 100}%` }}
          >
            {d === 0 ? '0' : sPredznakom(d)}
          </span>
        ))}
      </div>

      <div className="razlaga__smeri">
        <span>← nasprotnik močnejši</span>
        <span>ti močnejši →</span>
      </div>
      <p className="razlaga__odstavek razlaga__odstavek--r3">
        Krivulja je ista za vse tekme. Pri enakem ratingu je 50 : 50, pri 200 točkah razlike boljši
        zmaga v {p200} % tekem, pri 400 v {p400} %. Stolpci pod njo so napovedi za vsakih 20 točk
        razlike.
      </p>
    </Korak>
  )
}

/* ---------- 02 · Sprememba po tekmi ---------- */

function Korak2({
  p,
  razlika,
  verjetnost,
  zmaga,
  poraz,
  nasprotnik,
  nizi,
  naNize,
  vidno,
}: {
  p: PravilaRatingaDto
  razlika: number
  verjetnost: number
  zmaga: string
  poraz: string
  nasprotnik: string
  nizi: string
  naNize: (n: string) => void
  vidno: boolean
}) {
  const teza = p.ravni.find((r) => r.raven === 'URADNO')?.teza ?? 1
  const pDec = decimalno(verjetnost)
  const clenovi = [
    { oznaka: 'K', vrednost: String(p.kOsnovni), opis: 'hitrost · korak 03', poudarek: false, barva: '' },
    { oznaka: 'Teža', vrednost: odstotekTeze(teza), opis: 'tekmovanje · korak 04', poudarek: false, barva: '' },
    { oznaka: 'Izid', vrednost: '1 / 0', opis: 'zmaga ali poraz', poudarek: false, barva: '' },
    {
      oznaka: 'Pričakovano',
      vrednost: pDec,
      opis: 'iz krivulje · korak 01',
      poudarek: true,
      barva: '',
    },
    { oznaka: 'Sprememba', vrednost: zmaga, opis: 'ob zmagi', poudarek: false, barva: 'poz' },
  ]
  return (
    <Korak st={2} naslov="Sprememba po tekmi">
      <p className="razlaga__odstavek">
        Po tekmi se rating premakne za razliko med <strong>doseženim</strong> in{' '}
        <strong>pričakovanim</strong>. Pričakovano je številka iz koraka 01 — {pDec} za razliko{' '}
        {razlika === 0 ? '0' : sPredznakom(razlika)}.
      </p>

      <div className="razlaga__clenovi">
        {clenovi.map((c) => (
          <div
            key={c.oznaka}
            className={'razlaga__clen' + (c.poudarek ? ' razlaga__clen--poudarek' : '')}
          >
            <div className="razlaga__clen-oznaka">{c.oznaka}</div>
            <div
              className={
                'razlaga__clen-vrednost' + (c.barva === 'poz' ? ' razlaga__poz' : '')
              }
            >
              {c.vrednost}
            </div>
            <div className="razlaga__clen-opis">{c.opis}</div>
          </div>
        ))}
      </div>

      <div className="razlaga__obrazec">
        Zmaga: {p.kOsnovni} × {odstotekTeze(teza)} × (1 − {pDec}) ={' '}
        <span className="razlaga__poz">{zmaga}</span>
        <br />
        Poraz: {p.kOsnovni} × {odstotekTeze(teza)} × (0 − {pDec}) ={' '}
        <span className="razlaga__neg">{poraz}</span>
      </div>

      <div className="razlaga__dvojno razlaga__dvojno--konec">
        <div>
          <h3 className="razlaga__podnaslov">Zmaga je zmaga</h3>
          <p className="razlaga__odstavek razlaga__odstavek--r1">
            Šteje, kdo je zmagal, ne kako. Izberi izid — sprememba ostane ista.
          </p>
          <div className="razlaga__izbirnik-vrsta">
            <Izbirnik
              moznosti={IZIDI_NIZOV.map((i) => ({ vrednost: i, napis: i }))}
              izbrana={nizi}
              naIzbiro={naNize}
            />
          </div>
          <p className="razlaga__namig">
            Izid {nizi} → <strong className="razlaga__poz">{zmaga}</strong>. Rezultat se prepisuje s
            papirja, tekme se končajo tudi s predajo, pri starih tekmah je zapisan samo zmagovalec.
            Merjenje na pravih tekmah je pokazalo, da izid v nizih napovedi ne izboljša.
          </p>
        </div>
        <div className="razlaga__maskota razlaga__maskota--levo">
          <LikSTablo napis="Zmaga je zmaga." merilo={1.5} vidno={vidno} />
        </div>
      </div>

      <p className="razlaga__odstavek razlaga__odstavek--r4">
        Kar eden dobi, drugi izgubi: ob tvoji zmagi nasprotnik <strong>{nasprotnik}</strong>, vsota je{' '}
        <strong>0</strong>. To drži, dokler imata enak K — korak 03.
      </p>
    </Korak>
  )
}

/* ---------- 03 · K ---------- */

function Korak3({
  p,
  kN,
  naKN,
  kVrn,
  naKVrn,
  primer,
  vidno,
}: {
  p: PravilaRatingaDto
  kN: number
  naKN: (n: number) => void
  kVrn: boolean
  naKVrn: (v: boolean) => void
  primer: number | undefined
  vidno: boolean
}) {
  const k = kZa(p, kN, kVrn)
  const najvec = kNajvec(p)
  const razclenitev = [
    { opis: 'Osnova', vrednost: `${p.kOsnovni}`, aktivna: true },
    { opis: `Manj kot ${p.pragUstaljen} tekem`, vrednost: `+${p.pribitekNeustaljen}`, aktivna: kN < p.pragUstaljen },
    { opis: `Manj kot ${p.pragNovinec} tekem`, vrednost: `+${p.pribitekNovinec}`, aktivna: kN < p.pragNovinec },
    { opis: 'Vrnitev po odsotnosti', vrednost: `+${p.pribitekVrnitev}`, aktivna: kVrn },
  ]
  return (
    <Korak st={3} naslov="K — kako hitro se premika">
      <p className="razlaga__odstavek">
        O novem igralcu vemo malo, zato se njegova številka premika hitreje in prej najde pravo
        raven. K je osnova {p.kOsnovni}; pribitki veljajo, dokler o tebi ne vemo dovolj.
      </p>
      <div className="razlaga__dvojno razlaga__dvojno--siroko">
        <div>
          <label className="razlaga__polje-stolpec">
            <span className="razlaga__oznaka">
              Odigranih tekem: <strong>{kN}</strong>
            </span>
            <input
              type="range"
              className="razlaga__drsnik"
              min={0}
              max={60}
              value={kN}
              onChange={(e) => naKN(Number(e.target.value))}
            />
          </label>
          <label className="razlaga__kljukica">
            <input type="checkbox" checked={kVrn} onChange={(e) => naKVrn(e.target.checked)} />
            <span>
              Vrnitev po več kot {p.mesecevZaVrnitev} mesecih (prvih {p.tekemPoVrnitvi} tekem)
            </span>
          </label>
          <div className="razlaga__k-stolpci">
            {Array.from({ length: 61 }, (_, n) => (
              <span
                key={n}
                className={
                  'razlaga__k-stolpec' +
                  (n === kN ? ' razlaga__k-stolpec--trenutni' : n < kN ? ' razlaga__k-stolpec--odigran' : '')
                }
                style={{ height: vidno ? `${(kZa(p, n, kVrn) / najvec) * 100}%` : '0%' }}
              />
            ))}
          </div>
          <div className="razlaga__k-oznake">
            <span>0</span>
            <span>{p.pragNovinec}</span>
            <span>{p.pragUstaljen}</span>
            <span>60 tekem</span>
          </div>
        </div>
        <div>
          <div className="razlaga__k-vrednost">
            <span className="razlaga__k-stevilka">{k}</span>
            <span className="razlaga__oznaka">Tvoj K</span>
          </div>
          {razclenitev.map((r) => (
            <div
              key={r.opis}
              className={'razlaga__razclen' + (r.aktivna ? '' : ' razlaga__razclen--mirna')}
            >
              <span>{r.opis}</span>
              <span>{r.aktivna ? r.vrednost : '—'}</span>
            </div>
          ))}
          <p className="razlaga__namig">
            Ista zmaga kot v koraku 01 ti s tem K prinese{' '}
            <strong className="razlaga__poz">{primer === undefined ? '—' : sPredznakom(primer)}</strong>.
          </p>
        </div>
      </div>
    </Korak>
  )
}

/* ---------- 04 · Teža tekmovanja ---------- */

function Korak4({
  p,
  primerK,
  vidno,
}: {
  p: PravilaRatingaDto
  primerK: { ravni: { raven: RavenTekmovanja; zmaga: { sprememba: number } }[] } | undefined
  vidno: boolean
}) {
  return (
    <Korak st={4} naslov="Teža tekmovanja">
      <p className="razlaga__odstavek">
        Zmaga na državnem prvenstvu pove več kot zmaga na rekreativnem turnirju. Tekma je tekma — v
        število odigranih šteje enako —, rating pa premakne manj. Ista zmaga, tri tekmovanja:
      </p>
      <div className="razlaga__teze">
        {p.ravni.map((r) => {
          const primer = primerK?.ravni.find((x) => x.raven === r.raven)?.zmaga.sprememba
          return (
            <div key={r.raven} className="razlaga__teza">
              <div>
                <div className="razlaga__teza-ime">{KRATKO_RAVEN[r.raven]}</div>
                <div className="razlaga__teza-opis">{PRIMERI_RAVNI[r.raven]}</div>
              </div>
              <span className="razlaga__palica">
                <span
                  className="razlaga__palica-polnilo"
                  style={{ width: vidno ? `${r.teza * 100}%` : '0%' }}
                />
              </span>
              <span className="razlaga__teza-odstotek">{odstotekTeze(r.teza)}</span>
              <span className="razlaga__teza-primer">
                {primer === undefined ? '—' : sPredznakom(primer)}
              </span>
            </div>
          )
        })}
      </div>
    </Korak>
  )
}

/* ---------- 05 · Preizkusi tekmo ---------- */

type Izkusnja = 'NOVINEC' | 'NEUSTALJEN' | 'USTALJEN'

function IzbiraIzkusnje({
  p,
  izbrana,
  naIzbiro,
}: {
  p: PravilaRatingaDto
  izbrana: Izkusnja
  naIzbiro: (v: Izkusnja) => void
}) {
  return (
    <Izbirnik<Izkusnja>
      moznosti={[
        { vrednost: 'NOVINEC', napis: `Pod ${p.pragNovinec}` },
        { vrednost: 'NEUSTALJEN', napis: `${p.pragNovinec}–${p.pragUstaljen - 1}` },
        { vrednost: 'USTALJEN', napis: `${p.pragUstaljen}+` },
      ]}
      izbrana={izbrana}
      naIzbiro={naIzbiro}
    />
  )
}

function Korak5({ p }: { p: PravilaRatingaDto }) {
  const [rating, nastaviRating] = useState('1200')
  const [nasprotnik, nastaviNasprotnika] = useState('1350')
  const [izkusnja, nastaviIzkusnjo] = useState<Izkusnja>('USTALJEN')
  const [izkusnjaNasprotnika, nastaviIzkusnjoNasprotnika] = useState<Izkusnja>('USTALJEN')
  const [vrnitev, nastaviVrnitev] = useState(false)
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('URADNO')

  const tekem = (i: Izkusnja) => (i === 'NOVINEC' ? 0 : i === 'NEUSTALJEN' ? p.pragNovinec : p.pragUstaljen)
  const veljavno = veljavenRating(rating) && veljavenRating(nasprotnik)
  const zakasnjenRating = useZakasnjeno(rating)
  const zakasnjenNasprotnik = useZakasnjeno(nasprotnik)
  const vnos = {
    rating: Number(zakasnjenRating),
    nasprotnik: Number(zakasnjenNasprotnik),
    tekem: tekem(izkusnja),
    tekemNasprotnika: tekem(izkusnjaNasprotnika),
    vrnitev,
    vrnitevNasprotnika: false,
  }
  const izracun = useQuery({
    queryKey: ['rating-izracun', vnos],
    queryFn: () => razlagaRatingaApi.izracun(vnos),
    enabled: veljavenRating(zakasnjenRating) && veljavenRating(zakasnjenNasprotnik),
    placeholderData: (prej) => prej,
  })
  const d = izracun.data
  const r = d?.ravni.find((x) => x.raven === raven)

  return (
    <Korak st={5} naslov="Preizkusi tekmo">
      <p className="razlaga__odstavek">
        Vse štiri sestavine skupaj. Vpiši oba ratinga in poglej, kaj prinese zmaga in kaj vzame
        poraz.
      </p>
      <div className="razlaga__stranici">
        <div className="razlaga__stran">
          <span className="razlaga__oznaka razlaga__oznaka--glavna">Ti</span>
          <label className="razlaga__polje-stolpec razlaga__polje-stolpec--r12">
            <span className="razlaga__oznaka">Rating</span>
            <StevilskoPolje
              className="razlaga__vnos razlaga__vnos--velik"
              vrednost={rating}
              naSpremembo={nastaviRating}
              najvec={NAJVEC}
            />
          </label>
          <span className="razlaga__oznaka razlaga__oznaka--r16">Odigranih tekem</span>
          <IzbiraIzkusnje p={p} izbrana={izkusnja} naIzbiro={nastaviIzkusnjo} />
          <label className="razlaga__kljukica razlaga__kljukica--r8">
            <input type="checkbox" checked={vrnitev} onChange={(e) => nastaviVrnitev(e.target.checked)} />
            <span>Vračam se po več kot {p.mesecevZaVrnitev} mesecih</span>
          </label>
        </div>
        <div className="razlaga__stran">
          <span className="razlaga__oznaka">Nasprotnik</span>
          <label className="razlaga__polje-stolpec razlaga__polje-stolpec--r12">
            <span className="razlaga__oznaka">Rating</span>
            <StevilskoPolje
              className="razlaga__vnos razlaga__vnos--velik"
              vrednost={nasprotnik}
              naSpremembo={nastaviNasprotnika}
              najvec={NAJVEC}
            />
          </label>
          <span className="razlaga__oznaka razlaga__oznaka--r16">Odigranih tekem</span>
          <IzbiraIzkusnje p={p} izbrana={izkusnjaNasprotnika} naIzbiro={nastaviIzkusnjoNasprotnika} />
        </div>
      </div>
      <span className="razlaga__oznaka razlaga__oznaka--r24">Tekmovanje</span>
      <Izbirnik moznosti={MOZNOSTI_RAVNI} izbrana={raven} naIzbiro={nastaviRaven} />

      {!veljavno && (
        <p className="razlaga__napaka">
          Rating mora biti med {NAJMANJ} in {NAJVEC}.
        </p>
      )}
      {veljavno && <NapakaPoizvedbe poizvedba={izracun} kaj="izračuna" />}
      {veljavno && d && r && (
        <div className="razlaga__izid" aria-live="polite">
          <div className="razlaga__izid-vrstica">
            <span>Tvoja možnost {d.pricakovanOdstotek} %</span>
            <span>{100 - d.pricakovanOdstotek} % nasprotnik</span>
          </div>
          <div className="razlaga__razmerje">
            <span className="razlaga__razmerje-ti" style={{ width: `${d.pricakovano * 100}%` }} />
            <span className="razlaga__razmerje-on" />
          </div>
          <div className="razlaga__kazalniki">
            <div className="razlaga__kazalnik">
              <div className="razlaga__kazalnik-vrednost razlaga__poz">{sPredznakom(r.zmaga.sprememba)}</div>
              <div className="razlaga__kazalnik-oznaka">Če zmagaš → {r.zmaga.rating}</div>
            </div>
            <div className="razlaga__kazalnik razlaga__kazalnik--drugi">
              <div className="razlaga__kazalnik-vrednost razlaga__neg">{sPredznakom(r.poraz.sprememba)}</div>
              <div className="razlaga__kazalnik-oznaka">Če izgubiš → {r.poraz.rating}</div>
            </div>
          </div>
          <p className="razlaga__racun">
            Zmaga: K {d.k} × {odstotekTeze(r.teza)} × (1 − {decimalno(d.pricakovano)}) ≈{' '}
            {sPredznakom(r.zmaga.sprememba)}
            <br />
            Poraz: K {d.k} × {odstotekTeze(r.teza)} × (0 − {decimalno(d.pricakovano)}) ≈{' '}
            {sPredznakom(r.poraz.sprememba)}
          </p>
          <p className="razlaga__namig">
            Nasprotnik ob tvoji zmagi {sPredznakom(r.zmaga.spremembaNasprotnika)}, ob tvojem porazu{' '}
            {sPredznakom(r.poraz.spremembaNasprotnika)}
            {d.k !== d.kNasprotnika
              ? ` — K nasprotnika je ${d.kNasprotnika}, zato se premakne drugače kot ti`
              : ' — vsota obeh sprememb je 0'}
            .
          </p>
        </div>
      )}
    </Korak>
  )
}

/* ---------- 06 · Razloži mojo spremembo ---------- */

function Korak6({ p }: { p: PravilaRatingaDto }) {
  const [rating, nastaviRating] = useState(String(PRIMER_RATING))
  const [nasprotnik, nastaviNasprotnika] = useState(String(PRIMER_NASPROTNIK))
  const [zmaga, nastaviZmago] = useState(false)
  const [sprememba, nastaviSpremembo] = useState('−15')

  const zakasnjenRating = useZakasnjeno(rating)
  const zakasnjenNasprotnik = useZakasnjeno(nasprotnik)
  const spr = preberiSpremembo(sprememba)
  const veljavno = veljavenRating(rating) && veljavenRating(nasprotnik) && spr !== null

  /* K 48 / 58 / 68 / 78: ustaljen, neustaljen, novinec in novinec po vrnitvi. */
  const stopnje = [
    { tekem: p.pragUstaljen, vrnitev: false, opis: `Ustaljen (${p.pragUstaljen}+ tekem)` },
    {
      tekem: p.pragNovinec,
      vrnitev: false,
      opis: `Neustaljen (${p.pragNovinec}–${p.pragUstaljen - 1}) ali ustaljen po vrnitvi`,
    },
    { tekem: 0, vrnitev: false, opis: `Novinec (pod ${p.pragNovinec}) ali neustaljen po vrnitvi` },
    { tekem: 0, vrnitev: true, opis: 'Novinec po vrnitvi' },
  ]
  const poizvedbe = useQueries({
    queries: stopnje.map((s) => {
      const vnos = {
        rating: Number(zakasnjenRating),
        nasprotnik: Number(zakasnjenNasprotnik),
        tekem: s.tekem,
        tekemNasprotnika: p.pragUstaljen,
        vrnitev: s.vrnitev,
        vrnitevNasprotnika: false,
      }
      return {
        queryKey: ['rating-izracun', vnos],
        queryFn: () => razlagaRatingaApi.izracun(vnos),
        enabled: veljavenRating(zakasnjenRating) && veljavenRating(zakasnjenNasprotnik),
        placeholderData: (prej: Awaited<ReturnType<typeof razlagaRatingaApi.izracun>> | undefined) => prej,
      }
    }),
  })
  const odgovori = poizvedbe.map((q) => q.data)
  const imaVse = odgovori.every((o) => o !== undefined)
  const pp = odgovori[0]?.pricakovano ?? 0.5
  const izid = zmaga ? 1 : 0

  /* Mreža vseh možnosti: sprememba za vsak K in raven, za izbran izid. */
  const mreza = stopnje.map((s, i) => ({
    k: odgovori[i]?.k,
    opis: s.opis,
    celice: RAVNI.map((raven) => {
      const r = odgovori[i]?.ravni.find((x) => x.raven === raven)
      const vrednost = r ? (zmaga ? r.zmaga.sprememba : r.poraz.sprememba) : undefined
      return { raven, teza: r?.teza ?? 1, vrednost }
    }),
  }))
  const zadetki = veljavno && imaVse
    ? mreza.flatMap((v) =>
        v.celice
          .filter((c) => c.vrednost === spr)
          .map((c) => ({ k: v.k as number, opis: v.opis, raven: c.raven, teza: c.teza })),
      )
    : []

  let izid6: { naslov: string; besedilo: string; racun?: string; razred: 'ujema' | 'ne' | 'caka' }
  if (!veljavno) {
    izid6 = {
      naslov: 'Vpiši številke',
      besedilo: `Rating mora biti med 100 in ${NAJVEC}, sprememba pa celo število, npr. −15 ali +34.`,
      razred: 'caka',
    }
  } else if (!imaVse) {
    izid6 = { naslov: 'Računam …', besedilo: 'Strežnik izračuna vse možnosti za to tekmo.', razred: 'caka' }
  } else if (zadetki.length > 0) {
    const h = zadetki[0]
    const surovo = h.k * h.teza * (izid - pp)
    izid6 = {
      naslov:
        zadetki.length === 1
          ? `Ujema se: K ${h.k} · ${KRATKO_RAVEN[h.raven].toLowerCase()} ${odstotekTeze(h.teza)}`
          : `Ujema se z ${zadetki.length} možnostmi`,
      besedilo:
        zadetki.length === 1
          ? `${h.opis}. Možnost tvoje zmage je bila ${Math.round(pp * 100)} %.`
          : zadetki
              .map((z) => `K ${z.k} na ${KRATKO_RAVEN[z.raven].toLowerCase()}em tekmovanju (${z.opis.toLowerCase()})`)
              .join(' ali ')
            + '. Katera velja, pove število tvojih tekem in raven tekmovanja.',
      /* Surovi zmnožek je v zapisu z vezajem, kot ga je izpisala maketa. */
      racun: `${h.k} × ${decimalno(h.teza)} × (${izid} − ${decimalno(pp)}) = ${surovo
        .toFixed(2)
        .replace('.', ',')} → ${sPredznakom(spr as number)}`,
      razred: 'ujema',
    }
  } else {
    const napacenZnak = (zmaga && (spr as number) < 0) || (!zmaga && (spr as number) > 0)
    izid6 = {
      naslov: 'Ne ujema se z navadnim korakom',
      besedilo:
        `${napacenZnak ? 'Pri navadnem koraku zmaga nikoli ne vzame točk in poraz jih nikoli ne prinese. ' : ''}`
        + 'Najpogostejši razlog je prvi dan igranja (korak 07): takrat se rating po vsaki tekmi izračuna znova iz vseh izidov dneva. '
        + `Drugi razlogi: popravek rezultata za nazaj sproži preračun od dneva tekme, rating ne pade pod ${p.spodnjaMeja}, `
        + 'ali pa ima igralec zunanjo uvrstitev z navedenim virom.',
      razred: 'ne',
    }
  }

  return (
    <Korak st={6} naslov="Razloži mojo spremembo">
      <p className="razlaga__odstavek">
        Imaš številko iz zapisnika in ne veš, od kod je? Prepiši, kar piše ob tekmi. Primer: Luka
        Jereb (NTK Savinja, {PRIMER_RATING}) je izgubil s {PRIMER_NASPROTNIK} in ima ob tekmi −15.
      </p>
      <div className="razlaga__tri-polja">
        <label className="razlaga__polje-stolpec">
          <span className="razlaga__oznaka">Moj rating pred</span>
          <StevilskoPolje
            className="razlaga__vnos razlaga__vnos--srednji"
            vrednost={rating}
            naSpremembo={nastaviRating}
            najvec={NAJVEC}
          />
        </label>
        <label className="razlaga__polje-stolpec">
          <span className="razlaga__oznaka">Nasprotnik pred</span>
          <StevilskoPolje
            className="razlaga__vnos razlaga__vnos--srednji"
            vrednost={nasprotnik}
            naSpremembo={nastaviNasprotnika}
            najvec={NAJVEC}
          />
        </label>
        <label className="razlaga__polje-stolpec">
          <span className="razlaga__oznaka">Sprememba v zapisniku</span>
          <input
            type="text"
            inputMode="text"
            autoComplete="off"
            className="razlaga__vnos razlaga__vnos--srednji"
            value={sprememba}
            onChange={(e) => {
              if (jeVpisSpremembe(e.target.value)) nastaviSpremembo(e.target.value.trim())
            }}
          />
        </label>
      </div>
      <div className="razlaga__izbirnik-vrsta razlaga__izbirnik-vrsta--r16">
        <Izbirnik
          razred="izbirnik__gumb--siroki"
          moznosti={[
            { vrednost: true, napis: 'Zmaga' },
            { vrednost: false, napis: 'Poraz' },
          ]}
          izbrana={zmaga}
          naIzbiro={nastaviZmago}
        />
      </div>

      <div className={'razlaga__razlaga razlaga__razlaga--' + izid6.razred} aria-live="polite">
        <div className="razlaga__razlaga-naslov">{izid6.naslov}</div>
        <p className="razlaga__razlaga-besedilo">{izid6.besedilo}</p>
        {izid6.racun && <p className="razlaga__razlaga-racun">{izid6.racun}</p>}
      </div>

      <div className="razlaga__mreza-blok">
        <span className="razlaga__oznaka">Vse možnosti za to tekmo</span>
        <div className="razlaga__mreza">
          <span className="razlaga__mreza-glava">K</span>
          <span className="razlaga__mreza-glava razlaga__mreza-glava--desno">Uradno</span>
          <span className="razlaga__mreza-glava razlaga__mreza-glava--desno">Klubsko</span>
          <span className="razlaga__mreza-glava razlaga__mreza-glava--desno razlaga__mreza-glava--zadnja">
            Rekr.
          </span>
          {mreza.flatMap((v, i) => [
            <span key={`k${i}`} className="razlaga__mreza-k">
              K {v.k ?? '—'}
            </span>,
            ...v.celice.map((c) => {
              const zadetek = veljavno && c.vrednost !== undefined && c.vrednost === spr
              return (
                <span
                  key={`${i}-${c.raven}`}
                  className={'razlaga__mreza-celica' + (zadetek ? ' razlaga__mreza-celica--zadetek' : '')}
                >
                  {c.vrednost === undefined ? '—' : sPredznakom(c.vrednost)}
                </span>
              )
            }),
          ])}
        </div>
      </div>
    </Korak>
  )
}

/* ---------- 07 · Prvi dan novinca ---------- */

interface TekmaDne {
  nasprotnik: string
  zmaga: boolean
}

/* Primer, ki pokaže skok: rekreativec začne pri 800, dobi prvo tekmo, nato
   izgubi dve proti šibkejšima - druga zmaga številko spet dvigne. */
const PRIMER_DNE: TekmaDne[] = [
  { nasprotnik: '960', zmaga: true },
  { nasprotnik: '700', zmaga: false },
  { nasprotnik: '650', zmaga: false },
  { nasprotnik: '1000', zmaga: true },
]

type SpolSidra = 'M' | 'Z' | 'R'

function Korak7({ p, vidno }: { p: PravilaRatingaDto; vidno: boolean }) {
  const [spol, nastaviSpol] = useState<SpolSidra>('R')
  const [starost, nastaviStarost] = useState(16)
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('KLUBSKO')
  const [tekme, nastaviTekme] = useState<TekmaDne[]>(PRIMER_DNE)

  const izhodisce = spol === 'R' ? p.rekreativniZacetek : sidroZa(p, spol === 'M' ? 'MOSKI' : 'ZENSKI', starost)
  const vseVeljavne = tekme.length > 0 && tekme.every((t) => veljavenRating(t.nasprotnik))
  const zakasnjeneTekme = useZakasnjeno(tekme)
  const zakasnjenoIzhodisce = useZakasnjeno(izhodisce)
  const dan = useQuery({
    queryKey: ['rating-prvi-dan', zakasnjenoIzhodisce, raven, zakasnjeneTekme],
    queryFn: () =>
      razlagaRatingaApi.prviDan(
        zakasnjenoIzhodisce,
        raven,
        zakasnjeneTekme.map((t) => ({ nasprotnik: Number(t.nasprotnik), zmaga: t.zmaga })),
      ),
    enabled: zakasnjeneTekme.length > 0 && zakasnjeneTekme.every((t) => veljavenRating(t.nasprotnik)),
    placeholderData: (prej) => prej,
  })
  const koraki = vseVeljavne ? (dan.data?.koraki ?? []) : []
  const graf = grafDneva(izhodisce, koraki)

  const spremeni = (i: number, sprememba: Partial<TekmaDne>) =>
    nastaviTekme((prej) => prej.map((t, j) => (j === i ? { ...t, ...sprememba } : t)))

  return (
    <Korak st={7} naslov="Prvi dan novinca">
      <p className="razlaga__odstavek">
        Novinec ne začne pri 1000, ampak pri <strong>sredini igralcev svoje starosti in spola</strong>{' '}
        (starostno sidro). Dva novinca v U11 sta tako bližje drug drugemu kot novincu v U19.
      </p>
      <div className="razlaga__dvojno razlaga__dvojno--sidro">
        <div>
          <Izbirnik<SpolSidra>
            moznosti={[
              { vrednost: 'M', napis: 'Moški' },
              { vrednost: 'Z', napis: 'Ženska' },
              { vrednost: 'R', napis: 'Rekreativec' },
            ]}
            izbrana={spol}
            naIzbiro={nastaviSpol}
          />
          {spol !== 'R' && (
            <label className="razlaga__polje-stolpec razlaga__polje-stolpec--r16 razlaga__polje-stolpec--tesno">
              <span className="razlaga__oznaka">
                Starost v sezoni: <strong>{starost} let</strong>
              </span>
              <input
                type="range"
                className="razlaga__drsnik"
                min={8}
                max={45}
                value={starost}
                onChange={(e) => nastaviStarost(Number(e.target.value))}
              />
            </label>
          )}
        </div>
        <div className="razlaga__k-vrednost">
          <span className="razlaga__k-stevilka razlaga__k-stevilka--crna">{izhodisce}</span>
          <span className="razlaga__oznaka">
            {spol === 'R'
              ? 'Rekreativec ob vpisu'
              : `Sidro · ${spol === 'M' ? 'moški' : 'ženske'}, ${starost} let`}
          </span>
        </div>
      </div>

      <p className="razlaga__odstavek razlaga__odstavek--r4">
        Prvi dan igranja — prvi turnir ali prvi ligaški večer — se rating{' '}
        <strong>ne sešteva po tekmah</strong>. Po vsaki tekmi od druge naprej se izračuna znova iz{' '}
        <strong>vseh izidov tega dne</strong>: poiščemo številko, ki tvoje zmage in poraze najbolje
        razloži. Zraven štejeta še {p.navideznihTekem} navidezni neodločeni tekmi proti izhodišču, da
        ena srečna zmaga ne odnese previsoko.
      </p>

      <div className="razlaga__dvojno razlaga__dvojno--dan">
        <div>
          <span className="razlaga__oznaka">Izhodišče {izhodisce} · tekmovanje</span>
          <Izbirnik moznosti={MOZNOSTI_RAVNI} izbrana={raven} naIzbiro={nastaviRaven} />
        </div>
        <div className="razlaga__maskota razlaga__maskota--desno">
          <LikSTablo napis="Prvi dan skače." merilo={1.5} vidno={vidno} />
        </div>
      </div>

      <div className="razlaga__graf-dne">
        <svg viewBox="0 0 720 260" className="razlaga__graf-dne-slika">
          {graf.mrezaY.map((y) => (
            <line key={y} x1="40" x2="700" y1={y} y2={y} stroke="var(--barva-polnilo)" />
          ))}
          <polyline
            points={graf.potSestevka}
            fill="none"
            stroke="var(--barva-crnilo-4)"
            strokeWidth="1.5"
            strokeDasharray="5 5"
          />
          <polyline points={graf.potUvrstitve} fill="none" stroke="var(--barva-glavna)" strokeWidth="3" />
          {graf.tocke.map((t, i) => (
            <rect
              key={i}
              x={t.x - 5}
              y={t.y - 5}
              width="10"
              height="10"
              fill={
                t.naprej === 'izhodisce'
                  ? 'var(--barva-crnilo)'
                  : t.naprej === 'porast'
                    ? 'var(--barva-poudarek-tekst)'
                    : 'var(--barva-negativna)'
              }
              stroke="var(--barva-bela)"
              strokeWidth="1.5"
            />
          ))}
        </svg>
        {graf.tocke.map((t, i) => (
          <span
            key={i}
            className="razlaga__graf-dne-oznaka"
            style={{ left: `${(t.x / 720) * 100}%`, top: `${(t.y / 260) * 100}%` }}
          >
            {t.vrednost}
          </span>
        ))}
      </div>
      <div className="razlaga__kljuc">
        <span>
          <span className="razlaga__kljuc-polna" />
          Prvi dan (uvrstitev)
        </span>
        <span>
          <span className="razlaga__kljuc-crtkana" />
          Če bi se seštevalo po tekmah
        </span>
      </div>

      <ol className="razlaga__tekme">
        {tekme.map((t, i) => {
          const korak = vseVeljavne ? dan.data?.koraki[i] : undefined
          return (
            <li key={i} className="razlaga__tekma">
              <span className="razlaga__tekma-st">{i + 1}.</span>
              <StevilskoPolje
                className="razlaga__vnos razlaga__vnos--vrstica"
                aria-label="Rating nasprotnika"
                vrednost={t.nasprotnik}
                naSpremembo={(v) => spremeni(i, { nasprotnik: v })}
                najvec={NAJVEC}
              />
              <div className="razlaga__izid-tekme">
                <button
                  type="button"
                  className={'izbirnik__gumb izbirnik__gumb--ozki' + (t.zmaga ? ' izbirnik__gumb--aktiven' : '')}
                  aria-pressed={t.zmaga}
                  aria-label="Zmaga"
                  onClick={() => spremeni(i, { zmaga: true })}
                >
                  Z
                </button>
                <button
                  type="button"
                  className={'izbirnik__gumb izbirnik__gumb--ozki' + (!t.zmaga ? ' izbirnik__gumb--aktiven' : '')}
                  aria-pressed={!t.zmaga}
                  aria-label="Poraz"
                  onClick={() => spremeni(i, { zmaga: false })}
                >
                  P
                </button>
              </div>
              <span className="razlaga__tekma-rezultat">
                <span className={korak && korak.sprememba >= 0 ? 'razlaga__poz' : 'razlaga__neg'}>
                  {korak ? sPredznakom(korak.sprememba) : '—'}
                </span>{' '}
                → <strong>{korak ? korak.rating : ''}</strong>
                <span className="razlaga__tekma-nacin">
                  {korak ? (korak.uvrstitev ? 'iz vseh izidov' : 'navaden korak') : ''}
                </span>
              </span>
              <button
                type="button"
                className="razlaga__odstrani"
                aria-label="Odstrani tekmo"
                disabled={tekme.length <= 1}
                onClick={() => nastaviTekme((prej) => (prej.length > 1 ? prej.filter((_, j) => j !== i) : prej))}
              >
                ×
              </button>
            </li>
          )
        })}
      </ol>
      {tekme.length < NAJVEC_TEKEM_DNE && (
        <button
          type="button"
          className="gumb razlaga__dodaj"
          onClick={() => nastaviTekme((prej) => [...prej, { nasprotnik: '900', zmaga: true }])}
        >
          + Dodaj tekmo
        </button>
      )}
      <NapakaPoizvedbe poizvedba={dan} kaj="preizkusa" />
      <p className="razlaga__namig razlaga__namig--ozko">
        Prva tekma je navaden korak od izhodišča (K {p.kOsnovni + p.pribitekNeustaljen + p.pribitekNovinec} ×
        teža). Od druge naprej je rating izračunan znova iz vseh izidov dneva — zato poraz lahko vzame več
        kot sto točk, ker v novo luč postavi tudi zmage pred njim, naslednja zmaga pa jih vrne. Od drugega
        dne naprej teče rating po tekmah, kot pri vseh.
      </p>
    </Korak>
  )
}

/* ---------- 08 · Ko ne igraš ---------- */

function Korak8({ p }: { p: PravilaRatingaDto }) {
  const [mes, nastaviMes] = useState(8)
  const odbitek = odbitekZa(p, mes)
  const naLestvici = mes < p.mesecevDoSkritja
  const kVrnitve = p.kOsnovni + (mes > p.mesecevZaVrnitev ? p.pribitekVrnitev : 0)
  const stopnje = [...p.odbitki].sort((a, b) => a.mesecev - b.mesecev)
  const pravilo = `${stopnje
    .map((o, i) => (i === 0 ? `po ${o.mesecev} mesecih −${o.skupaj}` : `po ${o.mesecev} skupaj −${o.skupaj}`))
    .join(', ')} in potem nič več`

  return (
    <Korak st={8} naslov="Ko ne igraš">
      <p className="razlaga__odstavek">
        Kdor dolgo ne igra, se praviloma poslabša, številka pa ostane. Zato dobi majhen odbitek —
        izmerjeno na 91.741 tekmah odsotnost zniža moč za okrog 10 do 30 točk, ne za 80. Po dveh
        letih se ne odbija več.
      </p>
      <label className="razlaga__polje-stolpec razlaga__polje-stolpec--r24 razlaga__polje-stolpec--tesno">
        <span className="razlaga__oznaka">
          Mesecev brez tekme: <strong>{mes}</strong>
        </span>
        <input
          type="range"
          className="razlaga__drsnik"
          min={0}
          max={NAJVEC_MESECEV}
          value={mes}
          onChange={(e) => nastaviMes(Number(e.target.value))}
        />
      </label>
      <div className="razlaga__ravnilo">
        <div className="razlaga__ravnilo-podlaga" />
        <div className="razlaga__ravnilo-polnilo" style={{ width: `${(mes / NAJVEC_MESECEV) * 100}%` }} />
        {Array.from({ length: NAJVEC_MESECEV + 1 }, (_, m) => (
          <span
            key={m}
            className={'razlaga__crtica' + (m % 6 === 0 ? ' razlaga__crtica--dolga' : '')}
            style={{ left: `${(m / NAJVEC_MESECEV) * 100}%` }}
          />
        ))}
        {mejniki(p).map((m, j) => (
          <div
            key={m.mesecev}
            className={
              'razlaga__mejnik' +
              (j % 2 === 1 ? ' razlaga__mejnik--spodaj' : '') +
              (mes >= m.mesecev ? (m.skrit ? ' razlaga__mejnik--skrit' : ' razlaga__mejnik--dosezen') : '')
            }
            style={{ left: `${(m.mesecev / NAJVEC_MESECEV) * 100}%` }}
          >
            {m.vrednost}
            <span className="razlaga__mejnik-opis">{m.opis}</span>
          </div>
        ))}
      </div>
      <div className="razlaga__kazalniki razlaga__kazalniki--trije">
        <div className="razlaga__kazalnik">
          <div className={'razlaga__stevilka-48' + (odbitek ? ' razlaga__neg' : '')}>
            {odbitek ? `−${odbitek}` : '0'}
          </div>
          <div className="razlaga__kazalnik-oznaka">
            Odbitek · {PRIMER_RATING} → {PRIMER_RATING - odbitek}
          </div>
        </div>
        <div className="razlaga__kazalnik razlaga__kazalnik--drugi">
          <div className={'razlaga__stevilka-48 ' + (naLestvici ? 'razlaga__poz' : 'razlaga__neg')}>
            {naLestvici ? 'Da' : 'Ne'}
          </div>
          <div className="razlaga__kazalnik-oznaka">Na javni lestvici</div>
        </div>
        <div className="razlaga__kazalnik razlaga__kazalnik--drugi">
          <div className="razlaga__stevilka-48 razlaga__stevilka-48--razmerna razlaga__glavna">K {kVrnitve}</div>
          <div className="razlaga__kazalnik-oznaka">Ob vrnitvi</div>
        </div>
      </div>
      <p className="razlaga__namig razlaga__namig--ozko">
        {mes > p.mesecevZaVrnitev
          ? `Ob vrnitvi po ${mes} mesecih ima ustaljen igralec prvih ${p.tekemPoVrnitvi} tekem K ${kVrnitve} namesto ${p.kOsnovni} — da se hitreje vrne na svojo raven.${
              mes >= p.mesecevDoSkritja
                ? ' Z javne lestvice je izginil, rating pa ostane na profilu; ko spet zaigra, se vrne.'
                : ''
            }`
          : `Odbitki so skupni po stopnjah: ${pravilo}. Zapadejo po datumu, uveljavi pa jih nočno opravilo.`}
      </p>
    </Korak>
  )
}

/* ---------- 09 · Na kateri lestvici si ---------- */

function Korak9({ p }: { p: PravilaRatingaDto }) {
  const [spol, nastaviSpol] = useState<'M' | 'Z'>('M')
  const [tekme, nastaviTekme] = useState<TekmeNaLestvici>('dovolj')
  const [imaRating, nastaviImaRating] = useState(true)
  const [zadnjaPod18, nastaviZadnjo] = useState(true)
  const [starost, nastaviStarost] = useState(16)

  const o = odlocitevLestvice(p, spol, tekme, imaRating, zadnjaPod18)
  const zunaj = LESTVICE.length - 1
  const vmes = p.pragRekreativca - 1

  return (
    <Korak st={9} naslov="Na kateri lestvici si">
      <p className="razlaga__odstavek">
        Moški in ženske imajo vsak svojo lestvico — med njimi skoraj ni tekem, zato številki nista
        primerljivi. Rekreativci so svoja lestvica, dokler ne odigrajo {p.pragRekreativca} tekem na
        uradnem ali klubskem tekmovanju. Opiši igralca:
      </p>
      <div className="razlaga__opis-igralca">
        <div>
          <span className="razlaga__oznaka">Spol</span>
          <Izbirnik<'M' | 'Z'>
            moznosti={[
              { vrednost: 'M', napis: 'Moški' },
              { vrednost: 'Z', napis: 'Ženska' },
            ]}
            izbrana={spol}
            naIzbiro={nastaviSpol}
          />
        </div>
        <div>
          <span className="razlaga__oznaka">Tekme na uradnem ali klubskem</span>
          <Izbirnik<TekmeNaLestvici>
            moznosti={[
              { vrednost: 'nic', napis: '0' },
              ...(vmes >= 1 ? [{ vrednost: 'malo' as const, napis: vmes === 1 ? '1' : `1–${vmes}` }] : []),
              { vrednost: 'dovolj', napis: `${p.pragRekreativca} ali več` },
            ]}
            izbrana={tekme}
            naIzbiro={nastaviTekme}
          />
        </div>
        <div>
          <span className="razlaga__oznaka">Ima rating</span>
          <Izbirnik
            moznosti={[
              { vrednost: true, napis: 'Da' },
              { vrednost: false, napis: 'Ne' },
            ]}
            izbrana={imaRating}
            naIzbiro={nastaviImaRating}
          />
        </div>
        <div>
          <span className="razlaga__oznaka">Zadnja tekma</span>
          <Izbirnik
            moznosti={[
              { vrednost: true, napis: `Pod ${p.mesecevDoSkritja} mes.` },
              { vrednost: false, napis: `${p.mesecevDoSkritja}+ mes.` },
            ]}
            izbrana={zadnjaPod18}
            naIzbiro={nastaviZadnjo}
          />
        </div>
      </div>
      <label className="razlaga__polje-stolpec razlaga__polje-stolpec--r16 razlaga__polje-stolpec--tesno razlaga__polje-stolpec--ozko">
        <span className="razlaga__oznaka">
          Starost v sezoni: <strong>{starost} let</strong>
        </span>
        <input
          type="range"
          className="razlaga__drsnik"
          min={8}
          max={70}
          value={starost}
          onChange={(e) => nastaviStarost(Number(e.target.value))}
        />
      </label>

      <div className="razlaga__lestvice">
        {LESTVICE.map((l, i) => {
          const izbrana = i === o.izbrana
          return (
            <div
              key={l.ime}
              className={
                'razlaga__lestvica' +
                (izbrana ? (i === zunaj ? ' razlaga__lestvica--zunaj' : ' razlaga__lestvica--izbrana') : '')
              }
            >
              <div className="razlaga__lestvica-oznaka">{l.oznaka}</div>
              <div className="razlaga__lestvica-ime">{l.ime}</div>
            </div>
          )
        })}
      </div>
      <p className="razlaga__razlog">{o.razlog}</p>
      {o.imaKategorije && (
        <div className="razlaga__kategorije">
          <span className="razlaga__oznaka">Vidiš ga tudi v kategorijah</span>
          <div className="razlaga__znacke">
            {kategorijeIgralca(p, starost).map((k) => (
              <span
                key={k.oznaka}
                className={'razlaga__znacka' + (k.aktivna ? ' razlaga__znacka--aktivna' : '')}
              >
                {k.oznaka}
              </span>
            ))}
          </div>
        </div>
      )}
      <p className="razlaga__namig razlaga__namig--ozko">
        Kategorije so izbor iste lestvice: med člani so vsi tekmovalci, med U19 samo igralci do 19
        let. Starost se meri po pravilu NTZS na 31. december leta, v katerem se sezona začne. Rating
        ne more pasti pod {p.spodnjaMeja}.
      </p>
    </Korak>
  )
}

/* ---------- 10 · Zakaj …? ---------- */

function Korak10({ p, vidno }: { p: PravilaRatingaDto; vidno: boolean }) {
  return (
    <Korak st={10} naslov="Zakaj …?">
      <div className="razlaga__vrstica-vprasanj">
        <p className="razlaga__odstavek razlaga__odstavek--ozko razlaga__odstavek--brez-zgoraj">
          Vprašanja, ki jih igralci res postavljajo. Vsak odgovor kaže na korak, ki ga razloži.
        </p>
        <div className="razlaga__maskota">
          <LikSTablo napis="Vprašaj." merilo={1.8} vidno={vidno} />
        </div>
      </div>
      <div className="razlaga__vprasanja">
        {vprasanja(p).map((v, i) => (
          <details key={v.q} className="razlaga__vprasanje">
            <summary>
              <span className="razlaga__vprasanje-st">{String(i + 1).padStart(2, '0')}</span>
              <span className="razlaga__vprasanje-besedilo">{v.q}</span>
              <span className="razlaga__znak" aria-hidden="true">
                +
              </span>
            </summary>
            <p>
              {v.a}{' '}
              <a href={v.href} className="razlaga__vprasanje-povezava">
                {v.povezava}
              </a>
            </p>
          </details>
        ))}
      </div>
      <div className="razlaga__noga">
        <p>
          Turnirko rating ni uradna jakostna lestvica NTZS. Je lastna ocena iz vseh tekem v bazi —
          uradnih, klubskih in rekreativnih.
        </p>
        <div className="razlaga__noga-gumba">
          <a href="#k1" className="gumb">
            Na vrh
          </a>
          <Link to="/lestvica" className="gumb gumb--glavni">
            Na lestvico
          </Link>
        </div>
      </div>
    </Korak>
  )
}
