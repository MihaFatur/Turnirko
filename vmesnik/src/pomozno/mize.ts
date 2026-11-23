/* Pomožne funkcije strani Mize (M1, M2; docs/pravila/mize.md). Kaj je prosto,
   kdo je predlagan in v kakšnem vrstnem redu, odloči strežnik (PredlogTekem);
   tu je samo prikaz: minute, imena, privzeti sodnik in sproščene mize. */
import { imeUdelezenca } from '../api/tipi'
import type { KandidatMizeDto, MizaDto, OsebaMizeDto, TekmaMizeDto } from '../api/tipi'

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
