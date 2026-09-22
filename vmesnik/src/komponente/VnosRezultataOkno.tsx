/* Modalno okno za vnos rezultata tekme.

   Pravila (dokoncno jih preverja zaledje, tu jih le vodimo):
   - Odigrana tekma: zmagovalec mora dobiti natanko toliko nizov, kolikor
     jih je potrebnih za zmago (npr. 3 pri "najboljsi od 5"), zato rezultat
     izbiramo s seznama veljavnih izidov in napacen vnos sploh ni mogoc.
   - Tocke po nizih so neobvezne; ce so vnesene, jih mora biti natanko
     toliko, kot je odigranih nizov. Vnos in preverba sta v skupni komponenti
     TockeNizov - isti kot pri ligaski tekmi, ker so pravila niza ista.
   - Predaja: delni rezultat pred koncem tekme + zmagovalec.
   - Brez boja / diskvalifikacija: samo zmagovalec, nizi se pripisejo.

   POPRAVEK (lastnost popravek) je isti obrazec nad ze koncano tekmo: polja so
   napolnjena s shranjenim izidom, shrani pa se na drugo koncno tocko, ki
   zmagovalca ne sme spremeniti (glej TekmaStoritev.popraviRezultat). Isti
   obrazec zato, ker je popravek isti vpis - drugacen je samo njegov ucinek. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { tekmeApi } from '../api/zahteve'
import { imeUdelezenca, nizovZaZmago, priimekUdelezenca } from '../api/tipi'
import type { IzidTekme, NizVnos, TekmaDto, VnosRezultata } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { TockeNizov, preveriNize, vrsticeZaIzid, type VrsticaNiza } from './TockeNizov'

interface Lastnosti {
  tekma: TekmaDto
  onZapri: () => void
  /* Poklicano po uspesnem vnosu - stran osvezi podatke dogodka. */
  onShranjeno: () => void
  /* Ekipna tekma: izid nastane iz zapisnika srecanja, neposredno sta mogoca
     samo brez boja in diskvalifikacija (TekmaStoritev.vnesiRezultat). */
  samoBrezIgre?: boolean
  /* Popravek ze shranjenega rezultata (tekma je KONCANA). */
  popravek?: boolean
}

/* Izidi, ki jih rocno vnasa sodnik (PROSTO doloci sistem sam pri zrebu). */
const ROCNI_IZIDI: { vrednost: IzidTekme; oznaka: string }[] = [
  { vrednost: 'IGRANO', oznaka: 'Odigrana tekma' },
  { vrednost: 'PREDAJA', oznaka: 'Predaja med igro' },
  { vrednost: 'BREZ_BOJA', oznaka: 'Brez boja (nasprotnik ni nastopil)' },
  { vrednost: 'DISKVALIFIKACIJA', oznaka: 'Diskvalifikacija' },
]

