/* Stran »Mize« (/turnirji/:id/mize; M1, M2; docs/pravila/mize.md): organizator
   na prenosniku na pultu kliče tekme na mize. Igralce pokliče na glas, aplikacija
   klic samo zabeleži (odločitev lastnika, 23. 11. 2026).

   Levo so mize: zasedena pove, kdo igra, kaj in koliko časa, in kdo sodi; prosta
   pokaže predlog naslednje tekme z razlogi in gumb »Pokliči«. Desno so vse
   pripravljene tekme v vrstnem redu predloga. Kaj je prosto in v kakšnem vrstnem
   redu, odloči strežnik (PredlogTekem); stran se osveži vsake 4 s in ob sprostitvi
   mize kratko pisne. Izid se vpiše v istem oknu kot drugod (VnosRezultataOkno),
   vpis pa mizo sprosti sam. */
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, OsebaMizeDto, TekmaMizeDto } from '../api/tipi'
import { mizeApi } from '../api/zahteve'
import { useNazaj } from '../komponente/GlavaTelefona'
import { IskalniIzbirnik } from '../komponente/IskalniIzbirnik'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { StevilskoPolje } from '../komponente/StevilskoPolje'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import { Namig } from '../pomoc/Namig'
import {
  imenaTekme,
  minutOd,
  mizeTekme,
  opisTekme,
  predMinutami,
  privzetiSodnik,
  prosteMize,
  prviPredlogi,
  sprosceneMize,
} from '../pomozno/mize'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'

/* Osvežitev, dokler je stran vidna (skrit zavihek ne sprašuje). */
const OSVEZITEV_MS = 4000
/* Kako dolgo je sproščena miza poudarjena. */
const POUDAREK_MS = 30_000
const KLJUC_PISKA = 'turnirko-mize-pisk'

/* Odprto okno klica: tekma in miza (pri spremembi klica je tekma že na mizi). */
interface Klic {
  tekma: TekmaMizeDto
  miza: number | null
  sprememba: boolean
}

