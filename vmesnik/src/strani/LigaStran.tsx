/* Podroben pogled lige: konfiguracija, upravljanje ekip in kadra (v pripravi),
   generiranje razporeda ter lestvica in razpored srecanj (ko liga teče). */
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, ligeApi } from '../api/zahteve'
import type { EkipaDto, LigaDto, SrecanjeDto } from '../api/tipi'
import { OZNAKE_FORMAT } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { LigaObrazecOkno } from '../komponente/LigaObrazecOkno'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'

export function LigaStran() {
  const { id } = useParams()
  const idLiga = Number(id)
  const { jeAdmin } = useAvtentikacija()

  const liga = useQuery({ queryKey: ['liga', idLiga], queryFn: () => ligeApi.najdi(idLiga) })
  const srecanja = useQuery({ queryKey: ['srecanja', idLiga], queryFn: () => ligeApi.srecanja(idLiga) })

  if (liga.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (liga.error || !liga.data) return <SporociloNapake napaka={liga.error} />

  const l = liga.data
  const vPripravi = l.status === 'PRIPRAVA'
  const imaRazpored = (srecanja.data?.length ?? 0) > 0

  return (
    <section className="liga">
      <div className="naslovna-vrstica">
        <div>
          <Link to="/lige" className="nazaj">← Lige</Link>
          <h1>{l.ime}</h1>
        </div>
        <ZnackaStatusa status={l.status} />
      </div>

      <Konfiguracija liga={l} lahkoUreja={vPripravi && jeAdmin} />

      {vPripravi && jeAdmin && <EkipeUredi idLiga={idLiga} status={l.status} />}

      {vPripravi && (
        <p className="obvestilo">
          Liga je v pripravi. Dodaj ekipe in kader, nato generiraj razpored.
          {!jeAdmin && ' (za urejanje se prijavi kot administrator)'}
        </p>
      )}

      {imaRazpored && (
        <>
          <Lestvica idLiga={idLiga} />
          <Razpored srecanja={srecanja.data ?? []} />
        </>
      )}
    </section>
  )
}

/* Pregled pravil lige. Dokler je liga v pripravi, jih administrator lahko
   popravi (npr. če se je zmotil ali premislil); po generiranju razporeda so
   zaklenjena, ker bi sprememba razveljavila že odigrana srečanja. */
function Konfiguracija({ liga, lahkoUreja }: { liga: LigaDto; lahkoUreja: boolean }) {
  const odjemalec = useQueryClient()
  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)

  const podatki: [string, string][] = [
    ['Format', OZNAKE_FORMAT[liga.formatSrecanja]],
    ['Nizi', `najboljši od ${liga.steviloNizov}`],
    ['Konec srečanja', liga.zmagZaSrecanje ? `prvi do ${liga.zmagZaSrecanje} zmag` : 'vse tekme'],
    ['Sistem', liga.dvokrozno ? 'dvokrožno' : 'enokrožno'],
    ['Točke', `${liga.tockeZmaga} / ${liga.tockeNeodloceno} / ${liga.tockePoraz} (Z/N/P)`],
    ['Neodločeno', liga.dovoljenoNeodloceno ? 'mogoče' : 'ni mogoče'],
    ['Dvojna registracija', liga.prepovedDvojneRegistracije ? 'prepovedana' : 'dovoljena'],
    ['Šteje v ELO', liga.stejeVElo ? 'da (posamične)' : 'ne'],
  ]
  if (liga.visjaLigaIme) podatki.push(['Višja liga', liga.visjaLigaIme])
  if (liga.stNapreduje > 0) podatki.push(['Napreduje', String(liga.stNapreduje)])
  if (liga.stIzpade > 0) podatki.push(['Izpade', String(liga.stIzpade)])

  return (
    <div className="plosca liga__konfig">
      <div className="naslovna-vrstica naslovna-vrstica--tesno">
        <h2>Pravila</h2>
        {lahkoUreja && (
          <button className="gumb gumb--majhen" onClick={() => nastaviOdprtObrazec(true)}>
            Uredi pravila
          </button>
        )}
      </div>
      <dl className="opis-mreza">
        {podatki.map(([k, v]) => (
          <div key={k} className="opis-mreza__par">
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>

      {odprtObrazec && (
        <LigaObrazecOkno
          liga={liga}
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() => {
            odjemalec.invalidateQueries({ queryKey: ['liga', liga.id] })
            odjemalec.invalidateQueries({ queryKey: ['lige'] })
          }}
        />
      )}
    </div>
  )
}

function EkipeUredi({ idLiga, status }: { idLiga: number; status: string }) {
  const odjemalec = useQueryClient()
  const ekipe = useQuery({ queryKey: ['ekipe', idLiga], queryFn: () => ligeApi.ekipe(idLiga) })
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const [idKlub, nastaviKlub] = useState('')
  const [urejanKader, nastaviUrejanKader] = useState<EkipaDto | null>(null)

  const osveziEkipe = () => odjemalec.invalidateQueries({ queryKey: ['ekipe', idLiga] })
  const osveziLigo = () => odjemalec.invalidateQueries({ queryKey: ['liga', idLiga] })

  const dodaj = useMutation({
    mutationFn: () => ligeApi.dodajEkipo(idLiga, { idKlub: Number(idKlub), zaporedna: null, ime: null }),
    onSuccess: () => { osveziEkipe(); osveziLigo(); nastaviKlub('') },
  })
  const odstrani = useMutation({
    mutationFn: (idEkipa: number) => ligeApi.odstraniEkipo(idEkipa),
    onSuccess: () => { osveziEkipe(); osveziLigo() },
  })
  const razpored = useMutation({
    mutationFn: () => ligeApi.generirajRazpored(idLiga),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['srecanja', idLiga] })
      osveziLigo()
      odjemalec.invalidateQueries({ queryKey: ['lestvica', idLiga] })
    },
  })

  return (
    <div className="plosca">
      <div className="naslovna-vrstica naslovna-vrstica--tesno">
        <h2>Ekipe</h2>
        <button
          className="gumb gumb--glavni"
          disabled={(ekipe.data?.length ?? 0) < 2 || razpored.isPending}
          onClick={() => razpored.mutate()}
        >
          Generiraj razpored
        </button>
      </div>

      <SporociloNapake napaka={razpored.error} />

      <div className="obrazec__vrstica liga__dodaj-ekipo">
        <select value={idKlub} onChange={(d) => nastaviKlub(d.target.value)}>
          <option value="">— izberi klub —</option>
          {klubi.data?.map((k) => (
            <option key={k.id} value={k.id}>{k.ime}</option>
          ))}
        </select>
        <button className="gumb" disabled={!idKlub || dodaj.isPending} onClick={() => dodaj.mutate()}>
          + Dodaj ekipo
        </button>
      </div>
      <SporociloNapake napaka={dodaj.error} />

      {ekipe.data && ekipe.data.length === 0 && <p className="obvestilo">Ni še ekip.</p>}
      {ekipe.data && ekipe.data.length > 0 && (
        <ul className="liga__ekipe">
          {ekipe.data.map((e) => (
            <li key={e.id} className="liga__ekipa">
              <span>{e.prikazanoIme}</span>
              <span className="liga__ekipa-gumbi">
                <button className="gumb gumb--majhen" onClick={() => nastaviUrejanKader(e)}>Kader</button>
                <button className="gumb gumb--majhen gumb--nevaren" onClick={() => odstrani.mutate(e.id)}>
                  Odstrani
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      {urejanKader && (
        <KaderOkno ekipa={urejanKader} status={status} onZapri={() => nastaviUrejanKader(null)} />
      )}
    </div>
  )
}

function KaderOkno({ ekipa, onZapri }: { ekipa: EkipaDto; status: string; onZapri: () => void }) {
  const odjemalec = useQueryClient()
  const kader = useQuery({ queryKey: ['kader', ekipa.id], queryFn: () => ligeApi.kader(ekipa.id) })
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  const [idIgralec, nastaviIgralca] = useState('')

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['kader', ekipa.id] })
  const dodaj = useMutation({
    mutationFn: () => ligeApi.dodajVKader(ekipa.id, { idIgralec: Number(idIgralec), vrstniRed: null }),
    onSuccess: () => { osvezi(); nastaviIgralca('') },
  })
  const odstrani = useMutation({
    mutationFn: (idKader: number) => ligeApi.odstraniIzKadra(idKader),
    onSuccess: osvezi,
  })

  const vKadru = new Set(kader.data?.map((k) => k.idIgralec))
  const naVoljo = igralci.data?.filter((i) => !vKadru.has(i.id)) ?? []

  return (
    <ModalnoOkno naslov={`Kader – ${ekipa.prikazanoIme}`} onZapri={onZapri}>
      <div className="obrazec__vrstica liga__dodaj-ekipo">
        <select value={idIgralec} onChange={(d) => nastaviIgralca(d.target.value)}>
          <option value="">— izberi igralca —</option>
          {naVoljo.map((i) => (
            <option key={i.id} value={i.id}>{i.priimek} {i.ime}{i.klub ? ` (${i.klub.ime})` : ''}</option>
          ))}
        </select>
        <button className="gumb" disabled={!idIgralec || dodaj.isPending} onClick={() => dodaj.mutate()}>
          + Dodaj
        </button>
      </div>
      <SporociloNapake napaka={dodaj.error} />

      {kader.data && kader.data.length === 0 && <p className="obvestilo">Kader je prazen.</p>}
      {kader.data && kader.data.length > 0 && (
        <ul className="liga__ekipe">
          {kader.data.map((k) => (
            <li key={k.id} className="liga__ekipa">
              <span>{k.polnoIme}{k.rating != null && <span className="liga__rating"> {k.rating}</span>}</span>
              <button className="gumb gumb--majhen gumb--nevaren" onClick={() => odstrani.mutate(k.id)}>
                Odstrani
              </button>
            </li>
          ))}
        </ul>
      )}
    </ModalnoOkno>
  )
}

function Lestvica({ idLiga }: { idLiga: number }) {
  const lestvica = useQuery({ queryKey: ['lestvica', idLiga], queryFn: () => ligeApi.lestvica(idLiga) })
  if (lestvica.isPending) return <p className="obvestilo">Nalaganje lestvice …</p>
  if (!lestvica.data || lestvica.data.length === 0) return null

  return (
    <div className="plosca">
      <h2>Lestvica</h2>
      <div className="tabela-ovoj">
        <table className="tabela lestvica">
          <thead>
            <tr>
              <th>#</th>
              <th>Ekipa</th>
              <th className="lestvica__stevilka">Odig.</th>
              <th className="lestvica__stevilka">Z</th>
              <th className="lestvica__stevilka">N</th>
              <th className="lestvica__stevilka">P</th>
              <th className="lestvica__stevilka">Tekme</th>
              <th className="lestvica__stevilka">Nizi</th>
              <th className="lestvica__stevilka">Točke</th>
            </tr>
          </thead>
          <tbody>
            {lestvica.data.map((v) => (
              <tr key={v.idEkipa} className={v.cona ? `lestvica__vrsta--${v.cona.toLowerCase()}` : undefined}>
                <td className="lestvica__mesto">{v.mesto}</td>
                <td><strong>{v.ekipa}</strong></td>
                <td className="lestvica__stevilka">{v.odigrane}</td>
                <td className="lestvica__stevilka">{v.zmage}</td>
                <td className="lestvica__stevilka">{v.neodlocene}</td>
                <td className="lestvica__stevilka">{v.porazi}</td>
                <td className="lestvica__stevilka">{v.dobljeneTekme}:{v.prejeteTekme}</td>
                <td className="lestvica__stevilka">{v.dobljeniNizi}:{v.prejetiNizi}</td>
                <td className="lestvica__stevilka lestvica__rating">{v.tocke}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Razpored({ srecanja }: { srecanja: SrecanjeDto[] }) {
  const kola = [...new Set(srecanja.map((s) => s.kolo))].sort((a, b) => a - b)
  return (
    <div className="plosca">
      <h2>Razpored</h2>
      {kola.map((kolo) => (
        <div key={kolo} className="liga__kolo">
          <h3 className="liga__kolo-naslov">{kolo}. kolo</h3>
          <ul className="liga__srecanja">
            {srecanja.filter((s) => s.kolo === kolo).map((s) => (
              <li key={s.id}>
                <Link to={`/srecanja/${s.id}`} className="liga__srecanje">
                  <span className="liga__srecanje-ekipa liga__srecanje-ekipa--desno">{s.domaci}</span>
                  <span className="liga__srecanje-izid">
                    {s.status === 'KONCANO' ? `${s.dobljeneDomaci} : ${s.dobljeneGost}` : 'vs'}
                  </span>
                  <span className="liga__srecanje-ekipa">{s.gost}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}
