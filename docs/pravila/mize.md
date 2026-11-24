# Pravila: mize in klicanje tekem

> Stran Mize, klic tekme na mizo, sodnik ob klicu, predlog naslednje tekme.
> Nastalo 23. 11. 2026 (M1, M2; zasnova in odločitve lastnika v
> `docs/NACRT-BRANJE-LISTKOV.md`, razdelek »Odločitve lastnika za M1 in M2«).

## Odločitve lastnika (23. 11. 2026)

- Vse mize so za vse kategorije. Mize se ne delijo po dogodkih.
- Organizator igralce pokliče na glas, aplikacija klic samo beleži. Potisnih obvestil in
  javnega prikaza mize ni. **Zaslon za igralce (TV v dvorani) je od 24. 11. 2026 del prenove
  strani** (glej »Vmesnik«): samo za organizatorja in admina, odpre se v novem oknu v isti
  seji, bere isti klic kot stran Mize (zaledja ni treba spreminjati).
- Listki se tiskajo vnaprej, mizo v okence vpiše sodnik. Ob klicu na strani Mize se lahko
  natisne tudi listek z že vpisano mizo.
- **Poraženec prejšnje tekme na isti mizi sodi naslednjo.** To je nastavitev turnirja
  (`turnir.porazenec_sodi`). Ob klicu je poraženec predlagan, organizator ga lahko
  zamenja ali odstrani. Po tekmi dvojic sodnika ne predlaga nihče.
- Ekipno srečanje se pokliče na eno ali dve mizi.
- Pri predlogu ima zaostala skupina prednost pred počitkom. Počitek je privzeto 3 min
  (`turnir.pocitek_min`, 0–30).
- Stran je za prenosnik na pultu. Na telefonu deluje, a zanj ni optimizirana.

## Model (V76)

- `turnir.stevilo_miz` je 1–99 (toliko jih prebere okence listka, `MizaListka`).
  Vrednost `null` pomeni, da mize niso nastavljene in klic ni mogoč.
  `turnir.izklopljene_mize` je besedilo »3,7«.
- Na tekmi so `miza`, `miza_2` (samo ekipno srečanje), `poklicana_ob`, `koncana_ob`
  in `id_sodnik` (igralec; ON DELETE SET NULL). Združitev igralcev
  (`IgralciZdruzitevStoritev`, `PopravekZgodovineUkaz`) preusmeri tudi `id_sodnik`.
- **Nespremenljivka v `Tekma.setStatus`** (CHECK v bazi to varuje):
  - klic (miza, druga miza, čas klica, sodnik) obstaja samo pri `V_IGRI` in `KONCANA`;
  - prehod v `CAKA` ali `PRIPRAVLJENA` klic pobriše;
  - `koncana_ob` je čas prehoda v `KONCANA` in ga izhod iz `KONCANA` pobriše.

  Zato mizo sprosti vsaka pot, ki tekmo konča (vnos, sodnik s telefonom, branje na
  pultu, odstop, brez boja, ekipno srečanje), in nobena pot ne pušča »visečega« klica.
  Nova pot, ki spremeni status tekme, gre skozi `setStatus` in ne skozi masovni
  `UPDATE` (izjema je `oznaciPripravljenoCeStaOba`, ki gre iz `CAKA`, kjer klica ni).
- `koncana_ob` je čas prehoda, ne čas igre: prosti prehod in uvožene tekme ga dobijo
  ob nastanku. Počitek zato upošteva samo igrane tekme (ne `PROSTO`, ne prenesene).
- Miza ostane zapisana tudi na končani tekmi. Iz nje se ve, kdo je na mizi zadnji
  izgubil.

## Klic, premik, preklic (`MizeStoritev`)

- Poti so samo za organizatorja turnirja in admina, tudi GET
  (`/api/v1/turnirji/*/mize` stoji v `VarnostneNastavitve` pred splošnim
  »GET je javen«). Lastništvo preveri storitev. Vse spremembe tečejo pod zaklepom
  turnirja, zato dva klica na isto mizo ne prestaneta obeh preverb.
- Klic `PUT /api/v1/tekme/{id}/klic {miza, miza2, idSodnika}`:
  - dogodek je `V_TEKU`, mize so nastavljene;
  - tekma je `PRIPRAVLJENA` z obema udeležencema, že poklicana (to je premik ali
    zamenjava sodnika; čas klica ostane) ali ekipna v `V_IGRI` brez mize (srečanje
    je začela postava);
  - miza obstaja, ni izklopljena in na njej ne igra druga tekma;
  - druga miza velja samo pri ekipnem dogodku;
  - sodnik je igralec s prijavo na turnir (posamezno, v paru ali v kadru ekipe) in
    v tej tekmi ne igra.

  Igralec ali sodnik, zaseden drugje, ni napaka: vmesnik opozori, odloči organizator.
