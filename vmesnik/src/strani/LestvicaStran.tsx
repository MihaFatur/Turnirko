/* Globalna lestvica igralcev po Turnirko ratingu, z razmerjem
   zmag in porazov prek vseh dogodkov. Vidna vsem (tudi gostom).

   Na telefonu je vrstica DRUGO drevo in ne ožja tabela: sedem stolpcev se je
   pri 390 px ali odrezalo ali prelomilo. Tu je vrstica mreža treh stolpcev -
   mesto, ime z mono vrstico »klub · Z–P · gibanje« in rating kot največja
   številka ob desnem robu. Stolpec »Δ 30 dni« odpade, ker isto pove gibanje.

   Nad seznamom je odpadlo vse, kar je prvo vrstico razvrstitve potiskalo na
   ~620 px: uvodni odstavek, stalno polje iskanja, pas števcev in osem gumbov
   filtra v štirih vrstah. Ostane ena vrstica krmil (gumb »Filtriraj« in izbor
   razvrstitve), iskanje pa je preklopnik v lepljivi glavi.

   LESTVIC JE VEČ IN NISO ENA. Spol ni filter, ampak IZBIRA LESTVICE: med
   moškimi in ženskami ni niti ene obračunane tekme (0 od 91.741), zato sta
   skali neprimerljivi in skupno mesto ne pomeni ničesar — mešan seznam je
   bral kot razvrstitev, kar ni bil. Enako velja za rekreativce: dober
   rekreativec ne sme prehiteti nekoliko slabšega igralca, ki hodi na članske
   turnirje NTZS (teža tekmovanja to ublaži, ne odpravi). Zato sta nad tabelo
   dva segmentirana izbirnika, pas rekreativcev pa se pokaže šele, ko kdo tam
   sploh je.

   Privzeto se odpre lestvica gledalčevega spola, če je gledalec na njej;
   sicer moška (večja). Starost ostane filter, ker U19 in člani igrajo skupaj
   in se na isti lestvici primerjajo; kategorija (»Člani/Članice«) spol nosi
   samo pri članih, zato filtra »vse igralke« iz nje ni bilo mogoče sestaviti.
   Druga skupina filtra je klub.

   Iskanje po imenu ni skupina filtra, ampak zoži seznam PRED njim — števci ob
   merilih so tako vedno števci tega, kar gledalec vidi. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi } from '../api/zahteve'
import type { LestvicaIgralcaDto, Spol } from '../api/tipi'
import {
  KrmilaSeznama,
  poSeznamu,
  useFiltri,
  type Razvrstitev,
  type SkupinaFiltra,
} from '../komponente/Filtri'
import { GlavaDejanja, GlavaNaslov } from '../komponente/GlavaTelefona'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { useTelefon } from '../pomozno/telefon'

/* Vrstica lestvice z mestom. Mesto se pripne PRED filtriranjem — a samo zato,
   ker je vrstni red strežnika (v njem odločajo tudi izenačenja po zmagah in
   abecedi) in ga potrebujeta merilo »Rating« ter izenačenje pri drugih
   merilih. Gledalcu se ne izpiše: prikazani seznam se oštevilči znova (glej
   prikazani). */
interface Vrstica {
  igralec: LestvicaIgralcaDto
  mesto: number
}

/* Katera lestvica se gleda. Tip je ločen od spola, ker sta to dve neodvisni
   vprašanji in ne štiri enakovredne možnosti v enem pasu. */
type TipLestvice = 'TEKMOVALCI' | 'REKREATIVCI'

const OZNAKE_SPOLA: Record<Spol, string> = { MOSKI: 'Moški', ZENSKI: 'Ženske' }

const SKUPINE: SkupinaFiltra<Vrstica>[] = [
  {
    kljuc: 'starost',
    oznaka: 'Starost',
    vrednost: ({ igralec }) => (igralec.kategorija === null ? null : starost(igralec)),
    napis: (v) => OZNAKE_STAROSTI[v] ?? v,
    vrstniRed: poSeznamu(['U19', 'CLANSKA', 'VETERANI']),
  },
  { kljuc: 'klub', oznaka: 'Klub', vrednost: ({ igralec }) => igralec.klub },
]

