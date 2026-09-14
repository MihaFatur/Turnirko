/* Iskalno polje s predlogi (combobox) za izbiro igralca iz šifranta.

   Zakaj ne `<select>`: po uvozu zgodovine NTZS je v šifrantu več tisoč
   igralcev in spustnega seznama ni bilo mogoče prevrteti do imena. Vpiše se
   del imena, pod poljem pa se izpišejo zadetki.

   Ujemanje teče po besedah in brez šumnikov: »miha« najde vse Mihe (tudi po
   imenu, ne le po priimku), »novak ana« pa Ano Novak ne glede na vrstni red
   vpisanega, »krizan« pa Križana — iskalnik, ki zahteva strešico, v dvorani
   ne pomaga. Ob imenu stoji klub: brez njega soimenjakov, ki jih je v
   šifrantu cele države precej, ni mogoče ločiti.

   Tipkovnica: gor/dol izbira med predlogi, Enter potrdi, Escape zapre.
   Fokus ves čas ostane v polju (vzorec combobox), zato predlogi niso gumbi,
   ampak postavke, na katere kaže aria-activedescendant.

   Komponenta živi tu in ne v EnaNaEna, ker jo potrebujeta dva pogleda
   (semafor »Ena na ena« in napoved tekme na profilu); dve kopiji istega
   vzorca bi se razšli v tipkovnici in dostopnosti. */
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'

import type { IgralecDto } from '../api/tipi'
import { besedeIskanja, ustrezaBesedam } from '../pomozno/iskanje'

/* Koliko predlogov pokaže iskalno polje. Osem je toliko, kolikor jih na
   telefonu gre na zaslon, ne da bi seznam sam po sebi drsel. */
const NAJVEC_PREDLOGOV = 8

export function IzbirnikIgralca({
  oznaka,
  vidnaOznaka = false,
  namig = 'Vpiši ime',
  igralci,
  izkljuci,
  naSpremembo,
}: {
  oznaka: string
  /* Ali naj oznaka stoji nad poljem tudi na zaslonu. Privzeto je samo za
     bralnik: v semaforju jo nadomesti veliko ime nad poljem. */
  vidnaOznaka?: boolean
  namig?: string
  igralci: IgralecDto[]
  /* Igralec, ki ga ni mogoče izbrati (nasprotna stran oz. lastnik profila):
     sam s sabo se nihče ne primerja. */
  izkljuci: number | ''
  naSpremembo: (id: number) => void
}) {
  const [iskanje, nastaviIskanje] = useState('')
  const [odprt, nastaviOdprt] = useState(false)
  const [oznacen, nastaviOznacen] = useState(0)
  const ovoj = useRef<HTMLDivElement>(null)
  const idSeznama = useId()

  const zadetki = useMemo(() => {
    const besede = besedeIskanja(iskanje)
    if (besede.length === 0) return []
    return igralci
      .filter((i) => i.id !== izkljuci)
      .filter((i) => ustrezaBesedam(`${i.ime} ${i.priimek}`, besede))
      .slice(0, NAJVEC_PREDLOGOV)
  }, [igralci, izkljuci, iskanje])

  /* Zapre se ob kliku zunaj in ob Escape — isto kot meni dejanj. Zapiranje ob
     izgubi fokusa (blur) ne pride v poštev: sprožilo bi se PRED klikom na
     predlog in ta klik bi padel v prazno. */
  useEffect(() => {
    if (!odprt) return
    function obKliku(dogodek: MouseEvent) {
      if (ovoj.current && !ovoj.current.contains(dogodek.target as Node)) nastaviOdprt(false)
    }
    function obTipki(dogodek: globalThis.KeyboardEvent) {
      if (dogodek.key === 'Escape') nastaviOdprt(false)
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprt])

  function izberi(id: number) {
    naSpremembo(id)
    /* Polje se izprazni: izbranega igralca nosi izpis nad oz. ob njem, polje
       pa je iskalnik za naslednjo zamenjavo. */
    nastaviIskanje('')
    nastaviOdprt(false)
    nastaviOznacen(0)
  }

  function obTipki(dogodek: KeyboardEvent<HTMLInputElement>) {
    if (dogodek.key === 'ArrowDown' || dogodek.key === 'ArrowUp') {
      if (zadetki.length === 0) return
      dogodek.preventDefault()
      nastaviOdprt(true)
      nastaviOznacen((prej) => {
        const naslednji = dogodek.key === 'ArrowDown' ? prej + 1 : prej - 1
        return (naslednji + zadetki.length) % zadetki.length
      })
      return
    }
    if (dogodek.key === 'Enter' && odprt && zadetki[oznacen]) {
      // brez tega bi Enter poslal obrazec, v katerem polje morda stoji
      dogodek.preventDefault()
      izberi(zadetki[oznacen].id)
    }
  }

  const iscemo = odprt && iskanje.trim() !== ''

  return (
    <div className="izbirnik-igralca" ref={ovoj}>
      <label>
        <span className={vidnaOznaka ? 'izbirnik-igralca__oznaka' : 'samo-za-bralnik'}>
          {oznaka}
        </span>
        <input
          type="text"
          role="combobox"
          autoComplete="off"
          placeholder={namig}
          aria-expanded={iscemo && zadetki.length > 0}
          aria-controls={idSeznama}
          aria-autocomplete="list"
          aria-activedescendant={
            iscemo && zadetki[oznacen] ? `${idSeznama}-${oznacen}` : undefined
          }
          value={iskanje}
          onChange={(dogodek) => {
            nastaviIskanje(dogodek.target.value)
            nastaviOznacen(0)
            nastaviOdprt(true)
          }}
          onFocus={() => nastaviOdprt(true)}
          onKeyDown={obTipki}
        />
      </label>

      {iscemo && zadetki.length > 0 && (
        <ul className="izbirnik-igralca__predlogi" id={idSeznama} role="listbox" aria-label={oznaka}>
          {zadetki.map((igralec, indeks) => (
            <li
              key={igralec.id}
              id={`${idSeznama}-${indeks}`}
              role="option"
              aria-selected={indeks === oznacen}
              className={
                'izbirnik-igralca__predlog'
                + (indeks === oznacen ? ' izbirnik-igralca__predlog--oznacen' : '')
              }
              onMouseEnter={() => nastaviOznacen(indeks)}
              onClick={() => izberi(igralec.id)}
            >
              <span className="izbirnik-igralca__predlog-ime">
                {igralec.ime} {igralec.priimek}
              </span>
              {igralec.klub && (
                <span className="izbirnik-igralca__predlog-klub">{igralec.klub.ime}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      {iscemo && zadetki.length === 0 && (
        <p className="izbirnik-igralca__predlogi izbirnik-igralca__brez-zadetka">Ni zadetka</p>
      )}
    </div>
  )
}
