/* Rocni vpis in popravek skupin (prenova »Skupine 1a«, 23. 11. 2026): organizator pove, kdo je
   v kateri skupini in na kateri poziciji v njej (1 = najmocnejsi v skupini). Pozicija NI
   nosilec turnirja - vsaka skupina ima svoje 1., 2., ... mesto. Izhodisce je racunalniski
   predlog - isti zreb, kot bi ga izvedel dogodek, brez zapisa - pri popravku pa trenutne
   skupine.

   Okno je delovna ploskev: vse skupine so vidne naenkrat, igralca zamenjata dva klika
   (prvi izbere, drugi zamenja), prosto mesto sprejme izbranega igralca (»+ Premakni sem«).
   Pozicije so v vsaki skupini 1..velikost brez lukenj in podvojitev: igralec, ki se
   preseli v drugo skupino, pride na njen konec; zamenjava dveh igralcev zamenja njuni mesti.
   Strežnik vse preveri se enkrat in jakostni vrstni red izpelje sam. */
import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { dogodkiApi } from '../api/zahteve'
import { imePrijave } from '../api/tipi'
import type { PrijavaDto, SistemTekmovanja, SkupinaDto, SkupinePredlogDto } from '../api/tipi'
import { sklonIgralcev, sklonSkupin } from '../pomozno/oblikovanje'
import { oznakaSkupine, primerjajSkupine } from '../pomozno/oznakaSkupine'
import { useTelefon } from '../pomozno/telefon'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

/* Zgornja meja je enaka kot v zaledju. Racunalniski predlog pri 65 igralcih
   da 17 skupin, zato izbira ne sme koncati pri osmih: izbrana vrednost bi izpadla iz seznama. */
const NAJMANJ_SKUPIN = 2
const NAJVEC_SKUPIN = 26

/* Racunalniski zreb skupin+izlocilni ne naredi skupine s petimi (ZrebStoritev.NAJVEC_V_SKUPINI).
   Pri rocnem vpisu je to samo opozorilo: organizator ima zadnjo besedo. */
const NAJVEC_V_SKUPINI = 4

interface Mesto {
  /* Indeks skupine (0 = skupina 1); null = brez skupine (pri TOP rezerva). */
  skupina: number | null
  /* Pozicija v skupini, 1 = najmocnejsi v njej; 0 = brez skupine. */
  pozicija: number
}

interface Stanje {
  k: number
  /* Po id-ju prijave. */
  mesta: Record<number, Mesto>
}

interface Lastnosti {
  idDogodka: number
  sistem: SistemTekmovanja
  /* Mono nadnaslov okna: »Moški posamezno · 48 igralcev«. */
  nadnaslov: string
  /* Prijave v zrebu: prijavljeni in (pri TOP po zrebu) rezerve. */
  prijave: PrijavaDto[]
  /* Skupine, ki ze obstajajo (popravek); prazno pred zrebom. */
  skupine: SkupinaDto[]
  steviloSkupin: number
  velikostSkupine: number | null
  onZapri: () => void
  onShranjeno: () => void
}

/* Isto oznako zapiše strežnik: številka skupine po vrsti (1, 2, 3 ...). */
const oznaka = oznakaSkupine

function izPredloga(predlog: SkupinePredlogDto, prijave: PrijavaDto[]): Stanje {
  const mesta: Record<number, Mesto> = {}
  prijave.forEach((p) => {
    mesta[p.id] = { skupina: null, pozicija: 0 }
  })
  predlog.skupine.forEach((skupina, j) =>
    skupina.forEach((c) => {
      mesta[c.idPrijave] = { skupina: j, pozicija: c.pozicija ?? 0 }
    }),
  )
  return { k: predlog.skupine.length, mesta }
}

