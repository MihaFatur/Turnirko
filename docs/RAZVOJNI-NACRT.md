# Turnirko â†’ nacionalni sistem: brutalno iskren razvojni naÄŤrt

**Za:** Miha (solo razvijalec, NTK Savinja)
**Datum:** 20. 7. 2026
**Status dokumenta:** sinteza 3 spletnih raziskav (konkurenca, pravila NTZS, posel/GDPR) in 3 analiz kode (backend, frontend, domena)

---

## 1. Realnost trga

### 1.1 Kaj NTZS uporablja danes

Trg NI odprt. KljuÄŤna dejstva (z nivojem zanesljivosti vira):

- **[POTRJENO]** NTZS od pribl. sezone 2024/25 uporablja **Stupa Events** (ntzs.stupaevents.com, ntzsott.stupaevents.com) za spletne prijave na tekmovanja â€” PST ÄŤl. 31 celo predpisuje, da so prijave moĹľne *samo* prek spletne aplikacije NTZS. Vir: ntzs.si (povezave na domaÄŤi strani), PST v4.6.
- **[POTRJENO]** Tudi vse Ĺˇtiri lige (1.â€“4. SNTL, moĹˇki in Ĺľenske) teÄŤejo prek Stupa Events; lestvice so vdelane neposredno v ntzs.si prek WordPress shortcoda (`leaderboard_table event_id=...`), ki vleÄŤe podatke iz ntzseventsott.stupaevents.com. Vir: izvorna koda strani ntzs.si.
- **[POTRJENO]** Stupa Sports Analytics (Indija) je **uradni partner ITTF** (High Performance & Development od 2021, streaming partner WMTTC Rim 2024) in streĹľĐµ 30+ federacijam, med njimi Ĺ vedski, NorveĹˇki, Danski, Ĺ paniji in sami ETTU. Vir: stupasports.ai, ittf.com.
- **[POTRJENO]** PST ÄŤl. 21 pravi, da NTZS organizatorjem *sama zagotavlja* priporoÄŤeno programsko opremo za vodenje Ĺľrebov â€” konkretni izvajalec v pravilniku ni imenovan.
- **[VERJETNO]** Stupa cen ne objavlja; prodaja teÄŤe prek odnosov in ITTF partnerstva, ne prek primerjav funkcionalnosti.
- **[NEGOTOVO]** Da je prehod na Stupo sveĹľ (2024/25), sklepamo iz nedavne selitve spletiĹˇÄŤa NTZS (stara stran arhivirana na stara.slo-namiznitenis.si). To je indic, ne dokaz.

### 1.2 Kdo je konkurenca ĹˇirĹˇe

- **nuLiga/click-TT** (nu Datenautomaten): celoten nemĹˇki ligaĹˇki sistem od sezone 2005/06, Ĺ vica od 2014/15, avstrijske instance. 20 let reference. **[POTRJENO]**
- **Tournament Software** (Visual Reality): Tournament/League Planner, dominanten v badmintonu (BWF), prisoten v namiznem tenisu, fiksne licenÄŤne cene za organizatorje. **[POTRJENO]**
- **Ratings Central**: brezplaÄŤen, cenjen Bayesov rating sistem, odprt za vsak klub/zvezo (uporablja ga npr. Table Tennis Scotland od 9/2025); zraven brezplaÄŤni turnirski program Zermelo. **[POTRJENO]**
- **NiĹˇna nemĹˇko-govoreÄŤa orodja za turnirski dan** (MKTT, TTT2020, TT-Turnier, Turnonio...): fragmentiran trg, nobeno ni vseevropski standard. **[POTRJENO]**
- **Odprtokodno/generiÄŤno**: evroon/bracket, Challonge (SaaS z API-jem, a brez licenc, ratingov, upraviÄŤenosti â€” ni "federation-grade"). **[POTRJENO]**
- **ÄŚeĹˇki precedens**: federacijski sistem STIS ima tako slab UX, da je skupnost zgradila browser extension (CZ TT Tracker), ki ga popravlja. Nauk: federacijske platforme so pogosto osovraĹľene pri bazi. **[POTRJENO]**

### 1.3 Iskrene realne moĹľnosti

**Prvotni cilj â€” "licencirati Turnirko NTZS kot TA standard" â€” je na horizontu 1â€“3 let dejansko blokiran.** Zamenjal bi mednarodnega, ITTF-partnerskega ponudnika, ki mu zaupa 30+ federacij vkljuÄŤno z ETTU. Solo Ĺˇtudentski projekt tega ne premaga s funkcionalnostmi, ker se ta softver ne prodaja prek funkcionalnosti, ampak prek odnosov in priporoÄŤil. Tudi etablirani evropski ponudniki (nuLiga, Visual Reality) se med sabo ÄŤez meje niso izpodrinili.

**Realne vrzeli, ki obstajajo (in jih Stupa ne pokriva):**

1. **Jakostne lestvice** se Ĺˇe vedno objavljajo kot statiÄŤni .xlsx/.pdf na ntzs.si â€” ni interaktivnega spletnega rankinga. **[POTRJENO]**
2. **Rekreativno-veteranski krog** (7 turnirjev, Ĺˇteje najboljĹˇih 6) je oÄŤitno raÄŤunan roÄŤno in objavljen kot PDF-ji po turnirjih. **[POTRJENO]** (podrobnosti pravilnika za rekreativce: **[VERJETNO]**, PDF je bil bran prek povzetkov)
3. **Organizatorska stran turnirskega dne**: Ĺľrebi po Prilogah A/B/C, tekoÄŤi rezultati v dvorani, izpisi, uradni elektronski izvoz â€” NTZS-jeva "priporoÄŤena programska oprema" je verjetno zastarela (v pravilniku neimenovana; **[NEGOTOVO]**, ÄŤesa konkretno ne vemo).
4. **Offline delovanje**: Stupa je ÄŤisti cloud; med raziskavo 20. 7. 2026 so stupaevents.com, ntzs.stupaevents.com in ntzsott.stupaevents.com ob veÄŤkratnih poskusih vraÄŤali **HTTP 503**. **[POTRJENO kot opaĹľanje]** â€” lahko je nezanesljivost, lahko agresivno blokiranje botov; ne trdimo, da platforma "ne dela", trdimo, da je 100 % odvisna od interneta, telovadnice pa imajo slab Wi-Fi.
5. **Klubski in nesankcionirani turnirji** (memoriali, klubski veÄŤeri, NTK Savinja): pod pragom federacijske pogodbe s Stupo.

