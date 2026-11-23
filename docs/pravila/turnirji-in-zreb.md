# Pravila: turnirji in zreb

> Turnirji: prikazni status, sistemi, izračun kroga, nosilci, dvojice, kategorije, ročni žreb, TOP, odjava, ekipni dogodek.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Status, ki ga vidi gledalec, ni vedno dejanski.** Zreb turnirja ali razpored
  lige pred datumom začetka je dejansko `V_TEKU` (prijave zaprte, popravek mogoč),
  gledalcu pa je »V pripravi« do tega dne (`StatusTekmovanja.zaPrikaz`, odločitev
  lastnika, okt 2026; za lige datum prvega kola). `TurnirDto`, `DogodekDto` in
  `LigaDto` nosijo prikazni `status`; `dejanskiStatus` nosi dejanskega, po katerem
  vmesnik odloča o kontrolah. Storitve in prehodi berejo SAMO dejanski status
  (entiteta), nikoli `zaPrikaz`.
- **Seznam turnirjev gostu in igralcu ne kaže turnirjev »V pripravi«, ki so
  več kot 7 dni daleč** (`VidnostTurnirja`, `GET /turnirji`; odločitev lastnika,
  23. 10. 2026 — koledar sezone prinese ~50 turnirjev za vse leto naprej in bi
  seznam ter sklop na domači strani zapolnil s praznimi). Admin in organizator
  vidita vse. Skrit ni zaklenjen: `GET /turnirji/{id}` in koledar ga kažeta.
  »V pripravi« je prikazni status (tudi žreb pred datumom); turnir brez datuma
  ostane. Varuje `VidnostTurnirjaTest`.
- **Sistem tekmovanja** izbere dogodek; `ZrebStoritev` po njem razveji žreb
  (`IZLOCILNI`/`KROZNI`/`SKUPINE_IZLOCILNI`/`SKUPINE`). Lestvice računa
  `RazvrstitevStoritev` (zmage → porazi → **krog**: medsebojne zmage, razlika
  nizov, nato razlika točk **samo iz tekem med izenačenimi**, rekurzivno),
  izločilni del po skupinah pa `SkupineStoritev` (generira se, ko so odigrane
  vse skupine — nujno pred preverbo zaključka dogodka; **samo** pri
  `SKUPINE_IZLOCILNI`). Izlocilno mrezo gradi ena metoda
  (`ZrebStoritev.zgradiIzlocilnoMrezo`), da jo delita oba sistema.
- **Izračun kroga je viden gledalcu** (nov. 2026; `KrogDto`, `SkupinaDto.krogi`,
  `MrezaDto.krogi`, `IzracunKrogaOkno`; odločitve lastnika). Pod lestvico vseh
  turnirskih sistemov s skupinami in krožnega sistema je gumb »Kako je bil
  izračunan krog?«, samo kadar je v lestvici krog; odpre okno (ne razpira se pod
  tabelo). Lestvica lige ima svoje pravilo in tega nima.
  - **Nastane sproti med razvrščanjem** (`RazvrstitevStoritev.ZapisKroga`) in se ne
    računa znova: vmesnik pravil ne pozna, zato razlaga ne more trditi nečesa,
    česar razvrstitev ni storila.
  - **Ena tabela, stolpci samo za merila, ki so odločala.** Kjer odločijo zmage,
    nizov ni; kjer nizi, točk ni. Enaki nizi se izpišejo, ker pojasnijo, zakaj
    štejejo točke. Enake zmage niso stolpec, ampak stavek nad tabelo. Prva
    pojavitev merila gre v njegov stolpec, ponovitev v manjšem krogu v »Med
    njimi« (dvojica: »Med njima« z izidom njune tekme). Krog dveh z eno tekmo je
    samo ta tekma, brez tabele. Tekme kroga so za »Pokaži tekme«.
  - **Oznaka `krog` zdaj pomeni: izenačeni z vsaj eno medsebojno tekmo** in ne več
    »katero od meril je koga razbilo«. Povsem izenačeni (cikel 3 : 0 brez vpisanih
    točk) so bili prej brez oznake, čeprav prav tam gledalec najbolj sprašuje;
    izračun pove, katero zadnje merilo je odločilo (`KrogDto.Opomba`).
    Izenačeni, ki se še niso srečali (začetek skupine), kroga nimajo.
  - Gumb je viden tudi med igranjem; okno takrat pove, da se izračun lahko
    spremeni (`vseTekmeOdigrane`).
  - Imena se v stavkih ne sklanjajo (ime je podatek). Testi: `IzracunKrogaTest`.
