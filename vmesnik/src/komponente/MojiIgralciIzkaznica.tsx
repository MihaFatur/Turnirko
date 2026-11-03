/* Izkaznica igralca, ki ga račun spremlja (sklop »Moji igralci« na domači
   strani). Ena velika izkaznica naenkrat, igralci so zavihki; ob enem
   igralcu zavihkov ni.

   Komponenta je samo prikaz: podatke, preklop spremljanja in stanje
   poizvedbe dobi od MojIgralecSklop, zato jo uporablja tudi dev harness
   (/dev/posnetki, okno=moji-igralci) brez omrežja.

   Širina sledi vsebniku in ne oknu (container-type + cqw v clamp()), zato
   media queryjev ni — isti izrazi veljajo pri 1200 in pri 350 px.

   Gibanje (izjema od pravila 12 v DESIGN.md, razdelek 5g): rating se
   naštevilči in vrstice se dvignejo ob vstopu. Animirata se samo transform
   in opacity; ob prefers-reduced-motion je takoj končno stanje. */
import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { DomovIgralecDto, TekmaProfila } from '../api/tipi'
import { oblikujDanKratekMesec } from '../pomozno/oblikovanje'
import {
  bilancaSezone,
  cezNapis,
  danesLjubljana,
  lestvicaKratko,
  podZavihka,
  predznak,
  spremembaVsota,
  srecanjeIzkaznice,
  velikostPriimka,
} from '../pomozno/mojiIgralci'
import { GumbSpremljanja } from './GumbSpremljanja'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SlikaIgralca } from './SlikaIgralca'
import { ZnackaStatusa } from './Znacka'

export type StanjeSklopa = 'vsebina' | 'prazno' | 'napaka' | 'nalaganje'

/* Rating se naštevilči od (rating − 96) do rating v 900 ms. */
const TRAJANJE_STEVILA = 900
const RAZPON_STEVILA = 96

interface Lastnosti {
  stanje: StanjeSklopa
  igralci: DomovIgralecDto[]
  /* Račun s paketom dobi povezavo na sezonski zapisnik. */
  zapisnik: boolean
  naPreklop: (idIgralec: number) => void
  /* Poizvedba za stanje »napaka« (NapakaPoizvedbe). */
  poizvedba: { error: unknown; isFetching: boolean; refetch: () => unknown }
  /* Sporočilo pod glavo (npr. dosežena meja spremljanih igralcev). */
  sporocilo?: ReactNode
  /* Izbrani zavihek ob prvem prikazu (harness). */
  zacetni?: number
  /* Brez gibanja, ne glede na nastavitev sistema (harness). */
  brezGibanja?: boolean
  /* Današnji dan v Ljubljani (yyyy-mm-dd); harness ga zamrzne. */
  danes?: string
}

