# Pravila: lige

> Lige: format srečanja, prehodi, kvalifikacije, ekipe, razpored, termini, prestavitve, potek, lestvice, kader, menjave, brez boja, končnica.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

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
  Ob **nastanku** lige pridejo prehodi neobvezno zraven (`LigaVnos.prehodi`,
  obrazec nove lige deli polja s `PrehodiOkno` — `PrehodiPolja`); urejanje
  pravil jih ne bere.
- **Kvalifikacije med ligami** (V41, `KvalifikacijeStoritev`). Liga pove,
  koliko ekip tik pod napredovanjem (`st_kvalifikacije_gor`) oz. tik nad
  izpadom (`st_kvalifikacije_dol`) igra kvalifikacije s sosednjo ligo — kot
  napreduje/izpade del prehodov. Lestvica jih označi s conama
  `KVALIFIKACIJE_GOR/DOL` (svetlejši ton zelene/rjaste, ne nova barva).
  - **Kvalifikacije so SVOJA liga** (kot uvožene »I./II. SNTL«): nosi obe ligi
    (`id_kvalifikacije_visja/nizja`), pravila srečanja prevzame od višje,
    ekipe so **kopije s kadri** — zapisniki, termini, rating in lestvica
    tečejo po nespremenjeni kodi, lestvici obeh lig ostaneta nedotaknjeni.
    Ni nivo piramide (prehodi jo kot višjo/nižjo zavrnejo); piramida jo kaže
    kot vrstico »kval.« pod nivojem višje lige.
  - Nastane iz **končnih** lestvic obeh lig (oba redna dela odigrana), ustvari
    jo lastnik **višje** lige, pripada lastniku višje lige in **ne šteje** v
    mejo lig paketa. Način izbere organizator (`NacinKvalifikacij`): ena tekma
    ali serija (pari) oz. vsak z vsakim (mala liga, eno- ali dvokrožno).
  - **Pari so križni** (odločitev lastnika, okt 2026): najboljša ekipa višje
    lige z najslabšo nižje (8. A – 4. B, 9. A – 3. B), samo ob enakem številu
    ekip z obeh strani. Tečejo po kodi končnice z **enim krogom**
    (`KoncnicaStoritev.stKrogov`); prva stran serije je vedno ekipa višje lige
    (zato pri eni tekmi igra doma ona, končnica pa po strani ve izvorno ligo).
    Mala liga ostane v pripravi (žreb, termini kot pri navadni ligi); zgornja
    mesta (`st_napreduje`) igrajo v višji ligi.
  - Razveljavi se **v celoti** (`DELETE /lige/kvalifikacije/{id}`), dokler se
    ni začelo nobeno srečanje; razveljavitev same končnice je zanjo zaprta.
- **Ekipa ni nujno klub** (V11): `ekipa.id_klub` je neobvezen. *Prosta ekipa*
  (brez kluba) je zasedba, ki v šifrantu klubov nima zapisa in nastopa **samo
  v tej ligi** — rekreacijske in medpodjetniške lige; s tem šifranta ne zasuje
  z enkratnimi zapisi. Kader ostane **skupni register igralcev** (prosta je
  ekipa, ne igralci), zato rating in profili tečejo nespremenjeno. Ime je pri
  klubski ekipi neobvezno (sestavi ga `prikazanoIme()` iz kluba in zaporedne),
  pri prosti pa **obvezno** — to varuje `CHECK` v shemi in
  `LigaStoritev.dodajEkipo`. Ista metoda zahteva, da je **prikazano ime v ligi
  enolično** (primerja ga čez oba tipa, ne le `(klub, zaporedna)`): v
  razporedu, na lestvici in v zapisniku se ekipi ločita samo po njem.
  **Vsak `join fetch` na `ekipa.klub` mora biti LEVI** — notranji tiho izpusti
  celotno srečanje iz razporeda, lestvic in profilov (`EkipaRepozitorij`,
  `SrecanjeRepozitorij`, `TekmaSrecanjaRepozitorij`); regresija je
  `ProstaEkipaTest`.
