/* Organizatorski pregled: »Nadzorna plošča« organizatorja (smer 1a, zapisniška;
   design_handoff_organizatorski_pregled). Organizator na enem mestu vidi, kaj
   čaka njegovo dejanje, svoja tekmovanja tekoče sezone, prihajajoče termine,
   arhiv sezon in porabo paketa.

   Drevesi sta DVE, ne en odziven: namizna stran (≥ 1024 px) ima pas sezone,
   gumba za ustvarjanje in dva stolpca; telefonska ima krajše vrstice in en sam
   seznam sezone (glej pomozno/sirinaOkna.ts). Vse mere in barve so v slog.css
   (razdelek »Organizatorski pregled«); besedilo sestavi
   pomozno/organizatorPregled.ts, števila pa pošlje zaledje.

   Poraba paketa je številka iz zaledja (NarocninaStoritev): ista, ki jo
   strežnik preveri ob ustvarjanju. Gumb »+ Nova liga« / »+ Nov turnir« ob polni
   kvoti ostane aktiven in odpre obvestilo o nadgradnji - ne blokira tiho. */
import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { narocninaApi, organizatorApi } from '../api/zahteve'
import type {
  ArhivSezoneDto,
  CakaPregledaDto,
  KvotaDto,
  NarocninaDto,
  OrganizatorPregledDto,
  TekmovanjePregledaDto,
  TerminPregledaDto,
} from '../api/tipi'
import { OZNAKE_PAKET } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { LikNaPalici } from '../komponente/MaskotaPrizori'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujCeno, oblikujDanMesec, oblikujDatum } from '../pomozno/oblikovanje'
import {
  besediloCakanja,
  cakaOrganizatorja,
  gumbCakanja,
  imeTermina,
  jeOrganizatorPaket,
  jeRezultatAliZapisnik,
  kratkaSezona,
  kratkoCakanje,
  KRATKO_IME_PAKETA,
  napisTable,
  obsegSezone,
  opisCakanja,
  opisKvote,
  opisSezone,
  opisTekmovanja,
  opisTermina,
  oznakaNapredka,
  potCakanja,
  potTekmovanja,
  potTermina,
  povzetekCakanja,
  sklonLig,
  sklonTekmovanj,
  sklonTurnirjev,
  steviloCakajocih,
  tedenInMesec,
  visjiPaket,
  type OrganizatorPaket,
} from '../pomozno/organizatorPregled'
import { useNamizje } from '../pomozno/sirinaOkna'
import { NovTurnirOkno } from './TurnirjiStran'

type Filter = 'VSE' | 'LIGE' | 'TURNIRJI'

/* Kaj je odprto nad stranjo: obrazec ali obvestilo o polni kvoti. */
type Okno = 'liga' | 'turnir' | 'polna-liga' | 'polni-turnir' | 'brez-paketa' | null

