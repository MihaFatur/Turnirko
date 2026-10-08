/* Javna razlaga Turnirko ratinga (/o-ratingu).

   Za koga: igralec, ki je po enem porazu izgubil 113 točk, rekreativec, ki se
   ne najde na lestvici, mladinec, ki je zmagal in dobil tri točke. Številka
   brez razlage je videti kot napaka ali naklonjenost. Stran zato pove, kako
   rating nastane, in naravnost odgovori na vprašanja »zakaj …?«, ki jih
   ljudje res postavljajo.

   Dva preizkusa: ena tekma (kaj prinese zmaga in kaj vzame poraz) in prvi dan
   novinca (zakaj številka skače). Oba računa strežnik po istih pravilih kot
   obračun (RazlagaRatingaStoritev) - vmesnik ne podvaja obrazca, test v
   zaledju pa drži, da preizkus prvega dne da isto kot pravi obračun.

   Številke pravil (K, teže, odbitki, sidra) pridejo s strežnika: ob umeritvi
   se spremenijo in razlaga mora govoriti o pravilu, po katerem obračun teče. */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { razlagaRatingaApi } from '../api/zahteve'
import type { PravilaRatingaDto, RavenTekmovanja } from '../api/tipi'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { StevilskoPolje } from '../komponente/StevilskoPolje'
import { useNaslovStrani } from '../pomozno/naslovStrani'

const RAVNI: RavenTekmovanja[] = ['URADNO', 'KLUBSKO', 'REKREATIVNO']

const KRATKO_RAVEN: Record<RavenTekmovanja, string> = {
  URADNO: 'Uradno',
  KLUBSKO: 'Klubsko',
  REKREATIVNO: 'Rekreativno',
  NE_STEJE: '–',
}

const PRIMERI_RAVNI: Record<RavenTekmovanja, string> = {
  URADNO: 'turnirji in lige NTZS',
  KLUBSKO: 'npr. Savinja liga',
  REKREATIVNO: 'rekreativni turnirji in lige',
  NE_STEJE: '',
}

/* Najnižji in najvišji rating, ki ga preizkus sprejme (isti meji kot pri
   postavitvenem ratingu v zaledju). */
const NAJMANJ = 100
const NAJVEC = 3000
const NAJVEC_TEKEM_DNE = 12

type Izkusnja = 'NOVINEC' | 'NEUSTALJEN' | 'USTALJEN'

const KAZALO: { id: string; naslov: string }[] = [
  { id: 'osnova', naslov: 'Kako nastane' },
  { id: 'preizkus', naslov: 'Preizkusi tekmo' },
  { id: 'prvi-dan', naslov: 'Prvi dan novinca' },
  { id: 'zakaj', naslov: 'Zakaj …?' },
  { id: 'lestvica', naslov: 'Lestvica' },
]

export function RazlagaRatingaStran() {
  useNaslovStrani('Kako se računa Turnirko rating')
  const pravila = useQuery({ queryKey: ['rating-pravila'], queryFn: razlagaRatingaApi.pravila })
  const lokacija = useLocation()

  /* Povezava z drugih strani pride s sidrom (#prvi-dan, #lestvica). Router
     do njega sam ne podrsa, sekcije pa se izrišejo šele s pravili. */
  useEffect(() => {
    if (!lokacija.hash || !pravila.data) return
    document.getElementById(lokacija.hash.slice(1))?.scrollIntoView()
  }, [lokacija.hash, pravila.data])

  return (
    <section className="razlaga">
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Turnirko rating</span>
            <span className="naslov-strani__glavni">Kako deluje</span>
          </h1>
          <p className="uvod">
            Rating je ena številka, ki pove, kako močno igraš v primerjavi z ostalimi igralci na
            tej strani. Računa se samo iz zmag in porazov. Spodaj je, kako nastane, zakaj se
            včasih premakne bolj, kot pričakuješ, in dva preizkusa s tvojimi številkami.
          </p>
        </div>
      </div>

      <nav className="razlaga__kazalo" aria-label="Vsebina strani">
        {KAZALO.map((k) => (
          <a key={k.id} href={`#${k.id}`}>
            {k.naslov}
          </a>
        ))}
      </nav>

      <NapakaPoizvedbe poizvedba={pravila} kaj="pravil ratinga" />
      {pravila.isPending && <p className="obvestilo">Nalaganje …</p>}
      {pravila.data && <Vsebina p={pravila.data} />}
    </section>
  )
}

