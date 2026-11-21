/* Pravila strani »Branje listkov«: od predloga, ki ga vrne strežnik, do polj
   obrazca, ki ga organizator potrdi (VnosRezultataOkno), in nazaj v vnos.

   Predlog je že v straneh tekme (1 = udelezenec1), zato tu ni nič o tem, kateri
   stolpec listka je bil levi - to je strežnikova skrb (PredlogIzidaListka). */
import type { BranjeListkaDto, CeliceListkaDto, PredlogIzidaDto, VnosRezultata } from '../api/tipi'
import type { VrsticaNiza } from '../komponente/TockeNizov'

/* Vrstice za TockeNizov: neprebrana celica je prazno polje. */
export function vrsticeIzPredloga(predlog: PredlogIzidaDto): VrsticaNiza[] {
  return predlog.nizi.map((niz) => ({
    tocke1: niz.tocke1 === null ? '' : String(niz.tocke1),
    tocke2: niz.tocke2 === null ? '' : String(niz.tocke2),
  }))
}

/* Celice, ki jih mora organizator pogledati (mesto = niz × 2 + stran, kot v
   TockeNizov): negotove, neprebrane in celice neveljavnega niza. */
export function celiceZaPregled(predlog: PredlogIzidaDto): Set<number> {
  const mesta = new Set<number>()
  predlog.nizi.forEach((niz, i) => {
    if (niz.negotovo1 || niz.neveljaven || niz.tocke1 === null) mesta.add(i * 2)
    if (niz.negotovo2 || niz.neveljaven || niz.tocke2 === null) mesta.add(i * 2 + 1)
  })
  return mesta
}

/* Vnos rezultata natanko po predlogu (potrditev z enim dotikom). Samo za
   pripravljen predlog, ki ima vse celice prebrane. */
export function vnosIzPredloga(predlog: PredlogIzidaDto): VnosRezultata {
  const igrano = predlog.izidTip === 'IGRANO'
  return {
    izidTip: igrano ? null : predlog.izidTip,
    dobljeniNizi1: predlog.dobljeniNizi1,
    dobljeniNizi2: predlog.dobljeniNizi2,
    zmagovalecStran: predlog.zmagovalecStran,
    nizi:
      igrano && predlog.nizi.length > 0
        ? predlog.nizi.map((n) => ({ tocke1: n.tocke1 ?? 0, tocke2: n.tocke2 ?? 0 }))
        : null,
    steviloNizov: null,
  }
}

/* »11:7 9:11 11:5« za vrstico v vrsti; neprebrana celica je »?«. */
export function povzetekNizov(predlog: PredlogIzidaDto): string {
  return predlog.nizi.map((n) => `${n.tocke1 ?? '?'}:${n.tocke2 ?? '?'}`).join(' ')
}

/* Dobljeni nizi iz vpisanih točk, kadar so vse vrstice izpolnjene in veljavne;
   sicer null (organizator še popravlja). */
export function dobljeniIzTock(vrstice: VrsticaNiza[], veljaven: (a: number, b: number) => boolean) {
  if (vrstice.length === 0) return null
  let prvi = 0
  let drugi = 0
  for (const v of vrstice) {
    if (v.tocke1 === '' || v.tocke2 === '') return null
    const a = Number(v.tocke1)
    const b = Number(v.tocke2)
    if (!veljaven(a, b)) return null
    if (a > b) prvi++
    else drugi++
  }
  return { prvi, drugi }
}

/* Zanesljiv listek: prebran, s tekmo in s predlogom za en dotik (brez opozoril,
   negotovih celic in kontrole - kontrola nikoli ni pripravljena). */
export function jeZanesljiv(b: BranjeListkaDto): boolean {
  return b.stanje === 'PREBRANO' && b.tekma !== null && b.predlog?.pripravljen === true
}

/* Razdelitev vrste: najprej, kar čaka na organizatorja - zanesljivi zgoraj (F4.3,
   potrdijo se z enim dotikom), pod njimi negotovi, kontrole in napake; v vsaki
   skupini po vrsti fotografiranja, kakor ima listke v roki -, nato kar se še
   bere, nato zaključeno (najnovejše prvo). */
export function razdeliVrsto(branja: BranjeListkaDto[]) {
  const poCasu = (a: BranjeListkaDto, b: BranjeListkaDto) =>
    a.ustvarjenoOb.localeCompare(b.ustvarjenoOb) || a.id - b.id
  const poZanesljivosti = (a: BranjeListkaDto, b: BranjeListkaDto) =>
    Number(jeZanesljiv(b)) - Number(jeZanesljiv(a)) || poCasu(a, b)
  return {
    zaPotrditev: branja.filter((b) => b.stanje === 'PREBRANO' || b.stanje === 'NAPAKA').sort(poZanesljivosti),
    berem: branja.filter((b) => b.stanje === 'V_VRSTI').sort(poCasu),
    zakljucena: branja.filter((b) => b.stanje === 'POTRJENO' || b.stanje === 'ZAVRZENO'),
  }
}

/* Sled pregleda za meritev (V69): čas od trenutka, ko je organizator predlog
   lahko začel pregledovati, do potrditve. To je poznejši od dveh trenutkov:
   predlog se je prvič pokazal na strani ali pa je organizator potrdil ali zavrgel
   prejšnji listek. Pri listkih, ki jih potrjuje drugega za drugim, je to čas na
   listek; čakanje, ko je fotografiral naslednje, ni štet. Časi so iz
   performance.now() (ne skačejo ob premiku ure). Brez prikaza ni meritve. */
