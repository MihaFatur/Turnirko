/* Globalna lestvica igralcev po Turnirko ratingu, z razmerjem
   zmag in porazov prek vseh dogodkov. Vidna vsem (tudi gostom).

   Na telefonu je vrstica DRUGO drevo in ne ožja tabela: sedem stolpcev se je
   pri 390 px ali odrezalo ali prelomilo. Tu je vrstica mreža treh stolpcev -
   mesto, ime z mono vrstico »klub · Z–P · gibanje« in rating kot največja
   številka ob desnem robu. Stolpec »Δ 30 dni« odpade, ker isto pove gibanje.

   Nad seznamom je ena sama vrstica krmil (gumb »Filtriraj« in izbor
   razvrstitve), iskanje pa je na telefonu preklopnik v lepljivi glavi. Pasova
   segmentiranih gumbov (spol, tekmovalci/rekreativci) sta odšla v okno z
   merili: nad tabelo sta bila dve vrsti krmil, preden je gledalec prišel do
   prvega imena.

   SPOL NI FILTER, AMPAK IZBIRA LESTVICE: med moškimi in ženskami ni niti ene
   obračunane tekme (0 od 91.741), zato sta skali neprimerljivi in skupno mesto
   ne pomeni ničesar. V oknu je zato izbira natanko ene možnosti (IzbiraEne) —
   odznačiti se je ne da in »Počisti« je ne odnese —, izbrana lestvica pa
   stoji ob naslovu, ker med žetoni filtrov ni. Privzeto se odpre lestvica
   gledalčevega spola, če je gledalec na njej; sicer moška (večja).

   Kategorija je navaden filter: U11 … U21, člani in rekreativci, več hkrati,
   brez izbire vsi. Rekreativci so svoja kategorija ne glede na starost —
   dober rekreativec ne sme med mladinci prehiteti nekoliko slabšega igralca,
   ki hodi na turnirje NTZS. Druga skupina filtra je klub.

   Iskanje po imenu ni skupina filtra, ampak zoži seznam PRED njim — števci ob
   merilih so tako vedno števci tega, kar gledalec vidi. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi } from '../api/zahteve'
import type { LestvicaIgralcaDto, Spol } from '../api/tipi'
import {
  IzbiraEne,
  KrmilaSeznama,
  poSeznamu,
  useFiltri,
  type Razvrstitev,
  type SkupinaFiltra,
} from '../komponente/Filtri'
import { IskalnikSeznama, IskanjeTelefona } from '../komponente/IskanjeSeznama'
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

const ISKANJE_PO = 'po imenu ali klubu'

const OZNAKE_SPOLA: Record<Spol, string> = { MOSKI: 'Moški', ZENSKI: 'Ženske' }

/* Kategorije lestvice od najmlajše navzgor; rekreativci na koncu, ker niso
   starost, ampak svoja lestvica. */
const KATEGORIJE = ['U11', 'U13', 'U15', 'U17', 'U19', 'U21', 'CLANI', 'REKREATIVCI']

const OZNAKE_KATEGORIJ: Record<string, string> = {
  U11: 'U11',
  U13: 'U13',
  U15: 'U15',
  U17: 'U17',
  U19: 'U19',
  U21: 'U21',
  CLANI: 'Člani',
  REKREATIVCI: 'Rekreativci',
}

const SKUPINE: SkupinaFiltra<Vrstica>[] = [
  {
    kljuc: 'kategorija',
    oznaka: 'Kategorija',
    vrednost: ({ igralec }) => kategorija(igralec),
    napis: (v) => OZNAKE_KATEGORIJ[v] ?? v,
    vrstniRed: poSeznamu(KATEGORIJE),
  },
  { kljuc: 'klub', oznaka: 'Klub', vrednost: ({ igralec }) => igralec.klub },
]

/* Kategorija igralca na lestvici. Starost je starostni pas, ki ga izpelje
   strežnik (isti kot pri prijavah na dogodek) — datuma rojstva vmesnik nima in
   ga tudi ne sme imeti, ker je osebni podatek. Veterani so na lestvici člani:
   kategorij je osem in veteranske med njimi ni. Rekreativec je rekreativec ne
   glede na starost (glej uvod). Brez letnice kategorije ni in igralca izbor
   kategorije ne zajame. */
function kategorija(igralec: LestvicaIgralcaDto): string | null {
  if (igralec.rekreativec) return 'REKREATIVCI'
  if (igralec.starostniPas === 'VETERANI') return 'CLANI'
  return igralec.starostniPas
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
]

