# Pravila: branje listkov s fotografije

> Organizator s telefonom fotografira izpolnjen sodniški listek, Turnirko prebere točke in predlaga izid, organizator ga potrdi.
> Nastalo 19. 11. 2026 po naročilu lastnika. Migracija V68.

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
3. `CeliceListka`: celice se poiščejo po črtah (navpična lega se spreminja s številom
   nizov); pas brez navpičnih robov (okvirčka pod imeni) ni celica. Zadnja celica je
   Rezultat. Ob neuspehu še enkrat z blažjim pragom.
4. `RazrezCelice`: črnilo → komponente → skupine po stolpcih → razlage (ena ali dve
   števki, z verjetnostjo); števka v obliki MNIST (20 × 20, težišče v sredini 28 × 28).
5. `MrezaStevk` (LeNet, ~80.000 uteži, čista Java) da verjetnost števk (temperatura 1,5).
6. `RazlagaListka`: Viterbi po vrsticah izbere najverjetnejši **veljaven** izid
   (veljaven niz, konec tekme, prazne vrstice po odločitvi, celica Rezultat);
   naprej-nazaj da zaupanje celice. Celica je negotova pod 0,8 ali kadar so pravila
   preglasila mrežo. Imen in zapisov »b.b.« ne bere: posebni izidi so nepopolna tekma.

## Učenje

- Začetna mreža `resources/branje/stevke.bin`: `UcenjeStevk.main` na MNIST (6 epoh,
  popačenje), 99,24 % na preizkusnih MNIST. Ob njej `mnist-izbor.bin.gz` (3000, mešanje
  pri dotreniranju) in `mnist-preizkus.bin.gz` (1000, preverba). MNIST: CC BY-SA 3.0.
- Ob potrditvi `UcenjeIzListkov.poPotrditvi` (po commitu) znova razreže fotografijo in
  shrani števke s potrjeno vrednostjo (`stevka_listka`, 28 × 28, brez imen). Celica, ki
  je razrez ne razdeli na pravo število števk, se preskoči. Pri listku brez kode se smer
  stolpcev izbere po ujemanju z mrežo.
- Nočno (`turnirko.branje-listkov.ucenje-urnik`, 3.50): ob vsaj 200 novih števkah se
  kopija dotrenira (števke listkov ×4 + izbor MNIST); petina števk je preizkusnih. Nova
  razlica (`mreza_stevk`) se **sprejme samo, če na preizkusnih števkah ne bere slabše
  in na MNIST ne nazaduje za več kot 1 %**. `VirMrezeStevk` naloži zadnjo sprejeto.

## Dostop, hramba, merilo

- Lastnik turnirja ali admin; dokler je `turnirko.branje-listkov.samo-admin` (privzeto
  `true`, `TURNIRKO_BRANJE_SAMO_ADMIN`), samo admin.
- Fotografija ostane 30 dni po turnirju (`OpravilaBranjaListkov`, 4.35), vrstica branja
  ostane. Omejitev 120 fotografij na 10 minut na uporabnika.
- Merilo natančnosti: `popravljeno` ob potrditvi; stran kaže »brez popravka N od M«.
- Preverjeno na simuliranih fotografijah (`SimuliraneFotografijeTest`: števke iz
  preizkusnega MNIST na posnetku pravega listka, zasuk, perspektiva, svetloba, šum,
  JPEG): običajne 100 % celic, zahtevne 98,8 %, tihe napake ≤ 0,4 %. **Ni preverjeno na
  pravih listkih in pravi pisavi** — prvo merilo je naslednji turnir.
