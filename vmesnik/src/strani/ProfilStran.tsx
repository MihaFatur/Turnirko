/* Profil igralca s statistiko.

   Stran je ena sama kronoloska pripoved:
   glava -> Napredek ELO -> Forma -> Nasprotniki -> Nizi in tocke -> Razrezi
   -> Odigrane tekme.

   Javni del (uvrstitev, ELO blok, kolofon, graf, seznam tekem) vidi vsak.
   Zasebne analize (forma, nasprotniki, nizi in tocke, razrezi) se nalozijo
   posebej in samo takrat, ko je profil last prijavljenega igralca ali ko gleda
   administrator - streznik na ta klic sicer odgovori s 403.

   Vsi izpeljani prikazi (kolobar, toplotna karta, sparkline, razsevni graf,
   osi razrezov) se izracunajo iz ProfilDto in ProfilZasebnoDto med izrisom -
   brez novih poizvedb. Okolico na lestvici izracunamo iz globalne lestvice,
   ki jo vmesnik ima ze predpomnjeno (isti kljuc kot LestvicaStran). */
import type { CSSProperties } from 'react'
import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { profiliApi, statistikaApi } from '../api/zahteve'
import type {
  Delez,
  LestvicaIgralcaDto,
  ProfilDto,
  ProfilMesec,
  ProfilNasprotnik,
  ProfilZasebnoDto,
  RazsevnaTocka,
  Razmerje,
  TekmaProfila,
} from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GrafElo } from '../komponente/GrafElo'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import {
  oznakaMeseca,
  sklonMesecih,
  sklonTekem,
  sklonTock,
  sklonZmag,
} from '../pomozno/oblikovanje'

