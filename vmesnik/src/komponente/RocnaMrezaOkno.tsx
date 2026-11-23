/* Rocna izlocilna mreza / popravek mreze (prenova »Mreza 1b«, 23. 11. 2026): organizator vidi
   mrezo cetrtino za cetrtino in igralce vleci z miso (na telefonu: tap na mesto in izbira iz
   seznama). Mesta so od vrha mreze navzdol; sosednja mesta (1.-2., 3.-4. ...) igrajo med seboj
   v prvem kolu; prosto mesto pomeni prosti prehod. Izhodisce je racunalniski predlog - isti
   zreb, ki bi ga naredil dogodek -, gumb »Nakljucno« ga naredi znova.

   Izjema od DESIGN.md (odlocitev lastnika, razdelek 5k »Delovna ploskev zreba«): okno je cez
   cel zaslon, glava je crna, mreza ima crte. Vlecenje na drugo mesto igralca zamenja (pri
   polni mrezi je vsak igralec ze nekje); vlecenje na odlagalisce naredi mesto PRAZNO
   (samo v odjemalcu), Shrani pa je onemogocen, dokler je kaj praznega. Isto naredi
   tipkovnica: Enter/preslednica izbere mesto, drugo mesto ga zamenja.
   Ponovni vpis pred prvo tekmo izlocilnega dela je popravek. Strežnik vse preveri se enkrat. */
