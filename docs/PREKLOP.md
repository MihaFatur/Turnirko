# Preklop Turnirka s SQLite na PostgreSQL

Navodilo za lastnika. Predpostavlja, da selitve baze se nisi delal; vse, kar
potrebujes, je v tem dokumentu in v skriptah `skripte/preklop/`. Ozadje,
odlocitve in meritve so v [PRENOVA-BAZE.md](PRENOVA-BAZE.md); postavitev
streznika in vsakdanje vzdrzevanje baze v [OBJAVA.md](OBJAVA.md).

**Kratko:** v enem veceru (~30 minut izpada) se vsi podatki iz datoteke SQLite
prenesejo v bazo PostgreSQL, ki tece v svojem vsebniku. Pred tem sta dve
generalki, med preklopom pa vsak korak merimo in preverjamo. Do trenutka, ko
sam potrdis, je povratek na staro stanje ena skripta.

## Izvedeno: 2. 11. 2026

Preklop je bil opravljen v enem popoldnevu in potrjen ob 15.57. **Izpad za
gledalce je trajal 57 sekund** (aplikacija ustavljena 15.33.23, spet zdrava
15.34.20; Caddy je ves cas kazal stran Vzdrzevanje). Koraki: kopija SQLite po
ustavitvi 2 s, Flyway in prenos 38 s (45 tabel, 803.809 vrstic, nobena tabela
izpuscena), zagon aplikacije 17 s, dim-test 9 s (40 odgovorov API-ja enakih po
vsebini). Po preklopu: 0 napak v dnevnikih, aplikacija dela kot `turnirko_app`,
nobeno zaporedje (identity) ne zaostaja za najvecjim id-jem (`po-preklopu.sh`),
kopija `pg_dump` (9,6 MB) se obnovi v 4 s z vsemi 46 tabelami in 803.821
vrsticami (`vaja-obnove.sh`). Stara baza je ohranjena kot
`podatki/turnirko.db.pred-preklopom-20261102-142951` (hrani 3 mesece).

**Napake, ki jih je ujel prvi pravi zagon na strezniku** (Dockerja lokalno ni
bilo), vse popravljene in v `main`:

1. `00-vloge.sh`: `datlocprovider || ':'` (tip `"char"`) odpove, treba je
   `::text`. Ker docker-entrypoint pri ponovnem zagonu prve nastavitve ne
   ponovi, je baza ostala brez vlog.
2. Zdravstveni preizkus baze je gledal vtic, na katerem posluša tudi zacasni
   streznik prve nastavitve: zdaj `pg_isready -h 127.0.0.1`.
3. `pred-preklopom.sh` je kot tabeli stel dva pogleda razsiritve
   `pg_stat_statements`.
4. `idjiLig`: SQLite ga vrne v vrstnem redu zapisa, PostgreSQL po `ORDER BY`;
   `razlika-posnetkov.sh` ga primerja kot mnozico, vse drugo ostane razlika.
5. `preklop.sh`, korak 3: shramba slik Dockerja 29 (containerd) po gradnji nove
   slike z istim imenom stare ne hrani vec; `docker tag` in `docker commit`
   odpovesta. Skripta staro sliko zgradi iz oznake `sqlite-zadnja` (132 s) in
   preveri `java -version`, preden ustavi aplikacijo.
6. `varnostna-kopija.sh`: `pg_restore -l | grep -q` skupaj s `pipefail` da
   SIGPIPE in dobra kopija propade. Kazalo gre zdaj v datoteko.

**Stanje streznika, pomembno za naprej:** 3,7 GB RAM (dodan 2 GB `/swapfile`),
Dockerjev predpomnilnik gradnje je bil 22 GB (`docker builder prune`), na istem
streznikih tece se projekt Pikado (omrezje `turnirko_default`, blok v
`Caddyfile`, zato `git pull --autostash`), pred preklopom ni tekla nobena
samodejna kopija (cron je bil prazen). Windows OpenSSH odstrani znak `"` iz
ukaza: daljse ukaze na streznik daj v skripto v repozitoriju.

**Odnasanje kopij izven streznika je nastavljeno (2. 11. 2026):** Backblaze B2 v EU, sifrirano z
`rclone` crypt (`skripte/baza/nastavi-odnasanje.sh`), prva kopija v oblaku obnovljena (46 tabel,
803.821 vrstic, vsa stetja se ujemajo). `rclone.conf` mora biti shranjen tudi izven streznika.

