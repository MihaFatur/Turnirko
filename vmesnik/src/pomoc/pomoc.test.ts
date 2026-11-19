/* Celovitost pomoči: vsaka povezava, vsak razdelek in vsak gumb »?« v kodi
   mora kazati na nekaj, kar obstaja. Pokvarjena povezava v navodilih je
   slabša od nobene — organizator jo odpre ravno takrat, ko mu nekaj ni jasno. */
import { describe, expect, it } from 'vitest'

import { isciPomoc } from './iskanje'
import { CLANKI, najdiClanek, POGLAVJA, razdelkiClanka, vsebinaNamiga } from './vsebina'
import type { Blok, Clanek } from './tipi'

const POVEZAVA = /\[([^\]]+)\]\(([^)]+)\)/g

function besedilaBloka(blok: Blok): string[] {
  switch (blok.tip) {
    case 'odstavek':
    case 'pazi':
    case 'nasvet':
      return [blok.besedilo]
    case 'koraki':
    case 'seznam':
      return blok.postavke
    case 'tabela':
      return blok.vrstice.flat()
    default:
      return []
  }
}

function povezaveClanka(clanek: Clanek): string[] {
  const besedila = [clanek.kratko, ...clanek.vsebina.flatMap(besedilaBloka)]
  return besedila.flatMap((b) => [...b.matchAll(POVEZAVA)].map((z) => z[2]))
}

function obstaja(cilj: string): boolean {
  const [id, razdelek] = cilj.split('#')
  const clanek = najdiClanek(id)
  if (!clanek) return false
  return !razdelek || razdelkiClanka(clanek).some((r) => r.id === razdelek)
}

describe('vsebina pomoči', () => {
  it('ima enolične ID-je člankov in razdelkov ter znana poglavja', () => {
    const idji = CLANKI.map((c) => c.id)
    expect(new Set(idji).size).toBe(idji.length)
    const poglavja = new Set(POGLAVJA.map((p) => p.id))
    for (const c of CLANKI) {
      expect(poglavja.has(c.poglavje), c.id).toBe(true)
      const razdelki = razdelkiClanka(c).map((r) => r.id)
      expect(new Set(razdelki).size, c.id).toBe(razdelki.length)
      expect(c.kratko.length, c.id).toBeGreaterThan(20)
    }
  })

  it('vsaka povezava in vsak »Povezano« kaže na obstoječ članek in razdelek', () => {
    for (const c of CLANKI) {
      for (const cilj of povezaveClanka(c)) expect(obstaja(cilj), `${c.id} → ${cilj}`).toBe(true)
      for (const id of c.povezani ?? []) expect(najdiClanek(id), `${c.id} → ${id}`).toBeDefined()
    }
  })

  it('organizatorjev članek ne kaže na članek samo za admina', () => {
    for (const c of CLANKI.filter((c) => !c.samoAdmin)) {
      const cilji = [...povezaveClanka(c), ...(c.povezani ?? [])].map((x) => x.split('#')[0])
      for (const id of cilji) expect(najdiClanek(id)?.samoAdmin ?? false, `${c.id} → ${id}`).toBe(false)
    }
  })

  it('poglavje za admina je samo za admina', () => {
    for (const c of CLANKI) expect(c.samoAdmin ?? false, c.id).toBe(c.poglavje === 'admin')
  })

  it('oblaček razdelka vzame naslov in besedilo razdelka', () => {
    const vsebina = vsebinaNamiga('zreb', 'mreza')
    expect(vsebina?.naslov).toBe('Mreža: nosilci čim dlje narazen')
    expect(vsebina?.shema).toBe('mreza-nosilcev')
    expect(vsebinaNamiga('zreb')?.naslov).toBe('Žreb')
    expect(vsebinaNamiga('ne-obstaja')).toBeNull()
  })
})