- **Žreb z nosilci je svoja storitev** (`NosilciStoritev`; 21. in 22. člen
  PST). `ZrebStoritev` samo prebere jakostni vrstni red (`IzborStoritev`), ga
  zabeleži v `Prijava.stNosilca` in iz razporeditve sestavi tekme:
  - **Skupine se polnijo po jakostnih pasovih.** Prvi pas se **ne žreba** —
    1. nosilec je prvi zapisani v skupini 1, 2. v skupini 2 … N-ti v N-ti skupini.
    Vsak naslednji pas (N+1…2N, 2N+1…3N …) se žreba, po enega igralca v vsako
    skupino; nepoln zadnji pas dobijo **naključne** skupine (te so večje).
    Razpored znotraj skupine je nato krožna metoda **brez mešanja**
    (`kroznePare`), zato nosilec skupine začne z najšibkejšim in dvoboj 1–2
    pade v zadnje kolo — isto kot pri formatu TOP.
  - **Največ 4 igralci v skupini** (računalniški žreb `SKUPINE_IZLOCILNI`, od
    23. 11. 2026): `ZrebStoritev.izberiSteviloSkupin` vzame n/4 **navzgor**
    (`NAJVEC_V_SKUPINI`), zato so skupine po velikosti enake ali se razlikujejo
    za enega (4 in 3). 65 igralcev = 17 skupin (14 po 4, 3 po 3); prej je
    zaokroževanje na najbližje dalo 16 skupin in eno s petimi (n % 4 == 1).
    Izjema ni nobena: pri 5 igralcih sta dve skupini (3 + 2). Isto število
    velja za skupine za mesta, kadar organizator števila ni vpisal; ko ga je,
    ostane njegovo. Ročni vpis skupin (`RocniSkupineOkno`) skupine s 5+
    igralci le **opozori**, ne zavrne; format TOP in SV regija imata velikost
    skupin namenoma po svoje.
  - **Skupine so oštevilčene od 1** (odločitev lastnika, 23. 11. 2026; prej črke):
    »Skupina 1«, »Skupina 2« … do števila skupin (`skupina.oznaka` = `1`, `2` … `17`).
    Oznako daje **ena** funkcija, `OznakaSkupine.zaIndeks` (zaledje) z zrcalom
    `oznakaSkupine` v vmesniku (`pomozno/oznakaSkupine.ts`); nobeno mesto, ki skupine
    poimenuje (žreb, ročni žreb, TOP, predogled izbora, oba uvoza), ne sme več računati
    črke (`'A' + i`). **Izjema je SV regija**: tam je »Skupina 1, 2 …« nivo, zato bi
    številke znotraj nje (»Skupina 1, skupina 1«) zamenjale pomen; skupine v nivoju
    ostanejo črke po slovenski abecedi brez Č, Š, Ž (A … P, **R**, S, T, U, V, Z; po P
    pride R, ne Q; nad 22 se pred črko doda Z: ZA, ZB …), `OznakaSkupine.crkaPoVrsti`.
    Pasti:
    - **Oznaka je besedilo**, zato navadni `ORDER BY oznaka` da »10« pred »2« in bi
      pomešal nosilce izločilnega dela (zmagovalec skupine N je N-ti nosilec). Skupine
      zato vedno razvršča `SkupinaRepozitorij.findByDogodekIdOrderByOznakaAsc` (JPQL:
      najprej krajša oznaka, nato abecedno; ime metode je ostalo, da klici veljajo
      naprej), vmesnik pa `primerjajSkupine` (`localeCompare` z `numeric: true`). Vsako
      novo razvrščanje po oznaki mora uporabiti isto pravilo.
    - **Že zapisanih oznak ne preimenujemo** (migracije ni): starejši dogodki imajo še
      A, B … (in Q pri 17+ skupinah). Ponovni žreb ali ročni vpis jih zamenja s
      številkami; do tedaj so v bazi črke in številke hkrati, razvrščanje (dolžina,
      oznaka) to prenese.
    - Višje stopnje pri uvozu iz Stupe so `2-1`, `2-2` (ne `21`, ki bi se zlilo z 21.
      skupino prve stopnje); skupine za mesta imajo `M5`, `M9` …
    - Meja števila skupin pri računalniškem žrebu je 999 (stolpec dovoli 3 znake).
    Testi: `OznakaSkupineTest`, `ZrebNosilciTest.skupineSoOstevilceneOdEneInUrejenePoVrednosti`
    (65 igralcev = 17 skupin v vrstnem redu 1 … 17), `oznakaSkupine.test.ts`.
  - **Mreža: 1. nosilec na vrh, 2. na dno** (ta dva se ne žrebata), 3. in 4.
    na svoji četrtini (s prvima dvema se srečata šele v **polfinalu**), 5.–8.
    na svoje osmine (**četrtfinale**), 9.–16., 17.–32. … enako po pasovih, ki
    se podvajajo. Nosi ga `NosilciStoritev.seedVrstniRed`; **obrat pri lihem
    indeksu** je tisto, kar 2. nosilca potisne na dno in ne na sredino mreže
    (prej je bilo tako). Prosta mesta zasedejo najvišje številke, zato jih
    dobijo najvišji nosilci in se dve nikoli ne srečata.
  - **Izločilni del po skupinah**: zmagovalec skupine 1 je 1. nosilec, skupine 2 2. …
    — skupine so nastale iz pasov, zato je njihov vrstni red hkrati vrstni
    red nosilcev. **Nepoln zadnji pas zmagovalcev se žreba na vsa mesta
    svojega pasu** (Priloga C PST): pri 5 skupinah gre E1 na eno od mest
    5.–8. (dve zgoraj, dve spodaj). Do 16. 11. 2026 je pas obsegal le toliko
    mest, kolikor je bilo zmagovalcev, zato je bil E1 vedno na mestu 5.
    nosilca (zgoraj) in E2 vedno spodaj (1. OT Muta); enako pri 3 skupinah
    (C1 vedno spodaj) in 7 skupinah. **Prosti krog gre za zmagovalcem**:
    vsaka polovica ima enako igralcev, zato enako prostih mest; v polovici
    jih dobijo najprej zmagovalci po nosilskem vrstnem redu, preostale
    izžrebani drugouvrščeni. `vMrezoIzSkupin` zato vrne seznam dolžine cele
    mreže (`null` = prosto), ne le udeležencev. Odločitve lastnika (16. 11.):
    klubska ločitev v 1. kolu izločilnega dela **ostane** (čeprav PST 22.
    člen omejitev ne predvideva), v skupinah tudi; nosilci skupin ostanejo
    **fiksni** (3. v C, 4. v D …, ne žreb po 21. členu). Testi:
    `ZrebNosilciTest` (`petSkupinZmagovalecEPadeVObePolovici` …).
    Drugouvrščeni se žrebajo na preostala mesta, vsak v
    **nasprotno polovico** od zmagovalca svoje skupine, zato se soigralca iz
    iste skupine srečata šele v **finalu**. Iz tega samo od sebe sledi, da
    zmagovalec skupine v 1. kolu nikoli ne igra z drugim zmagovalcem skupine
    (ali je prost ali igra drugouvrščenega). Polovici vzideta natanko, ker
    ima vsaka skupina drugouvrščenega: v vsaki polovici je toliko igralcev,
    kolikor je skupin. `uravnoteziPolovici` premakne drugouvrščenega le, ko
    kateri skupini drugouvrščeni manjka.
  - **Klubska ločitev** (v 1. kolu mreže in znotraj skupine) je **mehko**
    pravilo: če je igralcev enega kluba preveč, se ji ni mogoče izogniti.
    Zato žreb ni iskanje po pravilih, ampak več naključnih poskusov, med
    katerimi obvelja tisti z **najmanj trki**; prvi brez trka konča iskanje.
  - **Dvojice so izvzete**: para ni mogoče jakostno umestiti (rating para ne
    obstaja, dvojice v rating ne štejejo), zato se žrebajo povsem naključno in
    `stNosilca` ostane prazen.
  - **Jakostni vrstni red je viden in urejljiv povsod, kjer žreb pozna
    nosilce** (`IzborDto` v `GET /dogodki/{id}`, `PUT /dogodki/{id}/vrstni-red`).
    Brez tega bi igralec **brez ratinga** — ta gre po pravilu `IzborStoritev`
    na **vrh** predloga — tiho postal 1. nosilec. Zastavica `crtaReza` loči
    format TOP (pod črto so rezerve) od ostalih sistemov (igrajo vsi).
  - **Lestvica ima jakostno mesto kot predzadnje merilo** (pred abecedo,
    `RazvrstitevStoritev`): dokler skupina ni začeta, so vsi izenačeni na 0 in
    lestvica je hkrati **izpis skupine** — po abecedi bi nosilec skupine
    pristal sredi seznama, čeprav je po pravilih žreba prvi zapisani.
