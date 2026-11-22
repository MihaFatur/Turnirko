# Načrt: zanesljivo branje listkov in postaja za zajem

> Nastalo 20. 11. 2026 po naročilu lastnika (»napiši natančen plan, da ga boš potem
> lahko naredil; pri vsakem koraku preveri, da ne puščaš napak ali ranljivosti«).
> Izhodišče: commit `a04b155` (V68) na veji `test`, branje samo za admina, preverjeno
> samo na simuliranih fotografijah. Pravila delujočega sistema:
> `docs/pravila/branje-listkov.md`. Ta dokument je **načrt**; ko je faza narejena, se
> njena pravila prenesejo v `docs/pravila/branje-listkov.md`, tu pa se faza označi kot
> narejena (datum, commit).

## 1. Cilj in merila

**Cilj:** turnir vodi en organizator. Listek pride od pulta do vpisanega izida brez
tipkanja, napačen izid pa ne gre v žreb neopažen.

| Merilo (meri ga F1) | Cilj za uvedbo (F9) |
|---|---|
| Listki brez popravka (potrjeni z enim dotikom) | ≥ 80 % |
| Tihe napake: napačna celica, ki ni bila označena kot negotova | ≤ 1 listek na 200 |
| Organizatorjev čas na listek (zajem + pregled + potrditev) | ≤ 10 s povprečno |
| Neuspeli zajemi (fotografijo ali vstavljanje je treba ponoviti) | ≤ 5 % |

**Česa ne delamo:**
- samodejne potrditve brez človeka (morda šele F9, korak 4, in samo na podlagi meritev);
- branja imen;
- plačljivih storitev (odločitev lastnika 19. 11. 2026).

## 2. Kako se izvaja vsak korak (obvezno, brez izjem)

**Pred začetkom faze**
1. `git status` in `git pull --ff-only` na veji `test`. Mapo deli več sej: tuje
   neshranjeno delo ne sme priti v commit te faze.
2. Preberi `docs/pravila/branje-listkov.md` in pravila področij, ki jih faza
   spreminja (rezultati, vloge, tisk, pomoč, vmesnik).
3. Številka nove migracije: najvišja v `db/migration/postgresql/` **in** na
   `origin/test` + 1. Migracija gre samo v `postgresql/`, obstoječih se ne spreminja.

**Med delom**
- Domenska logika najprej dobi test (pravilo projekta). Vsaka poizvedba, ki gre v
  DTO, ima `join fetch`. Novi poti, ki vrača DTO, pripada test brez `@Transactional`
  (vzorec `LenoNalaganjeTest`).
- Imena in komentarji so slovenski, brez šumnikov v identifikatorjih.

**Varnostni kontrolni seznam** (za vsako novo pot, stolpec, datoteko ali odvisnost)

| # | Področje | Preveri |
|---|---|---|
| V1 | Dostop | Vsaka pot preveri vlogo in lastništvo. Vsak id iz zahteve (branje, tekma, turnir, postaja, vadnica) se preveri, da pripada istemu turnirju ali lastniku. Test z drugim organizatorjem mora dobiti 403/404. |
| V2 | Vhod | Meje velikosti (bajti, piksli, dolžina seznamov, številski razponi). Nič ni neomejeno. Neveljaven vhod vrne 400 z razumljivim slovenskim sporočilom, ne 500. |
| V3 | Viri / DoS | Omejene zanke (število komponent, rezov, kandidatov), časovna meja branja, omejena vrsta izvajalca, omejitev hitrosti na uporabnika ali postajo, pomnilnik v 1-GB vsebniku testa. |
| V4 | Zasebnost | Fotografije listkov nosijo imena (tudi mladoletnih): samo organizator turnirja in admin, 30 dni. Kar se hrani dlje, ne sme vsebovati imen (izrezi celic). Nič osebnega v dnevniku, URL-ju ali odgovoru postaje. |
| V5 | Uhajanje | DTO-ji brez entitet; napake ne razkrijejo, ali tuj id obstaja; odgovori s slikami imajo `Cache-Control: private, no-store`. |
| V6 | Skrivnosti | Žetoni samo kot zgoščena vrednost (SHA-256), prikazani enkrat, nikoli v dnevniku ali URL-ju; primerjava prek iskanja po zgoščeni vrednosti. |
| V7 | Vmesnik | Brez `dangerouslySetInnerHTML`; besedilo s strežnika samo kot besedilo; nič občutljivega v `localStorage` ali poizvedbenem nizu. |
| V8 | Odvisnosti | Točna različica, licenca (MIT/Apache/BSD), velikost paketa; v vmesniku samo na strani, ki jo potrebuje (dinamični uvoz). |
| V9 | Obnova | Kaj se zgodi ob izpadu strežnika, omrežja ali elektrike sredi koraka? Nič se ne sme podvojiti ali tiho izgubiti. |

**Po koncu faze**
1. Zaledje: celotna zbirka `mvnw test` (JDK 25). Vmesnik: `npx vitest run`,
   `npm run lint`, `npm run build`. Vse zeleno; nove opozorila lint popravljena.
2. E2E v brskalniku na telefonski širini z lokalnim zaledjem in bazo (spomin:
   `zagon-zaledja-testna-baza-ui`). Posnetki prek brezglavega Chroma (CDP), ne iz
   Browser pane.
3. Varnostni pregled razlike: podagent Sonnet (največ 3 hkrati) preveri razliko po
   seznamu V1–V9 in poroča najdbe. Nato `/code-review` (medium). Vsaka najdba je
   popravljena ali utemeljeno zavrnjena v poročilu faze.
4. Dokumentacija: `docs/pravila/branje-listkov.md`, članek pomoči
   (`pomoc/vsebina/rezultati.ts`; pravilo »posodobi pomoč ob vsaki spremembi
   funkcije organizatorja«), ta načrt (oznaka »narejeno«), spomin.
5. Commit in push **samo na lastnikovo zahtevo**, na `test`. Nato
   `objavi.sh` na strežniku in preverba na test.turnirko-nt.si (če faza prinese
   migracijo: `osvezi-bazo.sh` pred tem, da migracija teče nad svežo kopijo).

## 3. Ugotovitve pregleda obstoječe kode (20. 11. 2026)

**Preverjeno, brez napake:**
- Vse poti branja preverijo lastništvo turnirja branja (`preveriDostop`).
- Potrditev zavrne tekmo z drugega turnirja (`tekmaTurnirja`).
- Dvojna potrditev se ne podvoji. Prva zapre branje. Hkratna druga pade na
  `KONCANA` v `TekmaStoritev` in se razveljavi, preden bi sprožila učenje (ta teče
  `afterCommit`).
- Slikovna »bomba« je omejena: `ObdelavaSlik.preberi` preveri mere pred
  dekodiranjem (≤ 8000 px, ≤ 40 MP, ≤ 8 MB).
- Mreža je varna za dve niti hkrati (`Prostor` na klic, `AtomicReference` v
  `VirMrezeStevk`).
- Prijava je Basic brez sej in piškotkov, zato CSRF ne pride v poštev.
- Brskalnik nima `Permissions-Policy`, ki bi prepovedala kamero (pomembno za F7).

**Za popravek v F0:**
1. 40 MP dovoljuje ~160 MB pomnilnika na eno dekodiranje. Ob nekaj hkratnih
   nalaganjih bi testni vsebnik (1 GB) lahko ostal brez pomnilnika.
2. Učenje deli števke na učne in preizkusne **po števki**, zato je ista pisava v obeh
   delih. Natančnost na preizkusnih števkah je zato videti boljša, kot je.
3. Ni zaščite pred dvojniki: isti listek, poslan dvakrat, da dve odprti branji iste
   tekme. Pri postaji (F7/F8) bo to pogosto.
4. `stevka_listka` in `mreza_stevk` rasteta brez meje.
5. Stran zasebnosti (`PogojiStran.tsx`) ne omenja fotografij listkov.

## 4. Faze

Velikost: S = do pol dneva dela, M = dan ali dva, L = več dni. Odvisnosti so v
razdelku 6.

### F0 — Utrditev obstoječega (S)

