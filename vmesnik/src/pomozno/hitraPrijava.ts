/* Hitra prijava igralcev na dogodek: kdo sme na dogodek in kateri igralec je
   v vrstici prilepljenega seznama.

   Organizator dobi prijave po e-pošti, v sporočilu ali v Excelu. Prilepi jih
   v okno »Prilepi seznam imen«, vsako ime v svoji vrstici; tu se vsaka vrstica
   prevede v igralca iz šifranta. Vrstica sme nositi še kaj drugega (zaporedno
   številko, klub, letnik, stolpce iz Excela): ime je v njej, če so v njej
   VSE besede imena in priimka - ne obratno. */

import type { IgralecDto, SpolKategorija } from '../api/tipi'
import { zaIskanje } from './iskanje'

/* Zakaj igralca na dogodek ni mogoče prijaviti; null = mogoče je. */
export type OviraPrijave = 'prijavljen' | 'kategorija'

export const OPISI_OVIRE: Record<OviraPrijave, string> = {
  prijavljen: 'že prijavljen',
  kategorija: 'ne ustreza kategoriji',
}

/* Odjavljeni (ODJAVLJEN) se smejo prijaviti znova - ponovna prijava aktivira
   njihov obstoječi zapis, zato v `zePrijavljeni` ne sodijo. Kategoriji
   MESANO in KDORKOLI po spolu ne omejujeta. */
export function oviraPrijave(
  igralec: Pick<IgralecDto, 'id' | 'spol'>,
  zePrijavljeni: ReadonlySet<number>,
  spolKategorija: SpolKategorija,
): OviraPrijave | null {
  if (zePrijavljeni.has(igralec.id)) return 'prijavljen'
  if (spolKategorija === 'MOSKI' && igralec.spol !== 'MOSKI') return 'kategorija'
  if (spolKategorija === 'ZENSKE' && igralec.spol !== 'ZENSKI') return 'kategorija'
  return null
}

/* Besede imena za primerjavo: male črke brez šumnikov, ločila (tabulator
   iz Excela, vejica v »Novak, Ana«, pika v »1.«, vezaj v dvojnem priimku)
   so presledki. Ista razčlenitev za vrstico in za ime igralca - sicer bi se
   »Novak-Kos« in »Novak Kos« zgrešila. */
export function besedeImena(besedilo: string): string[] {
  return zaIskanje(besedilo).split(/[^\p{L}\p{N}']+/u).filter(Boolean)
}

/* Vrstice prilepljenega besedila. Ločilo med igralci je samo nova vrstica:
   vejica je v »Novak, Ana« del imena, podpičje pa je v slovenskem Excelu
   ločilo stolpcev. */
export function vrsticeSeznama(besedilo: string): string[] {
  return besedilo
    .split(/\r?\n/)
    .map((vrstica) => vrstica.trim())
    .filter((vrstica) => besedeImena(vrstica).length > 0)
}

/* Pri približnem iskanju (»Novak A.«) se izpiše največ toliko možnosti;
   več jih ne pomaga izbrati, pomaga dopisati ime. */
export const NAJVEC_KANDIDATOV = 8

export type IzidVrstice =
  /* En sam igralec. `priblizno`: ime v vrstici ni bilo celo (»Novak A.«). */
  | { vrsta: 'najden'; idIgralca: number; priblizno: boolean }
  /* Isti igralec je že v kateri od prejšnjih vrstic. */
  | { vrsta: 'ponovljen'; idIgralca: number }
  /* Več igralcev; izbere organizator. `skupaj` je pravo število (kandidatov
     je izpisanih največ NAJVEC_KANDIDATOV). */
  | { vrsta: 'vec'; kandidati: number[]; skupaj: number }
  | { vrsta: 'brez' }

export interface VrsticaIskanja {
  besedilo: string
  izid: IzidVrstice
}

interface ImeZaPrimerjavo {
  id: number
  besede: string[]
}

/* Vsako vrstico prevede v igralca. Dva koraka:

   1. Natančno: igralec, katerega VSE besede imena in priimka so v vrstici.
      Ob več takih zmaga najdaljše ime - »Ana Marija Novak« je Ana Marija in
      ne Ana Novak, čeprav sta v vrstici obe imeni.
   2. Približno (samo kadar 1. ne najde nikogar): vsaka beseda vrstice je
      začetek kake besede imena (»Novak A.« → Ana Novak). Številke odpadejo,
      ker so zaporedna številka ali letnik in ne ime.

   Kar ostane, je »brez«: igralca v šifrantu ni ali je vrstica predaleč od
   imena (klub ob okrajšanem imenu). Tega ne ugibamo. */
export function poisciVrstice(
  vrstice: string[],
  igralci: Pick<IgralecDto, 'id' | 'ime' | 'priimek'>[],
): VrsticaIskanja[] {
  const imena: ImeZaPrimerjavo[] = igralci.map((igralec) => ({
    id: igralec.id,
    besede: [...new Set(besedeImena(`${igralec.ime} ${igralec.priimek}`))],
  }))
  const videni = new Set<number>()

  return vrstice.map((besedilo) => {
    const izid = izidVrstice(besedilo, imena)
    if (izid.vrsta !== 'najden') return { besedilo, izid }
    if (videni.has(izid.idIgralca)) {
      return { besedilo, izid: { vrsta: 'ponovljen', idIgralca: izid.idIgralca } }
    }
    videni.add(izid.idIgralca)
    return { besedilo, izid }
  })
}

function izidVrstice(besedilo: string, imena: ImeZaPrimerjavo[]): IzidVrstice {
  const besede = besedeImena(besedilo)
  const vVrstici = new Set(besede)

  const natancni = imena.filter(
    (ime) => ime.besede.length > 0 && ime.besede.every((beseda) => vVrstici.has(beseda)),
  )
  if (natancni.length > 0) {
    const najdaljse = Math.max(...natancni.map((ime) => ime.besede.length))
    const najboljsi = natancni.filter((ime) => ime.besede.length === najdaljse)
    return razvrsti(najboljsi, false)
  }

  const iskane = besede.filter((beseda) => !/^\d+$/.test(beseda))
  if (iskane.length === 0) return { vrsta: 'brez' }
  const priblizni = imena.filter((ime) =>
    iskane.every((iskana) => ime.besede.some((beseda) => beseda.startsWith(iskana))),
  )
  return razvrsti(priblizni, true)
}

function razvrsti(kandidati: ImeZaPrimerjavo[], priblizno: boolean): IzidVrstice {
  if (kandidati.length === 0) return { vrsta: 'brez' }
  if (kandidati.length === 1) return { vrsta: 'najden', idIgralca: kandidati[0].id, priblizno }
  return {
    vrsta: 'vec',
    kandidati: kandidati.slice(0, NAJVEC_KANDIDATOV).map((ime) => ime.id),
    skupaj: kandidati.length,
  }
}

/* Igralci prijav drugega dogodka (»Prenesi z drugega dogodka«): oba igralca
   para, brez odjavljenih in brez ekipnih prijav (te igralca nimajo). Vrstni
   red je vrstni red prijav, vsak igralec enkrat. */
export function igralciPrijav(
  prijave: { idIgralca: number | null; idIgralca2: number | null; status: string }[],
): number[] {
  const idji = new Set<number>()
  for (const prijava of prijave) {
    if (prijava.status === 'ODJAVLJEN') continue
    if (prijava.idIgralca !== null) idji.add(prijava.idIgralca)
    if (prijava.idIgralca2 !== null) idji.add(prijava.idIgralca2)
  }
  return [...idji]
}
