/* Register igralcev: pregled s trenutnim ratingom, dodajanje, urejanje
   in arhiviranje (namesto brisanja - zgodovina tekem mora ostati). */
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, krajiApi } from '../api/zahteve'
import type {
  IgralecDto,
  IgralecPodrobenDto,
  IgralecVnos,
  IgralnaRoka,
  PodobenIgralecDto,
  PodobenIgralecPodrobenDto,
  PodobniVnos,
  Spol,
  UjemanjeImena,
} from '../api/tipi'
import { OZNAKE_IGRALNA_ROKA, OZNAKE_PASU_KRATKO, OZNAKE_SPOL } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { IzbirnikKluba } from '../komponente/IzbirnikKluba'
import { IzbirnikKraja } from '../komponente/IzbirnikKraja'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { StevilskoPolje } from '../komponente/StevilskoPolje'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { letnica, oblikujDatum, sklonTekem, zVelikoZacetnico } from '../pomozno/oblikovanje'

/* Do toliko odigranih tekem je rating še provizoričen (ujema se s strežniškim
   pragom dinamičnega K, TurnirkoRatingStoritev.PRAG_PROVIZORICNI). */
const PROVIZORICNO_DO = 10

export function IgralciStran() {
  const odjemalec = useQueryClient()
  // organizator sme le dodati novega igralca; urejanje, arhiviranje in
  // postavitev ratinga ostanejo administratorju (streznik je zadnja obramba)
  const { jeAdmin } = useAvtentikacija()
  /* Osebne podatke (letnik, licenca, kontakti) strežnik da samo adminu, zato
     organizator dobi javni izpis. Ločen ključ, da se odgovora ne mešata v
     predpomnilniku; invalidacija po ['igralci'] zajame oba. */
  const igralci = useQuery<IgralecDto[]>({
    queryKey: ['igralci', jeAdmin ? 'podrobno' : 'javno'],
    queryFn: jeAdmin ? igralciApi.seznamPodrobno : igralciApi.seznam,
  })

  const [iskanje, nastaviIskanje] = useState('')
  /* null = obrazec zaprt; 'nov' = nov igralec; sicer igralec za urejanje
     (urejati sme le admin, ki ima podroben zapis). */
  const [urejanje, nastaviUrejanje] = useState<'nov' | IgralecPodrobenDto | null>(null)
  /* Igralec, za katerega cakamo potrditev arhiviranja. */
  const [arhiviranec, nastaviArhiviranca] = useState<IgralecDto | null>(null)
  /* Igralec, ki mu postavljamo začetni rating (le pred prvo tekmo). */
  const [postavljanec, nastaviPostavljanca] = useState<IgralecDto | null>(null)
  /* Igralec, ki mu vpisujemo rating z zunanje lestvice (dovoljeno tudi po
     odigranih tekmah — glej ZunanjaUvrstitevOkno). */
  const [zunanjiGost, nastaviZunanjegaGosta] = useState<IgralecDto | null>(null)

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
      {/* Glave strani (nadnaslov, naslov, uvod) ni: kje smo, pove navigacija.
          Ostanejo iskalnik, dejanje in števci — tam, kjer so stali prej. */}
      <div className="stran-glava stran-glava--dno stran-glava--brez-naslova">
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
              <th scope="col">Ime in priimek</th>
              <th scope="col">Klub</th>
              <th scope="col">Spol</th>
              {jeAdmin && <th scope="col">Letnik</th>}
              <th scope="col" className="lestvica__rating">Rating</th>
              {jeAdmin && <th scope="col">Licenca NTZS</th>}
              <th scope="col" className="tabela__dejanja"></th>
            </tr>
          </thead>
          <tbody>
            {prikazani.map((igralec) => {
              /* Adminu strežnik vrne podroben zapis (glej poizvedbo zgoraj),
                 organizatorju javnega. Pogojujemo na tej vrednosti in ne na
                 jeAdmin, da so stolpci z osebnimi podatki in tip, ki jih
                 nosi, ena in ista odločitev. */
              const podroben = jeAdmin ? (igralec as IgralecPodrobenDto) : null
              return (
              <tr key={igralec.id}>
                <td className="lestvica__ime">
                  {igralec.ime} {igralec.priimek}
                </td>
                <td className={igralec.klub ? 'lestvica__klub' : 'lestvica__klub igralec-klub--brez'}>
                  {igralec.klub?.ime ?? 'brez kluba'}
                </td>
                <td className="vrstica__mono">{OZNAKE_SPOL[igralec.spol]}</td>
                {podroben && (
                  <td className="vrstica__mono">
                    {letnica(podroben.datumRojstva)}
                    {/* Pri urejanju več igralcev naenkrat se vidi, kdo je že označen. */}
                    {podroben.rekreativniVstop && (
                      <span title="Rekreativec — začetni rating 800"> · rekr.</span>
                    )}
                  </td>
                )}
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
                  {!jeAdmin && igralec.rating == null && '—'}
                  {jeAdmin && igralec.steviloTekem === 0 && (
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => nastaviPostavljanca(igralec)}
                    >
                      {igralec.rating == null ? 'Postavi rating' : 'Popravi'}
                    </button>
                  )}
                  {/* Zunanja uvrstitev je na voljo VEDNO, tudi po odigranih
                      tekmah — prav zaradi redkih gostov obstaja. */}
                  {jeAdmin && (
                    <button
                      className="gumb gumb--majhen"
                      onClick={() => nastaviZunanjegaGosta(igralec)}
                    >
                      Zunanja uvrstitev
                    </button>
                  )}
                </td>
                {podroben && (
                  <td className="vrstica__mono">{podroben.ntzsLicenca ?? '—'}</td>
                )}
                <td className="tabela__dejanja">
                  {podroben && (
                    <span>
                      <button className="gumb gumb--majhen" onClick={() => nastaviUrejanje(podroben)}>
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
              )
            })}
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

      {zunanjiGost && (
        <ZunanjaUvrstitevOkno
          igralec={zunanjiGost}
          onZapri={() => nastaviZunanjegaGosta(null)}
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
          Močnemu novincu lahko postaviš vstopni rating, da mu ni treba plezati z 1000.
          Mogoče je le pred prvo odigrano tekmo; nato rating določajo rezultati.
        </p>
        <label className="obrazec__polje">
          <span>Turnirko rating (100–3000)</span>
          <StevilskoPolje
            najvec={3000}
            vrednost={vrednost}
            naSpremembo={nastaviVrednost}
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

/* Zunanja uvrstitev: rating, prepisan z zunanje lestvice (ITTF, NTZS, druga
   zveza). Za razliko od začetnega ratinga je na voljo tudi po odigranih
   tekmah — prav zato obstaja: igralec, ki pri nas odigra dve tekmi na leto,
   ker sicer igra po svetu, ima pri nas številko, ki o njem ne pove ničesar.

   Vir in pojasnilo sta obvezna in JAVNA (izpišeta se na profilu pod grafom).
   To ni formalnost: ročno vpisana številka na javni lestvici je brez
   zapisanega vira videti kot naklonjenost, z virom pa je trditev, ki jo lahko
   vsak preveri. */
function ZunanjaUvrstitevOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  igralec: IgralecDto
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [vrednost, nastaviVrednost] = useState(String(igralec.rating ?? 1000))
  const [vir, nastaviVir] = useState('')
  const [pojasnilo, nastaviPojasnilo] = useState('')

  const shranjevanje = useMutation({
    mutationFn: () =>
      igralciApi.zunanjaUvrstitev(igralec.id, {
        vrednost: Number(vrednost),
        vir: vir.trim(),
        pojasnilo: pojasnilo.trim(),
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const stevilka = Number(vrednost)
  const veljaven =
    Number.isInteger(stevilka)
    && stevilka >= 100
    && stevilka <= 3000
    && vir.trim() !== ''
    && pojasnilo.trim() !== ''

  return (
    <ModalnoOkno
      naslov={`Zunanja uvrstitev — ${igralec.ime} ${igralec.priimek}`}
      onZapri={onZapri}
    >
      <form
        className="obrazec"
        onSubmit={(d) => {
          d.preventDefault()
          if (veljaven) shranjevanje.mutate()
        }}
      >
        <p className="obvestilo">
          Za redkega gosta, ki pri nas odigra premalo tekem, da bi ga lestvica sama uvrstila.
          Trenutno: {igralec.rating ?? 'brez ratinga'} po {igralec.steviloTekem}{' '}
          {igralec.steviloTekem === 1 ? 'tekmi' : 'tekmah'}. Vir in pojasnilo sta javna —
          izpišeta se na igralčevem profilu.
        </p>
        <label className="obrazec__polje">
          <span>Turnirko rating (100–3000)</span>
          <StevilskoPolje
            najvec={3000}
            vrednost={vrednost}
            naSpremembo={nastaviVrednost}
            autoFocus
          />
        </label>
        <label className="obrazec__polje">
          <span>Vir</span>
          <input
            type="text"
            maxLength={200}
            placeholder="npr. ITTF svetovna lestvica, september 2026"
            value={vir}
            onChange={(d) => nastaviVir(d.target.value)}
          />
        </label>
        <label className="obrazec__polje">
          <span>Pojasnilo</span>
          <textarea
            rows={3}
            maxLength={500}
            placeholder="npr. Igra skoraj samo mednarodno, pri nas dve tekmi na leto."
            value={pojasnilo}
            onChange={(d) => nastaviPojasnilo(d.target.value)}
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
            Vpiši uvrstitev
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
  /* null = nov igralec (tega sme dodati tudi organizator, čeprav obstoječih
     osebnih podatkov ne vidi). Urejanje je vezano na podroben zapis. */
  igralec: IgralecPodrobenDto | null
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
  const [rekreativniVstop, nastaviRekreativniVstop] = useState(
    igralec?.rekreativniVstop ?? false,
  )
  /* Oznaka velja ob prvi tekmi. Kdor jih že ima, dobi nov začetek šele s
     preračunom — to mora urejevalec vedeti, preden shrani. */
  const potrebenPreracun =
    igralec != null && igralec.steviloTekem > 0 && rekreativniVstop !== igralec.rekreativniVstop

  const shranjevanje = useMutation({
    mutationFn: (vnos: IgralecVnos) =>
      igralec ? igralciApi.posodobi(igralec.id, vnos) : igralciApi.ustvari(vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Opozorilo »podoben igralec že obstaja« ob dodajanju. Admin dobi zapis z
     datumom rojstva (strežnik ga drugim ne da), organizator brez njega. */
  const { jeAdmin } = useAvtentikacija()
  const preverjanje = useMutation<PodobenZapis[], Error, PodobniVnos>({
    mutationFn: (vnos) =>
      jeAdmin ? igralciApi.podobniPodrobno(vnos) : igralciApi.podobni(vnos),
  })
  /* Opozorilo velja za natanko tisti vpis, ki ga je sprožil: ko vpisovalec
     kaj popravi, izgine in ob naslednjem shranjevanju se preveri znova.
     Podpis je izpeljan, ne hranjen — brez učinka, ki bi opozorilo brisal. */
  const [opozorilo, nastaviOpozorilo] = useState<{
    podpis: string
    zadetki: PodobenZapis[]
  } | null>(null)
  const podpis = [ime.trim(), priimek.trim(), spol, datumRojstva].join('|')
  const podobni = opozorilo?.podpis === podpis ? opozorilo.zadetki : []
  const okvirOpozorila = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (podobni.length > 0) okvirOpozorila.current?.scrollIntoView({ block: 'nearest' })
  }, [podobni.length])

  /* Začetnica se popravi med tipkanjem. Vrednost polja zapišemo že tu, ne šele
     z novim izrisom: ko React polju zamenja vrednost, kazalec skoči na konec,
     popravek sredi besedila pa bi ga vsakič odnesel. Ker se spremeni le velikost
     črk, ostane dolžina ista in izbor se natančno obnovi. */
  function obSpremembiImena(nastavi: (vrednost: string) => void) {
    return (dogodek: ChangeEvent<HTMLInputElement>) => {
      const polje = dogodek.target
      const { selectionStart: zacetek, selectionEnd: konec } = polje
      polje.value = zVelikoZacetnico(polje.value)
      polje.setSelectionRange(zacetek, konec)
      nastavi(polje.value)
    }
  }

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    const vnos: IgralecVnos = {
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
      rekreativniVstop,
    }
    // urejanje ne ustvari novega zapisa, vpis, na katerega je bil bralec
    // že opozorjen, pa je s ponovnim klikom potrjen
    if (igralec || podobni.length > 0) {
      shranjevanje.mutate(vnos)
      return
    }
    // poizvedba dobi samo to, po čemer išče: e-pošta, telefon in naslov
    // novega igralca ji ne pripadajo
    const iskano: PodobniVnos = {
      ime: vnos.ime,
      priimek: vnos.priimek,
      spol: vnos.spol,
      datumRojstva: vnos.datumRojstva,
    }
    preverjanje.mutate(iskano, {
      onSuccess: (zadetki) => {
        if (zadetki.length > 0) nastaviOpozorilo({ podpis, zadetki })
        else shranjevanje.mutate(vnos)
      },
      // opozorilo je pomoč pri vpisu in ne pogoj zanj: če ga strežnik ne
      // zna dati, igralca ne sme zadržati
      onError: () => shranjevanje.mutate(vnos),
    })
  }

  return (
    <ModalnoOkno naslov={igralec ? 'Uredi igralca' : 'Nov igralec'} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Ime *</span>
            <input value={ime} onChange={obSpremembiImena(nastaviIme)} required />
          </label>
          <label className="obrazec__polje">
            <span>Priimek *</span>
            <input value={priimek} onChange={obSpremembiImena(nastaviPriimek)} required />
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

        {/* Takoj pod datumom rojstva: tudi ta pove, kje igralec začne. */}
        <label className="obrazec__potrditev">
          <input
            type="checkbox"
            checked={rekreativniVstop}
            onChange={(d) => nastaviRekreativniVstop(d.target.checked)}
          />
          <span>Rekreativec — začetni rating 800</span>
        </label>
        <p className="namig">
          Brez oznake novinec začne pri povprečju registriranih igralcev svoje starosti (odrasel
          okoli 1500). Po prvem dnevu ga v obeh primerih uvrstijo izidi.
        </p>
        {potrebenPreracun && (
          <p className="obvestilo obvestilo--opozorilo" role="status">
            Igralec že ima odigrane tekme. Nov začetek obvelja šele, ko na strani Rating poženeš
            preračun od dneva njegove prve tekme.
          </p>
        )}

        <div className="obrazec__vrstica">
          <IzbirnikKluba
            namig="Brez kluba · vpiši ime"
            klubi={klubi.data ?? []}
            izbrano={idKlub ? Number(idKlub) : null}
            naSpremembo={(id) => nastaviIdKlub(id === null ? '' : String(id))}
          />
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
          <IzbirnikKraja
            kraji={kraji.data ?? []}
            izbrano={postnaSt ? Number(postnaSt) : null}
            naSpremembo={(st) => nastaviPostnaSt(st === null ? '' : String(st))}
          />
        </div>

        {podobni.length > 0 && (
          <div ref={okvirOpozorila}>
            <PodobniIgralci zadetki={podobni} vpisanDatum={datumRojstva} />
          </div>
        )}
        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          {podobni.length > 0 && (
            <button type="button" className="gumb" onClick={() => nastaviOpozorilo(null)}>
              Popravi vnos
            </button>
          )}
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={shranjevanje.isPending || preverjanje.isPending}
          >
            {igralec
              ? 'Shrani spremembe'
              : preverjanje.isPending
                ? 'Preverjam …'
                : podobni.length > 0
                  ? 'Vseeno dodaj igralca'
                  : 'Dodaj igralca'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

type PodobenZapis = PodobenIgralecDto | PodobenIgralecPodrobenDto

const OZNAKE_UJEMANJA: Record<UjemanjeImena, string> = {
  ISTO: 'Isto ime in priimek',
  OBRNJENO: 'Ime in priimek zamenjana',
  PODOBNO: 'Podobno ime',
}

/* Datum rojstva obstoječega igralca proti vpisanemu — samo za admina, ki ga
   strežnik pošlje; organizator te vrstice ne dobi. */
function opisDatuma(zapis: PodobenIgralecPodrobenDto, vpisanDatum: string): string {
  const obstojec = oblikujDatum(zapis.igralec.datumRojstva)
  switch (zapis.datum) {
    case 'ENAK':
      return `Enak datum rojstva: ${obstojec}`
    case 'PODOBEN':
      return `Podoben datum rojstva: ${obstojec} (vpisan ${oblikujDatum(vpisanDatum)})`
    case 'DRUG':
      return `Drug datum rojstva: ${obstojec} (vpisan ${oblikujDatum(vpisanDatum)})`
  }
}

/* Opozorilo ob dodajanju: v bazi že obstaja igralec s podobnim imenom. Isto
   ime imata lahko dve osebi, zato je to vprašanje in ne zavrnitev — odloči
   vpisovalec. Klub, pas in rating zadostujejo, da ugotovi, ali gre za isto
   osebo; admin dobi povrh še datum rojstva. */
function PodobniIgralci({
  zadetki,
  vpisanDatum,
}: {
  zadetki: PodobenZapis[]
  vpisanDatum: string
}) {
  return (
    <div className="obvestilo obvestilo--opozorilo podobni" role="status">
      <strong>
        {zadetki.length === 1
          ? 'V bazi že obstaja podoben igralec.'
          : 'V bazi že obstajajo podobni igralci.'}
      </strong>
      <ul className="podobni__seznam">
        {zadetki.map((zapis) => {
          const { igralec } = zapis
          const meta = [
            igralec.klub?.ime ?? 'brez kluba',
            igralec.starostniPas ? OZNAKE_PASU_KRATKO[igralec.starostniPas] : null,
            igralec.rating != null ? `rating ${igralec.rating}` : 'brez ratinga',
            `${igralec.steviloTekem} ${sklonTekem(igralec.steviloTekem)}`,
            zapis.arhiviran ? 'arhiviran' : null,
          ].filter(Boolean)
          return (
            <li key={igralec.id} className="podobni__igralec">
              <span className="podobni__ime">
                {igralec.ime} {igralec.priimek}
              </span>{' '}
              <span className="podobni__ujemanje">{OZNAKE_UJEMANJA[zapis.ujemanje]}</span>
              <span className="podobni__meta">{meta.join(' · ')}</span>
              {'datum' in zapis && (
                <span
                  className={
                    zapis.datum === 'ENAK'
                      ? 'podobni__meta podobni__meta--enak'
                      : 'podobni__meta'
                  }
                >
                  {opisDatuma(zapis, vpisanDatum)}
                  {zapis.datum === 'ENAK' && ' — skoraj gotovo isti igralec'}
                </span>
              )}
            </li>
          )
        })}
      </ul>
      <p className="podobni__vprasanje">
        {zadetki.length === 1
          ? 'Preveri, ali vpisani igralec ni isti kot ta.'
          : 'Preveri, ali vpisani igralec ni isti kot kdo od teh.'}
      </p>
    </div>
  )
}
