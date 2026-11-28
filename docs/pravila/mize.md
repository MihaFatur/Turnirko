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
  natisne tudi listek z že vpisano mizo, a samo, če organizator to odkljuka v oknu klica
  (privzeto izklopljeno; vlečenje, telefon in »Pokliči vse« ne tiskajo; lastnik
  24. 11. 2026). Do strani Listki za tiskanje vodi gumb v glavi strani Mize.
- **Vrstni red vrste »Na vrsti« sme organizator urediti sam** (lastnik 24. 11. 2026,
  V77, glej »Ročni vrstni red«).
- **Poraženec prejšnje tekme na isti mizi sodi naslednjo.** To je nastavitev turnirja
  (`turnir.porazenec_sodi`). Ob klicu je poraženec predlagan, organizator ga lahko
  zamenja ali odstrani. Po tekmi dvojic sodnika ne predlaga nihče.
- Ekipno srečanje se pokliče na eno ali dve mizi.
- Pri predlogu ima zaostala skupina prednost pred počitkom. Počitek je privzeto 3 min
  (`turnir.pocitek_min`, 0–30).
- Stran je za prenosnik na pultu. Na telefonu deluje, a zanj ni optimizirana.

## Odločitve lastnika (25. 11. 2026): skupne mize in strogo po kolih

Vprašanje lastnika: v isti dvorani pod istim organizatorjem igrata hkrati dva turnirja
(npr. U21 moški in ženske ter U15 moški in ženske) na istih mizah; organizator naj ju na
strani Mize in pri tisku listkov vidi kot celoto, gledalec pa kot prej.

- **Povezavo naredi organizator** na strani Mize (Uredi dvorano → Turnirji in kategorije),
  ne samodejno po dnevu. Povezati sme samo **svoje turnirje** (vedno isti račun) na **isti
  dan**.
- **Vrstni red kategorij v kolu nastavi organizator** (npr. U15m, U15ž, U21m, U21ž).
- **Predlog gre strogo po kolih, povsod** (tudi en sam turnir): najprej celo 1. kolo vseh
  skupin vseh kategorij, nato 2. kolo … To **preklicuje »večja skupina prej«** s 23. 11.
  (skupina po 5 zdaj konča zadnja). **Krog izločilne mreže šteje kot naslednje kolo** za
  zadnjim kolom skupin svoje kategorije.
- **Poraženec sodi katerokoli** naslednjo tekmo na mizi, tudi iz drugega turnirja.
- **En tisk listkov** za vse kategorije obeh turnirjev.
- Skupno je tudi **branje listkov na pultu, zaslon za igralce, počitek in »poraženec
  sodi«** (število in izklopljene mize tako ali tako).
- Ni bilo nujno za soboto 28. 11. (»lahko pozneje«).

## Odločitev lastnika (28. 11. 2026): vrstni red kol

- Poleg vrstnega reda kategorij sme organizator na strani Mize razvrstiti tudi **kola
  skupin vsake kategorije** (npr. 3, 1, 2 namesto 1, 2, 3); vrstni red predlaganih tekem
  se ravna po njem.
- Izvedba (izbira Claude, lastnik ni posebej odločal): vrstni red je **lastnost kategorije**
  (`dogodek.vrstni_red_kol`), ne skupnih miz, zato ostane ob povezavi in razdružitvi.
  Razvrščajo se samo **kola skupin** (tudi krožni sistem in skupine SV regije); izločilni
  del ostane za vsemi koli skupin, ker mreža brez prejšnjih krogov ne more teči. Po istem
  vrstnem redu gredo **listki za tisk po kolih** (tudi stolpci tabele in »Na vrsti«), da
  tisk ostane enak predlogu.

## Model (V76, V78, V80)

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
- `dogodek.vrstni_red_kol` (V80, besedilo »3,1,2«; NULL = 1, 2, 3 …) je vrstni red kol
  skupin kategorije. Velja `PredlogTekem.redKol`: našteta kola naprej, manjkajoča po vrsti
  za njimi, kola, ki jih ni (žreb ponovljen z manjšimi skupinami), odpadejo. Isto pravilo
  ima vmesnik (`pomozno/listki.ts`, `redKol`). Navzven gre v `DogodekDto.vrstniRedKol`
  (tisk) in kot izpeljan seznam `KategorijaMizDto.kola` (okno na strani Mize).
- **Vrstni red kol** `PUT …/mize/red-kol {idDogodka, kola}`: kategorija turnirja z istimi
  mizami (dostop do obeh turnirjev), samo kola od 1 do zadnjega kola skupin
  (`TekmaRepozitorij.zadnjeKoloSkupin`); kategorija brez skupin vrne napako. Prazen seznam
  = 1, 2, 3 … Teče pod zaklepom turnirja kategorije (lastnost kategorije, ne miz).

## Skupne mize (V78, `MizeStoritev`)

