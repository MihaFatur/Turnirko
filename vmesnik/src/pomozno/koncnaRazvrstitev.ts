/* Končna razvrstitev zaključenega dogodka: skupni zapis udeleženca za zaslon
   (stopničke, nivoji) in tisk ter čista pravila, po katerih se ostali
   razdelijo v nivoje oz. v dva stolpca lista. Brez Reacta, da sta zaslon in
   tisk res isti podatek (predaja design_handoff_koncna_razvrstitev, razdelka
   4.1 in 6). */
import { imePrijave } from '../api/tipi'
import type { MrezaDto, PrijavaDto, TekmaDto } from '../api/tipi'
import { kratekKlub } from './oblikovanje'

export interface UdelezenecRazvrstitve {
  id: number
  /* Končno mesto; udeleženci z istim mestom si ga delijo. */
  mesto: number
  /* »1.«, »3.–4.«, »5.–8.« - vedno s pikami, tudi na telefonu. */
  oznaka: string
  /* Ime in priimek ločeno (ime v lažji teži). Pri ekipi in paru je ime prazno
     in celotno ime nosi priimek: ni ene osebe, ki bi ji ju lahko ločili. */
  ime: string
  priimek: string
  /* Klub; prazen pri ekipi, ki ime nosi po klubu. */
  klub: string
  /* Izkupiček »9 – 0«: samo krožni sistem, ker po njem določa vrstni red. */
  izid: string
  /* Zgoščena vrednost javne profilne slike (samo stopničke, samo posamično). */
  slika?: string | null
}

/* Mesto 1–3 stoji na stopničkah, vse od četrtega naprej je v nivojih. Izločilni
   sistem brez tekme za 3. mesto da obema poražencema polfinala »3.–4.«, zato so
   stopničke tri ali štiri. */
export const NAJVISJE_MESTO_NA_STOPNICAH = 3

export function razdeliNaStopnice(razvrscene: UdelezenecRazvrstitve[]): {
  vrh: UdelezenecRazvrstitve[]
  ostali: UdelezenecRazvrstitve[]
} {
  return {
    vrh: razvrscene.filter((u) => u.mesto <= NAJVISJE_MESTO_NA_STOPNICAH),
    ostali: razvrscene.filter((u) => u.mesto > NAJVISJE_MESTO_NA_STOPNICAH),
  }
}

export interface NivoRazvrstitve {
  oznaka: string
  clani: UdelezenecRazvrstitve[]
}

/* Zaporedni udeleženci z istim mestom so en nivo (5.–8., 9.–16. ...). Ostali
   pridejo urejeni po mestu, zato je dovolj primerjati soseda. */
export function razdeliVNivoje(ostali: UdelezenecRazvrstitve[]): NivoRazvrstitve[] {
  const nivoji: NivoRazvrstitve[] = []
  for (const udelezenec of ostali) {
    const zadnji = nivoji[nivoji.length - 1]
    if (zadnji && zadnji.clani[0].mesto === udelezenec.mesto) zadnji.clani.push(udelezenec)
    else nivoji.push({ oznaka: udelezenec.oznaka, clani: [udelezenec] })
  }
  return nivoji
}

export type RobVrsticeTiska = 'brez' | 'nivo' | 'pika'

export interface VrsticaTiska {
  udelezenec: UdelezenecRazvrstitve
  /* Oznaka nivoja stoji v prvi vrstici nivoja IN v prvi vrstici vsakega
     stolpca, da se vrstica brez nje ne izgubi, ko se list prelomi. */
  oznaka: string
  rob: RobVrsticeTiska
}

/* Ploski seznam ostalih, razpolovljen na ceil(n/2): levi stolpec je daljši ali
   enak desnemu. Rob nad vrstico: nič v prvi vrstici stolpca, polna črta pred
   prvim v nivoju, pikasta znotraj nivoja. */
export function stolpciTiska(ostali: UdelezenecRazvrstitve[]): VrsticaTiska[][] {
  const polovica = Math.ceil(ostali.length / 2)
  const vrstice = ostali.map((udelezenec, i) => ({
    udelezenec,
    prva: i === 0 || ostali[i - 1].mesto !== udelezenec.mesto,
  }))
  const stolpec = (del: typeof vrstice): VrsticaTiska[] =>
    del.map(({ udelezenec, prva }, indeks) => ({
      udelezenec,
      oznaka: prva || indeks === 0 ? udelezenec.oznaka : '',
      rob: indeks === 0 ? 'brez' : prva ? 'nivo' : 'pika',
    }))
  return [stolpec(vrstice.slice(0, polovica)), stolpec(vrstice.slice(polovica))]
}

/* ---------- Iz podatkov dogodka ---------- */

/* Koliko udeležencev si deli posamezno končno mesto. Izločilni sistem brez
   tekme za 3. mesto da obema poražencema polfinala »3.«, poražencem
   četrtfinala »5.« ... */
