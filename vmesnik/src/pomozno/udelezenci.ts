/* Pogled »Udeleženci«: čista pravila (predaja design_handoff_udelezenci,
   varianta 1a). Brez Reacta, da so merila razvrščanja, skupin in filtrov na
   enem mestu in jih komponenta samo izriše.

   Ena vrstica je ena tekmovalna enota: igralec, par (dvojice) ali ekipa.
   Številka vrstice je JAKOSTNO MESTO (nosilec): po njem se razporedijo
   nosilci in po njem so vrstice razdeljene v skupine (1–2, 3–4, 5–8, 9–16,
   od 17). */
import { imePrijave } from '../api/tipi'
import type { MrezaDto, PrijavaDto, StatusPrijave } from '../api/tipi'
import { oznakaMesta, razvrsceniUdelezenci, steviloNaMestu } from './koncnaRazvrstitev'
import { kratekKlub, sklonEkip, sklonKlubov, sklonParov, sklonPrijavljenih } from './oblikovanje'

export type FazaUdelezencev = 'priprava' | 'v_teku' | 'zakljucen'
export type VrstaUdelezencev = 'posamezniki' | 'dvojice' | 'ekipe'
export type RazvrstitevUdelezencev = 'jakost' | 'uvrstitev' | 'priimek' | 'klub'
/* Stanje med turnirjem: v igri, izpadel (poraz v izločilni mreži, ni prišel v
   mrežo) ali odstopil. Izpeljano iz tekem, strežnik ga ne hrani. */
export type StanjeUdelezenca = 'v_igri' | 'izpadel' | 'odstopil'

/* Palica ratinga: širina je (rating − PALICA_NAJNIZJI_RATING) / PALICA_RATINGOV_NA_ODSTOTEK
   odstotkov, omejena na 0–100. Meji sta iz predaje (1400 do 2200), izbrani za
   vzorčne podatke; prava lestvica je nižje (povprečje igralcev je pod 1000), zato
   palica pri slabše ocenjenih udeležencih ostane prazna. Če jo je treba prilagoditi,
   se spremeni samo ta konstanta. */
export const PALICA_NAJNIZJI_RATING = 1400
export const PALICA_RATINGOV_NA_ODSTOTEK = 8

export function odstotekPalice(rating: number | null): number {
  if (rating === null) return 0
  return Math.max(
    0,
    Math.min(100, (rating - PALICA_NAJNIZJI_RATING) / PALICA_RATINGOV_NA_ODSTOTEK),
  )
}

/* Skupine so do štirih nosilcev kartice, potem seznam; mejo 24 vrstic za
   en stolpec določa predaja. */
const NAJVEC_VRSTIC_V_ENEM_STOLPCU = 24
const NAJMANJ_VRSTIC_ZA_DVA_STOLPCA = 4

export const BREZ_KLUBA = 'brez kluba'
const PROSTA_EKIPA = 'prosta ekipa'

export interface DelImena {
  /* Prazno pri ekipi: ime ekipe je celo v priimku (v debeli teži). */
  ime: string
  priimek: string
}

export interface VrsticaUdelezenca {
  /* Id prijave. */
  id: number
  /* Jakostno mesto (nosilec). */
  mesto: number
  /* Ena vrstica imena; par ima dve. */
  imena: DelImena[]
  /* Celotno ime za iskanje: »Ana Novak«, »Ana Novak / Eva Zajc«, ime ekipe. */
  imeCelo: string
  /* Klubi (kratka imena, brez ponovitev). Prazno pri prosti ekipi. */
  klubi: string[]
  klub: string
  /* »2184«, pri paru »2184 / 2165«, brez ratinga »—«. */
  rating: string
  /* Rating za razpon skupine in palico: pri paru povprečje obeh, sicer
     število; null, kadar ga ni (par z enim igralcem brez ratinga). */
  ratingStevilo: number | null
  palica: number
  /* »Kader: Novak · Kos · Petek« (samo ekipa). */
  podnapis: string
  stanje: StanjeUdelezenca | null
  /* Končno mesto in njegova oznaka (»3.–4.«); samo v zaključenem dogodku. */
  koncnoMesto: number | null
  koncnaOznaka: string
  /* Prijava še velja (ni odstopila, ni bila izključena). */
  jeAktiven: boolean
}

export interface DodatekUdelezencev {
  naslov: string
  namig: string
  imena: { besedilo: string; klub: string }[]
}

