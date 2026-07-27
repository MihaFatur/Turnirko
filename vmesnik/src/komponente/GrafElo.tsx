/* Graf napredka klubskega ELO. Narisan kot lasten SVG — za eno črto ni
   razloga za dodatno knjižnico, poleg tega se tako brez težav prilagodi
   širini in temi.

   Vodoravna os je zaporedje obračunanih tekem (ne koledar), ker so tekme
   pogosto zgoščene v turnirske dneve in bi časovno merilo dalo prazne pasove.
   Datum je izpisan pod prvo in zadnjo točko ter ob izbrani točki. */
import { useState } from 'react'

import type { TockaGrafa } from '../api/tipi'

type Obdobje = 'vse' | '12m' | '3m'

const OBDOBJA: { kljuc: Obdobje; oznaka: string; meseci: number | null }[] = [
  { kljuc: 'vse', oznaka: 'Vse', meseci: null },
  { kljuc: '12m', oznaka: '12 mesecev', meseci: 12 },
  { kljuc: '3m', oznaka: '3 meseci', meseci: 3 },
]

const SIRINA = 720
const VISINA = 240
const ROB = { levo: 44, desno: 12, zgoraj: 14, spodaj: 26 }

export function GrafElo({ tocke }: { tocke: TockaGrafa[] }) {
  const [obdobje, nastaviObdobje] = useState<Obdobje>('vse')
  const [izbrana, nastaviIzbrano] = useState<number | null>(null)

  const filtrirane = filtrirajPoObdobju(tocke, obdobje)

  if (tocke.length === 0) {
    return <p className="obvestilo">Ni še obračunanih tekem, zato graf ELO še ni na voljo.</p>
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

  return (
    <div className="graf">
      <div className="graf__glava">
        <div className="graf__obdobja">
          {OBDOBJA.map((o) => (
            <button
              key={o.kljuc}
              className={'gumb gumb--majhen' + (obdobje === o.kljuc ? ' gumb--glavni' : '')}
              onClick={() => {
                nastaviObdobje(o.kljuc)
                nastaviIzbrano(null)
              }}
            >
              {o.oznaka}
            </button>
          ))}
        </div>
        <span className="graf__stevec">
          {filtrirane.length} {tekemTekst(filtrirane.length)}
        </span>
      </div>

      {filtrirane.length === 0 ? (
        <p className="obvestilo">V izbranem obdobju ni obračunanih tekem.</p>
      ) : (
        <>
          <svg
            className="graf__svg"
            viewBox={`0 0 ${SIRINA} ${VISINA}`}
            role="img"
            aria-label="Graf napredka klubskega ELO"
          >
            {oznakeY.map((v) => (
              <g key={v}>
                <line
                  className="graf__mreza"
                  x1={ROB.levo}
                  x2={SIRINA - ROB.desno}
                  y1={y(v)}
                  y2={y(v)}
                />
                <text className="graf__os" x={ROB.levo - 8} y={y(v) + 4} textAnchor="end">
                  {Math.round(v)}
                </text>
              </g>
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

            <text className="graf__os" x={ROB.levo} y={VISINA - 6}>
              {datum(filtrirane[0].kdaj)}
            </text>
            {filtrirane.length > 1 && (
              <text className="graf__os" x={SIRINA - ROB.desno} y={VISINA - 6} textAnchor="end">
                {datum(filtrirane[filtrirane.length - 1].kdaj)}
              </text>
            )}
          </svg>

          {podrobnost && (
            <p className="graf__podrobnost">
              <strong>{podrobnost.vrednost}</strong>
              <span
                className={
                  'graf__sprememba' +
                  (podrobnost.sprememba >= 0 ? ' graf__sprememba--plus' : ' graf__sprememba--minus')
                }
              >
                {podrobnost.sprememba >= 0 ? '+' : ''}
                {podrobnost.sprememba}
              </span>
              <span className="graf__opis">
                {datum(podrobnost.kdaj)}
                {podrobnost.nasprotnik ? ` · proti ${podrobnost.nasprotnik}` : ''}
                {podrobnost.ligaska ? ' · liga' : ''}
              </span>
            </p>
          )}
        </>
      )}
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

function tekemTekst(n: number): string {
  if (n === 1) return 'tekma'
  if (n === 2) return 'tekmi'
  if (n === 3 || n === 4) return 'tekme'
  return 'tekem'
}
