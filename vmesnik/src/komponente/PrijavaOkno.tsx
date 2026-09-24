/* Okno za prijavo, registracijo s potrditvijo e-pošte in pozabljeno geslo.

   Prijavijo se administrator (uporabniško ime) in igralci (e-pošta). Ker gre
   za isto polje na strežniku, je obrazec en sam. Registracija: po vnosu
   podatkov lastnik naslova dobi šestmestno kodo in jo vpiše tukaj; mlajši
   od 15 let nato še kodo, ki jo je dobil starš oz. skrbnik. Če se ime,
   priimek in datum rojstva ujemajo z natanko enim igralcem v šifrantu, je
   račun povezan takoj; sicer ga poveže administrator. Kdor se prijavi z
   računom, ki naslova še ni potrdil, pristane naravnost pri vpisu kode. */
import { useEffect, useState, type FormEvent } from 'react'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { opisNapake } from '../api/odjemalec'
import { authApi } from '../api/zahteve'
import type { NamenKode, PotrditevOdgovorDto } from '../api/tipi'
import type { ShranjenoStanjePlacila } from '../pomozno/registracijaSeja'
import { KodaVnos } from './KodaVnos'
import { ModalnoOkno } from './ModalnoOkno'
import { RegistracijaTok } from './RegistracijaTok'

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
  obnovljenoStanjePlacila = null,
  naPaketu = false,
}: {
  onZapri: () => void
  /* Korak, na katerem se okno odpre (npr. "registracija" z domače strani ali
     "koda" za prijavljenega, ki naslova še ni potrdil). */
  zacetniNacin?: Nacin
  /* Naslov računa, ki čaka na kodo (samo z načinom "koda"). */
  email?: string | null
  /* Naslov je že potrjen, manjka skrbnikova koda. */
  kodaSkrbnika?: boolean
  /* Nadaljevanje registracijskega toka po vrnitvi s Stripe Checkouta (glej
     RegistracijaZakljucenaStran): okno se odpre naravnost na koraku "koda". */
  obnovljenoStanjePlacila?: ShranjenoStanjePlacila | null
  /* Samo z načinom "registracija": čarovnik se odpre na koraku "paket" z
     izbranim Igralcem Premium za eno leto (klik na maskoto). */
  naPaketu?: boolean
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

  /* Registracija ima svojo lupino (razdeljeno okno s temnim stolpcem korakov
     - glej design_handoff_onboarding) in ne deli je s prijavo/kodo/pozabljenim
     geslom, ki ostanejo pri običajnem ModalnoOkno z zavihkoma. */
  if (nacin === 'registracija') {
    return (
      <ModalnoOkno razdeljeno naslov={NASLOVI.registracija} onZapri={onZapri}>
        <RegistracijaTok
          onZapri={onZapri}
          onNazajNaPrijavo={() => naPrijavo()}
          obnovljenoStanje={obnovljenoStanjePlacila}
          naPaketu={naPaketu}
        />
      </ModalnoOkno>
    )
  }

  return (
    <ModalnoOkno nadnaslov="Turnirko" naslov={NASLOVI[nacin]} onZapri={onZapri}>
      {nacin === 'prijava' && (
        <PrijavaObrazec
          obvestilo={obvestilo}
          onZapri={onZapri}
          onKoda={naKodo}
          onPozabljeno={() => nastaviNacin('pozabljeno')}
          onRegistracija={() => nastaviNacin('registracija')}
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
  onRegistracija,
}: {
  obvestilo: string | null
  onZapri: () => void
  onKoda: (email: string, korak?: KorakKode) => void
  onPozabljeno: () => void
  onRegistracija: () => void
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
        <span>E-pošta</span>
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

      {/* Gumba sta neposredna otroka .obrazec (flex stolpec), zato se razteg-
          neta cez celo sirino okna. Nov racun je korak v drugo okno (onboarding)
          in ne del obrazca, a ima isto tezo kot prijava: to sta dve enakovredni
          vstopni tocki, gost pa pride sem, ker racuna se nima. Okno se zapre z
          Escape ali krizem. */}
      <button type="submit" className="gumb gumb--glavni" disabled={poteka}>
        {poteka ? 'Prijavljam …' : 'Prijava'}
      </button>
      <button type="button" className="gumb gumb--glavni" onClick={onRegistracija}>
        Ustvari nov račun
      </button>
      <button type="button" className="povezava-gumb" onClick={onPozabljeno}>
        Pozabljeno geslo?
      </button>
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

  async function potrdiKodo(vpisanaKoda: string) {
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      const vnos = { email: stanje.email, koda: vpisanaKoda }
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

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    void potrdiKodo(koda)
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
      <div
        className="obrazec__polje"
        role="group"
        aria-label={korak === 'EPOSTA' ? 'Koda iz e-pošte' : 'Koda skrbnika'}
      >
        <span>{korak === 'EPOSTA' ? 'Koda iz e-pošte' : 'Koda skrbnika'}</span>
        <KodaVnos
          vrednost={koda}
          naSpremembo={nastaviKodo}
          naDokoncano={(v) => void potrdiKodo(v)}
          disabled={poteka}
          autoFocus
        />
      </div>

      {napaka && <div className="napaka">{napaka}</div>}
      {poslano && <p className="obvestilo">{poslano}</p>}

      <button
        type="submit"
        className="gumb gumb--glavni"
        disabled={poteka || koda.length !== 6}
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

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      if (korak === 'email') {
        await authApi.pozabljenoGeslo(email.trim())
        nastaviKorak('koda')
      } else {
        await authApi.novoGeslo({ email: email.trim(), koda, geslo })
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
              koraka (isto mesto v drevesu) in autoFocus se ne sproži. Koda tu
              ne potrdi samodejno (naDokoncano ni podan) - sledi ji še novo
              geslo, zato oddajo obrazca sproži šele gumb spodaj. */}
          <div key="koda" className="obrazec__polje" role="group" aria-label="Koda iz e-pošte">
            <span>Koda iz e-pošte</span>
            <KodaVnos vrednost={koda} naSpremembo={nastaviKodo} disabled={poteka} autoFocus />
          </div>
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
          disabled={poteka || (korak === 'koda' && koda.length !== 6)}
        >
          {poteka ? 'Pošiljam …' : korak === 'email' ? 'Pošlji kodo' : 'Nastavi geslo'}
        </button>
      </div>
    </form>
  )
}
