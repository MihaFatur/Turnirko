/* Žreb izločilnega dela enega dogodka, narejen za tisk na vrata dvorane.

   Mreža je narisana kot na papirju: stolpec za vsako kolo, tekme med seboj
   povezane s črtami, na koncu črta za zmagovalca. Prazna mesta (igralec še ni
   znan) ostanejo črta, v katero organizator vpiše zmagovalca, zato je list
   uporaben tudi kot delovni obrazec. Odigrana tekma pokaže nize in odebeli
   zmagovalca - ob ponovnem tisku je list tudi pregled stanja.

   Črno-belo ne glede na temo, kot listki in žreb skupin (slogi v razdelku 16).
   Ležeči A4: mreža 32 igralcev gre na en list, večja se razreže na poddrevesa
   (pomozno/zrebMreze). */
import type { CSSProperties } from 'react'

import type { MrezaDto, TurnirDto } from '../api/tipi'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import {
  type MrezaStran,
  type MrezaTekmaTisk,
  sklopiZrebMreze,
  type ZrebMrezeList,
} from '../pomozno/zrebMreze'
import { vrsticaTurnirja } from '../pomozno/zrebSkupin'

export function ZrebMrezeDogodka({ mreza, turnir }: { mreza: MrezaDto; turnir?: TurnirDto }) {
  const sklop = sklopiZrebMreze(mreza)
  if (sklop === null) return null

  const dogodek = mreza.dogodek
  const ekipno = dogodek.disciplina === 'EKIPNO'
  const nadNaslovom = vrsticaTurnirja(turnir)

  return (
    <>
      {sklop.listi.map((list) => (
        <ListMreze
          key={list.kljuc}
          list={list}
          ime={dogodek.ime}
          nadNaslovom={nadNaslovom}
          podNaslovom={[
            'Izločilni del',
            `${sklop.steviloUdelezencev} ${ekipno ? sklonEkip(sklop.steviloUdelezencev) : sklonIgralcev(sklop.steviloUdelezencev)}`,
            list.del,
          ]
            .filter(Boolean)
            .join(' · ')}
        />
      ))}
    </>
  )
}

function ListMreze({
  list,
  ime,
  nadNaslovom,
  podNaslovom,
}: {
  list: ZrebMrezeList
  ime: string
  nadNaslovom: string | null
  podNaslovom: string
}) {
  /* Stolpcev: kola lista in stolpec zmagovalca. */
  const stolpcev = list.kola.length + 1
  /* Pri več kot osmih tekmah v prvem stolpcu je vrstica pod ~12 mm in pisava
     manjša, sicer se dve vrstici imen ne zmestita v vrstico. */
  const gosto = list.vrstic > 8
  const merila = { '--vrstic': list.vrstic, '--stolpcev': stolpcev } as CSSProperties

  return (
    <section className={'zreb-mreza' + (gosto ? ' zreb-mreza--gosto' : '')} style={merila}>
      <header className="zreb-glava zreb-glava--mreza">
        {nadNaslovom && <p className="zreb-glava__nad">{nadNaslovom}</p>}
        <h1 className="zreb-glava__ime">{ime}</h1>
        <p className="zreb-glava__pod">{podNaslovom}</p>
      </header>

      <div className="zreb-mreza__naslovi">
        {list.kola.map((kolo, indeks) => (
          <h2
            key={kolo.kolo}
            className={'zreb-mreza__naslov' + (indeks === 0 ? ' zreb-mreza__naslov--prvi' : '')}
          >
            {kolo.naslov}
          </h2>
        ))}
        <h2 className="zreb-mreza__naslov zreb-mreza__naslov--prvak">{list.prvak.naslov}</h2>
      </div>

      <div className="zreb-mreza__mreza">
        {list.kola.map((kolo, indeks) =>
          kolo.tekme.map((tekma) => (
            <div
              key={tekma.id}
              className={
                'zreb-mreza__tekma' +
                (indeks === 0 ? ' zreb-mreza__tekma--prvi' : '') +
                (tekma.smer ? ` zreb-mreza__tekma--${tekma.smer}` : ' zreb-mreza__tekma--zadnja')
              }
              style={{
                gridColumn: indeks + 1,
                gridRow: `${tekma.vrstica} / span ${tekma.razpon}`,
              }}
            >
              <TekmaMreze tekma={tekma} />
            </div>
          )),
        )}

        <div
          className="zreb-mreza__prvak"
          style={{ gridColumn: stolpcev, gridRow: '1 / -1' }}
        >
          <div className="zreb-mreza__prvak-crta">{list.prvak.ime}</div>
          {list.tretje && (
            <div className="zreb-mreza__tretje">
              <p className="zreb-mreza__tretje-naslov">Za 3. mesto</p>
              <TekmaMreze tekma={list.tretje} />
            </div>
          )}
        </div>
      </div>
    </section>
  )
}

function TekmaMreze({ tekma }: { tekma: MrezaTekmaTisk }) {
  return (
    <div className="zreb-mreza__okvir">
      <StranMreze stran={tekma.strani[0]} oznaka={tekma.oznaka} />
      <StranMreze stran={tekma.strani[1]} oznaka={tekma.oznaka} />
    </div>
  )
}

function StranMreze({ stran, oznaka }: { stran: MrezaStran; oznaka?: string | null }) {
  return (
    <div
      className={
        'zreb-mreza__stran' +
        (stran.zmagovalec ? ' zreb-mreza__stran--zmagovalec' : '') +
        (stran.prost ? ' zreb-mreza__stran--prost' : '')
      }
    >
      <span className="zreb-mreza__ime">
        {stran.prost ? 'prost' : (stran.ime ?? (stran.napis ? <i>{stran.napis}</i> : null))}
      </span>
      {/* »b. b.« in »predaja« stojita pri poražencu, ki izida ne razloži z nizi. */}
      {oznaka && stran.nizi !== null && !stran.zmagovalec && (
        <small className="zreb-mreza__oznaka">{oznaka}</small>
      )}
      {stran.nizi !== null && <span className="zreb-mreza__nizi">{stran.nizi}</span>}
    </div>
  )
}
