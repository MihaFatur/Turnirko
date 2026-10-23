/* Pogoji uporabe in varstvo osebnih podatkov (/pogoji, zasebnost je
   /pogoji#zasebnost).

   Registracija zahteva kljukico »Strinjam se s pogoji uporabe in obdelavo
   podatkov« - brez te strani se je strinjala z nečim, česar ni bilo (glej
   docs/VARNOSTNI-PREGLED.md, V18). Povezave nanjo stojijo tam, kjer se človek
   za kaj odloči: ob registraciji, ob prijavi in ob nakupu naročnine.

   Besedilo opisuje, kar aplikacija res počne (kateri podatki, kje se
   prikažejo, kdo jih obdeluje, kdaj se brišejo). Podatki o ponudniku so v
   pomozno/pravno.ts. Preden se na besedilo zanesemo, naj ga pregleda nekdo,
   ki pozna ZVOP-2 in ZVPot-1 - Turnirko ni pravnik.

   Postavitev (oktober 2026, design_handoff_pogoji_in_zasebnost): na vrhu
   povzetek »Na kratko« in kolofon ponudnika, točke so oštevilčene (naslov
   levo, besedilo desno), ob strani lepljivo kazalo, pod 1100 px pa vrstica
   zavihkov (na telefonu v lepljivi glavi). Povzetki (»Na kratko«, okvirja pri
   1.3 in 2.7) so bralcu v pomoč; velja besedilo točk, ki je ostalo dobesedno
   enako. */