function Vsebina({ p }: { p: PravilaRatingaDto }) {
  const kNeustaljen = p.kOsnovni + p.pribitekNeustaljen
  const kNovinec = kNeustaljen + p.pribitekNovinec
  const teza = (r: RavenTekmovanja) => p.ravni.find((x) => x.raven === r)?.teza ?? 1
  const napoved200 = p.napovedi.find((n) => n.razlika === 200)?.odstotek ?? 76

  return (
    <>
      {/* ---------- Osnova ---------- */}
      <Sekcija id="osnova" naslov="Kako nastane">
        <p>
          Pred vsako tekmo razlika ratingov napove, kolikšna je možnost zmage. Kdor ima 200 točk
          več, po številkah zmaga v {napoved200} % tekem:
        </p>
        <div className="razlaga__tabela" role="table" aria-label="Razlika ratingov in možnost zmage">
          <div className="razlaga__vrstica razlaga__vrstica--glava" role="row">
            <span role="columnheader">Razlika</span>
            <span role="columnheader" className="razlaga__desno">Možnost zmage boljšega</span>
          </div>
          {p.napovedi.map((n) => (
            <div key={n.razlika} className="razlaga__vrstica" role="row">
              <span role="cell">{n.razlika === 0 ? 'enak rating' : `${n.razlika} točk`}</span>
              <span role="cell" className="razlaga__desno razlaga__stevilka">{n.odstotek} %</span>
            </div>
          ))}
        </div>
        <p>
          Po tekmi se rating premakne za razliko med <strong>doseženim</strong> in{' '}
          <strong>pričakovanim</strong>:
        </p>
        <p className="razlaga__obrazec">sprememba = K × teža × (izid − pričakovano)</p>
        <ul className="seznam-preprost">
          <li>
            <strong>Izid</strong> je 1 za zmago in 0 za poraz. <strong>Zmaga je zmaga</strong>:
            3 : 0 in 3 : 2 prineseta isto, tudi predaja.
          </li>
          <li>
            <strong>Pričakovano</strong> je možnost zmage iz tabele zgoraj. Zmaga proti
            močnejšemu prinese veliko, zmaga proti šibkejšemu malo — bila je pričakovana. Pri
            porazu je obratno.
          </li>
          <li>
            <strong>K</strong> pove, kako hitro se rating premika. Kdor je odigral vsaj{' '}
            {p.pragUstaljen} tekem, ima K {p.kOsnovni}. Manj kot {p.pragUstaljen} tekem: K{' '}
            {kNeustaljen}. Manj kot {p.pragNovinec} tekem: K {kNovinec}. Kdor se vrne po več kot{' '}
            {p.mesecevZaVrnitev} mesecih, ima prvih {p.tekemPoVrnitvi} tekem K večji za{' '}
            {p.pribitekVrnitev}. O novem igralcu vemo malo, zato se njegova številka hitreje
            približa pravi.
          </li>
          <li>
            <strong>Teža</strong> je odvisna od tekmovanja:{' '}
            {RAVNI.map((r, i) => (
              <span key={r}>
                {i > 0 && (i === RAVNI.length - 1 ? ' in ' : ', ')}
                {KRATKO_RAVEN[r].toLowerCase()} {odstotek(teza(r))} ({PRIMERI_RAVNI[r]})
              </span>
            ))}
            . Zmaga na državnem prvenstvu pove več kot zmaga na rekreativnem turnirju.
          </li>
          <li>
            Kar eden dobi, drugi izgubi — dokler imata enak K. Če ima eden večji K (novinec),
            se premakne bolj kot nasprotnik.
          </li>
        </ul>
      </Sekcija>

      {/* ---------- Preizkus tekme ---------- */}
      <Sekcija id="preizkus" naslov="Preizkusi tekmo">
        <p>
          Vpiši svoj rating in rating nasprotnika ter poglej, kaj bi prinesla zmaga in kaj vzel
          poraz.
        </p>
        <PreizkusTekme p={p} />
      </Sekcija>

      {/* ---------- Prvi dan ---------- */}
      <Sekcija id="prvi-dan" naslov="Prvi dan novinca">
        <p>
          Novinec ne začne pri 1000, ampak pri <strong>sredini igralcev svoje starosti in
          spola</strong> (starostno sidro). Dva novinca v U11 sta tako bližje drug drugemu kot
          novincu v U19. Igralec, ki ga organizator ob vpisu označi kot rekreativca, začne pri{' '}
          {p.rekreativniZacetek}.
        </p>
        <Sidra sidra={p.sidra} />
        <p>
          Prvi dan igranja — prvi turnir ali prvi ligaški večer — se rating <strong>ne sešteva
          po tekmah</strong>. Po vsaki tekmi od druge naprej se izračuna znova iz{' '}
          <strong>vseh izidov tega dne</strong>: poiščemo številko, ki tvoje zmage in poraze
          najbolje razloži. Zraven štejeta še {p.navideznihTekem} navidezni neodločeni tekmi
          proti izhodišču, da ena srečna zmaga novinca ne odnese previsoko.
        </p>
        <p>
          Zato prvi dan številka skače: poraz v tretji tekmi lahko vzame več kot sto točk, ker
          v novo luč postavi tudi zmage pred njim. Od drugega dne naprej teče rating po tekmah,
          kot pri vseh.
        </p>
        <PreizkusPrvegaDne p={p} />
      </Sekcija>

      {/* ---------- Zakaj ---------- */}
      <Sekcija id="zakaj" naslov="Zakaj …?">
        <Vprasanje naslov="Zakaj se rating v enem večeru spusti za več kot 100 točk?">
          Najverjetneje je bil to prvi dan igranja. Takrat se rating po vsaki tekmi izračuna znova iz
          vseh izidov dneva, zato se številka v zapisniku premakne za stotine točk.{' '}
          <a href="#prvi-dan">Preizkusi s svojimi izidi →</a>
        </Vprasanje>
        <Vprasanje naslov="Zakaj zmaga prinese samo nekaj točk?">
          Ker je bila zmaga pričakovana. Proti igralcu z veliko nižjim ratingom je možnost zmage
          velika, zato je razlika med doseženim in pričakovanim majhna. Poraz proti istemu
          igralcu bi vzel veliko.
        </Vprasanje>
        <Vprasanje naslov="Zakaj 3 : 0 in 3 : 2 prineseta isto?">
          Šteje, kdo je zmagal, ne kako. Rezultat se prepisuje s papirja, tekme se končajo tudi s
          predajo, pri starih tekmah je zapisan samo zmagovalec — vse to se obračuna enako.
          Merjenje na pravih tekmah je pokazalo, da izid v nizih napovedi ne izboljša.
        </Vprasanje>
        <Vprasanje naslov="Zakaj ena stran dobi več, kot druga izgubi?">
          Ker imata različen K. Novinec ali igralec po vrnitvi ima večji K, zato se premakne bolj.
          Pri enakem K je vsota obeh sprememb natanko nič.
        </Vprasanje>
        <Vprasanje naslov="Zakaj je zmaga v Savinja ligi vredna manj kot na turnirju NTZS?">
          Vsako tekmovanje ima težo: uradno {odstotek(teza('URADNO'))}, klubsko{' '}
          {odstotek(teza('KLUBSKO'))}, rekreativno {odstotek(teza('REKREATIVNO'))}. Tekma je
          tekma — v število odigranih šteje enako —, rating pa premakne manj.
        </Vprasanje>
        <Vprasanje naslov="Zakaj se rating spremeni brez tekme?">
          Dva razloga. Kdor dolgo ne igra, dobi majhen odbitek:{' '}
          {p.odbitki
            .map((o) => `po ${o.mesecev} mesecih skupaj −${o.skupaj}`)
            .join(', ')}
          ; potem nič več. Drugi razlog je popravek: če organizator za nazaj popravi rezultat ali
          vpiše starejše kolo, se rating od dneva te tekme izračuna znova — tvoja tekma je
          vplivala na vse poznejše.
        </Vprasanje>
        <Vprasanje naslov="Zakaj mesto na lestvici pade brez tekme?">
          Mesto je primerjava: če igralci pod tabo zmagujejo, te prehitijo — tudi brez tvoje tekme. Puščica ob imenu kaže
          premik mest v zadnjih 30 dneh.
        </Vprasanje>
        <Vprasanje naslov="Zakaj ima nekdo rating z napisanim virom?">
          Nekateri igralci pri nas odigrajo le nekaj tekem na leto, ker igrajo v tujini. Njihovo
          moč administrator prepiše z zunanje lestvice (npr. ITTF) — vir in pojasnilo sta vedno
          javno izpisana na profilu.
        </Vprasanje>
        <Vprasanje naslov="Ali je to uradna jakostna lestvica NTZS?">
          Ne. Turnirko rating je lastna ocena Turnirka iz vseh tekem v bazi — uradnih, klubskih in
          rekreativnih. Uradne jakostne lestvice vodi Namiznoteniška zveza Slovenije.
        </Vprasanje>
        <Vprasanje naslov="Kako vemo, da so številke prave?">
          K in teže niso ugibanje: izmerjene so na desettisočih tekem iz zgodovine NTZS tako, da
          rating vsako tekmo najprej napove in se šele nato posodobi. Izbrane so vrednosti, ki
          izide napovedo najbolje.
        </Vprasanje>
      </Sekcija>

      {/* ---------- Lestvica ---------- */}
      <Sekcija id="lestvica" naslov="Kdo je na lestvici">
        <ul className="seznam-preprost">
          <li>
            <strong>Moški in ženske imajo vsak svojo lestvico.</strong> Med njimi skoraj ni
            tekem, zato številki nista primerljivi in skupno mesto ne bi pomenilo ničesar.
          </li>
          <li>
            <strong>Rekreativci so svoja lestvica</strong>: kdor še ni odigral{' '}
            {p.pragRekreativca} tekem na uradnem ali klubskem tekmovanju. Ko jih odigra, gre med
            tekmovalce in tam ostane.
          </li>
          <li>
            Na lestvici ni, kdor <strong>še nima ratinga</strong> (nobena tekma še ni štela
            vanj) ali <strong>{p.mesecevDoSkritja} mesecev ni igral</strong>. Rating mu ostane
            in je viden na profilu; ko spet zaigra, se vrne.
          </li>
          <li>
            Kategorije (U21, U19 …) so izbor iste lestvice: med člani so vsi tekmovalci, med U19
            samo igralci do 19 let. Starost se meri po pravilu NTZS na 31. december leta, v
            katerem se sezona začne.
          </li>
          <li>Rating ne more pasti pod {p.spodnjaMeja}.</li>
        </ul>
        <p>
          <Link to="/lestvica" className="gumb">
            Na lestvico
          </Link>
        </p>
      </Sekcija>
    </>
  )
}

