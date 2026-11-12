# Pravila: tisk

> Natisljivi listki tekem in skupin, žreb mreže za tisk, izpis rezultatov.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Listki tekem** (natisljivi zapisniki): dve strani, obe **namenoma zunaj
  `Postavitve`** (brez navigacije), da je natis čist; tiska brskalnik
  (`window.print`) — **brez zaledja in nove sheme**, listek je le pogled na
  podatke, ki že obstajajo:
  - `ListkiStran` (`/dogodki/:id/listki`) — turnirski dogodek; natisne
    **pripravljene tekme** (oba igralca znana, status `PRIPRAVLJENA`/`V_IGRI`;
    prosti prehodi in `CAKA` odpadejo sami) kot posamične **sodniške listke**
    (`komponente/Listek`, normaliziran `ListekPodatki`), narejene po papirnati
    predlogi lastnika (okt 2026). Povezava »Listki za tiskanje« na
    `DogodekStran` se pokaže organizatorju ob `V_TEKU`. Turnir ima le to eno
    predlogo (ni izbire). Pravila, ki jih ne razbij:
    - **Listek je tabela, v kateri ima vsak igralec SVOJ stolpec**: ime je
      glava stolpca, pod njim znački »Prvi servis« in »Timeout« (kvadratek, v
      katerega sodnik zapiše križ), nato po ena celica za vsak niz in
      rezultat (dobljeni nizi, debelejša obroba). Vse vrstice delijo
      `--listek-stolpci`, zato so točke vedno natanko pod imenom tistega, ki jih
      je dosegel. Dvopičje v sredinskem žlebu bere vrstico kot »11 : 7«.
    - Vsebina: znamka (`ZnakTurnirko` + »Turnirko«) in prazna črta »Miza št.«
      (številka se izpiše, če je že dodeljena), nato Dogodek (= turnir, kot ga
      igralci poznajo), Kategorija (ime dogodka; spol in starost se dodata samo,
      če ju ime ne pove, `oznakaKategorije`; »Kdorkoli« se izpusti), Skupina +
      kolo oz. Faza pri mreži, spodaj Sodnik in Podpis. **Kluba na listku ni**
      (prostor; sodnik igralca vidi). Dvojica ima vsakega igralca v svoji vrstici.
      Pri 6–7 nizih se listek stisne (`.listek--gosto`), da celice ostanejo
      ≥ 4,2 mm.
    - **List A4 = natanko 210 × 297 mm = 2 × 3 listki po 105 × 99 mm, brez
      robov** (`@page listki { margin: 0 }`, `page: listki` na
      `.listki-stran--listki`). Globalni rob 10 mm bi listek skrčil na
      95 × 92 mm in rez ne bi šel po sredini lista; brez roba brskalnik tudi ne
      natisne glave in noge. Trije rezi (ena navpična, dve vodoravni) so črtkane
      obrobe listkov (`.listek:nth-child`), narisane tudi na praznih mestih
      zadnjega lista (`razdeliNaListe`), da so rezi povsod isti. Vsebina je
      6–7 mm od roba lista, da je tiskalnik ne odreže. Vsak list pri tisku
      `break-before: page`; na ožjem zaslonu ga pomanjša CSS `zoom`
      (`ListkiStran`, pri tisku `zoom: 1 !important`).
    - Izmerjeno z brezglavim Chromom (PDF → mupdf, glej »Posnetki vmesnika«):
      12 listkov = 2 strani A4 (595 × 842 pt), brez prazne tretje; najslabši
      primer (7 nizov, imena v dveh vrsticah, 55 znakov dolg turnir) ostane
      znotraj listka. Ob spremembi mer ponovi.
  - `ListkiSrecanjaStran` (`/srecanja/:id/listki`) — ekipno srečanje; natisne
    **en uraden ekipni zapisnik NTZS** (`komponente/ZapisnikEkipnegaDvoboja`)
    za celotno srečanje: glava, obe ekipi s postavo (A/B/C : X/Y/Z), mreža vseh
    posamičnih tekem in podnožje (sodniki, vodji, podpisi). Predlogo (varianti
    `SNTL_1` / `SNTL_23`) izbere liga (`liga.predlogaListka`); varianta določa
    oznako lige in prisotnost vrstice za delegata NTZS. Na strani je še živ
    izbirnik variante za ta natis. (Ne uporablja več komponente `Listek`.)
    - **Obrazec se natisne tudi PRED postavo** (okt 2026): ekipi sta že
      natisnjeni, postavo in igralce tekem / dvojic vpiše ekipa z roko. Tekem
      srečanja pred postavo še ni, zato `ZapisnikEkipnegaDvoboja` prazne
      vrstice sestavi iz `RAZPORED_FORMATA[podrobno.format]` (višje vrstice,
      razred `zapisnik--prazen`); število stolpcev za nize pove
      `SrecanjePodrobnoDto.steviloNizov` (pravilo tekmovanja, ne tekma). Gumb
      »Zapisnik za tisk« na strani srečanja je zato ponujen, dokler srečanje ni
      končano (prej šele po postavi).
    - **Ekipna tekma turnirja** nima lige in zato nima predloge
      (`varianta = null`): podnaslov je ime dogodka, vrstica »Turnir«, brez
      delegata. Ura 00:00 pomeni »ura ni določena« in ostane prazno polje.
    - **Ekipni dogodek** natisne vse pripravljene tekme naenkrat: isti naslov
      `/dogodki/:id/listki` (`ListkiStran` ga za `disciplina === 'EKIPNO'`
      preda `ZapisnikiEkipnegaDogodka`), vsak zapisnik na svoj list
      (`.zapisnik-stran`, `break-before: page`); podatke bere iz poizvedbe
      srečanja, ki jo deli s stranjo srečanja. Tekma brez srečanja (prenesen
      izid, še nedoločen nasprotnik) odpade.
    - **2 ali 3 zapisniki na list** (izbirnik »Na list« na strani ekipnega
      dogodka, izbira ostane v `localStorage['turnirko-zapisnikov-na-list']`):
      list je steber 190 × 276 mm (`.zapisnik-list`), zapisnik v svoji rezi
      (`.zapisnik-rez`, višina `--rez`) in v zgoščeni obliki (`gosto` →
      `.zapisnik--gosto`). Merilo se **izmeri** (`ZapisnikVRezi`: višina obrazca
      pri tiskarski širini 190 mm, po naloženih pisavah, CSS `zoom`, nikoli
      povečava, 1,5 % rezerve), ne ugiba — višina je odvisna od formata.
      Izmerjeno (blank, 8 ekip): 2 na list SNTL (10 vrstic) 84 %, 3 na list
      SNTL 55 % (stran opozori pod 70 %), 3 na list Pokal NTZS (5 vrstic) 70 %,
      DŠI 82 %. Pri 1 na list se nič ne meri in ne pomanjšuje.
    - **Razreda `.zapisnik__glava` in `.zapisnik__oznaka` pripadata oknom
      igralcev (razdelek 21)** — obrazec NTZS zato uporablja `__vrh` in `__par`;
      pri istem imenu je glava dobila odmik, stolpec »Par« pa se je razširil čez
      vso tabelo in stisnil imeni igralcev.
    - **Turnirkov ekipni zapisnik** (okt 2026, `komponente/EkipniZapisnikTurnirko`,
      slog razdelek »Turnirkov ekipni zapisnik«, razredi `.ekipni-zapisnik__*`):
      druga OBLIKA istega zapisnika, po papirnatem zapisniku lige Savinja —
      **ležeči A4**, glava z znamko Turnirka (`ZnakTurnirko` v barvah + »Turnirko«)
      in logotipom lige, obe ekipi s postavo (dvojica, igralci, rezerva; vrstice
      za vpis so visoke 8 mm, `--ez-postava`), mreža tekem (Par, A-X … + nizi,
      Rezultat, Stanje dvoboja) in noga: rezultat dvoboja ter lokacija / datum /
      ura / kolo v isti vrstici na dnu lista (podpisov, sodnika in pripomb ni;
      lokacija, datum, ura in kolo so spodaj, ne zgoraj, da imata postavi več
      prostora — odločitev lastnika, 27. 10.). **Ime ekipe je v svoji vrstici
      čim večje** (do 28 pt, 22 pt pri formatih z 8+ vrsticami), oznaka »Ekipa
      A/X« je nad njim brez »domači/gostje«; velikost se zmanjša po dolžini imena
      (`velikostImenaPt`, ocena širine znaka, ne meritev), vrstica pa ima stalno
      višino, da postavi obeh ekip stojita v isti višini. Ponujen je **pri
      ligi in pri ekipnem dogodku turnirja** (izbirnik »Obrazec«,
      `IzbirnikObrazca`), uradni obrazec NTZS ostaja.
      - **Oblika ni pravilo tekmovanja** (`pomozno/oblikaZapisnika`): shema in
        zaledje je ne poznata, izbere jo tisk. Zadnja izbira ostane v
        `localStorage['turnirko-oblika-zapisnika']`; brez nje format **Savinja**
        (2 igralca, dvojice prve) dobi Turnirkov zapisnik, vsi ostali uradnega.
        Predloga lige (1. SNTL / 2./3. SNTL) velja samo za uradni obrazec.
      - **Sestava vrstic je skupna obema oblikama** (`pomozno/zapisnikPodatki`:
        `sestaviPodatkeZapisnika`, `razbijCas`) — kar je vpisano, se izpiše, ostalo
        je prazno za roko, in tudi ta obrazec se natisne pred postavo (vrstice iz
        formata). Stanje dvoboja se izpiše iz končanih tekem po vrstnem redu
        obrazca (`stanjaDvoboja`). **Lokacije in rezerve ne hranimo** — ostaneta črti.
      - **Logotip** je naložen logotip tekmovanja (V54, glej »Slike in logotip
        tekmovanja«): pri ligi njen, pri ekipnem dogodku logotip turnirja (stran ga
        poda kot `logotip`, različica TISK). Brez logotipa ima glava le znamko
        Turnirka. Do V54 je bil vgrajen in izbran po imenu lige (samo Savinja).
      - **List je natanko ena ležeča stran A4** (`@page ekipni-zapisnik`, rob
        10 mm; 188 mm vsebine, ker tisk v zaokroževanju sicer prelije na prazen
        list). Višino vrstic mreže razdeli CSS grid po tem, kar ostane (dvojica
        1,6 × vrstica, najmanj 6,4 mm), nič se ne meri v JS: Savinja 11 mm,
        SNTL z desetimi tekmami 9 mm (od 8 vrstic se glava in noga zgostita,
        `ekipni-zapisnik--gosto`). Na ekipnem dogodku je **en zapisnik na list**
        (izbira »Na list« velja le za NTZS). Ožji zaslon list pomanjša
        (`pomozno/meriloLista`, CSS `zoom`; pri tisku se izključi).
      - Barvna sta samo znamka in logotip; besedilo in črte so `#000`. Razredi
        `zapisnik__*` so zasedeni (obrazec NTZS, okna igralcev), zato `ekipni-zapisnik__*`.
      - Izmerjeno z brezglavim Chromom (CDP, lažen API, PDF → mupdf): Savinja
        (prazen, izpolnjen, 7 nizov), SNTL (10 tekem), DŠI in ekipni dogodek
        s tremi tekmami — vsak je ena ležeča stran 842 × 595 pt. **Ni preverjeno
        na pravem tiskalniku.**
  - Listki so **črno-beli ne glede na temo** (fiksni `#000/#fff`, ne temo
    odvisne spremenljivke); sloge in `@media print` (`@page` A4,
    `break-inside: avoid`, `print-color-adjust: exact`) drži `slog.css`.
