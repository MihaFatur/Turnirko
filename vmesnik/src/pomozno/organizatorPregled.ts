/* Besedilo organizatorskega pregleda. Zaledje pošlje števila, datume in imena;
   sklanjanje in poved sestavi vmesnik (isto načelo kot pri opozorilih
   napovedi tekme: strežnik poimenuje, vmesnik napiše). */
import type {
  CakaPregledaDto,
  KvotaDto,
  Paket,
  TekmovanjePregledaDto,
  TerminPregledaDto,
} from '../api/tipi'
import { oblikujDanMesec, oblikujObdobjeKratko, oblikujUro } from './oblikovanje'

export type OrganizatorPaket = Extract<Paket, `ORGANIZATOR_${string}`>

/* »Pro« v veliki besedi kolofona. */
export const KRATKO_IME_PAKETA: Record<OrganizatorPaket, string> = {
  ORGANIZATOR_BASIC: 'Basic',
  ORGANIZATOR_PLUS: 'Plus',
  ORGANIZATOR_PRO: 'Pro',
}

/* Naslednji višji paket; najvišji ga nima. */
export function visjiPaket(paket: OrganizatorPaket): OrganizatorPaket | null {
  if (paket === 'ORGANIZATOR_BASIC') return 'ORGANIZATOR_PLUS'
  if (paket === 'ORGANIZATOR_PLUS') return 'ORGANIZATOR_PRO'
  return null
}

export function jeOrganizatorPaket(paket: Paket | null): paket is OrganizatorPaket {
  return paket !== null && paket.startsWith('ORGANIZATOR_')
}

/* Sklanjanje po številu: samo 1, 2, 3 in 4 (tudi 101, 102 ...) imajo posebno
   obliko, vse drugo - tudi 21-24 in 31-34 - je rodilnik množine (»24 ekip«,
   »64 igralcev«). Zato ostanek pri 100 in ne pri 10, kot ga ima skupni
   `sklon` v oblikovanje.ts: ta bi »64« sklonil kot »3-4 igralci«. */
function sklon(n: number, ena: string, dve: string, tri: string, vec: string): string {
  const ostanek = n % 100
  if (ostanek === 1) return ena
  if (ostanek === 2) return dve
  if (ostanek === 3 || ostanek === 4) return tri
  return vec
}

export const sklonEkip = (n: number) => sklon(n, 'ekipa', 'ekipi', 'ekipe', 'ekip')
export const sklonIgralcev = (n: number) => sklon(n, 'igralec', 'igralca', 'igralci', 'igralcev')
export const sklonPrijavljenih = (n: number) =>
  sklon(n, 'prijavljen', 'prijavljena', 'prijavljeni', 'prijavljenih')
export const sklonTekmovanj = (n: number) =>
  sklon(n, 'tekmovanje', 'tekmovanji', 'tekmovanja', 'tekmovanj')
export const sklonTekem = (n: number) => sklon(n, 'tekma', 'tekmi', 'tekme', 'tekem')
export const sklonLig = (n: number) => sklon(n, 'liga', 'ligi', 'lige', 'lig')
export const sklonTurnirjev = (n: number) => sklon(n, 'turnir', 'turnirja', 'turnirji', 'turnirjev')
export const sklonSrecanj = (n: number) => sklon(n, 'srečanje', 'srečanji', 'srečanja', 'srečanj')
export const sklonUdelezencev = (n: number) =>
  sklon(n, 'udeleženec', 'udeleženca', 'udeleženci', 'udeležencev')
export const sklonProstihMest = (n: number) =>
  sklon(n, 'prosto mesto', 'prosti mesti', 'prosta mesta', 'prostih mest')

/* »1.418« - tisočice ločuje pika, kot jo piše maketa (za razliko od
   oblikujStevilo, ki ima ozek presledek za mono številke). */
