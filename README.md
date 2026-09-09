# Turnirko

Sistem za vodenje namiznoteniških turnirjev. Cilj projekta je profesionalno
orodje za turnirski dan (žreb, rezultati v živo, izpisi) po pravilih NTZS —
glej [docs/RAZVOJNI-NACRT.md](docs/RAZVOJNI-NACRT.md) za celotno strategijo.

## Struktura projekta

```
turnirko/
├── zaledje/     Spring Boot (Java 21) - poslovna logika, REST API, baza
├── vmesnik/     React + TypeScript (Vite) - uporabniski vmesnik
└── docs/        razvojni nacrt in pravilniki NTZS (PST, POT)
```

## Zaledje (backend)

### Zagon

```
cd zaledje
mvnw spring-boot:run
```

API teče na `http://localhost:8080/api/v1`. Baza (SQLite) se ustvari
samodejno v `zaledje/podatki/turnirko.db`, shemo postavi Flyway.

### Prijava (administrator / gost)

Gost dostopa brez prijave in ima **samo bralni dostop** (turnirji, lestvica,
mreže, rezultati, 1-na-1). Vse spremembe (žreb, vnos rezultatov, urejanje
igralcev in šifrantov) sme **samo prijavljen administrator (sodnik)**.

Ob prvem zagonu se ustvari začetni administrator (privzeto `admin` / `admin`).
**Privzeto geslo čim prej zamenjaj** prek nastavitve `turnirko.admin.privzeto-geslo`
(in `turnirko.admin.uporabnisko-ime`) v `application.properties`. Prijava teče
prek HTTP Basic; strežnik uveljavlja pravice na vsaki končni točki (GET javno,
POST/PUT/DELETE samo `ROLE_ADMIN`).

Namizna (portable) različica z avtomatskim odpiranjem brskalnika:

```
mvnw spring-boot:run "-Dspring-boot.run.profiles=namizni"
```

### Testi

```
cd zaledje
mvnw test
```

Testi tečejo na ločeni bazi v `target/` in nikoli ne vplivajo na prave podatke.

## Vmesnik (frontend)

### Zagon (razvoj)

```
cd vmesnik
npm install        # samo prvič
npm run dev
```

Vmesnik teče na `http://localhost:5173`; klici na `/api` se prek Vite
proxyja posredujejo zaledju na vratih 8080, zato mora zaledje teči zraven.
Preverba tipov in produkcijska gradnja: `npm run build` (izdelek v `dist/`).

### Arhitektura

| Mapa | Vloga |
|---|---|
| `src/api` | tipi (zrcalijo DTO-je zaledja), ovoj okoli fetch (z Basic prijavo), funkcije končnih točk |
| `src/avtentikacija` | kontekst prijave (gost/administrator), hramba poverilnic |
| `src/strani` | turnirji, turnir, dogodek (prikaz po sistemu), lestvica, 1-na-1, igralci, šifranti |
| `src/komponente` | mreža, lestvica, seznam tekem, kartica tekme, vnos rezultata, prijava, modalna in potrditvena okna |
| `src/pomozno` | oblikovanje datumov, imena kol |

Načela: podatke prek strežnika ureja TanStack Query (predpomnjenje in
osveževanje po vsaki spremembi); obrazci pošljejo vnos, dokončna pravila
preverja zaledje in vrne slovensko sporočilo, ki se prikaže ob obrazcu;
za nepovratna dejanja se vedno odpre potrditveno okno.

### Arhitektura

| Paket | Vloga |
|---|---|
| `modeli` | JPA entitete in enumi (preslikava tabel) |
| `repozitoriji` | dostop do baze (Spring Data JPA) |
| `storitve` | poslovna logika — žreb, rezultati, rating, življenjski cikli |
| `kontrolerji` | REST končne točke (tanki adapterji) |
| `dto` | objekti za izmenjavo s frontendom (nikoli surove entitete) |
| `izjeme` | domenske izjeme + globalni prevod v HTTP napake |
| `nastavitve` | CORS, namizni profil |

Ključna načela:

- **Shemo baze upravlja izključno Flyway** (`resources/db/migration/`);
  Hibernate je nikoli ne spreminja (`ddl-auto=none`).
- **Statuse vedno določa strežnik**, odjemalec le sproža prehode.
- **Napredovanje po mreži** teče po eksplicitnih povezavah med tekmami
  (`id_izvor_tekma_1/2` + vloga), ne po formuli — to omogoča tekmo za
  3. mesto in tolažilne tekme.
- **Rating je dnevnik** (`rating_zgodovina`): vsaka sprememba ima zapis,
  ista tekma se nikoli ne obračuna dvakrat.
- **Igralcev se ne briše** — samo arhivira (zgodovina tekem ostane).

### Kaj je podprto / kaj še ne

| Podprto | Nacrtovano (glej RAZVOJNI-NACRT.md) |
|---|---|
| **izločilni** sistem s prostimi mesti (poljubno št. igralcev) | nosilci in žreb po jakostnih točkah |
| **krožni** sistem (vsak z vsakim) z lestvico | dvojice, ekipna tekmovanja |
| **skupine + izločilni** (skupinski del → izločilna mreža) | prijavni roki, mize, urniki, izpisi |
| posebni izidi: w.o., predaja, diskvalifikacija, prosto | uradne jakostne točke NTZS |
| točke po nizih z validacijo (tudi vrstni red nizov) | ovrednotenje vseh mest (3./4. …) |
| klubski ELO z dnevnikom sprememb | |
| **globalna lestvica igralcev** in **pregled 1-na-1** | |
| **prijava administratorja**, bralni dostop za goste | uporabniški vmesnik za več administratorjev |
| več dogodkov na turnir, kategorije po spolu | |

### Sistemi tekmovanja

Sistem se izbere ob ustvarjanju dogodka:

- **Izločilni** – klasična mreža; ob lihem številu prosta mesta (bye).
- **Krožni** – vsak z vsakim (krožna metoda); razvrstitev po lestvici
  (zmage; ob izenačenju odloči le izkupiček med izenačenimi — razlika nizov,
  nato razlika točk).
- **Skupine + izločilni** – igralci se razdelijo v skupine (krožni del),
  po dva najboljša napredujeta v izločilno mrežo (žreb izločilnega dela se
  zgenerira samodejno, ko so odigrane vse skupine).
