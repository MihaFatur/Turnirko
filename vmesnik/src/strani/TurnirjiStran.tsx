/* Seznam vseh turnirjev + ustvarjanje novega. Turnir je okvir; tekmovanja
   znotraj njega so dogodki, zato vrstica nosi le okvirne podatke.

   Nad seznamom stoji pas "Danes v dvorani": sodnik, ki vodi turnir, mora
   svojega najti v manj kot sekundi, zato so turnirji v teku loceni od
   zgodovine in nosijo palico napredka.

   Na telefonu je vrstica DRUGA vsebina in ne le ozja mreza: namesto petih
   stolpcev (datum, ime s krajem, dogodki, obdobje, status) nosi datum, ime in
   eno mono vrstico "obdobje · kraj". Stevilo dogodkov je odpadlo - dobi se ga
   na strani turnirja - zato vrstica meri 68 px namesto 140. Dejanje
   "+ Turnir" se je preselilo v lepljivo glavo.

   Stiri gumbe filtra po statusu je nadomestilo eno okno z vsemi merili
   (stanje, sezona, kraj, organizator, rating) in ob njem izbor razvrstitve -
   glej komponente/Filtri. Sezone turnir ne nosi kot polje; izpelje se iz
   datuma zacetka, kraj in organizator pa se izriseta samo, kadar ju podatki
   imajo (uvozena zgodovina NTZS ju nima). */
import { useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { krajiApi, turnirjiApi } from '../api/zahteve'
import type { TurnirDto, TurnirVnos } from '../api/tipi'
import { OZNAKE_STATUS_TEKMOVANJA } from '../api/tipi'
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
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { Napredek, PalicaMobi } from '../komponente/Napredek'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { StatusMobi, ZnackaStatusa } from '../komponente/Znacka'
import {
  datumskiBlok,
  oblikujObdobje,
  oblikujObdobjeKratko,
  sezonaIzDatuma,
  sklonDogodkov,
} from '../pomozno/oblikovanje'
import { intervalOsvezevanja, uraOsvezitve } from '../pomozno/osvezevanje'
import { useTelefon } from '../pomozno/telefon'

/* Merila nad seznamom. Vrednost je vedno niz, ker je kljuc izbora - status je
   svoje ime enuma, sezona izpeljanka iz datuma, ostalo pa ime iz sifranta. */
const SKUPINE: SkupinaFiltra<TurnirDto>[] = [
  {
    kljuc: 'stanje',
    oznaka: 'Stanje',
    vrednost: (t) => t.status,
    napis: (v) => OZNAKE_STATUS_TEKMOVANJA[v as keyof typeof OZNAKE_STATUS_TEKMOVANJA],
    vrstniRed: poSeznamu(['V_TEKU', 'PRIPRAVA', 'ZAKLJUCEN']),
  },
  {
    kljuc: 'sezona',
    oznaka: 'Sezona',
    vrednost: (t) => sezonaIzDatuma(t.datumZacetka),
    vrstniRed: poVrednostiNazaj,
  },
  /* Kraj in dvorana sta LOCENI skupini in ne ena zlepljena vrednost: uvozena
     zgodovina NTZS kraja iz sifranta nima (postna_st je prazna), ime prizorisca
     pa je pristalo v "dvorani" — tam so zato imena mest ("Rakek", "M. Sobota").
     Rocno vpisan turnir ima obratno kraj iz sifranta in dvorano kot ime
     dvorane. Ker se skupina brez vrednosti sploh ne izrise, vsak od obeh
     virov pokaze svojo in nobena ne obljublja, cesar nima. */
  { kljuc: 'kraj', oznaka: 'Kraj', vrednost: (t) => t.kraj?.ime ?? null },
  { kljuc: 'dvorana', oznaka: 'Dvorana', vrednost: (t) => t.dvorana },
  { kljuc: 'organizator', oznaka: 'Organizator', vrednost: (t) => t.klubLastnik },
  {
    kljuc: 'elo',
    oznaka: 'Rating',
    vrednost: (t) => (t.stejeVElo ? 'da' : 'ne'),
    napis: (v) => (v === 'da' ? 'Šteje v ELO' : 'Ne šteje v ELO'),
    vrstniRed: poSeznamu(['da', 'ne']),
  },
]

/* Razvrstitev po datumu je privzeta in namerno ne sledi vrstnemu redu
   streznika (ta ureja po casu VNOSA): uvozena zgodovina je vsa vnesena isti
   dan, zato bi bil njen vrstni red nakljucen. */
const RAZVRSTITVE: Razvrstitev<TurnirDto>[] = [
  {
    kljuc: 'novi',
    oznaka: 'Najnovejši',
    primerjaj: (a, b) => poDatumu(a, b, -1) || a.ime.localeCompare(b.ime, 'sl'),
  },
  {
    kljuc: 'stari',
    oznaka: 'Najstarejši',
    primerjaj: (a, b) => poDatumu(a, b, 1) || a.ime.localeCompare(b.ime, 'sl'),
  },
  { kljuc: 'ime', oznaka: 'Po imenu', primerjaj: (a, b) => a.ime.localeCompare(b.ime, 'sl') },
]

/* Primerjava po datumu zacetka; `smer` je 1 za narascajoce in -1 za padajoce.
   Turnir brez datuma pade na konec V OBEH smereh in ne le v eni: datum manjka
   zato, ker ga organizator se ni vpisal, in tak turnir ni "najstarejsi". */
function poDatumu(a: TurnirDto, b: TurnirDto, smer: 1 | -1): number {
  if (!a.datumZacetka) return b.datumZacetka ? 1 : 0
  if (!b.datumZacetka) return -1
  return smer * a.datumZacetka.localeCompare(b.datumZacetka)
}

function sklonTurnirjev(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'turnir'
  if (ostanek === 2) return 'turnirja'
  if (ostanek === 3 || ostanek === 4) return 'turnirje'
  return 'turnirjev'
}

export function TurnirjiStran() {
  const odjemalec = useQueryClient()
  const { smeUstvarjati } = useAvtentikacija()
  const jeTelefon = useTelefon()
  /* Dokler kateri od turnirjev tece, se seznam osvezuje sam: pas "Danes v
     dvorani" nosi napredek, ki se med dnevom premika. */
  const turnirji = useQuery({
    queryKey: ['turnirji'],
    queryFn: turnirjiApi.seznam,
    refetchInterval: (poizvedba) =>
      poizvedba.state.data?.some((t) => t.status === 'V_TEKU')
        ? intervalOsvezevanja('V_TEKU')
        : false,
  })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  const vsi = useMemo(() => turnirji.data ?? [], [turnirji.data])
  const filtri = useFiltri(vsi, SKUPINE, RAZVRSTITVE)
  const prikazani = filtri.prikazani
  const vTeku = useMemo(() => vsi.filter((t) => t.status === 'V_TEKU'), [vsi])
  const osvezenoOb = uraOsvezitve(turnirji.dataUpdatedAt)

  const krmila = (
    <KrmilaSeznama
      stanje={filtri}
      razvrstitve={RAZVRSTITVE}
      naslovOkna="Turnirji"
      imeZadetkov={sklonTurnirjev}
    />
  )

  /* Stanja, ki ju risemo enako na obeh sirinah. */
  const stanje = (
    <>
      <NapakaPoizvedbe poizvedba={turnirji} kaj="turnirjev" />
      {turnirji.isPending && <p className="obvestilo">Nalaganje …</p>}

      {turnirji.data && vsi.length === 0 && (
        <p className="obvestilo">
          Ni še nobenega turnirja. Ustvari prvega z gumbom »+ Nov turnir«.
        </p>
      )}

      {/* Prazen izid filtra ni prazen seznam: pot nazaj mora biti tu, ne le
          v oknu, ki ga je gledalec ze zaprl. */}
      {vsi.length > 0 && prikazani.length === 0 && (
        <p className="obvestilo">
          Izbranim merilom ne ustreza noben turnir.{' '}
          <button type="button" className="povezava-gumb" onClick={filtri.pocisti}>
            Počisti filtre
          </button>
        </p>
      )}
    </>
  )

  if (jeTelefon) {
    return (
      <section>
        {/* Dejanje urejevalca stoji v lepljivi glavi in ne nad seznamom:
            gledalec (uporabnik st. 1) pride po turnirje, ne po gumb. */}
        <GlavaDejanja>
          {smeUstvarjati && (
            <button
              type="button"
              className="glava-telefon__gumb"
              onClick={() => nastaviOdprtObrazec(true)}
            >
              + Turnir
            </button>
          )}
        </GlavaDejanja>

        <div>
          <span className="naslov-mobi__nad">Tekmovanja</span>
          <h1 className="naslov-mobi naslov-mobi--seznam">Turnirji</h1>

          {vsi.length > 0 && krmila}
        </div>

        {vTeku.length > 0 && (
          <div>
            {/* Ura osvezitve je odsla iz vrstice krmil (tam sta zdaj filter in
                razvrstitev) sem, kjer sploh nekaj pove: napredek se premika
                samo pri turnirjih v teku. */}
            <div className="naslovna-mobi">
              <h2>Danes v dvorani</h2>
              <span className="naslovna-mobi__stevec naslovna-mobi__stevec--drobno">
                {vTeku.length}
                {osvezenoOb && ` · osveženo ${osvezenoOb}`}
              </span>
            </div>
            <div className="seznam-mobi">
              {vTeku.map((turnir) => (
                <VrsticaTurnirjaMobi key={turnir.id} turnir={turnir} danes />
              ))}
            </div>
          </div>
        )}

        <div>
          <div className="naslovna-mobi">
            <h2>Vsi turnirji</h2>
            <span className="naslovna-mobi__stevec">
              {prikazani.length === vsi.length
                ? vsi.length
                : `${prikazani.length} od ${vsi.length}`}
            </span>
          </div>
          {stanje}
          <div className="seznam-mobi">
            {prikazani.map((turnir) => (
              <VrsticaTurnirjaMobi key={turnir.id} turnir={turnir} />
            ))}
          </div>
        </div>

        {odprtObrazec && (
          <NovTurnirOkno
            onZapri={() => nastaviOdprtObrazec(false)}
            onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['turnirji'] })}
          />
        )}
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
            + Nov turnir
          </button>
        </div>
      )}

      {/* Brez turnirja v teku se pas ne izrise - prazno stanje bi bilo samo
          se ena vrstica, ki jo mora sodnik prebrati in preskociti. */}
      {vTeku.length > 0 && (
        <div>
          <div className="naslovna-vrstica">
            <h2>Danes v dvorani</h2>
            {osvezenoOb && <span className="sekcija__meta">Osveženo ob {osvezenoOb}</span>}
          </div>
          <div className="kartice">
            {vTeku.map((turnir) => (
              <VrsticaTurnirja key={turnir.id} turnir={turnir} danes />
            ))}
          </div>
        </div>
      )}

      <div>
        <div className="naslovna-vrstica">
          <h2>Vsi turnirji</h2>
          {turnirji.data && vsi.length > 0 && (
            <span className="sekcija__meta">
              {prikazani.length === vsi.length
                ? `${vsi.length} ${sklonTurnirjev(vsi.length)}`
                : `Prikazanih ${prikazani.length} od ${vsi.length}`}
            </span>
          )}
        </div>

        {turnirji.data && vsi.length > 0 && krmila}

        {stanje}

        {prikazani.length > 0 && (
          <>
            <div className="seznam-glava seznam-glava--turnirji">
              <span>Začetek</span>
              <span>Turnir</span>
              <span>Dogodki</span>
              <span>Obdobje</span>
              <span className="seznam-glava__sredinjeno">Status</span>
            </div>
            <div className="kartice">
              {prikazani.map((turnir) => (
                <VrsticaTurnirja key={turnir.id} turnir={turnir} />
              ))}
            </div>
          </>
        )}
      </div>

      {odprtObrazec && (
        <NovTurnirOkno
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['turnirji'] })}
        />
      )}
    </section>
  )
}

