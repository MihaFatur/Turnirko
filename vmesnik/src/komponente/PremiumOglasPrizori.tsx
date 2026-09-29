/* Risbe oglasa »Igralec Premium«: lik (poza zavesa ali semafor) in prizor
   ključavnice. Geometrija je prenesena 1:1 iz predaje (design/Maskota.dc.html in
   design/Prizor kljucavnica.dc.html); barve in debeline črt so v slog.css
   (razdelek »Oglas Igralec Premium«), gibanje pa v pomozno/animacijaOglasa.ts.

   Lik NI lik iz maskote v glavi (MaskotaPrizori.tsx): ima odprta usta in mežik,
   oglas pa ga riše veliko (do 3,6×), zato so debeline črt v enotah lika in se
   z njim večajo — pri maskoti v glavi so noge v pikslih zaslona.

   Sklepi so vrtišča kot povsod pri liku: zunanja skupina (`transform` kot atribut)
   postavi izhodišče v sklep, notranja (`data-m` / `data-k`) se vrti okoli
   svojega (0, 0). */
import { useLayoutEffect, useRef } from 'react'

import { predvajajKljucavnico, predvajajLika, type PozaLika } from '../pomozno/animacijaOglasa'

interface Poza {
  lRama: number
  lPod: number
  dRama: number
  dPod: number
  lopar: boolean
  zoga: boolean
  mezik: boolean
}

const POZE: Record<PozaLika, Poza> = {
  zavesa: { lRama: 140, lPod: 10, dRama: -100, dPod: -40, lopar: false, zoga: false, mezik: false },
  semafor: { lRama: 160, lPod: 10, dRama: -90, dPod: 0, lopar: true, zoga: true, mezik: true },
}

/* Lik z rokama; `zavesa` drži zaveso (roki v zraku), `semafor` odbija žogico
   z loparjem in mežika. Platno je 76 × 82 enot, tla so pri y = 62,5. */
export function PremiumLik({ poza }: { poza: PozaLika }) {
  const platno = useRef<SVGSVGElement>(null)
  const p = POZE[poza]

  useLayoutEffect(() => {
    if (!platno.current) return
    return predvajajLika(platno.current, poza)
  }, [poza])

  return (
    <svg
      ref={platno}
      className="premium-lik"
      viewBox="56 -14 76 82"
      width="100%"
      height="100%"
      aria-hidden="true"
      focusable="false"
    >
      <g data-m="figura">
        <g className="premium-lik__noge">
          <path d="M85.5 52 L83.5 62.5 H79.5" />
          <path d="M90.5 52 L92.5 62.5 H96.5" />
        </g>
        <rect className="premium-lik__majica" x="83" y="38.5" width="10" height="11" />
        <rect className="premium-lik__hlacke" x="83" y="49.5" width="10" height="4.5" />
        <g transform="translate(88 37.5)">
          <g data-m="glava">
            <circle className="premium-lik__obraz" cy="-8" r="7" />
            <path className="premium-lik__celni-trak" d="M-6 -11.2 Q0 -9.2 6 -11.2" />
            <path className="premium-lik__pentlja" d="M6 -11.2 L11.5 -13.4 M6 -11.2 L11 -8.8" />
            <circle className="premium-lik__oko" cx="-2.7" cy="-7.2" r="1.15" />
            {p.mezik ? (
              <path className="premium-lik__mezik" d="M1.5 -7 Q2.7 -8.4 3.9 -7" />
            ) : (
              <circle className="premium-lik__oko" cx="2.7" cy="-7.2" r="1.15" />
            )}
            <path className="premium-lik__usta" d="M-3 -4.6 Q0 -0.8 3 -4.6 Z" />
          </g>
        </g>

        <g transform="translate(83.5 38.5)">
          <g transform={`rotate(${p.lRama})`}>
            <g data-m="ramaL">
              <path className="premium-lik__roka" d="M0 0 V10.5" />
              <g transform="translate(0 10.5)">
                <g transform={`rotate(${p.lPod})`}>
                  <g data-m="podL">
                    <path className="premium-lik__roka" d="M0 0 V11.5" />
                    <path className="premium-lik__zapestnica" d="M0 8 V11" />
                    <circle className="premium-lik__dlan" cy="12" r="2.3" />
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>

        <g transform="translate(92.5 38.5)">
          <g transform={`rotate(${p.dRama})`}>
            <g data-m="ramaD">
              <path className="premium-lik__roka" d="M0 0 V10.5" />
              <g transform="translate(0 10.5)">
                <g transform={`rotate(${p.dPod})`}>
                  <g data-m="podD">
                    <path className="premium-lik__roka" d="M0 0 V11.5" />
                    <path className="premium-lik__zapestnica" d="M0 8 V11" />
                    {p.lopar && (
                      <>
                        <path className="premium-lik__rocaj" d="M0 13 V17" />
                        <ellipse className="premium-lik__lopar" cy="22.5" rx="2.2" ry="6" />
                      </>
                    )}
                    <circle className="premium-lik__dlan" cy="12" r="2.3" />
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>

        {p.zoga && (
          <g transform="translate(125.5 33.4)">
            <g data-m="zoga">
              <circle className="premium-lik__zoga" r="2.7" />
            </g>
          </g>
        )}
      </g>
    </svg>
  )
}

