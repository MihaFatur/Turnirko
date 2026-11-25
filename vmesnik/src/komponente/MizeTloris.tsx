/* Namizni pogled strani Mize: glava, črna vrstica »Dvorana«, tloris in vrsta »Na vrsti«
   (prenova »tloris dvorane«, smer 1b, 24. 11. 2026; design_handoff_mize_tloris).

   Mize so narisane kot prave mize v dvorani: igralca stojita vsak na svoji strani mreže.
   Prosta miza je modro črtkana, tekmo na njo povlečeš z vrste (ali tekmo z druge mize) ali
   klikneš. Vse je POGLED: kaj je prosto, kdo je predlagan in v kakšnem vrstnem redu, odloči
   strežnik (PredlogTekem), stran pa nosi stanje (vlečenje, urejanje, okna).

   Vlečenje (HTML5): enota mize je en element, ki stanje menja samo z razredom. Med
   vlečenjem stran ne sprašuje strežnika, sicer bi osvežitev zamenjala element pod kazalcem
   in `dragleave` bi utripal; otroci tarče so zato tudi brez dogodkov kazalca (CSS).
   Klik ima enako pot brez vlečenja (tipkovnica: Enter in presledek). */
import { useEffect, useRef, useState, type DragEvent, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, TekmaMizeDto } from '../api/tipi'
import { Namig } from '../pomoc/Namig'
import { MeniDejanj } from './MeniDejanj'
import {
  MEJA_PREDOLGO_MIN,
  imenaTekme,
  jePredolgo,
  kratekDel,
  kratkoImenaTekme,
  minutOd,
  opisTekme,
  oznakaMize,
  priimkiTekme,
  razlogiZasedenosti,
  sirinaTraka,
  type Vlecem,
} from '../pomozno/mize'

/* ---------- skupno ---------- */

/* Klik z miško in tipkovnico (Enter, presledek) na elementu, ki ni <button>: enota mize je
   mreža z več elementi, ki jih gumb ne sme vsebovati. */
export function klikljivo(opis: string, naKlik?: () => void) {
  if (!naKlik) return {}
  return {
    role: 'button' as const,
    tabIndex: 0,
    'aria-label': opis,
    onClick: naKlik,
    onKeyDown: (d: KeyboardEvent<HTMLElement>) => {
      if (d.target === d.currentTarget && (d.key === 'Enter' || d.key === ' ')) {
        d.preventDefault()
        naKlik()
      }
    },
  }
}

/* Vlečenje se začne s podatkom v prenosu (brskalnik drugače vleka sploh ne začne); kaj se
   vleče, je v stanju strani. */
function zacniVlecenje(d: DragEvent<HTMLElement>, vlecem: NonNullable<Vlecem>, naZacetek: (v: Vlecem) => void) {
  d.dataTransfer.setData('text/plain', JSON.stringify(vlecem))
  d.dataTransfer.effectAllowed = 'move'
  naZacetek(vlecem)
}

export interface DejanjaTloris {
  /* Klik na kandidata ali prosto mizo s predlogom: okno klica. */
  poklici: (kandidat: KandidatMizeDto, miza: number | null) => void
  izid: (tekma: TekmaMizeDto) => void
  preklopiMizo: (miza: number, izklopi: boolean) => void
  zacniVlecenje: (v: Vlecem) => void
  konecVlecenja: () => void
  nadMizo: (miza: number | null) => void
  spusti: (miza: number) => void
}

/* ---------- glava strani ---------- */

/* Kategorija v teku, za katero se tiskajo listki (ekipna: zapisniki). */
export interface KategorijaListkov {
  id: number
  ime: string
  ekipno: boolean
}

/* Isti naslov kot na strani kategorije: ekipni dogodek tiska zapisnike. */
export function oznakaListkov(k: KategorijaListkov) {
  return k.ekipno ? 'Zapisniki za tiskanje' : 'Listki za tiskanje'
}

/* Pot do strani »Listki za tiskanje« (izbira listkov po sklopih) brez poti čez kategorijo:
   ena kategorija v teku je povezava, več jih je v spustnem meniju. Ob vsaj dveh posamičnih
   kategorijah je prva postavka en tisk za vse (skupne mize, V78: tudi kategorije drugega
   turnirja na teh mizah). */
