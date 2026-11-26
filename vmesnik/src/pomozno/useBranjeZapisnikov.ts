/* Ali prijavljeni sme uporabljati zapisnike za bralnik (V79): organizator ali admin,
   strežnik ima mrežo števk, preizkus samo za admina je izklopljen. Povezavi
   »Zapisniki kola« in »Branje zapisnikov« se brez tega ne pokažeta. */
import { useQuery } from '@tanstack/react-query'

import { zapisnikiApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

export function useBranjeZapisnikovNaVoljo(): boolean {
  const { uporabnik, jeAdmin, jeOrganizator } = useAvtentikacija()
  const stanje = useQuery({
    queryKey: ['zapisniki', 'stanje', uporabnik?.id ?? null],
    queryFn: zapisnikiApi.stanje,
    enabled: uporabnik !== null && (jeAdmin || jeOrganizator),
    staleTime: 5 * 60 * 1000,
  })
  return stanje.data?.omogoceno === true
}
