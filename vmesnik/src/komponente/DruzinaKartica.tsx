/* »Družina« na strani Naročnina (V59): za navijača s paketom Navijač seznam
   povezanih igralcev do 21 let z odločitvijo administratorja, umik povezave in
   navodilo, kako se igralec doda; za igralca z Premium prek družine obvestilo,
   da paket plačuje drug račun.

   Povezava ne odpre nič, dokler je admin ne potrdi: kartica to pove naravnost,
   da oseba ne čaka na nekaj, česar nihče ne bo odobril. */
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { druzinaApi } from '../api/zahteve'
import type { DruzinskaPovezava } from '../api/tipi'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { useDruzina } from '../pomozno/druzina'
import { SporociloNapake } from './SporociloNapake'

const STANJE: Record<DruzinskaPovezava['status'], string> = {
  CAKA: 'Čaka na potrditev',
  POTRJENA: 'Potrjena',
  ZAVRNJENA: 'Zavrnjena',
}

export function DruzinaKartica() {
  const { pregled } = useDruzina()
  const odjemalec = useQueryClient()
  const umakni = useMutation({
    mutationFn: (id: number) => druzinaApi.umakni(id),
    onSettled: () => odjemalec.invalidateQueries({ queryKey: ['druzina'] }),
  })
  if (!pregled?.smemZahtevati) return null

  return (
    <div id="druzina" className="druzina-kartica">
      <div className="naslovna-vrstica">
        <h2>Družina</h2>
      </div>
      <p className="namig">
        Poveži do {pregled.najvec} igralca do 21 let: v svojem računu vidiš njuno zasebno statistiko (forma,
        nasprotniki, napoved, nizi in točke), njun račun pa dobi Premium. Povezavo potrdi administrator;
        brez tega ne odpre nič. Na profilu igralca pritisni »Dodaj v družino«.
      </p>
      {pregled.povezave.length === 0 && <p className="domov__prazno">Družina je prazna.</p>}
      {pregled.povezave.map((p) => (
        <div className="druzina-kartica__vrstica" key={p.id}>
          <span>
            <Link to={`/igralci/${p.idIgralec}/profil`}>{p.igralec}</Link>
            {p.klub ? ` · ${p.klub}` : ''}
            {p.veljaDo ? ` · velja do ${oblikujDatum(p.veljaDo)}` : ''}
          </span>
          <span className="druzina-kartica__stanje-vrstica">
            <span
              className={'druzina-kartica__stanje' + (p.veljavna ? ' druzina-kartica__stanje--veljavna' : '')}
            >
              {p.status === 'POTRJENA' && !p.veljavna ? 'Potrjena, ne velja' : STANJE[p.status]}
            </span>
            {p.status !== 'ZAVRNJENA' && (
              <button type="button" className="povezava-gumb" disabled={umakni.isPending}
                      onClick={() => umakni.mutate(p.id)}>
                Umakni
              </button>
            )}
          </span>
        </div>
      ))}
      <SporociloNapake napaka={umakni.error} />
    </div>
  )
}

/* Igralec, ki ima Premium prek družine: stran Naročnina bi mu sicer ponudila nakup. */
export function DruzinskiPremiumObvestilo() {
  const { pregled } = useDruzina()
  if (!pregled?.premiumPrekDruzine) return null
  return (
    <p className="obvestilo">
      Premium ti plačuje družinski član s paketom Navijač: zasebna statistika in domača stran po meri
      sta odprti, dokler povezavo ohranja. Lastna naročnina ni potrebna.
    </p>
  )
}