import { Fragment, useEffect, useState, type MouseEvent, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { GlavaZavihki } from '../komponente/GlavaTelefona'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { PONUDNIK, POGOJI_VELJAJO_OD } from '../pomozno/pravno'
import { useTelefon } from '../pomozno/telefon'

interface Tocka {
  id: string
  st: string
  naslov: string
  /* Povzetek nad besedilom (1.3 naročnina, 2.7 roki hranjenja). */
  okvir?: ReactNode
  vsebina: ReactNode
}

const V_KRATKEM: [string, string][] = [
  [
    'Cena',
    PONUDNIK.zavezanecZaDdv
      ? 'Izpisana pred nakupom in končna (vključuje DDV).'
      : 'Izpisana pred nakupom in končna. Prodajalec ni zavezanec za DDV, zato DDV ni obračunan.',
  ],
  ['Plačilo', 'Stripe. Račun za vsako plačilo (tudi za obnovo) prejmeš po e-pošti.'],
  ['Preizkus', 'Prvi mesec Premium brez plačila in brez kartice; ne obnovi se sam.'],
  ['Obnova', 'Samodejna ob koncu obdobja (mesec ali leto), dokler je ne prekličeš.'],
  ['Preklic', 'Kadar koli na strani Naročnina; velja do konca že plačanega obdobja.'],
  [
    'Odstop',
    '14 dni od nakupa, brez navedbe razloga. Piši nam; vrnemo plačilo, zmanjšano za že porabljeni del obdobja.',
  ],
]

const V_STEVILKAH: [string, string][] = [
  ['48 ur', 'Račun, katerega e-pošta ni potrjena.'],
  ['30 dni', 'Račun mlajšega od 15 let brez soglasja skrbnika.'],
  ['4 dni', 'Podatki registracije s plačljivim paketom, ki ni bila plačana.'],
  ['Na zahtevo', 'Račun izbrišemo na tvojo zahtevo.'],
  [
    'Po zakonu',
    'Izdani računi za plačila: toliko časa, kot zahtevajo davčni predpisi, tudi po izbrisu računa.',
  ],
  ['Kratek čas', 'Dnevnik zahtev z naslovi IP, nato se prepiše.'],
  ['Ostane', 'Rezultati tekmovanj kot zapis zgodovine tekmovanja.'],
]

const POGOJI: Tocka[] = [
  {
    id: 'pogoji-javno',
    st: '1.1',
    naslov: 'Kaj je javno',
    vsebina: (
      <>
        Rezultati tekmovanj, lige, koledar, lestvica in profili igralcev so dostopni vsem, brez
        prijave. Na profilu in v rezultatih se prikažejo ime, priimek, klub, starostna
        kategorija, odigrane tekme in Turnirko rating. Rezultati uradnih tekmovanj so
        prevzeti iz javnih objav Namiznoteniške zveze Slovenije (NTZS); uradni rezultati in
        jakostne lestvice so pri NTZS. Turnirko rating je lastna ocena Turnirka in ni uradna
        lestvica NTZS (<Link to="/o-ratingu">kako deluje</Link>).
      </>
    ),
  },
  {
    id: 'pogoji-racun',
    st: '1.2',
    naslov: 'Račun',
    vsebina: (
      <>
        Za račun potrebuješ e-poštni naslov, ki ga potrdiš s kodo. Vpiši resnične podatke in
        gesla ne deli z drugimi. Mlajši od 15 let potrebujejo soglasje starša ali skrbnika, ki
        ga ta da s kodo, poslano na svoj naslov. Po vsakem odigranem kolu lig, v katerih igraš
        ali jih spremljaš, ti pošljemo e-pošto z izidi in lestvico; izklopiš jo v meniju računa
        (»Obvestila«) ali s povezavo v vsakem sporočilu. Račun, ki se uporablja v nasprotju s
        temi pogoji, lahko onemogočimo.
      </>
    ),
  },
  {
    id: 'pogoji-narocnina',
    st: '1.3',
    naslov: 'Naročnina Premium in paketi organizatorjev',
    okvir: <Okvir naslov="V kratkem" vrstice={V_KRATKEM} />,
    vsebina: (
      <>
        Naročnino prodaja {zPiko(PONUDNIK.ime)} Cena je izpisana pred nakupom in je končna
        {PONUDNIK.zavezanecZaDdv
          ? ' (vključuje DDV).'
          : ': prodajalec ni zavezanec za DDV, zato DDV ni obračunan (1. odstavek 94. člena ZDDV-1).'}{' '}
        Plačilo obdela Stripe, račun za vsako plačilo (tudi za vsako obnovo) prejmeš po e-pošti na
        naslov, s katerim plačaš. Naročnina se ob koncu obdobja
        (mesec ali leto) samodejno obnovi, dokler je ne prekličeš. Prekličeš jo kadar koli na
        strani <Link to="/narocnina">Naročnina</Link>; velja do konca že plačanega obdobja.
        Preklop med mesečnim in letnim plačilom velja ob naslednji obnovi. Račun igralca dobi
        enkrat prvi mesec Premium brez plačila (preizkus), ko je povezan s svojim zapisom med
        igralci; preizkus ne zahteva kartice in se ne obnovi sam, po njem račun ostane
        brezplačen. Kot potrošnik lahko
        od pogodbe odstopiš v 14 dneh od nakupa brez navedbe razloga — piši nam; vrnemo
        plačilo, zmanjšano za že porabljeni del obdobja.
      </>
    ),
  },
  {
    id: 'pogoji-prepovedi',
    st: '1.4',
    naslov: 'Česa ne smeš',
    vsebina: (
      <>
        Samodejno zajemati podatkov s strani (programi, ki strani prenašajo v velikem
        obsegu), obremenjevati ali ovirati delovanja strani, se predstavljati kot kdo drug ali
        vpisovati rezultatov, ki se niso zgodili.
      </>
    ),
  },
  {
    id: 'pogoji-tocnost',
    st: '1.5',
    naslov: 'Točnost in odgovornost',
    vsebina: (
      <>
        Rezultate vnašajo organizatorji tekmovanj in jih prepisujejo s papirja, zato se lahko
        zgodi napaka. Če jo opaziš, jo sporoči organizatorju tekmovanja ali nam — popravek se
        upošteva tudi v ratingu. Stran se trudimo imeti vedno dosegljivo, a za izpade in
        posledice napak v podatkih ne odgovarjamo.
      </>
    ),
  },
  {
    id: 'pogoji-spremembe',
    st: '1.6',
    naslov: 'Spremembe pogojev',
    vsebina: (
      <>
        Spremembe objavimo na tej strani. O bistvenih spremembah imetnike računov obvestimo po
        e-pošti.
      </>
    ),
  },
]

const ZASEBNOST: Tocka[] = [
  {
    id: 'zasebnost-upravljavec',
    st: '2.1',
    naslov: 'Kdo je upravljavec',
    vsebina: (
      <>
        Upravljavec osebnih podatkov je {PONUDNIK.ime}
        {PONUDNIK.naslov && `, ${PONUDNIK.naslov}`}.
        {PONUDNIK.eposta && (
          <>
            {' '}Kontakt: <a href={`mailto:${PONUDNIK.eposta}`}>{PONUDNIK.eposta}</a>.
          </>
        )}
      </>
    ),
  },
  {
    id: 'zasebnost-igralci',
    st: '2.2',
    naslov: 'Igralci na tekmovanjih',
    vsebina: (
      <>
        Hranimo ime, priimek, spol, klub, datum rojstva in rezultate tekem. Podatke vpišejo
        organizatorji tekmovanj oziroma so prevzeti iz javnih rezultatov NTZS. Javno se
        prikažejo samo ime, priimek, klub, starostna kategorija, rezultati in rating.{' '}
        <strong>Datum rojstva ni javen</strong>: uporablja se le za starostno kategorijo in
        za ločevanje igralcev z enakim imenom. Podlaga je zakoniti interes vodenja javne
        zgodovine tekmovanj.
      </>
    ),
  },
  {
    id: 'zasebnost-racuni',
    st: '2.3',
    naslov: 'Imetniki računov',
    vsebina: (
      <>
        Ob registraciji hranimo e-poštni naslov, geslo (samo kot zgostitev — prebrati ga ne
        more nihče), ime, priimek, klub in datum rojstva, ki služi povezavi računa z zapisom
        igralca. Pri mlajših od 15 let hranimo še e-poštni naslov starša ali skrbnika. Na
        e-poštni naslov pošiljamo kode, obvestila o računu in — dokler jih ne izklopiš —
        obvestila po kolu lig, ki jih spremljaš ali v katerih igraš. Podlaga je pogodba
        (uporaba računa).
      </>
    ),
  },
  {
    id: 'zasebnost-placila',
    st: '2.4',
    naslov: 'Plačila',
    vsebina: (
      <>
        Plačilo s kartico obdela Stripe; številke kartice Turnirko ne vidi in ne hrani. Hranimo
        stanje naročnine (paket, obdobje, cena). Račun izda in pri finančni upravi davčno
        potrdi program Moj Račun, ki od Stripa dobi znesek, izdelek in e-poštni naslov kupca,
        pri paketih organizatorjev še naslov in davčno številko, če ju vpišeš. Podlaga je
        zakonska obveznost izdaje računa.
      </>
    ),
  },
  {
    id: 'zasebnost-obisk',
    st: '2.5',
    naslov: 'Obisk strani',
    vsebina: (
      <>
        Piškotkov ne uporabljamo. Statistika obiska teče na našem strežniku, brez piškotkov in
        brez osebnih podatkov; izklopiš jo z obiskom naslova{' '}
        <a href="/?statistika=izklop">turnirko-nt.si/?statistika=izklop</a>. Strežnik zaradi
        varnosti kratek čas hrani dnevnik zahtev z naslovi IP, nato se prepiše. V brskalniku
        ostanejo le nastavitve (npr. nazadnje ogledane lige) in prijava za trenutno sejo.
      </>
    ),
  },
  {
    id: 'zasebnost-obdelovalci',
    st: '2.6',
    naslov: 'Kdo še obdeluje podatke',
    vsebina: (
      <>
        Stripe (plačila), Moj Račun (izdaja in hramba računov), Brevo (pošiljanje e-pošte s
        potrditvenimi kodami)
        {PONUDNIK.gostovanje ? ` in ${PONUDNIK.gostovanje} (strežnik)` : ' in ponudnik strežnika'}.
        Podatkov ne prodajamo in jih ne posredujemo za oglaševanje.
      </>
    ),
  },
  {
    id: 'zasebnost-cas',
    st: '2.7',
    naslov: 'Koliko časa',
    okvir: <Okvir naslov="V številkah" vrstice={V_STEVILKAH} stevilke />,
    vsebina: (
      <>
        Račun, katerega e-pošta ni potrjena, se izbriše po 48 urah; račun mlajšega od 15 let
        brez soglasja skrbnika po 30 dneh. Podatki registracije s plačljivim paketom, ki ni
        bila plačana, se izbrišejo po 4 dneh. Račun izbrišemo na tvojo zahtevo. Izdane račune za
        plačila hranimo toliko časa, kot zahtevajo davčni predpisi, tudi po izbrisu računa.
        Rezultati tekmovanj ostanejo kot zapis zgodovine tekmovanja.
      </>
    ),
  },
  {
    id: 'zasebnost-pravice',
    st: '2.8',
    naslov: 'Tvoje pravice',
    vsebina: (
      <>
        Imaš pravico do vpogleda v svoje podatke, popravka, izbrisa, omejitve obdelave in
        ugovora. Zahtevo pošlji na kontakt spodaj; pri rezultatih tekmovanj jo presodimo
        posebej, ker so del javne zgodovine tekmovanja. Pritožbo lahko vložiš pri
        Informacijskem pooblaščencu (<a href="https://www.ip-rs.si">www.ip-rs.si</a>).
      </>
    ),
  },
]

/* Prva raven kazala in zavihkov. Id-ji #pogoji, #zasebnost in #kontakt
   morajo ostati: nanje vodijo registracija in strani naročnine. */
const RAZDELKI: { id: string; st: string; naslov: string; zavihek: string; tocke?: Tocka[] }[] = [
  { id: 'na-kratko', st: '', naslov: 'Na kratko', zavihek: 'Na kratko' },
  { id: 'pogoji', st: '1', naslov: 'Pogoji uporabe', zavihek: 'Pogoji', tocke: POGOJI },
  { id: 'zasebnost', st: '2', naslov: 'Varstvo osebnih podatkov', zavihek: 'Zasebnost', tocke: ZASEBNOST },
  { id: 'kontakt', st: '3', naslov: 'Kontakt', zavihek: 'Kontakt' },
]

/* Vsi cilji v vrstnem redu na strani - po njem kazalo ve, kaj se bere. */
const CILJI = RAZDELKI.flatMap((r) => [r.id, ...(r.tocke ?? []).map((t) => t.id)])

const NA_KRATKO = [
  {
    oznaka: 'Javno',
    vrednost: 'Rezultati in rating',
    pojasnilo: 'Ime, priimek, klub, starostna kategorija, rezultati in rating. Brez prijave.',
  },
  {
    oznaka: 'Ni javno',
    vrednost: 'Datum rojstva',
    pojasnilo: 'Uporablja se le za starostno kategorijo in za ločevanje igralcev z enakim imenom.',
  },
  {
    oznaka: 'Piškotki',
    vrednost: 'Ne uporabljamo',
    pojasnilo:
      'Statistika obiska teče na našem strežniku, brez piškotkov in brez osebnih podatkov.',
  },
  { oznaka: 'Podatki', vrednost: 'Ne prodajamo', pojasnilo: 'Ne posredujemo jih za oglaševanje.' },
  {
    oznaka: 'Naročnina',
    vrednost: 'Odstop v 14 dneh',
    pojasnilo: 'Kot potrošnik brez navedbe razloga. Preklic kadar koli.',
  },
]

/* Vrstica, katere vrednost še ni vpisana (null v pravno.ts), odpade. */
const KOLOFON = (
  [
    ['Upravljavec', PONUDNIK.ime],
    ['Sedež', kraj(PONUDNIK.naslov)],
    ['Veljajo od', POGOJI_VELJAJO_OD && oblikujDatum(POGOJI_VELJAJO_OD)],
    ['Kontakt', PONUDNIK.eposta],
  ] as const
).filter(([, vrednost]) => vrednost)

const KONTAKT = (
  [
    ['Ponudnik', PONUDNIK.ime],
    ['Naslov', PONUDNIK.naslov],
    ['Matična številka', PONUDNIK.maticna],
    ['Davčna številka', PONUDNIK.davcna],
    [
      'E-pošta',
      PONUDNIK.eposta && <a href={`mailto:${PONUDNIK.eposta}`}>{PONUDNIK.eposta}</a>,
    ],
  ] as const
).filter(([, vrednost]) => vrednost)

/* Kazalo ob strani je od 1100 px naprej (ista meja kot pri razlagi ratinga). */
const POIZVEDBA_KAZALO = '(min-width: 1100px)'

export function PogojiStran() {
  useNaslovStrani('Pogoji uporabe')
  const lokacija = useLocation()
  const jeTelefon = useTelefon()
  const aktivni = useBranoMesto()

  /* Povezava z registracije pride s sidrom (#zasebnost). Odmik pod lepljivo
     glavo nosi CSS (scroll-margin-top na ciljih). */
  useEffect(() => {
    if (lokacija.hash) document.getElementById(lokacija.hash.slice(1))?.scrollIntoView()
  }, [lokacija.hash])

  const zavihki = <Zavihki aktivni={aktivni} />

  return (
    <section className="pogoji">
      {jeTelefon ? <GlavaZavihki>{zavihki}</GlavaZavihki> : zavihki}

      <div id="na-kratko" className="pogoji__vrh">
        <div className="stran-glava pogoji__glava">
          <div>
            <h1 className="naslov-strani">
              <span className="naslov-strani__nad">Turnirko</span>
              <span className="naslov-strani__glavni">Pogoji in zasebnost</span>
            </h1>
            <p className="uvod">
              Turnirko je spletna stran za namizni tenis: rezultati turnirjev in lig, koledar,
              lestvica in profili igralcev. Upravlja jo {zPiko(PONUDNIK.ime)}
              {POGOJI_VELJAJO_OD && ` Pogoji veljajo od ${oblikujDatum(POGOJI_VELJAJO_OD)}.`}
            </p>
          </div>

          <div className="kolofon pogoji__kolofon">
            {KOLOFON.map(([oznaka, vrednost]) => (
              <div key={oznaka} className="kolofon__vrstica">
                <span className="kolofon__oznaka">{oznaka}</span>
                <span className="kolofon__vrednost">{vrednost}</span>
              </div>
            ))}
          </div>
        </div>

        <section className="pogoji__kratko" aria-labelledby="na-kratko-naslov">
          <div className="pogoji__glava-razdelka">
            <h2 id="na-kratko-naslov">Na kratko</h2>
            {!jeTelefon && <span className="pogoji__oznaka">Povzetek · velja besedilo spodaj</span>}
          </div>
          <div className="pogoji__kratko-mreza">
            {NA_KRATKO.map((k) => (
              <div key={k.oznaka} className="pogoji__kratko-celica">
                <span className="pogoji__oznaka">{k.oznaka}</span>
                <span className="pogoji__kratko-vrednost">{k.vrednost}</span>
                <span className="pogoji__kratko-pojasnilo">{k.pojasnilo}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="pogoji__telo">
        <Kazalo aktivni={aktivni} />

        <div className="pogoji__razdelki">
          <Razdelek id="pogoji" st="1" naslov="Pogoji uporabe" tocke={POGOJI} />
          <Razdelek id="zasebnost" st="2" naslov="Varstvo osebnih podatkov" tocke={ZASEBNOST} />

          <section
            id="kontakt"
            className="pogoji__razdelek pogoji__razdelek--kontakt"
            aria-labelledby="kontakt-naslov"
          >
            <div className="pogoji__glava-razdelka">
              <h2 id="kontakt-naslov">Kontakt</h2>
              {!jeTelefon && <span className="pogoji__oznaka">Razdelek 3</span>}
            </div>
            {KONTAKT.map(([oznaka, vrednost]) => (
              <div key={oznaka} className="pogoji__kontakt">
                <span className="pogoji__kontakt-oznaka">{oznaka}</span>
                <span>{vrednost}</span>
              </div>
            ))}
          </section>
        </div>
      </div>
    </section>
  )
}

/* Kaj gledalec bere: zadnji cilj (razdelek ali točka), katerega vrh je prešel
   črto malo pod mestom, kamor ga postavi skok iz kazala. Na vrhu strani je to
   »Na kratko«, na dnu Kontakt, tudi če njegov naslov črte ne doseže. */
function useBranoMesto(): string {
  const [aktivni, nastaviAktivni] = useState(CILJI[0])

  useEffect(() => {
    let okvir = 0
    const preberi = () => {
      okvir = 0
      const koren = document.documentElement
      if (window.scrollY + window.innerHeight >= koren.scrollHeight - 4) {
        nastaviAktivni(CILJI[CILJI.length - 1])
        return
      }
      let prag: number | undefined
      let zadnji = CILJI[0]
      for (const id of CILJI) {
        const cilj = document.getElementById(id)
        if (!cilj) continue
        prag ??= pragBranja(cilj)
        if (cilj.getBoundingClientRect().top <= prag) zadnji = id
      }
      nastaviAktivni(zadnji)
    }
    const obDrsenju = () => {
      if (!okvir) okvir = requestAnimationFrame(preberi)
    }
    preberi()
    window.addEventListener('scroll', obDrsenju, { passive: true })
    window.addEventListener('resize', obDrsenju)
    return () => {
      window.removeEventListener('scroll', obDrsenju)
      window.removeEventListener('resize', obDrsenju)
      cancelAnimationFrame(okvir)
    }
  }, [])

  return aktivni
}

/* Mesto skoka je lepljiva glava (scroll-padding-top na <html>) in odmik
   cilja (scroll-margin-top) - oboje iz CSS, da ga ni treba pisati dvakrat.
   Črta branja je 50 px nižje ob kazalu in 8 px pod zavihki, kjer bi na
   nizkem zaslonu sicer zajela že naslednjo točko. */
function pragBranja(cilj: HTMLElement): number {
  const odmik =
    (parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0)
    + (parseFloat(getComputedStyle(cilj).scrollMarginTop) || 0)
  return odmik + (window.matchMedia(POIZVEDBA_KAZALO).matches ? 50 : 8)
}

/* Kazalo in zavihki so prave povezave (delujejo v novem zavihku in brez
   skripte); navaden klik namesto skoka gladko podrsa do istega mesta. */
function skoci(dogodek: MouseEvent<HTMLAnchorElement>, id: string) {
  if (dogodek.button !== 0 || dogodek.metaKey || dogodek.ctrlKey || dogodek.shiftKey) return
  const cilj = document.getElementById(id)
  if (!cilj) return
  dogodek.preventDefault()
  const brezGibanja = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  cilj.scrollIntoView({ behavior: brezGibanja ? 'auto' : 'smooth' })
  window.history.replaceState(window.history.state, '', `#${id}`)
}

/* Razdelek, ki mu pripada id (točka 1.3 → »pogoji«). */
function skupina(id: string): string {
  return RAZDELKI.find((r) => id === r.id || id.startsWith(`${r.id}-`))?.id ?? id
}

/* Kazalo ob strani: podtočke so razprte samo pri razdelku, ki se bere. */
function Kazalo({ aktivni }: { aktivni: string }) {
  const odprta = skupina(aktivni)
  return (
    <aside className="pogoji__kazalo">
      <span className="pogoji__oznaka pogoji__kazalo-glava">Vsebina</span>
      <nav aria-label="Vsebina strani">
        {RAZDELKI.map((r) => (
          <Fragment key={r.id}>
            <PostavkaKazala
              id={r.id}
              st={r.st}
              naslov={r.naslov}
              aktivna={aktivni === r.id}
              razred={odprta === r.id ? 'pogoji__postavka--odprta' : ''}
            />
            {odprta === r.id
              && r.tocke?.map((t) => (
                <PostavkaKazala
                  key={t.id}
                  id={t.id}
                  st={t.st}
                  naslov={t.naslov}
                  aktivna={aktivni === t.id}
                  razred="pogoji__postavka--tocka"
                />
              ))}
          </Fragment>
        ))}
      </nav>
    </aside>
  )
}

function PostavkaKazala({
  id,
  st,
  naslov,
  aktivna,
  razred,
}: {
  id: string
  st: string
  naslov: string
  aktivna: boolean
  razred: string
}) {
  return (
    <a
      href={`#${id}`}
      onClick={(e) => skoci(e, id)}
      className={`pogoji__postavka ${razred}${aktivna ? ' pogoji__postavka--aktivna' : ''}`}
      aria-current={aktivna ? 'location' : undefined}
    >
      <span className="pogoji__postavka-st">{st}</span>
      <span>{naslov}</span>
    </a>
  )
}

/* Zavihki pod 1100 px: aktiven je razdelek, v katerem je bralec. */
function Zavihki({ aktivni }: { aktivni: string }) {
  const odprta = skupina(aktivni)
  return (
    <nav className="pogoji__zavihki" aria-label="Vsebina strani">
      {RAZDELKI.map((r) => (
        <a
          key={r.id}
          href={`#${r.id}`}
          onClick={(e) => skoci(e, r.id)}
          className={'pogoji__zavihek' + (odprta === r.id ? ' pogoji__zavihek--aktiven' : '')}
          aria-current={odprta === r.id ? 'location' : undefined}
        >
          {r.zavihek}
        </a>
      ))}
    </nav>
  )
}

function Razdelek({
  id,
  st,
  naslov,
  tocke,
}: {
  id: string
  st: string
  naslov: string
  tocke: Tocka[]
}) {
  const jeTelefon = useTelefon()
  return (
    <section id={id} className="pogoji__razdelek" aria-labelledby={`${id}-naslov`}>
      <div className="pogoji__glava-razdelka">
        <h2 id={`${id}-naslov`}>{naslov}</h2>
        <span className="pogoji__oznaka">
          {jeTelefon ? st : `Razdelek ${st} · ${tocke.length} točk`}
        </span>
      </div>
      {tocke.map((t) => (
        <div key={t.id} id={t.id} className="pogoji__tocka">
          <div className="pogoji__tocka-glava">
            <span className="pogoji__tocka-st">{t.st}</span>
            <h3>{t.naslov}</h3>
          </div>
          <div className="pogoji__tocka-besedilo">
            {t.okvir}
            <p>{t.vsebina}</p>
          </div>
        </div>
      ))}
    </section>
  )
}

/* Povzetek točke v obliki tabele: ključ (ali številka) levo, pojasnilo desno. */
function Okvir({
  naslov,
  vrstice,
  stevilke = false,
}: {
  naslov: string
  vrstice: [string, string][]
  stevilke?: boolean
}) {
  return (
    <div className={'pogoji__okvir' + (stevilke ? ' pogoji__okvir--stevilke' : '')}>
      <div className="pogoji__oznaka pogoji__okvir-naslov">{naslov}</div>
      {vrstice.map(([kljuc, vrednost]) => (
        <div key={kljuc} className="pogoji__okvir-vrstica">
          <span className="pogoji__okvir-kljuc">{kljuc}</span>
          <span className="pogoji__okvir-vrednost">{vrednost}</span>
        </div>
      ))}
    </div>
  )
}

/* Ime, ki se konča z okrajšavo (»s.p.«), ne dobi še ene pike na koncu stavka. */
function zPiko(ime: string) {
  return ime.endsWith('.') ? ime : `${ime}.`
}

/* Kraj iz naslova (»…, 3312 Prebold« → »Prebold«). */
function kraj(naslov: string | null) {
  return naslov?.split(',').pop()?.trim().replace(/^\d{4}\s+/, '') || null
}
