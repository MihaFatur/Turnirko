/* Računanje s koledarjem: meseci, mreža dni, ton vnosa in trakovi tedna.

   Vse je čista funkcija nad ISO datumi (»2026-10-04«) in nikoli nad objektom
   Date v lokalnem času — poletni čas bi mrežo premaknil za dan. Kjer Date
   vseeno rabimo (dan v tednu), gre skozi Date.UTC, tako kot oblikujTermin. */
import type { KoledarVnosDto } from '../api/tipi'

export interface Mesec {
  leto: number
  /* 1–12; JavaScriptovo štetje od nič ostane skrito v tem modulu. */
  mesec: number
}

const IMENA_MESECEV = [
  'Januar', 'Februar', 'Marec', 'April', 'Maj', 'Junij',
  'Julij', 'Avgust', 'September', 'Oktober', 'November', 'December',
]

/* Rodilnik za izpis celega datuma: »4. oktobra 2026«. */
const MESECI_RODILNIK = [
  'januarja', 'februarja', 'marca', 'aprila', 'maja', 'junija',
  'julija', 'avgusta', 'septembra', 'oktobra', 'novembra', 'decembra',
]

/* Glava mreže; teden se začne s ponedeljkom, ker se v Sloveniji tako bere. */
export const DNEVI_V_TEDNU = ['pon', 'tor', 'sre', 'čet', 'pet', 'sob', 'ned']

const POLNI_DNEVI = [
  'ponedeljek', 'torek', 'sreda', 'četrtek', 'petek', 'sobota', 'nedelja',
]

/* »sobota, 4. oktobra 2026« — naslov izbranega dneva. Dan v tednu je spredaj
   iz istega razloga kot pri terminu kola: gledalec dan išče po njem. */
export function oblikujDanPolno(iso: string): string {
  const [leto, mesec, dan] = iso.split('-').map(Number)
  if (!leto || !mesec || !dan) return iso
  return `${POLNI_DNEVI[danVTednu(iso)]}, ${dan}. ${MESECI_RODILNIK[mesec - 1]} ${leto}`
}

/* Današnji dan po URI NAPRAVE in ne po UTC: ob 23.30 v Sloveniji je v UTC že
   jutri in koledar bi označil napačen dan. */
export function danesIso(): string {
  const zdaj = new Date()
  return sestaviIso(zdaj.getFullYear(), zdaj.getMonth() + 1, zdaj.getDate())
}

export function mesecIzIso(iso: string): Mesec {
  const [leto, mesec] = iso.split('-').map(Number)
  return { leto, mesec }
}

export function premakniMesec(m: Mesec, zaMesecev: number): Mesec {
  const skupaj = m.leto * 12 + (m.mesec - 1) + zaMesecev
  return { leto: Math.floor(skupaj / 12), mesec: (skupaj % 12) + 1 }
}

export function imeMeseca(m: Mesec): string {
  return `${IMENA_MESECEV[m.mesec - 1]} ${m.leto}`
}

export function prviDanMeseca(m: Mesec): string {
  return sestaviIso(m.leto, m.mesec, 1)
}

export function zadnjiDanMeseca(m: Mesec): string {
  return sestaviIso(m.leto, m.mesec, dniVMesecu(m))
}

/* Celice mreže: vodilne prazne do prvega dne, nato dnevi meseca. Sosednjih
   mesecev namenoma ne izrisujemo — dan iz drugega meseca z lastno oznako bi
   se bral kot dan tega meseca, kaj je naslednje, pa pove seznam ob mreži. */
export function celiceMeseca(m: Mesec): (string | null)[] {
  const zamik = danVTednu(prviDanMeseca(m))
  const celice: (string | null)[] = Array.from({ length: zamik }, () => null)
  for (let dan = 1; dan <= dniVMesecu(m); dan++) {
    celice.push(sestaviIso(m.leto, m.mesec, dan))
  }
  /* Zadnja vrsta se dopolni do sedmih, da mreža ostane pravokotna. */
  while (celice.length % 7 !== 0) celice.push(null)
  return celice
}

