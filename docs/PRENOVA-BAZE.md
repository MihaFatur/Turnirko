# Prenova podatkovne plasti: SQLite → PostgreSQL — 28. 10. 2026

Načrt selitve Turnirka z ene datoteke SQLite na PostgreSQL. Pregledani so
zaledje (entitete, repozitoriji, izvorni SQL, transakcije), migracije,
postavitev (`compose.yaml`, `Dockerfile`, kopije), vmesnik (osveževanje) in
produkcijska kopija s 27. 10. (`savinja-tour-uvoz/produkcija.db`). Dokument je
načrt; stanje izvedbe (faze 1–5 narejene, preklop čaka lastnika) je takoj
spodaj.

**Odločitve lastnika (28. 10.):**

- **namizna (portable) različica se opusti** — samo PostgreSQL, ena shema,
  ena zbirka migracij;
- **baza teče na istem VPS v Dockerju**, kot Umami danes;
- **cilj za 12 mesecev je regija**: do ~10 hkratnih vnašalcev, do ~500
  gledalcev hkrati na enem turnirju;
- **preklop z napovedanim izpadom ~30 minut**, brez sočasnega pisanja v dve bazi.

## Stanje izvedbe

Delo teče na veji **`prenova-baze`**, v ločeni kopiji repozitorija
`Desktop\Turnirko\turnirko-postgresql` (git worktree). Glavna mapa
`turnirko/` ostane na SQLite, ker jo hkrati uporabljajo druge seje in ker
produkcija do preklopa teče na njej.

| Faza | Stanje |
|---|---|
| 0 | ni začeta (neodvisna) |
| 1 | **koda in testi na PostgreSQL — 28. 10. 2026** (podrobnosti spodaj); ni commitano |
| 2 | **koda in testi — 29. 10. 2026**: `Zaklepi` (svetovalni zaklepi), zaklep ratinga + preračuna z doobračunom in varovalko (`OpravilaRatinga`), zaklep turnirja/lige pri vnosu, koda popusta (tudi plačljiva pot), kvota, domača stran, potrditvena koda, Stripe webhook, 409 za napake zaklepa, `V53_1__socasnost.sql` (delna UNIQUE na dnevniku); `SocasnostTest` 4/4 in vseh 987 testov zelenih; ni commitano |
| 3 | **orodje in vse štiri preverbe — 30. 10. 2026**: ukaz `prenos` (`si.turnirko.prenos`), poročilo zeleno na kopiji produkcije; ni commitano |
| 4 | **datoteke pripravljene — 30. 10. 2026**: `compose.yaml` (storitev `baza`), `skripte/baza/00-vloge.sh`, kopije, vaja obnove, obnova, stran Vzdrževanje, `OBJAVA.md`. **Z Dockerjem ni preizkušeno** (lokalno ga ni): skripte so preverjene sintaktično, SQL vlog na pravem PostgreSQL, YAML razčlenjen; prvi pravi zagon je generalka 2 na strežniku |
| 5 | **skripte in navodilo pripravljeni** (`skripte/preklop/`, `docs/PREKLOP.md`); generalka 1 (lokalno, brez Dockerja) opravljena; generalka 2 in preklop sta stvar lastnika (dostop SSH) |
| 6 | **seznam pripravljen** (spodaj); izvedba šele po dveh tednih brez težav po preklopu |

### Faza 1 — kaj je narejeno

- `pom.xml`: ven `sqlite-jdbc` in `hibernate-community-dialects`, noter
  `org.postgresql:postgresql`, `flyway-database-postgresql`; za teste
  `io.zonky.test:embedded-postgres` 2.2.2 z binarnimi datotekami **PostgreSQL
  18.6** in `junit-platform-launcher`.
- **`db/migration/postgresql/V53__izhodisce.sql`**: 34 tabel v vrstnem redu
  tujih ključev, s komentarji iz SQLite in seme `starostno_sidro` (70 vrstic,
  enakih produkcijski kopiji). Flyway 11.14 ga na PostgreSQL 18.6 izvede brez
  opozorila.
- **Primerjava strukture** (produkcijska kopija SQLite po V53 proti
  PostgreSQL po izhodišču, skripta po katalogu): vseh 371 stolpcev v istem
  vrstnem redu z enako obveznostjo in privzetki, 72 tujih ključev z enakim
  `ON DELETE`, 35 enoličnosti (z delnimi), 45 indeksov, 167 CHECK-ov — **nič
  razlik**. 23 logičnih CHECK-ov je postalo tip `boolean`; edina namerna
  razlika je privzetek `igralec_ni_duplikat.ustvarjen_ob`
  (`localtimestamp(3)` namesto `datetime('now')`).
- Preslikava tipov: 111 × `bigint`, 83 × `integer`, 23 × `boolean`, 7 ×
  `date`, 38 × `timestamp(3)`, 4 × `double precision`, 105 × `text`.
- `CasKotBesedilo` je zbrisan (z 28 polj), izvorne poizvedbe berejo čase prek
  `pomozno/CasBaze`; `RacunPrijavaRepozitorij` primerja `timestamp`, ne nize;
  `INSERT OR IGNORE` → `ON CONFLICT DO NOTHING`; podpoizvedba v
  `RacunDogodekRepozitorij` ima vzdevek.
- **Razvrščanje kot v SQLite**: SQLite ima `NULL` za najmanjšo vrednost,
  PostgreSQL za največjo, zato srečanja lige po terminu nosijo `NULLS FIRST`
  (srečanje brez termina ostane v kolu prvo). Izenačitve: seznam turnirjev je
  bil urejen samo po `ustvarjen_ob` (386 turnirjev, 7 različnih vrednosti —
  SQLite je izenačene vrnil po id-ju, PostgreSQL bi jih v poljubnem redu), zdaj
  `ustvarjen_ob DESC, id`; enako dodan id v dveh poizvedbah zmagovalcev in v
  časih obračunanih tekem.
- Namizna različica je odšla (`NamizniNacin`, `application-namizni`,
  `admin/admin` v `ZacetniAdmin`, navodila v README in OBJAVA.md); V11 v
  `VARNOSTNI-PREGLED.md` je zaprt. `TurnirkoAplikacija.pripraviMapoBaze` ven.
- Testi: `TestnaBaza` (poslušalec seje JUnit) zažene PostgreSQL enkrat na
  testni zagon in ustvari bazo z istim stavkom kot strežnik (`C.UTF-8`);
  testni profil ima `ddl-auto=validate`. Trije testi načrtov poizvedb SQLite
  (`NacrtiDnevnikaTest`, `NacrtPoizvedbe`, en test v `LoceneLestviceTest`) in
  `TurnirkoAplikacijaTest` so odšli z kodo, ki so jo varovali. V SQL testov:
  `INSERT OR IGNORE`, `brez_boja = 1` in čas kot niz.
- **Izid: 987 testov, vsi zeleni** na PostgreSQL 18.6 (prvi zagon: 979
  zelenih, 8 napak — vse v SQL znotraj testov). Polni testni zagon traja
  ~2,5 min.

### Faza 1 — odstopanja od načrta in kar ostane

- **Paketno pisanje** (`batch_size`, `order_inserts`, `reWriteBatchedInserts`,
  razdelek 3.4) ni vklopljeno: spremeni vrstni red stavkov ob izpiranju, korist
  pa se pokaže šele pri preračunu. Meritev v fazi 3: preračun je na PostgreSQL 22 %
  počasnejši (639 s proti 523 s), zato ostane **nevklopljeno**, dokler ni potrebe.
