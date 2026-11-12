# Pravila: vloge in dostopi

> Vloge, lastništvo zapisov, organizatorski pregled, dostop organizatorjev do tekmovanj.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Avtorizacija in vloge:** pet vlog — `ADMIN` (vse), `ORGANIZATOR`
  (ustvarja turnirje/lige in upravlja **samo svoje** oz. klubske), `IGRALEC`
  (svoj profil), `NAVIJAC` (ne igra: spremlja lige, V55 — glej spodaj) in gost
  (neprijavljen, samo `GET`). Osnovna raven je v
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
- **Organizatorski pregled** (`OrganizatorPregledStoritev`, `GET
  /api/v1/organizator/pregled`, samo `ORGANIZATOR`; pot mora v
  `VarnostneNastavitve` stati pred »GET je javen«, varuje ga
  `OrganizatorPregledDostopTest`). Merila, ki jih ne razbij:
  - **»Moja« tekmovanja so tista, ki jih je račun USTVARIL ali mu jih je admin
    DODELIL** (`ustvaril` + `dostop_organizatorja`, glej spodaj): isto merilo kot
    meja paketa. Klubska tekmovanja drugih organizatorjev so urejevalcu dostopna,
    a v pregledu jih ni.
  - **Poraba paketa je številka iz `NarocninaStoritev`** (`omejitev`,
    `steviloLigVSezoni`, `steviloTurnirjevVSezoni`), ne štetje vrstic seznama:
    strežnik ob ustvarjanju preverja natanko to (ustvarjeno od 1. julija,
    `Sezona`). Vrstic je lahko več (tekmovanje, ustvarjeno pred 1. julijem, ki se
    igra v tej sezoni, na seznamu je, v meji ni). Meja se ponastavi 1. julija
    (`naslednjaSezonaOd`), ne 1. septembra, kot piše maketa.
  - **Tekmovanje je v »tekoči« sezoni, ce se v njej igra ALI je bilo v njej
    ustvarjeno ALI teče**; sicer je v arhivu po sezoni igranja (turnir: datum
    začetka, liga: polje `sezona`). Tako nobeno plačano ali tekoče tekmovanje
    ne izpade s seznama.
  - **Prijave vpisuje organizator, potrjevanja ni**, zato blok »Čaka te« nima
    »prijav za potrditev«: ob rezultatih in zapisnikih kaže turnirje, ki čakajo
    ŽREB (`cakaZreb`: v pripravi, ≥ 2 prijavi, rok prijave potekel ali začetek v
    7 dneh). Turnir nima omejitve mest ne datuma odprtja prijav, zato v vrstici
    ni »41 / 48«: palica meri, koliko okna za prijave je minilo.
  - **Zapisnik čaka za srečanje s terminom pred DANAŠNJIM dnem** (danes je še
    prihodnost); zapisi brez ure so krajši od meje, zato datum preveri Java.
  - Zaledje pošlje števila, datume in imena; **sklanjanje in poved sestavi
    vmesnik** (`pomozno/organizatorPregled.ts`).

- **Dostop organizatorjev do tekmovanj in štetje v mejo paketa** (V62, nov 2026;
  `DostopOrganizatorjevStoritev`, `DostopOrganizatorjevKontroler`, tabela
  `dostop_organizatorja`, vmesnik `OrganizatorjiOkno`). Admin na strani lige ali
  turnirja (gumb »Organizatorji«, vidi ga samo admin) določi, kateri organizatorji
  tekmovanje urejajo in ali se jim šteje v mejo paketa. Merila, ki jih ne razbij:
  - **Dostop ima zdaj tri poti** (`LastnistvoStoritev.preveri`): ustvaril, klub
    lastnik, dodelitev. Dodelitev je vrstica `(turnir ali liga, račun,
    steje_v_mejo, dodeljen_ob)`; poizvedba v bazo je zadnja, ker prvi dve ne
    potrebujeta nobene. Vmesnik jo bere iz `UporabnikDto.dostopTurnirji/dostopLige`
    (`smemUrejati` ima tretji parameter: tekmovanje) in se po dodelitvi osveži ob
    naslednji prijavi/osvežitvi profila.
  - **Vrstica o lastniku dostopa ne daje, samo prepiše štetje.** Privzetek lastnika
    je »šteje«, ki se NE hrani: preklop nazaj vrstico zbriše (stanje brez vrstice je
    vedno privzeto). Lastništvo (`ustvaril`) se ne spremeni nikoli.
  - **Meja = ustvarjeno (brez izvzetih) + dodeljeno, ki šteje**
    (`NarocninaStoritev.steviloTurnirjevVSezoni/steviloLigVSezoni`; poizvedbe v
    `DostopOrganizatorjaRepozitorij`). Ustvarjeno šteje od nastanka tekmovanja,
    dodeljeno od `dodeljen_ob` (trenutek dodelitve), zato dodelitev starega turnirja
    ne polni meje, ki se je že ponastavila. Ista številka gre v ustvarjanje,
    organizatorski pregled in karton računa (Dostopi). Kvalifikacije med ligami
    ne štejejo nikoli.
  - **Admin sme mejo preseči**: dodelitev se zaradi polne meje ne zavrne, okno pa
    presežek pokaže rjasto (»čez mejo«). Meja ostane pravilo ustvarjanja.
  - **Samo admin, tudi branje** (odgovor nosi e-poste organizatorjev): pravilo v
    `VarnostneNastavitve` stoji pred »GET je javen« in pred »organizator sme
    turnirje/lige«; druga obramba je `preveriAdmina` v storitvi. Varuje ga
    `DostopOrganizatorjevDostopTest`. Dodeli se lahko samo potrjen in aktiven
    organizator; igralec, navijač in admin ne.
  - Vsaka sprememba gre v dnevnik računa organizatorja (`RacunDnevnikStoritev`),
    kjer jo admin najde, ko organizator vpraša, zakaj mu je meja polna.
  - **Kvalifikacije podedujejo dodeljen dostop višje lige** (`KvalifikacijeStoritev.
    podedujDodeljenDostop`, vrstica z »ne šteje«): ob nastanku, kot podedujejo
    lastnika in klub. Dodelitev, vpisana pozneje, se ne prenese sama.
  - **Ni pokrito**: organizatorji istega kluba imajo dostop samodejno in se jim
    tekmovanje ne šteje v mejo (okno to pove z opombo); samodejnega dostopa prek
    kluba ni mogoče ugasniti. Dodeljeno tekmovanje je v knjižnici logotipov
    (`SlikaRepozitorij.knjiznicaOrganizatorja`) in organizatorskem pregledu.
  - Testi: `DostopOrganizatorjevTest` (storitev), `DostopOrganizatorjevDostopTest`
    (varnostna veriga).