/* 0 = ponedeljek … 6 = nedelja. */
export function danVTednu(iso: string): number {
  const [leto, mesec, dan] = iso.split('-').map(Number)
  return (new Date(Date.UTC(leto, mesec - 1, dan)).getUTCDay() + 6) % 7
}

export function jeVikend(iso: string): boolean {
  return danVTednu(iso) >= 5
}

export function stevilkaDneva(iso: string): number {
  return Number(iso.slice(8, 10))
}

/* Vsi dnevi, ki jih vnos zasede. Turnir traja lahko več dni in mora biti
   označen na vsakem — »13.–14. avgust« je en turnir in ne dva. */
export function dneviVnosa(vnos: KoledarVnosDto): string[] {
  const dnevi: string[] = []
  let dan = vnos.datum
  /* Varovalo: pokvarjen razpon (konec pred začetkom) ne sme v neskončno zanko. */
  for (let i = 0; i < 60 && dan <= vnos.datumKonca; i++) {
    dnevi.push(dan)
    dan = naslednjiDan(dan)
  }
  return dnevi.length > 0 ? dnevi : [vnos.datum]
}

export function naslednjiDan(iso: string): string {
  const [leto, mesec, dan] = iso.split('-').map(Number)
  const d = new Date(Date.UTC(leto, mesec - 1, dan + 1))
  return sestaviIso(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate())
}

/* Dan -> vnosi, ki ta dan tečejo. Vrstni red vnosov je vrstni red strežnika
   (datum, nato ime), zato je izpis dneva vedno enak. */
export function poDnevih(vnosi: KoledarVnosDto[]): Map<string, KoledarVnosDto[]> {
  const zbir = new Map<string, KoledarVnosDto[]>()
  for (const vnos of vnosi) {
    for (const dan of dneviVnosa(vnos)) {
      const zeVnjih = zbir.get(dan)
      if (zeVnjih) zeVnjih.push(vnos)
      else zbir.set(dan, [vnos])
    }
  }
  return zbir
}

/* Identiteta tekmovanja, ne vnosa: vsa kola iste lige so ista liga in imajo
   isto barvo. Turnirji in lige imajo ločeni zaporedji id-jev, zato je v
   ključu tudi vrsta. */
export function kljucTekmovanja(vnos: KoledarVnosDto): string {
  return `${vnos.vrsta}-${vnos.id}`
}

/* Ton vnosa: turnir je moder, ligaško kolo zeleno.

   Ton je POMEN in ne identiteta: pove, katere VRSTE je tekmovanje, ne
   katero tekmovanje je. Prej je bilo obratno — šest tonov, dodeljenih po
   vrstnem redu pojavitve — in barva je bila last pogleda, zato je pod mrežo
   morala stati legenda z imeni. Ta je pojedla več prostora kot mreža sama,
   imena pa tako ali tako izpiše seznam ob njej.

   Dve barvi je zato mogoče prebrati brez legende: ključ pod mrežo (Turnir ·
   Ligaško kolo · Odigrano) je stalen in ne raste s številom tekmovanj. */
export function tonVnosa(vnos: KoledarVnosDto): 1 | 2 {
  return vnos.vrsta === 'TURNIR' ? 1 : 2
}

/* En pas v tednu: kje se začne, čez koliko dni teče in v katerem pasu leži. */
export interface Trak {
  vnos: KoledarVnosDto
  /* Stolpec mreže, 1 … 7 (CSS grid šteje od ena). */
  stolpec: number
  /* Čez koliko dni tega tedna teče; večdnevni turnir je EN trak in ne več
     kvadratkov. */
  razpon: number
  /* Vrstica pasu, 0 navzgor. */
  pas: number
}

