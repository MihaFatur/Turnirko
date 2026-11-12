# Pravila: vmesnik tekmovanja

> Vmesnik turnirjev in lig: stopničke, udeleženci, zavihki, klik na tekmo, zapisnik, lestvica in glava lige.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Končna razvrstitev: stopničke** (4. 11. 2026, DESIGN.md 5h; predaja
  `design_handoff_koncna_razvrstitev`, možnost 1b + tisk 1c): `komponente/KoncnaRazvrstitev`
  (čista prikazna komponenta), čista pravila v `pomozno/koncnaRazvrstitev` (nivoji, razrez na liste
  za tisk, `udelezenciRazvrstitve` iz `MrezaDto`), tisk je del izpisa rezultatov (glej »Izpis
  rezultatov kategorije«; `/dogodki/:id/razvrstitev-tisk` je ista stran, zaklenjena na razvrstitev),
  razredi `.stopnice__*`, `.nivoji__*`, `.koncna-razvrstitev__*`, `.razvrstitev-list__*` na koncu
  `slog.css`. Pravila, ki jih ne razbij:
  - **Samo mesta 1.–3. (ali 1., 2. in dve »3.–4.«) so izpostavljena, ostali so za gumbom »Še N
    igralcev«** in se razgrnejo po nivojih (zaporedni z istim mestom). **Rating, sprememba ratinga in
    razlika nizov so izpuščeni; bilanca (`9 – 0`) samo pri krožnem sistemu**, ker po njej določa
    vrstni red. SV regija obdrži svoj seznam ostalih po nivojih (`RazvrstitevPoNivojih`) brez gumba.
  - **Ena struktura, dve postavitvi, odloča širina vsebnika** (`container-type: inline-size` +
    `@container (max-width: 899px)`): stopnice nad 900 px, palice pod njim. Glava razdelka in besedilo
    gumba (»Natisni« / »Natisni razvrstitev«) pa sledita strani (`useTelefon`: na telefonu je razvrstitev
    zavihek brez zgornje črte). Nikoli JS za mere.
  - **Razvrstitev kaže VSE, ki so na dogodku nastopili** (5. 11. 2026, `razvrsceniUdelezenci`; odločitev
    lastnika: »vedno vsi igralci«). Strežnik zapiše `koncno_mesto` samo, kjer ga sistem sam določi: izločilna
    mreža iz Turnirka ima zapisana le mesta 1–4 (finale, tekma za 3. mesto), format TOP nobenega — vsi drugi
    bi iz razvrstitve izpadli. Vmesnik zato **izpelje mesto po izpadu** in ga postavi ZA zapisana: izločilna
    mreža po kolu zadnjega nastopa (poraženci istega kola si delijo razpon, »5.–8.«, »17.–24.«; pri mreži z
    prostimi prehodi šteje število res izpadlih, ne velikost mreže), format TOP po skupini (A pred B, skupine
    so rangi) in mestu v skupini, izpad v skupini pred izločilnim delom po mestu v skupini. **Zapisano mesto
    je vedno zadnja beseda** (uvoženi dogodki, krožni sistem, SV regija — tam izpeljave ni, vsak ima mesto).
    **V razvrstitvi so PRIJAVLJEN, ODSTOPIL in DISKVALIFICIRAN; ODJAVLJEN (šel pred žrebom) in REZERVA (v
    TOP ni prišla v izbor) ne** — na tekmovanju nista bila. Izpeljava je samo prikaz: strežnik mest ne
    zapisuje, zato profil igralca in statistika še vedno poznata samo zapisana mesta.
  - **Ime in priimek sta ločena iz BAZE** (`PrijavaDto.ime/priimek`, null pri ekipi), ne iz rezanja
    `polnoIme`; ekipa in par imata celotno ime v vrstici priimka (`imePrijave`).
  - **Slika: `SlikaIgralca` `velikost="VELIKA"`** (480 px na strežniku; 160 px bi bilo na 320 px zamegljeno),
    velikost določa CSS (`--mera`), nikoli `mera`. Če slike na stopničkah nima nihče, okvirjev ni; če jo
    ima vsaj eden, ga dobijo vsi (obris glave in ramen).
  - **Razgrnitev ni samo odrezana**: zaprt `.nivoji` ima `visibility: hidden` po koncu prehoda
    (tipkovnica, bralnik). Gibanje samo `transform`, `opacity`, `grid-template-rows`; začetno stanje
    vstopa je VIDNO (`useLayoutEffect` ga skrije, časovnik 60 ms spusti, ne `requestAnimationFrame`).
  - **Merilo je prototip `Koncna razvrstitev.dc.html` v istem brezglavem Chromu** (dev harness
    `/dev/koncna-razvrstitev?primer=izlocilni|krozni|ekipni&stanje=zaprto|odprto&naprava=namizje|telefon&prikaz=tisk`,
    podatki `dev/koncnaRazvrstitevPodatki.ts` iz predaje). Izmerjeno 4. 11. 2026 na vseh 15 primerih: višine
    in širine enake, lege vseh besedil Δ 0,00 px, piksli ≤ 0,04 % (pri tisku mora biti list brez `outline`:
    zaslonski obris spremeni zglajevanje črk v celem listu, 4,4 % pikslov, mere pa ostanejo iste). Merilo
    velja za zaslon: list za tisk je od 8. 11. 2026 po izpisu rezultatov in prototipu ne sledi več.
  - Zaledje: samo `PrijavaDto.ime/priimek` (test v `LenoNalaganjeTest`). Ni preverjeno na pravem tiskalniku
    in z resnično profilno sliko (preverjeno z generirano SVG sliko).
