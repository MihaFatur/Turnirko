/* Slike (V54): javni naslov in priprava logotipa v brskalniku.

   Organizatorji imajo logotipe v vseh oblikah - JPG z belim ozadjem, PNG z
   velikim praznim robom, SVG, posnetek zaslona. Preden gre slika na strežnik,
   jo tu uredimo, da je na strani in na papirju videti kot logotip in ne kot
   prilepljen pravokotnik:
   - obrežemo prazen rob (prosojen ali enobarven), sicer bi bil logotip v glavi
     drobna pika sredi praznine;
   - belo ozadje, ki se dotika roba, lahko postane prosojno (papir strani ni
     bel, beli pravokotnik bi bil viden);
   - SVG narišemo kot PNG - strežnik SVG ne sprejme (lahko nosi skript);
   - pomanjšamo na največ 1200 px po daljši stranici, preden pošljemo (Wi-Fi v
     dvorani je počasen, fotografija s telefona ima 5 MB).
   Strežnik sliko vseeno prebere in zapiše znova (ObdelavaSlik) - tu gre za
   videz, ne za varnost. */
import type { SlikaDto } from '../api/tipi'

export type VelikostSlike = 'TISK' | 'ZASLON' | 'DELJENJE'

/* Ista zgostitev je vedno ista slika - naslov se lahko hrani za vedno. */
export function urlSlike(slika: Pick<SlikaDto, 'zgostitev'>, velikost: VelikostSlike): string {
  return `/api/v1/slike/${slika.zgostitev}?velikost=${velikost}`
}

/* Mere logotipa po ENAKI PLOŠČINI, ne enaki višini: pri isti višini je širok
   napis (»PETROL«) videti trikrat večji od kvadratnega grba. Meji držita, da
   pokončen logotip ne zraste čez naslov in širok ne čez stolpec; majhne slike
   ne povečamo čez njeno velikost (bila bi zamegljena). */
export function meraLogotipa(
  slika: Pick<SlikaDto, 'sirina' | 'visina'>,
  ploscina: number,
  najvecSirina: number,
  najvecVisina: number,
): { width: number; height: number } {
  const razmerje = slika.sirina / slika.visina
  const visina = Math.sqrt(ploscina / razmerje)
  const sirina = visina * razmerje
  const k = Math.min(1, najvecSirina / sirina, najvecVisina / visina, slika.sirina / sirina)
  return { width: Math.round(sirina * k), height: Math.round(visina * k) }
}

/* Daljša stranica, ki gre na strežnik (= različica TISK). */
export const NAJVEC_STRANICA = 1200

/* Največje mesto logotipa na papirju: Turnirkov zapisnik, 24 mm visoko in
   največ 64 mm široko (slog.css, .ekipni-zapisnik__logotip). */
const TISK_VISINA_MM = 24
const TISK_SIRINA_MM = 64
/* Pod tem je logotip na tisku opazno mehak (laserski tiskalnik ~300 dpi). */
export const NAJMANJ_DPI = 200

/* Ostrina logotipa na največjem mestu tiska, v pikah na palec. Širok napis
   se na zapisniku omeji s širino, pokončen grb z višino. */
export function dpiNaTisku(slika: Pick<SlikaDto, 'sirina' | 'visina'>): number {
  const razmerje = slika.sirina / slika.visina
  const sirinaMm = Math.min(TISK_SIRINA_MM, TISK_VISINA_MM * razmerje)
  return Math.round(slika.sirina / (sirinaMm / 25.4))
}
/* Večje slike pred obdelavo zmanjšamo - pomnilnik telefona. */
const NAJVEC_VHOD = 4000
/* SVG nima pik; narišemo ga za rezervo večjega in nato pomanjšamo. */
const SVG_STRANICA = 2400

/* Kako daleč od bele je pika: 0 = bela, 255 = najtemnejši kanal črn. */
function odBele(r: number, g: number, b: number): number {
  return 255 - Math.min(r, g, b)
}
/* Rob, ki ga obrežemo pri neprosojni sliki: skoraj bela (šum JPEG). */
const PRAG_ROBA = 24
/* Belo ozadje: kar je do tu od bele in povezano z robom, gre stran; med
   PRAG_PROSOJNO in PRAG_OZADJA pika postane delno prosojna (mehek rob). */
const PRAG_PROSOJNO = 10
const PRAG_OZADJA = 56

export interface AnalizaSlike {
  /* Rob slike je prosojen - ozadje je že urejeno. */
  prosojna: boolean
  /* Rob je (skoraj) bel in neprosojen - ponudimo odstranitev ozadja. */
  belo: boolean
}

export interface PripravljenaSlika {
  png: Blob
  /* Naslov objekta za predogled; sprosti ga klicatelj (URL.revokeObjectURL). */
  url: string
  sirina: number
  visina: number
}