export function VnosRezultataOkno({
  tekma,
  onZapri,
  onShranjeno,
  samoBrezIgre = false,
  popravek = false,
}: Lastnosti) {
  const zaZmago = nizovZaZmago(tekma.steviloNizov)
  const ime1 = imeUdelezenca(tekma.udelezenec1) ?? 'Igralec 1'
  const ime2 = imeUdelezenca(tekma.udelezenec2) ?? 'Igralec 2'
  const izidi = samoBrezIgre
    ? ROCNI_IZIDI.filter((izid) => izid.vrednost === 'BREZ_BOJA' || izid.vrednost === 'DISKVALIFIKACIJA')
    : ROCNI_IZIDI

  /* Popravek izhaja iz shranjenega izida - organizator popravlja tisto, kar
     je vpisano, in ne izpolnjuje praznega obrazca znova. */
  const shranjenaStran: '' | '1' | '2' =
    tekma.idZmagovalcaPrijave === null
      ? ''
      : tekma.idZmagovalcaPrijave === tekma.udelezenec1?.idPrijave
        ? '1'
        : '2'

  const [izidTip, nastaviIzidTip] = useState<IzidTekme>(
    popravek ? (tekma.izidTip ?? 'IGRANO') : samoBrezIgre ? 'BREZ_BOJA' : 'IGRANO',
  )
  /* Koncni rezultat v obliki "3:1" - izbran s seznama veljavnih izidov. */
  const [rezultat, nastaviRezultat] = useState(
    popravek && tekma.izidTip === 'IGRANO' ? `${tekma.dobljeniNizi1}:${tekma.dobljeniNizi2}` : '',
  )
  const [vnasamTocke, nastaviVnasamTocke] = useState(popravek && tekma.nizi.length > 0)
  /* Pri popravku je rezultat že izbran, zato se vrstice ne morejo pojaviti ob
     njegovi izbiri — pripravimo jih takoj, sicer bi organizator, ki točke
     dodaja k tekmi brez njih, dobil prazno mrežo in nerazumljivo napako. */
  const [tockeNizov, nastaviTockeNizov] = useState<VrsticaNiza[]>(() =>
    popravek && tekma.izidTip === 'IGRANO'
      ? vrsticeZaIzid(
          tekma.nizi.map((niz) => ({ tocke1: String(niz.tocke1), tocke2: String(niz.tocke2) })),
          tekma.dobljeniNizi1 + tekma.dobljeniNizi2,
        )
      : [],
  )
  /* Za posebne izide: katera stran je zmagala (1 ali 2). */
  const [zmagovalecStran, nastaviZmagovalecStran] = useState<'' | '1' | '2'>(
    popravek ? shranjenaStran : '',
  )
  /* Delni rezultat ob predaji. */
  const [predajaNizi1, nastaviPredajaNizi1] = useState(
    popravek && tekma.izidTip === 'PREDAJA' ? String(tekma.dobljeniNizi1) : '0',
  )
  const [predajaNizi2, nastaviPredajaNizi2] = useState(
    popravek && tekma.izidTip === 'PREDAJA' ? String(tekma.dobljeniNizi2) : '0',
  )
  const [napakaVnosa, nastaviNapakoVnosa] = useState<string | null>(null)

  const shranjevanje = useMutation({
    mutationFn: (vnos: VnosRezultata) =>
      popravek ? tekmeApi.popraviRezultat(tekma.id, vnos) : tekmeApi.vnesiRezultat(tekma.id, vnos),
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

  /* Ob spremembi rezultata prilagodi stevilo vrstic za tocke nizov. */
  function obSpremembiRezultata(nov: string) {
    nastaviRezultat(nov)
    if (!nov) return
    const [nizi1, nizi2] = nov.split(':').map(Number)
    nastaviTockeNizov((prejsnje) => vrsticeZaIzid(prejsnje, nizi1 + nizi2))
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
        const napaka = preveriNize(nizi, nizi1, nizi2, zaZmago)
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
    <ModalnoOkno
      naslov={popravek ? 'Popravek rezultata' : samoBrezIgre ? 'Izid brez igre' : 'Vnos rezultata'}
      onZapri={onZapri}
    >
      <p className="modal__podnaslov">
        {ime1} : {ime2}
        {!samoBrezIgre && (
          <span className="modal__namig"> (najboljši od {tekma.steviloNizov} nizov)</span>
        )}
      </p>
      {samoBrezIgre && !popravek && (
        <p className="namig">
          Ekipna tekma dobi izid iz zapisnika srečanja. Tu zapišeš samo, da ekipa ni
          nastopila ali je bila izključena — srečanje, ki se še ni začelo, se ob tem izbriše.
        </p>
      )}
      {popravek && (
        <p className="namig">
          Popravek sme spremeniti izid, ne pa zmagovalca — po njem je turnir tekel naprej.
          Turnirko rating se preračuna od dneva te tekme naprej, kar lahko traja nekaj
          trenutkov.
        </p>
      )}

      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Način zaključka</span>
          <select
            value={izidTip}
            onChange={(dogodek) => nastaviIzidTip(dogodek.target.value as IzidTekme)}
          >
            {izidi.map((izid) => (
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
              <TockeNizov
                vrstice={tockeNizov}
                nastaviVrstice={nastaviTockeNizov}
                priimek1={priimekUdelezenca(tekma.udelezenec1)}
                priimek2={priimekUdelezenca(tekma.udelezenec2)}
              />
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
            {shranjevanje.isPending
              ? 'Shranjujem …'
              : popravek
                ? 'Shrani popravek'
                : 'Shrani rezultat'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
