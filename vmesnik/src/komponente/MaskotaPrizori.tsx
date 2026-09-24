/* Lik maskote, tabla, letalo z zastavico in lestev (izris; gibanje:
   pomozno/animatorMaskote.ts, podatki prizorov: pomozno/prizoriMaskote.ts,
   umestitev: pomozno/umestitevMaskote.ts, gostitelj: Maskota.tsx).

   Vse je inline SVG in ne slika, Lottie ali video: barve so hišne spremenljivke
   (papir, črnilo, modra, zelena), zato ostane del sistema; ne potrebuje
   omrežja; tehta nekaj sto bajtov. Isti lik igra vse prizore — prizor ga ne
   riše, ampak premika po delih (`data-del`); letalo in lestev ga samo
   postavita na svoje mesto (v kabino, na lestev).

   Sklepi so vrtišča: vsak gibljivi del stoji v dveh skupinah. Zunanja
   (`transform` kot atribut) postavi izhodišče v sklep, notranja (`data-del`) se
   vrti okoli svojega (0, 0) — torej okoli sklepa. Le tako je `rotate` brez
   `transform-origin` v pikslih pravilen v vseh brskalnikih. */
import { type ReactNode, useEffect, useLayoutEffect, useRef } from 'react'

import { predvajaj, type PrizorPodatki, type Umestitev } from '../pomozno/animatorMaskote'
import {
  DODATEK_TABLE,
  LESTEV,
  LESTEV_STOPALA,
  LETALO,
  MERILO_LIKA,
  izmeriGlavo,
  potLestve,
  potLetala,
  steviloPasov,
} from '../pomozno/umestitevMaskote'
import { jeProstor } from '../pomozno/urnikMaskote'

/* Platno ob uporabniku sega 4 enote nad tla lika: tam je še glava strani, ki
   je prazna. */
const NAD_LIKOM = 4
export const VISINA_MASKOTE = 68
const SIRINA_NAJMANJ = 140

/* Prostor pod zgornjim robom platna, ki ga tabla sme zasesti pri skoku, nagibu
   in prenagljenem razgrinjanju (enote zaslona). Iz njega se izračuna največji
   nagib table. */
const REZERVA_ZGORAJ = 4.5

export interface Nastop {
  prizor: PrizorPodatki
  napis: string
  /* Širina table v enotah lika. */
  sirinaTable: number
  nagib: number
  umestitev: Umestitev
}

/* Iz izmerjenega napisa sestavi nastop ali vrne null, če se prizor v trenutno
   glavo ne prilega (preozko okno, predolg napis, manjka povezava). Širša tabla
   ima pri istem nagibu višji vogal, zato se nagib zmanjša: vogal se ne sme
   dvigniti čez rezervo. */
export function sestaviNastop(
  prizor: PrizorPodatki,
  napis: string,
  sirinaBesedila: number,
): Nastop | null {
  const sirinaTable = Math.ceil(sirinaBesedila) + DODATEK_TABLE
  const polSirine = (sirinaTable * MERILO_LIKA) / 2
  const nagib = Math.min(
    4.5,
    Math.max(1, (Math.asin(Math.min(1, REZERVA_ZGORAJ / polSirine)) * 180) / Math.PI),
  )

  if (prizor.postavitev === 'uporabnik') {
    const sirina = Math.max(SIRINA_NAJMANJ, Math.ceil(sirinaTable * MERILO_LIKA) + 24)
    if (!jeProstor(sirina)) return null
    return {
      prizor,
      napis,
      sirinaTable,
      nagib,
      umestitev: { vrsta: 'uporabnik', sirina, visina: VISINA_MASKOTE },
    }
  }

  const glava = izmeriGlavo()
  if (!glava) return null
  if (prizor.slika === 'letalo') {
    if (!potLetala(glava, sirinaTable)) return null
    return {
      prizor,
      napis,
      sirinaTable,
      nagib,
      umestitev: { vrsta: 'glava', sirina: glava.sirina, visina: LETALO.visina, glava },
    }
  }
  if (!potLestve(glava, sirinaTable)) return null
  return {
    prizor,
    napis,
    sirinaTable,
    nagib,
    umestitev: {
      vrsta: 'glava',
      sirina: glava.sirina,
      visina: LESTEV.visina + LESTEV.rezerva,
      glava,
    },
  }
}

