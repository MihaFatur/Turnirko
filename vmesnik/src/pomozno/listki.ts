/* Priprava listkov (zapisnikov) tekem za tisk: katere tekme pridejo v
   poštev, kako se razvrstijo, v kakšne sklope se združijo za hitro izbiro in
   katere so že natisnjene.

   Ločeno od izrisa, da je logika testljiva. Isto uporabljata posamični listki
   (ListkiStran) in ekipni zapisniki (ZapisnikiEkipnegaDogodka). */
import type { FazaTekme, MrezaDto, TekmaDto } from '../api/tipi'
import { imeKola } from './oblikovanje'
import { primerjajSkupine } from './oznakaSkupine'
import { imeTekmeZreba } from './svRegija'

/* Skupine se natisnejo prve (predtekmovanje), nato glavna mreža, nato
   tolažilne tekme (npr. za 3. mesto). */
const RANG_FAZE: Record<FazaTekme, number> = { SKUPINA: 0, GLAVNI: 1, TOLAZILNI: 2 }

/* Vrstni red listkov skupinskega dela na listih (lastnik 23. 11. 2026): po
   skupinah (vsa kola skupine 1, nato vsa kola skupine 2 …) ali po kolih (1. kolo
   skupine 1, 2, 3 …, nato 2. kolo …). Po kolih so tudi sklopi za izbiro kola, da
   organizator z enim klikom natisne npr. 1. kolo vseh skupin. Izločilni del in
   žrebi SV regije so v obeh enaki. */
export type RazvrstitevListkov = 'skupine' | 'kola'

/* Kategorija listka na skupnem tisku vseh kategorij (skupne mize, V78; SkupniListkiStran):
   tabela ima vrstice po kategoriji in skupini, sled tiska gre na dogodek. red = mesto
   kategorije v vrstnem redu kola. */
export interface KategorijaListka {
  idDogodka: number
  naslov: string
  red: number
}

export interface PripravljenListek {
  tekma: TekmaDto
  /* Oznaka skupine (»1«, starejši »A«); prazna pri izločilni mreži. */
  skupina: string | null
  /* Kolo skupine ali krog mreže. */
  faza: string
  /* Opis v seznamu izbire pod imeni: kolo, kadar so sklopi skupine, in
     skupina, kadar so sklopi kola (naslov sklopa že pove drugo). */
  opis: string
  /* Sklop za hitro izbiro (»Skupina 1«, »1. kolo«, »Tolažilni žreb«) in njegov ključ. */
  sklop: string
  sklopNaslov: string
  /* Samo na skupnem tisku vseh kategorij. */
  kategorija?: KategorijaListka
}

/* Tekme, ki jih je smiselno natisniti skupaj: skupina, žreb ali kolo. */
export interface SklopListkov {
  kljuc: string
  naslov: string
  listki: PripravljenListek[]
}

/* Tekma, za katero ima smisel natisniti listek: znana sta oba udeleženca in še
   ni odigrana. Prosti prehodi in še nedoločene tekme odpadejo sami. */
export function jePripravljena(t: TekmaDto): boolean {
  return (
    (t.status === 'PRIPRAVLJENA' || t.status === 'V_IGRI') &&
    t.udelezenec1 !== null &&
    t.udelezenec2 !== null
  )
}

/* Ali je organizator že potrdil tisk listka te tekme. */
export function jeNatisnjen(l: PripravljenListek): boolean {
  return !!l.tekma.listekNatisnjenOb
}

export function nenatisnjeni(listki: PripravljenListek[]): PripravljenListek[] {
  return listki.filter((l) => !jeNatisnjen(l))
}

/* Pripravi in razvrsti pripravljene tekme, vsaki doda človeku berljivo
   oznako skupine, kola/faze in sklopa. */