export interface UdelezenciDogodka {
  vrsta: VrstaUdelezencev
  faza: FazaUdelezencev
  vrstice: VrsticaUdelezenca[]
  /* Ali tekmovanje pozna izpad (izločilna mreža); sicer stanj »V igri«
     in »Izpadel« ni in jih pogled ne izpisuje. */
  imaIzpad: boolean
  dodatek: DodatekUdelezencev | null
}

export function vrstaUdelezencev(podatki: MrezaDto): VrstaUdelezencev {
  const disciplina = podatki.dogodek.disciplina
  return disciplina === 'DVOJICE' ? 'dvojice' : disciplina === 'EKIPNO' ? 'ekipe' : 'posamezniki'
}

/* Faza po DEJANSKEM stanju: žreb pred datumom turnirja je gledalcu »V pripravi«,
   a žreb je že izveden in nosilci veljajo. */
export function fazaUdelezencev(podatki: MrezaDto): FazaUdelezencev {
  const status = podatki.dogodek.dejanskiStatus
  return status === 'PRIPRAVA' ? 'priprava' : status === 'ZAKLJUCEN' ? 'zakljucen' : 'v_teku'
}

const NASTOPAJO: ReadonlySet<StatusPrijave> = new Set(['PRIJAVLJEN', 'ODSTOPIL', 'DISKVALIFICIRAN'])

/* ---------- Iz podatkov dogodka v vrstice ---------- */

function klubPrijave(klub: string | null): string {
  return klub ? kratekKlub(klub) : BREZ_KLUBA
}

function imenaPrijave(prijava: PrijavaDto, vrsta: VrstaUdelezencev): DelImena[] {
  if (vrsta === 'ekipe') return [{ ime: '', priimek: prijava.polnoIme }]
  /* Imeni prideta ločeni iz baze; rezanje polnega imena ne zna (»Ana Marija
     Novak«), zato se ob manjkajočem priimku izpiše celo ime v debeli teži. */
  const prvi = { ime: prijava.ime ?? '', priimek: prijava.priimek ?? prijava.polnoIme }
  if (prijava.polnoIme2 === null) return [prvi]
  return [prvi, { ime: prijava.ime2 ?? '', priimek: prijava.priimek2 ?? prijava.polnoIme2 }]
}

function klubiPrijave(prijava: PrijavaDto, vrsta: VrstaUdelezencev): string[] {
  if (vrsta === 'ekipe') return prijava.klub ? [kratekKlub(prijava.klub)] : []
  const prvi = klubPrijave(prijava.klub)
  if (prijava.polnoIme2 === null) return [prvi]
  const drugi = klubPrijave(prijava.klub2)
  return prvi === drugi ? [prvi] : [prvi, drugi]
}

function ratingPrijave(prijava: PrijavaDto): { besedilo: string; stevilo: number | null } {
  if (prijava.polnoIme2 !== null) {
    const { rating: prvi, rating2: drugi } = prijava
    return {
      besedilo: `${prvi ?? '—'} / ${drugi ?? '—'}`,
      stevilo: prvi !== null && drugi !== null ? (prvi + drugi) / 2 : null,
    }
  }
  return { besedilo: prijava.rating !== null ? String(prijava.rating) : '—', stevilo: prijava.rating }
}

/* Moč za razvrstitev, kadar jakostnega vrstnega reda ni povedal strežnik:
   rating ob žrebu (sicer trenutni), pri paru povprečje. Brez ratinga je na dnu. */
function mocPrijave(prijava: PrijavaDto): number {
  const prvi = prijava.ratingObZrebu ?? prijava.rating
  if (prijava.polnoIme2 === null) return prvi ?? -1
  const drugi = prijava.ratingObZrebu2 ?? prijava.rating2
  return prvi === null || drugi === null ? -1 : (prvi + drugi) / 2
}

/* Jakostni vrstni red in mesto vsake prijave.
   - Žreb je izveden in vsi imajo nosilca: velja strežnikova številka (tudi
     odstopili ostanejo na svojem mestu, vrzeli ni).
   - V pripravi strežnik, kjer žreb pozna nosilce, vrne prijave že v vrstnem
     redu, po katerem bo žrebal (shranjen vrstni red, sicer predlog) - tak
     vrstni red ostane.
   - Drugače (dvojice, krožni sistem): po moči. */
