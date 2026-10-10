/* Vrednost, ki sledi vhodu z zamikom: pri vlečenju drsnika ali tipkanju gre
   na strežnik samo vrednost, pri kateri se je gledalec ustavil, ne vsaka vmesna. */
import { useEffect, useState } from 'react'

export function useZakasnjeno<T>(vrednost: T, zamikMs = 120): T {
  const [zakasnjena, nastaviZakasnjeno] = useState(vrednost)
  useEffect(() => {
    const casovnik = window.setTimeout(() => nastaviZakasnjeno(vrednost), zamikMs)
    return () => window.clearTimeout(casovnik)
  }, [vrednost, zamikMs])
  return zakasnjena
}
