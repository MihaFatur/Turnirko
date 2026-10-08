/* Profil igralca s statistiko.

   Stran je ena sama kronoloska pripoved:
   glava -> Napredek ratinga -> Forma -> Nasprotniki -> Kaj prinese tekma
   -> Nizi in tocke -> Razrezi -> Odigrane tekme.

   Javni del (uvrstitev, blok ratinga, kolofon, graf, seznam tekem) vidi vsak.
   Zasebne analize (forma, nasprotniki, nizi in tocke, razrezi) se nalozijo
   posebej in samo takrat, ko je profil last prijavljenega igralca ali ko gleda
   administrator - streznik na ta klic sicer odgovori s 403.

   Vsi izpeljani prikazi (kolobar, toplotna karta, trak izidov, razsevni graf,
   osi razrezov) se izracunajo iz ProfilDto in ProfilZasebnoDto med izrisom -
   brez novih poizvedb. Okolico na lestvici izracunamo iz lestvice, ki jo
   vmesnik ima ze predpomnjeno (isti kljuc kot LestvicaStran), zozene na
   lestvico igralcevega spola. */
import type { CSSProperties } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

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
  StarostniPas,
  TekmaDvojic,
  TekmaProfila,
  TekmovanjeProfila,
} from '../api/tipi'
import { OZNAKE_IZID, izidNizov } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GrafRatinga } from '../komponente/GrafRatinga'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { NapovedTekme } from '../komponente/NapovedTekme'
import { usePremiumOglas } from '../komponente/PremiumOglasKontekst'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { oznakaPoti, useIzvor } from '../pomozno/izvor'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import {
  oznakaMeseca,
  sezonaIzDatuma,
  sklonMesecih,
  sklonPorazov,
  sklonTekem,
  sklonTekmovanj,
  sklonTock,
  sklonZmag,
} from '../pomozno/oblikovanje'

