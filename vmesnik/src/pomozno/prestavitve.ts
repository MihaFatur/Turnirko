/* Prestavljena srečanja lige (V47): skupna pravila za razpored, okno ekipe in
   urejanje terminov.

   Srečanje je prestavljeno, ko se ne igra v kolu iz žreba. Dva načina:
   - premaknjeno v drugo kolo: `kolo` je kolo, v katerem se igra, `izvirnoKolo`
     pa kolo iz žreba. Razpored ga kaže SAMO v kolu igranja, zato gledalec v
     vsakem kolu vidi, s kom je igral in kaj je odigrano;
   - brez novega termina (`cakaNaTermin`): ne sodi v noben dan, zato ga razpored
     kaže v seznamu čakajočih, v kolih pa ne. */
import type { SrecanjeDto } from '../api/tipi'

/* »Kolo«, ki ga razpored izbere z gumbom »?« na koncu traku kol: srečanja brez
   termina. Prava kola so od 1 naprej, zato 0 ne trči z nobenim. */
export const KOLO_BREZ_TERMINA = 0

/* Srečanje stoji v kolu, ki ni tisto iz žreba. */
export function jeVDrugemKolu(s: SrecanjeDto): boolean {
  return !s.cakaNaTermin && s.izvirnoKolo != null && s.izvirnoKolo !== s.kolo
}

export function jePrestavljeno(s: SrecanjeDto): boolean {
  return s.cakaNaTermin || jeVDrugemKolu(s)
}

/* Srečanja, ki se v razporedu kažejo v kolih (brez čakajočih). */
export function vKolih(srecanja: SrecanjeDto[]): SrecanjeDto[] {
  return srecanja.filter((s) => !s.cakaNaTermin)
}

/* Poved o prestavitvi pod vrstico srečanja: »Prestavljeno iz 5. kola«, pri
   čakajočem »Prestavljeno — termin še ni znan«. Razlog je poseben del, ker ga
   napiše človek (velike črke mono napisa bi ga naredile nečitljivega).

   Čakajoče srečanje stoji v seznamu brez kola (»?« v traku kol), zato tam poved
   nosi tudi kolo iz žreba (`zIzvirnimKolom`); v oknu ekipe ga vrstica že ima. */
export function opisPrestavitve(
  s: SrecanjeDto,
  zIzvirnimKolom = false,
): { naslov: string; razlog: string | null } | null {
  const razlog = s.razlogPrestavitve?.trim() || null
  if (s.cakaNaTermin) {
    const izKola = zIzvirnimKolom && s.izvirnoKolo != null ? ` iz ${s.izvirnoKolo}. kola` : ''
    return { naslov: `Prestavljeno${izKola} — termin še ni znan`, razlog }
  }
  if (jeVDrugemKolu(s)) return { naslov: `Prestavljeno iz ${s.izvirnoKolo}. kola`, razlog }
  return null
}

/* Termin kola: začetek srečanja, ki v kolu sodi. Prestavljeno srečanje se sme
   igrati na svoj dan, zato kolo datuma ne dobi od njega - razen kadar drugih ni. */
export function terminKola(srecanja: SrecanjeDto[], kolo: number): string | null {
  const vKolu = srecanja.filter((s) => s.kolo === kolo && !s.cakaNaTermin && s.predvidenZacetek)
  const domaca = vKolu.filter((s) => !jeVDrugemKolu(s))
  const izbrana = (domaca.length > 0 ? domaca : vKolu)
    .map((s) => s.predvidenZacetek as string)
    .sort()
  return izbrana[0] ?? null
}
