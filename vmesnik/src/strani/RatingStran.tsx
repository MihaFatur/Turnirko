/* Vzdrževanje Turnirko ratinga (samo administrator): ponovni preračun in
   uveljavljanje zapadlih odbitkov za neaktivnost.

   Zakaj je ta stran sploh potrebna. Obe opravili sta doslej obstajali samo kot
   končni točki (`POST /rating/preracun`, `POST /rating/neaktivnost`) — torej za
   nikogar, ki ne piše curl ukazov. Preračun pa ni izjemen poseg: potreben je
   vsakič, ko se zaporedje VNOSOV razide s časom TEKEM, kar se pri vpisovanju
   kola za nazaj zgodi samo od sebe in se nikjer ne vidi.

   Zato stran ne nosi samo gumba, ampak tudi razlago: kaj se pokvari, kaj
   preračun naredi in kdaj ga je treba pognati. Brez tega je gumb, ki rating
   vsem igralcem izračuna znova, bolj nevaren kot koristen. */
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ratingApi } from '../api/zahteve'
import type { NeaktivnostPorociloDto, PreracunPorociloDto } from '../api/tipi'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { oblikujDatum, oblikujStevilo, sklonTekem, sklonTock } from '../pomozno/oblikovanje'

/* Meja preračuna iz poročila. Preračun od začetka jo vrne kot 1. 1. 1900
   (tam veljajo tekme brez znanega datuma), kar je resnica o zaledju in ne
   podatek za bralca — zato jo povemo z besedo. */
function opisMeje(od: string): string {
  const datum = od.split('T')[0]
  return datum.startsWith('1900-') ? 'od začetka' : `od ${oblikujDatum(datum)}`
}

/* Mestnik: »pri 1 igralcu«, »pri 4 igralcih«. Splošnega sklanjala zanj v
   oblikovanje.ts ni, ker ga potrebuje samo ta stavek; dvojina ima isto obliko
   kot množina, zato sta obliki dve. */
function priIgralcih(n: number): string {
  return n % 100 !== 11 && n % 10 === 1 ? `${n} igralcu` : `${n} igralcih`
}

