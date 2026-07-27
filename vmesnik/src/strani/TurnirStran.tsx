/* Stran enega turnirja: osnovni podatki, seznam dogodkov (tekmovanj)
   in dodajanje novega dogodka. Turnir se lahko zakljuci sele, ko so
   zakljuceni vsi njegovi dogodki - to pravilo preverja zaledje. */
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { turnirjiApi } from '../api/zahteve'
import type { DogodekVnos, SistemTekmovanja, SpolKategorija } from '../api/tipi'
import { OZNAKE_SISTEM, OZNAKE_SISTEM_KRATKO, OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujDatum, oblikujObdobje } from '../pomozno/oblikovanje'

export function TurnirStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()
  const { jeAdmin } = useAvtentikacija()

  const turnir = useQuery({
    queryKey: ['turnir', idTurnirja],
    queryFn: () => turnirjiApi.najdi(idTurnirja),
  })
  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja),
  })

  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)
  const [potrjujemZakljucek, nastaviPotrjujemZakljucek] = useState(false)

  const zakljucevanje = useMutation({
    mutationFn: () => turnirjiApi.zakljuci(idTurnirja),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja] })
      odjemalec.invalidateQueries({ queryKey: ['turnirji'] })
    },
  })

  if (turnir.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (turnir.error) return <SporociloNapake napaka={turnir.error} />
  const podatki = turnir.data!

  /* Gumb za zakljucek ponudimo sele, ko so vsi dogodki zakljuceni. */
  const vsiDogodkiZakljuceni =
    (dogodki.data?.length ?? 0) > 0 &&
    dogodki.data!.every((dogodek) => dogodek.status === 'ZAKLJUCEN')

  return (
    <section>
      <Link to="/turnirji" className="povezava-nazaj">← Vsi turnirji</Link>

      <div className="naslovna-vrstica">
        <div>
          <h1>{podatki.ime}</h1>
          <p className="podnaslov">
            {[
              [podatki.kraj?.ime, podatki.dvorana].filter(Boolean).join(', '),
              oblikujObdobje(podatki.datumZacetka, podatki.datumKonca),
            ]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
        </div>
        <div className="naslovna-vrstica__desno">
          <ZnackaStatusa status={podatki.status} />
          {jeAdmin && podatki.status !== 'ZAKLJUCEN' && vsiDogodkiZakljuceni && (
            <button
              className="gumb"
              disabled={zakljucevanje.isPending}
              onClick={() => nastaviPotrjujemZakljucek(true)}
            >
              Zaključi turnir
            </button>
          )}
        </div>
      </div>

      {podatki.opombe && <p className="opombe">{podatki.opombe}</p>}
      <SporociloNapake napaka={zakljucevanje.error} />

      <div className="naslovna-vrstica">
        <h2>Dogodki</h2>
        {jeAdmin && podatki.status !== 'ZAKLJUCEN' && (
          <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
            + Nov dogodek
          </button>
        )}
      </div>

      <SporociloNapake napaka={dogodki.error} />
      {dogodki.data && dogodki.data.length === 0 && (
        <p className="obvestilo">
          Turnir še nima dogodkov. Dogodek je eno tekmovanje - npr. »Člani« ali
          »Članice do 21 let«. Igralci se prijavljajo na posamezen dogodek.
        </p>
      )}

      {dogodki.data && dogodki.data.length > 0 && (
        <div className="kartice">
          {dogodki.data.map((dogodek) => (
            <Link to={`/dogodki/${dogodek.id}`} className="kartica" key={dogodek.id}>
              <div className="kartica__glava">
                <h2>{dogodek.ime}</h2>
                <ZnackaStatusa status={dogodek.status} />
              </div>
              <p className="kartica__podrobnost">
                {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
                {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
                {` · na ${dogodek.privzetoSteviloNizov} nizov`}
              </p>
              <p className="kartica__podrobnost">
                <span className="znacka znacka--sistem">
                  {OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja]}
                </span>
              </p>
              {dogodek.rokPrijave && (
                <p className="kartica__podrobnost">
                  rok prijave: {oblikujDatum(dogodek.rokPrijave)}
                </p>
              )}
            </Link>
          ))}
        </div>
      )}

      {potrjujemZakljucek && (
        <PotrditvenoOkno
          naslov="Zaključek turnirja"
          sporocilo="Zaključenega turnirja ni mogoče znova odpreti. Zaključim turnir?"
          besedaPotrditve="Zaključi turnir"
          onPotrdi={() => zakljucevanje.mutate()}
          onZapri={() => nastaviPotrjujemZakljucek(false)}
        />
      )}

      {odprtObrazec && (
        <NovDogodekOkno
          idTurnirja={idTurnirja}
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() =>
            odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja, 'dogodki'] })
          }
        />
      )}
    </section>
  )
}

