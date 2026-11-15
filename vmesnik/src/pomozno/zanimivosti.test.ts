import { describe, expect, it } from 'vitest'

/* Izvorna datoteka zaledja kot besedilo (Vite `?raw`): prag je v vmesniku
   ročno prepisan, zato ga test primerja z izvirnikom. */
import zaledje from '../../../zaledje/src/main/java/si/turnirko/storitve/StatistikaTekmovanjaStoritev.java?raw'
import { srecanje } from '../testi/tovarna'
import { NASLOVI_RAZDELKOV, odigranihTekemLige, PRAG_ZANIMIVOSTI } from './zanimivosti'

describe('odigrane tekme lige', () => {
  it('vsota dobljenih tekem obeh ekip, tudi pri srečanju v teku', () => {
    const srecanja = [
      srecanje(1, { dobljeneDomaci: 5, dobljeneGost: 2, status: 'KONCANO' }),
      srecanje(2, { dobljeneDomaci: 1, dobljeneGost: 1, status: 'POTEKA' }),
      srecanje(3),
    ]
    expect(odigranihTekemLige(srecanja)).toBe(9)
  })

  it('brez boja in prenesen izid nosita izid brez odigranih tekem', () => {
    const srecanja = [
      srecanje(1, { dobljeneDomaci: 5, dobljeneGost: 0, brezBoja: true }),
      srecanje(2, { dobljeneDomaci: 4, dobljeneGost: 3, prenesen: true }),
      srecanje(3, { dobljeneDomaci: 5, dobljeneGost: 3 }),
    ]
    expect(odigranihTekemLige(srecanja)).toBe(8)
  })

  it('prazna liga', () => {
    expect(odigranihTekemLige([])).toBe(0)
  })
})

describe('prag zavihka Zanimivosti', () => {
  it('je enak pragu strežnika (StatistikaTekmovanjaStoritev.PRAG_TEKEM)', () => {
    const najden = /PRAG_TEKEM\s*=\s*(\d+)/.exec(zaledje)
    expect(najden, 'konstante PRAG_TEKEM v zaledju ni več').not.toBeNull()
    expect(PRAG_ZANIMIVOSTI).toBe(Number(najden![1]))
  })
})

describe('naslovi razdelkov', () => {
  it('vsak razdelek ima neprazen in enoličen naslov', () => {
    const naslovi = Object.values(NASLOVI_RAZDELKOV)
    expect(naslovi.every((naslov) => naslov.trim().length > 0)).toBe(true)
    expect(new Set(naslovi).size).toBe(naslovi.length)
  })
})
