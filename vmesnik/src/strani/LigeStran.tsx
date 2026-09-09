/* Seznam lig + ustvarjanje nove lige (prilagodljiva konfiguracija).
   Obrazec s pravili je skupen z urejanjem — glej LigaObrazecOkno.

   Na telefonu je vrstica DRUGA vsebina in ne le ožja mreža: namesto petih
   stolpcev (ime s podrobnostjo, ekipe, format srečanja, prehodi, status) nosi
   ime, eno mono vrstico in status. Število ekip, format in »1 ↑ · 2 ↓« so
   odpadli — dobijo se na strani lige — zato vrstica meri 68 px namesto ~150,
   kjer je značka statusa padla v svojo tretjo vrsto.

   Nad seznamom stoji pas »V teku«: gledalec (uporabnik št. 1) pride po ligo,
   ki igra danes, in te vrstice zato nosijo še kolo in palico napredka.
   Dejanje »+ Nova liga« se je preselilo v lepljivo glavo.

   Filtri so eno okno z vsemi merili (stanje, sezona, kategorija, format,
   organizator) in ob njem izbor razvrstitve — glej komponente/Filtri. Prej
   jih je namizje sploh ni imelo (seznam je štel vse uvožene sezone naenkrat,
   torej stotine lig), telefon pa le štiri gumbe po statusu. Sezona je tu
   pravo polje lige in ne izpeljanka iz datuma kot pri turnirjih. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto } from '../api/tipi'
import {
  OZNAKE_FORMAT,
  OZNAKE_SPOL_KATEGORIJA,
  OZNAKE_STATUS_TEKMOVANJA,
  type FormatSrecanja,
  type SpolKategorija,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  KrmilaSeznama,
  poSeznamu,
  poVrednostiNazaj,
  useFiltri,
  type Razvrstitev,
  type SkupinaFiltra,
} from '../komponente/Filtri'
import { GlavaDejanja } from '../komponente/GlavaTelefona'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { PalicaMobi } from '../komponente/Napredek'
import { StatusMobi, ZnackaStatusa } from '../komponente/Znacka'
import { intervalOsvezevanja, uraOsvezitve } from '../pomozno/osvezevanje'
import { useTelefon } from '../pomozno/telefon'

/* Merila nad seznamom lig. Sezona je prosto besedilo (uvoz jo je zapisal v
   dveh oblikah — »2023/24« in »2024-2025«), zato se ne razlaga, le razvrsti
   po vodilni letnici navzdol. */
const SKUPINE: SkupinaFiltra<LigaDto>[] = [
  {
    kljuc: 'stanje',
    oznaka: 'Stanje',
    vrednost: (l) => l.status,
    napis: (v) => OZNAKE_STATUS_TEKMOVANJA[v as keyof typeof OZNAKE_STATUS_TEKMOVANJA],
    vrstniRed: poSeznamu(['V_TEKU', 'PRIPRAVA', 'ZAKLJUCEN']),
  },
  {
    kljuc: 'sezona',
    oznaka: 'Sezona',
    vrednost: (l) => l.sezona,
    vrstniRed: poVrednostiNazaj,
  },
  {
    kljuc: 'kategorija',
    oznaka: 'Kategorija',
    vrednost: (l) => l.spolKategorija,
    napis: (v) => OZNAKE_SPOL_KATEGORIJA[v as SpolKategorija],
    vrstniRed: poSeznamu(['MOSKI', 'ZENSKE', 'MESANO']),
  },
  {
    kljuc: 'format',
    oznaka: 'Format srečanja',
    vrednost: (l) => l.formatSrecanja,
    napis: (v) => OZNAKE_FORMAT[v as FormatSrecanja],
  },
  { kljuc: 'organizator', oznaka: 'Organizator', vrednost: (l) => l.klubLastnik },
]

const RAZVRSTITVE: Razvrstitev<LigaDto>[] = [
  {
    kljuc: 'sezona',
    oznaka: 'Po sezoni',
    primerjaj: (a, b) =>
      (b.sezona ?? '').localeCompare(a.sezona ?? '', 'sl', { numeric: true }) ||
      a.ime.localeCompare(b.ime, 'sl'),
  },
  { kljuc: 'ime', oznaka: 'Po imenu', primerjaj: (a, b) => a.ime.localeCompare(b.ime, 'sl') },
  {
    kljuc: 'ekipe',
    oznaka: 'Največ ekip',
    primerjaj: (a, b) => b.steviloEkip - a.steviloEkip || a.ime.localeCompare(b.ime, 'sl'),
  },
]

