/* Pripomoček "1 na 1": medsebojni izid dveh igralcev prek vseh tekmovanj —
   turnirskih tekem in posamičnih tekem ligaških srečanj (dvojice ne štejejo).
   Semafor je ena sama mreža: zgoraj izbirnika, pod njima veliki imeni, na
   sredini pa izid, ki povezuje obe strani; gumb za naključni par stoji pod
   izidom. Ob prihodu se izžreba naključni par.
   Uporablja se na domači strani (strnjeno, brez imen in zgodovine) in na
   strani 1 na 1 (z razmerjem in tabelo tekem). Viden vsem (tudi gostom). */
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { igralciApi, statistikaApi } from '../api/zahteve'
import type { DvobojDto, IgralecDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { sklonTekem } from '../pomozno/oblikovanje'
import { SporociloNapake } from './SporociloNapake'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  /* Ali pod semaforjem pokaži razmerje in tabelo vseh medsebojnih tekem.
     Brez tega je pripomoček strnjen (domača stran): le izbirnika in izid. */
  pokaziZgodovino?: boolean
}

export function EnaNaEna({ pokaziZgodovino = false }: Lastnosti) {
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })

  /* Če sta igralca podana v naslovu (klik "Podrobna primerjava" z domače strani
     odpre /dvoboj?prvi=1&drugi=5), začni z njima; sicer se izžreba naključni par. */
  const [iskalniParametri] = useSearchParams()
  const [prvi, nastaviPrvega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('prvi')),
  )
  const [drugi, nastaviDrugega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('drugi')),
  )

  /* Če igralca nista prišla iz naslova, ob prvem nalaganju izberi naključni par. */
  useEffect(() => {
    const seznam = igralci.data
    if (!seznam || seznam.length < 2 || prvi !== '' || drugi !== '') return
    const [a, b] = dvaNakljucna(seznam)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }, [igralci.data, prvi, drugi])

  const veljavniPar = prvi !== '' && drugi !== '' && prvi !== drugi

  const dvoboj = useQuery({
    queryKey: ['dvoboj', prvi, drugi],
    queryFn: () => statistikaApi.dvoboj(Number(prvi), Number(drugi)),
    enabled: veljavniPar,
  })

  function izzrebaj() {
    if (!igralci.data || igralci.data.length < 2) return
    const [a, b] = dvaNakljucna(igralci.data)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }

  const premalo = (igralci.data?.length ?? 0) < 2
  const d = veljavniPar ? dvoboj.data : undefined

  return (
    <div className={'enanaena' + (pokaziZgodovino ? '' : ' enanaena--strnjen')}>
      <SporociloNapake napaka={igralci.error} />
      {veljavniPar && <SporociloNapake napaka={dvoboj.error} />}
      {veljavniPar && dvoboj.isPending && <p className="obvestilo">Nalaganje …</p>}

      <div className="enanaena__plosca">
        <IzbiraIgralca
          polozaj="enanaena__polje--1"
          oznaka="Igralec A"
          igralci={igralci.data ?? []}
          vrednost={prvi}
          izkljuci={drugi}
          naSpremembo={nastaviPrvega}
        />
        {/* Veliki imeni nosita naslov strani, zato ju strnjena različica izpusti. */}
        {d && pokaziZgodovino && (
          <IgralecStran
            polozaj="enanaena__kartica--1"
            idIgralca={d.prvi.id}
            ime={d.prvi.polnoIme}
            klub={d.prvi.klub}
            rating={d.prvi.rating}
          />
        )}

        {d && (
          <div className="enanaena__sredina">
            <div className="enanaena__oznaka">Medsebojno</div>
            <div className="enanaena__stevilo">
              <span className={barvaIzida(d.zmagePrvega, d.zmageDrugega)}>{d.zmagePrvega}</span>
              <span className="enanaena__crtica">:</span>
              <span className={barvaIzida(d.zmageDrugega, d.zmagePrvega)}>{d.zmageDrugega}</span>
            </div>
            <div className="enanaena__skupaj">
              {d.odigrane === 0
                ? 'še nista igrala'
                : `${d.odigrane} ${sklonTekem(d.odigrane)} · ${d.niziPrvega} : ${d.niziDrugega} v nizih`}
            </div>
            {!pokaziZgodovino && (
              <div className="enanaena__podrobno">
                <Link to={`/dvoboj?prvi=${prvi}&drugi=${drugi}`} className="domov__vec">
                  Podrobna primerjava →
                </Link>
              </div>
            )}
          </div>
        )}

        <IzbiraIgralca
          polozaj="enanaena__polje--2"
          oznaka="Igralec B"
          igralci={igralci.data ?? []}
          vrednost={drugi}
          izkljuci={prvi}
          naSpremembo={nastaviDrugega}
        />
        {d && pokaziZgodovino && (
          <IgralecStran
            polozaj="enanaena__kartica--2"
            idIgralca={d.drugi.id}
            ime={d.drugi.polnoIme}
            klub={d.drugi.klub}
            rating={d.drugi.rating}
          />
        )}

        <button className="gumb gumb--glavni enanaena__zreb" onClick={izzrebaj} disabled={premalo}>
          Naključni par
        </button>
      </div>

      {prvi !== '' && drugi !== '' && prvi === drugi && (
        <p className="napaka">Izberi dva različna igralca.</p>
      )}

      {igralci.data && premalo && (
        <p className="obvestilo">Za primerjavo sta potrebna vsaj dva igralca.</p>
      )}

      {pokaziZgodovino && d && d.tekme.length > 0 && (
        <>
          <Razmerje dvoboj={d} />
          <Zgodovina dvoboj={d} />
        </>
      )}
    </div>
  )
}

