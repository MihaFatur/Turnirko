# Pravila: rating

> Turnirko rating v zaledju: raven, sidra, novinci, vrnitev, dnevnik, lestvice, neaktivnost, preračun, obračun.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- Rating se spreminja izključno prek `RatingStoritev` (dnevnik v `rating_zgodovina`).
- **Vsako tekmovanje ima RAVEN, ta pa težo v ratingu** (`RavenTekmovanja`,
  `turnir.raven` in `liga.raven`, V20 — nadomešča prejšnjo zastavico
  `steje_v_elo`): `URADNO` 1,00 (NTZS) · `KLUBSKO` 0,75 (Savinja liga/tour) ·
  `REKREATIVNO` 0,50 · `NE_STEJE` 0. Teža **množi K** in s tem spremembo
  ratinga.
  - **Zakaj ne le da/ne:** zmaga na članskem turnirju NTZS in zmaga na
    rekreativnem turnirju nista enako vredni informaciji. Če oboje šteje enako,
    dober rekreativec prehiti nekoliko slabšega igralca, ki hodi na članske
    turnirje — to pa ni res.
  - **Teža je last TEKME, ne igralca**, zato je za oba enaka in vsota sprememb
    pri enakem K ostane 0.
  - **Teža NE vpliva na števec odigranih tekem**: rekreativna tekma je ena
    tekma, le rating premakne za polovico. Tako »30 tekem« res pomeni 30 tekem.
  - **Privzetek entitete je `KLUBSKO`** — kar nastane v aplikaciji, je klubsko;
    uradnega statusa si klub ne more podeliti sam. Uvoz NTZS
    (`PreslikavaTurnirjaStupe`, `PreslikavaLigeStupe`, `Stara*Uvoz`) zato raven
    izrecno nastavi na `URADNO`. **Testi,
    ki preverjajo številke ratinga, morajo tekmovanju nastaviti `URADNO`**,
    sicer so pričakovane vrednosti tri četrtine nečesa.
  - Obračun preskoči `NE_STEJE` na treh mestih: `RatingStoritev` (zadnja
    obramba), klicalca `TekmaStoritev`/`SrecanjeStoritev` in poizvedbi
    `ratinskeTekme` za ponovni preračun. Ligaške dvojice ne štejejo nikoli.
- **Sistem se imenuje »Turnirko rating«** — tako v kodi, v bazi in v vmesniku.
  V bazi je to vrednost stolpca `sistem` obeh ratinških tabel: **`TURNIRKO`**
  (prej `KLUBSKI_ELO`, preimenovano v V23). Stolpec je odprto besedilo zato, da
  jih zna baza hraniti več (kasneje npr. točke NTZS), zato vsaka poizvedba
  filtrira po `RatingStanje.SISTEM_TURNIRKO` in nikoli po prazni vrednosti.
  - **»Elo« ostane samo tam, kjer gre res za šahovski sistem** — logistična
    formula in delitelj 400 sta njegova, zato ju komentarji tako tudi imenujejo.
    Turnirko rating je Elo **z dodatki** (teža tekmovanja, starostno sidro,
    uvrstitev novinca, odbitek za neaktivnost), ne Elo sam.
  - **Past pri preimenovanju:** slovenščina je polna besed s črkami »elo«
    (celo, delo, telo, zelo, celota, delovni, tabelo, prelom), zato je vsako
    iskanje in zamenjava po vzorcu `elo` nevarna. Preimenuj po
    identifikatorjih in celih besednih zvezah, z mejo besede — in **nikoli ne
    poženi zamenjave čez že uveljavljene migracije**: Flyway preverja kontrolno
    vsoto celotne datoteke, zato podre zagon že popravljen komentar.
