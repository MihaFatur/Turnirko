/* Registracija igralca ali organizatorja, razdeljena na korake (design_handoff_onboarding,
   smer 1b): vloga → paket → podatki → klub (+ zapis med igralci) → [skrbnik,
   če je igralec mlajši od 15 let] → povzetek in plačilo → koda iz e-pošte →
   [koda skrbnika] → prvi koraki.

   Logika je bila prej v enem obrazcu (RegistracijaObrazec + KodaObrazec v
   PrijavaOkno.tsx); ta komponenta jo samo razdeli na korake in doda vlogo,
   paket, klub/zapis, povzetek in zaključek, ki jih star obrazec ni imel.
   Prijava, koda ob nepotrjenem obstoječem računu in pozabljeno geslo ostanejo
   v PrijavaOkno.tsx nespremenjeni - tja to okno ne posega. */
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { opisNapake } from '../api/odjemalec'
import { authApi, klubiApi, placilaApi } from '../api/zahteve'
import {
  CENA_ORGANIZATOR_LETNO,
  CENA_PREMIUM_LETNO,
  CENA_PREMIUM_MESECNO,
  OMEJITVE_ORGANIZATORJA,
  OZNAKE_PAKET,
  type CiklusPlacila,
  type Paket,
} from '../api/tipi'
import { jeStarejsiOd21ZaCeno, oblikujCeno, oblikujDatum } from '../pomozno/oblikovanje'
import { shraniStanjePlacila, type ShranjenoStanjePlacila } from '../pomozno/registracijaSeja'
import { useTelefon } from '../pomozno/telefon'
import { IskalniIzbirnik } from './IskalniIzbirnik'
import { KodaVnos } from './KodaVnos'
import { MaskotaTabela } from './MaskotaTabela'
import { ZnakTurnirko } from './Postavitev'

/* Sekund, preden je mogoče zahtevati novo kodo (isto pravilo kot v
   PrijavaOkno.tsx - strežnik dovoli eno na minuto, gumb to pove vnaprej). */
const ODMIK_PONOVNEGA_POSILJANJA = 60

type Vloga = 'igralec' | 'organizator'
type OrganizatorPaket = 'ORGANIZATOR_BASIC' | 'ORGANIZATOR_PLUS' | 'ORGANIZATOR_PRO'
type Korak = 'vloga' | 'paket' | 'podatki' | 'klub' | 'skrbnik' | 'povzetek' | 'koda' | 'konec'

const ORGANIZATOR_PAKETI: OrganizatorPaket[] = [
  'ORGANIZATOR_BASIC',
  'ORGANIZATOR_PLUS',
  'ORGANIZATOR_PRO',
]

const IME_KORAKA: Record<Korak, string> = {
  vloga: 'Vloga',
  paket: 'Paket',
  podatki: 'Podatki',
  klub: 'Klub',
  skrbnik: 'Skrbnik',
  povzetek: 'Povzetek',
  koda: 'Potrditev e-pošte',
  konec: 'Račun je pripravljen',
}

/* Seznam korakov je odvisen od vloge (organizator klub-a med igralci nima) in
   od tega, ali igralec potrebuje skrbnika - korak "skrbnik" se vstavi sproti. */
function koraki(vloga: Vloga, potrebujeSkrbnika: boolean): Korak[] {
  if (vloga === 'organizator') {
    return ['vloga', 'paket', 'podatki', 'klub', 'povzetek', 'koda', 'konec']
  }
  return [
    'vloga', 'paket', 'podatki', 'klub',
    ...(potrebujeSkrbnika ? (['skrbnik'] as const) : []),
    'povzetek', 'koda', 'konec',
  ]
}

/* Starost v letih iz ISO datuma; null, če datum ni berljiv. Isto pravilo kot
   na strežniku (Period). */
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

function izracunajCeno(paket: Paket, ciklus: CiklusPlacila, datumRojstva: string): number {
  if (paket === 'BREZPLACNO') return 0
  if (paket === 'PREMIUM') {
    const starejsi = jeStarejsiOd21ZaCeno(datumRojstva) ?? false
    const cene = ciklus === 'MESECNO' ? CENA_PREMIUM_MESECNO : CENA_PREMIUM_LETNO
    return starejsi ? cene.starejsi : cene.mlajsi
  }
  return CENA_ORGANIZATOR_LETNO[paket]
}

/* "0 €" brez decimalk (kot v prototipu) - oblikujCeno je splosna in vedno
   izpise dve decimalki, kar je za brezplacen paket videti kot cena. */
function cenaNapis(znesek: number): string {
  return znesek === 0 ? '0 €' : oblikujCeno(znesek)
}

interface Stanje {
  vloga: Vloga
  korak: number
  paket: Paket
  ciklus: CiklusPlacila
  ime: string
  priimek: string
  datumRojstva: string
  email: string
  geslo: string
  idKlub: string
  povezava: 'da' | 'ne' | null
  emailSkrbnika: string
  pogoji: boolean
  poteka: boolean
  napaka: string | null
  kodaKorak: 'EPOSTA' | 'SKRBNIK'
  koda: string
  posljanaZdaj: boolean
  odstevanje: number
  kodaPoslano: string | null
  izidPovezan: boolean | null
  izidOrganizator: boolean
  placanoZnesek: number | null
}

function zacetnoStanje(
  obnovljeno: ShranjenoStanjePlacila | null,
  naPaketu: boolean,
  zacetniCiklus: CiklusPlacila | undefined,
): Stanje {
  if (obnovljeno) {
    return {
      vloga: obnovljeno.vloga,
      korak: koraki(obnovljeno.vloga, obnovljeno.potrebnaKodaSkrbnika).indexOf('koda'),
      paket: obnovljeno.paket,
      ciklus: obnovljeno.ciklus ?? 'MESECNO',
      ime: obnovljeno.ime,
      priimek: obnovljeno.priimek,
      datumRojstva: '',
      email: obnovljeno.email,
      geslo: '',
      idKlub: '',
      povezava: null,
      emailSkrbnika: '',
      pogoji: true,
      poteka: false,
      napaka: null,
      kodaKorak: 'EPOSTA',
      koda: '',
      posljanaZdaj: true,
      odstevanje: ODMIK_PONOVNEGA_POSILJANJA,
      kodaPoslano: null,
      izidPovezan: null,
      izidOrganizator: obnovljeno.vloga === 'organizator',
      placanoZnesek: obnovljeno.cena,
    }
  }
  return {
    vloga: 'igralec',
    /* Klik na maskoto: izbrana vloga (igralec) je že jasna, zato okno začne na
       paketu z Igralcem Premium za eno leto. */
    korak: naPaketu ? koraki('igralec', false).indexOf('paket') : 0,
    paket: naPaketu ? 'PREMIUM' : 'BREZPLACNO',
    ciklus: naPaketu ? (zacetniCiklus ?? 'LETNO') : 'MESECNO',
    ime: '',
    priimek: '',
    datumRojstva: '',
    email: '',
    geslo: '',
    idKlub: '',
    povezava: null,
    emailSkrbnika: '',
    pogoji: false,
    poteka: false,
    napaka: null,
    kodaKorak: 'EPOSTA',
    koda: '',
    posljanaZdaj: false,
    odstevanje: 0,
    kodaPoslano: null,
    izidPovezan: null,
    izidOrganizator: false,
    placanoZnesek: null,
  }
}

