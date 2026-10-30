/* Okno »Pokrovitelji« lige ali turnirja (V56): seznam do osmih pokroviteljev z
   logotipom, imenom in neobvezno povezavo; eden je lahko glavni. Vrstni red
   seznama je vrstni red na strani in na tisku.

   Logotipe pokroviteljev jemlje iz iste knjižnice kot logotip tekmovanja
   (slikeApi): organizator, ki vodi štiri lige istega pokrovitelja, ga naloži
   enkrat. Seznam se shrani v celoti - strežnik ga preveri, preden stari
   zamenja. */
import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi, slikeApi, turnirjiApi } from '../api/zahteve'
import type { PokroviteljDto, PokroviteljVnos, SlikaDto } from '../api/tipi'
import { analiziraj, meraLogotipa, naloziVPlatno, pripravi, urlSlike } from '../pomozno/slike'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

/* Meja strežnika (PokroviteljiStoritev.NAJVEC). */
export const NAJVEC_POKROVITELJEV = 8

interface Lastnosti {
  vrsta: 'liga' | 'turnir'
  id: number
  ime: string
  onZapri: () => void
}

/* Vrstica urejevalnika; ključ je lokalen, da vrstice ostanejo svoje ob
   premikanju in odstranjevanju. */
interface Vrstica {
  kljuc: number
  slika: SlikaDto
  ime: string
  povezava: string
  glavni: boolean
}

export function PokroviteljiOkno({ vrsta, id, ime, onZapri }: Lastnosti) {
  const trenutni = useQuery({
    queryKey: ['pokrovitelji', vrsta, id],
    queryFn: () => (vrsta === 'liga' ? ligeApi.pokrovitelji(id) : turnirjiApi.pokrovitelji(id)),
  })

  return (
    <ModalnoOkno srednje naslov="Pokrovitelji" podnaslov={ime} onZapri={onZapri}>
      <NapakaPoizvedbe poizvedba={trenutni} kaj="pokroviteljev" />
      {trenutni.data && <Urejevalnik vrsta={vrsta} id={id} zacetni={trenutni.data} onZapri={onZapri} />}
    </ModalnoOkno>
  )
}

function vVrstice(pokrovitelji: PokroviteljDto[]): Vrstica[] {
  return pokrovitelji.map((p, i) => ({
    kljuc: i,
    slika: p.slika,
    ime: p.ime,
    povezava: p.povezava ?? '',
    glavni: p.glavni,
  }))
}

function vVnos(vrstice: Vrstica[]): PokroviteljVnos[] {
  return vrstice.map((v) => ({
    idSlika: v.slika.id,
    ime: v.ime.trim(),
    povezava: v.povezava.trim() === '' ? null : v.povezava.trim(),
    glavni: v.glavni,
  }))
}

