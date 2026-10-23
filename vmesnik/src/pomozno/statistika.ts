/* Dogodki za statistiko obiska (Umami na lastnem strežniku, glej docs/OBJAVA.md,
   razdelek 8). Umami sam šteje samo oglede strani; tu so dogodki, iz katerih se
   vidi pot do računa in Premium: kdo je videl poziv, kdo je začel registracijo,
   na katerem koraku je odnehal in kdo jo je končal.

   Pravila:
   - Brez osebnih podatkov: nikoli imena, e-pošte, id-ja igralca ali računa.
     Lastnosti so samo kratke oznake (izvor, korak, skupina).
   - Skript Umamija se naloži z `defer` z drugega strežnika in v razvoju sploh
     ne šteje (data-domains). Klic pred njegovim prihodom gre v kratko vrsto,
     ki se izprazni, ko `window.umami` obstaja; brez njega (izklopljena
     statistika, blokada, brez interneta) se dogodek tiho izgubi.
   - Napaka v statistiki ne sme nikoli ustaviti vmesnika. */

type Lastnosti = Record<string, string | number | boolean>

interface Umami {
  track: (ime: string, lastnosti?: Lastnosti) => void
  identify?: (lastnosti: Lastnosti) => void
}

declare global {
  interface Window {
    umami?: Umami
  }
}

/* Od kod je gledalec odprl registracijo - lastnost vsakega dogodka registracije. */
export type IzvorRegistracije = 'meni' | 'prijava' | 'maskota' | 'oglas' | 'poziv' | 'profil' | 'spremljanje'

const vrsta: ((u: Umami) => void)[] = []
const NAJVEC_V_VRSTI = 30
const POSKUSOV = 20
const RAZMIK_MS = 500

function izvedi(klic: (u: Umami) => void) {
  const umami = typeof window === 'undefined' ? undefined : window.umami
  if (umami) {
    try {
      klic(umami)
    } catch {
      /* statistika ne sme ustaviti vmesnika */
    }
    return
  }
  if (vrsta.length >= NAJVEC_V_VRSTI) return
  vrsta.push(klic)
  if (vrsta.length === 1) pocakaj(0)
}

function pocakaj(poskus: number) {
  setTimeout(() => {
    const umami = window.umami
    if (umami) {
      for (const klic of vrsta.splice(0)) {
        try {
          klic(umami)
        } catch {
          /* kot zgoraj */
        }
      }
    } else if (poskus < POSKUSOV) {
      pocakaj(poskus + 1)
    } else {
      vrsta.length = 0
    }
  }, RAZMIK_MS)
}

/* Dogodek z imenom v obliki »registracija-korak«. Vsak dogodek nosi tudi
   skupino poskusa s pozivom (`poziv`), da se registracije obeh skupin dajo
   primerjati. */
export function zabelezi(ime: string, lastnosti: Lastnosti = {}) {
  izvedi((u) => u.track(ime, { poziv: skupinaPoziva(), ...lastnosti }))
}

/* ---------- Poskus: poziv k računu ----------

   Polovica gostov dobi okno s pozivom k računu po 30 s (PozivRacuna.tsx),
   polovica ne. Samo tako se vidi, ali okno ljudi pripelje do računa ALI jih
   odžene: primerja se delež registracij in delež vrnjenih obiskovalcev med
   skupinama (seja v Umamiju nosi skupino prek `identify`). Skupina se izžreba
   enkrat na brskalnik in obstane. Ko je odločeno, DELEZ_Z_OKNOM postane 1
   (vsi) ali 0 (nihče). */
export const DELEZ_Z_OKNOM = 0.5

export type SkupinaPoziva = 'okno' | 'brez'

const KLJUC_SKUPINE = 'turnirko-poziv-skupina'
let skupinaSeje: SkupinaPoziva | null = null

export function skupinaPoziva(): SkupinaPoziva {
  if (skupinaSeje) return skupinaSeje
  try {
    const shranjena = localStorage.getItem(KLJUC_SKUPINE)
    if (shranjena === 'okno' || shranjena === 'brez') {
      skupinaSeje = shranjena
      return shranjena
    }
  } catch {
    /* brez shrambe se skupina izžreba za to nalaganje strani */
  }
  const nova: SkupinaPoziva = Math.random() < DELEZ_Z_OKNOM ? 'okno' : 'brez'
  skupinaSeje = nova
  try {
    localStorage.setItem(KLJUC_SKUPINE, nova)
  } catch {
    /* kot zgoraj */
  }
  return nova
}

/* Seja v Umamiju dobi skupino poskusa (enkrat na nalaganje strani). */
let sejaOznacena = false
export function oznaciSejo() {
  if (sejaOznacena) return
  sejaOznacena = true
  izvedi((u) => u.identify?.({ poziv: skupinaPoziva() }))
}
