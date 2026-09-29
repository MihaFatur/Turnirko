/* Stran »Naročnina« za organizatorja (design_handoff_narocnina_organizatorja):
   trenutni paket (Basic / Plus / Pro), dni do obnove, obdobje in poraba,
   primerjava treh paketov z nadgradnjo (takoj, s sorazmernim doplačilom) ali
   znižanjem (ob obnovi), preklic in obnova ter stanje »brez paketa« s plačilom
   prek Stripa.

   Zgradba in razredi so isti kot pri igralcu (NarocninaIgralec): plošča je
   navaden `.registracija` z levim stolpcem (telefon: trak), kolofon, znesek in
   obvestilo so `.registracija__*`. Nova sta le primerjalna tabela s tremi
   stolpci in okvir razlike med paketoma (razdelek »Naročnina organizatorja« v
   slog.css). Vsi organizatorski paketi so letni: izbirnika Mesečno/Letno ni.

   Kar strežnik ve, ne izračunavamo: obdobje, tekočo ceno in zabeleženo
   znižanje pove NarocninaDto, porabo paketa organizatorski pregled (isti vir
   kot palica na nadzorni plošči). Cene in meje ostanejo v api/tipi.ts;
   sorazmerno doplačilo je le PREDOGLED - končni znesek zaračuna Stripe. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import {
  CENA_ORGANIZATOR_LETNO,
  OMEJITVE_ORGANIZATORJA,
  type NarocninaDto,
  type OrganizatorPregledDto,
} from '../api/tipi'
import { narocninaApi, organizatorApi, placilaApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { danesIso, dniMed, oblikujCeno, oblikujDanMesec, oblikujDatum, sklonDni } from '../pomozno/oblikovanje'
import {
  jeOrganizatorPaket,
  KRATKO_IME_PAKETA,
  sklonLigTozilnik,
  sklonTekocihLig,
  sklonTurnirjev,
  sklonTurnirjevTozilnik,
  type OrganizatorPaket,
} from '../pomozno/organizatorPregled'
import { useTelefon } from '../pomozno/telefon'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { ZnakTurnirko } from './Postavitev'
import { useVrnitevSPlacila, type Obvestilo } from './useVrnitevSPlacila'

/* Koliko segmentov ima trak obdobja (dvanajst = leto v mesecih, bere se kot
   »koliko je že mimo«). */
const SEGMENTOV = 12

const PAKETI: OrganizatorPaket[] = ['ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO']

const PRVI_PAKET: OrganizatorPaket = 'ORGANIZATOR_BASIC'
const PRIVZETI_PAKET: OrganizatorPaket = 'ORGANIZATOR_PLUS'

const ZA_KOGA: Record<OrganizatorPaket, string> = {
  ORGANIZATOR_BASIC: 'Za klub z eno ligo in dvema turnirjema na sezono.',
  ORGANIZATOR_PLUS: 'Za večji klub ali regijo: tri lige hkrati in turnir skoraj vsak drugi mesec.',
  ORGANIZATOR_PRO: 'Za zvezo ali organizatorja cele sezone: pet lig hkrati in deset turnirjev.',
}

const FUNKCIJE: { ime: string; opis: string; pot: string; cilj: string }[] = [
  {
    ime: 'Turnirji s skupinami in mrežo',
    opis: 'Skupine, nosilci in izločilna mreža do finala. Žreb samodejno ali ročno.',
    pot: '/turnirji',
    cilj: 'Turnirji',
  },
  {
    ime: 'Ekipne lige in zapisniki',
    opis: 'Kola, postave in zapisniki ekipnih dvobojev; lestvica lige se izračuna sama.',
    pot: '/lige',
    cilj: 'Lige',
  },
  {
    ime: 'Vnos rezultatov v dvorani',
    opis: 'Rezultat po nizih kar s telefona. Lestvica, mreža in koledar se osvežijo takoj.',
    pot: '/moj-profil',
    cilj: 'Plošča',
  },
  {
    ime: 'Listki in zapisnik za tisk',
    opis: 'Listki za mize in zapisnik NTZS, pripravljeni za tiskalnik v dvorani.',
    pot: '/turnirji',
    cilj: 'Turnirji',
  },
  {
    ime: 'Rating po vsaki tekmi',
    opis: 'Odigrane tekme štejejo v rating igralcev brez ročnega uvoza.',
    pot: '/lestvica',
    cilj: 'Lestvica',
  },
  {
    ime: 'Nadzorna plošča sezone',
    opis: 'Kaj čaka vnos ali žreb, termini 14 dni naprej, poraba paketa in arhiv sezon.',
    pot: '/moj-profil',
    cilj: 'Plošča',
  },
]

const stevilka = (i: number) => String(i + 1).padStart(2, '0')

const CENA = CENA_ORGANIZATOR_LETNO
const enota = ' / leto'

/* Brezplačno leto Pro (obstoječi organizatorji) ima ceno 0. */
const cenaBesedilo = (znesek: number) => (znesek === 0 ? 'Brezplačno' : oblikujCeno(znesek))
const zaokrozi = (znesek: number) => Math.round(znesek * 100) / 100

