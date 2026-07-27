# Turnirko — navodila za delo s kodo

## Jezik in slog

- **Vsa imena (razredi, metode, spremenljivke, stolpci) in komentarji so v slovenščini**,
  brez šumnikov v identifikatorjih (Storitev, Zreb, Prijava...). Angleški ostanejo samo
  izrazi ogrodij (Repository sufiks je preveden: `...Repozitorij`).
- Komentarji pojasnjujejo ZAKAJ in domenska pravila, ne KAJ počne vrstica.

## Trdna pravila

- Shemo baze spreminjaj SAMO z novo Flyway migracijo v `zaledje/src/main/resources/db/migration/sqlite/`
  (nikoli ne spreminjaj obstoječih migracij; `ddl-auto` ostane `none`).
- Poslovna logika sodi v `storitve`, kontrolerji so tanki adapterji.
- Navzven gredo samo DTO-ji, nikoli entitete. Osebni podatki (e-pošta, telefon,
  naslov, datum rojstva) ne smejo v javne poglede.
- Vsaka sprememba domenske logike (žreb, rezultati, rating) MORA imeti test.
  Testi: `cd zaledje && mvnw test`.
- Statuse določa strežnik; prehodi stanj se preverjajo v storitvah.
- Rating se spreminja izključno prek `RatingStoritev` (dnevnik v `rating_zgodovina`).
- **Klubski ELO (`EloStoritev`)** ima tri lastnosti, ki jih ne razbij:
  - **dinamični K** glede na `RatingStanje.stTekem` posameznega igralca
    (`kFaktor`: <10 → 48, <30 → 32, sicer 20) — novinec se hitro umesti,
    ustaljen se malo giblje. K je torej lahko za oba igralca različen.
  - **ničvsotno zaokroževanje** z `Math.rint` (pol na sodo), ne `Math.round`:
    pri **enakem** K je vsota sprememb natanko 0 tudi ob izenačeni napovedi
    (`Math.round` bi pri lihem K ob vsakem izenačenju vbrizgal +1). Pri
    različnem K (novinec proti ustaljenemu) vsota namenoma ni 0.
  - **set-margina po presenečenju** (`marginaMnozitelj`): K se množi glede na
    to, koliko je razlika v nizih presenetljiva (dejansko dobljeni nizi
    poraženca proti pričakovanim iz razlike ratingov), NE po surovi razliki.
    Favoritova gladka zmaga je pričakovana (majhen bonus), avtsajderjeva velik.
- **Postavitveni (začetni) rating** (`RatingStoritev.nastaviZacetniRating`):
  admin sme novincu določiti vstopni ELO **samo dokler `stTekem == 0`**; potem
  ga določajo le rezultati. Zabeleži se kot zapis v `rating_zgodovina` **brez
  tekme** (oba `id_tekma`/`id_tekma_srecanja` prazna) — graf profila to
  prenese (glej `ProfilStoritev.graf`).
- **Avtorizacija:** gost (neprijavljen) sme samo `GET`; vse mutacije zahtevajo
  `ROLE_ADMIN` (`VarnostneNastavitve`, HTTP Basic, stateless). Gesla samo kot
  BCrypt zgostitev; začetnega admina ustvari `ZacetniAdmin`. Metodne varnosti
  (`@PreAuthorize`) ni — pravila so v varnostni verigi, zato jih testi, ki
  kličejo storitve/kontrolerje neposredno, ne sprožijo.
- **Gesel ni mogoče prebrati** (v bazi je le zgostitev). Zato admin gesla ne
  "vidi", ampak ga računu igralca **nastavi** — po svoji izbiri
  (`RacuniStoritev.nastaviGeslo`) ali naključno (`ponastaviGeslo`) — in ga
  izroči igralcu. Nastavljanje tujega gesla ne zahteva starega (admin ureja
  tuje račune); zamenjava lastnega gesla ga zahteva (`zamenjajGeslo`).
- **Sistem tekmovanja** izbere dogodek; `ZrebStoritev` po njem razveji žreb
  (`IZLOCILNI`/`KROZNI`/`SKUPINE_IZLOCILNI`/`SKUPINE`). Lestvice računa
  `RazvrstitevStoritev` (zmage → razlika nizov → medsebojna tekma), izločilni
  del po skupinah pa `SkupineStoritev` (generira se, ko so odigrane vse
  skupine — nujno pred preverbo zaključka dogodka; **samo** pri
  `SKUPINE_IZLOCILNI`). Izlocilno mrezo gradi ena metoda
  (`ZrebStoritev.zgradiIzlocilnoMrezo`), da jo delita oba sistema.