/* Roka: rama in komolec. `L`/`D` sta imeni sledov v prizorih; predznak
   vrtenja loči strani (glej SLEDI v animatorju). */
function Roka({
  stran,
  x,
  lopar,
  palec,
}: {
  stran: 'L' | 'D'
  x: number
  lopar: boolean
  palec: boolean
}) {
  return (
    <g transform={`translate(${x} 38.5)`}>
      <g data-del={`roka${stran}`} className="maskota__roka">
        <path d="M0 0 V10.5" />
        <g transform="translate(0 10.5)">
          <g data-del={`pod${stran}`} className="maskota__podlaket">
            <path d="M0 0 V11.5" />
            <path className="maskota__zapestnica" d="M0 8 V11" />
            {/* Lopar nadaljuje podlaket, dlan ga drži od spodaj. */}
            {stran === 'D' && lopar && (
              <g data-del="lopar" className="maskota__lopar">
                <path className="maskota__rocaj" d="M0 13 V17" />
                <ellipse className="maskota__lopar-glava" cy="22.5" rx="2.2" ry="6" />
              </g>
            )}
            <circle className="maskota__dlan" cy="12" r="2.3" />
            {/* Pest s palcem gor: palec je PRAVOKOTEN na podlaket, zato kaže gor,
                ko je roka iztegnjena vstran (kot pri »v redu« skozi okno avta).
                Nadaljevanje podlakti bi ob dvignjeni roki zašlo za tablo. Skrit,
                dokler ga prizor ne pokaže. */}
            {stran === 'D' && palec && (
              <g data-del="palec" opacity="0">
                <rect className="maskota__palec" x="1.4" y="11.6" width="7.2" height="3.2" rx="1.6" />
                <circle className="maskota__pest" cy="13" r="3.6" />
              </g>
            )}
          </g>
        </g>
      </g>
    </g>
  )
}

/* Lik stoji na tleh (y = 64, 1 px črta pod glavo je tik pod njimi). Noge so
   vezane na telo (glej `izpeljiNoge` v animatorju), zato stopala ostanejo na
   tleh pri počepu in skoku. `rekvizit` (tabla) se izriše za rokami, a pred
   njimi, da ga dlani držijo od spodaj. */
export function Lik({
  lopar = false,
  palec = false,
  rekvizit = null,
}: {
  lopar?: boolean
  palec?: boolean
  rekvizit?: ReactNode
}) {
  return (
    <g transform="translate(88 45)">
      <g data-del="figura" className="maskota__figura">
        <g transform="translate(-88 -45)">
          <g transform="translate(88 52)">
            <g data-del="noge" className="maskota__noge">
              <g transform="translate(-2.5 0)">
                <g data-del="nogaL">
                  <path d="M0 0 L-2 10.5 M-2 10.5 H-6" />
                </g>
              </g>
              <g transform="translate(2.5 0)">
                <g data-del="nogaD">
                  <path d="M0 0 L2 10.5 M2 10.5 H6" />
                </g>
              </g>
            </g>
          </g>

          <rect className="maskota__majica" x="83" y="38.5" width="10" height="11" />
          <rect className="maskota__hlacke" x="83" y="49.5" width="10" height="4.5" />

          <g transform="translate(88 37.5)">
            <g data-del="glava">
              <circle className="maskota__obraz" cy="-8" r="7" />
              <path className="maskota__celni-trak" d="M-6 -11.2 Q0 -9.2 6 -11.2" />
              <g transform="translate(6 -11.2)">
                <g data-del="pentlja">
                  <path className="maskota__pentlja" d="M0 0 L5.5 -2.2 M0 0 L5 2.4" />
                </g>
              </g>
              <g transform="translate(-2.7 -7.2)">
                <g data-del="okoL">
                  <circle className="maskota__oko" r="1.15" />
                </g>
              </g>
              <g transform="translate(2.7 -7.2)">
                <g data-del="okoD">
                  <circle className="maskota__oko" r="1.15" />
                </g>
              </g>
              <path className="maskota__usta" d="M-2.7 -4.4 Q0 -2 2.7 -4.4" />
            </g>
          </g>

          {rekvizit}

          <Roka stran="L" x={83.5} lopar={lopar} palec={palec} />
          <Roka stran="D" x={92.5} lopar={lopar} palec={palec} />

          {/* Žogica stoji nad loparjem, ko je desna roka vodoravna (glej prizor
              »zogica«), in se s telesom dviga in spušča. Vrtišče je njeno
              središče, da se stiska in razteguje na mestu. */}
          {lopar && (
            <g transform="translate(121.75 41.6)">
              <g data-del="zoga">
                <circle className="maskota__zoga" r="2.7" />
              </g>
            </g>
          )}
        </g>
      </g>
    </g>
  )
}

