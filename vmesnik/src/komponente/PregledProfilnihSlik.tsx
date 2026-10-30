/* Adminov pregled profilnih slik (V56): slike, ki čakajo na potrditev
   (mladoletnik ali račun, ki ga ni povezal admin), in nedavno objavljene, ki jih
   je mogoče še odstraniti. Povezava iz obvestila po pošti vodi na
   /igralci?slike=1 in razpre tudi objavljene.

   Čakajoča slika javno ni vidna, dokler je admin ne objavi; zavrnitev jo
   izbriše (igralec naloži drugo). */
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { profilneSlikeApi } from '../api/zahteve'
import type { ProfilnaSlikaPregledDto } from '../api/tipi'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { SlikaIgralca } from './SlikaIgralca'
import { SporociloNapake } from './SporociloNapake'

export function PregledProfilnihSlik() {
  const odjemalec = useQueryClient()
  const [iskanje] = useSearchParams()
  const [objavljeneOdprte, nastaviObjavljene] = useState(iskanje.get('slike') === '1')
  const [zaOdstranitev, nastaviOdstranitev] = useState<ProfilnaSlikaPregledDto | null>(null)

  const pregled = useQuery({ queryKey: ['profilne-slike-pregled'], queryFn: profilneSlikeApi.pregled })

  function osvezi() {
    odjemalec.invalidateQueries({ queryKey: ['profilne-slike-pregled'] })
    odjemalec.invalidateQueries({ queryKey: ['slike-igralcev'] })
  }
  const objavi = useMutation({ mutationFn: profilneSlikeApi.objavi, onSuccess: osvezi })
  const odstrani = useMutation({ mutationFn: profilneSlikeApi.odstrani, onSuccess: osvezi })

  const vse = pregled.data ?? []
  const cakajoce = vse.filter((s) => s.stanje === 'CAKA')
  const objavljene = vse.filter((s) => s.stanje === 'OBJAVLJENA')
  if (vse.length === 0) return null

  return (
    <section className="slike-pregled" aria-label="Profilne slike">
      <div className="slike-pregled__glava">
        <h2 className="slike-pregled__naslov">Profilne slike</h2>
        <span className="sekcija__meta">
          {cakajoce.length > 0 ? `${cakajoce.length} čaka na potrditev` : 'nič ne čaka'}
        </span>
      </div>

      {cakajoce.map((s) => (
        <Vrstica key={s.id} slika={s}>
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={objavi.isPending}
            onClick={() => objavi.mutate(s.id)}
          >
            Objavi
          </button>
          <button type="button" className="gumb gumb--nevaren" onClick={() => nastaviOdstranitev(s)}>
            Zavrni
          </button>
        </Vrstica>
      ))}

      {objavljene.length > 0 && (
        <>
          <button
            type="button"
            className="slike-pregled__preklop"
            aria-expanded={objavljeneOdprte}
            onClick={() => nastaviObjavljene((v) => !v)}
          >
            Nedavno objavljene ({objavljene.length}) {objavljeneOdprte ? '▴' : '▾'}
          </button>
          {objavljeneOdprte &&
            objavljene.map((s) => (
              <Vrstica key={s.id} slika={s}>
                <button type="button" className="gumb gumb--nevaren" onClick={() => nastaviOdstranitev(s)}>
                  Odstrani
                </button>
              </Vrstica>
            ))}
        </>
      )}

      <SporociloNapake napaka={objavi.error ?? odstrani.error} />

      {zaOdstranitev && (
        <PotrditvenoOkno
          naslov={zaOdstranitev.stanje === 'CAKA' ? 'Zavrni sliko' : 'Odstrani sliko'}
          sporocilo={`Slika igralca ${zaOdstranitev.ime} bo izbrisana. Igralec lahko naloži drugo.`}
          besedaPotrditve={zaOdstranitev.stanje === 'CAKA' ? 'Zavrni' : 'Odstrani'}
          onPotrdi={() => odstrani.mutate(zaOdstranitev.id)}
          onZapri={() => nastaviOdstranitev(null)}
        />
      )}
    </section>
  )
}

function Vrstica({ slika, children }: { slika: ProfilnaSlikaPregledDto; children: ReactNode }) {
  return (
    <div className="slike-pregled__vrstica">
      <SlikaIgralca zgostitev={slika.zgostitev} velikost="MALA" mera={72} />
      <div className="slike-pregled__besedilo">
        <span className="slike-pregled__ime">{slika.ime}</span>
        <span className="slike-pregled__meta">
          Naloženo {oblikujDatum(slika.nalozenaOb.slice(0, 10))}
          {slika.stanje === 'OBJAVLJENA' && !slika.javna && ' · javna šele s Premium'}
        </span>
        {slika.razlogCakanja && <span className="slike-pregled__razlog">{slika.razlogCakanja}</span>}
      </div>
      <div className="slike-pregled__dejanja">{children}</div>
    </div>
  )
}
