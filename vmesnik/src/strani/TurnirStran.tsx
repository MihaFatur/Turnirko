/* Stran enega turnirja: osnovni podatki, seznam dogodkov (tekmovanj)
   in dodajanje novega dogodka. Turnir se lahko zakljuci sele, ko so
   zakljuceni vsi njegovi dogodki - to pravilo preverja zaledje.

   Na telefonu je glava kompaktna (naslov 32 px namesto 80), stiri vrstice
   kolofona so mreza 2 x 2, dejanja urejevalca pa so v lepljivi glavi: gost in
   igralec (uporabnik st. 1) pridemo po kategorije, ne po gumbe. Pogoj za
   zakljucek turnirja je tam onemogocena postavka s pojasnilom in ne posebna
   vrstica v verzalkah pred vsebino. */
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { turnirjiApi } from '../api/zahteve'
import type {
  Disciplina,
  DogodekDto,
  DogodekVnos,
  SistemTekmovanja,
  SpolKategorija,
} from '../api/tipi'
import {
  OZNAKE_SISTEM,
  OZNAKE_SISTEM_KRATKO,
  OZNAKE_SISTEM_MOBI,
  OZNAKE_SPOL_KATEGORIJA,
  kategorijeZaDisciplino,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GlavaDejanja, GlavaZavihki, useNazaj } from '../komponente/GlavaTelefona'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { Napredek, PalicaMobi } from '../komponente/Napredek'
import { ZanimivostiTekmovanja } from '../komponente/ZanimivostiTekmovanja'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { StatusMobi, ZnackaStatusa, ZnackaVNaslovu } from '../komponente/Znacka'
import {
  oblikujObdobje,
  oblikujObdobjeKratko,
  sklonNizov,
  sklonPrijav,
  sklonPrijavljenih,
  sklonSkupin,
  sklonTekmovanj,
} from '../pomozno/oblikovanje'
import { intervalOsvezevanja } from '../pomozno/osvezevanje'
import { useTelefon } from '../pomozno/telefon'

/* Zavihka strani turnirja. »Zanimivosti« so čez VSE dogodke skupaj — na ravni
   ene kategorije je tekem pogosto premalo, da bi kaj povedale. */
type PogledTurnirja = 'DOGODKI' | 'ZANIMIVOSTI'

/* Pod tem številom odigranih tekem zavihka ne ponudimo. Isti prag ima
   strežnik (StatistikaTekmovanjaStoritev.PRAG_TEKEM); tu je zato, da gumba,
   ki bi povedal samo »premalo podatkov«, sploh ni. */
const PRAG_ZANIMIVOSTI = 10