- **Udeleženci: nosilci, palice, spustni meni** (5. 11. 2026, DESIGN.md 5i; predaja
  `design_handoff_udelezenci`, varianta 1a): `komponente/UdelezenciPogled` (izris),
  `komponente/MeniIzbire` (spustni meni), pravila v `pomozno/udelezenci`, razredi `.udelezenci__*` in
  `.meni-izbire__*` na koncu `slog.css`. Isti pogled za posameznike, pare in ekipe ter za pripravo (gost, `PripravaGost`),
  turnir v teku in zaključen (zavihek »Udeleženci«, `Udelezenci` v `DogodekStran`). Pravila, ki jih ne razbij:
  - **Številka je jakostno mesto (nosilec)** in po njem so vrstice v skupinah 1–2, 3–4 (kartice), 5–8, 9–16, od 17. Kartice samo,
    kadar je razvrstitev »Po jakosti« IN klub »Vsi klubi« IN iskanje prazno; sicer zgornji dve skupini preideta v seznam. Mesto
    je `stNosilca`, kadar ga imajo vsi (po žrebu), sicer vrstni red, ki ga je vrnil strežnik (`izbor`), sicer po moči (par:
    povprečje; brez ratinga na dnu). **Pri dvojicah nosilcev ni** (žreb je naključen, `stNosilca` prazen): številka je tam
    razvrstitev po ratingu, opomba iz predaje (»po njem se razporedijo nosilci«) pa je za pare neresnična - odločitev lastnika.
  - **»V igri« / »Izpadel« strežnik ne hrani**, izpelje ga `stanjaMedTurnirjem` iz tekem izločilne mreže (v igri: še ima tekmo
    ali ni nikoli izgubil; izpadel: izgubil in brez tekme, ali ni v mreži po skupinah) in ga **ne izpiše** pri krožnem sistemu, TOP
    in SV regiji. Odstopil ima svojo značko »Odstopil«; po koncu velja »Končal 3.–4.« iz `razvrsceniUdelezenci`.
  - **Zaledje**: `PrijavaDto.ime2/priimek2` (drugi igralec para, ločeno iz baze) in `kader` (priimki kadra ekipe, najmočnejši
    prvi; `IzborStoritev.priimkiKadrov`, ena poizvedba za vse ekipe); testa v `LenoNalaganjeTest` in `LenoNalaganjeEkipnoTest`.
  - **Palica ratinga** je `(rating − 1400) / 8` odstotkov (`PALICA_NAJNIZJI_RATING`); prava lestvica je nižje, zato je palica pri
    slabše ocenjenih prazna. Spremeni se ena konstanta.
  - **Dve postavitvi, ena struktura** (640 px v CSS); JS samo za besedilo v polju za iskanje. Značka in rating v vrstici ostaneta
    element tudi, kadar sta prazna: razmik pod njima je del višine vrstice.
  - **Organizator** v teku vidi pri udeležencu v igri povezavo »Odstopil« (potrditveno okno, kot prej); ekipni dogodek ima pod
    pogledom še upravljanje ekip (`EkipeDogodka`: kader, preimenovanje). Priprava za organizatorja je nespremenjena
    (`Priprava`: jakostni vrstni red, pari, `SeznamPrijavljenih` z odjavo) - tam so dejanja ob vrstici.
  - **Merilo je prototip `Udelezenci prenova.dc.html` v istem brezglavem Chromu** (ciljni PNG iz predaje niso ponovljivi: črta 3 px
    je 2 px, vrstice ~0,3 % nižje, telefon brez spodnje vrstice): besedila in škatle ±0,5 px, piksli 0 v vseh 14 stanjih iz
    predaje in 9 dodatnih (dvojice in ekipe v teku/zaključeno, razvrstitve). Telefonski prototip je širok 388 px (1 px obroba
    makete), aplikacija pri 390 px ima vsebino za 2 px širšo.
