import { describe, expect, it } from 'vitest'

import {
  NAJVEC_LISTOV,
  RAZLICICA,
  STOLPCEV,
  VREDNOSTI,
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

  it('list ima 140 okenc v 14 vrsticah (GeometrijaVadnice)', () => {
    expect(VREDNOSTI.length * STOLPCEV).toBe(140)
    expect(VREDNOSTI).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13])
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
