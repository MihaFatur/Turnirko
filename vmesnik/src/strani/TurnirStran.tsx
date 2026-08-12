/* Stran enega turnirja: osnovni podatki, seznam dogodkov (tekmovanj)
   in dodajanje novega dogodka. Turnir se lahko zakljuci sele, ko so
   zakljuceni vsi njegovi dogodki - to pravilo preverja zaledje. */
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { turnirjiApi } from '../api/zahteve'
import type { DogodekDto, DogodekVnos, SistemTekmovanja, SpolKategorija } from '../api/tipi'
import { OZNAKE_SISTEM, OZNAKE_SISTEM_KRATKO, OZNAKE_SPOL_KATEGORIJA } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { Napredek } from '../komponente/Napredek'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'
import {
  oblikujObdobje,
  sklonNizov,
  sklonPrijavljenih,
  sklonSkupin,
  sklonTekmovanj,
} from '../pomozno/oblikovanje'
import { intervalOsvezevanja } from '../pomozno/osvezevanje'

export function TurnirStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()
  const { smemUrejati } = useAvtentikacija()

  const turnir = useQuery({
    queryKey: ['turnir', idTurnirja],
    queryFn: () => turnirjiApi.najdi(idTurnirja),
  })
  /* Med turnirjem se seznam dogodkov osvežuje sam: statusi se med dnevom
     premikajo iz priprave v tek in v zaključek. */
  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja),
    refetchInterval: intervalOsvezevanja(turnir.data?.status),
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

  if (turnir.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (turnir.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (turnir.error) return <NapakaPoizvedbe poizvedba={turnir} kaj="turnirja" />
  if (!turnir.data) return <p className="obvestilo">Tega turnirja ni (več).</p>
  const podatki = turnir.data!
  // organizator sme upravljati svoj (ali klubski) turnir, admin vse
  const smem = smemUrejati(podatki.idLastnik, podatki.idKlubLastnik)

  /* Gumb za zakljucek ponudimo sele, ko so vsi dogodki zakljuceni. */
  const vsiDogodkiZakljuceni =
    (dogodki.data?.length ?? 0) > 0 &&
    dogodki.data!.every((dogodek) => dogodek.status === 'ZAKLJUCEN')

  return (
    <section>
      <Link to="/turnirji" className="povezava-nazaj">← Vsi turnirji</Link>

      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--podstran">
            <span className="naslov-strani__nad">Turnir</span>
            <span className="naslov-strani__glavni">{podatki.ime}</span>
          </h1>
          <p className="uvod">
            {[
              [podatki.kraj?.ime, podatki.dvorana].filter(Boolean).join(', '),
              oblikujObdobje(podatki.datumZacetka, podatki.datumKonca),
            ]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
          {podatki.opombe && <p className="opomba-bloka">{podatki.opombe}</p>}
        </div>
        <div>
          <div className="stran-glava__dejanja">
            <ZnackaStatusa status={podatki.status} />
            {smem && podatki.status !== 'ZAKLJUCEN' && (
              <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
                + Nov dogodek
              </button>
            )}
            {smem && podatki.status !== 'ZAKLJUCEN' && (
              <button
                className="gumb"
                disabled={zakljucevanje.isPending || !vsiDogodkiZakljuceni}
                onClick={() => nastaviPotrjujemZakljucek(true)}
              >
                Zaključi turnir
              </button>
            )}
            {smem && podatki.status !== 'ZAKLJUCEN' && !vsiDogodkiZakljuceni && (
              <span className="stran-glava__pogoj">
                Mogoče šele, ko so zaključeni vsi dogodki
              </span>
            )}
          </div>

          {/* Kolofon nosi vsote cez dogodke: koliko ljudi je na turnirju in
              koliko je odigranega. Stevila po statusih so ze v vrsticah
              dogodkov, zato jih tu ne ponavljamo. */}
          <div className="kolofon">
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Prijavljenih skupaj</span>
              <span className="kolofon__vrednost">{podatki.prijavljenihSkupaj}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Odigranih tekem</span>
              <span className="kolofon__vrednost">
                {podatki.odigranihTekem} / {podatki.vsehTekem}
              </span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Dogodki</span>
              <span className="kolofon__vrednost">{podatki.steviloDogodkov}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Šteje v ELO</span>
              <span className="kolofon__vrednost">{podatki.stejeVElo ? 'da' : 'ne'}</span>
            </div>
          </div>
        </div>
      </div>

      <SporociloNapake napaka={zakljucevanje.error} />

      <div>
        <div className="naslovna-vrstica">
          <h2>Dogodki</h2>
          {dogodki.data && dogodki.data.length > 0 && (
            <span className="sekcija__meta">
              {dogodki.data.length} {sklonTekmovanj(dogodki.data.length)}
            </span>
          )}
        </div>

        <NapakaPoizvedbe poizvedba={dogodki} kaj="dogodkov" />
        {dogodki.data && dogodki.data.length === 0 && (
          <p className="obvestilo">
            Turnir še nima dogodkov. Dogodek je eno tekmovanje — npr. »Člani« ali
            »Članice do 21 let«. Igralci se prijavljajo na posamezen dogodek.
          </p>
        )}

        {dogodki.data && dogodki.data.length > 0 && (
          <>
            <div className="seznam-glava seznam-glava--dogodki">
              <span>Dogodek</span>
              <span>Sistem</span>
              <span>Prijave</span>
              <span>Napredek</span>
              <span className="seznam-glava__sredinjeno">Status</span>
            </div>
            <div className="kartice">
              {dogodki.data.map((dogodek) => (
                <Link
                  to={`/dogodki/${dogodek.id}`}
                  className={`kartica kartica--dogodek kartica--${dogodek.status}`}
                  key={dogodek.id}
                >
                  <span className="kartica__glava">
                    <span className="kartica__ime">{dogodek.ime}</span>
                    <span className="kartica__podrobnost">
                      {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
                      {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
                      {` · na ${dogodek.privzetoSteviloNizov} ${sklonNizov(dogodek.privzetoSteviloNizov)}`}
                    </span>
                  </span>
                  <span className="znacka znacka--sistem">
                    {OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja]}
                  </span>
                  <span className="vrstica__mono">{opisPrijav(dogodek)}</span>
                  <Napredek
                    odigranih={dogodek.odigranihTekem}
                    vseh={dogodek.vsehTekem}
                    koncan={dogodek.status === 'ZAKLJUCEN'}
                  />
                  <ZnackaStatusa status={dogodek.status} />
                </Link>
              ))}
            </div>
          </>
        )}

        {/* Ko dogodkov ni, isto pojasnilo stoji ze v praznem stanju zgoraj. */}
        {dogodki.data && dogodki.data.length > 0 && (
          <p className="namig">
            Dogodek je eno tekmovanje — npr. »Člani« ali »Članice do 21 let«. Igralci se
            prijavljajo na posamezen dogodek.
          </p>
        )}
      </div>

      {potrjujemZakljucek && (
        <PotrditvenoOkno
          naslov="Zaključek turnirja"
          sporocilo="Zaključenega turnirja ni mogoče znova odpreti. Zaključim turnir?"
          besedaPotrditve="Zaključi turnir"
          onPotrdi={() => zakljucevanje.mutate()}
          onZapri={() => nastaviPotrjujemZakljucek(false)}
        />
      )}

      {/* Sklanjanje po številu; vzorec je enak kot pri ekipah na LigeStran. */}
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

/* Stolpec "Prijave". Format TOP pove razrez (skupine so rangi po jakosti in
   povedo, koliko najboljših sploh igra), vsi drugi sistemi pa število ljudi. */
function opisPrijav(dogodek: DogodekDto): string {
  if (dogodek.steviloSkupin && dogodek.velikostSkupine) {
    return `${dogodek.steviloSkupin} ${sklonSkupin(dogodek.steviloSkupin)} po ${dogodek.velikostSkupine}`
  }
  return `${dogodek.steviloPrijav} ${sklonPrijavljenih(dogodek.steviloPrijav)}`
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
