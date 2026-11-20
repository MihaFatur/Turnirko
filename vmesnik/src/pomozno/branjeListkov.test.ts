import { describe, expect, it } from 'vitest'

import type { BranjeListkaDto, PredlogIzidaDto } from '../api/tipi'
import { veljavenNiz } from '../komponente/TockeNizov'
import {
  celiceZaPregled,
  dobljeniIzTock,
  povzetekNizov,
  razdeliVrsto,
  vnosIzPredloga,
  vrsticeIzPredloga,
} from './branjeListkov'

function predlog(polja: Partial<PredlogIzidaDto> = {}): PredlogIzidaDto {
  return {
    izidTip: 'IGRANO',
    dobljeniNizi1: 3,
    dobljeniNizi2: 1,
    zmagovalecStran: null,
    nizi: [
      { tocke1: 11, tocke2: 7, negotovo1: false, negotovo2: false, neveljaven: false },
      { tocke1: 9, tocke2: 11, negotovo1: false, negotovo2: true, neveljaven: false },
      { tocke1: 11, tocke2: null, negotovo1: false, negotovo2: false, neveljaven: false },
      { tocke1: 11, tocke2: 10, negotovo1: false, negotovo2: false, neveljaven: true },
    ],
    opozorila: [],
    pripravljen: false,
    ...polja,
  }
}

describe('branje listkov', () => {
  it('predlog napolni vrstice, neprebrana celica ostane prazna', () => {
    expect(vrsticeIzPredloga(predlog())[2]).toEqual({ tocke1: '11', tocke2: '' })
  })

  it('za pregled so negotove, neprebrane in celice neveljavnega niza', () => {
    expect([...celiceZaPregled(predlog())].sort((a, b) => a - b)).toEqual([3, 5, 6, 7])
  })

  it('vnos po predlogu je navaden vnos rezultata', () => {
    const p = predlog({
      nizi: [{ tocke1: 11, tocke2: 7, negotovo1: false, negotovo2: false, neveljaven: false }],
      dobljeniNizi1: 1,
      dobljeniNizi2: 0,
    })
    expect(vnosIzPredloga(p)).toEqual({
      izidTip: null,
      dobljeniNizi1: 1,
      dobljeniNizi2: 0,
      zmagovalecStran: null,
      nizi: [{ tocke1: 11, tocke2: 7 }],
      steviloNizov: null,
    })
    expect(vnosIzPredloga(predlog({ izidTip: 'BREZ_BOJA', nizi: [], zmagovalecStran: 2 }))).toMatchObject({
      izidTip: 'BREZ_BOJA',
      zmagovalecStran: 2,
      nizi: null,
    })
  })

  it('povzetek nizov', () => {
    expect(povzetekNizov(predlog())).toBe('11:7 9:11 11:? 11:10')
  })

  it('dobljeni nizi iz točk samo pri veljavnih vrsticah', () => {
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '7' }, { tocke1: '5', tocke2: '11' }], veljavenNiz)).toEqual({
      prvi: 1,
      drugi: 1,
    })
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '10' }], veljavenNiz)).toBeNull()
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '' }], veljavenNiz)).toBeNull()
    expect(dobljeniIzTock([], veljavenNiz)).toBeNull()
  })

  it('vrsta: najprej za potrditev po času fotografiranja', () => {
    const b = (id: number, stanje: BranjeListkaDto['stanje'], ob: string) =>
      ({ id, stanje, ustvarjenoOb: ob }) as BranjeListkaDto
    const { zaPotrditev, berem, zakljucena } = razdeliVrsto([
      b(4, 'PREBRANO', '2026-11-19T10:04:00'),
      b(3, 'V_VRSTI', '2026-11-19T10:03:00'),
      b(2, 'NAPAKA', '2026-11-19T10:02:00'),
      b(1, 'POTRJENO', '2026-11-19T10:01:00'),
    ])
    expect(zaPotrditev.map((x) => x.id)).toEqual([2, 4])
    expect(berem.map((x) => x.id)).toEqual([3])
    expect(zakljucena.map((x) => x.id)).toEqual([1])
  })
})
