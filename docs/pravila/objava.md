# Pravila: objava

> Objava na splet: Docker, Umami, profil splet, predogled povezave, ikone, baza in vloge, HSTS, skrivnosti.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

## Objava na splet

- Navodilo po korakih je `docs/OBJAVA.md`; datoteke postavitve so v korenu
  (`Dockerfile`, `compose.yaml`, `Caddyfile`, `.env.primer`) in skripte v
  `skripte/` (kopije `varnostna-kopija.sh`, obnova `obnovi-bazo.sh`, mesečna
  vaja `vaja-obnove.sh`, pregled `baza-stanje.sh`, vloge baze `baza/00-vloge.sh`,
  selitev s SQLite `preklop/`, `docs/PREKLOP.md`).
- **Statistika obiska je Umami na istem strežniku** (storitvi `umami` in
  `umami-baza` v `compose.yaml`, poddomena `statistika.turnirko-nt.si` v
  `Caddyfile`, namestitev `skripte/vklopi-statistiko.sh`, OBJAVA.md razdelek 8).
  Brez piškotkov, zato brez pasice za soglasje — orodja, ki potrebuje
  soglasje (Google Analytics), ne dodajaj. Skript `obisk.js` v `index.html` ima
  `data-domains`, zato razvoj in namizna različica ne štejeta; naložen je z
  `defer`, da vmesnik brez interneta v dvorani ne čaka nanj.
- **Mavnov profil `splet`** (`mvnw -Psplet package`) zgradi vmesnik in ga
  vloži v isti `.jar`; `SpletniVmesnik` ga postreže in vsako pot, ki ni
  datoteka in ne začne z `api/`, vrne kot `index.html` (sicer osvežitev na
  `/turnirji/1` vrne 404). Privzeti prevod ostane brez vmesnika, da je hiter.