function Sekcija({ id, naslov, children }: { id: string; naslov: string; children: ReactNode }) {
  return (
    <section id={id} className="razlaga__sekcija" aria-labelledby={`${id}-naslov`}>
      <div className="naslovna-vrstica">
        <h2 id={`${id}-naslov`}>{naslov}</h2>
      </div>
      {children}
    </section>
  )
}

/* Vprašanje je zaprto, dokler ga gledalec ne odpre: odgovorov je deset in na
   telefonu bi sicer zavzeli več zaslonov. */
function Vprasanje({ naslov, children }: { naslov: string; children: ReactNode }) {
  return (
    <details className="razlaga__vprasanje">
      <summary>{naslov}</summary>
      <p>{children}</p>
    </details>
  )
}

/* Kje začne novinec dane starosti, za oba spola v eni tabeli. Zaporedne
   starosti z isto vrednostjo pri obeh spolih so ena vrstica (»10–14 let«) -
   sidro je zravnano in pri najmlajših enako, trikrat ista vrstica pa bi se
   brala kot napaka. */
function Sidra({ sidra }: { sidra: PravilaRatingaDto['sidra'] }) {
  const vrednost = (spol: 'MOSKI' | 'ZENSKI', starost: number) =>
    sidra.find((s) => s.spol === spol && s.starost === starost)?.vrednost ?? '—'
  const vse = [...new Set(sidra.map((s) => s.starost))].sort((a, b) => a - b)
  const razponi: { od: number; do: number }[] = []
  for (const st of vse) {
    const zadnji = razponi[razponi.length - 1]
    if (
      zadnji
      && vrednost('MOSKI', zadnji.od) === vrednost('MOSKI', st)
      && vrednost('ZENSKI', zadnji.od) === vrednost('ZENSKI', st)
    ) {
      zadnji.do = st
    } else {
      razponi.push({ od: st, do: st })
    }
  }
  if (razponi.length === 0) return null
  const zadnja = vse[vse.length - 1]
  const napis = (r: { od: number; do: number }) =>
    r.do === zadnja ? `${r.od} let in več` : r.od === r.do ? `${r.od} let` : `${r.od}–${r.do} let`
  return (
    <div className="razlaga__tabela" role="table" aria-label="Kje začne novinec">
      <div className="razlaga__vrstica razlaga__vrstica--tri razlaga__vrstica--glava" role="row">
        <span role="columnheader">Starost novinca</span>
        <span role="columnheader" className="razlaga__desno">Moški</span>
        <span role="columnheader" className="razlaga__desno">Ženske</span>
      </div>
      {razponi.map((r) => (
        <div key={r.od} className="razlaga__vrstica razlaga__vrstica--tri" role="row">
          <span role="cell">{napis(r)}</span>
          <span role="cell" className="razlaga__desno razlaga__stevilka">{vrednost('MOSKI', r.od)}</span>
          <span role="cell" className="razlaga__desno razlaga__stevilka">{vrednost('ZENSKI', r.od)}</span>
        </div>
      ))}
    </div>
  )
}