/* »preostalih 15 dni«, »preostali 1 dan«: števnik zahteva svoj sklon že v
   pridevniku (sklonDni loči samo dan / dni). */
function preostalihDni(n: number): string {
  if (n === 1) return 'preostali 1 dan'
  if (n === 2) return 'preostala 2 dneva'
  if (n === 3 || n === 4) return `preostale ${n} dni`
  return `preostalih ${n} dni`
}

const uspehPoVrnitvi = (n: NarocninaDto) =>
  `Stripe je potrdil plačilo ${oblikujCeno(n.cena ?? 0)}. Paket ${
    jeOrganizatorPaket(n.paket) ? KRATKO_IME_PAKETA[n.paket] : ''
  } je vklopljen.`

/* Nalaga naročnino in porabo ter obravnava vrnitev s Stripe Checkouta
   (useVrnitevSPlacila); sam pogled je ločen, da ima vedno podatke. */
export function NarocninaOrganizator() {
  const poizvedba = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  /* Poraba je dopolnilo: stran se izriše brez nje, vrstica in opozorila ob
     znižanju pa se pojavijo, ko pride. */
  const pregled = useQuery({ queryKey: ['organizator-pregled'], queryFn: organizatorApi.pregled })
  const vrnitevObvestilo = useVrnitevSPlacila({
    caka: 'Plačilo je prejeto. Paket se vklaplja …',
    uspeh: uspehPoVrnitvi,
  })

  if (poizvedba.error) return <NapakaPoizvedbe poizvedba={poizvedba} kaj="naročnine" />
  if (!poizvedba.data) return <p className="obvestilo">Nalagam naročnino …</p>

  return (
    <PogledNarocnine
      narocnina={poizvedba.data}
      poraba={pregled.data ?? null}
      vrnitevObvestilo={vrnitevObvestilo}
    />
  )
}