function poJakosti(
  prijave: PrijavaDto[],
  podatki: MrezaDto,
  faza: FazaUdelezencev,
): { prijava: PrijavaDto; mesto: number }[] {
  const vsiImajoNosilca = prijave.length > 0 && prijave.every((p) => p.stNosilca !== null)
  if (vsiImajoNosilca) {
    const urejene = [...prijave].sort((a, b) => a.stNosilca! - b.stNosilca!)
    return urejene.map((prijava, i) => ({
      prijava,
      mesto: faza === 'priprava' ? i + 1 : prijava.stNosilca!,
    }))
  }
  const urejene =
    podatki.izbor !== null
      ? prijave
      : [...prijave].sort(
          (a, b) => mocPrijave(b) - mocPrijave(a) || imePrijave(a).localeCompare(imePrijave(b), 'sl'),
        )
  return urejene.map((prijava, i) => ({ prijava, mesto: i + 1 }))
}

/* Stanje »V igri« / »Izpadel« med turnirjem. Strežnik ga ne hrani, zato ga
   izpelje vmesnik iz tekem izločilne mreže:
   - v igri je, kdor ima še neodigrano tekmo (glavna mreža ali tekma za 3. mesto)
     ali v mreži ni nikoli izgubil (prvak čaka na konec dogodka);
   - izpadel je, kdor je v mreži izgubil in nima več tekme, ali kdor v mrežo
     (po skupinah) ni prišel;
   - odstopil je odstopil, izključen je izpadel.
   Krožni sistem, format TOP in SV regija izpada ne poznajo (vsak igra vse
   svoje tekme), zato tam stanja ni - in tudi ne napisa »V igri« ob vsakem. */
function stanjaMedTurnirjem(podatki: MrezaDto): { stanja: Map<number, StanjeUdelezenca>; imaIzpad: boolean } {
  const sistem = podatki.dogodek.sistemTekmovanja
  const vMrezi = new Set<number>()
  const cakajoci = new Set<number>()
  const izgubili = new Set<number>()
  for (const tekma of podatki.tekme) {
    if (tekma.faza !== 'GLAVNI' && tekma.faza !== 'TOLAZILNI') continue
    const strani = [tekma.udelezenec1, tekma.udelezenec2].filter((u) => u !== null)
    if (tekma.faza === 'GLAVNI') strani.forEach((u) => vMrezi.add(u.idPrijave))
    if (tekma.status !== 'KONCANA') {
      strani.forEach((u) => cakajoci.add(u.idPrijave))
    } else if (
      tekma.faza === 'GLAVNI'
      && tekma.izidTip !== 'PROSTO'
      && tekma.idZmagovalcaPrijave !== null
    ) {
      strani
        .filter((u) => u.idPrijave !== tekma.idZmagovalcaPrijave)
        .forEach((u) => izgubili.add(u.idPrijave))
    }
  }
  const imaIzpad = sistem === 'IZLOCILNI' || (sistem === 'SKUPINE_IZLOCILNI' && vMrezi.size > 0)

  const stanja = new Map<number, StanjeUdelezenca>()
  for (const prijava of podatki.prijave) {
    if (prijava.status === 'ODSTOPIL') stanja.set(prijava.id, 'odstopil')
    else if (prijava.status === 'DISKVALIFICIRAN') stanja.set(prijava.id, 'izpadel')
    else if (imaIzpad && prijava.status === 'PRIJAVLJEN') {
      const vIgri = cakajoci.has(prijava.id) || (vMrezi.has(prijava.id) && !izgubili.has(prijava.id))
      stanja.set(prijava.id, vIgri ? 'v_igri' : 'izpadel')
    }
  }
  return { stanja, imaIzpad }
}

function dodatekUdelezencev(
  podatki: MrezaDto,
  faza: FazaUdelezencev,
  vrsta: VrstaUdelezencev,
): DodatekUdelezencev | null {
  if (vrsta === 'posamezniki' && faza !== 'priprava') {
    const rezerve = podatki.prijave
      .filter((p) => p.status === 'REZERVA')
      .sort((a, b) => (a.stNosilca ?? Number.MAX_SAFE_INTEGER) - (b.stNosilca ?? Number.MAX_SAFE_INTEGER))
    if (rezerve.length === 0) return null
    return {
      naslov: `Rezerve (${rezerve.length})`,
      namig: 'Niso prišli v izbor najboljših. Vrstni red pove, kdo je bil prvi pod črto.',
      imena: rezerve.map((p) => ({
        besedilo: `${p.stNosilca !== null ? `${p.stNosilca}. ` : ''}${p.polnoIme}`,
        klub: klubPrijave(p.klub),
      })),
    }
  }
  if (vrsta === 'dvojice' && faza === 'priprava') {
    const brezPara = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN' && p.polnoIme2 === null)
    if (brezPara.length === 0) return null
    return {
      naslov: `Brez soigralca (${brezPara.length})`,
      namig: 'Dokler ne dobijo para, v žreb ne gredo.',
      imena: brezPara.map((p) => ({ besedilo: p.polnoIme, klub: klubPrijave(p.klub) })),
    }
  }
  return null
}

