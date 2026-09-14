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
- **Ime pred priimkom, povsod.** Slovensko se oseba imenuje »Ana Novak« in tak
  je *vsak* izpis igralca — v zaledju ga sestavi `Igralec.polnoIme()`, v
  vmesniku pa vsako mesto, ki ime in priimek izpiše ločeno (izbirniki,
  šifrant, kader, predlogi računov). Obrnjeni vrstni red živi samo kot
  **urejevalni ključ**: `Igralec.abecedno()` (»Novak Ana«) in `ORDER BY
  i.priimek, i.ime` v repozitorijih — seznam torej *teče* po priimku, a se
  *bere* po imenu. `abecedno()` ne sme nikoli v DTO. Kjer DTO nosi `ime` in
  `priimek` ločeno (`LestvicaIgralcaDto`, `IgralecDto`, `DvobojDto.Igralec`),
  je to zato, da zna vmesnik razvrstiti po priimku — ne zato, da bi ju kje
  izpisal obrnjeno.
- **Starostni pas je izpeljanka, ne osebni podatek** (`StarostniPas`, polje
  `starostniPas` v `IgralecJavniDto`). Iz letnice se izpelje najožji pas, ki
  mu igralec ustreza (`U11`…`U21`, `CLANI`, `VETERANI`); brez njega organizator
  med tisoč igralci mladincev ne loči, datum rojstva pa ostane pod
  `/podrobno`. Pravilo je 11. člen PST: starost se meri na **31. december
  leta, v katerem se sezona začne**, zato je referenca sezonska (rez 1.
  julija, isti kot `sezonaIzDatuma` v vmesniku) in se pas od januarja do
  junija **ne premakne**. To je namerno drugačno od `KategorijaIgralca`, ki je
  starostno-**spolna** kategorija lestvice po koledarskem letu — pojma ne
  združuj, ker bi eden od obeh pogledov spremenil pomen. Ker so pasovi
  izključujoči, filter »U15« pomeni izbiro U11 + U13 + U15.
- **Admin geslo nima privzetka.** Če je baza prazna in
  `turnirko.admin.privzeto-geslo` ni nastavljen (oz. je krajši od 12 znakov),
  `ZacetniAdmin` ustavi zagon. Izjema je profil `namizni` (lokalna prenosna
  različica), ki obdrži `admin`/`admin`. Privzetka ne vračaj v
  `application.properties`.
- Vsaka sprememba domenske logike (žreb, rezultati, rating) MORA imeti test.
  Testi: `cd zaledje && mvnw test`.
- Statuse določa strežnik; prehodi stanj se preverjajo v storitvah.
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
    gostu: stran lige nad razporedom izpiše »Žreb ni bil naključen — razpored je
    vpisal organizator« (`OPOMBA_ROCNEGA_ZREBA`), ker mora igralec, ki je pare
    dobil po pošti, videti, da je to isti razpored. Piše jo **samo**
    `LigaStoritev` (vpis prižge, žreb in razveljavitev ugasneta), zato se
    vpisan razpored in žreb ne moreta razglasiti drug za drugega.
  - **Strežnik preverja strukturo, ne popolnosti.** Zavrne ekipo z dvema
    srečanjema v istem kolu (kolo je en igralni dan), ekipo samo proti sebi,
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
  - **K po negotovosti** (`kFaktor`): osnova **40**, nanjo se **seštevajo**
    pribitki po +8 za vse, česar o igralcu še ne vemo — manj kot 30 tekem,
    manj kot 10 tekem in prvih 15 tekem po vrnitvi. Novinec ima torej 56,
    vrnjeni novinec 64, ustaljen igralec 40; K je za oba igralca lahko različen.
    **Vrednosti niso ugibane**: umerjene so na 91.741 pravih tekmah po metodi
    »napovej, nato posodobi« (prequential log-loss). Prejšnje 48/32/20 z močjo
    margine 0,5 so dajale 0,4403, te 0,4184, optimum podatkov (K 56, moč 1,5)
    pa 0,4131 — izbrana je vmesna različica zaradi mirnejših skokov. Kdor
    parametre spreminja, naj jih izmeri, ne ugane.
  - **ničvsotno zaokroževanje** z `Math.rint` (pol na sodo), ne `Math.round`:
    pri **enakem** K je vsota sprememb natanko 0 tudi ob izenačeni napovedi
    (`Math.round` bi pri lihem K ob vsakem izenačenju vbrizgal +1). Pri
    različnem K (novinec proti ustaljenemu) vsota namenoma ni 0.
  - **set-margina po presenečenju** (`marginaMnozitelj`): K se množi glede na
    to, koliko je razlika v nizih presenetljiva (dejansko dobljeni nizi
    poraženca proti pričakovanim iz razlike ratingov), NE po surovi razliki.
    Favoritova gladka zmaga je pričakovana (majhen bonus), avtsajderjeva velik.
    Moč je **1,25** z mejama **0,35–1,65**. Meji nista okras: brez njiju bi pri
    veliki razliki v ratingu množitelj podivjal in bi ena sama presenetljivo
    gladka zmaga vrgla igralca čez pol lestvice.
  - **nizi poraženca se vzamejo po ZMAGOVALCU**, ne kot manjše od obeh števil.
    Pri končani tekmi je to isto, pri **predaji** pa ne: kdor preda pri vodstvu
    2 : 0, je »zmagovalcu« pustil nič dobljenih nizov in prejšnja koda je to
    brala kot gladko razbitje 3 : 0 — nasprotnik je za tekmo, ki jo je na mizi
    izgubljal, dobil najvišji možni bonus.
  - **pri predaji margine ni** (`izracunaj(..., predaja = true)` postavi
    množitelj na 1): predana tekma ni bila odigrana do konca, zato njen delni
    izid ne pove tega, kar model po nizih predpostavlja. Šteje samo, kdo je
    zmagal. Klicalca (`TekmaStoritev`, `SrecanjeStoritev`) zastavico izpeljeta
    iz `izid_tip`.
