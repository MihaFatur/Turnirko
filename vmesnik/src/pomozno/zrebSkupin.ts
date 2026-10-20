/* Listki skupin za tisk: iz mreže dogodka sestavi to, kar gre na papir -
   križno tabelo skupine (vsak z vsakim, nizi, zmage, mesto) in razpored tekem
   po kolih -, nato izračuna, kako visok je listek in kako se listki zložijo na
   liste A4. Brez izrisa, da pravila lista (kdo je prost, kdo je katera
   številka, koliko prostora dobi skupina) živijo na enem mestu.

   Podatki so isti kot na strani dogodka (MrezaDto), zaledje za tisk ne
   potrebuje ničesar svojega. */
import type {
  DogodekDto,
  IzidTekme,
  MrezaDto,
  PrijavaDto,
  SistemTekmovanja,
  SkupinaDto,
  TurnirDto,
} from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA, imePrijave, imeUdelezenca } from '../api/tipi'
import { zaIskanje } from './iskanje'
import { kratekKlub, oblikujObdobje } from './oblikovanje'

/* Vrstica turnirja (ime, datum, kraj) - vse, po čemer list prepoznaš, ko jih
   je v dvorani več. Uvožena tekmovanja imajo datum že v imenu (»1. OT ZA
   U-13, KIDRIČEVO, 19. 9. 2026«), zato se ne ponovi. Skupna listkom skupin in
   listu izločilne mreže. */
export function vrsticaTurnirja(turnir: TurnirDto | undefined): string | null {
  if (!turnir) return null
  const datum = oblikujObdobje(turnir.datumZacetka, turnir.datumKonca)
  return [
    turnir.ime,
    datum && !turnir.ime.includes(datum) ? datum : null,
    [turnir.kraj?.ime, turnir.dvorana].filter(Boolean).join(', '),
  ]
    .filter(Boolean)
    .join(' · ')
}

/* Kategorija na listku: ime dogodka, ki mu spol in starost dodamo le, kadar ju
   ime še ne pove (uvoz ju včasih zapiše kar v ime: »Dečki U-13«). Odprta
   kategorija (»Kdorkoli«) na papirju ne pove ničesar, zato jo izpustimo.
   Skupna sodniškemu listku in listku skupine. */
export function oznakaKategorije(dogodek: DogodekDto): string {
  const primerjava = (besedilo: string) => zaIskanje(besedilo).replace(/[^\p{L}\p{N}]/gu, '')
  const ime = primerjava(dogodek.ime)
  const dodatki = [
    dogodek.spolKategorija === 'KDORKOLI' ? null : OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija],
    dogodek.starostnaKategorija,
  ].filter((dodatek): dodatek is string => !!dodatek && !ime.includes(primerjava(dodatek)))
  return [dogodek.ime, ...dodatki].join(' · ')
}

/* Sistemi, ki imajo skupine; krožni sistem je ena sama lestvica brez skupin. */
const SISTEMI_S_SKUPINAMI: ReadonlySet<SistemTekmovanja> = new Set([
  'SKUPINE_IZLOCILNI',
  'SKUPINE',
  'SKUPINE_ZA_MESTA',
  'SV_REGIJA',
])

/* Ali ima dogodek skupine, ki jih je smiselno natisniti: žreb je narejen in
   tekmovanje teče. Pripravo sodi pod žreb (skupin še ni), zaključen dogodek
   pa v dvorani ni več potreben. Gleda DEJANSKI status, ker so skupine
   izžrebane že pred datumom, ko gledalcu status pove »V pripravi«. */
export function imaZrebanoSkupine(dogodek: DogodekDto): boolean {
  return SISTEMI_S_SKUPINAMI.has(dogodek.sistemTekmovanja) && dogodek.dejanskiStatus === 'V_TEKU'
}

/* Član skupine. Številka je njegova vrstica in stolpec v križni tabeli; z njo
   se v razporedu bere par (»1 – 4«). */