- **Ime ekipe se sme spremeniti v VSAKEM stanju tekmovanja** (okt 2026,
  `PUT /lige/ekipe/{id}/ime` → `LigaStoritev.preimenujEkipo`, `PUT
  /dogodki/ekipe/{id}/ime` → `EkipeDogodkaStoritev.preimenujEkipo`). Odstranitev
  oz. odjava ekipe je vezana na pripravo, napaka v imenu pa se opazi, ko igra že
  teče. Ime ni pravilo tekmovanja: razpored, lestvica, mreža in zapisniki nosijo
  ekipo po id-ju in ime berejo sproti, zato popravek ničesar ne preračunava.
  - Velja tudi po zaključku; uvoženo tekmovanje ostane samo za branje, sicer
    sme lastnik (ali njegov klub) in admin — preverba je `preveriPoEkipi`.
  - Spremeni se samo LASTNO ime (`ekipa.ime`), klub in zaporedna ostaneta. Prazno
    ime pri klubski ekipi jo vrne na »Klub N«, prosta ekipa ga ne more izgubiti.
    Pravila so v `PravilaImenaEkipe`: 2–60 znakov (isti meji kot CHECK v shemi,
    zavrne se pred zapisom) in prikazano ime enolično v ligi oz. dogodku, brez
    razlike med velikimi in malimi črkami ter čez klubski in prosti tip; ime, ki
    ga ekipa že ima, ni trk.
  - **Kopije ekip v ligi kvalifikacij** se preimenujejo z izvirnikom, če so še
    enake (klub + zaporedna + lastno ime) — zveze med njima ni drugje. Kopija, ki
    jo je kdo preimenoval sam, ostane. Trk imena z ekipo druge lige v ligi
    kvalifikacij zavrne vse preimenovanje.
  - Vmesnik: `komponente/PreimenovanjeEkipe` — vrstica v razprti ekipi okna
    »Ekipe in kader« (liga v teku; okno v oknu ne, ker Escape zapre obe) in
    okno »Preimenuj ekipo« iz vrstice ekipe (priprava lige, ekipni dogodek, tudi
    po žrebu). Po shranjevanju se osvežijo vse poizvedbe razen šifrantov. Testi:
    `PreimenovanjeEkipeTest`, `PreimenovanjeEkipeDostopTest`, po en v
    `EkipniTurnirTest`, `KvalifikacijeTest` in `LastnistvoTest`.
- **Enakomerna razvrstitev je žreb po PARIH, ne premešan krožni sistem** (V16).
  `liga.enakomerna_razvrstitev` prižge dvoje: ekipe dobijo jakostni vrstni red
  (`ekipa.st_nosilca`, isti pojem kot `prijava.st_nosilca`) in žreb teče po
  `RazporedStoritev.enokroznoPoParih` namesto po navadni circle metodi.
  - **Par je i-ta ekipa zgornje polovice z i-to ekipo spodnje** (pri 10 ekipah
    A-F, B-G, C-H, D-I, E-J). Par nastopa kot celota: v enem **krogu** odigra
    oba dvoboja proti istemu nasprotnemu paru, zato vsaka ekipa v krogu dobi
    enega nasprotnika iz zgornje in enega iz spodnje polovice; sezona se za par
    konča z njunim medsebojnim srečanjem.
  - **Krog parov = DVE koli**, ker je kolo en igralni dan in ekipa v njem
    odigra eno srečanje: prvo kolo zgornja-zgornja in spodnja-spodnja, drugo
    navzkrižno. **Gostitelj je posamezna ekipa in ne par** — prvo kolo doma pri
    prvem paru, drugo pri drugem; sicer bi ena ekipa gostila vse štiri dvoboje
    kroga in bi se domače pravice čez sezono razšle (regresija:
    `poParihDomacaSrecanjaSoRazdeljena`).
  - **Pri lihem številu parov (10, 6, 14 ekip) ima liga eno kolo več in vsaka
    ekipa eno prosto kolo.** To ni izbira, ampak nujnost: lihega števila parov
    ni mogoče razdeliti na dvoboje parov, zato par, ki v krogu ostane brez
    nasprotnega para, odigra svoj medsebojni dvoboj in drugo kolo počiva. Pri
    sodem številu parov (8, 12, 16) nihče ne ostane brez nasprotnika, zato so
    medsebojni dvoboji parov **sklepno kolo** in kol je toliko kot doslej.
  - **Vrstni red se ureja kot celota** (`PUT /lige/{id}/vrstni-red`,
    `shraniVrstniRedEkip`) in mora našteti vse ekipe natanko enkrat — isto
    pravilo kot `IzborStoritev.shraniVrstniRed`. Zato enoličnosti
    `(id_liga, st_nosilca)` **ne** vsiljuje indeks: mesta se prepišejo vsem
    naenkrat in vmesna stanja bi ob preverjanju po vrsticah trčila. Varuje jo
    `prestevilci`, ki mesta vedno zapiše od 1 naprej (tudi ob dodajanju in
    odstranjevanju ekipe).
  - Mesto dobi **vsaka** nova ekipa, tudi v ligi brez oznake — tam ne pomeni
    nič, zato pa je lestvica pripravljena, če organizator oznako prižge.
    `LigaStoritev.ekipe` zato vrne ekipe **po jakosti** samo pri ligi z oznako,
    sicer po abecedi (`najdiZaLigoPoJakosti` proti `najdiZaLigo`).
