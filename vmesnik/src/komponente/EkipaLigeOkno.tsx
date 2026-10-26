/* Okno ekipe lige: kader in vsa srečanja ekipe v tej ligi, po vrsti.

   Odpre ga klik na ekipo na lestvici (prej je vrstica kader razprla pod sabo).
   Igralec ekipe pride z vprašanjem »kdaj in s kom igramo, doma ali v gosteh,
   in kako se je izšlo« — v razporedu je odgovor raztresen po kolih, tu je na
   enem mestu. Srečanja so z vidika ekipe: najprej nasprotnik in kje se igra,
   izid pa »naše : njihove« z znakom zmage oz. poraza.

   Srečanj okno ne nalaga samo: dobi jih od strani lige (redni del in končnica),
   ki jih ima že naložene in jih med večerom osvežuje. Strežnik vpraša samo po
   kadru. */
import { Fragment, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, SrecanjeDto, TekmaIgralcaLigeDto } from '../api/tipi'
import { danesIso, imeKola, oblikujTermin } from '../pomozno/oblikovanje'
import { opisPrestavitve, terminKola, vKolih } from '../pomozno/prestavitve'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'

export type IzidEkipe = 'Z' | 'N' | 'P'

const OZNAKE_IZIDA: Record<IzidEkipe, string> = {
  Z: 'zmaga',
  N: 'neodločeno',
  P: 'poraz',
}

/* Izid srečanja z vidika ekipe. Šteje izid srečanja (dobljene tekme), ne
   posamične tekme - isto merilo kot forma na lestvici. */
export function izidZaEkipo(s: SrecanjeDto, idEkipa: number): IzidEkipe {
  const doma = s.idEkipaDomaci === idEkipa
  const svoje = doma ? s.dobljeneDomaci : s.dobljeneGost
  const tuje = doma ? s.dobljeneGost : s.dobljeneDomaci
  if (svoje > tuje) return 'Z'
  if (svoje < tuje) return 'P'
  return 'N'
}

/* Vrstica seznama: srečanje ali kolo, v katerem ekipa ne igra. */
type Postavka =
  | { vrsta: 'SRECANJE'; srecanje: SrecanjeDto }
  | { vrsta: 'PROSTO'; kolo: number; zacetek: string | null }

function zacetekPostavke(p: Postavka): string | null {
  return p.vrsta === 'SRECANJE' ? p.srecanje.predvidenZacetek : p.zacetek
}

function cakaNaTermin(p: Postavka): boolean {
  return p.vrsta === 'SRECANJE' && p.srecanje.cakaNaTermin
}

/* Končnica pride za rednim delom tudi brez datumov. */
function fazaPostavke(p: Postavka): number {
  return p.vrsta === 'SRECANJE' && p.srecanje.idSerija != null ? 1 : 0
}

/* Vrstni red znotraj faze, kadar datumov ni: kolo in ura kola v rednem delu,
   krog in zaporedna tekma serije v končnici. */
function redPostavke(p: Postavka): number {
  if (p.vrsta === 'PROSTO') return p.kolo * 100
  const s = p.srecanje
  if (s.idSerija != null) return (s.krogKoncnice ?? 0) * 100 + (s.tekmaVSeriji ?? 0)
  return s.kolo * 100 + (s.uraVKolu ?? 0)
}

/* Srečanja ekipe in njena prosta kola, po datumu.

   Po datumu in ne po kolu, ker se srečanje prestavi (prestavljeno kolo ne
   premakne naslednjih) - igralec pa sprašuje, kaj je naslednji teden. Če
   kateremu manjka termin, velja vrstni red kol: mešanje obeh meril bi dalo
   seznam, ki ni urejen po nobenem. */