export interface TiskClan {
  idPrijave: number
  stevilka: number
  ime: string
  klub: string | null
}

/* Izid odigrane tekme: nizi (»3 : 1«) in oznaka, kadar ni bila odigrana do
   konca (»b. b.«, »predaja«). */
export interface TiskIzid {
  nizi: string
  oznaka: string | null
}

/* Celica križne tabele z vidika igralca v vrstici: »3/1«, kadar je tekma
   odigrana; sicer prazna za ročni vpis. */
export interface TiskCelica {
  nizi: string
  oznaka: string | null
  zmaga: boolean
}

export interface TiskTekma {
  id: number
  kolo: number
  /* Številki članov v križni tabeli. */
  par: [number, number]
  ime1: string
  ime2: string
  /* Prazen, dokler tekma ni odigrana. */
  izid: TiskIzid | null
  /* Točke po nizih (»11:7«), kadar so vpisane. */
  tocke: string[]
  /* Zmagovalec: 1 ali 2, prazno pri neodigrani. */
  zmagovalec: 1 | 2 | null
  /* Finalna skupina prevzame dvoboj iz predtekmovanja - ne igra se znova. */
  prenesena: boolean
}

export interface TiskKolo {
  kolo: number
  tekme: TiskTekma[]
  /* Številke članov, ki v tem kolu ne igrajo (liho število članov). */
  prosti: number[]
}

/* Končni izkupiček člana; samo ko je skupina odigrana, sicer bi bila
   delna vsota na papirju videti kot končna. */
export interface TiskIzkupicek {
  nizi: string
  zmage: number
  mesto: number | null
}

export interface TiskSkupina {
  id: number
  naslov: string
  clani: TiskClan[]
  /* izidi[i][j]: tekma člana i proti članu j z vidika i; null = ni odigrana
     ali je ni. */
  izidi: (TiskCelica | null)[][]
  izkupicki: (TiskIzkupicek | null)[]
  kola: TiskKolo[]
  /* Največ nizov tekme v skupini (najboljši od N): toliko celic za točke. */
  steviloNizov: number
}

/* Skupine pod skupnim naslovom (predtekmovanje, finalne skupine, nivo). */
export interface TiskSklop {
  kljuc: string
  naslov: string | null
  skupine: TiskSkupina[]
}

/* Kratka oznaka izida, kadar tekma ni bila odigrana do konca; enaka besedila
   kot na kartici tekme. */
export const OZNAKE_IZIDA: Partial<Record<IzidTekme, string>> = {
  BREZ_BOJA: 'b. b.',
  PREDAJA: 'predaja',
  DISKVALIFIKACIJA: 'diskv.',
}

/* Klub člana: pri dvojicah oba, če sta različna; pri ekipi ga ni (ime ekipe
   je že klub). */
function klubClana(prijava: PrijavaDto | undefined, klubLestvice: string | null, ekipno: boolean) {
  if (ekipno) return null
  const kluba = [prijava?.klub ?? klubLestvice, prijava?.klub2]
    .filter((k): k is string => !!k)
    .map(kratekKlub)
  const brezPodvojitev = kluba.filter((k, i) => kluba.indexOf(k) === i)
  return brezPodvojitev.length > 0 ? brezPodvojitev.join(' / ') : null
}