- **Novinec ne začne pri 1000, ampak pri STAROSTNEM SIDRU**
  (`starostno_sidro`, `SidroStoritev`, V21): mediana ratinga umerjenih in
  aktivnih igralcev istega spola in starosti. Brez tega se dva novinca v U11,
  ki igrata med sabo, ustalita pri isti številki kot dva novinca v U19 — kar
  ni res.
  - **Starost je po 11. členu PST** (`StarostniPas.letaVSezoni`) in merjena na
    dan TEKME, ne danes — sicer ponovni preračun ne bi dal istih številk.
  - **Beri ga v dveh delih.** **Oblika** (razlike med starostmi in spoloma) je
    izmerjena — ta reši U11/U19. **Raven** (skupni pribitek **+270** vsem
    vrsticam) je merilo skale in ne meritev moči: Elo nima absolutne skale,
    raven lestvice določajo izključno vstopne vrednosti novincev. Pri goli
    mediani lestvica pade (izmerjeno na 91.741 tekmah: povprečje 965 → 703, dno
    se zabije v mejo 100); s pribitkom ostane (965 → 968, najnižji 145).
    **Ob vsaki spremembi formule ali osvežitvi sidra je treba raven izmeriti
    znova** — ni ugibanje in ni okras.
  - **Tabela je POSNETEK in se NE sme izpeljevati iz lastnega izhoda.** Sidro,
    izračunano iz lestvice, ki so jo oblikovali novinci, zasidrani po prejšnjem
    sidru, je povratna zanka. Osvežitev je zavestna odločitev, ne opravilo v
    ozadju.
  - Surova mediana po letih je sunkovita in ponekod pada z leti; seme V21 je
    zato zravnano (drseče okno ±1 leto, monotono nepadajoče, odrasli 26+ ena
    vrednost z mostom od 21. leta).
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
  **12 mesecih** spet igra, ima **prvih 15 tekem** K večji za 8 — v letu dni se
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
  `margina`, `teza`, `pricakovano`, V24). Igralec, ki vidi »+27«, dobi pod
  grafom zapisan obrazec: **+27 = K 64 × 1,37 (nizi) × 0,75 (teža) × (1 − 0,58)**.
  - **Sestavine se ZAPIŠEJO in se ne računajo nazaj.** K je odvisen od števila
    tekem IN od tega, ali se je igralec takrat vračal; težo tekmovanja lahko
    kdo vmes spremeni. Poznejši izračun bi torej dal današnje številke za staro
    tekmo in bi lahko protislovil zapisani spremembi.
  - **K in `pricakovano` sta last IGRALCA, `margina` in `teza` pa TEKME.**
    Vsak igralec ima svoj K; pričakovani izid drugega je 1 minus prvega.
  - **Prazne so, kadar sprememba ne nastane po tem obrazcu**: postavitev in
    odbitek (tekme ni) ter **uvrstitev novinca**, kjer se rating izračuna znova
    iz vseh izidov prvega dne. Vrstica, ki ima tekmo in nima `k`, je torej
    natanko uvrstitev — a vmesnik tega ne ugiba: strežnik pošlje
    `ProfilDto.NacinSpremembe` (`KORAK`, `UVRSTITEV`, `POSTAVITEV`,
    `NEAKTIVNOST`, `ZUNANJA_UVRSTITEV`) in vsak način ima svojo poved.
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
    potrebuje (profil ga je prej klical dvakrat). Varuje ga
    `stetjeTekmovalnihTekemBereSamoPokrivniIndeks` (načrt pravega SQL-a
    Hibernata) v `LoceneLestviceTest`.
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
  srečanja, postavitveni zapis pa trenutek postavitve. Stolpec ima
  `@Convert(CasKotBesedilo.class)` iz istega razloga kot termini kol.
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
  pravi. Varuje `NacrtiDnevnikaTest`; nova poizvedba po tekmah gre tja.
- **Rating je izpeljanka: `PreracunRatingaStoritev.preracunajOd(datum)` ga zna
  sestaviti znova** (`POST /api/v1/rating/preracun?od=`, admin).
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
  `RazvrstitevStoritev` (zmage → porazi → **krog**: razlika nizov, nato
  razlika točk **samo iz tekem med izenačenimi**, rekurzivno), izločilni
  del po skupinah pa `SkupineStoritev` (generira se, ko so odigrane vse
  skupine — nujno pred preverbo zaključka dogodka; **samo** pri
  `SKUPINE_IZLOCILNI`). Izlocilno mrezo gradi ena metoda
  (`ZrebStoritev.zgradiIzlocilnoMrezo`), da jo delita oba sistema.
- **Žreb z nosilci je svoja storitev** (`NosilciStoritev`; 21. in 22. člen
  PST). `ZrebStoritev` samo prebere jakostni vrstni red (`IzborStoritev`), ga
  zabeleži v `Prijava.stNosilca` in iz razporeditve sestavi tekme:
  - **Skupine se polnijo po jakostnih pasovih.** Prvi pas se **ne žreba** —
    1. nosilec je prvi zapisani v skupini A, 2. v B … N-ti v N-ti skupini.
    Vsak naslednji pas (N+1…2N, 2N+1…3N …) se žreba, po enega igralca v vsako
    skupino; nepoln zadnji pas dobijo **naključne** skupine (te so večje).
    Razpored znotraj skupine je nato krožna metoda **brez mešanja**
    (`kroznePare`), zato nosilec skupine začne z najšibkejšim in dvoboj 1–2
    pade v zadnje kolo — isto kot pri formatu TOP.
  - **Mreža: 1. nosilec na vrh, 2. na dno** (ta dva se ne žrebata), 3. in 4.
    na svoji četrtini (s prvima dvema se srečata šele v **polfinalu**), 5.–8.
    na svoje osmine (**četrtfinale**), 9.–16., 17.–32. … enako po pasovih, ki
    se podvajajo. Nosi ga `NosilciStoritev.seedVrstniRed`; **obrat pri lihem
    indeksu** je tisto, kar 2. nosilca potisne na dno in ne na sredino mreže
    (prej je bilo tako). Prosta mesta zasedejo najvišje številke, zato jih
    dobijo najvišji nosilci in se dve nikoli ne srečata.
  - **Izločilni del po skupinah**: zmagovalec skupine A je 1. nosilec, B 2. …
    — skupine so nastale iz pasov, zato je njihov vrstni red hkrati vrstni
    red nosilcev. Drugouvrščeni se žrebajo na preostala mesta, vsak v
    **nasprotno polovico** od zmagovalca svoje skupine, zato se soigralca iz
    iste skupine srečata šele v **finalu**. Iz tega samo od sebe sledi, da
    zmagovalec skupine v 1. kolu nikoli ne igra z drugim zmagovalcem skupine
    (ali je prost ali igra drugouvrščenega). Da polovici vzideta natanko,
    poskrbi lastnost mreže: nosilski mesti 2k−1 in 2k sta vedno v **različnih**
    polovicah — ne dodajaj “varovalke”, ki bi to štela drugače.
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
- **`SKUPINE` = format TOP** (npr. TOP 24 kot 3 skupine po 8):
  - igra najboljših N prijavljenih (N = `steviloSkupin` × `velikostSkupine`);
    kdor ne pride v izbor, dobi status `REZERVA` in ne igra;
  - razporeditev je **zaporedna po jakosti** — skupina A dobi mesta 1–8, B
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
- **Odstop med tekmovanjem** (`TekmaStoritev.odstopiIgralca`): že odigrane
  tekme obveljajo, preostale dobijo nasprotniki kot `BREZ_BOJA` (in ne
  `PREDAJA`) — te tekme niso bile odigrane, zato se rating ne sme obračunati.
