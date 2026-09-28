/* Umestitev nastopa maskote glede na DEJANSKO glavo strani.

   Platno nastopa je vedno čez celo glavo (namizni masthead ali 56 px visoka
   vrstica telefona), lik pa stoji na črti pod vrstico. Kje natanko, je odvisno
   od stvari, ki jih ni mogoče vnaprej vedeti: kje se konča logotip, koliko
   prostora je do oznake uporabnika, kje stoji »Domov«, je odvisno od širine
   okna, števila povezav in pisave. Zato se ob vsakem nastopu izmeri glava in
   iz nje izračuna, ali se prizor sploh prilega; če se ne (preozko okno,
   predolg napis), se ne pokaže — bolje nič kot zarezana tabla. Vse mere so v
   pikslih strani, glede na zgornji levi kot glave. */
import type { Glava, Naprava, PrizorPodatki, Umestitev } from './animatorMaskote'
import { PISAVA_NAPISA } from './napisiMaskote'

/* Merilo, v katerem se lik izriše, in velikost besedila na tabli v enotah lika.
   Namizje: lik 0,92, napis 13 (= ~12 px na zaslonu, najmanjša velikost
   besedila po DESIGN.md). Glava telefona je nižja (56 px proti 68), zato je lik
   manjši (0,75), napis pa večji (16 enot), da je na zaslonu spet 12 px; tabla
   je višja (22 enot), da besedilo diha. Zastavica letala je na obeh napravah
   0,92 z napisom 13: letalo ne potrebuje višine lika. */
export interface MeraLika {
  merilo: number
  pisava: number
  visinaTable: number
}

export const MERA_LIKA: Record<Naprava, MeraLika> = {
  namizje: { merilo: 0.92, pisava: PISAVA_NAPISA, visinaTable: 19 },
  telefon: { merilo: 0.75, pisava: 16, visinaTable: 22 },
}

export const MERILO_LIKA = MERA_LIKA.namizje.merilo

/* Tabla je za napisom daljša za naglasni trak in zrak ob njem (lokalne enote). */
export const DODATEK_TABLE = 30

/* Letalo: razdalje od njegove sredine (px). Nos sega za `nos` naprej, rep za
   `rep` nazaj — do konca vrvice, na kateri visi zastavica. */
export const LETALO = { nos: 34, rep: 43 }

/* Lestev (samo namizje): `visina` od zgornjega roba strani, `polSirine` med
   vrvema in razmik med prečkami. Sega pod črto glave (visoka je 68 px) v prazen
   zgornji rob vsebine: lik pleza do sredine (88 px), kjer je pod tablo, ki
   prekriva »Lestvica« (sega do ~58 px), in ima prostor, da se ga vidi. Platno
   je pri tem prizoru višje od glave (`visina + rezerva`). */
export const LESTEV = { visina: 176, polSirine: 15, razmik: 15, rezerva: 16 }
/* Kje stojijo stopala lika, ko je na sredini lestve (px od vrha). */
export const LESTEV_STOPALA = LESTEV.visina / 2 + 20

/* Višina lika od vrha table do tal (enote lika): telo in glava 44,5 + tabla. */
const VISINA_LIKA_BREZ_TABLE = 44.5

/* Prostor pod zgornjim robom platna, ki ga tabla sme zasesti pri skoku, nagibu
   in prenagljenem razgrinjanju (px zaslona). Iz njega se izračuna največji
   nagib table. */
const REZERVA_ZGORAJ = 4.5

/* Najožje platno lika ob oznaki uporabnika (namizje). */
const SIRINA_NAJMANJ = 140

/* Prazen prostor med likom in oznako uporabnika oz. navigacijo (px). */
const ZRAK = 8

/* Lik s tablo nad glavo stoji tik levo od oznake uporabnika: v prizoru
   »zogica« pa je med likom in znakom (ki nastane na mestu oznake) toliko px,
   da žogica že leti, ko znak še ne obstaja. */
const LIK_LEVO_OD_ZNAKA = 41
const POL_SIRINE_LIKA = 20

/* Pas zastavice: širina pasu ~12 enot, najmanj 8 pasov. Širši pasovi so ob
   valu videti kot stopnice v napisu. */
export function steviloPasov(sirinaTable: number): number {
  return Math.max(8, Math.ceil(sirinaTable / 12))
}

/* Izmeri glavo (namizno ali telefonsko); null, če manjka kateri od elementov —
   na telefonu tudi, ko glava ne kaže oznake uporabnika (podstran s puščico
   nazaj) ali ko stran vanjo vloži svoja dejanja: tam je prostor zaseden. */