- **`timestamp(3)`** in ne `timestamp(6)`: JSON nosi največ tri decimalke
  sekunde, kar je oblika, ki jo `Date.parse` zanesljivo prebere v vseh
  brskalnikih.
- `sqlite-jdbc` pride nazaj v fazi 3, a samo za ukaz `prenos` (branje kopije).
- Izstopni pogoj »aplikacija lokalno teče na prenesenih produkcijskih
  podatkih« je premaknjen v fazo 3 — brez orodja za prenos podatkov ni.
- **Za fazo 3:** seme `starostno_sidro` je v izhodišču, zato ga `prenos`
  pred kopiranjem izprazni (vir resnice je kopija SQLite). Poizvedbe brez
  `ORDER BY` so v SQLite vračale vrstice po id-ju, PostgreSQL po legi v
  tabeli (po posodobitvi vrstica »odpotuje« na konec) — preverba 6.2/3
  (posnetek API-ja) pokaže, kje je to vidno.
- **Nove migracije v glavni mapi:** med fazama 1 in 3 so v `turnirko/` (druge seje)
  nastale V54 (logotipi), V55 (vloga navijač), V56 (profilne slike, pokrovitelji),
  V57 (spremljani igralci) in V58–V61 (paket Navijač: plačljivi paket, družina,
  potisna obvestila, podpora klubu). Dvojnice za PostgreSQL so napisane (30. 10.) in
  skupaj z SQLite izvirniki v veji; prenos podpira zanje potrebni tip `bytea`.
  V55 in V57 sta bili v `main` še neobjavljeni — ob zlitju jih primerjaj z
  izvirnikom. Pravilo velja do preklopa: vsaka nova migracija v `main` potrebuje
  dvojnico, sicer `pred-preklopom.sh` in `prenos` ustavita preklop.
- Lokalni zagon aplikacije zdaj potrebuje PostgreSQL 18 (README, »Zagon«);
  testi ga ne.

### Faza 3 — kaj je narejeno (30. 10. 2026)

**Orodje** (`si.turnirko.prenos`: `Prenos`, `Pretvorba`, `Shema`, `PrenosPorocilo`,
`PrenosUkaz`; profil `prenos`, `application-prenos.properties`): prebere kopijo
SQLite samo za branje in jo prenese v PostgreSQL. Tipi, ključi in vrstni red
nalaganja se preberejo iz **sheme PostgreSQL** (vir resnice), zato nova
migracija z dvojnico ne zahteva spremembe orodja (razen novega tipa stolpca, ki
ustavi prenos z jasno napako). Trije načini: `preglej` (nič ne zapiše),
`prenesi` (ena transakcija; potrdi se samo ob zelenem poročilu), `preveri`
(primerja že preneseno bazo). Podrobnosti:

- **Pretvorba brez ugibanja** (`Pretvorba`): 0/1 → `boolean` (kaj drugega ustavi),
  datum samo `LLLL-MM-DD`, čas v štirih oblikah (samo datum = polnoč, `T` ali
  presledek, sekunde neobvezne, do devet decimalk) → `timestamp(3)`. Edina
  izguba je **izrecna**: decimalke pod milisekundo se odrežejo in vsak primer
  gre v poročilo (na kopiji produkcije 289 vrednosti v 13 stolpcih petih
  tabel, vse čas zapisa iz `LocalDateTime.now()`). Pregled najde **vse**
  napake naenkrat (do 100 podrobno), ne eno na zagon.
- **Struktura**: tabele in stolpci SQLite se morajo ujemati s shemo PostgreSQL
  po imenih (sicer se prenos ustavi, še preden se dotakne podatkov), prav tako
  verzija sheme (`flyway_schema_history`; PG-only migracije z `.` v verziji,
  kot 53.1, so izvzete).
- **Nalaganje**: `COPY` v besedilnem načinu prek `CopyManager`; samoreference
  (`liga`, `tekma`) in tuji ključi, ki bi naredili krog, gredo najprej kot `NULL`,
  nato v enem `UPDATE` na tabelo; zaporedja se poravnajo (`setval` na `max(id)`),
  nato `ANALYZE`. Seme `starostno_sidro` se zamenja z vsebino SQLite.
- **Vloga**: prenos mora teči kot lastnik sheme (`turnirko_lastnik`);
  `statement_timeout` in drugi časovni limiti se v seji izklopijo.

**Preverbe (razdelek 6.2) na kopiji produkcije s 27. 10. 2026** (79 MB,
798.581 vrstic, 34 tabel):

| # | Preverba | Rezultat |
|---|---|---|
| 1 | število vrstic po tabelah | vseh 34 enako |
| 2 | SHA-256 po tabelah (kanonična oblika, iz obeh baz; PG-stran bere, kar je baza res shranila) | vseh 34 enako; prenos **19,6 s** (pregled 2,3 s, COPY 15,7 s, preverba 1,4 s) |
| 3 | posnetek API-ja: aplikacija na SQLite (zadnja razlicica `main`) in aplikacija na PostgreSQL nad istimi podatki, `skripte/primerjaj-api.mjs` | **17.169 poti, vsi zapisi** (386 turnirjev, 957 dogodkov, 456 lig, vsa srečanja lig, 1.975 igralcev in profilov, koledar od 2012), 281 MB JSON, **0 razlik** |
| 4 | popoln preračun ratinga na obeh bazah (97.058 tekem, 1.866 igralcev), `skripte/RazlikaRating.java` | `rating_stanje` in `rating_zgodovina` **enaka v vseh vrednostih** (razlikujeta se samo surogatni `id` in čas zapisa `ustvarjen_ob`, ki ga SQLite hrani kot datum). **Preračun traja na PostgreSQL 639 s, na SQLite 523 s (+22 %)** |

Preverba 3 je ob prvem poskusu odkrila eno razliko: `GET /lestvica` je vrnil
liste lig igralca v drugem vrstnem redu (`SELECT DISTINCT` brez `ORDER BY` — v
SQLite je vrstni red ostal po naključju, v PostgreSQL ga določi `HashAggregate`).
Popravek: 9 poizvedb z `DISTINCT` je dobilo `ORDER BY` po id-jih
(`KaderEkipeRepozitorij`, `PrijavaRepozitorij`, `RatingZgodovinaRepozitorij`,
`SrecanjeRepozitorij`, `UporabnikRepozitorij`); polje `idjiLig` je po pomenu
množica, zato ga skripta primerja urejeno.

**Ugotovitev o hitrosti preračuna:** PostgreSQL je za 22 % počasnejši kot SQLite
(vsak stavek gre čez gonilnik, `IDENTITY` ne pusti paketnega vstavljanja). Na
strežniku bo zaradi omrežja vsebnikov še malo več, a to je ročno sproženo
opravilo, ne vsakdanja pot; paketno pisanje (razdelek 3.4) zato **ni** vklopljeno
in čaka na potrebo.

### Faza 4 — kaj je narejeno (30. 10. 2026)

- `compose.yaml`: storitev `baza` (`postgres:18.6`, brez `ports:`, zvezek
  `turnirko_baza`, `healthcheck: pg_isready`, nastavitve iz razdelka 3.3 z
  zmanjšavo za 2 GB prek `.env`), aplikacija `depends_on: baza: service_healthy`,
  `SPRING_DATASOURCE_URL` na PostgreSQL, `SPRING_FLYWAY_USER` = lastnik.
