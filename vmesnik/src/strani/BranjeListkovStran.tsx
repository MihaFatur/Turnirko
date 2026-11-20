/* Stran »Branje listkov« (/turnirji/:id/branje-listkov): organizator s telefonom
   fotografira izpolnjene sodniške listke, Turnirko prebere točke, organizator
   izid potrdi. Narejena za telefon na pultu (odločitev lastnika, 19. 11. 2026):
   sodniki pri mizi telefona nimajo, listek prinesejo na pult.

   Potek: »Fotografiraj listek« → fotografija se pomanjša v brskalniku in pošlje
   → strežnik jo bere v ozadju (nekaj sekund), organizator ta čas fotografira
   naslednjega → prebran listek se pojavi v vrsti »Za potrditev«. Listek brez
   opozoril in negotovih celic se potrdi z enim dotikom; vse ostalo se odpre v
   oknu z fotografijo nad istim obrazcem kot ročni vnos (VnosRezultataOkno).
   Branje samo nikoli ne zapiše izida - šele potrditev, po isti poti kot ročni
   vnos (docs/pravila/branje-listkov.md). */
import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { imeUdelezenca } from '../api/tipi'
import type {
  BranjeListkaDto,
  KandidatListkaDto,
  NacinPotrditve,
  PredlogIzidaDto,
  TekmaListkaDto,
  VnosRezultata,
} from '../api/tipi'
import { branjeListkovApi, turnirjiApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { useNazaj } from '../komponente/GlavaTelefona'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import { Namig } from '../pomoc/Namig'
import {
  casPregleda,
  celiceZaPregled,
  pomanjsajFotografijo,
  razdeliVrsto,
  vnosIzPredloga,
} from '../pomozno/branjeListkov'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'

/* Fotografija, ki je še na poti na strežnik. */
interface Posiljanje {
  kljuc: number
  predogled: string | null
  napaka: string | null
}

/* Odprto okno pregleda: branje, tekma (prepoznana ali izbrana s seznama) in predlog. */
interface Pregled {
  branje: BranjeListkaDto
  tekma: TekmaListkaDto
  predlog: PredlogIzidaDto | null
}

let naslednjiKljuc = 1

export function BranjeListkovStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()

  const turnir = useQuery({
    queryKey: ['turnir', idTurnirja],
    queryFn: () => turnirjiApi.najdi(idTurnirja),
  })
  const stanje = useQuery({
    queryKey: ['branje-listkov', 'stanje-strani'],
    queryFn: branjeListkovApi.stanje,
  })
  const omogoceno = stanje.data?.omogoceno === true

  const [posiljanja, nastaviPosiljanja] = useState<Posiljanje[]>([])
  /* Fotografija, ki jo je telefon pravkar posnel, se ne prenaša nazaj s strežnika. */
  const [predogledi, nastaviPredoglede] = useState<Record<number, string>>({})
  const [pregled, nastaviPregled] = useState<Pregled | null>(null)
  const { jeAdmin } = useAvtentikacija()

  /* Sled pregleda (meritev, V69): kdaj se je predlog prvič pokazal in kdaj je
     organizator nazadnje potrdil ali zavrgel listek (glej casPregleda). */
  const prikazanOb = useRef(new Map<number, number>())
  const zadnjaAkcija = useRef<number | null>(null)
  const sledPregleda = (id: number, nacin: NacinPotrditve) => ({
    casPregledaMs: casPregleda(prikazanOb.current.get(id), zadnjaAkcija.current, performance.now()),
    nacin,
  })
  const oznaciAkcijo = () => {
    zadnjaAkcija.current = performance.now()
  }

  const kljucBranj = ['turnir', idTurnirja, 'branja-listkov']
  const branja = useQuery({
    queryKey: kljucBranj,
    queryFn: () => branjeListkovApi.seznam(idTurnirja),
    enabled: omogoceno,
    /* Dokler se kaj bere, vsaki 1,5 s (branje traja nekaj sekund); sicer na 10 s,
       ker isti turnir lahko vodita dva organizatorja z dvema telefonoma. */
    refetchInterval: (poizvedba) =>
      posiljanja.length > 0 || poizvedba.state.data?.branja.some((b) => b.stanje === 'V_VRSTI')
        ? 1500
        : 10_000,
  })

  const jeTelefon = useTelefon()
  useNaslovStrani('Branje listkov')
  useNazaj(`/turnirji/${idTurnirja}`, 'Turnir')

  const osvezi = () => {
    void odjemalec.invalidateQueries({ queryKey: kljucBranj })
    // izid je spremenil tekme, skupine in mrežo: strani turnirja in dogodkov
    void odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja] })
    void odjemalec.invalidateQueries({ queryKey: ['dogodek'] })
  }

  async function poslji(datoteka: File) {
    const kljuc = naslednjiKljuc++
    nastaviPosiljanja((p) => [...p, { kljuc, predogled: null, napaka: null }])
    try {
      const jpeg = await pomanjsajFotografijo(datoteka)
      const predogled = URL.createObjectURL(jpeg)
      nastaviPosiljanja((p) => p.map((x) => (x.kljuc === kljuc ? { ...x, predogled } : x)))
      const branje = await branjeListkovApi.nalozi(idTurnirja, jpeg)
      nastaviPredoglede((p) => ({ ...p, [branje.id]: predogled }))
      nastaviPosiljanja((p) => p.filter((x) => x.kljuc !== kljuc))
      void odjemalec.invalidateQueries({ queryKey: kljucBranj })
    } catch (napaka) {
      nastaviPosiljanja((p) =>
        p.map((x) => (x.kljuc === kljuc ? { ...x, napaka: opisNapake(napaka) } : x)),
      )
    }
  }

  function obFotografiji(dogodek: ChangeEvent<HTMLInputElement>) {
    const datoteke = [...(dogodek.target.files ?? [])]
    // isto datoteko je mogoče izbrati znova (druga fotografija istega imena na telefonu)
    dogodek.target.value = ''
    datoteke.forEach((d) => void poslji(d))
  }

  /* Potrditev z enim dotikom (gumb v vrsti); okno potrjuje prek VnosRezultataOkno. */
  const potrditev = useMutation({
    mutationFn: ({ b, vnos, idTekme }: { b: BranjeListkaDto; vnos: VnosRezultata; idTekme: number | null }) =>
      branjeListkovApi.potrdi(b.id, { idTekme, izid: vnos, ...sledPregleda(b.id, 'EN_DOTIK') }),
    onSuccess: () => {
      oznaciAkcijo()
      osvezi()
    },
  })
  const zavrzenje = useMutation({
    mutationFn: (b: BranjeListkaDto) => branjeListkovApi.zavrzi(b.id),
    onSuccess: () => {
      oznaciAkcijo()
      osvezi()
    },
  })
  const ponovitev = useMutation({
    mutationFn: (b: BranjeListkaDto) => branjeListkovApi.znova(b.id),
    onSuccess: (_odgovor, b) => {
      // novo branje: čas pregleda šteje od novega prikaza, ne od prvega
      prikazanOb.current.delete(b.id)
      osvezi()
    },
  })

  const vrsta = useMemo(() => razdeliVrsto(branja.data?.branja ?? []), [branja.data])

  // prvi prikaz vsakega listka, ki čaka na organizatorja (tudi neuspelo branje, vpisano ročno)
  useEffect(() => {
    const zdaj = performance.now()
    for (const b of vrsta.zaPotrditev) {
      if (!prikazanOb.current.has(b.id)) prikazanOb.current.set(b.id, zdaj)
    }
  }, [vrsta])

  /* Po potrditvi v oknu se odpre naslednji prebrani listek s tekmo - organizator
     ima listke v roki po vrsti in jih potrjuje drugega za drugim. */
  function odpriNaslednjega(potrjen: number) {
    const naslednji = vrsta.zaPotrditev.find(
      (b) => b.id !== potrjen && b.stanje === 'PREBRANO' && b.tekma !== null,
    )
    nastaviPregled(naslednji ? { branje: naslednji, tekma: naslednji.tekma!, predlog: naslednji.predlog } : null)
  }

  if (stanje.isPending) return <p className="obvestilo">Nalaganje …</p>

  return (
    <section className="branje">
      {/* Na telefonu je pot nazaj v glavi (useNazaj). */}
      {!jeTelefon && (
        <Link to={`/turnirji/${idTurnirja}`} className="povezava-nazaj">
          ← Nazaj na turnir
        </Link>
      )}
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">Branje listkov</h1>
          {turnir.data && <p className="uvod uvod--tesno">{turnir.data.ime}</p>}
        </div>
      </div>

      {!omogoceno ? (
        <p className="namig">{stanje.data?.razlog ?? 'Branje listkov ni na voljo.'}</p>
      ) : (
        <>
          <div className="branje__zajem">
            <label className="gumb gumb--glavni branje__fotografiraj">
              Fotografiraj listek
              <input
                type="file"
                accept="image/*"
                capture="environment"
                className="branje__datoteka"
                onChange={obFotografiji}
              />
            </label>
            <p className="branje__navodilo">
              En listek na fotografijo, od zgoraj, da sta na sliki cela tabela in koda v desnem
              kotu. <Namig clanek="branje-listkov" />
            </p>
          </div>

          {branja.data && branja.data.potrjenih > 0 && (
            <p className="branje__merilo">
              Brez popravka {branja.data.brezPopravka} od {branja.data.potrjenih} potrjenih
            </p>
          )}
          {jeAdmin && (
            <Link to={`/branje-listkov/meritev?idTurnir=${idTurnirja}`} className="branje__meritev">
              Meritev branja →
            </Link>
          )}
          <SporociloNapake napaka={branja.error ?? potrditev.error ?? zavrzenje.error ?? ponovitev.error} />

          {(posiljanja.length > 0 || vrsta.berem.length > 0) && (
            <>
              <h2 className="branje__razdelek">Berem</h2>
              <ul className="branje__vrsta">
                {posiljanja.map((p) => (
                  <li key={`p${p.kljuc}`} className="branje-vrsta">
                    <Slicica url={p.predogled} />
                    <div className="branje-vrsta__telo">
                      {p.napaka ? (
                        <>
                          <p className="branje-vrsta__napaka">{p.napaka}</p>
                          <button
                            type="button"
                            className="gumb gumb--majhen"
                            onClick={() => nastaviPosiljanja((x) => x.filter((y) => y.kljuc !== p.kljuc))}
                          >
                            Opusti
                          </button>
                        </>
                      ) : (
                        <p className="branje-vrsta__stanje">Pošiljam …</p>
                      )}
                    </div>
                  </li>
                ))}
                {vrsta.berem.map((b) => (
                  <li key={b.id} className="branje-vrsta">
                    <Slicica url={predogledi[b.id] ?? null} id={b.id} />
                    <div className="branje-vrsta__telo">
                      {b.obviselo ? (
                        <>
                          <p className="branje-vrsta__napaka">Branje se je zataknilo.</p>
                          <button type="button" className="gumb gumb--majhen"
                            disabled={ponovitev.isPending} onClick={() => ponovitev.mutate(b)}>
                            Preberi znova
                          </button>
                        </>
                      ) : (
                        <p className="branje-vrsta__stanje">Berem listek …</p>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}

          <h2 className="branje__razdelek">
            Za potrditev{vrsta.zaPotrditev.length > 0 ? ` (${vrsta.zaPotrditev.length})` : ''}
          </h2>
          {vrsta.zaPotrditev.length === 0 ? (
            <p className="namig">
              {branja.isPending ? 'Nalaganje …' : 'Ni listkov, ki bi čakali na potrditev.'}
            </p>
          ) : (
            <ul className="branje__vrsta">
              {vrsta.zaPotrditev.map((b) => (
                <VrsticaZaPotrditev
                  key={b.id}
                  branje={b}
                  predogled={predogledi[b.id] ?? null}
                  zaseden={potrditev.isPending || zavrzenje.isPending || ponovitev.isPending}
                  onPreglej={(tekma, predlog) => nastaviPregled({ branje: b, tekma, predlog })}
                  onPotrdi={(predlog) => potrditev.mutate({ b, vnos: vnosIzPredloga(predlog), idTekme: null })}
                  onZavrzi={() => zavrzenje.mutate(b)}
                  onZnova={() => ponovitev.mutate(b)}
                />
              ))}
            </ul>
          )}

          {vrsta.zakljucena.length > 0 && (
            <>
              <h2 className="branje__razdelek">Zaključeni</h2>
              <ul className="branje__zakljuceni">
                {vrsta.zakljucena.map((b) => (
                  <li key={b.id} className="branje-zakljuceno">
                    <span className="branje-zakljuceno__imena">
                      {b.tekma ? imeTekme(b.tekma) : 'Neznana tekma'}
                    </span>
                    <span className="branje-zakljuceno__izid">
                      {b.stanje === 'ZAVRZENO'
                        ? 'zavrženo'
                        : `${izidTekme(b.tekma)}${b.popravljeno ? ' · popravljeno' : ''}`}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}

      {pregled && (
        <VnosRezultataOkno
          key={`${pregled.branje.id}-${pregled.tekma.tekma.id}`}
          tekma={pregled.tekma.tekma}
          naslov="Potrditev listka"
          predlog={pregled.predlog ?? undefined}
          nadObrazcem={<PregledListka branje={pregled.branje} tekma={pregled.tekma}
            predlog={pregled.predlog} predogled={predogledi[pregled.branje.id] ?? null} />}
          shrani={(vnos) =>
            branjeListkovApi.potrdi(pregled.branje.id, {
              idTekme: pregled.tekma.tekma.id,
              izid: vnos,
              ...sledPregleda(pregled.branje.id, 'OBRAZEC'),
            })
          }
          onShranjeno={() => {
            const potrjen = pregled.branje.id
            oznaciAkcijo()
            osvezi()
            // okno se zapre samo (onZapri), naslednji se odpre takoj za njim
            queueMicrotask(() => odpriNaslednjega(potrjen))
          }}
          onZapri={() => nastaviPregled(null)}
        />
      )}
    </section>
  )
}

/* Ena vrstica vrste »Za potrditev«. */
function VrsticaZaPotrditev({
  branje,
  predogled,
  zaseden,
  onPreglej,
  onPotrdi,
  onZavrzi,
  onZnova,
}: {
  branje: BranjeListkaDto
  predogled: string | null
  zaseden: boolean
  onPreglej: (tekma: TekmaListkaDto, predlog: PredlogIzidaDto | null) => void
  onPotrdi: (predlog: PredlogIzidaDto) => void
  onZavrzi: () => void
  onZnova: () => void
}) {
  const { tekma, predlog } = branje
  const brezImen = !branje.prebranoLevo && !branje.prebranoDesno
  const zakaj = brezImen
    ? 'Imen na listku ni bilo mogoče prebrati.'
    : `Na listku: ${branje.prebranoLevo ?? '?'} – ${branje.prebranoDesno ?? '?'}.`

  return (
    <li className="branje-vrsta">
      <button
        type="button"
        className="branje-vrsta__slika"
        aria-label="Preglej listek"
        disabled={!tekma}
        onClick={() => tekma && onPreglej(tekma, predlog)}
      >
        <Slicica url={predogled} id={branje.imaSliko ? branje.id : undefined} />
      </button>
      <div className="branje-vrsta__telo">
        {tekma && <p className="branje-vrsta__kategorija">{opisTekme(tekma)}</p>}
        {tekma && <p className="branje-vrsta__imena">{imeTekme(tekma)}</p>}

        {branje.stanje === 'NAPAKA' && <p className="branje-vrsta__napaka">{branje.napaka}</p>}

        {branje.stanje === 'PREBRANO' && tekma && predlog && (
          <>
            <Povzetek predlog={predlog} />
            {predlog.opozorila.length > 0 && (
              <ul className="branje-vrsta__opozorila">
                {predlog.opozorila.map((o) => (
                  <li key={o}>{o}</li>
                ))}
              </ul>
            )}
            {predlog.opozorila.length === 0 && !predlog.pripravljen && (
              <p className="branje-vrsta__namig">Obarvane številke preveri na fotografiji.</p>
            )}
          </>
        )}

        {branje.stanje === 'PREBRANO' && !tekma && (
          <>
            {/* Branje števil ni odvisno od tekme: pokažemo ga pred izbiro. */}
            {branje.kandidati[0] && <Povzetek predlog={branje.kandidati[0].predlog} />}
            <p className="branje-vrsta__napaka">
              {brezImen ? 'Listek nima kode tekme (prazen listek).' : `Tekma ni prepoznana. ${zakaj}`}
            </p>
            {branje.napaka && <p className="branje-vrsta__napaka">{branje.napaka}</p>}
            {branje.kandidati.length > 0 ? (
              <IzbiraTekme kandidati={branje.kandidati} onIzberi={(k) => onPreglej(k.tekma, k.predlog)} />
            ) : (
              <p className="branje-vrsta__namig">
                Med tekmami, ki čakajo na izid, ni teh imen. Vpiši izid na strani kategorije.
              </p>
            )}
          </>
        )}

        <div className="branje-vrsta__gumbi">
          {branje.stanje === 'PREBRANO' && tekma && predlog?.pripravljen && (
            <button type="button" className="gumb gumb--glavni branje-vrsta__potrdi" disabled={zaseden}
              onClick={() => onPotrdi(predlog)}>
              Potrdi
            </button>
          )}
          {tekma && (
            <button type="button" className="gumb gumb--majhen" disabled={zaseden}
              onClick={() => onPreglej(tekma, predlog)}>
              {branje.stanje === 'NAPAKA' ? 'Vpiši ročno' : 'Preglej'}
            </button>
          )}
          {branje.imaSliko && (
            <button type="button" className="gumb gumb--majhen" disabled={zaseden} onClick={onZnova}>
              Preberi znova
            </button>
          )}
          <button type="button" className="gumb gumb--majhen gumb--nevaren" disabled={zaseden}
            onClick={onZavrzi}>
            Zavrzi
          </button>
        </div>
      </div>
    </li>
  )
}

/* Izbira tekme za listek brez kode: nekaj gumbov ali, pri več tekmah, spustni seznam. */
function IzbiraTekme({
  kandidati,
  onIzberi,
}: {
  kandidati: KandidatListkaDto[]
  onIzberi: (k: KandidatListkaDto) => void
}) {
  const [izbrana, nastaviIzbrano] = useState('')
  if (kandidati.length <= 6) {
    return (
      <div className="branje-vrsta__kandidati">
        <span className="branje-vrsta__oznaka">Izberi tekmo</span>
        {kandidati.map((k) => (
          <button key={k.tekma.tekma.id} type="button" className="gumb gumb--majhen" onClick={() => onIzberi(k)}>
            {imeTekme(k.tekma)} · {k.tekma.imeDogodka}
          </button>
        ))}
      </div>
    )
  }
  const izbran = kandidati.find((k) => String(k.tekma.tekma.id) === izbrana)
  return (
    <div className="branje-vrsta__kandidati">
      <label className="branje-vrsta__oznaka" htmlFor={`izbira-${kandidati[0].tekma.tekma.id}`}>
        Izberi tekmo
      </label>
      <select
        id={`izbira-${kandidati[0].tekma.tekma.id}`}
        className="branje-vrsta__izbira"
        value={izbrana}
        onChange={(e) => nastaviIzbrano(e.target.value)}
      >
        <option value="">Tekme, ki čakajo na izid …</option>
        {kandidati.map((k) => (
          <option key={k.tekma.tekma.id} value={k.tekma.tekma.id}>
            {opisTekme(k.tekma)} · {imeTekme(k.tekma)}
          </option>
        ))}
      </select>
      <button type="button" className="gumb gumb--majhen" disabled={!izbran} onClick={() => izbran && onIzberi(izbran)}>
        Naprej
      </button>
    </div>
  )
}

/* Izid in točke v eni vrstici; številke za pregled so obarvane. */
function Povzetek({ predlog }: { predlog: PredlogIzidaDto }) {
  const zaPregled = celiceZaPregled(predlog)
  const posebno =
    predlog.izidTip === 'BREZ_BOJA' ? 'brez boja'
      : predlog.izidTip === 'DISKVALIFIKACIJA' ? 'diskvalifikacija'
        : predlog.izidTip === 'PREDAJA' ? 'predaja'
          : null
  return (
    <p className="branje-vrsta__izid">
      {predlog.dobljeniNizi1 !== null && predlog.dobljeniNizi2 !== null && (
        <span className="branje-vrsta__nizi">
          {predlog.dobljeniNizi1} : {predlog.dobljeniNizi2}
        </span>
      )}
      {posebno && <span className="branje-vrsta__posebno">{posebno}</span>}
      {predlog.nizi.map((n, i) => (
        <span key={i} className="branje-vrsta__niz">
          <span className={zaPregled.has(i * 2) ? 'branje-vrsta__pregled' : undefined}>{n.tocke1 ?? '?'}</span>
          :
          <span className={zaPregled.has(i * 2 + 1) ? 'branje-vrsta__pregled' : undefined}>{n.tocke2 ?? '?'}</span>
        </span>
      ))}
    </p>
  )
}

/* Nad obrazcem v oknu: fotografija (dotik jo poveča) in opozorila bralnika. */
function PregledListka({
  branje,
  tekma,
  predlog,
  predogled,
}: {
  branje: BranjeListkaDto
  tekma: TekmaListkaDto
  predlog: PredlogIzidaDto | null
  predogled: string | null
}) {
  const [povecano, nastaviPovecano] = useState(false)
  return (
    <div className="branje-pregled">
      <p className="branje-pregled__kategorija">{opisTekme(tekma)}</p>
      {(branje.imaSliko || predogled) && (
        <button
          type="button"
          className={'branje-pregled__slika' + (povecano ? ' branje-pregled__slika--povecano' : '')}
          aria-label={povecano ? 'Pomanjšaj fotografijo' : 'Povečaj fotografijo'}
          onClick={() => nastaviPovecano((p) => !p)}
        >
          <Fotografija url={predogled} id={branje.imaSliko ? branje.id : undefined} />
        </button>
      )}
      {branje.stanje === 'NAPAKA' && branje.napaka && (
        <p className="napaka">{branje.napaka} Izid vpiši ročno s fotografije.</p>
      )}
      {predlog && predlog.opozorila.length > 0 && (
        <ul className="branje-pregled__opozorila">
          {predlog.opozorila.map((o) => (
            <li key={o}>{o}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* Majhna slika v vrsti. */
function Slicica({ url, id }: { url: string | null; id?: number }) {
  return (
    <span className="branje-vrsta__slicica">
      <Fotografija url={url} id={id} />
    </span>
  )
}

/* En naslov na sliko: sličica v vrsti in fotografija v oknu delita isti Blob iz
   predpomnilnika poizvedb. Naslov se ne prekliče (fotografij je v seji nekaj
   deset), ker bi ga preklic ob ponovnem priklopu komponente pokvaril. */
const naslovi = new WeakMap<Blob, string>()
function naslovSlike(blob: Blob): string {
  let n = naslovi.get(blob)
  if (!n) {
    n = URL.createObjectURL(blob)
    naslovi.set(blob, n)
  }
  return n
}

/* Fotografija listka: lokalna (pravkar posneta) ali s strežnika. Slika gre prek
   odjemalca, ker <img src> ne pošlje glave Authorization. */
function Fotografija({ url, id }: { url: string | null; id?: number }) {
  const slika = useQuery({
    queryKey: ['branje-slika', id],
    queryFn: () => branjeListkovApi.slika(id!),
    enabled: !url && id !== undefined,
    staleTime: Infinity,
  })
  const vir = url ?? (slika.data ? naslovSlike(slika.data) : null)
  return vir ? <img src={vir} alt="Fotografija listka" /> : <span className="branje-vrsta__brez-slike" />
}

function imeTekme(t: TekmaListkaDto): string {
  return `${imeUdelezenca(t.tekma.udelezenec1) ?? '?'} – ${imeUdelezenca(t.tekma.udelezenec2) ?? '?'}`
}

function opisTekme(t: TekmaListkaDto): string {
  const del = t.oznakaSkupine
    ? `Skupina ${t.oznakaSkupine}`
    : t.tekma.faza === 'TOLAZILNI'
      ? 'Tolažilni del'
      : t.tekma.faza === 'GLAVNI'
        ? 'Izločilni del'
        : null
  return del ? `${t.imeDogodka} · ${del}` : t.imeDogodka
}

function izidTekme(t: TekmaListkaDto | null): string {
  if (!t || t.tekma.status !== 'KONCANA') return 'potrjeno'
  return `${t.tekma.dobljeniNizi1} : ${t.tekma.dobljeniNizi2}`
}