**Strategija: partner, ne izpodrivalec.** Cilj ni "zamenjati Stupo", ampak postati (a) najboljĹˇe slovensko orodje za turnirski dan in klubsko/rekreativno sceno, (b) komplement Stupi tam, kjer NTZS dela roÄŤno (veteranske lestvice), in (c) Ĺˇele nato, z referencami, kandidat za karkoli veÄŤjega.

---

## 2. StrateĹˇka pot

### 2.1 Zaporedje

1. **Klubsko orodje (zdaj â†’ ~6 mes.):** Turnirko postane sposoben izpeljati *pravi* slovenski turnir po pravilih PST (skupine + izloÄŤilni del, nosilci, prosta mesta, w.o., zapisniki). Uporabnik Ĺˇt. 1: NTK Savinja. Vsak domaÄŤi turnir je test v produkciji.
2. **Regionalni piloti (~6â€“12 mes.):** 3â€“5 klubov v Savinjski/Ĺ tajerski regiji + rekreativno-veteranski turnirji. Tu je najmehkejĹˇa vstopna toÄŤka: loÄŤen, enostavnejĹˇi pravilnik, roÄŤno raÄŤunana lestvica "najboljĹˇih 6 od 7" je konkreten, majhen, avtomatizabilen problem.
3. **NTZS pitch (~12â€“24 mes.):** Ne "zamenjajte Stupo", ampak: (a) avtomatizacija rekreativno-veteranske lestvice kot majhna plaÄŤana storitev, (b) organizatorsko orodje za turnirski dan, ki uvozi jakostno lestvico (xlsx) in izvozi uradne rezultate v elektronski obliki, ki jo pisarna NTZS Ĺľe sprejema. Interoperabilnost, ne konkurenca.
4. **Regija (leto 3+):** HrvaĹˇka NTZ (hsts.hr) vidno nima moderne platforme **[VERJETNO]** â€” ampak prodajni cikli z volontersko vodenimi zvezami so dolgi, proraÄŤuni Ĺˇe manjĹˇi. To je opcija, ne naÄŤrt.

### 2.2 Monetizacija â€” rangirano, z iskrenimi Ĺˇtevilkami

KljuÄŤni kontekst **[VERJETNO, iz AJPES poroÄŤila]**: NTZS ima letne prihodke ~430â€“510 kâ‚¬, v 2024 ~15 kâ‚¬ primanjkljaja in ~69 kâ‚¬ stroĹˇkov dela (â‰ 1 zaposlen). NTZS *ne more* plaÄŤati enterprise licence. Slovenske zveze pa dokazano kupujejo pri malih lokalnih ponudnikih (ManageSports.eu: KZS, OZS, OKS) â€” z neposredno pogodbo, brez razpisa (druĹˇtvo, ne javni naroÄŤnik; **[VERJETNO]**, sklep iz pravne oblike).

| # | Pot | Model | Realen prihodek/leto |
|---|-----|-------|---------------------|
| 1 | **Servisna pogodba z NTZS** (vzdrĹľevanje + gostovanje + razvoj; vstop prek veteranske lestvice ali organizatorskega orodja) | retainer | 3.000â€“8.000 â‚¬ |
| 2 | **Freemium za klube/organizatorje** (klubski turnirji, rekreativne lige) | 50â€“150 â‚¬/klub/leto ali 10â€“30 â‚¬/turnir | 1.000â€“3.000 â‚¬ |
| 3 | **Mikro-prispevki igralcev** po vzoru DTTB Turnierlizenz (4,99 â‚¬/polletje v NemÄŤiji) â€” deluje SAMO z mandatom zveze; Slovenija ima ~1.000â€“3.000 registriranih tekmovalcev | per-player | 5.000â€“15.000 â‚¬ bruto, samo kot nadgradnja poti 1 |
| 4 | **White-label Balkan / drugi Ĺˇporti z loparji** | pogodbe | leto 3+, Ĺˇpekulativno |

**Iskren strop: 5.000â€“20.000 â‚¬/leto skupaj.** To je resen postranski zasluĹľek in odliÄŤen portfelj/zaposlitveni adut â€” **ni preĹľivetje**. Enkratna prodaja IP ("licenca") NTZS je glede na njihove finance nerealna; edina realna oblika je storitvena. Fundacija za Ĺˇport (digitalizacija) je moĹľen sofinancer â€” preveri razpise.

**Kaj opustiti:** pitch "polna zamenjava Stupe". Kar naprej.

---

## 3. Ciljna arhitektura

### 3.1 Kaj obdrĹľati

- **Slojevit Spring backend** (controller/service/repository/dto) â€” koncept je pravi, izvedba ne (logika je danes v controllerjih).
- **EloService** â€” ÄŤist, deterministiÄŤen, testabilen; postane ena od implementacij za `RatingEngine` vmesnikom.
- **Bogate CHECK omejitve v shemi** â€” miselnost "baza varuje podatke" je pravilna; prenesti jih je treba v Flyway migracije.
- **Portable .exe / lokalni streĹľnik** â€” to je tvoja *edinstvena prednost* proti cloud-only Stupi; ohrani ga kot "dvoransko/offline edicijo", a za loÄŤeno gradnjo, ne kot privzeti model.

### 3.2 Ciljni sklad

