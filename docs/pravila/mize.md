# Pravila: mize in klicanje tekem

> Stran Mize, klic tekme na mizo, sodnik ob klicu, predlog naslednje tekme.
> Nastalo 23. 11. 2026 (M1, M2; zasnova in odločitve lastnika v
> `docs/NACRT-BRANJE-LISTKOV.md`, razdelek »Odločitve lastnika za M1 in M2«).

## Odločitve lastnika (23. 11. 2026)

- Vse mize so za vse kategorije. Mize se ne delijo po dogodkih.
- Organizator igralce pokliče na glas, aplikacija klic samo beleži. Zaslona v dvorani,
  potisnih obvestil in javnega prikaza mize ni.
- Listki se tiskajo vnaprej, mizo v okence vpiše sodnik.
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

## Vmesnik (`strani/MizeStran.tsx`)

- Levo so mize kot vrstice s številko, desno pripravljene tekme po vrstnem redu
  predloga.
- Barve vrstic:
  - prosta miza ima modri rob in mehko podlago (čaka na dejanje);
  - pravkar sproščena je 30 s zelena;
  - izklopljena zbledi.
- Osveži se vsake 4 s, dokler je stran vidna. Minute se računajo po uri strežnika
  (`casStreznika`).
- Kratek dvojni pisk ob sprostitvi mize: zvočni kontekst nastane ob prvem kliku na
  strani, nastavitev je v `localStorage` (udobje prenosnika).
- Izid se vpiše v `VnosRezultataOkno`, pri ekipni tekmi prek povezave na zapisnik
  srečanja.
- Pomoč: članek `mize` (`pomoc/vsebina/rezultati.ts`). Ob vsaki spremembi strani ga
  posodobi.
