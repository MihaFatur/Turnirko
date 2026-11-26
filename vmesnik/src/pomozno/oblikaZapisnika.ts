/* Oblika natisnjenega ekipnega zapisnika: uraden obrazec NTZS (pokončni A4,
   kopija papirnatega obrazca) ali Turnirkov zapisnik (ležeči A4, po zapisniku
   lige Savinja, z znamko Turnirka in logotipom lige).

   Oblika ni pravilo tekmovanja - shema je ne pozna, vsi podatki so isti -,
   zato jo izbere tisk. Zadnja izbira ostane v brskalniku (organizator ponovno
   natisne ob vsakem kolu); brez nje ima liga ležeči zapisnik, ekipna tekma
   turnirja pa privzetek formata (Turnirkov zapisnik je narejen po zapisniku,
   ki ga igra format Savinja). */
import type { FormatSrecanja, PredlogaLige } from '../api/tipi'

/* Za bralnik (V79): zapisnik, ki ga Turnirko prebere s fotografije - samodejna oblika
   (vrstice tekem, kadar gre kader nanje, sicer tabela kadra) ali izbrana. */
export type OblikaZapisnika = 'NTZS' | 'TURNIRKO' | 'BRALNIK' | 'BRALNIK_VRSTICE' | 'BRALNIK_KADER'

export const OBLIKE_BRALNIKA: OblikaZapisnika[] = ['BRALNIK', 'BRALNIK_VRSTICE', 'BRALNIK_KADER']

export const OZNAKE_BRALNIKA: Record<string, string> = {
  BRALNIK: 'Za bralnik – samodejna oblika',
  BRALNIK_VRSTICE: 'Za bralnik – vrstica tekme',
  BRALNIK_KADER: 'Za bralnik – tabela kadra',
}

export function jeZaBralnik(oblika: OblikaZapisnika | null | undefined): boolean {
  return oblika != null && OBLIKE_BRALNIKA.includes(oblika)
}

/* Oblika lista, kot jo pozna strežnik (null = samodejna). */
export function oblikaBralnika(oblika: OblikaZapisnika): 'VRSTICE' | 'KADER' | null {
  return oblika === 'BRALNIK_VRSTICE' ? 'VRSTICE' : oblika === 'BRALNIK_KADER' ? 'KADER' : null
}

/* Možnosti izbirnika »Obrazec«: pri ligi sta uradna obrazca dva (1. SNTL in
   2./3. SNTL), pri ekipni tekmi turnirja en sam. */
export type IzbiraObrazca = PredlogaLige | OblikaZapisnika

export const OZNAKA_OBRAZCA_NTZS = 'Uradni zapisnik NTZS'
export const OZNAKA_OBRAZCA_TURNIRKO = 'Zapisnik Turnirko – ležeči A4'

const KLJUC_SHRAMBE = 'turnirko-oblika-zapisnika'

/* Zadnja izbira ali null, če je še ni (ali shrambe ni). */
export function preberiOblikoZapisnika(): OblikaZapisnika | null {
  try {
    const v = window.localStorage.getItem(KLJUC_SHRAMBE)
    return v === 'NTZS' || v === 'TURNIRKO' || OBLIKE_BRALNIKA.includes(v as OblikaZapisnika)
      ? (v as OblikaZapisnika)
      : null
  } catch {
    return null
  }
}

export function shraniOblikoZapisnika(oblika: OblikaZapisnika): void {
  try {
    window.localStorage.setItem(KLJUC_SHRAMBE, oblika)
  } catch {
    /* izbira velja za to stran tudi brez shrambe */
  }
}

/* Brez shranjene izbire: format Savinja (2 igralca, dvojice prve) dobi
   Turnirkov zapisnik, ostali uradnega. Liga tega privzetka ne uporablja - vedno
   začne z ležečim (ListkiSrecanjaStran). */
export function privzetaOblika(format: FormatSrecanja | null | undefined): OblikaZapisnika {
  return format === 'SAVINJA' ? 'TURNIRKO' : 'NTZS'
}
