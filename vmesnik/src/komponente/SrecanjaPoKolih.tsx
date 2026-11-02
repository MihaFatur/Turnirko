/* Srečanja po kolih: pogled v oknu »Termini kol«, v katerem organizator uskladi
   razpored s tem, kar se je dejansko zgodilo ali se bo zgodilo — premakne
   srečanje v drugo kolo.

   Zakaj pogled po kolih in ne iskalnik po srečanjih: iskalnik je pokazal osem
   zadetkov in izpustil že prestavljena srečanja, ekipa z osemnajstimi tekmami
   pa je v njem izgubila polovico. Tu je vsako kolo vidno s svojimi srečanji,
   kot ga vidi gledalec, filter ekipe pa zoži pogled na vsa njena srečanja in
   pokaže, v katerem kolu ne igra.

   Premik je dvokorak (izberi srečanje, klikni kolo), da deluje tudi s prstom;
   na računalniku srečanje lahko tudi povlečeš v kolo. Vrsta oznak kol je
   lepljiva, da je kolo na dosegu, ne glede na to, kako daleč v seznamu stoji
   izbrano srečanje; oznaka pove, ali ekipi v tistem kolu že igrata.

   Prestavljeno srečanje stoji SAMO v kolu igranja (odločitev lastnika: kot ga
   vidi gledalec), z oznako »iz N. kola«. Razlog, lasten dan in ura se urejajo
   pod izbranim srečanjem.

   Pogled ne spreminja poti podatkov: vsak premik gre skozi `onPremakni` in
   `onVrni` okna, ki ju je imelo že pred prenovo; shrani jih »Shrani termine«. */
import { useEffect, useMemo, useState, type DragEvent, type KeyboardEvent } from 'react'

import {
  CAKA,
  NAJVEC_ZNAKOV_RAZLOGA,
  ekipeVnosov,
  igra,
  jePrestavljeno,
  oznakaSrecanja,
  podvojitve,
  prostaKola,
  zasedenostV,
  type VnosKola,
  type VnosSrecanja,
  type Zasedenost,
} from '../pomozno/srecanjaPoKolih'
import { danKratica, kolTekst, kratkoDatum, srecanjTekst } from '../pomozno/terminiKola'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'

interface Lastnosti {
  kola: VnosKola[]
  vnosi: VnosSrecanja[]
  zUrami: boolean
  /* Koliko srečanj ekipa v kolu običajno odigra (eno ali število ur kola). */
  dovoljeno: number
  naslednjeKolo: number | null
  odigranoKolo: (kolo: number) => boolean
  onPremakni: (s: VnosSrecanja, vrednost: string) => void
  onVrni: (s: VnosSrecanja) => void
  onSpremeni: (id: number, popravek: Partial<VnosSrecanja>) => void
}

/* Kam vleče uporabnik: kolo ali seznam čakajočih. */
type Cilj = number | typeof CAKA

