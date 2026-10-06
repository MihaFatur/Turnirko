/* SV regija: pogled »Nivoji« po žrebu.

   Dogodek ima nivoje (težavnostne skupine). Vsak nivo ima skupine »vsak z
   vsakim« in žreba: glavni (1.–2. iz vsake skupine) in tolažilni (3.–4.).
   Žreb se igra za VSA mesta: poraženca vsakega kola se srečata med seboj, do
   zadnjega para - zato žreb ni ena mreža, ampak drevo, ki ga stran izriše po
   kolih (stolpcih), v vsakem stolpcu pa po razponih mest.

   Nivoji tečejo neodvisno: žreb nivoja nastane, ko so odigrane skupine TEGA
   nivoja, ne celotnega dogodka. Dokler nastane, žreb stoji kot prazen razdelek
   z opisom, kdaj bo na vrsti.

   Organizator vidi dejanja samo, dokler jih strežnik dovoljuje (žreb skupin
   dokler nobena tekma ni začeta, razpored mest dokler se žreb ni začel):
   trajno ugasnjen gumb bi bil sredi turnirja samo grožnja. */
import { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { MrezaDto, SkupinaDto, SvNivoDto, SvZrebDto } from '../api/tipi'
import { sklonIgralcev, sklonSkupin } from '../pomozno/oblikovanje'
import { crkaSkupine, opisRazpona } from '../pomozno/svRegija'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { SkupinaVrstica } from './SkupinaVrstica'
import { SporociloNapake } from './SporociloNapake'
import { SvDrevo } from './SvDrevo'
import { SvSkupineOkno } from './SvSkupineOkno'
import { SvZrebOkno } from './SvZrebOkno'
import type { KlikTekme } from './TekmaKartica'
import { VsebinaSkupine, opisSkupine } from './VsebinaSkupine'

interface Lastnosti {
  podatki: MrezaDto
  klik?: KlikTekme
  osvezi: () => void
  /* Lastnik turnirja ali admin; dejanja sicer ni. */
  smem: boolean
}

export function SvRegijaDel({ podatki, klik, osvezi, smem }: Lastnosti) {
  const sv = podatki.svRegija
  const [izbran, nastaviIzbran] = useState<number | null>(null)
  const [urejamSkupine, nastaviUrejamSkupine] = useState(false)
  const [razveljavljam, nastaviRazveljavljam] = useState(false)

  const razveljavi = useMutation({
    mutationFn: () => svApi.razveljavi(podatki.dogodek.id),
    onSuccess: osvezi,
  })

  /* Napredek po nivojih: tekme skupin in žrebov nivoja. */
  const napredek = useMemo(() => {
    const nivoPoSkupini = new Map(podatki.skupine.map((s) => [s.id, s.nivo]))
    const nivoPoZrebu = new Map<number, number>()
    sv?.nivoji.forEach((n) =>
      n.zrebi.forEach((z) => {
        if (z.id !== null) nivoPoZrebu.set(z.id, n.nivo)
      }),
    )
    const zbir = new Map<number, { vseh: number; odigranih: number }>()
    for (const t of podatki.tekme) {
      const nivo =
        t.idSkupina !== null
          ? nivoPoSkupini.get(t.idSkupina)
          : t.idZreb !== null
            ? nivoPoZrebu.get(t.idZreb)
            : undefined
      if (nivo === undefined) continue
      const stanje = zbir.get(nivo) ?? { vseh: 0, odigranih: 0 }
      stanje.vseh += 1
      if (t.status === 'KONCANA') stanje.odigranih += 1
      zbir.set(nivo, stanje)
    }
    return zbir
  }, [podatki.skupine, podatki.tekme, sv])

  if (!sv || !sv.skupineZrebane || sv.nivoji.length === 0) {
    return <p className="obvestilo">Skupine še niso izžrebane.</p>
  }

  /* Privzeto prvi nivo, ki še ni v celoti odigran - tam se turnir dogaja. */
  const privzeti =
    sv.nivoji.find((n) => {
      const s = napredek.get(n.nivo)
      return s !== undefined && s.odigranih < s.vseh
    })?.nivo ?? sv.nivoji[0].nivo
  const nivoZaPrikaz = sv.nivoji.some((n) => n.nivo === izbran) ? (izbran as number) : privzeti
  const nivo = sv.nivoji.find((n) => n.nivo === nivoZaPrikaz) as SvNivoDto
  const stanje = napredek.get(nivo.nivo) ?? { vseh: 0, odigranih: 0 }
  const skupineNivoja = podatki.skupine
    .filter((s) => s.stopnja === 1 && s.nivo === nivo.nivo)
    .sort((a, b) => a.oznaka.localeCompare(b.oznaka, 'sl'))

  return (
    <div>
      {smem && sv.skupineUredljive && (
        <div className="sv-orodja">
          <p className="namig">
            Nobena tekma še ni začeta, zato je skupine še mogoče popraviti ali žreb razveljaviti.
          </p>
          <div className="zreb__krmila">
            <button type="button" className="gumb gumb--majhen" onClick={() => nastaviUrejamSkupine(true)}>
              Popravi skupine
            </button>
            <button
              type="button"
              className="gumb gumb--majhen gumb--nevaren"
              onClick={() => nastaviRazveljavljam(true)}
            >
              Razveljavi žreb
            </button>
          </div>
          <SporociloNapake napaka={razveljavi.error} />
        </div>
      )}

      {sv.nivoji.length > 1 && (
        <div className="mreza-krmar">
          <div className="izbirnik">
            {sv.nivoji.map((n) => {
              const s = napredek.get(n.nivo)
              return (
                <button
                  type="button"
                  key={n.nivo}
                  className={'izbirnik__gumb' + (nivoZaPrikaz === n.nivo ? ' izbirnik__gumb--aktiven' : '')}
                  aria-pressed={nivoZaPrikaz === n.nivo}
                  onClick={() => nastaviIzbran(n.nivo)}
                >
                  Nivo {n.nivo}
                  {s ? ` · ${s.odigranih}/${s.vseh}` : ''}
                </button>
              )
            })}
          </div>
        </div>
      )}

      <div className="naslovna-vrstica">
        <h2>Nivo {nivo.nivo}</h2>
        <span className="sekcija__meta">
          {opisRazpona(nivo.odMesta, nivo.doMesta)} · {nivo.velikost} {sklonIgralcev(nivo.velikost)} ·{' '}
          {skupineNivoja.length} {sklonSkupin(skupineNivoja.length)}
          {stanje.vseh > 0 && ` · odigranih ${stanje.odigranih} / ${stanje.vseh}`}
        </span>
      </div>

      <SkupineNivoja
        skupine={skupineNivoja}
        podatki={podatki}
        klik={klik}
        zrebi={nivo.zrebi}
        rangov={nivo.rangovVZreb}
      />

      {nivo.zrebi.length === 0 && (
        <p className="namig">
          Nivo ima eno samo skupino, zato žreba ni: mesta določi razvrstitev v skupini.
        </p>
      )}

      {nivo.zrebi.length > 0 && (
        <ZrebiNivoja
          key={nivo.nivo}
          zrebi={nivo.zrebi}
          podatki={podatki}
          klik={klik}
          smem={smem}
          osvezi={osvezi}
        />
      )}

      {urejamSkupine && (
        <SvSkupineOkno
          idDogodka={podatki.dogodek.id}
          podatki={podatki}
          izhodisce="trenutno"
          onZapri={() => nastaviUrejamSkupine(false)}
          onShranjeno={osvezi}
        />
      )}

      {razveljavljam && (
        <PotrditvenoOkno
          naslov="Razveljavitev žreba"
          sporocilo={
            'Skupine, njihove tekme in žrebi se izbrišejo, dogodek se vrne v pripravo.' +
            ' Prijave in jakostni vrstni red ostanejo. Razveljavim žreb?'
          }
          besedaPotrditve="Razveljavi žreb"
          onPotrdi={() => razveljavi.mutate()}
          onZapri={() => nastaviRazveljavljam(false)}
        />
      )}
    </div>
  )
}

/* ---------- Skupine nivoja ---------- */

/* Skupine kot zložljive vrstice, odprta je največ ena (kot pri drugih sistemih
   s skupinami). Privzeto gresta prva dva v vsaki skupini v glavni žreb, tretji in
   četrti v tolažilnega - lestvica to pove z barvo vrstice in legendo. Kadar ima
   nivo en rang v žrebu, je vsak rang svoj žreb: barvi pokrijeta prva dva ranga,
   ostale pove vrstica pod naslovom. */
function SkupineNivoja({
  skupine,
  podatki,
  klik,
  zrebi,
  rangov,
}: {
  skupine: SkupinaDto[]
  podatki: MrezaDto
  klik?: KlikTekme
  zrebi: SvZrebDto[]
  rangov: number
}) {
  const [odprta, nastaviOdprto] = useState<number | null>(null)
  if (skupine.length === 0) {
    return <p className="obvestilo">Nivo nima skupin.</p>
  }
  return (
    <div>
      <p className="podnaslov-sekcije">Skupine</p>
      {rangov === 1 && zrebi.length > 1 && (
        <p className="namig">
          Vsak rang iz skupine ima svoj žreb:{' '}
          {zrebi
            .map((z, i) => `${i + 1}. mesto → ${opisRazpona(z.prvoMesto, z.zadnjeMesto)}`)
            .join(', ')}
          .
        </p>
      )}
      <div className="skupine-seznam">
        {skupine.map((skupina) => (
          <SkupinaVrstica
            key={skupina.id}
            oznaka={crkaSkupine(skupina.oznaka)}
            opis={opisSkupine(skupina, false)}
            odprta={odprta === skupina.id}
            naPreklop={() => nastaviOdprto((prej) => (prej === skupina.id ? null : skupina.id))}
          >
            <VsebinaSkupine
              skupina={skupina}
              tekme={podatki.tekme.filter((t) => t.faza === 'SKUPINA' && t.idSkupina === skupina.id)}
              klik={klik}
              napreduje={zrebi.length > 0 ? rangov : undefined}
              tolazilni={zrebi.length > 1 ? 2 * rangov : undefined}
              legenda={rangov === 1 ? '1. mesto: glavni žreb' : '1.–2. mesto: glavni žreb'}
              legendaTolazilni={
                zrebi.length > 1
                  ? rangov === 1
                    ? '2. mesto: tolažilni žreb'
                    : '3.–4. mesto: tolažilni žreb'
                  : undefined
              }
              ekipno={false}
            />
          </SkupinaVrstica>
        ))}
      </div>
    </div>
  )
}

/* ---------- Žrebi nivoja ---------- */

/* Žrebi kot zložljive vrstice, tako kot skupine: štirje žrebi s po 12 tekmami bi
   bili en sam dolg zaslon. Odprt je največ eden; privzeto prvi (glavni žreb), ker
   se tam odloča o najboljših mestih. Vrstica pove mesta, ki jih žreb odloča, in -
   ko je žreb odigran - kdo je zmagal, da se ne da prebrati, ne da bi ga odprl. */
function ZrebiNivoja({
  zrebi,
  podatki,
  klik,
  smem,
  osvezi,
}: {
  zrebi: SvZrebDto[]
  podatki: MrezaDto
  klik?: KlikTekme
  smem: boolean
  osvezi: () => void
}) {
  const [odprt, nastaviOdprt] = useState<number | null>(zrebi[0].indeks)
  return (
    <div className="sv-zrebi">
      <p className="podnaslov-sekcije">Žrebi za vsa mesta</p>
      <div className="skupine-seznam">
        {zrebi.map((zreb) => (
          <SkupinaVrstica
            key={zreb.indeks}
            oznaka={`${zreb.prvoMesto}.`}
            opis={opisZreba(zreb, podatki)}
            odprta={odprt === zreb.indeks}
            naPreklop={() => nastaviOdprt((prej) => (prej === zreb.indeks ? null : zreb.indeks))}
          >
            <VsebinaZreba zreb={zreb} podatki={podatki} klik={klik} smem={smem} osvezi={osvezi} />
          </SkupinaVrstica>
        ))}
      </div>
    </div>
  )
}

/* »Glavni žreb · 1.–8. mesto · 8 igralcev« in, ko je žreb odigran, zmagovalec. */
function opisZreba(zreb: SvZrebDto, podatki: MrezaDto): string {
  const osnova = `${zreb.ime} · ${opisRazpona(zreb.prvoMesto, zreb.zadnjeMesto)} · ${zreb.stUdelezencev} ${sklonIgralcev(zreb.stUdelezencev)}`
  if (!zreb.zgrajen) return osnova
  const tekme = podatki.tekme.filter((t) => t.idZreb === zreb.id)
  const odigran = tekme.length > 0 && tekme.every((t) => t.status === 'KONCANA')
  const zmagovalec = odigran
    ? podatki.prijave.find((p) => p.koncnoMesto === zreb.prvoMesto)
    : undefined
  return zmagovalec ? `${osnova} · ${zreb.prvoMesto}. ${zmagovalec.polnoIme}` : osnova
}

/* Vsebina odprtega žreba: dejanja organizatorja in drevo za vsa mesta. */
function VsebinaZreba({
  zreb,
  podatki,
  klik,
  smem,
  osvezi,
}: {
  zreb: SvZrebDto
  podatki: MrezaDto
  klik?: KlikTekme
  smem: boolean
  osvezi: () => void
}) {
  const [osvetljena, nastaviOsvetljeno] = useState<number | null>(null)
  const [urejam, nastaviUrejam] = useState(false)
  const [potrjujem, nastaviPotrjujem] = useState(false)

  const znova = useMutation({
    mutationFn: () => svApi.znova(zreb.id as number),
    onSuccess: osvezi,
  })

  const tekme = useMemo(
    () => podatki.tekme.filter((t) => zreb.id !== null && t.idZreb === zreb.id),
    [podatki.tekme, zreb.id],
  )
  /* Pri enem ali dveh udeležencih ni kaj razporejati (en sam igralec ali ena tekma). */
  const uredljiv = smem && zreb.uredljiv && zreb.id !== null && zreb.stUdelezencev > 2

  return (
    <div className="skupina-vsebina__cela">
      {(uredljiv || zreb.rocni) && (
        <div className="sv-zreb__glava">
          <p className="namig">
            {zreb.rocni ? 'Razpored mest je vpisal organizator, žreb ni bil naključen.' : ''}
          </p>
          {uredljiv && (
            <span className="sv-zreb__dejanja">
              <button type="button" className="gumb gumb--majhen" onClick={() => nastaviUrejam(true)}>
                Uredi razporeditev
              </button>
              <button type="button" className="gumb gumb--majhen" onClick={() => nastaviPotrjujem(true)}>
                Žrebaj znova
              </button>
            </span>
          )}
        </div>
      )}

      <SporociloNapake napaka={znova.error} />

      {!zreb.zgrajen ? (
        <p className="obvestilo">
          Žreb nastane, ko so odigrane vse skupine nivoja. Vanj gresta
          {zreb.indeks === 0 ? ' prvo- in drugouvrščeni' : zreb.indeks === 1 ? ' tretje- in četrtouvrščeni' : ' naslednja dva'}{' '}
          vsake skupine.
        </p>
      ) : tekme.length === 0 ? (
        <p className="obvestilo">
          {zreb.stUdelezencev === 1
            ? 'V žrebu je en sam igralec, zato tekem ni: mesto je določeno.'
            : 'V žrebu ni tekem.'}
        </p>
      ) : (
        <SvDrevo
          tekme={tekme}
          zreb={zreb}
          klik={klik}
          osvetljena={osvetljena}
          naOsvetlitev={nastaviOsvetljeno}
        />
      )}

      {urejam && (
        <SvZrebOkno
          zreb={zreb}
          podatki={podatki}
          onZapri={() => nastaviUrejam(false)}
          onShranjeno={osvezi}
        />
      )}

      {potrjujem && (
        <PotrditvenoOkno
          naslov="Žreb znova"
          sporocilo={
            `Mesta v žrebu se znova razporedijo po pravilih žreba (${zreb.ime.toLowerCase()}).` +
            ' Prejšnja razporeditev, tudi ročna, se zavrže. Žrebam znova?'
          }
          besedaPotrditve="Žrebaj znova"
          onPotrdi={() => znova.mutate()}
          onZapri={() => nastaviPotrjujem(false)}
        />
      )}
    </div>
  )
}
