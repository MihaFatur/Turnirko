/* Stran »Naročnina« za igralca (design_handoff_narocnina): ime paketa, dni do
   obnove, obdobje, preklop mesečno ↔ letno, preklic in obnova; brez Premium
   nadgradnja s primerjalno tabelo kot v koraku »Paket« registracije.

   Stran ima tri stanja, ki jih določa zaledje (NarocninaDto): brez Premium,
   aktivna in preklicana (Premium velja do konca plačanega obdobja). Vse tri
   izrisujeta ISTO ploščo v istem jeziku kot razdeljeno okno registracije, zato
   so razredi `.registracija__*` (glej razdelek »Naročnina igralca« v
   slog.css). Organizatorski del strani je ostal v NarocninaStran.

   Kar strežnik ve, ne izračunavamo: obdobje, cikel in ceno tekočega cikla
   pove NarocninaDto (starejše naročnine imajo lahko drugo ceno od današnjega
   cenika). Cena DRUGEGA cikla za predogled preklopa pride iz cenika v
   api/tipi.ts po cenovnem pasu ob sklenitvi - zaledje je pri preklopu zadnja
   beseda in ceno izračuna znova. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { narocninaApi, placilaApi } from '../api/zahteve'
import {
  CENA_PREMIUM_LETNO,
  CENA_PREMIUM_MESECNO,
  OZNAKE_PAKET,
  type CiklusPlacila,
  type NarocninaDto,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { danesIso, dniMed, oblikujCeno, oblikujDatum, sklonDni } from '../pomozno/oblikovanje'
import { useTelefon } from '../pomozno/telefon'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { ZnakTurnirko } from './Postavitev'
import { useVrnitevSPlacila, type Obvestilo } from './useVrnitevSPlacila'

/* Koliko segmentov ima trak obdobja (dvanajst = mesec v šestinah tedna in leto
   v mesecih, oboje se bere kot »koliko je že mimo«). */
const SEGMENTOV = 12

const FUNKCIJE = [
  'Forma in nasprotniki',
  'Napoved tekme',
  'Nizi, točke in razrezi',
  'Spremljanje lig',
  'Zasebna statistika',
]

const PRIMERJAVA: [funkcija: string, brez: boolean][] = [
  ['Rezultati, lestvice, koledar', true],
  ['Prijava z lastnim računom', true],
  ['Forma in nasprotniki', false],
  ['Napoved tekme', false],
  ['Nizi, točke in razrezi', false],
  ['Spremljanje lig na domači strani', false],
]

const PREDNOSTI: { ime: string; opis: string; pot: string; cilj: string }[] = [
  {
    ime: 'Forma in nasprotniki',
    opis: 'Zadnjih deset tekem in izid proti vsakemu nasprotniku, s katerim si igral.',
    pot: '/moj-profil',
    cilj: 'Profil',
  },
  {
    ime: 'Napoved tekme',
    opis: 'Verjetnost zmage pred dvobojem, izračunana iz ratinga in medsebojnih tekem.',
    pot: '/dvoboj',
    cilj: '1 na 1',
  },
  {
    ime: 'Nizi, točke in razrezi',
    opis: 'Delež dobljenih nizov, tesne končnice in razrez po sistemu, kategoriji in sezoni.',
    pot: '/moj-profil',
    cilj: 'Profil',
  },
  {
    ime: 'Spremljanje lig',
    opis: 'Kola izbranih lig se pokažejo na domači strani — brez iskanja po seznamu.',
    pot: '/lige',
    cilj: 'Lige',
  },
  {
    ime: 'Zasebna statistika',
    opis: 'Podrobnosti vidiš samo ti. Javni profil ostane rezultati in rating.',
    pot: '/moj-profil',
    cilj: 'Profil',
  },
]

const imeCiklusa = (c: CiklusPlacila) => (c === 'LETNO' ? 'Letno' : 'Mesečno')
const enota = (c: CiklusPlacila) => (c === 'LETNO' ? ' / leto' : ' / mesec')
const stevilka = (i: number) => String(i + 1).padStart(2, '0')

const uspehPoVrnitvi = (n: NarocninaDto) =>
  `Stripe je potrdil plačilo ${oblikujCeno(n.cena ?? 0)}. Premium je vklopljen.`

/* Nalaga naročnino in obravnava vrnitev s Stripe Checkouta (useVrnitevSPlacila);
   sam pogled je ločen, da ima vedno podatke (brez preverjanj na vsakem koraku). */
