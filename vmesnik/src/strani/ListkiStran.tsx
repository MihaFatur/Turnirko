/* Natisljivi listki (zapisniki) tekem enega turnirskega dogodka.

   Sodniki jih izpolnijo z roko za mizo; izid se prebere s fotografije listka
   (branje listkov) ali ga organizator vnese prek običajnega vnosa. Listek je POGLED na podatke, ki jih dogodek že
   ima; edino, kar Turnirko pomni zraven, je sled tiska (tekma.listekNatisnjenOb):
   organizator listke natisne že med igro, rezultat pa se vnese šele pozneje,
   zato so natisnjeni listki označeni in jih naslednji tisk preskoči. Tiska
   brskalnik (window.print). Stran je namenoma zunaj skupne postavitve (brez
   navigacije), da je natis čist.

   Organizator izbere, kaj natisniti:
   - Tekme: privzeto vsi še nenatisnjeni; izbira se zoži na sklop (skupina,
     tolažilni žreb ...) ali dopolni s kljukicami za posamezne tekme. Listek
     imajo pripravljene tekme: znana sta oba igralca in še niso odigrane
     (PRIPRAVLJENA ali V_IGRI). Prosti prehodi in še nedoločene tekme odpadejo
     sami, ker nimajo obeh udeležencev.
   - Prazni listki: izbrano število listkov brez imen igralcev, da jih sodnik
     dopiše z roko (turnir in kategorija sta natisnjena).

   Listki so zloženi na liste A4 po šest (2 × 3, brez robov): vsak list je
   natančno 210 × 297 mm, listki 105 × 99 mm, zato ga režejo samo trije rezi po
   črtkanih črtah. Zadnji list se dopolni s praznimi mesti, da so rezi povsod
   enaki.

   QR koda na listku nosi skrivnost (S1), ki sodniku da pravico poslati izid te
   tekme s svojim telefonom. Kode s skrivnostjo da strežnik samo organizatorju
   tega turnirja in adminu; gost natisne listek z zasilno kodo brez skrivnosti
   (prebere jo pult, sodniška pot pa ne).

   Ekipni dogodek ima namesto listkov uradni ekipni zapisnik za vsako tekmo
   (ZapisnikiEkipnegaDogodka): iste pripravljene tekme, v istem vrstnem redu,
   z isto izbiro in sledjo tiska, le da je vsaka srečanje s postavo in mrežo
   posamičnih tekem. */
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import { OZNAKE_SPOL_KATEGORIJA, imeUdelezenca, type DogodekDto } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  LISTKOV_NA_LIST,
  NAJVEC_PRAZNIH_LISTKOV,
  pripraviListke,
  sklopiListkov,
  steviloPraznih,
  type PripravljenListek,
} from '../pomozno/listki'
import { sklonListkov } from '../pomozno/oblikovanje'
import { oznakaKategorije } from '../pomozno/zrebSkupin'
import { IzbiraListkov, PotrditevTiska, useIzbiraListkov } from '../komponente/IzbiraListkov'
import { Listek, type ListekPodatki } from '../komponente/Listek'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZapisnikiEkipnegaDogodka } from '../komponente/ZapisnikiEkipnegaDogodka'

/* Širina lista A4 na zaslonu (210 mm pri 96 dpi): ožji zaslon ga pomanjša, da
   je cel list viden in se ne drsi vodoravno. */
const SIRINA_LISTA_PX = (210 / 25.4) * 96

type Nacin = 'tekme' | 'prazni'

