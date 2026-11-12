# Pravila: domaca stran

> Domača stran, koledar, spremljane lige in obvestila po kolu, Zanimivosti, Ena na ena.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

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
    vrstni red namernosti:** izbor računa → lige igralca s Premium (kader
    ekipe) → adminov izbor → gostove zadnje ogledane lige → lige v teku.
    Parametra sta zato **ločena** in ne en seznam: ogled ni izbira in ena odprta
    liga izpred tedna ne sme povoziti tega, kar je zveza postavila na vhodno
    stran.
  - **Igralčeve lige (`ligeIgralcevihEkip`) so drugi korak in ne izbor
    računa:** sledijo iz kadra (`najdiNezakljuceneZaIgralca`), zato jih
    igralec ne vpisuje, lasten izbor pa jih **v celoti** nadomesti (kot
    adminov izbor). **Samo potrjen, z igralcem povezan račun** — od 23. 10.
    2026 tudi brez paketa (spremljanje lig je brezplačno, kot `spremljaj`);
    gost vidi adminov izbor kot prej. Okno »ekipa s sosedama« (spodaj) ostane
    Premium. **Zaključene lige izpadejo** — uvožena zgodovina
    ima pri dolgoletnem igralcu ducat starih sezon in domača stran ni arhiv;
    lige v teku so pred tistimi v pripravi. Števila ne omejujemo (adminov izbor
    ima mejo dve, ker je izlog; tu gre za lige, kjer igralec res nastopa).
    Pot `GET /domov/lige` je javna, a strežnik račun prebere iz glave
    `Authorization` — to varuje `DomovLigeDostopTest`. Zato je odgovor odvisen
    od računa in je račun v ključu poizvedbe na domači strani.
  - Naslov sklopa je »Moje lige« **samo**, kadar sklop res kaže moje: lasten
    izbor ali lige, v katerih igralec nastopa (`DomovLigaDto.izEkipe`, pove
    strežnik po vrstici); sicer »Lige«. Naslov, ki bi adminovima ligama rekel
    »moje«, bi lagal. Filter »Moje lige« pod sklopom meri isto.
- **Domača stran po meri je samo za Igralca Premium** (potrjen igralec,
  povezan z zapisom, z veljavnim paketom; oktober 2026). Paket preveri
  strežnik — vmesnik (`premiumIgralec` v `DomacaStran`) le ne sproži poizvedb
  zaman. Gost, igralec brez paketa in organizator vidijo stran kot prej.
  - **Razpredelnica lige je njegova ekipa s sosedama** (`DomovStoritev.okno`,
    `DomovLigaDto.Vrh.moja`, `ekip`): ena gor in ena dol, na vrhu dve dol, na
    dnu dve gor; pri prvi oz. zadnji ekipi droben napis »prva« / »zadnja«.
    Velja za vsako ligo na domači strani, v kateri igra (kader,
    `KaderEkipeRepozitorij.ekipeIgralcaVLigi`), ne glede na to, zakaj je tam
    (lasten izbor, kader ali adminova izložba); drugje ostane vrh.
  - **Turnirji so izbrani zanj, vsak z razlogom** (`GET /domov/turnirji`,
    `DomaciTurnirjiStoritev` zbere podatke, pravilo je čista funkcija
    `IzborTurnirjev`; drugim vrne prazno in vmesnik pokaže najnovejše):
    prihajajoči (prijavljen, sicer najbližji primeren), zadnji, na katerem je
    nastopil, nato zanimivi do skupaj 4, vsaj 2 (v teku, klubski kolegi,
    primeren — v oknu 60 dni, bližji prej; nato najnovejši).
    - **Raven za izbor NI zastavica lestvice rekreativcev**: ta šteje tudi
      klubska tekmovanja, zato je igralec Savinja lige po treh tekmah
      »tekmovalec« in bi dobil uradne turnirje NTZS. Merilo je vsaj
      `RekreativecStoritev.PRAG_TEKEM` tekem na URADNIH tekmovanjih: kdor jih
      nima, dobi rekreativne in nato klubske turnirje.
    - **Razpis** (`IzborTurnirjev.razpisDopusca`): spol dogodka in starostna
      kategorija iz prostega besedila — oznake U (»U-13«, »člani U-21«),
      besedne meje (»do 60 let«, »nad 60 let«), veterani in imena iz uvozene
      zgodovine (dečki U11, mlajši kadeti U13, kadeti U15, mladinci U19).
      Turnir, ki ga razpis ne dopušča, je med zanimivimi samo do najmanjšega
      števila — raje krajši sklop kot mladinski turnir odraslemu.
    - **Datum rojstva 1. 1. 1900 pomeni »ni znan«** in ga imajo skoraj samo
      odrasli rekreativci (v Savinja ligah 86 od 154), zato tak igralec velja
      za odraslega (40 let): mladinski razpis ga ne dopušča, veteranskega ne
      zapre.
  - **Lestvica pokaže njega in igralce okoli njega** (»Okoli mene«, privzeto,
    namesto »Vsi igralci«; samo na vmesniku iz `/lestvica`): okno osmih na
    NJEGOVI lestvici (spol × tekmovalci/rekreativci — isto mesto kot na
    profilu), številke so dejanska mesta, njegov položaj v oknu je naključen
    ob vsakem prihodu na stran (enkrat prvi, drugič peti …), ob vrhu ali dnu
    se okno poravna. Trimestna mesta dobijo na telefonu širši stolpec
    (`.domov__lestvica--siroka`). Filtri sklopa (Okoli mene / Vsi igralci,
    Moje lige, Moj klub) so na namizju segmentirani izbirnik, na telefonu
    nativni spustni izbor (`krmilo-izbor`, okt 2026) — trije gumbi so se pod
    ~375 px lomili v dve vrsti.
  - Regresije: `IzborTurnirjevTest` (pravila izbora, razpis, okno) in
    `DomovPremiumTest` (paket, račun, ekipa, prazno za druge).