- **Neodigrane tekme ne štejejo v statistiko igralca.** Merilo je
  `IzidTekme.jeOdigrana()` (le `IGRANO` in `PREDAJA`); poizvedbe v
  `TekmaRepozitorij`, ki hranijo statistiko, ga morajo upoštevati. V lestvici
  skupine pa `BREZ_BOJA` normalno šteje kot zmaga nasprotnika, ker odloča o
  uvrstitvi — to sta namerno ločeni poti.
- **Točke po nizih so povsod neobvezne in povsod po istih pravilih.** Vpisujejo
  se pri turnirskih *in* ligaških tekmah; pravila (veljaven niz do 11 z razliko
  2, ujemanje z izidom, **mogoč vrstni red**) so v enem samem razredu
  `NiziPravila`, ki ga kličeta `TekmaStoritev.shraniTockeNizov` in
  `SrecanjeStoritev.vnesiRezultat` — dve kopiji bi se sčasoma razšli in ligaški
  zapisnik bi sprejel vnos, ki ga turnirski zavrne. Vrstni red: tekma se konča v
  trenutku odločitve, zato noben niz ne sme slediti izidu, ko je zmagovalec že
  dosegel dovolj nizov. V vmesniku isto vlogo igra skupna komponenta
  `komponente/TockeNizov` (vrstice vnosa + `preveriNize`).
  Shrani jih **svoja tabela na vrsto tekme**: `niz` (`id_tekma`) in
  `niz_srecanja` (`id_tekma_srecanja`, V15). Loženi sta, ker je ločena že tekma
  sama in `niz.id_tekma` je `NOT NULL` — skupna tabela bi terjala prezidavo in
  vsaki poizvedbi dodala pogoj, kateri od obeh stolpcev je zapolnjen. Ceno
  plača profil: `ProfilStoritev.tocke` zaradi dveh id-prostorov bere iz obeh
  repozitorijev. Točke po nizih **ne** vplivajo na noben izid — lestvica lige
  in rating štejeta dobljene nize iz same tekme; edina izjema je krog v skupini
  turnirja (`RazvrstitevStoritev`), ki tam sešteje `niz`.
- **Vneseni nizi napolnijo natisnjeni zapisnik.**
  `ZapisnikEkipnegaDvoboja` je papirnati obrazec NTZS: kar je vpisano, izpiše,
  prazne celice pa pusti za ročni vpis (obrazec se natisne tudi pred
  srečanjem). Stolpcev za nize je pet kot na papirju, pri ligi na 7 nizov pa
  sedem, da natis vpisanih točk ne odreže.
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
  Vrsta živi v `pomozno/vrstaZahtev.ts` in je **ena za vse** takšne preklope
  (spremljane lige, lige na domači strani): pisec baze je en sam, zato bi dve
  vrsti pomenili nič.
- **Lige na domači strani so uredniška odločitev, ne osebna nastavitev** (V17,
  `liga.na_domaci`). Admin postavi **največ dve** ligi
  (`LigaStoritev.LIG_NA_DOMACI`), ki ju vidijo gostje in vsi, ki si izbora niso
  sestavili sami — prej je o vhodni strani odločal vrstni red id-jev. Pojma ne
  združuj s spremljanimi ligami: tam gre za račun, tu za izložbo zveze.
  - Zastavica **ni pravilo tekmovanja**, zato je kot prehodi in termini kol
    zunaj `LigaStoritev.uredi` (ta se ob žrebu zaklene, ligo na domači strani
    pa je treba zamenjati prav takrat, ko teče): `PUT`/`DELETE
    /lige/{id}/na-domaci`. Lastništva **ne** preverja — organizator sme svojo
    ligo, domača stran pa ni njegova; koncno tocko varnostna veriga omeji na
    `ADMIN` in to pravilo mora stati **pred** splošnim »lige sme tudi
    organizator«.
  - Mejo dveh varuje storitev in ne shema (pogoj čez več vrstic bi v SQLite
    terjal prožilec), sporočilo pa **našteje ligi, ki sta na poti** — drugače
    admin ugiba, kaj naj odkljuka. Okno tretje kljukice sploh ne ponudi.
  - **Vrstni red odločanja v `DomovStoritev.povzetkiLig(idji, ogledane)` je
    vrstni red namernosti:** izbor računa → adminov izbor → gostove zadnje
    ogledane lige → lige v teku. Parametra sta zato **ločena** in ne en seznam:
    ogled ni izbira in ena odprta liga izpred tedna ne sme povoziti tega, kar
    je zveza postavila na vhodno stran.
  - Naslov sklopa je »Moje lige« **samo**, kadar sklop res kaže lasten izbor;
    sicer »Lige«. Naslov, ki bi adminovima ligama rekel »moje«, bi lagal.
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
  para ni mogoče pripisati posamezniku — isto pravilo kot pri ratingu), dvojica pa
  je **neurejen par**: ista igralca so ista dvojica doma in v gosteh.
- **Kader pod vrstico lestvice je razvrščen po izkupičku, kader za postavo ne.**
  `LigaStoritev.kader` (`GET /lige/ekipe/{id}/kader`) vrne igralce po **zmagah
  za to ekipo v tej ligi** navzdol, ob izenačenju po manj porazih; ostalo
  razreši stabilnost razvrščanja, ki ohrani organizatorjev vrstni red in
  abecedo iz `KaderEkipeRepozitorij.najdiZaEkipo` (v pripravi so bilance 0 : 0,
  zato je izpis tam nespremenjen). Vprašanje odprte vrstice lestvice je »kdo
  ekipo nosi«. `SrecanjeStoritev.kader` (kadra v `SrecanjePodrobnoDto`) te
  razvrstitve **ne deli** — tam mesta A/B/C sledijo organizatorjevemu vrstnemu
  redu in bi drugačen vrstni red premešal postavo.
  Bilanca je bilanca **pri tisti ekipi** in ne v celi ligi:
  `LestvicaLigeStoritev.bilancePosamicnih` vrne `BilanceLige` s ključem
  (ekipa, igralec), ker sme liga brez `prepoved_dvojne_registracije` istega
  igralca voditi v dveh kadrih — njegov izkupiček pa tam ni isti (regresija:
  `bilancaKadraStejeSamoTekmeZaTistoEkipo`).
