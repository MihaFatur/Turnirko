/* Stran enega dogodka - osrednji delovni prostor.

   V pripravi (admin): urejanje prijav in izvedba žreba; gost vidi le seznam.
   Pri formatu TOP (sistem skupine po jakosti) je v pripravi še urejanje
   jakostnega vrstnega reda s črto reza - po njem tečeta izbor in razporeditev.

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

   Vnos rezultata je mogoč samo administratorju (oz. lastniku turnirja). */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi, igralciApi, turnirjiApi } from '../api/zahteve'
import type { IzborDto, MrezaDto, PrijavaDto, SkupinaDto, TekmaDto } from '../api/tipi'
import { OZNAKE_SISTEM_KRATKO, OZNAKE_SPOL_KATEGORIJA, OZNAKE_STATUS_PRIJAVE } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GlavaDejanja, GlavaNaslov, useNazaj } from '../komponente/GlavaTelefona'
import { Lestvica } from '../komponente/Lestvica'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { Mreza, kolaMreze } from '../komponente/Mreza'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import {
  PodnavigacijaDogodka,
  type PogledDogodka,
  type PogledGumb,
} from '../komponente/PodnavigacijaDogodka'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SkupinaVrstica } from '../komponente/SkupinaVrstica'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { TekmeSeznam } from '../komponente/TekmeSeznam'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import { ZnackaStatusa, ZnackaVNaslovu } from '../komponente/Znacka'
import {
  imeKolaKratko,
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

  /* Tekma, za katero je odprto okno za vnos rezultata. */
  const [izbranaTekma, nastaviIzbranoTekmo] = useState<TekmaDto | null>(null)
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
  // organizator sme upravljati dogodke svojega (ali klubskega) turnirja
  const smem = jeAdmin
    || (!!turnir.data && smemUrejati(turnir.data.idLastnik, turnir.data.idKlubLastnik))

  const vPripravi = dogodek.status === 'PRIPRAVA'
  const aktivnePrijave = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')
  /* Zadržek pove strežnik (npr. zadnja skupina bi imela enega igralca),
     da vmesnik ne podvaja pravil razreza. */
  const zadrzek = podatki.izbor?.zadrzek ?? null
  const zrebOnemogocen = aktivnePrijave.length < 2 || zadrzek !== null || zreb.isPending

  /* "Ženske · do 21 let · krožni · na 5 nizov" - lastnosti dogodka v enem
     stavku; enak vrstni red na obeh širinah. */
  const lastnosti = [
    OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija],
    dogodek.starostnaKategorija,
    OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja].toLowerCase(),
    `na ${dogodek.privzetoSteviloNizov} ${sklonNizov(dogodek.privzetoSteviloNizov)}`,
  ]
    .filter(Boolean)
    .join(' · ')

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
                      {aktivnePrijave.length < 2
                        ? 'Za žreb sta potrebna vsaj 2 igralca.'
                        : zadrzek}
                    </p>
                  )}
                  {smem && dogodek.status === 'V_TEKU' && (
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
            </div>
            <div className="naslovna-vrstica__desno">
              {smem && dogodek.status === 'V_TEKU' && (
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
                    aktivnePrijave.length < 2
                      ? 'Za žreb sta potrebna vsaj 2 igralca.'
                      : (zadrzek ?? undefined)
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
        <Tekmovanje
          podatki={podatki}
          naKlikTekme={smem ? nastaviIzbranoTekmo : undefined}
          osvezi={osvezi}
          jeAdmin={smem}
        />
      )}

      {potrjujemZreb && (
        <PotrditvenoOkno
          naslov="Izvedba žreba"
          sporocilo={
            podatki.izbor
              ? `Po žrebu prijav in vrstnega reda ni več mogoče spreminjati.` +
                ` Igralo bo najboljših ${podatki.izbor.igra} od ${podatki.izbor.prijavljenih} prijavljenih,` +
                ` ostali postanejo rezerve. Izvedem žreb?`
              : 'Po žrebu prijav ni več mogoče spreminjati. Izvedem žreb?'
          }
          besedaPotrditve="Izvedi žreb"
          onPotrdi={() => zreb.mutate()}
          onZapri={() => nastaviPotrjujemZreb(false)}
        />
      )}

      {izbranaTekma && (
        <VnosRezultataOkno
          tekma={izbranaTekma}
          onZapri={() => nastaviIzbranoTekmo(null)}
          onShranjeno={osvezi}
        />
      )}
    </section>
  )
}

