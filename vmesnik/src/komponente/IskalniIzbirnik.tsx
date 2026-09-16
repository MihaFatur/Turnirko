/* Iskalno polje s predlogi (combobox): izbira iz dolgega seznama z vpisom
   imena namesto s spustnim seznamom ali seznamom s kljukicami.

   Zakaj ne `<select>`: po uvozu zgodovine NTZS je v šifrantu več tisoč
   igralcev, klubov je sedemdeset in lig nekaj sto - spustnega seznama ni
   bilo mogoče prevrteti do imena. Vpiše se del imena, pod poljem pa se
   izpišejo zadetki. Ujemanje teče po besedah in brez šumnikov
   (`pomozno/iskanje.ts`); zadetki na začetku besede gredo naprej.

   Polje pozna dve vlogi in nič več:
   - DEJANJE (brez `izbrano`): izbira nekaj sproži (igralec gre v kader,
     semafor dobi igralca) in polje se izprazni za naslednjo. Izbrano nosi
     izpis ob polju.
   - VREDNOST (`izbrano` podan, tudi null): polje obrazca. V polju stoji ime
     izbranega; ob fokusu se besedilo označi, da ga vpis zamenja. Kdor polje
     izprazni in ga zapusti, izbor počisti (`naPraznjenje`); kdor vpiše del
     imena in odide brez izbire, dobi prejšnji izbor nazaj - razen kadar je
     vpisal ime natanko, takrat velja kot izbira. Polje, v katerem stoji
     besedilo, ki ni izbor, bi bilo past.

   Tipkovnica: gor/dol izbira med predlogi, Enter potrdi, Escape zapre (in v
   oknu zapre samo predloge, ne okna). Fokus ves čas ostane v polju (vzorec
   combobox), zato predlogi niso gumbi, ampak postavke, na katere kaže
   aria-activedescendant; pritisk miške na seznam fokusa ne vzame.

   Predlogi ležijo ČEZ vsebino (absolutno), da vsak vtipkani znak ne premika
   vsebine pod poljem. Kadar pod poljem ni prostora (polje na dnu okna, na
   telefonu tipkovnica), se odprejo navzgor - sicer bi jih bilo treba iskati
   z drsenjem, čemur se polje ravno izogiba. */
