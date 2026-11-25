import { describe, expect, it } from 'vitest'

import {
  GEOMETRIJA,
  MAJHNA_SLIKA,
  NAJVEC_LISTOV,
  okencaVadnice,
  RAZLICICA,
  STOLPCEV,
  VREDNOSTI,
  namigMajhneSlike,
  noviIdjiListov,
  oznakaLista,
  qrVadnice,
  slikaStevke,
  vsebinaQrVadnice,
} from './vadnica'

describe('vadnica števk', () => {
  it('koda QR in oznaka sta enaki kot v zaledju (KodaListka, VadnicaStoritev)', () => {
    expect(vsebinaQrVadnice(1234567890)).toBe(`TKV:${RAZLICICA}:1234567890`)
    expect(oznakaLista(1234567890)).toBe('V-1234567890')
  })

  it('koda je vedno različica 1 (21 modulov), na katero računa postavitev', () => {
    for (const id of noviIdjiListov(20)) {
      expect(qrVadnice(id).velikost).toBe(21)
    }
  })

  it('id-ji listov so desetmestni in različni', () => {
    const idji = noviIdjiListov(NAJVEC_LISTOV)
    expect(new Set(idji).size).toBe(NAJVEC_LISTOV)
    for (const id of idji) {
      expect(Number.isSafeInteger(id)).toBe(true)
      expect(String(id)).toMatch(/^\d{10}$/)
    }
    // tudi skrajni naključni vrednosti ostaneta v razponu
    expect(noviIdjiListov(1, () => 0)).toEqual([1_000_000_000])
    expect(noviIdjiListov(1, (meja) => meja - 1)).toEqual([9_999_999_999])
  })

  it('natisne se različica 2: 128 okenc 17 × 9 mm kot celica listka, 16 vrstic (GeometrijaVadnice.V2)', () => {
    expect(RAZLICICA).toBe(2)
    expect(VREDNOSTI.length * STOLPCEV).toBe(128)
    expect(VREDNOSTI).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 11, 10])
    expect(GEOMETRIJA.okenceSirina).toBe(17)
    expect(GEOMETRIJA.okenceVisina).toBe(9)
    // zadnje okence ostane znotraj oznak (desna 195 ± 3 mm, spodnja 282 ± 3 mm)
    expect(GEOMETRIJA.okenceX + (STOLPCEV - 1) * GEOMETRIJA.korakX + GEOMETRIJA.okenceSirina).toBeLessThan(192)
    expect(GEOMETRIJA.okenceY + (VREDNOSTI.length - 1) * GEOMETRIJA.korakY + GEOMETRIJA.okenceVisina).toBeLessThan(279)
  })

  it('pregled že natisnjenega lista uporabi postavitev njegove različice', () => {
    expect(okencaVadnice(1).stolpcev).toBe(10)
    expect(okencaVadnice(1).vrednosti).toHaveLength(14)
    expect(okencaVadnice(2).stolpcev).toBe(8)
    expect(okencaVadnice(99)).toBe(okencaVadnice(RAZLICICA))
  })

  it('števka 28 × 28 postane temno črnilo na belem', () => {
    const bajti = new Uint8Array(784)
    bajti[0] = 255
    const base64 = btoa(String.fromCharCode(...bajti))
    const piksli = slikaStevke(base64)
    expect(piksli.length).toBe(784 * 4)
    expect([...piksli.slice(0, 4)]).toEqual([0, 0, 0, 255])
    expect([...piksli.slice(4, 8)]).toEqual([255, 255, 255, 255])
  })
})

describe('namigMajhneSlike', () => {
  it('pri dovolj veliki sliki ne doda ničesar', () => {
    expect(namigMajhneSlike(1200, 1600)).toBe('')
    expect(namigMajhneSlike(MAJHNA_SLIKA, 900)).toBe('')
  })

  it('pri majhni sliki pove velikost in prosi za izvirnik', () => {
    const namig = namigMajhneSlike(600, 800)
    expect(namig).toContain('600 × 800 px')
    expect(namig).toContain('izvirno fotografijo')
    expect(namig.startsWith(' ')).toBe(true)
  })
})