export function ProfilStran() {
  const { id } = useParams()
  const idIgralec = Number(id)
  const { jeAdmin, mojIdIgralec, jePremium } = useAvtentikacija()
  const { odpri: odpriPremiumOglas } = usePremiumOglas()
  const odjemalec = useQueryClient()
  const navigiraj = useNavigate()
  /* Pot, s katere je gledalec prišel (liga, zapisnik, lestvica ...), ali null,
     če je prišel od zunaj - takrat povezava nazaj pelje na lestvico. */
  const izvor = useIzvor()

  const profil = useQuery({
    queryKey: ['profil', idIgralec],
    queryFn: () => profiliApi.profil(idIgralec),
  })

  /* Zasebni del zahtevamo samo, kadar imamo pravico — da uporabnik ne dobi
     nepotrebne napake 403 v konzoli. Lastnik brez Premium pravice nima -
     zanj je profil enak gostovemu (glej DostopDoProfila na zaledju). */
  const smemZasebno = jeAdmin || (mojIdIgralec === idIgralec && jePremium)
  /* Lastnik profila, ki bi zasebni del videl, ko bi imel Premium - tu dobi
     jasno povabilo k nadgradnji namesto tihega izpusta razdelkov. */
  const lastnikBrezPremium = !jeAdmin && mojIdIgralec === idIgralec && !jePremium
  const zasebno = useQuery({
    queryKey: ['profil-zasebno', idIgralec],
    queryFn: () => profiliApi.zasebno(idIgralec),
    enabled: smemZasebno && !Number.isNaN(idIgralec),
  })

  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Skok s točke grafa ratinga na vrstico iste tekme v seznamu spodaj. Seznam je
     izrisan v celoti (brez straničenja), zato zadošča iskanje po id-ju vrstice.
     Vrstica dobi fokus — brez tega bralnik zaslona po skoku ne pove, kam smo
     prišli — in ostane označena, dokler gledalec ne izbere druge tekme. */
  const [poudarjenaTekma, nastaviPoudarjeno] = useState<string | null>(null)
  /* Tekme so zbrane po tekmovanjih in zaprte, zato skok najprej odpre
     tekmovanje (in pokaže vse sezone, če je tekma v drugi), vrstico pa
     poišče šele po izrisu - `skok` je zahteva, ki jo učinek spodaj izvede. */
  const [odprtaTekmovanja, nastaviOdprtaTekmovanja] = useState<Set<string>>(() => new Set())
  const [sezona, nastaviSezono] = useState<string>(VSE_SEZONE)
  const [skok, nastaviSkok] = useState<{ kljuc: string; zaporedna: number } | null>(null)
  const skociNaTekmo = useCallback(
    (idTekme: number, ligaska: boolean) => {
      const kljuc = kljucTekme(idTekme, ligaska)
      nastaviPoudarjeno(kljuc)
      const tekma = profil.data?.tekme.find((t) => kljucTekme(t.idTekme, t.ligaska) === kljuc)
      if (tekma) {
        nastaviOdprtaTekmovanja((prej) => new Set(prej).add(tekma.tekmovanjeKljuc))
        const tekmovanje = profil.data?.tekmovanja.find((t) => t.kljuc === tekma.tekmovanjeKljuc)
        nastaviSezono((prej) =>
          prej === VSE_SEZONE || sezonaTekmovanja(tekmovanje) === prej ? prej : VSE_SEZONE,
        )
      }
      nastaviSkok((prej) => ({ kljuc, zaporedna: (prej?.zaporedna ?? 0) + 1 }))
    },
    [profil.data],
  )
  useEffect(() => {
    if (!skok) return
    const vrstica = document.getElementById('tekma-' + skok.kljuc)
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
  }, [skok])

  useNaslovStrani(profil.data?.glava.polnoIme)

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
  const vPasu = mestoVPasu(lestvica.data, idIgralec)

  return (
    <section className="profil">
      <div>
        {/* Nazaj tja, od koder je gledalec prišel - na profil najpogosteje
            pride iz lige ali zapisnika, stalna »← Lestvica« pa ga je odpeljala
            drugam. Korak nazaj v zgodovini (in ne nova stran) obdrži stanje
            prejšnje strani, npr. odprto okno ekipe v ligi. */}
        <Link
          to={izvor ?? '/lestvica'}
          className="povezava-nazaj"
          onClick={(dogodek) => {
            /* Odpiranje v novem zavihku (Ctrl/⌘/srednji klik) naj ostane navadna povezava. */
            if (izvor == null || dogodek.button !== 0) return
            if (dogodek.metaKey || dogodek.ctrlKey || dogodek.shiftKey || dogodek.altKey) return
            dogodek.preventDefault()
            navigiraj(-1)
          }}
        >
          ← {izvor ? oznakaPoti(izvor, odjemalec) : 'Lestvica'}
        </Link>

        <div className="stran-glava">
          <div>
            {/* Mesto pove tudi, na kateri lestvici je - samo »81. / 144« ni
                povedalo, med kom (spol, člani oz. rekreativci; isti obseg in
                isti imeni kot na strani Lestvica). */}
            {p.uvrstitev.mesto !== null && (
              <div className="profil__uvrstitev">
                <span className="profil__mesto-znacka">
                  {p.uvrstitev.mesto}. od {p.uvrstitev.skupajIgralcev}
                </span>
                <span className="profil__percentil">
                  {imeLestvice(p.uvrstitev)}
                  {p.uvrstitev.percentil !== null && (
                    <> · boljši od {p.uvrstitev.percentil} %</>
                  )}
                </span>
              </div>
            )}
            {/* Mladinec se primerja s svojim pasom: 104. med člani mu pove
                malo, 12. v U19 pa to, po kar je prišel. Isto merilo kot
                izbira kategorije na strani Lestvica (pas zajame mlajše). */}
            {vPasu && p.uvrstitev.spol !== null && (
              <div className="profil__uvrstitev">
                <span className="profil__mesto-znacka">
                  {vPasu.mesto}. od {vPasu.skupaj}
                </span>
                <span className="profil__percentil">
                  Lestvica {p.uvrstitev.spol === 'ZENSKI' ? 'Ženske' : 'Moški'} · {vPasu.pas}
                </span>
              </div>
            )}

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
            <div className="rating-blok">
              <span className="rating-blok__oznaka">Turnirko rating</span>
              <span className="rating-blok__vrednost">{p.glava.rating ?? '—'}</span>
              {z?.forma && (
                <div className="rating-blok__noga">
                  <span>
                    {z.forma.spremembaElo30dni === null
                      ? 'brez tekem v 30 dneh'
                      : `${z.forma.spremembaElo30dni >= 0 ? '+' : '−'}${Math.abs(z.forma.spremembaElo30dni)} / 30 dni`}
                  </span>
                  {z.forma.najvisjiRating !== null && (
                    <span className="rating-blok__vrh">vrh {z.forma.najvisjiRating}</span>
                  )}
                </div>
              )}
              {/* Številka brez razlage se bere kot napaka ali naklonjenost -
                  posebej po prvem dnevu, ko skoči za stotine točk. */}
              <Link to="/o-ratingu" className="rating-blok__povezava">
                Kako se računa →
              </Link>
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
              {/* Dve vrstici in ne »Turnirji / lige 0 / 4« - to se je bralo
                  kot štiri lige, v resnici pa so tekme. */}
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Tekme na turnirjih</span>
                <span className="kolofon__vrednost">{p.pregled.turnirskih}</span>
              </div>
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Ligaške tekme</span>
                <span className="kolofon__vrednost">{p.pregled.ligaskih}</span>
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

      <GrafRatinga tocke={p.graf} naTekmo={skociNaTekmo}>
        {z && <PricakovanIzkupicek meseci={z.forma.poMesecih} />}
      </GrafRatinga>

      {smemZasebno && z && (
        <>
          <Forma podatki={z} pregled={p.pregled} okolica={okolica} />
          <Nasprotniki podatki={z} tekme={p.tekme} lestvica={lestvica.data} mojRating={p.glava.rating} />
          {/* Napoved stoji za »Nasprotniki«: tam gledalec vidi, proti komu je
              igral, tu pa vpraša, kaj bi prinesel naslednji. Vrstni red je
              isti kot pripoved strani — najprej kaj je bilo, potem kaj bo. */}
          <NapovedTekme idIgralec={idIgralec} />
          <NiziInTocke podatki={z} />
          <Razrezi podatki={z} />
        </>
      )}
      {smemZasebno && zasebno.error && <SporociloNapake napaka={zasebno.error} />}
      {lastnikBrezPremium && (
        <div className="obvestilo">
          Forma, nasprotniki, napoved tekme, nizi in točke ter razrezi so Premium funkcija.{' '}
          <button
            type="button"
            className="povezava-gumb povezava-gumb--vrstica"
            onClick={() => odpriPremiumOglas('statistika')}
          >
            Nadgradi na Premium
          </button>
          , da jih vidiš.
        </div>
      )}

      {p.tekme.length === 0 ? (
        <div>
          <div className="naslovna-vrstica">
            <h2>Odigrane tekme</h2>
          </div>
          <p className="obvestilo">Ta igralec še ni odigral nobene tekme.</p>
        </div>
      ) : (
        <TekmePoTekmovanjih
          tekme={p.tekme}
          tekmovanja={p.tekmovanja}
          sezona={sezona}
          naSezono={nastaviSezono}
          odprta={odprtaTekmovanja}
          naPreklop={(kljuc) =>
            nastaviOdprtaTekmovanja((prej) => {
              const novo = new Set(prej)
              if (novo.has(kljuc)) novo.delete(kljuc)
              else novo.add(kljuc)
              return novo
            })
          }
          poudarjena={poudarjenaTekma}
        />
      )}

      {/* Dvojice so SVOJ seznam in ne štejejo ne v pregled ne v rating: izida
          para ni mogoče pripisati posamezniku. Razdelek se pokaže samo
          igralcu, ki je dvojice sploh igral. */}
      {p.dvojice.length > 0 && (
        <div>
          <div className="naslovna-vrstica">
            <h2>Dvojice</h2>
            <span className="sekcija__meta">{p.dvojice.length} skupaj</span>
          </div>
          <SeznamDvojic tekme={p.dvojice} />
          <p className="namig">
            Tekme dvojic ne štejejo v zgornji izkupiček ne v Turnirko rating —
            izida para ni mogoče pripisati posamezniku.
          </p>
        </div>
      )}
    </section>
  )
}