import {
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from 'react'

import { besedeIskanja, ustrezaBesedam, zadetihZacetkov } from '../pomozno/iskanje'

/* Koliko predlogov pokaže polje. Osem je toliko, kolikor jih na telefonu gre
   na zaslon, ne da bi seznam sam po sebi drsel. */
const NAJVEC_PREDLOGOV = 8
/* Višina vrstice predloga in cele plasti - za odločitev, ali gre plast pod
   polje ali nad njega. */
const VISINA_PREDLOGA = 50
const NAJVISJA_PLAST = NAJVEC_PREDLOGOV * VISINA_PREDLOGA
const NAJNIZJA_PLAST = 2.5 * VISINA_PREDLOGA
const ODMIK_OD_ROBA = 8

export interface MoznostIzbirnika {
  id: number
  ime: string
  /* Drobna druga vrstica: klub pri igralcu, »zdaj pod« pri ligi … */
  podrobnost?: string | null
  /* Besedilo, po katerem se išče; privzeto ime. */
  iskalno?: string
  /* Vidna, a je ni mogoče izbrati (npr. igralec, ki že ima dostop). */
  onemogocena?: boolean
}

export function IskalniIzbirnik({
  oznaka,
  vidnaOznaka = false,
  vObrazcu = false,
  namig = 'Vpiši ime',
  moznosti,
  brezIskanja,
  izbrano,
  naIzbiro,
  naPraznjenje,
}: {
  oznaka: string
  /* Ali naj oznaka stoji nad poljem tudi na zaslonu. Privzeto je samo za
     bralnik: v semaforju jo nadomesti veliko ime nad poljem. */
  vidnaOznaka?: boolean
  /* Polje stoji v obrazcu: oznaka in polje imata obliko ostalih polj
     (`.obrazec__polje`) in se v vrstici poravnata z njimi. */
  vObrazcu?: boolean
  namig?: string
  moznosti: MoznostIzbirnika[]
  /* Kaj ponuditi, preden je kaj vpisano (npr. predlogi po priimku). Brez
     tega prazno polje ne ponudi ničesar - začetek abecede ni predlog. */
  brezIskanja?: MoznostIzbirnika[]
  /* undefined = polje je dejanje; število ali null = polje nosi vrednost. */
  izbrano?: number | null
  naIzbiro: (id: number) => void
  naPraznjenje?: () => void
}) {
  const nosiVrednost = izbrano !== undefined
  const [iskanje, nastaviIskanje] = useState('')
  const [odprt, nastaviOdprt] = useState(false)
  /* Samo pri vrednosti: ali uporabnik v polju ureja (sicer polje kaže izbor)
     in ali je od fokusa kaj vtipkal (sicer označeno ime izbora ni iskanje). */
  const [urejam, nastaviUrejam] = useState(false)
  const [tipkano, nastaviTipkano] = useState(false)
  const [oznacen, nastaviOznacen] = useState(0)
  const [navzgor, nastaviNavzgor] = useState(false)
  const [visina, nastaviVisino] = useState(NAJVISJA_PLAST)
  const ovoj = useRef<HTMLDivElement>(null)
  const polje = useRef<HTMLInputElement>(null)
  const idPolja = useId()
  const idSeznama = useId()

  const izbor =
    nosiVrednost && izbrano !== null
      ? (moznosti.find((m) => m.id === izbrano) ?? brezIskanja?.find((m) => m.id === izbrano))
      : undefined

  const iskano = nosiVrednost && !tipkano ? '' : iskanje
  const besede = useMemo(() => besedeIskanja(iskano), [iskano])

  const zadetki = useMemo(() => {
    if (besede.length === 0) return brezIskanja ?? []
    return moznosti
      .filter((m) => ustrezaBesedam(m.iskalno ?? m.ime, besede))
      .map((m, indeks) => ({ m, indeks, tocke: zadetihZacetkov(m.iskalno ?? m.ime, besede) }))
      // stabilno: ob enakih točkah ostane vrstni red klicatelja (abeceda)
      .sort((a, b) => b.tocke - a.tocke || a.indeks - b.indeks)
      .slice(0, NAJVEC_PREDLOGOV)
      .map(({ m }) => m)
  }, [moznosti, brezIskanja, besede])

  const plastVidna = odprt && (besede.length > 0 || zadetki.length > 0)

  /* Smer in višina plasti se izmerita, preden brskalnik sliko izriše, da
     plast ne skoči z ene strani polja na drugo. */
  useLayoutEffect(() => {
    if (!plastVidna || !polje.current) return
    const okvir = polje.current.getBoundingClientRect()
    /* Na telefonu rob prekrivata lepljiva glava in spodnja vrstica; predlog
       pod njima ni viden, čeprav je »na zaslonu«. */
    const spodnjaVrstica = document.querySelector('.spodnja-vrstica')?.getBoundingClientRect()
    const glava = document.querySelector('.glava-telefon')?.getBoundingClientRect()
    const visinaOkna = window.visualViewport?.height ?? window.innerHeight
    const dno = spodnjaVrstica?.height ? Math.min(visinaOkna, spodnjaVrstica.top) : visinaOkna
    const vrh = glava?.height ? glava.bottom : 0
    const potrebno = Math.min(NAJVISJA_PLAST, Math.max(zadetki.length, 1) * VISINA_PREDLOGA + 2)
    const spodaj = dno - okvir.bottom - ODMIK_OD_ROBA
    const zgoraj = okvir.top - vrh - ODMIK_OD_ROBA
    const gor = spodaj < potrebno && zgoraj > spodaj
    nastaviNavzgor(gor)
    nastaviVisino(Math.max(NAJNIZJA_PLAST, Math.min(NAJVISJA_PLAST, gor ? zgoraj : spodaj)))
  }, [plastVidna, zadetki.length])

  /* Pri drsnem seznamu (malo prostora) mora biti označeni predlog viden. */
  useEffect(() => {
    if (!plastVidna) return
    document.getElementById(`${idSeznama}-${oznacen}`)?.scrollIntoView({ block: 'nearest' })
  }, [plastVidna, oznacen, idSeznama])

  function izberi(moznost: MoznostIzbirnika) {
    if (moznost.onemogocena) return
    naIzbiro(moznost.id)
    nastaviIskanje('')
    nastaviOdprt(false)
    nastaviOznacen(0)
    nastaviUrejam(false)
    nastaviTipkano(false)
  }

  /* Opusti urejanje brez posledic (Escape). Pri dejanju vpisano ostane v
     polju, pri vrednosti se vrne izbor. */
  function preklici() {
    nastaviOdprt(false)
    nastaviOznacen(0)
    nastaviUrejam(false)
    nastaviTipkano(false)
    if (nosiVrednost) nastaviIskanje('')
  }

  /* Zapusti polje (klik drugam, tabulator): pri vrednosti se vpisano razreši
     v izbor, praznjenje ali vrnitev prejšnjega izbora. */
  function zapri() {
    if (nosiVrednost && urejam && tipkano) {
      const vpisano = besedeIskanja(iskanje)
      if (vpisano.length === 0) {
        naPraznjenje?.()
      } else {
        const niz = vpisano.join(' ')
        const natanko = moznosti.find(
          (m) => !m.onemogocena && besedeIskanja(m.ime).join(' ') === niz,
        )
        if (natanko && natanko.id !== izbrano) naIzbiro(natanko.id)
      }
    }
    preklici()
  }

  /* Klik zunaj zapre predloge tudi tam, kjer dotik drugam polju fokusa ne
     vzame (iOS). Če ga ima, ga odvzamemo in zapiranje opravi onBlur - tako se
     zapri ne izvede dvakrat. */
  const zadnjiZapri = useRef(zapri)
  zadnjiZapri.current = zapri
  const aktiven = odprt || urejam
  useEffect(() => {
    if (!aktiven) return
    function obPritisku(dogodek: PointerEvent) {
      if (ovoj.current?.contains(dogodek.target as Node)) return
      if (document.activeElement === polje.current) polje.current?.blur()
      else zadnjiZapri.current()
    }
    document.addEventListener('pointerdown', obPritisku)
    return () => document.removeEventListener('pointerdown', obPritisku)
  }, [aktiven])

  function obTipki(dogodek: KeyboardEvent<HTMLInputElement>) {
    if (dogodek.key === 'ArrowDown' || dogodek.key === 'ArrowUp') {
      dogodek.preventDefault()
      if (!odprt) {
        nastaviOdprt(true)
        return
      }
      if (zadetki.length === 0) return
      nastaviOznacen((prej) => {
        const naslednji = dogodek.key === 'ArrowDown' ? prej + 1 : prej - 1
        return (naslednji + zadetki.length) % zadetki.length
      })
      return
    }
    if (dogodek.key === 'Enter' && plastVidna && zadetki[oznacen]) {
      // brez tega bi Enter poslal obrazec, v katerem polje morda stoji
      dogodek.preventDefault()
      izberi(zadetki[oznacen])
      return
    }
    if (dogodek.key === 'Escape' && (plastVidna || tipkano)) {
      // okno, v katerem polje stoji, se zapre šele ob naslednjem Escape
      dogodek.preventDefault()
      dogodek.stopPropagation()
      preklici()
    }
  }

  const razredOznake = vObrazcu
    ? 'iskalni-izbirnik__napis'
    : vidnaOznaka
      ? 'iskalni-izbirnik__oznaka'
      : 'samo-za-bralnik'

  return (
    <div
      className={'iskalni-izbirnik' + (vObrazcu ? ' obrazec__polje iskalni-izbirnik--obrazec' : '')}
      ref={ovoj}
    >
      <label htmlFor={idPolja} className={razredOznake}>
        {oznaka}
      </label>
      <div className="iskalni-izbirnik__sidro">
        <input
          ref={polje}
          id={idPolja}
          className={vObrazcu ? undefined : 'iskalni-izbirnik__vnos'}
          type="text"
          role="combobox"
          autoComplete="off"
          placeholder={namig}
          aria-expanded={plastVidna && zadetki.length > 0}
          aria-controls={idSeznama}
          aria-autocomplete="list"
          aria-activedescendant={
            plastVidna && zadetki[oznacen] ? `${idSeznama}-${oznacen}` : undefined
          }
          value={nosiVrednost && !urejam ? (izbor?.ime ?? '') : iskanje}
          onChange={(dogodek) => {
            nastaviIskanje(dogodek.target.value)
            nastaviUrejam(true)
            nastaviTipkano(true)
            nastaviOznacen(0)
            nastaviOdprt(true)
          }}
          onFocus={(dogodek) => {
            nastaviOdprt(true)
            if (nosiVrednost && !urejam) {
              nastaviUrejam(true)
              nastaviTipkano(false)
              nastaviIskanje(izbor?.ime ?? '')
              dogodek.target.select()
            }
          }}
          onClick={() => nastaviOdprt(true)}
          onBlur={zapri}
          onKeyDown={obTipki}
        />

        {plastVidna && (
          <ul
            className={
              'iskalni-izbirnik__predlogi' + (navzgor ? ' iskalni-izbirnik__predlogi--gor' : '')
            }
            style={{ maxHeight: visina }}
            id={idSeznama}
            role="listbox"
            aria-label={oznaka}
            // fokus ostane v polju, sicer bi onBlur zaprl plast pred klikom
            onMouseDown={(dogodek) => dogodek.preventDefault()}
          >
            {zadetki.map((moznost, indeks) => (
              <li
                key={moznost.id}
                id={`${idSeznama}-${indeks}`}
                role="option"
                aria-selected={indeks === oznacen}
                aria-disabled={moznost.onemogocena || undefined}
                className={
                  'iskalni-izbirnik__predlog'
                  + (indeks === oznacen ? ' iskalni-izbirnik__predlog--oznacen' : '')
                  + (moznost.onemogocena ? ' iskalni-izbirnik__predlog--onemogocen' : '')
                }
                onMouseEnter={() => nastaviOznacen(indeks)}
                onClick={() => izberi(moznost)}
              >
                <span className="iskalni-izbirnik__predlog-ime">{moznost.ime}</span>
                {moznost.podrobnost && (
                  <span className="iskalni-izbirnik__predlog-podrobnost">{moznost.podrobnost}</span>
                )}
              </li>
            ))}
            {zadetki.length === 0 && (
              <li className="iskalni-izbirnik__brez-zadetka" role="presentation">
                Ni zadetka
              </li>
            )}
          </ul>
        )}
      </div>
    </div>
  )
}