| Plast | OdloÄŤitev | Zakaj |
|---|---|---|
| Jezik/framework | Java **21 LTS** + Spring Boot 4 (ostani, a preveri ekosistem â€” springdoc/Testcontainers â€” zgodaj; fallback Boot 3.5.x naj ostane poceni) | virtual threads, daljĹˇa podpora; Boot 4 ekosistem je Ĺˇe tanek |
| Baza (hosted) | **PostgreSQL 16** | SQLite je single-writer datoteka â€” ne prenese veÄŤ-klubskega servisa; migracija je majhna (shema je majhna) |
| Baza (desktop edicija) | SQLite za istimi repository vmesniki | offline dvoranska zgodba |
| Migracije | **Flyway**, ÄŤisti SQL, `ddl-auto=validate` | reproducibilna shema; danes CHECK-i Ĺľivijo samo v .db datoteki |
| Auth | **Spring Security + lastni JWT** (access+refresh, bcrypt/argon2, tabela `uporabnik`); Keycloak/OIDC Ĺˇele, ÄŤe NTZS zahteva SSO | Keycloak je Ĺˇe en stateful servis za enega ÄŤloveka â€” ne Ĺˇe |
| Vloge | NTZS_ADMIN, KLUB_ADMIN (scoped na klub), ORGANIZATOR/SODNIK (scoped na turnir prek `turnir_osebje`), IGRALEC, PUBLIC | `@PreAuthorize` na servisni plasti; URL pravila ne znajo izraziti "sme vpisovati rezultate samo v turnirju X" |
| Tenancy | **Ena skupna nacionalna baza** z lastniĹˇtvom po vrsticah (`organizator_klub_id`, `created_by_user_id`), NE schema-per-tenant | identiteta igralcev in ratingi so inherentno nacionalni/deljeni â€” to JE vrednost produkta |
| API | DTO-ji (nikoli surove entitete), `/api/v1`, Bean Validation, RFC 9457 problem+json, Pageable, springdoc OpenAPI | OpenAPI spec je tudi prodajni artefakt za integratorje NTZS |
| Frontend | React 19 + Vite (ostani) + **TanStack Query v5** + **react-hook-form + zod** + **Tailwind v4 + shadcn/ui** + react-i18next (sl) | odpravi roÄŤni fetching, inline-style kaos, podvojene validacije; Radix reĹˇi a11y |
| Real-time | Faza 1: **polling** (refetchInterval 5â€“10 s); faza 2: **SSE** (SseEmitter); WebSocket samo, ÄŤe kdaj pride live toÄŤkovanje | slovenska skala (desetine gledalcev) ne opraviÄŤuje WebSocketov |
| Offline v dvorani | **NE gradi sync engine-a.** Klientna odpornost: ÄŤakalna vrsta oddaj v IndexedDB + idempotentni endpointi z idempotency kljuÄŤi; .exe kot rezerva za nesankcionirane dogodke | full offline-sync je najveÄŤja kompleksnostna past za solo razvijalca |
| Hosting | 1 VPS (Hetzner/Contabo, ~4 GB, 10â€“20 â‚¬/mes, EU zaradi GDPR) + Docker Compose (app + Postgres + **Caddy** za TLS) | brez Kubernetesa; slovenski promet je majhen |
| Backup/obratovanje | noÄŤni `pg_dump` off-site (Backblaze B2) s testnimi restorei; Actuator health + uptime monitor; JSON logi; konfiguracija prek env spremenljivk | |
| CI | GitHub Actions: `mvn verify` + frontend build + Docker image ob vsakem pushu | |

### 3.3 Diagram (tekstovno)

```
                    â”Śâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ VPS (EU) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
  igralci/gledalci  â”‚  Caddy (TLS) â”€â†’ Spring Boot API (/api/v1, JWT, SSE)                    â”‚
  (telefoni) â”€â”€â”€â”€â”€â”€â”€â”Ľâ”€â”€â†’  javni pogledi: /t/:id/zivo, /t/:id/projekcija (brez prijave)       â”‚
  organizator â”€â”€â”€â”€â”€â”€â”Ľâ”€â”€â†’  direktorska konzola (Ĺľreb, urnik, mize, popravki)                  â”‚
  sodniĹˇka miza â”€â”€â”€â”€â”Ľâ”€â”€â†’  /miza/:id (tablet, veliki gumbi, offline queue v IndexedDB)        â”‚
                    â”‚         â”‚                                                              â”‚
                    â”‚   Service plast (@Transactional, @PreAuthorize, state machine)          â”‚
                    â”‚         â”‚                                                              â”‚
                    â”‚   PostgreSQL 16 (Flyway; RatingZgodovina; audit; @Version)  â”€â”€ pg_dumpâ†’ B2
                    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
  loÄŤen build:  Turnirko Desktop (.exe, jpackage, SQLite, /shutdown SAMO tu) â€” dvoranska rezerva
  interop:      uvoz jakostne lestvice NTZS (.xlsx) za nosilce  |  izvoz uradnih rezultatov (xlsx/pdf)
```

---

## 4. Domenske funkcionalnosti

Trda resnica iz analize pravil: **trenutni format Turnirka (ÄŤisti KO, toÄŤno 2^n igralcev, brez prostih mest, nakljuÄŤen Ĺľreb) ne ustreza NOBENEMU sankcioniranemu formatu NTZS.** Standardni format po PST ÄŤl. 15/22 je: predtekmovanje v skupinah po 3â€“4 (prva dva napredujeta) â†’ finalna skupina 4â€“64 z regulirano postavljenimi prostimi mesti; pod 8 (ÄŤlani: pod 10) prijavami pa ena round-robin skupina.

Prioritetni seznam (severity iz analiz):