export function MizeStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()
  const jeTelefon = useTelefon()
  useNaslovStrani('Mize')
  useNazaj(`/turnirji/${idTurnirja}`, 'Turnir')

  const kljuc = ['turnir', idTurnirja, 'mize']
  const pregled = useQuery({
    queryKey: kljuc,
    queryFn: () => mizeApi.pregled(idTurnirja),
    refetchInterval: OSVEZITEV_MS,
  })

  const [klic, nastaviKlic] = useState<Klic | null>(null)
  const [izbiraZa, nastaviIzbiraZa] = useState<MizaDto | null>(null)
  const [vnosIzida, nastaviVnosIzida] = useState<TekmaMizeDto | null>(null)
  const [preklic, nastaviPreklic] = useState<TekmaMizeDto | null>(null)
  const [vsiPredlogi, nastaviVsePredloge] = useState(false)
  const [nastavitveOdprte, nastaviNastavitveOdprte] = useState(false)

  const osvezi = useCallback(() => {
    void odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja] })
    void odjemalec.invalidateQueries({ queryKey: ['dogodek'] })
  }, [odjemalec, idTurnirja])

  /* Sproščene mize: pisk in poudarek. Primerjava s prejšnjo osvežitvijo, zato
     stran ne piska, ko se odpre. Pisk sledi tudi organizatorjevemu vnosu izida:
     zvok pove, da je miza prosta, ne kdo jo je sprostil. */
  const [pisk, nastaviPisk] = useState(() => beriPisk())
  const piskni = usePisk()
  const prejsnje = useRef<MizaDto[] | undefined>(undefined)
  const [poudarjene, nastaviPoudarjene] = useState<Map<number, number>>(new Map())
  useEffect(() => {
    const zdaj = pregled.data?.mize
    if (!zdaj) return
    const sproscene = sprosceneMize(prejsnje.current, zdaj)
    prejsnje.current = zdaj
    if (sproscene.length === 0) return
    if (pisk) piskni()
    const do_ = Date.now() + POUDAREK_MS
    nastaviPoudarjene((p) => {
      const nov = new Map(p)
      sproscene.forEach((st) => nov.set(st, do_))
      return nov
    })
  }, [pregled.data, pisk, piskni])
  useEffect(() => {
    if (poudarjene.size === 0) return
    const casovnik = window.setTimeout(() => {
      const zdaj = Date.now()
      nastaviPoudarjene((p) => new Map([...p].filter(([, doKdaj]) => doKdaj > zdaj)))
    }, 2000)
    return () => window.clearTimeout(casovnik)
  }, [poudarjene])

  const izklop = useMutation({
    mutationFn: ({ podatki, miza, izklopi }: { podatki: MizeTurnirjaDto; miza: number; izklopi: boolean }) =>
      mizeApi.nastavi(idTurnirja, {
        steviloMiz: podatki.steviloMiz,
        izklopljeneMize: izklopi
          ? [...podatki.izklopljeneMize, miza]
          : podatki.izklopljeneMize.filter((m) => m !== miza),
        porazenecSodi: podatki.porazenecSodi,
        pocitekMin: podatki.pocitekMin,
      }),
    onSuccess: osvezi,
  })
  const preklicKlica = useMutation({
    mutationFn: (t: TekmaMizeDto) => mizeApi.preklici(t.tekma.id),
    onSuccess: osvezi,
  })
  /* »Pokliči predloge«: zaporedni klici prvih predlogov prostih miz (vsak svoja
     preverba na strežniku); ob prvi napaki se ustavi in jo pokaže. Pred klici se
     stanje prebere znova: pokliče se samo par, ki je še vedno prvi predlog svoje
     mize (medtem je lahko drug organizator koga poklical drugam). */
  const klicVseh = useMutation({
    mutationFn: async (potrjeni: [MizaDto, KandidatMizeDto][]) => {
      const sveze = await mizeApi.pregled(idTurnirja)
      const pari = prviPredlogi(sveze.mize, sveze.kandidati).filter(([m, k]) =>
        potrjeni.some(([pm, pk]) => pm.stevilka === m.stevilka && pk.tekma.tekma.id === k.tekma.tekma.id))
      for (const [miza, k] of pari) {
        await mizeApi.poklici(k.tekma.tekma.id, {
          miza: miza.stevilka,
          miza2: null,
          idSodnika: privzetiSodnik(miza, k.tekma)?.id ?? null,
        })
      }
    },
    onSettled: osvezi,
  })

  if (pregled.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (!pregled.data) return <NapakaPoizvedbe poizvedba={pregled} kaj="miz" />
  const podatki = pregled.data
  const kandidatPoId = new Map(podatki.kandidati.map((k) => [k.tekma.tekma.id, k]))
  const proste = prosteMize(podatki.mize)
  const vIgri = podatki.mize.filter((m) => m.tekma !== null).length
  const pari = prviPredlogi(podatki.mize, podatki.kandidati)
  const nastavljene = podatki.steviloMiz !== null

  return (
    <section className="mize">
      {!jeTelefon && (
        <Link to={`/turnirji/${idTurnirja}`} className="povezava-nazaj">
          ← Nazaj na turnir
        </Link>
      )}
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">
            Mize <Namig clanek="mize" />
          </h1>
          <p className="uvod uvod--tesno">{podatki.imeTurnirja}</p>
        </div>
        {nastavljene && (
          <div className="kolofon">
            <Kolofon oznaka="Proste mize" vrednost={String(proste.length)} />
            <Kolofon oznaka="V igri" vrednost={String(vIgri)} />
            <Kolofon oznaka="Pripravljene tekme" vrednost={String(podatki.kandidati.length)} />
          </div>
        )}
      </div>
      <NapakaPoizvedbe poizvedba={pregled} kaj="miz" />

      {!nastavljene || nastavitveOdprte ? (
        <NastavitveMiz
          podatki={podatki}
          prvic={!nastavljene}
          onShranjeno={() => {
            nastaviNastavitveOdprte(false)
            osvezi()
          }}
          onZapri={nastavljene ? () => nastaviNastavitveOdprte(false) : undefined}
        />
      ) : (
        <div className="mize__orodja">
          <p className="mize__povzetek">
            {podatki.steviloMiz} {mizSklon(podatki.steviloMiz!)}
            {podatki.izklopljeneMize.length > 0 && ` · izklopljene ${podatki.izklopljeneMize.join(', ')}`}
            {' · '}
            {podatki.porazenecSodi ? 'poraženec sodi naslednjo tekmo' : 'sodnika izbereš ob klicu'}
            {` · počitek ${podatki.pocitekMin} min`}
          </p>
          <div className="mize__orodja-desno">
            <label className="obrazec__polje obrazec__polje--stikalo">
              <input
                type="checkbox"
                checked={pisk}
                onChange={(d) => {
                  nastaviPisk(d.target.checked)
                  zapisiPisk(d.target.checked)
                }}
              />
              Pisk ob prosti mizi
            </label>
            <button type="button" className="gumb gumb--majhen" onClick={() => nastaviNastavitveOdprte(true)}>
              Nastavitve miz
            </button>
          </div>
        </div>
      )}

      {nastavljene && !podatki.dogodkiVTeku && (
        <p className="namig">
          Nobena kategorija tega turnirja še ne teče. Ko izžrebaš kategorijo, se tu pokažejo
          pripravljene tekme.
        </p>
      )}

      {nastavljene && (
        <div className="mize__postavitev">
          <div className="mize__stolpec">
            <div className="naslovna-vrstica mize__glava">
              <h2 className="sekcija__naslov--manjsi">Mize</h2>
              {pari.length > 1 && (
                <div className="naslovna-vrstica__desno">
                  <button
                    type="button"
                    className="gumb gumb--glavni"
                    disabled={klicVseh.isPending}
                    onClick={() => nastaviVsePredloge(true)}
                  >
                    Pokliči predloge ({pari.length})
                  </button>
                </div>
              )}
            </div>
            <SporociloNapake napaka={klicVseh.error ?? izklop.error ?? preklicKlica.error} />
            <ol className="mize__seznam">
              {podatki.mize.map((m) => (
                <VrsticaMize
                  key={m.stevilka}
                  miza={m}
                  podatki={podatki}
                  kandidatPoId={kandidatPoId}
                  poudarjena={poudarjene.has(m.stevilka)}
                  onPoklici={(t) => nastaviKlic({ tekma: t, miza: m.stevilka, sprememba: false })}
                  onDruga={() => nastaviIzbiraZa(m)}
                  onVnosIzida={nastaviVnosIzida}
                  onSprememba={(t) => nastaviKlic({ tekma: t, miza: t.miza, sprememba: true })}
                  onPreklic={nastaviPreklic}
                  onIzklop={(izklopi) => izklop.mutate({ podatki, miza: m.stevilka, izklopi })}
                />
              ))}
            </ol>
          </div>

          <aside className="mize__stolpec">
            <div className="naslovna-vrstica mize__glava">
              <h2 className="sekcija__naslov--manjsi">Pripravljene</h2>
              <span className="mize__stevec">{podatki.kandidati.length}</span>
            </div>
            {podatki.kandidati.length === 0 ? (
              <p className="namig">Ni pripravljenih tekem: vse tekme se igrajo ali čakajo na prejšnja kola.</p>
            ) : (
              <ol className="mize__pripravljene">
                {podatki.kandidati.map((k) => (
                  <VrsticaKandidata
                    key={k.tekma.tekma.id}
                    kandidat={k}
                    onPoklici={
                      proste.length > 0
                        ? () => nastaviKlic({ tekma: k.tekma, miza: k.tekma.miza ?? proste[0], sprememba: false })
                        : undefined
                    }
                  />
                ))}
              </ol>
            )}
          </aside>
        </div>
      )}

      {klic && (
        <OknoKlica
          idTurnirja={idTurnirja}
          podatki={podatki}
          klic={klic}
          kandidat={kandidatPoId.get(klic.tekma.tekma.id)}
          onZapri={() => nastaviKlic(null)}
          onPoklicano={osvezi}
        />
      )}

      {izbiraZa && (
        <OknoIzbire
          miza={izbiraZa}
          podatki={podatki}
          onIzbira={(k) => {
            nastaviIzbiraZa(null)
            nastaviKlic({ tekma: k.tekma, miza: izbiraZa.stevilka, sprememba: false })
          }}
          onZapri={() => nastaviIzbiraZa(null)}
        />
      )}

      {vnosIzida && (
        <VnosRezultataOkno
          tekma={vnosIzida.tekma}
          nadObrazcem={
            <p className="mize__okno-meta">
              Miza {mizeTekme(vnosIzida)} · {opisTekme(vnosIzida)}
            </p>
          }
          onZapri={() => nastaviVnosIzida(null)}
          onShranjeno={osvezi}
        />
      )}

      {preklic && (
        <PotrditvenoOkno
          naslov="Prekliči klic"
          sporocilo={`${imenaTekme(preklic)} gre z mize ${mizeTekme(preklic)} nazaj med pripravljene tekme.`}
          besedaPotrditve="Prekliči klic"
          onPotrdi={() => preklicKlica.mutate(preklic)}
          onZapri={() => nastaviPreklic(null)}
        />
      )}

      {vsiPredlogi && (
        <PotrditvenoOkno
          naslov="Pokliči predloge"
          sporocilo={pari
            .map(([m, k]) => `Miza ${m.stevilka}: ${imenaTekme(k.tekma)}`)
            .join('; ')}
          besedaPotrditve={`Pokliči ${pari.length}`}
          onPotrdi={() => klicVseh.mutate(pari)}
          onZapri={() => nastaviVsePredloge(false)}
        />
      )}
    </section>
  )
}