/* Odpre datoteko in jo nariše na platno v naravni velikosti (do NAJVEC_VHOD). */
export async function naloziVPlatno(datoteka: File): Promise<HTMLCanvasElement> {
  const jeSvg = datoteka.type === 'image/svg+xml' || /\.svg$/i.test(datoteka.name)
  try {
    if (jeSvg) return await svgVPlatno(await datoteka.text())
    /* createImageBitmap upošteva usmerjenost iz EXIF (fotografija s telefona). */
    const bitna = await createImageBitmap(datoteka)
    const k = Math.min(1, NAJVEC_VHOD / Math.max(bitna.width, bitna.height))
    const platno = novoPlatno(Math.round(bitna.width * k), Math.round(bitna.height * k))
    risi(platno).drawImage(bitna, 0, 0, platno.width, platno.height)
    bitna.close()
    return platno
  } catch {
    throw new Error(
      jeSvg
        ? 'SVG ni bilo mogoče narisati. Shrani logotip kot PNG in poskusi znova.'
        : 'Datoteke ni bilo mogoče odpreti kot slike. Izberi PNG, JPG ali SVG.',
    )
  }
}

export function analiziraj(platno: HTMLCanvasElement): AnalizaSlike {
  const { width: s, height: v } = platno
  const pike = risi(platno).getImageData(0, 0, s, v).data
  let prosojnih = 0
  let belih = 0
  let vseh = 0
  zaRob(s, v, (i) => {
    vseh++
    const a = pike[i + 3]
    if (a < 16) prosojnih++
    else if (a > 240 && odBele(pike[i], pike[i + 1], pike[i + 2]) <= PRAG_ROBA) belih++
  })
  return {
    prosojna: prosojnih / vseh > 0.5,
    belo: belih / vseh > 0.9,
  }
}

/* Obreže prazen rob, pomanjša na NAJVEC_STRANICA in po izbiri odstrani belo
   ozadje. Vrne PNG za strežnik in predogled. */
export async function pripravi(
  izvor: HTMLCanvasElement,
  analiza: AnalizaSlike,
  odstraniOzadje: boolean,
): Promise<PripravljenaSlika> {
  const okvir = vsebina(izvor, analiza)
  if (!okvir) throw new Error('Slika je prazna — na njej ni ničesar razen ozadja.')
  const obrezana = novoPlatno(okvir.s, okvir.v)
  risi(obrezana).drawImage(izvor, okvir.x, okvir.y, okvir.s, okvir.v, 0, 0, okvir.s, okvir.v)

  const koncna = pomanjsaj(obrezana, NAJVEC_STRANICA)
  if (odstraniOzadje && analiza.belo) odstraniBeloOzadje(koncna)

  const png = await new Promise<Blob>((koncano, napaka) =>
    koncna.toBlob((b) => (b ? koncano(b) : napaka(new Error('Slike ni bilo mogoče zapisati.'))), 'image/png'),
  )
  return { png, url: URL.createObjectURL(png), sirina: koncna.width, visina: koncna.height }
}

// ---------- Notranje ----------

function novoPlatno(sirina: number, visina: number): HTMLCanvasElement {
  const platno = document.createElement('canvas')
  platno.width = Math.max(1, sirina)
  platno.height = Math.max(1, visina)
  return platno
}

function risi(platno: HTMLCanvasElement): CanvasRenderingContext2D {
  const ctx = platno.getContext('2d', { willReadFrequently: true })
  if (!ctx) throw new Error('Brskalnik ne zna risati na platno.')
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  return ctx
}

/* SVG brez mer (samo viewBox) se v <img> nariše kot 300 x 150 ali sploh ne,
   zato mu mere postavimo sami - daljša stranica SVG_STRANICA. */
async function svgVPlatno(besedilo: string): Promise<HTMLCanvasElement> {
  const dokument = new DOMParser().parseFromString(besedilo, 'image/svg+xml')
  const svg = dokument.documentElement
  if (svg.nodeName.toLowerCase() !== 'svg') throw new Error('ni SVG')
  const okvir = (svg.getAttribute('viewBox') ?? '').split(/[\s,]+/).map(Number)
  let sirina = parseFloat(svg.getAttribute('width') ?? '')
  let visina = parseFloat(svg.getAttribute('height') ?? '')
  if (okvir.length === 4 && okvir.every(Number.isFinite) && okvir[2] > 0 && okvir[3] > 0) {
    sirina = okvir[2]
    visina = okvir[3]
  }
  if (!(sirina > 0 && visina > 0)) {
    sirina = 1
    visina = 1
  }
  const k = SVG_STRANICA / Math.max(sirina, visina)
  const s = Math.round(sirina * k)
  const v = Math.round(visina * k)
  svg.setAttribute('width', String(s))
  svg.setAttribute('height', String(v))
  if (!svg.getAttribute('viewBox')) svg.setAttribute('viewBox', `0 0 ${sirina} ${visina}`)

  const naslov = URL.createObjectURL(
    new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' }),
  )
  try {
    const slika = new Image()
    slika.decoding = 'async'
    slika.src = naslov
    await slika.decode()
    const platno = novoPlatno(s, v)
    risi(platno).drawImage(slika, 0, 0, s, v)
    return platno
  } finally {
    URL.revokeObjectURL(naslov)
  }
}