- `skripte/baza/00-vloge.sh` (ob prvem zagonu): vlogi `turnirko_lastnik` in
  `turnirko_app` (SELECT/INSERT/UPDATE/DELETE; `statement_timeout 30s`,
  `lock_timeout 5s`, `idle_in_transaction_session_timeout 60s`), privzete pravice,
  **preverba razvrščanja `C.UTF-8`** (sicer se baza ne naredi) in
  `pg_stat_statements`.
- Kopije: `varnostna-kopija.sh ura|noc|rocno` (`pg_dump -Fc`, preverba s
  `pg_restore -l`, hramba 48 ur / 30 dni / 12 mesecev, `rclone` v šifrirano shrambo),
  `vaja-obnove.sh` (obnova v začasen vsebnik + primerjava števila vrstic), 
  `obnovi-bazo.sh` (obnova produkcije), `baza-stanje.sh` (teden opazovanja).
- Caddy: stran »Turnirko je v vzdrževanju« (503) ob 502/503/504, tudi ob običajni
  posodobitvi aplikacije.
- `OBJAVA.md`: razdelka 4 (baza, vloge, pregled, zamenjava gesla) in 7 (kopije,
  odnašanje, obnova) razložena od začetka; razdelek »Ukazi nad bazo«.
- **Preverjeno lokalno:** SQL vlog na pravem PostgreSQL 18; aplikacija pod
  vlogo `turnirko_app` z Flywayem kot `turnirko_lastnik` (glej spodaj, »Vloge«);
  `bash -n` vseh skript; YAML `compose.yaml`. **Ni preverjeno** (Dockerja ni):
  zagon vsebnika `postgres:18.6`, `docker compose` skript, `rclone`, `Caddyfile`
  (`caddy validate` teče v `pred-preklopom.sh`).

### Faza 5 — kaj je narejeno (30. 10. 2026)

- `docs/PREKLOP.md`: navodilo od začetka do konca (termin, priprava kode,
  priprava strežnika, generalka 2, večer preklopa, povratek, po preklopu,
  odpravljanje težav).
- `skripte/preklop/`: `pred-preklopom.sh` (vsa preverjanja in priprave brez
  vpliva na stran), `generalka.sh` (+ `compose.generalka.yaml`: projekt
  `generalka`, brez Caddyja, `127.0.0.1:18080`), `preklop.sh` (9 korakov z
  merjenjem časa in potrditvami), `nazaj.sh` (povratek do potrditve),
  `posnetek-api.sh` (dim-test: ~40 poti, SHA-256, primerjava).
- **Generalka 1** (lokalno): prenos, zagon aplikacije na PostgreSQL in primerjava
  API-ja je opravljena (preverbe 1–4 zgoraj); brez Dockerja, zato ne pokaže
  časa vsebnikov. **Generalka 2** na strežniku je prvi pravi preizkus skript
  `preklop/` in vsebnika `baza`: opravi jo lastnik (`generalka.sh`) pred
  preklopom.

### Vloge — preizkus aplikacije pod omejenimi pravicami

Lokalni PostgreSQL 18.6, baza `turnirko` z vlogama iz `00-vloge.sh` (isti SQL).
Kopija produkcije je z migracijami SQLite pripeljana na V57 (dvojnice V54–V57):

- **Prenos kot `turnirko_lastnik`**: Flyway ustvari 40 tabel (vključno z
  novimi V54–V57 in prezidano `uporabnik`), prenos 18 s, poročilo zeleno.
- **Aplikacija kot `turnirko_app`** (Flyway kot lastnik prek
  `SPRING_FLYWAY_USER`, kakor v `compose.yaml`): 783 poti API-ja enakih
  aplikaciji na SQLite.
- **Pravice**: `SELECT` in `UPDATE` vloga sme; `DROP TABLE`, `TRUNCATE`,
  `ALTER TABLE` in `CREATE TABLE` zavrne (`42501`); `SHOW statement_timeout` je
  30 s in `SELECT pg_sleep(31)` se prekine (`57014`). V kodi ni nobenega DDL
  ali `TRUNCATE` (preverjeno z iskanjem).
- **Polni preračun ratinga pod vlogo aplikacije** (30 s `statement_timeout`, 5 s
  `lock_timeout`): uspe v 596 s, brez prekinjenega stavka; rezultat je enak
  preračunu na SQLite v vseh vrednostih (`RazlikaRating.java`).

### Faza 6 — seznam (izvedba po dveh tednih brez težav)

Pogoj je dvotedensko opazovanje po preklopu (`baza-stanje.sh` dnevno, brez
napak 5xx in brez ponavljajočih se počasnih poizvedb). Šele nato, v enem
commitu:

1. **Iz repozitorija ven:** `zaledje/src/main/resources/db/migration/sqlite/`
   (zgodovina ostane v gitu), odvisnost `org.xerial:sqlite-jdbc` v `pom.xml`,
   paket `si.turnirko.prenos` in `application-prenos.properties`,
   `PrenosTest`/`PretvorbaTest`, `UrnikOpravil` dobi spet navaden
   `@EnableScheduling` (profil `prenos` odpade), `@Profile` na `ZacetniAdmin`,
   `skripte/preklop/`, `compose.generalka.yaml`, `skripte/primerjaj-api.mjs` in
   `skripte/RazlikaRating.java` (orodja za primerjavo SQLite ↔ PostgreSQL),
   `.gitattributes` ostane.
2. **Pravilo za nove migracije:** v `CLAUDE.md` ostane samo mapa
   `postgresql/` (dvojnica ni več potrebna); pred tem odstrani odstavke o
   »dvojnici v obeh mapah« in o namizni različici.
3. **Obvozi, ki jih PostgreSQL ne potrebuje:** paketi `IN` (omejitev števila
   parametrov SQLite), vrsta zahtev v vmesniku (`pomozno/vrstaZahtev.ts`: pisec
   baze ni več en sam — odstrani, ko je izmerjeno, da hkratni preklopi ne vračajo
   napak 409), komentarji o izrazu `concat(z.sistem, '')` v
   `RatingZgodovinaRepozitorij` (načrt SQLite brez statistik; v PostgreSQL ni
   potreben — izmeri `EXPLAIN` najprej).
4. **Denar** v `numeric(10,2)` + `BigDecimal` (`prijavnina`,
   `cena_ob_sklenitvi`): nova migracija **V58 samo za PostgreSQL**; `Pretvorba`
   (prenos) takrat ni več potrebna.
5. **Neprekinjene kopije (WAL-G):** slika PostgreSQL z WAL-G, `archive_mode=on`,
   `archive_command='wal-g wal-push %p'`, `archive_timeout=60`, v isto shrambo EU
   kot kopije; povrnitev na katerikoli trenutek zadnjih 14 dni. Preizkusi
   povrnitev v začasni vsebnik, preden izklopiš urne kopije.
6. **Dokumentacija:** `CLAUDE.md` (pot migracij, pravilo o kopijah — že
   posodobljeno —, odstavki o enem piscu in vrsti zahtev), `PRODUCT.md`
   (že posodobljeno), `README.md`.
7. **Po želji:** baza Umami v isti strežnik PostgreSQL (ločena baza, vloga) —
   prihranek ~150 MB pomnilnika.

## Povzetek

