/* Mreža števk in njeno učenje (branje listkov F6.4, samo admin; stran Meritev branja).

   Katera različica mreže bere listke, ali učenje teče, koliko novih števk čaka
   (z vadnic in potrjenih listkov) in zgodovina različic: kako je vsaka brala
   preizkusne števke, zlati nabor in MNIST pred in po učenju, ter zakaj ni bila
   sprejeta. »Uči zdaj« požene učenje takoj (ne čaka noči), »Vrni prejšnjo«
   umakne različico v uporabi, če se na turnirju izkaže za slabšo. Pod gumbi so
   napake mreže po števkah (NapakeMreze, F6.6). */
import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { MrezeStevkDto, RazlicicaMrezeDto } from '../api/tipi'
import { meritevBranjaApi } from '../api/zahteve'
import { delez, prejPotem, stanjeRazlicice, trajanje, type StanjeRazlicice } from '../pomozno/branjeListkov'
import { oblikujTermin, oblikujUro } from '../pomozno/oblikovanje'
import { KLJUC_NAPAK, NapakeMreze } from './NapakeMreze'
import { SporociloNapake } from './SporociloNapake'

const KLJUC = ['mreze-stevk']

const OZNAKA_STANJA: Record<StanjeRazlicice, string> = {
  'v-uporabi': 'V uporabi',
  sprejeta: 'Sprejeta',
  umaknjena: 'Umaknjena',
  zavrnjena: 'Zavrnjena',
  prekinjena: 'Prekinjena',
}

export function MrezeStevk() {
  const odjemalec = useQueryClient()
  const [potrjujemVrnitev, nastaviPotrjujemVrnitev] = useState(false)
  const stanje = useQuery({
    queryKey: KLJUC,
    queryFn: meritevBranjaApi.mreze,
    // učenje traja nekaj minut; dokler teče, stran sama pokaže izid; po premoru se
    // »Uči zdaj« spet odklene brez osvežitve strani
    refetchInterval: (poizvedba) => {
      const d = poizvedba.state.data
      if (d?.uciSe) return 5000
      if (d?.uciZdajOd) return Math.max(1000, new Date(d.uciZdajOd).getTime() - Date.now() + 1000)
      return false
    },
  })
  // ko se učenje konča, se lahko zamenja mreža v uporabi: meritev spodaj jo navaja
  const ucilSe = useRef(false)
  const uciSe = stanje.data?.uciSe ?? false
  useEffect(() => {
    if (ucilSe.current && !uciSe) {
      void odjemalec.invalidateQueries({ queryKey: ['meritev-branja'] })
      void odjemalec.invalidateQueries({ queryKey: KLJUC_NAPAK })
    }
    ucilSe.current = uciSe
  }, [uciSe, odjemalec])
  const shrani = (s: MrezeStevkDto) => {
    odjemalec.setQueryData(KLJUC, s)
    // tabela »Po različicah mreže«, meritev nabora in napake navajajo mrežo v uporabi
    void odjemalec.invalidateQueries({ queryKey: ['meritev-branja'] })
    void odjemalec.invalidateQueries({ queryKey: KLJUC_NAPAK })
  }
  const uci = useMutation({ mutationFn: meritevBranjaApi.uciZdaj, onSuccess: shrani })
  const vrni = useMutation({
    mutationFn: meritevBranjaApi.vrniPrejsnjo,
    onSuccess: (s) => {
      shrani(s)
      nastaviPotrjujemVrnitev(false)
    },
  })

  const s = stanje.data
  const vUporabi = s?.razlicice.find((r) => r.id === s.vUporabi)
  const manjka = s ? Math.max(0, s.najmanjNovih - s.novihStevk) : 0

  return (
    <div className="mreze-stevk">
      <SporociloNapake napaka={stanje.error ?? uci.error ?? vrni.error} />
      {stanje.isPending && <p className="obvestilo">Nalaganje …</p>}
      {s && (
        <>
          <p className="meritev-branja__besedilo">
            <strong>V uporabi:</strong>{' '}
            {s.vUporabi === null
              ? 'vgrajena mreža (naučena samo na ameriški zbirki števk MNIST).'
              : `različica ${s.vUporabi}${vUporabi ? `, naučena ${oblikujTermin(vUporabi.ustvarjenoOb)}` : ''}.`}
          </p>
          <p className="meritev-branja__besedilo">
            Števk v učenju: {s.stevkVUcenju} (z vadnic {s.stevkVadnice}). Novih od zadnjega učenja: {s.novihStevk},
            učenje jih potrebuje vsaj {s.najmanjNovih}. Učenje teče vsako noč ob 3.50 ali takoj z »Uči zdaj«. Nova
            mreža se sprejme samo, če na preizkusnih števkah in zlatem naboru ne bere slabše.
          </p>

          {s.uciSe ? (
            <p className="obvestilo">
              Učenje teče od {oblikujUro(s.ucenjeOd)} ({s.nacinUcenja === 'ROCNO' ? 'na zahtevo' : 'nočno'}). Traja
              nekaj minut; izid se pokaže v zgodovini spodaj.
            </p>
          ) : (
            s.sporocilo && (
              <p className="namig">
                {s.sporocilo} {s.sporociloOb && <span>({oblikujTermin(s.sporociloOb)})</span>}
              </p>
            )
          )}

          <div className="meritev-branja__gumbi">
            <button
              type="button"
              className="gumb gumb--glavni"
              disabled={s.uciSe || manjka > 0 || s.uciZdajOd !== null || uci.isPending || vrni.isPending}
              onClick={() => uci.mutate()}
            >
              {uci.isPending ? 'Zaganjam …' : 'Uči zdaj'}
            </button>
            {s.vUporabi !== null && !potrjujemVrnitev && (
              <button
                type="button"
                className="gumb"
                disabled={s.uciSe || vrni.isPending}
                onClick={() => nastaviPotrjujemVrnitev(true)}
              >
                Vrni prejšnjo
              </button>
            )}
          </div>
          {!s.uciSe && manjka > 0 && (
            <p className="namig">
              Do učenja manjka novih števk: {manjka}. Odobri še kak list vadnice ali počakaj na potrjene listke.
            </p>
          )}
          {!s.uciSe && s.uciZdajOd && (
            <p className="namig">Učenje je bilo pravkar pognano. Znova ga lahko poženeš ob {oblikujUro(s.uciZdajOd)}.</p>
          )}

          {potrjujemVrnitev && (
            <div className="mreze-stevk__potrditev">
              <p className="meritev-branja__besedilo">
                Bralnik bo spet bral s prejšnjo sprejeto različico (ali vgrajeno, če prejšnje ni). Različica{' '}
                {s.vUporabi} ostane v zgodovini kot umaknjena in se ne vrne sama.
              </p>
              <div className="meritev-branja__gumbi">
                <button type="button" className="gumb gumb--nevaren" disabled={vrni.isPending}
                  onClick={() => vrni.mutate()}>
                  {vrni.isPending ? 'Vračam …' : `Umakni različico ${s.vUporabi}`}
                </button>
                <button type="button" className="gumb" onClick={() => nastaviPotrjujemVrnitev(false)}>
                  Prekliči
                </button>
              </div>
            </div>
          )}

          <NapakeMreze />

          <h3 className="meritev-branja__podnaslov">Zgodovina učenja</h3>
          {s.razlicice.length === 0 ? (
            <p className="namig">Mreža se še ni učila.</p>
          ) : (
            <Zgodovina razlicice={s.razlicice} vUporabi={s.vUporabi} />
          )}
        </>
      )}
    </div>
  )
}

