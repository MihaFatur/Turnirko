/* Gumb »?« ob funkciji in njegov oblaček s kratko razlago.

   Okrogla ikona je zavestna izjema od DESIGN.md (točka 9, »brez ikon«),
   odločitev lastnika 16. 11. 2026: »?« ob funkciji je mednarodno znan znak za
   pomoč in ne nadomešča besede, ki bi jo sicer bilo treba napisati. Oblaček in
   predal ostaneta oglata kot vse ostalo.

   Gumb se izriše samo organizatorju in adminu z vklopljenimi namigi; gledalcu
   ga ni (in ne zasede prostora). Oblaček gre prek portala v body, da ga ne
   poreže `overflow` ali `content-visibility` prednika; zato ustavi širjenje
   klikov po drevesu Reacta — sicer bi klik v oblaček sprožil še vrstico ali
   okno, v katerem gumb stoji. Na telefonu je oblaček spodnji list. */
import { useEffect, useId, useLayoutEffect, useRef, useState, type SyntheticEvent } from 'react'
import { createPortal } from 'react-dom'

import { useTelefon } from '../pomozno/telefon'
import { Besedilo, SkokPomociKontekst } from './Besedilo'
import { usePomocNeobvezno } from './PomocKontekst'
import { Shema } from './sheme'
import { najdiClanek, vsebinaNamiga } from './vsebina'

/* Lucide »circle-help« (ISC licenca), poteza 1.75 kot ikone spodnje vrstice. */
export function IkonaPomoci({ velikost = 18 }: { velikost?: number }) {
  return (
    <svg
      width={velikost}
      height={velikost}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <path d="M12 17h.01" />
    </svg>
  )
}

const ROB = 16
const SIRINA = 340

interface Lega {
  levo: number
  zgoraj: number
  /* Omejitev višine, kadar oblaček ne gre cel ob gumb (vsebina se pomika). */
  najvisje?: number
  vidno: boolean
}

function ustavi(dogodek: SyntheticEvent) {
  dogodek.stopPropagation()
}

