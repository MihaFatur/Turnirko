/* Celoten koledar: mesec po mesecu, s podrobnostmi vsakega dneva.

   Kaj ima ta stran in sklop na domači strani nima:
   - listanje NAZAJ. Vsa uvožena zgodovina NTZS (sezone od 2012/13) ima datume,
     zato je koledar tudi pot vanjo — »kdaj smo lani igrali z Ljubljano«.
   - izpis dneva. Klik na dan odpre njegove vnose s pari kola; brez izbranega
     dneva stran izpiše cel mesec po dnevih, a brez parov (v mesecu z devetimi
     ligami bi to bilo nekaj sto vrstic).
   - filtre (vrsta, stanje, klub, kraj) prek skupnih krmil.

   Izbrani dan živi v naslovu (?dan=2026-10-04), filtri pa v stanju strani.
   To ni nedoslednost: dan je KRAJ, na katerega se pride po povezavi z domače
   strani in ki ga je smiselno deliti, filter pa je pogled nanj — isto pravilo
   kot pri iskanju na lestvici. */
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { koledarApi } from '../api/zahteve'
import { OZNAKE_STATUS_TEKMOVANJA, type KoledarVnosDto } from '../api/tipi'
import {
  KrmilaSeznama,
  poSeznamu,
  useFiltri,
  type Razvrstitev,
  type SkupinaFiltra,
} from '../komponente/Filtri'
import { MrezaMeseca, VrsticaKoledarja } from '../komponente/Koledar'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import {
  danesIso,
  imeMeseca,
  kljucTekmovanja,
  mesecIzIso,
  oblikujDanPolno,
  poDnevih,
  prviDanMeseca,
  zadnjiDanMeseca,
  type Mesec,
} from '../pomozno/koledar'
import { sklonTekmovanj } from '../pomozno/oblikovanje'
import { useTelefon } from '../pomozno/telefon'

/* Merila nad koledarjem. Kraj ima samo turnir (liga se igra pri domačih
   ekipah, torej vsako kolo drugje), zato se ta skupina po pravilu »skupina je
   podatkovna« izriše šele, ko so v mesecu vsaj dva različna kraja.

   Vrsta je »zunanja«: krmili jo segmentirani izbirnik nad mrežo in ne okno z
   merili — dve krmili za isto merilo eno vrsto narazen sta past. */
const SKUPINE: SkupinaFiltra<KoledarVnosDto>[] = [
  {
    kljuc: 'vrsta',
    oznaka: 'Vrsta',
    vrednost: (v) => v.vrsta,
    napis: (v) => (v === 'TURNIR' ? 'Turnir' : 'Ligaško kolo'),
    vrstniRed: poSeznamu(['TURNIR', 'LIGA']),
    zunanja: true,
  },
  {
    kljuc: 'stanje',
    oznaka: 'Stanje',
    vrednost: (v) => v.status,
    napis: (v) => OZNAKE_STATUS_TEKMOVANJA[v as keyof typeof OZNAKE_STATUS_TEKMOVANJA],
    vrstniRed: poSeznamu(['V_TEKU', 'PRIPRAVA', 'ZAKLJUCEN']),
  },
  { kljuc: 'klub', oznaka: 'Klub', vrednost: (v) => v.klub },
  { kljuc: 'kraj', oznaka: 'Kraj', vrednost: (v) => v.kraj ?? v.dvorana },
]

/* Ena sama razvrstitev: koledar je po definiciji urejen po datumu. Krmila
   izbirnik pri enem samem merilu ne izrišejo. */
const RAZVRSTITVE: Razvrstitev<KoledarVnosDto>[] = [
  {
    kljuc: 'datum',
    oznaka: 'Po datumu',
    primerjaj: (a, b) => a.datum.localeCompare(b.datum) || a.ime.localeCompare(b.ime, 'sl'),
  },
]

/* Postavke segmentiranega izbirnika vrste. »Vse« je prazen izbor skupine in ne
   tretja vrednost — filter, ki ne omejuje, ne sme obstajati v stanju. */
const VRSTE: { kljuc: string; oznaka: string; vrednosti: string[] }[] = [
  { kljuc: 'VSE', oznaka: 'Vse', vrednosti: [] },
  { kljuc: 'TURNIR', oznaka: 'Turnirji', vrednosti: ['TURNIR'] },
  { kljuc: 'LIGA', oznaka: 'Lige', vrednosti: ['LIGA'] },
]

/* Koliko vnosov meseca izpiše telefon, dokler dan ni izbran. Cel mesec je pod
   mrežo dolg tudi trideset vrstic in gledalec do spodnje vrstice ne pride —
   na telefonu je seznam napoved, mreža pa pot do dneva. Ostalo odpre gumb pod
   njim; brez njega bi bil mesec dostopen samo po enem dnevu naenkrat. */
