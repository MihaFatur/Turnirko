/* Meritev branja listkov (načrt F1, samo admin; docs/NACRT-BRANJE-LISTKOV.md).

   Ali branje drži merila za uvedbo (razdelek 1 načrta): listki brez popravka,
   tihe napake (ocena iz naključnih kontrol), čas na listek, neuspeli zajemi.
   Pod tem celice potrjenih listkov (prav/narobe × zanesljiva/za pregled), isto po
   turnirjih in po različicah mreže, ter zlati nabor: meritev trenutnega bralnika
   nad shranjenimi listki in prenos nabora za lokalne meritve.

   Obdobje in turnir sta v naslovu strani (?od=&do=&idTurnir=), da se meritev da
   deliti in osvežiti. */
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { SkupinaMeritve, StanjeNaboraDto } from '../api/tipi'
import { meritevBranjaApi } from '../api/zahteve'
import { useNazaj } from '../komponente/GlavaTelefona'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { odstotek, trajanje } from '../pomozno/branjeListkov'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { oblikujDatum, oblikujTermin, sklonListkov } from '../pomozno/oblikovanje'

/* Cilji za uvedbo (načrt, razdelek 1). */
const CILJ_BREZ_POPRAVKA = 0.8
const CILJ_TIHIH = 1 / 200
const CILJ_CASA_MS = 10_000
const CILJ_NEUSPELIH = 0.05
/* Brez opažene napake je ocena »≤ 1 na 200« zanesljiva šele pri stotinah kontrol. */
const NAJMANJ_KONTROL = 200

type Ocena = 'dosezen' | 'ni' | 'premalo'

/* Delež, ki mora doseči najmanj (ali največ) cilj. */
function ocenaDeleza(del: number, celota: number, cilj: number, najvec = false): Ocena {
  if (celota === 0) return 'premalo'
  const p = del / celota
  return (najvec ? p <= cilj : p >= cilj) ? 'dosezen' : 'ni'
}

/* Tihe napake: ena napaka med malo vzorci že pove, da cilj ni dosežen; brez
   napake pa je »≤ 1 na 200« zanesljivo šele pri NAJMANJ_KONTROL vzorcih. */
function ocenaTihih(napak: number, vzorcev: number): Ocena {
  if (vzorcev > 0 && napak / vzorcev > CILJ_TIHIH) return 'ni'
  return vzorcev >= NAJMANJ_KONTROL ? 'dosezen' : 'premalo'
}