/* Pozicija pri obstojecih skupinah je mesto po jakosti (stNosilca) znotraj skupine. */
function izTrenutnih(skupine: SkupinaDto[], prijave: PrijavaDto[]): Stanje {
  const urejene = skupine
    .filter((s) => s.stopnja === 1)
    .sort(primerjajSkupine)
  const mesta: Record<number, Mesto> = {}
  prijave.forEach((p) => {
    mesta[p.id] = { skupina: null, pozicija: 0 }
  })
  urejene.forEach((s, j) => {
    prijave
      .filter((p) => p.idSkupina === s.id)
      .sort((a, b) => (a.stNosilca ?? Number.MAX_SAFE_INTEGER) - (b.stNosilca ?? Number.MAX_SAFE_INTEGER))
      .forEach((p, i) => {
        mesta[p.id] = { skupina: j, pozicija: i + 1 }
      })
  })
  return { k: urejene.length, mesta }
}

/* »4 premaknjeni«: samo števila 1–4 (pri 100) imajo posebno obliko. */
function sklonPremaknjenih(n: number): string {
  const o = n % 100
  if (o === 1) return 'premaknjen'
  if (o === 2) return 'premaknjena'
  if (o === 3 || o === 4) return 'premaknjeni'
  return 'premaknjenih'
}

/* Ime 300, priimek 700; par (dvojice) po vrsti za igralcem; ekipa ostane eno krepko ime. */
function ImeIgralca({ p }: { p: PrijavaDto }) {
  if (p.ime && p.priimek) {
    return (
      <>
        <span className="rocne-skupine__ime-lahko">{p.ime}</span> <b>{p.priimek}</b>
        {p.ime2 && p.priimek2 && (
          <>
            {' / '}
            <span className="rocne-skupine__ime-lahko">{p.ime2}</span> <b>{p.priimek2}</b>
          </>
        )}
      </>
    )
  }
  return <b>{imePrijave(p)}</b>
}

/* Priimek za kratke vrstice (telefon): pri paru oba, pri ekipi ime ekipe. */
function priimekPrijave(p: PrijavaDto): string {
  if (p.priimek) return p.priimek2 ? `${p.priimek} / ${p.priimek2}` : p.priimek
  return imePrijave(p)
}

