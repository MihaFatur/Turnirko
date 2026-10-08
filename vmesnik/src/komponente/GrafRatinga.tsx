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

   Graf ima osi: navpično z vrednostmi ratinga na okroglih številkah (po 10,
   25, 50, 100 …) in vodoravno z datumi nekaj tekem. Prej sta bili le dve
   oznaki ob robu in na telefonu nobena (pod 768 px so bile skrite), graf pa
   je bil tam visok 84 px - črta brez merila.

   Risalna ploskev ima mere okvirja, v katerem stoji (ResizeObserver), in se ne
   razteza: pri 1120 × 280 raztegnjenih na 335 px telefona je bil graf
   sploščen, oznake pa nečitljive. Oznake osi so HTML nad ploskvijo (mono
   pisava strani). */
import type { ReactNode } from 'react'
import { useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

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

/* Širina pred prvo meritvijo okvirja: vsebinski okvir namizja (1280 px minus
   2 x 40 px odmika). */
const PRIVZETA_SIRINA = 1120
/* Ozka ploskev (telefon) je nižja, a ne sploščena - pod to širino 220 px. */
const OZKA_SIRINA = 600
const ROB = { levo: 48, desno: 12, zgoraj: 12, spodaj: 28 }
/* Približna širina datumske oznake na vodoravni osi (»12. 9. 25«) z razmikom. */
const PROSTOR_OZNAKE_X = 110

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
  const ovoj = useRef<HTMLDivElement>(null)
  const [sirina, nastaviSirino] = useState(PRIVZETA_SIRINA)

  const filtrirane = filtrirajPoObdobju(tocke, obdobje)
  const prikazan = filtrirane.length > 0

  /* Mere ploskve so mere okvirja (glej opombo na vrhu). Pred izrisom, da
     prvi prikaz ni v napačnem razmerju. */
  useLayoutEffect(() => {
    const okvir = ovoj.current
    if (!okvir) return
    const izmeri = () => nastaviSirino(Math.max(240, Math.round(okvir.clientWidth)))
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(okvir)
    return () => opazovalec.disconnect()
  }, [prikazan])
  const visina = sirina < OZKA_SIRINA ? 220 : 280

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

  const risalnaSirina = sirina - ROB.levo - ROB.desno
  const risalnaVisina = visina - ROB.zgoraj - ROB.spodaj

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

  const dno = ROB.zgoraj + risalnaVisina
  const oznakeY = okrogleVrednosti(spodaj, zgoraj, visina < 250 ? 4 : 5)
  const oznakeX = izbraneTekme(
    filtrirane,
    Math.max(2, Math.min(6, Math.floor(risalnaSirina / PROSTOR_OZNAKE_X))),
  )
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
          <div className="graf__ovoj" ref={ovoj}>
            {/* Navpična os: rating. */}
            {oznakeY.map((v) => (
              <span
                key={v}
                className="graf__oznaka graf__oznaka--y"
                style={{ top: y(v), width: ROB.levo - 8 }}
              >
                {v}
              </span>
            ))}
            {/* Vodoravna os: tekme po vrsti, pod nekaj izmed njih datum. Skrajni
                oznaki sta poravnani ob rob, da ne štrlita iz grafa. */}
            {oznakeX.map((i, k) => (
              <span
                key={i}
                className={
                  'graf__oznaka graf__oznaka--x' +
                  (k === 0 && oznakeX.length > 1 ? ' graf__oznaka--x-prva' : '') +
                  (k === oznakeX.length - 1 && oznakeX.length > 1 ? ' graf__oznaka--x-zadnja' : '')
                }
                style={{ left: x(i), top: dno + 8 }}
              >
                {datumKratko(casTocke(filtrirane[i]))}
              </span>
            ))}
            <svg
              className="graf__svg"
              width={sirina}
              height={visina}
              viewBox={`0 0 ${sirina} ${visina}`}
              role="img"
              aria-label="Graf napredka Turnirko ratinga"
            >
              {oznakeY.map((v) => (
                <line
                  key={v}
                  className="graf__mreza"
                  x1={ROB.levo}
                  x2={sirina - ROB.desno}
                  y1={y(v)}
                  y2={y(v)}
                />
              ))}
              {/* Osi in zarezi ob oznakah. */}
              <line className="graf__os" x1={ROB.levo} x2={ROB.levo} y1={ROB.zgoraj} y2={dno} />
              <line className="graf__os" x1={ROB.levo} x2={sirina - ROB.desno} y1={dno} y2={dno} />
              {oznakeY.map((v) => (
                <line key={v} className="graf__os" x1={ROB.levo - 4} x2={ROB.levo} y1={y(v)} y2={y(v)} />
              ))}
              {oznakeX.map((i) => (
                <line key={i} className="graf__os" x1={x(i)} x2={x(i)} y1={dno} y2={dno + 4} />
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
                {/* »proti« bi zahteval dajalnik (»proti Alešu Sešlu«), imen pa ne
                    sklanjamo - zato »nasprotnik« in ime v imenovalniku. */}
                {podrobnost.nasprotnik ? ` · nasprotnik ${podrobnost.nasprotnik}` : ''}
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

          {/* Sprememba, ki ni nastala iz tekme po običajni poti (postavitev,
              odbitek, uvrstitev), dobi pod točko poved — brez nje bi se
              skok brez nasprotnika bral kot napaka. Navadna tekma razlage
              nima. */}
          {podrobnost && razlaga(podrobnost) && (
            <p className="graf__razlaga">
              {razlaga(podrobnost)}
              {/* Uvrstitev je skok, ki ga gledalec najmanj razume - na javni
                  razlagi ga lahko preizkusi s svojimi izidi. */}
              {podrobnost.nacin === 'UVRSTITEV' && (
                <>
                  {' '}
                  <Link to="/o-ratingu#prvi-dan">Preizkusi s svojimi izidi →</Link>
                </>
              )}
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
  // tekma brez znanega datuma ne sodi v nobeno omejeno obdobje: ne vemo, ali
  // je bila odigrana v njem
  return tocke.filter((t) => {
    const cas = casTocke(t)
    return cas !== null && new Date(cas) >= meja
  })
}

/* Dan, na katerega sprememba velja. Brez njega je samo tekma, ki ji vir
   datuma ne pove (uvožene lige 2024/25 brez terminov): takrat datum NI
   znan. Trenutek obračuna (`kdaj`) tu ne sme nastopiti namesto njega -
   pri preračunu je to današnji dan in tekma iz leta 2024 bi na grafu
   stala kot odigrana danes. */
function casTocke(t: TockaGrafa): string | null {
  return t.datum
}

/* Poved pod točko za spremembe, ki niso navaden korak po tekmi. Obrazca
   koraka (K × teža × izid) pod grafom ni: gledalcu je bil šum,
   sestavine pa ostanejo zapisane v dnevniku. */
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
  return ''
}

/* Isti zapis za nativni namig (<title>) in za bralnik zaslona. */
function opisTocke(t: TockaGrafa): string {
  const deli = [
    datum(casTocke(t)),
    `${t.vrednost} (${t.sprememba >= 0 ? '+' : '−'}${Math.abs(t.sprememba)})`,
  ]
  if (t.nasprotnik) deli.push(`nasprotnik ${t.nasprotnik}`)
  if (t.tekmovanje) deli.push(t.tekmovanje)
  else if (t.ligaska) deli.push('liga')
  else if (t.razlog) deli.push(OZNAKE_RAZLOG[t.razlog])
  if (t.del) deli.push(t.del)
  return deli.join(' · ')
}

/* Vrednosti navpične osi: okrogle številke (večkratniki 5, 10, 20, 25, 50,
   100 …) znotraj razpona, največ »najvec« oznak. Izbran je najmanjši korak,
   ki še gre - čim gostejše merilo, a ne gneča. */
function okrogleVrednosti(spodaj: number, zgoraj: number, najvec: number): number[] {
  const koraki = [5, 10, 20, 25, 50, 100, 200, 250, 500, 1000]
  const korak =
    koraki.find((k) => Math.floor(zgoraj / k) - Math.ceil(spodaj / k) + 1 <= najvec) ?? 1000
  const vrednosti: number[] = []
  for (let v = Math.ceil(spodaj / korak) * korak; v <= zgoraj; v += korak) vrednosti.push(v)
  return vrednosti
}

/* Katere tekme dobijo datum na vodoravni osi: prva, zadnja in enakomerno
   razporejene vmes (po vrsti, ker je os zaporedje tekem). Tekma brez
   znanega datuma oznake ne dobi - »datum ni znan« pod osjo ne pove nič. */
function izbraneTekme(tocke: TockaGrafa[], najvec: number): number[] {
  const n = tocke.length
  if (n === 0) return []
  const stevilo = Math.min(najvec, n)
  const indeksi =
    stevilo === 1
      ? [0]
      : Array.from({ length: stevilo }, (_, k) => Math.round((k * (n - 1)) / (stevilo - 1)))
  return [...new Set(indeksi)].filter((i) => casTocke(tocke[i]) !== null)
}

/* Kratek datum za oznako osi (»12. 9. 25«) - polni »12. 9. 2025« je za
   telefon predolg, letnica pa mora ostati: graf sega čez več sezon. */
function datumKratko(iso: string | null): string {
  if (iso === null) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getDate()}. ${d.getMonth() + 1}. ${String(d.getFullYear()).slice(2)}`
}

function datum(iso: string | null): string {
  if (iso === null) return 'datum ni znan'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