| # | Funkcionalnost | Severity | KljuÄŤne zahteve |
|---|---|---|---|
| 1 | **Dogodek/kategorija (veÄŤdogodkovni turnir)** | BLOCKER | Razcep `Turnir` â†’ `Turnir` (prizoriĹˇÄŤe, datumi, organizator) + `Dogodek` (disciplina, spol, starostna kategorija, tip, status). `Igralec.spol` NOT NULL â€” danes ga sploh ni. Starost po preseÄŤnem datumu 31. 12. (PST ÄŤl. 10); igralec lahko nastopi v veÄŤ kategorijah isti dan. **To je najveÄŤji refaktoring sheme â€” naredi ga PRVEGA, vse ostalo visi na Dogodku.** |
| 2 | **Prosta mesta (byes)** | BLOCKER | Ne generiÄŤni algoritem: **Priloga A2/A3 PST sta lookup tabeli** (Ĺˇt. prijav â†’ Ĺˇt. skupin, sestava 3/4, velikost finalne skupine, TOÄŚNE pozicije prostih mest, npr. 33â€“36 prijav â†’ 24-mreĹľa, prosta mesta 5,20,8,17,2,23). Implementiraj dobesedno. Celotno besedilo PST je izvleÄŤeno v `C:/Users/Miha/AppData/Local/Temp/claude/C--Users-Miha-Desktop-Turnirko/51277485-0452-4609-b786-d71b5999d445/scratchpad/pst.txt` (POT v `pot.txt`) â€” **kopiraj oba v repo, preden scratchpad izgine.** Bye tekma: status `BYE`, zmagovalec takoj, ELO preskoÄŤen. |
| 3 | **Nosilci in ITTF Ĺľreb** | BLOCKER | Ĺ t. nosilcev = Ĺˇt. skupin; â‰Ą75 % po jakostni lestvici, do 25 % selektorska diskrecija (roÄŤni override sloti). Postavitev: 1â€“2 fiksno, 3â€“4 Ĺľreb na poziciji 3â€“4, pasovi 5â€“8/9â€“16/17â€“32. `Udelezba.nosilec_st` + persistiran Ĺľreb (avditabilen, ponatisljiv). Uvoz lestvice iz .xlsx za seeding, snapshot ratinga v Udelezbo ob Ĺľrebu. |
| 4 | **Skupine + izloÄŤilni del** | BLOCKER | Tabela `Skupina`, `Tekma.faza` ('SKUPINA','GLAVNI_DEL','TOLAZILNI'), razĹˇirjen `tip` CHECK. Omejitve: soklubovci se v skupini sreÄŤajo v 1. kolu; tekma 2. proti 3. zadnja (fiksne tabele Priloga B); zmagovalec in drugouvrĹˇÄŤeni iste skupine v nasprotni polovici KO; ponovni Ĺľreb finalne skupine na prizoriĹˇÄŤu. |
| 5 | **Vrstni red v skupinah + tie-break** | BLOCKER | Zmage â†’ medsebojne tekme â†’ koliÄŤniki (tekme/nizi/toÄŤke) med izenaÄŤenimi, rekurzivno na podmnoĹľici (PST ÄŤl. 20 / ITTF). Trda odvisnost: **potrebuje toÄŤke po nizih**. IzraÄŤun zamrzni v `mesto` ob zakljuÄŤku skupine. |
| 6 | **ToÄŤke po nizih (per-set scores)** | BLOCKER | Tabela `Set` (tekma, zaporedna_st, tocke_p1, tocke_p2) z validacijo â‰Ą11, razlika â‰Ą2, deuce. `sets_p1/p2` postaneta denormaliziran cache. Konfigurabilno: klubski naÄŤin samo nizi, sankcionirani polni zapisnik. |
| 7 | **Posebni izidi: w.o., predaja, DQ** | BLOCKER (w.o., predaja) / MAJOR (DQ) | `izid_tip` ('IGRANO','BREZ_BOJA','PREDAJA','DISKVALIFIKACIJA'). W.o.: napreduje zmagovalec, 0 toÄŤk v skupini, brez ELO. Predaja: delni rezultat ostane, 1 toÄŤka v skupini. PST ÄŤl. 20: kdor preda, ne sme nadaljevati turnirja (razen zdravniĹˇko potrjene poĹˇkodbe) â€” vgradi kot pravilo s sodnikovim overridom. DQ: `Udelezba.status` + pretvorba preostalih tekem v w.o. |
| 8 | **Best-of po kolu, ne po turnirju** | BLOCKER za sankcionirane | PST ÄŤl. 15/16: DP skupine bo5, od ÄŤetrtfinala bo7; ÄŤlani cel finalni del bo7; U11/U13 vedno bo5; dvojice bo5... `stevilo_setov` premakni na `Tekma` (privzeto iz Dogodka + override po kolu). Trenutni CHECK "fiksno na turnir" ne zadoĹˇÄŤa. |
| 9 | **NTZS licenca in identiteta igralca** | BLOCKER | `Igralec.ntzs_licenca` UNIQUE nullable; merge-players operacija; klub ob prijavi snapshotan v `Udelezba` (zgodovina prestopov); uvoz/sync registra NTZS. UNIQUE(ime,priimek,datum_rojstva) nacionalno ne zdrĹľi. |
| 10 | **Uradni rating engine (jakostne toÄŤke), ELO degradiran v klubsko igraÄŤko** | BLOCKER za NTZS pitch | Uradna lestvica NI ELO: fiksne uvrstitvene tabele po tipu (DP 2000/1600/1300/..., OT 1300/1000/..., lige 75/25/10/5 na dobljeno tekmo, PST ÄŤl. 30), sezonski seĹˇtevek minus najslabĹˇi rezultat (razen U11/U21), carryover okoli DP, nadomestne toÄŤke za reprezentanÄŤne odsotnosti, brez toÄŤk za w.o., deljena mesta, zlitje z ITTF lestvico ob koncu sezone. `RatingEngine` vmesnik: `ClubElo` (obstojeÄŤi) + `NtzsTocke` + kasneje opcijsko **Ratings Central** integracija (brezplaÄŤna, mednarodno kredibilna â€” pametnejĹˇa pot kot lastni ELO kot "standard"). Rating po (igralec, sistem, kategorija), ne en int. |
| 11 | **Rating zgodovina + popravek rezultata** | MAJOR | `RatingZgodovina` append-only ledger; popravek FINISHED tekme kot prvorazredna operacija (rollback ledgerja, retrakcija napredovanega igralca, zavrni ÄŤe so downstream tekme Ĺľe igrane). Danes napaÄŤen ponovni vnos trajno pokvari rating in mreĹľo. |
| 12 | **Tekma za 3. mesto, razigravanje, tolaĹľilni** | MAJOR | Per-dogodek opcije; eksplicitni feedi `izvor_tekma_p1/p2` + vloga WINNER/LOSER (aritmetika (p+1)/2 ne zna izraziti feeda poraĹľencev); `Udelezba.koncno_mesto` (od njega so odvisne jakostne toÄŤke). |
| 13 | **Mize + klicanje tekem** | MAJOR | `Miza`, `Tekma.ID_miza`, lifecycle PENDINGâ†’CALLEDâ†’IN_PROGRESSâ†’FINISHED, "naslednja prosta miza" queue â€” killer feature za operaterja. |
| 14 | **Urnik** | MAJOR | `predviden_cas` / dejanski zaÄŤetek-konec na tekmi; POT: zaÄŤetek ob 10:00, objavljen razpored, w.o. po 15 min. |
| 15 | **Prijave, roki, odjave, prijavnine** | MAJOR | Rok (petek 10:00 po PST ÄŤl. 31), `Udelezba.status`, rezerva, plaÄŤano/kdaj, odjava pred Ĺľrebom = izpad, po Ĺľrebu = w.o.; evidenca za **7 % dajatev NTZS** (POT ÄŤl. 21a). |
| 16 | **Dvojice** | MAJOR | `Par` + posploĹˇen udeleĹľenec tekme; Ĺľreb na prizoriĹˇÄŤu, ÄŤisti KO bo5 (PST ÄŤl. 15/19); brez singl ELO. |
| 17 | **Ekipna tekmovanja (SNTL, Corbillon)** | MAJOR, faza 2+ | `Ekipa`/`Srecanje` z otroĹˇkimi tekmami in postavitvami A/B/X/Y; U13/U15 Corbillon, U17/U19 modificiran brez dvojic (PST ÄŤl. 14). NaÄŤrtuj FK na Tekmi zdaj (Dogodek XOR Srecanje), gradi kasneje. |
| 18 | **Sodniki/delegat** | MINOR (a formalna zahteva) | Vsaj `Turnir.ID_vrhovni_sodnik` + polje za delegata (POT ÄŤl. 13â€“16 ju zahtevata); per-tekma sodnik opcijski. |
| 19 | **Tujci/gosti, provizoriÄŤni rating, klubska separacija** | MINOR | `drzavljanstvo`, `Udelezba.gost`; sprosti CHECK poĹˇtnih Ĺˇtevilk 1000â€“9265; viĹˇji K za prvih ~15 tekem; per-turnir stikalo za klubsko separacijo (klubski veÄŤeri jo namenoma izklopijo). |
| 20 | **Uradni izvozi** | MAJOR za pitch | Elektronski rezultati za NTZS do konca turnirja (POT ÄŤl. 14), poroÄŤilo delegata v 7 dneh (POT ÄŤl. 24), xlsx/pdf v formatu objav NTZS; razpis/propozicije. |

