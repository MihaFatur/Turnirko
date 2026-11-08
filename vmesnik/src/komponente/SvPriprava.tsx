/* SV regija v pripravi: razrez v Skupine (1, 2 …) in nastavitve.

   Ploščo piše strežnik (MrezaDto.svRegija): koliko Skupin bo imel dogodek po
   trenutnem številu prijav, kako se razdelijo v skupine A, B … in koliko
   igralcev pride v vsak izločilni žreb. Vmesnik ničesar ne računa sam - ko se
   prijav ali nastavitev spremeni, strežnik pošlje nov predogled. (V kodi in
   API-ju je Skupina še vedno »nivo«.)

   Nastavitve so vse neobvezne (prazno = samodejno): število Skupin, ročne
   velikosti Skupin (»16, 16, 19«), skupin A, B … v polni Skupini, igralcev v
   skupini, in za vsako Skupino raven, na kateri se začnejo žrebi (privzeto
   četrtfinale), ter število žrebov. Igralci gredo v žrebe po vrsti A1, B1 …,
   A2, B2 …; vpisano število žrebov, ki ne sprejme vseh, strežnik zavrne
   (zadržek nad predogledom).

   Žreb skupin je lahko naključen (gumb »Izvedi žreb« nad stranjo) ali ročen:
   okno z igralci po skupinah, ki se ga odpre od tu. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { MrezaDto, SvNivoDto } from '../api/tipi'
import { sklonIgralcev, sklonSkupin, sklonVelikihSkupin } from '../pomozno/oblikovanje'
import { RAVNI_ZREBA, imeRavni, imeRavniZaIzbiro, opisRazpona } from '../pomozno/svRegija'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'
import { SvSkupineOkno } from './SvSkupineOkno'

/* »4 skupine po 4« oz. »5 skupin (4 + 4 + 4 + 4 + 3)«. */
function opisSkupin(velikosti: number[]): string {
  const stevilo = `${velikosti.length} ${sklonSkupin(velikosti.length)}`
  return velikosti.every((v) => v === velikosti[0])
    ? `${stevilo} po ${velikosti[0]}`
    : `${stevilo} (${velikosti.join(' + ')})`
}

/* »žrebi od četrtfinala: 8 + 8 + 3« oz. »brez žreba (ena skupina)«. */
function opisZrebov(nivo: SvNivoDto): string {
  if (nivo.zrebi.length === 0) return 'brez žreba (ena skupina)'
  const velikosti = nivo.zrebi.map((z) => z.stUdelezencev).join(' + ')
  const beseda = nivo.zrebi.length === 1 ? 'žreb' : 'žrebi'
  return `${beseda} od ${imeRavni(nivo.velikostZreba)}: ${velikosti}`
}

/* »16, 16 12« -> [16, 16, 12]; null, kadar je kaj narobe. */
function preberiVelikosti(besedilo: string): number[] | null {
  const deli = besedilo.split(/[\s,;]+/).filter((d) => d !== '')
  const stevila = deli.map(Number)
  return stevila.every((n) => Number.isInteger(n) && n >= 2) ? stevila : null
}

