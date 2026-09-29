/* Celozaslonski oglas »Igralec Premium«: pokaže se, ko igralec brez Premium
   klikne zaklenjeno funkcijo (spremljanje in urejanje lig, zasebna statistika).
   Tri različice iste ponudbe — 1a Zavesa, 1b Ključavnica, 1c Končni izid —, vsaka
   v postavitvi za namizje in za telefon (pod 768 px); ob vsakem odprtju se
   naključno izbere ena (glej PremiumOglasKontekst).

   Izvor je predaja design_handoff_premium_popup: mere, pisave, barve in gibanje so
   prepisani iz prototipa in se s posnetki ujemajo na piksel (glej razdelek »Oglas
   Igralec Premium« v slog.css). Oglas je NAMERNA IZJEMA od DESIGN.md (razdelek 5c):
   glasnejši ton in lik tudi na telefonu. Izjema velja samo za ta oglas.

   Ponudba je vedno ista, spreminja se samo, kdo jo vidi:
   - gost: »Ustvari račun · Premium …« odpre registracijo na koraku »Paket« z
     izbranim ciklom; povezava odpre prijavo;
   - prijavljen igralec: »Nadgradi na Premium · …« odpre Stripe Checkout za izbrani
     cikel; povezava vodi na stran Naročnina.
   Cenovni pas (»Mlajši od 21?«) določi stikalo samo pri gostu. Prijavljen igralec
   ima pas že v računu (`starejsiOd21`) in Stripe računa po njem, zato mu stikala
   ne pokažemo: cena v oglasu mora biti cena, ki jo bo plačal.

   Oglas je pravi modal: ozadje je `inert`, fokus je ob odprtju na glavnem gumbu,
   »Ne zdaj« se pojavi po 2 s in šele takrat deluje tudi Escape. Pri
   `prefers-reduced-motion: reduce` je takoj končno stanje. */
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  type ReactNode,
  type RefObject,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'

import { opisNapake } from '../api/odjemalec'
import { placilaApi, premiumApi } from '../api/zahteve'
import type { CiklusPlacila, PremiumDokazDto } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  jeMirno,
  poskokStevilke,
  predvajajOglas,
} from '../pomozno/animacijaOglasa'
import {
  KONCNI_IZID,
  KONTEKSTI,
  PRIMERJAVA,
  TOCKE_IZIDA,
  besediloDokaza,
  izracunajCene,
  kvadratkiDokaza,
  type BesedilaKonteksta,
  type CeneOglasa,
  type KontekstOglasa,
  type RazlicicaOglasa,
} from '../pomozno/premiumOglas'
import { PremiumLik, PrizorKljucavnica } from './PremiumOglasPrizori'

/* Čez koliko ms se pojavi »Ne zdaj« (in začne delovati Escape). */
const ZAMIK_ZAPIRANJA_MS = 2000

const POIZVEDBA_TELEFON = '(max-width: 767px)'

function useTelefonOglasa(): boolean {
  const [telefon, nastavi] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(POIZVEDBA_TELEFON).matches,
  )
  useEffect(() => {
    const poizvedba = window.matchMedia(POIZVEDBA_TELEFON)
    const obSpremembi = (d: MediaQueryListEvent) => nastavi(d.matches)
    nastavi(poizvedba.matches)
    poizvedba.addEventListener('change', obSpremembi)
    return () => poizvedba.removeEventListener('change', obSpremembi)
  }, [])
  return telefon
}

/* Skupno stanje zaslona; vsak od šestih zaslonov ga bere in ne zna ničesar
   svojega razen postavitve. */
interface Stanje {
  k: BesedilaKonteksta
  c: CeneOglasa
  cikel: CiklusPlacila
  nastaviCikel: (c: CiklusPlacila) => void
  u21: boolean
  znanPas: boolean
  preklopU21: () => void
  gost: boolean
  cta: string
  ctaDrugo: string
  dokaz: PremiumDokazDto | null
  zapriViden: boolean
  onZapri: () => void
  onCta: () => void
  onDrugo: () => void
  poteka: boolean
  napaka: string | null
  ctaRef: RefObject<HTMLButtonElement | null>
  izid: { f: number; p: number }
  stevecF: RefObject<HTMLSpanElement | null>
  stevecP: RefObject<HTMLSpanElement | null>
}

/* ---------- Skupni deli ---------- */

/* Logotip: štirje navpični pasovi; barve prvih treh se menjajo po podlagi. */
function Logo({ barve }: { barve: [string, string, string] }) {
  return (
    <svg className="premium-oglas__logo-znak" viewBox="0 0 28 28" aria-hidden="true">
      <rect x="1.5" y="4" width="3" height="20" fill={barve[0]} />
      <rect x="8.5" y="4" width="3" height="20" fill={barve[1]} />
      <rect x="15.5" y="4" width="3" height="20" fill={barve[2]} />
      <rect x="22.5" y="4" width="3" height="20" fill="#7BB900" />
    </svg>
  )
}

