/* Izločilni del v izpisu rezultatov: izločilna mreža in žrebi SV regije za vsa mesta.
   Okvir tekme je za oba isti: dve vrstici imen z dobljenimi nizi, zmagovalec krepek, pod
   okvirjem v tanki vrstici točke po nizih, kadar so vpisane in izbrane. Klub stoji samo
   tam, kjer igralec v mrežo vstopi. Črno-belo; mere pridejo izračunane
   (pomozno/izpisRezultatov), črte drevesa SV pa so postavljene v mm. */
import type { CSSProperties } from 'react'

import type { TekmaDto } from '../api/tipi'
import {
  MERE_SV,
  type BlokZreba,
  type ListMreze,
  type ListSv,
  type MerilaOkvirja,
  type OdsekNaListu,
} from '../pomozno/izpisRezultatov'
import {
  oznakaIzida,
  stranTekme,
  tockeTekme,
  type MrezaStran,
} from '../pomozno/zrebMreze'
import type { Postavljena } from './SvDrevo'

function slogOkvirja(merila: MerilaOkvirja): CSSProperties {
  return {
    '--stran-visina': `${merila.visinaStrani}mm`,
    '--stran-pisava': `${merila.pisava}pt`,
  } as CSSProperties
}

/* ---------- Okvir tekme ---------- */

function OkvirTekme({
  strani,
  oznaka,
  tocke,
  sKlubom,
  pisava,
  sirina,
}: {
  strani: [MrezaStran, MrezaStran]
  oznaka: string | null
  /* Prazno, kadar točke niso vpisane ali niso izbrane. */
  tocke: string
  sKlubom: boolean
  pisava: number
  /* Širina okvirja v mm: po njej se par skrajša na priimka, kadar ne gre cel. */
  sirina: number
}) {
  return (
    <div className="izpis-okvir">
      <StranOkvirja stran={strani[0]} oznaka={oznaka} sKlubom={sKlubom} pisava={pisava} sirina={sirina} />
      <StranOkvirja stran={strani[1]} oznaka={oznaka} sKlubom={sKlubom} pisava={pisava} sirina={sirina} />
      {tocke && (
        <div className="izpis-okvir__tocke" style={{ fontSize: `${pisavaTock(tocke, sirina).toFixed(2)}pt` }}>
          {tocke}
        </div>
      )}
    </div>
  )
}

/* Vrstica točk mora iti v širino okvirja: znak pisave Plex Mono je 0,58 višine (z
   zoženim razmikom), 1 pt = 0,353 mm. Pod 4,5 pt ni več berljivo; tolikšna je samo tekma
   na 7 nizov v najožjem stolpcu, kjer se konec vrstice raje odreže. */
function pisavaTock(tocke: string, sirina: number): number {
  return Math.max(4.5, Math.min(6, sirina / (tocke.length * 0.58 * 0.353)))
}

/* Ocena širine imena v mm: povprečna črka Karla je ~0,52 višine pisave (krepka je
   širša, zato vzamemo njo), 1 pt = 0,353 mm. */
function sirinaImena(ime: string, pisava: number): number {
  return ime.length * pisava * 0.353 * 0.52
}

/* Prostor v okvirju za ime in klub: brez odmikov in stolpca z nizi. */
const ZASEDENO_V_OKVIRJU_MM = 6.4
/* Dolgo ime se najprej zmanjša, šele nato odreže; manjše ni več berljivo. */
const NAJMANJSE_MERILO_IMENA = 0.72
/* Klub, ki bi dobil manj od tega, je samo še odrezan začetek besede. */
const NAJMANJ_ZA_KLUB_MM = 9

function imeZaOkvir(stran: MrezaStran, pisava: number, prostor: number): string | null {
  if (!stran.ime || !stran.kratko) return stran.ime
  return sirinaImena(stran.ime, pisava) * NAJMANJSE_MERILO_IMENA > prostor ? stran.kratko : stran.ime
}