import { useEffect, useMemo, useState, type DragEvent, type KeyboardEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import type { IzlocilniPredlogDto } from '../api/tipi'
import { sklonIgralcev } from '../pomozno/oblikovanje'
import { useKompakten } from '../pomozno/telefon'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

export interface Udelezenec {
  /* Polno ime: za bralnik zaslona, iskanje in besedilo stanja. */
  ime: string
  /* Ime (lahko, 300) in priimek (krepko, 700) za vrstico; pri paru je priimek »Novak / Zajc«. */
  imeLahko: string
  priimek: string
  /* Mesto v skupini, npr. »3 · 1.«; prazno pri izbiri, ki skupin nima. */
  uvrstitev: string
  klub: string | null
  rating: number | null
  /* Jakostno mesto (1 = najmocnejsi); crna znacka »nosilec« pri prvi cetrtini mrez. */
  stNosilca: number | null
}

interface Lastnosti {
  naslov: string
  /* Ime dogodka: »Moški posamezno« v mono nadnaslovu. */
  podnaslov: string
  /* Popravek ze zrebane mreze (Naključno, Shrani popravek) ali prvi vpis (Nov predlog). */
  popravek: boolean
  /* Podatki o igralcih po id-ju prijave. */
  poId: Map<number, Udelezenec>
  predlog: () => Promise<IzlocilniPredlogDto>
  shrani: (idjiPoMestih: (number | null)[]) => Promise<unknown>
  onZapri: () => void
  onShranjeno: () => void
}

/* Mesto, ki ga je organizator odnesel na odlagalisce: ni »prosto« (to je null, prosti prehod),
   ampak se nic ne igra, dokler tja ne pride kdo. Ostane samo v odjemalcu. */
const PRAZNO = 'prazno'
type Mesto = number | null | typeof PRAZNO

/* Od kod se vleče: mesto v mreži ali vnos na odlagališču. */
type Vir = { iz: 'mesto'; i: number } | { iz: 'odlagalisce'; j: number }

/* Do te velikosti mreze (mest) se vse vidi naenkrat in cetrtin ni. */
const NAJVEC_BREZ_CETRTIN = 16

function jeId(m: Mesto | undefined): m is number {
  return typeof m === 'number'
}

function sklonProstihPrehodov(n: number): string {
  const o = n % 100
  if (o === 1) return 'prosti prehod'
  if (o === 2) return 'prosta prehoda'
  if (o === 3 || o === 4) return 'prosti prehodi'
  return 'prostih prehodov'
}

export function RocnaMrezaOkno({
  naslov,
  podnaslov,
  popravek,
  poId,
  predlog,
  shrani,
  onZapri,
  onShranjeno,
}: Lastnosti) {
  const telefon = useKompakten()
  const [udelezenci, nastaviUdelezence] = useState<number[]>([])
  const [mesta, nastaviMesta] = useState<Mesto[] | null>(null)
  /* Prvi predlog - »Razveljavi« se vrne na to razporeditev. */
  const [izhodisce, nastaviIzhodisce] = useState<Mesto[] | null>(null)
  /* Igralci (in prosta mesta) na odlagališču; vlečeš jih nazaj na katero koli mesto. */
  const [odlagalisce, nastaviOdlagalisce] = useState<(number | null)[]>([])
  const [cetrtina, nastaviCetrtino] = useState(0)
  const [vlecem, nastaviVlecem] = useState<Vir | null>(null)
  const [cilj, nastaviCilj] = useState<number | null>(null)
  /* Izbrano mesto: na namizju drugi klik/Enter zamenja, na telefonu odpre spodnji list. */
  const [izbrano, nastaviIzbrano] = useState<number | null>(null)
  const [iskanje, nastaviIskanje] = useState('')

  const predlogZahteva = useMutation({
    mutationFn: predlog,
    onSuccess: (p) => {
      nastaviUdelezence(p.udelezenci)
      nastaviMesta(p.idjiPoMestih)
      nastaviIzhodisce((prej) => prej ?? p.idjiPoMestih)
      nastaviOdlagalisce([])
      nastaviIzbrano(null)
    },
  })

  useEffect(() => {
    predlogZahteva.mutate()
    // predlog se pridobi enkrat ob odprtju okna
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stMest = mesta?.length ?? 0
  const velikostCetrtine = stMest > NAJVEC_BREZ_CETRTIN ? stMest / 4 : stMest
  const jeCetrtin = stMest > NAJVEC_BREZ_CETRTIN
  const prag = stMest / 4

  const prazno = mesta ? mesta.filter((m) => m === PRAZNO).length : 0
  const prostih = mesta ? mesta.filter((m) => m === null).length : 0
  const sprememb = mesta && izhodisce ? mesta.filter((m, i) => m !== izhodisce[i]).length : 0

  /* Dve prosti mesti v istem paru bi pomenili dvoboj brez igralcev - strežnik to zavrne. */
  const napacniPari = useMemo(() => {
    const pari: number[] = []
    if (mesta) {
      for (let i = 0; i < mesta.length; i += 2) {
        if (mesta[i] === null && mesta[i + 1] === null) pari.push(i / 2 + 1)
      }
    }
    return pari
  }, [mesta])

  const shranjevanje = useMutation({
    mutationFn: () => shrani((mesta ?? []).map((m) => (jeId(m) ? m : null))),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const igralec = (m: Mesto | undefined): Udelezenec | null => (jeId(m) ? (poId.get(m) ?? null) : null)
  const nosilec = (u: Udelezenec | null): string | null =>
    u && u.stNosilca !== null && u.stNosilca <= prag && !u.uvrstitev ? String(u.stNosilca) : null

  /* Spusti vir na mesto ali odlagališče; vse spremembe naenkrat, da je izris dosleden. */
  function spusti(ciljVira: { iz: 'mesto'; i: number } | { iz: 'odlagalisce' }) {
    if (!vlecem || !mesta) return
    const novaMesta = [...mesta]
    const novoOdlagalisce = [...odlagalisce]
    if (vlecem.iz === 'mesto' && ciljVira.iz === 'mesto') {
      ;[novaMesta[vlecem.i], novaMesta[ciljVira.i]] = [novaMesta[ciljVira.i], novaMesta[vlecem.i]]
    } else if (vlecem.iz === 'mesto' && ciljVira.iz === 'odlagalisce') {
      const v = novaMesta[vlecem.i]
      if (v === PRAZNO) return
      novoOdlagalisce.push(v)
      novaMesta[vlecem.i] = PRAZNO
    } else if (vlecem.iz === 'odlagalisce' && ciljVira.iz === 'mesto') {
      const v = novoOdlagalisce[vlecem.j]
      const staro = novaMesta[ciljVira.i]
      novaMesta[ciljVira.i] = v
      novoOdlagalisce.splice(vlecem.j, 1)
      if (staro !== PRAZNO) novoOdlagalisce.push(staro)
    }
    nastaviMesta(novaMesta)
    nastaviOdlagalisce(novoOdlagalisce)
    nastaviVlecem(null)
    nastaviCilj(null)
  }

  function zamenjaj(i: number, k: number) {
    if (!mesta) return
    const nova = [...mesta]
    ;[nova[i], nova[k]] = [nova[k], nova[i]]
    nastaviMesta(nova)
    nastaviIzbrano(null)
    nastaviIskanje('')
  }

  /* Klik ali Enter na mestu: prvo izbere, drugo zamenja, isto izbiro prekliče. */
  function kliknjeno(i: number) {
    if (telefon || izbrano === null) {
      nastaviIzbrano(i)
      nastaviIskanje('')
    } else if (izbrano === i) {
      nastaviIzbrano(null)
    } else {
      zamenjaj(izbrano, i)
    }
  }

  function obTipki(dogodek: KeyboardEvent, i: number) {
    if (dogodek.key === 'Enter' || dogodek.key === ' ') {
      dogodek.preventDefault()
      kliknjeno(i)
    }
  }

  function razveljavi() {
    if (!izhodisce) return
    nastaviMesta(izhodisce)
    nastaviOdlagalisce([])
    nastaviIzbrano(null)
  }

  /* Escape najprej zapre spodnji list oziroma prekliče izbiro, šele nato okno. */
  const obZapiranju = () => {
    if (izbrano !== null) {
      nastaviIzbrano(null)
      nastaviIskanje('')
    } else onZapri()
  }

  /* Stanje: prazna mesta, dve prosti v paru, sicer pripravljeno. */
  let barva = 'v-redu'
  let kratko = 'Pripravljeno'
  let dolgo = ''
  if (mesta) {
    if (prazno > 0) {
      barva = 'napaka'
      kratko = `${prazno} prazno`
      dolgo = `${prazno} ${prazno === 1 ? 'mesto je prazno' : 'mest je praznih'}, igralci čakajo na odlagališču.`
    } else if (napacniPari.length > 0) {
      barva = 'napaka'
      kratko = `Par ${napacniPari.join(', ')}: dve prosti`
      dolgo = `Par ${napacniPari.join(', ')} ima dve prosti mesti.`
    } else {
      const vsiObNosilcih = mesta.every(
        (m, i) => m !== null || nosilec(igralec(mesta[i ^ 1])) !== null,
      )
      dolgo =
        prostih === 0
          ? 'Mreža je polna, brez prostih prehodov.'
          : `${prostih} ${sklonProstihPrehodov(prostih)}${vsiObNosilcih ? ', vsi ob nosilcih' : ''}.`
    }
  }
  const neMorem =
    !mesta || prazno > 0 || napacniPari.length > 0 || shranjevanje.isPending || (popravek && sprememb === 0)

  /* Pogled: cetrtina (ali vse) kot bloki po 4 mesta (2 para in tekma 2. kola). */
  const blokov = velikostCetrtine / 4
  const zacetekCetrtine = jeCetrtin ? cetrtina * velikostCetrtine : 0
  const bloki = Array.from({ length: Math.max(0, Math.floor(blokov)) }, (_, r) => zacetekCetrtine + r * 4)
  const vCetrtini = (q: number) =>
    Array.from({ length: velikostCetrtine }, (_, d) => q * velikostCetrtine + d)

  const nadnaslov = `${podnaslov}${udelezenci.length > 0 ? ` · ${udelezenci.length} ${sklonIgralcev(udelezenci.length)}` : ''}${stMest > 0 ? ` · mreža ${stMest}` : ''}`
  const nadnaslovTelefon = `${podnaslov}${stMest > 0 ? ` · mreža ${stMest}` : ''}`

  /* Kdo gre iz para naprej: prosti prehod ali »Priimek / Priimek« in zmagovalec. */
  const naprej = (i: number) => {
    const a = mesta?.[i]
    const b = mesta?.[i + 1]
    const prost = a === null ? b : b === null ? a : undefined
    if (jeId(prost)) {
      const u = igralec(prost)
      return { ime: u ? `${u.imeLahko} ${u.priimek}`.trim() : '—', opis: 'prosti prehod', pomembno: true }
    }
    const kratkoIme = (m: Mesto | undefined) => igralec(m)?.priimek ?? '—'
    return { ime: `${kratkoIme(a)} / ${kratkoIme(b)}`, opis: `zmagovalec ${i + 1}–${i + 2}`, pomembno: false }
  }
  const naprejKratko = (i: number) => {
    const n = naprej(i)
    if (!n.pomembno) return `zmag. ${i + 1}–${i + 2}`
    return igralec(mesta?.[i] === null ? mesta?.[i + 1] : mesta?.[i])?.priimek ?? '—'
  }

  /* Podatki o cetrtini za desni stolpec in zavihke na telefonu. */
  const cetrtine = Array.from({ length: jeCetrtin ? 4 : 0 }, (_, q) => {
    const mestaQ = vCetrtini(q)
    const nos: Udelezenec[] = []
    let lukenj = 0
    let napaka = false
    mestaQ.forEach((i) => {
      const m = mesta?.[i]
      const u = igralec(m)
      if (u && nosilec(u)) nos.push(u)
      if (m === PRAZNO) lukenj++
      if (i % 2 === 0 && mesta?.[i] === null && mesta?.[i + 1] === null) napaka = true
    })
    nos.sort((x, y) => (x.stNosilca ?? 0) - (y.stNosilca ?? 0))
    return {
      q,
      napaka,
      meta: napaka ? 'napaka' : lukenj ? `${lukenj} prazno` : `${q * velikostCetrtine + 1}–${(q + 1) * velikostCetrtine}`,
      nosilci: nos.slice(0, 2).map((u) => `[${u.stNosilca}] ${u.priimek}`).join(' · '),
    }
  })

  /* Spodnji list (telefon): vsi igralci v mreži po abecedi priimkov, na vrhu prosto mesto. */
  const izbranoV = izbrano !== null ? mesta?.[izbrano] : undefined
  const zadetki = (() => {
    if (!mesta || izbrano === null) return []
    const q = iskanje.trim().toLowerCase()
    const vsi: { i: number; u: Udelezenec }[] = []
    mesta.forEach((m, i) => {
      const u = igralec(m)
      if (u && i !== izbrano) vsi.push({ i, u })
    })
    vsi.sort((a, b) => a.u.priimek.localeCompare(b.u.priimek, 'sl'))
    const sez: { kljuc: string; u: Udelezenec | null; kje: string; i: number }[] = vsi
      .filter((x) => !q || `${x.u.imeLahko} ${x.u.priimek}`.toLowerCase().includes(q))
      .slice(0, 30)
      .map((x) => ({ kljuc: `i${x.i}`, u: x.u, kje: `mesto ${x.i + 1}`, i: x.i }))
    if (izbranoV !== null && !q) {
      const prosto = mesta.findIndex((x, i) => x === null && Math.floor(i / 2) !== Math.floor(izbrano / 2))
      if (prosto >= 0) sez.unshift({ kljuc: 'prosto', u: null, kje: `mesto ${prosto + 1}`, i: prosto })
    }
    return sez
  })()

  /* Dogodki vlečenja: ciljno mesto in pogled na četrtino pod prstom. */
  const naMesto = (i: number) => ({
    onDragOver: (e: DragEvent) => {
      e.preventDefault()
      if (cilj !== i) nastaviCilj(i)
    },
    onDrop: (e: DragEvent) => {
      e.preventDefault()
      spusti({ iz: 'mesto', i })
    },
  })

  const naslovOkna = naslov
  const imeMesta = (i: number) => {
    const m = mesta?.[i]
    const u = igralec(m)
    return m === PRAZNO ? 'prazno' : u ? u.ime : 'prosto'
  }

  const vrsticaMesta = (i: number) => {
    const m = mesta?.[i]
    const u = igralec(m)
    const luknja = m === PRAZNO
    const jeCilj = cilj === i && vlecem !== null
    const sel = izbrano === i
    const sprem = !!izhodisce && m !== izhodisce[i] && !luknja
    const nos = nosilec(u)
    const razredi =
      'rocna-mreza__mesto'
      + (luknja ? ' rocna-mreza__mesto--luknja' : u ? '' : ' rocna-mreza__mesto--prosto')
      + (jeCilj ? ' rocna-mreza__mesto--cilj' : '')
      + (sel ? ' rocna-mreza__mesto--izbrano' : '')
      + (sprem ? ' rocna-mreza__mesto--spremenjeno' : '')
      + (vlecem && vlecem.iz === 'mesto' && vlecem.i === i ? ' rocna-mreza__mesto--vlecem' : '')
    return (
      <div
        key={i}
        className={razredi}
        role="button"
        tabIndex={0}
        aria-pressed={sel}
        aria-label={`Mesto ${i + 1}: ${imeMesta(i)}`}
        draggable={!luknja && !telefon}
        onDragStart={(e) => {
          e.dataTransfer.effectAllowed = 'move'
          e.dataTransfer.setData('text/plain', String(i))
          nastaviVlecem({ iz: 'mesto', i })
        }}
        onDragEnd={() => {
          nastaviVlecem(null)
          nastaviCilj(null)
        }}
        {...naMesto(i)}
        onClick={() => kliknjeno(i)}
        onKeyDown={(e) => obTipki(e, i)}
      >
        <span className="rocna-mreza__st">{i + 1}</span>
        <span className="rocna-mreza__igralec">
          {u && nos && <span className="rocna-mreza__nosilec">{nos}</span>}
          {u && !nos && u.uvrstitev && <span className="rocna-mreza__uvrstitev">{u.uvrstitev}</span>}
          <span className="rocna-mreza__ime">
            {u ? (
              <>
                <b>{u.priimek}</b> {u.imeLahko && <span className="rocna-mreza__ime-lahko">{u.imeLahko}</span>}
              </>
            ) : luknja ? 'spusti sem' : 'prosto'}
          </span>
          {u && !telefon && u.klub && <span className="rocna-mreza__klub">{u.klub}</span>}
        </span>
        <span className="rocna-mreza__rating">{u?.rating ?? ''}</span>
      </div>
    )
  }

  const par = (i: number) => {
    const napaka = mesta?.[i] === null && mesta?.[i + 1] === null
    return (
      <div key={i} className={'rocna-mreza__par' + (napaka ? ' rocna-mreza__par--napaka' : '')}>
        {vrsticaMesta(i)}
        {vrsticaMesta(i + 1)}
      </div>
    )
  }

  const odlozeni = odlagalisce.map((v, j) => {
    const u = igralec(v)
    return (
      <div
        key={j}
        className="rocna-mreza__odlozen"
        draggable
        onDragStart={(e) => {
          e.dataTransfer.effectAllowed = 'move'
          e.dataTransfer.setData('text/plain', `p${j}`)
          nastaviVlecem({ iz: 'odlagalisce', j })
        }}
        onDragEnd={() => {
          nastaviVlecem(null)
          nastaviCilj(null)
        }}
      >
        <b>{u ? `${u.priimek} ${u.imeLahko}`.trim() : 'prosto mesto'}</b>
        <span className="rocna-mreza__rating">{u?.rating ?? ''}</span>
      </div>
    )
  })

  const naslovCetrtine = jeCetrtin
    ? `${cetrtina + 1}. četrtina · mesta ${zacetekCetrtine + 1}–${zacetekCetrtine + velikostCetrtine}`
    : `Mesta 1–${stMest}`

  return (
    <ModalnoOkno naslov={naslovOkna} celZaslon onZapri={obZapiranju}>
      <div className="rocna-mreza__glava">
        <button type="button" className="rocna-mreza__zapri" aria-label="Zapri" onClick={onZapri}>
          ✕
        </button>
        <div className="rocna-mreza__naslovi">
          <span className="rocna-mreza__nad">{telefon ? nadnaslovTelefon : nadnaslov}</span>
          <h2 className="rocna-mreza__naslov">{naslovOkna}</h2>
        </div>
        {!telefon && (
          <div className="rocna-mreza__orodja">
            <span
              className={'rocna-mreza__stanje' + (barva === 'napaka' ? ' rocna-mreza__stanje--napaka' : '')}
              role="status"
            >
              <span className={`rocna-mreza__kvadratek rocna-mreza__kvadratek--${barva}`} aria-hidden="true" />
              {kratko}
            </span>
            <button
              type="button"
              className="rocna-mreza__gumb"
              disabled={predlogZahteva.isPending}
              onClick={() => predlogZahteva.mutate()}
            >
              {predlogZahteva.isPending ? 'Žrebam …' : popravek ? 'Naključno' : 'Nov predlog'}
            </button>
            <button type="button" className="rocna-mreza__gumb" disabled={sprememb === 0} onClick={razveljavi}>
              Razveljavi
            </button>
            <button
              type="button"
              className="rocna-mreza__gumb rocna-mreza__gumb--glavni"
              disabled={neMorem}
              onClick={() => shranjevanje.mutate()}
            >
              {shranjevanje.isPending ? 'Shranjujem …' : popravek ? 'Shrani popravek' : 'Shrani in začni'}
            </button>
          </div>
        )}
      </div>

      {(predlogZahteva.error || shranjevanje.error) && (
        <div className="rocna-mreza__napaka">
          <SporociloNapake napaka={predlogZahteva.error ?? shranjevanje.error} />
        </div>
      )}

      {!mesta ? (
        <p className="obvestilo rocna-mreza__nalaganje">Žrebam predlog …</p>
      ) : telefon ? (
        <>
          {jeCetrtin && (
            <div className="rocna-mreza__zavihki" role="tablist" aria-label="Četrtine">
              {cetrtine.map((c) => (
                <button
                  key={c.q}
                  type="button"
                  role="tab"
                  aria-selected={cetrtina === c.q}
                  className={
                    'rocna-mreza__zavihek'
                    + (cetrtina === c.q ? ' rocna-mreza__zavihek--aktiven' : '')
                    + (c.napaka ? ' rocna-mreza__zavihek--napaka' : '')
                  }
                  onClick={() => nastaviCetrtino(c.q)}
                >
                  <span className="rocna-mreza__zavihek-st">{c.q + 1}.</span>
                  <span className="rocna-mreza__zavihek-meta">{c.meta}</span>
                </button>
              ))}
            </div>
          )}
          <div className="rocna-mreza__telo rocna-mreza__telo--telefon">
            {bloki.map((z) => (
              <div key={z} className="rocna-mreza__blok-telefon">
                {par(z)}
                {par(z + 2)}
                <span className="rocna-mreza__naprej-kratko">
                  2. KOLO → {naprejKratko(z)} – {naprejKratko(z + 2)}
                </span>
              </div>
            ))}
          </div>
          <div className="rocna-mreza__noga">
            <div className="rocna-mreza__noga-stanje" role="status">
              <span className={`rocna-mreza__kvadratek rocna-mreza__kvadratek--${barva}`} aria-hidden="true" />
              <span className={'rocna-mreza__noga-besedilo' + (barva === 'napaka' ? ' rocna-mreza__noga-besedilo--napaka' : '')}>
                {dolgo}
              </span>
              {sprememb > 0 && <span className="rocna-mreza__spremembe">{sprememb} spremenjenih</span>}
            </div>
            <button
              type="button"
              className="rocna-mreza__shrani-telefon"
              disabled={neMorem}
              onClick={() => shranjevanje.mutate()}
            >
              {shranjevanje.isPending ? 'Shranjujem …' : popravek ? 'Shrani popravek' : 'Shrani in začni'}
            </button>
          </div>
          {izbrano !== null && (
            <div className="rocna-mreza__zastor" onClick={obZapiranju}>
              <div
                className="rocna-mreza__list"
                role="dialog"
                aria-label={`Zamenjaj mesto ${izbrano + 1}`}
                onClick={(e) => e.stopPropagation()}
              >
                <div className="rocna-mreza__list-glava">
                  <div className="rocna-mreza__list-naslovi">
                    <span className="rocna-mreza__list-nad">Mesto {izbrano + 1}</span>
                    <span className="rocna-mreza__list-naslov">
                      {izbranoV === null || !igralec(izbranoV)
                        ? 'Prosto mesto'
                        : `Zamenjaj: ${igralec(izbranoV)!.ime}`}
                    </span>
                  </div>
                  <button type="button" className="rocna-mreza__zapri" aria-label="Zapri" onClick={obZapiranju}>
                    ✕
                  </button>
                </div>
                <input
                  className="rocna-mreza__iskanje"
                  value={iskanje}
                  placeholder="Poišči igralca …"
                  aria-label="Poišči igralca"
                  onChange={(e) => nastaviIskanje(e.target.value)}
                />
                <div className="rocna-mreza__zadetki">
                  {zadetki.map((z) => (
                    <button
                      key={z.kljuc}
                      type="button"
                      className="rocna-mreza__zadetek"
                      onClick={() => zamenjaj(izbrano, z.i)}
                    >
                      <span>
                        {z.u ? (
                          <>
                            <b>{z.u.priimek}</b> {z.u.imeLahko && <span className="rocna-mreza__ime-lahko">{z.u.imeLahko}</span>}
                          </>
                        ) : (
                          <b>Prosto mesto</b>
                        )}
                      </span>
                      <span className="rocna-mreza__zadetek-kje">{z.kje}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="rocna-mreza__ploskev">
          <div className="rocna-mreza__levo">
            <div className="rocna-mreza__glava-mreze">
              <span className="rocna-mreza__cetrtina-naslov">
                <span className="rocna-mreza__cetrtina-ime">{naslovCetrtine}</span>
                <span className="rocna-mreza__kolo">1. kolo</span>
              </span>
              <span />
              <span className="rocna-mreza__kolo">2. kolo</span>
              <span className="rocna-mreza__namig">Povleci igralca na drugo mesto in zamenjata se.</span>
            </div>
            <div className="rocna-mreza__telo">
              {bloki.map((z) => {
                const a = naprej(z)
                const b = naprej(z + 2)
                return (
                  <div key={z} className="rocna-mreza__blok">
                    <div className="rocna-mreza__pari">
                      {par(z)}
                      {par(z + 2)}
                    </div>
                    <div className="rocna-mreza__povezava">
                      <span className="rocna-mreza__povezava-crta" />
                    </div>
                    <div className="rocna-mreza__naprej">
                      {[a, b].map((n, k) => (
                        <div key={k} className={'rocna-mreza__naprej-vrstica' + (k === 1 ? ' rocna-mreza__naprej-vrstica--druga' : '')}>
                          <span className={'rocna-mreza__naprej-ime' + (n.pomembno ? ' rocna-mreza__naprej-ime--pomembno' : '')}>
                            {n.ime}
                          </span>
                          <span className="rocna-mreza__naprej-opis">{n.opis}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {jeCetrtin && (
            <div className="rocna-mreza__desno">
              <div className="rocna-mreza__desno-naslov">Četrtine</div>
              <div className="rocna-mreza__cetrtine">
                {cetrtine.map((c) => (
                  <button
                    key={c.q}
                    type="button"
                    className={
                      'rocna-mreza__cetrtina'
                      + (cetrtina === c.q ? ' rocna-mreza__cetrtina--aktivna' : '')
                      + (c.napaka ? ' rocna-mreza__cetrtina--napaka' : '')
                    }
                    aria-pressed={cetrtina === c.q}
                    onClick={() => nastaviCetrtino(c.q)}
                    onDragEnter={() => {
                      if (vlecem && cetrtina !== c.q) nastaviCetrtino(c.q)
                    }}
                    onDragOver={(e) => e.preventDefault()}
                  >
                    <span className="rocna-mreza__cetrtina-vrstica">
                      <span className="rocna-mreza__cetrtina-ime2">{c.q + 1}. četrtina</span>
                      <span className="rocna-mreza__cetrtina-meta">{c.meta}</span>
                    </span>
                    <span className="rocna-mreza__cetrtina-nosilci">{c.nosilci}</span>
                  </button>
                ))}
              </div>
              <div className="rocna-mreza__desno-namig">Med vlečenjem se z miško nad četrtino premakneš vanjo.</div>
              <div
                className={'rocna-mreza__odlagalisce' + (vlecem && vlecem.iz === 'mesto' ? ' rocna-mreza__odlagalisce--cilj' : '')}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault()
                  spusti({ iz: 'odlagalisce' })
                }}
              >
                <span className="rocna-mreza__odlagalisce-naslov">Odlagališče</span>
                <span className="rocna-mreza__odlagalisce-navodilo">
                  {odlagalisce.length
                    ? 'Povleci nazaj na prazno ali zasedeno mesto.'
                    : 'Odloži igralca sem, ko ga prestavljaš daleč.'}
                </span>
                {odlozeni}
              </div>
            </div>
          )}
        </div>
      )}
    </ModalnoOkno>
  )
}