function postavkeEkipe(idEkipa: number, srecanja: SrecanjeDto[]): Postavka[] {
  const svoja = srecanja.filter((s) => s.idEkipaDomaci === idEkipa || s.idEkipaGost === idEkipa)
  const redna = vKolih(srecanja.filter((s) => s.idSerija == null))
  /* Kolo, v katerem je imela ekipa srečanje po žrebu, ni prosto, tudi če je
     srečanje prestavljeno drugam - prosto kolo je samo tisto, kjer ekipa po
     žrebu ne igra. */
  const kolaEkipe = new Set(
    svoja
      .filter((s) => s.idSerija == null)
      .flatMap((s) => [s.kolo, s.izvirnoKolo ?? s.kolo]),
  )

  /* Liga z lihim številom ekip (ali parov pri enakomerni razvrstitvi) ima v
     vsakem kolu ekipo, ki ne igra. V razporedu je to samo odsotnost, igralec
     pa mora vedeti, da tisti dan ne igra. Termin je termin kola. */
  const prosta: Postavka[] = [...new Set(redna.map((s) => s.kolo))]
    .filter((k) => !kolaEkipe.has(k))
    .map((k) => ({
      vrsta: 'PROSTO',
      kolo: k,
      zacetek: terminKola(redna, k),
    }))

  const vse: Postavka[] = [
    ...svoja.map((s): Postavka => ({ vrsta: 'SRECANJE', srecanje: s })),
    ...prosta,
  ]
  /* Prestavljeno srečanje brez novega termina nima dneva, zato bi mešanje z
     datumi seznam spravilo na vrstni red kol: čakajoča gredo na konec rednega
     dela, ostala se urejajo po datumu. */
  const poDatumu = vse.filter((p) => !cakaNaTermin(p)).every((p) => zacetekPostavke(p) != null)
  return vse.sort((a, b) => {
    const faza = fazaPostavke(a) - fazaPostavke(b)
    if (faza !== 0) return faza
    const caka = Number(cakaNaTermin(a)) - Number(cakaNaTermin(b))
    if (caka !== 0) return caka
    if (poDatumu && !cakaNaTermin(a)) {
      const za = zacetekPostavke(a) as string
      const zb = zacetekPostavke(b) as string
      if (za !== zb) return za < zb ? -1 : 1
    }
    return redPostavke(a) - redPostavke(b)
  })
}

/* Mono vrstica nad nasprotnikom: kolo (oz. krog končnice) in termin. */
function kdajSrecanja(s: SrecanjeDto, steviloKrogov: number): string[] {
  const del =
    s.idSerija != null && s.krogKoncnice != null
      ? [imeKola(s.krogKoncnice, steviloKrogov), s.tekmaVSeriji != null ? `${s.tekmaVSeriji}. tekma` : '']
      : [`${s.kolo}. kolo`]
  /* Čakajoče srečanje ima kolo iz žreba, dneva pa ne: namesto praznine pove
     to izrecno. */
  if (s.cakaNaTermin) return [...del, 'termin še ni znan']
  return [...del, ...deliTermina(s.predvidenZacetek)].filter(Boolean)
}

/* »sob, 11. okt« in »17.00« sta dva dela: če vrstica na telefonu ne gre v eno
   vrsto, naj se prelomi med deli in ne sredi datuma. */
function deliTermina(zacetek: string | null): string[] {
  return oblikujTermin(zacetek).split(' · ')
}

/* Deli mono vrstice; vsak ostane cel, prelom je samo med njimi. Pika se
   (nedeljivi presledek) drži prejšnjega dela, zato vrstica ne začne s piko. */
function Kdaj({ deli, oznaka }: { deli: string[]; oznaka?: string | null }) {
  const vsi = oznaka ? [oznaka, ...deli] : deli
  return (
    <span className="ekipa-okno__kdaj">
      {vsi.map((d, i) => (
        <Fragment key={i}>
          {i > 0 && '\u00a0· '}
          <span className={'ekipa-okno__del' + (oznaka && i === 0 ? ' ekipa-okno__oznaka' : '')}>
            {d}
          </span>
        </Fragment>
      ))}
    </span>
  )
}

interface Lastnosti {
  liga: LigaDto
  idEkipa: number
  /* Vsa srečanja lige - redni del in končnica. */
  srecanja: SrecanjeDto[]
  onZapri: () => void
}