export function SrecanjaPoKolih({
  kola,
  vnosi,
  zUrami,
  dovoljeno,
  naslednjeKolo,
  odigranoKolo,
  onPremakni,
  onVrni,
  onSpremeni,
}: Lastnosti) {
  const [izbranoId, nastaviIzbrano] = useState<number | null>(null)
  const [ekipa, nastaviEkipo] = useState<string | null>(null)
  const [samoPrestavljena, nastaviSamoPrestavljena] = useState(false)
  /* Odigrana kola so zgoščena na eno vrstico; to so tista, ki jih je organizator razprl. */
  const [razprta, nastaviRazprta] = useState<Set<number>>(() => new Set())
  const [vlecemId, nastaviVlecem] = useState<number | null>(null)
  const [nadCiljem, nastaviNadCiljem] = useState<Cilj | null>(null)
  /* Srečanje, h kateremu je treba po premiku zdrseti: vrstica zapusti kolo, v katerem je stala. */
  const [skoci, nastaviSkoci] = useState<number | null>(null)

  const izbrano = vnosi.find((s) => s.id === izbranoId) ?? null
  const ekipe = useMemo(() => ekipeVnosov(vnosi), [vnosi])
  const moznostiEkip: MoznostIzbirnika[] = useMemo(
    () =>
      ekipe.map((ime, id) => {
        const n = vnosi.filter((s) => igra(s, ime)).length
        return { id, ime, podrobnost: `${n} ${srecanjTekst(n)}` }
      }),
    [ekipe, vnosi],
  )
  const podvojeno = useMemo(() => podvojitve(vnosi, dovoljeno), [vnosi, dovoljeno])
  const prestavljenih = vnosi.filter(jePrestavljeno).length
  const filtrirano = ekipa !== null || samoPrestavljena

  useEffect(() => {
    if (skoci === null) return
    document.getElementById(idVrstice(skoci))?.scrollIntoView({ block: 'center' })
    nastaviSkoci(null)
  }, [skoci])

  /* Izbrano srečanje ostane na seznamu, tudi ko ga filter sicer ne bi pokazal:
     sicer bi izginilo sredi urejanja (npr. po »Vrni« z vključenim »Prestavljena«). */
  function vidno(s: VnosSrecanja): boolean {
    if (s.id === izbranoId) return true
    if (ekipa !== null && !igra(s, ekipa)) return false
    if (samoPrestavljena && !jePrestavljeno(s)) return false
    return true
  }

  function razvrsti(seznam: VnosSrecanja[]): VnosSrecanja[] {
    return [...seznam].sort((a, b) => a.uraVKolu - b.uraVKolu || a.id - b.id)
  }

  function premakni(s: VnosSrecanja, vrednost: string) {
    onPremakni(s, vrednost)
    nastaviSkoci(s.id)
  }

  function preklopiRazpetje(kolo: number) {
    nastaviRazprta((prej) => {
      const nova = new Set(prej)
      if (nova.has(kolo)) nova.delete(kolo)
      else nova.add(kolo)
      return nova
    })
  }

  function preklopiIzbiro(s: VnosSrecanja) {
    nastaviIzbrano((prej) => (prej === s.id ? null : s.id))
  }

  /* Escape najprej opusti izbiro; okno se zapre šele z naslednjim (isto kot pri iskalniku). */
  function obTipki(dogodek: KeyboardEvent<HTMLDivElement>) {
    if (dogodek.key === 'Escape' && izbranoId !== null) {
      dogodek.preventDefault()
      dogodek.stopPropagation()
      nastaviIzbrano(null)
    }
  }

  function pocisti() {
    nastaviEkipo(null)
    nastaviSamoPrestavljena(false)
  }

  // ---------- povleci in spusti ----------

  function obZacetkuVlecenja(dogodek: DragEvent<HTMLDivElement>, s: VnosSrecanja) {
    dogodek.dataTransfer.setData('text/plain', String(s.id))
    dogodek.dataTransfer.effectAllowed = 'move'
    nastaviVlecem(s.id)
    nastaviIzbrano(s.id)
  }

  function obKoncuVlecenja() {
    nastaviVlecem(null)
    nastaviNadCiljem(null)
  }

  function vlecenjeNad(dogodek: DragEvent<HTMLElement>, cilj: Cilj, smeSem: boolean) {
    if (vlecemId === null || !smeSem) return
    dogodek.preventDefault()
    dogodek.dataTransfer.dropEffect = 'move'
    if (nadCiljem !== cilj) nastaviNadCiljem(cilj)
  }

  function vlecenjeOdSebe(dogodek: DragEvent<HTMLElement>, cilj: Cilj) {
    if (dogodek.currentTarget.contains(dogodek.relatedTarget as Node | null)) return
    nastaviNadCiljem((prej) => (prej === cilj ? null : prej))
  }

  function spusti(dogodek: DragEvent<HTMLElement>, cilj: Cilj) {
    dogodek.preventDefault()
    const s = vnosi.find((x) => x.id === vlecemId)
    obKoncuVlecenja()
    if (!s) return
    if (cilj === CAKA ? s.kolo !== null : s.kolo !== cilj) premakni(s, String(cilj))
  }

  // ---------- izris ----------

  const poKolih = new Map<number, VnosSrecanja[]>()
  for (const s of vnosi) {
    if (s.kolo === null) continue
    const v = poKolih.get(s.kolo)
    if (v) v.push(s)
    else poKolih.set(s.kolo, [s])
  }
  const cakajoca = razvrsti(vnosi.filter((s) => s.kolo === null && vidno(s)))
  const cakajocaSkupaj = vnosi.filter((s) => s.kolo === null).length

  function izrisiVrstico(s: VnosSrecanja) {
    const jeIzbrano = s.id === izbranoId
    const prestavljeno = jePrestavljeno(s)
    const caka = s.kolo === null
    const opombe = podvojeno.get(s.id)
    return (
      <div key={s.id} id={idVrstice(s.id)}
        className={'po-kolih__srecanje'
          + (jeIzbrano ? ' po-kolih__srecanje--izbrano' : '')
          + (s.odigrano ? ' po-kolih__srecanje--odigrano' : '')
          + (vlecemId === s.id ? ' po-kolih__srecanje--vlecem' : '')}>
        <div className="po-kolih__vrstica" role="button" tabIndex={0} draggable
          aria-pressed={jeIzbrano}
          aria-label={`${oznakaSrecanja(s)}${s.odigrano ? ', odigrano' : ''}${prestavljeno && !caka ? `, iz ${s.izvirnoKolo}. kola` : ''}${caka ? ', termin še ni znan' : ''}`}
          onClick={() => preklopiIzbiro(s)}
          onKeyDown={(dogodek) => {
            if (dogodek.key === 'Enter' || dogodek.key === ' ') {
              dogodek.preventDefault()
              preklopiIzbiro(s)
            }
          }}
          onDragStart={(dogodek) => obZacetkuVlecenja(dogodek, s)}
          onDragEnd={obKoncuVlecenja}>
          <span className="po-kolih__ime">{oznakaSrecanja(s)}</span>
          <span className="po-kolih__oznake">
            {s.ura && <span className="po-kolih__ura">{s.ura}</span>}
            {s.odigrano && <span className="po-kolih__oznaka">odigrano</span>}
            {prestavljeno && (
              <span className="po-kolih__oznaka po-kolih__oznaka--prestavljeno">
                iz {s.izvirnoKolo}. kola
              </span>
            )}
          </span>
        </div>

        {opombe && <p className="po-kolih__opomba">{besediloPodvojitve(opombe)}</p>}
        {s.razlog && prestavljeno && !jeIzbrano && <p className="po-kolih__razlog">{s.razlog}</p>}
        {jeIzbrano && prestavljeno && (
          <PodrobnostiPrestavitve s={s}
            dnevKola={kola.find((k) => k.kolo === s.kolo)?.datum ?? ''}
            onSpremeni={(popravek) => onSpremeni(s.id, popravek)}
            onVrni={() => onVrni(s)} />
        )}
      </div>
    )
  }

  return (
    <div className="po-kolih" onKeyDown={obTipki}>
      <p className="termini__uvod">
        Izberi srečanje in klikni kolo, v katerem se bo igralo, ali ga povleci vanj. V razporedu stoji samo tam.
      </p>

      <div className="po-kolih__orodje">
        <IskalniIzbirnik vObrazcu oznaka="Ekipa" namig="Vpiši ime ekipe"
          moznosti={moznostiEkip} brezIskanja={moznostiEkip}
          izbrano={ekipa === null ? null : ekipe.indexOf(ekipa)}
          naIzbiro={(id) => nastaviEkipo(ekipe[id] ?? null)}
          naPraznjenje={() => nastaviEkipo(null)} />
        <button type="button"
          className={'izbirnik__gumb izbirnik__gumb--polje' + (samoPrestavljena ? ' izbirnik__gumb--aktiven' : '')}
          aria-pressed={samoPrestavljena}
          disabled={prestavljenih === 0 && !samoPrestavljena}
          onClick={() => nastaviSamoPrestavljena(!samoPrestavljena)}>
          Prestavljena ({prestavljenih})
        </button>
        {filtrirano && (
          <button type="button" className="gumb gumb--majhen" onClick={pocisti}>Počisti</button>
        )}
      </div>

      {ekipa !== null && <PovzetekEkipe ekipa={ekipa} vnosi={vnosi} kola={kola} />}

      {izbrano ? (
        <div className="po-kolih__izbira">
          <div className="po-kolih__izbira-glava">
            <div className="po-kolih__izbira-besedilo">
              <span className="po-kolih__izbira-ime">{oznakaSrecanja(izbrano)}</span>
              <span className="po-kolih__izbira-pod">
                po žrebu {izbrano.izvirnoKolo}. kolo
                {' · '}
                {izbrano.kolo === null ? 'zdaj brez termina' : `zdaj v ${izbrano.kolo}. kolu`}
              </span>
            </div>
            <button type="button" className="gumb gumb--majhen" onClick={() => nastaviIzbrano(null)}>
              Končano
            </button>
          </div>
          <div className="po-kolih__kola" role="group" aria-label="Kolo, v katerem se bo igralo">
            {kola.map((k) => {
              const tu = izbrano.kolo === k.kolo
              const zreb = izbrano.izvirnoKolo === k.kolo
              const zasedeni = tu || zreb ? [] : zasedenostV(izbrano, k.kolo, vnosi, dovoljeno)
              const stanje = tu ? 'tu' : zreb ? 'zreb' : zasedeni.length > 0 ? 'zasedeno' : 'prosto'
              return (
                <button key={k.kolo} type="button" disabled={tu}
                  className={'po-kolih__kolo-gumb po-kolih__kolo-gumb--' + stanje}
                  aria-current={tu ? 'true' : undefined}
                  title={opisKola(k, stanje, zasedeni)}
                  aria-label={opisKola(k, stanje, zasedeni)}
                  onClick={() => premakni(izbrano, String(k.kolo))}>
                  <span className="po-kolih__kolo-stevilka">{k.kolo}</span>
                  <span className="po-kolih__kolo-datum">{k.datum ? kratkoDatum(k.datum) : '—'}</span>
                  <span className="po-kolih__kolo-stanje">{oznakaStanja(stanje, zasedeni)}</span>
                </button>
              )
            })}
            {(izbrano.smeCakati || izbrano.kolo === null) && (
              <button type="button" disabled={izbrano.kolo === null}
                className={'po-kolih__kolo-gumb po-kolih__kolo-gumb--caka'
                  + (izbrano.kolo === null ? ' po-kolih__kolo-gumb--tu' : '')}
                title="Termin še ni znan" aria-label="Termin še ni znan"
                aria-current={izbrano.kolo === null ? 'true' : undefined}
                onClick={() => premakni(izbrano, CAKA)}>
                <span className="po-kolih__kolo-stevilka">?</span>
                <span className="po-kolih__kolo-datum">brez</span>
                <span className="po-kolih__kolo-stanje">{izbrano.kolo === null ? 'tu' : 'termina'}</span>
              </button>
            )}
          </div>
          <p className="po-kolih__legenda">
            <span className="po-kolih__legenda-prosto">prosto</span> ekipi tam še ne igrata ·{' '}
            <span className="po-kolih__legenda-zasedeno">ime ekipe</span> ta ekipa tam že igra
          </p>
        </div>
      ) : (
        <p className="po-kolih__namig">Ni izbranega srečanja.</p>
      )}

      <div className="po-kolih__seznam">
        {kola.map((k) => {
          const vsa = poKolih.get(k.kolo) ?? []
          const vidna = razvrsti(vsa.filter(vidno))
          if (samoPrestavljena && vidna.length === 0) return null
          const odigrano = odigranoKolo(k.kolo)
          /* Zgoščeno je odigrano kolo, ki ga nihče ne išče: izbrana ekipa, filter in izbrano
             srečanje v njem ga razprejo. */
          const lahkoZgosti = odigrano && !filtrirano && izbrano?.kolo !== k.kolo
          const zgosceno = lahkoZgosti && !razprta.has(k.kolo)
          const oznaka = odigrano ? 'odigrano' : k.kolo === naslednjeKolo ? 'naslednje' : null
          return (
            <section key={k.kolo}
              className={'po-kolih__kolo'
                + (odigrano ? ' po-kolih__kolo--odigrano' : '')
                + (nadCiljem === k.kolo ? ' po-kolih__kolo--cilj' : '')}
              aria-label={`${k.kolo}. kolo`}
              onDragOver={(dogodek) => vlecenjeNad(dogodek, k.kolo, true)}
              onDragLeave={(dogodek) => vlecenjeOdSebe(dogodek, k.kolo)}
              onDrop={(dogodek) => spusti(dogodek, k.kolo)}>
              <div className="po-kolih__glava">
                <span className="po-kolih__stevilka">{k.kolo}. kolo</span>
                <span className="po-kolih__datum">
                  {k.datum ? `${danKratica(k.datum)}, ${kratkoDatum(k.datum)}` : 'brez datuma'}
                  {!zUrami && k.ura ? ` · ${k.ura}` : ''}
                </span>
                {oznaka && (
                  <span className={'termini__oznaka' + (oznaka === 'naslednje' ? ' termini__oznaka--naslednje' : '')}>
                    {oznaka}
                  </span>
                )}
                {lahkoZgosti ? (
                  <button type="button" className="termini__srecanja-gumb po-kolih__razpri"
                    aria-expanded={!zgosceno} onClick={() => preklopiRazpetje(k.kolo)}>
                    {zgosceno ? `${vsa.length} ${srecanjTekst(vsa.length)} ▾` : 'Skrij ▴'}
                  </button>
                ) : (
                  <span className="po-kolih__stevilo">{vsa.length} {srecanjTekst(vsa.length)}</span>
                )}
              </div>
              {!zgosceno && (
                <div className="po-kolih__srecanja">
                  {vidna.map(izrisiVrstico)}
                  {ekipa !== null && !samoPrestavljena && vidna.length === 0 && (
                    <p className="po-kolih__prosto">{ekipa} v tem kolu ne igra.</p>
                  )}
                </div>
              )}
            </section>
          )
        })}

        {(cakajoca.length > 0 || vlecemId !== null) && (
          <section
            className={'po-kolih__kolo po-kolih__kolo--caka' + (nadCiljem === CAKA ? ' po-kolih__kolo--cilj' : '')}
            aria-label="Termin še ni znan"
            onDragOver={(dogodek) => {
              const s = vnosi.find((x) => x.id === vlecemId)
              vlecenjeNad(dogodek, CAKA, !!s && (s.smeCakati || s.kolo === null))
            }}
            onDragLeave={(dogodek) => vlecenjeOdSebe(dogodek, CAKA)}
            onDrop={(dogodek) => spusti(dogodek, CAKA)}>
            <div className="po-kolih__glava">
              <span className="po-kolih__stevilka">?</span>
              <span className="po-kolih__datum">termin še ni znan</span>
              <span className="po-kolih__stevilo">
                {cakajocaSkupaj} {srecanjTekst(cakajocaSkupaj)}
              </span>
            </div>
            <div className="po-kolih__srecanja">
              {cakajoca.length > 0
                ? cakajoca.map(izrisiVrstico)
                : <p className="po-kolih__prosto">Sem povleci srečanje, katerega dan še ni znan.</p>}
            </div>
          </section>
        )}
      </div>
    </div>
  )
}