export function RocniSkupineOkno({
  idDogodka,
  sistem,
  nadnaslov,
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
  const telefon = useTelefon()

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
  /* Izhodisce okna: nanj se vrne »Razveljavi spremembe«, od njega se racuna »premaknjen«. */
  const [izhodisce, nastaviIzhodisce] = useState<Stanje | null>(stanje)
  /* Igralec, ki ga je organizator izbral s prvim klikom; drugi klik ga zamenja. */
  const [izbran, nastaviIzbran] = useState<number | null>(null)

  /* Ko predlog prispe in okno se nima izhodisca, je to prvo stanje okna. */
  useEffect(() => {
    if (stanje === null && predlog.data) {
      const prvo = izPredloga(predlog.data, prijave)
      nastaviStanje(prvo)
      nastaviIzhodisce(prvo)
    }
  }, [predlog.data, stanje, prijave])

  async function predlagajZnova() {
    const rezultat = await predlog.refetch()
    if (rezultat.data) {
      nastaviStanje(izPredloga(rezultat.data, prijave))
      nastaviIzbran(null)
    }
  }

  const k = stanje?.k ?? steviloSkupin
  const mesta = stanje?.mesta ?? {}

  function nastaviSteviloSkupin(novo: number) {
    if (novo < NAJMANJ_SKUPIN || novo > NAJVEC_SKUPIN) return
    nastaviIzbran(null)
    nastaviStanje((prej) => {
      if (!prej) return prej
      const nova = { ...prej.mesta }
      Object.keys(nova).forEach((id) => {
        const m = nova[Number(id)]
        if (m.skupina !== null && m.skupina >= novo) {
          nova[Number(id)] = { skupina: null, pozicija: 0 }
        }
      })
      return { k: novo, mesta: nova }
    })
  }

  /* Igralec v novi skupini pride na njen konec; v stari se za njim vrstni red zapre. */
  function nastaviSkupino(id: number, j: number | null) {
    nastaviStanje((prej) => {
      if (!prej) return prej
      const m = prej.mesta[id]
      if (m.skupina === j) return prej
      const nova = { ...prej.mesta }
      if (m.skupina !== null) {
        Object.keys(nova).forEach((d) => {
          const o = nova[Number(d)]
          if (o.skupina === m.skupina && o.pozicija > m.pozicija) {
            nova[Number(d)] = { ...o, pozicija: o.pozicija - 1 }
          }
        })
      }
      if (j === null) {
        nova[id] = { skupina: null, pozicija: 0 }
      } else {
        const naKoncu = Object.values(nova).filter((o) => o.skupina === j).length
        nova[id] = { skupina: j, pozicija: naKoncu + 1 }
      }
      return { ...prej, mesta: nova }
    })
  }

  /* Prvi klik izbere igralca, klik na drugega zamenja njuni mesti (skupina in pozicija),
     klik na istega izbiro prekliče. */
  function kliknjen(id: number) {
    if (izbran === null) {
      nastaviIzbran(id)
      return
    }
    if (izbran === id) {
      nastaviIzbran(null)
      return
    }
    const a = izbran
    nastaviStanje((prej) => {
      if (!prej) return prej
      return { ...prej, mesta: { ...prej.mesta, [a]: prej.mesta[id], [id]: prej.mesta[a] } }
    })
    nastaviIzbran(null)
  }

  function premakniSem(j: number) {
    if (izbran === null) return
    nastaviSkupino(izbran, j)
    nastaviIzbran(null)
  }

  /* Clani skupin po vrsti 1, 2 ..., najmocnejsi prvi - tako kot pri zrebu. */
  const claniSkupin: PrijavaDto[][] = Array.from({ length: k }, () => [])
  const brezSkupineSeznam: PrijavaDto[] = []
  prijave.forEach((p) => {
    const m = mesta[p.id]
    if (m && m.skupina !== null && m.skupina < k) claniSkupin[m.skupina].push(p)
    else if (m) brezSkupineSeznam.push(p)
  })
  claniSkupin.forEach((c) => c.sort((a, b) => mesta[a.id].pozicija - mesta[b.id].pozicija))
  /* Bazen: najmocnejsi (po ratingu) prvi, da je vrstni red ob vsakem izrisu isti. */
  brezSkupineSeznam.sort((a, b) => (b.rating ?? -1) - (a.rating ?? -1) || a.id - b.id)
  const vSkupinah = claniSkupin.reduce((vsota, c) => vsota + c.length, 0)
  const brezSkupine = prijave.length - vSkupinah

  /* Vrstice, ki jih strežnik ne bi sprejel; pri TOP so brez skupine rezerve, ne napaka. */
  const opozorila: string[] = []
  if (!fiksno && brezSkupine > 0) {
    opozorila.push(`${brezSkupine} ${sklonIgralcev(brezSkupine)} brez skupine.`)
  }
  const prevecVIzboru = fiksno && velikostSkupine !== null && vSkupinah > k * velikostSkupine
  const besediloPrevec = `V izboru sme biti največ ${k * (velikostSkupine ?? 0)} igralcev, izbranih je ${vSkupinah}.`
  if (prevecVIzboru) opozorila.push(besediloPrevec)
  const premale = claniSkupin.flatMap((c, j) => (c.length < 2 ? [j] : []))
  premale.forEach((j) => {
    opozorila.push(`Skupina ${oznaka(j)} ima ${claniSkupin[j].length} ${sklonIgralcev(claniSkupin[j].length)} (vsaj 2).`)
  })

  /* Ne preprecuje shranjevanja (opozorila zgoraj ga): racunalniski zreb take skupine ne naredi,
     rocni vpis pa jih sme. Velja samo za skupine+izlocilni; TOP ima skupine po 8 namenoma. */
  const svarila: string[] = []
  if (sistem === 'SKUPINE_IZLOCILNI') {
    claniSkupin.forEach((c, j) => {
      if (c.length > NAJVEC_V_SKUPINI) {
        svarila.push(
          `Skupina ${oznaka(j)} ima ${c.length} ${sklonIgralcev(c.length)} (običajno največ ${NAJVEC_V_SKUPINI}).`,
        )
      }
    })
  }

  const shranjevanje = useMutation({
    mutationFn: () =>
      dogodkiApi.rocniSkupine(
        idDogodka,
        claniSkupin.map((c) => c.map((p) => ({ idPrijave: p.id, pozicija: mesta[p.id].pozicija }))),
      ),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Premaknjen = drugacen od izhodisca; pri rocnem vpisu izhodisce ni »prej«, zato ga ne kazemo. */
  const spremenjen = (id: number) => {
    if (!zeZrebano || !izhodisce) return false
    const a = izhodisce.mesta[id]
    const b = mesta[id]
    return !a || !b || a.skupina !== b.skupina || a.pozicija !== b.pozicija
  }
  const steviloSprememb = zeZrebano ? prijave.filter((p) => spremenjen(p.id)).length : 0
  const razlikaOdIzhodisca =
    !!stanje && !!izhodisce
    && (stanje.k !== izhodisce.k || prijave.some((p) => {
      const a = izhodisce.mesta[p.id]
      const b = stanje.mesta[p.id]
      return !a || !b || a.skupina !== b.skupina || a.pozicija !== b.pozicija
    }))

  function razveljavi() {
    if (!izhodisce) return
    nastaviStanje(izhodisce)
    nastaviIzbran(null)
  }

  /* Stanje noge: velja prvo, ki drzi. */
  const izbranaPrijava = izbran !== null ? prijave.find((p) => p.id === izbran) : undefined
  const izbranoMesto = izbran !== null ? mesta[izbran] : undefined
  const razporejeno = fiksno || brezSkupine === 0
  let barva = 'v-redu'
  let besedilo: string
  let besediloKratko: string
  if (!stanje) {
    barva = 'caka'
    besedilo = besediloKratko = 'Žrebam predlog …'
  } else if (izbranaPrijava && izbranoMesto) {
    const kje =
      izbranoMesto.skupina === null || izbranoMesto.skupina >= k
        ? fiksno ? 'rezerva' : 'brez skupine'
        : `skupina ${oznaka(izbranoMesto.skupina)}`
    barva = 'izbran'
    besedilo = `Izbran ${imePrijave(izbranaPrijava)} (${kje}). Klikni igralca za zamenjavo ali »Premakni sem«.`
    besediloKratko = `${priimekPrijave(izbranaPrijava)} izbran. Tapni drugega igralca ali »Premakni sem«.`
  } else if (!fiksno && brezSkupine > 0) {
    barva = 'caka'
    besedilo = `Razporedi še ${brezSkupine} ${sklonIgralcev(brezSkupine)}: klikni igralca, nato prosto mesto.`
    besediloKratko = `Razporedi še ${brezSkupine} ${sklonIgralcev(brezSkupine)}.`
  } else if (premale.length > 0) {
    barva = 'napaka'
    besedilo = besediloKratko = `Skupina ${premale.map(oznaka).join(', ')} potrebuje vsaj 2 igralca.`
  } else if (prevecVIzboru) {
    barva = 'napaka'
    besedilo = besediloKratko = besediloPrevec
  } else {
    const velikosti = [...new Set(claniSkupin.map((c) => c.length))].sort((a, b) => a - b)
    besedilo = besediloKratko =
      `Vse je v redu: ${k} ${sklonSkupin(k)} po ${velikosti.join(' ali ')}.`
    if (svarila.length > 0) {
      besedilo += ` ${svarila.join(' ')}`
      besediloKratko = besedilo
    }
  }

  const neMorem =
    !stanje
    || opozorila.length > 0
    || izbran !== null
    || shranjevanje.isPending
    || (zeZrebano && steviloSprememb === 0)

  /* Escape najprej prekliče izbiro, šele nato zapre okno. */
  const obZapiranju = () => {
    if (izbran !== null) nastaviIzbran(null)
    else onZapri()
  }

  const naslov = zeZrebano ? 'Popravi skupine' : 'Ročni vpis skupin'
  const nadnaslovOkna = telefon ? nadnaslov.split(' · ')[0] : nadnaslov
  const najvecMest = fiksno && velikostSkupine ? velikostSkupine : NAJVEC_V_SKUPINI
  const povprecje = (c: PrijavaDto[]) => {
    const z = c.filter((p) => p.rating !== null)
    return z.length ? Math.round(z.reduce((s, p) => s + (p.rating ?? 0), 0) / z.length) : null
  }

  return (
    <ModalnoOkno naslov={naslov} zeloSiroko onZapri={obZapiranju}>
      <div className="rocne-skupine__glava">
        <div className="rocne-skupine__naslovi">
          <span className="rocne-skupine__nad">{nadnaslovOkna}</span>
          <h2 className="rocne-skupine__naslov">{naslov}</h2>
        </div>
        <button type="button" className="rocne-skupine__zapri" aria-label="Zapri" onClick={onZapri}>
          ✕
        </button>
      </div>

      <div className="rocne-skupine__orodja">
        <span className="rocne-skupine__oznaka" id="rocne-skupine-st">Skupin</span>
        <div className="rocne-skupine__koracnik" role="group" aria-labelledby="rocne-skupine-st">
          <button
            type="button"
            className="rocne-skupine__koracnik-gumb"
            aria-label="Ena skupina manj"
            disabled={fiksno || !stanje || k <= NAJMANJ_SKUPIN}
            onClick={() => nastaviSteviloSkupin(k - 1)}
          >
            −
          </button>
          <span className="rocne-skupine__koracnik-st" aria-live="polite">
            {k}
            <span className="rocne-skupine__koracnik-pod">SKUPIN</span>
          </span>
          <button
            type="button"
            className="rocne-skupine__koracnik-gumb"
            aria-label="Ena skupina več"
            disabled={fiksno || !stanje || k >= NAJVEC_SKUPIN}
            onClick={() => nastaviSteviloSkupin(k + 1)}
          >
            +
          </button>
        </div>
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={predlog.isFetching}
          onClick={() => predlagajZnova()}
        >
          {predlog.isFetching ? 'Žrebam …' : zeZrebano ? 'Naključno' : 'Predlog žreba'}
        </button>
        <button
          type="button"
          className="gumb gumb--majhen rocne-skupine__razveljavi"
          disabled={!razlikaOdIzhodisca}
          onClick={razveljavi}
        >
          {telefon ? 'Razveljavi' : 'Razveljavi spremembe'}
        </button>
        <span className="rocne-skupine__namig">Klikni dva igralca in zamenjata se.</span>
      </div>

      {(predlog.error || shranjevanje.error) && (
        <div className="rocne-skupine__napaka">
          <SporociloNapake napaka={predlog.error ?? shranjevanje.error} />
        </div>
      )}

      <div className="rocne-skupine__telo">
        {!stanje ? (
          <p className="obvestilo">Žrebam predlog …</p>
        ) : (
          <>
            {brezSkupineSeznam.length > 0 && (
              <section
                className="rocne-skupine__bazen"
                aria-label={fiksno ? 'Rezerve' : 'Brez skupine'}
              >
                <div className="rocne-skupine__bazen-glava">
                  <span className="rocne-skupine__bazen-naslov">{fiksno ? 'Rezerve' : 'Brez skupine'}</span>
                  <span className="rocne-skupine__bazen-stevec">
                    {telefon && <>{fiksno ? 'Rezerve' : 'Brez skupine'} · </>}
                    {brezSkupineSeznam.length} {sklonIgralcev(brezSkupineSeznam.length)}
                  </span>
                </div>
                <div className="rocne-skupine__cipi">
                  {brezSkupineSeznam.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      className={
                        'rocne-skupine__cip' + (izbran === p.id ? ' rocne-skupine__cip--izbran' : '')
                      }
                      aria-pressed={izbran === p.id}
                      onClick={() => kliknjen(p.id)}
                    >
                      {telefon ? (
                        <b>{priimekPrijave(p)}</b>
                      ) : (
                        <>
                          <span><ImeIgralca p={p} /></span>
                          {p.rating !== null && <span className="rocne-skupine__cip-rating">{p.rating}</span>}
                        </>
                      )}
                    </button>
                  ))}
                </div>
              </section>
            )}

            <div className="rocne-skupine__mreza">
              {claniSkupin.map((c, j) => {
                const povp = povprecje(c)
                const malo = c.length < 2
                const napakaSkupine = malo && razporejeno
                const cilj = izbranoMesto !== undefined && izbranoMesto.skupina !== j
                const prostih = Math.max(najvecMest - c.length, cilj && c.length >= najvecMest ? 1 : 0)
                return (
                  <section
                    key={j}
                    className={
                      'rocne-skupine__skupina' + (napakaSkupine ? ' rocne-skupine__skupina--napaka' : '')
                    }
                    aria-label={`Skupina ${oznaka(j)}`}
                  >
                    <div className="rocne-skupine__skupina-glava">
                      <h3 className="rocne-skupine__skupina-ime">Skupina {oznaka(j)}</h3>
                      <span
                        className={
                          'rocne-skupine__skupina-meta' + (malo ? ' rocne-skupine__skupina-meta--napaka' : '')
                        }
                      >
                        {c.length === 0
                          ? 'prazna'
                          : malo
                            ? '1 igralec'
                            : povp !== null ? `Ø ${povp}` : `${c.length} ${sklonIgralcev(c.length)}`}
                      </span>
                    </div>
                    {c.map((p) => {
                      const jeIzbran = izbran === p.id
                      return (
                        <button
                          key={p.id}
                          type="button"
                          className={
                            'rocne-skupine__vrstica'
                            + (jeIzbran
                              ? ' rocne-skupine__vrstica--izbrana'
                              : spremenjen(p.id) ? ' rocne-skupine__vrstica--premaknjena' : '')
                          }
                          aria-pressed={jeIzbran}
                          onClick={() => kliknjen(p.id)}
                        >
                          <span className="rocne-skupine__mesto">{mesta[p.id].pozicija}</span>
                          <span className="rocne-skupine__igralec">
                            <span className="rocne-skupine__ime"><ImeIgralca p={p} /></span>
                            <span className="rocne-skupine__klub">{p.klub ?? 'brez kluba'}</span>
                          </span>
                          <span className="rocne-skupine__rating">{p.rating ?? '—'}</span>
                        </button>
                      )
                    })}
                    {Array.from({ length: prostih }, (_, i) =>
                      cilj && i === 0 ? (
                        <button
                          key={`prosto-${i}`}
                          type="button"
                          className="rocne-skupine__prosto rocne-skupine__prosto--cilj"
                          onClick={() => premakniSem(j)}
                        >
                          + Premakni sem
                        </button>
                      ) : (
                        <div key={`prosto-${i}`} className="rocne-skupine__prosto" aria-hidden="true">
                          prosto
                        </div>
                      ),
                    )}
                  </section>
                )
              })}
            </div>
          </>
        )}
      </div>

      <div className="rocne-skupine__noga">
        <div className="rocne-skupine__stanje" role="status">
          <span className={`rocne-skupine__kvadratek rocne-skupine__kvadratek--${barva}`} aria-hidden="true" />
          <span className={`rocne-skupine__stanje-besedilo rocne-skupine__stanje-besedilo--${barva}`}>
            {telefon ? besediloKratko : besedilo}
          </span>
          {izbran !== null && !telefon && (
            <button
              type="button"
              className="rocne-skupine__preklici-izbiro"
              onClick={() => nastaviIzbran(null)}
            >
              Prekliči izbiro
            </button>
          )}
        </div>
        {steviloSprememb > 0 && (
          <span className="rocne-skupine__premaknjenih">
            {steviloSprememb} {sklonPremaknjenih(steviloSprememb)}
          </span>
        )}
        {telefon && izbran !== null ? (
          /* Na telefonu med izbiro gumb Shrani odstopi mesto preklicu izbire. */
          <button type="button" className="gumb rocne-skupine__preklici" onClick={() => nastaviIzbran(null)}>
            Prekliči izbiro
          </button>
        ) : (
          <>
            {!telefon && (
              <button type="button" className="gumb rocne-skupine__preklici" onClick={onZapri}>
                Prekliči
              </button>
            )}
            <button
              type="button"
              className="gumb gumb--glavni rocne-skupine__shrani"
              disabled={neMorem}
              onClick={() => shranjevanje.mutate()}
            >
              {shranjevanje.isPending
                ? 'Shranjujem …'
                : zeZrebano
                  ? 'Shrani popravek'
                  : 'Shrani in začni dogodek'}
            </button>
          </>
        )}
      </div>
    </ModalnoOkno>
  )
}
