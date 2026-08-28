/* Podroben pogled lige, smer »Zapisnik«: dokument od zgoraj navzdol.

   Glava strani nosi stanje lige (značka, naslednje kolo, napredek) in dejanji;
   pravila so se z nje umaknila v modalno okno, ker so referenca in ne to, kar
   gledalec ob prihodu išče. Sledijo piramida sezone (kam liga vodi in od kod
   nanjo napredujejo), lestvica s formo in razširljivim kadrom ter razpored, po
   katerem se lista po kolih namesto izpisa vseh kol naenkrat.

   Postavitev mora zdržati lige različnih velikosti (4 do 16+ ekip), povezane in
   samostojne lige ter ligo v pripravi (takrat lestvice in razporeda še ni in se
   sekciji ne izrišeta prazni). */
import { Fragment, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, ligeApi } from '../api/zahteve'
import type { EkipaDto, LestvicaEkipeDto, LigaDto, SrecanjeDto } from '../api/tipi'
import { OZNAKE_FORMAT, OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  GlavaDejanja,
  GlavaNaslov,
  GlavaZavihki,
  useNazaj,
} from '../komponente/GlavaTelefona'
import { GumbSpremljanja } from '../komponente/GumbSpremljanja'
import { LestviceLige } from '../komponente/LestviceLige'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PrehodiOkno } from '../komponente/PrehodiOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { TerminiOkno } from '../komponente/TerminiOkno'
import { ZnackaStatusa, ZnackaVNaslovu } from '../komponente/Znacka'
import { oblikujDanMesec, oblikujTermin } from '../pomozno/oblikovanje'
import { zabeleziOgledLige } from '../pomozno/ogledaneLige'
import { useSpremljanjeLig } from '../pomozno/spremljaneLige'
import { intervalOsvezevanja } from '../pomozno/osvezevanje'
import { useTelefon } from '../pomozno/telefon'

/* Faza tekmovanja v ligi. Play-off je v postavitvi predviden kot zavihek, a ga
   podatkovni model lige (še) ne pozna - glej PRIKAZI_PLAYOFF. */
type Faza = 'REDNI' | 'PLAYOFF'

/* Na telefonu tri sekcije ne gredo eno pod drugo brez neskončnega drsenja, zato
   se lestvica in razpored menjata z zavihki. Preklop je CSS (v širokem pogledu
   sta obe sekciji vidni), stanje pa vseeno živi tu, ker si ga zavihka delita. */
type MobilniPogled = 'LESTVICA' | 'RAZPORED'

/* Play-off zahteva fazo lige v podatkovnem modelu (pari, termini, kdo se uvrsti).
   Dokler je ni, zavihka ne ponujamo - postavitev spodaj je pripravljena, da se
   ob dodani fazi prižge, ne da bi se stran prepisala. */
const PRIKAZI_PLAYOFF = false

