# Pravila: branje listkov s fotografije

> Organizator s telefonom fotografira izpolnjen sodniški listek, Turnirko prebere točke in predlaga izid, organizator ga potrdi.
> Nastalo 19. 11. 2026 po naročilu lastnika. Migracija V68.
> Načrt nadaljnjega dela: `docs/NACRT-BRANJE-LISTKOV.md`. **Od 22. 11. 2026 je smer
> »sodnik slika listek«** (faze S1, S2, M1, M2): sodnik listek fotografira s svojim
> telefonom brez prijave in izid potrdi sam, organizator nadzira in kliče tekme na
> mize; pult ostane rezerva. Postaja in škatla sta ustavljeni.

- **Zakaj:** na turnirju sta morala biti dva organizatorja, ker prepisovanje listkov v
  Turnirko traja predolgo. Do 22. 11. 2026 je veljalo, da sodniki pri mizi telefona
  nimajo, zato se je listek fotografiral na pultu. Lastnik je nato odločil, da listek
  slika sodnik sam takoj po tekmi (manj dela in napak za organizatorja, prosta miza je
  znana takoj); kdor telefona nima, nese listek na pult.
- **Brezplačno in brez omejitve** (odločitev lastnika): bere lastna nevronska mreža na
  strežniku (`si.turnirko.branje`), brez zunanjih storitev. Branje s Claudom je bilo
  zgrajeno in na zahtevo lastnika odstranjeno (strošek na listek). Ne uvajaj plačljive
  storitve brez izrecne odločitve lastnika.
- **Izid zapiše samo potrditev**, nikoli branje: `BranjeListkovStoritev.potrdi` kliče
  `TekmaStoritev.vnesiRezultat` (iste preverbe kot ročni vnos). Branje je predlog in
  njegova sled (`branje_listka`).

## Listek (vmesnik ↔ zaledje)

- Trije polni črni kvadrati 4 × 4 mm (zgoraj levo, spodaj levo, spodaj desno) in QR
  koda 13 mm zgoraj desno stojijo na **stalnih mestih** (`slog.css` razdelek »Sodniški
  listek«, `GeometrijaListka.java`). **Ob spremembi listka spremeni oboje.** Oznaki sta
  vektorski (`<svg>`), ker rob ali ozadje brskalnik zaokroži oz. ne natisne.
- QR nosi `TK:<id tekme>:<skrivnost>` (verzija 1, ECC M; S1 od 22. 11. 2026), pod njo
  je `T-<id>` (`pomozno/kodaListka.ts`, `KodaListka.java`). Listki, natisnjeni prej,
  nosijo samo `TK:<id>`: bralnik jih še prebere. Prazen listek kode nima — tekmo
  izbere organizator s seznama tekem, ki čakajo na izid.
- Celica za točke je **prosta** (lastnik je zavrnil okenca za števke): razrez na eno ali
  dve števki je del branja.
- **Okence za mizo** (S1) stoji na stalnem mestu glave: zunanji robovi x 62–80 mm,
  y 5–12 mm, črta 0,45 mm (`GeometrijaListka.MIZA`, `.listek__miza-okence`), levo od
  njega oznaka »Miza«. Znana miza je ob tisku natisnjena v okencu.

## Skrivnost listka in miza (S1, 22. 11. 2026, V74)

- **Skrivnost** (`SkrivnostListka`) je prvih 40 bitov HMAC-SHA256(ključ, `TK:<id>`) v
  base32 (A–Z, 2–7), 8 znakov. Zapis ostane v QR verzije 1 do id-ja z 8 števkami
  (`potQr` vsili verzijo 1 in pri daljšem zapisu vrže napako). Skrivnost sodniku (S2)
  da pravico poslati izid te tekme: kdor ima listek, sme poslati izid samo te tekme.
- **Ključ** (32 bajtov, `SecureRandom`) ustvari aplikacija v tabeli `kljuc_aplikacije`
  (V74), v **lastni transakciji** (REQUIRES_NEW), naloži pa ga že ob zagonu
  (`ApplicationReadyEvent`): prva uporaba sredi tiska kod bi potrebovala drugo
  povezavo iz bazena. V migraciji in repozitoriju ga ni; v dnevnik gre samo opozorilo,
  da je nastal nov ključ. Ključ je del baze in njenih kopij — **obnova baze brez te
  tabele naredi nov ključ, listki, natisnjeni prej, pa za sodnika ne veljajo več**
  (pult jih še prebere). Testno okolje ga dobi s kopijo baze.
- **Kode dobi samo organizator** turnirja in admin: `GET /dogodki/{id}/listki/kode`
  (preverba lastništva, varnostna veriga `authenticated`, `Cache-Control: no-store`).
  Javni `TekmaDto` skrivnosti nima; `KodaListka.NajdenaKoda.toString` je ne izpiše.
  Gost lahko listke natisne, a z zasilno kodo brez skrivnosti (pult jo prebere,
  sodniška pot ne). Stran za tisk kode uporabi samo, dokler uporabnik sme urejati, in
  odjava jih odstrani iz predpomnilnika (sicer bi jih gost v istem zavihku natisnil).
  Če kod ni bilo mogoče naložiti ali kateri izbrani tekmi manjka, stran to pove in tisk
  dovoli; ključ poizvedbe so id-ji pripravljenih tekem.
- **Branje mize** (`MizaListka`, `LokalniBralnikListka.miza`): okence se poišče po
  robovih kot okenca vadnice; ista mreža prebere 1–99. Okence velja le, če so najdeni
  vsaj trije robovi (listek pred S1 ima na istem mestu samo črto za vpis → mize ni).
  Ker za mizo ni pravil, ki bi branje popravila, je **negotova**, kadar:
  - je zaupanje pod 0,8 (ničla ni kandidat, a šteje v vsoto);
  - je zapis prečrtan in prepisan;
  - okence ni »zanesljivo«: manjka rob, mere se ne ujemajo s natisnjenim ali čez
    notranjost gre ravna črta od roba do roba. Simulacija je pokazala, da senca
    pregiba čez okence mreža prebere kot »1« (prazno okence → tiha miza 1).
