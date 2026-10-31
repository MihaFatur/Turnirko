/* Kode popusta ob registraciji (administrator, stran Dostopi → »Kode za popust«).

   Admin nastavi kodo in ugodnost, ki jo prinese; oseba jo vpiše v povzetku registracije
   (RegistracijaTok). Tri vrste (strežnik: KodePopustaStoritev):
   - Premium brez plačila: brezplačna registracija igralca, preizkus Premium traja toliko
     mesecev namesto enega;
   - popust na prvo plačilo: registracija s plačilom (Premium ali organizator), obnova je
     po polni ceni;
   - organizatorski paket brez plačila: toliko mesecev od potrditve organizatorja.

   Ugodnosti kode po nastanku ni mogoče spremeniti (kdor se je registriral, je dobil, kar je
   koda obljubljala): popravi se opis, rok, največ uporab in aktivnost. Neuporabljeno kodo je
   mogoče izbrisati, uporabljeno samo izklopiti - sicer bi računi izgubili sled, od kod imajo
   ugodnost. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { kodePopustaApi } from '../api/zahteve'
import {
  OZNAKE_PAKET,
  type KodaPopustaDto,
  type KodaPopustaVnos,
  type Paket,
  type StanjeKodePopusta,
  type VrstaKodePopusta,
} from '../api/tipi'
import { opisUgodnosti } from '../pomozno/kodePopusta'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'

const OZNAKE_VRSTE: Record<VrstaKodePopusta, string> = {
  PREMIUM_MESECI: 'Premium brez plačila (igralec)',
  ODSTOTEK: 'Popust na prvo plačilo',
  ORGANIZATOR_MESECI: 'Organizatorski paket brez plačila',
  NAVIJAC_MESECI: 'Paket Navijač brez plačila (navijač)',
}

/* Kaj vrsta pomeni za osebo in kje velja: admin mora vedeti, preden kodo razdeli. */
const POJASNILO_VRSTE: Record<VrstaKodePopusta, string> = {
  PREMIUM_MESECI:
    'Brezplačna registracija igralca. Preizkus Premium traja toliko mesecev namesto enega; '
    + 'začne se, ko je račun povezan z igralcem.',
  ODSTOTEK:
    'Registracija s plačilom (Premium, Navijač ali organizator). Popust velja za prvo plačilo, obnova je po '
    + 'polni ceni. Največ 75 %: Stripe ne sprejme plačila pod 0,50 €.',
  ORGANIZATOR_MESECI:
    'Registracija organizatorja brez plačila. Paket velja toliko mesecev od tvoje potrditve '
    + 'organizatorja; po koncu se organizator vrne brez paketa.',
  NAVIJAC_MESECI:
    'Brezplačna registracija navijača. Paket Navijač velja toliko mesecev od potrditve računa; po '
    + 'koncu se račun vrne na brezplačen paket (en spremljan igralec).',
}

const OZNAKE_STANJA: Record<StanjeKodePopusta, string> = {
  VELJA: 'Velja',
  IZKLJUCENA: 'Izklopljena',
  POTEKLA: 'Potekla',
  IZCRPANA: 'Izčrpana',
}

const ORGANIZATORSKI_PAKETI: Paket[] = ['ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO']

