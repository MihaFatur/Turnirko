/* Ligaški zapisnik za bralnik (V79): ležeči A4, narisan kot ena risba SVG v mm
   natanko po postavitvi, ki jo pove strežnik (NatisZapisnikaDto.postavitev,
   GeometrijaZapisnika.java). Bralnik išče oznake, kvadratke in celice na istih
   legah, zato tu ni nobene lastne mere: kar je v postavitvi, se nariše, ostalo
   je samo besedilo (imena, oznake tekem, navodilo).

   Dve obliki (lastnik 26. 11. 2026): VRSTICE - vrstica tekme (križci domačih,
   točke nizov, križci gostov v isti vrsti, imena kadra so glave stolpcev) in
   KADER - tabela kadra (igralci so vrstice, tekme stolpci) nad tabelo točk, za
   večji kader. Zadnja vrstica kadra je prazna: za igralca, ki ga v kadru še ni.

   Črte so 0,45 mm v SVG (obroba CSS bi se pri tisku zaokrožila in tanka črta se
   na fotografiji raztrga), oznake so polni črni kvadrati, QR je pot SVG. List je
   črnilo na papirju ne glede na temo. */
import type { BesediloZapisnika, NatisZapisnikaDto, PravokotnikMm } from '../api/tipi'
import { potQr } from '../pomozno/kodaListka'
import { razbijCas } from '../pomozno/zapisnikPodatki'

/* Velikost pisave v mm (SVG je v mm). */
const PISAVA_IME = 2.7
const PISAVA_OZNAKA = 2.8

export function ZapisnikZaBralnik({ natis }: { natis: NatisZapisnikaDto }) {
  const p = natis.postavitev
  const qr = potQr(natis.koda)
  const pol = p.crta / 2
  return (
    <svg
      className="zapisnik-bralnik"
      viewBox={`0 0 ${p.sirina} ${p.visina}`}
      width={`${p.sirina}mm`}
      height={`${p.visina}mm`}
      role="img"
      aria-label={`Zapisnik za bralnik: ${natis.domaci} : ${natis.gost}`}
    >
      <rect x={0} y={0} width={p.sirina} height={p.visina} fill="#fff" />
      {p.oznake.map(([x, y], i) => (
        <rect
          key={`o${i}`}
          x={x - p.stranicaOznake / 2}
          y={y - p.stranicaOznake / 2}
          width={p.stranicaOznake}
          height={p.stranicaOznake}
          fill="#000"
        />
      ))}
      <g transform={`translate(${p.qr.x} ${p.qr.y}) scale(${p.qr.sirina / qr.velikost})`}>
        <path d={qr.pot} fill="#000" />
      </g>
      {p.pasovi.map((pas, i) =>
        [pas.levo, pas.desno].map(([x, y], j) => (
          <rect
            key={`p${i}-${j}`}
            x={x - p.stranicaOznakeVrste / 2}
            y={y - p.stranicaOznakeVrste / 2}
            width={p.stranicaOznakeVrste}
            height={p.stranicaOznakeVrste}
            fill="#000"
          />
        )),
      )}
      <g fill="none" stroke="#000" strokeWidth={p.crta}>
        {p.kvadratki.map((k, i) => (
          <rect key={`k${i}`} x={k.p.x + pol} y={k.p.y + pol} width={k.p.sirina - p.crta} height={k.p.visina - p.crta} />
        ))}
        {p.celice.map((c, i) => (
          <rect key={`c${i}`} x={c.p.x + pol} y={c.p.y + pol} width={c.p.sirina - p.crta} height={c.p.visina - p.crta} />
        ))}
      </g>
      {p.besedila.map((b, i) => (
        <Besedilo key={`b${i}`} b={b} natis={natis} />
      ))}
    </svg>
  )
}