function StranOkvirja({
  stran,
  oznaka,
  sKlubom,
  pisava,
  sirina,
}: {
  stran: MrezaStran
  oznaka: string | null
  sKlubom: boolean
  pisava: number
  sirina: number
}) {
  const prostor = sirina - ZASEDENO_V_OKVIRJU_MM
  const ime = imeZaOkvir(stran, pisava, prostor)
  const ocena = ime ? sirinaImena(ime, pisava) : 0
  const merilo = ocena > prostor ? Math.max(NAJMANJSE_MERILO_IMENA, prostor / ocena) : 1
  const zaKlub = prostor - ocena * merilo - 1
  return (
    <div
      className={
        'izpis-okvir__stran' +
        (stran.zmagovalec ? ' izpis-okvir__stran--zmagovalec' : '') +
        (stran.prost ? ' izpis-okvir__stran--prost' : '')
      }
    >
      <span className="izpis-okvir__ime" style={merilo < 1 ? { fontSize: `${(pisava * merilo).toFixed(2)}pt` } : undefined}>
        {stran.prost ? 'prost' : (ime ?? (stran.napis ? <i>{stran.napis}</i> : null))}
      </span>
      {sKlubom && stran.klub && !stran.prost && zaKlub >= NAJMANJ_ZA_KLUB_MM && (
        <span className="izpis-okvir__klub">{stran.klub}</span>
      )}
      {oznaka && stran.nizi !== null && !stran.zmagovalec && (
        <small className="izpis-okvir__oznaka">{oznaka}</small>
      )}
      {stran.nizi !== null && <span className="izpis-okvir__nizi">{stran.nizi}</span>}
    </div>
  )
}

/* ---------- Izločilna mreža ---------- */

/* Vodoravni razmik med okvirji je 2 x spoj; navpičnica stoji na sredi. */
const SPOJ_MREZE_MM = 3