/* ---------- Preizkus ene tekme ---------- */

function PreizkusTekme({ p }: { p: PravilaRatingaDto }) {
  const [rating, nastaviRating] = useState('1200')
  const [nasprotnik, nastaviNasprotnika] = useState('1350')
  const [izkusnja, nastaviIzkusnjo] = useState<Izkusnja>('USTALJEN')
  const [izkusnjaNasprotnika, nastaviIzkusnjoNasprotnika] = useState<Izkusnja>('USTALJEN')
  const [vrnitev, nastaviVrnitev] = useState(false)
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('URADNO')

  const tekem = (i: Izkusnja) => (i === 'NOVINEC' ? 0 : i === 'NEUSTALJEN' ? p.pragNovinec : p.pragUstaljen)
  const veljaven = (v: string) => v !== '' && Number(v) >= NAJMANJ && Number(v) <= NAJVEC
  const vnos = {
    rating: Number(rating),
    nasprotnik: Number(nasprotnik),
    tekem: tekem(izkusnja),
    tekemNasprotnika: tekem(izkusnjaNasprotnika),
    vrnitev,
    vrnitevNasprotnika: false,
  }
  const izracun = useQuery({
    queryKey: ['rating-izracun', vnos],
    queryFn: () => razlagaRatingaApi.izracun(vnos),
    enabled: veljaven(rating) && veljaven(nasprotnik),
    placeholderData: (prej) => prej,
  })
  const r = izracun.data?.ravni.find((x) => x.raven === raven)
  const verjetnost = (izracun.data?.pricakovanOdstotek ?? 50) / 100

  return (
    <div className="razlaga__preizkus">
      <div className="razlaga__polja">
        <label className="obrazec__polje">
          <span>Tvoj rating</span>
          <StevilskoPolje vrednost={rating} naSpremembo={nastaviRating} najvec={NAJVEC} />
        </label>
        <label className="obrazec__polje">
          <span>Rating nasprotnika</span>
          <StevilskoPolje vrednost={nasprotnik} naSpremembo={nastaviNasprotnika} najvec={NAJVEC} />
        </label>
      </div>
      {(!veljaven(rating) || !veljaven(nasprotnik)) && (
        <p className="namig">Rating mora biti med {NAJMANJ} in {NAJVEC}.</p>
      )}

      <IzbiraIzkusnje
        oznaka="Tvojih odigranih tekem"
        p={p}
        izbrana={izkusnja}
        naIzbiro={nastaviIzkusnjo}
      />
      <label className="razlaga__kljukica">
        <input type="checkbox" checked={vrnitev} onChange={(d) => nastaviVrnitev(d.target.checked)} />
        <span>Vračam se po več kot {p.mesecevZaVrnitev} mesecih brez tekme</span>
      </label>
      <IzbiraIzkusnje
        oznaka="Odigranih tekem nasprotnika"
        p={p}
        izbrana={izkusnjaNasprotnika}
        naIzbiro={nastaviIzkusnjoNasprotnika}
      />

      <span className="razlaga__oznaka">Tekmovanje</span>
      <div className="izbirnik razlaga__izbirnik">
        {RAVNI.map((v) => (
          <button
            key={v}
            type="button"
            className={'izbirnik__gumb' + (raven === v ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={raven === v}
            onClick={() => nastaviRaven(v)}
          >
            {KRATKO_RAVEN[v]}
          </button>
        ))}
      </div>

      <NapakaPoizvedbe poizvedba={izracun} kaj="izračuna" />
      {izracun.data && r && (
        <div className="razlaga__izid" aria-live="polite">
          <p className="razlaga__verjetnost">
            Možnost tvoje zmage: <strong>{izracun.data.pricakovanOdstotek} %</strong>
          </p>
          <div className="profil__kazalniki">
            <div className="kazalnik kazalnik--prvi">
              <div className="kazalnik__vrednost profil__zmaga">{sPredznakom(r.zmaga.sprememba)}</div>
              <div className="kazalnik__oznaka">Če zmagaš → {r.zmaga.rating}</div>
            </div>
            <div className="kazalnik">
              <div className="kazalnik__vrednost profil__poraz">{sPredznakom(r.poraz.sprememba)}</div>
              <div className="kazalnik__oznaka">Če izgubiš → {r.poraz.rating}</div>
            </div>
          </div>
          <p className="razlaga__racun">
            Zmaga: K {izracun.data.k} × {odstotek(r.teza)} × (1 − {decimalno(verjetnost)}) ≈{' '}
            {sPredznakom(r.zmaga.sprememba)}
            <br />
            Poraz: K {izracun.data.k} × {odstotek(r.teza)} × (0 − {decimalno(verjetnost)}) ≈{' '}
            {sPredznakom(r.poraz.sprememba)}
          </p>
          <p className="namig">
            Nasprotnik ob tvoji zmagi {sPredznakom(r.zmaga.spremembaNasprotnika)}, ob tvojem
            porazu {sPredznakom(r.poraz.spremembaNasprotnika)}
            {izracun.data.k !== izracun.data.kNasprotnika &&
              ` (K nasprotnika je ${izracun.data.kNasprotnika}, zato se premakne drugače kot ti)`}
            .
          </p>
        </div>
      )}
    </div>
  )
}

function IzbiraIzkusnje({
  oznaka,
  p,
  izbrana,
  naIzbiro,
}: {
  oznaka: string
  p: PravilaRatingaDto
  izbrana: Izkusnja
  naIzbiro: (v: Izkusnja) => void
}) {
  const moznosti: { v: Izkusnja; napis: string }[] = [
    { v: 'NOVINEC', napis: `Manj kot ${p.pragNovinec}` },
    { v: 'NEUSTALJEN', napis: `${p.pragNovinec}–${p.pragUstaljen - 1}` },
    { v: 'USTALJEN', napis: `${p.pragUstaljen} ali več` },
  ]
  return (
    <>
      <span className="razlaga__oznaka">{oznaka}</span>
      <div className="izbirnik razlaga__izbirnik">
        {moznosti.map((m) => (
          <button
            key={m.v}
            type="button"
            className={'izbirnik__gumb' + (izbrana === m.v ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={izbrana === m.v}
            onClick={() => naIzbiro(m.v)}
          >
            {m.napis}
          </button>
        ))}
      </div>
    </>
  )
}

/* ---------- Preizkus prvega dne ---------- */

interface TekmaDne {
  nasprotnik: string
  zmaga: boolean
}

/* Primer, ki pokaže skok: rekreativec začne pri 800, dobi prvo tekmo, nato
   izgubi dve proti šibkejšima - druga zmaga številko spet dvigne. */
const PRIMER_DNE: TekmaDne[] = [
  { nasprotnik: '960', zmaga: true },
  { nasprotnik: '700', zmaga: false },
  { nasprotnik: '650', zmaga: false },
  { nasprotnik: '1000', zmaga: true },
]

function PreizkusPrvegaDne({ p }: { p: PravilaRatingaDto }) {
  const [izhodisce, nastaviIzhodisce] = useState(String(p.rekreativniZacetek))
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('KLUBSKO')
  const [tekme, nastaviTekme] = useState<TekmaDne[]>(PRIMER_DNE)

  const veljaven = (v: string) => v !== '' && Number(v) >= NAJMANJ && Number(v) <= NAJVEC
  const vseVeljavne = veljaven(izhodisce) && tekme.length > 0 && tekme.every((t) => veljaven(t.nasprotnik))
  const vnos = useMemo(
    () => tekme.map((t) => ({ nasprotnik: Number(t.nasprotnik), zmaga: t.zmaga })),
    [tekme],
  )
  const dan = useQuery({
    queryKey: ['rating-prvi-dan', izhodisce, raven, vnos],
    queryFn: () => razlagaRatingaApi.prviDan(Number(izhodisce), raven, vnos),
    enabled: vseVeljavne,
    placeholderData: (prej) => prej,
  })

  const spremeni = (i: number, sprememba: Partial<TekmaDne>) =>
    nastaviTekme((prej) => prej.map((t, j) => (j === i ? { ...t, ...sprememba } : t)))

  return (
    <div className="razlaga__preizkus">
      <h3 className="razlaga__podnaslov">Preizkusi svoj prvi dan</h3>
      <div className="razlaga__polja">
        <label className="obrazec__polje">
          <span>Izhodišče</span>
          <StevilskoPolje vrednost={izhodisce} naSpremembo={nastaviIzhodisce} najvec={NAJVEC} />
        </label>
      </div>
      <p className="namig">
        Rekreativec {p.rekreativniZacetek}, sicer sidro iz tabele zgoraj.
      </p>

      <span className="razlaga__oznaka">Tekmovanje</span>
      <div className="izbirnik razlaga__izbirnik">
        {RAVNI.map((v) => (
          <button
            key={v}
            type="button"
            className={'izbirnik__gumb' + (raven === v ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={raven === v}
            onClick={() => nastaviRaven(v)}
          >
            {KRATKO_RAVEN[v]}
          </button>
        ))}
      </div>

      <ol className="razlaga__tekme">
        {tekme.map((t, i) => {
          const korak = dan.data?.koraki[i]
          return (
            <li key={i} className="razlaga__tekma">
              <span className="razlaga__tekma-st">{i + 1}.</span>
              <label className="razlaga__tekma-polje">
                <span className="samo-za-bralnik">Rating nasprotnika v {i + 1}. tekmi</span>
                <StevilskoPolje
                  vrednost={t.nasprotnik}
                  naSpremembo={(v) => spremeni(i, { nasprotnik: v })}
                  najvec={NAJVEC}
                />
              </label>
              <div className="izbirnik razlaga__izid-tekme">
                <button
                  type="button"
                  className={'izbirnik__gumb' + (t.zmaga ? ' izbirnik__gumb--aktiven' : '')}
                  aria-pressed={t.zmaga}
                  onClick={() => spremeni(i, { zmaga: true })}
                >
                  Zmaga
                </button>
                <button
                  type="button"
                  className={'izbirnik__gumb' + (!t.zmaga ? ' izbirnik__gumb--aktiven' : '')}
                  aria-pressed={!t.zmaga}
                  onClick={() => spremeni(i, { zmaga: false })}
                >
                  Poraz
                </button>
              </div>
              <span className="razlaga__tekma-izid">
                {korak && vseVeljavne ? (
                  <>
                    <span className={korak.sprememba >= 0 ? 'profil__zmaga' : 'profil__poraz'}>
                      {sPredznakom(korak.sprememba)}
                    </span>{' '}
                    → <strong>{korak.rating}</strong>
                  </>
                ) : (
                  '—'
                )}
              </span>
              {tekme.length > 1 && (
                <button
                  type="button"
                  className="razlaga__odstrani"
                  aria-label={`Odstrani ${i + 1}. tekmo`}
                  onClick={() => nastaviTekme((prej) => prej.filter((_, j) => j !== i))}
                >
                  ×
                </button>
              )}
            </li>
          )
        })}
      </ol>
      {tekme.length < NAJVEC_TEKEM_DNE && (
        <button
          type="button"
          className="gumb"
          onClick={() => nastaviTekme((prej) => [...prej, { nasprotnik: '900', zmaga: true }])}
        >
          + Dodaj tekmo
        </button>
      )}
      <NapakaPoizvedbe poizvedba={dan} kaj="preizkusa" />
      <p className="namig">
        Prva tekma je navaden korak od izhodišča (K {p.kOsnovni + p.pribitekNeustaljen + p.pribitekNovinec}
        {' '}× teža tekmovanja). Od druge naprej je rating izračunan znova iz vseh izidov dneva —
        zato poraz lahko vzame veliko, zmaga pa vrne. Število ob tekmi je točno tisto, ki ga
        prikaže zapisnik.
      </p>
    </div>
  )
}

/* ---------- Oblikovanje ---------- */

function odstotek(teza: number): string {
  return `${Math.round(teza * 100)} %`
}

function decimalno(v: number): string {
  return v.toFixed(2).replace('.', ',')
}

/* Pravi minus (−) namesto vezaja; ±0 pri ničli, ker »+0« obljublja pridobitev. */
function sPredznakom(v: number): string {
  if (v === 0) return '±0'
  return v > 0 ? `+${v}` : `−${Math.abs(v)}`
}