export function EkipaLigeOkno({ liga, idEkipa, srecanja, onZapri }: Lastnosti) {
  /* Lestvica je ista poizvedba kot na strani, zato je v predpomnilniku. */
  const lestvica = useQuery({
    queryKey: ['lestvica', liga.id],
    queryFn: () => ligeApi.lestvica(liga.id),
  })
  const vrstica = lestvica.data?.find((v) => v.idEkipa === idEkipa)

  const postavke = postavkeEkipe(idEkipa, srecanja)
  const svoja = postavke.flatMap((p) => (p.vrsta === 'SRECANJE' ? [p.srecanje] : []))
  const prvo = svoja[0]
  const ime =
    vrstica?.ekipa ?? (prvo ? (prvo.idEkipaDomaci === idEkipa ? prvo.domaci : prvo.gost) : 'Ekipa')
  const odigranih = svoja.filter((s) => s.status === 'KONCANO').length

  /* Naslednje je prvo srečanje, ki se še ni začelo in mu termin ni mimo.
     Neodigrano srečanje s preteklim terminom je zamujen vpis ali prestavitev
     brez novega datuma - za »naslednje« bi zavajalo. Brez terminov velja
     prvo neodigrano po vrsti. */
  const danes = danesIso()
  /* Čakajoče srečanje (prestavljeno brez novega termina) ni naslednje: dneva
     nima, zato bi pred pravim naslednjim stalo samo zato, ker datuma ni. */
  const naslednje = svoja.find(
    (s) =>
      s.status === 'RAZPORED' &&
      !s.cakaNaTermin &&
      (s.predvidenZacetek == null || s.predvidenZacetek.slice(0, 10) >= danes),
  )

  const steviloKrogov = liga.koncnicaEkip ? Math.round(Math.log2(liga.koncnicaEkip)) : 0

  return (
    <ModalnoOkno
      naslov={ime}
      podnaslov={vrstica ? `${vrstica.mesto}. mesto · ${vrstica.tocke} ${tockTekst(vrstica.tocke)}` : undefined}
      siroko
      onZapri={onZapri}
    >
      <div className="ekipa-okno">
        <KaderEkipe liga={liga} idEkipa={idEkipa} srecanja={srecanja} steviloKrogov={steviloKrogov} />

        <section className="ekipa-okno__sklop">
          <div className="liga__kader-glava">
            <h3 className="liga__kader-naslov">Srečanja</h3>
            <span className="sekcija__meta">
              {odigranih} od {svoja.length} odigranih
            </span>
          </div>

          {postavke.length === 0 && <p className="obvestilo">Ekipa v tej ligi nima srečanj.</p>}

          <ul className="ekipa-okno__seznam">
            {postavke.map((p) =>
              p.vrsta === 'PROSTO' ? (
                <li key={`prosto-${p.kolo}`} className="ekipa-okno__srecanje ekipa-okno__srecanje--prosto">
                  <Kdaj
                    deli={[`${p.kolo}. kolo`, ...deliTermina(p.zacetek?.slice(0, 10) ?? null)].filter(
                      Boolean,
                    )}
                  />
                  <span className="ekipa-okno__nasprotnik">Prosto kolo — ekipa ne igra</span>
                </li>
              ) : (
                <li key={p.srecanje.id}>
                  <VrsticaSrecanja
                    s={p.srecanje}
                    idEkipa={idEkipa}
                    kdaj={kdajSrecanja(p.srecanje, steviloKrogov)}
                    naslednje={p.srecanje.id === naslednje?.id}
                  />
                </li>
              ),
            )}
          </ul>
        </section>
      </div>
    </ModalnoOkno>
  )
}

function VrsticaSrecanja({
  s,
  idEkipa,
  kdaj,
  naslednje,
}: {
  s: SrecanjeDto
  idEkipa: number
  kdaj: string[]
  naslednje: boolean
}) {
  const doma = s.idEkipaDomaci === idEkipa
  const svoje = doma ? s.dobljeneDomaci : s.dobljeneGost
  const tuje = doma ? s.dobljeneGost : s.dobljeneDomaci
  const konec = s.status === 'KONCANO'
  const vTeku = s.status === 'POTEKA'
  const izid = konec ? izidZaEkipo(s, idEkipa) : null
  const prestavitev = opisPrestavitve(s)
  const oznaka = vTeku ? 'V teku' : naslednje ? 'Naslednje' : null

  return (
    <Link
      to={`/srecanja/${s.id}`}
      className={
        'ekipa-okno__srecanje'
        + (oznaka ? ' ekipa-okno__srecanje--naslednje' : '')
        + (prestavitev ? ' ekipa-okno__srecanje--z-opombo' : '')
      }
    >
      <Kdaj deli={kdaj} oznaka={oznaka} />
      <span className="ekipa-okno__nasprotnik">
        <span className="ekipa-okno__stran">{doma ? 'doma' : 'v gosteh'}</span>
        <span className="ekipa-okno__ime">{doma ? s.gost : s.domaci}</span>
      </span>
      {/* Srečanje v teku izid že ima (strežnik ga sešteva sproti); oznaka
          »V teku« pove, da ni končen. */}
      <span className={'ekipa-okno__izid' + (konec || vTeku ? '' : ' ekipa-okno__izid--caka')}>
        {konec || vTeku ? `${svoje} : ${tuje}` : 'vs'}
        {konec && s.brezBoja && <span className="liga__brez-borbe" title="brez borbe">b. b.</span>}
        {konec && s.prenesen && (
          <span className="liga__brez-borbe" title="izid prenesen iz predtekmovanja">prenesen</span>
        )}
      </span>
      <span className="ekipa-okno__znak-celica">
        {izid && (
          <span className={`ekipa-okno__znak ekipa-okno__znak--${izid}`}>
            <span aria-hidden="true">{izid}</span>
            <span className="samo-za-bralnik">{OZNAKE_IZIDA[izid]}</span>
          </span>
        )}
      </span>
      {/* Prestavljeno srečanje ne sme ostati nepojasnjeno: igralec vidi, da ga
          je zdaj v drugem kolu kot po žrebu, in če je organizator vpisal,
          zakaj. */}
      {prestavitev && (
        <span className="ekipa-okno__opomba">
          {prestavitev.naslov}
          {prestavitev.razlog && (
            <span className="ekipa-okno__razlog">{prestavitev.razlog}</span>
          )}
        </span>
      )}
    </Link>
  )
}

