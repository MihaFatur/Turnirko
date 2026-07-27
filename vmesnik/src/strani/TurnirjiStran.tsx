/* Seznam vseh turnirjev + ustvarjanje novega. */
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { krajiApi, turnirjiApi } from '../api/zahteve'
import type { TurnirVnos } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujObdobje } from '../pomozno/oblikovanje'

export function TurnirjiStran() {
  const odjemalec = useQueryClient()
  const { jeAdmin } = useAvtentikacija()
  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  return (
    <section>
      <div className="naslovna-vrstica">
        <h1>Turnirji</h1>
        {jeAdmin && (
          <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
            + Nov turnir
          </button>
        )}
      </div>

      <SporociloNapake napaka={turnirji.error} />
      {turnirji.isPending && <p className="obvestilo">Nalaganje …</p>}

      {turnirji.data && turnirji.data.length === 0 && (
        <p className="obvestilo">
          Ni še nobenega turnirja. Ustvari prvega z gumbom »+ Nov turnir«.
        </p>
      )}

      {turnirji.data && turnirji.data.length > 0 && (
        <div className="kartice">
          {turnirji.data.map((turnir) => (
            <Link to={`/turnirji/${turnir.id}`} className="kartica" key={turnir.id}>
              <div className="kartica__glava">
                <h2>{turnir.ime}</h2>
                <ZnackaStatusa status={turnir.status} />
              </div>
              <p className="kartica__podrobnost">
                {[turnir.kraj?.ime, turnir.dvorana].filter(Boolean).join(', ') || '—'}
              </p>
              <p className="kartica__podrobnost">
                {oblikujObdobje(turnir.datumZacetka, turnir.datumKonca) || 'datum ni določen'}
              </p>
            </Link>
          ))}
        </div>
      )}

      {odprtObrazec && (
        <NovTurnirOkno
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['turnirji'] })}
        />
      )}
    </section>
  )
}

function NovTurnirOkno({
  onZapri,
  onShranjeno,
}: {
  onZapri: () => void
  onShranjeno: () => void
}) {
  const kraji = useQuery({ queryKey: ['kraji'], queryFn: krajiApi.seznam })

  const [ime, nastaviIme] = useState('')
  const [postnaSt, nastaviPostnaSt] = useState('')
  const [dvorana, nastaviDvorano] = useState('')
  const [datumZacetka, nastaviDatumZacetka] = useState('')
  const [datumKonca, nastaviDatumKonca] = useState('')
  const [opombe, nastaviOpombe] = useState('')

  const shranjevanje = useMutation({
    mutationFn: (vnos: TurnirVnos) => turnirjiApi.ustvari(vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      postnaSt: postnaSt ? Number(postnaSt) : null,
      dvorana: dvorana.trim() || null,
      datumZacetka: datumZacetka || null,
      datumKonca: datumKonca || null,
      opombe: opombe.trim() || null,
    })
  }

  return (
    <ModalnoOkno naslov="Nov turnir" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Ime turnirja *</span>
          <input
            value={ime}
            onChange={(dogodek) => nastaviIme(dogodek.target.value)}
            placeholder="npr. Odprto prvenstvo NTK Savinja 2026"
            required
          />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Kraj</span>
            <select
              value={postnaSt}
              onChange={(dogodek) => nastaviPostnaSt(dogodek.target.value)}
            >
              <option value="">— izberi kraj —</option>
              {kraji.data?.map((kraj) => (
                <option key={kraj.postnaSt} value={kraj.postnaSt}>
                  {kraj.postnaSt} {kraj.ime}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Dvorana</span>
            <input
              value={dvorana}
              onChange={(dogodek) => nastaviDvorano(dogodek.target.value)}
              placeholder="npr. ŠD Golovec"
            />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Datum začetka</span>
            <input
              type="date"
              value={datumZacetka}
              onChange={(dogodek) => nastaviDatumZacetka(dogodek.target.value)}
            />
          </label>
          <label className="obrazec__polje">
            <span>Datum konca</span>
            <input
              type="date"
              value={datumKonca}
              onChange={(dogodek) => nastaviDatumKonca(dogodek.target.value)}
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Opombe</span>
          <textarea
            value={opombe}
            onChange={(dogodek) => nastaviOpombe(dogodek.target.value)}
            rows={2}
          />
        </label>

        {kraji.data?.length === 0 && (
          <p className="namig">Namig: kraje lahko dodaš na strani Šifranti.</p>
        )}

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Ustvari turnir
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
