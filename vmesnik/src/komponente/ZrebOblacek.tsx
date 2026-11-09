/* Oznaka žreba z oblačkom: gumb (npr. »Ročni žreb«), ki ob prehodu miške oz. ob
   tapu odpre pojasnilo. Uporabljata ga ročni žreb lige (LigaStran) in žreb
   Skupine (SvRegija), zato se povsod vede enako. Pojasnilo je »children«, ker je
   lahko odstavek ali seznam. */
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

/* Ročni žreb lige in turnirja: gledalec mora izvedeti, od kod razpored - sicer bi
   igralec, ki je pare dobil po pošti, sklepal, da je aplikacija izžrebala nove. */
export const POJASNILO_ROCNEGA_ZREBA =
  'Žreb ni bil naključen — razpored je vpisal organizator. Za pojasnilo se obrnite na organizatorja.'

export function ZrebOblacek({
  oznaka,
  levo = false,
  children,
}: {
  oznaka: string
  /* Oblaček sega v desno od levega roba, kadar oznaka stoji ob levem robu glave. */
  levo?: boolean
  children: ReactNode
}) {
  const [odprta, nastaviOdprta] = useState(false)
  const ovoj = useRef<HTMLSpanElement>(null)
  const idOblacka = useId()

  /* Na dotik se oblaček odpre s tapom in zapre s tapom drugam (ali Escape) -
     prehoda miške telefon nima. */
  useEffect(() => {
    if (!odprta) return
    function obKliku(dogodek: MouseEvent) {
      if (ovoj.current && !ovoj.current.contains(dogodek.target as Node)) nastaviOdprta(false)
    }
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key === 'Escape') nastaviOdprta(false)
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprta])

  return (
    <span ref={ovoj} className={'zreb-oznaka' + (odprta ? ' zreb-oznaka--odprta' : '')}>
      <button
        type="button"
        className="zreb-oznaka__gumb"
        aria-expanded={odprta}
        aria-describedby={idOblacka}
        onClick={() => nastaviOdprta((prej) => !prej)}
      >
        {oznaka}
      </button>
      <span
        id={idOblacka}
        role="tooltip"
        className={'zreb-oznaka__oblacek' + (levo ? ' zreb-oznaka__oblacek--levo' : '')}
      >
        {children}
      </span>
    </span>
  )
}