---

## 5. TehniÄŤni dolg in prenova

Ocena iz analize backend-a drĹľi: **~20 % (domensko jedro) je vredno obdrĹľati, ~80 % operativne lupine je treba zamenjati.** Vrstni red je pomemben â€” najprej zavaruj obnaĹˇanje s testi, Ĺˇele nato premikaj.

### 5.1 Korektnostni hroĹˇÄŤi â€” popravi TAKOJ (pokvarjeno Ĺľe danes, single-user)

1. **`nastaviRezultat` brez state guarda** (`poskus2/src/main/java/com/miha/poskus2/controller/TekmaController.java:48â€“104`): sprejme nov rezultat za FINISHED tekmo, katere zmagovalec je Ĺľe napredoval â†’ v naslednjem kolu pristane napaÄŤen igralec, `elo_posodobljen` pa zagotovi, da se ELO nikoli ne popravi. Uvedi state machine v `TekmaService`: rezultat samo na PENDING/IN_PROGRESS tekmi RUNNING turnirja; popravek FINISHED kot loÄŤena admin operacija z retrakcijo.
2. **Lost-update dirka v `napredujZmagovalca`** (`TekmaController.java:106â€“145`): read-modify-save cele vrstice naslednjega kola; dve sosednji tekmi, ki se konÄŤata soÄŤasno, si prepiĹˇeta slot. Danes maskira SQLite-ov globalni lock, Postgres bo eksplodiral prvi naporen dan. Fix: `@Version` na Tekma/Turnir/Igralec + atomarni `UPDATE ... SET id_p1=:w WHERE id=:id AND id_p1 IS NULL` (ali `@Lock(PESSIMISTIC_WRITE)`). Isto za ELO update (read-modify-write dveh Igralec vrstic).
3. **`BracketController` ne preklopi turnirja v RUNNING** in ne zavrne generiranja za FINISHED; `TurnirController.java:71â€“86` pusti klientu poslati poljuben status ob kreiranju. Status doloÄŤa streĹľnik, v isti transakciji.
4. **`UdelezbaController.prijavi` (36â€“54) ni transakcijska**: slab ID sredi seznama pusti delne prijave; duplikat = surova 500. â†’ `@Transactional` na servisu, duplikat = 409, prijava zavrnjena na RUNNING/FINISHED.

### 5.2 Varnostni blockerji (pred kakrĹˇnimkoli hostanjem)

1. **`POST /shutdown` = nezaĹˇÄŤiten `System.exit(0)`** (`ShutdownController.java:17â€“27`). En anonimen curl ubije "nacionalni sistem". Skupaj z `AutoOpenBrowser.java` in `headless=false` premakni za Maven/Spring profil `desktop`; v server buildu ju NI. Frontend zrcalo: beacon v `App.tsx:24â€“35` za `import.meta.env.VITE_DESKTOP_MODE` (in odstrani logout-on-unload, ki briĹˇe vlogo ob vsakem refreshu).
2. **NiÄŤ avtentikacije**: PIN "1234" v `aplikacija-turnirji/src/auth/AuthContext.tsx:15`, vloga v localStorage, vsi mutirajoÄŤi endpointi odprti v omreĹľje. â†’ Spring Security + JWT + tabela `uporabnik` (glej Â§3.2); frontend: auth-aware wrapper v `api.ts`, 401 â†’ login, `/me` endpoint, vloge iz streĹľnika.
3. **GDPR puĹˇÄŤanje**: `GET /igralci` (`IgralecController.java:47â€“50`) serializira e-poĹˇto, telefon, rojstni datum in naslov vsakemu klicatelju. â†’ DTO-ji takoj: javni `PlayerDto` brez PII, PII samo za pooblaĹˇÄŤene vloge. To je poceni in nujno Ĺˇe pred javnim pilotom.

### 5.3 Vrstni red prenove