function gibanjeDovoljeno(brezGibanja: boolean | undefined): boolean {
  if (brezGibanja) return false
  return !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

export function MojiIgralciIzkaznica({
  stanje,
  igralci,
  zapisnik,
  naPreklop,
  poizvedba,
  sporocilo,
  zacetni = 0,
  brezGibanja,
  danes,
}: Lastnosti) {
  const koren = useRef<HTMLDivElement>(null)
  const zavihkiRef = useRef<(HTMLButtonElement | null)[]>([])
  const [izbran, nastaviIzbranega] = useState(zacetni)
  const [stevilo, nastaviStevilo] = useState<{ id: number | null; t: number }>({ id: null, t: 0 })

  const stIgralcev = igralci.length
  const aktivni = Math.min(izbran, Math.max(0, stIgralcev - 1))
  const igralec = igralci[aktivni] as DomovIgralecDto | undefined
  const idAktivnega = igralec?.idIgralec ?? null
  const kazeVsebino = stanje === 'vsebina' && igralec !== undefined
  const imaZavihke = stIgralcev > 1

  /* Igralec, ki po osvežitvi izgine, vrne izbor na prvega. */
  useEffect(() => {
    if (izbran > 0 && izbran >= stIgralcev) nastaviIzbranega(0)
  }, [izbran, stIgralcev])

  /* Vstop vrstic: ob prvem prikazu vsebine, ob menjavi zavihka in ob prehodu
     nalaganje/napaka → vsebina. Layout efekt, da elementi ne zabliskajo v
     končnem stanju, preden se animacija začne. */
  useLayoutEffect(() => {
    if (!kazeVsebino || !gibanjeDovoljeno(brezGibanja)) return
    const el = koren.current
    if (!el?.animate) return
    el.querySelectorAll('[data-vstop]').forEach((vrstica, i) => {
      vrstica.animate(
        [
          { transform: 'translateY(12px)', opacity: 0 },
          { transform: 'none', opacity: 1 },
        ],
        { duration: 420, delay: Math.min(i, 12) * 45, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'backwards' },
      )
    })
  }, [kazeVsebino, idAktivnega, brezGibanja])

  /* Naštevilčenje ratinga. Stanje nosi id igralca, da novi zavihek ne
     zabliska s končno vrednostjo, preden se efekt zažene. */
  useEffect(() => {
    if (!kazeVsebino || idAktivnega === null || !gibanjeDovoljeno(brezGibanja)) return
    let okvir = 0
    const zacetek = performance.now()
    const korak = (zdaj: number) => {
      const t = Math.min(1, (zdaj - zacetek) / TRAJANJE_STEVILA)
      nastaviStevilo({ id: idAktivnega, t })
      if (t < 1) okvir = requestAnimationFrame(korak)
    }
    nastaviStevilo({ id: idAktivnega, t: 0 })
    okvir = requestAnimationFrame(korak)
    return () => cancelAnimationFrame(okvir)
  }, [kazeVsebino, idAktivnega, brezGibanja])

  const potek = gibanjeDovoljeno(brezGibanja) ? (stevilo.id === idAktivnega ? stevilo.t : 0) : 1
  const dan = danes ?? danesLjubljana()

  /* Standardni tablist: puščici levo/desno preklopita zavihek. */
  const naTipko = (e: KeyboardEvent) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const naslednji = (aktivni + (e.key === 'ArrowRight' ? 1 : -1) + stIgralcev) % stIgralcev
    nastaviIzbranega(naslednji)
    zavihkiRef.current[naslednji]?.focus()
  }

  return (
    <div className="moji-igralci" ref={koren}>
      <div className="naslovna-vrstica moji-igralci__glava">
        <h2 className="moji-igralci__naslov">{stIgralcev > 1 ? 'Moji igralci' : 'Moj igralec'}</h2>
        <Link to="/lestvica" className="moji-igralci__povezava">
          Dodaj igralca →
        </Link>
      </div>

      {sporocilo}

      {stanje === 'napaka' && (
        <div className="moji-igralci__napaka">
          <NapakaPoizvedbe poizvedba={poizvedba} kaj="igralcev" />
        </div>
      )}

      {(stanje === 'prazno' || (stanje === 'vsebina' && !igralec)) && <PrazneIzkaznica />}

      {stanje === 'nalaganje' && <OkosjeIzkaznice />}

      {kazeVsebino && igralec && (
        <div className="moji-igralci__vsebina">
          {imaZavihke && (
            <div className="moji-igralci__zavihki" role="tablist" aria-label="Spremljani igralci" onKeyDown={naTipko}>
              {igralci.map((z, i) => (
                <button
                  type="button"
                  role="tab"
                  id={`moji-igralci-zavihek-${z.idIgralec}`}
                  aria-selected={i === aktivni}
                  aria-controls="moji-igralci-izkaznica"
                  tabIndex={i === aktivni ? 0 : -1}
                  className={'moji-igralci__zavihek' + (i === aktivni ? ' moji-igralci__zavihek--aktiven' : '')}
                  onClick={() => nastaviIzbranega(i)}
                  ref={(el) => {
                    zavihkiRef.current[i] = el
                  }}
                  key={z.idIgralec}
                >
                  <SlikaIgralca zgostitev={z.zgostitev} velikost="MALA" razred="moji-igralci__zavihek-slika" />
                  <span className="moji-igralci__zavihek-besedilo">
                    <span className="moji-igralci__zavihek-ime">
                      {z.ime} <span className="moji-igralci__zavihek-priimek">{z.priimek}</span>
                    </span>
                    <span className="moji-igralci__zavihek-pod">{podZavihka(z)}</span>
                  </span>
                </button>
              ))}
            </div>
          )}

          <Izkaznica
            igralec={igralec}
            zapisnik={zapisnik}
            naPreklop={() => naPreklop(igralec.idIgralec)}
            potek={potek}
            danes={dan}
            tabela={imaZavihke}
          />
        </div>
      )}
    </div>
  )
}

