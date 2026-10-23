/* Karton računa (stran »Dostopi«): vse o enem računu in vsa dejanja nad njim.
   Isti karton stoji v desni ploskvi kartoteke, ob nabiralniku in v listu na
   telefonu — zato ne ve, kje je: obvestilo po dejanju dobi od strani, ker mora
   preživeti zamenjavo računa (potrjena zahteva izgine iz nabiralnika, karton pa
   pokaže naslednjo in na njenem vrhu obvestilo o prejšnji).

   Karton se ob zamenjavi računa (ali spremembi njegovega stanja) izriše znova —
   zato urejevalnik gesla, potrditev, iskanje igralca in predizbira ne
   puščajo sledi iz prejšnjega računa (`key` določi stran).

   Potrditve nepovratnih dejanj so vrinjene v karton, ne modalno okno
   (odločitev iz predaje, DESIGN.md »Nabiralnik + kartoteka«). Gesla ni mogoče
   prebrati — shranjena je le zgostitev —, zato ga admin nastavi (ali
   generira) in ga vidi samo enkrat, v tem kartonu. */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, racuniApi } from '../api/zahteve'
import type { RacunIgralcaDto } from '../api/tipi'
import {
  datum,
  dniDo,
  imeNarocnine,
  imeRacuna,
  jeCakajoca,
  oznakaVloge,
  rel,
  stanjeRacuna,
  ura,
} from '../pomozno/dostopi'
import { besedeIskanja, ustrezaBesedam, zadetihZacetkov } from '../pomozno/iskanje'
import { IzbirnikKluba } from './IzbirnikKluba'
import { SporociloNapake } from './SporociloNapake'

/* Največ zadetkov pod poljem za iskanje igralca. */
const NAJVEC_ZADETKOV = 5
const NAJMANJ_ZNAKOV_GESLA = 8

type Potrditev = 'zavrni' | 'razvezi' | 'izbrisi'

interface VrsticaPodatka {
  oznaka: string
  vrednost: string
  opozorilo?: boolean
}

interface SkupinaPodatkov {
  naslov: string
  vrstice: VrsticaPodatka[]
}

