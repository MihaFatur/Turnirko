/* Pogled-model sklopa »Moji igralci« (izkaznica): iz DomovIgralecDto izpelje
   besedila, ki jih izkaznica izpiše. Računanje je tu in ne v komponenti, da
   je mogoče vsako pravilo (predznak, množina dni, odstotek) prebrati na enem
   mestu. */
import type {
  DomovIgralecDto,
  NaslednjeSrecanjeIgralca,
  ProfilUvrstitev,
  TekmaProfila,
} from '../api/tipi'
import { oblikujDanKratekMesec, oblikujTermin, oblikujUro } from './oblikovanje'

/* Smer spremembe ratinga: po njej se izbere barva (zelena, rjasta, siva). */
export type SmerSpremembe = 'gor' | 'dol' | 'nic'

/* Predznak z minusom U+2212, kot drugod v vmesniku: »+7«, »−4«, »+0«. */
export function predznak(n: number): string {
  return `${n >= 0 ? '+' : '−'}${Math.abs(n)}`
}

/* Lestvica brez predpone »Lestvica« (kot imeLestvice): »Moški · Člani«. */
export function lestvicaKratko(uvrstitev: ProfilUvrstitev): string {
  return [
    uvrstitev.spol === 'ZENSKI' ? 'Ženske' : 'Moški',
    uvrstitev.rekreativec ? 'Rekreativci' : 'Člani',
  ].join(' · ')
}

/* Vsota sprememb ratinga zadnjih tekem (brez spremembe = 0). */
export function spremembaVsota(zadnje: TekmaProfila[]): { besedilo: string; smer: SmerSpremembe } {
  const vsota = zadnje.reduce((s, t) => s + (t.spremembaRatinga ?? 0), 0)
  return { besedilo: predznak(vsota), smer: vsota > 0 ? 'gor' : vsota < 0 ? 'dol' : 'nic' }
}

export interface BilancaSezone {
  /* »18–7« z en dash. */
  prikaz: string
  /* »72 % zmag« ali »brez tekem«. */
  pod: string
  /* Širina palice v odstotkih. */
  odstotek: number
}

export function bilancaSezone(sezona: DomovIgralecDto['sezona']): BilancaSezone | null {
  if (!sezona) return null
  const skupaj = sezona.zmage + sezona.porazi
  const odstotek = skupaj === 0 ? 0 : Math.round((100 * sezona.zmage) / skupaj)
  return { prikaz: `${sezona.zmage}–${sezona.porazi}`, pod: `${odstotek} % zmag`, odstotek }
}

/* Velikost priimka po dolgoti: dolg priimek bi pri 104 px presegel vrstico. */
export function velikostPriimka(priimek: string): string {
  const dolzina = priimek.length
  if (dolzina <= 8) return 'clamp(36px,9cqw,104px)'
  if (dolzina <= 11) return 'clamp(32px,7cqw,80px)'
  return 'clamp(28px,5.2cqw,60px)'
}

/* Današnji koledarski dan v Ljubljani (yyyy-mm-dd). Brskalnik v drugem
   časovnem pasu bi sicer »jutri« preračunal po svojem. */
export function danesLjubljana(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Ljubljana',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function dnevi(iso: string): number {
  const [l, m, d] = iso.slice(0, 10).split('-').map(Number)
  return Date.UTC(l, m - 1, d) / 86_400_000
}

/* »danes«, »jutri«, »čez 5 dni« (vedno »dni«, tudi pri 2). */
export function cez(datum: string, danes: string): string {
  const n = Math.round(dnevi(datum) - dnevi(danes))
  if (n <= 0) return 'danes'
  if (n === 1) return 'jutri'
  return `čez ${n} dni`
}

export interface SrecanjeIzkaznice {
  idSrecanje: number
  /* Dan v tednu (»ned«), datum (»8. nov«) in ura (»18.00«) — prazni, kadar
     začetka ni; takrat izkaznica izpiše samo »6. kolo«. */
  dan: string
  datum: string
  ura: string
  imaZacetek: boolean
  kolo: string
  opis: string
  pod: string
}

export function srecanjeIzkaznice(s: NaslednjeSrecanjeIgralca): SrecanjeIzkaznice {
  const imaZacetek = s.zacetek !== null
  return {
    idSrecanje: s.idSrecanje,
    dan: imaZacetek ? oblikujTermin(s.zacetek).split(',')[0] : '',
    datum: s.zacetek ? oblikujDanKratekMesec(s.zacetek.slice(0, 10)) : '',
    ura: imaZacetek ? oblikujUro(s.zacetek) : '',
    imaZacetek,
    kolo: `${s.kolo}. kolo`,
    opis: `${s.ekipa} – ${s.nasprotnik}`,
    pod: `${s.liga} · ${s.kolo}. kolo · ${s.doma ? 'doma' : 'v gosteh'}`,
  }
}

/* Napis nad vstopnico: srečanje ima prednost pred turnirjem. */
export function cezNapis(igralec: DomovIgralecDto, danes: string): string {
  const s = igralec.naslednjeSrecanje
  if (s?.zacetek) return cez(s.zacetek, danes)
  const t = igralec.prihajajociTurnir
  if (s) return ''
  return t ? cez(t.datum, danes) : ''
}

/* Zavihek: »1482 · 14.« (rating »—«, če ga ni; brez mesta samo rating). */
export function podZavihka(igralec: DomovIgralecDto): string {
  const rating = igralec.rating ?? '—'
  const mesto = igralec.uvrstitev.mesto
  return mesto === null ? String(rating) : `${rating} · ${mesto}.`
}
