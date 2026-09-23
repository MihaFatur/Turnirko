/* Stanje registracijskega toka, ki mora preživeti preusmeritev na Stripe
   Checkout in nazaj (RegistracijaZakljucenaStran): komponenta okna se ob
   preusmeritvi odmontira, zato gre potrebni del stanja v sessionStorage.
   Gesla NIKOLI ne hrani - poslano je bilo zaledju pred preusmeritvijo in ob
   vrnitvi ni več potrebno (koda se potrdi brez njega, prijava sledi ročno). */
import type { CiklusPlacila, Paket } from '../api/tipi'

const KLJUC = 'turnirko-registracija-placilo'

export interface ShranjenoStanjePlacila {
  vloga: 'igralec' | 'organizator'
  paket: Paket
  ciklus: CiklusPlacila | null
  cena: number
  ime: string
  priimek: string
  email: string
  potrebnaKodaSkrbnika: boolean
}

export function shraniStanjePlacila(stanje: ShranjenoStanjePlacila) {
  try {
    sessionStorage.setItem(KLJUC, JSON.stringify(stanje))
  } catch {
    /* zasebno brskanje ali polna shramba - vrnitev pade nazaj na splosno
       sporocilo (glej RegistracijaZakljucenaStran) */
  }
}

/* Prebere in TAKOJ pobriše - stanje je za eno samo vrnitev, ne za vsak
   ponoven obisk te strani. */
export function preberiInPobrisiStanjePlacila(): ShranjenoStanjePlacila | null {
  try {
    const surovo = sessionStorage.getItem(KLJUC)
    sessionStorage.removeItem(KLJUC)
    return surovo ? (JSON.parse(surovo) as ShranjenoStanjePlacila) : null
  } catch {
    return null
  }
}
