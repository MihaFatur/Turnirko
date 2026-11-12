# Pravila: vmesnik

> Vmesnik splošno: oblikovni sistem, barve, pisave, navigacija, telefon, filtri, izbira z vpisom, sklanjanje, preverba builda.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

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
- **Števila se vpisujejo, ne vrtijo: `komponente/StevilskoPolje`, nikoli
  `type="number"`.** Kolesce miške nad ostrenim številskim poljem tiho spremeni
  vpisano vrednost — organizator, ki med vnosom rezultata podrsa po zapisniku,
  dobi drugo številko in tega ne opazi, ker se je premaknila stran, ne kazalec.
  Isto naredita puščici gor/dol, ti pa sta pri točkah nizov potrebni za premik
  med okenci. Polje je zato besedilno s `inputMode="numeric"` (številčna
  tipkovnica na telefonu ostane), vpis pa filtrira: kar ni števka (oz. ločilo
  pri `decimalno`), se sploh ne zapiše. Ker `min`/`max` na besedilnem polju ne
  veljata, gre zgornja meja v `najvec` (vpis čez njo se zavrne), spodnjo meja
  pa **napiši v oznako polja** (»Srečanje se konča pri zmagah (3–5)«) — brez
  vrtavke je drugje ni videti. Nova številska polja gredo skozi to komponento;
  `type="number"` v `.tsx` je napaka.
- **Namizje in telefon imata ločeni navigaciji.** Nad 640 px je masthead mreža
  (`grid-template-areas`, `.glava`): logotip, navigacija, kontekst uporabnika;
  1 px in 3 px črto nosi `.glava__crta`. **Namizna glava je lepljiva**
  (`position: sticky`, neprosojna papirna podlaga, `z-index: 30`: nad predlogi
  izbirnika 20, pod modalnim oknom 50). Kar se samo prilepi na vrh strani
  (`.podnavigacija` dogodka, `.lestvica--globalna thead th`) in skoki na fokus
  (`html { scroll-padding-top }`) berejo njeno višino iz `--visina-glave`, ki
  jo `Postavitev` **izmeri** (ResizeObserver, ni konstanta: ~74,5 px, na dotik
  več) in zapiše na `<html>`; na telefonu je ni in CSS pade na 0. Nov lepljiv
  element na namizju mora imeti `top: var(--visina-glave, 0px)`, sicer zdrsne
  pod glavo. Pod 640 px se ta glava sploh ne
  izriše — `Postavitev` po `useTelefon()` (`src/pomozno/telefon.ts`, ista
  prelomna točka kot v `slog.css`) izriše `.glava-telefon` (56 px, lepljiva)
  in **spodnjo vrstico** `.spodnja-vrstica` (Domov · Turnirji · Lige ·
  Lestvica · Več). Vodoravno drsna vrstica zavihkov v glavi je polovico
  postavk skrivala pred očmi. »Več« je predal z urejevalskimi stranmi
  (Igralci, Dostopi, Šifranti) in se izriše samo adminu/organizatorju.