/* Indeksi pik (×4) na robu slike. */
function zaRob(s: number, v: number, naPiko: (i: number) => void) {
  for (let x = 0; x < s; x++) {
    naPiko(x * 4)
    if (v > 1) naPiko(((v - 1) * s + x) * 4)
  }
  for (let y = 1; y < v - 1; y++) {
    naPiko(y * s * 4)
    if (s > 1) naPiko((y * s + s - 1) * 4)
  }
}

/* Najmanjši pravokotnik z vsebino: pri prosojni sliki vse, kar ni prosojno,
   pri beli vse, kar ni skoraj belo. Drugačno ozadje (barvna ploskev) ostane. */
function vsebina(platno: HTMLCanvasElement, analiza: AnalizaSlike) {
  const { width: s, height: v } = platno
  const pike = risi(platno).getImageData(0, 0, s, v).data
  const jeVsebina = (i: number) =>
    pike[i + 3] > 8 && (!analiza.belo || odBele(pike[i], pike[i + 1], pike[i + 2]) > PRAG_ROBA)
  if (!analiza.prosojna && !analiza.belo) return { x: 0, y: 0, s, v }
  let levo = s
  let desno = -1
  let zgoraj = v
  let spodaj = -1
  for (let y = 0; y < v; y++) {
    for (let x = 0; x < s; x++) {
      if (jeVsebina((y * s + x) * 4)) {
        if (x < levo) levo = x
        if (x > desno) desno = x
        if (y < zgoraj) zgoraj = y
        if (y > spodaj) spodaj = y
      }
    }
  }
  if (desno < 0) return null
  return { x: levo, y: zgoraj, s: desno - levo + 1, v: spodaj - zgoraj + 1 }
}

/* Polovičenje v korakih: en skok s 3000 na 600 px razdrobi tanke črte in
   drobno besedilo logotipa. */
function pomanjsaj(vir: HTMLCanvasElement, najvec: number): HTMLCanvasElement {
  const k = najvec / Math.max(vir.width, vir.height)
  if (k >= 1) return vir
  const ciljS = Math.max(1, Math.round(vir.width * k))
  const ciljV = Math.max(1, Math.round(vir.height * k))
  let trenutno = vir
  while (trenutno.width !== ciljS || trenutno.height !== ciljV) {
    const s = Math.max(ciljS, Math.floor(trenutno.width / 2))
    const v = Math.max(ciljV, Math.floor(trenutno.height / 2))
    const naslednje = novoPlatno(s, v)
    risi(naslednje).drawImage(trenutno, 0, 0, s, v)
    trenutno = naslednje
  }
  return trenutno
}

/* Belo, ki se dotika roba, postane prosojno; bela znotraj logotipa (črke v
   grbu) ostane, ker do nje ni poti od roba. Mehke robove (glajenje) pretvori v
   delno prosojnost in jim vrne barvo brez bele primesi - sicer bi okoli
   logotipa ostal bel obroč. */
function odstraniBeloOzadje(platno: HTMLCanvasElement) {
  const { width: s, height: v } = platno
  const ctx = risi(platno)
  const slika = ctx.getImageData(0, 0, s, v)
  const pike = slika.data
  const obiskana = new Uint8Array(s * v)
  const vrsta = new Int32Array(s * v)
  let glava = 0
  let rep = 0
  const dodaj = (p: number) => {
    if (obiskana[p]) return
    const i = p * 4
    if (odBele(pike[i], pike[i + 1], pike[i + 2]) > PRAG_OZADJA) return
    obiskana[p] = 1
    vrsta[rep++] = p
  }
  zaRob(s, v, (i) => dodaj(i / 4))
  while (glava < rep) {
    const p = vrsta[glava++]
    const x = p % s
    if (x > 0) dodaj(p - 1)
    if (x < s - 1) dodaj(p + 1)
    if (p >= s) dodaj(p - s)
    if (p < s * (v - 1)) dodaj(p + s)

    const i = p * 4
    const d = odBele(pike[i], pike[i + 1], pike[i + 2])
    const a = Math.min(1, Math.max(0, (d - PRAG_PROSOJNO) / (PRAG_OZADJA - PRAG_PROSOJNO)))
    if (a === 0) {
      pike[i + 3] = 0
      continue
    }
    for (let k = 0; k < 3; k++) {
      pike[i + k] = Math.round(Math.min(255, Math.max(0, (pike[i + k] - 255 * (1 - a)) / a)))
    }
    pike[i + 3] = Math.round(pike[i + 3] * a)
  }
  ctx.putImageData(slika, 0, 0)
}