- **Organizatorski pregled** (`strani/OrganizatorskiPregledStran.tsx`, razdelek
  »Organizatorski pregled« na koncu `slog.css`, `.org-pregled__…`) je stran
  `/moj-profil` za organizatorja (`MojProfil` v `App.tsx`), poustvarjena po
  `design_handoff_organizatorski_pregled` (smer 1a). V vmesniku se imenuje
  **»Nadzorna plošča«** in je PRVA postavka namizne navigacije (pred »Domov«;
  `nadzornaPlosca` v `Postavitev`, ločena od `povezave`, ker spodnja vrstica na
  telefonu jemlje njihove prve štiri) in v uporabniškem meniju; igralcu ostane
  »Moj profil« (pot je ista). Namizna glava organizatorja je s to oznako široka
  ~770 px — pod tem se ob dolgem imenu v kotu prekrijeta navigacija in ime.
  Pasti:
  - **Drevesi sta dve** (`pomozno/sirinaOkna.ts`, 1024 px): namizje ima pas
    sezone, gumba za ustvarjanje in dva stolpca, telefon krajše vrstice in en
    seznam. Pod 640 px stoji poleg tega telefonska glava in spodnja vrstica
    (`Postavitev`), neodvisno od te točke.
  - **Merilo ujemanja je prototip `*.dc.html` v istem brezglavem Chromu**, ne
    PNG; primerjaj besedilne liste (tesna škatla + slog) po besedilu. Izmerjeno:
    namizje 0,5 %, telefon 0,14–0,19 % različnih pikslov, vse ostalo je
    podatkovno besedilo. Okvir prototipa leži na necelem pikslu (292,5625) - pred
    diffom ga poravnaj, sicer se vsako besedilo drugače zaokroži.
  - **Razmik črk naslova je −1,12 px**, ne `-0.035em` na spanih: maketa ga
    postavi na h1 s privzetimi 32 px (isti razlog kot pri strani naročnine).
    Koren strani zato postavi `font: 400 16px / normal`; sicer bi vsaka vrstica
    podedovala 17 px in 1,5 iz `body`.
  - **Gumb vsebino navpično centrira**: aktivni filter brez roba (maketa je
    span) bi zdrsnil za piksel, zato ima spodnji odmik 10 px namesto raztegnitve.
  - **Lik ob palici** (`LikNaPalici`, izjema od DESIGN.md 5b): palica ima
    `overflow: hidden`, zato lik stoji v ovoju ob njej. Tabla izstopi navzdol
    (`clip-path` ovoja), sedeči lik vstran; ob `prefers-reduced-motion` je
    nepremičen. Črte se večajo z likom (brez `vector-effect`).
  - **Sklanjanje ima lasten `sklon`** (ostanek pri 100: »64 igralcev«) — od
    oktobra 2026 po istem pravilu kot skupni `sklon` v `oblikovanje.ts`.
  - Gumba »+ Nova liga« / »+ Nov turnir« ob polni kvoti odpreta obvestilo o
    nadgradnji (`ObvestiloONadgradnji`), ne blokirata tiho; obrazec za turnir je
    `NovTurnirOkno` iz `TurnirjiStran.tsx`.
  - `?sezona=2025/26` prikaže pretekle sezone iz arhiva na isti strani; brez
    veljavne sezone velja tekoča. Cena 0 (prehodna doba, V34) se izpiše
    »Brezplačno«.
