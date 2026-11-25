/* Pomožne funkcije strani Mize (M1, M2; docs/pravila/mize.md). Kaj je prosto,
   kdo je predlagan in v kakšnem vrstnem redu, odloči strežnik (PredlogTekem);
   tu je samo prikaz: minute, imena, privzeti sodnik in sproščene mize. */
import { imeUdelezenca, priimekUdelezenca } from '../api/tipi'
import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, OsebaMizeDto, TekmaMizeDto, Udelezenec } from '../api/tipi'

/* Meja »predolgo« (prenova tlorisa, 24. 11. 2026): tekma, ki na mizi traja vsaj toliko
   minut, je na tlorisu rjasto označena (ne velja za ekipno srečanje, ki je dolgo že po
   naravi). Konstanta, da jo lahko lastnik pozneje prestavi v nastavitve turnirja. */
export const MEJA_PREDOLGO_MIN = 30

/* Cele minute od časa `od` do ure strežnika (ura prenosnika lahko teče drugače). */
export function minutOd(od: string | null, casStreznika: string): number | null {
  if (!od) return null
  const razlika = Date.parse(casStreznika) - Date.parse(od)
  if (Number.isNaN(razlika)) return null
  return Math.max(0, Math.floor(razlika / 60_000))
}

/* »Ana Novak – Bor Kovač« (pri dvojicah »A / B – C / D«, pri ekipah imeni ekip). */
export function imenaTekme(t: TekmaMizeDto): string {
  return `${imeUdelezenca(t.tekma.udelezenec1) ?? '?'} – ${imeUdelezenca(t.tekma.udelezenec2) ?? '?'}`
}

/* »Člani · Skupina 2« */
export function opisTekme(t: TekmaMizeDto): string {
  return `${t.imeDogodka} · ${t.del}`
}

/* Skupne mize (V78): turnirja na istih mizah imata lahko enako imenovane kategorije
   (»Moški« v U15 in v U21). Kategorija, katere ime ni edino med kategorijami turnirjev na
   teh mizah, dobi ime turnirja, da so oznake na mizi, v vrsti in na zaslonu nedvoumne.
   Brez povezave ostane vse, kot je. */
export function sOznakamiKategorij(p: MizeTurnirjaDto): MizeTurnirjaDto {
  if (p.skupne.turnirji.length < 2) return p
  const imen = new Map<string, number>()
  for (const k of p.skupne.kategorije) imen.set(k.ime, (imen.get(k.ime) ?? 0) + 1)
  const oznaci = (t: TekmaMizeDto): TekmaMizeDto =>
    (imen.get(t.imeDogodka) ?? 0) > 1 ? { ...t, imeDogodka: `${t.imeDogodka} · ${t.imeTurnirja}` } : t
  return {
    ...p,
    mize: p.mize.map((m) => (m.tekma ? { ...m, tekma: oznaci(m.tekma) } : m)),
    kandidati: p.kandidati.map((k) => ({ ...k, tekma: oznaci(k.tekma) })),
  }
}

/* Mize tekme v igri: »3« ali »3 in 4« (ekipno srečanje na dveh mizah). */
export function mizeTekme(t: TekmaMizeDto): string {
  if (t.miza === null) return ''
  return t.miza2 === null ? String(t.miza) : `${t.miza} in ${t.miza2}`
}

/* Privzeti sodnik ob klicu na mizo: poraženec zadnje tekme na njej, razen če igra
   v tekmi, ki se kliče (svoje tekme ne sodi). */
export function privzetiSodnik(miza: MizaDto | undefined, t: TekmaMizeDto): OsebaMizeDto | null {
  const sodnik = miza?.cakajociSodnik ?? null
  if (!sodnik || t.osebe.includes(sodnik.id)) return null
  return sodnik
}

/* Proste mize (ne izklopljene, nihče ne igra), po številki. */
export function prosteMize(mize: MizaDto[]): number[] {
  return mize.filter((m) => !m.izklopljena && m.tekma === null).map((m) => m.stevilka)
}

/* Mize, ki so se od prejšnje osvežitve sprostile (pisk in poudarek). Ob prvem
   nalaganju (prej ni) ni sproščenih: stran ne piska, ko se odpre. */
export function sprosceneMize(prej: MizaDto[] | undefined, zdaj: MizaDto[]): number[] {
  if (!prej) return []
  const zasedene = new Set(prej.filter((m) => m.tekma !== null).map((m) => m.stevilka))
  return zdaj.filter((m) => m.tekma === null && !m.izklopljena && zasedene.has(m.stevilka)).map((m) => m.stevilka)
}

/* Prvi predlog vsake proste mize (gumb »Pokliči predloge«): [miza, kandidat]. */
export function prviPredlogi(mize: MizaDto[], kandidati: KandidatMizeDto[]): [MizaDto, KandidatMizeDto][] {
  const poId = new Map(kandidati.map((k) => [k.tekma.tekma.id, k]))
  const pari: [MizaDto, KandidatMizeDto][] = []
  for (const m of mize) {
    if (m.izklopljena || m.tekma !== null || m.predlogi.length === 0) continue
    const k = poId.get(m.predlogi[0])
    if (k && !k.zaseden) pari.push([m, k])
  }
  return pari
}

/* »pred 4 min«, »pravkar« */
export function predMinutami(minute: number | null): string {
  if (minute === null) return ''
  return minute === 0 ? 'pravkar' : `pred ${minute} min`
}

/* ---------- tloris dvorane (prenova 24. 11. 2026) ---------- */