/* ---------- Seznam prijavljenih (priprava in pogled Udeleženci) ---------- */

/* Cilj je 100 prijavljenih na enem zaslonu prenosnika brez straničenja: do 24
   en stolpec, 25-120 dva, nad 120 trije. Iskanje in filter kluba delujeta na
   celoten seznam in ga znova razdelita, zato se stolpci vedno enako napolnijo.
   Številka pred imenom je jakostno mesto, ki ga bo uporabil žreb. */
function SeznamPrijavljenih({
  naslov,
  prijave,
  dejanje,
}: {
  naslov: string
  prijave: PrijavaDto[]
  dejanje?: (prijava: PrijavaDto) => ReactNode
}) {
  const [iskanje, nastaviIskanje] = useState('')
  const [klub, nastaviKlub] = useState('vsi')

  /* Razvrstitev po ratingu navzdol; brez ratinga na dno, da jih človek opazi. */
  const urejene = useMemo(
    () =>
      [...prijave].sort((prva, druga) => (druga.rating ?? -1) - (prva.rating ?? -1)),
    [prijave],
  )

  const klubi = useMemo(() => {
    const stevci = new Map<string, number>()
    for (const prijava of urejene) {
      const ime = prijava.klub ?? 'brez kluba'
      stevci.set(ime, (stevci.get(ime) ?? 0) + 1)
    }
    return [...stevci.entries()].sort((prva, druga) => prva[0].localeCompare(druga[0], 'sl'))
  }, [urejene])

  const prikazane = useMemo(() => {
    const iskano = iskanje.trim().toLowerCase()
    return urejene
      .map((prijava, indeks) => ({ prijava, mesto: indeks + 1 }))
      .filter(({ prijava }) => {
        if (klub !== 'vsi' && (prijava.klub ?? 'brez kluba') !== klub) return false
        return !iskano || prijava.polnoIme.toLowerCase().includes(iskano)
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
            ? 'Ni še prijavljenih igralcev.'
            : 'Noben prijavljeni ne ustreza iskanju.'}
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
                <span>Igralec</span>
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
                    {prijava.polnoIme}
                    <span className="prijava-vrstica__klub">
                      {' · '}
                      {prijava.klub ?? 'brez kluba'}
                    </span>
                  </span>
                  <span className="prijava-vrstica__rating">{prijava.rating ?? '—'}</span>
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
  const aktivne = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')
  return (
    <>
      <SeznamPrijavljenih naslov="Prijavljeni" prijave={aktivne} />
      <p className="namig">Žreb izvede administrator (sodnik) po prijavi.</p>
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

  const odjava = useMutation({
    mutationFn: (idPrijave: number) => dogodkiApi.odjavi(idPrijave),
    onSuccess: osvezi,
  })

  /* Seznam prijavljenih dobi vso širino okvirja: glavni cilj tega zaslona je
     100 prijavljenih na enem zaslonu prenosnika brez straničenja, kar z blokom
     ob strani ne gre. Blok "Dodaj igralce" zato stoji pod njim. */
  return (
    <>
      <SporociloNapake napaka={odjava.error} />

      {izbor ? (
        <div>
          <div className="naslovna-vrstica">
            <h2>Jakostni vrstni red</h2>
            <span className="sekcija__meta">
              {aktivnePrijave.length} {sklonPrijavljenih(aktivnePrijave.length)}
            </span>
          </div>
          {aktivnePrijave.length === 0 ? (
            <p className="obvestilo">Ni še prijavljenih igralcev.</p>
          ) : (
            <JakostniVrstniRed
              prijave={aktivnePrijave}
              izbor={izbor}
              idDogodka={idDogodka}
              osvezi={osvezi}
              onOdjava={(id) => odjava.mutate(id)}
            />
          )}
        </div>
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

      <DodajanjeIgralcev podatki={podatki} idDogodka={idDogodka} osvezi={osvezi} />
    </>
  )
}

/* Format TOP: seznam prijavljenih po jakosti s črto reza.
   Vrstni red odloča izbor IN skupino, zato je edino, kar se ureja.
   Premikanje je s puščicama (zanesljivo tudi na dotik in s tipkovnico). */
function JakostniVrstniRed({
  prijave,
  izbor,
  idDogodka,
  osvezi,
  onOdjava,
}: {
  prijave: PrijavaDto[]
  izbor: IzborDto
  idDogodka: number
  osvezi: () => void
  onOdjava: (idPrijave: number) => void
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

  /* Katera skupina pripada mestu (1-based) po predogledu strežnika. */
  function skupinaZaMesto(mesto: number): string | null {
    return izbor.skupine.find((s) => mesto >= s.odMesta && mesto <= s.doMesta)?.oznaka ?? null
  }

  return (
    <>
      <p className="izbor__povzetek">
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
      </p>

      {izbor.zadrzek && <p className="obvestilo obvestilo--opozorilo">{izbor.zadrzek}</p>}

      <SporociloNapake napaka={shranjevanje.error} />

      <ol className="izbor">
        {vrstni.map((prijava, indeks) => {
          const mesto = indeks + 1
          const rezerva = mesto > izbor.igra
          const skupina = rezerva ? null : skupinaZaMesto(mesto)
          return (
            <li key={prijava.id}>
              {mesto === izbor.igra + 1 && (
                <div className="izbor__crta">
                  <span>črta reza — spodnji so rezerve</span>
                </div>
              )}
              <div className={`izbor__vrstica${rezerva ? ' izbor__vrstica--rezerva' : ''}`}>
                <span className="izbor__mesto">{mesto}.</span>
                {skupina && <span className="izbor__skupina">{skupina}</span>}
                <span className="izbor__ime">
                  {prijava.polnoIme}
                  <span className="izbor__podrobnost">
                    {prijava.klub ?? 'brez kluba'}
                    {prijava.rating !== null ? ` · ${prijava.rating}` : ''}
                  </span>
                </span>
                {prijava.rating === null && (
                  <span className="znacka znacka--opozorilo">brez ratinga</span>
                )}
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
                  <button
                    className="gumb gumb--majhen"
                    title="Odjavi igralca"
                    onClick={() => onOdjava(prijava.id)}
                  >
                    Odjavi
                  </button>
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
          Predlog je razvrščen po klubskem ELO; igralci brez ratinga so na vrhu,
          da jih uvrstiš sam. Vrstni red določa tudi skupino.
        </p>
      )}
    </>
  )
}

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

  const prijavljanje = useMutation({
    mutationFn: (idji: number[]) => dogodkiApi.prijaviIgralce(idDogodka, idji),
    onSuccess: () => {
      nastaviIzbrane(new Set())
      osvezi()
    },
  })

  /* Na voljo so igralci, ki na ta dogodek se nimajo aktivne prijave in ki po
     spolu ustrezajo kategoriji dogodka. Odjavljeni (ODJAVLJEN) se spet
     pojavijo - ponovna prijava aktivira njihov obstojeci zapis. */
  const naVoljo = useMemo(() => {
    if (!igralci.data) return []
    const zePrijavljeni = new Set(
      podatki.prijave
        .filter((prijava) => prijava.status !== 'ODJAVLJEN')
        .map((prijava) => prijava.idIgralca),
    )
    return igralci.data.filter((igralec) => {
      if (zePrijavljeni.has(igralec.id)) return false
      if (podatki.dogodek.spolKategorija === 'MOSKI') return igralec.spol === 'MOSKI'
      if (podatki.dogodek.spolKategorija === 'ZENSKE') return igralec.spol === 'ZENSKI'
      return true
    })
  }, [igralci.data, podatki])

  function preklopi(idIgralca: number) {
    nastaviIzbrane((prejsnji) => {
      const novi = new Set(prejsnji)
      if (novi.has(idIgralca)) novi.delete(idIgralca)
      else novi.add(idIgralca)
      return novi
    })
  }

  return (
    <div className="plosca">
      <div className="naslovna-vrstica">
        <h2 className="sekcija__naslov--manjsi">Dodaj igralce</h2>
        <button
          className="gumb"
          disabled={izbrani.size === 0 || prijavljanje.isPending}
          onClick={() => prijavljanje.mutate([...izbrani])}
        >
          Prijavi izbrane ({izbrani.size})
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

      <ul className="seznam-izbire">
        {naVoljo.map((igralec) => (
          <li key={igralec.id}>
            <label>
              <input
                type="checkbox"
                checked={izbrani.has(igralec.id)}
                onChange={() => preklopi(igralec.id)}
              />
              <span>
                {igralec.priimek} {igralec.ime}
                <span className="seznam-izbire__podrobnost">
                  {igralec.klub?.ime ?? 'brez kluba'}
                  {igralec.rating !== null && ` · rating ${igralec.rating}`}
                </span>
              </span>
            </label>
          </li>
        ))}
      </ul>
    </div>
  )
}

/* ---------- Po žrebu: podnavigacija in pogledi ---------- */

function Tekmovanje({
  podatki,
  naKlikTekme,
  osvezi,
  jeAdmin,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
  osvezi: () => void
  jeAdmin: boolean
}) {
  const jeTelefon = useTelefon()
  const koncan = podatki.dogodek.status === 'ZAKLJUCEN'
  const naKlik = koncan ? undefined : naKlikTekme
  const sistem = podatki.dogodek.sistemTekmovanja
  const izlocilne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
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
    if (izlocilne.length > 0 || sistem === 'IZLOCILNI' || sistem === 'SKUPINE_IZLOCILNI') {
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

  const odigranih = podatki.tekme.filter((t) => t.status === 'KONCANA').length
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
            <Lestvica vrstice={podatki.lestvica} />
          ) : (
            <Krozni podatki={podatki} naKlikTekme={naKlik} />
          )
        ) : (
          <Skupine podatki={podatki} naKlikTekme={naKlik} />
        ))}

      {izbrani === 'tekme' && <TekmePoKolih tekme={skupinske} naKlikTekme={naKlik} />}

      {izbrani === 'mreza' && <IzlocilniDel tekme={izlocilne} naKlikTekme={naKlik} />}

      {izbrani === 'udelezenci' && (
        <Udelezenci podatki={podatki} osvezi={osvezi} jeAdmin={jeAdmin && !koncan} />
      )}

      {!koncan && naKlik && (
        <p className="namig">
          {izbrani === 'mreza'
            ? 'Klikni tekmo z obema znanima igralcema za vnos rezultata. Zmagovalec samodejno napreduje.'
            : 'Klikni tekmo za vnos rezultata. Lestvica skupine se preračuna sproti.'}
        </p>
      )}
    </>
  )
}

/* ---------- Pogled: skupine kot zložljive vrstice ---------- */

function Skupine({
  podatki,
  naKlikTekme,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  /* Odprta je vedno največ ena skupina; klik na isto jo zapre. */
  const [odprta, nastaviOdprto] = useState<string | null>(null)
  /* Format TOP nima izločilnega dela, zato tam nihče ne "napreduje". */
  const napreduje = podatki.dogodek.sistemTekmovanja === 'SKUPINE_IZLOCILNI' ? 2 : undefined

  if (podatki.skupine.length === 0) {
    return <p className="obvestilo">Skupine še niso ustvarjene.</p>
  }

  return (
    <div>
      <div className="seznam-glava seznam-glava--skupine">
        <span>Skupina</span>
        <span>Igralci</span>
        <span className="seznam-glava__desno">Podrobno</span>
      </div>
      <div className="skupine-seznam">
        {podatki.skupine.map((skupina) => (
          <SkupinaVrstica
            key={skupina.id}
            oznaka={skupina.oznaka}
            steviloIgralcev={skupina.lestvica.length}
            odprta={odprta === skupina.oznaka}
            naPreklop={() =>
              nastaviOdprto((prej) => (prej === skupina.oznaka ? null : skupina.oznaka))
            }
          >
            <VsebinaSkupine
              skupina={skupina}
              tekme={podatki.tekme.filter(
                (t) => t.faza === 'SKUPINA' && t.idSkupina === skupina.id,
              )}
              naKlikTekme={naKlikTekme}
              napreduje={napreduje}
            />
          </SkupinaVrstica>
        ))}
      </div>
    </div>
  )
}

function VsebinaSkupine({
  skupina,
  tekme,
  naKlikTekme,
  napreduje,
}: {
  skupina: SkupinaDto
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
  napreduje?: number
}) {
  /* Tekme so razdeljene po kolih tako kot pri krožnem sistemu: brez tega je
     skupina osmih igralcev en sam seznam 28 vrstic, iz katerega ni razvidno,
     kaj je bilo odigrano skupaj in kaj šele pride. */
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <>
      <div>
        <Lestvica vrstice={skupina.lestvica} napreduje={napreduje} strnjena />
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
            <TekmeSeznam
              tekme={tekme.filter((t) => t.kolo === kolo)}
              naKlikTekme={naKlikTekme}
              strnjen
            />
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
  naKlikTekme,
}: {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const kola = useMemo(() => kolaMreze(tekme), [tekme])
  /* Privzeto najzgodnejše kolo, ki še ni v celoti odigrano - tam se turnir
     dogaja. Ko je vse odigrano, ostane prvo kolo. */
  const privzeto =
    kola.find((k) => k.tekme.some((t) => t.status !== 'KONCANA'))?.kolo ?? kola[0]?.kolo ?? 1
  const [izbrano, nastaviIzbrano] = useState<number | null>(null)
  const [osvetljena, nastaviOsvetljeno] = useState<number | null>(null)

  if (kola.length === 0) {
    return (
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
        naKlikTekme={naKlikTekme}
        odKola={koloZaPrikaz}
        osvetljenaPrijava={osvetljena}
        naOsvetlitev={nastaviOsvetljeno}
      />
    </div>
  )
}

/* ---------- Pogled: udeleženci ---------- */

/* Kdo igra, kdo je rezerva (ni prišel v izbor formata TOP) in kdo je odstopil.
   Odstop je nepovraten, zato gre prek potrditvenega okna. */
function Udelezenci({
  podatki,
  osvezi,
  jeAdmin,
}: {
  podatki: MrezaDto
  osvezi: () => void
  jeAdmin: boolean
}) {
  const [odstopnik, nastaviOdstopnika] = useState<PrijavaDto | null>(null)

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
      <SeznamPrijavljenih
        naslov="Udeleženci"
        prijave={igrajo}
        dejanje={
          jeAdmin
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
            boja (te ne štejejo k ELO).
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
          naslov="Odstop igralca"
          sporocilo={
            `${odstopnik.polnoIme} odstopi s tekmovanja? Že odigrane tekme obveljajo,` +
            ` vse preostale pa dobijo nasprotniki brez boja. Ker te tekme niso bile` +
            ` odigrane, se ne štejejo k ELO. Dejanja ni mogoče razveljaviti.`
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
  naKlikTekme,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  return (
    <div className="dvostolpicno dvostolpicno--lestvica">
      <div className="plosca">
        <h2 className="sekcija__naslov--manjsi">Lestvica</h2>
        <Lestvica vrstice={podatki.lestvica} />
      </div>
      <div className="plosca">
        <h2 className="sekcija__naslov--manjsi">Tekme</h2>
        <TekmePoKolih tekme={skupinske} naKlikTekme={naKlikTekme} />
      </div>
    </div>
  )
}

/* Tekme, razdeljene po kolih. Brez tega je krožni sistem z 12 igralci en sam
   seznam 66 vrstic, iz katerega ni razvidno, kaj je bilo odigrano skupaj.
   Na telefonu je to samostojen pogled (zavihek »Tekme«). */
function TekmePoKolih({
  tekme,
  naKlikTekme,
}: {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <>
      {kola.map((kolo) => (
        <div key={kolo} className="kolo-skupina">
          <div className="kolo-skupina__naslov">{kolo}. kolo</div>
          <TekmeSeznam tekme={tekme.filter((t) => t.kolo === kolo)} naKlikTekme={naKlikTekme} />
        </div>
      ))}
    </>
  )
}

/* ---------- Zaključen dogodek: razvrstitev in končni vrstni red ---------- */

/* Bilanca ene prijave: zmage, porazi in vsota sprememb klubskega ELO.
   Sešteta je iz že prenesenih tekem - nova poizvedba ni potrebna. */
interface Bilanca {
  zmage: number
  porazi: number
  elo: number | null
}

function bilancePrijav(tekme: TekmaDto[]): Map<number, Bilanca> {
  const bilance = new Map<number, Bilanca>()
  const vzemi = (idPrijave: number): Bilanca => {
    let bilanca = bilance.get(idPrijave)
    if (!bilanca) {
      bilanca = { zmage: 0, porazi: 0, elo: null }
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
      if (sprememba !== null) bilanca.elo = (bilanca.elo ?? 0) + sprememba
    }
  }
  return bilance
}

/* "+38" / "−18" / "—" - sprememba klubskega ELO čez cel dogodek. */
function oznakaElo(elo: number | null): string {
  if (elo === null) return '—'
  return elo > 0 ? `+${elo}` : elo < 0 ? `−${Math.abs(elo)}` : '0'
}

function razredElo(elo: number | null, osnova: string): string {
  if (elo === null || elo === 0) return osnova
  return osnova + (elo > 0 ? ` ${osnova}--poz` : ` ${osnova}--neg`)
}

function Zakljucek({ podatki }: { podatki: MrezaDto }) {
  const jeTelefon = useTelefon()
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
          {razvrscene.length} {sklonIgralcev(razvrscene.length)}
          {!jeTelefon && ' · bilanca · ELO'}
        </span>
      </div>

      {/* Mesta so številke, ne medalje - odličje nosi barva črte ob levem robu
          (zelena zmagovalec, modra drugi, črnilo tretji). */}
      <div className={'razvrstitev__vrh' + (vrh.length === 2 ? ' razvrstitev__vrh--dve' : '')}>
        {vrh.map((prijava, indeks) => {
          const bilanca = bilance.get(prijava.id)
          const elo = bilanca?.elo ?? null
          return (
            <div className={`razvrstitev__mesto razvrstitev__mesto--${indeks + 1}`} key={prijava.id}>
              <span className="razvrstitev__stevilka">{prijava.koncnoMesto}</span>
              <span className="razvrstitev__oseba">
                <span className="razvrstitev__ime">{prijava.polnoIme}</span>
                <span className="razvrstitev__klub">
                  {prijava.klub ?? 'brez kluba'}
                  {prijava.rating !== null && ` · ${prijava.rating}`}
                </span>
              </span>
              <span className="razvrstitev__izid">
                <span className="razvrstitev__bilanca">
                  {bilanca ? `${bilanca.zmage}${locilo}${bilanca.porazi}` : '—'}
                </span>
                <span className={razredElo(elo, 'razvrstitev__elo')}>{oznakaElo(elo)}</span>
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
                const elo = bilanca?.elo ?? null
                return (
                  <div className="vrstni-red__vrstica" key={prijava.id}>
                    <span className="vrstni-red__mesto">
                      {prijava.koncnoMesto}
                      {!jeTelefon && '.'}
                    </span>
                    <span className="vrstni-red__ime">
                      {prijava.polnoIme}
                      <span className="vrstni-red__klub">
                        {prijava.klub ?? 'brez kluba'}
                      </span>
                    </span>
                    <span className="vrstni-red__izkupicek">
                      {bilanca ? `${bilanca.zmage}${locilo}${bilanca.porazi}` : '—'}
                    </span>
                    <span className={razredElo(elo, 'vrstni-red__elo')}>{oznakaElo(elo)}</span>
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