- **Turnirko rating (`TurnirkoRatingStoritev`)** ima lastnosti, ki jih ne razbij:
  - **K po negotovosti** (`kFaktor`): osnova **48**, nanjo se **seštevajo**
    pribitki po +10 za vse, česar o igralcu še ne vemo — manj kot 30 tekem,
    manj kot 10 tekem in prvih 15 tekem po vrnitvi. Novinec ima torej 68,
    vrnjeni novinec 78, ustaljen igralec 48; K je za oba igralca lahko različen.
    **Vrednosti niso ugibane**: umerjene so na pravih tekmah zgodovine NTZS po
    metodi »napovej, nato posodobi« (prequential log-loss). Do oktobra 2026 je
    bila osnova 40 (+8) s set-margino; brez margine je K 40 napovedoval slabše
    (log-loss od 2024: K ×1,0 0,3951, ×1,2 0,3930, ×1,4 0,3922, ×1,6 0,3922),
    lastnik je izbral ×1,2, ker so skoki blizu prejšnjih (povprečno 13 namesto
    11,8 točke, največ 57 namesto 74). Meri `UmeritevKPoskus` (test, ki teče
    samo nad podano kopijo baze). Kdor parametre spreminja, naj jih izmeri, ne
    ugane.
  - **ničvsotno zaokroževanje** z `Math.rint` (pol na sodo), ne `Math.round`:
    pri **enakem** K je vsota sprememb natanko 0 tudi ob izenačeni napovedi
    (`Math.round` bi pri lihem K ob vsakem izenačenju vbrizgal +1). Pri
    različnem K (novinec proti ustaljenemu) vsota namenoma ni 0.
  - **Zmaga je zmaga** (odločitev lastnika, 30. 9. 2026): izid v nizih na
    spremembo **ne vpliva** — 3 : 0 in 3 : 2 prineseta isto, 0 : 3 in 2 : 3
    vzameta isto. Sprememba je `K × teža × (izid − pričakovano)`;
    `izracunaj` nizov sploh ne sprejme, zmagovalec je podan izrecno (predaja
    pri izenačenih nizih, uvožena tekma z zapisanim samo zmagovalcem 0 : 0).
    Prej je K množila »set-margina po presenečenju« (moč 1,25, meji
    0,35–1,65) in je potrebovala izjeme za predajo in tekme brez nizov; z
    njenim odhodom so vse odigrane tekme obračunane po istem pravilu. Stolpec
    `rating_zgodovina.margina` je odstranila V38. Pravilo drži
    `RazclenitevSpremembeTest.zmagaJeZmagaNeGledeNaNize` (3 : 0, 3 : 2,
    predaja in 0 : 0 dajo isto). Kdor bi margino vračal, naj izmeri, koliko
    napoved res izboljša.
