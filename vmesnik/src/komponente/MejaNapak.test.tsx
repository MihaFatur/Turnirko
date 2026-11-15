// @vitest-environment jsdom
import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { MejaNapak } from './MejaNapak'

/* React brez tega opozarja, da posodobitve v testu niso ovite v act(). */
;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

function Pokvarjena(): ReactNode {
  throw new Error('polje, ki ga DTO nima')
}

function Zdrava() {
  return <section>Rezultati turnirja</section>
}

let vsebnik: HTMLDivElement
let koren: Root

function izrisi(drevo: ReactNode) {
  act(() => koren.render(drevo))
}

beforeEach(() => {
  vsebnik = document.createElement('div')
  document.body.appendChild(vsebnik)
  koren = createRoot(vsebnik)
  /* React izjemo izrisa izpiše v konzolo (in meja doda svojo sled): v testu je
     pričakovana, zato izpis utišamo. */
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

afterEach(() => {
  act(() => koren.unmount())
  vsebnik.remove()
  vi.restoreAllMocks()
})

describe('MejaNapak', () => {
  it('brez napake izriše otroke nespremenjene, brez lastnega ovoja', () => {
    izrisi(
      <MejaNapak>
        <Zdrava />
      </MejaNapak>,
    )
    expect(vsebnik.innerHTML).toBe('<section>Rezultati turnirja</section>')
  })

  it('napaka v otroku pokaže sporočilo z gumbom za ponovno nalaganje', () => {
    izrisi(
      <MejaNapak>
        <Pokvarjena />
      </MejaNapak>,
    )
    const opozorilo = vsebnik.querySelector('[role="alert"]')
    expect(opozorilo?.textContent).toContain('Te strani ni bilo mogoče prikazati.')
    expect(vsebnik.querySelector('button')?.textContent).toBe('Naloži znova')
    expect(vsebnik.querySelector('a')?.getAttribute('href')).toBe('/')
  })

  it('kar stoji zunaj meje (glava, navigacija), ostane na zaslonu', () => {
    izrisi(
      <div>
        <header>Turnirko</header>
        <MejaNapak>
          <Pokvarjena />
        </MejaNapak>
      </div>,
    )
    expect(vsebnik.querySelector('header')?.textContent).toBe('Turnirko')
    expect(vsebnik.querySelector('[role="alert"]')).not.toBeNull()
  })

  it('napako zapiše v konzolo, da ostane sled', () => {
    izrisi(
      <MejaNapak>
        <Pokvarjena />
      </MejaNapak>,
    )
    const klici = vi.mocked(console.error).mock.calls
    expect(klici.some((klic) => klic[0] === 'Napaka med izrisom strani:')).toBe(true)
  })

  it('prehod na drugo stran mejo ponastavi', () => {
    izrisi(
      <MejaNapak ponastaviOb="/dogodki/1">
        <Pokvarjena />
      </MejaNapak>,
    )
    expect(vsebnik.querySelector('[role="alert"]')).not.toBeNull()

    izrisi(
      <MejaNapak ponastaviOb="/lestvica">
        <Zdrava />
      </MejaNapak>,
    )
    expect(vsebnik.querySelector('[role="alert"]')).toBeNull()
    expect(vsebnik.textContent).toBe('Rezultati turnirja')
  })

  it('na isti strani sporočilo ostane, tudi ko se starš izriše znova', () => {
    const drevo = (
      <MejaNapak ponastaviOb="/dogodki/1">
        <Pokvarjena />
      </MejaNapak>
    )
    izrisi(drevo)
    izrisi(drevo)
    expect(vsebnik.querySelector('[role="alert"]')).not.toBeNull()
  })

  it('stran, ki pade tudi po ponastavitvi, spet pokaže sporočilo (brez zanke)', () => {
    izrisi(
      <MejaNapak ponastaviOb="/dogodki/1">
        <Pokvarjena />
      </MejaNapak>,
    )
    izrisi(
      <MejaNapak ponastaviOb="/dogodki/2">
        <Pokvarjena />
      </MejaNapak>,
    )
    expect(vsebnik.querySelector('[role="alert"]')).not.toBeNull()
  })

  it('zunanja meja sporočilo postavi v odmik strani', () => {
    izrisi(
      <MejaNapak celaStran>
        <Pokvarjena />
      </MejaNapak>,
    )
    expect(vsebnik.querySelector('main.vsebina [role="alert"]')).not.toBeNull()
  })
})