- **Koledar je pogled po DNEVIH in ne po tekmovanjih** (`KoledarStoritev`,
  `GET /api/v1/koledar?od=&do=`, javen kot ostali GET-i). Nove sheme ne
  potrebuje — bere datume turnirjev in termine kol, ki že obstajajo. Trije
  premisleki, ki jih ne razbij:
  - **Zrnatost ligaškega vnosa je KOLO, ne srečanje.** Termin je last kola
    (`nastaviTermine`), zato bi vnos na srečanje isto ligo v istem dnevu
    izpisal petkrat; pari kola gredo v `srecanja` istega vnosa. V ključu
    zbiranja je vseeno tudi DAN — posamično prestavljeno srečanje sodi na svoj
    dan in ne k ostalim.
  - **Meji poizvedbe srečanj sta za dan širši od obdobja**, natančno omeji šele
    Java po datumu. `predviden_zacetek` je besedilo (`CasKotBesedilo`) in stari
    zapisi brez ure (`2026-10-04`) so pri primerjavi nizov krajši od
    `2026-10-04T00:00` — na prvi dan obdobja bi odpadli.
  - **Vrstni red vnosov je datum, nato ime.** Vmesnik iz njega izpelje vrstni
    red trakov v tednu in vrstic v seznamu, zato mora biti med klici enak.
- **Domača stran bere izpeljanke, ne surovih tekem.** `TurnirDto` nosi
  `faza`/`zmagovalec`/`zadnjiIzid` (izračun v `PovzetkiStoritev`,
  skupinske poizvedbe — nikoli po ena na turnir), `LestvicaIgralcaDto` pa
  `premik` (razlika mest proti stanju pred 30 dnevi) in `eloZgodovina`.
  **Črta rating teče po tekmah in ne po koledarju** — os je zaporedje obračunanih
  tekem, ne koledar (klub vnese celo kolo naenkrat). Okno črte in mejnik premika
  pa od V18 tečeta po `velja_ob` (čas tekme) in ne po `ustvarjen_ob` (čas
  vnosa): pri uvoženi zgodovini je slednji za vseh 183 tisoč zapisov isti dan,
  zato je črta pokazala štirinajst let kot zadnje leto, premik mesta pa se je
  meril proti točki znotraj uvoza.
- **Naključni par v sklopu »Ena na ena« ima vsaj eno medsebojno tekmo**
  (`StatistikaStoritev.nakljucniPar`, `GET /dvoboj/nakljucni`): izid 0 : 0 o
  igralcih ne pove ničesar. Žreb teče po **igralcih** in ne po tekmah —
  najprej igralec z odigrano tekmo, nato nasprotnik *izmed njegovih*;
  enakomerno izbrana tekma bi vlekla iste najbolj dejavne igralce, ker jih je
  v seznamu tekem največ. Merilo odigranosti v `idjiZOdigranoTekmo` in
  `nasprotniki` (obe tabeli) mora ostati **isto kot v `najdiDvoboje`** — sicer
  žreb ponudi par, ki mu pregled »1 na 1« izpiše 0 : 0. Kadar odigranih tekem
  ni (nova namestitev), žreb vrne kar dva aktivna igralca.
- **Zavihek »Zanimivosti« je ena storitev za obe tekmovanji**
  (`StatistikaTekmovanjaStoritev`, `GET /turnirji/{id}/statistika` in
  `GET /lige/{id}/statistika`, javna kot ostali GET-i). Turnir in liga hranita
  tekme v ločenih tabelah, zgodbe pa so iste, zato se obe strani najprej
  prevedeta v vmesni `Nastop` (točke nizov **vedno z vidika zmagovalca**) in
  skupne postavke se računajo enkrat. Pravila, ki jih ne razbij:
  - **Turnirski zavihek teče čez VSE dogodke turnirja**, ne po kategorijah —
    na ravni ene kategorije je tekem premalo, klub in »V številkah« pa tam
    izgubita pomen. Viden je **že med tekmovanjem** (`vTeku` to pove).
  - **Padca rating zavihek nima.** Vrstica je samo `vzponi`; v klubu, kjer se vsi
    poznajo, je razglasitev največjega padca dneva edina postavka, ki bi komu
    škodila. Ne dodajaj je »zaradi simetrije«.
  - **Prazna postavka je odsotna postavka.** Uvožena zgodovina brez ratingov,
    liga brez vpisanih točk po nizih in turnir v prvi uri nimajo istih
    podatkov; ničla bi trdila, da se nekaj ni zgodilo. Pod `PRAG_TEKEM` (10)
    zavihka sploh ni (`dovoljPodatkov`), vmesnik pa gumba ne ponudi že prej
    (`odigranihTekem`, oz. pri ligi odigrano vsaj eno kolo).
  - **Dvojice ne vstopajo v vrstice o posamezniku** (isto pravilo kot pri ratingu).
    Štejejo v »V številkah« in v svojo vrstico. Edina izjema je »Največ tekem«,
    kjer se štejejo **nastopi** in ne izkupiček — a v svoj števec.
  - **»Prvi naslov« se meri po datumu začetka turnirja in strogo »prej«.**
    Merilo »vsi njegovi naslovi so s tega turnirja« bi lanskemu turnirju
    vrstico odvzelo v trenutku, ko isti človek zmaga še enkrat — zapis o
    preteklosti se ne spreminja. Turnir brez datuma ne more biti »prej«.
    Uvoz zgodovine `koncno_mesto` piše (uradna mesta Stupe, sicer pravilo
    sistema v `KoncnaMestaUvoza`), zato vrstica velja tudi za uvožene turnirje.
  - **»Srečanje na nož« je najtesnejše odločeno srečanje, ki ga je odločila
    zadnja odigrana tekma.** Merilo »brez nje zmage še ne bi imel« je odvisno
    od `zmag_za_srecanje`: liga s pragom se ob odločitvi ustavi, zato zadnja
    tekma zmago **vedno** prinese in loči šele tesnost izida; liga, ki odigra
    vse tekme, pa lahko konča 7 : 3 in zadnja tekma ni odločila ničesar.
  - **Poizvedbe so po TEKMOVANJU in ne po seznamu id-jev tekem** — velik turnir
    ima nekaj sto tekem, sqlite pa omejuje število vezanih parametrov. Skupaj
    jih je pet (turnir) oz. štiri (liga), nobena ni na tekmo.
- **Leno nalaganje:** kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije.
  Vsaka poizvedba, katere rezultat gre v DTO, mora z "join fetch" vnaprej naložiti
  vse povezave, ki jih DTO bere (kraj, igralca, klub) — sicer na pravem strežniku
  poči LazyInitializationException, testi z @Transactional pa tega NE ujamejo.
  Regresijski testi za to: `LenoNalaganjeTest` (namenoma brez @Transactional).
  Pri ekipnih dogodkih jih drži `LenoNalaganjeEkipnoTest`. **Pot
  `x.povezava.id` v JPQL (Hibernate 7) bere tuji ključ brez združitve** —
  vrstica z NULL povezavo (srečanje turnirja nima lige, prijava ekipe nima
  igralca) zato vrne `null` in ne izpade iz rezultata; poizvedba, ki po njej
  združuje, potrebuje izrecen `JOIN` ali `IS NOT NULL`.
