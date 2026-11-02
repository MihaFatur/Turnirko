/* Sklop »Moj igralec« na domači strani (V57): navijač spremlja ljudi, ne le
   lige — starša zanima, kako gre otroku, ne celotni ligi. Vrstica igralca je
   mali zapisnik: kdo je, kje je na lestvici, zadnje tri tekme in kdaj igra
   naslednjič. Vse to je že javno na profilu igralca (strežnik zasebnih analiz
   v povzetek ne da).

   Gosta sklopa ni: izbor je last računa. Račun brez spremljanega igralca sklop
   vidi samo, če je navijač — njemu vrstica pove, kako se igralec doda; igralcu
   in organizatorju bi bila le šum. Mesto sklopa je med vrhom domače strani in
   lestvico, ker je odgovor na vprašanje »kako je z mojim igralcem« tisto, po
   kar navijač pride. */
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { domovApi } from '../api/zahteve'
import type { DomovIgralecDto, TekmaProfila } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { imeLestvice, oblikujDanKratekMesec, oblikujTermin } from '../pomozno/oblikovanje'
import { useSpremljanjeIgralcev } from '../pomozno/spremljaniIgralci'
import { GumbSpremljanja } from './GumbSpremljanja'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'
import { ZnackaStatusa } from './Znacka'

export function MojIgralecSklop() {
  const { uporabnik, imaSirokoSpremljanje } = useAvtentikacija()
  const povzetki = useQuery({
    queryKey: ['moji-igralci-povzetki', uporabnik?.id ?? null],
    queryFn: domovApi.povzetkiIgralcev,
    enabled: uporabnik !== null,
  })
  const { preklopi, napaka } = useSpremljanjeIgralcev()

  if (!uporabnik) return null
  const igralci = povzetki.data ?? []
  const jeNavijac = uporabnik.vloga === 'NAVIJAC'
  /* Dokler povzetek ne pride, ne pokažemo nič: sklop, ki bi se pojavil in nato
     izginil, bi stran premikal dvakrat. */
  if (povzetki.isPending) return null
  if (!povzetki.isError && igralci.length === 0 && !jeNavijac) return null

  return (
    <div className="domov__sklop domov__moj-igralec">
      <div className="naslovna-vrstica">
        <h2>{igralci.length > 1 ? 'Moji igralci' : 'Moj igralec'}</h2>
      </div>
      <NapakaPoizvedbe poizvedba={povzetki} kaj="igralca" />
      <SporociloNapake napaka={napaka} />
      {igralci.map((igralec) => (
        <VrsticaIgralca
          igralec={igralec}
          zapisnik={imaSirokoSpremljanje}
          naPreklop={() => preklopi(igralec.idIgralec)}
          key={igralec.idIgralec}
        />
      ))}
      {igralci.length === 0 && !povzetki.isError && (
        <div className="domov__liga-dodaj">
          <span className="domov__namig">
            Še ne spremljaš nobenega igralca. Na profilu igralca pritisni »Spremljaj«: tu bodo
            zadnje tekme in naslednji termin.
          </span>
          <Link to="/lestvica" className="gumb gumb--majhen">
            Poišči igralca
          </Link>
        </div>
      )}
    </div>
  )
}

