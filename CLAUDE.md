# Turnirko — navodila za delo s kodo

## Jezik in slog

- **Vsa imena (razredi, metode, spremenljivke, stolpci) in komentarji so v slovenščini**,
  brez šumnikov v identifikatorjih (Storitev, Zreb, Prijava...). Angleški ostanejo samo
  izrazi ogrodij (Repository sufiks je preveden: `...Repozitorij`).
- Komentarji pojasnjujejo ZAKAJ in domenska pravila, ne KAJ počne vrstica.

## Delovni prostor, objava in varnost produkcije

Velja od preklopa na PostgreSQL (2. 11. 2026). Preberi to, preden karkoli spremeniš.

### Kje je koda in kam gredo spremembe

- **Ena delovna mapa: `C:\Users\Miha\Desktop\Turnirko\turnirko`**: git repozitorij,
  **veja `test`** (od 10. 11. 2026, testno okolje), `origin` =
  `github.com/MihaFatur/Turnirko`. V njej beri, piši, testiraj, commitaj in
  pushaj. Tudi `mvnw` in `npm` tečeta tam. `main` je od 10. 11. 2026 samo
  **preverjeno** stanje (kar teče ali bo teklo v produkciji): na `main` se ne
  commita in se ne preklaplja nanj; dobi ga samo povišanje preizkušenega
  commita (glej »Testno okolje« spodaj).
- Nadrejena mapa `C:\Users\Miha\Desktop\Turnirko` **ni repozitorij**: v njej so
  uvozni podatki, posnetki produkcije in prenosna lokalna baza (`pg18`).
  Projektnih datotek tam ne ustvarjaj.
- Mapa `turnirko-postgresql` je bila začasen *git worktree* (druga kopija istega
  repozitorija na veji `prenova-baze`), narejen za selitev baze, da je glavna
  mapa ostala prosta za druge seje. Po preklopu je veja vsebinsko enaka `main` in
  mapa se pospravi (`git worktree remove`, veja `prenova-baze` se izbriše).
  **Če jo še najdeš, v njej ne delaj**: povej uporabniku, naj se pospravi.
- Začetek dela: `git status` in `git pull --ff-only`. Če je lokalni `main` zadaj
  ali je v mapi tuje neshranjeno delo (isto mapo uporablja več sej), ustavi in
  povej; ne prepisuj.
- **Commitaj in pushaj samo, ko uporabnik to zahteva.** Vedno na vejo `test`
  (ne na `main`), nikoli `--force`. Sporočilo commita slovensko: kaj in zakaj.
  Če je delovna mapa na drugi veji kot `test` in v njej ni tujega neshranjenega
  dela, ustavi in povej; mapo deli več sej, zato veje ne preklapljaj brez
  vednosti uporabnika.

### Objava na strežnik

- Strežnik: `root@46.225.187.162`, mapa `/srv/turnirko`, naslov
  `https://turnirko-nt.si`. Claude se nanj ne more prijaviti sam (SSH ključ ima
  geslo): ukaz zažene z `run_in_terminal` (`ssh root@46.225.187.162 "..."`),
  uporabnik v zavihek vpiše geslo ključa, izpis se bere z `read_terminal`. Pod
  Windowsom odpadejo narekovaji v narekovajih, zato daljše ukaze daj v skripto v
  repozitoriju in jo na strežniku samo pokliči.
- **Testno okolje** (`https://test.turnirko-nt.si`, od 10. 11. 2026; celotna
  navodila `docs/TESTNO-OKOLJE.md`): koda gre v produkcijo SAMO prek testa.
  Pot: (1) commit + push na vejo `test`; (2) na strežniku
  `bash /srv/turnirko-test/skripte/test/objavi.sh` (zgradi, zažene, preveri;
  po potrebi prej `bash /srv/turnirko/skripte/test/osvezi-bazo.sh`, kopija
  produkcijske baze); (3) uporabnik pregleda test; (4) **šele po njegovem
  izrecnem »v produkcijo«** `git push origin <preizkušen-commit>:main` (brez
  preklopa na `main`, brez `--force`; commit izpiše `objavi.sh`); (5)
  `bash /srv/turnirko/skripte/test/v-produkcijo.sh`, ki zavrne objavo, če `main`
  vsebuje kaj, česar test ni zgradil. Skripte so `bash`, ne `sh` (strežnikov `sh`
  je `dash`, ne pozna `pipefail`). Test je kopija prave baze z naslovi pravih
  igralcev: pošta in plačila sta v njem ugasnjena in tega v `compose.test.yaml`
  ne spreminjaj; kode za registracijo bere `skripte/test/koda.sh`. Ukazov
  `docker compose` v `/srv/turnirko-test` ne poganjaj z `-f compose.yaml`
  (to je produkcijski compose).
