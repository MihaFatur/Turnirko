/* Dostopi (administrator): nabiralnik čakajočih zahtev in kartoteka vseh računov
   (predaja design_handoff_dostopi, različica 1b »Nabiralnik + kartoteka«).

   Stran ima dva dela z dvema opraviloma. Zgoraj nabiralnik: število čakajočih
   zahtev je naslov strani, zahteve se obdelajo ena za drugo, izbrana je odprta
   kot karton na desni. Spodaj kartoteka: vsi računi po abecedi, abecedno
   kazalo, iskanje, filtri; ob seznamu stalen karton izbranega računa, brez
   izbire pa »Stanje dostopov«. Vsa dejanja so v kartonu (DostopiKarton), nič
   ni v vrstici seznama. Na telefonu sta ista dva dela, karton je spodnji list
   čez stran, abecedno kazalo pa navpičen trak ob desnem robu.

   Admina čakajo samo računi s potrjenim naslovom (in skrbnikom, če je
   potreben); registracije brez kode se same izbrišejo in so v kartoteki le za
   pregled. Računi, ki so se povezali samodejno, so označeni — admin jih lahko
   razveže, če je ujemanje zgrešilo soimenjaka. Naročnina je vidna pri vsakem
   računu (tudi pred potrditvijo: plačan paket se zabeleži še pred njo). Vidi jo
   samo administrator. Iz kartoteke se odpre še okno »Kode za popust« (ugodnosti, ki jih
   oseba vpiše ob registraciji). */
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'

import { racuniApi } from '../api/zahteve'
import type { RacunIgralcaDto } from '../api/tipi'
import { DostopiKarton } from '../komponente/DostopiKarton'
import { DruzineNapis, DruzineOkno } from '../komponente/DruzineOkno'
import { KodePopustaOkno } from '../komponente/KodePopustaOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import {
  CRKE_KAZALA,
  type FilterRacunov,
  type KljucStanja,
  MOZNOSTI_PAKETA,
  MOZNOSTI_RAZVRSTITVE,
  MOZNOSTI_STANJA,
  MOZNOSTI_VLOGE,
  PRAZNI_FILTRI,
  type Razvrstitev,
  type SkupinaRacunov,
  besedeIskanja,
  imeNarocnine,
  imeRacuna,
  jeCakajoca,
  narocninaZahtevaPozornost,
  oznakaVloge,
  pregledStanja,
  primerjalnik,
  rel,
  skupinePoCrki,
  smerRazvrstitve,
  stanjeNarocnine,
  stanjeRacuna,
  steviloAktivnihFiltrov,
  ujemaFiltrom,
} from '../pomozno/dostopi'
import { useTelefon } from '../pomozno/telefon'

/* Višina lepljive glave telefona (56 px vrstica + 3 px črta): pod njo obstane
   glava skupine, do nje se pomakne skok na črko. */
const GLAVA_TELEFONA = 59