- **Uvodna animacija ob prvem nalaganju** (DESIGN.md 5e, predaja
  `design_handoff_uvodna_animacija`, 1a · Poteze, 22. 10. 2026):
  `komponente/UvodnaAnimacija.tsx` (zadnji otrok `Postavitev`, portal v `body`),
  stanje v `pomozno/uvod.ts`, slogi v razdelku »Uvodna animacija« na koncu
  `slog.css`. Pravila, ki jih ne razbij:
  - **Enkrat na sejo zavihka** (`sessionStorage['turnirko-uvod-predvajan']`,
    zapisan ob začetku predvajanja) in samo ob prvem nalaganju aplikacije
    (`history.state.idx === 0`), nikoli ob premiku med stranmi; odločitev pade
    pred prvim izrisom, zato se pri zapisanem ključu ne pokaže niti en okvir.
    Predogled brez nove seje: `/?uvod`.
  - **Znamka je logotip glave, povečan k-krat**: izmeri se `.glava__logotip`
    (pisava, `column-gap`, širina znaka), `k = clamp(1.5, 5, W × 0,56 (telefon
    0,82) / širina logotipa)`, `font-variation-settings: 'opsz' <pisava glave>`
    (brez tega Bricolage pri veliki pisavi izbere drugo optično velikost) in
    črke na legah iz `Range` čez cel napis (spodsekavanje). Le tako se ob
    pristanku (1700 ms) pokrije z logotipom do piksla. Telefonska podstran s
    puščico nazaj logotipa nima: znamka namesto poleta zbledi.
  - **Konec je vezan na `finished` vseh animacij, ne na uro** (zajem posnetkov
    jih ustavi). Dve pasti Chroma: animacija elementa, ki ga React med uvodom
    odstrani (okostje, ko pridejo podatki), se ne konča nikoli, in preklicana
    (`idle`) dobi nov `finished`, ki ne pride nikoli — zato opazovalec
    odstranjenim animacijo prekliče, čakanje pa preklicanih ne čaka.
  - **Vrstni red vsebine**: glava (navigacija, uporabnik; telefon: puščica,
    dejanja, uporabnik), nato vsebina. Domača stran ga pove sama (`data-uvod` =
    vrsta od začetka vsebine; Turnirji in Lige vzporedno, ko stojita drug ob
    drugem, sicer zaporedno — `vrsteUvoda`). Drugje neposredni otroci
    `.vsebina > section` in vrstice seznamov v prvem zaslonu po navpični legi
    (`vrsteVPrvemZaslonu`), največ 30 vrst. Vrstica, ki pride med uvodom,
    dobi animacijo z uro, usklajeno s sidrom.
  - Črti glave sta dva elementa (`.glava__crta-tanka`, `.glava__crta-debela`, na
    namizju in telefonu), ne obroba in `::after`. Koren prekrivala je
    `.uvod-zaslon`, ker `.uvod` že nosi uvodni odstavek strani. Med uvodom
    `html.uvod-tece` (brez drsenja, `scrollbar-gutter: stable`, da se ob vrnitvi
    drsnika nič ne premakne), ozadje `#koren` je `inert` do pristanka.
  - Maskota (urnik šteje od konca uvoda) in oglas Premium (odprt počaka) se med
    uvodom ne pokažeta (`useUvodKoncan`).
  - **Merilo ujemanja je prototip v istem brezglavem Chromu** (Playwright s
    sistemskim Chromom, podatki prototipa prek `page.route`, `Math.random`
    fiksiran za spol lestvice): vseh 28 sličic ≤ 0,011 % proti prototipu.
    Ostanek so črtice zelenega duha in oznake spodnje vrstice (lokalna
    `fontsource` pisava proti Google Fonts). Prototip ima domačo stran pri
    924 px v dveh stolpcih, aplikacija pod 1100 px v enem.

- **V lepljivo glavo telefona vlagajo strani svoje skozi `GlavaTelefona`**
  (`useNazaj` za puščico nazaj, portali `GlavaDejanja` / `GlavaNaslov` /
  `GlavaZavihki`). Portal in ne podvojen izris: dejanje je v drevesu natanko
  enkrat, zato ga bralnik zaslona prebere enkrat. Kontekst uporabnika stoji
  samo tam, kjer ni puščice nazaj (vstopni zasloni).
- **Tema je vedno svetla** (odločitev lastnika, sep 2026): temne različice
  spremenljivk v `slog.css` ni več, `:root` in `index.html` nosita
  `color-scheme: only light`, ki prepreči tudi samodejno temnjenje brskalnika
  (Chrome na Androidu, Samsung Internet). Temne teme ne vračaj.
