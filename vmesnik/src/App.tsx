/* Usmerjanje med stranmi. Vse strani so ogrnjene v skupno postavitev
   (glava z navigacijo + vsebina). Strani za urejanje (igralci, šifranti)
   so dostopne samo prijavljenemu administratorju; gost je preusmerjen. */
import type { ReactElement } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'

import { Postavitev } from './komponente/Postavitev'
import { useAvtentikacija } from './avtentikacija/AvtentikacijaKontekst'
import { DomacaStran } from './strani/DomacaStran'
import { TurnirjiStran } from './strani/TurnirjiStran'
import { TurnirStran } from './strani/TurnirStran'
import { DogodekStran } from './strani/DogodekStran'
import { ListkiStran } from './strani/ListkiStran'
import { IgralciStran } from './strani/IgralciStran'
import { SifrantiStran } from './strani/SifrantiStran'
import { LestvicaStran } from './strani/LestvicaStran'
import { DvobojStran } from './strani/DvobojStran'
import { KoledarStran } from './strani/KoledarStran'
import { LigeStran } from './strani/LigeStran'
import { LigaStran } from './strani/LigaStran'
import { SrecanjeStran } from './strani/SrecanjeStran'
import { ListkiSrecanjaStran } from './strani/ListkiSrecanjaStran'
import { ProfilStran } from './strani/ProfilStran'
import { RacuniStran } from './strani/RacuniStran'
import { RatingStran } from './strani/RatingStran'
import { RazlagaRatingaStran } from './strani/RazlagaRatingaStran'
import { PogojiStran } from './strani/PogojiStran'
import { UvozStran } from './strani/UvozStran'
import { NarocninaStran } from './strani/NarocninaStran'
import { OrganizatorskiPregledStran } from './strani/OrganizatorskiPregledStran'
import { RegistracijaZakljucenaStran } from './strani/RegistracijaZakljucenaStran'

/* Ovoj, ki stran razkrije samo administratorju; med preverjanjem prijave
   pokaže obvestilo, gosta pa preusmeri na lestvico. */
function SamoAdmin({ children }: { children: ReactElement }) {
  const { jeAdmin, nalaganje } = useAvtentikacija()
  if (nalaganje) return <p className="obvestilo">Preverjanje prijave …</p>
  if (!jeAdmin) return <Navigate to="/lestvica" replace />
  return children
}

/* Ovoj za urejevalce (administrator ali organizator). Stran igralcev je tu -
   organizator sme dodati novega igralca (urejevalna dejanja zanj skrije sama
   stran); gosta in navadnega igralca preusmeri na lestvico. */
function SamoUrejevalec({ children }: { children: ReactElement }) {
  const { jeAdmin, jeOrganizator, nalaganje } = useAvtentikacija()
  if (nalaganje) return <p className="obvestilo">Preverjanje prijave …</p>
  if (!jeAdmin && !jeOrganizator) return <Navigate to="/lestvica" replace />
  return children
}

/* Bližnjica "Moj profil": prijavljenega igralca preusmeri na njegov profil,
   organizatorju pokaže organizatorski pregled. Račun, ki čaka na potrditev,
   še nima povezanega igralca (oz. potrjene vloge), zato mu razložimo, zakaj
   strani (še) ni. */
