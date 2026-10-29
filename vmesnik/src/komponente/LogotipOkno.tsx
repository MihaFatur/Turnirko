/* Okno »Logotip« lige ali turnirja (V54): naloži novega ali izberi že
   naloženega in vidi, kako bo videti na strani in na črno-belem tisku.

   Predogled »na tisku« je namenoma siv: rumen ali svetlo zelen logotip na
   laserskem tiskalniku izgine in organizator mora to videti doma, ne v dvorani.
   Sliko pred pošiljanjem pripravi brskalnik (pomozno/slike: obrez roba, belo
   ozadje, pomanjšanje), strežnik jo nato prebere in zapiše znova.

   Knjižnica (svoji logotipi in logotipi klubskih tekmovanj) je zato, ker je
   liga vsako sezono nov zapis: Liga Savinja A-D logotip naloži enkrat. */
import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, slikeApi, turnirjiApi } from '../api/zahteve'
import type { SlikaDto } from '../api/tipi'
import {
  analiziraj,
  dpiNaTisku,
  meraLogotipa,
  NAJMANJ_DPI,
  naloziVPlatno,
  pripravi,
  urlSlike,
  type AnalizaSlike,
  type PripravljenaSlika,
} from '../pomozno/slike'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  vrsta: 'liga' | 'turnir'
  id: number
  ime: string
  logotip: SlikaDto | null
  onZapri: () => void
}

/* Kar je izbrano v oknu: nova datoteka ali slika iz knjižnice. */
type Izbira =
  | { vrsta: 'datoteka'; platno: HTMLCanvasElement; analiza: AnalizaSlike }
  | { vrsta: 'knjiznica'; slika: SlikaDto }

const KJE = {
  liga: 'v glavi strani lige, na Turnirkovem ekipnem zapisniku in v predogledu, ko kdo deli povezavo lige',
  turnir: 'v glavi strani turnirja, na žrebu skupin in mreže za tisk ter v predogledu, ko kdo deli povezavo turnirja',
} as const

