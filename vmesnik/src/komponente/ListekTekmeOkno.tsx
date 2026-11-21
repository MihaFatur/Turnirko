/* Fotografija sodniškega listka tekme (branje listkov, F4.4): organizator
   turnirja ali admin jo odpre iz okna s točkami po nizih, ko želi preveriti, kaj
   je sodnik v resnici napisal. Na voljo je zadnje potrjeno branje tekme, dokler
   se fotografija ne izbriše (30 dni po turnirju). Gost okna ne vidi nikoli.

   Slika gre prek odjemalca, ker <img src> ne pošlje glave Authorization; naslov
   Blob se ob zaprtju prekliče. */
import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { branjeListkovApi } from '../api/zahteve'
import { ModalnoOkno } from './ModalnoOkno'

export function ListekTekmeOkno({ idTekme, onZapri }: { idTekme: number; onZapri: () => void }) {
  const slika = useQuery({
    queryKey: ['listek-tekme', idTekme],
    queryFn: () => branjeListkovApi.listekTekme(idTekme),
    retry: false,
    gcTime: 0,
  })
  const [naslov, nastaviNaslov] = useState<string | null>(null)
  useEffect(() => {
    if (!slika.data) return
    const n = URL.createObjectURL(slika.data)
    nastaviNaslov(n)
    return () => URL.revokeObjectURL(n)
  }, [slika.data])

  return (
    <ModalnoOkno naslov="Listek tekme" onZapri={onZapri}>
      {slika.isPending && <p className="obvestilo">Nalaganje …</p>}
      {slika.isError && <p className="namig">{opisNapake(slika.error)}</p>}
      {naslov && <img className="listek-tekme__slika" src={naslov} alt="Fotografija sodniškega listka" />}
    </ModalnoOkno>
  )
}