- Miza gre v `PrebranListek` (`miza`, `negotovaMiza`; branja pred S1 jih nimajo) in v
  `BranjeListkaDto.prebranaMiza`; izrez okenca je v `CeliceListkaDto.miza`. Stran
  Branje listkov pokaže »Miza 7 (z listka)«, negotovo rjasto z izrezom. **Izida ne
  spremeni.** Uporabi jo klicanje tekem (M1, `docs/pravila/mize.md`):
  - miza, ki se razlikuje od klica, je opozorilo (»· poklicana na mizo 5«);
  - nepoklicana tekma ob potrditvi dobi sodnikovo ali zanesljivo prebrano mizo, a
    samo, če na tej mizi ne igra druga tekma.
- **Simulacija** (`SimuliraneFotografijeTest`) od S1 riše na posnetek listka z okencem
  (`/dev/listek`, brezglavi Chrome, deviceScaleFactor 3) in v okence piše mizo z ločenim
  virom naključnosti (zaporedje celic ostane primerljivo). Izmerjeno 22. 11. 2026:
  miza prav 100 % (običajne, zahtevne), 93 % (neugodne), 0 tihih napak na vseh ravneh;
  točke nespremenjene v okviru ene celice. Listek pred S1 (`listek-5-nizov-pred-s1.png`)
  se prebere kot prej in mize nima.

## Postopek branja (`LokalniBralnikListka`)

1. `KodaListka.najdi` (ZXing) prebere QR in središča iskalnih vzorcev.
2. `PoravnavaListka`: oznake so polni kvadrati (izotropna, kompaktna komponenta); trojica
   v obliki L se izbere po geometriji (pravi kot, razmerje 85 : 87, predznak vektorskega
   produkta), zato je vseeno, kako je listek obrnjen. S QR je preslikava homografija
   (popravi perspektivo), brez nje afina. Izhod: listek pri 10 px/mm.
   **Trojica, ki ne ustreza QR** (napoved iskalnega vzorca zgoraj desno dlje od
   `najvecOdmikQr`), se zavrže (F2.6): ko prst zakrije oznako, je »tretja oznaka«
   nekaj drugega in poravnava bi bila napačna. Takrat **rezervna poravnava**: trije
   iskalni vzorci QR so točke na znanih mestih, afina napoved iz njih pokaže, kje
   iskati oznake (krog 25 % razdalje od QR), z vsaj dvema oznakama je točk pet in
   homografija po najmanjših kvadratih se mora z vsemi ujemati na 1 mm.
3. `CeliceListka`: celice se poiščejo po črtah (navpična lega se spreminja s številom
   nizov); pas brez navpičnih robov (okvirčka pod imeni) ni celica. Zadnja celica je
   Rezultat. Ob neuspehu še enkrat z blažjim pragom. **Črte in celice se iščejo v
   vsakem stolpcu posebej** (toleranca ±2 px) in se nato povežejo v vrstice po
   bližini središča (≤ 1,2 mm), ker sta stolpca prepognjenega listka lahko zamaknjena
   ali nagnjena drug proti drugemu (F2.6).
4. `RazrezCelice`: črnilo → komponente → skupine po stolpcih → razlage (ena ali dve
   števki, z verjetnostjo); števka v obliki MNIST (20 × 20, težišče v sredini 28 × 28).
   **Popravljena celica** (F2.5: delež črnila > 0,12, več kot tri skupine potez ali
   zapis čez več kot 35 % širine celice; navaden zapis sega do ~28 %) je vedno
   negotova, listek dobi opombo »Popravljena celica (2. niz). Preveri jo.«, njene
   števke ne gredo v učenje. Celica z več kot 40 deli se ne obdeluje naprej
   (varnost) in je popravljena. Vadnica tega pravila nima (ozka okenca, slabe
   števke izloči admin).
5. `MrezaStevk` (LeNet, ~80.000 uteži, čista Java) da verjetnost števk (temperatura 1,5).
   **Petkratno branje** (F2.2, `VecBranj`): izvirnik, premik ±1 px, zasuk ±4°;
   verjetnosti se povprečijo.
6. `RazlagaListka`: Viterbi po vrsticah izbere najverjetnejši **veljaven** izid
   (veljaven niz, konec tekme, prazne vrstice po odločitvi, celica Rezultat);
   naprej-nazaj da zaupanje celice. Celica je negotova pod 0,8 ali kadar so pravila
   preglasila mrežo. Imen in zapisov »b.b.« ne bere: posebni izidi so nepopolna tekma.
   **Pogostost izidov iz zgodovine** (F2.1, `PogostostNizov`): vsak veljaven niz je
   utežen s (P(niz) / povprečni P(11:a))^β, β = `turnirko.branje-listkov.beta`
   (privzeto 0,5; 0 = brez zgodovine). Celica je negotova tudi, kadar bi brez
   zgodovine izbrali drugo vrednost (odločila je zgodovina, ne pisava). Opomba
   »slabo se ujemajo s pravili« se meri brez zgodovine. Pogostost prešteje
   `PogostostNizovStoritev` ob zagonu in vsako noč (ena poizvedba GROUP BY nad
   `niz` in `tekma`: samo `IGRANO` + `KONCANA`, turnir z znanim datumom, veljavni
   nizi, brez osebnih podatkov); pod 2000 nizi velja vgrajena porazdelitev (kopija
   produkcije 20. 11. 2026: 200.287 nizov; 11:7, 11:8, 11:9 po 13,6 %, 11:0 0,6 %,
   12:10 6,2 %). Glajenje: 100 navideznih nizov vgrajene oblike, zato redek izid
   nikoli ni nemogoč. β zunaj 0–1 se ob zagonu zamenja s privzeto (opozorilo v dnevniku).
   Meritev zlatega nabora uporablja **vgrajeno** pogostost (meritvi dveh mrež na različna
   dneva morata biti primerljivi).
7. `PredlogIzidaListka`: število prebranih vrstic nizov se mora ujemati s številom nizov
   tekme (toliko jih listek natisne); sicer opozorilo — bralnik vrstice ni našel in je
   npr. zadnji niz vzel za Rezultat (prst čez celico Rezultata).