export function Namig({
  clanek,
  razdelek,
  className,
}: {
  clanek: string
  razdelek?: string
  className?: string
}) {
  const pomoc = usePomocNeobvezno()
  const kljuc = useId()
  const jeTelefon = useTelefon()
  const gumb = useRef<HTMLButtonElement>(null)
  const oblacek = useRef<HTMLDivElement>(null)
  const [lega, nastaviLego] = useState<Lega>({ levo: ROB, zgoraj: 0, vidno: false })

  const odprt = pomoc?.odprtNamig === kljuc
  const zapri = () => pomoc?.nastaviOdprtNamig(null)

  /* Lega pod gumbom, poravnana z njegovim levim robom in stisnjena v okno; če
     spodaj ni prostora, gre nad gumb. Kadar oblaček (s prikazom) ne gre cel ne
     spodaj ne zgoraj, gre na stran z več prostora in se tam pomika — gumba, ob
     katerem stoji, ne sme pokriti. Višina je znana šele po izrisu, zato prvi
     izris teče nevidno. */
  useLayoutEffect(() => {
    if (!odprt || jeTelefon) return
    function postavi() {
      const g = gumb.current?.getBoundingClientRect()
      const o = oblacek.current
      if (!g || !o) return
      const sirina = o.offsetWidth
      /* Naravna višina, tudi ko jo prejšnja lega že omejuje. */
      const visina = o.scrollHeight + (o.offsetHeight - o.clientHeight)
      const levo = Math.min(Math.max(ROB, g.left - 12), window.innerWidth - sirina - ROB)
      const spodaj = window.innerHeight - ROB - (g.bottom + 8)
      const zgoraj = g.top - 8 - ROB
      let lega: Lega
      if (visina <= spodaj) lega = { levo, zgoraj: g.bottom + 8, vidno: true }
      else if (visina <= zgoraj) lega = { levo, zgoraj: g.top - 8 - visina, vidno: true }
      else if (Math.max(spodaj, zgoraj) < 200) {
        /* Gumb tik ob robu nizkega okna: raje cel oblaček čez gumb kot drobna reža. */
        lega = { levo, zgoraj: Math.max(ROB, window.innerHeight - visina - ROB), vidno: true }
      } else if (spodaj >= zgoraj) lega = { levo, zgoraj: g.bottom + 8, najvisje: spodaj, vidno: true }
      else lega = { levo, zgoraj: ROB, najvisje: zgoraj, vidno: true }
      nastaviLego(lega)
    }
    postavi()
    window.addEventListener('resize', postavi)
    window.addEventListener('scroll', postavi, true)
    return () => {
      window.removeEventListener('resize', postavi)
      window.removeEventListener('scroll', postavi, true)
    }
  }, [odprt, jeTelefon])

  /* Klik zunaj zapre oblaček. */
  useEffect(() => {
    if (!odprt) return
    function obKliku(dogodek: PointerEvent) {
      const cilj = dogodek.target as Node
      if (oblacek.current?.contains(cilj) || gumb.current?.contains(cilj)) return
      pomoc?.nastaviOdprtNamig(null)
    }
    document.addEventListener('pointerdown', obKliku)
    return () => document.removeEventListener('pointerdown', obKliku)
  }, [odprt, pomoc])

  /* Fokus v oblaček ob odprtju; ob zaprtju nazaj na gumb, če je bil v njem. */
  useEffect(() => {
    if (!odprt) return
    const o = oblacek.current
    o?.focus({ preventScroll: true })
    const g = gumb.current
    return () => {
      if (o && o.contains(document.activeElement) && g?.isConnected) g.focus({ preventScroll: true })
    }
  }, [odprt])

  if (!pomoc || !pomoc.vidna || !pomoc.namigiVklopljeni) return null
  const podatki = najdiClanek(clanek)
  const vsebina = vsebinaNamiga(clanek, razdelek)
  if (!podatki || !vsebina || (podatki.samoAdmin && !pomoc.jeAdmin)) return null

  const idNaslova = `${kljuc}-naslov`

  return (
    <>
      <button
        ref={gumb}
        type="button"
        className={'namig-gumb' + (odprt ? ' namig-gumb--odprt' : '') + (className ? ' ' + className : '')}
        aria-label={`Pomoč: ${vsebina.naslov}`}
        aria-haspopup="dialog"
        aria-expanded={odprt}
        onClick={(dogodek) => {
          /* Gumb lahko stoji v oznaki polja ali vrstici s klikom. */
          dogodek.preventDefault()
          dogodek.stopPropagation()
          pomoc.nastaviOdprtNamig(odprt ? null : kljuc)
        }}
      >
        <IkonaPomoci />
      </button>
      {odprt &&
        createPortal(
          <>
            {jeTelefon && (
              <div
                className="namig-zastor"
                aria-hidden="true"
                onClick={(dogodek) => {
                  dogodek.stopPropagation()
                  zapri()
                }}
              />
            )}
            <div
              ref={oblacek}
              className={'namig-oblacek' + (jeTelefon ? ' namig-oblacek--telefon' : '')}
              style={
                jeTelefon
                  ? undefined
                  : {
                      left: lega.levo,
                      top: lega.zgoraj,
                      maxHeight: lega.najvisje,
                      width: Math.min(SIRINA, window.innerWidth - 2 * ROB),
                      visibility: lega.vidno ? 'visible' : 'hidden',
                    }
              }
              role="dialog"
              aria-labelledby={idNaslova}
              tabIndex={-1}
              onClick={ustavi}
              onPointerDown={ustavi}
              onMouseDown={ustavi}
              onKeyDown={ustavi}
            >
              <div className="namig-oblacek__glava">
                <span className="namig-oblacek__nad">Pomoč</span>
                <button type="button" className="namig-oblacek__zapri" onClick={zapri}>
                  Zapri ×
                </button>
              </div>
              <h2 id={idNaslova} className="namig-oblacek__naslov">
                {vsebina.naslov}
              </h2>
              <p className="namig-oblacek__besedilo">
                <SkokPomociKontekst.Provider value={(mesto) => pomoc.odpriVPredalu(mesto)}>
                  <Besedilo besedilo={vsebina.besedilo} />
                </SkokPomociKontekst.Provider>
              </p>
              {vsebina.shema && (
                <div className="namig-oblacek__shema">
                  <Shema kljuc={vsebina.shema} />
                </div>
              )}
              <button
                type="button"
                className="namig-oblacek__vec"
                onClick={() => pomoc.odpriVPredalu({ clanek, razdelek })}
              >
                Preberi več <span aria-hidden="true">→</span>
              </button>
            </div>
          </>,
          document.body,
        )}
    </>
  )
}
