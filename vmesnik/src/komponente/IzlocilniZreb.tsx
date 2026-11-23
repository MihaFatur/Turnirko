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
import { Namig } from '../pomoc/Namig'

/* Igralci po id-ju prijave za okno mreze: ime, mesto v skupini (»3 · 1.«), klub, rating in jakostno
   mesto (crna znacka nosilca pri samostojnem izlocilnem sistemu). */
export function udelezenciPoId(podatki: MrezaDto): Map<number, Udelezenec> {
  const skupinaPoId = new Map(podatki.skupine.map((s) => [s.id, s]))
  return new Map(
    podatki.prijave.map((p): [number, Udelezenec] => {
      const skupina = p.idSkupina !== null ? skupinaPoId.get(p.idSkupina) : undefined
      const uvrstitev = skupina && p.mestoVSkupini !== null
        ? `${skupina.oznaka} · ${p.mestoVSkupini}.`
        : ''
      const par = !!p.polnoIme2
      return [p.id, {
        ime: imePrijave(p),
        /* Par (dvojice) ima oba priimka krepko in nobenega imena; ekipa samo ime ekipe. */
        imeLahko: par ? '' : (p.ime ?? ''),
        priimek: par && p.priimek && p.priimek2
          ? `${p.priimek} / ${p.priimek2}`
          : (p.priimek ?? p.polnoIme),
        uvrstitev,
        klub: p.klub,
        rating: p.rating,
        stNosilca: p.stNosilca,
      }]
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
  /* Mreža je že žrebana (prva tekma še ni začeta): to je popravek, ne prvi vpis. */
  const popravek = podatki.tekme.some((t) => t.faza === 'GLAVNI')
  return (
    <RocnaMrezaOkno
      naslov={popravek ? 'Popravi mrežo' : 'Ročna mreža'}
      podnaslov={podatki.dogodek.ime}
      popravek={popravek}
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
      <p className="podnaslov-sekcije">
        Izločilni del
        <Namig clanek="izlocilni-del" />
      </p>
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
