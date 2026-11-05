/* Vzorčni podatki za posnetke končne razvrstitve (/dev/koncna-razvrstitev).
   Prepisani iz predaje design_handoff_koncna_razvrstitev/primerPodatki.json -
   posnetki v predaji so narejeni prav iz teh številk, zato jih ne spreminjaj.

   foto: »slika« = razvojni nadomestek fotografije (črtast kvadrat), »obris« =
   igralec brez slike (obris glave in ramen), »brez« = slik na stopničkah ni.
   izid: samo krožni sistem. */

export type FotoPrimera = 'slika' | 'obris' | 'brez'

export interface UdelezenecPrimera {
  koncnoMesto: number
  steviloNaMestu: number
  ime: string
  priimek: string
  klub: string
  foto: FotoPrimera
  izid?: string
}

export interface PrimerRazvrstitve {
  naslov: string
  lastnosti: string
  ekipno: boolean
  udelezenci: UdelezenecPrimera[]
}

export const PRIMERI: Record<'izlocilni' | 'krozni' | 'ekipni', PrimerRazvrstitve> = {
  izlocilni: {
    naslov: 'Odprto prvenstvo Savinje',
    lastnosti: 'Člani · Moški · Posamezno · Izločilni sistem',
    ekipno: false,
    udelezenci: [
      { koncnoMesto: 1, steviloNaMestu: 1, ime: 'Žiga', priimek: 'Kos', klub: 'NTK Gorica', foto: 'slika' },
      { koncnoMesto: 2, steviloNaMestu: 1, ime: 'Rok', priimek: 'Pirc', klub: 'TTK Velenje', foto: 'obris' },
      { koncnoMesto: 3, steviloNaMestu: 2, ime: 'Urban', priimek: 'Turk', klub: 'KNT Radlje', foto: 'slika' },
      { koncnoMesto: 3, steviloNaMestu: 2, ime: 'Peter', priimek: 'Jerman', klub: 'ŠD Sežana', foto: 'obris' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: 'Grega', priimek: 'Perko', klub: 'NTK Celje', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: 'Nejc', priimek: 'Sever', klub: 'KNT Kajuh', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: 'Gašper', priimek: 'Pungartnik', klub: 'NTK Ptuj', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: 'Klemen', priimek: 'Vidmar', klub: 'NTK Savinja', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Andrej', priimek: 'Mlakar', klub: 'NTK Ormož', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Dejan', priimek: 'Petek', klub: 'NTK Krško', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Jure', priimek: 'Zupančič', klub: 'NTK Maribor', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Jan', priimek: 'Hočevar', klub: 'NTK Žalec', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Aljaž', priimek: 'Tratnik', klub: 'NTK Gorica', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Simon', priimek: 'Drofenik', klub: 'TTK Velenje', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Luka', priimek: 'Golob', klub: 'KNT Radlje', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 8, ime: 'Anže', priimek: 'Kovačič', klub: 'ŠD Sežana', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Blaž', priimek: 'Rozman', klub: 'NTK Celje', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'David', priimek: 'Bizjak', klub: 'KNT Kajuh', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Tadej', priimek: 'Kocjančič', klub: 'NTK Ptuj', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Matej', priimek: 'Podgoršek', klub: 'NTK Savinja', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Tilen', priimek: 'Kovač', klub: 'NTK Ormož', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Domen', priimek: 'Kralj', klub: 'NTK Krško', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Marko', priimek: 'Lah', klub: 'NTK Maribor', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Vid', priimek: 'Potočnik', klub: 'NTK Žalec', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Žiga', priimek: 'Kolar', klub: 'NTK Gorica', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Rok', priimek: 'Rebernik', klub: 'TTK Velenje', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Urban', priimek: 'Fras', klub: 'KNT Radlje', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Peter', priimek: 'Novak', klub: 'ŠD Sežana', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Grega', priimek: 'Božič', klub: 'NTK Celje', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Nejc', priimek: 'Kavčič', klub: 'KNT Kajuh', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Gašper', priimek: 'Šuštar', klub: 'NTK Ptuj', foto: 'brez' },
      { koncnoMesto: 17, steviloNaMestu: 16, ime: 'Klemen', priimek: 'Čeh', klub: 'NTK Savinja', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Andrej', priimek: 'Cerar', klub: 'NTK Ormož', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Dejan', priimek: 'Lesjak', klub: 'NTK Krško', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Jure', priimek: 'Horvat', klub: 'NTK Maribor', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Jan', priimek: 'Korošec', klub: 'NTK Žalec', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Aljaž', priimek: 'Zajc', klub: 'NTK Gorica', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Simon', priimek: 'Breznik', klub: 'TTK Velenje', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Luka', priimek: 'Žnidar', klub: 'KNT Radlje', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Anže', priimek: 'Jelen', klub: 'ŠD Sežana', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Blaž', priimek: 'Bračko', klub: 'NTK Celje', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'David', priimek: 'Zupan', klub: 'KNT Kajuh', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Tadej', priimek: 'Hribar', klub: 'NTK Ptuj', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Matej', priimek: 'Oblak', klub: 'NTK Savinja', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Tilen', priimek: 'Medved', klub: 'NTK Ormož', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Domen', priimek: 'Vodopivec', klub: 'NTK Krško', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Marko', priimek: 'Mavrič', klub: 'NTK Maribor', foto: 'brez' },
      { koncnoMesto: 33, steviloNaMestu: 16, ime: 'Vid', priimek: 'Ribič', klub: 'NTK Žalec', foto: 'brez' },
    ],
  },
  krozni: {
    naslov: 'Savinjski pokal – veterani',
    lastnosti: 'Veterani 50+ · Moški · Posamezno · Krožni sistem',
    ekipno: false,
    udelezenci: [
      { koncnoMesto: 1, steviloNaMestu: 1, ime: 'Jure', priimek: 'Hribar', klub: 'NTK Maribor', foto: 'slika', izid: '9 – 0' },
      { koncnoMesto: 2, steviloNaMestu: 1, ime: 'Jan', priimek: 'Oblak', klub: 'NTK Žalec', foto: 'obris', izid: '8 – 1' },
      { koncnoMesto: 3, steviloNaMestu: 1, ime: 'Aljaž', priimek: 'Medved', klub: 'NTK Gorica', foto: 'slika', izid: '6 – 3' },
      { koncnoMesto: 4, steviloNaMestu: 1, ime: 'Simon', priimek: 'Vodopivec', klub: 'TTK Velenje', foto: 'brez', izid: '6 – 3' },
      { koncnoMesto: 5, steviloNaMestu: 1, ime: 'Luka', priimek: 'Mavrič', klub: 'KNT Radlje', foto: 'brez', izid: '5 – 4' },
      { koncnoMesto: 6, steviloNaMestu: 1, ime: 'Anže', priimek: 'Ribič', klub: 'ŠD Sežana', foto: 'brez', izid: '4 – 5' },
      { koncnoMesto: 7, steviloNaMestu: 1, ime: 'Blaž', priimek: 'Kos', klub: 'NTK Celje', foto: 'brez', izid: '3 – 6' },
      { koncnoMesto: 8, steviloNaMestu: 1, ime: 'David', priimek: 'Pirc', klub: 'KNT Kajuh', foto: 'brez', izid: '2 – 7' },
      { koncnoMesto: 9, steviloNaMestu: 1, ime: 'Tadej', priimek: 'Turk', klub: 'NTK Ptuj', foto: 'brez', izid: '1 – 8' },
      { koncnoMesto: 10, steviloNaMestu: 1, ime: 'Matej', priimek: 'Jerman', klub: 'NTK Savinja', foto: 'brez', izid: '1 – 8' },
    ],
  },
  ekipni: {
    naslov: 'Ekipno prvenstvo Štajerske',
    lastnosti: 'Člani · Ekipno · Izločilni sistem',
    ekipno: true,
    udelezenci: [
      { koncnoMesto: 1, steviloNaMestu: 1, ime: '', priimek: 'NTK Savinja 1', klub: '', foto: 'brez' },
      { koncnoMesto: 2, steviloNaMestu: 1, ime: '', priimek: 'NTK Gorica 1', klub: '', foto: 'brez' },
      { koncnoMesto: 3, steviloNaMestu: 1, ime: '', priimek: 'KNT Kajuh', klub: '', foto: 'brez' },
      { koncnoMesto: 4, steviloNaMestu: 1, ime: '', priimek: 'NTK Maribor 1', klub: '', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: '', priimek: 'ŠD Sežana', klub: '', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: '', priimek: 'NTK Ormož', klub: '', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: '', priimek: 'NTK Savinja 2', klub: '', foto: 'brez' },
      { koncnoMesto: 5, steviloNaMestu: 4, ime: '', priimek: 'NTK Gorica 2', klub: '', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 4, ime: '', priimek: 'NTK Ptuj', klub: '', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 4, ime: '', priimek: 'TTK Velenje', klub: '', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 4, ime: '', priimek: 'NTK Žalec', klub: '', foto: 'brez' },
      { koncnoMesto: 9, steviloNaMestu: 4, ime: '', priimek: 'NTK Celje', klub: '', foto: 'brez' },
    ],
  },
}