export function pripraviListke(
  podatki: MrezaDto | undefined,
  razvrstitev: RazvrstitevListkov = 'skupine',
): PripravljenListek[] {
  if (!podatki) return []

  const glavne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  const zadnjeKolo = glavne.length > 0 ? Math.max(...glavne.map((t) => t.kolo)) : 1
  const poKolih = razvrstitev === 'kola'

  /* Skupine po vrsti oznak (9 pred 10), ne po id-ju: ročni žreb jih lahko zapiše
     v drugem vrstnem redu. Neznana skupina gre za znanimi. */
  const rangi = new Map(
    [...podatki.skupine].sort(primerjajSkupine).map((s, i) => [s.id, i] as const),
  )
  const rangSkupine = (t: TekmaDto) =>
    t.idSkupina === null ? -1 : (rangi.get(t.idSkupina) ?? rangi.size + t.idSkupina)

  return podatki.tekme
    .filter(jePripravljena)
    .sort(
      (a, b) =>
        RANG_FAZE[a.faza] - RANG_FAZE[b.faza] ||
        (poKolih && a.faza === 'SKUPINA' ? a.kolo - b.kolo : 0) ||
        rangSkupine(a) - rangSkupine(b) ||
        (a.idZreb ?? 0) - (b.idZreb ?? 0) ||
        a.kolo - b.kolo ||
        a.pozicija - b.pozicija,
    )
    .map((tekma) => {
      const skupina =
        tekma.faza === 'SKUPINA'
          ? (podatki.skupine.find((s) => s.id === tekma.idSkupina)?.oznaka ?? null)
          : null
      const sklop = sklopTekme(tekma, skupina, podatki, poKolih)
      const faza = oznakaFaze(tekma, zadnjeKolo, podatki)
      return {
        tekma,
        skupina,
        faza,
        opis: poKolih && skupina !== null ? `Skupina ${skupina}` : faza,
        sklop: sklop.kljuc,
        sklopNaslov: sklop.naslov,
      }
    })
}

function oznakaFaze(tekma: TekmaDto, zadnjeKolo: number, podatki: MrezaDto): string {
  if (tekma.faza === 'SKUPINA') return `${tekma.kolo}. kolo`
  /* SV regija: tekma žreba nosi razpon mest, ki ga odloča (»Skupina 2 · Za 21. mesto«). */
  if (tekma.idZreb !== null && tekma.razponOd !== null && tekma.razponDo !== null) {
    for (const nivo of podatki.svRegija?.nivoji ?? []) {
      const zreb = nivo.zrebi.find((z) => z.id === tekma.idZreb)
      if (zreb) {
        return `Skupina ${nivo.nivo} · ${imeTekmeZreba(
          tekma.razponOd, tekma.razponDo, zreb.prvoMesto, zreb.indeks === 0,
        )}`
      }
    }
  }
  if (tekma.faza === 'TOLAZILNI') return 'Za 3. mesto'
  return imeKola(tekma.kolo, zadnjeKolo)
}

/* Sklop, v katerem je tekma: skupina (»Skupina 1«), žreb SV regije (»Skupina 2 ·
   Tolažilni žreb«), glavni ali tolažilni del mreže; krožni sistem nima skupin,
   zato se tam sklopi ujemajo s koli (»3. kolo«), enako kot skupinski del, ko so
   listki razvrščeni po kolih. */
function sklopTekme(
  tekma: TekmaDto,
  skupina: string | null,
  podatki: MrezaDto,
  poKolih: boolean,
): { kljuc: string; naslov: string } {
  if (tekma.faza === 'SKUPINA') {
    if (skupina === null || poKolih) return { kljuc: `kolo-${tekma.kolo}`, naslov: `${tekma.kolo}. kolo` }
    const ime = podatki.skupine.find((s) => s.id === tekma.idSkupina)?.ime
    return {
      kljuc: `skupina-${tekma.idSkupina}`,
      naslov: ime ? `Skupina ${skupina} · ${ime}` : `Skupina ${skupina}`,
    }
  }
  if (tekma.idZreb !== null) {
    for (const nivo of podatki.svRegija?.nivoji ?? []) {
      const zreb = nivo.zrebi.find((z) => z.id === tekma.idZreb)
      if (zreb) {
        return { kljuc: `zreb-${tekma.idZreb}`, naslov: `Skupina ${nivo.nivo} · ${zreb.ime}` }
      }
    }
  }
  if (tekma.faza === 'TOLAZILNI') return { kljuc: 'tolazilni', naslov: 'Tekma za 3. mesto' }
  return { kljuc: 'glavni', naslov: 'Glavni žreb' }
}

/* Ali ima izbira vrstnega reda smisel: vsaj dve skupini s pripravljenimi tekmami
   (z eno samo skupino ali brez skupin sta oba vrstna reda enaka). */
export function imaVecSkupin(listki: PripravljenListek[]): boolean {
  return new Set(listki.filter((l) => l.skupina !== null).map((l) => l.tekma.idSkupina)).size > 1
}

const KLJUC_RAZVRSTITVE = 'turnirko-razvrstitev-listkov'

/* Zadnja izbira vrstnega reda ostane v brskalniku (organizator tiska ob vsakem
   kolu); brez nje ali brez shrambe po skupinah, kot doslej. */