function pripraviSkupino(mreza: MrezaDto, skupina: SkupinaDto): TiskSkupina {
  const ekipno = mreza.dogodek.disciplina === 'EKIPNO'
  const prijavePoId = new Map(mreza.prijave.map((p) => [p.id, p]))

  /* Člani predtekmovalne skupine so našteti po jakosti (nosilec skupine
     prvi), kakor jih je žreb razporedil; lestvica sama bi jih med tekmovanjem
     premetavala po izidih in številke v razporedu bi ob ponovnem tisku
     pomenile druge ljudi. Skupine za mesta (višja stopnja) nimajo žrebanih
     članov, zato ostane vrstni red lestvice. */
  const vrstice = skupina.lestvica.map((vrstica, indeks) => ({
    vrstica,
    indeks,
    prijava: prijavePoId.get(vrstica.idPrijave),
  }))
  if (skupina.stopnja === 1) {
    const nosilec = (p: PrijavaDto | undefined) => p?.stNosilca ?? Number.MAX_SAFE_INTEGER
    vrstice.sort((a, b) => nosilec(a.prijava) - nosilec(b.prijava) || a.indeks - b.indeks)
  }
  const clani: TiskClan[] = vrstice.map(({ vrstica, prijava }, indeks) => ({
    idPrijave: vrstica.idPrijave,
    stevilka: indeks + 1,
    ime: prijava ? imePrijave(prijava) : vrstica.polnoIme,
    klub: klubClana(prijava, vrstica.klub, ekipno),
  }))
  const stevilkaPrijave = new Map(clani.map((c) => [c.idPrijave, c.stevilka]))

  const tekme = mreza.tekme.filter(
    (t) =>
      t.faza === 'SKUPINA' &&
      t.idSkupina === skupina.id &&
      t.udelezenec1 !== null &&
      t.udelezenec2 !== null &&
      stevilkaPrijave.has(t.udelezenec1.idPrijave) &&
      stevilkaPrijave.has(t.udelezenec2.idPrijave),
  )

  const izidi: (TiskCelica | null)[][] = clani.map(() => clani.map(() => null))
  for (const t of tekme) {
    if (t.status !== 'KONCANA') continue
    const i = stevilkaPrijave.get(t.udelezenec1!.idPrijave)! - 1
    const j = stevilkaPrijave.get(t.udelezenec2!.idPrijave)! - 1
    const oznaka = (t.izidTip && OZNAKE_IZIDA[t.izidTip]) || null
    const zmagal1 = t.idZmagovalcaPrijave === t.udelezenec1!.idPrijave
    izidi[i][j] = { nizi: `${t.dobljeniNizi1}/${t.dobljeniNizi2}`, oznaka, zmaga: zmagal1 }
    izidi[j][i] = { nizi: `${t.dobljeniNizi2}/${t.dobljeniNizi1}`, oznaka, zmaga: !zmagal1 }
  }

  /* Nizi, zmage in mesto se izpišejo šele, ko je odigrana vsa skupina - delni
     seštevek bi se na papirju bral kot končni. */
  const koncana = tekme.length > 0 && tekme.every((t) => t.status === 'KONCANA')
  const lestvicaPoPrijavi = new Map(skupina.lestvica.map((v) => [v.idPrijave, v]))
  const izkupicki = clani.map((clan): TiskIzkupicek | null => {
    const v = lestvicaPoPrijavi.get(clan.idPrijave)
    if (!koncana || !v) return null
    return { nizi: `${v.niziZa}/${v.niziProti}`, zmage: v.zmage, mesto: v.mesto }
  })

  const kola = [...new Set(tekme.map((t) => t.kolo))]
    .sort((a, b) => a - b)
    .map((kolo): TiskKolo => {
      const vKolu = tekme.filter((t) => t.kolo === kolo).sort((a, b) => a.pozicija - b.pozicija)
      const igra = new Set(
        vKolu.flatMap((t) => [t.udelezenec1!.idPrijave, t.udelezenec2!.idPrijave]),
      )
      return {
        kolo,
        tekme: vKolu.map((t) => ({
          id: t.id,
          kolo,
          par: [
            stevilkaPrijave.get(t.udelezenec1!.idPrijave)!,
            stevilkaPrijave.get(t.udelezenec2!.idPrijave)!,
          ],
          ime1: imeUdelezenca(t.udelezenec1) ?? '',
          ime2: imeUdelezenca(t.udelezenec2) ?? '',
          izid:
            t.status === 'KONCANA'
              ? {
                  nizi: `${t.dobljeniNizi1}/${t.dobljeniNizi2}`,
                  oznaka: (t.izidTip && OZNAKE_IZIDA[t.izidTip]) || null,
                }
              : null,
          tocke: t.status === 'KONCANA' ? t.nizi.map((n) => `${n.tocke1}:${n.tocke2}`) : [],
          zmagovalec:
            t.status !== 'KONCANA' || t.idZmagovalcaPrijave === null
              ? null
              : t.idZmagovalcaPrijave === t.udelezenec1!.idPrijave
                ? 1
                : 2,
          prenesena: t.idPrenesena !== null,
        })),
        prosti: clani.filter((c) => !igra.has(c.idPrijave)).map((c) => c.stevilka),
      }
    })

  return {
    id: skupina.id,
    naslov: skupina.ime ?? `Skupina ${skupina.oznaka}`,
    clani,
    izidi,
    izkupicki,
    kola,
    /* Ekipna tekma nima nizov, ampak posamične tekme srečanja: v razporedu
       ostane samo izid. */
    steviloNizov: ekipno
      ? 0
      : Math.max(mreza.dogodek.privzetoSteviloNizov, ...tekme.map((t) => t.steviloNizov)),
  }
}