function ListkiZaTiskanje({ kategorije, skupniTisk }: { kategorije: KategorijaListkov[]; skupniTisk?: string }) {
  if (kategorije.length === 0) return null
  if (kategorije.length === 1 && !skupniTisk) {
    return (
      <Link to={`/dogodki/${kategorije[0].id}/listki`} className="mize__zaslon-povezava">
        {oznakaListkov(kategorije[0])}
      </Link>
    )
  }
  return (
    <MeniDejanj naslov="Listki za tiskanje po kategorijah" oznaka="Listki za tiskanje" razred="mize__zaslon-povezava mize__listki">
      {(zapri) => [
        ...(skupniTisk
          ? [
              <Link key="vse" to={skupniTisk} role="menuitem" className="uporabnik-meni__postavka" onClick={zapri}>
                Vse kategorije · en tisk
              </Link>,
            ]
          : []),
        ...kategorije.map((k) => (
          <Link
            key={k.id}
            to={`/dogodki/${k.id}/listki`}
            role="menuitem"
            className="uporabnik-meni__postavka"
            onClick={zapri}
          >
            {k.ime}
            {k.ekipno ? ' · zapisniki' : ''}
          </Link>
        )),
      ]}
    </MeniDejanj>
  )
}

export function GlavaMize({
  idTurnirja,
  imeTurnirja,
  ura,
  kazalniki,
  listki,
  skupniTisk,
}: {
  idTurnirja: number
  imeTurnirja: string
  ura: string
  /* Samo, ko so mize nastavljene. */
  kazalniki?: { proste: number; vIgri: number; naVrsti: number }
  /* Kategorije v teku (gumb »Listki za tiskanje«). */
  listki: KategorijaListkov[]
  /* Pot do enega tiska vseh kategorij (skupne mize, V78). */
  skupniTisk?: string
}) {
  return (
    <div className="mize__glava">
      <div>
        <Link to={`/turnirji/${idTurnirja}`} className="povezava-nazaj mize__nazaj">
          ← Nazaj na turnir
        </Link>
        <h1 className="mize__naslov">
          Mize<Namig clanek="mize" />
        </h1>
        <p className="mize__podnaslov">
          {imeTurnirja}
          {ura && ` · ura ${ura}`}
        </p>
      </div>
      <div className="mize__glava-desno">
        {kazalniki && (
          <div className="mize__kazalniki">
            <Kazalnik vrednost={kazalniki.proste} oznaka="proste" modro />
            <Kazalnik vrednost={kazalniki.vIgri} oznaka="v igri" />
            <Kazalnik vrednost={kazalniki.naVrsti} oznaka="na vrsti" />
          </div>
        )}
        <ListkiZaTiskanje kategorije={listki} skupniTisk={skupniTisk} />
        {/* Zaslon za igralce je ločena stran celega zaslona; odpre se v novem oknu (drugi zaslon). */}
        <a
          className="mize__zaslon-povezava"
          href={`/turnirji/${idTurnirja}/mize/zaslon`}
          target="_blank"
          rel="noopener"
        >
          Zaslon za igralce ↗
        </a>
      </div>
    </div>
  )
}

function Kazalnik({ vrednost, oznaka, modro = false }: { vrednost: number; oznaka: string; modro?: boolean }) {
  return (
    <span className={'mize__kazalnik' + (modro ? ' mize__kazalnik--modro' : '')}>
      <span className="mize__kazalnik-st">{vrednost}</span>
      <span className="mize__kazalnik-oznaka">{oznaka}</span>
    </span>
  )
}

/* ---------- črna vrstica »Dvorana« ---------- */

export interface UrejanjeDvorane {
  porazenecSodi: boolean
  pocitekMin: number
  pisk: boolean
  /* Ob delu na strežniku so gumbi onemogočeni (dva hitra klika ne smeta prepisati drug drugega). */
  zasedeno: boolean
  naPorazenec: (vklopljen: boolean) => void
  naPocitek: (minute: number) => void
  naPisk: (vklopljen: boolean) => void
  odstraniZadnjo: () => void
  /* Okno »Turnirji in kategorije«: skupne mize in vrstni red kategorij (V78). */
  naSkupne: () => void
  konec: () => void
}