- **Razpored nastane na dva načina; ročni vpis je enakovreden žrebu** (V26).
  `LigaStoritev.generirajRazpored` pare **izžreba**, `rocniRazpored`
  (`POST /lige/{id}/razpored/rocni`) jih **prebere od organizatorja**. Oba
  končata v isti točki (`zapisiRazpored`): srečanja dobijo termine iz semena in
  liga gre v `V_TEKU`, zato lestvica, termini, zapisniki in rating tečejo
  nespremenjeno. Loči ju samo `liga.rocni_zreb`.
  - **Zakaj obstaja:** liga, ki se je doslej vodila na roke, pride v aplikacijo
    z razporedom, ki je že narejen in razposlan igralcem. Naključni žreb bi ga
    zavrgel in ljudje bi imeli v rokah dva različna razporeda.
  - **Zastavica je JAVNA in ne tehnična opomba.** `LigaDto.rocniZreb` gre tudi
    gostu: ob desnem robu vrstice »Vsa kola« pod razporedom stoji droben napis
    »Ročni žreb« (`OznakaRocnegaZreba`), ki ob prehodu miške oz. ob dotiku
    odpre pojasnilo »Žreb ni bil naključen — razpored je vpisal organizator. Za
    pojasnilo se obrnite na organizatorja.« (`OPOMBA_ROCNEGA_ZREBA`). Igralec,
    ki je pare dobil po pošti, mora videti, da je to isti razpored — a cela
    vrstica nad razporedom (do okt 2026) je zvenela kot opozorilo, zato je
    zdaj oznaka (odločitev lastnika). Piše jo **samo**
    `LigaStoritev` (vpis prižge, žreb in razveljavitev ugasneta), zato se
    vpisan razpored in žreb ne moreta razglasiti drug za drugega.
  - **Strežnik preverja strukturo, ne popolnosti.** Zavrne ekipo z dvema
    srečanjema hkrati — v kolu z enim krogom dve srečanji v kolu (kolo je en
    igralni dan), v ligi z urami dve srečanji ob isti uri kola —, ekipo samo proti sebi,
    ekipo iz druge lige in vrzel med koli (kola tečejo od 1 naprej). Par, ki se
    ne sreča, in par, ki igra trikrat, pa **spusti skozi**: ročno vodene lige
    imajo tudi nepopolne razporede in popolnost je stvar tekmovanja, ne sheme.
    Nanjo opozori vmesnik (`RocniZrebOkno`), a shranjevanja ne ustavi — meja
    med napako in opozorilom je na obeh straneh ista.
  - **Predlog** (`GET /lige/{id}/razpored/predlog`, `predlogRazporeda`) vrne
    žreb **brez zapisa v bazo**: iz njega vmesnik sestavi prazno mrežo pravih
    mer (koliko kol, koliko srečanj v kolu) in jo na zahtevo napolni s pari, ki
    jih organizator popravi. Pravila razporeda tako ostanejo samo v
    `RazporedStoritev` in se kopija v vmesniku ne more raziti z njimi.
  - **Izbira ekipe, ki v kolu že igra, ekipi ZAMENJA** (vmesnik). Brez tega
    polnega kola ne bi bilo mogoče popraviti: vsaka ekipa je že nekje in
    izbirnik bi ostal prazen, organizator pa bi moral mesta najprej prazniti.
    Zamenjava podvojitve ne more ustvariti (število ekip v kolu ostane isto),
    izbira nasprotnika iz iste vrstice pa po istem pravilu obrne domačo pravico.
  - **Razveljavitev razporeda** (`DELETE /lige/{id}/razpored`,
    `razveljaviRazpored`) je **eno pravilo za oba načina** in ne izjema ročnega
    vpisa: liga se vrne v `PRIPRAVO`, srečanja (in z njimi termini kol) gredo,
    ekipe in kader ostanejo. Meja je prvo srečanje, ki je zapustilo stanje
    `RAZPORED` — tisto ima postavo in generirane tekme, morda z rezultati in
    obračunanim ratingom, in tega izbris ne sme tiho odnesti. V vmesniku je
    dejanje ponujeno **samo, dokler je mogoče**; trajno ugasnjen gumb za brisanje
    razporeda bi bil sredi sezone samo grožnja.
