# Testno okolje (test.turnirko-nt.si)

Druga, ločena kopija Turnirka na istem strežniku. Namen: vsaka sprememba kode
najprej steče tam, na kopiji prave baze, in šele ko je pregledana, gre v
produkcijo. Migracije so enosmerne, zato je to edino mesto, kjer se vidi, da
migracija na pravih podatkih odpove, preden odpove v produkciji.

## Kaj je kaj

| Kos | Produkcija | Test |
|---|---|---|
| Naslov | `https://turnirko-nt.si` | `https://test.turnirko-nt.si` (skrita povezava) |
| Mapa na strežniku | `/srv/turnirko` | `/srv/turnirko-test` (svoj klon repozitorija) |
| Veja Gita | `main` | `test` |
| Compose datoteka | `compose.yaml` | `compose.test.yaml` |
| Vsebnika | `app`, `baza` (+ `umami`, `caddy`) | `test-app`, `test-baza` |
| Baza | zvezek `turnirko_turnirko_baza` | zvezek `turnirko-test_baza_test` (kopija produkcije) |
| Pošta | prava (SMTP) | v dnevnik (kode: `skripte/test/koda.sh`) |
| Plačila, push | prava | ugasnjena (ni ključev) |
| Statistika | Umami šteje | ne šteje (skript je omejen na `turnirko-nt.si`) |

**Strežnika ne delimo na dele.** Docker vsaki kopiji da svojo mapo, svoj
zvezek za bazo in omejitev pomnilnika (`test-app` največ 1 GB, `test-baza`
512 MB). Caddy je še vedno samo en, v produkciji: njegov blok
`test.turnirko-nt.si` v `Caddyfile` usmeri promet v `test-app` in sam pridobi
certifikat.

## Pot kode: od commita do produkcije

```
računalnik                  GitHub                strežnik
-----------                 ------                --------
commit na vejo test  ---->  test       ---->  objavi.sh  -> test.turnirko-nt.si
                                                    pregled (ti)
git push origin <sha>:main  main       ---->  v-produkcijo.sh -> turnirko-nt.si
```

1. **Commit in push na vejo `test`.** Delovna mapa na računalniku je vedno na
   veji `test`; na `main` se ne commita.
2. **Na test:** `bash /srv/turnirko-test/skripte/test/objavi.sh`. Skripta
   potegne `test`, zgradi, zažene, počaka na zdravo aplikacijo (Flyway takrat
   izvede nove migracije nad kopijo prave baze), izpiše zadnje migracije in
   število napak v dnevniku ter zapiše preizkušen commit v
   `/srv/turnirko-test/.zadnja-objava`. Traja okoli 2 minuti; produkcija teče
   naprej.
3. **Pregled** na `https://test.turnirko-nt.si`: kar si spremenil, pa tudi
   domača stran, lestvica, turnir, prijava, telefon. Če se je kaj pokvarilo,
   popraviš, commitaš na `test` in ponoviš 2. korak. Produkcija o tem ne ve
   ničesar.
4. **Povišanje v `main`:** `git push origin <commit>:main` (commit je izpisan
   ob koncu `objavi.sh`). Ker je `main` vedno prednik veje `test`, je to
   *fast-forward* in ne zahteva prestavljanja vej.
5. **V produkcijo:** `bash /srv/turnirko/skripte/test/v-produkcijo.sh`.
   Skripta zavrne objavo, če `main` vsebuje karkoli, česar test ni zgradil.
   Če objava prinese migracijo, najprej naredi ročno varnostno kopijo. Če se je
   spremenil `Caddyfile`, ga preveri in znova zažene Caddy.

Nujen popravek produkcije gre po isti poti: commit na `test`, `objavi.sh`,
hiter pregled, povišanje, `v-produkcijo.sh`. Zaradi vmesnega koraka traja
nekaj minut dlje, a produkcija nikoli ne dobi kode, ki ni stekla vsaj enkrat.

## Osvežitev baze iz produkcije

`bash /srv/turnirko/skripte/test/osvezi-bazo.sh`

