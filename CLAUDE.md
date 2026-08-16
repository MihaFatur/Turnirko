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
- **Osebni podatki igralcev so ločeni na ravni tipa, ne pogojne veje.**
  `IgralecJavniDto` (ime, priimek, spol, roka, klub, rating) je edino, kar
  vrnejo `GET /igralci`, `GET /igralci/{id}` in *vse* mutacije — te poti so
  javne oz. odprte tudi organizatorju. Poln `IgralecDto` z osebnimi podatki
  vračata **samo** `GET /igralci/podrobno` in `GET /igralci/{id}/podrobno`,
  ki ju varnostna veriga omeji na `ADMIN` (organizator jih namenoma ne vidi:
  šifrant je skupen vsem klubom). Ne združuj poti in ne dodajaj osebnih polj
  v javni DTO — pravilo varuje `IgralciZasebnostTest`, ki preverja surovo
  telo odgovora.
- **Admin geslo nima privzetka.** Če je baza prazna in
  `turnirko.admin.privzeto-geslo` ni nastavljen (oz. je krajši od 12 znakov),
  `ZacetniAdmin` ustavi zagon. Izjema je profil `namizni` (lokalna prenosna
  različica), ki obdrži `admin`/`admin`. Privzetka ne vračaj v
  `application.properties`.
- Vsaka sprememba domenske logike (žreb, rezultati, rating) MORA imeti test.
  Testi: `cd zaledje && mvnw test`.
- Statuse določa strežnik; prehodi stanj se preverjajo v storitvah.
- Rating se spreminja izključno prek `RatingStoritev` (dnevnik v `rating_zgodovina`).
- **Štetje v ELO je izbirno na ravni tekmovanja:** `turnir.steje_v_elo` in
  `liga.steje_v_elo` (oba privzeto `true`). Obračun se sproži le, če zastavica
  velja — turnir v `TekmaStoritev.vnesiRezultat`
  (`tekma.getDogodek().getTurnir().isStejeVElo()`), liga v
  `SrecanjeStoritev` (`liga.isStejeVElo()`); ligaške dvojice ne štejejo nikoli.
- **Format srečanja je edini vir vrstnega reda tekem** (`FormatSrecanja.razpored()`):
  `SNTL` (3 igralci, dvojice prve), `CORBILLON` (2 igralca, dvojice na sredini)
  in `SAVINJA` (2 igralca, dvojice prve). Baza hrani samo ime, zato je nov
  format enum + `razpored()` + razširjen `CHECK` stolpca `liga.format_srecanja`
  z novo migracijo. `zmag_za_srecanje` je `NULL`, kadar se odigrajo **vse**
  tekme — to je enakovredna izbira in ne »manjkajoča vrednost«, zato jo obrazec
  napiše, ne pusti praznega polja.
- **Prehodi lige (mesto v piramidi) niso pravilo tekmovanja.** `id_visja_liga`,
  `st_napreduje` in `st_izpade` na razpored ne vplivajo, zato jih *ne* ureja
  `LigaStoritev.uredi` (ta je zaklenjen na `PRIPRAVA`), ampak
  `nastaviPrehode` (`PUT /lige/{id}/prehodi`) — v vsakem stanju lige. Povezavo
  je mogoče vpisati z **obeh** strani (višja liga ali seznam nižjih); pri
  nižjih se piše v **njihov** stolpec, zato se lastništvo preveri tudi zanje,
  krogi pa se zavrnejo (`preveriBrezKroga`). Piramida na strani lige sledi
  izključno vpisanim povezavam — **sezona ne filtrira** (prej je in tiho
  razdrla piramide z drugače zapisano sezono); lige druge sezone so označene.