- **Listki skupin (žreb skupin za tisk)** (okt 2026; 20. 10. prenovljeno po
  papirnati predlogi lastnika — list skupin državnega prvenstva in TOP-a):
  vsaka skupina je **listek s križno tabelo** (vsak z vsakim: številka, ime,
  klub, celica za vsakega člana s poševnico za nize, siva diagonala, nato
  Nizi · Zmage · Mesto) in pod njo **razporedom po kolih**. Isti zasnovi kot
  sodniški listki — stran zunaj `Postavitve`, tiska brskalnik, **brez
  zaledja**: vse je že v `MrezaDto` (skupine, prijave, tekme).
  - Poti: `/dogodki/:id/zreb-skupin` (ena kategorija) in
    `/turnirji/:id/zreb-skupin` (vse kategorije turnirja z izžrebanimi
    skupinami, ena za drugo — listek nosi kategorijo, zato si smejo deliti
    list). Strani `ZrebSkupinDogodkaStran` / `ZrebSkupinTurnirjaStran`
    (`strani/ZrebSkupinStran`), izris `komponente/ZrebSkupinTisk`
    (`ListiSkupin`), **sestava, mere in zlaganje so čista logika** v
    `pomozno/zrebSkupin.ts` (`sklopiZrebSkupin`, `deliListka`, `listkiSkupin`,
    `zloziNaListe`). Gumb »Žreb skupin za tiskanje« ostaja, kjer je bil
    (organizator, `imaZrebanoSkupine`: skupinski sistem + dejanski `V_TEKU`).
  - **Dve obliki razporeda** (izbirnik »Pod tabelo« v orodni vrstici, ne
    shranjuje se): **razpored z nizi** (vrstica tekme: kolo, par »1 – 4«,
    imeni z dvopičjem na sredini, okence za vsak niz, Rezultat — skupina igra
    sama za mizo) in **samo razpored parov** (kola v stolpcih, pari po
    številkah — sodniki pišejo na svoje listke, kot pri TOP). **Samodejno**
    (privzeto) ima vsaka kategorija svojo: do 6 igralcev z nizi, večja samo
    pare (`privzetNacin`). Ekipni dogodek nima okenc za nize (samo Rezultat),
    stolpec vsote je »Tekme«.
  - **List A4 = 210 × 297 mm brez robov** (`@page zreb-skupin { margin: 0 }`,
    `page` na `.zreb-stran--skupine`). **Listek je širok kot list in visok
    2, 3, 4 ali 6 šestin** (99 / 148,5 / 198 / 297 mm; 5 šestin ne — odpadek
    sredi lista), zato se list reže **samo vodoravno**, po črtkani črti pod
    vsakim listkom, ki ne sega do dna lista. Listki se zlagajo po vrsti, nov
    list, ko naslednji ne gre več (`zloziNaListe`). Vsebina je 8 mm od strani
    in 5 mm od zgornjega/spodnjega roba listka.
  - **Višina se izračuna, ne izmeri**: vse vrstice imajo stalno višino v mm
    (`MERE`), besedilo se ne lomi (dolgo ime se zmanjša, nato odreže, klub v
    največ dveh vrsticah), zato je višina listka vsota vrstic. Listek dobi
    najmanjši del lista, na katerega gre z vrsticami, **stisnjenimi največ na
    0,9**, nato vrstice raztegne do polnega listka (**največ 1,35**). Izris iste
    številke postavi v spremenljivke sloga na `.listek-skupine` (`--kriz-vrstica`
    …) — slog drugih višin nima, zato se izračun in izris ne moreta raziti.
    Izmerjeno: skupine po 3–4 (z nizi) in do 8 (pari) gredo **tri na list**,
    5–6 z nizi dve na list, 7 z nizi dve tretjini, 8–9 z nizi cel list, 10 in
    12 s pari pol lista.
  - **Velika skupina se nadaljuje**: razpored, ki tudi stisnjen ne gre na cel
    list (10+ igralcev z nizi), gre na naslednji listek (»nadaljevanje
    razporeda · 2. del od 2«); kolo se ne razdeli.
  - Vsak listek nosi **kategorijo** (`oznakaKategorije`, skupna s sodniškim
    listkom), **turnir** (`vrsticaTurnirja`), **skupino** (in sklop:
    Predtekmovanje / Finalne skupine / Nivo N) ter **znamko Turnirko**
    (`ZnakTurnirko` + napis).
  - Člani predtekmovalne skupine so oštevilčeni **po jakosti** (`stNosilca`,
    kakor jih je žreb razporedil), ne po lestvici — ta bi jih med turnirjem
    premetavala in številke v razporedu bi ob ponovnem tisku pomenile druge
    ljudi. Pri skupinah za mesta ostane vrstni red lestvice.
  - **Odigrana tekma se izpiše** (v celici »3/1« z vidika vrstice, zmaga
    odebeljena; v razporedu točke nizov in rezultat), zato je ponovni tisk tudi
    pregled stanja. **Nizi, zmage in mesto pa šele, ko je odigrana vsa
    skupina** — delni seštevek bi se bral kot končni. Stolpec je »Zmage« in ne
    »Točke« s predloge: uvrstitev v skupini je po zmagah (PST 20. člen,
    `RazvrstitevStoritev`). Pri lihem številu članov kolo pove, kdo je prost
    (»prost 5«). Diagonala je 25 % siva kot na predlogi (DESIGN.md dovoli
    sivine nad 10 %).
  - Okvir strani (orodna vrstica, izbirnik, navodilo za tisk, pomanjšanje
    lista na ožjem zaslonu — pokončni in ležeči) je `komponente/ZrebOkvir`,
    skupen listu izločilne mreže.
  - Izmerjeno z brezglavim Chromom (`Page.printToPDF`, PDF → mupdf) na testni
    bazi s skupinami 3, 4, 5, 6, 7, 8, 10, 12 in ekipnim dogodkom: vsak list je
    210 × 297 mm, noben listek ne prelije, vsebina konča ≥ 5,5 mm nad rezom.
    Ob spremembi `MERE` ali slogov razdelka »Listki skupin« ponovi. Ni
    preverjeno na pravem tiskalniku.