- **Vrsta ekipe je preklop, ne polje.** V pripravi lige (`EkipeUredi` v
  `LigaStran`) stoji nad obrazcem segmentirani izbirnik »Iz registra / Prosta
  ekipa«: prvi zavihek ponudi klub in neobvezno lastno ime, drugi samo ime.
  Pod 640 px vrstica ekipe (`.liga__ekipa`) prelomi — dejanja gredo pod ime,
  ker se ime, velikost kadra in dva gumba v 390 px ne zložijo v eno vrsto.
  Oznaka pod imenom (`.liga__ekipa-oznaka`) se izpiše samo, kadar kaj pove:
  klub pri klubski ekipi z lastnim imenom, »prosta ekipa« pri prosti.
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
- **Zavihki dogodka na telefonu tečejo po vrsti tekmovanja** (`pogledi` v
  `DogodekStran`, odločitev lastnika, okt 2026): Skupine (privzeto) ·
  Izločilni del · Razvrstitev · Udeleženci. Zavihek »Razvrstitev« (končna
  razvrstitev) obstaja samo pri zaključenem dogodku, a tudi takrat ni prvi —
  privzeti pogled je prvi del tekmovanja. Izjema je krožni sistem: tam končna
  razvrstitev zamenja živo lestvico in stoji na njenem mestu (Razvrstitev ·
  Tekme · Udeleženci); SV regija ima Nivoji · Razvrstitev · Udeleženci. Na
  namizju končna razvrstitev ostane blok nad pasom pogledov.
- **Kaj pomeni klik na tekmo, odloči stran** (`KlikTekme` v `TekmaKartica`:
  `klikljiva`, `naKlik`, `namig`) — kartica mreže in vrstica seznama ne ugibata
  po stanju tekme. Posamično tekmovanje: organizatorju vnos rezultata.
  **Končana tekma z vpisanimi točkami** (`TekmaDto.nizi`, ena poizvedba na
  dogodek — `NizRepozitorij.tockeZaDogodek`) pa **vsakemu gledalcu** odpre
  `NiziTekmeOkno` (samo branje); isto okno odpre vrstica zapisnika srečanja
  (`TekmaSrecanjaDto.nizi`). Brez točk končana tekma **gostu** ni klikljiva —
  okno bi ponovilo izid s kartice; organizatorju pa je, ker je to njegov vhod
  v **popravek**: `NiziTekmeOkno` dobi `onPopravi` in pod tabelo gumb »Popravi
  rezultat«, ki odpre `VnosRezultataOkno popravek` (polja napolnjena s
  shranjenim izidom). V zapisniku srečanja isto vlogo igra gumb »Popravi« v
  stolpcu stanja (klik ustavi širjenje, ker vrstica sama odpira točke).
  Popravek je mogoč **tudi po zaključku dogodka** — takrat se napake največkrat
  odkrijejo —, ne pa pri prostem prehodu, prenesenem izidu in ekipni tekmi
  (ta ima izid iz zapisnika). Okno ne ve, od kod tekma pride: obe vrsti se pred
  klicem prevedeta v dve `StranTekme`. Pod 640 px gre ime v svojo vrstico nad
  izid in nize, sicer tekma na 7 nizov imenu pusti 36 px.
  **Ekipni dogodek**: vsakemu gledalcu zapisnik srečanja (javen), prenesen izid
  finalne skupine vodi v zapisnik predtekmovalnega srečanja, organizatorju
  pa klik na neodločeno tekmo ponudi izbiro med zapisnikom in izidom brez
  boja (`EkipnaTekmaOkno`, `VnosRezultataOkno samoBrezIgre`). Ekipe in kadre
  ekipnega dogodka kaže in ureja `EkipeDogodka` (priprava: prijava ekipe,
  sestava kadra z `IzbirnikIgralca`; med tekmovanjem samo dopolnitev kadra).
  Skupine z več stopnjami (skupine za mesta) so razdeljene z naslovi
  stopenj; tekma za 3. mesto in tolažilna mreža stojita pod glavno mrežo
  (`TolazilniDel`).