function NovDogodekOkno({
  idTurnirja,
  onZapri,
  onShranjeno,
}: {
  idTurnirja: number
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [ime, nastaviIme] = useState('')
  const [spolKategorija, nastaviSpolKategorijo] = useState<SpolKategorija>('MESANO')
  const [starostnaKategorija, nastaviStarostnoKategorijo] = useState('')
  const [steviloNizov, nastaviSteviloNizov] = useState('5')
  const [sistem, nastaviSistem] = useState<SistemTekmovanja>('IZLOCILNI')
  const [steviloSkupin, nastaviSteviloSkupin] = useState('3')
  const [velikostSkupine, nastaviVelikostSkupine] = useState('8')
  const [prijavnina, nastaviPrijavnino] = useState('')
  const [rokPrijave, nastaviRokPrijave] = useState('')

  /* Format TOP: skupine so rangi po jakosti, zato je treba njihovo
     število in velikost določiti že ob dogodku - zmnožek pove, koliko
     najboljših prijavljenih sploh igra. */
  const skupinskiSistem = sistem === 'SKUPINE'
  const mejaIzbora = Number(steviloSkupin) * Number(velikostSkupine)

  const shranjevanje = useMutation({
    mutationFn: (vnos: DogodekVnos) => turnirjiApi.dodajDogodek(idTurnirja, vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      spolKategorija,
      starostnaKategorija: starostnaKategorija.trim() || null,
      privzetoSteviloNizov: Number(steviloNizov),
      prijavnina: prijavnina ? Number(prijavnina) : null,
      rokPrijave: rokPrijave || null,
      sistemTekmovanja: sistem,
      steviloSkupin: skupinskiSistem ? Number(steviloSkupin) : null,
      velikostSkupine: skupinskiSistem ? Number(velikostSkupine) : null,
    })
  }

  return (
    <ModalnoOkno naslov="Nov dogodek" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Ime dogodka *</span>
          <input
            value={ime}
            onChange={(d) => nastaviIme(d.target.value)}
            placeholder="npr. Člani odprto"
            required
          />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Kategorija *</span>
            <select
              value={spolKategorija}
              onChange={(d) => nastaviSpolKategorijo(d.target.value as SpolKategorija)}
            >
              {Object.entries(OZNAKE_SPOL_KATEGORIJA).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Starostna kategorija</span>
            <input
              value={starostnaKategorija}
              onChange={(d) => nastaviStarostnoKategorijo(d.target.value)}
              placeholder="npr. do 15 let"
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Sistem tekmovanja *</span>
          <select value={sistem} onChange={(d) => nastaviSistem(d.target.value as SistemTekmovanja)}>
            {Object.entries(OZNAKE_SISTEM).map(([vrednost, oznaka]) => (
              <option key={vrednost} value={vrednost}>
                {oznaka}
              </option>
            ))}
          </select>
        </label>

        {skupinskiSistem && (
          <div className="obrazec__sklop">
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Število skupin *</span>
                <input
                  type="number"
                  min={1}
                  max={26}
                  value={steviloSkupin}
                  onChange={(d) => nastaviSteviloSkupin(d.target.value)}
                  required
                />
              </label>
              <label className="obrazec__polje">
                <span>Igralcev v skupini *</span>
                <input
                  type="number"
                  min={2}
                  max={24}
                  value={velikostSkupine}
                  onChange={(d) => nastaviVelikostSkupine(d.target.value)}
                  required
                />
              </label>
            </div>
            <p className="namig">
              {mejaIzbora > 0 ? (
                <>
                  Igralo bo <strong>najboljših {mejaIzbora}</strong> prijavljenih:
                  skupina A dobi mesta 1.–{velikostSkupine}, B naslednja in tako
                  naprej. Znotraj skupine igra vsak z vsakim, izločilnega dela ni.
                  Vrstni red urediš pred žrebom.
                </>
              ) : (
                'Vpiši število skupin in velikost skupine.'
              )}
            </p>
          </div>
        )}

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Igra se na … nizov *</span>
            <select value={steviloNizov} onChange={(d) => nastaviSteviloNizov(d.target.value)}>
              <option value="3">3 (na 2 dobljena niza)</option>
              <option value="5">5 (na 3 dobljene nize)</option>
              <option value="7">7 (na 4 dobljene nize)</option>
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Prijavnina (€)</span>
            <input
              type="number"
              min={0}
              step="0.5"
              value={prijavnina}
              onChange={(d) => nastaviPrijavnino(d.target.value)}
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Rok prijave</span>
          <input
            type="date"
            value={rokPrijave}
            onChange={(d) => nastaviRokPrijave(d.target.value)}
          />
        </label>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Dodaj dogodek
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