export function ListkiStran() {
  const { id } = useParams()
  const idDogodka = Number(id)
  const { jeAdmin, smemUrejati, uporabnik } = useAvtentikacija()

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja za glavo natisa; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })

  const ekipno = mreza.data?.dogodek.disciplina === 'EKIPNO'
  /* Ekipna tekma brez srečanja (npr. prenesen izid finalne skupine) zapisnika
     nima, zato odpade. */
  const listki = useMemo(() => {
    const vsi = pripraviListke(mreza.data)
    return ekipno ? vsi.filter((l) => l.tekma.idSrecanje !== null) : vsi
  }, [mreza.data, ekipno])
  const sklopi = useMemo(() => sklopiListkov(listki), [listki])

  /* Sled tiska smeta pisati organizator tega turnirja in admin; uvoženo
     tekmovanje je samo za branje, gost pa listke samo natisne. */
  const uvozen = mreza.data?.dogodek.vir != null
  const smemOznacevati =
    !uvozen &&
    (jeAdmin ||
      (!!turnir.data &&
        smemUrejati(turnir.data.idLastnik, turnir.data.idKlubLastnik, {
          vrsta: 'turnir',
          id: turnir.data.id,
        })))
  const izbira = useIzbiraListkov(idDogodka, listki, smemOznacevati)

  /* Kode QR s skrivnostjo (S1). Ključ so id-ji pripravljenih tekem: ko se nabor
     spremeni (žreb, nova tekma), se kode naložijo znova. Podatki se uporabijo SAMO,
     dokler sme uporabnik urejati - onemogočena poizvedba bi sicer vrnila kode iz
     predpomnilnika tudi gostu, ki odpre stran po odjavi v istem zavihku (odjava
     jih tudi odstrani, AvtentikacijaKontekst). */
  const idjiListkov = useMemo(() => listki.map((l) => l.tekma.id).join(','), [listki])
  const kodePoizvedba = useQuery({
    queryKey: ['kode-listkov', idDogodka, idjiListkov],
    queryFn: () => dogodkiApi.kodeListkov(idDogodka),
    enabled: smemOznacevati && !ekipno,
  })
  const kode = smemOznacevati ? kodePoizvedba.data : undefined

  const [nacin, nastaviNacin] = useState<Nacin>('tekme')
  const [vnosPraznih, nastaviVnosPraznih] = useState(String(LISTKOV_NA_LIST))
  const praznih = steviloPraznih(vnosPraznih)

  /* Ožji zaslon list pomanjša (CSS zoom); pri tisku se pomanjšanje izključi. */
  const [okvir, nastaviOkvir] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)
  useEffect(() => {
    if (!okvir) return
    const izmeri = () => nastaviMerilo(Math.min(1, okvir.clientWidth / SIRINA_LISTA_PX))
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(okvir)
    return () => opazovalec.disconnect()
  }, [okvir])

  const dogodek = mreza.data?.dogodek
  const imeTurnirja = turnir.data?.ime ?? null

  /* Listki izbranih tekem (v vrstnem redu tiska) in prazni listki. */
  const listkiTekem = useMemo<ListekZaTisk[]>(
    () =>
      dogodek
        ? izbira.izbraniListki.map((l) => ({
            kljuc: `tekma-${l.tekma.id}`,
            podatki: podatkiListka(l, imeTurnirja, dogodek, kode?.[String(l.tekma.id)] ?? null),
          }))
        : [],
    [dogodek, izbira.izbraniListki, imeTurnirja, kode],
  )
  const prazniListki = useMemo<ListekZaTisk[]>(() => {
    if (!dogodek) return []
    /* Dvojica ima vsakega igralca v svoji vrstici, zato prazen listek dvojic dobi dve. */
    const ime = dogodek.disciplina === 'DVOJICE' ? ' / ' : ''
    return Array.from({ length: praznih }, (_, i) => ({
      kljuc: `prazen-${i}`,
      podatki: {
        dogodek: imeTurnirja,
        kategorija: oznakaKategorije(dogodek),
        skupina: null,
        faza: '',
        steviloNizov: dogodek.privzetoSteviloNizov,
        miza: null,
        idTekme: null,
        stran1: { ime },
        stran2: { ime },
        prazno: true,
      },
    }))
  }, [dogodek, imeTurnirja, praznih])

  if (mreza.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (mreza.error) return <SporociloNapake napaka={mreza.error} />
  if (!dogodek) return null

  /* Ekipna tekma nima listka, ampak zapisnik srečanja. */
  if (ekipno) {
    return (
      <ZapisnikiEkipnegaDogodka
        dogodek={dogodek}
        imeTurnirja={imeTurnirja}
        logotipTurnirja={turnir.data?.logotip ?? null}
        sklopi={sklopi}
        izbira={izbira}
      />
    )
  }

  const praznoNacin = nacin === 'prazni'
  /* Izbran listek brez kode s skrivnostjo, čeprav jo organizator sme dobiti. */
  const manjkaKoda = !!kode && izbira.izbraniListki.some((l) => !kode[String(l.tekma.id)])
  const steviloZaTisk = praznoNacin ? praznih : izbira.izbraniListki.length
  const listi = razdeli(praznoNacin ? prazniListki : listkiTekem, LISTKOV_NA_LIST)

  const natisni = () => {
    if (praznoNacin) window.print()
    else izbira.natisni()
  }

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/dogodki/${idDogodka}`} className="povezava-nazaj">
          ← Nazaj na dogodek
        </Link>
        <span className="listki-orodja__stevec">
          {steviloZaTisk} {sklonListkov(steviloZaTisk)} za tisk
        </span>
        <button
          className="gumb gumb--glavni"
          /* Prijavljen ne-admin: dokler turnir ni naložen, se ne ve, ali sme dobiti
             kode s skrivnostjo - tisk bi sicer šel z zasilnimi kodami brez opozorila. */
          disabled={
            steviloZaTisk === 0 ||
            (!praznoNacin && (kodePoizvedba.isLoading || (!!uporabnik && !jeAdmin && turnir.isPending)))
          }
          onClick={natisni}
        >
          {praznoNacin ? 'Natisni prazne' : 'Natisni izbrane'} ({steviloZaTisk})
        </button>
        {!praznoNacin && <PotrditevTiska izbira={izbira} vrsta="listek" />}
      </div>
      {!praznoNacin && (kodePoizvedba.error || manjkaKoda) && (
        <p className="obvestilo obvestilo--opozorilo zaslon-samo">
          Kod za sodnike ni bilo mogoče naložiti. Listke lahko natisneš, a izid z njih bo
          mogoče poslati samo s fotografijo na pultu, ne s telefonom sodnika.
        </p>
      )}

      <div className="listki-stran listki-stran--listki">
        <header className="listki-naslov zaslon-samo">
          <h1>
            {turnir.data ? `${turnir.data.ime} — ` : ''}
            {dogodek.ime}
          </h1>
          <p>
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
          </p>
          <p className="listki-naslov__navodilo">
            Natisni na A4 v merilu 100 %, brez robov in brez glave ter noge. Vsak list ima šest
            listkov; razreži ga po treh črtkanih črtah (ena navpična, dve vodoravni).
          </p>
        </header>

        <div className="izbirnik listki-nacin zaslon-samo" role="group" aria-label="Vrsta listkov">
          <button
            type="button"
            className={'izbirnik__gumb' + (!praznoNacin ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={!praznoNacin}
            onClick={() => nastaviNacin('tekme')}
          >
            Listki tekem
          </button>
          <button
            type="button"
            className={'izbirnik__gumb' + (praznoNacin ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={praznoNacin}
            onClick={() => nastaviNacin('prazni')}
          >
            Prazni listki
          </button>
        </div>

        {praznoNacin ? (
          <PraznoPolje vnos={vnosPraznih} onVnos={nastaviVnosPraznih} stevilo={praznih} />
        ) : listki.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih tekem za tiskanje. Listek se ustvari za tekmo, kjer
            sta znana oba igralca in še ni odigrana.
          </p>
        ) : (
          <IzbiraListkov sklopi={sklopi} izbira={izbira} vrsta="listek" />
        )}

        {listi.length > 0 && (
          <>
            <h2 className="listki-predogled zaslon-samo">Predogled natisa</h2>
            <div className="listki-listi" ref={nastaviOkvir}>
              {listi.map((list, i) => (
                <section
                  key={i}
                  className="listki-list"
                  aria-label={`List ${i + 1} od ${listi.length}`}
                  style={merilo < 1 ? { zoom: merilo } : undefined}
                >
                  {list.map((l) => (
                    <Listek key={l.kljuc} podatki={l.podatki} />
                  ))}
                  {/* Prazna mesta na zadnjem listu: rezi so povsod isti. */}
                  {Array.from({ length: LISTKOV_NA_LIST - list.length }, (_, j) => (
                    <div key={`prazno-${j}`} className="listek listek--prazen" aria-hidden="true" />
                  ))}
                </section>
              ))}
            </div>
          </>
        )}
      </div>
    </>
  )
}

/* Listek, pripravljen za izris, s stabilnim ključem. */
interface ListekZaTisk {
  kljuc: string
  podatki: ListekPodatki
}

/* Razdeli na dele po `velikost` (list A4 nosi po šest listkov). */
function razdeli<T>(seznam: T[], velikost: number): T[][] {
  const deli: T[][] = []
  for (let i = 0; i < seznam.length; i += velikost) deli.push(seznam.slice(i, i + velikost))
  return deli
}

/* Podatki listka za pripravljeno tekmo. */
function podatkiListka(
  l: PripravljenListek,
  imeTurnirja: string | null,
  dogodek: DogodekDto,
  kodaQr: string | null,
): ListekPodatki {
  return {
    dogodek: imeTurnirja,
    kategorija: oznakaKategorije(dogodek),
    skupina: l.skupina,
    faza: l.faza,
    steviloNizov: l.tekma.steviloNizov,
    miza: l.tekma.miza,
    idTekme: l.tekma.id,
    kodaQr,
    stran1: { ime: imeUdelezenca(l.tekma.udelezenec1) ?? '—' },
    stran2: { ime: imeUdelezenca(l.tekma.udelezenec2) ?? '—' },
  }
}

/* Izbira števila praznih listkov. */
function PraznoPolje({
  vnos,
  onVnos,
  stevilo,
}: {
  vnos: string
  onVnos: (v: string) => void
  stevilo: number
}) {
  const listov = Math.ceil(stevilo / LISTKOV_NA_LIST)
  return (
    <section className="listki-izbira zaslon-samo" aria-label="Prazni listki">
      <h2 className="listki-izbira__naslov">Prazni listki</h2>
      <p className="listki-izbira__pojasnilo">
        Natisnjena sta turnir in kategorija. Imena igralcev, skupino ali fazo in mizo sodnik dopiše
        z roko. Prazni listki se ne vpišejo kot natisnjeni.
      </p>
      <div className="listki-izbira__prazni">
        <label className="obrazec__polje">
          Število listkov
          <input
            className="vnos--ozek"
            type="number"
            inputMode="numeric"
            min={1}
            max={NAJVEC_PRAZNIH_LISTKOV}
            value={vnos}
            onChange={(d) => onVnos(d.target.value)}
          />
        </label>
        <div className="izbirnik" role="group" aria-label="Hitra izbira števila">
          {[6, 12, 24].map((n) => (
            <button
              key={n}
              type="button"
              className={'izbirnik__gumb' + (stevilo === n ? ' izbirnik__gumb--aktiven' : '')}
              aria-pressed={stevilo === n}
              onClick={() => onVnos(String(n))}
            >
              {n}
            </button>
          ))}
        </div>
      </div>
      <p className="listki-izbira__povzetek">
        {stevilo} {sklonListkov(stevilo)} · {listov} {listov === 1 ? 'list' : listov === 2 ? 'lista' : 'listov'} A4
      </p>
    </section>
  )
}