export function LestvicaStran() {
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })
  const { mojIdIgralec } = useAvtentikacija()
  const jeTelefon = useTelefon()
  const [iskanje, nastaviIskanje] = useState('')
  const [spol, nastaviSpol] = useState<Spol | null>(null)

  /* Koliko igralcev je na kateri lestvici - za števce v oknu in za privzetek.
     Igralec brez spola (v bazi je obvezen, a tip dopušča null) ne pripada
     nobeni od obeh lestvic in se ne šteje nikjer. */
  const steviloNaLestvici = useMemo(() => {
    const stevci: Record<Spol, number> = { MOSKI: 0, ZENSKI: 0 }
    for (const v of lestvica.data ?? []) {
      if (v.spol !== null) stevci[v.spol]++
    }
    return stevci
  }, [lestvica.data])

  /* Privzeti spol: gledalčev, če je na lestvici; sicer moška (večja), razen
     kadar moških na lestvici ni. Izbira ene od dveh lestvic kot "prve" je
     neizogibna, zato naj bo gledalčeva. */
  const izbraniSpol: Spol = spol
    ?? (lestvica.data ?? []).find((v) => v.idIgralca === mojIdIgralec)?.spol
    ?? (steviloNaLestvici.MOSKI > 0 || steviloNaLestvici.ZENSKI === 0 ? 'MOSKI' : 'ZENSKI')

  /* Igralci ene lestvice. Mesto se pripne TU, ker je mesto na svoji lestvici
     in ne v skupnem seznamu obeh spolov. */
  const izbranaLestvica = useMemo(
    () =>
      (lestvica.data ?? [])
        .filter((v) => v.spol === izbraniSpol)
        .map((igralec, indeks) => ({ igralec, mesto: indeks + 1 })),
    [lestvica.data, izbraniSpol],
  )

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
     širinah. Spol se ne šteje: je izbira lestvice in ne izsek iz nje. */
  const zozeno = filtri.steviloIzbranih > 0 || iskanje.trim() !== ''

  const sklon = izbraniSpol === 'ZENSKI' ? sklonIgralk : sklonIgralcev

  /* Izbrana lestvica stoji ob naslovu in ne med žetoni: žeton se da
     odstraniti, lestvica pa je vedno natanko ena. Na telefonu število klubov
     odpade — ob spolu se je števec pri 375 px odrezal sredi besede. */
  const stevecLestvice =
    `${OZNAKE_SPOLA[izbraniSpol]} · ` +
    (zozeno
      ? `prikazanih ${prikazani.length} od ${vseh}`
      : `${vseh} ${sklon(vseh)}` + (jeTelefon ? '' : ` · ${klubov} ${sklonKlubov(klubov)}`))

  /* Obe lestvici morata obstajati, sicer ni česa izbirati (isto pravilo kot
     skupina filtra z eno samo vrednostjo). */
  const izbiraSpola =
    steviloNaLestvici.MOSKI > 0 && steviloNaLestvici.ZENSKI > 0 ? (
      <IzbiraEne
        oznaka="Spol"
        moznosti={(['MOSKI', 'ZENSKI'] as Spol[]).map((v) => ({
          vrednost: v,
          napis: OZNAKE_SPOLA[v],
          stevec: steviloNaLestvici[v],
        }))}
        izbrana={izbraniSpol}
        naIzbiro={(v) => nastaviSpol(v as Spol)}
      />
    ) : undefined

  const krmila = (
    <KrmilaSeznama
      stanje={filtri}
      razvrstitve={RAZVRSTITVE}
      naslovOkna="Lestvica"
      imeZadetkov={sklon}
      vOknu={izbiraSpola}
    />
  )

  const opisTabele =
    `Lestvica igralcev po Turnirko ratingu — ${OZNAKE_SPOLA[izbraniSpol]}` +
    (filtri.zetoni.length > 0
      ? `; izbrano: ${filtri.zetoni.map((z) => `${z.oznaka} ${z.napis}`).join(', ')}`
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

  const namig = (
    <p className="namig">
      Igralci brez obračunane tekme še niso na lestvici; po 18 mesecih brez tekme z nje
      izginejo. Moška in ženska lestvica sta ločeni, ker med spoloma ni obračunanih tekem
      in številki nista primerljivi. Rekreativci so svoja kategorija, dokler ne odigrajo
      treh tekem na uradnem ali klubskem tekmovanju. Ime igralca vodi na profil s
      statistiko.
    </p>
  )

  if (jeTelefon) {
    return (
      <section className="stran-mobi--lestvica">
        <IskanjeTelefona iskanje={iskanje} naIskanje={nastaviIskanje} poCem={ISKANJE_PO} />

        <div>
          <span className="naslov-mobi__nad">Turnirko rating</span>
          <h1 className="naslov-mobi naslov-mobi--seznam">Lestvica</h1>

          {vseh > 0 && krmila}
        </div>

        <div>
          {/* Brez črte: krmila tik nad naslovom sodijo k istemu seznamu. */}
          <div className="naslovna-mobi naslovna-mobi--brez-crte">
            <h2>Lestvica</h2>
            {lestvica.data && (
              <span className="naslovna-mobi__stevec naslovna-mobi__stevec--drobno">
                {stevecLestvice}
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
        <div className="naslovna-vrstica naslovna-vrstica--brez-crte">
          <h2>Lestvica</h2>
          <div className="naslovna-vrstica__desno">
            <IskalnikSeznama iskanje={iskanje} naIskanje={nastaviIskanje} poCem={ISKANJE_PO} />
            {lestvica.data && <span className="sekcija__meta">{stevecLestvice}</span>}
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

/* Isto za žensko lestvico: "400 igralcev" pod naslovom Ženske se bere kot
   napaka v števcu. */
function sklonIgralk(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'igralka'
  if (ostanek === 2) return 'igralki'
  if (ostanek === 3 || ostanek === 4) return 'igralke'
  return 'igralk'
}
