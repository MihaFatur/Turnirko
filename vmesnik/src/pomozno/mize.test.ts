import { describe, expect, it } from 'vitest'

import type { KandidatMizeDto, MizaDto, TekmaMizeDto } from '../api/tipi'
import {
  imenaTekme,
  minutOd,
  mizeTekme,
  predMinutami,
  privzetiSodnik,
  prosteMize,
  prviPredlogi,
  sprosceneMize,
} from './mize'

function tekma(id: number, osebe: number[] = [1, 2], miza: number | null = null): TekmaMizeDto {
  return {
    tekma: {
      id,
      udelezenec1: { idPrijave: 10, polnoIme: 'Ana Novak', klub: null, polnoIme2: null, klub2: null, priimek: 'Novak', priimek2: null },
      udelezenec2: { idPrijave: 11, polnoIme: 'Bor Kovač', klub: null, polnoIme2: null, klub2: null, priimek: 'Kovač', priimek2: null },
    } as unknown as TekmaMizeDto['tekma'],
    idDogodka: 1,
    imeDogodka: 'Člani',
    del: 'Skupina B',
    ekipno: false,
    idSrecanje: null,
    miza,
    miza2: null,
    poklicanaOb: null,
    sodnik: null,
    osebe,
  }
}

function miza(stevilka: number, dodatno: Partial<MizaDto> = {}): MizaDto {
  return { stevilka, izklopljena: false, tekma: null, prostaOd: null, cakajociSodnik: null, predlogi: [], ...dodatno }
}

describe('minutOd', () => {
  it('šteje cele minute po uri strežnika', () => {
    expect(minutOd('2026-12-05T10:00:00', '2026-12-05T10:12:59')).toBe(12)
    expect(minutOd('2026-12-05T10:00:00', '2026-12-05T09:59:00')).toBe(0)
    expect(minutOd(null, '2026-12-05T10:00:00')).toBeNull()
  })
})

describe('prikaz tekme', () => {
  it('imena in mize', () => {
    expect(imenaTekme(tekma(1))).toBe('Ana Novak – Bor Kovač')
    expect(mizeTekme({ ...tekma(1), miza: 3, miza2: 4 })).toBe('3 in 4')
    expect(mizeTekme(tekma(1, [1, 2], 5))).toBe('5')
    expect(predMinutami(0)).toBe('pravkar')
    expect(predMinutami(4)).toBe('pred 4 min')
  })
})

describe('privzetiSodnik', () => {
  it('je poraženec zadnje tekme na mizi, razen če igra v tej tekmi', () => {
    const m = miza(2, { cakajociSodnik: { id: 7, ime: 'Cene Zajc' } })
    expect(privzetiSodnik(m, tekma(1, [1, 2]))).toEqual({ id: 7, ime: 'Cene Zajc' })
    expect(privzetiSodnik(m, tekma(1, [7, 2]))).toBeNull()
    expect(privzetiSodnik(miza(3), tekma(1))).toBeNull()
    expect(privzetiSodnik(undefined, tekma(1))).toBeNull()
  })
})

describe('proste in sproščene mize', () => {
  const zasedena = miza(1, { tekma: tekma(5, [1, 2], 1) })
  it('prosta je miza brez tekme, ki ni izklopljena', () => {
    expect(prosteMize([zasedena, miza(2), miza(3, { izklopljena: true })])).toEqual([2])
  })
  it('sproščena je miza, ki je bila zasedena in je zdaj prosta', () => {
    expect(sprosceneMize(undefined, [miza(1)])).toEqual([])
    expect(sprosceneMize([zasedena, miza(2)], [miza(1), miza(2)])).toEqual([1])
    // izklopljena miza ne piska
    expect(sprosceneMize([zasedena], [miza(1, { izklopljena: true })])).toEqual([])
  })
})

describe('prviPredlogi', () => {
  it('vzame prvi predlog vsake proste mize, zasedenih ne', () => {
    const k = (id: number, zaseden = false): KandidatMizeDto => ({ tekma: tekma(id), zaseden, razlogi: [] })
    const mize = [miza(1, { predlogi: [11, 12] }), miza(2, { predlogi: [12] }), miza(3, { tekma: tekma(9, [5, 6], 3), predlogi: [] })]
    const pari = prviPredlogi(mize, [k(11), k(12)])
    expect(pari.map(([m, kand]) => [m.stevilka, kand.tekma.tekma.id])).toEqual([[1, 11], [2, 12]])
    expect(prviPredlogi(mize, [k(11, true), k(12)]).map(([m]) => m.stevilka)).toEqual([2])
  })
})