- **Uvoženo tekmovanje je samo za branje — za vse, tudi za administratorja**
  (`turnir.vir`, `liga.vir`, V27; `STUPA` ali `STARA_NTZS`). Vir resnice je
  zveza: popravek v Turnirku bi naslednji uvoz povozil. Preverba vira stoji v
  `LastnistvoStoritev` **pred** preverbo vloge, sporočilo pa pove, kje se
  popravi. Izjemi sta opis lige v piramidi (prehodi) in uredniška izbira lig na
  domači strani — nista pravilo tekmovanja. Uvoz sam piše mimo storitev
  (repozitoriji), zato ga pravilo ne ustavi. Vmesnik dejanja skrije
  (`smem = … && vir == null`) in pod naslov izpiše `.oznaka-vira`.
- **Sinhronizacija s Stupo je ročna in dokazana** (`si.turnirko.uvoz.stupa`,
  stran `/uvoz`, API `/api/v1/uvoz/stupa/**` samo za `ADMIN` — poročilo nosi
  datume rojstva). Potek za eno tekmovanje:
  1. **Predogled** (`UvozStupeStoritev.predogled`): posnetek dogodka pri viru
     (`StupaOdjemalec` → mapa JSON z oznako `{id}-{yyyyMMdd-HHmmss}` in
     zgostitvijo SHA-256), preslikava in uskladitev v transakciji, ki se
     **razveljavi**.
  2. **Uvoz** (`uvozi`): ista preslikava nad **istim posnetkom** z odločitvami
     admina; transakcija se potrdi samo, če poročilo `dovoljuje()` (brez napak,
     brez nerazrešenih odločitev, vse obvezne preverbe se ujemajo), sicer je
     zagon `ZAVRNJENO`. Po potrditvi `preracunajOd(dan tekmovanja)`. Vsak zagon
     ima vrstico v `uvoz_zagon`; enak posnetek kot pri zadnjem uspehu je
     `BREZ_SPREMEMB` (razen `vsili`).
  - **Ena preslikava za sprotni in zgodovinski uvoz** (`UvozStupeStoritev.izvedi`,
    `PreslikavaTurnirjaStupe`, `PreslikavaLigeStupe`, `SrecanjaStupe`,
    `IdentitetaStupe`). Dve kopiji bi isti dogodek zapisali različno.
  - **Ponovni uvoz obdrži id-je** (`zunanja_povezava`: vir + vrsta + id pri
    viru → id v Turnirku za igralca, klub, turnir, dogodek, ligo, ekipo,
    srečanje): turnir, dogodki, lige, ekipe lige in srečanja lige ostanejo,
    vsebina pod njimi se zbriše (`CiscenjeUvoza`, nativni SQL) in zapiše znova.
  - **Uskladitev po zapisu** (`UskladitevStupe`) bere bazo po izpraznjeni seji
    in primerja z virom **tekmo za tekmo** (status, zmagovalec, nizi, brez boja,
    preneseni izid, napredovanje v mreži, posamične tekme in igralci srečanj,
    točke lestvice, zmagovalci serij). Uradna mesta v skupinah in na lestvici
    so **neobvezna** preverba: pravilo izenačenih je v Turnirku zavestno drugo
    (razlika, ne količnik — glej `RazvrstitevStoritev` in lestvico lige).
  - **Istovetnost oseb** (`IdentitetaStupe`): odločitev admina → povezava →
    licenca NTZS **samo skupaj** z datumom rojstva in spolom (licenca ni ključ
    osebe) → ime in datum rojstva (pri strogem načinu odločitev) → nov
    igralec. Stupa hrani podatke osebe **po prijavah**, ne v profilu (isti
    igralec je v enem dogodku brez datuma rojstva, ime se sproti popravlja),
    zato zgodovinski uvoz poda `ZnaneOsebeStupe` iz vseh posnetkov, sprotni pa
    osebo brez imena in priimka, datuma ali spola da v odločitev s seznamom
    `manjka` — admin podatke vpiše (`UvozZahtevaDto.Odlocitev`) ali izbere
    igralca.
  - **Zapisan samo zmagovalec je odigrana tekma z izidom 0 : 0.** Stupa pri
    nekaterih srečanjih vpiše zmagovalce podtekem brez nizov (in izida
    srečanja ne sešteje — `SrecanjaStupe.izidEkipneTekme` ga prešteje iz
    podtekem). Rating tako tekmo bere po `IzidTekme.samoZmagovalec` — brez
    margine, kot predajo — sicer bi 0 : 0 štel kot gladko zmago. V aplikaciji
    take tekme ni mogoče vnesti.
  - **Točkovanje lige se prebere iz uradne lestvice** (`PreslikavaLigeStupe.
    tockovanje`): pravila SNTL se med sezonami razlikujejo (2024/25 točka za
    poraz, 2025/26 ne). Išče se zmaga/neodločeno/poraz in odbitek za poraz
    brez borbe, pri katerih izkupički srečanj dajo natanko uradne točke vseh
    ekip; kadar jih ni (vir sam s sabo ni skladen, kazenske točke), ostane
    2-1-0 z odbitkom 1 in preverba točk postane neobvezna. Pri sodem številu
    tekem (SNTL z dvojicami: 10) je neodločeno dovoljeno — vir 5 : 5 zapiše
    brez zmagovalca.
  - **Izbrisana ali izključena prijava brez tekem ni udeleženec**
    (`is_deleted`, `is_excluded`): 2. SNTL M 2025/26 ima izključeno podvojeno
    prijavo kluba, ki je stala na lestvici kot »NTK Vesna (2)« z 0 srečanji.
    Izključen udeleženec, ki je igral (ekipni DP U17 2026), ostane — njegove
    tekme so odigrane.
  - **Turnir, ki se še ni začel** (kategorije brez stopenj pri viru), se sme
    uvoziti kot turnir v pripravi brez dogodkov — tekmovanja prinese ponovni
    uvoz z istim id-jem. Poročilo to pove z opozorilom, stran pa namesto
    »ujema se v vseh preverbah« izpiše, da preverb ni.
  - **Skupine za mesta pri viru so vsaka svoja stopnja** (»1.–4.« stopnja 2,
    »5.–8.« stopnja 3); `stopnja` ostane vrstni red vira (bere ga vrstni red
    ratinga v dnevu), vmesnik pa skupine s `prvoMesto` zbere pod en naslov
    »Finalne skupine«.
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

## Vmesnik (vmesnik/)

