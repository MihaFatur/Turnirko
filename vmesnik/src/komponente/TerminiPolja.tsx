/* Termini kol v obrazcu nove lige (design_handoff_termini_prehodi, 2b).

   To je seme razporeda, ne seznam datumov: ekip (in s tem števila kol) ob
   ustvarjanju lige še ni, zato žreb iz prvega kola, razmika in števila srečanj
   izračuna vse datume. Predogled prvih treh kol organizatorju pokaže, kako bo
   razpored izgledal, še preden ima liga ekipe.

   Stanje ima obrazec lige (LigaObrazecOkno); to polje samo prikazuje vrednosti
   in javlja spremembe, da ga lahko uporabi tudi prikazni harness (dev/PosnetkiOkna). */
import { danKratica, kratkoDatum, prestej, srecanjTekst } from '../pomozno/terminiKola'
import { StevilskiKorak } from './StevilskiKorak'
import { StevilskoPolje } from './StevilskoPolje'

const NAJVEC_DNI = 365
/* Predogled pokaže prva tri kola, ne glede na to, koliko jih liga ima. */
const KOLA_V_PREDOGLEDU = 3

interface Lastnosti {
  datum: string
  naDatum: (datum: string) => void
  /* Ura prvega kola, kadar je srečanje eno na kolo. */
  ura: string
  naUro: (ura: string) => void
  razmik: number
  naRazmik: (razmik: number) => void
  /* Razmik po meri: »Po meri« je aktivno tudi, če je razmik 7 ali 14 po naključju. */
  poMeri: boolean
  naPoMeri: (poMeri: boolean) => void
  /* Koliko srečanj ima vsaka ekipa v kolu; 1 = navadna liga. */
  srecanj: number
  naSrecanj: (srecanj: number) => void
  /* Ure srečanj kola, po vrsti; dolžina je vsaj `srecanj`, ko je več kot eno. */
  ure: string[]
  naUroSrecanja: (mesto: number, ura: string) => void
}

export function TerminiPolja({
  datum,
  naDatum,
  ura,
  naUro,
  razmik,
  naRazmik,
  poMeri,
  naPoMeri,
  srecanj,
  naSrecanj,
  ure,
  naUroSrecanja,
}: Lastnosti) {
  const brezDatuma = datum === ''
  const onemogocen = brezDatuma ? ' termini__obrazec-blok--onemogocen' : ''
  const urePrvih = srecanj === 1 ? [ura] : ure.slice(0, srecanj)

  return (
    <div role="group" aria-label="Termini kol" className="obrazec__skupina termini__obrazec">
      <span className="termini__obrazec-naslov">Termini kol</span>

      <div className="termini__obrazec-blok">
        <span className="oznaka-polja">Kdaj je prvo kolo</span>
        <div className="termini__obrazec-vrstica">
          <input type="date" className="termini__obrazec-datum" aria-label="Datum prvega kola"
            value={datum} onChange={(d) => naDatum(d.target.value)} />
          {srecanj === 1 && (
            <>
              <span className="termini__obrazec-ob">ob</span>
              <input type="time" className="termini__obrazec-ura" aria-label="Ura prvega kola"
                value={ura} onChange={(d) => naUro(d.target.value)} />
            </>
          )}
        </div>
      </div>

      <div className={'termini__obrazec-blok' + onemogocen}>
        <span className="oznaka-polja">Vsako naslednje kolo</span>
        <div className="termini__obrazec-razmik">
          <div className="izbirnik" role="group" aria-label="Razmik med kola">
            <button type="button" className={izbirnikGumb(!poMeri && razmik === 7)}
              aria-pressed={!poMeri && razmik === 7}
              onClick={() => { naRazmik(7); naPoMeri(false) }}>
              Vsak teden
            </button>
            <button type="button" className={izbirnikGumb(!poMeri && razmik === 14)}
              aria-pressed={!poMeri && razmik === 14}
              onClick={() => { naRazmik(14); naPoMeri(false) }}>
              Na 2 tedna
            </button>
            <button type="button" className={izbirnikGumb(poMeri)} aria-pressed={poMeri}
              onClick={() => naPoMeri(true)}>
              Po meri
            </button>
          </div>
          {poMeri && (
            <div className="termini__obrazec-po-meri">
              <StevilskoPolje className="termini__obrazec-dni" aria-label="Razmik v dneh"
                najvec={NAJVEC_DNI} vrednost={String(razmik)}
                naSpremembo={(v) => naRazmik(Number(v))} />
              <span className="termini__obrazec-beseda">dni po prejšnjem</span>
            </div>
          )}
        </div>
      </div>

      <div className={'termini__obrazec-blok' + onemogocen}>
        <span className="oznaka-polja">Srečanj vsake ekipe na kolo</span>
        <div className="termini__obrazec-vrstica">
          <StevilskiKorak vrednost={srecanj} najmanj={1} najvec={10}
            naSpremembo={naSrecanj}
            oznaka="Število srečanj na kolo" oznakaManj="Manj srečanj" oznakaVec="Več srečanj" />
          <span className="termini__obrazec-beseda">{srecanjTekst(srecanj)}</span>
        </div>
        {srecanj > 1 && (
          <div className="termini__ure-mreza">
            {Array.from({ length: srecanj }, (_, i) => (
              <label key={i} className="obrazec__polje">
                <span>{i + 1}. srečanje</span>
                <input type="time" value={ure[i] ?? ''}
                  onChange={(d) => naUroSrecanja(i, d.target.value)} />
              </label>
            ))}
          </div>
        )}
        <p className="termini__obrazec-pojasnilo">
          {srecanj === 1
            ? 'Vsa srečanja kola se začnejo ob isti uri.'
            : `Vsaka ekipa v kolu odigra ${srecanj} ${srecanjTekst(srecanj)}, ob vsaki uri vse ekipe hkrati; kol je zato ${srecanj === 2 ? 'pol manj' : `${srecanj}-krat manj`}. Prazna ura pomeni, da ura ni določena.`}
        </p>
      </div>

      <div className="termini__predogled">
        <span className="termini__predogled-naslov">Tako bo videti razpored</span>
        {brezDatuma ? (
          <p className="termini__predogled-konec">
            Brez datuma prvega kola razpored pri neodigranih kolih pokaže samo oznako »razpored«.
            Datume lahko vpišeš tudi pozneje, na strani lige pod »Termini«.
          </p>
        ) : (
          <>
            {Array.from({ length: KOLA_V_PREDOGLEDU }, (_, i) => {
              const dan = prestej(datum, i * razmik)
              return (
                <div key={i} className="termini__predogled-vrstica">
                  <span className="termini__kolo">{i + 1}. kolo</span>
                  <b>{danKratica(dan)} {kratkoDatum(dan)}</b>
                  <span>
                    {srecanj === 1
                      ? (urePrvih[0] || 'ura ni določena')
                      : urePrvih.map((u) => u || '—').join(' · ')}
                  </span>
                </div>
              )
            })}
            <p className="termini__predogled-konec">
              … in tako naprej; število kol in vse datume izračuna žreb.
            </p>
          </>
        )}
      </div>
    </div>
  )
}

function izbirnikGumb(aktiven: boolean): string {
  return 'izbirnik__gumb' + (aktiven ? ' izbirnik__gumb--aktiven' : '')
}
