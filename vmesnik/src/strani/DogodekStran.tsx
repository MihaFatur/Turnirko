/* Stran enega dogodka - osrednji delovni prostor.

   V pripravi (admin): urejanje prijav in izvedba žreba; gost vidi le seznam.
   Povsod, kjer žreb pozna nosilce (vse razen krožnega sistema in dvojic), je
   v pripravi še urejanje jakostnega vrstnega reda - po njem tečejo nosilska
   mesta v mreži in skupinah, pri formatu TOP pa še izbor s črto reza.

   Po žrebu stran ni več en dolg izpis, ampak podnavigacija s pogledi:
     - Skupine (pri krožnem sistemu Razvrstitev) - skupine so zložljive
       vrstice, odprta je vedno največ ena; pri 100 prijavljenih je skupin 25
       in odprte vse hkrati bi bile nekaj tisoč vrstic,
     - Izločilni del - mreža se ne izriše cela, izbrano kolo je prvi stolpec,
     - Udeleženci - kdo igra, kdo je rezerva in kdo je odstopil.
   Zaključen dogodek pokaže še »Končno razvrstitev« - ENO sekcijo, v kateri so
   prva tri mesta poudarjena na vrhu istega seznama (prej sta bili dve sekciji
   in ista imena je bilo treba prebrati dvakrat).

   Na telefonu se naslov in pas pogledov preselita v lepljivo glavo, končna
   razvrstitev pa je zavihek in ne blok nad pasom - sicer bi moral gledalec
   prevoziti cel seznam, preden bi prišel do zavihkov.

   Ekipni dogodek (V28) ima namesto igralcev ekipe s kadri (EkipeDogodka).
   Tekma v mreži ali skupini je tam srečanje: klik vodi v zapisnik (postava in
   posamične tekme, javen za vse), izid tekme pa nastane iz srečanja samega.
   Prenesen izid finalne skupine vodi v zapisnik predtekmovalnega srečanja.

   Vnos rezultata je mogoč samo administratorju (oz. lastniku turnirja) in ne
   pri uvoženem dogodku - vir resnice je tam zveza (strežnik mutacije zavrne).
   Končana tekma z vpisanimi točkami pa vsakemu gledalcu odpre okno z nizi. */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi, igralciApi, turnirjiApi } from '../api/zahteve'
import type {
  IgralecDto,
  IzborDto,
  MrezaDto,
  PrijavaDto,
  SkupinaDto,
  StarostniPas,
  TekmaDto,
  Udelezenec,
} from '../api/tipi'
import {
  OZNAKE_FORMAT,
  OZNAKE_PASU_KRATKO,
  OZNAKE_SISTEM_KRATKO,
  OZNAKE_SPOL_KATEGORIJA,
  OZNAKE_STAROSTNI_PAS,
  OZNAKE_STATUS_PRIJAVE,
  OZNAKE_VIR,
  VRSTNI_RED_STAROSTNIH_PASOV,
  igralcevFormata,
  imePrijave,
  imeUdelezenca,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { EkipeDogodka } from '../komponente/EkipeDogodka'
import {
  KrmilaSeznama,
  poSeznamu,
  useFiltri,
  type ObmocjeFiltra,
  type Razvrstitev,
  type SkupinaFiltra,
} from '../komponente/Filtri'
import { GlavaDejanja, GlavaNaslov, useNazaj } from '../komponente/GlavaTelefona'
import { IskalniIzbirnik, type MoznostIzbirnika } from '../komponente/IskalniIzbirnik'
import { Lestvica } from '../komponente/Lestvica'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { Mreza, kolaMreze } from '../komponente/Mreza'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import {
  PodnavigacijaDogodka,
  type PogledDogodka,
  type PogledGumb,
} from '../komponente/PodnavigacijaDogodka'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SkupinaVrstica } from '../komponente/SkupinaVrstica'
import { NiziTekmeOkno, type StranTekme } from '../komponente/NiziTekmeOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { TekmaKartica, type KlikTekme } from '../komponente/TekmaKartica'
import { TekmeSeznam } from '../komponente/TekmeSeznam'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import { ZnackaStatusa, ZnackaVNaslovu } from '../komponente/Znacka'
import { besedeIskanja, ustrezaBesedam } from '../pomozno/iskanje'
import {
  imeKolaKratko,
  sklonEkip,
  sklonIgralcev,
  sklonNizov,
  sklonPrijavljenih,
  sklonSkupin,
} from '../pomozno/oblikovanje'
import { intervalOsvezevanja, jeVZivo, uraOsvezitve } from '../pomozno/osvezevanje'
import { useTelefon } from '../pomozno/telefon'

