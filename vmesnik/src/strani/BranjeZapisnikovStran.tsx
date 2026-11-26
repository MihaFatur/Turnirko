/* Stran »Branje zapisnikov« (/zapisniki/branje, V79): organizator lige po kolu
   fotografira ali skenira zapisnike srečanj, Turnirko prebere, kdo je igral
   (križci) in točke nizov. Odločitve lastnika (26. 11. 2026): zapisnik pride
   fizično k organizatorju, ta ga slika list za listom s telefonom ali skenira kup
   listov v PDF; zanesljivo prebran zapisnik se zapiše sam, ostale organizator
   pregleda - samo negotova polja so obarvana, z izrezom fotografije ob polju.

   Ena stran za vse lige organizatorja: koda QR na listu pove srečanje, zato je
   vseeno, katera liga je v kupu. Pošiljanje gre skozi isto vrsto brez signala
   kot listki turnirja (pomozno/vrstaFotografij.ts, prostor zapisnikov). */
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type {
  BranjeZapisnikaDto,
  CeliceZapisnikaDto,
  IgralecBranjaZapisnika,
  IzidTekme,
  StranEkipe,
  TekmaBranjaZapisnika,
  TekmaZapisaZapisnika,
  ZapisZapisnikaVnos,
} from '../api/tipi'
import { zapisnikiApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { useNazaj } from '../komponente/GlavaTelefona'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { Namig } from '../pomoc/Namig'
import { slogIzreza } from '../pomozno/branjeListkov'
import { useBudenZaslon } from '../pomozno/budenZaslon'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { jePdf, stranPdfVSlike } from '../pomozno/pdfVSlike'
import { besediloCakajocih, PROSTOR_ZAPISNIKOV, useVrstaFotografij, type FotografijaVrste } from '../pomozno/vrstaFotografij'

/* Zapisnik je list A4: daljša stranica 3200 px (strežnik: BranjeZapisnikovStoritev.NAJVEC_STRANICA). */
const STRANICA_ZAPISNIKA = 3200
const KLJUC_SAMODEJNO = 'turnirko-zapisniki-samodejno'

function preberiSamodejno(): boolean {
  try {
    return window.localStorage.getItem(KLJUC_SAMODEJNO) !== 'ne'
  } catch {
    return true
  }
}

export function BranjeZapisnikovStran() {
  const odjemalec = useQueryClient()
  const { uporabnik } = useAvtentikacija()
  useNaslovStrani('Branje zapisnikov')
  useNazaj('/lige', 'Lige')

  const stanje = useQuery({ queryKey: ['zapisniki', 'stanje'], queryFn: zapisnikiApi.stanje })
  const omogoceno = stanje.data?.omogoceno === true
  const [samodejno, nastaviSamodejno] = useState(preberiSamodejno)
  const [predogledi, nastaviPredoglede] = useState<Record<number, string>>({})
  const [odprt, nastaviOdprt] = useState<number | null>(null)
  const [napakaPdf, nastaviNapakoPdf] = useState<string | null>(null)
  const [pdfVPripravi, nastaviPdfVPripravi] = useState(0)
  useBudenZaslon(omogoceno)

  // vrsta pošilja v ozadju: izbira »zapiši sam« velja ob pošiljanju fotografije
  const samodejnoZdaj = useRef(samodejno)
  useEffect(() => {
    samodejnoZdaj.current = samodejno
  }, [samodejno])

  const kljucSeznama = ['zapisniki', 'seznam']
  const vrsta = useVrstaFotografij<BranjeZapisnikaDto>(
    PROSTOR_ZAPISNIKOV,
    omogoceno ? (uporabnik?.id ?? null) : null,
    (branje, predogled) => {
      if (predogled) nastaviPredoglede((p) => ({ ...p, [branje.id]: predogled }))
      void odjemalec.invalidateQueries({ queryKey: kljucSeznama })
    },
    (zapis, signal) => zapisnikiApi.nalozi(zapis.jpeg, samodejnoZdaj.current, zapis.kljuc, signal),
    STRANICA_ZAPISNIKA,
  )
  const posiljanja = vrsta.fotografije
  const cakajoNaSignal = posiljanja.some((f) => f.stanje === 'caka')
    ? posiljanja.filter((f) => f.stanje === 'caka' || f.stanje === 'v-vrsti' || f.stanje === 'posiljam').length
    : 0

  const seznam = useQuery({
    queryKey: kljucSeznama,
    queryFn: zapisnikiApi.seznam,
    enabled: omogoceno,
    refetchInterval: (poizvedba) =>
      posiljanja.length > 0 || poizvedba.state.data?.some((b) => b.stanje === 'V_VRSTI') ? 1500 : 5000,
  })

  const osvezi = () => {
    void odjemalec.invalidateQueries({ queryKey: kljucSeznama })
    void odjemalec.invalidateQueries({ queryKey: ['srecanja'] })
    void odjemalec.invalidateQueries({ queryKey: ['srecanje'] })
  }

  async function obDatotekah(dogodek: ChangeEvent<HTMLInputElement>) {
    const datoteke = [...(dogodek.target.files ?? [])]
    dogodek.target.value = ''
    nastaviNapakoPdf(null)
    for (const d of datoteke) {
      if (jePdf(d)) {
        nastaviPdfVPripravi((n) => n + 1)
        try {
          for (const stran of await stranPdfVSlike(d, STRANICA_ZAPISNIKA)) await vrsta.dodaj(stran)
        } catch (e) {
          nastaviNapakoPdf(`${d.name}: ${e instanceof Error ? e.message : 'PDF-ja ni bilo mogoče odpreti.'}`)
        } finally {
          nastaviPdfVPripravi((n) => n - 1)
        }
      } else {
        void vrsta.dodaj(d)
      }
    }
  }

  const zavrzenje = useMutation({ mutationFn: (id: number) => zapisnikiApi.zavrzi(id), onSuccess: osvezi })
  const ponovitev = useMutation({ mutationFn: (id: number) => zapisnikiApi.znova(id), onSuccess: osvezi })
  const zapisVseh = useMutation({
    mutationFn: async (branja: BranjeZapisnikaDto[]) => {
      for (const b of branja) await zapisnikiApi.potrdi(b.id, vnosIzPredloga(b, false))
    },
    onSettled: osvezi,
  })

  const razdeljeno = useMemo(() => razdeli(seznam.data ?? []), [seznam.data])
  const zanesljivi = razdeljeno.zaPregled.filter((b) => b.zanesljivo && b.srecanje && !b.srecanje.vpisano)

  // po potrditvi v oknu se odpre naslednji, ki čaka (zapisniki so v roki po vrsti)
  function odpriNaslednjega(potrjen: number) {
    const naslednji = razdeljeno.zaPregled.find((b) => b.id !== potrjen && b.stanje === 'PREBRANO')
    nastaviOdprt(naslednji ? naslednji.id : null)
  }

  if (stanje.isPending) return <p className="obvestilo">Nalaganje …</p>

  return (
    <section className="branje branje--zapisniki">
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">Branje zapisnikov</h1>
          <p className="uvod uvod--tesno">
            Ligaški zapisniki, natisnjeni iz Turnirka »za bralnik«. Koda na listu pove srečanje, zato lahko
            v enem kupu slikaš zapisnike vseh svojih lig.
          </p>
        </div>
      </div>

      {!omogoceno ? (
        <p className="namig">{stanje.data?.razlog ?? 'Branje zapisnikov ni na voljo.'}</p>
      ) : (
        <>
          <div className="branje__zajem">
            <label className="gumb gumb--glavni branje__fotografiraj">
              Fotografiraj zapisnik
              <input type="file" accept="image/*" capture="environment" className="branje__datoteka"
                onChange={(d) => void obDatotekah(d)} />
            </label>
            <label className="gumb branje__fotografiraj">
              Naloži slike ali PDF
              <input type="file" accept="image/*,application/pdf" multiple className="branje__datoteka"
                onChange={(d) => void obDatotekah(d)} />
            </label>
            <p className="branje__navodilo">
              Cel list od zgoraj, da se vidijo trije črni kvadrati v vogalih in koda zgoraj desno. Iz skenerja
              naloži PDF z vsemi listi kola. <Namig clanek="branje-zapisnikov" />
            </p>
            <label className="branje__samodejno">
              <input
                type="checkbox"
                checked={samodejno}
                onChange={(d) => {
                  nastaviSamodejno(d.target.checked)
                  try {
                    window.localStorage.setItem(KLJUC_SAMODEJNO, d.target.checked ? 'da' : 'ne')
                  } catch {
                    /* izbira velja za to stran */
                  }
                }}
              />{' '}
              Zanesljivo prebrane zapisnike zapiši sam (ostale pregledaš)
            </label>
          </div>
          {pdfVPripravi > 0 && <p className="branje__signal" role="status">Pripravljam strani PDF …</p>}
          {napakaPdf && <p className="napaka">{napakaPdf}</p>}
          <SporociloNapake napaka={seznam.error ?? zavrzenje.error ?? ponovitev.error ?? zapisVseh.error} />

          {(posiljanja.length > 0 || razdeljeno.berem.length > 0) && (
            <>
              <h2 className="branje__razdelek">Berem</h2>
              {cakajoNaSignal > 0 && (
                <p className="branje__signal" role="status">
                  {besediloCakajocih(cakajoNaSignal)} Pošljejo se same, ko bo povezava; pusti to stran odprto.
                </p>
              )}
              <ul className="branje__vrsta">
                {posiljanja.map((p) => (
                  <li key={`p${p.kljuc}`} className="branje-vrsta">
                    <Slicica url={p.predogled} />
                    <div className="branje-vrsta__telo">
                      <StanjePosiljanja f={p} />
                      {(p.stanje === 'napaka' || p.stanje === 'caka' || p.stanje === 'ustavljeno') && (
                        <button type="button" className="gumb gumb--majhen" onClick={() => vrsta.opusti(p.kljuc)}>
                          Opusti
                        </button>
                      )}
                    </div>
                  </li>
                ))}
                {razdeljeno.berem.map((b) => (
                  <li key={b.id} className="branje-vrsta">
                    <Slicica url={predogledi[b.id] ?? null} id={b.id} />
                    <div className="branje-vrsta__telo">
                      {b.obviselo ? (
                        <>
                          <p className="branje-vrsta__napaka">Branje se je zataknilo.</p>
                          <button type="button" className="gumb gumb--majhen" disabled={ponovitev.isPending}
                            onClick={() => ponovitev.mutate(b.id)}>
                            Preberi znova
                          </button>
                        </>
                      ) : (
                        <p className="branje-vrsta__stanje">Berem zapisnik …</p>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}

          <div className="branje__razdelek-glava">
            <h2 className="branje__razdelek">
              Za pregled{razdeljeno.zaPregled.length > 0 ? ` (${razdeljeno.zaPregled.length})` : ''}
            </h2>
            {zanesljivi.length >= 2 && (
              <button type="button" className="gumb gumb--glavni branje__potrdi-vse"
                disabled={zapisVseh.isPending} onClick={() => zapisVseh.mutate(zanesljivi)}>
                {zapisVseh.isPending ? 'Zapisujem …' : `Zapiši vse zanesljive (${zanesljivi.length})`}
              </button>
            )}
          </div>
          {razdeljeno.zaPregled.length === 0 ? (
            <p className="namig">{seznam.isPending ? 'Nalaganje …' : 'Ni zapisnikov, ki bi čakali nate.'}</p>
          ) : (
            <Skupine
              branja={razdeljeno.zaPregled}
              render={(b) => (
                <VrsticaZaPregled
                  key={b.id}
                  branje={b}
                  predogled={predogledi[b.id] ?? null}
                  zaseden={zavrzenje.isPending || ponovitev.isPending || zapisVseh.isPending}
                  onPreglej={() => nastaviOdprt(b.id)}
                  onZavrzi={() => zavrzenje.mutate(b.id)}
                  onZnova={() => ponovitev.mutate(b.id)}
                />
              )}
            />
          )}

          {razdeljeno.zapisana.length > 0 && (
            <>
              <h2 className="branje__razdelek">Zapisano ({razdeljeno.zapisana.length})</h2>
              <Skupine
                branja={razdeljeno.zapisana}
                render={(b) => (
                  <li key={b.id} className="branje-zakljuceno">
                    <span className="branje-zakljuceno__imena">
                      {b.srecanje ? (
                        <Link to={`/srecanja/${b.srecanje.id}`}>
                          {b.srecanje.domaci} : {b.srecanje.gost}
                        </Link>
                      ) : (
                        'Srečanje ni več v ligi'
                      )}
                    </span>
                    <span className="branje-zakljuceno__izid">
                      {b.srecanje ? `${b.srecanje.dobljeneDomaci} : ${b.srecanje.dobljeneGost}` : ''}
                      {b.zapisanoSamodejno ? ' · zapisal Turnirko sam' : ' · potrdil si'}
                      {b.slika && b.srecanje && (
                        <>
                          {' · '}
                          <button type="button" className="povezava-gumb" onClick={() => nastaviOdprt(b.id)}>
                            popravi
                          </button>
                        </>
                      )}
                    </span>
                  </li>
                )}
              />
            </>
          )}
          {razdeljeno.zavrzenih > 0 && (
            <p className="branje__merilo">Zavrženih fotografij: {razdeljeno.zavrzenih}</p>
          )}
        </>
      )}

      {odprt !== null && (
        <PregledZapisnika
          id={odprt}
          predogled={predogledi[odprt] ?? null}
          onZapisano={() => {
            osvezi()
            queueMicrotask(() => odpriNaslednjega(odprt))
          }}
          onZapri={() => nastaviOdprt(null)}
        />
      )}
    </section>
  )
}

function razdeli(branja: BranjeZapisnikaDto[]) {
  return {
    berem: branja.filter((b) => b.stanje === 'V_VRSTI'),
    zaPregled: branja.filter((b) => b.stanje === 'PREBRANO' || b.stanje === 'NAPAKA'),
    zapisana: branja.filter((b) => b.stanje === 'ZAPISANO'),
    zavrzenih: branja.filter((b) => b.stanje === 'ZAVRZENO').length,
  }
}

/* Zapisniki po ligah in kolih (organizator ima v kupu lahko več lig). */
function Skupine({ branja, render }: { branja: BranjeZapisnikaDto[]; render: (b: BranjeZapisnikaDto) => ReactNode }) {
  const skupine = new Map<string, BranjeZapisnikaDto[]>()
  for (const b of branja) {
    const kljuc = b.srecanje ? `${b.srecanje.tekmovanje} · ${b.srecanje.opis ?? ''}` : 'Zapisnika ni bilo mogoče prebrati'
    skupine.set(kljuc, [...(skupine.get(kljuc) ?? []), b])
  }
  return (
    <>
      {[...skupine.entries()].map(([kljuc, vSkupini]) => (
        <div key={kljuc} className="branje__skupina">
          <h3 className="branje__skupina-naslov">{kljuc}</h3>
          <ul className="branje__vrsta">{vSkupini.map(render)}</ul>
        </div>
      ))}
    </>
  )
}

function VrsticaZaPregled({
  branje,
  predogled,
  zaseden,
  onPreglej,
  onZavrzi,
  onZnova,
}: {
  branje: BranjeZapisnikaDto
  predogled: string | null
  zaseden: boolean
  onPreglej: () => void
  onZavrzi: () => void
  onZnova: () => void
}) {
  const s = branje.srecanje
  const p = branje.predlog
  return (
    <li className="branje-vrsta">
      <button type="button" className="branje-vrsta__slika" aria-label="Preglej zapisnik" disabled={!s}
        onClick={onPreglej}>
        <Slicica url={predogled} id={branje.slika ? branje.id : undefined} />
      </button>
      <div className="branje-vrsta__telo">
        {s && (
          <p className="branje-vrsta__kategorija">
            {s.domaci} : {s.gost}
          </p>
        )}
        {branje.stanje === 'NAPAKA' ? (
          <p className="branje-vrsta__napaka">{branje.napaka}</p>
        ) : (
          <>
            {p && (
              <p className={'branje-vrsta__stanje' + (p.negotovih > 0 || !p.zanesljiv ? ' branje-vrsta__stanje--pregled' : '')}>
                {povzetekPredloga(p.tekme)}
                {p.negotovih > 0 ? ` · ${p.negotovih} ${polj(p.negotovih)} za pregled` : ''}
              </p>
            )}
            {branje.opomba && <p className="branje-vrsta__napaka">{branje.opomba}</p>}
            {p && p.opozorila.length > 0 && <p className="branje-vrsta__napaka">{p.opozorila[0]}</p>}
          </>
        )}
        <div className="branje-vrsta__gumbi">
          {branje.stanje === 'PREBRANO' && s && (
            <button type="button" className="gumb gumb--glavni branje__potrdi-vse" disabled={zaseden} onClick={onPreglej}>
              Preglej
            </button>
          )}
          {branje.stanje === 'NAPAKA' && branje.slika && (
            <button type="button" className="gumb gumb--majhen" disabled={zaseden} onClick={onZnova}>
              Preberi znova
            </button>
          )}
          <button type="button" className="gumb gumb--majhen" disabled={zaseden} onClick={onZavrzi}>
            Zavrzi
          </button>
        </div>
      </div>
    </li>
  )
}

function polj(n: number): string {
  const o = n % 100
  return o === 1 ? 'polje' : o === 2 ? 'polji' : o === 3 || o === 4 ? 'polja' : 'polj'
}

/* »3 : 2 (5 tekem)« iz predloga. */
function povzetekPredloga(tekme: TekmaBranjaZapisnika[]): string {
  let d = 0
  let g = 0
  for (const t of tekme) {
    if (t.stanje !== 'ODIGRANA') continue
    if (t.niziDomaci > t.niziGost) d++
    else g++
  }
  return `Prebrano ${d} : ${g}`
}

/* Zapis iz predloga (brez popravkov) - »Zapiši vse zanesljive«. */
function vnosIzPredloga(b: BranjeZapisnikaDto, prepisi: boolean): ZapisZapisnikaVnos {
  const tekme: TekmaZapisaZapisnika[] = (b.predlog?.tekme ?? []).map((t) => {
    if (t.stanje !== 'ODIGRANA') return { domaci: [], gost: [], nizi: [], izidTip: null, zmagovalecStran: null }
    return {
      domaci: t.domaci.filter((x): x is number => x !== null),
      gost: t.gost.filter((x): x is number => x !== null),
      nizi: t.nizi
        .filter((n) => n.domaci !== null && n.gost !== null)
        .map((n) => ({ tocke1: n.domaci!, tocke2: n.gost! })),
      izidTip: null,
      zmagovalecStran: null,
    }
  })
  return { tekme, prepisi, casPregledaMs: null }
}

// ---------- Okno pregleda ----------

interface PoljeNiza {
  d: string
  g: string
}

interface ObrazecTekme {
  domaci: string[]
  gost: string[]
  nizi: PoljeNiza[]
  /* '' = odigrana po točkah; sicer posebni izid in zmagovalec (»PREDAJA:DOMACI«) */
  poseben: string
}

function obrazecIzPredloga(t: TekmaBranjaZapisnika, nizov: number): ObrazecTekme {
  const k = t.dvojice ? 2 : 1
  const igralci = (l: (number | null)[]) =>
    Array.from({ length: k }, (_, i) => (l[i] != null ? String(l[i]) : ''))
  const nizi = Array.from({ length: nizov }, (_, i) => {
    const n = t.nizi[i]
    return { d: n?.domaci != null ? String(n.domaci) : '', g: n?.gost != null ? String(n.gost) : '' }
  })
  return {
    domaci: t.stanje === 'NEODIGRANA' ? igralci([]) : igralci(t.domaci),
    gost: t.stanje === 'NEODIGRANA' ? igralci([]) : igralci(t.gost),
    nizi,
    poseben: '',
  }
}

/* Obrazec iz zapisa, ki je že šel v srečanje (»popravi« zapisanega). */
function obrazecIzPotrjenega(z: TekmaZapisaZapisnika, t: TekmaBranjaZapisnika, nizov: number): ObrazecTekme {
  const k = t.dvojice ? 2 : 1
  const igralci = (l: number[]) => Array.from({ length: k }, (_, i) => (l[i] != null ? String(l[i]) : ''))
  const poseben = z.izidTip && z.izidTip !== 'IGRANO' && z.zmagovalecStran ? `${z.izidTip}:${z.zmagovalecStran}` : ''
  return {
    domaci: igralci(z.domaci),
    gost: igralci(z.gost),
    nizi: Array.from({ length: nizov }, (_, i) => {
      const n = z.nizi[i]
      return { d: n ? String(n.tocke1) : '', g: n ? String(n.tocke2) : '' }
    }),
    poseben,
  }
}

function PregledZapisnika({
  id,
  predogled,
  onZapisano,
  onZapri,
}: {
  id: number
  predogled: string | null
  onZapisano: () => void
  onZapri: () => void
}) {
  const branje = useQuery({ queryKey: ['zapisniki', 'branje', id], queryFn: () => zapisnikiApi.podrobno(id) })
  const [obrazec, nastaviObrazec] = useState<ObrazecTekme[] | null>(null)
  const [fotografija, nastaviFotografijo] = useState(false)
  const [napaka, nastaviNapako] = useState<string | null>(null)

  const b = branje.data
  const pod = b?.podrobnosti
  useEffect(() => {
    if (b?.predlog && pod && obrazec === null) {
      // zapisan zapisnik (»popravi«) začne iz tega, kar je šlo v srečanje, ne iz branja
      const potrjeno = b.stanje === 'ZAPISANO' ? pod.potrjeno : null
      nastaviObrazec(
        b.predlog.tekme.map((t, i) => {
          const z = potrjeno?.tekme[i]
          return z ? obrazecIzPotrjenega(z, t, pod.steviloNizov) : obrazecIzPredloga(t, pod.steviloNizov)
        }),
      )
    }
  }, [b, pod, obrazec])

  const prepisi = b?.stanje === 'ZAPISANO' || b?.srecanje?.vpisano === true
  const zapis = useMutation({
    mutationFn: (vnos: ZapisZapisnikaVnos) => zapisnikiApi.potrdi(id, vnos),
    onSuccess: () => {
      onZapisano()
      onZapri()
    },
  })

  function shrani() {
    if (!b?.predlog || !obrazec) return
    nastaviNapako(null)
    const tekme: TekmaZapisaZapisnika[] = []
    for (let i = 0; i < obrazec.length; i++) {
      const o = obrazec[i]
      const t = b.predlog.tekme[i]
      const nizi = o.nizi
        .filter((n) => n.d.trim() !== '' || n.g.trim() !== '')
        .map((n) => ({ tocke1: Number(n.d), tocke2: Number(n.g) }))
      if (nizi.some((n) => !Number.isInteger(n.tocke1) || !Number.isInteger(n.tocke2))) {
        nastaviNapako(`Pri tekmi ${t.oznaka} vpiši obe števili vsakega niza.`)
        return
      }
      const [izidTip, zmagovalec] = o.poseben ? (o.poseben.split(':') as [IzidTekme, StranEkipe]) : [null, null]
      const ima = nizi.length > 0 || izidTip !== null
      if (ima && (o.domaci.some((x) => x === '') || o.gost.some((x) => x === ''))) {
        nastaviNapako(`Pri tekmi ${t.oznaka} izberi, kdo je igral.`)
        return
      }
      tekme.push({
        domaci: ima ? o.domaci.map(Number) : [],
        gost: ima ? o.gost.map(Number) : [],
        nizi: izidTip ? [] : nizi,
        izidTip,
        zmagovalecStran: zmagovalec,
      })
    }
    zapis.mutate({ tekme, prepisi, casPregledaMs: null })
  }

  const s = b?.srecanje
  return (
    <ModalnoOkno
      naslov={s ? `${s.domaci} : ${s.gost}` : 'Zapisnik'}
      podnaslov={s ? `${s.tekmovanje} · ${s.opis ?? ''}` : undefined}
      siroko
      razred="modal--pregled-zapisnika"
      onZapri={onZapri}
    >
      {branje.isPending && <p className="obvestilo">Nalaganje …</p>}
      <SporociloNapake napaka={branje.error} />
      {b && pod && b.predlog && obrazec && (
        <div className="pregled-zapisnika">
          {prepisi && (
            <p className="pregled-zapisnika__opozorilo">
              Zapisnik tega srečanja je že vpisan. Shranitev ga razveljavi in zapiše tega (rating se preračuna).
            </p>
          )}
          {b.predlog.opozorila.length > 0 && (
            <ul className="pregled-zapisnika__opozorila">
              {b.predlog.opozorila.map((o) => (
                <li key={o}>{o}</li>
              ))}
            </ul>
          )}
          <p className="pregled-zapisnika__namig">
            Obarvana polja bralnik ni prebral zanesljivo: primerjaj jih z izrezom fotografije in po potrebi popravi.
            {' '}
            <button type="button" className="povezava-gumb" onClick={() => nastaviFotografijo((f) => !f)}>
              {fotografija ? 'Skrij fotografijo' : 'Pokaži fotografijo'}
            </button>
          </p>
          {fotografija && (
            <div className="pregled-zapisnika__fotografija">
              <Fotografija url={predogled} id={b.slika ? b.id : undefined} />
            </div>
          )}
          {b.predlog.tekme.map((t, i) => (
            <TekmaPregleda
              key={i}
              indeks={i}
              tekma={t}
              obrazec={obrazec[i]}
              kaderDomaci={pod.kaderDomaci}
              kaderGost={pod.kaderGost}
              celice={pod.celice}
              idBranja={b.id}
              onSpremeni={(nov) => nastaviObrazec(obrazec.map((x, j) => (j === i ? nov : x)))}
            />
          ))}
          {napaka && <p className="napaka">{napaka}</p>}
          <SporociloNapake napaka={zapis.error} />
          <div className="obrazec__gumbi">
            <button type="button" className="gumb" onClick={onZapri}>
              Prekliči
            </button>
            <button type="button" className="gumb gumb--glavni" disabled={zapis.isPending} onClick={shrani}>
              {zapis.isPending ? 'Zapisujem …' : prepisi ? 'Prepiši zapisnik' : 'Zapiši v srečanje'}
            </button>
          </div>
        </div>
      )}
    </ModalnoOkno>
  )
}

function TekmaPregleda({
  indeks,
  tekma,
  obrazec,
  kaderDomaci,
  kaderGost,
  celice,
  idBranja,
  onSpremeni,
}: {
  indeks: number
  tekma: TekmaBranjaZapisnika
  obrazec: ObrazecTekme
  kaderDomaci: IgralecBranjaZapisnika[]
  kaderGost: IgralecBranjaZapisnika[]
  celice: CeliceZapisnikaDto | null
  idBranja: number
  onSpremeni: (o: ObrazecTekme) => void
}) {
  const izbira = (domaci: boolean, i: number, kader: IgralecBranjaZapisnika[], negotovo: boolean) => {
    const vrednosti = domaci ? obrazec.domaci : obrazec.gost
    return (
      <select
        key={i}
        className={'pregled-zapisnika__igralec' + (negotovo ? ' pregled-zapisnika__polje--negotovo' : '')}
        value={vrednosti[i]}
        aria-label={`${domaci ? 'Domači' : 'Gost'} igralec tekme ${tekma.oznaka}`}
        onChange={(d) => {
          const nove = [...vrednosti]
          nove[i] = d.target.value
          onSpremeni(domaci ? { ...obrazec, domaci: nove } : { ...obrazec, gost: nove })
        }}
      >
        <option value="">— izberi —</option>
        {kader.map((k) => (
          <option key={k.id} value={String(k.id)}>
            {k.ime}
          </option>
        ))}
      </select>
    )
  }
  const izrezKvadratkov = (domaci: boolean) => {
    if (!celice) return null
    const k = celice.kvadratki.filter((x) => x.tekma === indeks && x.domaci === domaci)
    if (k.length === 0) return null
    const p = {
      x0: Math.min(...k.map((x) => x.x0)),
      y0: Math.min(...k.map((x) => x.y0)),
      x1: Math.max(...k.map((x) => x.x1)),
      y1: Math.max(...k.map((x) => x.y1)),
    }
    return <Izrez id={idBranja} slog={slogIzreza(p, celice.sirina, celice.visina, 10)} siroko />
  }
  const izrezCelice = (niz: number, domaci: boolean) => {
    const c = celice?.celice.find((x) => x.tekma === indeks && x.niz === niz && x.domaci === domaci)
    if (!c || !celice) return null
    return <Izrez id={idBranja} slog={slogIzreza(c, celice.sirina, celice.visina)} />
  }
  const posebni: { vrednost: string; oznaka: string }[] = [
    { vrednost: '', oznaka: 'po točkah' },
    { vrednost: 'PREDAJA:DOMACI', oznaka: 'predaja – zmagal domači' },
    { vrednost: 'PREDAJA:GOST', oznaka: 'predaja – zmagal gost' },
    { vrednost: 'BREZ_BOJA:DOMACI', oznaka: 'brez boja – zmagal domači' },
    { vrednost: 'BREZ_BOJA:GOST', oznaka: 'brez boja – zmagal gost' },
  ]

  return (
    <div className={'pregled-zapisnika__tekma' + (tekma.stanje === 'NEODIGRANA' ? ' pregled-zapisnika__tekma--prazna' : '')}>
      <div className="pregled-zapisnika__glava">
        <span className="pregled-zapisnika__oznaka">{tekma.dvojice ? 'Dvojice' : tekma.oznaka}</span>
        <div className="pregled-zapisnika__stran">
          <span className="pregled-zapisnika__ime-strani">Domači</span>
          {obrazec.domaci.map((_, i) => izbira(true, i, kaderDomaci, tekma.negotovoDomaci))}
          {tekma.negotovoDomaci && izrezKvadratkov(true)}
        </div>
        <span className="pregled-zapisnika__proti">:</span>
        <div className="pregled-zapisnika__stran">
          <span className="pregled-zapisnika__ime-strani">Gostje</span>
          {obrazec.gost.map((_, i) => izbira(false, i, kaderGost, tekma.negotovoGost))}
          {tekma.negotovoGost && izrezKvadratkov(false)}
        </div>
        <select className="pregled-zapisnika__poseben" value={obrazec.poseben} aria-label="Izid tekme"
          onChange={(d) => onSpremeni({ ...obrazec, poseben: d.target.value })}>
          {posebni.map((p) => (
            <option key={p.vrednost} value={p.vrednost}>
              {p.oznaka}
            </option>
          ))}
        </select>
      </div>
      {obrazec.poseben === '' && (
        <div className="pregled-zapisnika__nizi">
          {obrazec.nizi.map((n, s) => {
            const prebran = tekma.nizi[s]
            const negD = prebran?.negotovoDomaci ?? false
            const negG = prebran?.negotovoGost ?? false
            const polje = (domaci: boolean, negotovo: boolean) => (
              <input
                type="text"
                inputMode="numeric"
                maxLength={2}
                className={'pregled-zapisnika__tocke' + (negotovo ? ' pregled-zapisnika__polje--negotovo' : '')}
                value={domaci ? n.d : n.g}
                aria-label={`${s + 1}. niz, ${domaci ? 'domači' : 'gost'}`}
                onChange={(d) => {
                  const v = d.target.value.replace(/\D/g, '')
                  const nizi = obrazec.nizi.map((x, j) => (j === s ? (domaci ? { ...x, d: v } : { ...x, g: v }) : x))
                  onSpremeni({ ...obrazec, nizi })
                }}
              />
            )
            return (
              <div key={s} className="pregled-zapisnika__niz">
                <span className="pregled-zapisnika__st-niza">{s + 1}.</span>
                <div className="pregled-zapisnika__par">
                  {polje(true, negD)}
                  <span>:</span>
                  {polje(false, negG)}
                </div>
                {(negD || negG) && (
                  <div className="pregled-zapisnika__izrezi">
                    {negD && izrezCelice(s, true)}
                    {negG && izrezCelice(s, false)}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function Izrez({ id, slog, siroko = false }: { id: number; slog: ReturnType<typeof slogIzreza>; siroko?: boolean }) {
  const slika = useQuery({
    queryKey: ['zapisniki', 'poravnana', id],
    queryFn: () => zapisnikiApi.poravnana(id),
    staleTime: Infinity,
    retry: false,
  })
  if (!slika.data) return <span className="izrez-celice izrez-celice--prazen" />
  return (
    <span
      role="img"
      aria-label="Izrez fotografije zapisnika"
      className={'izrez-celice pregled-zapisnika__izrez' + (siroko ? ' pregled-zapisnika__izrez--siroko' : '')}
      style={{ ...slog, backgroundImage: `url(${naslovSlike(slika.data)})` }}
    />
  )
}

// ---------- Sličice in fotografije ----------

const naslovi = new WeakMap<Blob, string>()
function naslovSlike(blob: Blob): string {
  let n = naslovi.get(blob)
  if (!n) {
    n = URL.createObjectURL(blob)
    naslovi.set(blob, n)
  }
  return n
}

function Slicica({ url, id }: { url: string | null; id?: number }) {
  return (
    <span className="branje-vrsta__slicica">
      <Fotografija url={url} id={id} />
    </span>
  )
}

function Fotografija({ url, id }: { url: string | null; id?: number }) {
  const slika = useQuery({
    queryKey: ['zapisniki', 'slika', id],
    queryFn: () => zapisnikiApi.slika(id!),
    enabled: !url && id !== undefined,
    staleTime: Infinity,
  })
  const vir = url ?? (slika.data ? naslovSlike(slika.data) : null)
  return vir ? <img src={vir} alt="Fotografija zapisnika" /> : <span className="branje-vrsta__brez-slike" />
}

function StanjePosiljanja({ f }: { f: FotografijaVrste }) {
  switch (f.stanje) {
    case 'pripravljam':
      return <p className="branje-vrsta__stanje">Pripravljam fotografijo …</p>
    case 'v-vrsti':
      return <p className="branje-vrsta__stanje">V vrsti za pošiljanje …</p>
    case 'posiljam':
      return <p className="branje-vrsta__stanje">Pošiljam …</p>
    case 'caka':
      return (
        <p className="branje-vrsta__stanje">
          {f.napaka ?? 'Ni povezave.'} Poskusim znova ({f.poskusov}. poskus ni uspel) …
        </p>
      )
    default:
      return <p className="branje-vrsta__napaka">{f.napaka}</p>
  }
}