/* ---------------- 1. Napredek ratinga: pričakovan proti doseženemu ---------------- */

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
            rating nasprotnikov. Skupno si v {vidni.length} {sklonMesecih(vidni.length)} zbral{' '}
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
              <FormaKazalnik oznaka="Najvišji rating" vrednost={f.najvisjiRating ?? '—'} />
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

/* Igralec pred in za tem igralcem na lestvici njegovega spola — pokaže, koliko
   točk ratinga manjka do naslednjega mesta. Brez lestvice (ali brez uvrstitve)
   se blok ne izriše. */
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
          <span className="okolica__rating">{v.rating ?? '—'}</span>
          <span className="okolica__razlika">
            {v.jaz ? '—' : v.razlika === null ? '' : `${v.razlika > 0 ? '+' : '−'}${Math.abs(v.razlika)}`}
          </span>
        </div>
      ))}
      {okolica.razlikaNad != null && (
        <p className="profil__primerjava">
          Do <strong>{okolica.mestoNad}. mesta</strong> ti manjka {okolica.razlikaNad}{' '}
          {sklonTock(okolica.razlikaNad)} rating — približno {okolica.zmagDoNaslednjega}{' '}
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
  mojRating,
}: {
  podatki: ProfilZasebnoDto
  tekme: TekmaProfila[]
  lestvica: LestvicaIgralcaDto[] | undefined
  mojRating: number | null
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
        {/* Desna vrednost je pri vsakem izpostavljenem tista, zaradi katere je
            izpostavljen: rating premaganega, število porazov, število tekem. */}
        <Izpostavljen
          naslov="Najboljša zmaga"
          nasprotnik={n.najboljsaZmaga}
          desno={n.najboljsaZmaga?.rating ?? null}
        />
        <Izpostavljen
          naslov="Najtežji nasprotnik"
          nasprotnik={n.nemesis}
          desno={n.nemesis ? `${n.nemesis.porazi} ${sklonPorazov(n.nemesis.porazi)}` : null}
        />
        <Izpostavljen
          naslov="Najpogostejši nasprotnik"
          nasprotnik={n.najpogostejsi}
          desno={
            n.najpogostejsi
              ? `${n.najpogostejsi.zmage}–${n.najpogostejsi.porazi} · ${
                  n.najpogostejsi.zmage + n.najpogostejsi.porazi
                } ${sklonTekem(n.najpogostejsi.zmage + n.najpogostejsi.porazi)}`
              : null
          }
        />
      </div>

      {h2h.length > 0 && (
        <>
          <div className="podnaslov-sekcije">Najpogostejši nasprotniki</div>
          <div className="h2h__vrstica h2h__vrstica--glava">
            <span>Igralec</span>
            <span>Medsebojni rezultat</span>
            <span className="h2h__sredina">Zadnjih {h2h[0].zadnjih.length}</span>
            <span className="h2h__desno">Njegov rating</span>
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
              {/* Isti zapisnik kot trak forme: najstarejša tekma levo, Z zeleno,
                  P rdeče — smer je razvidna brez branja krivulje. */}
              <span className="h2h__trak">
                {v.zadnjih.map((zmaga, i) => (
                  <span
                    key={i}
                    className={'h2h__znak ' + (zmaga ? 'h2h__znak--z' : 'h2h__znak--p')}
                  >
                    {zmaga ? 'Z' : 'P'}
                  </span>
                ))}
              </span>
              <span className="h2h__rating">{v.rating ?? '—'}</span>
              <span className="h2h__skupaj">{v.zmage + v.porazi}</span>
            </div>
          ))}
        </>
      )}

      <RazsevniGraf tocke={podatki.razsevni} mojRating={mojRating} />
    </div>
  )
}