function IzbiraIgralca({
  polozaj,
  oznaka,
  igralci,
  vrednost,
  izkljuci,
  naSpremembo,
}: {
  polozaj: string
  oznaka: string
  igralci: IgralecDto[]
  vrednost: number | ''
  izkljuci: number | ''
  naSpremembo: (id: number | '') => void
}) {
  return (
    <label className={'enanaena__polje ' + polozaj}>
      <span>{oznaka}</span>
      <select
        value={vrednost}
        onChange={(d) => naSpremembo(d.target.value === '' ? '' : Number(d.target.value))}
      >
        <option value="">— izberi —</option>
        {igralci
          .filter((i) => i.id !== izkljuci)
          .map((i) => (
            <option key={i.id} value={i.id}>
              {i.priimek} {i.ime}
            </option>
          ))}
      </select>
    </label>
  )
}

/* Ena stran semaforja: veliko ime in pod njim klub z ratingom v eni vrstici. */
function IgralecStran({
  polozaj,
  idIgralca,
  ime,
  klub,
  rating,
}: {
  polozaj: string
  idIgralca: number
  ime: string
  klub: string | null
  rating: number | null
}) {
  const ratingTekst = rating !== null ? `rating ${rating}` : 'brez ratinga'
  /* Ime je hkrati naslov te strani semaforja, zato je naslovni element. */
  return (
    <div className={polozaj}>
      <div className="enanaena__igralec">
        <h2 className="enanaena__ime">
          <Link to={`/igralci/${idIgralca}/profil`}>{ime}</Link>
        </h2>
        <span className="enanaena__klub">{klub ? `${klub} · ${ratingTekst}` : ratingTekst}</span>
      </div>
    </div>
  )
}

