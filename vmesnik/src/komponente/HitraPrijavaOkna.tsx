/* Okni za hitro prijavo več igralcev hkrati (blok »Dodaj igralce« na dogodku).

   - »Prilepi seznam imen«: prijave pridejo po e-pošti, v sporočilu ali v
     Excelu; organizator jih prilepi, vsako ime v svoji vrstici, okno pa jih
     poišče v šifrantu (`pomozno/hitraPrijava`).
   - »Prenesi z drugega dogodka«: pri seriji turnirjev in pri več kategorijah
     istega turnirja nastopajo večinoma isti igralci.

   Obe okni se končata enako: pregled (kdo bo prijavljen, kdo ne in zakaj) in
   en gumb »Prijavi N igralcev«. Pregled je obvezen - okno igralcev ne prijavi
   na slepo, ker bi napačno prepoznan igralec stal v žrebu. Kar zahteva
   odločitev (več igralcev z istim imenom, igralca ni v šifrantu), stoji na
   vrhu pregleda. */
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import { OZNAKE_PASU_KRATKO, type IgralecDto } from '../api/tipi'
import {
  OPISI_OVIRE,
  igralciPrijav,
  poisciVrstice,
  vrsticeSeznama,
  type OviraPrijave,
  type VrsticaIskanja,
} from '../pomozno/hitraPrijava'
import { oblikujDatum, sklonPrijav } from '../pomozno/oblikovanje'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { Namig } from '../pomoc/Namig'

/* Prijava iz okna: ista zahteva kot gumb »Prijavi izbrane«, a svoje stanje,
   da okno ne pobriše izbora na strani. */
export interface PrijavaIzOkna {
  prijavi: (idji: number[]) => void
  poteka: boolean
  napaka: unknown
}

interface SkupneLastnosti {
  igralci: IgralecDto[]
  ovira: (igralec: IgralecDto) => OviraPrijave | null
  prijava: PrijavaIzOkna
  onZapri: () => void
}

/* Druga vrstica igralca - ista kot v seznamu »Dodaj igralce«. */
export function podrobnostIgralca(igralec: IgralecDto): string {
  return (
    (igralec.klub?.ime ?? 'brez kluba')
    + (igralec.starostniPas !== null ? ` · ${OZNAKE_PASU_KRATKO[igralec.starostniPas]}` : '')
    + (igralec.rating !== null ? ` · rating ${igralec.rating}` : '')
  )
}

function polnoIme(igralec: IgralecDto): string {
  return `${igralec.ime} ${igralec.priimek}`
}

/* Isto besedilo kot gumb na strani (»Prijavi izbrane (3)«) - število v
   oklepaju se ne sklanja. */
function gumbPrijave(stevilo: number, poteka: boolean): string {
  return poteka ? 'Prijavljam …' : `Prijavi izbrane (${stevilo})`
}

/* ---------- Prilepi seznam imen ---------- */