/* »Ne zdaj ×«: do zamika nevidno in brez klikov (`pointer-events`), potem
   prehod prosojnosti. Stoji v DOM od začetka, zato razmik glave ostane. */
function NeZdaj({ s }: { s: Stanje }) {
  return (
    <button
      type="button"
      className={'premium-oglas__ne-zdaj' + (s.zapriViden ? ' premium-oglas__ne-zdaj--viden' : '')}
      onClick={s.onZapri}
      tabIndex={s.zapriViden ? 0 : -1}
      aria-hidden={s.zapriViden ? undefined : true}
    >
      Ne zdaj &times;
    </button>
  )
}

/* Izbirnik Mesečno / Letno. Diagonalni trak »−10 %« na Letnem prikaže prihranek
   letnega plačila. `svetel`: izbira na modri podlagi (1b). */
function Izbirnik({ s, svetel = false }: { s: Stanje; svetel?: boolean }) {
  const razred = svetel ? 'premium-oglas__izbirnik premium-oglas__izbirnik--svetel' : 'premium-oglas__izbirnik'
  return (
    <div className={razred} role="group" aria-label="Način plačevanja">
      <button
        type="button"
        className={'premium-oglas__cikel' + (s.cikel === 'MESECNO' ? ' premium-oglas__cikel--izbran' : '')}
        aria-pressed={s.cikel === 'MESECNO'}
        onClick={() => s.nastaviCikel('MESECNO')}
      >
        Mesečno
      </button>
      <button
        type="button"
        className={
          'premium-oglas__cikel premium-oglas__cikel--letno'
          + (s.cikel === 'LETNO' ? ' premium-oglas__cikel--izbran' : '')
        }
        aria-pressed={s.cikel === 'LETNO'}
        onClick={() => s.nastaviCikel('LETNO')}
      >
        Letno
        <span className="premium-oglas__trak-popust" aria-hidden="true">
          &minus;10%
        </span>
      </button>
    </div>
  )
}

/* Stikalo »Mlajši od 21?«: kvadratek, ki se ob vklopu pobarva zeleno. Pri
   prijavljenem z znano starostjo ga ni (glej zgoraj). */
function StikaloU21({ s, prost = false }: { s: Stanje; prost?: boolean }) {
  if (s.znanPas) return null
  const napis = s.u21
    ? `Cena do 21. leta: ${s.c.drugaCena} ${s.c.enota}`
    : `Mlajši od 21? Plačaš samo ${s.c.drugaCena}`
  return (
    <button
      type="button"
      className={
        'premium-oglas__u21'
        + (prost ? ' premium-oglas__u21--prost' : '')
        + (s.u21 ? ' premium-oglas__u21--vklopljen' : '')
      }
      role="switch"
      aria-checked={s.u21}
      onClick={s.preklopU21}
    >
      <span className="premium-oglas__u21-kvadratek" />
      <span className="premium-oglas__u21-napis">{napis}</span>
    </button>
  )
}

/* Socialni dokaz: kvadratki brez črk in vrstica »N igralcev iz tvojega kluba že
   ima Premium«. Brez števila z zaledja vrstice ni (glej PremiumDokazStoritev). */
function Dokaz({ s, sIgra = false }: { s: Stanje; sIgra?: boolean }) {
  if (!s.dokaz || s.dokaz.stevilo <= 0) return null
  const { prazni, ostalo } = kvadratkiDokaza(s.dokaz.stevilo)
  const besedilo = besediloDokaza(s.dokaz.stevilo, s.dokaz.izKluba, sIgra)
  return (
    <div className="premium-oglas__dokaz">
      <span className="premium-oglas__dokaz-kvadratki" aria-hidden="true">
        {Array.from({ length: prazni }, (_, i) => (
          <span key={i} className={`premium-oglas__dokaz-kvadratek premium-oglas__dokaz-kvadratek--${i + 1}`} />
        ))}
        {ostalo !== null && (
          <span className="premium-oglas__dokaz-kvadratek premium-oglas__dokaz-kvadratek--ostalo">
            +{ostalo}
          </span>
        )}
      </span>
      <span className="premium-oglas__dokaz-besedilo">
        <strong>{besedilo.krepko}</strong>
        {besedilo.rep}
      </span>
    </div>
  )
}