/* Tabla s krajšim napisom je ožja: širina sledi besedilu, da ne ostane prazen
   rob in da se dolg napis ne izteče čez rob. Vrtišče je spodnji sredinski rob,
   ker ga držita dlani (`x`, `y` ga postavita; privzeto nad glavo lika). */
function Tabla({
  napis,
  sirina,
  x = 88,
  y = 19.5,
}: {
  napis: string
  sirina: number
  x?: number
  y?: number
}) {
  return (
    <g transform={`translate(${x} ${y})`}>
      <g data-del="tabla" className="maskota__tabla">
        <rect className="maskota__plosca" x={-sirina / 2} y="-19" width={sirina} height="19" />
        <rect
          className="maskota__poudarek"
          x={-sirina / 2 + 1.5}
          y="-17.5"
          width="6"
          height="16"
        />
        <text className="maskota__napis" x="3.25" y="-5.6" textAnchor="middle">
          {napis}
        </text>
      </g>
    </g>
  )
}

function SlikaLik({ nastop }: { nastop: Nastop }) {
  return (
    <g
      transform={`translate(${nastop.umestitev.sirina / 2} 64) scale(${MERILO_LIKA}) translate(-88 -64)`}
    >
      <Lik
        lopar={nastop.prizor.lopar === true}
        rekvizit={<Tabla napis={nastop.napis} sirina={nastop.sirinaTable} />}
      />
    </g>
  )
}

/* Letalo z voznikom (lik v kabini, trup mu zakrije spodnji del) in zastavico
   za sabo. Zastavica je razrezana na pasove, ki se vsak zase pomikajo gor in
   dol (cikli): iz istega napisa, vsak pas ga obreže na svoj stolpec, zato se
   napis ob majhnih zamikih ne pretrga, tkanina pa dobi gube. Okno (`clipPath`)
   je fiksno med logotipom in »Domov«: letalo ne izleti iz zaslona, ampak izza
   logotipa nastane in za povezavo izgine. */
function SlikaLetalo({ nastop }: { nastop: Nastop }) {
  const glava = nastop.umestitev.glava!
  const pot = potLetala(glava, nastop.sirinaTable)!
  const S = nastop.sirinaTable
  const n = steviloPasov(S)
  const pasovi = Array.from({ length: n }, (_, i) => i)

  return (
    <>
      <defs>
        <clipPath id="maskota-okno">
          <rect
            x={pot.xLevo}
            y="0"
            width={pot.xDesno - pot.xLevo}
            height={nastop.umestitev.visina}
          />
        </clipPath>
        {pasovi.map((i) => (
          <clipPath id={`maskota-pas-${i}`} key={i}>
            {/* Pasovi se rahlo prekrivajo, da med njimi ne ostane vidna reža. */}
            <rect x={-S + (i * S) / n} y="-11" width={S / n + 0.6} height="22" />
          </clipPath>
        ))}
      </defs>

      <g clipPath="url(#maskota-okno)">
        <g data-del="letalo" className="maskota__klik">
          <g data-del="plovba">
            <g transform={`translate(${-LETALO.rep} 1) scale(${MERILO_LIKA})`}>
              {pasovi.map((i) => (
                <g key={i} data-del={`val${i}`} clipPath={`url(#maskota-pas-${i})`}>
                  <rect className="maskota__plosca" x={-S} y="-9.5" width={S} height="19" />
                  <rect className="maskota__poudarek" x={-S + 1.5} y="-8" width="6" height="16" />
                  <text
                    className="maskota__napis"
                    x={-S / 2 + 3.25}
                    y="3.9"
                    textAnchor="middle"
                  >
                    {nastop.napis}
                  </text>
                </g>
              ))}
            </g>
            <path className="maskota__vrvica" d={`M-31 1 L${-LETALO.rep} 1`} />

            <path className="maskota__rep" d="M-25 -6 L-33 -16 L-23 -16 L-19 -6 Z" />
            <path className="maskota__podvozje" d="M-13 8 L-14 12 M10 8 L11 12" />
            <circle className="maskota__kolo" cx="-14" cy="12.5" r="2.6" />
            <circle className="maskota__kolo" cx="11" cy="12.5" r="2.6" />

            {/* Voznik: vrh trupa mu seže do prsi. */}
            <g transform="translate(0 -6) scale(0.8) translate(-88 -44)">
              <Lik />
            </g>

            <rect className="maskota__trup" x="-30" y="-7" width="62" height="15" rx="7" />
            <path className="maskota__pas" d="M-23 1 H24" />
            <ellipse className="maskota__krilo" cx="0" cy="9" rx="13" ry="2.8" />
            <g transform="translate(35 1)">
              <g data-del="propeler">
                <ellipse className="maskota__propeler" rx="1.6" ry="9" />
              </g>
            </g>
            <circle className="maskota__nos" cx="33" cy="1" r="2.2" />
          </g>
        </g>
      </g>
    </>
  )
}

