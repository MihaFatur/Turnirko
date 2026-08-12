/* Graf napredka klubskega ELO. Narisan kot lasten SVG — za eno črto ni
   razloga za dodatno knjižnico, poleg tega se tako brez težav prilagodi
   širini in temi.

   Vodoravna os je zaporedje obračunanih tekem (ne koledar), ker so tekme
   pogosto zgoščene v turnirske dneve in bi časovno merilo dalo prazne pasove.
   Datum je izpisan pod prvo in zadnjo točko ter ob izbrani točki.

   Oznake osi so HTML nad risalno ploskvijo, ne <text> v SVG: SVG jih pri
   raztegu ploskve na širino okvirja skalira skupaj z grafom (12 px bi na
   1360 px oknu postalo 14,6 px), poleg tega jih na telefonu ni mogoče
   preprosto skriti. */
import type { ReactNode } from 'react'
import { useState } from 'react'

import type { TockaGrafa } from '../api/tipi'

type Obdobje = 'vse' | '12m' | '3m'

const OBDOBJA: { kljuc: Obdobje; oznaka: string; meseci: number | null }[] = [
  { kljuc: 'vse', oznaka: 'Vse', meseci: null },
  { kljuc: '12m', oznaka: '12 mesecev', meseci: 12 },
  { kljuc: '3m', oznaka: '3 meseci', meseci: 3 },
]

/* Risalna ploskev je široka kot vsebinski okvir (1280 px minus 2 x 40 px
   odmika), da se graf razteza čez celo sekcijo — kot na maketi. */
const SIRINA = 1120
const VISINA = 280
const ROB = { levo: 56, desno: 16, zgoraj: 16, spodaj: 32 }

/* "otroci" so bloki, ki sodijo v isto sekcijo pod graf (npr. pričakovan proti
   doseženemu izkupičku) — sekcijo namreč izriše ta komponenta, ker izbirnik
   obdobja stoji v njeni naslovni vrstici. */