export function RegistracijaTok({
  onZapri,
  onNazajNaPrijavo,
  obnovljenoStanje = null,
  naPaketu = false,
  zacetniCiklus,
}: {
  onZapri: () => void
  onNazajNaPrijavo: () => void
  /* Nadaljevanje po vrnitvi s Stripe Checkouta (glej RegistracijaZakljucenaStran):
     okno se odpre naravnost na koraku "koda". */
  obnovljenoStanje?: ShranjenoStanjePlacila | null
  /* Okno se odpre na koraku "paket" z izbranim Igralcem Premium za eno leto
     (klik na maskoto, glej Maskota.tsx). */
  naPaketu?: boolean
  /* Izbrano plačevanje ob začetku na koraku »paket« (oglas Igralec Premium);
     brez njega letno. */
  zacetniCiklus?: CiklusPlacila
}) {
  const telefon = useTelefon()
  const { prijava } = useAvtentikacija()
  const [s, nastavi] = useState<Stanje>(() =>
    zacetnoStanje(obnovljenoStanje, naPaketu, zacetniCiklus),
  )
  /* Lik v tabeli paketov nastopi enkrat na odprto okno: ob vrnitvi na korak
     (Nazaj/Naprej) ga ne bi bilo treba spet gledati. */
  const [tabelaKoncana, nastaviTabelaKoncana] = useState(false)
  const odstevalnik = useRef<ReturnType<typeof setInterval> | null>(null)

  function posodobi(delno: Partial<Stanje>) {
    nastavi((prejsnje) => ({ ...prejsnje, ...delno }))
  }

  /* Odštevanje do ponovnega pošiljanja (isto pravilo kot v PrijavaOkno.tsx -
     strežnik dovoli eno kodo na minuto). */
  function zacniOdstevanje() {
    if (odstevalnik.current) clearInterval(odstevalnik.current)
    posodobi({ odstevanje: ODMIK_PONOVNEGA_POSILJANJA })
    odstevalnik.current = setInterval(() => {
      nastavi((prejsnje) => {
        if (prejsnje.odstevanje <= 1) {
          if (odstevalnik.current) clearInterval(odstevalnik.current)
          return { ...prejsnje, odstevanje: 0 }
        }
        return { ...prejsnje, odstevanje: prejsnje.odstevanje - 1 }
      })
    }, 1000)
  }

  useEffect(() => () => {
    if (odstevalnik.current) clearInterval(odstevalnik.current)
  }, [])

  const jeIgralec = s.vloga === 'igralec'
  const leta = jeIgralec ? starost(s.datumRojstva) : null
  const potrebujeSkrbnika = leta !== null && leta < 15
  const danes = new Date().toISOString().slice(0, 10)
  const polnoIme = `${s.ime.trim()} ${s.priimek.trim()}`.trim()

  const seznamKorakov = koraki(s.vloga, potrebujeSkrbnika)
  const k = Math.min(s.korak, seznamKorakov.length - 1)
  const trenutniKorak = seznamKorakov[k]

  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const klubIzbran = klubi.data?.find((klub) => String(klub.id) === s.idKlub) ?? null

  /* Predogled zapisa (glej README design_handoff_onboarding, korak "Klub in
     zapis"): sme teči šele, ko polja z imenom/priimkom/datumom niso več
     aktivno urejana (korak "podatki"), sicer bi vsak pritisnjen znak sprožil
     svojo zahtevo - strežnik predogled tudi omejuje po IP. */
  const zunajPodatkov = trenutniKorak !== 'vloga' && trenutniKorak !== 'paket' && trenutniKorak !== 'podatki'
  const predogled = useQuery({
    queryKey: ['predogled-zapisa', s.ime.trim(), s.priimek.trim(), s.datumRojstva],
    queryFn: () =>
      authApi.predogledZapisa({ ime: s.ime.trim(), priimek: s.priimek.trim(), datumRojstva: s.datumRojstva }),
    enabled: jeIgralec && zunajPodatkov && !!s.ime.trim() && !!s.priimek.trim() && !!s.datumRojstva,
    staleTime: Infinity,
    retry: false,
  })

  const cena = izracunajCeno(s.paket, s.ciklus, s.datumRojstva)
  const placljiv = cena > 0
  const starejsiOd21 = jeStarejsiOd21ZaCeno(s.datumRojstva)
  const cenaPremiumMesecno = (starejsiOd21 ?? false) ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_MESECNO.mlajsi
  const cenaPremiumLetno = (starejsiOd21 ?? false) ? CENA_PREMIUM_LETNO.starejsi : CENA_PREMIUM_LETNO.mlajsi
  /* Obe ceni izbranega plačevanja za pojasnilo pod tabelo (starost je na
     tem koraku še neznana, zato sta prikazani obe). */
  const cenePremium = s.ciklus === 'MESECNO' ? CENA_PREMIUM_MESECNO : CENA_PREMIUM_LETNO
  const enotaCene = s.ciklus === 'MESECNO' ? 'na mesec' : 'na leto'

  const imePaketa = jeIgralec
    ? (s.paket === 'BREZPLACNO' ? 'Brezplačno' : 'Premium')
    : OZNAKE_PAKET[s.paket].replace('Organizator ', '')

  const obnovaBesedilo = (() => {
    if (!placljiv) return 'Brez plačila'
    const d = new Date()
    if (s.paket === 'PREMIUM' && s.ciklus === 'MESECNO') d.setMonth(d.getMonth() + 1)
    else d.setFullYear(d.getFullYear() + 1)
    return `Obnova ${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}`
  })()

  const zapisVrednost = !jeIgralec
    ? null
    : s.povezava === 'da' && predogled.data?.najden
      ? `${predogled.data.ime} ${predogled.data.priimek}${predogled.data.rating != null ? ` · ${predogled.data.rating}` : ''}`
      : s.povezava === 'ne' || (predogled.data && !predogled.data.najden)
        ? 'Poveže administrator'
        : 'Ni potrjeno'

  function imeKoraka(korak: Korak): string {
    if (korak === 'klub') return jeIgralec ? 'Klub in zapis' : 'Klub'
    if (korak === 'povzetek') return placljiv ? 'Povzetek in plačilo' : 'Povzetek'
    return IME_KORAKA[korak]
  }

  function pojdiNaKorak(ime: Korak) {
    const indeks = seznamKorakov.indexOf(ime)
    if (indeks >= 0) posodobi({ korak: indeks })
  }

  function vlogaSePrestavi(nova: Vloga) {
    posodobi({
      vloga: nova,
      paket: nova === 'organizator' ? 'ORGANIZATOR_PLUS' : 'BREZPLACNO',
      ciklus: 'MESECNO',
    })
  }

  const onemogoceno = (() => {
    if (trenutniKorak === 'podatki') {
      return (
        !s.ime.trim()
        || !s.priimek.trim()
        || !s.email.includes('@')
        || s.geslo.length < 8
        || (jeIgralec && !s.datumRojstva)
      )
    }
    if (trenutniKorak === 'skrbnik') return !s.emailSkrbnika.includes('@')
    if (trenutniKorak === 'povzetek') return !s.pogoji || s.poteka
    if (trenutniKorak === 'koda') return s.koda.length !== 6 || s.poteka
    return false
  })()

  async function oddajPovzetek() {
    posodobi({ napaka: null, poteka: true })
    const racun = {
      ime: s.ime.trim(),
      priimek: s.priimek.trim(),
      idKlub: s.idKlub ? Number(s.idKlub) : null,
      email: s.email.trim(),
      geslo: s.geslo,
      organizator: !jeIgralec,
      datumRojstva: jeIgralec ? s.datumRojstva : null,
      emailSkrbnika: potrebujeSkrbnika ? s.emailSkrbnika.trim() : null,
    }
    try {
      if (s.paket === 'BREZPLACNO') {
        const odgovor = await authApi.registracija(racun)
        posodobi({
          email: odgovor.email,
          poteka: false,
          kodaKorak: 'EPOSTA',
          koda: '',
          posljanaZdaj: true,
          korak: seznamKorakov.indexOf('koda'),
        })
        zacniOdstevanje()
        return
      }
      const ciklusPlacila = s.paket === 'PREMIUM' ? s.ciklus : 'LETNO'
      shraniStanjePlacila({
        vloga: s.vloga,
        paket: s.paket,
        ciklus: ciklusPlacila,
        cena,
        ime: racun.ime,
        priimek: racun.priimek,
        email: racun.email,
        potrebnaKodaSkrbnika: !!racun.emailSkrbnika,
      })
      const seja = await placilaApi.registracija({ racun, paket: s.paket, ciklus: ciklusPlacila })
      window.location.href = seja.url
    } catch (e) {
      posodobi({ napaka: opisNapake(e), poteka: false })
    }
  }

  async function potrdiKodo(vpisanaKoda: string) {
    posodobi({ napaka: null, poteka: true })
    try {
      const vnos = { email: s.email, koda: vpisanaKoda }
      const odgovor =
        s.kodaKorak === 'EPOSTA' ? await authApi.potrdiEposto(vnos) : await authApi.potrdiSkrbnika(vnos)
      if (!odgovor.povezan && odgovor.potrebnaKodaSkrbnika) {
        posodobi({ kodaKorak: 'SKRBNIK', koda: '', kodaPoslano: null, poteka: false })
        return
      }
      /* Prijava sledi samodejno, kadar imamo geslo (sveža brezplačna
         registracija) - po vrnitvi s Stripe Checkouta gesla ni več (nikoli
         ni šlo v sessionStorage), zato uporabnik ostane gost in se prijavi
         ročno. Napaka pri samodejni prijavi (npr. streznik se ni odzval) ne
         sme ustaviti zaključka registracije. */
      if (s.geslo) {
        try {
          await prijava(s.email, s.geslo)
        } catch {
          /* konec vseeno pokaže rezultat, prijava ostane ročna */
        }
      }
      posodobi({
        poteka: false,
        izidPovezan: odgovor.povezan,
        izidOrganizator: odgovor.organizator,
        korak: seznamKorakov.indexOf('konec'),
      })
    } catch (e) {
      posodobi({ napaka: opisNapake(e), poteka: false })
    }
  }

  async function posljiZnova() {
    posodobi({ napaka: null })
    try {
      await authApi.ponovnoPoslji(s.email, s.kodaKorak)
      posodobi({
        kodaPoslano: s.kodaKorak === 'EPOSTA' ? 'Nova koda je poslana.' : 'Skrbnik je dobil novo kodo.',
        posljanaZdaj: true,
      })
      zacniOdstevanje()
    } catch (e) {
      posodobi({ napaka: opisNapake(e) })
    }
  }

  function naprej() {
    if (trenutniKorak === 'povzetek') {
      void oddajPovzetek()
      return
    }
    if (trenutniKorak === 'koda') {
      void potrdiKodo(s.koda)
      return
    }
    if (trenutniKorak === 'konec') {
      onZapri()
      return
    }
    posodobi({ korak: Math.min(k + 1, seznamKorakov.length - 1) })
  }

  const glavniGumbNapis = (() => {
    if (trenutniKorak === 'povzetek') {
      if (s.poteka) return placljiv ? 'Preusmerjam na plačilo …' : 'Ustvarjam …'
      return placljiv ? `Nadaljuj na plačilo · ${oblikujCeno(cena)}` : 'Ustvari račun'
    }
    if (trenutniKorak === 'koda') return s.poteka ? 'Preverjam …' : 'Potrdi'
    if (trenutniKorak === 'konec') return 'Na pregled'
    return 'Naprej'
  })()

  const prikaziPrijava = trenutniKorak === 'vloga'
  const prikaziNazaj = trenutniKorak !== 'vloga' && trenutniKorak !== 'koda' && trenutniKorak !== 'konec'

  /* Cena v traku telefona: brezplačno je "0 €" brez enote (glej posnetek
     igralec-01-vloga-telefon), plačljivo dobi enoto glede na cikel - letni
     paketi organizatorja so vedno "na leto". */
  const cenaEnota = s.paket === 'PREMIUM' && s.ciklus === 'MESECNO' ? ' / mesec' : ' / leto'
  const cenaNapisTrak = cena === 0 ? '0 €' : oblikujCeno(cena) + cenaEnota

  const org = !jeIgralec ? OMEJITVE_ORGANIZATORJA[s.paket as OrganizatorPaket] : null

  const povzetekVrstice: { oznaka: string; vrednost: ReactNode; korak: Korak }[] = jeIgralec
    ? [
        { oznaka: 'Vloga', vrednost: 'Igralec', korak: 'vloga' },
        { oznaka: 'Ime', vrednost: polnoIme || '—', korak: 'podatki' },
        { oznaka: 'E-pošta', vrednost: s.email || '—', korak: 'podatki' },
        { oznaka: 'Klub', vrednost: klubIzbran?.ime ?? 'Brez kluba', korak: 'klub' },
        { oznaka: 'Zapis', vrednost: zapisVrednost, korak: 'klub' },
        ...(seznamKorakov.includes('skrbnik')
          ? [{ oznaka: 'Skrbnik', vrednost: s.emailSkrbnika || '—', korak: 'skrbnik' as Korak }]
          : []),
        {
          oznaka: 'Paket',
          vrednost: imePaketa + (s.paket === 'PREMIUM' ? (s.ciklus === 'MESECNO' ? ' · mesečno' : ' · letno') : ''),
          korak: 'paket',
        },
      ]
    : [
        { oznaka: 'Vloga', vrednost: 'Organizator', korak: 'vloga' },
        { oznaka: 'Kontakt', vrednost: polnoIme || '—', korak: 'podatki' },
        { oznaka: 'E-pošta', vrednost: s.email || '—', korak: 'podatki' },
        { oznaka: 'Klub', vrednost: klubIzbran?.ime ?? 'Brez kluba', korak: 'klub' },
        {
          oznaka: 'Paket',
          vrednost: org
            ? `${imePaketa} · ${org.lig} ${org.lig === 1 ? 'liga' : 'lige'}, ${org.turnirjev} turnirjev`
            : imePaketa,
          korak: 'paket',
        },
      ]

  const moc = (() => {
    const g = s.geslo
    let m = 0
    if (g.length >= 8) m++
    if (/\d/.test(g)) m++
    if (/[^A-Za-z0-9]/.test(g) || /[A-Z]/.test(g)) m++
    if (g.length >= 12) m++
    return g.length < 8 ? Math.min(m, 1) : m
  })()
  const mocNapis = !s.geslo ? 'Vsaj 8 znakov' : s.geslo.length < 8 ? 'Prekratko' : ['', 'Šibko', 'Srednje', 'Dobro', 'Močno'][moc]

  const zapri = () => onZapri()
  const korakNapis =
    trenutniKorak === 'konec'
      ? 'Končano'
      : `Korak ${String(k + 1).padStart(2, '0')} / ${String(seznamKorakov.length - 1).padStart(2, '0')}`

  return (
    <div className="registracija">
      {telefon ? (
        <TrakTelefon
          korakNapis={korakNapis}
          cenaNapis={cenaNapisTrak}
          prikaziNapredek={trenutniKorak !== 'konec'}
          seznamKorakov={seznamKorakov}
          trenutniIndeks={k}
          onZapri={zapri}
        />
      ) : (
        <LeviStolpec
          vloga={s.vloga}
          seznamKorakov={seznamKorakov}
          trenutniIndeks={k}
          trenutniKorak={trenutniKorak}
          imeKoraka={imeKoraka}
          onPojdi={pojdiNaKorak}
          izbiraVrstice={[
            { oznaka: 'Vloga', vrednost: jeIgralec ? 'Igralec' : 'Organizator' },
            { oznaka: 'Paket', vrednost: imePaketa },
            { oznaka: 'Cena', vrednost: cenaNapis(cena) },
          ]}
        />
      )}

      <div className="registracija__desni">
        <div className="registracija__glava">
          {!telefon && (
            <div className="registracija__glava-vrstica">
              <span className="registracija__korak-napis">{korakNapis}</span>
              <button type="button" className="registracija__zapri" onClick={zapri} aria-label="Zapri">
                ✕
              </button>
            </div>
          )}
          <h2 className="registracija__naslov">
            <span className="registracija__glavni-naslov">{imeKoraka(trenutniKorak)}</span>
          </h2>
          {!telefon && trenutniKorak !== 'konec' && (
            <div className="registracija__napredek">
              <div className="registracija__napredek-vrstica">
                <span className="registracija__napredek-trenutni">{imeKoraka(trenutniKorak)}</span>
                {k + 1 >= seznamKorakov.length - 1 && (
                  <span className="registracija__korak-napis">Zadnji korak</span>
                )}
              </div>
              <div className="registracija__napredek-segmenti">
                {seznamKorakov.slice(0, -1).map((_, i) => (
                  <span
                    key={i}
                    className={
                      'registracija__napredek-segment'
                      + (i <= k ? ' registracija__napredek-segment--opravljen' : '')
                    }
                  />
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="registracija__telo">
          {trenutniKorak === 'vloga' && (
            <>
              <div className="registracija__vloge">
                <button
                  type="button"
                  className={'registracija__vloga' + (jeIgralec ? ' registracija__vloga--izbrana' : '')}
                  onClick={() => vlogaSePrestavi('igralec')}
                >
                  <span className="registracija__vloga-glava">
                    <span>01</span>
                    {jeIgralec && <span className="registracija__vloga-oznaka">Izbrano</span>}
                  </span>
                  <span className="registracija__vloga-ime">Igralec</span>
                  <span className="registracija__vloga-opis">
                    Tvoj profil, rating in zgodovina tekem. Račun povežemo z zapisom med igralci.
                  </span>
                  <span className="registracija__vloga-noga">Premium od 3,99 € / mesec</span>
                </button>
                <button
                  type="button"
                  className={'registracija__vloga' + (!jeIgralec ? ' registracija__vloga--izbrana' : '')}
                  onClick={() => vlogaSePrestavi('organizator')}
                >
                  <span className="registracija__vloga-glava">
                    <span>02</span>
                    {!jeIgralec && <span className="registracija__vloga-oznaka">Izbrano</span>}
                  </span>
                  <span className="registracija__vloga-ime">Organizator</span>
                  <span className="registracija__vloga-opis">
                    Klub ali oseba, ki vodi turnirje in lige. Vlogo potrdi administrator.
                  </span>
                  <span className="registracija__vloga-noga">Letni paket od 89,99 €</span>
                </button>
              </div>
            </>
          )}

          {trenutniKorak === 'paket' && jeIgralec && (
            <>
              <div className="izbirnik">
                <button
                  type="button"
                  className={'izbirnik__gumb' + (s.ciklus === 'MESECNO' ? ' izbirnik__gumb--aktiven' : '')}
                  onClick={() => posodobi({ ciklus: 'MESECNO' })}
                >
                  Mesečno
                </button>
                <button
                  type="button"
                  className={
                    'izbirnik__gumb izbirnik__gumb--trak' + (s.ciklus === 'LETNO' ? ' izbirnik__gumb--aktiven' : '')
                  }
                  onClick={() => posodobi({ ciklus: 'LETNO' })}
                >
                  Letno
                  <span className="izbirnik__trak">−10%</span>
                </button>
              </div>
              <div className="registracija__primerjava">
                <div className="registracija__primerjava-glava">
                  <span className="registracija__primerjava-oznaka">Kaj dobiš</span>
                  <button
                    type="button"
                    className={'registracija__stolp' + (s.paket === 'BREZPLACNO' ? ' registracija__stolp--izbran' : '')}
                    onClick={() => posodobi({ paket: 'BREZPLACNO' })}
                  >
                    <span className="registracija__stolp-ime">Brezplačno</span>
                    <span className="registracija__stolp-cena">0 €</span>
                    <span className="registracija__stolp-enota">Vedno</span>
                    <span
                      className={
                        'registracija__stolp-oznaka'
                        + (s.paket === 'BREZPLACNO' ? ' registracija__stolp-oznaka--izbran' : '')
                      }
                    >
                      {s.paket === 'BREZPLACNO' ? 'Izbrano' : 'Izberi'}
                    </span>
                  </button>
                  <button
                    type="button"
                    className={'registracija__stolp' + (s.paket === 'PREMIUM' ? ' registracija__stolp--izbran' : '')}
                    onClick={() => posodobi({ paket: 'PREMIUM' })}
                  >
                    <span className="registracija__stolp-ime">Premium</span>
                    <span className="registracija__stolp-cena">
                      {oblikujCeno(s.ciklus === 'MESECNO' ? cenaPremiumMesecno : cenaPremiumLetno)}
                    </span>
                    <span className="registracija__stolp-enota">
                      {s.ciklus === 'MESECNO'
                        ? (starejsiOd21 === null ? 'Od · na mesec' : 'Na mesec')
                        : (starejsiOd21 === null ? 'Od · na leto' : 'Na leto')}
                    </span>
                    <span
                      className={
                        'registracija__stolp-oznaka'
                        + (s.paket === 'PREMIUM' ? ' registracija__stolp-oznaka--izbran' : '')
                      }
                    >
                      {s.paket === 'PREMIUM' ? 'Izbrano' : 'Izberi'}
                    </span>
                  </button>
                </div>
                {(
                  [
                    ['Rezultati, lestvice, koledar', true, true],
                    ['Prijava z lastnim računom', true, true],
                    ['Forma in nasprotniki', false, true],
                    ['Napoved tekme', false, true],
                    ['Nizi, točke in razrezi', false, true],
                    ['Spremljanje lig na domači strani', false, true],
                  ] as [string, boolean, boolean][]
                ).map(([funkcija, brez, prem]) => (
                  <div className="registracija__primerjava-vrstica" key={funkcija}>
                    <span className="registracija__primerjava-funkcija">{funkcija}</span>
                    <span
                      className={
                        'registracija__primerjava-vrednost'
                        + (s.paket === 'BREZPLACNO' ? ' registracija__primerjava-vrednost--stolpec' : '')
                        + (brez ? ' registracija__primerjava-vrednost--da' : ' registracija__primerjava-vrednost--ne')
                      }
                    >
                      {brez ? 'DA' : '—'}
                    </span>
                    <span
                      className={
                        'registracija__primerjava-vrednost'
                        + (s.paket === 'PREMIUM' ? ' registracija__primerjava-vrednost--stolpec' : '')
                        + ' registracija__primerjava-vrednost--da'
                      }
                    >
                      {prem ? 'DA' : '—'}
                    </span>
                  </div>
                ))}
                {/* Lik skače po vrsticah tabele in kaže, kaj doda Premium (glej
                    MaskotaTabela.tsx); samo namizje in samo prvič. */}
                {trenutniKorak === 'paket' && !telefon && !tabelaKoncana && (
                  <MaskotaTabela obKoncu={() => nastaviTabelaKoncana(true)} />
                )}
              </div>
              <p className="registracija__pojasnilo">
                {starejsiOd21 === null
                  ? `Premium stane ${oblikujCeno(cenePremium.mlajsi)} ${enotaCene} do 21. leta in ${oblikujCeno(cenePremium.starejsi)} po njem.`
                  : `${starejsiOd21 ? `Cena od 21. leta naprej; mlajši plačajo ${oblikujCeno(cenePremium.mlajsi)} ${enotaCene}.` : 'Cena do 21. leta.'} Preklic kadarkoli.`}
              </p>
            </>
          )}

          {trenutniKorak === 'paket' && !jeIgralec && (
            <>
              <p className="registracija__uvod">
                Vsi paketi so letni. Obseg — koliko lig in turnirjev smeš voditi — je edina razlika.
              </p>
              <div>
                <div className="registracija__org-glava">
                  <span>Paket</span>
                  <span>Lige</span>
                  <span>Turnirji</span>
                  <span>Na leto</span>
                </div>
                {ORGANIZATOR_PAKETI.map((id) => {
                  const omejitve = OMEJITVE_ORGANIZATORJA[id]
                  const izbran = s.paket === id
                  return (
                    <button
                      type="button"
                      key={id}
                      className={'registracija__org-vrstica' + (izbran ? ' registracija__org-vrstica--izbrana' : '')}
                      onClick={() => posodobi({ paket: id })}
                    >
                      <span className="registracija__org-ime">
                        {OZNAKE_PAKET[id].replace('Organizator ', '')}
                        {izbran && <span className="registracija__org-oznaka">Izbrano</span>}
                      </span>
                      <span className="registracija__org-stevilo">{omejitve.lig}</span>
                      <span className="registracija__org-stevilo">{omejitve.turnirjev}</span>
                      <span className="registracija__org-cena">{oblikujCeno(CENA_ORGANIZATOR_LETNO[id])}</span>
                    </button>
                  )
                })}
              </div>
              <p className="registracija__pojasnilo">Lige štejejo tekoče (hkrati odprte), turnirji na sezono.</p>
            </>
          )}

          {trenutniKorak === 'podatki' && (
            <>
              {!jeIgralec && <p className="registracija__uvod">Kontaktna oseba organizatorja.</p>}
              <div className="obrazec__vrstica obrazec__vrstica--par">
                <label className="obrazec__polje">
                  <span>Ime</span>
                  <input value={s.ime} onChange={(d) => posodobi({ ime: d.target.value })} autoComplete="given-name" />
                </label>
                <label className="obrazec__polje">
                  <span>Priimek</span>
                  <input
                    value={s.priimek}
                    onChange={(d) => posodobi({ priimek: d.target.value })}
                    autoComplete="family-name"
                  />
                </label>
              </div>
              {jeIgralec && (
                <label className="obrazec__polje">
                  <span>Datum rojstva</span>
                  <input
                    type="date"
                    value={s.datumRojstva}
                    max={danes}
                    onChange={(d) => posodobi({ datumRojstva: d.target.value })}
                  />
                  <span className="registracija__polje-pripis">
                    Javno ni viden. Z njim poiščemo tvoj zapis med igralci.
                    {potrebujeSkrbnika ? ' Mlajši od 15 let: sledi korak za skrbnika.' : ''}
                  </span>
                </label>
              )}
              <label className="obrazec__polje">
                <span>E-pošta</span>
                <input
                  type="email"
                  value={s.email}
                  onChange={(d) => posodobi({ email: d.target.value })}
                  autoComplete="email"
                />
              </label>
              <label className="obrazec__polje">
                <span className="registracija__moc-glava">
                  <span>Geslo</span>
                  <span className={'registracija__moc-napis' + (s.geslo && s.geslo.length < 8 ? ' registracija__moc-napis--prekratko' : '')}>
                    {mocNapis}
                  </span>
                </span>
                <input
                  type="password"
                  value={s.geslo}
                  onChange={(d) => posodobi({ geslo: d.target.value })}
                  autoComplete="new-password"
                />
                <span className="registracija__moc-segmenti">
                  {[1, 2, 3, 4].map((i) => (
                    <span
                      key={i}
                      className={
                        'registracija__moc-segment' + (i <= moc ? ` registracija__moc-segment--${moc}` : '')
                      }
                    />
                  ))}
                </span>
              </label>
            </>
          )}

          {trenutniKorak === 'klub' && (
            <>
              {!jeIgralec && (
                <p className="registracija__uvod">
                  Klub, ki ga zastopaš. Vlogo organizatorja in klub potrdi administrator.
                </p>
              )}

              {klubIzbran ? (
                <div className="registracija__klub-izbran">
                  <span className="registracija__klub-ime">
                    <span className="registracija__klub-ime-glavno">{klubIzbran.ime}</span>
                    {klubIzbran.kratica && (
                      <span className="registracija__klub-kratica">{klubIzbran.kratica}</span>
                    )}
                  </span>
                  <button type="button" className="povezava-gumb" onClick={() => posodobi({ idKlub: '' })}>
                    Zamenjaj
                  </button>
                </div>
              ) : (
                <>
                  <IskalniIzbirnik
                    vObrazcu
                    oznaka={jeIgralec ? 'Klub' : 'Klub, ki ga zastopaš'}
                    namig="Vpiši ime kluba ali kraj"
                    moznosti={(klubi.data ?? []).map((klub) => ({
                      id: klub.id,
                      ime: klub.ime,
                      podrobnost: klub.kratica,
                      iskalno: `${klub.ime} ${klub.kratica ?? ''}`,
                    }))}
                    naIzbiro={(id) => posodobi({ idKlub: String(id) })}
                  />
                  <button
                    type="button"
                    className="povezava-gumb"
                    onClick={() => (document.activeElement as HTMLElement)?.blur()}
                  >
                    Nisem član kluba
                  </button>
                </>
              )}

              {jeIgralec && (
                <div className="registracija__zapis">
                  <span className="registracija__zapis-glava">Zapis med igralci</span>
                  {predogled.isLoading && (
                    <p className="registracija__pojasnilo">Iščemo zapis …</p>
                  )}
                  {!predogled.isLoading && predogled.isError && (
                    <p className="registracija__obvestilo">Račun povežemo ob potrditvi e-pošte.</p>
                  )}
                  {!predogled.isLoading && !predogled.isError && predogled.data && (
                    predogled.data.najden ? (
                      <>
                        {s.povezava === null && (
                          <>
                            <div className="registracija__zapis-najden">
                              <div>
                                <span className="registracija__zapis-status">Najden en zapis</span>
                                <span className="registracija__zapis-ime">
                                  {predogled.data.ime} {predogled.data.priimek}
                                </span>
                              </div>
                              {predogled.data.rating != null && (
                                <div className="registracija__rating-blok">
                                  <span className="registracija__rating-st">{predogled.data.rating}</span>
                                  <span className="registracija__rating-oznaka">Rating</span>
                                </div>
                              )}
                            </div>
                            <div className="registracija__kolofon">
                              {predogled.data.klub && (
                                <div className="registracija__kolofon-vrstica">
                                  <span className="registracija__kolofon-oznaka">Klub v zapisu</span>
                                  <span className="registracija__kolofon-vrednost">{predogled.data.klub}</span>
                                </div>
                              )}
                              <div className="registracija__kolofon-vrstica">
                                <span className="registracija__kolofon-oznaka">Rojen(a)</span>
                                <span className="registracija__kolofon-vrednost">{oblikujDatum(s.datumRojstva)}</span>
                              </div>
                              <div className="registracija__kolofon-vrstica">
                                <span className="registracija__kolofon-oznaka">Tekem</span>
                                <span className="registracija__kolofon-vrednost">{predogled.data.steviloTekem}</span>
                              </div>
                            </div>
                            <div className="registracija__zapis-gumbi">
                              <button
                                type="button"
                                className="gumb gumb--glavni"
                                onClick={() => posodobi({ povezava: 'da' })}
                              >
                                To sem jaz
                              </button>
                              <button type="button" className="gumb" onClick={() => posodobi({ povezava: 'ne' })}>
                                Ni moj zapis
                              </button>
                            </div>
                          </>
                        )}
                        {s.povezava === 'da' && (
                          <div className="registracija__obvestilo registracija__obvestilo--uspeh">
                            <span>Račun se poveže s tem zapisom takoj, ko potrdiš e-pošto.</span>
                            <button
                              type="button"
                              className="povezava-gumb"
                              onClick={() => posodobi({ povezava: null })}
                            >
                              Razveljavi
                            </button>
                          </div>
                        )}
                        {s.povezava === 'ne' && (
                          <div className="registracija__obvestilo">
                            <span>Račun poveže administrator. Do takrat si prijavljen kot gost.</span>
                            <button
                              type="button"
                              className="povezava-gumb"
                              onClick={() => posodobi({ povezava: null })}
                            >
                              Razveljavi
                            </button>
                          </div>
                        )}
                      </>
                    ) : (
                      <p className="registracija__obvestilo">
                        Med igralci ni natanko enega zapisa z imenom {polnoIme || '—'} in tem datumom rojstva. Račun
                        poveže administrator.
                      </p>
                    )
                  )}
                </div>
              )}
            </>
          )}

          {trenutniKorak === 'skrbnik' && (
            <>
              <p className="registracija__uvod">Mlajši od 15 let potrebujejo soglasje starša oz. skrbnika.</p>
              <div className="registracija__kolofon registracija__kolofon--rob-zgoraj">
                <div className="registracija__kolofon-vrstica">
                  <span className="registracija__kolofon-oznaka">Starost</span>
                  <span className="registracija__kolofon-vrednost">{leta} let</span>
                </div>
              </div>
              <label className="obrazec__polje">
                <span>E-pošta starša oz. skrbnika</span>
                <input
                  type="email"
                  value={s.emailSkrbnika}
                  onChange={(d) => posodobi({ emailSkrbnika: d.target.value })}
                />
                <span className="registracija__polje-pripis">
                  Na ta naslov gre koda s soglasjem, ki velja 24 ur. Vpišeš jo po svoji kodi.
                </span>
              </label>
            </>
          )}

          {trenutniKorak === 'povzetek' && (
            <>
              <div className="registracija__povzetek">
                {povzetekVrstice.map((vrstica) => (
                  <div className="registracija__povzetek-vrstica" key={vrstica.oznaka}>
                    <span className="registracija__povzetek-oznaka">{vrstica.oznaka}</span>
                    <span className="registracija__povzetek-vrednost">{vrstica.vrednost}</span>
                    <button type="button" className="povezava-gumb" onClick={() => pojdiNaKorak(vrstica.korak)}>
                      Uredi
                    </button>
                  </div>
                ))}
              </div>
              <div className="registracija__znesek">
                <span className="registracija__znesek-oznake">
                  <span className="registracija__znesek-oznaka">Za plačilo danes</span>
                  <span className="registracija__znesek-oznaka">{obnovaBesedilo}</span>
                </span>
                <span className="registracija__znesek-st">{cenaNapis(cena)}</span>
              </div>
              <label className="registracija__pogoji">
                <input type="checkbox" checked={s.pogoji} onChange={(d) => posodobi({ pogoji: d.target.checked })} />
                <span>Strinjam se s pogoji uporabe in obdelavo podatkov.</span>
              </label>
              {placljiv && (
                <p className="registracija__placilo-pripis">
                  Plačilo prek Stripe. Račun nastane, ko Stripe potrdi plačilo.
                </p>
              )}
            </>
          )}

          {trenutniKorak === 'koda' && (
            <>
              {s.placanoZnesek != null && s.kodaKorak === 'EPOSTA' && (
                <div className="registracija__obvestilo registracija__obvestilo--uspeh">
                  <span>
                    Stripe je potrdil plačilo {oblikujCeno(s.placanoZnesek)}. Paket se vklopi, ko potrdiš e-pošto.
                  </span>
                </div>
              )}
              <label className="obrazec__polje">
                <span>{s.kodaKorak === 'EPOSTA' ? 'Koda iz e-pošte' : 'Koda skrbnika'}</span>
                <div className="registracija__koda-polja">
                  <KodaVnos
                    vrednost={s.koda}
                    naSpremembo={(v) => posodobi({ koda: v })}
                    naDokoncano={(v) => void potrdiKodo(v)}
                    disabled={s.poteka}
                    autoFocus
                  />
                </div>
              </label>
              {s.kodaPoslano && <p className="obvestilo">{s.kodaPoslano}</p>}
              <div className="registracija__koda-meta">
                <span className="registracija__korak-napis">
                  Koda velja {s.kodaKorak === 'SKRBNIK' ? '24 ur' : '10 minut'}
                </span>
                <button
                  type="button"
                  className="povezava-gumb"
                  disabled={s.odstevanje > 0}
                  onClick={() => void posljiZnova()}
                >
                  {s.odstevanje > 0 ? `Pošlji kodo znova (${s.odstevanje} s)` : 'Pošlji kodo znova'}
                </button>
              </div>
            </>
          )}

          {trenutniKorak === 'konec' && (
            <KonecKoraka
              jeIgralec={jeIgralec}
              povezan={!!s.izidPovezan}
              organizator={s.izidOrganizator}
              imePaketa={imePaketa}
              placljiv={placljiv}
              obnovaBesedilo={obnovaBesedilo}
              rating={predogled.data?.rating ?? null}
              org={org}
              onZapri={onZapri}
            />
          )}

          {s.napaka && <div className="napaka">{s.napaka}</div>}
        </div>

        <div className="registracija__noga">
          {prikaziPrijava && (
            <button type="button" className="povezava-gumb" onClick={onNazajNaPrijavo}>
              Že imaš račun? Prijava
            </button>
          )}
          {prikaziNazaj && (
            <button type="button" className="gumb" onClick={() => posodobi({ korak: Math.max(0, k - 1) })}>
              Nazaj
            </button>
          )}
          {!prikaziPrijava && !prikaziNazaj && <span />}
          <button type="button" className="gumb gumb--glavni" disabled={onemogoceno} onClick={naprej}>
            {glavniGumbNapis}
          </button>
        </div>
      </div>
    </div>
  )
}

/* Levi stolpec (namizje): logotip, seznam korakov, kolofon trenutne izbire. */
function LeviStolpec({
  vloga,
  seznamKorakov,
  trenutniIndeks,
  trenutniKorak,
  imeKoraka,
  onPojdi,
  izbiraVrstice,
}: {
  vloga: Vloga
  seznamKorakov: Korak[]
  trenutniIndeks: number
  trenutniKorak: Korak
  imeKoraka: (korak: Korak) => string
  onPojdi: (korak: Korak) => void
  izbiraVrstice: { oznaka: string; vrednost: string }[]
}) {
  return (
    <aside className="registracija__stolpec">
      <div className="registracija__logotip">
        <ZnakTurnirko velikost={24} />
        Turnirko
      </div>
      <div>
        <span className="registracija__seznam-glava">
          Nov račun · {vloga === 'igralec' ? 'Igralec' : 'Organizator'}
        </span>
        {seznamKorakov.slice(0, -1).map((korak, i) => {
          const opravljen = i < trenutniIndeks
          const aktiven = i === trenutniIndeks
          const moze = opravljen && trenutniKorak !== 'koda' && trenutniKorak !== 'konec'
          return (
            <button
              type="button"
              key={korak}
              className={
                'registracija__korak'
                + (opravljen ? ' registracija__korak--opravljen' : '')
                + (aktiven ? ' registracija__korak--aktiven' : '')
                + (moze ? ' registracija__korak--klikljiv' : '')
              }
              onClick={() => moze && onPojdi(korak)}
            >
              <span className="registracija__korak-st">{opravljen ? 'OK' : String(i + 1).padStart(2, '0')}</span>
              <span className="registracija__korak-ime">{imeKoraka(korak)}</span>
            </button>
          )
        })}
      </div>
      <div className="registracija__izbira">
        <span className="registracija__izbira-glava">Tvoja izbira</span>
        {izbiraVrstice.map((v) => (
          <div className="registracija__izbira-vrstica" key={v.oznaka}>
            <span className="registracija__izbira-oznaka">{v.oznaka}</span>
            <span className="registracija__izbira-vrednost">{v.vrednost}</span>
          </div>
        ))}
      </div>
    </aside>
  )
}

/* Trak (telefon): nadomesti levi stolpec čez vso širino nad vsebino. */
function TrakTelefon({
  korakNapis,
  cenaNapis,
  prikaziNapredek,
  seznamKorakov,
  trenutniIndeks,
  onZapri,
}: {
  korakNapis: string
  cenaNapis: string
  prikaziNapredek: boolean
  seznamKorakov: Korak[]
  trenutniIndeks: number
  onZapri: () => void
}) {
  return (
    <div className="registracija__trak">
      <div className="registracija__trak-vrstica">
        <span className="registracija__trak-levo">
          <ZnakTurnirko velikost={20} />
          <span className="registracija__trak-korak">{korakNapis}</span>
        </span>
        <span className="registracija__trak-desno">
          <span className="registracija__trak-cena">{cenaNapis}</span>
          <button type="button" className="registracija__trak-zapri" onClick={onZapri} aria-label="Zapri">
            ✕
          </button>
        </span>
      </div>
      {prikaziNapredek && (
        <div className="registracija__trak-segmenti">
          {seznamKorakov.slice(0, -1).map((korak, i) => (
            <span
              key={korak}
              className={
                'registracija__trak-segment'
                + (i < trenutniIndeks ? ' registracija__trak-segment--opravljen' : '')
                + (i === trenutniIndeks ? ' registracija__trak-segment--aktiven' : '')
              }
            />
          ))}
        </div>
      )}
    </div>
  )
}

/* Zadnji korak: "Račun je pripravljen" - kolofon in seznam prvih korakov. */
function KonecKoraka({
  jeIgralec,
  povezan,
  organizator,
  imePaketa,
  placljiv,
  obnovaBesedilo,
  rating,
  org,
  onZapri,
}: {
  jeIgralec: boolean
  povezan: boolean
  organizator: boolean
  imePaketa: string
  placljiv: boolean
  obnovaBesedilo: string
  rating: number | null
  org: { lig: number; turnirjev: number } | null
  onZapri: () => void
}) {
  const jePremium = jeIgralec && imePaketa === 'Premium'
  return (
    <>
      <p className="registracija__uvod">
        {jeIgralec
          ? (povezan
              ? 'E-pošta je potrjena in račun je povezan s tvojim zapisom med igralci.'
              : 'E-pošta je potrjena. Račun poveže administrator; do takrat si prijavljen kot gost.')
          : 'E-pošta je potrjena. Račun čaka, da ti administrator dodeli vlogo organizatorja in klub.'}
      </p>
      {organizator && <span className="znacka">Čaka potrditev administratorja</span>}
      <div className="registracija__kolofon">
        <div className="registracija__kolofon-vrstica">
          <span className="registracija__kolofon-oznaka">Paket</span>
          <span className="registracija__kolofon-vrednost">
            {imePaketa}{placljiv ? ` · ${obnovaBesedilo.toLowerCase()}` : ''}
          </span>
        </div>
        <div className="registracija__kolofon-vrstica">
          <span className="registracija__kolofon-oznaka">E-pošta</span>
          <span className="registracija__kolofon-vrednost registracija__kolofon-vrednost--uspeh">Potrjena</span>
        </div>
        {jeIgralec && (
          <div className="registracija__kolofon-vrstica">
            <span className="registracija__kolofon-oznaka">Profil</span>
            <span
              className={'registracija__kolofon-vrednost' + (povezan ? ' registracija__kolofon-vrednost--uspeh' : '')}
            >
              {povezan ? `Povezan${rating != null ? ` · ${rating}` : ''}` : 'Čaka povezavo'}
            </span>
          </div>
        )}
      </div>
      <div>
        <div className="registracija__prvi-koraki-glava">
          <h3 className="registracija__prvi-koraki-naslov">Prvi koraki</h3>
          <span className="registracija__prvi-koraki-stevec">0 od 3</span>
        </div>
        {jeIgralec ? (
          <>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">01</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Odpri svoj profil</span>
                <span className="registracija__korak-prvi-opis">
                  {povezan ? 'Rating, zgodovina tekem in forma.' : 'Profil se odpre, ko administrator poveže račun.'}
                </span>
              </span>
              {povezan ? (
                <Link to="/moj-profil" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
                  Profil →
                </Link>
              ) : (
                <span className="registracija__korak-prvi-meta">Kasneje</span>
              )}
            </div>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">02</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Izberi lige, ki jih spremljaš</span>
                <span className="registracija__korak-prvi-opis">
                  {jePremium ? 'Njihova kola se pokažejo na domači strani.' : 'Na voljo s paketom Premium.'}
                </span>
              </span>
              {jePremium ? (
                <Link to="/lige" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
                  Lige →
                </Link>
              ) : (
                <span className="registracija__korak-prvi-meta">Premium</span>
              )}
            </div>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">03</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Primerjaj se z nasprotnikom</span>
                <span className="registracija__korak-prvi-opis">Pripomoček »1 na 1« na domači strani.</span>
              </span>
              <Link to="/dvoboj" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
                1 na 1 →
              </Link>
            </div>
          </>
        ) : (
          <>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">01</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Ustvari prvi turnir</span>
                <span className="registracija__korak-prvi-opis">
                  {org ? `0 od ${org.turnirjev} turnirjev v sezoni.` : ''}
                </span>
              </span>
              <span className="registracija__korak-prvi-meta">Po potrditvi</span>
            </div>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">02</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Odpri ligo</span>
                <span className="registracija__korak-prvi-opis">
                  {org ? `0 od ${org.lig} ${org.lig === 1 ? 'tekoče lige' : 'tekočih lig'}.` : ''}
                </span>
              </span>
              <span className="registracija__korak-prvi-meta">Po potrditvi</span>
            </div>
            <div className="registracija__korak-prvi">
              <span className="registracija__korak-prvi-st">03</span>
              <span className="registracija__korak-prvi-besedilo">
                <span className="registracija__korak-prvi-naslov">Dodaj igralce v šifrant</span>
                <span className="registracija__korak-prvi-opis">Samo tiste, ki jih med igralci še ni.</span>
              </span>
              <span className="registracija__korak-prvi-meta">Po potrditvi</span>
            </div>
          </>
        )}
      </div>
    </>
  )
}