- **Ekipa ni nujno klub** (V11): `ekipa.id_klub` je neobvezen. *Prosta ekipa*
  (brez kluba) je zasedba, ki v šifrantu klubov nima zapisa in nastopa **samo
  v tej ligi** — rekreacijske in medpodjetniške lige; s tem šifranta ne zasuje
  z enkratnimi zapisi. Kader ostane **skupni register igralcev** (prosta je
  ekipa, ne igralci), zato ELO in profili tečejo nespremenjeno. Ime je pri
  klubski ekipi neobvezno (sestavi ga `prikazanoIme()` iz kluba in zaporedne),
  pri prosti pa **obvezno** — to varuje `CHECK` v shemi in
  `LigaStoritev.dodajEkipo`. Ista metoda zahteva, da je **prikazano ime v ligi
  enolično** (primerja ga čez oba tipa, ne le `(klub, zaporedna)`): v
  razporedu, na lestvici in v zapisniku se ekipi ločita samo po njem.
  **Vsak `join fetch` na `ekipa.klub` mora biti LEVI** — notranji tiho izpusti
  celotno srečanje iz razporeda, lestvic in profilov (`EkipaRepozitorij`,
  `SrecanjeRepozitorij`, `TekmaSrecanjaRepozitorij`); regresija je
  `ProstaEkipaTest`.
- **Termini kol so seme + ročni popravki, ne seznam datumov v pravilih.**
  Liga nosi `zacetek_prvega_kola` in `razmik_dni` (V10) — vpišeta se že ob
  ustvarjanju, ko ekip (in s tem števila kol) še ni. Datume vsem srečanjem
  napiše `LigaStoritev.generirajRazpored` (`terminKola`), ker je šele takrat
  znano, koliko kol liga ima. Popravke po kolih dela `nastaviTermine`
  (`PUT /lige/{id}/termini`) — kot prehodi **v vsakem stanju lige** in ne pod
  zaklepom pravil, ker se kolo prestavi tudi sredi sezone. Prestavljeno kolo
  **ne premakne naslednjih** (ta so že objavljena) in semena ne popravi.
  Termin je last **kola**, ne srečanja: vsa srečanja kola dobijo isti čas.
  Ura `00:00` pomeni »ura ni določena« — vmesnik takrat izpiše samo datum.
- **Časovni žig s pomembno uro potrebuje `@Convert(CasKotBesedilo.class)`.**
  Gonilnik sqlite-jdbc pozna eno samo obliko za datume (`date_string_format`,
  v `application.properties` `yyyy-MM-dd`) in z njo piše **tudi** časovne žige
  — vsak `LocalDateTime` skozi gonilnik torej izgubi uro (`liga.ustvarjen_ob`
  je v bazi `2026-07-23`). Nastavitve `timestamp_string_format` gonilnik ne
  pozna in jo tiho spregleda. Pretvornik naredi iz stolpca navaden niz in ura
  obstane; uporabljata ga `srecanje.predviden_zacetek` in
  `liga.zacetek_prvega_kola`. **Testi tega sami ne ujamejo**: `@Transactional`
  test dobi entiteto iz predpomnilnika seje, zato mora preverba ure izrecno
  `flush()` + `clear()` (glej `uraTerminaPrezivizapisVBazo`).
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
- **Avtorizacija in vloge:** štiri vloge — `ADMIN` (vse), `ORGANIZATOR`
  (ustvarja turnirje/lige in upravlja **samo svoje** oz. klubske), `IGRALEC`
  (svoj profil) in gost (neprijavljen, samo `GET`). Osnovna raven je v
  varnostni verigi (`VarnostneNastavitve`, HTTP Basic, stateless): gost samo
  `GET`; organizator sme na turnirje/lige/dogodke/tekme/srečanja in `POST`
  igralca; ostale mutacije (šifranti, računi, urejanje/brisanje igralcev) le
  `ADMIN`. Gesla samo kot BCrypt zgostitev; začetnega admina (privzeto
  `admin`/`admin`) ustvari `ZacetniAdmin`.