**Odprto po preklopu:** gesla baze (`.env`) v upravitelj gesel; kopijo SQLite
(`kopije/sqlite-pred-preklopom-20261102-142951.db`) prenesti s streznika in jo
hraniti 3 mesece; teden opazovanja (`skripte/baza-stanje.sh`, `po-preklopu.sh`); faza 6
(pospravljanje) po dveh tednih brez tezav, torej po 16. 11. 2026.

## Kaj se spremeni in zakaj

Danes je vsa baza ena datoteka (SQLite), v katero lahko naenkrat pise samo en
vnos. Za eno kolo lige to zadosca; za 10 hkratnih vnasalcev in 500 gledalcev
na enem turnirju ne. PostgreSQL je baza, ki to zna: tece kot loceno
programsko okolje, vec vnosov hkrati bere in pise, iz kopije se da povrniti
do minute natancno (kasneje) in ima vloge s pravicami.

Za uporabnika se ne spremeni nic: iste strani, isti racuni, isti rating.
Preverjeno (28.-30. 10. 2026, kopija produkcije s 27. 10.): vseh 17.169
javnih poti API-ja da na SQLite in na PostgreSQL **iste odgovore do bajta**.

## Pregled: kaj te caka, po vrstnem redu

| # | Kje | Kaj | Casa |
|---|---|---|---|
| 0 | racunalnik | **narejeno (2. 11. 2026):** vsa koda za PostgreSQL je commitana na veji `prenova-baze` in zlita z `main` (do commita `2efea30`), vseh 1125 testov je zelenih | – |
| 1 | streznik | objavi trenutni `main` (SQLite) kot ponavadi in preveri, da deluje | 15 min |
| 2 | racunalnik | oznaka `sqlite-zadnja`, pull request `prenova-baze` → `main` | 15 min |
| 3 | ti | odlocitve: termin, shramba za kopije (razdelek 1) | – |
| 4 | streznik, dan prej | `.env`, `git pull`, `pred-preklopom.sh` (razdelek 3) | 30–60 min |
| 5 | streznik, dan prej | `generalka.sh` (razdelek 4) | 20 min |
| 6 | streznik, vecer | `preklop.sh` (razdelek 5) | ~30 min izpada |
| 7 | streznik, po | cron kopij, teden opazovanja (razdelek 6) | 5 min / dan |
| 8 | racunalnik, 2 tedna po | pospravljanje (PRENOVA-BAZE.md, faza 6) | 2–3 dni |

**Rezultati, vneseni od zadnjega pogovora, ne povzrocajo tezav.** Preklop ne
prenasa starega posnetka: podatke prebere iz kopije SQLite, narejene sele
**po ustavitvi aplikacije** (korak 5 preklopa). Vse, kar je vneseno do tistega
trenutka, je v kopiji in zato v PostgreSQL; po ustavitvi vnosov ni.

## 1. Termin in odlocitve

- **Termin:** vecer med tednom, ko ni kol lig ne turnirjev; preveri koledar
  SNTL in Savinja lige. Organizatorjem teden prej sporoci: »Turnirko v sredo od
  21.00 do 21.30 ne bo dosegljiv; vnosi pred tem so varni.« Vnos rezultata ob
  izpadu ni mogoc; kar je takrat na papirju, se vnese po 21.30.
- **Shramba za kopije** (OBJAVA.md, razdelek 7): kopije na istem strezniku
  propadejo skupaj z njim, zato jih vsako noc odnasa `rclone` v sifrirano
  shrambo v EU (Hetzner Object Storage ali Backblaze B2 EU, ~1 EUR/mesec).
  Preklopa to ne ustavi (kopija SQLite iz preklopa se vedno prenese tudi z
  `scp`), a brez nje prvi teden ni varnostne mreze pred izgubo streznika —
  uredi jo cim prej, najbolje pred preklopom.
- **Pomnilnik streznika:** na strezniku `free -h` in `df -h`. Priporoceno
  4 GB RAM in 10 GB prostega diska; pri 2 GB glej razdelek 3.

## 2. Priprava kode

### 2.1 Objavi trenutni main (SQLite) — korak 1

Prenos zahteva, da produkcijska baza SQLite **natanko ustreza** shemi, ki jo
pozna nova koda (isto stevilo zadnje migracije). Danes je to **V61**. Zato
najprej objavi `main` na strezniku, tako kot vedno:

```bash
cd /srv/turnirko && git pull && sudo docker compose up -d --build
```

in preveri na strezniku, kaj res tece:

