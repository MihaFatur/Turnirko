# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Uporabnik številka ena: gledalec brez prijave, na telefonu.** Pride med
turnirjem ali po njem, ničesar ne ureja in se nikoli ne prijavi. Njegova
opravila (vsa štiri potrjena):

- spremlja turnir v živo — mreža, tekoči rezultati, kdo še igra;
- po koncu pogleda izid — končna razvrstitev, svoje tekme, sprememba ratinga;
- brska po lestvici, profilih igralcev in medsebojnih dvobojih neodvisno od
  posameznega dogodka;
- preveri koledar — kdaj in kje je naslednji turnir ali ligaško srečanje.

Ostale potrjene skupine (podpora, ne merilo odločitev):

- **organizator / sodnik** — na turnirski dan izpelje žreb, vnaša rezultate,
  tiska listke; ureja samo svoja oz. klubska tekmovanja;
- **administrator** — igralci, šifranti (klubi, kraji), računi, postavitveni
  rating, potrjevanje organizatorjev;
- **prijavljen igralec** — svoj profil s statistiko, ki je javno ne kaže
  (nasprotniki, forma, nizi in točke, razrez po tekmovanjih).

## Product Purpose

Turnirko vodi namiznoteniška tekmovanja — posamične turnirje z več dogodki in
ekipne lige — po pravilih NTZS: prijave, žreb, mreže in skupine, vnos
rezultatov, razvrstitve, klubski ELO in natisljivi zapisniki. Vse, kar sme biti
javno, je javno dosegljivo brez prijave.

Uspeh je dvojen: (1) turnir se od prijav do končne razvrstitve izpelje v celoti
v Turnirku, brez vzporednega Excela, in (2) človek, ki ni bil v dvorani, na
telefonu v nekaj sekundah najde, kaj se je zgodilo.

## Positioning

Turnirko je **slovensko orodje za turnirski dan in klubsko sceno, ne zamenjava
federacijske platforme.** NTZS od pribl. sezone 2024/25 uporablja Stupa Events
(partner ITTF); tekmovanje z njo prek funkcionalnosti ni realno. Kar Turnirko
ima in tega generični turnirski SaaS nima:

- slovenska pravila kot koda, ne kot nastavitev — PST/POT, formati, tie-break
  kaskada, oznake lig;
- slovenščina povsod, tudi v napakah in izpisih;
- tiskani zapisniki v obliki, ki jo dvorana dejansko uporablja (sodniški listki,
  uradni ekipni zapisnik NTZS z variantama predloge);
- javna, brskljiva lestvica in pregled 1-na-1 — NTZS jakostne lestvice še vedno
  objavlja kot statične .xlsx/.pdf;
- klubski ELO z dnevnikom vseh sprememb, ki ga tekmovanje lahko tudi izklopi.

## Operating Context

- **Regionalni piloti so cilj naslednjih 12 mesecev:** 3–5 klubov v Savinjski /
  Štajerski regiji in rekreativno-veteranski turnirji. Prvi uporabnik ostaja
  NTK Savinja; vsak domači turnir je test v produkciji.
- **Delovanje brez interneta ni več zahteva, odpornost pa je.** Pilote poganja
  gostovana storitev, zato vmesnik ne sme imeti zunanjih klicev (pisave in
  sredstva so vgrajena lokalno) in mora preživeti slab Wi-Fi v telovadnici.
  Sinhronizacijskega pogona za resnično offline delo se ne gradi.
- **Papir je del poteka dela.** Sodniški listki (10 na A4) in ekipni zapisnik
  NTZS se natisnejo in izpolnjujejo na roko; natisne jih brskalnik, brez
  zaledja in brez lastne sheme.
- **Zaslon je večinoma telefon.** Javne poglede se odpira med turnirjem v
  dvorani ali kasneje doma.

## Capabilities and Constraints

Potrjeno delujoče:

- sistemi tekmovanja: izločilni (s prostimi mesti), krožni, skupine + izločilni,
  in `SKUPINE` = format TOP (npr. TOP 24 kot 3 skupine po 8, skupine so rangi);
- turnir → več dogodkov (disciplina, spol, kategorija), prijave z jakostnim
  vrstnim redom in mehkim izbrisom ob odjavi;
- ekipne lige: srečanja, postava, uradni zapisnik, ligaška lestvica;
- izidi: igrano, brez boja, predaja, diskvalifikacija; točke po nizih z
  validacijo mogočega vrstnega reda; odstop med tekmovanjem;
- klubski ELO z dnevnikom (`rating_zgodovina`), dinamičnim K, ničvsotnim
  zaokroževanjem in margino po presenečenju; štetje v ELO je izbirno na ravni
  turnirja in lige;
- štiri vloge (admin, organizator, igralec, gost) z lastništvom na ravni zapisa;
- javna lestvica, profil igralca z grafom ELO, pregled 1-na-1.