**Meritev bralnika:** vsaka sprememba se meri s `SimulacijaBralnikaPoskus` (tri
ravni simulacije + vsaka težava tretje ravni posebej, `-Dzgodovina=true` za tekme
po pogostosti iz zgodovine). Sprememba, ki poslabša katerokoli merilo, se ne
sprejme. Tretja raven (`NEUGODNO`, F2.7): prepognjen listek, prst čez eno oznako,
prečrtane in prepisane celice, enica z nosom in prečrtana sedmica.

## Učenje

- Začetna mreža `resources/branje/stevke.bin`: `UcenjeStevk.main` na MNIST (6 epoh,
  popačenje), 99,24 % na preizkusnih MNIST. Ob njej `mnist-izbor.bin.gz` (3000, mešanje
  pri dotreniranju) in `mnist-preizkus.bin.gz` (1000, preverba). MNIST: CC BY-SA 3.0.
- Ob potrditvi `UcenjeIzListkov.poPotrditvi` (po commitu) znova razreže fotografijo in
  shrani števke s potrjeno vrednostjo (`stevka_listka`, 28 × 28, brez imen). Celica, ki
  je razrez ne razdeli na pravo število števk, se preskoči. Pri listku brez kode se smer
  stolpcev izbere po ujemanju z mrežo. Listek se zapiše samo enkrat (`existsByIdBranje`).
- **Celica in močna oznaka (F6.2, V72):** vsaka števka listka nosi celico listka
  (`celica` = niz × 2 + stolpec, rezultat 100 + stolpec). Celica, v kateri se potrjena
  vrednost razlikuje od predloga bralnika (tudi neprebrana), je `popravljena = true`
  (`PopravljeneCelice.izPredloga`, v straneh tekme) in pri učenju šteje dvojno. Brez
  predloga (branje ni uspelo) ni močnih oznak.
- **Popravek izida (F6.1):** `TekmaStoritev.popraviRezultat` objavi `IzidTekmePopravljen`;
  `UcenjeIzListkov.obPopravkuIzida` (po commitu, v izvajalcu branja) za vsak potrjen
  listek tekme primerja potrjeni izid z novim po celicah. Do 2 spremenjeni celici:
  števke se zapišejo znova z novim izidom (spremenjene celice in prejšnje močne so
  močne), resnica v zlatem naboru se popravi. Več kot 2, drugo število nizov ali poseben
  izid: listek je bil verjetno potrjen k napačni tekmi - števke in vnos v zlatem naboru
  se samo izbrišejo. Brez fotografije (30 dni) se ob spremembi števke izbrišejo, resnica
  nabora se popravi. Zapis ob potrditvi in ob popravku tečeta pod istim zaklepom
  (`zapisovanje`); če je popravek prehitel potrditev, potrditev ne zapiše ničesar.
- **Učenje (`UcenjeMreze`, F6):** nočno (`turnirko.branje-listkov.ucenje-urnik`, 3.50) ali
  z »Uči zdaj« (admin, stran Meritev branja), ob vsaj 200 novih števkah od zadnjega
  učenja (tudi ročno: ponavljanje na istih števkah bi novo mrežo prej ali slej po
  naključju »izboljšalo« na istem preizkusnem delu). Kopija se dotrenira (števke ×4,
  močne še ×4, + izbor MNIST). Preizkusne so vse števke vsakega petega **listka ali lista
  vadnice** (`jePreizkusna`), ne vsaka peta števka: ista pisava ne sme biti v obeh
  delih. Pod 50 preizkusnimi števkami se učenje odloži brez zapisa.
- **Sprejem (F6.3):** nova različica (`mreza_stevk`) se sprejme samo, če (1) na
  preizkusnih števkah ne bere slabše, (2) na zlatem naboru (prvih 400 listkov, če jih je
  vsaj 20) nima več napačnih celic in (3) na MNIST ne nazaduje za več kot 1 odstotno
  točko od mreže v uporabi **in** za več kot 2 od vgrajene (`PADEC_MNIST_SKUPAJ`, od
  21. 11. 2026: meja samo od prejšnje bi dovolila zdrs za točko ob vsakem učenju; prva
  različica v produkciji je padla 99,5 % → 98,6 %). Razlog zavrnitve se zapiše (`razlog`),
  z vsemi meritvami, načinom in trajanjem.
- **Meje (F6.5):** hkrati teče največ eno učenje (zastavica; vrnitev prejšnje vzame isto),
  »Uči zdaj« ima premor 10 min. Učenje (2 niti) se po 30 min prekine: zapis brez uteži
  (`razlog` »Prekinjeno …«), mreža ostane stara. Ob ustavitvi strežnika se nič ne zapiše.
  Zaklepa v bazi ni: aplikacija teče v enem vsebniku, zaklep v bazi bi do 30 min držal
  povezavo.
- **»Vrni prejšnjo« (F6.4):** različica v uporabi dobi `umaknjena_ob`; `VirMrezeStevk`
  naloži zadnjo sprejeto neumaknjeno (ali vgrajeno). Umaknjena ostane v zgodovini in
  se ne vrne sama. Med učenjem ni mogoča (409).
- **Napake mreže (`NapakeMreze`, F6.6, 21. 11. 2026):** na strani Meritev branja (admin)
  na klik, ne ob nalaganju: vse števke iz učenja (`najnovejse`, ista razdelitev
  `jePreizkusna`) gredo skozi mrežo v uporabi, preizkusne še skozi vgrajeno. Pokaže za
  vsako števko 0–9 delež prav in najpogostejše zamenjave, narobe prebrane preizkusne
  (s sliko, največ 60 za vsako napisano števko) in **sumljive oznake**: učne števke, ki
  jih mreža kljub učenju bere drugače z verjetnostjo ≥ 0,9 (napisana druga števka ali
  narobe izrezano okence). »Izloči« (`DELETE /mreze/stevke/{id}`) števko izbriše iz
  učenja in zmanjša `vadnica.stevk`; že naučena mreža ostane. Izločati se sme samo
  napačne oznake: grdo napisana prava preizkusna števka mora ostati, sicer je preizkus
  videti boljši, kot je.

## Sprejem fotografije in dvojniki (F0, 20. 11. 2026)

- `sprejmi` teče v treh korakih: (1) dostop in omejitev hitrosti v kratki transakciji,
  (2) dekodiranje **zunaj transakcije**, (3) zapis v kratki transakciji. Dekodiranje
  torej ne drži povezave z bazo in ga ne more sprožiti nihče brez pravice (test
  `dostopSePreveriPredDekodiranjem`).
