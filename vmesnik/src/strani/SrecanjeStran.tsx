/* Podroben pogled srecanja: postava (za administratorja, dokler ni rezultatov)
   in zapisnik - seznam posamicnih tekem z vnosom rezultatov. */
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, srecanjaApi } from '../api/zahteve'
import type {
  IzidTekme,
  MestoVnos,
  SrecanjePodrobnoDto,
  StranEkipe,
  TekmaSrecanjaDto,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { SpremembaElo } from '../komponente/SpremembaElo'

export function SrecanjeStran() {
  const { id } = useParams()
  const idSrecanje = Number(id)
  const { jeAdmin, jeOrganizator, smemUrejati } = useAvtentikacija()
  const podrobno = useQuery({
    queryKey: ['srecanje', idSrecanje],
    queryFn: () => srecanjaApi.podrobno(idSrecanje),
  })

  /* Za urejanje potrebujemo lastnistvo lige srecanja; poizvedbo sprozimo le
     za morebitne urejevalce. */
  const idLiga = podrobno.data?.srecanje.idLiga
  const liga = useQuery({
    queryKey: ['liga', idLiga],
    queryFn: () => ligeApi.najdi(idLiga!),
    enabled: idLiga != null && (jeAdmin || jeOrganizator),
  })

  if (podrobno.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (podrobno.error || !podrobno.data) return <SporociloNapake napaka={podrobno.error} />

  const p = podrobno.data
  const s = p.srecanje
  // organizator sme upravljati srecanja svoje (ali klubske) lige, admin vse
  const smem = jeAdmin || (!!liga.data && smemUrejati(liga.data.idLastnik, liga.data.idKlubLastnik))
  const imaRezultate = p.tekme.some((t) => t.status === 'KONCANA')
  const lahkoUrejaPostavo = smem && s.status !== 'KONCANO' && !imaRezultate
  /* Listki so smiselni le, ko je postava določena in kaka tekma še čaka. */
  const imaZaTiskanje = p.tekme.some((t) => t.status === 'CAKA')

  return (
    <section className="srecanje">
      <div className="srecanje__glava">
        <span className="srecanje__ekipa srecanje__ekipa--desno">{s.domaci}</span>
        <span className="srecanje__izid">
          {p.tekme.length > 0 ? `${s.dobljeneDomaci} : ${s.dobljeneGost}` : 'vs'}
        </span>
        <span className="srecanje__ekipa">{s.gost}</span>
      </div>
      <p className="srecanje__meta">{s.kolo}. kolo · {statusOznaka(s.status)}</p>

      {smem && imaZaTiskanje && (
        <p className="srecanje__dejanja">
          <Link to={`/srecanja/${idSrecanje}/listki`} className="gumb gumb--majhen">
            🖨 Listki
          </Link>
        </p>
      )}

      {lahkoUrejaPostavo && <PostavaUredi podrobno={p} />}

      {p.tekme.length === 0 ? (
        <p className="obvestilo">
          Postava še ni določena. {smem ? 'Določi jo zgoraj.' : 'Čaka na organizatorja.'}
        </p>
      ) : (
        <Zapisnik podrobno={p} jeAdmin={smem} />
      )}
    </section>
  )
}

function statusOznaka(status: string): string {
  return status === 'KONCANO' ? 'končano' : status === 'POTEKA' ? 'poteka' : 'razpored'
}

function Zapisnik({ podrobno, jeAdmin }: { podrobno: SrecanjePodrobnoDto; jeAdmin: boolean }) {
  const [urejana, nastaviUrejano] = useState<TekmaSrecanjaDto | null>(null)
  const s = podrobno.srecanje
  const koncano = s.status === 'KONCANO'

  return (
    <div className="plosca">
      <div className="tabela-ovoj">
        <table className="tabela srecanje__tabela">
          <thead>
            <tr>
              <th className="lestvica__stevilka">#</th>
              <th>Domači</th>
              <th className="lestvica__stevilka">Izid</th>
              <th>Gost</th>
              {jeAdmin && !koncano && <th></th>}
            </tr>
          </thead>
          <tbody>
            {podrobno.tekme.map((t) => (
              <tr key={t.id} className={t.status === 'NEODIGRANA' ? 'srecanje__vrsta--neodigrana' : undefined}>
                <td className="lestvica__stevilka srecanje__oznaka">{t.oznaka}</td>
                <td className={t.zmagovalecStran === 'DOMACI' ? 'srecanje__zmaga' : undefined}>
                  {imeStrani(t, 'DOMACI')}
                  <SpremembaElo vrednost={t.spremembaEloDomaci} />
                </td>
                <td className="lestvica__stevilka">
                  {t.status === 'KONCANA'
                    ? `${t.dobljeniNiziDomaci} : ${t.dobljeniNiziGost}`
                    : t.status === 'NEODIGRANA'
                      ? '—'
                      : jeAdmin && !koncano
                        ? '·'
                        : '–'}
                </td>
                <td className={t.zmagovalecStran === 'GOST' ? 'srecanje__zmaga' : undefined}>
                  {imeStrani(t, 'GOST')}
                  <SpremembaElo vrednost={t.spremembaEloGost} />
                </td>
                {jeAdmin && !koncano && (
                  <td>
                    {t.status === 'CAKA' && (
                      <button className="gumb gumb--majhen" onClick={() => nastaviUrejano(t)}>Vnesi</button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {urejana && (
        <RezultatOkno tekma={urejana} idSrecanje={s.id} onZapri={() => nastaviUrejano(null)} />
      )}
    </div>
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
}: {
  tekma: TekmaSrecanjaDto
  idSrecanje: number
  onZapri: () => void
}) {
  const odjemalec = useQueryClient()
  const [izid, nastaviIzid] = useState<'IGRANO' | Exclude<IzidTekme, 'IGRANO' | 'PROSTO'>>('IGRANO')
  const [niziDomaci, nastaviNiziDomaci] = useState('')
  const [niziGost, nastaviNiziGost] = useState('')
  const [zmagovalec, nastaviZmagovalca] = useState<StranEkipe>('DOMACI')

  const shrani = useMutation({
    mutationFn: () =>
      srecanjaApi.vnesiRezultat(tekma.id, {
        izidTip: izid === 'IGRANO' ? null : izid,
        dobljeniNiziDomaci: izid === 'IGRANO' ? Number(niziDomaci) : null,
        dobljeniNiziGost: izid === 'IGRANO' ? Number(niziGost) : null,
        zmagovalecStran: izid === 'IGRANO' ? null : zmagovalec,
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
    shrani.mutate()
  }

  return (
    <ModalnoOkno naslov={`Rezultat – ${tekma.oznaka}`} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="srecanje__pari">
          <strong>{imeStrani(tekma, 'DOMACI')}</strong> proti <strong>{imeStrani(tekma, 'GOST')}</strong>
        </p>

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
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Dobljeni nizi (domači)</span>
              <input type="number" min={0} value={niziDomaci}
                onChange={(d) => nastaviNiziDomaci(d.target.value)} required />
            </label>
            <label className="obrazec__polje">
              <span>Dobljeni nizi (gost)</span>
              <input type="number" min={0} value={niziGost}
                onChange={(d) => nastaviNiziGost(d.target.value)} required />
            </label>
          </div>
        ) : (
          <label className="obrazec__polje">
            <span>Zmagovalec</span>
            <select value={zmagovalec} onChange={(d) => nastaviZmagovalca(d.target.value as StranEkipe)}>
              <option value="DOMACI">Domači ({imeStrani(tekma, 'DOMACI')})</option>
              <option value="GOST">Gost ({imeStrani(tekma, 'GOST')})</option>
            </select>
          </label>
        )}

        <p className="namig">Najboljši od {tekma.steviloNizov} nizov (za zmago {Math.floor(tekma.steviloNizov / 2) + 1}).</p>
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

  const vsaIzbrana =
    podrobno.pozicijeDomaci.every((p) => domaci[p]) && podrobno.pozicijeGost.every((p) => gost[p])
  const dvojiceOk =
    !podrobno.izbiraDvojice ||
    (dvojiceD.size === podrobno.stVDvojici && dvojiceG.size === podrobno.stVDvojici)

  return (
    <div className="plosca">
      <h2>Postava</h2>
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
