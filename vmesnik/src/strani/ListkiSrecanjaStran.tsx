/* Natisljivi listki (zapisniki) posamičnih tekem enega ekipnega srečanja.

   Enako kot pri turnirjih: pot je zunaj skupne postavitve (brez navigacije),
   tiska brskalnik (window.print), brez zaledja in nove sheme. Natisnejo se
   pripravljene tekme srečanja — tiste, kjer je določena postava in še niso
   odigrane (status CAKA). Pri dvojicah sta na strani dva igralca, pod imenom
   pa je ekipa. */
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { srecanjaApi } from '../api/zahteve'
import type { SrecanjePodrobnoDto, TekmaSrecanjaDto } from '../api/tipi'
import { Listek, type ListekPodatki } from '../komponente/Listek'
import { sklonListkov } from '../pomozno/oblikovanje'
import { SporociloNapake } from '../komponente/SporociloNapake'

export function ListkiSrecanjaStran() {
  const { id } = useParams()
  const idSrecanje = Number(id)

  const podrobno = useQuery({
    queryKey: ['srecanje', idSrecanje],
    queryFn: () => srecanjaApi.podrobno(idSrecanje),
  })

  const listki = useMemo(() => pripraviListke(podrobno.data), [podrobno.data])

  if (podrobno.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.error || !podrobno.data) return <SporociloNapake napaka={podrobno.error} />

  const s = podrobno.data.srecanje

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/srecanja/${idSrecanje}`} className="povezava-nazaj">
          ← Nazaj na srečanje
        </Link>
        <span className="listki-orodja__stevec">
          {listki.length} {sklonListkov(listki.length)}
        </span>
        <button
          className="gumb gumb--glavni"
          disabled={listki.length === 0}
          onClick={() => window.print()}
        >
          🖨 Natisni
        </button>
      </div>

      <div className="listki-stran">
        <header className="listki-naslov">
          <h1>
            {s.domaci} — {s.gost}
          </h1>
          <p>{s.kolo}. kolo</p>
        </header>

        {listki.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih tekem za tiskanje. Listki se ustvarijo, ko je
            določena postava in tekme še niso odigrane.
          </p>
        ) : (
          <div className="listki-mreza">
            {listki.map(({ id: idTekme, podatki }) => (
              <Listek key={idTekme} podatki={podatki} />
            ))}
          </div>
        )}
      </div>
    </>
  )
}

function pripraviListke(
  podrobno: SrecanjePodrobnoDto | undefined,
): { id: number; podatki: ListekPodatki }[] {
  if (!podrobno) return []
  const s = podrobno.srecanje
  return podrobno.tekme
    .filter((t) => t.status === 'CAKA')
    .sort((a, b) => a.zaporedje - b.zaporedje)
    .map((t) => ({ id: t.id, podatki: vListek(t, s.domaci, s.gost) }))
}

function vListek(t: TekmaSrecanjaDto, ekipaDomaci: string, ekipaGost: string): ListekPodatki {
  /* Pri dvojicah sta na strani dva igralca; oznaka (npr. "A-X" ali "dvojice")
     je že opisna, dvojica pa razvidna iz dveh imen — zato je ne dopolnjujemo.
     Mize ligaška tekma nima; prazna črta ostane za ročni vpis. */
  const domaci = [t.domaci, t.domaci2].filter(Boolean).join(' / ') || '—'
  const gost = [t.gost, t.gost2].filter(Boolean).join(' / ') || '—'
  return {
    oznaka: t.oznaka,
    steviloNizov: t.steviloNizov,
    miza: null,
    stran1: { ime: domaci, podnaslov: ekipaDomaci },
    stran2: { ime: gost, podnaslov: ekipaGost },
  }
}