- `turnir.id_skupne_mize` kaže na **glavni turnir** skupine (NULL = sam ali glavni). Skupina
  je globoka en nivo: ko se pridruži turnir, ki že ima člane, gredo vsi pod novega glavnega.
  Glavni nosi **nastavitve miz** (`stevilo_miz`, `izklopljene_mize`, `porazenec_sodi`,
  `pocitek_min`), **ročni vrstni red** (`vrstni_red_tekem`) in **vrstni red kategorij**
  (`vrstni_red_dogodkov`, id-ji dogodkov). Nastavitve članov se med povezavo ne berejo.
- Turnir brez povezave je skupina enega: vse poti delujejo kot prej.
- **Pregled** (`GET …/mize` kateregakoli člana) sestavi posnetek vseh turnirjev skupine;
  `MizeTurnirjaDto.skupne` nosi turnirje (glavni prvi) in kategorije v vrstnem redu kola,
  `TekmaMizeDto` turnir tekme. Pravica: dostop do turnirja, ki ga uporabnik odpre; dejanja
  na tekmi preverijo turnir tekme.
- **Povezava** `PUT …/mize/skupne {idTurnirja}`: ta turnir (z vsemi, s katerimi že deli
  mize) se pridruži skupini izbranega. Pogoji: vsi turnirji obeh skupin so
  organizatorjevi (`preveriTurnir`, uvoženi odpadejo), turnirja se dotikata istih dni,
  mize v igri se ne prekrivajo in obstajajo v skupnih mizah. Nastavitve ostanejo
  izbranega, razen če jih ta še nima (takrat jih dobi od pridruženega). Vrstni red
  kategorij pridruženih gre za že razvrščenimi. `GET …/mize/za-povezavo` ponudi
  organizatorjeve turnirje istih dni (`najdiVObdobju` + `smeUrejatiTurnir`).
- **Razdružitev** `DELETE …/mize/skupne`: turnir dobi kopijo nastavitev in vrstnih redov
  skupine (tekme v igri ostanejo na svojih mizah). Če odide glavni, skupino prevzame
  naslednji (po datumu in id).
- **Vrstni red kategorij** `PUT …/mize/red-kategorij {idjiDogodkov}`: samo dogodki
  turnirjev skupine; prazen seznam = privzeti red (po turnirju, glavni prvi, nato po
  nastanku kategorije).
- **Zaklepi:** `Zaklepi.Vrsta.MIZE` s ključem glavnega turnirja, vedno ZA zaklepom turnirja
  (tekmovanje → mize → rating). Klic: zaklep turnirja tekme, nato mize. Nastavitve: turnir
  glavnega (entiteta z verzijo), nato mize. Povezava: vsi turnirji obeh skupin po vrsti
  id-jev, nato mize obeh. Če se povezava spremeni, medtem ko zahteva čaka, ta odneha
  (»Skupne mize turnirja so se ravno spremenile«); miza z listka se takrat preskoči.
- **Sodnik** je igralec kateregakoli turnirja skupine; `osebe` vrne vse.
- **Branje listkov** (`BranjeListkovStoritev`): pult bere kode in imena tekem vseh turnirjev
  skupine, seznam kaže branja vseh. Branje ostane na turnirju, kjer je bila fotografija
  poslana; potrditev tekme drugega turnirja preveri še dostop do njega. Stikalo »Sodniki
  slikajo listke« se nastavi vsem turnirjem skupine.
- **Tisk** (`/turnirji/:id/listki`, `SkupniListkiStran`): en tisk vseh posamičnih kategorij
  v teku, glej `docs/pravila/tisk.md`.
- Gledalec povezave ne vidi: nobena javna pot je ne bere.

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
  2. **strogo po kolih** (lastnik 25. 11. 2026, velja povsod): kolo skupine, v izločilnem
     delu (glavna in tolažilna mreža, žrebi SV regije) krog mreže **za zadnjim kolom
     skupin svoje kategorije** (pri 3 kolih skupin je 1. krog mreže 4. kolo). Posledice:
     - **zaostala tekma** nižjega kola gre pred višje kolo drugih skupin, tudi pred
       počitkom;
     - skupina po 3 (3 kola) gre v kolu med skupinami po 4;
     - **skupina po 5 konča zadnja** (4. in 5. kolo igra sama); to preklicuje »večja
       skupina prej« s 23. 11. 2026;
     - kategorije gredo po kolih: najprej 1. kolo vseh;
     - **vrstni red kol** (V80): kolo skupin šteje po mestu v vrstnem redu kol svoje
       kategorije. Pri 3, 1, 2 gre 3. kolo te kategorije v prvi val skupaj s 1. kolom
       ostalih. Krog mreže je še vedno za zadnjim kolom skupin (4. pri treh kolih).
       Razlog predloga pove pravo kolo (»3. kolo«);
  3. **počitek**: tekme, v katerih so vsi udeleženci končali pred vsaj N min, pridejo
     pred ostale (samo v istem kolu);
  4. **vrstni red kategorij** (skupne mize, V78), ki ga nastavi organizator; brez njega
     po turnirju in nastanku kategorije;
  5. stalen vrstni red: dogodek, **skupina po vrsti kot na listkih** (1, 2, 3 …, pri
     starejših dogodkih in v SV regiji A, B, C …; krajša oznaka prej, zato 9 pred 10
     in Z pred ZA, isto pravilo kot `SkupinaRepozitorij.findByDogodekIdOrderByOznakaAsc`),
     žreb, pozicija, id. V istem kolu gredo tako obe tekmi skupine 1, nato skupine 2 …
     Merilo »najdlje čaka« (23. 11.) je odpadlo: v kolu odloči red kategorij.