function Izkaznica({
  igralec,
  zapisnik,
  naPreklop,
  potek,
  danes,
  tabela,
}: {
  igralec: DomovIgralecDto
  zapisnik: boolean
  naPreklop: () => void
  /* Napredek naštevilčenja ratinga, 0–1. */
  potek: number
  danes: string
  /* Izkaznica je plošča zavihkov. */
  tabela: boolean
}) {
  const { uvrstitev } = igralec
  const sprememba = spremembaVsota(igralec.zadnje)
  const bilanca = bilancaSezone(igralec.sezona)
  const srecanje = igralec.naslednjeSrecanje ? srecanjeIzkaznice(igralec.naslednjeSrecanje) : null
  const turnir = igralec.prihajajociTurnir
  const profil = `/igralci/${igralec.idIgralec}/profil`

  /* Rating se naštevilči od (rating − 96) navzgor; brez ratinga ni animacije. */
  const e = 1 - Math.pow(1 - potek, 3)
  const rating = igralec.rating === null ? '—' : String(Math.round(igralec.rating - (1 - e) * RAZPON_STEVILA))

  return (
    <div
      className="moji-igralci__izkaznica"
      id="moji-igralci-izkaznica"
      role={tabela ? 'tabpanel' : undefined}
      aria-labelledby={tabela ? `moji-igralci-zavihek-${igralec.idIgralec}` : undefined}
    >
      <div className="moji-igralci__pas moji-igralci__identiteta" data-vstop="">
        <div className="moji-igralci__oseba">
          <Link to={profil} className="moji-igralci__portret-povezava" tabIndex={-1} aria-hidden="true">
            <SlikaIgralca zgostitev={igralec.zgostitev} velikost="VELIKA" razred="moji-igralci__portret" />
          </Link>
          <div className="moji-igralci__besedilo">
            <Link to={profil} className="moji-igralci__ime-povezava">
              <span className="moji-igralci__ime">{igralec.ime}</span>
              <span className="moji-igralci__priimek" style={{ fontSize: velikostPriimka(igralec.priimek) }}>
                {igralec.priimek}
              </span>
            </Link>
            {igralec.druzina && <div className="moji-igralci__druzina">Družina</div>}
          </div>
        </div>
        <div className="moji-igralci__rating">
          <div className="moji-igralci__oznaka-rating">Turnirko rating</div>
          <div className="moji-igralci__stevilka">{rating}</div>
          <div className="moji-igralci__rating-pod">
            {igralec.zadnje.length > 0 ? `${sprememba.besedilo} v zadnjih 3 tekmah` : 'brez odigranih tekem'}
          </div>
        </div>
      </div>

      <div className="moji-igralci__kolofon" data-vstop="">
        <div className="moji-igralci__celica">
          <div className="moji-igralci__oznaka">Klub</div>
          <div className="moji-igralci__vrednost">{igralec.klub ?? 'brez kluba'}</div>
        </div>
        <div className="moji-igralci__celica">
          <div className="moji-igralci__oznaka">Lestvica</div>
          <div className="moji-igralci__vrednost">{lestvicaKratko(uvrstitev)}</div>
        </div>
        <div className="moji-igralci__celica">
          <div className="moji-igralci__oznaka">Mesto</div>
          <div className="moji-igralci__meritev">
            <span className="moji-igralci__velika-stevilka">{uvrstitev.mesto === null ? '—' : `${uvrstitev.mesto}.`}</span>
            <span className="moji-igralci__meritev-pod">
              {uvrstitev.mesto === null ? 'brez uvrstitve' : `od ${uvrstitev.skupajIgralcev}`}
            </span>
          </div>
        </div>
        <div className="moji-igralci__celica">
          <div className="moji-igralci__oznaka">Sezona</div>
          <div className="moji-igralci__meritev">
            <span className="moji-igralci__velika-stevilka moji-igralci__velika-stevilka--nowrap">
              {bilanca ? bilanca.prikaz : '—'}
            </span>
            <span className="moji-igralci__meritev-pod">{bilanca ? bilanca.pod : 'brez tekem'}</span>
          </div>
          <div className="moji-igralci__palica">
            <div className="moji-igralci__palica-polnilo" style={{ width: `${bilanca ? bilanca.odstotek : 0}%` }} />
          </div>
        </div>
      </div>

      <div className="moji-igralci__vstopnica" data-vstop="">
        <div className="moji-igralci__vstopnica-glava">
          <span>Naslednje</span>
          <span>{cezNapis(igralec, danes)}</span>
        </div>
        {srecanje && (
          <Link to={`/srecanja/${srecanje.idSrecanje}`} className="moji-igralci__srecanje">
            {srecanje.imaZacetek ? (
              <div className="moji-igralci__kdaj">
                <div className="moji-igralci__dan">{srecanje.dan}</div>
                <div className="moji-igralci__datum">{srecanje.datum}</div>
                {srecanje.ura && <div className="moji-igralci__ura">{srecanje.ura}</div>}
              </div>
            ) : (
              <div className="moji-igralci__kdaj">
                <div className="moji-igralci__datum">{srecanje.kolo}</div>
              </div>
            )}
            <div className="moji-igralci__ekipe">
              <div className="moji-igralci__opis">{srecanje.opis}</div>
              <div className="moji-igralci__opis-pod">{srecanje.pod}</div>
            </div>
          </Link>
        )}
        {turnir && (
          <Link to={`/turnirji/${turnir.idTurnir}`} className="moji-igralci__turnir">
            <span className="moji-igralci__turnir-kdaj">{oblikujDanKratekMesec(turnir.datum)}</span>
            <span className="moji-igralci__turnir-ime">{turnir.ime}</span>
            <span className="moji-igralci__turnir-pod">Prijavljen</span>
            <ZnackaStatusa status={turnir.status} />
          </Link>
        )}
        {!srecanje && !turnir && <p className="moji-igralci__brez-termina">Ni napovedanega termina.</p>}
      </div>

      <div className="moji-igralci__pas moji-igralci__tekme">
        <div className="moji-igralci__forma" data-vstop="">
          <div className="moji-igralci__naslov-razdelka">Forma · zadnjih 5</div>
          {igralec.forma.length > 0 ? (
            <div className="moji-igralci__kvadratki">
              {igralec.forma.map((znak, i) => (
                <span
                  className={'moji-igralci__izid-kvadratek moji-igralci__izid-kvadratek--' + (znak === 'Z' ? 'zmaga' : 'poraz')}
                  key={i}
                >
                  {znak}
                </span>
              ))}
            </div>
          ) : (
            <p className="moji-igralci__prazno-besedilo">Brez odigranih tekem.</p>
          )}
        </div>
        <div className="moji-igralci__zadnje">
          <div className="moji-igralci__naslov-razdelka moji-igralci__naslov-razdelka--razmaknjen" data-vstop="">
            <span>Zadnje tekme</span>
            {igralec.zadnje.length > 0 && (
              <span className={`moji-igralci__sprememba moji-igralci__sprememba--${sprememba.smer}`}>
                Rating {sprememba.besedilo}
              </span>
            )}
          </div>
          {igralec.zadnje.map((t) => (
            <VrsticaTekme tekma={t} key={`${t.ligaska ? 'l' : 't'}${t.idTekme}`} />
          ))}
        </div>
      </div>

      <div className="moji-igralci__noga" data-vstop="">
        <div className="moji-igralci__povezave">
          <Link to={profil} className="moji-igralci__povezava">
            Profil →
          </Link>
          {zapisnik && (
            <Link to={`/igralci/${igralec.idIgralec}/sezona`} className="moji-igralci__povezava">
              Sezonski zapisnik →
            </Link>
          )}
        </div>
        {/* Družino račun ureja na strani Naročnina; spremljanje je preklop (V57). */}
        {igralec.druzina ? (
          <Link to={profil} className="moji-igralci__povezava">
            Zasebna statistika →
          </Link>
        ) : (
          <GumbSpremljanja ime={igralec.polnoIme} spremljam naPreklop={naPreklop} />
        )}
      </div>
    </div>
  )
}

