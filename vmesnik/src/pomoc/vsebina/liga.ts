import type { Clanek } from '../tipi'

export const LIGA: Clanek[] = [
  {
    id: 'liga-nova',
    naslov: 'Nova liga in njena pravila',
    poglavje: 'liga',
    kratko:
      'Liga nosi pravila srečanja: format (vrstni red tekem), kdaj je srečanje končano, točkovanje in termine kol. Pravila lahko urejaš, dokler razpored ni generiran.',
    kljucne: ['nova liga', 'ustvari ligo', 'pravila', 'točkovanje', 'sezona', 'dvokrožno', 'uredi pravila'],
    shemaNamiga: 'potek-lige',
    vsebina: [
      { tip: 'kje', pot: ['Lige', '+ Nova liga'] },
      { tip: 'shema', shema: 'potek-lige', opis: 'Pravila so odprta do razporeda; termini, kader in imena ekip ostanejo odprti vso sezono.' },
      {
        tip: 'tabela',
        glava: ['Polje', 'Kaj pomeni'],
        vrstice: [
          ['Ime lige, Sezona', '»1. liga Savinja«, »2026/27«. Sezona loči lige v arhivu.'],
          ['Kategorija', 'Moški, Ženske ali Mešano (igrajo oboji).'],
          ['Raven tekmovanja', 'Koliko liga šteje v rating. [Raven](turnir-nov#raven)'],
          ['Format srečanja', 'Koliko igralcev in v kakšnem vrstnem redu igrajo. [Formati](format-srecanja)'],
          ['Konec srečanja', 'Ali se srečanje konča, ko ena ekipa doseže prag zmag, ali se odigrajo vse tekme.'],
          ['Nizi (prvi do)', 'Do koliko dobljenih nizov se igra posamična tekma.'],
          ['Točkovanje', 'Točke za zmago, neodločeno in poraz srečanja na lestvici.'],
          ['Dvokrožno', 'Vsak z vsakim doma in v gosteh.'],
          ['Enakomerna razvrstitev', 'Žreb po parih ekip — [glej Razpored](razpored#enakomerna).'],
          ['Termini', 'Kdaj je prvo kolo, razmik med koli in ure krogov. [Termini](termini-kol)'],
          ['Končnica', 'Ali po rednem delu igrajo končnico in do koliko zmag. [Končnica](koncnica)'],
          ['Predloga zapisnika', 'Oblika natisnjenega ekipnega zapisnika NTZS (1. ali 2./3. SNTL).'],
          ['Prepovej dvojno registracijo', 'Igralec je lahko v kadru ene same ekipe te lige.'],
          ['Piramida in prehodi', 'Višja liga ter koliko ekip napreduje in izpade. [Prehodi](prehodi)'],
        ],
      },
      {
        tip: 'razdelek',
        id: 'konec-srecanja',
        naslov: 'Konec srečanja: prag zmag ali vse tekme',
        kratko:
          'Pri pragu (npr. 5) je srečanje končano, ko ena ekipa doseže 5 zmag; preostale tekme se ne igrajo. »Odigrajo se vse tekme« pomeni, da se igra vseh 10 ne glede na izid.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Pri pragu je srečanje po odločilni tekmi končano, preostale tekme v zapisniku ostanejo neodigrane. Neodločen izid (npr. 5 : 5, ko se igra vseh deset tekem) dovoliš s kljukico **Neodločen izid srečanja je mogoč**; na lestvici prinese točke **Neodločeno**.',
      },
      {
        tip: 'razdelek',
        id: 'urejanje',
        naslov: 'Urejanje pravil',
        kratko:
          'Pravila lige urediš z »Uredi pravila«, dokler razpored ni generiran. Termini, prehodi, kader in imena ekip ostanejo odprti vso sezono.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Pravila so zaklenjena, ker je razpored narejen po njih (npr. dvokrožno, ure kola). Če moraš pravilo res spremeniti, najprej **Razveljavi razpored** (mogoče, dokler ni odigrano nobeno srečanje).',
      },
    ],
    povezani: ['format-srecanja', 'ekipe-kader', 'razpored', 'vodic-liga'],
  },
  {
    id: 'format-srecanja',
    naslov: 'Formati ekipnega srečanja',
    poglavje: 'liga',
    kratko:
      'Format pove, koliko igralcev nastopi (A, B, C proti X, Y, Z), ali so dvojice in v kakšnem vrstnem redu se igrajo tekme. Po njem nastane zapisnik vsakega srečanja.',
    kljucne: ['format', 'SNTL', 'Corbillon', 'Savinja', 'DŠI', 'olimpijski', 'vrstni red tekem', 'A-X'],
    shemaNamiga: 'format-sntl',
    vsebina: [
      { tip: 'shema', shema: 'format-sntl', opis: 'Format SNTL: dvojice, nato vsak igralec z vsakim nasprotnikom.' },
      {
        tip: 'tabela',
        glava: ['Format', 'Igralcev', 'Vrstni red tekem'],
        vrstice: [
          ['SNTL', '3 + dvojice', 'dvojice · A-X · B-Y · C-Z · B-X · A-Z · C-Y · B-Z · C-X · A-Y'],
          ['SNTL 1. liga', '3 + dvojice', 'dvojice + 6 posamičnih'],
          ['SNTL brez dvojic', '3', '9 posamičnih'],
          ['Olimpijski', '3', 'A-X · B-Y · C-Z · A-Y · B-X'],
          ['Corbillon', '2 + dvojice', 'A-X · B-Y · dvojice · A-Y · B-X'],
          ['Savinja liga', '2 + dvojice', 'dvojice · A-X · B-Y · A-Y · B-X'],
          ['DŠI', '2 + dvojice', 'A-X · B-Y · dvojice (samo ob 1 : 1) — prvi do dveh zmag'],
          ['Ekipni DP, Pokal NTZS', 'po pravilih NTZS', 'dvojice + 4 oz. 5 posamičnih'],
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Domača ekipa igra z mesti A, B, C, gostujoča z X, Y, Z. V postavi vsakemu mestu dodeliš igralca iz kadra; tekme »A-X« nato dobijo prava imena. Natančen vrstni red vidiš v obrazcu lige pod izbiro formata.',
      },
    ],
    povezani: ['zapisnik-srecanja', 'liga-nova'],
  },
  {
    id: 'ekipe-kader',
    naslov: 'Ekipe in kader',
    poglavje: 'liga',
    kratko:
      'Ekipa je klubska (iz registra klubov) ali prosta (samo za to ligo, npr. podjetje). Vsaka ima kader igralcev. Ekipe dodajaš v pripravi, kader dopolnjuješ tudi med sezono.',
    kljucne: ['ekipa', 'kader', 'prosta ekipa', 'klub', 'dodaj ekipo', 'preimenuj', 'rezerva'],
    shemaNamiga: 'ekipe',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Ekipe', '+ Dodaj ekipo'] },
      { tip: 'shema', shema: 'ekipe', opis: 'Klubska in prosta ekipa; obe imata kader iz registra igralcev.' },
      {
        tip: 'koraki',
        postavke: [
          'Izberi **Iz registra** (klubska ekipa) ali **Prosta ekipa**.',
          'Pri klubski izberi klub; ime je neobvezno (sicer »Klub 1«, »Klub 2«). Prosta ekipa mora imeti ime.',
          'Klikni **+ Dodaj ekipo**.',
          'Ob ekipi klikni **Kader** in dodaj igralce.',
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Prosta ekipa nastopa samo v tej ligi in ne pride v register klubov. Igralci v njenem kadru so isti igralci registra, zato rating in profili delujejo enako.',
      },
      {
        tip: 'razdelek',
        id: 'med-sezono',
        naslov: 'Kader med sezono',
        kratko:
          'Igralca dodaš v kader kadarkoli (okno »Ekipe in kader«). Odstraniti ne moreš igralca, ki je za ekipo že igral ali je postavljen v tekmo, ki čaka.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Ko liga teče, ekipe ne dodajaš in ne odstranjuješ več (razpored je razpored ekip). Kader pa urediš v meniju lige **Ekipe in kader**: ekipo razpreš in dodaš igralca.',
      },
      {
        tip: 'razdelek',
        id: 'ime',
        naslov: 'Preimenovanje ekipe',
        kratko: 'Ime ekipe lahko popraviš v vsakem stanju lige, tudi po koncu. Ime mora biti v ligi enolično.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'V pripravi z **Preimenuj** ob ekipi, med sezono v oknu **Ekipe in kader**. Razpored, lestvica in zapisniki ekipo prepoznajo po zapisu, ne po imenu, zato se nič ne preračunava. Prazno ime klubske ekipe jo vrne na »Klub N«.',
      },
    ],
    povezani: ['razpored', 'liga-nova'],
  },
  {
    id: 'razpored',
    naslov: 'Razpored lige',
    poglavje: 'liga',
    kratko:
      'Razpored nastane z žrebom (»Generiraj razpored«) ali z ročnim vpisom, kadar je že narejen in razposlan. Z njim liga začne teči. Razveljaviš ga lahko, dokler ni odigrano nobeno srečanje.',
    kljucne: ['razpored', 'žreb lige', 'generiraj', 'ročni razpored', 'kola', 'razveljavi razpored', 'pari'],
    shemaNamiga: 'krozni-razpored',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Generiraj razpored / Vpiši razpored ročno'] },
      {
        tip: 'razdelek',
        id: 'zreb',
        naslov: 'Generiraj razpored',
        kratko: 'Žreb sestavi pare vsak z vsakim (pri dvokrožni ligi doma in v gosteh) in vsem srečanjem napiše termine kol.',
      },
      { tip: 'shema', shema: 'krozni-razpored', opis: 'Krožni sistem: v vsakem kolu igra vsaka ekipa enkrat.' },
      {
        tip: 'odstavek',
        besedilo:
          'Pri lihem številu ekip ima v vsakem kolu ena ekipa prosto. Domače pravice se izmenjujejo.',
      },
      {
        tip: 'razdelek',
        id: 'rocno',
        naslov: 'Vpiši razpored ročno',
        kratko:
          'Za ligo, ki ima razpored že narejen na papirju. Prazna mreža ima prave mere; »Predlagaj razpored« jo napolni z žrebom, ki ga popraviš.',
        shemaNamiga: 'rocni-razpored',
      },
      { tip: 'shema', shema: 'rocni-razpored', opis: 'Vpis po kolih; izbira ekipe, ki v kolu že igra, ekipi zamenja.' },
      {
        tip: 'seznam',
        postavke: [
          'Izbira ekipe, ki v kolu že igra, ekipi **zamenja** — tako popraviš polno kolo brez praznjenja.',
          'Ekipa ne sme igrati dveh srečanj hkrati in ne sama s sabo; to okno zavrne.',
          'Nepopoln razpored (par, ki se ne sreča) okno opozori, a ga shrani — nekatere lige igrajo tako.',
          'Shraniš z **Shrani razpored**. Gledalci ob razporedu vidijo oznako »Ročni žreb«.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'enakomerna',
        naslov: 'Enakomerna razvrstitev (žreb po parih)',
        kratko:
          'Ekipe razvrstiš po moči. Prva iz zgornje polovice tvori par s prvo iz spodnje … Par v vsakem krogu igra proti istemu nasprotnemu paru, sezono pa konča z medsebojnim srečanjem.',
        shemaNamiga: 'enakomerna',
      },
      { tip: 'shema', shema: 'enakomerna', opis: '10 ekip: pari A–F, B–G, C–H, D–I, E–J.' },
      {
        tip: 'odstavek',
        besedilo:
          'Tako vsaka ekipa v vsakem krogu dobi enega močnejšega in enega šibkejšega nasprotnika. Vrstni red ekip urediš s puščicama in **Shrani vrstni red**. Pri lihem številu parov (10, 6, 14 ekip) ima liga eno kolo več in vsaka ekipa eno prosto kolo.',
      },
      {
        tip: 'razdelek',
        id: 'razveljavi',
        naslov: 'Razveljavi razpored',
        kratko:
          'Liga se vrne v pripravo, srečanja in termini gredo, ekipe in kader ostanejo. Mogoče je, dokler nobeno srečanje ni začeto.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Gumb **Razveljavi razpored** je v meniju lige samo, dokler je razveljavitev mogoča. Ko ima prvo srečanje postavo, ga ni več.',
      },
    ],
    povezani: ['termini-kol', 'ure-kola', 'ekipe-kader', 'vodic-obstojeca-liga'],
  },
  {
    id: 'ure-kola',
    naslov: 'Kolo z več urami (večer z več krogi)',
    poglavje: 'liga',
    kratko:
      'Liga, ki se zbere en večer na teden, lahko v enem kolu odigra več krogov: ob 18.30 prvega, ob 19.45 drugega. Vsaka ekipa odigra toliko srečanj, kolikor je ur, kol pa je zato manj.',
    kljucne: ['ure', 'večer', 'več srečanj na kolo', 'ure krogov', 'srečanj vsake ekipe na kolo'],
    shemaNamiga: 'ure-kola',
    vsebina: [
      { tip: 'shema', shema: 'ure-kola', opis: 'Kolo ob dveh urah: dva kroga istega večera.' },
      {
        tip: 'seznam',
        postavke: [
          'V obrazcu lige pri terminih nastavi **Srečanj vsake ekipe na kolo** in vpiši **Ure krogov** (vsaj dve, naraščajoče).',
          'Ob isti uri igrajo vse ekipe hkrati, vsaka enkrat.',
          'Dve uri pomenita pol manj kol. V razporedu ima kolo nad vsako uro svoj naslov.',
          'Ure so pravilo lige: po razporedu se zaklenejo. Uro posameznega srečanja pa lahko popraviš v terminih.',
        ],
      },
    ],
    povezani: ['termini-kol', 'razpored'],
  },
  {
    id: 'termini-kol',
    naslov: 'Termini kol',
    poglavje: 'liga',
    kratko:
      'Datume kol izračuna žreb iz začetka prvega kola in razmika (npr. vsakih 7 dni). Posamezno kolo nato premakneš v oknu »Termini kol« — tudi sredi sezone. Prestavljeno kolo ne premakne naslednjih.',
    kljucne: ['termin', 'datum', 'ura', 'kolo', 'razmik', 'koledar', 'napolni po razmiku'],
    shemaNamiga: 'termini',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Termini', 'Termini kol'] },
      { tip: 'shema', shema: 'termini', opis: 'Prvo kolo + razmik dasta vse datume; kolo 4 je ročno premaknjeno.' },
      {
        tip: 'koraki',
        postavke: [
          'Že ob ustvarjanju lige vpiši **Prvo kolo** (datum in uro) in **Razmik** (koliko dni po prejšnjem).',
          'Ob razporedu dobijo vsa kola datume.',
          'V oknu **Termini kol** popravi kolo, ki je na drug dan (praznik, zasedena dvorana), in shrani.',
          '**Napolni po razmiku** prepiše datume vseh kol iz prvega; prestavljena srečanja ostanejo.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Ura 00:00 pomeni »ura ni določena« — izpiše se samo datum.',
          'Prestavljeno kolo ne premakne naslednjih: ta so že objavljena.',
          'Kolo je za gledalca »odigrano«, ko je njegov datum mimo — tudi če kak zapisnik še ni vpisan.',
        ],
      },
    ],
    povezani: ['prestavitve', 'ure-kola', 'razpored'],
  },
  {
    id: 'prestavitve',
    naslov: 'Prestavitev srečanja',
    poglavje: 'liga',
    kratko:
      'Posamezno srečanje prestaviš v drugo kolo v oknu Termini, zavihek »Srečanja po kolih«: izberi srečanje in klikni kolo (ali ga povleci). Če termin še ni znan, ga povleci na »?«.',
    kljucne: ['prestavitev', 'prestavi', 'preloži', 'odpoved', 'termin ni znan', 'nadomestni termin'],
    shemaNamiga: 'prestavitev',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Termini', 'Srečanja po kolih'] },
      { tip: 'shema', shema: 'prestavitev', opis: 'Srečanje iz 3. kola gre v 5. kolo; srečanje brez termina gre na »?«.' },
      {
        tip: 'koraki',
        postavke: [
          'Odpri **Termini** in zavihek **Srečanja po kolih**. S filtrom ekipe hitro najdeš srečanje.',
          'Izberi srečanje in klikni kolo, v katerem se bo igralo (na računalniku ga lahko povlečeš).',
          'Vpiši dan in uro novega termina ter, če želiš, **Razlog** (neobvezno).',
          'Shrani.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Srečanje stoji **samo v kolu, v katerem se igra** — v razporedu, koledarju in pri ekipi.',
          'Prestaviš lahko tudi že odigrano srečanje (odigrano prej) ali neodigrano izpred kola; izid ostane.',
          '**Termin še ni znan**: povleci srečanje na polje »?«. V razporedu je pod gumbom **?** na koncu traku kol.',
          'Okno opozori, kadar bi ekipa v kolu igrala več srečanj kot običajno, a ne prepove.',
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Razlog je javen. Ne vpisuj osebnih podatkov (bolezen, naslovi) — dovolj je »na željo gostov«.',
      },
    ],
    povezani: ['termini-kol', 'razpored'],
  },
  {
    id: 'zapisnik-srecanja',
    naslov: 'Zapisnik srečanja',
    poglavje: 'liga',
    kratko:
      'Najprej postava: vsakemu mestu (A, B, C · X, Y, Z) dodeliš igralca iz kadra in shraniš — nastanejo tekme. Nato pri vsaki tekmi klikneš »Vnesi« in vpišeš nize.',
    kljucne: ['zapisnik', 'postava', 'srečanje', 'vnos srečanja', 'menjava', 'zamenjava igralca', 'dvojice', 'w.o.'],
    shemaNamiga: 'postava',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Razpored', 'klik na srečanje'] },
      { tip: 'shema', shema: 'postava', opis: 'Postava: mesta ekip, nato tekme po formatu.' },
      {
        tip: 'razdelek',
        id: 'postava',
        naslov: 'Postava',
        kratko: 'Vsakemu mestu dodeli igralca iz kadra; pri dvojicah izberi par. »Shrani postavo in generiraj tekme« ustvari tekme po formatu lige.',
      },
      {
        tip: 'koraki',
        postavke: [
          'Za domačo ekipo izberi igralce na mestih A, B, C, za gostujočo X, Y, Z.',
          'Pri dvojicah izberi oba igralca para.',
          'Klikni **Shrani postavo in generiraj tekme**.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'tekme',
        naslov: 'Izidi tekem',
        kratko: 'Pri vsaki tekmi klikni »Vnesi« in vpiši nize (in točke). Ko ena ekipa doseže prag zmag, je srečanje končano.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Vnos je enak kot na turnirju. Pri izidu lahko izbereš **Brez boja (w.o.)**, **Predaja** ali **Diskvalifikacija**. [Vnos rezultata](vnos-rezultata)',
      },
      {
        tip: 'razdelek',
        id: 'menjava',
        naslov: 'Menjava igralca',
        kratko: 'Menjava velja za eno tekmo, ki še čaka: na mesto iz postave postaviš drugega igralca iz kadra iste ekipe. Postava ostane začetna.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Pri tekmi, ki še čaka, klikni **Menjava** in izberi igralca iz kadra. V rating in lestvico igralcev šteje tisti, ki je tekmo res igral. Oznaka tekme (»A-Y«) ostane, ob imenu pa je zapisana menjava.',
      },
      { tip: 'razdelek', id: 'tisk', naslov: 'Zapisnik za tisk' },
      {
        tip: 'odstavek',
        besedilo:
          'Dokler srečanje ni končano, je na strani gumb **Zapisnik za tisk**: pred postavo natisne prazen obrazec z imeni ekip, po postavi pa zapisnik z igralci. [Zapisnik za tisk](zapisnik-tisk)',
      },
      {
        tip: 'pazi',
        besedilo:
          'Postave po prvem izidu ni več mogoče spremeniti. Če si v postavo vpisal napačna igralca, zapisnik razveljavi in ga vpiši znova. [Razveljavitev](razveljavitev-zapisnika)',
      },
    ],
    povezani: ['format-srecanja', 'razveljavitev-zapisnika', 'lestvica-lige', 'branje-zapisnikov'],
  },
  {
    id: 'razveljavitev-zapisnika',
    naslov: 'Razveljavitev zapisnika',
    poglavje: 'liga',
    kratko:
      'Srečanje se vrne v razpored, kot da ga nihče ni vpisal: postava, tekme, točke in rating gredo. Postavo in izide nato vpišeš znova. Zavrne se, kjer je po izidu že kaj teklo (končnica).',
    kljucne: ['razveljavi', 'izbriši zapisnik', 'napačna postava', 'ponoven vnos'],
    vsebina: [
      { tip: 'kje', pot: ['Srečanje', 'na dnu zapisnika', 'Razveljavi zapisnik'] },
      {
        tip: 'seznam',
        postavke: [
          'Uporabi, ko je narobe **postava** (popravek izida igralcev ne zamenja).',
          'Rating se preračuna od časa srečanja naprej.',
          'Ni mogoče, ko je srečanje že vplivalo na nekaj naprej: končana tekma končnice ali srečanje rednega dela, iz katerega je že sestavljena končnica.',
          'Pri ekipni tekmi turnirja okno pove, kaj se vrne v turnirju (mreža, skupina).',
        ],
      },
    ],
    povezani: ['zapisnik-srecanja', 'popravek-rezultata'],
  },
  {
    id: 'lestvica-lige',
    naslov: 'Lestvice lige',
    poglavje: 'liga',
    kratko:
      'Liga ima tri lestvice: ekipno (po točkah iz pravil lige) ter lestvici posameznikov in dvojic, ki štejeta samo tekme te lige.',
    kljucne: ['lestvica', 'točke', 'tabela', 'posamezniki', 'dvojice', 'uspešnost'],
    shemaNamiga: 'cone',
    vsebina: [
      {
        tip: 'seznam',
        postavke: [
          '**Ekipna** — točke po točkovanju lige; obarvane so cone napredovanja, izpada in kvalifikacij.',
          '**Posamezniki** — zmage v tej ligi, ob izenačenju uspešnost in razlika nizov.',
          '**Dvojice** — isti par šteje kot ena dvojica, doma in v gosteh.',
        ],
      },
      { tip: 'shema', shema: 'cone', opis: 'Cone na lestvici: napredovanje, kvalifikacije, izpad.' },
      {
        tip: 'odstavek',
        besedilo:
          'Klik na ekipo odpre njen kader, razvrščen po zmagah za to ekipo v tej ligi, in njena srečanja.',
      },
    ],
    povezani: ['prehodi', 'razvrstitev'],
  },
  {
    id: 'prehodi',
    naslov: 'Piramida lig in prehodi',
    poglavje: 'liga',
    kratko:
      'Liga lahko pove, katera liga je nad njo ter koliko ekip napreduje in izpade. Prehodi niso pravilo tekmovanja, zato jih urejaš v vsakem stanju lige (»Uredi prehode«).',
    kljucne: ['piramida', 'napredovanje', 'izpad', 'višja liga', 'nižja liga', 'prehodi', 'uredi prehode'],
    shemaNamiga: 'piramida',
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Uredi prehode'] },
      { tip: 'shema', shema: 'piramida', opis: 'Tri lige v piramidi: prvi dve napredujeta, zadnji dve izpadeta.' },
      {
        tip: 'seznam',
        postavke: [
          '**Višja liga** — kamor se napreduje. Povezavo lahko vpišeš tudi z druge strani (pri višji ligi kot nižjo).',
          '**↑ Napredovanje** in **↓ Izpad** — koliko ekip gre neposredno gor oz. dol.',
          'Kvalifikacije: koliko ekip tik pod napredovanjem oz. tik nad izpadom igra s sosednjo ligo. [Kvalifikacije](kvalifikacije)',
          'Piramida na strani lige sledi samo vpisanim povezavam.',
        ],
      },
    ],
    povezani: ['kvalifikacije', 'lestvica-lige'],
  },
  {
    id: 'kvalifikacije',
    naslov: 'Kvalifikacije med ligama',
    poglavje: 'liga',
    kratko:
      'Kvalifikacije so svoja mala liga med ekipami tik pod napredovanjem in tik nad izpadom. Ustvari jih lastnik višje lige, ko sta odigrani obe ligi. Pari so križni: najboljši višje z najslabšim nižje.',
    kljucne: ['kvalifikacije', 'baraž', 'dodatne tekme', 'obstanek', 'križno'],
    shemaNamiga: 'kvalifikacije',
    vsebina: [
      { tip: 'kje', pot: ['Višja liga', 'Ustvari kvalifikacije'] },
      { tip: 'shema', shema: 'kvalifikacije', opis: 'Križni pari: 8. višje lige s 4. nižje, 9. s 3.' },
      {
        tip: 'koraki',
        postavke: [
          'V prehodih obeh lig nastavi, koliko ekip igra kvalifikacije.',
          'Ko sta redna dela obeh lig odigrana, lastnik višje lige klikne **Ustvari kvalifikacije**.',
          'Izberi način: **Ena tekma**, **Serija do 2/3 zmag** ali **Vsak z vsakim** (enkrat ali doma in v gosteh).',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Ekipe so kopije s kadri — lestvici obeh lig ostaneta nedotaknjeni.',
          'Pri eni tekmi in seriji igra prvo tekmo doma ekipa višje lige.',
          'Kvalifikacije ne štejejo v mejo lig tvojega paketa.',
          'Razveljaviš jih v celoti (**Razveljavi kvalifikacije**), dokler se ni začelo nobeno srečanje.',
        ],
      },
    ],
    povezani: ['prehodi', 'koncnica'],
  },
  {
    id: 'koncnica',
    naslov: 'Končnica',
    poglavje: 'liga',
    kratko:
      'Po rednem delu igrajo najboljše 2, 4 ali 8 ekip serije do N zmag. Končnico ustvariš z »Ustvari končnico«, ko so vpisani vsi izidi rednega dela. Pari: 1–4 in 2–3.',
    kljucne: ['končnica', 'play-off', 'polfinale', 'finale', 'serija'],
    shemaNamiga: 'koncnica',
    vsebina: [
      { tip: 'shema', shema: 'koncnica', opis: 'Končnica štirih: 1–4 in 2–3, zmagovalca v finale.' },
      {
        tip: 'koraki',
        postavke: [
          'V pravilih lige pri **Po rednem delu** izberi končnico in **Serijo do zmag**.',
          'Ko so vpisani vsi izidi rednega dela, se na strani lige pojavi **Ustvari končnico**.',
          'Vsaki tekmi serije nastavi termin (**Shrani termin**) in vpisuj zapisnike kot v rednem delu.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Prvo tekmo serije gosti slabše uvrščena ekipa, odločilno bolje uvrščena.',
          'Ko je serija odločena, neodigrane tekme izginejo same.',
          'Srečanja končnice ne štejejo v lestvico rednega dela.',
          '**Razveljavi končnico** je mogoče, dokler se ne začne nobena tekma.',
        ],
      },
    ],
    povezani: ['kvalifikacije', 'lestvica-lige', 'liga-nova'],
  },
  {
    id: 'branje-zapisnikov',
    naslov: 'Zapisniki za bralnik',
    poglavje: 'liga',
    kratko:
      'Zapisnike kola natisneš »za bralnik«, igralci prekrižajo, kdo je igral, in vpišejo točke. Po kolu liste fotografiraš ali skeniraš, Turnirko jih prebere in zanesljive zapiše sam; ostale pregledaš.',
    kljucne: ['fotografija', 'slika', 'skener', 'PDF', 'QR', 'križec', 'menjava', 'samodejno', 'zapisnik kola', 'bralnik', 'tabela kadra', 'vrstica tekme'],
    vsebina: [
      { tip: 'kje', pot: ['Liga', 'Zapisniki kola'] },
      {
        tip: 'koraki',
        postavke: [
          'Pred kolom odpri **Zapisniki kola**, izberi kolo in **Natisni**. Vsako srečanje dobi svoj ležeči list s kodo QR in kadroma obeh ekip.',
          'Liste razdeli ekipam. Na srečanju pri vsaki tekmi prekrižajo igralca, ki jo je igral (pri dvojicah oba), in vpišejo točke nizov.',
          'Po kolu odpri **Branje zapisnikov** in liste fotografiraj z **Fotografiraj zapisnik** ali naloži PDF iz skenerja z **Naloži slike ali PDF**.',
          'Zanesljivo prebrani zapisniki se zapišejo sami. Ostali so pod **Za pregled**: odpri **Preglej**, primerjaj obarvana polja z izrezom fotografije, popravi in **Zapiši v srečanje**.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'oblika',
        naslov: 'Vrstica tekme ali tabela kadra',
        kratko: 'Vrstica tekme: križci in točke v isti vrsti, za kader do šest igralcev. Tabela kadra: igralci so vrstice, tekme stolpci - za večji kader. Samodejna oblika izbere sama.',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Vrstica tekme**: vsaka tekma je ena vrsta - kvadratki domačih, točke nizov, kvadratki gostov. Imena kadra so glave stolpcev.',
          '**Tabela kadra**: zgoraj kadra obeh ekip (igralec je vrstica, tekma stolpec), spodaj tabela točk. Prostora je za več imen.',
          'Samodejna oblika vzame vrstico tekme, kadar gre kader nanjo, sicer tabelo kadra.',
          'Zadnja vrstica ali stolpec kadra je za igralca, ki ga v kadru še ni: ime napišejo na črto. Takega igralca izbereš ob pregledu.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'krizci',
        naslov: 'Križci in napake',
        kratko: 'Napačen križec igralci zapolnijo do črnega - bralnik ga ne šteje. Pri posamični tekmi je na ekipo en križec, pri dvojicah dva.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Postave ni treba posebej vpisovati: mesto A je igralec, ki je igral prvo tekmo z mestom A (pri Savinji A-X). Kdor kasneje igra namesto njega, je menjava te tekme. [Menjava](zapisnik-srecanja#menjava)',
      },
      {
        tip: 'razdelek',
        id: 'samodejno',
        naslov: 'Kdaj se zapisnik zapiše sam',
        kratko: 'Ko je vsak križec in vsaka številka prebrana zanesljivo, potek srečanja drži pravila in srečanje še ni vpisano. Izbiro »zapiši sam« lahko izklopiš.',
      },
      {
        tip: 'seznam',
        postavke: [
          'Zapis gre po isti poti kot ročni vnos: postava, menjave, izidi, rating.',
          'Če je srečanje že vpisano, se zapisnik ne prepiše sam: odpri ga in izberi **Prepiši zapisnik**.',
          'Zapisan zapisnik lahko pod **Zapisano** odpreš s **popravi** - prejšnji zapis se razveljavi in zapiše popravljeni.',
          'Fotografija ostane 30 dni, vidiš jo samo ti (in administrator).',
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Bralnik bere samo zapisnike, natisnjene iz Turnirka z obliko »za bralnik« (koda QR zgoraj desno). Uradni obrazec NTZS in ležeči zapisnik Turnirko vpišeš ročno.',
      },
    ],
    povezani: ['zapisnik-srecanja', 'razveljavitev-zapisnika', 'branje-listkov'],
  },
]
