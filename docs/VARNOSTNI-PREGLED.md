# Varnostni pregled Turnirka — 3. 10. 2026

Pregled kode (zaledje, vmesnik, postavitev) in pasivna preverba žive strani
turnirko-nt.si. Proti produkciji je šlo le nekaj neškodljivih zahtev GET
(glave odgovora, poskus branja datotek, en prenos `/api/v1/igralci`); nobenega
napada, ugibanja gesel ali kod. Koda ni spremenjena — ta dokument je načrt.

Stanje produkcijske baze (kopija z 2. 10.): **1777 igralcev, vsi z datumom
rojstva** (od tega ~230 v pasovih U11–U15, torej mladoletni), **1648 z licenco
NTZS**, brez e-pošt, telefonov in naslovov; 5 računov. Najobčutljivejši podatek
v bazi je torej **datum rojstva**, nato licenca.

## Povzetek po prednosti

| # | Ugotovitev | Resnost | Napor popravka |
|---|---|---|---|
| V1 | Vse omejitve po IP se obidejo z glavo `Forwarded` | **visoka** | 15 min |
| V2 | Javni predogled zapisa je orakelj za datum rojstva (tudi otrok) | **visoka** | pol dneva |
| V3 | Samodejna povezava = prevzem identitete igralca z javnimi podatki | **visoka** | 1 dan |
| V4 | Celotno bazo je mogoče kopirati v nekaj minutah (kot ti iz Stupe) | srednja | več dni |
| V5 | Drage javne poti brez meja → zrušitev strežnika z enim računalnikom | srednja | pol dneva |
| V6 | Geslo živi v brskalniku (sessionStorage), brez CSP | srednja | 1–2 dni |
| V7 | Plačljiva registracija: razkrije obstoj e-pošte, hash gesla v Stripu | srednja | 1 dan |
| V8 | Pošiljanje kod kot orodje za spam/bombing (tudi prek naslova skrbnika) | srednja | pol dneva |
| V9 | Preverjanje kod: tekma pri štetju poskusov, brez meje po IP | srednja | 2 uri |
| V10 | Admin: znano ime `admin`, brez 2FA, zaklenljiv od zunaj | srednja | 1 dan |
| V11 | Namizna različica: admin/admin dosegljiv vsem na Wi-Fi dvorane | srednja | 1 ura |
| V12 | Manjkajoče varnostne glave (CSP, Permissions-Policy …) | nizka | 30 min |
| V13 | Pravila gesel: brez seznama šibkih, >72 bajtov = napaka 500 | nizka | 1 ura |
| V14 | Stripe webhook in nadgradnja: manjkajoče preverbe | nizka | 1 ura |
| V15 | Kvalifikacije: lastništvo nižje lige ni preverjeno | nizka | 15 min |
| V16 | Strežnik in vsebniki: utrditev, kopije, Umami | srednja | pol dneva |
| V17 | Kopije produkcijske baze na namizju | srednja | 10 min |
| V18 | GDPR: ni politike zasebnosti, »soglasje« s pogoji, ki ne obstajajo | srednja | pravno |

Priporočen vrstni red je na koncu (razdelek »Načrt izvedbe«).

---

## V1 — Obvod vseh omejitev po IP z glavo `Forwarded` (visoka)

**Kaj.** `application-splet.properties` ima `server.forward-headers-strategy=framework`.
To vklopi Springov `ForwardedHeaderFilter`, ki `request.getRemoteAddr()` nadomesti
z vrednostjo iz glave **`Forwarded: for=…`** (preverjeno v izvorni kodi
spring-web 7.0.3, `ForwardedHeaderUtils.parseForwardedFor`: najprej `Forwarded`,
šele nato prvi zapis v `X-Forwarded-For`). Caddy po dokumentaciji glave
X-Forwarded-* od odjemalca zavrže in postavi sam, **vse ostale glave pa prepusti
nespremenjene — tudi `Forwarded`**. Napadalec torej v vsako zahtevo vpiše
izmišljen naslov in je za strežnik vsakič nov obiskovalec.

**Kaj vse pade:** vse meje, ki temeljijo na IP —
- prijava: 50 neuspehov na IP, 10 na račun z istega IP (`OmejitevPrijavFilter.java:52`,
  `PrijavaDogodki.java:47`); ostane samo 100/15 min na račun → ~9600 ugibanj
  gesla na dan na račun, neomejeno »prskanje« pogostih gesel po vseh računih;
- **zaklep admina od zunaj**: 100 napačnih prijav na `admin` z različnimi
  lažnimi IP → admin se 15 minut ne more prijaviti, ponavljaj v nedogled
  (admin nima »pozabljenega gesla«);
