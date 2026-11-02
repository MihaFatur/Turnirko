/* Sezonski zapisnik igralca (paket Navijač, faza 3): ena sezona igralca na enem
   listu, narejena iz javnega profila (ProfilDto). Zaledje ne rabi ničesar
   svojega: vse, kar zapisnik pove, je že javno na profilu, zato je tudi
   izračun v vmesniku in ga ni mogoče razlikovati od tistega, kar gledalec
   vidi na profilu.

   Sezona je 1. julij – 30. junij (isti rez kot sezonaIzDatuma). Tekma brez
   znanega datuma (uvožene lige 2024/25 brez terminov) ne sodi v nobeno
   sezono; zapisnik jih prešteje in pove, namesto da bi jih tiho izpustil. */
import type { ProfilDto, TekmaProfila, TockaGrafa } from '../api/tipi'
import { sezonaIzDatuma } from './oblikovanje'

export interface TekmovanjeSezone {
  kljuc: string
  ligaska: boolean
  ime: string
  del: string
  /* Prva odigrana tekma v sezoni; po njem so tekmovanja v zapisniku urejena. */
  datum: string | null
  zmage: number
  porazi: number
  /* Vsota sprememb ratinga tekem v sezoni; null, kadar nobena tekma nima spremembe. */
  sprememba: number | null
  /* Končno mesto v kategoriji turnirja (pri ligi ga ni). */
  mesto: number | null
  mestoDo: number | null
  udelezencev: number | null
}

export interface SezonskiZapisnik {
  sezona: string
  tekem: number
  zmag: number
  porazov: number
  odstotekZmag: number | null
  dobljeniNizi: number
  prejetiNizi: number
  ratingZacetek: number | null
  ratingKonec: number | null
  ratingNajvisji: number | null
  ratingNajnizji: number | null
  /* Vrednosti ratinga po zaporedju sprememb v sezoni (os grafa je zaporedje, ne koledar). */
  potRatinga: number[]
  tekmovanja: TekmovanjeSezone[]
  /* Tekme sezone po datumu navzgor (v profilu so najnovejše prve). */
  tekme: TekmaProfila[]
}

/* Tekma, ki je bila res odigrana (kot IzidTekme.jeOdigrana v zaledju). */
function jeOdigrana(tekma: TekmaProfila): boolean {
  return tekma.izidTip === null || tekma.izidTip === 'IGRANO' || tekma.izidTip === 'PREDAJA'
}

/* Sezone, v katerih ima igralec odigrane tekme, najnovejša prva. */
export function sezoneIgralca(profil: ProfilDto): string[] {
  const sezone = new Set<string>()
  for (const t of profil.tekme) {
    const sezona = sezonaIzDatuma(t.datum)
    if (sezona && jeOdigrana(t)) sezone.add(sezona)
  }
  return [...sezone].sort().reverse()
}

/* Koliko odigranih tekem nima znanega datuma in zato ne sodi v nobeno sezono. */
export function tekemBrezDatuma(profil: ProfilDto): number {
  return profil.tekme.filter((t) => jeOdigrana(t) && !sezonaIzDatuma(t.datum)).length
}

function poDatumu(a: string | null, b: string | null): number {
  return (a ?? '').localeCompare(b ?? '')
}

/* Točke grafa, ki veljajo v sezoni, po vrstnem redu obračuna. Postavitev in
   odbitek za neaktivnost nosita dan, ko sta začela veljati, zato sodita v
   sezono kot tekme. */
function tockeSezone(graf: TockaGrafa[], sezona: string): TockaGrafa[] {
  return graf.filter((t) => sezonaIzDatuma(t.datum) === sezona)
}