/* Seznam namesto tabele: na telefonu mora biti stanje in razlog zavrnitve viden brez
   vodoravnega pomikanja (sedem stolpcev ne gre v 390 px). */
function Zgodovina({ razlicice, vUporabi }: { razlicice: RazlicicaMrezeDto[]; vUporabi: number | null }) {
  return (
    <ol className="mreze-stevk__zgodovina">
      {razlicice.map((r) => {
        const st = stanjeRazlicice(r, vUporabi)
        return (
          <li key={r.id} className="mreze-stevk__razlicica">
            <div className="mreze-stevk__glava">
              <span className="mreze-stevk__ime">Različica {r.id}</span>
              <span className={`znacka mreze-stevk__stanje mreze-stevk__stanje--${st}`}>{OZNAKA_STANJA[st]}</span>
            </div>
            <p className="meritev-branja__datum">
              {oblikujTermin(r.ustvarjenoOb)} · {r.nacin === 'ROCNO' ? 'na zahtevo' : 'nočno'} · števk {r.vzorcev}
              {r.trajanjeMs !== null && ` · ${trajanje(r.trajanjeMs)}`}
            </p>
            <dl className="mreze-stevk__meritve">
              <div>
                <dt>Preizkusne števke{r.preizkusnih !== null && ` (${r.preizkusnih})`}</dt>
                <dd>{r.prekinjena ? delez(r.natancnostPrej) : prejPotem(r.natancnostPrej, r.natancnost)}</dd>
              </div>
              <div>
                <dt>Zlati nabor{r.zlatih !== null && ` (${r.zlatih} listkov)`}</dt>
                <dd>{prejPotem(r.zlatiPrej, r.zlati)}</dd>
              </div>
              <div>
                <dt>MNIST</dt>
                <dd>{prejPotem(r.mnistPrej, r.mnist)}</dd>
              </div>
            </dl>
            {r.razlog && <p className="mreze-stevk__razlog">{r.razlog}</p>}
            {st === 'umaknjena' && r.umaknjenaOb && (
              <p className="meritev-branja__datum">Umaknjena {oblikujTermin(r.umaknjenaOb)}.</p>
            )}
          </li>
        )
      })}
    </ol>
  )
}