export function KodePopustaOkno({ onZapri }: { onZapri: () => void }) {
  const kode = useQuery({ queryKey: ['kode-popusta'], queryFn: kodePopustaApi.seznam })
  const [novaOdprta, nastaviNovoOdprto] = useState(false)
  const [ustvarjena, nastaviUstvarjeno] = useState<string | null>(null)

  return (
    <ModalnoOkno nadnaslov="Dostopi" naslov="Kode za popust" siroko onZapri={onZapri}>
      <p className="kode__uvod">
        Oseba kodo vpiše v povzetku registracije. Ugodnosti kode po nastanku ni mogoče spremeniti:
        spremeniš lahko rok, največ uporab in kodo izklopiš. Za drugačno ugodnost ustvari novo kodo.
      </p>

      {kode.isPending && <p className="obvestilo">Nalaganje …</p>}
      <SporociloNapake napaka={kode.error} />
      {ustvarjena && (
        <p className="obvestilo kode__ustvarjena" role="status">
          Koda <strong>{ustvarjena}</strong> je ustvarjena.
        </p>
      )}

      {kode.data && kode.data.length === 0 && !novaOdprta && (
        <p className="kode__prazno">Kod še ni. Ustvari prvo.</p>
      )}
      {kode.data && kode.data.length > 0 && (
        <ul className="kode__seznam">
          {kode.data.map((k) => (
            <VrsticaKode key={k.id} koda={k} />
          ))}
        </ul>
      )}

      {novaOdprta ? (
        <NovaKoda
          onUstvarjena={(koda) => {
            nastaviUstvarjeno(koda)
            nastaviNovoOdprto(false)
          }}
          onPreklici={() => nastaviNovoOdprto(false)}
        />
      ) : (
        <div className="obrazec__gumbi kode__gumbi-nova">
          <button
            type="button"
            className="gumb gumb--glavni"
            onClick={() => {
              nastaviUstvarjeno(null)
              nastaviNovoOdprto(true)
            }}
          >
            Nova koda
          </button>
        </div>
      )}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Zapri
        </button>
      </div>
    </ModalnoOkno>
  )
}

function VrsticaKode({ koda: k }: { koda: KodaPopustaDto }) {
  const odjemalec = useQueryClient()
  const [urejanje, nastaviUrejanje] = useState(false)
  const [brisanje, nastaviBrisanje] = useState(false)
  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['kode-popusta'] })

  const izbrisi = useMutation({
    mutationFn: () => kodePopustaApi.izbrisi(k.id),
    onSuccess: osvezi,
  })
  const jeZasedena = k.uporab + k.cakajocih > 0

  return (
    <li className="kode__vrstica">
      <div className="kode__glava">
        <span className="kode__koda">{k.koda}</span>
        <span className={'znacka ' + (k.stanje === 'VELJA' ? 'znacka--uspeh' : 'znacka--PRIPRAVA')}>
          {OZNAKE_STANJA[k.stanje]}
        </span>
      </div>
      <div className="kode__ugodnost">
        {opisUgodnosti(k)}
        {k.zaVlogo
          && ` · samo ${k.zaVlogo === 'IGRALEC' ? 'igralci' : k.zaVlogo === 'NAVIJAC' ? 'navijači' : 'organizatorji'}`}
      </div>
      <div className="kode__meta">
        <span>
          Uporabljena {k.uporab}
          {k.najvecUporab !== null ? ` od ${k.najvecUporab}` : ''}
          {k.cakajocih > 0 ? ` · ${k.cakajocih} čaka na plačilo` : ''}
        </span>
        <span>{k.veljaDo ? `Velja do ${oblikujDatum(k.veljaDo)}` : 'Brez roka'}</span>
      </div>
      {k.opis && <p className="kode__opis">{k.opis}</p>}

      {urejanje ? (
        <UrediKodo koda={k} onKonec={() => nastaviUrejanje(false)} />
      ) : brisanje ? (
        <div className="kode__dejanja">
          <span className="kode__vprasanje">Izbrišem kodo {k.koda}?</span>
          <button
            type="button"
            className="gumb gumb--nevaren"
            disabled={izbrisi.isPending}
            onClick={() => izbrisi.mutate()}
          >
            Izbriši
          </button>
          <button type="button" className="gumb" onClick={() => nastaviBrisanje(false)}>
            Prekliči
          </button>
        </div>
      ) : (
        <div className="kode__dejanja">
          <button type="button" className="gumb" onClick={() => nastaviUrejanje(true)}>
            Uredi
          </button>
          {/* Kode, ki jo je kdo uporabil ali jo drži plačilni poskus, ni mogoče izbrisati:
              izklopi se (pri Uredi). */}
          {!jeZasedena && (
            <button type="button" className="gumb" onClick={() => nastaviBrisanje(true)}>
              Izbriši
            </button>
          )}
        </div>
      )}
      <SporociloNapake napaka={izbrisi.error} />
    </li>
  )
}