/* Trakovi enega tedna, razporejeni v pasove.

   Čista funkcija nad ISO datumi: dobi celice tedna (null = dan pred prvim oz.
   za zadnjim v mesecu) in vnose, ki se meseca dotikajo.

   Vrstni red pred razporejanjem je namenoma po razponu NAVZDOL: dolgi pasovi
   gredo zgoraj, sicer se kratki zataknejo pod njimi in teden dobi luknje.
   Razporeditev sama je požrešna — vsak kos v prvi pas, kjer se ne prekriva z
   že postavljenim.

   Kar v `najvecPasov` ne gre, se ne izriše, ampak prešteje: vsak dan, ki ga
   tak kos zaseda, dobi svoj »+N« (obstoječi .koledar__vec). Tiho odrezan trak
   bi pomenil dan, ki v mreži trdi, da je prazen. */
export function trakoviTedna(
  teden: (string | null)[],
  vnosi: KoledarVnosDto[],
  najvecPasov: number,
): { trakovi: Trak[]; skriti: Map<string, number> } {
  const prvi = teden.find((d) => d !== null)
  const zadnji = [...teden].reverse().find((d) => d !== null)
  if (!prvi || !zadnji) return { trakovi: [], skriti: new Map() }

  const kosi: Trak[] = []
  for (const vnos of vnosi) {
    /* Presek vnosa s tednom; kar teče čez nedeljo, se v naslednjem tednu
       začne znova od ponedeljka. */
    const od = vnos.datum > prvi ? vnos.datum : prvi
    const doKdaj = vnos.datumKonca < zadnji ? vnos.datumKonca : zadnji
    if (od > doKdaj) continue
    const zacetek = teden.indexOf(od)
    const konec = teden.indexOf(doKdaj)
    if (zacetek < 0 || konec < 0) continue
    kosi.push({ vnos, stolpec: zacetek + 1, razpon: konec - zacetek + 1, pas: 0 })
  }

  kosi.sort(
    (a, b) =>
      b.razpon - a.razpon ||
      a.stolpec - b.stolpec ||
      a.vnos.datum.localeCompare(b.vnos.datum) ||
      a.vnos.ime.localeCompare(b.vnos.ime, 'sl'),
  )

  const pasovi: Trak[][] = []
  for (const kos of kosi) {
    let pas = 0
    for (;;) {
      const vrsta = (pasovi[pas] ??= [])
      const trk = vrsta.some(
        (x) => kos.stolpec < x.stolpec + x.razpon && x.stolpec < kos.stolpec + kos.razpon,
      )
      if (!trk) {
        vrsta.push(kos)
        kos.pas = pas
        break
      }
      pas++
    }
  }

  const trakovi: Trak[] = []
  const skriti = new Map<string, number>()
  for (const kos of kosi) {
    if (kos.pas < najvecPasov) {
      trakovi.push(kos)
      continue
    }
    for (let stolpec = kos.stolpec; stolpec < kos.stolpec + kos.razpon; stolpec++) {
      const dan = teden[stolpec - 1]
      if (dan) skriti.set(dan, (skriti.get(dan) ?? 0) + 1)
    }
  }
  return { trakovi, skriti }
}

/* Vnosi, ki se še niso končali, od najbližjega naprej — sklop »Naslednje«.
   Merilo je datumKonca in ne datum: turnir, ki se je začel včeraj in traja še
   danes, je »v teku« in ne mimo. */
export function prihajajoci(vnosi: KoledarVnosDto[], odDneva: string): KoledarVnosDto[] {
  return vnosi
    .filter((v) => v.datumKonca >= odDneva)
    .slice()
    .sort((a, b) => a.datum.localeCompare(b.datum) || a.ime.localeCompare(b.ime, 'sl'))
}

function dniVMesecu(m: Mesec): number {
  return new Date(Date.UTC(m.leto, m.mesec, 0)).getUTCDate()
}

function sestaviIso(leto: number, mesec: number, dan: number): string {
  return `${leto}-${String(mesec).padStart(2, '0')}-${String(dan).padStart(2, '0')}`
}
