/* Okno »Družine« v Dostopih (samo admin, V59): zahteve navijačev, da bi v družino
   povezali igralca do 21 let, in že potrjene povezave.

   Admin po izpisu odloča o dostopu do zasebne statistike otroka, zato vidi tisto,
   kar potrebuje za odločitev: kdo zahteva (račun, e-pošta, ali ima veljaven paket
   Navijač), katerega igralca (klub, datum rojstva, starostni pas) in kdaj. Potrditev
   da navijaču zasebno statistiko in otrokovemu računu Premium; zavrnitev vrstico
   obdrži (oseba zahteve ne ponavlja tiho). Datum rojstva ostane v tem oknu: javno ni
   nikjer, v pošto navijaču ne gre. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { OZNAKE_STAROSTNI_PAS, type DruzinaAdmin } from '../api/tipi'
import { druzinaApi } from '../api/zahteve'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { SporociloNapake } from './SporociloNapake'

/* Napis gumba v kartoteki: število zahtev, ki čakajo na odločitev, da jih admin opazi. */
export function DruzineNapis() {
  const seznam = useQuery({ queryKey: ['druzina-admin'], queryFn: druzinaApi.admin })
  const cakajocih = (seznam.data ?? []).filter((z) => z.status === 'CAKA').length
  return <>Družine{cakajocih > 0 ? ` (${cakajocih})` : ''}</>
}

export function DruzineOkno({ onZapri }: { onZapri: () => void }) {
  const seznam = useQuery({ queryKey: ['druzina-admin'], queryFn: druzinaApi.admin })
  const cakajoce = (seznam.data ?? []).filter((z) => z.status === 'CAKA')
  const potrjene = (seznam.data ?? []).filter((z) => z.status === 'POTRJENA')

  return (
    <ModalnoOkno nadnaslov="Dostopi" naslov="Družine" siroko onZapri={onZapri}>
      <p className="kode__uvod">
        Navijač s paketom Navijač lahko v družino poveže do dva igralca do 21 let in vidi njuno zasebno
        statistiko; otrokov račun dobi Premium. Brez tvoje potrditve ne odpre nič.
      </p>
      {seznam.isPending && <p className="obvestilo">Nalaganje …</p>}
      <SporociloNapake napaka={seznam.error} />

      {seznam.data && cakajoce.length === 0 && potrjene.length === 0 && (
        <p className="kode__prazno">Družinskih povezav še ni.</p>
      )}
      {cakajoce.length > 0 && (
        <>
          <h3 className="druzine__naslov">Čakajo na odločitev ({cakajoce.length})</h3>
          <ul className="kode__seznam">
            {cakajoce.map((z) => (
              <Vrstica zahteva={z} key={z.id} />
            ))}
          </ul>
        </>
      )}
      {potrjene.length > 0 && (
        <>
          <h3 className="druzine__naslov">Potrjene povezave ({potrjene.length})</h3>
          <ul className="kode__seznam">
            {potrjene.map((z) => (
              <Vrstica zahteva={z} key={z.id} />
            ))}
          </ul>
        </>
      )}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Zapri
        </button>
      </div>
    </ModalnoOkno>
  )
}

function Vrstica({ zahteva: z }: { zahteva: DruzinaAdmin }) {
  const odjemalec = useQueryClient()
  const [razvezava, nastaviRazvezavo] = useState(false)
  const osvezi = () => {
    void odjemalec.invalidateQueries({ queryKey: ['druzina-admin'] })
    void odjemalec.invalidateQueries({ queryKey: ['druzina'] })
  }
  const potrdi = useMutation({ mutationFn: () => druzinaApi.potrdi(z.id), onSuccess: osvezi })
  const zavrni = useMutation({ mutationFn: () => druzinaApi.zavrni(z.id), onSuccess: osvezi })
  const razveze = useMutation({ mutationFn: () => druzinaApi.razveze(z.id), onSuccess: osvezi })
  const zaposlen = potrdi.isPending || zavrni.isPending || razveze.isPending

  return (
    <li className="kode__vrstica druzine__vrstica">
      <div>
        <div className="kode__koda">
          {z.racun} <span className="kode__meta">{z.email}</span>
        </div>
        <div className="kode__meta">
          igralec <strong>{z.igralec}</strong>
          {z.klub ? ` · ${z.klub}` : ''}
          {z.datumRojstva ? ` · rojen ${oblikujDatum(z.datumRojstva)}` : ''}
          {z.pas ? ` · ${OZNAKE_STAROSTNI_PAS[z.pas] ?? z.pas}` : ''}
        </div>
        <div className="kode__meta">
          zahtevano {oblikujDatum(z.zahtevanaOb)}
          {z.veljaDo ? ` · velja do ${oblikujDatum(z.veljaDo)} (igralec je prerasel U21)` : ''}
          {z.placnikImaPaket ? '' : ' · račun trenutno nima veljavnega paketa Navijač'}
        </div>
        <SporociloNapake napaka={potrdi.error ?? zavrni.error ?? razveze.error} />
      </div>
      <div className="druzine__dejanja">
        {z.status === 'CAKA' ? (
          <>
            <button type="button" className="gumb gumb--glavni" disabled={zaposlen} onClick={() => potrdi.mutate()}>
              Potrdi
            </button>
            <button type="button" className="gumb" disabled={zaposlen} onClick={() => zavrni.mutate()}>
              Zavrni
            </button>
          </>
        ) : (
          <button type="button" className="gumb" disabled={zaposlen} onClick={() => nastaviRazvezavo(true)}>
            Razveži
          </button>
        )}
      </div>
      {razvezava && (
        <PotrditvenoOkno
          naslov="Razveži družinsko povezavo"
          sporocilo={`Račun ${z.racun} ne bo več videl zasebne statistike igralca ${z.igralec}, otrokov račun pa izgubi Premium prek družine.`}
          besedaPotrditve="Razveži"
          onPotrdi={() => razveze.mutate()}
          onZapri={() => nastaviRazvezavo(false)}
        />
      )}
    </li>
  )
}
