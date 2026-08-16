/* Prožilnik »⋯« s spustnim menijem dejanj v lepljivi glavi telefona.

   Na telefonu za vrsto gumbov v 56 px pasu ni prostora, glavno dejanje strani
   (»+ Dogodek«, »Izvedi žreb«) pa mora ostati vidno. Preostala dejanja gredo
   zato pod tri pike. Pogoji (»Mogoče šele, ko so zaključeni vsi dogodki«) so tu
   onemogočena postavka s pojasnilom in ne posebna vrstica v verzalkah.

   Videz in obnašanje sta ista kot pri uporabniškem meniju (zapre se ob kliku
   zunaj in ob Escape) - zato si delita razrede .uporabnik-meni*. */
import { useEffect, useRef, useState, type ReactNode } from 'react'

interface Lastnosti {
  /* Za bralnik zaslona: kaj meni ponuja (»Dejanja turnirja«). */
  naslov: string
  /* Postavke; zapri() zapre meni po izbiri dejanja. */
  children: (zapri: () => void) => ReactNode
}

export function MeniDejanj({ naslov, children }: Lastnosti) {
  const [odprt, nastaviOdprt] = useState(false)
  const ovoj = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!odprt) return
    function obKliku(dogodek: MouseEvent) {
      if (ovoj.current && !ovoj.current.contains(dogodek.target as Node)) nastaviOdprt(false)
    }
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key === 'Escape') nastaviOdprt(false)
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprt])

  return (
    <div className="meni-dejanj" ref={ovoj}>
      <button
        type="button"
        className="meni-dejanj__gumb"
        aria-haspopup="menu"
        aria-expanded={odprt}
        aria-label={naslov}
        onClick={() => nastaviOdprt((prej) => !prej)}
      >
        ⋯
      </button>
      {odprt && (
        <div className="uporabnik-meni" role="menu">
          {children(() => nastaviOdprt(false))}
        </div>
      )}
    </div>
  )
}