Izbriše testno bazo in vanjo prenese svežo kopijo produkcijske (`pg_dump` po
cevi v `pg_restore`; produkcijska baza se samo bere), nato zažene testno
aplikacijo, da Flyway izvede nove migracije. Naredi to pred vsakim pomembnim
preizkusom, predvsem pred preizkusom migracije. Vse, kar si v testu vnesel,
se prepiše.

## Pošta, kode za registracijo

Testna aplikacija nikoli ne pošlje prave e-pošte: baza je kopija s pravimi
naslovi, zato bi obvestila po kolu in po turnirju sicer šla pravim ljudem.
Sporočila se izpišejo v dnevnik; kodo za registracijo ali pozabljeno geslo
preberi z:

```
bash /srv/turnirko/skripte/test/koda.sh
```

## Dostop (skrita povezava)

Test je zaklenjen, ker vsebuje kopijo pravih podatkov in nepreverjeno kodo.
Aplikacija sama uporablja glavo `Authorization`, zato geslo na vratih Caddyja
(basic auth) ne pride v poštev. Namesto tega:

1. odpreš `https://test.turnirko-nt.si/odpri/<TEST_ZETON>` (žeton je v
   `/srv/turnirko/.env`; polno povezavo izpiše `vklopi-caddy.sh`),
2. Caddy ti nastavi piškotek `test_dostop` (30 dni) in te preusmeri na `/`,
3. vsak obisk brez piškotka dobi 404, kot da strani ni.

Povezavo odpreš enkrat na vsaki napravi (računalnik, telefon). Če žeton
uide, ga zamenjaš: vrednost `TEST_ZETON` v `/srv/turnirko/.env` in
`docker compose up -d --no-deps --force-recreate caddy`. Vsi piškotki
prenehajo veljati.

Na vrhu strani je rjast trak »TESTNO OKOLJE« (skript v `vmesnik/index.html`,
prikaže se samo na naslovih, ki se začnejo s `test.`), da test ne zamenjaš s
pravo stranjo.

## Prva nastavitev (enkrat)

1. Pri ponudniku domene dodaj zapis **A**: ime `test`, vrednost IP strežnika
   (`46.225.187.162`). Počakaj, da se razširi (`nslookup test.turnirko-nt.si`).
2. Na strežniku: `cd /srv/turnirko && git pull --autostash`, nato
   `bash skripte/test/nastavi.sh` (klon v `/srv/turnirko-test`, `.env` z novimi
   gesli, `TEST_ZETON` v produkcijski `.env`).
3. `bash /srv/turnirko-test/skripte/test/objavi.sh` (prva gradnja).
4. `bash /srv/turnirko/skripte/test/osvezi-bazo.sh` (kopija baze).
5. `bash /srv/turnirko/skripte/test/vklopi-caddy.sh` (to je edini korak, ki se
   dotakne produkcije: Caddy se za nekaj sekund znova zažene).

Zapis DNS mora veljati *pred* 5. korakom: Let's Encrypt zavrne po petih
neuspelih poskusih na uro in Caddy bi ga sam prehitel.

## Česa ne smeš

- **V `/srv/turnirko-test` ne poganjaj `docker compose` z drugo datoteko**
  (`-f compose.yaml`): to je produkcijski compose, v testni mapi bi zagnal
  drugo kopijo produkcije. Ob običajnem `docker compose ...` v tej mapi
  poskrbi `.env` (`COMPOSE_FILE`, `COMPOSE_PROJECT_NAME`), da gre za test.
- **Stripe, SMTP in push v test ne vnašaj**, razen če veš, kaj delaš: baza je
  kopija z naslovi pravih igralcev. Za preizkus plačil bi se dodal Stripe
  sandbox (ključ `sk_test_`, nikoli `sk_live_`) in lasten webhook.
- Testno geslo skrbnika (`TURNIRKO_ADMIN_GESLO` v testnem `.env`) velja samo za
  prazno bazo; po osvežitvi iz produkcije se v testu prijaviš s svojim
  produkcijskim računom.

## Za katero kodo je test slab

Test ne zazna: pravega pošiljanja pošte, pravih plačil, obremenitve (ima manj
pomnilnika kot produkcija), motenj, ki jih povzroča samo pravi promet. Za to
ostane `skripte/preklop/po-preklopu.sh` po objavi in redno spremljanje.