export function NarocninaIgralec() {
  const poizvedba = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  const vrnitevObvestilo = useVrnitevSPlacila({
    caka: 'Plačilo je prejeto. Premium se vklaplja …',
    uspeh: uspehPoVrnitvi,
  })

  if (poizvedba.error) return <NapakaPoizvedbe poizvedba={poizvedba} kaj="naročnine" />
  if (!poizvedba.data) return <p className="obvestilo">Nalagam naročnino …</p>

  return <PogledNarocnine narocnina={poizvedba.data} vrnitevObvestilo={vrnitevObvestilo} />
}

function PogledNarocnine({
  narocnina: n,
  vrnitevObvestilo,
}: {
  narocnina: NarocninaDto
  vrnitevObvestilo: Obvestilo | null
}) {
  const { uporabnik, osvezi } = useAvtentikacija()
  const telefon = useTelefon()
  const odjemalec = useQueryClient()

  const [izbranRocno, nastaviIzbran] = useState<CiklusPlacila | null>(null)
  const [vprasanjePreklic, nastaviVprasanje] = useState(false)
  const [obvestiloRocno, nastaviObvestilo] = useState<Obvestilo | null>(null)
  const [napaka, nastaviNapako] = useState<string | null>(null)

  const jePremium = n.aktivna && !n.preklicana
  const jePreklicana = n.aktivna && n.preklicana
  const imaPremium = n.aktivna
  const jeFree = !imaPremium

  const ciklus: CiklusPlacila = n.ciklus ?? 'MESECNO'
  const preklopNa = jePremium ? n.naslednjiCiklus : null
  const naslednjiCiklus: CiklusPlacila = preklopNa ?? ciklus
  const izbran: CiklusPlacila = izbranRocno ?? preklopNa ?? ciklus

  /* Cenik po pasu: pri aktivni naročnini po pasu ob sklenitvi (kdor je Premium
     sklenil kot U21, ostane v njem), sicer po pasu računa. Cena tekočega
     cikla pa je vedno tista, ki jo Stripe res zaračunava. */
  const starejsi = (imaPremium ? n.starejsiOd21 : uporabnik?.starejsiOd21) ?? false
  const cene: Record<CiklusPlacila, number> = {
    MESECNO: starejsi ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_MESECNO.mlajsi,
    LETNO: starejsi ? CENA_PREMIUM_LETNO.starejsi : CENA_PREMIUM_LETNO.mlajsi,
  }
  if (imaPremium && n.ciklus && n.cena !== null) cene[n.ciklus] = n.cena
  const dvanajstMesecno = cene.MESECNO * 12
  const prihranek = oblikujCeno(dvanajstMesecno - cene.LETNO)

  // ---------- Obdobje ----------

  const konec = oblikujDatum(n.obdobjeDo)
  const imaObdobje = imaPremium && !!n.obdobjeOd && !!n.obdobjeDo
  const dni = Math.max(0, dniMed(danesIso(), n.obdobjeDo) ?? 0)
  const dniObdobja = dniMed(n.obdobjeOd, n.obdobjeDo) ?? 0
  const pretecenih = imaObdobje && dniObdobja > 0
    ? Math.min(SEGMENTOV, Math.max(0, Math.round((1 - dni / dniObdobja) * SEGMENTOV)))
    : 0
  const segmenti = Array.from({ length: SEGMENTOV }, (_, i) => i < pretecenih)

  // ---------- Dejanja ----------

  function poDejanju(podatki: NarocninaDto, obvestilo: Obvestilo | null) {
    nastaviNapako(null)
    nastaviObvestilo(obvestilo)
    odjemalec.setQueryData(['narocnina'], podatki)
    // profil (paketAktiven, meni, dostop do zasebne statistike) se bere iz konteksta
    void osvezi()
  }

  const naNapako = (e: unknown) => nastaviNapako(opisNapake(e))

  const preklopi = useMutation({
    mutationFn: (c: CiklusPlacila) => narocninaApi.preklopi(c),
    onSuccess: (podatki, c) => {
      poDejanju(podatki, {
        besedilo: `Od ${konec} plačuješ ${c === 'LETNO' ? 'letno' : 'mesečno'}, ${oblikujCeno(cene[c])}${enota(c)}.`,
        uspeh: true,
        razveljavi: true,
      })
    },
    onError: naNapako,
  })

  const razveljavi = useMutation({
    mutationFn: () => narocninaApi.razveljaviPreklop(),
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
      poDejanju(podatki, { besedilo: `Naročnina je preklicana. Premium velja do ${konec}.`, uspeh: true })
    },
    onError: naNapako,
  })

  const obnovi = useMutation({
    mutationFn: (c: CiklusPlacila) => narocninaApi.obnovi({ ciklus: c }),
    onSuccess: (podatki) => {
      nastaviIzbran(null)
      poDejanju(podatki, { besedilo: `Naročnina je obnovljena. Naslednja obnova ${konec}.`, uspeh: true })
    },
    onError: naNapako,
  })

  const nadgradi = useMutation({
    mutationFn: () => placilaApi.nadgradnja({ paket: 'PREMIUM', ciklus: izbran }),
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

  const izberi = (c: CiklusPlacila) => () => {
    nastaviIzbran(c)
    nastaviVprasanje(false)
  }

  /* Sporočilo na vrhu telesa: najprej tisto, ki ga je sprožilo dejanje na tej
     strani, nato vrnitev s Stripa, nazadnje zabeležen preklop - ta se mora
     dati umakniti tudi po osvežitvi strani, ne le tik po potrditvi. */
  const obvestilo: Obvestilo | null =
    obvestiloRocno
    ?? vrnitevObvestilo
    ?? (preklopNa
      ? {
          besedilo: `Od ${konec} plačuješ ${preklopNa === 'LETNO' ? 'letno' : 'mesečno'}, ${oblikujCeno(cene[preklopNa])}${enota(preklopNa)}.`,
          uspeh: true,
          razveljavi: true,
        }
      : null)

  const prikaziPreklop = jePremium && !preklopNa && izbran !== ciklus

  // ---------- Besedila ----------

  const imePaketa = OZNAKE_PAKET[imaPremium ? 'PREMIUM' : 'BREZPLACNO']
  const naslov = jeFree ? 'Nadgradi na Premium' : 'Igralec Premium'
  const statusNapis = jeFree ? 'Paket · Free' : jePreklicana ? 'Naročnina · preklicana' : 'Naročnina · aktivna'
  const naslovDesno = jeFree ? 'Preklic kadarkoli' : jePreklicana ? `Velja do ${konec}` : `Obnova ${konec}`
  const trakCena = jeFree
    ? oblikujCeno(cene[izbran]) + enota(izbran)
    : jePreklicana
      ? 'Brez obnove'
      : oblikujCeno(cene[ciklus]) + enota(ciklus)
  const dniNapis = `Še ${dni} ${sklonDni(dni)}${jePreklicana ? ', nato Free' : ''}`
  const nazivStolpca = imaPremium ? 'Premium · vključeno' : 'Premium · kaj dobiš'

  const izbiraVrstice = jeFree
    ? [
        { oznaka: 'Paket', vrednost: 'Free' },
        { oznaka: 'Plačevanje', vrednost: imeCiklusa(izbran) },
        { oznaka: 'Cena', vrednost: oblikujCeno(cene[izbran]) },
      ]
    : [
        { oznaka: 'Paket', vrednost: 'Premium' },
        { oznaka: 'Plačevanje', vrednost: imeCiklusa(ciklus) },
        { oznaka: 'Cena', vrednost: oblikujCeno(cene[ciklus]) },
      ]

  const kolofon: { oznaka: string; vrednost: string; razred?: string }[] = imaPremium
    ? [
        { oznaka: 'Naročen od', vrednost: oblikujDatum(n.narocenOd) || '—' },
        { oznaka: 'Plačevanje', vrednost: `${imeCiklusa(ciklus)} · ${oblikujCeno(cene[ciklus])}` },
        {
          oznaka: 'Trenutno obdobje',
          vrednost: imaObdobje ? `${oblikujDatum(n.obdobjeOd)} – ${konec}` : '—',
        },
        jePreklicana
          ? { oznaka: 'Velja do', vrednost: konec || '—', razred: 'narocnina__vrednost--negativna' }
          : {
              oznaka: 'Naslednja obnova',
              vrednost: `${konec || '—'} · ${oblikujCeno(cene[naslednjiCiklus])}`,
            },
      ]
    : []
  if (preklopNa) {
    kolofon.push({
      oznaka: 'Od obnove',
      vrednost: `${imeCiklusa(preklopNa)} · ${oblikujCeno(cene[preklopNa])}${enota(preklopNa)}`,
      razred: 'registracija__kolofon-vrednost--uspeh',
    })
  }

  const preklopBesedilo =
    izbran === 'LETNO'
      ? `Namesto 12 × ${oblikujCeno(cene.MESECNO)} = ${oblikujCeno(dvanajstMesecno)} plačaš ${oblikujCeno(cene.LETNO)} za celo leto. Mesečno obdobje se izteče ${konec}, letno začne takrat.`
      : `Letno obdobje je plačano do ${konec}. Od takrat plačuješ ${oblikujCeno(cene.MESECNO)} na mesec, preklic je mogoč vsak mesec.`
  const preklopOznaka1 = izbran === 'LETNO' ? `Ob obnovi ${konec}` : `Od ${konec}`
  const preklopOznaka2 =
    izbran === 'LETNO'
      ? `Prihraniš ${prihranek} na leto`
      : `Na mesec · ${oblikujCeno(dvanajstMesecno)} na leto`

  const plackaPojasnilo = jePreklicana
    ? `Ob obnovi izberi plačevanje. Letno je 10 % ceneje: ${oblikujCeno(cene.LETNO)} namesto ${oblikujCeno(dvanajstMesecno)}.`
    : preklopNa
      ? `Preklop je zabeležen. Do ${konec} ostaja ${imeCiklusa(ciklus).toLowerCase()} plačevanje.`
      : ciklus === 'MESECNO'
        ? `Plačuješ mesečno. Letno je 10 % ceneje: ${oblikujCeno(cene.LETNO)} namesto ${oblikujCeno(dvanajstMesecno)}.`
        : `Plačuješ letno, ${prihranek} manj kot 12 mesečnih plačil.`

  const oznakaGumba = (c: CiklusPlacila) =>
    !jePreklicana && c === ciklus ? `${imeCiklusa(c)} · trenutno` : imeCiklusa(c)

  const cenaMlajsi = izbran === 'MESECNO' ? CENA_PREMIUM_MESECNO.mlajsi : CENA_PREMIUM_LETNO.mlajsi
  const cenaStarejsi = izbran === 'MESECNO' ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_LETNO.starejsi
  const enotaCene = izbran === 'MESECNO' ? 'na mesec' : 'na leto'
  const pojasniloCene = starejsi
    ? `Cena od 21. leta naprej; mlajši plačajo ${oblikujCeno(cenaMlajsi)} ${enotaCene}. Preklic kadarkoli.`
    : `Cena do 21. leta; nato ${oblikujCeno(cenaStarejsi)} ${enotaCene}. Preklic kadarkoli.`

  const zasedeno =
    preklopi.isPending || razveljavi.isPending || preklici.isPending || obnovi.isPending

  return (
    <section>
      <div className="narocnina__glava">
        <div className="narocnina__glava-naslov">
          <h1 className="naslov-strani">
            <span className="naslov-strani__glavni narocnina__ime-paketa">{imePaketa}</span>
          </h1>
        </div>

        <div className="narocnina__glava-stanje">
          {imaPremium && (
            <div className={'narocnina__blok' + (jePreklicana ? ' narocnina__blok--preklicano' : '')}>
              <div className="narocnina__blok-vrstica">
                <span className="narocnina__blok-st">{imaObdobje ? dni : '—'}</span>
                <span className="narocnina__blok-oznaka">
                  {jePreklicana ? 'dni Premium še velja' : 'dni do obnove'}
                </span>
              </div>
              <div className="narocnina__blok-noga">
                <span>{naslovDesno}</span>
                <span>{jePreklicana ? 'Brez obnove' : oblikujCeno(cene[naslednjiCiklus])}</span>
              </div>
            </div>
          )}
          {jeFree && (
            <div className="registracija__kolofon narocnina__kolofon--crn">
              {[
                { oznaka: 'Paket', vrednost: 'Free' },
                { oznaka: 'Premium mesečno', vrednost: oblikujCeno(cene.MESECNO) },
                { oznaka: 'Premium letno', vrednost: oblikujCeno(cene.LETNO) },
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
                {FUNKCIJE.map((ime, i) => (
                  <li
                    key={ime}
                    className={
                      'registracija__korak narocnina__korak'
                      + (imaPremium ? ' registracija__korak--opravljen' : '')
                    }
                  >
                    <span className="registracija__korak-st">{imaPremium ? 'OK' : stevilka(i)}</span>
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
                <span className="registracija__korak-napis">{naslovDesno}</span>
              </div>
            )}
            <h2 className="registracija__naslov">
              <span className="registracija__glavni-naslov">{naslov}</span>
            </h2>
            {imaObdobje && (
              <div className="registracija__napredek">
                <div className="registracija__napredek-vrstica narocnina__napredek-vrstica">
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

            {jeFree && (
              <div className="narocnina__nakup">
                <div className="izbirnik">
                  <button
                    type="button"
                    className={'izbirnik__gumb' + (izbran === 'MESECNO' ? ' izbirnik__gumb--aktiven' : '')}
                    onClick={izberi('MESECNO')}
                  >
                    Mesečno
                  </button>
                  <button
                    type="button"
                    className={
                      'izbirnik__gumb izbirnik__gumb--trak' + (izbran === 'LETNO' ? ' izbirnik__gumb--aktiven' : '')
                    }
                    onClick={izberi('LETNO')}
                  >
                    Letno
                    <span className="izbirnik__trak">−10%</span>
                  </button>
                </div>

                <div className="registracija__primerjava">
                  <div className="registracija__primerjava-glava">
                    <span className="registracija__primerjava-oznaka">Kaj dobiš</span>
                    <div className="registracija__stolp narocnina__stolp">
                      <span className="registracija__stolp-ime">Free</span>
                      <span className="registracija__stolp-cena">0 €</span>
                      <span className="registracija__stolp-enota">Vedno</span>
                      <span className="registracija__stolp-oznaka">Trenutno</span>
                    </div>
                    <div className="registracija__stolp registracija__stolp--izbran narocnina__stolp">
                      <span className="registracija__stolp-ime">Premium</span>
                      <span className="registracija__stolp-cena">{oblikujCeno(cene[izbran])}</span>
                      <span className="registracija__stolp-enota">{izbran === 'MESECNO' ? 'Na mesec' : 'Na leto'}</span>
                      <span className="registracija__stolp-oznaka registracija__stolp-oznaka--izbran">Izbrano</span>
                    </div>
                  </div>
                  {PRIMERJAVA.map(([funkcija, brez]) => (
                    <div className="registracija__primerjava-vrstica" key={funkcija}>
                      <span className="registracija__primerjava-funkcija">{funkcija}</span>
                      <span
                        className={
                          'registracija__primerjava-vrednost'
                          + (brez ? ' registracija__primerjava-vrednost--da' : ' registracija__primerjava-vrednost--ne')
                        }
                      >
                        {brez ? 'DA' : '—'}
                      </span>
                      <span
                        className={
                          'registracija__primerjava-vrednost registracija__primerjava-vrednost--stolpec'
                          + ' registracija__primerjava-vrednost--da'
                        }
                      >
                        DA
                      </span>
                    </div>
                  ))}
                </div>

                <p className="registracija__pojasnilo">{pojasniloCene}</p>
              </div>
            )}

            {imaPremium && (
              <div className="narocnina__sklopi">
                <div className="registracija__kolofon registracija__kolofon--rob-zgoraj narocnina__kolofon--obdobje">
                  {kolofon.map((v) => (
                    <div className="registracija__kolofon-vrstica" key={v.oznaka}>
                      <span className="registracija__kolofon-oznaka">{v.oznaka}</span>
                      <span className={'registracija__kolofon-vrednost' + (v.razred ? ` ${v.razred}` : '')}>
                        {v.vrednost}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="narocnina__sklop">
                  <span className="registracija__zapis-glava">
                    {jePreklicana ? 'Plačevanje ob obnovi' : 'Spremeni plačevanje'}
                  </span>
                  <div className="izbirnik">
                    <button
                      type="button"
                      className={'izbirnik__gumb' + (izbran === 'MESECNO' ? ' izbirnik__gumb--aktiven' : '')}
                      onClick={izberi('MESECNO')}
                    >
                      {oznakaGumba('MESECNO')}
                    </button>
                    <button
                      type="button"
                      className={
                        'izbirnik__gumb izbirnik__gumb--trak'
                        + (izbran === 'LETNO' ? ' izbirnik__gumb--aktiven' : '')
                      }
                      onClick={izberi('LETNO')}
                    >
                      {oznakaGumba('LETNO')}
                      <span className="izbirnik__trak">−10%</span>
                    </button>
                  </div>

                  {prikaziPreklop ? (
                    <div className="narocnina__preklop">
                      <p className="registracija__uvod">{preklopBesedilo}</p>
                      <div className="registracija__znesek narocnina__znesek">
                        <span className="registracija__znesek-oznake">
                          <span className="registracija__znesek-oznaka">{preklopOznaka1}</span>
                          <span
                            className={
                              'registracija__znesek-oznaka'
                              + (izbran === 'LETNO' ? ' narocnina__znesek-prihranek' : '')
                            }
                          >
                            {preklopOznaka2}
                          </span>
                        </span>
                        <span className="registracija__znesek-st">{oblikujCeno(cene[izbran])}</span>
                      </div>
                      <div className="narocnina__dejanja">
                        <button
                          type="button"
                          className="gumb gumb--glavni narocnina__dejanje narocnina__dejanje--glavno"
                          disabled={zasedeno}
                          onClick={() => preklopi.mutate(izbran)}
                        >
                          {izbran === 'LETNO' ? 'Preklopi na letno' : 'Preklopi na mesečno'}
                        </button>
                        <button
                          type="button"
                          className="gumb narocnina__dejanje narocnina__dejanje--drugo"
                          onClick={() => nastaviIzbran(ciklus)}
                        >
                          {izbran === 'LETNO' ? 'Ostani pri mesečnem' : 'Ostani pri letnem'}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="registracija__pojasnilo narocnina__pojasnilo">{plackaPojasnilo}</p>
                  )}
                </div>

                {jePremium && (
                  <div className="narocnina__sklop">
                    <span className="registracija__zapis-glava">Preklic</span>
                    <p className="narocnina__besedilo">
                      Premium ostane do konca plačanega obdobja, {konec}. Potem račun preide na Free; rating,
                      zgodovina tekem in profil ostanejo.
                    </p>
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
                          Prekličem Premium?
                        </span>
                        <span className="narocnina__potrditev-besedilo">
                          Obnove {konec} ne bo. Statistiko profila in spremljanje lig izgubiš čez {dni}{' '}
                          {sklonDni(dni)}. Naročnino lahko do takrat obnoviš brez plačila.
                        </span>
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
                            Obdrži Premium
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {jePreklicana && (
                  <div className="narocnina__sklop">
                    <span className="registracija__zapis-glava">Obnova</span>
                    <p className="narocnina__besedilo">
                      Obnova ne stane nič do {konec}. Takrat se obdobje nadaljuje z izbranim plačevanjem.
                    </p>
                    <div className="narocnina__dejanja">
                      <button
                        type="button"
                        className="gumb gumb--glavni narocnina__dejanje narocnina__dejanje--obnova"
                        disabled={zasedeno}
                        onClick={() => obnovi.mutate(izbran)}
                      >
                        {`Obnovi · ${oblikujCeno(cene[izbran])}${enota(izbran)}`}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="registracija__noga">
            {/* Nakup je odločitev: pogoji naročnine (obnova, preklic, odstop)
                morajo biti en klik stran. */}
            <span className="registracija__placilo-pripis">
              Plačilo prek Stripe · <Link to="/pogoji#pogoji">Pogoji</Link>
            </span>
            {jeFree ? (
              <button
                type="button"
                className="gumb gumb--glavni narocnina__dejanje"
                disabled={nadgradi.isPending}
                onClick={() => nadgradi.mutate()}
              >
                {nadgradi.isPending ? 'Preusmerjam na plačilo …' : `Nadgradi na Premium · ${oblikujCeno(cene[izbran])}`}
              </button>
            ) : (
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
          <h2 className="narocnina__prednosti-naslov">Kaj dobiš s Premium</h2>
          <span className="narocnina__prednosti-meta">
            {imaPremium ? `${PREDNOSTI.length} od ${PREDNOSTI.length} vključeno` : `Od ${oblikujCeno(cene.MESECNO)} / mesec`}
          </span>
        </div>
        <div className="narocnina__prednosti-seznam">
          {PREDNOSTI.map((p, i) => (
            <div className="narocnina__prednost" key={p.ime}>
              <span className={'narocnina__prednost-st' + (imaPremium ? ' narocnina__prednost-st--vkljuceno' : '')}>
                {stevilka(i)}
              </span>
              <div className="narocnina__prednost-besedilo">
                <span className="narocnina__prednost-ime">{p.ime}</span>
                <span className="narocnina__prednost-opis">{p.opis}</span>
              </div>
              {imaPremium ? (
                <Link to={p.pot} className="narocnina__prednost-povezava">
                  {p.cilj} →
                </Link>
              ) : (
                <span className="narocnina__prednost-povezava narocnina__prednost-povezava--oznaka">Premium</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
