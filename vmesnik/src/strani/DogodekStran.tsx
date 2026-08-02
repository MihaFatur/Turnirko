/* Stran enega dogodka - osrednji delovni prostor.

   V pripravi (admin): urejanje prijav in izvedba žreba; gost vidi le seznam.
   Pri formatu TOP (sistem skupine po jakosti) je v pripravi še urejanje
   jakostnega vrstnega reda s črto reza - po njem tečeta izbor in razporeditev.
   Po žrebu se prikaz prilagodi sistemu tekmovanja:
     - izločilni: mreža,
     - krožni: skupna lestvica + tekme po kolih,
     - skupine + izločilni: lestvice skupin s tekmami, nato izločilna mreža,
     - skupine po jakosti: samo lestvice skupin (izločilnega dela ni).
   Vnos rezultata je mogoč samo administratorju. */
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi, igralciApi, turnirjiApi } from '../api/zahteve'
import type { IzborDto, MrezaDto, PrijavaDto, SkupinaDto, TekmaDto } from '../api/tipi'
import { OZNAKE_SISTEM_KRATKO, OZNAKE_SPOL_KATEGORIJA, OZNAKE_STATUS_PRIJAVE } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { Lestvica } from '../komponente/Lestvica'
import { Mreza } from '../komponente/Mreza'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { TekmeSeznam } from '../komponente/TekmeSeznam'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import { ZnackaStatusa } from '../komponente/Znacka'
import { intervalOsvezevanja, jeVZivo, uraOsvezitve } from '../pomozno/osvezevanje'