- pošiljanje kod 20/h na IP (`RegistracijaStoritev.java:74`) → spam (V8);
- predogled zapisa 30/h na IP (`RegistracijaStoritev.java:80`) → V2 postane takojšen;
- spomin omejevalnika raste brez meje (vsak lažen IP in ime je nov ključ, hrani
  se 24 h — `OmejevalnikPoskusov.java:32`), kar ob dolgem napadu porabi pomnilnik.

**Popravek (oba koraka, drug drugega varujeta):**

1. `Caddyfile`, v bloku `turnirko-nt.si`:
   ```caddy
   reverse_proxy app:8080 {
       # odjemalec ne sme sam povedati, kdo je - to ve samo Caddy
       header_up -Forwarded
       header_up -X-Real-IP
   }
   ```
2. `application-splet.properties` — namesto `framework` Tomcatov ventil, ki
   glavi verjame **samo**, ce zahteva pride od posrednika v Dockerjevem omrežju,
   in bere samo `X-Forwarded-For` (ki ga Caddy vedno prepiše):
   ```properties
   server.forward-headers-strategy=native
   server.tomcat.remoteip.internal-proxies=172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}
   server.tomcat.remoteip.remote-ip-header=X-Forwarded-For
   server.tomcat.remoteip.protocol-header=X-Forwarded-Proto
   ```
   (Absolutnih naslovov aplikacija ne sestavlja iz zahteve — Stripe dobi
   `turnirko.stripe.vrni-na` — zato menjava nima stranskih učinkov.) Na
   strežniku z `docker network inspect turnirko_default` preveri, da je omrežje
   res v 172.16.0.0/12; če ga je Docker dal v 192.168.x, prilagodi vzorec.
3. Za prihodnost (IPv6): ključ omejevalnika naj za IPv6 vzame prefiks /64, ne
   celega naslova — en strežnik ima tipično 2^64 naslovov. Zdaj domena nima
   zapisa AAAA, zato ni nujno.
4. Omejevalnik: `ConcurrentHashMap` zamenjaj z omejenim predpomnilnikom
   (Caffeine, `maximumSize(100_000)`, `expireAfterAccess(24h)`), da napad ne more
   porabiti pomnilnika.

