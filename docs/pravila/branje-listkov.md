# Pravila: branje listkov s fotografije

> Organizator s telefonom fotografira izpolnjen sodniški listek, Turnirko prebere točke in predlaga izid, organizator ga potrdi.
> Nastalo 19. 11. 2026 po naročilu lastnika. Migracija V68.
> Načrt nadaljnjega dela (merjenje, natančnost, vadnica, postaja za zajem s škatlo):
> `docs/NACRT-BRANJE-LISTKOV.md`.

- **Zakaj:** na turnirju sta morala biti dva organizatorja, ker prepisovanje listkov v
  Turnirko traja predolgo. Sodniki in igralci pri mizi telefona nimajo (QR pri mizi
  zavrnjen), zato se listek fotografira na pultu.
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
- QR nosi `TK:<id tekme>` (verzija 1), pod njo je `T-<id>` (`pomozno/kodaListka.ts`,
  `KodaListka.java`). Prazen listek kode nima — tekmo izbere organizator s seznama
  tekem, ki čakajo na izid.
- Celica za točke je **prosta** (lastnik je zavrnil okenca za števke): razrez na eno ali
  dve števki je del branja.

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
