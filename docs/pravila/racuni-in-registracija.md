# Pravila: racuni in registracija

> Računi, gesla, registracija in potrditev e-pošte, stran /racuni, pogoji in zasebnost.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Admin geslo nima privzetka.** Če je baza prazna in
  `turnirko.admin.privzeto-geslo` ni nastavljen (oz. je krajši od 12 znakov),
  `ZacetniAdmin` ustavi zagon. Izjeme ni (namizna različica je odšla s
  prenovo baze). Privzetka ne vračaj v `application.properties`.
- **Gesel ni mogoče prebrati** (v bazi je le zgostitev). Zato admin gesla ne
  "vidi", ampak ga računu igralca **nastavi** — po svoji izbiri
  (`RacuniStoritev.nastaviGeslo`) ali naključno (`ponastaviGeslo`) — in ga
  izroči igralcu. Nastavljanje tujega gesla ne zahteva starega (admin ureja
  tuje račune); zamenjava lastnega gesla ga zahteva (`zamenjajGeslo`).
- **E-pošta velja šele s kodo** (V33, `RegistracijaStoritev`). Registracija
  ustvari račun v `CAKA` z `email_potrjen_ob = NULL` in lastniku naslova pošlje
  šestmestno kodo; »naslov ni potrjen« je **izpeljanka**, ne novo stanje
  (`Uporabnik.jeEmailPotrjen()`), obstoječi računi so ob migraciji dobili
  datum nastanka. Pravila, ki jih ne razbij:
  - **Odgovori ne razkrivajo obstoja naslova.** Registracija, ponovno
    pošiljanje in pozabljeno geslo vrnejo vedno isto (odgovor je izpeljan
    samo iz vnosa); lastnika obstoječega računa obvesti pošta, klicatelja ne.
    Napačna, potekla in neobstoječa koda dobijo isto sporočilo
    (`NAPACNA_KODA`), tudi čas je enak (`KodeStoritev.preveriNeznanega`).
    Drži `AvtentikacijaDostopTest`.
  - **Nepotrjen naslov nova registracija zamenja**, potrjen, adminov in
    **zavrnjen** račun pa naslov zasedejo. Nočno čiščenje
    (`CiscenjeRacunovStoritev`, 3.45) po 48 urah pobriše nepotrjene, po 30
    dneh račune brez skrbnikovega soglasja; zavrnjenih ne (adminova odločitev).
  - **Kode** (`potrditvena_koda`) so BCrypt zgostitve, žive so največ ena na
    račun in namen (`NamenKode`: EPOSTA 10 min, SKRBNIK 24 h, GESLO 10 min),
    po 5 napačnih vnosih propadejo. Milijon možnosti brez teh mej ni nič —
    omejitve so obramba, ne udobje. Množični izbris poteklih
    (`pobrisiPotekle`) gre mimo enote dela, zato jo **izprazni**
    (`clearAutomatically`); pred izbrisom računa se njegove kode pobrišejo
    tudi v enoti dela (`KodeStoritev.pobrisiVse`), ne le v bazi — sicer
    Hibernate ob izpisu naleti na kodo, ki kaže na izbrisan račun.
  - **Omejevalnik poskusov** (`OmejevalnikPoskusov`, v pomnilniku, en
    strežnik): kode 1/min in 5/h na naslov, 20/h na IP; prijava v 15 min
    največ 10 neuspehov za **račun z istega IP**, 100 za račun skupaj in 50 za
    IP → `OmejitevPrijavFilter` vrne 429 **pred** preverbo gesla, uspešna
    prijava števca računa pobriše (`PrijavaDogodki`), novo geslo s kodo vse
    (`pocistiPrijaveRacuna`). Meja samo po računu bi bila vrata za nagajanje
    (desetkrat napačno geslo zaklene tuj račun), zato je nizka meja vezana na
    par račun + IP. Odgovor registracije za zaseden naslov stane enako
    (BCrypt se izračuna vedno). Testi, ki isti naslov registrirajo večkrat,
    kličejo `pocistiVse()`.
  - **Pošta gre ven šele po potrditvi transakcije** (`PostaStoritev`), sicer
    bi koda za razveljavljen račun prišla do prejemnika. Kanal izbere
    `turnirko.posta.nacin`: `dnevnik` (razvoj: besedilo s kodo v dnevnik),
    `smtp` (`spring.mail.*`, brez gostitelja se zagon ustavi), `pomnilnik`
    (testi berejo kodo iz `PomnilniskiPosiljatelj`, ki se ne odloži — testna
    transakcija nikoli ne potrdi). Naslov prejemnika v dnevniku zakrij.
  - **Mlajši od 15 let** (ZVOP-2) navedejo naslov starša oz. skrbnika, ki
    dobi svojo kodo; brez nje računa ni mogoče povezati z igralcem. Meja je
    `RegistracijaStoritev.STAROST_SKRBNIKA`, računa jo strežnik, vmesnik
    polje le pokaže.
  - **Samodejna povezava z igralcem** (`poskusiSamodejnoPovezavo`): ob
    potrjenem naslovu (in skrbniku) se ime, priimek (brez šumnikov in velikih
    črk, `RacuniStoritev.normaliziraj`) in **datum rojstva** ujamejo z
    natanko enim aktivnim igralcem brez računa → `POTRJEN`,
    `vir_povezave = SAMODEJNO`. Dva zadetka ali nobeden pomenita čakanje na
    admina; **organizator kluba ne potrjuje** (odločitev lastnika, sep 2026).
    Admin samodejno povezavo vidi in jo sme razvezati (`razvezi`), po tem
    račun spet čaka. Admina štejejo samo zahteve s potrjenim naslovom
    (`steviloCakajocihNaAdmina`).
  - **Admin dobi e-pošto, ko račun začne čakati nanj**
    (`ObvestiloAdminuStoritev`, `posta/SporociloAdminu`, okt 2026): kdo (ime,
    naslov, želeni klub), kateri paket (plačan paket je v zadevi in na vrhu;
    paket se bere iz `narocnina`, ki jo ob plačilu ustvari webhook: Premium
    pri igralcu, Basic/Plus/Pro pri organizatorju, brez vrstice »Brezplačno«),
    zakaj čaka (igralec: ni zapisa / več zapisov / zapis že ima račun;
    organizator: vedno ga potrdi admin) in gumb v Dostope. **Ne ob
    registraciji**, ampak ko je pogoj izpolnjen (`obvestiAdminaCeCaka`):
    potrjen naslov IN soglasje skrbnika, če je potrebno — zato sta klica na
    obeh poteh (`potrdiEposto`, `potrdiSkrbnika`) in pogoj postane resničen
    natanko enkrat na račun. Igralec in organizator imata isto sporočilo, ki se
    loči po vlogi (zadeva »račun« / »organizator«, poziv »Poveži račun z
    igralcem« / »Potrdi organizatorja«). Datuma rojstva in naslova skrbnika
    ni v pošti (admin ju vidi v Dostopih). Naslov je `turnirko.posta.admin`
    (`TURNIRKO_POSTA_ADMIN`, privzeto info@turnirko-nt.si, prazno = brez
    obvestil), pošiljatelj je običajni `turnirko.posta.od`. HTML sloga delita
    obvestilo po kolu in ta pošta prek `PostniOkvir`. Varuje `RegistracijaTest`
    (razdelek »Obvestilo adminu«) in `SporociloAdminuTest`.
  - **Admin se ne registrira in gesla ne ponastavlja po pošti**: najmočnejši
    račun ostane izven te poti (`Vloga.ADMIN` se v `RegistracijaStoritev`
    povsod izloči).
  - **Navijač** (`Vloga.NAVIJAC`, V55, okt 2026; faza 0 predloga »paket
    Navijač«): oseba, ki ne igra — starš, soigralec, klubski človek. Prej si je
    tak človek lahko odprl samo račun igralca, ki se ni mogel povezati z nobenim
    zapisom, obvisel je v `CAKA` in admin je dobil e-pošto o čakajočem računu.
    Pravila, ki jih ne razbij:
    - **Admina ne čaka**: potrjen naslov (in soglasje skrbnika pri mlajših od 15
      let, zato je datum rojstva obvezen kot pri igralcu) račun potrdi
      (`RegistracijaStoritev.potrdiNavijaca`, oba klica ob kodi);
      `obvestiAdminaCeCaka` navijača izloči izrecno. `PotrditevOdgovorDto.navijac`
      vmesniku pove, da je račun pripravljen.
    - **Z zapisom med igralci se ne primerja** (tudi ob ujemanju imena in datuma)
      in ga admin ne poveže (`RacuniStoritev.potrdi` ga zavrne). Varuje ga tudi
      shema: `CHECK (vloga <> 'NAVIJAC' OR id_igralec IS NULL)`.
    - **Brezplačen**: plačljiva registracija (`PlacilaStoritev.pripraviRegistracijo`)
      in kode popusta ga zavrnejo, nadgradnja na Premium tudi (»Premium je paket
      za igralca«). Pravica v verigi je `ROLE_NAVIJAC` (`CAKAJOCI`, dokler ni
      potrjen) — odpre le osebne poti, ki zahtevajo prijavo (izbor lig,
      obvestila). Ker preverbe Premium in organizatorja povsod sprašujejo »je
      IGRALEC / ORGANIZATOR«, navijač nikamor ne pade po pomoti; nova preverba
      vloge naj ostane v tej obliki in ne »ni ORGANIZATOR«.
    - `RegistracijaVnos.navijac` se izključuje z `organizator`.
    - V55 prezida `uporabnik` z izklopljenimi tujimi ključi (izven transakcije):
      na tabelo kažejo vrstice z `ON DELETE CASCADE` in `SET NULL`. Preverjeno
      na kopiji produkcije: števila vrstic odvisnih tabel enaka, `foreign_key_check`
      prazen. Testi: `NavijacTest`, `AvtentikacijaDostopTest`
      (`potrjenNavijacImaOsebnePotiInNicVec`).
    - **Spremljani igralci** (V57, okt 2026; zadnji del faze 0): račun spremlja ljudi, ne le lige.
      Tabela `spremljan_igralec` (račun + igralec, kot `spremljana_liga`), `/api/v1/domov/moji-igralci`
      (GET/PUT/DELETE; osebna pot, pravilo v verigi stoji pred »GET je javen«) in
      `GET /domov/moji-igralci-povzetki` (`SpremljaniIgralciStoritev`, `DomovIgralecDto`).
      - **Meja je pravilo paketa in ne sheme**: `NAJVEC_BREZPLACNO` = 1 za vse vloge (tudi za igralca s
        Premium: ta je paket za lasten profil). Ob doseženi meji PUT vrne 409 s sporočilom, koga račun že
        spremlja; drugega igralca ne zamenjamo tiho (staršu bi ob pomoti otrok izginil z domače strani).
        Plačljiv Navijač (faza 1) mejo dvigne, shema ostane. Račun igralca sebe ne spremlja.
      - **Povzetek bere samo javno**: ime, klub, rating, mesto (`ProfilDto.Uvrstitev`), zadnje 3 tekme in
        naslednje ligaško srečanje ekipe (isto pravilo kot `PotekLige`: ne končano, ne čaka na termin,
        termin ni mimo) ali prihajajoči turnir prijave. Zasebnih analiz profila ni (vidi jih samo igralec).
      - **Združitev igralcev izbor preusmeri** (`IgralciZdruzitevStoritev`): račun, ki je spremljal oba
        zapisa, obdrži enega; račun, ki je sam obdržani igralec, sebe ne spremlja.
      - Vmesnik: gumb »Spremljaj« (`GumbSpremljanja`) pod vrstico kluba na profilu,
        `pomozno/spremljaniIgralci` (preklop NI optimističen: meja je lahko dosežena), sklop
        `MojIgralecSklop` med vrhom in lestvico domače strani (gost ga nima; račun brez izbora ga vidi samo
        kot navijač, kjer pove, kako se igralec doda). Gost po kliku dobi kratko registracijo (`izvor:
        'spremljanje'`, `igralec`), po prijavi ga račun začne spremljati sam.
      - **Izkaznica** (nov. 2026, `MojiIgralciIzkaznica`, DESIGN.md 5g): ena izkaznica naenkrat, igralci so
        zavihki. `DomovIgralecDto` nosi še `ime`/`priimek` ločeno, `forma` (zadnjih 5 odigranih, najnovejša
        prva), `sezona` (bilanca od 1. julija — `Sezona.zacetek`, isti rez kot sezonski zapisnik; null brez
        tekem) in `zgostitev` javne profilne slike (`ProfilneSlikeStoritev.vidne`). Izpeljava besedil je v
        `pomozno/mojiIgralci.ts`, posnetki: `/dev/posnetki?okno=moji-igralci` (stanje, st, zacetni, sirina).
      - Testi: `SpremljaniIgralciTest`, `AvtentikacijaDostopTest` (`gostNimaSpremljanihIgralcev`).
    - **Plačljiv Navijač in njegove ugodnosti** (V58–V61, okt 2026; faze 1–3 predloga, NI preverjeno
      proti živemu Stripu ne pravemu potisnemu strežniku). Paket `NAVIJAC` (`Paket`, `CenikStoritev`):
      19,99 €/leto, **samo letno** (mesečne cene ni; Stripe vzame 2,2 % + 0,25 € na plačilo). Cena je
      na dveh mestih kot pri Premium: `CenikStoritev` in `CENA_NAVIJAC_LETNO` v `api/tipi.ts`.
      - **Kdo ima kaj** (`NarocninaStoritev`): `imaNavijaca` (veljaven paket NAVIJAC), `imaPremium` (lasten
        ALI družinski), `imaDruzinskiPremium`, **`imaSirokoSpremljanje` = Navijač ALI Premium ALI admin** —
        to je pogoj za neomejeno število spremljanih igralcev, e-pošto po turnirju, osebni koledar, potisna
        obvestila, sezonski zapisnik in napoved poljubnega para. Nova preverba paketa naj ostane v tej obliki.
      - **Plačljiva registracija in nadgradnja** (`PlacilaStoritev`): `RegistracijaVnos.navijac` skupaj s
        paketom NAVIJAC gre skozi Stripe Checkout (`CakajocaRegistracija.navijac`, V58), račun brez plačila
        ostane brezplačen navijač. Brezplačen navijač se nadgradi prek iste poti kot igralec
        (`pripraviNadgradnjo` je skupna). Kode popusta imajo vlogo (`za_vlogo` + `NAVIJAC_MESECI`, V58):
        brezplačni meseci paketa Navijač za nov račun navijača.
      - **Spremljani igralci**: brezplačen račun 1, s paketom neomejeno (`SpremljaniIgralciStoritev`); družina je
        v povzetkih prva.
      - **E-pošta po turnirju** (`ObvestilaPoTurnirjuStoritev`, `obvestilo_turnirja`): ko je turnir
        zaključen, prejemnik s paketom, ki spremlja nastopivšega igralca, dobi mesto, izkupiček in spremembo
        ratinga. **Vsak turnir enkrat**; samo turnir, zaključen v zadnjih 7 dneh (uvožena zgodovina ni novica);
        isto stikalo in odjava kot obvestila po kolu (`obvestila_po_kolu`, žeton odjave).
      - **Osebni koledar .ics** (`OsebniKoledarStoritev`, `OsebniKoledarKontroler`): Google in Apple koledar ne
        znata poslati gesla, zato naslov `/api/v1/koledar-ics/{žeton}.ics` varuje naključen žeton računa
        (`uporabnik.zeton_koledarja`, unikaten delni indeks). Žeton odpre samo ta koledar; zamenja se z enim klikom
        (`POST /moj-koledar/zamenjaj`). **Paket se preveri ob vsakem branju** (potekel = 403, koledar v telefonu
        ostane, a se neha posodabljati). Stalen UID (`srecanje-{id}`, `turnir-{id}`): prestavitev dogodek
        premakne in ga ne podvoji. V koledar gredo le javni podatki. Pot je v verigi javna (`GET /koledar-ics/**`),
        `/moj-koledar*` zahteva prijavo.
      - **Družina** (V59, `DruzinaStoritev`): račun s paketom Navijač prosi za povezavo z največ DVEMA igralcema
        v mladinskem pasu U11–U21 (isto merilo kot cena Premium za mlajše; igralec brez datuma rojstva ni primeren).
        **Povezavo vedno potrdi administrator** (V3 iz varnostnega pregleda: zasebna statistika tujega otroka ne sme
        priti do nikogar samo z imenom in datumom rojstva); zahteva (`CAKA`) ne odpre ničesar. Potrjena povezava
        odpre navijaču zasebno statistiko (`DostopDoProfila.preveriLastnistvo` pozna družino) in igralčevemu računu
        da **Premium, dokler paket navijača velja** (`imaDruzinskiPremium`, `RacuniStoritev.profil` ga javi kot
        `zDruzinskimPremiumom`). Ko igralec preraste U21, povezava velja do konca plačanega obdobja (`velja_do`,
        zapiše ga dnevno opravilo, ne branje). Zavrnjena vrstica ostane, da je oseba ne ponavlja tiho. Poti
        `/druzina/admin` in odločitve so `hasRole("ADMIN")` PRED splošnimi pravili `/druzina*` v
        `VarnostneNastavitve`. Admin dobi e-pošto ob zahtevi (brez datuma rojstva).
      - **Potisna obvestila** (V60, `PotisnaObvestilaStoritev`, `pomozno/WebPush`, `VapidKljuci`): Web Push z
        JDK-jevo kriptografijo (RFC 8291 aes128gcm, RFC 8292 VAPID), **preverjena z uradnim testnim vektorjem
        RFC 8291** (`WebPushTest`) — ne pa z živim FCM/Mozilla/Apple. Ključi: `java -cp target/classes
        si.turnirko.pomozno.VapidKljuci` izpiše par; nastavitve `TURNIRKO_PUSH_JAVNI_KLJUC`,
        `TURNIRKO_PUSH_ZASEBNI_KLJUC`, `TURNIRKO_PUSH_SUBJEKT` (npr. `mailto:info@turnirko-nt.si`). **Brez ključev
        je vse ugasnjeno** (naprave ni mogoče vpisati, opravilo ne dela nič; `GET /push/kljuc` vrne prazno, vmesnik
        kartice ne pokaže). Pasti: (1) **SSRF** — naslov vpiše odjemalec, zato strežnik pošilja samo na FCM, Mozilla,
        Apple in Windows push (https, privzeta vrata), preverba ob vpisu IN ob pošiljanju; (2) pošiljanje je ZUNAJ
        transakcije (SQLite en pisec, HTTP traja sekunde); (3) tekma se oznani enkrat (`potisni_dogodek`, ključ
        tekma + igralec; zbiranje se ob zagonu začne pri največjem id-ju dnevnika, da se stare tekme ne oznanijo);
        (4) naprava, ki jo storitev zavrne z 404/410, se izbriše; (5) samo račun z veljavnim paketom. Vmesnik:
        `public/sw.js` (prikaz + klik, nič se ne predpomni), `pomozno/potisnaObvestila` (dovoljenje samo iz klika;
        na iPhonu Push obstaja šele, ko je stran na začetnem zaslonu — kartica to pove), kartica na strani Naročnina.
      - **Podpora klubu** (V61, `PodporaKlubaStoritev`): navijač s paketom izbere klub; ko ima klub 10/20/30
        podpornikov, **organizator kluba** (`uporabnik.id_klub`, ki ga potrdi admin) dobi 10/20/30 % popusta na
        **prvo plačilo** organizatorskega paketa (nakup ali nadgradnja, Stripov kupon `once` kot kode popusta).
        Šteje se samo račun, ki paket plačuje ZDAJ (število se bere ob vsakem vprašanju, ne hrani). **Izplačil ni.**
        **Popusta ob obnovi ni** — bi terjal poseg v Stripovo naročnino pred vsakim obdobjem, ki ga nismo
        preverili. Imen podpornikov ne vidi nihče. Vmesnik pove omejitve, preden navijač izbere.
      - **Sezonski zapisnik** (`strani/SezonskiZapisnikStran`, `/igralci/:id/sezona`, `pomozno/sezonskiZapisnik`):
        list A4 za tisk, sestavljen v vmesniku iz JAVNEGA profila — zaledje ne rabi ničesar. **Pogoj paketa je v
        vmesniku (povezava na profilu in domači strani), sama stran je javna kot profil**: ne razkrije nobenega
        podatka, ki ga gost ne bi videl na profilu. Tekme brez datuma ne sodijo v nobeno sezono (izpiše se njihovo
        število). Postavitev ni preverjena na PDF ali pravem tiskalniku.
      - **Maskota** navijaču ne nastopa (gost ali igralec brez Premium, nihče drug).
      - **Ni preverjeno** (ni Stripovih ključev, ni pravega brskalnika s Push): izdelek Navijač v Stripu in kako Moj
        Račun izda račun zanj, kupon kluba na sandboxu, dostava pravega potisnega obvestila. Pred živim preklopom
        preveri po `docs/NAROCNINE-V-ZIVO.md`.
      - Testi: `PlacljivNavijacTest`, `OsebniKoledarInObvestiloTurnirjaTest`, `DruzinaTest`,
        `PotisnaObvestilaTest`, `PodporaKlubaTest`, `pomozno/IcsTest`, `pomozno/WebPushTest`,
        `AvtentikacijaDostopTest` (nove poti). Vsaka nova migracija SQLite potrebuje dvojnico v
        `db/migration/postgresql` (od preklopa 2. 11. 2026 velja samo mapa `postgresql/`).