type StanjeKola = 'tu' | 'zreb' | 'prosto' | 'zasedeno'

/* Zadnja vrstica oznake kola: pri zasedenem kolu pove, KATERA ekipa tam že igra
   (»Celje«, pri obeh »oba«); samo »zasedeno« bi pustilo uganjati, ali je prosta
   ekipa, ki jo organizator premika. */
function oznakaStanja(stanje: StanjeKola, zasedeni: Zasedenost[]): string {
  if (stanje === 'tu') return 'tu'
  if (stanje === 'zreb') return 'žreb'
  if (stanje === 'prosto') return 'prosto'
  return zasedeni.length > 1 ? 'oba' : (zasedeni[0]?.ekipa ?? 'zasedeno')
}

function idVrstice(id: number): string {
  return `po-kolih-srecanje-${id}`
}

/* Poved o ekipi, ki v kolu igra več srečanj, kot jih ima običajno. Imen ne sklanjamo
   (CLAUDE.md), zato nasprotnikov ne omenjamo s predlogom, ampak naštejemo srečanja. */
function besediloPodvojitve(zasedene: Zasedenost[]): string {
  return zasedene
    .map((z) => `${z.ekipa} igra v tem kolu še: ${z.druga.map(oznakaSrecanja).join(', ')}.`)
    .join(' ')
}