/* Glavni gumb in povezava pod njim (razred določa različica). */
function Cta({ s }: { s: Stanje }) {
  return (
    <button
      ref={s.ctaRef}
      type="button"
      className="premium-oglas__cta"
      onClick={s.onCta}
      disabled={s.poteka}
      aria-busy={s.poteka}
    >
      {s.poteka ? 'Odpiram plačilo …' : s.cta}
    </button>
  )
}

function Povezava({ s }: { s: Stanje }) {
  if (s.gost) {
    return (
      <button type="button" className="premium-oglas__povezava" onClick={s.onDrugo}>
        {s.ctaDrugo}
      </button>
    )
  }
  return (
    <Link to="/narocnina" className="premium-oglas__povezava" onClick={s.onZapri}>
      {s.ctaDrugo}
    </Link>
  )
}

function Napaka({ s }: { s: Stanje }) {
  return s.napaka ? (
    <p className="premium-oglas__napaka" role="alert">
      {s.napaka}
    </p>
  ) : null
}

/* Zamegljene vrstice predogleda pod zaveso (1a). */
function Predogled({ s }: { s: Stanje }) {
  return (
    <div className="premium-oglas__predogled-seznam">
      {s.k.predogled.map((v) => (
        <div key={v.a} className="premium-oglas__predogled-vrstica">
          <span className="premium-oglas__predogled-ime">{v.a}</span>
          <span className="premium-oglas__predogled-vrednost">
            {v.b}
          </span>
        </div>
      ))}
    </div>
  )
}

/* Primerjalna tabela (1c): vrstica, ki ustreza kontekstu, je poudarjena. */
function Primerjava({ s }: { s: Stanje }) {
  return (
    <div className="premium-oglas__primerjava">
      {PRIMERJAVA.map(([ime, free]) => {
        const vodilna = ime === s.k.vrstica
        return (
          <div
            key={ime}
            className={'premium-oglas__primerjava-vrstica' + (vodilna ? ' premium-oglas__primerjava-vrstica--vodilna' : '')}
          >
            <span className="premium-oglas__primerjava-ime">{ime}</span>
            <span
              className={
                'premium-oglas__primerjava-free' + (free ? ' premium-oglas__primerjava-free--da' : '')
              }
            >
              {free ? 'DA' : '—'}
            </span>
            <span className="premium-oglas__primerjava-premium">DA</span>
          </div>
        )
      })}
    </div>
  )
}

/* Izid tekme Free : Premium, ki se šteje ob prikazu (1c). */
function Izid({ s, telefon }: { s: Stanje; telefon: boolean }) {
  return (
    <div className="premium-oglas__izid">
      {telefon ? (
        <span className="premium-oglas__izid-free">Free</span>
      ) : (
        <span className="premium-oglas__izid-stran">
          <span className="premium-oglas__izid-free">Free</span>
          <span className="premium-oglas__izid-podnapis premium-oglas__izid-podnapis--free">0 € · vedno</span>
        </span>
      )}
      <span
        className="premium-oglas__izid-tocke"
        role="img"
        aria-label={`Izid: Free ${s.izid.f}, Premium ${s.izid.p}`}
      >
        <span ref={s.stevecF} className="premium-oglas__izid-tocka premium-oglas__izid-tocka--free">
          {s.izid.f}
        </span>
        <span className="premium-oglas__izid-dvopicje">:</span>
        <span ref={s.stevecP} className="premium-oglas__izid-tocka premium-oglas__izid-tocka--premium">
          {s.izid.p}
        </span>
      </span>
      {telefon ? (
        <span className="premium-oglas__izid-premium">Premium</span>
      ) : (
        <span className="premium-oglas__izid-stran premium-oglas__izid-stran--desno">
          <span className="premium-oglas__izid-premium">Premium</span>
          <span className="premium-oglas__izid-podnapis premium-oglas__izid-podnapis--premium">
            od {s.c.cenaOdMesecno} / mesec
          </span>
        </span>
      )}
      <span className="premium-oglas__zig" data-anim="zig">
        Premium zmaga
      </span>
    </div>
  )
}

/* ---------- 1a Zavesa ---------- */