- Meja prejete fotografije je 6000 px in 20 Mpx (`NAJVEC_STRANICA_VHODA`,
  `NAJVEC_PIKSLOV_VHODA`; splošna meja slik je 40 Mpx). Dovoli nepomanjšano 16-Mpx
  fotografijo telefona; 20 Mpx zasede ob dekodiranju ~140 MB.
- Največ 2 hkratni dekodiranji (pravični semafor), en uporabnik pa največ eno
  (pas 64 semaforjev po id uporabnika): organizator z več hkratnimi zahtevami tako
  ne zasede obeh. Naslednja zahteva čaka skupaj do 10 s, nato dobi **503 z
  `Retry-After: 5`** (`ZasedenoIzjema`) in ne shrani ničesar. Vrsta brez signala in
  postaja odgovor 503 ponovita, 4xx pa ne.
- Izvajalec branja (`BranjeListkovNastavitve`): 2 niti, vrsta 10.000 nalog (samo id-ji),
  polna vrsta nalogo **zavrne** in zapiše v dnevnik. Nikoli `CallerRunsPolicy`: naloga
  se odda iz `afterCommit`, kjer so viri potrjene transakcije še vezani na nit, in
  zapis branja bi tiho izginil. Iz `afterCommit` se v bazo ne piše.
- **Dvojniki** (isti listek fotografiran večkrat): ko branje s tekmo po QR kodi
  konča, med odprtimi branji te tekme ostane eno, najnovejše uspešno prebrano, sicer
  najnovejše. Ostala dobijo stanje ZAVRZENO z razlogom »Nadomeščeno z novejšo
  fotografijo istega listka.«. Potrditev zapre še ostale odprte fotografije iste
  tekme. Upoštevajo se samo branja, pri katerih je tekmo določila **koda**. Če ostane
  starejša (novejša ni bila prebrana), ima novejša razlog »…velja starejša fotografija«.
- Napaka pri zapisu predloga konča v NAPAKA (nikoli ne obvisi v V_VRSTI). Branje, ki je
  V_VRSTI dlje kot 2 minuti (npr. zavrnjeno ob polni vrsti), ima v DTO `obviselo` in
  gumb »Preberi znova«. Čas računa strežnik (aplikacija nima nastavljenega `TZ`).
- Zaključek branja, potrditev, zavrženje in ponovno branje tečejo pod istim zaklepom
  v pomnilniku (`zakljucevanje`), vsak v svoji transakciji, ki se potrdi pred
  sprostitvijo zaklepa. Tako se hkrati zaključeni fotografiji vidita in potrditev ter
  zaključek ne prepišeta drug drugemu stanja (`branje_listka` nima stolpca verzije).
  Zaklep se vzame pred transakcijo (čakajoča nit ne drži povezave z bazo).
  Aplikacija teče v enem primerku; ob več primerkih bi potrebovali zaklep v bazi.

## Sodnik slika listek (S2, 22. 11. 2026, V75)

- **Stikalo turnirja** `turnir.sodniki_listki` (organizator na strani Branje listkov,
  `PUT /turnirji/{id}/sodniki-listki`, preverba kot za branje): `null` = izklopljeno,
  `PREDPREGLED` = sodnikova potrditev počaka organizatorja (en dotik), `VELJA_TAKOJ` =
  izid gre v tekmovanje ob sodnikovi potrditvi. Prvi pravi turnir teče v predpregledu.
- **Stran `/s`** (`SodnikStran.tsx`, zunaj postavitve, brez prijave): »Slikaj listek« →
  pošiljanje z istim ključem do odgovora (časovna meja 45 s, po ~20 s »Ni signala,
  listek oddaj na pultu«; vrsta je samo v pomnilniku strani) → branje (sprašuje vsakih
  1,5 s) → potrditev → »Izid je vpisan« ali »čaka na potrditev organizatorja«. Zaslon ne
  ugasne.
- **Pot do `/s` je hrbtna stran listka** (lastnik 22. 11. 2026, namesto plakata za ob
  mize): ko je stikalo vklopljeno, `GET /dogodki/{id}/listki/kode` vrne tudi
  `sodnikiListki` in `ListkiStran` za vsakim listom A4 izriše njegov hrbet
  (`HrbetListka`: QR do `<origin>/s`, trije koraki; nič skrivnega). Tisk je
  obojestranski; **koda je na vseh šestih mestih hrbta, tudi za nepolnim listom**
  (lastnik 23. 11. 2026). Prej je dobilo kodo samo mesto, za katerim je listek pri
  obratu po dolgem ali kratkem robu, a pri pravem tisku z enim listkom je koda
  pristala za praznim mestom in listek je ostal brez hrbta: kako tiskalnik list
  obrne, ni znano vnaprej. Kode za praznimi mesti gredo z odrezki.
  Vsebina hrbta je znotraj roba 20 mm zgoraj/spodaj in 15 mm levo/desno: v obeh
  načinih prosojno sije samo za tabelo točk, ne za oznake, QR ali okence mize.
  Da prosojnost tabele ne moti, meri `SimuliraneFotografijeTest.hrbetNeMotiBranja`.
  Organizator hrbet pred tiskom lahko odkljuka (tiskalnik brez obojestranskega
  tiska); prazni listki ga nimajo (tekme ne poznajo).
- **Edine poti, kjer gost sme pisati** (PRODUCT.md): `POST /api/v1/sodnik/listki`,
  `.../branja/{id}/stanje`, `.../branja/{id}/potrdi`; vse ostalo pod `/sodnik/**` je
  `denyAll`. Žeton gre vedno v telesu, nikoli v naslovu (naslovi gredo v dnevnike).