const poOznaki = (a: SkupinaDto, b: SkupinaDto) =>
  a.oznaka.localeCompare(b.oznaka, 'sl', { numeric: true })

/* Skupine dogodka v sklopih, kot jih razvrsti stran dogodka: predtekmovanje,
   nato skupine, ki odločajo mesta; SV regija po nivojih. Naslov sklopa je
   samo tam, kjer jih je več (en sam sklop ne potrebuje naslova). */
export function sklopiZrebSkupin(mreza: MrezaDto): TiskSklop[] {
  const skupine = mreza.skupine
  if (skupine.length === 0) return []

  if (mreza.dogodek.sistemTekmovanja === 'SV_REGIJA') {
    const nivoji = [...new Set(skupine.map((s) => s.nivo))].sort((a, b) => a - b)
    return nivoji.map((nivo) => ({
      kljuc: `nivo-${nivo}`,
      naslov: nivoji.length > 1 ? `Nivo ${nivo}` : null,
      skupine: skupine
        .filter((s) => s.nivo === nivo)
        .sort(poOznaki)
        .map((s) => pripraviSkupino(mreza, s)),
    }))
  }

  /* Skupine za mesta gredo pod en naslov ne glede na stopnjo (vir ima vsako
     za svojo fazo, igrajo pa se vzporedno) - enako kot na strani dogodka. */
  const zaMesta = skupine
    .filter((s) => s.stopnja > 1 && s.prvoMesto !== null)
    .sort((a, b) => (a.prvoMesto ?? 0) - (b.prvoMesto ?? 0))
  const ostale = skupine.filter((s) => !zaMesta.includes(s))
  const sklopi = [...new Set(ostale.map((s) => s.stopnja))]
    .sort((a, b) => a - b)
    .map((stopnja) => ({
      kljuc: String(stopnja),
      naslov: stopnja === 1 ? 'Predtekmovanje' : `${stopnja}. stopnja`,
      skupine: ostale.filter((s) => s.stopnja === stopnja).sort(poOznaki),
    }))
  if (zaMesta.length > 0) {
    sklopi.push({ kljuc: 'mesta', naslov: 'Finalne skupine', skupine: zaMesta })
  } else if (sklopi.length > 1) {
    sklopi[sklopi.length - 1].naslov = 'Finalne skupine'
  }

  return sklopi.map((sklop) => ({
    kljuc: sklop.kljuc,
    naslov: sklopi.length > 1 ? sklop.naslov : null,
    skupine: sklop.skupine.map((s) => pripraviSkupino(mreza, s)),
  }))
}

export function steviloSkupinVSklopih(sklopi: TiskSklop[]): number {
  return sklopi.reduce((vsota, sklop) => vsota + sklop.skupine.length, 0)
}