export function DogodekStran() {
  const { id } = useParams()
  const idDogodka = Number(id)
  const odjemalec = useQueryClient()
  const navigiraj = useNavigate()
  const { jeAdmin, smemUrejati } = useAvtentikacija()
  const jeTelefon = useTelefon()

  /* Med tekmovanjem se mreža osvežuje sama - gledalec v dvorani ne sme biti
     odvisen od ročnega ponovnega nalaganja. Ko je dogodek zaključen ali še v
     pripravi, se ne osvežuje nič. */
  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
    refetchInterval: (poizvedba) => intervalOsvezevanja(poizvedba.state.data?.dogodek.status),
  })

  /* Nadrejeni turnir nosi dvoje, kar dogodek sam ne: lastnistvo (za urejanje)
     in svoje ime - to na telefonu stoji v puscici nazaj, zato ga potrebuje
     tudi gost. Poizvedba je ista kot na strani turnirja, torej je odgovor
     najveckrat ze v predpomnilniku. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir != null,
  })

  /* Ekipni dogodek v pripravi: velikosti kadrov odločajo, ali je žreb mogoč
     (vsaka ekipa mora imeti za mizo dovolj igralcev). Ključ je isti kot v
     seznamu ekip, zato si odgovor delita. */
  const ekipniVPripravi =
    mreza.data?.dogodek.disciplina === 'EKIPNO' && mreza.data.dogodek.status === 'PRIPRAVA'
  const ekipe = useQuery({
    queryKey: ['ekipe-dogodka', idDogodka],
    queryFn: () => dogodkiApi.ekipe(idDogodka),
    enabled: ekipniVPripravi,
  })

  /* Tekma, za katero je odprto okno za vnos rezultata. */
  const [izbranaTekma, nastaviIzbranoTekmo] = useState<TekmaDto | null>(null)
  /* Ekipna tekma, pri kateri organizator izbira med zapisnikom in izidom
     brez boja. */
  const [ekipnaTekma, nastaviEkipnoTekmo] = useState<TekmaDto | null>(null)
  /* Končana tekma, za katero je odprto okno s točkami po nizih. */
  const [tekmaZNizi, nastaviTekmoZNizi] = useState<TekmaDto | null>(null)
  const [potrjujemZreb, nastaviPotrjujemZreb] = useState(false)

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['dogodek', idDogodka] })

  const zreb = useMutation({
    mutationFn: () => dogodkiApi.izvediZreb(idDogodka),
    onSuccess: () => {
      osvezi()
      nastaviPotrjujemZreb(false)
    },
  })

  useNazaj(idTurnir ? `/turnirji/${idTurnir}` : '/turnirji', turnir.data?.ime ?? 'Turnir')

  /* isLoading, ne isPending: ustavljena poizvedba (brez povezave) ali napaka
     ne smeta obviseti v večnem "Nalaganje …". */
  if (mreza.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (mreza.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (mreza.error) return <NapakaPoizvedbe poizvedba={mreza} kaj="dogodka" />
  if (!mreza.data) return <p className="obvestilo">Tega dogodka ni (več).</p>
  const podatki = mreza.data!
  const dogodek = podatki.dogodek
  const ekipno = dogodek.disciplina === 'EKIPNO'
  const uvozen = dogodek.vir !== null
  const koncan = dogodek.status === 'ZAKLJUCEN'
  /* Organizator sme upravljati dogodke svojega (ali klubskega) turnirja, admin
     vse - razen uvoženih: vir resnice je zveza. */
  const smem = !uvozen
    && (jeAdmin || (!!turnir.data && smemUrejati(turnir.data.idLastnik, turnir.data.idKlubLastnik)))

  const vPripravi = dogodek.status === 'PRIPRAVA'
  const aktivnePrijave = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')
  /* Zadržek pove strežnik (npr. zadnja skupina bi imela enega igralca),
     da vmesnik ne podvaja pravil razreza. */
  const zadrzek = podatki.izbor?.zadrzek ?? null
  /* Dvojice: v žreb gredo samo sestavljeni pari. Igralca brez soigralca ne
     smemo tiho izpustiti - žreb ga zavrne, zato gumb ugasnemo že tu. Isto
     velja za ekipo s premajhnim kadrom. */
  const brezPara = aktivnePrijave.filter((p) => p.polnoIme2 === null)
  const parovPremalo = dogodek.disciplina === 'DVOJICE' && brezPara.length > 0
  const zaMizo = ekipno && dogodek.formatSrecanja ? igralcevFormata(dogodek.formatSrecanja) : 0
  const premajhniKadri = ekipno ? (ekipe.data ?? []).filter((e) => e.steviloKadra < zaMizo) : []
  const zadrzekZreba = parovPremalo
    ? `Brez soigralca: ${brezPara.map((p) => p.polnoIme).join(', ')}.`
      + ' Sestavi pare ali igralce odjavi.'
    : premajhniKadri.length > 0
      ? `Premajhen kader (za mizo ${zaMizo} ${sklonIgralcev(zaMizo)}): `
        + `${premajhniKadri.map((e) => e.prikazanoIme).join(', ')}.`
      : zadrzek
  const premaloZaZreb = ekipno
    ? 'Za žreb sta potrebni vsaj 2 ekipi.'
    : 'Za žreb sta potrebna vsaj 2 igralca.'
  const zrebOnemogocen =
    aktivnePrijave.length < 2 || zadrzekZreba !== null || zreb.isPending
    || (ekipno && ekipe.isPending)

  /* "Ženske · do 21 let · krožni · na 5 nizov" - lastnosti dogodka v enem
     stavku; enak vrstni red na obeh širinah. Disciplina stoji na začetku samo
     pri dvojicah in ekipah: posamično je pravilo in bi bilo v vsaki vrstici
     odveč. Ekipni dogodek pove še format srečanja in prag zmag. */
  const jeDvojice = dogodek.disciplina === 'DVOJICE'
  const lastnosti = [
    jeDvojice ? 'Dvojice' : ekipno ? 'Ekipno' : null,
    OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija],
    dogodek.starostnaKategorija,
    OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja].toLowerCase(),
    ekipno && dogodek.formatSrecanja ? OZNAKE_FORMAT[dogodek.formatSrecanja] : null,
    ekipno && dogodek.zmagZaSrecanje
      ? `srečanje do ${dogodek.zmagZaSrecanje} ${dogodek.zmagZaSrecanje === 1 ? 'zmage' : 'zmag'}`
      : null,
    `na ${dogodek.privzetoSteviloNizov} ${sklonNizov(dogodek.privzetoSteviloNizov)}`,
  ]
    .filter(Boolean)
    .join(' · ')

  /* Zapisnik ekipne tekme. Prenesen izid finalne skupine svojega srečanja
     nima - dvoboj je bil odigran v predtekmovanju, zato vodi v tistega. */
  const tekmePoId = new Map(podatki.tekme.map((t) => [t.id, t]))
  const srecanjeTekme = (t: TekmaDto): number | null =>
    t.idSrecanje
    ?? (t.idPrenesena !== null ? (tekmePoId.get(t.idPrenesena)?.idSrecanje ?? null) : null)
  /* Ekipni tekmi, ki še ni odločena, sme organizator zapisati tudi izid brez
     boja (ekipa ni prišla) - zato mu klik ponudi izbiro in ne takoj zapisnika. */
  const izbiraEkipne = (t: TekmaDto) =>
    smem && !koncan && t.status !== 'KONCANA' && t.idPrenesena === null
    && t.udelezenec1 !== null && t.udelezenec2 !== null
  /* Organizatorju klik na tekmo, ki čaka, odpre vnos rezultata; končana tekma
     z vpisanimi točkami pa vsakemu gledalcu okno z nizi - to je le branje. */
  const vnosRezultata = (t: TekmaDto) =>
    smem && !koncan && (t.status === 'PRIPRAVLJENA' || t.status === 'V_IGRI')
  const klik: KlikTekme | undefined = ekipno
    ? {
        klikljiva: (t) => srecanjeTekme(t) !== null || izbiraEkipne(t),
        naKlik: (t) => {
          if (izbiraEkipne(t)) nastaviEkipnoTekmo(t)
          else navigiraj(`/srecanja/${srecanjeTekme(t)}`)
        },
        namig: (t) =>
          izbiraEkipne(t)
            ? 'Klikni za zapisnik srečanja ali izid brez boja'
            : 'Odpri zapisnik srečanja',
      }
    : {
        klikljiva: (t) => vnosRezultata(t) || imaTocke(t),
        naKlik: (t) => (vnosRezultata(t) ? nastaviIzbranoTekmo(t) : nastaviTekmoZNizi(t)),
        namig: (t) => (vnosRezultata(t) ? 'Klikni za vnos rezultata' : 'Pokaži točke po nizih'),
      }
  const oznakaVira = dogodek.vir && (
    <span className="oznaka-vira">{OZNAKE_VIR[dogodek.vir]} · uvoženo, samo za branje</span>
  )

  return (
    <section>
      {jeTelefon ? (
        <>
          <GlavaDejanja>
            {/* Žreb je dejanje, ki dogodek požene - edino, ki ostane vidno;
                vse drugo gre pod tri pike. */}
            {smem && vPripravi && (
              <button
                type="button"
                className="glava-telefon__gumb glava-telefon__gumb--zreb"
                disabled={zrebOnemogocen}
                onClick={() => nastaviPotrjujemZreb(true)}
              >
                {zreb.isPending ? 'Žrebam …' : 'Žreb'}
              </button>
            )}
            <MeniDejanj naslov="Podrobnosti in dejanja kategorije">
              {(zapri) => (
                <>
                  {/* Lastnosti dogodka so v glavi ene same vrstice odrezane;
                      tu stojijo cele. */}
                  <div className="uporabnik-meni__glava">
                    <span className="uporabnik-meni__ime">{dogodek.ime}</span>
                    <span className="uporabnik-meni__vloga">{lastnosti}</span>
                  </div>
                  {jeVZivo(dogodek.status) && (
                    <p className="uporabnik-meni__namig">
                      Osveženo ob {uraOsvezitve(mreza.dataUpdatedAt)}
                    </p>
                  )}
                  {smem && vPripravi && zrebOnemogocen && (
                    <p className="uporabnik-meni__namig">
                      {aktivnePrijave.length < 2 ? premaloZaZreb : zadrzekZreba}
                    </p>
                  )}
                  {/* Listki so sodniški listki posamičnih tekem; ekipna tekma
                      ima svoj zapisnik z natisom na strani srečanja. */}
                  {smem && dogodek.status === 'V_TEKU' && !ekipno && (
                    <Link
                      to={`/dogodki/${idDogodka}/listki`}
                      role="menuitem"
                      className="uporabnik-meni__postavka"
                      onClick={zapri}
                    >
                      Listki za tiskanje
                    </Link>
                  )}
                </>
              )}
            </MeniDejanj>
          </GlavaDejanja>

          {/* Naslovni blok ostane v lepljivi glavi: pri drsenju po razvrstitvi
              mora biti ves čas vidno, katera kategorija se gleda. */}
          <GlavaNaslov>
            <div className="glava-telefon__naslov">
              <div className="naslov-mobi__vrsta">
                <h1 className="naslov-mobi naslov-mobi--kategorija">{dogodek.ime}</h1>
                <ZnackaVNaslovu status={dogodek.status} />
              </div>
              <p className="naslov-mobi__meta naslov-mobi__meta--tesno">{lastnosti}</p>
            </div>
          </GlavaNaslov>
          {oznakaVira}
        </>
      ) : (
        <>
          <Link to={`/turnirji/${dogodek.idTurnir}`} className="povezava-nazaj">
            ← Nazaj na turnir
          </Link>

          {/* Ime dogodka je naslov strani v eni vrstici (72 px), lastnosti pod njim. */}
          <div className="stran-glava stran-glava--dejanja stran-glava--dno">
            <div>
              <h1 className="naslov-strani naslov-strani--enovrsticni">{dogodek.ime}</h1>
              <p className="uvod">{lastnosti}</p>
              {oznakaVira}
            </div>
            <div className="naslovna-vrstica__desno">
              {smem && dogodek.status === 'V_TEKU' && !ekipno && (
                <Link to={`/dogodki/${idDogodka}/listki`} className="gumb">
                  Listki za tiskanje
                </Link>
              )}
              <ZnackaStatusa status={dogodek.status} />
              {/* Žreb je dejanje, ki dogodek požene - zato stoji ob znački
                  statusa in ne skrit v seznamu prijav. */}
              {smem && vPripravi && (
                <button
                  className="gumb gumb--zreb"
                  disabled={zrebOnemogocen}
                  title={
                    aktivnePrijave.length < 2 ? premaloZaZreb : (zadrzekZreba ?? undefined)
                  }
                  onClick={() => nastaviPotrjujemZreb(true)}
                >
                  {zreb.isPending ? 'Žrebam …' : 'Izvedi žreb'}
                </button>
              )}
              {/* Ura zadnjega odgovora strežnika: brez nje gledalec ne ve, ali
                  stoji rezultat ali njegova povezava. */}
              {jeVZivo(dogodek.status) && (
                <span className="sekcija__meta">
                  Osveženo ob {uraOsvezitve(mreza.dataUpdatedAt)}
                </span>
              )}
            </div>
          </div>
        </>
      )}

      <SporociloNapake napaka={zreb.error} />

      {vPripravi ? (
        smem ? (
          <Priprava podatki={podatki} idDogodka={idDogodka} osvezi={osvezi} />
        ) : (
          <PripravaGost podatki={podatki} />
        )
      ) : (
        <Tekmovanje podatki={podatki} klik={klik} osvezi={osvezi} jeAdmin={smem} />
      )}

      {potrjujemZreb && (
        <PotrditvenoOkno
          naslov="Izvedba žreba"
          sporocilo={
            podatki.izbor?.crtaReza
              ? `Po žrebu prijav in vrstnega reda ni več mogoče spreminjati.` +
                ` Igralo bo najboljših ${podatki.izbor.igra} od ${podatki.izbor.prijavljenih} prijavljenih,` +
                ` ostali postanejo rezerve. Izvedem žreb?`
              : podatki.izbor
                ? 'Po žrebu prijav in jakostnega vrstnega reda ni več mogoče spreminjati.' +
                  ' Po njem se določijo nosilci. Izvedem žreb?'
                : 'Po žrebu prijav ni več mogoče spreminjati. Izvedem žreb?'
          }
          besedaPotrditve="Izvedi žreb"
          onPotrdi={() => zreb.mutate()}
          onZapri={() => nastaviPotrjujemZreb(false)}
        />
      )}

      {ekipnaTekma && (
        <EkipnaTekmaOkno
          tekma={ekipnaTekma}
          idSrecanje={srecanjeTekme(ekipnaTekma)}
          onZapri={() => nastaviEkipnoTekmo(null)}
          onBrezBoja={() => {
            nastaviIzbranoTekmo(ekipnaTekma)
            nastaviEkipnoTekmo(null)
          }}
        />
      )}

      {izbranaTekma && (
        <VnosRezultataOkno
          tekma={izbranaTekma}
          samoBrezIgre={ekipno}
          onZapri={() => nastaviIzbranoTekmo(null)}
          onShranjeno={osvezi}
        />
      )}

      {tekmaZNizi && (
        <NiziTekmeOkno
          nadnaslov={dogodek.ime}
          strani={[
            stranTekme(tekmaZNizi.udelezenec1, tekmaZNizi.dobljeniNizi1, tekmaZNizi),
            stranTekme(tekmaZNizi.udelezenec2, tekmaZNizi.dobljeniNizi2, tekmaZNizi),
          ]}
          nizi={tekmaZNizi.nizi}
          izidTip={tekmaZNizi.izidTip}
          onZapri={() => nastaviTekmoZNizi(null)}
        />
      )}
    </section>
  )
}

/* Končana tekma odpre okno z nizi, samo kadar so točke vpisane - brez njih bi
   okno ponovilo izid, ki je že na kartici. */
function imaTocke(tekma: TekmaDto): boolean {
  return tekma.status === 'KONCANA' && tekma.nizi.length > 0
}

function stranTekme(udelezenec: Udelezenec | null, dobljeniNizi: number, tekma: TekmaDto): StranTekme {
  return {
    imena: udelezenec
      ? [udelezenec.polnoIme, udelezenec.polnoIme2].filter((ime): ime is string => !!ime)
      : ['—'],
    dobljeniNizi,
    zmagovalec: udelezenec !== null && tekma.idZmagovalcaPrijave === udelezenec.idPrijave,
  }
}

/* Izbira pri ekipni tekmi (samo organizator): zapisnik srečanja ali izid brez
   boja. Izid ekipne tekme sicer nastane iz zapisnika - brez boja je edini, ki
   se zapiše neposredno, in ker odstrani srečanje, mora biti izrecna izbira. */
