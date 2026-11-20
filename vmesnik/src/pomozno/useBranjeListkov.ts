/* Ali prijavljeni sme uporabljati branje listkov (strežnik: ključ API, preizkus
   samo za admina). Povezava »Branje listkov« se brez tega ne pokaže. */
import { useQuery } from '@tanstack/react-query'

import { branjeListkovApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

export function useBranjeListkovNaVoljo(): boolean {
  const { uporabnik, jeAdmin, jeOrganizator } = useAvtentikacija()
  const stanje = useQuery({
    queryKey: ['branje-listkov', 'stanje', uporabnik?.id ?? null],
    queryFn: branjeListkovApi.stanje,
    enabled: uporabnik !== null && (jeAdmin || jeOrganizator),
    staleTime: 5 * 60 * 1000,
  })
  return stanje.data?.omogoceno === true
}