- Ročna redna objava brez testa (`git pull --autostash && docker compose up -d
  --build` v `/srv/turnirko`) je od 10. 11. 2026 **izjema za sili**, ne pot;
  zahteva izrecen ukaz uporabnika. `--autostash` je obvezen: `Caddyfile` na
  strežniku ima lokalen blok za projekt Pikado. Gradnja traja ~1,5 min,
  aplikacija je ~20 s nedosegljiva (Caddy kaže stran Vzdrževanje). Migracije
  Flyway tečejo ob zagonu same.
- Pred objavo, ki vsebuje migracijo, naredi ročno kopijo:
  `skripte/varnostna-kopija.sh rocno` (gre tudi v oblak). Migracije so
  enosmerne: povratek kode ne razveljavi sheme. Napako v migraciji popravi z
  NOVO migracijo ali z obnovo kopije (`docs/OBJAVA.md`, »Obnova v sili«).
- Po objavi: `docker compose ps` (`app` in `baza` morata biti `healthy`),
  `skripte/preklop/po-preklopu.sh 10` (napake v dnevnikih in zaporedja) in
  javni API.
- Kopije: cron na strežniku (`skripte/baza/turnirko-kopije.cron`) vsako uro,
  ponoči in mesečna vaja obnove; šifrirano odnašanje v Backblaze B2 (`rclone`,
  `TURNIRKO_ODNASANJE` v `.env`). Gesla baze in `rclone.conf` hrani uporabnik v
  upravitelju gesel.

### Česa na strežniku in v bazi ne smeš

- **Nikoli `docker compose down -v`, `docker volume rm` ali `docker volume
  prune`**: zvezek `turnirko_turnirko_baza` JE baza. Brez izrecnega ukaza
  uporabnika ga ne diraj.
- Uveljavljene migracije se ne spreminjajo (Flyway preveri kontrolno vsoto).
  Nova migracija gre **samo** v
  `zaledje/src/main/resources/db/migration/postgresql/`; mapa `sqlite/` je
  zamrznjena do faze 6 (pospravljanje, po 16. 11. 2026).
- Aplikacija v produkciji teče kot `turnirko_app` (samo SELECT, INSERT, UPDATE,
  DELETE); migracije kot `turnirko_lastnik`. Migracija ne sme predpostavljati
  drugih pravic.
- `uvoz`, `popravek` in `prenos` ne poganjaj nad produkcijsko bazo brez
  kopije in izrecnega ukaza uporabnika.

### Lokalno delo

- Testi: `cd zaledje` in `.\mvnw.cmd test` z
  `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`. Tečejo na
  vgrajenem PostgreSQL, Docker ni potreben.
- Frontend: `cd vmesnik` in `npm run dev` (vrata 5173; za podatke potrebuje
  zaledje na vratih 8080).
- Zaledje lokalno potrebuje PostgreSQL 18 (README, razdelek Zagon). Prenosna
  namestitev je v `C:\Users\Miha\Desktop\Turnirko\pg18` (brez `psql`); zagon:
  `pg18\bin\pg_ctl.exe -D pg18\podatki -o "-p 5433" -l pg18\pg.log start`.
  Lokalna razvojna baza je testna in sme izgubiti podatke.

## Trdna pravila

- Shemo baze spreminjaj SAMO z novo Flyway migracijo v `zaledje/src/main/resources/db/migration/postgresql/`
  (nikoli ne spreminjaj obstoječih migracij; `ddl-auto` ostane `none`).
  **Baza je PostgreSQL** (prenova baze, `docs/PRENOVA-BAZE.md`). Prva datoteka
  je izhodišče `V53__izhodisce.sql` (shema, enaka SQLite po V53); prva nova
  migracija je V54. **Produkcija teče na PostgreSQL od 2. 11. 2026** (preklop: `docs/PREKLOP.md`).
  Nova migracija gre SAMO v `postgresql/`; mapa `sqlite/` je zamrznjena do
  faze 6 (pospravljanje) in po njej ne teče več nič.
- Poslovna logika sodi v `storitve`, kontrolerji so tanki adapterji.
- Navzven gredo samo DTO-ji, nikoli entitete. Osebni podatki (e-pošta, telefon,
  naslov, datum rojstva) ne smejo v javne poglede.
- Vsaka sprememba domenske logike (žreb, rezultati, rating) MORA imeti test.
  Testi: `cd zaledje && mvnw test`. Tečejo na pravem PostgreSQL, ki ga ob
  začetku zažene `TestnaBaza` (zonky embedded-postgres, brez namestitve in
  Dockerja); v testnem profilu je `ddl-auto=validate`, zato vsako neujemanje
  entitete s shemo ustavi testni zagon.
- Statuse določa strežnik; prehodi stanj se preverjajo v storitvah.
- **Časi so `timestamp(3)`, datumi `date`, logične vrednosti `boolean`**
  (izhodišče V53). Entiteta dobi `LocalDateTime`/`LocalDate` od Hibernata;
  **izvorna poizvedba** (`List<Object[]>`, `getSingleResult`) pa vrne tip
  gonilnika, zato gre vsak čas iz nje skozi `pomozno/CasBaze` — nikoli
  `toString()` in rezanje niza (tako je bilo v SQLite, kjer so bili časi
  besedilo s pretvornikom `CasKotBesedilo`). Zapisi iz časa SQLite, ki jim je
  gonilnik odrezal uro, so ostali polnoč. Preverba ure v `@Transactional`
  testu še vedno potrebuje `flush()` + `clear()` (entiteta pride iz
  predpomnilnika seje; glej `uraTerminaPrezivizapisVBazo`,
  `kodaInCasiRacunaPrezivijoZapisVBazo`).
