/* Končnica lige: serije po krogih (polfinale, finale …), v vsaki seriji obe
   ekipi z mestom rednega dela in zmagami ter tekme serije s termini.

   Serija se igra do liga.koncnicaZmag zmag; tekme, ki jih ni bilo treba
   odigrati, strežnik ob odločitvi serije izbriše, zato jih stran ne kaže.
   Organizator (lastnik lige, ne pri uvoženi ligi) končnico ustvari po koncu
   rednega dela in jo razveljavi, dokler se nobena tekma ni začela; posamezni
   tekmi pred začetkom nastavi termin in zamenja domačina. */
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, srecanjaApi } from '../api/zahteve'
import type { KoncnicaSerija, KoncnicaStran, LigaDto, SrecanjeDto } from '../api/tipi'
import { oblikujTermin } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  /* Sme urejati (lastnik ali admin) in liga ni uvožena. */
  smem: boolean
}

export function KoncnicaLige({ liga, smem }: Lastnosti) {
  const odjemalec = useQueryClient()
  const koncnica = useQuery({
    queryKey: ['koncnica', liga.id],
    queryFn: () => ligeApi.koncnica(liga.id),
  })
  const [razveljavitev, nastaviRazveljavitev] = useState(false)
  const [terminZa, nastaviTerminZa] = useState<SrecanjeDto | null>(null)

  const osvezi = () => {
    odjemalec.invalidateQueries({ queryKey: ['koncnica', liga.id] })
    odjemalec.invalidateQueries({ queryKey: ['srecanja', liga.id] })
    odjemalec.invalidateQueries({ queryKey: ['liga', liga.id] })
  }
  const ustvari = useMutation({ mutationFn: () => ligeApi.ustvariKoncnico(liga.id), onSuccess: osvezi })
  const razveljavi = useMutation({ mutationFn: () => ligeApi.razveljaviKoncnico(liga.id), onSuccess: osvezi })
  const zamenjaj = useMutation({ mutationFn: (id: number) => srecanjaApi.zamenjajDomacina(id), onSuccess: osvezi })

  if (koncnica.isPending) return <p className="obvestilo">Nalaganje končnice …</p>
  if (koncnica.error) return <NapakaPoizvedbe poizvedba={koncnica} kaj="končnice" />
  const k = koncnica.data
  const serije = k.serije
  const krogi = [...new Set(serije.map((s) => s.krog))].sort((a, b) => a - b)
  const vseTekme = serije.flatMap((s) => s.tekme)
  const nobenaZaceta = serije.length > 0 && vseTekme.every((t) => t.status === 'RAZPORED')

  return (
    <div className="koncnica">
      <div className="naslovna-vrstica">
        <h2>Končnica</h2>
        <span className="sekcija__meta">
          {k.ekip} {k.ekip === 2 ? 'ekipi' : 'ekipe'} · serija do {k.zmagZaSerijo}{' '}
          {k.zmagZaSerijo === 1 ? 'zmage' : 'zmag'}
        </span>
      </div>

      {serije.length === 0 && (
        <p className="obvestilo">
          {k.pripravljenaZaZacetek
            ? 'Redni del je končan — končnico lahko ustvari organizator lige.'
            : `Končnica nastane po rednem delu: pari sledijo končni lestvici (1. proti ${k.ekip}. …).`}
        </p>
      )}
      {smem && k.pripravljenaZaZacetek && (
        <div className="stran-glava__dejanja">
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={ustvari.isPending}
            onClick={() => ustvari.mutate()}
          >
            Ustvari končnico
          </button>
        </div>
      )}
      <SporociloNapake napaka={ustvari.error ?? razveljavi.error ?? zamenjaj.error} />

      {krogi.map((krog) => (
        <div key={krog} className="koncnica__krog">
          <h3 className="liga__kolo-naslov">
            {serije.find((s) => s.krog === krog)?.imeKroga ?? `${krog}. krog`}
            <span className="liga__kolo-datum">
              {serije.filter((s) => s.krog === krog).length}{' '}
              {serije.filter((s) => s.krog === krog).length === 1 ? 'serija' : 'serije'}
            </span>
          </h3>
          {serije
            .filter((s) => s.krog === krog)
            .sort((a, b) => a.par - b.par)
            .map((s) => (
              <Serija
                key={s.id}
                serija={s}
                smem={smem}
                onTermin={nastaviTerminZa}
                onZamenjaj={(id) => zamenjaj.mutate(id)}
              />
            ))}
        </div>
      ))}

      {smem && nobenaZaceta && (
        <div className="stran-glava__dejanja">
          <button type="button" className="gumb gumb--majhen gumb--nevaren" onClick={() => nastaviRazveljavitev(true)}>
            Razveljavi končnico
          </button>
        </div>
      )}

      {razveljavitev && (
        <PotrditvenoOkno
          naslov="Razveljavi končnico"
          sporocilo="Serije in tekme končnice se zbrišejo. Redni del ostane nespremenjen; končnico lahko ustvariš znova."
          besedaPotrditve="Razveljavi končnico"
          onPotrdi={() => razveljavi.mutate()}
          onZapri={() => nastaviRazveljavitev(false)}
        />
      )}
      {terminZa && (
        <TerminTekmeOkno tekma={terminZa} onZapri={() => nastaviTerminZa(null)} onShranjeno={osvezi} />
      )}
    </div>
  )
}

