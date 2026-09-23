/* Okno za prijavo, registracijo s potrditvijo e-pošte in pozabljeno geslo.

   Prijavijo se administrator (uporabniško ime) in igralci (e-pošta). Ker gre
   za isto polje na strežniku, je obrazec en sam. Registracija: po vnosu
   podatkov lastnik naslova dobi šestmestno kodo in jo vpiše tukaj; mlajši
   od 15 let nato še kodo, ki jo je dobil starš oz. skrbnik. Če se ime,
   priimek in datum rojstva ujemajo z natanko enim igralcem v šifrantu, je
   račun povezan takoj; sicer ga poveže administrator. Kdor se prijavi z
   računom, ki naslova še ni potrdil, pristane naravnost pri vpisu kode. */
import { useEffect, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { opisNapake } from '../api/odjemalec'
import { authApi, klubiApi, placilaApi } from '../api/zahteve'
import {
  CENA_ORGANIZATOR_LETNO,
  CENA_PREMIUM_MESECNO,
  MESECEV_V_LETNI_NAROCNINI,
  OMEJITVE_ORGANIZATORJA,
  type CiklusPlacila,
  type NamenKode,
  type Paket,
  type PotrditevOdgovorDto,
} from '../api/tipi'
import { jeStarejsiOd21ZaCeno, oblikujCeno } from '../pomozno/oblikovanje'
import { IzbirnikKluba } from './IzbirnikKluba'
import { ModalnoOkno } from './ModalnoOkno'

type Nacin = 'prijava' | 'registracija' | 'koda' | 'pozabljeno'

/* Kateri korak vpisa kode teče: lastni naslov ali skrbnikova koda. */
type KorakKode = Exclude<NamenKode, 'GESLO'>

interface StanjeKode {
  email: string
  korak: KorakKode
  /* Koda je šla ven pravkar (registracija). Kdor pride iz prijave ali menija,
     kode ni dobil zdaj - besedilo ne sme trditi drugače, gumb za novo kodo
     pa je na voljo takoj. */
  posljanaZdaj: boolean
}

/* Sekund, preden je mogoče zahtevati novo kodo (strežnik dovoli eno na minuto). */
const ODMIK_PONOVNEGA_POSILJANJA = 60

const NASLOVI: Record<Nacin, string> = {
  prijava: 'Prijava',
  registracija: 'Registracija',
  koda: 'Potrditev e-pošte',
  pozabljeno: 'Pozabljeno geslo',
}

export function PrijavaOkno({
  onZapri,
  zacetniNacin = 'prijava',
  email = null,
  kodaSkrbnika = false,
}: {
  onZapri: () => void
  /* Zavihek, na katerem se okno odpre (npr. iz menija "Ustvari račun" ali
     "Vpiši kodo" za prijavljenega, ki naslova še ni potrdil). */
  zacetniNacin?: Nacin
  /* Naslov računa, ki čaka na kodo (samo z načinom "koda"). */
  email?: string | null
  /* Naslov je že potrjen, manjka skrbnikova koda. */
  kodaSkrbnika?: boolean
}) {
  const [nacin, nastaviNacin] = useState<Nacin>(zacetniNacin)
  const [koda, nastaviKodo] = useState<StanjeKode | null>(
    email ? { email, korak: kodaSkrbnika ? 'SKRBNIK' : 'EPOSTA', posljanaZdaj: false } : null,
  )
  /* Sporočilo nad prijavo po končanem koraku (koda vpisana, geslo nastavljeno). */
  const [obvestilo, nastaviObvestilo] = useState<string | null>(null)

  function naPrijavo(sporocilo: string | null = null) {
    nastaviObvestilo(sporocilo)
    nastaviNacin('prijava')
  }

  function naKodo(emailRacuna: string, korak: KorakKode = 'EPOSTA', posljanaZdaj = false) {
    nastaviKodo({ email: emailRacuna, korak, posljanaZdaj })
    nastaviNacin('koda')
  }

  const zavihki = nacin === 'prijava' || nacin === 'registracija'

  return (
    <ModalnoOkno nadnaslov="Turnirko" naslov={NASLOVI[nacin]} onZapri={onZapri}>
      {zavihki && (
        <div className="zavihki">
          <button
            className={'zavihki__gumb' + (nacin === 'prijava' ? ' zavihki__gumb--aktiven' : '')}
            onClick={() => naPrijavo()}
          >
            Prijava
          </button>
          <button
            className={
              'zavihki__gumb' + (nacin === 'registracija' ? ' zavihki__gumb--aktiven' : '')
            }
            onClick={() => nastaviNacin('registracija')}
          >
            Nov račun
          </button>
        </div>
      )}

      {nacin === 'prijava' && (
        <PrijavaObrazec
          obvestilo={obvestilo}
          onZapri={onZapri}
          onKoda={naKodo}
          onPozabljeno={() => nastaviNacin('pozabljeno')}
        />
      )}
      {nacin === 'registracija' && (
        <RegistracijaObrazec
          onNazaj={() => naPrijavo()}
          onKoda={(emailRacuna) => naKodo(emailRacuna, 'EPOSTA', true)}
        />
      )}
      {nacin === 'koda' && koda && (
        <KodaObrazec stanje={koda} onPrijava={naPrijavo} onZapri={onZapri} />
      )}
      {nacin === 'pozabljeno' && (
        <PozabljenoObrazec onNazaj={() => naPrijavo()} onKonec={naPrijavo} />
      )}
    </ModalnoOkno>
  )
}

function PrijavaObrazec({
  obvestilo,
  onZapri,
  onKoda,
  onPozabljeno,
}: {
  obvestilo: string | null
  onZapri: () => void
  onKoda: (email: string, korak?: KorakKode) => void
  onPozabljeno: () => void
}) {
  const { prijava } = useAvtentikacija()
  const [uporabniskoIme, nastaviUporabniskoIme] = useState('')
  const [geslo, nastaviGeslo] = useState('')
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      const profil = await prijava(uporabniskoIme.trim(), geslo)
      /* Račun brez potrjenega naslova ali brez skrbnikove kode ne čaka
         admina, ampak kodo - okno gre naravnost tja. */
      if (profil.vloga !== 'ADMIN' && (!profil.emailPotrjen || profil.potrebnaKodaSkrbnika)) {
        onKoda(profil.uporabniskoIme, profil.emailPotrjen ? 'SKRBNIK' : 'EPOSTA')
        return
      }
      onZapri()
    } catch (e) {
      // 401 pomeni napačne poverilnice; sicer splošno sporočilo (tudi 429)
      nastaviNapako(
        (e as { stanje?: number })?.stanje === 401
          ? 'Napačno uporabniško ime oz. e-pošta ali geslo.'
          : opisNapake(e),
      )
    } finally {
      nastaviPoteka(false)
    }
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      {obvestilo && <p className="obvestilo">{obvestilo}</p>}
      <label className="obrazec__polje">
        <span>Uporabniško ime ali e-pošta</span>
        <input
          value={uporabniskoIme}
          onChange={(d) => nastaviUporabniskoIme(d.target.value)}
          autoComplete="username"
          autoFocus
          required
        />
      </label>
      <label className="obrazec__polje">
        <span>Geslo</span>
        <input
          type="password"
          value={geslo}
          onChange={(d) => nastaviGeslo(d.target.value)}
          autoComplete="current-password"
          required
        />
      </label>

      {napaka && <div className="napaka">{napaka}</div>}

      {/* Edino dejanje obrazca: gumb je neposreden otrok .obrazec (flex stolpec),
          zato se raztegne cez celo sirino okna. Okno se zapre z Escape ali krizem. */}
      <button type="submit" className="gumb gumb--glavni" disabled={poteka}>
        {poteka ? 'Prijavljam …' : 'Prijava'}
      </button>
      <button type="button" className="povezava-gumb" onClick={onPozabljeno}>
        Pozabljeno geslo?
      </button>
    </form>
  )
}