function opisKola(k: VnosKola, stanje: StanjeKola, zasedeni: Zasedenost[]): string {
  const kolo = `${k.kolo}. kolo${k.datum ? `, ${danKratica(k.datum)}, ${kratkoDatum(k.datum)}` : ''}`
  if (stanje === 'tu') return `${kolo}: tu se srečanje igra zdaj`
  if (stanje === 'zreb') return `${kolo}: kolo iz žreba (vrne srečanje nazaj)`
  if (stanje === 'prosto') return `${kolo}: ekipi tu še ne igrata`
  return `${kolo}: zasedeno. ${besediloPodvojitve(zasedeni)}`
}

/* Vrstica nad seznamom, ko je izbrana ekipa: koliko srečanj ima, kje je prosta. */
function PovzetekEkipe({ ekipa, vnosi, kola }: { ekipa: string; vnosi: VnosSrecanja[]; kola: VnosKola[] }) {
  const njena = vnosi.filter((s) => igra(s, ekipa))
  const prestavljena = njena.filter(jePrestavljeno).length
  const prosta = prostaKola(vnosi, kola, ekipa)
  return (
    <p className="po-kolih__povzetek">
      {ekipa} · {njena.length} {srecanjTekst(njena.length)}
      {prestavljena > 0 ? ` · prestavljenih ${prestavljena}` : ''}
      {prosta.length > 0 ? ` · brez srečanja: ${prosta.map((k) => `${k}.`).join(', ')} ${kolTekst(prosta.length)}` : ''}
    </p>
  )
}