1. **Testi okoli obstojeÄŤega jedra (pred vsem ostalim):** JUnit 5 za `EloService` (simetrija, floor, K-margin meje, null rating) in bracket/napredovanje (property-style: za n â {2,4,8,16,32} je mreĹľa popolna in vsak simuliran turnir konÄŤa z natanko enim zmagovalcem). LoÄŤen testni profil â€” danes edini test (`contextLoads`) tepta pravo `./db/Podatki.db`.
2. **Ekstrakcija servisov:** `TurnirService`, `BracketService`, `TekmaService`, `PrijavaService`; controllerji tanki HTTP adapterji; poenoti na Springov `@Transactional` (danes meĹˇano z jakarta v `TekmaController.java:24`). Tu vgradi state machine in lock fixe iz 5.1.
3. **Flyway:** `V1__baseline.sql`, ki reproducira danaĹˇnjo shemo (vkljuÄŤno z vsemi CHECK-i, ki danes obstajajo SAMO v .db datoteki!), `ddl-auto=validate`.
4. **PostgreSQL:** driver + dialect, DATE-kot-TEXT â†’ prave date/timestamptz, REAL prijavnina â†’ numeric(8,2), AUTOINCREMENT â†’ identity. SQLite ostane za desktop edicijo za repository vmesniki.
5. **Spring Security + vloge + `turnir_osebje`** (glej Â§3.2).
6. **API higiena:** DTO-ji povsod, LAZY + fetch-joins (danes EAGER na `Tekma.java:27â€“47` vleÄŤe cele grafe), Bean Validation, `@RestControllerAdvice` s problem+json, Pageable, `/api/v1`, springdoc.
7. **Audit + lifecycle:** JPA auditing (@CreatedBy/@LastModifiedBy, timestamptz), Envers ali audit-tabela za Tekmo in rating; **konec hard-delete igralcev** (`IgralecController.java:133â€“145`) â†’ arhiviranje + GDPR anonimizacija (PII na null, tekmovalna zgodovina ostane).
8. **Frontend prenova (vzporedno s 5â€“7):** TanStack Query (zamenja 19Ă— copy-paste `setErr(String(...))` in roÄŤne `refresh()`); dekompozicija monolitov (`TournamentBracketPage.tsx` 360 vrstic â†’ BracketView/MatchCard/ResultDialog/RegistrationPanel; `GuestDashboard.tsx` 459 vrstic z dvema ~70-vrstiÄŤnima kopijama player-carda â†’ ena komponenta 2Ă— renderirana); Tailwind + shadcn/ui (obstojeÄŤe tokene iz `index.css` prenesi v temo); react-hook-form + zod (popravi tudi drift: DB dovoli nize 3/5/7, UI validira in ponuja samo 3/5 â€” `TournamentsPage.tsx:56,161â€“165`); tipiziran `ApiError` + sonner toasti + error boundary; react-i18next (sl) + prevod backend enumov (konec "WAITING/FINISHED/setsP1" v UI); Intl.DateTimeFormat("sl-SI").
9. **Bracket UI:** lasten SVG/CSS-grid BracketView s povezovalnimi ÄŤrtami (~200 vrstic; @g-loot/react-tournament-brackets samo kot dizajn referenca â€” React-17 era, nevzdrĹľevan). Slovenska imena kol (Osmina finala/ÄŚetrtfinale/Polfinale/Finale), "Ime Priimek (Klub)", brez ID-jev ("(ID 12)", "Zmagovalec ID: 5" â€” `TournamentBracketPage.tsx:295,159`), mobilni layout.
10. **CI + e2e:** GitHub Actions (mvn verify + frontend build + Docker image); Vitest/RTL; en Playwright happy-path (ustvari turnir â†’ prijavi â†’ Ĺľrebaj â†’ vnesi rezultate â†’ zmagovalec); Testcontainers-Postgres integracijski test "dve niti konÄŤata sosednji tekmi" kot dokaz lock fixa; openapi-typescript, da frontend tipi ne driftajo.
11. **Drobnarije:** izbriĹˇi `console.log` (`TournamentsPage.tsx:38`), mrtve datoteke (App.css, react.svg), CORS iz hardcoded `http://localhost:5173` (`CorsConfig.java:17`) v property.

---

## 6. Faze razvoja

Predpostavka: **~10 ur/teden** ob Ĺˇtudiju. Ocene so realistiÄŤne, ne optimistiÄŤne.

### Faza 0 â€” Temelji (meseci 1â€“2, ~80 h)

- **Cilji:** obstojeÄŤe jedro zavarovano, korektnostni hroĹˇÄŤiÄŤudno popravljeni, infrastruktura reproducibilna.
- **Deliverables:** git + CI; testni profil; unit testi ELO + bracket; servisna ekstrakcija; state machine za rezultate + retrakcija; `@Version` + atomarno napredovanje; Flyway baseline; DTO-ji (PII skrit); desktop profil (/shutdown, AutoOpenBrowser, beacon izolirani); PST/POT tekst v repo.
- **Izstopni kriterij:** `mvn verify` zelen na ÄŤisti bazi iz migracij; ponovni vnos rezultata FINISHED tekme je zavrnjen oz. pravilno retrahiran; GET /igralci ne vraÄŤa PII.

### Faza 1 â€” Pravi klubski turnir end-to-end (meseci 3â€“6, ~160 h)

- **Cilji:** Turnirko izpelje pravi turnir po PST formatu na klubski ravni.
- **Deliverables:** refaktoring `Dogodek` (+ `Igralec.spol`); Priloga A2/A3 lookup tabele (byes) + testi proti tabelam; nosilci + ITTF Ĺľreb + uvoz lestvice .xlsx; skupine + Priloga B razporedi + tie-break kaskada; tabela `Set` (toÄŤke po nizih); `izid_tip` (w.o./predaja/DQ); best-of po kolu; `RatingZgodovina`; mize + klicanje; tiskanje prek `@media print` (Ĺľrebna lista, zapisnik, rezultati); prenovljen BracketView.
- **Izstopni kriterij:** **dva prava turnirja NTK Savinja** (npr. klubski + memorial) izpeljana v celoti v Turnirku, z Ĺľrebom po pravilniku, w.o. primerom in natisnjeno Ĺľrebno listo na steni.

### Faza 2 â€” Cloud multi-tenant + javni rezultati v Ĺľivo (meseci 7â€“10, ~160 h)

- **Cilji:** hostana veÄŤuporabniĹˇka storitev z javnim ogledom.
- **Deliverables:** Postgres migracija; Spring Security + vloge + `turnir_osebje`; VPS + Docker Compose + Caddy + backupi + monitoring; javni `/t/:id/zivo` in `/t/:id/projekcija` (brez prijave, polling); sodniĹˇka konzola `/miza/:id` (tablet, IndexedDB queue, idempotentni endpointi); direktorska konzola (prijave z rokom, predogled Ĺľreba pred objavo, popravki, w.o.); responzivnost + a11y osnove; SSE ko polling ne zadoĹˇÄŤa.
- **Izstopni kriterij:** turnir teÄŤe na VPS; gledalec na telefonu brez prijave spremlja mreĹľo v Ĺľivo; dva operaterja hkrati vnaĹˇata rezultate brez izgubljenih posodobitev (Testcontainers test + praksa); restore backupa preizkuĹˇen.

