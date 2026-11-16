/* Opozorilo nad vsebino strani, ko OSVEŽITEV podatkov ni uspela, podatki pa so
   že na zaslonu.

   Gledalec v dvorani s slabim signalom je prej ob enem samem neuspelem
   prenosu izgubil celo stran (rezultati so se zamenjali z napako, izbrani
   zavihek in kolo sta se ponastavila). Zdaj zadnje stanje ostane, ta vrstica
   pa pove, da ni sveže in od kdaj je. Ko naslednja osvežitev uspe, izgine sama.

   Napaka ob PRVEM nalaganju ni stvar te komponente: takrat ni česa pokazati in
   stran izriše NapakaPoizvedbe (odloča `prikazStrani` v pomozno/osvezevanje). */
import { osvezitevNiUspela, uraOsvezitve } from '../pomozno/osvezevanje'

interface Lastnosti {
  /* Del rezultata poizvedbe (TanStack Query). */
  poizvedba: {
    data: unknown
    error: unknown
    isPaused: boolean
    isFetching: boolean
    dataUpdatedAt: number
    refetch: () => unknown
  }
}

export function OpozoriloOsvezitve({ poizvedba }: Lastnosti) {
  if (!osvezitevNiUspela(poizvedba)) return null
  const ura = uraOsvezitve(poizvedba.dataUpdatedAt)
  return (
    <div className="opozorilo-osvezitve" role="status">
      <span>
        Osvežitev ni uspela{' '}
        <span className="opozorilo-osvezitve__pojasnilo">
          — {ura ? `prikazano je stanje ob ${ura}` : 'prikazano je zadnje naloženo stanje'}.
        </span>
      </span>
      <button
        type="button"
        className="gumb gumb--majhen"
        onClick={() => poizvedba.refetch()}
        disabled={poizvedba.isFetching}
      >
        {poizvedba.isFetching ? 'Poskušam …' : 'Poskusi znova'}
      </button>
    </div>
  )
}
