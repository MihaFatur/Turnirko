/* Stran enega turnirja: osnovni podatki, seznam dogodkov (tekmovanj)
   in dodajanje novega dogodka. Turnir se lahko zakljuci sele, ko so
   zakljuceni vsi njegovi dogodki - to pravilo preverja zaledje.

   Na telefonu je glava kompaktna (naslov 32 px namesto 80), stiri vrstice
   kolofona so mreza 2 x 2, dejanja urejevalca pa so v lepljivi glavi: gost in
   igralec (uporabnik st. 1) pridemo po kategorije, ne po gumbe. Pogoj za
   zakljucek turnirja je tam onemogocena postavka s pojasnilom in ne posebna
   vrstica v verzalkah pred vsebino. */
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { DogodekDto } from '../api/tipi'
import {
  OZNAKE_RAVEN,
  OZNAKE_SISTEM_KRATKO,
  OZNAKE_SISTEM_MOBI,
  OZNAKE_SPOL_KATEGORIJA,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GlavaDejanja, GlavaZavihki, useNazaj } from '../komponente/GlavaTelefona'
import { DogodekObrazecOkno } from '../komponente/DogodekObrazecOkno'
import { MeniDejanj } from '../komponente/MeniDejanj'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { Napredek, PalicaMobi } from '../komponente/Napredek'
import { ZanimivostiTekmovanja } from '../komponente/ZanimivostiTekmovanja'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { StatusMobi, ZnackaStatusa, ZnackaVNaslovu } from '../komponente/Znacka'
import {
  oblikujObdobje,
  oblikujObdobjeKratko,
  sklonEkip,
  sklonNizov,
  sklonPrijav,
  sklonPrijavljenih,
  sklonSkupin,
  sklonTekmovanj,
} from '../pomozno/oblikovanje'
import { intervalOsvezevanja } from '../pomozno/osvezevanje'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'
import { PRAG_ZANIMIVOSTI } from '../pomozno/zanimivosti'
import { imaMrezoZaTisk, jeKandidatZaTiskMreze } from '../pomozno/zrebMreze'
import { imaZrebanoSkupine } from '../pomozno/zrebSkupin'

/* Zavihka strani turnirja. »Zanimivosti« so čez VSE dogodke skupaj — na ravni
   ene kategorije je tekem pogosto premalo, da bi kaj povedale. */