- **Sprejem** (`SodniskaBranjaStoritev.sprejmi`): (1) fotografija največ 4 MB
  (kontroler, pred kopiranjem v pomnilnik); omejitev po naslovu IP (120 / 10 min,
  šteje tudi zavrnjene poskuse – prav ti so ugibanje skrivnosti; IPv6 po omrežju /64)
  in skupna (1000 / 10 min, šteje SAMO listke s pravo skrivnostjo, da je smeti z več
  naslovov ne izčrpajo); (2) dekodiranje pod skupno mejo z organizatorji; (3) QR z
  **veljavno skrivnostjo** –
  šele nato branje sploh nastane (stari listek, napačna skrivnost, brez kode: isto
  sporočilo); (4) stikalo vklopljeno, turnir ni uvožen, dogodek je v teku, tekma ni
  ekipna, ni KONCANA ali CAKA; največ 10 sodniških fotografij na tekmo. Branje nastane brez uporabnika
  (`poslal_sodnik`), s tekmo po kodi in z **enkratnim žetonom** (32 bajtov, v bazi
  SHA-256, velja 30 min). Branje teče v ozadju kot pri organizatorju.
- **Idempotenca**: ključ pošiljanja velja za tekmo (`uq_branje_listka_kljuc_sodnika`);
  ponovitev vrne isto branje z NOVIM žetonom (starega strežnik ne pozna).
- **Žeton**: napačen, tuj, potekel ali po potrditvi porabljen žeton → vedno isti 404
  (ne izda, ali branje obstaja). Neuspeli poskusi so omejeni po naslovu (300 / 10 min);
  potrditev preveri žeton PRED zaklepom zaključevanja (poplava smeti ne čaka na istem
  zaklepu kot organizatorji). Ponovitev s ključem po sodnikovi potrditvi novega žetona
  ne da.
- **Potrditev** pod zaklepom zaključevanja (kot organizatorjeva): samo PREBRANO, samo
  odigrana tekma s točkami 0–99 (predajo, brez boja in diskvalifikacijo vpiše
  organizator). **Skrivnost dokazuje le, da je nekdo listek videl** (QR je na mizi,
  fotografira ga lahko kdorkoli), ne da je izid pravi. Zato pri `VELJA_TAKOJ` velja izid
  takoj samo, če ga podpira fotografija (`smeTakoj`): bralnik je sam prebral ODLOČENO
  tekmo (zmagovalec ima dovolj nizov; prazen listek da 0:0), sodnik ne zamenja zmagovalca
  in spremeni kvečjemu negotove ali neprebrane celice. Sicer gre izid v predpregled k
  organizatorju (sodnik to izve). Takojšnji vpis:
  `TekmaStoritev.vnesiRezultatSodnika` (iste preverbe in posledice kot
  ročni vnos, **brez preverbe lastništva** – pravico je dala skrivnost; metoda je vidna
  samo paketu `storitve`), način `SODNIK`, ostala branja tekme se zaprejo, učenje z
  navadno težo samo iz takojšnjih vpisov (močne oznake F6.2 so samo organizatorjevi
  popravki; ostalo se uči ob organizatorjevi potrditvi). `PREDPREGLED`:
  izid se preveri (`preveriIgrano`), zapiše se samo `sodnikov_izid`.
- **»Preberi znova«** na sodniškem branju ohrani tekmo (sicer bi sodnikov žeton ostal
  brez nje); branje brez tekme (nov žreb) sodniku vrne »Te tekme ni več«.
- **Organizator** vidi »Poslal sodnik« / »Sodnik je izid potrdil – čaka tebe«, mizo
  sodnika in med zaključenimi »potrdil sodnik« / »sodnik popravil«. Pri predpregledu je
  predlog sodnikov izid (`predlogSodnika`): pripravljen za en dotik, razen če je sodnik
  spremenil prebrano (opozorilo `SODNIK_SPREMENIL`) ali ima tekma izid že vpisan.
- **Sodnikov zaslon**: zmagovalec z velikimi črkami (»Zmagal je … 3:1«), točke kot na
  listku, negotove in neprebrane celice ter negotova miza **močno obarvane** (debel
  rjast rob, rjasto ozadje), nad tabelo opozorilo »Pozor: N negotovih številk. Obarvane
  primerjaj z listkom in napačno popravi.«. **Vsake posebej sodnik ne potrjuje več**
  (lastnik 24. 11. 2026: dotik vsake celice je bil zamuden; prej je bil gumb onemogočen,
  dokler se ni dotaknil vseh). Gumb se pri negotovih glasi »Obarvane sem preveril ·
  Potrdi izid«: sodnik s tem zagotovi, da je pogledal. Popravljena celica ni več
  obarvana. Varovalka ostane na strežniku (`smeTakoj`): sprememba zanesljive celice ali
  drug zmagovalec gre v predpregled. Miza: prebrana ali poklicana; razlika med prebrano
  in poklicano je opozorilo.

## Vrsta brez signala (F5, 22. 11. 2026, V73)

- **Vrsta na telefonu** (`pomozno/vrstaFotografij.ts`, stran Branje listkov): fotografija
  se pomanjša, shrani v IndexedDB (`turnirko-vrsta`) in šele nato pošilja, ena za drugo,
  najstarejša najprej (novejša fotografija istega listka mora priti zadnja, sicer bi
  pravilo dvojnikov obdržalo starejšo). Napaka omrežja, časovna meja 45 s, 5xx, 408 in
  429: ponovi po 2, 4, 8 … največ 60 s (503/429 vsaj 5 s), takoj ob dogodku `online` in
  ob vrnitvi na stran. 401 in 403 (dostop je lahko le začasno izklopljen): vrsta se
  ustavi, fotografije počakajo na novo odprtje strani oziroma prijavo.
  Ostali 4xx: ne ponavlja, fotografija se izbriše, razlog ostane v vrsti do »Opusti«.
  Pošilja se samo na strani branja turnirja, kjer je bila posneta, in samo za
  uporabnika, ki jo je posnel; ob odprtju strani se nadaljuje (stran zaprta, brskalnik
  ugasnjen). Brez IndexedDB (ali ko zapis ne uspe) vrsta živi v pomnilniku strani in
  vrstica to pove (»ne zapri strani«). Vrstni red je čas posnetka, ne konec priprave.
- **Zasebnost (V4):** fotografija nosi imena, zato se iz brskalnika izbriše po uspehu,
  najkasneje po 24 urah (`VELJA_MS`, ob vsakem zagonu aplikacije za vse uporabnike) in ob odjavi
  (`AvtentikacijaKontekst.odjava`). Neveljavna prijava (401) ni odjava: vrsta ostane.
