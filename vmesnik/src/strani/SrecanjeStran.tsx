/* Podroben pogled srecanja: postava (za administratorja, dokler ni rezultatov)
   in zapisnik - seznam posamicnih tekem z vnosom rezultatov.

   Srecanje je ligasko (redni del ali tekma koncnice) ali pa ekipna tekma
   turnirja - kje je, pove kontekst (tekmovanje, dogodek, "polfinale",
   "koncnica · finale · 1. tekma"). Uvozeno srecanje je samo za branje. */
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, srecanjaApi, turnirjiApi } from '../api/zahteve'
import type {
  IzidTekme,
  MestoVnos,
  NizVnos,
  SrecanjePodrobnoDto,
  StranEkipe,
  TekmaSrecanjaDto,
} from '../api/tipi'
import { OZNAKE_FORMAT, OZNAKE_VIR, izidNizov, nizovZaZmago } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { SpremembaRatinga } from '../komponente/SpremembaRatinga'
import {
  TockeNizov,
  preveriNize,
  vrsticeZaIzid,
  type VrsticaNiza,
} from '../komponente/TockeNizov'
import { intervalOsvezevanja, jeVZivo, uraOsvezitve } from '../pomozno/osvezevanje'

export function SrecanjeStran() {
  const { id } = useParams()
  const idSrecanje = Number(id)
  const { jeAdmin, jeOrganizator, smemUrejati } = useAvtentikacija()
  /* Med srečanjem se zapisnik osvežuje sam (gl. DogodekStran). */
  const podrobno = useQuery({
    queryKey: ['srecanje', idSrecanje],
    queryFn: () => srecanjaApi.podrobno(idSrecanje),
    refetchInterval: (poizvedba) => intervalOsvezevanja(poizvedba.state.data?.srecanje.status),
  })

  /* Za urejanje potrebujemo lastnistvo lige oz. turnirja srecanja; poizvedbo
     sprozimo le za morebitne urejevalce in ne pri uvozenem srecanju. */
  const idLiga = podrobno.data?.srecanje.idLiga
  const idTurnir = podrobno.data?.srecanje.idTurnir
  const uvozeno = podrobno.data?.kontekst.vir != null
  const liga = useQuery({
    queryKey: ['liga', idLiga],
    queryFn: () => ligeApi.najdi(idLiga!),
    enabled: idLiga != null && !uvozeno && (jeAdmin || jeOrganizator),
  })
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir != null && !uvozeno && jeOrganizator && !jeAdmin,
  })

  if (podrobno.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (podrobno.error) return <NapakaPoizvedbe poizvedba={podrobno} kaj="srečanja" />
  if (!podrobno.data) return <p className="obvestilo">Tega srečanja ni (več).</p>

  const p = podrobno.data
  const s = p.srecanje
  const k = p.kontekst
  /* Organizator sme upravljati srecanja svoje (ali klubske) lige oz. turnirja,
     admin vse - razen uvozenih: vir resnice je zveza. */
  const lastnik = liga.data ?? turnir.data
  const smem = k.vir == null
    && (jeAdmin || (!!lastnik && smemUrejati(lastnik.idLastnik, lastnik.idKlubLastnik)))
  const opis = k.opis ?? `${s.kolo}. kolo`
  const imaRezultate = p.tekme.some((t) => t.status === 'KONCANA')
  const lahkoUrejaPostavo = smem && s.status !== 'KONCANO' && !imaRezultate
  /* Listki so smiselni le, ko je postava določena in kaka tekma še čaka. */
  const imaZaTiskanje = p.tekme.some((t) => t.status === 'CAKA')

  const odigrano = p.tekme.length > 0
  const domVodi = odigrano && s.dobljeneDomaci > s.dobljeneGost
  const gostVodi = odigrano && s.dobljeneGost > s.dobljeneDomaci
  const { datum, ura } = razbijCas(s.predvidenZacetek)

  return (
    <section className="srecanje">
      <div>
        {s.idDogodek != null ? (
          <Link to={`/dogodki/${s.idDogodek}`} className="povezava-nazaj">← {k.dogodek ?? 'Dogodek'}</Link>
        ) : (
          <Link to={`/lige/${s.idLiga}`} className="povezava-nazaj">← {k.tekmovanje || 'Liga'}</Link>
        )}
        <p className="uvod uvod--tesno">
          {[k.tekmovanje, k.sezona, k.dogodek].filter(Boolean).join(' · ')}
        </p>
        {k.vir && <span className="oznaka-vira">{OZNAKE_VIR[k.vir]} · uvoženo, samo za branje</span>}

        {/* Maketa nad semaforjem nima naslova, dokument pa mora imeti ime -
            sicer bralnik zaslona strani ne zna poimenovati. */}
        <h1 className="samo-za-bralnik">
          {s.domaci} proti {s.gost}, {opis}
        </h1>

        {/* Semafor med dvema debelima crtama: doma levo, gostje desno. */}
        <div className="srecanje__glava">
          <div className="srecanje__ekipa srecanje__ekipa--desno">
            <span className="srecanje__stran-oznaka">Domači</span>
            <span className="srecanje__ekipa-ime">{s.domaci}</span>
          </div>
          <div>
            <div className="srecanje__izid">
              {odigrano ? (
                <>
                  <span className={domVodi ? 'srecanje__izid-vodi' : undefined}>
                    {s.dobljeneDomaci}
                  </span>
                  <span className="srecanje__izid-locilo"> : </span>
                  <span className={gostVodi ? 'srecanje__izid-vodi' : undefined}>
                    {s.dobljeneGost}
                  </span>
                </>
              ) : (
                'vs'
              )}
            </div>
            <p className="srecanje__meta">
              {opis} · {statusOznaka(s.status)}
              {datum && (
                <>
                  <br />
                  {datum}
                  {ura && ` · ${ura}`}
                </>
              )}
              {jeVZivo(s.status) && (
                <>
                  <br />
                  Osveženo ob {uraOsvezitve(podrobno.dataUpdatedAt)}
                </>
              )}
            </p>
          </div>
          <div className="srecanje__ekipa">
            <span className="srecanje__stran-oznaka">Gostje</span>
            <span className="srecanje__ekipa-ime">{s.gost}</span>
          </div>
        </div>

        {smem && imaZaTiskanje && (
          <div className="srecanje__dejanja">
            <Link to={`/srecanja/${idSrecanje}/listki`} className="gumb">
              Zapisnik za tisk
            </Link>
          </div>
        )}
      </div>

      {p.tekme.length === 0 ? (
        <p className="obvestilo">
          {s.status === 'KONCANO'
            ? 'Srečanje je zapisano brez posamičnih tekem (brez boja).'
            : `Postava še ni določena. ${smem ? 'Določi jo spodaj.' : 'Čaka na organizatorja.'}`}
        </p>
      ) : (
        <Zapisnik podrobno={p} jeAdmin={smem} />
      )}

      {lahkoUrejaPostavo && <PostavaUredi podrobno={p} />}
    </section>
  )
}