export function PrilepiSeznamOkno({ igralci, ovira, prijava, onZapri }: SkupneLastnosti) {
  const [besedilo, nastaviBesedilo] = useState('')
  const [pregled, nastaviPregled] = useState(false)
  /* Izbor pri vrstici z več igralci: indeks vrstice → id igralca. */
  const [izbire, nastaviIzbire] = useState<Map<number, number>>(new Map())
  /* Najdeni so privzeto obkljukani; tu so indeksi vrstic, ki jih je
     organizator odkljukal. */
  const [odkljukane, nastaviOdkljukane] = useState<Set<number>>(new Set())

  /* Kazalec v polju, ko se okno odpre ali ko se organizator vrne na urejanje:
     prvo dejanje je Ctrl+V. Učinek in ne autoFocus - ModalnoOkno sicer v
     razvojnem StrictMode fokus prestavi na prvi gumb (✕). Otrokov učinek teče
     pred oknovim, zato ga okno najde že v sebi. */
  const polje = useRef<HTMLTextAreaElement>(null)
  useEffect(() => {
    if (!pregled) polje.current?.focus()
  }, [pregled])

  const steviloVrstic = vrsticeSeznama(besedilo).length
  const poId = useMemo(() => new Map(igralci.map((igralec) => [igralec.id, igralec])), [igralci])
  const vrstice = useMemo(
    () => (pregled ? poisciVrstice(vrsticeSeznama(besedilo), igralci) : []),
    [pregled, besedilo, igralci],
  )

  type Vrstica = VrsticaIskanja & { indeks: number }
  const zaIzbiro: Vrstica[] = []
  const brez: Vrstica[] = []
  const najdeni: Vrstica[] = []
  const neBodo: Vrstica[] = []
  vrstice.forEach((vrstica, indeks) => {
    const v = { ...vrstica, indeks }
    const { izid } = vrstica
    if (izid.vrsta === 'vec') zaIzbiro.push(v)
    else if (izid.vrsta === 'brez') brez.push(v)
    else if (izid.vrsta === 'ponovljen') neBodo.push(v)
    else {
      const igralec = poId.get(izid.idIgralca)
      if (igralec && ovira(igralec) === null) najdeni.push(v)
      else neBodo.push(v)
    }
  })

  const izbrani = new Set<number>()
  for (const v of najdeni) {
    if (v.izid.vrsta === 'najden' && !odkljukane.has(v.indeks)) izbrani.add(v.izid.idIgralca)
  }
  for (const id of izbire.values()) {
    const igralec = poId.get(id)
    if (igralec && ovira(igralec) === null) izbrani.add(id)
  }

  function preklopiVrstico(indeks: number) {
    nastaviOdkljukane((prej) => {
      const nove = new Set(prej)
      if (nove.has(indeks)) nove.delete(indeks)
      else nove.add(indeks)
      return nove
    })
  }

  function izberi(indeks: number, id: number | null) {
    nastaviIzbire((prej) => {
      const nove = new Map(prej)
      if (id === null) nove.delete(indeks)
      else nove.set(indeks, id)
      return nove
    })
  }

  function nazajNaVnos() {
    /* Indeksi vrstic po urejanju ne veljajo več. */
    nastaviIzbire(new Map())
    nastaviOdkljukane(new Set())
    nastaviPregled(false)
  }

  if (!pregled) {
    return (
      <ModalnoOkno naslov="Prilepi seznam imen" srednje onZapri={onZapri}>
        <div className="modal-pomoc"><Namig clanek="prijave" razdelek="seznam" /></div>
        <p className="modal__podnaslov">
          Vsak igralec v svoji vrstici. Prilepiš lahko stolpec iz Excela ali kar sporočilo s
          prijavami: številke, klub in letnik v vrstici ne motijo.
        </p>
        <label className="obrazec__polje">
          <span>Seznam</span>
          <textarea
            ref={polje}
            className="hitra-prijava__vnos"
            rows={10}
            value={besedilo}
            onChange={(dogodek) => nastaviBesedilo(dogodek.target.value)}
            onKeyDown={(dogodek) => {
              // Ctrl+Enter: poišči, ne da bi segel po miški
              if (dogodek.key === 'Enter' && (dogodek.ctrlKey || dogodek.metaKey) && steviloVrstic > 0) {
                dogodek.preventDefault()
                nastaviPregled(true)
              }
            }}
            placeholder={'Ana Novak\nKrižan Dora\n3. Bor Novak, NTK Žalec'}
          />
        </label>
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={steviloVrstic === 0}
            onClick={() => nastaviPregled(true)}
          >
            {steviloVrstic === 0 ? 'Poišči igralce' : `Poišči igralce (${steviloVrstic})`}
          </button>
        </div>
      </ModalnoOkno>
    )
  }

  return (
    <ModalnoOkno naslov="Prilepi seznam imen" srednje onZapri={onZapri}>
      <p className="hitra-prijava__povzetek">
        {[
          `Najdeni: ${najdeni.length}`,
          zaIzbiro.length > 0 && `za izbiro: ${zaIzbiro.length}`,
          brez.length > 0 && `ni v šifrantu: ${brez.length}`,
          neBodo.length > 0 && `ne bodo prijavljeni: ${neBodo.length}`,
        ]
          .filter(Boolean)
          .join(' · ')}
      </p>

      <div className="hitra-prijava__pregled">
        {zaIzbiro.length > 0 && (
          <Skupina naslov="Izberi pravega igralca" stevilo={zaIzbiro.length}>
            {zaIzbiro.map((v) =>
              v.izid.vrsta !== 'vec' ? null : (
                <li key={v.indeks} className="hitra-prijava__vrstica hitra-prijava__vrstica--izbira">
                  <span className="hitra-prijava__vir">{v.besedilo}</span>
                  <select
                    aria-label={`Igralec za »${v.besedilo}«`}
                    value={izbire.get(v.indeks) ?? ''}
                    onChange={(dogodek) =>
                      izberi(v.indeks, dogodek.target.value === '' ? null : Number(dogodek.target.value))
                    }
                  >
                    <option value="">Nobenega</option>
                    {v.izid.kandidati.map((id) => {
                      const igralec = poId.get(id)
                      if (!igralec) return null
                      const zakaj = ovira(igralec)
                      return (
                        <option key={id} value={id} disabled={zakaj !== null}>
                          {polnoIme(igralec)} · {podrobnostIgralca(igralec)}
                          {zakaj !== null ? ` · ${OPISI_OVIRE[zakaj]}` : ''}
                        </option>
                      )
                    })}
                  </select>
                  {v.izid.skupaj > v.izid.kandidati.length && (
                    <span className="hitra-prijava__opomba">
                      Prikazanih {v.izid.kandidati.length} od {v.izid.skupaj} — dopiši ime.
                    </span>
                  )}
                </li>
              ),
            )}
          </Skupina>
        )}

        {brez.length > 0 && (
          <Skupina naslov="Ni v šifrantu" stevilo={brez.length}>
            {brez.map((v) => (
              <li key={v.indeks} className="hitra-prijava__vrstica">
                <span className="hitra-prijava__vir">{v.besedilo}</span>
              </li>
            ))}
            <li className="hitra-prijava__opomba">
              Preveri zapis imena ali igralca najprej dodaj na strani Igralci.
            </li>
          </Skupina>
        )}

        {najdeni.length > 0 && (
          <Skupina naslov="Najdeni" stevilo={najdeni.length}>
            {najdeni.map((v) => {
              if (v.izid.vrsta !== 'najden') return null
              const igralec = poId.get(v.izid.idIgralca)!
              return (
                <li key={v.indeks}>
                  <label className="hitra-prijava__vrstica hitra-prijava__vrstica--kljukica">
                    <input
                      type="checkbox"
                      checked={!odkljukane.has(v.indeks)}
                      onChange={() => preklopiVrstico(v.indeks)}
                    />
                    <span>
                      {polnoIme(igralec)}
                      <span className="seznam-izbire__podrobnost">{podrobnostIgralca(igralec)}</span>
                      {v.izid.priblizno && (
                        <span className="hitra-prijava__priblizno">
                          Približno · iz »{v.besedilo}«
                        </span>
                      )}
                    </span>
                  </label>
                </li>
              )
            })}
          </Skupina>
        )}

        {neBodo.length > 0 && (
          <Skupina naslov="Ne bodo prijavljeni" stevilo={neBodo.length}>
            {neBodo.map((v) => {
              const id = v.izid.vrsta === 'najden' || v.izid.vrsta === 'ponovljen' ? v.izid.idIgralca : null
              const igralec = id !== null ? poId.get(id) : undefined
              const zakaj =
                v.izid.vrsta === 'ponovljen'
                  ? 'že višje v seznamu'
                  : igralec
                    ? OPISI_OVIRE[ovira(igralec) ?? 'prijavljen']
                    : ''
              return (
                <li key={v.indeks} className="hitra-prijava__vrstica">
                  <span>
                    {igralec ? polnoIme(igralec) : v.besedilo}
                    <span className="seznam-izbire__podrobnost">{zakaj}</span>
                  </span>
                </li>
              )
            })}
          </Skupina>
        )}
      </div>

      <SporociloNapake napaka={prijava.napaka} />

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={nazajNaVnos}>
          Uredi seznam
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={izbrani.size === 0 || prijava.poteka}
          onClick={() => prijava.prijavi([...izbrani])}
        >
          {gumbPrijave(izbrani.size, prijava.poteka)}
        </button>
      </div>
    </ModalnoOkno>
  )
}