- **Poraženec sodi** (ko je vklopljen): predlagani sodnik proste mize je poraženec
  zadnje končane tekme na njej. Pogoji:
  - tekma posameznikov z izidom odigrano ali predaja;
  - končana današnji dan;
  - poraženec ne igra in ne sodi;
  - od tedaj ni bil poklican (ne kot igralec ne kot sodnik).

  Ob klicu tekme, v kateri igra, ni predlagan.
- **Ročni vrstni red (V77, `turnir.vrstni_red_tekem`, besedilo »12,45,7«):** ko
  organizator kartico v vrsti povleče na drugo mesto (ali Alt + ↑/↓), vmesnik pošlje
  `PUT /api/v1/turnirji/{id}/mize/vrstni-red {idjiTekem}` s **celo vrsto, kot jo vidi**.
  Shranijo se samo tekme tega turnirja (isti posnetek kot pregled). `PredlogTekem`
  (`poRocnemRedu`) tekme s seznama postavi naprej v tem redu **pred vsemi merili**
  (tudi pred zasedenostjo in počitkom: kar je organizator uredil, ostane), tekme brez
  mesta (pripravile so se pozneje) sledijo v samodejnem redu. Zasedena tekma ostane na
  svojem mestu, mize pa ne dobi (razporeditev po mizah jo preskoči). Prazen seznam vrne
  samodejni red (gumb »Samodejni vrstni red«). `MizeTurnirjaDto.rocniVrstniRed` pove,
  ali je vsaj ena tekma vrste v ročnem redu. Zavestna izbira: cela vrsta namesto
  »pripetih« tekem, ker je tako vrsta po premiku natanko taka, kot jo je organizator
  videl; cena je, da počitek urejenih tekem ne premika več. Zaslon za igralce bere isti
  vrstni red.
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
  druga miza ekipnega = sosednja prosta, brez tiska); kartica vrste → drugo mesto v vrsti = ročni
  vrstni red (modra črta med karticami, zgornja polovica kartice = pred njo; s tipkovnico
  Alt + ↑/↓; vrsta se uredi takoj, osveževanje počaka na shranjevanje; sporočilo z
  razveljavitvijo vrne prejšnji red); zasedena miza → prosta miza = premik (sodnik ostane). Med vlečenjem se osveževanje (4 s) ustavi in elementi se ne zamenjujejo (samo
  razred). Isto je mogoče s klikom in tipkovnico (Enter, presledek): prosta miza s predlogom ali
  kartica → okno klica, zasedena miza → okno izida.
- **Sporočilo z razveljavitvijo** (6 s) po klicu, »Pokliči vse«, premiku in spremembi vrstnega
  reda; izid razveljavitve nima.
- **Turnirji in kategorije** (Uredi dvorano, `komponente/MizeSkupne.tsx`): povezava turnirjev,
  vrstni red kategorij (↑/↓) in **vrstni red kol** (V80): za vsako kategorijo v teku z vsaj
  dvema koloma skupin vrsta kol, levo prej, vsako kolo s puščicama ← in → (zamenja mesto s
  sosedom, shrani se takoj); »Po vrsti« vrne 1, 2, 3. Ko je vrsta urejena ročno, okno
  opozori, da premaknjene tekme ostanejo na svojih mestih (ročni red je pred vsemi merili).
- **Listki za tiskanje** (glava): ena kategorija v teku = povezava na `/dogodki/:id/listki`,
  več = spustni meni kategorij (ekipna: zapisniki); na telefonu v meniju »⋯«.
- **Okno izida** izbere zmagovalca in izid v nizih, shranjevanje pa teče v `VnosRezultataOkno` z
  že izbranim izidom (odločitev lastnika, 24. 11. 2026: točke po nizih lahko odločijo o vrstnem
  redu v skupini, zato hitrega shranjevanja brez njih ni). Okno vsebuje tudi »Zamenjaj sodnika«
  (okno klica v načinu spremembe) in »Prekliči klic« (s potrditvijo).
- **Uredi dvorano** (črna vrstica): klik na prosto mizo jo izklopi, na izklopljeno vklopi;
  »+ Miza« in »− Odstrani zadnjo« (ne, dokler se na zadnji igra); Poraženec sodi, **Počitek**
  (0–30 min) in **Pisk ob prosti mizi** (`localStorage`, udobje prenosnika) so v drugi vrstici
  črne vrstice. Vse gre skozi `mizeApi.nastavi` (stanje se prebere znova pred vsako spremembo).
- **Listek ob klicu** (`MizeListekTisk`, samo s kljukico v oknu klica, privzeto izklopljeno):
  skrit natisljiv vsebnik v portalu + `window.print()`;
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
