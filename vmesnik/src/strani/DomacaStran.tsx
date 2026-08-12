/* Domača (začetna) stran: štirje sklopi enega samega zapisnika — Turnirji in
   Moje lige drug ob drugem, pod njima lestvica čez vso širino in na dnu
   medsebojni izid dveh igralcev.

   Vrstice s sezono in števci pod mastheadom namenoma ni: stran naj se začne z
   vsebino, ne s povzetkom o sebi. Vse je bralno in vidno tudi gostom;
   spremljanje lig je edino dejanje in zahteva prijavo. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { domovApi, ligeApi, statistikaApi, turnirjiApi } from '../api/zahteve'
import type { DomovLigaDto, LestvicaIgralcaDto, TurnirDto } from '../api/tipi'
import { EnaNaEna } from '../komponente/EnaNaEna'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { PrijavaOkno } from '../komponente/PrijavaOkno'
import { ZnackaStatusa } from '../komponente/Znacka'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { oblikujDanKratekMesec, oblikujDatum, sklonIgralcev } from '../pomozno/oblikovanje'
import { ogledaneLige } from '../pomozno/ogledaneLige'

/* Koliko vrstic nosi posamezen sklop. Domača stran je povzetek: kdor hoče
   več, gre po povezavi v glavi sklopa. */
const TURNIRJEV = 4
const IGRALCEV = 8

type FilterLestvice = 'vsi' | 'mojeLige' | 'mojKlub'

/* Preklop spremljanja ene lige; "spremljam" je stanje PRED klikom. */
interface PreklopLige {
  id: number
  spremljam: boolean
}