### Faza 3 â€” Pilot na pravem odprtem/rekreativnem turnirju (meseci 11â€“14, ~100 h)

- **Cilji:** validacija zunaj lastnega kluba; referenÄŤni uporabniki.
- **Deliverables:** 2â€“3 zunanji klubi/organizatorji; **rekreativno-veteranski modul**: sezonska lestvica "najboljĹˇih 6 od 7", uporaba kumulative za seeding skupin naslednjega turnirja (per pravilnik â€” podrobnosti **[VERJETNO]**, preveri pri izvirnem PDF-ju!); uradni izvozi (elektronski rezultati, xlsx/pdf v NTZS formatu); dvojice, ÄŤe jih pilot potrebuje; GDPR paket (glej Â§7).
- **Izstopni kriterij:** vsaj en turnir, ki ga NE organizira NTK Savinja, izpeljan v celoti; organizator, pripravljen dati izjavo/referenco; veteranska lestvica se za en dejanski krog ujema z roÄŤno objavljeno.

### Faza 4 â€” NTZS pitch + integracija jakostne lestvice (meseci 15â€“20)

- **Cilji:** formalen predlog NTZS kot komplement Stupi.
- **Deliverables:** implementacija uradnega toÄŤkovanja PST ÄŤl. 30 (`NtzsTocke` engine) z reprodukcijo ene pretekle objavljene lestvice kot dokazom; demo paket (Ĺľiv pilot + raÄŤun za IO NTZS); ponudba: (a) avtomatizacija rekreativno-veteranske lestvice, (b) organizatorsko orodje za turnirski dan z uvozom lestvice in izvozom v NTZS format, (c) servisna pogodba 3â€“8 kâ‚¬/leto; osnutek pogodbe o obdelavi (ÄŤl. 28) Ĺľe priloĹľen.
- **Izstopni kriterij:** sestanek z IO/strokovno sluĹľbo NTZS opravljen; jasen da/ne/pogoji. **ÄŚe ne: freemium klubska pot (pot 2) je samostojna in preĹľivi brez NTZS.**

Ekipna tekmovanja (SNTL/Corbillon) so zavestno Ĺˇele post-faza-4 modul.

---

## 7. Pravno in GDPR

Dobra novica: pot je zaÄŤrtana in je lahko **prodajna prednost** pred volontersko zgrajenimi sistemi. Slaba: trenutna arhitektura (odprt API, PII vsem, hard delete, PIN 1234) centraliziranih podatkov mladoletnikov **ne sme** drĹľati niti en dan.

- **Objava rezultatov:** ime + priimek + klub na zakoniti podlagi zakonitega interesa â€” mnenje IP-RS Ĺˇt. 0712-1/2018/1213 (17. 5. 2018) **[POTRJENO]**; rezultati so "bistvo Ĺˇportnih dogodkov". **Letnica rojstva zahteva privolitev** in udeleĹľenca ni dovoljeno izkljuÄŤiti, ker je ne da. Mnenje je pred-ZVOP-2 â€” logika sledi GDPR 6(1)(f), a ob NTZS pitchu preveri aktualna mnenja IP-RS.
- **Mladoletniki:** ZVOP-2 (od 26. 1. 2023) postavlja starost veljavne otrokove privolitve za storitve informacijske druĹľbe na **15 let**; pod 15 privolitev/odobritev starĹˇa ali skrbnika. Za udeleĹľbo in objavo podatkov mladoletnikov soglasje starĹˇev. â†’ Vgradi zajem starĹˇevskega soglasja v registracijo (flag + datum + kdo), pred Fazo 3.
- **Vloge po GDPR:** zveza/klub = upravljavec, ti = obdelovalec â†’ **pisna pogodba o obdelavi po ÄŤl. 28 GDPR** (varnost, obveĹˇÄŤanje o krĹˇitvah, podobdelovalci). Pripravi predlogo v Fazi 2, ne Ĺˇele ob pitchu.
- **Vgradi v produkt:** (1) hramba â€” izbris kontaktnih podatkov ob koncu ÄŤlanstva, tekmovalna zgodovina ostane anonimizirana/psevdonimizirana; (2) anonimizacija namesto brisanja (Ĺľe v Â§5.3/7); (3) audit trail (kdo je kaj spremenil) â€” hkrati prva podporna zahteva; (4) EU hosting; (5) nikoli rojstni datumi v javnih pogledih ali URL parametrih; (6) TLS povsod, hashirana gesla.
- **Kdaj:** PII-DTO-ji in konec hard-delete = Faza 0. Soglasja, hramba, DPA predloga = pred prvim zunanjim pilotom (Faza 2/3). Ne odlaĹˇaj na "ko bo NTZS vpraĹˇal" â€” tvegani zvezin odbor bo to vpraĹˇal prvi.

---

## 8. Tveganja