export function DogodekStran() {
  const { id } = useParams()
  const idDogodka = Number(id)
  const odjemalec = useQueryClient()
  const { jeAdmin, jeOrganizator, smemUrejati } = useAvtentikacija()

  /* Med tekmovanjem se mreža osvežuje sama - gledalec v dvorani ne sme biti
     odvisen od ročnega ponovnega nalaganja. Ko je dogodek zaključen ali še v
     pripravi, se ne osvežuje nič. */
  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
    refetchInterval: (poizvedba) => intervalOsvezevanja(poizvedba.state.data?.dogodek.status),
  })

  /* Za urejanje potrebujemo lastnistvo nadrejenega turnirja (dogodek ga sam
     ne nosi); poizvedbo sprozimo le za morebitne urejevalce. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir != null && (jeAdmin || jeOrganizator),
  })

  /* Tekma, za katero je odprto okno za vnos rezultata. */
  const [izbranaTekma, nastaviIzbranoTekmo] = useState<TekmaDto | null>(null)

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['dogodek', idDogodka] })

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

  return (
    <section>
      <Link to={`/turnirji/${dogodek.idTurnir}`} className="povezava-nazaj">
        ← Nazaj na turnir
      </Link>

      {/* Ime dogodka je naslov strani v eni vrstici (72 px), lastnosti pod njim. */}
      <div className="stran-glava stran-glava--dejanja stran-glava--dno">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">{dogodek.ime}</h1>
          <p className="uvod">
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
            {` · na ${dogodek.privzetoSteviloNizov} nizov · `}
            {OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja].toLowerCase()}
          </p>
        </div>
        <div className="naslovna-vrstica__desno">
          {smem && dogodek.status === 'V_TEKU' && (
            <Link to={`/dogodki/${idDogodka}/listki`} className="gumb">
              Listki za tiskanje
            </Link>
          )}
          <ZnackaStatusa status={dogodek.status} />
          {/* Ura zadnjega odgovora strežnika: brez nje gledalec ne ve, ali
              stoji rezultat ali njegova povezava. */}
          {jeVZivo(dogodek.status) && (
            <span className="sekcija__meta">Osveženo ob {uraOsvezitve(mreza.dataUpdatedAt)}</span>
          )}
        </div>
      </div>

      {dogodek.status === 'PRIPRAVA' ? (
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

/* ---------- Faza priprave (gost): samo seznam prijavljenih ---------- */

function PripravaGost({ podatki }: { podatki: MrezaDto }) {
  const aktivne = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN')
  return (
    <div className="plosca">
      <h2>Prijavljeni ({aktivne.length})</h2>
      {aktivne.length === 0 ? (
        <p className="obvestilo">Ni še prijavljenih igralcev.</p>
      ) : (
        <table className="tabela">
          <thead>
            <tr>
              <th scope="col">Igralec</th>
              <th scope="col">Klub</th>
            </tr>
          </thead>
          <tbody>
            {aktivne.map((prijava) => (
              <tr key={prijava.id}>
                <td>{prijava.polnoIme}</td>
                <td>{prijava.klub ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="namig">Žreb izvede administrator (sodnik) po prijavi.</p>
    </div>
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
  const [potrjujemZreb, nastaviPotrjujemZreb] = useState(false)
  const izbor = podatki.izbor

  const odjava = useMutation({
    mutationFn: (idPrijave: number) => dogodkiApi.odjavi(idPrijave),
    onSuccess: osvezi,
  })

  const zreb = useMutation({
    mutationFn: () => dogodkiApi.izvediZreb(idDogodka),
    onSuccess: osvezi,
  })

  /* Zadržek pove strežnik (npr. zadnja skupina bi imela enega igralca),
     da vmesnik ne podvaja pravil razreza. */
  const zadrzek = izbor?.zadrzek ?? null
  const premalo = aktivnePrijave.length < 2
  const zrebOnemogocen = premalo || zadrzek !== null || zreb.isPending

  return (
    <div className="dvostolpicno dvostolpicno--lestvica">
      <div>
        <div className="naslovna-vrstica">
          <h2>{izbor ? 'Jakostni vrstni red' : 'Prijavljeni'}</h2>
          <div className="naslovna-vrstica__desno">
            <span className="sekcija__meta">
              {aktivnePrijave.length} {prijavljenihTekst(aktivnePrijave.length)}
            </span>
            <button
              className="gumb gumb--zreb"
              disabled={zrebOnemogocen}
              title={premalo ? 'Za žreb sta potrebna vsaj 2 igralca.' : (zadrzek ?? undefined)}
              onClick={() => nastaviPotrjujemZreb(true)}
            >
              {zreb.isPending ? 'Žrebam …' : 'Izvedi žreb'}
            </button>
          </div>
        </div>

        <SporociloNapake napaka={zreb.error} />
        <SporociloNapake napaka={odjava.error} />

        {aktivnePrijave.length === 0 ? (
          <p className="obvestilo">Ni še prijavljenih igralcev.</p>
        ) : izbor ? (
          <JakostniVrstniRed
            prijave={aktivnePrijave}
            izbor={izbor}
            idDogodka={idDogodka}
            osvezi={osvezi}
            onOdjava={(id) => odjava.mutate(id)}
          />
        ) : (
          <table className="tabela">
            <thead>
              <tr>
                <th scope="col">Igralec</th>
                <th scope="col">Klub</th>
                <th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              {aktivnePrijave.map((prijava) => (
                <tr key={prijava.id}>
                  <td>{prijava.polnoIme}</td>
                  <td>{prijava.klub ?? '—'}</td>
                  <td className="tabela__dejanja">
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => odjava.mutate(prijava.id)}
                    >
                      Odjavi
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <DodajanjeIgralcev podatki={podatki} idDogodka={idDogodka} osvezi={osvezi} />

      {potrjujemZreb && (
        <PotrditvenoOkno
          naslov="Izvedba žreba"
          sporocilo={
            izbor
              ? `Po žrebu prijav in vrstnega reda ni več mogoče spreminjati.` +
                ` Igralo bo najboljših ${izbor.igra} od ${izbor.prijavljenih} prijavljenih,` +
                ` ostali postanejo rezerve. Izvedem žreb?`
              : 'Po žrebu prijav ni več mogoče spreminjati. Izvedem žreb?'
          }
          besedaPotrditve="Izvedi žreb"
          onPotrdi={() => zreb.mutate()}
          onZapri={() => nastaviPotrjujemZreb(false)}
        />
      )}
    </div>
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
        <h2>Dodaj igralce</h2>
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

/* ---------- Po žrebu: prikaz glede na sistem ---------- */

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
  const koncan = podatki.dogodek.status === 'ZAKLJUCEN'
  const naKlik = koncan ? undefined : naKlikTekme
  const sistem = podatki.dogodek.sistemTekmovanja

  /* Vrstni red sledi poteku tekmovanja: najprej skupine oz. izločilni del,
     nazadnje razvrstitev - kdo je kje končal, je zaključek, ne uvod. Vsak
     sistem svoje tekme izpiše sam (po kolih oz. v mreži). */
  return (
    <>
      {sistem === 'KROZNI' && <Krozni podatki={podatki} naKlikTekme={naKlik} />}
      {sistem === 'SKUPINE_IZLOCILNI' && <SkupineIzlocilni podatki={podatki} naKlikTekme={naKlik} />}
      {sistem === 'SKUPINE' && <SkupinePoJakosti podatki={podatki} naKlikTekme={naKlik} />}
      {sistem === 'IZLOCILNI' && (
        <div>
          <div className="naslovna-vrstica">
            <h2>Izločilna mreža</h2>
            {naKlik && <span className="sekcija__meta">Klikni tekmo za vnos rezultata</span>}
          </div>
          <Mreza tekme={podatki.tekme} naKlikTekme={naKlik} />
        </div>
      )}

      {sistem === 'SKUPINE' && <Udelezenci podatki={podatki} osvezi={osvezi} jeAdmin={jeAdmin && !koncan} />}

      {koncan && <Razvrstitev prijave={podatki.prijave} />}

      {!koncan && naKlik && (
        <p className="namig">
          {sistem === 'SKUPINE'
            ? 'Klikni tekmo za vnos rezultata. Lestvica skupine se preračuna sproti.'
            : 'Klikni tekmo z obema znanima igralcema za vnos rezultata. Zmagovalec samodejno napreduje.'}
        </p>
      )}
    </>
  )
}

/* Format TOP: samo lestvice skupin. Skupine so rangi (A je najmočnejša),
   zato ni ne izločilnega dela ne skupne razvrstitve čez skupine. */
function SkupinePoJakosti({
  podatki,
  naKlikTekme,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  return (
    <div className="skupine">
      {podatki.skupine.map((skupina) => (
        <SkupinaPlosca
          key={skupina.id}
          skupina={skupina}
          tekme={podatki.tekme.filter((t) => t.faza === 'SKUPINA' && t.idSkupina === skupina.id)}
          naKlikTekme={naKlikTekme}
        />
      ))}
    </div>
  )
}

/* Udeleženci formata TOP: rezerve (niso prišle v izbor) in odstopi.
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

  if (!jeAdmin && rezerve.length === 0 && odstopili.length === 0) return null

  return (
    <div className="plosca">
      <h2>Udeleženci</h2>

      {jeAdmin && igrajo.length > 0 && (
        <div className="udelezenci">
          {igrajo.map((prijava) => (
            <div className="udelezenci__vrstica" key={prijava.id}>
              <span>
                {prijava.stNosilca !== null && (
                  <span className="izbor__mesto">{prijava.stNosilca}.</span>
                )}
                {prijava.polnoIme}
                <span className="izbor__podrobnost">{prijava.klub ?? 'brez kluba'}</span>
              </span>
              <button
                className="gumb gumb--majhen gumb--nevaren"
                onClick={() => nastaviOdstopnika(prijava)}
              >
                Odstopil
              </button>
            </div>
          ))}
        </div>
      )}

      {rezerve.length > 0 && (
        <>
          <h3 className="podnaslov">Rezerve ({rezerve.length})</h3>
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
        </>
      )}

      {odstopili.length > 0 && (
        <>
          <h3 className="podnaslov">Odstopili ({odstopili.length})</h3>
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
        </>
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
    </div>
  )
}

/* Krožni sistem: skupna lestvica + tekme po kolih. */
function Krozni({
  podatki,
  naKlikTekme,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  const kola = [...new Set(skupinske.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <div className="dvostolpicno dvostolpicno--lestvica">
      <div className="plosca">
        <h2>Lestvica</h2>
        <Lestvica vrstice={podatki.lestvica} />
      </div>
      <div className="plosca">
        <h2>Tekme</h2>
        {kola.map((kolo) => (
          <div key={kolo} className="kolo-skupina">
            <div className="kolo-skupina__naslov">{kolo}. kolo</div>
            <TekmeSeznam
              tekme={skupinske.filter((t) => t.kolo === kolo)}
              naKlikTekme={naKlikTekme}
            />
          </div>
        ))}
      </div>
    </div>
  )
}

/* Skupinski del: lestvica in tekme vsake skupine, nato izločilna mreža. */
function SkupineIzlocilni({
  podatki,
  naKlikTekme,
}: {
  podatki: MrezaDto
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const izlocilne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  const odigranihSkupinskih = skupinske.filter((t) => t.status === 'KONCANA').length
  return (
    <>
      <div>
        <div className="naslovna-vrstica">
          <h2>Skupine</h2>
          <span className="sekcija__meta">
            Napredujeta po dva · {odigranihSkupinskih} / {skupinske.length} odigranih
          </span>
        </div>
      </div>
      <div className="skupine">
        {podatki.skupine.map((skupina) => (
          <SkupinaPlosca
            key={skupina.id}
            skupina={skupina}
            tekme={podatki.tekme.filter((t) => t.faza === 'SKUPINA' && t.idSkupina === skupina.id)}
            naKlikTekme={naKlikTekme}
          />
        ))}
      </div>

      <div className="naslovna-vrstica">
        <h2>Izločilni del</h2>
        {izlocilne.length > 0 && naKlikTekme && (
          <span className="sekcija__meta">Klikni tekmo za vnos rezultata</span>
        )}
      </div>
      {izlocilne.length === 0 ? (
        <p className="obvestilo">
          Izločilni del se samodejno zažene, ko so odigrane vse tekme skupin
          (napredujeta po dva iz vsake skupine).
        </p>
      ) : (
        <Mreza tekme={izlocilne} naKlikTekme={naKlikTekme} />
      )}
    </>
  )
}

function SkupinaPlosca({
  skupina,
  tekme,
  naKlikTekme,
}: {
  skupina: SkupinaDto
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
}) {
  const odigranih = tekme.filter((t) => t.status === 'KONCANA').length
  /* Tekme so razdeljene po kolih tako kot pri krožnem sistemu: brez tega je
     skupina osmih igralcev en sam seznam 28 vrstic, iz katerega ni razvidno,
     kaj je bilo odigrano skupaj in kaj šele pride. */
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <div>
      <div className="skupina__glava">
        <span className="skupina__naslov">Skupina {skupina.oznaka}</span>
        <span className="sekcija__meta">
          {odigranih} / {tekme.length}
        </span>
      </div>
      <Lestvica vrstice={skupina.lestvica} napreduje={2} />
      {kola.map((kolo) => (
        <div key={kolo} className="kolo-skupina">
          <div className="kolo-skupina__naslov">{kolo}. kolo</div>
          <TekmeSeznam
            tekme={tekme.filter((t) => t.kolo === kolo)}
            naKlikTekme={naKlikTekme}
          />
        </div>
      ))}
    </div>
  )
}

function Razvrstitev({ prijave }: { prijave: PrijavaDto[] }) {
  const razvrscene = prijave
    .filter((prijava) => prijava.koncnoMesto !== null)
    .sort((prva, druga) => prva.koncnoMesto! - druga.koncnoMesto!)

  if (razvrscene.length === 0) return null

  /* Mesta so številke, ne medalje - odličje nosi barva črte ob levem robu. */
  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razvrstitev</h2>
      </div>
      <div className="podij">
        {razvrscene.map((prijava) => (
          <div className="podij__mesto" key={prijava.id}>
            <span className="podij__stevilka">{prijava.koncnoMesto}.</span>
            <span className="podij__ime">{prijava.polnoIme}</span>
            <span className="podij__klub">{prijava.klub ?? 'brez kluba'}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/* Slovnično pravilna oblika besede "prijavljen" glede na število. */
function prijavljenihTekst(n: number): string {
  if (n === 1) return 'prijavljen'
  if (n === 2) return 'prijavljena'
  if (n === 3 || n === 4) return 'prijavljeni'
  return 'prijavljenih'
}
