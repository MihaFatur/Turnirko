/* Pregled lista vadnice (/vadnica/:id, samo admin; načrt F3.3).

   Števke lista po vrsticah: levo vzorec (kaj bi moralo pisati), desno števke vseh
   okenc, kot jih je razrezal Turnirko. Klik izloči števko, ki ni napisana kot
   vzorec (pomota, prečrtano) ali je narobe razrezana (pol števke). Števke, ki jih
   mreža zdaj bere drugače, so obrobljene - najprej preveri te. »Odobri list«
   pošlje preostale v učenje; izločene se izbrišejo. */
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { StevkaVadniceDto } from '../api/tipi'
import { vadnicaApi } from '../api/zahteve'
import { useNazaj } from '../komponente/GlavaTelefona'
import { SlikaStevke } from '../komponente/SlikaStevke'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'
import { STOLPCEV, VREDNOSTI, oznakaLista } from '../pomozno/vadnica'

export function VadnicaPregledStran() {
  const { id } = useParams()
  const idLista = Number(id)
  const navigiraj = useNavigate()
  const odjemalec = useQueryClient()
  const jeTelefon = useTelefon()
  useNaslovStrani(`Vadnica ${oznakaLista(idLista)}`)
  useNazaj('/vadnica', 'Vadnica')

  const podrobno = useQuery({
    queryKey: ['vadnice', idLista],
    queryFn: () => vadnicaApi.podrobno(idLista),
    enabled: Number.isFinite(idLista),
  })
  const [izlocene, nastaviIzlocene] = useState<Set<number>>(() => new Set())
  const [slika, nastaviSliko] = useState<string | null>(null)

  useEffect(() => () => {
    if (slika) URL.revokeObjectURL(slika)
  }, [slika])

  const nazaj = () => {
    void odjemalec.invalidateQueries({ queryKey: ['vadnice'] })
    navigiraj('/vadnica')
  }
  const odobritev = useMutation({
    mutationFn: () => vadnicaApi.odobri(idLista, [...izlocene]),
    onSuccess: nazaj,
  })
  const izbris = useMutation({ mutationFn: () => vadnicaApi.izbrisi(idLista), onSuccess: nazaj })
  const prikazSlike = useMutation({
    mutationFn: () => vadnicaApi.slika(idLista),
    onSuccess: (blob) => nastaviSliko(URL.createObjectURL(blob)),
  })

  /* Števke po vrsticah lista, v vrstici po okencih. */
  const vrstice = useMemo(() => {
    const po = VREDNOSTI.map(() => Array.from({ length: STOLPCEV }, () => [] as StevkaVadniceDto[]))
    for (const s of podrobno.data?.stevke ?? []) {
      const vrstica = Math.floor(s.okence / STOLPCEV)
      if (vrstica < po.length) po[vrstica][s.okence % STOLPCEV].push(s)
    }
    return po
  }, [podrobno.data])

  if (podrobno.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.error || !podrobno.data) return <SporociloNapake napaka={podrobno.error} />

  const { list, stevke } = podrobno.data
  const drugace = stevke.filter((s) => !s.mrezaPrav).length
  const ostane = stevke.length - izlocene.size
  const preklopi = (idStevke: number) =>
    nastaviIzlocene((x) => {
      const nov = new Set(x)
      if (nov.has(idStevke)) nov.delete(idStevke)
      else nov.add(idStevke)
      return nov
    })

  return (
    <section className="vadnica-pregled">
      {!jeTelefon && (
        <Link to="/vadnica" className="povezava-nazaj">
          ← Vadnica števk
        </Link>
      )}
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">List {oznakaLista(list.id)}</h1>
          <p className="vadnica-pregled__povzetek">
            {list.stevk} števk · praznih okenc {list.praznih} · preskočenih {list.preskocenih} · mreža
            zdaj bere drugače {drugace}
            {list.odobrena && ' · odobren'}
          </p>
        </div>
      </div>

      {!list.odobrena && (
        <p className="namig">
          Klikni števko, ki ni napisana kot številka na začetku vrstice ali je razrezana narobe
          (na primer pol števke). Obrobljene najprej preveri: mreža jih zdaj bere drugače.
        </p>
      )}

      <div className="vadnica-pregled__vrstice">
        {VREDNOSTI.map((vrednost, i) => (
          <div key={vrednost} className="vadnica-pregled__vrstica">
            <span className="vadnica-pregled__vzorec">{vrednost}</span>
            <div className="vadnica-pregled__okenca">
              {vrstice[i]
                .filter((okence) => okence.length > 0)
                .map((okence) => (
                  <span key={okence[0].okence} className="vadnica-pregled__okence">
                    {okence.map((s) => (
                      <Stevka
                        key={s.id}
                        stevka={s}
                        izlocena={izlocene.has(s.id)}
                        onklik={list.odobrena ? undefined : () => preklopi(s.id)}
                      />
                    ))}
                  </span>
                ))}
            </div>
          </div>
        ))}
      </div>

      <SporociloNapake napaka={odobritev.error ?? izbris.error ?? prikazSlike.error} />
      <div className="vadnica-pregled__dejanja">
        {!list.odobrena && (
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={ostane === 0 || odobritev.isPending}
            onClick={() => odobritev.mutate()}
          >
            Odobri list ({ostane} {ostane === 1 ? 'števka' : ostane === 2 ? 'števki' : ostane <= 4 ? 'števke' : 'števk'} v učenje)
          </button>
        )}
        <button
          type="button"
          className="gumb"
          disabled={prikazSlike.isPending || slika !== null}
          onClick={() => prikazSlike.mutate()}
        >
          Pokaži list
        </button>
        <button
          type="button"
          className="gumb gumb--nevaren"
          disabled={izbris.isPending}
          onClick={() => {
            if (
              window.confirm(
                list.odobrena
                  ? 'Izbrišem list? Njegove števke ne gredo več v učenje.'
                  : 'Izbrišem list in vse njegove števke?',
              )
            ) {
              izbris.mutate()
            }
          }}
        >
          Izbriši list
        </button>
      </div>
      {slika && <img className="vadnica-pregled__slika" src={slika} alt={`Poravnan list ${oznakaLista(list.id)}`} />}
    </section>
  )
}

/* Števka 28 × 28 na platnu; klik jo izloči ali vrne. */
function Stevka({
  stevka,
  izlocena,
  onklik,
}: {
  stevka: StevkaVadniceDto
  izlocena: boolean
  onklik?: () => void
}) {
  return (
    <button
      type="button"
      className={
        'vadnica-pregled__stevka' +
        (stevka.mrezaPrav ? '' : ' vadnica-pregled__stevka--drugace') +
        (izlocena ? ' vadnica-pregled__stevka--izlocena' : '')
      }
      aria-pressed={izlocena}
      aria-label={`Števka ${stevka.vrednost}${izlocena ? ', izločena' : ''}${stevka.mrezaPrav ? '' : ', mreža bere drugače'}`}
      disabled={!onklik}
      onClick={onklik}
    >
      <SlikaStevke slika={stevka.slika} />
    </button>
  )
}