| # | Tveganje | Verjetnost | Udar | Mitigacija |
|---|---|---|---|---|
| 1 | **Incumbent lock-in**: NTZS je pogodbeno na Stupi (ITTF partner); pitch "zamenjava" propade | zelo visoka | visok | Strategija komplementa: veteranska lestvica, turnirski dan, interop (uvoz xlsx / izvoz NTZS format). Klubska/freemium pot mora preĹľiveti tudi brez NTZS. |
| 2 | **Solo bus-factor**: en ÄŤlovek, Ĺˇtudij, motivacija | visoka | visok | CI, Flyway, testi, OpenAPI = projekt je predajljiv; razmisli o odprtokodnem jedru (gradi ugled + zniĹľa "kaj ÄŤe Miha izgine" ugovor zveze); majhne faze z vidnimi izidi proti izgorelosti. |
| 3 | **Scope creep**: pravilnik je ogromen (dvojice, ekipno, Corbillon, Masters, TOP...) | zelo visoka | srednji | Trdi rez: Faza 1 pokrije samo posamiÄŤni format skupine+KO; ekipno Ĺˇele po Fazi 4; offline-sync engine se NE gradi (IndexedDB queue je 80 % reĹˇitev). |
| 4 | **Federacijska politika**: odloÄŤitve po odnosih, dolgi cikli, en zaposlen | visoka | srednji | ReferenÄŤni piloti pred pitchem; vstop prek konkretne boleÄŤe toÄŤke (roÄŤna veteranska lestvica), ne prek vizije; poiĹˇÄŤi zaveznika v IO/strokovni sluĹľbi zgodaj (Faza 1, ne Faza 4). |
| 5 | **NapaÄŤen rating fokus**: leta piljenja lastnega ELO brez uradne veljave | srednja | srednji | ELO = klubska zabava. Uradne toÄŤke PST ÄŤl. 30 = kar Ĺˇteje. Ratings Central = mednarodna kredibilnost zastonj. |
| 6 | **Pravna napaka z mladoletniki** pri javnem pilotu | srednja | visok | Â§7 pred Fazo 3; brez rojstnih letnic javno; DPA predloga vnaprej. |
| 7 | **Boot 4 ekosistemske luknje** (Security/springdoc/Testcontainers) | nizkaâ€“srednja | nizek | Preveri kompatibilnost v Fazi 0, dokler je fallback na 3.5.x Ĺˇe poceni. |
| 8 | **Konkurenca od spodaj**: Challonge/generiÄŤni SaaS za klubske veÄŤere | srednja | nizek | Diferenciacija = slovenska pravila (Priloge A/B/C hardkodirane), slovenski jezik, offline dvorana, tiskani zapisniki â€” tega generiki nimajo. |
| 9 | **NapaÄŤna priÄŤakovanja o denarju** | â€” | osebni | Sprejmi strop 5â€“20 kâ‚¬/leto. NajveÄŤja dejanska vrednost projekta je portfelj + reference + znanje. ÄŚe to ni sprejemljivo, spremeni cilj zdaj, ne ÄŤez dve leti. |

---

## 9. Prvih 90 dni

Predpostavka ~10 h/teden. "T" = teden.

- **T1 â€” ReĹˇi podatke in odloÄŤi strategijo.** Kopiraj `pst.txt` in `pot.txt` iz scratchpada v repo (`docs/pravilniki/`) â€” scratchpad je zaÄŤasen. Prenesi izvirna PDF-ja PST v4.6 in POT v2.8 ter pravilnik za rekreativce z ntzs.si. Git repo (ÄŤe Ĺˇe ni), README z odloÄŤitvijo: **strategija komplementa, ne zamenjave**. ZapiĹˇi si tudi obe vrzeli iz konteksta (dogovorjeno: konec cilja "licenca kot lump-sum").
- **T2 â€” Varnostna mreĹľa.** Testni Spring profil (in-memory/loÄŤena SQLite), JUnit 5 testi za `EloService`; property-style testi bracket generacije in napredovanja (n=2..32, en zmagovalec). GitHub Actions z `mvn verify`.
- **T3 â€” Popravi korektnostna hroĹˇÄŤa.** State machine za `nastaviRezultat` (zavrni ponovni vnos na FINISHED; admin popravek z retrakcijo), status RUNNING ob generiranju mreĹľe, streĹľniĹˇko doloÄŤen status ob kreiranju turnirja. Testi za vse tri.
- **T4 â€” SoÄŤasnost.** `@Version` na Tekma/Turnir/Igralec; atomaren UPDATE za slot napredovanja; transakcijska prijava z 409 na duplikat. Test z dvema nitma.
- **T5 â€” Desktop izolacija + PII.** Maven/Spring profil `desktop` (ShutdownController, AutoOpenBrowser, headless); `VITE_DESKTOP_MODE` za beacon v `App.tsx`; DTO-ji za igralce (javno brez e-poĹˇte/telefona/naslova/rojstva).
- **T6 â€” Flyway.** `V1__baseline.sql` z VSEMI CHECK-i iz Podatki.db; `ddl-auto=validate`; ÄŤista baza iz migracij v CI.
- **T7â€“8 â€” Refaktoring `Dogodek` + spol.** NajveÄŤji shema rez: Turnir/Dogodek razcep, `Igralec.spol`, preusmeritev Udelezba/Tekma na Dogodek, migracija obstojeÄŤih podatkov. Frontend prilagoditev na minimum (en dogodek na turnir kot privzetek).
- **T9â€“10 â€” Priloga A: prosta mesta.** Lookup tabele A2/A3 kot podatki (ne koda), generator mreĹľe z byes (status BYE, brez ELO, takojĹˇnje napredovanje), testi dobesedno proti tabelam iz pravilnika (npr. 33â€“36 â†’ 24-mreĹľa, mesta 5,20,8,17,2,23).
- **T11â€“12 â€” Nosilci + Ĺľreb.** `Udelezba.nosilec_st`, ITTF postavitev (1â€“2 fiksno, 3â€“4, pasovi), klubska separacija (toggle), uvoz .xlsx lestvice za seeding, persistiran/avditabilen Ĺľreb.
- **T13 â€” Frontend temelj.** TanStack Query v projekt; migriraj TournamentBracketPage in TournamentsPage; zbriĹˇi roÄŤne refresh() in 19 catch kopij na teh straneh; sonner za napake.
- **Sproti (nizka intenzivnost):** pokliÄŤi/piĹˇi 2â€“3 organizatorjem rekreativno-veteranskih turnirjev â€” vpraĹˇaj, kako danes raÄŤunajo lestvico "6 od 7" in kaj jih najbolj boli. To je trĹľna raziskava za Fazo 3/4 in iskanje zaveznica v zvezi; zaÄŤni zdaj, ker traja.
- **Mejnik ob dnevu 90:** iz ÄŤiste baze prek migracij lahko ustvariĹˇ turnir z npr. 13 prijavljenimi, sistem sam doloÄŤi skupinsko/KO strukturo po Prilogi A (ali vsaj KO z byes), Ĺľreb z nosilci iz uvoĹľene lestvice, in noben znani korektnostni hroĹˇÄŤ (ponovni vnos, race) ne obstaja veÄŤ. Skupine + toÄŤke po nizih so naslednji kos (Faza 1, meseci 4â€“6).

---

*Zadnja iskrena misel: Turnirko danes je soliden klubski projekt in odliÄŤen uÄŤni poligon. Njegova pot do "nacionalnega standarda" ne pelje mimo Stupe, ampak okoli nje â€” skozi telovadnice, veteranske turnirje in Excel tabele, ki jih nihÄŤe ne mara raÄŤunati roÄŤno. Tam zmaga najboljĹˇe lokalno orodje, ne najveÄŤja platforma.*