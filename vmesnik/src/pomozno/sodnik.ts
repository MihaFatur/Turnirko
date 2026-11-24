/* Pravila strani za sodnika (S2, /s): od predloga, ki ga prebere strežnik, do
   izida, ki ga sodnik potrdi. Sodnik vpiše samo odigrano tekmo s točkami po
   nizih; predajo, brez boja in diskvalifikacijo vpiše organizator na pultu.

   Predlog je v straneh tekme (1 = igralec1), kot ga dobi organizator. */
import type { PredlogIzidaDto, VnosRezultata } from '../api/tipi'
import { preveriNize, veljavenNiz } from '../komponente/TockeNizov'

/* Ena vrstica niza na zaslonu sodnika (besedilo, ker je prazno polje veljavno
   stanje med tipkanjem). */
export interface VrsticaSodnika {
  tocke1: string
  tocke2: string
}

/* Toliko vrstic, kolikor nizov ima tekma (najboljši od N); prebrane točke so
   vpisane, neprebrane prazne. */
export function vrsticeSodnika(predlog: PredlogIzidaDto | null, steviloNizov: number): VrsticaSodnika[] {
  return Array.from({ length: steviloNizov }, (_, i) => {
    const n = predlog?.nizi[i]
    return {
      tocke1: n?.tocke1 == null ? '' : String(n.tocke1),
      tocke2: n?.tocke2 == null ? '' : String(n.tocke2),
    }
  })
}

/* Mesta (niz × 2 + stran), ki jih mora sodnik primerjati z listkom (obarvana,
   opozorilo s številom): negotove in neprebrane celice ter celice neveljavnega
   niza - samo v nizih, ki so bili odigrani (vrstica ni prazna). */
export function celiceZaPotrditev(predlog: PredlogIzidaDto | null): Set<number> {
  const mesta = new Set<number>()
  predlog?.nizi.forEach((n, i) => {
    const prazna = n.tocke1 === null && n.tocke2 === null
    if (prazna) return
    if (n.negotovo1 || n.neveljaven || n.tocke1 === null) mesta.add(i * 2)
    if (n.negotovo2 || n.neveljaven || n.tocke2 === null) mesta.add(i * 2 + 1)
  })
  return mesta
}

export interface IzidSodnika {
  /* Vnos za strežnik ali null, dokler izid ni popoln in veljaven. */
  vnos: VnosRezultata | null
  /* Razlog, zakaj izida še ni mogoče potrditi; null, ko je vse v redu. */
  napaka: string | null
  /* Dobljeni nizi, kadar so vsi vpisani nizi veljavni. */
  dobljeni: { prvi: number; drugi: number } | null
}

/* Izid iz vrstic: odigrani nizi so izpolnjene vrstice od prve naprej, prazne
   vrstice na koncu so neodigrani nizi. Pravila niza in konca tekme so ista kot
   pri ročnem vnosu (preveriNize, v zaledju NiziPravila). */
export function izidSodnika(vrstice: VrsticaSodnika[], steviloNizov: number): IzidSodnika {
  const zaZmago = Math.floor(steviloNizov / 2) + 1
  const nizi: { tocke1: number; tocke2: number }[] = []
  let konec = false
  for (let i = 0; i < vrstice.length; i++) {
    const v = vrstice[i]
    const prazna = v.tocke1 === '' && v.tocke2 === ''
    if (prazna) {
      konec = true
      continue
    }
    if (konec) return { vnos: null, napaka: `${i}. niz je prazen, ${i + 1}. pa ne. Popravi vrstni red nizov.`, dobljeni: null }
    if (v.tocke1 === '' || v.tocke2 === '') {
      return { vnos: null, napaka: `Vpiši obe števili ${i + 1}. niza.`, dobljeni: null }
    }
    const a = Number(v.tocke1)
    const b = Number(v.tocke2)
    if (!Number.isInteger(a) || !Number.isInteger(b)) {
      return { vnos: null, napaka: `${i + 1}. niz mora imeti cela števila.`, dobljeni: null }
    }
    if (!veljavenNiz(a, b)) {
      return { vnos: null, napaka: `${i + 1}. niz (${a}:${b}) ni veljaven rezultat.`, dobljeni: null }
    }
    nizi.push({ tocke1: a, tocke2: b })
  }
  if (nizi.length === 0) return { vnos: null, napaka: 'Vpiši točke odigranih nizov.', dobljeni: null }
  const prvi = nizi.filter((n) => n.tocke1 > n.tocke2).length
  const drugi = nizi.length - prvi
  const dobljeni = { prvi, drugi }
  if (Math.max(prvi, drugi) < zaZmago) {
    return { vnos: null, napaka: `Zmagovalec mora dobiti ${zaZmago} nize. Vpiši vse odigrane nize.`, dobljeni }
  }
  // niz po odločeni tekmi (in s tem preveč dobljenih nizov) najde preveriNize
  const napaka = preveriNize(nizi, prvi, drugi, zaZmago)
  if (napaka) return { vnos: null, napaka, dobljeni }
  if (Math.max(prvi, drugi) !== zaZmago) {
    return { vnos: null, napaka: `Zmagovalec mora dobiti natanko ${zaZmago} nize.`, dobljeni }
  }
  return {
    vnos: { izidTip: null, dobljeniNizi1: prvi, dobljeniNizi2: drugi, zmagovalecStran: null, nizi, steviloNizov: null },
    napaka: null,
    dobljeni,
  }
}

/* »Zmagal je Žan Kovačič 3:1« - zmagovalčevi nizi so vedno prvi. */
export function besediloZmagovalca(
  dobljeni: { prvi: number; drugi: number },
  igralec1: string | null,
  igralec2: string | null,
): string {
  const prviZmagal = dobljeni.prvi > dobljeni.drugi
  const ime = (prviZmagal ? igralec1 : igralec2) ?? (prviZmagal ? 'prvi igralec' : 'drugi igralec')
  const izid = prviZmagal ? `${dobljeni.prvi}:${dobljeni.drugi}` : `${dobljeni.drugi}:${dobljeni.prvi}`
  return `Zmagal je ${ime} ${izid}`
}

/* Miza iz polja: 1-99 ali prazno; karkoli drugega je napaka. */
export function mizaIzPolja(vnos: string): { miza: number | null; veljavna: boolean } {
  if (vnos.trim() === '') return { miza: null, veljavna: true }
  const n = Number(vnos)
  return Number.isInteger(n) && n >= 1 && n <= 99 ? { miza: n, veljavna: true } : { miza: null, veljavna: false }
}
