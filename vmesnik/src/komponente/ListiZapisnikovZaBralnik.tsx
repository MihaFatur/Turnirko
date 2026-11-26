/* Zapisniki za bralnik za tisk: en ležeči A4 na srečanje (@page zapisnik-bralnik,
   rob 0 - postavitev je v mm od roba lista). Na ozkem zaslonu se listi pomanjšajo,
   pri tisku ne. Kode QR pripravi strežnik (zapisnikiApi.natisni) samo
   organizatorju tekmovanja in adminu. */
import { useQuery } from '@tanstack/react-query'

import type { OblikaZapisnikaBralnika } from '../api/tipi'
import { zapisnikiApi } from '../api/zahteve'
import { SIRINA_LEZECEGA_LISTA_PX, useMeriloLista } from '../pomozno/meriloLista'
import { SporociloNapake } from './SporociloNapake'
import { ZapisnikZaBralnik } from './ZapisnikZaBralnik'

export function ListiZapisnikovZaBralnik({
  srecanja,
  oblika,
}: {
  srecanja: number[]
  /* null = samodejna (vrstice tekem, kadar gre kader nanje, sicer tabela kadra) */
  oblika: OblikaZapisnikaBralnika | null
}) {
  const merilo = useMeriloLista(SIRINA_LEZECEGA_LISTA_PX)
  const natisi = useQuery({
    queryKey: ['zapisniki-natis', srecanja, oblika],
    queryFn: () => zapisnikiApi.natisni(srecanja, oblika),
    enabled: srecanja.length > 0,
    staleTime: Infinity,
    retry: false,
  })

  if (srecanja.length === 0) {
    return <p className="obvestilo zaslon-samo">V tem kolu ni srečanja, ki bi čakalo na zapisnik.</p>
  }
  if (natisi.isPending) return <p className="obvestilo zaslon-samo">Pripravljam zapisnike …</p>
  if (natisi.error || !natisi.data) return <SporociloNapake napaka={natisi.error} />

  return (
    <div className="zapisnik-bralnik-listi" ref={merilo.okvir} style={merilo.slog}>
      {natisi.data.map((n) => (
        <div className="zapisnik-bralnik-list" key={n.idNatis}>
          <ZapisnikZaBralnik natis={n} />
        </div>
      ))}
    </div>
  )
}