- **Novinec ne začne pri 1000, ampak pri STAROSTNEM SIDRU**
  (`starostno_sidro`, `SidroStoritev`, V21): mediana ratinga umerjenih in
  aktivnih igralcev istega spola in starosti. Brez tega se dva novinca v U11,
  ki igrata med sabo, ustalita pri isti številki kot dva novinca v U19 — kar
  ni res.
  - **Starost je po 11. členu PST** (`StarostniPas.letaVSezoni`) in merjena na
    dan TEKME, ne danes — sicer ponovni preračun ne bi dal istih številk.
  - **Beri ga v dveh delih.** **Oblika** (razlike med starostmi in spoloma) je
    izmerjena — ta reši U11/U19. **Raven** (pribitek vsem vrsticam spola:
    +270 v V21, nato v V40 še **moški +64, ženske +93**) je merilo skale in ne
    meritev moči: Elo nima absolutne skale, raven lestvice določajo izključno
    vstopne vrednosti novincev. Pri goli mediani lestvica pade (izmerjeno na
    91.741 tekmah: povprečje 965 → 703, dno se zabije v mejo 100); s pribitkom
    ostane (965 → 968, najnižji 145). **Ob vsaki spremembi formule ali
    osvežitvi sidra je treba raven izmeriti znova** — ni ugibanje in ni okras.
    Tako je nastal V40 (oktober 2026): odhod set-margine in K 48 sta skalo
    spustila za 11 točk, odprava tekem z datumom 1. 1. 1900 (lige 2024/25,
    obračunane pred vso zgodovino, ko so bili vsi njihovi igralci novinci) pa
    še za ~50 (moški) oz. ~70 (ženske) — tudi +270 je bil izmerjen na bazi s
    temi tekmami. V40 vrne povprečje vsake lestvice tja, kjer je bilo v
    produkciji (moški 974,3 → 974,4, ženske 886,6 → 886,8). Lestvici sta
    ločeni, zato ima vsaka svoj pribitek. Merilo: povprečje vseh igralcev z
    ratingom brez odbitkov za neaktivnost po popolnem preračunu.
  - **Tabela je POSNETEK in se NE sme izpeljevati iz lastnega izhoda.** Sidro,
    izračunano iz lestvice, ki so jo oblikovali novinci, zasidrani po prejšnjem
    sidru, je povratna zanka. Osvežitev je zavestna odločitev, ne opravilo v
    ozadju.
  - Surova mediana po letih je sunkovita in ponekod pada z leti; seme V21 je
    zato zravnano (drseče okno ±1 leto, monotono nepadajoče, odrasli 26+ ena
    vrednost z mostom od 21. leta).
  - **Rekreativni vstop** (V36, `igralec.rekreativni_vstop`, kljukica
    »Rekreativec — začetni rating 800« v obrazcu igralca): kdor je ob vpisu
    označen, začne pri `SidroStoritev.REKREATIVNI_ZACETEK` (800) namesto pri
    sidru. Sidro je mediana registriranih igralcev NTZS (odrasel moški 1597) in je
    za igralca iz rekreacije 700–900 točk previsoko: v Savinja ligi so
    novinci, ki so prvi večer igrali samo novince, obstali pri 1500–1700 (47.
    mesto od 459 aktivnih moških). 800 je **odločitev lastnika**, ne meritev.
    - Odloča **ena metoda** (`SidroStoritev.zacetniRating`), zato 800 velja
      povsod, kjer se bere sidro: vstopna vrednost, **izhodišče uvrstitve
      novinca** (navidezni remiji so proti 800) in napoved tekme. `postavljen`
      se ne prižge — uvrstitev prvega dne še vedno teče po izidih.
    - **Ni zastavica lestvice rekreativcev** (`RekreativecStoritev`, izpeljana
      iz treh tekem na uradnih/klubskih tekmovanjih). Zato ime `rekreativni_vstop`
      in ne `rekreativec`.
    - Oznaka velja ob **prvi** tekmi. Igralec, ki tekme že ima, dobi nov začetek
      šele s preračunom od dneva prve tekme — samodejno ga urejanje ne sproži
      (admin označi več igralcev in požene en preračun); obrazec to pove.
    - V javni DTO ne gre (samo `IgralecDto` za `/podrobno`); nastavi pa jo lahko
      tudi organizator ob vpisu igralca. Drži `RekreativniVstopTest`.
  - **Poizvedba uvrstitve potrebuje `idx_rating_zgodovina_igralec_velja`**
    (V21). Brez njega sqlite samopovezavo dnevnika zažene z nasprotnikove
    strani in za vsako poizvedbo prebere vseh 183 tisoč vrstic: 2,7 s namesto
    0,8 ms, celoten preračun ure namesto minut. Iz istega razloga sta turnirska
    in ligaška različica LOČENI poizvedbi — `OR` med njima indeks ubije.
- **Uvrstitev novinca po prvem dnevu igranja** (`UvrstitevNovinca`): dokler
  igralec igra svoj prvi dan, se rating ne sešteva po tekmah, ampak se **vsakič
  znova izračuna** iz vseh izidov tega dne — tisti rating, pri katerem je vsota
  pričakovanih izidov enaka vsoti dejanskih, plus **dve navidezni izenačeni
  tekmi proti sidru** (regularizacija). Močan novinec tako po prvem turnirju ne
  kotira prenizko in šibek ne previsoko.
  - **Prvi dan = prvi turnir**: turnirska tekma dobi datum turnirja, zato imajo
    vse tekme enega turnirja isti `velja_ob`. Mejo nosi
    `rating_stanje.prva_tekma_ob`.
  - **Prve tekme se ne uvršča** (pri eni tekmi je ugibanje večje od podatka),
    od druge naprej da.
  - **Nasprotnikova sprememba ostane navadna** — zaslužil si jo je proti
    ratingu PRED tekmo. Uvrstitev popravi samo novinčevo številko.
  - **Nizov uvrstitev ne upošteva**: šteje, koga je premagal. To je tudi
    podatek, ki ga zna igralec sam preveriti.
  - **`rating_stanje.postavljen` uvrstitev ustavi.** Kdor ima rating od
    človeka (`nastaviZacetniRating`, kasneje zunanja uvrstitev za redke goste),
    ni novinec — prvi turnir pri nas o njem pove manj kot postavitev.
  - **`rating_zgodovina.tocke`** (1/0, V21) pove, ali je igralec tekmo dobil.
    Iz spremembe točk se to ne da zanesljivo prebrati (sprememba je lahko 0),
    uvrstitev pa izide potrebuje.
