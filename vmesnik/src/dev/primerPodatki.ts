/* Vzorčni podatki za prikazni harness (/dev/posnetki). Prepisani iz predaje
   design_handoff_termini_prehodi/primerjava/primer-podatkov.json — posnetki
   v predaji so narejeni prav iz teh številk, zato jih ne spreminjaj.

   Srečanje: [id, domači, gost, izvirno kolo, kolo, ura kroga, ura, odigrano,
   razlog prestavitve, status]. Kolo: [številka, datum, odigrano]. */

export type VrsticaSrecanja = [
  number, string, string, number, number, number, string, boolean, string, 'KONCANO' | 'RAZPORED',
]

export interface VzorecKola {
  ure: string[] | null
  kola: [number, string, boolean][]
  srecanja: VrsticaSrecanja[]
}

/* Ena ura na kolo: navadna liga, odigrana kola so skrita. */
export const ENA_URA: VzorecKola = {
  ure: null,
  kola: [
    [1, '2026-10-03', true], [2, '2026-10-10', true], [3, '2026-10-17', true],
    [4, '2026-10-24', false], [5, '2026-11-07', false], [6, '2026-11-14', false],
    [7, '2026-11-20', false], [8, '2026-11-28', false], [9, '2026-12-05', false],
    [10, '2026-12-12', false],
  ],
  srecanja: [
    [1, 'Velenje', 'Šoštanj', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [2, 'Celje', 'Mozirje', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [3, 'Ptuj', 'Žalec', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [4, 'Mozirje', 'Velenje', 2, 2, 0, '18:00', true, '', 'KONCANO'],
    [5, 'Žalec', 'Šoštanj', 2, 2, 0, '18:00', true, '', 'KONCANO'],
    [6, 'Ptuj', 'Celje', 2, 2, 0, '18:00', true, '', 'KONCANO'],
    [7, 'Velenje', 'Žalec', 3, 3, 0, '18:00', true, '', 'KONCANO'],
    [8, 'Mozirje', 'Ptuj', 3, 3, 0, '18:00', true, '', 'KONCANO'],
    [9, 'Šoštanj', 'Celje', 3, 3, 0, '18:00', true, '', 'KONCANO'],
    [10, 'Ptuj', 'Velenje', 4, 6, 0, '19:00', false, 'Dvorana zasedena (turnir mladincev)', 'RAZPORED'],
    [11, 'Celje', 'Žalec', 4, 4, 0, '18:00', false, '', 'RAZPORED'],
    [12, 'Šoštanj', 'Mozirje', 4, 4, 0, '18:00', false, '', 'RAZPORED'],
    [13, 'Velenje', 'Celje', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [14, 'Ptuj', 'Šoštanj', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [15, 'Žalec', 'Mozirje', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [16, 'Šoštanj', 'Velenje', 6, 6, 0, '18:00', false, '', 'RAZPORED'],
    [17, 'Mozirje', 'Celje', 6, 6, 0, '18:00', false, '', 'RAZPORED'],
    [18, 'Žalec', 'Ptuj', 6, 6, 0, '18:00', false, '', 'RAZPORED'],
    [19, 'Velenje', 'Mozirje', 7, 7, 0, '18:00', false, '', 'RAZPORED'],
    [20, 'Šoštanj', 'Žalec', 7, 7, 0, '18:00', false, '', 'RAZPORED'],
    [21, 'Celje', 'Ptuj', 7, 7, 0, '18:00', false, '', 'RAZPORED'],
    [22, 'Žalec', 'Velenje', 8, 8, 0, '18:00', false, '', 'RAZPORED'],
    [23, 'Ptuj', 'Mozirje', 8, 8, 0, '18:00', false, '', 'RAZPORED'],
    [24, 'Celje', 'Šoštanj', 8, 8, 0, '18:00', false, '', 'RAZPORED'],
    [25, 'Velenje', 'Ptuj', 9, 9, 0, '18:00', false, '', 'RAZPORED'],
    [26, 'Žalec', 'Celje', 9, 9, 0, '18:00', false, '', 'RAZPORED'],
    [27, 'Mozirje', 'Šoštanj', 9, 9, 0, '18:00', false, '', 'RAZPORED'],
    [28, 'Celje', 'Velenje', 10, 10, 0, '18:00', false, '', 'RAZPORED'],
    [29, 'Šoštanj', 'Ptuj', 10, 10, 0, '18:00', false, '', 'RAZPORED'],
    [30, 'Mozirje', 'Žalec', 10, 10, 0, '18:00', false, '', 'RAZPORED'],
  ],
}

/* Več ur na kolo (večer 18:00 in 19:30): razmik 14 dni, prvo kolo odigrano. */
export const VEC_UR: VzorecKola = {
  ure: ['18:00', '19:30'],
  kola: [
    [1, '2026-10-10', true], [2, '2026-10-24', false], [3, '2026-11-07', false],
    [4, '2026-11-21', false], [5, '2026-12-05', false],
  ],
  srecanja: [
    [1, 'Velenje', 'Šoštanj', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [2, 'Celje', 'Mozirje', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [3, 'Ptuj', 'Žalec', 1, 1, 0, '18:00', true, '', 'KONCANO'],
    [4, 'Mozirje', 'Velenje', 2, 4, 0, '19:00', false, 'Dvorana zasedena (turnir mladincev)', 'RAZPORED'],
    [5, 'Žalec', 'Šoštanj', 2, 2, 0, '18:00', false, '', 'RAZPORED'],
    [6, 'Ptuj', 'Celje', 2, 2, 0, '18:00', false, '', 'RAZPORED'],
    [7, 'Velenje', 'Žalec', 3, 3, 0, '18:00', false, '', 'RAZPORED'],
    [8, 'Mozirje', 'Ptuj', 3, 3, 0, '18:00', false, '', 'RAZPORED'],
    [9, 'Šoštanj', 'Celje', 3, 3, 0, '18:00', false, '', 'RAZPORED'],
    [10, 'Ptuj', 'Velenje', 4, 4, 0, '18:00', false, '', 'RAZPORED'],
    [11, 'Celje', 'Žalec', 4, 4, 0, '18:00', false, '', 'RAZPORED'],
    [12, 'Šoštanj', 'Mozirje', 4, 4, 0, '18:00', false, '', 'RAZPORED'],
    [13, 'Velenje', 'Celje', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [14, 'Ptuj', 'Šoštanj', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [15, 'Žalec', 'Mozirje', 5, 5, 0, '18:00', false, '', 'RAZPORED'],
    [16, 'Šoštanj', 'Velenje', 1, 1, 1, '19:30', true, '', 'KONCANO'],
    [17, 'Mozirje', 'Celje', 1, 1, 1, '19:30', true, '', 'KONCANO'],
    [18, 'Žalec', 'Ptuj', 1, 1, 1, '19:30', true, '', 'KONCANO'],
    [19, 'Velenje', 'Mozirje', 2, 2, 1, '19:30', false, '', 'RAZPORED'],
    [20, 'Šoštanj', 'Žalec', 2, 2, 1, '19:30', false, '', 'RAZPORED'],
    [21, 'Celje', 'Ptuj', 2, 2, 1, '19:30', false, '', 'RAZPORED'],
    [22, 'Žalec', 'Velenje', 3, 3, 1, '19:30', false, '', 'RAZPORED'],
    [23, 'Ptuj', 'Mozirje', 3, 3, 1, '19:30', false, '', 'RAZPORED'],
    [24, 'Celje', 'Šoštanj', 3, 3, 1, '19:30', false, '', 'RAZPORED'],
    [25, 'Velenje', 'Ptuj', 4, 4, 1, '19:30', false, '', 'RAZPORED'],
    [26, 'Žalec', 'Celje', 4, 4, 1, '19:30', false, '', 'RAZPORED'],
    [27, 'Mozirje', 'Šoštanj', 4, 4, 1, '19:30', false, '', 'RAZPORED'],
    [28, 'Celje', 'Velenje', 5, 5, 1, '19:30', false, '', 'RAZPORED'],
    [29, 'Šoštanj', 'Ptuj', 5, 5, 1, '19:30', false, '', 'RAZPORED'],
    [30, 'Mozirje', 'Žalec', 5, 5, 1, '19:30', false, '', 'RAZPORED'],
  ],
}

/* Lige, ki jih vidi okno piramide. Liga B (id 100) je tista, ki se ureja, in
   jo harness doda sam. */
export const LIGE = [
  { id: 1, ime: 'Savinja liga A', sezona: '2026/27', idVisjaLiga: null, visjaLigaIme: null, stKvalifikacijeGor: 0, stKvalifikacijeDol: 1 },
  { id: 2, ime: 'Savinja liga C', sezona: '2026/27', idVisjaLiga: 100, visjaLigaIme: 'Savinja liga B', stKvalifikacijeGor: 1, stKvalifikacijeDol: 0 },
  { id: 3, ime: 'Savinja liga D', sezona: '2026/27', idVisjaLiga: null, visjaLigaIme: null, stKvalifikacijeGor: 0, stKvalifikacijeDol: 0 },
  { id: 4, ime: 'Zasavska liga', sezona: '2026/27', idVisjaLiga: 1, visjaLigaIme: 'Savinja liga A', stKvalifikacijeGor: 0, stKvalifikacijeDol: 0 },
  { id: 5, ime: 'Koroška liga', sezona: '8. Sezona', idVisjaLiga: null, visjaLigaIme: null, stKvalifikacijeGor: 2, stKvalifikacijeDol: 0 },
]

/* Igralci za okno »Moji igralci« (izkaznica): iste številke kot
   referenca/moji-igralci-podatki.js v predaji design_handoff_moji_igralci.
   Zgostitev »dev-slika« harness nadomesti z diagonalno črtkanim poljem, kot
   ga ima maketa namesto prave fotografije. */
import type { DomovIgralecDto, TekmaProfila } from '../api/tipi'

function tekma(
  idTekme: number, datum: string, nasprotnik: string, tekmovanje: string,
  niziZa: number, niziProti: number, sprememba: number,
): TekmaProfila {
  return {
    idTekme, ligaska: true, datum, tekmovanje, del: '', idNasprotnika: 900 + idTekme, nasprotnik,
    klubNasprotnika: null, niziZa, niziProti, zmaga: niziZa > niziProti, izidTip: 'IGRANO',
    spremembaRatinga: sprememba, tekmovanjeKljuc: `l${idTekme}`,
  }
}

const TURNIR_CELJE = {
  idTurnir: 77, ime: 'Odprto prvenstvo Celja', datum: '2026-11-22', status: 'PRIPRAVA' as const,
}

export const MOJI_IGRALCI: DomovIgralecDto[] = [
  {
    idIgralec: 1, polnoIme: 'Luka Zupančič', ime: 'Luka', priimek: 'Zupančič', klub: 'KTT Maribor',
    rating: 1482,
    uvrstitev: { mesto: 14, skupajIgralcev: 212, percentil: null, klubskoPovprecje: null, spol: 'MOSKI', rekreativec: false },
    zadnje: [
      tekma(1, '2026-10-25', 'Žiga Horvat', 'Liga Savinja D', 3, 1, 6),
      tekma(2, '2026-10-18', 'Rok Tomšič', 'Liga Savinja D', 2, 3, -8),
      tekma(3, '2026-10-11', 'Matej Kos', 'Jesenski turnir Maribor', 3, 0, 9),
    ],
    naslednjeSrecanje: {
      idSrecanje: 501, idLiga: 5, liga: 'Liga Savinja D', kolo: 6, zacetek: '2026-11-08T18:00:00',
      ekipa: 'KTT Maribor B', nasprotnik: 'TTK Ptuj', doma: true,
    },
    prihajajociTurnir: TURNIR_CELJE,
    druzina: false,
    forma: ['Z', 'P', 'Z', 'Z', 'Z'],
    sezona: { zmage: 18, porazi: 7 },
    zgostitev: 'dev-slika',
  },
  {
    idIgralec: 2, polnoIme: 'Maja Zupan', ime: 'Maja', priimek: 'Zupan', klub: 'TTK Ptuj',
    rating: 1215,
    uvrstitev: { mesto: 31, skupajIgralcev: 88, percentil: null, klubskoPovprecje: null, spol: 'ZENSKI', rekreativec: true },
    zadnje: [
      tekma(4, '2026-10-24', 'Eva Krajnc', 'Jesenski turnir Maribor', 3, 2, 11),
      tekma(5, '2026-10-10', 'Nina Vidmar', 'Odprto prvenstvo Ptuja', 0, 3, -7),
      tekma(6, '2026-09-27', 'Sara Božič', 'Odprto prvenstvo Ptuja', 3, 1, 5),
    ],
    naslednjeSrecanje: null,
    prihajajociTurnir: TURNIR_CELJE,
    druzina: true,
    forma: ['Z', 'P', 'Z', 'Z', 'P'],
    sezona: { zmage: 11, porazi: 9 },
    zgostitev: null,
  },
  {
    idIgralec: 3, polnoIme: 'Jan Rozman', ime: 'Jan', priimek: 'Rozman', klub: null, rating: null,
    uvrstitev: { mesto: null, skupajIgralcev: 0, percentil: null, klubskoPovprecje: null, spol: 'MOSKI', rekreativec: false },
    zadnje: [],
    naslednjeSrecanje: null,
    prihajajociTurnir: null,
    druzina: false,
    forma: [],
    sezona: null,
    zgostitev: null,
  },
]