- **Lastništvo na ravni zapisa** (`LastnistvoStoritev`): ker veriga pozna le
  vlogo, ne pa cigav je zapis, se lastništvo turnirja/lige preverja **v
  storitvah**. `turnir`/`liga` imata `id_ustvaril` (racun) in `id_klub_lastnik`
  (posnetek organizatorjevega kluba ob nastanku); organizator sme urejati
  svoje (ustvaril == on) ali od svojega kluba (klubLastnik == njegov klub),
  admin vse. To je **namerna izjema** od »metodne varnosti ni«: gre za navadno
  kodo v storitvah (ne `@PreAuthorize`), zato jo storitveni testi normalno
  sprožijo — testi morajo nastaviti `SecurityContext` (glej `LastnistvoTest`).
  Brez konteksta (interni klic/test) preverba ne omejuje. Organizator se
  registrira sam (vloga v `RegistracijaVnos`), vlogo in (neobvezni) klub mu
  potrdi admin (`RacuniStoritev.potrdiOrganizatorja`).
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
- **Spremljane lige so osebna nastavitev računa**, ne zapis o tekmovanju:
  živijo v `spremljana_liga` (račun + liga) in jih vrača/ureja
  `/api/v1/domov/moje-lige` (`DomovStoritev`). To je edina pot, kjer sme
  pisati tudi navaden igralec, zato jo varnostna veriga našteje posebej
  (`.authenticated()`, brez vloge) — pravilo mora stati **pred** splošnim
  »GET je javen«. Gost izbora nima; njegov brskalnik si zadnje ogledane lige
  zapomni sam (`vmesnik/src/pomozno/ogledaneLige.ts`).
  **Vse branje in pisanje izbora teče skozi en kavelj**
  (`vmesnik/src/pomozno/spremljaneLige.ts`): sklop »Moje lige«, okno za
  urejanje izbora in stran lige berejo isto stanje in preklapljajo enako.
  Zahteve preklopa gredo skozi **modulsko vrsto** (`vVrsto`) — SQLite prenese
  enega pisca naenkrat in dva hkratna zapisa strežnik vrne kot 500, v oknu za
  izbor pa gledalec odkljuka več lig v sekundi. Vgrajeni `scope` TanStack
  Queryja tega ne reši: če se prva zahteva konča, preden druga pride do svojega
  premora, se znak za nadaljevanje izgubi in vrsta obstane. Iz istega razloga
  `onMutate` **ne sme biti async** — predpomnilnik mora dobiti novo vrednost že
  v odzivu na klik, ker iz njega naslednji klik prebere trenutno stanje.
- **Razpored pri neodigranem kolu izpiše termin, ne besede »razpored«.**
  Merilo je `metaKola` v `LigaStran`: odigrano kolo dobi datum in oznako
  »odigrano«, kolo, ki šele pride, pa termin (`oblikujTermin` → »ned, 4. okt ·
  18.00«, poudarjen z `.liga__kolo-termin`). Beseda »razpored« ostane samo,
  kadar termina ni — takrat organizator datumov ni vpisal.
- **Liga ima tri lestvice, ne eno** (`LestvicaLigeStoritev`): ekipno (glavna),
  posameznikov (`/lige/{id}/lestvica-igralcev`) in dvojic
  (`/lige/{id}/lestvica-dvojic`). Zadnji dve **štejeta samo tekme te lige** —
  rating tega ne zna, ker teče čez vsa tekmovanja. Merilo je izkupiček:
  **zmage**, ob izenačenju uspešnost (deleža se primerjata navzkrižno, da ne
  odloča zaokroženi odstotek iz prikaza) in razlika nizov; praga najmanjšega
  števila tekem ni. Posamične tekme in dvojice sta **ločena seznama** (izida
  para ni mogoče pripisati posamezniku — isto pravilo kot pri ELO), dvojica pa
  je **neurejen par**: ista igralca so ista dvojica doma in v gosteh.
- **Domača stran bere izpeljanke, ne surovih tekem.** `TurnirDto` nosi
  `faza`/`zmagovalec`/`zadnjiIzid` (izračun v `PovzetkiStoritev`,
  skupinske poizvedbe — nikoli po ena na turnir), `LestvicaIgralcaDto` pa
  `premik` (razlika mest proti stanju pred 30 dnevi) in `eloZgodovina`.
  **Črta ELO teče po tekmah in ne po koledarju**: `ustvarjen_ob` v
  `rating_zgodovina` je čas VNOSA, ne čas tekme (klub vnese celo kolo
  naenkrat), zato bi časovno vzorčenje vsem narisalo ravno črto.
- **Leno nalaganje:** kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije.
  Vsaka poizvedba, katere rezultat gre v DTO, mora z "join fetch" vnaprej naložiti
  vse povezave, ki jih DTO bere (kraj, igralca, klub) — sicer na pravem strežniku
  poči LazyInitializationException, testi z @Transactional pa tega NE ujamejo.
  Regresijski testi za to: `LenoNalaganjeTest` (namenoma brez @Transactional).