- **Zapisnik srečanja na telefonu ni tabela** (`ZapisnikMobi` v
  `SrecanjeStran`, razdelek »Zapisnik na telefonu« v `slog.css`; oktober 2026).
  Tabela petih stolpcev je bila pri 375 px široka 500 px: stolpec gostov je
  bil za robom, imena so se lomila v štiri vrstice, nizi so bili odrezani.
  Tekma je vrstica s črto: oznaka (A-X) in stanje/gumb, nato domači in gost
  vsak v svoji vrsti (ime čez vso širino, `white-space: nowrap`; par ima
  vsakega igralca v svoji vrstici), desno sprememba ratinga in dobljeni nizi,
  pod njima točke po nizih. Klik vrstice z nizi odpre `NiziTekmeOkno`, gumba
  »Popravi« / »Vnesi« klik ustavita. Pod zapisnikom (obe širini) stoji
  povezava na `/o-ratingu`, kadar ima kaka tekma spremembo ratinga.
- **Menjava v zapisniku srečanja** (`MenjavaOkno` v `SrecanjeStran`): na
  namizju ima tekma, ki čaka, gumba »Menjava« in »Vnesi«; na telefonu
  (`ZapisnikMobi`) gumba »Menjava« ni. Tam menjavo ponudi okno za rezultat
  (»Menjava igralcev«) — to je tudi trenutek, ko organizator s papirja opazi
  drugega igralca. Stran, ki igra na tujem mestu, nosi pod imenom mono oznako
  »menjava« (`.srecanje__menjava`).
- **Glava lige: ime zgoraj, sezona pod njim** (`.naslov-strani__pod`,
  `.naslov-mobi__pod`) — izrecna odločitev lastnika in zavestna izjema od
  para nadnaslov/naslov iz DESIGN.md. Glava ne nosi vrstice »10 ekip ·
  dvokrožno« (sistem je v Pravilih, potek v napredku), piramida se imenuje
  »Piramida lig« in brez kategorije, kader (v oknu ekipe) ima stolpca z
  oznakama »Rating« in »Izkupiček« (do okt 2026 angleški »Score«), vsaka desno
  nad svojimi številkami. Na
  seznamu lig stoji samo ime sistema (`imeSistema`: »SNTL« brez sestave v
  oklepaju); celoten opis ostane v obrazcu in pravilih.
- **Lestvica lige na telefonu (`LestvicaMobi`) nosi razmerji tekem in nizov**
  (okt 2026): vrstica je ime s formo, pod njim »1-1 · tekme 6:4 · nizi 22:15«,
  točke ob desnem robu. Brez razmerij igralec ni vedel, zakaj je pri petih
  ekipah z 1-1 njegova sedma. Pod lestvico, kadar imata vsaj dve ekipi enako
  točk, stoji vrstni red izenačenih (`IzenaceneEkipe`: medsebojni izid →
  razlika tekem → razlika nizov — isto kot `LestvicaLigeStoritev`), ker
  medsebojnega izida v vrstici ni. Pravila, ki jih ne razbij:
  - **Forma ima črke Z/N/P tudi na telefonu**, zadnja **tri** srečanja
    (`FORMA_TELEFON`; namizje pet). Podlagi sta srednja tona
    (`--barva-poudarek-srednje`, `--barva-negativna-srednje`) s črko v
    črnilu — mehka tona sta bila pri 20 px skoraj papir, zelena oz. rjasta črka
    na srednjem tonu pa ne doseže 4,5 : 1.
  - **Ime ekipe je modro**: vrstica je gumb, ki odpre okno ekipe, modro
    besedilo pa je v aplikaciji povezava (na namizju isto pove »Kader in
    tekme«). Ime se ne odreže, dolgo se prelomi.
