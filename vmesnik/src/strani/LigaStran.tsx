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
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { intervalOsvezevanja } from '../pomozno/osvezevanje'

export function LigaStran() {
  const { id } = useParams()
  const idLiga = Number(id)
  const { smemUrejati } = useAvtentikacija()

  const liga = useQuery({ queryKey: ['liga', idLiga], queryFn: () => ligeApi.najdi(idLiga) })
  /* Dokler liga teče, se razpored osvežuje sam - rezultati srečanj prihajajo
     med večerom. Zaključena liga se ne spreminja. */
  const srecanja = useQuery({
    queryKey: ['srecanja', idLiga],
    queryFn: () => ligeApi.srecanja(idLiga),
    refetchInterval: intervalOsvezevanja(liga.data?.status),
  })

  if (liga.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (liga.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (liga.error) return <NapakaPoizvedbe poizvedba={liga} kaj="lige" />
  if (!liga.data) return <p className="obvestilo">Te lige ni (več).</p>

  const l = liga.data
  // organizator sme urejati svojo (ali klubsko) ligo, admin vse
  const smem = smemUrejati(l.idLastnik, l.idKlubLastnik)
  const vPripravi = l.status === 'PRIPRAVA'
  const vsa = srecanja.data ?? []
  const imaRazpored = vsa.length > 0

  /* Napredek lige: koliko kol je do konca odigranih. Kolo šteje za odigrano,
     ko je končano vsako njegovo srečanje. */
  const kola = [...new Set(vsa.map((s) => s.kolo))].sort((a, b) => a - b)
  const odigranihKol = kola.filter((k) =>
    vsa.filter((s) => s.kolo === k).every((s) => s.status === 'KONCANO'),
  ).length

  const uvod = [
    `${l.steviloEkip} ${ekipTekst(l.steviloEkip)}`,
    l.dvokrozno ? 'dvokrožno' : 'enokrožno',
    imaRazpored ? `${odigranihKol}. od ${kola.length} kol odigranih` : 'razpored ni generiran',
  ].join(' · ')

  return (
    <section className="liga">
      <div>
        <Link to="/lige" className="povezava-nazaj">← Lige</Link>

        <div className="stran-glava">
          <div>
            <h1 className="naslov-strani naslov-strani--podstran">
              {/* Sezona je prosto besedilo (»2025/26«, »8. sezona«), zato je ne
                  opremljamo s predpono - v nadnaslovu stoji taka, kot je vpisana. */}
              <span className="naslov-strani__nad">{l.sezona ?? 'Liga'}</span>
              <span className="naslov-strani__glavni">{l.ime}</span>
            </h1>
            <p className="uvod">{uvod}</p>
            <div className="naslovna-vrstica__desno liga__stanje">
              <ZnackaStatusa status={l.status} />
              <span className="sekcija__meta">
                {vPripravi
                  ? 'Pravila je še mogoče popraviti'
                  : 'Pravila so zaklenjena — razpored je že generiran'}
              </span>
            </div>
          </div>
          <Konfiguracija liga={l} lahkoUreja={vPripravi && smem} />
        </div>
      </div>

      {vPripravi && smem && <EkipeUredi idLiga={idLiga} status={l.status} />}

      {vPripravi && (
        <p className="obvestilo">
          Liga je v pripravi. Dodaj ekipe in kader, nato generiraj razpored.
          {!smem && ' (urejate lahko le lige svojega kluba oz. kot administrator)'}
        </p>
      )}

      {imaRazpored && (
        <>
          <Lestvica idLiga={idLiga} liga={l} />
          <Razpored srecanja={vsa} kola={kola} />
        </>
      )}
    </section>
  )
}

function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
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
    <div className="liga__konfig">
      <div className="okolica__glava">
        <span className="podnaslov-sekcije liga__pravila-naslov">Pravila</span>
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
      <div className="naslovna-vrstica">
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

function Lestvica({ idLiga, liga }: { idLiga: number; liga: LigaDto }) {
  const lestvica = useQuery({ queryKey: ['lestvica', idLiga], queryFn: () => ligeApi.lestvica(idLiga) })
  if (lestvica.isPending) return <p className="obvestilo">Nalaganje lestvice …</p>
  if (!lestvica.data || lestvica.data.length === 0) return null

  const conaOpis = [
    liga.stNapreduje > 0 ? `${liga.stNapreduje} napreduje` : null,
    liga.stIzpade > 0 ? `${liga.stIzpade} izpade` : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Lestvica</h2>
        {conaOpis && <span className="sekcija__meta">{conaOpis}</span>}
      </div>
      <div className="tabela-ovoj">
        <table className="tabela">
          <caption className="samo-za-bralnik">Lestvica lige: ekipe po osvojenih točkah</caption>
          <thead>
            <tr>
              <th scope="col" className="lestvica__mesto">#</th>
              <th scope="col">Ekipa</th>
              <th scope="col" className="lestvica__stevilka lestvica__odigrane">Odig.</th>
              <th scope="col" className="lestvica__stevilka">Z</th>
              <th scope="col" className="lestvica__stevilka">N</th>
              <th scope="col" className="lestvica__stevilka">P</th>
              <th scope="col" className="lestvica__stevilka lestvica__tekme">Tekme</th>
              <th scope="col" className="lestvica__stevilka lestvica__nizi">Nizi</th>
              <th scope="col" className="lestvica__rating">Točke</th>
            </tr>
          </thead>
          <tbody>
            {lestvica.data.map((v) => (
              <tr key={v.idEkipa} className={v.cona ? `lestvica__vrsta--${v.cona.toLowerCase()}` : undefined}>
                <td className="lestvica__mesto">{v.mesto}</td>
                <td className="lestvica__ime">{v.ekipa}</td>
                <td className="lestvica__stevilka lestvica__odigrane">{v.odigrane}</td>
                <td className="lestvica__stevilka lestvica__zmage">{v.zmage}</td>
                <td className="lestvica__stevilka">{v.neodlocene}</td>
                <td className="lestvica__stevilka lestvica__porazi">{v.porazi}</td>
                <td className="lestvica__stevilka lestvica__tekme">
                  {v.dobljeneTekme}:{v.prejeteTekme}
                </td>
                <td className="lestvica__stevilka lestvica__nizi">
                  {v.dobljeniNizi}:{v.prejetiNizi}
                </td>
                <td className="lestvica__rating">{v.tocke}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {conaOpis && (
        <div className="legenda">
          {liga.stNapreduje > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--napreduje" />
              Napreduje
            </span>
          )}
          {liga.stIzpade > 0 && (
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--izpade" />
              Izpade
            </span>
          )}
        </div>
      )}
    </div>
  )
}

function Razpored({ srecanja, kola }: { srecanja: SrecanjeDto[]; kola: number[] }) {
  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razpored</h2>
        <span className="sekcija__meta">
          {kola.length} {kolTekst(kola.length)}
        </span>
      </div>
      <div className="liga__kola">
        {kola.map((kolo) => {
          const vKolu = srecanja.filter((s) => s.kolo === kolo)
          /* Datum kola vzamemo iz prvega srečanja, ki ga ima. */
          const datum = vKolu.find((s) => s.predvidenZacetek)?.predvidenZacetek ?? null
          return (
            <div key={kolo} className="liga__kolo">
              <h3 className="liga__kolo-naslov">
                {kolo}. kolo
                {datum && <span className="liga__kolo-datum">{oblikujDatum(datum.slice(0, 10))}</span>}
              </h3>
              <ul className="liga__srecanja">
                {vKolu.map((s) => {
                  const konec = s.status === 'KONCANO'
                  const domZmaga = konec && s.dobljeneDomaci > s.dobljeneGost
                  const gostZmaga = konec && s.dobljeneGost > s.dobljeneDomaci
                  return (
                    <li key={s.id}>
                      <Link to={`/srecanja/${s.id}`} className="liga__srecanje">
                        <span
                          className={
                            'liga__srecanje-ekipa liga__srecanje-ekipa--desno' +
                            (domZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                            (gostZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                          }
                        >
                          {s.domaci}
                        </span>
                        <span
                          className={
                            'liga__srecanje-izid' + (konec ? '' : ' liga__srecanje-izid--caka')
                          }
                        >
                          {konec ? `${s.dobljeneDomaci} : ${s.dobljeneGost}` : 'vs'}
                        </span>
                        <span
                          className={
                            'liga__srecanje-ekipa' +
                            (gostZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                            (domZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                          }
                        >
                          {s.gost}
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/* Slovnično pravilna oblika besede "kolo" glede na število. */
function kolTekst(n: number): string {
  if (n === 1) return 'kolo'
  if (n === 2) return 'koli'
  if (n === 3 || n === 4) return 'kola'
  return 'kol'
}
