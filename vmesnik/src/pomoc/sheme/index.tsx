/* Register narisanih prikazov: ključ iz vsebine → komponenta. Tip Record
   zagotovi, da ima vsak ključ iz `kljuci.ts` svoj prikaz. */
import type { ComponentType } from 'react'

import type { KljucSheme } from './kljuci'
import { ShemaCone, ShemaEkipe, ShemaEnakomerna, ShemaFormatSntl, ShemaKoncnica, ShemaKrozniRazpored, ShemaKvalifikacije, ShemaPiramida, ShemaPostava, ShemaPrestavitev, ShemaRocniRazpored, ShemaTermini, ShemaUreKola } from './liga'
import { ShemaDostop, ShemaLogotip, ShemaNabiralnik, ShemaPaket, ShemaPotekLige, ShemaPotekTurnirja, ShemaRaven, ShemaStatusi, ShemaUvoz, ShemaZdruzitev, ShemaZgradba } from './osnove'
import { ShemaKrog, ShemaPopravek, ShemaTockePoNizih, ShemaVnosRezultata } from './rezultati'
import { ShemaIzpis, ShemaListkiA4, ShemaMrezaA4, ShemaSkupineA4, ShemaZapisnikOblike } from './tisk'
import { ShemaEkipnaTekma, ShemaIzSkupin, ShemaMrezaNosilcev, ShemaPar, ShemaPasovi, ShemaPrijava, ShemaPrilepiSeznam, ShemaRocneSkupine, ShemaSistemi, ShemaSvRegija, ShemaVrstniRed } from './turnir'

export type { KljucSheme } from './kljuci'

export const SHEME: Record<KljucSheme, ComponentType> = {
  zgradba: ShemaZgradba,
  statusi: ShemaStatusi,
  dostop: ShemaDostop,
  paket: ShemaPaket,
  'potek-turnirja': ShemaPotekTurnirja,
  'potek-lige': ShemaPotekLige,
  raven: ShemaRaven,
  sistemi: ShemaSistemi,
  prijava: ShemaPrijava,
  'prilepi-seznam': ShemaPrilepiSeznam,
  par: ShemaPar,
  'vrstni-red': ShemaVrstniRed,
  pasovi: ShemaPasovi,
  'mreza-nosilcev': ShemaMrezaNosilcev,
  'rocne-skupine': ShemaRocneSkupine,
  'iz-skupin': ShemaIzSkupin,
  'sv-regija': ShemaSvRegija,
  'ekipna-tekma': ShemaEkipnaTekma,
  'vnos-rezultata': ShemaVnosRezultata,
  'tocke-po-nizih': ShemaTockePoNizih,
  popravek: ShemaPopravek,
  krog: ShemaKrog,
  'format-sntl': ShemaFormatSntl,
  ekipe: ShemaEkipe,
  'krozni-razpored': ShemaKrozniRazpored,
  'rocni-razpored': ShemaRocniRazpored,
  enakomerna: ShemaEnakomerna,
  'ure-kola': ShemaUreKola,
  termini: ShemaTermini,
  prestavitev: ShemaPrestavitev,
  postava: ShemaPostava,
  cone: ShemaCone,
  piramida: ShemaPiramida,
  kvalifikacije: ShemaKvalifikacije,
  koncnica: ShemaKoncnica,
  'listki-a4': ShemaListkiA4,
  'skupine-a4': ShemaSkupineA4,
  'mreza-a4': ShemaMrezaA4,
  izpis: ShemaIzpis,
  'zapisnik-oblike': ShemaZapisnikOblike,
  logotip: ShemaLogotip,
  zdruzitev: ShemaZdruzitev,
  nabiralnik: ShemaNabiralnik,
  uvoz: ShemaUvoz,
}

/* Opis za bralnik zaslona nosi prikaz sam (aria-label na `Platno`). */
export function Shema({ kljuc }: { kljuc: KljucSheme }) {
  const Komponenta = SHEME[kljuc]
  return <Komponenta />
}