| Faza | Kaj | Napor |
|---|---|---|
| 0 | Obliž na SQLite: WAL, da gledalci ne čakajo pisca (neodvisno, takoj) | pol dneva |
| 1 | Koda na PostgreSQL: izhodiščna migracija, tipi, entitete, izvorni SQL, testi | 5–7 dni |
| 2 | **Sočasnost: pravila, ki jih danes varuje en sam pisec** | 3–5 dni |
| 3 | Orodje za prenos podatkov + samodejne preverbe | 2–3 dni |
| 4 | Strežnik: vsebnik `baza`, vloge, nastavitve, kopije, vaja obnove | 1 dan |
| 5 | Dve generalki + preklop + teden opazovanja | 1 večer + teden |
| 6 | Pospravljanje: odstranitev SQLite, obvozov in namizne; neprekinjene kopije | 2–3 dni |

Skupaj **~3–4 tedne dela**.

Najpomembnejša ugotovitev ni prenos podatkov (80 MB je stvar minut), ampak
**faza 2**. SQLite danes vse zapise izvaja enega za drugim, zato se dva
sočasna zapisa, ki bi si prišla navzkriž, končata z napako 500. PostgreSQL
pusti oba skozi. Kjer pravilo varuje storitev po vzorcu »preberi → preveri →
zapiši« (dvojni obračun ratinga, največ uporab kode, kvota turnirjev …), bo
iz glasne napake nastala **tiha napaka v podatkih**. Brez faze 2 selitev ni
varna, čeprav bi vsi današnji testi ostali zeleni.

---

## 1. Stanje danes (izmerjeno)

**Velikost.** 79 MB, 36 tabel, ~800.000 vrstic: `rating_zgodovina` 200.311,
`niz` 200.287, `niz_srecanja` 174.830, `tekma` 61.230, `tekma_srecanja`
50.520, `postava_srecanja` 43.888, `prijava` 30.949, igralcev 1.975. Zgodovina
od sezone 2012/13. Tudi desetkratna rast ostane pod 1 GB — cela baza bo v
pomnilniku.

**Shema.** 62 migracij (V1–V53), 190 omejitev CHECK, 31 UNIQUE, 2 delna
indeksa, 17 tujih ključev z `ON DELETE`, brez prožilcev in pogledov. Med
tabelami ni krogov tujih ključev, samo dve samoreferenci (`liga`, `tekma`).
`PRAGMA foreign_key_check` in `quick_check` sta na kopiji čista.

**Tipi stolpcev v SQLite:** 217 × INTEGER (od tega 24 logičnih s
`CHECK (x IN (0, 1))`), 151 × TEXT (od tega 44 datumov in časov), 4 × REAL
(`teza`, `pricakovano`, `prijavnina`, `cena_ob_sklenitvi` — vsi `Double` v
entitetah).

**Datumi so besedilo v štirih oblikah, tudi v istem stolpcu:**

| Stolpec | Oblike v produkciji |
|---|---|
| `uporabnik.email_potrjen_ob` | `2026-09-23` (2), `…T18:00` (2), `…T18:00:00` (5) |
| `narocnina.*_ob`, `trenutno_obdobje_do` | `…T…:…:…` (3), `2026-09-23 18:00…` s presledkom (1) |
| `racun_dogodek.ob` | samo datum (4), minute (1), sekunde (19) |
| `srecanje.odigran_ob` | samo datum (16), minute (94) |
| `igralec/turnir/liga/dogodek/prijava.ustvarjen_ob`, `rating_zgodovina.ustvarjen_ob` | samo datum — ura je bila izgubljena že ob zapisu (gonilnik, glej `CasKotBesedilo`) |

PostgreSQL vse štiri oblike razume kot `timestamp`; izgubljene ure ostanejo
polnoč (tako kot danes).

**Zakaj danes pride do napak 500.** Baza teče v načinu `journal_mode=delete`
(ne WAL), bazen povezav ima 10 povezav, transakcije pa se začnejo z
navadnim `BEGIN`. Transakcija, ki najprej bere in nato piše, ob sočasnem
pisalcu dobi `SQLITE_BUSY` **takoj**, brez čakanja (sicer bi se dve
transakciji zaklenili). Zato v vmesniku obstaja `pomozno/vrstaZahtev.ts`, ki
preklope spremljanih lig pošilja enega za drugim.

**Obvozi, ki obstajajo samo zaradi SQLite:**

- `CasKotBesedilo` + `date_string_format`: gonilnik odreže uro;
- vrsta zahtev v vmesniku (en pisec);
- `RacunDnevnikStoritev` hrani čase zahtev samo v pomnilniku (en pisec);
- klic Stripa namenoma zunaj transakcije (`KodePopustaStoritev.stripeKupon`);
- poizvedbe po paketih zaradi omejitve števila vezanih parametrov
  (`PreracunRatingaStoritev`, `RatingZgodovinaRepozitorij`, `NizRepozitorij`);
- slovenska abeceda se razvršča v Javi (`SlovenskaAbeceda`, 6 repozitorijev);
- meja »največ dve ligi na domači strani« ni v shemi (`LigaStoritev:255`);
- komentarji o ponovni rabi izbrisanih id-jev (`UporabaKodePopustaRepozitorij`).

**Koda:** 47.000 vrstic Jave v 369 datotekah, 126 testnih razredov, 210 ×
`@Transactional`, 187 × `@Query` (JPQL — Hibernate ga prevede sam), izvorni
SQL na ~10 mestih (`RacunDogodekRepozitorij`, `RacunPrijavaRepozitorij`,
`DvojnikiIgralcevStoritev` z `INSERT OR IGNORE`, `IgralciZdruzitevStoritev`,
`CiscenjeStare`, `CiscenjeUvoza`, `PopravekZgodovineUkaz`). 11 entitet ima
`@Version`.

**Delovni tok s podatki.** Kopija `.db` z strežnika → Node in `sqlite3`
lokalno → datoteka SQL → na strežniku `docker compose stop app` +
`sqlite3 -bail` + `chown` (npr. `savinja-na-streznik.sh`,
`koledar-na-streznik.sh`). Kopije dela `skripte/varnostna-kopija.sh`
(`.backup`, enkrat na dan, na istem strežniku; odnašanje ni nastavljeno).

**Gledalci.** Strani v teku se osvežujejo vsakih 15 s
(`pomozno/osvezevanje.ts`). 500 gledalcev na eni strani turnirja je ~33
zahtev na sekundo, vsaka z več poizvedbami.

---

## 2. Zakaj PostgreSQL

| Možnost | Ocena |
|---|---|
| **SQLite + WAL (+ Litestream)** | Zmogljivost zadošča še dolgo. Ne reši pa več pisalcev hkrati (pisec je še vedno en, desetminutni preračun ratinga ga zaseda v paketih), ne dovoli dveh primerkov aplikacije (posodobitev brez izpada) in vsi obvozi zgoraj ostanejo. **Primerno kot obliž (faza 0), ne kot cilj.** |
| **PostgreSQL** ✔ | Več pisalcev hkrati z zaklepi na vrstico namesto na celo bazo. Transakcijski DDL: migracija Flyway, ki pade, se v celoti povrne (v SQLite ostane pol sheme). Pravi tipi: `date`, `timestamp`, `boolean`, `numeric`. Delni indeksi in indeksi nad izrazi, omejitve EXCLUDE, svetovalni zaklepi (`pg_advisory_lock`), LISTEN/NOTIFY za kasnejše sprotno obveščanje, neprekinjene kopije s povrnitvijo na poljuben trenutek. Hibernate in Flyway ga podpirata v celoti. Umami na istem strežniku že teče na njem, `RAZVOJNI-NACRT.md` ga predvideva, `spring.flyway.locations=classpath:db/migration/{vendor}` je pripravljen že od začetka. |
| MySQL / MariaDB | Brez transakcijskega DDL; MariaDB nima delnih indeksov (shema ju že ima). Nobene prednosti pred PostgreSQL. |
| CockroachDB, YugabyteDB, Turso/libSQL, Supabase | Rešujejo težave, ki jih Turnirko nima (več regij sveta, robno računalništvo). Več stroškov, več odvisnosti od ponudnika, manj znanja na voljo. |