/* Starost v letih iz ISO datuma; null, če datum ni berljiv. Isto pravilo
   kot na strežniku (Period), ki je zadnja beseda. */
function starost(iso: string): number | null {
  if (!iso) return null
  const rojstvo = new Date(iso)
  if (Number.isNaN(rojstvo.getTime())) return null
  const danes = new Date()
  let leta = danes.getFullYear() - rojstvo.getFullYear()
  const mesec = danes.getMonth() - rojstvo.getMonth()
  if (mesec < 0 || (mesec === 0 && danes.getDate() < rojstvo.getDate())) leta--
  return leta
}

function RegistracijaObrazec({
  onNazaj,
  onKoda,
}: {
  onNazaj: () => void
  onKoda: (email: string) => void
}) {
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const [organizator, nastaviOrganizator] = useState(false)
  const [ime, nastaviIme] = useState('')
  const [priimek, nastaviPriimek] = useState('')
  const [idKlub, nastaviKlub] = useState('')
  const [datumRojstva, nastaviDatumRojstva] = useState('')
  const [emailSkrbnika, nastaviEmailSkrbnika] = useState('')
  const [email, nastaviEmail] = useState('')
  const [geslo, nastaviGeslo] = useState('')
  /* Plan je ločen od vloge, a je vezan nanjo: Premium je samo igralčev,
     organizatorski trije paketi samo njegovi - preklop vloge zato ponastavi
     tudi plan (glej vlogaSePrestavi). */
  const [paket, nastaviPaket] = useState<Paket>('BREZPLACNO')
  const [ciklus, nastaviCiklus] = useState<CiklusPlacila>('MESECNO')
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)

  const leta = organizator ? null : starost(datumRojstva)
  const potrebujeSkrbnika = leta !== null && leta < 15
  const danes = new Date().toISOString().slice(0, 10)

  /* Samo za PREDOGLED cene v obrazcu - zaledje (CenikStoritev) ob placilu
     ceno prera znova iz res vpisanega datuma, to je zadnja beseda. */
  const starejsiOd21 = jeStarejsiOd21ZaCeno(datumRojstva) ?? false
  const cenaPremiumMesecno = starejsiOd21 ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_MESECNO.mlajsi
  const cenaPremiumLetno = Math.round(cenaPremiumMesecno * MESECEV_V_LETNI_NAROCNINI * 100) / 100

  function vlogaSePrestavi(novOrganizator: boolean) {
    nastaviOrganizator(novOrganizator)
    nastaviPaket(novOrganizator ? 'ORGANIZATOR_BASIC' : 'BREZPLACNO')
  }

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      const racun = {
        ime: ime.trim(),
        priimek: priimek.trim(),
        idKlub: idKlub ? Number(idKlub) : null,
        email: email.trim(),
        geslo,
        organizator,
        datumRojstva: organizator ? null : datumRojstva,
        emailSkrbnika: potrebujeSkrbnika ? emailSkrbnika.trim() : null,
      }
      if (paket === 'BREZPLACNO') {
        const odgovor = await authApi.registracija(racun)
        onKoda(odgovor.email)
        return
      }
      /* Placljiv paket: racun NE nastane tu, ampak sele ko Stripe webhook
         potrdi placilo - odgovor je naslov Stripe Checkouta. */
      const seja = await placilaApi.registracija({
        racun,
        paket,
        ciklus: paket === 'PREMIUM' ? ciklus : 'LETNO',
      })
      window.location.href = seja.url
    } catch (e) {
      nastaviNapako(opisNapake(e))
      nastaviPoteka(false)
    }
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      {/* Ime in priimek sta prva: to vpiše vsak, ne glede na vlogo. */}
      <div className="obrazec__vrstica obrazec__vrstica--par">
        <label className="obrazec__polje">
          <span>Ime *</span>
          <input value={ime} onChange={(d) => nastaviIme(d.target.value)} required />
        </label>
        <label className="obrazec__polje">
          <span>Priimek *</span>
          <input value={priimek} onChange={(d) => nastaviPriimek(d.target.value)} required />
        </label>
      </div>

      {/* Vloga je navadno polje obrazca, ne zavihek: zavihki obljubljajo
          preklop med dvema pogledoma, tu pa gre za en sam vnos, ki potuje na
          streznik skupaj z ostalimi. Igralec vidi svoj profil, organizator
          vodi tekmovanja; organizatorja potrdi administrator. */}
      <label className="obrazec__polje">
        <span>Vloga *</span>
        <select
          value={organizator ? 'organizator' : 'igralec'}
          onChange={(d) => vlogaSePrestavi(d.target.value === 'organizator')}
        >
          <option value="igralec">Igralec</option>
          <option value="organizator">Organizator</option>
        </select>
      </label>

      {/* Pojasnilo ostane samo organizatorju: njegova vloga ni samoumevna,
          igralcu pa polja sama povedo dovolj. */}
      {organizator && (
        <p className="modal__podnaslov">
          Registracija organizatorja (klub oz. oseba, ki vodi tekmovanja). Vpiši
          kontaktno ime in klub, ki ga zastopaš; administrator ti po potrditvi dodeli
          vlogo in klub.
        </p>
      )}

      <IzbirnikKluba
        oznaka={organizator ? 'Klub, ki ga zastopaš' : 'Klub'}
        namig="Vpiši ime kluba ali pusti prazno"
        klubi={klubi.data ?? []}
        izbrano={idKlub ? Number(idKlub) : null}
        naSpremembo={(id) => nastaviKlub(id === null ? '' : String(id))}
      />

      {/* Datum rojstva: po njem se račun samodejno poveže z zapisom igralca
          in določi starostna kategorija. Javno ni viden. */}
      {!organizator && (
        <label className="obrazec__polje">
          <span>Datum rojstva * (za povezavo s tvojim zapisom med igralci; javno ni viden)</span>
          <input
            type="date"
            value={datumRojstva}
            max={danes}
            onChange={(d) => nastaviDatumRojstva(d.target.value)}
            required
          />
        </label>
      )}

      {/* Pojasnilo stoji zunaj oznake: span v .obrazec__polje dobi slog
          oznake (mono, velike črke) in bi se bral kot drugo polje. */}
      {potrebujeSkrbnika && (
        <>
          <label className="obrazec__polje">
            <span>E-pošta starša oz. skrbnika * (mlajši od 15 let)</span>
            <input
              type="email"
              value={emailSkrbnika}
              onChange={(d) => nastaviEmailSkrbnika(d.target.value)}
              required
            />
          </label>
          <p className="modal__podnaslov">
            Na ta naslov gre koda s soglasjem. Vpišeš jo po svoji kodi.
          </p>
        </>
      )}

      <label className="obrazec__polje">
        <span>E-pošta * (z njo se prijaviš; nanjo dobiš kodo)</span>
        <input
          type="email"
          value={email}
          onChange={(d) => nastaviEmail(d.target.value)}
          autoComplete="email"
          required
        />
      </label>
      <label className="obrazec__polje">
        <span>Geslo * (vsaj 8 znakov)</span>
        <input
          type="password"
          value={geslo}
          onChange={(d) => nastaviGeslo(d.target.value)}
          autoComplete="new-password"
          minLength={8}
          required
        />
      </label>

      {/* Plan: brezplačno je na voljo samo igralcu (kot gost, le prijavljen);
          organizator vedno izbere enega od treh plačljivih paketov - obseg
          (koliko lig/turnirjev na sezono) je edina razlika med njimi. */}
      {!organizator && (
        <label className="obrazec__polje">
          <span>Plan *</span>
          <select value={paket} onChange={(d) => nastaviPaket(d.target.value as Paket)}>
            <option value="BREZPLACNO">Brezplačno — kot gost, le prijavljen</option>
            <option value="PREMIUM">Premium — zasebna statistika in spremljanje lig</option>
          </select>
        </label>
      )}

      {!organizator && paket === 'PREMIUM' && (
        <label className="obrazec__polje">
          <span>Plačevanje *</span>
          <select value={ciklus} onChange={(d) => nastaviCiklus(d.target.value as CiklusPlacila)}>
            <option value="MESECNO">{oblikujCeno(cenaPremiumMesecno)} / mesec</option>
            <option value="LETNO">
              {oblikujCeno(cenaPremiumLetno)} / leto ({MESECEV_V_LETNI_NAROCNINI}× mesečna cena)
            </option>
          </select>
        </label>
      )}

      {organizator && (
        <label className="obrazec__polje">
          <span>Paket *</span>
          <select value={paket} onChange={(d) => nastaviPaket(d.target.value as Paket)}>
            <option value="ORGANIZATOR_BASIC">
              Basic — {oblikujCeno(CENA_ORGANIZATOR_LETNO.ORGANIZATOR_BASIC)}/leto (
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_BASIC.lig} tekoča liga,{' '}
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_BASIC.turnirjev} turnirja na sezono)
            </option>
            <option value="ORGANIZATOR_PLUS">
              Plus — {oblikujCeno(CENA_ORGANIZATOR_LETNO.ORGANIZATOR_PLUS)}/leto (
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_PLUS.lig} tekoče lige,{' '}
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_PLUS.turnirjev} turnirjev na sezono)
            </option>
            <option value="ORGANIZATOR_PRO">
              Pro — {oblikujCeno(CENA_ORGANIZATOR_LETNO.ORGANIZATOR_PRO)}/leto (
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_PRO.lig} tekočih lig,{' '}
              {OMEJITVE_ORGANIZATORJA.ORGANIZATOR_PRO.turnirjev} turnirjev na sezono)
            </option>
          </select>
        </label>
      )}

      {napaka && <div className="napaka">{napaka}</div>}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onNazaj}>
          Prekliči
        </button>
        <button type="submit" className="gumb gumb--glavni" disabled={poteka}>
          {poteka
            ? paket === 'BREZPLACNO'
              ? 'Ustvarjam …'
              : 'Preusmerjam na plačilo …'
            : paket === 'BREZPLACNO'
              ? 'Ustvari račun'
              : 'Nadaljuj na plačilo'}
        </button>
      </div>
    </form>
  )
}

