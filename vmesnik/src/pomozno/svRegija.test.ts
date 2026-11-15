import { describe, expect, it } from 'vitest'

import {
  crkaSkupine,
  imeRavni,
  imeRavniZaIzbiro,
  imeStopnje,
  imeTekmeZreba,
  mestVZrebu,
  opisRazpona,
  RAVNI_ZREBA,
  velikostiZrebov,
} from './svRegija'

describe('razponi mest in stopnje žreba', () => {
  it('eno mesto ali razpon', () => {
    expect(opisRazpona(5, 5)).toBe('5. mesto')
    expect(opisRazpona(5, 8)).toBe('5.–8. mesto')
  })

  it('ime stopnje po številu udeležencev mreže', () => {
    expect([2, 4, 8, 16, 32].map(imeStopnje)).toEqual([
      'Finale', 'Polfinale', 'Četrtfinale', 'Osmina finala', '1/16 finala',
    ])
  })

  it('finale je ena sama tekma na turnirju: samo v glavnem žrebu za prvo mesto', () => {
    expect(imeTekmeZreba(1, 2, 1, true)).toBe('Finale')
    expect(imeTekmeZreba(1, 2, 1, false)).toBe('Za 1. mesto')
    expect(imeTekmeZreba(9, 10, 9, false)).toBe('Za 9. mesto')
    expect(imeTekmeZreba(3, 4, 1, true)).toBe('Za 3. mesto')
  })

  it('stopnja v zgornjem razponu brez dodatka, drugje z razponom mest', () => {
    expect(imeTekmeZreba(1, 4, 1, true)).toBe('Polfinale')
    expect(imeTekmeZreba(5, 8, 1, true)).toBe('Polfinale za 5.–8. mesto')
    expect(imeTekmeZreba(9, 16, 9, false)).toBe('Četrtfinale')
  })

  it('raven žreba v rodilniku in za izbirnik', () => {
    expect([2, 4, 8, 16, 32].map(imeRavni)).toEqual([
      'finala', 'polfinala', 'četrtfinala', 'osmine finala', 'šestnajstine finala',
    ])
    expect(imeRavni(64)).toBe('mreže 64')
    expect(RAVNI_ZREBA.map(imeRavniZaIzbiro)).toEqual([
      'Šestnajstina finala', 'Osmina finala', 'Četrtfinale', 'Polfinale', 'Finale',
    ])
  })
})

describe('skupine in žrebi', () => {
  it('oznaka skupine brez številke Skupine', () => {
    expect(crkaSkupine('1A')).toBe('A')
    expect(crkaSkupine('12B')).toBe('B')
    expect(crkaSkupine('C')).toBe('C')
  })

  it('velikosti žrebov: zadnji dobi ostanek', () => {
    expect(velikostiZrebov(20, 8)).toEqual([8, 8, 4])
    expect(velikostiZrebov(16, 8)).toEqual([8, 8])
    expect(velikostiZrebov(5, 8)).toEqual([5])
    expect(velikostiZrebov(0, 8)).toEqual([])
  })

  it('koliko mest vsake skupine gre v žreb', () => {
    const mest = mestVZrebu(['A1', 'B1', 'C1', 'A2', 'B2'])
    expect([...mest.entries()]).toEqual([['A', 2], ['B', 2], ['C', 1]])
  })

  it('ravni so naštete od najzgodnejše', () => {
    expect(RAVNI_ZREBA).toEqual([32, 16, 8, 4, 2])
  })
})
