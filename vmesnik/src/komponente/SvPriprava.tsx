/* SV regija v pripravi: razrez po nivojih in nastavitve.

   Ploščo piše strežnik (MrezaDto.svRegija): koliko nivojev bo imel dogodek po
   trenutnem številu prijav, kako se razdelijo v skupine in koliko igralcev
   pride v vsak žreb. Vmesnik ničesar ne računa sam - ko se prijav ali nastavitev
   spremeni, strežnik pošlje nov predogled.

   Nastavitve so vse neobvezne (prazno = samodejno): število nivojev, ročne
   velikosti nivojev (»16,16,12«), skupin na poln nivo, igralcev v skupini in
   rangov iz skupine v en žreb po nivojih (»2,2,1«). Ročne velikosti imajo
   prednost pred številom nivojev.

   Žreb skupin je lahko naključen (gumb »Izvedi žreb« nad stranjo) ali ročen:
   okno z igralci po skupinah, ki se ga odpre od tu. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { MrezaDto } from '../api/tipi'
import { sklonIgralcev, sklonNivojev, sklonSkupin } from '../pomozno/oblikovanje'
import { opisRazpona } from '../pomozno/svRegija'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'
import { SvSkupineOkno } from './SvSkupineOkno'

/* »4 skupine po 4« oz. »3 skupine (4 + 3 + 3)«. */
function opisSkupin(velikosti: number[]): string {
  const stevilo = `${velikosti.length} ${sklonSkupin(velikosti.length)}`
  return velikosti.every((v) => v === velikosti[0])
    ? `${stevilo} po ${velikosti[0]}`
    : `${stevilo} (${velikosti.join(' + ')})`
}

/* »2, 2 1« -> [2, 2, 1]; null, kadar je kaj narobe (rangov v žrebu je 1 ali 2). */
function preberiRange(besedilo: string): number[] | null {
  const stevila = besedilo.split(/[s,;]+/).filter((d) => d !== '').map(Number)
  return stevila.every((n) => n === 1 || n === 2) ? stevila : null
}

/* »16, 16 12« -> [16, 16, 12]; null, kadar je kaj narobe. */
function preberiVelikosti(besedilo: string): number[] | null {
  const deli = besedilo.split(/[\s,;]+/).filter((d) => d !== '')
  const stevila = deli.map(Number)
  return stevila.every((n) => Number.isInteger(n) && n >= 2) ? stevila : null
}