const NA_TELEFONU = 4

export function KoledarStran() {
  const jeTelefon = useTelefon()
  const danes = danesIso()
  const [naslov, nastaviNaslov] = useSearchParams()

  /* Dan iz naslova je hkrati izhodišče meseca: povezava z domače strani
     (?dan=…) mora odpreti pravi mesec, ne tekočega. */
  const izbraniDan = naslov.get('dan')
  const [mesec, nastaviMesec] = useState<Mesec>(() => mesecIzIso(izbraniDan ?? danes))

  const od = prviDanMeseca(mesec)
  const doKdaj = zadnjiDanMeseca(mesec)
  const jeTekoci = danes >= od && danes <= doKdaj

  const koledar = useQuery({
    queryKey: ['koledar', od, doKdaj],
    queryFn: () => koledarApi.obdobje(od, doKdaj),
  })

  const vsi = useMemo(() => koledar.data ?? [], [koledar.data])
  const filtri = useFiltri(vsi, SKUPINE, RAZVRSTITVE)
  const prikazani = filtri.prikazani

  /* Katera postavka izbirnika je aktivna. Oba statusa hkrati (izbrana v oknu
     pred prenovo) pomenita isto kot brez omejitve, zato »Vse«. */
  const izbraneVrste = filtri.izbor['vrsta'] ?? []
  const vrsta = izbraneVrste.length === 1 ? izbraneVrste[0] : 'VSE'

  /* Dnevi meseca z vsaj enim vnosom, urejeni naraščajoče. */
  const dnevi = useMemo(() => {
    const zbir = poDnevih(prikazani)
    return [...zbir.entries()]
      .filter(([dan]) => dan >= od && dan <= doKdaj)
      .sort((a, b) => a[0].localeCompare(b[0]))
  }, [prikazani, od, doKdaj])

  const izpisani = izbraniDan ? dnevi.filter(([dan]) => dan === izbraniDan) : dnevi

  /* Telefon bere ravno vrsto (dan, vnos) in ne skupin po dnevih: podnaslov z
     dnevom bi na 390 px podvojil datumski blok vrstice pod njim. */
  const ravni = useMemo(
    () => izpisani.flatMap(([dan, vnosi]) => vnosi.map((vnos) => ({ dan, vnos }))),
    [izpisani],
  )
  const [vseNaTelefonu, nastaviVseNaTelefonu] = useState(false)
  /* Ob listanju meseca in ob izbiri dneva se seznam spet skrči — razširitev
     velja za pogled, ki ga je gledalec razširil, in ne za vsak naslednji. */
  useEffect(() => nastaviVseNaTelefonu(false), [mesec, izbraniDan])
  const skrcen = !izbraniDan && !vseNaTelefonu
  const naTelefonu = skrcen ? ravni.slice(0, NA_TELEFONU) : ravni

  function izberiDan(dan: string) {
    /* Ponoven klik na isti dan izbor sprosti — dan je preklop in ne gumb. */
    if (dan === izbraniDan) nastaviNaslov({}, { replace: true })
    else nastaviNaslov({ dan }, { replace: true })
  }

  function preberiMesec(novi: Mesec) {
    nastaviMesec(novi)
    /* Dan pripada mesecu: ob listanju izbor odpade, sicer bi seznam kazal dan
       iz drugega meseca kot mreža. */
    if (izbraniDan) nastaviNaslov({}, { replace: true })
  }

  /* Segmentirani izbirnik vrste in za njim mono imena skupin, ki ostanejo v
     oknu. Imena so napoved in ne krmilo: povedo, po čem se sploh da filtrirati,
     preden gledalec odpre okno. */
  const izbirnikVrste = (
    <>
      <div className="izbirnik">
        {VRSTE.map((v) => (
          <button
            type="button"
            key={v.kljuc}
            className={'izbirnik__gumb' + (vrsta === v.kljuc ? ' izbirnik__gumb--aktiven' : '')}
            aria-pressed={vrsta === v.kljuc}
            onClick={() => filtri.nastaviSkupino('vrsta', v.vrednosti)}
          >
            {v.oznaka}
          </button>
        ))}
      </div>
      {filtri.moznosti.length > 0 && (
        <span className="krmila__skupine" aria-hidden="true">
          <span className="krmila__locilo" />
          {filtri.moznosti.map((s) => (
            <span key={s.kljuc}>{s.oznaka}</span>
          ))}
        </span>
      )}
    </>
  )

  const telo = (
    <>
      <NapakaPoizvedbe poizvedba={koledar} kaj="koledarja" />
      {vsi.length > 0 && (
        <KrmilaSeznama
          stanje={filtri}
          razvrstitve={RAZVRSTITVE}
          naslovOkna="Koledar"
          imeZadetkov={sklonTekmovanj}
          poFiltru={izbirnikVrste}
          desno={`${prikazani.length} ${sklonTekmovanj(prikazani.length)}`}
        />
      )}

      <div className="koledar-stran">
        <div className="koledar-stran__mreza">
          <MrezaMeseca
            mesec={mesec}
            vnosi={prikazani}
            danes={danes}
            izbraniDan={izbraniDan}
            naDan={izberiDan}
            naMesec={preberiMesec}
            naDanes={jeTekoci ? undefined : () => preberiMesec(mesecIzIso(danes))}
            kljuc={jeTelefon ? 'kratek' : 'poln'}
            /* Na telefonu je celica 60 px in ne 92 px — četrti pas trakov bi
               segel čez njeno dno. */
            najvecPasov={jeTelefon ? 3 : 4}
            /* Kompaktna celica telefona je 60 px: trije pasovi sežejo do njenega
               dna in »+N« bi se z njimi prekril. Kaj dan nosi, pove seznam pod
               mrežo — klik na dan ga zoži nanj. */
            sStevcem={!jeTelefon}
          />
        </div>

        <div className="koledar-stran__seznam">
          <div className="naslovna-vrstica">
            <h2 className="sekcija__naslov--manjsi">
              {izbraniDan ? oblikujDanPolno(izbraniDan) : imeMeseca(mesec)}
            </h2>
            {izbraniDan && (
              <button
                type="button"
                className="povezava-gumb"
                onClick={() => nastaviNaslov({}, { replace: true })}
              >
                Cel mesec
              </button>
            )}
          </div>

          {koledar.isPending && <p className="obvestilo">Nalaganje …</p>}

          {!koledar.isPending && vsi.length === 0 && (
            <p className="obvestilo">
              Ta mesec ni tekmovanj. S puščicama nad mrežo prelistaš mesece — koledar
              nosi tudi uvoženo zgodovino.
            </p>
          )}

          {vsi.length > 0 && izpisani.length === 0 && (
            <p className="obvestilo">
              {filtri.steviloIzbranih > 0 || vrsta !== 'VSE' ? (
                <>
                  Izbranim merilom ta mesec ne ustreza nobeno tekmovanje.{' '}
                  <button type="button" className="povezava-gumb" onClick={filtri.pocisti}>
                    Počisti filtre
                  </button>
                </>
              ) : (
                'Ta dan ni tekmovanj.'
              )}
            </p>
          )}

          {jeTelefon
            ? naTelefonu.map(({ dan, vnos }) => (
                <VrsticaKoledarja
                  key={`${dan}-${kljucTekmovanja(vnos)}-${vnos.kolo ?? ''}`}
                  vnos={vnos}
                  danes={danes}
                  dan={dan}
                  sPari={izbraniDan !== null}
                />
              ))
            : izpisani.map(([dan, vnosi]) => (
                <div className="koledar-dan" key={dan}>
                  {/* Pri izbranem dnevu je datum že naslov sklopa; podnaslov bi
                      ga ponovil takoj pod njim. */}
                  {!izbraniDan && <h3 className="podnaslov-sekcije">{oblikujDanPolno(dan)}</h3>}
                  {vnosi.map((vnos) => (
                    <VrsticaKoledarja
                      key={`${kljucTekmovanja(vnos)}-${vnos.datum}-${vnos.kolo ?? ''}`}
                      vnos={vnos}
                      danes={danes}
                      dan={dan}
                      /* Pari samo pri izbranem dnevu: cel mesec z devetimi ligami
                         bi jih naštel nekaj sto. */
                      sPari={izbraniDan !== null}
                    />
                  ))}
                </div>
              ))}

          {jeTelefon && skrcen && ravni.length > naTelefonu.length && (
            <button
              type="button"
              className="gumb gumb--majhen koledar-stran__vec"
              onClick={() => nastaviVseNaTelefonu(true)}
            >
              Pokaži vse ({ravni.length})
            </button>
          )}
        </div>
      </div>
    </>
  )

  if (jeTelefon) {
    return (
      <section>
        <div>
          <span className="naslov-mobi__nad">Turnirji in lige</span>
          <h1 className="naslov-mobi naslov-mobi--seznam">Koledar</h1>
        </div>
        {telo}
      </section>
    )
  }

  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Turnirji in lige</span>
            <span className="naslov-strani__glavni">Koledar</span>
          </h1>
          <p className="uvod">
            Kdaj in kje se igra. Moder pas je turnir, zelen ligaško kolo. Klik na dan
            odpre njegova srečanja.
          </p>
        </div>
      </div>
      {telo}
    </section>
  )
}