- **`SKUPINE` = format TOP** (npr. TOP 24 kot 3 skupine po 8):
  - igra najboljših N prijavljenih (N = `steviloSkupin` × `velikostSkupine`);
    kdor ne pride v izbor, dobi status `REZERVA` in ne igra;
  - razporeditev je **zaporedna po jakosti** — skupina A dobi mesta 1–8, B
    9–16 …; skupine so torej **rangi**, ne uravnotežene skupine;
  - jakostni vrstni red je edino merilo in živi v `Prijava.stNosilca`.
    Predlog naredi `IzborStoritev` po klubskem ELO, igralci **brez ratinga
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
- **Odstop med tekmovanjem** (`TekmaStoritev.odstopiIgralca`): že odigrane
  tekme obveljajo, preostale dobijo nasprotniki kot `BREZ_BOJA` (in ne
  `PREDAJA`) — te tekme niso bile odigrane, zato se rating ne sme obračunati.
- **Neodigrane tekme ne štejejo v statistiko igralca.** Merilo je
  `IzidTekme.jeOdigrana()` (le `IGRANO` in `PREDAJA`); poizvedbe v
  `TekmaRepozitorij`, ki hranijo statistiko, ga morajo upoštevati. V lestvici
  skupine pa `BREZ_BOJA` normalno šteje kot zmaga nasprotnika, ker odloča o
  uvrstitvi — to sta namerno ločeni poti.
- Točke po nizih morajo biti v **mogočem vrstnem redu**: tekma se konča v
  trenutku odločitve, zato noben niz ne sme slediti izidu, ko je zmagovalec
  že dosegel dovolj nizov (glej `TekmaStoritev.shraniTockeNizov`).
- **Leno nalaganje:** kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije.
  Vsaka poizvedba, katere rezultat gre v DTO, mora z "join fetch" vnaprej naložiti
  vse povezave, ki jih DTO bere (kraj, igralca, klub) — sicer na pravem strežniku
  poči LazyInitializationException, testi z @Transactional pa tega NE ujamejo.
  Regresijski testi za to: `LenoNalaganjeTest` (namenoma brez @Transactional).

## Vmesnik (vmesnik/)

- React + TypeScript (Vite), TanStack Query; brez dodatnih knjižnic brez potrebe.
- Tipi v `src/api/tipi.ts` morajo zrcaliti DTO-je zaledja — ob spremembi API-ja
  posodobi oboje.
- Strežniške napake (problem-detail) prikazuje `SporociloNapake`; obrazci ne
  podvajajo domenskih pravil, le vodijo vnos (npr. izbira samo veljavnih izidov).
- Za nepovratna dejanja uporabi `PotrditvenoOkno`, nikoli `window.confirm`.
- **Prijava** je v `avtentikacija/AvtentikacijaKontekst`; `useAvtentikacija().jeAdmin`
  odloča o prikazu urejevalnih dejanj. Strežnik je zadnja obramba (mutacija brez
  prijave vrne 401), vmesnik dejanja le skrije. Poti `/igralci` in `/sifranti`
  so za goste zaprte (`SamoAdmin`). `DogodekStran` se izriše glede na sistem
  (mreža / lestvica / skupine).
- **Listki tekem** (natisljivi zapisniki): dve strani, obe **namenoma zunaj
  `Postavitve`** (brez navigacije), da je natis čist; tiska brskalnik
  (`window.print`) — **brez zaledja in nove sheme**, listek je le pogled na
  podatke, ki že obstajajo:
  - `ListkiStran` (`/dogodki/:id/listki`) — turnirski dogodek; natisne
    **pripravljene tekme** (oba igralca znana, status `PRIPRAVLJENA`/`V_IGRI`;
    prosti prehodi in `CAKA` odpadejo sami). Gumb »🖨 Listki« na `DogodekStran`
    se pokaže adminu ob `V_TEKU`.
  - `ListkiSrecanjaStran` (`/srecanja/:id/listki`) — ekipno srečanje; natisne
    tekme s stanjem `CAKA` (postava je določena, še niso odigrane). Dvojice
    imajo na strani dva igralca, ekipa je podnaslov, mize ligaška tekma nima.
    Gumb na `SrecanjeStran` se pokaže adminu, kadar obstaja `CAKA` tekma.
  - Oba izrisujeta skupno komponento **`komponente/Listek`** (normaliziran
    `ListekPodatki`). Listek je **črno-bel ne glede na temo** (fiksni `#000/#fff`,
    ne temo odvisne spremenljivke); sloge in `@media print` (`@page` A4, mreža
    2 stolpca, `break-inside: avoid`) drži `slog.css`.
- Preverba pred zaključkom dela: `cd vmesnik && npm run build` (tsc + vite).

## Kontekst projekta

- Razvojna strategija in prioritete: `docs/RAZVOJNI-NACRT.md`.
- Pravilnika NTZS (izvlečeno besedilo): `docs/pravilniki/pst.txt` in `pot.txt` —
  Priloge A/B/C v PST so predloge za prihodnji skupinski sistem in žreb z nosilci.
- Stari prototip (referenca, ne spreminjaj): `../poskus2` in `../aplikacija-turnirji`.