- **Žreb izločilnega dela za vrata dvorane** (okt 2026): ista zasnova za
  izločilno mrežo — stran zunaj `Postavitve`, tiska brskalnik, brez zaledja
  (vse je v `MrezaDto`). Okvir strani (orodna vrstica, števec, »Natisni«,
  pomanjšanje na ožjem zaslonu) je skupen listkom skupin: `komponente/ZrebOkvir`.
  - Poti: `/dogodki/:id/zreb-mreze` in `/turnirji/:id/zreb-mreze` (vse
    kategorije turnirja z mrežo, vsaka na svojem listu). Strani
    `ZrebMrezeDogodkaStran` / `ZrebMrezeTurnirjaStran` (`strani/ZrebMrezeStran`),
    izris `komponente/ZrebMrezeTisk`, **sestava lista je čista logika** v
    `pomozno/zrebMreze.ts` (`sklopiZrebMreze`).
  - Gumb »Izločilni žreb za tiskanje« vidi organizator (`smem`), ko dogodek
    **teče** (`jeKandidatZaTiskMreze`) in mreža res **obstaja** (`imaMrezoZaTisk`:
    pri skupinah nastane šele po njih, zato jo stran turnirja vpraša za dogodke
    `SKUPINE_IZLOCILNI`; čisti `IZLOCILNI` jo ima od žreba). SV regija
    (svoje drevo za vsa mesta) in skupine za mesta mreže za tisk nimata.
  - **Ležeči A4** (`@page zreb-mreze`, `page` na `.zreb-stran--lezeci`; ostali
    tiski so pokončni). Mreža je **CSS grid**: tekma kola k zaseda 2^(k−1)
    vrstic, zato stoji natanko med svojima virovoma; črte (navpičnica med
    virovoma, vstop v okvir) risajo `::before`/`::after` ovoja tekme.
    Mreža mora biti polno binarno drevo (v kolu k tekme s pozicijami
    1…2^(K−k)), sicer lista ne ponudimo.
  - **Do 16 tekem v prvem kolu (32 igralcev) gre na en list.** Večja se razreže
    na poddrevesa po 16 tekem (5 kol, »1. del od 2« …) in **končni list** s
    preostalimi koli, katerega prvi stolpec piše »Zmagovalec 2. dela«. Stolpec
    zmagovalca stoji ob vsakem listu (na poddrevesu »Zmagovalec 1. dela«).
  - **Prazno mesto ostane črta za ročni vpis** (list je tudi delovni obrazec);
    odigrana tekma pokaže nize in odebeli zmagovalca, »b. b.« / »predaja« stoji
    pri poražencu; prost prehod je vrstica »prost«. Tekma za 3. mesto je v
    stolpcu zmagovalca. **Kluba in nosilcev na listu ni** (prostor; pozicija ob
    ročnem žrebu ni nosilec).
  - Izmerjeno z brezglavim Chromom (PDF → mupdf): 12, 32 in 64 igralcev, 6 po
    skupinah; vsak list je ležeči A4 (842 × 595 pt), mreža 32 igralcev ob robu
    (150 mm visoka). Ob spremembi mer ponovi.