**Test:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`:
31 klicev `/auth/predogled-zapisa`, vsak z drugo `Forwarded: for=`, 31. mora
vrniti 429. Enako za 51 napačnih prijav.

---

## V2 — Orakelj za datum rojstva: `POST /auth/predogled-zapisa` (visoka)

**Kaj.** Pot je javna (`VarnostneNastavitve.java:123`) in na vnos ime + priimek +
datum rojstva odgovori `najden: true/false` (`RegistracijaStoritev.java:333–347`).
Ime in priimek sta javna (lestvica), **starostni pas tudi** — pas obsega dve
letnici, torej je pri otroku le ~730 možnih datumov. Pri 30 poskusih na uro z
enega IP je to ~12 ur v povprečju, z V1 nekaj minut. Isto (počasneje) razkrije
registracija sama: odgovor `povezan` po potrditvi e-pošte.

Podatek, ki ga `IgralecJavniDto` skrbno skriva, je tako dosegljiv za
kateregakoli od 1777 igralcev — vključno z ~230 otroki.

**Popravek:**
1. Predogled premakni **za potrditev e-pošte**: pot naj zahteva prijavljen račun
   s potrjenim naslovom (`.authenticated()` + preverba v storitvi), število
   predogledov pa naj se šteje **na račun v bazi** (npr. največ 5 na dan), ne v
   pomnilniku po IP.
2. Vrstni red korakov v `RegistracijaTok.tsx`: e-pošta + geslo → koda → »Klub in
   zapis« (predogled) → povzetek. Uporabnik izgubi le to, da zapis vidi pred
   vpisom gesla.
3. Odgovor `PotrditevOdgovorDto.povezan` naj ne bo edini signal: glej V3.
4. Dodaten pas: globalna meja predogledov (npr. 300 na uro za vse skupaj) — ob
   preseku obvestilo adminu, ker normalna raba tega nikoli ne doseže.

---

## V3 — Samodejna povezava je prevzem identitete z javnimi podatki (visoka)

**Kaj.** `poskusiSamodejnoPovezavo` (`RegistracijaStoritev.java:363–392`) poveže
račun z igralcem, če se ujemajo ime, priimek in datum rojstva. Nič od tega ni
skrivnost: ime je javno, datum se ugane (V2) ali je znan (sošolci, družbena
omrežja, morda celo javni API Stupe, iz katerega je prišel). Napadalec z
**lastnim** e-naslovom tako postane »Žiga Abraham«:
- ima njegov »Moj profil« in mu lahko kupi Premium (zasebne analize),
- pravi igralec se pozneje ne more povezati (`existsByIgralecId`), dokler admin
  ne posreduje,
- pri mlajših od 15 let soglasje skrbnika ne pomaga: naslov skrbnika vpiše
  napadalec sam (`preverjenEmailSkrbnika` preveri le, da se razlikuje od
  otrokovega) — odrasel neznanec tako ustvari račun, povezan z 10-letnim otrokom.

Danes je škoda omejena, ker povezan račun ne vidi osebnih podatkov. Vsaka
prihodnja funkcija »za igralca« (samoprijava na turnir, sporočila, urejanje
profila) pa jo poveča.

**Popravek (odločitev o mejah je tvoja — to je predlog):**
1. **Mlajši od 15 let: nikoli samodejno.** Povezavo potrdi admin; v seznamu
   računov naj bo jasno označeno »mladoletnik, skrbnik: …«.
2. **Odrasli: samodejno le z drugim dokazom.** Ker ima 1648/1777 igralcev
   licenco NTZS, naj registracijski obrazec vpraša še številko licence; povezava
   je samodejna samo, če se ujema tudi ta (normalizirano). Brez licence → admin.
   Opomba: tudi licenca ni prava skrivnost (če je javna v Stupi ali zaporedna),
   zato je to dvig ovire, ne zaklep — zato ostaneta točki 3 in 4.
3. **Obvestilo**: ob vsaki samodejni povezavi naj admin dobi zapis v pregledu
   (»povezano samodejno v zadnjih 7 dneh«) — `razvezi` že obstaja.
4. **»To ni moj račun«**: pravi igralec, ki se ne more povezati, naj v vmesniku
   dobi gumb »Zapis je že povezan — prijavi napako«, ki adminu odpre primer,
   namesto tihega čakanja.
5. Vse registracijske poti zaščiti s CAPTCHA (glej V8), da ugibanje stane.

---

## V4 — Množično kopiranje podatkov (srednja)

To je vprašanje »ali lahko kdo iz Turnirka naloži podatke, kot sem jih jaz iz
Stupe«. **Odgovor: da, in to zelo hitro.** Preverjeno na živi strani:
- `GET /api/v1/igralci` v **0,4 s** vrne **vseh 1767** aktivnih igralcev (ime,
  priimek, spol, klub, starostni pas, rating, število tekem) — 356 kB;
- `GET /api/v1/lestvica` vrne celotno lestvico (261 kB);
- `GET /api/v1/igralci/{id}/profil` vrne celotno zgodovino igralca (do 300 kB,
  0,6 s); id-ji so zaporedni (1, 2, 3 …), zato zanka čez vse traja nekaj minut;
- turnirji, lige, srečanja, mreže — vse po zaporednih id-jih, brez omejitve hitrosti.

**Pošteno:** podatkov, ki jih vidi gost brez prijave, ni mogoče popolnoma
zaščititi — kar pokaže brskalnik, lahko prebere tudi program. Cilj je zato:
(a) nejavni podatki (datum rojstva, licenca, e-pošte) **nikoli** ne uidejo — to
je zdaj v redu, razen prek V2; (b) množičen zajem naj bo počasen, drag in
viden; (c) pravna podlaga, da ga lahko prepoveš.

**Ukrepi, od najučinkovitejšega:**
1. **Brez množičnih seznamov za gosta.** `GET /igralci` (cel šifrant) naj bo
   samo za ORGANIZATOR/ADMIN (rabijo ga obrazci prijav). Gost ga rabi le v
   izbirniku »1 na 1« (`EnaNaEna.tsx`) → nova pot `GET /igralci/iskanje?q=…`
   (najmanj 2 znaka, največ 20 zadetkov).
2. **Straneženje**: `/lestvica?stran=1&velikost=50`, profil: glava + povzetek +
   zadnjih 20 tekem, ostalo `…/profil/tekme?stran=2`. Hkrati hitrejša stran na
   telefonu.
3. **Omejitev hitrosti za GET** (v aplikaciji, isti omejevalnik kot za prijave,
   ali knjižnica Bucket4j): npr. 300 zahtev/min in 3000/h na IP, za drage poti
   (profil, lestvica, dvoboj) 60/min; odgovor 429 z `Retry-After`. **Pazi na
   dvorano:** vsi gledalci na istem Wi-Fi-ju imajo en javni IP, zato meje ne smejo
   biti nizke; prijavljeni organizatorji naj bodo izvzeti.
4. **Nezaporedni javni id-ji** (neobvezno, večji poseg): v javnih naslovih
   kratka naključna oznaka namesto `1, 2, 3`. Sama po sebi ne ustavi zajema (vsi
   igralci so na lestvici), skupaj s 1–3 pa ga upočasni.
5. **Globlji podatki za prijavljene** (produktna odločitev — PRODUCT.md daje
   gostu prednost): npr. gost vidi zadnjih 20 tekem, brezplačen račun vse.
   Zajem potem zahteva račune, ki jih lahko omejiš in zapreš.
6. **Pravno**: v pogojih uporabe izrecno prepovej samodejen zajem; Turnirko kot
   podatkovna baza uživa pravico izdelovalca baze (ZASP, 141.a–141.i člen), ki
   prepoveduje prenos bistvenega dela. `robots.txt` z `Disallow: /api/`
   (ustavi le poštene robote, je pa jasen signal).
7. **Opazovanje**: nočna skripta, ki iz `dnevniki/dostop.log` prešteje zahteve
   po IP in te opozori nad pragom (npr. > 5000 na dan).
8. Neobvezno: Cloudflare (brezplačen paket) pred strežnikom — zaščita pred roboti,
   pravila hitrosti, predpomnjenje. Slabost: tuj ponudnik vidi ves promet
   (tudi prijave) → pogodba o obdelavi in omemba v politiki zasebnosti.

---

## V5 — Zrušitev strežnika z malo truda (srednja)

- `GET /api/v1/domov/lige?idji=1,1,1,…` — seznam ni omejen ne očiščen
  dvojnikov (`DomovStoritev.java:177`); en klic z ~2000 id-ji sproži 2000
  povzetkov lig. **Popravek:** `idji.stream().distinct().limit(LIG_NA_DOMACI)`,
  enako za `ogledane`.
- Profil (0,6 s, 300 kB), lestvica (0,65 s) — brez predpomnjenja in brez meje
  hitrosti; 20–50 vzporednih zahtev zasede majhen VPS. **Popravek:** V4/3 +
  predpomnilnik (Spring Cache + Caffeine, TTL 30–60 s, izbris ob vnosu rezultata)
  za lestvico, profil in domačo stran.
- **Velikost telesa zahteve ni omejena** — webhook bere celo telo v `String`
  (`PlacilaKontroler.java:51`), JSON poti prav tako. **Popravek** v `Caddyfile`:
  ```caddy
  request_body {
      max_size 1MB
  }
  ```
- BCrypt se izračuna ob **vsaki** zahtevi z glavo Authorization (Basic, brez
  seje) in na javni `/placila/registracija` brez meje (V7). Zaradi V1 je to
  neomejeno. Dolgoročno rešitev prinese V6.

---

## V6 — Geslo v brskalniku, brez CSP (srednja)

**Kaj.** Prijava je HTTP Basic: `base64(e-pošta:geslo)` se shrani v
`sessionStorage` (`AvtentikacijaKontekst.tsx:83`) in pošilja ob vsaki zahtevi.
Trenutno nisem našel nobene luknje XSS (React vse ubeži, ni
`dangerouslySetInnerHTML`, ni dinamičnih `href`). A če se kdaj pojavi (nova
knjižnica, razširitev v brskalniku), napadalec ne dobi žetona za eno sejo, ampak
**geslo v čistopisu** — ki ga ljudje uporabljajo tudi drugje. Glave CSP, ki bi
XSS omejila, ni.

**Popravek (v dveh korakih):**
1. Takoj: CSP (V12). Vmesnik nima zunanjih virov (pisave so lokalne), zato je
   stroga politika mogoča.
2. Pozneje: namesto Basic **seja s piškotkom** — `POST /auth/prijava` preveri
   geslo enkrat in nastavi piškotek `HttpOnly; Secure; SameSite=Strict` (Spring
   Session JDBC na SQLite ali preprost lasten žeton v tabeli); takrat je treba
   vklopiti CSRF (Spring `CookieCsrfTokenRepository`). Prednosti: JavaScript
   gesla nikoli ne vidi, BCrypt teče enkrat na prijavo namesto ob vsaki zahtevi,
   odjava in »odjavi vse naprave« postaneta resnična.

---

## V7 — Plačljiva registracija (srednja)

`POST /api/v1/placila/registracija` je javen in:
1. **razkrije, ali e-pošta že ima račun** (`PlacilaStoritev.java:129–131`:
   »Ta e-posta je ze v uporabi.«) — v nasprotju z načelom, ki ga brezplačna
   registracija skrbno spoštuje;
2. pošlje v Stripe kot metapodatke **BCrypt zgostitev gesla**, datum rojstva in
   e-pošto skrbnika (`PlacilaStoritev.java:146–166`) — vidno vsakomur z dostopom do
   Stripe nadzorne plošče, hrani se neomejeno; Stripe sam odsvetuje občutljive
   podatke v metapodatkih;
3. brez omejitve ustvarja Stripe seje in računa BCrypt (zloraba API kvote, CPU);
4. webhook ne preveri `session.getPaymentStatus()` — pri odloženih načinih
   plačila (npr. SEPA, če jih kdaj vklopiš v Stripu) dobi račun paket pred plačilom.

**Popravek (najčistejši):** plačljiva registracija naj ne obstaja kot ločena
pot. Tok: brezplačna registracija → koda → **prijavljen** račun gre na
`/placila/nadgradnja`. To odpravi 1–3 naenkrat (račun in geslo sta že v bazi,
metapodatki nosijo samo `idUporabnik`, pot zahteva prijavo). Vmesnik lahko
uporabniku to pokaže kot en tok (»Ustvari račun → potrdi e-pošto → plačaj«).

Če ločeno pot obdržiš: čakajočo registracijo shrani v svojo tabelo
(`cakajoca_registracija`, z naključnim žetonom), v Stripe pošlji samo žeton
(`client_reference_id`); zaseden naslov obravnavaj tiho (tako kot
brezplačna registracija); dodaj mejo po IP in CAPTCHA. Za 4: obdelaj samo
`payment_status` `paid` ali `no_payment_required`, za ostalo počakaj na
`checkout.session.async_payment_succeeded`.

---

## V8 — Pošiljanje kod kot orodje za spam (srednja)

- Koda skrbniku gre na **poljuben** naslov, meja pa se šteje po otrokovem
  naslovu (`RegistracijaStoritev.java:422–434`) — z menjavo izmišljenih otroških
  naslovov napadalec pošilja na isti tuji naslov, omejen le z 20/h na IP (z V1
  neomejeno).
- Besedilo za skrbnika vsebuje ime in priimek, ki ju vpiše napadalec (do 2×60
  znakov, brez omejitve znakov — `SporocilaPoste.java:125`). Iz domene
  turnirko-nt.si gre lahko »Ana Novak – nagrada na www.zlonamerno.si«; poštni
  odjemalci naslov spremenijo v povezavo. Ugled domene pri Brevu trpi.
- Gmail ignorira pike in `+…`: `a.na@`, `an.a@`, `ana+1@` so za omejevalnik
  različni naslovi, nabiralnik pa isti.

**Popravek:**
1. Meja po **prejemniku** za vsako pošto (tudi skrbnik): ključ `posta:<prejemnik>`,
   3/h, 10/dan.
2. **Globalna meja** vse odhodne pošte (npr. 200/h) — ob preseku registracija
   vrne »poskusi pozneje« in admin dobi opozorilo. Brezplačen Brevo ima itak
   ~300/dan.
3. **CAPTCHA** na registraciji, ponovnem pošiljanju, pozabljenem geslu in
   predogledu. Predlog: **ALTCHA** (odprta koda, dokaz dela v brskalniku,
   preverba na tvojem strežniku, brez tretje strani in piškotkov → ni nove
   obdelave osebnih podatkov). Alternativa: Cloudflare Turnstile.
4. Ime in priimek preveri z vzorcem samo črke (z naglasi), presledek, `-`, `'`
   in `.` — npr. `^[\p{L}][\p{L} .'-]{1,59}$`. Velja za `RegistracijaVnos`,
   `IgralecVnos` in `PredogledZapisaVnos`. Odpravi URL-je v pošti in tudi
   nevidne/obratne znake (`‮`), s katerimi se ime na lestvici ponaredi.