export function casPregleda(prikazan: number | undefined, zadnjaAkcija: number | null, zdaj: number): number | null {
  if (prikazan === undefined) return null
  const od = zadnjaAkcija === null ? prikazan : Math.max(prikazan, zadnjaAkcija)
  return Math.max(0, Math.round(zdaj - od))
}

/* Delež za stran meritev: »80 %« ali »–«, ko ni od česa. */
export function odstotek(del: number, celota: number): string {
  if (celota <= 0) return '–'
  const p = (100 * del) / celota
  return `${p >= 10 || p === 0 ? Math.round(p) : p.toFixed(1).replace('.', ',')} %`
}

/* Čas pregleda za stran meritev: »4,2 s« ali »–«. */
export function trajanje(ms: number | null): string {
  if (ms === null) return '–'
  if (ms >= 59_950) {
    const sekund = Math.round(ms / 1000)
    return `${Math.floor(sekund / 60)} min ${sekund % 60} s`
  }
  return `${(ms / 1000).toFixed(1).replace('.', ',')} s`
}

/* Telefonska fotografija ima 12 Mpx in več; strežnik jo hrani do 2000 px, toliko
   je dovolj za števke in QR kodo (BranjeListkovStoritev.NAJVEC_STRANICA). Manjša
   gre hitreje po mobilnem omrežju v dvorani. createImageBitmap upošteva zasuk iz
   EXIF, zato je listek pokončen, kot ga je telefon posnel; JPEG iz platna pa
   nima metapodatkov (kraj, naprava). */
export const NAJVEC_STRANICA = 2000

export async function pomanjsajFotografijo(datoteka: Blob, najvecStranica = NAJVEC_STRANICA): Promise<Blob> {
  const slika = await createImageBitmap(datoteka)
  const k = Math.min(1, najvecStranica / Math.max(slika.width, slika.height))
  const platno = document.createElement('canvas')
  platno.width = Math.round(slika.width * k)
  platno.height = Math.round(slika.height * k)
  const risba = platno.getContext('2d')
  if (!risba) throw new Error('Platno ni na voljo.')
  risba.drawImage(slika, 0, 0, platno.width, platno.height)
  slika.close()
  return new Promise((razresi, zavrni) =>
    platno.toBlob(
      (blob) => (blob ? razresi(blob) : zavrni(new Error('Fotografije ni bilo mogoče pripraviti.'))),
      'image/jpeg',
      0.88,
    ),
  )
}

/* Izrez celice ob polju (F4.1): pravokotnik celice na poravnanem listku za polje
   na mestu »mesto« (niz × 2 + stran tekme, kot v TockeNizov). Pri obrnjenem
   listku je stran 1 tekme desni stolpec listka. null, če celice ni (vrstica, ki
   je bralnik ni našel). */
export interface Pravokotnik {
  x0: number
  y0: number
  x1: number
  y1: number
}

export function izrezCelice(celice: CeliceListkaDto, mesto: number, obrnjeno: boolean): Pravokotnik | null {
  const vrstica = celice.nizi[Math.floor(mesto / 2)]
  if (!vrstica || vrstica.length !== 8) return null
  const stolpec = (mesto % 2 === 0) !== obrnjeno ? 0 : 1
  const [x0, y0, x1, y1] = vrstica.slice(stolpec * 4, stolpec * 4 + 4)
  return { x0, y0, x1, y1 }
}

/* CSS ozadja, ki iz ene slike (sirina × visina) pokaže samo pravokotnik z robom
   »rob« px (da se vidi črta celice): velikost in lega v odstotkih, zato se izrez
   raztegne na širino polja; razmerje stranic drži aspect-ratio. */
export function slogIzreza(p: Pravokotnik, sirina: number, visina: number, rob = 6) {
  const x0 = Math.max(0, p.x0 - rob)
  const y0 = Math.max(0, p.y0 - rob)
  const x1 = Math.min(sirina - 1, p.x1 + rob)
  const y1 = Math.min(visina - 1, p.y1 + rob)
  const w = x1 - x0 + 1
  const h = y1 - y0 + 1
  const lega = (z: number, del: number, celota: number) => (celota === del ? 0 : (z / (celota - del)) * 100)
  return {
    backgroundSize: `${(sirina / w) * 100}% ${(visina / h) * 100}%`,
    backgroundPosition: `${lega(x0, w, sirina)}% ${lega(y0, h, visina)}%`,
    aspectRatio: `${w} / ${h}`,
  }
}

/* Delež 0-1 kot »97,4 %« (zgodovina mreže števk, F6.4); null je »–«. */
export function delez(d: number | null): string {
  return d === null ? '–' : `${(d * 100).toFixed(1).replace('.', ',')} %`
}

/* Prej → potem; prekinjeno ali neizmerjeno »–«. */
export function prejPotem(prej: number | null, potem: number | null): string {
  if (prej === null || potem === null) return '–'
  return `${delez(prej)} → ${delez(potem)}`
}

export type StanjeRazlicice = 'v-uporabi' | 'sprejeta' | 'umaknjena' | 'zavrnjena' | 'prekinjena'

/* Kaj se je z različico mreže zgodilo, za značko v zgodovini. */
export function stanjeRazlicice(
  r: { id: number; sprejeta: boolean; prekinjena: boolean; umaknjenaOb: string | null },
  vUporabi: number | null,
): StanjeRazlicice {
  if (r.prekinjena) return 'prekinjena'
  if (!r.sprejeta) return 'zavrnjena'
  if (r.umaknjenaOb) return 'umaknjena'
  return r.id === vUporabi ? 'v-uporabi' : 'sprejeta'
}