## Vmesnik (vmesnik/)

- **Oblikovni sistem je zavezujoč:** `vmesnik/turnirko-profil-redesign/project/DESIGN.md`
  (značaj »športni zapisnik na papirju«). Če predlog nasprotuje temu dokumentu,
  se popravi predlog, ne dokument. Ključna pravila: `border-radius: 0` povsod,
  brez senc (edina izjema je podčrtaj aktivne navigacijske postavke), brez
  gradientov razen 8 % polnila pod črto grafa, brez emojijev in ikon (edina
  ikona je logotip), naslovi levo poravnani, 8 px ritem, zadetkovna površina na
  dotik ≥ 44 px. Barve pomenijo: modra `#0088CE` = dejanje/podatek, zelena
  `#7BB900` = uspeh/napredovanje, rjasta `#B23A1E` = izguba/napaka/brisanje.
- **Modra ima dve vrednosti in ju ne smeš zamenjati.** `--barva-glavna`
  (`#0088CE`) **riše** — črte, palice, polnilo grafa, obroba gumba, fokus; tam
  ni besedila. Kjer modra postane **podlaga pod besedilom** (glavni gumb,
  značka »V teku«, blok ELO), velja `--barva-glavna-polna` (`#0071AB`), ker je
  bela na `#0088CE` samo 3,88:1 in pade WCAG AA za oznake pod 24 px; polna
  modra da 5,32:1. Ob prehodu miške `--barva-glavna-polna-mocna` (`#005A88`).
  Drugotno besedilo na modri ploskvi je `--barva-na-glavni-2` (svetlozelena),
  nikoli siva. Novih polnih ploskev ne slikaj z `--barva-glavna`.
  Podatke nosijo **vrstice s črtami**, nikoli mreže zaobljenih kartic; pas
  kazalnikov nadomešča kolofon (oznaka mono ↔ vrednost mono).
  Natančne makete zaslonov so `*.dc.html` v isti mapi.
- Pisave (tri družine, nič več): `Bricolage Grotesque` display, `Karla` telo,
  `IBM Plex Mono` oznake/številke. Lokalno vgrajene prek `@fontsource` v
  `main.tsx` — brez zunanjih klicev, da vmesnik dela tudi brez interneta v
  dvorani. Razredi sistema (`.naslov-strani`, `.kolofon`, `.naslovna-vrstica`,
  `.izbirnik`, `.elo-blok`, `.vrstica` …) so v `vmesnik/src/slog.css`; preden
  napišeš nov razred, preveri, ali obstoječi zadošča.
- **Namizje in telefon imata ločeni navigaciji.** Nad 640 px je masthead mreža
  (`grid-template-areas`, `.glava`): logotip, navigacija, kontekst uporabnika;
  1 px in 3 px črto nosi `.glava__crta`. Pod 640 px se ta glava sploh ne
  izriše — `Postavitev` po `useTelefon()` (`src/pomozno/telefon.ts`, ista
  prelomna točka kot v `slog.css`) izriše `.glava-telefon` (56 px, lepljiva)
  in **spodnjo vrstico** `.spodnja-vrstica` (Domov · Turnirji · Lige ·
  Lestvica · Več). Vodoravno drsna vrstica zavihkov v glavi je polovico
  postavk skrivala pred očmi. »Več« je predal z urejevalskimi stranmi
  (Igralci, Dostopi, Šifranti) in se izriše samo adminu/organizatorju.
- **V lepljivo glavo telefona vlagajo strani svoje skozi `GlavaTelefona`**
  (`useNazaj` za puščico nazaj, portali `GlavaDejanja` / `GlavaNaslov` /
  `GlavaZavihki`). Portal in ne podvojen izris: dejanje je v drevesu natanko
  enkrat, zato ga bralnik zaslona prebere enkrat. Kontekst uporabnika stoji
  samo tam, kjer ni puščice nazaj (vstopni zasloni).
