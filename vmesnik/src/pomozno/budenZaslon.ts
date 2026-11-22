/* Zaslon ne ugasne, dokler je stran odprta (branje listkov F5.4): organizator na
   pultu med listki telefona ne dotika, ugasnjen zaslon pa pomeni odklepanje pred
   vsako fotografijo. Wake Lock brskalnik sprosti, ko stran ni vidna, zato se ob
   vrnitvi zahteva znova. Brez podpore (starejši brskalnik) se ne zgodi nič. */
import { useEffect } from 'react'

export function useBudenZaslon(vklopljen: boolean) {
  useEffect(() => {
    if (!vklopljen || !('wakeLock' in navigator)) return
    let zaklep: WakeLockSentinel | null = null
    let ziv = true
    const zahtevaj = async () => {
      if (document.visibilityState !== 'visible' || (zaklep && !zaklep.released)) return
      try {
        const nov = await navigator.wakeLock.request('screen')
        if (ziv) zaklep = nov
        else void nov.release()
      } catch {
        /* zavrnjeno (varčevanje z baterijo, brez HTTPS) - zaslon ugaša kot sicer */
      }
    }
    const obVidnosti = () => void zahtevaj()
    void zahtevaj()
    document.addEventListener('visibilitychange', obVidnosti)
    return () => {
      ziv = false
      document.removeEventListener('visibilitychange', obVidnosti)
      if (zaklep && !zaklep.released) void zaklep.release()
    }
  }, [vklopljen])
}