export function RacuniStran() {
  const racuni = useQuery({ queryKey: ['racuni'], queryFn: racuniApi.seznam })

  if (racuni.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (racuni.error) return <SporociloNapake napaka={racuni.error} />

  return <Dostopi vsi={racuni.data ?? []} />
}

function Dostopi({ vsi }: { vsi: RacunIgralcaDto[] }) {
  const telefon = useTelefon()
  const [kodeOdprte, nastaviKodeOdprte] = useState(false)
  const [druzineOdprte, nastaviDruzineOdprte] = useState(false)

  const cakajoci = useMemo(() => vsi.filter(jeCakajoca), [vsi])
  const igralciZRacunom = useMemo(
    () => new Set(vsi.flatMap((r) => (r.idIgralec !== null ? [r.idIgralec] : []))),
    [vsi],
  )

  return (
    <section className={'dostopi' + (telefon ? ' dostopi--telefon' : '')}>
      {telefon ? (
        <NabiralnikTelefon cakajoci={cakajoci} igralciZRacunom={igralciZRacunom} />
      ) : (
        <NabiralnikNamizje cakajoci={cakajoci} igralciZRacunom={igralciZRacunom} />
      )}
      <Kartoteka
        vsi={vsi}
        telefon={telefon}
        igralciZRacunom={igralciZRacunom}
        onKode={() => nastaviKodeOdprte(true)}
        onDruzine={() => nastaviDruzineOdprte(true)}
      />
      {kodeOdprte && <KodePopustaOkno onZapri={() => nastaviKodeOdprte(false)} />}
      {druzineOdprte && <DruzineOkno onZapri={() => nastaviDruzineOdprte(false)} />}
    </section>
  )
}

/* Račun karton izriše znova, kadar se spremeni njegovo stanje (potrditev,
   razvezava): predizbira in lokalno stanje kartona veljata za stanje, v
   katerem je bil karton odprt. */
function kljucKartona(r: RacunIgralcaDto): string {
  return `${r.id}-${r.status}-${r.idIgralec ?? 0}`
}

/* ============================================================
   Nabiralnik
   ============================================================ */

/* Izbrana zahteva: tista, ki jo je admin izbral, sicer prva. Ko izbrana
   izgine (potrjena, zavrnjena), se samodejno izbere prva preostala. */
function useIzbiraZahteve(cakajoci: RacunIgralcaDto[]) {
  const [izbranId, nastaviIzbranId] = useState<number | null>(null)
  const [obvestilo, nastaviObvestilo] = useState<string | null>(null)
  const izbrana = cakajoci.find((r) => r.id === izbranId) ?? cakajoci[0] ?? null
  const izberi = (id: number) => {
    nastaviIzbranId(id)
    nastaviObvestilo(null)
  }
  return { izbrana, izbranId, izberi, obvestilo, nastaviObvestilo }
}

function NabiralnikNamizje({
  cakajoci,
  igralciZRacunom,
}: {
  cakajoci: RacunIgralcaDto[]
  igralciZRacunom: Set<number>
}) {
  const { izbrana, izberi, obvestilo, nastaviObvestilo } = useIzbiraZahteve(cakajoci)
  const n = cakajoci.length
  const indeks = izbrana ? cakajoci.indexOf(izbrana) : 0
  /* ← in → obhodita seznam (z obratom). */
  const naIndeks = (i: number) => izberi(cakajoci[(i + n) % n].id)

  return (
    <div className="dostopi-nabiralnik" data-dostopi="nabiralnik">
      <div className="dostopi-nabiralnik__levo">
        <h1 className="dostopi-nabiralnik__naslov">
          <span className={'dostopi-nabiralnik__stevilka' + (n > 0 ? ' dostopi-nabiralnik__stevilka--caka' : '')}>
            {n}
          </span>
          <span className="dostopi-nabiralnik__oznaka">
            Čaka na
            <br />
            potrditev
          </span>
        </h1>
        {n === 0 && <div className="dostopi-nabiralnik__prazno">Ni novih zahtev.</div>}
        <div className="dostopi-nabiralnik__seznam">
          {cakajoci.map((r, j) => (
            <button
              key={r.id}
              type="button"
              data-dostopi="nabiralnik-vrstica"
              className={'dostopi-zahteva' + (r.id === izbrana?.id ? ' dostopi-zahteva--izbrana' : '')}
              aria-pressed={r.id === izbrana?.id}
              onClick={() => izberi(r.id)}
            >
              <span className="dostopi-zahteva__zap">{String(j + 1).padStart(2, '0')}</span>
              <span className="dostopi-zahteva__besedilo">
                <span className="dostopi-zahteva__ime">{imeRacuna(r)}</span>
                <span className="dostopi-zahteva__pod">
                  {oznakaVloge(r.vloga)} · reg. {rel(r.ustvarjenOb)}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>

      <div className="dostopi-nabiralnik__desno">
        {izbrana ? (
          <>
            <div className="dostopi-pager">
              <span className="dostopi-pager__oznaka">
                Zahteva {indeks + 1} od {n}
              </span>
              <div className="dostopi-pager__gumbi">
                <button type="button" className="dostopi-pager__gumb" aria-label="Prejšnja zahteva" onClick={() => naIndeks(indeks - 1)}>
                  ←
                </button>
                <button type="button" className="dostopi-pager__gumb" aria-label="Naslednja zahteva" onClick={() => naIndeks(indeks + 1)}>
                  →
                </button>
              </div>
            </div>
            <DostopiKarton
              key={kljucKartona(izbrana)}
              racun={izbrana}
              obvestilo={obvestilo}
              naObvestilo={nastaviObvestilo}
              igralciZRacunom={igralciZRacunom}
            />
          </>
        ) : (
          <p className="dostopi-nabiralnik__prazno-opis">
            Nove registracije se pojavijo tukaj, ko lastnik potrdi e-pošto.
          </p>
        )}
      </div>
    </div>
  )
}

function NabiralnikTelefon({
  cakajoci,
  igralciZRacunom,
}: {
  cakajoci: RacunIgralcaDto[]
  igralciZRacunom: Set<number>
}) {
  const { izbrana, izberi, obvestilo, nastaviObvestilo } = useIzbiraZahteve(cakajoci)
  const [listOdprt, nastaviListOdprt] = useState(false)
  const n = cakajoci.length
  const indeks = izbrana ? cakajoci.indexOf(izbrana) : 0

  return (
    <div className="dostopi-nabiralnik" data-dostopi="nabiralnik">
      <h1 className="dostopi-nabiralnik__naslov">
        <span className={'dostopi-nabiralnik__stevilka' + (n > 0 ? ' dostopi-nabiralnik__stevilka--caka' : '')}>
          {n}
        </span>
        <span className="dostopi-nabiralnik__oznaka">Čaka na potrditev</span>
      </h1>
      {n === 0 && <div className="dostopi-nabiralnik__prazno">Ni novih zahtev.</div>}
      <div className="dostopi-nabiralnik__seznam">
        {cakajoci.map((r, j) => (
          <button
            key={r.id}
            type="button"
            data-dostopi="nabiralnik-vrstica"
            className="dostopi-zahteva"
            onClick={() => {
              izberi(r.id)
              nastaviListOdprt(true)
            }}
          >
            <span className="dostopi-zahteva__zap">{String(j + 1).padStart(2, '0')}</span>
            <span className="dostopi-zahteva__besedilo">
              <span className="dostopi-zahteva__ime">{imeRacuna(r)}</span>
              <span className="dostopi-zahteva__pod">
                {oznakaVloge(r.vloga)} · {rel(r.ustvarjenOb)}
              </span>
            </span>
            <span className="dostopi-zahteva__odpri">Odpri →</span>
          </button>
        ))}
      </div>

      {listOdprt && izbrana && (
        <DostopiList
          nujno
          oznaka={`Zahteva ${indeks + 1} od ${n}`}
          onZapri={() => nastaviListOdprt(false)}
        >
          <DostopiKarton
            key={kljucKartona(izbrana)}
            racun={izbrana}
            obvestilo={obvestilo}
            naObvestilo={nastaviObvestilo}
            igralciZRacunom={igralciZRacunom}
          />
        </DostopiList>
      )}
    </div>
  )
}

/* ============================================================
   Kartoteka
   ============================================================ */

function Kartoteka({
  vsi,
  telefon,
  igralciZRacunom,
  onKode,
  onDruzine,
}: {
  vsi: RacunIgralcaDto[]
  telefon: boolean
  igralciZRacunom: Set<number>
  /* Odpre okno »Kode za popust« (ugodnosti ob registraciji). */
  onKode: () => void
  /* Odpre okno »Družine« (zahteve navijačev za povezavo z igralcem do 21 let, V59). */
  onDruzine: () => void
}) {
  const [filtri, nastaviFiltre] = useState<FilterRacunov>(PRAZNI_FILTRI)
  const [razvrstitev, nastaviRazvrstitev] = useState<Razvrstitev>('ime')
  const [izbranId, nastaviIzbranId] = useState<number | null>(null)
  const [obvestilo, nastaviObvestilo] = useState<string | null>(null)
  const [filtriOdprti, nastaviFiltreOdprte] = useState(false)

  const besede = useMemo(() => besedeIskanja(filtri.iskanje), [filtri.iskanje])

  /* Števila na žetonih so skupna, ne glede na filtre. */
  const stevilaVlog = useMemo(() => {
    const s: Record<string, number> = { vsi: vsi.length, IGRALEC: 0, NAVIJAC: 0, ORGANIZATOR: 0, ADMIN: 0 }
    for (const r of vsi) s[r.vloga]++
    return s
  }, [vsi])
  const stevilaStanj = useMemo(() => {
    const s: Record<string, number> = { vsi: vsi.length }
    for (const r of vsi) {
      const k = stanjeRacuna(r).kljuc
      s[k] = (s[k] ?? 0) + 1
    }
    return s
  }, [vsi])

  const prikazani = useMemo(() => {
    const cmp = primerjalnik(razvrstitev)
    const smer = smerRazvrstitve(razvrstitev)
    return vsi.filter((r) => ujemaFiltrom(r, filtri, besede)).sort((a, b) => cmp(a, b) * smer)
  }, [vsi, filtri, besede, razvrstitev])

  const poCrkah = razvrstitev === 'ime'
  const skupine: SkupinaRacunov[] = useMemo(
    () => (poCrkah ? skupinePoCrki(prikazani) : [{ crka: '', racuni: prikazani }]),
    [poCrkah, prikazani],
  )
  const izbran = vsi.find((r) => r.id === izbranId) ?? null

  const spremeniFiltre = (spremembe: Partial<FilterRacunov>) => nastaviFiltre((f) => ({ ...f, ...spremembe }))
  const pocistiFiltre = () => nastaviFiltre(PRAZNI_FILTRI)
  /* Klik na izbrano vrstico karton zapre. */
  const preklopiRacun = (id: number) => {
    nastaviIzbranId((trenutni) => (trenutni === id && !telefon ? null : id))
    nastaviObvestilo(null)
  }
  const zapriKarton = () => nastaviIzbranId(null)
  const obRazvrstitvi = (r: Razvrstitev) => nastaviRazvrstitev(r)

  /* Skok na črko: glava skupine pride na vrh seznama (namizje) oz. pod
     lepljivo glavo telefona. */
  const seznamRef = useRef<HTMLDivElement>(null)
  const skociNaCrko = (crka: string) => {
    const glava = document.getElementById(idSkupine(crka))
    if (!glava) return
    if (telefon) {
      window.scrollTo({ top: window.scrollY + glava.getBoundingClientRect().top - GLAVA_TELEFONA })
      return
    }
    const seznam = seznamRef.current
    if (!seznam) return
    seznam.scrollTop += glava.getBoundingClientRect().top - seznam.getBoundingClientRect().top
  }
  const crkeZRacuni = useMemo(() => new Set(skupine.map((s) => s.crka)), [skupine])

  const ujemanje = prikazani.length

  const krmila = {
    filtri,
    spremeniFiltre,
    razvrstitev,
    obRazvrstitvi,
    stevilaVlog,
    stevilaStanj,
  }

  const seznamVrstic = (vrstica: (r: RacunIgralcaDto) => ReactNode) => (
    <>
      {skupine.map((s) => (
        <div key={s.crka || 'vse'} id={poCrkah ? idSkupine(s.crka) : undefined}>
          {poCrkah && (
            <div className="dostopi-skupina-glava">
              <span className="dostopi-skupina-glava__crka">{s.crka}</span>
              <span className="dostopi-skupina-glava__stevilo">{s.racuni.length}</span>
            </div>
          )}
          {s.racuni.map(vrstica)}
        </div>
      ))}
      {ujemanje === 0 && (
        <div className="dostopi-prazno">
          Ni zadetkov.
          <button type="button" className="dostopi-gumb dostopi-gumb--okvir dostopi-gumb--manjsi" onClick={pocistiFiltre}>
            Počisti filtre
          </button>
        </div>
      )}
    </>
  )

  if (telefon) {
    const nFiltrov = steviloAktivnihFiltrov(filtri)
    return (
      <div className="dostopi-kartoteka" data-dostopi="kartoteka-seznam">
        <div className="dostopi-kartoteka__naslov">
          <h2>Kartoteka</h2>
          <span className="dostopi-kartoteka__naslov-desno">
            <span className="dostopi-meta">
              {ujemanje} od {vsi.length}
            </span>
            <button type="button" className="dostopi-gumb dostopi-gumb--okvir" onClick={onKode}>
              Kode za popust
            </button>
            <button type="button" className="dostopi-gumb dostopi-gumb--okvir" onClick={onDruzine}>
              <DruzineNapis />
            </button>
          </span>
        </div>
        <div className="dostopi-kartoteka__iskanje-vrstica">
          <input
            type="text"
            data-dostopi="iskanje"
            className="dostopi-iskanje"
            value={filtri.iskanje}
            placeholder="Išči po imenu ali e-pošti"
            aria-label="Išči po imenu ali e-pošti"
            autoComplete="off"
            onChange={(d) => spremeniFiltre({ iskanje: d.target.value })}
          />
          <button
            type="button"
            className={'dostopi-filtri-gumb' + (nFiltrov > 0 ? ' dostopi-filtri-gumb--vklopljen' : '')}
            aria-expanded={filtriOdprti}
            onClick={() => nastaviFiltreOdprte((v) => !v)}
          >
            Filtri {nFiltrov > 0 ? `· ${nFiltrov}` : ''}
          </button>
        </div>
        {filtriOdprti && (
          <div className="dostopi-filtri-plosca">
            <div className="dostopi-filtri-plosca__oznaka">Vloga</div>
            <div className="dostopi-filtri-plosca__zetoni">
              <ZetoniVloge {...krmila} />
            </div>
            <div className="dostopi-filtri-plosca__oznaka">Stanje</div>
            <div className="dostopi-filtri-plosca__zetoni">
              <ZetoniStanja {...krmila} />
            </div>
            <div className="dostopi-filtri-plosca__vrsta">
              <SelectPaketa {...krmila} />
              <SelectRazvrstitve {...krmila} />
            </div>
            <div className="dostopi-filtri-plosca__vrsta">
              <StikaloSamodejno {...krmila} stisnjeno />
              <button type="button" className="dostopi-pocisti" onClick={pocistiFiltre}>
                Počisti
              </button>
            </div>
          </div>
        )}

        <div className="dostopi-seznam dostopi-seznam--telefon">
          {seznamVrstic((r) => (
            <VrsticaTelefon key={r.id} racun={r} izbrana={r.id === izbranId} onKlik={() => preklopiRacun(r.id)} />
          ))}
        </div>

        {poCrkah && <KazaloTelefon crkeZRacuni={crkeZRacuni} onCrka={skociNaCrko} />}

        {izbran && (
          <DostopiList oznaka={null} onZapri={zapriKarton}>
            <DostopiKarton
              key={kljucKartona(izbran)}
              racun={izbran}
              obvestilo={obvestilo}
              naObvestilo={nastaviObvestilo}
              igralciZRacunom={igralciZRacunom}
            />
          </DostopiList>
        )}
      </div>
    )
  }

  return (
    <div className="dostopi-kartoteka">
      <div className="dostopi-kartoteka__naslov">
        <h2>Kartoteka</h2>
        <span className="dostopi-kartoteka__naslov-desno">
          <span className="dostopi-meta">
            {ujemanje} od {vsi.length}
          </span>
          <button type="button" className="dostopi-gumb dostopi-gumb--okvir" onClick={onKode}>
            Kode za popust
          </button>
          <button type="button" className="dostopi-gumb dostopi-gumb--okvir" onClick={onDruzine}>
            <DruzineNapis />
          </button>
        </span>
      </div>

      <div className="dostopi-kartoteka__krmila">
        <div className="dostopi-kartoteka__vrsta">
          <input
            type="text"
            data-dostopi="iskanje"
            className="dostopi-iskanje dostopi-iskanje--namizje"
            value={filtri.iskanje}
            placeholder="Išči po imenu, e-pošti ali klubu"
            aria-label="Išči po imenu, e-pošti ali klubu"
            autoComplete="off"
            onChange={(d) => spremeniFiltre({ iskanje: d.target.value })}
          />
          <SelectPaketa {...krmila} />
          <SelectRazvrstitve {...krmila} />
          <StikaloSamodejno {...krmila} />
        </div>
        <div className="dostopi-kartoteka__zetoni">
          <ZetoniVloge {...krmila} />
          <span className="dostopi-kartoteka__razmik" />
          <ZetoniStanja {...krmila} />
        </div>
        {poCrkah && (
          <div className="dostopi-kazalo">
            {CRKE_KAZALA.map((c) => (
              <button
                key={c}
                type="button"
                className={'dostopi-kazalo__crka' + (crkeZRacuni.has(c) ? '' : ' dostopi-kazalo__crka--prazna')}
                aria-label={`Skoči na črko ${c}`}
                aria-disabled={!crkeZRacuni.has(c) || undefined}
                onClick={() => crkeZRacuni.has(c) && skociNaCrko(c)}
              >
                {c}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="dostopi-kartoteka__mreza">
        <div className="dostopi-seznam" data-dostopi="kartoteka-seznam" ref={seznamRef}>
          {seznamVrstic((r) => (
            <VrsticaNamizje key={r.id} racun={r} izbrana={r.id === izbranId} onKlik={() => preklopiRacun(r.id)} />
          ))}
        </div>

        <div className="dostopi-kartoteka__plosca">
          {izbran ? (
            <>
              <div className="dostopi-kartoteka__plosca-glava">
                <span className="dostopi-pager__oznaka">Karton</span>
                <button type="button" className="dostopi-zapri" onClick={zapriKarton}>
                  Zapri ×
                </button>
              </div>
              <DostopiKarton
                key={kljucKartona(izbran)}
                racun={izbran}
                obvestilo={obvestilo}
                naObvestilo={nastaviObvestilo}
                igralciZRacunom={igralciZRacunom}
              />
            </>
          ) : (
            <StanjeDostopov vsi={vsi} />
          )}
        </div>
      </div>
    </div>
  )
}

function idSkupine(crka: string): string {
  return `dostopi-skupina-${crka}`
}

/* ---------- Krmila ---------- */

interface Krmila {
  filtri: FilterRacunov
  spremeniFiltre: (s: Partial<FilterRacunov>) => void
  razvrstitev: Razvrstitev
  obRazvrstitvi: (r: Razvrstitev) => void
  stevilaVlog: Record<string, number>
  stevilaStanj: Record<string, number>
}

function ZetoniVloge({ filtri, spremeniFiltre, stevilaVlog }: Krmila) {
  return (
    <>
      {MOZNOSTI_VLOGE.map(([vrednost, oznaka]) => (
        <button
          key={vrednost}
          type="button"
          className={'dostopi-zeton' + (filtri.vloga === vrednost ? ' dostopi-zeton--vklopljen' : '')}
          aria-pressed={filtri.vloga === vrednost}
          onClick={() => spremeniFiltre({ vloga: vrednost })}
        >
          {oznaka} <span className="dostopi-zeton__n">{stevilaVlog[vrednost] ?? 0}</span>
        </button>
      ))}
    </>
  )
}

function ZetoniStanja({ filtri, spremeniFiltre, stevilaStanj }: Krmila) {
  return (
    <>
      {MOZNOSTI_STANJA.map(([vrednost, oznaka]) => (
        <button
          key={vrednost}
          type="button"
          className={'dostopi-zeton' + (filtri.stanje === vrednost ? ' dostopi-zeton--vklopljen' : '')}
          aria-pressed={filtri.stanje === vrednost}
          onClick={() => spremeniFiltre({ stanje: vrednost as KljucStanja | 'vsi' })}
        >
          {oznaka} <span className="dostopi-zeton__n">{stevilaStanj[vrednost] ?? 0}</span>
        </button>
      ))}
    </>
  )
}

function SelectPaketa({ filtri, spremeniFiltre }: Krmila) {
  return (
    <select
      className="dostopi-izbor"
      aria-label="Paket"
      value={filtri.paket}
      onChange={(d) => spremeniFiltre({ paket: d.target.value as FilterRacunov['paket'] })}
    >
      {MOZNOSTI_PAKETA.map(([vrednost, oznaka]) => (
        <option key={vrednost} value={vrednost}>
          {oznaka}
        </option>
      ))}
    </select>
  )
}

function SelectRazvrstitve({ razvrstitev, obRazvrstitvi }: Krmila) {
  return (
    <select
      className="dostopi-izbor"
      data-dostopi="razvrsti"
      aria-label="Razvrsti"
      value={razvrstitev}
      onChange={(d) => obRazvrstitvi(d.target.value as Razvrstitev)}
    >
      {MOZNOSTI_RAZVRSTITVE.map(([vrednost, oznaka]) => (
        <option key={vrednost} value={vrednost}>
          {oznaka}
        </option>
      ))}
    </select>
  )
}

function StikaloSamodejno({
  filtri,
  spremeniFiltre,
  stisnjeno = false,
}: Krmila & { stisnjeno?: boolean }) {
  return (
    <button
      type="button"
      className={
        'dostopi-stikalo' +
        (filtri.samodejno ? ' dostopi-stikalo--vklopljeno' : '') +
        (stisnjeno ? ' dostopi-stikalo--stisnjeno' : '')
      }
      aria-pressed={filtri.samodejno}
      onClick={() => spremeniFiltre({ samodejno: !filtri.samodejno })}
    >
      <span className="dostopi-stikalo__kvadratek" />
      Samodejno povezani
    </button>
  )
}

/* ---------- Vrstice seznama ---------- */

function ZnackaStanja({ racun, velikost }: { racun: RacunIgralcaDto; velikost?: 'majhna' }) {
  const stanje = stanjeRacuna(racun)
  return (
    <span
      className={
        `dostopi-znacka dostopi-znacka--${stanje.kljuc}` + (velikost ? ` dostopi-znacka--${velikost}` : '')
      }
    >
      {stanje.besedilo}
    </span>
  )
}

/* Stanje prijave v vrstici: potrjen račun ima prijavo, ostali so še
   registracija. */
function prijavaVVrstici(r: RacunIgralcaDto): string {
  return r.status === 'POTRJEN'
    ? r.zadnjaPrijava
      ? `prijava ${rel(r.zadnjaPrijava)}`
      : 'ni prijave'
    : `reg. ${rel(r.ustvarjenOb)}`
}

function VrsticaNamizje({
  racun,
  izbrana,
  onKlik,
}: {
  racun: RacunIgralcaDto
  izbrana: boolean
  onKlik: () => void
}) {
  const jeAdmin = racun.vloga === 'ADMIN'
  return (
    <button
      type="button"
      data-dostopi="kartoteka-vrstica"
      className={`dostopi-vrstica dostopi-vrstica--${racun.vloga}` + (izbrana ? ' dostopi-vrstica--izbrana' : '')}
      aria-pressed={izbrana}
      onClick={onKlik}
    >
      <span className="dostopi-vrstica__oseba">
        <span className="dostopi-vrstica__ime-vrsta">
          <span className="dostopi-vrstica__ime">{imeRacuna(racun)}</span>
          {racun.vloga !== 'IGRALEC' && (
            <span className={`dostopi-vrstica__vloga dostopi-vrstica__vloga--${racun.vloga}`}>
              {jeAdmin ? 'Admin' : oznakaVloge(racun.vloga)}
            </span>
          )}
        </span>
        <span className="dostopi-vrstica__email">{racun.email}</span>
      </span>
      <span className="dostopi-vrstica__desno">
        <ZnackaStanja racun={racun} />
        <span className="dostopi-vrstica__meta">
          {jeAdmin ? 'admin' : imeNarocnine(racun.narocnina)} · {prijavaVVrstici(racun)}
        </span>
      </span>
    </button>
  )
}

function VrsticaTelefon({
  racun,
  izbrana,
  onKlik,
}: {
  racun: RacunIgralcaDto
  izbrana: boolean
  onKlik: () => void
}) {
  const jeAdmin = racun.vloga === 'ADMIN'
  const stanjeNar = jeAdmin ? null : stanjeNarocnine(racun.narocnina)
  const narocnina = jeAdmin
    ? 'administrator'
    : imeNarocnine(racun.narocnina) + (stanjeNar ? ` · ${stanjeNar}` : '')
  return (
    <button
      type="button"
      data-dostopi="kartoteka-vrstica"
      className={
        `dostopi-vrstica dostopi-vrstica--telefon dostopi-vrstica--${racun.vloga}` +
        (izbrana ? ' dostopi-vrstica--izbrana' : '')
      }
      aria-pressed={izbrana}
      onClick={onKlik}
    >
      <span className="dostopi-vrstica__ime-vrsta">
        <span className="dostopi-vrstica__ime">{imeRacuna(racun)}</span>
        <ZnackaStanja racun={racun} velikost="majhna" />
      </span>
      <span className="dostopi-vrstica__email">{racun.email}</span>
      <span className="dostopi-vrstica__meta dostopi-vrstica__meta--telefon">
        <span
          className={
            'dostopi-vrstica__narocnina' +
            (!jeAdmin && narocninaZahtevaPozornost(racun.narocnina)
              ? ' dostopi-vrstica__narocnina--opozorilo'
              : '')
          }
        >
          {narocnina}
        </span>
        <span className="dostopi-vrstica__prijava">{prijavaVVrstici(racun)}</span>
      </span>
    </button>
  )
}

/* ---------- Stanje dostopov (karton brez izbire) ---------- */

function StanjeDostopov({ vsi }: { vsi: RacunIgralcaDto[] }) {
  const skupine = useMemo(() => pregledStanja(vsi), [vsi])
  return (
    <div>
      <div className="dostopi-pager__oznaka dostopi-pregled__oznaka">Stanje dostopov</div>
      {skupine.map((s) => (
        <div key={s.naslov} className="dostopi-pregled__skupina">
          <h3 className="dostopi-pregled__naslov">{s.naslov}</h3>
          {s.vrstice.map((v) => (
            <div key={v.oznaka} className="dostopi-pregled__vrstica">
              <div className="dostopi-pregled__glava">
                <span>{v.oznaka}</span>
                <span className="dostopi-pregled__stevilo">{v.n}</span>
              </div>
              <div className="dostopi-palica">
                <div className="dostopi-palica__polnilo" style={{ width: v.delez }} />
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

/* ---------- Telefon: abecedno kazalo in list ---------- */

function KazaloTelefon({
  crkeZRacuni,
  onCrka,
}: {
  crkeZRacuni: Set<string>
  onCrka: (crka: string) => void
}) {
  /* Trak je pritrjen na okno, zato ga izrišemo v <body>: lepljiva glava in
     spodnja vrstica ga ne smeta prekriti ali premakniti. */
  return createPortal(
    <div className="dostopi-kazalo-telefon">
      {CRKE_KAZALA.map((c) => (
        <button
          key={c}
          type="button"
          className={'dostopi-kazalo-telefon__crka' + (crkeZRacuni.has(c) ? '' : ' dostopi-kazalo-telefon__crka--prazna')}
          aria-label={`Skoči na črko ${c}`}
          aria-disabled={!crkeZRacuni.has(c) || undefined}
          onClick={() => crkeZRacuni.has(c) && onCrka(c)}
        >
          {c}
        </button>
      ))}
    </div>,
    document.body,
  )
}

/* List (karton) čez stran na telefonu: zastor, na vrhu ostane glava telefona;
   klik na zastor zapre. Vzorec zadrževanja tipkovnice je isti kot pri
   predalu »Več« (Postavitev): izris v <body>, ozadje dobi `inert`, stran pod
   listom se ne drsi, fokus gre v list in se ob zaprtju vrne. Brez drsne
   animacije. */
function DostopiList({
  oznaka,
  nujno = false,
  onZapri,
  children,
}: {
  /* Levo v prvi vrstici (»Zahteva 1 od 4«); pri listu računa je ni. */
  oznaka: string | null
  /* List zahteve iz nabiralnika ima rjav zgornji rob. */
  nujno?: boolean
  onZapri: () => void
  children: ReactNode
}) {
  const okvir = useRef<HTMLDivElement>(null)
  const sprozilec = useRef<HTMLElement | null>(null)
  if (sprozilec.current === null && typeof document !== 'undefined') {
    sprozilec.current = document.activeElement as HTMLElement | null
  }

  useEffect(() => {
    const obTipki = (dogodek: KeyboardEvent) => {
      if (dogodek.key === 'Escape') onZapri()
    }
    window.addEventListener('keydown', obTipki)
    return () => window.removeEventListener('keydown', obTipki)
  }, [onZapri])

  useEffect(() => {
    const koren = document.getElementById('koren')
    const prejsnjiOverflow = document.body.style.overflow
    koren?.setAttribute('inert', '')
    document.body.style.overflow = 'hidden'
    okvir.current?.focus()
    return () => {
      koren?.removeAttribute('inert')
      document.body.style.overflow = prejsnjiOverflow
      const nazaj = sprozilec.current
      if (nazaj?.isConnected) nazaj.focus()
    }
  }, [])

  return createPortal(
    <div className="dostopi-list-zastor">
      <div className="dostopi-list-zastor__zgoraj" onClick={onZapri} />
      <div
        ref={okvir}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={oznaka ?? 'Karton računa'}
        className={'dostopi-list' + (nujno ? ' dostopi-list--nujno' : '')}
      >
        <div className="dostopi-list__vrsta">
          {oznaka ? <span className="dostopi-list__oznaka">{oznaka}</span> : <span />}
          <button type="button" className="dostopi-zapri" onClick={onZapri}>
            Zapri ×
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}

