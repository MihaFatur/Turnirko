/* Rocni vpis skupin: organizator pove, kdo je v kateri skupini in kot kateri nosilec
   (jakostno mesto, 1 = najmocnejsi). Izhodisce je racunalniski predlog - isti zreb, kot
   bi ga izvedel dogodek, brez zapisa - pri popravku pa trenutne skupine.

   Nosilec se zamenja z igralcem, ki ga ze ima (kot pri jakostnem vrstnem redu), zato je
   vsak nosilec dodeljen najvec enemu igralcu. Format TOP ima fiksno stevilo skupin, igralci
   brez skupine pa so rezerve. Strežnik vse preveri se enkrat. */
import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { dogodkiApi } from '../api/zahteve'
import { imePrijave } from '../api/tipi'
import type { PrijavaDto, SistemTekmovanja, SkupinaDto, SkupinePredlogDto } from '../api/tipi'
import { sklonIgralcev, sklonSkupin } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

/* Skupine so A–H; vec kot osem jih pri tem stevilu igralcev ne bi bilo smiselno. */
const NAJMANJ_SKUPIN = 2
const NAJVEC_SKUPIN = 8

interface Mesto {
  /* Indeks skupine (0 = A); null = brez skupine (pri TOP rezerva). */
  skupina: number | null
  nosilec: number
}

interface Stanje {
  k: number
  /* Po id-ju prijave. */
  mesta: Record<number, Mesto>
}

interface Lastnosti {
  idDogodka: number
  sistem: SistemTekmovanja
  /* Prijave v zrebu: prijavljeni in (pri TOP po zrebu) rezerve. */
  prijave: PrijavaDto[]
  /* Skupine, ki ze obstajajo (popravek); prazno pred zrebom. */
  skupine: SkupinaDto[]
  steviloSkupin: number
  velikostSkupine: number | null
  onZapri: () => void
  onShranjeno: () => void
}

function oznaka(j: number): string {
  return String.fromCharCode(65 + j)
}

/* Nosilci 1..n brez lukenj in podvojitev; vrstni red ohrani, kot ga da vhod. */
function normaliziraj(mesta: Record<number, Mesto>, prijave: PrijavaDto[]): Record<number, Mesto> {
  const urejeni = [...prijave].sort(
    (a, b) => (mesta[a.id]?.nosilec ?? Number.MAX_SAFE_INTEGER) - (mesta[b.id]?.nosilec ?? Number.MAX_SAFE_INTEGER),
  )
  const nova: Record<number, Mesto> = {}
  urejeni.forEach((p, i) => {
    nova[p.id] = { skupina: mesta[p.id]?.skupina ?? null, nosilec: i + 1 }
  })
  return nova
}

function izPredloga(predlog: SkupinePredlogDto, prijave: PrijavaDto[]): Stanje {
  const mesta: Record<number, Mesto> = {}
  predlog.skupine.forEach((skupina, j) =>
    skupina.forEach((c) => {
      mesta[c.idPrijave] = { skupina: j, nosilec: c.nosilec }
    }),
  )
  predlog.rezerve.forEach((c) => {
    mesta[c.idPrijave] = { skupina: null, nosilec: c.nosilec }
  })
  return { k: predlog.skupine.length, mesta: normaliziraj(mesta, prijave) }
}

function izTrenutnih(skupine: SkupinaDto[], prijave: PrijavaDto[]): Stanje {
  const urejene = skupine
    .filter((s) => s.stopnja === 1)
    .sort((a, b) => a.oznaka.localeCompare(b.oznaka, 'sl'))
  const mesta: Record<number, Mesto> = {}
  prijave.forEach((p) => {
    const j = urejene.findIndex((s) => s.id === p.idSkupina)
    mesta[p.id] = { skupina: j >= 0 ? j : null, nosilec: p.stNosilca ?? Number.MAX_SAFE_INTEGER }
  })
  return { k: urejene.length, mesta: normaliziraj(mesta, prijave) }
}