export function RatingStran() {
  const odjemalec = useQueryClient()
  const [od, nastaviOd] = useState('')
  const [potrjujem, nastaviPotrjujem] = useState(false)
  const [izidPreracuna, nastaviIzidPreracuna] = useState<PreracunPorociloDto | null>(null)
  const [izidOdbitkov, nastaviIzidOdbitkov] = useState<NeaktivnostPorociloDto | null>(null)

  /* Rating stoji na lestvici, v profilih, na karticah tekem in v napovedih —
     po preračunu ni predpomnjenega odgovora, ki bi še držal. */
  const osveziVse = () => odjemalec.invalidateQueries()

  const preracun = useMutation({
    mutationFn: () => ratingApi.preracunaj(od || null),
    onSuccess: (porocilo) => {
      nastaviIzidPreracuna(porocilo)
      osveziVse()
    },
  })

  const odbitki = useMutation({
    mutationFn: () => ratingApi.neaktivnost(),
    onSuccess: (porocilo) => {
      nastaviIzidOdbitkov(porocilo)
      osveziVse()
    },
  })

  const tece = preracun.isPending || odbitki.isPending

  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Administracija</span>
            <span className="naslov-strani__glavni">Rating</span>
          </h1>
          <p className="uvod">
            Turnirko rating je izpeljanka: edini vir resnice so izidi tekem, v dnevniku pa ima
            vsaka sprememba svojo vrstico. Zato ga je mogoče kadar koli sestaviti znova — in
            včasih je to treba.
          </p>
        </div>
      </div>

      <div className="plosca">
        <div className="naslovna-vrstica">
          <h2>Ponovni preračun</h2>
        </div>

        <p className="namig">
          Obračun teče v vrstnem redu <strong>vnosa</strong>, v dnevnik pa se zapiše datum{' '}
          <strong>tekme</strong>. Dokler vpisuješ kolo za kolom, je to isto. Ko vpišeš rezultat
          za nazaj — recimo novembrsko kolo pred oktobrskim — se razide troje:
        </p>
        <ul className="seznam-preprost">
          <li>
            Oktobrska tekma se računa proti <strong>napačnima ratingoma</strong>: proti
            številkama, ki že vsebujeta november.
          </li>
          <li>
            Na grafu napredka stoji na oktobrskem datumu <strong>vrednost, ki takrat ni
            veljala</strong> — nastala je pozneje.
          </li>
          <li>
            Zamakneta se <strong>vrnitev po odsotnosti</strong> (šteje od zadnjega vnesenega
            termina, ta pa gre samo naprej) in <strong>prvi dan novinca</strong> (postavi ga
            prvi vnos, ne najzgodnejša tekma).
          </li>
        </ul>
        <p className="namig">
          Preračun to popravi: pobriše obračune od izbranega dne, iz dnevnika obnovi stanje na
          tisti dan in vse skupaj odigra znova v pravem časovnem zaporedju. Postavitveni
          ratingi in zunanje uvrstitve ostanejo — nista posledica izida, ampak odločitev
          človeka.
        </p>

        <div className="obrazec__vrstica rating-vzdrzevanje__vrstica">
          <label className="obrazec__polje">
            <span>Od dneva</span>
            <input
              type="date"
              value={od}
              disabled={tece}
              onChange={(dogodek) => nastaviOd(dogodek.target.value)}
            />
          </label>
          <button
            type="button"
            className="gumb gumb--glavni"
            disabled={tece}
            onClick={() => nastaviPotrjujem(true)}
          >
            {preracun.isPending ? 'Računam …' : 'Preračunaj rating'}
          </button>
        </div>
        <p className="namig">
          Prazno polje pomeni <strong>od začetka</strong> — vse tekme v bazi. Sicer vpiši dan
          tekme, od katere naprej je zaporedje sporno; po navadi je to dan kola, ki si ga
          vpisal za nazaj. Zgodnejši dan ni napaka, pomeni le več dela.
        </p>

        <p className="opomba-bloka">
          Preračun teče sinhrono: stran naj ostane odprta, dokler ne vrne izida. Celotna
          uvožena zgodovina (~92 tisoč tekem) traja okoli tri minute in pol, en dan tekoče
          sezone pa nekaj sekund. Med računanjem so lestvice lahko vmesne.
        </p>

        <SporociloNapake napaka={preracun.error} />
        {izidPreracuna && (
          <div className="izid-opravila">
            <strong>
              Preračunano {opisMeje(izidPreracuna.od)}:{' '}
              {oblikujStevilo(izidPreracuna.obracunanihTekem)}{' '}
              {sklonTekem(izidPreracuna.obracunanihTekem)}.
            </strong>
            {/* Oznaka in vrednost namesto stavka: »4 igralci« bi v tem stavku
                terjalo tožilnik, pri 1 in 2 pa še svojo obliko. */}
            <span className="izid-opravila__pod">
              Igralcev z ratingom: {oblikujStevilo(izidPreracuna.igralcevZRatingom)}
            </span>
          </div>
        )}
      </div>

      <div className="plosca">
        <div className="naslovna-vrstica">
          <h2>Kdaj ga poženeš</h2>
        </div>
        <ul className="seznam-preprost">
          <li>
            <strong>Rezultat, vpisan za nazaj</strong> — poženi od dneva tiste tekme. To je
            edini primer, ki ga aplikacija ne opazi sama.
          </li>
          <li>
            <strong>Popravek že shranjene tekme</strong> — ni treba, popravek preračuna sam od
            dneva tekme.
          </li>
          <li>
            <strong>Uvoz NTZS</strong> — ni treba, uvoz preračuna sam od dneva tekmovanja.
          </li>
          <li>
            <strong>Sprememba parametrov formule</strong> — od začetka (pusti polje prazno),
            sicer se stare in nove vrednosti pomešajo.
          </li>
          <li>
            <strong>Sum, da je lestvica napačna</strong> — od začetka. Če se številke ne
            spremenijo, je dnevnik skladen in vzrok je drugje.
          </li>
        </ul>
      </div>

      <div className="plosca">
        <div className="naslovna-vrstica">
          <h2>Odbitki za neaktivnost</h2>
        </div>
        <p className="namig">
          Kdor dlje časa ne igra, mu rating začne padati — sicer bi na javni lestvici držal
          mesto s številko izpred treh let. Odbitki zapadejo po datumu, uveljavi pa jih nočno
          opravilo ob 3.15. Ta gumb naredi isto takoj; uporabi ga, kadar ne moreš čakati do
          jutra, na primer pred objavo lestvice. Preračun jih uveljavi tudi sam, zato ga po
          njem ni treba klikniti posebej.
        </p>
        <div className="obrazec__gumbi rating-vzdrzevanje__gumbi">
          <button
            type="button"
            className="gumb"
            disabled={tece}
            onClick={() => odbitki.mutate()}
          >
            {odbitki.isPending ? 'Uveljavljam …' : 'Uveljavi zapadle odbitke'}
          </button>
        </div>
        <SporociloNapake napaka={odbitki.error} />
        {izidOdbitkov && (
          <div className="izid-opravila">
            <strong>
              {izidOdbitkov.zapisanihOdbitkov === 0
                ? 'Nič ni zapadlo — vsi ratingi so tekoči.'
                : `Skupaj −${oblikujStevilo(izidOdbitkov.odbitihTock)} ${sklonTock(
                    izidOdbitkov.odbitihTock,
                  )} pri ${priIgralcih(izidOdbitkov.prizadetihIgralcev)}.`}
            </strong>
            {izidOdbitkov.zapisanihOdbitkov > 0 && (
              <span className="izid-opravila__pod">
                Zapisov v dnevniku: {oblikujStevilo(izidOdbitkov.zapisanihOdbitkov)}
              </span>
            )}
          </div>
        )}
      </div>

      {potrjujem && (
        <PotrditvenoOkno
          naslov="Ponovni preračun ratinga"
          sporocilo={
            od
              ? `Rating vseh igralcev se bo od ${oblikujDatum(od)} naprej izračunal znova.`
                + ' Dokler preračun teče, so lestvice vmesne; stran naj ostane odprta.'
              : 'Rating vseh igralcev se bo izračunal znova od prve tekme v bazi.'
                + ' Pri celotni uvoženi zgodovini to traja okoli tri minute in pol;'
                + ' stran naj ostane odprta.'
          }
          besedaPotrditve="Preračunaj"
          onPotrdi={() => {
            nastaviIzidPreracuna(null)
            preracun.mutate()
          }}
          onZapri={() => nastaviPotrjujem(false)}
        />
      )}
    </section>
  )
}
