/* Pravila strani »Branje listkov«: od predloga, ki ga vrne strežnik, do polj
   obrazca, ki ga organizator potrdi (VnosRezultataOkno), in nazaj v vnos.

   Predlog je že v straneh tekme (1 = udelezenec1), zato tu ni nič o tem, kateri
   stolpec listka je bil levi - to je strežnikova skrb (PredlogIzidaListka). */
import type { BranjeListkaDto, PredlogIzidaDto, VnosRezultata } from '../api/tipi'
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

/* Razdelitev vrste: najprej, kar čaka na organizatorja (po vrsti fotografiranja,
   kakor ima listke v roki), nato kar se še bere, nato zaključeno (najnovejše prvo). */
export function razdeliVrsto(branja: BranjeListkaDto[]) {
  const poCasu = (a: BranjeListkaDto, b: BranjeListkaDto) =>
    a.ustvarjenoOb.localeCompare(b.ustvarjenoOb) || a.id - b.id
  return {
    zaPotrditev: branja.filter((b) => b.stanje === 'PREBRANO' || b.stanje === 'NAPAKA').sort(poCasu),
    berem: branja.filter((b) => b.stanje === 'V_VRSTI').sort(poCasu),
    zakljucena: branja.filter((b) => b.stanje === 'POTRJENO' || b.stanje === 'ZAVRZENO'),
  }
}

/* Telefonska fotografija ima 12 Mpx in več; strežnik jo hrani do 2000 px, toliko
   je dovolj za števke in QR kodo (BranjeListkovStoritev.NAJVEC_STRANICA). Manjša
   gre hitreje po mobilnem omrežju v dvorani. createImageBitmap upošteva zasuk iz
   EXIF, zato je listek pokončen, kot ga je telefon posnel; JPEG iz platna pa
   nima metapodatkov (kraj, naprava). */
export const NAJVEC_STRANICA = 2000

export async function pomanjsajFotografijo(datoteka: Blob): Promise<Blob> {
  const slika = await createImageBitmap(datoteka)
  const k = Math.min(1, NAJVEC_STRANICA / Math.max(slika.width, slika.height))
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
