/* Registracija igralca ali organizatorja, razdeljena na korake (design_handoff_onboarding,
   smer 1b): vloga → paket → podatki → klub (+ zapis med igralci) → [skrbnik,
   če je igralec mlajši od 15 let] → povzetek in plačilo → koda iz e-pošte →
   [koda skrbnika] → prvi koraki.

   Logika je bila prej v enem obrazcu (RegistracijaObrazec + KodaObrazec v
   PrijavaOkno.tsx); ta komponenta jo samo razdeli na korake in doda vlogo,
   paket, klub/zapis, povzetek in zaključek, ki jih star obrazec ni imel.
   Prijava, koda ob nepotrjenem obstoječem računu in pozabljeno geslo ostanejo
   v PrijavaOkno.tsx nespremenjeni - tja to okno ne posega.

   Kratka pot (`kratko`, oktober 2026): poziv k računu in »To si ti?« na profilu
   vodita gosta, ki je očitno igralec in ne kupuje, zato koraka »Vloga« in
   »Paket« odpadeta - igralec, brezplačno, s prvim mesecem Premium (preizkus
   začne strežnik ob povezavi z zapisom). Ostanejo podatki, klub in zapis,
   povzetek in koda. Vsak korak gre v statistiko (pomozno/statistika.ts).

   Navijač (V55, oktober 2026) je oseba, ki ne igra: vloga → podatki →
   [skrbnik] → povzetek → koda. Brez paketa (brezplačen), kluba in zapisa (z
   igralci se ne primerja), brez kode popusta; račun je pripravljen, ko potrdi
   e-pošto. Kratka pot iz »Spremljaj« (`izvor: 'spremljanje'`) zato ima korak
   »Vloga« z izbiro Igralec / Navijač - ligo spremlja tudi, kdor v njej ne igra. */
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { opisNapake } from '../api/odjemalec'
import { authApi, domovApi, klubiApi, placilaApi } from '../api/zahteve'
import {
  CENA_NAVIJAC_LETNO,
  CENA_ORGANIZATOR_LETNO,
  CENA_PREMIUM_LETNO,
  CENA_PREMIUM_MESECNO,
  OMEJITVE_ORGANIZATORJA,
  OZNAKE_PAKET,
  prihranekLetnoOdstotki,
  type CiklusPlacila,
  type Paket,
  type PreverjenaKodaDto,
} from '../api/tipi'
import { mesecev, opisUgodnosti, prvoPlacilo } from '../pomozno/kodePopusta'
import { jeStarejsiOd21ZaCeno, ligaVTozilniku, oblikujCeno, oblikujDatum } from '../pomozno/oblikovanje'
import { shraniStanjePlacila, type ShranjenoStanjePlacila } from '../pomozno/registracijaSeja'
import { zabelezi, type IzvorRegistracije } from '../pomozno/statistika'
import { useTelefon } from '../pomozno/telefon'
import { IskalniIzbirnik } from './IskalniIzbirnik'
import { KodaVnos } from './KodaVnos'
import { MaskotaTabela } from './MaskotaTabela'
import { ZnakTurnirko } from './Postavitev'

/* Sekund, preden je mogoče zahtevati novo kodo (isto pravilo kot v
   PrijavaOkno.tsx - strežnik dovoli eno na minuto, gumb to pove vnaprej). */
const ODMIK_PONOVNEGA_POSILJANJA = 60

type Vloga = 'igralec' | 'organizator' | 'navijac'

const OZNAKA_VLOGE: Record<Vloga, string> = {
  igralec: 'Igralec',
  organizator: 'Organizator',
  navijac: 'Navijač',
}

/* Kartice koraka »Vloga«, v tem vrstnem redu (številka je mesto v seznamu). */
/* Paketa navijača (V58): brezplačen in plačljiv Navijač. Opisa nista ponavljanje ugodnosti na
   strani Naročnina, ampak povzetek, po katerem se oseba odloči že v registraciji. */
const PAKETI_NAVIJACA: { paket: Paket; ime: string; opis: string }[] = [
  {
    paket: 'BREZPLACNO',
    ime: 'Brezplačno',
    opis: 'Spremljaš lige in enega igralca, po vsakem kolu dobiš izide po e-pošti.',
  },
  {
    paket: 'NAVIJAC',
    ime: 'Navijač',
    opis: 'Neomejeno igralcev, e-pošta po turnirju, koledar v telefonu in napoved dvoboja za kateri koli par.',
  },
]

const MOZNOSTI_VLOGE: { vloga: Vloga; opis: string; noga: string }[] = [
  {
    vloga: 'igralec',
    opis: 'Tvoj profil, rating in zgodovina tekem. Račun povežemo z zapisom med igralci.',
    noga: 'Prvi mesec Premium brez plačila',
  },
  {
    vloga: 'navijac',
    opis: 'Ne igraš, a spremljaš lige in igralce: starš, soigralec, klub. Račun je pripravljen, ko potrdiš e-pošto.',
    noga: 'Brezplačno',
  },
  {
    vloga: 'organizator',
    opis: 'Klub ali oseba, ki vodi turnirje in lige. Vlogo potrdi administrator.',
    noga: 'Letni paket od 89,99 €',
  },
]
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

