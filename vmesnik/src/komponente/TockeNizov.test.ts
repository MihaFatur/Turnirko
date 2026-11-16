import { describe, expect, it } from 'vitest'

import { drugaTocka, veljavenNiz, zacetekPodaljska } from './TockeNizov'

/* Simulacija tipkanja v eno vrstico, kot ga naredi komponenta: vsaka sprememba
   polja gre skozi drugaTocka, ki lahko prepiše ali izbriše drugi stolpec. */
function vpisi(vrstica: { tocke1: string; tocke2: string }, stran: 'tocke1' | 'tocke2', niz: string) {
  const druga = stran === 'tocke1' ? 'tocke2' : 'tocke1'
  let stanje = { ...vrstica }
  for (let i = 1; i <= niz.length; i++) {
    const vpis = niz.slice(0, i)
    stanje = { ...stanje, [stran]: vpis, [druga]: drugaTocka(vpis, stanje[druga]) }
  }
  return stanje
}

describe('drugaTocka — podaljšek 11:13', () => {
  it('11 v levem in nato 13 v desnem stolpcu ohrani 11', () => {
    let vrstica = { tocke1: '', tocke2: '' }
    vrstica = vpisi(vrstica, 'tocke1', '11')
    expect(vrstica).toEqual({ tocke1: '11', tocke2: '' })
    vrstica = vpisi(vrstica, 'tocke2', '13')
    expect(vrstica).toEqual({ tocke1: '11', tocke2: '13' })
  })

  it('13 v levem in nato 11 v desnem stolpcu ohrani 13', () => {
    let vrstica = { tocke1: '', tocke2: '' }
    vrstica = vpisi(vrstica, 'tocke1', '13')
    vrstica = vpisi(vrstica, 'tocke2', '11')
    expect(vrstica).toEqual({ tocke1: '13', tocke2: '11' })
  })

  it('13 po samodejnem 11 ostane 13:11, ker je to edini mogoč par', () => {
    // prva števka »1« samodejno napolni drugi stolpec z 11; ker 13 ne more imeti drugega
    // nasprotnika kot 11, ostane tak vpis veljaven
    const vrstica = vpisi({ tocke1: '', tocke2: '' }, 'tocke1', '13')
    expect(vrstica).toEqual({ tocke1: '13', tocke2: '11' })
  })

  it('ostali podaljški se še vedno ohranijo ob vnosu v obeh vrstnih redih', () => {
    for (const [a, b] of [
      ['12', '14'],
      ['14', '12'],
      ['15', '17'],
      ['17', '15'],
      ['21', '19'],
      ['19', '21'],
    ]) {
      expect(vpisi(vpisi({ tocke1: '', tocke2: '' }, 'tocke1', a), 'tocke2', b)).toEqual({
        tocke1: a,
        tocke2: b,
      })
    }
  })
})

describe('drugaTocka — samodejno polnjenje', () => {
  it('2–9 v enem stolpcu da 11 v drugem', () => {
    for (const d of ['0', '2', '5', '9']) expect(drugaTocka(d, '')).toBe('11')
  })

  it('10 da 12', () => {
    expect(drugaTocka('10', '')).toBe('12')
  })

  it('1 napolni samo prazen stolpec', () => {
    expect(drugaTocka('1', '')).toBe('11')
    expect(drugaTocka('1', '7')).toBe('7')
  })

  it('ostanek 11 se izbriše, kadar z vpisom ne tvori veljavnega niza', () => {
    // 7 → samodejni 11, nato popravek 7 v 11, 12 ali 14: 11:11, 12:11 in 14:11 niso nizi
    for (const vpis of ['11', '12', '14', '20']) {
      expect(veljavenNiz(Number(vpis), 11)).toBe(false)
      expect(drugaTocka(vpis, '11')).toBe('')
    }
  })

  it('drugega stolpca z drugo vrednostjo ne izbriše', () => {
    expect(drugaTocka('13', '9')).toBe('9')
    expect(drugaTocka('11', '7')).toBe('7')
  })

  it('enomestni vpis ne prepiše podaljška, ki se z njim ujema', () => {
    expect(zacetekPodaljska(2, 19)).toBe(true)
    expect(drugaTocka('2', '19')).toBe('19')
  })
})