- **Dvojice niso sistem, ampak disciplina dogodka** (V14). Igrajo isto
  izločilno mrežo kot posamično tekmovanje, zato shema in `TurnirjiStoritev`
  zavrneta `DVOJICE` s katerim koli drugim sistemom (krožni in skupine bi
  potrebovali še lestvice parov, ki jih ni).
  - **Tekmovalna enota je `Prijava`, ne igralec.** Par je ENA vrstica z
    `igralec` + `igralec2` (vsak s svojim posnetkom kluba in ratinga ob žrebu).
    Tabele `tekma` zato **ni** bilo treba spreminjati in mreža, napredovanje,
    vnos rezultatov, listki in končna mesta tečejo po nespremenjeni kodi.
    Ne uvajaj vzporedne tabele parov — s tem bi se razcepilo vse zgoraj.
  - **Prijava je posamična, par sestavi organizator** (`poveziVPar` /
    `razdruziPar`, `POST /dogodki/{id}/pari` in
    `POST /dogodki/pari/{id}/razdruzi`). Povezava **izbriše** vrstico drugega
    igralca (pred žrebom nanjo ne kaže nobena tekma), razdružitev jo ustvari
    nazaj. Zato: odjava para je zavrnjena (odjavila bi dva človeka hkrati —
    najprej razdruži), soigralca pa je treba ob ponovni prijavi iskati še v
    drugem stolpcu (`najdiPoSoigralcu`) — `UNIQUE (dogodek, igralec)` ga ne
    pokriva, ker svoje vrstice nima.
  - **Žreb zavrne prijavo brez soigralca** (`preveriSestavljenePare`). Tihi
    izpust bi pomenil, da organizator prijavljenega v mreži zaman išče.
  - **Dvojice ne štejejo v rating in ne v posamično statistiko** — izida para ni
    mogoče pripisati posamezniku (isto pravilo kot pri ligaških dvojicah).
    Varujeta ga `TekmaStoritev.vnesiRezultat` in `RatingStoritev` sam. Vsaka
    poizvedba v `TekmaRepozitorij`, ki hrani **statistiko posameznika**
    (`najdiZaIgralca`, `najdiDvoboje`, `nasprotniki`, `idjiZOdigranoTekmo`,
    `izidiVsehOdigranih`, `najdiZadnje`), mora imeti
    `d.disciplina = POSAMICNO` — brez tega bi zajela še prvega člana para in
    drugega tiho izpustila. Poizvedbe o **poteku tekmovanja**
    (`najdiZadnjeVsakegaTurnirja`, `zmagovalciPoTurnirjih`) dvojice nasprotno
    vključujejo in naložijo oba igralca. Profil ima za dvojice svoj razdelek
    (`ProfilDto.dvojice`, `najdiDvojiceZaIgralca` — igralca išče v vseh
    štirih mestih tekme).
