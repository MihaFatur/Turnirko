/* »Dodaj v družino« na profilu igralca (V59, paket Navijač): zahteva za povezavo
   igralca do 21 let z računom navijača. Ne odpre nič: zasebna statistika se odpre,
   ko povezavo potrdi administrator (DruzinaStoritev). Gumb vidi samo račun z
   veljavnim paketom Navijač; igralec, ki je očitno odrasel (po starostnem pasu z
   lestvice), ga ne dobi — strežnik pas vseeno preveri sam, tu je samo vljudnost,
   da gumb ne obljublja, česar ne bo. */
import { useMutation, useQueryClient } from '@tanstack/react-query'

import type { StarostniPas } from '../api/tipi'
import { druzinaApi } from '../api/zahteve'
import { useDruzina } from '../pomozno/druzina'
import { SporociloNapake } from './SporociloNapake'

const ODRASLI: (StarostniPas | null | undefined)[] = ['CLANI', 'VETERANI']

export function DruzinaGumb({
  idIgralec,
  pas,
}: {
  idIgralec: number
  /* Starostni pas igralca z lestvice; prazno, če ga lestvica ne pozna. */
  pas: StarostniPas | null | undefined
}) {
  const druzina = useDruzina()
  const odjemalec = useQueryClient()
  const zahtevaj = useMutation({
    mutationFn: () => druzinaApi.zahtevaj(idIgralec),
    onSettled: () => odjemalec.invalidateQueries({ queryKey: ['druzina'] }),
  })

  const pregled = druzina.pregled
  if (!pregled?.smemZahtevati) return null
  const povezava = druzina.povezava(idIgralec)

  if (povezava) {
    const besedilo = povezava.status === 'CAKA'
      ? 'Družina: čaka na potrditev administratorja'
      : povezava.status === 'ZAVRNJENA'
        ? 'Družina: zahteva je zavrnjena'
        : povezava.veljavna
          ? 'Družina: zasebna statistika je odprta spodaj'
          : 'Družina: povezava trenutno ne velja'
    return <span className="profil__druzina-stanje">{besedilo}</span>
  }
  if (ODRASLI.includes(pas)) return null
  if (pregled.povezave.filter((p) => p.status !== 'ZAVRNJENA').length >= pregled.najvec) return null

  return (
    <>
      <button
        type="button"
        className="povezava-gumb"
        disabled={zahtevaj.isPending}
        onClick={() => zahtevaj.mutate()}
      >
        Dodaj v družino
      </button>
      <SporociloNapake napaka={zahtevaj.error} />
    </>
  )
}
