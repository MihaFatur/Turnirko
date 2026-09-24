/* Umestitev nastopa maskote glede na DEJANSKO glavo strani.

   Prizora čez celo glavo (letalo, lestev) se vežeta na elemente, ki jih ni
   mogoče vnaprej vedeti: kje se konča logotip, kje se začne »Domov«, kje stoji
   »Lestvica«, je odvisno od širine okna, števila povezav in pisave. Zato se ob
   vsakem nastopu izmeri glava in iz nje izračuna pot; če se prizor ne prilega
   (preozko okno, predolg napis), se ne pokaže — bolje nič kot zarezana tabla.
   Vse mere so v pikslih strani, glede na zgornji levi kot glave. */
import type { Glava } from './animatorMaskote'

/* Lik je pomanjšan, da ima tabla nad glavo prostor za skok in nagib; napis se
   izriše v pisavi 13 (× 0,92 = ~12 px na zaslonu, najmanjša velikost besedila
   po DESIGN.md). Isto merilo velja za tablo, zastavico in znak na lestvi. */
export const MERILO_LIKA = 0.92

/* Tabla je za napisom daljša za naglasni trak in zrak ob njem (lokalne enote). */
export const DODATEK_TABLE = 30

/* Letalo: razdalje od njegove sredine (px). Nos sega za `nos` naprej, rep za
   `rep` nazaj — do konca vrvice, na kateri visi zastavica. */
export const LETALO = { nos: 34, rep: 43, visina: 70 }

/* Lestev: `visina` od zgornjega roba strani, `polSirine` med vrvema in razmik
   med prečkami. Sega pod črto glave (visoka je 68 px) v prazen zgornji rob
   vsebine: lik pleza do sredine (88 px), kjer je pod tablo, ki prekriva
   »Lestvica« (sega do ~58 px), in ima prostor, da se ga vidi. */
export const LESTEV = { visina: 176, polSirine: 15, razmik: 15, rezerva: 16 }
/* Kje stojijo stopala lika, ko je na sredini lestve (px od vrha). */
export const LESTEV_STOPALA = LESTEV.visina / 2 + 20

/* Pas zastavice: širina pasu ~12 enot, najmanj 8 pasov. Širši pasovi so ob
   valu videti kot stopnice v napisu. */
export function steviloPasov(sirinaTable: number): number {
  return Math.max(8, Math.ceil(sirinaTable / 12))
}

/* Izmeri glavo; null, če manjka kateri od elementov (npr. telefonska glava). */
export function izmeriGlavo(): Glava | null {
  const glava = document.querySelector('.glava')
  if (!glava) return null
  const logotip = glava.querySelector('.glava__logotip')
  const domov = glava.querySelector('.glava__navigacija a[href="/"]')
  const lestvica = glava.querySelector('.glava__navigacija a[href="/lestvica"]')
  if (!logotip || !domov || !lestvica) return null

  const g = glava.getBoundingClientRect()
  const z = logotip.getBoundingClientRect()
  const d = domov.getBoundingClientRect()
  const l = lestvica.getBoundingClientRect()
  return {
    sirina: g.width,
    logotipDesno: z.right - g.left,
    domovLevo: d.left - g.left,
    lestvicaSredina: (l.left + l.right) / 2 - g.left,
    vrsticaSredina: (d.top + d.bottom) / 2 - g.top,
  }
}

export interface PotLetala {
  /* Okno, v katerem je letalo vidno: levo od njega ga zakriva logotip, desno
     povezava »Domov«. */
  xLevo: number
  xDesno: number
  /* Navpična sredina letala. */
  y: number
  /* Širina zastavice v px. */
  sirinaZastavice: number
  /* Vodoravna lega sredine letala: skrito za logotipom, zastavica cela v
     oknu, zastavica še cela v oknu po počasnem preletu, vse za povezavo. */
  x0: number
  x1: number
  x2: number
  x3: number
}

/* Pot letala ali null, če se zastavica ne prilega v okno. Zastavica MORA biti
   vsaj kratek čas cela vidna, sicer se napis ne prebere; zato okno ne sme biti
   ožje od letala z zastavico in nekaj zraka. */
export function potLetala(glava: Glava, sirinaTable: number): PotLetala | null {
  const sirinaZastavice = sirinaTable * MERILO_LIKA
  const xLevo = glava.logotipDesno + 4
  const xDesno = glava.domovLevo - 6
  const potrebno = LETALO.nos + LETALO.rep + sirinaZastavice + 12
  if (xDesno - xLevo < potrebno) return null

  const x0 = xLevo - LETALO.nos
  const x1 = xLevo + 6 + LETALO.rep + sirinaZastavice
  /* Počasen prelet po polni vidnosti; nos ostane 10 px pred oknom. */
  const x2 = Math.max(x1, Math.min(x1 + 60, xDesno - LETALO.nos - 10))
  const x3 = xDesno + LETALO.rep + sirinaZastavice + 4
  return { xLevo, xDesno, y: glava.vrsticaSredina - 3, sirinaZastavice, x0, x1, x2, x3 }
}

export interface PotLestve {
  /* Vodoravna sredina lestve in table (sredina povezave »Lestvica«). */
  x: number
  /* Sredina table po višini = sredina povezave, da jo prekrije. */
  yZnak: number
  sirinaZnaka: number
}

/* Tabla, široka kot napis, se mora prilegati glavi: sicer bi štrlela čez rob
   strani. Širša od povezave je vedno in prekrije tudi sosede — to je del
   šale, a le za dobre tri sekunde. */
export function potLestve(glava: Glava, sirinaTable: number): PotLestve | null {
  const sirinaZnaka = sirinaTable * MERILO_LIKA
  const x = glava.lestvicaSredina
  if (x - sirinaZnaka / 2 < 4 || x + sirinaZnaka / 2 > glava.sirina - 4) return null
  return { x, yZnak: glava.vrsticaSredina, sirinaZnaka }
}