export function SvPriprava({
  podatki,
  idDogodka,
  osvezi,
}: {
  podatki: MrezaDto
  idDogodka: number
  osvezi: () => void
}) {
  const sv = podatki.svRegija
  const dogodek = podatki.dogodek
  const [nastavitve, nastaviNastavitve] = useState(false)
  const [rocno, nastaviRocno] = useState(false)

  const [steviloNivojev, nastaviSteviloNivojev] = useState(String(dogodek.steviloNivojev ?? ''))
  const [velikosti, nastaviVelikosti] = useState(dogodek.velikostiNivojev.join(', '))
  const [rangi, nastaviRange] = useState(dogodek.rangovVZreb.join(', '))
  const [skupin, nastaviSkupin] = useState(String(dogodek.steviloSkupin ?? ''))
  const [velikostSkupine, nastaviVelikostSkupine] = useState(String(dogodek.velikostSkupine ?? ''))
  const [napakaVpisa, nastaviNapakoVpisa] = useState<string | null>(null)

  const shrani = useMutation({
    mutationFn: (vnos: Parameters<typeof svApi.nastavitve>[1]) =>
      svApi.nastavitve(idDogodka, vnos),
    onSuccess: osvezi,
  })

  if (!sv) return null

  const aktivnih = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN').length

  function obOddaji(dogodekObrazca: FormEvent) {
    dogodekObrazca.preventDefault()
    const velikostiNivojev = velikosti.trim() === '' ? [] : preberiVelikosti(velikosti)
    if (velikostiNivojev === null) {
      nastaviNapakoVpisa('Velikosti nivojev vpiši kot števila z vejico, npr. 16, 16, 12 (vsaj 2).')
      return
    }
    const rangovVZreb = rangi.trim() === '' ? [] : preberiRange(rangi)
    if (rangovVZreb === null) {
      nastaviNapakoVpisa('Rangov v žrebu vpiši po nivojih kot 1 ali 2, npr. 2, 2, 1.')
      return
    }
    nastaviNapakoVpisa(null)
    shrani.mutate({
      steviloNivojev: steviloNivojev === '' ? null : Number(steviloNivojev),
      velikostiNivojev,
      steviloSkupin: skupin === '' ? null : Number(skupin),
      velikostSkupine: velikostSkupine === '' ? null : Number(velikostSkupine),
      rangovVZreb,
    })
  }

  function samodejno() {
    nastaviSteviloNivojev('')
    nastaviVelikosti('')
    nastaviRange('')
    nastaviSkupin('')
    nastaviVelikostSkupine('')
    nastaviNapakoVpisa(null)
    shrani.mutate({
      steviloNivojev: null,
      velikostiNivojev: [],
      steviloSkupin: null,
      velikostSkupine: null,
      rangovVZreb: [],
    })
  }

  return (
    <div className="sv-priprava">
      <div className="naslovna-vrstica">
        <h2>Nivoji in skupine</h2>
        <span className="sekcija__meta">
          {sv.nivoji.length} {sklonNivojev(sv.nivoji.length)}
        </span>
      </div>

      <p className="namig">
        Igralci so po jakosti razdeljeni v nivoje (privzeto po {sv.skupinNaNivo * sv.velikostSkupine}:{' '}
        {sv.skupinNaNivo} {sklonSkupin(sv.skupinNaNivo)} po {sv.velikostSkupine}; zadnji nivo dobi
        ostanek). V skupini igra vsak z vsakim. Prvo- in drugouvrščeni gredo v glavni žreb nivoja,
        tretje- in četrtouvrščeni v tolažilnega; žreb se igra za vsa mesta.
      </p>

      {sv.zadrzek && <p className="obvestilo obvestilo--opozorilo">{sv.zadrzek}</p>}

      {sv.nivoji.length > 0 && (
        <div className="sv-razrez">
          {sv.nivoji.map((nivo) => (
            <div className="sv-razrez__vrstica" key={nivo.nivo}>
              <span className="sv-razrez__oznaka">N{nivo.nivo}</span>
              <span className="sv-razrez__mesta">{opisRazpona(nivo.odMesta, nivo.doMesta)}</span>
              <span className="sv-razrez__opis">
                {nivo.velikost} {sklonIgralcev(nivo.velikost)} · {opisSkupin(nivo.velikostiSkupin)}
                {nivo.zrebi.length > 0
                  ? ` · ${nivo.zrebi.map((z) => `${z.ime.toLowerCase()} ${z.stUdelezencev}`).join(' · ')}`
                  : ' · brez žreba (ena skupina)'}
              </span>
            </div>
          ))}
        </div>
      )}

      <div className="zreb__krmila">
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={aktivnih < 2 || sv.zadrzek !== null}
          onClick={() => nastaviRocno(true)}
        >
          Ročni vpis skupin
        </button>
        <button
          type="button"
          className="gumb gumb--majhen"
          aria-expanded={nastavitve}
          onClick={() => nastaviNastavitve((odprte) => !odprte)}
        >
          {nastavitve ? 'Skrij nastavitve' : 'Nastavitve razreza'}
        </button>
      </div>

      {nastavitve && (
        <form className="obrazec__sklop sv-priprava__obrazec" onSubmit={obOddaji}>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Število nivojev</span>
              <StevilskoPolje
                najvec={9}
                vrednost={steviloNivojev}
                naSpremembo={nastaviSteviloNivojev}
                placeholder="samodejno"
                disabled={velikosti.trim() !== ''}
              />
            </label>
            <label className="obrazec__polje">
              <span>Velikosti nivojev</span>
              <input
                value={velikosti}
                onChange={(d) => nastaviVelikosti(d.target.value)}
                placeholder="samodejno, npr. 16, 16, 12"
                inputMode="numeric"
                autoComplete="off"
              />
            </label>
          </div>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Skupin na poln nivo</span>
              <StevilskoPolje
                najvec={26}
                vrednost={skupin}
                naSpremembo={nastaviSkupin}
                placeholder={String(sv.skupinNaNivo)}
              />
            </label>
            <label className="obrazec__polje">
              <span>Igralcev v skupini</span>
              <StevilskoPolje
                najvec={24}
                vrednost={velikostSkupine}
                naSpremembo={nastaviVelikostSkupine}
                placeholder={String(sv.velikostSkupine)}
              />
            </label>
          </div>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Rangov iz skupine v žreb (po nivojih)</span>
              <input
                value={rangi}
                onChange={(d) => nastaviRange(d.target.value)}
                placeholder="samodejno (povsod 2), npr. 2, 2, 1"
                inputMode="numeric"
                autoComplete="off"
              />
            </label>
          </div>
          <p className="namig">
            Pri 2 gresta prvo- in drugouvrščeni v glavni žreb, tretje- in četrtouvrščeni v tolažilnega;
            pri 1 ima vsak rang svoj žreb (osem skupin da štiri žrebe po 8 igralcev). Nivoji brez vnosa
            imajo 2.
          </p>
          <p className="namig">
            Vpisane velikosti nivojev nadomestijo samodejno razdelitev in se morajo ujemati s
            številom prijavljenih ({aktivnih}). Nepopolna skupina (3 igralci namesto 4) je dovoljena;
            nivo z eno samo skupino žreba nima.
          </p>
          {napakaVpisa && <div className="napaka">{napakaVpisa}</div>}
          <SporociloNapake napaka={shrani.error} />
          <div className="obrazec__gumbi">
            <button type="button" className="gumb" onClick={samodejno} disabled={shrani.isPending}>
              Samodejno
            </button>
            <button type="submit" className="gumb gumb--glavni" disabled={shrani.isPending}>
              {shrani.isPending ? 'Shranjujem …' : 'Shrani nastavitve'}
            </button>
          </div>
        </form>
      )}

      {rocno && (
        <SvSkupineOkno
          idDogodka={idDogodka}
          podatki={podatki}
          izhodisce="predlog"
          onZapri={() => nastaviRocno(false)}
          onShranjeno={osvezi}
        />
      )}
    </div>
  )
}