/* Razmerje moči: štiri številke in dvobarvna palica deleža zmag. */
function Razmerje({ dvoboj }: { dvoboj: DvobojDto }) {
  const { prvi, drugi, odigrane, zmagePrvega, zmageDrugega, niziPrvega, niziDrugega } = dvoboj

  /* Tekma z razliko enega samega niza se je odločila šele v zadnjem nizu —
     ločenega podatka o odločilnem nizu strežnik ne pošilja. */
  const odlocilni = dvoboj.tekme.filter(
    (t) => Math.abs(t.niziPrvega - t.niziDrugega) === 1,
  ).length

  const delezPrvega = odigrane > 0 ? Math.round((zmagePrvega / odigrane) * 100) : 0

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razmerje</h2>
        <span className="sekcija__meta">Vsa tekmovanja</span>
      </div>

      <div className="profil__kazalniki">
        <div className="kazalnik">
          <div className={'kazalnik__vrednost ' + barvaIzida(zmagePrvega, zmageDrugega)}>
            {zmagePrvega}
          </div>
          <div className="kazalnik__oznaka">Zmage · {prvi.polnoIme}</div>
        </div>
        <div className="kazalnik">
          <div className={'kazalnik__vrednost ' + barvaIzida(zmageDrugega, zmagePrvega)}>
            {zmageDrugega}
          </div>
          <div className="kazalnik__oznaka">Zmage · {drugi.polnoIme}</div>
        </div>
        <div className="kazalnik">
          <div className="kazalnik__vrednost">
            {niziPrvega} : {niziDrugega}
          </div>
          <div className="kazalnik__oznaka">Nizi</div>
        </div>
        <div className="kazalnik">
          <div className="kazalnik__vrednost">{odlocilni}</div>
          <div className="kazalnik__oznaka">V odločilnem nizu</div>
        </div>
      </div>

      {odigrane > 0 && (
        <>
          <div className="razmerje-palica">
            <span className="razmerje-palica__z" style={{ width: `${delezPrvega}%` }} />
            <span className="razmerje-palica__p" style={{ width: `${100 - delezPrvega}%` }} />
          </div>
          <div className="razmerje-legenda">
            <span>
              {prvi.polnoIme} {delezPrvega} %
            </span>
            <span>
              {drugi.polnoIme} {100 - delezPrvega} %
            </span>
          </div>
        </>
      )}
    </div>
  )
}

function Zgodovina({ dvoboj }: { dvoboj: DvobojDto }) {
  const { prvi, drugi } = dvoboj
  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Odigrane tekme</h2>
        <span className="sekcija__meta">
          {dvoboj.tekme.length} {sklonTekem(dvoboj.tekme.length)}
        </span>
      </div>

      <div className="tabela-ovoj">
        <table className="tabela">
          <caption className="samo-za-bralnik">Vse medsebojne tekme obeh igralcev</caption>
          <thead>
            <tr>
              <th scope="col">Vir</th>
              <th scope="col">Tekmovanje</th>
              <th scope="col" className="lestvica__stevilka">Izid</th>
              <th scope="col" className="lestvica__stevilka">ELO</th>
              <th scope="col">Zmagovalec</th>
            </tr>
          </thead>
          <tbody>
            {dvoboj.tekme.map((t) => (
              <tr key={(t.ligaska ? 'l' : 't') + t.idTekme}>
                <td>
                  <span className="enanaena__vir">{t.ligaska ? 'liga' : 'turnir'}</span>
                </td>
                <td>
                  {t.tekmovanje}
                  <span className="profil__del">{t.del}</span>
                </td>
                <td className="lestvica__stevilka">
                  <span
                    className={
                      'profil__izid ' + (t.zmagalPrvi ? 'profil__zmaga' : 'profil__poraz')
                    }
                  >
                    {t.niziPrvega}:{t.niziDrugega}
                  </span>
                  {t.izidTip && t.izidTip !== 'IGRANO' && (
                    <span className="enanaena__posebni"> ({OZNAKE_IZID[t.izidTip]})</span>
                  )}
                </td>
                <td className="lestvica__stevilka enanaena__elo-celica">
                  <SpremembaElo vrednost={t.spremembaPrvega} />
                  <span className="enanaena__elo-locilo">/</span>
                  <SpremembaElo vrednost={t.spremembaDrugega} />
                </td>
                <td className={t.zmagalPrvi ? 'profil__zmaga' : 'profil__poraz'}>
                  {t.zmagalPrvi ? prvi.polnoIme : drugi.polnoIme}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* Zelena za vodilno stran, rjasta za zaostajajočo, brez barve ob izenačenju. */
function barvaIzida(svoje: number, tuje: number): string {
  if (svoje > tuje) return 'enanaena__vodi'
  if (svoje < tuje) return 'enanaena__izgublja'
  return ''
}

/* Pretvori naslovni parameter (npr. ?prvi=5) v veljaven id igralca ali v prazno. */
function steviloIzParametra(v: string | null): number | '' {
  if (v === null) return ''
  const n = Number(v)
  return Number.isInteger(n) && n > 0 ? n : ''
}

/* Dva različna naključna igralca s seznama. */
function dvaNakljucna(seznam: IgralecDto[]): [IgralecDto, IgralecDto] {
  const a = Math.floor(Math.random() * seznam.length)
  let b = Math.floor(Math.random() * seznam.length)
  while (b === a) b = Math.floor(Math.random() * seznam.length)
  return [seznam[a], seznam[b]]
}
