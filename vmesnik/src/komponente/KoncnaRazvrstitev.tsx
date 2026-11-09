/* Končna razvrstitev zaključenega dogodka (predaja design_handoff_koncna_razvrstitev,
   možnost 1b »Stopničke«; DESIGN.md, razdelek 5h).

   Samo mesta 1.–3. (ali 1., 2. in dve »3.–4.«) so izpostavljena, kot prave
   stopnice: obarvani bloki različnih višin stojijo na skupnih tleh, slika
   igralca stoji na vrhu bloka. Vsi ostali so za črnim trakom »Še 44 igralcev«
   in se ob kliku razgrnejo po nivojih (5.–8., 9.–16. ...). Rating, sprememba
   ratinga in razlika nizov so izpuščeni; bilanca je samo pri krožnem sistemu,
   ker po njej določa vrstni red (`izid`).

   Ena struktura, dve postavitvi: namizna (stopnice) velja, kadar je razdelek
   širok vsaj 900 px, ožji dobi palice 1., 2., 3. od zgoraj navzdol - odloča
   širina vsebnika (`@container`), ne okna, zato JS ne meri ničesar. Glava
   razdelka pa sledi strani: na telefonu je razvrstitev zavihek brez zgornje
   črte (`jeTelefon`).

   Čista prikazna komponenta: podatke pripravi stran (`Zakljucek`) ali razvojni
   harness (`/dev/koncna-razvrstitev`). */
import {
  type CSSProperties,
  type ReactNode,
  useId,
  useLayoutEffect,
  useMemo,
  useState,
} from 'react'
import { Link } from 'react-router-dom'

import {
  type UdelezenecRazvrstitve,
  razdeliNaStopnice,
  razdeliVNivoje,
} from '../pomozno/koncnaRazvrstitev'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import { SlikaIgralca } from './SlikaIgralca'

export interface KoncnaRazvrstitevLastnosti {
  /* Vsi razvrščeni, urejeni po končnem mestu. */
  udelezenci: UdelezenecRazvrstitve[]
  ekipno: boolean
  jeTelefon: boolean
  /* Ali stopničke nosijo slike: če jo ima vsaj eden, okvir dobijo vsi (kdor
     slike nima, ima v okvirju obris), sicer jih ni. */
  zSlikami?: boolean
  /* Ob spremembi se stopnice znova dvignejo (menjava dogodka). */
  kljucVstopa: string | number
  /* Stran za tisk cele razvrstitve. */
  povezavaTisk: string
  /* SV regija ima svoj seznam ostalih po nivojih in gumba »Še N« ne potrebuje. */
  ostaliSv?: ReactNode
  /* Začetno stanje seznama ostalih (razvojni harness). */
  razgrnjeno?: boolean
  /* Razvojni nadomestek slike (harness); sicer javna profilna slika. */
  izrisiSliko?: (udelezenec: UdelezenecRazvrstitve, razred: string) => ReactNode
  /* Zaključen dogodek na namizju: razvrstitev je zaprta, dokler je gledalec ne
     odpre (odločitev lastnika, nov. 2026). Brez lastnosti je vedno razgrnjena. */
  zlozljiva?: { odprta: boolean; naPreklop: () => void }
}

/* Stopničke nastopijo z zamikom 90 ms po vrsti; ob zmanjšanem gibanju je takoj
   končno stanje (CSS to ponovi, JS začetnega stanja sploh ne sproži). */
