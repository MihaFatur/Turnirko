/* Natisljivi ekipni zapisnik za eno srecanje lige ali ekipno tekmo turnirja.

   En list na srecanje: mreza vseh posamicnih tekem, sodnik ga izpolni z roko
   za mizo. Pred postavo so imena ekip ze natisnjena, postavo in igralce
   tekem vpise ekipa sama (glej ZapisnikEkipnegaDvoboja). Pot je zunaj skupne
   postavitve (brez navigacije), tiska brskalnik (window.print), brez zaledja
   in nove sheme.

   Obliki sta dve: uraden obrazec NTZS (pokoncni A4; predlogo 1. SNTL oz.
   2./3. SNTL doloca liga, na tej strani pa jo je mogoce zacasno preklopiti za
   ta natis; ekipna tekma turnirja predloge nima) in Turnirkov zapisnik (lezeci
   A4, EkipniZapisnikTurnirko). Izbrano obliko si brskalnik zapomni. */
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { ligeApi, srecanjaApi, turnirjiApi } from '../api/zahteve'
import type { PredlogaLige } from '../api/tipi'
import { EkipniZapisnikTurnirko } from '../komponente/EkipniZapisnikTurnirko'
import { IzbirnikObrazca } from '../komponente/IzbirnikObrazca'
import { ZapisnikEkipnegaDvoboja } from '../komponente/ZapisnikEkipnegaDvoboja'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { SIRINA_LEZECEGA_LISTA_PX, useMeriloLista } from '../pomozno/meriloLista'
import {
  type IzbiraObrazca,
  type OblikaZapisnika,
  preberiOblikoZapisnika,
  privzetaOblika,
  shraniOblikoZapisnika,
} from '../pomozno/oblikaZapisnika'

export function ListkiSrecanjaStran() {
  const { id } = useParams()
  const idSrecanje = Number(id)

  const podrobno = useQuery({
    queryKey: ['srecanje', idSrecanje],
    queryFn: () => srecanjaApi.podrobno(idSrecanje),
  })

  /* Ime, sezona in privzeta predloga so na ligi; srecanje pozna le njen id. */
  const idLiga = podrobno.data?.srecanje.idLiga
  const liga = useQuery({
    queryKey: ['liga', idLiga],
    queryFn: () => ligeApi.najdi(idLiga!),
    enabled: idLiga != null,
  })
  /* Ekipna tekma turnirja nosi logotip turnirja (V54), liga svojega. */
  const idTurnir = idLiga == null ? podrobno.data?.srecanje.idTurnir : null
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir != null,
  })
  const logotip = (idLiga != null ? liga.data?.logotip : turnir.data?.logotip) ?? null

  /* null = uporabi privzeto predlogo lige; sicer zacasna izbira za ta natis. */
  const [varianta, nastaviVarianto] = useState<PredlogaLige | null>(null)
  /* null = zadnje izbire ni: velja privzetek formata. */
  const [izbranaOblika, nastaviObliko] = useState<OblikaZapisnika | null>(preberiOblikoZapisnika)
  const merilo = useMeriloLista(SIRINA_LEZECEGA_LISTA_PX)

  if (podrobno.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.error || !podrobno.data) return <SporociloNapake napaka={podrobno.error} />

  const srecanje = podrobno.data.srecanje
  /* Ekipna tekma turnirja nima lige in zato ne predloge (null). */
  const izbrana: PredlogaLige | null =
    srecanje.idLiga == null ? null : (varianta ?? liga.data?.predlogaListka ?? 'SNTL_23')
  const oblika: OblikaZapisnika = izbranaOblika ?? privzetaOblika(podrobno.data.format)
  const lezeci = oblika === 'TURNIRKO'

  const izberi = (izbira: IzbiraObrazca) => {
    const nova: OblikaZapisnika = izbira === 'TURNIRKO' ? 'TURNIRKO' : 'NTZS'
    nastaviObliko(nova)
    shraniOblikoZapisnika(nova)
    if (izbira !== 'TURNIRKO' && izbira !== 'NTZS') nastaviVarianto(izbira)
  }

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/srecanja/${idSrecanje}`} className="povezava-nazaj">
          ← Nazaj na srečanje
        </Link>
        <IzbirnikObrazca oblika={oblika} predloga={izbrana} onIzbira={izberi} />
        <button className="gumb gumb--glavni" onClick={() => window.print()}>
          Natisni
        </button>
      </div>

      <div
        className={'listki-stran listki-stran--zapisnik' + (lezeci ? ' listki-stran--ekipni' : '')}
        ref={merilo.okvir}
        style={lezeci ? merilo.slog : undefined}
      >
        {/* Naslov je samo za zaslon: natisne se obrazec, ki ima svojo glavo in
            ne prenese dodatkov nad njo. */}
        <header className="listki-naslov zaslon-samo">
          <h1>
            {srecanje.domaci} : {srecanje.gost}
          </h1>
          <p>
            {[podrobno.data.kontekst.tekmovanje, podrobno.data.kontekst.dogodek].filter(Boolean).join(' · ')}
            {' · '}{podrobno.data.kontekst.opis ?? `${srecanje.kolo}. kolo`}
          </p>
        </header>

        {lezeci ? (
          <EkipniZapisnikTurnirko podrobno={podrobno.data} liga={liga.data} logotip={logotip} />
        ) : (
          <ZapisnikEkipnegaDvoboja podrobno={podrobno.data} liga={liga.data} varianta={izbrana} />
        )}
      </div>
    </>
  )
}