- **Termini kol so seme + ročni popravki, ne seznam datumov v pravilih.**
  Liga nosi `zacetek_prvega_kola` in `razmik_dni` (V10) — vpišeta se že ob
  ustvarjanju, ko ekip (in s tem števila kol) še ni. Datume vsem srečanjem
  napiše `LigaStoritev.zapisiRazpored` (`terminKola`) — sklepni korak žreba in
  ročnega vpisa — ker je šele takrat znano, koliko kol liga ima. Popravke po kolih dela `nastaviTermine`
  (`PUT /lige/{id}/termini`) — kot prehodi **v vsakem stanju lige** in ne pod
  zaklepom pravil, ker se kolo prestavi tudi sredi sezone. Prestavljeno kolo
  **ne premakne naslednjih** (ta so že objavljena) in semena ne popravi.
  **Dan** je last kola; ura je pri kolu z enim krogom ena za vsa srečanja,
  liga z urami kola (spodaj) pa ima uro pri vsakem srečanju.
  Ura `00:00` pomeni »ura ni določena« — vmesnik takrat izpiše samo datum.
- **Ure kola: kolo je VEČER z več krogi, ne en krog** (V31,
  `liga.ure_srecanj`, zapis `"18:30,19:45"`). Liga, ki se zbere en večer na
  teden, v kolu odigra toliko krogov krožnega sistema, kolikor je ur: ob prvi
  uri prvega, ob drugi drugega. **Vsaka ekipa v kolu torej odigra toliko
  srečanj, kolikor je ur, kol pa je toliko manj** (dve uri = pol manj kol).
  Ob isti uri igrajo vse ekipe hkrati, vsaka enkrat. `NULL` = kolo je en krog
  — enakovredna izbira, ne manjkajoča vrednost.
  - **Ni »razvrščanja po večerih«**: `RazporedStoritev.razpored(..., ure)`
    vzame pare krožnega sistema nespremenjene in krog `k` postavi v kolo
    `(k-1)/N + 1` ob uri `(k-1) % N`. Vsak z vsakim, domače pravice in žreb po
    parih zato veljajo brez posebne veje. Pri dvokrožni ligi sme večer združiti
    zadnji krog prvega dela s prvim povratnim — le tako je kol res N-krat manj.
  - **Ure so pravilo tekmovanja** (od njih je odvisen žreb), zato jih ureja
    `uredi` in se po žrebu zaklenejo. Vsaj **dve** (ena ura je navadna liga z
    uro v semenu — dveh zapisov za isto ne sme biti), največ
    `NAJVEC_UR_V_KOLU` (10), **strogo naraščajoče** (enaki uri bi pomenili dve
    srečanji vsake ekipe hkrati); 00:00 se ne primerja. Ura v semenu
    (`zacetek_prvega_kola`) je ura prvega kroga; `uporabiVnos` jo poravna s
    prvo uro seznama.
  - **Srečanje si zapomni svojo uro kola** (`srecanje.ura_v_kolu`, V32;
    `SrecanjeDto.uraVKolu`, prazno pri ligi brez ur). Iz začetka se je ne da
    zanesljivo prebrati: liga brez datuma ga ob žrebu nima, organizator pa ga
    sme prestaviti. Po njej `terminSrecanja` izračuna začetek, polnilo v
    `TerminiOkno` vrne uro lige, predlog (`ParRazporedaDto.uraVKolu`) in ročni
    vpis (`ParVnos.uraVKolu`) pa pomenita isto.
  - **Ekipa ne igra dveh srečanj hkrati — na treh mestih**: žreb (po zgradbi),
    ročni vpis (`rocniRazpored`: ključ je kolo + ura kola; pri ligi brez ur je
    ura vedno 0, zato je to celo kolo) in popravek terminov
    (`preveriHkratnaSrecanja`, po dejanskem začetku). Zadnji šteje le trke, v
    katerih je vsaj eno srečanje spremenil ta vnos — star zapis ne sme ustaviti
    nepovezanega popravka.
  - **Popravek ure je po srečanju** (`TerminiVnos.srecanja`, obvelja za
    terminom kola). Srečanja lige zato pridejo **po kolu in začetku**
    (`najdiZaLigo`, `najdiRednaZaLigo`): razpored, »naslednje srečanje« in
    koledar sledijo uri, ne vrstnemu redu zapisa.
  - Vmesnik: kolo z več začetki ima v razporedu nad vsako uro naslov s črto
    (`skupineKola` → `.liga__ura`), kolo pa nosi samo dan (`ureVKolu` v
    `LigaStran`). Prej je ura stala na mestu izida in ista ekipa se je v
    kolu pojavila dvakrat brez pojasnila. Srečanje, prestavljeno na drug dan,
    dobi naslov z dnevom. `RocniZrebOkno` srečanja kola razdeli po urah (tako
    jih ima papirnati razpored); zamenjava ekip velja samo med srečanji iste
    ure.
