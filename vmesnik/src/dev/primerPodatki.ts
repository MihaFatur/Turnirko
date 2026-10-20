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