/* Kader ekipe. Vrstni red je strežnikov (LigaStoritev.kader): največ zmag za
   to ekipo v tej ligi na vrhu, zato je številka pred imenom mesto po
   izkupičku in ne organizatorjev vrstni red. */
function KaderEkipe({
  liga,
  idEkipa,
  srecanja,
  steviloKrogov,
}: {
  liga: LigaDto
  idEkipa: number
  srecanja: SrecanjeDto[]
  steviloKrogov: number
}) {
  const kader = useQuery({ queryKey: ['kader', idEkipa], queryFn: () => ligeApi.kader(idEkipa) })
  /* Odprti igralci po idIgralec: puščica razpre vse njegove tekme v tej ligi, ob
     vsakem odprtju pa se tekme naložijo šele tedaj. Odprtih je lahko več hkrati. */
  const [odprti, setOdprti] = useState<Set<number>>(() => new Set())

  function preklopi(idIgralec: number) {
    setOdprti((prej) => {
      const nov = new Set(prej)
      if (nov.has(idIgralec)) nov.delete(idIgralec)
      else nov.add(idIgralec)
      return nov
    })
  }

  return (
    <section className="ekipa-okno__sklop">
      {/* Glava je mreža istih stolpcev kot vrstice, zato oznaka stoji nad
          svojimi številkami. Zadnji stolpec ima samo skrit naslov: puščica je
          sama dovolj jasna. */}
      <div className="liga__kader-glava ekipa-okno__kader-glava">
        <h3 className="liga__kader-naslov">Kader</h3>
        <span className="sekcija__meta">Rating</span>
        {/* Zmage : porazi za to ekipo v tej ligi. Prej je pisalo angleško
            »Score«. */}
        <span className="sekcija__meta">Izkupiček</span>
        <span className="samo-za-bralnik">Tekme igralca</span>
      </div>
      {kader.isPending && <p className="obvestilo">Nalaganje kadra …</p>}
      <NapakaPoizvedbe poizvedba={kader} kaj="kadra" />
      {kader.data && kader.data.length === 0 && <p className="obvestilo">Kader je prazen.</p>}
      {kader.data?.map((k, i) => {
        const odprt = odprti.has(k.idIgralec)
        return (
          <div key={k.id}>
            <div className="liga__kader-vrstica liga__kader-vrstica--tekme">
              <span className="liga__kader-mesto">{i + 1}.</span>
              <span className="liga__kader-ime">{k.polnoIme}</span>
              <span className="liga__kader-rating">{k.rating ?? '—'}</span>
              <span className="liga__kader-bilanca">
                {k.zmage} : {k.porazi}
              </span>
              <button
                type="button"
                className="liga__kader-tekme-gumb"
                aria-expanded={odprt}
                aria-label={`${odprt ? 'Skrij' : 'Pokaži'} tekme: ${k.polnoIme}`}
                onClick={() => preklopi(k.idIgralec)}
              >
                <span aria-hidden="true" className="liga__kader-puscica">
                  ▾
                </span>
              </button>
            </div>
            {odprt && (
              <TekmeIgralca
                idLiga={liga.id}
                idIgralec={k.idIgralec}
                idEkipa={idEkipa}
                srecanja={srecanja}
                steviloKrogov={steviloKrogov}
              />
            )}
          </div>
        )
      })}
    </section>
  )
}