function statusOznaka(status: string): string {
  return status === 'KONCANO' ? 'končano' : status === 'POTEKA' ? 'poteka' : 'razpored'
}

/* Iz ISO datuma-casa loci datum (dd. mm. llll) in uro (hh.mm); brez casa vrne
   prazna niza, da se vrstica ne izpise. Ura 00:00 pomeni »ura ni dolocena«
   (turnirsko srecanje pozna samo dan, organizator uro lahko izpusti), zato
   odpade - kot v oblikujTermin. */
function razbijCas(iso: string | null): { datum: string; ura: string } {
  if (!iso) return { datum: '', ura: '' }
  const [d, t] = iso.split('T')
  const deli = d.split('-')
  const datum = deli.length === 3 ? `${Number(deli[2])}. ${Number(deli[1])}. ${deli[0]}` : ''
  const ura = t && t.slice(0, 5) !== '00:00' ? t.slice(0, 5).replace(':', '.') : ''
  return { datum, ura }
}

function Zapisnik({ podrobno, jeAdmin }: { podrobno: SrecanjePodrobnoDto; jeAdmin: boolean }) {
  const [urejana, nastaviUrejano] = useState<TekmaSrecanjaDto | null>(null)
  const [menjava, nastaviMenjavo] = useState<TekmaSrecanjaDto | null>(null)
  const s = podrobno.srecanje
  const koncano = s.status === 'KONCANO'

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Zapisnik</h2>
        <span className="sekcija__meta">
          {OZNAKE_FORMAT[podrobno.format]} · najboljši od {podrobno.tekme[0]?.steviloNizov ?? 5} nizov
        </span>
      </div>

      <div className="tabela-ovoj">
        <table className="tabela srecanje__tabela">
          <caption className="samo-za-bralnik">Zapisnik srečanja: posamične tekme po vrstnem redu</caption>
          <thead>
            <tr>
              <th scope="col" className="srecanje__oznaka-glava">Par</th>
              <th scope="col" className="srecanje__stran-glava">Domači</th>
              <th scope="col" className="srecanje__izid-glava">Izid</th>
              <th scope="col">Gost</th>
              <th scope="col" className="tabela__dejanja">Stanje</th>
            </tr>
          </thead>
          <tbody>
            {podrobno.tekme.map((t) => {
              const konec = t.status === 'KONCANA'
              const domZmaga = t.zmagovalecStran === 'DOMACI'
              const gostZmaga = t.zmagovalecStran === 'GOST'
              return (
                <tr
                  key={t.id}
                  className={t.status === 'NEODIGRANA' ? 'srecanje__vrsta--neodigrana' : undefined}
                >
                  <td className="srecanje__oznaka">{t.oznaka}</td>
                  <td
                    className={
                      'srecanje__stran-celica' +
                      (domZmaga ? ' srecanje__zmaga' : gostZmaga ? ' srecanje__poraz' : '')
                    }
                  >
                    {imeStrani(t, 'DOMACI')}
                    <SpremembaRatinga vrednost={t.spremembaRatingaDomaci} />
                    {t.menjavaDomaci && <OznakaMenjave />}
                  </td>
                  <td className="srecanje__izid-tekme">
                    {konec ? (
                      <>
                        {izidNizov(t.dobljeniNiziDomaci, t.dobljeniNiziGost, t.izidTip)}
                        {/* Točke po nizih so neobvezne — izpišejo se le, kadar
                            jih je organizator vpisal. */}
                        {t.nizi.length > 0 && (
                          <span className="srecanje__nizi">
                            {t.nizi.map((n) => `${n.tocke1}:${n.tocke2}`).join(', ')}
                          </span>
                        )}
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td
                    className={
                      gostZmaga ? 'srecanje__zmaga' : domZmaga ? 'srecanje__poraz' : undefined
                    }
                  >
                    {imeStrani(t, 'GOST')}
                    <SpremembaRatinga vrednost={t.spremembaRatingaGost} />
                    {t.menjavaGost && <OznakaMenjave />}
                  </td>
                  <td className="tabela__dejanja">
                    {konec ? (
                      <span className="srecanje__stanje">Končana</span>
                    ) : jeAdmin && !koncano && t.status === 'CAKA' ? (
                      <div>
                        <button
                          className="gumb gumb--majhen srecanje__gumb-menjave"
                          aria-label={`Menjava igralcev – ${t.oznaka}`}
                          onClick={() => nastaviMenjavo(t)}
                        >
                          Menjava
                        </button>
                        <button className="gumb gumb--majhen" onClick={() => nastaviUrejano(t)}>
                          Vnesi
                        </button>
                      </div>
                    ) : (
                      <span className="srecanje__stanje srecanje__stanje--caka">Čaka</span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {urejana && (
        <RezultatOkno
          tekma={urejana}
          idSrecanje={s.id}
          onZapri={() => nastaviUrejano(null)}
          onMenjava={() => {
            nastaviUrejano(null)
            nastaviMenjavo(urejana)
          }}
        />
      )}
      {menjava && (
        <MenjavaOkno tekma={menjava} podrobno={podrobno} onZapri={() => nastaviMenjavo(null)} />
      )}
    </div>
  )
}

/* Oznaka tekme (»A-Y«) je mesto v začetni postavi. Kadar na njem igra kdo
   drug, to pove oznaka pod imenom — sicer bi zapisnik trdil, da je C igral
   kot A. */
function OznakaMenjave() {
  return <span className="srecanje__menjava">menjava</span>
}

/* Menjava velja za to eno tekmo in ne »od tu naprej«: v ligi se igralci med
   srečanjem menjajo prosto (po A-X in B-Y lahko sledita C-Y in A-Z), zato
   organizator prepiše zapisnik s papirja tekmo za tekmo. Ponujen je kader
   ekipe; kdor na srečanje pride na novo, gre najprej v kader. */
function MenjavaOkno({
  tekma,
  podrobno,
  onZapri,
}: {
  tekma: TekmaSrecanjaDto
  podrobno: SrecanjePodrobnoDto
  onZapri: () => void
}) {
  const odjemalec = useQueryClient()
  const s = podrobno.srecanje
  const dvojice = tekma.tip === 'DVOJICE'
  const vNiz = (id: number | null) => (id == null ? '' : String(id))
  const [domaci, nastaviDomaci] = useState(vNiz(tekma.idDomaci))
  const [domaci2, nastaviDomaci2] = useState(vNiz(tekma.idDomaci2))
  const [gost, nastaviGost] = useState(vNiz(tekma.idGost))
  const [gost2, nastaviGost2] = useState(vNiz(tekma.idGost2))

  const shrani = useMutation({
    mutationFn: () =>
      srecanjaApi.zamenjajIgralce(tekma.id, {
        idDomaci: Number(domaci),
        idDomaci2: dvojice ? Number(domaci2) : null,
        idGost: Number(gost),
        idGost2: dvojice ? Number(gost2) : null,
      }),
    onSuccess: (novo) => {
      odjemalec.setQueryData(['srecanje', s.id], novo)
      onZapri()
    },
  })

  const izbrani = dvojice ? [domaci, domaci2, gost, gost2] : [domaci, gost]
  const vsiIzbrani = izbrani.every((id) => id !== '')
  const parRazlicen = !dvojice || (domaci !== domaci2 && gost !== gost2)
  const spremenjeno =
    domaci !== vNiz(tekma.idDomaci) || gost !== vNiz(tekma.idGost)
    || (dvojice && (domaci2 !== vNiz(tekma.idDomaci2) || gost2 !== vNiz(tekma.idGost2)))
  /* Kje se kader dopolni: pri ligi okno »Ekipe in kader«, pri ekipnem
     dogodku stran dogodka. */
  const potDoKadra = s.idDogodek != null ? `/dogodki/${s.idDogodek}` : `/lige/${s.idLiga}`

  return (
    <ModalnoOkno naslov={`Menjava – ${tekma.oznaka}`} onZapri={onZapri}>
      <form
        className="obrazec"
        onSubmit={(d) => {
          d.preventDefault()
          if (vsiIzbrani && parRazlicen && spremenjeno && !shrani.isPending) shrani.mutate()
        }}
      >
        <div className="obrazec__vrstica">
          <IzbiraIgralca oznaka={dvojice ? `${s.domaci} – 1. igralec` : s.domaci}
            kader={podrobno.kaderDomaci} vrednost={domaci} naSpremembo={nastaviDomaci} />
          {dvojice && (
            <IzbiraIgralca oznaka={`${s.domaci} – 2. igralec`}
              kader={podrobno.kaderDomaci} vrednost={domaci2} naSpremembo={nastaviDomaci2} />
          )}
        </div>
        <div className="obrazec__vrstica">
          <IzbiraIgralca oznaka={dvojice ? `${s.gost} – 1. igralec` : s.gost}
            kader={podrobno.kaderGost} vrednost={gost} naSpremembo={nastaviGost} />
          {dvojice && (
            <IzbiraIgralca oznaka={`${s.gost} – 2. igralec`}
              kader={podrobno.kaderGost} vrednost={gost2} naSpremembo={nastaviGost2} />
          )}
        </div>
        {!parRazlicen && <div className="napaka">Par sestavljata dva različna igralca.</div>}

        <p className="namig">
          Menjava velja samo za to tekmo; oznaka {tekma.oznaka} ostane mesto v začetni postavi.
          Igralca ni na seznamu? Najprej ga dodaj v <Link to={potDoKadra}>kader ekipe</Link>.
        </p>
        <SporociloNapake napaka={shrani.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={!vsiIzbrani || !parRazlicen || !spremenjeno || shrani.isPending}
          >
            Shrani menjavo
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function IzbiraIgralca({
  oznaka,
  kader,
  vrednost,
  naSpremembo,
}: {
  oznaka: string
  kader: { idIgralec: number; polnoIme: string }[]
  vrednost: string
  naSpremembo: (vrednost: string) => void
}) {
  return (
    <label className="obrazec__polje">
      <span>{oznaka}</span>
      <select value={vrednost} onChange={(d) => naSpremembo(d.target.value)}>
        <option value="">— igralec —</option>
        {kader.map((k) => (
          <option key={k.idIgralec} value={k.idIgralec}>{k.polnoIme}</option>
        ))}
      </select>
    </label>
  )
}

function imeStrani(t: TekmaSrecanjaDto, stran: StranEkipe): string {
  if (stran === 'DOMACI') {
    return [t.domaci, t.domaci2].filter(Boolean).join(' / ') || '—'
  }
  return [t.gost, t.gost2].filter(Boolean).join(' / ') || '—'
}

function RezultatOkno({
  tekma,
  idSrecanje,
  onZapri,
  onMenjava,
}: {
  tekma: TekmaSrecanjaDto
  idSrecanje: number
  onZapri: () => void
  /* Rezultat se vpisuje s papirja; če je tam drug igralec, je to trenutek, ko
     organizator razliko opazi. Na telefonu je to tudi edini vhod v menjavo. */
  onMenjava: () => void
}) {
  const odjemalec = useQueryClient()
  const zaZmago = nizovZaZmago(tekma.steviloNizov)
  const [izid, nastaviIzid] = useState<'IGRANO' | Exclude<IzidTekme, 'IGRANO' | 'PROSTO'>>('IGRANO')
  const [niziDomaci, nastaviNiziDomaci] = useState('')
  const [niziGost, nastaviNiziGost] = useState('')
  const [zmagovalec, nastaviZmagovalca] = useState<StranEkipe>('DOMACI')
  /* Točke po nizih so tudi v ligi neobvezne (enako kot pri turnirjih). */
  const [vnasamTocke, nastaviVnasamTocke] = useState(false)
  const [tockeNizov, nastaviTockeNizov] = useState<VrsticaNiza[]>([])
  const [napakaVnosa, nastaviNapakoVnosa] = useState<string | null>(null)

  /* Koliko nizov je bilo odigranih — po tem se ravna število vrstic za točke;
     dokler izid ni v celoti vpisan, vrstic ni. */
  const odigranihNizov =
    niziDomaci !== '' && niziGost !== '' ? Number(niziDomaci) + Number(niziGost) : 0

  function obSpremembiNizov(stran: 'domaci' | 'gost', vrednost: string) {
    const dom = stran === 'domaci' ? vrednost : niziDomaci
    const gost = stran === 'gost' ? vrednost : niziGost
    if (stran === 'domaci') nastaviNiziDomaci(vrednost)
    else nastaviNiziGost(vrednost)
    const skupaj = dom !== '' && gost !== '' ? Number(dom) + Number(gost) : 0
    nastaviTockeNizov((prejsnje) => vrsticeZaIzid(prejsnje, skupaj))
  }

  const shrani = useMutation({
    mutationFn: (nizi: NizVnos[] | null) =>
      srecanjaApi.vnesiRezultat(tekma.id, {
        izidTip: izid === 'IGRANO' ? null : izid,
        dobljeniNiziDomaci: izid === 'IGRANO' ? Number(niziDomaci) : null,
        dobljeniNiziGost: izid === 'IGRANO' ? Number(niziGost) : null,
        zmagovalecStran: izid === 'IGRANO' ? null : zmagovalec,
        nizi,
      }),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['srecanje', idSrecanje] })
      odjemalec.invalidateQueries({ queryKey: ['srecanja'] })
      odjemalec.invalidateQueries({ queryKey: ['lestvica'] })
      onZapri()
    },
  })

  function obOddaji(e: FormEvent) {
    e.preventDefault()
    nastaviNapakoVnosa(null)

    if (izid !== 'IGRANO' || !vnasamTocke) {
      shrani.mutate(null)
      return
    }

    if (tockeNizov.some((niz) => niz.tocke1 === '' || niz.tocke2 === '')) {
      nastaviNapakoVnosa('Vnesi točke vseh nizov ali izklopi vnos točk.')
      return
    }
    const nizi = tockeNizov.map((niz) => ({
      tocke1: Number(niz.tocke1),
      tocke2: Number(niz.tocke2),
    }))
    const napaka = preveriNize(nizi, Number(niziDomaci), Number(niziGost), zaZmago)
    if (napaka) {
      nastaviNapakoVnosa(napaka)
      return
    }
    shrani.mutate(nizi)
  }

  return (
    <ModalnoOkno naslov={`Rezultat – ${tekma.oznaka}`} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="srecanje__pari">
          <strong>{imeStrani(tekma, 'DOMACI')}</strong> proti <strong>{imeStrani(tekma, 'GOST')}</strong>
        </p>
        <div>
          <button type="button" className="gumb gumb--majhen" onClick={onMenjava}>
            Menjava igralcev
          </button>
        </div>

        <label className="obrazec__polje">
          <span>Izid</span>
          <select value={izid} onChange={(d) => nastaviIzid(d.target.value as typeof izid)}>
            <option value="IGRANO">Odigrano</option>
            <option value="PREDAJA">Predaja</option>
            <option value="BREZ_BOJA">Brez boja (w.o.)</option>
            <option value="DISKVALIFIKACIJA">Diskvalifikacija</option>
          </select>
        </label>

        {izid === 'IGRANO' ? (
          <>
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Dobljeni nizi (domači)</span>
                <input type="number" min={0} value={niziDomaci}
                  onChange={(d) => obSpremembiNizov('domaci', d.target.value)} required />
              </label>
              <label className="obrazec__polje">
                <span>Dobljeni nizi (gost)</span>
                <input type="number" min={0} value={niziGost}
                  onChange={(d) => obSpremembiNizov('gost', d.target.value)} required />
              </label>
            </div>

            <label className="obrazec__potrditev">
              <input
                type="checkbox"
                checked={vnasamTocke}
                onChange={(d) => nastaviVnasamTocke(d.target.checked)}
              />
              <span>Vnesi tudi točke po nizih</span>
            </label>

            {vnasamTocke && odigranihNizov > 0 && (
              <TockeNizov vrstice={tockeNizov} nastaviVrstice={nastaviTockeNizov} />
            )}
          </>
        ) : (
          <label className="obrazec__polje">
            <span>Zmagovalec</span>
            <select value={zmagovalec} onChange={(d) => nastaviZmagovalca(d.target.value as StranEkipe)}>
              <option value="DOMACI">Domači ({imeStrani(tekma, 'DOMACI')})</option>
              <option value="GOST">Gost ({imeStrani(tekma, 'GOST')})</option>
            </select>
          </label>
        )}

        <p className="namig">Najboljši od {tekma.steviloNizov} nizov (za zmago {zaZmago}).</p>
        {napakaVnosa && <div className="napaka">{napakaVnosa}</div>}
        <SporociloNapake napaka={shrani.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shrani.isPending}>Shrani</button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function PostavaUredi({ podrobno }: { podrobno: SrecanjePodrobnoDto }) {
  const odjemalec = useQueryClient()
  const s = podrobno.srecanje

  const zacetna = (stran: StranEkipe) => {
    const rez: Record<string, string> = {}
    podrobno.postave.filter((p) => p.stran === stran).forEach((p) => { rez[p.pozicija] = String(p.idIgralec) })
    return rez
  }
  const zacetneDvojice = (stran: StranEkipe) =>
    new Set(podrobno.postave.filter((p) => p.stran === stran && p.vDvojici).map((p) => p.pozicija))

  const [domaci, nastaviDomaci] = useState<Record<string, string>>(() => zacetna('DOMACI'))
  const [gost, nastaviGost] = useState<Record<string, string>>(() => zacetna('GOST'))
  const [dvojiceD, nastaviDvojiceD] = useState<Set<string>>(() => zacetneDvojice('DOMACI'))
  const [dvojiceG, nastaviDvojiceG] = useState<Set<string>>(() => zacetneDvojice('GOST'))

  const shrani = useMutation({
    mutationFn: () => {
      const mesta: MestoVnos[] = []
      podrobno.pozicijeDomaci.forEach((poz) =>
        mesta.push({ stran: 'DOMACI', pozicija: poz, idIgralec: Number(domaci[poz]), vDvojici: jeVDvojici('DOMACI', poz) }))
      podrobno.pozicijeGost.forEach((poz) =>
        mesta.push({ stran: 'GOST', pozicija: poz, idIgralec: Number(gost[poz]), vDvojici: jeVDvojici('GOST', poz) }))
      return srecanjaApi.nastaviPostavo(s.id, { mesta })
    },
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['srecanje', s.id] }),
  })

  function jeVDvojici(stran: StranEkipe, poz: string): boolean {
    if (!podrobno.izbiraDvojice) return true // vsi igrajo dvojice (npr. Corbillon)
    return (stran === 'DOMACI' ? dvojiceD : dvojiceG).has(poz)
  }

  function preklopiDvojico(stran: StranEkipe, poz: string) {
    const [mnozica, nastavi] = stran === 'DOMACI' ? [dvojiceD, nastaviDvojiceD] : [dvojiceG, nastaviDvojiceG]
    const nova = new Set(mnozica)
    if (nova.has(poz)) nova.delete(poz)
    else nova.add(poz)
    nastavi(nova)
  }

  /* Shranjena postava tekme sestavi znova, zato vpisane menjave odpadejo. */
  const imaMenjave = podrobno.tekme.some((t) => t.menjavaDomaci || t.menjavaGost)
  const vsaIzbrana =
    podrobno.pozicijeDomaci.every((p) => domaci[p]) && podrobno.pozicijeGost.every((p) => gost[p])
  const dvojiceOk =
    !podrobno.izbiraDvojice ||
    (dvojiceD.size === podrobno.stVDvojici && dvojiceG.size === podrobno.stVDvojici)

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Postava</h2>
        <span className="sekcija__meta">Določi jo pred prvim rezultatom</span>
      </div>
      <div className="postava">
        <StranPostava
          naslov={s.domaci}
          pozicije={podrobno.pozicijeDomaci}
          kader={podrobno.kaderDomaci}
          izbrano={domaci}
          nastaviIzbrano={nastaviDomaci}
          izbiraDvojice={podrobno.izbiraDvojice}
          dvojice={dvojiceD}
          preklopi={(poz) => preklopiDvojico('DOMACI', poz)}
        />
        <StranPostava
          naslov={s.gost}
          pozicije={podrobno.pozicijeGost}
          kader={podrobno.kaderGost}
          izbrano={gost}
          nastaviIzbrano={nastaviGost}
          izbiraDvojice={podrobno.izbiraDvojice}
          dvojice={dvojiceG}
          preklopi={(poz) => preklopiDvojico('GOST', poz)}
        />
      </div>
      {podrobno.izbiraDvojice && (
        <p className="namig">Označi {podrobno.stVDvojici} igralca na vsaki strani za dvojice.</p>
      )}
      {imaMenjave && (
        <p className="namig">
          Ponovna shranitev postave tekme sestavi znova — vpisane menjave pri tem odpadejo.
        </p>
      )}
      <SporociloNapake napaka={shrani.error} />
      <div className="obrazec__gumbi">
        <button className="gumb gumb--glavni" disabled={!vsaIzbrana || !dvojiceOk || shrani.isPending}
          onClick={() => shrani.mutate()}>
          Shrani postavo in generiraj tekme
        </button>
      </div>
    </div>
  )
}

function StranPostava({
  naslov,
  pozicije,
  kader,
  izbrano,
  nastaviIzbrano,
  izbiraDvojice,
  dvojice,
  preklopi,
}: {
  naslov: string
  pozicije: string[]
  kader: { idIgralec: number; polnoIme: string }[]
  izbrano: Record<string, string>
  nastaviIzbrano: (v: Record<string, string>) => void
  izbiraDvojice: boolean
  dvojice: Set<string>
  preklopi: (poz: string) => void
}) {
  return (
    <div className="postava__stran">
      <h3>{naslov}</h3>
      {pozicije.map((poz) => (
        <div key={poz} className="postava__mesto">
          <span className="postava__oznaka">{poz}</span>
          <select
            value={izbrano[poz] ?? ''}
            onChange={(d) => nastaviIzbrano({ ...izbrano, [poz]: d.target.value })}
          >
            <option value="">— igralec —</option>
            {kader.map((k) => (
              <option key={k.idIgralec} value={k.idIgralec}>{k.polnoIme}</option>
            ))}
          </select>
          {izbiraDvojice && (
            <label className="postava__dvojice" title="V dvojicah">
              <input type="checkbox" checked={dvojice.has(poz)} onChange={() => preklopi(poz)} />
              <span>2×</span>
            </label>
          )}
        </div>
      ))}
      {kader.length === 0 && <p className="namig">Kader je prazen – dodaj igralce v ekipo.</p>}
    </div>
  )
}