- **Prestavitev srečanja** (V47, okt 2026; `TerminiVnos.TerminSrecanja`,
  `Srecanje.prestaviVKolo` / `zacniCakatiNaTermin`). Organizator jo vpiše **samo v
  oknu »Termini kol«** (zavihek »Srečanja po kolih«, komponenta `SrecanjaPoKolih`:
  vsa kola s svojimi srečanji, filter ekipe, izberi srečanje → klikni kolo ali ga
  povleci; opozorilo, ne prepoved, ko ekipa v kolu igra več srečanj, kot jih ima
  običajno; do 31. 10. 2026 je bil razdelek »Prestavitve« z iskalnikom po
  srečanjih, ki je kazal osem zadetkov) — odločitev
  lastnika; gumba pri vrstici razporeda ni. Gre v istem `PUT /lige/{id}/termini`
  kot termini kol: najprej prestavitve, nato termini kol, nazadnje izrecni začetki.
  - **`kolo` je kolo, v katerem se srečanje IGRA**, ne kolo iz žreba: prestavljeno
    srečanje stoji v razporedu, v oknu ekipe, v koledarju in v poteku lige
    **samo v kolu igranja** (odločitev lastnika: »samo v kolu novega termina«, ne
    podvojeno), zato igralec v vsakem kolu vidi, s kom je igral in kaj je
    odigrano. Kolo iz žreba hrani `srecanje.izvirno_kolo` (prazno = ni
    prestavljeno): potrebujeta ga vrnitev in okno ekipe (kolo, v katerem je imela
    ekipa srečanje po žrebu, ni »prosto kolo«). Kolo je znano tudi, ko se je
    izpraznilo (kolo ali izvirno kolo kakega srečanja) — vanj se da vrniti.
  - **Prestavi se lahko tudi odigrano srečanje** (poznega kola, odigrano že zdaj)
    in neodigrano, ki bi moralo biti že odigrano (odločitev lastnika). Izid in
    stanje ostaneta; rating se ob prestavitvi ne preračuna (kot pri popravku
    termina kola — `Srecanje.casOdigranja` bere shranjeni vrednosti).
  - **»Termin še ni znan«** (`srecanje.caka_na_termin`, samo srečanje v
    `RAZPORED`, sicer zavrnitev): kolo ostane izvirno, začetka ni. Tako srečanje
    ne sodi v noben dan: razpored ga kaže pod gumbom **»?« na koncu traku
    »Vsa kola«** (odločitev lastnika; seznam nad kolom je bil najprej, a je bil
    viden ob vsakem kolu). »?« je še ena postaja traku (`KOLO_BREZ_TERMINA` = 0,
    `SrecanjaBrezTermina`; »Naslednje« z zadnjega kola vodi vanj) in obstaja samo,
    dokler je kaj brez termina. `PotekLige` ga ne šteje (ne zadrži kola, ni
    »naslednje«), okno ekipe ga uvrsti na konec z napisom »termin še ni znan«
    (z datumi bi sicer cel seznam padel na vrstni red kol) in ga ne šteje za
    »Naslednje«. Koledar ga izpusti sam (`najdiVObdobju` bere samo srečanja z
    začetkom).
  - **Dan prestavljenega srečanja je lahko svoj** (kolo je sicer en dan), zato
    datum kola določajo samo srečanja, ki v njem sodijo (`PotekLige.Termin.vDrugemKolu`,
    `pomozno/prestavitve.terminKola`) — sicer bi srečanje v četrtek premaknilo
    kolo, ki je bilo v torek. **Strežnik začetka ne ugiba**: vnos ga poda
    izrecno (`zacetek` null = brez termina), vmesnik prestavljenemu srečanju vedno
    pošlje svojega, sicer bi ga termin kola pogazil ali pa zadel ob uro, ob kateri
    obe ekipi v kolu že igrata (`preveriHkratnaSrecanja` to zavrne). Zato je ura
    pri novi prestavitvi prazna (00:00 = ni določena), vpiše jo organizator.
  - **Razlog** (neobvezen, ≤ 200 znakov) je **javen**; okno opozori, naj bo brez
    osebnih podatkov (zdravje, naslovi). `razlog` brez vrednosti ga pusti, prazen
    niz ga zbriše; z vrnitvijo v kolo iz žreba izgine.
  - Testi: `PrestavitevSrecanjaTest`, `PotekLigeTest` (prestavljeno in čakajoče).
    Vmesnik: `TerminiOkno`, `LigaStran` (`OpombaPrestavitve`, `CakajocaSrecanja`),
    `EkipaLigeOkno`, skupna pravila v `pomozno/prestavitve.ts`.
  - **Ni pokrito**: končnica lige (ima svoje termine, `KoncnicaStoritev`),
    ekipni turnirji in stran zapisnika srečanja (o prestavitvi molči).
