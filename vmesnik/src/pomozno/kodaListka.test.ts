import { describe, expect, it } from 'vitest'

import { natisnjenaKoda, potQr, vsebinaQr } from './kodaListka'

describe('koda tekme na listku', () => {
  it('ima isti zapis kot zaledje (KodaListka.java)', () => {
    expect(vsebinaQr(48213)).toBe('TK:48213')
    expect(natisnjenaKoda(48213)).toBe('T-48213')
  })

  it('je majhna QR koda (verzija 1 ali 2) tudi za dolg id', () => {
    expect(potQr(vsebinaQr(7)).velikost).toBe(21)
    expect(potQr(vsebinaQr(123456789)).velikost).toBeLessThanOrEqual(25)
  })

  it('riše samo temne module, v mejah kode', () => {
    const { velikost, pot } = potQr(vsebinaQr(48213))
    const pravokotniki = pot.match(/M\d+ \d+h\d+v1h-\d+z/g) ?? []
    expect(pravokotniki.length).toBeGreaterThan(20)
    expect(pravokotniki.join('')).toBe(pot)
    for (const p of pravokotniki) {
      const [, x, y, sirina] = p.match(/M(\d+) (\d+)h(\d+)/)!.map(Number)
      expect(x + sirina).toBeLessThanOrEqual(velikost)
      expect(y).toBeLessThan(velikost)
    }
  })
})