export function ProfilStran() {
  const { id } = useParams()
  const idIgralec = Number(id)
  const { jeAdmin, mojIdIgralec } = useAvtentikacija()

  const profil = useQuery({
    queryKey: ['profil', idIgralec],
    queryFn: () => profiliApi.profil(idIgralec),
  })

  /* Zasebni del zahtevamo samo, kadar imamo pravico — da uporabnik ne dobi
     nepotrebne napake 403 v konzoli. */
  const smemZasebno = jeAdmin || mojIdIgralec === idIgralec
  const zasebno = useQuery({
    queryKey: ['profil-zasebno', idIgralec],
    queryFn: () => profiliApi.zasebno(idIgralec),
    enabled: smemZasebno && !Number.isNaN(idIgralec),
  })

  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Skok s točke grafa ELO na vrstico iste tekme v seznamu spodaj. Seznam je
     izrisan v celoti (brez straničenja), zato zadošča iskanje po id-ju vrstice.
     Vrstica dobi fokus — brez tega bralnik zaslona po skoku ne pove, kam smo
     prišli — in ostane označena, dokler gledalec ne izbere druge tekme. */
  const [poudarjenaTekma, nastaviPoudarjeno] = useState<string | null>(null)
  const skociNaTekmo = useCallback((idTekme: number, ligaska: boolean) => {
    const kljuc = kljucTekme(idTekme, ligaska)
    nastaviPoudarjeno(kljuc)
    const vrstica = document.getElementById('tekma-' + kljuc)
    if (!vrstica) return
    vrstica.focus({ preventScroll: true })
    /* Pri uvoženi zgodovini je seznam dolg tudi 80 000 px. Mehko drsenje čez
       tako razdaljo ni pot, ampak zabrisan blisk, zato je mehko samo, kadar je
       cilj v dosegu nekaj zaslonov — sicer skočimo. */
    const razdalja = Math.abs(vrstica.getBoundingClientRect().top - window.innerHeight / 2)
    const mirno = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    vrstica.scrollIntoView({
      behavior: mirno || razdalja > window.innerHeight * 4 ? 'auto' : 'smooth',
      block: 'center',
    })
  }, [])

  /* Merilo je isLoading (= brez podatkov IN zahteva teče), ne isPending:
     poizvedba brez podatkov, ki ne teče, je ustavljena (npr. brez povezave)
     ali končana z napako - takrat mora stran to povedati, ne pa do konca sveta
     kazati "Nalaganje …". */
  if (profil.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (profil.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (profil.error) return <NapakaPoizvedbe poizvedba={profil} kaj="profila" />
  if (!profil.data) return <p className="obvestilo">Tega igralca ni (več).</p>

  const p = profil.data
  const z = zasebno.data
  const jeMoj = mojIdIgralec === idIgralec
  const { priimek, ime } = razbijIme(p.glava.polnoIme)
  const okolica = izracunajOkolico(lestvica.data, idIgralec, p.uvrstitev.mesto)

  return (
    <section className="profil">
      <div>
        <Link to="/lestvica" className="povezava-nazaj">← Lestvica</Link>

        <div className="stran-glava">
          <div>
            <div className="profil__uvrstitev">
              {p.uvrstitev.mesto !== null && (
                <span className="profil__mesto-znacka">
                  {p.uvrstitev.mesto}. / {p.uvrstitev.skupajIgralcev}
                </span>
              )}
              {p.uvrstitev.percentil !== null && (
                <span className="profil__percentil">
                  Boljši od {p.uvrstitev.percentil} % igralcev z ratingom
                </span>
              )}
            </div>

            <h1 className="naslov-strani">
              <span className="naslov-strani__nad">{ime}</span>
              <span className="naslov-strani__glavni">{priimek}</span>
            </h1>

            <div className="profil__meta">
              <span>{p.glava.klub ?? 'brez kluba'}</span>
              {p.glava.igralnaRoka && (
                <span>{p.glava.igralnaRoka === 'LEVA' ? 'Levičar' : 'Desničar'}</span>
              )}
              {jeMoj && <span className="profil__moj">Tvoj profil</span>}
            </div>

            {/* Tri velike številke nadomeščajo štiri vrstice kolofona: mesto,
                odigrane, izkupiček in uspešnost so podatki, ki jih gledalec
                išče prvi, zato so tu in ne v drobnem tisku desno. */}
            <div className="profil__povzetek">
              <Kazalnik oznaka="Odigrane tekme" vrednost={p.pregled.odigrane} prvi />
              <Kazalnik
                oznaka="Zmage – porazi"
                vrednost={`${p.pregled.zmage}–${p.pregled.porazi}`}
              />
              <Kazalnik oznaka="Uspešnost" vrednost={`${p.pregled.odstotekZmag} %`} poudarjen />
            </div>
          </div>

          <div>
            <div className="elo-blok">
              <span className="elo-blok__oznaka">Klubski ELO</span>
              <span className="elo-blok__vrednost">{p.glava.rating ?? '—'}</span>
              {z?.forma && (
                <div className="elo-blok__noga">
                  <span>
                    {z.forma.spremembaElo30dni === null
                      ? 'brez tekem v 30 dneh'
                      : `${z.forma.spremembaElo30dni >= 0 ? '+' : '−'}${Math.abs(z.forma.spremembaElo30dni)} / 30 dni`}
                  </span>
                  {z.forma.najvisjiElo !== null && (
                    <span className="elo-blok__vrh">vrh {z.forma.najvisjiElo}</span>
                  )}
                </div>
              )}
            </div>

            <div className="kolofon">
              {p.uvrstitev.klubskoPovprecje !== null && (
                <div className="kolofon__vrstica">
                  <span className="kolofon__oznaka">Klubsko povprečje</span>
                  <span className="kolofon__vrednost">
                    {p.uvrstitev.klubskoPovprecje}
                    {p.glava.rating !== null && (
                      <>
                        {' '}
                        <span
                          className={
                            p.glava.rating >= p.uvrstitev.klubskoPovprecje
                              ? 'kolofon__vrednost--poz'
                              : 'kolofon__vrednost--neg'
                          }
                        >
                          {p.glava.rating >= p.uvrstitev.klubskoPovprecje ? '+' : '−'}
                          {Math.abs(p.glava.rating - p.uvrstitev.klubskoPovprecje)}
                        </span>
                      </>
                    )}
                  </span>
                </div>
              )}
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Turnirji / lige</span>
                <span className="kolofon__vrednost">
                  {p.pregled.turnirskih} / {p.pregled.ligaskih}
                </span>
              </div>
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Zadnja tekma</span>
                <span className="kolofon__vrednost">
                  {p.tekme.length > 0 && p.tekme[0].datum ? datum(p.tekme[0].datum) : '—'}
                </span>
              </div>
              {okolica?.razlikaNad != null && (
                <div className="kolofon__vrstica">
                  <span className="kolofon__oznaka">Do {okolica.mestoNad}. mesta</span>
                  <span className="kolofon__vrednost">
                    {okolica.razlikaNad} {sklonTock(okolica.razlikaNad)}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <GrafElo tocke={p.graf} naTekmo={skociNaTekmo}>
        {z && <PricakovanIzkupicek meseci={z.forma.poMesecih} />}
      </GrafElo>

      {smemZasebno && z && (
        <>
          <Forma podatki={z} pregled={p.pregled} okolica={okolica} />
          <Nasprotniki podatki={z} tekme={p.tekme} lestvica={lestvica.data} mojElo={p.glava.rating} />
          <NiziInTocke podatki={z} />
          <Razrezi podatki={z} />
        </>
      )}
      {smemZasebno && zasebno.error && <SporociloNapake napaka={zasebno.error} />}
      {!smemZasebno && (
        <p className="obvestilo">
          Poglobljene analize (forma, nasprotniki, nizi in točke) vidi samo igralec sam.
          Če je to tvoj profil, se prijavi.
        </p>
      )}

      <div>
        <div className="naslovna-vrstica">
          <h2>Odigrane tekme</h2>
          <span className="sekcija__meta">{p.tekme.length} skupaj</span>
        </div>
        {p.tekme.length === 0 ? (
          <p className="obvestilo">Ta igralec še ni odigral nobene tekme.</p>
        ) : (
          <SeznamTekem tekme={p.tekme} poudarjena={poudarjenaTekma} />
        )}
      </div>
    </section>
  )
}

/* ---------------- 1. Napredek ELO: pričakovan proti doseženemu ---------------- */

/* Stolpec je dosežen izkupiček meseca, črna črta čezenj pa vsota verjetnosti
   zmage iz razlike ratingov. Razlika pod stolpcem pove, ali je igralec mesec
   odigral nad ali pod svojim ratingom. */
function PricakovanIzkupicek({ meseci }: { meseci: ProfilMesec[] }) {
  if (meseci.length === 0) return null

  const vidni = meseci.slice(-12)
  const SIRINA = 720
  const VISINA = 170
  const OS_Y = 160
  const VISINA_STOLPCA = 130
  /* Korak je izpeljan iz števila mesecev, da stolpci vedno zapolnijo ploskev —
     pri devetih mesecih da 72 px in 26 px širok stolpec kot na maketi. */
  const korak = (SIRINA - 72) / vidni.length
  const sirinaStolpca = Math.min(48, korak * 0.36)

  const najvec =
    Math.ceil(Math.max(...vidni.map((m) => Math.max(m.zmage, m.pricakovaneZmage)))) + 1
  const x = (i: number) => 56 + i * korak
  const h = (v: number) => (v / najvec) * VISINA_STOLPCA
  const skupnaRazlika = vidni.reduce((a, m) => a + m.zmage - m.pricakovaneZmage, 0)
  const nad = skupnaRazlika >= 0

  return (
    <>
      <div className="podnaslov-sekcije">Pričakovan proti doseženemu izkupičku</div>
      <div className="izkupicek">
        <div>
          <svg
            className="izkupicek__graf"
            viewBox={`0 0 ${SIRINA} ${VISINA}`}
            role="img"
            aria-label="Pričakovane in dosežene zmage po mesecih"
          >
            <line className="izkupicek__os" x1={40} x2={SIRINA - 8} y1={OS_Y} y2={OS_Y} />
            {vidni.map((m, i) => (
              <g key={m.mesec}>
                <rect
                  className="izkupicek__stolpec"
                  x={x(i)}
                  y={OS_Y - h(m.zmage)}
                  width={sirinaStolpca}
                  height={h(m.zmage)}
                />
                <line
                  className="izkupicek__pricakovano"
                  x1={x(i) - 6}
                  x2={x(i) + sirinaStolpca + 6}
                  y1={OS_Y - h(m.pricakovaneZmage)}
                  y2={OS_Y - h(m.pricakovaneZmage)}
                />
              </g>
            ))}
          </svg>
          <div className="izkupicek__oznake">
            {vidni.map((m, i) => {
              const razlika = m.zmage - m.pricakovaneZmage
              return (
                <span
                  key={m.mesec}
                  className="izkupicek__oznaka"
                  style={{ left: `${((x(i) + sirinaStolpca / 2) / SIRINA) * 100}%` }}
                >
                  {oznakaMeseca(m.mesec)}
                  <span
                    className={
                      'izkupicek__razlika' +
                      (razlika >= 0 ? ' izkupicek__razlika--plus' : ' izkupicek__razlika--minus')
                    }
                  >
                    {stevilka(razlika, true)}
                  </span>
                </span>
              )
            })}
          </div>
        </div>

        <div>
          <div className="izkupicek__vrstica">
            <span className="izkupicek__naziv">
              {nad ? 'Nad pričakovanji' : 'Pod pričakovanji'}
            </span>
            <span
              className={
                'izkupicek__vsota' +
                (nad ? ' izkupicek__vsota--plus' : ' izkupicek__vsota--minus')
              }
            >
              {stevilka(skupnaRazlika, true)}
            </span>
          </div>
          <p className="izkupicek__opis">
            Modri stolpec je doseženo število zmag v mesecu, črna črta pa pričakovano glede na
            ELO nasprotnikov. Skupno si v {vidni.length} {sklonMesecih(vidni.length)} zbral{' '}
            {stevilka(Math.abs(skupnaRazlika), false)} zmage {nad ? 'več' : 'manj'}, kot bi jih
            povprečen igralec tvojega ratinga.
          </p>
        </div>
      </div>
    </>
  )
}

/* ---------------- 2. Forma ---------------- */

function Forma({
  podatki,
  pregled,
  okolica,
}: {
  podatki: ProfilZasebnoDto
  pregled: ProfilDto['pregled']
  okolica: Okolica | null
}) {
  const f = podatki.forma
  /* DTO pošlje zadnjih deset od najnovejšega; trak beremo kot zapisnik —
     najstarejša tekma levo. */
  const trak = [...f.zadnjih10].reverse()
  const zmag10 = trak.filter(Boolean).length

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Forma</h2>
        <span className="plosca__zasebno">Samo zate</span>
      </div>

      <div className="forma">
        <div>
          <div className="forma__trak">
            {trak.length === 0 && <span className="obvestilo">Ni še tekem.</span>}
            {trak.map((zmaga, i) => (
              <span
                key={i}
                className={'forma__znak ' + (zmaga ? 'forma__znak--z' : 'forma__znak--p')}
              >
                {zmaga ? 'Z' : 'P'}
              </span>
            ))}
          </div>
          {trak.length > 0 && (
            <div className="forma__legenda">
              <span>Zadnjih {trak.length} tekem · najstarejša levo</span>
              <span>
                {zmag10}–{trak.length - zmag10}
              </span>
            </div>
          )}

          <div className="forma__povzetek">
            <Kolobar odstotek={pregled.odstotekZmag} />
            <div>
              <FormaKazalnik
                oznaka={f.trenutniNizZmag ? 'Niz zmag' : 'Niz porazov'}
                vrednost={f.trenutniNiz}
              />
              <FormaKazalnik oznaka="Najdaljši niz zmag" vrednost={f.najdaljsiNizZmag} />
              <FormaKazalnik oznaka="Najvišji ELO" vrednost={f.najvisjiElo ?? '—'} />
            </div>
          </div>
        </div>

        <div>
          <OkolicaBlok okolica={okolica} />
        </div>
      </div>
    </div>
  )
}

/* Kolobar deleža zmag. Številka in oznaka sta HTML nad SVG, ne <text> v njem:
   pisava se tako ne razteza skupaj z grafiko in ostane 40 px povsod. */
function Kolobar({ odstotek }: { odstotek: number }) {
  const OBSEG = 2 * Math.PI * 60
  const lok = (Math.max(0, Math.min(100, odstotek)) / 100) * OBSEG
  return (
    <div className="kolobar">
      <svg className="kolobar__svg" viewBox="0 0 160 160" role="img" aria-label="Delež zmag">
        <circle className="kolobar__podlaga" cx="80" cy="80" r="60" />
        <circle
          className="kolobar__lok"
          cx="80"
          cy="80"
          r="60"
          strokeDasharray={`${lok.toFixed(1)} ${OBSEG.toFixed(2)}`}
          transform="rotate(-90 80 80)"
        />
      </svg>
      <div className="kolobar__sredina">
        <div className="kolobar__vrednost">{odstotek}%</div>
        <div className="kolobar__oznaka">Zmag</div>
      </div>
    </div>
  )
}

/* Igralec pred in za tem igralcem na lestvici — pokaže, koliko točk ELO manjka
   do naslednjega mesta. Brez lestvice (ali brez uvrstitve) se blok ne izriše. */
function OkolicaBlok({ okolica }: { okolica: Okolica | null }) {
  if (!okolica) return null
  return (
    <div className="okolica">
      <div className="okolica__glava">
        <span className="profil__percentil">Okolica na lestvici</span>
        <Link to="/lestvica" className="sekcija__meta">
          Celotna lestvica →
        </Link>
      </div>
      {okolica.vrstice.map((v) => (
        <div
          className={'okolica__vrstica' + (v.jaz ? ' okolica__vrstica--jaz' : '')}
          key={v.idIgralca}
        >
          <span className="okolica__mesto">{v.mesto}.</span>
          <span>
            <span className="okolica__ime">{v.polnoIme}</span>
            <span className="okolica__klub">{v.klub ?? 'brez kluba'}</span>
          </span>
          <span className="okolica__elo">{v.rating ?? '—'}</span>
          <span className="okolica__razlika">
            {v.jaz ? '—' : v.razlika === null ? '' : `${v.razlika > 0 ? '+' : '−'}${Math.abs(v.razlika)}`}
          </span>
        </div>
      ))}
      {okolica.razlikaNad != null && (
        <p className="profil__primerjava">
          Do <strong>{okolica.mestoNad}. mesta</strong> ti manjka {okolica.razlikaNad}{' '}
          {sklonTock(okolica.razlikaNad)} ELO — približno {okolica.zmagDoNaslednjega}{' '}
          {sklonZmag(okolica.zmagDoNaslednjega)} proti močnejšemu nasprotniku.
        </p>
      )}
    </div>
  )
}

/* ---------------- 3. Nasprotniki ---------------- */

function Nasprotniki({
  podatki,
  tekme,
  lestvica,
  mojElo,
}: {
  podatki: ProfilZasebnoDto
  tekme: TekmaProfila[]
  lestvica: LestvicaIgralcaDto[] | undefined
  mojElo: number | null
}) {
  const n = podatki.nasprotniki
  const h2h = izracunajH2H(tekme, lestvica)

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Nasprotniki</h2>
        <span className="plosca__zasebno">Samo zate</span>
      </div>

      <div className="profil__izpostavljeni">
        <Izpostavljen naslov="Najboljša zmaga" nasprotnik={n.najboljsaZmaga} kazeRating />
        <Izpostavljen naslov="Nemesis" nasprotnik={n.nemesis} />
        <Izpostavljen naslov="Najpogostejši nasprotnik" nasprotnik={n.najpogostejsi} kazeTekme />
      </div>

      {h2h.length > 0 && (
        <>
          <div className="podnaslov-sekcije">
            Najpogostejši nasprotniki · gibanje medsebojnih tekem
          </div>
          <div className="h2h__vrstica h2h__vrstica--glava">
            <span>Igralec</span>
            <span className="h2h__desno">Bilanca</span>
            <span className="h2h__sredina">Zadnjih {h2h[0].zadnjih.length}</span>
            <span className="h2h__desno">Njegov ELO</span>
            <span className="h2h__desno">Skupaj</span>
          </div>
          {h2h.map((v) => (
            <div className="h2h__vrstica" key={v.idIgralca}>
              <span>
                <Link to={`/igralci/${v.idIgralca}/profil`} className="profil__nasprotnik">
                  {v.polnoIme}
                </Link>
                <span className="profil__klub">{v.klub ?? 'brez kluba'}</span>
              </span>
              <span className={'h2h__bilanca ' + izidRazred(v.zmage, v.porazi)}>
                {v.zmage}–{v.porazi}
              </span>
              <Sparkline izidi={v.zadnjih} razred={izidRazred(v.zmage, v.porazi)} />
              <span className="h2h__elo">{v.rating ?? '—'}</span>
              <span className="h2h__skupaj">{v.zmage + v.porazi}</span>
            </div>
          ))}
        </>
      )}

      <RazsevniGraf tocke={podatki.razsevni} mojElo={mojElo} />
    </div>
  )
}

/* Gibanje medsebojnih tekem: črta se ob zmagi dvigne, ob porazu spusti.
   Absolutne vrednosti ni — pomembna je smer, zato tudi ni osi. */
function Sparkline({ izidi, razred }: { izidi: boolean[]; razred: string }) {
  /* Korak je izpeljan iz števila tekem, da črta vedno zapolni vseh 120 px —
     pri osmih tekmah da natanko 17 px kot na maketi. */
  const korak = izidi.length > 1 ? 119 / (izidi.length - 1) : 0
  let y = 14
  const tocke = izidi.map((zmaga, i) => {
    y = zmaga ? Math.max(4, y - 5) : Math.min(24, y + 5)
    return `${(i * korak).toFixed(1)},${y}`
  })
  return (
    <svg
      className="sparkline"
      viewBox="0 0 120 28"
      role="img"
      aria-label="Gibanje medsebojnih tekem"
    >
      <line className="sparkline__os" x1="0" x2="120" y1="14" y2="14" />
      <polyline className="sparkline__crta" points={tocke.join(' ')} />
      {izidi.length > 0 && (
        <circle
          className={'sparkline__zadnja ' + razred}
          cx={(izidi.length - 1) * korak}
          cy={y}
          r="3"
        />
      )}
    </svg>
  )
}

/* Razsevni graf: vodoravno ELO nasprotnika ob tekmi, navpično sprememba
   lastnega ratinga. Črtkana navpičnica je lasten ELO — kar je desno od nje,
   je bilo odigrano proti močnejšemu. */
function RazsevniGraf({ tocke, mojElo }: { tocke: RazsevnaTocka[]; mojElo: number | null }) {
  if (tocke.length === 0) return null

  const SIRINA = 1120
  const VISINA = 300
  const LEVO = 56
  const DESNO = 16
  const SREDINA = 150
  const RAZPON = 112

  const ratingi = tocke.map((t) => t.ratingNasprotnika)
  const najmanj = Math.min(...ratingi, mojElo ?? Infinity)
  const najvec = Math.max(...ratingi, mojElo ?? -Infinity)
  const rob = Math.max(30, (najvec - najmanj) * 0.08)
  const od = najmanj - rob
  const doKam = najvec + rob
  const x = (r: number) => LEVO + ((r - od) / (doKam - od)) * (SIRINA - LEVO - DESNO)

  const meja = Math.max(5, Math.ceil(Math.max(...tocke.map((t) => Math.abs(t.sprememba)))))
  const y = (s: number) => SREDINA - (s / meja) * RAZPON

  const korak = doKam - od > 400 ? 100 : doKam - od > 200 ? 50 : 25
  const oznakeX: number[] = []
  for (let v = Math.ceil(od / korak) * korak; v <= doKam; v += korak) oznakeX.push(v)

  const zmagProtiMocnejsim =
    mojElo === null ? 0 : tocke.filter((t) => t.zmaga && t.ratingNasprotnika > mojElo).length

  return (
    <>
      <div className="podnaslov-sekcije">ELO nasprotnika proti izidu</div>
      <div className="razsevni">
        {mojElo !== null && (
          <span
            className="razsevni__moj"
            style={{ left: `${((x(mojElo) + 8) / SIRINA) * 100}%` }}
          >
            Tvoj ELO {mojElo}
          </span>
        )}
        {oznakeX.map((v) => (
          <span
            key={v}
            className="razsevni__os-x"
            style={{ left: `${(x(v) / SIRINA) * 100}%` }}
          >
            {v}
          </span>
        ))}
        <span className="razsevni__os-y" style={{ top: `${(y(meja) / VISINA) * 100}%` }}>
          +{meja}
        </span>
        <span className="razsevni__os-y" style={{ top: `${(SREDINA / VISINA) * 100}%` }}>
          0
        </span>
        <span className="razsevni__os-y" style={{ top: `${(y(-meja) / VISINA) * 100}%` }}>
          −{meja}
        </span>
        <svg
          className="razsevni__svg"
          viewBox={`0 0 ${SIRINA} ${VISINA}`}
          role="img"
          aria-label="Razsevni graf: ELO nasprotnika proti spremembi ratinga"
        >
          <line className="razsevni__nicla" x1={LEVO} x2={SIRINA - DESNO} y1={SREDINA} y2={SREDINA} />
          {mojElo !== null && (
            <line className="razsevni__meja" x1={x(mojElo)} x2={x(mojElo)} y1={16} y2={VISINA - 32} />
          )}
          {tocke.map((t, i) => (
            <circle
              key={i}
              className={'razsevni__tocka ' + (t.zmaga ? 'razsevni__tocka--z' : 'razsevni__tocka--p')}
              cx={x(t.ratingNasprotnika)}
              cy={y(t.sprememba)}
              r="4"
            />
          ))}
        </svg>
      </div>
      <p className="profil__primerjava">
        Vsaka pika je tekma: vodoravno ELO nasprotnika, navpično sprememba tvojega ratinga.
        {mojElo !== null && (
          <> Zelene pike desno od črtkane črte so zmage proti močnejšim — teh je {zmagProtiMocnejsim}.</>
        )}
      </p>
    </>
  )
}

/* ---------------- 4. Nizi in točke ---------------- */

function NiziInTocke({ podatki }: { podatki: ProfilZasebnoDto }) {
  const nt = podatki.niziInTocke
  const t = nt.tocke

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Nizi in točke</h2>
        <span className="plosca__zasebno">Samo zate</span>
      </div>

      <ToplotnaKarta razmerja={nt.razmerja} />

      <div className="nizi__stolpca">
        <div>
          <div className="razrez__naslov">Pod pritiskom</div>
          <div className="nizi__kazalniki">
            <Kazalnik
              oznaka="Odločilni niz"
              vrednost={`${nt.odlocilniNiz.zmage}:${nt.odlocilniNiz.porazi}`}
              prvi
            />
            <Kazalnik
              oznaka="Uspešnost v odl. nizu"
              vrednost={`${nt.odlocilniNiz.odstotek} %`}
              poudarjen
            />
          </div>
          <PritiskVrstica
            oznaka="Odločilni niz"
            odstotek={nt.odlocilniNiz.odstotek}
            vidna={nt.odlocilniNiz.odigrane > 0}
          />
          <PritiskVrstica
            oznaka="Tekme brez izgubljenega niza"
            odstotek={nt.brezIzgubljenegaNiza.odstotek}
            vidna={nt.brezIzgubljenegaNiza.odigrane > 0}
          />
          <PritiskVrstica
            oznaka="Točke pri izidu 9:9 in več"
            odstotek={t.odstotekTockPodPritiskom}
            vidna={t.nizovPodPritiskom > 0}
          />
        </div>

        <div>
          <div className="razrez__naslov">Točke · samo turnirske tekme</div>
          {t.steviloTekem > 0 && (
            <div className="nizi__tocke">
              <Kazalnik oznaka="Osvojene točke" vrednost={t.tockeZa} prvi />
              <Kazalnik oznaka="Prejete točke" vrednost={t.tockeProti} />
              <Kazalnik oznaka="Delež točk" vrednost={`${t.odstotekTock} %`} prvi />
              <Kazalnik oznaka="Povprečje na niz" vrednost={t.povprecjeNaNiz} />
            </div>
          )}
          <p className="profil__opomba">
            {t.steviloTekem === 0
              ? 'Točke po nizih so shranjene samo za turnirske tekme in za zdaj ni nobene take tekme.'
              : `Točke po nizih so shranjene samo za turnirske tekme; izračun temelji na ${t.steviloTekem} takih tekmah, ligaška srečanja hranijo samo nize.`}
          </p>
        </div>
      </div>
    </div>
  )
}

/* Toplotna karta končnih izidov: gostota črnila je pogostost. Nadomešča
   seznam značk, ker med "3:1 ×24" in "0:3 ×7" tam ni bilo videti razlike. */
function ToplotnaKarta({ razmerja }: { razmerja: Razmerje[] }) {
  if (razmerja.length === 0) return null

  /* Urejeno po razliki nizov: gladke zmage levo, gladki porazi desno. */
  const celice = [...razmerja].sort((a, b) => razlikaNizov(b.oznaka) - razlikaNizov(a.oznaka))
  const skupaj = celice.reduce((a, c) => a + c.stevilo, 0)
  const najvec = Math.max(...celice.map((c) => c.stevilo))

  return (
    <>
      <div className="razrez__naslov razrez__naslov--sekcija">
        Končni izidi · gostota črnila je pogostost
      </div>
      {/* Mreža je široka toliko, kolikor je izidov (največ šest v vrsti, na
          telefonu tri) — pri treh izidih bi šest praznih stolpcev naredilo
          luknjo. Število gre v spremenljivko, da odzivno pravilo ostane v CSS. */}
      <div
        className="toplotna"
        style={
          {
            '--stolpcev': Math.min(celice.length, 6),
            '--stolpcev-ozko': Math.min(celice.length, 3),
          } as CSSProperties
        }
      >
        {celice.map((c) => {
          const delez = c.stevilo / najvec
          const gostota = delez > 0.75 ? 'polna' : delez > 0.45 ? 'srednja' : 'mehka'
          return (
            <div key={c.oznaka + (c.zmaga ? 'z' : 'p')}>
              <div
                className={`toplotna__celica toplotna__celica--${c.zmaga ? 'z' : 'p'} toplotna__celica--${gostota}`}
              >
                <div>
                  <div className="toplotna__izid">{c.oznaka}</div>
                  <div className="toplotna__stevec">×{c.stevilo}</div>
                </div>
              </div>
              <div className="toplotna__delez">{Math.round((c.stevilo / skupaj) * 100)} %</div>
              <div className="toplotna__opis">{opisIzida(c.oznaka)}</div>
            </div>
          )
        })}
      </div>
    </>
  )
}

function PritiskVrstica({
  oznaka,
  odstotek,
  vidna,
}: {
  oznaka: string
  odstotek: number
  vidna: boolean
}) {
  if (!vidna) return null
  return (
    <div className="pritisk__vrstica">
      <span className="pritisk__oznaka">{oznaka}</span>
      <Os odstotek={odstotek} sirina={200} />
      <span className="pritisk__vrednost">{odstotek} %</span>
    </div>
  )
}

/* ---------------- 5. Razrezi ---------------- */

function Razrezi({ podatki }: { podatki: ProfilZasebnoDto }) {
  const n = podatki.nasprotniki
  const pt = podatki.poTekmovanjih

  const skupine: { naslov: string; vrstice: Delez[]; opomba?: string }[] = [
    {
      naslov: 'Po igralni roki nasprotnika',
      vrstice: [
        preimenuj(n.protiDesnicarjem, 'Proti desničarjem'),
        preimenuj(n.protiLevicarjem, 'Proti levičarjem'),
        preimenuj(n.rokaNeznana, 'Roka ni znana'),
      ],
    },
    {
      naslov: 'Po moči nasprotnika',
      vrstice: [
        preimenuj(n.protiMocnejsim, 'Močnejši (+50 ELO)'),
        preimenuj(n.protiPodobnim, 'Podoben rating'),
        preimenuj(n.protiSibkejsim, 'Šibkejši (−50 ELO)'),
      ],
    },
    {
      naslov: 'Turnirji, lige, gostovanja',
      vrstice: [
        preimenuj(pt.turnirji, 'Turnirji'),
        preimenuj(pt.lige, 'Liga'),
        preimenuj(pt.doma, 'Liga · doma'),
        preimenuj(pt.vGosteh, 'Liga · v gosteh'),
        preimenuj(pt.dvojice, 'Liga · dvojice'),
      ],
      opomba: pt.dvojice.odigrane > 0
        ? 'Dvojice ne štejejo v ELO ne med posamične zmage.'
        : undefined,
    },
    {
      naslov: 'Po fazi in poziciji',
      vrstice: [
        ...pt.poFazi.map((d) => preimenuj(d, zVelikoZacetnico(d.oznaka))),
        ...pt.poPoziciji.map((d) => preimenuj(d, d.oznaka.replace(/^pozicija /, 'Postava '))),
      ],
    },
  ]

  const vidne = skupine
    .map((s) => ({ ...s, vrstice: s.vrstice.filter((d) => d.odigrane > 0) }))
    .filter((s) => s.vrstice.length > 0)

  if (vidne.length === 0) return null

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razrezi</h2>
        <span className="sekcija__meta">Vse v enem pregledu</span>
      </div>

      <div className="razrezi">
        {vidne.map((s) => (
          <div key={s.naslov}>
            <div className="razrez__naslov">{s.naslov}</div>
            <div className="razrez__vrstica razrez__vrstica--glava">
              <span>Razrez</span>
              <span className="razrez__desno">Tekem</span>
              <span className="razrez__desno">Z–P</span>
              <span className="razrez__sredina">0 – 50 – 100 %</span>
              <span className="razrez__desno">%</span>
            </div>
            {s.vrstice.map((d) => (
              <div className="razrez__vrstica" key={d.oznaka}>
                <span className="razrez__oznaka">{d.oznaka}</span>
                <span className="razrez__tekem">{d.odigrane}</span>
                <span className="razrez__izid">
                  {d.zmage}–{d.porazi}
                </span>
                <Os odstotek={d.odstotek} sirina={160} oznaciSlabse />
                <span className="razrez__odstotek">{d.odstotek} %</span>
              </div>
            ))}
            {s.opomba && <p className="profil__opomba">{s.opomba}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}

/* Os 0–100 % brez polnila: polna palica bi po dolžini tekmovala z drugimi
   vrsticami, tu pa je pomembna samo lega glede na polovico. */
function Os({
  odstotek,
  sirina,
  oznaciSlabse = false,
}: {
  odstotek: number
  sirina: number
  oznaciSlabse?: boolean
}) {
  return (
    <svg
      className="os"
      viewBox={`0 0 ${sirina} 14`}
      width={sirina}
      height={14}
      role="img"
      aria-label={`Uspešnost ${odstotek} odstotkov na osi od 0 do 100`}
    >
      <line className="os__crta" x1="0" x2={sirina} y1="10" y2="10" />
      <line className="os__polovica" x1={sirina / 2} x2={sirina / 2} y1="6" y2="14" />
      <rect
        className={'os__znak' + (oznaciSlabse && odstotek < 50 ? ' os__znak--neg' : '')}
        x={(odstotek / 100) * (sirina - 2)}
        y="0"
        width="2"
        height="14"
      />
    </svg>
  )
}

/* ---------------- 6. Odigrane tekme ---------------- */

/* Turnirske in ligaške tekme imajo ločeni zaporedji id-jev, zato je ključ
   vrstice šele par (vir, id) — enak dogovor kot v grafu ELO. */
function kljucTekme(idTekme: number, ligaska: boolean): string {
  return (ligaska ? 'l' : 't') + idTekme
}

/* Vsaka vrstica nosi id ("tekma-t12" / "tekma-l7"), ker je cilj skoka s točke
   grafa ELO. "poudarjena" je ključ tekme, na katero je gledalec pravkar
   skočil — označena ostane, dokler ne izbere druge. */
function SeznamTekem({
  tekme,
  poudarjena,
}: {
  tekme: TekmaProfila[]
  poudarjena: string | null
}) {
  return (
    <div className="tekme-mreza">
      <div className="tekma-vrstica tekma-vrstica--glava">
        <span>Datum</span>
        <span>Tekmovanje</span>
        <span>Nasprotnik</span>
        <span className="tekma-vrstica__desno">Rezultat</span>
        <span className="tekma-vrstica__desno">ELO</span>
      </div>
      {tekme.map((t) => (
        <div
          className={
            'tekma-vrstica' +
            (poudarjena === kljucTekme(t.idTekme, t.ligaska)
              ? ' tekma-vrstica--poudarjena'
              : '')
          }
          key={kljucTekme(t.idTekme, t.ligaska)}
          id={'tekma-' + kljucTekme(t.idTekme, t.ligaska)}
          tabIndex={-1}
        >
          <span className="tekma-vrstica__datum">{t.datum ? datum(t.datum) : '—'}</span>
          <span className="tekma-vrstica__tekmovanje">
            <span className="tekma-vrstica__ime">{t.tekmovanje}</span>
            <span className="tekma-vrstica__del">
              {t.ligaska ? 'liga · ' : ''}
              {t.del}
              {t.izidTip && t.izidTip !== 'IGRANO' ? ` · ${OZNAKE_IZID[t.izidTip]}` : ''}
            </span>
          </span>
          <span className="tekma-vrstica__nasprotnik">
            <Link to={`/igralci/${t.idNasprotnika}/profil`} className="profil__nasprotnik">
              {t.nasprotnik}
            </Link>
            <span className="profil__klub">{t.klubNasprotnika ?? 'brez kluba'}</span>
          </span>
          <span className={'tekma-vrstica__izid ' + (t.zmaga ? 'profil__zmaga' : 'profil__poraz')}>
            {t.niziZa}:{t.niziProti}
          </span>
          <span
            className={
              'tekma-vrstica__elo ' + (t.zmaga ? 'profil__zmaga' : 'profil__poraz')
            }
          >
            {t.spremembaElo === null
              ? '—'
              : `${t.spremembaElo >= 0 ? '+' : '−'}${Math.abs(t.spremembaElo)}`}
          </span>
        </div>
      ))}
    </div>
  )
}

/* ---------------- Skupne drobne komponente ---------------- */

/* Velika številka v stolpcu, ločenem s hairline (nikoli kartica).
   "prvi" je stolpec brez leve črte, "poudarjen" tisti s 4 px modro. */
function Kazalnik({
  oznaka,
  vrednost,
  prvi = false,
  poudarjen = false,
}: {
  oznaka: string
  vrednost: string | number
  prvi?: boolean
  poudarjen?: boolean
}) {
  return (
    <div
      className={
        'kazalnik' + (prvi ? ' kazalnik--prvi' : '') + (poudarjen ? ' kazalnik--poudarjen' : '')
      }
    >
      <div className="kazalnik__vrednost">{vrednost}</div>
      <div className="kazalnik__oznaka">{oznaka}</div>
    </div>
  )
}

/* Vrstica forme: mono oznaka levo, velika številka desno. */
function FormaKazalnik({ oznaka, vrednost }: { oznaka: string; vrednost: string | number }) {
  return (
    <div className="forma__kazalnik">
      <span className="forma__kazalnik-oznaka">{oznaka}</span>
      <span className="forma__kazalnik-vrednost">{vrednost}</span>
    </div>
  )
}

function Izpostavljen({
  naslov,
  nasprotnik,
  kazeRating = false,
  kazeTekme = false,
}: {
  naslov: string
  nasprotnik: ProfilNasprotnik | null
  kazeRating?: boolean
  kazeTekme?: boolean
}) {
  if (!nasprotnik) return null
  const tekem = nasprotnik.zmage + nasprotnik.porazi
  return (
    <div className="izpostavljen">
      <div className="izpostavljen__naslov">{naslov}</div>
      <Link to={`/igralci/${nasprotnik.idIgralec}/profil`} className="izpostavljen__ime">
        {nasprotnik.polnoIme}
      </Link>
      <div className="izpostavljen__opis">
        {nasprotnik.klub ?? 'brez kluba'} · {nasprotnik.zmage}–{nasprotnik.porazi}
        {kazeRating && nasprotnik.rating !== null && ` · rating ${nasprotnik.rating}`}
        {kazeTekme && ` · ${tekem} ${sklonTekem(tekem)}`}
      </div>
    </div>
  )
}

/* ---------------- Izračuni nad že naloženimi podatki ---------------- */

type OkolicaVrstica = {
  idIgralca: number
  mesto: number
  polnoIme: string
  klub: string | null
  rating: number | null
  razlika: number | null
  jaz: boolean
}

type Okolica = {
  vrstice: OkolicaVrstica[]
  mestoNad: number
  razlikaNad: number | null
  zmagDoNaslednjega: number
}

/* Približek: zmaga proti močnejšemu nasprotniku prinese okrog 15 točk ELO
   (K med 20 in 32, pomnožen z verjetnostjo poraza). */
const TOCK_NA_ZMAGO = 15

function izracunajOkolico(
  vrstice: LestvicaIgralcaDto[] | undefined,
  idIgralec: number,
  mesto: number | null,
): Okolica | null {
  if (!vrstice || mesto === null) return null
  const indeks = vrstice.findIndex((v) => v.idIgralca === idIgralec)
  if (indeks < 0) return null

  const od = Math.max(0, indeks - 1)
  const jaz = vrstice[indeks]
  const nad = indeks > 0 ? vrstice[indeks - 1] : null
  const razlikaNad =
    nad && nad.rating !== null && jaz.rating !== null && nad.rating > jaz.rating
      ? nad.rating - jaz.rating
      : null

  return {
    vrstice: vrstice.slice(od, indeks + 2).map((v, i) => ({
      idIgralca: v.idIgralca,
      mesto: od + i + 1,
      polnoIme: v.polnoIme,
      klub: v.klub,
      rating: v.rating,
      razlika:
        v.idIgralca === idIgralec || v.rating === null || jaz.rating === null
          ? null
          : v.rating - jaz.rating,
      jaz: v.idIgralca === idIgralec,
    })),
    mestoNad: indeks,
    razlikaNad,
    zmagDoNaslednjega: razlikaNad === null ? 0 : Math.max(1, Math.round(razlikaNad / TOCK_NA_ZMAGO)),
  }
}

type H2HVrstica = {
  idIgralca: number
  polnoIme: string
  klub: string | null
  rating: number | null
  zmage: number
  porazi: number
  /* Zadnjih osem medsebojnih tekem, najstarejša prva. */
  zadnjih: boolean[]
}

/* Medsebojne bilance izpeljemo iz že naloženega seznama tekem (od najnovejše),
   aktualni rating nasprotnika pa iz že predpomnjene lestvice — brez novega
   klica na strežnik. */
function izracunajH2H(
  tekme: TekmaProfila[],
  lestvica: LestvicaIgralcaDto[] | undefined,
): H2HVrstica[] {
  const ratingi = new Map((lestvica ?? []).map((v) => [v.idIgralca, v.rating]))
  const po = new Map<number, H2HVrstica>()

  for (const t of tekme) {
    let v = po.get(t.idNasprotnika)
    if (!v) {
      v = {
        idIgralca: t.idNasprotnika,
        polnoIme: t.nasprotnik,
        klub: t.klubNasprotnika,
        rating: ratingi.get(t.idNasprotnika) ?? null,
        zmage: 0,
        porazi: 0,
        zadnjih: [],
      }
      po.set(t.idNasprotnika, v)
    }
    if (t.zmaga) v.zmage++
    else v.porazi++
    if (v.zadnjih.length < 8) v.zadnjih.unshift(t.zmaga)
  }

  return [...po.values()]
    .filter((v) => v.zmage + v.porazi > 1)
    .sort((a, b) => b.zmage + b.porazi - (a.zmage + a.porazi))
    .slice(0, 5)
}

/* "3:1" -> 2; po tej razliki so celice toplotne karte urejene. */
function razlikaNizov(oznaka: string): number {
  const [za, proti] = oznaka.split(':').map(Number)
  return (za || 0) - (proti || 0)
}

/* Kratek opis končnega izida pod celico toplotne karte. */
function opisIzida(oznaka: string): string {
  const [za, proti] = oznaka.split(':').map(Number)
  if (Number.isNaN(za) || Number.isNaN(proti)) return ''
  if (proti === 0) return 'Brez izgubljenega niza'
  if (za === 0) return 'Brez niza'
  if (Math.abs(za - proti) === 1) return 'Odločilni niz'
  return za > proti ? 'Nadzorovano' : 'Brez priložnosti'
}

function izidRazred(zmage: number, porazi: number): string {
  if (zmage > porazi) return 'profil__zmaga'
  if (zmage < porazi) return 'profil__poraz'
  return 'profil__izenaceno'
}

function preimenuj(d: Delez, oznaka: string): Delez {
  return { ...d, oznaka }
}

function zVelikoZacetnico(besedilo: string): string {
  return besedilo.charAt(0).toUpperCase() + besedilo.slice(1)
}

/* Ena decimalka z vejico (slovenski zapis) in po želji predznakom. */
function stevilka(v: number, sPredznakom: boolean): string {
  const zaokrozena = Math.abs(v).toFixed(1).replace('.', ',')
  if (!sPredznakom) return zaokrozena
  return (v >= 0 ? '+' : '−') + zaokrozena
}

/* Zaledje sestavi polno ime kot "Priimek Ime", zato je prva beseda priimek.
   Naslov strani ga postavi v veliko vrstico, ime pa v nadnaslov. */
function razbijIme(polnoIme: string): { priimek: string; ime: string } {
  const presledek = polnoIme.indexOf(' ')
  if (presledek < 0) return { priimek: polnoIme, ime: '' }
  return { priimek: polnoIme.slice(0, presledek), ime: polnoIme.slice(presledek + 1) }
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