function UrediKodo({ koda: k, onKonec }: { koda: KodaPopustaDto; onKonec: () => void }) {
  const odjemalec = useQueryClient()
  const [opis, nastaviOpis] = useState(k.opis ?? '')
  const [veljaDo, nastaviVeljaDo] = useState(k.veljaDo ?? '')
  const [najvec, nastaviNajvec] = useState(k.najvecUporab !== null ? String(k.najvecUporab) : '')
  const [aktivna, nastaviAktivna] = useState(k.aktivna)

  const shrani = useMutation({
    mutationFn: () =>
      kodePopustaApi.uredi(k.id, {
        opis: opis.trim() || null,
        veljaDo: veljaDo || null,
        najvecUporab: najvec ? Number(najvec) : null,
        aktivna,
      }),
    onSuccess: () => {
      void odjemalec.invalidateQueries({ queryKey: ['kode-popusta'] })
      onKonec()
    },
  })

  return (
    <form
      className="obrazec kode__obrazec"
      onSubmit={(d) => {
        d.preventDefault()
        shrani.mutate()
      }}
    >
      <label className="obrazec__polje">
        <span>Opis (vidiš samo ti)</span>
        <input value={opis} maxLength={200} onChange={(d) => nastaviOpis(d.target.value)} />
      </label>
      <div className="obrazec__vrstica obrazec__vrstica--par">
        <label className="obrazec__polje">
          <span>Velja do</span>
          <input type="date" value={veljaDo} onChange={(d) => nastaviVeljaDo(d.target.value)} />
        </label>
        <label className="obrazec__polje">
          <span>Največ uporab</span>
          <StevilskoPolje
            vrednost={najvec}
            naSpremembo={nastaviNajvec}
            placeholder="neomejeno"
            aria-label="Največ uporab"
          />
        </label>
      </div>
      <label className="obrazec__potrditev">
        <input type="checkbox" checked={aktivna} onChange={(d) => nastaviAktivna(d.target.checked)} />
        <span>Koda je vklopljena</span>
      </label>
      <SporociloNapake napaka={shrani.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onKonec}>
          Prekliči
        </button>
        <button type="submit" className="gumb gumb--glavni" disabled={shrani.isPending}>
          {shrani.isPending ? 'Shranjujem …' : 'Shrani'}
        </button>
      </div>
    </form>
  )
}