| Korak | Kaj | Preverba |
|---|---|---|
| 0.1 | Lastna meja za fotografijo listka: daljša stranica ≤ 4096 px, ≤ 16 MP (vmesnik pošlje ≤ 2000 px, postaja ≤ 2600 px). Pred dekodiranjem v `sprejmi` semafor (največ 2 hkratni dekodiranji, čakanje ≤ 10 s, sicer 503 »Strežnik je zaseden, poskusi znova«). | Test: 5000 × 4000 px → 400 s sporočilom; tri hkratne zahteve → tretja počaka. |
| 0.2 | Razdelitev učnih in preizkusnih števk **po listku** (`id_branje`; kasneje po listu vadnice), ne po števki. | Test: vse števke istega branja so v istem delu. |
| 0.3 | Dvojnik: ko QR določi tekmo, ki že ima odprto branje (V_VRSTI, PREBRANO, NAPAKA), se starejše zapre kot ZAVRZENO z razlogom »Nadomeščeno z novejšo fotografijo«. Velja najnovejša fotografija. | Test: dve branji iste tekme → odprto ostane samo novejše; potrjeno se ne dotakne. |
| 0.4 | Hramba: `stevka_listka` največ 200.000 vrstic (najstarejše gredo), `mreza_stevk` vse sprejete zadnjih 20 in zavrnjene zadnjih 10. Nočno opravilo v `OpravilaBranjaListkov`. | Test meje in da trenutno uporabljena mreža nikoli ni izbrisana. |
| 0.5 | Zasebnost: razdelek v `PogojiStran.tsx`. Fotografije listkov hrani in obdeluje samo strežnik Turnirka, vidita jih organizator turnirja in admin, izbrišejo se 30 dni po turnirju. Za učenje se hranijo izrezi celic s številkami, brez imen. **Besedilo potrdi lastnik** (potrjeno 20. 11. 2026). | Pregled besedila. |
| 0.6 | Dnevnik: pregled vseh `log.*` v branju in učenju. Samo id-ji in številke, nikoli imena ali vsebina predloga. | Grep + pregled. |

**Gotovo, ko:** testi zeleni, seznam V1–V9 pregledan, lastnik je potrdil besedilo
zasebnosti.

### F1 — Merjenje (M)

Brez tega ne vemo, ali sistem deluje. Vsa naslednja merila temeljijo na F1.

| Korak | Kaj |
|---|---|
| 1.1 | **Sled pregleda.** Vmesnik ob potrditvi pošlje `casPregledaMs` (od prikaza predloga do potrditve; strežnik sprejme 0–3.600.000, sicer shrani `null`) in `nacin` (`EN_DOTIK` ali `OBRAZEC`). Nova stolpca v `branje_listka`. |
| 1.2 | **Naključna kontrola.** V `obdelaj` dobi pripravljen predlog z verjetnostjo *p* (privzeto 5 %, `turnirko.branje-listkov.kontrola`) oznako `kontrola`. Vmesnik ga pokaže v celotnem pregledu z opombo »Naključna kontrola«, ne šteje med zanesljive in ga »Potrdi vse« (F4) izpusti. Iz kontrol se oceni delež tihih napak. |
| 1.3 | **Statistika** (`MeritevBranja`, samo admin, `GET /api/v1/branje-listkov/meritev?od=&do=&idTurnir=`). Iz `prebrano` in `potrjeno` (upoštevaj obrnjene strani) za vsako celico: prav ali narobe × negotova ali zanesljiva. Izpelje: listke brez popravka, tihe napake, lažne alarme, mediano in p90 časa pregleda. Po turnirju, po različici mreže in skupaj. Stran `/branje-listkov/meritev` (admin). |
| 1.4 | **Zlati nabor** (`zlati_listek`). Po potrditvi se v izvajalcu učenja shrani poravnan listek 1050 × 990 v sivinah (PNG), **na katerem so ohranjeni samo piksli znotraj najdenih celic**, vse ostalo je belo. Zraven: potrjen izid, število nizov, oznaka mreže. Nima imen, zato se hrani trajno. Do 5000 listkov, nato naključna zamenjava (*reservoir sampling*). |
| 1.5 | **Meritev nad naborom.** `MeritevNabora` požene trenutni bralnik od celic naprej nad zlatim naborom in vrne iste številke kot 1.3. Uporablja se pri vsaki spremembi bralnika (F2) in pri sprejemu nove mreže (F6). Admin lahko prenese nabor kot zip (PNG + JSON) za lokalne meritve. Regresijski test v repozitoriju dobi podnabor (≤ 100 listkov, ≤ 3 MB) **šele po lastnikovem soglasju**. |

**Varnost:**
- 1.3 in 1.5 sta samo za admina (V1).
- Test za 1.4 preveri, da je slika zunaj celic povsem bela (V4): sintetičen listek s
  »črnilom« v glavi → v shranjenem PNG ga ni.
- Zip je omejen na velikost nabora (V3).

**Gotovo, ko:** stran meritev na testnih podatkih kaže pravilne številke, preverjene
na roko na petih listkih.

### F2 — Natančnost branja (M–L)

Vsaka sprememba se meri na simulaciji (`SimuliraneFotografijeTest`) in, ko obstaja,
na zlatem naboru. **Sprememba, ki poslabša katerokoli merilo, se ne sprejme.**

| Korak | Kaj |
|---|---|
| 2.1 | **Pogostost izidov iz zgodovine** (`PogostostNizov`). Iz tabele `niz` šteje poraženčeve točke pri 11 in dolžino podaljšanja z glajenjem. Upošteva samo normalno končane tekme, brez datuma 1. 1. 1900 in samo veljavne nize. Izračun ob zagonu in nočno (ena poizvedba `GROUP BY`). V `RazlagaListka` vstopi kot faktor P(niz)^β; β privzeto 0,5, umeri se na naboru. Celica je negotova tudi, kadar se izbira z zgodovino razlikuje od izbire brez nje (dve izvedbi naprej-nazaj). |
| 2.2 | **Večkratno branje števke.** Pet različic (premik ±1 px, zasuk ±4°), povprečje verjetnosti. Meja: listek < 1 s. |
| 2.3 | **Več rezov sklenjenega zapisa.** 3–5 kandidatnih rezov (najtanjši stolpci v 30–70 % širine), vsak je svoja razlaga. Verjetnost iz oblike × mreže. |
| 2.4 | **Črte tabele.** Po poravnavi je lega črt znana (`CeliceListka`). Piksli na črti ne povezujejo komponent, zato števka, napisana čez črto, ostane cela in ni odrezana. |
| 2.5 | **Popravljena celica.** Nenavadno veliko črnila ali več kot tri skupine potez → vedno negotova z opombo »popravljena celica«. |
| 2.6 | **Poravnava z rezervnimi točkami.** QR da tri iskalne vzorce na znanih mestih. Iz šestih znanih točk (3 oznake + 3 vzorci) se izbere ≥ 4 z najmanjšo napako preslikave; vsaj dve morata biti oddaljeni > 60 mm. Prst čez eno oznako ni več napaka. Nato lokalni popravek po črtah tabele (prepognjen listek). |
| 2.7 | **Zahtevnejša simulacija.** Prepognjen listek, prst čez vogal, prečrtana celica, evropska enica z nosom in prečrtana sedmica (sintetično dodani poteznici na števke MNIST). Gre v `SimuliraneFotografijeTest` kot tretja raven. |

**Varnost:**
- 2.2 in 2.3 množita računanje, zato sta omejena: največ 40 komponent na celico,
  največ 5 rezov, časovna meja branja 5 s → NAPAKA »Branje je trajalo predolgo«
  (V3).
- Poizvedba 2.1 bere samo tabelo `niz` in `tekma` brez osebnih podatkov.

**Gotovo, ko:**
- simulacija: običajne ostanejo 100 %, zahtevne ≥ 99 %, nova raven izmerjena;
- tihe napake ≤ 0,2 % celic;
- čas na listek izmerjen na strežniku (dnevnik).

### F3 — Vadnica števk (M)

Hladen začetek: tisoče števk slovenske pisave, preden pride prvi pravi listek.

| Korak | Kaj |
|---|---|
| 3.1 | **Tisk.** Admin ali organizator ustvari *n* listov (`POST /api/v1/vadnice`, *n* ≤ 50; strežnik vrne id-je). Natisljiva stran A4 pokončno: oznake v vogalih + QR `TKV:1:<id lista>` (različica postavitve, id). 14 vrstic po 10 prostih celic enake velikosti kot na listku: vrstice 0–9 in vrstice »10«, »11«, »12«, »13« (sklenjeni dvomestni zapisi so najtežji). Vzorec števke je natisnjen levo od vrstice, ne v celici. Postavitev pozna `GeometrijaVadnice`. |
| 3.2 | **Branje.** Ista stran za fotografiranje prepozna `TKV` in pošlje list v `VadnicaStoritev`. Oznaka števke je vrstica. Dvomestne celice se razrežejo v dve števki; če razrez ne da dveh, se celica preskoči in prešteje. Shrani v `stevka_listka` z novima stolpcema `vir` (`LISTEK`/`VADNICA`) in `id_vadnica`, **še ne za učenje** (`odobreno = false`). |
| 3.3 | **Pregled admina.** Mreža vseh števk lista po vrsticah; klik izloči napačno napisano števko. »Odobri list« šele spusti števke v učenje. |
| 3.4 | **Učenje.** Nočno učenje vključi odobrene števke vadnice. Preizkusni del se deli po listu (en list = en pisec). |
| 3.5 | **Akcija (lastnik).** Na klubskem treningu 20–30 ljudi izpolni vsak en list (~140 števk) → 3000–4000 števk. Če se da, pisci s kemičnim svinčnikom, kot sodniki. |

**Varnost:**
- **Zastrupitev mreže:** zlonameren uporabnik bi lahko naložil napačno označene
  števke. Zato šele odobritev admina spusti list v učenje (V1), poleg tega velja
  sprejem nove mreže iz F6.
- Vadnica nima polj za imena (V4).
- Omejitev *n* in hitrosti nalaganja (V3).