- **Vrnitev po odsotnosti (`SledilnikVrnitve`, V19).** Kdor po več kot
  **12 mesecih** spet igra, ima **prvih 15 tekem** K večji za 10 — v letu dni se
  človek preveč spremeni (mladinec zraste, po poškodbi forma pade), da bi stara
  številka še veljala. Stanje nosita `rating_stanje.zadnja_tekma_ob` in
  `preostanek_vrnitve`.
  - **Pravilo živi na enem mestu**, ker ga potrebujeta dva: redni obračun
    (iz shranjenega stanja) in ponovni preračun, ki stanje obnovi tako, da vse
    čase igralčevih tekem po vrsti spusti skozi isti sledilnik. To je edini del
    stanja, ki se ga iz zadnje vrstice dnevnika **ne da** prebrati — je
    posledica celotnega zaporedja.
  - **Tekma brez datuma (`BREZ_DATUMA`) vrnitve ne sproži in ne premakne
    zadnjega termina**: o njej ne vemo, kdaj je bila, zato bi sicer vsaka
    uvožena tekma brez datuma naslednjo pravo tekmo označila za »vrnitev po
    stotih letih«. Iz istega razloga gre zadnji termin samo naprej — rezultat
    se lahko vnese tudi za nazaj.
- **Vrstica dnevnika razloži svojo številko** (`rating_zgodovina.k`,
  `teza`, `pricakovano`, V24; `margina` je odšla z V38): **+25 = K 78 ×
  0,75 (teža) × (1 − 0,58)**. Sestavine gredo v `TockaGrafa.razclenitev`, a jih
  vmesnik pod grafom **ne izpiše** (odločitev lastnika, september 2026 —
  gledalcu je bil obrazec šum); poved dobijo samo načini brez obrazca.
  - **Sestavine se ZAPIŠEJO in se ne računajo nazaj.** K je odvisen od števila
    tekem IN od tega, ali se je igralec takrat vračal; težo tekmovanja lahko
    kdo vmes spremeni. Poznejši izračun bi torej dal današnje številke za staro
    tekmo in bi lahko protislovil zapisani spremembi.
  - **K in `pricakovano` sta last IGRALCA, `teza` pa TEKME.**
    Vsak igralec ima svoj K; pričakovani izid drugega je 1 minus prvega.
  - **Prazne so, kadar sprememba ne nastane po tem obrazcu**: postavitev in
    odbitek (tekme ni) ter **uvrstitev novinca**, kjer se rating izračuna znova
    iz vseh izidov prvega dne. Vrstica, ki ima tekmo in nima `k`, je torej
    natanko uvrstitev — a vmesnik tega ne ugiba: strežnik pošlje
    `ProfilDto.NacinSpremembe` (`KORAK`, `UVRSTITEV`, `POSTAVITEV`,
    `NEAKTIVNOST`, `ZUNANJA_UVRSTITEV`) in vsak način razen `KORAK` ima svojo
    poved.
  - **Za nazaj jih napolni ponovni preračun**, ne migracija: sestavine so
    izpeljanka iz zaporedja tekem in jih pozna samo obračun.
  - Test `RazclenitevSpremembeTest` drži edino obljubo, ki šteje: zapisane
    sestavine se **zmnožijo natanko v zapisano spremembo**.