function Zavesa({ s, telefon }: { s: Stanje; telefon: boolean }) {
  if (telefon) {
    return (
      <div className="premium-oglas__zaslon">
        <header className="premium-oglas__glava">
          <div className="premium-oglas__logo">
            <Logo barve={['#FBF9F5', '#FBF9F5', '#0088CE']} />
            Turnirko
          </div>
          <NeZdaj s={s} />
        </header>
        <div className="premium-oglas__predogled" aria-hidden="true">
          <div className="premium-oglas__predogled-trak">
            <span className="premium-oglas__predogled-trak-levo">Samo za Premium</span>
            <span className="premium-oglas__predogled-trak-desno">{s.k.kratko}</span>
          </div>
          <div className="premium-oglas__predogled-telo">
            <h3 className="premium-oglas__predogled-naslov">{s.k.naslov}</h3>
            <Predogled s={s} />
          </div>
          <div className="premium-oglas__predogled-crta" />
          <div className="premium-oglas__zavesa" data-anim="zavesa" data-shift="-108%">
            <div className="premium-oglas__zavesa-platno" />
            <div className="premium-oglas__zavesa-lik">
              <PremiumLik poza="zavesa" />
            </div>
          </div>
        </div>
        <div className="premium-oglas__prodaja">
          <h2 className="premium-oglas__naslov">
            <span className="premium-oglas__naslov-nad">{s.k.aNad}</span>
            <span className="premium-oglas__naslov-glavni">{s.k.aGlavni}</span>
          </h2>
          <Izbirnik s={s} />
          <div className="premium-oglas__cena-vrstica">
            <span className="premium-oglas__cena">{s.c.cena}</span>
            <span className="premium-oglas__enota">{s.c.enota}</span>
            {s.c.letno && <span className="premium-oglas__sidro-majhno">{s.c.sidro}</span>}
          </div>
          <span className="premium-oglas__teden-vrstica">
            <strong>{s.c.naTeden} na teden</strong> — manj kot kava v dvorani.
          </span>
          <StikaloU21 s={s} />
          <div className="premium-oglas__noga">
            <Dokaz s={s} />
            <Cta s={s} />
          </div>
          <Napaka s={s} />
          <Povezava s={s} />
        </div>
      </div>
    )
  }

  return (
    <div className="premium-oglas__zaslon">
      <header className="premium-oglas__glava">
        <div className="premium-oglas__glava-levo">
          <div className="premium-oglas__logo">
            <Logo barve={['#FBF9F5', '#FBF9F5', '#0088CE']} />
            Turnirko
          </div>
          <span className="premium-oglas__oznaka">{s.k.oznaka}</span>
        </div>
        <NeZdaj s={s} />
      </header>
      <div className="premium-oglas__telo">
        <div className="premium-oglas__predogled-okvir">
          <div className="premium-oglas__predogled" aria-hidden="true">
            <div className="premium-oglas__predogled-trak">
              <span className="premium-oglas__predogled-trak-levo">Samo za Premium</span>
              <span className="premium-oglas__predogled-trak-desno">{s.k.kratko}</span>
            </div>
            <div className="premium-oglas__predogled-telo">
              <span className="premium-oglas__predogled-nadnaslov">Predogled · primer</span>
              <h3 className="premium-oglas__predogled-naslov">{s.k.naslov}</h3>
              <Predogled s={s} />
            </div>
            <div className="premium-oglas__predogled-crta" />
            <div className="premium-oglas__zavesa" data-anim="zavesa" data-shift="-127%">
              <div className="premium-oglas__zavesa-platno" />
              <div className="premium-oglas__zavesa-lik">
                <PremiumLik poza="zavesa" />
              </div>
            </div>
          </div>
        </div>
        <div className="premium-oglas__prodaja">
          <h2 className="premium-oglas__naslov">
            <span className="premium-oglas__naslov-nad">Zavesa gor.</span>
            <span className="premium-oglas__naslov-glavni">{s.k.aKratko}</span>
          </h2>
          <div className="premium-oglas__znacke">
            {s.k.znacke.map((z) => (
              <span key={z} className="premium-oglas__znacka">
                {z}
              </span>
            ))}
          </div>
          <Izbirnik s={s} />
          <div className="premium-oglas__cena-blok">
            <span className="premium-oglas__cena-vrstica">
              <span className="premium-oglas__cena">{s.c.cena}</span>
            </span>
            <span className="premium-oglas__cena-pod">
              {s.c.enota}
              {s.c.letno && (
                <>
                  {' '}
                  · namesto <span className="premium-oglas__precrtano">{s.c.sidro}</span>
                </>
              )}
              {' '}
              · <span className="premium-oglas__cena-pod-poudarek">{s.c.naTeden} na teden</span>
            </span>
            {s.c.letno && (
              <span className="premium-oglas__nalepka" data-anim="nalepka">
                <span className="premium-oglas__nalepka-oznaka">Prihraniš</span>
                <span className="premium-oglas__nalepka-znesek">{s.c.prihranek}</span>
              </span>
            )}
          </div>
          <StikaloU21 s={s} />
          <div className="premium-oglas__noga">
            <Dokaz s={s} />
            <Cta s={s} />
            <Napaka s={s} />
            <Povezava s={s} />
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- 1b Ključavnica ---------- */

function Kljucavnica({ s, telefon }: { s: Stanje; telefon: boolean }) {
  if (telefon) {
    return (
      <div className="premium-oglas__zaslon">
        <header className="premium-oglas__glava">
          <div className="premium-oglas__logo">
            <Logo barve={['#FBF9F5', '#FBF9F5', '#14110F']} />
            Turnirko
          </div>
          <NeZdaj s={s} />
        </header>
        <div className="premium-oglas__prizor-pas">
          <span className="premium-oglas__oznaka">{s.k.kratko}</span>
          <div className="premium-oglas__prizor">
            <PrizorKljucavnica />
          </div>
        </div>
        <div className="premium-oglas__prodaja">
          <h2 className="premium-oglas__naslov">
            <span className="premium-oglas__naslov-nad">{s.k.bNad}</span>
            <span className="premium-oglas__naslov-glavni">{s.k.bGlavni}</span>
          </h2>
          <div className="premium-oglas__teden">
            <span className="premium-oglas__teden-cena">{s.c.naTeden}</span>
            <span className="premium-oglas__teden-pod">
              <span className="premium-oglas__teden-oznaka">Na teden</span>
              <span className="premium-oglas__teden-mono">
                {s.c.cena} {s.c.enota}
              </span>
            </span>
          </div>
          <Izbirnik s={s} svetel />
          <StikaloU21 s={s} prost />
          <div className="premium-oglas__noga">
            <Dokaz s={s} />
            <Cta s={s} />
          </div>
          <Napaka s={s} />
          <Povezava s={s} />
        </div>
      </div>
    )
  }

  return (
    <div className="premium-oglas__zaslon">
      <header className="premium-oglas__glava">
        <div className="premium-oglas__glava-levo">
          <div className="premium-oglas__logo">
            <Logo barve={['#FBF9F5', '#FBF9F5', '#14110F']} />
            Turnirko
          </div>
          <span className="premium-oglas__oznaka">{s.k.oznaka}</span>
        </div>
        <NeZdaj s={s} />
      </header>
      <div className="premium-oglas__telo">
        <div className="premium-oglas__prizor-stran">
          <div className="premium-oglas__prizor">
            <PrizorKljucavnica />
          </div>
        </div>
        <div className="premium-oglas__prodaja">
          <h2 className="premium-oglas__naslov">
            <span className="premium-oglas__naslov-nad">{s.k.bNad}</span>
            <span className="premium-oglas__naslov-glavni">{s.k.bGlavni}</span>
          </h2>
          <p className="premium-oglas__uvod">{s.k.uvod}</p>
          <div className="premium-oglas__teden">
            <span className="premium-oglas__teden-cena">{s.c.naTeden}</span>
            <span className="premium-oglas__teden-pod">
              <span className="premium-oglas__teden-oznaka">Na teden</span>
              <span className="premium-oglas__teden-mono">
                {s.c.cena} {s.c.enota}
              </span>
              <span className="premium-oglas__teden-mono">Manj kot kava v dvorani</span>
            </span>
          </div>
          <Izbirnik s={s} svetel />
          <StikaloU21 s={s} prost />
          <div className="premium-oglas__noga">
            <Dokaz s={s} />
            <Cta s={s} />
            <Napaka s={s} />
            <div className="premium-oglas__noga-vrsta">
              <Povezava s={s} />
              <span className="premium-oglas__noga-namig">Stripe · preklic kadarkoli</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ---------- 1c Končni izid ---------- */

function Racun({ s }: { s: Stanje }) {
  return (
    <>
      <div className="premium-oglas__racun">
        {s.c.letno ? (
          <>
            <div className="premium-oglas__racun-vrstica premium-oglas__racun-vrstica--sidro">
              <span>12 &times; {s.c.mesecna}</span>
              <span className="premium-oglas__precrtano">{s.c.sidro}</span>
            </div>
            <div className="premium-oglas__racun-vrstica premium-oglas__racun-vrstica--popust">
              <span>Letni popust &minus;10&nbsp;%</span>
              <span>&minus;{s.c.prihranek}</span>
            </div>
          </>
        ) : (
          <div className="premium-oglas__racun-vrstica premium-oglas__racun-vrstica--mesec">
            <span>1 mesec</span>
            <span>{s.c.cena}</span>
          </div>
        )}
        <div className="premium-oglas__skupaj">
          <span className="premium-oglas__skupaj-levo">
            <span className="premium-oglas__skupaj-oznaka">Skupaj {s.c.enota}</span>
            <span className="premium-oglas__skupaj-teden">{s.c.naTeden} na teden</span>
          </span>
          <span className="premium-oglas__skupaj-znesek">{s.c.cena}</span>
        </div>
      </div>
      {s.c.letno ? (
        <div className="premium-oglas__prihranek">
          <span className="premium-oglas__prihranek-znesek">Prihraniš {s.c.prihranek}</span>
          <span className="premium-oglas__prihranek-pod">
            Več kot mesec
            <br />
            zastonj
          </span>
        </div>
      ) : (
        <button type="button" className="premium-oglas__na-letno" onClick={() => s.nastaviCikel('LETNO')}>
          <span>Z letnim paketom prihraniš {s.c.prihranekLetni}</span>
          <span>&rarr;</span>
        </button>
      )}
    </>
  )
}

function KoncniIzid({ s, telefon }: { s: Stanje; telefon: boolean }) {
  if (telefon) {
    return (
      <div className="premium-oglas__zaslon">
        <header className="premium-oglas__glava-omot">
          <div className="premium-oglas__glava">
            <div className="premium-oglas__logo">
              <Logo barve={['#14110F', '#14110F', '#0088CE']} />
              Turnirko
            </div>
            <NeZdaj s={s} />
          </div>
          <div className="premium-oglas__dvojna-crta">
            <div />
          </div>
        </header>
        <div className="premium-oglas__drsenje">
          <div className="premium-oglas__pas">
            <div className="premium-oglas__pas-besedilo">
              <span className="premium-oglas__oznaka">{s.k.kratko}</span>
              <span className="premium-oglas__pas-podnapis">Končni izid · Free proti Premium</span>
            </div>
            <div className="premium-oglas__pas-lik">
              <PremiumLik poza="semafor" />
            </div>
          </div>
          <Izid s={s} telefon />
          <Primerjava s={s} />
          <div className="premium-oglas__nastavitve">
            <Izbirnik s={s} />
            <StikaloU21 s={s} />
            {s.c.letno ? (
              <div className="premium-oglas__racun">
                <div className="premium-oglas__racun-vrstica premium-oglas__racun-vrstica--sidro">
                  <span>12 &times; {s.c.mesecna}</span>
                  <span className="premium-oglas__precrtano">{s.c.sidro}</span>
                </div>
                <div className="premium-oglas__racun-vrstica premium-oglas__racun-vrstica--popust">
                  <span>Letni popust &minus;10&nbsp;%</span>
                  <span>&minus;{s.c.prihranek}</span>
                </div>
              </div>
            ) : (
              <button type="button" className="premium-oglas__na-letno" onClick={() => s.nastaviCikel('LETNO')}>
                <span>Z letnim prihraniš {s.c.prihranekLetni}</span>
                <span>&rarr;</span>
              </button>
            )}
          </div>
        </div>
        <div className="premium-oglas__spodaj">
          <Dokaz s={s} sIgra={false} />
          <div className="premium-oglas__spodaj-cena">
            <span className="premium-oglas__spodaj-levo">
              <span className="premium-oglas__spodaj-znesek">{s.c.cena}</span>
              <span className="premium-oglas__enota">{s.c.enota}</span>
            </span>
            {s.c.letno ? (
              <span className="premium-oglas__spodaj-znacka">Prihraniš {s.c.prihranek}</span>
            ) : (
              <span className="premium-oglas__spodaj-teden">{s.c.naTeden} / teden</span>
            )}
          </div>
          <Cta s={s} />
          <Napaka s={s} />
          <Povezava s={s} />
        </div>
      </div>
    )
  }

  return (
    <div className="premium-oglas__zaslon">
      <header className="premium-oglas__glava-omot">
        <div className="premium-oglas__glava">
          <div className="premium-oglas__glava-levo">
            <div className="premium-oglas__logo">
              <Logo barve={['#14110F', '#14110F', '#0088CE']} />
              Turnirko
            </div>
            <span className="premium-oglas__oznaka">{s.k.oznaka}</span>
          </div>
          <NeZdaj s={s} />
        </div>
        <div className="premium-oglas__dvojna-crta">
          <div />
        </div>
      </header>
      <div className="premium-oglas__telo">
        <div className="premium-oglas__levo">
          <div className="premium-oglas__pas">
            <div className="premium-oglas__pas-besedilo">
              <span className="premium-oglas__pas-podnapis">Končni izid · Free proti Premium</span>
              <p className="premium-oglas__uvod">{s.k.uvod}</p>
            </div>
            <div className="premium-oglas__pas-lik">
              <PremiumLik poza="semafor" />
            </div>
          </div>
          <Izid s={s} telefon={false} />
          <Primerjava s={s} />
        </div>
        <div className="premium-oglas__desno">
          <div className="premium-oglas__listek">
            <div className="premium-oglas__listek-glava">
              <span className="premium-oglas__listek-naslov">Listek · Igralec Premium</span>
              <span className="premium-oglas__listek-stripe">Stripe</span>
            </div>
            <div className="premium-oglas__listek-telo">
              <Izbirnik s={s} />
              <Racun s={s} />
              <StikaloU21 s={s} />
            </div>
            <div className="premium-oglas__listek-crta" />
            <div className="premium-oglas__listek-noga">
              <Dokaz s={s} sIgra />
              <Cta s={s} />
              <Napaka s={s} />
              <Povezava s={s} />
            </div>
          </div>
          <span className="premium-oglas__listek-namig">Varno plačilo · preklic kadarkoli</span>
        </div>
      </div>
    </div>
  )
}

/* ---------- Lupina ---------- */

export interface LastnostiOglasa {
  kontekst: KontekstOglasa
  razlicica: RazlicicaOglasa
  onZapri: () => void
  /* Gost: ustvari račun z izbranim ciklom / odpri prijavo. Okno registracije živi
     v gostitelju in ne v oglasu, ker oglas ob tem izgine. */
  onUstvariRacun: (ciklus: CiklusPlacila) => void
  onPrijava: () => void
}

export function PremiumOglas({
  kontekst,
  razlicica,
  onZapri,
  onUstvariRacun,
  onPrijava,
}: LastnostiOglasa) {
  const { uporabnik } = useAvtentikacija()
  const telefon = useTelefonOglasa()
  const koren = useRef<HTMLDivElement>(null)
  const ctaRef = useRef<HTMLButtonElement>(null)
  const stevecF = useRef<HTMLSpanElement>(null)
  const stevecP = useRef<HTMLSpanElement>(null)

  const gost = uporabnik === null
  /* Pas prijavljenega igralca določa račun in po njem Stripe računa; brez
     datuma rojstva (null) ostane stikalo kot pri gostu. */
  const znanPas = uporabnik !== null && typeof uporabnik.starejsiOd21 === 'boolean'

  const [cikel, nastaviCikel] = useState<CiklusPlacila>('LETNO')
  const [u21, nastaviU21] = useState(() => (znanPas ? uporabnik?.starejsiOd21 === false : false))
  const [zapriViden, nastaviZapriViden] = useState(false)
  const [izid, nastaviIzid] = useState<{ f: number; p: number }>({
    f: KONCNI_IZID.free,
    p: KONCNI_IZID.premium,
  })

  const k = KONTEKSTI[kontekst]
  const c = izracunajCene(cikel, u21)

  const dokaz = useQuery({
    queryKey: ['premium-dokaz', uporabnik?.id ?? null],
    queryFn: premiumApi.dokaz,
    staleTime: 5 * 60_000,
    retry: false,
  })
  /* Za pregled ob razvoju (`?oglas=1c&dokaz=412`) lahko število nadomestimo, da
     se ujema s predajo; v izdelku števila ne določa naslov. */
  const dokazZaPrikaz = dokazIzNaslova() ?? dokaz.data ?? null

  const nadgradi = useMutation({
    mutationFn: () => placilaApi.nadgradnja({ paket: 'PREMIUM', ciklus: cikel }),
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
  })

  const onCta = useCallback(() => {
    if (gost) {
      onUstvariRacun(cikel)
    } else {
      nadgradi.mutate()
    }
  }, [gost, cikel, onUstvariRacun, nadgradi])

  /* »Ne zdaj« se pojavi po 2 s in šele takrat deluje Escape. */
  useEffect(() => {
    const casovnik = window.setTimeout(() => nastaviZapriViden(true), ZAMIK_ZAPIRANJA_MS)
    return () => window.clearTimeout(casovnik)
  }, [])

  useEffect(() => {
    if (!zapriViden) return
    const obEscape = (d: KeyboardEvent) => {
      if (d.key === 'Escape') onZapri()
    }
    window.addEventListener('keydown', obEscape)
    return () => window.removeEventListener('keydown', obEscape)
  }, [zapriViden, onZapri])

  /* Pravi modal (isti prijemi kot ModalnoOkno): ozadje `inert`, telo ne drsi,
     fokus na glavnem gumbu, ob zaprtju nazaj na sprožilec. */
  const sprozilec = useRef<HTMLElement | null>(null)
  if (sprozilec.current === null && typeof document !== 'undefined') {
    sprozilec.current = document.activeElement as HTMLElement | null
  }
  useEffect(() => {
    const ozadje = document.getElementById('koren')
    const prejsnjiOverflow = document.body.style.overflow
    ozadje?.setAttribute('inert', '')
    document.body.style.overflow = 'hidden'
    return () => {
      ozadje?.removeAttribute('inert')
      document.body.style.overflow = prejsnjiOverflow
      const nazaj = sprozilec.current
      if (nazaj?.isConnected) nazaj.focus()
    }
  }, [])

  /* Gibanje: zavesa, nalepka in žig; maskota in ključavnica se predvajata sami
     (PremiumOglasPrizori). Ob zamenjavi postavitve (zasuk telefona) se vse zažene
     znova, ker je drevo drugo. */
  useLayoutEffect(() => {
    if (!koren.current) return
    return predvajajOglas(koren.current)
  }, [telefon, razlicica])

  useLayoutEffect(() => {
    /* Fokus ob odprtju: glavni gumb (stoji v drevesu takoj, gumb »Ne zdaj« pa
       do zamika ni dosegljiv s tabulatorjem). */
    ctaRef.current?.focus({ preventScroll: true })
  }, [telefon, razlicica])

  /* Števec izida (1c): šteje od 0 : 0 do 2 : 6 po točkah v predpisanem zaporedju;
     ob vsaki točki številka poskoči. Brez gibanja takoj končni izid. */
  useLayoutEffect(() => {
    if (razlicica !== '1c') return
    if (jeMirno()) {
      nastaviIzid({ f: KONCNI_IZID.free, p: KONCNI_IZID.premium })
      return
    }
    nastaviIzid({ f: 0, p: 0 })
    const casovniki: number[] = []
    let f = 0
    let p = 0
    /* Prva točka pade pri 400 ms, prvih pet je med sabo 210 ms narazen, zadnje tri
       170 ms (predaja). Prototip je razmike zapisal kot `i * (i < 5 ? 210 : 170)`,
       kar šesto točko pomakne na 10 ms za peto; tu se razmiki seštevajo. */
    let cas = 400
    TOCKE_IZIDA.forEach((kdo, i) => {
      if (i > 0) cas += i <= 4 ? 210 : 170
      casovniki.push(
        window.setTimeout(() => {
          if (kdo === 'F') f++
          else p++
          nastaviIzid({ f, p })
          /* Element se po ponovnem izrisu ne zamenja (isti ključ), zato je poskok
             mogoče sprožiti takoj. */
          poskokStevilke(kdo === 'F' ? stevecF.current : stevecP.current)
        }, cas),
      )
    })
    return () => casovniki.forEach((t) => window.clearTimeout(t))
  }, [razlicica, telefon])

  const stanje: Stanje = {
    k,
    c,
    cikel,
    nastaviCikel,
    u21,
    znanPas,
    preklopU21: () => nastaviU21((v) => !v),
    gost,
    cta: gost ? `Ustvari račun · Premium ${c.cena}` : `Nadgradi na Premium · ${c.cena}`,
    ctaDrugo: gost ? 'Že imaš račun? Prijavi se →' : 'Primerjaj in spremeni paket v Naročnini →',
    dokaz: dokazZaPrikaz,
    zapriViden,
    onZapri,
    onCta,
    onDrugo: onPrijava,
    poteka: nadgradi.isPending || nadgradi.isSuccess,
    napaka: nadgradi.error ? opisNapake(nadgradi.error) : null,
    ctaRef,
    izid,
    stevecF,
    stevecP,
  }

  let zaslon: ReactNode
  if (razlicica === '1a') zaslon = <Zavesa s={stanje} telefon={telefon} />
  else if (razlicica === '1b') zaslon = <Kljucavnica s={stanje} telefon={telefon} />
  else zaslon = <KoncniIzid s={stanje} telefon={telefon} />

  const razredi =
    'premium-oglas'
    + ` premium-oglas--${razlicica === '1a' ? 'zavesa' : razlicica === '1b' ? 'kljucavnica' : 'izid'}`
    + (telefon ? ' premium-oglas--telefon' : '')

  return createPortal(
    <div
      ref={koren}
      className={razredi}
      role="dialog"
      aria-modal="true"
      aria-label={`Igralec Premium · ${k.kratko}`}
    >
      {zaslon}
    </div>,
    document.body,
  )
}

/* Razvojni pregled: `?oglas=1a&kontekst=lige&dokaz=412`. Število socialnega
   dokaza se sme podati samo v razvoju (`npm run dev`) — v izdelku bi bila
   povezava s poljubnim številom laž, ki jo lahko nekdo pošlje naprej. */
function dokazIzNaslova(): PremiumDokazDto | null {
  if (!import.meta.env.DEV || typeof window === 'undefined') return null
  const stevilo = Number(new URLSearchParams(window.location.search).get('dokaz'))
  return Number.isFinite(stevilo) && stevilo > 0 ? { stevilo, izKluba: true } : null
}