function Kolofon({ oznaka, vrednost }: { oznaka: string; vrednost: string }) {
  return (
    <div className="kolofon__vrstica">
      <span className="kolofon__oznaka">{oznaka}</span>
      <span className="kolofon__vrednost">{vrednost}</span>
    </div>
  )
}

/* ---------- vrstica mize ---------- */

function VrsticaMize({
  miza,
  podatki,
  kandidatPoId,
  poudarjena,
  onPoklici,
  onDruga,
  onVnosIzida,
  onSprememba,
  onPreklic,
  onIzklop,
}: {
  miza: MizaDto
  podatki: MizeTurnirjaDto
  kandidatPoId: Map<number, KandidatMizeDto>
  poudarjena: boolean
  onPoklici: (t: TekmaMizeDto) => void
  onDruga: () => void
  onVnosIzida: (t: TekmaMizeDto) => void
  onSprememba: (t: TekmaMizeDto) => void
  onPreklic: (t: TekmaMizeDto) => void
  onIzklop: (izklopi: boolean) => void
}) {
  const t = miza.tekma
  if (miza.izklopljena) {
    return (
      <li className="miza-vrstica miza-vrstica--izklopljena">
        <span className="miza-vrstica__st">{miza.stevilka}</span>
        <div className="miza-vrstica__vsebina">
          <p className="miza-vrstica__stanje">Izklopljena</p>
        </div>
        <div className="miza-vrstica__dejanja">
          <button type="button" className="gumb gumb--majhen" onClick={() => onIzklop(false)}>
            Vklopi
          </button>
        </div>
      </li>
    )
  }

  if (t) {
    const minute = minutOd(t.poklicanaOb, podatki.casStreznika)
    return (
      <li className="miza-vrstica miza-vrstica--zasedena">
        <span className="miza-vrstica__st">{miza.stevilka}</span>
        <div className="miza-vrstica__vsebina">
          <p className="miza-vrstica__imena">{imenaTekme(t)}</p>
          <p className="miza-vrstica__meta">
            {opisTekme(t)}
            {t.miza2 !== null && ` · mizi ${mizeTekme(t)}`}
            {t.sodnik ? ` · sodi ${t.sodnik.ime}` : ''}
          </p>
        </div>
        <span className="miza-vrstica__cas" title="Minute od klica">
          {minute !== null ? `${minute} min` : ''}
        </span>
        <div className="miza-vrstica__dejanja">
          {t.ekipno && t.idSrecanje !== null ? (
            <Link to={`/srecanja/${t.idSrecanje}`} className="gumb gumb--majhen">
              Zapisnik
            </Link>
          ) : (
            <button type="button" className="gumb" onClick={() => onVnosIzida(t)}>
              Vnesi izid
            </button>
          )}
          <MeniDejanj naslov={`Dejanja mize ${miza.stevilka}`}>
            {(zapri) => (
              <>
                <button
                  type="button"
                  role="menuitem"
                  className="uporabnik-meni__postavka"
                  onClick={() => {
                    zapri()
                    onSprememba(t)
                  }}
                >
                  Premakni ali zamenjaj sodnika
                </button>
                <button
                  type="button"
                  role="menuitem"
                  className="uporabnik-meni__postavka"
                  onClick={() => {
                    zapri()
                    onPreklic(t)
                  }}
                >
                  Prekliči klic
                </button>
              </>
            )}
          </MeniDejanj>
        </div>
      </li>
    )
  }

  const predlog = miza.predlogi.length > 0 ? kandidatPoId.get(miza.predlogi[0]) : undefined
  const prostaMinut = minutOd(miza.prostaOd, podatki.casStreznika)
  const sodnik = predlog ? privzetiSodnik(miza, predlog.tekma) : miza.cakajociSodnik
  return (
    <li className={'miza-vrstica miza-vrstica--prosta' + (poudarjena ? ' miza-vrstica--sproscena' : '')}>
      <span className="miza-vrstica__st">{miza.stevilka}</span>
      <div className="miza-vrstica__vsebina">
        <p className="miza-vrstica__stanje">
          Prosta{miza.prostaOd ? ` · ${predMinutami(prostaMinut)}` : ''}
          {miza.cakajociSodnik && ` · sodi ${miza.cakajociSodnik.ime} (poraženec)`}
        </p>
        {predlog ? (
          <div className="miza-predlog">
            <p className="miza-vrstica__imena">{imenaTekme(predlog.tekma)}</p>
            <p className="miza-vrstica__meta">
              {opisTekme(predlog.tekma)} · {predlog.razlogi.join(' · ')}
            </p>
            {miza.cakajociSodnik && !sodnik && (
              <p className="miza-vrstica__meta">
                {miza.cakajociSodnik.ime} igra v tej tekmi – sodnika izberi v oknu klica.
              </p>
            )}
          </div>
        ) : podatki.kandidati.length === 0 ? (
          <p className="miza-vrstica__meta">Ni pripravljenih tekem.</p>
        ) : (
          <p className="miza-vrstica__meta">
            Vse pripravljene tekme imajo igralca, ki igra ali je predlagan za drugo mizo. Izbereš
            jih lahko v meniju mize (Druga tekma …).
          </p>
        )}
      </div>
      <span className="miza-vrstica__cas" />
      <div className="miza-vrstica__dejanja">
        {predlog && (
          <button type="button" className="gumb gumb--glavni" onClick={() => onPoklici(predlog.tekma)}>
            Pokliči
          </button>
        )}
        <MeniDejanj naslov={`Dejanja mize ${miza.stevilka}`}>
          {(zapri) => (
            <>
              <button
                type="button"
                role="menuitem"
                className="uporabnik-meni__postavka"
                disabled={podatki.kandidati.length === 0}
                onClick={() => {
                  zapri()
                  onDruga()
                }}
              >
                Druga tekma …
              </button>
              <button
                type="button"
                role="menuitem"
                className="uporabnik-meni__postavka"
                onClick={() => {
                  zapri()
                  onIzklop(true)
                }}
              >
                Izklopi mizo
              </button>
            </>
          )}
        </MeniDejanj>
      </div>
    </li>
  )
}