- **Lestvic je VEČ in niso ena.** Spol ni filter, ampak **izbira lestvice**:
  med moškimi in ženskami ni niti ene obračunane tekme (0 od 91.741), zato sta
  skali neprimerljivi in skupno mesto ne pomeni ničesar. Enako velja za
  rekreativce (`RekreativecStoritev`): rekreativec je, kdor še ni odigral
  **3 tekem** na tekmovanju ravni `URADNO` ali `KLUBSKO`; prehod je **trajen**.
  - **Premik mesta se šteje LE znotraj skupine** (spol × tekmovalci/rekreativci)
    — sicer bi se ženski del lestvice premikal zato, ker je kdo v moškem delu
    zmagal.
  - **Isto velja za profil igralca:** »Okolica na lestvici« in »Do N. mesta«
    (`izracunajOkolico` v `ProfilStran`) štejeta lestvico igralčevega spola in
    njegove skupine (tekmovalci/rekreativci), ne skupne — številka v glavi
    profila (`ProfilStoritev.uvrstitev`) je merjena po isti lestvici in se z
    »Do N. mesta« mora ujemati.
  - **Zastavica rekreativca se IZPELJE iz dnevnika in se ne hrani.** Število
    tekmovalnih tekem je stvar zgodovine, ki se ne more zmanjšati; hranjenje bi
    zahtevalo še eno polje, ki ga mora preračun obnoviti. Poizvedba seže do
    `turnir.raven` oz. `liga.raven`, zato sledi tudi poznejši spremembi ravni.
  - **Izpeljava teče ob VSAKEM ogledu lestvice in profila, zato mora biti
    poceni — to je pogoj, da je zastavica lahko neshranjena.** Štetje
    (`RatingZgodovinaRepozitorij.tekmovalnihTekem`) je **ena** poizvedba čez
    vse tri vire (turnir, liga, ekipni turnir), ki iz dnevnika bere **samo
    pokrivni indeks** `idx_rating_zgodovina_igralec_tekme` (V30): tekme pravih
    ravni zbereta podpoizvedbi, dnevnik se preleti po indeksu. `OR` med viroma
    tu indeksa **ne** ubije (za razliko od uvrstitve novinca) — obseg
    `sistem = ?` se prebere v celoti v vsakem primeru. Brez indeksa je sqlite
    za vsak zapis bral še celo vrstico: 1,13 s namesto 0,09 s, lestvica ~3,2 s,
    profil do 3,3 s. Klicatelj sproži štetje **enkrat** za vse igralce, ki jih
    potrebuje (profil ga je prej klical dvakrat). Test načrta za SQLite je
    odšel s prenovo baze; v PostgreSQL se meri na kopiji produkcije
    (`docs/PRENOVA-BAZE.md`, razdelek 9).
  - **Lestvica, uvrstitev na profilu, kandidati naključnega para in seznam
    turnirjev pridejo iz predpomnilnika** (`PredpomnilnikBranja`, nov. 2026,
    najdba C1 pregleda kakovosti): so izpeljanke čez vso zgodovino, ki jih
    gledalec odpira ob vsakem obisku (na kopiji produkcije lestvica 297 → 14 ms,
    seznam turnirjev 236 → 12 ms). Zastavica rekreativca ostane izpeljana in
    neshranjena v bazi; predpomni se samo izid štetja. Pravila, ki jih ne razbij:
    - **Razveljavi ga VSAK zapis v bazo**, ne seznam »pomembnih« mest: vsak
      stavek, ki ga Hibernate pošlje bazi in ni branje, ob potrditvi svoje
      transakcije poveča rod (`PredpomnilnikNastavitve`, `StatementInspector`).
      Nova storitev, ki piše, zato ne rabi vedeti, da predpomnilnik obstaja.
    - **V transakciji, ki sme pisati, se ne uporablja** (ne bere in ne polni):
      ta vidi svoje nepotrjene zapise. Zato so testi z `@Transactional`
      nedotaknjeni, test predpomnilnika pa namenoma ni `@Transactional`
      (`PredpomnilnikJavnihPotiTest`).
    - **Zapisa mimo Hibernata ne vidi** (`JdbcTemplate`, popravek v bazi na roko,
      uvoz v drugem procesu). Za to je veljavnost vnosa
      (`turnirko.predpomnilnik.veljavnost`, privzeto 5 minut; `PT0S` ga izklopi).
      Po ročnem posegu v bazo je stanje največ toliko staro, ali pa aplikacijo
      znova zaženi.
    - **Vanj sodi samo, kar je za vse zahtevke isto.** Kar je odvisno od računa
      ali vloge (vidnost turnirjev, domača stran po meri), se računa sproti.
      Vrednost, odvisna od dneva (status »V pripravi«, starostni pas), nosi dan
      kot različico.
  - **Lestvica šteje zmage in poraze iz ŠTEVILK, ne iz entitet**
    (`izidiVsehOdigranih`, `izidiVsehOdigranihPosamicnih`): ~94 tisoč tekem ob
    vsakem ogledu, potrebni pa so samo trije id-ji na tekmo. Nalaganje tekem s
    prijavami, igralci in posredniki srečanj je lestvico podaljšalo za ~1,7 s
    (2,15 s → 0,43 s).
  - **Kdor je z lestvice skrit, z nje IZPADE** in ne pristane med igralci brez
    ratinga: igralec s štiristo tekmami ni novinec, četudi danes ne igra več.
  - **Iz nevednosti ne sklepaj na neaktivnost**: igralec brez znanega termina
    zadnje tekme (same tekme brez datuma) ostane na lestvici. Prej ga je
    pravilo 18 mesecev pobralo, kot da ga ni bilo.
  - **Skritje meri `RatingStanje.svezOb()`, ne zadnje tekme** — zunanja
    uvrstitev je prav tako podatek o igralcu (glej zunanjo uvrstitev spodaj).
