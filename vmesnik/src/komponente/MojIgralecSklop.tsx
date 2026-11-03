/* Sklop »Moji igralci« na domači strani (V57): navijač spremlja ljudi, ne le
   lige — starša zanima, kako gre otroku, ne celotni ligi. Vsak igralec je
   izkaznica: kdo je, kje je na lestvici, forma, zadnje tekme in kdaj igra
   naslednjič (izgled in gibanje: MojiIgralciIzkaznica). Vse to je že javno na
   profilu igralca (strežnik zasebnih analiz v povzetek ne da).

   Gosta sklopa ni: izbor je last računa. Račun brez spremljanega igralca sklop
   vidi samo, če je navijač — njemu izkaznica pove, kako se igralec doda;
   igralcu in organizatorju bi bila le šum. Mesto sklopa je med vrhom domače
   strani in lestvico, ker je odgovor na vprašanje »kako je z mojim igralcem«
   tisto, po kar navijač pride. */
import { useQuery } from '@tanstack/react-query'

import { domovApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { useSpremljanjeIgralcev } from '../pomozno/spremljaniIgralci'
import { MojiIgralciIzkaznica, type StanjeSklopa } from './MojiIgralciIzkaznica'
import { SporociloNapake } from './SporociloNapake'

export function MojIgralecSklop() {
  const { uporabnik, imaSirokoSpremljanje } = useAvtentikacija()
  const povzetki = useQuery({
    queryKey: ['moji-igralci-povzetki', uporabnik?.id ?? null],
    queryFn: domovApi.povzetkiIgralcev,
    enabled: uporabnik !== null,
  })
  const { preklopi, napaka, spremljani } = useSpremljanjeIgralcev()

  if (!uporabnik) return null
  const igralci = povzetki.data ?? []
  const jeNavijac = uporabnik.vloga === 'NAVIJAC'

  let stanje: StanjeSklopa = 'vsebina'
  if (povzetki.isError) {
    stanje = 'napaka'
  } else if (povzetki.isPending) {
    /* Okostje samo, če je znano, da bo sklop prikazan (navijač ali račun z
       izborom): sklop, ki bi se pojavil in nato izginil, bi stran premikal
       dvakrat. Sicer ostane stran nespremenjena. */
    if (!jeNavijac && spremljani.length === 0) return null
    stanje = 'nalaganje'
  } else if (igralci.length === 0) {
    if (!jeNavijac) return null
    stanje = 'prazno'
  }

  return (
    <MojiIgralciIzkaznica
      stanje={stanje}
      igralci={igralci}
      zapisnik={imaSirokoSpremljanje}
      naPreklop={(idIgralec) => preklopi(idIgralec)}
      poizvedba={povzetki}
      sporocilo={<SporociloNapake napaka={napaka} />}
    />
  )
}