/* Razsevni graf: vodoravno rating nasprotnika ob tekmi, navpično sprememba
   lastnega ratinga. Črtkana navpičnica je lasten rating — kar je desno od nje,
   je bilo odigrano proti močnejšemu. */
function RazsevniGraf({ tocke, mojRating }: { tocke: RazsevnaTocka[]; mojRating: number | null }) {
  if (tocke.length === 0) return null

  const SIRINA = 1120
  const VISINA = 300
  const LEVO = 56
  const DESNO = 16
  const SREDINA = 150
  const RAZPON = 112

  const ratingi = tocke.map((t) => t.ratingNasprotnika)
  const najmanj = Math.min(...ratingi, mojRating ?? Infinity)
  const najvec = Math.max(...ratingi, mojRating ?? -Infinity)
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
    mojRating === null ? 0 : tocke.filter((t) => t.zmaga && t.ratingNasprotnika > mojRating).length

  return (
    <>
      <div className="podnaslov-sekcije">rating nasprotnika proti izidu</div>
      <div className="razsevni">
        {mojRating !== null && (
          <span
            className="razsevni__moj"
            style={{ left: `${((x(mojRating) + 8) / SIRINA) * 100}%` }}
          >
            Tvoj rating {mojRating}
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
          aria-label="Razsevni graf: rating nasprotnika proti spremembi ratinga"
        >
          <line className="razsevni__nicla" x1={LEVO} x2={SIRINA - DESNO} y1={SREDINA} y2={SREDINA} />
          {mojRating !== null && (
            <line className="razsevni__meja" x1={x(mojRating)} x2={x(mojRating)} y1={16} y2={VISINA - 32} />
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
        Vsaka pika je tekma: vodoravno rating nasprotnika, navpično sprememba tvojega ratinga.
        {mojRating !== null && (
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
            oznaka="Zmaga seta na razliko"
            odstotek={t.odstotekTockPodPritiskom}
            vidna={t.nizovPodPritiskom > 0}
          />
        </div>

        <div>
          <div className="razrez__naslov">Točke</div>
          {t.steviloTekem > 0 && (
            <div className="nizi__tocke">
              <Kazalnik oznaka="Osvojene točke" vrednost={t.tockeZa} prvi />
              <Kazalnik oznaka="Izgubljene točke" vrednost={t.tockeProti} />
              <Kazalnik oznaka="Delež točk" vrednost={`${t.odstotekTock} %`} prvi />
              <Kazalnik oznaka="Povprečje na niz" vrednost={t.povprecjeNaNiz} />
            </div>
          )}
          {t.steviloTekem === 0 && (
            <p className="profil__opomba">
              Vnos točk po nizih je neobvezen in za zdaj ni nobene tekme, pri kateri bi bile
              vpisane.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

/* Toplotna karta končnih izidov: gostota črnila je pogostost celice. Nadomešča
   seznam značk, ker med "3:1 ×24" in "0:3 ×7" tam ni bilo videti razlike. */
function ToplotnaKarta({ razmerja }: { razmerja: Razmerje[] }) {
  if (razmerja.length === 0) return null

  /* Urejeno po razliki nizov: gladke zmage levo, gladki porazi desno. */
  const celice = [...razmerja].sort((a, b) => razlikaNizov(b.oznaka) - razlikaNizov(a.oznaka))
  const skupaj = celice.reduce((a, c) => a + c.stevilo, 0)
  const najvec = Math.max(...celice.map((c) => c.stevilo))

  return (
    <>
      <div className="razrez__naslov razrez__naslov--sekcija">Končni izidi</div>
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

  const skupine: { naslov: string; vrstice: Delez[] }[] = [
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
        preimenuj(n.protiMocnejsim, 'Močnejši (+50 točk)'),
        preimenuj(n.protiPodobnim, 'Podoben rating'),
        preimenuj(n.protiSibkejsim, 'Šibkejši (−50 točk)'),
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
    },
    {
      naslov: 'Po fazi in poziciji',
      vrstice: [
        ...pt.poFazi.map((d) => preimenuj(d, zVelikoZacetnico(d.oznaka))),
        ...pt.poPoziciji.map((d) => preimenuj(d, zVelikoZacetnico(d.oznaka))),
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
                <Os odstotek={d.odstotek} sirina={132} oznaciSlabse />
                <span className="razrez__odstotek">{d.odstotek} %</span>
              </div>
            ))}
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

const VSE_SEZONE = 'vse'

function sezonaTekmovanja(t: TekmovanjeProfila | undefined): string | null {
  return t ? sezonaIzDatuma(t.datum) : null
}

/* »5. mesto od 32«, deljeno »3.–4. mesto od 16«; pri ligi mesta ni. */
function oznakaMestaTekmovanja(t: TekmovanjeProfila): string | null {
  if (t.mesto === null) return null
  const mesto =
    t.mestoDo !== null && t.mestoDo > t.mesto ? `${t.mesto}.–${t.mestoDo}.` : `${t.mesto}.`
  return t.udelezencev !== null ? `${mesto} mesto od ${t.udelezencev}` : `${mesto} mesto`
}

/* Odigrane tekme, zbrane po tekmovanju: vsaka kategorija turnirja oz. liga je
   ena vrstica »OT Kidričevo · 5–1 · 5. mesto od 32 · +64«, tekme so pod njo
   (klik razpre). Prej je bilo 419 tekem v enem seznamu in ime turnirja (z
   datumom v imenu) se je ponovilo v vsaki vrstici - mladinec, ki išče svoje
   uvrstitve, jih ni našel nikjer. Nad seznamom sta izbira sezone in odličja
   (prva tri mesta v kategorijah turnirjev; deljeno 3. mesto šteje kot 3.). */
function TekmePoTekmovanjih({
  tekme,
  tekmovanja,
  sezona,
  naSezono,
  odprta,
  naPreklop,
  poudarjena,
}: {
  tekme: TekmaProfila[]
  tekmovanja: TekmovanjeProfila[]
  sezona: string
  naSezono: (sezona: string) => void
  odprta: Set<string>
  naPreklop: (kljuc: string) => void
  poudarjena: string | null
}) {
  const sezone = useMemo(() => {
    const vse = new Set<string>()
    for (const t of tekmovanja) {
      const s = sezonaTekmovanja(t)
      if (s) vse.add(s)
    }
    return [...vse].sort((a, b) => b.localeCompare(a))
  }, [tekmovanja])
  const izbrana = useMemo(
    () => (sezona === VSE_SEZONE ? tekmovanja : tekmovanja.filter((t) => sezonaTekmovanja(t) === sezona)),
    [tekmovanja, sezona],
  )
  const tekmePoTekmovanju = useMemo(() => {
    const po = new Map<string, TekmaProfila[]>()
    for (const t of tekme) {
      const seznam = po.get(t.tekmovanjeKljuc)
      if (seznam) seznam.push(t)
      else po.set(t.tekmovanjeKljuc, [t])
    }
    return po
  }, [tekme])
  const steviloTekem = izbrana.reduce((vsota, t) => vsota + t.zmage + t.porazi, 0)
  const odlicja = [1, 2, 3].map(
    (mesto) => izbrana.filter((t) => !t.ligaska && t.mesto === mesto).length,
  )

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Odigrane tekme</h2>
        <span className="sekcija__meta">
          {steviloTekem} {sklonTekem(steviloTekem)} · {izbrana.length}{' '}
          {sklonTekmovanj(izbrana.length)}
        </span>
      </div>

      <div className="tekmovanja-profila__krmila">
        {sezone.length > 1 && (
          <label className="krmilo-izbor">
            <span className="samo-za-bralnik">Sezona</span>
            <span className="krmilo-izbor__oznaka" aria-hidden="true">
              {sezona === VSE_SEZONE ? 'Vse sezone' : `Sezona ${sezona}`}
              <span className="krmilo-izbor__puscica">▾</span>
            </span>
            <select
              className="krmilo-izbor__polje"
              value={sezona}
              onChange={(dogodek) => naSezono(dogodek.target.value)}
            >
              <option value={VSE_SEZONE}>Vse sezone</option>
              {sezone.map((s) => (
                <option key={s} value={s}>
                  Sezona {s}
                </option>
              ))}
            </select>
          </label>
        )}
        {odlicja.some((n) => n > 0) && (
          <span className="tekmovanja-profila__odlicja">
            {odlicja.map((n, i) =>
              n > 0 ? (
                <span key={i} className={`tekmovanja-profila__odlicje tekmovanja-profila__odlicje--${i + 1}`}>
                  {i + 1}. mesto × {n}
                </span>
              ) : null,
            )}
          </span>
        )}
      </div>

      <div className="tekmovanja-profila">
        {izbrana.map((t) => {
          const jeOdprto = odprta.has(t.kljuc)
          const mesto = oznakaMestaTekmovanja(t)
          const naStopnickah = !t.ligaska && t.mesto !== null && t.mesto <= 3
          return (
            <div
              key={t.kljuc}
              className={
                'tekmovanje-profila'
                + (naStopnickah ? ` tekmovanje-profila--${t.mesto}` : '')
                + (jeOdprto ? ' tekmovanje-profila--odprto' : '')
              }
            >
              <button
                type="button"
                className="tekmovanje-profila__glava"
                aria-expanded={jeOdprto}
                onClick={() => naPreklop(t.kljuc)}
              >
                <span className="tekmovanje-profila__ime">{t.ime}</span>
                <span className="tekmovanje-profila__meta">
                  {[t.del, t.datum ? datum(t.datum) : null, mesto].filter(Boolean).join(' · ')}
                </span>
                <span className="tekmovanje-profila__izid">
                  {t.zmage}–{t.porazi}
                </span>
                <span
                  className={
                    'tekmovanje-profila__rating'
                    + (t.spremembaRatinga === null
                      ? ''
                      : t.spremembaRatinga >= 0
                        ? ' profil__zmaga'
                        : ' profil__poraz')
                  }
                >
                  {t.spremembaRatinga === null
                    ? ''
                    : `${t.spremembaRatinga >= 0 ? '+' : '−'}${Math.abs(t.spremembaRatinga)}`}
                </span>
                <span className="tekmovanje-profila__puscica" aria-hidden="true">
                  {jeOdprto ? '▴' : '▾'}
                </span>
              </button>
              {jeOdprto && (
                <SeznamTekem
                  tekme={tekmePoTekmovanju.get(t.kljuc) ?? []}
                  poudarjena={poudarjena}
                  vTekmovanju
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

/* Turnirske in ligaške tekme imajo ločeni zaporedji id-jev, zato je ključ
   vrstice šele par (vir, id) — enak dogovor kot v grafu ratinga. */
function kljucTekme(idTekme: number, ligaska: boolean): string {
  return (ligaska ? 'l' : 't') + idTekme
}

/* Vsaka vrstica nosi id ("tekma-t12" / "tekma-l7"), ker je cilj skoka s točke
   grafa ratinga. "poudarjena" je ključ tekme, na katero je gledalec pravkar
   skočil — označena ostane, dokler ne izbere druge. */
function SeznamTekem({
  tekme,
  poudarjena,
  vTekmovanju = false,
}: {
  tekme: TekmaProfila[]
  poudarjena: string | null
  /* Seznam pod vrstico tekmovanja: ime tekmovanja je že v njej, zato stolpec
     nosi le del (kolo s parom ekip pri ligi, kategorijo pri turnirju ne). */
  vTekmovanju?: boolean
}) {
  return (
    <div className={'tekme-mreza' + (vTekmovanju ? ' tekme-mreza--v-tekmovanju' : '')}>
      <div className="tekma-vrstica tekma-vrstica--glava">
        <span>Datum</span>
        <span>{vTekmovanju ? 'Kolo' : 'Tekmovanje'}</span>
        <span>Nasprotnik</span>
        <span className="tekma-vrstica__desno">Rezultat</span>
        <span className="tekma-vrstica__desno">± rating</span>
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
            {vTekmovanju ? (
              /* Pri turnirski tekmi je lahko prazno - takrat span ostane prazen
                 in CSS ne izpiše ločila »·« za datumom. */
              [t.ligaska ? t.del : null, t.izidTip && t.izidTip !== 'IGRANO' ? OZNAKE_IZID[t.izidTip] : null]
                .filter(Boolean)
                .join(' · ') || null
            ) : (
              <>
                <span className="tekma-vrstica__ime">{t.tekmovanje}</span>
                <span className="tekma-vrstica__del">
                  {t.ligaska ? 'liga · ' : ''}
                  {t.del}
                  {t.izidTip && t.izidTip !== 'IGRANO' ? ` · ${OZNAKE_IZID[t.izidTip]}` : ''}
                </span>
              </>
            )}
          </span>
          <span className="tekma-vrstica__nasprotnik">
            <Link to={`/igralci/${t.idNasprotnika}/profil`} className="profil__nasprotnik">
              {t.nasprotnik}
            </Link>
            <span className="profil__klub">{t.klubNasprotnika ?? 'brez kluba'}</span>
          </span>
          <span className={'tekma-vrstica__izid ' + (t.zmaga ? 'profil__zmaga' : 'profil__poraz')}>
            {izidNizov(t.niziZa, t.niziProti, t.izidTip)}
          </span>
          {/* Barva po predznaku spremembe in ne po izidu: na dan uvrstitve
              novinca zmaga lahko rating tudi zniža. */}
          <span
            className={
              'tekma-vrstica__rating' +
              (t.spremembaRatinga === null
                ? ''
                : t.spremembaRatinga >= 0
                  ? ' profil__zmaga'
                  : ' profil__poraz')
            }
            title="Sprememba Turnirko ratinga zaradi te tekme"
          >
            {t.spremembaRatinga === null
              ? '—'
              : `${t.spremembaRatinga >= 0 ? '+' : '−'}${Math.abs(t.spremembaRatinga)}`}
          </span>
        </div>
      ))}
    </div>
  )
}

/* Tekme dvojic. Stolpec "Nasprotnik" nosi cel nasprotni par, stolpec ratinga pa
   odpade - dvojice se v rating ne obračunajo; namesto njega stoji soigralec,
   ki je pri dvojicah edini podatek, ki ga v posamični tabeli ni. */
function SeznamDvojic({ tekme }: { tekme: TekmaDvojic[] }) {
  return (
    <div className="tekme-mreza">
      <div className="tekma-vrstica tekma-vrstica--glava">
        <span>Datum</span>
        <span>Tekmovanje</span>
        <span>Nasprotni par</span>
        <span className="tekma-vrstica__desno">Rezultat</span>
        <span className="tekma-vrstica__desno">Soigralec</span>
      </div>
      {tekme.map((t) => (
        <div className="tekma-vrstica" key={t.idTekme}>
          <span className="tekma-vrstica__datum">{t.datum ? datum(t.datum) : '—'}</span>
          <span className="tekma-vrstica__tekmovanje">
            <span className="tekma-vrstica__ime">{t.tekmovanje}</span>
            <span className="tekma-vrstica__del">
              {t.del}
              {t.izidTip && t.izidTip !== 'IGRANO' ? ` · ${OZNAKE_IZID[t.izidTip]}` : ''}
            </span>
          </span>
          <span className="tekma-vrstica__nasprotnik">{t.nasprotnika}</span>
          <span className={'tekma-vrstica__izid ' + (t.zmaga ? 'profil__zmaga' : 'profil__poraz')}>
            {izidNizov(t.niziZa, t.niziProti, t.izidTip)}
          </span>
          <span className="tekma-vrstica__nasprotnik">
            {t.idSoigralca !== null ? (
              <Link to={`/igralci/${t.idSoigralca}/profil`} className="profil__nasprotnik">
                {t.soigralec}
              </Link>
            ) : (
              '—'
            )}
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

/* Naslov, pod njim ime levo in poudarjena vrednost ("desno") v isti vrstici,
   pod obema klub. Katera vrednost to je, pove klicatelj — pri vsakem
   izpostavljenem je druga. */
function Izpostavljen({
  naslov,
  nasprotnik,
  desno,
}: {
  naslov: string
  nasprotnik: ProfilNasprotnik | null
  desno: string | number | null
}) {
  if (!nasprotnik) return null
  return (
    <div className="izpostavljen">
      <div className="izpostavljen__naslov">{naslov}</div>
      <div className="izpostavljen__vrstica">
        <Link to={`/igralci/${nasprotnik.idIgralec}/profil`} className="izpostavljen__ime">
          {nasprotnik.polnoIme}
        </Link>
        {desno !== null && <span className="izpostavljen__vrednost">{desno}</span>}
      </div>
      <div className="izpostavljen__opis">{nasprotnik.klub ?? 'brez kluba'}</div>
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

/* Približek: zmaga proti močnejšemu nasprotniku prinese okrog 15 točk ratinga
   (K med 20 in 32, pomnožen z verjetnostjo poraza). */
const TOCK_NA_ZMAGO = 15

/* Sosedi so sosedi na lestvici, na kateri igralec STOJI: njegov spol in njegova
   skupina (tekmovalci ali rekreativci). Med moškimi in ženskami ni niti ene
   obračunane tekme, zato skupna lestvica ne pomeni ničesar — »do 5. mesta«, ki
   šteje tudi igralce drugega spola, bi bilo drugo število kot mesto v glavi
   profila, ki ga strežnik (ProfilStoritev.uvrstitev) meri po isti lestvici. */
function izracunajOkolico(
  skupna: LestvicaIgralcaDto[] | undefined,
  idIgralec: number,
  mesto: number | null,
): Okolica | null {
  if (!skupna || mesto === null) return null
  const igralec = skupna.find((v) => v.idIgralca === idIgralec)
  // igralec brez spola ne pripada nobeni lestvici (isto kot na strani lestvice)
  if (!igralec || igralec.spol === null) return null
  const vrstice = skupna.filter(
    (v) => v.spol === igralec.spol && v.rekreativec === igralec.rekreativec,
  )
  const indeks = vrstice.findIndex((v) => v.idIgralca === idIgralec)

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
  return za > proti ? 'Nadzorovano' : 'Borba'
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

/* Zaledje sestavi polno ime kot "Ime Priimek", zato je prva beseda ime, vse
   ostalo pa priimek (dvodelni priimki ostanejo celi). Naslov strani postavi
   priimek v veliko vrstico, ime pa v nadnaslov nad njo. */
function razbijIme(polnoIme: string): { priimek: string; ime: string } {
  const presledek = polnoIme.indexOf(' ')
  if (presledek < 0) return { priimek: polnoIme, ime: '' }
  return { ime: polnoIme.slice(0, presledek), priimek: polnoIme.slice(presledek + 1) }
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}

/* Mladinski pasovi od najmlajšega navzgor - isti kot na strani Lestvica. */
const MLADINSKI_PASOVI: StarostniPas[] = ['U11', 'U13', 'U15', 'U17', 'U19', 'U21']

/* Mesto v igralčevem mladinskem pasu: med igralci istega spola, ki niso
   rekreativci in so v tem ali mlajšem pasu (U19 zajame U11 … U19 - isto kot
   kategorija na strani Lestvica). Mesto je število boljših plus ena, kot v
   glavi profila. Člani, veterani, rekreativci in igralci brez letnice pasu
   nimajo. */
function mestoVPasu(
  lestvica: LestvicaIgralcaDto[] | undefined,
  idIgralec: number,
): { pas: StarostniPas; mesto: number; skupaj: number } | null {
  const jaz = lestvica?.find((v) => v.idIgralca === idIgralec)
  if (!lestvica || !jaz || jaz.spol === null || jaz.rekreativec || jaz.rating === null) return null
  const meja = jaz.starostniPas ? MLADINSKI_PASOVI.indexOf(jaz.starostniPas) : -1
  if (meja === -1 || !jaz.starostniPas) return null
  const vPasu = lestvica.filter((v) => {
    const indeks = v.starostniPas ? MLADINSKI_PASOVI.indexOf(v.starostniPas) : -1
    return v.spol === jaz.spol && !v.rekreativec && indeks !== -1 && indeks <= meja
  })
  const boljsih = vPasu.filter((v) => v.rating !== null && v.rating > jaz.rating!).length
  return { pas: jaz.starostniPas, mesto: boljsih + 1, skupaj: vPasu.length }
}

/* Ime lestvice, na kateri je mesto - z istimi besedami kot izbira na strani
   Lestvica (spol · Člani oz. Rekreativci), da ju gledalec poveže. */
function imeLestvice(u: ProfilDto['uvrstitev']): string {
  const spol = u.spol === 'ZENSKI' ? 'Ženske' : u.spol === 'MOSKI' ? 'Moški' : null
  const skupina = u.rekreativec ? 'Rekreativci' : 'Člani'
  return ['Lestvica', [spol, skupina].filter(Boolean).join(' · ')].join(' ')
}
