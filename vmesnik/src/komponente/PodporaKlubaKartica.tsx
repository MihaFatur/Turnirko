/* »Podpora klubu« (V61): navijač s paketom Navijač izbere klub, ki ga podpira. Ko ima
   klub 10 podpornikov, njegov organizator dobi 10 % popusta na organizatorski paket,
   z vsakimi naslednjimi 10 še 10 %, največ 30 %. Izplačil ni: iz s.p. ne teče noben denar
   klubu; klub dobi razlog, da Navijača priporoči staršem.

   Stran pove tudi, česa NE obljublja: popust velja za prvo plačilo (nakup ali nadgradnjo
   paketa), ne za obnovo, in šteje samo račune, ki paket Navijač plačujejo zdaj. Imen
   podpornikov ne vidi nihče, število je zbirno. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { klubiApi, podporaKlubaApi } from '../api/zahteve'
import type { PodporaKlubaDto } from '../api/tipi'
import { IzbirnikKluba } from './IzbirnikKluba'
import { SporociloNapake } from './SporociloNapake'

export function PodporaKlubaKartica() {
  const odjemalec = useQueryClient()
  const podpora = useQuery({ queryKey: ['podpora-kluba'], queryFn: podporaKlubaApi.moja })
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam, enabled: !!podpora.data?.smemIzbrati })
  const [izbira, nastaviIzbiro] = useState<number | null>(null)

  const poSpremembi = (nova: PodporaKlubaDto) => {
    odjemalec.setQueryData(['podpora-kluba'], nova)
    nastaviIzbiro(null)
  }
  const izberi = useMutation({ mutationFn: (id: number) => podporaKlubaApi.izberi(id), onSuccess: poSpremembi })
  const umakni = useMutation({ mutationFn: podporaKlubaApi.umakni, onSuccess: poSpremembi })

  const d = podpora.data
  if (!d?.smemIzbrati) return null

  return (
    <div id="podpora-kluba" className="podpora-kartica">
      <div className="naslovna-vrstica">
        <h2>Podpora klubu</h2>
      </div>
      <p className="namig">
        Izberi klub, ki ga podpiraš. Ko ima klub 10 podpornikov s paketom Navijač, njegov organizator dobi
        10 % popusta na organizatorski paket, z vsakimi naslednjimi 10 še 10 %, največ 30 %. Popust velja za
        prvo plačilo (nakup ali nadgradnjo), ne za obnovo. Izplačil ni, imen podpornikov ne vidi nihče.
      </p>

      {d.izbran && d.popust ? (
        <div className="podpora-kartica__izbran">
          <div className="kolofon">
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Podpiraš</span>
              <span className="kolofon__vrednost">{d.klub}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Podpornikov</span>
              <span className="kolofon__vrednost">{d.popust.podpornikov}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Popust kluba</span>
              <span className="kolofon__vrednost">{d.popust.odstotek} %</span>
            </div>
            {d.popust.naslednjaMeja !== null && (
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Naslednja stopnja</span>
                <span className="kolofon__vrednost">
                  {d.popust.naslednjaMeja} podpornikov · {d.popust.naslednjiOdstotek} %
                </span>
              </div>
            )}
          </div>
          <div className="obrazec__gumbi">
            <button type="button" className="gumb" disabled={umakni.isPending} onClick={() => umakni.mutate()}>
              Ne podpiraj več
            </button>
          </div>
        </div>
      ) : (
        <p className="domov__prazno">Nobenega kluba ne podpiraš.</p>
      )}

      <div className="podpora-kartica__izbira">
        <IzbirnikKluba
          oznaka={d.izbran ? 'Zamenjaj klub' : 'Klub'}
          klubi={klubi.data ?? []}
          izbrano={izbira}
          naSpremembo={nastaviIzbiro}
        />
        <button type="button" className="gumb gumb--glavni" disabled={izbira === null || izberi.isPending}
                onClick={() => izbira !== null && izberi.mutate(izbira)}>
          {d.izbran ? 'Zamenjaj' : 'Podpiraj klub'}
        </button>
      </div>
      <SporociloNapake napaka={izberi.error ?? umakni.error} />
    </div>
  )
}

/* Organizator: koliko podpornikov ima njegov klub in kakšen popust to prinese na paket. */
export function PodporaKlubaOrganizator() {
  const podpora = useQuery({ queryKey: ['podpora-kluba-organizator'], queryFn: podporaKlubaApi.mojKlub })
  const d = podpora.data
  if (!d?.klub || !d.popust) return null
  const p = d.popust
  return (
    <p className="obvestilo">
      Klub {d.klub} podpira {p.podpornikov} {p.podpornikov === 1 ? 'navijač' : 'navijačev'} s paketom Navijač:{' '}
      {p.odstotek > 0
        ? `popust ${p.odstotek} % na prvo plačilo paketa (nakup ali nadgradnja).`
        : p.naslednjaMeja !== null
          ? `do popusta ${p.naslednjiOdstotek} % jih manjka ${p.naslednjaMeja - p.podpornikov}.`
          : ''}
    </p>
  )
}