type PogledTurnirja = 'DOGODKI' | 'ZANIMIVOSTI'

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

  /* Gumb za izločilni žreb: mreža pri skupinah nastane šele po njih, zato jo
     vprašamo za dogodke, ki bi jo lahko imeli. Odgovor je pod ključem strani
     dogodka (isti zapis), pri čistem izločilnem sistemu mreža obstaja od žreba.
     Naloži se samo za urejevalca - gost gumba ne vidi. */
  const smemUrejatiTurnir =
    !!turnir.data && smemUrejati(turnir.data.idLastnik, turnir.data.idKlubLastnik)
    && turnir.data.vir == null && turnir.data.status !== 'ZAKLJUCEN'
  const kandidatiMreze = smemUrejatiTurnir
    ? (dogodki.data ?? []).filter(
        (d) => jeKandidatZaTiskMreze(d) && d.sistemTekmovanja !== 'IZLOCILNI',
      )
    : []
  const mrezeKandidatov = useQueries({
    queries: kandidatiMreze.map((dogodek) => ({
      queryKey: ['dogodek', dogodek.id],
      queryFn: () => dogodkiApi.mreza(dogodek.id),
    })),
  })

  useNaslovStrani(turnir.data?.ime)

  if (turnir.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (turnir.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (turnir.error) return <NapakaPoizvedbe poizvedba={turnir} kaj="turnirja" />
  if (!turnir.data) return <p className="obvestilo">Tega turnirja ni (več).</p>
  const podatki = turnir.data!
  /* Organizator sme upravljati svoj (ali klubski) turnir, admin vse - razen
     uvoženega: vir resnice je zveza, strežnik mutacijo zavrne. */
  const smem = smemUrejati(podatki.idLastnik, podatki.idKlubLastnik) && podatki.vir == null

  /* Gumb za zakljucek ponudimo sele, ko so vsi dogodki zakljuceni. */
  const vsiDogodkiZakljuceni =
    (dogodki.data?.length ?? 0) > 0 &&
    dogodki.data!.every((dogodek) => dogodek.status === 'ZAKLJUCEN')

  const seUreja = smem && podatki.status !== 'ZAKLJUCEN'
  /* Žreb skupin za vrata dvorane: ponujen, ko ima vsaj ena kategorija
     izžrebane skupine in teče. */
  const zaTiskSkupin = seUreja && (dogodki.data ?? []).some(imaZrebanoSkupine)
  /* Žreb izločilnega dela: čisti izločilni dogodek v teku mrežo že ima, pri
     skupinah jo pokaže šele naložena mreža. */
  const zaTiskMreze =
    seUreja
    && ((dogodki.data ?? []).some(
      (d) => jeKandidatZaTiskMreze(d) && d.sistemTekmovanja === 'IZLOCILNI',
    )
      || mrezeKandidatov.some((m) => m.data !== undefined && imaMrezoZaTisk(m.data)))
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
        <DogodekObrazecOkno
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
                <>
                  {zaTiskSkupin && (
                    <Link
                      to={`/turnirji/${idTurnirja}/zreb-skupin`}
                      role="menuitem"
                      className="uporabnik-meni__postavka"
                      onClick={zapri}
                    >
                      Žreb skupin za tiskanje
                    </Link>
                  )}
                  {zaTiskMreze && (
                    <Link
                      to={`/turnirji/${idTurnirja}/zreb-mreze`}
                      role="menuitem"
                      className="uporabnik-meni__postavka"
                      onClick={zapri}
                    >
                      Izločilni žreb za tiskanje
                    </Link>
                  )}
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
                </>
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
            <ZnackaVNaslovu status={podatki.status} />
          </div>
          <h1 className="naslov-mobi naslov-mobi--podstran">{podatki.ime}</h1>
          <p className="naslov-mobi__meta">
            {[oblikujObdobjeKratko(podatki.datumZacetka, podatki.datumKonca, true), kraj]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
          {podatki.opombe && !podatki.vir && <p className="opomba-bloka">{podatki.opombe}</p>}
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
            <span className="kolofon__oznaka">Raven</span>
            <span className="kolofon__vrednost">{OZNAKE_RAVEN[podatki.raven]}</span>
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
              <p className="obvestilo">Turnir še nima dogodkov.</p>
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
            <span className="naslov-strani__glavni">{podatki.ime}</span>
          </h1>
          <p className="uvod">
            {[kraj, oblikujObdobje(podatki.datumZacetka, podatki.datumKonca)]
              .filter(Boolean)
              .join(' · ') || 'kraj in datum še nista določena'}
          </p>
          {podatki.opombe && !podatki.vir && <p className="opomba-bloka">{podatki.opombe}</p>}
        </div>
        <div>
          <div className="stran-glava__dejanja">
            <ZnackaStatusa status={podatki.status} />
            {smem && podatki.status !== 'ZAKLJUCEN' && (
              <button className="gumb gumb--glavni" onClick={() => nastaviOdprtObrazec(true)}>
                + Nov dogodek
              </button>
            )}
            {zaTiskSkupin && (
              <Link to={`/turnirji/${idTurnirja}/zreb-skupin`} className="gumb">
                Žreb skupin za tiskanje
              </Link>
            )}
            {zaTiskMreze && (
              <Link to={`/turnirji/${idTurnirja}/zreb-mreze`} className="gumb">
                Izločilni žreb za tiskanje
              </Link>
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
              <span className="kolofon__oznaka">Raven</span>
              <span className="kolofon__vrednost">{OZNAKE_RAVEN[podatki.raven]}</span>
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
          <p className="obvestilo">Turnir še nima dogodkov.</p>
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
                      {dogodek.disciplina === 'EKIPNO' && 'Ekipno · '}
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
      </div>

      {okna}
    </section>
  )
}

/* Vrstica kategorije na telefonu (~72 px): ime in ena mono vrstica
   "sistem · prijave · odigranost". */
function VrsticaKategorije({ dogodek }: { dogodek: DogodekDto }) {
  const meta = [
    dogodek.disciplina === 'DVOJICE'
      ? 'Dvojice'
      : dogodek.disciplina === 'EKIPNO'
        ? `Ekipno · ${OZNAKE_SISTEM_MOBI[dogodek.sistemTekmovanja]}`
        : OZNAKE_SISTEM_MOBI[dogodek.sistemTekmovanja],
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
  if (dogodek.disciplina === 'EKIPNO') {
    return `${dogodek.steviloPrijav} ${sklonEkip(dogodek.steviloPrijav)}`
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