export function MeritevBranjaStran() {
  const [iskanje, nastaviIskanje] = useSearchParams()
  const od = iskanje.get('od')
  const doDatuma = iskanje.get('do')
  const idTurnir = Number(iskanje.get('idTurnir')) || null
  const odjemalec = useQueryClient()

  useNaslovStrani('Meritev branja')
  useNazaj(idTurnir ? `/turnirji/${idTurnir}/branje-listkov` : '/turnirji', idTurnir ? 'Branje listkov' : 'Turnirji')

  const meritev = useQuery({
    queryKey: ['meritev-branja', od, doDatuma, idTurnir],
    queryFn: () => meritevBranjaApi.meritev(od, doDatuma, idTurnir),
  })
  const nabor = useQuery({
    queryKey: ['meritev-branja', 'nabor'],
    queryFn: meritevBranjaApi.nabor,
    // meritev nabora teče v ozadju nekaj minut
    refetchInterval: (poizvedba) => (poizvedba.state.data?.tece ? 2000 : false),
  })
  const izmeri = useMutation({
    mutationFn: meritevBranjaApi.izmeriNabor,
    onSuccess: (stanje) => odjemalec.setQueryData(['meritev-branja', 'nabor'], stanje),
  })
  const prenos = useMutation({
    mutationFn: async () => {
      const zip = await meritevBranjaApi.zipNabora()
      const url = URL.createObjectURL(zip)
      const povezava = document.createElement('a')
      povezava.href = url
      povezava.download = 'zlati-nabor.zip'
      povezava.click()
      setTimeout(() => URL.revokeObjectURL(url), 10_000)
    },
  })

  function nastavi(kljuc: string, vrednost: string | null) {
    const novo = new URLSearchParams(iskanje)
    if (vrednost) novo.set(kljuc, vrednost)
    else novo.delete(kljuc)
    nastaviIskanje(novo, { replace: true })
  }

  const m = meritev.data
  const imeTurnirja = idTurnir ? m?.poTurnirjih.find((t) => t.idTurnir === idTurnir)?.oznaka : null

  return (
    <section className="meritev-branja">
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">Meritev branja</h1>
          <p className="uvod uvod--tesno">
            Kako dobro Turnirko bere sodniške listke in koliko dela ima organizator.
          </p>
        </div>
      </div>

      <div className="meritev-branja__filtri">
        <label className="obrazec__polje">
          <span>Od</span>
          <input type="date" value={m?.od ?? od ?? ''} onChange={(e) => nastavi('od', e.target.value)} />
        </label>
        <label className="obrazec__polje">
          <span>Do</span>
          <input type="date" value={m?.doDatuma ?? doDatuma ?? ''} onChange={(e) => nastavi('do', e.target.value)} />
        </label>
        {idTurnir && (
          <p className="meritev-branja__turnir">
            Samo turnir {imeTurnirja ?? `#${idTurnir}`}.{' '}
            <button type="button" className="gumb gumb--majhen" onClick={() => nastavi('idTurnir', null)}>
              Vsi turnirji
            </button>
          </p>
        )}
      </div>

      <SporociloNapake napaka={meritev.error ?? nabor.error ?? izmeri.error ?? prenos.error} />
      {meritev.isPending && <p className="obvestilo">Nalaganje …</p>}

      {m && (
        <>
          <h2 className="meritev-branja__razdelek">Merila za uvedbo</h2>
          {m.skupaj.branj === 0 ? (
            <p className="namig">V tem obdobju ni branj listkov.</p>
          ) : (
            <>
              <Merila s={m.skupaj} />
              <h3 className="meritev-branja__podnaslov">Celice potrjenih listkov</h3>
              <Celice s={m.skupaj} />
            </>
          )}

          {m.poTurnirjih.length > 1 && (
            <>
              <h2 className="meritev-branja__razdelek">Po turnirjih</h2>
              <Tabela skupine={m.poTurnirjih} prvi="Turnir"
                onIzberi={(s) => s.idTurnir !== null && nastavi('idTurnir', String(s.idTurnir))} />
            </>
          )}
          {m.poMrezah.length > 0 && (
            <>
              <h2 className="meritev-branja__razdelek">Po različicah mreže</h2>
              <Tabela skupine={m.poMrezah} prvi="Mreža" />
            </>
          )}
        </>
      )}

      <h2 className="meritev-branja__razdelek">Zlati nabor</h2>
      {nabor.data && (
        <Nabor
          stanje={nabor.data}
          zaseden={izmeri.isPending}
          prenasam={prenos.isPending}
          onIzmeri={() => izmeri.mutate()}
          onPrenesi={() => prenos.mutate()}
        />
      )}
    </section>
  )
}

