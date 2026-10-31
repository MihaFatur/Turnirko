/* »Koledar v telefonu« (V58): osebna povezava .ics, ki jo Google in Apple koledar
   osvežujeta sama. V koledarju so turnirji in kola igralcev, ki jih račun
   spremlja; prestavitev srečanja se prenese brez dela (isti UID, nov čas).

   Povezava nima prijave — koledar v telefonu ne zna poslati gesla —, zato jo
   varuje naključen žeton: kdor povezavo ima, vidi samo ta koledar. Račun jo
   zamenja z enim klikom (stara preneha delovati). Kartica se pokaže samo
   računu s paketom Navijač ali Igralec Premium: strežnik koledar vseeno
   preveri ob vsakem branju. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { narocninaApi, osebniKoledarApi } from '../api/zahteve'
import { PotrditvenoOkno } from './PotrditvenoOkno'

export function OsebniKoledarKartica() {
  const narocnina = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  const smem = !!narocnina.data?.aktivna
    && (narocnina.data.paket === 'NAVIJAC' || narocnina.data.paket === 'PREMIUM')
  const povezava = useQuery({
    queryKey: ['moj-koledar'],
    queryFn: osebniKoledarApi.moj,
    enabled: smem,
  })
  const odjemalec = useQueryClient()
  const [vprasanje, nastaviVprasanje] = useState(false)
  const [kopirano, nastaviKopirano] = useState(false)

  const zamenjaj = useMutation({
    mutationFn: osebniKoledarApi.zamenjaj,
    onSuccess: (nova) => odjemalec.setQueryData(['moj-koledar'], nova),
  })

  if (!smem) return null
  const zeton = povezava.data?.zeton
  const https = zeton ? `${window.location.origin}/api/v1/koledar-ics/${zeton}.ics` : ''
  const webcal = https.replace(/^https?:/, 'webcal:')

  async function kopiraj() {
    try {
      await navigator.clipboard.writeText(https)
      nastaviKopirano(true)
      setTimeout(() => nastaviKopirano(false), 2500)
    } catch {
      /* brez dovoljenja za odložišče se povezava kopira ročno iz polja */
    }
  }

  return (
    <div id="koledar" className="koledar-kartica">
      <div className="naslovna-vrstica">
        <h2>Koledar v telefonu</h2>
      </div>
      <p className="namig">
        Turnirji in kola igralcev, ki jih spremljaš, v Google ali Apple koledarju. Koledar se osvežuje
        sam, zato se prestavitev srečanja prenese brez dela.
      </p>
      {povezava.error && <div className="napaka" role="alert">{opisNapake(povezava.error)}</div>}
      {zeton && (
        <>
          <label className="obrazec__polje">
            <span>Tvoja povezava</span>
            <input readOnly value={https} onFocus={(d) => d.currentTarget.select()} aria-label="Povezava koledarja" />
          </label>
          <div className="obrazec__gumbi koledar-kartica__gumbi">
            <a className="gumb gumb--glavni" href={webcal}>Dodaj v koledar</a>
            <button type="button" className="gumb" onClick={() => void kopiraj()}>
              {kopirano ? 'Kopirano' : 'Kopiraj povezavo'}
            </button>
            <button type="button" className="gumb" onClick={() => nastaviVprasanje(true)}>
              Zamenjaj povezavo
            </button>
          </div>
          <p className="namig">
            Povezave ne deli: kdor jo ima, vidi tvoj koledar. Če jo izgubiš ali deliš, jo zamenjaj in
            stara preneha delovati.
          </p>
        </>
      )}
      {zamenjaj.error != null && <div className="napaka" role="alert">{opisNapake(zamenjaj.error)}</div>}
      {vprasanje && (
        <PotrditvenoOkno
          naslov="Zamenjaj povezavo koledarja"
          sporocilo="Stara povezava preneha delovati: koledar v telefonu moraš dodati znova z novo povezavo."
          besedaPotrditve="Zamenjaj"
          onPotrdi={() => zamenjaj.mutate()}
          onZapri={() => nastaviVprasanje(false)}
        />
      )}
    </div>
  )
}