function EkipnaTekmaOkno({
  tekma,
  idSrecanje,
  onZapri,
  onBrezBoja,
}: {
  tekma: TekmaDto
  idSrecanje: number | null
  onZapri: () => void
  onBrezBoja: () => void
}) {
  return (
    <ModalnoOkno naslov="Ekipna tekma" onZapri={onZapri}>
      <p className="modal__podnaslov">
        {imeUdelezenca(tekma.udelezenec1)} : {imeUdelezenca(tekma.udelezenec2)}
      </p>
      <p className="namig">
        Postavo in izide posamičnih tekem vpišeš v zapisnik srečanja. Ko je srečanje odločeno,
        se izid tekme zapiše sam in zmagovalec napreduje. Brez boja zapišeš, kadar ekipa ni
        nastopila ali je bila izključena.
      </p>
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onBrezBoja}>
          Izid brez boja …
        </button>
        {idSrecanje !== null && (
          <Link to={`/srecanja/${idSrecanje}`} className="gumb gumb--glavni">
            Odpri zapisnik
          </Link>
        )}
      </div>
    </ModalnoOkno>
  )
}

/* ---------- Seznam prijavljenih (priprava in pogled Udeleženci) ---------- */

/* Klubi ene tekmovalne enote: posameznik ima enega, par pa enega ali dva.
   Filter kluba zato pri paru zadene, če se ujema kateri koli od njiju. */
function klubiPrijave(prijava: PrijavaDto): string[] {
  const prvi = prijava.klub ?? 'brez kluba'
  if (!prijava.polnoIme2) return [prvi]
  const drugi = prijava.klub2 ?? 'brez kluba'
  return prvi === drugi ? [prvi] : [prvi, drugi]
}

/* Moč enote za razvrstitev: rating posameznika oz. vsota ratingov para.
   Enota, ki ji manjka rating, gre na dno (-1), da jo človek opazi. */
function mocPrijave(prijava: PrijavaDto): number {
  if (!prijava.polnoIme2) return prijava.rating ?? -1
  if (prijava.rating === null || prijava.rating2 === null) return -1
  return prijava.rating + prijava.rating2
}

/* Cilj je 100 prijavljenih na enem zaslonu prenosnika brez straničenja: do 24
   en stolpec, 25-120 dva, nad 120 trije. Iskanje in filter kluba delujeta na
   celoten seznam in ga znova razdelita, zato se stolpci vedno enako napolnijo.
   Številka pred imenom je jakostno mesto, ki ga bo uporabil žreb. */