function zmanjsanoGibanje(): boolean {
  return typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function KoncnaRazvrstitev({
  udelezenci,
  ekipno,
  jeTelefon,
  zSlikami = false,
  kljucVstopa,
  povezavaTisk,
  ostaliSv,
  razgrnjeno = false,
  izrisiSliko,
  zlozljiva,
}: KoncnaRazvrstitevLastnosti) {
  const [odprto, nastaviOdprto] = useState(razgrnjeno)
  const idOstalih = useId()
  const idTelesa = useId()
  const zaprta = zlozljiva !== undefined && !zlozljiva.odprta

  /* Začetno stanje je VIDNO: če bi stopnice začele skrite in bi se zagon
     zataknil (skrit zavihek, rAF ne teče), bi ostal razdelek prazen. Skrijemo
     jih pred prvim izrisom in čez 60 ms spustimo; requestAnimationFrame se v
     skritem zavihku ne izvaja, časovnik pa. Ob vsakem odprtju zaprte razvrstitve
     se stopnice dvignejo znova, zato je odprtje zaprte razvrstitve del odvisnosti. */
  const [vstop, nastaviVstop] = useState(true)
  useLayoutEffect(() => {
    if (zmanjsanoGibanje() || document.visibilityState !== 'visible') return
    nastaviVstop(false)
    const cas = window.setTimeout(() => nastaviVstop(true), 60)
    return () => window.clearTimeout(cas)
  }, [kljucVstopa, zlozljiva?.odprta])

  const { vrh, ostali } = useMemo(() => razdeliNaStopnice(udelezenci), [udelezenci])
  const nivoji = useMemo(() => razdeliVNivoje(ostali), [ostali])

  if (udelezenci.length === 0) return null

  const enota = ekipno ? sklonEkip : sklonIgralcev
  const stevilo = `${udelezenci.length} ${enota(udelezenci.length)}`

  /* Na zaslonu je 2. mesto levo od 1., tretja mesta desno; DOM ostane po
     mestih (bralnik prebere 1., 2., 3.), vrstni red zaslona nosi `order`. */
  const imaDrugo = vrh.some((u) => u.mesto === 2)
  let tretjih = 0
  const stopnice = vrh.map((u, i) => {
    const mesto = Math.min(u.mesto, 3)
    const deljeno = vrh.filter((v) => v.mesto === u.mesto).length > 1
    const red = !imaDrugo ? i + 1 : mesto === 1 ? 2 : mesto === 2 ? 1 : 3 + tretjih++
    return { u, mesto, deljeno, red, i }
  })

  const razredStopnic = [
    'stopnice',
    `stopnice--s${Math.min(vrh.length, 4)}`,
    ekipno ? 'stopnice--ekipno' : '',
    vstop ? '' : 'stopnice--skrite',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={'koncna-razvrstitev' + (jeTelefon ? ' koncna-razvrstitev--telefon' : '')}>
      <div className="koncna-razvrstitev__glava">
        <h2 className="koncna-razvrstitev__naslov">Končna razvrstitev</h2>
        <div className="koncna-razvrstitev__desno">
          {!jeTelefon && <span className="koncna-razvrstitev__meta">{stevilo}</span>}
          <Link className="koncna-razvrstitev__natisni" to={povezavaTisk}>
            {jeTelefon ? 'Natisni' : 'Natisni razvrstitev'}
          </Link>
          {zlozljiva && (
            <button
              type="button"
              className="koncna-razvrstitev__natisni"
              aria-expanded={zlozljiva.odprta}
              aria-controls={idTelesa}
              onClick={zlozljiva.naPreklop}
            >
              {zlozljiva.odprta ? 'Skrij ↑' : 'Pokaži razvrstitev ↓'}
            </button>
          )}
        </div>
      </div>

      <div id={idTelesa} className="koncna-razvrstitev__telo" hidden={zaprta}>
        <div className={razredStopnic}>
          {stopnice.map(({ u, mesto, deljeno, red, i }) => (
            <div
              key={u.id}
              className={
                `stopnice__stopnica stopnice__stopnica--m${mesto}` + (deljeno ? ' stopnice__stopnica--deljeno' : '')
              }
              style={{ '--red': red, '--i': i } as CSSProperties}
            >
              {/* Slika je okras (alt=""): ime stoji ob njej (DESIGN.md 4d). */}
              {zSlikami
                && (izrisiSliko?.(u, 'stopnice__slika') ?? (
                  <SlikaIgralca zgostitev={u.slika} velikost="VELIKA" razred="stopnice__slika" />
                ))}
              <div className="stopnice__blok">
                <span className="stopnice__mesto">{u.oznaka}</span>
                <div className="stopnice__besedilo">
                  <div className="stopnice__ime">{u.ime}</div>
                  <div className="stopnice__priimek">{u.priimek}</div>
                  <div className="stopnice__podatki">
                    <div className="stopnice__klub">{u.klub}</div>{' '}
                    {u.izid && <div className="stopnice__izid">{u.izid}</div>}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {ostaliSv ?? (
          ostali.length > 0 && (
            <>
              <button
                type="button"
                className="stopnice__tla"
                aria-expanded={odprto}
                aria-controls={idOstalih}
                onClick={() => nastaviOdprto((prej) => !prej)}
              >
                <span className="stopnice__tla-levo">
                  Še {ostali.length} {enota(ostali.length)}
                </span>
                <span className="stopnice__tla-desno">{odprto ? 'Skrij ↑' : 'Pokaži vse ↓'}</span>
              </button>

              {/* Zaprt seznam je skrit pred bralnikom in tipkovnico (visibility
                  po koncu prehoda), ne le odrezan. */}
              <div id={idOstalih} className={'nivoji' + (odprto ? ' nivoji--odprti' : '')}>
                <div className="nivoji__notranji">
                  <div className="nivoji__vsebina">
                    {nivoji.map((nivo) => {
                      const vec = nivo.clani.length > 1
                      return (
                        <div className="nivoji__nivo" key={nivo.clani[0].id}>
                          <div className="nivoji__glava">
                            <div className="nivoji__oznaka">{nivo.oznaka}</div>
                            <div className="nivoji__stevec">
                              {vec ? `${nivo.clani.length} ${enota(nivo.clani.length)}` : ''}
                            </div>
                          </div>
                          <div className={'nivoji__vrstice' + (vec ? '' : ' nivoji__vrstice--ena')}>
                            {nivo.clani.map((c) => (
                              <div className="nivoji__vrstica" key={c.id}>
                                <span className="nivoji__ime">
                                  <span className="nivoji__ime-lahko">{c.ime}</span>{' '}
                                  <span className="nivoji__priimek">{c.priimek}</span>
                                </span>
                                <span className="nivoji__desno">
                                  <span className="nivoji__klub">{c.klub}</span>{' '}
                                  <span className="nivoji__izid">{c.izid}</span>
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            </>
          )
        )}
      </div>
    </div>
  )
}