- Preklic (`DELETE …/klic`) vrne tekmo v `PRIPRAVLJENA`. Ekipna tekma, katere
  srečanje že ima postavo, ostane `V_IGRI` in izgubi samo mizo.
- Števila miz ni mogoče zmanjšati in mize ni mogoče izklopiti, dokler se na njej igra.
- **Poklicana tekma velja za začeto** (`ZrebStoritev.zacet`, SV regija, vmesnik):
  žreba kategorije po klicu ni mogoče ponoviti, po preklicu pa spet je.
- **Miza z listka** (`zapisiMizoZListka`, ob potrditvi branja ali sodnikove
  potrditve): končana tekma, ki ni bila poklicana, dobi sodnikovo ali zanesljivo
  prebrano mizo. To se zgodi samo, če miza obstaja in na njej ne igra druga tekma.
  Če se miza z listka razlikuje od klica, to vidita sodnik (S2) in organizator
  (stran Branje listkov).

## Predlog naslednje tekme (`PredlogTekem`, čist razred s testi)

- Vhod je posnetek turnirja: vse tekme dogodkov v teku, tekme v igri in tekme,
  končane danes.
- **Kandidati** so tekme `PRIPRAVLJENA` z obema udeležencema in ekipne tekme v
  `V_IGRI` brez mize.
- **Zaseden** je, kdor igra ali sodi tekmo v `V_IGRI`. Zasedenost se meri po osebi
  (igralec v posamični tekmi in dvojicah) in po prijavi (ekipa). Pri ekipi štejejo
  igralci postave. Tekma z zasedenim udeležencem ni predlagana, ostane pa na seznamu
  z razlogom.
- **Vrstni red** (vsako merilo odloči šele pri izenačenju prejšnjega):
  1. tekme brez poraženca, ki čaka na sojenje, pridejo pred tekme s takim igralcem;
  2. **zaostanek**: koliko kol ima skupina od kola te tekme do konca (v izločilnem
     delu mreža). Šteje skupina, ne igralec (lastnik 23. 11. 2026): skupina po 3 ima
     3 kola kot skupina po 4, zato njeno 1. kolo pride med 1. kola ostalih, ne na
     konec (prej je štel igralec z največ preostalimi tekmami in skupine po 3 so
     zdrsnile za vse skupine po 4). Posledice:
     - **zaostala tekma** nižjega kola gre pred višje kolo drugih skupin, tudi pred
       počitkom;
     - **večja skupina prej**: skupina po 5 (5 kol) odigra 1. in 2. kolo pred 1. kolom
       skupin po 4, da konča hkrati z njimi. Njeni igralci zato lahko igrajo dve koli
       zapored brez počitka;
     - **kategorije se izmenjujejo po kolih**: ocene izločilnega dela, ki sledi, ni,
       zato kategorija z več skupinami ne gre vsa naprej;
  3. **počitek**: tekme, v katerih so vsi udeleženci končali pred vsaj N min, pridejo
     pred ostale;
  4. nižje kolo;
  5. **najdlje čaka**: odloči najkrajši premor med udeleženci, kdor še ni igral, čaka
     najdlje;
  6. stalen vrstni red: dogodek, **skupina po vrsti kot na listkih** (1, 2, 3 …, pri
     starejših dogodkih in v SV regiji A, B, C …; krajša oznaka prej, zato 9 pred 10
     in Z pred ZA, isto pravilo kot `SkupinaRepozitorij.findByDogodekIdOrderByOznakaAsc`),
     pozicija, id. V istem kolu gredo tako obe tekmi skupine 1, nato skupine 2 …
- **Poraženec sodi** (ko je vklopljen): predlagani sodnik proste mize je poraženec
  zadnje končane tekme na njej. Pogoji:
  - tekma posameznikov z izidom odigrano ali predaja;
  - končana današnji dan;
  - poraženec ne igra in ne sodi;
  - od tedaj ni bil poklican (ne kot igralec ne kot sodnik).

  Ob klicu tekme, v kateri igra, ni predlagan.
- **Razporeditev po mizah:** vsaka prosta miza dobi svoj prvi predlog. Najprej pridejo
  na vrsto mize, kjer poraženec čaka na sojenje (prej sproščena prej), nato ostale po
  številki. Dve mizi nimata istega prvega predloga in ne tekem z isto osebo. Tudi
  sodnik predloga ene mize ne igra v predlogu druge: »Pokliči predloge« pokliče vse
  prve predloge naenkrat. Zamenjavi (največ dve) sta naslednja prosta kandidata.