5. Normalizacija Gmail naslovov samo za **ključ** omejevalnika (ne za račun).

---

## V9 — Preverjanje kod (srednja)

- `KodeStoritev.preveri` (`KodeStoritev.java:60–82`) poveča števec poskusov šele
  **po** primerjavi in shrani na koncu transakcije. Več vzporednih zahtev prebere
  isti števec → v enem valu se preveri več kot 5 kod.
  **Popravek:** najprej atomarno `UPDATE potrditvena_koda SET poskusi = poskusi + 1
  WHERE id = ? AND poskusi < 5` (če vrne 0 vrstic → zavrni), šele nato BCrypt.
- Poti `/potrdi-eposto`, `/potrdi-skrbnika`, `/novo-geslo` nimajo meje po IP.
  Napadalec, ki hkrati sproži »pozabljeno geslo« za veliko računov, ima pri
  vsakem 25 ugibanj na uro (5 kod × 5 poskusov); pri 500 računih je to ~1 %
  verjetnosti prevzema na uro. **Popravek:** 30 neuspešnih vnosov kode na IP na
  uro; na račun največ 15 neuspehov na dan, nato »pozabljeno geslo« za ta račun
  24 ur ne pošilja več kod.
- Neobvezno: za pozabljeno geslo 8 števk ali povezava z 128-bitnim žetonom.