- **Vrsta ekipe je preklop, ne polje.** V pripravi lige (`EkipeUredi` v
  `LigaStran`) stoji nad obrazcem segmentirani izbirnik »Iz registra / Prosta
  ekipa«: prvi zavihek ponudi klub in neobvezno lastno ime, drugi samo ime.
  Pod 640 px vrstica ekipe (`.liga__ekipa`) prelomi — dejanja gredo pod ime,
  ker se ime, velikost kadra in dva gumba v 390 px ne zložijo v eno vrsto.
  Oznaka pod imenom (`.liga__ekipa-oznaka`) se izpiše samo, kadar kaj pove:
  klub pri klubski ekipi z lastnim imenom, »prosta ekipa« pri prosti.
- **Razlike med širinama nosi CSS, ne JS** — izjema so mesta, kjer se
  razlikuje *vsebina* (druga mera vrstice turnirja, zavihek »Tekme« pri
  krožnem sistemu, dejanja v glavi). Tam odloča `useTelefon()`.
- React + TypeScript (Vite), TanStack Query; brez dodatnih knjižnic brez potrebe.
- Tipi v `src/api/tipi.ts` morajo zrcaliti DTO-je zaledja — ob spremembi API-ja
  posodobi oboje.
- Strežniške napake (problem-detail) prikazuje `SporociloNapake`; obrazci ne
  podvajajo domenskih pravil, le vodijo vnos (npr. izbira samo veljavnih izidov).
- Za nepovratna dejanja uporabi `PotrditvenoOkno`, nikoli `window.confirm`.
- **Filtriranje in razvrščanje seznamov teče skozi `komponente/Filtri.tsx`**
  (`useFiltri` + `KrmilaSeznama`) — turnirji, lige in lestvica. Nad seznamom
  stoji ena vrstica: gumb »Filtriraj«, ki odpre okno z **vsemi** merili, in ob
  njem nativni izbor razvrstitve. Pas segmentiranih gumbov po statusu je
  odpadel: meril je pet ali šest in bi na 390 px pojedel pol zaslona, preden
  bi gledalec videl prvo vrstico. Pravila, ki jih ne razbij:
  - **Skupina je podatkovna, ne našteta.** Izriše se samo, kadar jo podatki
    napolnijo z **vsaj dvema** različnima vrednostma (uvožena zgodovina NTZS
    nima ne kraja ne organizatorja — tam ti skupini preprosto ni). Merilo je
    ves seznam in ne zožen: skupina, ki med filtriranjem izgine in se vrne, je
    slabša od skupine z eno možnostjo.
  - **Števci so navzkrižni**: možnost šteje zadetke ob *ostalih* izbranih
    skupinah, možnosti brez zadetkov odpadejo. Zato ni mogoče sestaviti
    kombinacije, ki vrne prazen seznam.
  - **Izbrano mora biti vidno na strani, ne le v oknu** — vrsta žetonov
    (`.zeton`) pod krmili, vsak odstranljiv, ob njih »Počisti vse«. Filter, ki
    ga ne vidiš, je past.
  - Znotraj skupine velja **ali-ali** (»v teku *ali* priprava«), med skupinami
    **in**. Postavka ima v vsaki skupini eno vrednost; večvrednih meril
    (»igra v tej ligi«) `SkupinaFiltra` namenoma ne pozna.
  - Izbor živi v stanju strani in **ne v naslovu** — isto pravilo kot iskanje
    na lestvici.
  - **Turnir sezone nima kot polje**; izpelje jo `sezonaIzDatuma`
    (`pomozno/oblikovanje.ts`) z rezom 1. julija. Uvožena zgodovina se začne
    najprej sredi septembra in konča najkasneje sredi junija, zato skozi rez
    ne pade nobeno tekmovanje. Kraj in dvorana sta **ločeni** skupini: uvoz je
    ime prizorišča zapisal v `dvorana` (tam so imena mest), ročni vnos pa ima
    kraj iz šifranta.
  - **Lestvica loči starost in spol.** `KategorijaIgralca` spol nosi samo pri
    članih (pri U19 in veteranih se izgubi), zato ga `LestvicaIgralcaDto`
    vrača posebej (`spol`) — brez tega filtra »vse igralke« ni mogoče
    sestaviti. Mesto na lestvici se pripne **pred** filtriranjem (v zoženem
    seznamu so mesta 4, 9, 13), pri razvrstitvi po drugem merilu pa se
    prešteje znova.