function NovaKoda({
  onUstvarjena,
  onPreklici,
}: {
  onUstvarjena: (koda: string) => void
  onPreklici: () => void
}) {
  const odjemalec = useQueryClient()
  const [koda, nastaviKodo] = useState('')
  const [opis, nastaviOpis] = useState('')
  const [vrsta, nastaviVrsto] = useState<VrstaKodePopusta>('PREMIUM_MESECI')
  const [meseci, nastaviMesece] = useState('3')
  const [odstotek, nastaviOdstotek] = useState('20')
  const [paket, nastaviPaket] = useState<Paket>('ORGANIZATOR_PLUS')
  const [zaVlogo, nastaviVlogo] = useState<'' | 'IGRALEC' | 'NAVIJAC' | 'ORGANIZATOR'>('')
  const [veljaDo, nastaviVeljaDo] = useState('')
  const [najvec, nastaviNajvec] = useState('')

  const ustvari = useMutation({
    mutationFn: () => {
      const vnos: KodaPopustaVnos = {
        koda: koda.trim() || null,
        opis: opis.trim() || null,
        vrsta,
        meseci: vrsta === 'ODSTOTEK' ? null : Number(meseci) || null,
        odstotek: vrsta === 'ODSTOTEK' ? Number(odstotek) || null : null,
        paket: vrsta === 'ORGANIZATOR_MESECI' ? paket : null,
        zaVlogo: vrsta === 'ODSTOTEK' && zaVlogo ? zaVlogo : null,
        veljaDo: veljaDo || null,
        najvecUporab: najvec ? Number(najvec) : null,
      }
      return kodePopustaApi.ustvari(vnos)
    },
    onSuccess: (nova) => {
      void odjemalec.invalidateQueries({ queryKey: ['kode-popusta'] })
      onUstvarjena(nova.koda)
    },
  })

  return (
    <form
      className="obrazec kode__obrazec kode__obrazec--nova"
      onSubmit={(d) => {
        d.preventDefault()
        ustvari.mutate()
      }}
    >
      <h3 className="kode__naslov-obrazca">Nova koda</h3>

      <label className="obrazec__polje">
        <span>Ugodnost</span>
        <select value={vrsta} onChange={(d) => nastaviVrsto(d.target.value as VrstaKodePopusta)}>
          {(Object.keys(OZNAKE_VRSTE) as VrstaKodePopusta[]).map((v) => (
            <option key={v} value={v}>
              {OZNAKE_VRSTE[v]}
            </option>
          ))}
        </select>
        <span className="kode__pojasnilo">{POJASNILO_VRSTE[vrsta]}</span>
      </label>

      <div className="obrazec__vrstica obrazec__vrstica--par">
        {vrsta !== 'ODSTOTEK' && (
          <label className="obrazec__polje">
            <span>Število mesecev</span>
            <StevilskoPolje vrednost={meseci} naSpremembo={nastaviMesece} najvec={36} aria-label="Število mesecev" />
          </label>
        )}
        {vrsta === 'ODSTOTEK' && (
          <>
            <label className="obrazec__polje">
              <span>Popust v %</span>
              <StevilskoPolje vrednost={odstotek} naSpremembo={nastaviOdstotek} najvec={75} aria-label="Popust v odstotkih" />
            </label>
            <label className="obrazec__polje">
              <span>Za koga</span>
              <select value={zaVlogo} onChange={(d) => nastaviVlogo(d.target.value as typeof zaVlogo)}>
                <option value="">Igralce, navijače in organizatorje</option>
                <option value="IGRALEC">Samo igralce</option>
                <option value="NAVIJAC">Samo navijače</option>
                <option value="ORGANIZATOR">Samo organizatorje</option>
              </select>
            </label>
          </>
        )}
        {vrsta === 'ORGANIZATOR_MESECI' && (
          <label className="obrazec__polje">
            <span>Paket</span>
            <select value={paket} onChange={(d) => nastaviPaket(d.target.value as Paket)}>
              {ORGANIZATORSKI_PAKETI.map((p) => (
                <option key={p} value={p}>
                  {OZNAKE_PAKET[p]}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <label className="obrazec__polje">
        <span>Koda</span>
        <input
          value={koda}
          maxLength={40}
          placeholder="prazno = naključna"
          autoComplete="off"
          spellCheck={false}
          onChange={(d) => nastaviKodo(d.target.value)}
        />
        <span className="kode__pojasnilo">
          3 do 32 znakov: črke brez šumnikov, številke in vezaj. Oseba jo vpiše z velikimi ali malimi črkami.
        </span>
      </label>

      <div className="obrazec__vrstica obrazec__vrstica--par">
        <label className="obrazec__polje">
          <span>Velja do</span>
          <input type="date" value={veljaDo} onChange={(d) => nastaviVeljaDo(d.target.value)} />
        </label>
        <label className="obrazec__polje">
          <span>Največ uporab</span>
          <StevilskoPolje
            vrednost={najvec}
            naSpremembo={nastaviNajvec}
            placeholder="neomejeno"
            aria-label="Največ uporab"
          />
        </label>
      </div>

      <label className="obrazec__polje">
        <span>Opis (vidiš samo ti)</span>
        <input
          value={opis}
          maxLength={200}
          placeholder="npr. razdeljeno na turnirju v Celju"
          onChange={(d) => nastaviOpis(d.target.value)}
        />
      </label>

      <SporociloNapake napaka={ustvari.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onPreklici}>
          Prekliči
        </button>
        <button type="submit" className="gumb gumb--glavni" disabled={ustvari.isPending}>
          {ustvari.isPending ? 'Ustvarjam …' : 'Ustvari kodo'}
        </button>
      </div>
    </form>
  )
}