/* ---------- Mere listka in zlaganje na A4 ----------

   List A4 (210 × 297 mm, brez robov) se reže samo VODORAVNO, zato je listek
   vedno širok kot list, visok pa šestino lista ali več: tretjina, polovica,
   dve tretjini ali cel list. Tako sta vsak list in vsak rez enaka, ne glede
   na to, kako veliko skupino listek nosi.

   Višina se izračuna in ne izmeri: vse vrstice imajo stalno višino v mm
   (besedilo se ne lomi), zato je višina listka vsota vrstic. Listek, ki mu
   ostane prostor, vrstice raztegne (do MOST_RAZTEGA), da zapolni svoj del
   lista; pri skupini, ki ne gre na cel list, se razpored nadaljuje na
   naslednjem listku. Iste številke nosi slog (razdelek 16 v slog.css) prek
   spremenljivk, ki jih postavi izris. */

/* Kaj listek nosi pod križno tabelo: razpored z okenci za točke po nizih
   (skupina igra sama za mizo in izide piše na listek) ali samo razpored parov
   (izide pišejo sodniki na svoje listke, kot pri TOP). */
export type NacinListka = 'nizi' | 'razpored'

export const VISINA_LISTA_MM = 297
const SESTINA_MM = VISINA_LISTA_MM / 6

/* Mere v mm pri raztezku 1; izris jih postavi v spremenljivke sloga
   (.listek-skupine), zato drugih številk slog nima. Rob 5 mm zgoraj in spodaj
   je rob tiskalnika na prvem in zadnjem listku lista. */
export const MERE = {
  robZgoraj: 5,
  robSpodaj: 5,
  /* Glava listka (kategorija, turnir, znamka) z razmikom do tabele. */
  glava: 7,
  /* Križna tabela: glava, vrstica člana, zunanja obroba (2 × 0,5 mm). */
  krizGlava: 8.5,
  krizVrstica: 6.6,
  krizObroba: 1,
  /* Razmik med križno tabelo in razporedom. */
  razmik: 3,
  /* Razpored z nizi: glava stolpcev in vrstica tekme. */
  seznamGlava: 4.6,
  seznamVrstica: 5.3,
  /* Razpored parov: glava kola in vrstica para. */
  razporedGlava: 4.4,
  razporedVrstica: 4.2,
  /* Glava nadaljevanja (»nadaljevanje razporeda«). */
  nadaljevanje: 5,
} as const

/* Vrstice se raztegnejo največ za toliko; več bi bilo pri skupini treh igralcev
   na tretjini lista videti prazno, ne pa bolj berljivo. */
const MOST_RAZTEGA = 1.35
/* In stisnejo največ za toliko, da gre skupina na manjši del lista (skupina
   osmih na tretjino lista, kot pri TOP): vrstica tabele ima tedaj 5,9 mm, kar
   za »3/1« z roko še zadošča. */
const NAJMANJ_RAZTEGA = 0.9

/* Razpored parov ima največ toliko kol v enem pasu; pri večji skupini se kola
   razporedijo v dva pasova. */
export const KOL_V_PASU = 11

export interface DelListka {
  /* Križna tabela je na prvem delu; nadaljevanje nosi samo razpored. */
  kriznaTabela: boolean
  /* Kola razporeda na tem delu. */
  kola: TiskKolo[]
  /* Višina dela lista v šestinah (2, 3, 4 ali 6). */
  sestin: number
  /* Raztezek vrstic (1 = najmanjše mere). */
  raztezek: number
  /* Zaporedna številka dela in vseh delov skupine (nadaljevanje). */
  del: number
  delov: number
}

function visinaKrizne(clanov: number, raztezek = 1): number {
  return MERE.krizGlava + clanov * MERE.krizVrstica * raztezek + MERE.krizObroba
}

/* Vrstic para v enem kolu razporeda parov: tekme in, pri lihem številu,
   vrstica prostega. */
