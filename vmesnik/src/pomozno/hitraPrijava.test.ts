import { describe, expect, it } from 'vitest'

import {
  besedeImena,
  igralciPrijav,
  NAJVEC_KANDIDATOV,
  oviraPrijave,
  poisciVrstice,
  vrsticeSeznama,
} from './hitraPrijava'

const igralci = [
  { id: 1, ime: 'Ana', priimek: 'Novak' },
  { id: 2, ime: 'Bor', priimek: 'Novak' },
  { id: 3, ime: 'Dora', priimek: 'Križan' },
  { id: 4, ime: 'Ana Marija', priimek: 'Novak' },
  { id: 5, ime: 'Eva', priimek: 'Narancsik-Smotlak' },
]

function izid(vrstica: string) {
  return poisciVrstice([vrstica], igralci)[0].izid
}

describe('vrstice prilepljenega seznama', () => {
  it('vsak igralec v svoji vrstici, prazne vrstice odpadejo', () => {
    expect(vrsticeSeznama('Ana Novak\r\n\n  Dora Križan  \n\t\n')).toEqual([
      'Ana Novak',
      'Dora Križan',
    ])
  })

  it('vejica in podpičje ne ločita igralcev', () => {
    expect(vrsticeSeznama('Novak, Ana\nKrižan;Dora;NTK Žalec')).toHaveLength(2)
  })

  it('ločila so presledki, šumniki odpadejo', () => {
    expect(besedeImena('1.\tKRIŽAN, Dora (2012)')).toEqual(['1', 'krizan', 'dora', '2012'])
    expect(besedeImena('Narancsik-Smotlak')).toEqual(['narancsik', 'smotlak'])
  })
})

describe('iskanje igralca po vrstici', () => {
  it('ime in priimek v poljubnem vrstnem redu, brez šumnikov', () => {
    expect(izid('Ana Novak')).toEqual({ vrsta: 'najden', idIgralca: 1, priblizno: false })
    expect(izid('krizan dora')).toEqual({ vrsta: 'najden', idIgralca: 3, priblizno: false })
  })

  it('vrstica sme nositi še številko, klub in letnik (stolpci iz Excela)', () => {
    expect(izid('3.\tKrižan\tDora\tNTK Žalec\t2012')).toEqual({
      vrsta: 'najden',
      idIgralca: 3,
      priblizno: false,
    })
  })

  it('daljše ime zmaga: Ana Marija Novak ni Ana Novak', () => {
    expect(izid('Novak Ana Marija')).toEqual({ vrsta: 'najden', idIgralca: 4, priblizno: false })
  })

  it('dvojni priimek z vezajem ali brez', () => {
    expect(izid('Eva Narancsik Smotlak').vrsta).toBe('najden')
    expect(izid('Narancsik-Smotlak Eva').vrsta).toBe('najden')
  })

  it('okrajšano ime je približen zadetek', () => {
    expect(izid('Novak B.')).toEqual({ vrsta: 'najden', idIgralca: 2, priblizno: true })
  })

  it('samo priimek da vse z njim - izbere organizator', () => {
    expect(izid('Novak')).toEqual({ vrsta: 'vec', kandidati: [1, 2, 4], skupaj: 3 })
  })

  it('dva igralca v isti vrstici nista ugibanje', () => {
    expect(izid('Ana Novak, Bor Novak')).toEqual({ vrsta: 'vec', kandidati: [1, 2], skupaj: 2 })
  })

  it('neznanega igralca ne ugiba', () => {
    expect(izid('Janez Kos')).toEqual({ vrsta: 'brez' })
    // okrajšano ime s klubom je predaleč od imena
    expect(izid('Novak A., NTK Žalec')).toEqual({ vrsta: 'brez' })
    expect(izid('17.')).toEqual({ vrsta: 'brez' })
  })

  it('isti igralec v dveh vrsticah se prijavi enkrat', () => {
    const vrstice = poisciVrstice(['Ana Novak', 'Dora Križan', 'NOVAK ANA'], igralci)
    expect(vrstice.map((v) => v.izid.vrsta)).toEqual(['najden', 'najden', 'ponovljen'])
  })

  it('kandidatov je izpisanih največ toliko, skupaj pove pravo število', () => {
    const mnogo = Array.from({ length: 20 }, (_, i) => ({ id: 100 + i, ime: `Ime${i}`, priimek: 'Kos' }))
    const rezultat = poisciVrstice(['Kos'], mnogo)[0].izid
    expect(rezultat.vrsta).toBe('vec')
    if (rezultat.vrsta === 'vec') {
      expect(rezultat.kandidati).toHaveLength(NAJVEC_KANDIDATOV)
      expect(rezultat.skupaj).toBe(20)
    }
  })
})

describe('ovira prijave', () => {
  const prijavljeni = new Set([1])

  it('že prijavljen gre pred kategorijo', () => {
    expect(oviraPrijave({ id: 1, spol: 'ZENSKI' }, prijavljeni, 'MOSKI')).toBe('prijavljen')
  })

  it('spol omejuje samo moško in žensko kategorijo', () => {
    expect(oviraPrijave({ id: 2, spol: 'ZENSKI' }, prijavljeni, 'MOSKI')).toBe('kategorija')
    expect(oviraPrijave({ id: 2, spol: 'MOSKI' }, prijavljeni, 'ZENSKE')).toBe('kategorija')
    expect(oviraPrijave({ id: 2, spol: 'ZENSKI' }, prijavljeni, 'MESANO')).toBeNull()
    expect(oviraPrijave({ id: 2, spol: 'MOSKI' }, prijavljeni, 'KDORKOLI')).toBeNull()
  })
})

describe('igralci prijav drugega dogodka', () => {
  it('oba igralca para, brez odjavljenih, ekip in ponovitev', () => {
    expect(
      igralciPrijav([
        { idIgralca: 1, idIgralca2: 2, status: 'PRIJAVLJEN' },
        { idIgralca: 3, idIgralca2: null, status: 'ODJAVLJEN' },
        { idIgralca: null, idIgralca2: null, status: 'PRIJAVLJEN' },
        { idIgralca: 4, idIgralca2: null, status: 'ODSTOPIL' },
        { idIgralca: 2, idIgralca2: null, status: 'PRIJAVLJEN' },
      ]),
    ).toEqual([1, 2, 4])
  })
})
