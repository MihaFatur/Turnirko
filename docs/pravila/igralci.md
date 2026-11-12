# Pravila: igralci

> Igralci: osebni podatki, dvojniki, čistka, datum rojstva, imena, abeceda, starostni pas.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Osebni podatki igralcev so ločeni na ravni tipa, ne pogojne veje.**
  `IgralecJavniDto` (ime, priimek, spol, roka, klub, rating) je edino, kar
  vrnejo `GET /igralci`, `GET /igralci/{id}` in *vse* mutacije — te poti so
  javne oz. odprte tudi organizatorju. Poln `IgralecDto` z osebnimi podatki
  vračata **samo** `GET /igralci/podrobno` in `GET /igralci/{id}/podrobno`,
  ki ju varnostna veriga omeji na `ADMIN` (organizator jih namenoma ne vidi:
  šifrant je skupen vsem klubom). Ne združuj poti in ne dodajaj osebnih polj
  v javni DTO — pravilo varuje `IgralciZasebnostTest`, ki preverja surovo
  telo odgovora.
- **Dvojnik igralca se pri vpisu opozori, ne prepreči** (`PodobnostImen`,
  `IgralciStoritev.najdiPodobne`). Obrazec »Nov igralec« ob shranjevanju vpraša
  `POST /igralci/podobni` in, če so zadetki, pokaže seznam in
  »Vseeno dodaj igralca«; urejanje se ne preverja. Isto ime imata lahko dve
  osebi, dvojnik pa razbije zgodovino in rating na dva zapisa — zato odloči
  človek. Razmerje pravil in zasebnosti je namerno:
  - **Zadetek odločajo SAMO imena** (brez šumnikov, velikih črk in ločil):
    isto, zamenjano (ime ↔ priimek) ali podobno — **največ ena beseda** se
    razlikuje, in to samo pri dovolj dolgi (od 5 črk ena napaka, od 9 dve:
    »Tina« / »Nina« sta dve osebi), ali je krajša oblika imena (»Miha« /
    »Mihael«, še 1–3 črke več). Če je znan spol obeh in se razlikuje, ime
    zožimo na enako (»Matej« / »Mateja«). Zadetkov je največ 10, tudi
    arhivirani (imajo zgodovino).
  - **Datum rojstva zadetka ne vpliva** — sicer bi organizator s poskušanjem
    datumov iz tega, ali se zadetek pojavi, izvedel datum rojstva znanega
    igralca. Datum zato ne sodi v javni izpis: `/podobni` (admin + organizator)
    vrne `PodobenIgralecJavniDto` (javni zapis, klub, pas), samo ADMIN dobi
    `/podobni/podrobno` s polnim zapisom in primerjavo datuma (`ENAK`,
    `PODOBEN`, `DRUG`). Poti sta ločeni po istem načelu kot `/podrobno`;
    `PodobniIgralciTest` preverja vloge in surovo telo odgovora.
  - POST in ne GET, ker datum rojstva ne sme v naslov zahteve.
  - Vmesnik **ne zadrži vpisa**, če poizvedba odpove (opozorilo je pomoč, ne
    pogoj). Admin vidi pri vsakem zadetku tudi, ali je datum enak, podoben
    ali drug; ob popravku vnosa opozorilo izgine in se ob shranjevanju preveri
    znova.
- **Čistka duplikatov** (stran Igralci, V44, `DvojnikiIgralcevStoritev`,
  `IgralciZdruzitevStoritev`). `GET /igralci/duplikati` (samo ADMIN, nosi datume
  rojstva) vrne pare aktivnih igralcev z istimi pravili podobnosti kot opozorilo
  ob vpisu (`PodobnostImen`); enak datum ni pogoj, le podatek ob paru, neznan
  datum (1. 1. 1900) pa ni nikoli »enak«. `POST /igralci/duplikati/ni-duplikat`
  par trajno skrije (`igralec_ni_duplikat`, urejen par), `POST
  /igralci/{id}/zdruzi` obdrži igralca v naslovu, odvečnega arhivira.
  - **Zdruzitev preusmeri VSE tuje kljuce** (prijave, kader, postave, tekme
    srečanj, račun, `zunanja_povezava`) in prazna polja obdržanega izpolni iz
    odvečnega (licenca, e-pošta in telefon sta UNIQUE: odvečni ju najprej izgubi).
    **Zavrne** (409), kadar bi preusmeritev trčila: oba v istem dogodku/postavi/
    tekmi, oba z računom, ali ročni rating (postavitev, zunanja uvrstitev) ene
    strani in zapisi ratinga druge — ročne odločitve ni mogoče pomešati z obračunom.
  - **Rating**: obračuni odvečnega se zbrišejo, rating obdržanega se sestavi znova
    (`PreracunRatingaStoritev.preracunajOd` od dneva odvečnikove prve tekme);
    brez tekem preračuna ni, ročna postavitev se prenese. Preračun teče po
    transakciji združitve in je sinhron (kot `/rating/preracun`), zato lahko
    traja nekaj minut. Varuje ga `IgralciZdruzitevTest`, dostop
    `DvojnikiIgralcevDostopTest`.
  - Admin dobi v `IgralecDto` tudi izpeljan `starostniPas` (isti kot v javnem
    izpisu; datum rojstva ostane samo pri adminu).