/* ---------- Prenesi z drugega dogodka ---------- */

/* Koliko turnirjev ponudi prazno polje: ta turnir in zadnji turnirji s
   prijavami - iz njih se prenaša najpogosteje. */
const PREDLOGOV_TURNIRJEV = 8

export function PrenosPrijavOkno({
  idDogodka,
  idTurnir,
  igralci,
  ovira,
  prijava,
  onZapri,
}: SkupneLastnosti & { idDogodka: number; idTurnir: number }) {
  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
  const [idTurnirja, nastaviIdTurnirja] = useState<number | null>(idTurnir)
  const [izbraniDogodek, nastaviIzbraniDogodek] = useState<number | null>(null)

  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja!),
    enabled: idTurnirja !== null,
  })

  /* Ekipni dogodek nima igralcev (prijava je ekipa), zato ga ni med viri. */
  const viri = (dogodki.data ?? []).filter(
    (dogodek) => dogodek.id !== idDogodka && dogodek.disciplina !== 'EKIPNO',
  )
  const idVira =
    izbraniDogodek !== null && viri.some((dogodek) => dogodek.id === izbraniDogodek)
      ? izbraniDogodek
      : (viri.find((dogodek) => dogodek.steviloPrijav > 0)?.id ?? null)
  const vir = viri.find((dogodek) => dogodek.id === idVira)

  const mreza = useQuery({
    queryKey: ['dogodek', idVira],
    queryFn: () => dogodkiApi.mreza(idVira!),
    enabled: idVira !== null,
  })

  const moznosti = useMemo<MoznostIzbirnika[]>(() => {
    const vsi = [...(turnirji.data ?? [])].sort((a, b) =>
      (b.datumZacetka ?? '').localeCompare(a.datumZacetka ?? ''),
    )
    return vsi.map((turnir) => ({
      id: turnir.id,
      ime: turnir.id === idTurnir ? `${turnir.ime} (ta turnir)` : turnir.ime,
      podrobnost:
        [oblikujDatum(turnir.datumZacetka), turnir.kraj?.ime].filter(Boolean).join(' · ') || null,
      iskalno: `${turnir.ime} ${turnir.kraj?.ime ?? ''} ${turnir.datumZacetka?.slice(0, 4) ?? ''}`,
    }))
  }, [turnirji.data, idTurnir])

  const predlogi = useMemo(() => {
    const ta = moznosti.find((moznost) => moznost.id === idTurnir)
    const sPrijavami = new Set(
      (turnirji.data ?? [])
        .filter((turnir) => turnir.prijavljenihSkupaj > 0 && turnir.id !== idTurnir)
        .map((turnir) => turnir.id),
    )
    const zadnji = moznosti.filter((moznost) => sPrijavami.has(moznost.id))
    return [...(ta ? [ta] : []), ...zadnji].slice(0, PREDLOGOV_TURNIRJEV)
  }, [moznosti, turnirji.data, idTurnir])

  return (
    <ModalnoOkno naslov="Prenesi z drugega dogodka" srednje onZapri={onZapri}>
      <div className="modal-pomoc"><Namig clanek="prijave" razdelek="prenos" /></div>
      <p className="modal__podnaslov">
        Izberi dogodek, s katerega prevzameš igralce. Pregledaš jih, preden jih prijaviš.
      </p>

      <IskalniIzbirnik
        oznaka="Turnir"
        vObrazcu
        namig="Vpiši ime turnirja"
        moznosti={moznosti}
        brezIskanja={predlogi}
        izbrano={idTurnirja}
        naIzbiro={(id) => {
          nastaviIdTurnirja(id)
          nastaviIzbraniDogodek(null)
        }}
        naPraznjenje={() => {
          nastaviIdTurnirja(null)
          nastaviIzbraniDogodek(null)
        }}
      />

      {idTurnirja !== null && dogodki.isPending && <p className="obvestilo">Nalaganje …</p>}
      {dogodki.data && viri.length === 0 && (
        <p className="obvestilo">
          {idTurnirja === idTurnir
            ? 'Ta turnir nima drugega dogodka s posamičnimi prijavami. Izberi drug turnir.'
            : 'Ta turnir nima dogodka s posamičnimi prijavami.'}
        </p>
      )}

      {viri.length > 0 && (
        <label className="obrazec__polje">
          <span>Dogodek</span>
          <select
            value={idVira ?? ''}
            onChange={(dogodek) => nastaviIzbraniDogodek(Number(dogodek.target.value))}
          >
            {idVira === null && <option value="">Izberi dogodek</option>}
            {viri.map((dogodek) => (
              <option key={dogodek.id} value={dogodek.id} disabled={dogodek.steviloPrijav === 0}>
                {dogodek.ime} ·{' '}
                {dogodek.steviloPrijav === 0
                  ? 'brez prijav'
                  : `${dogodek.steviloPrijav} ${sklonPrijav(dogodek.steviloPrijav)}`}
              </option>
            ))}
          </select>
        </label>
      )}

      {idVira !== null && mreza.isPending && <p className="obvestilo">Nalaganje prijav …</p>}
      {mreza.data && idVira !== null && (
        <IgralciVira
          /* Nov vir = nov izbor (vsi spet obkljukani). */
          key={idVira}
          idjiIgralcev={igralciPrijav(mreza.data.prijave)}
          dvojice={vir?.disciplina === 'DVOJICE'}
          igralci={igralci}
          ovira={ovira}
          prijava={prijava}
          onZapri={onZapri}
        />
      )}

      {(idVira === null || !mreza.data) && (
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
        </div>
      )}
    </ModalnoOkno>
  )
}

