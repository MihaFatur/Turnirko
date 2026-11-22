/* Priprava listkov (zapisnikov) tekem za tisk: katere tekme pridejo v
   poštev, kako se razvrstijo, v kakšne sklope se združijo za hitro izbiro in
   katere so že natisnjene.

   Ločeno od izrisa, da je logika testljiva. Isto uporabljata posamični listki
   (ListkiStran) in ekipni zapisniki (ZapisnikiEkipnegaDogodka). */
import type { FazaTekme, MrezaDto, TekmaDto } from '../api/tipi'
import { imeKola } from './oblikovanje'
import { imeTekmeZreba } from './svRegija'

/* Skupine se natisnejo prve (predtekmovanje), nato glavna mreža, nato
   tolažilne tekme (npr. za 3. mesto). */
const RANG_FAZE: Record<FazaTekme, number> = { SKUPINA: 0, GLAVNI: 1, TOLAZILNI: 2 }

export interface PripravljenListek {
  tekma: TekmaDto
  /* Oznaka skupine (»A«); prazna pri izločilni mreži. */
  skupina: string | null
  /* Kolo skupine ali krog mreže. */
  faza: string
  /* Sklop za hitro izbiro (»Skupina A«, »Tolažilni žreb«) in njegov ključ. */
  sklop: string
  sklopNaslov: string
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
export function pripraviListke(podatki: MrezaDto | undefined): PripravljenListek[] {
  if (!podatki) return []

  const glavne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  const zadnjeKolo = glavne.length > 0 ? Math.max(...glavne.map((t) => t.kolo)) : 1

  return podatki.tekme
    .filter(jePripravljena)
    .sort(
      (a, b) =>
        RANG_FAZE[a.faza] - RANG_FAZE[b.faza] ||
        (a.idSkupina ?? 0) - (b.idSkupina ?? 0) ||
        (a.idZreb ?? 0) - (b.idZreb ?? 0) ||
        a.kolo - b.kolo ||
        a.pozicija - b.pozicija,
    )
    .map((tekma) => {
      const skupina =
        tekma.faza === 'SKUPINA'
          ? (podatki.skupine.find((s) => s.id === tekma.idSkupina)?.oznaka ?? null)
          : null
      const sklop = sklopTekme(tekma, skupina, podatki)
      return {
        tekma,
        skupina,
        faza: oznakaFaze(tekma, zadnjeKolo, podatki),
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

/* Sklop, v katerem je tekma: skupina (»Skupina A«), žreb SV regije (»Skupina 2 ·
   Tolažilni žreb«), glavni ali tolažilni del mreže; krožni sistem nima skupin,
   zato se tam sklopi ujemajo s koli (»3. kolo«). */
function sklopTekme(
  tekma: TekmaDto,
  skupina: string | null,
  podatki: MrezaDto,
): { kljuc: string; naslov: string } {
  if (tekma.faza === 'SKUPINA') {
    if (skupina === null) return { kljuc: `kolo-${tekma.kolo}`, naslov: `${tekma.kolo}. kolo` }
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

/* Listke združi v sklope po vrstnem redu prve pojavitve (listki so že
   razvrščeni po fazi, skupini in žrebu). */
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

/* Mesta na hrbtni strani lista (S2), ki dobijo kodo za sodnika, ko ima prednja
   stran `listkov` listkov (zaporedje po vrsticah: 2 stolpca x 3 vrstice).
   Obojestranski tisk obrne list po dolgem robu (hrbet stolpca c je za prednjim
   stolpcem 1 - c) ali po kratkem (hrbet vrstice r je za vrstico 2 - r); katero,
   izbere tiskalnik. Mesto dobi kodo, če je za njim listek v kateremkoli od obeh
   načinov - koda je na vseh hrbtih ista, odvečna pa pristane na odrezku. */
export function mestaHrbta(listkov: number): boolean[] {
  return Array.from({ length: LISTKOV_NA_LIST }, (_, i) => {
    const vrstica = Math.floor(i / 2)
    const stolpec = i % 2
    const poDolgem = vrstica * 2 + (1 - stolpec)
    const poKratkem = (2 - vrstica) * 2 + stolpec
    return poDolgem < listkov || poKratkem < listkov
  })
}

/* Število praznih listkov iz vnosa: celo število od 1 do meje, sicer privzetek. */
export function steviloPraznih(vnos: string, privzeto = LISTKOV_NA_LIST): number {
  const n = Math.floor(Number(vnos))
  if (!Number.isFinite(n) || n < 1) return privzeto
  return Math.min(n, NAJVEC_PRAZNIH_LISTKOV)
}