export function CrnaVrstica({
  urejanje,
  vlecem,
  predlogov,
  vseZasedeno,
  najdlje,
  onUredi,
  onPoklicVseh,
  poklicVsehNalaga,
}: {
  /* Podatki za urejanje; undefined = navadni pogled. */
  urejanje?: UrejanjeDvorane
  vlecem: boolean
  predlogov: number
  vseZasedeno: boolean
  najdlje: string
  onUredi: () => void
  onPoklicVseh: () => void
  poklicVsehNalaga: boolean
}) {
  return (
    <div className={'mize__dvorana' + (urejanje ? ' mize__dvorana--urejanje' : '')}>
      <div className="mize__dvorana-levo">
        <span className="mize__dvorana-ime">Dvorana</span>
        {urejanje ? (
          <span className="mize__dvorana-namig mize__dvorana-namig--urejanje">
            Klikni prosto mizo, da jo izklopiš, ali izklopljeno, da jo vklopiš.
          </span>
        ) : (
          <>
            <div className="mize__legenda">
              <span className="mize__legenda-postavka">
                <span className="mize__legenda-vzorec mize__legenda-vzorec--prosta" />
                Prosta
              </span>
              <span className="mize__legenda-postavka">
                <span className="mize__legenda-vzorec mize__legenda-vzorec--sproscena" />
                Sproščena
              </span>
              <span className="mize__legenda-postavka">
                <span className="mize__legenda-vzorec mize__legenda-vzorec--igra" />V igri
              </span>
              {/* Tri besedila v vrsti z razmikom 6 px kot pri ostalih postavkah (meja je število). */}
              <span className="mize__legenda-postavka">
                <span className="mize__legenda-vzorec mize__legenda-vzorec--predolgo" />
                <span>Nad</span>
                <span>{MEJA_PREDOLGO_MIN}</span>
                <span>min</span>
              </span>
            </div>
            <span className="mize__dvorana-namig">
              {vlecem ? 'Spusti na modro mizo.' : 'Povleci tekmo z vrste na prosto mizo.'}
            </span>
          </>
        )}
      </div>

      <div className="mize__dvorana-desno">
        {urejanje ? (
          <>
            <label className="mize__stikalo">
              <input
                type="checkbox"
                checked={urejanje.porazenecSodi}
                disabled={urejanje.zasedeno}
                onChange={(d) => urejanje.naPorazenec(d.target.checked)}
              />
              Poraženec sodi
            </label>
            <button
              type="button"
              className="mize__d-gumb"
              disabled={urejanje.zasedeno}
              onClick={urejanje.odstraniZadnjo}
            >
              − Odstrani zadnjo
            </button>
            <button type="button" className="mize__d-gumb" onClick={urejanje.naSkupne}>
              Turnirji in kategorije
            </button>
            <button type="button" className="mize__d-gumb mize__d-gumb--konec" onClick={urejanje.konec}>
              Končaj urejanje
            </button>
          </>
        ) : (
          <>
            <button type="button" className="mize__d-gumb" onClick={onUredi}>
              Uredi dvorano
            </button>
            {predlogov > 0 && (
              <button
                type="button"
                className="mize__d-gumb mize__d-gumb--glavni"
                disabled={poklicVsehNalaga}
                onClick={onPoklicVseh}
              >
                Pokliči vse predloge
                <span className="mize__d-znacka">{predlogov}</span>
              </button>
            )}
            {vseZasedeno && <span className="mize__vse-zasedeno">Vse zasedeno · {najdlje}</span>}
          </>
        )}
      </div>

      {/* Počitek in pisk sta nastavitvi, ki sta bili v obrazcu »Nastavitve miz«; funkcij ni
          mogoče izgubiti, zato stojita v drugi vrstici črne vrstice v načinu urejanja. */}
      {urejanje && (
        <div className="mize__dvorana-dodatno">
          <span className="mize__stikalo mize__korak">
            Počitek
            <button
              type="button"
              className="mize__korak-gumb"
              aria-label="Krajši počitek"
              disabled={urejanje.zasedeno || urejanje.pocitekMin <= 0}
              onClick={() => urejanje.naPocitek(urejanje.pocitekMin - 1)}
            >
              −
            </button>
            <span className="mize__korak-vrednost">{urejanje.pocitekMin} min</span>
            <button
              type="button"
              className="mize__korak-gumb"
              aria-label="Daljši počitek"
              disabled={urejanje.zasedeno || urejanje.pocitekMin >= 30}
              onClick={() => urejanje.naPocitek(urejanje.pocitekMin + 1)}
            >
              +
            </button>
          </span>
          <label className="mize__stikalo">
            <input type="checkbox" checked={urejanje.pisk} onChange={(d) => urejanje.naPisk(d.target.checked)} />
            Pisk ob prosti mizi
          </label>
        </div>
      )}
    </div>
  )
}

