# Pravila: branje ligaških zapisnikov s fotografije

> Organizator lige natisne zapisnike kola »za bralnik«, po kolu jih fotografira ali skenira,
> Turnirko prebere, kdo je igral (križci), in točke nizov. Zanesljivo prebran zapisnik se zapiše
> sam, ostale organizator pregleda. Nastalo 26. 11. 2026 po naročilu lastnika. Migracija V79.
> Bralnik števk in poravnava sta ista kot pri listkih turnirja (`docs/pravila/branje-listkov.md`).

## Odločitve lastnika (26. 11. 2026)

- Lige: Savinja A–D in nove regionalne lige; **samo formati z dvema igralcema** (SAVINJA,
  CORBILLON, DSI). SNTL ne (izidi pridejo iz Stupe).
- Organizator zapisnike kola natisne iz Turnirka in jih razdeli; po kolu jih dobi nazaj
  (fizično) in jih slika **s telefonom list za listom** ali **skenira v PDF**.
- Kdo je igral: **križci ob natisnjenih imenih kadra** (ne številke, ne imena z roko, ne
  telefon kapetana). Menjave so pogoste. Točke vsakega niza ostanejo.
- **Obe obliki** lista (lastnik: »način 2 ima ob večjih kadrih resne probleme, takrat raje
  način 1«): `VRSTICE` (vrstica tekme) in `KADER` (tabela kadra); samodejna izbira.
- **Samodejni zapis, če je zanesljivo**; fotografija 30 dni, vidi jo samo organizator.

## List (vmesnik ↔ zaledje)

- **Edini vir leg je `GeometrijaZapisnika.java`.** Strežnik ob tisku vrne postavitev
  (`NatisZapisnikaDto.postavitev`), `ZapisnikZaBralnik.tsx` jo nariše kot en SVG v mm
  (ležeči A4, `@page zapisnik-bralnik` z robom 0). Vmesnik nima nobene lastne mere.
  **Ob spremembi leg povečaj `RAZLICICA`** in staro ohrani v `izracunaj` (natis hrani
  različico, natisnjeni listi se morajo še brati).
- Oznake v vogalih so **8 mm** (vadnica 6 mm), oznake vrst **2,5 mm**: simulacija je
  pokazala, da poravnava 3-milimetrsko oznako vrste ob zamegljeni fotografiji vzame za vogal
  (razmerje ploščin 0,25 je tik pod mejo 0,3, zamegljenost jo preseže). Pri 8 in 2,5 mm je
  razmerje ~0,1.
- QR `TZ:<id natisa>:<skrivnost>` (verzija 1, 20 mm, `SkrivnostListka.zaZapisnik`: isti ključ
  kot listki, drug vhod), pod njo `Z-<id>`.
- Vsaka vrsta (tekma pri VRSTICAH; vrstica kadra in vrstica točk pri KADRU) ima na obeh
  koncih oznako vrste. Kvadratki 5–5,5 mm, celice 10–16 mm široke, 10–12 mm visoke, vse s
  črto 0,45 mm (kot vadnica), razmik med celicama vsaj 3 mm (razrez gleda 1,5 mm okolice).
- **VRSTICE**: oznaka tekme, kvadratki domačih, nizi (domači : gost), kvadratki gostov;
  imena kadra navpično nad stolpci. Gre, dokler je celica vsaj 10 mm: pri 5 nizih do 14
  vrstic kadra skupaj (npr. 6 + prazna na ekipo).
- **KADER**: zgoraj dve tabeli kadra (igralec = vrstica, tekma = stolpec), spodaj tabela
  točk. Vrstica kadra 6–8 mm (pri petih tekmah ~10 vrstic na ekipo).
- **Samodejna oblika**: VRSTICE, kadar gre kader nanje, sicer KADER, sicer napaka.
- Zadnja vrstica (stolpec) kadra je **prazna, za novega igralca**: križec v njej je vedno
  negotov, igralca izbere organizator.

## Natis (`NatisZapisnikovStoritev`, `POST /api/v1/zapisniki/natis`)

- Samo organizator tekmovanja in admin (`preveriPoSrecanju`; uvoženo se zavrne), samo
  formati z dvema igralcema, samo nekončano srečanje.
- `natis_zapisnika` zapiše srečanje, obliko, različico, format, število nizov in **kadra po
  vrsticah** (JSON id-jev v vrstnem redu kadra). QR nosi samo id natisa: kader se lahko po
  tisku spremeni, list ostane berljiv. **Enak natis se ne podvaja** (ponoven tisk kola).
- Strani: `/lige/:id/zapisniki-kola?kolo=&oblika=` (vse kolo, zunaj postavitve) in izbira
  »Za bralnik« na `/srecanja/:id/listki`. Povezavi na strani lige (»Zapisniki kola«,
  »Branje zapisnikov«) vidi lastnik lige z dvema igralcema, ko je branje omogočeno.

## Branje (`LokalniBralnikZapisnika`)

1. QR → natis (skrivnost, dostop nalagalca), sicer `NAPAKA` z razlago (tudi »to je listek
   turnirja«, »dva zapisnika na eni fotografiji«).
2. Poravnava kot pri listku (`PoravnavaListka` s `GeometrijaZapisnika.POSTAVITEV`).
3. **Izboljšana poravnava** (`OdcitekZapisnika.izboljsaj`): oznake vrst (desetine točk po
   vsem listu) + vogali → homografija po najmanjših kvadratih, točke z ostankom nad 1,5 mm
   se izločijo, dva kroga, list se vsakič preslika iz FOTOGRAFIJE. Brez tega je bila
   poravnava A4 iz treh oznak in majhne QR zamaknjena za več mm.
