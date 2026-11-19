/* Iskanje po pomoči. Isto pravilo kot iskalniki igralcev (`pomozno/iskanje`):
   brez šumnikov in po besedah v poljubnem vrstnem redu — »zreb skupin« najde
   »Žreb skupin za tiskanje«. Zadetek pove tudi RAZDELEK, v katerem je beseda,
   da klik pelje točno tja in ne na vrh dolgega članka. */
import { besedeIskanja, zaIskanje } from '../pomozno/iskanje'
import type { Blok, Clanek } from './tipi'

/* Besedilo brez oznak: **krepko** → krepko, [besedilo](cilj) → besedilo. */
export function golBesedilo(besedilo: string): string {
  return besedilo.replace(/\*\*(.+?)\*\*/g, '$1').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
}

function besediloBloka(blok: Blok): string {
  switch (blok.tip) {
    case 'odstavek':
    case 'pazi':
    case 'nasvet':
      return blok.besedilo
    case 'koraki':
    case 'seznam':
      return blok.postavke.join(' ')
    case 'kje':
      return blok.pot.join(' ')
    case 'tabela':
      return [...blok.glava, ...blok.vrstice.flat()].join(' ')
    case 'shema':
      return blok.opis ?? ''
    case 'razdelek':
      return [blok.naslov, blok.kratko ?? ''].join(' ')
  }
}

interface Odsek {
  razdelek?: string
  besedilo: string
  /* Za odlomek pod zadetkom: gol, s šumniki. */
  izvirno: string
  utez: number
}

/* Teže: naslov članka največ, nato ključne besede (izrazi, ki jih v besedilu
   ni), naslov razdelka, povzetka, telo. */
function odsekiClanka(clanek: Clanek): Odsek[] {
  const odseki: Odsek[] = []
  const dodaj = (besedilo: string, utez: number, razdelek?: string) => {
    const gol = golBesedilo(besedilo)
    if (gol.trim()) odseki.push({ razdelek, besedilo: zaIskanje(gol), izvirno: gol, utez })
  }
  dodaj(clanek.naslov, 10)
  if (clanek.kljucne?.length) dodaj(clanek.kljucne.join(' · '), 7)
  dodaj(clanek.kratko, 4)
  let razdelek: string | undefined
  for (const blok of clanek.vsebina) {
    if (blok.tip === 'razdelek') {
      razdelek = blok.id
      dodaj(blok.naslov, 6, razdelek)
      if (blok.kratko) dodaj(blok.kratko, 4, razdelek)
    } else {
      dodaj(besediloBloka(blok), 1, razdelek)
    }
  }
  return odseki
}

export interface Zadetek {
  clanek: Clanek
  razdelek?: string
  odlomek: string
  tocke: number
}

const DOLZINA_ODLOMKA = 150

/* Odlomek okoli prve najdene besede, da se vidi, zakaj je članek zadel. */
function odlomekOkoli(izvirno: string, beseda: string): string {
  const mesto = zaIskanje(izvirno).indexOf(beseda)
  if (izvirno.length <= DOLZINA_ODLOMKA || mesto < 0) return izvirno
  const zacetek = Math.max(0, mesto - 40)
  const konec = Math.min(izvirno.length, zacetek + DOLZINA_ODLOMKA)
  return (zacetek > 0 ? '… ' : '') + izvirno.slice(zacetek, konec).trim() + (konec < izvirno.length ? ' …' : '')
}

export function isciPomoc(clanki: Clanek[], iskanje: string): Zadetek[] {
  const besede = besedeIskanja(iskanje)
  if (besede.length === 0) return []
  const zadetki: Zadetek[] = []
  for (const clanek of clanki) {
    const odseki = odsekiClanka(clanek)
    let tocke = 0
    let najboljsi: Odsek | undefined
    let vse = true
    for (const beseda of besede) {
      const zadeti = odseki.filter((o) => o.besedilo.includes(beseda))
      if (zadeti.length === 0) {
        vse = false
        break
      }
      const naj = zadeti.reduce((a, b) => (b.utez > a.utez ? b : a))
      tocke += naj.utez
      if (!najboljsi || naj.utez > najboljsi.utez) najboljsi = naj
    }
    if (!vse || !najboljsi) continue
    /* Razdelek: tisti, v katerem je največ iskanih besed — tako »popravek
       skupine« pelje v razdelek o skupini in ne na vrh članka. */
    const poRazdelkih = new Map<string, number>()
    for (const o of odseki) {
      if (!o.razdelek) continue
      const n = besede.filter((b) => o.besedilo.includes(b)).length * o.utez
      if (n > 0) poRazdelkih.set(o.razdelek, (poRazdelkih.get(o.razdelek) ?? 0) + n)
    }
    const razdelek = [...poRazdelkih.entries()].sort((a, b) => b[1] - a[1])[0]?.[0]
    /* Odlomek je stavek: ne naslov (ta stoji že nad njim) in ne seznam ključnih
       besed (teža 7). Prednost ima odsek z največ iskanimi besedami, sicer povzetek. */
    const stavki = odseki.filter((o) => o.utez !== 10 && o.utez !== 7)
    const zaOdlomek = stavki
      .map((o) => ({ o, n: besede.filter((b) => o.besedilo.includes(b)).length }))
      .filter((x) => x.n > 0)
      .sort((a, b) => b.n - a.n || b.o.utez - a.o.utez)[0]?.o
    const prva = besede.find((b) => zaOdlomek?.besedilo.includes(b)) ?? besede[0]
    zadetki.push({
      clanek,
      razdelek,
      odlomek: odlomekOkoli(zaOdlomek?.izvirno ?? golBesedilo(clanek.kratko), prva),
      tocke,
    })
  }
  return zadetki.sort((a, b) => b.tocke - a.tocke)
}
