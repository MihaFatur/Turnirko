import type { Poglavje } from '../tipi'

/* Vrstni red je vrstni red na strani Pomoč: najprej vodiči (opravilo od
   začetka do konca), nato priročnik v vrstnem redu, v katerem organizator
   funkcije sreča. */
export const POGLAVJA: Poglavje[] = [
  {
    id: 'vodici',
    naslov: 'Vodiči',
    opis: 'Opravilo od začetka do konca, korak za korakom.',
  },
  {
    id: 'zacetek',
    naslov: 'Osnove',
    opis: 'Kako je Turnirko zgrajen, kdo kaj ureja in kaj pomenijo statusi.',
  },
  {
    id: 'turnir',
    naslov: 'Turnir',
    opis: 'Turnir, kategorije, prijave, nosilci, žreb in sistemi tekmovanja.',
  },
  {
    id: 'rezultati',
    naslov: 'Rezultati',
    opis: 'Vnos, točke po nizih, popravki, odstop in razvrstitev.',
  },
  {
    id: 'liga',
    naslov: 'Liga',
    opis: 'Pravila, ekipe, razpored, termini, zapisniki, lestvice, prehodi in končnica.',
  },
  {
    id: 'tisk',
    naslov: 'Tisk',
    opis: 'Listki, žreb za vrata dvorane, zapisniki in izpis rezultatov.',
  },
  {
    id: 'tekmovanje',
    naslov: 'Tekmovanje in račun',
    opis: 'Logotip, pokrovitelji, Zanimivosti, kdo sme urejati in naročnina.',
  },
  {
    id: 'admin',
    naslov: 'Administracija',
    opis: 'Igralci, dostopi, šifranti, uvoz, kode popusta in rating. Vidi samo admin.',
    samoAdmin: true,
  },
]