- **Odbitek za neaktivnost** (`Neaktivnost`, `NeaktivnostStoritev`, V22):
  **−10** po 6 mesecih, skupno **−25** po 12, skupno **−40** po 24, nato nič.
  Po **18 mesecih** igralec izgine z **javne lestvice** (številka mu ostane —
  na profilu in v zgodovini je vse vidno). Odbitki so majhni namenoma:
  izmerjeno odsotnost res zniža moč, a za ~10–30 točk, TTR-jevih −80 podatki
  ne podpirajo.
  - **Odbitek je zapis v dnevniku z datumom ZAPADLOSTI**, ne z datumom vpisa.
    Le tako ga preračun postavi na isto mesto v časovno vrsto in graf napredka
    pokaže, kdaj je padec nastal.
  - **Uveljavlja se na dveh mestih, a skozi isto metodo**: ob obračunu tekme
    (premor se poplača PRED tekmo, da nasprotnik igra proti popravljeni
    številki) in v dnevnem opravilu ob 3.15 (`@Scheduled`, zato je na
    `TurnirkoAplikacija` `@EnableScheduling`) — sicer bi igralec, ki je nehal
    igrati, držal mesto s stanjem izpred treh let. Ročno:
    `POST /api/v1/rating/neaktivnost`.
  - **Isti odbitek se ne sme zapisati dvakrat**: koliko stopenj je za tekoči
    premor že uveljavljenih, se prešteje iz dnevnika (`steviloOdbitkovPo`).
  - **`rating_zgodovina.razlog`** (V22) loči `POSTAVITEV` od `NEAKTIVNOST`
    in od V25 od `ZUNANJA_UVRSTITEV` — zapis brez tekme je bil prej nujno
    postavitev. Ločnica ni kozmetična: **kar je določil človek, preračun
    OHRANI**, **odbitek POBRIŠE in izračuna znova** (izpeljanka iz zaporedja
    tekem). Zato mora vsaka poizvedba o postavitvah filtrirati po `razlog`, ne
    po »brez tekme«. SQLite omejitve stolpca ne zna spremeniti, zna pa stolpec
    odstraniti (3.50) — zato V25 vrednosti odloži, stolpec postavi znova s
    tremi vrednostmi in vrednosti vrne.
- **Dnevnik ratinga ima časovno os (`rating_zgodovina.velja_ob`, V18).**
  `ustvarjen_ob` je čas ZAPISA, `velja_ob` pa čas TEKME, ki je spremembo
  povzročila — turnirska tekma dobi datum turnirja ob polnoči, ligaška čas
  srečanja, postavitveni zapis pa trenutek postavitve.
  Po njem tečejo preračun od datuma, okno črte in premik mesta na lestvici;
  tekma brez znanega datuma velja 1. 1. 1900 (`VrstaRatinskeTekme.BREZ_DATUMA`)
  — namenoma na začetku, kjer najmanj škodi, in **ne** na dan vnosa, kjer bi se
  brala kot »odigrano ta teden«.
- **Dnevnik se po TEKMAH išče s `concat(z.sistem, '') = :sistem`, ne z
  `z.sistem = :sistem`** (`RatingZgodovinaRepozitorij`: spremembe ob tekmah,
  rating pred tekmami, tekme turnirja/lige za statistiko, brisanje obračunov).
  Baza nima statistik (ANALYZE), zato sqlite ne ve, da ima `sistem` eno samo
  vrednost, in vsak indeks, ki se z njim začne (V18, V21, V30), izbere pred
  indeksom po tekmi — poizvedba za deset tekem preleti ves dnevnik. Izraz nad
  stolpcem ga iz izbire indeksa izloči, pomen ostane isti. Izmerjeno: zadnje
  tekme 24 → 7 ms, statistika turnirja 74 → 29 ms, brisanje ob preračunu
  3,6 → 1,8 s. **Druge poti so preverjene in slabše:** sestavljen indeks
  `(id_tekma, sistem)` obrne samopovezavo uvrstitve novinca na cel dnevnik,
  ANALYZE pa štetje tekmovalnih tekem odvrne s pokrivnega indeksa V30 (in
  spremeni načrte po vsej aplikaciji). Poizvedbe, ki berejo **ves** obseg
  sistema ali iščejo po igralcu (`najdiZaIgralca`, `tekmovalnihTekem`,
  uvrstitev novinca), ostanejo pri `z.sistem` — tam indeks s `sistem` je
  pravi. **Velja za SQLite**: PostgreSQL ima statistike in izraz ne škodi;
  odstranitev sodi v fazo 6 prenove baze, ko so načrti izmerjeni na kopiji
  produkcije (`NacrtiDnevnikaTest` je bil test načrta SQLite in je odšel).