**Gotovo, ko:** list natisnjen, izpolnjen na roko, fotografiran in pravilno razrezan
(≥ 95 % celic) na testu; nočno učenje z vadnico izmerjeno na preizkusnih listih.

### F4 — Pregled in potrditev (M)

| Korak | Kaj |
|---|---|
| 4.1 | **Izrez celice ob številki.** `obdelaj` shrani poravnan listek (JPEG ~100 kB) v `branje_listka_slika.poravnana` in pravokotnike celic (mm) v `prebrano`. Nova pot `GET /api/v1/branja-listkov/{id}/poravnana` z istim preverjanjem dostopa. Vmesnik pokaže izrez celice ob vsakem polju iz ene slike (CSS ozadje, brez dodatnih zahtev); negotove so povečane. Brisanje po 30 dneh zajame obe sliki. |
| 4.2 | **»Potrdi vse zanesljive«.** Zaporedni klici obstoječe potrditve (vsak svoja transakcija in preverba). Ustavi se ob prvi napaki in jo pokaže. Kontrole iz F1 izpusti. |
| 4.3 | **Vrstni red.** Zanesljivi zgoraj, negotovi in kontrole spodaj; koda `T-…` vidna v vsaki vrstici. |
| 4.4 | **Fotografija pri tekmi.** V oknu tekme ima organizator ali admin povezavo »Listek« (zadnje potrjeno branje te tekme, dokler fotografija obstaja). `GET /api/v1/tekme/{id}/listek` preveri lastništvo turnirja tekme. Javnost je ne vidi nikoli. |
| 4.5 | **Samodejno osveževanje vrste.** Vsake 3 s, dokler je stran vidna (`visibilityState`). Potrebno za postajo. |

**Varnost:**
- Nove poti s slikami: lastništvo prek turnirja branja ali tekme; test s tujim
  organizatorjem (V1).
- Glave `Content-Type: image/jpeg`, `Cache-Control: private, no-store`,
  `X-Content-Type-Options: nosniff` (V5).

**Gotovo, ko:** E2E na telefonu in prenosniku. Negotova celica pokaže pravi izrez,
»Potrdi vse« potrdi samo zanesljive.

### F5 — Telefon in signal v dvorani (M)

| Korak | Kaj |
|---|---|
| 5.1 | **Preverba pred pošiljanjem** (v brskalniku, na pomanjšani sliki): ostrina (varianca Laplacea), svetlost, prisotnost QR (`jsqr` 1.4.0, MIT, dinamično naložen samo na strani branja). Neuspeh → »Fotografiraj znova« ali »Vseeno pošlji«. Pragovi se umerijo na zbranih fotografijah. |
| 5.2 | **Vrsta brez signala.** Fotografija gre najprej v IndexedDB, nato se pošlje. Ob napaki omrežja ali 5xx ponovi z naraščajočim zamikom; ob 4xx ne ponavlja in pokaže razlog. Prikaz »3 čakajo na signal«. Izbris po uspehu, najkasneje po 24 h in ob odjavi. Vsak vnos ima naključni ključ, ki ga strežnik uporabi za idempotenco (glava `Idempotency-Key`, unikaten na uporabnika, 24 h), zato ponovitev po izgubljenem odgovoru ne ustvari dvojnika. |
| 5.3 | **Velikost datoteke.** Preveri, ali kakovost 0,82 pri 2000 px ohrani natančnost na simulaciji. Na listku ne sme biti manj kot 12 px/mm. |
| 5.4 | **Zaslon ne ugasne.** Wake Lock, dokler je stran odprta. |

**Varnost:**
- IndexedDB vsebuje fotografije z imeni: brisanje po uspehu, 24 h, ob odjavi (V4).
- Ključ idempotence je vezan na uporabnika, da ga tuj uporabnik ne more »zasesti«
  (V1).
- Novi stolpec ali tabela za ključe ima nočno čiščenje.

**Gotovo, ko:** v Chromu z omrežjem »Offline« (DevTools) se trije posnetki zberejo in
po vklopu pošljejo natanko enkrat.

### F6 — Učenje brez napak (M)

| Korak | Kaj |
|---|---|
| 6.1 | **Popravek izida popravi oznake.** `TekmaStoritev.popraviRezultat` (in vse druge poti, ki spremenijo izid končane posamične tekme; poišči jih z grepom) objavi dogodek. `UcenjeIzListkov` izbriše števke branj te tekme in jih, če fotografija še obstaja, zapiše znova z novim izidom. |
| 6.2 | **Močne oznake.** Celica, ki jo je organizator izrecno popravil, dobi `popravljena = true` in pri učenju dvojno težo. |
| 6.3 | **Strožji sprejem nove mreže.** Ne sme biti slabša na preizkusnih števkah (deljenih po listku ali listu), na zlatem naboru (cel listek, F1.5), na MNIST pa ne slabša za več kot 1 %. |
| 6.4 | **Admin: zgodovina, »Uči zdaj«, »Vrni prejšnjo«.** Zgodovina je tabela `mreza_stevk` s številkami. »Uči zdaj« teče asinhrono, vedno samo eno učenje (zaklep v bazi + `AtomicBoolean`), najmanj 10 min med zagoni. »Vrni prejšnjo« označi zadnjo sprejeto kot umaknjeno in `VirMrezeStevk` naloži prejšnjo. |
| 6.5 | **Meje učenja.** Trajanje se zapiše v dnevnik. Učenje teče z 2 nitma in se prekine po 30 min (zapis »prekinjeno«, mreža ostane stara). |

**Varnost:**
- Samo admin (V1).
- Zaklep in premor preprečita zaporedne zagone (V3).
- Ob prekinitvi ali padcu strežnika se nič ne spremeni: mreža se zamenja šele po
  zapisu sprejete različice (V9).

**Gotovo, ko:** testi pokrijejo popravek oznak po popravku izida, sprejem in
zavrnitev po zlatem naboru, »Vrni prejšnjo« in zaklep.

### F7 — Postaja: programski del na strežniku in v brskalniku (L)

**7.1 Postaja v brskalniku** (telefon na stojalu, prehodna rešitev in preizkus poteka
pred škatlo):
- Stran `/turnirji/:id/branje-listkov/postaja` z obstoječo prijavo organizatorja.
- Zadnja kamera prek `getUserMedia` (≥ 1920 × 1080). Na pomanjšanih sličicah 5× na
  sekundo `jsqr` + prisotnost belega listka + ostrina.
- Ko je listek prisoten, oster in pri miru 0,4 s → zajem polne ločljivosti → pošiljanje
  prek vrste iz F5.
- Naslednji zajem šele, ko listek izgine iz slike (ni dvojnikov).
- Zelen ali rdeč blisk in zvok; Wake Lock.

**7.2 Naprave postaje** (za škatlo iz F8):
- **Tabela `postaja`:** `id`, `id_lastnik`, `ime`, `zeton_hash` (bytea 32,
  unikaten), `ustvarjeno_ob`, `zadnja_uporaba_ob`, `id_zadnji_turnir`,
  `preklicano_ob`. Branje dobi `id_postaja`.
- **Dodaj postajo:** organizator na strani »Postaje« (7.3). Strežnik ustvari
  256-bitni žeton (`SecureRandom`, oblika `tkp_` + base64url). Shrani samo SHA-256,
  žeton pokaže **enkrat** skupaj z datoteko `postaja.toml` za kartico SD. Preklic
  deluje takoj.
- **Pošiljanje:** `POST /api/v1/postaja/listki` z glavo
  `Authorization: Postaja <žeton>`.
  - Lasten filter velja samo za `/api/v1/postaja/**`.
  - Na tej poti Basic ne velja, žeton pa ne velja nikjer drugje.
  - Neveljaven in preklican žeton dobita enak 401.
- **Usmerjanje po QR:** strežnik QR prebere že ob sprejemu (~50 ms) in iz tekme
  izpelje turnir.
  - Sprejme samo, če ima **lastnik postaje** pravico do tega turnirja in dogodek teče.
    Nova metoda v `LastnistvoStoritev`, ki preveri podanega uporabnika, ne
    »trenutnega«.
  - Brez QR gre listek v zadnji turnir postaje, če še teče; sicer 422 »Listek nima
    kode in postaja nima turnirja«.
  - Odgovor ne razkrije, ali tuja tekma obstaja.
- **Omejitve:** 120 listkov / 10 min na postajo in skupaj na lastnika, ≤ 8 MB,
  ≤ 16 MP, semafor iz 0.1, idempotenca iz 5.2.
- **Odgovor sprejema:** samo `{sprejeto, idBranja, koda: "T-1234" | null, razlog}`,
  brez imen.
- **`GET /api/v1/postaja/stanje`:** ime postaje in veljavnost žetona (preverba ob
  zagonu naprave), nič drugega.

**7.3 Stran »Postaje«** (organizator): seznam, zadnja uporaba, zadnji turnir, dodaj,
preklic; članek pomoči.