- **Datum rojstva ugiban** (V64, nov 2026; `igralec.datum_rojstva_ugiban`,
  `IgralecVnos.datumRojstvaUgiban`, `IgralecDto.datumRojstvaUgiban`). Admin in
  organizator ob vpisu novega igralca (admin tudi ob urejanju) obkljukata, da je
  vpisani datum PRIBLIŽEK (npr. 1. 1. letnika, da igralec spada v kategorijo, v
  kateri igra, ker pravega datuma ni). Pravila, ki jih ne razbij:
  - **Ugiban datum ni dokaz o osebi.** Račun se z zapisom NE poveže samodejno in
    predogled zapisa ga ne ponudi (`RegistracijaStoritev.kandidatiZaRacun`,
    `predogledZapisa`): datum 1. 1. letnika kdo uganè ali pozna, zato bi bil
    prevzem profila prelahek; tak zapis poveže samo admin. Primerjava dvojnikov
    (`DvojnikiIgralcevStoritev.primerjajDatum`) in opozorilo ob vpisu
    (`najdiPodobnePodrobno`) ga obravnavata kot neznan datum (ne »enak«). Obrazec
    ob ugibanem datumu podobnih igralcev išče brez datuma.
  - **Starostni pas in sidro ratinga se izpeljeta iz ugibanega datuma kot iz
    pravega** — zato obstaja: ocenjen datum uvrsti igralca v kategorijo, v kateri
    igra. Od 1. 1. 1900 (= »datum ni znan«, igralec velja za odraslega) se loči
    po tem, da je ugiban datum izbran tako, da starost drži. Uvoz mladinskih
    turnirjev (1. MRNTZ) uporablja 1. 1. letnika, ki je zgornja meja kategorije:
    U11 → 2016, U13 → 2014, U15 → 2012, U19 → 2008 (sezona 2026/27).
  - **Združitev dvojnikov**: pravi datum premaga ugibanega (in neznanega), znacka
    gre z datumom (`IgralciZdruzitevStoritev`). Značka je samo v podrobnem
    zapisu (admin), nikoli v javnem; organizator je ne vidi po vpisu.
  - Obstoječi igralci so `false`. Admin popravi datum v oknu »Uredi igralca« in
    odkljuka značko, ko pravi datum izve. Testi: `DatumRojstvaUgibanTest`,
    `RegistracijaTest` (ugibanDatumRojstvaNePovezeSamodejnoInNiVPredogledu),
    `IgralciZdruzitevTest`.
- **Ime pred priimkom, povsod.** Slovensko se oseba imenuje »Ana Novak« in tak
  je *vsak* izpis igralca — v zaledju ga sestavi `Igralec.polnoIme()`, v
  vmesniku pa vsako mesto, ki ime in priimek izpiše ločeno (izbirniki,
  šifrant, kader, predlogi računov). Obrnjeni vrstni red živi samo kot
  **urejevalni ključ**: `Igralec.abecedno()` (»Novak Ana«) in
  `Igralec.PO_ABECEDI` (priimek, ime) v repozitorijih — seznam torej *teče* po
  priimku, a se *bere* po imenu. `abecedno()` ne sme nikoli v DTO. Kjer DTO nosi `ime` in
  `priimek` ločeno (`LestvicaIgralcaDto`, `IgralecDto`, `DvobojDto.Igralec`),
  je to urejevalni ključ (razvrstitev po priimku; na lestvici izenačeni v
  strežniku) — ne zato, da bi ju kje izpisal obrnjeno.
- **Abecedni vrstni red je slovenski** (a b c č d … s š t … z ž). Besedilo
  (imena, priimki, klubi, ekipe, lige) razvrščaj v zaledju *vedno* prek
  `SlovenskaAbeceda.RED` (ICU, isti standard kot `Intl.Collator` v brskalniku),
  v vmesniku prek `localeCompare(…, 'sl')`. **Nikoli** `ORDER BY` po besedilu
  (SQLite primerja kodne točke: Č, Š, Ž pristanejo za Z, velike črke pred
  malimi), `Sort.by("ime")`, `String.compareTo`, `compareToIgnoreCase` ali
  JDK-jev `Collator` (ta ignorira presledke, zato »Kos Marko« pade za
  »Kosem«). Repozitorij z imenskim vrstnim redom zato izpostavi metodo z
  `default` telesom, ki uredi v Javi (`Igralec.PO_ABECEDI`, `Ekipa.PO_ABECEDI`,
  `Prijava.PO_ABECEDI`), poizvedba pod njo (`…Neurejene`, `…Neurejeno`)
  pa vrača vrstni red vpisa; varuje ju `SlovenskoUrejanjeTest`.
- **Starostni pas je izpeljanka, ne osebni podatek** (`StarostniPas`, polje
  `starostniPas` v `IgralecJavniDto` in `LestvicaIgralcaDto`). Iz letnice se izpelje najožji pas, ki
  mu igralec ustreza (`U11`…`U21`, `CLANI`, `VETERANI`); brez njega organizator
  med tisoč igralci mladincev ne loči, datum rojstva pa ostane pod
  `/podrobno`. Pravilo je 11. člen PST: starost se meri na **31. december
  leta, v katerem se sezona začne**, zato je referenca sezonska (rez 1.
  julija, isti kot `sezonaIzDatuma` v vmesniku) in se pas od januarja do
  junija **ne premakne**. Je **edini** pojem o starosti: po njem se prijavlja
  na turnir in filtrira lestvica, zato ima igralec na obeh mestih isto
  kategorijo (nekdanja koledarska `KategorijaIgralca` lestvice je odšla
  septembra 2026). Ker so pasovi izključujoči, filter »U15« pomeni izbiro
  U11 + U13 + U15.
