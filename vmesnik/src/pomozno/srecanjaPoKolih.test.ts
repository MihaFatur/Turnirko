import { describe, expect, it } from 'vitest'

import {
  ekipeVnosov,
  igra,
  jePrestavljeno,
  oznakaSrecanja,
  podvojitve,
  prostaKola,
  zasedenostV,
  type VnosSrecanja,
} from './srecanjaPoKolih'

function vnos(
  id: number,
  domaci: string,
  gost: string,
  kolo: number | null,
  izvirnoKolo: number = kolo ?? 1,
): VnosSrecanja {
  return {
    id,
    domaci,
    gost,
    izvirnoKolo,
    kolo,
    datum: null,
    ura: '',
    uraVKolu: 1,
    razlog: '',
    prej: { kolo, razlog: '' },
    odigrano: false,
    smeCakati: true,
  }
}

const kola = [1, 2, 3].map((kolo) => ({ kolo, datum: '', ura: '' }))

describe('vnos srečanja', () => {
  it('prestavljeno je, kar ni v kolu iz žreba ali čaka na termin', () => {
    expect(jePrestavljeno(vnos(1, 'A', 'B', 2, 2))).toBe(false)
    expect(jePrestavljeno(vnos(1, 'A', 'B', 3, 2))).toBe(true)
    expect(jePrestavljeno(vnos(1, 'A', 'B', null, 2))).toBe(true)
  })

  it('ekipa igra doma ali v gosteh', () => {
    const s = vnos(1, 'Žalec', 'Celje', 1)
    expect(igra(s, 'Žalec')).toBe(true)
    expect(igra(s, 'Celje')).toBe(true)
    expect(igra(s, 'Velenje')).toBe(false)
    expect(oznakaSrecanja(s)).toBe('Žalec – Celje')
  })

  it('imena ekip so brez ponovitev in po slovenski abecedi', () => {
    const vnosi = [vnos(1, 'Žalec', 'Celje', 1), vnos(2, 'Čatež', 'Žalec', 1), vnos(3, 'Zreče', 'Celje', 2)]
    expect(ekipeVnosov(vnosi)).toEqual(['Celje', 'Čatež', 'Zreče', 'Žalec'])
  })
})

describe('zasedenost kola', () => {
  const ab = vnos(1, 'A', 'B', 1)
  const ac = vnos(2, 'A', 'C', 2)
  const bd = vnos(3, 'B', 'D', 3)
  const vnosi = [ab, ac, bd]

  it('ekipa, ki v ciljnem kolu že igra', () => {
    expect(zasedenostV(ab, 2, vnosi, 1)).toEqual([{ ekipa: 'A', druga: [ac] }])
    expect(zasedenostV(ab, 3, vnosi, 1)).toEqual([{ ekipa: 'B', druga: [bd] }])
  })

  it('srečanje samo sebe ne šteje', () => {
    expect(zasedenostV(ab, 1, vnosi, 1)).toEqual([])
  })

  it('liga z dvema urama v kolu prenese dve srečanji ekipe', () => {
    expect(zasedenostV(ab, 2, vnosi, 2)).toEqual([])
  })
})

describe('podvojitve po vnosu', () => {
  it('označi vsa srečanja ekipe, ki v kolu igra preveč', () => {
    const ab = vnos(1, 'A', 'B', 1)
    const ac = vnos(2, 'A', 'C', 2)
    const ad = vnos(3, 'A', 'D', 2, 3)
    const rezultat = podvojitve([ab, ac, ad], 1)
    expect([...rezultat.keys()].sort()).toEqual([2, 3])
    expect(rezultat.get(2)).toEqual([{ ekipa: 'A', druga: [ad] }])
    expect(rezultat.get(3)).toEqual([{ ekipa: 'A', druga: [ac] }])
  })

  it('čakajoče srečanje ni v nobenem kolu', () => {
    const rezultat = podvojitve([vnos(1, 'A', 'B', null), vnos(2, 'A', 'C', null)], 1)
    expect(rezultat.size).toBe(0)
  })

  it('pri dveh urah kola sta dve srečanji običajni, tri ne', () => {
    const dve = [vnos(1, 'A', 'B', 1), vnos(2, 'A', 'C', 1)]
    expect(podvojitve(dve, 2).size).toBe(0)
    expect(podvojitve([...dve, vnos(3, 'A', 'D', 1)], 2).size).toBe(3)
  })
})

describe('prosta kola ekipe', () => {
  it('kola, v katerih ekipa nima srečanja', () => {
    const vnosi = [vnos(1, 'A', 'B', 1), vnos(2, 'A', 'D', 2), vnos(3, 'B', 'C', 3)]
    expect(prostaKola(vnosi, kola, 'D')).toEqual([1, 3])
    expect(prostaKola(vnosi, kola, 'A')).toEqual([3])
  })

  it('prestavljeno drugam ali čakajoče srečanje sprosti svoje kolo', () => {
    const vnosi = [vnos(1, 'A', 'B', null, 1), vnos(2, 'A', 'C', 3, 2)]
    expect(prostaKola(vnosi, kola, 'A')).toEqual([1, 2])
  })
})