export function steviloNaMestu(prijave: PrijavaDto[]): Map<number, number> {
  const stevilo = new Map<number, number>()
  for (const p of prijave) {
    if (p.koncnoMesto !== null) stevilo.set(p.koncnoMesto, (stevilo.get(p.koncnoMesto) ?? 0) + 1)
  }
  return stevilo
}

/* Mesto za izpis. Deljeno mesto se zapiše kot razpon, kot je navada v
   razpisih in biltenih (»3.–4.«, »5.–8.«), in ne kot dvakrat »3.« - tisto se
   je bralo, kot da je eden od njiju napaka. `kratko` je zapis brez pik za
   ozko vrstico telefona (»5–8«); stopničke in nivoji ga ne uporabljajo. */
export function oznakaMesta(mesto: number, deljena: Map<number, number>, kratko: boolean): string {
  const k = deljena.get(mesto) ?? 1
  const pika = kratko ? '' : '.'
  return k > 1 ? `${mesto}${pika}–${mesto + k - 1}${pika}` : `${mesto}${pika}`
}

/* Podnapis pod imenom prijave: klub igralca, pri ekipi pa klub samo, kadar ga
   ime ne pove že samo (klubska ekipa se imenuje po klubu, prosta kluba nima). */
export function podnapisPrijave(prijava: PrijavaDto, ekipno: boolean): string | null {
  if (!ekipno) return prijava.klub ? kratekKlub(prijava.klub) : 'brez kluba'
  if (prijava.klub === null) return 'prosta ekipa'
  return prijava.polnoIme.toLocaleLowerCase('sl').startsWith(prijava.klub.toLocaleLowerCase('sl'))
    ? null
    : prijava.klub
}

/* Bilanca ene prijave: zmage, porazi in vsota sprememb Turnirko ratinga.
   Sešteta je iz že prenesenih tekem - nova poizvedba ni potrebna. */
export interface Bilanca {
  zmage: number
  porazi: number
  rating: number | null
}

export function bilancePrijav(tekme: TekmaDto[]): Map<number, Bilanca> {
  const bilance = new Map<number, Bilanca>()
  const vzemi = (idPrijave: number): Bilanca => {
    let bilanca = bilance.get(idPrijave)
    if (!bilanca) {
      bilanca = { zmage: 0, porazi: 0, rating: null }
      bilance.set(idPrijave, bilanca)
    }
    return bilanca
  }

  for (const tekma of tekme) {
    /* Prosti prehod ni odigrana tekma in ne sme v izkupiček. */
    if (tekma.status !== 'KONCANA' || tekma.izidTip === 'PROSTO') continue
    const strani: [number | undefined, number | null][] = [
      [tekma.udelezenec1?.idPrijave, tekma.spremembaElo1],
      [tekma.udelezenec2?.idPrijave, tekma.spremembaElo2],
    ]
    for (const [idPrijave, sprememba] of strani) {
      if (idPrijave === undefined) continue
      const bilanca = vzemi(idPrijave)
      if (tekma.idZmagovalcaPrijave === idPrijave) bilanca.zmage += 1
      else if (tekma.idZmagovalcaPrijave !== null) bilanca.porazi += 1
      if (sprememba !== null) bilanca.rating = (bilanca.rating ?? 0) + sprememba
    }
  }
  return bilance
}

/* Razvrščeni udeleženci zaključenega dogodka po končnem mestu. `slike` so
   javne profilne slike (id igralca -> zgoščena vrednost); imajo jih samo
   posamični udeleženci, ekipa in par nimata ene fotografije. */
export function udelezenciRazvrstitve(
  podatki: MrezaDto,
  slike: Map<number, string> = new Map(),
): UdelezenecRazvrstitve[] {
  const ekipno = podatki.dogodek.disciplina === 'EKIPNO'
  const razvrscene = podatki.prijave
    .filter((p) => p.koncnoMesto !== null)
    .sort((prva, druga) => prva.koncnoMesto! - druga.koncnoMesto!)
  const deljena = steviloNaMestu(razvrscene)
  /* Bilanco kaže samo krožni sistem: po njej določa vrstni red. */
  const bilance = podatki.dogodek.sistemTekmovanja === 'KROZNI' ? bilancePrijav(podatki.tekme) : null
  return razvrscene.map((p) => {
    const posameznik = !ekipno && p.idIgralca2 === null
    const bilanca = bilance?.get(p.id)
    return {
      id: p.id,
      mesto: p.koncnoMesto!,
      oznaka: oznakaMesta(p.koncnoMesto!, deljena, false),
      ime: posameznik ? (p.ime ?? '') : '',
      priimek: posameznik ? (p.priimek ?? p.polnoIme) : imePrijave(p),
      klub: podnapisPrijave(p, ekipno) ?? '',
      izid: bilance ? (bilanca ? `${bilanca.zmage} – ${bilanca.porazi}` : '—') : '',
      slika: posameznik ? (slike.get(p.idIgralca ?? -1) ?? null) : null,
    }
  })
}