- **Dolgi seznami na telefonu** (`.seznam-mobi`, `.lestvica-mobi`) imajo na
  vrsticah `content-visibility: auto`: vrstice pod robom se izrišejo šele
  blizu zaslona, kar je ob preklopu v spodnji vrstici skrajšalo čas do
  izrisa za ~40 % (4× upočasnjen procesor). V taki vrstici ne sme stati nič,
  kar sega čez njen rob (predlogi comboboxa, meni) — porezalo bi se.
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
  - **Iskanje po imenu** (lestvica, turnirji, lige, udeleženci dogodka) teče
    skozi `komponente/IskanjeSeznama.tsx`: na namizju polje **skrajno desno**
    v naslovni vrstici seznama in števec seznama (»329 turnirjev«) **pod
    njim** (`IskalnikSeznama stevec`, `.naslovna-vrstica__desno--iskanje`;
    gumb urejevalca stoji levo od polja), na telefonu preklopnik »Išči«
    skrajno desno v vrsti naslova strani (`.naslov-mobi__vrsta--iskanje`),
    ki odpre pas pod črto
    lepljive glave (tam ostane, da polje med drsenjem ne uide). Dejanja
    urejevalca (»+ Turnir«) stran vloži v glavo sama skozi `GlavaDejanja`.
    Vstopni seznami na telefonu **nadnaslova nimajo** (»Tekmovanja«,
    »Turnirko rating« … so odšli septembra 2026). Iskanje zoži seznam
    **pred** filtri. Turnirji iščejo po imenu, lige po imenu in sezoni.
  - **Turnir sezone nima kot polje**; izpelje jo `sezonaIzDatuma`
    (`pomozno/oblikovanje.ts`) z rezom 1. julija. Uvožena zgodovina se začne
    najprej sredi septembra in konča najkasneje sredi junija, zato skozi rez
    ne pade nobeno tekmovanje. Kraj in dvorana sta **ločeni** skupini: uvoz je
    ime prizorišča zapisal v `dvorana` (tam so imena mest), ročni vnos pa ima
    kraj iz šifranta.
  - **Na lestvici spol ni filter, ampak izbira lestvice** — med spoloma ni
    obračunanih tekem in skali nista primerljivi. V oknu z merili stoji kot
    `IzbiraEne` (podaja jo stran prek `KrmilaSeznama vOknu`): vedno je
    izbrana natanko ena vrednost, »Počisti« je ne odnese, med žetoni je ni —
    izbrana lestvica se zato izpiše ob naslovu (»Moški · Člani · 1234
    igralcev«). Segmentiranih pasov nad tabelo ni več. **Kategorija je prav
    tako `IzbiraEne`, ne filter**, ker so kategorije **vgnezdene**: Člani so
    vsi razen rekreativcev (privzeto), U21 vsi razen članov in rekreativcev,
    U19 še brez U21 … do U11; Rekreativci so samo rekreativci ne glede na
    starost. Igralec bi imel v skupini več vrednosti hkrati, česar
    `SkupinaFiltra` ne pozna. Izpelje se iz `starostniPas` (veterani in
    igralci brez letnice so samo med člani). Števci kategorij in spola so
    navzkrižni (iskanje, klub, druga izbira). Na telefonu je kategorija naslov
    seznama namesto podvojenega »Lestvica«, števec pa »Moški · 110 od 121« —
    vse v enem števcu se pri 375 px odreže. Razvrstitve po priimku na
    lestvici ni. **Prikazani seznam se oštevilči od 1 naprej po izbrani
    lestvici, filtrih in razvrstitvi** — številka pove mesto v tem, kar
    gledalec gleda, ne v celi lestvici. Filter »U19«, ki se je začel pri 35.,
    se je bral kot izsek sredine, koliko mladincev je pred tem igralcem, pa je
    bilo treba šteti na roke. **Iskanje pa NE oštevilči znova** (oktober 2026,
    `mestaBrezIskanja`): vrstico le poišče. Prej je bil vsak najdeni igralec
    »1.« in svojega mesta ni izvedel, čeprav je prav po to prišel. Globalno
    mesto po ratingu v vrstici (`Vrstica.mesto`) vseeno ostane: po njem teče
    razvrstitev »Rating« in izenačenja pri drugih merilih — a se ne izpiše.
  - **Iskanje teče po izbrani lestvici, zadetki drugje pa se pokažejo pod
    njo** (`drugje`, `namigIskanja`): igralka na moški lestvici, rekreativec
    med člani — z lestvico, mestom na njej in gumbom »Pokaži lestvico«. Kdor
    ni na nobeni lestvici, se poišče v javnem šifrantu (`igralciApi.seznam`,
    naloži se šele takrat) z razlogom: še brez ratinga oziroma 18 mesecev brez
    tekme. Iskanje je brez šumnikov in po besedah (`pomozno/iskanje.ts`).