function VrsticaIgralca({
  igralec,
  zapisnik,
  naPreklop,
}: {
  igralec: DomovIgralecDto
  /* Račun s paketom dobi povezavo na sezonski zapisnik za tisk. */
  zapisnik: boolean
  naPreklop: () => void
}) {
  const { uvrstitev } = igralec
  return (
    <div className="domov__igralec">
      <div className="domov__igralec-levo">
        <div className="domov__liga-glava">
          <Link to={`/igralci/${igralec.idIgralec}/profil`} className="domov__liga-ime">
            {igralec.polnoIme}
          </Link>
          {/* Družino racun ureja na strani Naročnina; spremljanje je preklop (V57). */}
          {!igralec.druzina && <GumbSpremljanja ime={igralec.polnoIme} spremljam naPreklop={naPreklop} />}
        </div>
        <div className="domov__igralec-klub">{igralec.klub ?? 'brez kluba'}</div>
        {igralec.druzina && (
          <div className="domov__igralec-druzina">
            Družina · <Link to={`/igralci/${igralec.idIgralec}/profil`}>Zasebna statistika →</Link>
          </div>
        )}
        <div className="domov__igralec-rating">
          <span className="domov__igralec-stevilka">{igralec.rating ?? '—'}</span>
          <span className="domov__igralec-oznaka">Turnirko rating</span>
        </div>
        {uvrstitev.mesto !== null && (
          <div className="domov__igralec-mesto">
            {uvrstitev.mesto}. od {uvrstitev.skupajIgralcev} · {imeLestvice(uvrstitev)}
          </div>
        )}
        {zapisnik && (
          <div className="domov__igralec-druzina">
            <Link to={`/igralci/${igralec.idIgralec}/sezona`}>Sezonski zapisnik →</Link>
          </div>
        )}
      </div>

      <div className="domov__igralec-desno">
        <NaslednjiTermin igralec={igralec} />
        <div className="domov__igralec-podnaslov">Zadnje tekme</div>
        {igralec.zadnje.length === 0 ? (
          <p className="domov__prazno">Brez odigranih tekem.</p>
        ) : (
          <div className="domov__igralec-tekme">
            {igralec.zadnje.map((t) => (
              <TekmaIgralca tekma={t} key={`${t.ligaska ? 'l' : 't'}${t.idTekme}`} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/* Naslednje ligaško srečanje ekipe in/ali prihajajoči turnir. Ligaško
   srečanje igralec ne igra nujno (kader je širši od postave), zato vrstica
   navaja ekipo in ne igralca. */
function NaslednjiTermin({ igralec }: { igralec: DomovIgralecDto }) {
  const srecanje = igralec.naslednjeSrecanje
  const turnir = igralec.prihajajociTurnir
  if (!srecanje && !turnir) return null
  return (
    <div className="domov__igralec-naslednje">
      <div className="domov__igralec-podnaslov">Naslednje</div>
      {srecanje && (
        <Link to={`/srecanja/${srecanje.idSrecanje}`} className="domov__igralec-termin">
          <span className="domov__igralec-termin-kdaj">
            {srecanje.zacetek ? oblikujTermin(srecanje.zacetek) : `${srecanje.kolo}. kolo`}
          </span>
          <span className="domov__igralec-termin-opis">
            {srecanje.ekipa} – {srecanje.nasprotnik}
            <span className="domov__igralec-termin-pod">
              {srecanje.liga} · {srecanje.kolo}. kolo · {srecanje.doma ? 'doma' : 'v gosteh'}
            </span>
          </span>
        </Link>
      )}
      {turnir && (
        <Link to={`/turnirji/${turnir.idTurnir}`} className="domov__igralec-termin">
          <span className="domov__igralec-termin-kdaj">{oblikujDanKratekMesec(turnir.datum)}</span>
          <span className="domov__igralec-termin-opis">
            {turnir.ime}
            <span className="domov__igralec-termin-pod">
              Prijavljen na turnir · <ZnackaStatusa status={turnir.status} />
            </span>
          </span>
        </Link>
      )}
    </div>
  )
}

function TekmaIgralca({ tekma }: { tekma: TekmaProfila }) {
  const sprememba = tekma.spremembaRatinga
  return (
    <div className="domov__igralec-tekma">
      <span className="domov__igralec-tekma-datum">{oblikujDanKratekMesec(tekma.datum)}</span>
      <span className="domov__igralec-tekma-ime">
        {tekma.nasprotnik}
        <span className="domov__igralec-termin-pod">{tekma.tekmovanje}</span>
      </span>
      <span
        className={
          'domov__igralec-tekma-izid ' + (tekma.zmaga ? 'profil__zmaga' : 'profil__poraz')
        }
      >
        {tekma.niziZa}:{tekma.niziProti}
      </span>
      <span
        className={
          'domov__igralec-tekma-rating'
          + (sprememba === null ? '' : sprememba >= 0 ? ' profil__zmaga' : ' profil__poraz')
        }
      >
        {sprememba === null ? '' : `${sprememba >= 0 ? '+' : '−'}${Math.abs(sprememba)}`}
      </span>
    </div>
  )
}