4. Zamik vsake vrste po njenih oznakah (linearno med njima), nato rob vsakega kvadratka in
   celice po črti (`CeliceVadnice.okence`).
5. Kvadratek: delež črnila v notranjosti brez 0,5 mm ob robu → `PRAZEN` (≤ 3,5 %),
   `KRIZEC` (6–55 %), `POCRNJEN` (≥ 70 %, preklic), sicer `NEJASNO`.
6. Točke vsake tekme: mreža števk + `RazlagaListka` (brez celice Rezultat).

## Predlog (`PredlogZapisnika`)

- Na stran tekme: natanko 1 (dvojice 2) jasna križca in nič nejasnega = zanesljivo; nič =
  ni igral; sicer najverjetnejši, negotovo. Nov igralec je vedno negotov.
- Potek: pri pragu zmag morajo biti tekme do odločitve odigrane, za njo prazne; brez praga
  vse odigrane. Neskladje je opozorilo.
- Vsako mesto (A, B / X, Y) mora imeti igralca iz prve odigrane posamične tekme tega mesta;
  isti igralec ne sme biti na dveh mestih.
- **Zanesljiv** = brez negotovega križca in celice, brez opozoril, najdenih vsaj 80 % oznak vrst.

## Zapis (`ZapisZapisnikaStoritev`)

- **Ista pot kot ročni vnos, v eni transakciji**: `nastaviPostavo` → `zamenjajIgralce` (pred
  izidi) → `vnesiRezultat` po vrsti; vse preverbe, lastništvo, rating in preračun za nazaj.
  Karkoli se zavrne → nič se ne zapiše.
- **Postava se izpelje**: mesto P je igralec prve tekme z izidom, v kateri nastopa mesto P;
  drug igralec kasneje = menjava te tekme (`docs/pravila/lige.md`, menjava je last ENE
  tekme). Pri formatih brez izbire para sta v dvojicah oba igralca postave; drug par je
  menjava dvojic.
- Že vpisano srečanje (končano ali s KONCANO tekmo) se zapiše le s `prepisi` → najprej
  `razveljaviZapisnik`. Brez boja se zavrne.
- **Past:** preračun ratinga (prepis, vnos za nazaj) izprazni sejo — vrstica branja se po
  zapisu prebere znova (`potrdi`), sicer se sprememba stanja izgubi (ujel test).

## Storitev branja (`BranjeZapisnikovStoritev`, V79)

- Sprejem kot pri listkih: dostop in meja (120 / 10 min) v kratki transakciji, dekodiranje
  pod skupno mejo dekodiranj (`BranjeListkovStoritev.podDekodiranjem`), fotografija do
  3200 px, ključ pošiljanja (`Idempotency-Key`) proti dvojnikom, branje na izvajalcu branja
  listkov po potrditvi transakcije.
- **Branje in samodejni zapis tečeta z identiteto nalagalca** (`kotNalagalec`: varnostni
  kontekst v niti izvajalca), zato veljajo natanko njegove pravice; natis tujega tekmovanja
  konča v napaki.
- Samodejni zapis: `samodejno` (izbira na strani ob nalaganju) + zanesljivo + srečanje ni
  vpisano. Neuspel zapis (npr. igralec ni več v kadru) ostane v pregledu z `opomba`.
- Dvojniki: novo prebrano branje istega srečanja zapre starejša odprta; zapis zapre ostala.
- Branje vidi samo, kdor ga je naložil, in admin. Isto stikalo kot listki
  (`turnirko.branje-listkov.samo-admin`). Fotografija 30 dni od nalaganja (nočno opravilo),
  vrstica ostane; ob zagonu se branja v vrsti označijo kot napaka.
- **Učenje**: števke gredo v `stevka_listka` (tretji vir `id_branje_zapisnika`) samo iz
  zapisnikov, ki jih je **potrdil organizator** (samodejni zapis bi mrežo učil na njenem
  branju); popravljena celica je močna oznaka; štejejo med »števke iz dvorane«.

## Vmesnik

- `/zapisniki/branje`: »Fotografiraj zapisnik«, »Naloži slike ali PDF« (PDF strani nariše
  `pdfjs-dist` v brskalniku, naloži se šele ob prvem PDF), stikalo »zapiši sam«
  (localStorage), vrsta brez signala (`useVrstaFotografij`, prostor `PROSTOR_ZAPISNIKOV`,
  3200 px), skupine po ligi in kolu, »Zapiši vse zanesljive«, pregled z izrezi (pri
  igralcih izrez vseh kvadratkov te strani tekme), »popravi« zapisanega (prepis).

## Merilo

- `SimulacijaZapisnikaTest` (obe obliki, običajne in zahtevne fotografije, menjave,
  preklicani križci, prag zmag): igralci 100 %, celice 99,6–99,8 %, **0 tihih napak** na 80
  zapisnikih (`-Dsimulacija.zapisnikov=20`). Negotovih celic ~5–6 % (kot listki): pri ~40
  celicah ima večina zapisnikov 2–4 polja za pregled, samodejno se jih zapiše malo, dokler
  se mreža ne nauči pisave iz dvorane.
- `BranjeZapisnikaFotografijeTest`: natis iz baze → list → fotografija → branje → potrditev →
  števke za učenje (obe obliki). `NatisnjenZapisnikPoskus`: list, kot ga natisne VMESNIK
  (posnetek iz brezglavega Chroma), prebran na pravem zaledju — obe obliki brez napake
  (26. 11. 2026). **Na pravih natisnjenih listih in pravi pisavi še ni preverjeno.**