- **Leno nalaganje:** kontrolerji pretvarjajo entitete v DTO-je IZVEN transakcije.
  Vsaka poizvedba, katere rezultat gre v DTO, mora z "join fetch" vnaprej naložiti
  vse povezave, ki jih DTO bere (kraj, igralca, klub) — sicer na pravem strežniku
  poči LazyInitializationException, testi z @Transactional pa tega NE ujamejo.
  Regresijski testi za to: `LenoNalaganjeTest` (namenoma brez @Transactional).
  Pri ekipnih dogodkih jih drži `LenoNalaganjeEkipnoTest`. **Pot
  `x.povezava.id` v JPQL (Hibernate 7) bere tuji ključ brez združitve** —
  vrstica z NULL povezavo (srečanje turnirja nima lige, prijava ekipe nima
  igralca) zato vrne `null` in ne izpade iz rezultata; poizvedba, ki po njej
  združuje, potrebuje izrecen `JOIN` ali `IS NOT NULL`.

## Kazalo pravil po področjih

Preden spreminjaš področje, preberi ustrezni dokument v `docs/pravila/`.

| Preden spreminjaš … | preberi |
|---|---|
| Igralci: osebni podatki, dvojniki, čistka, datum rojstva, imena, abeceda, starostni pas | `docs/pravila/igralci.md` |
| Računi, gesla, registracija in potrditev e-pošte, stran /racuni, pogoji in zasebnost | `docs/pravila/racuni-in-registracija.md` |
| Vloge, lastništvo zapisov, organizatorski pregled, dostop organizatorjev do tekmovanj | `docs/pravila/vloge-in-dostopi.md` |
| Turnirji: prikazni status, sistemi, izračun kroga, nosilci, dvojice, kategorije, ročni žreb, TOP, odjava, ekipni dogodek | `docs/pravila/turnirji-in-zreb.md` |
| Lige: format srečanja, prehodi, kvalifikacije, ekipe, razpored, termini, prestavitve, potek, lestvice, kader, menjave, brez boja, končnica | `docs/pravila/lige.md` |
| Popravek rezultata, razveljavitev zapisnika, vnos za nazaj, odstop, neodigrane tekme, točke po nizih | `docs/pravila/rezultati-in-zapisniki.md` |
| Turnirko rating v zaledju: raven, sidra, novinci, vrnitev, dnevnik, lestvice, neaktivnost, preračun, obračun | `docs/pravila/rating.md` |
| Rating v vmesniku: graf, profil, napoved tekme, /rating, /o-ratingu, lestvica na telefonu | `docs/pravila/rating-vmesnik.md` |
| Sistem SV regija | `docs/pravila/sv-regija.md` |
| Uvoz in sinhronizacija (Stupa, stara stran NTZS), uvoženo samo za branje, stran /uvoz, klubi | `docs/pravila/uvoz.md` |
| Slike in logotip tekmovanja, profilna slika, pokrovitelji | `docs/pravila/slike-in-pokrovitelji.md` |
| Naročnine, cene, Stripe, kode popusta, strani /narocnina | `docs/pravila/narocnine-in-placila.md` |
| Maskota in oglas Igralec Premium, preizkus Premium in poziv k računu | `docs/pravila/premium-igralca.md` |
| Domača stran, koledar, spremljane lige in obvestila po kolu, Zanimivosti, Ena na ena | `docs/pravila/domaca-stran.md` |
| Vmesnik splošno: oblikovni sistem, barve, pisave, navigacija, telefon, filtri, izbira z vpisom, sklanjanje, preverba builda | `docs/pravila/vmesnik.md` |
| Vmesnik turnirjev in lig: stopničke, udeleženci, zavihki, klik na tekmo, zapisnik, lestvica in glava lige | `docs/pravila/vmesnik-tekmovanja.md` |
| Natisljivi listki tekem in skupin, žreb mreže za tisk, izpis rezultatov | `docs/pravila/tisk.md` |
| Objava na splet: Docker, Umami, profil splet, predogled povezave, ikone, baza in vloge, HSTS, skrivnosti | `docs/pravila/objava.md` |

## Kontekst projekta

- Razvojna strategija in prioritete: `docs/RAZVOJNI-NACRT.md`.
- Pravilnika NTZS (izvlečeno besedilo): `docs/pravilniki/pst.txt` in `pot.txt` —
  Priloge A/B/C v PST so predloge za prihodnji skupinski sistem in žreb z nosilci.
- Stari prototip (referenca, ne spreminjaj): `../poskus2` in `../aplikacija-turnirji`.