- **Idempotenca** (`Idempotency-Key`, `branje_listka.kljuc_posiljanja`, V73): vsaka
  fotografija ima naključni ključ (`crypto.randomUUID`, strežnik sprejme
  `[A-Za-z0-9-]{8,64}`, sicer 400). Če je uporabnik s tem ključem že poslal, `sprejmi`
  vrne obstoječe branje: brez dekodiranja, brez nove vrstice, ne šteje v omejitev
  hitrosti. Ključ velja za uporabnika (edinstveni indeks `id_nalozil, kljuc_posiljanja`,
  tuj uporabnik ga ne more zasesti), ključ za drug turnir je 400. Dve hkratni zahtevi z
  istim ključem: druga po dekodiranju najde zapisano ali jo zavrne indeks, `sprejmi` to
  ujame in vrne prvo. Nočno opravilo (`OpravilaBranjaListkov`) ključe, starejše od
  24 ur, pobriše. Brez glave je sprejem kot prej (postaja, starejši vmesnik).
- **Zaslon ne ugasne** (`pomozno/budenZaslon.ts`, Wake Lock), dokler je stran branja
  odprta in branje omogočeno; ob vrnitvi na stran se zahteva znova.
- **Kakovost JPEG ostane 0,88** (F5.3, simulacija 3 × 30 listkov): 0,82 da 29 % manjšo
  datoteko, a listek manj brez popravka (20 namesto 21) in celico manj na neugodnih;
  0,75 46 % manjšo in 19 brez popravka. Velikost z vrsto ni več kritična
  (`-Dsimulacija.kakovost` v `SimuliraneFotografijeTest`).
- **Preverba pred pošiljanjem (5.1) ni narejena:** pragov ostrine in svetlosti ni mogoče
  umeriti brez pravih fotografij (generalka), koda QR pa ni merilo (prazen listek je
  nima). Lažen alarm bi stal dotik pri vsakem listku.

## Meritev (F1, 20. 11. 2026, V69)

- **Sled pregleda** (`branje_listka`): ob potrditvi se zapišejo `pripravljen` (ali bi
  bil predlog ponujen za en dotik: `predlog(..., zKontrolo=false)` z opozorili tekme,
  brez kontrole, izračunan PRED vpisom izida; pri tekmi, izbrani ročno, predlog
  kandidata), `cas_pregleda_ms` in `nacin_potrditve`
  (`EN_DOTIK` = gumb v vrsti, `OBRAZEC` = okno). Čas meri vmesnik
  (`casPregleda` v `pomozno/branjeListkov.ts`): od poznejšega od prvega prikaza listka
  in zadnje potrditve ali zavrženja na strani, zato je pri listkih drug za drugim to
  čas na listek; »Preberi znova« čas prikaza ponastavi. Strežnik sprejme
  0–3.600.000 ms, drugače shrani `null` in potrditve ne zavrne. Čas šteje tudi pri
  neuspelem branju, vpisanem ročno.
- **Naključna kontrola** (`kontrola`): v `obdelaj` dobi predlog, ki bi bil ponujen za
  en dotik (`predlog(...)` z opozorili tekme), z verjetnostjo
  `turnirko.branje-listkov.kontrola` (privzeto 0,05; v testih 0) oznako kontrole.
  Seznam ga pokaže z opozorilom `KONTROLA` in brez »Potrdi«; organizator ga pregleda
  v celoti. Kontrola ostane tudi po »Preberi znova« (sicer bi se ji organizator
  izognil). Najprej žreb, šele nato predlog (poizvedbe tečejo pod zaklepom). Ista
  populacija kot `pripravljen`, zato je ocena tihih napak iz kontrol primerljiva.
  Nastavitev izven 0–1 obvelja kot najbližja meja, NaN kot 0.
- **Celice** (`MeritevBranja`, čist izračun): prav/narobe × zanesljiva/za pregled. Za
  pregled je negotova celica in obe celici niza z opozorilom (neprebrana celica,
  neveljaven niz); prazne vrstice za zadnjim nizom se ne štejejo (predlog jih
  izpusti). Manjkajoča ali odvečna vrstica je tiha napaka celice. Rezultat se meri samo,
  če je prebran, ali če tock po nizih ni (tedaj odloča). Listek s kodo se primerja v
  natisnjeni smeri, ostali v smeri z več pravilnimi celicami (pri slabem branju
  to napake rahlo podceni; listki brez kode so redki). Posebni izidi se ne merijo.
- **Tiha napaka listka** = `pripravljen` in vsaj ena napačna celica. Pri potrditvah z
  enim dotikom je ne vidimo; nepristranska ocena je iz kontrol (`kontrolZNapako` /
  `kontrol`). Stran meritev pokaže »premalo podatkov«, dokler ni 200 kontrol ali
  opažene napake.
- **Statistika** (`GET /api/v1/branje-listkov/meritev?od=&do=&idTurnir=`, samo admin,
  pravilo v `VarnostneNastavitve` PRED splošnim pravilom branja): obdobje po datumu
  nastanka (ura strežnika), oba dneva vključno, privzeto zadnjih 365 dni, leta
  2020–2100 (sicer 400); največ
  20.000 branj (projekcija `VrsticaMeritveBranja`, brez entitet), sicer 400. Skupaj,
  po turnirjih in po mrežah (neuspela branja nimajo mreže). Čas: mediana in p90 po
  metodi najbližjega ranga. Stran `/branje-listkov/meritev` (povezava na strani branja
  samo za admina).