- **Rating je izpeljanka: `PreracunRatingaStoritev.preracunajOd(datum)` ga zna
  sestaviti znova** (`POST /api/v1/rating/preracun?od=`, admin; v vmesniku
  stran `/rating`, gl. razdelek Vmesnik).
  Pobriše obračune od datuma, iz preostanka dnevnika obnovi `rating_stanje`
  in vse skupaj odigra znova v pravem zaporedju. Brez tega je dnevnik enosmeren:
  varovalka `existsByTekmaId` drugi obračun iste tekme zavrne, zato popravljen
  rezultat ratinga nikoli ne popravi, spremembe parametrov formule pa pomešajo
  stare in nove vrednosti. Nemški TTR isto izvaja načrtno (preračun od 2005) in
  prav zato si sme popravljati logiko za nazaj.
  - **Postavitveni zapisi (brez tekme) se NE brišejo** — niso posledica izida,
    ampak odločitev administratorja, in ostanejo kot izhodišče igralca.
  - **Pravilo razvrščanja v časovno vrsto živi na enem mestu**
    (`VrstaRatinskeTekme`) in ga delita uvoz in preračun. Dve kopiji bi se
    sčasoma razšli in isti izidi bi dali dva različna ratinga.
  - Preračun celotne uvožene zgodovine (91.741 tekem) traja ~3,5 minute in
    reproducira obstoječe ratinge do zadnje točke — to je merilo, da je
    dnevnik res rekonstruirljiv.
  - **`Porocilo.igralcevZRatingom` je stanje PO preračunu** in ne število
    obnovljenih stanj (tako je bilo prej): pri preračunu od začetka je dnevnik
    ob obnovi prazen, zato bi bila tista številka vedno 0 — vmesnik bi trdil,
    da se ni zgodilo nič, medtem ko se je izračunalo vse.
- **Čas ligaške tekme je eno pravilo: `Srecanje.casOdigranja`** (obračun v
  `RatingStoritev.casLigaskeTekme`, preračun v `vsaVrsta`, datum na profilu v
  `OpisSrecanja.cas`). Velja **termin**, razen kadar je bil **prvi izid**
  (`srecanje.odigran_ob`, od septembra 2026 s pretvornikom in postavljen ob
  PRVEM izidu, pred obračunom, po slovenskem času na minuto) vpisan pred
  dnem termina — takrat sta se ekipi zamenjali za termin in velja trenutek
  vpisa (star zapis brez ure dobi uro termina). Prej je obračun bral termin,
  preračun pa dan zaključka vnosa: vsak popravek je premešal tekme večera
  (vse na polnoč dneva vnosa), srečanje 4. kola, odigrano na večer 1., pa je
  stalo dva meseca v prihodnosti. Merilo: preračun ponovi obračun ob vnosu
  (`srecanjePredTerminomVeljaObVpisuInPreracunObdrziCasInRating`).
- **Obračun teče v vrstnem redu VNOSA, ne po datumu tekme.** `RatingStoritev`
  vzame stanje, kakršno je *zdaj*, in nanj prišteje spremembo; v dnevnik pa
  zapiše `velja_ob` **tekme**. Rezultat, vpisan za nazaj (novembrsko kolo
  vneseno pred oktobrskim), zato ni napačen le za tisto tekmo: oktobrska je
  računana proti številkam, ki že vsebujejo november, na grafu pa stoji na
  oktobrskem datumu z vrednostjo, ki je takrat ni bilo. Isto zamakne vrnitev po
  odsotnosti (`SledilnikVrnitve` termin premika samo naprej) in prvi dan
  igranja novinca (`prva_tekma_ob` se postavi ob **prvem vnosu**). Popravek je
  `preracunajOd(datum)` — in prav to je razlog, da preračun sploh obstaja.
  Ligaško srečanje ga od okt 2026 ob koncu vnosa sproži samo (glej »Srečanje,
  vneseno za nazaj«); turnirske tekme še ne.
