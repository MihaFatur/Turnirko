import { describe, expect, it } from 'vitest'

import { besedeIskanja, ustrezaBesedam, zadetihZacetkov, zaIskanje } from './iskanje'

describe('iskanje po imenih', () => {
  it('brez šumnikov in velikih črk', () => {
    expect(zaIskanje('Križan')).toBe('krizan')
    expect(zaIskanje('ČŠŽ ćž')).toBe('csz cz')
  })

  it('vpisano se razbije na besede, presledki odpadejo', () => {
    expect(besedeIskanja('  Novak   ana ')).toEqual(['novak', 'ana'])
    expect(besedeIskanja('')).toEqual([])
    expect(besedeIskanja('   ')).toEqual([])
  })

  it('ustrezati morajo VSE besede, vrstni red ni pomemben', () => {
    const besedilo = 'Ana Novak NTK Žalec'
    expect(ustrezaBesedam(besedilo, besedeIskanja('novak ana'))).toBe(true)
    expect(ustrezaBesedam(besedilo, besedeIskanja('zalec'))).toBe(true)
    expect(ustrezaBesedam(besedilo, besedeIskanja('novak eva'))).toBe(false)
  })

  it('prazno iskanje ustreza vsem', () => {
    expect(ustrezaBesedam('Ana Novak', [])).toBe(true)
  })

  it('šumnik v iskanju najde ime brez njega in obratno', () => {
    expect(ustrezaBesedam('Krizan', besedeIskanja('križan'))).toBe(true)
    expect(ustrezaBesedam('Križan', besedeIskanja('krizan'))).toBe(true)
  })

  /* ZNANA NAPAKA (pregled kakovosti, V-POMOZNO-1-11): »đ« nima razgradnje v
     Unicode, zato ga odstranitev naglasnih znamenj ne spremeni v »d« in
     »dordevic« ne najde Đorđevića. Test je označen z `fails`; ko bo iskanje
     popravljeno, bo padel - takrat `.fails` odstrani. */
  it.fails('»dordevic« najde Đorđevića (znana napaka)', () => {
    expect(ustrezaBesedam('Đorđević', besedeIskanja('dordevic'))).toBe(true)
  })
})

describe('zadetki na začetku besede', () => {
  /* Predlogov je le osem: »ana« mora ponuditi Ano pred Dijano. */
  it('šteje besede, ki zadenejo začetek kake besede', () => {
    expect(zadetihZacetkov('Ana Novak', ['ana'])).toBe(1)
    expect(zadetihZacetkov('Dijana Novak', ['ana'])).toBe(0)
    expect(zadetihZacetkov('Ana Novak', ['ana', 'nov'])).toBe(2)
  })

  it('beseda se začne tudi za vezajem in ločili', () => {
    expect(zadetihZacetkov('Kos-Novak Ana', ['novak'])).toBe(1)
    expect(zadetihZacetkov('Novak Ana (NTK Žalec)', ['ntk', 'zal'])).toBe(2)
  })
})