- **Oblikovni sistem je zavezujoč:** `vmesnik/turnirko-profil-redesign/project/DESIGN.md`
  (značaj »športni zapisnik na papirju«). Če predlog nasprotuje temu dokumentu,
  se popravi predlog, ne dokument. Ključna pravila: `border-radius: 0` povsod,
  brez senc (edina izjema je podčrtaj aktivne navigacijske postavke), brez
  gradientov razen 8 % polnila pod črto grafa, brez emojijev in ikon (edini
  ikoni sta logotip in postavke spodnje vrstice na telefonu — odobrena izjema,
  glej DESIGN.md, točki 9 in 12), naslovi levo poravnani, 8 px ritem,
  zadetkovna površina na dotik ≥ 44 px. Barve pomenijo: modra `#0088CE` = dejanje/podatek, zelena
  `#7BB900` = uspeh/napredovanje, rjasta `#B23A1E` = izguba/napaka/brisanje.
- **Modra ima dve vrednosti in ju ne smeš zamenjati.** `--barva-glavna`
  (`#0088CE`) **riše** — črte, palice, polnilo grafa, obroba gumba, fokus; tam
  ni besedila. Kjer modra postane **podlaga pod besedilom** (glavni gumb,
  značka »V teku«, blok ratinga), velja `--barva-glavna-polna` (`#0071AB`), ker je
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
- **Zavihek »Zanimivosti« pozna dve obliki in nič več**
  (`komponente/ZanimivostiTekmovanja.tsx`, slog razdelek 15b): **zgodba** je
  enkraten dogodek (presenečenje, obrat, najdaljši niz) v obliki mono oznaka →
  stavek z imeni → mono kontekst; **lestvička** je primerjava (vzpon rating, zid,
  klubi) in uporablja običajno `.vrstica`. Kartic s številkami tu ni.
  - Imena so **brez glagolov** (»A proti B«, ne »A je premagal B«): zapisnik
    nikogar ne sklanja po spolu, tekmo pa opiše izid.
  - Seznam nikoli ne dobi štirih enakih blokov zapored — »Prvi naslov« je zato
    lestvička s kategorijo namesto številke in ne štiri zgodbe.
  - **Na strani lige sta zavihka dva pasova.** Mobilni (`.liga__zavihki`) je
    nad 640 px skrit, ker sta tam lestvica in razpored oba vidna, zato ima
    široki pogled svoj preklop dveh gumbov (`.liga__zavihki-namizje`,
    »Lestvica in razpored« ↔ »Zanimivosti«). Trije gumbi bi iz lestvice in
    razporeda naredili zavihka — to je zavestno drugače.
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
  - **Zvezno merilo je `ObmocjeFiltra` (dve polji »od«/»do«), ne pas naštetih
    razredov.** Meja, ki jo organizator potrebuje, je meja *tega* tekmovanja
    (»od 1200 navzgor«); vnaprej narisani pasovi bi jo znali samo približati.
    Namestnici polj sta dejanski najmanjša in največja vrednost v seznamu.
    Dve posledici: postavka **brez** vrednosti (igralec brez ratinga) ob
    vpisani meji odpade, in območje — za razliko od naštetih skupin — **lahko
    vrne prazen seznam**, ker meja ni izbira med možnostmi s števci. Prazno
    stanje mora zato ponuditi »Počisti filtre«. Žetoni območij tečejo po
    **vseh** območjih in ne po trenutno smiselnih (kot pri skupinah): ozko
    iskanje merilo iz okna umakne, vpisana meja pa še naprej reže — žeton, ki
    bi takrat izginil, bi pustil filter, ki ga ni mogoče ne videti ne
    odstraniti.
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
    sestaviti. **Prikazani seznam se vedno oštevilči od 1 naprej** — številka
    pove mesto v tem, kar gledalec gleda, ne v celi lestvici. Filter »U19«,
    ki se je začel pri 35., se je bral kot izsek sredine, koliko mladincev je
    pred tem igralcem, pa je bilo treba šteti na roke. Globalno mesto po
    ratingu v vrstici (`Vrstica.mesto`) vseeno ostane: po njem teče
    razvrstitev »Rating« in izenačenja pri drugih merilih — a se ne izpiše.
- **Blok »Dodaj igralce« (`DodajanjeIgralcev` v `DogodekStran`) je iskalnik s
  filtri, ne seznam vsega.** V šifrantu je po uvozu zgodovine NTZS več tisoč
  igralcev; prej je moral organizator do vsakega prevoziti šifrant cele
  države. Nad seznamom stojita iskalnik (ime in priimek, brez šumnikov in po
  besedah — `pomozno/iskanje.ts`, isto pravilo kot izbirnik v »Ena na ena«) in
  ista krmila kot na lestvici: skupine **starost** (`starostniPas`), **spol**
  in **klub** ter območje **rating od–do**. Pravila, ki jih ne razbij:
  - **Iskanje zoži seznam PRED filtri**, zato so števci ob merilih števci
    tega, kar organizator vidi (isto kot na lestvici).
  - **Izbrani, ki jih trenutna merila ne pokažejo, se izpišejo v svoji
    skupini nad seznamom** (»Izbrani zunaj seznama«). Gumb prijavi tudi
    tiste, ki so med iskanjem naslednjega igralca padli iz pogleda — izbor,
    ki ga ne vidiš, je past. Vrstica, ki merilom ustreza, ob kljukici **ne
    odskoči**: ostane, kjer je (drugače kot izbor lig, ki ima za to posnetek).
  - **Izriše se največ `NAJVEC_VRSTIC` (200) vrstic in rez se izpiše.** Ves
    seznam v DOM pomeni tisoč vrstic ob vsaki vtipkani črki; tiho odrezan
    seznam pa bi trdil, da igralca ni.
  - Števca »N od M na voljo« na telefonu **ni**: v vrsti bi bil tretji del in
    izbor razvrstitve bi stisnil v »Razvrsti: P ▾«. Koliko jih ostane, pove
    gumb v oknu z merili.
