import type { Clanek } from '../tipi'

/* Vodiči so članki poglavja »vodici«: koraki od začetka do konca, vsak
   korak s povezavo na podroben članek priročnika. */
export const VODICI: Clanek[] = [
  {
    id: 'vodic-turnir',
    naslov: 'Turnir od prijav do končne razvrstitve',
    poglavje: 'vodici',
    kratko: 'Deset korakov, ki turnir peljejo od praznega obrazca do zaključene razvrstitve.',
    kljucne: ['prvi turnir', 'začetek', 'postopek', 'kako začnem'],
    shemaNamiga: 'potek-turnirja',
    vsebina: [
      { tip: 'shema', shema: 'potek-turnirja', opis: 'Turnir v štirih delih: priprava, žreb, igra, zaključek.' },
      { tip: 'razdelek', id: 'priprava', naslov: 'Priprava (dnevi pred turnirjem)' },
      {
        tip: 'koraki',
        postavke: [
          'Ustvari turnir: **Turnirji → + Nov turnir**. Vpiši ime, datum, kraj in raven. [Več o obrazcu](turnir-nov)',
          'Dodaj kategorije: na strani turnirja **+ Dogodek**. Za vsako izberi sistem tekmovanja, nize in velikost skupin. [Kateri sistem izbrati](sistemi)',
          'Prijavi igralce: odpri kategorijo in jih dodaj z iskalnikom, s prilepljenim seznamom ali iz druge kategorije. [Prijave](prijave)',
          'Preveri jakostni vrstni red (nosilce). Igralci brez ratinga stojijo na vrhu — premakni jih na pravo mesto. [Nosilci](nosilci)',
        ],
      },
      { tip: 'razdelek', id: 'zreb', naslov: 'Žreb' },
      {
        tip: 'koraki',
        postavke: [
          'Klikni **Izvedi žreb**. Po žrebu prijav ni več mogoče spreminjati, žreb pa lahko ponoviš, dokler se ne začne prva tekma. [Žreb](zreb)',
          'Natisni žreb skupin za vrata dvorane in listke za prvo kolo. [Tisk na turnirski dan](vodic-dan-turnirja)',
        ],
      },
      { tip: 'razdelek', id: 'igra', naslov: 'Igra' },
      {
        tip: 'koraki',
        postavke: [
          'Vpisuj rezultate s klikom na tekmo — z listka, kot pride od mize. [Vnos rezultata](vnos-rezultata)',
          'Ko so skupine odigrane, izberi izločilni del: računalniški žreb ali ročna mreža. [Izločilni del](izlocilni-del)',
          'Napako na listku popravi s klikom na odigrano tekmo. [Popravek](popravek-rezultata)',
        ],
      },
      { tip: 'razdelek', id: 'konec', naslov: 'Zaključek' },
      {
        tip: 'koraki',
        postavke: [
          'Kategorija se zaključi sama ob zadnji tekmi. Natisni rezultate za oglasno desko. [Izpis rezultatov](izpis-rezultatov)',
          'Ko so zaključene vse kategorije, v dejanjih turnirja izberi **Zaključi turnir**. [Statusi](statusi#zakljucek)',
        ],
      },
    ],
    povezani: ['vodic-dan-turnirja', 'sistemi', 'zreb'],
  },
  {
    id: 'vodic-dan-turnirja',
    naslov: 'Turnirski dan v dvorani',
    poglavje: 'vodici',
    kratko: 'Kaj natisniti pred začetkom, kako teče vnos med igro in kaj narediš ob koncu dneva.',
    kljucne: ['dvorana', 'sodnik', 'tisk', 'listki', 'potek dneva'],
    shemaNamiga: 'listki-a4',
    vsebina: [
      { tip: 'razdelek', id: 'pred', naslov: 'Pred začetkom' },
      {
        tip: 'koraki',
        postavke: [
          'Preveri prijave — kdo je prišel, kdo ne. Odsotne odjavi **pred** žrebom (gumb × v seznamu prijavljenih).',
          'Izvedi žreb. Če ste žrebali na papir, ga prepiši z **Ročni vpis skupin** ali **Ročna mreža**. [Ročni žreb](rocni-zreb)',
          'Natisni **Žreb skupin za tiskanje** in ga obesi na vrata dvorane. [Žreb skupin za tisk](zreb-skupin-tisk)',
          'Natisni **Listke za tiskanje** za tekme, ki se igrajo prve. Natisnjene tekme si aplikacija zapomni, zato naslednjič izbereš samo nove. [Listki](listki)',
        ],
      },
      { tip: 'shema', shema: 'listki-a4', opis: 'Šest listkov na list A4, črtkane črte za rez.' },
      { tip: 'razdelek', id: 'med', naslov: 'Med igro' },
      {
        tip: 'koraki',
        postavke: [
          'Sodnik vrne izpolnjen listek. Klikni tekmo, vpiši nize (in če želiš točke po nizih) in shrani. [Vnos rezultata](vnos-rezultata)',
          'Gledalci vidijo rezultat takoj — mreža in lestvica se osvežujeta sami.',
          'Igralec, ki odstopi, naj ne izgine iz žreba: v zavihku **Udeleženci** ob njem klikni **Odstopil**. Neodigrane tekme dobijo nasprotniki brez boja. [Odstop](odstop)',
          'Ko so skupine odigrane, izberi izločilni del in natisni **Izločilni žreb za tiskanje**. [Izločilni del](izlocilni-del)',
        ],
      },
      { tip: 'razdelek', id: 'po', naslov: 'Ob koncu' },
      {
        tip: 'koraki',
        postavke: [
          'Natisni rezultate kategorije (razvrstitev, skupine, mreža). [Izpis rezultatov](izpis-rezultatov)',
          'Zaključi turnir. Popravke rezultatov lahko vneseš tudi kasneje. [Popravek](popravek-rezultata)',
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Vnos je narejen za eno roko: v točkah po nizih se okence ob vsaki dokončani številki premakne samo naprej. [Točke po nizih](tocke-po-nizih)',
      },
    ],
    povezani: ['listki', 'vnos-rezultata', 'izpis-rezultatov'],
  },
  {
    id: 'vodic-liga',
    naslov: 'Liga skozi sezono',
    poglavje: 'vodici',
    kratko: 'Od praznega obrazca lige do končnice: pravila, ekipe, razpored, termini, zapisniki.',
    kljucne: ['nova liga', 'sezona', 'postopek', 'liga od začetka'],
    shemaNamiga: 'potek-lige',
    vsebina: [
      { tip: 'shema', shema: 'potek-lige', opis: 'Liga gre iz priprave v tek z razporedom.' },
      { tip: 'razdelek', id: 'priprava', naslov: 'Priprava' },
      {
        tip: 'koraki',
        postavke: [
          'Ustvari ligo: **Lige → + Nova liga**. Izberi format srečanja, kdaj se srečanje konča, točkovanje in termin prvega kola. [Nova liga](liga-nova)',
          'Dodaj ekipe in vsaki kader igralcev. [Ekipe in kader](ekipe-kader)',
          'Če liga sodi v piramido, vpiši višjo ligo ter koliko ekip napreduje in izpade. [Prehodi](prehodi)',
        ],
      },
      { tip: 'razdelek', id: 'razpored', naslov: 'Razpored' },
      {
        tip: 'koraki',
        postavke: [
          'Klikni **Generiraj razpored** (žreb) ali **Vpiši razpored ročno**, če je razpored že narejen in razposlan. [Razpored](razpored)',
          'Preveri termine kol in popravi tista kola, ki so na drug dan (praznik, dvorana zasedena). [Termini kol](termini-kol)',
        ],
      },
      { tip: 'razdelek', id: 'sezona', naslov: 'Med sezono' },
      {
        tip: 'koraki',
        postavke: [
          'Po vsakem kolu vpiši zapisnike: postava, nato izidi tekem. [Zapisnik srečanja](zapisnik-srecanja)',
          'Prestavljeno srečanje premakni v **Termini → Srečanja po kolih**. [Prestavitev](prestavitve)',
          'Nov igralec v ekipi? Kader lahko dopolniš kadarkoli. [Kader](ekipe-kader#med-sezono)',
        ],
      },
      { tip: 'razdelek', id: 'konec', naslov: 'Konec rednega dela' },
      {
        tip: 'koraki',
        postavke: [
          'Če ima liga končnico, jo ustvari z **Ustvari končnico**, ko so vpisani vsi izidi. [Končnica](koncnica)',
          'Kvalifikacije s sosednjo ligo ustvari lastnik višje lige. [Kvalifikacije](kvalifikacije)',
        ],
      },
    ],
    povezani: ['vodic-obstojeca-liga', 'liga-nova', 'zapisnik-srecanja'],
  },
  {
    id: 'vodic-obstojeca-liga',
    naslov: 'Liga, ki že teče na papirju',
    poglavje: 'vodici',
    kratko:
      'Liga ima razpored že razposlan in nekaj kol odigranih? Vpiši razpored ročno, nato zapisnike odigranih kol po vrstnem redu.',
    kljucne: ['prenos', 'excel', 'ročni razpored', 'vnos za nazaj', 'sredi sezone'],
    shemaNamiga: 'rocni-razpored',
    vsebina: [
      {
        tip: 'odstavek',
        besedilo:
          'Žreb bi naredil nov razpored, igralci pa imajo v rokah starega. Zato liga, ki jo prenašaš sredi sezone, dobi razpored z ročnim vpisom.',
      },
      {
        tip: 'koraki',
        postavke: [
          'Ustvari ligo z enakimi pravili, kot jih ima liga zdaj, in dodaj ekipe s kadri. [Nova liga](liga-nova)',
          'Klikni **Vpiši razpored ročno**. Aplikacija pripravi prazno mrežo pravih mer; **Predlagaj razpored** jo zapolni z naključnim žrebom, ki ga nato popraviš po papirju. [Ročni razpored](razpored#rocno)',
          'Nastavi termine kol po papirnatem razporedu. [Termini kol](termini-kol)',
          'Vpiši zapisnike odigranih kol **po vrstnem redu kol** — najprej 1. kolo, nato 2. Rating se računa po vrstnem redu tekem.',
        ],
      },
      { tip: 'shema', shema: 'rocni-razpored', opis: 'Ročni razpored: kolo za kolom vpišeš pare, kot so na papirju.' },
      {
        tip: 'pazi',
        besedilo:
          'Če zapisnik poznega kola vpišeš pred zgodnejšim, se srečanje ob koncu vnosa samo preračuna za nazaj. Vseeno je najvarneje vpisovati po vrsti.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Gledalci ob razporedu vidijo droben napis »Ročni žreb«. Tako vedo, da je to isti razpored, ki so ga dobili po pošti.',
      },
    ],
    povezani: ['razpored', 'zapisnik-srecanja', 'vodic-liga'],
  },
]
