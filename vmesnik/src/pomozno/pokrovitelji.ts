/* Pokrovitelji lige ali turnirja (V56): ena poizvedba za trak na strani in za
   nogo tiskanih listov, da si odgovor delita (isti ključ). */
import { useQuery } from '@tanstack/react-query'

import { ligeApi, turnirjiApi } from '../api/zahteve'
import type { PokroviteljDto } from '../api/tipi'

export function usePokrovitelje(vrsta: 'liga' | 'turnir', id: number | null | undefined): PokroviteljDto[] {
  const pokrovitelji = useQuery({
    queryKey: ['pokrovitelji', vrsta, id],
    queryFn: () => (vrsta === 'liga' ? ligeApi.pokrovitelji(id!) : turnirjiApi.pokrovitelji(id!)),
    enabled: id != null,
  })
  return pokrovitelji.data ?? []
}