export function LogotipOkno({ vrsta, id, ime, logotip, onZapri }: Lastnosti) {
  const odjemalec = useQueryClient()
  const knjiznica = useQuery({ queryKey: ['slike', 'knjiznica'], queryFn: slikeApi.knjiznica })

  const [izbira, nastaviIzbiro] = useState<Izbira | null>(null)
  const [odstraniOzadje, nastaviOdstraniOzadje] = useState(true)
  const [pripravljena, nastaviPripravljeno] = useState<PripravljenaSlika | null>(null)
  const [napakaDatoteke, nastaviNapakoDatoteke] = useState<string | null>(null)
  const izbirnik = useRef<HTMLInputElement>(null)

  /* Priprava teče ob vsaki spremembi izbire ali kljukice ozadja; predogled
     nosi naslov objekta, ki ga je treba sprostiti, ko ga zamenja naslednji. */
  useEffect(() => {
    if (izbira?.vrsta !== 'datoteka') {
      nastaviPripravljeno(null)
      return
    }
    let preklicano = false
    pripravi(izbira.platno, izbira.analiza, odstraniOzadje)
      .then((p) => {
        if (preklicano) URL.revokeObjectURL(p.url)
        else nastaviPripravljeno(p)
      })
      .catch((e: Error) => {
        if (!preklicano) nastaviNapakoDatoteke(e.message)
      })
    return () => {
      preklicano = true
    }
  }, [izbira, odstraniOzadje])

  useEffect(() => {
    if (!pripravljena) return
    return () => URL.revokeObjectURL(pripravljena.url)
  }, [pripravljena])

  async function obDatoteki(datoteka: File | undefined) {
    nastaviNapakoDatoteke(null)
    if (!datoteka) return
    try {
      const platno = await naloziVPlatno(datoteka)
      const analiza = analiziraj(platno)
      nastaviOdstraniOzadje(analiza.belo)
      nastaviIzbiro({ vrsta: 'datoteka', platno, analiza })
    } catch (e) {
      nastaviNapakoDatoteke((e as Error).message)
    } finally {
      // isto datoteko je mogoče izbrati znova (po preklicu)
      if (izbirnik.current) izbirnik.current.value = ''
    }
  }

  function osvezi() {
    odjemalec.invalidateQueries({ queryKey: [vrsta, id] })
    odjemalec.invalidateQueries({ queryKey: [vrsta === 'liga' ? 'lige' : 'turnirji'] })
    odjemalec.invalidateQueries({ queryKey: ['slike', 'knjiznica'] })
  }

  const nastavi = (idSlika: number) =>
    vrsta === 'liga' ? ligeApi.nastaviLogotip(id, idSlika) : turnirjiApi.nastaviLogotip(id, idSlika)

  const shrani = useMutation({
    mutationFn: async () => {
      if (izbira?.vrsta === 'knjiznica') return nastavi(izbira.slika.id)
      if (!pripravljena) throw new Error('Logotip še ni pripravljen.')
      const slika = await slikeApi.nalozi(pripravljena.png)
      return nastavi(slika.id)
    },
    onSuccess: () => {
      osvezi()
      onZapri()
    },
  })

  const odstrani = useMutation({
    mutationFn: () => (vrsta === 'liga' ? ligeApi.odstraniLogotip(id) : turnirjiApi.odstraniLogotip(id)),
    onSuccess: () => {
      osvezi()
      onZapri()
    },
  })

  const izbrisi = useMutation({
    mutationFn: (idSlika: number) => slikeApi.izbrisi(idSlika),
    onSuccess: (_, idSlika) => {
      if (izbira?.vrsta === 'knjiznica' && izbira.slika.id === idSlika) nastaviIzbiro(null)
      odjemalec.invalidateQueries({ queryKey: ['slike', 'knjiznica'] })
    },
  })

  /* Kaj pokaže predogled: nova slika, izbrana iz knjižnice ali trenutni logotip. */
  const predogled: { url: string; sirina: number; visina: number } | null =
    izbira?.vrsta === 'datoteka'
      ? pripravljena
      : izbira?.vrsta === 'knjiznica'
        ? { url: urlSlike(izbira.slika, 'ZASLON'), sirina: izbira.slika.sirina, visina: izbira.slika.visina }
        : logotip
          ? { url: urlSlike(logotip, 'ZASLON'), sirina: logotip.sirina, visina: logotip.visina }
          : null

  const spremenjeno =
    izbira?.vrsta === 'datoteka'
      ? pripravljena != null
      : izbira?.vrsta === 'knjiznica' && izbira.slika.id !== logotip?.id
  const dpi = izbira?.vrsta === 'datoteka' && pripravljena != null ? dpiNaTisku(pripravljena) : null
  const majhna = dpi != null && dpi < NAJMANJ_DPI
  const slike = knjiznica.data ?? []
  const zaposleno = shrani.isPending || odstrani.isPending

  return (
    <ModalnoOkno srednje naslov="Logotip" podnaslov={ime} onZapri={onZapri}>
      <form
        className="obrazec logotip-okno"
        onSubmit={(d) => {
          d.preventDefault()
          shrani.mutate()
        }}
      >
        <p className="logotip-okno__uvod">Logotip stoji {KJE[vrsta]}.</p>

        <div className="logotip-okno__predogled" aria-live="polite">
          <div className="logotip-okno__okvir">
            <span className="oznaka-polja">Na strani</span>
            <div className="logotip-okno__papir">
              {predogled ? (
                <img
                  src={predogled.url}
                  alt="Predogled logotipa na strani"
                  {...meraLogotipa(predogled, 104 * 104, 260, 120)}
                />
              ) : (
                <span className="logotip-okno__prazno">Brez logotipa</span>
              )}
            </div>
          </div>
          <div className="logotip-okno__okvir">
            <span className="oznaka-polja">Na črno-belem tisku</span>
            <div className="logotip-okno__papir logotip-okno__papir--tisk">
              {predogled ? (
                <img
                  src={predogled.url}
                  alt="Predogled logotipa na črno-belem tisku"
                  {...meraLogotipa(predogled, 104 * 104, 260, 96)}
                />
              ) : (
                <span className="logotip-okno__prazno">Brez logotipa</span>
              )}
            </div>
          </div>
        </div>

        {izbira?.vrsta === 'datoteka' && (
          <div className="logotip-okno__priprava">
            {pripravljena && (
              <span className="logotip-okno__mera">
                {pripravljena.sirina} × {pripravljena.visina} px
                {izbira.analiza.prosojna ? ' · prosojno ozadje' : ''}
              </span>
            )}
            {izbira.analiza.belo && (
              <label className="obrazec__polje obrazec__polje--stikalo">
                <input
                  type="checkbox"
                  checked={odstraniOzadje}
                  onChange={(d) => nastaviOdstraniOzadje(d.target.checked)}
                />
                <span>Odstrani belo ozadje</span>
              </label>
            )}
            {majhna && (
              <p className="obvestilo obvestilo--opozorilo">
                Slika je za tisk majhna (na zapisniku pribl. {dpi} dpi). Na strani bo v redu,
                na papirju bo mehka — če imaš večjo, naloži njo.
              </p>
            )}
          </div>
        )}

        {napakaDatoteke && <div className="napaka">{napakaDatoteke}</div>}

        <div className="logotip-okno__dejanja">
          <label className="gumb logotip-okno__izberi">
            <input
              ref={izbirnik}
              className="samo-za-bralnik"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/svg+xml,.svg"
              onChange={(d) => obDatoteki(d.target.files?.[0])}
            />
            {izbira?.vrsta === 'datoteka' ? 'Izberi drugo datoteko' : 'Naloži datoteko'}
          </label>
          <span className="logotip-okno__namig">
            PNG s prosojnim ozadjem je najboljši; sprejme tudi JPG in SVG. Prazen rob
            odrežemo sami.
          </span>
        </div>

        {slike.length > 0 && (
          <div className="logotip-okno__knjiznica" role="group" aria-labelledby="logotip-okno-knjiznica">
            <span id="logotip-okno-knjiznica" className="oznaka-polja">Tvoji logotipi</span>
            <div className="logotip-okno__ploscice">
              {slike.map((slika, i) => {
                const izbrana =
                  izbira?.vrsta === 'knjiznica' ? izbira.slika.id === slika.id : !izbira && logotip?.id === slika.id
                return (
                  <div key={slika.id} className="logotip-okno__ploscica">
                    <button
                      type="button"
                      className={'logotip-okno__slika' + (izbrana ? ' logotip-okno__slika--izbrana' : '')}
                      aria-pressed={izbrana}
                      aria-label={`Logotip ${i + 1} od ${slike.length}`}
                      onClick={() => {
                        nastaviNapakoDatoteke(null)
                        nastaviIzbiro({ vrsta: 'knjiznica', slika })
                      }}
                    >
                      <img src={urlSlike(slika, 'ZASLON')} alt="" loading="lazy" />
                    </button>
                    {slika.id !== logotip?.id && (
                      <button
                        type="button"
                        className="logotip-okno__izbrisi"
                        disabled={izbrisi.isPending}
                        onClick={() => izbrisi.mutate(slika.id)}
                      >
                        Izbriši
                      </button>
                    )}
                  </div>
                )
              })}
            </div>
            <SporociloNapake napaka={izbrisi.error} />
          </div>
        )}
        <NapakaPoizvedbe poizvedba={knjiznica} kaj="logotipov" />

        <SporociloNapake napaka={shrani.error ?? odstrani.error} />
        <div className="obrazec__noga">
          {logotip ? (
            <button
              type="button"
              className="gumb gumb--nevaren"
              disabled={zaposleno}
              onClick={() => odstrani.mutate()}
            >
              Odstrani logotip
            </button>
          ) : (
            <span className="obrazec__noga-stanje">Tekmovanje nima logotipa</span>
          )}
          <div className="obrazec__noga-gumbi">
            <button type="button" className="gumb" onClick={onZapri}>
              Prekliči
            </button>
            <button type="submit" className="gumb gumb--glavni" disabled={!spremenjeno || zaposleno}>
              {shrani.isPending ? 'Shranjujem …' : 'Shrani logotip'}
            </button>
          </div>
        </div>
      </form>
    </ModalnoOkno>
  )
}