export function udelezenciDogodka(podatki: MrezaDto): UdelezenciDogodka {
  const vrsta = vrstaUdelezencev(podatki)
  const faza = fazaUdelezencev(podatki)

  let prijave: PrijavaDto[]
  if (faza === 'priprava') {
    /* Par je prijava z obema igralcema; kdor soigralca še nima, v žreb ne gre
       in stoji v dodatku. */
    prijave = podatki.prijave.filter(
      (p) => p.status === 'PRIJAVLJEN' && (vrsta !== 'dvojice' || p.polnoIme2 !== null),
    )
  } else if (faza === 'zakljucen') {
    /* Vsi, ki so nastopili, s končnim mestom (zapisanim ali izpeljanim). */
    prijave = razvrsceniUdelezenci(podatki)
  } else {
    prijave = podatki.prijave.filter((p) => NASTOPAJO.has(p.status))
  }

  const { stanja, imaIzpad } = faza === 'v_teku' ? stanjaMedTurnirjem(podatki) : { stanja: new Map(), imaIzpad: false }
  const deljena = faza === 'zakljucen' ? steviloNaMestu(prijave) : new Map<number, number>()

  const vrstice = poJakosti(prijave, podatki, faza).map(({ prijava, mesto }): VrsticaUdelezenca => {
    const rating = ratingPrijave(prijava)
    const klubi = klubiPrijave(prijava, vrsta)
    return {
      id: prijava.id,
      mesto,
      imena: imenaPrijave(prijava, vrsta),
      imeCelo: imePrijave(prijava),
      klubi,
      klub: klubi.length > 0 ? klubi.join(' · ') : PROSTA_EKIPA,
      rating: rating.besedilo,
      ratingStevilo: rating.stevilo,
      palica: odstotekPalice(rating.stevilo),
      podnapis: vrsta === 'ekipe' && prijava.kader.length > 0 ? `Kader: ${prijava.kader.join(' · ')}` : '',
      stanje: stanja.get(prijava.id) ?? null,
      koncnoMesto: faza === 'zakljucen' ? prijava.koncnoMesto : null,
      koncnaOznaka:
        faza === 'zakljucen' && prijava.koncnoMesto !== null
          ? oznakaMesta(prijava.koncnoMesto, deljena, false)
          : '',
      jeAktiven: prijava.status === 'PRIJAVLJEN',
    }
  })

  return { vrsta, faza, vrstice, imaIzpad, dodatek: dodatekUdelezencev(podatki, faza, vrsta) }
}

/* ---------- Besedila ---------- */

export function naslovUdelezencev(vrsta: VrstaUdelezencev, faza: FazaUdelezencev): string {
  if (vrsta === 'ekipe') return 'Ekipe'
  return vrsta === 'dvojice' && faza === 'priprava' ? 'Pari' : 'Udeleženci'
}

export function glavaImena(vrsta: VrstaUdelezencev): string {
  return vrsta === 'dvojice' ? 'Par' : vrsta === 'ekipe' ? 'Ekipa' : 'Igralec'
}

export function stevilkeKlubov(vrstice: VrsticaUdelezenca[]): Map<string, number> {
  const stevci = new Map<string, number>()
  for (const vrstica of vrstice) {
    for (const klub of vrstica.klubi) stevci.set(klub, (stevci.get(klub) ?? 0) + 1)
  }
  return stevci
}

/* »36 prijavljenih · 7 klubov« (pri dvojicah »parov«, pri ekipah »ekip«). */
export function metaUdelezencev(vrsta: VrstaUdelezencev, vrstice: VrsticaUdelezenca[]): string {
  const n = vrstice.length
  const enota = vrsta === 'dvojice' ? sklonParov(n) : vrsta === 'ekipe' ? sklonEkip(n) : sklonPrijavljenih(n)
  const klubov = stevilkeKlubov(vrstice).size
  return `${n} ${enota} · ${klubov} ${sklonKlubov(klubov)}`
}