- **Potek lige je ENO pravilo za vse poglede** (`PotekLige`, oktober 2026):
  »odigrano N od M kol« in naslednje kolo izpisujejo domača stran
  (`DomovStoritev`), seznam lig in glava strani lige (`LigaDto.odigranihKol`,
  `odigranaKola`, `naslednje` iz `LigaStoritev`) in organizatorski pregled —
  vsi iz `PotekLige.izracunaj`. Prej sta stran lige in seznam štela kolo šele,
  ko je bilo končano VSAKO srečanje: ob enem nevpisanem izidu 1. kola je
  Savinja B do 7. 10. kazala »0. od 6 kol · naslednje 24. 9.«, domača stran
  pa za isto ligo »1. od 6 kol · naslednje 15. 10.«.
  - **Kolo je odigrano, ko je njegov datum mimo** (najzgodnejši predvideni
    začetek srečanj, slovenski dan — `PotekLige.danes()`); današnje kolo je
    še naslednje. **Nikoli po prvem končanem srečanju**: ekipe se neuradno
    menjajo za termine in vsako kolo ima že eno končano srečanje
    (`DomovOdigranaKolaTest`). Kolo brez datuma je odigrano, ko je končano
    vsako njegovo srečanje; končnica (`serija`) v števcu ne šteje.
  - **Naslednje** je prvo nekončano srečanje, ki ga termin ni prehitel —
    srečanje z nevpisanim izidom in starim datumom NI naslednje. Domača stran
    poda tudi končnico (po rednem delu obljublja tekmo serije), `LigaDto` pa
    samo redni del: glavo strani po rednem delu nosi končnica sama.
  - Kadar naslednjega ni, liga pa ni zaključena (vsa kola so po datumu mimo,
    kak izid ni vpisan), glava piše »Zadnje kolo …« in ne »Končano« —
    značka ob njej še piše »V teku«.
  - **Kvalifikacije in končnica še vedno čakajo na VSE izide** (`vseKoncano`
    v `LigaStran`): nastanejo iz končne lestvice, datum tam ne zadošča.
  - Vmesnik izpiše potek s `potekKol` (»odigrano 1 od 6 kol«); vrstilni
    »0. od 6 kol« se je bral kot kolo, ki teče. Na telefonu glava nima več
    odstotka: dolžino vrstice je pojedel, isto pa pove že števec in palica.
    Regresiji: `PotekLigeTest` (pravilo) in
    `domacaStranSeznamInStranLigeRecejoIsto`.