function VrsticaTekme({ tekma }: { tekma: TekmaProfila }) {
  const sprememba = tekma.spremembaRatinga
  const barva = tekma.zmaga ? 'profil__zmaga' : 'profil__poraz'
  return (
    <div className="moji-igralci__tekma" data-vstop="">
      <span className="moji-igralci__tekma-datum">{oblikujDanKratekMesec(tekma.datum)}</span>
      <span className="moji-igralci__tekma-ime">
        {tekma.nasprotnik}
        <span className="moji-igralci__tekma-pod">{tekma.tekmovanje}</span>
      </span>
      <span className={'moji-igralci__tekma-stevilka ' + barva}>
        {tekma.niziZa}:{tekma.niziProti}
      </span>
      <span className={'moji-igralci__tekma-stevilka ' + barva}>{sprememba === null ? '' : predznak(sprememba)}</span>
    </div>
  )
}

/* Navijač brez igralcev: izkaznica, ki še ni izpolnjena, in pot do nje. */
function PrazneIzkaznica() {
  return (
    <div className="moji-igralci__prazno">
      <SlikaIgralca zgostitev={null} velikost="VELIKA" razred="moji-igralci__prazen-portret" />
      <div className="moji-igralci__prazno-vsebina">
        <div className="moji-igralci__prazno-vrstica">Prazna</div>
        <div className="moji-igralci__prazno-naslov">izkaznica.</div>
        <p className="moji-igralci__prazno-opis">
          Na profilu igralca pritisni »Spremljaj«. Tu bosta potem njegov rating in naslednji termin, pod njima pa zadnje
          tekme.
        </p>
        <Link to="/lestvica" className="gumb gumb--glavni moji-igralci__poisci">
          Poišči igralca
        </Link>
      </div>
    </div>
  )
}

/* Okostje izkaznice: sklop se ne sme pojaviti šele po odgovoru in stran
   premakniti. Sive ploskve brez animacije. */
function OkosjeIzkaznice() {
  return (
    <div className="moji-igralci__okostje" aria-hidden="true">
      <div className="moji-igralci__okostje-vrh">
        <span className="moji-igralci__okostje-portret" />
        <span className="moji-igralci__okostje-stolpec">
          <span className="moji-igralci__okostje-letev moji-igralci__okostje-letev--ime" />
          <span className="moji-igralci__okostje-letev moji-igralci__okostje-letev--priimek" />
        </span>
        <span className="moji-igralci__okostje-rating" />
      </div>
      <div className="moji-igralci__okostje-vrstice">
        <span className="moji-igralci__okostje-letev moji-igralci__okostje-letev--vrstica" />
        <span className="moji-igralci__okostje-letev moji-igralci__okostje-letev--vrstica" />
        <span className="moji-igralci__okostje-letev moji-igralci__okostje-letev--vrstica" />
      </div>
    </div>
  )
}