/* Igralec, s čigar profila je gost prišel (»To si ti?«): ime in klub se vpišeta
   vnaprej. Klub je ime, kot ga kaže profil; id se poišče v šifrantu klubov. */
export interface PredizbranIgralec {
  ime: string
  priimek: string
  klub: string | null
}

/* Liga, ob kateri je gost začel registracijo z gumbom »Spremljaj«. */
export interface LigaZaSpremljanje {
  id: number
  ime: string
}

/* Igralec, ob katerem je gost začel registracijo z gumbom »Spremljaj« na profilu (V57). */
export interface IgralecZaSpremljanje {
  id: number
  ime: string
}

/* Seznam korakov je odvisen od vloge (organizator klub-a med igralci nima,
   navijač ne paketa ne kluba) in od tega, ali igralec oz. navijač potrebuje
   skrbnika - korak "skrbnik" se vstavi sproti. Kratka pot nima paketa (vedno
   brezplačno), vlogo pa samo, kadar ponuja izbiro igralec / navijač. */
function koraki(vloga: Vloga, potrebujeSkrbnika: boolean, kratko = false, izbiraVloge = false): Korak[] {
  if (vloga === 'organizator') {
    return ['vloga', 'paket', 'podatki', 'klub', 'povzetek', 'koda', 'konec']
  }
  if (vloga === 'navijac') {
    return [
      ...(!kratko ? (['vloga', 'paket'] as const) : (['vloga'] as const)),
      'podatki',
      ...(potrebujeSkrbnika ? (['skrbnik'] as const) : []),
      'povzetek', 'koda', 'konec',
    ]
  }
  return [
    ...(!kratko ? (['vloga', 'paket'] as const) : izbiraVloge ? (['vloga'] as const) : []),
    'podatki', 'klub',
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
  if (paket === 'NAVIJAC') return CENA_NAVIJAC_LETNO
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
  /* Zadnji dan preizkusa Premium, ki se je začel s povezavo (ISO). */
  izidPreizkusDo: string | null
  placanoZnesek: number | null
  /* Koda popusta (V53), ki jo je strežnik v povzetku potrdil za izbrano vlogo in paket.
     Ob spremembi vloge ali paketa odpade (posodobi). */
  kodaPopusta: PreverjenaKodaDto | null
  /* Polje za kodo je odprto; vpisano besedilo še ni uporabljeno, dokler ga strežnik ne potrdi. */
  kodaOdprta: boolean
  kodaVnos: string
  kodaPreverja: boolean
  kodaNapaka: string | null
}

function zacetnoStanje(
  obnovljeno: ShranjenoStanjePlacila | null,
  naPaketu: boolean,
  zacetniCiklus: CiklusPlacila | undefined,
  predizbran: PredizbranIgralec | undefined,
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
      izidPreizkusDo: null,
      placanoZnesek: obnovljeno.cena,
      kodaPopusta: null,
      kodaOdprta: false,
      kodaVnos: '',
      kodaPreverja: false,
      kodaNapaka: null,
    }
  }
  return {
    vloga: 'igralec',
    /* Klik na maskoto: izbrana vloga (igralec) je že jasna, zato okno začne na
       paketu z Igralcem Premium za eno leto. */
    korak: naPaketu ? koraki('igralec', false).indexOf('paket') : 0,
    paket: naPaketu ? 'PREMIUM' : 'BREZPLACNO',
    ciklus: naPaketu ? (zacetniCiklus ?? 'LETNO') : 'MESECNO',
    ime: predizbran?.ime ?? '',
    priimek: predizbran?.priimek ?? '',
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
    izidPreizkusDo: null,
    placanoZnesek: null,
    kodaPopusta: null,
    kodaOdprta: false,
    kodaVnos: '',
    kodaPreverja: false,
    kodaNapaka: null,
  }
}

