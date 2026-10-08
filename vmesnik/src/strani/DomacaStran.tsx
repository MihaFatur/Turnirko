/* Domača (začetna) stran: pet sklopov enega samega zapisnika — na vrhu
   Turnirji in Moje lige drug ob drugem, pod njima lestvica čez vso širino,
   nato koledar tekočega meseca in na dnu medsebojni izid dveh igralcev.

   Koledar stoji pod lestvico in ne na vrhu: mreža meseca je najvišji sklop
   strani in gledalec je pred njo videl same črte, preden je prišel do imena.
   Vprašanje »kdaj je naslednji turnir« mu odgovori tudi vrstica »Naslednje«
   ob mreži, do katere zdaj pride z eno stranjo drsenja. Sam sklop je v
   komponente/KoledarSklop.

   Vrstice s sezono in števci pod mastheadom namenoma ni: stran naj se začne z
   vsebino, ne s povzetkom o sebi. Vse je bralno in vidno tudi gostom;
   spremljanje lig je edino dejanje in zahteva prijavo — gostu zato krmil
   računa (kvadratki, filtri lestvice) sploh ne pokažemo, namesto da bi jih
   pokazali in ob kliku zahtevali prijavo. Izjema je »Uredi izbor« ob ligah:
   ena mono povezava, ki gostu in igralcu brez Premium pokaže oglas Igralec
   Premium (PremiumOglas), ostalim pa okno za urejanje izbora.

   Igralcu s Premium je stran po meri (oktober 2026): v ligah, kjer igra, kaže
   razpredelnica njegovo ekipo s sosedama namesto vrha (izračuna strežnik),
   turnirji so izbrani zanj z razlogom ob vrstici (IzborTurnirjev v zaledju),
   lestvica pa pokaže njega in igralce okoli njega na njegovi lestvici. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { domovApi, ligeApi, statistikaApi, turnirjiApi } from '../api/zahteve'
import type { DomovLigaDto, LestvicaIgralcaDto, RazlogTurnirja, Spol, TurnirDto } from '../api/tipi'
import { EnaNaEna } from '../komponente/EnaNaEna'
import { GumbSpremljanja } from '../komponente/GumbSpremljanja'
import { KoledarSklop } from '../komponente/KoledarSklop'
import { DomaceLigeOkno, IzborLigOkno } from '../komponente/IzborLigOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { usePremiumOglas } from '../komponente/PremiumOglasKontekst'
import { ZnackaStatusa } from '../komponente/Znacka'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { oblikujDanKratekMesec, oblikujDatum, potekKol, sklonIgralcev } from '../pomozno/oblikovanje'
import { useSpremljanjeLig } from '../pomozno/spremljaneLige'

/* Koliko vrstic nosi posamezen sklop. Domača stran je povzetek: kdor hoče
   več, gre po povezavi v glavi sklopa. */
const TURNIRJEV = 4
const IGRALCEV = 8

type FilterLestvice = 'vsi' | 'okoli' | 'mojeLige' | 'mojKlub'