/* Štiri merila iz načrta in delež za en dotik, vsako z vrednostjo in ciljem. */
function Merila({ s }: { s: SkupinaMeritve }) {
  const brezPopravka = ocenaDeleza(s.brezPopravka, s.potrjenih, CILJ_BREZ_POPRAVKA)
  const tihe = ocenaTihih(s.kontrolZNapako, s.kontrol)
  const cas: Ocena = s.casi.mediana === null ? 'premalo' : s.casi.mediana <= CILJ_CASA_MS ? 'dosezen' : 'ni'
  const neuspeli = ocenaDeleza(s.neuspelih, s.branj, CILJ_NEUSPELIH, true)
  return (
    <ul className="meritev-branja__merila">
      <Merilo
        oznaka="Brez popravka"
        vrednost={odstotek(s.brezPopravka, s.potrjenih)}
        podrobnost={`${s.brezPopravka} od ${s.potrjenih} potrjenih listkov`}
        cilj="cilj ≥ 80 %"
        ocena={brezPopravka}
      />
      <Merilo
        oznaka="Tihe napake v kontrolah"
        vrednost={`${s.kontrolZNapako} od ${s.kontrol}`}
        podrobnost={
          `Kontrola je naključen zanesljiv listek, pregledan v celoti. ` +
          `Opažene tihe napake vseh listkov: ${s.tihihNapak} od ${s.pripravljenih} za en dotik.`
        }
        cilj="cilj ≤ 1 na 200"
        ocena={tihe}
      />
      <Merilo
        oznaka="Čas na listek"
        vrednost={trajanje(s.casi.mediana)}
        podrobnost={
          `Mediana; izmerjenih potrditev: ${s.casi.izmerjenih}, 90 % jih je hitrejših od ${trajanje(s.casi.p90)}. ` +
          `En dotik ${trajanje(s.casi.medianaEnDotik)}, obrazec ${trajanje(s.casi.medianaObrazec)}.`
        }
        cilj="cilj ≤ 10 s"
        ocena={cas}
      />
      <Merilo
        oznaka="Neuspeli zajemi"
        vrednost={odstotek(s.neuspelih, s.branj)}
        podrobnost={`${s.neuspelih} od ${s.branj} fotografij ni dalo predloga`}
        cilj="cilj ≤ 5 %"
        ocena={neuspeli}
      />
      <Merilo
        oznaka="Za en dotik"
        vrednost={odstotek(s.pripravljenih, s.potrjenih)}
        podrobnost={`${s.pripravljenih} od ${s.potrjenih} potrjenih je bilo brez opozoril in negotovih celic`}
      />
    </ul>
  )
}

function Merilo({
  oznaka,
  vrednost,
  podrobnost,
  cilj,
  ocena,
}: {
  oznaka: string
  vrednost: string
  podrobnost: string
  cilj?: string
  ocena?: Ocena
}) {
  return (
    <li className="meritev-branja__merilo">
      <span className="meritev-branja__oznaka">{oznaka}</span>
      <span className="meritev-branja__vrednost">{vrednost}</span>
      {cilj && (
        <span className={`meritev-branja__cilj meritev-branja__cilj--${ocena}`}>
          {cilj} ·{' '}
          {ocena === 'dosezen' ? 'dosežen' : ocena === 'ni' ? 'ni dosežen' : 'premalo podatkov'}
        </span>
      )}
      <span className="meritev-branja__podrobnost">{podrobnost}</span>
    </li>
  )
}