function Besedilo({ b, natis }: { b: BesediloZapisnika; natis: NatisZapisnikaDto }) {
  const oblika = natis.postavitev.oblika
  switch (b.vrsta) {
    case 'NASLOV': {
      const { datum, ura } = razbijCas(natis.termin)
      const deli = [natis.tekmovanje, natis.sezona, natis.opis, [datum, ura].filter(Boolean).join(' ob ')]
        .filter(Boolean)
        .join(' · ')
      return (
        <Vrstica p={b.p} velikost={3.6} teza={700} pisava="display" barva="#000">
          {deli.toUpperCase()}
        </Vrstica>
      )
    }
    case 'EKIPE':
      return (
        <Vrstica p={b.p} velikost={6.2} teza={800} pisava="display">
          {`${natis.domaci} : ${natis.gost}`}
        </Vrstica>
      )
    case 'KODA':
      return (
        <text x={b.p.x + b.p.sirina / 2} y={b.p.y + b.p.visina * 0.8} fontSize={2.6} textAnchor="middle"
          fontFamily="var(--pisava-mono)" fill="#000">
          {natis.oznaka}
        </text>
      )
    case 'EKIPA': {
      const ime = b.domaci ? natis.domaci : natis.gost
      const predpona = b.domaci ? 'Domači' : 'Gostje'
      return (
        <Vrstica p={b.p} velikost={3.2} teza={700} poravnava={oblika === 'VRSTICE' ? 'sredina' : 'levo'}>
          {`${predpona}: ${ime}`}
        </Vrstica>
      )
    }
    case 'IME': {
      const kader = b.domaci ? natis.kaderDomaci : natis.kaderGost
      const igralec = b.indeks != null ? kader[b.indeks] : undefined
      if (oblika === 'VRSTICE') {
        // navpično, od spodaj navzgor; prazna (zadnja) vrstica je za novega igralca
        const besedilo = igralec ? `${igralec.priimek} ${igralec.ime.charAt(0)}.` : 'nov igralec'
        const dolzina = Math.min(b.p.visina - 1, besedilo.length * PISAVA_IME * 0.52)
        return (
          <text
            transform={`translate(${b.p.x + b.p.sirina / 2 + PISAVA_IME * 0.35} ${b.p.y + b.p.visina - 0.5}) rotate(${b.kot})`}
            fontSize={PISAVA_IME}
            fontFamily="var(--pisava-telo)"
            fontStyle={igralec ? 'normal' : 'italic'}
            fill="#000"
            textLength={besedilo.length * PISAVA_IME * 0.52 > b.p.visina - 1 ? dolzina : undefined}
            lengthAdjust="spacingAndGlyphs"
          >
            {besedilo}
          </text>
        )
      }
      return (
        <Vrstica p={b.p} velikost={Math.min(3.4, b.p.visina * 0.55)}>
          {igralec ? `${igralec.ime} ${igralec.priimek}` : ''}
        </Vrstica>
      )
    }
    case 'NOV': {
      if (oblika === 'KADER') {
        // zadnja vrstica tabele kadra: črta za ime novega igralca
        return (
          <g>
            <Vrstica p={{ ...b.p, sirina: 20 }} velikost={2.8} slog="italic">nov igralec:</Vrstica>
            <line x1={b.p.x + 21} x2={b.p.x + b.p.sirina - 1} y1={b.p.y + b.p.visina * 0.78}
              y2={b.p.y + b.p.visina * 0.78} stroke="#000" strokeWidth={0.2} />
          </g>
        )
      }
      const opis = b.domaci ? 'Nov igralec domačih:' : 'Nov igralec gostov:'
      return (
        <g>
          <Vrstica p={{ ...b.p, sirina: 30 }} velikost={2.8}>{opis}</Vrstica>
          <line x1={b.p.x + 31} x2={b.p.x + b.p.sirina} y1={b.p.y + b.p.visina * 0.78}
            y2={b.p.y + b.p.visina * 0.78} stroke="#000" strokeWidth={0.2} />
        </g>
      )
    }
    case 'OZNAKA_TEKME': {
      const tekma = b.indeks != null ? natis.tekme[b.indeks] : undefined
      if (!tekma) return null
      // glava stolpca v tabeli kadra je ozka (8 mm): »Dv«
      const ozko = b.p.sirina < 10
      const besedilo = tekma.dvojice ? (ozko ? 'Dv' : 'dvojice') : tekma.oznaka
      return (
        <Vrstica p={b.p} velikost={ozko ? 2.6 : PISAVA_OZNAKA} teza={700} poravnava="sredina" pisava="mono">
          {besedilo}
        </Vrstica>
      )
    }
    case 'NIZ':
      return (
        <Vrstica p={b.p} velikost={2.6} poravnava="sredina" pisava="mono">
          {`${(b.indeks ?? 0) + 1}. niz`}
        </Vrstica>
      )
    case 'DVOPICJE':
      return (
        <text x={b.p.x + b.p.sirina / 2} y={b.p.y + b.p.visina / 2 + 1.1} fontSize={3.2} textAnchor="middle"
          fontFamily="var(--pisava-telo)" fontWeight={700} fill="#000">
          :
        </text>
      )
    case 'NAVODILO':
      return (
        <g>
          <Vrstica p={{ ...b.p, visina: b.p.visina / 2 }} velikost={2.7} teza={700}>
            {oblika === 'VRSTICE'
              ? 'Pri vsaki tekmi prekrižaj igralca, ki jo je igral (pri dvojicah oba), in vpiši točke nizov v isto vrsto.'
              : 'Pri vsaki tekmi (stolpec) prekrižaj igralca, ki jo je igral (pri dvojicah oba). Točke vpiši spodaj.'}
          </Vrstica>
          <Vrstica p={{ ...b.p, y: b.p.y + b.p.visina / 2, visina: b.p.visina / 2 }} velikost={2.5}>
            Napačen križec zapolni do črnega. Piši znotraj okvirčkov, ne čez črne kvadratke. Neodigrane tekme pusti prazne.
          </Vrstica>
        </g>
      )
    default:
      return null
  }
}

/* Ena vrstica besedila v pravokotniku (navpično na sredini). */
function Vrstica({
  p,
  velikost,
  children,
  teza = 400,
  poravnava = 'levo',
  pisava = 'telo',
  slog = 'normal',
  barva = '#000',
}: {
  p: PravokotnikMm
  velikost: number
  children: string
  teza?: number
  poravnava?: 'levo' | 'sredina'
  pisava?: 'telo' | 'display' | 'mono'
  slog?: 'normal' | 'italic'
  barva?: string
}) {
  const x = poravnava === 'sredina' ? p.x + p.sirina / 2 : p.x
  // približna širina znaka: dolgo besedilo se stisne v pravokotnik, ne čez rob
  const ocena = children.length * velikost * (pisava === 'mono' ? 0.6 : 0.52)
  return (
    <text
      x={x}
      y={p.y + p.visina / 2 + velikost * 0.35}
      fontSize={velikost}
      fontWeight={teza}
      fontStyle={slog}
      fontFamily={`var(--pisava-${pisava})`}
      textAnchor={poravnava === 'sredina' ? 'middle' : 'start'}
      fill={barva}
      textLength={ocena > p.sirina ? p.sirina : undefined}
      lengthAdjust="spacingAndGlyphs"
    >
      {children}
    </text>
  )
}
