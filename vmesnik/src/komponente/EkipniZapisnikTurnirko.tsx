/* Turnirkov ekipni zapisnik za tisk (ležeči A4): drugačen izgled istega
   zapisnika, kot ga drži liga Savinja na papirju - glava, obe ekipi s postavo
   (dvojica, igralci, rezerva), mreža tekem z nizi in stanjem dvoboja, noga z
   rezultatom, lokacijo, datumom, uro in kolom. Glava nosi znamko Turnirka,
   tekmovanje z logotipom (V54: liga ali turnir ekipnega dogodka) pa še njega.

   Podatke bere kot uradni obrazec NTZS (ZapisnikEkipnegaDvoboja): kar je
   vpisano, izpiše, ostalo ostane prazno za ročni vpis, obrazec pa se natisne
   tudi pred postavo. Polja, ki jih ne hranimo (lokacija, rezerva), ostanejo
   črte. Zapisnik je črnilo na papirju ne glede na temo; barvna sta samo
   znamka in logotip tekmovanja.

   List je natanko ena ležeča stran A4 znotraj robov 10 mm: višina vrstic mreže
   se razdeli po višini, ki ostane (CSS grid), zato je zapisnik s petimi
   vrsticami (Savinja) zračen, s sedmimi in več (SNTL) pa se stisne. */
import { Fragment, type CSSProperties } from 'react'

import type { LigaDto, SlikaDto, SrecanjePodrobnoDto, StranEkipe } from '../api/tipi'
import { RAZPORED_FORMATA } from '../api/tipi'
import { usePokrovitelje } from '../pomozno/pokrovitelji'
import { urlSlike } from '../pomozno/slike'
import { razbijCas, sestaviPodatkeZapisnika, stanjaDvoboja } from '../pomozno/zapisnikPodatki'
import { PokroviteljiTisk } from './PokroviteljiTisk'
import { ZnakTurnirko } from './Postavitev'

/* Od te vrstice naprej (SNTL z desetimi tekmami) se glava in noga zgostita,
   da vrstice mreže ostanejo dovolj visoke za pisanje z roko. */
const VRSTIC_ZA_GOSTO = 8

/* Dvojica je vrstica z dvema igralcema na vsaki strani in dobi več višine. */
const TEZA_DVOJICE = 1.6

/* Ime ekipe je čim večje (do najvecje velikosti v pt), a mora v svoj stolpec
   (polovica lista, ~130 mm): dolgo ime je manjše, a nikoli pod najmanjšo.
   Širina znaka v Bricolage 800 je ~0,55 em, 620 pa vključuje rezervo za široke
   črke (W, M) in velike začetnice; kar tudi tako ne gre, odreže tropičje. */
const NAJVECJE_IME_PT = 28
const NAJVECJE_IME_GOSTO_PT = 22
const NAJMANJSE_IME_PT = 13

function velikostImenaPt(ime: string, najvec: number): number {
  return Math.max(NAJMANJSE_IME_PT, Math.min(najvec, 620 / Math.max(ime.length, 1)))
}