function Urejevalnik({
  vrsta,
  id,
  zacetni,
  onZapri,
}: {
  vrsta: 'liga' | 'turnir'
  id: number
  zacetni: PokroviteljDto[]
  onZapri: () => void
}) {
  const odjemalec = useQueryClient()
  const knjiznica = useQuery({ queryKey: ['slike', 'knjiznica'], queryFn: slikeApi.knjiznica })
  const [vrstice, nastaviVrstice] = useState<Vrstica[]>(() => vVrstice(zacetni))
  const naslednjiKljuc = useRef(zacetni.length)
  const izbirnik = useRef<HTMLInputElement>(null)
  const [nalagam, nastaviNalagam] = useState(false)
  const [napakaDatoteke, nastaviNapakoDatoteke] = useState<string | null>(null)

  const vnos = vVnos(vrstice)
  const spremenjeno = JSON.stringify(vnos) !== JSON.stringify(vVnos(vVrstice(zacetni)))
  const polno = vrstice.length >= NAJVEC_POKROVITELJEV

  function dodaj(slika: SlikaDto) {
    nastaviVrstice((prej) =>
      prej.length >= NAJVEC_POKROVITELJEV
        ? prej
        : [...prej, { kljuc: naslednjiKljuc.current++, slika, ime: '', povezava: '', glavni: false }],
    )
  }

  function spremeni(kljuc: number, delno: Partial<Vrstica>) {
    nastaviVrstice((prej) =>
      prej.map((v) => {
        if (v.kljuc === kljuc) return { ...v, ...delno }
        /* Glavni je en sam: izbira drugega prvega odznači. */
        return delno.glavni ? { ...v, glavni: false } : v
      }),
    )
  }

  function premakni(indeks: number, smer: -1 | 1) {
    nastaviVrstice((prej) => {
      const cilj = indeks + smer
      if (cilj < 0 || cilj >= prej.length) return prej
      const kopija = [...prej]
      const vmes = kopija[indeks]
      kopija[indeks] = kopija[cilj]
      kopija[cilj] = vmes
      return kopija
    })
  }

  async function obDatoteki(datoteka: File | undefined) {
    nastaviNapakoDatoteke(null)
    if (!datoteka) return
    nastaviNalagam(true)
    try {
      const platno = await naloziVPlatno(datoteka)
      const analiza = analiziraj(platno)
      /* Belo ozadje se odstrani samo, kadar ga slika očitno ima. */
      const pripravljena = await pripravi(platno, analiza, analiza.belo)
      URL.revokeObjectURL(pripravljena.url)
      const slika = await slikeApi.nalozi(pripravljena.png)
      odjemalec.invalidateQueries({ queryKey: ['slike', 'knjiznica'] })
      dodaj(slika)
    } catch (e) {
      nastaviNapakoDatoteke((e as Error).message)
    } finally {
      nastaviNalagam(false)
      if (izbirnik.current) izbirnik.current.value = ''
    }
  }

  const shrani = useMutation({
    mutationFn: () =>
      vrsta === 'liga' ? ligeApi.shraniPokrovitelje(id, vnos) : turnirjiApi.shraniPokrovitelje(id, vnos),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['pokrovitelji', vrsta, id] })
      odjemalec.invalidateQueries({ queryKey: ['slike', 'knjiznica'] })
      onZapri()
    },
  })

  const vKnjiznici = (knjiznica.data ?? []).filter((s) => !vrstice.some((v) => v.slika.id === s.id))

  return (
    <form
      className="obrazec logotip-okno pokrovitelji-okno"
      onSubmit={(d) => {
        d.preventDefault()
        shrani.mutate()
      }}
    >
      <p className="logotip-okno__uvod">
        Pokrovitelji stojijo na dnu strani in v nogi Turnirkovega zapisnika ter mreže za tisk.
        Glavni je večji in prvi. Največ {NAJVEC_POKROVITELJEV}.
      </p>

      {vrstice.length === 0 && <p className="pokrovitelji-okno__prazno">Še ni pokroviteljev.</p>}

      <ol className="pokrovitelji-okno__seznam">
        {vrstice.map((v, i) => (
          <li key={v.kljuc} className="pokrovitelji-okno__vrstica">
            <div className="pokrovitelji-okno__logo">
              <img src={urlSlike(v.slika, 'ZASLON')} alt="" {...meraLogotipa(v.slika, 56 * 56, 96, 56)} />
            </div>
            <div className="pokrovitelji-okno__polja">
              <label className="obrazec__polje">
                <span>Ime *</span>
                <input
                  value={v.ime}
                  maxLength={80}
                  required
                  onChange={(d) => spremeni(v.kljuc, { ime: d.target.value })}
                />
              </label>
              <label className="obrazec__polje">
                <span>Povezava</span>
                <input
                  value={v.povezava}
                  inputMode="url"
                  maxLength={300}
                  placeholder="https://"
                  onChange={(d) => spremeni(v.kljuc, { povezava: d.target.value })}
                />
              </label>
              <label className="obrazec__polje obrazec__polje--stikalo">
                <input
                  type="checkbox"
                  checked={v.glavni}
                  onChange={(d) => spremeni(v.kljuc, { glavni: d.target.checked })}
                />
                <span>Glavni pokrovitelj</span>
              </label>
            </div>
            <div className="pokrovitelji-okno__dejanja">
              <button type="button" className="gumb" disabled={i === 0} onClick={() => premakni(i, -1)}>
                Gor
              </button>
              <button
                type="button"
                className="gumb"
                disabled={i === vrstice.length - 1}
                onClick={() => premakni(i, 1)}
              >
                Dol
              </button>
              <button
                type="button"
                className="gumb gumb--nevaren"
                onClick={() => nastaviVrstice((prej) => prej.filter((x) => x.kljuc !== v.kljuc))}
              >
                Odstrani
              </button>
            </div>
          </li>
        ))}
      </ol>

      <div className="logotip-okno__dejanja">
        <label className={'gumb logotip-okno__izberi' + (polno || nalagam ? ' gumb--onemogocen' : '')}>
          <input
            ref={izbirnik}
            className="samo-za-bralnik"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/svg+xml,.svg"
            disabled={polno || nalagam}
            onChange={(d) => obDatoteki(d.target.files?.[0])}
          />
          {nalagam ? 'Nalagam …' : 'Dodaj logotip'}
        </label>
        <span className="logotip-okno__namig">
          PNG s prosojnim ozadjem je najboljši; belo ozadje in prazen rob odstranimo sami.
        </span>
      </div>
      {napakaDatoteke && <div className="napaka">{napakaDatoteke}</div>}

      {vKnjiznici.length > 0 && !polno && (
        <div className="logotip-okno__knjiznica" role="group" aria-labelledby="pokrovitelji-knjiznica">
          <span id="pokrovitelji-knjiznica" className="oznaka-polja">Že naloženi logotipi</span>
          <div className="logotip-okno__ploscice">
            {vKnjiznici.map((slika, i) => (
              <div key={slika.id} className="logotip-okno__ploscica">
                <button
                  type="button"
                  className="logotip-okno__slika"
                  aria-label={`Dodaj logotip ${i + 1} od ${vKnjiznici.length}`}
                  onClick={() => dodaj(slika)}
                >
                  <img src={urlSlike(slika, 'ZASLON')} alt="" loading="lazy" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
      <NapakaPoizvedbe poizvedba={knjiznica} kaj="logotipov" />

      <SporociloNapake napaka={shrani.error} />
      <div className="obrazec__noga">
        <span className="obrazec__noga-stanje">
          {vrstice.length} od {NAJVEC_POKROVITELJEV}
        </span>
        <div className="obrazec__noga-gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={!spremenjeno || shrani.isPending}>
            {shrani.isPending ? 'Shranjujem …' : 'Shrani pokrovitelje'}
          </button>
        </div>
      </div>
    </form>
  )
}