export function LigaStran() {
  const { id } = useParams()
  const idLiga = Number(id)
  const { smemUrejati } = useAvtentikacija()
  const jeTelefon = useTelefon()
  /* Na telefonu glava strani ni nosila konteksta (samo logotip), ker stran
     kavlja ni klicala - gledalec ni imel poti nazaj na seznam lig. Kontekst
     uporabnika ob puščici ostane: liga glavnega dejanja v glavi nima. */
  useNazaj('/lige', 'Lige', true)

  const liga = useQuery({ queryKey: ['liga', idLiga], queryFn: () => ligeApi.najdi(idLiga) })
  /* Dokler liga teče, se razpored osvežuje sam - rezultati srečanj prihajajo
     med večerom. Zaključena liga se ne spreminja. */
  const srecanja = useQuery({
    queryKey: ['srecanja', idLiga],
    queryFn: () => ligeApi.srecanja(idLiga),
    refetchInterval: intervalOsvezevanja(liga.data?.status),
  })
  /* Piramido sestavimo iz povezav "višja liga" v seznamu lig; seznam je isti kot
     na strani /lige, zato je pogosto že v predpomnilniku. */
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })

  const [faza, nastaviFazo] = useState<Faza>('REDNI')
  /* null = kola še ni izbral človek; takrat velja privzetek (prvo neodigrano). */
  const [rocnoKolo, nastaviKolo] = useState<number | null>(null)
  const [odprtaEkipa, nastaviOdprtoEkipo] = useState<number | null>(null)
  const [pravilaOdprta, nastaviPravilaOdprta] = useState(false)
  const [ekipeOdprte, nastaviEkipeOdprte] = useState(false)
  const [obrazecOdprt, nastaviObrazecOdprt] = useState(false)
  const [prehodiOdprti, nastaviPrehodiOdprte] = useState(false)
  const [terminiOdprti, nastaviTerminiOdprte] = useState(false)
  const [mobilniPogled, nastaviMobilniPogled] = useState<MobilniPogled>('LESTVICA')

  /* Ligo se spremlja tam, kjer se jo najde — sicer bi moral gledalec izbor
     sestavljati po spominu v oknu na domači strani. Gost izbora nima, zato
     preklopa ne vidi (njegov brskalnik si to ligo tako ali tako zapomni). */
  const { jePrijavljen, spremljam, preklopi } = useSpremljanjeLig()

  const odjemalec = useQueryClient()

  /* Gost izbora spremljanih lig nima (ta je last računa), zato mu sklop "Moje
     lige" na domači strani pokaže lige, ki si jih je nazadnje ogledal. */
  useEffect(() => {
    if (Number.isInteger(idLiga) && idLiga > 0) zabeleziOgledLige(idLiga)
  }, [idLiga])

  if (liga.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (liga.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (liga.error) return <NapakaPoizvedbe poizvedba={liga} kaj="lige" />
  if (!liga.data) return <p className="obvestilo">Te lige ni (več).</p>

  const l = liga.data
  // organizator sme urejati svojo (ali klubsko) ligo, admin vse
  const smem = smemUrejati(l.idLastnik, l.idKlubLastnik)
  const vPripravi = l.status === 'PRIPRAVA'
  const vsa = srecanja.data ?? []
  const imaRazpored = vsa.length > 0

  /* Napredek lige: koliko kol je do konca odigranih. Kolo šteje za odigrano,
     ko je končano vsako njegovo srečanje. */
  const kola = [...new Set(vsa.map((s) => s.kolo))].sort((a, b) => a - b)
  const koloOdigrano = (k: number) =>
    vsa.filter((s) => s.kolo === k).every((s) => s.status === 'KONCANO')
  const odigranihKol = kola.filter(koloOdigrano).length
  const naslednjeKolo = kola.find((k) => !koloOdigrano(k)) ?? null

  /* Ob prihodu na stran je izbrano prvo neodigrano kolo (to gledalec išče), sicer
     zadnje odigrano. Ročno izbiro spustimo, če je razpored medtem prišel drugačen. */
  const privzetoKolo = naslednjeKolo ?? kola[kola.length - 1] ?? 1
  const kolo = rocnoKolo != null && kola.includes(rocnoKolo) ? rocnoKolo : privzetoKolo

  const uvod = [
    `${l.steviloEkip} ${ekipTekst(l.steviloEkip)}`,
    l.dvokrozno ? 'dvokrožno' : 'enokrožno',
    imaRazpored ? `${odigranihKol}. od ${kola.length} kol odigranih` : 'razpored ni generiran',
  ].join(' · ')

  const nivojiPiramide = lige.data ? piramidaSezone(l, lige.data) : []
  /* Samostojna liga (nima višje in nobena ne kaže nanjo) piramide ne dobi -
     ena sama vrstica z eno ligo ne pove nič. */
  const kaziPiramido = nivojiPiramide.length > 1 || (nivojiPiramide[0]?.lige.length ?? 0) > 1

  const razsiriKader = (idEkipa: number) =>
    nastaviOdprtoEkipo(odprtaEkipa === idEkipa ? null : idEkipa)

  /* Okna so ista na obeh širinah - razlikuje se le, od kod se odprejo
     (na telefonu iz zavihka »Pravila« oz. menija »⋯«). */
  const okna = (
    <>
      {pravilaOdprta && (
        <PravilaOkno
          liga={l}
          lahkoUreja={vPripravi && smem}
          smemUrejatiPrehode={smem}
          nizje={nizjeLige(l, lige.data ?? [])}
          onUredi={() => {
            nastaviPravilaOdprta(false)
            nastaviObrazecOdprt(true)
          }}
          onUrediPrehode={() => {
            nastaviPravilaOdprta(false)
            nastaviPrehodiOdprte(true)
          }}
          onZapri={() => nastaviPravilaOdprta(false)}
        />
      )}

      {/* Prehodi niso pravilo tekmovanja, ampak opis sezone - zato jih sme
          lastnik urejati tudi po žrebu, ko so pravila že zaklenjena. */}
      {prehodiOdprti && (
        <PrehodiOkno
          liga={l}
          vse={lige.data ?? []}
          onZapri={() => nastaviPrehodiOdprte(false)}
          onShranjeno={() => {
            odjemalec.invalidateQueries({ queryKey: ['liga', idLiga] })
            /* Spremenile so se lahko tudi nižje lige, zato cel seznam. */
            odjemalec.invalidateQueries({ queryKey: ['lige'] })
          }}
        />
      )}

      {ekipeOdprte && (
        <EkipeKaderOkno idLiga={idLiga} onZapri={() => nastaviEkipeOdprte(false)} />
      )}

      {/* Termini prav tako niso pravilo tekmovanja: kolo se prestavi tudi
          sredi sezone, ko so pravila že zaklenjena. */}
      {terminiOdprti && (
        <TerminiOkno
          liga={l}
          srecanja={vsa}
          onZapri={() => nastaviTerminiOdprte(false)}
          onShranjeno={() => {
            odjemalec.invalidateQueries({ queryKey: ['srecanja', idLiga] })
            /* Glava strani nosi »naslednje kolo <datum>«, domača stran pa
               povzetek lige - oba berta iste termine. */
            odjemalec.invalidateQueries({ queryKey: ['liga', idLiga] })
            odjemalec.invalidateQueries({ queryKey: ['domov-lige'] })
          }}
        />
      )}

      {obrazecOdprt && (
        <LigaObrazecOkno
          liga={l}
          onZapri={() => nastaviObrazecOdprt(false)}
          onShranjeno={() => {
            odjemalec.invalidateQueries({ queryKey: ['liga', idLiga] })
            odjemalec.invalidateQueries({ queryKey: ['lige'] })
          }}
        />
      )}
    </>
  )

  if (jeTelefon) {
    const delez = imaRazpored ? Math.round((odigranihKol / kola.length) * 100) : 0
    /* »Liga · 2025/26 · moški« - sezona je prosto besedilo, zato je ne
       opremljamo s predpono. */
    const nadnaslov = [
      'Liga',
      l.sezona,
      OZNAKE_SPOL_KATEGORIJA[l.spolKategorija].toLowerCase(),
    ]
      .filter(Boolean)
      .join(' · ')

    return (
      <section className={'liga' + (imaRazpored ? ' stran-mobi--zavihki' : '')}>
        {/* Glavnega dejanja liga nima, zato gre vse urejevalsko pod »⋯«:
            gumba »Pravila« in »Ekipe in kader« sta se s tem umaknila izpod
            naslova, kjer sta lestvico potiskala pod rob zaslona. Preklop
            spremljanja pa ni urejanje (sme ga vsak prijavljen) in je premalo
            globoko za meni — stoji ob njem. */}
        <GlavaDejanja>
          {jePrijavljen && (
            <GumbSpremljanja
              ime={l.ime}
              slog="glava"
              spremljam={spremljam(idLiga)}
              naPreklop={() => preklopi(idLiga)}
            />
          )}
          {smem && (
            <MeniDejanj naslov="Dejanja lige">
              {(zapri) => (
                <>
                  <button
                    type="button"
                    role="menuitem"
                    className="uporabnik-meni__postavka"
                    disabled={vPripravi}
                    onClick={() => {
                      zapri()
                      nastaviEkipeOdprte(true)
                    }}
                  >
                    Ekipe in kader
                    {vPripravi && (
                      <span className="uporabnik-meni__pojasnilo">
                        V pripravi jih ureja sekcija na strani
                      </span>
                    )}
                  </button>
                  {/* Termini se za razliko od pravil ne zaklenejo - kolo se
                      prestavi tudi sredi sezone - a seznama kol pred žrebom
                      ni; do takrat jih nosi obrazec lige (prvo kolo + razmik). */}
                  <button
                    type="button"
                    role="menuitem"
                    className="uporabnik-meni__postavka"
                    disabled={!imaRazpored}
                    onClick={() => {
                      zapri()
                      nastaviTerminiOdprte(true)
                    }}
                  >
                    Termini kol
                    {!imaRazpored && (
                      <span className="uporabnik-meni__pojasnilo">
                        Na voljo, ko je razpored generiran
                      </span>
                    )}
                  </button>
                  <button
                    type="button"
                    role="menuitem"
                    className="uporabnik-meni__postavka"
                    disabled={!vPripravi}
                    onClick={() => {
                      zapri()
                      nastaviObrazecOdprt(true)
                    }}
                  >
                    Uredi pravila
                    {!vPripravi && (
                      <span className="uporabnik-meni__pojasnilo">
                        Zaklenjeno, ker je razpored generiran
                      </span>
                    )}
                  </button>
                </>
              )}
            </MeniDejanj>
          )}
        </GlavaDejanja>

        {/* Ime, stanje in napredek ostanejo na zaslonu med drsenjem po
            lestvici - pri 390 px je sicer po nekaj potegih vseeno, katero
            ligo gledaš. */}
        <GlavaNaslov>
          <div className="glava-telefon__naslov">
            <span className="naslov-mobi__nad">{nadnaslov}</span>
            <div className="naslov-mobi__vrsta naslov-mobi__vrsta--odmik">
              <h1 className="naslov-mobi naslov-mobi--ena-vrsta">{l.ime}</h1>
              <ZnackaVNaslovu status={l.status} />
            </div>
            <div className="liga-mobi__stanje">
              <span>{stanjeMobi(l, vsa, kola, odigranihKol, naslednjeKolo)}</span>
              {imaRazpored && <span className="liga-mobi__delez">{delez} %</span>}
            </div>
            {imaRazpored && (
              <span className="palica palica--tanka">
                <span className="palica__polnilo" style={{ width: `${delez}%` }} />
              </span>
            )}
          </div>
        </GlavaNaslov>

        {imaRazpored && (
          <GlavaZavihki>
            <div className="podnavigacija podnavigacija--telefon podnavigacija--enakomerna">
              <button
                type="button"
                className={
                  'izbirnik__gumb' +
                  (mobilniPogled === 'LESTVICA' ? ' izbirnik__gumb--aktiven' : '')
                }
                aria-pressed={mobilniPogled === 'LESTVICA'}
                onClick={() => nastaviMobilniPogled('LESTVICA')}
              >
                Lestvica
              </button>
              <button
                type="button"
                className={
                  'izbirnik__gumb' +
                  (mobilniPogled === 'RAZPORED' ? ' izbirnik__gumb--aktiven' : '')
                }
                aria-pressed={mobilniPogled === 'RAZPORED'}
                onClick={() => nastaviMobilniPogled('RAZPORED')}
              >
                Razpored
              </button>
              {/* Pravila niso pogled, ampak referenca - zato okno in ne
                  zavihek z vsebino. */}
              <button
                type="button"
                className="izbirnik__gumb"
                onClick={() => nastaviPravilaOdprta(true)}
              >
                Pravila
              </button>
            </div>
          </GlavaZavihki>
        )}

        {vPripravi && smem && (
          <EkipeUredi
            idLiga={idLiga}
            steviloEkip={l.steviloEkip}
            onUrediPravila={() => nastaviObrazecOdprt(true)}
          />
        )}

        {vPripravi && !smem && (
          <p className="obvestilo">
            Liga je v pripravi. Ekipe in kader ureja organizator, razpored pride po žrebu.
          </p>
        )}

        {imaRazpored && mobilniPogled === 'LESTVICA' && (
          <>
            <LestvicaMobi
              idLiga={idLiga}
              liga={l}
              srecanja={vsa}
              nivoji={nivojiPiramide}
              odprtaEkipa={odprtaEkipa}
              onPreklopiKader={razsiriKader}
            />
            {/* Piramida je kontekst in ne stanje tekmovanja, zato na telefonu
                stoji na koncu zavihka in ne nad lestvico. */}
            {kaziPiramido && <Piramida liga={l} nivoji={nivojiPiramide} />}
            {/* Osebni izkupički so drugo branje iste lige - zato zaprta sklopa
                na dnu zavihka z lestvico in ne svoj zavihek. */}
            <LestviceLige idLiga={idLiga} jeTelefon />
          </>
        )}

        {imaRazpored && mobilniPogled === 'RAZPORED' && (
          <RazporedMobi
            srecanja={vsa}
            kola={kola}
            kolo={kolo}
            koloOdigrano={koloOdigrano}
            onKolo={nastaviKolo}
          />
        )}

        {okna}
      </section>
    )
  }

  return (
    <section className="liga">
      <div>
        <Link to="/lige" className="povezava-nazaj">← Lige</Link>

        <div className="stran-glava stran-glava--dno">
          <div>
            <h1 className="naslov-strani naslov-strani--podstran">
              {/* Sezona je prosto besedilo (»2025/26«, »8. sezona«), zato je ne
                  opremljamo s predpono - v nadnaslovu stoji taka, kot je vpisana. */}
              <span className="naslov-strani__nad">{l.sezona ?? 'Liga'}</span>
              <span className="naslov-strani__glavni">{l.ime}</span>
            </h1>
            <p className="uvod uvod--tesno">{uvod}</p>
          </div>

          <div className="liga__stanje-blok">
            <div className="naslovna-vrstica__desno">
              <ZnackaStatusa status={l.status} />
              <span className="sekcija__meta">
                {stanjeLige(l, vsa, kola, naslednjeKolo, kaziPiramido)}
              </span>
            </div>

            {imaRazpored && (
              <div>
                <div className="liga__napredek">
                  <span className="kolofon__oznaka">Odigrano</span>
                  <span className="kolofon__vrednost">
                    {odigranihKol} / {kola.length}
                  </span>
                </div>
                <span className="palica">
                  <span
                    className="palica__polnilo"
                    style={{ width: `${Math.round((odigranihKol / kola.length) * 100)}%` }}
                  />
                </span>
              </div>
            )}

            <div className="stran-glava__dejanja stran-glava__dejanja--vrsta">
              {/* Spremljanje ni urejanje: sme ga vsak prijavljen, zato stoji
                  pred urejevalskimi gumbi. Označena liga je nato na domači
                  strani. */}
              {jePrijavljen && (
                <GumbSpremljanja
                  ime={l.ime}
                  spremljam={spremljam(idLiga)}
                  naPreklop={() => preklopi(idLiga)}
                />
              )}
              <button type="button" className="gumb" onClick={() => nastaviPravilaOdprta(true)}>
                Pravila
              </button>
              {/* V pripravi ekipe in kader ureja sekcija na strani, zato okna ne
                  ponujamo dvakrat. */}
              {smem && !vPripravi && (
                <button
                  type="button"
                  className="gumb gumb--majhen"
                  onClick={() => nastaviEkipeOdprte(true)}
                >
                  Ekipe in kader
                </button>
              )}
              {/* Seznam kol obstaja šele po žrebu; pred njim termine nosi
                  obrazec lige (prvo kolo + razmik). */}
              {smem && imaRazpored && (
                <button
                  type="button"
                  className="gumb gumb--majhen"
                  onClick={() => nastaviTerminiOdprte(true)}
                >
                  Termini
                </button>
              )}
            </div>
          </div>
        </div>

        {imaRazpored && (
          <div className="izbirnik liga__zavihki">
            <button
              type="button"
              className={
                'izbirnik__gumb' + (mobilniPogled === 'LESTVICA' ? ' izbirnik__gumb--aktiven' : '')
              }
              onClick={() => nastaviMobilniPogled('LESTVICA')}
            >
              Lestvica
            </button>
            <button
              type="button"
              className={
                'izbirnik__gumb' + (mobilniPogled === 'RAZPORED' ? ' izbirnik__gumb--aktiven' : '')
              }
              onClick={() => nastaviMobilniPogled('RAZPORED')}
            >
              Razpored
            </button>
            <button
              type="button"
              className="izbirnik__gumb"
              onClick={() => nastaviPravilaOdprta(true)}
            >
              Pravila
            </button>
          </div>
        )}
      </div>

      {kaziPiramido && <Piramida liga={l} nivoji={nivojiPiramide} />}

      {vPripravi && smem && (
        <EkipeUredi
          idLiga={idLiga}
          steviloEkip={l.steviloEkip}
          onUrediPravila={() => nastaviObrazecOdprt(true)}
        />
      )}

      {vPripravi && !smem && (
        <p className="obvestilo">
          Liga je v pripravi. Ekipe in kader ureja organizator, razpored pride po žrebu
          (urejate lahko le lige svojega kluba oz. kot administrator).
        </p>
      )}

      {imaRazpored && (
        <>
          <div
            className={
              'liga__sekcija' + (mobilniPogled === 'LESTVICA' ? '' : ' liga__sekcija--skrita')
            }
          >
            <div className="naslovna-vrstica">
              <h2>{faza === 'REDNI' ? 'Lestvica' : 'Play-off'}</h2>
              {PRIKAZI_PLAYOFF && (
                <div className="izbirnik">
                  <button
                    type="button"
                    className={
                      'izbirnik__gumb' + (faza === 'REDNI' ? ' izbirnik__gumb--aktiven' : '')
                    }
                    onClick={() => nastaviFazo('REDNI')}
                  >
                    Redni del
                  </button>
                  <button
                    type="button"
                    className={
                      'izbirnik__gumb' + (faza === 'PLAYOFF' ? ' izbirnik__gumb--aktiven' : '')
                    }
                    onClick={() => nastaviFazo('PLAYOFF')}
                  >
                    Play-off
                  </button>
                </div>
              )}
            </div>

            {faza === 'REDNI' ? (
              <Lestvica
                idLiga={idLiga}
                liga={l}
                srecanja={vsa}
                nivoji={nivojiPiramide}
                odprtaEkipa={odprtaEkipa}
                onPreklopiKader={razsiriKader}
              />
            ) : (
              <PlayOff idLiga={idLiga} />
            )}
          </div>

          <div
            className={
              'liga__sekcija' + (mobilniPogled === 'RAZPORED' ? '' : ' liga__sekcija--skrita')
            }
          >
            <Razpored
              srecanja={vsa}
              kola={kola}
              kolo={kolo}
              odigranihKol={odigranihKol}
              koloOdigrano={koloOdigrano}
              onKolo={nastaviKolo}
            />
          </div>

          {/* Zaprta sklopa na dnu strani: lestvica lige so ekipe, osebni
              izkupički pa drugo branje - zato pod njo in ne v zavihku. */}
          <LestviceLige idLiga={idLiga} jeTelefon={false} />
        </>
      )}

      {okna}
    </section>
  )
}

/* ---------- Stanje lige v glavi strani ---------- */

/* Ena mono vrstica ob znački: kdaj je naslednje kolo, kdaj se je liga končala
   oz. da razporeda še ni. Samostojna liga brez piramide to tudi pove - drugače
   bi gledalec sklepal, da podatek manjka. */
function stanjeLige(
  liga: LigaDto,
  srecanja: SrecanjeDto[],
  kola: number[],
  naslednjeKolo: number | null,
  imaPiramido: boolean,
): string {
  if (srecanja.length === 0) {
    return imaPiramido ? 'Razpored ni generiran' : 'Samostojna — brez piramide'
  }
  if (liga.status === 'ZAKLJUCEN' || naslednjeKolo == null) {
    const datum = datumKola(srecanja, kola[kola.length - 1])
    return datum ? `Končano ${oblikujDanMesec(datum)}` : 'Vsa kola odigrana'
  }
  const datum = datumKola(srecanja, naslednjeKolo)
  return datum ? `Naslednje kolo ${oblikujDanMesec(datum)}` : `Naslednje ${naslednjeKolo}. kolo`
}

/* Ista vrsta na telefonu, a z napredkom spredaj: v lepljivi glavi je ena sama
   mono vrstica, zato mora nositi oboje - koliko kol je za nami in kdaj je
   naslednje. Uvod (»10 ekip · dvokrožno«) na telefonu odpade: ekipe prešteje
   lestvica pod njim, sistem pa je v pravilih. */
function stanjeMobi(
  liga: LigaDto,
  srecanja: SrecanjeDto[],
  kola: number[],
  odigranihKol: number,
  naslednjeKolo: number | null,
): string {
  if (srecanja.length === 0) return 'Razpored ni generiran'
  if (liga.status === 'ZAKLJUCEN' || naslednjeKolo == null) {
    const datum = datumKola(srecanja, kola[kola.length - 1])
    return datum ? `Končano ${oblikujDanMesec(datum)}` : 'Vsa kola odigrana'
  }
  const potek = `${odigranihKol}. od ${kola.length} kol`
  const datum = datumKola(srecanja, naslednjeKolo)
  return datum ? `${potek} · naslednje ${oblikujDanMesec(datum)}` : potek
}

/* Termin kola vzamemo iz prvega srečanja, ki ga ima: kolo se odigra en dan,
   zato vsa njegova srečanja nosijo isti čas (piše ga zaledje iz semena lige
   oz. ročnega popravka v TerminiOkno). */
function terminKola(srecanja: SrecanjeDto[], kolo: number): string | null {
  return srecanja.find((x) => x.kolo === kolo && x.predvidenZacetek)?.predvidenZacetek ?? null
}

function datumKola(srecanja: SrecanjeDto[], kolo: number): string | null {
  return terminKola(srecanja, kolo)?.slice(0, 10) ?? null
}

/* Kaj piše ob številki kola v razporedu. Kolo, ki šele pride, nosi termin —
   »kdaj se to igra« je edino, kar gledalec ob neodigranem kolu išče; beseda
   »razpored« ni povedala nič. Ostane samo, kadar termina ni (organizator ga
   ni vpisal). Odigrano kolo obdrži oznako, datum pa mu je kontekst. */
function metaKola(srecanja: SrecanjeDto[], kolo: number, odigrano: boolean): string {
  const termin = oblikujTermin(terminKola(srecanja, kolo))
  if (odigrano) return [termin, 'odigrano'].filter(Boolean).join(' · ')
  return termin || 'razpored'
}

/* ---------- Piramida sezone ---------- */

interface PiramidaNivo {
  nivo: number
  lige: LigaDto[]
  napreduje: number
  izpade: number
}

/* Lige, ki so z izbrano povezane prek »višje lige« — navzgor do vrha in navzdol
   po vseh vejah, torej tudi sosednje skupine istega nivoja.

   Merilo je izključno vpisana povezava, sezona ne filtrira. Prej je morala biti
   sezona enaka (da se ne bi zlile piramide več sezon), a povezava je trd kazalec
   na eno samo ligo — vpisana je bila namenoma. Filter je zato tiho razdrl
   piramide, kjer je bila sezona zapisana drugače (»8. Sezona« proti »25/26«), in
   človek ni imel kje videti, zakaj. Lige druge sezone so v izrisu označene. */
function piramidaSezone(liga: LigaDto, vse: LigaDto[]): PiramidaNivo[] {
  const poId = new Map(vse.map((k) => [k.id, k]))
  const nizje = new Map<number, LigaDto[]>()
  for (const k of vse) {
    if (k.idVisjaLiga != null && poId.has(k.idVisjaLiga)) {
      nizje.set(k.idVisjaLiga, [...(nizje.get(k.idVisjaLiga) ?? []), k])
    }
  }

  const najdene = new Map<number, LigaDto>()
  const vrsta: LigaDto[] = [poId.get(liga.id) ?? liga]
  while (vrsta.length > 0) {
    const t = vrsta.pop() as LigaDto
    if (najdene.has(t.id)) continue
    najdene.set(t.id, t)
    const visja = t.idVisjaLiga != null ? poId.get(t.idVisjaLiga) : undefined
    if (visja) vrsta.push(visja)
    for (const n of nizje.get(t.id) ?? []) vrsta.push(n)
  }

  const poNivojih = new Map<number, LigaDto[]>()
  for (const k of najdene.values()) {
    const n = nivoLige(k, poId)
    poNivojih.set(n, [...(poNivojih.get(n) ?? []), k])
  }

  return [...poNivojih.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([nivo, seznam]) => ({
      nivo,
      lige: [...seznam].sort((a, b) => a.ime.localeCompare(b.ime, 'sl')),
      /* Tok nivoja povzame ligo z največ napredovanji oz. izpadi: skupine istega
         nivoja imajo pravila enaka, razhajanje pa naj bo raje prikazano kot
         zamolčano. */
      napreduje: Math.max(...seznam.map((k) => k.stNapreduje)),
      izpade: Math.max(...seznam.map((k) => k.stIzpade)),
    }))
}

/* Globina lige v piramidi: 1 je vrh (nima višje lige). Obiske beležimo, da
   morebiten cikel v podatkih ne zavrti zanke. */
function nivoLige(liga: LigaDto, poId: Map<number, LigaDto>): number {
  let globina = 1
  let t = liga
  const videne = new Set<number>([liga.id])
  while (t.idVisjaLiga != null) {
    const visja = poId.get(t.idVisjaLiga)
    if (!visja || videne.has(visja.id)) break
    videne.add(visja.id)
    t = visja
    globina++
  }
  return globina
}

function Piramida({ liga, nivoji }: { liga: LigaDto; nivoji: PiramidaNivo[] }) {
  /* Piramida naj bi bila ena sezona. Če povezave vežejo lige različnih sezon,
     tega ne skrijemo za sezono izbrane lige — v glavi piše, da jih je več,
     posamezne pa nosijo svojo. */
  const sezone = new Set(nivoji.flatMap((n) => n.lige).map((k) => k.sezona ?? ''))
  const meta = [
    OZNAKE_SPOL_KATEGORIJA[liga.spolKategorija],
    sezone.size > 1 ? 'več sezon' : liga.sezona,
  ]
    .filter(Boolean)
    .join(' · ')
  const razlicneSezone = sezone.size > 1

  return (
    <div className="liga__piramida">
      <div className="liga__piramida-glava">
        <span className="podnaslov-sekcije liga__podnaslov--vrstica">Piramida sezone</span>
        <span className="sekcija__meta">{meta}</span>
      </div>
      {nivoji.map((n) => {
        const tok = tokNivoja(n)
        return (
          <div key={n.nivo} className="liga__piramida-vrstica">
            <span className="liga__piramida-nivo">{n.nivo}. nivo</span>
            <span className="liga__piramida-lige">
              {n.lige.map((k) =>
                k.id === liga.id ? (
                  <span
                    key={k.id}
                    className="liga__piramida-liga liga__piramida-liga--tukaj"
                    aria-current="page"
                  >
                    {imeVPiramidi(k, razlicneSezone)}
                  </span>
                ) : (
                  <Link key={k.id} to={`/lige/${k.id}`} className="liga__piramida-liga">
                    {imeVPiramidi(k, razlicneSezone)}
                  </Link>
                ),
              )}
            </span>
            <span className={`liga__piramida-tok${tok.razred}`}>{tok.besedilo}</span>
          </div>
        )
      })}
    </div>
  )
}

/* Ime lige v piramidi. Sezono pripiše samo, kadar se v piramidi mešajo — sicer
   bi jo vsaka vrstica ponavljala, čeprav stoji že v glavi sklopa. */
function imeVPiramidi(liga: LigaDto, razlicneSezone: boolean): string {
  if (!razlicneSezone) return liga.ime
  return `${liga.ime} · ${liga.sezona || 'brez sezone'}`
}

/* Lige, ki kažejo na dano kot na svojo višjo — torej nivo pod njo. */
function nizjeLige(liga: LigaDto, vse: LigaDto[]): LigaDto[] {
  return vse
    .filter((k) => k.idVisjaLiga === liga.id)
    .sort((a, b) => a.ime.localeCompare(b.ime, 'sl'))
}

/* Opis toka nivoja: vrh samo izpade, dno samo napreduje, vmesni oboje. */
function tokNivoja(n: PiramidaNivo): { besedilo: string; razred: string } {
  if (n.napreduje > 0 && n.izpade > 0) {
    return { besedilo: `↑ ${n.napreduje} · ↓ ${n.izpade}`, razred: '' }
  }
  if (n.izpade > 0) {
    return {
      besedilo: `↓ ${n.izpade} ${izpadeTekst(n.izpade)}`,
      razred: ' liga__piramida-tok--izpad',
    }
  }
  if (n.napreduje > 0) {
    return {
      besedilo: `↑ ${n.napreduje} ${napredujeTekst(n.napreduje)}`,
      razred: ' liga__piramida-tok--napredek',
    }
  }
  return { besedilo: '', razred: '' }
}

/* ---------- Lestvica ---------- */

interface LestvicaLastnosti {
  idLiga: number
  liga: LigaDto
  srecanja: SrecanjeDto[]
  nivoji: PiramidaNivo[]
  odprtaEkipa: number | null
  onPreklopiKader: (idEkipa: number) => void
}

function Lestvica({
  idLiga,
  liga,
  srecanja,
  nivoji,
  odprtaEkipa,
  onPreklopiKader,
}: LestvicaLastnosti) {
  const lestvica = useQuery({
    queryKey: ['lestvica', idLiga],
    queryFn: () => ligeApi.lestvica(idLiga),
  })
  if (lestvica.isPending) return <p className="obvestilo">Nalaganje lestvice …</p>
  if (lestvica.error) return <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice" />
  if (!lestvica.data || lestvica.data.length === 0) return null

  const cilji = ciljneLige(liga, nivoji)

  return (
    <div>
      <div className="tabela-ovoj">
        <table className="tabela liga__lestvica">
          <caption className="samo-za-bralnik">
            Lestvica lige: ekipe po osvojenih točkah. Klik na vrstico odpre kader ekipe.
          </caption>
          <thead>
            <tr>
              <th scope="col" className="lestvica__mesto">#</th>
              <th scope="col">Ekipa</th>
              <th scope="col" className="lestvica__stevilka lestvica__odigrane">Odig.</th>
              <th scope="col" className="lestvica__stevilka lestvica__izkupicek">Z</th>
              <th scope="col" className="lestvica__stevilka lestvica__izkupicek">N</th>
              <th scope="col" className="lestvica__stevilka lestvica__izkupicek">P</th>
              <th scope="col" className="lestvica__stevilka lestvica__tekme">Tekme</th>
              <th scope="col" className="lestvica__stevilka lestvica__nizi">Nizi</th>
              <th scope="col" className="lestvica__forma-glava">Zadnjih 5</th>
              <th scope="col" className="lestvica__rating">Točke</th>
            </tr>
          </thead>
          <tbody>
            {lestvica.data.map((v) => {
              const odprta = odprtaEkipa === v.idEkipa
              return (
                <Fragment key={v.idEkipa}>
                  <tr
                    className={vrsticaRazred(v, odprta)}
                    onClick={() => onPreklopiKader(v.idEkipa)}
                  >
                    <td className="lestvica__mesto">{v.mesto}</td>
                    <td>
                      {/* Vrstica je klikljiva zaradi hitrosti, tipkovnica in
                          bralnik zaslona pa potrebujeta pravi gumb; klik nanj
                          zato ne sme še enkrat potovati do vrstice. */}
                      <button
                        type="button"
                        className="lestvica__ekipa"
                        aria-expanded={odprta}
                        onClick={(dogodek) => {
                          dogodek.stopPropagation()
                          onPreklopiKader(v.idEkipa)
                        }}
                      >
                        <span className="lestvica__ime">{v.ekipa}</span>
                        <span className="lestvica__kader-oznaka">
                          Kader {odprta ? '▴' : '▾'}
                        </span>
                      </button>
                    </td>
                    <td className="lestvica__stevilka lestvica__odigrane">{v.odigrane}</td>
                    <td className="lestvica__stevilka lestvica__izkupicek lestvica__zmage">
                      {v.zmage}
                    </td>
                    <td className="lestvica__stevilka lestvica__izkupicek">{v.neodlocene}</td>
                    <td className="lestvica__stevilka lestvica__izkupicek lestvica__porazi">
                      {v.porazi}
                    </td>
                    <td className="lestvica__stevilka lestvica__tekme">
                      {v.dobljeneTekme}:{v.prejeteTekme}
                    </td>
                    <td className="lestvica__stevilka lestvica__nizi">
                      {v.dobljeniNizi}:{v.prejetiNizi}
                    </td>
                    <td className="lestvica__forma-celica">
                      <Forma znaki={forma(v.idEkipa, srecanja)} />
                    </td>
                    <td className="lestvica__rating">{v.tocke}</td>
                  </tr>
                  {odprta && (
                    <tr className="lestvica__kader-vrsta">
                      <td colSpan={10}>
                        <Kader idEkipa={v.idEkipa} ekipa={v.ekipa} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>

      {(liga.stNapreduje > 0 || liga.stIzpade > 0) && (
        <div className="legenda">
          {liga.stNapreduje > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--napreduje" />
              {cilji.visja ? `Napreduje v ${cilji.visja}` : 'Napreduje'}
            </span>
          )}
          {liga.stIzpade > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--izpade" />
              {cilji.nizja ? `Izpade v ${cilji.nizja}` : 'Izpade'}
            </span>
          )}
          <span>Klik na vrstico odpre kader</span>
        </div>
      )}
    </div>
  )
}

/* Ista lestvica na telefonu. Namizna tabela ima deset stolpcev (odigrane,
   Z/N/P, tekme, nizi ...) - pri 390 px se prebere le prvih nekaj, ostali pa
   vrstico raztegnejo. Tu ostanejo mesto, ime, bilanca s formo in točke;
   podrobnosti so v zapisniku srečanja.

   Ločenega gumba »Kader ▾« ni: vrstica je gumb in kader razpre pod sabo -
   dve zadetkovni površini v 390 px vrstici sta ena preveč. */
function LestvicaMobi({
  idLiga,
  liga,
  srecanja,
  nivoji,
  odprtaEkipa,
  onPreklopiKader,
}: LestvicaLastnosti) {
  const lestvica = useQuery({
    queryKey: ['lestvica', idLiga],
    queryFn: () => ligeApi.lestvica(idLiga),
  })
  const vrstice = lestvica.data ?? []
  const cilji = ciljneLige(liga, nivoji)

  return (
    <div>
      {/* Zavihki nad vsebino že režejo pas, zato naslovna vrstica brez črte. */}
      <div className="naslovna-mobi naslovna-mobi--brez-crte">
        <h2>Lestvica</h2>
        {vrstice.length > 0 && (
          <span className="naslovna-mobi__stevec naslovna-mobi__stevec--drobno">
            {vrstice.length} {ekipTekst(vrstice.length)} · Zadnjih 5
          </span>
        )}
      </div>

      {lestvica.isPending && <p className="obvestilo">Nalaganje lestvice …</p>}
      <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice" />

      {vrstice.length > 0 && (
        <div className="lestvica-mobi">
          {vrstice.map((v) => {
            const odprta = odprtaEkipa === v.idEkipa
            const cona = v.cona ? v.cona.toLowerCase() : null
            return (
              <Fragment key={v.idEkipa}>
                <button
                  type="button"
                  className={
                    'lestvica-mobi__vrstica lestvica-mobi__vrstica--ekipa' +
                    (cona ? ` lestvica-mobi__vrstica--${cona}` : '') +
                    (odprta ? ' lestvica-mobi__vrstica--odprta' : '')
                  }
                  aria-expanded={odprta}
                  onClick={() => onPreklopiKader(v.idEkipa)}
                >
                  <span
                    className={
                      'lestvica-mobi__mesto lestvica-mobi__mesto--ekipa' +
                      (cona ? ` lestvica-mobi__mesto--${cona}` : '')
                    }
                  >
                    {v.mesto}
                  </span>
                  <span className="lestvica-mobi__ime">{v.ekipa}</span>
                  <span className="lestvica-mobi__izkupicek">
                    <span className="lestvica-mobi__bilanca">
                      {v.zmage}-{v.neodlocene}-{v.porazi}
                    </span>
                    <Forma znaki={forma(v.idEkipa, srecanja)} />
                  </span>
                  <span className="lestvica-mobi__tocke">{v.tocke}</span>
                </button>
                {odprta && <Kader idEkipa={v.idEkipa} ekipa={v.ekipa} />}
              </Fragment>
            )
          })}
        </div>
      )}

      {vrstice.length > 0 && (liga.stNapreduje > 0 || liga.stIzpade > 0) && (
        <div className="legenda legenda--mobi">
          {liga.stNapreduje > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--napreduje" />
              {cilji.visja ? `Napreduje v ${cilji.visja}` : 'Napreduje'}
            </span>
          )}
          {liga.stIzpade > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--izpade" />
              {cilji.nizja ? `Izpade v ${cilji.nizja}` : 'Izpade'}
            </span>
          )}
        </div>
      )}
    </div>
  )
}

function vrsticaRazred(v: LestvicaEkipeDto, odprta: boolean): string {
  return (
    'lestvica__vrsta' +
    (v.cona ? ` lestvica__vrsta--${v.cona.toLowerCase()}` : '') +
    (odprta ? ' lestvica__vrsta--odprta' : '')
  )
}

/* Kam se iz te lige napreduje in kam izpade — ime pride iz piramide, da legenda
   ne govori splošno (»Napreduje«), ampak konkretno (»Napreduje v 1. SNTL«). */
function ciljneLige(
  liga: LigaDto,
  nivoji: PiramidaNivo[],
): { visja: string | null; nizja: string | null } {
  const nizje = nizjeLige(liga, nivoji.flatMap((n) => n.lige)).map((k) => k.ime)
  return {
    visja: liga.visjaLigaIme,
    nizja: nizje.length > 0 ? nizje.join(' / ') : null,
  }
}

type ZnakForme = 'Z' | 'N' | 'P'

const OZNAKE_FORME: Record<ZnakForme, string> = {
  Z: 'zmaga',
  N: 'neodločeno',
  P: 'poraz',
}

/* Forma ekipe: do pet njenih zadnjih končanih srečanj, najstarejše levo. Šteje
   izid srečanja (dobljene tekme), ne posamične tekme. */
function forma(idEkipa: number, srecanja: SrecanjeDto[]): ZnakForme[] {
  return srecanja
    .filter(
      (s) =>
        s.status === 'KONCANO' && (s.idEkipaDomaci === idEkipa || s.idEkipaGost === idEkipa),
    )
    .sort((a, b) => a.kolo - b.kolo)
    .slice(-5)
    .map((s) => {
      const doma = s.idEkipaDomaci === idEkipa
      const svoje = doma ? s.dobljeneDomaci : s.dobljeneGost
      const tuje = doma ? s.dobljeneGost : s.dobljeneDomaci
      if (svoje > tuje) return 'Z'
      if (svoje < tuje) return 'P'
      return 'N'
    })
}

function Forma({ znaki }: { znaki: ZnakForme[] }) {
  if (znaki.length === 0) return null
  return (
    <span className="lestvica__forma">
      {znaki.map((z, i) => (
        <span key={i} className={`lestvica__forma-znak lestvica__forma-znak--${z}`}>
          {/* Na telefonu je značka le kvadratek, zato mora črka ostati dosegljiva
              bralniku zaslona; skrije jo CSS, ne pogojni izris. */}
          <span className="lestvica__forma-crka">{z}</span>
          <span className="samo-za-bralnik">{OZNAKE_FORME[z]}</span>
        </span>
      ))}
    </span>
  )
}

/* Kader se naloži šele, ko vrstico odpreš (poizvedba se ne sproži prej).

   Vrstni red je strežnikov (LigaStoritev.kader): največ zmag za to ekipo v tej
   ligi na vrhu, zato je številka pred imenom mesto po izkupičku in ne
   organizatorjev vrstni red. */
function Kader({ idEkipa, ekipa }: { idEkipa: number; ekipa: string }) {
  const kader = useQuery({ queryKey: ['kader', idEkipa], queryFn: () => ligeApi.kader(idEkipa) })

  return (
    <div className="liga__kader">
      <div className="liga__kader-glava">
        <span className="liga__kader-naslov">Kader — {ekipa}</span>
        <span className="sekcija__meta">Po zmagah v ligi · rating · bilanca</span>
      </div>
      {kader.isPending && <p className="obvestilo">Nalaganje kadra …</p>}
      <NapakaPoizvedbe poizvedba={kader} kaj="kadra" />
      {kader.data && kader.data.length === 0 && <p className="obvestilo">Kader je prazen.</p>}
      {kader.data?.map((k, i) => (
        <div key={k.id} className="liga__kader-vrstica">
          <span className="liga__kader-mesto">{i + 1}.</span>
          <span className="liga__kader-ime">{k.polnoIme}</span>
          <span className="liga__kader-rating">{k.rating ?? '—'}</span>
          <span className="liga__kader-bilanca">
            {k.zmage} : {k.porazi}
          </span>
        </div>
      ))}
    </div>
  )
}

/* ---------- Play-off (predvidena faza) ---------- */

/* Postavitev zavihka je pripravljena za trenutek, ko liga dobi fazo play-offa:
   dve koloni parov, pod vsakim parom trenutni kandidat iz lestvice. Dokler faze
   v podatkovnem modelu ni, se zavihek ne ponudi (PRIKAZI_PLAYOFF). */
function PlayOff({ idLiga }: { idLiga: number }) {
  const lestvica = useQuery({
    queryKey: ['lestvica', idLiga],
    queryFn: () => ligeApi.lestvica(idLiga),
  })
  const vrstice = lestvica.data ?? []
  const ime = (mesto: number) => vrstice.find((v) => v.mesto === mesto)?.ekipa ?? '—'
  const zadnji = vrstice.length
  /* Pod osmimi ekipami se para "za naslov" in "za obstanek" prekrivata; takrat
     play-offa ni smiselno risati. */
  if (zadnji < 8) {
    return <p className="obvestilo">Za play-off je potrebnih vsaj osem ekip.</p>
  }

  const vrh = [
    { faza: 'Polfinale 1', par: '1. — 4.', kandidat: `trenutno ${ime(1)} — ${ime(4)}` },
    { faza: 'Polfinale 2', par: '2. — 3.', kandidat: `trenutno ${ime(2)} — ${ime(3)}` },
    { faza: 'Finale', par: 'zmagovalca polfinalov', kandidat: 'na dve dobljeni srečanji' },
  ]
  const dno = [
    {
      faza: 'Par A',
      par: `${zadnji - 3}. — ${zadnji}.`,
      kandidat: `trenutno ${ime(zadnji - 3)} — ${ime(zadnji)}`,
    },
    {
      faza: 'Par B',
      par: `${zadnji - 2}. — ${zadnji - 1}.`,
      kandidat: `trenutno ${ime(zadnji - 2)} — ${ime(zadnji - 1)}`,
    },
    { faza: 'Izpad', par: 'poraženca izpadeta', kandidat: 'kvalifikacij ni' },
  ]

  return (
    <div>
      <p className="uvod uvod--tesno">
        Play-off se odigra po rednem delu. Pari se določijo iz končne lestvice; dokler
        redni del teče, so mesta prazna in stran pokaže trenutne kandidate.
      </p>
      <div className="liga__playoff">
        <PlayOffStolpec naslov="Za naslov" meta="Mesta 1–4" vrstice={vrh} />
        <PlayOffStolpec
          naslov="Za obstanek"
          meta={`Mesta ${zadnji - 3}–${zadnji}`}
          vrstice={dno}
        />
      </div>
    </div>
  )
}

function PlayOffStolpec({
  naslov,
  meta,
  vrstice,
}: {
  naslov: string
  meta: string
  vrstice: { faza: string; par: string; kandidat: string }[]
}) {
  return (
    <div>
      <h3 className="liga__kolo-naslov">
        {naslov}
        <span className="liga__kolo-datum">{meta}</span>
      </h3>
      {vrstice.map((p) => (
        <div key={p.faza} className="liga__playoff-vrstica">
          <span className="liga__playoff-faza">{p.faza}</span>
          <span>
            <span className="liga__playoff-par">{p.par}</span>
            <span className="liga__playoff-kandidat">{p.kandidat}</span>
          </span>
        </div>
      ))}
    </div>
  )
}

/* ---------- Razpored ---------- */

interface RazporedLastnosti {
  srecanja: SrecanjeDto[]
  kola: number[]
  kolo: number
  odigranihKol: number
  koloOdigrano: (kolo: number) => boolean
  onKolo: (kolo: number) => void
}

function Razpored({
  srecanja,
  kola,
  kolo,
  odigranihKol,
  koloOdigrano,
  onKolo,
}: RazporedLastnosti) {
  const mesto = kola.indexOf(kolo)
  const vKolu = srecanja.filter((s) => s.kolo === kolo)
  const odigrano = koloOdigrano(kolo)
  const meta = metaKola(srecanja, kolo, odigrano)
  /* Termin kola, ki šele pride, je poudarjen - to je vprašanje, s katerim
     gledalec pride na razpored. */
  const poudarjenTermin = !odigrano && terminKola(srecanja, kolo) != null

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razpored</h2>
        <span className="sekcija__meta">
          {kola.length} {kolTekst(kola.length)} · {odigranihKol} odigranih
        </span>
      </div>

      <div className="liga__krmar">
        <button
          type="button"
          className="izbirnik__gumb"
          disabled={mesto <= 0}
          onClick={() => onKolo(kola[mesto - 1])}
        >
          ← Prejšnje
        </button>
        <span className="liga__krmar-sredina">
          <span className="liga__krmar-kolo">{kolo}. kolo</span>
          <span className={'sekcija__meta' + (poudarjenTermin ? ' liga__kolo-termin' : '')}>
            {meta}
          </span>
        </span>
        <button
          type="button"
          className="izbirnik__gumb"
          disabled={mesto < 0 || mesto >= kola.length - 1}
          onClick={() => onKolo(kola[mesto + 1])}
        >
          Naslednje →
        </button>
      </div>

      <ul className="liga__srecanja">
        {vKolu.map((s) => {
          const konec = s.status === 'KONCANO'
          const domZmaga = konec && s.dobljeneDomaci > s.dobljeneGost
          const gostZmaga = konec && s.dobljeneGost > s.dobljeneDomaci
          return (
            <li key={s.id}>
              <Link to={`/srecanja/${s.id}`} className="liga__srecanje">
                <span
                  className={
                    'liga__srecanje-ekipa liga__srecanje-ekipa--desno' +
                    (domZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                    (gostZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                  }
                >
                  {s.domaci}
                </span>
                <span
                  className={'liga__srecanje-izid' + (konec ? '' : ' liga__srecanje-izid--caka')}
                >
                  {konec ? `${s.dobljeneDomaci} : ${s.dobljeneGost}` : 'vs'}
                </span>
                <span
                  className={
                    'liga__srecanje-ekipa' +
                    (gostZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                    (domZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                  }
                >
                  {s.gost}
                </span>
                <span className="liga__srecanje-dejanje">{konec ? 'Zapisnik' : 'Postava'}</span>
              </Link>
            </li>
          )
        })}
      </ul>

      <div className="liga__trak">
        <span className="podnaslov-sekcije">Vsa kola</span>
        <div className="liga__trak-kol">
          {kola.map((k) => (
            <button
              type="button"
              key={k}
              className={
                'liga__trak-gumb' +
                (koloOdigrano(k) ? ' liga__trak-gumb--odigrano' : '') +
                (k === kolo ? ' liga__trak-gumb--izbrano' : '')
              }
              aria-current={k === kolo ? 'true' : undefined}
              /* Gumb je samo številka; termin mora do bralnika zaslona in do
                 miške priti tu, sicer je trak brez pomena. */
              aria-label={`${k}. kolo — ${metaKola(srecanja, k, koloOdigrano(k))}`}
              title={metaKola(srecanja, k, koloOdigrano(k))}
              onClick={() => onKolo(k)}
            >
              {k}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

/* Isti razpored na telefonu. Krmar kola je tri celice (← / kolo z datumom /
   →) namesto treh gumbov z besedilom: »Prejšnje« in »Naslednje« sta pri
   390 px pojedla ves prostor, smer pa nosi že puščica. Naslova »Razpored« ni
   - pove ga zavihek, ki je pripeljal sem. */
function RazporedMobi({
  srecanja,
  kola,
  kolo,
  koloOdigrano,
  onKolo,
}: Omit<RazporedLastnosti, 'odigranihKol'>) {
  const mesto = kola.indexOf(kolo)
  const vKolu = srecanja.filter((s) => s.kolo === kolo)
  const odigrano = koloOdigrano(kolo)
  const meta = metaKola(srecanja, kolo, odigrano)
  const poudarjenTermin = !odigrano && terminKola(srecanja, kolo) != null

  return (
    <>
      <div>
        <div className="liga-mobi__krmar">
          <button
            type="button"
            className="liga-mobi__krmar-gumb"
            aria-label="Prejšnje kolo"
            disabled={mesto <= 0}
            onClick={() => onKolo(kola[mesto - 1])}
          >
            ←
          </button>
          <span className="liga-mobi__krmar-sredina">
            <span className="liga-mobi__kolo">{kolo}. kolo</span>
            <span
              className={
                'liga-mobi__kolo-meta' + (poudarjenTermin ? ' liga__kolo-termin' : '')
              }
            >
              {meta}
            </span>
          </span>
          <button
            type="button"
            className="liga-mobi__krmar-gumb"
            aria-label="Naslednje kolo"
            disabled={mesto < 0 || mesto >= kola.length - 1}
            onClick={() => onKolo(kola[mesto + 1])}
          >
            →
          </button>
        </div>

        <div className="liga-mobi__srecanja">
          {vKolu.map((s) => {
            const konec = s.status === 'KONCANO'
            const domZmaga = konec && s.dobljeneDomaci > s.dobljeneGost
            const gostZmaga = konec && s.dobljeneGost > s.dobljeneDomaci
            return (
              <Link key={s.id} to={`/srecanja/${s.id}`} className="liga-mobi__srecanje">
                <span
                  className={
                    'liga-mobi__ekipa liga-mobi__ekipa--desno' +
                    (gostZmaga ? ' liga-mobi__ekipa--poraz' : '')
                  }
                >
                  {s.domaci}
                </span>
                <span
                  className={'liga-mobi__izid' + (konec ? '' : ' liga-mobi__izid--caka')}
                >
                  {konec ? `${s.dobljeneDomaci} : ${s.dobljeneGost}` : 'vs'}
                </span>
                <span
                  className={
                    'liga-mobi__ekipa' + (domZmaga ? ' liga-mobi__ekipa--poraz' : '')
                  }
                >
                  {s.gost}
                </span>
              </Link>
            )
          })}
        </div>
      </div>

      <div>
        <span className="liga-mobi__trak-naslov">Vsa kola</span>
        <div className="liga-mobi__trak">
          {kola.map((k) => (
            <button
              type="button"
              key={k}
              className={
                'liga-mobi__trak-gumb' +
                (koloOdigrano(k) ? ' liga-mobi__trak-gumb--odigrano' : '') +
                (k === kolo ? ' liga-mobi__trak-gumb--izbrano' : '')
              }
              aria-current={k === kolo ? 'true' : undefined}
              aria-label={`${k}. kolo — ${metaKola(srecanja, k, koloOdigrano(k))}`}
              onClick={() => onKolo(k)}
            >
              {k}
            </button>
          ))}
        </div>
      </div>
    </>
  )
}

/* ---------- Pravila (modalno okno) ---------- */

/* Pravila so referenca: gledalec jih pogleda enkrat, zato jih stran ne nosi v
   glavi, ampak jih odpre okno. Pari so isti kot prej (kolofon oznaka <-> vrednost). */
function PravilaOkno({
  liga,
  lahkoUreja,
  smemUrejatiPrehode,
  nizje,
  onUredi,
  onUrediPrehode,
  onZapri,
}: {
  liga: LigaDto
  lahkoUreja: boolean
  /* Prehodi se ne zaklenejo z razporedom, zato ima urejanje svoj pogoj. */
  smemUrejatiPrehode: boolean
  nizje: LigaDto[]
  onUredi: () => void
  onUrediPrehode: () => void
  onZapri: () => void
}) {
  const podatki: [string, string][] = [
    ['Format', OZNAKE_FORMAT[liga.formatSrecanja]],
    ['Nizi', `najboljši od ${liga.steviloNizov}`],
    ['Konec srečanja', liga.zmagZaSrecanje ? `prvi do ${liga.zmagZaSrecanje} zmag` : 'vse tekme'],
    ['Sistem', liga.dvokrozno ? 'dvokrožno' : 'enokrožno'],
    ['Točke', `${liga.tockeZmaga} / ${liga.tockeNeodloceno} / ${liga.tockePoraz} (Z/N/P)`],
    ['Neodločeno', liga.dovoljenoNeodloceno ? 'mogoče' : 'ni mogoče'],
    ['Dvojna registracija', liga.prepovedDvojneRegistracije ? 'prepovedana' : 'dovoljena'],
    ['Šteje v ELO', liga.stejeVElo ? 'da (posamične)' : 'ne'],
  ]
  podatki.push(['Višja liga', liga.visjaLigaIme ?? '—'])
  podatki.push(['Nižje lige', nizje.length > 0 ? nizje.map((k) => k.ime).join(' · ') : '—'])
  podatki.push(['Napreduje', String(liga.stNapreduje)])
  podatki.push(['Izpade', String(liga.stIzpade)])

  return (
    <ModalnoOkno naslov="Pravila lige" onZapri={onZapri}>
      {lahkoUreja ? (
        <div className="liga__pravila-dejanje">
          <button type="button" className="gumb gumb--majhen" onClick={onUredi}>
            Uredi pravila
          </button>
        </div>
      ) : (
        <p className="liga__pravila-opomba">
          Zaklenjena, ker je razpored že generiran. Popravek bi razveljavil odigrana
          srečanja.
        </p>
      )}
      {smemUrejatiPrehode && (
        <div className="liga__pravila-dejanje">
          <button type="button" className="gumb gumb--majhen" onClick={onUrediPrehode}>
            Uredi prehode
          </button>
        </div>
      )}
      <dl className="opis-mreza">
        {podatki.map(([k, v]) => (
          <div key={k} className="opis-mreza__par">
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
    </ModalnoOkno>
  )
}

/* ---------- Ekipe in kader ---------- */

/* Ime ekipe z oznako pod njim. Oznaka se izpiše samo, kadar kaj pove:
   pri klubski ekipi z lastnim imenom klub (sicer JE ime že klub), pri prosti
   pa to, da klub nima — sicer bi bila v seznamu videti kot vsaka druga. */
function ImeEkipe({ ekipa }: { ekipa: EkipaDto }) {
  const oznaka = ekipa.klub === null ? 'prosta ekipa' : ekipa.ime ? ekipa.klub : null
  return (
    <span className="liga__ekipa-ime">
      <span>{ekipa.prikazanoIme}</span>
      {oznaka && <span className="liga__ekipa-oznaka">{oznaka}</span>}
    </span>
  )
}

/* Pogled ekip za ligo, ki že teče: kader je takrat zaklenjen (strežnik ga v
   drugih stanjih ne spusti), zato okno samo pokaže, kdo je prijavljen. */
function EkipeKaderOkno({ idLiga, onZapri }: { idLiga: number; onZapri: () => void }) {
  const ekipe = useQuery({ queryKey: ['ekipe', idLiga], queryFn: () => ligeApi.ekipe(idLiga) })
  const [odprta, nastaviOdprto] = useState<number | null>(null)

  return (
    <ModalnoOkno naslov="Ekipe in kader" onZapri={onZapri}>
      {ekipe.isPending && <p className="obvestilo">Nalaganje …</p>}
      <NapakaPoizvedbe poizvedba={ekipe} kaj="ekip" />
      {ekipe.data && ekipe.data.length === 0 && <p className="obvestilo">Ni še ekip.</p>}
      {ekipe.data && ekipe.data.length > 0 && (
        <ul className="liga__ekipe">
          {ekipe.data.map((e) => (
            <li key={e.id}>
              <button
                type="button"
                className="liga__ekipa liga__ekipa--klikljiva"
                aria-expanded={odprta === e.id}
                onClick={() => nastaviOdprto(odprta === e.id ? null : e.id)}
              >
                <ImeEkipe ekipa={e} />
                <span className="sekcija__meta">
                  {kaderTekst(e.steviloKadra)} {odprta === e.id ? '▴' : '▾'}
                </span>
              </button>
              {odprta === e.id && <Kader idEkipa={e.id} ekipa={e.prikazanoIme} />}
            </li>
          ))}
        </ul>
      )}
      <p className="namig">Kader je mogoče spreminjati samo, dokler je liga v pripravi.</p>
    </ModalnoOkno>
  )
}

/* Urejanje ekip in kadra, dokler je liga v pripravi. Sekcija stoji na strani
   (in ne v oknu), ker je to takrat glavno opravilo organizatorja: dodaj ekipe,
   sestavi kader, generiraj razpored. */
function EkipeUredi({
  idLiga,
  steviloEkip,
  onUrediPravila,
}: {
  idLiga: number
  steviloEkip: number
  onUrediPravila: () => void
}) {
  const odjemalec = useQueryClient()
  const ekipe = useQuery({ queryKey: ['ekipe', idLiga], queryFn: () => ligeApi.ekipe(idLiga) })
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  /* Vrsta ekipe je odločitev pred vnosom: klubska se izbere iz registra,
     prosta se poimenuje sama in v register klubov ne pride. */
  const [nacin, nastaviNacin] = useState<'klub' | 'prosta'>('klub')
  const [idKlub, nastaviKlub] = useState('')
  const [ime, nastaviIme] = useState('')
  const [urejanKader, nastaviUrejanKader] = useState<EkipaDto | null>(null)

  const osveziEkipe = () => odjemalec.invalidateQueries({ queryKey: ['ekipe', idLiga] })
  const osveziLigo = () => odjemalec.invalidateQueries({ queryKey: ['liga', idLiga] })

  /* Ime je pri prosti ekipi edino poimenovanje (strežnik zahteva vsaj dva
     znaka), pri klubski pa neobvezen nadomestek za »Klub N«. */
  const lahkoDodam = nacin === 'klub' ? idKlub !== '' : ime.trim().length >= 2

  const dodaj = useMutation({
    mutationFn: () =>
      ligeApi.dodajEkipo(idLiga, {
        idKlub: nacin === 'klub' ? Number(idKlub) : null,
        zaporedna: null,
        ime: ime.trim() || null,
      }),
    onSuccess: () => { osveziEkipe(); osveziLigo(); nastaviKlub(''); nastaviIme('') },
  })
  const odstrani = useMutation({
    mutationFn: (idEkipa: number) => ligeApi.odstraniEkipo(idEkipa),
    onSuccess: () => { osveziEkipe(); osveziLigo() },
  })
  const razpored = useMutation({
    mutationFn: () => ligeApi.generirajRazpored(idLiga),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['srecanja', idLiga] })
      osveziLigo()
      odjemalec.invalidateQueries({ queryKey: ['lestvica', idLiga] })
    },
  })

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Ekipe</h2>
        <span className="sekcija__meta">{steviloEkip} od najmanj 2</span>
      </div>

      <div className="izbirnik liga__nacin-ekipe" role="group" aria-label="Vrsta ekipe">
        <button
          type="button"
          className={'izbirnik__gumb' + (nacin === 'klub' ? ' izbirnik__gumb--aktiven' : '')}
          aria-pressed={nacin === 'klub'}
          onClick={() => nastaviNacin('klub')}
        >
          Iz registra
        </button>
        <button
          type="button"
          className={'izbirnik__gumb' + (nacin === 'prosta' ? ' izbirnik__gumb--aktiven' : '')}
          aria-pressed={nacin === 'prosta'}
          onClick={() => nastaviNacin('prosta')}
        >
          Prosta ekipa
        </button>
      </div>

      <form
        className="obrazec__vrstica liga__dodaj-ekipo"
        onSubmit={(d) => {
          d.preventDefault()
          if (lahkoDodam && !dodaj.isPending) dodaj.mutate()
        }}
      >
        {nacin === 'klub' && (
          <label className="obrazec__polje">
            <span>Klub</span>
            <select value={idKlub} onChange={(d) => nastaviKlub(d.target.value)}>
              <option value="">— izberi klub —</option>
              {klubi.data?.map((k) => (
                <option key={k.id} value={k.id}>{k.ime}</option>
              ))}
            </select>
          </label>
        )}
        <label className="obrazec__polje">
          <span>{nacin === 'klub' ? 'Ime ekipe (neobvezno)' : 'Ime ekipe'}</span>
          <input
            value={ime}
            maxLength={60}
            onChange={(d) => nastaviIme(d.target.value)}
            placeholder={nacin === 'klub' ? 'sicer klub in številka' : 'npr. Kuhinja'}
          />
        </label>
        <button className="gumb" type="submit" disabled={!lahkoDodam || dodaj.isPending}>
          + Dodaj ekipo
        </button>
      </form>
      {nacin === 'prosta' && (
        <p className="namig">
          Prosta ekipa nastopa samo v tej ligi in v register klubov ne pride. Kader ji sestaviš
          iz igralcev registra, enako kot klubski.
        </p>
      )}
      <SporociloNapake napaka={dodaj.error} />

      {ekipe.data && ekipe.data.length === 0 && <p className="obvestilo">Ni še ekip.</p>}
      {ekipe.data && ekipe.data.length > 0 && (
        <ul className="liga__ekipe">
          {ekipe.data.map((e) => (
            <li key={e.id} className="liga__ekipa">
              <ImeEkipe ekipa={e} />
              <span className="liga__ekipa-gumbi">
                <span className="sekcija__meta">{kaderTekst(e.steviloKadra)}</span>
                <button className="gumb gumb--majhen" onClick={() => nastaviUrejanKader(e)}>Kader</button>
                <button className="gumb gumb--majhen gumb--nevaren" onClick={() => odstrani.mutate(e.id)}>
                  Odstrani
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
      <SporociloNapake napaka={odstrani.error} />

      <div className="liga__priprava-dejanja">
        <button
          className="gumb gumb--zreb"
          disabled={(ekipe.data?.length ?? 0) < 2 || razpored.isPending}
          onClick={() => razpored.mutate()}
        >
          Generiraj razpored
        </button>
        <button className="gumb gumb--majhen" onClick={onUrediPravila}>
          Uredi pravila
        </button>
      </div>
      <SporociloNapake napaka={razpored.error} />

      {urejanKader && (
        <KaderOkno ekipa={urejanKader} onZapri={() => nastaviUrejanKader(null)} />
      )}
    </div>
  )
}

function KaderOkno({ ekipa, onZapri }: { ekipa: EkipaDto; onZapri: () => void }) {
  const odjemalec = useQueryClient()
  const kader = useQuery({ queryKey: ['kader', ekipa.id], queryFn: () => ligeApi.kader(ekipa.id) })
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  const [idIgralec, nastaviIgralca] = useState('')

  const osvezi = () => {
    odjemalec.invalidateQueries({ queryKey: ['kader', ekipa.id] })
    /* Seznam ekip nosi velikost kadra, zato ga je treba osvežiti z njim. */
    odjemalec.invalidateQueries({ queryKey: ['ekipe'] })
  }
  const dodaj = useMutation({
    mutationFn: () => ligeApi.dodajVKader(ekipa.id, { idIgralec: Number(idIgralec), vrstniRed: null }),
    onSuccess: () => { osvezi(); nastaviIgralca('') },
  })
  const odstrani = useMutation({
    mutationFn: (idKader: number) => ligeApi.odstraniIzKadra(idKader),
    onSuccess: osvezi,
  })

  const vKadru = new Set(kader.data?.map((k) => k.idIgralec))
  const naVoljo = igralci.data?.filter((i) => !vKadru.has(i.id)) ?? []

  return (
    <ModalnoOkno naslov={`Kader – ${ekipa.prikazanoIme}`} onZapri={onZapri}>
      <div className="obrazec__vrstica liga__dodaj-ekipo">
        <select value={idIgralec} onChange={(d) => nastaviIgralca(d.target.value)}>
          <option value="">— izberi igralca —</option>
          {naVoljo.map((i) => (
            <option key={i.id} value={i.id}>{i.priimek} {i.ime}{i.klub ? ` (${i.klub.ime})` : ''}</option>
          ))}
        </select>
        <button className="gumb" disabled={!idIgralec || dodaj.isPending} onClick={() => dodaj.mutate()}>
          + Dodaj
        </button>
      </div>
      <SporociloNapake napaka={dodaj.error} />

      {kader.data && kader.data.length === 0 && <p className="obvestilo">Kader je prazen.</p>}
      {kader.data && kader.data.length > 0 && (
        <ul className="liga__ekipe">
          {kader.data.map((k) => (
            <li key={k.id} className="liga__ekipa">
              <span>{k.polnoIme}{k.rating != null && <span className="liga__rating"> {k.rating}</span>}</span>
              <button className="gumb gumb--majhen gumb--nevaren" onClick={() => odstrani.mutate(k.id)}>
                Odstrani
              </button>
            </li>
          ))}
        </ul>
      )}
    </ModalnoOkno>
  )
}

/* ---------- Sklanjanje ---------- */

function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
}

/* Slovnično pravilna oblika besede "kolo" glede na število. */
function kolTekst(n: number): string {
  if (n === 1) return 'kolo'
  if (n === 2) return 'koli'
  if (n === 3 || n === 4) return 'kola'
  return 'kol'
}

/* Velikost kadra v vrstici ekipe; prazen kader je stanje in ne "0 igralcev". */
function kaderTekst(n: number): string {
  if (n === 0) return 'kader prazen'
  if (n === 1) return '1 igralec'
  if (n === 2) return '2 igralca'
  if (n === 3 || n === 4) return `${n} igralci`
  return `${n} igralcev`
}

function napredujeTekst(n: number): string {
  if (n === 1) return 'napreduje'
  if (n === 2) return 'napredujeta'
  return 'napredujejo'
}

function izpadeTekst(n: number): string {
  if (n === 1) return 'izpade'
  if (n === 2) return 'izpadeta'
  return 'izpadejo'
}