Trdne omejitve, ki jih prihodnje delo ne sme razbiti:

- shemo baze spreminja izključno nova Flyway migracija (`ddl-auto=none`);
- navzven gredo samo DTO-ji; **osebni podatki (e-pošta, telefon, naslov, datum
  rojstva) nikoli v javne poglede** — javno je ime, priimek in klub;
- statuse in pravice določa strežnik, vmesnik dejanja le skrije; gost sme samo
  `GET`; gesla samo kot BCrypt zgostitev, brati jih ni mogoče;
- igralca se ne briše, samo arhivira — tekmovalna zgodovina ostane;
- vsaka sprememba domenske logike (žreb, rezultati, rating) potrebuje test.

Izrecno neodločeno / še ne obstaja (ne razglašaj za obstoječe):

- uradne jakostne točke NTZS (PST čl. 30) — implementiran je samo klubski ELO;
- gostovanje: danes SQLite in lokalni zagon; Postgres in večklubska raba sta
  načrtovana, ne izvedena;
- dvojice v krožnem in skupinskem sistemu — turnirske dvojice obstajajo, a
  igrajo izključno izločilno mrežo (par je ena prijava, v ELO ne šteje);
- ekipna tekmovanja onkraj že podprtih lig SNTL;
- mize, urniki, prijavni roki in prijavnine;
- uvoz jakostne lestvice NTZS (.xlsx) in uradni izvozi rezultatov.

## Brand Commitments

- Ime **Turnirko**. Edina ikona je znak z loparjem in žogico; drugih ikon ni.
- **Jezik je slovenščina** — vmesnik, sporočila napak in izpisi s šumniki;
  imena v kodi (razredi, metode, stolpci) slovensko brez šumnikov. Prevoda v
  druge jezike ni in ni načrtovan.
- **Oblikovni sistem je zavezujoč in že zapisan:**
  `vmesnik/turnirko-profil-redesign/project/DESIGN.md` (značaj »športni zapisnik
  na papirju«) z maketami `*.dc.html` v isti mapi. Če predlog nasprotuje temu
  dokumentu, se popravi predlog. Ta datoteka tega sistema ne razširja in ga ne
  nadomešča.
- **Ton:** stvaren in kratek, kot zapisnik. Brez vzklikov, brez emojijev, brez
  navdušenih pridevnikov; številka pove več kot beseda.

## Evidence on Hand

- Pravilnika NTZS, izvlečeno besedilo: `docs/pravilniki/pst.txt` in `pot.txt`
  (Priloge A/B/C so predloge za skupinski sistem in žreb z nosilci).
- Razvojna strategija, tržna raziskava in faze: `docs/RAZVOJNI-NACRT.md`
  (20. 7. 2026) — vsebuje tudi oznake zanesljivosti virov.
- Zavezujoče makete zaslonov in `DESIGN.md` v
  `vmesnik/turnirko-profil-redesign/project/`.
- Delujoča aplikacija s testno bazo in semenskimi podatki (`podatki/*.db` je
  odvržljiv razvojni podatek, ne dokaz).
- **Česa ni in se ne sme izmišljati:** referenc ali izjav zunanjih klubov,
  kakršnegakoli dogovora ali pogodbe z NTZS, števila uporabnikov, cenika,
  uradnih jakostnih točk, primerjav s Stupo, ki niso v razvojnem načrtu.

## Product Principles

1. **Javni pogled je izdelek, ne stranski produkt.** Kar sme biti javno, mora
   biti dosegljivo brez prijave in berljivo na telefonu, brez iskanja po menijih.
2. **Pravilnik je vir resnice.** Kjer se pravilnik NTZS in udobje razideta,
   zmaga pravilnik; bližnjica se zapiše kot znana izjema, ne kot tiho pravilo.
3. **Strežnik odloča, vmesnik prikazuje.** Statusi, pravice in rating se
   računajo na zaledju; vmesnik pravil ne podvaja, le vodi vnos.
4. **Papir je enakovredna izhodna naprava.** Vsak izpis mora biti berljiv
   črno-belo, brez interneta in brez razlage.
5. **Zasebnost je privzeta.** Osebni podatek se prikaže samo tistemu, ki ima
   pravico; javno ime, priimek in klub sta zgornja meja, ne izhodišče.

## Accessibility & Inclusion

Formalnega standarda (npr. WCAG raven) uporabnik ni zavezal. Znane
uporabniške potrebe, ki iz konteksta izhajajo:

- **telefon v dvorani** kot glavna naprava — dovolj velika zadetkovna površina
  in berljivost pri slabi svetlobi;
- **rekreativno-veteransko občinstvo** — velikosti pisav in kontrast se ne
  žrtvujeta za gostoto;
- **mladoletni tekmovalci** — soglasje staršev in ravnanje z letnico rojstva sta
  odprti zahtevi iz razvojnega načrta (§7), pred prvim zunanjim pilotom.