- **Naslov in predogled povezave sestavi strežnik** (okt 2026, paket
  `splet`): `StraniKontroler` za `/lige/{id}` (z `?ekipa=` je naslov
  ekipa), `/turnirji/{id}`, `/dogodki/{id}`, `/srecanja/{id}`,
  `/igralci/{id}/profil` in stalne strani (`/lige`, `/lestvica` …) vrne
  `index.html`, v katerem `PredogledStrani` zamenja blok med
  `<!--predogled-->` in `<!--/predogled-->` (title, description, canonical,
  og:title/description/url). Prej je imela vsaka stran isti naslov in
  povezava na ligo v WhatsAppu je bila videti kot domača stran — WhatsApp,
  Facebook in Viber JavaScripta ne izvajajo. Pravila, ki jih ne razbij:
  - **Oznak v `index.html` ne briši**; brez njih ostane HTML nespremenjen
    (stran dela, predogled je splošen). Neznan ali neveljaven id da splošen
    predogled, ne 404/400 — stran nato sama pove, da zapisa ni.
  - Besedila (`PredogledStoritev`) so samo javni podatki, ki jih stran kaže
    gostu; imena so ubežana (vpisujejo jih organizatorji).
  - **Naslov zavihka vzdržuje vmesnik** (`useNaslovStrani`, zapis
    »<naslov> – Turnirko« = `PredogledStrani.PRIPONA`); stran, ki jo odpre
    WhatsApp, in zavihek se imenujeta enako.
  - og:url in kanonični naslov gradi `turnirko.javni-naslov`
    (`TURNIRKO_JAVNI_NASLOV`, privzeto https://turnirko-nt.si); slika je
    `predogled.png` (1200 × 630), razen pri ligi, turnirju, kategoriji in
    srečanju lige, ki imajo logotip (V54): takrat je og:image njegova različica
    DELJENJE (`Predogled.logotip`). Zato so oznake og:image v bloku med
    `<!--predogled-->` in `<!--/predogled-->`.
- **Ikone za bližnjico na začetnem zaslonu** (`apple-touch-icon.png`,
  `ikona-192/512.png`, `manifest.json` z `display: minimal-ui`,
  `theme-color` = papir) in `predogled.png` so izrisani iz
  `skripte/ikone/*.html` z `bash skripte/ikone/naredi.sh` (brezglavi Chrome).
  Znak v ikoni zaseda srednjih 60 % (iOS zaobli vogale, Android obreže za
  maskable). `minimal-ui` in ne `standalone`: na iOS bi bližnjica brez
  brskalnikove vrstice ostala brez poti nazaj.
- Profil `splet` (`application-splet.properties`) je za strežnik za Caddyjem:
  prazen CORS (isti izvor), `forward-headers-strategy`, brez sledi sklada v
  odgovoru. Vrata 8080 se na strežniku ne objavijo — do aplikacije se pride
  samo skozi Caddy, torej samo prek HTTPS (HTTP Basic bi bil sicer berljiv).
- **Baze PostgreSQL (zvezka `turnirko_baza`) nikoli ne kopiraj kot mapo**; kopija je
  samo `pg_dump -Fc` (`skripte/varnostna-kopija.sh`: urna, nočna, mesečna, odnašanje
  z `rclone` v šifrirano shrambo v EU). Kopija, ki je nihče ni obnovil, ni kopija:
  `skripte/vaja-obnove.sh` jo vsak mesec obnovi v začasen vsebnik in primerja
  števila vrstic. Obnova produkcije je `skripte/obnovi-bazo.sh`.
- **Baza ima tri vloge** (`skripte/baza/00-vloge.sh`, ob prvem zagonu praznega
  zvezka): `postgres` (samo vzdrževanje), `turnirko_lastnik` (Flyway ter ukazi
  `uvoz`/`popravek`/`prenos`) in `turnirko_app` (aplikacija: samo SELECT/INSERT/
  UPDATE/DELETE; `statement_timeout` 30 s, `lock_timeout` 5 s,
  `idle_in_transaction_session_timeout` 60 s). Aplikacija v `splet` profilu se
  prijavi z `turnirko_app`, Flyway z `SPRING_FLYWAY_USER` (compose.yaml). Novo
  tabelo ali zaporedje Flyway ustvari kot `turnirko_lastnik`; privzete pravice
  (`ALTER DEFAULT PRIVILEGES`) jih dajo aplikaciji. Ukaz, ki bi moral spremeniti
  shemo ali zbrisati tabelo, z vlogo aplikacije ne bo deloval — to je namen.
- **HSTS postavlja Caddy, ne aplikacija.** Spring Security privzeto pošilja
  `Strict-Transport-Security` z `includeSubDomains`, takoj ko zahteva pride po
  HTTPS. Poddomene niso na tem strežniku (`webmail.` in `mail.` sta pri
  gostitelju domene in imata certifikat za *njegovo* ime, poddomene za povezave
  v pošti pa pri ponudniku transakcijske pošte), zato bi to brskalniku za leto
  dni vsililo HTTPS tam, kjer certifikat ne ustreza — opozorila pod HSTS pa ni
  mogoče obiti in webmail postane nedosegljiv. Zato je v
  `VarnostneNastavitve` HSTS izklopljen, glavo pa doda `Caddyfile`, namenoma
  brez `includeSubDomains`. (Izmerjeno 22. 9. 2026 na turnirko-nt.si.)
- **Lokalne skrivnosti gredo v `zaledje/config/application-<profil>.properties`,
  nikoli v `zaledje/config/application.properties`.** Mapo `./config` Spring
  Boot bere ob vsakem zagonu in ima prednost pred `application-test.properties`
  iz razreda, zato brezprofilna datoteka nastavitve podtakne tudi testom —
  `turnirko.posta.nacin=smtp` je tako podrl 20 testov, ker
  `PomnilniskiPosiljatelj` ni bil ustvarjen. Profilna datoteka se brez
  `-Dspring-boot.run.profiles=<profil>` sploh ne prebere.

