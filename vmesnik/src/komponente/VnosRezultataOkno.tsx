/* Modalno okno za vnos rezultata tekme.

   Pravila (dokoncno jih preverja zaledje, tu jih le vodimo):
   - Odigrana tekma: zmagovalec mora dobiti natanko toliko nizov, kolikor
     jih je potrebnih za zmago (npr. 3 pri "najboljsi od 5"), zato rezultat
     izbiramo s seznama veljavnih izidov in napacen vnos sploh ni mogoc.
   - Tocke po nizih so neobvezne; ce so vnesene, jih mora biti natanko
     toliko, kot je odigranih nizov.
   - Predaja: delni rezultat pred koncem tekme + zmagovalec.
   - Brez boja / diskvalifikacija: samo zmagovalec, nizi se pripisejo. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { tekmeApi } from '../api/zahteve'
import { nizovZaZmago } from '../api/tipi'
import type { IzidTekme, NizVnos, TekmaDto, VnosRezultata } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  tekma: TekmaDto
  onZapri: () => void
  /* Poklicano po uspesnem vnosu - stran osvezi podatke dogodka. */
  onShranjeno: () => void
}

/* Izidi, ki jih rocno vnasa sodnik (PROSTO doloci sistem sam pri zrebu). */
const ROCNI_IZIDI: { vrednost: IzidTekme; oznaka: string }[] = [
  { vrednost: 'IGRANO', oznaka: 'Odigrana tekma' },
  { vrednost: 'PREDAJA', oznaka: 'Predaja med igro' },
  { vrednost: 'BREZ_BOJA', oznaka: 'Brez boja (nasprotnik ni nastopil)' },
  { vrednost: 'DISKVALIFIKACIJA', oznaka: 'Diskvalifikacija' },
]