/* Deset koščkov konfetov: [širina, višina, razred barve]. Vsi vzletijo iz iste
   točke nad lokom; smer in vrtenje jim da animacija po zaporedju. */
const KONFETI: [number, number, string][] = [
  [8, 8, 'zelena'], [6, 10, 'papir'], [8, 5, 'crnilo'], [7, 7, 'mehka'], [9, 4, 'zelena'],
  [5, 9, 'papir'], [8, 8, 'crnilo'], [6, 6, 'zelena'], [7, 4, 'mehka'], [5, 8, 'papir'],
]

/* Prizor ključavnice (1b): maskota priteče s ključem, ga porine v ključavnico,
   obrne, ključavnica klikne in se odpre. Končno stanje (brez animacije) je
   odprta ključavnica s ključem v njej in maskota z rokama v zraku, ki mežika. */
export function PrizorKljucavnica() {
  const platno = useRef<SVGSVGElement>(null)

  useLayoutEffect(() => {
    if (!platno.current) return
    return predvajajKljucavnico(platno.current)
  }, [])

  return (
    <svg
      ref={platno}
      className="premium-kljucavnica"
      viewBox="0 0 360 220"
      width="100%"
      height="100%"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <clipPath id="premium-kljuc-rez">
          <rect x="-400" y="-200" width="659" height="600" />
        </clipPath>
      </defs>
      <rect className="premium-kljucavnica__tla" x="0" y="199" width="360" height="3" />

      <g data-k="iskre" style={{ opacity: 0 }}>
        <g className="premium-kljucavnica__iskra">
          <path d="M259 2 V-12" />
          <path d="M226 12 L216 2" />
          <path d="M292 12 L302 2" />
          <path d="M212 40 H198" />
          <path d="M306 40 H320" />
        </g>
      </g>

      <g data-k="telo">
        <g transform="translate(0 -26)">
          <g data-k="lukDvig">
            <g transform="translate(289 0)">
              <g transform="scale(-1 1)">
                <g data-k="lukKlap">
                  <g transform="translate(-289 0)">
                    <path className="premium-kljucavnica__luk" d="M229 100 V64 A30 30 0 0 1 289 64 V132" />
                    <path className="premium-kljucavnica__luk-rob" d="M225 96 V64 A34 34 0 0 1 259 30" />
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>

        <rect className="premium-kljucavnica__telo" x="214" y="96" width="90" height="104" />
        <rect className="premium-kljucavnica__trak" data-k="trak" x="214" y="96" width="90" height="12" />
        <rect className="premium-kljucavnica__okvir" x="222" y="116" width="74" height="76" />
        <rect className="premium-kljucavnica__zakovica" x="217" y="111" width="4" height="4" />
        <rect className="premium-kljucavnica__zakovica" x="297" y="111" width="4" height="4" />
        <rect className="premium-kljucavnica__zakovica" x="217" y="193" width="4" height="4" />
        <rect className="premium-kljucavnica__zakovica" x="297" y="193" width="4" height="4" />
        <g className="premium-kljucavnica__luknja" data-k="luknja">
          <circle cx="259" cy="140" r="11" />
          <path d="M253 146 H265 L268 166 H250 Z" />
        </g>
        <text className="premium-kljucavnica__napis" x="259" y="184" textAnchor="middle">
          PREMIUM
        </text>
      </g>

      <g clipPath="url(#premium-kljuc-rez)">
        <g transform="translate(293 140)">
          <g data-k="kljucPot">
            <g data-k="kljucObrat">
              <g className="premium-kljucavnica__kljuc">
                <path d="M-30 3 H-20 V14 H-30 Z" />
                <path d="M-16 3 H-8 V10 H-16 Z" />
                <rect x="-82" y="-4" width="82" height="8" />
              </g>
              <rect className="premium-kljucavnica__kljuc-vrat" x="-86" y="-8" width="8" height="16" />
              <circle className="premium-kljucavnica__kljuc-loper" cx="-100" r="18" />
              <circle className="premium-kljucavnica__kljuc-obroc" cx="-100" r="12.5" />
              <rect className="premium-kljucavnica__kljuc-luknja" x="-105" y="-5" width="10" height="10" />
            </g>
          </g>
        </g>
      </g>

      <g data-k="tim">
        <g data-k="skok">
          <g transform="translate(84 200)">
            <g data-k="nagib">
              <g transform="translate(-84 -200)">
                <g transform="translate(84 200) scale(3.6) translate(-88 -62.5)">
                  <g className="premium-lik__noge">
                    <g transform="translate(85.5 52)">
                      <g data-k="nogaL">
                        <g transform="translate(-85.5 -52)">
                          <path d="M85.5 52 L83.5 62.5 H79.5" />
                        </g>
                      </g>
                    </g>
                    <g transform="translate(90.5 52)">
                      <g data-k="nogaD">
                        <g transform="translate(-90.5 -52)">
                          <path d="M90.5 52 L92.5 62.5 H96.5" />
                        </g>
                      </g>
                    </g>
                  </g>
                  <g transform="translate(83.5 38.5)">
                    <g transform="rotate(160)">
                      <g data-k="ramaL">
                        <path className="premium-lik__roka" d="M0 0 V10.5" />
                        <g transform="translate(0 10.5)">
                          <g data-k="podL">
                            <path className="premium-lik__roka" d="M0 0 V11.5" />
                            <path className="premium-lik__zapestnica" d="M0 8 V11" />
                            <circle className="premium-lik__dlan" cy="12" r="2.3" />
                          </g>
                        </g>
                      </g>
                    </g>
                  </g>
                  <rect className="premium-lik__majica" x="83" y="38.5" width="10" height="11" />
                  <rect className="premium-lik__hlacke" x="83" y="49.5" width="10" height="4.5" />
                  <g transform="translate(88 37.5)">
                    <g data-k="glava">
                      <circle className="premium-lik__obraz" cy="-8" r="7" />
                      <path className="premium-lik__celni-trak" d="M-6 -11.2 Q0 -9.2 6 -11.2" />
                      <g data-k="pentlja">
                        <path className="premium-lik__pentlja" d="M6 -11.2 L11.5 -13.4 M6 -11.2 L11 -8.8" />
                      </g>
                      <circle className="premium-lik__oko" cx="-2.7" cy="-7.2" r="1.15" />
                      <circle
                        className="premium-lik__oko"
                        data-k="okoOdprto"
                        cx="2.7"
                        cy="-7.2"
                        r="1.15"
                        style={{ opacity: 0 }}
                      />
                      <path className="premium-lik__mezik" data-k="okoMezik" d="M1.5 -7 Q2.7 -8.4 3.9 -7" />
                      <path
                        className="premium-lik__usta-ravna"
                        data-k="ustaZ"
                        d="M-2 -4 H2"
                        style={{ opacity: 0 }}
                      />
                      <path className="premium-lik__usta" data-k="ustaS" d="M-3.2 -4.8 Q0 -0.4 3.2 -4.8 Z" />
                    </g>
                  </g>
                  <g transform="translate(92.5 38.5)">
                    <g transform="rotate(-160)">
                      <g data-k="ramaD">
                        <path className="premium-lik__roka" d="M0 0 V10.5" />
                        <g transform="translate(0 10.5)">
                          <g data-k="podD">
                            <path className="premium-lik__roka" d="M0 0 V11.5" />
                            <path className="premium-lik__zapestnica" d="M0 8 V11" />
                            <circle className="premium-lik__dlan" cy="12" r="2.3" />
                          </g>
                        </g>
                      </g>
                    </g>
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>
      </g>

      <g data-k="konfeti">
        {KONFETI.map(([sirina, visina, barva], i) => (
          <rect
            key={i}
            className={`premium-kljucavnica__konfet premium-kljucavnica__konfet--${barva}`}
            data-k="kf"
            x="255"
            y="46"
            width={sirina}
            height={visina}
          />
        ))}
      </g>

      <g transform="translate(150 44) rotate(-9)">
        <g data-k="klik">
          <text className="premium-kljucavnica__klik" x="0" y="0" textAnchor="middle">
            KLIK!
          </text>
        </g>
      </g>
    </svg>
  )
}