/* Vpis šestmestne kode: najprej lastni naslov, pri mlajših od 15 let nato
   še skrbnikova. Po pravilni kodi pove, kaj sledi. */
function KodaObrazec({
  stanje,
  onPrijava,
  onZapri,
}: {
  stanje: StanjeKode
  onPrijava: (sporocilo: string | null) => void
  onZapri: () => void
}) {
  const { uporabnik, osvezi } = useAvtentikacija()
  const [korak, nastaviKorak] = useState<KorakKode>(stanje.korak)
  const [koda, nastaviKodo] = useState('')
  const [izid, nastaviIzid] = useState<PotrditevOdgovorDto | null>(null)
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poslano, nastaviPoslano] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)
  /* Ali je koda za trenutni korak šla ven pravkar (ob registraciji oz. ob
     prehodu na skrbnikov korak, ki ga je poslala ista registracija). */
  const [posljanaZdaj, nastaviPosljanaZdaj] = useState(stanje.posljanaZdaj)
  const [odstevanje, nastaviOdstevanje] = useState(
    stanje.posljanaZdaj ? ODMIK_PONOVNEGA_POSILJANJA : 0,
  )

  /* Odštevanje do ponovnega pošiljanja: strežnik dovoli eno kodo na minuto,
     gumb to pove vnaprej, namesto da bi klik vrnil napako. */
  useEffect(() => {
    if (odstevanje <= 0) return
    const stevec = setTimeout(() => nastaviOdstevanje((s) => s - 1), 1000)
    return () => clearTimeout(stevec)
  }, [odstevanje])

  const stevke = koda.replace(/\s/g, '')

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      const vnos = { email: stanje.email, koda: stevke }
      const odgovor =
        korak === 'EPOSTA' ? await authApi.potrdiEposto(vnos) : await authApi.potrdiSkrbnika(vnos)
      await osvezi()
      if (!odgovor.povezan && odgovor.potrebnaKodaSkrbnika) {
        /* Skrbnikova koda je odšla hkrati z otrokovo (ob registraciji), zato
           je nova na voljo takoj, razen če smo tu naravnost iz registracije. */
        nastaviKorak('SKRBNIK')
        nastaviKodo('')
        nastaviPoslano(null)
      } else {
        nastaviIzid(odgovor)
      }
    } catch (e) {
      nastaviNapako(opisNapake(e))
    } finally {
      nastaviPoteka(false)
    }
  }

  async function posljiZnova() {
    nastaviNapako(null)
    try {
      await authApi.ponovnoPoslji(stanje.email, korak)
      nastaviPoslano(korak === 'EPOSTA' ? 'Nova koda je poslana.' : 'Skrbnik je dobil novo kodo.')
      nastaviPosljanaZdaj(true)
      nastaviOdstevanje(ODMIK_PONOVNEGA_POSILJANJA)
    } catch (e) {
      nastaviNapako(opisNapake(e))
    }
  }

  if (izid) {
    const prijavljen = uporabnik !== null
    return (
      <div className="obrazec">
        <p className="obvestilo">
          {izid.povezan
            ? 'E-pošta je potrjena in račun je povezan s tvojim zapisom med igralci.'
            : izid.organizator
              ? 'E-pošta je potrjena. Račun čaka, da ti administrator dodeli vlogo organizatorja in klub.'
              : 'E-pošta je potrjena. Med igralci ni bilo natanko enega zapisa s tvojim imenom in datumom rojstva, zato račun poveže administrator.'}{' '}
          {prijavljen
            ? izid.povezan
              ? 'Tvoj profil je v meniju v desnem kotu.'
              : 'Prijavljen ostaneš; profil se odpre po povezavi.'
            : 'Prijaviš se lahko že zdaj.'}
        </p>
        <button
          type="button"
          className="gumb gumb--glavni"
          onClick={() => (prijavljen ? onZapri() : onPrijava(null))}
        >
          {prijavljen ? 'Zapri' : 'Na prijavo'}
        </button>
      </div>
    )
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      <p className="modal__podnaslov">
        {korak === 'EPOSTA'
          ? posljanaZdaj
            ? `Na ${stanje.email} smo poslali šestmestno kodo. Velja 10 minut.`
            : `E-pošta ${stanje.email} še ni potrjena. Vpiši kodo iz sporočila; če je potekla (velja 10 minut), zahtevaj novo.`
          : 'Starš oz. skrbnik je na svoj naslov dobil kodo, ki velja 24 ur. Vpiši jo tukaj.'}
      </p>
      <label className="obrazec__polje">
        <span>{korak === 'EPOSTA' ? 'Koda iz e-pošte' : 'Koda skrbnika'}</span>
        <input
          className="obrazec__koda"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9 ]*"
          maxLength={7}
          value={koda}
          onChange={(d) => nastaviKodo(d.target.value)}
          autoFocus
          required
        />
      </label>

      {napaka && <div className="napaka">{napaka}</div>}
      {poslano && <p className="obvestilo">{poslano}</p>}

      <button
        type="submit"
        className="gumb gumb--glavni"
        disabled={poteka || stevke.length !== 6}
      >
        {poteka ? 'Preverjam …' : 'Potrdi'}
      </button>
      <button
        type="button"
        className="povezava-gumb"
        disabled={odstevanje > 0}
        onClick={posljiZnova}
      >
        {odstevanje > 0 ? `Pošlji kodo znova (${odstevanje} s)` : 'Pošlji kodo znova'}
      </button>
    </form>
  )
}