export function IzpisMreze({ list: podatki }: { list: ListMreze }) {
  const { list, stolpci, sKlubom, sTockami, merila, vrstica } = podatki
  const merilaMreze = {
    '--spoj': `${SPOJ_MREZE_MM}mm`,
    '--vrstic': list.vrstic,
    '--stolpcev': stolpci.length,
    '--mreza-vrstica': `${vrstica}mm`,
    ...slogOkvirja(merila),
  } as CSSProperties
  const predloga = stolpci.map((s) => `${s}mm`).join(' ')
  const zmagovalec = stolpci.length

  return (
    <div className="izpis-mreza" style={merilaMreze}>
      <div className="zreb-mreza__naslovi" style={{ gridTemplateColumns: predloga }}>
        {list.kola.map((kolo, indeks) => (
          <h2
            key={kolo.kolo}
            className={'zreb-mreza__naslov' + (indeks === 0 ? ' zreb-mreza__naslov--prvi' : '')}
          >
            {kolo.naslov}
          </h2>
        ))}
        <h2 className="zreb-mreza__naslov zreb-mreza__naslov--prvak">{list.prvak.naslov}</h2>
      </div>

      <div className="zreb-mreza__mreza" style={{ gridTemplateColumns: predloga }}>
        {list.kola.map((kolo, indeks) =>
          kolo.tekme.map((tekma) => (
            <div
              key={tekma.id}
              className={
                'zreb-mreza__tekma' +
                (indeks === 0 ? ' zreb-mreza__tekma--prvi' : '') +
                (tekma.smer ? ` zreb-mreza__tekma--${tekma.smer}` : ' zreb-mreza__tekma--zadnja')
              }
              style={{ gridColumn: indeks + 1, gridRow: `${tekma.vrstica} / span ${tekma.razpon}` }}
            >
              <OkvirTekme
                strani={tekma.strani}
                oznaka={tekma.oznaka}
                tocke={sTockami ? tekma.tocke : ''}
                sKlubom={sKlubom && indeks === 0}
                pisava={merila.pisava}
                sirina={stolpci[indeks] - 2 * SPOJ_MREZE_MM}
              />
            </div>
          )),
        )}

        <div className="zreb-mreza__prvak" style={{ gridColumn: zmagovalec, gridRow: '1 / -1' }}>
          <div className="zreb-mreza__prvak-crta">{list.prvak.ime}</div>
          {list.tretje && (
            <div className="zreb-mreza__tretje izpis-mreza__tretje">
              <p className="zreb-mreza__tretje-naslov">Za 3. mesto</p>
              <OkvirTekme
                strani={list.tretje.strani}
                oznaka={list.tretje.oznaka}
                tocke={sTockami ? list.tretje.tocke : ''}
                sKlubom={false}
                pisava={merila.pisava}
                sirina={stolpci[stolpci.length - 1]}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/* ---------- Žrebi SV regije ---------- */

export function IzpisSv({ list }: { list: ListSv }) {
  return (
    <div
      className="izpis-sv"
      style={
        {
          '--sv-naslov': `${MERE_SV.naslovZreba}mm`,
          '--sv-odsek': `${MERE_SV.naslovOdseka}mm`,
          '--sv-glava': `${MERE_SV.glavaStolpcev}mm`,
          '--sv-med-odseki': `${MERE_SV.medOdseki}mm`,
          '--sv-med-pasovoma': `${MERE_SV.medPasovoma}mm`,
          '--sv-med-zrebi': `${MERE_SV.medZrebi}mm`,
        } as CSSProperties
      }
    >
      {list.zrebi.map((blok) => (
        <section key={blok.kljuc} className="izpis-sv__zreb" style={slogOkvirja(blok.merila)}>
          <h2 className="izpis-sv__naslov">{blok.naslov}</h2>
          <div className="izpis-sv__pasova">
            <div className="izpis-sv__pas" style={{ width: `${blok.sirinaLevo}mm` }}>
              {blok.levo.map((o) => (
                <OdsekZreba key={o.odsek.od} odsek={o} blok={blok} />
              ))}
            </div>
            {blok.desno.length > 0 && (
              <div className="izpis-sv__pas">
                {blok.desno.map((o) => (
                  <OdsekZreba key={o.odsek.od} odsek={o} blok={blok} />
                ))}
              </div>
            )}
          </div>
        </section>
      ))}
    </div>
  )
}

function OdsekZreba({ odsek: naListu, blok }: { odsek: OdsekNaListu; blok: BlokZreba }) {
  const { odsek, glavni } = naListu
  const { vrstica, stolpec, uvrstitev, sKlubom, sTockami, merila } = blok
  const stolpcev = odsek.stolpci.length
  /* Klub samo v prvem stolpcu glavne poti: tam igralec vstopi v žreb. */
  const dodatek = glavni && sKlubom ? MERE_SV.klub : 0
  const levo = (s: number) => (s === 0 ? 0 : dodatek + s * stolpec)
  const sirinaStolpca = (s: number) => stolpec + (s === 0 ? dodatek : 0)
  const sirinaOkvirja = (s: number) => sirinaStolpca(s) - MERE_SV.spoj
  const y = (enot: number) => enot * vrstica
  const idTekme = new Map(odsek.tekme.map((p) => [p.tekma.id, p]))
  const predloga = [...odsek.stolpci.map((_, s) => `${sirinaStolpca(s)}mm`), `${uvrstitev}mm`].join(' ')

  return (
    <section className="izpis-sv__odsek" style={{ width: `${naListu.sirina}mm` }}>
      <h3 className="izpis-sv__odsek-naslov">{odsek.naslov}</h3>
      {stolpcev > 1 && (
        <div className="izpis-sv__stolpci" style={{ gridTemplateColumns: predloga }}>
          {odsek.stolpci.map((s, i) => (
            <div key={i}>{s.glava}</div>
          ))}
          <div>Uvrstitev</div>
        </div>
      )}
      <div className="izpis-sv__platno" style={{ height: `${y(odsek.visina)}mm` }}>
        {odsek.tekme.map((p) => {
          const viri = [p.tekma.idIzvorZmagovalca1, p.tekma.idIzvorZmagovalca2]
            .map((id) => (id !== null ? idTekme.get(id) : undefined))
            .filter((v): v is Postavljena => v !== undefined)
          if (viri.length === 0) return null
          const ys = viri.map((v) => v.y)
          const zgoraj = Math.min(...ys, p.y)
          const spodaj = Math.max(...ys, p.y)
          const x = levo(p.stolpec) - MERE_SV.spoj / 2
          return (
            <span key={`crte-${p.tekma.id}`}>
              {viri.map((v) => (
                <i
                  key={v.tekma.id}
                  className="izpis-sv__crta"
                  style={{
                    left: `${levo(v.stolpec) + sirinaOkvirja(v.stolpec)}mm`,
                    top: `${y(v.y)}mm`,
                    width: `${MERE_SV.spoj / 2}mm`,
                  }}
                />
              ))}
              <i
                className="izpis-sv__crta izpis-sv__crta--navpicna"
                style={{ left: `${x}mm`, top: `${y(zgoraj)}mm`, height: `${y(spodaj - zgoraj)}mm` }}
              />
              <i
                className="izpis-sv__crta"
                style={{ left: `${x}mm`, top: `${y(p.y)}mm`, width: `${MERE_SV.spoj / 2}mm` }}
              />
            </span>
          )
        })}

        {odsek.tekme.map((p) => (
          <div
            key={p.tekma.id}
            className="izpis-sv__tekma"
            style={{
              left: `${levo(p.stolpec)}mm`,
              top: `${y(p.y)}mm`,
              width: `${sirinaOkvirja(p.stolpec)}mm`,
            }}
          >
            <OkvirTekme
              strani={[stranTekme(p.tekma, 1, null), stranTekme(p.tekma, 2, null)]}
              oznaka={oznakaIzida(p.tekma)}
              tocke={sTockami ? tockeTekme(p.tekma) : ''}
              sKlubom={sKlubom && glavni && p.stolpec === 0}
              pisava={merila.pisava}
              sirina={sirinaOkvirja(p.stolpec)}
            />
          </div>
        ))}

        {odsek.zadnja && (
          <Uvrstitev
            tekma={odsek.zadnja.tekma}
            od={odsek.od}
            left={levo(stolpcev)}
            top={y(odsek.zadnja.y)}
            sirina={uvrstitev}
          />
        )}
      </div>
    </section>
  )
}

/* Kdo je dobil par mest, ki ga odloča zadnja tekma poti. */
function Uvrstitev({
  tekma,
  od,
  left,
  top,
  sirina,
}: {
  tekma: TekmaDto
  od: number
  left: number
  top: number
  sirina: number
}) {
  if (tekma.status !== 'KONCANA' || tekma.idZmagovalcaPrijave === null) return null
  const prvi = tekma.udelezenec1?.idPrijave === tekma.idZmagovalcaPrijave
  const zmagovalec = prvi ? tekma.udelezenec1 : tekma.udelezenec2
  const porazenec = prvi ? tekma.udelezenec2 : tekma.udelezenec1
  const vrstica = (mesto: number, ime: string | undefined) => {
    /* Številka mesta z razmikom vzame ~5 mm; dolgo ime se zmanjša, kot v okvirju. */
    const ocena = ime ? sirinaImena(ime, PISAVA_UVRSTITVE_PT) : 0
    const merilo = ocena > sirina - 5 ? Math.max(NAJMANJSE_MERILO_IMENA, (sirina - 5) / ocena) : 1
    return (
      <li style={merilo < 1 ? { fontSize: `${(PISAVA_UVRSTITVE_PT * merilo).toFixed(2)}pt` } : undefined}>
        <b>{mesto}.</b> {ime}
      </li>
    )
  }
  return (
    <ol className="izpis-sv__mesta" style={{ left: `${left}mm`, top: `${top}mm`, width: `${sirina}mm` }}>
      {vrstica(od, zmagovalec?.polnoIme)}
      {porazenec && vrstica(od + 1, porazenec.polnoIme)}
    </ol>
  )
}

/* Enaka kot v slogu .izpis-sv__mesta. */
const PISAVA_UVRSTITVE_PT = 7.5