/* ---------- tloris ---------- */

export function Tloris({
  podatki,
  predlogi,
  poudarjene,
  urejanje,
  vlecem,
  nadMizo,
  dejanja,
  naDodaj,
}: {
  podatki: MizeTurnirjaDto
  /* Prvi predlog proste mize (številka mize → kandidat). */
  predlogi: Map<number, KandidatMizeDto>
  poudarjene: Set<number>
  urejanje: boolean
  vlecem: boolean
  nadMizo: number | null
  dejanja: DejanjaTloris
  naDodaj: () => void
}) {
  return (
    <div className="mize__tloris">
      <ol className="mize__mreza">
        {podatki.mize.map((m) => (
          <EnotaMize
            key={m.stevilka}
            miza={m}
            casStreznika={podatki.casStreznika}
            predlog={predlogi.get(m.stevilka)}
            poudarjena={poudarjene.has(m.stevilka)}
            urejanje={urejanje}
            vlecem={vlecem}
            nadMizo={nadMizo === m.stevilka}
            dejanja={dejanja}
          />
        ))}
        {urejanje && (
          <li className="mize__dodaj-ovoj">
            <button type="button" className="mize__dodaj" onClick={naDodaj}>
              + Miza {podatki.mize.length + 1}
            </button>
          </li>
        )}
      </ol>
    </div>
  )
}