export function preberiRazvrstitev(): RazvrstitevListkov {
  try {
    return window.localStorage.getItem(KLJUC_RAZVRSTITVE) === 'kola' ? 'kola' : 'skupine'
  } catch {
    return 'skupine'
  }
}

export function shraniRazvrstitev(r: RazvrstitevListkov): void {
  try {
    window.localStorage.setItem(KLJUC_RAZVRSTITVE, r)
  } catch {
    /* izbira velja za to stran tudi brez shrambe */
  }
}

/* Listke združi v sklope po vrstnem redu prve pojavitve (listki so že
   razvrščeni po fazi, skupini oz. kolu in žrebu). */
export function sklopiListkov(listki: PripravljenListek[]): SklopListkov[] {
  const poKljucu = new Map<string, SklopListkov>()
  for (const l of listki) {
    let sklop = poKljucu.get(l.sklop)
    if (!sklop) {
      sklop = { kljuc: l.sklop, naslov: l.sklopNaslov, listki: [] }
      poKljucu.set(l.sklop, sklop)
    }
    sklop.listki.push(l)
  }
  return [...poKljucu.values()]
}

/* ---------- Tabela listkov (prenova »Listki 1b«, 23. 11. 2026) ----------
   Organizator vidi skupine v vrsticah in kola v stolpcih: vsak listek je ploščica na
   preseku. Izločilni del, tolažilni žrebi in žrebi SV regije (tekme brez skupine) so
   vrstice pod tabelo, vsaka s svojo oznako faze. */

/* Do toliko kol skupinskega dela stoji v stolpcih; pri več (krožni sistem z deset kol)
   so ploščice preglednejše v vrsticah po kolih. */
export const NAJVEC_KOL_V_TABELI = 7

export interface VrstaTabele {
  kljuc: string
  /* Ime kategorije nad oznako skupine (samo na skupnem tisku). */
  kategorija?: string
  /* »Sk. 3«; na telefonu »Skupina 3«. */
  naslov: string
  naslovDolgo: string
  /* Listki po kolih tabele (enak vrstni red kot `kola`); prazna celica, kjer skupina v kolu
     nima pripravljene tekme. */
  celice: PripravljenListek[][]
}

export interface OstaloVTabeli {
  kljuc: string
  kategorija?: string
  naslov: string
  listki: PripravljenListek[]
}

export interface TabelaListkov {
  /* Kola v stolpcih; prazno, kadar skupinskega dela ni v tabeli. */
  kola: number[]
  vrste: VrstaTabele[]
  ostali: OstaloVTabeli[]
}

/* Kar hitra izbira »Na vrsti« obravnava kot eno celoto: kolo skupinskega dela ali ena vrstica
   izločilnega dela. Pri izbiri so enote po vrsti tiska. */
export interface EnotaListkov {
  kljuc: string
  naslov: string
  listki: PripravljenListek[]
}

/* Oznaka vrstice izločilnega dela: »Mreža · 1/8 finala«, »Tolažilni«; žreb SV regije že nosi
   »Skupina 2 · Za 21. mesto«, krožni sistem »3. kolo«. */
function oznakaVrstice(l: PripravljenListek): { kljuc: string; naslov: string } {
  if (l.tekma.faza === 'SKUPINA') return { kljuc: `kolo-${l.tekma.kolo}`, naslov: `${l.tekma.kolo}. kolo` }
  if (l.sklop === 'glavni') return { kljuc: `glavni|${l.faza}`, naslov: `Mreža · ${l.faza}` }
  if (l.sklop === 'tolazilni') return { kljuc: 'tolazilni', naslov: 'Tolažilni' }
  return { kljuc: `${l.sklop}|${l.faza}`, naslov: l.faza }
}