/* Ena vrstica seznama turnirjev. Ista mreza stolpcev sluzi obema pasovoma;
   razlikuje se le tretji stolpec: v pasu "Danes v dvorani" nosi napredek
   (kar sodnika zanima med turnirjem), v seznamu pa stevilo dogodkov. */
function VrsticaTurnirja({ turnir, danes = false }: { turnir: TurnirDto; danes?: boolean }) {
  const { dan, mesec } = datumskiBlok(turnir.datumZacetka)
  const kraj =
    [turnir.kraj?.ime, turnir.dvorana].filter(Boolean).join(', ') || 'kraj še ni določen'

  return (
    <Link
      to={`/turnirji/${turnir.id}`}
      className={'kartica kartica--z-datumom' + (danes ? ' kartica--danes' : '')}
      key={turnir.id}
    >
      <span className={`datum-blok datum-blok--${turnir.status}`}>
        <span className="datum-blok__dan">{dan}</span>
        <span className="datum-blok__mesec">{mesec}</span>
      </span>
      <span className="kartica__glava">
        <span className="kartica__ime">{turnir.ime}</span>
        <span className="kartica__podrobnost">
          {danes ? `${kraj}${stanjeDogodkov(turnir)}` : kraj}
        </span>
      </span>
      {danes ? (
        <Napredek odigranih={turnir.odigranihTekem} vseh={turnir.vsehTekem} />
      ) : (
        <span className="kartica__organizator">
          {turnir.steviloDogodkov} {sklonDogodkov(turnir.steviloDogodkov)}
        </span>
      )}
      <span className="vrstica__mono">
        {oblikujObdobje(turnir.datumZacetka, turnir.datumKonca) || '—'}
      </span>
      <ZnackaStatusa status={turnir.status} />
    </Link>
  )
}