function EnotaMize({
  miza,
  casStreznika,
  predlog,
  poudarjena,
  urejanje,
  vlecem,
  nadMizo,
  dejanja,
}: {
  miza: MizaDto
  casStreznika: string
  predlog: KandidatMizeDto | undefined
  poudarjena: boolean
  urejanje: boolean
  vlecem: boolean
  nadMizo: boolean
  dejanja: DejanjaTloris
}) {
  const st = miza.stevilka
  const t = miza.tekma

  if (miza.izklopljena) {
    return (
      <li className="mize__miza mize__miza--izklopljena">
        <div
          className="mize__miza-vsebina"
          {...klikljivo(`Miza ${st}, izklopljena. Vklopi`, urejanje ? () => dejanja.preklopiMizo(st, false) : undefined)}
        >
          <div className="mize__miza-vrh">
            <span className="mize__miza-st">{st}</span>
            <span className="mize__miza-stanje">Izklopljena</span>
          </div>
          <div className="mize__pravokotnik">{urejanje ? 'Vklopi' : ''}</div>
        </div>
      </li>
    )
  }

  if (t) {
    /* Druga miza ekipnega srečanja je narisana skupaj s prvo. */
    if (t.miza !== st) return null
    const minute = minutOd(t.poklicanaOb, casStreznika) ?? 0
    const [pa, pb] = priimkiTekme(t)
    const del = kratekDel(t.del)
    const vsebina = {
      draggable: true,
      onDragStart: (d: DragEvent<HTMLElement>) => zacniVlecenje(d, { tip: 'm', miza: st }, dejanja.zacniVlecenje),
      onDragEnd: dejanja.konecVlecenja,
      ...klikljivo(`Miza ${oznakaMize(t)}, ${imenaTekme(t)}, ${minute} min. Vnesi izid`, () => dejanja.izid(t)),
    }

    if (t.ekipno) {
      return (
        <li className="mize__miza mize__miza--ekipno">
          <div className="mize__miza-vsebina" {...vsebina}>
            <div className="mize__miza-vrh">
              <span className="mize__miza-st">{oznakaMize(t)}</span>
              <span className="mize__miza-stanje mize__miza-stanje--ekipno">
                {t.imeDogodka} · {del} · dve mizi · {minute}′
              </span>
            </div>
            <div className="mize__ekipno">
              <PravokotnikIgre levo={pa} desno={pb} />
              <span className="mize__ekipno-vez" />
              <PravokotnikIgre levo={pa} desno={pb} />
            </div>
          </div>
        </li>
      )
    }

    const dolgo = jePredolgo(t, minute)
    return (
      <li className="mize__miza">
        <div className="mize__miza-vsebina" {...vsebina}>
          <div className="mize__miza-vrh">
            <span className="mize__miza-st">{st}</span>
            <span className={'mize__miza-stanje mize__miza-minute' + (dolgo ? ' mize__miza-minute--dolgo' : '')}>
              {minute}′
            </span>
          </div>
          <PravokotnikIgre
            levo={pa}
            desno={pb}
            kategorija={t.imeDogodka}
            del={del}
            dolgo={dolgo}
            trak={dolgo ? undefined : sirinaTraka(minute)}
          />
        </div>
      </li>
    )
  }

  /* Prosta miza. Urejanje je lastno stanje; sicer je to isti element v stanjih prosta,
     sproščena in tarča (med vlečenjem). */
  if (urejanje) {
    return (
      <li className="mize__miza">
        <div
          className="mize__miza-vsebina"
          {...klikljivo(`Miza ${st}, prosta. Izklopi`, () => dejanja.preklopiMizo(st, true))}
        >
          <div className="mize__miza-vrh">
            <span className="mize__miza-st">{st}</span>
            <span className="mize__miza-stanje mize__miza-stanje--urejanje">Prosta</span>
          </div>
          <div className="mize__pravokotnik mize__pravokotnik--uredi">Izklopi</div>
        </div>
      </li>
    )
  }

  const prostaMinut = minutOd(miza.prostaOd, casStreznika)
  const naKlik = predlog ? () => dejanja.poklici(predlog, st) : undefined
  const razred = vlecem ? 'tarca' : poudarjena ? 'sproscena' : 'prosta'
  const stanje = vlecem
    ? 'Spusti sem'
    : poudarjena
      ? 'Sproščena'
      : 'Prosta' + (prostaMinut ? ` · ${prostaMinut}′` : '')
  return (
    <li className={'mize__miza mize__miza--' + razred}>
      <div
        className="mize__miza-vsebina"
        {...klikljivo(predlog ? `Miza ${st}, prosta. Pokliči ${imenaTekme(predlog.tekma)}` : `Miza ${st}, prosta`, vlecem ? undefined : naKlik)}
      >
        <div className="mize__miza-vrh">
          <span className="mize__miza-st">{st}</span>
          <span className="mize__miza-stanje">{stanje}</span>
        </div>
        <div
          className={
            'mize__pravokotnik mize__pravokotnik--' + razred + (vlecem && nadMizo ? ' mize__pravokotnik--nad' : '')
          }
          onDragOver={(d) => {
            if (!vlecem) return
            d.preventDefault()
            d.dataTransfer.dropEffect = 'move'
            if (!nadMizo) dejanja.nadMizo(st)
          }}
          onDragLeave={() => {
            if (nadMizo) dejanja.nadMizo(null)
          }}
          onDrop={(d) => {
            if (!vlecem) return
            d.preventDefault()
            dejanja.spusti(st)
          }}
        >
          {vlecem ? (
            <span className="mize__tarca-st">{st}</span>
          ) : (
            <>
              <span className="mize__predlog">{predlog ? kratkoImenaTekme(predlog.tekma) : 'Ni primerne tekme'}</span>
              <span className="mize__akcija">{predlog ? 'Pokliči ›' : 'Povleci tekmo sem'}</span>
            </>
          )}
        </div>
      </div>
    </li>
  )
}

/* Črna miza z mrežo na sredini; imena stojijo vsako na svoji strani. */
function PravokotnikIgre({
  levo,
  desno,
  kategorija,
  del,
  dolgo = false,
  trak,
}: {
  levo: string
  desno: string
  kategorija?: string
  del?: string
  dolgo?: boolean
  /* Širina modrega traku časa v odstotkih; brez njega (predolgo, ekipno) traku ni. */
  trak?: number
}) {
  return (
    <div className={'mize__pravokotnik mize__pravokotnik--igra' + (dolgo ? ' mize__pravokotnik--dolgo' : '')}>
      <span className="mize__mreza-sredina" />
      <span className="mize__igralec">{levo}</span>
      <span className="mize__igralec">{desno}</span>
      {kategorija !== undefined && (
        <>
          <span className="mize__podatek mize__podatek--levo">{kategorija}</span>
          <span className="mize__podatek mize__podatek--desno">{del}</span>
        </>
      )}
      {trak !== undefined && <span className="mize__trak" style={{ width: `${trak}%` }} />}
    </div>
  )
}

/* ---------- vrsta »Na vrsti« ---------- */

/* Najvec toliko kartic je vidnih, dokler organizator ne pokaže vseh. */
export const NAJVEC_KARTIC = 10

