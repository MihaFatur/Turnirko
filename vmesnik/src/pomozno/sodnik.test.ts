import { describe, expect, it } from 'vitest'

import type { PredlogIzidaDto } from '../api/tipi'
import { besediloZmagovalca, celiceZaPotrditev, izidSodnika, mizaIzPolja, vrsticeSodnika } from './sodnik'

const niz = (tocke1: number | null, tocke2: number | null, negotovo = false) => ({
  tocke1, tocke2, negotovo1: negotovo, negotovo2: false, neveljaven: false,
})

const predlog = (nizi: ReturnType<typeof niz>[]): PredlogIzidaDto => ({
  izidTip: 'IGRANO', dobljeniNizi1: 3, dobljeniNizi2: 1, zmagovalecStran: 1, nizi, opozorila: [], pripravljen: true,
})

describe('vrsticeSodnika', () => {
  it('ima toliko vrstic, kolikor nizov ima tekma; neprebrane so prazne', () => {
    const v = vrsticeSodnika(predlog([niz(11, 7), niz(null, null)]), 5)
    expect(v).toHaveLength(5)
    expect(v[0]).toEqual({ tocke1: '11', tocke2: '7' })
    expect(v[1]).toEqual({ tocke1: '', tocke2: '' })
    expect(v[4]).toEqual({ tocke1: '', tocke2: '' })
  })
})

describe('celiceZaPotrditev', () => {
  it('negotove in neprebrane celice odigranih nizov, prazne vrstice ne', () => {
    const m = celiceZaPotrditev(predlog([niz(11, 7, true), niz(9, null), niz(null, null)]))
    expect([...m].sort()).toEqual([0, 3])
  })
})

describe('izidSodnika', () => {
  const vrstice = (...nizi: [string, string][]) => nizi.map(([tocke1, tocke2]) => ({ tocke1, tocke2 }))

  it('veljaven izid 3:1 z neodigranim petim nizom', () => {
    const i = izidSodnika(vrstice(['11', '7'], ['9', '11'], ['11', '5'], ['11', '8'], ['', '']), 5)
    expect(i.napaka).toBeNull()
    expect(i.vnos).toMatchObject({ dobljeniNizi1: 3, dobljeniNizi2: 1, izidTip: null })
    expect(i.vnos?.nizi).toHaveLength(4)
  })

  it('neveljaven niz, nepopoln niz, premalo nizov', () => {
    expect(izidSodnika(vrstice(['11', '10'], ['11', '5'], ['11', '3']), 5).napaka).toMatch(/ni veljaven/)
    expect(izidSodnika(vrstice(['11', ''], ['11', '5'], ['11', '3']), 5).napaka).toMatch(/obe števili/)
    expect(izidSodnika(vrstice(['11', '7'], ['11', '5'], ['', '']), 5).napaka).toMatch(/3 nize/)
  })

  it('niz po odločeni tekmi in luknja med nizi', () => {
    expect(izidSodnika(vrstice(['11', '7'], ['11', '5'], ['11', '3'], ['11', '2']), 5).napaka).toMatch(/odločena/)
    expect(izidSodnika(vrstice(['11', '7'], ['', ''], ['11', '3'], ['11', '2']), 5).napaka).toMatch(/prazen/)
  })

  it('tekma na tri nize', () => {
    expect(izidSodnika(vrstice(['11', '7'], ['5', '11'], ['12', '10']), 3).vnos)
      .toMatchObject({ dobljeniNizi1: 2, dobljeniNizi2: 1 })
  })
})

describe('besediloZmagovalca', () => {
  it('zmagovalčevi nizi so prvi', () => {
    expect(besediloZmagovalca({ prvi: 1, drugi: 3 }, 'Ana Novak', 'Eva Zajc')).toBe('Zmagal je Eva Zajc 3:1')
    expect(besediloZmagovalca({ prvi: 3, drugi: 0 }, 'Ana Novak', 'Eva Zajc')).toBe('Zmagal je Ana Novak 3:0')
  })
})

describe('mizaIzPolja', () => {
  it('1-99 ali prazno', () => {
    expect(mizaIzPolja('')).toEqual({ miza: null, veljavna: true })
    expect(mizaIzPolja('7')).toEqual({ miza: 7, veljavna: true })
    expect(mizaIzPolja('0').veljavna).toBe(false)
    expect(mizaIzPolja('100').veljavna).toBe(false)
    expect(mizaIzPolja('3.5').veljavna).toBe(false)
  })
})
