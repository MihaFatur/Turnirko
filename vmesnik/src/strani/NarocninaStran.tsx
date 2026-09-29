/* Pregled in upravljanje plačilnega paketa (Premium za igralca, Basic/Plus/Pro
   za organizatorja). Nadgradnja Free → plačljiv paket gre prek Stripe
   Checkouta - stran samo začne plačilo in preusmeri brskalnik tja; račun in
   nova naročnina nastaneta šele prek webhooka (glej PlacilaStoritev).

   Igralec in organizator imata vsak svojo stran (NarocninaIgralec,
   NarocninaOrganizator): obdobje, preklic, obnova, preklop plačevanja oz.
   zamenjava paketa. Tu je samo izbira po vlogi. */
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { NarocninaIgralec } from '../komponente/NarocninaIgralec'
import { NarocninaOrganizator } from '../komponente/NarocninaOrganizator'

export function NarocninaStran() {
  const { uporabnik, nalaganje } = useAvtentikacija()

  if (nalaganje) return <p className="obvestilo">Preverjanje prijave …</p>
  if (!uporabnik) {
    return <p className="obvestilo">Za pregled naročnine se najprej prijavi.</p>
  }
  if (uporabnik.vloga === 'ADMIN') {
    return <p className="obvestilo">Administrator nima placilnega paketa.</p>
  }
  if (uporabnik.vloga === 'IGRALEC') {
    return <NarocninaIgralec />
  }

  return <NarocninaOrganizator />
}