/* Vrsta »Na vrsti«. Kartico organizator povleče na prosto mizo (klic) ali na drugo mesto v
   vrsti (ročni vrstni red, lastnik 24. 11. 2026); s tipkovnico Alt + ↑ / ↓. Mesto vstavitve
   je med karticami: zgornja polovica kartice pomeni »pred njo«, spodnja »za njo«. */
export function VrstaTekem({
  kandidati,
  predlagane,
  vse,
  dejanja,
  naPreklopVseh,
  dogodkiVTeku,
  vlecem,
  rocniRed,
  naPremik,
  naSamodejno,
}: {
  kandidati: KandidatMizeDto[]
  /* Id tekme → miza, za katero je tekma prvi predlog. */
  predlagane: Map<number, number>
  vse: boolean
  dejanja: DejanjaTloris
  naPreklopVseh: () => void
  /* Ali kakšna kategorija turnirja teče (sicer vrsta ne more imeti tekem). */
  dogodkiVTeku: boolean
  vlecem: Vlecem
  /* Vrsto je uredil organizator (sicer samodejni vrstni red strežnika). */
  rocniRed: boolean
  /* Tekmo postavi na mesto `mesto` (indeks vstavitve v trenutni vrsti, 0 = na vrh). */
  naPremik: (idTekme: number, mesto: number) => void
  naSamodejno: () => void
}) {
  const vidni = vse ? kandidati : kandidati.slice(0, NAJVEC_KARTIC)
  const [mesto, nastaviMesto] = useState<number | null>(null)
  const seznam = useRef<HTMLOListElement>(null)
  /* Po premiku s tipkovnico ostane fokus na premaknjeni kartici (React jo pri
     preurejanju premakne v drevesu in brskalnik ji fokus vzame). */
  const fokus = useRef<number | null>(null)
  useEffect(() => {
    if (fokus.current === null) return
    const el = seznam.current?.querySelector<HTMLElement>(`[data-tekma="${fokus.current}"]`)
    if (el && document.activeElement !== el) el.focus()
    fokus.current = null
  }, [kandidati])

  const vlecemKartico = vlecem?.tip === 'k' ? vlecem.id : null
  const indeksVlecene = vlecemKartico === null ? -1 : kandidati.findIndex((k) => k.tekma.tekma.id === vlecemKartico)
  /* Spust na isto mesto ni premik: kazalnika ni. */
  const brezUcinka = (m: number) => indeksVlecene >= 0 && (m === indeksVlecene || m === indeksVlecene + 1)
  const cilj = mesto !== null && !brezUcinka(mesto) ? mesto : null
  useEffect(() => {
    if (vlecemKartico === null) nastaviMesto(null)
  }, [vlecemKartico])

  const premakniSTipko = (i: number, smer: -1 | 1) => {
    const id = kandidati[i].tekma.tekma.id
    const nov = smer < 0 ? i - 1 : i + 2
    if (nov < 0 || nov > kandidati.length) return
    fokus.current = id
    naPremik(id, nov)
  }

  return (
    <aside className="mize__vrsta">
      <div className="mize__vrsta-glava">
        <h2 className="mize__vrsta-naslov">Na vrsti</h2>
        <span className="mize__vrsta-stevec">{kandidati.length}</span>
      </div>
      <p className="mize__vrsta-namig">
        Primi tekmo in jo spusti na prosto mizo ali na drugo mesto v vrsti. Številka desno je predlagana miza.
      </p>
      {rocniRed && (
        <p className="mize__vrsta-rocno">
          <span>Vrstni red si uredil ročno.</span>
          <button type="button" className="mize__vrsta-samodejno" onClick={naSamodejno}>
            Samodejni vrstni red
          </button>
        </p>
      )}
      <ol
        className="mize__vrsta-seznam"
        ref={seznam}
        onDragLeave={(d) => {
          if (!d.currentTarget.contains(d.relatedTarget as Node | null)) nastaviMesto(null)
        }}
      >
        {vidni.map((k, i) => {
          const id = k.tekma.tekma.id
          const razred =
            cilj === i ? ' mize__vrsta-mesto--pred' : cilj === i + 1 && i === vidni.length - 1 ? ' mize__vrsta-mesto--za' : ''
          return (
            <li
              key={id}
              className={'mize__vrsta-mesto' + razred}
              onDragOver={(d) => {
                if (vlecemKartico === null) return
                d.preventDefault()
                d.dataTransfer.dropEffect = 'move'
                const r = d.currentTarget.getBoundingClientRect()
                const m = d.clientY < r.top + r.height / 2 ? i : i + 1
                if (m !== mesto) nastaviMesto(m)
              }}
              onDrop={(d) => {
                if (vlecemKartico === null) return
                d.preventDefault()
                const r = d.currentTarget.getBoundingClientRect()
                const m = d.clientY < r.top + r.height / 2 ? i : i + 1
                nastaviMesto(null)
                dejanja.konecVlecenja()
                if (!brezUcinka(m)) naPremik(vlecemKartico, m)
              }}
            >
              <KarticaKandidata
                kandidat={k}
                miza={predlagane.get(id)}
                dejanja={dejanja}
                naTipko={(smer) => premakniSTipko(i, smer)}
              />
            </li>
          )
        })}
      </ol>
      {kandidati.length === 0 && (
        <p className="mize__vrsta-namig">
          {dogodkiVTeku
            ? 'Ni pripravljenih tekem: vse tekme se igrajo ali čakajo na prejšnja kola.'
            : 'Nobena kategorija tega turnirja še ne teče. Ko izžrebaš kategorijo, se tu pokažejo pripravljene tekme.'}
        </p>
      )}
      {kandidati.length > NAJVEC_KARTIC && (
        <button type="button" className="mize__vrsta-vec" onClick={naPreklopVseh}>
          {vse ? 'Pokaži manj' : `Pokaži vseh ${kandidati.length}`}
        </button>
      )}
    </aside>
  )
}

