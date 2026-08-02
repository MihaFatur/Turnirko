/* Sifranti: klubi in kraji. Oboje se ureja kar v tabeli
   (dodajanje zgoraj, urejanje v vrstici, brisanje s potrditvijo). */
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { klubiApi, krajiApi } from '../api/zahteve'
import type { KlubDto, KrajDto } from '../api/tipi'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'

export function SifrantiStran() {
  return (
    <section>
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Osnovni podatki</span>
            <span className="naslov-strani__glavni">Šifranti</span>
          </h1>
          <p className="uvod">
            Klubi in kraji so podlaga vsemu ostalemu — igralci, ekipe in turnirji se
            sklicujejo nanje.
          </p>
        </div>
      </div>

      <div className="dvostolpicno">
        <KlubiPlosca />
        <KrajiPlosca />
      </div>
    </section>
  )
}

/* ---------- Klubi ---------- */

function KlubiPlosca() {
  const odjemalec = useQueryClient()
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['klubi'] })

  const [novoIme, nastaviNovoIme] = useState('')
  const [novaKratica, nastaviNovoKratico] = useState('')
  /* Id kluba, ki se ureja v vrstici; skupaj z delovnima vrednostma. */
  const [urejanId, nastaviUrejanId] = useState<number | null>(null)
  const [urejanoIme, nastaviUrejanoIme] = useState('')
  const [urejanaKratica, nastaviUrejanoKratico] = useState('')
  const [brisanec, nastaviBrisanca] = useState<KlubDto | null>(null)

  const dodajanje = useMutation({
    mutationFn: () =>
      klubiApi.ustvari({ ime: novoIme.trim(), kratica: novaKratica.trim() || null }),
    onSuccess: () => {
      nastaviNovoIme('')
      nastaviNovoKratico('')
      osvezi()
    },
  })

  const posodabljanje = useMutation({
    mutationFn: (id: number) =>
      klubiApi.posodobi(id, { ime: urejanoIme.trim(), kratica: urejanaKratica.trim() || null }),
    onSuccess: () => {
      nastaviUrejanId(null)
      osvezi()
    },
  })

  const brisanje = useMutation({
    mutationFn: (id: number) => klubiApi.izbrisi(id),
    onSuccess: osvezi,
  })

  function obDodajanju(dogodek: FormEvent) {
    dogodek.preventDefault()
    dodajanje.mutate()
  }

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Klubi</h2>
        {klubi.data && (
          <span className="sekcija__meta">
            {klubi.data.length} {klubovTekst(klubi.data.length)}
          </span>
        )}
      </div>

      <form className="obrazec__vrstica obrazec__vrstica--dodajanje" onSubmit={obDodajanju}>
        <input
          placeholder="Ime kluba"
          value={novoIme}
          onChange={(dogodek) => nastaviNovoIme(dogodek.target.value)}
          required
        />
        <input
          placeholder="Kratica"
          className="vnos--ozek"
          value={novaKratica}
          onChange={(dogodek) => nastaviNovoKratico(dogodek.target.value)}
        />
        <button className="gumb gumb--glavni" disabled={dodajanje.isPending}>
          Dodaj
        </button>
      </form>

      <SporociloNapake napaka={dodajanje.error} />
      <SporociloNapake napaka={posodabljanje.error} />
      <SporociloNapake napaka={brisanje.error} />
      {klubi.isPending && <p className="obvestilo">Nalaganje …</p>}
      {klubi.data?.length === 0 && <p className="obvestilo">Ni še nobenega kluba.</p>}

      {klubi.data && klubi.data.length > 0 && (
        <table className="tabela">
          <tbody>
            {klubi.data.map((klub: KlubDto) =>
              urejanId === klub.id ? (
                <tr key={klub.id}>
                  <td>
                    <input
                      value={urejanoIme}
                      onChange={(dogodek) => nastaviUrejanoIme(dogodek.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="vnos--ozek"
                      value={urejanaKratica}
                      onChange={(dogodek) => nastaviUrejanoKratico(dogodek.target.value)}
                    />
                  </td>
                  <td className="tabela__dejanja">
                    <button
                      className="gumb gumb--majhen gumb--glavni"
                      onClick={() => posodabljanje.mutate(klub.id)}
                    >
                      Shrani
                    </button>
                    <button className="gumb gumb--majhen" onClick={() => nastaviUrejanId(null)}>
                      Prekliči
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={klub.id}>
                  <td>{klub.ime}</td>
                  <td>{klub.kratica ?? ''}</td>
                  <td className="tabela__dejanja">
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => {
                        nastaviUrejanId(klub.id)
                        nastaviUrejanoIme(klub.ime)
                        nastaviUrejanoKratico(klub.kratica ?? '')
                      }}
                    >
                      Uredi
                    </button>
                    <button
                      className="gumb gumb--majhen gumb--nevaren"
                      onClick={() => nastaviBrisanca(klub)}
                    >
                      Izbriši
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}

      {brisanec && (
        <PotrditvenoOkno
          naslov="Brisanje kluba"
          sporocilo={`Izbrišem klub ${brisanec.ime}?`}
          besedaPotrditve="Izbriši"
          onPotrdi={() => brisanje.mutate(brisanec.id)}
          onZapri={() => nastaviBrisanca(null)}
        />
      )}
    </div>
  )
}

/* ---------- Kraji ---------- */

function KrajiPlosca() {
  const odjemalec = useQueryClient()
  const kraji = useQuery({ queryKey: ['kraji'], queryFn: krajiApi.seznam })
  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['kraji'] })

  const [novaPostnaSt, nastaviNovoPostnoSt] = useState('')
  const [novoIme, nastaviNovoIme] = useState('')
  const [urejanaPostnaSt, nastaviUrejanoPostnoSt] = useState<number | null>(null)
  const [urejanoIme, nastaviUrejanoIme] = useState('')
  const [brisanec, nastaviBrisanca] = useState<KrajDto | null>(null)

  const dodajanje = useMutation({
    mutationFn: () =>
      krajiApi.ustvari({ postnaSt: Number(novaPostnaSt), ime: novoIme.trim() }),
    onSuccess: () => {
      nastaviNovoPostnoSt('')
      nastaviNovoIme('')
      osvezi()
    },
  })

  const posodabljanje = useMutation({
    mutationFn: (postnaSt: number) =>
      krajiApi.posodobi(postnaSt, { postnaSt, ime: urejanoIme.trim() }),
    onSuccess: () => {
      nastaviUrejanoPostnoSt(null)
      osvezi()
    },
  })

  const brisanje = useMutation({
    mutationFn: (postnaSt: number) => krajiApi.izbrisi(postnaSt),
    onSuccess: osvezi,
  })

  function obDodajanju(dogodek: FormEvent) {
    dogodek.preventDefault()
    dodajanje.mutate()
  }

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Kraji</h2>
        {kraji.data && (
          <span className="sekcija__meta">
            {kraji.data.length} {krajevTekst(kraji.data.length)}
          </span>
        )}
      </div>

      <form className="obrazec__vrstica obrazec__vrstica--dodajanje" onSubmit={obDodajanju}>
        <input
          placeholder="Poštna št."
          className="vnos--ozek"
          type="number"
          min={1000}
          max={9999}
          value={novaPostnaSt}
          onChange={(dogodek) => nastaviNovoPostnoSt(dogodek.target.value)}
          required
        />
        <input
          placeholder="Ime kraja"
          value={novoIme}
          onChange={(dogodek) => nastaviNovoIme(dogodek.target.value)}
          required
        />
        <button className="gumb gumb--glavni" disabled={dodajanje.isPending}>
          Dodaj
        </button>
      </form>

      <SporociloNapake napaka={dodajanje.error} />
      <SporociloNapake napaka={posodabljanje.error} />
      <SporociloNapake napaka={brisanje.error} />
      {kraji.isPending && <p className="obvestilo">Nalaganje …</p>}
      {kraji.data?.length === 0 && <p className="obvestilo">Ni še nobenega kraja.</p>}

      {kraji.data && kraji.data.length > 0 && (
        <table className="tabela">
          <tbody>
            {kraji.data.map((kraj: KrajDto) =>
              urejanaPostnaSt === kraj.postnaSt ? (
                <tr key={kraj.postnaSt}>
                  <td className="tabela__ozek">{kraj.postnaSt}</td>
                  <td>
                    <input
                      value={urejanoIme}
                      onChange={(dogodek) => nastaviUrejanoIme(dogodek.target.value)}
                    />
                  </td>
                  <td className="tabela__dejanja">
                    <button
                      className="gumb gumb--majhen gumb--glavni"
                      onClick={() => posodabljanje.mutate(kraj.postnaSt)}
                    >
                      Shrani
                    </button>
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => nastaviUrejanoPostnoSt(null)}
                    >
                      Prekliči
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={kraj.postnaSt}>
                  <td className="tabela__ozek">{kraj.postnaSt}</td>
                  <td>{kraj.ime}</td>
                  <td className="tabela__dejanja">
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => {
                        nastaviUrejanoPostnoSt(kraj.postnaSt)
                        nastaviUrejanoIme(kraj.ime)
                      }}
                    >
                      Uredi
                    </button>
                    <button
                      className="gumb gumb--majhen gumb--nevaren"
                      onClick={() => nastaviBrisanca(kraj)}
                    >
                      Izbriši
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}

      {brisanec && (
        <PotrditvenoOkno
          naslov="Brisanje kraja"
          sporocilo={`Izbrišem kraj ${brisanec.ime}?`}
          besedaPotrditve="Izbriši"
          onPotrdi={() => brisanje.mutate(brisanec.postnaSt)}
          onZapri={() => nastaviBrisanca(null)}
        />
      )}
    </div>
  )
}

/* Slovnično pravilna oblika besede "klub" glede na število. */
function klubovTekst(n: number): string {
  if (n === 1) return 'klub'
  if (n === 2) return 'kluba'
  if (n === 3 || n === 4) return 'klubi'
  return 'klubov'
}

/* Slovnično pravilna oblika besede "kraj" glede na število. */
function krajevTekst(n: number): string {
  if (n === 1) return 'kraj'
  if (n === 2) return 'kraja'
  if (n === 3 || n === 4) return 'kraji'
  return 'krajev'
}