---

## V10 — Administratorski račun (srednja)

- Prijavno ime je fiksno `admin` (`application.properties`), prijava gre čez
  isto javno pot kot vsi; ni drugega faktorja; zaklene se ga od zunaj (V1).
- **Popravki:**
  1. Ime admina naj bo nenapovedljivo (npr. `turnirko.admin.uporabnisko-ime`
     v `.env`, obstoječega preimenuj z enkratno migracijo/ukazom).
  2. **TOTP 2FA** za ADMIN (in neobvezno za ORGANIZATOR): ob prijavi poleg gesla
     še 6-mestna koda iz aplikacije (Google Authenticator, Aegis). Knjižnica
     `dev.samstevens.totp` ali lasten HOTP (RFC 6238, ~60 vrstic). Z V6 (seja)
     je to preprosto; z Basic je treba kodo pošiljati v glavi.
  3. Meja neuspešnih prijav za admina naj ne zaklene prijave z **dovoljenih**
     naslovov (seznam IP v `.env`), sicer je zaklep orodje za nagajanje.
  4. **Dnevnik sprememb** (RAZVOJNI-NACRT, razdelek 7, je že na seznamu): kdo je
     kdaj spremenil rezultat, žreb, igralca, račun — tabela `dnevnik_sprememb`
     (račun, čas, dejanje, id zapisa, staro → novo). Brez tega zlorabe
     organizatorjevega ali ukradenega računa ni mogoče niti opaziti niti
     popraviti.
  5. Gesla, ki jih nastavi admin (`ponastaviGeslo`), naj bodo enkratna:
     zastavica `zamenjaj_geslo` → po prijavi vmesnik zahteva novo.