- **Izbor spremljanih lig ureja `IzborLigOkno`, ne stran `/lige`.** Izbor je
  nastavitev domače strani in ne pot v ligo, zato »Uredi izbor« odpre okno —
  gledalec ostane, kjer je, in takoj vidi, kaj se je spremenilo; na `/lige` je
  vsaka vrstica povezava v ligo in preklopa ne more nositi. Okno mora zdržati
  **stotine lig** (uvožene sezone), zato: spremljane so v svoji skupini na
  vrhu in tam **obstanejo, dokler je okno odprto** (posnetek ob odprtju — sicer
  bi vrstica ob odkljukanju odskočila v skupino svojega statusa), zaključene so
  pospravljene za gumbom, iskanje pa teče čez ime in sezono in jih odpre.
  Ker seznam drsi znotraj sebe (`.modal:has(.izbor-lig)` je stolpec z mejo v
  višini zaslona), sta glava in noga na telefonu vedno vidni.
  Preklop sam je povsod isti znak — `GumbSpremljanja` (zelen kvadratek
  `.kljukica`), v treh okvirjih: sam ob imenu, kot gumb v vrstici dejanj strani
  lige in stisnjen v lepljivi glavi telefona.
- **Prijava** je v `avtentikacija/AvtentikacijaKontekst`. Za prikaz dejanj:
  `jeAdmin`, `jeOrganizator`, `smeUstvarjati` (admin ali organizator — gumbi za
  nov turnir/ligo) in `smemUrejati(idLastnik, idKlubLastnik)` (lastniško
  urejanje turnirja/lige; admin vse). Strežnik je zadnja obramba (mutacija brez
  pravice vrne 401/403), vmesnik dejanja le skrije. Podstrani, ki lastništva
  nimajo pri roki (`DogodekStran`, `SrecanjeStran`), naložijo nadrejeni
  turnir/ligo (ki nosi lastništvo) le za morebitne urejevalce. `/sifranti` in
  `/racuni` sta `SamoAdmin`; `/igralci` je `SamoUrejevalec` (organizator sme
  **dodati** igralca, urejanje/rating ostane adminu). `DogodekStran` se izriše
  glede na sistem (mreža / lestvica / skupine).
- **Listki tekem** (natisljivi zapisniki): dve strani, obe **namenoma zunaj
  `Postavitve`** (brez navigacije), da je natis čist; tiska brskalnik
  (`window.print`) — **brez zaledja in nove sheme**, listek je le pogled na
  podatke, ki že obstajajo:
  - `ListkiStran` (`/dogodki/:id/listki`) — turnirski dogodek; natisne
    **pripravljene tekme** (oba igralca znana, status `PRIPRAVLJENA`/`V_IGRI`;
    prosti prehodi in `CAKA` odpadejo sami) kot posamične **sodniške listke**
    (`komponente/Listek`, normaliziran `ListekPodatki`): niz je vrstica, vsak
    igralec svoj stolpec, spodaj skupni rezultat. Mreža 2 stolpcev s skupnimi
    robovi (negativni rob = ena črta za rezanje) da **6 listkov na A4**. Gumb
    »🖨 Listki« na `DogodekStran` se pokaže adminu ob `V_TEKU`. Turnir ima le
    to eno predlogo (ni izbire).
  - `ListkiSrecanjaStran` (`/srecanja/:id/listki`) — ekipno srečanje; natisne
    **en uraden ekipni zapisnik NTZS** (`komponente/ZapisnikEkipnegaDvoboja`)
    za celotno srečanje: glava, obe ekipi s postavo (A/B/C : X/Y/Z), mreža vseh
    posamičnih tekem in podnožje (sodniki, vodji, podpisi). Predlogo (varianti
    `SNTL_1` / `SNTL_23`) izbere liga (`liga.predlogaListka`); varianta določa
    oznako lige in prisotnost vrstice za delegata NTZS. Na strani je še živ
    izbirnik variante za ta natis. (Ne uporablja več komponente `Listek`.)
  - Listki so **črno-beli ne glede na temo** (fiksni `#000/#fff`, ne temo
    odvisne spremenljivke); sloge in `@media print` (`@page` A4,
    `break-inside: avoid`, `print-color-adjust: exact`) drži `slog.css`.