export function izmeriGlavo(): Glava | null {
  const telefon = document.querySelector('.glava-telefon')
  const glava = telefon ?? document.querySelector('.glava')
  if (!glava) return null

  const oznaka = glava.querySelector('.glava__uporabnik')
  const crta = glava.querySelector(telefon ? '.glava-telefon__crta' : '.glava__crta')
  if (!oznaka || !crta) return null

  const g = glava.getBoundingClientRect()
  const o = oznaka.getBoundingClientRect()
  const skupno = {
    sirina: g.width,
    tla: crta.getBoundingClientRect().top - g.top,
    oznakaLevo: o.left - g.left,
    oznakaDesno: o.right - g.left,
    oznakaY: (o.top + o.bottom) / 2 - g.top,
  }

  if (telefon) {
    const vrsta = glava.querySelector('.glava-telefon__vrsta')
    /* Skrajno levo: logotip ali puščica nazaj. */
    const levo = vrsta?.firstElementChild
    const dejanja = glava.querySelector('.glava-telefon__dejanja')
    if (!vrsta || !levo || (dejanja && dejanja.childElementCount > 0)) return null
    const l = levo.getBoundingClientRect()
    const v = vrsta.getBoundingClientRect()
    return {
      naprava: 'telefon',
      ...skupno,
      logotipDesno: l.right - g.left,
      navDesno: l.right - g.left,
      domovLevo: g.width,
      vrsticaSredina: (v.top + v.bottom) / 2 - g.top,
    }
  }

  const logotip = glava.querySelector('.glava__logotip')
  const navigacija = glava.querySelector('.glava__navigacija')
  const domov = navigacija?.querySelector('a[href="/"]')
  if (!logotip || !navigacija || !domov) return null
  const z = logotip.getBoundingClientRect()
  const n = navigacija.getBoundingClientRect()
  const d = domov.getBoundingClientRect()
  const l = navigacija.querySelector('a[href="/lestvica"]')?.getBoundingClientRect()
  return {
    naprava: 'namizje',
    ...skupno,
    logotipDesno: z.right - g.left,
    navDesno: n.right - g.left,
    domovLevo: d.left - g.left,
    lestvicaSredina: l ? (l.left + l.right) / 2 - g.left : undefined,
    vrsticaSredina: (d.top + d.bottom) / 2 - g.top,
  }
}

export interface PotLetala {
  /* Okno, v katerem je letalo vidno: levo od njega ga zakriva logotip, desno
     povezava »Domov« (na telefonu rob zaslona). */
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
   vsaj kratek čas cela vidna, sicer se napis ne prebere.