```bash
git log -1 --oneline
sudo sqlite3 -readonly podatki/turnirko.db "SELECT max(CAST(version AS INTEGER)) FROM flyway_schema_history;"
```

Izpis mora biti `61` (ali visje, ce so v `main` nastale se nove migracije; vsaka
mora imeti dvojnico v veji — glej 2.3). Preskoci, ce ze tece zadnji `main`.

### 2.2 Oznaka in zlitje — korak 2

Na svojem racunalniku, v mapi `turnirko-postgresql` (to je veja `prenova-baze`):

```bash
git status                                  # "nothing to commit"
git push -u origin prenova-baze
git tag sqlite-zadnja <commit-ki-tece-na-strezniku>
git push origin sqlite-zadnja
```

Oznaka `sqlite-zadnja` kaze na zadnjo razlicico za SQLite: ce preklop spodleti,
`nazaj.sh` iz nje vzame stari `compose.yaml`. Commit, ki tece na strezniku, je
tisti iz `git log -1` v koraku 2.1.

Nato **pull request** na GitHubu: `prenova-baze` → `main`, »Merge pull request«
(ali `gh pr create --base main --head prenova-baze`). Od zdaj naprej je `main`
razlicica za PostgreSQL; **na strezniku je ne vleci, dokler ne pride dan pred
preklopom** (razdelek 3).

### 2.3 Ce se je `main` medtem premaknil

Ce so v `main` po 2. 11. 2026 nastali novi commiti, jih zlij v vejo **pred**
pull requestom:

```bash
git checkout prenova-baze && git merge main
cd zaledje && .\mvnw.cmd test               # 1125+ testov mora biti zelenih
```

Posebej pazi na dvoje (to je tudi tisto, kar je bilo treba popraviti pri zlitju
2. 11.):

- **Vsaka nova migracija SQLite potrebuje dvojnico** v
  `zaledje/src/main/resources/db/migration/postgresql/` (isto stevilo in ime;
  tipi kot v `V53__izhodisce.sql`: `bigint GENERATED BY DEFAULT AS IDENTITY`,
  `timestamp(3)`, `boolean`, `bytea`, `double precision`). Test `PrenosTest`
  takoj pokaze, ce se dvojnica in izvirnik razideta po tabelah ali stolpcih.
- **Izvorni SQL v novi kodi** (`nativeQuery`, `INSERT OR IGNORE`,
  `last_insert_rowid`, primerjava logicnih stolpcev z 0/1, `ORDER BY` z
  `NULLS FIRST`, `SELECT DISTINCT` brez `ORDER BY`) in **pravila »najvec N na
  racun«** (stetje pred zapisom): zaklep iz `Zaklepi.java` in test v
  `SocasnostTest` (primer: `dvaHkratnaSpremljanjaIstegaRacunaSprejmetaEnega`).

Dokler preklop ni opravljen, je vsaka nova migracija v `main` dvojno delo;
zato je smiselno razvoj novih funkcij ustaviti od 2.1 do preklopa.

## 3. Priprava streznika (dan prej) — korak 4

Vse spodaj na strezniku v mapi projekta (`/srv/turnirko`), z `sudo`.

```bash
cd /srv/turnirko
sudo apt install -y sqlite3 rclone           # sqlite3 samo za kopijo stare baze
nano .env                                    # dodaj gesla baze (OBJAVA.md, razdelek 4)
git pull && git fetch --tags                 # zdaj pride koda za PostgreSQL
sudo skripte/preklop/pred-preklopom.sh
```

V `.env` dodaj tri **razlicna** gesla (vsako `openssl rand -base64 24`):
`TURNIRKO_BAZA_ADMIN_GESLO`, `TURNIRKO_BAZA_LASTNIK_GESLO`,
`TURNIRKO_BAZA_APP_GESLO` (vzorec je v `.env.primer`). Gesla shrani tudi v
upravitelj gesel — brez njih baze ne obnovis.

`pred-preklopom.sh` ne ustavi in ne spremeni delujoce strani. Naredi pa vse, kar
se da narediti vnaprej: potegne sliko baze, **zazene prazno bazo** (nova
storitev, stari aplikaciji ne moti), preveri vlogi, ali je baza prazna, ali je
Caddyfile veljaven in ga ponovno prebere (nova stran »Vzdrzevanje«), ali se
shemi ujemata in **zgradi novo sliko aplikacije** (nekaj minut). Ce kaj
manjka, napise, kaj; popravi in zazeni znova. Skripte imajo ze nastavljen bit
za izvajanje (`git`); ce se `Permission denied`, `chmod +x skripte/*.sh skripte/*/*.sh`.