interface PodrobnostiLastnosti {
  s: VnosSrecanja
  /* Dan kola, v katerem srečanje stoji; sledi mu, dokler organizator ne vpiše svojega. */
  dnevKola: string
  onSpremeni: (popravek: Partial<VnosSrecanja>) => void
  onVrni: () => void
}

/* Razlog, lasten dan in ura prestavljenega srečanja ter vrnitev v kolo iz žreba. */
function PodrobnostiPrestavitve({ s, dnevKola, onSpremeni, onVrni }: PodrobnostiLastnosti) {
  const caka = s.kolo === null
  const dan = s.datum ?? dnevKola
  return (
    <div className="termini__prestavitev po-kolih__podrobnosti">
      <label className="obrazec__polje">
        <span className="termini__razlog-oznaka">
          <span>Razlog (neobvezno)</span>
          <span>{s.razlog.length}/{NAJVEC_ZNAKOV_RAZLOGA}</span>
        </span>
        <input type="text" value={s.razlog} maxLength={NAJVEC_ZNAKOV_RAZLOGA}
          placeholder="npr. dvorana zasedena"
          onChange={(d) => onSpremeni({ razlog: d.target.value })} />
        <span className="termini__razlog-pojasnilo">
          Razlog vidijo vsi obiskovalci. Brez osebnih podatkov (zdravje, naslovi).
        </span>
      </label>

      {!caka && (
        <div className="termini__prestavitev-cas">
          <label className="obrazec__polje">
            <span>Dan</span>
            <input type="date" value={dan}
              onChange={(d) => onSpremeni({ datum: d.target.value })} />
          </label>
          <label className="obrazec__polje">
            <span>Ura</span>
            <input type="time" value={s.ura} disabled={!dan}
              onChange={(d) => onSpremeni({ ura: d.target.value })} />
          </label>
        </div>
      )}

      {!caka && !s.ura && (
        <p className="namig">
          Brez ure se srečanje igra ob nedoločeni uri tistega dne. Če ekipa v tem kolu že igra,
          vpiši uro, da ne bosta imeli dveh srečanj hkrati.
        </p>
      )}

      <div className="termini__urejevalnik-dejanja">
        <button type="button" className="gumb gumb--majhen termini__vrni" onClick={onVrni}>
          Vrni v {s.izvirnoKolo}. kolo
        </button>
      </div>
    </div>
  )
}