export function opombaUdelezencev(
  faza: FazaUdelezencev,
  vrstice: VrsticaUdelezenca[],
  imaIzpad: boolean,
): string {
  if (faza === 'priprava') {
    return 'Žreb še ni izveden. Številka je jakostno mesto; po njem se razporedijo nosilci.'
  }
  if (faza === 'v_teku') {
    if (!imaIzpad) return 'Žreb je izveden. Številka je nosilec, kot je bil določen ob žrebu.'
    const vIgri = vrstice.filter((v) => v.stanje === 'v_igri').length
    return `V igri ${vIgri} od ${vrstice.length}. Izpadli so zbledeli.`
  }
  return 'Razporeditev je kot ob prijavi (po nosilcih); ob vsakem udeležencu piše, kje je končal.'
}

/* ---------- Meniji ---------- */

export interface MoznostMenija<T extends string | null> {
  vrednost: T
  ime: string
  /* Število ob imenu (mono, desno); prazno, kjer števila ni. */
  stevilo: string
}

/* »Vsi klubi« (skupno število), nato klubi po abecedi s številom. Pri dvojicah
   par šteje v vsakem klubu igralcev. */
export function moznostiKluba(vrstice: VrsticaUdelezenca[]): MoznostMenija<string | null>[] {
  const klubi = [...stevilkeKlubov(vrstice).entries()].sort((a, b) => a[0].localeCompare(b[0], 'sl'))
  return [
    { vrednost: null, ime: 'Vsi klubi', stevilo: String(vrstice.length) },
    ...klubi.map(([ime, stevilo]) => ({ vrednost: ime, ime, stevilo: String(stevilo) })),
  ]
}

export function moznostiRazvrstitve(faza: FazaUdelezencev): MoznostMenija<RazvrstitevUdelezencev>[] {
  const zakljucen = faza === 'zakljucen'
  return [
    { vrednost: 'jakost', ime: zakljucen ? 'Po nosilcih' : 'Po jakosti', stevilo: '' },
    ...(zakljucen ? [{ vrednost: 'uvrstitev' as const, ime: 'Po uvrstitvi', stevilo: '' }] : []),
    { vrednost: 'priimek', ime: 'Po priimku', stevilo: '' },
    { vrednost: 'klub', ime: 'Po klubu', stevilo: '' },
  ]
}

/* ---------- Prikaz: filtri, razvrstitev, skupine ---------- */

export interface FiltriUdelezencev {
  iskanje: string
  /* null = vsi klubi. */
  klub: string | null
  razvrstitev: RazvrstitevUdelezencev
}

export interface KarticeSkupine {
  naslov: string
  vrstice: VrsticaUdelezenca[]
}

export interface SkupinaSeznama {
  /* Brez naslova je to ena tabela z vrstico glave (razvrstitev ni po jakosti). */
  naslov: string | null
  razpon: string
  stolpci: VrsticaUdelezenca[][]
}

export interface PrikazUdelezencev {
  karte: KarticeSkupine[]
  skupine: SkupinaSeznama[]
  prikazanih: number
}

/* Skupina nosilcev po mestu: 0 = 1–2, 1 = 3–4, 2 = 5–8, 3 = 9–16, 4 = od 17. */
function nivoNosilca(mesto: number): number {
  return mesto <= 2 ? 0 : mesto <= 4 ? 1 : mesto <= 8 ? 2 : mesto <= 16 ? 3 : 4
}

/* Naslov skupine; zgornjo mejo skrajša na število udeležencev, en sam se
   imenuje samostalniško (»Nosilec 5«). */
function naslovNivoja(nivo: number, skupaj: number): string {
  switch (nivo) {
    case 0:
      return skupaj >= 2 ? 'Nosilca 1 in 2' : 'Nosilec 1'
    case 1:
      return skupaj >= 4 ? 'Nosilca 3 in 4' : 'Nosilec 3'
    case 2:
      return skupaj > 5 ? `Nosilci 5–${Math.min(8, skupaj)}` : 'Nosilec 5'
    case 3:
      return skupaj > 9 ? `Nosilci 9–${Math.min(16, skupaj)}` : 'Nosilec 9'
    default:
      return 'Nosilci od 17 naprej'
  }
}

