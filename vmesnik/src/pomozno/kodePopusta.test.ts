import { describe, expect, it } from 'vitest'

import { mesecev, opisUgodnosti, prvoPlacilo } from './kodePopusta'

describe('prvo plačilo po popustu', () => {
  /* Isto zaokrožanje kot KodePopustaStoritev.prvoPlacilo in Stripe: zaokroži se
     POPUST (v centih), ostanek je plačilo. */
  it('popust se zaokroži na cent, ostanek je plačilo', () => {
    expect(prvoPlacilo(19.99, 15)).toBe(16.99)
    expect(prvoPlacilo(1.99, 33)).toBe(1.33)
  })

  it('pol centa popusta gre navzgor', () => {
    expect(prvoPlacilo(9.99, 50)).toBe(4.99)
  })

  it('robova: brez popusta in zastonj', () => {
    expect(prvoPlacilo(2.99, 0)).toBe(2.99)
    expect(prvoPlacilo(2.99, 100)).toBe(0)
  })

  it('cena v evrih se ne izgubi v plavajoči vejici', () => {
    /* 19.99 * 100 je 1998,9999…; brez zaokrožitve bi manjkal cent. */
    expect(prvoPlacilo(19.99, 0)).toBe(19.99)
  })
})

describe('besedilo ugodnosti', () => {
  it('meseci v ednini, dvojini in množini', () => {
    expect([1, 2, 3, 4, 6, 12].map(mesecev)).toEqual([
      '1 mesec', '2 meseca', '3 meseci', '4 meseci', '6 mesecev', '12 mesecev',
    ])
  })

  it('opis po vrsti kode', () => {
    const osnova = { meseci: null, odstotek: null, paket: null }
    expect(opisUgodnosti({ ...osnova, vrsta: 'PREMIUM_MESECI', meseci: 3 })).toBe('Premium brez plačila: 3 meseci')
    expect(opisUgodnosti({ ...osnova, vrsta: 'NAVIJAC_MESECI', meseci: 12 })).toBe('Navijač brez plačila: 12 mesecev')
    expect(opisUgodnosti({ ...osnova, vrsta: 'ORGANIZATOR_MESECI', meseci: 2 })).toBe(
      'Organizator brez plačila: 2 meseca',
    )
    expect(opisUgodnosti({ ...osnova, vrsta: 'ODSTOTEK', odstotek: 20 })).toBe('−20 % na prvo plačilo')
  })

  it('manjkajoči podatki ne podrejo izpisa', () => {
    const osnova = { meseci: null, odstotek: null, paket: null }
    expect(opisUgodnosti({ ...osnova, vrsta: 'PREMIUM_MESECI' })).toBe('Premium brez plačila: 1 mesec')
    expect(opisUgodnosti({ ...osnova, vrsta: 'ODSTOTEK' })).toBe('−0 % na prvo plačilo')
    expect(opisUgodnosti({ ...osnova, vrsta: null })).toBe('')
  })
})