- **Razpored pri neodigranem kolu izpiše termin, ne besede »razpored«.**
  Merilo je `metaKola` v `LigaStran`: odigrano kolo (po `LigaDto.odigranaKola`,
  isto pravilo kot glava) dobi datum in oznako »odigrano«, kolo, ki šele pride,
  pa termin (`oblikujTermin` → »ned, 4. okt · 18.00«, poudarjen z
  `.liga__kolo-termin`). Beseda »razpored« ostane samo, kadar termina ni —
  takrat organizator datumov ni vpisal. Razpored se odpre na naslednjem kolu.
  Srečanje brez izida, katerega dan je mimo (`brezIzida`), nosi na mestu
  izida »—«, pod vrstico pa napis »Izid ni vpisan« (`.liga__srecanje-opomba`)
  — samo title na telefonu ni bil berljiv, »18.30« izpred dveh tednov pa se je
  bralo kot prihodnost. Imena ekip v razporedu na telefonu se ne odrežejo,
  ampak prelomijo v drugo vrsto (»Zavarovalnica S…« ni bilo prepoznavno).
- **Liga ima tri lestvice, ne eno** (`LestvicaLigeStoritev`): ekipno (glavna),
  posameznikov (`/lige/{id}/lestvica-igralcev`) in dvojic
  (`/lige/{id}/lestvica-dvojic`). Zadnji dve **štejeta samo tekme te lige** —
  rating tega ne zna, ker teče čez vsa tekmovanja. Merilo je izkupiček:
  **zmage**, ob izenačenju uspešnost (deleža se primerjata navzkrižno, da ne
  odloča zaokroženi odstotek iz prikaza) in razlika nizov; praga najmanjšega
  števila tekem ni. Posamične tekme in dvojice sta **ločena seznama** (izida
  para ni mogoče pripisati posamezniku — isto pravilo kot pri ratingu), dvojica pa
  je **neurejen par**: ista igralca so ista dvojica doma in v gosteh.
- **Kader v oknu ekipe (klik na lestvici) je razvrščen po izkupičku, kader za postavo ne.**
  `LigaStoritev.kader` (`GET /lige/ekipe/{id}/kader`) vrne igralce po **zmagah
  za to ekipo v tej ligi** navzdol, ob izenačenju po manj porazih; ostalo
  razreši stabilnost razvrščanja, ki ohrani organizatorjev vrstni red in
  abecedo iz `KaderEkipeRepozitorij.najdiZaEkipo` (v pripravi so bilance 0 : 0,
  zato je izpis tam nespremenjen). Vprašanje kadra v oknu ekipe je »kdo
  ekipo nosi«. `SrecanjeStoritev.kader` (kadra v `SrecanjePodrobnoDto`) te
  razvrstitve **ne deli** — tam mesta A/B/C sledijo organizatorjevemu vrstnemu
  redu in bi drugačen vrstni red premešal postavo.
  Bilanca je bilanca **pri tisti ekipi** in ne v celi ligi:
  `LestvicaLigeStoritev.bilancePosamicnih` vrne `BilanceLige` s ključem
  (ekipa, igralec), ker sme liga brez `prepoved_dvojne_registracije` istega
  igralca voditi v dveh kadrih — njegov izkupiček pa tam ni isti (regresija:
  `bilancaKadraStejeSamoTekmeZaTistoEkipo`).