export function sestaviSezonskiZapisnik(profil: ProfilDto, sezona: string): SezonskiZapisnik {
  const tekme = profil.tekme
    .filter((t) => jeOdigrana(t) && sezonaIzDatuma(t.datum) === sezona)
    .sort((a, b) => poDatumu(a.datum, b.datum) || a.idTekme - b.idTekme)

  const zmag = tekme.filter((t) => t.zmaga).length
  const porazov = tekme.length - zmag

  const tocke = tockeSezone(profil.graf, sezona)
  const prva = tocke[0]
  const zadnja = tocke[tocke.length - 1]
  /* Rating pred prvo spremembo v sezoni je tisti, s katerim je igralec sezono začel. */
  const zacetek = prva ? prva.vrednost - prva.sprememba : null
  const vrednosti = prva ? [prva.vrednost - prva.sprememba, ...tocke.map((t) => t.vrednost)] : []

  /* Tekmovanja: vsota po tekmah sezone, podatki o mestu pa iz profila. */
  const skupine = new Map<string, TekmaProfila[]>()
  for (const t of tekme) skupine.set(t.tekmovanjeKljuc, [...(skupine.get(t.tekmovanjeKljuc) ?? []), t])
  const tekmovanja: TekmovanjeSezone[] = [...skupine.entries()].map(([kljuc, tekmeTekmovanja]) => {
    const meta = profil.tekmovanja.find((t) => t.kljuc === kljuc)
    const spremembe = tekmeTekmovanja.map((t) => t.spremembaRatinga).filter((s): s is number => s !== null)
    const prvaTekma = tekmeTekmovanja[0]
    return {
      kljuc,
      ligaska: prvaTekma.ligaska,
      ime: meta?.ime ?? prvaTekma.tekmovanje,
      del: meta?.del ?? prvaTekma.del,
      datum: prvaTekma.datum,
      zmage: tekmeTekmovanja.filter((t) => t.zmaga).length,
      porazi: tekmeTekmovanja.filter((t) => !t.zmaga).length,
      sprememba: spremembe.length > 0 ? spremembe.reduce((a, b) => a + b, 0) : null,
      mesto: meta?.mesto ?? null,
      mestoDo: meta?.mestoDo ?? null,
      udelezencev: meta?.udelezencev ?? null,
    }
  })
  tekmovanja.sort((a, b) => poDatumu(a.datum, b.datum) || a.ime.localeCompare(b.ime, 'sl'))

  return {
    sezona,
    tekem: tekme.length,
    zmag,
    porazov,
    odstotekZmag: tekme.length > 0 ? Math.round((zmag / tekme.length) * 100) : null,
    dobljeniNizi: tekme.reduce((vsota, t) => vsota + t.niziZa, 0),
    prejetiNizi: tekme.reduce((vsota, t) => vsota + t.niziProti, 0),
    ratingZacetek: zacetek,
    ratingKonec: zadnja ? zadnja.vrednost : null,
    ratingNajvisji: vrednosti.length > 0 ? Math.max(...vrednosti) : null,
    ratingNajnizji: vrednosti.length > 0 ? Math.min(...vrednosti) : null,
    potRatinga: vrednosti,
    tekmovanja,
    tekme,
  }
}

/* »+12« / »−7« / »±0«; prazno, kadar spremembe ni. Minus je pravi znak, ne vezaj. */
export function oblikujSpremembo(sprememba: number | null): string {
  if (sprememba === null) return ''
  if (sprememba === 0) return '±0'
  return `${sprememba > 0 ? '+' : '−'}${Math.abs(sprememba)}`
}

/* »3.«, »3.–4.« ali »5. od 32« za končno mesto v kategoriji. */
export function oblikujMesto(t: Pick<TekmovanjeSezone, 'mesto' | 'mestoDo' | 'udelezencev'>): string {
  if (t.mesto === null) return ''
  const razpon = t.mestoDo !== null && t.mestoDo > t.mesto ? `${t.mesto}.–${t.mestoDo}.` : `${t.mesto}.`
  return t.udelezencev !== null ? `${razpon} od ${t.udelezencev}` : razpon
}
