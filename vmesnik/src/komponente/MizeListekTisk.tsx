/* Listek z vpisano mizo ob klicu tekme (stran Mize, 24. 11. 2026).

   Organizator ob klicu tekme na mizo natisne sodniški listek, na katerem je številka mize
   že vpisana v okence (Listek, `miza`). Listek je isti kot na strani Listki (podatki iz
   žreba dogodka, koda QR s skrivnostjo za sodnika); tu je samo skrit natisljiv vsebnik, ki
   ga zahteva klic, in `window.print()`. Listi so isti kot tam (A4, po šest listkov, rezi),
   zato se več sočasno poklicanih tekem natisne na en list.

   Vsebnik je v portalu v body in je na zaslonu skrit (.listki-stran--listki); pri tisku
   se skrije ostala aplikacija (slog.css, razdelek »Mize«). Sled tiska (»natisnjeno« na
   tekmi) se tu NE zapisuje: potrdi jo organizator na strani Listki po tisku, ker brskalnik
   ne pove, ali je tiskalnik res natisnil. */
import { Fragment, useCallback, useEffect, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useQueryClient } from '@tanstack/react-query'

import { dogodkiApi } from '../api/zahteve'
import { LISTKOV_NA_LIST, pripraviListke } from '../pomozno/listki'
import { podatkiListka } from '../strani/ListkiStran'
import { HrbetListka, Listek, type ListekPodatki } from './Listek'

export interface NalogaTiska {
  idDogodka: number
  idTekme: number
  miza: number
  /* Turnir tekme, kadar ni turnir strani (skupne mize, V78: tekma drugega turnirja). */
  imeTurnirja?: string
}

interface ListekZaTisk {
  kljuc: string
  podatki: ListekPodatki
}

export function useTiskListkov(imeTurnirja: string): {
  natisni: (naloge: NalogaTiska[]) => Promise<void>
  izris: ReactNode
} {
  const odjemalec = useQueryClient()
  const [zaTisk, nastaviZaTisk] = useState<{ listki: ListekZaTisk[]; hrbet: boolean } | null>(null)

  const natisni = useCallback(
    async (naloge: NalogaTiska[]) => {
      const idji = [...new Set(naloge.map((n) => n.idDogodka))]
      const vsi: ListekZaTisk[] = []
      let hrbet = false
      for (const id of idji) {
        /* Žreb se prebere znova: tekma je pravkar postala »v igri«, skupina in kolo pa
           morata biti na listku taka kot na strani Listki. */
        const mreza = await odjemalec.fetchQuery({
          queryKey: ['dogodek', id],
          queryFn: () => dogodkiApi.mreza(id),
          staleTime: 0,
        })
        /* Brez kode s skrivnostjo (organizator je ne dobi ob napaki) listek še vedno deluje:
           izid z njega prebere pult, sodnikova pot pa ne. */
        const kode = await dogodkiApi.kodeListkov(id).catch(() => null)
        if (kode?.sodnikiListki) hrbet = true
        const listki = pripraviListke(mreza)
        for (const n of naloge.filter((x) => x.idDogodka === id)) {
          const l = listki.find((x) => x.tekma.id === n.idTekme)
          if (!l) continue
          vsi.push({
            kljuc: `tekma-${n.idTekme}`,
            podatki: { ...podatkiListka(l, n.imeTurnirja ?? imeTurnirja, mreza.dogodek, kode?.kode[String(n.idTekme)] ?? null), miza: n.miza },
          })
        }
      }
      if (vsi.length > 0) nastaviZaTisk({ listki: vsi, hrbet })
    },
    [odjemalec, imeTurnirja],
  )

  /* Tisk po izrisu vsebnika. Časovnik namesto requestAnimationFrame: v zavihku, ki ni v
     ospredju, se sliki ne izrisujeta. window.print() ob zaprtju okna vrne nadzor. */
  useEffect(() => {
    if (!zaTisk) return
    const casovnik = window.setTimeout(() => {
      window.print()
      nastaviZaTisk(null)
    }, 150)
    return () => window.clearTimeout(casovnik)
  }, [zaTisk])

  const izris = zaTisk
    ? createPortal(<ListiZaTisk listki={zaTisk.listki} hrbet={zaTisk.hrbet} />, document.body)
    : null

  return { natisni, izris }
}

function ListiZaTisk({ listki, hrbet }: { listki: ListekZaTisk[]; hrbet: boolean }) {
  const listi: ListekZaTisk[][] = []
  for (let i = 0; i < listki.length; i += LISTKOV_NA_LIST) listi.push(listki.slice(i, i + LISTKOV_NA_LIST))
  const naslovSodnika = `${window.location.origin}/s`
  return (
    <div className="listki-stran listki-stran--listki mize-tisk">
      <div className="listki-listi">
        {listi.map((list, i) => (
          <Fragment key={i}>
            <section className="listki-list" aria-label={`List ${i + 1} od ${listi.length}`}>
              {list.map((l) => (
                <Listek key={l.kljuc} podatki={l.podatki} />
              ))}
              {Array.from({ length: LISTKOV_NA_LIST - list.length }, (_, j) => (
                <div key={`prazno-${j}`} className="listek listek--prazen" aria-hidden="true" />
              ))}
            </section>
            {hrbet && (
              <section className="listki-list listki-list--hrbet" aria-label={`Hrbtna stran lista ${i + 1}`}>
                {Array.from({ length: LISTKOV_NA_LIST }, (_, j) => (
                  <HrbetListka key={j} naslov={naslovSodnika} />
                ))}
              </section>
            )}
          </Fragment>
        ))}
      </div>
    </div>
  )
}