- **Koledar je pogled po DNEVIH in ne po tekmovanjih** (`KoledarStoritev`,
  `GET /api/v1/koledar?od=&do=`, javen kot ostali GET-i). Nove sheme ne
  potrebuje — bere datume turnirjev in termine kol, ki že obstajajo. Trije
  premisleki, ki jih ne razbij:
  - **Zrnatost ligaškega vnosa je KOLO, ne srečanje.** Dan je last kola
    (`nastaviTermine`), zato bi vnos na srečanje isto ligo v istem dnevu
    izpisal petkrat; pari kola gredo v `srecanja` istega vnosa. V ključu
    zbiranja je vseeno tudi DAN — posamično prestavljeno srečanje sodi na svoj
    dan in ne k ostalim.
  - **Meji poizvedbe srečanj sta za dan širši od obdobja**, natančno omeji šele
    Java po datumu (iz časa SQLite, ko je bil `predviden_zacetek` besedilo in so
    zapisi brez ure na prvi dan obdobja odpadli; s `timestamp` je razširitev
    odveč, a ne škodi).
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
    podatkov; ničla bi trdila, da se nekaj ni zgodilo. Pod `PRAG_TEKEM` (9,
    odločitev lastnika, 6. 10. 2026) zavihka sploh ni (`dovoljPodatkov`),
    vmesnik pa gumba ne ponudi že prej: isti prag `PRAG_ZANIMIVOSTI`
    (`pomozno/zanimivosti.ts`) velja za turnir (`odigranihTekem`) in ligo
    (`odigranihTekemLige`, vsota dobljenih tekem srečanj brez b. b. in
    prenesenih). **Pri ligi merilo NI odigrano kolo**: ekipe se neuradno menjajo
    za termine, zato v Savinja ligi A–C še tednov ni bilo celega kola in
    zavihka ni bilo, čeprav je bilo odigranih preko 45 tekem. Prag mora ostati
    v zaledju in vmesniku isti (mejni test `zavihekSePokazeZDevetoTekmo`).
  - **Dvojice ne vstopajo v vrstice o posamezniku** (isto pravilo kot pri ratingu).
    Štejejo v »V številkah« in v svojo vrstico. Edina izjema je »Največji
    garač«, kjer se štejejo **nastopi** in ne izkupiček — a v svoj števec.
  - **Preobratov pride seznam, ne en sam** (`obrati`, najbolj borben — največ
    točk — prvi). Zgodba pokaže prvega, števec »3 preobrati na tekmovanju« pa
    je gumb, ki pod njo razpre ostale.
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
- **Obvestila po kolu in brezplačno spremljanje lig** (V50, odločitev lastnika
  23. 10. 2026; faza 2 za preizkusom Premium). Račun je moral dati nekaj, česar gost
  nima, in ga pripeljati nazaj: spremljanje lig je zdaj **brezplačno** (prej Premium;
  `DomovStoritev.spremljaj`, tudi lige iz kadra na domači strani), po vsakem kolu pa
  pride **e-pošta** z izidi, lestvico in prejemnikovimi tekmami.
  - **Kdaj** (`ObvestilaPoKoluStoritev.zrelo`): vsa srečanja kola (redni del, brez
    čakajočih na termin) so končana ALI je končana vsaj polovica in je od dneva kola
    minilo 4 dni; zadnji izid (`srecanje.odigran_ob`) je star vsaj 2 uri (popravki
    med vnosom večera) in največ 7 dni (staro kolo ni novica — to izloči tudi uvoženo
    zgodovino brez časa vnosa, torej **lige iz Stupe obvestil nimajo**). Opravilo teče
    vsako uro ob :20 med 8. in 20. uro po slovenskem času
    (`turnirko.obvestila.urnik`, nastavljiv samo za razvoj).
  - **Vsako kolo enkrat** (`obvestilo_kola`, ključ liga + kolo): tudi po popravku ali
    razveljavitvi zapisnika ne gre znova. Več zrelih kol hkrati → sporočilo samo za
    najnovejše, starejša `preskoceno`; kolo, ki se dopolni za že obdelanim novejšim,
    se preskoči.
  - **Komu**: računi z igralcem v kadru lige (`najdiIgralceLige`, potrjeni) in računi,
    ki ligo spremljajo, vsak enkrat; aktiven, potrjen naslov (in skrbnik),
    `uporabnik.obvestila_po_kolu` = 1 (privzeto). Igralec dobi svoje tekme vseh
    srečanj ekipe v kolu (liga z urami kola ima v kolu dve), spremembo ratinga v kolu
    in naslednje srečanje (ki mu termin ni mimo). **Vrstica o Premium** gre samo
    potrjenemu igralcu brez paketa (ponudba s ceno po pasu) ali v preizkusu (datum
    konca); otrok (naslov skrbnika) in plačnik je ne dobita.
  - **E-pošta** je multipart (besedilo + HTML, `posta/SporociloKola`, slog po
    DESIGN.md 5f) z glavama `List-Unsubscribe` in `List-Unsubscribe-Post` (odjava z
    enim klikom, RFC 8058). `PosiljateljPoste.poslji(prejemnik, Besedilo)` nosi HTML in
    glave; `DnevniskiPosiljatelj` HTML zapiše v `turnirko.posta.mapa-predogleda`, če
    je nastavljena (predogled v razvoju).
  - **Odjava**: `uporabnik.zeton_odjave` (naključen, nastane ob prvem obvestilu,
    UNIQUE) — `POST /api/v1/obvestila/odjava?z=` brez prijave (izklopi samo obvestila;
    neznan žeton 404), stran `/odjava-obvestil` z gumbom (ne samodejno ob odprtju —
    pregledovalniki pošte povezave odprejo sami). Nastavitev `GET/PUT
    /api/v1/obvestila` (prijava; pravilo pred »GET je javen«), v vmesniku okno
    »Obvestila« v meniju računa (`ObvestilaOkno`) s seznamom lig.
  - **Transakcija mora biti na metodi, ki jo kliče urnik** (`@Transactional` na
    `urnoPreverjanje` in `PreizkusPremiumStoritev.dnevniOpomniki`): klic transakcijske
    metode iz istega razreda gre mimo Springovega posrednika in spremembe entitet
    (žeton, `opomnik_poslan_ob`) se ne zapišejo. Testi tega ne ujamejo (so
    `@Transactional`); izmerjeno v brskalniku na kopiji baze.
  - **Gost**: »Spremljaj« na strani lige in »Sledi ligi →« na domači strani odpreta
    kratko registracijo (`izvor: 'spremljanje'`, `MoznostiRegistracije.liga`); po
    samodejni prijavi `RegistracijaTok` ligo spremlja sam. Primerjave paketov
    (registracija, naročnina, oglas 1c) imajo »Spremljanje lig in e-pošta po kolu« pri
    Free in »Domača stran po meri« pri Premium.
  - Regresije: `ObvestilaPoKoluTest`, `ObvestilaDostopTest`, `DomovStoritevTest`,
    `DomovLigeIgralcevihEkipTest`.
