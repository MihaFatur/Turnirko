/* Natisljivi listki (zapisniki) tekem enega turnirskega dogodka.

   Sodniki jih izpolnijo z roko za mizo; izid se prebere s fotografije listka
   (branje listkov) ali ga organizator vnese prek običajnega vnosa. Listek je POGLED na podatke, ki jih dogodek že
   ima; edino, kar Turnirko pomni zraven, je sled tiska (tekma.listekNatisnjenOb):
   organizator listke natisne že med igro, rezultat pa se vnese šele pozneje,
   zato so natisnjeni listki označeni in jih naslednji tisk preskoči. Tiska
   brskalnik (window.print). Stran je namenoma zunaj skupne postavitve (brez
   navigacije), da je natis čist.

   Zaslon je delovna ploskev (prenova »Listki 1b«, 23. 11. 2026, komponenta ListkiPloskev):
   tabela skupina × kolo, v njej vsak listek ploščica, ki jo organizator izbere s klikom.
   Velikega predogleda na zaslonu ni več: listi A4 se izrišejo samo za tisk (@media print),
   zato na zaslonu ni ničesar, kar bi bilo videti kot natis. Organizator izbere, kaj natisniti:
   - Tekme: privzeto kolo (ali vrstica izločilnega dela), ki je na vrsti; izbira se zoži na
     skupino ali posamezen listek ali razširi na vse nenatisnjene. Listek imajo pripravljene
     tekme: znana sta oba igralca in še niso odigrane (PRIPRAVLJENA ali V_IGRI). Prosti
     prehodi in še nedoločene tekme odpadejo sami, ker nimajo obeh udeležencev.
   - Prazni listki: izbrano število listkov brez imen igralcev, da jih sodnik dopiše z roko
     (turnir in kategorija sta natisnjena). Gredo v isti tisk za pravimi listki; zadnji list se
     po želji dopolni z njimi do polnega lista.

   Listki so zloženi na liste A4 po šest (2 × 3, brez robov): vsak list je
   natančno 210 × 297 mm, listki 105 × 99 mm, zato ga režejo samo trije rezi po
   črtkanih črtah. Zadnji list se dopolni s praznimi mesti, da so rezi povsod
   enaki.

   QR koda na listku nosi skrivnost (S1), ki sodniku da pravico poslati izid te
   tekme s svojim telefonom. Kode s skrivnostjo da strežnik samo organizatorju
   tega turnirja in adminu; gost natisne listek z zasilno kodo brez skrivnosti
   (prebere jo pult, sodniška pot pa ne). Ko sodniki slikajo listke (stikalo na
   strani Branje listkov), sledi vsakemu listu njegova hrbtna stran s kodo do
   strani za sodnika (/s): tiska se obojestransko, organizator jo lahko izklopi.

   Ekipni dogodek ima namesto listkov uradni ekipni zapisnik za vsako tekmo
   (ZapisnikiEkipnegaDogodka): iste pripravljene tekme, v istem vrstnem redu,
   z isto izbiro in sledjo tiska, le da je vsaka srečanje s postavo in mrežo
   posamičnih tekem. */
import { Fragment, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import { imeUdelezenca, type DogodekDto, type MrezaDto, type SlikaDto } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import {
  LISTKOV_NA_LIST,
  imaVecSkupin,
  praznihZaTisk,
  preberiRazvrstitev,
  pripraviListke,
  shraniRazvrstitev,
  type PripravljenListek,
  type RazvrstitevListkov,
} from '../pomozno/listki'
import { useBranjeListkovNaVoljo } from '../pomozno/useBranjeListkov'
import { oznakaKategorije } from '../pomozno/zrebSkupin'
import { useIzbiraListkov } from '../komponente/IzbiraListkov'
import { HrbetListka, Listek, type ListekPodatki } from '../komponente/Listek'
import { ListkiPloskev } from '../komponente/ListkiPloskev'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZapisnikiEkipnegaDogodka } from '../komponente/ZapisnikiEkipnegaDogodka'

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

  /* Kode QR s skrivnostjo (S1). Ključ so id-ji pripravljenih tekem: ko se nabor
     spremeni (žreb, nova tekma), se kode naložijo znova. Podatki se uporabijo SAMO,
     dokler sme uporabnik urejati - onemogočena poizvedba bi sicer vrnila kode iz
     predpomnilnika tudi gostu, ki odpre stran po odjavi v istem zavihku (odjava
     jih tudi odstrani, AvtentikacijaKontekst). */
  const ekipno = mreza.data?.dogodek.disciplina === 'EKIPNO'
  const idjiListkov = useMemo(
    () => pripraviListke(mreza.data).map((l) => l.tekma.id).join(','),
    [mreza.data],
  )
  const kodePoizvedba = useQuery({
    queryKey: ['kode-listkov', idDogodka, idjiListkov],
    queryFn: () => dogodkiApi.kodeListkov(idDogodka),
    enabled: smemOznacevati && !ekipno,
  })
  const branjeNaVoljo = useBranjeListkovNaVoljo()

  if (mreza.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (mreza.error) return <SporociloNapake napaka={mreza.error} />
  if (!mreza.data) return null

  return (
    <ListkiPogled
      idDogodka={idDogodka}
      mreza={mreza.data}
      imeTurnirja={turnir.data?.ime ?? null}
      logotipTurnirja={turnir.data?.logotip ?? null}
      smemOznacevati={smemOznacevati}
      kode={smemOznacevati ? kodePoizvedba.data?.kode : undefined}
      /* Sodniki slikajo listke (S2): list dobi hrbtno stran s kodo do /s. */
      sodnikiSlikajo={smemOznacevati && !!kodePoizvedba.data?.sodnikiListki}
      kodeNalaganje={kodePoizvedba.isLoading}
      kodeNapaka={!!kodePoizvedba.error}
      branjeNaVoljo={branjeNaVoljo}
      /* Prijavljen ne-admin: dokler turnir ni naložen, se ne ve, ali sme dobiti
         kode s skrivnostjo - tisk bi sicer šel z zasilnimi kodami brez opozorila. */
      cakaTurnir={!!uporabnik && !jeAdmin && turnir.isPending}
    />
  )
}

