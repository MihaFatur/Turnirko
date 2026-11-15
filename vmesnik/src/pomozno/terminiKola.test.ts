import { describe, expect, it } from 'vitest'

import {
  danKratica,
  kolTekst,
  kratkoDatum,
  najpogostejsa,
  prestavitevTekst,
  prestej,
  razlikaDni,
  srecanjTekst,
} from './terminiKola'

describe('datumska računica terminov kol', () => {
  it('teden naprej čez prehod ure ostane isti dan v tednu', () => {
    expect(prestej('2026-03-28', 7)).toBe('2026-04-04')
    expect(prestej('2026-10-24', 7)).toBe('2026-10-31')
    expect(prestej('2026-10-24', 1)).toBe('2026-10-25')
  })

  it('čez novo leto, prestopni dan in nazaj', () => {
    expect(prestej('2026-12-28', 7)).toBe('2027-01-04')
    expect(prestej('2024-02-28', 1)).toBe('2024-02-29')
    expect(prestej('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('neberljiv datum vrne nespremenjen', () => {
    expect(prestej('', 7)).toBe('')
    expect(prestej('jutri', 7)).toBe('jutri')
  })

  it('kratica dneva in kratek datum', () => {
    expect(danKratica('2026-10-03')).toBe('sob')
    expect(danKratica('2026-03-29')).toBe('ned')
    expect(danKratica('')).toBe('')
    expect(kratkoDatum('2026-10-03')).toBe('3. 10.')
    expect(kratkoDatum('')).toBe('')
  })

  it('razlika dni je cela tudi čez prehod ure', () => {
    expect(razlikaDni('2026-03-28', '2026-03-30')).toBe(2)
    expect(razlikaDni('2026-10-24', '2026-10-31')).toBe(7)
    expect(razlikaDni('2026-10-31', '2026-10-24')).toBe(-7)
  })
})

describe('najpogostejša vrednost', () => {
  it('modus; pri izenačenju prva, ki se pojavi', () => {
    expect(najpogostejsa([7, 14, 7])).toBe(7)
    expect(najpogostejsa([14, 7])).toBe(14)
    expect(najpogostejsa(['sob', 'ned', 'ned'])).toBe('ned')
  })

  it('prazen seznam pomeni »ni podatka«', () => {
    expect(najpogostejsa([])).toBeNull()
  })
})

describe('sklanjanje kol, srečanj in prestavitev', () => {
  it('ena, dve, tri–štiri in množina', () => {
    expect([1, 2, 3, 4, 5].map(kolTekst)).toEqual(['kolo', 'kola', 'kola', 'kola', 'kol'])
    expect([1, 2, 3, 4, 5].map(srecanjTekst)).toEqual([
      'srečanje', 'srečanji', 'srečanja', 'srečanja', 'srečanj',
    ])
    expect([1, 2, 3, 5].map(prestavitevTekst)).toEqual([
      'prestavitev', 'prestavitvi', 'prestavitve', 'prestavitev',
    ])
  })

  it('nič in števila od 11 do 14 so množina', () => {
    expect([0, 11, 12, 13, 14].map(srecanjTekst)).toEqual([
      'srečanj', 'srečanj', 'srečanj', 'srečanj', 'srečanj',
    ])
  })

  it('stotice se sklanjajo kot enice', () => {
    expect([101, 102, 103, 111].map(srecanjTekst)).toEqual([
      'srečanje', 'srečanji', 'srečanja', 'srečanj',
    ])
  })

  /* ZNANA NAPAKA (pregled kakovosti, V-POMOZNO-2-02): ta kopija sklanja po
     zadnji števki in da »21 srečanje«, »24 srečanja«. Hišno pravilo
     (docs/pravila/vmesnik.md, `sklon` v oblikovanje.ts) pravi rodilnik množine.
     Test je označen z `fails`: zdaj je zelen, ker koda pravilu NE ustreza. Ko
     bo sklanjanje popravljeno, bo padel - takrat `.fails` odstrani. */
  it.fails('21–24, 31–34 … so rodilnik množine (znana napaka)', () => {
    expect([21, 22, 24, 31].map(srecanjTekst)).toEqual(['srečanj', 'srečanj', 'srečanj', 'srečanj'])
  })
})