**Različica: PostgreSQL 18** (izšel septembra 2025, ima leto popravkov).
Ob izvedbi preveri, da Flyway iz Spring Boot 4.0.x podpira 18 brez opozorila;
sicer vzemi 17. V `compose.yaml` gre točna manjša različica (npr.
`postgres:18.x`), kot pri Umamiju — posodobitev naj bo odločitev, ne stranski
učinek ponovnega zagona.

**Česa ne potrebujemo** (in zakaj): bralne replike, Redis, Kubernetes,
ločenega strežnika za bazo. Ena instanca PostgreSQL na VPS s 4 GB pomnilnika
ob tej velikosti baze zmore več tisoč poizvedb na sekundo. Meja, ob kateri
se premaknemo naprej, je v razdelku 9.

---

## 3. Ciljna postavitev

### 3.1 Vsebnik

V `compose.yaml` nova storitev `baza` (ločena od `umami-baza` — drug
življenjski cikel, druge kopije, druga glavna različica):

- slika `postgres:18.x`, zvezek `turnirko_baza`, **brez `ports:`** (do baze
  pride samo aplikacija po notranjem omrežju; za pregled od doma SSH-tunel);
- `healthcheck: pg_isready`, aplikacija `depends_on: baza: condition:
  service_healthy`;
- `SPRING_DATASOURCE_URL=jdbc:postgresql://baza:5432/turnirko`, uporabnik in
  geslo iz `.env` (nove spremenljivke v `.env.primer`).

### 3.2 Baza in vloge

```sql
CREATE DATABASE turnirko
  ENCODING 'UTF8' LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8'
  TEMPLATE template0;
```

`C.UTF-8` razvršča po kodnih točkah — **natanko tako kot SQLite danes**, zato
se noben vrstni red v aplikaciji ne spremeni (Č, Š, Ž še naprej uredi Java).
Slovensko razvrščanje v bazi je kasneje mogoče posamično
(`ORDER BY priimek COLLATE "sl-SI-x-icu"`).

Dve vlogi:

- `turnirko_lastnik` — lastnik sheme; z njo tečejo Flyway
  (`spring.flyway.user/password`) ter ukazi `uvoz`, `popravek` in `prenos`;
- `turnirko_app` — samo `SELECT/INSERT/UPDATE/DELETE`; s to teče aplikacija.
  Vdor v aplikacijo ne more zbrisati tabel.

Nastavitve vloge `turnirko_app`: `statement_timeout = '30s'`,
`lock_timeout = '5s'`, `idle_in_transaction_session_timeout = '60s'`. Zaklep,
ki ga nekdo pozabi, tako ne ustavi celega turnirja.

### 3.3 Nastavitve strežnika baze (VPS s 4 GB)

`shared_buffers = 512MB`, `effective_cache_size = 1GB`, `work_mem = 16MB`,
`max_connections = 50`, `log_min_duration_statement = 250ms`,
`shared_preload_libraries = pg_stat_statements`,
`timezone = 'Europe/Ljubljana'`. Pri 2 GB pomnilnika vse prepolovi —
**velikost VPS je treba preveriti** (`free -h`, `df -h`); JVM, Caddy, Umami z
lastnim PostgreSQL in nova baza skupaj na 2 GB ne bodo udobni.

### 3.4 Aplikacija

- odvisnosti: ven `sqlite-jdbc` in `hibernate-community-dialects`, noter
  `org.postgresql:postgresql` (različico upravlja Spring Boot); narečje
  Hibernate se izbere samo;
- Hikari: `maximum-pool-size=10`, `connection-timeout=5000`;
- gonilnik: `reWriteBatchedInserts=true`; Hibernate `jdbc.batch_size=50`,
  `order_inserts=true`, `order_updates=true` (za preračun in uvoze);
- iz `application.properties` gredo `PRAGMA foreign_keys`,
  `date_string_format`, `timestamp_string_format`, `date_class`.

### 3.5 Varnostne kopije

**Ob preklopu:** `pg_dump -Fc` vsako uro med 7. in 23. uro (hrani 48 ur) in
vsako noč (hrani 30 dni) ter prvi dan v mesecu (hrani 12 mesecev). Kopija je
stisnjena ~20 MB in nastane v nekaj sekundah. Odnašanje z `rclone` v
shrambo S3 v EU (Hetzner Object Storage, Backblaze B2 EU …, ~1 €/mesec).
Najslabši primer: izgubljena ura vnosov — te so tudi na papirnih listkih.

**V fazi 6:** neprekinjeno arhiviranje WAL z WAL-G v isto shrambo
(`archive_timeout = 60`) — povrnitev na katerikoli trenutek zadnjih 14 dni,
izguba največ minuto.

**Vaja obnove** (skripta, vsak mesec): zadnjo kopijo obnovi v začasen
vsebnik, prešteje vrstice po tabelah in jih primerja s produkcijo. Kopija, ki
je nihče ni obnovil, ni kopija.

Kopija ostane osebni podatek (datumi rojstva): `chmod 600`, šifrirano
odnašanje (`rclone crypt`), shramba v EU.

---

## 4. Kaj se spremeni v kodi (faza 1)

Načelo: **ob preklopu spremeni samo to, kar mora**. Poenostavitve (odstranitev
obvozov) pridejo v fazi 6, ko je nova baza stabilna — tako je vsaka razlika v
delovanju po preklopu krivda prenosa in ne preurejanja.

### 4.1 Shema: ena izhodiščna migracija

Nova mapa `db/migration/postgresql/` z eno datoteko
**`V53__izhodisce.sql`**: shema, enaka stanju SQLite po V53, s pravimi tipi.
Številka 53 je namerna: zgodovina migracij ostane berljiva (»V41 je
uvedla kvalifikacije« velja še naprej), prva nova migracija bo V54. Prazna
baza PostgreSQL izvede samo izhodišče.

Preslikava tipov:

| SQLite | PostgreSQL | Opomba |
|---|---|---|
| `id INTEGER PRIMARY KEY [AUTOINCREMENT]` | `bigint GENERATED BY DEFAULT AS IDENTITY` | po prenosu `setval` na `max(id)` |
| `INTEGER REFERENCES …` | `bigint` | |
| `INTEGER CHECK (x IN (0,1))` (24) | `boolean` | CHECK odpade |
| ostali `INTEGER` | `integer` | `bigint`, kjer je v entiteti `Long` |
| TEXT z datumom (`datum_*`, `*_rojstva`, `velja_do`) | `date` | |
| TEXT s časom (`*_ob`, `predviden_zacetek`, `zacetek_prvega_kola` …) | `timestamp(3)` brez časovnega pasu | aplikacija dela z `LocalDateTime`; pomen se ne spremeni |
| `REAL` (4) | `double precision` | rating mora ostati enak do zadnjega bita; denar v `numeric` šele v fazi 6 skupaj z `BigDecimal` |
| `TEXT` | `text` | CHECK-i za naštevne vrednosti ostanejo |

Vseh 190 CHECK-ov se prenese (`length()` in `trim()` sta v PostgreSQL enaka),
oba delna indeksa tudi. Osnutek izhodišča se izpelje iz `.schema` produkcijske
kopije s skripto in nato pregleda ročno, tabelo za tabelo.

