/* Stran »Listki za tiskanje« in »Zapisniki za tiskanje« (prenova »Listki 1b«, 23. 11. 2026).

   Organizator mora na prvi pogled videti stanje tiska: skupine v vrsticah, kola v stolpcih, vsak
   listek je ploščica (bela = še ni natisnjen, modra = za tisk, siva = natisnjen). Klik na
   ploščico, skupino ali kolo izbere; natisnjene lahko izbereš znova za ponovni tisk. Desni
   stolpec nosi število listkov za tisk, sličice listov A4, hitro izbiro in glavni gumb.
   Po tisku stolpec zamenja vprašanje, ali je tisk uspel (brskalnik tega ne pove).

   Velikega predogleda listkov na zaslonu ni več: listi A4 se izrišejo samo za tisk
   (ListkiStran, @media print). Ta komponenta je samo zaslonski del; izbira je v
   useIzbiraListkov, listi pa ostanejo, kot so bili. Ista stran velja za ekipne zapisnike
   (vrsta »zapisnik«): le besedi sta drugi in listov z listki ni. */
import { useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import {
  LISTKOV_NA_LIST,
  NAJVEC_PRAZNIH_LISTKOV,
  enoteListkov,
  enotaNaVrsti,
  jeNatisnjen,
  kratekCasNatisa,
  nenatisnjeni,
  praznihZaTisk,
  sklonListovTiska,
  tabelaListkov,
  type EnotaListkov,
  type PripravljenListek,
} from '../pomozno/listki'
import { useKompakten } from '../pomozno/telefon'
import { Namig } from '../pomoc/Namig'
import { BESEDILA, type Izbira, type IzbiraRazvrstitve, type VrstaListka } from './IzbiraListkov'
import { SporociloNapake } from './SporociloNapake'

export interface PrazniListki {
  stevilo: number
  nastavi: (n: number) => void
  /* Zadnji list dopolni s praznimi listki do polnega lista. */
  dopolni: boolean
  preklopiDopolni: () => void
}

interface Lastnosti {
  vrsta: VrstaListka
  /* »Jesenski turnir NTK Celje · Moški posamezno«. */
  nadnaslov: string
  naslov: string
  povezavaNazaj: string
  listki: PripravljenListek[]
  izbira: Izbira
  razvrstitev?: IzbiraRazvrstitve
  /* Prazni listki za ročni vpis; ekipni zapisniki jih nimajo. */
  prazni?: PrazniListki
  /* Koda za sodnike na hrbtu; samo ko sodniki slikajo listke. */
  hrbet?: { vklopljen: boolean; preklopi: () => void }
  /* Opozorilo nad tabelo (kode za sodnike se niso naložile). */
  opozorilo?: ReactNode
  /* Opomba pod nastavitvami (npr. kje vklopiš kode za sodnike). */
  opomba?: ReactNode
  /* Dodatne nastavitve ekipnih zapisnikov (obrazec, zapisnikov na list). */
  nastavitve?: ReactNode
  /* Vrstica pod velikim številom namesto »3 listi A4« (ekipni zapisniki). */
  meta?: string
  /* Mono navodilo nad glavnim gumbom. */
  navodilo: string
  /* Tisk še ni mogoč (kode se nalagajo), čeprav je kaj izbrano. */
  tiskCaka?: boolean
  onNatisni: () => void
  /* Po tisku: potrdi (true) ali tisk ni uspel (false). */
  onPotrdi: (natisnil: boolean) => void
}

const sklonListkovBesede = (vrsta: VrstaListka, n: number) => BESEDILA[vrsta].sklon(n)

/* Ploščica listka: dve vrstici priimkov in miza ali čas tiska. */
function Ploscica({
  l,
  izbira,
  telefon,
}: {
  l: PripravljenListek
  izbira: Izbira
  telefon: boolean
}) {
  const id = l.tekma.id
  const izbran = izbira.izbrani.has(id)
  const caka = !!izbira.caka?.includes(id)
  const natisnjen = jeNatisnjen(l)
  const imeStrani = (u: PripravljenListek['tekma']['udelezenec1']) =>
    u ? (u.priimek2 ? `${u.priimek} / ${u.priimek2}` : u.priimek) : '—'
  const a = imeStrani(l.tekma.udelezenec1)
  const b = imeStrani(l.tekma.udelezenec2)
  const meta =
    natisnjen && !izbran && l.tekma.listekNatisnjenOb
      ? `✓ ${kratekCasNatisa(l.tekma.listekNatisnjenOb)}`
      : l.tekma.miza !== null
        ? `M${l.tekma.miza}`
        : ''
  return (
    <button
      type="button"
      className={
        'listki-ploscica'
        + (telefon ? ' listki-ploscica--telefon' : '')
        + (caka ? ' listki-ploscica--caka' : izbran ? ' listki-ploscica--izbrana' : natisnjen ? ' listki-ploscica--natisnjena' : '')
      }
      aria-pressed={izbran}
      aria-label={`${a} proti ${b}${meta ? `, ${meta}` : ''}`}
      onClick={() => {
        if (!izbira.caka) izbira.preklopi([id], !izbran)
      }}
    >
      <span className="listki-ploscica__imena">
        <span>{a}</span>
        <span>{b}</span>
      </span>
      <span className="listki-ploscica__meta">{meta}</span>
    </button>
  )
}

export function ListkiPloskev({
  vrsta,
  nadnaslov,
  naslov,
  povezavaNazaj,
  listki,
  izbira,
  razvrstitev,
  prazni,
  hrbet,
  opozorilo,
  opomba,
  nastavitve,
  meta,
  navodilo,
  tiskCaka,
  onNatisni,
  onPotrdi,
}: Lastnosti) {
  const telefon = useKompakten()
  const { tozilnik } = BESEDILA[vrsta]

  const tabela = tabelaListkov(listki)
  const enote = enoteListkov(tabela)
  const naVrsti = enotaNaVrsti(enote)

  /* Izbrano / natisnjeno po enotah (stolpec tabele ali vrstica pod njo). */
  const idji = (l: PripravljenListek[]) => l.map((x) => x.tekma.id)
  const vseIzbrane = (ids: number[]) => ids.length > 0 && ids.every((id) => izbira.izbrani.has(id))
  /* Klik na skupino ali kolo: izbere nenatisnjene ali, če jih ni, vse (ponovni tisk); če so
     že izbrani, jih odznači. */
  const preklopiSklop = (sklop: PripravljenListek[]) => {
    if (izbira.caka) return
    const nenat = idji(nenatisnjeni(sklop))
    const ids = nenat.length ? nenat : idji(sklop)
    izbira.preklopi(ids, !vseIzbrane(ids))
  }

  const izbranih = izbira.izbraniListki.length
  const praznihSkupaj = prazni ? praznihZaTisk(izbranih, prazni.stevilo, prazni.dopolni) : 0
  const zaTisk = izbranih + praznihSkupaj
  const steviloListov = meta !== undefined ? 0 : Math.ceil(zaTisk / LISTKOV_NA_LIST)
  const vrzel = prazni ? (LISTKOV_NA_LIST - ((izbranih + prazni.stevilo) % LISTKOV_NA_LIST)) % LISTKOV_NA_LIST : 0
  const nenatisnjenihIzbranih = izbira.izbraniListki.filter(jeNatisnjen)
  const neMorem = zaTisk === 0 || !!tiskCaka || !!izbira.caka

  /* Glavni gumb: če so vsi izbrani listki iz iste enote, se pove, katere. */
  const enotaKljuc = new Map<number, EnotaListkov>()
  enote.forEach((e) => e.listki.forEach((l) => enotaKljuc.set(l.tekma.id, e)))
  const enoteIzbranih = new Set(izbira.izbraniListki.map((l) => enotaKljuc.get(l.tekma.id)?.kljuc))
  const enotaIzbranih = enoteIzbranih.size === 1 ? enotaKljuc.get(izbira.izbraniListki[0].tekma.id) : undefined
  const besediloGumba = neMorem && zaTisk === 0
    ? `Izberi ${tozilnik}`
    : `Natisni ${zaTisk} ${sklonListkovBesede(vrsta, zaTisk)}${enotaIzbranih && !praznihSkupaj ? ` · ${enotaIzbranih.naslov}` : ''}`

  const [zavihek, nastaviZavihek] = useState<string | null>(null)

  const vprasanje = izbira.caka ? `Si natisnil ${izbira.caka.length} ${sklonListkovBesede(vrsta, izbira.caka.length)}?` : ''

  const naTisk = (
    <button
      type="button"
      className="listki-ploskev__natisni"
      disabled={neMorem}
      onClick={onNatisni}
    >
      {besediloGumba}
    </button>
  )

  /* ---------- Telefon: zavihki enot, skupine v dveh stolpcih, lepljiva noga ---------- */
  if (telefon) {
    const aktivna = enote.find((e) => e.kljuc === zavihek) ?? naVrsti ?? enote[0]
    const vseVEnoti = aktivna ? idji(nenatisnjeni(aktivna.listki)) : []
    const celo = vseIzbrane(vseVEnoti)
    const vrsteEnote = aktivna && tabela.kola.length > 0 && aktivna.kljuc.startsWith('kolo-')
      ? tabela.vrste.map((v) => ({
          kljuc: v.kljuc,
          naslov: v.naslovDolgo,
          listki: v.celice[tabela.kola.indexOf(Number(aktivna.kljuc.slice(5)))] ?? [],
        }))
      : aktivna
        ? [{ kljuc: aktivna.kljuc, naslov: aktivna.naslov, listki: aktivna.listki }]
        : []
    return (
      <div className="listki-ploskev listki-ploskev--telefon zaslon-samo">
        <header className="listki-ploskev__glava">
          <Link to={povezavaNazaj} className="listki-ploskev__nazaj" aria-label="Nazaj na dogodek">
            ←
          </Link>
          <h1 className="listki-ploskev__naslov">{naslov}</h1>
        </header>
        {enote.length > 0 && (
          <div className="listki-ploskev__zavihki" role="tablist" aria-label="Kola">
            {enote.map((e) => {
              const nenat = nenatisnjeni(e.listki)
              const izb = e.listki.filter((l) => izbira.izbrani.has(l.tekma.id)).length
              const vse = nenat.length === 0
              return (
                <button
                  key={e.kljuc}
                  type="button"
                  role="tab"
                  aria-selected={aktivna?.kljuc === e.kljuc}
                  className={'listki-ploskev__zavihek' + (aktivna?.kljuc === e.kljuc ? ' listki-ploskev__zavihek--aktiven' : '')}
                  onClick={() => nastaviZavihek(e.kljuc)}
                >
                  <span className="listki-ploskev__zavihek-naslov">{e.naslov}</span>
                  <span className="listki-ploskev__zavihek-meta">{vse ? '✓ natisnjeno' : `${izb}/${nenat.length}`}</span>
                </button>
              )
            })}
          </div>
        )}
        {opozorilo && <div className="listki-ploskev__opozorilo">{opozorilo}</div>}
        {aktivna && (
          <div className="listki-ploskev__orodja-telefon">
            <button
              type="button"
              className="listki-ploskev__celo"
              onClick={() => preklopiSklop(aktivna.listki)}
            >
              {celo
                ? `Odznači ${aktivna.naslov}`
                : aktivna.kljuc.startsWith('kolo-') ? `Izberi celo ${aktivna.naslov}` : `Izberi: ${aktivna.naslov}`}
            </button>
            <button
              type="button"
              className="listki-ploskev__pocisti"
              disabled={izbira.izbrani.size === 0}
              onClick={() => izbira.nastavi([])}
            >
              Počisti
            </button>
          </div>
        )}
        <div className="listki-ploskev__skupine-telefon">
          {enote.length === 0 && (
            <p className="obvestilo">Ni pripravljenih tekem za tiskanje.</p>
          )}
          {vrsteEnote.map((v) => {
            const izb = v.listki.filter((l) => izbira.izbrani.has(l.tekma.id)).length
            return (
              <div
                key={v.kljuc}
                className={'listki-ploskev__skupina-telefon' + (vrsteEnote.length === 1 && tabela.kola.length === 0 ? ' listki-ploskev__skupina-telefon--cela' : '')}
              >
                <button type="button" className="listki-ploskev__skupina-glava" onClick={() => preklopiSklop(v.listki)}>
                  <span className="listki-ploskev__skupina-ime">{v.naslov}</span>
                  <span className="listki-ploskev__skupina-meta">{izb ? `${izb}/${v.listki.length}` : ''}</span>
                </button>
                {v.listki.map((l) => (
                  <Ploscica key={l.tekma.id} l={l} izbira={izbira} telefon />
                ))}
              </div>
            )
          })}
        </div>
        <SporociloNapake napaka={izbira.napaka} />
        <div className="listki-ploskev__noga-telefon">
          <span className="listki-ploskev__noga-stevilo">
            <span className="listki-ploskev__noga-st">{zaTisk}</span>
            <span className="listki-ploskev__noga-meta">
              {meta ?? `${sklonListkovBesede(vrsta, zaTisk)} · ${steviloListov} ${sklonListovTiska(steviloListov)} A4`}
            </span>
          </span>
          <button
            type="button"
            className="listki-ploskev__natisni listki-ploskev__natisni--telefon"
            disabled={neMorem}
            onClick={onNatisni}
          >
            Natisni
          </button>
        </div>
        {izbira.caka && (
          <div className="listki-ploskev__zastor">
            <div className="listki-ploskev__list" role="dialog" aria-label="Po tisku">
              <span className="listki-ploskev__vprasanje listki-ploskev__vprasanje--telefon">{vprasanje}</span>
              <span className="listki-ploskev__pojasnilo">Potrjeni postanejo sivi in jih naslednjič ne izbere.</span>
              <button type="button" className="listki-ploskev__da listki-ploskev__da--telefon" onClick={() => onPotrdi(true)}>
                Da, natisnjeno
              </button>
              <button type="button" className="listki-ploskev__ne" onClick={() => onPotrdi(false)}>
                Ne, tisk ni uspel
              </button>
            </div>
          </div>
        )}
      </div>
    )
  }

  /* ---------- Namizje ---------- */
  const kolaStolpcev = tabela.kola.length
  const slogMreze = { '--stolpcev': Math.max(kolaStolpcev, 1) } as CSSProperties
  const nenatisnjeniVsi = idji(nenatisnjeni(listki))

  return (
    <div className="listki-ploskev zaslon-samo">
      <header className="listki-ploskev__glava">
        <Link to={povezavaNazaj} className="listki-ploskev__nazaj">
          ← Dogodek
        </Link>
        <div className="listki-ploskev__naslovi">
          <span className="listki-ploskev__nad">{nadnaslov}</span>
          <h1 className="listki-ploskev__naslov">{naslov}</h1>
        </div>
        <div className="listki-ploskev__legenda" aria-hidden="true">
          <span className="listki-ploskev__legenda-vnos">
            <span className="listki-ploskev__legenda-kvadratek listki-ploskev__legenda-kvadratek--izbran" />Za tisk
          </span>
          <span className="listki-ploskev__legenda-vnos">
            <span className="listki-ploskev__legenda-kvadratek" />Še ni natisnjen
          </span>
          <span className="listki-ploskev__legenda-vnos">
            <span className="listki-ploskev__legenda-kvadratek listki-ploskev__legenda-kvadratek--natisnjen" />Natisnjen
          </span>
        </div>
      </header>

      <div className="listki-ploskev__telo">
        <div className="listki-ploskev__levo">
          {opozorilo && <div className="listki-ploskev__opozorilo">{opozorilo}</div>}
          {enote.length === 0 ? (
            <p className="obvestilo">
              Ni pripravljenih tekem za tiskanje. Listek se ustvari za tekmo, kjer sta znana oba
              udeleženca in še ni odigrana.
            </p>
          ) : (
            <>
              <div className="listki-tabela" style={slogMreze}>
                {kolaStolpcev > 0 && (
                  <div className="listki-tabela__glava">
                    <span className="listki-tabela__skupina">Skupina</span>
                    {tabela.kola.map((k, i) => {
                      const e = enote[i]
                      const nenat = nenatisnjeni(e.listki)
                      const izb = e.listki.filter((l) => izbira.izbrani.has(l.tekma.id)).length
                      const vse = nenat.length === 0
                      return (
                        <button
                          key={k}
                          type="button"
                          className={'listki-tabela__kolo' + (izb ? ' listki-tabela__kolo--izbrano' : '')}
                          onClick={() => preklopiSklop(e.listki)}
                        >
                          <span className="listki-tabela__kolo-naslov">{e.naslov}</span>
                          <span className="listki-tabela__kolo-meta">
                            {vse ? `✓ vseh ${e.listki.length}` : izb ? `${izb} izbranih` : `${nenat.length} čaka`}
                          </span>
                        </button>
                      )
                    })}
                  </div>
                )}
                <div className="listki-tabela__vrste">
                  {tabela.vrste.map((v) => {
                    const vsi = v.celice.flat()
                    const izb = vsi.filter((l) => izbira.izbrani.has(l.tekma.id)).length
                    return (
                      <div key={v.kljuc} className="listki-tabela__vrsta">
                        <button type="button" className="listki-tabela__oznaka" onClick={() => preklopiSklop(vsi)}>
                          <span className="listki-tabela__oznaka-ime">{v.naslov}</span>
                          <span className="listki-tabela__oznaka-meta">{izb || ''}</span>
                        </button>
                        {v.celice.map((celica, i) => (
                          <div key={tabela.kola[i]} className="listki-tabela__celica">
                            {celica.map((l) => (
                              <Ploscica key={l.tekma.id} l={l} izbira={izbira} telefon={false} />
                            ))}
                          </div>
                        ))}
                      </div>
                    )
                  })}
                  {tabela.ostali.map((o) => (
                    <div key={o.kljuc} className="listki-tabela__vrsta listki-tabela__vrsta--ostali">
                      <button type="button" className="listki-tabela__oznaka" onClick={() => preklopiSklop(o.listki)}>
                        <span className="listki-tabela__oznaka-ime listki-tabela__oznaka-ime--ostali">{o.naslov}</span>
                        <span className="listki-tabela__oznaka-meta">
                          {o.listki.filter((l) => izbira.izbrani.has(l.tekma.id)).length || ''}
                        </span>
                      </button>
                      <div className="listki-tabela__ostali">
                        {o.listki.map((l) => (
                          <Ploscica key={l.tekma.id} l={l} izbira={izbira} telefon={false} />
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <span className="listki-ploskev__namig">
                Klikni {vrsta === 'listek' ? 'listek' : 'zapisnik'}, skupino ali kolo. Natisnjene lahko izbereš znova za ponovni tisk.
              </span>
            </>
          )}
        </div>

        <aside className="listki-ploskev__desno" aria-label="Tisk">
          {izbira.caka ? (
            <div className="listki-ploskev__po-tisku" role="status">
              <span className="listki-ploskev__oznaka">Po tisku</span>
              <span className="listki-ploskev__vprasanje">{vprasanje}</span>
              <span className="listki-ploskev__pojasnilo">Potrjeni postanejo sivi in jih naslednjič ne izbere.</span>
              <button type="button" className="listki-ploskev__da" onClick={() => onPotrdi(true)}>
                Da, natisnjeno
              </button>
              <button type="button" className="listki-ploskev__ne" onClick={() => onPotrdi(false)}>
                Ne, tisk ni uspel
              </button>
            </div>
          ) : (
            <>
              <div className="listki-ploskev__stolpec">
                <div className="listki-ploskev__za-tisk">
                  <span className="listki-ploskev__oznaka">
                    Za tisk
                    <Namig clanek="listki" />
                  </span>
                  <span className="listki-ploskev__stevilo-vrstica">
                    <span className="listki-ploskev__stevilo">{zaTisk}</span>
                    <span className="listki-ploskev__stevilo-beseda">{sklonListkovBesede(vrsta, zaTisk)}</span>
                  </span>
                  <span className="listki-ploskev__listi-meta">
                    {meta ?? `${steviloListov} ${sklonListovTiska(steviloListov)} A4${praznihSkupaj ? ` · od tega ${praznihSkupaj} praznih` : ''}`}
                  </span>
                  {izbira.smemOznacevati && nenatisnjenihIzbranih.length > 0 && (
                    <button
                      type="button"
                      className="listki-ploskev__odznaci"
                      disabled={izbira.oznacuje}
                      onClick={() => izbira.odznaci(idji(nenatisnjenihIzbranih))}
                    >
                      {nenatisnjenihIzbranih.length} izbranih je že natisnjenih · Odznači natisnjene
                    </button>
                  )}
                </div>

                {meta === undefined && (
                  <div className="listki-ploskev__listi" aria-hidden="true">
                    {Array.from({ length: steviloListov }, (_, i) => (
                      <div key={i} className="listki-ploskev__list-slicica">
                        {Array.from({ length: LISTKOV_NA_LIST }, (_, j) => {
                          const n = i * LISTKOV_NA_LIST + j
                          return (
                            <span
                              key={j}
                              className={n < izbranih ? 'listki-ploskev__mesto--izbran' : n < zaTisk ? 'listki-ploskev__mesto--prazen' : undefined}
                            />
                          )
                        })}
                      </div>
                    ))}
                  </div>
                )}

                {prazni && zaTisk > 0 && izbranih > 0 && (vrzel > 0 || prazni.dopolni) && (
                  <button
                    type="button"
                    className={'listki-ploskev__dopolni' + (prazni.dopolni ? ' listki-ploskev__dopolni--vklopljen' : '')}
                    aria-pressed={prazni.dopolni}
                    onClick={prazni.preklopiDopolni}
                  >
                    <span className="listki-ploskev__kljukica" aria-hidden="true">{prazni.dopolni ? '✓' : ''}</span>
                    {prazni.dopolni
                      ? `Zadnji list dopolnjen s praznimi (${praznihSkupaj - prazni.stevilo})`
                      : `Zadnji list ima ${vrzel} ${vrzel === 1 ? 'prosto mesto' : vrzel === 2 ? 'prosti mesti' : 'prosta mesta'}: dopolni s praznimi`}
                  </button>
                )}

                <div className="listki-ploskev__razdelek">
                  <span className="listki-ploskev__oznaka">Hitra izbira</span>
                  <button
                    type="button"
                    className="listki-ploskev__na-vrsti"
                    disabled={!naVrsti}
                    onClick={() => {
                      if (naVrsti) {
                        izbira.nastavi(idji(nenatisnjeni(naVrsti.listki)))
                        nastaviZavihek(null)
                      }
                    }}
                  >
                    <span>Na vrsti: {naVrsti?.naslov ?? '—'}</span>
                    <span className="listki-ploskev__na-vrsti-st">
                      {naVrsti ? `${nenatisnjeni(naVrsti.listki).length} ${sklonListkovBesede(vrsta, nenatisnjeni(naVrsti.listki).length)}` : ''}
                    </span>
                  </button>
                  <div className="listki-ploskev__hitra">
                    <button
                      type="button"
                      className="listki-ploskev__manjsi"
                      disabled={nenatisnjeniVsi.length === 0}
                      onClick={() => izbira.nastavi(nenatisnjeniVsi)}
                    >
                      Vsi nenatisnjeni
                    </button>
                    <button
                      type="button"
                      className="listki-ploskev__manjsi"
                      disabled={izbira.izbrani.size === 0}
                      onClick={() => izbira.nastavi([])}
                    >
                      Počisti
                    </button>
                  </div>
                </div>

                {prazni && (
                  <div className="listki-ploskev__vrstica">
                    <span className="listki-ploskev__vrstica-besedilo">
                      <span className="listki-ploskev__vrstica-naslov">Prazni listki</span>
                      <span className="listki-ploskev__vrstica-pod">za ročni vpis</span>
                    </span>
                    <div className="listki-ploskev__koracnik" role="group" aria-label="Število praznih listkov">
                      <button
                        type="button"
                        className="listki-ploskev__koracnik-gumb"
                        aria-label="Manj praznih listkov"
                        onClick={() => prazni.nastavi(Math.max(0, prazni.stevilo - 1))}
                      >
                        −
                      </button>
                      <span className="listki-ploskev__koracnik-st" aria-live="polite">{prazni.stevilo}</span>
                      <button
                        type="button"
                        className="listki-ploskev__koracnik-gumb"
                        aria-label="Več praznih listkov"
                        onClick={() => prazni.nastavi(Math.min(NAJVEC_PRAZNIH_LISTKOV, prazni.stevilo + 1))}
                      >
                        +
                      </button>
                    </div>
                  </div>
                )}

                {razvrstitev && (
                  <div className="listki-ploskev__vrstica">
                    <span className="listki-ploskev__vrstica-naslov" id="listki-vrstni-red">Vrstni red na listih</span>
                    <div className="listki-ploskev__izbirnik" role="group" aria-labelledby="listki-vrstni-red">
                      {(
                        [
                          ['kola', 'Po kolih', '1. kolo skupine 1, 2, 3 …, nato 2. kolo …'],
                          ['skupine', 'Po skupinah', 'Vsa kola skupine 1, nato skupine 2 …'],
                        ] as const
                      ).map(([vrednost, oznaka, opis]) => (
                        <button
                          key={vrednost}
                          type="button"
                          title={opis}
                          className={'listki-ploskev__red' + (razvrstitev.vrednost === vrednost ? ' listki-ploskev__red--aktiven' : '')}
                          aria-pressed={razvrstitev.vrednost === vrednost}
                          onClick={() => razvrstitev.nastavi(vrednost)}
                        >
                          {oznaka}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {nastavitve}

                {hrbet && (
                  <button
                    type="button"
                    className="listki-ploskev__hrbet"
                    aria-pressed={hrbet.vklopljen}
                    onClick={hrbet.preklopi}
                  >
                    <span
                      className={'listki-ploskev__kljukica listki-ploskev__kljukica--velika' + (hrbet.vklopljen ? ' listki-ploskev__kljukica--vklopljena' : '')}
                      aria-hidden="true"
                    >
                      {hrbet.vklopljen ? '✓' : ''}
                    </span>
                    Koda za sodnike na hrbtu
                  </button>
                )}

                {opomba}
                <span className="listki-ploskev__navodilo">{navodilo}</span>
                <SporociloNapake napaka={izbira.napaka} />
              </div>
              <div className="listki-ploskev__noga">{naTisk}</div>
            </>
          )}
        </aside>
      </div>
    </div>
  )
}