- **Spolne kategorije dogodka so štiri** (V14): `MOSKI`, `ZENSKE`,
  `MESANO` = **strogo mešan par** (moški + ženska) in `KDORKOLI` = odprto.
  `MESANO` je pravilo o **sestavi para**, zato ga preveri šele povezava para
  (`preveriMesanPar`), pri posamičnem dogodku pa sploh ni izbira — CHECK v
  shemi ga zaveže na `DVOJICE`. Do V14 je `MESANO` pomenil »kdorkoli«, zato
  je migracija obstoječe dogodke prepisala v `KDORKOLI`. **Tabele `liga` se
  to ne dotakne**: tam `MESANO` še naprej pomeni ligo, v kateri igrajo oboji,
  in `KDORKOLI` ni izbira (obrazec lige našteje svoje tri možnosti sam).
- **Ročni žreb skupin in izločilne mreže** (okt 2026). Žreb skupin (vsi sistemi z
  jakostnimi skupinami, razen SV regije, ki ima svoj vpis) lahko organizator vpiše
  ročno: vsak igralec dobi skupino **in pozicijo v skupini** (`RocniClanVnos.pozicija`,
  1 = najmočnejši v svoji skupini). **Pozicija ni nosilec turnirja** — vsaka skupina ima
  svoje 1., 2., … mesto, in pozicije so v skupini brez lukenj. Strežnik iz njih izpelje
  `Prijava.stNosilca` (najprej pozicija, nato skupina 1 pred 2; rezerve po ratingu).
  Vpis konča v isti točki kot zreb (`ZrebStoritev.zapisiSkupineIzlocilni`), zato tekme in
  napredovanje tečejo po nespremenjeni kodi; predlog (`POST …/skupine/predlog`) je zreb
  brez zapisa in vrne pozicije (rezerve brez pozicije).
  - **Izločilni del po skupinah NE nastane sam** (`SKUPINE_IZLOCILNI`, tudi SV
    regija): ko se skupine končajo, organizator izbere računalniški žreb ali ročno
    mrežo (`IzlocilniZrebStoritev`, `POST …/izlocilni/zreb`, `PUT …/izlocilni/mesta`).
    Dokler ni izbire, dogodek **ni zaključen** (`TekmaStoritev.zakljuciDogodekCeKoncan`
    in `SkupineStoritev.imaIzlocilniDel`); pri SV regiji to varuje `vsiZrebiZgrajeni`.
    Razlog: organizator naj ima pred žrebom možnost izbire (računalniški žreb ali
    ročna mreža), ne da se mu izločilni del zgradi sam.
  - **Popravek do prve tekme:** skupine, mreža in zrebi nivojev se smejo ponovno
    zrebati ali vpisati, dokler nobena tekma ni začeta (`ZrebStoritev.razveljaviZreb`,
    SV: `razveljaviZrebSkupin`). »Začeta« je `V_IGRI` ali `KONCANA` **razen** prostega
    prehoda (`IzidTekme.PROSTO`, ta je koncan ob nastanku) — `ZrebStoritev.zacet`.
    Tekma, poklicana na mizo (M1, `docs/pravila/mize.md`), je `V_IGRI` in torej
    začeta; po preklicu klica spet ni.
    Brisanje tekem gre skozi `izbrisiTekme`, ki najprej pretrga izvorne povezave.
  - **Mreža:** sosednja mesta igrata v prvem kolu; dva prosta mesta drug ob drugem
    ne smeta biti (`ZrebStoritev.mestaZaRocniVpis`). Prosta mesta so `null`.
  - **Oknu sta prenovljeni delovni ploskvi** (23. 11. 2026, predaja
    `design_handoff_skupine_mreza_listki`, DESIGN.md 5k; logika in zaledje nespremenjena):
    - **»Popravi skupine« / »Ročni vpis skupin«** (`RocniSkupineOkno`): vse skupine naenkrat,
      igralca zamenjata dva klika (prvi izbere, drugi zamenja; Escape najprej prekliče izbiro),
      prosto mesto sprejme izbranega (`nastaviSkupino`: na konec skupine, stara skupina zapre
      vrzel). Stanje v nogi velja prvo, ki drži: izbran igralec, kdo brez skupine, skupina z manj
      kot 2, vse v redu; Shrani je onemogočen pri napaki, med izbiro in pri popravku brez sprememb.
      »Premaknjen« (modri levi rob, števec) se pokaže samo pri popravku (izhodišče so trenutne
      skupine); pri ročnem vpisu je izhodišče predlog, ki se naloži ob odprtju, in »Razveljavi
      spremembe« vrne nanj. Pri TOP je bazen »Rezerve« in ni napaka, koračnik je onemogočen.
      Skupina s petimi pri skupine + izločilni ne ustavi shranjevanja (svarilo v nogi).
    - **»Popravi mrežo« / »Ročna mreža«** (`RocnaMrezaOkno`, `ModalnoOkno celZaslon`): popravek je
      mreža, ki že ima glavne tekme (`RocnaMrezaZaDogodek`). Mesto na mesto zamenja (vlečenje ali
      Enter/preslednica), odlagališče naredi mesto **prazno** (`'prazno'`, samo v odjemalcu, ne
      `null`: prosto mesto je prosti prehod, prazno čaka na igralca); dokler je kaj praznega,
      Shrani ni mogoč. Nosilec (črna značka) je `stNosilca <= velikost mreže / 4`; pri izločilnem
      delu skupin namesto značke stoji uvrstitev »3 · 1.« (`udelezenciPoId`). Do 16 mest je
      mreža vidna cela, brez stolpca četrtin.