export function LigeStran() {
  const odjemalec = useQueryClient()
  const { smeUstvarjati } = useAvtentikacija()
  const jeTelefon = useTelefon()
  /* Dokler katera od lig teče, se seznam osvežuje sam - pas "V teku" nosi
     napredek, ki se med večerom premika. */
  const lige = useQuery({
    queryKey: ['lige'],
    queryFn: ligeApi.seznam,
    refetchInterval: (poizvedba) =>
      poizvedba.state.data?.some((l) => l.status === 'V_TEKU')
        ? intervalOsvezevanja('V_TEKU')
        : false,
  })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  const vse = useMemo(() => lige.data ?? [], [lige.data])
  const filtri = useFiltri(vse, SKUPINE, RAZVRSTITVE)
  const prikazane = filtri.prikazani
  const vTeku = useMemo(() => vse.filter((l) => l.status === 'V_TEKU'), [vse])
  const osvezenoOb = uraOsvezitve(lige.dataUpdatedAt)

  const krmila = (
    <KrmilaSeznama
      stanje={filtri}
      razvrstitve={RAZVRSTITVE}
      naslovOkna="Lige"
      imeZadetkov={ligTekst}
    />
  )

  /* Stanja, ki ju rišemo enako na obeh širinah. */
  const stanje = (
    <>
      <NapakaPoizvedbe poizvedba={lige} kaj="lig" />
      {lige.isPending && <p className="obvestilo">Nalaganje …</p>}

      {lige.data && vse.length === 0 && (
        <p className="obvestilo">Ni še nobene lige. Ustvari prvo z gumbom »+ Nova liga«.</p>
      )}

      {vse.length > 0 && prikazane.length === 0 && (
        <p className="obvestilo">
          Izbranim merilom ne ustreza nobena liga.{' '}
          <button type="button" className="povezava-gumb" onClick={filtri.pocisti}>
            Počisti filtre
          </button>
        </p>
      )}
    </>
  )

  const obrazec = odprtObrazec && (
    <LigaObrazecOkno
      onZapri={() => nastaviOdprtObrazec(false)}
      onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['lige'] })}
    />
  )

  if (jeTelefon) {
    return (
      <section>
        {/* Dejanje urejevalca stoji v lepljivi glavi in ne nad seznamom:
            gledalec pride po lige, ne po gumb. */}
        <GlavaDejanja>
          {smeUstvarjati && (
            <button
              type="button"
              className="glava-telefon__gumb"
              onClick={() => nastaviOdprtObrazec(true)}
            >
              + Liga
            </button>
          )}
        </GlavaDejanja>

        <div>
          <span className="naslov-mobi__nad">Ekipna tekmovanja</span>
          <h1 className="naslov-mobi naslov-mobi--seznam">Lige</h1>

          {vse.length > 0 && krmila}
        </div>

        {vTeku.length > 0 && (
          <div>
            {/* Ura osvezitve je odsla iz vrstice krmil sem: napredek se premika
                samo pri ligah, ki igrajo. */}
            <div className="naslovna-mobi">
              <h2>V teku</h2>
              <span className="naslovna-mobi__stevec naslovna-mobi__stevec--drobno">
                {vTeku.length}
                {osvezenoOb && ` · osveženo ${osvezenoOb}`}
              </span>
            </div>
            <div className="seznam-mobi seznam-mobi--odmik">
              {vTeku.map((liga, indeks) => (
                <VrsticaLigeMobi
                  key={liga.id}
                  liga={liga}
                  vPasu
                  poudarjena={indeks === 0}
                />
              ))}
            </div>
          </div>
        )}

        <div>
          <div className="naslovna-mobi">
            <h2>Vse lige</h2>
            <span className="naslovna-mobi__stevec">
              {prikazane.length === vse.length
                ? vse.length
                : `${prikazane.length} od ${vse.length}`}
            </span>
          </div>
          {stanje}
          <div className="seznam-mobi seznam-mobi--odmik">
            {prikazane.map((liga) => (
              <VrsticaLigeMobi key={liga.id} liga={liga} />
            ))}
          </div>
        </div>

        {obrazec}
      </section>
    )
  }

  return (
    <section>
      {/* Glave strani (nadnaslov, naslov, uvod) ni: kje smo, pove navigacija,
          in seznam se sme začeti z vsebino. Ostane le dejanje urejevalca. */}
      {smeUstvarjati && (
        <div className="stran-dejanja">
          <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
            + Nova liga
          </button>
        </div>
      )}

      <div>
        <div className="naslovna-vrstica">
          <h2>Vse lige</h2>
          {vse.length > 0 && (
            <span className="sekcija__meta">
              {prikazane.length === vse.length
                ? `${vse.length} ${ligTekst(vse.length)}`
                : `Prikazanih ${prikazane.length} od ${vse.length}`}
            </span>
          )}
        </div>

        {vse.length > 0 && krmila}

        {stanje}

        {prikazane.length > 0 && (
          <div className="kartice">
            {prikazane.map((liga) => (
              <Link
                to={`/lige/${liga.id}`}
                className={`kartica kartica--liga kartica--${liga.status}`}
                key={liga.id}
              >
                <span className="kartica__glava">
                  <span className="kartica__ime">{liga.ime}</span>
                  <span className="kartica__podrobnost">
                    {[
                      liga.sezona ? `sezona ${liga.sezona}` : null,
                      OZNAKE_SPOL_KATEGORIJA[liga.spolKategorija].toLowerCase(),
                      liga.dvokrozno ? 'dvokrožno' : 'enokrožno',
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </span>
                <span className="vrstica__pod">
                  {liga.steviloEkip} {ekipTekst(liga.steviloEkip)}
                </span>
                <span className="vrstica__mono">{OZNAKE_FORMAT[liga.formatSrecanja]}</span>
                <span className="vrstica__mono">
                  {liga.stNapreduje > 0 || liga.stIzpade > 0
                    ? `${liga.stNapreduje} ↑ · ${liga.stIzpade} ↓`
                    : `na ${liga.steviloNizov} nizov`}
                </span>
                <ZnackaStatusa status={liga.status} />
              </Link>
            ))}
          </div>
        )}
      </div>

      {obrazec}
    </section>
  )
}

/* Vrstica lige na telefonu: ime, mono meta in status.

   V pasu »V teku« nosi meta še kolo (»2025/26 · 7. od 18 kol«) in pod njo
   stoji 4 px palica napredka; v seznamu vseh lig oboje odpade, ker gledalec
   tam išče ligo in ne njenega poteka - vrstica s tem pade na 68 px in vseh
   pet lig je na zaslonu brez drsenja. */
function VrsticaLigeMobi({
  liga,
  vPasu = false,
  poudarjena = false,
}: {
  liga: LigaDto
  vPasu?: boolean
  poudarjena?: boolean
}) {
  const imaKola = liga.steviloKol > 0
  const kola = vPasu && imaKola ? `${liga.odigranihKol}. od ${liga.steviloKol} kol` : null
  /* Meta nikoli ni prazna vrstica: brez sezone in brez kol ostane črtica, da
     vrstica ohrani višino in poravnavo s sosednjimi. */
  const meta = [liga.sezona, kola].filter(Boolean).join(' · ') || '—'

  return (
    <Link
      to={`/lige/${liga.id}`}
      className={
        `vrstica-mobi vrstica-mobi--liga vrstica-mobi--${liga.status}` +
        (poudarjena ? ' vrstica-mobi--poudarjena' : '')
      }
    >
      <span className="vrstica-mobi__telo">
        <span className="vrstica-mobi__ime">{liga.ime}</span>
        <span className="vrstica-mobi__meta">{meta}</span>
        {vPasu && imaKola && liga.status === 'V_TEKU' && (
          <PalicaMobi
            odigranih={liga.odigranihKol}
            vseh={liga.steviloKol}
            naModri={poudarjena}
          />
        )}
      </span>
      <StatusMobi status={liga.status} />
    </Link>
  )
}

function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
}

function ligTekst(n: number): string {
  if (n === 1) return 'liga'
  if (n === 2) return 'ligi'
  if (n === 3 || n === 4) return 'lige'
  return 'lig'
}