interface PogledLastnosti {
  idDogodka: number
  mreza: MrezaDto
  imeTurnirja: string | null
  logotipTurnirja: SlikaDto | null
  smemOznacevati: boolean
  /* Koda QR po id-ju tekme; undefined, kadar je uporabnik ne sme dobiti (gost). */
  kode: Record<string, string> | undefined
  sodnikiSlikajo: boolean
  kodeNalaganje: boolean
  kodeNapaka: boolean
  branjeNaVoljo: boolean
  cakaTurnir: boolean
}

/* Vse, kar stran zna brez strežnika: izbira, prazni listki, listi za tisk. Ločeno od
   ListkiStran, da ga razvojni harness (dev/PrenovaZrebaPosnetki) izriše s stalnimi podatki. */
export function ListkiPogled({
  idDogodka,
  mreza,
  imeTurnirja,
  logotipTurnirja,
  smemOznacevati,
  kode,
  sodnikiSlikajo,
  kodeNalaganje,
  kodeNapaka,
  branjeNaVoljo,
  cakaTurnir,
}: PogledLastnosti) {
  const dogodek = mreza.dogodek
  const ekipno = dogodek.disciplina === 'EKIPNO'

  /* Skupinski del po skupinah ali po kolih (lastnik 23. 11. 2026); zadnja izbira
     ostane v brskalniku. */
  const [razvrstitev, nastaviRazvrstitevStanje] = useState<RazvrstitevListkov>(preberiRazvrstitev)
  const nastaviRazvrstitev = (r: RazvrstitevListkov) => {
    nastaviRazvrstitevStanje(r)
    shraniRazvrstitev(r)
  }
  /* Ekipna tekma brez srečanja (npr. prenesen izid finalne skupine) zapisnika
     nima, zato odpade. */
  const listki = useMemo(() => {
    const vsi = pripraviListke(mreza, razvrstitev)
    return ekipno ? vsi.filter((l) => l.tekma.idSrecanje !== null) : vsi
  }, [mreza, ekipno, razvrstitev])
  const izbiraRazvrstitve = imaVecSkupin(listki)
    ? { vrednost: razvrstitev, nastavi: nastaviRazvrstitev }
    : undefined

  const izbira = useIzbiraListkov(idDogodka, listki, smemOznacevati)

  const [hrbet, nastaviHrbet] = useState(true)
  /* Prazni listki gredo v isti tisk za pravimi; po potrjenem tisku se vrnejo na nič. */
  const [prazni, nastaviPrazne] = useState(0)
  const [dopolni, nastaviDopolni] = useState(false)

  const nadnaslov = [imeTurnirja, dogodek.ime].filter(Boolean).join(' · ')
  const povezavaNazaj = `/dogodki/${idDogodka}`

  /* Ekipna tekma nima listka, ampak zapisnik srečanja. */
  if (ekipno) {
    return (
      <ZapisnikiEkipnegaDogodka
        dogodek={dogodek}
        nadnaslov={nadnaslov}
        povezavaNazaj={povezavaNazaj}
        logotipTurnirja={logotipTurnirja}
        listki={listki}
        izbira={izbira}
        razvrstitev={izbiraRazvrstitve}
      />
    )
  }

  const izbranih = izbira.izbraniListki.length
  const praznih = praznihZaTisk(izbranih, prazni, dopolni)
  /* Izbran listek brez kode s skrivnostjo, čeprav jo organizator sme dobiti. */
  const manjkaKoda = !!kode && izbira.izbraniListki.some((l) => !kode[String(l.tekma.id)])
  /* Prazen listek tekme še nima, zato sodnik z njim izida ne more poslati; hrbet pa dobi vsak
     list, sicer bi obojestranski tisk list za listom zamaknil hrbte. */
  const zHrbtom = izbranih > 0 && sodnikiSlikajo && hrbet
  const naslovSodnika = `${window.location.origin}/s`

  /* Listki izbranih tekem (v vrstnem redu tiska), nato prazni. */
  const listkiZaTisk: ListekZaTisk[] = [
    ...izbira.izbraniListki.map((l) => ({
      kljuc: `tekma-${l.tekma.id}`,
      podatki: podatkiListka(l, imeTurnirja, dogodek, kode?.[String(l.tekma.id)] ?? null),
    })),
    ...Array.from({ length: praznih }, (_, i) => ({
      kljuc: `prazen-${i}`,
      /* Dvojica ima vsakega igralca v svoji vrstici, zato prazen listek dvojic dobi dve. */
      podatki: prazenListek(imeTurnirja, dogodek),
    })),
  ]
  const listi = razdeli(listkiZaTisk, LISTKOV_NA_LIST)

  const natisni = () => {
    if (izbranih > 0) izbira.natisni()
    else window.print()
  }
  const potrdi = (natisnil: boolean) => {
    izbira.potrdi(natisnil)
    if (natisnil) {
      nastaviPrazne(0)
      nastaviDopolni(false)
    }
  }

  return (
    <>
      <ListkiPloskev
        vrsta="listek"
        nadnaslov={nadnaslov}
        naslov="Listki za tiskanje"
        povezavaNazaj={povezavaNazaj}
        listki={listki}
        izbira={izbira}
        razvrstitev={izbiraRazvrstitve}
        prazni={{
          stevilo: prazni,
          nastavi: nastaviPrazne,
          dopolni,
          preklopiDopolni: () => nastaviDopolni(!dopolni),
        }}
        hrbet={sodnikiSlikajo ? { vklopljen: hrbet, preklopi: () => nastaviHrbet(!hrbet) } : undefined}
        opozorilo={
          (kodeNapaka || manjkaKoda) && izbranih > 0 ? (
            <p className="obvestilo obvestilo--opozorilo">
              Kod za sodnike ni bilo mogoče naložiti. Listke lahko natisneš, a izid z njih bo
              mogoče poslati samo s fotografijo na pultu, ne s telefonom sodnika.
            </p>
          ) : undefined
        }
        opomba={
          !sodnikiSlikajo && branjeNaVoljo && !!kode ? (
            <p className="listki-ploskev__opomba">
              Ko na strani Branje listkov vklopiš »Sodniki slikajo listke«, dobi vsak list na
              hrbtni strani kodo, s katero sodnik po tekmi odpre stran za pošiljanje izida.
            </p>
          ) : undefined
        }
        navodilo={zHrbtom ? 'A4 obojestransko · 100 % · brez robov' : 'A4 · 100 % · brez robov'}
        /* Samo pravi listki čakajo na kode; prazni so brez njih. */
        tiskCaka={izbranih > 0 && (kodeNalaganje || cakaTurnir)}
        onNatisni={natisni}
        onPotrdi={potrdi}
      />

      {/* Listi A4 so samo za tisk (@media print); na zaslonu jih ni. */}
      <div className="listki-stran listki-stran--listki">
        <div className="listki-listi">
          {listi.map((list, i) => (
            <Fragment key={i}>
              <section className="listki-list" aria-label={`List ${i + 1} od ${listi.length}`}>
                {list.map((l) => (
                  <Listek key={l.kljuc} podatki={l.podatki} />
                ))}
                {/* Prazna mesta na zadnjem listu: rezi so povsod isti. */}
                {Array.from({ length: LISTKOV_NA_LIST - list.length }, (_, j) => (
                  <div key={`prazno-${j}`} className="listek listek--prazen" aria-hidden="true" />
                ))}
              </section>
              {/* Hrbtna stran takoj za svojim listom: obojestranski tisk jo da na hrbet.
                  Koda je na vseh šestih mestih tudi za nepolnim listom: kako tiskalnik
                  list obrne, ni znano vnaprej (lastnik 23. 11. 2026: pri enem listku je
                  koda pristala za praznim mestom, listek je ostal brez hrbta). Kode za
                  praznimi mesti gredo z odrezki. */}
              {zHrbtom && (
                <section className="listki-list listki-list--hrbet" aria-label={`Hrbtna stran lista ${i + 1}`}>
                  {Array.from({ length: LISTKOV_NA_LIST }, (_, j) => (
                    <HrbetListka key={j} naslov={naslovSodnika} />
                  ))}
                </section>
              )}
            </Fragment>
          ))}
        </div>
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

/* Prazen listek: turnir in kategorija sta natisnjena, vse ostalo sodnik dopiše z roko. */
function prazenListek(imeTurnirja: string | null, dogodek: DogodekDto): ListekPodatki {
  const ime = dogodek.disciplina === 'DVOJICE' ? ' / ' : ''
  return {
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
  }
}