export function TurnirStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()
  const { smemUrejati } = useAvtentikacija()
  const jeTelefon = useTelefon()
  useNazaj('/turnirji', 'Turnirji')

  const turnir = useQuery({
    queryKey: ['turnir', idTurnirja],
    queryFn: () => turnirjiApi.najdi(idTurnirja),
  })
  /* Med turnirjem se seznam dogodkov osvežuje sam: statusi se med dnevom
     premikajo iz priprave v tek in v zaključek. */
  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja),
    refetchInterval: intervalOsvezevanja(turnir.data?.status),
  })

  const [odprtObrazec, nastaviOdprtObrazec] = useState(false)
  const [potrjujemZakljucek, nastaviPotrjujemZakljucek] = useState(false)
  const [pogled, nastaviPogled] = useState<PogledTurnirja>('DOGODKI')

  /* Zanimivosti se naložijo šele, ko gledalec odpre zavihek — poizvedba je
     nekaj skupinskih sestevkov čez vse tekme turnirja in vstopne strani ne
     sme obremeniti. Med turnirjem se osvežuje kot ostalo: to je razlog, da
     je zavihek viden že takrat. */
  const zanimivosti = useQuery({
    queryKey: ['turnir', idTurnirja, 'statistika'],
    queryFn: () => turnirjiApi.statistika(idTurnirja),
    enabled: pogled === 'ZANIMIVOSTI',
    refetchInterval: intervalOsvezevanja(turnir.data?.status),
  })

  const zakljucevanje = useMutation({
    mutationFn: () => turnirjiApi.zakljuci(idTurnirja),
    onSuccess: () => {
      odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja] })
      odjemalec.invalidateQueries({ queryKey: ['turnirji'] })
    },
  })

  if (turnir.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (turnir.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (turnir.error) return <NapakaPoizvedbe poizvedba={turnir} kaj="turnirja" />
  if (!turnir.data) return <p className="obvestilo">Tega turnirja ni (več).</p>
  const podatki = turnir.data!
  // organizator sme upravljati svoj (ali klubski) turnir, admin vse
  const smem = smemUrejati(podatki.idLastnik, podatki.idKlubLastnik)

  /* Gumb za zakljucek ponudimo sele, ko so vsi dogodki zakljuceni. */
  const vsiDogodkiZakljuceni =
    (dogodki.data?.length ?? 0) > 0 &&
    dogodki.data!.every((dogodek) => dogodek.status === 'ZAKLJUCEN')

  const seUreja = smem && podatki.status !== 'ZAKLJUCEN'
  const kraj = [podatki.kraj?.ime, podatki.dvorana].filter(Boolean).join(', ')

  /* Zavihek se ponudi že med turnirjem — gledalec pride pogledat, kaj se je
     zgodilo danes, ne šele čez teden dni. */
  const imaZanimivosti = podatki.odigranihTekem >= PRAG_ZANIMIVOSTI

  const zavihki = imaZanimivosti && (
    <div className="izbirnik turnir__zavihki">
      <button
        type="button"
        className={'izbirnik__gumb' + (pogled === 'DOGODKI' ? ' izbirnik__gumb--aktiven' : '')}
        aria-pressed={pogled === 'DOGODKI'}
        onClick={() => nastaviPogled('DOGODKI')}
      >
        Kategorije
      </button>
      <button
        type="button"
        className={'izbirnik__gumb' + (pogled === 'ZANIMIVOSTI' ? ' izbirnik__gumb--aktiven' : '')}
        aria-pressed={pogled === 'ZANIMIVOSTI'}
        onClick={() => nastaviPogled('ZANIMIVOSTI')}
      >
        Zanimivosti
      </button>
    </div>
  )

  const zanimivostiVsebina = (
    <>
      <NapakaPoizvedbe poizvedba={zanimivosti} kaj="zanimivosti" />
      {zanimivosti.isPending && <p className="obvestilo">Nalaganje …</p>}
      {zanimivosti.data && (
        <ZanimivostiTekmovanja podatki={zanimivosti.data} jeLiga={false} />
      )}
    </>
  )

  /* Okni (nov dogodek, potrditev zakljucka) sta na obeh sirinah isti. */
  const okna = (
    <>
      {potrjujemZakljucek && (
        <PotrditvenoOkno
          naslov="Zaključek turnirja"
          sporocilo="Zaključenega turnirja ni mogoče znova odpreti. Zaključim turnir?"
          besedaPotrditve="Zaključi turnir"
          onPotrdi={() => zakljucevanje.mutate()}
          onZapri={() => nastaviPotrjujemZakljucek(false)}
        />
      )}

      {odprtObrazec && (
        <NovDogodekOkno
          idTurnirja={idTurnirja}
          onZapri={() => nastaviOdprtObrazec(false)}
          onShranjeno={() =>
            odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja, 'dogodki'] })
          }
        />
      )}
    </>
  )

  if (jeTelefon) {
    return (
      <section className="stran-mobi--tesna">
        <GlavaDejanja>
          {seUreja && (
            <button
              type="button"
              className="glava-telefon__gumb"
              onClick={() => nastaviOdprtObrazec(true)}
            >
              + Dogodek
            </button>
          )}
          {seUreja && (
            <MeniDejanj naslov="Dejanja turnirja">
              {(zapri) => (
                <button
                  type="button"
                  role="menuitem"
                  className="uporabnik-meni__postavka"
                  disabled={zakljucevanje.isPending || !vsiDogodkiZakljuceni}
                  onClick={() => {
                    zapri()
                    nastaviPotrjujemZakljucek(true)
                  }}
                >
                  Zaključi turnir
                  {!vsiDogodkiZakljuceni && (
                    <span className="uporabnik-meni__pojasnilo">
                      Mogoče šele, ko so zaključeni vsi dogodki
                    </span>
                  )}
                </button>
              )}
            </MeniDejanj>
          )}
        </GlavaDejanja>

        {/* Zavihka gresta v lepljivo glavo: krmilo strani mora biti nad
            vsebino, ki jo krmili — pod naslovnim blokom in kolofonom bi ga
            gledalec našel šele po drsenju. */}
        {imaZanimivosti && (
          <GlavaZavihki>
            <div className="podnavigacija podnavigacija--telefon podnavigacija--enakomerna">
              <button
                type="button"
                className={
                  'izbirnik__gumb' + (pogled === 'DOGODKI' ? ' izbirnik__gumb--aktiven' : '')
                }
                aria-pressed={pogled === 'DOGODKI'}
                onClick={() => nastaviPogled('DOGODKI')}
              >
                Kategorije
              </button>
              <button
                type="button"
                className={
                  'izbirnik__gumb' + (pogled === 'ZANIMIVOSTI' ? ' izbirnik__gumb--aktiven' : '')
                }
                aria-pressed={pogled === 'ZANIMIVOSTI'}
                onClick={() => nastaviPogled('ZANIMIVOSTI')}
              >
                Zanimivosti
              </button>
            </div>
          </GlavaZavihki>
        )}

        <div>
          <div className="naslov-mobi__vrsta">
            <span className="naslov-mobi__nad">Turnir</span>
            <ZnackaVNaslovu status={podatki.status} />
          </div>
          <h1 className="naslov-mobi naslov-mobi--podstran">{podatki.ime}</h1>
          <p className="naslov-mobi__meta">
            {[oblikujObdobjeKratko(podatki.datumZacetka, podatki.datumKonca, true), kraj]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
          {podatki.opombe && <p className="opomba-bloka">{podatki.opombe}</p>}
        </div>

        {/* Kolofon 2 x 2: stiri vrstice oznaka <-> vrednost so na telefonu
            zasedle pol zaslona, mreza pove isto v polovici visine. */}
        <div className="kolofon kolofon--mreza">
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Prijavljenih</span>
            <span className="kolofon__vrednost">{podatki.prijavljenihSkupaj}</span>
          </div>
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Odigranih</span>
            <span className="kolofon__vrednost">
              {podatki.odigranihTekem} / {podatki.vsehTekem}
            </span>
          </div>
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Dogodki</span>
            <span className="kolofon__vrednost">{podatki.steviloDogodkov}</span>
          </div>
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Šteje v ELO</span>
            <span className="kolofon__vrednost">{podatki.stejeVElo ? 'da' : 'ne'}</span>
          </div>
        </div>

        <SporociloNapake napaka={zakljucevanje.error} />

        {pogled === 'ZANIMIVOSTI' && zanimivostiVsebina}

        {pogled === 'DOGODKI' && (
          <div>
            <div className="naslovna-mobi">
              <h2>Kategorije</h2>
              {dogodki.data && dogodki.data.length > 0 && (
                <span className="naslovna-mobi__stevec">{dogodki.data.length}</span>
              )}
            </div>

            <NapakaPoizvedbe poizvedba={dogodki} kaj="dogodkov" />
            {dogodki.data && dogodki.data.length === 0 && (
              <p className="obvestilo">
                Turnir še nima dogodkov. Dogodek je eno tekmovanje — npr. »Člani« ali
                »Članice do 21 let«. Igralci se prijavljajo na posamezen dogodek.
              </p>
            )}

            {dogodki.data && dogodki.data.length > 0 && (
              <div className="seznam-mobi seznam-mobi--odmik">
                {dogodki.data.map((dogodek) => (
                  <VrsticaKategorije key={dogodek.id} dogodek={dogodek} />
                ))}
              </div>
            )}
          </div>
        )}

        {okna}
      </section>
    )
  }

  return (
    <section>
      <Link to="/turnirji" className="povezava-nazaj">← Vsi turnirji</Link>

      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--podstran">
            <span className="naslov-strani__nad">Turnir</span>
            <span className="naslov-strani__glavni">{podatki.ime}</span>
          </h1>
          <p className="uvod">
            {[kraj, oblikujObdobje(podatki.datumZacetka, podatki.datumKonca)]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
          {podatki.opombe && <p className="opomba-bloka">{podatki.opombe}</p>}
        </div>
        <div>
          <div className="stran-glava__dejanja">
            <ZnackaStatusa status={podatki.status} />
            {smem && podatki.status !== 'ZAKLJUCEN' && (
              <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
                + Nov dogodek
              </button>
            )}
            {smem && podatki.status !== 'ZAKLJUCEN' && (
              <button
                className="gumb"
                disabled={zakljucevanje.isPending || !vsiDogodkiZakljuceni}
                onClick={() => nastaviPotrjujemZakljucek(true)}
              >
                Zaključi turnir
              </button>
            )}
            {smem && podatki.status !== 'ZAKLJUCEN' && !vsiDogodkiZakljuceni && (
              <span className="stran-glava__pogoj">
                Mogoče šele, ko so zaključeni vsi dogodki
              </span>
            )}
          </div>

          {/* Kolofon nosi vsote cez dogodke: koliko ljudi je na turnirju in
              koliko je odigranega. Stevila po statusih so ze v vrsticah
              dogodkov, zato jih tu ne ponavljamo. */}
          <div className="kolofon">
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Prijavljenih skupaj</span>
              <span className="kolofon__vrednost">{podatki.prijavljenihSkupaj}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Odigranih tekem</span>
              <span className="kolofon__vrednost">
                {podatki.odigranihTekem} / {podatki.vsehTekem}
              </span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Dogodki</span>
              <span className="kolofon__vrednost">{podatki.steviloDogodkov}</span>
            </div>
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">Šteje v ELO</span>
              <span className="kolofon__vrednost">{podatki.stejeVElo ? 'da' : 'ne'}</span>
            </div>
          </div>
        </div>
      </div>

      {zavihki}

      <SporociloNapake napaka={zakljucevanje.error} />

      {pogled === 'ZANIMIVOSTI' && zanimivostiVsebina}

      <div hidden={pogled !== 'DOGODKI'}>
        <div className="naslovna-vrstica">
          <h2>Dogodki</h2>
          {dogodki.data && dogodki.data.length > 0 && (
            <span className="sekcija__meta">
              {dogodki.data.length} {sklonTekmovanj(dogodki.data.length)}
            </span>
          )}
        </div>

        <NapakaPoizvedbe poizvedba={dogodki} kaj="dogodkov" />
        {dogodki.data && dogodki.data.length === 0 && (
          <p className="obvestilo">
            Turnir še nima dogodkov. Dogodek je eno tekmovanje — npr. »Člani« ali
            »Članice do 21 let«. Igralci se prijavljajo na posamezen dogodek.
          </p>
        )}

        {dogodki.data && dogodki.data.length > 0 && (
          <>
            <div className="seznam-glava seznam-glava--dogodki">
              <span>Dogodek</span>
              <span>Sistem</span>
              <span>Prijave</span>
              <span>Napredek</span>
              <span className="seznam-glava__sredinjeno">Status</span>
            </div>
            <div className="kartice">
              {dogodki.data.map((dogodek) => (
                <Link
                  to={`/dogodki/${dogodek.id}`}
                  className={`kartica kartica--dogodek kartica--${dogodek.status}`}
                  key={dogodek.id}
                >
                  <span className="kartica__glava">
                    <span className="kartica__ime">{dogodek.ime}</span>
                    <span className="kartica__podrobnost">
                      {/* Posamično je pravilo, dvojice izjema - zato izpišemo
                          samo disciplino, ki jo je treba opaziti. */}
                      {dogodek.disciplina === 'DVOJICE' && 'Dvojice · '}
                      {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
                      {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
                      {` · na ${dogodek.privzetoSteviloNizov} ${sklonNizov(dogodek.privzetoSteviloNizov)}`}
                    </span>
                  </span>
                  <span className="znacka znacka--sistem">
                    {OZNAKE_SISTEM_KRATKO[dogodek.sistemTekmovanja]}
                  </span>
                  <span className="vrstica__mono">{opisPrijav(dogodek)}</span>
                  <Napredek
                    odigranih={dogodek.odigranihTekem}
                    vseh={dogodek.vsehTekem}
                    koncan={dogodek.status === 'ZAKLJUCEN'}
                  />
                  <ZnackaStatusa status={dogodek.status} />
                </Link>
              ))}
            </div>
          </>
        )}

        {/* Ko dogodkov ni, isto pojasnilo stoji ze v praznem stanju zgoraj. */}
        {dogodki.data && dogodki.data.length > 0 && (
          <p className="namig">
            Dogodek je eno tekmovanje — npr. »Člani« ali »Članice do 21 let«. Igralci se
            prijavljajo na posamezen dogodek.
          </p>
        )}
      </div>

      {okna}
    </section>
  )
}

/* Vrstica kategorije na telefonu (~72 px): ime in ena mono vrstica
   "sistem · prijave · odigranost". Namig "Dogodek je eno tekmovanje ..." tu
   ne stoji - pri petih kategorijah bi zasedel prostor ene od njih; na
   namizju ostane. */
function VrsticaKategorije({ dogodek }: { dogodek: DogodekDto }) {
  const meta = [
    dogodek.disciplina === 'DVOJICE' ? 'Dvojice' : OZNAKE_SISTEM_MOBI[dogodek.sistemTekmovanja],
    opisPrijavKratko(dogodek),
    dogodek.vsehTekem > 0
      ? `${dogodek.odigranihTekem}/${dogodek.vsehTekem}`
      : 'žreb še ni izveden',
  ].join(' · ')

  return (
    <Link
      to={`/dogodki/${dogodek.id}`}
      className={`vrstica-mobi vrstica-mobi--brez-datuma vrstica-mobi--${dogodek.status}`}
    >
      <span className="vrstica-mobi__telo">
        <span className="vrstica-mobi__ime">{dogodek.ime}</span>
        <span className="vrstica-mobi__meta">{meta}</span>
        {dogodek.status === 'V_TEKU' && (
          <PalicaMobi odigranih={dogodek.odigranihTekem} vseh={dogodek.vsehTekem} />
        )}
      </span>
      <StatusMobi status={dogodek.status} />
    </Link>
  )
}

/* Stolpec "Prijave". Format TOP pove razrez (skupine so rangi po jakosti in
   povedo, koliko najboljših sploh igra), vsi drugi sistemi pa število ljudi. */
function opisPrijav(dogodek: DogodekDto): string {
  if (dogodek.steviloSkupin && dogodek.velikostSkupine) {
    return `${dogodek.steviloSkupin} ${sklonSkupin(dogodek.steviloSkupin)} po ${dogodek.velikostSkupine}`
  }
  /* Pri dvojicah je ena prijava PAR (ali igralec, ki soigralca še nima), zato
     bi »12 prijavljenih« pomenilo dvanajst ljudi, teh pa je do štiriindvajset.
     Nevtralna »prijava« je edina beseda, ki drži v obeh stanjih. */
  if (dogodek.disciplina === 'DVOJICE') {
    return `${dogodek.steviloPrijav} ${sklonPrijav(dogodek.steviloPrijav)}`
  }
  return `${dogodek.steviloPrijav} ${sklonPrijavljenih(dogodek.steviloPrijav)}`
}

/* Isto v mono vrstici telefona: "12 prijav" namesto "12 prijavljenih". */
function opisPrijavKratko(dogodek: DogodekDto): string {
  if (dogodek.steviloSkupin && dogodek.velikostSkupine) {
    return `${dogodek.steviloSkupin} ${sklonSkupin(dogodek.steviloSkupin)} po ${dogodek.velikostSkupine}`
  }
  return `${dogodek.steviloPrijav} ${sklonPrijav(dogodek.steviloPrijav)}`
}

function NovDogodekOkno({
  idTurnirja,
  onZapri,
  onShranjeno,
}: {
  idTurnirja: number
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [ime, nastaviIme] = useState('')
  const [disciplina, nastaviDisciplino] = useState<Disciplina>('POSAMICNO')
  const [spolKategorija, nastaviSpolKategorijo] = useState<SpolKategorija>('KDORKOLI')
  const [starostnaKategorija, nastaviStarostnoKategorijo] = useState('')
  const [steviloNizov, nastaviSteviloNizov] = useState('5')
  const [sistem, nastaviSistem] = useState<SistemTekmovanja>('IZLOCILNI')
  const [steviloSkupin, nastaviSteviloSkupin] = useState('3')
  const [velikostSkupine, nastaviVelikostSkupine] = useState('8')
  const [prijavnina, nastaviPrijavnino] = useState('')
  const [rokPrijave, nastaviRokPrijave] = useState('')

  /* Dvojice se igrajo samo po izločilnem sistemu (takojšnje izpadanje), zato
     izbirnik sistema odpade; »Mešano« pa je pravilo o sestavi para in ga
     posamično tekmovanje ne pozna. Obojega ne uveljavlja samo obrazec —
     enako zavrneta strežnik in shema. */
  const dvojice = disciplina === 'DVOJICE'
  const kategorije = kategorijeZaDisciplino(disciplina)

  /* Format TOP: skupine so rangi po jakosti, zato je treba njihovo
     število in velikost določiti že ob dogodku - zmnožek pove, koliko
     najboljših prijavljenih sploh igra. */
  const skupinskiSistem = !dvojice && sistem === 'SKUPINE'
  const mejaIzbora = Number(steviloSkupin) * Number(velikostSkupine)

  /* Ob preklopu na dvojice kategorija, ki je tam ni, obtiči izbrana in
     strežnik bi vnos zavrnil - zato jo vrnemo na odprto. */
  function zamenjajDisciplino(nova: Disciplina) {
    nastaviDisciplino(nova)
    if (!kategorijeZaDisciplino(nova).includes(spolKategorija)) {
      nastaviSpolKategorijo('KDORKOLI')
    }
  }

  const shranjevanje = useMutation({
    mutationFn: (vnos: DogodekVnos) => turnirjiApi.dodajDogodek(idTurnirja, vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      spolKategorija,
      starostnaKategorija: starostnaKategorija.trim() || null,
      privzetoSteviloNizov: Number(steviloNizov),
      prijavnina: prijavnina ? Number(prijavnina) : null,
      rokPrijave: rokPrijave || null,
      disciplina,
      sistemTekmovanja: dvojice ? 'IZLOCILNI' : sistem,
      steviloSkupin: skupinskiSistem ? Number(steviloSkupin) : null,
      velikostSkupine: skupinskiSistem ? Number(velikostSkupine) : null,
    })
  }

  return (
    <ModalnoOkno naslov="Nov dogodek" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Ime dogodka *</span>
          <input
            value={ime}
            onChange={(d) => nastaviIme(d.target.value)}
            placeholder="npr. Člani odprto"
            required
          />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Disciplina *</span>
            <select
              value={disciplina}
              onChange={(d) => zamenjajDisciplino(d.target.value as Disciplina)}
            >
              <option value="POSAMICNO">Posamično</option>
              <option value="DVOJICE">Dvojice</option>
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Kategorija *</span>
            <select
              value={spolKategorija}
              onChange={(d) => nastaviSpolKategorijo(d.target.value as SpolKategorija)}
            >
              {kategorije.map((vrednost) => (
                <option key={vrednost} value={vrednost}>
                  {OZNAKE_SPOL_KATEGORIJA[vrednost]}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Starostna kategorija</span>
            <input
              value={starostnaKategorija}
              onChange={(d) => nastaviStarostnoKategorijo(d.target.value)}
              placeholder="npr. do 15 let"
            />
          </label>
        </div>

        {dvojice ? (
          <p className="namig">
            Dvojice se igrajo po sistemu <strong>takojšnjega izpadanja</strong> —
            enaka mreža kot posamično. Igralce prijaviš posamično, pare pa
            sestaviš pred žrebom.
            {spolKategorija === 'MESANO' && ' Vsak par mora sestavljati en moški in ena ženska.'}
          </p>
        ) : (
          <label className="obrazec__polje">
            <span>Sistem tekmovanja *</span>
            <select value={sistem} onChange={(d) => nastaviSistem(d.target.value as SistemTekmovanja)}>
              {Object.entries(OZNAKE_SISTEM).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
        )}

        {skupinskiSistem && (
          <div className="obrazec__sklop">
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Število skupin *</span>
                <input
                  type="number"
                  min={1}
                  max={26}
                  value={steviloSkupin}
                  onChange={(d) => nastaviSteviloSkupin(d.target.value)}
                  required
                />
              </label>
              <label className="obrazec__polje">
                <span>Igralcev v skupini *</span>
                <input
                  type="number"
                  min={2}
                  max={24}
                  value={velikostSkupine}
                  onChange={(d) => nastaviVelikostSkupine(d.target.value)}
                  required
                />
              </label>
            </div>
            <p className="namig">
              {mejaIzbora > 0 ? (
                <>
                  Igralo bo <strong>najboljših {mejaIzbora}</strong> prijavljenih:
                  skupina A dobi mesta 1.–{velikostSkupine}, B naslednja in tako
                  naprej. Znotraj skupine igra vsak z vsakim, izločilnega dela ni.
                  Vrstni red urediš pred žrebom.
                </>
              ) : (
                'Vpiši število skupin in velikost skupine.'
              )}
            </p>
          </div>
        )}

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Igra se na … nizov *</span>
            <select value={steviloNizov} onChange={(d) => nastaviSteviloNizov(d.target.value)}>
              <option value="3">3 (na 2 dobljena niza)</option>
              <option value="5">5 (na 3 dobljene nize)</option>
              <option value="7">7 (na 4 dobljene nize)</option>
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Prijavnina (€)</span>
            <input
              type="number"
              min={0}
              step="0.5"
              value={prijavnina}
              onChange={(d) => nastaviPrijavnino(d.target.value)}
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Rok prijave</span>
          <input
            type="date"
            value={rokPrijave}
            onChange={(d) => nastaviRokPrijave(d.target.value)}
          />
        </label>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Dodaj dogodek
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