/* Ista vrstica na telefonu: datum, ime, "obdobje · kraj" in status.

   Stevilo dogodkov je odpadlo (dobi se ga na strani turnirja), obdobje pa se
   je preselilo v mono vrstico pod imenom - s tem vrstica pade s 140 px na
   68 px in prvih pet turnirjev je na zaslonu brez drsenja. Palica napredka
   se izrise samo pri turnirju, ki tece: pri pripravi in zakljucku bi bila
   prazna oz. polna crta brez sporocila. */
function VrsticaTurnirjaMobi({ turnir, danes = false }: { turnir: TurnirDto; danes?: boolean }) {
  const { dan, mesec } = datumskiBlok(turnir.datumZacetka)
  const obdobje = oblikujObdobjeKratko(turnir.datumZacetka, turnir.datumKonca)
  const meta =
    [obdobje, turnir.kraj?.ime].filter(Boolean).join(' · ') || 'kraj in datum še nista določena'
  const tece = turnir.status === 'V_TEKU'

  return (
    <Link
      to={`/turnirji/${turnir.id}`}
      className={
        `vrstica-mobi vrstica-mobi--${turnir.status}` + (danes ? ' vrstica-mobi--danes' : '')
      }
    >
      <span className="vrstica-mobi__datum">
        <span className="vrstica-mobi__dan">{dan}</span>
        <span className="vrstica-mobi__mesec">{mesec}</span>
      </span>
      <span className="vrstica-mobi__telo">
        <span className="vrstica-mobi__ime">{turnir.ime}</span>
        <span className="vrstica-mobi__meta">{meta}</span>
        {tece && (
          <PalicaMobi
            odigranih={turnir.odigranihTekem}
            vseh={turnir.vsehTekem}
            naModri={danes}
          />
        )}
      </span>
      <StatusMobi status={turnir.status} />
    </Link>
  )
}

