/* Seznam vseh turnirjev + ustvarjanje novega. Turnir je okvir; tekmovanja
   znotraj njega so dogodki, zato vrstica nosi le okvirne podatke. */
import { useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { krajiApi, turnirjiApi } from '../api/zahteve'
import type { StatusTekmovanja, TurnirVnos } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'
import { datumskiBlok, oblikujObdobje } from '../pomozno/oblikovanje'

/* Segmentirani filter po statusu; "vsi" ni status, zato je poseben. */
type Filter = 'vsi' | StatusTekmovanja

const FILTRI: { kljuc: Filter; oznaka: string }[] = [
  { kljuc: 'vsi', oznaka: 'Vsi' },
  { kljuc: 'V_TEKU', oznaka: 'V teku' },
  { kljuc: 'PRIPRAVA', oznaka: 'Priprava' },
  { kljuc: 'ZAKLJUCEN', oznaka: 'Zaključeni' },
]

export function TurnirjiStran() {
  const odjemalec = useQueryClient()
  const { smeUstvarjati } = useAvtentikacija()
  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)
  const [filter, nastaviFilter] = useState<Filter>('vsi')

  const vsi = turnirji.data ?? []
  const prikazani = useMemo(
    () => (filter === 'vsi' ? vsi : vsi.filter((t) => t.status === filter)),
    [vsi, filter],
  )

  return (
    <section>
      <div className="stran-glava stran-glava--dejanja">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Tekmovanja</span>
            <span className="naslov-strani__glavni">Turnirji</span>
          </h1>
          <p className="uvod">
            Turnir je okvir; tekmovanja znotraj njega so dogodki — člani, članice, kategorije.
          </p>
        </div>
        {smeUstvarjati && (
          <div className="naslovna-vrstica__desno">
            <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
              + Nov turnir
            </button>
          </div>
        )}
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Vsi turnirji</h2>
          {turnirji.data && (
            <div className="izbirnik">
              {FILTRI.map((f) => (
                <button
                  type="button"
                  key={f.kljuc}
                  className={
                    'izbirnik__gumb' + (filter === f.kljuc ? ' izbirnik__gumb--aktiven' : '')
                  }
                  onClick={() => nastaviFilter(f.kljuc)}
                >
                  {f.oznaka}
                  {f.kljuc === 'vsi' ? ` · ${vsi.length}` : ''}
                </button>
              ))}
            </div>
          )}
        </div>

        <NapakaPoizvedbe poizvedba={turnirji} kaj="turnirjev" />
        {turnirji.isPending && <p className="obvestilo">Nalaganje …</p>}

        {turnirji.data && vsi.length === 0 && (
          <p className="obvestilo">
            Ni še nobenega turnirja. Ustvari prvega z gumbom »+ Nov turnir«.
          </p>
        )}

        {vsi.length > 0 && prikazani.length === 0 && (
          <p className="obvestilo">V tem statusu ni turnirjev.</p>
        )}

        {prikazani.length > 0 && (
          <div className="kartice">
            {prikazani.map((turnir) => {
              const { dan, mesec } = datumskiBlok(turnir.datumZacetka)
              return (
                <Link
                  to={`/turnirji/${turnir.id}`}
                  className="kartica kartica--z-datumom"
                  key={turnir.id}
                >
                  <span className={`datum-blok datum-blok--${turnir.status}`}>
                    <span className="datum-blok__dan">{dan}</span>
                    <span className="datum-blok__mesec">{mesec}</span>
                  </span>
                  <span className="kartica__glava">
                    <span className="kartica__ime">{turnir.ime}</span>
                    <span className="kartica__podrobnost">
                      {[turnir.kraj?.ime, turnir.dvorana].filter(Boolean).join(', ') ||
                        'kraj še ni določen'}
                    </span>
                  </span>
                  <span className="vrstica__mono">
                    {oblikujObdobje(turnir.datumZacetka, turnir.datumKonca) || '—'}
                  </span>
                  <span className="kartica__organizator">
                    {turnir.klubLastnik ?? ''}
                  </span>
                  <ZnackaStatusa status={turnir.status} />
                </Link>
              )
            })}
          </div>
        )}
      </div>

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
  const [stejeVElo, nastaviStejeVElo] = useState(true)

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
      stejeVElo,
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

        <label className="obrazec__polje obrazec__polje--stikalo">
          <input
            type="checkbox"
            checked={stejeVElo}
            onChange={(dogodek) => nastaviStejeVElo(dogodek.target.checked)}
          />
          <span>Tekme štejejo v klubski ELO (rating)</span>
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