export function DomacaStran() {
  const { uporabnik, mojIdIgralec, jeAdmin, jePremium } = useAvtentikacija()
  /* Izbrani filter lestvice; null = privzetek (glej filterLestvice spodaj). */
  const [filter, nastaviFilter] = useState<FilterLestvice | null>(null)
  const [izborOdprt, nastaviIzborOdprt] = useState(false)
  const [domaceOdprt, nastaviDomaceOdprt] = useState(false)
  const { odpri: odpriPremiumOglas } = usePremiumOglas()

  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })

  /* Igralec s Premium, povezan z zapisom med igralci - samo njemu je stran po
     meri. Paket preveri tudi strežnik (domov/turnirji drugim vrne prazno,
     domov/lige drugim vrh lestvice); tu le ne sprožimo poizvedbe zaman. */
  const premiumIgralec = jePremium && uporabnik?.vloga === 'IGRALEC' && mojIdIgralec != null
  const turnirjiZame = useQuery({
    queryKey: ['domov-turnirji', uporabnik?.id ?? null],
    queryFn: domovApi.turnirji,
    enabled: premiumIgralec,
  })
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Izbor lig je last računa; gost ga nima, zato mu sklop pokaže lige, ki si
     jih je nazadnje ogledal (zapomni si jih njegov brskalnik). */
  const { jePrijavljen, spremljane, preklopi, ponudiPremium } = useSpremljanjeLig()

  /* Sklop ima več virov in strežnik med njimi razsodi sam (glej
     DomovStoritev.povzetkiLig), zato mu povemo, katere vrste je seznam, ki ga
     pošiljamo: izbor RAČUNA prevlada, gostov spomin brskalnika pa obvelja šele,
     če admin domače strani ni uredil. Ogled namreč ni izbira — ena odprta liga
     pred tednom ne sme povoziti tega, kar je zveza postavila na vhodno stran.
     Igralcu s Premium strežnik brez lastnega izbora pokaže lige njegovih ekip —
     odgovor je zato odvisen tudi od računa, ki je zahtevo poslal, in račun je
     del ključa (drugače bi si odjavljen gost in prijavljen igralec delila
     predpomnjen odgovor). */
  const izbraneRacuna = jePrijavljen ? spremljane : []
  const ogledane = jePrijavljen ? [] : spremljane

  const povzetkiLig = useQuery({
    queryKey: ['domov-lige', uporabnik?.id ?? null, izbraneRacuna, ogledane],
    queryFn: () => domovApi.lige(izbraneRacuna, ogledane),
  })

  /* Najnovejši turnir je zgoraj — isti vrstni red kot privzeti »Najnovejši«
     na /turnirji, da sklop ostane začetek seznama, v katerega vodi »Vsi →«. */
  const prikazaniTurnirji = useMemo(() => razvrstiTurnirje(turnirji.data), [turnirji.data])

  /* Izbor za igralca s Premium: strežnik pove katere in zakaj, turnirje same
     pa imamo že naložene. Prazen izbor (ni turnirjev) pusti privzeti seznam. */
  const turnirjiPoMeri = useMemo(() => {
    if (!premiumIgralec || !turnirjiZame.data || !turnirji.data) return null
    const poId = new Map(turnirji.data.map((t) => [t.id, t]))
    const izbor = turnirjiZame.data.flatMap((z) => {
      const t = poId.get(z.idTurnir)
      return t ? [{ turnir: t, razlog: z.razlog }] : []
    })
    return izbor.length > 0 ? izbor : null
  }, [premiumIgralec, turnirjiZame.data, turnirji.data])
  const nalagamTurnirje = turnirji.isPending || (premiumIgralec && turnirjiZame.isPending)

  const mojaVrstica = lestvica.data?.find((v) => v.idIgralca === mojIdIgralec) ?? null
  const mojIdKluba = uporabnik?.idKlub ?? mojaVrstica?.idKluba ?? null

  /* Sklop kaže ENO lestvico: med moškimi in ženskami ni obračunanih tekem in
     skupnega mesta ni (isto pravilo kot na strani Lestvica). Prej je mešal oba
     spola - Sara Tokić je bila 6., zato je bil Hribar na domači strani 7., na
     strani Lestvica pa 6. Prijavljen igralec vidi lestvico svojega spola in
     skupine (tekmovalci oz. rekreativci); gost in kdor na lestvici ni, dobi ob
     vsakem prihodu naključno moško ali žensko lestvico članov. */
  const [nakljucniSpol] = useState<Spol>(() => (Math.random() < 0.5 ? 'MOSKI' : 'ZENSKI'))
  const lestvicaSklopa = {
    spol: mojaVrstica?.spol ?? nakljucniSpol,
    rekreativci: mojaVrstica?.rekreativec ?? false,
  }

  /* Igralec s Premium, ki je na lestvici, privzeto vidi sebe in igralce okoli
     sebe; vsi ostali vrh lestvice. */
  const okoliMene = premiumIgralec && mojaVrstica !== null
  const filterLestvice: FilterLestvice = filter ?? (okoliMene ? 'okoli' : 'vsi')

  /* Merilo je, kaj sklop DEJANSKO kaže, in ne izbor: gost izbora nima, pa mu
     vseeno pokažemo lige v teku — vrstica "ne spremljaš N lig" bi jih sicer
     štela med nespremljane, čeprav so tik nad njo. */
  /* Ali sklop kaže LASTEN izbor računa. Po tem se ravna naslov in prazno
     stanje: brez izbora tu ne stojijo »moje« lige, ampak adminove. */
  const lastenIzbor = jePrijavljen && spremljane.length > 0

  const prikazaneLige = (povzetkiLig.data ?? []).map((l) => l.id)

  /* Strežnik po vrstici pove, da je liga tu zato, ker igralec v njej nastopa.
     Take lige so »moje«, čeprav izbora ni sestavil; adminovim bi naslov
     »moje« lagal. */
  const izEkipe = (povzetkiLig.data ?? []).some((l) => l.izEkipe)
  const mojeLige = lastenIzbor || izEkipe

  /* Filter »Moje lige« na lestvici mora meriti isto, kar sklop nad njim
     imenuje »moje«. */
  const idjiMojihLig = !lastenIzbor && izEkipe ? prikazaneLige : spremljane

  const nespremljanihVTeku = (lige.data ?? []).filter(
    (l) => l.status === 'V_TEKU' && !prikazaneLige.includes(l.id),
  ).length

  return (
    <section className="domov">
      {/* Naslova prve vrste sta brez črte: nad njima ni vsebine, od katere bi ju
          ločila — stran se z njima šele začne. Sklopi niže ločnico obdržijo. */}
      <div className="domov__vrh">
        <div className="domov__sklop">
          <div className="naslovna-vrstica naslovna-vrstica--brez-crte">
            {/* »Zate« samo takrat, ko izbor res je njegov - kot »Moje lige«. */}
            <h2>{turnirjiPoMeri ? 'Turnirji zate' : 'Turnirji'}</h2>
            <Link to="/turnirji" className="sekcija__meta">
              Vsi →
            </Link>
          </div>
          <NapakaPoizvedbe poizvedba={turnirji} kaj="turnirjev" />
          {nalagamTurnirje && <Skelet vrstic={3} />}
          {!nalagamTurnirje && turnirji.data && prikazaniTurnirji.length === 0 && (
            <p className="domov__prazno">Ni turnirjev.</p>
          )}
          {!nalagamTurnirje && (
            <div className="domov__seznam">
              {turnirjiPoMeri
                ? turnirjiPoMeri.map(({ turnir, razlog }) => (
                    <VrsticaTurnirja turnir={turnir} razlog={razlog} key={turnir.id} />
                  ))
                : prikazaniTurnirji.map((t) => <VrsticaTurnirja turnir={t} key={t.id} />)}
            </div>
          )}
        </div>

        <div className="domov__sklop">
          <div className="naslovna-vrstica naslovna-vrstica--brez-crte">
            {/* »Moje lige« samo takrat, ko sklop res kaže moje: lasten izbor ali
                lige, v katerih igram. Kdor ni ne eno ne drugo, tu vidi lige, ki
                jih je postavil admin — in naslov, ki bi jim rekel »moje«, bi
                lagal. */}
            <h2>{mojeLige ? 'Moje lige' : 'Lige'}</h2>
            <div className="naslovna-vrstica__desno">
              {/* Izbor je nastavitev domače strani, zato okno in ne pot na
                  /lige — tam vrstica lige vodi v ligo in preklopa ne nosi.
                  Izbor je last računa, zato gost na istem mestu dobi okno za
                  nov račun: napis mu pove, kaj račun prinese, namesto da bi
                  gumb skrili in bi za možnost sploh ne vedel. */}
              <button
                type="button"
                className="sekcija__meta"
                onClick={() =>
                  jePrijavljen && !ponudiPremium
                    ? nastaviIzborOdprt(true)
                    : odpriPremiumOglas('lige')
                }
              >
                Uredi izbor →
              </button>
              {/* Kaj stoji tu privzeto, je uredniška odločitev zveze in ne
                  osebna nastavitev — zato svoje okno in samo za admina. */}
              {jeAdmin && (
                <button
                  type="button"
                  className="sekcija__meta"
                  onClick={() => nastaviDomaceOdprt(true)}
                >
                  Privzeti ligi →
                </button>
              )}
            </div>
          </div>
          <NapakaPoizvedbe poizvedba={povzetkiLig} kaj="lig" />
          {povzetkiLig.isPending && <Skelet vrstic={3} />}
          {povzetkiLig.data && povzetkiLig.data.length === 0 && (
            <p className="domov__prazno">
              {lastenIzbor ? 'Ne spremljaš še nobene lige.' : 'Nobena liga ne teče.'}
            </p>
          )}
          <div className="domov__seznam">
            {(povzetkiLig.data ?? []).map((liga) => (
              <KarticaLige
                liga={liga}
                key={liga.id}
                /* Kvadratek je preklop računa; gost ga nima, zato ga tudi ne
                   vidi — vrstica lige mu ostane sama povezava. */
                spremljam={jePrijavljen ? spremljane.includes(liga.id) : null}
                naPreklop={() => preklopi(liga.id)}
              />
            ))}
            {/* Vabilo k prijavi je odšlo: gostu je sklop bralen tak, kot je, in
                poziv pod seznamom je bil edina vrstica, ki od njega nekaj
                terja. Pot do prijave nosi glava. */}
            {jePrijavljen && nespremljanihVTeku > 0 && (
              <div className="domov__liga-dodaj">
                <span className="domov__namig">
                  Ne spremljaš {nespremljanihVTeku} {sklonLig(nespremljanihVTeku)}, ki
                  {nespremljanihVTeku === 1 ? ' je' : ' so'} v teku.
                </span>
                <button
                  type="button"
                  className="gumb gumb--majhen"
                  onClick={() => nastaviIzborOdprt(true)}
                >
                  Dodaj ligo
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      <SklopLestvica
        vrstice={lestvica.data}
        poizvedba={lestvica}
        filter={filterLestvice}
        naFilter={nastaviFilter}
        mojaVrstica={okoliMene ? mojaVrstica : null}
        lestvicaSklopa={lestvicaSklopa}
        mojIdIgralec={mojIdIgralec}
        mojIdKluba={mojIdKluba}
        spremljane={idjiMojihLig}
        jePrijavljen={jePrijavljen}
      />

      <KoledarSklop />

      <div className="domov__sklop">
        <div className="naslovna-vrstica">
          <h2>Ena na ena</h2>
        </div>
        <EnaNaEna />
      </div>

      {izborOdprt && <IzborLigOkno onZapri={() => nastaviIzborOdprt(false)} />}
      {domaceOdprt && <DomaceLigeOkno onZapri={() => nastaviDomaceOdprt(false)} />}
    </section>
  )
}

/* ---------- Turnirji ---------- */

/* Po datumu začetka navzdol, ne po id: uvožena zgodovina je vnesena isti dan
   in id bi jo razvrstil naključno. Turnir brez datuma gre na konec (pravilo
   poDatumu v TurnirjiStran): datum manjka, ker ga organizator še ni vpisal. */
function razvrstiTurnirje(turnirji: TurnirDto[] | undefined): TurnirDto[] {
  return [...(turnirji ?? [])]
    .sort((a, b) => {
      if (!a.datumZacetka) return b.datumZacetka ? 1 : 0
      if (!b.datumZacetka) return -1
      return b.datumZacetka.localeCompare(a.datumZacetka) || a.ime.localeCompare(b.ime, 'sl')
    })
    .slice(0, TURNIRJEV)
}

function VrsticaTurnirja({ turnir, razlog }: { turnir: TurnirDto; razlog?: RazlogTurnirja }) {
  const vTeku = turnir.status === 'V_TEKU'
  const spalica = vTeku && turnir.vsehTekem > 0
  const odstotek = turnir.vsehTekem > 0
    ? Math.round((turnir.odigranihTekem / turnir.vsehTekem) * 100)
    : 0
  /* Uvožen zaključen turnir nima ne kraja ne česa drugega za podnaslov;
     prazna vrstica bi pod imenom pustila odmik brez besedila. */
  const opis = opisTurnirja(turnir)

  return (
    <Link to={`/turnirji/${turnir.id}`} className="domov__turnir">
      {/* Zakaj je turnir tu - brez tega bi bil izbor po meri videti naključen. */}
      {razlog && <span className="domov__turnir-razlog">{napisRazloga(razlog, turnir)}</span>}
      <span className="domov__turnir-glava">
        <span className="domov__turnir-ime">{turnir.ime}</span>
        <ZnackaStatusa status={turnir.status} />
      </span>
      {opis && <span className="domov__turnir-opis">{opis}</span>}
      {spalica && (
        <>
          <span className="palica">
            <span className="palica__polnilo" style={{ width: `${odstotek}%` }} />
          </span>
          <span className="domov__turnir-stanje">
            <span>
              {turnir.odigranihTekem} od {turnir.vsehTekem} tekem
            </span>
            {turnir.zadnjiIzid && <span>Zadnji izid: {turnir.zadnjiIzid}</span>}
          </span>
        </>
      )}
    </Link>
  )
}

/* Mono vrstica nad imenom turnirja v izboru za igralca s Premium. */
function napisRazloga(razlog: RazlogTurnirja, turnir: TurnirDto): string {
  switch (razlog) {
    case 'PRIJAVLJEN':
      return 'Prihaja · tvoja prijava'
    case 'PRIHAJA_PRIMEREN':
      return 'Prihaja · primeren zate'
    case 'ZADNJI':
      return turnir.status === 'V_TEKU' ? 'Tvoj turnir · v teku' : 'Tvoj zadnji turnir'
    case 'V_TEKU':
      return 'Poteka zdaj'
    case 'KOLEGI':
      return turnir.status === 'PRIPRAVA'
        ? 'Prijavljeni klubski kolegi'
        : turnir.status === 'V_TEKU'
          ? 'Igrajo klubski kolegi'
          : 'Igrali so klubski kolegi'
    case 'PRIMEREN':
      return 'Primeren zate'
    case 'OSTALO':
      return turnir.status === 'PRIPRAVA' ? 'Prihaja' : 'Najnovejši'
  }
}

/* Podnaslov vrstice: kraj in nato tisto, kar o turnirju v tem stanju največ
   pove — koliko jih igra in kje je oz. kdaj se začne. Zmagovalca domača stran
   ne izpiše: sklop pove, kaj se igra, izid pa stoji na strani turnirja. */
function opisTurnirja(turnir: TurnirDto): string {
  const deli: string[] = []
  if (turnir.kraj) deli.push(turnir.kraj.ime)
  if (turnir.status === 'V_TEKU') {
    if (turnir.prijavljenihSkupaj > 0) {
      deli.push(`${turnir.prijavljenihSkupaj} ${sklonIgralcev(turnir.prijavljenihSkupaj)}`)
    }
    if (turnir.faza) deli.push(turnir.faza)
  } else if (turnir.status === 'PRIPRAVA') {
    const datum = oblikujDatum(turnir.datumZacetka)
    deli.push(datum ? `začetek ${datum}` : 'datum še ni znan')
  }
  return deli.join(' · ')
}

/* ---------- Moje lige ---------- */

function KarticaLige({
  liga,
  spremljam,
  naPreklop,
}: {
  liga: DomovLigaDto
  /* null = gost; izbor je last računa, zato kvadratka sploh ne izrišemo. */
  spremljam: boolean | null
  naPreklop: () => void
}) {
  return (
    <div className="domov__liga">
      <div className="domov__liga-glava">
        <span className="domov__liga-naslov">
          {spremljam !== null && (
            <GumbSpremljanja
              ime={liga.ime}
              slog="kvadratek"
              spremljam={spremljam}
              naPreklop={naPreklop}
            />
          )}
          <Link to={`/lige/${liga.id}`} className="domov__liga-ime">
            {liga.ime}
          </Link>
        </span>
        {liga.vsehKol > 0 && (
          <span className="domov__liga-kolo">
            {potekKol(liga.odigranihKol, liga.vsehKol)}
          </span>
        )}
      </div>

      {liga.vrh.length > 0 && (
        <div className="domov__ekipe">
          <div className="domov__ekipe-vrstica domov__ekipe-vrstica--glava">
            <span>M.</span>
            <span>Ekipa</span>
            <span>Sr.</span>
            <span>Toč.</span>
          </div>
          {/* Igralcu s Premium strežnik pošlje njegovo ekipo s sosedama; ta je
              poudarjena, na vrhu oz. dnu lestvice pa to pove droben napis -
              okno tam ne more biti »ena gor, ena dol«. */}
          {liga.vrh.map((v) => (
            <div
              className={'domov__ekipe-vrstica' + (v.moja ? ' domov__ekipe-vrstica--moja' : '')}
              key={v.mesto + v.ekipa}
            >
              <span className="domov__ekipe-mesto">{v.mesto}.</span>
              <span className="domov__ekipe-ime">
                {v.ekipa}
                {v.moja && v.mesto === 1 && <span className="domov__ekipe-meja">prva</span>}
                {v.moja && v.mesto > 1 && v.mesto === liga.ekip && (
                  <span className="domov__ekipe-meja">zadnja</span>
                )}
              </span>
              <span className="domov__ekipe-odigrane">{v.odigrane}</span>
              <span className="domov__ekipe-tocke">{v.tocke}</span>
            </div>
          ))}
        </div>
      )}

      {/* Samo kdaj in ne kdo: kolo igra več parov hkrati, izpisan prvi med
          njimi pa se je bral kot edino srečanje kola. Pare nosi razpored lige. */}
      {liga.naslednje && (
        <div className="domov__liga-noga">
          Naslednje kolo{' '}
          {liga.naslednje.datum
            ? oblikujDanKratekMesec(liga.naslednje.datum)
            : `${liga.naslednje.kolo}.`}
        </div>
      )}
    </div>
  )
}

/* ---------- Lestvica ---------- */

function SklopLestvica({
  vrstice,
  poizvedba,
  filter,
  naFilter,
  mojIdIgralec,
  mojIdKluba,
  spremljane,
  jePrijavljen,
  mojaVrstica,
  lestvicaSklopa,
}: {
  vrstice: LestvicaIgralcaDto[] | undefined
  poizvedba: { error: unknown; isFetching: boolean; refetch: () => unknown; isPending: boolean }
  filter: FilterLestvice
  naFilter: (v: FilterLestvice) => void
  mojIdIgralec: number | null
  mojIdKluba: number | null
  spremljane: number[]
  /* Oba zožena izbora govorita o računu (»moje« lige, »moj« klub), zato ju
     gost ne dobi: gledal bi filtra, ki mu ne moreta vrniti ničesar. */
  jePrijavljen: boolean
  /* Igralec s Premium, ki je na lestvici: sklop ponudi »Okoli mene«. */
  mojaVrstica: LestvicaIgralcaDto | null
  /* Katero lestvico sklop kaže (spol in skupina). */
  lestvicaSklopa: { spol: Spol; rekreativci: boolean }
}) {
  /* Na katerem mestu okna stoji igralec (0 = prvi napisan). Naključno ob
     vsakem prihodu na stran - enkrat je prvi in pod njim sedem, drugič peti -,
     da domača stran ni vsakič enaka; ob vrhu ali dnu lestvice se okno
     poravna, da je polno. */
  const [odmik] = useState(() => Math.floor(Math.random() * IGRALCEV))

  /* Lestvica igralca: njegov spol in skupina (tekmovalci ali rekreativci),
     ista kot mesto na profilu. Med spoloma in skupinama ni primerljivih
     številk, zato okno ne teče čez skupni seznam. */
  const okolica = useMemo(() => {
    if (!mojaVrstica || !vrstice) return null
    const lestvica = vrstice.filter(
      (v) => v.spol === mojaVrstica.spol && v.rekreativec === mojaVrstica.rekreativec,
    )
    const i = lestvica.findIndex((v) => v.idIgralca === mojaVrstica.idIgralca)
    if (i < 0) return null
    const zacetek = Math.max(0, Math.min(i - odmik, lestvica.length - IGRALCEV))
    return {
      vrstice: lestvica
        .slice(zacetek, zacetek + IGRALCEV)
        .map((igralec, k) => ({ igralec, mesto: zacetek + k + 1 })),
      opis:
        `${mojaVrstica.spol === 'ZENSKI' ? 'Ženske' : 'Moški'} · `
        + `${mojaVrstica.rekreativec ? 'Rekreativci' : 'Člani'} · `
        + `tvoje mesto ${i + 1}. od ${lestvica.length}`,
    }
  }, [mojaVrstica, vrstice, odmik])

  /* Mesto je vedno mesto na CELI lestvici izbranega spola in skupine — filter
     zoži prikaz, ne razvrstitve. Zato ga pripnemo pred filtriranjem. */
  const prikazane = useMemo(() => {
    if (filter === 'okoli' && okolica) return okolica.vrstice
    const vse = (vrstice ?? [])
      .filter((v) => v.spol === lestvicaSklopa.spol && v.rekreativec === lestvicaSklopa.rekreativci)
      .map((igralec, indeks) => ({ igralec, mesto: indeks + 1 }))
    const zozene = vse.filter(({ igralec }) => {
      if (filter === 'mojeLige') return igralec.idjiLig.some((id) => spremljane.includes(id))
      if (filter === 'mojKlub') return mojIdKluba !== null && igralec.idKluba === mojIdKluba
      return true
    })
    return zozene.slice(0, IGRALCEV)
  }, [vrstice, filter, spremljane, mojIdKluba, okolica, lestvicaSklopa.spol, lestvicaSklopa.rekreativci])

  return (
    <div className="domov__sklop">
      <div className="naslovna-vrstica">
        <h2>Lestvica</h2>
        <div className="naslovna-vrstica__desno">
          {jePrijavljen && (
            <div className="izbirnik">
              {/* Igralec s Premium ima namesto vrha sebe in okolico; vrh je
                  en klik stran (»Cela lestvica«). */}
              {okolica ? (
                <Filter oznaka="Okoli mene" vrednost="okoli" izbrani={filter} naFilter={naFilter} />
              ) : (
                <Filter oznaka="Vsi igralci" vrednost="vsi" izbrani={filter} naFilter={naFilter} />
              )}
              <Filter oznaka="Moje lige" vrednost="mojeLige" izbrani={filter} naFilter={naFilter} />
              <Filter oznaka="Moj klub" vrednost="mojKlub" izbrani={filter} naFilter={naFilter} />
            </div>
          )}
          {/* Vsak del je svoj element in razmik dela gap: povezava je
             inline-flex, ta pa presledke med svojimi otroki poje — »Cela
             lestvica →« se je brez tega bralo kot »Celalestvica→«. */}
          <Link to="/lestvica" className="sekcija__meta domov__cela">
            <span>Cela</span>
            <span className="domov__cela-dolgo">lestvica</span>
            <span aria-hidden="true">→</span>
          </Link>
        </div>
      </div>

      {/* Katera lestvica je to: brez napisa bi gledalec ne vedel, da ženske
          niso izpuščene, ampak na drugi lestvici. */}
      <p className="domov__lestvica-opis">
        {filter === 'okoli' && okolica
          ? okolica.opis
          : `${lestvicaSklopa.spol === 'ZENSKI' ? 'Ženske' : 'Moški'} · ${lestvicaSklopa.rekreativci ? 'Rekreativci' : 'Člani'}`}
      </p>

      <NapakaPoizvedbe poizvedba={poizvedba} kaj="lestvice" />
      {poizvedba.isPending && <Skelet vrstic={5} />}

      {vrstice && prikazane.length === 0 && (
        <p className="domov__prazno">{praznoZaFilter(filter, mojIdKluba, spremljane)}</p>
      )}

      {prikazane.length > 0 && (
        /* Okno »Okoli mene« ima mesta tudi trimestna (104.), ozek stolpec mest
           na telefonu pa je meril za vrh lestvice - jih je stisnil ob premik. */
        <div
          className={
            'domov__lestvica' + (prikazane.some((p) => p.mesto >= 100) ? ' domov__lestvica--siroka' : '')
          }
        >
          <div className="domov__lestvica-vrstica domov__lestvica-vrstica--glava">
            <span>Mesto</span>
            <span>Premik</span>
            <span>Igralec</span>
            <span>Klub</span>
            <span className="domov__rating-glava">Rating 12 mesecev</span>
            <span className="domov__desno">Rating</span>
          </div>
          {prikazane.map(({ igralec, mesto }) => (
            <VrsticaLestvice
              igralec={igralec}
              mesto={mesto}
              jaz={igralec.idIgralca === mojIdIgralec}
              key={igralec.idIgralca}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function Filter({
  oznaka,
  vrednost,
  izbrani,
  naFilter,
}: {
  oznaka: string
  vrednost: FilterLestvice
  izbrani: FilterLestvice
  naFilter: (v: FilterLestvice) => void
}) {
  return (
    <button
      type="button"
      className={'izbirnik__gumb' + (izbrani === vrednost ? ' izbirnik__gumb--aktiven' : '')}
      aria-pressed={izbrani === vrednost}
      onClick={() => naFilter(vrednost)}
    >
      {oznaka}
    </button>
  )
}

function praznoZaFilter(
  filter: FilterLestvice,
  mojIdKluba: number | null,
  spremljane: number[],
): string {
  if (filter === 'mojKlub' && mojIdKluba === null) {
    return 'Kluba nimamo pri roki — prijavi se, da bo filter vedel, kateri je tvoj.'
  }
  if (filter === 'mojeLige' && spremljane.length === 0) {
    return 'Ne spremljaš še nobene lige.'
  }
  return 'Za ta izbor ni igralcev.'
}

function VrsticaLestvice({
  igralec,
  mesto,
  jaz,
}: {
  igralec: LestvicaIgralcaDto
  mesto: number
  jaz: boolean
}) {
  return (
    <Link
      to={`/igralci/${igralec.idIgralca}/profil`}
      className={'domov__lestvica-vrstica' + (jaz ? ' domov__lestvica-vrstica--jaz' : '')}
    >
      {/* Prva tri mesta so modra - edini poudarek v stolpcu mest. */}
      <span
        className={'domov__lestvica-mesto' + (mesto <= 3 ? ' domov__lestvica-mesto--vrh' : '')}
      >
        {mesto}.
      </span>
      <Premik mest={igralec.premik} />
      <span className="domov__lestvica-igralec">
        {igralec.ime} {igralec.priimek}
        {jaz && <span className="domov__oznaka-jaz">ti</span>}
      </span>
      <span className="domov__lestvica-klub">{igralec.klub ?? '—'}</span>
      <CrtaRatinga tocke={igralec.potekRatinga} />
      <span className="domov__lestvica-rating">{igralec.rating ?? '—'}</span>
    </Link>
  )
}

/* Premik mesta v zadnjem mesecu. Puščica je znak in ne ikona (ikone so
   prepovedane); bralniku zaslona pove isto beseda v aria-label. */
function Premik({ mest }: { mest: number | null }) {
  if (mest === null || mest === 0) {
    return (
      <span className="domov__premik" aria-label="brez spremembe mesta">
        –
      </span>
    )
  }
  const gor = mest > 0
  return (
    <span
      className={'domov__premik ' + (gor ? 'domov__premik--gor' : 'domov__premik--dol')}
      aria-label={
        gor
          ? `napredoval za ${mest} ${sklonMest(mest)}`
          : `nazadoval za ${-mest} ${sklonMest(-mest)}`
      }
    >
      <span aria-hidden="true">
        {gor ? '▲' : '▼'} {Math.abs(mest)}
      </span>
    </span>
  )
}

/* Črta gibanja Turnirko ratinga v zadnjem letu: sedem točk, brez osi in oznak —
   ob vrstici pove samo smer. Vrednosti so raztegnjene na celotno višino, ker
   je zanimiva oblika krivulje in ne absolutna razlika. */
function CrtaRatinga({ tocke }: { tocke: number[] }) {
  if (tocke.length < 2) return <span className="domov__rating" />
  const sirina = 140
  const visina = 24
  const rob = 3
  const naj = Math.max(...tocke)
  const naj2 = Math.min(...tocke)
  const razpon = naj - naj2
  const koordinate = tocke
    .map((v, i) => {
      const x = (i / (tocke.length - 1)) * sirina
      const y = razpon === 0
        ? visina / 2
        : visina - rob - ((v - naj2) / razpon) * (visina - 2 * rob)
      return `${Math.round(x)},${Math.round(y * 10) / 10}`
    })
    .join(' ')

  return (
    <svg
      className="domov__rating"
      width={sirina}
      height={visina}
      viewBox={`0 0 ${sirina} ${visina}`}
      aria-hidden="true"
    >
      <polyline points={koordinate} />
    </svg>
  )
}

/* ---------- Skupno ---------- */

/* Nalaganje: vrstice v višini pravih, brez vrtavk — stran naj se ob prihodu
   podatkov ne premakne. */
function Skelet({ vrstic }: { vrstic: number }) {
  return (
    <div className="domov__skelet" aria-hidden="true">
      {Array.from({ length: vrstic }, (_, i) => (
        <span className="domov__skelet-vrstica" key={i} />
      ))}
    </div>
  )
}

/* Slovnično pravilna oblika besede "liga" glede na število. */
function sklonLig(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'lige'
  if (ostanek === 2) return 'lig'
  if (ostanek === 3 || ostanek === 4) return 'lig'
  return 'lig'
}

/* "za 1 mesto", "za 2 mesti", "za 3 mesta", "za 5 mest". */
function sklonMest(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'mesto'
  if (ostanek === 2) return 'mesti'
  if (ostanek === 3 || ostanek === 4) return 'mesta'
  return 'mest'
}
