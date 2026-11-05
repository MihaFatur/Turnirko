/* Končna razvrstitev zaključenega dogodka: skupni zapis udeleženca za zaslon
   (stopničke, nivoji) in tisk ter čista pravila, po katerih se ostali
   razdelijo v nivoje oz. v dva stolpca lista. Brez Reacta, da sta zaslon in
   tisk res isti podatek (predaja design_handoff_koncna_razvrstitev, razdelka
   4.1 in 6). */
import { imePrijave } from '../api/tipi'
import type { MrezaDto, PrijavaDto, StatusPrijave, TekmaDto } from '../api/tipi'
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

/* Kdo je na dogodku nastopil: prijavljeni in tisti, ki so med tekmovanjem
   odstopili ali bili izključeni (njihove tekme so odigrane). Odjavljeni so šli
   pred žrebom, rezerva v izbor ni prišla (format TOP) - ta dva v razvrstitvi
   nista, ker nista bila na tekmovanju. */
const NASTOPILI: ReadonlySet<StatusPrijave> = new Set(['PRIJAVLJEN', 'ODSTOPIL', 'DISKVALIFICIRAN'])

const NEZNANO = Number.MAX_SAFE_INTEGER

/* Merilo izpada prijave, ki strežnik zanjo ni zapisal končnega mesta; manjši
   ključ je boljša uvrstitev, enaka ključa si mesto delita.
   - izločilna mreža: kdor je izpadel v poznejšem kolu, je boljši (kolo, v
     katerem je nazadnje igral, je kolo izpada);
   - format TOP: skupine so rangi, zato A pred B, v skupini pa mesto v skupini;
   - izpad v skupini pred izločilnim delom: po mestu v skupini, skupine skupaj;
   - ostalo (npr. odstop pred prvo tekmo) skupaj na koncu. */
function kljucIzpada(podatki: MrezaDto): (prijava: PrijavaDto) => number[] {
  const zadnjeKolo = new Map<number, number>()
  for (const tekma of podatki.tekme) {
    if (tekma.faza !== 'GLAVNI') continue
    for (const udelezenec of [tekma.udelezenec1, tekma.udelezenec2]) {
      if (udelezenec) {
        zadnjeKolo.set(udelezenec.idPrijave, Math.max(zadnjeKolo.get(udelezenec.idPrijave) ?? 0, tekma.kolo))
      }
    }
  }
  const skupinePoVrsti = podatki.skupine
    .filter((skupina) => skupina.stopnja === 1)
    .sort((prva, druga) => prva.oznaka.localeCompare(druga.oznaka, 'sl'))
  const jeTop = podatki.dogodek.sistemTekmovanja === 'SKUPINE'

  return (prijava) => {
    const kolo = zadnjeKolo.get(prijava.id)
    if (kolo !== undefined) return [0, -kolo, 0]
    const vSkupini = prijava.mestoVSkupini ?? NEZNANO
    if (jeTop) {
      const indeks = skupinePoVrsti.findIndex((skupina) => skupina.id === prijava.idSkupina)
      return [0, indeks < 0 ? NEZNANO : indeks, vSkupini]
    }
    return prijava.idSkupina !== null || prijava.mestoVSkupini !== null ? [1, vSkupini, 0] : [2, 0, 0]
  }
}

function primerjajKljuca(prvi: number[], drugi: number[]): number {
  for (let i = 0; i < prvi.length; i++) {
    if (prvi[i] !== drugi[i]) return prvi[i] < drugi[i] ? -1 : 1
  }
  return 0
}

/* VSI, ki so nastopili na dogodku, po končnem mestu. Strežnik zapiše mesta le
   tam, kjer jih sistem sam določi (finale, tekma za 3. mesto, lestvica, uvoz) -
   pri izločilni mreži s 32 igralci torej samo prva štiri, pri formatu TOP nobenega.
   Ostalim vmesnik izpelje mesto po izpadu (glej `kljucIzpada`) in ga postavi za
   zapisana: izpadli v istem kolu si delijo razpon (»5.–8.«), kot v uvoženih
   dogodkih. Zapisano mesto je vedno zadnja beseda. SV regija je izjema: tam
   ima mesto vsak udeleženec po nivojih, izpeljave ne potrebuje. */
export function razvrsceniUdelezenci(podatki: MrezaDto): PrijavaDto[] {
  const sodelujoci = podatki.prijave.filter((p) => p.koncnoMesto !== null || NASTOPILI.has(p.status))
  const zapisani = sodelujoci.filter((p) => p.koncnoMesto !== null)
  const brezMesta = sodelujoci.filter((p) => p.koncnoMesto === null)
  const poMestu = (prva: PrijavaDto, druga: PrijavaDto) => prva.koncnoMesto! - druga.koncnoMesto!
  if (brezMesta.length === 0 || podatki.dogodek.sistemTekmovanja === 'SV_REGIJA') {
    return zapisani.sort(poMestu)
  }

  let prvoProsto = 1
  for (const [mesto, stevilo] of steviloNaMestu(zapisani)) {
    prvoProsto = Math.max(prvoProsto, mesto + stevilo)
  }

  /* Znotraj razreda najprej jakostni vrstni red dogodka, nato abeceda - da je
     izpis med nalaganji isti. */
  const kljuc = kljucIzpada(podatki)
  const kljuci = new Map(brezMesta.map((p) => [p.id, kljuc(p)]))
  brezMesta.sort(
    (prva, druga) =>
      primerjajKljuca(kljuci.get(prva.id)!, kljuci.get(druga.id)!)
      || (prva.stNosilca ?? NEZNANO) - (druga.stNosilca ?? NEZNANO)
      || prva.polnoIme.localeCompare(druga.polnoIme, 'sl'),
  )

  let zacetekRazreda = 0
  const izpeljani = brezMesta.map((p, i) => {
    if (i > 0 && primerjajKljuca(kljuci.get(brezMesta[i - 1].id)!, kljuci.get(p.id)!) !== 0) {
      zacetekRazreda = i
    }
    return { ...p, koncnoMesto: prvoProsto + zacetekRazreda }
  })
  return [...zapisani, ...izpeljani].sort(poMestu)
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
  const razvrscene = razvrsceniUdelezenci(podatki)
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