- **Barva v koledarju pove, katere VRSTE je tekmovanje — in to je edina taka
  raba barve v vmesniku.** Dva tona (`--barva-ton-1` turnir, `--barva-ton-2`
  ligaško kolo) so izrecna izjema od DESIGN.md, zapisana tam v razdelku
  »Paleta koledarja«; pogoji izjeme veljajo vsi hkrati in nova barvna raba
  drugod ostane prepovedana. Pravila, ki jih ne razbij:
  - **Ton je pomen, ne identiteta.** `tonVnosa` (`pomozno/koledar.ts`) je
    `vrsta === 'TURNIR' ? 1 : 2` in nič več. Prej je bilo tonov šest in so se
    dodeljevali po vrstnem redu pojavitve — barva je bila last POGLEDA in ne
    lige, zato je pod mrežo morala stati legenda z imeni. Ta je rasla s
    pogledom (en mesec uvožene zgodovine ima tudi šestnajst tekmovanj) in je
    zavzela več prostora kot mreža sama. **Legende ni več**; nadomešča jo
    stalni ključ (`KljucKoledarja`: Turnir · Ligaško kolo · Odigrano), katero
    tekmovanje je katero pa pove seznam ob mreži.
  - **Mreža so divi z `role="table"`, ne `<table>`.** Večdnevni turnir je EN
    pas čez dneve (`.koledar__trak`, `grid-column: N / span M`) in tabela čez
    celice ne zna risati. Teden je `position: relative` mreža sedmih celic,
    nad njo pa absolutno pozicioniran pas trakov (`.koledar__trakovi`).
    Branje bralnika zaslona ostane isto prek `role="table"/"row"/"cell"`.
  - **Razporeditev trakov v pasove je čista funkcija** (`trakoviTedna`):
    presek vnosa s tednom, razvrstitev po razponu navzdol (dolgi pasovi
    zgoraj, sicer se kratki zataknejo pod njimi) in požrešna razporeditev v
    prvi prosti pas. Kar v `najvecPasov` ne gre (4 na strani, 3 v sklopu in na
    telefonu), se **prešteje in izpiše kot »+N«** v vsakem dnevu, ki ga
    zaseda — tiho odrezan trak bi pomenil dan, ki v mreži trdi, da je prazen.
  - **Odigrano zbledi, ne spremeni barve**: trak `opacity: 0.32`, vrstica
    seznama `0.62`. Brez tega prihajajoče in odigrano izgledata enako.
  - **Mere mreže so spremenljivke, ne podvojena pravila.** Ista mreža služi
    strani (92 px celica), sklopu na domači strani (`.koledar--sklop`, 58 px)
    in telefonu (60 px); razlika so samo vrednosti `--koledar-*` v `slog.css`.
  - **Vrsto krmili segmentirani izbirnik nad mrežo, ne okno z merili.**
    Skupina `vrsta` je v `SKUPINE` označena `zunanja: true` — stanje ostane v
    istem izboru `useFiltri` (nič vzporednega), a je v oknu in med žetoni ni.
    Dve krmili za isto merilo eno vrsto narazen sta past.
  - **Sklop na domači strani naloži štiri mesece, mreža pa kaže enega.** Sklop
    »Naslednje« mora imeti kaj pokazati tudi v mesecu brez tekmovanj (poletni
    premor), sicer je koledar tam prazen kvadrat brez pojasnila. Listanje po
    mesecih (in s tem po uvoženi zgodovini) pripada `/koledar`, ne sklopu.
    Sklop stoji **pod Lestvico in nad »1 na 1«**: mreža meseca kot najvišji
    sklop strani je bila stena črt, preden je gledalec prišel do imena.
  - **Izbrani dan živi v naslovu (`/koledar?dan=2026-10-04`), filtri v stanju
    strani.** Dan je kraj, do katerega vodi povezava z domače strani in ki ga
    je smiselno deliti; filter je pogled nanj — isto pravilo kot pri iskanju na
    lestvici. Pari kola se izpišejo **samo pri izbranem dnevu**: cel mesec z
    devetimi ligami bi jih naštel nekaj sto.
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
- **Točka grafa ratinga in vrstica v »Odigrane tekme« sta ista tekma.** Zato
  `TockaGrafa` nosi poleg nasprotnika tudi `tekmovanje` in `del` — isti zapis
  kot `TekmaProfila` (`ProfilStoritev.graf` ju prepiše iz istega `Nastopa`) —
  in klik na točko skoči na to vrstico. Brez imena tekmovanja skok rating ne
  pove ničesar: isti nasprotnik se v seznamu ponovi tudi desetkrat, ker igra
  v ligi in na turnirjih. Vez drži par `(idTekme, ligaska)`: turnirske in
  ligaške tekme imajo ločeni zaporedji id-jev, zato je ključ vrstice
  `tekma-t12` / `tekma-l7` (`kljucTekme` v `ProfilStran`). Točka brez
  `tekmovanja` para v seznamu nima (postavitveni rating) in ni klikljiva.
  Skok je **mehak samo na kratke razdalje** — pri uvoženi zgodovini je seznam
  dolg 80 000 px in mehko drsenje čez to je zabrisan blisk, ne pot.
  **Obdobje grafa krmili spustni meni** (30 dni, 3 meseci, 6 mesecev, 1 leto,
  vse; privzeto 3 meseci) — pas gumbov je z vsakim novim obdobjem rasel čez
  naslovno vrstico.
- **Točka grafa ratinga nosi dva časa in nista isto.** `kdaj` je trenutek
  **obračuna ratinga** (`rating_zgodovina.ustvarjen_ob`) in po njem so točke
  urejene — vodoravna os je zaporedje obračunanih tekem, ne koledar. `datum`
  pa je dan **tekme**: datum turnirja oz. srečanja, isti kot v vrstici seznama
  tekem (`ProfilStoritev` ga vzame iz istega `Nastopa`). Izpisani datum in
  izbrano obdobje tečeta po `datum`, ker je pri uvoženi zgodovini vseh
  200 000 obračunov nastalo ob uvozu — po `kdaj` bi desetletje tekem padlo v
  en sam dan in nobeno obdobje ne bi odrezalo ničesar. Prazen je `datum` samo
  pri postavitvenem ratingu (tekme ni), zato tam obvelja `kdaj`. Regresija je
  `tockaGrafaNosiDatumTekmeInNeDnevaObracuna`.
- **Igralec se izbere z vpisom imena, ne s spustnim seznamom**
  (`komponente/IzbirnikIgralca.tsx`): po uvozu zgodovine je v šifrantu več
  tisoč igralcev in `<select>` je bil neuporaben. Ujemanje je **brez šumnikov
  in po besedah** (»krizan« najde Križana, »novak ana« pa Ano Novak) —
  iskalnik, ki zahteva strešico, v dvorani ne pomaga. Klub stoji ob imenu, ker
  se soimenjaka drugače ne ločita; predlogi ležijo **čez** vsebino
  (absolutno), da vsak vtipkani znak ne premika vsebine pod poljem, in so na
  desni strani semaforja zrcalno usidrani (`.enanaena__stran--2`). Komponenta
  je **deljena** (»Ena na ena« in »Kaj prinese tekma«), zato so razredi
  `.izbirnik-igralca__*` in ne `.enanaena__*` — dve kopiji istega comboboxa bi
  se razšli v tipkovnici in dostopnosti. Oznaki »Medsebojno · vsa tekmovanja«
  in »Naključni par« sta samo v razširjeni različici `EnaNaEna`
  (`pokaziZgodovino`, stran dvoboja); na domači strani ju nadomesti naslov
  sklopa.