- **Zlati nabor** (`zlati_listek`, `ZlatiNabor`): po potrditvi (izvajalec učenja,
  `UcenjeIzListkov.zabelezi`) poravnan listek PNG v sivinah, ohranjene **samo celice s
  točkami**, in to samo notranjost, ki jo bere razrez (brez `RazrezCelice.ODMIK` ob
  črti: ime, napisano čez črto, ne pride v nabor); v celici se odšteje raven papirja, ki jo vzame razrez
  (`RazrezCelice.papir`), in kar je pod 0,06, je belo. Maska razreza se zato ne
  spremeni razen zaokrožitve na 1/255 tik ob pragu (test
  `bralnikNadNaboromBereEnakoKotNadFotografijo`), slika ima ~15 kB.
  Zraven lega celic, potrjen izid v stolpcih listka, število nizov in mreža. Samo
  izid IGRANO s točkami po nizih, listek s kodo (smer, izbrana po ujemanju z mrežo,
  bi resnico prilagodila bralniku) in največ 9 vrstic; največ 5000, vzorec po naključnem
  ključu (ostanejo najmanjši; enakomeren vzorec brez števca). Napaka nabora ne
  izgubi števk (te so zapisane prej). `id_branje` je samo v bazi (UNIQUE, SET NULL),
  izvoz ga ne vsebuje. Zlati listek ostane tudi po izbrisu branja ali turnirja; ob
  poznejšem popravku izida se resnica (še) ne popravi - to naredi F6.1. Kar je pred
  celicami (poravnava, črte), se na naboru ne da meriti.
- **Meritev nabora** (`MeritevNabora`): trenutni bralnik od celic naprej
  (`LokalniBralnikListka.beri`) nad vsakim listkom, ista merila. Admin jo sproži
  (`POST /api/v1/branje-listkov/nabor/meritev`), teče v svoji niti z nizko prednostjo,
  vedno ena (409, če že teče); rezultat je v pomnilniku do ponovnega zagona (napaka,
  tudi `Error`, konča v stanju z razlogom, ne »merim«). »Brez napake« v naboru je
  listek brez napačne celice, tudi Rezultata (strožje od »brez popravka«). `izmeri`
  je sinhrona za F2/F6. Zip (`GET /api/v1/branje-listkov/nabor/zip`) se piše
  neposredno v odgovor (ne asinhrono), pravica se preveri pred prvim bajtom; zadnja
  datoteka `konec.txt` s številom listkov pove, da prenos ni bil prekinjen.

## Pregled in potrditev (F4, 21. 11. 2026, V71)

- Ob branju se shrani **poravnan listek** (sivine, 10 px/mm, JPEG ~50–100 kB) v
  `branje_listka_slika.poravnana` in **lega celic** v `branje_listka.celice`
  (JSON `CeliceListkaDto`, samo številke). Ponovno branje oboje zamenja; brez
  poravnave ni izrezov. Poravnana slika gre z isto vrstico kot fotografija, zato jo
  izbriše isto nočno brisanje po 30 dneh; DTO izrezov ne vrne, ko je fotografija
  izbrisana.
- `GET /api/v1/branja-listkov/{id}/poravnana` (isti dostop kot fotografija) in
  `GET /api/v1/tekme/{id}/listek` (zadnje potrjeno branje tekme s fotografijo;
  lastnik turnirja tekme ali admin; v varnostni verigi `authenticated`, čeprav je
  GET pod `/tekme`). Glave `image/jpeg`, `Cache-Control: no-store, private`.
- Vmesnik: v oknu pregleda je pod vsako vrstico točk vrstica izrezov iz ene slike
  (CSS ozadje v odstotkih, ena zahteva); negotova celica je čez vso širino z rjastim
  robom. `BranjeListkaDto.obrnjeno` pove, kateri stolpec listka je udeleženec 1.
- Vrsta: zanesljivi listki (`jeZanesljiv`: prebrano, tekma, predlog pripravljen) na
  vrhu, nato ostali, v skupini po času; koda `T-…` v vsaki vrstici. **Potrdi vse
  zanesljive** (od dveh naprej) zaporedno kliče potrditev; ob prvi napaki se ustavi.
  Čas pregleda se razdeli na potrjene listke. Kontrola ni nikoli pripravljena, zato
  je nikoli ne potrdi skupaj.
- Vrsta se osvežuje vsake 3 s (med branjem 1,5 s), samo dokler je stran vidna.
- Okno s točkami po nizih ima za organizatorja turnirja in admina gumb **Listek**
  (kadar je branje listkov vklopljeno); brez fotografije pokaže »Za to tekmo ni
  fotografije listka«.

## Vadnica števk (F3, 20. 11. 2026, V70)

Hladen začetek učenja: označene števke slovenske pisave, preden pride dovolj pravih
listkov. Vse je **samo za admina** (`/api/v1/vadnice/**` v `VarnostneNastavitve`,
storitev preveri še enkrat; strani `/vadnica`, `/vadnica/tisk`, `/vadnica/:id`).

- **List** (A4 pokončno, `ListVadnice.tsx` ↔ `GeometrijaVadnice`): oznake 6 × 6 mm
  (središča 15/15, 15/282, 195/282 mm), QR 22 mm na (173, 9) z vsebino
  `TKV:<različica>:<id lista>`, 14 vrstic (0–9, 10–13) po 10 okenc 14 × 10 mm (x 38 +
  16·j, y 46 + 16·i). Okenca so ena risba SVG v mm s črto 0,45 mm: obroba CSS bi se
  pri tisku zaokrožila na 0,26 mm, tanka črta pa se na fotografiji raztrga. Polja za
  ime ni. Ob spremembi lista spremeni oboje in povečaj `RAZLICICA` (vmesnik in
  zaledje); list stare različice bralnik zavrne. Natis: `@page listki` (A4, rob 0),
  preverjeno v PDF na 0,1 mm.
- **Id lista izbere brskalnik** ob tisku (`noviIdjiListov`: deset mest, kripto
  naključno; koda ostane različica 1 z 21 moduli, na katero računa postavitev). List z
  enega okolja (test) zato velja tudi na drugem (produkcija); strežnik zapis lista
  ustvari šele ob prvi fotografiji. »Nove kode« da nove id-je, že natisnjeni ostanejo
  veljavni. Listov se ne fotokopira (en list = en pisec).