**Varnostni testi** (vsi obvezni):
- tuj, preklican in napačno oblikovan žeton → 401;
- QR tekme tujega organizatorja → zavrnjeno, enako sporočilo kot neobstoječa tekma;
- Basic na `/postaja/**` → 401;
- žeton postaje na drugih poteh → 401;
- omejitev hitrosti;
- v bazi je samo zgoščena vrednost;
- žeton se ne pojavi v dnevniku (test z zajemom dnevnika).

**Gotovo, ko:** telefon na stojalu na testu zajame 30 listkov zapored brez dvojnika;
skripta s `curl` potrdi vse zgornje varnostne primere na test.turnirko-nt.si.

### F8 — Postaja: naprava (škatla z režo) (L)

Zasnova je v razdelku 5. Programje in navodila v repozitoriju, mapa `postaja/`.

**Kdo kaj naredi:**
- **Claude:** programje naprave, namestitveno skripto, načrt ohišja (mere, razrez,
  datoteke OpenSCAD za tiskane dele), vezalno shemo, navodila sestave, postopek
  umerjanja in preizkusa, preizkus programja z lažno kamero.
- **Lastnik:** nakup delov, izdelava ohišja, sestava, zapis kartice SD in fizični
  preizkusi. Claude fizične naprave ne more preizkusiti; vsak preizkus ima opisan
  postopek in pričakovan izid, lastnik pa poroča, kaj vidi.

| Korak | Kaj |
|---|---|
| 8.1 | Programje (Python 3.11, Raspberry Pi OS Lite 64-bit Bookworm): `postaja/naprava/` — `kamera.py` (picamera2), `zaznava.py`, `posiljanje.py`, `mehanika.py`, `signali.py`, `glavno.py` (stroj stanj), `nastavitve.toml`. Odvisnosti iz paketov Debian (`python3-picamera2`, `python3-opencv`, `python3-gpiozero`, `python3-requests`, `python3-luma.oled`), čim manj iz pip in s točnimi različicami. |
| 8.2 | Preizkusi brez naprave: `pytest` z lažno kamero (slike iz mape: prazen pladenj, listek, obrnjen listek, zamegljen, listek brez QR) in `gpiozero` MockFactory. Pokrijejo stroj stanj, vrsto brez signala, dvojnike, odziv na 401/422/5xx. **Za lokalni zagon je potreben Python na lastnikovem računalniku (odločitev 3).** |
| 8.3 | Namestitev: `postaja/namestitev/namesti.sh` (paketi, uporabnik `postaja` brez sudo, storitev systemd z `Restart=always`, samodejne varnostne posodobitve, brez odprtih vrat, SSH izklopljen ali samo s ključem). Prvi zagon prebere `postaja.toml` z zagonske particije (FAT, vidna v Windowsih), premakne žeton v `/etc/turnirko-postaja/zeton` (0600) in datoteko z zagonske particije izbriše. Omrežja Wi-Fi (dvorana, organizatorjev telefon) iz iste datoteke prek `nmcli`. |
| 8.4 | Ohišje in mehanika: `postaja/ohisje/` — `ohisje.scad` (parametrično: nosilec kamere, pladenj z osjo, sklopka servomotorja, vodila), seznam razreza plošč, vezalna shema, `NAVODILA-SESTAVE.md` z umerjanjem (višina in fokus kamere, osvetlitev, koti servomotorja) in prevzemnim preizkusom (razdelek 5.6). |
| 8.5 | Prototipi: H1 → H2 → H3 (razdelek 5.7). |

## 5. Postaja za zajem — zasnova škatle

### 5.1 Kako se uporablja

Organizator vzame listek od sodnika in ga s pisavo navzgor potisne v režo, z
vrhom ali dnom naprej (oboje deluje). Po ~1 s se zasliši kratek pisk:
- **zeleno:** listek zdrsne v predal;
- **rdeče:** listek zdrsne nazaj iz reže, na zaslonu je razlog (»Obrni listek«,
  »Ne vidim kode«, »Zamegljeno – vstavi znova«).

Vrsta na prenosniku se polni sama; organizator potrjuje, ko utegne.

Vsi listki so v predalu, urejeni po času. To je tudi fizični arhiv in rezerva, če
strežnik ali omrežje odpove.

### 5.2 Prerez (pogled s strani)

```
        ┌───────────────────────────┐
        │  Raspberry Pi, napajanje  │  elektronika (~5 cm)
        │  zaslon OLED (spredaj)    │
        ├─────────────┬─────────────┤
        │          [kamera]         │
        │      LED trak + difuzor   │  temna komora: zunanja luč ne vpliva,
        │                           │  vsaka fotografija je enaka
        │            ~16 cm         │
        │                           │
 reža → ══════[ pladenj ]═════════│  pladenj (črn mat pleksi), os ob reži
  lučka ●     ↙ sprejet: zadnji    │  rdeče: zadnji rob gor ~35° → listek nazaj skozi režo
              rob dol ~50°         │  zeleno: zadnji rob dol ~50° → listek v predal
        │                           │
        ├───────────────────────────┤
        │        PREDAL             │  ~9 cm, ~500 listkov
        └───────────────────────────┘
```

### 5.3 Mehanizem (zakaj tako)

- **Nagibni pladenj z eno osjo ob reži** namesto valjev, ki bi listek vlekli kot
  bankomat. Valji se zatikajo, rabijo motor z gonilnikom in natančno izdelavo. Pladenj
  rabi en servomotor in gravitacijo.
- **Os leži ob sprednjem robu pladnja, na višini reže.** Servomotor jo vrti v dve
  smeri:
  - **sprejem:** zadnji rob se spusti za ~50°, listek zdrsne po pladnju in pade za
    njim v predal. Razdalja zadnjega roba do zadnje stene se pri tem odpre na ~40 mm;
  - **zavrnitev:** zadnji rob se dvigne za ~35°, listek zdrsne naprej skozi isto režo
    na zunanjo polico. Organizator ga vzame v roke.
- **Konec vstavljanja** je zadnja stena škatle (listek do nje), ne del pladnja. Zato
  pri nagibu nič ne zadene ob omejilo.
- **Papir na gladkem pleksiju zdrsne že pri ~20–25°**, zato sta 35° in 50° varna. Na
  koncu nagiba servomotor pladenj rahlo strese (±5°) proti listku, ki se zaradi statike
  prime.
- **Stranski vodili** listek usredinita in pritisneta robova. Prekrijeta največ 4 mm
  roba, oznake so 7 mm od roba.
- **Mere (okvirno, potrdi H1):**
  - pladenj 110 × 102 mm (listek 105 × 99);
  - reža 112 × 5 mm s poševnim vhodom;
  - notranjost ~145 (š) × 110 (g) mm;
  - zunaj ~16 × 15 × 38 cm.

### 5.4 Kamera, svetloba, zaznava

- **Kamera Raspberry Pi Camera Module 3, navadna leča (ne »Wide«).**
  - Vidno polje 66° × 41° pomeni, da mora biti kamera ~16 cm nad pladnjem, da
    krajša stranica slike pokrije listek z robom.
  - To da ~22 px/mm (bralnik rabi 10).
  - Široka leča bi skrajšala škatlo, a ima izkrivljenje (sod), ki ga homografija ne
    popravi.
- **Fokus in osvetlitev sta fiksna,** nastavljena enkrat pri umerjanju (razdalja se ne
  spreminja). Brez lovljenja fokusa je zajem hitrejši in vsaka slika enaka.
- **Svetloba:** LED trak 5 V, 4000 K, CRI ≥ 90, usmerjen v belo steno ali skozi
  opalni difuzor. Razpršena svetloba odstrani odsev kemičnega svinčnika in toneria.
- **Zaznava** (Raspberry Pi, nizka ločljivost 5–10× na sekundo):
  - prazen pladenj je črn;
  - listek = velika bela ploskev, ki 0,3 s miruje → zajem polne ločljivosti;
  - nato lokalna preverba: QR (OpenCV), tri oznake, ostrina;
  - z zadnje strani obrnjen listek nima oznak → zavrnitev »Obrni listek«.
- **Pošiljanje:** JPEG ≤ 2600 px (~25 px/mm na listku je dovolj), kakovost 0,88,
  ~500 kB.

### 5.5 Elektronika in deli (okvirne cene)

| Del | Okvirno € |
|---|---|
| Raspberry Pi 4 Model B 2 GB (odločitev 1; Pi Zero 2 W ~20 € je cenejši in počasnejši; Pi Zero 2 W in Pi 5 rabita kabel kamere 22 → 15 pinov, ~3 €) | 50 |
| Raspberry Pi Camera Module 3 (navadna) | 27 |
| microSD 32 GB A1 (SanDisk ali Samsung) | 8 |
| Napajalnik 5 V / 5 A + razdelilnik: ena veja USB-C za Pi, druga servomotor in LED (kondenzator 1000 µF, skupna masa) | 20 |
| Servomotor MG90S (kovinski zobniki) | 5 |
| LED trak 5 V COB 4000 K CRI ≥ 90 (~40 cm) + opalni pleksi 2 mm | 13 |
| Zaslon OLED 0,96" SSD1306 (I²C) | 5 |
| RGB LED + 3 upori 220 Ω, aktivni brenčač | 3 |
| Os: jeklena palica 4 mm + dve puši (tiskani ali bronasti) | 5 |
| Ohišje: vezana plošča 6 mm (~0,5 m²), črn mat pleksi 3 mm za pladenj, vijaki, distančniki M2.5, ročaj predala | 25 |
| Kabli, sponke, termoskrčka | 8 |
| **Skupaj** | **~170** (s Pi Zero 2 W ~140) |