- **`SKUPINE` = format TOP** (npr. TOP 24 kot 3 skupine po 8):
  - igra najboljših N prijavljenih (N = `steviloSkupin` × `velikostSkupine`);
    kdor ne pride v izbor, dobi status `REZERVA` in ne igra;
  - razporeditev je **zaporedna po jakosti** — skupina 1 dobi mesta 1–8, skupina 2
    9–16 …; skupine so torej **rangi**, ne uravnotežene skupine;
  - jakostni vrstni red je edino merilo in živi v `Prijava.stNosilca`.
    Predlog naredi `IzborStoritev` po Turnirko ratingu, igralci **brez ratinga
    gredo na vrh** (da jih človek zavestno uvrsti); admin ga uredi pred
    žrebom, žreb pa mesta zabeleži vsem, tudi če jih ni urejal;
  - če prijav ni dovolj, je **zadnja skupina manjša** (`velikostiSkupin`);
    skupina z enim samim igralcem se zavrne (`zadrzekRazreza` — isto pravilo
    uporabi predogled v pripravi, da razsodita enako);
  - razpored v skupini je krožna metoda **brez mešanja** (`kroznePare`), zato
    prvi nosilec začne z najšibkejšim, dvoboj 1–2 pa pade v zadnje kolo;
  - **izločilnega dela in skupne razvrstitve čez skupine ni** — `koncnoMesto`
    ostane prazen, vsak dobi le `mestoVSkupini`.