export function vrsticKola(kolo: TiskKolo): number {
  return kolo.tekme.length + (kolo.prosti.length > 0 ? 1 : 0)
}

/* Višina razporeda brez raztezka, v mm. */
function visinaRazporeda(kola: TiskKolo[], nacin: NacinListka): number {
  if (kola.length === 0) return 0
  if (nacin === 'nizi') {
    const tekem = kola.reduce((vsota, kolo) => vsota + kolo.tekme.length, 0)
    return MERE.seznamGlava + tekem * MERE.seznamVrstica
  }
  let visina = 0
  for (let i = 0; i < kola.length; i += KOL_V_PASU) {
    const pas = kola.slice(i, i + KOL_V_PASU)
    visina += MERE.razporedGlava + Math.max(...pas.map(vrsticKola)) * MERE.razporedVrstica
  }
  return visina
}

/* Del vrstic, ki se raztegne (vrstice tabele in razporeda). */
function raztegljivo(clanov: number, kola: TiskKolo[], nacin: NacinListka, kriznaTabela: boolean) {
  const krizne = kriznaTabela ? clanov * MERE.krizVrstica : 0
  const razpored = visinaRazporeda(kola, nacin)
  const glaveRazporeda =
    kola.length === 0
      ? 0
      : nacin === 'nizi'
        ? MERE.seznamGlava
        : Math.ceil(kola.length / KOL_V_PASU) * MERE.razporedGlava
  return krizne + razpored - glaveRazporeda
}

/* Višina dela pri raztezku 1, v mm. */
function visinaDela(clanov: number, kola: TiskKolo[], nacin: NacinListka, kriznaTabela: boolean) {
  const okvir = MERE.robZgoraj + MERE.robSpodaj + MERE.glava
  const tabela = kriznaTabela ? visinaKrizne(clanov) : MERE.nadaljevanje
  const razpored = visinaRazporeda(kola, nacin)
  const razmik = kriznaTabela && kola.length > 0 ? MERE.razmik : 0
  return okvir + tabela + razmik + razpored
}

/* Mere listka: polovica in več so 3, 4 ali 6 šestin - 5 šestin ni lepa mera za
   rez (ostanek ene šestine bi bil odpadek sredi lista). */
const VELIKOSTI_V_SESTINAH = [2, 3, 4, 6]

function del(
  clanov: number,
  kola: TiskKolo[],
  nacin: NacinListka,
  kriznaTabela: boolean,
): Omit<DelListka, 'del' | 'delov'> {
  const vrstice = raztegljivo(clanov, kola, nacin, kriznaTabela)
  const stalno = visinaDela(clanov, kola, nacin, kriznaTabela) - vrstice
  /* Najmanjši del lista, na katerega gre listek s stisnjenimi vrsticami. */
  for (const sestin of VELIKOSTI_V_SESTINAH) {
    const zaVrstice = sestin * SESTINA_MM - stalno
    if (vrstice === 0 ? zaVrstice >= 0 : zaVrstice / vrstice >= NAJMANJ_RAZTEGA) {
      const raztezek = vrstice > 0 ? Math.min(MOST_RAZTEGA, zaVrstice / vrstice) : 1
      return { kriznaTabela, kola, sestin, raztezek }
    }
  }
  return { kriznaTabela, kola, sestin: 6, raztezek: NAJMANJ_RAZTEGA }
}

/* Ali gre del na cel list (s stisnjenimi vrsticami). */
function greNaList(clanov: number, kola: TiskKolo[], nacin: NacinListka, kriznaTabela: boolean) {
  const vrstice = raztegljivo(clanov, kola, nacin, kriznaTabela)
  const stalno = visinaDela(clanov, kola, nacin, kriznaTabela) - vrstice
  return stalno + vrstice * NAJMANJ_RAZTEGA <= VISINA_LISTA_MM
}

