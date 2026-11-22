/* Stran za sodnika (S2, /s): po tekmi fotografira izpolnjen listek s svojim
   telefonom, brez prijave, preveri prebrane točke in izid potrdi.

   Pravico da skrivnost v QR kodi listka (strežnik jo preveri, preden branje
   nastane), nato enkraten žeton branja. Žeton je samo v pomnilniku te strani:
   ne gre v naslov, localStorage ali sessionStorage. Ob osvežitvi strani je
   izgubljen - sodnik listek fotografira znova (nova fotografija istega listka
   nadomesti staro).

   Brez signala se fotografija pošilja znova, dokler je stran odprta (kot vrsta
   na pultu, F5); po ~20 s sodnik izve, naj listek odda na pultu. Če fotografija
   pride pozneje, jo organizator vidi kot nepotrjeno.

   Sodnik vpiše samo odigrano tekmo s točkami. Negotove celice mora potrditi z
   dotikom: »Potrdi« brez gledanja je največja nevarnost te poti (otroci sodijo
   tudi sami). Zmagovalec je zato napisan z velikimi črkami. */
import { useEffect, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import type { SodniskaPotrditev, SodniskoBranjeDto } from '../api/tipi'
import { sodnikApi } from '../api/zahteve'
import { ZnakTurnirko } from '../komponente/Postavitev'
import { useBudenZaslon } from '../pomozno/budenZaslon'
import { opisNapakeFotografije, pripraviFotografijo } from '../pomozno/branjeListkov'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import {
  besediloZmagovalca,
  celiceZaPotrditev,
  izidSodnika,
  mizaIzPolja,
  vrsticeSodnika,
  type VrsticaSodnika,
} from '../pomozno/sodnik'
import { CASOVNA_MEJA_MS, novKljucPosiljanja, odzivNaNapako, zamikPonovitve } from '../pomozno/vrstaFotografij'

/* Po tem času brez odgovora sodnik izve, naj listek odda na pultu. */
const OPOZORILO_SIGNALA_MS = 20_000

type Faza =
  | { vrsta: 'zacetek' }
  | { vrsta: 'pripravljam' }
  | { vrsta: 'posiljam'; brezSignala: boolean }
  | { vrsta: 'branje'; id: number; zeton: string }
  /* vpisano: izid je že v tekmovanju; sicer čaka organizatorja. Pri stopnji
     VELJA_TAKOJ čaka, kadar fotografija izida ne podpira (sodnik je spremenil
     zanesljivo prebrano številko ali je listek prazen) - zaledje: smeTakoj. */
  | { vrsta: 'hvala'; vpisano: boolean; stopnja: SodniskaPotrditev | null }
  | { vrsta: 'napaka'; sporocilo: string }

const cakaj = (ms: number) => new Promise((r) => setTimeout(r, ms))

export function SodnikStran() {
  useNaslovStrani('Sodnik')
  useBudenZaslon(true)
  const [faza, nastaviFazo] = useState<Faza>({ vrsta: 'zacetek' })
  /* Prekine pošiljanje (nova fotografija, odhod s strani). */
  const posiljanje = useRef(0)
  useEffect(() => {
    const stevec = posiljanje
    return () => {
      stevec.current++
    }
  }, [])

  async function poslji(datoteka: File) {
    const moje = ++posiljanje.current
    const velja = () => posiljanje.current === moje
    nastaviFazo({ vrsta: 'pripravljam' })
    let jpeg: Blob
    try {
      jpeg = (await pripraviFotografijo(datoteka)).jpeg
    } catch (e) {
      if (velja()) nastaviFazo({ vrsta: 'napaka', sporocilo: opisNapakeFotografije(e) })
      return
    }
    if (!velja()) return
    const kljuc = novKljucPosiljanja()
    nastaviFazo({ vrsta: 'posiljam', brezSignala: false })
    const opozorilo = setTimeout(() => {
      if (velja()) nastaviFazo((f) => (f.vrsta === 'posiljam' ? { ...f, brezSignala: true } : f))
    }, OPOZORILO_SIGNALA_MS)
    try {
      for (let poskus = 1; velja(); poskus++) {
        const prekinitev = new AbortController()
        const meja = setTimeout(() => prekinitev.abort(), CASOVNA_MEJA_MS)
        try {
          const poslano = await sodnikApi.poslji(jpeg, kljuc, prekinitev.signal)
          if (velja()) nastaviFazo({ vrsta: 'branje', id: poslano.idBranja, zeton: poslano.zeton })
          return
        } catch (e) {
          if (!velja()) return
          if (odzivNaNapako(e) !== 'ponovi') {
            nastaviFazo({ vrsta: 'napaka', sporocilo: opisNapake(e) })
            return
          }
          await cakaj(zamikPonovitve(poskus, e))
        } finally {
          clearTimeout(meja)
        }
      }
    } finally {
      clearTimeout(opozorilo)
    }
  }

  function obFotografiji(dogodek: ChangeEvent<HTMLInputElement>) {
    const datoteka = dogodek.target.files?.[0]
    dogodek.target.value = ''
    if (datoteka) void poslji(datoteka)
  }

  const branje = useQuery({
    queryKey: ['sodnik-branje', faza.vrsta === 'branje' ? faza.id : null],
    queryFn: () => (faza.vrsta === 'branje' ? sodnikApi.stanje(faza.id, faza.zeton) : Promise.reject()),
    enabled: faza.vrsta === 'branje',
    refetchInterval: (q) => (!q.state.data || q.state.data.stanje === 'V_VRSTI' ? 1500 : false),
    retry: (n, e) => odzivNaNapako(e) === 'ponovi' && n < 30,
    gcTime: 0,
  })

  const gumbFotografije = (besedilo: string, glavni = true) => (
    <label className={'gumb sodnik__fotografiraj' + (glavni ? ' gumb--glavni' : '')}>
      {besedilo}
      <input type="file" accept="image/*" capture="environment" className="sodnik__datoteka"
        onChange={obFotografiji} />
    </label>
  )

  let vsebina
  if (faza.vrsta === 'zacetek') {
    vsebina = (
      <>
        <p className="sodnik__uvod">
          Po tekmi položi izpolnjen listek na mizo in ga fotografiraj od zgoraj, da sta na sliki
          cela tabela in koda v desnem kotu. Turnirko prebere točke, ti jih preveriš in potrdiš.
        </p>
        {gumbFotografije('Slikaj listek')}
        <p className="sodnik__drobno">
          Prijava ni potrebna. Fotografija gre organizatorju turnirja in se izbriše najkasneje 30 dni
          po turnirju. Listek po tekmi vseeno oddaj na pultu.
        </p>
      </>
    )
  } else if (faza.vrsta === 'pripravljam' || faza.vrsta === 'posiljam') {
    vsebina = (
      <div role="status">
        <p className="sodnik__stanje">{faza.vrsta === 'pripravljam' ? 'Pripravljam fotografijo …' : 'Pošiljam …'}</p>
        {faza.vrsta === 'posiljam' && faza.brezSignala && (
          <p className="sodnik__signal">
            Ni signala. Listek oddaj na pultu – fotografija se pošlje sama, ko bo povezava, če pustiš
            to stran odprto.
          </p>
        )}
        <button type="button" className="gumb" onClick={() => {
          posiljanje.current++
          nastaviFazo({ vrsta: 'zacetek' })
        }}>
          Prekliči
        </button>
      </div>
    )
  } else if (faza.vrsta === 'hvala') {
    vsebina = (
      <>
        <p className="sodnik__hvala">
          {faza.vpisano ? 'Izid je vpisan.' : 'Hvala! Izid čaka na potrditev organizatorja.'}
        </p>
        {!faza.vpisano && faza.stopnja === 'VELJA_TAKOJ' && (
          <p className="sodnik__uvod">
            Izid se ne ujema povsem s tem, kar je Turnirko prebral s fotografije, zato ga pogleda še
            organizator.
          </p>
        )}
        <p className="sodnik__uvod">Listek oddaj na pultu.</p>
        {gumbFotografije('Slikaj naslednji listek')}
      </>
    )
  } else if (faza.vrsta === 'napaka') {
    vsebina = <Napaka sporocilo={faza.sporocilo} gumb={gumbFotografije('Fotografiraj znova')} />
  } else if (branje.error && odzivNaNapako(branje.error) !== 'ponovi') {
    vsebina = <Napaka sporocilo={opisNapake(branje.error)} gumb={gumbFotografije('Fotografiraj znova')} />
  } else if (!branje.data || branje.data.stanje === 'V_VRSTI') {
    vsebina = <p className="sodnik__stanje" role="status">Berem listek …</p>
  } else if (branje.data.stanje === 'PREBRANO') {
    vsebina = (
      <Potrditev
        key={branje.data.id}
        branje={branje.data}
        zeton={faza.zeton}
        onPotrjeno={(b) => nastaviFazo({ vrsta: 'hvala', vpisano: b.stanje === 'POTRJENO', stopnja: b.velja })}
        gumbZnova={gumbFotografije('Fotografiraj znova', false)}
      />
    )
  } else {
    // NAPAKA branja ali zaključeno (nadomeščeno z novejšo fotografijo, potrjeno na pultu)
    vsebina = (
      <Napaka
        sporocilo={branje.data.napaka ?? 'Listka ni bilo mogoče prebrati.'}
        gumb={gumbFotografije('Fotografiraj znova')}
      />
    )
  }

  return (
    <main className="sodnik">
      <header className="sodnik__glava">
        <span className="sodnik__znamka">
          <ZnakTurnirko velikost={22} />
          Turnirko
        </span>
        <span className="sodnik__vloga">Sodnik</span>
      </header>
      {vsebina}
    </main>
  )
}

function Napaka({ sporocilo, gumb }: { sporocilo: string; gumb: ReactNode }) {
  return (
    <>
      <p className="sodnik__napaka" role="alert">{sporocilo}</p>
      {gumb}
      <p className="sodnik__drobno">Če ne gre, listek oddaj na pultu.</p>
    </>
  )
}

/* Pregled in potrditev prebranega listka. */
function Potrditev({
  branje,
  zeton,
  onPotrjeno,
  gumbZnova,
}: {
  branje: SodniskoBranjeDto
  zeton: string
  onPotrjeno: (b: SodniskoBranjeDto) => void
  gumbZnova: ReactNode
}) {
  const [vrstice, nastaviVrstice] = useState<VrsticaSodnika[]>(() =>
    vrsticeSodnika(branje.predlog, branje.steviloNizov),
  )
  const zaPotrditev = celiceZaPotrditev(branje.predlog)
  const [preverjene, nastaviPreverjene] = useState<Set<number>>(() => new Set())
  const [miza, nastaviMizo] = useState(String(branje.prebranaMiza ?? branje.poklicanaMiza ?? ''))
  const [mizaPreverjena, nastaviMizaPreverjena] = useState(!branje.negotovaMiza)

  const izid = izidSodnika(vrstice, branje.steviloNizov)
  const prebranaMiza = mizaIzPolja(miza)
  const manjka = [...zaPotrditev].filter((m) => !preverjene.has(m)).length + (mizaPreverjena ? 0 : 1)

  const potrditev = useMutation({
    mutationFn: () => sodnikApi.potrdi(branje.id, zeton, izid.vnos!, prebranaMiza.miza),
    onSuccess: onPotrjeno,
  })

  if (branje.izidZeVpisan) {
    return <Napaka sporocilo="Izid te tekme je že vpisan. Listek oddaj na pultu." gumb={gumbZnova} />
  }

  const preveri = (mesto: number) => nastaviPreverjene((p) => (p.has(mesto) ? p : new Set(p).add(mesto)))
  const spremeni = (i: number, stran: 1 | 2, vrednost: string) => {
    const cisto = vrednost.replace(/\D/g, '').slice(0, 2)
    nastaviVrstice((v) => v.map((r, j) => (j !== i ? r : stran === 1 ? { ...r, tocke1: cisto } : { ...r, tocke2: cisto })))
    preveri(i * 2 + stran - 1)
  }
  // opozorila strežnika (npr. več listkov na sliki); celice so obarvane posebej
  const opozorila = (branje.predlog?.opozorila ?? []).filter((o) => !o.startsWith('Ta tekma ima izid'))
  const razlicnaMiza = branje.poklicanaMiza !== null && branje.prebranaMiza !== null
    && branje.poklicanaMiza !== branje.prebranaMiza

  return (
    <form className="sodnik__potrditev" onSubmit={(e) => {
      e.preventDefault()
      if (izid.vnos && manjka === 0 && prebranaMiza.veljavna) potrditev.mutate()
    }}>
      <p className="sodnik__tekma">
        {branje.dogodek}{branje.del ? ` · ${branje.del}` : ''}
      </p>
      <p className="sodnik__zmagovalec" aria-live="polite">
        {izid.vnos && izid.dobljeni
          ? besediloZmagovalca(izid.dobljeni, branje.igralec1, branje.igralec2)
          : 'Preveri točke'}
      </p>
      {zaPotrditev.size > 0 && (
        <p className="sodnik__navodilo">Obarvane številke primerjaj z listkom in se jih dotakni.</p>
      )}
      {opozorila.map((o) => <p key={o} className="sodnik__opozorilo">{o}</p>)}

      <div className="sodnik__nizi" role="group" aria-label="Točke po nizih">
        <span />
        <span className="sodnik__ime">{branje.igralec1}</span>
        <span />
        <span className="sodnik__ime">{branje.igralec2}</span>
        {vrstice.map((r, i) => (
          <Vrstica key={i} i={i} r={r} zaPotrditev={zaPotrditev} preverjene={preverjene}
            onSpremeni={spremeni} onPreveri={preveri} />
        ))}
      </div>

      <label className="sodnik__miza">
        Miza
        <input
          className={'sodnik__polje' + (!mizaPreverjena ? ' sodnik__polje--preveri' : '')}
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={2}
          value={miza}
          onFocus={() => nastaviMizaPreverjena(true)}
          onChange={(e) => {
            nastaviMizo(e.target.value.replace(/\D/g, '').slice(0, 2))
            nastaviMizaPreverjena(true)
          }}
        />
      </label>
      {razlicnaMiza && (
        <p className="sodnik__opozorilo">
          Na listku piše miza {branje.prebranaMiza}, tekma je bila poklicana na mizo {branje.poklicanaMiza}.
          Vpiši, kje ste igrali.
        </p>
      )}
      {!prebranaMiza.veljavna && <p className="sodnik__napaka">Miza je število od 1 do 99.</p>}
      {izid.napaka && vrstice.some((r) => r.tocke1 !== '' || r.tocke2 !== '') && (
        <p className="sodnik__napaka">{izid.napaka}</p>
      )}
      {potrditev.error && <p className="sodnik__napaka" role="alert">{opisNapake(potrditev.error)}</p>}

      <button
        type="submit"
        className="gumb gumb--glavni sodnik__potrdi"
        disabled={!izid.vnos || manjka > 0 || !prebranaMiza.veljavna || potrditev.isPending}
      >
        {potrditev.isPending
          ? 'Potrjujem …'
          : manjka > 0
            ? `Preveri še ${manjka} ${manjka === 1 ? 'številko' : manjka === 2 ? 'številki' : manjka <= 4 ? 'številke' : 'številk'}`
            : 'Potrdi izid'}
      </button>
      {gumbZnova}
      <p className="sodnik__drobno">
        Predaja, brez boja ali kaj drugega na listku? Ne potrjuj – listek oddaj na pultu.
      </p>
    </form>
  )
}

function Vrstica({
  i,
  r,
  zaPotrditev,
  preverjene,
  onSpremeni,
  onPreveri,
}: {
  i: number
  r: VrsticaSodnika
  zaPotrditev: Set<number>
  preverjene: Set<number>
  onSpremeni: (i: number, stran: 1 | 2, vrednost: string) => void
  onPreveri: (mesto: number) => void
}) {
  const polje = (stran: 1 | 2) => {
    const mesto = i * 2 + stran - 1
    const preveri = zaPotrditev.has(mesto) && !preverjene.has(mesto)
    return (
      <input
        className={'sodnik__polje' + (preveri ? ' sodnik__polje--preveri' : '')}
        inputMode="numeric"
        pattern="[0-9]*"
        maxLength={2}
        aria-label={`${i + 1}. niz, ${stran === 1 ? 'prvi' : 'drugi'} igralec`}
        value={stran === 1 ? r.tocke1 : r.tocke2}
        onFocus={() => onPreveri(mesto)}
        onChange={(e) => onSpremeni(i, stran, e.target.value)}
      />
    )
  }
  return (
    <>
      <span className="sodnik__niz">{i + 1}. niz</span>
      {polje(1)}
      <span className="sodnik__dvopicje">:</span>
      {polje(2)}
    </>
  )
}