export function stevilkaZPiko(n: number): string {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

/* »2026/27« -> »26/27«. */
export function kratkaSezona(sezona: string): string {
  return sezona.length >= 7 ? sezona.slice(2) : sezona
}

// ---------- Kvota paketa ----------

export interface OpisKvote {
  prosto: number
  poln: boolean
  /* Delež polne palice, 0-100. */
  delez: number
  /* Opomba pod palico: »Še 3 lige v tej sezoni« ali pojasnilo, zakaj je polno. */
  opomba: string
}

export function opisKvote(
  kvota: KvotaDto,
  vrsta: 'liga' | 'turnir',
  imaVisji: boolean,
  naslednjaSezonaOd: string,
): OpisKvote {
  const prosto = Math.max(kvota.meja - kvota.uporabljeno, 0)
  const poln = kvota.uporabljeno >= kvota.meja
  const delez = kvota.meja > 0 ? Math.min(Math.round((kvota.uporabljeno / kvota.meja) * 100), 100) : 100
  const datum = datumBrezZapisa(naslednjaSezonaOd)
  let opomba: string
  if (poln) {
    opomba = imaVisji
      ? `Nov${vrsta === 'liga' ? 'a liga' : ' turnir'} šele z nadgradnjo ali ${datum}`
      : `Nova sezona ${datum}`
  } else {
    opomba =
      vrsta === 'liga'
        ? `Še ${prosto} ${sklonLig(prosto)} v tej sezoni`
        : `Še ${prosto} ${sklonTurnirjev(prosto)} v tej sezoni`
  }
  return { prosto, poln, delez, opomba }
}

/* Napis na tabli lika ob palici. */
export function napisTable(opis: OpisKvote, imaVisji: boolean, naslednjaSezonaOd: string): string {
  if (!opis.poln) return sklonProstihMestZStevilom(opis.prosto)
  return imaVisji ? 'Polno — nadgradi' : `Polno do ${oblikujDanMesec(naslednjaSezonaOd)}`
}

function sklonProstihMestZStevilom(n: number): string {
  return `${n} ${sklonProstihMest(n)}`
}

/* »1. 7. 2027« iz ISO datuma. */
function datumBrezZapisa(iso: string): string {
  const [leto, mesec, dan] = iso.split('-').map(Number)
  return `${dan}. ${mesec}. ${leto}`
}

// ---------- Vrstice tekmovanj ----------

/* Opis pod imenom: turnir »Žalec · 13. 9. 2026 · 64 igralcev« (število
   igralcev samo, ko turnir ni več v pripravi - tam ga nosi palica), liga
   »10 ekip · dvokrožno«. */
export function opisTekmovanja(t: TekmovanjePregledaDto): string {
  if (t.vrsta === 'LIGA') {
    return `${t.udelezencev} ${sklonEkip(t.udelezencev)} · ${t.dvokrozno ? 'dvokrožno' : 'enokrožno'}`
  }
  const deli: string[] = []
  if (t.kraj) deli.push(t.kraj)
  const obdobje = oblikujObdobjeKratko(t.datumZacetka, t.datumKonca, true)
  if (obdobje) deli.push(obdobje)
  if (t.status !== 'PRIPRAVA') deli.push(`${t.udelezencev} ${sklonIgralcev(t.udelezencev)}`)
  return deli.join(' · ')
}

/* Oznaka nad palico: »Kolo 3 / 18«, »Tekme 142 / 188«, »Prijave 41«. */
export function oznakaNapredka(t: TekmovanjePregledaDto): string {
  switch (t.napredekVrsta) {
    case 'KOLO':
      return t.napredekVseh > 0 ? `Kolo ${t.napredekTrenutno} / ${t.napredekVseh}` : 'Razpored še ni'
    case 'TEKME':
      return t.napredekVseh > 0 ? `Tekme ${t.napredekTrenutno} / ${t.napredekVseh}` : 'Žreb še ni izveden'
    case 'PRIJAVE':
      return `Prijave ${t.napredekTrenutno}`
    case 'EKIPE':
      return `Ekipe ${t.napredekTrenutno}`
    case 'KONCANO':
      return 'Končano'
  }
}

/* Besedilo v stolpcu »čaka« (velike črke doda slog). Prazno, kadar ni kaj
   povedati. */
export function besediloCakanja(t: TekmovanjePregledaDto): string {
  const n = t.cakaStevilo ?? 0
  switch (t.cakaVrsta) {
    case 'ZAPISNIKI':
      return `${n} ${sklon(n, 'zapisnik čaka', 'zapisnika čakata', 'zapisniki čakajo', 'zapisnikov čaka')}`
    case 'REZULTATI':
      return `${n} ${sklon(n, 'rezultat', 'rezultata', 'rezultati', 'rezultatov')}`
    case 'ZREB':
      return 'Žreb čaka'
    case 'NASLEDNJE_SRECANJE':
      return `Naslednje ${oblikujDanMesec(t.cakaDatum)}`
    case 'ROK_PRIJAVE':
      return `Rok prijave ${oblikujDanMesec(t.cakaDatum)}`
    case 'ZACETEK':
      return `Začetek ${oblikujDanMesec(t.cakaDatum)}`
    case 'RATING_OBRACUNAN':
      return 'Rating preračunan'
    case 'NE_STEJE_V_RATING':
      return 'Ne šteje v rating'
    default:
      return ''
  }
}

/* Ali vrstica čaka na organizatorja (moder levi rob, moder tekst) ali je
   samo informacija. */
export function cakaOrganizatorja(t: TekmovanjePregledaDto): boolean {
  return t.cakaVrsta === 'ZAPISNIKI' || t.cakaVrsta === 'REZULTATI' || t.cakaVrsta === 'ZREB'
}

/* Pot do tekmovanja iz vrstice. */
export function potTekmovanja(vrsta: 'LIGA' | 'TURNIR', id: number): string {
  return vrsta === 'LIGA' ? `/lige/${id}` : `/turnirji/${id}`
}

// ---------- »Čaka te« ----------

export function jeRezultatAliZapisnik(c: CakaPregledaDto): boolean {
  return c.vrsta === 'REZULTATI' || c.vrsta === 'ZAPISNIKI'
}

export function gumbCakanja(c: CakaPregledaDto): string {
  if (c.vrsta === 'REZULTATI') return 'Vnesi rezultate'
  if (c.vrsta === 'ZAPISNIKI') return c.stevilo === 1 ? 'Vnesi zapisnik' : 'Vnesi zapisnike'
  return 'Naredi žreb'
}

/* Kam vodi gumb: rezultati v dogodek, kjer čakajo (klik na tekmo odpre vnos),
   zapisnik na srečanje (pri več čakajočih na stran lige, kjer so vsa), žreb na
   turnir (dogodkov v pripravi je lahko več). */
export function potCakanja(c: CakaPregledaDto): string {
  if (c.vrsta === 'REZULTATI') {
    return c.idDogodka != null ? `/dogodki/${c.idDogodka}` : potTekmovanja('TURNIR', c.idTekmovanja)
  }
  if (c.vrsta === 'ZAPISNIKI') {
    return c.stevilo === 1 && c.idSrecanja != null
      ? `/srecanja/${c.idSrecanja}`
      : potTekmovanja('LIGA', c.idTekmovanja)
  }
  return potTekmovanja('TURNIR', c.idTekmovanja)
}

/* Opis pod imenom (namizje). */
export function opisCakanja(c: CakaPregledaDto): string {
  if (c.vrsta === 'REZULTATI') {
    const dogodki = c.podrobnosti.join(', ')
    return `Tekme čakajo na vnos rezultata: ${dogodki}${c.faza ? `, ${c.faza}` : ''}.`
  }
  if (c.vrsta === 'ZAPISNIKI') {
    const pari = c.podrobnosti.join(' in ')
    const se = c.stevilo - c.podrobnosti.length
    return `${c.kolo}. kolo, ${oblikujDanMesec(c.datum)} ${pari}${se > 0 ? ` in še ${se}` : ''}.`
  }
  const zacetek = c.datum ? `Začetek ${oblikujDanMesec(c.datum)} · ` : ''
  return `${zacetek}${c.stevilo} ${sklonPrijavljenih(c.stevilo)}. Žreb še ni narejen.`
}

/* Kratka mono vrstica pod imenom (telefon). */
export function kratkoCakanje(c: CakaPregledaDto): string {
  if (c.vrsta === 'REZULTATI') {
    return [c.podrobnosti[0], c.faza].filter(Boolean).join(' · ')
  }
  if (c.vrsta === 'ZAPISNIKI') return `${c.kolo}. kolo · ${oblikujDanMesec(c.datum)}`
  return c.datum ? `Začetek ${oblikujDanMesec(c.datum)}` : 'Prijave zaprte'
}

/* Povzetek v glavi bloka: »16 rezultatov · 2 žreba«. */
export function povzetekCakanja(caka: CakaPregledaDto[]): string {
  const rezultatov = caka.filter(jeRezultatAliZapisnik).reduce((vsota, c) => vsota + c.stevilo, 0)
  const zrebov = caka.filter((c) => c.vrsta === 'ZREB').length
  const deli = [`${rezultatov} ${sklon(rezultatov, 'rezultat', 'rezultata', 'rezultati', 'rezultatov')}`]
  if (zrebov > 0) deli.push(`${zrebov} ${sklon(zrebov, 'žreb', 'žreba', 'žrebi', 'žrebov')}`)
  return deli.join(' · ')
}

/* Koliko stvari čaka organizatorja: tekme, srečanja in žrebi. */
export function steviloCakajocih(caka: CakaPregledaDto[]): number {
  return caka.reduce((vsota, c) => vsota + (c.vrsta === 'ZREB' ? 1 : c.stevilo), 0)
}

// ---------- Termini ----------

const DNEVI = ['NED', 'PON', 'TOR', 'SRE', 'ČET', 'PET', 'SOB']
const MESECI = ['JAN', 'FEB', 'MAR', 'APR', 'MAJ', 'JUN', 'JUL', 'AVG', 'SEP', 'OKT', 'NOV', 'DEC']

/* »SOB · OKT« pod datumom. */
export function tedenInMesec(iso: string): string {
  const [leto, mesec, dan] = iso.split('-').map(Number)
  /* UTC, da poletni čas dneva ne premakne. */
  const dnevVTednu = DNEVI[new Date(Date.UTC(leto, mesec - 1, dan)).getUTCDay()]
  return `${dnevVTednu} · ${MESECI[mesec - 1]}`
}

export function imeTermina(t: TerminPregledaDto): string {
  return t.vrsta === 'LIGA' ? `${t.imeTekmovanja} · ${t.kolo}. kolo` : t.imeTekmovanja
}

export function opisTermina(t: TerminPregledaDto): string {
  if (t.vrsta === 'TURNIR') return t.kraj ?? ''
  const n = t.steviloSrecanj ?? 0
  const ura = oblikujUro(t.ura)
  return `${n} ${sklonSrecanj(n)}${ura ? ` · ${ura}` : ''}`
}

export function potTermina(t: TerminPregledaDto): string {
  return potTekmovanja(t.vrsta, t.idTekmovanja)
}

// ---------- Arhiv ----------

export function obsegSezone(lig: number, turnirjev: number): string {
  return `${lig} ${sklonLig(lig)} · ${turnirjev} ${sklonTurnirjev(turnirjev)}`
}

export function opisSezone(udelezencev: number, tekem: number): string {
  return `${stevilkaZPiko(udelezencev)} ${sklonUdelezencev(udelezencev)} · ${stevilkaZPiko(tekem)} ${sklonTekem(tekem)}`
}