/* ---------- pripravljene tekme ---------- */

function VrsticaKandidata({ kandidat, onPoklici }: { kandidat: KandidatMizeDto; onPoklici?: () => void }) {
  const t = kandidat.tekma
  return (
    <li className={'mize-kandidat' + (kandidat.zaseden ? ' mize-kandidat--zaseden' : '')}>
      <div className="mize-kandidat__vsebina">
        <p className="miza-vrstica__imena">{imenaTekme(t)}</p>
        <p className="miza-vrstica__meta">{opisTekme(t)}</p>
        <p className={'mize-kandidat__razlogi' + (kandidat.zaseden ? ' mize-kandidat__razlogi--zaseden' : '')}>
          {kandidat.razlogi.join(' · ')}
        </p>
      </div>
      {onPoklici && (
        <button type="button" className="gumb gumb--majhen" onClick={onPoklici}>
          Pokliči …
        </button>
      )}
    </li>
  )
}

/* Izbira druge tekme za prosto mizo: najprej zamenjavi iz predloga, nato vse
   ostale pripravljene po vrsti (zasedene na koncu, z razlogom). */
function OknoIzbire({
  miza,
  podatki,
  onIzbira,
  onZapri,
}: {
  miza: MizaDto
  podatki: MizeTurnirjaDto
  onIzbira: (k: KandidatMizeDto) => void
  onZapri: () => void
}) {
  const poId = new Map(podatki.kandidati.map((k) => [k.tekma.tekma.id, k]))
  const prvi = miza.predlogi.map((id) => poId.get(id)).filter((k): k is KandidatMizeDto => !!k)
  const ostali = podatki.kandidati.filter((k) => !miza.predlogi.includes(k.tekma.tekma.id))
  return (
    <ModalnoOkno naslov={`Tekma za mizo ${miza.stevilka}`} srednje brezObrazca onZapri={onZapri}>
      <ol className="mize__pripravljene mize__pripravljene--okno">
        {[...prvi, ...ostali].map((k) => (
          <VrsticaKandidata key={k.tekma.tekma.id} kandidat={k} onPoklici={() => onIzbira(k)} />
        ))}
      </ol>
    </ModalnoOkno>
  )
}