export function VnosRezultataOkno({ tekma, onZapri, onShranjeno }: Lastnosti) {
  const zaZmago = nizovZaZmago(tekma.steviloNizov)
  const ime1 = tekma.udelezenec1?.polnoIme ?? 'Igralec 1'
  const ime2 = tekma.udelezenec2?.polnoIme ?? 'Igralec 2'

  const [izidTip, nastaviIzidTip] = useState<IzidTekme>('IGRANO')
  /* Koncni rezultat v obliki "3:1" - izbran s seznama veljavnih izidov. */
  const [rezultat, nastaviRezultat] = useState('')
  const [vnasamTocke, nastaviVnasamTocke] = useState(false)
  const [tockeNizov, nastaviTockeNizov] = useState<{ tocke1: string; tocke2: string }[]>([])
  /* Za posebne izide: katera stran je zmagala (1 ali 2). */
  const [zmagovalecStran, nastaviZmagovalecStran] = useState<'' | '1' | '2'>('')
  /* Delni rezultat ob predaji. */
  const [predajaNizi1, nastaviPredajaNizi1] = useState('0')
  const [predajaNizi2, nastaviPredajaNizi2] = useState('0')
  const [napakaVnosa, nastaviNapakoVnosa] = useState<string | null>(null)

  const shranjevanje = useMutation({
    mutationFn: (vnos: VnosRezultata) => tekmeApi.vnesiRezultat(tekma.id, vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Vsi veljavni koncni rezultati, najprej zmage prvega igralca. */
  const veljavniRezultati1: string[] = []
  const veljavniRezultati2: string[] = []
  for (let porazencevi = 0; porazencevi < zaZmago; porazencevi++) {
    veljavniRezultati1.push(`${zaZmago}:${porazencevi}`)
    veljavniRezultati2.push(`${porazencevi}:${zaZmago}`)
  }

  /* Ali je posamezen niz veljaven namiznoteniski rezultat (kot v zaledju):
     do 11 z razliko vsaj 2, pri podaljsku (nad 11) razlika natanko 2. */
  function veljavenNiz(tocke1: number, tocke2: number): boolean {
    const vec = Math.max(tocke1, tocke2)
    const manj = Math.min(tocke1, tocke2)
    return manj >= 0 && ((vec === 11 && manj <= 9) || (vec > 11 && vec - manj === 2))
  }

  /* Preveri nize po istih pravilih kot zaledje, da uporabnik napako vidi
     takoj, brez klica streznika. Vrne sporocilo napake ali null. */
  function preveriNize(nizi: NizVnos[], nizi1: number, nizi2: number): string | null {
    let dobljeni1 = 0
    let dobljeni2 = 0
    for (let i = 0; i < nizi.length; i++) {
      const { tocke1, tocke2 } = nizi[i]
      if (tocke1 === tocke2) return `${i + 1}. niz ne more biti neodločen.`
      if (!veljavenNiz(tocke1, tocke2)) {
        return `${i + 1}. niz (${tocke1}:${tocke2}) ni veljaven namiznoteniški rezultat.`
      }
      // tekma se konca v trenutku odlocitve - noben niz se ne igra po tem
      if (dobljeni1 === zaZmago || dobljeni2 === zaZmago) {
        return `Tekma je bila odločena že po ${i} nizih (${dobljeni1}:${dobljeni2}), zato se ${
          i + 1
        }. niz ne bi igral. Popravi rezultat ali nize.`
      }
      if (tocke1 > tocke2) dobljeni1++
      else dobljeni2++
    }
    if (dobljeni1 !== nizi1 || dobljeni2 !== nizi2) {
      return `Točke po nizih dajo ${dobljeni1}:${dobljeni2}, izbran pa je rezultat ${nizi1}:${nizi2}.`
    }
    return null
  }

  /* Ob spremembi rezultata prilagodi stevilo vrstic za tocke nizov. */
  function obSpremembiRezultata(nov: string) {
    nastaviRezultat(nov)
    if (!nov) return
    const [nizi1, nizi2] = nov.split(':').map(Number)
    const steviloVrstic = nizi1 + nizi2
    nastaviTockeNizov((prejsnje) => {
      const nove = prejsnje.slice(0, steviloVrstic)
      while (nove.length < steviloVrstic) nove.push({ tocke1: '', tocke2: '' })
      return nove
    })
  }

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapakoVnosa(null)

    if (izidTip === 'IGRANO') {
      if (!rezultat) {
        nastaviNapakoVnosa('Izberi končni rezultat.')
        return
      }
      const [nizi1, nizi2] = rezultat.split(':').map(Number)

      let nizi: NizVnos[] | null = null
      if (vnasamTocke) {
        if (tockeNizov.some((niz) => niz.tocke1 === '' || niz.tocke2 === '')) {
          nastaviNapakoVnosa('Vnesi točke vseh nizov ali izklopi vnos točk.')
          return
        }
        nizi = tockeNizov.map((niz) => ({
          tocke1: Number(niz.tocke1),
          tocke2: Number(niz.tocke2),
        }))
        const napaka = preveriNize(nizi, nizi1, nizi2)
        if (napaka) {
          nastaviNapakoVnosa(napaka)
          return
        }
      }

      shranjevanje.mutate({
        izidTip: 'IGRANO',
        dobljeniNizi1: nizi1,
        dobljeniNizi2: nizi2,
        zmagovalecStran: null,
        nizi,
      })
      return
    }

    /* Posebni izidi potrebujejo zmagovalca. */
    if (zmagovalecStran !== '1' && zmagovalecStran !== '2') {
      nastaviNapakoVnosa('Izberi zmagovalca.')
      return
    }
    shranjevanje.mutate({
      izidTip,
      dobljeniNizi1: izidTip === 'PREDAJA' ? Number(predajaNizi1) : null,
      dobljeniNizi2: izidTip === 'PREDAJA' ? Number(predajaNizi2) : null,
      zmagovalecStran: Number(zmagovalecStran) as 1 | 2,
      nizi: null,
    })
  }

  /* Moznosti delnega rezultata ob predaji: 0 .. zaZmago-1 dobljenih nizov. */
  const delniNizi = Array.from({ length: zaZmago }, (_, indeks) => String(indeks))

  return (
    <ModalnoOkno naslov="Vnos rezultata" onZapri={onZapri}>
      <p className="modal__podnaslov">
        {ime1} : {ime2}
        <span className="modal__namig"> (najboljši od {tekma.steviloNizov} nizov)</span>
      </p>

      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Način zaključka</span>
          <select
            value={izidTip}
            onChange={(dogodek) => nastaviIzidTip(dogodek.target.value as IzidTekme)}
          >
            {ROCNI_IZIDI.map((izid) => (
              <option key={izid.vrednost} value={izid.vrednost}>
                {izid.oznaka}
              </option>
            ))}
          </select>
        </label>

        {izidTip === 'IGRANO' && (
          <>
            <label className="obrazec__polje">
              <span>Končni rezultat (nizi)</span>
              <select
                value={rezultat}
                onChange={(dogodek) => obSpremembiRezultata(dogodek.target.value)}
              >
                <option value="">— izberi —</option>
                <optgroup label={`Zmaga: ${ime1}`}>
                  {veljavniRezultati1.map((moznost) => (
                    <option key={moznost} value={moznost}>
                      {moznost}
                    </option>
                  ))}
                </optgroup>
                <optgroup label={`Zmaga: ${ime2}`}>
                  {veljavniRezultati2.map((moznost) => (
                    <option key={moznost} value={moznost}>
                      {moznost}
                    </option>
                  ))}
                </optgroup>
              </select>
            </label>

            <label className="obrazec__potrditev">
              <input
                type="checkbox"
                checked={vnasamTocke}
                onChange={(dogodek) => nastaviVnasamTocke(dogodek.target.checked)}
              />
              <span>Vnesi tudi točke po nizih</span>
            </label>

            {vnasamTocke && rezultat && (
              <div className="obrazec__nizi">
                {tockeNizov.map((niz, indeks) => (
                  <div className="obrazec__niz" key={indeks}>
                    <span className="obrazec__niz-oznaka">{indeks + 1}. niz</span>
                    <input
                      type="number"
                      min={0}
                      max={99}
                      placeholder="11"
                      value={niz.tocke1}
                      onChange={(dogodek) =>
                        nastaviTockeNizov((prejsnje) =>
                          prejsnje.map((vrstica, i) =>
                            i === indeks ? { ...vrstica, tocke1: dogodek.target.value } : vrstica,
                          ),
                        )
                      }
                    />
                    <span>:</span>
                    <input
                      type="number"
                      min={0}
                      max={99}
                      placeholder="7"
                      value={niz.tocke2}
                      onChange={(dogodek) =>
                        nastaviTockeNizov((prejsnje) =>
                          prejsnje.map((vrstica, i) =>
                            i === indeks ? { ...vrstica, tocke2: dogodek.target.value } : vrstica,
                          ),
                        )
                      }
                    />
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {izidTip !== 'IGRANO' && (
          <>
            <div className="obrazec__polje">
              <span>Zmagovalec</span>
              <div className="obrazec__radio-skupina">
                <label>
                  <input
                    type="radio"
                    name="zmagovalec"
                    checked={zmagovalecStran === '1'}
                    onChange={() => nastaviZmagovalecStran('1')}
                  />
                  {ime1}
                </label>
                <label>
                  <input
                    type="radio"
                    name="zmagovalec"
                    checked={zmagovalecStran === '2'}
                    onChange={() => nastaviZmagovalecStran('2')}
                  />
                  {ime2}
                </label>
              </div>
            </div>

            {izidTip === 'PREDAJA' && (
              <label className="obrazec__polje">
                <span>Delni rezultat ob predaji (nizi)</span>
                <div className="obrazec__niz">
                  <select
                    value={predajaNizi1}
                    onChange={(dogodek) => nastaviPredajaNizi1(dogodek.target.value)}
                  >
                    {delniNizi.map((vrednost) => (
                      <option key={vrednost} value={vrednost}>
                        {vrednost}
                      </option>
                    ))}
                  </select>
                  <span>:</span>
                  <select
                    value={predajaNizi2}
                    onChange={(dogodek) => nastaviPredajaNizi2(dogodek.target.value)}
                  >
                    {delniNizi.map((vrednost) => (
                      <option key={vrednost} value={vrednost}>
                        {vrednost}
                      </option>
                    ))}
                  </select>
                </div>
              </label>
            )}
          </>
        )}

        {napakaVnosa && <div className="napaka">{napakaVnosa}</div>}
        <SporociloNapake napaka={shranjevanje.error} />

        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            {shranjevanje.isPending ? 'Shranjujem …' : 'Shrani rezultat'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