export function RocniSkupineOkno({
  idDogodka,
  sistem,
  prijave,
  skupine,
  steviloSkupin,
  velikostSkupine,
  onZapri,
  onShranjeno,
}: Lastnosti) {
  /* Format TOP: stevilo skupin je del dogodka, ne izbira organizatorja. */
  const fiksno = sistem === 'SKUPINE'
  const zeZrebano = skupine.some((s) => s.stopnja === 1)

  /* Predlog se pridobi samo pred prvim zrebom; pri popravku je izhodisce trenutno stanje,
     predlog pa organizator prikliče z gumbom. */
  const predlog = useQuery({
    queryKey: ['skupine-predlog', idDogodka],
    queryFn: () => dogodkiApi.predlogSkupin(idDogodka),
    enabled: !zeZrebano,
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  })

  const [stanje, nastaviStanje] = useState<Stanje | null>(() =>
    zeZrebano ? izTrenutnih(skupine, prijave) : null,
  )

  /* Ko predlog prispe in okno se nima izhodisca, je to prvo stanje okna. */
  useEffect(() => {
    if (stanje === null && predlog.data) {
      nastaviStanje(izPredloga(predlog.data, prijave))
    }
  }, [predlog.data, stanje, prijave])

  async function predlagajZnova() {
    const rezultat = await predlog.refetch()
    if (rezultat.data) {
      nastaviStanje(izPredloga(rezultat.data, prijave))
    }
  }

  const k = stanje?.k ?? steviloSkupin
  const mesta = stanje?.mesta ?? {}

  function nastaviSteviloSkupin(novo: number) {
    nastaviStanje((prej) => {
      if (!prej) return prej
      const nova = { ...prej.mesta }
      Object.keys(nova).forEach((id) => {
        const m = nova[Number(id)]
        if (m.skupina !== null && m.skupina >= novo) {
          nova[Number(id)] = { ...m, skupina: null }
        }
      })
      return { k: novo, mesta: nova }
    })
  }

  function nastaviSkupino(id: number, j: number | null) {
    nastaviStanje((prej) =>
      prej && { ...prej, mesta: { ...prej.mesta, [id]: { ...prej.mesta[id], skupina: j } } },
    )
  }

  /* Nosilec, ki ga ze ima drug igralec, se zamenja - nosilci ostanejo enolicni. */
  function nastaviNosilca(id: number, novi: number) {
    nastaviStanje((prej) => {
      if (!prej) return prej
      const stari = prej.mesta[id].nosilec
      const drugi = Object.keys(prej.mesta)
        .map(Number)
        .find((d) => d !== id && prej.mesta[d].nosilec === novi)
      const nova = { ...prej.mesta, [id]: { ...prej.mesta[id], nosilec: novi } }
      if (drugi !== undefined) {
        nova[drugi] = { ...nova[drugi], nosilec: stari }
      }
      return { ...prej, mesta: nova }
    })
  }

  /* Clani skupin po vrsti A, B ..., najmocnejsi prvi - tako kot pri zrebu. */
  const claniSkupin: PrijavaDto[][] = Array.from({ length: k }, () => [])
  prijave.forEach((p) => {
    const m = mesta[p.id]
    if (m && m.skupina !== null && m.skupina < k) claniSkupin[m.skupina].push(p)
  })
  claniSkupin.forEach((c) => c.sort((a, b) => mesta[a.id].nosilec - mesta[b.id].nosilec))
  const vSkupinah = claniSkupin.reduce((vsota, c) => vsota + c.length, 0)
  const brezSkupine = prijave.length - vSkupinah

  /* Vrstice, ki jih strežnik ne bi sprejel; pri TOP so brez skupine rezerve, ne napaka. */
  const opozorila: string[] = []
  if (!fiksno && brezSkupine > 0) {
    opozorila.push(`${brezSkupine} ${sklonIgralcev(brezSkupine)} brez skupine.`)
  }
  if (fiksno && velikostSkupine !== null && vSkupinah > k * velikostSkupine) {
    opozorila.push(`V izboru sme biti največ ${k * velikostSkupine} igralcev, izbranih je ${vSkupinah}.`)
  }
  claniSkupin.forEach((c, j) => {
    if (c.length < 2) {
      opozorila.push(`Skupina ${oznaka(j)} ima ${c.length} ${sklonIgralcev(c.length)} (vsaj 2).`)
    }
  })
  const nosilciVSkupinah = claniSkupin.flat().map((p) => mesta[p.id].nosilec).sort((a, b) => a - b)
  if (nosilciVSkupinah.some((n, i) => n !== i + 1)) {
    opozorila.push(`Jakostna mesta igralcev v skupinah morajo biti od 1 do ${vSkupinah}.`)
  }

  const shranjevanje = useMutation({
    mutationFn: () =>
      dogodkiApi.rocniSkupine(
        idDogodka,
        claniSkupin.map((c) => c.map((p) => ({ idPrijave: p.id, nosilec: mesta[p.id].nosilec }))),
      ),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Igralci po jakostnem mestu - tako organizator vidi vrstni red, ki ga vpisuje. */
  const urejeni = [...prijave].sort(
    (a, b) => (mesta[a.id]?.nosilec ?? Number.MAX_SAFE_INTEGER) - (mesta[b.id]?.nosilec ?? Number.MAX_SAFE_INTEGER),
  )
  const opcijeSkupin = Array.from(
    { length: NAJVEC_SKUPIN - NAJMANJ_SKUPIN + 1 },
    (_, i) => NAJMANJ_SKUPIN + i,
  )

  return (
    <ModalnoOkno naslov={zeZrebano ? 'Popravek skupin' : 'Ročni vpis skupin'} siroko onZapri={onZapri}>
      <p className="obvestilo">
        {zeZrebano
          ? 'Skupine so že žrebane. Popravek je mogoč, dokler se ne začne prva tekma dogodka.'
          : 'Skupine in nosilce vpišeš sam; izhodišče je računalniški predlog. Po shranjevanju se dogodek začne.'}
        {fiksno && ' Format TOP: v skupinah so najboljši, ostali so rezerve.'}
      </p>

      <div className="zreb__krmila">
        <label>
          Število skupin{' '}
          <select
            value={k}
            disabled={fiksno || !stanje}
            onChange={(d) => nastaviSteviloSkupin(Number(d.target.value))}
          >
            {opcijeSkupin.map((n) => (
              <option key={n} value={n}>
                {n} {sklonSkupin(n)}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={predlog.isFetching}
          onClick={() => predlagajZnova()}
        >
          {predlog.isFetching ? 'Žrebam …' : 'Naključno'}
        </button>
        <span className="sekcija__meta">
          {claniSkupin.map((c, j) => `${oznaka(j)} · ${c.length}`).join('   ')}
          {!fiksno && brezSkupine > 0 && `   brez skupine · ${brezSkupine}`}
        </span>
      </div>
      <SporociloNapake napaka={predlog.error} />

      {!stanje ? (
        <p className="obvestilo">Žrebam predlog …</p>
      ) : (
        <ul className="sv-urejanje__clani">
          {urejeni.map((p) => (
            <li key={p.id}>
              <span className="sv-urejanje__ime">
                {imePrijave(p)}
                <span className="sv-urejanje__podrobnost">
                  {[p.klub ?? 'brez kluba', p.rating]
                    .filter((d) => d !== null)
                    .join(' · ')}
                </span>
              </span>
              <span className="sv-urejanje__izbira">
                <select
                  aria-label={`Nosilec igralca ${imePrijave(p)}`}
                  value={mesta[p.id].nosilec}
                  onChange={(d) => nastaviNosilca(p.id, Number(d.target.value))}
                >
                  {urejeni.map((_, i) => (
                    <option key={i + 1} value={i + 1}>
                      {i + 1}.
                    </option>
                  ))}
                </select>
                <select
                  aria-label={`Skupina igralca ${imePrijave(p)}`}
                  value={mesta[p.id].skupina === null ? '' : String(mesta[p.id].skupina)}
                  onChange={(d) =>
                    nastaviSkupino(p.id, d.target.value === '' ? null : Number(d.target.value))
                  }
                >
                  <option value="">{fiksno ? 'rezerva' : 'brez skupine'}</option>
                  {Array.from({ length: k }, (_, j) => (
                    <option key={j} value={j}>
                      {oznaka(j)}
                    </option>
                  ))}
                </select>
              </span>
            </li>
          ))}
        </ul>
      )}

      {/* Opozorila o razporeditvi velja pokazati šele, ko je izhodišče naloženo. */}
      {stanje && opozorila.length > 0 && (
        <div className="zreb__napake">
          <p className="podnaslov-sekcije">Skupine še niso pripravljene</p>
          <ul className="zreb__seznam">
            {opozorila.map((o) => (
              <li key={o}>{o}</li>
            ))}
          </ul>
        </div>
      )}

      <SporociloNapake napaka={shranjevanje.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={!stanje || opozorila.length > 0 || shranjevanje.isPending}
          onClick={() => shranjevanje.mutate()}
        >
          {shranjevanje.isPending
            ? 'Shranjujem …'
            : zeZrebano
              ? 'Shrani popravek skupin'
              : 'Shrani skupine in začni'}
        </button>
      </div>
    </ModalnoOkno>
  )
}