/* Zadnja beseda: »Tadej Mihelič« → »Mihelič«. Ime brez presledka ostane celo. */
export function zadnjaBeseda(ime: string): string {
  const deli = ime.trim().split(/\s+/)
  return deli[deli.length - 1] ?? ''
}

/* Ime na mizi je priimek (strežnik ga pošlje v `priimek`: pri dvojicah »Kokalj / Logar«,
   pri ekipi ime ekipe). Če ga ni, pade na zadnjo besedo polnega imena. */
export function priimekNaMizi(u: Udelezenec | null): string {
  const priimek = priimekUdelezenca(u)
  if (priimek) return priimek
  const ime = imeUdelezenca(u)
  return ime ? (ime.includes(' / ') ? ime.split(' / ').map(zadnjaBeseda).join(' / ') : zadnjaBeseda(ime)) : '?'
}

/* »Vidmar – Kolar« */
export function priimkiTekme(t: TekmaMizeDto): [string, string] {
  return [priimekNaMizi(t.tekma.udelezenec1), priimekNaMizi(t.tekma.udelezenec2)]
}

export function kratkoImenaTekme(t: TekmaMizeDto): string {
  const [a, b] = priimkiTekme(t)
  return `${a} – ${b}`
}

/* Del tekme na mizi: »Skupina 2« je »sk. 2« (v oknih in na vrsti ostane polno). */
export function kratekDel(del: string): string {
  return del.replace(/^Skupina\b/, 'sk.')
}

/* Oznaka mize ali mize in druge mize ekipnega srečanja: »5«, »11 + 12«. */
export function oznakaMize(t: Pick<TekmaMizeDto, 'miza' | 'miza2'>, locilo = ' + '): string {
  if (t.miza === null) return ''
  return t.miza2 === null ? String(t.miza) : `${t.miza}${locilo}${t.miza2}`
}

/* Druga miza ekipnega srečanja ob klicu na mizo `miza`: sosednja prosta (najprej +1, nato
   −1), sicer prva druga prosta. Brez proste mize je null (srečanje se pokliče na eno). */
export function drugaMizaEkipnega(proste: number[], miza: number): number | null {
  const druge = proste.filter((st) => st !== miza)
  if (druge.includes(miza + 1)) return miza + 1
  if (druge.includes(miza - 1)) return miza - 1
  return druge[0] ?? null
}

/* Širina modrega traku časa na mizi v igri (odstotki, celo število kot v prototipu). */
export function sirinaTraka(minute: number): number {
  return Math.min(100, Math.round((minute / MEJA_PREDOLGO_MIN) * 100))
}

export function jePredolgo(t: TekmaMizeDto, minute: number | null): boolean {
  return !t.ekipno && minute !== null && minute >= MEJA_PREDOLGO_MIN
}

/* Tekme, ki se igrajo (ekipno srečanje stoji na dveh mizah, a je ena tekma). */
export function tekmeVIgri(mize: MizaDto[]): TekmaMizeDto[] {
  const videne = new Set<number>()
  const tekme: TekmaMizeDto[] = []
  for (const m of mize) {
    if (!m.tekma || videne.has(m.tekma.tekma.id)) continue
    videne.add(m.tekma.tekma.id)
    tekme.push(m.tekma)
  }
  return tekme
}

/* Najdlje igra: »11 + 12« in minute; null, kadar se ne igra nič. Pri izenačenju velja
   nižja miza (prva v seznamu). */
export function najdljeIgra(mize: MizaDto[], casStreznika: string): { oznaka: string; minute: number } | null {
  let najdlje: { oznaka: string; minute: number } | null = null
  for (const t of tekmeVIgri(mize)) {
    const minute = minutOd(t.poklicanaOb, casStreznika) ?? 0
    if (!najdlje || minute > najdlje.minute) najdlje = { oznaka: oznakaMize(t), minute }
  }
  return najdlje
}

/* »10:42« iz časa strežnika (»2026-11-24T10:42:07«); brez veljavne ure prazno. */
export function uraIzCasa(cas: string | null): string {
  const zadetek = cas ? /T(\d{2}:\d{2})/.exec(cas) : null
  return zadetek ? zadetek[1] : ''
}

/* Kaj vleče organizator na tlorisu: tekmo z vrste (id tekme) ali tekmo z mize (številka mize). */
export type Vlecem = { tip: 'k'; id: number } | { tip: 'm'; miza: number } | null

/* Razlog je zasedenost, če pove, kdo igra ali sodi (na kateri mizi). */
function jeZasedenost(razlog: string): boolean {
  return !razlog.includes(' naj sodi') && / (igra|sodi)( na mizi|$)/.test(razlog)
}

/* Razlogi, zakaj kandidat ni predlagan: kdo igra ali sodi na kateri mizi. Drugi razlogi
   (»do konca skupine še 2 kola«) so predlog, ne zasedenost, in tu odpadejo. Kadar
   strežnik zasedenosti ne poimenuje, ostanejo vsi razlogi. */
export function razlogiZasedenosti(k: KandidatMizeDto): string[] {
  const zasedenost = k.razlogi.filter(jeZasedenost)
  return zasedenost.length > 0 ? zasedenost : k.razlogi
}

/* Razlogi za predlog (vse razen zasedenosti): vrstica »Predlagano: …« v oknu klica. */
export function razlogiPredloga(k: KandidatMizeDto): string[] {
  return k.razlogi.filter((r) => !jeZasedenost(r))
}

/* »miza«, »mizi«, »mize«, »miz« po številu (Postavi 20 miz). */
export function mizSklon(n: number): string {
  const o = n % 100
  if (o === 1) return 'miza'
  if (o === 2) return 'mizi'
  if (o === 3 || o === 4) return 'mize'
  return 'miz'
}