/* ---------- okno klica ---------- */

function OknoKlica({
  idTurnirja,
  podatki,
  klic,
  kandidat,
  onZapri,
  onPoklicano,
}: {
  idTurnirja: number
  podatki: MizeTurnirjaDto
  klic: Klic
  kandidat: KandidatMizeDto | undefined
  onZapri: () => void
  onPoklicano: () => void
}) {
  const t = klic.tekma
  const proste = prosteMize(podatki.mize)
  // pri spremembi sta na voljo tudi mizi, na katerih tekma že je
  const moznosti = [
    ...new Set([...(klic.sprememba ? [t.miza, t.miza2] : []), ...proste].filter((m): m is number => m !== null)),
  ].sort((a, b) => a - b)
  const zacetnaMiza = klic.miza ?? moznosti[0] ?? null
  const mizaPo = (st: number | null) => podatki.mize.find((m) => m.stevilka === st)

  const [miza, nastaviMizo] = useState<number | null>(zacetnaMiza)
  const [miza2, nastaviMizo2] = useState<number | null>(klic.sprememba ? t.miza2 : null)
  const [sodnik, nastaviSodnika] = useState<OsebaMizeDto | null>(
    klic.sprememba ? t.sodnik : privzetiSodnik(mizaPo(zacetnaMiza), t),
  )
  // ročno izbran sodnik ostane, ko organizator zamenja mizo
  const [sodnikRocno, nastaviSodnikRocno] = useState(klic.sprememba)

  const osebe = useQuery({
    queryKey: ['turnir', idTurnirja, 'mize', 'osebe'],
    queryFn: () => mizeApi.osebe(idTurnirja),
    staleTime: 60_000,
  })
  const moznostiSodnika = useMemo(() => {
    const vse = (osebe.data ?? []).filter((o) => !t.osebe.includes(o.id))
    if (sodnik && !vse.some((o) => o.id === sodnik.id)) vse.unshift(sodnik)
    return vse.map((o) => ({ id: o.id, ime: o.ime }))
  }, [osebe.data, t.osebe, sodnik])

  /* Kdo zdaj igra ali sodi: opozorilo za izbranega sodnika. */
  const zasedeniSodniki = useMemo(() => {
    const z = new Map<number, number>()
    for (const m of podatki.mize) {
      if (!m.tekma || m.tekma.tekma.id === t.tekma.id) continue
      m.tekma.osebe.forEach((o) => z.set(o, m.stevilka))
      if (m.tekma.sodnik) z.set(m.tekma.sodnik.id, m.stevilka)
    }
    return z
  }, [podatki.mize, t.tekma.id])

  const poklici = useMutation({
    mutationFn: () => mizeApi.poklici(t.tekma.id, { miza: miza!, miza2, idSodnika: sodnik?.id ?? null }),
    onSuccess: () => {
      onPoklicano()
      onZapri()
    },
  })

  function obMizi(st: number) {
    nastaviMizo(st)
    if (miza2 === st) nastaviMizo2(null)
    if (!sodnikRocno) nastaviSodnika(privzetiSodnik(mizaPo(st), t))
  }

  function obOddaji(d: FormEvent) {
    d.preventDefault()
    if (miza !== null) poklici.mutate()
  }

  const predlaganSodnik = !sodnikRocno && sodnik !== null && sodnik.id === mizaPo(miza)?.cakajociSodnik?.id
  const zasedenSodnik = sodnik ? zasedeniSodniki.get(sodnik.id) : undefined

  return (
    <ModalnoOkno
      naslov={klic.sprememba ? 'Spremeni klic' : 'Pokliči tekmo'}
      nadnaslov={opisTekme(t)}
      onZapri={onZapri}
    >
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="mize__okno-imena">{imenaTekme(t)}</p>
        {kandidat?.zaseden && (
          <p className="obvestilo obvestilo--opozorilo">
            {kandidat.razlogi.filter((r) => !r.includes(' naj sodi') && / (igra|sodi)( na mizi|$)/.test(r)).join(' · ')}. Tekmo lahko vseeno
            pokličeš.
          </p>
        )}
        {moznosti.length === 0 ? (
          <p className="obvestilo obvestilo--opozorilo">Ni proste mize.</p>
        ) : (
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              Miza
              <select value={miza ?? ''} onChange={(d) => obMizi(Number(d.target.value))}>
                {moznosti.map((st) => (
                  <option key={st} value={st}>
                    {st}
                  </option>
                ))}
              </select>
            </label>
            {t.ekipno && (
              <label className="obrazec__polje">
                Druga miza
                <select
                  value={miza2 ?? ''}
                  onChange={(d) => nastaviMizo2(d.target.value === '' ? null : Number(d.target.value))}
                >
                  <option value="">brez</option>
                  {moznosti
                    .filter((st) => st !== miza)
                    .map((st) => (
                      <option key={st} value={st}>
                        {st}
                      </option>
                    ))}
                </select>
              </label>
            )}
          </div>
        )}

        <IskalniIzbirnik
          oznaka="Sodnik"
          vidnaOznaka
          vObrazcu
          namig="Vpiši ime ali pusti prazno"
          moznosti={moznostiSodnika}
          izbrano={sodnik?.id ?? null}
          naIzbiro={(id) => {
            nastaviSodnikRocno(true)
            nastaviSodnika(moznostiSodnika.find((o) => o.id === id) ?? null)
          }}
          naPraznjenje={() => {
            nastaviSodnikRocno(true)
            nastaviSodnika(null)
          }}
        />
        {predlaganSodnik && <p className="namig">Poraženec prejšnje tekme na tej mizi.</p>}
        {zasedenSodnik !== undefined && (
          <p className="obvestilo obvestilo--opozorilo">
            {sodnik!.ime} zdaj igra ali sodi na mizi {zasedenSodnik}.
          </p>
        )}

        <SporociloNapake napaka={poklici.error} />
        <div className="obrazec__gumbi">
          <button type="submit" className="gumb gumb--glavni" disabled={miza === null || poklici.isPending}>
            {klic.sprememba ? 'Shrani' : `Pokliči na mizo ${miza ?? ''}`}
          </button>
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

/* ---------- nastavitve miz ---------- */

function NastavitveMiz({
  podatki,
  prvic,
  onShranjeno,
  onZapri,
}: {
  podatki: MizeTurnirjaDto
  prvic: boolean
  onShranjeno: () => void
  onZapri?: () => void
}) {
  const [stevilo, nastaviStevilo] = useState(podatki.steviloMiz === null ? '' : String(podatki.steviloMiz))
  const [porazenecSodi, nastaviPorazenecSodi] = useState(prvic ? true : podatki.porazenecSodi)
  const [pocitek, nastaviPocitek] = useState(String(podatki.pocitekMin))
  const shrani = useMutation({
    mutationFn: () => {
      const n = Number(stevilo)
      return mizeApi.nastavi(podatki.idTurnir, {
        steviloMiz: n,
        // izklopljene mize, ki jih po zmanjšanju ni več, odpadejo
        izklopljeneMize: podatki.izklopljeneMize.filter((m) => m <= n),
        porazenecSodi,
        pocitekMin: pocitek === '' ? 0 : Number(pocitek),
      })
    },
    onSuccess: onShranjeno,
  })
  const veljavno = stevilo !== '' && Number(stevilo) >= 1

  return (
    <form
      className="obrazec mize__nastavitve"
      onSubmit={(d) => {
        d.preventDefault()
        if (veljavno) shrani.mutate()
      }}
    >
      {prvic && (
        <p className="namig">
          Vpiši, koliko miz imaš v dvorani. Mize so oštevilčene od 1 naprej, kot jih sodnik vpiše
          v okence na listku.
        </p>
      )}
      <div className="obrazec__vrstica">
        <label className="obrazec__polje">
          Število miz (1–99)
          <StevilskoPolje vrednost={stevilo} naSpremembo={nastaviStevilo} najvec={99} className="vnos--ozek" />
        </label>
        <label className="obrazec__polje">
          Počitek med tekmami (0–30 min)
          <StevilskoPolje vrednost={pocitek} naSpremembo={nastaviPocitek} najvec={30} className="vnos--ozek" />
        </label>
      </div>
      <label className="obrazec__polje obrazec__polje--stikalo">
        <input type="checkbox" checked={porazenecSodi} onChange={(d) => nastaviPorazenecSodi(d.target.checked)} />
        Poraženec sodi naslednjo tekmo na svoji mizi
      </label>
      <SporociloNapake napaka={shrani.error} />
      <div className="obrazec__gumbi">
        <button type="submit" className="gumb gumb--glavni" disabled={!veljavno || shrani.isPending}>
          Shrani
        </button>
        {onZapri && (
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
        )}
      </div>
    </form>
  )
}

/* ---------- pomožno ---------- */

function mizSklon(n: number): string {
  const o = n % 100
  if (o === 1) return 'miza'
  if (o === 2) return 'mizi'
  if (o === 3 || o === 4) return 'mize'
  return 'miz'
}

/* Nastavitev piska je udobje tega prenosnika: hrani se v brskalniku, privzeto vklopljena. */
function beriPisk(): boolean {
  try {
    return localStorage.getItem(KLJUC_PISKA) !== 'ne'
  } catch {
    return true
  }
}

function zapisiPisk(vklopljen: boolean) {
  try {
    localStorage.setItem(KLJUC_PISKA, vklopljen ? 'da' : 'ne')
  } catch {
    // zasebno okno ali prepovedana shramba: nastavitev velja do osvežitve
  }
}

/* Kratek dvojni pisk. Brskalnik dovoli zvok šele po dejanju uporabnika, zato se
   zvočni kontekst ustvari ob prvem kliku na strani (organizator na strani klika). */
function usePisk(): () => void {
  const kontekst = useRef<AudioContext | null>(null)
  useEffect(() => {
    const obDotiku = () => {
      try {
        if (!kontekst.current) kontekst.current = new AudioContext()
        else void kontekst.current.resume()
      } catch {
        // brez zvoka
      }
    }
    window.addEventListener('pointerdown', obDotiku)
    return () => window.removeEventListener('pointerdown', obDotiku)
  }, [])
  return useCallback(() => {
    const k = kontekst.current
    if (!k) return
    for (const zamik of [0, 0.25]) {
      const ton = k.createOscillator()
      const glasnost = k.createGain()
      ton.frequency.value = 880
      glasnost.gain.value = 0.15
      ton.connect(glasnost)
      glasnost.connect(k.destination)
      ton.start(k.currentTime + zamik)
      ton.stop(k.currentTime + zamik + 0.15)
    }
  }, [])
}