export function EkipniZapisnikTurnirko({
  podrobno,
  liga,
  logotip,
}: {
  podrobno: SrecanjePodrobnoDto
  liga: LigaDto | undefined
  /* Logotip lige oz. turnirja ekipnega dogodka (V54); stran ga ima pri roki. */
  logotip: SlikaDto | null
}) {
  const s = podrobno.srecanje
  const { prazen, nizi, vrstice, imeNa } = sestaviPodatkeZapisnika(podrobno)
  const stanja = stanjaDvoboja(vrstice)
  const { datum, ura } = razbijCas(s.predvidenZacetek)

  const jeLiga = s.idLiga != null
  /* Pokrovitelji lige oz. turnirja ekipnega dogodka (V56) v vrstici pod nogo. */
  const pokrovitelji = usePokrovitelje(jeLiga ? 'liga' : 'turnir', jeLiga ? s.idLiga : s.idTurnir)
  const tekmovanje = liga?.ime ?? podrobno.kontekst.tekmovanje
  /* Pod naslovom: sezona lige oz. kategorija turnirja. */
  const podnaslov = jeLiga ? (liga?.sezona ?? podrobno.kontekst.sezona) : podrobno.kontekst.dogodek
  const rednoKolo = jeLiga && s.idSerija == null
  const kolo = rednoKolo ? `${s.kolo}.` : (podrobno.kontekst.opis ?? `${s.kolo}.`)

  const imaDvojice = RAZPORED_FORMATA[podrobno.format].includes('dvojice')
  const koncan = s.status === 'KONCANO'
  const gosto = vrstice.length >= VRSTIC_ZA_GOSTO
  const najvecImePt = gosto ? NAJVECJE_IME_GOSTO_PT : NAJVECJE_IME_PT

  const vrsticeMreze = ['auto', ...vrstice.map((v) => (v.dvojice ? `minmax(7mm, ${TEZA_DVOJICE}fr)` : 'minmax(6.4mm, 1fr)'))]

  return (
    <article
      className={['ekipni-zapisnik', gosto && 'ekipni-zapisnik--gosto', prazen && 'ekipni-zapisnik--prazen']
        .filter(Boolean)
        .join(' ')}
    >
      <header className="ekipni-zapisnik__glava">
        <span className="ekipni-zapisnik__znamka">
          <ZnakTurnirko velikost={28} />
          Turnirko
        </span>
        <div className="ekipni-zapisnik__naslov">
          <p className="ekipni-zapisnik__tekmovanje">{tekmovanje}</p>
          <p className="ekipni-zapisnik__vrsta">Zapisnik ekipnega dvoboja</p>
          {podnaslov && <p className="ekipni-zapisnik__podnaslov">{podnaslov}</p>}
        </div>
        {logotip ? (
          <img
            className="ekipni-zapisnik__logotip"
            src={urlSlike(logotip, 'TISK')}
            width={logotip.sirina}
            height={logotip.visina}
            alt={`Logotip: ${tekmovanje}`}
          />
        ) : (
          <span />
        )}
      </header>

      <div className="ekipni-zapisnik__ekipi">
        <Ekipa
          oznaka="Ekipa A"
          ime={s.domaci}
          imePt={velikostImenaPt(s.domaci, najvecImePt)}
          vrstice={vrsticePostave(podrobno, 'DOMACI', imaDvojice, imeNa)}
        />
        <Ekipa
          oznaka="Ekipa X"
          ime={s.gost}
          imePt={velikostImenaPt(s.gost, najvecImePt)}
          vrstice={vrsticePostave(podrobno, 'GOST', imaDvojice, imeNa)}
        />
      </div>

      <div
        className="ekipni-zapisnik__tekme"
        style={
          {
            '--nizov': nizi.length,
            gridTemplateRows: vrsticeMreze.join(' '),
          } as CSSProperties
        }
      >
        <Glava>Ekipa A</Glava>
        <Glava>Igralec</Glava>
        <Glava>Ekipa X</Glava>
        <Glava>Igralec</Glava>
        {nizi.map((n) => (
          <Glava key={n}>{`${n}. niz`}</Glava>
        ))}
        <Glava>Rezultat</Glava>
        <Glava>Stanje dvoboja</Glava>

        {vrstice.map((v, i) => {
          const [mestoDomaci, mestoGost] = v.oznaka.split('-')
          return (
            <Fragment key={v.kljuc}>
              <Celica razred="ekipni-zapisnik__celica--mesto">{v.dvojice ? 'Par' : mestoDomaci}</Celica>
              <Imena igralci={v.domaciIgralci} dvojice={v.dvojice} />
              <Celica razred="ekipni-zapisnik__celica--mesto">{v.dvojice ? 'Par' : (mestoGost ?? '')}</Celica>
              <Imena igralci={v.gostIgralci} dvojice={v.dvojice} />
              {/* Že vneseno se izpiše, ostalo ostane prazna celica za ročni
                  vpis - obrazec se natisne tudi pred srečanjem. */}
              {nizi.map((n) => {
                const niz = v.nizi[n - 1]
                return (
                  <Celica key={n} razred="ekipni-zapisnik__celica--niz">
                    {niz ? `${niz.tocke1}:${niz.tocke2}` : ''}
                  </Celica>
                )
              })}
              <Celica razred="ekipni-zapisnik__celica--igre">{v.igre}</Celica>
              <Celica razred="ekipni-zapisnik__celica--stanje">{stanja[i]}</Celica>
            </Fragment>
          )
        })}
      </div>

      {/* Noga: rezultat dvoboja in podatki o srečanju (lokacija, datum, ura,
          kolo). Vrstica je na dnu lista, da imata postavi ekip nad mrežo več
          prostora za vpis. */}
      <footer className="ekipni-zapisnik__noga">
        <div className="ekipni-zapisnik__izid">
          <span className="ekipni-zapisnik__oznaka">Rezultat dvoboja</span>
          <span className="ekipni-zapisnik__izid-polje">{koncan ? s.dobljeneDomaci : ''}</span>
          <span className="ekipni-zapisnik__izid-dvopicje">:</span>
          <span className="ekipni-zapisnik__izid-polje">{koncan ? s.dobljeneGost : ''}</span>
        </div>
        <Polje oznaka="Lokacija" razred="ekipni-zapisnik__polje--siroko" />
        <Polje oznaka="Datum" vrednost={datum} razred="ekipni-zapisnik__polje--datum" />
        <Polje oznaka="Ura" vrednost={ura} razred="ekipni-zapisnik__polje--ura" />
        <Polje oznaka={rednoKolo ? 'Kolo' : 'Faza'} vrednost={kolo} razred="ekipni-zapisnik__polje--kolo" />
      </footer>

      <PokroviteljiTisk pokrovitelji={pokrovitelji} razred="ekipni-zapisnik__pokrovitelji" />
    </article>
  )
}

