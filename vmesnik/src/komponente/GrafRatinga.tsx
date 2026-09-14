/* Graf napredka Turnirko ratinga. Narisan kot lasten SVG — za eno črto ni
   razloga za dodatno knjižnico, poleg tega se tako brez težav prilagodi
   širini in temi.

   Vodoravna os je zaporedje obračunanih tekem (ne koledar), ker so tekme
   pogosto zgoščene v turnirske dneve in bi časovno merilo dalo prazne pasove.
   Datum je izpisan pod prvo in zadnjo točko ter ob izbrani točki.

   Ta datum (in z njim izbrano obdobje) je dan TEKME, ne trenutek obračuna
   ratinga: pri uvoženi zgodovini so vsi obračuni nastali ob uvozu, zato bi
   po njem vse tekme padle v isti dan in nobeno obdobje ne bi odrezalo nič.

   Točka ni le prikaz: pove, s kom in na katerem tekmovanju je bila tekma
   odigrana, ob kliku pa stran skoči na to vrstico v seznamu tekem (prop
   "naTekmo"). Sicer je iz skoka rating nemogoče ugotoviti, kaj ga je povzročilo.

   Privzeto obdobje so trije meseci: gledalec pride po zadnjo formo, ne po
   celotno zgodovino, ta pa je pri uvoženih igralcih dolga tudi deset let.

   Oznake osi so HTML nad risalno ploskvijo, ne <text> v SVG: SVG jih pri
   raztegu ploskve na širino okvirja skalira skupaj z grafom (12 px bi na
   1360 px oknu postalo 14,6 px), poleg tega jih na telefonu ni mogoče
   preprosto skriti. */
import type { ReactNode } from 'react'
import { useState } from 'react'

import type { TockaGrafa } from '../api/tipi'
import { OZNAKE_RAZLOG } from '../api/tipi'

type Obdobje = 'vse' | '12m' | '6m' | '3m' | '30d'

/* Obdobje je omejeno bodisi z dnevi bodisi s koledarskimi meseci: "30 dni" je
   res 30 dni, "3 meseci" pa isti dan tri mesece nazaj (ne 90 dni). */
const OBDOBJA: { kljuc: Obdobje; oznaka: string; dni: number | null; meseci: number | null }[] = [
  { kljuc: '30d', oznaka: '30 dni', dni: 30, meseci: null },
  { kljuc: '3m', oznaka: '3 meseci', dni: null, meseci: 3 },
  { kljuc: '6m', oznaka: '6 mesecev', dni: null, meseci: 6 },
  { kljuc: '12m', oznaka: '1 leto', dni: null, meseci: 12 },
  { kljuc: 'vse', oznaka: 'Vse', dni: null, meseci: null },
]

/* Risalna ploskev je široka kot vsebinski okvir (1280 px minus 2 x 40 px
   odmika), da se graf razteza čez celo sekcijo — kot na maketi. */
const SIRINA = 1120
const VISINA = 280
const ROB = { levo: 56, desno: 16, zgoraj: 16, spodaj: 32 }

/* "otroci" so bloki, ki sodijo v isto sekcijo pod graf (npr. pričakovan proti
   doseženemu izkupičku) — sekcijo namreč izriše ta komponenta, ker izbirnik
   obdobja stoji v njeni naslovni vrstici.

   "naTekmo" pokliče stran, ko gledalec klikne točko: graf pove, katera tekma
   je to, skok po seznamu tekem pa je stvar strani (graf ne ve, kje na strani
   seznam je). Točka brez tekmovanja para v seznamu nima (postavitveni rating),
   zato ni klikljiva. */