   Okno je na namizju med logotipom in »Domov«, na telefonu od logotipa do roba
   zaslona. Če je dovolj široko za letalo IN zastavico, je letalo ves čas v
   celoti vidno. Če je široko le za zastavico (telefon; ozko namizno okno), letalo,
   ko je zastavica cela vidna, že zapušča okno (kot vlečna zastavica na plaži):
   vidna ostane zastavica, letalo pa se je pokazalo, ko je izza logotipa izplulo. */
export function potLetala(glava: Glava, sirinaTable: number): PotLetala | null {
  const sirinaZastavice = sirinaTable * MERILO_LIKA
  const xLevo = glava.logotipDesno + 4
  const xDesno = glava.naprava === 'telefon' ? glava.sirina : glava.domovLevo - 6
  const y = glava.vrsticaSredina - 3
  const x0 = xLevo - LETALO.nos
  const okno = xDesno - xLevo

  const x1 = xLevo + 6 + LETALO.rep + sirinaZastavice
  const x3 = xDesno + LETALO.rep + sirinaZastavice + 4

  if (okno >= LETALO.nos + LETALO.rep + sirinaZastavice + 12) {
    /* Počasen prelet po polni vidnosti; nos ostane 10 px pred oknom. */
    const x2 = Math.max(x1, Math.min(x1 + 60, xDesno - LETALO.nos - 10))
    return { xLevo, xDesno, y, sirinaZastavice, x0, x1, x2, x3 }
  }
  if (okno >= sirinaZastavice + 12) {
    /* Zastavica ne sme čez rob že med počasnim preletom. */
    const x2 = Math.min(x1 + 40, xDesno + LETALO.rep - 2)
    return { xLevo, xDesno, y, sirinaZastavice, x0, x1, x2, x3 }
  }
  return null
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
   šale, a le za dobre tri sekunde. Samo namizje: na telefonu ni ne povezave ne
   prostora pod glavo. */
export function potLestve(glava: Glava, sirinaTable: number): PotLestve | null {
  if (glava.naprava !== 'namizje' || glava.lestvicaSredina === undefined) return null
  const sirinaZnaka = sirinaTable * MERILO_LIKA
  const x = glava.lestvicaSredina
  if (x - sirinaZnaka / 2 < 4 || x + sirinaZnaka / 2 > glava.sirina - 4) return null
  return { x, yZnak: glava.vrsticaSredina, sirinaZnaka }
}

/* Vodoravna sredina lika s tablo nad glavo ali null, če ni prostora.

   Namizje: lik stoji tik levo od oznake uporabnika, prostor je med navigacijo
   in njo. Telefon: prostor je od logotipa do roba zaslona (oznake uporabnika
   takrat ni, prizor jo skrije), lik pa stoji na sredini tega prostora, kolikor
   dovoli tabla. */
export function umestiLik(glava: Glava, sirinaTable: number, merilo: number): number | null {
  const sirinaTablePx = Math.ceil(sirinaTable * merilo)

  if (glava.naprava === 'namizje') {
    const sirina = Math.max(SIRINA_NAJMANJ, sirinaTablePx + 24)
    /* 16 px zraka do oznake uporabnika (osem od roba lika, osem od navigacije). */
    if (glava.oznakaLevo - glava.navDesno < sirina + 2 * ZRAK) return null
    return glava.oznakaLevo - ZRAK - sirina / 2
  }

  const levo = glava.logotipDesno + ZRAK
  const desno = glava.sirina - 6
  if (desno - levo < sirinaTablePx) return null
  const sredina = (levo + desno) / 2
  return Math.min(desno - sirinaTablePx / 2, Math.max(levo + sirinaTablePx / 2, sredina))
}

/* Prizor »zogica« na namizju: znak nastane na mestu oznake uporabnika (desni
   rob sta oznaka in znak skupna), lik pa stoji levo od njega. Vrne vodoravno
   sredino lika ali null, če se ne prilega med navigacijo in oznako. */
export function umestiZnakNaOznaki(glava: Glava, sirinaTable: number): number | null {
  if (glava.naprava !== 'namizje') return null
  const znakLevo = glava.oznakaDesno - sirinaTable * MERILO_LIKA
  const x = znakLevo - LIK_LEVO_OD_ZNAKA
  if (x - POL_SIRINE_LIKA < glava.navDesno + ZRAK) return null
  return x
}

/* Vse, kar igra en nastop: kateri prizor, kateri napis, kako široka je tabla,
   kje stoji in koliko sme nagibati. */
export interface Nastop {
  prizor: PrizorPodatki
  napis: string
  /* Širina table v enotah lika. */
  sirinaTable: number
  nagib: number
  umestitev: Umestitev
}

/* Iz izmerjenega napisa in glave sestavi nastop ali vrne null, če se prizor v
   glavo ne prilega (ne velja za to napravo, preozko okno, predolg napis,
   manjka povezava). `sirinaBesedila` je izmerjena pri velikosti PISAVA_NAPISA;
   na telefonu je besedilo večje, zato tabla širša. Širša tabla ima pri istem
   nagibu višji vogal, zato se nagib zmanjša: vogal se ne sme dvigniti čez
   rezervo. */
export function sestaviNastop(
  prizor: PrizorPodatki,
  napis: string,
  sirinaBesedila: number,
  glava: Glava,
): Nastop | null {
  if (!prizor.naprave.includes(glava.naprava)) return null

  const mera = MERA_LIKA[glava.naprava]
  const jeLetalo = prizor.slika === 'letalo'
  const merilo = jeLetalo ? MERILO_LIKA : mera.merilo
  const pisava = jeLetalo ? PISAVA_NAPISA : mera.pisava
  const sirinaTable = Math.ceil((sirinaBesedila * pisava) / PISAVA_NAPISA) + DODATEK_TABLE

  let x = 0
  let visina = glava.tla
  if (jeLetalo) {
    if (!potLetala(glava, sirinaTable)) return null
  } else if (prizor.slika === 'lestev') {
    if (!potLestve(glava, sirinaTable)) return null
    visina = LESTEV.visina + LESTEV.rezerva
  } else {
    const xLika = prizor.znakNaOznaki
      ? umestiZnakNaOznaki(glava, sirinaTable)
      : umestiLik(glava, sirinaTable, merilo)
    if (xLika === null) return null
    x = xLika
  }

  const prostor = glava.tla - (VISINA_LIKA_BREZ_TABLE + mera.visinaTable) * merilo
  const rezerva = Math.min(REZERVA_ZGORAJ, Math.max(1, prostor - 1))
  const polSirine = (sirinaTable * merilo) / 2
  const nagib = Math.min(
    4.5,
    Math.max(1, (Math.asin(Math.min(1, rezerva / polSirine)) * 180) / Math.PI),
  )

  return {
    prizor,
    napis,
    sirinaTable,
    nagib,
    umestitev: {
      sirina: glava.sirina,
      visina,
      glava,
      x,
      merilo,
      pisava,
      visinaTable: mera.visinaTable,
    },
  }
}