/* Seznam na N zaporednih delov (levo prva polovica, desno druga), ne izmenično:
   mesta se berejo navzdol po stolpcu. */
function razdeli<T>(seznam: T[], stolpcev: number): T[][] {
  const naStolpec = Math.ceil(seznam.length / stolpcev) || 1
  const deli: T[][] = []
  for (let i = 0; i < stolpcev; i++) deli.push(seznam.slice(i * naStolpec, (i + 1) * naStolpec))
  return deli.filter((del, indeks) => indeks === 0 || del.length > 0)
}

function razponSkupine(vrstice: VrsticaUdelezenca[], kazeVIgri: boolean): string {
  if (kazeVIgri) return `V igri ${vrstice.filter((v) => v.stanje === 'v_igri').length} / ${vrstice.length}`
  const ratingi = vrstice.map((v) => v.ratingStevilo).filter((r) => r !== null)
  if (ratingi.length === 0) return ''
  return ratingi.length > 1
    ? `rating ${Math.round(Math.max(...ratingi))} – ${Math.round(Math.min(...ratingi))}`
    : `rating ${Math.round(ratingi[0])}`
}

export function sestaviPrikaz(
  vrstice: VrsticaUdelezenca[],
  filtri: FiltriUdelezencev,
  /* V desnem kotu skupine »V igri 2 / 4« namesto razpona ratinga. */
  kazeVIgri: boolean,
): PrikazUdelezencev {
  const iskano = filtri.iskanje.trim().toLowerCase()
  let prikazane = vrstice.filter(
    (v) =>
      (filtri.klub === null || v.klubi.includes(filtri.klub))
      && (!iskano || v.imeCelo.toLowerCase().includes(iskano)),
  )

  const BREZ_MESTA = Number.MAX_SAFE_INTEGER
  if (filtri.razvrstitev === 'uvrstitev') {
    prikazane = [...prikazane].sort(
      (a, b) => (a.koncnoMesto ?? BREZ_MESTA) - (b.koncnoMesto ?? BREZ_MESTA) || a.mesto - b.mesto,
    )
  } else if (filtri.razvrstitev === 'priimek') {
    prikazane = [...prikazane].sort((a, b) => a.imena[0].priimek.localeCompare(b.imena[0].priimek, 'sl'))
  } else if (filtri.razvrstitev === 'klub') {
    prikazane = [...prikazane].sort((a, b) => a.klub.localeCompare(b.klub, 'sl') || a.mesto - b.mesto)
  }

  /* Skupine nosilcev so samo pri razvrstitvi po jakosti; kartice samo, kadar je
     seznam cel (brez kluba in iskanja), sicer bi četverica lagala. */
  const poSkupinah = filtri.razvrstitev === 'jakost'
  const zKarticami = poSkupinah && filtri.klub === null && iskano === ''
  const skupaj = vrstice.length

  const kartice = zKarticami ? prikazane.filter((v) => nivoNosilca(v.mesto) <= 1) : []
  const seznam = poSkupinah ? prikazane.filter((v) => !zKarticami || nivoNosilca(v.mesto) > 1) : prikazane

  const karte = [0, 1]
    .map((nivo) => ({
      naslov: naslovNivoja(nivo, skupaj),
      vrstice: kartice.filter((v) => nivoNosilca(v.mesto) === nivo),
    }))
    .filter((skupina) => skupina.vrstice.length > 0)

  let skupine: SkupinaSeznama[]
  if (poSkupinah) {
    skupine = [0, 1, 2, 3, 4].flatMap((nivo) => {
      const clani = seznam.filter((v) => nivoNosilca(v.mesto) === nivo)
      if (clani.length === 0) return []
      const stolpcev = clani.length >= NAJMANJ_VRSTIC_ZA_DVA_STOLPCA ? 2 : 1
      return [
        {
          naslov: naslovNivoja(nivo, skupaj),
          razpon: razponSkupine(clani, kazeVIgri),
          stolpci: razdeli(clani, stolpcev),
        },
      ]
    })
  } else {
    const stolpcev = seznam.length <= NAJVEC_VRSTIC_V_ENEM_STOLPCU ? 1 : 2
    skupine = seznam.length > 0 ? [{ naslov: null, razpon: '', stolpci: razdeli(seznam, stolpcev) }] : []
  }

  return { karte, skupine, prikazanih: prikazane.length }
}