/* Starostna skupina brez spola. Izpelje se iz kategorije, ki jo računa
   strežnik (KategorijaIgralca) — datuma rojstva vmesnik nima in ga tudi ne
   sme imeti, ker je osebni podatek. */
const OZNAKE_STAROSTI: Record<string, string> = {
  U19: 'Mladinci (do 19)',
  CLANSKA: 'Člani (19–39)',
  VETERANI: 'Veterani (40+)',
}

function starost(igralec: LestvicaIgralcaDto): string | null {
  if (igralec.kategorija === 'U19') return 'U19'
  if (igralec.kategorija === 'VETERANI') return 'VETERANI'
  return igralec.kategorija === null ? null : 'CLANSKA'
}

const RAZVRSTITVE: Razvrstitev<Vrstica>[] = [
  /* Rating je vrstni red strežnika (mesto), ne ponovljena primerjava: tam
     odloča tudi izenačenje po zmagah in abecedi. */
  { kljuc: 'rating', oznaka: 'Rating', primerjaj: (a, b) => a.mesto - b.mesto },
  {
    kljuc: 'uspesnost',
    oznaka: 'Uspešnost',
    primerjaj: (a, b) => uspesnost(b.igralec) - uspesnost(a.igralec) || a.mesto - b.mesto,
  },
  {
    kljuc: 'odigrane',
    oznaka: 'Odigrane',
    primerjaj: (a, b) => b.igralec.odigrane - a.igralec.odigrane || a.mesto - b.mesto,
  },
  {
    kljuc: 'priimek',
    oznaka: 'Priimek (A–Ž)',
    primerjaj: (a, b) =>
      a.igralec.priimek.localeCompare(b.igralec.priimek, 'sl') ||
      a.igralec.ime.localeCompare(b.igralec.ime, 'sl'),
  },
]