- **Igralec, klub, kraj in liga se izberejo z vpisom imena — nikjer s
  spustnim seznamom ali seznamom s kljukicami, po katerem bi bilo treba
  drseti.** Po uvozu zgodovine je igralcev več tisoč, klubov sedemdeset, lig
  nekaj sto. Vse teče skozi en combobox, `komponente/IskalniIzbirnik.tsx`
  (razredi `.iskalni-izbirnik__*`); tanki ovoji so `IzbirnikIgralca`,
  `IzbirnikKluba` in `IzbirnikKraja`, lige in prijave dvojic ga kličejo
  neposredno. Dve kopiji istega comboboxa bi se razšli v tipkovnici in
  dostopnosti. Pravila, ki jih ne razbij:
  - Ujemanje je **brez šumnikov in po besedah** (`pomozno/iskanje.ts`:
    »krizan« najde Križana, »novak ana« Ano Novak), zadetki na **začetku
    besede gredo naprej** (`zadetihZacetkov`) — pokaže se jih le osem in »ana«
    mora ponuditi Ano pred Dijano.
  - **Dve vlogi, izbrani z `izbrano`.** Brez njega je polje *dejanje*
    (semafor, kader lige in dogodka, dodaj nižjo ligo): izbira sproži, polje se
    izprazni. Z `izbrano` (tudi `null`) je *vrednost obrazca* (klub, kraj,
    višja liga, igralec računa, igralca para): v polju stoji ime izbora,
    prazno polje ob odhodu izbor počisti, delni vpis vrne prejšnji izbor,
    natančno vpisano ime pa velja kot izbira. Besedilo v polju, ki ni izbor,
    bi bilo past.
  - Escape zapre **samo predloge**, okno (`ModalnoOkno`) šele naslednji.
    Predlogi ležijo **čez** vsebino (absolutno) in se odprejo **navzgor**, kadar
    pod poljem ni prostora (dno okna, tipkovnica, na telefonu spodnja
    vrstica) — do predloga se nikoli ne drsi. Na desni strani semaforja so
    zrcalno usidrani (`.enanaena__stran--2`).
  - Ob imenu igralca stoji klub, ker se soimenjaka drugače ne ločita.
  - `brezIskanja` ponudi predloge, preden je kaj vpisano (predlogi po
    priimku pri potrditvi računa); brez tega prazno polje ne ponudi ničesar.
  - **Dvojice**: par se sestavi z dvema poljema (»Igralec«, »Soigralec«) iz
    prijavljenih brez soigralca; seznam »Brez soigralca« pod njima je samo
    pregled z odjavo.
  - Izjema ostaja blok »Dodaj igralce« na dogodku (zgoraj): tam iskanje zoži
    seznam s kljukicami, ker se prijavlja več igralcev hkrati in s filtri.

  Oznaki »Medsebojno · vsa tekmovanja« in »Naključni par« sta samo v
  razširjeni različici `EnaNaEna` (`pokaziZgodovino`, stran dvoboja); na
  domači strani ju nadomesti naslov sklopa.
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
- **Sklanjanje po številu: posebno obliko imajo samo števila, ki se pri 100
  končajo na 1, 2, 3 ali 4** (`sklon` v `pomozno/oblikovanje.ts`): »21 igralcev«,
  »64 igralcev«, »102 igralca«. Do oktobra 2026 je pravilo delilo po ostanku pri
  10 in pisalo »64 IGRALCI«. Nove funkcije `sklon…` gredo skozi `sklon`.
- **Ime lige za predlogom se sklanja samo v besedi »liga«** (`ligaVTozilniku`
  »Napreduje v Ligo Savinja A«, `ligaVMestniku` »Igra v Ligi …«); »1. SNTL«
  ostane. **Imen oseb ne sklanjamo**, zato med imenoma stoji pomišljaj (»Jure
  Pejovnik – Ivan Zera« v zanimivostih), ob enem imenu pa »nasprotnik Aleš
  Sešel« (graf ratinga) — »proti Ivan Zera« je bilo narobe (dajalnik).
- **Napaka 404 nima gumba »Poskusi znova«** (`NapakaPoizvedbe`): pove, da
  zapisa ni, in ponudi domačo stran; tehničnega sporočila zaledja (»Igralec z id
  … ne obstaja«) ne izpiše. Tudi 401/403 gumba nimata.
- **Izbira ENE možnosti v oknu filtra nima kvadratka** (`.filtri__vrstica--ena`):
  kvadratek je kljukica (več izbir); izbrano pove modra črta in modro ime.
  Krogca ni, ker velja radij 0.
- **Iskanje na telefonu fokusira polje v istem dotiku** (`IskanjeTelefona`:
  `flushSync` + `focus()`): Safari na iOS sicer tipkovnice ne odpre.
- Preverba pred zaključkom dela: `cd vmesnik && npm run build` (tsc + vite).