export function RegistracijaTok({
  onZapri,
  onNazajNaPrijavo,
  obnovljenoStanje = null,
  naPaketu = false,
  zacetniCiklus,
  kratko = false,
  izvor = 'meni',
  predizbran,
  liga,
  igralec,
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
  /* Kratka pot brez korakov »Vloga« in »Paket« (glej glavo datoteke). */
  kratko?: boolean
  /* Od kod je okno odprto - lastnost vsakega dogodka za statistiko. */
  izvor?: IzvorRegistracije
  /* Igralec s profila (»To si ti?«): ime, priimek in klub so vpisani vnaprej. */
  predizbran?: PredizbranIgralec
  /* Liga, ob kateri je gost kliknil »Spremljaj«: po samodejni prijavi jo
     račun začne spremljati (in z njo dobiva obvestila po kolu). */
  liga?: LigaZaSpremljanje
  /* Igralec, ob katerem je gost kliknil »Spremljaj« (profil): po samodejni prijavi ga račun
     začne spremljati, na domači strani dobi sklop »Moj igralec«. */
  igralec?: IgralecZaSpremljanje
}) {
  const telefon = useTelefon()
  const { prijava } = useAvtentikacija()
  const odjemalec = useQueryClient()
  /* Ime lige, ki jo je račun ob registraciji začel spremljati (korak »konec«). */
  const [ligaSpremljana, nastaviLigaSpremljana] = useState<string | null>(null)
  /* Ime igralca, ki ga je račun ob registraciji začel spremljati (korak »konec«). */
  const [igralecSpremljan, nastaviIgralecSpremljan] = useState<string | null>(null)
  const [s, nastavi] = useState<Stanje>(() =>
    zacetnoStanje(obnovljenoStanje, naPaketu, zacetniCiklus, predizbran),
  )
  /* Lik v tabeli paketov nastopi enkrat na odprto okno: ob vrnitvi na korak
     (Nazaj/Naprej) ga ne bi bilo treba spet gledati. */
  const [tabelaKoncana, nastaviTabelaKoncana] = useState(false)
  const odstevalnik = useRef<ReturnType<typeof setInterval> | null>(null)

  /* Koda popusta je preverjena za vlogo in paket, ki sta bila izbrana, ko jo je strežnik
     potrdil: drug paket ali vloga pomeni drugo pot (koda z meseci velja za brezplačno
     registracijo, odstotek za plačljivo), zato odpade in se vpiše znova. */
  function posodobi(delno: Partial<Stanje>) {
    nastavi((prejsnje) => {
      const drugaPot =
        (delno.paket !== undefined && delno.paket !== prejsnje.paket)
        || (delno.vloga !== undefined && delno.vloga !== prejsnje.vloga)
      return {
        ...prejsnje,
        ...delno,
        ...(drugaPot && delno.kodaPopusta === undefined ? { kodaPopusta: null, kodaNapaka: null } : {}),
      }
    })
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
  const jeNavijac = s.vloga === 'navijac'
  const jeOrganizator = s.vloga === 'organizator'
  /* Kdor pride s »Spremljaj«, ni nujno igralec: kratka pot mu ponudi še navijača. */
  const izbiraVloge = kratko && izvor === 'spremljanje'
  const leta = !jeOrganizator ? starost(s.datumRojstva) : null
  const potrebujeSkrbnika = leta !== null && leta < 15
  const danes = new Date().toISOString().slice(0, 10)
  const polnoIme = `${s.ime.trim()} ${s.priimek.trim()}`.trim()

  const seznamKorakov = koraki(s.vloga, potrebujeSkrbnika, kratko, izbiraVloge)
  const k = Math.min(s.korak, seznamKorakov.length - 1)
  const trenutniKorak = seznamKorakov[k]

  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const klubIzbran = klubi.data?.find((klub) => String(klub.id) === s.idKlub) ?? null

  /* Klub s profila se izbere enkrat, ko je šifrant klubov naložen; »Zamenjaj«
     ga potem lahko izprazni, ne da bi se vrnil. */
  const klubPredizbran = useRef(false)
  useEffect(() => {
    if (klubPredizbran.current || !predizbran?.klub || !klubi.data) return
    klubPredizbran.current = true
    const ime = predizbran.klub.trim().toLowerCase()
    const najden = klubi.data.find((klub) => klub.ime.trim().toLowerCase() === ime)
    if (najden) nastavi((prejsnje) => (prejsnje.idKlub ? prejsnje : { ...prejsnje, idKlub: String(najden.id) }))
  }, [predizbran, klubi.data])

  /* Statistika poti do računa: začetek (ne po vrnitvi s Stripa - to je isti
     poskus), vsak dosežen korak in opustitev, če se okno zapre pred koncem. */
  const korakZaStatistiko = useRef<Korak>(trenutniKorak)
  korakZaStatistiko.current = trenutniKorak
  useEffect(() => {
    if (obnovljenoStanje) return
    zabelezi('registracija-zacetek', { izvor, kratko })
    return () => {
      if (korakZaStatistiko.current !== 'konec') {
        zabelezi('registracija-opuscena', { izvor, korak: korakZaStatistiko.current })
      }
    }
    // namerno enkrat na odprto okno
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    zabelezi('registracija-korak', { izvor, korak: trenutniKorak })
  }, [izvor, trenutniKorak])

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

  /* Koda popusta spremeni, kaj oseba dobi in plača: odstotek zniža prvo plačilo, meseci
     brez plačila nadomestijo plačilo (organizator dobi paket kode, ne izbranega). Cena v
     povzetku in na gumbu je zato ta po popustu, polna pa je cena obnove. */
  const koda = s.kodaPopusta
  const cenaPolna = izracunajCeno(s.paket, s.ciklus, s.datumRojstva)
  const cena = koda?.vrsta === 'ODSTOTEK' && koda.odstotek
    ? prvoPlacilo(cenaPolna, koda.odstotek)
    : koda?.vrsta === 'ORGANIZATOR_MESECI'
      ? 0
      : cenaPolna
  const placljiv = cena > 0
  const paketUcinek: Paket = koda?.vrsta === 'ORGANIZATOR_MESECI' && koda.paket ? koda.paket : s.paket
  const starejsiOd21 = jeStarejsiOd21ZaCeno(s.datumRojstva)
  const cenaPremiumMesecno = (starejsiOd21 ?? false) ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_MESECNO.mlajsi
  const cenaPremiumLetno = (starejsiOd21 ?? false) ? CENA_PREMIUM_LETNO.starejsi : CENA_PREMIUM_LETNO.mlajsi
  /* Obe ceni izbranega plačevanja za pojasnilo pod tabelo (starost je na
     tem koraku še neznana, zato sta prikazani obe). */
  const cenePremium = s.ciklus === 'MESECNO' ? CENA_PREMIUM_MESECNO : CENA_PREMIUM_LETNO
  const enotaCene = s.ciklus === 'MESECNO' ? 'na mesec' : 'na leto'

  const imePaketa = !jeOrganizator
    ? (s.paket === 'BREZPLACNO' ? 'Brezplačno' : jeNavijac ? 'Navijač' : 'Premium')
    : OZNAKE_PAKET[paketUcinek].replace('Organizator ', '')

  const obnovaBesedilo = (() => {
    if (!placljiv) {
      if (koda?.vrsta === 'PREMIUM_MESECI' && koda.meseci) return `Premium: ${mesecev(koda.meseci)}`
      if (koda?.vrsta === 'NAVIJAC_MESECI' && koda.meseci) return `Navijač: ${mesecev(koda.meseci)}`
      if (koda?.vrsta === 'ORGANIZATOR_MESECI' && koda.meseci) return `Brez plačila: ${mesecev(koda.meseci)}`
      return jeIgralec ? 'Prvi mesec s Premium' : 'Brez plačila'
    }
    const d = new Date()
    if (s.paket === 'PREMIUM' && s.ciklus === 'MESECNO') d.setMonth(d.getMonth() + 1)
    else d.setFullYear(d.getFullYear() + 1)
    /* Popust velja samo za prvo plačilo: obnova je po polni ceni, in to mora biti vidno. */
    return `Obnova ${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}`
      + (koda?.vrsta === 'ODSTOTEK' ? ` · ${oblikujCeno(cenaPolna)}` : '')
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
        || (!jeOrganizator && !s.datumRojstva)
      )
    }
    if (trenutniKorak === 'skrbnik') return !s.emailSkrbnika.includes('@')
    /* Vpisana koda, ki je strežnik še ni potrdil, bi tiho odpadla: oseba bi ustvarila račun
       brez ugodnosti, ki jo pričakuje. Zato računa ne ustvari, dokler kode ne uporabi ali
       izbriše. */
    if (trenutniKorak === 'povzetek') {
      return !s.pogoji || s.poteka || s.kodaPreverja || (!koda && s.kodaOdprta && !!s.kodaVnos.trim())
    }
    if (trenutniKorak === 'koda') return s.koda.length !== 6 || s.poteka
    return false
  })()

  /* Strežnik pove, ali koda velja in kaj prinese za izbrano vlogo in paket; ugibanje kod
     omejuje po naslovu IP. Razlog zavrnitve je v odgovoru, da oseba ve, kaj popraviti. */
  async function uporabiKodo() {
    const vpisana = s.kodaVnos.trim()
    if (!vpisana || s.kodaPreverja) return
    posodobi({ kodaPreverja: true, kodaNapaka: null })
    try {
      const odgovor = await authApi.kodaPopusta({
        koda: vpisana,
        organizator: jeOrganizator,
        navijac: jeNavijac,
        paket: s.paket,
        email: s.email.trim() || null,
      })
      if (!odgovor.veljavna || !odgovor.uporabna) {
        /* Hitra registracija nima koraka »Paket«, zato odstotka ni kje uporabiti. */
        const sporocilo = kratko && odgovor.vrsta === 'ODSTOTEK'
          ? 'Ta koda zniža ceno plačljivega paketa. V tej hitri registraciji paketa ni mogoče izbrati: '
            + 'odpri registracijo v meniju računa.'
          : (odgovor.sporocilo ?? 'Koda ni veljavna ali je potekla.')
        posodobi({ kodaPreverja: false, kodaNapaka: sporocilo })
        return
      }
      zabelezi('koda-popusta-uporabljena', { izvor, vrsta: odgovor.vrsta ?? '' })
      posodobi({ kodaPreverja: false, kodaPopusta: odgovor, kodaVnos: '', kodaOdprta: false })
    } catch (e) {
      posodobi({ kodaPreverja: false, kodaNapaka: opisNapake(e) })
    }
  }

  function odstraniKodo() {
    posodobi({ kodaPopusta: null, kodaNapaka: null })
  }

  async function oddajPovzetek() {
    posodobi({ napaka: null, poteka: true })
    const racun = {
      ime: s.ime.trim(),
      priimek: s.priimek.trim(),
      idKlub: s.idKlub ? Number(s.idKlub) : null,
      email: s.email.trim(),
      geslo: s.geslo,
      organizator: jeOrganizator,
      navijac: jeNavijac,
      datumRojstva: !jeOrganizator ? s.datumRojstva : null,
      emailSkrbnika: potrebujeSkrbnika ? s.emailSkrbnika.trim() : null,
      kodaPopusta: koda?.koda ?? null,
    }
    try {
      /* Brez plačila gre po navadni poti: brezplačen igralec, pa tudi organizator s kodo
         za paket brez plačila (paket nadomesti koda, ne izbira). */
      if (!placljiv) {
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
      zabelezi('registracija-placilo', { izvor, paket: s.paket })
      shraniStanjePlacila({
        vloga: jeOrganizator ? 'organizator' : jeNavijac ? 'navijac' : 'igralec',
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
          /* Spremljanje lige, ob kateri je gost začel, je pomoč in ne pogoj:
             če ne uspe, jo doda sam z istim gumbom. */
          if (liga) {
            try {
              await domovApi.spremljaj(liga.id)
              nastaviLigaSpremljana(liga.ime)
              void odjemalec.invalidateQueries({ queryKey: ['moje-lige'] })
            } catch {
              /* ostane nespremljana */
            }
          }
          /* Enako za igralca s profila (V57): pomoč in ne pogoj. */
          if (igralec) {
            try {
              await domovApi.spremljajIgralca(igralec.id)
              nastaviIgralecSpremljan(igralec.ime)
              void odjemalec.invalidateQueries({ queryKey: ['moji-igralci'] })
              void odjemalec.invalidateQueries({ queryKey: ['moji-igralci-povzetki'] })
            } catch {
              /* ostane nespremljan */
            }
          }
        } catch {
          /* konec vseeno pokaže rezultat, prijava ostane ročna */
        }
      }
      zabelezi('registracija-potrjena', {
        izvor,
        vloga: s.vloga,
        povezan: odgovor.povezan,
        preizkus: odgovor.preizkusDo !== null,
      })
      posodobi({
        poteka: false,
        izidPovezan: odgovor.povezan,
        izidOrganizator: odgovor.organizator,
        izidPreizkusDo: odgovor.preizkusDo,
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

  /* Prvi korak (vloga, pri kratki poti podatki) ponudi prijavo, ostali Nazaj. */
  const prikaziPrijava = k === 0
  const prikaziNazaj = k > 0 && trenutniKorak !== 'koda' && trenutniKorak !== 'konec'

  /* Cena v traku telefona: brezplačno je "0 €" brez enote (glej posnetek
     igralec-01-vloga-telefon), plačljivo dobi enoto glede na cikel - letni
     paketi organizatorja so vedno "na leto". */
  const cenaEnota = s.paket === 'PREMIUM' && s.ciklus === 'MESECNO' ? ' / mesec' : ' / leto'
  const cenaNapisTrak = cena === 0 ? '0 €' : oblikujCeno(cena) + cenaEnota

  const org = jeOrganizator ? OMEJITVE_ORGANIZATORJA[paketUcinek as OrganizatorPaket] : null

  const povzetekVrstice: { oznaka: string; vrednost: ReactNode; korak: Korak }[] = jeNavijac
    ? [
        { oznaka: 'Vloga', vrednost: 'Navijač', korak: 'vloga' },
        { oznaka: 'Ime', vrednost: polnoIme || '—', korak: 'podatki' },
        { oznaka: 'E-pošta', vrednost: s.email || '—', korak: 'podatki' },
        ...(seznamKorakov.includes('skrbnik')
          ? [{ oznaka: 'Skrbnik', vrednost: s.emailSkrbnika || '—', korak: 'skrbnik' as Korak }]
          : []),
        {
          oznaka: 'Paket',
          vrednost: s.paket === 'NAVIJAC'
            ? `Navijač · ${oblikujCeno(CENA_NAVIJAC_LETNO)} na leto`
            : koda?.vrsta === 'NAVIJAC_MESECI' && koda.meseci
              ? `Navijač: ${mesecev(koda.meseci)} brez plačila`
              : 'Brezplačno',
          korak: 'paket',
        },
      ]
    : jeIgralec
    ? [
        ...(seznamKorakov.includes('vloga')
          ? [{ oznaka: 'Vloga', vrednost: 'Igralec', korak: 'vloga' as Korak }]
          : []),
        { oznaka: 'Ime', vrednost: polnoIme || '—', korak: 'podatki' },
        { oznaka: 'E-pošta', vrednost: s.email || '—', korak: 'podatki' },
        { oznaka: 'Klub', vrednost: klubIzbran?.ime ?? 'Brez kluba', korak: 'klub' },
        { oznaka: 'Zapis', vrednost: zapisVrednost, korak: 'klub' },
        ...(seznamKorakov.includes('skrbnik')
          ? [{ oznaka: 'Skrbnik', vrednost: s.emailSkrbnika || '—', korak: 'skrbnik' as Korak }]
          : []),
        {
          oznaka: 'Paket',
          vrednost: imePaketa + (s.paket === 'PREMIUM'
            ? (s.ciklus === 'MESECNO' ? ' · mesečno' : ' · letno')
            : koda?.vrsta === 'PREMIUM_MESECI' && koda.meseci
              ? ` · Premium: ${mesecev(koda.meseci)}`
              : ' · prvi mesec Premium'),
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
              + (koda?.vrsta === 'ORGANIZATOR_MESECI' && koda.meseci ? ` · brez plačila: ${mesecev(koda.meseci)}` : '')
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
            { oznaka: 'Vloga', vrednost: OZNAKA_VLOGE[s.vloga] },
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
              {/* Kratka pot s »Spremljaj« organizatorja ne ponudi: tja prideš iz menija. */}
              {(() => {
                const vloge = MOZNOSTI_VLOGE.filter((m) => !izbiraVloge || m.vloga !== 'organizator')
                return (
                  <div className={'registracija__vloge' + (vloge.length > 2 ? ' registracija__vloge--tri' : '')}>
                    {vloge.map((m, i) => {
                      const izbrana = s.vloga === m.vloga
                      return (
                        <button
                          type="button"
                          key={m.vloga}
                          className={'registracija__vloga' + (izbrana ? ' registracija__vloga--izbrana' : '')}
                          onClick={() => vlogaSePrestavi(m.vloga)}
                        >
                          <span className="registracija__vloga-glava">
                            <span>{String(i + 1).padStart(2, '0')}</span>
                            {izbrana && <span className="registracija__vloga-oznaka">Izbrano</span>}
                          </span>
                          <span className="registracija__vloga-ime">{OZNAKA_VLOGE[m.vloga]}</span>
                          <span className="registracija__vloga-opis">{m.opis}</span>
                          <span className="registracija__vloga-noga">{m.noga}</span>
                        </button>
                      )
                    })}
                  </div>
                )
              })()}
            </>
          )}

          {trenutniKorak === 'paket' && jeNavijac && (
            <div className="registracija__vloge">
              {PAKETI_NAVIJACA.map((p, i) => {
                const izbran = s.paket === p.paket
                return (
                  <button
                    type="button"
                    key={p.paket}
                    className={'registracija__vloga' + (izbran ? ' registracija__vloga--izbrana' : '')}
                    aria-pressed={izbran}
                    onClick={() => posodobi({ paket: p.paket, ciklus: 'LETNO' })}
                  >
                    <span className="registracija__vloga-glava">
                      <span>{String(i + 1).padStart(2, '0')}</span>
                      {izbran && <span className="registracija__vloga-oznaka">Izbrano</span>}
                    </span>
                    <span className="registracija__vloga-ime">{p.ime}</span>
                    <span className="registracija__vloga-opis">{p.opis}</span>
                    <span className="registracija__vloga-noga">
                      {p.paket === 'NAVIJAC' ? `${oblikujCeno(CENA_NAVIJAC_LETNO)} na leto` : 'Brezplačno'}
                    </span>
                  </button>
                )
              })}
            </div>
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
                  <span className="izbirnik__trak">−{prihranekLetnoOdstotki(cenaPremiumMesecno, cenaPremiumLetno)}%</span>
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
                    <span className="registracija__stolp-enota">+ mesec Premium</span>
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
                    ['Spremljanje lig in e-pošta po kolu', true, true],
                    ['Forma in nasprotniki', false, true],
                    ['Napoved tekme', false, true],
                    ['Nizi, točke in razrezi', false, true],
                    ['Domača stran po meri', false, true],
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
                Brezplačen račun ima prvi mesec vse iz Premium, brez kartice; mesec začne teči, ko je račun
                povezan s tvojim zapisom.{' '}
                {starejsiOd21 === null
                  ? `Premium stane ${oblikujCeno(cenePremium.mlajsi)} ${enotaCene} do 21. leta in ${oblikujCeno(cenePremium.starejsi)} po njem.`
                  : `${starejsiOd21 ? `Cena od 21. leta naprej; mlajši plačajo ${oblikujCeno(cenePremium.mlajsi)} ${enotaCene}.` : 'Cena do 21. leta.'} Preklic kadarkoli.`}
              </p>
            </>
          )}

          {trenutniKorak === 'paket' && jeOrganizator && (
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
              {jeOrganizator && <p className="registracija__uvod">Kontaktna oseba organizatorja.</p>}
              {kratko && (
                <p className="registracija__uvod">
                  {liga
                    ? `${liga.ime}: po vsakem kolu ti pošljemo izide in lestvico. `
                    : igralec
                      ? `${igralec.ime}: na domači strani vidiš zadnje tekme in naslednji termin. `
                      : 'Račun je brezplačen: spremljaš svoje lige in po vsakem kolu dobiš izide po e-pošti. '}
                  {/* Preizkus Premium začne povezava z zapisom igralca - navijač je nima. */}
                  {jeNavijac
                    ? 'Račun je brezplačen in pripravljen takoj, ko potrdiš e-pošto.'
                    : 'Prvi mesec dobiš še Premium brez plačila, kartica ni potrebna.'}
                </p>
              )}
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
              {!jeOrganizator && (
                <label className="obrazec__polje">
                  <span>Datum rojstva</span>
                  <input
                    type="date"
                    value={s.datumRojstva}
                    max={danes}
                    onChange={(d) => posodobi({ datumRojstva: d.target.value })}
                  />
                  <span className="registracija__polje-pripis">
                    {jeNavijac
                      ? 'Javno ni viden. Mlajši od 15 let potrebujejo soglasje starša oz. skrbnika.'
                      : 'Javno ni viden. Z njim poiščemo tvoj zapis med igralci.'}
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
              {jeOrganizator && (
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
                    oznaka={jeOrganizator ? 'Klub, ki ga zastopaš' : 'Klub'}
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
                    {/* Kratka pot nima koraka »Paket«: paket je tam dan, ne izbira. */}
                    {seznamKorakov.includes(vrstica.korak) ? (
                      <button type="button" className="povezava-gumb" onClick={() => pojdiNaKorak(vrstica.korak)}>
                        Uredi
                      </button>
                    ) : (
                      <span />
                    )}
                  </div>
                ))}
                {koda && (
                  <div className="registracija__povzetek-vrstica">
                    <span className="registracija__povzetek-oznaka">Koda</span>
                    <span className="registracija__povzetek-vrednost">
                      {koda.koda} · {opisUgodnosti(koda)}
                    </span>
                    <button type="button" className="povezava-gumb" onClick={odstraniKodo}>
                      Odstrani
                    </button>
                  </div>
                )}
              </div>
              {/* Koda velja za vsako vlogo: navijač dobi odstotek na paket Navijač ali mesece paketa. */}
              {!koda && (
                <div className="registracija__koda-popusta">
                  {s.kodaOdprta ? (
                    <div className="obrazec__polje">
                      <span>Koda za popust</span>
                      <div className="registracija__koda-popusta-vnos">
                        <input
                          aria-label="Koda za popust"
                          value={s.kodaVnos}
                          onChange={(d) => posodobi({ kodaVnos: d.target.value, kodaNapaka: null })}
                          onKeyDown={(d) => {
                            if (d.key === 'Enter') {
                              d.preventDefault()
                              void uporabiKodo()
                            }
                          }}
                          autoComplete="off"
                          autoCapitalize="characters"
                          spellCheck={false}
                          autoFocus
                        />
                        <button
                          type="button"
                          className="gumb"
                          disabled={!s.kodaVnos.trim() || s.kodaPreverja}
                          onClick={() => void uporabiKodo()}
                        >
                          {s.kodaPreverja ? 'Preverjam …' : 'Uporabi'}
                        </button>
                      </div>
                      {s.kodaNapaka ? (
                        <div className="napaka" role="alert">{s.kodaNapaka}</div>
                      ) : (
                        s.kodaVnos.trim() && (
                          <span className="registracija__polje-pripis">
                            Pritisni »Uporabi«, sicer koda ne velja.
                          </span>
                        )
                      )}
                      <button
                        type="button"
                        className="povezava-gumb"
                        onClick={() => posodobi({ kodaOdprta: false, kodaVnos: '', kodaNapaka: null })}
                      >
                        Nimam kode
                      </button>
                    </div>
                  ) : (
                    <button type="button" className="povezava-gumb" onClick={() => posodobi({ kodaOdprta: true })}>
                      Imam kodo za popust
                    </button>
                  )}
                </div>
              )}
              <div className="registracija__znesek">
                <span className="registracija__znesek-oznake">
                  <span className="registracija__znesek-oznaka">{koda?.vrsta === 'ODSTOTEK' ? 'Prvo plačilo' : 'Za plačilo danes'}</span>
                  <span className="registracija__znesek-oznaka">{obnovaBesedilo}</span>
                </span>
                <span className="registracija__znesek-st">{cenaNapis(cena)}</span>
              </div>
              <label className="registracija__pogoji">
                <input type="checkbox" checked={s.pogoji} onChange={(d) => posodobi({ pogoji: d.target.checked })} />
                {/* Povezavi odpreta nov zavihek: okno registracije bi se ob
                    prehodu na drugo stran zaprlo in vpisano bi izginilo. */}
                <span>
                  Strinjam se s{' '}
                  <a href="/pogoji#pogoji" target="_blank" rel="noopener">pogoji uporabe</a> in{' '}
                  <a href="/pogoji#zasebnost" target="_blank" rel="noopener">obdelavo podatkov</a>.
                </span>
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

          {trenutniKorak === 'konec' && jeNavijac && (
            <KonecNavijaca
              paketNapis={
                s.paket === 'NAVIJAC'
                  ? 'Navijač'
                  : koda?.vrsta === 'NAVIJAC_MESECI' && koda.meseci
                    ? `Navijač: ${mesecev(koda.meseci)} brez plačila`
                    : 'Brezplačno'
              }
              ligaSpremljana={ligaSpremljana}
              igralecSpremljan={igralecSpremljan}
              onZapri={onZapri}
            />
          )}
          {trenutniKorak === 'konec' && !jeNavijac && (
            <KonecKoraka
              jeIgralec={jeIgralec}
              povezan={!!s.izidPovezan}
              organizator={s.izidOrganizator}
              imePaketa={imePaketa}
              placljiv={placljiv}
              obnovaBesedilo={obnovaBesedilo}
              preizkusDo={s.izidPreizkusDo}
              meseciKode={koda?.vrsta === 'PREMIUM_MESECI' ? koda.meseci : null}
              brezPlacilaMeseci={koda?.vrsta === 'ORGANIZATOR_MESECI' ? koda.meseci : null}
              rating={predogled.data?.rating ?? null}
              org={org}
              ligaSpremljana={ligaSpremljana}
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
          Nov račun · {OZNAKA_VLOGE[vloga]}
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

/* Zadnji korak navijača: račun ne čaka nikogar, zato so prvi koraki tisto, kar
   gledalec počne tudi brez računa - z računom pa pride izid kola po e-pošti. */
function KonecNavijaca({
  paketNapis,
  ligaSpremljana,
  igralecSpremljan,
  onZapri,
}: {
  paketNapis: string
  ligaSpremljana: string | null
  igralecSpremljan: string | null
  onZapri: () => void
}) {
  return (
    <>
      <p className="registracija__uvod">E-pošta je potrjena. Račun navijača je pripravljen.</p>
      <div className="registracija__kolofon">
        <div className="registracija__kolofon-vrstica">
          <span className="registracija__kolofon-oznaka">Paket</span>
          <span className="registracija__kolofon-vrednost">{paketNapis}</span>
        </div>
        <div className="registracija__kolofon-vrstica">
          <span className="registracija__kolofon-oznaka">E-pošta</span>
          <span className="registracija__kolofon-vrednost registracija__kolofon-vrednost--uspeh">Potrjena</span>
        </div>
      </div>
      <div>
        <div className="registracija__prvi-koraki-glava">
          <h3 className="registracija__prvi-koraki-naslov">Prvi koraki</h3>
          <span className="registracija__prvi-koraki-stevec">0 od 3</span>
        </div>
        <div className="registracija__korak-prvi">
          <span className="registracija__korak-prvi-st">01</span>
          <span className="registracija__korak-prvi-besedilo">
            <span className="registracija__korak-prvi-naslov">
              {igralecSpremljan
                ? `Spremljaš ${igralecSpremljan}`
                : ligaSpremljana
                  ? `Spremljaš ${ligaVTozilniku(ligaSpremljana)}`
                  : 'Izberi lige, ki jih spremljaš'}
            </span>
            <span className="registracija__korak-prvi-opis">
              {igralecSpremljan
                ? 'Zadnje tekme in naslednji termin sta na domači strani.'
                : 'Njihova kola so na domači strani, izidi vsakega kola pridejo po e-pošti.'}
            </span>
          </span>
          <Link
            to={igralecSpremljan ? '/' : '/lige'}
            className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno"
            onClick={onZapri}
          >
            {igralecSpremljan ? 'Domov →' : 'Lige →'}
          </Link>
        </div>
        <div className="registracija__korak-prvi">
          <span className="registracija__korak-prvi-st">02</span>
          <span className="registracija__korak-prvi-besedilo">
            <span className="registracija__korak-prvi-naslov">Preveri koledar</span>
            <span className="registracija__korak-prvi-opis">Kdaj in kje je naslednji turnir ali kolo.</span>
          </span>
          <Link to="/koledar" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
            Koledar →
          </Link>
        </div>
        <div className="registracija__korak-prvi">
          <span className="registracija__korak-prvi-st">03</span>
          <span className="registracija__korak-prvi-besedilo">
            <span className="registracija__korak-prvi-naslov">Poglej lestvico</span>
            <span className="registracija__korak-prvi-opis">Turnirko rating vseh igralcev, po spolu in kategorijah.</span>
          </span>
          <Link to="/lestvica" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
            Lestvica →
          </Link>
        </div>
      </div>
    </>
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
  preizkusDo,
  meseciKode,
  brezPlacilaMeseci,
  rating,
  org,
  ligaSpremljana,
  onZapri,
}: {
  jeIgralec: boolean
  povezan: boolean
  organizator: boolean
  imePaketa: string
  placljiv: boolean
  obnovaBesedilo: string
  /* Konec preizkusa Premium, ki se je začel s povezavo (ISO). */
  preizkusDo: string | null
  /* Meseci Premium, ki jih prinaša koda popusta (igralec; začnejo teči ob povezavi). */
  meseciKode: number | null
  /* Meseci organizatorskega paketa brez plačila iz kode (začnejo teči ob potrditvi). */
  brezPlacilaMeseci: number | null
  rating: number | null
  org: { lig: number; turnirjev: number } | null
  /* Liga, ki jo je račun začel spremljati ob registraciji (gumb »Spremljaj«). */
  ligaSpremljana: string | null
  onZapri: () => void
}) {
  const paketNapis = !jeIgralec || placljiv
    ? `${imePaketa}${placljiv
        ? ` · ${obnovaBesedilo.toLowerCase()}`
        : brezPlacilaMeseci ? ` · brez plačila: ${mesecev(brezPlacilaMeseci)}` : ''}`
    : preizkusDo
      ? `Premium do ${oblikujDatum(preizkusDo)} · brez plačila`
      : meseciKode
        ? `Brezplačno · Premium po povezavi: ${mesecev(meseciKode)}`
        : 'Brezplačno · mesec Premium po povezavi'
  return (
    <>
      <p className="registracija__uvod">
        {jeIgralec
          ? (povezan
              ? 'E-pošta je potrjena in račun je povezan s tvojim zapisom med igralci.'
              : 'E-pošta je potrjena. Račun poveže administrator; do takrat si prijavljen kot gost.')
          : 'E-pošta je potrjena. Račun čaka, da ti administrator dodeli vlogo organizatorja in klub.'
            + (brezPlacilaMeseci ? ' Paket brez plačila začne teči ob tej potrditvi.' : '')}
      </p>
      {organizator && <span className="znacka">Čaka potrditev administratorja</span>}
      <div className="registracija__kolofon">
        <div className="registracija__kolofon-vrstica">
          <span className="registracija__kolofon-oznaka">Paket</span>
          <span className="registracija__kolofon-vrednost">{paketNapis}</span>
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
                  {povezan
                    ? 'Rating, zgodovina tekem in forma.'
                    : `Profil se odpre, ko administrator poveže račun. Takrat začne teči tudi ${
                        meseciKode ? `Premium brez plačila (${mesecev(meseciKode)})` : 'mesec Premium'}.`}
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
                <span className="registracija__korak-prvi-naslov">
                  {ligaSpremljana ? `Spremljaš ${ligaVTozilniku(ligaSpremljana)}` : 'Izberi lige, ki jih spremljaš'}
                </span>
                <span className="registracija__korak-prvi-opis">
                  Njihova kola so na domači strani, izidi vsakega kola pridejo po e-pošti.
                </span>
              </span>
              <Link to="/lige" className="registracija__korak-prvi-meta registracija__korak-prvi-meta--dejavno" onClick={onZapri}>
                Lige →
              </Link>
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