- **Postavitveni (začetni) rating** (`RatingStoritev.nastaviZacetniRating`):
  admin sme novincu določiti vstopni rating **samo dokler `stTekem == 0`**; potem
  ga določajo le rezultati. Zabeleži se kot zapis v `rating_zgodovina` **brez
  tekme** (oba `id_tekma`/`id_tekma_srecanja` prazna) — graf profila to
  prenese (glej `ProfilStoritev.graf`).
- **Zunanja uvrstitev za redke goste** (`RatingStoritev.zunanjaUvrstitev`,
  `POST /igralci/{id}/zunanja-uvrstitev`, V25). Igralec, ki pri nas odigra dve
  tekmi na leto, ker sicer igra po svetu, ima pri nas številko, ki o njem ne
  pove ničesar: lestvica ga postavi očitno prenizko ali pa ga po 18 mesecih ne
  kaže več. Njegova moč pri tem **ni neznana** — zapisana je na ITTF lestvici,
  v točkah NTZS ali pri drugi zvezi. Zato jo admin prepiše.
  - **Od postavitve se loči po treh stvareh, in vse tri so bistvene:**
    1. **Dovoljena je tudi igralcu, ki tekme ŽE IMA.** Prav zato obstaja;
       postavitev je vstopna vrednost in je pred prvo tekmo.
    2. **VIR in POJASNILO sta obvezna** (`rating_zgodovina.vir`,
       `pojasnilo`; varuje ju `CHECK` v V25 in ne le obrazec) in **javna** —
       izpišeta se na profilu pod grafom. Ročno vpisana številka na javni
       lestvici je brez zapisanega vira videti kot naklonjenost, z virom pa je
       trditev, ki jo lahko vsak preveri.
    3. **V časovni vrsti velja OB SVOJEM ČASU.** Postavitev je *izhodišče*
       igralca in v preračunu stoji pred njegovo prvo tekmo; zunanja uvrstitev
       je *popravek na določen dan*, zato jo `PreracunRatingaStoritev` odigra
       na njenem mestu **med tekmami** (`Korak` = tekma ali uvrstitev, ena
       vrsta, stabilno razvrščena po `velja_ob`). Odigrana na koncu bi
       pomenila, da so vse poznejše tekme stekle iz napačne številke; na
       začetku, da tiste pred njo niso vplivale na nič.
  - **Vpisana vrednost je nedotakljiva, sprememba se preračuna.** Preračun
    `nova_vrednost` ne spremeni nikoli (to je tisto, kar je človek prebral z
    zunanje lestvice), `sprememba` pa je razlika do stanja pred njo in je
    odvisna od vsega, kar se je zgodilo prej — zato jo popravi
    (`RatingZgodovina.popraviSpremembo`). Brez tega bi graf trdil »+240« tam,
    kjer je razlika zdaj +190. Iz istega razloga `zadnjeVrednosti(sistem, od)`
    **izpusti** uvrstitve od meje naprej: stanje pred mejo jih še ne sme
    vsebovati.
  - **Šteje kot svež podatek o igralcu** (`RatingStanje.zunanja_uvrstitev_ob`,
    `svezOb()` = poznejši od zadnje tekme in zadnje uvrstitve). Po njem merita
    **odbitek za neaktivnost** in **umik z javne lestvice**: številki, pravkar
    prepisani z ITTF lestvice, ni mogoče odbiti 40 točk za odsotnost, ki jo ta
    lestvica že upošteva, skriti takega igralca pa bi pomenilo zavreči ravno
    tisto, kar smo vpisali. **`zadnja_tekma_ob` ostane, kar piše** — po njem
    naprej teče vrnitev po odsotnosti (višji K), ta pa se nanaša na igralca in
    ne na svežino naše številke.
  - **Zapadli odbitki se poplačajo PRED uvrstitvijo** (isto kot pred tekmo):
    zgodili so se in v zgodovini ostanejo, številka nato skoči na zunanjo.
  - **`postavljen` uvrstitev novinca ustavi** — enako kot pri postavitvi.
  - Regresija je `ZunanjaUvrstitevTest`; najpomembnejši test je
    `preracunOdZacetkaOhraniVpisanoStevilko` (če preračun uvrstitev odigra na
    napačnem mestu, je konec ponovljivosti).