- **Zavihek »Zanimivosti« pozna dve obliki in nič več**
  (`komponente/ZanimivostiTekmovanja.tsx`, slog razdelek 15b): **zgodba** je
  enkraten dogodek (presenečenje, preobrat, najdaljši niz) v obliki mono oznaka →
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
  - **Telefon: zavihki lige v lepljivi glavi so Lestvica · Razpored ·
    (Končnica) · Piramida lig · (Zanimivosti) · Pravila** (odločitev lastnika,
    sep 2026). Piramida je svoj pogled (`MobilniPogled 'PIRAMIDA'`) in ne več
    dno zavihka z lestvico; ponujena je **vsaki** ligi — samostojni pove, da
    piramide ni (lastniku ponudi »Uredi prehode«). Štirje zavihki gredo brez
    drsenja do 360 px (`.podnavigacija--enakomerna`: enake širine, a nikoli
    ožji od oznake). **Ko jih je več kot štiri, prvi trije ostanejo, ostali
    gredo v meni »Več ▾«** (`ZavihkiTelefona`, `MeniDejanj` z vidno oznako) —
    prej je pas drsel in »Pravila« so ostala odrezana za robom. »Več« ob
    izbranem pogledu iz menija dobi slog izbranega zavihka, imena pa ne
    prevzame (»Zanimivosti ▾« ob »Piramida lig« pri 375 px ne gre v pas).
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
  nastavitev domače strani in ne pot v ligo, zato »Sledi ligi« odpre okno —
  gledalec ostane, kjer je, in takoj vidi, kaj se je spremenilo (gost izbora
  nima, zato mu isti napis odpre `PrijavaOkno` naravnost na registraciji); na `/lige` je
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
- **Blok »Lestvica« na domači strani kaže ENO lestvico** (`lestvicaSklopa`):
  prijavljen igralec svojo (spol in skupina), gost naključno moško ali žensko
  lestvico članov ob vsakem prihodu; napis pod naslovom pove, katera je. Prej je
  mešal spola in mesta se niso ujemala s stranjo Lestvica.
