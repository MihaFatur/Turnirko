import { describe, expect, it } from 'vitest'

import { srecanje } from '../testi/tovarna'
import {
  jePrestavljeno,
  jeVDrugemKolu,
  opisPrestavitve,
  terminKola,
  vKolih,
} from './prestavitve'

describe('prestavljeno srečanje', () => {
  it('srečanje v kolu iz žreba ni prestavljeno', () => {
    expect(jeVDrugemKolu(srecanje(1, { kolo: 5, izvirnoKolo: null }))).toBe(false)
    expect(jeVDrugemKolu(srecanje(1, { kolo: 5, izvirnoKolo: 5 }))).toBe(false)
    expect(jePrestavljeno(srecanje(1, { kolo: 5, izvirnoKolo: 5 }))).toBe(false)
  })

  it('premaknjeno v drugo kolo', () => {
    const s = srecanje(1, { kolo: 7, izvirnoKolo: 5 })
    expect(jeVDrugemKolu(s)).toBe(true)
    expect(jePrestavljeno(s)).toBe(true)
  })

  it('čakajoče je prestavljeno, a ne stoji v nobenem kolu', () => {
    const s = srecanje(1, { kolo: 5, izvirnoKolo: 5, cakaNaTermin: true })
    expect(jeVDrugemKolu(s)).toBe(false)
    expect(jePrestavljeno(s)).toBe(true)
    expect(vKolih([s, srecanje(2)]).map((x) => x.id)).toEqual([2])
  })
})

describe('poved o prestavitvi', () => {
  it('redno srečanje povedi nima', () => {
    expect(opisPrestavitve(srecanje(1, { kolo: 5 }))).toBeNull()
  })

  it('premaknjeno pove kolo iz žreba', () => {
    expect(opisPrestavitve(srecanje(1, { kolo: 7, izvirnoKolo: 5 }))).toEqual({
      naslov: 'Prestavljeno iz 5. kola',
      razlog: null,
    })
  })

  it('čakajoče: kolo iz žreba samo, kadar ga vrstica sama ne nosi', () => {
    const s = srecanje(1, { kolo: 5, izvirnoKolo: 5, cakaNaTermin: true })
    expect(opisPrestavitve(s)?.naslov).toBe('Prestavljeno — termin še ni znan')
    expect(opisPrestavitve(s, true)?.naslov).toBe('Prestavljeno iz 5. kola — termin še ni znan')
  })

  it('razlog je obrezan, prazen razlog je null', () => {
    const z = (razlogPrestavitve: string | null) =>
      opisPrestavitve(srecanje(1, { kolo: 7, izvirnoKolo: 5, razlogPrestavitve }))?.razlog
    expect(z('  bolezen  ')).toBe('bolezen')
    expect(z('   ')).toBeNull()
    expect(z(null)).toBeNull()
  })
})

describe('termin kola', () => {
  it('najzgodnejši začetek srečanj, ki v kolu sodijo', () => {
    const srecanja = [
      srecanje(1, { kolo: 3, predvidenZacetek: '2026-10-10T18:00:00' }),
      srecanje(2, { kolo: 3, predvidenZacetek: '2026-10-09T18:00:00' }),
      srecanje(3, { kolo: 4, predvidenZacetek: '2026-10-02T18:00:00' }),
    ]
    expect(terminKola(srecanja, 3)).toBe('2026-10-09T18:00:00')
  })

  it('prestavljeno srečanje kolu ne določa datuma', () => {
    const srecanja = [
      srecanje(1, { kolo: 3, predvidenZacetek: '2026-10-10T18:00:00' }),
      srecanje(2, { kolo: 3, izvirnoKolo: 1, predvidenZacetek: '2026-10-01T18:00:00' }),
    ]
    expect(terminKola(srecanja, 3)).toBe('2026-10-10T18:00:00')
  })

  it('razen kadar v kolu ni drugih', () => {
    const srecanja = [srecanje(2, { kolo: 3, izvirnoKolo: 1, predvidenZacetek: '2026-10-01T18:00:00' })]
    expect(terminKola(srecanja, 3)).toBe('2026-10-01T18:00:00')
  })

  it('čakajoča in srečanja brez začetka ne štejejo', () => {
    const srecanja = [
      srecanje(1, { kolo: 3, predvidenZacetek: null }),
      srecanje(2, { kolo: 3, cakaNaTermin: true, predvidenZacetek: '2026-10-01T18:00:00' }),
    ]
    expect(terminKola(srecanja, 3)).toBeNull()
    expect(terminKola([], 1)).toBeNull()
  })
})