---

## V11 — Namizna različica: admin/admin na Wi-Fi dvorane (srednja)

`ZacetniAdmin.java:43` — pod profilom `namizni` je geslo `admin`, OBJAVA.md pa
svetuje, naj gledalci stran odprejo prek naslova prenosnika na istem Wi-Fi-ju.
Vsak gledalec se lahko prijavi kot admin in popravlja rezultate (po HTTP,
nešifrirano).

**Popravek** (eno od):
- ob prvem zagonu naključno geslo, izpisano v konzolo in shranjeno v
  `podatki/admin-geslo.txt`; ali
- v profilu `namizni` dovoli mutacije (ne-GET) samo z `127.0.0.1`/`::1`
  (pravilo v varnostni verigi z `IpAddressMatcher`); gledalci še vedno berejo.

---

## V12 — Varnostne glave (nizka)

Živa stran ima HSTS, `nosniff`, `X-Frame-Options`, `Referrer-Policy`. Manjkajo:

```caddy
header {
    Strict-Transport-Security "max-age=31536000"
    X-Content-Type-Options "nosniff"
    X-Frame-Options "DENY"
    Referrer-Policy "same-origin"
    Content-Security-Policy "default-src 'self'; script-src 'self' https://statistika.turnirko-nt.si; connect-src 'self' https://statistika.turnirko-nt.si; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self' https://checkout.stripe.com; frame-ancestors 'none'"
    Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=()"
    Cross-Origin-Opener-Policy "same-origin"
    -Server
    -Via
}
```

Najprej jo vklopi kot `Content-Security-Policy-Report-Only` za nekaj dni in v
konzoli brskalnika preveri, da nič ne poči (Stripe preusmeritev gre z
`window.location`, zato `form-action` ni nujen; `'unsafe-inline'` za slog je
potreben, če Vite vstavi slog v `<style>`). Za Umami subdomeno enako brez
`script-src` za statistiko.

Drobnarija: Tomcat na neveljavno pot (`/%2e%2e/…`) vrne svojo HTML stran 400
(»HTTP Status 400«), še preden pride do Springa — razkrije le, da gre za
Tomcat. Kozmetično.

---

## V13 — Gesla (nizka)

- Najmanj 8 znakov, brez preverbe šibkih gesel. **Popravek:** zavrni gesla s
  seznama najpogostejših (10.000 iz SecLists, datoteka v viru) in gesla, ki
  vsebujejo e-pošto ali ime.