interface VrsticaPostave {
  oznaka: string
  ime: string
}

/* Postava ene ekipe, kot jo ima papirni zapisnik: dvojica (kadar jo format
   pozna), igralci po mestih in rezerva. Dvojica je izpisana iz postave
   (kdor je v dvojici), rezerva pa ni podatek postave - ostane črta. */
function vrsticePostave(
  podrobno: SrecanjePodrobnoDto,
  stran: StranEkipe,
  imaDvojice: boolean,
  imeNa: (stran: StranEkipe, poz: string) => string,
): VrsticaPostave[] {
  const pozicije = stran === 'DOMACI' ? podrobno.pozicijeDomaci : podrobno.pozicijeGost
  const vDvojici = pozicije
    .filter((p) => podrobno.postave.some((m) => m.stran === stran && m.pozicija === p && m.vDvojici))
    .map((p) => imeNa(stran, p))
    .filter(Boolean)
    .join(' / ')
  return [
    ...(imaDvojice ? [{ oznaka: 'Dvojica', ime: vDvojici }] : []),
    ...pozicije.map((p) => ({ oznaka: p, ime: imeNa(stran, p) })),
    { oznaka: 'Rezerva', ime: '' },
  ]
}

function Ekipa({
  oznaka,
  ime,
  imePt,
  vrstice,
}: {
  oznaka: string
  ime: string
  imePt: number
  vrstice: VrsticaPostave[]
}) {
  return (
    <section className="ekipni-zapisnik__ekipa">
      <div className="ekipni-zapisnik__ekipa-glava">
        <span className="ekipni-zapisnik__oznaka">{oznaka}</span>
        <span className="ekipni-zapisnik__ekipa-ime" style={{ fontSize: `${imePt}pt` }}>
          {ime}
        </span>
      </div>
      <div className="ekipni-zapisnik__postava">
        {vrstice.map((v) => (
          <Fragment key={v.oznaka}>
            <span className="ekipni-zapisnik__postava-oznaka">{v.oznaka}</span>
            <span className="ekipni-zapisnik__vpis">{v.ime}</span>
          </Fragment>
        ))}
      </div>
    </section>
  )
}

function Polje({ oznaka, vrednost = '', razred }: { oznaka: string; vrednost?: string; razred: string }) {
  return (
    <div className={`ekipni-zapisnik__polje ${razred}`}>
      <span className="ekipni-zapisnik__oznaka">{oznaka}</span>
      <span className="ekipni-zapisnik__vpis">{vrednost}</span>
    </div>
  )
}

function Glava({ children }: { children: string }) {
  return <div className="ekipni-zapisnik__celica ekipni-zapisnik__celica--glava">{children}</div>
}

function Celica({ razred, children }: { razred: string; children?: string | number }) {
  return <div className={`ekipni-zapisnik__celica ${razred}`}>{children}</div>
}

/* Celica z imeni ene strani tekme: pri dvojici dve vrstici (po ena na igralca),
   sicer ena. Manjkajoče ime je prazna vrstica za ročni vpis. */
function Imena({ igralci, dvojice }: { igralci: string[]; dvojice: boolean }) {
  const vrstic = dvojice ? 2 : 1
  return (
    <div className="ekipni-zapisnik__celica ekipni-zapisnik__celica--imena">
      {Array.from({ length: vrstic }, (_, i) => (
        <span key={i} className="ekipni-zapisnik__ime">
          <span className="ekipni-zapisnik__ime-besedilo">{igralci[i] ?? ''}</span>
        </span>
      ))}
    </div>
  )
}
