// @vitest-environment jsdom
/* Neuspela osvežitev ne sme zamenjati strani (pregled kakovosti, G2).

   Test ne naloži cele strani dogodka (2300 vrstic, deset poizvedb), ampak
   majhno stran z ISTIM vzorcem, kot ga imajo strani dogodka, turnirja, lige in
   srečanja: `prikazStrani` odloči o celostranskem nadomestku, nad vsebino stoji
   `OpozoriloOsvezitve`. Poizvedba je pravi TanStack Query, da test drži tudi
   njegovo vedenje (ob neuspeli osvežitvi podatke obdrži in nastavi napako). */
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { NapakaStreznika } from '../api/odjemalec'
import { prikazStrani } from '../pomozno/osvezevanje'
import { OpozoriloOsvezitve } from './OpozoriloOsvezitve'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

/* Kaj strežnik odgovori na naslednjo zahtevo. */
let odgovor: () => Promise<{ izid: string }>

function Stran() {
  const dogodek = useQuery({ queryKey: ['dogodek', 1], queryFn: () => odgovor(), retry: false })
  const prikaz = prikazStrani(dogodek)
  if (prikaz === 'nalaganje') return <p>Nalaganje …</p>
  if (prikaz === 'brez-povezave') return <p>Ni povezave</p>
  if (prikaz === 'napaka') return <p role="alert">Napaka strani</p>
  if (!dogodek.data) return <p>Tega dogodka ni (več).</p>
  return (
    <section>
      <OpozoriloOsvezitve poizvedba={dogodek} />
      <h1>Finale {dogodek.data.izid}</h1>
    </section>
  )
}

let vsebnik: HTMLDivElement
let koren: Root
let poizvedbe: QueryClient

/* TanStack Query obvestila opazovalcem pošlje v naslednjem obhodu zanke
   dogodkov (setTimeout 0), zato act počaka še nanj. */
const naslednjiObhod = () => new Promise((razresi) => setTimeout(razresi, 0))

async function izrisi() {
  await act(async () => {
    koren.render(
      <QueryClientProvider client={poizvedbe}>
        <Stran />
      </QueryClientProvider>,
    )
  })
  /* Prvi prenos se začne v učinku po izrisu; počakamo, da se konča. */
  await act(async () => {
    await naslednjiObhod()
    await naslednjiObhod()
  })
}

/* Osvežitev, kot jo sproži interval: vse poizvedbe se prenesejo znova. */
async function osvezi() {
  await act(async () => {
    await poizvedbe.refetchQueries()
    await naslednjiObhod()
  })
}

const besedilo = () => vsebnik.textContent ?? ''

beforeEach(() => {
  poizvedbe = new QueryClient()
  vsebnik = document.createElement('div')
  document.body.appendChild(vsebnik)
  koren = createRoot(vsebnik)
  odgovor = async () => ({ izid: '3 : 1' })
})

afterEach(() => {
  act(() => koren.unmount())
  vsebnik.remove()
})

describe('neuspela osvežitev strani', () => {
  it('izpad pri osvežitvi pusti zadnje podatke in pokaže opozorilo', async () => {
    await izrisi()
    expect(besedilo()).toContain('Finale 3 : 1')
    expect(vsebnik.querySelector('.opozorilo-osvezitve')).toBeNull()

    odgovor = async () => {
      throw new TypeError('Failed to fetch')
    }
    await osvezi()

    expect(besedilo()).toContain('Finale 3 : 1')
    expect(vsebnik.querySelector('[role="alert"]')).toBeNull()
    const opozorilo = vsebnik.querySelector('.opozorilo-osvezitve')
    expect(opozorilo?.textContent).toContain('Osvežitev ni uspela')
    expect(opozorilo?.textContent).toMatch(/prikazano je stanje ob \d{2}:\d{2}/)
  })

  it('napaka strežnika 5xx pri osvežitvi prav tako ne zamenja strani', async () => {
    await izrisi()
    odgovor = async () => {
      throw new NapakaStreznika(503, 'Napaka', 'Strežnik se zaganja.')
    }
    await osvezi()

    expect(besedilo()).toContain('Finale 3 : 1')
    expect(vsebnik.querySelector('.opozorilo-osvezitve')).not.toBeNull()
  })

  it('ko naslednja osvežitev uspe, opozorilo izgine in podatki so novi', async () => {
    await izrisi()
    odgovor = async () => {
      throw new TypeError('Failed to fetch')
    }
    await osvezi()
    odgovor = async () => ({ izid: '3 : 2' })
    await osvezi()

    expect(besedilo()).toContain('Finale 3 : 2')
    expect(vsebnik.querySelector('.opozorilo-osvezitve')).toBeNull()
  })

  it('gumb »Poskusi znova« sproži nov prenos', async () => {
    await izrisi()
    odgovor = async () => {
      throw new TypeError('Failed to fetch')
    }
    await osvezi()
    odgovor = async () => ({ izid: '3 : 2' })

    const gumb = vsebnik.querySelector<HTMLButtonElement>('.opozorilo-osvezitve button')
    expect(gumb?.textContent).toBe('Poskusi znova')
    await act(async () => {
      gumb?.click()
      await naslednjiObhod()
    })

    expect(besedilo()).toContain('Finale 3 : 2')
    expect(vsebnik.querySelector('.opozorilo-osvezitve')).toBeNull()
  })

  it('napaka ob PRVEM nalaganju ostane celostranska, kot prej', async () => {
    odgovor = async () => {
      throw new NapakaStreznika(503, 'Napaka', 'Strežnik se zaganja.')
    }
    await izrisi()

    expect(vsebnik.querySelector('[role="alert"]')?.textContent).toBe('Napaka strani')
    expect(vsebnik.querySelector('.opozorilo-osvezitve')).toBeNull()
  })

  it('izbrisan dogodek (404 pri osvežitvi) se ne kaže naprej kot veljaven', async () => {
    await izrisi()
    odgovor = async () => {
      throw new NapakaStreznika(404, 'Ni najdeno', 'Dogodek ne obstaja.')
    }
    await osvezi()

    expect(besedilo()).not.toContain('Finale')
    expect(vsebnik.querySelector('[role="alert"]')).not.toBeNull()
  })
})