/* Gumbi »?« v kodi: <Namig clanek="…" razdelek="…" /> in <OznakaZNamigom …/>.
   Tudi izraza (clanek={x ? 'a' : 'b'}) se preverita po vseh nizih v njem. */
const IZVORNE = import.meta.glob('../**/*.tsx', { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>

function nizi(vrednost: string): string[] {
  const dobesedno = vrednost.match(/^"([^"]*)"$/)
  if (dobesedno) return [dobesedno[1]]
  /* Samo izida izraza ({x ? 'a' : 'b'}), ne nizi iz pogoja (zavihek === 'datumi'). */
  return [...vrednost.matchAll(/(?:^\{|[?:])\s*'([^']*)'/g)].map((z) => z[1])
}

describe('gumbi »?« v kodi', () => {
  const gumbi: { datoteka: string; clanki: string[]; razdelki: string[] }[] = []
  for (const [datoteka, koda] of Object.entries(IZVORNE)) {
    for (const oznaka of koda.matchAll(/<(?:Namig|OznakaZNamigom)\b([\s\S]*?)\/>/g)) {
      const atributi = oznaka[1]
      const vrednost = (ime: string) =>
        atributi.match(new RegExp(`\\b${ime}=("[^"]*"|\\{[^}]*\\})`))?.[1]
      const clanek = vrednost('clanek')
      /* Spremenljivka (okvir, ki članek prejme od strani) se preveri tam, kjer
         je zapisana: pri `pomoc="…"` spodaj. */
      if (!clanek || nizi(clanek).length === 0) continue
      const razdelek = vrednost('razdelek')
      gumbi.push({
        datoteka,
        clanki: nizi(clanek),
        razdelki: razdelek ? nizi(razdelek).filter(Boolean) : [],
      })
    }
    for (const okvir of koda.matchAll(/<ZrebOkvir\b[^>]*?\bpomoc="([^"]*)"/g)) {
      gumbi.push({ datoteka, clanki: [okvir[1]], razdelki: [] })
    }
  }

  it('jih je v aplikaciji vsaj trideset', () => {
    expect(gumbi.length).toBeGreaterThanOrEqual(30)
  })

  it('vsak kaže na obstoječ članek in razdelek', () => {
    for (const g of gumbi) {
      expect(g.clanki.length, g.datoteka).toBeGreaterThan(0)
      for (const id of g.clanki) expect(najdiClanek(id), `${g.datoteka}: ${id}`).toBeDefined()
      for (const r of g.razdelki) {
        const najden = g.clanki.some((id) => obstaja(`${id}#${r}`))
        expect(najden, `${g.datoteka}: ${g.clanki.join('|')}#${r}`).toBe(true)
      }
    }
  })
})

describe('iskanje po pomoči', () => {
  it('najde brez šumnikov in po besedah v poljubnem vrstnem redu', () => {
    const zadetki = isciPomoc(CLANKI, 'skupin zreb')
    expect(zadetki.map((z) => z.clanek.id)).toContain('zreb-skupin-tisk')
    expect(zadetki.map((z) => z.clanek.id)).toContain('zreb')
  })

  it('naslov članka šteje več kot omemba v besedilu', () => {
    expect(isciPomoc(CLANKI, 'prestavitev')[0].clanek.id).toBe('prestavitve')
  })

  it('zadetek pelje v razdelek, kjer so iskane besede', () => {
    const zadetek = isciPomoc(CLANKI, 'enakomerna razvrstitev').find((z) => z.clanek.id === 'razpored')
    expect(zadetek?.razdelek).toBe('enakomerna')
  })

  it('ključne besede najdejo izraze, ki jih v besedilu ni', () => {
    expect(isciPomoc(CLANKI, 'seed').map((z) => z.clanek.id)).toContain('nosilci')
  })

  it('prazno iskanje ne vrne ničesar', () => {
    expect(isciPomoc(CLANKI, '   ')).toEqual([])
  })
})
