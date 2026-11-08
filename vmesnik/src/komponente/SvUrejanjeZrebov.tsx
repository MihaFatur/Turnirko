/* Raven in število izločilnih žrebov ene Skupine PO žrebu skupin.

   Organizator pogosto šele med igranjem skupin vidi, da žrebe potrebuje
   drugače, razveljavitev žreba pa takrat ne gre več (tudi odstop igralca
   zapiše tekme brez boja) in bi skupine izžrebala na novo. Zato se tu
   zamenjajo samo žrebi Skupine: skupine in odigrane tekme ostanejo. Strežnik
   spremembo dovoli, dokler se nobena tekma žrebov te Skupine ni začela
   (SvNivoDto.zrebiUredljivi), in zavrne število, ki ne sprejme vseh igralcev.
   Predogled velikosti je pomoč pri vpisu; zadnjo besedo ima strežnik. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { SvNivoDto } from '../api/tipi'
import { sklonIgralcev } from '../pomozno/oblikovanje'
import { RAVNI_ZREBA, imeRavni, imeRavniZaIzbiro, velikostiZrebov } from '../pomozno/svRegija'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'

const PRIVZETA_RAVEN = 8

/* »en žreb«, »2 žreba«, »3 žrebi«, »5 žrebov«. */
function zrebov(n: number): string {
  if (n === 1) return 'en žreb'
  if (n % 100 === 2) return `${n} žreba`
  if (n % 100 === 3 || n % 100 === 4) return `${n} žrebi`
  return `${n} žrebov`
}

interface Lastnosti {
  idDogodka: number
  nivo: SvNivoDto
  /* Raven, ki jo je za to Skupino vpisal organizator (null = četrtfinale). */
  vpisanaRaven: number | null
  onZapri: () => void
  onShranjeno: () => void
}

export function SvUrejanjeZrebov({ idDogodka, nivo, vpisanaRaven, onZapri, onShranjeno }: Lastnosti) {
  const [raven, nastaviRaven] = useState(vpisanaRaven === null ? '' : String(vpisanaRaven))
  const [stevilo, nastaviStevilo] = useState(
    nivo.vpisanoZrebov === null ? '' : String(nivo.vpisanoZrebov),
  )

  const shrani = useMutation({
    mutationFn: () =>
      svApi.zrebiSkupine(idDogodka, nivo.nivo, {
        steviloZrebov: stevilo === '' ? null : Number(stevilo),
        velikostZreba: raven === '' ? null : Number(raven),
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const velikostMreze = raven === '' ? PRIVZETA_RAVEN : Number(raven)
  const novi = velikostiZrebov(nivo.velikost, velikostMreze)
  const neustreza = stevilo !== '' && Number(stevilo) !== novi.length
  const zgrajeni = nivo.zrebi.some((z) => z.zgrajen)

  function obOddaji(dogodekObrazca: FormEvent) {
    dogodekObrazca.preventDefault()
    if (!neustreza) shrani.mutate()
  }

  return (
    <form className="obrazec__sklop sv-zrebi__urejanje" onSubmit={obOddaji}>
      <div className="obrazec__vrstica">
        <label className="obrazec__polje">
          <span>Začetek</span>
          <select value={raven} onChange={(d) => nastaviRaven(d.target.value)}>
            <option value="">Četrtfinale</option>
            {RAVNI_ZREBA.filter((r) => r !== PRIVZETA_RAVEN).map((r) => (
              <option key={r} value={String(r)}>
                {imeRavniZaIzbiro(r)}
              </option>
            ))}
          </select>
        </label>
        <label className="obrazec__polje">
          <span>Število žrebov</span>
          <StevilskoPolje
            najvec={16}
            vrednost={stevilo}
            naSpremembo={nastaviStevilo}
            placeholder="samodejno"
          />
        </label>
      </div>

      <p className="namig">
        {nivo.velikost} {sklonIgralcev(nivo.velikost)} od {imeRavni(velikostMreze)}: {zrebov(novi.length)}{' '}
        ({novi.join(' + ')}).
      </p>
      {neustreza && (
        <p className="obvestilo obvestilo--opozorilo">
          Vpisano število ne sprejme vseh igralcev Skupine. Potrebno število žrebov od{' '}
          {imeRavni(velikostMreze)}: {novi.length}. Popravi število ali izberi drugo raven.
        </p>
      )}
      <p className="namig">
        Skupine in odigrane tekme ostanejo.
        {zgrajeni &&
          ' Že narejeni žrebi te Skupine se podrejo; računalniški žreb ali ročno razporeditev narediš znova.'}
      </p>

      <SporociloNapake napaka={shrani.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri} disabled={shrani.isPending}>
          Prekliči
        </button>
        <button type="submit" className="gumb gumb--glavni" disabled={shrani.isPending || neustreza}>
          {shrani.isPending ? 'Shranjujem …' : 'Shrani žrebe'}
        </button>
      </div>
    </form>
  )
}