/* " · 2 dogodka v teku, 3 v pripravi" - dopisano h kraju v pasu "Danes v
   dvorani". Prazen niz, kadar ni cesa povedati. */
function stanjeDogodkov(turnir: TurnirDto): string {
  const deli: string[] = []
  if (turnir.dogodkovVTeku > 0) {
    deli.push(
      `${turnir.dogodkovVTeku} ${sklonDogodkov(turnir.dogodkovVTeku)} v teku`,
    )
  }
  if (turnir.dogodkovVPripravi > 0) {
    deli.push(`${turnir.dogodkovVPripravi} v pripravi`)
  }
  return deli.length > 0 ? ` · ${deli.join(', ')}` : ''
}

function NovTurnirOkno({
  onZapri,
  onShranjeno,
}: {
  onZapri: () => void
  onShranjeno: () => void
}) {
  const kraji = useQuery({ queryKey: ['kraji'], queryFn: krajiApi.seznam })

  const [ime, nastaviIme] = useState('')
  const [postnaSt, nastaviPostnaSt] = useState('')
  const [dvorana, nastaviDvorano] = useState('')
  const [datumZacetka, nastaviDatumZacetka] = useState('')
  const [datumKonca, nastaviDatumKonca] = useState('')
  const [opombe, nastaviOpombe] = useState('')
  const [stejeVElo, nastaviStejeVElo] = useState(true)

  const shranjevanje = useMutation({
    mutationFn: (vnos: TurnirVnos) => turnirjiApi.ustvari(vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      postnaSt: postnaSt ? Number(postnaSt) : null,
      dvorana: dvorana.trim() || null,
      datumZacetka: datumZacetka || null,
      datumKonca: datumKonca || null,
      opombe: opombe.trim() || null,
      stejeVElo,
    })
  }

  return (
    <ModalnoOkno naslov="Nov turnir" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Ime turnirja *</span>
          <input
            value={ime}
            onChange={(dogodek) => nastaviIme(dogodek.target.value)}
            placeholder="npr. Odprto prvenstvo NTK Savinja 2026"
            required
          />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Kraj</span>
            <select
              value={postnaSt}
              onChange={(dogodek) => nastaviPostnaSt(dogodek.target.value)}
            >
              <option value="">— izberi kraj —</option>
              {kraji.data?.map((kraj) => (
                <option key={kraj.postnaSt} value={kraj.postnaSt}>
                  {kraj.postnaSt} {kraj.ime}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Dvorana</span>
            <input
              value={dvorana}
              onChange={(dogodek) => nastaviDvorano(dogodek.target.value)}
              placeholder="npr. ŠD Golovec"
            />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Datum začetka</span>
            <input
              type="date"
              value={datumZacetka}
              onChange={(dogodek) => nastaviDatumZacetka(dogodek.target.value)}
            />
          </label>
          <label className="obrazec__polje">
            <span>Datum konca</span>
            <input
              type="date"
              value={datumKonca}
              onChange={(dogodek) => nastaviDatumKonca(dogodek.target.value)}
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Opombe</span>
          <textarea
            value={opombe}
            onChange={(dogodek) => nastaviOpombe(dogodek.target.value)}
            rows={2}
          />
        </label>

        <label className="obrazec__polje obrazec__polje--stikalo">
          <input
            type="checkbox"
            checked={stejeVElo}
            onChange={(dogodek) => nastaviStejeVElo(dogodek.target.checked)}
          />
          <span>Tekme štejejo v klubski ELO (rating)</span>
        </label>

        {kraji.data?.length === 0 && (
          <p className="namig">Namig: kraje lahko dodaš na strani Šifranti.</p>
        )}

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Ustvari turnir
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