- `@Size(max = 100)` šteje znake, BCrypt v Spring Security 7 pa **vrže napako
  nad 72 bajti** (preverjeno v `spring-security-crypto-7.0.2`: »password cannot
  be more than 72 bytes«) — geslo z 80 znaki ali 40 šumniki da napako 500.
  **Popravek:** v `RegistracijaVnos`, `NovoGesloVnos`, `SpremembaGeslaVnos`,
  `NastavitevGeslaVnos` preveri `geslo.getBytes(UTF_8).length <= 72` z jasnim
  sporočilom (ali `max = 64`).

---

## V14 — Plačila: manjše preverbe (nizka)

- Webhook: ob prazni `turnirko.stripe.webhook-skrivnost` naj `obdelajDogodek`
  takoj zavrne (zdaj ga po naključju ustavi `SecretKeySpec`, ki prazen ključ
  zavrne — ne zanašaj se na to).
- `zacniNadgradnjo` ne preveri, ali račun že ima **aktivno** Stripe naročnino —
  drug nakup prepiše `stripe_narocnina_id`, stara naročnina pa v Stripu teče in
  se zaračunava naprej (dvojno plačilo). Zavrni, če `jeVeljavna()` in
  `stripeNarocninaId != null` (za menjavo paketa je `/narocnina/paket`).
- Idempotenca webhooka sloni na branju pred pisanjem; dodaj `UNIQUE` na
  `narocnina.stripe_narocnina_id` (nova migracija), če ga še ni.

---

## V15 — Kvalifikacije med ligami (nizka)

`KvalifikacijeStoritev.ustvari` (`KvalifikacijeStoritev.java:201`) preveri
lastništvo le višje lige; nižja je lahko tuja. Tuje lige ne spremeni (ekipe
kopira), a organizator lahko javno objavi kvalifikacije s tujo ligo.
**Popravek:** `lastnistvo.preveriLigaPoId(v.idNizja())` ali vsaj
`preveriLigaZaOpis`.

Vse ostale spreminjajoče poti (40 metod storitev za turnirje, dogodke, tekme,
lige, srečanja) lastništvo preverijo — preverjeno s skripto. Edina brez
preverbe, `LigaStoritev.nastaviNaDomaci`, je v varnostni verigi omejena na ADMIN.

---

## V16 — Strežnik, vsebniki, kopije, Umami (srednja)

1. **Varnostne kopije niso odnesene s strežnika** (odnašanje je v skripti
   zakomentirano) in niso šifrirane. Ob izgubi ali prevzemu strežnika gredo vse.
   **Popravek:** šifriraj z `age` (javni ključ na strežniku, zasebni samo doma) in
   pošlji v EU shrambo (npr. Hetzner Storage Box ali Backblaze EU prek `rclone`):
   ```sh
   age -r "$KLJUC_JAVNI" -o "$CILJ.age" "$CILJ" && rm "$CILJ"
   rclone copy "$CILJ.age" kopije:turnirko/
   ```
   Enkrat na mesec kopijo obnovi in odpri — sicer ne veš, ali dela.
2. **Umami**: ob prvem zagonu ima `admin`/`umami` na javni domeni. Preden zapis
   DNS obstaja, ali pa nadzorno ploščo v `Caddyfile` zapri in javno pusti samo
   to, kar rabi števec:
   ```caddy
   statistika.turnirko-nt.si {
       @javno path /obisk.js /api/send
       handle @javno { reverse_proxy umami:3000 }
       handle { basic_auth { lastnik <bcrypt-hash iz "caddy hash-password"> }
                reverse_proxy umami:3000 }
   }
   ```
3. **Vsebniki** (`compose.yaml`, storitev `app`):
   ```yaml
   read_only: true
   tmpfs: [/tmp]
   cap_drop: [ALL]
   security_opt: [no-new-privileges:true]
   mem_limit: 768m
   ```
   (preveri, da aplikacija piše samo v `/podatki` in `/tmp`). Sliko `caddy:2`
   pripni na točno različico kot Umami.
4. **SSH in sistem**: v `/etc/ssh/sshd_config` `PasswordAuthentication no` in
   `PermitRootLogin no`; `sudo apt install unattended-upgrades` (samodejni
   varnostni popravki). Docker objavljena vrata obidejo `ufw` — danes so
   objavljena samo 80/443, pazi, da tako ostane.
5. `.dockerignore`: dodaj `zaledje/config/` (lokalno geslo SMTP gre sicer v
   gradbeni korak slike) in `*.csv`.