- **Stran `/racuni` (Dostopi, samo admin)** je »Nabiralnik + kartoteka«
  (`strani/RacuniStran.tsx`, karton `komponente/DostopiKarton.tsx`, logika
  `pomozno/dostopi.ts`, razdelek 15 v `slog.css`, `.dostopi-…`; predaja
  `design_handoff_dostopi`, 12. 10. 2026, DESIGN.md vzorec 9). Merila in pasti:
  - **Stanje računa je izpeljanka** (`stanjeRacuna`): čakajoč račun s potrjeno
    e-pošto (in skrbnikom) je »Čaka« in gre v nabiralnik; brez kode se sam
    izbriše, zato je samo v kartoteki (»Brez kode« / »Čaka skrbnika«). Števila na
    žetonih so **skupna**, ne glede na filtre.
  - **Kartoteka kaže tudi administratorje** (`najdiRacuneOseb` jih vključuje),
    a je njihov karton samo za branje: strežnik adminovega računa ne ureja,
    izklop lastnega pa bi bil zaklep. Račun brez imena (admin) se bere po naslovu.
  - **Zadnja prijava = prva overjena zahteva po ≥ 30 min mirovanja**
    (`RacunDnevnikStoritev`, tabela `racun_prijava`, V45): veriga je brez
    stanja (HTTP Basic), seje ni. Zadnji čas zahteve je v pomnilniku, v bazo gre
    ena vrstica na prijavo (`PrijavaDogodki`); napaka zapisa ne ustavi zahteve.
    Po zagonu strežnika je prva zahteva vsakega računa nova prijava. Preizkusi,
    ki belezijo prijave, morajo uporabiti vsak svoj naslov (pomnilnik živi med
    preizkusi).
  - **Dnevnik računa** (`racun_dogodek`, V45): besedilo se zapiše ob dogodku in
    se ne računa nazaj (registracija, e-pošta, skrbnik, povezava, potrditev,
    zavrnitev, razvezava, izklop/vklop, geslo, plačilo). V45 je za obstoječe
    račune napolnila le dogodke z znanim datumom; zavrnitve, izklopa in gesla
    datuma nista hranila, zato ju ni. Karton nosi največ 8, najnovejši prvi.
  - **Poraba paketa** v kartonu je ista številka kot pri ustvarjanju
    (`NarocninaStoritev.steviloLigVSezoni` / `…TurnirjevVSezoni`), ne štetje
    vrstic; paket se bere iz zapisa, ne iz tega, ali velja.
  - **Klub igralca** v `RacunIgralcaDto.klub` je klub povezanega igralca (`idKlub`
    ostane prazen: račun sam kluba nima). `potrdiOrganizatorja` zdaj zapiše
    `povezanOb` (datum potrditve, karton ga kaže kot »Potrjen«).
  - **Karton se izriše znova** ob zamenjavi računa ali njegovega stanja (`key`):
    predizbira (prvi predlog brez računa, navedeni klub) in lokalno stanje
    (geslo, potrditev, iskanje) tako ne puščajo sledi. **Obvestilo po dejanju drži
    stran** (po instanci: nabiralnik, kartoteka), ker mora preživeti zamenjavo
    računa — potrjena zahteva izgine iz nabiralnika, karton pokaže naslednjo, na
    njenem vrhu pa obvestilo o prejšnji.
  - **Klub organizatorja v zahtevi je `IzbirnikKluba`** (`vObrazcu={false}`: brez
    vidne oznake, ker jo nosi rjava oznaka bloka), ne žetoni iz prototipa: v
    registru je 70+ klubov.
  - **Merilo ujemanja so posnetki iz predaje, ne 0,05 %**: pisave so pri
    aplikaciji lokalne, pri posnetkih iz Googla (skripta `preveri.mjs` je tekla
    z `playwright-core` in sistemskim Chromom, zunaj repozitorija); izmerjeno je,
    da se geometrija ujame (višine strani enake, razlike le v zglajevanju pisav 0,2–0,45 %
    na namizju, 0,09–0,15 % na telefonu list). Pasti: prototip je
    **content-box** (kvadratek 16 + 2 px obroba = 18 px, vrstica zahteve na
    telefonu `min-height` 56 + 2 × 10 + 1 = 77 px, seznam 680 + 1 px); **telefonski
    posnetki imajo vsebino 2 px (1 CSS px) pomaknjeno** glede na rez skripte
    (obrez 0 namesto 2 px); `desktop-06` kaže v izbirniku še vedno »Priimek A–Ž«
    (neobvladan `select` v prototipu), seznam pa je po prijavi; v `desktop-04/07`
    ima polje fokusni obroč (`outline` aplikacije); **list na telefonu ima na
    vrhu 56 px zastora** (`max-height: calc(100% - 56px)`, ne 90 % iz README);
    na telefonu številka nabiralnika brez tabularnih števk. Pri `desktop-02` se
    primerja le del nad kontrolnikom kluba.
  - **Brez oznak »NOVO«**: predaja jih je imela za podatke, ki jih API ni
    vračal (`zadnjaPrijava`, `prijavNaZadnjih30Dni`, `poraba`, `dnevnik`, klub
    igralca) — zaledje jih zdaj vrača, zato jih ni.
- **Pogoji in zasebnost `/pogoji`** (`strani/PogojiStran.tsx`, zasebnost je
  `/pogoji#zasebnost`). Podatki o ponudniku (NTK Žalec, naslov, matična in
  davčna, e-pošta, gostovanje) so v `pomozno/pravno.ts` — kar je `null`, se ne
  izpiše, a ZEPT zahteva stalno dostopne podatke o ponudniku, zato jih je
  treba vpisati pred objavo. Besedilo opisuje, kar aplikacija res počne; pred
  zanašanjem nanj ga naj pregleda nekdo, ki pozna ZVOP-2 in ZVPot-1. Povezave:
  kljukica v registraciji (nov zavihek — okno bi se sicer zaprlo), dno okna
  prijave (noge strani ni; okno prijave je z vsake strani en dotik) in pripis
  »Plačilo prek Stripe« na straneh naročnine.
