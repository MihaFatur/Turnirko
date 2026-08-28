/* Sklop »Koledar« na domači strani: mreža tekočega meseca in ob njej to, kar
   pride naslednje.

   Zakaj stoji pod Lestvico in nad »1 na 1«: gledalec brez prijave (uporabnik
   št. 1) pride med drugim po odgovor »kdaj in kje je naslednji turnir ali
   ligaško srečanje« — PRODUCT.md ga našteva med njegovimi štirimi opravili.
   Doslej je moral do njega skozi seznam turnirjev IN seznam lig.

   Mesec je vedno tekoči in puščic za listanje tu ni: listanje (tudi po
   uvoženi zgodovini) pripada celotnemu koledarju, sklop pa mora ostati kratek.
   Zato se poizvedba razteza ŠTIRI mesece naprej, mreža pa kaže samo tega:
   sklop »Naslednje« mora imeti kaj pokazati tudi v mesecu, ko se ne igra nič
   — sicer je koledar poleti prazen kvadrat brez pojasnila. */
import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { koledarApi } from '../api/zahteve'
import { MrezaMeseca, VrsticaKoledarja } from './Koledar'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import {
  danesIso,
  kljucTekmovanja,
  mesecIzIso,
  premakniMesec,
  prihajajoci,
  prviDanMeseca,
  zadnjiDanMeseca,
} from '../pomozno/koledar'
import { sklonTekmovanj } from '../pomozno/oblikovanje'

/* Koliko mesecev naprej naloži sklop in koliko vrstic pokaže »Naslednje«.
   Štirje meseci pokrijejo tudi poletni premor med sezonama. */
const MESECEV_NAPREJ = 3
const NASLEDNJIH = 3

/* Celica sklopa meri 58 px in ne 92 px kot na strani koledarja, zato gredo
   vanjo trije pasovi trakov; ostalo dan prešteje s »+N«. */
const PASOV = 3

export function KoledarSklop() {
  const kamor = useNavigate()
  const danes = danesIso()
  const mesec = mesecIzIso(danes)

  const od = prviDanMeseca(mesec)
  const doKdaj = zadnjiDanMeseca(premakniMesec(mesec, MESECEV_NAPREJ))

  const koledar = useQuery({
    queryKey: ['koledar', od, doKdaj],
    queryFn: () => koledarApi.obdobje(od, doKdaj),
  })

  const vnosi = useMemo(() => koledar.data ?? [], [koledar.data])

  /* Mreža kaže samo tekoči mesec. */
  const vMesecu = useMemo(
    () => vnosi.filter((v) => v.datum <= zadnjiDanMeseca(mesec) && v.datumKonca >= od),
    [vnosi, mesec, od],
  )

  const naslednji = useMemo(() => prihajajoci(vnosi, danes).slice(0, NASLEDNJIH), [vnosi, danes])

  return (
    <div className="domov__sklop">
      <div className="naslovna-vrstica">
        <h2>Koledar</h2>
        <Link to="/koledar" className="sekcija__meta">
          Celoten koledar →
        </Link>
      </div>

      <NapakaPoizvedbe poizvedba={koledar} kaj="koledarja" />

      <div className="domov__koledar">
        <div className="domov__koledar-mreza">
          <MrezaMeseca
            mesec={mesec}
            vnosi={vMesecu}
            danes={danes}
            /* Klik na dan odpre celoten koledar pri tem dnevu — v sklopu
               samem ni prostora za izpis dneva. */
            naDan={(dan) => kamor(`/koledar?dan=${dan}`)}
            desno={`${vMesecu.length} ${sklonTekmovanj(vMesecu.length)}`}
            najvecPasov={PASOV}
            /* Za »+N« v 58 px celici pod trakovi ni prostora; kar ne gre v
               mrežo, pove celoten koledar. */
            sStevcem={false}
            sklop
          />
          {!koledar.isPending && vMesecu.length === 0 && (
            <p className="domov__prazno">Ta mesec ni tekmovanj.</p>
          )}
        </div>

        <div className="domov__koledar-naslednje">
          <h3 className="podnaslov-sekcije">Naslednje</h3>
          {koledar.isPending && <p className="obvestilo">Nalaganje …</p>}
          {!koledar.isPending && naslednji.length === 0 && (
            <p className="domov__prazno">
              Napovedanih tekmovanj ni. Ko organizator vpiše datume, se pojavijo tukaj.
            </p>
          )}
          {naslednji.map((vnos) => (
            <VrsticaKoledarja
              key={`${kljucTekmovanja(vnos)}-${vnos.datum}-${vnos.kolo ?? ''}`}
              vnos={vnos}
              danes={danes}
            />
          ))}
          <Link to="/koledar" className="gumb domov__koledar-gumb">
            Poglej si celoten koledar
          </Link>
        </div>
      </div>
    </div>
  )
}
