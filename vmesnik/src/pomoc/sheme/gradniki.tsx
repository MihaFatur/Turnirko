/* Gradniki narisanih prikazov pomoči.

   Prikazi so v HTML in CSS (razredi `.sh-*` v slog.css, razdelek »Pomoč«), ne
   v slikah: ostanejo ostri na telefonu, berejo iste barve in pisave kot
   aplikacija in se ne postarajo ob vsaki spremembi zaslona, kot bi se posnetki
   (odločitev lastnika). Pomen barv je hišni: modra = dejanje ali poudarek,
   zelena = napredovanje, uspeh, rjasta = izguba, napaka, nepovratno.

   Prikaz je za bralnik zaslona ENA slika z opisom (`role="img"`), zato
   notranji deli nimajo svojih vlog. */
import type { ReactNode } from 'react'

export type Ton = 'modra' | 'zelena' | 'rjasta' | 'siva' | 'zelena-mehko' | 'rjasta-mehko'

export function Platno({
  opis,
  children,
  razred,
}: {
  opis: string
  children: ReactNode
  razred?: string
}) {
  return (
    <div className={'sh' + (razred ? ' ' + razred : '')} role="img" aria-label={opis}>
      {children}
    </div>
  )
}

/* Oštevilčena oznaka, na katero se sklicujejo koraki pod prikazom. */
export function Oznaka({ st }: { st: number | string }) {
  return <span className="sh-oznaka">{st}</span>
}

/* Maketa zaslona ali okna: mono naslov nad 3 px črto, belo telo. */
export function Okno({ naslov, children }: { naslov: string; children: ReactNode }) {
  return (
    <div className="sh-okno">
      <div className="sh-okno__vrh">{naslov}</div>
      <div className="sh-okno__telo">{children}</div>
    </div>
  )
}

export function Gumb({
  children,
  glavni,
  st,
  majhen,
}: {
  children: ReactNode
  glavni?: boolean
  st?: number
  majhen?: boolean
}) {
  return (
    <span className="sh-gumb-ovoj">
      <span className={'sh-gumb' + (glavni ? ' sh-gumb--glavni' : '') + (majhen ? ' sh-gumb--majhen' : '')}>
        {children}
      </span>
      {st !== undefined && <Oznaka st={st} />}
    </span>
  )
}

export function Polje({ oznaka, vrednost, st, siroko }: { oznaka?: string; vrednost: ReactNode; st?: number; siroko?: boolean }) {
  return (
    <span className={'sh-polje-ovoj' + (siroko ? ' sh-polje-ovoj--siroko' : '')}>
      {oznaka && <span className="sh-polje__oznaka">{oznaka}</span>}
      <span className="sh-polje">
        {vrednost}
        {st !== undefined && <Oznaka st={st} />}
      </span>
    </span>
  )
}

export function Znacka({ children, ton = 'siva' }: { children: ReactNode; ton?: Ton }) {
  return <span className={'sh-znacka sh-znacka--' + ton}>{children}</span>
}

/* Mono oznaka nad delom prikaza. */
export function Napis({ children }: { children: ReactNode }) {
  return <span className="sh-napis">{children}</span>
}

export function Skatla({
  naslov,
  children,
  ton,
  crtkano,
}: {
  naslov?: ReactNode
  children?: ReactNode
  ton?: Ton
  crtkano?: boolean
}) {
  return (
    <div className={'sh-skatla' + (ton ? ' sh-skatla--' + ton : '') + (crtkano ? ' sh-skatla--crtkano' : '')}>
      {naslov && <div className="sh-skatla__naslov">{naslov}</div>}
      {children}
    </div>
  )
}

export function Puscica({ smer = 'desno', oznaka }: { smer?: 'desno' | 'dol'; oznaka?: ReactNode }) {
  return (
    <span className={'sh-puscica sh-puscica--' + smer}>
      {oznaka && <span className="sh-puscica__oznaka">{oznaka}</span>}
      <span className="sh-puscica__znak">{smer === 'desno' ? '→' : '↓'}</span>
    </span>
  )
}

