/* Izlocilni del po skupinah (SKUPINE_IZLOCILNI) in rocna izlocilna mreza.

   Ko so vse skupinske tekme odigrane, izlocilni del ne nastane sam: organizator izbere
   racunalniski zreb ali rocno razporeditev mest. Dokler se ne zacne nobena tekma izlocilnega
   dela, izbiro lahko ponovi - popravek. Rocna mreza se odpre tudi pri samostojnem
   izlocilnem sistemu IZLOCILNI, kjer je zreb dogodka hkrati izbira organizatorja. */
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { dogodkiApi } from '../api/zahteve'
import { imePrijave } from '../api/tipi'
import type { MrezaDto } from '../api/tipi'
import { RocnaMrezaOkno, type Udelezenec } from './RocnaMrezaOkno'
import { SporociloNapake } from './SporociloNapake'

/* Igralci po id-ju prijave za okno mreze: ime, mesto v skupini in klub. */
export function udelezenciPoId(podatki: MrezaDto): Map<number, Udelezenec> {
  const skupinaPoId = new Map(podatki.skupine.map((s) => [s.id, s]))
  return new Map(
    podatki.prijave.map((p): [number, Udelezenec] => {
      const skupina = p.idSkupina !== null ? skupinaPoId.get(p.idSkupina) : undefined
      const uvrstitev = skupina && p.mestoVSkupini !== null
        ? `${skupina.oznaka} ${p.mestoVSkupini}.`
        : ''
      return [p.id, { ime: imePrijave(p), uvrstitev, klub: p.klub }]
    }),
  )
}

export function RocnaMrezaZaDogodek({
  podatki,
  idDogodka,
  onZapri,
  onShranjeno,
}: {
  podatki: MrezaDto
  idDogodka: number
  onZapri: () => void
  onShranjeno: () => void
}) {
  return (
    <RocnaMrezaOkno
      naslov={podatki.dogodek.sistemTekmovanja === 'IZLOCILNI'
        ? 'Ročna mreža'
        : 'Ročna razporeditev izločilnega dela'}
      podnaslov={podatki.dogodek.ime}
      poId={udelezenciPoId(podatki)}
      predlog={() => dogodkiApi.izlocilniPredlog(idDogodka)}
      shrani={(mesta) => dogodkiApi.izlocilniMesta(idDogodka, mesta)}
      onZapri={onZapri}
      onShranjeno={onShranjeno}
    />
  )
}

/* Kartica nad pogledi, dokler organizator ni izbral izlocilnega dela. Ni vidna, ce
   skupine se niso odigrane, ce dogodek ni v teku ali ce je ze zacet izlocilni del. */
export function IzlocilniZrebDel({
  podatki,
  idDogodka,
  osvezi,
}: {
  podatki: MrezaDto
  idDogodka: number
  osvezi: () => void
}) {
  const [rocnaOdprta, nastaviRocnaOdprta] = useState(false)

  const zrebi = useMutation({
    mutationFn: () => dogodkiApi.izlocilniZreb(idDogodka),
    onSuccess: () => osvezi(),
  })

  const skupinske = podatki.tekme.filter((t) => t.faza === 'SKUPINA')
  const izlocilne = podatki.tekme.filter((t) => t.faza === 'GLAVNI' || t.faza === 'TOLAZILNI')
  const skupineKoncane = skupinske.length > 0 && skupinske.every((t) => t.status === 'KONCANA')
  const zacet = izlocilne.some((t) =>
    t.status === 'V_IGRI' || (t.status === 'KONCANA' && t.izidTip !== 'PROSTO'),
  )
  if (
    podatki.dogodek.sistemTekmovanja !== 'SKUPINE_IZLOCILNI'
    || podatki.dogodek.dejanskiStatus !== 'V_TEKU'
    || !skupineKoncane
    || zacet
  ) {
    return null
  }
  const zreban = izlocilne.length > 0

  return (
    <section className="izlocilni-zreb" aria-label="Izločilni del">
      <p className="podnaslov-sekcije">Izločilni del</p>
      <p className="obvestilo">
        {zreban
          ? 'Izločilni del je žreban, prva tekma še ni začeta. Razporeditev lahko še popraviš.'
          : 'Vse skupine so odigrane. Izločilni del še ni žreban: izberi računalniški žreb'
            + ' ali ročno razporeditev mest.'}
      </p>
      <div className="zreb__krmila">
        <button
          type="button"
          className="gumb"
          disabled={zrebi.isPending}
          onClick={() => zrebi.mutate()}
        >
          {zrebi.isPending ? 'Žrebam …' : zreban ? 'Ponovi računalniški žreb' : 'Računalniški žreb'}
        </button>
        <button type="button" className="gumb" onClick={() => nastaviRocnaOdprta(true)}>
          {zreban ? 'Popravi ročno' : 'Ročna razporeditev'}
        </button>
      </div>
      <SporociloNapake napaka={zrebi.error} />
      {rocnaOdprta && (
        <RocnaMrezaZaDogodek
          podatki={podatki}
          idDogodka={idDogodka}
          onZapri={() => nastaviRocnaOdprta(false)}
          onShranjeno={osvezi}
        />
      )}
    </section>
  )
}
