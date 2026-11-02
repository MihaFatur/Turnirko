# Objava Turnirka na splet

Navodilo za prvo postavitev. Predpostavlja, da streznika se nisi imel.
Ce se odlocas, ali sploh objaviti, preberi najprej razdelek
[Kdaj objava sploh ni potrebna](#kdaj-objava-sploh-ni-potrebna).

## Kaj je ze urejeno

| Zahteva | Stanje | Kje |
|---|---|---|
| Osebni podatki niso javni | urejeno | `IgralecJavniDto`, `VarnostneNastavitve` |
| Brez privzetega admin gesla | urejeno | `ZacetniAdmin` |
| HTTPS | pripravljeno | `Caddyfile` |
| Vmesnik in API v enem izdelku | urejeno | Mavnov profil `splet`, `SpletniVmesnik` |
| Baza PostgreSQL | pripravljena | `compose.yaml` (storitev `baza`), razdelek 4 |
| Varnostne kopije | skripte pripravljene | `skripte/varnostna-kopija.sh`, `skripte/vaja-obnove.sh` |
| Selitev s SQLite | pripravljena | `docs/PREKLOP.md` |

Kar **ni** urejeno in ostane pred prvim tujim pilotom: starsevska soglasja
za mladoletnike, pogodba o obdelavi podatkov s klubom in dnevnik
sprememb (kdo je kaj popravil). Glej `RAZVOJNI-NACRT.md`, razdelek 7.

## Kaj potrebujes

| Kos | Kaj je | Priblizen strosek |
|---|---|---|
| Streznik (VPS) | racunalnik, ki je vedno prizgan | 4–6 EUR/mesec |
| Domena | ime, ki kaze nanj | ~15 EUR/leto |
| Certifikat | omogoci `https://` | brezplacno (Let's Encrypt) |

Streznik izberi v **EU** (npr. Nemcija ali Finska) - v bazi so osebni
podatki igralcev. Najcenejsi paket zadostuje z veliko rezerve.

## Postopek

### 1. Domena

Registriraj jo pri poljubnem registrarju. V nastavitvah DNS dodaj zapis
tipa **A**, ki kaze na IP naslov streznika (dobis ga v 2. koraku).
Sprememba zacne veljati v nekaj minutah do nekaj ur.

### 2. Streznik

Ustvari VPS z **Ubuntu**. Prijavi se s **kljucem SSH**, ne z geslom.
Nato na strezniku:

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

Zadnja vrstica zapre vsa vrata razen SSH, HTTP in HTTPS. **Vrata 8080 naj
ostanejo zaprta** - do aplikacije se pride samo skozi Caddy.

### 3. Koda in skrivnosti

```bash
sudo mkdir -p /srv && cd /srv
sudo git clone <naslov-tvojega-repozitorija> turnirko
cd turnirko
cp .env.primer .env
openssl rand -base64 24        # rezultat prilepi v .env
nano .env
chmod 600 .env
```

V `Caddyfile` sta ze vpisani domena `turnirko-nt.si` in naslov za opozorila
Let's Encrypta; ob menjavi domene popravi oboje.

Preden zazenes Caddy, mora zapis A ze kazati na ta streznik in biti viden v
javnem DNS. Let's Encrypt lastnistvo preveri tako, da se na domeno poveze po
vratih 80; ce ta se kaze drugam, izdaja certifikata spodleti in po nekaj
poskusih te zacasno zavrne. Preveri z `nslookup turnirko-nt.si 8.8.8.8`.

### 4. Baza PostgreSQL in mapa za datoteke

Vsi podatki Turnirka (igralci, turnirji, rezultati, rating, racuni) so v
**bazi PostgreSQL**. Baza je poseben program, ki tece v svojem vsebniku
(storitev `baza` v `compose.yaml`) in skrbi za podatke; aplikacija ji samo
pise in jo bere po omrezju znotraj streznika. Od zunaj do nje ne pride
nihce: njena vrata niso objavljena, tako kot vrata aplikacije.

**Podatki ne lezijo v mapi, ampak v Dockerjevem zvezku** (`turnirko_baza`).
Zvezek je prostor na disku, ki ga upravlja Docker in ki prezivi vsako
posodobitev vsebnika. Zato velja pravilo: **mape ali zvezka baze nikoli ne
kopiraj** (kopija med delovanjem je okvarjena) - kopije delaj samo s skripto
`skripte/varnostna-kopija.sh` (razdelek 7).

**Kaj je vloga?** Baza ima svoje uporabnike, imenovane vloge, in vsaka sme
samo tisto, kar potrebuje. Turnirko uporablja tri:

| Vloga | Kdo jo uporablja | Kaj sme |
|---|---|---|
| `postgres` (skrbnik) | ti, rocno | vse; za vzdrzevanje in kopije |
| `turnirko_lastnik` | Flyway ob zagonu, ukazi `uvoz` / `popravek` / `prenos` | gradi in spreminja tabele |
| `turnirko_app` | delujoca aplikacija | samo branje in pisanje vrstic |

Zakaj tri: ce kdo vdre v aplikacijo, dobi v roke samo `turnirko_app` in ne
more ne zbrisati tabel ne spremeniti sheme. Aplikacija ima poleg tega
omejitve: poizvedba, ki tece dlje kot 30 s, se ustavi; cakanje na zaklep
konca po 5 s; transakcija, ki 60 s samo caka, se prekine. Ena pozabljena
poizvedba tako ne more ustaviti turnirja.

Pred prvim zagonom v `.env` vpisi tri **razlicna** gesla (vsako ustvari z
`openssl rand -base64 24`): `TURNIRKO_BAZA_ADMIN_GESLO`,
`TURNIRKO_BAZA_LASTNIK_GESLO`, `TURNIRKO_BAZA_APP_GESLO`. Vloge ustvari
skripta `skripte/baza/00-vloge.sh` **samo ob prvem zagonu** (ko je zvezek
prazen), zato gesel kasneje ne zamenjas s spremembo `.env` - glej spodaj.

Potem pripravi mapo za datoteke aplikacije (posnetki Stupe, porocila uvoza):

```bash
mkdir -p podatki dnevniki kopije
sudo chown -R 10001:10001 podatki
chmod +x skripte/*.sh skripte/baza/*.sh
```

Stevilka 10001 je uporabnik, pod katerim tece aplikacija v vsebniku (glej
`Dockerfile`); brez tega v mapo ne bo mogla pisati.

**Pregled baze.** Ukazna vrstica baze (`psql`):

```bash
sudo docker compose exec baza psql -U postgres -d turnirko
```

Primer: `SELECT count(*) FROM igralec;`, izhod z `\q`. Za grafični pregled od
doma (DBeaver, pgAdmin) uporabi tunel SSH; vrata baze namrec niso objavljena,
zato tunel pokaze na naslov vsebnika:

```bash
# na strezniku: naslov vsebnika baze
sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' turnirko-baza-1
# doma (naslov vstavi namesto 172.18.0.2):
ssh -L 5433:172.18.0.2:5432 <uporabnik>@<streznik>
```

Nato se v DBeaverju povezes na `localhost:5433`, baza `turnirko`, uporabnik
`postgres`, geslo iz `.env`. Pazi: skrbnik sme vse, tudi zbrisati; za
branje je varnejsa vloga `turnirko_app`.

**Zamenjava gesla baze.** Spremenjen `.env` gesla v obstojecem zvezku ne
spremeni. Gesli vlog zamenjas v bazi, nato v `.env`, nato zazenes aplikacijo:

```bash
sudo docker compose exec baza psql -U postgres -c "ALTER ROLE turnirko_app PASSWORD 'novo-geslo'"
nano .env                                   # isto geslo v TURNIRKO_BAZA_APP_GESLO
sudo docker compose up -d app
```

(Geslo brez enojnih narekovajev; ustvari ga z `openssl rand -hex 24`.) Isto
velja za `turnirko_lastnik` in `TURNIRKO_BAZA_LASTNIK_GESLO`.

**Nova glavna razlicica PostgreSQL** (iz 18 v 19) NI posodobitev: zvezka
starejse glavne razlicice nova slika ne odpre. Takrat naredi kopijo, ustvari
novo bazo na novi sliki in kopijo obnovi (`skripte/obnovi-bazo.sh`). Manjse
razlicice (18.6 -> 18.7) zamenjas samo z imenom slike v `compose.yaml`.

### 5. Zagon

```bash
sudo docker compose up -d --build
sudo docker compose logs -f app
```

Prvi prevod traja nekaj minut (prenese Node in Maven odvisnosti). Ko v
dnevniku vidis `Started TurnirkoAplikacija`, odpri `https://tvoja-domena`.
Certifikat Caddy pridobi sam ob prvem obisku.

Ce se aplikacija ne zazene in v dnevniku pise, da manjka geslo, `.env` ni
bil prebran - preveri, da je v isti mapi kot `compose.yaml`.

### 6. Prva prijava

Prijavi se kot `admin` z geslom iz `.env`, **takoj ga zamenjaj v
aplikaciji**, nato ustvari racune organizatorjev. Geslo iz `.env` je
uporabljeno samo enkrat, ob prazni bazi.

### 6a. E-posta za potrditvene kode

Vsak nov racun potrdi svoj naslov s sestmestno kodo, ki jo dobi po e-posti
(enako "pozabljeno geslo" in soglasje starsa pri mlajsih od 15 let). Brez
nastavljene poste gredo kode samo v dnevnik aplikacije in se **nihce ne more
registrirati**. Potrebujes troje:

1. **Ponudnika transakcijske poste.** Brevo (brezplacen paket, 300 sporocil na
   dan, strezniki v EU) zadosca; podobno delujeta Postmark ali Resend. Po
   prijavi dobis podatke za SMTP: streznik (`smtp-relay.brevo.com`), vrata
   587, uporabnika in kljuc SMTP. Kljuc SMTP ni geslo za prijavo k ponudniku
   in ni geslo postnega predala.

   Posta aplikacije in tvoj predal (`info@...`) sta dve loceni stvari.
   Aplikacija NE posilja iz predala: ta ima pri gostitelju domene nizko
   omejitev odhodne poste (pri Neoservu privzeto 30 sporocil na uro, rocno do
   nekaj sto), njegovo geslo pa odpira tudi BRANJE poste - na strezniku bi
   torej lezal kljuc do tvojega nabiralnika.

2. **Zapise DNS pri gostitelju domene.** DNS je imenik domene: zapis A pove,
   na katerem strezniku je stran, spodnji trije pa, kdo sme posiljati posto v
   njenem imenu. Brez njih posta konca med nezeleno ali se sploh ne dostavi.
   - **SPF** - zapis TXT na domeni: seznam streznikov, ki smejo posiljati.
   - **DKIM** - zapis TXT ali CNAME z imenom, kot ga da ponudnik: podpis
     vsakega sporocila. To je edini del, ki tujcu prepreci, da bi se
     predstavljal s tvojo domeno.
   - **DMARC** - zapis TXT na `_dmarc.tvoja-domena.si`, za zacetek
     `v=DMARC1; p=none; rua=...`. `p=none` pomeni "pusti skozi, a mi
     porocaj"; z zavracanjem zacni sele, ko posta nekaj tednov tece.

   Tri pasti, izmerjene 22. 9. 2026 ob postavitvi turnirko-nt.si:

   - **Zapis SPF sme biti en sam in zapis DMARC en sam.** Gostitelj domene ju
     pogosto ustvari ze sam ob registraciji, zato obstojeci zapis UREDI in ne
     dodajaj novega. Dva zapisa DMARC po standardu pomenita, da DMARC-a ni -
     oba se prezreta. Zdruzen SPF za Neoserv in Brevo izgleda tako:
     `v=spf1 +a +mx include:_spf.mail-neoserv.si include:spf.brevo.com ~all`
   - **Brevo zapisa SPF sploh ne zahteva**, ker se zanasa na DKIM. Vseeno ga
     potrebujes za svoj predal - in ce obstaja, mora zajeti oba posiljatelja,
     sicer posta ponudnika pade na preverjanju SPF.
   - **Nova domena nekaj ur ne obstaja.** Register .si jo drzi v stanju
     `inactive`, dokler imenski strezniki ne odgovorijo. Do takrat svet,
     vkljucno s ponudnikom poste, na vsako vprasanje dobi "te domene ni" in
     gumb "preveri" javi neujemanje pri VSEH zapisih, ceprav so pravilni. Ne
     popravljaj zapisov, ampak preveri stanje: `whois turnirko-nt.si` mora
     kazati `status: ok`. Sele nato pritisni "preveri".

   Ko je domena overjena, so vsi posiljatelji z nje samodejno potrjeni;
   posameznega naslova (npr. `ne-odgovarjaj@`) ni treba posebej potrjevati s
   kodo. Ce ponudnik kodo vseeno zahteva, domena se ni overjena.

3. **Vrednosti v `.env`** (glej `.env.primer`): `TURNIRKO_POSTA_NACIN=smtp`,
   `TURNIRKO_POSTA_OD` (`Turnirko <ne-odgovarjaj@turnirko-nt.si>` - naslov
   mora biti na TVOJI domeni, sicer DKIM ne velja), `TURNIRKO_POSTA_ODGOVOR`
   (`info@turnirko-nt.si`, kamor pade odgovor uporabnika),
   `TURNIRKO_SMTP_STREZNIK`, `TURNIRKO_SMTP_VRATA`, `TURNIRKO_SMTP_UPORABNIK`,
   `TURNIRKO_SMTP_GESLO`. Nato `docker compose up -d`.

   Admin dobi e-posto tudi, ko se igralec registrira in se njegov racun ne
   poveze sam z igralcem, in ko se registrira organizator (kdo, kateri paket,
   zakaj caka). Gre na
   `TURNIRKO_POSTA_ADMIN` (privzeto `info@turnirko-nt.si`; prazna vrednost
   obvestila izklopi), od `TURNIRKO_POSTA_OD`. Ker je prejemnik na isti domeni
   kot posiljatelj, ob prvem sporocilu preveri, da ni pristalo med nezeleno.

**Preizkus se pred streznikom.** Posiljanja ni treba cakati do objave: v mapi
`zaledje/config/` ustvari `application-posta.properties` z istimi nastavitvami
(mapa je v `.gitignore`, ker vsebuje kljuc SMTP) in zazeni s profilom:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=posta
```

Ime datoteke MORA biti vezano na profil. Mapo `./config` Spring Bootovi
privzetki berejo ob vsakem zagonu in ima prednost pred `application-test.properties`
iz razreda - navadna `application.properties` bi zato nastavitve podtaknila
tudi testom in ti bi posiljali namesto brali iz pomnilnika (20 padlih testov,
22. 9. 2026). Profilna datoteka se brez `-Dspring-boot.run.profiles=posta`
sploh ne prebere.

Preizkus: ustvari racun s svojim naslovom in preveri, da koda pride v nekaj
sekundah - najprej na Gmail, nato na naslov pri slovenskem ponudniku
(siol.net, t-2), ki so pri filtriranju strozji. V Gmailu odpri sporocilo,
izberi "Prikazi izvirnik" in preveri, da pri SPF, DKIM in DMARC pise PASS; to
je edini zanesljiv dokaz, da so zapisi DNS pravilni. Ce kode ni, poglej
dnevnik (`docker compose logs -f app`): vrstica "ni bilo mogoce poslati" pove,
kaj je zavrnil streznik poste; "Posta ni nastavljena" pomeni, da `.env` ni bil
prebran.

### 7. Varnostne kopije

Kopije delata dve skripti: `skripte/varnostna-kopija.sh` (naredi kopijo) in
`skripte/vaja-obnove.sh` (preveri, da se kopija res da obnoviti). Kopija je
datoteka `.dump`, ki jo naredi ukaz **pg_dump**: bere bazo kot ena dosledna
slika tocno ob zacetku, tudi ce vmes nekdo vnasa rezultate, in je stisnjena
(~20 MB). Obnovi jo ukaz `pg_restore`.

Tri vrste kopij, vsaka v svoji podmapi mape `kopije/`:

| Vrsta | Kdaj | Koliko casa se hrani |
|---|---|---|
| `ura` | vsako uro med 7. in 23. uro | 48 ur |
| `noc` | vsako noc ob 3.20 | 30 dni |
| `mesec` | prvi dan v mesecu (kopija nocne) | 12 mesecev |

Najslabsi primer ob izgubi streznika je tako izgubljena ura vnosov (ti so
tudi na papirnatih listkih). Namesti `rclone` (odnasanje, spodaj) in dodaj v
crontab (`crontab -e`):

```
0 7-23 * * * /srv/turnirko/skripte/varnostna-kopija.sh ura >> /srv/turnirko/dnevniki/kopije.log 2>&1
20 3 * * *   /srv/turnirko/skripte/varnostna-kopija.sh noc >> /srv/turnirko/dnevniki/kopije.log 2>&1
30 4 2 * *   /srv/turnirko/skripte/vaja-obnove.sh >> /srv/turnirko/dnevniki/vaja-obnove.log 2>&1
```

Vse tri vrstice so tudi v datoteki `skripte/baza/turnirko-kopije.cron`, ki se na
strezniku namesti z `sudo install -m 644 skripte/baza/turnirko-kopije.cron /etc/cron.d/turnirko-kopije`.
Streznik uporablja cas UTC (ura 5-22 UTC pokrije 7-23 po slovenskem casu); skripte
so bash, zato se kliceju neposredno in ne z `sh`.

Zadnja vrstica je **mesecna vaja obnove**: skripta zadnjo nocno kopijo
obnovi v zacasen vsebnik (produkcijska baza ostane nedotaknjena), prestej
vrstice vsake tabele in jih primerja s stetjem, zapisanim ob kopiji. Kopija,
ki je nihce ni obnovil, ni kopija. Uspeh ali napako najdes v
`dnevniki/vaja-obnove.log`; prvic jo pozeni rocno.

**Odnasanje s streznika** je obvezno: kopije na istem strezniku propadejo
skupaj z njim. Kopija vsebuje osebne podatke (datume rojstva), zato naj bo
shramba v **EU** in kopije **sifrirane**. Postopek z `rclone`:

```bash
sudo apt install -y rclone
rclone config
```

V `rclone config` ustvari (1) povezavo na shrambo S3 (Hetzner Object Storage,
Backblaze B2 EU ... ~1 EUR/mesec; ponudnik ti da kljuc in naslov), nato (2)
povezavo tipa **crypt**, ki kaze na prvo in sifrira imena datotek in vsebino
z geslom, ki ga shranis ZUNAJ streznika (v upravitelj gesel). Ime druge
povezave in mapa gresta v `.env`:

```
TURNIRKO_ODNASANJE=oblak-sifrirano:turnirko-kopije
```

**Hitra pot (Backblaze B2, 2. 11. 2026):** v spletni konzoli B2 ustvari zasebno vedro in
aplikacijski kljuc, omejen na to vedro (Read and Write), nato na strezniku
`ssh -t root@streznik "cd /srv/turnirko && skripte/baza/nastavi-odnasanje.sh <ime-vedra>"`.
Skripta vpraseje keyID in applicationKey (skrito), sama ustvari sifrirno plast (imena in
vsebina, gesli samo v rclone.conf), preizkusi zapis in branje, pokaze nerazumljiva imena v
vedru in vpise `TURNIRKO_ODNASANJE=kopije:turnirko` v `.env`. Po koncu **skopiraj celo
`/root/.config/rclone/rclone.conf` v upravitelj gesel**: brez nje kopij v oblaku ni mogoce prebrati.
ApplicationKey ima 31 znakov in se zacne s `K003`; pokaze se samo ob nastanku kljuca.

Preizkusi: `./skripte/varnostna-kopija.sh rocno`. V dnevniku mora pisati
`odneseno: ...`; vsak dan brez tega je opozorilo `odnasanje ni nastavljeno`.

**Obnova v sili.** Najprej poglej, kaj je kopija (`ls -lt kopije/noc | head`),
nato:

```bash
./skripte/obnovi-bazo.sh kopije/noc/turnirko-20261101-032000.dump
```

Skripta vpraša za potrditev (obstojeca baza se zbrise), pred tem naredi se
kopijo trenutnega stanja v `kopije/pred-obnovo/`. Po obnovi izgubis vse, kar je
bilo vneseno po kopiji. Ce je streznik izgubljen, ga postavi znova po
razdelkih 2-5, kopijo prenesi iz shrambe (`rclone copy`) in obnovi z isto
skripto.

Statistika obiska (Umami, razdelek 8) ima svojo bazo in NI v teh kopijah.

### 8. Statistika obiska (Umami)

Umami steje oglede: koliko ljudi pride, katere strani odprejo, koliko casa
ostanejo, s telefona ali racunalnika, od kod pridejo (Facebook, Google,
ntzs.si ...) in koliko jih je na strani prav zdaj. Tece na tem strezniku
kot dva vsebnika (Umami in njegova baza PostgreSQL, glej `compose.yaml`),
pregled je na `https://statistika.turnirko-nt.si`.

Zakaj ne Google Analytics: ta uporablja piskotke, zato bi po zakonu rabil
pasico za soglasje - gost na telefonu bi jo pogosto zavrnil in stevilke bi
bile luknjaste. Umami piskotkov ne uporablja in naslovov IP ne hrani, zato
pasica ni potrebna, podatki pa ne zapustijo streznika.

1. **Zapis DNS.** Pri gostitelju domene dodaj zapis **A** z imenom
   `statistika`, ki kaze na isti IP kot `turnirko-nt.si`. Ko ga doma
   `nslookup statistika.turnirko-nt.si` vrne, nadaljuj.

2. **Zagon na strezniku:**

   ```bash
   cd /srv/turnirko && git pull && sudo sh ./skripte/vklopi-statistiko.sh
   ```

   Skripta preveri DNS in prosti pomnilnik, v `.env` sama doda tri nakljucne
   skrivnosti, zazene Umami in Caddy ustvari znova (stran je nekaj sekund
   nedosegljiva). Aplikacije se ne dotakne.

3. **Takoj zamenjaj geslo.** Odpri `https://statistika.turnirko-nt.si`,
   prijavi se z `admin` / `umami` in v profilu nastavi novo geslo. Dokler je
   privzeto, se lahko prijavi kdorkoli.

4. **Dodaj stran** (Settings -> Websites -> Add website): ime `Turnirko`,
   domena `turnirko-nt.si`. Umami pokaze **Website ID**.

5. **Vkljuci skript v vmesnik.** V `vmesnik/index.html`, v `<head>`:

   ```html
   <script defer src="https://statistika.turnirko-nt.si/obisk.js"
           data-website-id="<Website ID iz 4. koraka>"
           data-domains="turnirko-nt.si,www.turnirko-nt.si"></script>
   ```

   `data-domains` poskrbi, da se ogledi stejejo samo na pravi strani - razvoj
   na `localhost` in namizna razlicica v dvorani ne posljeta nicesar. Nato
   commit, push in [posodobitev](#posodobitev).

**Svojih ogledov ne stej.** Odpri `https://turnirko-nt.si/?statistika=izklop`
- stran potrdi z oknom. Velja za ta brskalnik na tej napravi, zato ponovi na
telefonu in povsod, kjer pogosto preverjas stran (nazaj:
`?statistika=vklop`). Pri majhnem obisku tvoji ogledi sicer opazno napihnejo
stevilke. Na racunalniku gre tudi brez posodobitve: konzola (F12 -> Console)
in `localStorage.setItem('umami.disabled', '1')` - izpis `undefined` je
pravilen.

**Posodobitev Umamija** ni samodejna (v `compose.yaml` je tocna razlicica).
Ko hoces novejso, preberi opombe ob izdaji, zamenjaj oznako slike in pozeni
`sudo docker compose up -d umami`.

Statistika ni v varnostni kopiji baze Turnirka (ta kopira samo bazo `turnirko`).
Ce jo izgubis, izgubis samo zgodovino obiska; turnirjev to ne prizadene.

## Posodobitev

```bash
cd /srv/turnirko && git pull --autostash && sudo docker compose up -d --build
```

`--autostash` je obvezen, ker ima `Caddyfile` na strezniku lokalen blok za projekt
Pikado. Koda mora biti pred tem pushana na `main` (na racunalniku, v mapi `turnirko`).
**Nikoli `docker compose down -v` ali `docker volume rm`**: zvezek `turnirko_turnirko_baza`
je baza.

Baza je v svojem vsebniku in zvezku (razdelek 4), zato posodobitev aplikacije ne
izgubi podatkov. Migracije Flyway se izvedejo ob zagonu same (z vlogo
`turnirko_lastnik`). Med posodobitvijo Caddy pokaze stran »Turnirko je v
vzdrzevanju«. Pred vecjo posodobitvijo pozeni varnostno kopijo rocno:
`./skripte/varnostna-kopija.sh rocno`.

### Ukazi nad bazo: uvoz, popravek, prenos

Trije ukazi aplikacije delajo nad bazo mimo spletnega vmesnika: `uvoz`
(zgodovina NTZS v prazno bazo), `popravek` (popravek uvozene zgodovine) in
`prenos` (enkratna selitev iz SQLite, `docs/PREKLOP.md`). Tecejo kot
samostojen zagon aplikacije in morajo imeti pravice **lastnika** baze
(`turnirko_lastnik`), ne aplikacije (`turnirko_app`).

`uvoz` in `popravek` potrebujeta mape, ki jih na strezniku ni (pretvorjena
stara stran, posnetki Stupe), zato tecejo **na tvojem racunalniku nad kopijo
baze** - tako kot prej nad kopijo datoteke SQLite:

1. Na strezniku ustavi aplikacijo, da se baza vmes ne spremeni, in naredi
   kopijo: `sudo docker compose stop app && ./skripte/varnostna-kopija.sh rocno`.
2. Kopijo prenesi domov (`scp`), v lokalni PostgreSQL 18 ustvari bazo
   `turnirko` (`CREATE DATABASE turnirko ENCODING 'UTF8' LOCALE_PROVIDER
   builtin BUILTIN_LOCALE 'C.UTF-8' TEMPLATE template0;`) in jo obnovi:
   `pg_restore -d turnirko --no-owner <kopija.dump>`.
3. V mapi `zaledje` pozeni ukaz nad lokalno bazo, na primer popravek:
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=popravek "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:postgresql://localhost:5432/turnirko --spring.datasource.username=postgres"`.
4. Popravljeno bazo shrani (`pg_dump -Fc turnirko > popravljena.dump`), jo
   prenesi na streznik in obnovi s `./skripte/obnovi-bazo.sh popravljena.dump`.

Aplikacija je med tem nedosegljiva, zato to naredi takrat, ko se ne igra.

SQL skripte za uvoze (`psql`) gredo v bazo na strezniku z
`sudo docker compose exec -T baza psql -U postgres -d turnirko -v ON_ERROR_STOP=1 --single-transaction < uvoz.sql`.
Aplikacije ni treba ustavljati: skripta je ena transakcija in gledalci do
potrditve vidijo staro stanje. Po uvozu tekem je treba rating preracunati
(stran Rating). V skriptah SQLite zamenjaj `INSERT OR IGNORE` z `ON CONFLICT
DO NOTHING` in `last_insert_rowid()` z `RETURNING id`.

## Lokalni prevod brez Dockerja

```bash
cd zaledje && ./mvnw -Psplet package
java -jar target/turnirko-zaledje-0.1.0.jar --spring.profiles.active=splet
```

Profil `splet` zgradi vmesnik in ga vlozi v isti `.jar`. Brez njega prevod
naredi samo API (za razvoj, kjer vmesnik tece na Vite, vrata 5173).