- **Kader lige se sme dopolnjevati tudi med sezono** (`LigaStoritev.dodajVKader`
  stanja lige ne preverja); zaklenjene so le ekipe (`preveriVPripravi`), ker je
  razpored razpored ekip. **Odstraniti pa ni mogoče igralca, ki je za ekipo
  nastopil ali je postavljen v tekmo, ki čaka** (`steviloNastopovZaEkipo`) —
  isto pravilo kot kader ekipnega dogodka. Zato je bilanca novega člana kadra
  vedno 0 : 0. V vmesniku okno »Ekipe in kader« lige v teku ekipo razpre v
  urejanje kadra (`UrejanjeKadra`, isto kot `KaderOkno` v pripravi).
- **Menjava je last ENE tekme srečanja, ne »od tu naprej«**
  (`SrecanjeStoritev.zamenjajIgralce`, `PUT /srecanja/tekme/{id}/igralci`).
  V ligi se igralci med srečanjem menjajo prosto — po dvojicah AB/XY ter A-X in
  B-Y lahko sledita C-Y in A-Z (A se umakne C-ju in se vrne na mesto B) —, zato
  bi pravilo »zamenjani ostane zunaj« tak vpis onemogočilo. Pravila, ki jih ne
  razbij:
  - **Postava ostane ZAČETNA postava** in oznaka tekme (»A-Y«) ostane mesto v
    njej; kdo tekmo res igra, stoji v tekmi. Rating, lestvica igralcev in
    bilanca kadra že berejo igralca iz tekme, zato zamenjani šteje brez veje.
  - **Samo tekma, ki ČAKA** (odigrana ima obračunan rating), igralec iz kadra
    **svoje** ekipe, pri dvojicah dva različna. Koliko tekem odigra en igralec,
    strežnik ne preverja — liga v aplikaciji tega pravila ne pozna.
  - **Oznaka menjave se izpelje, ne hrani** (`TekmaSrecanjaDto.menjavaDomaci/
    menjavaGost`, `SrecanjeStoritev.menjava`): mesto se prebere iz oznake
    tekme (pri dvojicah par, označen v postavi), zato velja tudi za uvožena
    srečanja; kadar mesta ni mogoče najti, se ne ugiba.
  - Ponovna shranitev postave tekme sestavi znova in menjave zavrže (dovoljena
    je le pred prvim rezultatom).
  - Regresije: `menjavaVeljaZaSvojoTekmoInStejeIgralcu_kiJeIgral`,
    `menjavaParaZaDvojice`, `menjavaOdigraneTekmeInIgralcaIzvenKadraSeZavrne`,
    `kaderSeMedSezonoDopolniOdstraniPaSeSamoKdorNiNastopil`.
- **Poraz brez borbe** (V29, `srecanje.brez_boja`, `liga.odbitek_brez_boja`):
  Pravila igranja v SNTL (20. člen in naslednji) izid registrirajo s 4 : 0 oz.
  5 : 0, »poraženi ekipi se odvzame še eno točko« od skupnega števila.
  `LestvicaLigeStoritev` odbitek odšteje od **skupnih** točk poraženca;
  medsebojni izid ostane izid srečanja (odbitek je kazen, ne rezultat
  dvoboja). Uvoz srečanje označi po zastavici `walkover` pri viru; razpored
  lige pod izidom izpiše »b. b.«. Liga iz aplikacije ima odbitek 0.
- **Končnica lige** (V28, `serija_koncnice`, `KoncnicaStoritev`): liga ob
  nastanku pove, ali jo ima (`koncnica_ekip` 2/4/8, `koncnica_zmag`).
  Nastane iz **končne** lestvice rednega dela in nikoli prej; vsi krogi
  naenkrat (višji s praznimi ekipami); pari po nosilskem vrstnem redu mreže
  (pri štirih 1-4 in 3-2); vse tekme serije vnaprej, neodigrane se ob odločitvi
  serije zbrišejo; prvo tekmo gosti slabše uvrščena ekipa, odločilno bolje;
  termin je last tekme serije in ne kola. **Lestvica rednega dela srečanj
  končnice ne šteje** (`najdiRednaZaLigo`), razpored rednega dela jih ne kaže.
  Razveljavitev končnice je mogoča, dokler se nobena tekma ni začela. Glava
  strani lige po rednem delu nosi stanje končnice (naslednja tekma oz.
  »Končano« z dnem zadnje tekme serije), ne zadnjega kola rednega dela.