/* Deli listka ene skupine: navadno en sam; pri skupini, katere razpored ne gre
   na cel list, se razpored nadaljuje na naslednjih listkih. Kolo se ne
   razdeli na dva lista. */
export function deliListka(skupina: TiskSkupina, nacin: NacinListka): DelListka[] {
  const clanov = skupina.clani.length
  const deli: Omit<DelListka, 'del' | 'delov'>[] = []
  let preostala = skupina.kola
  let kriznaTabela = true
  while (deli.length === 0 || preostala.length > 0) {
    let koliko = preostala.length
    while (koliko > 1 && !greNaList(clanov, preostala.slice(0, koliko), nacin, kriznaTabela)) {
      koliko--
    }
    deli.push(del(clanov, preostala.slice(0, koliko), nacin, kriznaTabela))
    preostala = preostala.slice(koliko)
    kriznaTabela = false
  }
  return deli.map((d, i) => ({ ...d, del: i + 1, delov: deli.length }))
}

/* Privzeti način: manjša skupina igra sama za mizo in izide piše na listek;
   pri sedmih in več (TOP) ima vsaka tekma svojega sodnika in listek nosi samo
   razpored - razpored z nizi bi zasedel cel list. */
export function privzetNacin(skupine: TiskSkupina[]): NacinListka {
  return skupine.every((s) => s.clani.length <= 6) ? 'nizi' : 'razpored'
}

/* En listek, kot gre na papir: skupina (ali nadaljevanje njenega razporeda) z
   vsem, kar ga naredi berljivega, ko je odrezan od lista - kategorija, turnir
   in sklop (predtekmovanje, finalne skupine, nivo). */
export interface ListekSkupine {
  kljuc: string
  kategorija: string
  turnir: string | null
  sklop: string | null
  skupina: TiskSkupina
  del: DelListka
  nacin: NacinListka
  ekipno: boolean
  sestin: number
}

/* Listki vseh skupin podanih dogodkov po vrsti (kategorija za kategorijo,
   skupina za skupino). Brez izbranega načina (null = samodejno) ima vsaka
   kategorija svoj privzeti način: na istem turnirju igrajo mlajši v skupinah po
   štiri brez sodnikov, TOP pa v skupini osmih s sodniki. Znotraj kategorije
   imajo vsi listki isto obliko. */
export function listkiSkupin(
  mreze: MrezaDto[],
  turnir: TurnirDto | undefined,
  izbranNacin: NacinListka | null,
): ListekSkupine[] {
  const vrstica = vrsticaTurnirja(turnir)
  return mreze.flatMap((mreza) => {
    const sklopi = sklopiZrebSkupin(mreza)
    const nacin = izbranNacin ?? privzetNacin(sklopi.flatMap((sklop) => sklop.skupine))
    return sklopi.flatMap((sklop) =>
      sklop.skupine.flatMap((skupina) =>
        deliListka(skupina, nacin).map(
          (del): ListekSkupine => ({
            kljuc: `${skupina.id}-${del.del}`,
            kategorija: oznakaKategorije(mreza.dogodek),
            turnir: vrstica,
            sklop: sklop.naslov,
            skupina,
            del,
            nacin,
            ekipno: mreza.dogodek.disciplina === 'EKIPNO',
            sestin: del.sestin,
          }),
        ),
      ),
    )
  })
}

/* Listki zloženi po listih A4: po vrsti, vsak list zapolnjen od vrha, nov list,
   ko naslednji listek ne gre več na tekočega. Skupina gre na list cela (ali z
   nadaljevanjem na naslednjem), nikoli razrezana čez rob. */
export function zloziNaListe<T extends { sestin: number }>(listki: T[]): T[][] {
  const listi: T[][] = []
  let zasedeno = 6
  for (const listek of listki) {
    if (zasedeno + listek.sestin > 6) {
      listi.push([])
      zasedeno = 0
    }
    listi[listi.length - 1].push(listek)
    zasedeno += listek.sestin
  }
  return listi
}