- **Izpis rezultatov kategorije** (8. 11. 2026, zahteva lastnika; `strani/IzpisStran`,
  `/dogodki/:id/izpis`, gumb »Natisni rezultate«). Končna razvrstitev, skupinski in izločilni del
  **posebej ali v enem dokumentu**, po kljukicah; stikalo »Točke po nizih« velja za skupine in mrežo
  in ga ni, kadar točke nikjer niso vpisane. Izbira je v naslovu (`?deli=razvrstitev,skupine&tocke=0`).
  Stran je zunaj `Postavitve`, tiska brskalnik, **brez zaledja**: vse je v `MrezaDto`. Odločitve lastnika:
  - **Ena kategorija naenkrat**, samo na strani dogodka (ne na strani turnirja). Vrstni red delov je
    vedno razvrstitev, skupine, izločilni del.
  - **Vsak list nosi** znak in napis Turnirko, vrsto lista, ime turnirja, kategorijo, datum in kraj,
    logotip turnirja (če ga ima), v nogi pokrovitelje in »List 2 od 4«; v mreži je tudi klub.
  - **Gumb vidita organizator in admin, ko je kategorija zaključena** (`dejanskiStatus`); admin tudi pri
    uvoženem dogodku, ker tisk ničesar ne spremeni. Žreb skupin in mreža za vrata dvorane ostaneta,
    kot sta bila (samo med tekmovanjem). `/dogodki/:id/razvrstitev-tisk` (gumb »Natisni razvrstitev«)
    ostane javen kot prej.
  - **Vse je izračunano, nič izmerjeno** (`pomozno/izpisRezultatov`, čista logika; `sestaviIzpis` vrne
    liste): vrstice imajo stalno višino v mm, besedilo se ne lomi, zato je število listov znano,
    preden se kaj izriše, in iste številke (`MERE_LISTA`, `MERE_SKUPIN`, `MERE_SV`) postavi izris v
    spremenljivke sloga. `@page izpis-pokoncni` in `izpis-lezeci` imata rob 0 (brskalnik tako ne doda
    svoje glave in noge), rob je odmik lista; mešana usmeritev v istem PDF deluje prek `page` na listu.
  - **Razvrstitev na en list, največ dva** (zahteva lastnika): `listiRazvrstitve` vzame najbolj prostorno
    od gostot (`GOSTOTE`), pri kateri gre na en list, sicer najbolj prostorno, pri kateri gre na dva.
    Brez pokroviteljev gre okoli 82 udeležencev na en list in okoli 195 na dva. **Stolpca se polnita po vrsti**
    (`listiOstalih`: levi do dna lista, desni, nato naslednji list; razpolovljen je samo zadnji list):
    prejšnji razpolovljen seznam se je na dveh listih pomešal (levi stolpec je šel na drugi list, desni
    je tekel naprej na prvem). Ime ima prednost pred klubom, izkupiček krožnega sistema se ne odreže.
    Par in dolgo ime ekipe sta v enem stolpcu. Če si več kot štirje delijo vrh (stara uvožena zgodovina
    z zapisanima samo 1. in 2. mestom, ostali »3.–91.«), gre tretje mesto med ostale (`razdeliNaStopnice`).
  - **Skupine** uporabijo `KriznaTabela` in `RazporedZNizi` listkov z nižjimi vrsticami (`zaRezultate`:
    izid »3:1« namesto poševnice, prazno ostane prazno), brez črt za rez; štiri skupine po 4 s točkami
    oz. sedem do osem samo s tabelo na list. Skupina gre na list cela, samo prevelika (krožni sistem) se
    nadaljuje med koli. Krožni sistem je ena križna tabela »Vsak z vsakim« (`sklopKroznega`). Skupina
    brez vpisanih točk ima samo tabelo (seznam ne bi povedal nič novega).
  - **Mreža** (`IzpisMreza`, ležeči A4, razrez kot pri mreži za vrata) ima točke v tanki vrstici pod
    okvirjem (zunaj okvirja, da črte ostanejo ravne), klub pa samo v prvem kolu lista: tam igralec v
    mrežo vstopi, naprej se isti ljudje ponavljajo. Dolgo ime se zmanjša, preden se odreže; par se v
    ozkem stolpcu skrajša na priimka. Tekma za 3. mesto stoji ob zmagovalcu.
  - **Žrebi SV regije** (`IzpisSv`, `blokiZreba`): poti žreba (`odsekiZreba` iz `SvDrevo`) v dveh pasovih
    na ležečem listu, najširša levo, ožje desno, kar desno ne gre, pod levo. Dva žreba po 8 ali en
    žreb 16 na list (tudi s pokrovitelji); žreb 32 tri liste.
  - **Uvoženi dogodki imajo tudi drugačne oblike**: polna tolažilna mreža dobi svoje liste
    (»Tolažilna mreža«), nepolna mreža in tekme za razvrstitev pa seznam po kolih (`listiTekem`,
    »Izločilne tekme«, »Tekme za razvrstitev«), da ne manjka noben rezultat.
  - Izmerjeno z brezglavim Chromom (`Page.printToPDF`, PDF → mupdf) na testni bazi z vsemi sistemi
    (izločilni 8–180, skupine, TOP, krožni do 14, skupine za mesta, SV regija z žrebi 8/16/32, dvojice,
    ekipno) in na vzorcu pravih uvoženih dogodkov: število strani je enako izračunu, nič ne prelije;
    čez vseh 957 dogodkov testne baze pravila tečejo brez napake, vsaka odigrana izločilna tekma je na
    izpisu, razvrstitev nikjer čez dva lista. **Na pravem tiskalniku ni preverjeno**; mešana usmeritev
    je preverjena v PDF iz Chroma.
  - **Slog SVR** (9. 11. 2026, zahteva lastnika; `pomozno/izpisSvr`, `komponente/IzpisSvr`,
    `?slog=svr&oblikovanje=0`). Pri dogodkih **SV regije** (samo tam; `imaSlogSvr`) izbere organizator ob tisku
    med **Turnirko** (privzeto) in **SVR**: pokončni A4, ki posnemajo Excelove liste SV regije (vzorca 1. in
    5. OT SV regije, PDF iz LibreOffica). Po nivojih kot v vzorcu: listi skupin (dve skupini na list:
    križna tabela z rdečimi točkami 2/0, skor, točke = 2 × zmage, mesto, razpored po kolih »1. Kolo (1-4, 2-3)«
    s seti), nato list(i) žreba za vsa mesta (glavna pot, poti za 3., 5., 7. mesto …, oznake »A1« ob vstopu,
    izid v oklepaju za imenom zmagovalca, »bye«, »N. mesto«). Naslov lista je **ime turnirja**, nivo »I. SKUPINA«
    (rimsko do IX.; vzorec ima pri 4. nivoju arabsko, kar je Excelov spodrsljaj). Imena so »Ime Priimek (Klub)«
    brez predpone NTK/NTD. **Končna razvrstitev ostane izbirni del in je Turnirkov list** (vzorca je nimata), z
    lastno glavo. Kljukica »Točke po nizih« v SVR napolni stolpec »Točke« v razporedu skupin (v vzorcu je
    prazen); žreb točk nima.
    - **Dodatno oblikovanje** (kljukica, privzeto vključena; `?oblikovanje=0` ga izklopi): pas zgoraj
      (znamka Turnirko, kategorija, datum in kraj, logotip turnirja) in noga (pokrovitelji, »List i od n«). Brez
      njega je list enak vzorcu; z njim se vsebina po potrebi pomanjša (`merilo`) nad nogo.
    - **Cel list je en SVG v točkah** (viewBox 595,28 × 841,89 = 1 enota je 1 pt): HTML črte pod ~0,75 pt
      brskalnik zaokroži na slikovno piko, vzorec pa ima črte 0,4 in 0,53 pt. Mere so zapisane po vzorcu (konstante
      `SK` za skupine, `ZR` za žrebe: pisava 8,39 oz. 6,4 pt, ker je Excel vsaki strani nastavil drugo merilo;
      vrstice tabele 24 pt, ker ima vzorec 22,6 in 25,4). Izmerjeno s prekrivanjem PDF-ja vzorca in mojega
      (žreb se ujema do slikovne pike, tabela do višine vrstic).
    - **Žreb se rekonstruira iz `razpored` žreba in poišče tekme po paru igralcev** (`poti`, `odigraj`): enako
      drevo kot `SvRegijaStoritev.razdeli` (zmagovalci naprej, poraženec para i v pot za slabša mesta na mesto i,
      prosti prehod pusti prazno mesto poraženca). Stolpec poti: poraženec stoji v stolpcu kola, ki ga je
      izgubil (3. mesto v 2. stolpcu, 5. v 1.). Zato »bye« stoji na pravem mestu tudi v tolažilnih poteh, ki jih
      iz samih tekem ni mogoče sestaviti. Razmik med potmi: 18 pt za glavno, 27 pt med ostalimi in med žrebi.
    - **Dev harness** `/dev/izpis-svr` (podatki prepisani iz vzorca; `velik=16|32`, `deli=skupine|zreb`,
      `oblikovanje=0`) in `/dev/izpis-svr-stran/1` (prava stran z napolnjenim predpomnilnikom). Preverjeno tudi s
      pravim zaledjem: dogodek SV regije s 27 igralci, nivo 2 z žrebom treh igralcev v mreži 4 (prosti prehod).
      Primerjava z vzorcem: PDF → mupdf, prekrivanje (vzorec rdeče, moj izpis modro).
    - **Ni preverjeno**: pravi tiskalnik, Firefox/Safari, dogodki z več kot ~8 igralci v skupini (tabela raste v
      širino, zadnja stolpca gresta do roba), žreb 64.
