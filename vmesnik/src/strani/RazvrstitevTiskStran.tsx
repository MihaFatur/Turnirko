/* Natisljiva končna razvrstitev zaključenega dogodka (/dogodki/:id/razvrstitev-tisk).
   Zasnova kot žreb skupin in mreže: stran je zunaj skupne postavitve, tiska jo
   brskalnik (window.print), zaledje ne rabi ničesar svojega - vse je že v mreži
   dogodka. Natisne se VSA razvrstitev, ne glede na to, ali je seznam ostalih na
   zaslonu razgrnjen. */
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import { OZNAKE_SISTEM_KRATKO } from '../api/tipi'
import type { DogodekDto } from '../api/tipi'
import { KoncnaRazvrstitevTisk } from '../komponente/KoncnaRazvrstitevTisk'
import { ZrebOkvir } from '../komponente/ZrebOkvir'
import { udelezenciRazvrstitve } from '../pomozno/koncnaRazvrstitev'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import { oznakaKategorije } from '../pomozno/zrebSkupin'

/* Kategorija, disciplina in sistem v eni vrstici pod naslovom lista. */
function lastnostiDogodka(dogodek: DogodekDto): string {
  const sistem = OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja]
  return [
    oznakaKategorije(dogodek),
    dogodek.disciplina === 'EKIPNO' ? 'Ekipno' : dogodek.disciplina === 'DVOJICE' ? 'Dvojice' : 'Posamezno',
    dogodek.sistemTekmovanja === 'IZLOCILNI' || dogodek.sistemTekmovanja === 'KROZNI'
      ? `${sistem} sistem`
      : sistem,
  ].join(' · ')
}

export function RazvrstitevTiskStran() {
  const { id } = useParams()
  const idDogodka = Number(id)

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja je naslov lista; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })

  const udelezenci = useMemo(() => (mreza.data ? udelezenciRazvrstitve(mreza.data) : []), [mreza.data])
  const dogodek = mreza.data?.dogodek
  const ekipno = dogodek?.disciplina === 'EKIPNO'
  const skupaj = `${udelezenci.length} ${ekipno ? sklonEkip(udelezenci.length) : sklonIgralcev(udelezenci.length)}`

  return (
    <ZrebOkvir
      nazaj={`/dogodki/${idDogodka}`}
      nazajOznaka="← Nazaj na dogodek"
      naslov={dogodek ? `Končna razvrstitev · ${dogodek.ime}` : null}
      nalaganje={mreza.isPending}
      napaka={mreza.error}
      stevec={skupaj}
      steviloZaTisk={udelezenci.length}
      prazno="Dogodek še nima končne razvrstitve. Natisne se, ko je dogodek zaključen."
      razred="zreb-stran--razvrstitev"
    >
      {dogodek && (
        <KoncnaRazvrstitevTisk
          naslov={turnir.data?.ime ?? dogodek.ime}
          lastnosti={lastnostiDogodka(dogodek)}
          skupaj={skupaj}
          udelezenci={udelezenci}
        />
      )}
    </ZrebOkvir>
  )
}