function KarticaKandidata({
  kandidat,
  miza,
  dejanja,
  naTipko,
}: {
  kandidat: KandidatMizeDto
  miza: number | undefined
  dejanja: DejanjaTloris
  /* Alt + ↑ / ↓: premik za eno mesto v vrsti. */
  naTipko: (smer: -1 | 1) => void
}) {
  const t = kandidat.tekma
  const poklici = () => dejanja.poklici(kandidat, miza ?? null)
  return (
    <div
      className={'mize__kandidat' + (kandidat.zaseden ? ' mize__kandidat--zaseden' : '')}
      data-tekma={t.tekma.id}
      draggable
      onDragStart={(d) => zacniVlecenje(d, { tip: 'k', id: t.tekma.id }, dejanja.zacniVlecenje)}
      onDragEnd={dejanja.konecVlecenja}
      {...klikljivo(`${imenaTekme(t)}. Pokliči na mizo. Alt in puščica gor ali dol premakne tekmo v vrsti`, poklici)}
      onKeyDown={(d) => {
        if (d.altKey && (d.key === 'ArrowUp' || d.key === 'ArrowDown')) {
          d.preventDefault()
          naTipko(d.key === 'ArrowUp' ? -1 : 1)
        } else if (d.target === d.currentTarget && (d.key === 'Enter' || d.key === ' ')) {
          d.preventDefault()
          poklici()
        }
      }}
    >
      <span className="mize__rocaj" aria-hidden="true">
        ⋮⋮
      </span>
      <div className="mize__kandidat-besedilo">
        <p className="mize__kandidat-imena">{imenaTekme(t)}</p>
        <p className={'mize__kandidat-opis' + (kandidat.zaseden ? ' mize__kandidat-opis--razlog' : '')}>
          {kandidat.zaseden ? razlogiZasedenosti(kandidat).join(' · ') : opisTekme(t)}
        </p>
      </div>
      {!kandidat.zaseden && miza !== undefined && <span className="mize__kandidat-miza">{miza}</span>}
    </div>
  )
}

/* ---------- sporočilo z razveljavitvijo ---------- */

export interface SporociloMize {
  besedilo: string
  razveljavi?: () => void
}

export function SporociloZRazveljavitvijo({
  sporocilo,
  naZapri,
}: {
  sporocilo: SporociloMize
  naZapri: () => void
}): ReactNode {
  return (
    <div className="mize__sporocilo" role="status">
      <span>{sporocilo.besedilo}</span>
      {sporocilo.razveljavi && (
        <button
          type="button"
          className="mize__sporocilo-gumb"
          onClick={() => {
            sporocilo.razveljavi?.()
            naZapri()
          }}
        >
          Razveljavi
        </button>
      )}
    </div>
  )
}