**Zakaj ločeno napajanje servomotorja:** Pi 4 ob obremenitvi porabi ~2 A,
servomotor ob zagonu sunkovito do 1 A. Skupno napajanje skozi Pi bi povzročilo padec
napetosti, ponovni zagon in poškodovano kartico SD.

**Priključki GPIO:**

| Signal | GPIO |
|---|---|
| Servomotor (strojni PWM) | 18 |
| RGB LED | 5, 6, 13 |
| Brenčač | 12 |
| OLED (I²C) | 2, 3 |

### 5.6 Programje naprave: stroj stanj in varnost

**Stroj stanj:**

```
ZAGON → (žeton veljaven? /postaja/stanje) → PRAZNO
PRAZNO → listek prisoten in miruje 0,3 s → ZAJEM → PREVERBA
PREVERBA ok           → nagib »sprejem«, zelen pisk, pošlji v vrsto → PRAZNO
PREVERBA brez QR, a ostro in oznake vidne → isto kot ok (organizator izbere tekmo)
PREVERBA ni oznak / zamegljeno / obrnjeno → nagib »nazaj«, rdeč pisk, razlog na OLED → PRAZNO
NI_POVEZAVE: vrsta na kartici, modra lučka utripa, zajem teče naprej
```

**Vrsta na kartici:**
- mapa `/var/lib/turnirko-postaja/vrsta/`;
- vsak posnetek ima ključ idempotence;
- izbris po uspešnem pošiljanju, najkasneje po 24 h;
- največ 500 posnetkov, nato zavrnitev z razlogom »Vrsta polna«.

**Varnost naprave:**
- Samo odhodni HTTPS s preverjanjem certifikata.
- Naslov strežnika je zapisan v `nastavitve.toml`, ne v QR ali drugem vhodu, zato ga
  ponarejen listek ne more preusmeriti.
- Ni odprtih vrat. Storitev teče kot neprivilegiran uporabnik. Žeton je v datoteki
  0600.
- Ob kraji ali izgubi lastnik žeton takoj prekliče na strani »Postaje«. Žeton zna samo
  naložiti fotografijo v turnirje lastnika, ki tečejo; brati ne zna ničesar.
- Izpad elektrike: koren je samo za branje (overlayfs), vrsta je na ločeni pisljivi
  particiji. Posnetek, ki še ni bil zapisan v vrsto, se izgubi, listek pa je v
  predalu.

**Prevzemni preizkus** (lastnik, po sestavi):
1. 50 listkov zapored, vstavljenih z vrhom in z dnom naprej: vsi v predalu, vsi na
   prenosniku, nobenega dvojnika.
2. 5 obrnjenih listkov: vsi vrnjeni z razlogom.
3. 3 prazni listki brez kode: sprejeti, tekmo izbere organizator.
4. Izklop Wi-Fi pri 10 listkih, nato vklop: vsi prispejo natanko enkrat.
5. Izklop elektrike med delom, nato vklop: naprava se sama zažene, vrsta je cela.
6. 3 prepognjeni listki: zaznani ali vrnjeni, nikoli zatikanje.
7. Čas od vstavitve do piska ≤ 2 s.

### 5.7 Prototipi

- **H1 — kartonska škatla (en vikend).** Pi + kamera + LED. Pladenj ročno (brez
  servomotorja), listek se vzame z roko. Preveri višino kamere, osvetlitev, zaznavo
  in kakovost slik na resničnih listkih. Te slike gredo v umerjanje in v lažno kamero
  testov.
- **H2 — vezana plošča + tiskani deli.** Servomotor, pladenj, predal, zunanja polica,
  OLED, lučka, brenčač. Prevzemni preizkus iz 5.6.
- **H3 — dodelava po prvem turnirju.** Ročaj, napajanje iz baterije (USB-C PD,
  ≥ 20.000 mAh, pod pogojem, da zmore 5 V / 3 A), napisi na ohišju.

## 6. Vrstni red in odvisnosti

| Kdaj | Faze |
|---|---|
| **Takoj** (lastnik, vzporedno) | Naročilo delov za H1 (Pi, kamera, kartica, LED, napajalnik), da so do F8 doma. |
| **Pred prvim turnirjem z branjem** | F0 → F1 → F2 (2.1, 2.2, 2.5–2.7) → F4 → F5. F3, če je pred turnirjem klubski trening. 7.1 (telefon na stojalu) za preizkus poteka. |
| **Prvi turnir (F9, korak 1)** | Zbiranje zlatega nabora in meritev. |
| **Po prvem turnirju** | F2 (umerjanje β, 2.3, 2.4 na resničnih podatkih) → F6 → F7 (7.2–7.3) → F8 (H1 → H2). |
| **Po meritvah, ki izpolnijo merila iz razdelka 1** | F9, koraki 2–3. |

Migracije: zaporedno od V69 (F1: sled pregleda, kontrola, zlati nabor; F3: vadnica;
F4: poravnana slika; F5: idempotenca; F6: močne oznake; F7: postaja). Številko
preveri ob začetku vsake faze (razdelek 2).

### F9 — Uvedba na turnirjih

| Korak | Kako | Merilo za naprej |
|---|---|---|
| 1. V senci | Fotografira se vsak listek in vsak se pregleda v celoti. Drugi organizator je še zraven za varnost. | 150–300 listkov v zlatem naboru, meritve F1. |
| 2. En dotik | Zanesljivi listki z enim dotikom, kontrole tečejo. | Merila iz razdelka 1 na vsaj dveh turnirjih. |
| 3. Za vse | Izklop `samo-admin`, članek pomoči, kratek video. Meritve po organizatorjih. | Merila veljajo tudi za druge organizatorje. |
| 4. Morda | Samodejna potrditev izjemno zanesljivih listkov; organizator vidi samo izjeme. | Kontrole skozi mesece kažejo ~0 tihih napak. Posebna odločitev lastnika. |

**Kontrolni seznam za dan turnirja** (gre v pomoč):
- tisk listkov na 100 %, oznake polno črne (brez varčevanja s tonerjem);
- poskusni listek pred začetkom (fotografija ali postaja → pravilen predlog);
- omrežje: Wi-Fi dvorane ali telefon kot dostopna točka;
- postaja: zelena lučka, predal prazen;
- rezerva: ročni vnos deluje vedno, listki ostanejo v predalu.

## 7. Odprte odločitve (lastnik)

1. **Računalnik naprave:** Pi 4 2 GB (priporočam: hitrejši, enostavnejši kabel
   kamere) ali Pi Zero 2 W (~30 € ceneje, počasnejši).
2. **Ohišje:** H1 karton; H2 vezana plošča, ročno ali laserski razrez (FabLab), mali
   deli tiskani 3D (naročilo ali FabLab). Kaj ti je dostopno?
3. **Python na tvojem računalniku** za preizkus programja naprave pred sestavo
   (namestitev prek `winget`), ali se programje preizkuša samo na Pi.