/* Listke (že razvrščene za tisk) razporedi v tabelo. */
export function tabelaListkov(listki: PripravljenListek[]): TabelaListkov {
  const skupinski = listki.filter((l) => l.tekma.faza === 'SKUPINA')
  const kola = [...new Set(skupinski.map((l) => l.tekma.kolo))].sort((a, b) => a - b)
  const vTabeli =
    skupinski.length > 0 &&
    skupinski.every((l) => l.skupina !== null) &&
    kola.length <= NAJVEC_KOL_V_TABELI

  /* Na skupnem tisku je vrstica kategorija in skupina (»Sk. 1« obstaja v vsaki kategoriji),
     po vrstnem redu kategorij; sicer samo skupina. */
  const vrste: VrstaTabele[] = []
  if (vTabeli) {
    const kategorije = new Map<number, KategorijaListka | undefined>()
    for (const l of skupinski) kategorije.set(l.kategorija?.idDogodka ?? 0, l.kategorija)
    const poRedu = [...kategorije.entries()].sort(([, a], [, b]) => (a?.red ?? 0) - (b?.red ?? 0))
    for (const [idKat, kat] of poRedu) {
      const vKategoriji = skupinski.filter((l) => (l.kategorija?.idDogodka ?? 0) === idKat)
      const oznake = [...new Set(vKategoriji.map((l) => l.skupina as string))].sort((a, b) =>
        primerjajSkupine({ oznaka: a }, { oznaka: b }),
      )
      for (const oznaka of oznake) {
        vrste.push({
          kljuc: kat ? `${idKat}|skupina-${oznaka}` : `skupina-${oznaka}`,
          kategorija: kat?.naslov,
          naslov: `Sk. ${oznaka}`,
          naslovDolgo: kat ? `${kat.naslov} · Skupina ${oznaka}` : `Skupina ${oznaka}`,
          celice: kola.map((k) =>
            vKategoriji.filter((l) => l.skupina === oznaka && l.tekma.kolo === k),
          ),
        })
      }
    }
  }

  const ostali: OstaloVTabeli[] = []
  const poKljucu = new Map<string, OstaloVTabeli>()
  for (const l of listki) {
    if (vTabeli && l.tekma.faza === 'SKUPINA') continue
    const oznaka = oznakaVrstice(l)
    const kljuc = l.kategorija ? `${l.kategorija.idDogodka}|${oznaka.kljuc}` : oznaka.kljuc
    let vrsta = poKljucu.get(kljuc)
    if (!vrsta) {
      vrsta = { kljuc, naslov: oznaka.naslov, kategorija: l.kategorija?.naslov, listki: [] }
      poKljucu.set(kljuc, vrsta)
      ostali.push(vrsta)
    }
    vrsta.listki.push(l)
  }
  return { kola: vTabeli ? kola : [], vrste, ostali }
}

/* Enote hitre izbire po vrsti tiska: najprej kola skupinskega dela, nato vrstice pod tabelo. */
export function enoteListkov(tabela: TabelaListkov): EnotaListkov[] {
  const enote: EnotaListkov[] = tabela.kola.map((k, i) => ({
    kljuc: `kolo-${k}`,
    naslov: `${k}. kolo`,
    listki: tabela.vrste.flatMap((v) => v.celice[i]),
  }))
  for (const o of tabela.ostali) {
    enote.push({ kljuc: o.kljuc, naslov: o.kategorija ? `${o.kategorija} · ${o.naslov}` : o.naslov, listki: o.listki })
  }
  return enote
}

/* Kolo po vrsti za skupni tisk (isto pravilo kot predlog tekem na strani Mize): skupinski
   del po kolih skupin, izločilni del kot naslednja kola za zadnjim kolom skupin kategorije. */
export function koloPoVrsti(t: TekmaDto, zadnjeKoloSkupin: number): number {
  return t.faza === 'SKUPINA' ? t.kolo : zadnjeKoloSkupin + t.kolo
}

/* Listki vseh kategorij za en tisk (skupne mize, V78): po kolih = 1. kolo vseh kategorij v
   vrstnem redu kategorij, nato 2. kolo …; po skupinah = kategorija za kategorijo, v vsaki
   kot na njeni strani listkov. */
export function listkiVsehKategorij(
  kategorije: { idDogodka: number; naslov: string; mreza: MrezaDto }[],
  razvrstitev: RazvrstitevListkov,
): PripravljenListek[] {
  const vsi: { l: PripravljenListek; kolo: number }[] = []
  kategorije.forEach((k, red) => {
    const skupinska = k.mreza.tekme.filter((t) => t.faza === 'SKUPINA').map((t) => t.kolo)
    const zadnje = skupinska.length > 0 ? Math.max(...skupinska) : 0
    for (const l of pripraviListke(k.mreza, razvrstitev)) {
      vsi.push({
        l: { ...l, kategorija: { idDogodka: k.idDogodka, naslov: k.naslov, red } },
        kolo: koloPoVrsti(l.tekma, zadnje),
      })
    }
  })
  if (razvrstitev === 'kola') {
    // stabilno: v istem kolu kategorije po vrsti, v kategoriji kot na njeni strani
    vsi.sort((a, b) => a.kolo - b.kolo || (a.l.kategorija!.red - b.l.kategorija!.red))
  }
  return vsi.map((x) => x.l)
}