function Serija({
  serija,
  smem,
  onTermin,
  onZamenjaj,
}: {
  serija: KoncnicaSerija
  smem: boolean
  onTermin: (t: SrecanjeDto) => void
  onZamenjaj: (id: number) => void
}) {
  return (
    <div className="koncnica__serija">
      <StranSerije stran={serija.stran1} zmagovalec={serija.idZmagovalec} nadomestek={`zmagovalec ${serija.par * 2 - 1}. para`} />
      <StranSerije stran={serija.stran2} zmagovalec={serija.idZmagovalec} nadomestek={`zmagovalec ${serija.par * 2}. para`} />
      {serija.tekme.length > 0 && (
        <ul className="liga__srecanja koncnica__tekme">
          {[...serija.tekme]
            .sort((a, b) => (a.tekmaVSeriji ?? 0) - (b.tekmaVSeriji ?? 0))
            .map((t) => {
              const konec = t.status === 'KONCANO'
              const domZmaga = konec && t.dobljeneDomaci > t.dobljeneGost
              const gostZmaga = konec && t.dobljeneGost > t.dobljeneDomaci
              const termin = oblikujTermin(t.predvidenZacetek)
              return (
                <li key={t.id}>
                  <span className="koncnica__tekma-meta">
                    {t.tekmaVSeriji}. tekma{termin ? ` · ${termin}` : ''}
                    {smem && t.status === 'RAZPORED' && (
                      <span className="koncnica__dejanja">
                        <button type="button" className="gumb gumb--majhen" onClick={() => onTermin(t)}>
                          Termin
                        </button>
                        <button type="button" className="gumb gumb--majhen" onClick={() => onZamenjaj(t.id)}>
                          Zamenjaj domačina
                        </button>
                      </span>
                    )}
                  </span>
                  <Link to={`/srecanja/${t.id}`} className="liga__srecanje">
                    <span
                      className={
                        'liga__srecanje-ekipa liga__srecanje-ekipa--desno' +
                        (domZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                        (gostZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                      }
                    >
                      {t.domaci}
                    </span>
                    <span className={'liga__srecanje-izid' + (konec ? '' : ' liga__srecanje-izid--caka')}>
                      {konec ? `${t.dobljeneDomaci} : ${t.dobljeneGost}` : 'vs'}
                    </span>
                    <span
                      className={
                        'liga__srecanje-ekipa' +
                        (gostZmaga ? ' liga__srecanje-ekipa--zmaga' : '') +
                        (domZmaga ? ' liga__srecanje-ekipa--poraz' : '')
                      }
                    >
                      {t.gost}
                    </span>
                    <span className="liga__srecanje-dejanje">{konec ? 'Zapisnik' : 'Postava'}</span>
                  </Link>
                </li>
              )
            })}
        </ul>
      )}
    </div>
  )
}

function StranSerije({
  stran,
  zmagovalec,
  nadomestek,
}: {
  stran: KoncnicaStran | null
  zmagovalec: number | null
  nadomestek: string
}) {
  if (!stran) {
    return (
      <div className="koncnica__stran koncnica__stran--prazna">
        <span className="koncnica__mesto">—</span>
        <span className="koncnica__ekipa">{nadomestek}</span>
        <span className="koncnica__zmage" />
      </div>
    )
  }
  const zmaga = zmagovalec != null && zmagovalec === stran.idEkipa
  const poraz = zmagovalec != null && !zmaga
  return (
    <div className={'koncnica__stran' + (zmaga ? ' koncnica__stran--zmaga' : '') + (poraz ? ' koncnica__stran--poraz' : '')}>
      <span className="koncnica__mesto">{stran.mesto != null ? `${stran.mesto}.` : ''}</span>
      <span className="koncnica__ekipa">{stran.ekipa}</span>
      <span className="koncnica__zmage">{stran.zmage}</span>
    </div>
  )
}

/* Termin ene tekme končnice — ura 00:00 pomeni »ura ni določena«. */
function TerminTekmeOkno({
  tekma,
  onZapri,
  onShranjeno,
}: {
  tekma: SrecanjeDto
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [datum, nastaviDatum] = useState(tekma.predvidenZacetek?.slice(0, 10) ?? '')
  const [ura, nastaviUro] = useState(tekma.predvidenZacetek?.slice(11, 16) ?? '')
  const shrani = useMutation({
    mutationFn: () => srecanjaApi.nastaviTermin(tekma.id, datum ? `${datum}T${ura || '00:00'}` : null),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })
  function obOddaji(d: FormEvent) {
    d.preventDefault()
    shrani.mutate()
  }
  return (
    <ModalnoOkno naslov={`${tekma.tekmaVSeriji}. tekma: ${tekma.domaci} – ${tekma.gost}`} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Datum</span>
            <input type="date" value={datum} onChange={(d) => nastaviDatum(d.target.value)} />
          </label>
          <label className="obrazec__polje">
            <span>Ura</span>
            <input type="time" value={ura} disabled={!datum} onChange={(d) => nastaviUro(d.target.value)} />
          </label>
        </div>
        <p className="namig">Brez datuma tekma ostane brez termina.</p>
        <SporociloNapake napaka={shrani.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shrani.isPending}>Shrani termin</button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
