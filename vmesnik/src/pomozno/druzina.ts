/* Družina računa (V59) — ena pot za profil, stran Naročnina in sklop »Moj igralec«.

   Pregled pove, ali sme račun zahtevati povezavo (paket Navijač), njegove
   povezave s stanjem in ali ima igralčev račun Premium prek družine. Gost
   pregleda nima. Strežnik je pri vsaki zasebni poti zadnja beseda
   (DostopDoProfila): tu je samo vmesnikova slika, da ne sproži zahtev, ki bi
   bile zavrnjene. */
import { useQuery } from '@tanstack/react-query'

import type { DruzinaPregled } from '../api/tipi'
import { druzinaApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

export interface Druzina {
  pregled: DruzinaPregled | undefined
  /* Igralec je v družini računa in povezava zdaj velja (zasebna statistika je odprta). */
  jeClan: (idIgralec: number) => boolean
  /* Povezava z igralcem obstaja v katerem koli stanju (čaka, potrjena, zavrnjena). */
  povezava: (idIgralec: number) => DruzinaPregled['povezave'][number] | undefined
}

export function useDruzina(): Druzina {
  const { uporabnik } = useAvtentikacija()
  const poizvedba = useQuery({
    queryKey: ['druzina', uporabnik?.id ?? null],
    queryFn: druzinaApi.pregled,
    enabled: uporabnik !== null && uporabnik.vloga !== 'ADMIN',
  })
  const pregled = poizvedba.data
  return {
    pregled,
    jeClan: (idIgralec) => !!pregled?.povezave.some((p) => p.idIgralec === idIgralec && p.veljavna),
    povezava: (idIgralec) => pregled?.povezave.find((p) => p.idIgralec === idIgralec),
  }
}