/* Vnos po Skupinah (po indeksu) kot besedila za polja; manjkajoče je prazno. */
function poSkupinah(vrednosti: (number | null)[], koliko: number): string[] {
  return Array.from({ length: koliko }, (_, i) => {
    const v = vrednosti[i]
    return v === null || v === undefined ? '' : String(v)
  })
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
  const steviloVelikih = sv?.nivoji.length ?? 0
  const [nastavitve, nastaviNastavitve] = useState(false)
  const [rocno, nastaviRocno] = useState(false)

  const [steviloNivojev, nastaviSteviloNivojev] = useState(String(dogodek.steviloNivojev ?? ''))
  const [velikosti, nastaviVelikosti] = useState(dogodek.velikostiNivojev.join(', '))
  const [skupin, nastaviSkupin] = useState(String(dogodek.steviloSkupin ?? ''))
  const [velikostSkupine, nastaviVelikostSkupine] = useState(String(dogodek.velikostSkupine ?? ''))
  const [stevilaZrebov, nastaviStevilaZrebov] = useState(() =>
    poSkupinah(dogodek.steviloZrebov, steviloVelikih),
  )
  const [ravni, nastaviRavni] = useState(() => poSkupinah(dogodek.velikostZrebov, steviloVelikih))
  const [napakaVpisa, nastaviNapakoVpisa] = useState<string | null>(null)

  const shrani = useMutation({
    mutationFn: (vnos: Parameters<typeof svApi.nastavitve>[1]) =>
      svApi.nastavitve(idDogodka, vnos),
    onSuccess: osvezi,
  })

  if (!sv) return null

  const aktivnih = podatki.prijave.filter((p) => p.status === 'PRIJAVLJEN').length

  /* Ko se število Skupin v predogledu spremeni (nove prijave, shranjen razrez),
     se polja žrebov poravnajo nanj - vpisano ostane na svojem indeksu. */
  const stevilaPolj = poravnaj(stevilaZrebov, sv.nivoji.length)
  const ravniPolj = poravnaj(ravni, sv.nivoji.length)

  function nastaviNaIndeksu(seznam: string[], indeks: number, vrednost: string): string[] {
    const nov = poravnaj(seznam, sv?.nivoji.length ?? 0)
    nov[indeks] = vrednost
    return nov
  }

  function obOddaji(dogodekObrazca: FormEvent) {
    dogodekObrazca.preventDefault()
    const velikostiNivojev = velikosti.trim() === '' ? [] : preberiVelikosti(velikosti)
    if (velikostiNivojev === null) {
      nastaviNapakoVpisa('Velikosti Skupin vpiši kot števila z vejico, npr. 16, 16, 19 (vsaj 2).')
      return
    }
    nastaviNapakoVpisa(null)
    shrani.mutate({
      steviloNivojev: steviloNivojev === '' ? null : Number(steviloNivojev),
      velikostiNivojev,
      steviloSkupin: skupin === '' ? null : Number(skupin),
      velikostSkupine: velikostSkupine === '' ? null : Number(velikostSkupine),
      steviloZrebov: stevilaPolj.map((v) => (v === '' ? null : Number(v))),
      velikostZrebov: ravniPolj.map((v) => (v === '' ? null : Number(v))),
    })
  }

  function samodejno() {
    nastaviSteviloNivojev('')
    nastaviVelikosti('')
    nastaviSkupin('')
    nastaviVelikostSkupine('')
    nastaviStevilaZrebov([])
    nastaviRavni([])
    nastaviNapakoVpisa(null)
    shrani.mutate({
      steviloNivojev: null,
      velikostiNivojev: [],
      steviloSkupin: null,
      velikostSkupine: null,
      steviloZrebov: [],
      velikostZrebov: [],
    })
  }

  return (
    <div className="sv-priprava">
      <div className="naslovna-vrstica">
        <h2>Skupine</h2>
        <span className="sekcija__meta">
          {sv.nivoji.length} {sklonVelikihSkupin(sv.nivoji.length)}
        </span>
      </div>

      <p className="namig">
        Igralci so po jakosti razdeljeni v Skupine (privzeto po {sv.skupinNaNivo * sv.velikostSkupine}:{' '}
        {sv.skupinNaNivo} {sklonSkupin(sv.skupinNaNivo)} po {sv.velikostSkupine}; zadnja Skupina dobi
        ostanek). V skupini igra vsak z vsakim. Nato gredo igralci po vrsti A1, B1, C1 …, A2, B2 … v
        izločilne žrebe (privzeto od četrtfinala, po 8); žreb se igra za vsa mesta.
      </p>

      {sv.zadrzek && <p className="obvestilo obvestilo--opozorilo">{sv.zadrzek}</p>}

      {sv.nivoji.length > 0 && (
        <div className="sv-razrez">
          {sv.nivoji.map((nivo) => (
            <div className="sv-razrez__vrstica" key={nivo.nivo}>
              <span className="sv-razrez__oznaka">S{nivo.nivo}</span>
              <span className="sv-razrez__mesta">{opisRazpona(nivo.odMesta, nivo.doMesta)}</span>
              <span className="sv-razrez__opis">
                {nivo.velikost} {sklonIgralcev(nivo.velikost)} · {opisSkupin(nivo.velikostiSkupin)} ·{' '}
                {opisZrebov(nivo)}
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
          {nastavitve ? 'Skrij nastavitve' : 'Nastavitve razreza in žrebov'}
        </button>
      </div>

      {nastavitve && (
        <form className="obrazec__sklop sv-priprava__obrazec" onSubmit={obOddaji}>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Število Skupin</span>
              <StevilskoPolje
                najvec={9}
                vrednost={steviloNivojev}
                naSpremembo={nastaviSteviloNivojev}
                placeholder="samodejno"
                disabled={velikosti.trim() !== ''}
              />
            </label>
            <label className="obrazec__polje">
              <span>Velikosti Skupin</span>
              <input
                value={velikosti}
                onChange={(d) => nastaviVelikosti(d.target.value)}
                placeholder="samodejno, npr. 16, 16, 19"
                inputMode="numeric"
                autoComplete="off"
              />
            </label>
          </div>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Skupin v polni Skupini</span>
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
          <p className="namig">
            Ostanek odločiš sam: brez vnosa se ostanek, manjši od ene skupine, pridruži zadnji
            Skupini (67 = 16 + 16 + 16 + 19). Z izbranim številom Skupin so vse razen zadnje polne in
            zadnja dobi vse preostale (60 v treh Skupinah = 16 + 16 + 28). Vpisane velikosti
            nadomestijo oboje in se morajo ujemati s številom prijavljenih ({aktivnih}).
          </p>

          {sv.nivoji.length > 0 && (
            <div className="sv-zrebi-nastavitve">
              <p className="podnaslov-sekcije">Izločilni žrebi</p>
              {sv.nivoji.map((nivo, i) => (
                <div className="sv-zrebi-nastavitve__vrstica" key={nivo.nivo}>
                  <span className="sv-zrebi-nastavitve__oznaka">
                    Skupina {nivo.nivo}
                    <span className="sv-zrebi-nastavitve__igralci">
                      {nivo.velikost} {sklonIgralcev(nivo.velikost)}
                    </span>
                  </span>
                  <label className="obrazec__polje">
                    <span>Začetek</span>
                    <select
                      value={ravniPolj[i]}
                      onChange={(d) => nastaviRavni((prej) => nastaviNaIndeksu(prej, i, d.target.value))}
                    >
                      <option value="">Četrtfinale</option>
                      {RAVNI_ZREBA.filter((r) => r !== 8).map((r) => (
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
                      vrednost={stevilaPolj[i]}
                      naSpremembo={(v) => nastaviStevilaZrebov((prej) => nastaviNaIndeksu(prej, i, v))}
                      placeholder="samodejno"
                    />
                  </label>
                </div>
              ))}
              <p className="namig">
                Vsak žreb sprejme toliko igralcev, kolikor jih ima mreža izbrane ravni (četrtfinale 8),
                zadnji dobi ostanek. Vpisano število žrebov mora sprejeti vse igralce Skupine, sicer
                žreba ni. Žrebe nastaviš za Skupine iz predogleda; če spremeniš razrez, ga najprej
                shrani.
              </p>
            </div>
          )}

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

/* Seznam besedil, podaljšan s praznimi ali skrajšan na dano dolžino. */
function poravnaj(seznam: string[], dolzina: number): string[] {
  return Array.from({ length: dolzina }, (_, i) => seznam[i] ?? '')
}