function SeznamPrijavljenih({
  naslov,
  prijave,
  dejanje,
  enota = 'igralec',
}: {
  naslov: string
  prijave: PrijavaDto[]
  dejanje?: (prijava: PrijavaDto) => ReactNode
  /* Kaj je v tem seznamu ena vrstica. Seznama parov ni mogoče ugotoviti iz
     vsebine: prazen seznam parov in prazen seznam posameznikov sta enaka. */
  enota?: 'igralec' | 'par'
}) {
  const [iskanje, nastaviIskanje] = useState('')
  const [klub, nastaviKlub] = useState('vsi')
  const jePar = enota === 'par'

  /* Razvrstitev po ratingu navzdol; brez ratinga na dno, da jih človek opazi. */
  const urejene = useMemo(
    () => [...prijave].sort((prva, druga) => mocPrijave(druga) - mocPrijave(prva)),
    [prijave],
  )

  const klubi = useMemo(() => {
    const stevci = new Map<string, number>()
    for (const prijava of urejene) {
      for (const ime of klubiPrijave(prijava)) {
        stevci.set(ime, (stevci.get(ime) ?? 0) + 1)
      }
    }
    return [...stevci.entries()].sort((prva, druga) => prva[0].localeCompare(druga[0], 'sl'))
  }, [urejene])

  const prikazane = useMemo(() => {
    const iskano = iskanje.trim().toLowerCase()
    return urejene
      .map((prijava, indeks) => ({ prijava, mesto: indeks + 1 }))
      .filter(({ prijava }) => {
        if (klub !== 'vsi' && !klubiPrijave(prijava).includes(klub)) return false
        if (!iskano) return true
        // pri paru zadene tudi soigralčev priimek
        return imePrijave(prijava).toLowerCase().includes(iskano)
      })
  }, [urejene, iskanje, klub])

  /* Filter kluba ostane veljaven, tudi ko se seznam spremeni (odjava zadnjega
     igralca kluba) - sicer bi seznam obtičal prazen brez razloga. */
  useEffect(() => {
    if (klub !== 'vsi' && !klubi.some(([ime]) => ime === klub)) nastaviKlub('vsi')
  }, [klubi, klub])

  const steviloStolpcev = prikazane.length <= 24 ? 1 : prikazane.length <= 120 ? 2 : 3
  const stolpci = razdeli(prikazane, steviloStolpcev)

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>{naslov}</h2>
        <div className="naslovna-vrstica__desno">
          <input
            className="iskalnik iskalnik--kratek"
            type="search"
            value={iskanje}
            onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
            placeholder="išči po priimku"
            aria-label="Išči po priimku"
          />
          <span className="sekcija__meta">
            {prikazane.length} od {urejene.length} prikazanih
          </span>
        </div>
      </div>

      {klubi.length > 1 && (
        <div className="izbirnik">
          <button
            type="button"
            className={'izbirnik__gumb' + (klub === 'vsi' ? ' izbirnik__gumb--aktiven' : '')}
            onClick={() => nastaviKlub('vsi')}
          >
            Vsi klubi · {urejene.length}
          </button>
          {klubi.map(([ime, stevilo]) => (
            <button
              type="button"
              key={ime}
              className={'izbirnik__gumb' + (klub === ime ? ' izbirnik__gumb--aktiven' : '')}
              onClick={() => nastaviKlub(ime)}
            >
              {ime} · {stevilo}
            </button>
          ))}
        </div>
      )}

      {prikazane.length === 0 ? (
        <p className="obvestilo">
          {urejene.length === 0
            ? (jePar ? 'Ni še sestavljenih parov.' : 'Ni še prijavljenih igralcev.')
            : (jePar ? 'Noben par ne ustreza iskanju.' : 'Noben prijavljeni ne ustreza iskanju.')}
        </p>
      ) : (
        <div
          className={
            'prijavljeni' +
            (steviloStolpcev === 1 ? ' prijavljeni--en' : '') +
            (steviloStolpcev === 3 ? ' prijavljeni--trije' : '')
          }
        >
          {stolpci.map((stolpec, indeks) => (
            <div key={indeks}>
              <div
                className={
                  'prijava-vrstica prijava-vrstica--glava' +
                  (dejanje ? ' prijava-vrstica--z-dejanjem' : '')
                }
              >
                <span className="prijava-vrstica__mesto">#</span>
                <span>{jePar ? 'Par' : 'Igralec'}</span>
                <span className="prijava-vrstica__rating">Rating</span>
                {dejanje && <span />}
              </div>
              {stolpec.map(({ prijava, mesto }) => (
                <div
                  className={
                    'prijava-vrstica' + (dejanje ? ' prijava-vrstica--z-dejanjem' : '')
                  }
                  key={prijava.id}
                >
                  <span className="prijava-vrstica__mesto">{mesto}</span>
                  <span className="prijava-vrstica__ime">
                    {imePrijave(prijava)}
                    <span className="prijava-vrstica__klub">
                      {' · '}
                      {klubiPrijave(prijava).join(' · ')}
                    </span>
                  </span>
                  {/* Par ima dva ratinga; skupnega nima, ker dvojice v rating
                      ne štejejo - zato ju izpišemo oba. */}
                  <span className="prijava-vrstica__rating">
                    {prijava.polnoIme2
                      ? `${prijava.rating ?? '—'} / ${prijava.rating2 ?? '—'}`
                      : (prijava.rating ?? '—')}
                  </span>
                  {dejanje && dejanje(prijava)}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/* Seznam razdeli na N priblizno enakih zaporednih delov (levo prva polovica,
   desno druga) - ne izmenicno, ker se mesta berejo navzdol po stolpcu. */
function razdeli<T>(seznam: T[], koliko: number): T[][] {
  const naStolpec = Math.ceil(seznam.length / koliko)
  const deli: T[][] = []
  for (let i = 0; i < koliko; i++) {
    deli.push(seznam.slice(i * naStolpec, (i + 1) * naStolpec))
  }
  return deli.filter((del, indeks) => indeks === 0 || del.length > 0)
}

/* ---------- Faza priprave (gost): samo seznam prijavljenih ---------- */

function PripravaGost({ podatki }: { podatki: MrezaDto }) {
  /* Uvožen dogodek žreba pri nas nima - izvede ga zveza v svojem sistemu. */
  const namig = podatki.dogodek.vir
    ? 'Tekmovanje vodi zveza; žreb in rezultati se uvozijo iz njenega sistema.'
    : 'Žreb izvede administrator (sodnik) po prijavi.'
  if (podatki.dogodek.disciplina === 'EKIPNO') {
    return (
      <>
        <EkipeDogodka dogodek={podatki.dogodek} smem={false} />
        <p className="namig">{namig}</p>
      </>
    )
  }
  const aktivne = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')
  /* Pri dvojicah gost vidi ločeno, kdo je že v paru in kdo še išče soigralca -
     to je edino, kar se do žreba dogaja. */
  const dvojice = podatki.dogodek.disciplina === 'DVOJICE'
  const pari = aktivne.filter((p) => p.polnoIme2 !== null)
  const brezPara = aktivne.filter((p) => p.polnoIme2 === null)
  return (
    <>
      {dvojice ? (
        <>
          <SeznamPrijavljenih naslov="Pari" prijave={pari} enota="par" />
          {brezPara.length > 0 && (
            <SeznamPrijavljenih naslov="Brez soigralca" prijave={brezPara} />
          )}
        </>
      ) : (
        <SeznamPrijavljenih naslov="Prijavljeni" prijave={aktivne} />
      )}
      <p className="namig">{namig}</p>
    </>
  )
}

/* ---------- Faza priprave (admin): prijave in žreb ---------- */

function Priprava({
  podatki,
  idDogodka,
  osvezi,
}: {
  podatki: MrezaDto
  idDogodka: number
  osvezi: () => void
}) {
  const aktivnePrijave = podatki.prijave.filter((prijava) => prijava.status === 'PRIJAVLJEN')
  const izbor = podatki.izbor
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'

  const odjava = useMutation({
    mutationFn: (idPrijave: number) => dogodkiApi.odjavi(idPrijave),
    onSuccess: osvezi,
  })

  /* Seznam prijavljenih dobi vso širino okvirja: glavni cilj tega zaslona je
     100 prijavljenih na enem zaslonu prenosnika brez straničenja, kar z blokom
     ob strani ne gre. Blok "Dodaj igralce" zato stoji pod njim.

     Ekipe se odjavljajo v seznamu ekip (odjava izbriše tudi kader, zato gre
     prek potrditve) - jakostni vrstni red jih samo ureja. */
  return (
    <>
      <SporociloNapake napaka={odjava.error} />

      {izbor ? (
        <div>
          <div className="naslovna-vrstica">
            <h2>Jakostni vrstni red</h2>
            <span className="sekcija__meta">
              {aktivnePrijave.length}{' '}
              {ekipno ? sklonEkip(aktivnePrijave.length) : sklonPrijavljenih(aktivnePrijave.length)}
            </span>
          </div>
          {aktivnePrijave.length === 0 ? (
            <p className="obvestilo">
              {ekipno ? 'Ni še prijavljenih ekip.' : 'Ni še prijavljenih igralcev.'}
            </p>
          ) : (
            <JakostniVrstniRed
              prijave={aktivnePrijave}
              izbor={izbor}
              idDogodka={idDogodka}
              osvezi={osvezi}
              ekipno={ekipno}
              onOdjava={ekipno ? undefined : (id) => odjava.mutate(id)}
            />
          )}
        </div>
      ) : ekipno ? null : podatki.dogodek.disciplina === 'DVOJICE' ? (
        <SestavljanjePar
          prijave={aktivnePrijave}
          idDogodka={idDogodka}
          mesanKategorija={podatki.dogodek.spolKategorija === 'MESANO'}
          osvezi={osvezi}
          onOdjava={(id) => odjava.mutate(id)}
        />
      ) : (
        <SeznamPrijavljenih
          naslov="Prijavljeni"
          prijave={aktivnePrijave}
          dejanje={(prijava) => (
            <button
              type="button"
              className="prijava-vrstica__dejanje"
              onClick={() => odjava.mutate(prijava.id)}
            >
              Odjavi
            </button>
          )}
        />
      )}

      {ekipno ? (
        <EkipeDogodka dogodek={podatki.dogodek} smem />
      ) : (
        <DodajanjeIgralcev podatki={podatki} idDogodka={idDogodka} osvezi={osvezi} />
      )}
    </>
  )
}

/* Dvojice v pripravi: sestavljene pare zgoraj, igralce brez soigralca spodaj.

   Igralci se prijavijo posamično, pare pa sestavi organizator - tako se lahko
   prijavi tudi tisti, ki soigralca še nima. Par je ENA prijava: ko ju povežemo,
   vrstica drugega izgine, ob razdružitvi pa se vrne. Dokler kdo ostane brez
   soigralca, žreba ni - v mreži bi ga zaman iskali. */
function SestavljanjePar({
  prijave,
  idDogodka,
  mesanKategorija,
  osvezi,
  onOdjava,
}: {
  prijave: PrijavaDto[]
  idDogodka: number
  mesanKategorija: boolean
  osvezi: () => void
  onOdjava: (idPrijave: number) => void
}) {
  const pari = prijave.filter((p) => p.polnoIme2 !== null)
  const brezPara = prijave.filter((p) => p.polnoIme2 === null)
  const [prvi, nastaviPrvega] = useState<number | null>(null)
  const [drugi, nastaviDrugega] = useState<number | null>(null)

  /* Izbor velja samo, dokler je igralec še brez soigralca - odjavljen (ali
     med tem drugje povezan) igralec iz polja izpade. */
  const prviVelja = brezPara.some((p) => p.id === prvi) ? prvi : null
  const drugiVelja = brezPara.some((p) => p.id === drugi) ? drugi : null

  const povezovanje = useMutation({
    mutationFn: ([prva, druga]: [number, number]) => dogodkiApi.poveziVPar(idDogodka, prva, druga),
    onSuccess: () => {
      nastaviPrvega(null)
      nastaviDrugega(null)
      osvezi()
    },
  })

  const razdruzevanje = useMutation({
    mutationFn: (idPrijave: number) => dogodkiApi.razdruziPar(idPrijave),
    onSuccess: osvezi,
  })

  /* Pri velikem turnirju dvojic je brez soigralca tudi šestdeset imen, zato se
     igralca para poiščeta z vpisom imena in ne s kljukicama v seznamu. Vsako
     polje ponudi vse brez soigralca razen tistega, ki je že v drugem polju. */
  const moznostPrijave = (prijava: PrijavaDto): MoznostIzbirnika => ({
    id: prijava.id,
    ime: prijava.polnoIme,
    podrobnost: [prijava.klub ?? 'brez kluba', prijava.rating !== null ? `rating ${prijava.rating}` : null]
      .filter(Boolean)
      .join(' · '),
  })
  const moznostiPrvega = brezPara.filter((p) => p.id !== drugiVelja).map(moznostPrijave)
  const moznostiDrugega = brezPara.filter((p) => p.id !== prviVelja).map(moznostPrijave)
  const lahkoPovezem = prviVelja !== null && drugiVelja !== null && !povezovanje.isPending

  return (
    <>
      <SporociloNapake napaka={povezovanje.error} />
      <SporociloNapake napaka={razdruzevanje.error} />

      <SeznamPrijavljenih
        naslov="Pari"
        prijave={pari}
        enota="par"
        dejanje={(par) => (
          <button
            type="button"
            className="prijava-vrstica__dejanje"
            onClick={() => razdruzevanje.mutate(par.id)}
          >
            Razdruži
          </button>
        )}
      />

      <div className="plosca">
        <div className="naslovna-vrstica">
          <h2 className="sekcija__naslov--manjsi">Poveži v par</h2>
          {brezPara.length > 0 && (
            <span className="sekcija__meta">{brezPara.length} brez soigralca</span>
          )}
        </div>

        {brezPara.length === 0 ? (
          <p className="obvestilo">
            {pari.length === 0
              ? 'Ni še prijavljenih igralcev.'
              : 'Vsi prijavljeni so v parih — žreb je mogoč.'}
          </p>
        ) : (
          <form
            className="obrazec__vrstica sestavljanje-para"
            onSubmit={(dogodek) => {
              dogodek.preventDefault()
              if (lahkoPovezem) povezovanje.mutate([prviVelja, drugiVelja])
            }}
          >
            <IskalniIzbirnik
              vObrazcu
              oznaka="Igralec"
              namig="Vpiši ime"
              moznosti={moznostiPrvega}
              izbrano={prviVelja}
              naIzbiro={nastaviPrvega}
              naPraznjenje={() => nastaviPrvega(null)}
            />
            <IskalniIzbirnik
              vObrazcu
              oznaka="Soigralec"
              namig="Vpiši ime"
              moznosti={moznostiDrugega}
              izbrano={drugiVelja}
              naIzbiro={nastaviDrugega}
              naPraznjenje={() => nastaviDrugega(null)}
            />
            <button className="gumb" type="submit" disabled={!lahkoPovezem}>
              {povezovanje.isPending ? 'Povezujem …' : 'Poveži v par'}
            </button>
          </form>
        )}

        <p className="namig">
          Vpiši imeni obeh igralcev in ju poveži v par.
          {mesanKategorija
            ? ' Kategorija so mešane dvojice: par mora sestavljati en moški in ena ženska.'
            : ''}{' '}
          Dokler kdo ostane brez soigralca, žreb ni mogoč.
        </p>
      </div>

      {brezPara.length > 0 && (
        <SeznamPrijavljenih
          naslov="Brez soigralca"
          prijave={brezPara}
          dejanje={(prijava) => (
            <button
              type="button"
              className="prijava-vrstica__dejanje"
              onClick={() => onOdjava(prijava.id)}
            >
              Odjavi
            </button>
          )}
        />
      )}
    </>
  )
}

/* Seznam prijavljenih po jakosti - pri formatu TOP še s črto reza.
   Vrstni red odloča nosilce (v mreži in skupinah), pri formatu TOP pa tudi
   izbor, zato je edino, kar se pred žrebom ureja. Ureja se ročno, ker igralci
   BREZ ratinga stojijo na vrhu predloga: sistem o njih ne ve nič in mora
   človek zavestno povedati, kam sodijo.
   Premikanje je s puščicama (zanesljivo tudi na dotik in s tipkovnico). */
function JakostniVrstniRed({
  prijave,
  izbor,
  idDogodka,
  osvezi,
  ekipno = false,
  onOdjava,
}: {
  prijave: PrijavaDto[]
  izbor: IzborDto
  idDogodka: number
  osvezi: () => void
  /* Vrstice so ekipe: rating je povprečje najboljših ratingov kadra. */
  ekipno?: boolean
  /* Brez dejanja vrstica odjave ne ponudi (ekipa se odjavi v seznamu ekip). */
  onOdjava?: (idPrijave: number) => void
}) {
  /* Strežnik pošlje prijave že v veljavnem vrstnem redu; tu se ureja
     samo lokalna kopija, dokler je ne shranimo. */
  const [vrstni, nastaviVrstni] = useState<PrijavaDto[]>(prijave)
  const kljucStreznika = prijave.map((p) => p.id).join(',')

  useEffect(() => {
    nastaviVrstni(prijave)
    // ob spremembi seznama na strežniku (nova prijava, odjava, shranjeno)
    // se lokalna kopija zavrže
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kljucStreznika])

  const spremenjeno = vrstni.map((p) => p.id).join(',') !== kljucStreznika

  const shranjevanje = useMutation({
    mutationFn: () => dogodkiApi.shraniVrstniRed(idDogodka, vrstni.map((p) => p.id)),
    onSuccess: osvezi,
  })

  function premakni(indeks: number, zaKoliko: number) {
    const cilj = indeks + zaKoliko
    if (cilj < 0 || cilj >= vrstni.length) return
    const novi = [...vrstni]
    ;[novi[indeks], novi[cilj]] = [novi[cilj], novi[indeks]]
    nastaviVrstni(novi)
  }

  /* Katera skupina pripada mestu (1-based) po predogledu strežnika. Predogled
     je samo pri formatu TOP, kjer je razporeditev zaporedna; žrebane skupine
     ga nimajo, ker se odloči šele ob žrebu. */
  const imaSkupine = izbor.skupine.length > 0

  function skupinaZaMesto(mesto: number): string | null {
    return izbor.skupine.find((s) => mesto >= s.odMesta && mesto <= s.doMesta)?.oznaka ?? null
  }

  return (
    <>
      <p className="izbor__povzetek">
        {izbor.crtaReza ? (
          <>
            Igra <strong>{izbor.igra}</strong> od {izbor.prijavljenih} prijavljenih
            {izbor.igra < izbor.meja && ` (mest je ${izbor.meja}, a je prijav manj)`}
            {izbor.skupine.length > 0 && (
              <>
                {' · '}
                {izbor.skupine
                  .map((s) => `${s.oznaka}: ${s.odMesta}.–${s.doMesta}.`)
                  .join(' · ')}
              </>
            )}
          </>
        ) : izbor.steviloSkupin !== null ? (
          <>
            Igrajo vsi. Žreb jih razdeli v <strong>{izbor.steviloSkupin}</strong>{' '}
            {sklonSkupin(izbor.steviloSkupin)}: prvi po jakosti je nosilec skupine A, drugi
            skupine B in tako naprej, ostali se žrebajo po jakostnih pasovih.
          </>
        ) : (
          <>
            Igrajo vsi. Vrstni red določa nosilce: prvi gre na vrh mreže, drugi na dno, ostali se
            žrebajo po jakostnih pasovih (3.–4., 5.–8., 9.–16. …).
          </>
        )}
      </p>

      {izbor.zadrzek && <p className="obvestilo obvestilo--opozorilo">{izbor.zadrzek}</p>}

      <SporociloNapake napaka={shranjevanje.error} />

      {/* Vrstica je mreža s stalnimi stolpci, zato mora imeti vsak stolpec
          svojo celico tudi takrat, ko je prazna - manjkajoča celica bi vse
          naslednje potisnila en stolpec levo in ime stisnila na 32 px.
          Stolpec oznake skupine obstaja samo, kadar je predogled skupin. */}
      <ol className={'izbor' + (imaSkupine ? '' : ' izbor--brez-skupin')}>
        {vrstni.map((prijava, indeks) => {
          const mesto = indeks + 1
          const rezerva = izbor.crtaReza && mesto > izbor.igra
          const skupina = rezerva ? null : skupinaZaMesto(mesto)
          return (
            <li key={prijava.id}>
              {izbor.crtaReza && mesto === izbor.igra + 1 && (
                <div className="izbor__crta">
                  <span>črta reza — spodnji so rezerve</span>
                </div>
              )}
              <div className={`izbor__vrstica${rezerva ? ' izbor__vrstica--rezerva' : ''}`}>
                <span className="izbor__mesto">{mesto}.</span>
                {imaSkupine && <span className="izbor__skupina">{skupina}</span>}
                <span className="izbor__ime">
                  {prijava.polnoIme}
                  <span className="izbor__podrobnost">
                    {[podnapisPrijave(prijava, ekipno), prijava.rating].filter((d) => d !== null).join(' · ')}
                  </span>
                </span>
                <span className="izbor__oznaka">
                  {prijava.rating === null && (
                    <span className="znacka znacka--opozorilo">brez ratinga</span>
                  )}
                </span>
                <span className="izbor__gumbi">
                  <button
                    className="gumb gumb--majhen"
                    disabled={indeks === 0}
                    title="Premakni navzgor"
                    onClick={() => premakni(indeks, -1)}
                  >
                    ↑
                  </button>
                  <button
                    className="gumb gumb--majhen"
                    disabled={indeks === vrstni.length - 1}
                    title="Premakni navzdol"
                    onClick={() => premakni(indeks, 1)}
                  >
                    ↓
                  </button>
                  {onOdjava && (
                    <button
                      className="gumb gumb--majhen"
                      title="Odjavi igralca"
                      onClick={() => onOdjava(prijava.id)}
                    >
                      Odjavi
                    </button>
                  )}
                </span>
              </div>
            </li>
          )
        })}
      </ol>

      <div className="obrazec__gumbi">
        <button
          className="gumb"
          disabled={!spremenjeno}
          onClick={() => nastaviVrstni(prijave)}
        >
          Razveljavi
        </button>
        <button
          className="gumb gumb--glavni"
          disabled={!spremenjeno || shranjevanje.isPending}
          onClick={() => shranjevanje.mutate()}
        >
          {shranjevanje.isPending ? 'Shranjujem …' : 'Shrani vrstni red'}
        </button>
      </div>
      {!spremenjeno && (
        <p className="namig">
          {ekipno
            ? 'Predlog je razvrščen po povprečnem ratingu najboljših igralcev kadra (toliko, kolikor' +
              ' jih sede za mizo); ekipe brez igralca z ratingom so na vrhu, da jih uvrstiš sam.' +
              ' Dopolnjen kader predlog spremeni. Vrstni red določa tudi skupino.'
            : 'Predlog je razvrščen po Turnirko ratingu; igralci brez ratinga so na vrhu,' +
              ' da jih uvrstiš sam. Vrstni red določa tudi skupino.'}
        </p>
      )}
    </>
  )
}

/* Podnapis pod imenom prijave: klub igralca, pri ekipi pa klub samo, kadar ga
   ime ne pove že samo (klubska ekipa se imenuje po klubu, prosta kluba nima). */
function podnapisPrijave(prijava: PrijavaDto, ekipno: boolean): string | null {
  if (!ekipno) return prijava.klub ?? 'brez kluba'
  if (prijava.klub === null) return 'prosta ekipa'
  return prijava.polnoIme.toLocaleLowerCase('sl').startsWith(prijava.klub.toLocaleLowerCase('sl'))
    ? null
    : prijava.klub
}

/* Merila nad seznamom igralcev, ki jih je mogoče prijaviti.

   Skupine so iste kot na lestvici (starost, spol, klub) - organizator ju bere
   izmenično in dve različni razdelitvi istih ljudi bi bili dve zgodbi. Spol
   in klub sta podatkovna: pri dogodku za ženske ostane v seznamu en sam spol
   in skupina se sploh ne izriše (glej useFiltri).

   Starostni pas je NAJOŽJI, ki mu igralec ustreza, zato so pasovi
   izključujoči: za turnir U15 se izberejo U11 + U13 + U15. Prekrivajočih se
   meril ("in vsi mlajši") SkupinaFiltra namenoma ne pozna. */
const SKUPINE_IGRALCEV: SkupinaFiltra<IgralecDto>[] = [
  {
    kljuc: 'starost',
    oznaka: 'Starost',
    vrednost: (igralec) => igralec.starostniPas,
    napis: (v) => OZNAKE_STAROSTNI_PAS[v as StarostniPas] ?? v,
    vrstniRed: poSeznamu(VRSTNI_RED_STAROSTNIH_PASOV),
  },
  {
    kljuc: 'spol',
    oznaka: 'Spol',
    vrednost: (igralec) => igralec.spol,
    napis: (v) => (v === 'MOSKI' ? 'Moški' : 'Ženske'),
    vrstniRed: poSeznamu(['MOSKI', 'ZENSKI']),
  },
  { kljuc: 'klub', oznaka: 'Klub', vrednost: (igralec) => igralec.klub?.ime ?? null },
]

/* Rating je meja tega tekmovanja ("od 1200 navzgor") in ne izbira med
   vnaprej narisanimi pasovi, zato dve polji. */
const OBMOCJA_IGRALCEV: ObmocjeFiltra<IgralecDto>[] = [
  {
    kljuc: 'rating',
    oznaka: 'Rating',
    vrednost: (igralec) => igralec.rating,
    namig: 'Igralec brez obračunane tekme ratinga nima — ob vpisani meji odpade.',
  },
]

/* Priimek je privzetek: organizator igralca išče po imenu, ne po jakosti.
   Rating je drugo merilo (za turnirje z mejo), klub tretje (klub prijavi
   svoje naenkrat). */
const RAZVRSTITVE_IGRALCEV: Razvrstitev<IgralecDto>[] = [
  { kljuc: 'priimek', oznaka: 'Priimek (A–Ž)', primerjaj: poPriimku },
  {
    kljuc: 'rating',
    oznaka: 'Rating',
    /* Brez ratinga na dno (-1) - isto pravilo kot v seznamu prijavljenih. */
    primerjaj: (a, b) => (b.rating ?? -1) - (a.rating ?? -1) || poPriimku(a, b),
  },
  {
    kljuc: 'klub',
    oznaka: 'Klub',
    primerjaj: (a, b) =>
      imeKlubaZaVrstniRed(a).localeCompare(imeKlubaZaVrstniRed(b), 'sl') || poPriimku(a, b),
  },
]

function poPriimku(a: IgralecDto, b: IgralecDto): number {
  return a.priimek.localeCompare(b.priimek, 'sl') || a.ime.localeCompare(b.ime, 'sl')
}

/* Igralci brez kluba gredo na konec in ne med "B" - klub je tu razvrstitev,
   ne oznaka. */
function imeKlubaZaVrstniRed(igralec: IgralecDto): string {
  return igralec.klub?.ime ?? '￿'
}

/* Koliko vrstic se sploh izriše. Šifrant ima po uvozu zgodovine NTZS več kot
   tisoč igralcev; ves seznam v DOM pomeni, da se ob vsaki vtipkani črki in
   vsaki kljukici izriše tisoč vrstic. Rez je izrecen (izpiše se, koliko jih
   je zunaj) - tiho odrezan seznam bi trdil, da igralca ni. */
const NAJVEC_VRSTIC = 200

/* Dodajanje igralcev na dogodek.

   Prej je bil tu en sam dolg seznam vseh neprijavljenih igralcev s
   kljukicami: organizator je moral do vsakega prevoziti šifrant cele države.
   Zdaj so nad njim iskalnik (po imenu in priimku) in ista merila kot na
   lestvici (starost, spol, klub) ter meji ratinga.

   Dve pravili, ki ju ne razbij:
   - iskanje zoži seznam PRED filtri, zato so števci ob merilih števci tega,
     kar organizator vidi (isto kot na lestvici);
   - izbrani, ki jih trenutna merila ne pokažejo, se izpišejo v svoji skupini
     nad seznamom. Gumb prijavi tudi tiste, ki so med iskanjem naslednjega
     igralca padli iz pogleda - izbor, ki ga ne vidiš, je past. Vrstica, ki
     merilom ustreza, ob kljukici NE odskoči: ostane, kjer je. */
function DodajanjeIgralcev({
  podatki,
  idDogodka,
  osvezi,
}: {
  podatki: MrezaDto
  idDogodka: number
  osvezi: () => void
}) {
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  const [izbrani, nastaviIzbrane] = useState<Set<number>>(new Set())
  const [iskanje, nastaviIskanje] = useState('')
  const jeTelefon = useTelefon()

  const prijavljanje = useMutation({
    mutationFn: (idji: number[]) => dogodkiApi.prijaviIgralce(idDogodka, idji),
    onSuccess: () => {
      nastaviIzbrane(new Set())
      osvezi()
    },
  })

  /* Na voljo so igralci, ki na ta dogodek se nimajo aktivne prijave in ki po
     spolu ustrezajo kategoriji dogodka. Odjavljeni (ODJAVLJEN) se spet
     pojavijo - ponovna prijava aktivira njihov obstojeci zapis.

     Pri dvojicah je treba pogledati OBA igralca prijave: soigralec para nima
     svoje vrstice (ta je ob povezavi izginila), pa vendar že nastopa.

     Kategorija MESANO in KDORKOLI po spolu ne omejujeta - pri mešanih
     dvojicah je pravilo o sestavi PARA in ga preveri povezava para. */
  const naVoljo = useMemo(() => {
    if (!igralci.data) return []
    const zePrijavljeni = new Set(
      podatki.prijave
        .filter((prijava) => prijava.status !== 'ODJAVLJEN')
        .flatMap((prijava) =>
          prijava.idIgralca2 !== null
            ? [prijava.idIgralca, prijava.idIgralca2]
            : [prijava.idIgralca],
        ),
    )
    return igralci.data.filter((igralec) => {
      if (zePrijavljeni.has(igralec.id)) return false
      if (podatki.dogodek.spolKategorija === 'MOSKI') return igralec.spol === 'MOSKI'
      if (podatki.dogodek.spolKategorija === 'ZENSKE') return igralec.spol === 'ZENSKI'
      return true
    })
  }, [igralci.data, podatki])

  /* Iskanje po imenu in priimku (brez šumnikov, po besedah): "krizan" najde
     Križana, "novak ana" pa Novak Ano ne glede na vrstni red vpisanega. */
  const najdeni = useMemo(() => {
    const besede = besedeIskanja(iskanje)
    if (besede.length === 0) return naVoljo
    return naVoljo.filter((igralec) =>
      ustrezaBesedam(`${igralec.ime} ${igralec.priimek}`, besede),
    )
  }, [naVoljo, iskanje])

  const filtri = useFiltri(najdeni, SKUPINE_IGRALCEV, RAZVRSTITVE_IGRALCEV, OBMOCJA_IGRALCEV)

  const naZaslonu = filtri.prikazani.slice(0, NAJVEC_VRSTIC)
  const odrezanih = filtri.prikazani.length - naZaslonu.length

  /* Izbrani, ki jih trenutna merila (ali rez seznama) ne pokažejo. Ti gredo v
     svojo skupino nad seznam: gumb jih bo prijavil, zato morajo biti vidni.
     Brez useMemo - seznam na zaslonu je ob vsakem izrisu nov, zato bi se
     preračun tako ali tako ponovil, dve zanki čez tisoč vrstic pa nista nič. */
  const vidni = new Set(naZaslonu.map((igralec) => igralec.id))
  const skritiIzbrani =
    izbrani.size === 0
      ? []
      : naVoljo.filter((igralec) => izbrani.has(igralec.id) && !vidni.has(igralec.id))

  function preklopi(idIgralca: number) {
    nastaviIzbrane((prejsnji) => {
      const novi = new Set(prejsnji)
      if (novi.has(idIgralca)) novi.delete(idIgralca)
      else novi.add(idIgralca)
      return novi
    })
  }

  const vrstica = (igralec: IgralecDto) => (
    <li key={igralec.id}>
      <label>
        <input
          type="checkbox"
          checked={izbrani.has(igralec.id)}
          onChange={() => preklopi(igralec.id)}
        />
        <span>
          {igralec.ime} {igralec.priimek}
          <span className="seznam-izbire__podrobnost">
            {igralec.klub?.ime ?? 'brez kluba'}
            {igralec.starostniPas !== null && ` · ${OZNAKE_PASU_KRATKO[igralec.starostniPas]}`}
            {igralec.rating !== null && ` · rating ${igralec.rating}`}
          </span>
        </span>
      </label>
    </li>
  )

  /* Praznega seznama sta dva različna vzroka in vsak ima svoj izhod. */
  const nicNiOstalo = naVoljo.length > 0 && filtri.prikazani.length === 0

  return (
    <div className="plosca">
      <div className="naslovna-vrstica">
        <h2 className="sekcija__naslov--manjsi">Dodaj igralce</h2>
        <button
          className="gumb"
          disabled={izbrani.size === 0 || prijavljanje.isPending}
          onClick={() => prijavljanje.mutate([...izbrani])}
        >
          {prijavljanje.isPending ? 'Prijavljam …' : `Prijavi izbrane (${izbrani.size})`}
        </button>
      </div>

      <SporociloNapake napaka={prijavljanje.error} />

      {igralci.isPending && <p className="obvestilo">Nalaganje …</p>}
      {naVoljo.length === 0 && igralci.data && (
        <p className="obvestilo">
          Ni več igralcev, ki bi jih lahko prijavil. Nove lahko dodaš na strani{' '}
          <Link to="/igralci">Igralci</Link>.
        </p>
      )}

      {naVoljo.length > 0 && (
        <>
          {/* Iskalnik stoji NAD krmili in čez vso širino: pri tisoč igralcih je
              to prvo, kar organizator naredi, filtri pa so drugo. */}
          <label className="dodajanje__iskanje">
            <span className="samo-za-bralnik">Išči po imenu ali priimku</span>
            <input
              className="iskalnik"
              type="search"
              value={iskanje}
              onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
              placeholder="Išči po imenu ali priimku"
            />
          </label>

          <KrmilaSeznama
            stanje={filtri}
            razvrstitve={RAZVRSTITVE_IGRALCEV}
            naslovOkna="Dodaj igralce"
            imeZadetkov={sklonIgralcev}
            /* Števec je na 390 px tretji del vrste in stisne izbor
               razvrstitve v "Razvrsti: P ▾"; koliko jih ostane, pove gumb v
               oknu ("Pokaži 211 igralcev") in seznam sam. */
            desno={
              jeTelefon ? undefined : (
                <span className="sekcija__meta">
                  {filtri.prikazani.length} od {naVoljo.length} na voljo
                </span>
              )
            }
          />

          {skritiIzbrani.length > 0 && (
            <div className="dodajanje__izbrani">
              <p className="podnaslov-sekcije">
                Izbrani zunaj seznama · {skritiIzbrani.length}
              </p>
              <ul className="seznam-izbire seznam-izbire--kratek">
                {skritiIzbrani.map(vrstica)}
              </ul>
            </div>
          )}

          {nicNiOstalo ? (
            <p className="obvestilo">
              Iskanju in merilom ne ustreza noben igralec.{' '}
              <button
                type="button"
                className="povezava-gumb"
                onClick={() => {
                  nastaviIskanje('')
                  filtri.pocisti()
                }}
              >
                Počisti iskanje in filtre
              </button>
            </p>
          ) : (
            <ul className="seznam-izbire">{naZaslonu.map(vrstica)}</ul>
          )}

          {odrezanih > 0 && (
            <p className="namig">
              Prikazanih prvih {NAJVEC_VRSTIC} od {filtri.prikazani.length} zadetkov — do
              ostalih se pride z iskanjem ali merili.
            </p>
          )}
        </>
      )}
    </div>
  )
}

/* ---------- Po žrebu: podnavigacija in pogledi ---------- */

function Tekmovanje({
  podatki,
  klik,
  osvezi,
  jeAdmin,
}: {
  podatki: MrezaDto
  klik?: KlikTekme
  osvezi: () => void
  jeAdmin: boolean
}) {
  const jeTelefon = useTelefon()
  const koncan = podatki.dogodek.status === 'ZAKLJUCEN'
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'
  const sistem = podatki.dogodek.sistemTekmovanja
  const izlocilne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  /* Tekma za 3. mesto in tolažilna mreža (uvoženi dogodki) stojita zunaj
     glavne mreže, a v istem pogledu. */
  const tolazilne = podatki.tekme.filter((t) => t.faza === 'TOLAZILNI')
  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  const razvrscenih = podatki.prijave.filter((p) => p.koncnoMesto !== null).length
  const aktivnih = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN').length

  /* Na telefonu je koncna razvrstitev SVOJ zavihek in ne blok nad pasom: nad
     njim bi moral gledalec prevoziti cel seznam, preden bi prisel do zavihkov.
     Pri kroznem sistemu takrat zavihek "Razvrstitev" (ziva lestvica) odpade -
     koncna razvrstitev je ista lestvica, le dokoncna, in dva enako imenovana
     zavihka ne povesta, po cem se razlikujeta. */
  const zakljucekVZavihku = jeTelefon && koncan && razvrscenih > 0

  /* Pas pogledov se odloca po tem, kaj dogodek DEJANSKO ima, in ne po sistemu:
     tako se dogodek s sistemom, ki ga podnavigacija ne pozna, izrise brez
     napake in pokaze samo obstojece poglede. */
  const pogledi = useMemo<PogledGumb[]>(() => {
    const seznam: PogledGumb[] = []
    if (zakljucekVZavihku) {
      seznam.push({ kljuc: 'zakljucek', oznaka: 'Razvrstitev' })
    }
    if (
      (podatki.skupine.length > 0 || podatki.lestvica.length > 0) &&
      !(zakljucekVZavihku && sistem === 'KROZNI')
    ) {
      seznam.push({
        kljuc: 'skupine',
        oznaka: sistem === 'KROZNI' ? 'Razvrstitev' : 'Skupine',
      })
    }
    /* Pri kroznem sistemu stojijo tekme na namizju ob lestvici; pri 390 px
       dva stolpca nista dva stolpca, zato dobijo svoj zavihek. */
    if (jeTelefon && sistem === 'KROZNI' && skupinske.length > 0) {
      seznam.push({ kljuc: 'tekme', oznaka: 'Tekme', stevec: skupinske.length })
    }
    if (
      izlocilne.length > 0 || tolazilne.length > 0
      || sistem === 'IZLOCILNI' || sistem === 'SKUPINE_IZLOCILNI'
    ) {
      seznam.push({ kljuc: 'mreza', oznaka: 'Izločilni del' })
    }
    if (podatki.prijave.length > 0) {
      seznam.push({ kljuc: 'udelezenci', oznaka: 'Udeleženci', stevec: aktivnih })
    }
    return seznam
  }, [
    podatki.skupine.length,
    podatki.lestvica.length,
    podatki.prijave.length,
    izlocilne.length,
    tolazilne.length,
    skupinske.length,
    aktivnih,
    sistem,
    jeTelefon,
    zakljucekVZavihku,
  ])

  const [pogled, nastaviPogled] = useState<PogledDogodka | null>(null)
  /* Privzeti pogled je prvi obstojeci; ce izbrani izgine (npr. po odstopu ali
     ob prehodu na sirsi zaslon), pas ne sme ostati prazen. */
  const izbrani = pogled && pogledi.some((p) => p.kljuc === pogled)
    ? pogled
    : pogledi[0]?.kljuc ?? 'udelezenci'

  /* Na telefonu kartica ne pokaže, da je klikljiva (dotik nima prehoda miške),
     zato namig pove, kaj klik naredi. Točke omeni samo, kadar jih ima vsaj ena
     tekma - gledalcu sicer klik ne naredi ničesar. */
  const zTockami = podatki.tekme.some(imaTocke)
  const namigKlika = ekipno
    ? (koncan
        ? null
        : 'Klikni tekmo za zapisnik srečanja — postavo in izide posamičnih tekem. Izid tekme' +
          ' nastane iz srečanja, zmagovalec napreduje sam.')
    : jeAdmin && !koncan
      ? [
          izbrani === 'mreza'
            ? 'Klikni tekmo z obema znanima igralcema za vnos rezultata. Zmagovalec samodejno napreduje.'
            : 'Klikni tekmo za vnos rezultata. Lestvica skupine se preračuna sproti.',
          zTockami ? 'Odigrana tekma pokaže točke po nizih.' : null,
        ]
          .filter(Boolean)
          .join(' ')
      : zTockami
        ? 'Klikni odigrano tekmo za točke po nizih.'
        : null

  const odigranih =podatki.tekme.filter((t) => t.status === 'KONCANA').length
  const povzetek = [
    podatki.skupine.length > 0
      ? `${podatki.skupine.length} ${sklonSkupin(podatki.skupine.length)}`
      : null,
    podatki.tekme.length > 0 ? `odigranih ${odigranih} / ${podatki.tekme.length}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <>
      {koncan && !zakljucekVZavihku && <Zakljucek podatki={podatki} />}

      <PodnavigacijaDogodka
        pogledi={pogledi}
        izbrani={izbrani}
        naIzbiro={nastaviPogled}
        povzetek={povzetek}
      />

      {izbrani === 'zakljucek' && <Zakljucek podatki={podatki} />}

      {izbrani === 'skupine' &&
        (sistem === 'KROZNI' ? (
          jeTelefon ? (
            <Lestvica vrstice={podatki.lestvica} ekipno={ekipno} />
          ) : (
            <Krozni podatki={podatki} klik={klik} />
          )
        ) : (
          <Skupine podatki={podatki} klik={klik} />
        ))}

      {izbrani === 'tekme' && <TekmePoKolih tekme={skupinske} klik={klik} />}

      {izbrani === 'mreza' && (
        <IzlocilniDel
          tekme={izlocilne}
          tolazilne={tolazilne}
          zaTretjeMesto={podatki.dogodek.tekmaZaTretjeMesto}
          klik={klik}
        />
      )}

      {izbrani === 'udelezenci' && (
        <Udelezenci podatki={podatki} osvezi={osvezi} smem={jeAdmin} />
      )}

      {namigKlika && izbrani !== 'udelezenci' && izbrani !== 'zakljucek' && (
        <p className="namig">{namigKlika}</p>
      )}
    </>
  )
}

/* ---------- Pogled: skupine kot zložljive vrstice ---------- */

function Skupine({
  podatki,
  klik,
}: {
  podatki: MrezaDto
  klik?: KlikTekme
}) {
  /* Odprta je vedno največ ena skupina; klik na isto jo zapre. */
  const [odprta, nastaviOdprto] = useState<number | null>(null)
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'
  /* Format TOP nima izločilnega dela, zato tam nihče ne "napreduje". */
  const napreduje = podatki.dogodek.sistemTekmovanja === 'SKUPINE_IZLOCILNI' ? 2 : undefined

  if (podatki.skupine.length === 0) {
    return <p className="obvestilo">Skupine še niso ustvarjene.</p>
  }

  /* Skupine za mesta (in večstopenjski uvoženi dogodki) imajo več stopenj:
     predtekmovalne skupine, nato skupine, ki odločajo mesta. Brez naslovov bi
     »A« in »1.–4. mesto« stala v istem seznamu, kot da sta ista vrsta.
     Skupine za mesta gredo pod en naslov ne glede na stopnjo: vir (Stupa) ima
     vsako od njih za svojo fazo, zato bi »1.–4.« in »5.–8. mesto« sicer stali
     pod različnima naslovoma, čeprav se igrata vzporedno. */
  const zaMesta = podatki.skupine
    .filter((s) => s.stopnja > 1 && s.prvoMesto !== null)
    .sort((a, b) => (a.prvoMesto ?? 0) - (b.prvoMesto ?? 0))
  const ostale = podatki.skupine.filter((s) => !zaMesta.includes(s))
  const stopnje = [...new Set(ostale.map((s) => s.stopnja))].sort((a, b) => a - b)
  const sekcije = stopnje.map((stopnja) => ({
    kljuc: String(stopnja),
    naslov: stopnja === 1 ? 'Predtekmovanje' : `${stopnja}. stopnja`,
    skupine: ostale.filter((s) => s.stopnja === stopnja),
  }))
  if (zaMesta.length > 0) {
    sekcije.push({ kljuc: 'mesta', naslov: 'Finalne skupine', skupine: zaMesta })
  } else if (sekcije.length > 1) {
    sekcije[sekcije.length - 1].naslov = 'Finalne skupine'
  }

  return (
    <div>
      <div className="seznam-glava seznam-glava--skupine">
        <span>Skupina</span>
        <span>{ekipno ? 'Ekipe' : 'Igralci'}</span>
        <span className="seznam-glava__desno">Podrobno</span>
      </div>
      {sekcije.map((sekcija) => (
        <div key={sekcija.kljuc}>
          {sekcije.length > 1 && <p className="podnaslov-sekcije">{sekcija.naslov}</p>}
          <div className="skupine-seznam">
            {sekcija.skupine.map((skupina) => (
              <SkupinaVrstica
                key={skupina.id}
                oznaka={skupina.oznaka}
                opis={opisSkupine(skupina, ekipno)}
                odprta={odprta === skupina.id}
                naPreklop={() =>
                  nastaviOdprto((prej) => (prej === skupina.id ? null : skupina.id))
                }
              >
                <VsebinaSkupine
                  skupina={skupina}
                  tekme={podatki.tekme.filter(
                    (t) => t.faza === 'SKUPINA' && t.idSkupina === skupina.id,
                  )}
                  klik={klik}
                  napreduje={napreduje}
                  ekipno={ekipno}
                />
              </SkupinaVrstica>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

/* »4 igralci« oz. pri skupini za mesta »5.–8. mesto · 4 ekipe«. */
function opisSkupine(skupina: SkupinaDto, ekipno: boolean): string {
  const n = skupina.lestvica.length
  const clani = `${n} ${ekipno ? sklonEkip(n) : sklonIgralcev(n)}`
  return skupina.ime ? `${skupina.ime} · ${clani}` : clani
}

function VsebinaSkupine({
  skupina,
  tekme,
  klik,
  napreduje,
  ekipno,
}: {
  skupina: SkupinaDto
  tekme: TekmaDto[]
  klik?: KlikTekme
  napreduje?: number
  ekipno: boolean
}) {
  /* Tekme so razdeljene po kolih tako kot pri krožnem sistemu: brez tega je
     skupina osmih igralcev en sam seznam 28 vrstic, iz katerega ni razvidno,
     kaj je bilo odigrano skupaj in kaj šele pride. */
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <>
      <div>
        <Lestvica vrstice={skupina.lestvica} napreduje={napreduje} strnjena ekipno={ekipno} />
        {napreduje !== undefined && (
          <div className="legenda">
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--napreduje" />
              napredujeta v izločilni del
            </span>
          </div>
        )}
      </div>
      <div>
        {kola.map((kolo) => (
          <div key={kolo} className="kolo-skupina">
            <div className="kolo-skupina__naslov">{kolo}. kolo</div>
            <TekmeSeznam tekme={tekme.filter((t) => t.kolo === kolo)} klik={klik} strnjen />
          </div>
        ))}
      </div>
    </>
  )
}

/* ---------- Pogled: izločilni del ---------- */

/* Mreža 64 igralcev se ne izriše naenkrat: izbrano kolo je prvi stolpec,
   naslednja kola stojijo desno od njega. Prehod čez ime osvetli isto ime v
   vseh kolih - tako se pot igralca prebere brez klika. */
function IzlocilniDel({
  tekme,
  tolazilne,
  zaTretjeMesto,
  klik,
}: {
  tekme: TekmaDto[]
  tolazilne: TekmaDto[]
  zaTretjeMesto: boolean
  klik?: KlikTekme
}) {
  const kola = useMemo(() => kolaMreze(tekme), [tekme])
  /* Privzeto najzgodnejše kolo, ki še ni v celoti odigrano - tam se turnir
     dogaja. Ko je vse odigrano, ostane prvo kolo. */
  const privzeto =
    kola.find((k) => k.tekme.some((t) => t.status !== 'KONCANA'))?.kolo ?? kola[0]?.kolo ?? 1
  const [izbrano, nastaviIzbrano] = useState<number | null>(null)
  const [osvetljena, nastaviOsvetljeno] = useState<number | null>(null)

  if (kola.length === 0) {
    return tolazilne.length > 0 ? (
      <TolazilniDel tekme={tolazilne} zaTretjeMesto={zaTretjeMesto} klik={klik} />
    ) : (
      <p className="obvestilo">
        Izločilni del se samodejno zažene, ko so odigrane vse tekme skupin
        (napredujeta po dva iz vsake skupine).
      </p>
    )
  }

  const koloZaPrikaz = izbrano !== null && izbrano <= kola.length ? izbrano : privzeto

  return (
    <div>
      <div className="mreza-krmar">
        <div className="izbirnik">
          {kola.map((k) => (
            <button
              type="button"
              key={k.kolo}
              className={
                'izbirnik__gumb' + (koloZaPrikaz === k.kolo ? ' izbirnik__gumb--aktiven' : '')
              }
              aria-pressed={koloZaPrikaz === k.kolo}
              onClick={() => nastaviIzbrano(k.kolo)}
            >
              {imeKolaKratko(k.kolo, kola.length)} · {k.tekme.length}
            </button>
          ))}
        </div>
        <span className="sekcija__meta mreza-krmar__opomba">
          miška nad igralcem osvetli njegovo pot
        </span>
      </div>
      <Mreza
        tekme={tekme}
        klik={klik}
        odKola={koloZaPrikaz}
        osvetljenaPrijava={osvetljena}
        naOsvetlitev={nastaviOsvetljeno}
      />
      {tolazilne.length > 0 && (
        <TolazilniDel tekme={tolazilne} zaTretjeMesto={zaTretjeMesto} klik={klik} />
      )}
    </div>
  )
}

/* Tekme zunaj glavne mreže: tekma za 3. mesto (poraženca polfinalov) ali
   tolažilna mreža uvoženega dogodka. Kola se ne imenujejo po glavni mreži -
   tekma za 3. mesto nosi kolo finala, pa vendar ni finale. */
function TolazilniDel({
  tekme,
  zaTretjeMesto,
  klik,
}: {
  tekme: TekmaDto[]
  zaTretjeMesto: boolean
  klik?: KlikTekme
}) {
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  const samoZaTretje = zaTretjeMesto && tekme.length === 1
  return (
    <div>
      <p className="podnaslov-sekcije">{samoZaTretje ? 'Za 3. mesto' : 'Tolažilni del'}</p>
      <div className="mreza mreza--tolazilna">
        {kola.map((kolo) => (
          <div className="mreza__kolo" key={kolo}>
            {!samoZaTretje && <div className="mreza__naslov-kola">{kolo}. kolo</div>}
            <div className="mreza__tekme">
              {tekme
                .filter((t) => t.kolo === kolo)
                .sort((a, b) => a.pozicija - b.pozicija)
                .map((t) => (
                  <TekmaKartica key={t.id} tekma={t} klik={klik} />
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ---------- Pogled: udeleženci ---------- */

/* Kdo igra, kdo je rezerva (ni prišel v izbor formata TOP) in kdo je odstopil.
   Odstop je nepovraten, zato gre prek potrditvenega okna. */
function Udelezenci({
  podatki,
  osvezi,
  smem,
}: {
  podatki: MrezaDto
  osvezi: () => void
  /* Sme urejati (lastnik ali admin, dogodek ni uvožen). */
  smem: boolean
}) {
  const [odstopnik, nastaviOdstopnika] = useState<PrijavaDto | null>(null)
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'
  /* Odstop je dejanje med tekmovanjem; po koncu ni česa več predati. */
  const smemOdstop = smem && podatki.dogodek.status !== 'ZAKLJUCEN'

  const odstop = useMutation({
    mutationFn: (idPrijave: number) => dogodkiApi.odstop(idPrijave),
    onSuccess: () => {
      osvezi()
      nastaviOdstopnika(null)
    },
  })

  const rezerve = podatki.prijave.filter((p) => p.status === 'REZERVA')
  const odstopili = podatki.prijave.filter((p) => p.status === 'ODSTOPIL')
  const igrajo = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')

  return (
    <>
      {/* Pri ekipah je udeleženec ekipa s kadrom: kdo je igral za katero
          ekipo, je del zapisa tekmovanja, zato seznam ekip nosi kadre. */}
      {ekipno ? (
        <EkipeDogodka
          dogodek={podatki.dogodek}
          smem={smem}
          dejanje={(ekipa) => {
            const prijava = igrajo.find((p) => p.idEkipa === ekipa.id)
            return smemOdstop && prijava ? (
              <button
                type="button"
                className="gumb gumb--majhen"
                onClick={() => nastaviOdstopnika(prijava)}
              >
                Odstopila
              </button>
            ) : null
          }}
        />
      ) : (
        <SeznamPrijavljenih
          naslov="Udeleženci"
          prijave={igrajo}
          dejanje={
            smemOdstop
              ? (prijava) => (
                  <button
                    type="button"
                    className="prijava-vrstica__dejanje"
                    onClick={() => nastaviOdstopnika(prijava)}
                  >
                    Odstopil
                  </button>
                )
              : undefined
          }
        />
      )}

      {rezerve.length > 0 && (
        <div className="plosca">
          <h2 className="sekcija__naslov--manjsi">Rezerve ({rezerve.length})</h2>
          <p className="namig">
            Niso prišli v izbor najboljših. Vrstni red pove, kdo je bil prvi pod črto.
          </p>
          <ul className="seznam-preprost">
            {rezerve.map((p) => (
              <li key={p.id}>
                {p.stNosilca !== null && <strong>{p.stNosilca}. </strong>}
                {p.polnoIme}
                {p.klub && <span className="izbor__podrobnost">{p.klub}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {odstopili.length > 0 && (
        <div className="plosca">
          <h2 className="sekcija__naslov--manjsi">Odstopili ({odstopili.length})</h2>
          <p className="namig">
            Njihove odigrane tekme obveljajo, preostale so dobili nasprotniki brez
            boja (te ne štejejo v rating).
          </p>
          <ul className="seznam-preprost">
            {odstopili.map((p) => (
              <li key={p.id}>
                {p.polnoIme}
                <span className="izbor__podrobnost">{OZNAKE_STATUS_PRIJAVE[p.status]}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <SporociloNapake napaka={odstop.error} />

      {odstopnik && (
        <PotrditvenoOkno
          naslov={ekipno ? 'Odstop ekipe' : 'Odstop igralca'}
          sporocilo={
            `${ekipno ? `Ekipa ${odstopnik.polnoIme}` : odstopnik.polnoIme} odstopi s tekmovanja?` +
            ` Že odigrane tekme obveljajo, vse preostale pa dobijo nasprotniki brez boja. Ker te` +
            ` tekme niso bile odigrane, se ne štejejo v rating. Dejanja ni mogoče razveljaviti.`
          }
          besedaPotrditve="Potrdi odstop"
          onPotrdi={() => odstop.mutate(odstopnik.id)}
          onZapri={() => nastaviOdstopnika(null)}
        />
      )}
    </>
  )
}

/* ---------- Krožni sistem: skupna lestvica + tekme po kolih ---------- */

function Krozni({
  podatki,
  klik,
}: {
  podatki: MrezaDto
  klik?: KlikTekme
}) {
  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  return (
    <div className="dvostolpicno dvostolpicno--lestvica">
      <div className="plosca">
        <h2 className="sekcija__naslov--manjsi">Lestvica</h2>
        <Lestvica vrstice={podatki.lestvica} ekipno={podatki.dogodek.disciplina === 'EKIPNO'} />
      </div>
      <div className="plosca">
        <h2 className="sekcija__naslov--manjsi">Tekme</h2>
        <TekmePoKolih tekme={skupinske} klik={klik} />
      </div>
    </div>
  )
}

/* Tekme, razdeljene po kolih. Brez tega je krožni sistem z 12 igralci en sam
   seznam 66 vrstic, iz katerega ni razvidno, kaj je bilo odigrano skupaj.
   Na telefonu je to samostojen pogled (zavihek »Tekme«). */
function TekmePoKolih({
  tekme,
  klik,
}: {
  tekme: TekmaDto[]
  klik?: KlikTekme
}) {
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <>
      {kola.map((kolo) => (
        <div key={kolo} className="kolo-skupina">
          <div className="kolo-skupina__naslov">{kolo}. kolo</div>
          <TekmeSeznam tekme={tekme.filter((t) => t.kolo === kolo)} klik={klik} />
        </div>
      ))}
    </>
  )
}

/* ---------- Zaključen dogodek: razvrstitev in končni vrstni red ---------- */

/* Bilanca ene prijave: zmage, porazi in vsota sprememb Turnirko ratinga.
   Sešteta je iz že prenesenih tekem - nova poizvedba ni potrebna. */
interface Bilanca {
  zmage: number
  porazi: number
  rating: number | null
}

function bilancePrijav(tekme: TekmaDto[]): Map<number, Bilanca> {
  const bilance = new Map<number, Bilanca>()
  const vzemi = (idPrijave: number): Bilanca => {
    let bilanca = bilance.get(idPrijave)
    if (!bilanca) {
      bilanca = { zmage: 0, porazi: 0, rating: null }
      bilance.set(idPrijave, bilanca)
    }
    return bilanca
  }

  for (const tekma of tekme) {
    /* Prosti prehod ni odigrana tekma in ne sme v izkupiček. */
    if (tekma.status !== 'KONCANA' || tekma.izidTip === 'PROSTO') continue
    const strani: [number | undefined, number | null][] = [
      [tekma.udelezenec1?.idPrijave, tekma.spremembaElo1],
      [tekma.udelezenec2?.idPrijave, tekma.spremembaElo2],
    ]
    for (const [idPrijave, sprememba] of strani) {
      if (idPrijave === undefined) continue
      const bilanca = vzemi(idPrijave)
      if (tekma.idZmagovalcaPrijave === idPrijave) bilanca.zmage += 1
      else if (tekma.idZmagovalcaPrijave !== null) bilanca.porazi += 1
      if (sprememba !== null) bilanca.rating = (bilanca.rating ?? 0) + sprememba
    }
  }
  return bilance
}

/* "+38" / "−18" / "—" - sprememba Turnirko ratinga čez cel dogodek. */
function oznakaRatinga(rating: number | null): string {
  if (rating === null) return '—'
  return rating > 0 ? `+${rating}` : rating < 0 ? `−${Math.abs(rating)}` : '0'
}

function razredRatinga(rating: number | null, osnova: string): string {
  if (rating === null || rating === 0) return osnova
  return osnova + (rating > 0 ? ` ${osnova}--poz` : ` ${osnova}--neg`)
}

function Zakljucek({ podatki }: { podatki: MrezaDto }) {
  const jeTelefon = useTelefon()
  /* Ekipa ratinga nima (v rating gredo posamične tekme njenih srečanj), zato
     pri ekipah stolpec ratinga odpade in bilanca so zmage in porazi tekem. */
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'
  const razvrscene = useMemo(
    () =>
      podatki.prijave
        .filter((prijava) => prijava.koncnoMesto !== null)
        .sort((prva, druga) => prva.koncnoMesto! - druga.koncnoMesto!),
    [podatki.prijave],
  )
  const bilance = useMemo(() => bilancePrijav(podatki.tekme), [podatki.tekme])

  if (razvrscene.length === 0) return null

  const vrh = razvrscene.slice(0, 3)
  const ostali = razvrscene.slice(3)
  /* Pri 24 igralcih je to 12 vrstic na stolpec; nad 40 gredo trije stolpci. */
  const stolpcev = ostali.length > 40 ? 3 : 2
  const stolpci = razdeli(ostali, stolpcev)
  /* V ozki vrstici telefona je "11–0" en podatek, na namizju "11 – 0". */
  const locilo = jeTelefon ? '–' : ' – '

  return (
    <div>
      <div className={jeTelefon ? 'naslovna-mobi naslovna-mobi--brez-crte' : 'naslovna-vrstica'}>
        <h2>Končna razvrstitev</h2>
        <span className={jeTelefon ? 'naslovna-mobi__stevec naslovna-mobi__stevec--drobno' : 'sekcija__meta'}>
          {razvrscene.length}{' '}
          {ekipno ? sklonEkip(razvrscene.length) : sklonIgralcev(razvrscene.length)}
          {!jeTelefon && (ekipno ? ' · bilanca' : ' · bilanca · rating')}
        </span>
      </div>

      {/* Mesta so številke, ne medalje - odličje nosi barva črte ob levem robu
          (zelena zmagovalec, modra drugi, črnilo tretji). */}
      <div className={'razvrstitev__vrh' + (vrh.length === 2 ? ' razvrstitev__vrh--dve' : '')}>
        {vrh.map((prijava, indeks) => {
          const bilanca = bilance.get(prijava.id)
          const rating = bilanca?.rating ?? null
          return (
            <div className={`razvrstitev__mesto razvrstitev__mesto--${indeks + 1}`} key={prijava.id}>
              <span className="razvrstitev__stevilka">{prijava.koncnoMesto}</span>
              <span className="razvrstitev__oseba">
                <span className="razvrstitev__ime">{prijava.polnoIme}</span>
                <span className="razvrstitev__klub">
                  {[podnapisPrijave(prijava, ekipno), ekipno ? null : prijava.rating]
                    .filter((d) => d !== null)
                    .join(' · ')}
                </span>
              </span>
              <span className="razvrstitev__izid">
                <span className="razvrstitev__bilanca">
                  {bilanca ? `${bilanca.zmage}${locilo}${bilanca.porazi}` : '—'}
                </span>
                {!ekipno && (
                  <span className={razredRatinga(rating, 'razvrstitev__rating')}>{oznakaRatinga(rating)}</span>
                )}
              </span>
            </div>
          )
        })}
      </div>

      {ostali.length > 0 && (
        <div className={'vrstni-red' + (stolpcev === 3 ? ' vrstni-red--trije' : '')}>
          {stolpci.map((stolpec, indeks) => (
            <div key={indeks}>
              {stolpec.map((prijava) => {
                const bilanca = bilance.get(prijava.id)
                const rating = bilanca?.rating ?? null
                return (
                  <div className="vrstni-red__vrstica" key={prijava.id}>
                    <span className="vrstni-red__mesto">
                      {prijava.koncnoMesto}
                      {!jeTelefon && '.'}
                    </span>
                    <span className="vrstni-red__ime">
                      {prijava.polnoIme}
                      <span className="vrstni-red__klub">{podnapisPrijave(prijava, ekipno)}</span>
                    </span>
                    <span className="vrstni-red__izkupicek">
                      {bilanca ? `${bilanca.zmage}${locilo}${bilanca.porazi}` : '—'}
                    </span>
                    {/* Pri ekipi celica ostane (prazna), da stolpci vrstice
                        ostanejo poravnani z mrežo razreda. */}
                    <span className={ekipno ? 'vrstni-red__rating' : razredRatinga(rating, 'vrstni-red__rating')}>
                      {ekipno ? '' : oznakaRatinga(rating)}
                    </span>
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