function MojProfil() {
  const { nalaganje, mojIdIgralec, uporabnik, jeOrganizator } = useAvtentikacija()
  if (nalaganje) return <p className="obvestilo">Preverjanje prijave …</p>
  if (mojIdIgralec !== null) return <Navigate to={`/igralci/${mojIdIgralec}/profil`} replace />
  if (uporabnik?.vloga === 'ORGANIZATOR') {
    if (jeOrganizator) return <OrganizatorskiPregledStran />
    if (!uporabnik.emailPotrjen) {
      return (
        <p className="obvestilo">
          Tvoja e-pošta še ni potrjena. Vpiši kodo, ki si jo dobil po pošti (v meniju v desnem
          kotu: »Vpiši kodo«).
        </p>
      )
    }
    return (
      <p className="obvestilo">
        {uporabnik.status === 'ZAVRNJEN'
          ? 'Tvoj račun je bil zavrnjen.'
          : 'Tvoj račun čaka na potrditev administratorja. Ko ga potrdi, se tu odpre pregled tvojih tekmovanj.'}
      </p>
    )
  }
  if (uporabnik?.vloga === 'IGRALEC') {
    if (uporabnik.status === 'ZAVRNJEN') {
      return <p className="obvestilo">Tvoj račun je bil zavrnjen.</p>
    }
    /* Dokler naslov (ali skrbnik) ni potrjen, admin računa sploh ne vidi -
       kar manjka, je koda, ne potrditev. */
    if (!uporabnik.emailPotrjen || uporabnik.potrebnaKodaSkrbnika) {
      return (
        <p className="obvestilo">
          {uporabnik.emailPotrjen
            ? 'Tvoj račun čaka na kodo, ki jo je po e-pošti dobil starš oz. skrbnik.'
            : 'Tvoja e-pošta še ni potrjena. Vpiši kodo, ki si jo dobil po pošti.'}{' '}
          Vpis kode najdeš v meniju v desnem kotu (»Vpiši kodo«).
        </p>
      )
    }
    return (
      <p className="obvestilo">
        Tvoj račun čaka na potrditev administratorja. Med igralci ni bilo natanko enega
        zapisa s tvojim imenom in datumom rojstva, zato ga administrator poveže ročno; ko ga
        poveže, se tu odpre tvoj profil s statistiko.
      </p>
    )
  }
  return <Navigate to="/lestvica" replace />
}

/* Neznana pot. Gost pogosto pride po povezavi, ki jo je nekdo delil - zato
   stran ne sme biti slepa ulica, ampak mora ponuditi poti, ki jih res ima. */
function StranNeObstaja() {
  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">404</span>
            <span className="naslov-strani__glavni">Te strani ni</span>
          </h1>
          <p className="uvod">
            Povezava je napačna ali pa je bil zapis izbrisan. Iz teh strani prideš do vsega
            javnega:
          </p>
        </div>
      </div>
      <div className="stran-glava__dejanja">
        <Link to="/" className="gumb gumb--glavni">
          Na pregled
        </Link>
        <Link to="/turnirji" className="gumb">
          Turnirji
        </Link>
        <Link to="/lige" className="gumb">
          Lige
        </Link>
        <Link to="/lestvica" className="gumb">
          Lestvica
        </Link>
      </div>
    </section>
  )
}

export function App() {
  return (
    <Routes>
      {/* Natis listkov je zunaj skupne postavitve - stran brez navigacije,
          da je tiskanje čisto (turnirski dogodki in ekipna srečanja). */}
      <Route path="/dogodki/:id/listki" element={<ListkiStran />} />
      <Route path="/srecanja/:id/listki" element={<ListkiSrecanjaStran />} />
      <Route element={<Postavitev />}>
        <Route index element={<DomacaStran />} />
        <Route path="/turnirji" element={<TurnirjiStran />} />
        <Route path="/turnirji/:id" element={<TurnirStran />} />
        <Route path="/dogodki/:id" element={<DogodekStran />} />
        <Route path="/lige" element={<LigeStran />} />
        <Route path="/lige/:id" element={<LigaStran />} />
        <Route path="/srecanja/:id" element={<SrecanjeStran />} />
        <Route path="/lestvica" element={<LestvicaStran />} />
        <Route path="/dvoboj" element={<DvobojStran />} />
        <Route path="/koledar" element={<KoledarStran />} />
        <Route path="/o-ratingu" element={<RazlagaRatingaStran />} />
        <Route path="/pogoji" element={<PogojiStran />} />
        <Route path="/igralci/:id/profil" element={<ProfilStran />} />
        <Route path="/moj-profil" element={<MojProfil />} />
        <Route path="/narocnina" element={<NarocninaStran />} />
        <Route path="/registracija/zakljucena" element={<RegistracijaZakljucenaStran />} />
        <Route
          path="/racuni"
          element={
            <SamoAdmin>
              <RacuniStran />
            </SamoAdmin>
          }
        />
        <Route
          path="/igralci"
          element={
            <SamoUrejevalec>
              <IgralciStran />
            </SamoUrejevalec>
          }
        />
        <Route
          path="/sifranti"
          element={
            <SamoAdmin>
              <SifrantiStran />
            </SamoAdmin>
          }
        />
        <Route
          path="/uvoz"
          element={
            <SamoAdmin>
              <UvozStran />
            </SamoAdmin>
          }
        />
        <Route
          path="/rating"
          element={
            <SamoAdmin>
              <RatingStran />
            </SamoAdmin>
          }
        />
        <Route path="*" element={<StranNeObstaja />} />
      </Route>
    </Routes>
  )
}