export function DostopiKarton({
  racun,
  obvestilo,
  naObvestilo,
  igralciZRacunom,
}: {
  racun: RacunIgralcaDto
  /* Besedilo zadnjega dejanja (»Dostop potrjen: …«); drži ga stran. */
  obvestilo: string | null
  naObvestilo: (besedilo: string) => void
  /* Igralci, ki račun že imajo (zadetki iskanja jih ne smejo ponuditi). */
  igralciZRacunom: Set<number>
}) {
  const odjemalec = useQueryClient()
  const zahteva = jeCakajoca(racun)
  const jeOrganizator = racun.vloga === 'ORGANIZATOR'
  const jeIgralec = racun.vloga === 'IGRALEC'
  const jeAdmin = racun.vloga === 'ADMIN'
  const brezKode = racun.status === 'CAKA' && !zahteva
  /* Dejanja računa: potrjen, zavrnjen ali izklopljen račun osebe. Adminov
     račun je samo za branje: strežnik ga ne ureja, izklop lastnega računa pa
     bi bil zaklep. */
  const dejanja = racun.status !== 'CAKA' && !jeAdmin
  const stanje = stanjeRacuna(racun)
  const ime = imeRacuna(racun)

  const [potrditev, nastaviPotrditev] = useState<Potrditev | null>(null)
  const [gesloUrejanje, nastaviGesloUrejanje] = useState(false)
  const [gesloVnos, nastaviGesloVnos] = useState('')
  const [novoGeslo, nastaviNovoGeslo] = useState<string | null>(null)

  /* Predizbran je prvi predlog brez računa; igralec, ki ga admin najde z
     iskanjem, stoji potem na vrhu seznama kot izbrani. */
  const [izbraniIgralec, nastaviIzbranegaIgralca] = useState<number | null>(
    () => racun.predlogi.find((p) => !p.zeImaRacun)?.idIgralec ?? null,
  )
  const [iskanjeIgralca, nastaviIskanjeIgralca] = useState('')
  const [izbraniKlub, nastaviIzbranegaKluba] = useState<number | null>(null)

  const igralci = useQuery({
    queryKey: ['igralci'],
    queryFn: igralciApi.seznam,
    enabled: zahteva && jeIgralec,
  })
  const klubi = useQuery({
    queryKey: ['klubi'],
    queryFn: klubiApi.seznam,
    enabled: zahteva && jeOrganizator,
  })

  /* Predizberi klub, ki ga je organizator navedel ob registraciji (DTO nosi le
     ime). Samo enkrat, ko se klubi naložijo: sicer bi admin, ki polje izprazni
     (brez kluba), navedeni klub takoj dobil nazaj. */
  const predizbrano = useRef(false)
  useEffect(() => {
    if (predizbrano.current || !klubi.data) return
    predizbrano.current = true
    const najden = klubi.data.find((k) => k.ime === racun.klubZelja)
    if (najden) nastaviIzbranegaKluba(najden.id)
  }, [klubi.data, racun.klubZelja])

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['racuni'] })

  const potrdi = useMutation({
    mutationFn: () =>
      jeOrganizator
        ? racuniApi.potrdiOrganizatorja(racun.id, izbraniKlub)
        : racuniApi.potrdi(racun.id, izbraniIgralec as number),
    onSuccess: () => {
      naObvestilo(`Dostop potrjen: ${ime}`)
      nastaviPotrditev(null)
      osvezi()
    },
  })
  const zavrni = useMutation({
    mutationFn: () => racuniApi.zavrni(racun.id),
    onSuccess: () => {
      naObvestilo(`Zahteva zavrnjena: ${ime}`)
      nastaviPotrditev(null)
      osvezi()
    },
  })
  const razvezi = useMutation({
    mutationFn: () => racuniApi.razvezi(racun.id),
    onSuccess: () => {
      naObvestilo('Račun je spet med čakajočimi.')
      nastaviPotrditev(null)
      osvezi()
    },
  })
  const izbrisi = useMutation({
    mutationFn: () => racuniApi.izbrisi(racun.id),
    onSuccess: () => {
      nastaviPotrditev(null)
      osvezi()
    },
  })
  const preklop = useMutation({
    mutationFn: () => racuniApi.nastaviAktiven(racun.id, !racun.aktiven),
    onSuccess: osvezi,
  })
  const nastaviGeslo = useMutation({
    mutationFn: () => racuniApi.nastaviGeslo(racun.id, gesloVnos),
    onSuccess: () => {
      nastaviNovoGeslo(gesloVnos)
      nastaviGesloVnos('')
      nastaviGesloUrejanje(false)
      osvezi()
    },
  })
  const generiraj = useMutation({
    mutationFn: () => racuniApi.ponastaviGeslo(racun.id),
    onSuccess: (odgovor) => {
      nastaviNovoGeslo(odgovor.geslo)
      nastaviGesloVnos('')
      nastaviGesloUrejanje(false)
      osvezi()
    },
  })

  /* ---------- Povezava z igralcem (zahteva igralca) ---------- */

  const idjiPredlogov = useMemo(() => new Set(racun.predlogi.map((p) => p.idIgralec)), [racun.predlogi])
  const poId = useMemo(() => new Map((igralci.data ?? []).map((i) => [i.id, i])), [igralci.data])

  interface Izbira {
    id: number
    ime: string
    podrobnost: string
    onemogocena: boolean
  }

  const predlogi: Izbira[] = racun.predlogi.map((p) => ({
    id: p.idIgralec,
    ime: p.polnoIme,
    podrobnost: [p.klub, p.zeImaRacun ? 'že ima dostop' : 'predlog po priimku'].filter(Boolean).join(' · '),
    onemogocena: p.zeImaRacun,
  }))

  const tuj = izbraniIgralec !== null && !idjiPredlogov.has(izbraniIgralec) ? poId.get(izbraniIgralec) : undefined
  const izbire: Izbira[] = tuj
    ? [{ id: tuj.id, ime: `${tuj.ime} ${tuj.priimek}`, podrobnost: tuj.klub?.ime ?? '', onemogocena: false }, ...predlogi]
    : predlogi

  const besede = useMemo(() => besedeIskanja(iskanjeIgralca), [iskanjeIgralca])
  const zadetki: Izbira[] = useMemo(() => {
    if (besede.length === 0 || !zahteva || !jeIgralec) return []
    return (igralci.data ?? [])
      .filter((i) => !idjiPredlogov.has(i.id))
      .map((i) => ({ i, ime: `${i.ime} ${i.priimek}` }))
      .filter(({ ime: polno }) => ustrezaBesedam(polno, besede))
      // zadetki na začetku besede naprej (»ana« najprej Ana, nato Jana), sicer vrstni red seznama
      .map((z, indeks) => ({ ...z, indeks, tocke: zadetihZacetkov(z.ime, besede) }))
      .sort((a, b) => b.tocke - a.tocke || a.indeks - b.indeks)
      .slice(0, NAJVEC_ZADETKOV)
      .map(({ i, ime: polno }) => ({
        id: i.id,
        ime: polno,
        podrobnost: [i.klub?.ime, igralciZRacunom.has(i.id) ? 'že ima dostop' : null].filter(Boolean).join(' · '),
        onemogocena: igralciZRacunom.has(i.id),
      }))
  }, [besede, igralci.data, idjiPredlogov, igralciZRacunom, zahteva, jeIgralec])

  const jeGotov = jeOrganizator || izbraniIgralec !== null
  const prekratkoGeslo = gesloVnos.trim().length < NAJMANJ_ZNAKOV_GESLA

  const izberi = (i: Izbira, izpraznitiIskanje: boolean) => {
    if (i.onemogocena) return
    nastaviIzbranegaIgralca(i.id)
    if (izpraznitiIskanje) nastaviIskanjeIgralca('')
  }

  const namig = jeOrganizator
    ? 'Brez kluba upravlja samo tekmovanja, ki jih ustvari sam.'
    : racun.predlogi.length === 0
      ? 'Ni ujemanja po priimku. Poišči igralca z imenom.'
      : 'Samodejna povezava ni nastala. Izberi pravega.'

  const opisPotrditve =
    potrditev === 'zavrni'
      ? `Zavrnem zahtevo za ${racun.email}? Račun ostane (da se ista e-pošta ne registrira znova), a brez dostopa.`
      : potrditev === 'razvezi'
        ? `Razvežem račun ${racun.email} od igralca ${racun.imeIgralca}? Račun se vrne med čakajoče.`
        : brezKode
          ? `Izbrišem registracijo ${racun.email}? Naslov se sprosti takoj.`
          : `Izbrišem račun ${racun.email}? E-pošta se sprosti za novo registracijo. Igralec in njegove tekme ostanejo.`
  const besedaPotrditve = potrditev === 'zavrni' ? 'Zavrni' : potrditev === 'razvezi' ? 'Razveži' : 'Izbriši'
  const izvediPotrditev = () => {
    if (potrditev === 'zavrni') zavrni.mutate()
    else if (potrditev === 'razvezi') razvezi.mutate()
    else if (potrditev === 'izbrisi') izbrisi.mutate()
  }

  const skupine = skupinePodatkov(racun)

  return (
    <div className="dostopi-karton" data-dostopi="karton">
      <div>
        <div className="dostopi-karton__oznake">
          <span className={`dostopi-karton__vloga dostopi-karton__vloga--${racun.vloga}`}>
            {oznakaVloge(racun.vloga)}
          </span>
          <span className={`dostopi-znacka dostopi-znacka--velika dostopi-znacka--${stanje.kljuc}`}>
            {stanje.besedilo}
          </span>
        </div>
        <h3 className="dostopi-karton__ime">{ime}</h3>
        <div className="dostopi-karton__email">{racun.email}</div>
      </div>

      {obvestilo && (
        <div className="dostopi-karton__obvestilo" role="status">
          {obvestilo}
        </div>
      )}

      {zahteva && (
        <div className="dostopi-karton__zahteva">
          <div className="dostopi-karton__zahteva-oznaka">
            {jeOrganizator ? 'Klub organizatorja' : 'Poveži z igralcem'}
          </div>
          <div className="dostopi-karton__namig">{namig}</div>

          {jeOrganizator && (
            <IzbirnikKluba
              vObrazcu={false}
              oznaka="Klub organizatorja"
              namig="Brez kluba · vpiši ime"
              klubi={klubi.data ?? []}
              izbrano={izbraniKlub}
              naSpremembo={nastaviIzbranegaKluba}
            />
          )}

          {jeIgralec && (
            <div className="dostopi-izbire">
              {izbire.map((i) => (
                <IzbirnaVrstica
                  key={i.id}
                  izbira={i}
                  izbrana={izbraniIgralec === i.id}
                  onKlik={() => izberi(i, false)}
                />
              ))}
              <input
                type="text"
                className="dostopi-izbire__iskanje"
                value={iskanjeIgralca}
                placeholder="Poišči med vsemi igralci"
                aria-label="Poišči med vsemi igralci"
                autoComplete="off"
                onChange={(d) => nastaviIskanjeIgralca(d.target.value)}
              />
              {zadetki.map((i) => (
                <IzbirnaVrstica
                  key={i.id}
                  izbira={i}
                  izbrana={izbraniIgralec === i.id}
                  onKlik={() => izberi(i, true)}
                />
              ))}
            </div>
          )}

          <div className="dostopi-karton__gumbi">
            <button
              type="button"
              className="dostopi-gumb dostopi-gumb--glavni dostopi-gumb--raztegljiv"
              disabled={!jeGotov || potrdi.isPending}
              onClick={() => potrdi.mutate()}
            >
              {jeOrganizator ? 'Potrdi organizatorja' : 'Potrdi dostop'}
            </button>
            <button
              type="button"
              className="dostopi-gumb dostopi-gumb--zavrni"
              onClick={() => nastaviPotrditev('zavrni')}
            >
              Zavrni
            </button>
          </div>
          <SporociloNapake napaka={potrdi.error} />
        </div>
      )}

      {brezKode && (
        <div className="dostopi-karton__brez-kode">
          <div className="dostopi-karton__namig dostopi-karton__namig--temno">
            {racun.emailPotrjen
              ? 'Čaka kodo skrbnika. Izbriše se samodejno po 30 dneh.'
              : 'Lastnik še ni vpisal kode. Izbriše se samodejno po 48 urah.'}
          </div>
          <div className="dostopi-karton__gumbi">
            <button
              type="button"
              className="dostopi-gumb dostopi-gumb--zavrni"
              onClick={() => nastaviPotrditev('izbrisi')}
            >
              Izbriši registracijo
            </button>
          </div>
        </div>
      )}

      {dejanja && (
        <div className="dostopi-karton__dejanja">
          <div className="dostopi-karton__mreza-dejanj">
            <button type="button" className="dostopi-gumb dostopi-gumb--okvir" onClick={() => preklop.mutate()}>
              {racun.aktiven ? 'Izklopi' : 'Vklopi'}
            </button>
            <button
              type="button"
              className={'dostopi-gumb dostopi-gumb--okvir' + (gesloUrejanje ? ' dostopi-gumb--vklopljen' : '')}
              aria-expanded={gesloUrejanje}
              onClick={() => {
                nastaviGesloUrejanje((v) => !v)
                nastaviNovoGeslo(null)
                nastaviGesloVnos('')
              }}
            >
              {gesloUrejanje ? 'Prekliči' : 'Nastavi geslo'}
            </button>
            {jeIgralec && racun.idIgralec !== null && (
              <button
                type="button"
                className="dostopi-gumb dostopi-gumb--tiho"
                onClick={() => nastaviPotrditev('razvezi')}
              >
                Razveži od igralca
              </button>
            )}
            <button
              type="button"
              className="dostopi-gumb dostopi-gumb--nevarno"
              onClick={() => nastaviPotrditev('izbrisi')}
            >
              Izbriši račun
            </button>
          </div>

          {gesloUrejanje && (
            <div className="dostopi-karton__geslo-ured">
              <input
                type="text"
                value={gesloVnos}
                placeholder="novo geslo (vsaj 8 znakov)"
                aria-label="Novo geslo"
                autoComplete="off"
                onChange={(d) => nastaviGesloVnos(d.target.value)}
                onKeyDown={(d) => {
                  if (d.key === 'Enter' && !prekratkoGeslo) nastaviGeslo.mutate()
                }}
              />
              <div className="dostopi-karton__gumbi">
                <button
                  type="button"
                  className="dostopi-gumb dostopi-gumb--glavni dostopi-gumb--manjsi"
                  disabled={prekratkoGeslo || nastaviGeslo.isPending}
                  onClick={() => nastaviGeslo.mutate()}
                >
                  Nastavi
                </button>
                <button
                  type="button"
                  className="dostopi-gumb dostopi-gumb--okvir dostopi-gumb--manjsi"
                  disabled={generiraj.isPending}
                  onClick={() => generiraj.mutate()}
                >
                  Generiraj naključno
                </button>
              </div>
            </div>
          )}

          {novoGeslo && (
            <div className="dostopi-karton__novo-geslo">
              Geslo: <strong>{novoGeslo}</strong>
              <span>Izroči ga lastniku, kasneje ga ni več mogoče prebrati.</span>
            </div>
          )}
          <SporociloNapake napaka={preklop.error ?? nastaviGeslo.error ?? generiraj.error ?? razvezi.error} />
        </div>
      )}

      {potrditev && (
        <div className="dostopi-karton__potrditev">
          <div className="dostopi-karton__potrditev-besedilo">{opisPotrditve}</div>
          <div className="dostopi-karton__potrditev-gumbi">
            <button type="button" className="dostopi-gumb dostopi-gumb--potrdi" onClick={izvediPotrditev}>
              {besedaPotrditve}
            </button>
            <button type="button" className="dostopi-gumb dostopi-gumb--preklici" onClick={() => nastaviPotrditev(null)}>
              Prekliči
            </button>
          </div>
          <SporociloNapake napaka={zavrni.error ?? razvezi.error ?? izbrisi.error} />
        </div>
      )}

      {skupine.map((s) => (
        <div key={s.naslov}>
          <div className="dostopi-skupina__naslov">{s.naslov}</div>
          {s.vrstice.map((v) => (
            <div key={v.oznaka} className="dostopi-skupina__vrstica">
              <span className="dostopi-skupina__oznaka">{v.oznaka}</span>
              <span
                className={
                  'dostopi-skupina__vrednost' + (v.opozorilo ? ' dostopi-skupina__vrednost--opozorilo' : '')
                }
              >
                {v.vrednost}
              </span>
            </div>
          ))}
        </div>
      ))}

      {racun.poraba && (
        <div>
          <div className="dostopi-skupina__naslov">Poraba paketa</div>
          {[
            { oznaka: 'Tekoče lige', kvota: racun.poraba.lige },
            { oznaka: 'Turnirji v sezoni', kvota: racun.poraba.turnirji },
          ].map(({ oznaka, kvota }) => (
            <div key={oznaka} className="dostopi-poraba">
              <div className="dostopi-poraba__glava">
                <span>{oznaka}</span>
                <span className="dostopi-poraba__stevilo">
                  {kvota.uporabljeno} / {kvota.najvec}
                  {kvota.uporabljeno >= kvota.najvec ? ' · polno' : ''}
                </span>
              </div>
              <div className="dostopi-palica">
                <div
                  className="dostopi-palica__polnilo"
                  style={{ width: `${Math.round((kvota.uporabljeno / (kvota.najvec || 1)) * 100)}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      {racun.dnevnik.length > 0 && (
        <div>
          <div className="dostopi-skupina__naslov">Dnevnik</div>
          {racun.dnevnik.slice(0, 8).map((d, i) => (
            <div key={`${d.ob}-${i}`} className="dostopi-dnevnik__vrstica">
              <span className="dostopi-dnevnik__ob">{datum(d.ob)}</span>
              <span className="dostopi-dnevnik__tekst">{d.tekst}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function IzbirnaVrstica({
  izbira,
  izbrana,
  onKlik,
}: {
  izbira: { ime: string; podrobnost: string; onemogocena: boolean }
  izbrana: boolean
  onKlik: () => void
}) {
  return (
    <button
      type="button"
      className={
        'dostopi-izbira' +
        (izbrana ? ' dostopi-izbira--izbrana' : '') +
        (izbira.onemogocena ? ' dostopi-izbira--onemogocena' : '')
      }
      aria-pressed={izbrana}
      aria-disabled={izbira.onemogocena || undefined}
      onClick={onKlik}
    >
      <span className="dostopi-izbira__kvadratek" />
      <span className="dostopi-izbira__besedilo">
        <span className="dostopi-izbira__ime">{izbira.ime}</span>
        <span className="dostopi-izbira__podrobnost">{izbira.podrobnost}</span>
      </span>
    </button>
  )
}

/* Podatkovne skupine kartona; kar račun ne pove, odpade (vrstica ali cela
   skupina), nikoli ne stoji prazna ali s ničlo, ki bi lagala. */
function skupinePodatkov(r: RacunIgralcaDto): SkupinaPodatkov[] {
  const skupine: SkupinaPodatkov[] = []
  const racun: VrsticaPodatka[] = [
    { oznaka: 'Vloga', vrednost: oznakaVloge(r.vloga) },
    { oznaka: 'Registriran', vrednost: `${datum(r.ustvarjenOb)} · ${rel(r.ustvarjenOb)}` },
  ]
  /* Naslov administratorja ni prijavljen s kodo: vrstice »ni potrjena« ne kaže. */
  if (r.vloga !== 'ADMIN') {
    racun.push({ oznaka: 'E-pošta', vrednost: r.emailPotrjen ? 'potrjena' : 'ni potrjena', opozorilo: !r.emailPotrjen })
  }
  if (r.skrbnikPotrjen !== null) {
    racun.push({
      oznaka: 'Skrbnik',
      vrednost: r.skrbnikPotrjen ? 'soglasje dano' : 'čaka kodo',
      opozorilo: !r.skrbnikPotrjen,
    })
  }
  if (r.prijavljeniDatumRojstva && r.vloga === 'IGRALEC') {
    racun.push({ oznaka: 'Rojen', vrednost: datum(r.prijavljeniDatumRojstva) })
  }
  if (r.klubZelja && !r.klub) racun.push({ oznaka: 'Navedeni klub', vrednost: r.klubZelja })
  skupine.push({ naslov: 'Račun', vrstice: racun })

  if (r.vloga !== 'ADMIN') {
    const n = r.narocnina
    const vrstice: VrsticaPodatka[] = [
      { oznaka: 'Paket', vrednost: imeNarocnine(n), opozorilo: n.paket === null },
    ]
    if (n.paket && n.paket !== 'BREZPLACNO') {
      const stanjeNarocnine =
        n.status === 'AKTIVNA'
          ? 'aktivna'
          : n.status === 'PREKLICANA'
            ? n.veljavna
              ? 'preklicana, velja do konca'
              : 'potekla'
            : n.status === 'ZAPADLA'
              ? 'zapadla'
              : n.status === 'CAKA_PLACILO'
                ? 'čaka plačilo'
                : n.status === 'PREIZKUS'
                  ? n.veljavna
                    ? 'preizkus, brez plačila'
                    : 'preizkus potekel'
                  : '—'
      vrstice.push(
        { oznaka: 'Obračun', vrednost: n.ciklus === 'LETNO' ? 'letno' : n.ciklus === 'MESECNO' ? 'mesečno' : '—' },
        { oznaka: 'Stanje', vrednost: stanjeNarocnine, opozorilo: n.status === 'ZAPADLA' || !n.veljavna },
      )
      if (n.obdobjeDo) {
        const dni = dniDo(n.obdobjeDo)
        vrstice.push({
          oznaka: 'Velja do',
          vrednost: `${datum(n.obdobjeDo)}${dni >= 0 ? ` · čez ${dni} d` : ` · pred ${-dni} d`}`,
          opozorilo: dni <= 30,
        })
      }
    }
    skupine.push({ naslov: 'Naročnina', vrstice })
  }

  if (r.status !== 'CAKA' && r.vloga === 'IGRALEC') {
    skupine.push({
      naslov: 'Povezava',
      vrstice: [
        { oznaka: 'Igralec', vrednost: r.imeIgralca ?? '—' },
        { oznaka: 'Klub', vrednost: r.klub ?? '—' },
        {
          oznaka: 'Povezal',
          vrednost: r.virPovezave === 'SAMODEJNO' ? 'samodejno' : r.virPovezave === 'ADMIN' ? 'administrator' : '—',
        },
        { oznaka: 'Povezan', vrednost: datum(r.povezanOb) },
      ],
    })
  }
  if (r.status !== 'CAKA' && r.vloga === 'ORGANIZATOR') {
    skupine.push({
      naslov: 'Klub',
      vrstice: [
        { oznaka: 'Klub', vrednost: r.klub ?? 'brez kluba' },
        { oznaka: 'Potrjen', vrednost: datum(r.povezanOb) },
      ],
    })
  }
  if (r.status === 'POTRJEN') {
    skupine.push({
      naslov: 'Aktivnost',
      vrstice: [
        {
          oznaka: 'Zadnja prijava',
          vrednost: r.zadnjaPrijava ? `${ura(r.zadnjaPrijava)} · ${rel(r.zadnjaPrijava)}` : 'nikoli',
        },
        { oznaka: 'Prijav v 30 dneh', vrednost: String(r.prijavNaZadnjih30Dni) },
      ],
    })
  }
  return skupine
}