- **Branje** (`BranjeVadnice`, `POST /api/v1/vadnice`): fotografija do 3200 px
  (vmesnik pomanjša; ~11 px/mm), dekodiranje in branje pod isto mejo hkratnih
  dekodiranj kot listki (`BranjeListkovStoritev.podDekodiranjem`). Koda QR je obvezna
  (brez nje lista ni mogoče pripisati piscu). Poravnava je ista kot pri listku z drugo
  `Postavitev` (`najvecOdmikQr` 40 mm namesto 15). Okenca (`CeliceVadnice`): vsak rob
  je črta, najbližja pričakovani legi v pasu ±12 px (sosednje okence je 20 px stran),
  pokritost ≥ 35 % srednjih treh petin z 1 px tolerance; črta, zlita s števko (širša
  od 10 px), se odreže le v debelini črte, sicer bi »1« ob levem robu izginila in iz
  »10« bi nastala razpolovljena ničla (napaka, ujeta v simulaciji). Vrnjena celica je
  notranjost minus `RazrezCelice.ODMIK`, zato razrez začne takoj za črto. Vrednost ni
  prebrana, ampak je vrstica; števke dobijo oznake po istem pravilu kot potrjen listek
  (`UcenjeIzListkov.stevkeCelice`): dvomestno število mora razrez dati kot dve števki,
  sicer se okence preskoči. Prazen list (0 števk) → 400. Listek tekme na strani vadnice
  in list vadnice na strani branja dobita razlago, kam sodita.
- **Naložene slike** (F3.6, 22. 11. 2026): poleg »Fotografiraj list« (`capture`, na
  telefonu odpre kamero) je »Naloži slike« (brez `capture`, galerija ali datoteke, več
  hkrati) za liste, ki jih fotografirajo in pošljejo drugi. Obe gresta po isti poti
  (vmesnik pomanjša na 3200 px JPEG, strežnik se ne spremeni); slike se pošiljajo ena
  za drugo, sporočilo naložene ima ime datoteke. Slika iz aplikacije za sporočila je
  dovolj: simulacija (`VadnicaTest.slikaIzAplikacijeZaSporocilaSePrebere`) bere pri
  1600 in 1280 px daljše stranice kot izvirnik, pri 1000 px še bere (mreža slabše),
  pri 800 px kode QR ne prebere. Zato vmesnik ob neuspelem branju (400) slike pod
  `MAJHNA_SLIKA` (1200 px) doda, naj pošiljatelj pošlje izvirnik. Datoteka, ki je
  brskalnik ne zna odpreti (HEIC v Chromu, PDF), dobi `NapakaFotografije` z navodilom
  za JPG/PNG namesto »Povezava s strežnikom ni uspela« (velja tudi za stran branja
  listkov).
- **Hramba**: tabela `vadnica` (id iz QR, PNG poravnanega lista s samimi okenci kot pri
  zlatem naboru, ~0,4 MB, števci) in števke v `stevka_listka` (`id_vadnica`, `celica`,
  `odobreno = false`; CHECK: števka je z listka ali z lista, ne obojega). Fotografija se
  ne hrani. Nova fotografija istega **neodobrenega** lista zamenja prejšnjo (števke se
  ne podvojijo); odobren list se znova ne prebere (409, najprej ga izbriši).
- **Pregled in odobritev** (`/vadnica/:id`): števke po vrsticah, obrobljene tiste, ki
  jih mreža bere drugače; klik izloči. `POST /api/v1/vadnice/{id}/odobri {izlocene}`
  (največ 400 id-jev) izbriše samo izločene števke TEGA lista (poizvedba je omejena na
  list), ostalim nastavi `odobreno` in čas na čas odobritve, da štejejo kot nove za
  nočno učenje; odobritev je pogojna (`odobrena = false`), druga hkratna dobi 409. Vse
  izločene → 409 (list se izbriše). Izbris lista izbriše njegove števke (že naučena
  mreža ostane).
- **Učenje**: `najnovejse` in štetje novih števk vzameta samo `odobreno = true`
  (zastrupitev: neodobrena števka nikoli ne gre v učenje). Preizkusni del se deli po
  listu (`jePreizkusna`: id branja, sicer id lista, sicer id števke), en list je ves
  učni ali ves preizkusni. Hramba 200.000 števk velja tudi za vadnico (števec
  `vadnica.stevk` se ob tem ne popravi).
- **Preverjeno**: `VadnicaTest` (posnetek lista iz brezglavega Chroma
  `resources/branje/vadnica-list.png` + števke MNIST, telefon 2400 × 3200): običajne
  fotografije 180 števk iz 140 okenc, mreža prav 97–99 %; zahtevne (zasuk do 20°,
  perspektiva, zamegljenost) prav tako vsa okenca; vsako okence najdeno ≤ 1 mm od
  natisnjenega roba. **Na pravi pisavi in pravem tisku še ni preverjeno.**

## Dostop, hramba, merilo

- Lastnik turnirja ali admin; dokler je `turnirko.branje-listkov.samo-admin` (privzeto
  `true`, `TURNIRKO_BRANJE_SAMO_ADMIN`), samo admin.
- Fotografija ostane 30 dni po turnirju (`OpravilaBranjaListkov`, 4.35), vrstica branja
  ostane. Omejitev 120 fotografij na 10 minut na uporabnika.
- Hramba učenja (isto nočno opravilo): `stevka_listka` največ 200.000 najnovejših,
  `mreza_stevk` 20 najnovejših sprejetih in 10 zavrnjenih; mreža, ki jo bralnik
  uporablja (`VirMrezeStevk.idTrenutne`), ostane vedno.
- Zlati nabor: največ 5000 listkov (`ZlatiNabor.NAJVEC`, ~75 MB), brez roka, ker
  nima imen (odločitev lastnika 20. 11. 2026); vzorec se obdrži ob vsakem zapisu.
- Stran zasebnosti (`PogojiStran.tsx`, 2.2 in 2.7) opisuje fotografije listkov: obdela
  jih samo strežnik, vidita jih organizator in upravljavec, izbrišejo se 30 dni po
  tekmovanju, za učenje in merjenje ostanejo izrezane številke brez imen (števke in
  pri delu listkov celice s točkami). Besedilo je lastnik potrdil 20. 11. 2026. Ob
  spremembi hrambe ali
  dostopa posodobi tudi to besedilo.
- Merilo natančnosti: `popravljeno` ob potrditvi; stran kaže »brez popravka N od M«.
- Preverjeno na simuliranih fotografijah (`SimuliraneFotografijeTest`: števke iz
  preizkusnega MNIST na posnetku pravega listka, zasuk, perspektiva, svetloba, šum,
  JPEG): običajne 100 % celic, zahtevne 98,8 %, tihe napake ≤ 0,4 %. **Ni preverjeno na
  pravih listkih in pravi pisavi** — prvo merilo je naslednji turnir.