- Preverba pred zaključkom dela: `cd vmesnik && npm run build` (tsc + vite).

## Objava na splet

- Navodilo po korakih je `docs/OBJAVA.md`; datoteke postavitve so v korenu
  (`Dockerfile`, `compose.yaml`, `Caddyfile`, `.env.primer`) in
  `skripte/varnostna-kopija.sh`.
- **Mavnov profil `splet`** (`mvnw -Psplet package`) zgradi vmesnik in ga
  vloži v isti `.jar`; `SpletniVmesnik` ga postreže in vsako pot, ki ni
  datoteka in ne začne z `api/`, vrne kot `index.html` (sicer osvežitev na
  `/turnirji/1` vrne 404). Privzeti prevod ostane brez vmesnika, da je hiter.
- Profil `splet` (`application-splet.properties`) je za strežnik za Caddyjem:
  prazen CORS (isti izvor), `forward-headers-strategy`, brez sledi sklada v
  odgovoru. Vrata 8080 se na strežniku ne objavijo — do aplikacije se pride
  samo skozi Caddy, torej samo prek HTTPS (HTTP Basic bi bil sicer berljiv).
- SQLite baze **nikoli ne kopiraj z `cp`** med delovanjem; uporabi
  `sqlite3 ... ".backup"` (tako dela skripta za varnostne kopije).

## Kontekst projekta

- Razvojna strategija in prioritete: `docs/RAZVOJNI-NACRT.md`.
- Pravilnika NTZS (izvlečeno besedilo): `docs/pravilniki/pst.txt` in `pot.txt` —
  Priloge A/B/C v PST so predloge za prihodnji skupinski sistem in žreb z nosilci.
- Stari prototip (referenca, ne spreminjaj): `../poskus2` in `../aplikacija-turnirji`.
- **Uvoz zgodovine NTZS teče iz DVEH virov v enem zagonu** (`si.turnirko.uvoz`
  pod profilom `uvoz`; ob navadnem zagonu se ne sproži, zahteva prazno bazo):
  stara stran `stara.ntzs.si` za sezone 2012/13–2023/24
  (`si.turnirko.uvoz.stara`, navodilo `../uvoz-stara-ntzs/README.md`) in Stupa
  Events od 2024/25 naprej (`../uvoz-stupa/README.md`). Vira se ne prekrivata.
  - **Skupen šifrant je pogoj, ne podrobnost.** Ista oseba nastopa v obeh
    virih; `ZbirnikSifrantov` zato ključe označi z virom (`stara:3300`,
    `stupa:18896`), `SifrantiUvoz` pa osebe združi **po licenci NTZS** (v obeh
    virih ista oblika `059/15/16`). Po imenu se ne združuje — zlepilo bi
    soimenjake. Stara stran hrani ime in priimek **ločeno**, zato njena
    razdelitev povozi ugibanje `RazdelitevImena` tudi za zapise iz Stupe.
  - **Tekmovanja se uvozijo po datumu, v enem koledarju** (`UvozUkaz`) — turnir,
    kolo lige, turnir, kot so si sledili; ne najprej vsi turnirji in nato lige.
  - **`EloUvoz` ureja po datumu IN uri.** Ligaška srečanja uro imajo (2. SNTL
    odigra dve koli v enem dnevu), turnirske tekme ne — te dobijo polnoč in so
    tako na isti dan pred ligo. Znotraj tekmovanja ureja `zaporedje`, ki nosi
    tudi fazo: brez tega bi prvo kolo izločilnega dela padlo pred drugo kolo
    skupin.
  - Uvoznik ne uporablja storitev, zato **v sebi ponovi njihova merila** —
    katera tekma šteje v ELO, kateri izidi so odigrani; ob spremembi pravila v
    `TekmaStoritev`/`SrecanjeStoritev` popravi tudi `EloUvoz`. Entitete so po
    `save()` odklopljene, zato uvoz **ne sme brati lenih povezav** (klub igralca
    gre skozi `SifrantiUvoz.klubIgralca`, obračun ELO tekme naloži znova).