/* Celice potrjenih listkov: prav ali narobe × zanesljiva ali za pregled. */
function Celice({ s }: { s: SkupinaMeritve }) {
  const c = s.celice
  return (
    <div className="tabela-ovoj">
      <table className="tabela meritev-branja__celice">
        <thead>
          <tr>
            <th>
              {s.izmerjenih} {sklonListkov(s.izmerjenih)}
            </th>
            <th className="meritev-branja__desno">Zanesljiva</th>
            <th className="meritev-branja__desno">Za pregled</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Prav</td>
            <td className="meritev-branja__stevilo">{c.pravZanesljive}</td>
            <td className="meritev-branja__stevilo">
              {c.pravNegotove} <span className="meritev-branja__pojasnilo">lažni alarm</span>
            </td>
          </tr>
          <tr>
            <td>Narobe</td>
            <td className="meritev-branja__stevilo meritev-branja__stevilo--tiha">
              {c.narobeZanesljive} <span className="meritev-branja__pojasnilo">tiha</span>
            </td>
            <td className="meritev-branja__stevilo">
              {c.narobeNegotove} <span className="meritev-branja__pojasnilo">ujeta</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

/* Turnirji ali mreže v vrsticah; klik na turnir omeji meritev nanj. */
function Tabela({
  skupine,
  prvi,
  onIzberi,
}: {
  skupine: SkupinaMeritve[]
  prvi: string
  onIzberi?: (s: SkupinaMeritve) => void
}) {
  return (
    <div className="tabela-ovoj">
      <table className="tabela meritev-branja__tabela">
        <thead>
          <tr>
            <th>{prvi}</th>
            <th className="meritev-branja__desno">Fotografij</th>
            <th className="meritev-branja__desno">Potrjenih</th>
            <th className="meritev-branja__desno">Brez popravka</th>
            <th className="meritev-branja__desno">Tihe napake</th>
            <th className="meritev-branja__desno">Čas</th>
          </tr>
        </thead>
        <tbody>
          {skupine.map((s) => (
            <tr key={`${s.idTurnir ?? ''}-${s.oznaka}`}>
              <td>
                {onIzberi ? (
                  <button type="button" className="meritev-branja__izbira" onClick={() => onIzberi(s)}>
                    {s.oznaka}
                  </button>
                ) : (
                  s.oznaka
                )}
                {s.datum && <span className="meritev-branja__datum">{oblikujDatum(s.datum)}</span>}
              </td>
              <td className="meritev-branja__stevilo">{s.branj}</td>
              <td className="meritev-branja__stevilo">{s.potrjenih}</td>
              <td className="meritev-branja__stevilo">{odstotek(s.brezPopravka, s.potrjenih)}</td>
              <td className="meritev-branja__stevilo">{s.tihihNapak}</td>
              <td className="meritev-branja__stevilo">{trajanje(s.casi.mediana)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* Zlati nabor: velikost, meritev trenutnega bralnika (v ozadju) in prenos. */
function Nabor({
  stanje,
  zaseden,
  prenasam,
  onIzmeri,
  onPrenesi,
}: {
  stanje: StanjeNaboraDto
  zaseden: boolean
  prenasam: boolean
  onIzmeri: () => void
  onPrenesi: () => void
}) {
  const [odprto, nastaviOdprto] = useState(false)
  const r = stanje.rezultat
  return (
    <div className="meritev-branja__nabor">
      <p className="meritev-branja__besedilo">
        {stanje.listkov} od največ {stanje.najvec} potrjenih listkov, naključno izbranih. Na listku so samo
        celice s točkami, brez imen. Na naboru se meri vsaka sprememba bralnika in vsaka nova mreža.
      </p>
      <div className="meritev-branja__gumbi">
        <button type="button" className="gumb gumb--glavni" disabled={stanje.tece || zaseden || stanje.listkov === 0}
          onClick={onIzmeri}>
          Izmeri trenutni bralnik
        </button>
        <button type="button" className="gumb" disabled={prenasam || stanje.listkov === 0} onClick={onPrenesi}>
          {prenasam ? 'Pripravljam …' : 'Prenesi nabor (zip)'}
        </button>
      </div>
      {stanje.tece && (
        <p className="meritev-branja__besedilo">
          Merim … {stanje.obdelanih} od {stanje.vseh} listkov (mreža {stanje.mreza}).
        </p>
      )}
      {!stanje.tece && stanje.napaka && <p className="napaka">{stanje.napaka}</p>}
      {!stanje.tece && r && (
        <>
          <p className="meritev-branja__besedilo">
            Zadnja meritev: mreža {stanje.mreza}, {oblikujTermin(stanje.konec)}, {r.izmerjenih}{' '}
            {sklonListkov(r.izmerjenih)}.
          </p>
          <ul className="meritev-branja__merila">
            <Merilo
              oznaka="Brez napake"
              vrednost={odstotek(r.brezPopravka, r.izmerjenih)}
              podrobnost={`${r.brezPopravka} od ${r.izmerjenih} listkov prebranih brez napačne celice`}
              cilj="cilj ≥ 80 %"
              ocena={ocenaDeleza(r.brezPopravka, r.izmerjenih, CILJ_BREZ_POPRAVKA)}
            />
            <Merilo
              oznaka="Tihe napake"
              vrednost={`${r.tihihNapak} od ${r.pripravljenih}`}
              podrobnost="Listki za en dotik, ki imajo napačno celico."
              cilj="cilj ≤ 1 na 200"
              ocena={ocenaTihih(r.tihihNapak, r.pripravljenih)}
            />
            <Merilo
              oznaka="Za en dotik"
              vrednost={odstotek(r.pripravljenih, r.izmerjenih)}
              podrobnost={`${r.pripravljenih} od ${r.izmerjenih} listkov brez opozoril in negotovih celic`}
            />
          </ul>
          <button type="button" className="gumb gumb--majhen" onClick={() => nastaviOdprto((o) => !o)}>
            {odprto ? 'Skrij celice' : 'Pokaži celice'}
          </button>
          {odprto && <Celice s={r} />}
        </>
      )}
      <p className="meritev-branja__besedilo">
        <Link to="/pomoc/branje-listkov">Kako deluje branje listkov</Link>
      </p>
    </div>
  )
}