/* Vrvna lestev (vrvi in prečke), po njej pleza lik; tabla pade nanjo in prekrije
   povezavo »Lestvica«. Lik je otrok lestve, zato ga ta ob dvigu potegne s sabo.
   Vrstni red: lestev, lik, tabla — lik med plezanjem pod tablo za njo izgine in
   izpod nje spet pride. */
function SlikaLestev({ nastop }: { nastop: Nastop }) {
  const glava = nastop.umestitev.glava!
  const pot = potLestve(glava, nastop.sirinaTable)!
  const p = LESTEV.polSirine
  const stPrecek = Math.floor((LESTEV.visina - LESTEV.razmik / 2) / LESTEV.razmik) + 1
  const precke = Array.from(
    { length: stPrecek },
    (_, k) => `M${-p} ${LESTEV.razmik / 2 + k * LESTEV.razmik} H${p}`,
  ).join(' ')

  return (
    <>
      <g transform={`translate(${pot.x} 0)`} className="maskota__klik">
        <g data-del="lestev">
          <g data-del="zibanje">
            <path className="maskota__vrv" d={`M${-p} 0 V${LESTEV.visina} M${p} 0 V${LESTEV.visina}`} />
            <path className="maskota__precka" d={precke} />
            <g transform={`translate(0 ${LESTEV_STOPALA}) scale(${MERILO_LIKA}) translate(-88 -64)`}>
              <Lik palec />
            </g>
          </g>
        </g>
      </g>

      <g
        transform={`translate(${pot.x} ${pot.yZnak + 9}) scale(${MERILO_LIKA})`}
        className="maskota__klik"
      >
        <Tabla napis={nastop.napis} sirina={nastop.sirinaTable} x={0} y={0} />
      </g>
    </>
  )
}

/* Platno; ob izrisu (pred prvim slikanjem, sicer bi lik za trenutek obstal v
   mirovni legi) zažene prizor in ob njegovem koncu pokliče `obKoncu`. */
export function PlatnoMaskote({ nastop, obKoncu }: { nastop: Nastop; obKoncu: () => void }) {
  const koren = useRef<SVGSVGElement>(null)
  const obKoncuRef = useRef(obKoncu)
  useEffect(() => {
    obKoncuRef.current = obKoncu
  })

  useLayoutEffect(() => {
    if (!koren.current) return
    const seja = predvajaj(koren.current, nastop.prizor, {
      nagib: nastop.nagib,
      umestitev: nastop.umestitev,
      sirinaTable: nastop.sirinaTable,
    })
    void seja.konec.then(() => obKoncuRef.current())
    return seja.ustavi
  }, [nastop])

  const u = nastop.umestitev
  return (
    <svg
      ref={koren}
      className="maskota__platno"
      viewBox={u.vrsta === 'uporabnik' ? `0 ${-NAD_LIKOM} ${u.sirina} ${u.visina}` : `0 0 ${u.sirina} ${u.visina}`}
      width={u.sirina}
      height={u.visina}
      aria-hidden="true"
      focusable="false"
    >
      {nastop.prizor.slika === 'lik' && <SlikaLik nastop={nastop} />}
      {nastop.prizor.slika === 'letalo' && <SlikaLetalo nastop={nastop} />}
      {nastop.prizor.slika === 'lestev' && <SlikaLestev nastop={nastop} />}
    </svg>
  )
}