Pomnilnik: skripta izpise velikost streznika. Pri 4 GB je vse privzeto; pri
2 GB dodaj v `.env` `BAZA_SHARED_BUFFERS=256MB`, `BAZA_CACHE=512MB`,
`BAZA_DELO=8MB` (vrstice so v `.env.primer`).

## 4. Generalka 2 (dan prej)

```bash
sudo skripte/preklop/generalka.sh
```

Isto kot preklop, a v loceni postavitvi (`compose.generalka.yaml`, projekt
`generalka`): kopija zive baze, prazen zvezek, prenos, zagon aplikacije na
`127.0.0.1:18080`, primerjava odgovorov API-ja s produkcijo. Delujoce strani
se ne dotakne in ne poslje nicesar na internet. Na koncu izpise cas vsakega
koraka; vsota korakov 3-6 je izpad. **Preklop se zgodi sele, ko generalka
gre od zacetka do konca brez rocnega posega** in z rezultatom `ENAKO`.

Prva generalka je bila narejena lokalno nad kopijo produkcije (glej spodaj).
Tej na strezniku je potrebno verjeti vec, ker pozna prav ta streznik, Docker in
diske.

## 5. Veceri preklopa

```bash
cd /srv/turnirko
sudo skripte/preklop/preklop.sh
```

Skripta se pred vsakim nepovratnim korakom ustavi in vprasa (vpisi `DA`).
Koraki:

| # | Korak | Kaj vidi gledalec |
|---|---|---|
| 1 | predpogoji (`pred-preklopom.sh --hitro`) | stran dela |
| 2 | posnetek API-ja pred preklopom (~50 odgovorov, SHA-256) | stran dela |
| 3 | stara slika aplikacije se shrani (`turnirko-app:pred-preklopom`) | stran dela |
| 4 | aplikacija se ustavi | stran »Turnirko je v vzdrzevanju« |
| 5 | kopija SQLite (`kopije/sqlite-pred-preklopom-*.db`) = **povratna tocka** | vzdrzevanje |
| 6 | Flyway ustvari shemo, ukaz `prenos` prenese podatke; porocilo mora biti **zeleno** | vzdrzevanje |
| 7 | zagon nove aplikacije | vzdrzevanje, nato stran dela |
| 8 | dim-test: posnetek po preklopu mora biti enak posnetku pred | stran dela |
| 9 | prva rocna preverba, nato potrditev | stran dela |

**Korak 6** je jedro. Ukaz `prenos`: prebere kopijo SQLite (samo za branje),
vsako vrednost pretvori v pravi tip (0/1 v logicno vrednost, stiri oblike
casa v `timestamp`, ...), vrednost, ki se ne pretvori, **ustavi** prenos;
nalozi tabele z `COPY` v eni transakciji; nato po tabelah primerja stevilo
vrstic in kontrolno vsoto SHA-256, izracunano iz obeh baz. Samo ce je vse
ujeto, se transakcija potrdi. Pri rdecem poročilu v PostgreSQL ni spremenjeno
nic - `preklop.sh` ti pove, kaj je narobe, in ponudi `nazaj.sh`.

**Korak 9:** odpri stran, prijavi se kot admin, na testnem turnirju vnesi
rezultat tekme in poglej, da se je rating spremenil. Sele po tem vpises `DA`:
stara baza dobi ime `podatki/turnirko.db.pred-preklopom-<cas>` in od tu naprej
**ni vec povratka**.

### Povratek

```bash
sudo skripte/preklop/nazaj.sh
```

Do potrditve v koraku 9 vrne staro aplikacijo (shranjena slika, `compose.yaml`
iz oznake `sqlite-zadnja`) nad nespremenjeno datoteko SQLite. Vnosi, nastali
po preklopu, so izgubljeni - zato je najbolje, da povratek odlocis takoj po
koraku 8 ali 9. Po potrditvi se napake popravljajo naprej, ne nazaj.

## 6. Po preklopu

1. Kopije v cron in odnasanje (OBJAVA.md, razdelek 7); `varnostna-kopija.sh noc`
   enkrat rocno, v dnevniku mora pisati `odneseno`.
2. **Teden opazovanja:** vsak dan `skripte/baza-stanje.sh` (ali v cron ob 8.00):
   starost kopij, velikost baze, najpocasnejse poizvedbe, stevilo pocasnih
   poizvedb in zaklepov, napake 409/5xx. Opozorila: nocna kopija starejsa od
   26 ur, napake 5xx, pocasne poizvedbe nad 250 ms, ki se ponavljajo.
