/* Oznaka skupine: številke od 1 naprej (splošni turnirji) in črke po slovenski
   abecedi brez Č, Š, Ž (samo SV regija), enako kot v zaledju (OznakaSkupineTest). */
import { describe, expect, it } from 'vitest'

import { crkaPoVrsti, oznakaSkupine, primerjajSkupine } from './oznakaSkupine'

describe('oznakaSkupine', () => {
  it('skupine so oštevilčene od 1', () => {
    expect(oznakaSkupine(0)).toBe('1')
    expect(oznakaSkupine(1)).toBe('2')
    expect(oznakaSkupine(16)).toBe('17')
  })
})

describe('primerjajSkupine', () => {
  const urejene = (oznake: string[]) =>
    oznake.map((oznaka) => ({ oznaka })).sort(primerjajSkupine).map((s) => s.oznaka)

  it('2 je pred 10', () => {
    expect(urejene(['10', '2', '1', '11', '3'])).toEqual(['1', '2', '3', '10', '11'])
  })

  it('starejše črke ostanejo abecedno urejene', () => {
    expect(urejene(['C', 'A', 'B'])).toEqual(['A', 'B', 'C'])
  })
})

describe('crkaPoVrsti (SV regija)', () => {
  it('po P pride R, ne Q', () => {
    expect(crkaPoVrsti(15)).toBe('P')
    expect(crkaPoVrsti(16)).toBe('R')
    expect(crkaPoVrsti(17)).toBe('S')
  })

  it('prvih 22 skupin je slovenska abeceda brez šumnikov', () => {
    const vse = Array.from({ length: 22 }, (_, i) => crkaPoVrsti(i)).join('')
    expect(vse).toBe('ABCDEFGHIJKLMNOPRSTUVZ')
  })

  it('ko črk zmanjka, se pred črko doda Z', () => {
    expect(crkaPoVrsti(22)).toBe('ZA')
    expect(crkaPoVrsti(43)).toBe('ZZ')
    expect(crkaPoVrsti(44)).toBe('ZZA')
  })

  it('abecedni vrstni red je vrstni red skupin', () => {
    const oznake = Array.from({ length: 66 }, (_, i) => crkaPoVrsti(i))
    const urejene = [...oznake].sort((a, b) => a.localeCompare(b, 'sl'))
    expect(urejene).toEqual(oznake)
  })
})