- **Odjava pred žrebom** (`TurnirjiStoritev.odjavi`) je **mehak izbris** —
  prijava ostane v bazi s statusom `ODJAVLJEN` (tabela ima `UNIQUE
  (id_dogodek, id_igralec)`). Zato **ponovna prijava** (`prijaviIgralce`)
  odjavljeni zapis **aktivira** (nazaj na `PRIJAVLJEN`, klub posname znova),
  ne ustvari dvojnika; za druge statuse ostane napaka »je že prijavljen«.
  Vmesnik zato v seznamu »Dodaj igralce« izloči le **ne-odjavljene** prijave.
- **Ekipni dogodek turnirja** (V28, disciplina `EKIPNO`): **tekmovalna enota je
  prijava z ekipo** (`prijava.id_ekipa`, isto kot par pri dvojicah), zato žreb,
  mreža, skupine, napredovanje in končna mesta tečejo po nespremenjeni kodi.
  Ekipa in kader pripadata dogodku (`EkipeDogodkaStoritev`); igralec sme biti v
  kadru **ene** ekipe dogodka; žreb zahteva kader vsaj velikosti formata
  (`FormatSrecanja.getStIgralcev`). Vsaka ekipna tekma z obema ekipama ima
  **natanko eno srečanje** (`EkipneTekmeStoritev.zagotoviSrecanja`) — tam
  postava in posamične tekme po formatu dogodka; **izid tekme nastane iz
  srečanja**, neposredno sta dovoljena samo `BREZ_BOJA` in
  `DISKVALIFIKACIJA`. V rating gredo posamične tekme srečanja, ekipna tekma
  nikoli. **Skupine za mesta** (`SKUPINE_ZA_MESTA`, PST 14. člen): skupina ima
  `stopnja`, `ime` in `prvo_mesto`; dvoboj iz predtekmovanja se v finalno
  skupino prenese (`tekma.id_prenesena`) in se ne igra ter ne šteje znova.
  Tekma za 3. mesto je `TOLAZILNI` z izvorom `PORAZENEC` iz polfinal.