/* Vse tekme igralca v ligi, po vrsti srečanj (kolo, nato termin - kot v oknu
   ekipe). Srečanje, ki ga seznam srečanj lige nima, tekme ne pokaže. */
function TekmeIgralca({
  idLiga,
  idIgralec,
  idEkipa,
  srecanja,
  steviloKrogov,
}: {
  idLiga: number
  idIgralec: number
  idEkipa: number
  srecanja: SrecanjeDto[]
  steviloKrogov: number
}) {
  const tekme = useQuery({
    queryKey: ['tekme-igralca', idLiga, idIgralec],
    queryFn: () => ligeApi.tekmeIgralca(idLiga, idIgralec),
  })
  const poSrecanju = new Map(srecanja.map((s, i) => [s.id, { s, i }]))
  /* Tekme enega srečanja ostanejo med sabo v vrstnem redu zapisa, ker je
     sortiranje stabilno; srečanja pridejo v vrstnem redu lige. */
  const urejene = tekme.data
    ?.flatMap((t) => {
      const zadetek = poSrecanju.get(t.idSrecanje)
      return zadetek ? [{ t, s: zadetek.s, i: zadetek.i }] : []
    })
    .sort((a, b) => a.i - b.i)

  return (
    <div className="ekipa-okno__tekme-igralca">
      {tekme.isPending && <p className="obvestilo">Nalaganje tekem …</p>}
      <NapakaPoizvedbe poizvedba={tekme} kaj="tekem" />
      {urejene && urejene.length === 0 && <p className="obvestilo">Igralec v tej ligi še nima tekem.</p>}
      {urejene && urejene.length > 0 && (
        <ul className="ekipa-okno__seznam">
          {urejene.map(({ t, s }) => (
            <li key={t.id}>
              <VrsticaTekmeIgralca t={t} s={s} idEkipa={idEkipa} steviloKrogov={steviloKrogov} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* Tekma igralca z njegove strani: kje in s kom igra, njegovi nizi proti nasprotniku
   in znak zmage. Zgradba je ista kot pri srečanju ekipe, da se bere enako. Če je
   igralec v tej ligi za drugo ekipo, vrstica pove, za katero. */
function VrsticaTekmeIgralca({
  t,
  s,
  idEkipa,
  steviloKrogov,
}: {
  t: TekmaIgralcaLigeDto
  s: SrecanjeDto
  idEkipa: number
  steviloKrogov: number
}) {
  const izid: IzidEkipe | null = t.zmaga === null ? null : t.zmaga ? 'Z' : 'P'
  const opomba = [
    t.soigralec ? `v paru z ${t.soigralec}` : null,
    t.idEkipa !== idEkipa ? `za ${t.ekipa}` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <Link to={`/srecanja/${s.id}`} className="ekipa-okno__srecanje">
      <Kdaj deli={kdajSrecanja(s, steviloKrogov)} />
      <span className="ekipa-okno__nasprotnik">
        <span className="ekipa-okno__stran">{t.domaci ? 'doma' : 'v gosteh'}</span>
        <span className="ekipa-okno__ime">
          {t.nasprotniki.join(' in ')}
          {opomba && <span className="ekipa-okno__par">{opomba}</span>}
        </span>
      </span>
      <span className={'ekipa-okno__izid' + (t.zmaga === null ? ' ekipa-okno__izid--caka' : '')}>
        {t.zmaga === null ? 'vs' : `${t.niziIgralca} : ${t.niziNasprotnika}`}
        {t.izidTip === 'BREZ_BOJA' && (
          <span className="liga__brez-borbe" title="brez borbe">
            b. b.
          </span>
        )}
      </span>
      <span className="ekipa-okno__znak-celica">
        {izid && (
          <span className={`ekipa-okno__znak ekipa-okno__znak--${izid}`}>
            <span aria-hidden="true">{izid}</span>
            <span className="samo-za-bralnik">{OZNAKE_IZIDA[izid]}</span>
          </span>
        )}
      </span>
    </Link>
  )
}

function tockTekst(n: number): string {
  if (n === 1) return 'točka'
  if (n === 2) return 'točki'
  if (n === 3 || n === 4) return 'točke'
  return 'točk'
}