4. **Delež naključne kontrole:** priporočam 5 %.
5. **Zlati nabor:** trajna hramba izrezov celic s številkami, brez imen —
   **potrjeno 20. 11. 2026.** Še odprto: podnabor za regresijski test (F1.5) v
   repozitoriju (gre v zgodovino gita na GitHubu, teče v CI) ali zunaj njega (mapa
   `Desktop\Turnirko\`, test teče samo lokalno in se brez mape preskoči).
6. **Besedilo zasebnosti** (F0.5 in dopolnitev F1) — **potrjeno 20. 11. 2026.**
7. **Datum naslednjega turnirja,** na katerem bi preizkusila (določi, kaj gre pred
   njega).
8. **Klubski trening za vadnico** (F3.5): kdaj?

## 8. Dnevnik izvajanja

| Faza | Stanje | Datum | Commit |
|---|---|---|---|
| F0 | v produkciji od 21. 11. 2026 | 20. 11. 2026 | `d774ce0` |
| F1 | v produkciji od 21. 11. 2026 | 20. 11. 2026 | `d774ce0` |
| F3 | v produkciji od 21. 11. 2026 (lastnik: »kar zdaj naredi vadnico«, pred F2; 20 listov, ~3500 števk odobrenih 21. 11.) | 20. 11. 2026 | `794470f` |
| F2 (2.1, 2.2, 2.5–2.7) | v produkciji; 2.3, 2.4 in umeritev β po prvem turnirju | 21. 11. 2026 | `7cd3289` |
| F4 | v produkciji | 21. 11. 2026 | `7cd3289` |
| F6 | v produkciji od 21. 11. 2026 20.15 (lastnik: generalka + F6 + F5; prej pregledano na testu) | 21. 11. 2026 | `66dddae` |
| F6.6 napake mreže + meja MNIST | v produkciji od 21. 11. 2026 (lastnik po prvem učenju: »katere cifre ga matrajo«; test in produkcija v istem zagonu po lastnikovem »objavi na test in produkcijo«) | 21. 11. 2026 | `de9dd42` |
| F3.6 nalaganje slik vadnice | objava na test in v produkcijo 22. 11. 2026 (lastnik: kolegi bodo liste fotografirali in poslali; »test in produkcija«) | 22. 11. 2026 | — |
| F5, F7–F9 | načrtovano | — | — |

**F0 — odstopanja od načrta in dodatno:**
- Meja fotografije je 6000 px / 20 Mpx (ne 4096 / 16 Mpx). Pri 16 Mpx bi bila zavrnjena
  nepomanjšana 16-Mpx fotografija telefona (4624 × 3472 = 16,05 Mpx).
- Varnostni pregled (podagent) je našel še:
  - tekmo med potrditvijo in zaključkom branja → potrditev, zavrženje in ponovno
    branje so pod istim zaklepom;
  - zapiranje dvojnikov je upoštevalo tudi branja, ki tekme niso določila po kodi →
    samo koda;
  - en organizator je lahko zasedel obe dekodiranji → pas semaforjev po uporabniku;
  - nenatančno besedilo zasebnosti → popravljeno.
- Starejša napaka iz V68: `CallerRunsPolicy` bi ob polni vrsti branje izvedel v
  `afterCommit` in zapis izgubil → `AbortPolicy`, vrsta 10.000.
- Med preizkusom v brskalniku je konzola ujela podvojen ključ React v tabeli »V
  številkah« (dve vrstici »30 dni«) → ključ je cela vrstica.
- `/code-review` (high) je našel še:
  - napačen razlog, ko se umakne novejša neuspela fotografija → razlog »velja
    starejša«;
  - branje, ki bi ob napaki zapisa ali zavrnjeni nalogi obviselo v V_VRSTI → napaka
    gre v NAPAKA, obviselo branje (> 2 min) ima gumb »Preberi znova«;
  - napako v nočnem opravilu je druga zakrila → opravili sta ločeni.
  - Namerno pustil: globalni zaklep čez potrditev (enostavnost, en primerek);
    neuspeli poskusi se ne štejejo v omejitev (pas uporabnika jih omeji na enega
    hkrati); dvojna preverba dostopa (namerno, pravica se lahko vmes odvzame).
- Učenje se brez vsaj 50 preizkusnih števk odloži brez zapisa (prej bi se ob prazni
  preizkusni množici sprejelo samo po MNIST).
- Preverjeno:
  - testi: 1377 zaledja pred popravki in vsi po njih, 318 vmesnika, lint brez novih
    opozoril, build;
  - pravo zaledje s testno bazo (pg18): štiri fotografije istega listka → odprta
    ostane samo najnovejša; potrditev nadomeščene vrne 409 z razlogom; potrditev
    najnovejše konča tekmo 3:1 in zapiše 15 števk za učenje; v dnevniku zaledja ni
    napak;
  - stran zasebnosti v brskalniku brez napak v konzoli.

**F1 — odstopanja od načrta in dodatno:**
- Migracija V69: `branje_listka` dobi `kontrola`, `pripravljen`, `cas_pregleda_ms`,
  `nacin_potrditve` (in indeks po `ustvarjeno_ob`), nova tabela `zlati_listek`.
- **Čas pregleda** se meri od poznejšega od prvega prikaza listka in zadnje potrditve
  ali zavrženja (ne od prikaza): pri listkih, potrjenih drug za drugim, je to čas na
  listek, čakanje med fotografiranjem ni šteto. Stran pokaže mediano (ne povprečja,
  kot piše razdelek 1): ena pozabljena potrditev povprečje pokvari.
- **»Potrdi vse« (F4)** še ne obstaja; kontrola zdaj skrije gumb »Potrdi« v vrsti.
- **Smer stolpcev** se ne shrani: listek s kodo je natisnjen v smeri tekme, pri
  ostalih velja smer z več pravilnimi celicami. Zlati nabor vzame samo listke s kodo.
- **Zlati nabor**: namesto zamenjave z verjetnostjo (»reservoir sampling«) vsak listek
  dobi naključen ključ in ostane 5000 z najmanjšimi - enak enakomeren vzorec brez
  števca videnih. Papir v celici se shrani kot belo (odšteta raven papirja), slika
  ima ~15 kB namesto ~700 kB. Meritev nabora teče v ozadju (minute), rezultat je v
  pomnilniku. Zip ima na koncu `konec.txt`.
- **Regresijski podnabor (1.5)** ni narejen: čaka na prve prave listke in na
  odločitev, ali gre v repozitorij ali v mapo zunaj njega (odločitev 5; trajna hramba
  nabora je potrjena).
- Varnostni pregled (podagent) je našel, popravljeno:
  - `pripravljen` je bil ocena bralnika brez opozoril tekme, kontrola pa je izbirala
    po predlogu z njimi (različni populaciji) → `pripravljen` = bi bil ponujen za en
    dotik;
  - »Preberi znova« je kontrolo žrebal znova (izogib kontroli) → kontrola ostane;
  - v naboru je ostal tudi 4-px rob celice ob črti (ime, napisano čez črto) → samo
    notranjost, največ 9 vrstic;
  - resnica nabora pri listku brez kode je bila v smeri, ki jo izbere mreža → samo
    listki s kodo;
  - prazne vrstice za zadnjim nizom so napihnile »prav« → ne štejejo;
  - `Error` v meritvi nabora je pustil stanje »merim«, zavrnjena naloga je izbrisala
    prejšnji rezultat → popravljeno;
  - skrajni datumi so dali 500 → leta 2020–2100, sicer 400;
  - prekinjen zip ni bil prepoznaven → `konec.txt`.
  - Ostalo dokumentirano: zlati listek preživi izbris branja; ob popravku izida se
    resnica ne popravi (F6.1).
- `/code-review` je našel, popravljeno: zastavica »meritev teče« je ob napaki baze
  ostala nastavljena; čas ročno vpisanih (neuspelih) listkov ni štel; »Preberi znova«
  ni ponastavil časa prikaza; kontrola je delala poizvedbe pred žrebom pod zaklepom;
  podvojena ocena meril v vmesniku. Namerno pustil: »brez napake« v naboru šteje tudi
  napačen Rezultat (strožje, dokumentirano); izbira smeri pri listkih brez kode v
  meritvi branj (redki, dokumentirano); dve podobni poizvedbi za meritev.
- Besedilo zasebnosti (F0.5) je dopolnjeno z naborom (»pri delu listkov vse celice s
  točkami skupaj«, »število je omejeno«). Lastnik ga je potrdil 20. 11. 2026, skupaj
  s trajno hrambo nabora (odločitev 5).
- Preverjeno:
  - testi: zaledje 1405 (tudi po popravkih pregledov), vmesnik 322, lint brez novih
    opozoril, build;
  - pravo zaledje s testno bazo (pg18), brezglavi Chrome na 390 px: potrditev z enim
    dotikom zapiše 2,8 s in `EN_DOTIK`; kontrola (delež 1) skrije »Potrdi«, pokaže
    opozorilo v vrsti in oknu, popravek v oknu zapiše `kontrola`, `popravljeno`,
    4,0 s in `OBRAZEC`; stran meritev pokaže natanko ročno izračunane številke
    (4 potrjeni, 3 brez popravka, 39 + 1 celic, 1 kontrola z napako, mediana 2,8 s,
    p90 4,0 s); meritev nabora 2 listkov; zip z `konec.txt` in brez povezave na
    branje; shranjena slika ima samo ročno napisane številke; brez napak v konzoli
    in dnevniku, brez vodoravnega pomikanja.

**F3 — odstopanja od načrta in dodatno** (lastnik je vadnico zahteval pred F2, da jo
deli že zdaj; migracija V70):
- **Id lista izbere brskalnik** ob tisku (deset naključnih mest), ne `POST
  /api/v1/vadnice` s strežniškimi id-ji: list, natisnjen na testnem okolju, tako velja
  tudi v produkciji (baza testa se ob osvežitvi prepiše), tisk ne potrebuje strežnika.
  Zapis lista nastane ob prvi fotografiji. Meja *n* ≤ 50 velja za stran tiska.
- **Samo admin** (načrt: admin ali organizator): branje je še samo za admina, liste
  deli lastnik. Ko se izklopi `samo-admin` (F9.3), se vadnica odpre organizatorjem.
- **Brez stolpca `vir`** v `stevka_listka`: vir je `id_vadnica` (ni NULL) ali
  `id_branje`; CHECK prepove oboje. Dodana `celica` (okence) za pregled po vrsticah.
- **Shrani se poravnan list s samimi okenci** (kot zlati nabor, ~0,4 MB), da se števke
  ob boljšem razrezu (F2.3) izrežejo znova; fotografija se ne hrani.
- **Odobritev nastavi čas števk na čas odobritve**, da štejejo kot nove za nočno
  učenje (meja 200 novih števk).
- Simulacija je ujela dve napaki iskanja okenc, preden je šel list v tisk: tanka črta
  (CSS 0,3/0,4 mm se pri tisku zaokroži na 0,26 mm) se na fotografiji raztrga, poteza
  »1« ob robu pa je polna, zato je bralnik »1« vzel za rob in jo odrezal (iz »10« je
  nastala razpolovljena ničla z oznakama 1 in 0 — zastrupljen učni primer). Popravek:
  okenca so SVG s črto 0,45 mm, rob je črta, najbližja pričakovani legi, zlita črta se
  odreže le v debelini črte; test preveri lego vseh okenc.
- Pomoč: vadnica je samo za admina, zato nima članka (pravilo pomoči velja za funkcije
  organizatorja); navodila so na strani sami.
- Preverjeno:
  - `VadnicaTest` (8), `VadnicaDostopTest` (2), `vadnica.test.ts` (5);
  - pravo zaledje s testno bazo (pg18), brezglavi Chrome: tisk 2 listov (PDF: dve strani
    A4, oznake in okenca na 0,1 mm natančno), na telefonu (390 px) fotografija
    simuliranega lista → »180 števk, čaka na pregled«, fotografija listka tekme →
    »To je listek tekme«; pregled po vrsticah, izločitev dveh števk, odobritev →
    178 števk v učenju, ponovna odobritev 409; shranjena slika ima samo okenca;
    brez napak v konzoli in brez vodoravnega pomikanja.

**F2 — odstopanja od načrta in dodatno** (21. 11. 2026; narejeni koraki 2.1, 2.2,
2.5, 2.6, 2.7, kot jih načrt zahteva pred prvim turnirjem; 2.3 in 2.4 ter umeritev β
na zlatem naboru po prvem turnirju):
- **2.7 najprej**, da je vsak naslednji korak izmerjen: tretja raven `NEUGODNO`
  (prepognjen listek z zamikom in senco ob pregibu, prst čez eno oznako, 5 %
  prečrtanih in prepisanih celic, polovica enic z nosom in prečrtanih sedmic) in
  `SimulacijaBralnikaPoskus` (120 listkov na raven, tretja raven še po vsaki težavi
  posebej; ni v zbirki testov, teče nekaj minut). Tekme simulacije po pogostosti iz
  zgodovine (`-Dzgodovina=true`), sicer bi bila pogostost (2.1) merjena na
  izmišljeni porazdelitvi.
- **2.6:** trojica oznak, ki ne ustreza QR, se zavrže — prej je bralnik ob prstu
  čez oznako izbral drug »kvadrat« in listek napačno poravnal (vseh 120 listkov s
  prstom neprebranih). Rezervna poravnava uporabi pet točk (dve oznaki + trije
  iskalni vzorci QR), ne šest: s tremi oznakami ostane dosedanja homografija iz
  štirih točk (brez spremembe za običajne listke). Namesto »lokalnega popravka po
  črtah« se črte in celice iščejo v vsakem stolpcu posebej in povežejo po bližini.
- **2.5:** meje popravljene celice so umerjene (`PopravljenaCelicaPoskus`): navaden
  zapis sega čez največ 28 % širine celice, popravljen čez najmanj 36 % → meja
  razpona 35 %; črnilo > 0,12 in več kot tri skupine potez za prepis čez vrednost.
  Celica z več kot 40 deli se ne obdeluje naprej (varnost: združevanje skupin je
  kubično) in je popravljena.
- **2.1:** pogostost šteje `niz` tekem turnirjev (`tekma_srecanja` lige ni vključena;
  listki so za turnirje). Vgrajena porazdelitev iz kopije produkcije, ker testna
  baza in nove namestitve nizov nimajo.
- **Časovna meja 5 s** iz varnostnega razdelka ni potrebna, dokler ni 2.3: petkratno
  branje je omejeno (≤ 12 celic × 2 razlagi × 2 števki × 5 različic).
- Meritev (120 listkov na raven, tekme po zgodovini; prej = commit `794470f`):

  | Raven | prej: neprebranih / prav / tihe / brez popravka | F2 |
  |---|---|---|
  | običajne | 0 / 99,90 % / 0,10 % / 78 | 0 / 99,80 % / **0 %** / 81 |
  | zahtevne | 0 / 99,03 % / 0,39 % / 26 | 0 / **99,23 %** / **0,19 %** / 28 |
  | neugodne (vse težave) | **60** / 91,6 % / 1,00 % / 0 | **1** / 93,7 % / **0,30 %** / 2 |
  | samo prst | **120** / — | **4** / 99,5 % / 0 % / 33 |
  | samo pregib | 0 / 97,4 % / 0,72 % / 24 | 0 / 97,5 % / 0,72 % / 24 |
  | samo popravki | 0 / 94,6 % / 0,51 % / 19 | 0 / 95,6 % / 0,40 % / 20 |
  | samo evropske | 0 / 98,0 % / 0,41 % / 0 | enako |

  Po korakih (brez enega koraka, ostalo F2): brez zgodovine (β = 0) zahtevne 98,94 %
  in tihe 0,29 %, neugodne 90,0 %; brez petkratnega branja zahtevne 99,03 % in tihe
  0,39 %, neugodne tihe 0,70 %. Petkratno branje pri običajnih eno tiho napako
  spremeni v označeno in eno pravilno celico v označeno napako (1 od ~980 celic).
  Na 400 listkih (običajne / zahtevne, z njim : brez): prav 99,85 % : 99,82 % in
  99,01 % : 98,99 %, tihe 0 % : 0,03 % in 0,18 % : 0,21 %, brez popravka 269 : 260 in
  118 : 121 (razlika v šumu). Čas branja listka zaporedno 0,39 s (meja 1 s).
  **Izbira β** (120 listkov, prav / tihe; običajne · zahtevne · neugodne):

  | β | tekme po zgodovini | vsi izidi enako pogosti |
  |---|---|---|
  | 0 | 99,80/0 · 98,94/0,29 · 90,0/0,30 | 99,70/0 · 98,50/0,10 · 91,2/0,62 |
  | 0,3 | 99,80/0 · 99,23/0,29 · 93,2/0,30 | 99,60/0 · 98,30/0,10 · 91,9/0,62 |
  | **0,5** | 99,80/0 · 99,23/**0,19** · **93,7**/0,30 | 99,60/0 · 98,40/0,10 · 92,4/0,82 |

  Na resničnih tekmah je 0,5 najboljša v vseh merilih; na izmišljeni enakomerni
  porazdelitvi (redki 11:0, 11:1 so tam pogosti) je za 1 celico slabša in vse te
  napake so označene. Privzeta ostane 0,5, umeri se na zlatem naboru.
  Neprebrani listki s prstom: prst čez
  rob same tabele (spodnji desni kot) – sporočilo zdaj omeni tudi prst.
- **Ostaja odprto (pravi listki, generalka):**
  - **evropska enica in sedmica**: mreža MNIST enico z nosom bere kot 7; pravila
    vrednost popravijo, a celico označijo (0 od 120 listkov brez popravka, ko so vse
    enice evropske). Rešitev je učenje iz vadnice (F3), ne bralnik.
  - **prepognjen listek**: tihe napake ostanejo 0,72 % (4 od 120 listkov), ko
    zapis leži na črti upognjene tabele in se stolpec zamakne za vrstico. Ali je to
    v dvorani težava, pokažejo pravi prepognjeni listki; potem lokalni popravek po
    črtah (2.4).
  - umeritev β na zlatem naboru (F1) po prvem turnirju.

**F4 — odstopanja od načrta in dodatno** (21. 11. 2026, V71):
- **Izrez ni ob polju, ampak pod vrstico niza:** stolpca točk v obrazcu sta široka
  4,5 rem (~70 px), celica listka je 35 mm — izrez ob polju bi imel števke visoke
  ~10 px. Zato je pod vsako vrstico pas čez vso širino obrazca z izrezoma obeh
  celic, negotova celica čez vso širino (~6 px/mm).
- **Lega celic v `branje_listka.celice`**, ne v `prebrano`: seznam jo potrebuje
  brez nalaganja slik, `PrebranListek` pa ostane zapis bralnika.
- **Gumb »Listek« se pokaže vsem urejevalcem turnirja**, ko je branje vklopljeno;
  ali fotografija obstaja, pove šele klik (brez dodatne poizvedbe ob vsakem oknu).
- **»Potrdi vse zanesljive« od dveh listkov naprej** (en listek ima svoj gumb).
- Pregled F2 (/code-review) pred F4: predlog zdaj primerja število prebranih vrstic s
  številom nizov tekme (prst čez celico Rezultat bi sicer zadnji niz tiho vzel za
  Rezultat); meritev zlatega nabora uporablja vgrajeno pogostost; β zunaj 0–1 se
  zavrne.
- Preverjeno:
  - zaledje: `BranjeListkovStoritevTest` (poravnava in lega celic, ponovno branje,
    listek tekme: pred potrditvijo ni, lastnik in admin da, tuj organizator 403,
    brez fotografije 404), `SimuliraneFotografijeTest.poravnanListekZaIzrezCelic`,
    `MeritevBranjaDostopTest` (gost 401 na obeh poteh); vmesnik: izrez, ozadje,
    vrstni red (`branjeListkov.test.ts`);
  - pravo zaledje s testno bazo (pg18, V71 uveljavljena), brezglavi Chrome na 390 px
    in 1280 px: dve simulirani fotografiji (T-3 zanesljiv, T-1 z negotovo celico) →
    T-3 na vrhu, kode T-3 in T-1; pregled T-1 z osmimi izrezi, negotov povečan; T-3
    potrjen z enim dotikom; v oknu tekme »Listek« pokaže fotografijo; brez napak v
    konzoli in brez vodoravnega pomikanja; z dvema zanesljivima listkoma (T-3, T-1)
    »Potrdi vse zanesljive (2)« potrdi oba (vrsta prazna, oba 3 : 1 med zaključenimi).
    Popravljeno ob preverbi: gumb--majhen je glavnemu gumbu vzel barvo besedila
    (lasten razred `branje__potrdi-vse`); desni izrez pod povečanim levim stoji desno.

**F6 — odstopanja od načrta in dodatno** (21. 11. 2026, V72; lastnik je po 20
odobrenih vadnicah izbral generalko, F6 in F5):
- **Učenje je ločeno v `UcenjeMreze`** (nočno, »Uči zdaj«, vrnitev, hramba, sprejem);
  `UcenjeIzListkov` samo zbira števke (potrditev, popravek izida).
- **Zaklep samo v pomnilniku** (zastavica `tece`, ne v bazi): aplikacija teče v enem
  vsebniku, zaklep v bazi bi do 30 min držal povezavo iz bazena. Vrnitev prejšnje vzame
  isto zastavico, zato se ne more prepletati s sprejemom nove mreže. Premor 10 min med
  zagoni velja tudi po nočnem učenju.
- **»Uči zdaj« potrebuje enako novih števk (200) kot nočno:** ponovni zagoni na istih
  števkah bi z izbiranjem po šumu prej ali slej »premagali« staro mrežo na istem
  preizkusnem delu. Brez dovolj števk gumb pove, koliko jih manjka.
- **Popravek izida → dogodek** `IzidTekmePopravljen` (Spring, po commitu), da storitev
  tekme ne pozna branja listkov. Edina pot, ki spremeni izid končane posamične tekme, je
  `popraviRezultat` (izbris izidov pozneje igranih tekem pri zamenjavi zmagovalca oznak
  NE spremeni: papir tistih listkov ostane, kar je bil). Popravek, ki spremeni več kot 2
  celici (ali število nizov, poseben izid), pomeni drug listek: števke se samo izbrišejo.
- **Števka listka ima celico** (stolpec `celica` iz V70): popravek izida zato ohrani
  močne oznake iz potrditve in pozna celice, ki jih je spremenil.
- **Zlati nabor pri sprejemu:** prvih 400 listkov (nabor je naključen vzorec, zato tudi
  to; pri isti velikosti nabora vedno isti listki), odloča od 20 listkov naprej; merilo
  so napačne celice (stara in nova mreža na istih listkih).
- **Stran:** mreža in učenje sta na vrhu strani Meritev branja (veljata za vse turnirje),
  vadnica ima povezavo nanjo. Članka pomoči ni (samo admin, pravilo pomoči velja za
  funkcije organizatorja).
- **Znani vrzeli (pregled /code-review, sprejeto):** (1) popravek oznak teče kot naloga v
  pomnilniku po commitu: ponovni zagon strežnika v tisti sekundi pusti napačne oznake
  (redko, samo kakovost učenja); (2) popravek upošteva vsa potrjena branja tekme, tudi
  listek pred zamenjavo zmagovalca z izbrisom izidov - njegove (pravilne) števke se
  izbrišejo. Obe vodita kvečjemu v izgubo učnih števk, nikoli v napačen izid.
- Preverjeno:
  - zaledje 1449 testov (+12): `UcenjeMrezeTest` (11: zapis z meritvami, zgodovina brez
    uteži, razlogi zavrnitve, zlati nabor pri sprejemu, prekinitev, delitev, odlog,
    vrnitev prejšnje + nalaganje ob zagonu, »Uči zdaj« pove zakaj + zaklep, samo admin,
    hramba), `UcenjeIzListkovTest` (močne oznake in celice, razlika po celicah,
    popravek izida zapiše oznake znova + zlati nabor, popravek na drug listek izbriše),
    `MeritevBranjaDostopTest` (gost 401, organizator 403 na novih poteh); vmesnik 332
    testov, lint 0 napak, gradnja;
  - pravo zaledje s testno bazo (pg18, V72 uveljavljena), brezglavi Chrome na 390 px in
    1280 px: »Uči zdaj« (909 števk, 19 s) → različica zavrnjena z razlogom »Na
    preizkusnih števkah slabša: 100,00 % → 99,39 %«, mreža ostala vgrajena, gumb v
    premoru; sprejeta različica se naloži ob zagonu, »Vrni prejšnjo« s potrditvijo →
    vgrajena, različica »Umaknjena«; brez napak v konzoli in brez vodoravnega pomikanja
    (zgodovina je zato seznam, ne tabela s sedmimi stolpci).

**F6.6 — napake mreže in meja MNIST** (21. 11. 2026, brez migracije; lastnik po prvem
učenju v produkciji, različica 1: preizkusne 64,4 % → 96,1 %, MNIST 99,5 % → 98,6 %,
»katere cifre ga matrajo najpogosteje«):
- **Napake mreže** (`NapakeMreze`, stran Meritev branja, na klik): tabela po števkah 0–9
  (vgrajena in različica v uporabi, najpogostejše zamenjave), galerija narobe prebranih
  preizkusnih števk s sliko in listom vadnice, sumljive oznake (učne, ki jih mreža po
  učenju bere drugače z ≥ 90 %), »Izloči« (izbris števke iz učenja). Pravila:
  `docs/pravila/branje-listkov.md`, »Napake mreže«.
- **Meja MNIST tudi od vgrajene** (−2 točki skupaj), ne samo od prejšnje različice
  (−1): prej bi lahko vsako učenje vzelo svojo točko.
- Na telefonu tabela pri primerjavi skrije stolpec »Števk« (pet stolpcev ne gre v 390 px).
- **Znani vrzeli (pregled /code-review, sprejeto):** (1) števka listka, izločena iz
  učenja, se vrne, če organizator pozneje popravi izid te tekme (popravek oznak izreže
  števke znova); (2) pregled naloži vse števke iz učenja (do 60.000) v zahtevi, brez
  zaklepa - pri sedanjih ~3500 števkah je to sekunda.
- Preverjeno: zaledje 1454 testov (`NapakeMrezeTest` 3, `UcenjeMrezeTest` +1 za zdrs
  MNIST, `MeritevBranjaDostopTest` +1 in DELETE 401/403); vmesnik 334 testov, lint 0
  napak, gradnja; pravo zaledje s pg18 in dvema listoma vadnice z namerno napačnimi
  oznakami, brezglavi Chrome 390 in 1280 px: vse 3 napačno označene preizkusne med
  napakami, napačne učne med sumljivimi (tudi pravi narobe izrezani okenci z
  vadnice iz F3), izbira števke omeji galerijo, »Izloči« odstrani kartico, slike
  narisane, brez vodoravnega pomikanja, brez napak v konzoli.

**F3.6 — nalaganje slik vadnice** (22. 11. 2026, brez migracije in brez spremembe
zaledja; lastnik: »kolegi, ki bodo izpolnili, bodo slikali liste in mi jih poslali«):
- Na `/vadnica` gumb »Naloži slike« poleg »Fotografiraj list« (brez `capture`: galerija
  na telefonu, datoteke na računalniku; več hkrati, ena za drugo). Sporočilo pove ime
  datoteke. Stolpec »Fotografiran« je zdaj »Naložen« (čas nalaganja).
- **Stisnjene slike:** simulirana fotografija 2400 × 3200, pomanjšana s povprečenjem in
  JPEG 70 %: 1600 in 1280 px vseh 140 okenc, mreža 98–100 %; 1000 px vseh 140 okenc,
  mreža 86–93 %; 800 px koda QR neberljiva. Stalni test pri 1280 px. Vmesnik ob
  neuspelem branju slike pod 1200 px prosi za izvirnik.
- Datoteka, ki je brskalnik ne odpre (HEIC v Chromu, PDF), je prej javila »Povezava s
  strežnikom ni uspela« (tudi na strani branja listkov); zdaj navodilo za JPG/PNG.
- Ni narejeno: povleci in spusti na računalniku, PDF iz optičnega bralnika (shrani kot
  JPG).
- Preverjeno: `VadnicaTest` (+1), vmesnik 336 testov, lint 0 napak, gradnja; pravo
  zaledje s pg18, brezglavi Chrome 1100 in 390 px, pet datotek naenkrat: izvirnik z
  dolgim imenom in 1280 px prebrana (180 števk), 525 × 700 px napaka QR + namig o
  velikosti, PDF s končnico .heic navodilo za JPG/PNG, že odobren list zavrnjen; brez
  vodoravnega pomikanja, brez napak v konzoli.