3. Kopijo SQLite (`kopije/sqlite-pred-preklopom-*.db`) shrani **izven
   streznika** (`scp`) in jo hrani 3 mesece.
4. Prvic mesecna vaja obnove: `skripte/vaja-obnove.sh`.
5. Po dveh tednih brez tezav: pospravljanje (PRENOVA-BAZE.md, faza 6).

## Ce gre kaj narobe

| Simptom | Vzrok in ukrep |
|---|---|
| `pred-preklopom.sh`: »SQLite je na V.., koda na V..« | migracija brez dvojnice (ali produkcija ni na zadnji razlicici). Dodaj dvojnico v `db/migration/postgresql/` ali posodobi produkcijo |
| `prenos`: »N vrednosti se ne da pretvoriti« | seznam v porocilu pove tabelo, stolpec in id vrstice; popravi vrednost v SQLite kopiji ali pretvornik (`Pretvorba.java`) in ponovi. PostgreSQL je ostal prazen |
| `prenos`: »Ciljna tabela .. ni prazna« | v bazi je ze kaj. Prenos gre samo v svezo bazo: izprazni zvezek (pred-preklopom.sh pove ukaz) in ponovi |
| baza ne postane zdrava | `docker compose logs baza`; najpogosteje premalo pomnilnika ali disk poln; pri prvem zagonu napaka v `00-vloge.sh` (jasno napise) |
| dim-test: razlike | najprej poglej, ali se je med posnetkoma res kaj spremenilo (datum, vnos). Drugace `nazaj.sh` in javi razliko (pot in oba odgovora sta v `dnevniki/preklop/`) |
| aplikacija ne postane zdrava | `docker compose logs app`: napaka gesla vloge (`TURNIRKO_BAZA_APP_GESLO`), povezava z bazo ali Flyway |
| po preklopu pocasne strani | `baza-stanje.sh`: pocasne poizvedbe; PRENOVA-BAZE.md razdelek 9 (N+1, predpomnilnik) |

## Meritve generalke 1 (lokalno, 30. 10. 2026)

Kopija produkcije s 27. 10. 2026 (79 MB, 798.581 vrstic v 34 tabelah), PostgreSQL
18.6 na istem racunalniku kot aplikacija (brez omrezja vsebnikov):

| Meritev | Rezultat |
|---|---|
| prenos (Flyway je ze naredil shemo): pregled, COPY, preverba | **19 s**, porocilo zeleno, vseh 34 tabel ujetih po vrsticah in SHA-256 |
| odrezek na milisekunde | 289 vrednosti v 13 stolpcih petih tabel (`narocnina`, `potrditvena_koda`, `racun_dogodek`, `uporabnik`, `uvoz_zagon`): casi zapisa z nanosekundami iz `LocalDateTime.now()`; v porocilu je vsak stolpec posebej, v ostalih 29 tabelah ni odrezka |
| primerjava API-ja nad **vsemi** zapisi (turnirji, dogodki, lige, srecanja, profili, koledar, ...) | 17.169 poti, 281 MB JSON, **0 razlik**. Prvi poskus je nasel eno razliko (vrstni red lig igralca na lestvici: `SELECT DISTINCT` brez `ORDER BY`); 9 takih poizvedb je zdaj urejenih po id-jih, polje `idjiLig` pa se primerja kot mnozica |
| polni preracun ratinga (97.058 tekem, 1.866 igralcev) | PostgreSQL 639 s; SQLite glej PRENOVA-BAZE.md |

**Ponovitev z zdruzeno kodo (2. 11. 2026, `main` 2efea30 + veja, shema V61):**
kopija produkcije je z **dejanskimi migracijami SQLite** pripeljana iz V53 na V61
(aplikacija iz `main`; V55 in V58 prezideta tabele), nato prenesena z ukazom
`prenos` v PostgreSQL: **45 tabel, 798.581 vrstic, porocilo zeleno, 25 s**.
Primerjava API-ja: aplikacija iz `main` na SQLite proti zdruzeni aplikaciji na
PostgreSQL nad **17.172 potmi** (vsi zapisi), 281,6 MB JSON, **0 razlik**.
Testi na zdruzeni kodi: 1125 zelenih (vkljucno z novimi pravili paketa Navijac in
sočasnostnim testom za zaklep racuna).

Izpad na strezniku bo daljsi od teh 19 s (ustavitev, kopija, zagon aplikacije,
prva preverba); generalka 2 pokaze pravo vsoto.