/* Pozabljeno geslo: naslov, nato koda in novo geslo. Strežnik nikoli ne pove,
   ali račun obstaja - besedilo to pove vnaprej. */
function PozabljenoObrazec({
  onNazaj,
  onKonec,
}: {
  onNazaj: () => void
  onKonec: (sporocilo: string) => void
}) {
  const [korak, nastaviKorak] = useState<'email' | 'koda'>('email')
  const [email, nastaviEmail] = useState('')
  const [koda, nastaviKodo] = useState('')
  const [geslo, nastaviGeslo] = useState('')
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)

  const stevke = koda.replace(/\s/g, '')

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      if (korak === 'email') {
        await authApi.pozabljenoGeslo(email.trim())
        nastaviKorak('koda')
      } else {
        await authApi.novoGeslo({ email: email.trim(), koda: stevke, geslo })
        onKonec('Geslo je nastavljeno. Prijavi se z novim geslom.')
      }
    } catch (e) {
      nastaviNapako(opisNapake(e))
    } finally {
      nastaviPoteka(false)
    }
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      {korak === 'email' ? (
        <>
          <p className="modal__podnaslov">
            Vpiši e-pošto, s katero se prijavljaš. Če račun obstaja in je naslov potrjen,
            dobiš kodo za novo geslo.
          </p>
          <label className="obrazec__polje">
            <span>E-pošta</span>
            <input
              type="email"
              value={email}
              onChange={(d) => nastaviEmail(d.target.value)}
              autoComplete="email"
              autoFocus
              required
            />
          </label>
        </>
      ) : (
        <>
          <p className="modal__podnaslov">
            Če račun s tem naslovom obstaja, si dobil kodo. Velja 10 minut.
          </p>
          {/* key: brez njega React ponovno uporabi polje za e-pošto s prejšnjega
              koraka (isto mesto v drevesu) in autoFocus se ne sproži. */}
          <label key="koda" className="obrazec__polje">
            <span>Koda iz e-pošte</span>
            <input
              className="obrazec__koda"
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9 ]*"
              maxLength={7}
              value={koda}
              onChange={(d) => nastaviKodo(d.target.value)}
              autoFocus
              required
            />
          </label>
          <label key="geslo" className="obrazec__polje">
            <span>Novo geslo (vsaj 8 znakov)</span>
            <input
              type="password"
              value={geslo}
              onChange={(d) => nastaviGeslo(d.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </label>
        </>
      )}

      {napaka && <div className="napaka">{napaka}</div>}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onNazaj}>
          Nazaj
        </button>
        <button
          type="submit"
          className="gumb gumb--glavni"
          disabled={poteka || (korak === 'koda' && stevke.length !== 6)}
        >
          {poteka ? 'Pošiljam …' : korak === 'email' ? 'Pošlji kodo' : 'Nastavi geslo'}
        </button>
      </div>
    </form>
  )
}