function PogledNarocnine({
  narocnina: n,
  poraba,
  vrnitevObvestilo,
}: {
  narocnina: NarocninaDto
  poraba: OrganizatorPregledDto | null
  vrnitevObvestilo: Obvestilo | null
}) {
  const { osvezi } = useAvtentikacija()
  const telefon = useTelefon()
  const odjemalec = useQueryClient()

  const [izbranPaket, nastaviIzbran] = useState<OrganizatorPaket | null>(null)
  const [vprasanjePreklic, nastaviVprasanje] = useState(false)
  const [obvestiloRocno, nastaviObvestilo] = useState<Obvestilo | null>(null)
  const [napaka, nastaviNapako] = useState<string | null>(null)

  const trenutni: OrganizatorPaket | null = n.aktivna && jeOrganizatorPaket(n.paket) ? n.paket : null
  const jeBrez = trenutni === null
  const jePreklicana = trenutni !== null && n.preklicana
  const jeAktivna = trenutni !== null && !n.preklicana
  const imaPaket = !jeBrez
  /* Brezplačno leto Pro (obstoječi organizatorji) nima Stripa: paketa ni mogoče
     zamenjati ne preklicati, stran ga samo pokaže. */
  const upravlja = jeBrez || n.upravljiva

  /* Zabeleženo znižanje ob obnovi. */
  const prehodNa: OrganizatorPaket | null =
    jeAktivna && n.naslednjiPaket && jeOrganizatorPaket(n.naslednjiPaket) ? n.naslednjiPaket : null

  /* Ciljni paket v primerjavi: pri »brez« je vedno kakšen (privzeto Plus), sicer
     samo, kar je uporabnik izbral. */
  const cilj: OrganizatorPaket | null = jeBrez
    ? (izbranPaket ?? PRIVZETI_PAKET)
    : upravlja
      ? izbranPaket
      : null
  const kazeRazliko = cilj !== null && (jeBrez || cilj !== trenutni)

  // ---------- Obdobje ----------

  const konec = oblikujDatum(n.obdobjeDo)
  const konecKratko = oblikujDanMesec(n.obdobjeDo)
  const imaObdobje = imaPaket && !!n.obdobjeOd && !!n.obdobjeDo
  const dni = Math.max(0, dniMed(danesIso(), n.obdobjeDo) ?? 0)
  const dniObdobja = dniMed(n.obdobjeOd, n.obdobjeDo) ?? 0
  const pretecenih = imaObdobje && dniObdobja > 0
    ? Math.min(SEGMENTOV, Math.max(0, Math.round((1 - dni / dniObdobja) * SEGMENTOV)))
    : 0
  const segmenti = Array.from({ length: SEGMENTOV }, (_, i) => i < pretecenih)

  // ---------- Cene in meje ----------

  const cenaTekoca = trenutni ? (n.cena ?? CENA[trenutni]) : 0
  /* Cena ob naslednji obnovi: novi paket po znižanju, sicer tekoča. */
  const cenaObnove = prehodNa ? CENA[prehodNa] : cenaTekoca
  const meje = (p: OrganizatorPaket) => OMEJITVE_ORGANIZATORJA[p]
  const lige = poraba?.lige ?? null
  const turnirji = poraba?.turnirji ?? null

  // ---------- Dejanja ----------

  function poDejanju(podatki: NarocninaDto, obvestilo: Obvestilo | null) {
    nastaviNapako(null)
    nastaviObvestilo(obvestilo)
    odjemalec.setQueryData(['narocnina'], podatki)
    /* Profil (paketAktiven, meni) se bere iz konteksta, meje paketa pa
       nadzorna plošča iz pregleda: oboje se spremeni z paketom. */
    void osvezi()
    void odjemalec.invalidateQueries({ queryKey: ['organizator-pregled'] })
  }

  const naNapako = (e: unknown) => nastaviNapako(opisNapake(e))

  const zamenjaj = useMutation({
    mutationFn: (paket: OrganizatorPaket) => narocninaApi.zamenjajPaket(paket),
    onSuccess: (odgovor, paket) => {
      nastaviIzbran(null)
      const ime = KRATKO_IME_PAKETA[paket]
      if (odgovor.doplacilo !== null) {
        poDejanju(odgovor.narocnina, {
          besedilo: `${ime} je vklopljen. Stripe je zaračunal doplačilo ${oblikujCeno(odgovor.doplacilo)}.`,
          uspeh: true,
        })
      } else {
        poDejanju(odgovor.narocnina, {
          besedilo: `Od ${konec} imaš paket ${ime}, ${oblikujCeno(CENA[paket])}${enota}.`,
          uspeh: true,
          razveljavi: true,
        })
      }
    },
    onError: naNapako,
  })

  const razveljavi = useMutation({
    mutationFn: () => narocninaApi.razveljaviPrehod(),
    onSuccess: (podatki) => {
      nastaviIzbran(null)
      poDejanju(podatki, null)
    },
    onError: naNapako,
  })

  const preklici = useMutation({
    mutationFn: () => narocninaApi.preklici(),
    onSuccess: (podatki) => {
      nastaviIzbran(null)
      nastaviVprasanje(false)
      poDejanju(podatki, {
        besedilo: `Naročnina je preklicana. ${trenutni ? KRATKO_IME_PAKETA[trenutni] : ''} velja do ${konec}.`,
        uspeh: true,
      })
    },
    onError: naNapako,
  })

  const obnovi = useMutation({
    mutationFn: (paket: OrganizatorPaket | null) => narocninaApi.obnovi({ paket }),
    onSuccess: (podatki) => {
      nastaviIzbran(null)
      poDejanju(podatki, { besedilo: `Naročnina je obnovljena. Naslednja obnova ${konec}.`, uspeh: true })
    },
    onError: naNapako,
  })

  const nadgradi = useMutation({
    mutationFn: (paket: OrganizatorPaket) => placilaApi.nadgradnja({ paket, ciklus: 'LETNO' }),
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
    onError: naNapako,
  })

  const portal = useMutation({
    mutationFn: () => placilaApi.portal(),
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
    onError: naNapako,
  })

  const izberi = (paket: OrganizatorPaket) => () => {
    if (!upravlja) return
    nastaviIzbran(!jeBrez && paket === trenutni ? null : paket)
    nastaviVprasanje(false)
  }

  /* Sporočilo na vrhu telesa: najprej tisto, ki ga je sprožilo dejanje na tej
     strani, nato vrnitev s Stripa, nazadnje zabeleženo znižanje - to se mora
     dati umakniti tudi po osvežitvi strani, ne le tik po potrditvi. */
  const obvestilo: Obvestilo | null =
    obvestiloRocno
    ?? vrnitevObvestilo
    ?? (prehodNa
      ? {
          besedilo: `Od ${konec} imaš paket ${KRATKO_IME_PAKETA[prehodNa]}, ${oblikujCeno(CENA[prehodNa])}${enota}.`,
          uspeh: true,
          razveljavi: true,
        }
      : null)

  const zasedeno =
    zamenjaj.isPending || razveljavi.isPending || preklici.isPending || obnovi.isPending

  // ---------- Razlika med paketoma ----------

  let razlika: {
    nadgradnja: boolean
    naslov: string
    opis: string
    vrstice: { oznaka: string; vrednost: string; razred?: string }[]
    opozorila: string[]
  } | null = null
  let menjava: {
    besedilo: string
    oznaka1: string
    oznaka2: string
    znesek: string
    gumb: string
    ostani: string
    gor: boolean
  } | null = null

  if (cilj && kazeRazliko) {
    const imeCilja = KRATKO_IME_PAKETA[cilj]
    if (jeBrez) {
      razlika = {
        nadgradnja: true,
        naslov: `Kaj dobiš s paketom ${imeCilja}`,
        opis: ZA_KOGA[cilj],
        vrstice: [
          { oznaka: 'Tekoče lige', vrednost: `${meje(cilj).lig} ${sklonTekocihLig(meje(cilj).lig)}` },
          { oznaka: 'Turnirji na sezono', vrednost: `${meje(cilj).turnirjev} ${sklonTurnirjev(meje(cilj).turnirjev)}` },
          { oznaka: 'Cena', vrednost: oblikujCeno(CENA[cilj]) + enota },
        ],
        opozorila: [],
      }
    } else if (trenutni) {
      const gor = PAKETI.indexOf(cilj) > PAKETI.indexOf(trenutni)
      const razlikaLig = meje(cilj).lig - meje(trenutni).lig
      const razlikaTurnirjev = meje(cilj).turnirjev - meje(trenutni).turnirjev
      const pozitivno = (r: number) => (r > 0 ? `+${r}` : `${r}`)
      const opozorila: string[] = []
      if (!gor && lige && lige.uporabljeno > meje(cilj).lig) {
        opozorila.push(
          `V tej sezoni imaš ${lige.uporabljeno} ${sklonLigTozilnik(lige.uporabljeno)}, ${imeCilja} dovoli ${meje(cilj).lig}. `
          + 'Obstoječe tečejo naprej; novih do nove sezone ne ustvariš.',
        )
      }
      if (!gor && turnirji && turnirji.uporabljeno > meje(cilj).turnirjev) {
        opozorila.push(
          `V tej sezoni imaš ${turnirji.uporabljeno} ${sklonTurnirjevTozilnik(turnirji.uporabljeno)}, ${imeCilja} jih dovoli `
          + `${meje(cilj).turnirjev}. Novih do nove sezone ne ustvariš.`,
        )
      }
      const razred = gor ? 'registracija__kolofon-vrednost--uspeh' : 'narocnina__vrednost--negativna'
      razlika = {
        nadgradnja: gor,
        naslov: gor ? `Kaj pridobiš s paketom ${imeCilja}` : `Kaj se spremeni s paketom ${imeCilja}`,
        opis: ZA_KOGA[cilj],
        vrstice: [
          {
            oznaka: 'Tekoče lige',
            vrednost: `${meje(trenutni).lig} → ${meje(cilj).lig} · ${pozitivno(razlikaLig)}`,
            razred,
          },
          {
            oznaka: 'Turnirji na sezono',
            vrednost: `${meje(trenutni).turnirjev} → ${meje(cilj).turnirjev} · ${pozitivno(razlikaTurnirjev)}`,
            razred,
          },
          { oznaka: 'Cena', vrednost: `${oblikujCeno(cenaTekoca)} → ${oblikujCeno(CENA[cilj])}${enota}` },
        ],
        opozorila,
      }
      if (jeAktivna) {
        if (gor) {
          const doplacilo = zaokrozi((CENA[cilj] - cenaTekoca) * (dni / (dniObdobja || 1)))
          menjava = {
            besedilo:
              `${imeCilja} se vklopi takoj. Za ${preostalihDni(dni)} obdobja doplačaš razliko, `
              + `od ${konec} plačuješ ${oblikujCeno(CENA[cilj])}${enota}.`,
            oznaka1: 'Doplačilo danes',
            oznaka2: `Sorazmerno · ${dni} od ${dniObdobja} dni`,
            znesek: oblikujCeno(doplacilo),
            gumb: `Nadgradi v ${imeCilja} · ${oblikujCeno(doplacilo)}`,
            ostani: `Ostani pri ${KRATKO_IME_PAKETA[trenutni]}`,
            gor: true,
          }
        } else {
          menjava = {
            besedilo:
              `${KRATKO_IME_PAKETA[trenutni]} velja do konca plačanega obdobja, ${konec}. `
              + `Od takrat imaš ${imeCilja} za ${oblikujCeno(CENA[cilj])}${enota}; danes ne plačaš nič.`,
            oznaka1: `Od ${konec}`,
            oznaka2: `${oblikujCeno(zaokrozi(cenaTekoca - CENA[cilj]))} manj${enota}`,
            znesek: oblikujCeno(CENA[cilj]),
            gumb: `Preidi na ${imeCilja} ob obnovi`,
            ostani: `Ostani pri ${KRATKO_IME_PAKETA[trenutni]}`,
            gor: false,
          }
        }
      }
    }
  }

  // ---------- Stolpci primerjave ----------

  const stolpci = PAKETI.map((paket) => {
    const jeCilj = cilj === paket && (jeBrez || paket !== trenutni)
    const jeTrenutni = !jeBrez && paket === trenutni && !jeCilj
    let oznaka = 'Izberi'
    if (!jeBrez && trenutni) {
      oznaka = PAKETI.indexOf(paket) > PAKETI.indexOf(trenutni) ? 'Nadgradi' : 'Zamenjaj'
    }
    if (jeTrenutni) oznaka = jePreklicana ? `Do ${konecKratko}` : 'Trenutno'
    if (!jeCilj && !jeTrenutni && prehodNa === paket) oznaka = 'Od obnove'
    if (jeCilj) oznaka = 'Izbrano'
    return { paket, jeCilj, jeTrenutni, oznaka }
  })
  const razredStolpca = (s: { jeCilj: boolean; jeTrenutni: boolean }) =>
    s.jeCilj ? 'izbran' : s.jeTrenutni ? 'trenutni' : null

  const vrsticeTabele: { funkcija: string; vrednost: (p: OrganizatorPaket) => string; da?: boolean }[] = [
    { funkcija: 'Tekoče lige', vrednost: (p) => String(meje(p).lig) },
    { funkcija: 'Turnirji na sezono', vrednost: (p) => String(meje(p).turnirjev) },
    { funkcija: 'Vse funkcije spodaj', vrednost: () => 'DA', da: true },
  ]

  // ---------- Besedila ----------

  const imeStrani = trenutni ? `Organizator ${KRATKO_IME_PAKETA[trenutni]}` : 'Organizator'
  const statusNapis = jeBrez ? 'Paket · ni aktiven' : jePreklicana ? 'Naročnina · preklicana' : 'Naročnina · aktivna'
  const statusDesno = jeBrez
    ? 'Preklic kadarkoli'
    : jePreklicana || !upravlja
      ? `Velja do ${konec}`
      : `Obnova ${konec}`
  const naslovPlosce = jeBrez ? 'Izberi paket' : jePreklicana ? 'Brez obnove' : 'Tvoja naročnina'
  const trakCena = jeBrez
    ? oblikujCeno(CENA[cilj ?? PRIVZETI_PAKET]) + enota
    : jePreklicana
      ? 'Brez obnove'
      : cenaBesedilo(cenaTekoca) + (cenaTekoca === 0 ? '' : enota)
  const dniNapis = `Še ${dni} ${sklonDni(dni)}${jePreklicana ? ', nato brez paketa' : ''}`

  const zaSeznam = jeBrez ? (cilj ?? PRIVZETI_PAKET) : (trenutni ?? PRIVZETI_PAKET)
  const nazivStolpca = jeBrez
    ? `${KRATKO_IME_PAKETA[zaSeznam]} · kaj dobiš`
    : jePreklicana
      ? `${KRATKO_IME_PAKETA[zaSeznam]} · do ${konecKratko}`
      : `${KRATKO_IME_PAKETA[zaSeznam]} · vključeno`
  const seznam = [
    `${meje(zaSeznam).lig} ${sklonTekocihLig(meje(zaSeznam).lig)}`,
    `${meje(zaSeznam).turnirjev} ${sklonTurnirjev(meje(zaSeznam).turnirjev)} na sezono`,
    ...FUNKCIJE.map((f) => f.ime),
  ]

  const izbiraVrstice = [
    { oznaka: 'Paket', vrednost: KRATKO_IME_PAKETA[zaSeznam] },
    { oznaka: 'Plačevanje', vrednost: 'Letno' },
    { oznaka: 'Cena', vrednost: jeBrez ? oblikujCeno(CENA[zaSeznam]) : cenaBesedilo(cenaTekoca) },
  ]

  const kolofon: { oznaka: string; vrednost: string; razred?: string }[] = []
  if (trenutni) {
    kolofon.push(
      { oznaka: 'Naročen od', vrednost: oblikujDatum(n.narocenOd) || '—' },
      { oznaka: 'Paket', vrednost: `${KRATKO_IME_PAKETA[trenutni]} · ${meje(trenutni).lig} / ${meje(trenutni).turnirjev}` },
      { oznaka: 'Plačevanje', vrednost: `Letno · ${cenaBesedilo(cenaTekoca)}` },
      { oznaka: 'Trenutno obdobje', vrednost: imaObdobje ? `${oblikujDatum(n.obdobjeOd)} – ${konec}` : '—' },
    )
    if (lige && turnirji) {
      kolofon.push({
        oznaka: 'Poraba v sezoni',
        vrednost: `${lige.uporabljeno}/${lige.meja} lig · ${turnirji.uporabljeno}/${turnirji.meja} turn.`,
      })
    }
    if (jePreklicana) {
      kolofon.push({ oznaka: 'Velja do', vrednost: konec || '—', razred: 'narocnina__vrednost--negativna' })
    } else if (!upravlja) {
      kolofon.push({ oznaka: 'Velja do', vrednost: konec || '—' })
    } else {
      kolofon.push({ oznaka: 'Naslednja obnova', vrednost: `${konec || '—'} · ${cenaBesedilo(cenaObnove)}` })
    }
    if (prehodNa) {
      kolofon.push({
        oznaka: 'Paket od obnove',
        vrednost: `${KRATKO_IME_PAKETA[prehodNa]} · ${oblikujCeno(CENA[prehodNa])}${enota}`,
        razred: 'registracija__kolofon-vrednost--uspeh',
      })
    }
  }

  const glavaPaketa = jeBrez ? 'Paket' : jePreklicana ? 'Paket ob obnovi' : 'Zamenjaj paket'
  const pojasnilo = !upravlja
    ? `To je brezplačno leto paketa ${trenutni ? KRATKO_IME_PAKETA[trenutni] : ''} za obstoječe organizatorje, ki velja do ${konec}. `
      + 'Vsi paketi so letni in se razlikujejo samo po obsegu.'
    : jePreklicana && !cilj
      ? 'Vsi paketi so letni. Ob obnovi lahko izbereš tudi drug paket.'
      : prehodNa && trenutni
        ? `Prehod je zabeležen: do ${konec} ${KRATKO_IME_PAKETA[trenutni]}, nato ${KRATKO_IME_PAKETA[prehodNa]}.`
        : 'Vsi paketi so letni in se razlikujejo samo po obsegu. Lige štejejo tekoče (hkrati odprte), turnirji na sezono.'

  const preklicBesedilo = `${trenutni ? KRATKO_IME_PAKETA[trenutni] : ''} ostane do konca plačanega obdobja, ${konec}. `
    + 'Potem novih lig in turnirjev ne moreš ustvariti; obstoječa tekmovanja, rezultati in arhiv sezon ostanejo.'
  const potrditevBesedilo = `Obnove ${konec} ne bo. Čez ${dni} ${sklonDni(dni)} ustvarjanje lig in turnirjev zaklenemo. `
    + 'Do takrat naročnino obnoviš brez plačila.'

  const obnovaPaket = cilj ?? trenutni
  const obnovaGumb = `Obnovi ${obnovaPaket ? KRATKO_IME_PAKETA[obnovaPaket] : ''} · ${
    obnovaPaket ? oblikujCeno(CENA[obnovaPaket]) : ''
  }${enota}`

  const prednostiMeta = jeBrez
    ? `Od ${oblikujCeno(CENA[PRVI_PAKET])}${enota}`
    : `${FUNKCIJE.length} od ${FUNKCIJE.length} vključeno`

  return (
    <section>
      <div className="narocnina__glava">
        <div className="narocnina__glava-naslov">
          <h1 className="naslov-strani">
            <span className="narocnina__naslov-nad">Naročnina</span>
            <span className="narocnina__naslov-ime">{imeStrani}</span>
          </h1>
        </div>

        <div className="narocnina__glava-stanje">
          {imaPaket && (
            <div className={'narocnina__blok' + (jePreklicana ? ' narocnina__blok--preklicano' : '')}>
              <div className="narocnina__blok-vrstica">
                <span className="narocnina__blok-st">{imaObdobje ? dni : '—'}</span>
                <span className="narocnina__blok-oznaka">
                  {jePreklicana ? 'dni paket še velja' : upravlja ? 'dni do obnove' : 'dni paket še velja'}
                </span>
              </div>
              <div className="narocnina__blok-noga">
                <span>{jePreklicana || !upravlja ? `Velja do ${konec}` : `Obnova ${konec}`}</span>
                <span>
                  {jePreklicana ? 'Brez obnove' : upravlja ? cenaBesedilo(cenaObnove) : 'Brezplačno'}
                </span>
              </div>
            </div>
          )}
          {jeBrez && (
            <div className="registracija__kolofon narocnina__kolofon--crn">
              {[
                { oznaka: 'Paket', vrednost: 'Brez' },
                { oznaka: 'Basic', vrednost: oblikujCeno(CENA.ORGANIZATOR_BASIC) + enota },
                { oznaka: 'Pro', vrednost: oblikujCeno(CENA.ORGANIZATOR_PRO) + enota },
              ].map((v) => (
                <div className="registracija__kolofon-vrstica" key={v.oznaka}>
                  <span className="registracija__kolofon-oznaka">{v.oznaka}</span>
                  <span className="registracija__kolofon-vrednost">{v.vrednost}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {napaka && <div className="napaka">{napaka}</div>}

      <div className="registracija narocnina__plosca">
        {telefon ? (
          <div className="registracija__trak">
            <div className="registracija__trak-vrstica">
              <span className="registracija__trak-levo">
                <ZnakTurnirko velikost={20} />
                <span className="registracija__trak-korak">{statusNapis}</span>
              </span>
              <span className="registracija__trak-cena narocnina__trak-cena">{trakCena}</span>
            </div>
            {imaObdobje && (
              <div className="registracija__trak-segmenti">
                {segmenti.map((pretecen, i) => (
                  <span
                    key={i}
                    className={
                      'registracija__trak-segment' + (pretecen ? ' narocnina__trak-segment--pretecen' : '')
                    }
                  />
                ))}
              </div>
            )}
          </div>
        ) : (
          <aside className="registracija__stolpec">
            <div className="registracija__logotip">
              <ZnakTurnirko velikost={24} />
              Turnirko
            </div>
            <div>
              <span className="registracija__seznam-glava">{nazivStolpca}</span>
              <ul className="narocnina__seznam">
                {seznam.map((ime, i) => (
                  <li
                    key={ime}
                    className={
                      'registracija__korak narocnina__korak narocnina__korak--paket'
                      + (imaPaket ? ' registracija__korak--opravljen' : ' narocnina__korak--brez')
                      + (i < 2 ? ' narocnina__korak--obseg' : '')
                    }
                  >
                    <span className="registracija__korak-st">{imaPaket ? 'OK' : stevilka(i)}</span>
                    <span className="registracija__korak-ime">{ime}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="registracija__izbira">
              <span className="registracija__izbira-glava">Tvoja naročnina</span>
              {izbiraVrstice.map((v) => (
                <div className="registracija__izbira-vrstica" key={v.oznaka}>
                  <span className="registracija__izbira-oznaka">{v.oznaka}</span>
                  <span className="registracija__izbira-vrednost">{v.vrednost}</span>
                </div>
              ))}
            </div>
          </aside>
        )}

        <div className="registracija__desni">
          <div className="registracija__glava">
            {!telefon && (
              <div className="registracija__glava-vrstica narocnina__glava-vrstica">
                <span className="registracija__korak-napis">{statusNapis}</span>
                <span className="registracija__korak-napis">{statusDesno}</span>
              </div>
            )}
            <h2 className="registracija__naslov">
              <span className="registracija__glavni-naslov">{naslovPlosce}</span>
            </h2>
            {imaObdobje && (
              <div className="registracija__napredek">
                <div className="registracija__napredek-vrstica narocnina__napredek-vrstica narocnina__napredek-vrstica--paket">
                  <span className="registracija__napredek-trenutni">
                    Obdobje {oblikujDatum(n.obdobjeOd)} – {konec}
                  </span>
                  <span className="registracija__korak-napis">{dniNapis}</span>
                </div>
                <div className="registracija__napredek-segmenti">
                  {segmenti.map((pretecen, i) => (
                    <span
                      key={i}
                      className={
                        'registracija__napredek-segment'
                        + (pretecen ? ' registracija__napredek-segment--opravljen' : '')
                      }
                    />
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="registracija__telo">
            {obvestilo && (
              <div
                className={'registracija__obvestilo' + (obvestilo.uspeh ? ' registracija__obvestilo--uspeh' : '')}
                role="status"
              >
                <span>{obvestilo.besedilo}</span>
                {obvestilo.razveljavi && (
                  <button
                    type="button"
                    className="narocnina__obvestilo-gumb"
                    disabled={razveljavi.isPending}
                    onClick={() => razveljavi.mutate()}
                  >
                    Razveljavi
                  </button>
                )}
              </div>
            )}

            <div className="narocnina__sklopi">
              {imaPaket && (
                <div className="registracija__kolofon registracija__kolofon--rob-zgoraj">
                  {kolofon.map((v) => (
                    <div className="registracija__kolofon-vrstica" key={v.oznaka}>
                      <span className="registracija__kolofon-oznaka">{v.oznaka}</span>
                      <span className={'registracija__kolofon-vrednost' + (v.razred ? ` ${v.razred}` : '')}>
                        {v.vrednost}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              <div className="narocnina__sklop">
                <span className="registracija__zapis-glava">{glavaPaketa}</span>

                <div className="narocnina__tabela">
                  <div className="narocnina__tabela-vrstica narocnina__tabela-vrstica--glava">
                    <span className="registracija__primerjava-oznaka">Kaj dobiš</span>
                    {stolpci.map((s) => {
                      const stanje = razredStolpca(s)
                      return (
                        <button
                          type="button"
                          key={s.paket}
                          className={
                            'registracija__stolp narocnina__stolp'
                            + (stanje ? ` narocnina__stolp--${stanje}` : '')
                          }
                          aria-pressed={s.jeCilj}
                          disabled={!upravlja}
                          onClick={izberi(s.paket)}
                        >
                          <span className="registracija__stolp-ime">{KRATKO_IME_PAKETA[s.paket]}</span>
                          <span className="registracija__stolp-cena">{oblikujCeno(CENA[s.paket])}</span>
                          <span className="registracija__stolp-enota">Na leto</span>
                          <span
                            className={
                              'registracija__stolp-oznaka'
                              + (stanje ? ` narocnina__stolp-oznaka--${stanje}` : '')
                            }
                          >
                            {s.oznaka}
                          </span>
                        </button>
                      )
                    })}
                  </div>
                  {vrsticeTabele.map((v) => (
                    <div className="narocnina__tabela-vrstica" key={v.funkcija}>
                      <span className="registracija__primerjava-funkcija">{v.funkcija}</span>
                      {stolpci.map((s) => {
                        const stanje = razredStolpca(s)
                        return (
                          <span
                            key={s.paket}
                            className={
                              'narocnina__celica'
                              + (v.da ? ' narocnina__celica--da' : '')
                              + (stanje ? ` narocnina__celica--${stanje}` : '')
                            }
                          >
                            {v.vrednost(s.paket)}
                          </span>
                        )
                      })}
                    </div>
                  ))}
                </div>

                {razlika && (
                  <div
                    className={
                      'narocnina__razlika'
                      + (razlika.nadgradnja ? ' narocnina__razlika--nadgradnja' : ' narocnina__razlika--znizanje')
                    }
                  >
                    <span className="narocnina__razlika-naslov">{razlika.naslov}</span>
                    <span className="narocnina__razlika-opis">{razlika.opis}</span>
                    <div className="registracija__kolofon narocnina__razlika-kolofon">
                      {razlika.vrstice.map((v) => (
                        <div className="registracija__kolofon-vrstica" key={v.oznaka}>
                          <span className="registracija__kolofon-oznaka">{v.oznaka}</span>
                          <span className={'registracija__kolofon-vrednost' + (v.razred ? ` ${v.razred}` : '')}>
                            {v.vrednost}
                          </span>
                        </div>
                      ))}
                    </div>
                    {razlika.opozorila.map((o) => (
                      <span className="narocnina__razlika-opozorilo" key={o}>
                        {o}
                      </span>
                    ))}
                  </div>
                )}

                {menjava && cilj ? (
                  <div className="narocnina__preklop">
                    <p className="registracija__uvod">{menjava.besedilo}</p>
                    <div className="registracija__znesek narocnina__znesek narocnina__znesek--paket">
                      <span className="registracija__znesek-oznake">
                        <span className="registracija__znesek-oznaka">{menjava.oznaka1}</span>
                        <span className="registracija__znesek-oznaka">{menjava.oznaka2}</span>
                      </span>
                      <span className="registracija__znesek-st">{menjava.znesek}</span>
                    </div>
                    <div className="narocnina__dejanja">
                      <button
                        type="button"
                        className="gumb gumb--glavni narocnina__dejanje narocnina__dejanje--glavno"
                        disabled={zasedeno}
                        onClick={() => zamenjaj.mutate(cilj)}
                      >
                        {menjava.gumb}
                      </button>
                      <button
                        type="button"
                        className="gumb narocnina__dejanje narocnina__dejanje--drugo"
                        onClick={() => nastaviIzbran(null)}
                      >
                        {menjava.ostani}
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="registracija__pojasnilo narocnina__pojasnilo narocnina__pojasnilo--paket">{pojasnilo}</p>
                )}
              </div>

              {jeAktivna && upravlja && (
                <div className="narocnina__sklop">
                  <span className="registracija__zapis-glava">Preklic</span>
                  <p className="narocnina__besedilo">{preklicBesedilo}</p>
                  {!vprasanjePreklic ? (
                    <div className="narocnina__dejanja">
                      <button
                        type="button"
                        className="gumb gumb--nevaren narocnina__dejanje"
                        onClick={() => nastaviVprasanje(true)}
                      >
                        Prekliči naročnino
                      </button>
                    </div>
                  ) : (
                    <div className="narocnina__potrditev" role="alertdialog" aria-labelledby="narocnina-preklic">
                      <span className="narocnina__potrditev-naslov" id="narocnina-preklic">
                        Prekličem naročnino?
                      </span>
                      <span className="narocnina__potrditev-besedilo">{potrditevBesedilo}</span>
                      <div className="narocnina__dejanja">
                        <button
                          type="button"
                          className="gumb narocnina__gumb--preklic narocnina__dejanje narocnina__dejanje--drugo"
                          disabled={zasedeno}
                          onClick={() => preklici.mutate()}
                        >
                          Da, prekliči
                        </button>
                        <button
                          type="button"
                          className="gumb narocnina__gumb--papir narocnina__dejanje narocnina__dejanje--drugo"
                          onClick={() => nastaviVprasanje(false)}
                        >
                          Obdrži paket
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {jePreklicana && upravlja && (
                <div className="narocnina__sklop">
                  <span className="registracija__zapis-glava">Obnova</span>
                  <p className="narocnina__besedilo">
                    Obnova ne stane nič do {konec}. Takrat se obdobje nadaljuje z izbranim paketom in plačevanjem.
                  </p>
                  <div className="narocnina__dejanja">
                    <button
                      type="button"
                      className="gumb gumb--glavni narocnina__dejanje narocnina__dejanje--obnova narocnina__dejanje--obnova-paket"
                      disabled={zasedeno}
                      onClick={() => obnovi.mutate(cilj)}
                    >
                      {obnovaGumb}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="registracija__noga">
            <span className="registracija__placilo-pripis">Plačilo prek Stripe</span>
            {jeBrez && (
              <button
                type="button"
                className="gumb gumb--glavni narocnina__dejanje"
                disabled={nadgradi.isPending}
                onClick={() => nadgradi.mutate(cilj ?? PRIVZETI_PAKET)}
              >
                {nadgradi.isPending
                  ? 'Preusmerjam na plačilo …'
                  : `Nadaljuj na plačilo · ${oblikujCeno(CENA[cilj ?? PRIVZETI_PAKET])}`}
              </button>
            )}
            {imaPaket && upravlja && (
              <button
                type="button"
                className="gumb narocnina__dejanje"
                disabled={portal.isPending}
                onClick={() => portal.mutate()}
              >
                {portal.isPending ? 'Odpiram …' : 'Kartica in računi v Stripe'}
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="narocnina__prednosti">
        <div className="narocnina__prednosti-glava">
          <h2 className="narocnina__prednosti-naslov">V vsakem paketu</h2>
          <span className="narocnina__prednosti-meta">{prednostiMeta}</span>
        </div>
        <div className="narocnina__prednosti-seznam">
          {FUNKCIJE.map((f, i) => (
            <div className="narocnina__prednost" key={f.ime}>
              <span className={'narocnina__prednost-st' + (imaPaket ? ' narocnina__prednost-st--vkljuceno' : '')}>
                {stevilka(i)}
              </span>
              <div className="narocnina__prednost-besedilo">
                <span className="narocnina__prednost-ime">{f.ime}</span>
                <span className="narocnina__prednost-opis">{f.opis}</span>
              </div>
              {imaPaket ? (
                <Link to={f.pot} className="narocnina__prednost-povezava">
                  {f.cilj} →
                </Link>
              ) : (
                <span className="narocnina__prednost-povezava narocnina__prednost-povezava--oznaka">
                  Vsi paketi
                </span>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