Test, ki ga dodamo: Hibernate `ddl-auto=validate` **samo v testnem profilu**
— ob zagonu primerja vsako entiteto s shemo in ustavi testni zagon, če se tip
stolpca in polja ne ujemata. Produkcija ostane na `none`.

### 4.2 Entitete in nastavitve

- `@Convert(converter = CasKotBesedilo.class)` gre z vseh polj (stolpec je zdaj
  `timestamp`); razred se zbriše;
- `TurnirkoAplikacija.pripraviMapoBaze` gre ven (ustvarjala je mapo za
  datoteko baze);
- profil `namizni`, `NamizniNacin`, privzeti `admin/admin` v `ZacetniAdmin` in
  navodila za jpackage gredo ven (odločitev lastnika; s tem se zapre tudi
  točka V11 iz `VARNOSTNI-PREGLED.md`);
- `application-test.properties`: testna baza PostgreSQL (glej 4.4).

### 4.3 Izvorni SQL

| Mesto | Sprememba |
|---|---|
| `DvojnikiIgralcevStoritev.niDuplikat` | `INSERT OR IGNORE` → `INSERT … ON CONFLICT DO NOTHING` |
| `RacunDogodekRepozitorij.najnovejsiZaVse` | `ROW_NUMBER()` deluje enako; podpoizvedba brez vzdevka je dovoljena šele od PostgreSQL 16 — vzdevek dodaj, da poizvedba ni odvisna od različice |
| `RacunPrijavaRepozitorij` (2) | primerja čase kot besedilo — zdaj `timestamp` |
| `IgralciZdruzitevStoritev.prviDan` | reže niz `toString().substring(0, 10)` — vrednost je zdaj `Timestamp`/`LocalDateTime` |
| `CiscenjeStare`, `CiscenjeUvoza`, `PopravekZgodovineUkaz` | pregled stavkov enega po enega |
| Vsi porabniki `List<Object[]>` iz izvornih poizvedb | SQLite vrača `Long`/`Double`/`String`, PostgreSQL `Long`/`Integer`/`BigDecimal`/`Timestamp`/`Boolean` — vsak `(Tip) vrstica[i]` preveri |

JPQL (187 poizvedb) prevede Hibernate; nevarna sta le `LIKE` (v PostgreSQL
občutljiv na velike črke — v kodi ga ni, preverjeno) in primerjave besedila
z datumi (iskanje po `CasKotBesedilo` in `'yyyy-MM-dd'`).

### 4.4 Testi

Lokalno ni Dockerja, zato **`io.zonky.test:embedded-postgres`**: Maven
prenese binarne datoteke PostgreSQL in jih zažene brez namestitve, tudi na
Windows. Ena baza na Spring kontekst, sveža iz Flyway. Preveri, da je na voljo
ista glavna različica kot v produkciji.

Pasti v obstoječih testih:

- testi, ki se zanašajo na ponovno rabo id-jev v SQLite
  (`ponovnaRegistracijaIsteOsebeNeZasedeMesta`), morajo ostati zeleni z
  zaporedji, ki id-jev nikoli ne ponovijo;
- testi brez `@Transactional` (`LenoNalaganjeTest` …) potrebujejo čiščenje
  med testi, ki ga je prej opravila nova datoteka.

Pogoj za konec faze 1: vseh 126 testnih razredov zelenih na PostgreSQL,
`npm run build` zelen.

---

## 5. Sočasnost — kjer se obnašanje dejansko spremeni (faza 2)

### 5.1 Kaj se zgodi

| | SQLite danes | PostgreSQL (`READ COMMITTED`) |
|---|---|---|
| Dve transakciji pišeta različne vrstice | druga čaka ali dobi 500 | obe uspeta takoj ✔ |
| Dve transakciji preverita »je še prostor?« in nato vpišeta | druga dobi 500 — **pravilo ostane celo** | **obe uspeta — pravilo je prekršeno brez napake** |
| Dve transakciji spremenita isto vrstico z `@Version` | druga dobi 500 ali 409 | druga dobi 409 |
| Dolg preračun ratinga in vnos rezultata | vnos čaka med paketi ali dobi 500 | oba tečeta — **vrstni red v ratingu se lahko pomeša** |

### 5.2 Najdena mesta

1. **Obračun ratinga ob vnosu rezultata** (`RatingStoritev.obracunajZaTurnirsko`,
   `obracunajZaLigasko`): `existsByTekmaId(...)` → `return`, sicer obračunaj.
   Dve sočasni zahtevi za isto tekmo (dvojni klik, ponovljena zahteva po slabem
   Wi-Fi) obe prestaneta preverbo → **dvojni obračun**. V `rating_zgodovina`
   ni omejitve UNIQUE, ki bi to ujela.
2. **Rating je časovna vrsta.** Dve sočasni tekmi istega igralca (ekipno
   srečanje, dve mizi lige) — `RatingStanje` ima `@Version`, zato drugi
   vnašalec dobi 409 »Zapis je medtem spremenil nekdo drug«. Na turnirju je to
   slaba izkušnja za operaterja, ki ni naredil nič narobe.
3. **Preračun med vnosom.** `PreracunRatingaStoritev.preracunajOd` seznam
   tekem prebere na začetku in nato dela v paketih po svojih transakcijah
   (polni preračun traja 9–10 minut). Tekma, vnesena vmes, se obračuna iz
   napol preračunanega stanja, preračun pa jo nato povozi ali izpusti.
   Napaka obstaja že danes, večja sočasnost jo naredi verjetnejšo.
4. **Napredovanje v izločilni mreži.** Dve sosednji tekmi se končata hkrati
   in obe vpišeta zmagovalca v isto naslednjo tekmo → drugi vnos dobi 409.
5. **Pravila čez več vrstic, ki jih varuje storitev:**
   - največ dve ligi na domači strani (`LigaStoritev:264`);
   - največ uporab kode popusta (`KodePopustaStoritev:416`);
   - kvota turnirjev organizatorja v sezoni (`NarocninaStoritev:85`);
   - število poskusov potrditvene kode (`KodeStoritev:76`, že V9 v
     varnostnem pregledu);
   - zgornja meja prijav (`najvec`) na dogodek;
   - preostalih ~27 metod `count…`/`exists…` v repozitorijih, ki stojijo pred
     `save` — pregledati vse po seznamu.
6. **Webhook Stripa**: isti dogodek lahko pride dvakrat hkrati — preveri, da
   je obdelava idempotentna z omejitvijo v shemi (id dogodka UNIQUE), ne s
   preverbo v kodi.

### 5.3 Rešitve

- **En zaklep za ves rating.** Vsaka transakcija, ki spremeni rating (obračun
  tekme, zunanja uvrstitev, začetni rating, odbitek za neaktivnost, paket
  preračuna), najprej vzame `pg_advisory_xact_lock(<ključ ratinga>)`. Rating
  je ena časovna vrsta, zato je zaporedno pisanje njegov pravi pomen in ne
  ozko grlo: obračun traja milisekunde.
- **Rezultat se shrani tudi med preračunom.** Vnos rezultata zaklep poskusi
  vzeti s `pg_try_advisory_xact_lock`. Če preračun teče, rezultat shrani brez
  obračuna; preračun se konča z zanko »obračunaj vse končane tekme brez
  dnevnika, dokler jih je«. Ista poizvedba vsakih 5 minut teče kot varovalka
  (samozdravljenje, če bi kdaj kaj ušlo).