## Vmesnik (`strani/MizeStran.tsx`, prenova »tloris dvorane«, 24. 11. 2026)

Po predaji `design_handoff_mize_tloris` (smer 1b). Komponente: `komponente/MizeTloris.tsx` (glava,
črna vrstica, tloris, vrsta), `MizeOkna.tsx` (klic, izid, prvi zagon), `MizeTelefon.tsx`,
`MizeListekTisk.tsx`, stran `MizeZaslonStran.tsx`; CSS razdelek »Mize« v `slog.css`.

- **Tloris:** mize so pravokotniki z mrežo na sredini, priimek vsakega igralca na njegovi strani.
  Stanja: prosta (modro črtkana, predlog + »Pokliči ›«), pravkar sproščena (30 s zelena, obstoječa
  logika `sprosceneMize` + pisk), v igri (črna, modri trak časa), predolgo (30 min,
  `MEJA_PREDOLGO_MIN` v `pomozno/mize.ts`; rjasta črta + minute v znački; ne velja za ekipno),
  izklopljena (šrafirana), tarča (med vlečenjem). Ekipno srečanje je ena enota čez dve mizi.
- **Vlečenje (HTML5):** kartica vrste → prosta miza = klic (sodnik po pravilu »poraženec sodi«,
  druga miza ekipnega = sosednja prosta, listek se natisne); zasedena miza → prosta miza = premik
  (sodnik ostane). Med vlečenjem se osveževanje (4 s) ustavi in elementi se ne zamenjujejo (samo
  razred). Isto je mogoče s klikom in tipkovnico (Enter, presledek): prosta miza s predlogom ali
  kartica → okno klica, zasedena miza → okno izida.
- **Sporočilo z razveljavitvijo** (6 s) po klicu, »Pokliči vse« in premiku; izid razveljavitve nima.
- **Okno izida** izbere zmagovalca in izid v nizih, shranjevanje pa teče v `VnosRezultataOkno` z
  že izbranim izidom (odločitev lastnika, 24. 11. 2026: točke po nizih lahko odločijo o vrstnem
  redu v skupini, zato hitrega shranjevanja brez njih ni). Okno vsebuje tudi »Zamenjaj sodnika«
  (okno klica v načinu spremembe) in »Prekliči klic« (s potrditvijo).
- **Uredi dvorano** (črna vrstica): klik na prosto mizo jo izklopi, na izklopljeno vklopi;
  »+ Miza« in »− Odstrani zadnjo« (ne, dokler se na zadnji igra); Poraženec sodi, **Počitek**
  (0–30 min) in **Pisk ob prosti mizi** (`localStorage`, udobje prenosnika) so v drugi vrstici
  črne vrstice. Vse gre skozi `mizeApi.nastavi` (stanje se prebere znova pred vsako spremembo).
- **Listek ob klicu** (`MizeListekTisk`): skrit natisljiv vsebnik v portalu + `window.print()`;
  podatki iz žreba dogodka kot na strani Listki, miza v okencu je klicana miza. Sled tiska
  (»natisnjeno«) se NE zapisuje (potrdi jo organizator na strani Listki). **Ekipno srečanje ima
  zapisnik, ki se ob klicu ne tiska samodejno** (zapisnik odpre »Odpri zapisnik«).
- **Zaslon za igralce** (`/turnirji/:id/mize/zaslon`, brez glave aplikacije, oder 1280 × 720 s
  `transform: scale()`): zadnjih 5 klicev, 6 naslednjih tekem brez zasedenih igralcev in
  zemljevid dvorane; osvežuje se vsake 4 s. Najnovejši klic je »pravkar« do 5 min starosti.
- Telefon (≤ 640 px): pomanjšan tloris, tap na mizo, nato tekma (»Na N« takoj pokliče).
- Imena na mizi so priimki (`priimekNaMizi`: strežnikov `priimek`, pri dvojicah » / «, pri
  ekipah ime ekipe). Razlogi zasedenosti: `razlogiZasedenosti`; razlogi predloga so samo v oknu
  klica.
- Pomoč: članek `mize` (`pomoc/vsebina/rezultati.ts`). Ob vsaki spremembi strani ga posodobi.
- Preverba videza: razvojni harness `/dev/mize/:id?stanje=…` in `/dev/mize-zaslon/:id`
  (`dev/MizePosnetki.tsx`, `dev/mizePodatki.ts`, prepis podatkov prototipa).