export function OrganizatorskiPregledStran() {
  const pregled = useQuery({ queryKey: ['organizator-pregled'], queryFn: organizatorApi.pregled })
  /* Cena in obnova sta podatek naročnine (GET /narocnina), ne pregleda. */
  const narocnina = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  const namizje = useNamizje()

  if (pregled.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (!pregled.data) return <NapakaPoizvedbe poizvedba={pregled} kaj="pregleda" />

  return namizje ? (
    <Namizje pregled={pregled.data} narocnina={narocnina.data ?? null} />
  ) : (
    <Telefon pregled={pregled.data} narocnina={narocnina.data ?? null} />
  )
}

// ---------- Skupno ----------

/* Sezona, ki jo stran kaže: tekoča ali pretekla iz arhiva (?sezona=2025/26). */
function useIzbranaSezona(pregled: OrganizatorPregledDto) {
  const [iskalniNiz] = useSearchParams()
  const zahtevana = iskalniNiz.get('sezona')
  const veljavna = zahtevana !== null && pregled.arhiv.some((a) => a.sezona === zahtevana)
  return veljavna ? zahtevana : pregled.sezona
}

function tekmovanjaSezone(pregled: OrganizatorPregledDto, sezona: string): TekmovanjePregledaDto[] {
  return pregled.tekmovanja.filter((t) => t.sezona === sezona)
}

/* Telefon: lige, nato turnirji, ki še niso končani, na koncu končani. */
function poTelefonskemVrstnemRedu(tekmovanja: TekmovanjePregledaDto[]): TekmovanjePregledaDto[] {
  const lige = tekmovanja.filter((t) => t.vrsta === 'LIGA')
  const turnirji = tekmovanja.filter((t) => t.vrsta === 'TURNIR')
  return [
    ...lige,
    ...turnirji.filter((t) => t.status !== 'ZAKLJUCEN'),
    ...turnirji.filter((t) => t.status === 'ZAKLJUCEN'),
  ]
}

function imeOrganizatorja(pregled: OrganizatorPregledDto, prijavnoIme: string | undefined): string {
  return pregled.ime ?? prijavnoIme ?? 'Organizator'
}

/* Naslov strani: »Organizator« v lahki, ime v težki. Razmik črk je -1,12 px
   in ne -0.035em: maketa ga postavi na h1 s privzetimi 32 px, kar podedujeta
   oba span-a (glej razdelek v slog.css). */
function Naslov({ ime }: { ime: string }) {
  return (
    <h1 className="org-pregled__naslov">
      <span className="org-pregled__naslov-nad">Organizator</span>
      <span className="org-pregled__naslov-glavni">{ime}</span>
    </h1>
  )
}

/* Lik ob palici: po vstopu izza roba stoji ~4 s in izstopi (skupaj 5 s), enkrat
   na obisk. Ob `prefers-reduced-motion` je nepremičen in stalen. Ovojni element
   drži mesto (levo, premik), notranji se giblje - tako gibanje ne prepiše
   `translateX`, ki lik postavi na palico.

   Pri tabli lik izstopi navzdol, »za palico«: ovoj odreže vse pod zgornjim
   robom palice. Sedeči lik izstopi vstran, ker mu noge visijo čez palico. Po
   koncu je element `visibility: hidden` - ne ostane niti tipka za tab niti
   klik. */
function LikKvote({
  napis,
  poza,
  levo,
  premik,
  merilo,
}: {
  napis: string
  poza: 'tabla' | 'sedi'
  levo: string
  premik: string
  merilo: number
}) {
  const gibljiv = useRef<HTMLDivElement>(null)
  const sedi = poza === 'sedi'

  useLayoutEffect(() => {
    const el = gibljiv.current
    if (!el || typeof el.animate !== 'function') return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const izhod = sedi ? 'translateX(48px)' : 'translateY(110%)'
    const gibanje = el.animate(
      [
        { transform: 'translateX(48px)', opacity: 0, visibility: 'visible', offset: 0 },
        { transform: 'none', opacity: 1, visibility: 'visible', offset: 0.1 },
        { transform: 'none', opacity: 1, visibility: 'visible', offset: 0.9 },
        { transform: izhod, opacity: sedi ? 0 : 1, visibility: 'hidden', offset: 1 },
      ],
      { duration: 5000, easing: 'ease-in-out', fill: 'forwards' },
    )
    return () => gibanje.cancel()
  }, [sedi])

  const lik = <LikNaPalici napis={napis} poza={poza} merilo={merilo} />
  return (
    <div
      className={'org-pregled__maskota' + (sedi ? '' : ' org-pregled__maskota--odrezana')}
      style={{ left: levo, transform: `translateX(${premik})` }}
    >
      <div ref={gibljiv}>
        {sedi ? (
          <Link to="/narocnina" className="org-pregled__maskota-povezava" aria-label={napis}>
            {lik}
          </Link>
        ) : (
          lik
        )}
      </div>
    </div>
  )
}

interface KvotaVrstica {
  ime: string
  oznaka: string
  kvota: KvotaDto
  vrsta: 'liga' | 'turnir'
}

/* Kolofon paketa: glava (»Organizator« + ime paketa) in dve vrstici porabe.
   Lik ob palici stoji na natanko eni palici: na prvi polni, sicer na palici
   lig. Vrstica z likom je višja, da je zanj prostor. */
function KolofonPaketa({
  pregled,
  narocnina,
  telefon,
  merilo,
}: {
  pregled: OrganizatorPregledDto
  narocnina: NarocninaDto | null
  telefon: boolean
  merilo: number
}) {
  const paket: OrganizatorPaket | null = jeOrganizatorPaket(pregled.paket) ? pregled.paket : null
  const visji = paket ? visjiPaket(paket) : null
  const vrstice: KvotaVrstica[] =
    pregled.lige && pregled.turnirji
      ? [
          { ime: 'lige', oznaka: 'Tekoče lige', kvota: pregled.lige, vrsta: 'liga' },
          {
            ime: 'turnirji',
            oznaka: `Turnirji v sezoni ${pregled.sezona}`,
            kvota: pregled.turnirji,
            vrsta: 'turnir',
          },
        ]
      : []
  const prvaPolna = vrstice.findIndex((v) => v.kvota.uporabljeno >= v.kvota.meja)
  const zLikom = prvaPolna >= 0 ? prvaPolna : 0

  return (
    <div className="org-pregled__paket">
      <div className="org-pregled__paket-glava">
        <div className="org-pregled__paket-vrsta">
          <span className="org-pregled__paket-oznaka">Moj paket</span>
          <span className={'znacka ' + (paket ? 'znacka--uspeh' : 'znacka--opozorilo')}>
            {paket ? 'Aktiven' : 'Ni aktiven'}
          </span>
        </div>
        <div className="org-pregled__paket-ime">
          <span className="org-pregled__paket-ime-nad">Organizator</span>
          <span className="org-pregled__paket-ime-glavni">
            {paket ? KRATKO_IME_PAKETA[paket] : 'Brez'}
          </span>
        </div>
      </div>

      {vrstice.map((v, i) => {
        const opis = opisKvote(v.kvota, v.vrsta, visji !== null, pregled.naslednjaSezonaOd)
        const zMaskoto = i === zLikom
        const vrh = zMaskoto
          ? opis.poln
            ? telefon ? 96 : 128
            : telefon ? 84 : 112
          : 12
        return (
          <div key={v.ime} className="org-pregled__kvota" style={{ paddingTop: vrh }}>
            <div className="org-pregled__kvota-vrsta">
              <span className="kolofon__oznaka">{v.oznaka}</span>
              <span className="kolofon__vrednost">
                {v.kvota.uporabljeno} / {v.kvota.meja}
              </span>
            </div>
            <div className="org-pregled__palica">
              <span className="palica">
                <span className="palica__polnilo" style={{ width: `${opis.delez}%` }} />
              </span>
              {zMaskoto && (
                <LikKvote
                  napis={napisTable(opis, visji !== null, pregled.naslednjaSezonaOd)}
                  poza={opis.poln ? 'sedi' : 'tabla'}
                  levo={opis.poln ? '100%' : `${opis.delez + (100 - opis.delez) / 2}%`}
                  premik={opis.poln ? '-100%' : '-50%'}
                  merilo={merilo}
                />
              )}
            </div>
            <div className="org-pregled__kvota-opomba">
              {opis.poln && <span className="znacka znacka--opozorilo">Polno</span>}
              <span>{opis.opomba}</span>
            </div>
          </div>
        )
      })}

      {telefon ? (
        <NarocninaVrstica oznaka={obnova(narocnina).oznaka} vrednost={obnova(narocnina).vrednost} />
      ) : (
        <>
          <NarocninaVrstica oznaka="Cena" vrednost={cena(narocnina)} />
          <NarocninaVrstica oznaka="Stanje" vrednost={stanje(narocnina)} />
          <NarocninaVrstica oznaka={obnova(narocnina).oznaka} vrednost={obnova(narocnina).vrednost} />
        </>
      )}

      {!telefon && (
        <div className="org-pregled__paket-gumbi">
          <Link to="/narocnina" className="gumb">
            {paket ? 'Upravljaj naročnino' : 'Izberi paket'}
          </Link>
          {visji && (
            <Link to="/narocnina" className="gumb gumb--glavni">
              Nadgradi v {KRATKO_IME_PAKETA[visji]}
            </Link>
          )}
        </div>
      )}
    </div>
  )
}

function NarocninaVrstica({ oznaka, vrednost }: { oznaka: string; vrednost: string }) {
  return (
    <div className="kolofon__vrstica">
      <span className="kolofon__oznaka">{oznaka}</span>
      <span className="kolofon__vrednost">{vrednost}</span>
    </div>
  )
}

function cena(narocnina: NarocninaDto | null): string {
  if (!narocnina || narocnina.cena === null) return '—'
  /* Prehodna doba: obstoječi organizatorji so Pro dobili brezplačno za eno leto
     (V34), cena ob sklenitvi je tam 0. */
  if (narocnina.cena === 0) return 'Brezplačno'
  return `${oblikujCeno(narocnina.cena)} / ${narocnina.ciklus === 'MESECNO' ? 'mesec' : 'leto'}`
}

function stanje(narocnina: NarocninaDto | null): string {
  if (!narocnina) return '—'
  if (narocnina.preklicana) return 'Preklicana'
  return narocnina.aktivna ? 'Aktivna' : 'Ni aktivna'
}

/* Preklicana naročnina se ne obnovi: takrat je datum konec veljave. */
function obnova(narocnina: NarocninaDto | null): { oznaka: string; vrednost: string } {
  const datum = narocnina?.obdobjeDo ? oblikujDatum(narocnina.obdobjeDo) : '—'
  return { oznaka: narocnina?.preklicana ? 'Velja do' : 'Obnova', vrednost: datum }
}

/* Dejanje »+ Nova liga« / »+ Nov turnir« z obvestilom ob polni kvoti. Skupno
   obema drevesoma: gumbe izrisuje namizje, okna pa so ista. */
function useUstvarjanje(pregled: OrganizatorPregledDto) {
  const odjemalec = useQueryClient()
  const [okno, nastaviOkno] = useState<Okno>(null)

  const polna = (kvota: KvotaDto | null) => kvota !== null && kvota.uporabljeno >= kvota.meja

  function odpri(vrsta: 'liga' | 'turnir') {
    if (!jeOrganizatorPaket(pregled.paket)) nastaviOkno('brez-paketa')
    else if (vrsta === 'liga') nastaviOkno(polna(pregled.lige) ? 'polna-liga' : 'liga')
    else nastaviOkno(polna(pregled.turnirji) ? 'polni-turnir' : 'turnir')
  }

  function poShranjevanju() {
    odjemalec.invalidateQueries({ queryKey: ['organizator-pregled'] })
    odjemalec.invalidateQueries({ queryKey: ['lige'] })
    odjemalec.invalidateQueries({ queryKey: ['turnirji'] })
  }

  const zapri = () => nastaviOkno(null)
  const okna = (
    <>
      {okno === 'liga' && <LigaObrazecOkno onZapri={zapri} onShranjeno={poShranjevanju} />}
      {okno === 'turnir' && <NovTurnirOkno onZapri={zapri} onShranjeno={poShranjevanju} />}
      {(okno === 'polna-liga' || okno === 'polni-turnir' || okno === 'brez-paketa') && (
        <ObvestiloONadgradnji vrsta={okno} pregled={pregled} onZapri={zapri} />
      )}
    </>
  )
  return { odpri, okna }
}

function ObvestiloONadgradnji({
  vrsta,
  pregled,
  onZapri,
}: {
  vrsta: 'polna-liga' | 'polni-turnir' | 'brez-paketa'
  pregled: OrganizatorPregledDto
  onZapri: () => void
}) {
  const paket = jeOrganizatorPaket(pregled.paket) ? pregled.paket : null
  const visji = paket ? visjiPaket(paket) : null
  const datum = oblikujDatum(pregled.naslednjaSezonaOd)
  let sporocilo: string
  if (vrsta === 'brez-paketa') {
    sporocilo = 'Za ustvarjanje lig in turnirjev potrebuješ veljaven organizatorski paket.'
  } else {
    const kvota = vrsta === 'polna-liga' ? pregled.lige : pregled.turnirji
    const kaj = vrsta === 'polna-liga' ? 'lig' : 'turnirjev'
    sporocilo =
      `Dosegel si mejo ${kvota?.meja} ustvarjenih ${kaj} na sezono za paket ` +
      `${paket ? OZNAKE_PAKET[paket] : ''}. ` +
      (visji
        ? `Z nadgradnjo v ${KRATKO_IME_PAKETA[visji]} jih lahko ustvariš več, sicer se meja ponastavi ${datum}.`
        : `Meja se ponastavi ${datum}.`)
  }
  return (
    <ModalnoOkno naslov={vrsta === 'polna-liga' ? 'Nova liga' : vrsta === 'polni-turnir' ? 'Nov turnir' : 'Paket'} onZapri={onZapri}>
      <p>{sporocilo}</p>
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Zapri
        </button>
        {(visji || vrsta === 'brez-paketa') && (
          <Link to="/narocnina" className="gumb gumb--glavni">
            {visji ? `Nadgradi v ${KRATKO_IME_PAKETA[visji]}` : 'Izberi paket'}
          </Link>
        )}
      </div>
    </ModalnoOkno>
  )
}

/* Naslov bloka: 3 px črta, naslov 40/800 levo, povzetek mono desno. */
function GlavaBloka({
  naslov,
  desno,
  telefon = false,
}: {
  naslov: string
  desno?: ReactNode
  telefon?: boolean
}) {
  return (
    <div className={'naslovna-vrstica org-pregled__glava' + (telefon ? ' org-pregled__glava--telefon' : '')}>
      <h2 className="org-pregled__h2">{naslov}</h2>
      {desno}
    </div>
  )
}

// ---------- Namizje ----------

function Namizje({
  pregled,
  narocnina,
}: {
  pregled: OrganizatorPregledDto
  narocnina: NarocninaDto | null
}) {
  const { uporabnik } = useAvtentikacija()
  const sezona = useIzbranaSezona(pregled)
  const tekoca = sezona === pregled.sezona
  const izbrana = tekmovanjaSezone(pregled, sezona)
  const [filter, nastaviFilter] = useState<Filter>('VSE')
  const { odpri, okna } = useUstvarjanje(pregled)

  const lige = izbrana.filter((t) => t.vrsta === 'LIGA')
  const turnirji = izbrana.filter((t) => t.vrsta === 'TURNIR')
  const cakajoce = steviloCakajocih(pregled.caka)
  const skupine = [
    {
      naslov: 'Rezultati in zapisniki',
      vrstice: pregled.caka.filter(jeRezultatAliZapisnik),
    },
    { naslov: 'Žreb čaka', vrstice: pregled.caka.filter((c) => c.vrsta === 'ZREB') },
  ].filter((s) => s.vrstice.length > 0)
  const arhivSezona = pregled.arhiv.find((a) => a.sezona === sezona)

  return (
    <section className="org-pregled">
      <div className="org-pregled__uvod">
        <div>
          <Naslov ime={imeOrganizatorja(pregled, uporabnik?.uporabniskoIme)} />
          <p className="org-pregled__podnaslov">
            {[
              pregled.klub,
              pregled.organiziraOdSezone ? `organizira od sezone ${pregled.organiziraOdSezone}` : null,
              `${pregled.skupajTekmovanj} ${sklonTekmovanj(pregled.skupajTekmovanj)}`,
            ]
              .filter(Boolean)
              .join(' · ')}
            .
          </p>

          <div className="org-pregled__pas">
            <PasCelica
              oznaka="Tekmovanj"
              stevilo={izbrana.length}
              opis={`${lige.length} ${sklonLig(lige.length)} · ${turnirji.length} ${sklonTurnirjev(turnirji.length)}`}
            />
            <PasCelica
              oznaka="Udeležencev"
              stevilo={tekoca ? pregled.udelezencev : (arhivSezona?.udelezencev ?? 0)}
              opis={`Sezona ${sezona}`}
            />
            <PasCelica
              oznaka="Odigranih tekem"
              stevilo={tekoca ? pregled.odigranihTekem : (arhivSezona?.tekem ?? 0)}
              opis={tekoca ? `${cakajoce} čaka vnos ali žreb` : `Sezona ${sezona}`}
            />
          </div>

          <Naslednje termin={pregled.naslednje} />

          <div className="org-pregled__gumbi">
            <button type="button" className="gumb gumb--glavni" onClick={() => odpri('liga')}>
              + Nova liga
            </button>
            <button type="button" className="gumb" onClick={() => odpri('turnir')}>
              + Nov turnir
            </button>
          </div>
        </div>

        <KolofonPaketa pregled={pregled} narocnina={narocnina} telefon={false} merilo={1.4} />
      </div>

      {tekoca && skupine.length > 0 && (
        <div>
          <GlavaBloka
            naslov="Čaka te"
            desno={<span className="sekcija__meta">{povzetekCakanja(pregled.caka)}</span>}
          />
          <div className="org-pregled__caka">
            {skupine.map((skupina) => (
              <div key={skupina.naslov}>
                <div className="org-pregled__oznaka-skupine">{skupina.naslov}</div>
                {skupina.vrstice.map((c) => (
                  <CakaVrstica key={`${c.vrsta}-${c.idTekmovanja}`} caka={c} />
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      <div>
        <GlavaBloka
          naslov={`Sezona ${sezona}`}
          desno={
            <div className="org-pregled__filtri">
              {(
                [
                  ['VSE', `Vse · ${izbrana.length}`],
                  ['LIGE', `Lige · ${lige.length}`],
                  ['TURNIRJI', `Turnirji · ${turnirji.length}`],
                ] as [Filter, string][]
              ).map(([kljuc, napis]) => (
                <button
                  key={kljuc}
                  type="button"
                  className={'org-pregled__filter' + (filter === kljuc ? ' org-pregled__filter--aktiven' : '')}
                  aria-pressed={filter === kljuc}
                  onClick={() => nastaviFilter(kljuc)}
                >
                  {napis}
                </button>
              ))}
            </div>
          }
        />
        {!tekoca && (
          <p className="org-pregled__pretekla">
            Pretekla sezona.{' '}
            <Link to="/moj-profil" replace>
              Nazaj na sezono {pregled.sezona}
            </Link>
          </p>
        )}
        {izbrana.length === 0 && <p className="obvestilo">V tej sezoni še ni tekmovanj.</p>}
        {filter !== 'TURNIRJI' && lige.length > 0 && (
          <SeznamSezone naslov="Lige" tekmovanja={lige} prvi />
        )}
        {filter !== 'LIGE' && turnirji.length > 0 && (
          <SeznamSezone naslov="Turnirji" tekmovanja={turnirji} prvi={filter === 'TURNIRJI' || lige.length === 0} />
        )}
      </div>

      <div className="org-pregled__dva">
        <div>
          <GlavaBloka
            naslov="Prihaja"
            desno={<span className="sekcija__meta">Naslednjih 14 dni</span>}
          />
          <div className="org-pregled__seznam">
            {pregled.prihaja.length === 0 && (
              <p className="obvestilo">V naslednjih 14 dneh ni terminov.</p>
            )}
            {pregled.prihaja.map((t) => (
              <TerminVrstica key={`${t.vrsta}-${t.idTekmovanja}-${t.kolo}`} termin={t} />
            ))}
            <Link to="/koledar" className="org-pregled__povezava">
              Celoten koledar →
            </Link>
          </div>
        </div>

        <div>
          <GlavaBloka
            naslov="Arhiv"
            desno={<span className="sekcija__meta">Pretekle sezone</span>}
          />
          <div className="org-pregled__seznam">
            {pregled.arhiv.length === 0 && <p className="obvestilo">Preteklih sezon še ni.</p>}
            {pregled.arhiv.map((a) => (
              <ArhivVrstica key={a.sezona} arhiv={a} />
            ))}
          </div>
        </div>
      </div>

      {okna}
    </section>
  )
}

function PasCelica({ oznaka, stevilo, opis }: { oznaka: string; stevilo: number; opis: string }) {
  return (
    <div className="org-pregled__pas-celica">
      <span className="org-pregled__pas-oznaka">{oznaka}</span>
      <span className="org-pregled__pas-stevilo">{stevilo}</span>
      <span className="org-pregled__pas-opis">{opis}</span>
    </div>
  )
}

/* Vrstica »Naslednje«: najbližji termin, tudi dlje od 14 dni. */
function Naslednje({ termin }: { termin: TerminPregledaDto | null }) {
  if (!termin) {
    return (
      <div className="org-pregled__naslednje org-pregled__naslednje--prazno">
        <span className="org-pregled__naslednje-oznaka">Naslednje</span>
        <span className="org-pregled__naslednje-besedilo">
          <span className="org-pregled__naslednje-ime">Ni prihajajočih terminov</span>
        </span>
      </div>
    )
  }
  const opis = opisTermina(termin)
  return (
    <Link to={potTermina(termin)} className="org-pregled__naslednje">
      <span className="org-pregled__naslednje-oznaka">Naslednje · {oblikujDanMesec(termin.datum)}</span>
      <span className="org-pregled__naslednje-besedilo">
        <span className="org-pregled__naslednje-ime">{imeTermina(termin)}</span>
        {opis && <span className="org-pregled__naslednje-opis"> · {opis}</span>}
      </span>
      <span className="org-pregled__naslednje-povezava">Odpri →</span>
    </Link>
  )
}

function CakaVrstica({ caka }: { caka: CakaPregledaDto }) {
  return (
    <div className="org-pregled__caka-vrstica">
      <span className="org-pregled__caka-stevilo">{caka.stevilo}</span>
      <span className="org-pregled__caka-besedilo">
        <span className="org-pregled__caka-ime">{caka.imeTekmovanja}</span>
        <span className="org-pregled__caka-opis">{opisCakanja(caka)}</span>
      </span>
      <Link to={potCakanja(caka)} className="gumb org-pregled__caka-gumb">
        {gumbCakanja(caka)}
      </Link>
    </div>
  )
}

function SeznamSezone({
  naslov,
  tekmovanja,
  prvi,
}: {
  naslov: string
  tekmovanja: TekmovanjePregledaDto[]
  prvi: boolean
}) {
  return (
    <>
      <div className={'org-pregled__oznaka-skupine org-pregled__oznaka-skupine--seznam' + (prvi ? ' org-pregled__oznaka-skupine--prva' : '')}>
        {naslov}
      </div>
      {tekmovanja.map((t) => (
        <VrsticaTekmovanja key={`${t.vrsta}-${t.id}`} tekmovanje={t} />
      ))}
    </>
  )
}

function VrsticaTekmovanja({ tekmovanje: t }: { tekmovanje: TekmovanjePregledaDto }) {
  const caka = cakaOrganizatorja(t)
  const koncan = t.status === 'ZAKLJUCEN'
  const besedilo = besediloCakanja(t)
  return (
    <Link
      to={potTekmovanja(t.vrsta, t.id)}
      className={
        'org-pregled__vrstica' +
        (caka ? ' org-pregled__vrstica--caka' : '') +
        (koncan ? ' org-pregled__vrstica--koncan' : '')
      }
    >
      <span>
        <span className="org-pregled__vrstica-ime">{t.ime}</span>
        <span className="org-pregled__vrstica-opis">{opisTekmovanja(t)}</span>
      </span>
      <span className={'org-pregled__vrstica-caka' + (caka ? ' org-pregled__vrstica-caka--caka' : '')}>
        {besedilo}
      </span>
      <span className="napredek">
        <span className="napredek__oznaka">{oznakaNapredka(t)}</span>
        <span className="palica">
          <span
            className={'palica__polnilo' + (koncan ? ' palica__polnilo--koncan' : '')}
            style={{ width: `${t.napredekDelez}%` }}
          />
        </span>
      </span>
      <span className="org-pregled__vrstica-status">
        <ZnackaStatusa status={t.status} />
      </span>
    </Link>
  )
}

function TerminVrstica({ termin }: { termin: TerminPregledaDto }) {
  const dan = oblikujDanMesec(termin.datum)
  const opis = opisTermina(termin)
  return (
    <Link to={potTermina(termin)} className="org-pregled__termin">
      <span>
        <span className="org-pregled__termin-dan">{dan}</span>
        <span className="org-pregled__termin-teden">{tedenInMesec(termin.datum)}</span>
      </span>
      <span>
        <span className="org-pregled__termin-ime">{imeTermina(termin)}</span>
        {opis && <span className="org-pregled__termin-opis">{opis}</span>}
      </span>
      <span
        className={
          'org-pregled__termin-vrsta ' +
          (termin.vrsta === 'TURNIR' ? 'org-pregled__termin-vrsta--turnir' : 'org-pregled__termin-vrsta--kolo')
        }
      >
        {termin.vrsta === 'TURNIR' ? 'Turnir' : 'Ligaško kolo'}
      </span>
    </Link>
  )
}

function ArhivVrstica({ arhiv }: { arhiv: ArhivSezoneDto }) {
  return (
    <Link to={`/moj-profil?sezona=${arhiv.sezona}`} className="org-pregled__arhiv">
      <span className="org-pregled__arhiv-sezona">{kratkaSezona(arhiv.sezona)}</span>
      <span>
        <span className="org-pregled__termin-ime">{obsegSezone(arhiv.lig, arhiv.turnirjev)}</span>
        <span className="org-pregled__termin-opis">{opisSezone(arhiv.udelezencev, arhiv.tekem)}</span>
      </span>
      <span className="org-pregled__naslednje-povezava">Odpri →</span>
    </Link>
  )
}

// ---------- Telefon ----------

function Telefon({
  pregled,
  narocnina,
}: {
  pregled: OrganizatorPregledDto
  narocnina: NarocninaDto | null
}) {
  const { uporabnik } = useAvtentikacija()
  const sezona = useIzbranaSezona(pregled)
  const tekoca = sezona === pregled.sezona
  const izbrana = poTelefonskemVrstnemRedu(tekmovanjaSezone(pregled, sezona))
  const najnovejsaPretekla = pregled.arhiv[0]
  const pretekleSezone = pregled.arhiv.length

  return (
    <section className="org-pregled org-pregled--telefon">
      <div>
        <Naslov ime={imeOrganizatorja(pregled, uporabnik?.uporabniskoIme)} />
        <div className="org-pregled__telefon-paket">
          <KolofonPaketa pregled={pregled} narocnina={narocnina} telefon merilo={1.05} />
        </div>
      </div>

      {tekoca && pregled.caka.length > 0 && (
        <div>
          <GlavaBloka
            telefon
            naslov="Čaka te"
            desno={<span className="sekcija__meta">{steviloCakajocih(pregled.caka)}</span>}
          />
          {pregled.caka.map((c) => (
            <Link key={`${c.vrsta}-${c.idTekmovanja}`} to={potCakanja(c)} className="org-pregled__tel-caka">
              <span className="org-pregled__tel-stevilo">{c.stevilo}</span>
              <span>
                <span className="org-pregled__tel-ime">{c.imeTekmovanja}</span>
                <span className="org-pregled__tel-kratko">{kratkoCakanje(c)}</span>
              </span>
              <span className="org-pregled__tel-puscica" aria-hidden="true">
                →
              </span>
            </Link>
          ))}
        </div>
      )}

      <div>
        <GlavaBloka
          telefon
          naslov={`Sezona ${kratkaSezona(sezona)}`}
          desno={<span className="sekcija__meta">{izbrana.length}</span>}
        />
        {!tekoca && (
          <p className="org-pregled__pretekla">
            <Link to="/moj-profil" replace>
              Nazaj na sezono {pregled.sezona}
            </Link>
          </p>
        )}
        {izbrana.length === 0 && <p className="obvestilo">V tej sezoni še ni tekmovanj.</p>}
        {izbrana.map((t) => (
          <Link
            key={`${t.vrsta}-${t.id}`}
            to={potTekmovanja(t.vrsta, t.id)}
            className={
              'org-pregled__tel-vrstica' +
              (cakaOrganizatorja(t) ? ' org-pregled__tel-vrstica--caka' : '') +
              (t.status === 'ZAKLJUCEN' ? ' org-pregled__tel-vrstica--koncan' : '')
            }
          >
            <span>
              <span className="org-pregled__tel-ime">{t.ime}</span>
              <span className="org-pregled__tel-kratko">{oznakaNapredka(t)}</span>
            </span>
            <ZnackaStatusa status={t.status} />
          </Link>
        ))}
        {tekoca && najnovejsaPretekla && (
          <Link to={`/moj-profil?sezona=${najnovejsaPretekla.sezona}`} className="org-pregled__tel-arhiv">
            <span>
              Arhiv · {pretekleSezone} {pretekleSezoneBesedilo(pretekleSezone)}
            </span>
            <span className="org-pregled__tel-puscica" aria-hidden="true">
              →
            </span>
          </Link>
        )}
      </div>
    </section>
  )
}

function pretekleSezoneBesedilo(n: number): string {
  const mod100 = n % 100
  if (mod100 === 1) return 'pretekla sezona'
  if (mod100 === 2) return 'preteki sezoni'
  if (mod100 === 3 || mod100 === 4) return 'pretekle sezone'
  return 'preteklih sezon'
}