export function GrafElo({ tocke, children }: { tocke: TockaGrafa[]; children?: ReactNode }) {
  const [obdobje, nastaviObdobje] = useState<Obdobje>('vse')
  const [izbrana, nastaviIzbrano] = useState<number | null>(null)

  const filtrirane = filtrirajPoObdobju(tocke, obdobje)

  if (tocke.length === 0) {
    return (
      <div>
        <div className="naslovna-vrstica">
          <h2>Napredek ELO</h2>
        </div>
        <p className="obvestilo">Ni še obračunanih tekem, zato graf ELO še ni na voljo.</p>
        {children}
      </div>
    )
  }

  const najmanj = Math.min(...filtrirane.map((t) => t.vrednost))
  const najvec = Math.max(...filtrirane.map((t) => t.vrednost))
  /* Če je razpon ničeln (ena sama tekma), umetno razpri, da črta ni na robu. */
  const razpon = Math.max(najvec - najmanj, 20)
  const spodaj = najmanj - razpon * 0.15
  const zgoraj = najvec + razpon * 0.15

  const risalnaSirina = SIRINA - ROB.levo - ROB.desno
  const risalnaVisina = VISINA - ROB.zgoraj - ROB.spodaj

  const x = (i: number) =>
    ROB.levo + (filtrirane.length === 1 ? risalnaSirina / 2 : (i / (filtrirane.length - 1)) * risalnaSirina)
  const y = (v: number) =>
    ROB.zgoraj + risalnaVisina - ((v - spodaj) / (zgoraj - spodaj)) * risalnaVisina

  const crta = filtrirane.map((t, i) => `${x(i)},${y(t.vrednost)}`).join(' ')
  const ploskev =
    filtrirane.length > 1
      ? `${ROB.levo},${ROB.zgoraj + risalnaVisina} ${crta} ${ROB.levo + risalnaSirina},${
          ROB.zgoraj + risalnaVisina
        }`
      : ''

  const oznakeY = [zgoraj, (zgoraj + spodaj) / 2, spodaj]
  const podrobnost = izbrana !== null ? filtrirane[izbrana] : filtrirane[filtrirane.length - 1]

  /* Izbirnik obdobja stoji v naslovni vrstici sekcije, zato komponenta izriše
     celo sekcijo — tako je vse v eni vrstici, kot zahteva maketa. */
  return (
    <div className="graf">
      <div className="naslovna-vrstica">
        <h2>Napredek ELO</h2>
        <div className="izbirnik">
          {OBDOBJA.map((o) => (
            <button
              type="button"
              key={o.kljuc}
              className={
                'izbirnik__gumb' + (obdobje === o.kljuc ? ' izbirnik__gumb--aktiven' : '')
              }
              onClick={() => {
                nastaviObdobje(o.kljuc)
                nastaviIzbrano(null)
              }}
            >
              {o.oznaka}
              {obdobje === o.kljuc ? ` · ${filtrirane.length}` : ''}
            </button>
          ))}
        </div>
      </div>

      {filtrirane.length === 0 ? (
        <p className="obvestilo">V izbranem obdobju ni obračunanih tekem.</p>
      ) : (
        <>
          <div className="graf__ovoj">
            {oznakeY.map((v) => (
              <span
                key={v}
                className="graf__oznaka graf__oznaka--y"
                style={{ top: `${(y(v) / VISINA) * 100}%` }}
              >
                {Math.round(v)}
              </span>
            ))}
            <span className="graf__oznaka graf__oznaka--prvi">{datum(filtrirane[0].kdaj)}</span>
            {filtrirane.length > 1 && (
              <span className="graf__oznaka graf__oznaka--zadnji">
                {datum(filtrirane[filtrirane.length - 1].kdaj)}
              </span>
            )}
            <svg
              className="graf__svg"
              viewBox={`0 0 ${SIRINA} ${VISINA}`}
              role="img"
              aria-label="Graf napredka klubskega ELO"
            >
              {oznakeY.map((v) => (
                <line
                  key={v}
                  className="graf__mreza"
                  x1={ROB.levo}
                  x2={SIRINA - ROB.desno}
                  y1={y(v)}
                  y2={y(v)}
                />
              ))}

              {ploskev && <polygon className="graf__ploskev" points={ploskev} />}
              <polyline className="graf__crta" points={crta} />

              {filtrirane.map((t, i) => (
                <circle
                  key={`${t.ligaska ? 'l' : 't'}${t.idTekme}-${i}`}
                  className={
                    'graf__tocka' +
                    (izbrana === i ? ' graf__tocka--izbrana' : '') +
                    (t.sprememba >= 0 ? ' graf__tocka--plus' : ' graf__tocka--minus')
                  }
                  cx={x(i)}
                  cy={y(t.vrednost)}
                  r={izbrana === i ? 5 : 3.5}
                  onMouseEnter={() => nastaviIzbrano(i)}
                  onFocus={() => nastaviIzbrano(i)}
                  tabIndex={0}
                >
                  <title>
                    {datum(t.kdaj)} · {t.vrednost} ({t.sprememba >= 0 ? '+' : ''}
                    {t.sprememba}){t.nasprotnik ? ` · ${t.nasprotnik}` : ''}
                  </title>
                </circle>
              ))}
            </svg>
          </div>

          {podrobnost && (
            <p className="graf__podrobnost">
              <strong>{podrobnost.vrednost}</strong>
              <span className="graf__opis">·</span>
              <span
                className={
                  'graf__sprememba' +
                  (podrobnost.sprememba >= 0 ? ' graf__sprememba--plus' : ' graf__sprememba--minus')
                }
              >
                {podrobnost.sprememba >= 0 ? '+' : '−'}
                {Math.abs(podrobnost.sprememba)}
              </span>
              <span className="graf__opis">
                · {datum(podrobnost.kdaj)}
                {podrobnost.nasprotnik ? ` · proti ${podrobnost.nasprotnik}` : ''}
                {podrobnost.ligaska ? ' · liga' : ''} · vodoravno je zaporedje obračunanih
                tekem, ne koledar
              </span>
            </p>
          )}
        </>
      )}

      {children}
    </div>
  )
}

function filtrirajPoObdobju(tocke: TockaGrafa[], obdobje: Obdobje): TockaGrafa[] {
  const meseci = OBDOBJA.find((o) => o.kljuc === obdobje)?.meseci
  if (!meseci) return tocke
  const meja = new Date()
  meja.setMonth(meja.getMonth() - meseci)
  return tocke.filter((t) => new Date(t.kdaj) >= meja)
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