6. Odvisnosti: `npm audit` javi react-router (GHSA-qwww-vcr4-c8h2, velja le za
   način RSC, ki ga ne uporabljaš — vseeno `npm update react-router-dom`).
   V GitHubu vklopi **Dependabot** (deluje tudi za zasebne repozitorije) za
   Maven, npm in Docker; Spring Boot drži na zadnji 4.0.x.

---

## V17 — Kopije produkcijske baze na namizju (srednja)

`Desktop/Turnirko/popravek-produkcija/` ima tri kopije produkcije (~70 MB vsaka:
datumi rojstva in licence 1777 oseb), poleg tega `uvoz-*.csv`. Izguba ali kraja
prenosnika = uhajanje osebnih podatkov (prijava Informacijskemu pooblaščencu v
72 urah).
**Popravek:** po končanem popravku jih izbriši (ali premakni v šifriran
vsebnik); preveri, da je na prenosniku vklopljen **BitLocker**
(Nastavitve → Zasebnost in varnost → Šifriranje naprave).

---

## V18 — GDPR in pravna plat (srednja; nisem pravnik)

- Registracija zahteva kljukico »Strinjam se s pogoji uporabe in obdelavo
  podatkov«, **pogojev in politike zasebnosti pa ni** (ne strani ne povezave).
- Podatki 1777 oseb so prišli iz Stupe in stare strani NTZS, ne od njih samih →
  GDPR člen 14 zahteva obvestilo (kdo obdeluje, zakaj, na kateri podlagi, kako
  dolgo, pravice). Podlaga je verjetno zakoniti interes ali dogovor z NTZS — to
  je treba zapisati; pri mladoletnih je presoja strožja.
- Pogodbe o obdelavi (DPA) s Stripom, Brevom in ponudnikom VPS (vsi jih
  ponujajo s klikom).
- Postopek za pravico do izbrisa/vpogleda (zdaj samo admin ročno) — dovolj je,
  če je opisan v politiki.
- Dnevnik Caddyja hrani IP naslove (50 MB kroženja) — navedi v politiki.

**Predlog:** stran `/zasebnost` in `/pogoji` (statični besedili), povezavi iz
noge in iz registracije; v pogoje prepoved samodejnega zajema (V4/6). Za
besedilo se posvetuj z nekom, ki pozna ZVOP-2 (npr. predloga Informacijskega
pooblaščenca za manjše upravljavce).

---

## Kaj je že dobro (ne podiraj)

- Ločitev `IgralecJavniDto` / `IgralecDto` na ravni tipa in test na surovo telo —
  noben javni odgovor ne nosi osebnih podatkov (preverjeno za vse DTO-je).
- Lastništvo preverjeno v vseh 40 spreminjajočih metodah storitev.
- Gesla in kode samo kot BCrypt; odgovori registracije, ponovnega pošiljanja in
  pozabljenega gesla ne razkrijejo obstoja naslova; enak čas odgovora.
- Pošta brez povezav (nič za ponaredit), naslov v dnevniku zakrit.
- Admin brez privzetega gesla na strežniku; admin ne gre skozi pozabljeno geslo.
- Brez sledi sklada v odgovorih, brez actuatorja, CORS zaprt, vrata 8080 niso
  objavljena, aplikacija ne teče kot root, v gitu ni skrivnosti, repozitorij je
  zaseben, SQL je parametriziran.
- Poskus branja datotek z žive strani (`/../application.properties`,
  `/.env`, razredi, migracije) — nič ne uide.

---

## Načrt izvedbe

**Faza 0 — takoj (ena ura, brez nove logike):**
V1 (Caddy `header_up -Forwarded` + `native`), `request_body max_size` (V5),
glave v Report-Only (V12), omejitev `idji` (V5), meja 72 bajtov (V13), prazna
skrivnost webhooka (V14), `.dockerignore` (V16/5), izbris kopij z namizja (V17).

**Faza 1 — ta teden:**
V2 + V3 (predogled za potrditvijo, samodejna povezava z licenco, otroci prek
admina), V8 (meja po prejemniku, globalna meja, vzorec imen), V9 (atomarno
štetje, meja po IP), V7 (plačilo samo kot nadgradnja), V11, V15, V14 ostalo.

**Faza 2 — naslednji teden:**
V4 (iskanje namesto celega šifranta, straneženje, omejitev hitrosti GET,
predpomnilnik), CAPTCHA (ALTCHA), V10 (ime admina, dnevnik sprememb), V16
(šifrirane kopije izven strežnika, utrditev vsebnikov, SSH).

**Faza 3 — ko bo čas:**
V6 (seja s piškotkom namesto Basic) → nato TOTP za admina (V10/2), stroga CSP,
V18 (besedila politike in pogojev).