function IgralciVira({
  idjiIgralcev,
  dvojice,
  igralci,
  ovira,
  prijava,
  onZapri,
}: SkupneLastnosti & { idjiIgralcev: number[]; dvojice: boolean }) {
  const [odkljukani, nastaviOdkljukane] = useState<Set<number>>(new Set())

  const { mozni, neBodo } = useMemo(() => {
    const poId = new Map(igralci.map((igralec) => [igralec.id, igralec]))
    const vsi = idjiIgralcev
      .map((id) => poId.get(id))
      .filter((igralec): igralec is IgralecDto => igralec !== undefined)
      .sort(
        (a, b) => a.priimek.localeCompare(b.priimek, 'sl') || a.ime.localeCompare(b.ime, 'sl'),
      )
    return {
      mozni: vsi.filter((igralec) => ovira(igralec) === null),
      neBodo: vsi.filter((igralec) => ovira(igralec) !== null),
    }
  }, [idjiIgralcev, igralci, ovira])

  const izbrani = mozni.filter((igralec) => !odkljukani.has(igralec.id)).map((igralec) => igralec.id)
  const vsiIzbrani = izbrani.length === mozni.length

  function preklopi(id: number) {
    nastaviOdkljukane((prej) => {
      const novi = new Set(prej)
      if (novi.has(id)) novi.delete(id)
      else novi.add(id)
      return novi
    })
  }

  return (
    <>
      {dvojice && (
        <p className="hitra-prijava__opomba">
          Prenesejo se igralci, ne pari - pare sestaviš znova.
        </p>
      )}

      <div className="hitra-prijava__pregled">
        {mozni.length > 0 && (
          <Skupina
            naslov="Bodo prijavljeni"
            stevilo={izbrani.length}
            dejanje={
              <button
                type="button"
                className="povezava-gumb"
                onClick={() =>
                  nastaviOdkljukane(vsiIzbrani ? new Set(mozni.map((igralec) => igralec.id)) : new Set())
                }
              >
                {vsiIzbrani ? 'Odkljukaj vse' : 'Obkljukaj vse'}
              </button>
            }
          >
            {mozni.map((igralec) => (
              <li key={igralec.id}>
                <label className="hitra-prijava__vrstica hitra-prijava__vrstica--kljukica">
                  <input
                    type="checkbox"
                    checked={!odkljukani.has(igralec.id)}
                    onChange={() => preklopi(igralec.id)}
                  />
                  <span>
                    {polnoIme(igralec)}
                    <span className="seznam-izbire__podrobnost">{podrobnostIgralca(igralec)}</span>
                  </span>
                </label>
              </li>
            ))}
          </Skupina>
        )}

        {neBodo.length > 0 && (
          <Skupina naslov="Ne bodo prijavljeni" stevilo={neBodo.length}>
            {neBodo.map((igralec) => (
              <li key={igralec.id} className="hitra-prijava__vrstica">
                <span>
                  {polnoIme(igralec)}
                  <span className="seznam-izbire__podrobnost">
                    {OPISI_OVIRE[ovira(igralec) ?? 'prijavljen']}
                  </span>
                </span>
              </li>
            ))}
          </Skupina>
        )}

        {mozni.length === 0 && neBodo.length === 0 && (
          <p className="obvestilo">Ta dogodek nima prijavljenih igralcev.</p>
        )}
      </div>

      <SporociloNapake napaka={prijava.napaka} />

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={izbrani.length === 0 || prijava.poteka}
          onClick={() => prijava.prijavi(izbrani)}
        >
          {gumbPrijave(izbrani.length, prijava.poteka)}
        </button>
      </div>
    </>
  )
}

function Skupina({
  naslov,
  stevilo,
  dejanje,
  children,
}: {
  naslov: string
  stevilo: number
  dejanje?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="hitra-prijava__skupina">
      <div className="hitra-prijava__glava">
        <h3 className="podnaslov-sekcije">
          {naslov} · {stevilo}
        </h3>
        {dejanje}
      </div>
      <ul className="hitra-prijava__seznam">{children}</ul>
    </section>
  )
}