- **Sklop »Kaj prinese tekma« je napoved, ne obračun** (`NapovedTekme.tsx`,
  `NapovedTekmeStoritev`, `GET /igralci/{id}/profil/napoved?nasprotnik=`).
  Igralec izbere kateregakoli nasprotnika in vidi, koliko ratinga bi mu
  prinesla zmaga oz. vzel poraz, če bi tekmo odigrala **zdaj**. Pravila, ki
  jih ne razbij:
  - **Ena številka bi bila laž.** Sprememba je `K × margina × teža ×
    (izid − pričakovano)`; margina je odvisna od **izida v nizih**, teža pa od
    **ravni tekmovanja** — obojega pred tekmo ni. Zato je vrstica za vsak
    možni izid (3:0 … 0:3, urejeni od najboljše zmage do najhujšega poraza) in
    preklopnik ravni; povprečje čez to bi dalo številko, kakršne ne bi
    prinesla nobena prava tekma.
  - **Ravni izračuna strežnik, vmesnik ne množi s težo.** Vsak zmnožek je
    zaokrožen posebej (`Math.rint`), zato `0,75 × prikazana številka` ni to,
    kar bi tekma res prinesla.
  - **Napoved mora povzeti vse, kar `RatingStoritev` pripravi pred izračunom**
    — število tekem (K), sledilnik vrnitve in starostno sidro za igralca brez
    ratinga —, sicer napoved in obračun ne dasta iste številke. To drži test
    `napovedanaSpremembaJeTista_kiJoTekmaZaresPrinese`, ki isto tekmo najprej
    napove in nato zares odigra.
  - **Česar napoved ne posnema, pove naravnost.** Odbitka za neaktivnost ne
    uveljavlja (zapadle je že uveljavilo dnevno opravilo), uvrstitve novinca
    pa ne računa — kdor igra svoj prvi dan, dobi opozorilo `PRVI_DAN`, igralec
    brez ratinga pa `BREZ_RATINGA`. Opozorila poimenuje **strežnik**, vmesnik
    iz njih napiše poved (isto pravilo kot `ProfilDto.NacinSpremembe`).
  - **Sklop je zasebni del profila** (`Samo zate`): vidi ga igralec sam in
    admin. Lastništvo preveri `DostopDoProfila` — isto pravilo kot pri
    analizah, zato živi na **enem** mestu in ne v vsaki storitvi posebej.
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
- **Kaj pomeni klik na tekmo, odloči stran** (`KlikTekme` v `TekmaKartica`:
  `klikljiva`, `naKlik`, `namig`) — kartica mreže in vrstica seznama ne ugibata
  po stanju tekme. Posamično tekmovanje: organizatorju vnos rezultata.
  **Ekipni dogodek**: vsakemu gledalcu zapisnik srečanja (javen), prenesen izid
  finalne skupine vodi v zapisnik predtekmovalnega srečanja, organizatorju
  pa klik na neodločeno tekmo ponudi izbiro med zapisnikom in izidom brez
  boja (`EkipnaTekmaOkno`, `VnosRezultataOkno samoBrezIgre`). Ekipe in kadre
  ekipnega dogodka kaže in ureja `EkipeDogodka` (priprava: prijava ekipe,
  sestava kadra z `IzbirnikIgralca`; med tekmovanjem samo dopolnitev kadra).
  Skupine z več stopnjami (skupine za mesta) so razdeljene z naslovi
  stopenj; tekma za 3. mesto in tolažilna mreža stojita pod glavno mrežo
  (`TolazilniDel`).
- **Končnica lige ima svoj pogled** (`KoncnicaLige`): na namizju preklop
  »Lestvica / Končnica« nad lestvico, na telefonu zavihek; liga samo s
  končnico (kvalifikacije med ligami) kaže serije kar na strani. Razpored
  rednega dela srečanj serij ne kaže (`idSerija == null`).
- **Stran `/uvoz` (Uvoz NTZS, samo admin)** vodi sinhronizacijo s Stupo:
  sezona → tekmovanja s stanjem zadnjega uvoza → predogled (preverbe, napake,
  odločitve o istovetnosti z vpisom manjkajočih podatkov, novi igralci s
  popravkom imena, opozorila) → uvoz → dnevnik zagonov. **Dovoljenje za uvoz
  velja samo za predogled z istimi odločitvami**: vsaka sprememba odločitev
  gumb »Uvozi« ugasne, dokler se predogled ne ponovi (strežnik bi uvoz sicer
  zavrnil).
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
- **Uvoz zgodovine NTZS teče iz DVEH virov v enem zagonu** (`UvozUkaz` pod
  profilom `uvoz`; ob navadnem zagonu se ne sproži, zahteva prazno bazo):
  stara stran `stara.ntzs.si` za sezone 2012/13–2023/24
  (`si.turnirko.uvoz.stara`, navodilo `../uvoz-stara-ntzs/README.md`) in Stupa
  Events od 2024/25 naprej (posnetek `../uvoz-stupa/posnetek.ps1`, navodilo
  `../uvoz-stupa/README.md`). Vira se ne prekrivata.
  - **Stupa gre skozi ISTO preslikavo kot sinhronizacija med sezono**
    (`UvozStupeStoritev.izvedi`, samodejni način istovetnosti, `ZnaneOsebeStupe`
    iz vseh posnetkov) — dogodek za dogodkom, vsak v svoji transakciji in z
    uskladitvijo. Dnevnik `uvoz_zagon` po izgradnji pozna vse dogodke; `NAPAKA`
    pomeni, da je dogodek zapisan, a se z virom ne ujema (vredno ga je uvoziti
    znova na strani `/uvoz`).
  - **Skupen šifrant je pogoj, ne podrobnost.** Ista oseba nastopa v obeh
    virih; `SifrantiUvoz` osebe stare strani združi **po licenci NTZS** (v
    obeh virih ista oblika `059/15/16`), `IdentitetaStupe` pa osebe iz Stupe
    poveže z njimi po licenci **in** datumu rojstva. Po imenu se samodejno ne
    združuje — zlepilo bi soimenjake. Stara stran hrani ime in priimek
    **ločeno**, zato razdelitev iz registra povozi ugibanje `RazdelitevImena`.
  - **Tekmovanja se uvozijo po datumu, v enem koledarju** — turnir, kolo lige,
    turnir, kot so si sledili; ne najprej vsi turnirji in nato lige.
  - **Rating se na koncu preračuna v celoti** (`PreracunRatingaStoritev`),
    vrstni red tekem (datum in ura, faza) pa je v `VrstaRatinskeTekme` — isto
    pravilo kot pri vsakem preračunu.