/* Prva enota, v kateri je še kaj nenatisnjenega: kar organizator natisne naslednje. */
export function enotaNaVrsti(enote: EnotaListkov[]): EnotaListkov | null {
  return enote.find((e) => e.listki.some((l) => !jeNatisnjen(l))) ?? null
}

/* Privzeta izbira: nenatisnjeni listki enote, ki je na vrsti. */
export function idjiNaVrsti(listki: PripravljenListek[]): number[] {
  const e = enotaNaVrsti(enoteListkov(tabelaListkov(listki)))
  return e ? nenatisnjeni(e.listki).map((l) => l.tekma.id) : []
}

/* Mesta na listu A4, ki ostanejo prosta za zadnjim listkom (0 pri polnem listu). */
export function prostaMestaNaListu(steviloListkov: number): number {
  return (LISTKOV_NA_LIST - (steviloListkov % LISTKOV_NA_LIST)) % LISTKOV_NA_LIST
}

/* Koliko praznih listkov gre v tisk: izbrani število in prazni se dopolnijo do polnega lista, če
   organizator tako želi (le ob vsaj enem pravem listku: samo prazni listki se ne dopolnjujejo). */
export function praznihZaTisk(izbranih: number, prazni: number, dopolni: boolean): number {
  return prazni + (dopolni && izbranih > 0 ? prostaMestaNaListu(izbranih + prazni) : 0)
}

/* Kratek čas tiska v ploščici: »09:12« za danes, »16. 11.« za starejši dan. */
export function kratekCasNatisa(iso: string, zdaj: Date = new Date()): string {
  const cas = new Date(iso)
  if (Number.isNaN(cas.getTime())) return ''
  const istiDan =
    cas.getFullYear() === zdaj.getFullYear() &&
    cas.getMonth() === zdaj.getMonth() &&
    cas.getDate() === zdaj.getDate()
  return istiDan
    ? cas.toLocaleTimeString('sl-SI', { hour: '2-digit', minute: '2-digit' })
    : `${cas.getDate()}. ${cas.getMonth() + 1}.`
}

/* 1 list, 2 lista, 3 listi, 5 listov (list je moškega spola). */
export function sklonListovTiska(n: number): string {
  const o = n % 100
  if (o === 1) return 'list'
  if (o === 2) return 'lista'
  if (o === 3 || o === 4) return 'listi'
  return 'listov'
}

/* Id-ji tekem za hitro izbiro: vse še nenatisnjene, ali samo iz enega sklopa. */
export function idjiNenatisnjenih(listki: PripravljenListek[], sklop?: string): number[] {
  return nenatisnjeni(listki)
    .filter((l) => sklop === undefined || l.sklop === sklop)
    .map((l) => l.tekma.id)
}

/* Ali sta množici enaki (hitra izbira je »vklopljena«, kadar je izbira natanko ta). */
export function enakiIdji(a: ReadonlySet<number>, b: readonly number[]): boolean {
  return a.size === b.length && b.every((id) => a.has(id))
}

/* Kdaj je bil listek natisnjen: »ob 14:32«, za starejši dan »16. 11. ob 14:32«.
   Čas pride iz strežnika brez časovnega pasu (njegov lokalni čas). */
export function opisNatisa(iso: string, zdaj: Date = new Date()): string {
  const cas = new Date(iso)
  if (Number.isNaN(cas.getTime())) return ''
  const ura = cas.toLocaleTimeString('sl-SI', { hour: '2-digit', minute: '2-digit' })
  const istiDan =
    cas.getFullYear() === zdaj.getFullYear() &&
    cas.getMonth() === zdaj.getMonth() &&
    cas.getDate() === zdaj.getDate()
  return istiDan ? `ob ${ura}` : `${cas.getDate()}. ${cas.getMonth() + 1}. ob ${ura}`
}

/* Največ listkov, ki jih je mogoče natisniti praznih naenkrat (20 listov A4). */
export const NAJVEC_PRAZNIH_LISTKOV = 120
export const LISTKOV_NA_LIST = 6

/* Število praznih listkov iz vnosa: celo število od 1 do meje, sicer privzetek. */
export function steviloPraznih(vnos: string, privzeto = LISTKOV_NA_LIST): number {
  const n = Math.floor(Number(vnos))
  if (!Number.isFinite(n) || n < 1) return privzeto
  return Math.min(n, NAJVEC_PRAZNIH_LISTKOV)
}
