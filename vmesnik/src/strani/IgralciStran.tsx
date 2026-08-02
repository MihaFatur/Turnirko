/* Register igralcev: pregled s trenutnim ratingom, dodajanje, urejanje
   in arhiviranje (namesto brisanja - zgodovina tekem mora ostati). */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, krajiApi } from '../api/zahteve'
import type { IgralecDto, IgralecVnos, IgralnaRoka, Spol } from '../api/tipi'
import { OZNAKE_IGRALNA_ROKA, OZNAKE_SPOL } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { letnica } from '../pomozno/oblikovanje'

/* Do toliko odigranih tekem je rating še provizoričen (ujema se s strežniškim
   pragom dinamičnega K, EloStoritev.PRAG_PROVIZORICNI). */
const PROVIZORICNO_DO = 10

export function IgralciStran() {
  const odjemalec = useQueryClient()
  // organizator sme le dodati novega igralca; urejanje, arhiviranje in
  // postavitev ratinga ostanejo administratorju (streznik je zadnja obramba)
  const { jeAdmin } = useAvtentikacija()
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })

  const [iskanje, nastaviIskanje] = useState('')
  /* null = obrazec zaprt; 'nov' = nov igralec; sicer igralec za urejanje. */
  const [urejanje, nastaviUrejanje] = useState<'nov' | IgralecDto | null>(null)
  /* Igralec, za katerega cakamo potrditev arhiviranja. */
  const [arhiviranec, nastaviArhiviranca] = useState<IgralecDto | null>(null)
  /* Igralec, ki mu postavljamo začetni rating (le pred prvo tekmo). */
  const [postavljanec, nastaviPostavljanca] = useState<IgralecDto | null>(null)

  const arhiviranje = useMutation({
    mutationFn: (id: number) => igralciApi.arhiviraj(id),
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['igralci'] }),
  })

  const prikazani = useMemo(() => {
    if (!igralci.data) return []
    const iskano = iskanje.trim().toLowerCase()
    if (!iskano) return igralci.data
    return igralci.data.filter((igralec) =>
      `${igralec.ime} ${igralec.priimek} ${igralec.klub?.ime ?? ''}`
        .toLowerCase()
        .includes(iskano),
    )
  }, [igralci.data, iskanje])

  const vsi = igralci.data ?? []
  const zRatingom = vsi.filter((i) => i.rating != null).length
  const klubov = new Set(vsi.map((i) => i.klub?.id).filter((v) => v !== undefined)).size

  return (
    <section>
      <div className="stran-glava stran-glava--dno">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Kartoteka</span>
            <span className="naslov-strani__glavni">Igralci</span>
          </h1>
          <p className="uvod">
            {jeAdmin
              ? `Klub, igralna roka in datum rojstva vplivajo na kategorije in statistiko. Zvezdica pomeni provizoričen rating (manj kot ${PROVIZORICNO_DO} odigranih tekem).`
              : 'Kot organizator lahko dodaš novega igralca; urejanje in rating ureja administrator.'}
          </p>
        </div>
        <div>
          <label className="obrazec__polje">
            <span>Išči</span>
            <input
              className="iskalnik"
              placeholder="Išči po imenu ali klubu …"
              value={iskanje}
              onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
            />
          </label>
          <div className="stran-glava__dejanja">
            <button className="gumb gumb--glavni" onClick={() => nastaviUrejanje('nov')}>
              + Nov igralec
            </button>
          </div>
          {igralci.data && (
            <div className="stevci">
              <span className="stevci__postavka">{vsi.length} igralcev</span>
              <span className="stevci__postavka">{klubov} klubov</span>
              <span className="stevci__postavka">{zRatingom} z ratingom</span>
            </div>
          )}
        </div>
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Vsi igralci</h2>
          {igralci.data && (
            <span className="sekcija__meta">
              Prikazanih {prikazani.length} od {vsi.length}
            </span>
          )}
        </div>

      <SporociloNapake napaka={igralci.error} />
      <SporociloNapake napaka={arhiviranje.error} />
      {igralci.isPending && <p className="obvestilo">Nalaganje …</p>}

      {igralci.data && igralci.data.length === 0 && (
        <p className="obvestilo">Ni še nobenega igralca. Dodaj prvega z gumbom »+ Nov igralec«.</p>
      )}

      {vsi.length > 0 && prikazani.length === 0 && (
        <p className="obvestilo">Iskanju ne ustreza noben igralec.</p>
      )}

      {prikazani.length > 0 && (
        <div className="tabela-ovoj">
        <table className="tabela">
          <thead>
            <tr>
              <th scope="col">Priimek in ime</th>
              <th scope="col">Klub</th>
              <th scope="col">Spol</th>
              <th scope="col">Letnik</th>
              <th scope="col" className="lestvica__rating">Rating</th>
              <th scope="col">Licenca NTZS</th>
              <th scope="col" className="tabela__dejanja"></th>
            </tr>
          </thead>
          <tbody>
            {prikazani.map((igralec) => (
              <tr key={igralec.id}>
                <td className="lestvica__ime">
                  {igralec.priimek} {igralec.ime}
                </td>
                <td className={igralec.klub ? 'lestvica__klub' : 'lestvica__klub igralec-klub--brez'}>
                  {igralec.klub?.ime ?? 'brez kluba'}
                </td>
                <td className="vrstica__mono">{OZNAKE_SPOL[igralec.spol]}</td>
                <td className="vrstica__mono">{letnica(igralec.datumRojstva)}</td>
                <td
                  className={
                    'igralec-rating' + (igralec.rating == null ? ' igralec-rating--brez' : '')
                  }
                >
                  {igralec.rating != null && (
                    <span>
                      {igralec.rating}
                      {igralec.steviloTekem < PROVIZORICNO_DO && (
                        <span
                          className="provizoricno"
                          title={`Provizoričen rating (${igralec.steviloTekem} od ${PROVIZORICNO_DO} tekem)`}
                        >
                          ★
                        </span>
                      )}
                    </span>
                  )}
                  {jeAdmin && igralec.steviloTekem === 0 ? (
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => nastaviPostavljanca(igralec)}
                    >
                      {igralec.rating == null ? 'Postavi rating' : 'Popravi'}
                    </button>
                  ) : (
                    igralec.rating == null && '—'
                  )}
                </td>
                <td className="vrstica__mono">{igralec.ntzsLicenca ?? '—'}</td>
                <td className="tabela__dejanja">
                  {jeAdmin && (
                    <span>
                      <button className="gumb gumb--majhen" onClick={() => nastaviUrejanje(igralec)}>
                        Uredi
                      </button>
                      <button
                        className="gumb gumb--majhen gumb--nevaren"
                        onClick={() => nastaviArhiviranca(igralec)}
                      >
                        Arhiviraj
                      </button>
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
      </div>

      {arhiviranec && (
        <PotrditvenoOkno
          naslov="Arhiviranje igralca"
          sporocilo={`Igralec ${arhiviranec.ime} ${arhiviranec.priimek} bo izginil s seznamov, zgodovina njegovih tekem pa ostane. Arhiviram?`}
          besedaPotrditve="Arhiviraj"
          onPotrdi={() => arhiviranje.mutate(arhiviranec.id)}
          onZapri={() => nastaviArhiviranca(null)}
        />
      )}

      {urejanje && (
        <IgralecOkno
          igralec={urejanje === 'nov' ? null : urejanje}
          onZapri={() => nastaviUrejanje(null)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['igralci'] })}
        />
      )}

      {postavljanec && (
        <ZacetniRatingOkno
          igralec={postavljanec}
          onZapri={() => nastaviPostavljanca(null)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['igralci'] })}
        />
      )}
    </section>
  )
}

/* Postavitveni (začetni) rating za novinca. Na voljo le, dokler igralec ni
   odigral nobene tekme — kasneje rating določajo samo rezultati. */
function ZacetniRatingOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  igralec: IgralecDto
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [vrednost, nastaviVrednost] = useState(String(igralec.rating ?? 1000))

  const shranjevanje = useMutation({
    mutationFn: () => igralciApi.nastaviZacetniRating(igralec.id, Number(vrednost)),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const stevilka = Number(vrednost)
  const veljaven = Number.isInteger(stevilka) && stevilka >= 100 && stevilka <= 3000

  return (
    <ModalnoOkno naslov={`Začetni rating — ${igralec.ime} ${igralec.priimek}`} onZapri={onZapri}>
      <form
        className="obrazec"
        onSubmit={(d) => {
          d.preventDefault()
          if (veljaven) shranjevanje.mutate()
        }}
      >
        <p className="obvestilo">
          Močnemu novincu lahko postaviš vstopni ELO, da mu ni treba plezati z 1000.
          Mogoče je le pred prvo odigrano tekmo; nato rating določajo rezultati.
        </p>
        <label className="obrazec__polje">
          <span>Klubski ELO (100–3000)</span>
          <input
            type="number"
            min={100}
            max={3000}
            value={vrednost}
            onChange={(d) => nastaviVrednost(d.target.value)}
            autoFocus
          />
        </label>
        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={!veljaven || shranjevanje.isPending}
          >
            Shrani rating
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function IgralecOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  igralec: IgralecDto | null
  onZapri: () => void
  onShranjeno: () => void
}) {
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const kraji = useQuery({ queryKey: ['kraji'], queryFn: krajiApi.seznam })

  const [ime, nastaviIme] = useState(igralec?.ime ?? '')
  const [priimek, nastaviPriimek] = useState(igralec?.priimek ?? '')
  const [spol, nastaviSpol] = useState<Spol>(igralec?.spol ?? 'MOSKI')
  const [datumRojstva, nastaviDatumRojstva] = useState(igralec?.datumRojstva ?? '')
  const [idKlub, nastaviIdKlub] = useState(igralec?.klub ? String(igralec.klub.id) : '')
  const [ntzsLicenca, nastaviLicenco] = useState(igralec?.ntzsLicenca ?? '')
  const [igralnaRoka, nastaviRoko] = useState<'' | IgralnaRoka>(igralec?.igralnaRoka ?? '')
  const [email, nastaviEmail] = useState(igralec?.email ?? '')
  const [telefonskaSt, nastaviTelefon] = useState(igralec?.telefonskaSt ?? '')
  const [drzavljanstvo, nastaviDrzavljanstvo] = useState(igralec?.drzavljanstvo ?? '')
  const [naslov, nastaviNaslov] = useState(igralec?.naslov ?? '')
  const [postnaSt, nastaviPostnaSt] = useState(
    igralec?.kraj ? String(igralec.kraj.postnaSt) : '',
  )

  const shranjevanje = useMutation({
    mutationFn: (vnos: IgralecVnos) =>
      igralec ? igralciApi.posodobi(igralec.id, vnos) : igralciApi.ustvari(vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      priimek: priimek.trim(),
      spol,
      datumRojstva,
      email: email.trim() || null,
      telefonskaSt: telefonskaSt.trim() || null,
      igralnaRoka: igralnaRoka || null,
      ntzsLicenca: ntzsLicenca.trim() || null,
      drzavljanstvo: drzavljanstvo.trim() || null,
      naslov: naslov.trim() || null,
      postnaSt: postnaSt ? Number(postnaSt) : null,
      idKlub: idKlub ? Number(idKlub) : null,
    })
  }

  return (
    <ModalnoOkno naslov={igralec ? 'Uredi igralca' : 'Nov igralec'} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Ime *</span>
            <input value={ime} onChange={(d) => nastaviIme(d.target.value)} required />
          </label>
          <label className="obrazec__polje">
            <span>Priimek *</span>
            <input value={priimek} onChange={(d) => nastaviPriimek(d.target.value)} required />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Spol *</span>
            <select value={spol} onChange={(d) => nastaviSpol(d.target.value as Spol)}>
              {Object.entries(OZNAKE_SPOL).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Datum rojstva *</span>
            <input
              type="date"
              value={datumRojstva}
              onChange={(d) => nastaviDatumRojstva(d.target.value)}
              required
            />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Klub</span>
            <select value={idKlub} onChange={(d) => nastaviIdKlub(d.target.value)}>
              <option value="">— brez kluba —</option>
              {klubi.data?.map((klub) => (
                <option key={klub.id} value={klub.id}>
                  {klub.ime}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Licenca NTZS</span>
            <input value={ntzsLicenca} onChange={(d) => nastaviLicenco(d.target.value)} />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Igralna roka</span>
            <select
              value={igralnaRoka}
              onChange={(d) => nastaviRoko(d.target.value as '' | IgralnaRoka)}
            >
              <option value="">— ni podatka —</option>
              {Object.entries(OZNAKE_IGRALNA_ROKA).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Državljanstvo</span>
            <input
              value={drzavljanstvo}
              onChange={(d) => nastaviDrzavljanstvo(d.target.value)}
              placeholder="npr. slovensko"
            />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>E-pošta</span>
            <input type="email" value={email} onChange={(d) => nastaviEmail(d.target.value)} />
          </label>
          <label className="obrazec__polje">
            <span>Telefon</span>
            <input value={telefonskaSt} onChange={(d) => nastaviTelefon(d.target.value)} />
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Naslov</span>
            <input value={naslov} onChange={(d) => nastaviNaslov(d.target.value)} />
          </label>
          <label className="obrazec__polje">
            <span>Kraj</span>
            <select value={postnaSt} onChange={(d) => nastaviPostnaSt(d.target.value)}>
              <option value="">— izberi kraj —</option>
              {kraji.data?.map((kraj) => (
                <option key={kraj.postnaSt} value={kraj.postnaSt}>
                  {kraj.postnaSt} {kraj.ime}
                </option>
              ))}
            </select>
          </label>
        </div>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            {igralec ? 'Shrani spremembe' : 'Dodaj igralca'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