/* Vodoravni potek: koraki s puščicami (na ozkem zaslonu se prelomijo). */
export function Potek({
  koraki,
}: {
  koraki: { naslov: string; postavke?: string[]; ton?: Ton; prehod?: string }[]
}) {
  return (
    <div className="sh-potek">
      {koraki.map((k, i) => (
        <div key={i} className="sh-potek__del">
          {i > 0 && <Puscica />}
          {/* Kaj sproži prehod, stoji v škatli: oznaka ob puščici je v ozkem
              oblačku pojedla širino škatle. */}
          <Skatla naslov={k.naslov} ton={k.ton}>
            {k.prehod && <span className="sh-potek__prehod">po: {k.prehod}</span>}
            {k.postavke && (
              <ul className="sh-postavke">
                {k.postavke.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            )}
          </Skatla>
        </div>
      ))}
    </div>
  )
}

/* Preprosta tabela prikaza; `toni` obarva cele vrstice. */
export function Tabela({
  glava,
  vrstice,
  toni,
  sredina,
}: {
  glava?: ReactNode[]
  vrstice: ReactNode[][]
  toni?: (Ton | undefined)[]
  sredina?: boolean
}) {
  return (
    <table className={'sh-tabela' + (sredina ? ' sh-tabela--sredina' : '')}>
      {glava && (
        <thead>
          <tr>
            {glava.map((c, i) => (
              <th key={i}>{c}</th>
            ))}
          </tr>
        </thead>
      )}
      <tbody>
        {vrstice.map((v, i) => (
          <tr key={i} className={toni?.[i] ? 'sh-tabela__vrstica--' + toni[i] : undefined}>
            {v.map((c, j) => (
              <td key={j}>{c}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/* Pomanjšan list papirja (A4 pokončno ali ležeče). */
export function List({ lezeci, naslov, children }: { lezeci?: boolean; naslov?: string; children: ReactNode }) {
  return (
    <div className="sh-list-ovoj">
      <div className={'sh-list' + (lezeci ? ' sh-list--lezeci' : '')}>{children}</div>
      {naslov && <span className="sh-napis">{naslov}</span>}
    </div>
  )
}

export function Legenda({ postavke }: { postavke: [number | string, string][] }) {
  return (
    <ol className="sh-legenda">
      {postavke.map(([st, besedilo]) => (
        <li key={st}>
          <Oznaka st={st} />
          <span>{besedilo}</span>
        </li>
      ))}
    </ol>
  )
}

/* Izločilna mreža v SVG: kola od leve proti desni, vsak dvoboj dve vrstici.
   Mere so v enotah viewBoxa, zato se mreža skrči na širino oblačka. */
export interface DvobojMreze {
  a: string
  b: string
  /* Kateri igralec je poudarjen (zmagovalec ali nosilec, o katerem govori besedilo). */
  poudarek?: 'a' | 'b'
  ton?: 'modra' | 'zelena' | 'rjasta'
  /* Besedilo desno od dvoboja (izid). */
  izid?: string
}

export function MrezaSvg({ kola, naslovi }: { kola: DvobojMreze[][]; naslovi?: string[] }) {
  const VRSTICA = 18
  const SIRINA_POLJA = 96
  const RAZMAK = 22
  const prvo = kola[0].length
  const visina = prvo * 2 * VRSTICA + (prvo - 1) * 10 + (naslovi ? 18 : 0)
  const zamik = naslovi ? 18 : 0
  const sirina = kola.length * SIRINA_POLJA + (kola.length - 1) * RAZMAK
  /* Navpično središče dvoboja i v kolu k: kolo k ima 2^k-krat manj dvobojev. */
  const sredina = (k: number, i: number) => {
    const blok = (prvo / kola[k].length) * (2 * VRSTICA + 10)
    return zamik + i * blok + blok / 2 - 5
  }
  return (
    <svg
      className="sh-mreza"
      viewBox={`0 0 ${sirina} ${visina}`}
      preserveAspectRatio="xMinYMin meet"
      /* Dve koli se ne smeta raztegniti čez ves prikaz (velika pisava). */
      style={{ maxWidth: Math.round(sirina * 1.2) }}
    >
      {naslovi?.map((n, k) => (
        <text key={n} className="sh-mreza__kolo" x={k * (SIRINA_POLJA + RAZMAK)} y={11}>
          {n}
        </text>
      ))}
      {kola.map((kolo, k) =>
        kolo.map((d, i) => {
          const x = k * (SIRINA_POLJA + RAZMAK)
          const y = sredina(k, i) - VRSTICA
          const razred = (stran: 'a' | 'b') =>
            'sh-mreza__ime' + (d.poudarek === stran ? ' sh-mreza__ime--' + (d.ton ?? 'modra') : '')
          return (
            <g key={`${k}-${i}`}>
              <rect className={'sh-mreza__polje' + (d.ton === 'rjasta' && !d.poudarek ? ' sh-mreza__polje--rjasta' : '')} x={x} y={y} width={SIRINA_POLJA} height={2 * VRSTICA} />
              <line className="sh-mreza__loc" x1={x} x2={x + SIRINA_POLJA} y1={y + VRSTICA} y2={y + VRSTICA} />
              <text className={razred('a')} x={x + 5} y={y + 12.5}>
                {d.a}
              </text>
              <text className={razred('b')} x={x + 5} y={y + VRSTICA + 12.5}>
                {d.b}
              </text>
              {d.izid && (
                <text className="sh-mreza__izid" x={x + SIRINA_POLJA - 4} y={y + 12.5} textAnchor="end">
                  {d.izid}
                </text>
              )}
              {k < kola.length - 1 && (
                <path
                  className="sh-mreza__crta"
                  d={`M${x + SIRINA_POLJA} ${sredina(k, i)} H${x + SIRINA_POLJA + RAZMAK / 2} V${sredina(k + 1, Math.floor(i / 2))} H${x + SIRINA_POLJA + RAZMAK}`}
                />
              )}
            </g>
          )
        }),
      )}
    </svg>
  )
}