- **Omejitve v shemi namesto preverb, kjer gre:** delna UNIQUE
  `rating_zgodovina (id_tekma, id_igralec, sistem) WHERE id_tekma IS NOT NULL`
  in enako za `id_tekma_srecanja`; največ dve ligi na domači strani z delnim
  indeksom ni mogoče, zato zaklep (spodaj).
- **Zaklep »starševske« vrstice** na začetku transakcije za pravila čez več
  vrstic: `SELECT … FOR UPDATE` na kodi popusta, organizatorju, dogodku,
  naslednji tekmi v mreži. Koda storitve ostane enaka, samo prva poizvedba
  postane zaklepajoča (`@Lock(PESSIMISTIC_WRITE)` v repozitoriju).
- **Preslikava napak:** `PessimisticLockingFailureException` in
  `CannotAcquireLockException` (potekel `lock_timeout`, zastoj) v
  `GlobalniObravnavalecIzjem` → 409 s prijaznim besedilom, ne 500.

### 5.4 Dokaz

Integracijski testi na pravem PostgreSQL z dvema nitma in `CountDownLatch`
(obe zahtevi gresta skozi preverbo v isti milisekundi):

- dva vnosa iste tekme → en obračun;
- dve sosednji tekmi → obe napredujeta v naslednjo, brez 409;
- tretja uporaba kode z `najvec_uporab = 2` → zavrnjena;
- vnos rezultata med preračunom → rating na koncu enak, kot če bi bil vnesen
  pred preračunom.

To je tudi izstopni kriterij iz `RAZVOJNI-NACRT.md` (»dva operaterja hkrati
vnašata rezultate brez izgubljenih posodobitev«).

---

## 6. Prenos podatkov (faza 3)

### 6.1 Orodje

Ukaz v zaledju s profilom `prenos` (kot `uvoz` in `popravek`):
prebere kopijo SQLite prek JDBC in piše v PostgreSQL, katerega shemo je
ustvaril Flyway (izhodišče V53), z `COPY` (gonilnikov `CopyManager`).
Zakaj ne pgloader: ta ustvari svoje tipe, mi pa želimo shemo iz Flywaya;
na Windows ga ni preprosto zagnati; pretvorbe in preverbe morajo biti
izrecne.

- Vrstni red tabel po grafu tujih ključev. `liga` in `tekma` (samoreferenci)
  gresta v dveh korakih: najprej vrstice s samoreferenco `NULL`, nato
  `UPDATE`.
- Pretvorbe: 0/1 → `boolean`; štiri oblike časa → `timestamp`; datum →
  `date`. **Vrednost, ki se ne pretvori, ustavi prenos** in gre v poročilo —
  nobenega tihega `NULL`.
- Po prenosu: `setval` zaporedja vsake tabele na `max(id)`, `ANALYZE`.
- Ves prenos je ena transakcija: ali vse ali nič.

### 6.2 Samodejne preverbe (poročilo mora biti zeleno)

1. **Število vrstic** po tabelah enako.
2. **Kontrolna vsota po tabeli**: vsaka vrstica v kanonični obliki (pretvorjene
   vrednosti), SHA-256 čez tabelo, izračunano iz obeh baz.
3. **Posnetek API-ja**: ~200 javnih poti (lestvice po kategorijah, vsi igralci,
   po 20 turnirjev, lig, srečanj in profilov) iz aplikacije na SQLite in iz
   aplikacije na PostgreSQL nad istimi podatki; odgovori JSON se morajo
   ujemati do bajta. To preveri vse skupaj — tipe, pretvorbe, poizvedbe,
   vrstni red.
4. **Preračun ratinga od začetka** na obeh bazah → `rating_stanje` in
   `rating_zgodovina` enaka do zadnjega bita. Hkrati izmeri čas preračuna na
   PostgreSQL (danes 550–585 s).

---

## 7. Preklop (faza 5)

### 7.1 Generalki

Dvakrat na sveži kopiji produkcije, z merjenjem časa vsakega koraka: prvič
lokalno, drugič na strežniku v ločenem projektu compose (druga mapa, druga
imena vsebnikov, brez Caddyja). Preklop se zgodi šele, ko druga generalka
gre od začetka do konca brez ročnega posega.

### 7.2 Večer preklopa (~30 min)

Termin: med tednom zvečer, ko ni kol lig ne turnirjev (preveri koledar SNTL
in Savinja ter obvesti organizatorje teden dni prej).

1. Nova slika zgrajena že prej; vsebnik `baza` že teče in je prazen.
2. Caddy postreže stran »Vzdrževanje, nazaj ob 21.30« (`handle_errors` za
   502), `docker compose stop app` — pisanje ustavljeno.
3. `skripte/varnostna-kopija.sh` → ta kopija SQLite je **povratna točka**.
4. Flyway ustvari shemo, ukaz `prenos` prenese podatke; poročilo zeleno.
5. Zagon nove aplikacije; dim-test: skripta s `curl` nad 20 potmi in
   primerjava s posnetkom API-ja, narejenim v koraku 2.
6. Odpri promet. Prva ročna preverba: vnos rezultata na testnem turnirju
   in njegov obračun v ratingu.

**Povratna pot:** do koraka 6 je povratek ena zamenjava slike in stara
datoteka SQLite (nespremenjena). Po odprtju prometa nastajajo zapisi samo v
PostgreSQL, zato je **korak 6 točka brez vrnitve** — od tod naprej se napake
popravljajo naprej, ne nazaj. Kopija SQLite ostane shranjena 3 mesece.

### 7.3 Teden opazovanja

Vsak dan: `pg_stat_statements` (najpočasnejše poizvedbe), dnevnik počasnih
poizvedb (> 250 ms), napake 409/500 v dnevniku aplikacije, uspeh nočne
kopije in odnašanja.

---

## 8. Delovni tok po selitvi

- **Lokalna baza:** PostgreSQL 18 za Windows (namestitev EDB ali prenosna
  mapa z `psql`, `pg_dump`, `pg_restore`); za pregled DBeaver ali pgAdmin.
- **Kopija produkcije domov:** na strežniku `pg_dump -Fc` (ena vrstica v
  skripti), `scp`, lokalno `pg_restore`. Nadomesti današnjo kopijo `.db`.
- **Uvozi in popravki iz SQL:** narečje SQLite → PostgreSQL:
  `.bail on` + `BEGIN IMMEDIATE` → `psql -v ON_ERROR_STOP=1 --single-transaction`;
  `INSERT OR IGNORE` → `ON CONFLICT DO NOTHING`; `last_insert_rowid()` →
  `RETURNING id`. Na strežniku
  `docker compose exec -T baza psql … < uvoz.sql`. **Aplikacije ni več treba
  ustavljati**: uvoz je ena transakcija in gledalci do potrditve vidijo staro
  stanje. Preračun ratinga po uvozu ostane (zaklep iz 5.3 ga uskladi z
  vnosi).
- **Skripte Node** v mapah uvozov (zunaj gita) so zgodovina; nove berejo
  bazo s paketom `pg` namesto `sqlite3`.
- **Pravilo »nikoli `cp` baze«** se spremeni v »nikoli kopije mape
  podatkov PostgreSQL; samo `pg_dump`«.

---

## 9. Bralna pot za gledalce (po selitvi, z merjenjem)

SQLite skrije poizvedbe N+1: poizvedba v istem procesu traja mikrosekunde.
V PostgreSQL vsaka poizvedba prepotuje omrežje vsebnikov (~0,1 ms), zato
stran s 300 drobnimi poizvedbami postane opazno počasnejša. Najprej merimo,
nato ukrepamo:

1. **Meritev:** Hibernate statistika (število poizvedb na zahtevo) za glavne
   javne strani — turnir, dogodek, liga, srečanje, lestvica, profil; obremenilni
   test (k6 ali oha) na generalki: 500 navideznih gledalcev s 15-sekundnim
   osveževanjem. Cilj: p95 < 200 ms, CPU < 50 %.
2. **Če je treba, po vrsti:** odprava N+1 (`join fetch`, ena poizvedba na
   seznam); ETag/304 s številko različice tekmovanja (en `SELECT` namesto
   celega drevesa, odgovor brez telesa); kratek predpomnilnik (Caffeine,
   2–5 s) za javne odgovore tekmovanj v teku; SSE + LISTEN/NOTIFY namesto
   osveževanja (faza 2 v `RAZVOJNI-NACRT.md`).

Za regijo bo po pričakovanju dovolj prva točka, morda druga.

**Merila za naslednjo stopnjo** (ko bi rasli čez regijo): p95 javnih strani
> 300 ms v vrhu ali CPU strežnika > 70 % dlje kot 10 minut → najprej
predpomnilnik in ETag, nato večji VPS, šele nato drugi primerek aplikacije
(takrat `@Scheduled` opravila dobijo ShedLock in omejevalnik poskusov gre iz
pomnilnika v bazo). Bralna replika baze pride v poštev šele krepko čez to.

---

## 10. Faze podrobno

### Faza 0 — obliž na SQLite (pol dneva, neodvisno, lahko takoj)

V `application.properties`:

```properties
spring.datasource.hikari.data-source-properties.journal_mode=WAL
spring.datasource.hikari.data-source-properties.synchronous=NORMAL
```

Kaj prinese: v WAL gledalci med zapisom ne čakajo več na pisca (danes ob
vsaki potrditvi zapisa čakajo do 3 s — privzeti `busy_timeout` gonilnika).
Pri več gledalcih in vnašalcih to odstrani zakasnitve na javnih straneh.

Česa **ne** prinese: napake 500, ko dve transakciji najprej bereta in nato
pišeta (preklop spremljanih lig, dva vnosa hkrati), ostanejo — v WAL se
druga konča s `SQLITE_BUSY_SNAPSHOT`. Edini popravek na SQLite bi bil
`transaction_mode=IMMEDIATE`, ki zaklene bazo že ob začetku **vsake**
transakcije, tudi bralne — vse zahteve bi stale v eni vrsti. Tega ne
priporočam; to reši PostgreSQL. Faza 0 ne spremeni ničesar, kar bi faza 1
morala razveljaviti.

**Past:** ob WAL nastaneta datoteki `turnirko.db-wal` in `turnirko.db-shm`.
Skripte, ki tečejo kot root (`sqlite3 … < uvoz.sql`), ju ustvarijo kot root
in aplikacija (uporabnik 10001) ob naslednjem zagonu baze ne more odpreti.
V vseh skriptah za strežnik mora `chown` zajeti celo mapo:
`chown -R 10001:10001 podatki`. `.backup` v skripti za kopije z WAL deluje.

### Faza 1 — koda na PostgreSQL (5–7 dni)

Razdelek 4. Izstop: testi zeleni na PostgreSQL, Hibernate `validate` v testih
brez opozoril, aplikacija lokalno teče na prenesenih produkcijskih podatkih.

### Faza 2 — sočasnost (3–5 dni)

Razdelek 5. Izstop: štirje testi z dvema nitma zeleni, pregledan seznam vseh
`count…`/`exists…` pred `save` z zapisano odločitvijo za vsakega.

### Faza 3 — prenos (2–3 dni)

Razdelek 6. Izstop: poročilo zeleno na kopiji z vsemi štirimi preverbami.

### Faza 4 — strežnik (1 dan)

Razdelek 3: vsebnik, vloge, nastavitve, kopije z odnašanjem, vaja obnove,
stran za vzdrževanje v Caddyju. Nov razdelek v `docs/OBJAVA.md`, razložen od
začetka (kaj je vloga, kaj je `pg_dump`, kako obnoviš).

### Faza 5 — generalki in preklop (1 večer + teden)

Razdelek 7.

### Faza 6 — pospravljanje (2–3 dni, po dveh tednih brez težav)

- iz repozitorija gredo `db/migration/sqlite/`, `CasKotBesedilo`, vse o
  namizni različici, `skripte/varnostna-kopija.sh` v obliki za SQLite;
- obvozi iz razdelka 1, kjer jih ne potrebujemo več: paketi `IN`, vrsta zahtev
  v vmesniku (ostane neškodljiva — odstrani, ko je izmerjeno, da sočasni
  preklopi ne vračajo napak), čas klica Stripa zunaj transakcije ostane
  (dober razlog tudi brez SQLite);
- denar v `numeric(10,2)` + `BigDecimal` (`prijavnina`, `cena_ob_sklenitvi`);
- neprekinjene kopije WAL-G;
- posodobitev `CLAUDE.md` (pot migracij, pravilo o kopijah, odstavki o
  `CasKotBesedilo`, enem piscu, vrsti zahtev) in `PRODUCT.md` (»gostovanje:
  PostgreSQL«);
- po želji: Umamijevo bazo preseli v isti strežnik PostgreSQL (ločena baza)
  in prihrani ~150 MB pomnilnika.

---

## 11. Tveganja

| Tveganje | Verjetnost | Ublažitev |
|---|---|---|
| Tiho prekršeno pravilo zaradi sočasnosti | visoka brez faze 2 | faza 2 je pogoj za preklop; testi z dvema nitma |
| Preračun ratinga počasnejši (vsak stavek čez omrežje, IDENTITY brez paketnega vstavljanja) | srednja | izmeri v preverbi 6.2/4; po potrebi paketi in zaporedja z `allocationSize` |
| Javne strani počasnejše zaradi N+1 | srednja | razdelek 9, meritev pred preklopom |
| Pretvorba časa, ki se ne posreči | nizka | prenos se ustavi, nič tihega |
| Premalo pomnilnika na VPS | neznana | preveri pred fazo 4; 4 GB priporočeno |
| Nova komponenta za vzdrževanje (posodobitve, kopije) | gotova | skripte + navodila v `OBJAVA.md`, mesečna vaja obnove |
| Več sej Claude Code v istem checkoutu med selitvijo | srednja | selitev v svoji veji; druge spremembe sheme med fazama 1 in 5 gredo v **obe** mapi migracij |

Zadnja vrstica je pomembna: dokler preklop ni opravljen, produkcija teče na
SQLite. Vsaka nova migracija (V54 …), ki nastane vmes, mora imeti dvojnico v
`db/migration/postgresql/`, sicer jo prenos izpusti.

## 12. Kaj ostane enako (ne podiraj)

- Flyway je edini lastnik sheme, `ddl-auto=none` v produkciji, migracij se ne
  spreminja za nazaj.
- Navzven samo DTO-ji; osebni podatki ločeni na ravni tipa.
- `join fetch` disciplina in `LenoNalaganjeTest` — v PostgreSQL so še bolj
  pomembni (razdelek 9).
- `@Version` na 11 entitetah in odgovor 409 ob sočasni spremembi.
- Pošta gre ven šele po potrditvi transakcije.

## Odprto

- Velikost VPS (pomnilnik, disk) — pred fazo 4.
- Datum preklopa — po koledarju lig, ko sta fazi 1–3 zaključeni.
- Ponudnik shrambe za odnašanje kopij (EU).