export function LestvicaStran() {
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })
  const { mojIdIgralec } = useAvtentikacija()
  const jeTelefon = useTelefon()
  const [iskanje, nastaviIskanje] = useState('')
  /* Iskanje na telefonu živi v stanju strani in ne v naslovu: je opravilo
     enega obiska, ne stanje, ki bi ga kdo delil s povezavo. */
  const [iskanjeOdprto, nastaviIskanjeOdprto] = useState(false)
  const [tip, nastaviTip] = useState<TipLestvice>('TEKMOVALCI')
  const [spol, nastaviSpol] = useState<Spol | null>(null)

  /* Igralci ene lestvice: en spol in ena skupina. Mesto se pripne TU, ker je
     mesto na svoji lestvici in ne v skupnem seznamu vseh. */
  const izbranaLestvica = useMemo(() => {
    const vse = lestvica.data ?? []
    /* Privzeti spol: gledalčev, če je na lestvici; sicer moška (večja).
       Izbira ene od dveh lestvic kot "prve" je neizogibna, zato naj bo
       gledalčeva. */
    const mojSpol = vse.find((v) => v.idIgralca === mojIdIgralec)?.spol ?? null
    const izbrani = spol ?? mojSpol ?? 'MOSKI'
    const rekreativci = tip === 'REKREATIVCI'
    return vse
      .filter((v) => v.spol === izbrani && v.rekreativec === rekreativci)
      .map((igralec, indeks) => ({ igralec, mesto: indeks + 1 }))
  }, [lestvica.data, spol, tip, mojIdIgralec])

  /* Koliko igralcev je na kateri lestvici - za števce ob gumbih in za to, da
     se pas rekreativcev ne pokaže, dokler tam ni nikogar. */
  const steviloNaLestvici = useMemo(() => {
    const stevci = { MOSKI: 0, ZENSKI: 0, TEKMOVALCI: 0, REKREATIVCI: 0 } as Record<string, number>
    for (const v of lestvica.data ?? []) {
      if (v.rekreativec) stevci.REKREATIVCI++
      else stevci.TEKMOVALCI++
      /* Igralec brez spola (v bazi je obvezen, a tip dopušča null) ne pripada
         nobeni od obeh lestvic in se ne šteje nikjer. */
      if (v.spol !== null && v.rekreativec === (tip === 'REKREATIVCI')) stevci[v.spol]++
    }
    return stevci
  }, [lestvica.data, tip])

  const izbraniSpol = spol
    ?? (lestvica.data ?? []).find((v) => v.idIgralca === mojIdIgralec)?.spol
    ?? 'MOSKI'

  const najdeni = useMemo(() => {
    const iskano = iskanje.trim().toLocaleLowerCase('sl')
    if (!iskano) return izbranaLestvica
    return izbranaLestvica.filter(({ igralec }) =>
      `${igralec.polnoIme} ${igralec.klub ?? ''}`.toLocaleLowerCase('sl').includes(iskano),
    )
  }, [izbranaLestvica, iskanje])

  const filtri = useFiltri(najdeni, SKUPINE, RAZVRSTITVE)

  /* Prikazani seznam se VEDNO prešteje od 1 naprej: številka pove mesto v tem,
     kar gledalec gleda. Filter »U19«, ki se je začel pri 35., je bral kot izsek
     sredine lestvice — koliko mladincev je pred tem igralcem, pa je bilo treba
     šteti na roke. Isto velja za razvrstitev po drugem merilu: »4. po
     uspešnosti« ni »4. po ratingu«. Globalno mesto ostane v vrstici (Vrstica),
     ker po njem teče razvrščanje. */
  const prikazani = useMemo(
    () => filtri.prikazani.map(({ igralec }, indeks) => ({ igralec, mesto: indeks + 1 })),
    [filtri.prikazani],
  )

  const vseh = izbranaLestvica.length
  const klubov = useMemo(
    () => new Set(izbranaLestvica.map(({ igralec }) => igralec.klub).filter((v) => v !== null)).size,
    [izbranaLestvica],
  )

  /* Ali gledalec gleda izsek ali celo lestvico. Od tega je odvisen števec ob
     naslovu: dokler ni zoženo, je "Prikazanih 1703 od 1703" prazna poved in
     namesto nje pove obseg lestvice (igralci, klubi). Merilo je isto na obeh
     širinah. */
  const zozeno = filtri.steviloIzbranih > 0 || iskanje.trim() !== ''

  const krmila = (
    <KrmilaSeznama
      stanje={filtri}
      razvrstitve={RAZVRSTITVE}
      naslovOkna="Lestvica"
      imeZadetkov={sklonIgralcev}
    />
  )

  const opisTabele =
    'Lestvica igralcev po Turnirko ratingu' +
    (filtri.zetoni.length > 0
      ? ` — izbrano: ${filtri.zetoni.map((z) => `${z.oznaka} ${z.napis}`).join(', ')}`
      : '')

  /* Stanja, ki jih rišemo enako na obeh širinah. */
  const stanje = (
    <>
      <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice" />
      {lestvica.isPending && <p className="obvestilo">Nalaganje …</p>}

      {lestvica.data && lestvica.data.length === 0 && (
        <p className="obvestilo">Še ni igralcev.</p>
      )}

      {vseh > 0 && prikazani.length === 0 && (
        <p className="obvestilo">
          {najdeni.length === 0 ? (
            'Iskanju ne ustreza noben igralec.'
          ) : (
            <>
              Izbranim merilom ne ustreza noben igralec.{' '}
              <button type="button" className="povezava-gumb" onClick={filtri.pocisti}>
                Počisti filtre
              </button>
            </>
          )}
        </p>
      )}
    </>
  )

  /* Segmentirana izbirnika: spol vedno, tip samo, kadar rekreativci obstajajo.
     Isti razred kot podnavigacija dogodka - ista poteza naj bo videti enako. */
  const izbirnikLestvice = (
    <div className="izbirnik-lestvic">
      <div className="izbirnik">
        {(['MOSKI', 'ZENSKI'] as Spol[]).map((v) => (
          <button
            key={v}
            type="button"
            className={'izbirnik__gumb' + (izbraniSpol === v ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={izbraniSpol === v}
            onClick={() => nastaviSpol(v)}
          >
            {OZNAKE_SPOLA[v]} · {steviloNaLestvici[v]}
          </button>
        ))}
      </div>
      {steviloNaLestvici.REKREATIVCI > 0 && (
        <div className="izbirnik">
          {(['TEKMOVALCI', 'REKREATIVCI'] as TipLestvice[]).map((v) => (
            <button
              key={v}
              type="button"
              className={'izbirnik__gumb' + (tip === v ? ' izbirnik__gumb--aktiven' : '')}
              aria-pressed={tip === v}
              onClick={() => nastaviTip(v)}
            >
              {v === 'TEKMOVALCI' ? 'Tekmovalci' : 'Rekreativci'}
            </button>
          ))}
        </div>
      )}
    </div>
  )

  const namig = (
    <p className="namig">
      Igralci brez obračunane tekme še niso na lestvici; po 18 mesecih brez tekme z nje
      izginejo. Moška in ženska lestvica sta ločeni, ker med spoloma ni obračunanih tekem
      in številki nista primerljivi.{' '}
      {steviloNaLestvici.REKREATIVCI > 0
        ? 'Rekreativci imajo svojo lestvico, dokler ne odigrajo treh tekem na uradnem ali klubskem tekmovanju.'
        : ''}
      Ime igralca vodi na profil s statistiko.
    </p>
  )

  if (jeTelefon) {
    /* Preklic izbriše iskanje in pas zapre: pas, ki ostane odprt s praznim
       poljem, gledalcu jemlje 68 px zaslona za nič. */
    const zapriIskanje = () => {
      nastaviIskanje('')
      nastaviIskanjeOdprto(false)
    }

    return (
      <section className="stran-mobi--lestvica">
        <GlavaDejanja>
          <button
            type="button"
            className="glava-telefon__gumb glava-telefon__gumb--preklop"
            aria-pressed={iskanjeOdprto}
            onClick={() => (iskanjeOdprto ? zapriIskanje() : nastaviIskanjeOdprto(true))}
          >
            Išči
          </button>
        </GlavaDejanja>

        {iskanjeOdprto && (
          <GlavaNaslov>
            <div className="iskanje-mobi">
              <input
                className="iskalnik"
                /* Pas se odpre na gledalčevo dejanje, zato tipkovnica sme
                   priti z njim - drugega opravila v pasu ni. */
                autoFocus
                aria-label="Išči po imenu ali klubu"
                placeholder="Išči po imenu ali klubu …"
                value={iskanje}
                onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
              />
              <button type="button" className="iskanje-mobi__preklic" onClick={zapriIskanje}>
                Prekliči
              </button>
            </div>
          </GlavaNaslov>
        )}

        <div>
          <span className="naslov-mobi__nad">Turnirko rating</span>
          <h1 className="naslov-mobi naslov-mobi--seznam">Lestvica</h1>

          {izbirnikLestvice}
          {vseh > 0 && krmila}
        </div>

        <div>
          <div className="naslovna-mobi">
            <h2>Razvrstitev</h2>
            {lestvica.data && (
              <span className="naslovna-mobi__stevec naslovna-mobi__stevec--drobno">
                {zozeno
                  ? `Prikazanih ${prikazani.length} od ${vseh}`
                  : `${vseh} ${sklonIgralcev(vseh)} · ${klubov} ${sklonKlubov(klubov)}`}
              </span>
            )}
          </div>

          {stanje}

          {prikazani.length > 0 && (
            <>
              <div className="lestvica-mobi">
                {prikazani.map(({ igralec, mesto }) => (
                  <VrsticaLestviceMobi
                    key={igralec.idIgralca}
                    igralec={igralec}
                    mesto={mesto}
                    jaz={igralec.idIgralca === mojIdIgralec}
                  />
                ))}
              </div>
              <p className="lestvica-mobi__opomba">
                Gibanje in Δ: primerjava s stanjem pred 30 dnevi
              </p>
            </>
          )}

          {namig}
        </div>
      </section>
    )
  }

  return (
    <section>
      {/* Glave strani (nadnaslov, naslov, uvod) ni: kje smo, pove navigacija.
          Iskanje in števci tudi ne stojijo več v svojem pasu nad seznamom:
          zavzeli so 380 px stolpca in prvo vrstico razvrstitve potisnili
          nizko, čeprav sta oba krmilo TE tabele. Zdaj sta v njeni naslovni
          vrstici — iskalnik in ob njem števec, isti par kot v seznamu prijav
          (DogodekStran). */}
      <div>
        {izbirnikLestvice}
        <div className="naslovna-vrstica">
          <h2>Razvrstitev</h2>
          <div className="naslovna-vrstica__desno">
            <input
              className="iskalnik iskalnik--kratek"
              type="search"
              value={iskanje}
              onChange={(d) => nastaviIskanje(d.target.value)}
              placeholder="išči po imenu ali klubu"
              aria-label="Išči po imenu ali klubu"
            />
            {lestvica.data && (
              <span className="sekcija__meta">
                {zozeno
                  ? `Prikazanih ${prikazani.length} od ${vseh}`
                  : `${vseh} ${sklonIgralcev(vseh)} · ${klubov} ${sklonKlubov(klubov)}`}
              </span>
            )}
          </div>
        </div>

        {vseh > 0 && krmila}

        {stanje}

        {prikazani.length > 0 && (
          <>
            <div className="tabela-ovoj lestvica-ovoj">
              <table className="tabela lestvica--globalna">
                <caption className="samo-za-bralnik">{opisTabele}</caption>
                {/* Fiksne širine stolpcev: dolgo ime in dolg klub se odrežeta,
                    namesto da bi prelomila vrstico ali potisnila številke. */}
                <colgroup>
                  <col className="lestvica__stolpec--mesto" />
                  <col className="lestvica__stolpec--gibanje" />
                  <col className="lestvica__stolpec--igralec" />
                  <col className="lestvica__stolpec--klub" />
                  <col className="lestvica__stolpec--izid" />
                  <col className="lestvica__stolpec--rating" />
                  <col className="lestvica__stolpec--delta" />
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col" className="lestvica__mesto">#</th>
                    <th scope="col" className="lestvica__gibanje">Gib.</th>
                    <th scope="col">Igralec</th>
                    <th scope="col">Klub</th>
                    <th scope="col" className="lestvica__stevilka lestvica__izid-glava">
                      Z – P
                    </th>
                    <th scope="col" className="lestvica__rating">Rating</th>
                    <th scope="col" className="lestvica__delta">Δ 30 dni</th>
                  </tr>
                </thead>
                <tbody>
                  {prikazani.map(({ igralec, mesto }) => {
                    const gib = gibanje(igralec.premik)
                    const delta = spremembaRatinga(igralec.spremembaRatinga)
                    return (
                      <tr
                        key={igralec.idIgralca}
                        className={
                          igralec.idIgralca === mojIdIgralec ? 'lestvica__vrstica--jaz' : undefined
                        }
                      >
                        {/* Prva tri mesta so modra - edini poudarek v stolpcu mest. */}
                        <td
                          className={
                            'lestvica__mesto' + (mesto <= 3 ? ' lestvica__mesto--vrh' : '')
                          }
                        >
                          {mesto}
                        </td>
                        {/* Puščica ni edini nosilec pomena: barvo podvoji opis. */}
                        <td className={`lestvica__gibanje lestvica__gibanje--${gib.smer}`}>
                          <span aria-label={gib.opis}>{gib.zapis}</span>
                        </td>
                        <td className="lestvica__igralec">
                          <Link to={`/igralci/${igralec.idIgralca}/profil`} className="lestvica__ime">
                            {igralec.polnoIme}
                          </Link>
                        </td>
                        <td className="lestvica__klub">{igralec.klub ?? '—'}</td>
                        {/* Zmage in porazi nista dve številki eno za drugo,
                            ampak stolpca ob ločilu: pomišljaj stoji na isti
                            navpičnici v vseh vrsticah, sicer se "12 – 3" in
                            "9 – 11" zamakneta in stolpca ni več mogoče brati
                            navzdol. */}
                        <td className="lestvica__stevilka">
                          <span className="lestvica__izid">
                            <span className="lestvica__zmage">{igralec.zmage}</span>
                            <span className="lestvica__locilo">–</span>
                            <span className="lestvica__porazi">{igralec.porazi}</span>
                          </span>
                        </td>
                        <td className="lestvica__rating">{igralec.rating ?? '—'}</td>
                        <td className={`lestvica__delta lestvica__delta--${delta.smer}`}>
                          {delta.zapis}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <p className="lestvica__opomba">Gib. in Δ: primerjava s stanjem pred 30 dnevi</p>
          </>
        )}

        {namig}
      </div>
    </section>
  )
}

/* Vrstica lestvice na telefonu: mesto, ime, mono "klub · Z–P · gibanje" in
   rating kot največja številka ob desnem robu.

   Puščica ni edini nosilec pomena - barvo in smer podvoji opis iz gibanje(),
   ki ga prebere bralnik zaslona. */
function VrsticaLestviceMobi({
  igralec,
  mesto,
  jaz,
}: {
  igralec: LestvicaIgralcaDto
  mesto: number
  jaz: boolean
}) {
  const gib = gibanje(igralec.premik)

  return (
    <div
      className={
        'lestvica-mobi__vrstica' + (jaz ? ' lestvica-mobi__vrstica--jaz' : '')
      }
    >
      <span
        className={
          'lestvica-mobi__mesto' + (mesto <= 3 ? ' lestvica-mobi__mesto--vrh' : '')
        }
      >
        {mesto}
      </span>
      <Link to={`/igralci/${igralec.idIgralca}/profil`} className="lestvica-mobi__ime">
        {igralec.polnoIme}
      </Link>
      <span className="lestvica-mobi__meta">
        {igralec.klub ?? '—'} · {igralec.zmage}–{igralec.porazi} ·{' '}
        <span className={`lestvica-mobi__gib--${gib.smer}`} aria-label={gib.opis}>
          {gib.zapis}
        </span>
      </span>
      <span className="lestvica-mobi__rating">{igralec.rating ?? '—'}</span>
    </div>
  )
}

/* Delež zmag; kdor še ni igral, gre na konec seznama in ne na vrh. */
function uspesnost(igralec: LestvicaIgralcaDto): number {
  return igralec.odigrane > 0 ? igralec.zmage / igralec.odigrane : -1
}

/* Premik mesta proti stanju pred 30 dnevi. Brez podatka in brez premika sta
   isti zapis (nevtralna črtica) — v obeh primerih se ni kaj pokazati. */
function gibanje(premik: number | null): { zapis: string; smer: string; opis: string } {
  if (premik === null) return { zapis: '–', smer: 'brez', opis: 'Ni podatka o premiku' }
  if (premik === 0) return { zapis: '–', smer: 'brez', opis: 'Brez premika' }
  const mest = Math.abs(premik)
  return premik > 0
    ? { zapis: `↑${mest}`, smer: 'gor', opis: `Napredoval za ${mest} ${sklonMest(mest)}` }
    : { zapis: `↓${mest}`, smer: 'dol', opis: `Nazadoval za ${mest} ${sklonMest(mest)}` }
}

/* Razlika ratinga proti stanju pred 30 dnevi. Minus je tipografski (U+2212),
   da je enako širok kot plus in se stolpec poravna. */
function spremembaRatinga(sprememba: number | null): { zapis: string; smer: string } {
  if (sprememba === null) return { zapis: '—', smer: 'brez' }
  if (sprememba === 0) return { zapis: '±0', smer: 'brez' }
  return sprememba > 0
    ? { zapis: `+${sprememba}`, smer: 'gor' }
    : { zapis: `−${Math.abs(sprememba)}`, smer: 'dol' }
}

/* Slovnično pravilna oblika besede "mesto" glede na število. */
function sklonMest(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'mesto'
  if (ostanek === 2) return 'mesti'
  if (ostanek === 3 || ostanek === 4) return 'mesta'
  return 'mest'
}

/* Slovnično pravilna oblika besede "igralec" glede na število. */
function sklonIgralcev(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'igralec'
  if (ostanek === 2) return 'igralca'
  if (ostanek === 3 || ostanek === 4) return 'igralci'
  return 'igralcev'
}

/* Slovnično pravilna oblika besede "klub" glede na število. */
function sklonKlubov(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'klub'
  if (ostanek === 2) return 'kluba'
  if (ostanek === 3 || ostanek === 4) return 'klubi'
  return 'klubov'
}