export function GrafRatinga({
  tocke,
  naTekmo,
  children,
}: {
  tocke: TockaGrafa[]
  naTekmo?: (idTekme: number, ligaska: boolean) => void
  children?: ReactNode
}) {
  const [obdobje, nastaviObdobje] = useState<Obdobje>('3m')
  const [izbrana, nastaviIzbrano] = useState<number | null>(null)

  const filtrirane = filtrirajPoObdobju(tocke, obdobje)

  if (tocke.length === 0) {
    return (
      <div>
        <div className="naslovna-vrstica">
          <h2>Napredek ratinga</h2>
        </div>
        <p className="obvestilo">Ni še obračunanih tekem, zato graf ratinga še ni na voljo.</p>
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

  const skok = (t: TockaGrafa) => (naTekmo && t.idTekme !== null && t.tekmovanje !== null
    ? () => naTekmo(t.idTekme as number, t.ligaska)
    : null)

  /* Izbirnik obdobja stoji v naslovni vrstici sekcije, zato komponenta izriše
     celo sekcijo — tako je vse v eni vrstici, kot zahteva maketa. */
  return (
    <div className="graf">
      <div className="naslovna-vrstica">
        <h2>Napredek ratinga</h2>
        <label className="krmilo-izbor">
          <span className="samo-za-bralnik">Obdobje grafa</span>
          <span className="krmilo-izbor__oznaka" aria-hidden="true">
            {OBDOBJA.find((o) => o.kljuc === obdobje)?.oznaka} · {filtrirane.length}
            <span className="krmilo-izbor__puscica">▾</span>
          </span>
          <select
            className="krmilo-izbor__polje"
            value={obdobje}
            onChange={(dogodek) => {
              nastaviObdobje(dogodek.target.value as Obdobje)
              nastaviIzbrano(null)
            }}
          >
            {OBDOBJA.map((o) => (
              <option key={o.kljuc} value={o.kljuc}>
                {o.oznaka}
              </option>
            ))}
          </select>
        </label>
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
            <span className="graf__oznaka graf__oznaka--prvi">{datum(casTocke(filtrirane[0]))}</span>
            {filtrirane.length > 1 && (
              <span className="graf__oznaka graf__oznaka--zadnji">
                {datum(casTocke(filtrirane[filtrirane.length - 1]))}
              </span>
            )}
            <svg
              className="graf__svg"
              viewBox={`0 0 ${SIRINA} ${VISINA}`}
              role="img"
              aria-label="Graf napredka Turnirko ratinga"
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

              {filtrirane.map((t, i) => {
                const naKlik = skok(t)
                return (
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
                    onClick={naKlik ?? undefined}
                    onKeyDown={
                      naKlik
                        ? (e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault()
                              naKlik()
                            }
                          }
                        : undefined
                    }
                    role={naKlik ? 'button' : undefined}
                    aria-label={naKlik ? `Pokaži tekmo v seznamu: ${opisTocke(t)}` : undefined}
                    tabIndex={0}
                  >
                    <title>{opisTocke(t)}</title>
                  </circle>
                )
              })}
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
                · {datum(casTocke(podrobnost))}
                {podrobnost.nasprotnik ? ` · proti ${podrobnost.nasprotnik}` : ''}
                {/* Del (dogodek oz. kolo s parom ekip) je samo v namigu in v
                    vrstici seznama: imena uvoženih turnirjev so dolga cel
                    stavek in bi vrstico na telefonu raztegnila čez pol
                    zaslona. */}
                {podrobnost.tekmovanje
                  ? ` · ${podrobnost.tekmovanje}`
                  : podrobnost.ligaska
                    ? ' · liga'
                    : podrobnost.razlog
                      ? ` · ${OZNAKE_RAZLOG[podrobnost.razlog]}`
                      : ''}
              </span>
            </p>
          )}

          {/* Od kod je številka prišla. Igralec, ki vidi »+27«, ima pravico
              vedeti, kaj ga je premaknilo — in prav tu je edino mesto, kjer
              so vse štiri sestavine hkrati znane. */}
          {podrobnost && (
            <p
              className={
                'graf__razlaga'
                + (podrobnost.nacin === 'KORAK' ? '' : ' graf__razlaga--poved')
              }
            >
              {razlaga(podrobnost)}
            </p>
          )}
        </>
      )}

      {children}
    </div>
  )
}

function filtrirajPoObdobju(tocke: TockaGrafa[], obdobje: Obdobje): TockaGrafa[] {
  const o = OBDOBJA.find((x) => x.kljuc === obdobje)
  if (!o || (o.dni === null && o.meseci === null)) return tocke
  const meja = new Date()
  if (o.dni !== null) meja.setDate(meja.getDate() - o.dni)
  else meja.setMonth(meja.getMonth() - (o.meseci as number))
  return tocke.filter((t) => new Date(casTocke(t)) >= meja)
}

/* Dan, na katerega sprememba velja; `kdaj` je zasilni izhod za stare zapise. */
function casTocke(t: TockaGrafa): string {
  return t.datum ?? t.kdaj
}

/* Razlaga spremembe v eni vrstici. Obrazec je zapisan tako, kot se bere:
     +27 = K 64 × 1,37 (nizi) × 0,75 (klubsko) × (1 − 0,58)
   Uvrstitev, postavitev, odbitek in zunanja uvrstitev obrazca koraka ne
   uporabljajo, zato ima vsak svoj stavek - prazna vrstica bi bila slabša od
   povedi. */
function razlaga(t: TockaGrafa): string {
  if (t.nacin === 'POSTAVITEV') {
    return 'Postavitev ratinga: vrednost je določil organizator, ni izračunana iz tekem.'
  }
  if (t.nacin === 'NEAKTIVNOST') {
    return 'Odbitek za neaktivnost: po pol leta brez tekme −10, po letu skupno −25, po dveh letih skupno −40.'
  }
  if (t.nacin === 'UVRSTITEV') {
    return 'Uvrstitev novinca: rating se prvi dan ne sešteva po tekmah, ampak se vsakič znova izračuna iz vseh izidov tega dne.'
  }
  /* Zunanja uvrstitev: vir in pojasnilo sta obvezna, zato ju razlaga izpiše
     oba — to je edino, kar ročni poseg loči od samovolje. */
  if (t.nacin === 'ZUNANJA_UVRSTITEV') {
    return (
      `Zunanja uvrstitev: ${t.vrednost} po viru »${t.vir ?? 'ni naveden'}«.`
      + (t.pojasnilo ? ` ${t.pojasnilo}` : '')
    )
  }
  const r = t.razclenitev
  if (!r) return ''
  const znak = t.sprememba >= 0 ? '+' : '−'
  return `${znak}${Math.abs(t.sprememba)} = K ${r.k}`
    + ` × ${stevilo(r.margina)} (nizi)`
    + (r.teza < 1 ? ` × ${stevilo(r.teza)} (teža tekmovanja)` : '')
    + ` × (${r.tocke} − ${stevilo(r.pricakovano)})`
}

/* Dve decimalki z vejico - tako, kot se piše v slovenščini. */
function stevilo(v: number): string {
  return v.toFixed(2).replace('.', ',')
}

/* Isti zapis za nativni namig (<title>) in za bralnik zaslona. */
function opisTocke(t: TockaGrafa): string {
  const deli = [
    datum(casTocke(t)),
    `${t.vrednost} (${t.sprememba >= 0 ? '+' : '−'}${Math.abs(t.sprememba)})`,
  ]
  if (t.nasprotnik) deli.push(`proti ${t.nasprotnik}`)
  if (t.tekmovanje) deli.push(t.tekmovanje)
  else if (t.ligaska) deli.push('liga')
  else if (t.razlog) deli.push(OZNAKE_RAZLOG[t.razlog])
  if (t.del) deli.push(t.del)
  return deli.join(' · ')
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