export function DomacaStran() {
  const { uporabnik, mojIdIgralec } = useAvtentikacija()
  const odjemalec = useQueryClient()
  const [filter, nastaviFilter] = useState<FilterLestvice>('vsi')
  const [prijavaOdprta, nastaviPrijavaOdprta] = useState(false)

  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Izbor lig je last računa; gost ga nima, zato mu sklop pokaže lige, ki si
     jih je nazadnje ogledal (zapomni si jih njegov brskalnik). */
  const mojeLige = useQuery({
    queryKey: ['moje-lige'],
    queryFn: domovApi.mojeLige,
    enabled: uporabnik !== null,
  })
  const spremljane = uporabnik ? (mojeLige.data ?? []) : ogledaneLige()

  const povzetkiLig = useQuery({
    queryKey: ['domov-lige', spremljane],
    queryFn: () => domovApi.lige(spremljane),
  })

  /* Optimistična posodobitev: kvadratek se prevesi takoj, ob napaki se izbor
     povrne na zadnje potrjeno stanje strežnika. */
  const preklop = useMutation<void, unknown, PreklopLige, { prejsnje: number[] }>({
    mutationFn: async ({ id, spremljam }) => {
      if (spremljam) await domovApi.nehajSpremljati(id)
      else await domovApi.spremljaj(id)
    },
    onMutate: async ({ id, spremljam }) => {
      await odjemalec.cancelQueries({ queryKey: ['moje-lige'] })
      const prejsnje = odjemalec.getQueryData<number[]>(['moje-lige']) ?? []
      odjemalec.setQueryData<number[]>(
        ['moje-lige'],
        spremljam ? prejsnje.filter((v) => v !== id) : [...prejsnje, id],
      )
      return { prejsnje }
    },
    onError: (_napaka, _vnos, kontekst) => {
      if (kontekst) odjemalec.setQueryData(['moje-lige'], kontekst.prejsnje)
    },
    onSettled: () => odjemalec.invalidateQueries({ queryKey: ['moje-lige'] }),
  })

  function preklopiLigo(id: number) {
    if (!uporabnik) {
      nastaviPrijavaOdprta(true)
      return
    }
    preklop.mutate({ id, spremljam: spremljane.includes(id) })
  }

  /* Vrstni red na domači strani je vrstni red dogajanja: kar teče, je zgoraj. */
  const prikazaniTurnirji = useMemo(() => razvrstiTurnirje(turnirji.data), [turnirji.data])

  const mojaVrstica = lestvica.data?.find((v) => v.idIgralca === mojIdIgralec) ?? null
  const mojIdKluba = uporabnik?.idKlub ?? mojaVrstica?.idKluba ?? null

  /* Merilo je, kaj sklop DEJANSKO kaže, in ne izbor: gost izbora nima, pa mu
     vseeno pokažemo lige v teku — vrstica "ne spremljaš N lig" bi jih sicer
     štela med nespremljane, čeprav so tik nad njo. */
  const prikazaneLige = (povzetkiLig.data ?? []).map((l) => l.id)
  const nespremljanihVTeku = (lige.data ?? []).filter(
    (l) => l.status === 'V_TEKU' && !prikazaneLige.includes(l.id),
  ).length

  return (
    <section className="domov">
      <div className="domov__vrh">
        <div className="domov__sklop">
          <div className="naslovna-vrstica">
            <h2>Turnirji</h2>
            <Link to="/turnirji" className="sekcija__meta">
              Vsi →
            </Link>
          </div>
          <NapakaPoizvedbe poizvedba={turnirji} kaj="turnirjev" />
          {turnirji.isPending && <Skelet vrstic={3} />}
          {turnirji.data && prikazaniTurnirji.length === 0 && (
            <p className="domov__prazno">Ni turnirjev.</p>
          )}
          <div className="domov__seznam">
            {prikazaniTurnirji.map((t) => (
              <VrsticaTurnirja turnir={t} key={t.id} />
            ))}
          </div>
        </div>

        <div className="domov__sklop">
          <div className="naslovna-vrstica">
            <h2>Moje lige</h2>
            <Link to="/lige" className="sekcija__meta">
              Uredi izbor →
            </Link>
          </div>
          <NapakaPoizvedbe poizvedba={povzetkiLig} kaj="lig" />
          {povzetkiLig.isPending && <Skelet vrstic={3} />}
          {povzetkiLig.data && povzetkiLig.data.length === 0 && (
            <p className="domov__prazno">Ne spremljaš še nobene lige.</p>
          )}
          <div className="domov__seznam">
            {(povzetkiLig.data ?? []).map((liga) => (
              <KarticaLige
                liga={liga}
                key={liga.id}
                spremljam={spremljane.includes(liga.id)}
                naPreklop={() => preklopiLigo(liga.id)}
              />
            ))}
            {nespremljanihVTeku > 0 && (
              <div className="domov__liga-dodaj">
                <span className="domov__namig">
                  Ne spremljaš {nespremljanihVTeku} {sklonLig(nespremljanihVTeku)}, ki
                  {nespremljanihVTeku === 1 ? ' je' : ' so'} v teku.
                </span>
                <Link to="/lige" className="gumb gumb--majhen">
                  Dodaj ligo
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>

      <SklopLestvica
        vrstice={lestvica.data}
        poizvedba={lestvica}
        filter={filter}
        naFilter={nastaviFilter}
        mojIdIgralec={mojIdIgralec}
        mojIdKluba={mojIdKluba}
        spremljane={spremljane}
      />

      <div className="domov__sklop">
        <div className="naslovna-vrstica">
          <h2>Ena na ena</h2>
        </div>
        <EnaNaEna />
      </div>

      {prijavaOdprta && (
        <PrijavaOkno zacetniNacin="prijava" onZapri={() => nastaviPrijavaOdprta(false)} />
      )}
    </section>
  )
}

/* ---------- Turnirji ---------- */

/* V teku najprej, nato priprava, na koncu zaključeni. */
const VRSTNI_RED_STATUSA = { V_TEKU: 0, PRIPRAVA: 1, ZAKLJUCEN: 2 }

function razvrstiTurnirje(turnirji: TurnirDto[] | undefined): TurnirDto[] {
  return [...(turnirji ?? [])]
    .sort((a, b) => VRSTNI_RED_STATUSA[a.status] - VRSTNI_RED_STATUSA[b.status] || b.id - a.id)
    .slice(0, TURNIRJEV)
}

function VrsticaTurnirja({ turnir }: { turnir: TurnirDto }) {
  const vTeku = turnir.status === 'V_TEKU'
  const spalica = vTeku && turnir.vsehTekem > 0
  const odstotek = turnir.vsehTekem > 0
    ? Math.round((turnir.odigranihTekem / turnir.vsehTekem) * 100)
    : 0

  return (
    <Link to={`/turnirji/${turnir.id}`} className="domov__turnir">
      <span className="domov__turnir-glava">
        <span className="domov__turnir-ime">{turnir.ime}</span>
        <ZnackaStatusa status={turnir.status} />
      </span>
      <span className="domov__turnir-opis">{opisTurnirja(turnir)}</span>
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

/* Podnaslov vrstice: kraj in nato tisto, kar o turnirju v tem stanju največ
   pove — koliko jih igra in kje je, kdaj se začne oz. kdo ga je dobil. */
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
  } else if (turnir.zmagovalec) {
    deli.push(`zmagal ${turnir.zmagovalec}`)
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
  spremljam: boolean
  naPreklop: () => void
}) {
  return (
    <div className="domov__liga">
      <div className="domov__liga-glava">
        <span className="domov__liga-naslov">
          <button
            type="button"
            className={'domov__kljukica' + (spremljam ? ' domov__kljukica--polna' : '')}
            aria-pressed={spremljam}
            aria-label={spremljam ? `Nehaj spremljati ${liga.ime}` : `Spremljaj ${liga.ime}`}
            onClick={naPreklop}
          />
          <Link to={`/lige/${liga.id}`} className="domov__liga-ime">
            {liga.ime}
          </Link>
        </span>
        {liga.vsehKol > 0 && (
          <span className="domov__liga-kolo">
            {liga.odigranihKol}. od {liga.vsehKol} kol
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
          {liga.vrh.map((v) => (
            <div className="domov__ekipe-vrstica" key={v.mesto}>
              <span className="domov__ekipe-mesto">{v.mesto}.</span>
              <span className="domov__ekipe-ime">{v.ekipa}</span>
              <span className="domov__ekipe-odigrane">{v.odigrane}</span>
              <span className="domov__ekipe-tocke">{v.tocke}</span>
            </div>
          ))}
        </div>
      )}

      {liga.naslednje && (
        <div className="domov__liga-noga">
          Naslednje kolo{' '}
          {liga.naslednje.datum
            ? oblikujDanKratekMesec(liga.naslednje.datum)
            : `${liga.naslednje.kolo}.`}{' '}
          · {liga.naslednje.domaci} – {liga.naslednje.gost}
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
}: {
  vrstice: LestvicaIgralcaDto[] | undefined
  poizvedba: { error: unknown; isFetching: boolean; refetch: () => unknown; isPending: boolean }
  filter: FilterLestvice
  naFilter: (v: FilterLestvice) => void
  mojIdIgralec: number | null
  mojIdKluba: number | null
  spremljane: number[]
}) {
  /* Mesto je vedno mesto na CELI lestvici — filter zoži prikaz, ne
     razvrstitve. Zato ga pripnemo pred filtriranjem. */
  const prikazane = useMemo(() => {
    const vse = (vrstice ?? []).map((igralec, indeks) => ({ igralec, mesto: indeks + 1 }))
    const zozene = vse.filter(({ igralec }) => {
      if (filter === 'mojeLige') return igralec.idjiLig.some((id) => spremljane.includes(id))
      if (filter === 'mojKlub') return mojIdKluba !== null && igralec.idKluba === mojIdKluba
      return true
    })
    return zozene.slice(0, IGRALCEV)
  }, [vrstice, filter, spremljane, mojIdKluba])

  return (
    <div className="domov__sklop">
      <div className="naslovna-vrstica">
        <h2>Lestvica</h2>
        <div className="naslovna-vrstica__desno">
          <div className="izbirnik">
            <Filter oznaka="Vsi igralci" vrednost="vsi" izbrani={filter} naFilter={naFilter} />
            <Filter oznaka="Moje lige" vrednost="mojeLige" izbrani={filter} naFilter={naFilter} />
            <Filter oznaka="Moj klub" vrednost="mojKlub" izbrani={filter} naFilter={naFilter} />
          </div>
          <Link to="/lestvica" className="sekcija__meta domov__cela">
            Cela<span className="domov__cela-dolgo"> lestvica</span> →
          </Link>
        </div>
      </div>

      <NapakaPoizvedbe poizvedba={poizvedba} kaj="lestvice" />
      {poizvedba.isPending && <Skelet vrstic={5} />}

      {vrstice && prikazane.length === 0 && (
        <p className="domov__prazno">{praznoZaFilter(filter, mojIdKluba, spremljane)}</p>
      )}

      {prikazane.length > 0 && (
        <div className="domov__lestvica">
          <div className="domov__lestvica-vrstica domov__lestvica-vrstica--glava">
            <span>Mesto</span>
            <span>Premik</span>
            <span>Igralec</span>
            <span>Klub</span>
            <span className="domov__elo-glava">Elo 12 mesecev</span>
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
      <CrtaElo tocke={igralec.eloZgodovina} />
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

/* Črta gibanja klubskega ELO v zadnjem letu: sedem točk, brez osi in oznak —
   ob vrstici pove samo smer. Vrednosti so raztegnjene na celotno višino, ker
   je zanimiva oblika krivulje in ne absolutna razlika. */
function CrtaElo({ tocke }: { tocke: number[] }) {
  if (tocke.length < 2) return <span className="domov__elo" />
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
      className="domov__elo"
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
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'lig'
  const mod10 = n % 10
  if (mod10 === 1) return 'lige'
  if (mod10 === 2) return 'lig'
  if (mod10 === 3 || mod10 === 4) return 'lig'
  return 'lig'
}

/* "za 1 mesto", "za 2 mesti", "za 3 mesta", "za 5 mest". */
function sklonMest(n: number): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'mest'
  const mod10 = n % 10
  if (mod10 === 1) return 'mesto'
  if (mod10 === 2) return 'mesti'
  if (mod10 === 3 || mod10 === 4) return 'mesta'
  return 'mest'
}