- **Klik na ekipo na lestvici lige odpre okno ekipe** (`komponente/EkipaLigeOkno`,
  razdelek »Okno ekipe lige« v `slog.css`; oktober 2026 — prej je vrstica kader
  razprla pod sabo). Okno je za igralca te ekipe: kader in **vsa srečanja ekipe v
  ligi na enem mestu**. Pravila, ki jih ne razbij:
  - **Okno je v naslovu** (`/lige/5?ekipa=12`): povezavo si ekipa deli, gumb nazaj
    (kretnja na telefonu) okno zapre. Odprtje doda zapis v zgodovino (`state
    ekipaSStrani`), zaprtje se vrne nanj; kdor pride naravnost po povezavi, mu
    zaprtje parameter le pobriše (korak nazaj bi zapustil stran). Parameter
    ekipe, ki v ligi ne igra, se prezre.
  - **Srečanja okno dobi od strani** (redni del in končnica, ista poizvedba, ki se
    med večerom osvežuje); strežnik vpraša samo po kadru. Zaledje za okno nima
    nove poti.
  - **Vrstni red je po DATUMU** (prestavljeno srečanje ne premakne kola), končnica
    za rednim delom; če kateremu srečanju termin manjka, velja vrstni red kol —
    mešano merilo ne bi urejalo po nobenem.
  - **Z vidika ekipe**: »doma / v gosteh«, nasprotnik, izid »naše : njihove« z
    znakom Z/N/P (`izidZaEkipo`, isto merilo kot forma na lestvici). Srečanje v
    teku pokaže sprotni izid z oznako »V teku«.
  - **Prosto kolo je vrstica** (liga z lihim številom ekip): v razporedu je samo
    odsotnost, igralec pa mora vedeti, da tisti dan ne igra.
  - **»Naslednje«** je prvo srečanje v `RAZPORED`, ki mu termin ni mimo —
    neodigrano s preteklim terminom je zamujen vpis in bi zavajalo.
  - Na telefonu je okno cel zaslon (kot okno z merili), na namizju stolpec z
    mejo v višini zaslona — v obeh primerih drsi samo telo, ime in križec ostaneta.
    Glava: ime ekipe zgoraj, »2. mesto · 30 točk« pod njim (`ModalnoOkno
    podnaslov`) — odločitev lastnika.
- **Končnica lige ima svoj pogled** (`KoncnicaLige`): na namizju preklop
  »Lestvica / Končnica« nad lestvico, na telefonu zavihek; liga samo s
  končnico (kvalifikacije med ligami) kaže serije kar na strani. Razpored
  rednega dela srečanj serij ne kaže (`idSerija == null`).
- **Končno mesto, ki si ga deli več udeležencev, je razpon** (»3.–4.«, »5.–8.«;
  `oznakaMesta` v `pomozno/koncnaRazvrstitev`, `TekmovanjeProfila.mestoDo` na profilu).
  Izločilni sistem brez tekme za 3. mesto (uvoz Stupe) da obema polfinalistoma
  »3.«: oba sta na stopničkah s slogom tretjega (`.stopnice--s4`);
  prej je imel en slog stopničk, drugi navadno vrstico.
- **Seznam turnirjev ima razdelek »Prihajajoči«** (v pripravi ali z datumom od
  danes, ne v teku in ne zaključeni; turnir v pripravi s preteklim datumom ne,
  najbližji prvi) nad »Vsi turnirji«, ta pa je pri razvrstitvi po datumu
  razdeljen po sezonah (`zLocnicamiSezon`, `.seznam-locnica`). Vrstica na
  telefonu nosi letnico pod mesecem, obdobje v mono vrstici samo pri
  večdnevnem turnirju (»03 OKT« in »3. 10.« je bil isti podatek dvakrat), ime
  sme v dve vrstici (`.vrstica-mobi__ime`, tudi lige in kategorije).
- **Stran turnirja na telefonu ima kolofon zaprt** (Prijavljenih, Odigranih,
  Dogodki, Raven; odločitev lastnika, okt 2026): odpre ga preklopnik
  »Podrobnosti ▾« desno ob znački statusa (`aria-expanded`, isti gumb kot
  »Išči«). Beseda in ne ikona »i« — DESIGN.md, točka 9. Na namizju ostane odprt.
