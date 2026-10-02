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
| Varnostne kopije | skripta pripravljena | `skripte/varnostna-kopija.sh` |

Kar **ni** urejeno in ostane pred prvim tujim pilotom: starsevska soglasja
za mladoletnike, pogodba o obdelavi podatkov s klubom in dnevnik
sprememb (kdo je kaj popravil). Glej `RAZVOJNI-NACRT.md`, razdelek 7.

## Kdaj objava sploh ni potrebna

Za prve turnirje zadostuje namizna razlicica na prenosniku v dvorani:

```bash
java -jar turnirko-zaledje-0.1.0.jar --spring.profiles.active=namizni
```

Brskalnik se odpre sam, gledalci na istem Wi-Fi-ju stran odprejo prek
naslova tvojega racunalnika. Nic ni na spletu, osebni podatki ne zapustijo
prenosnika. Splet je smiseln sele, ko naj rezultate spremljajo ljudje, ki
niso v dvorani.

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
sudo apt update && sudo apt install -y docker.io docker-compose-v2 sqlite3 git
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

### 4. Mapa za bazo

```bash
mkdir -p podatki dnevniki kopije
sudo chown -R 10001:10001 podatki
```

Stevilka 10001 je uporabnik, pod katerim tece aplikacija v vsebniku (glej
`Dockerfile`); brez tega v bazo ne bo mogla pisati.

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

```bash
chmod +x skripte/varnostna-kopija.sh
crontab -e
```

Dodaj:

```
20 3 * * * /srv/turnirko/skripte/varnostna-kopija.sh >> /srv/turnirko/dnevniki/kopije.log 2>&1
```

Nato **enkrat rocno pozeni skripto in preizkusi obnovitev** na svojem
racunalniku. Kopija, ki je nisi nikoli odprl, ni kopija. In uredi
odnasanje kopij s streznika (glej komentar na koncu skripte) - kopije na
istem strezniku propadejo skupaj z njim.

## Posodobitev

```bash
cd /srv/turnirko && git pull && sudo docker compose up -d --build
```

Baza je izven vsebnika (mapa `podatki/`), zato posodobitev ne izgubi
podatkov. Migracije Flyway se izvedejo ob zagonu same. Pred vecjo
posodobitvijo vseeno pozeni varnostno kopijo rocno.

### Enkratni popravek zgodovine (oktober 2026)

Ta posodobitev prinese tudi popravek ze uvozene zgodovine NTZS
(`PopravekZgodovineUkaz`, glej `../uvoz-stara-ntzs/README.md`). Popravek
tece na TVOJEM racunalniku nad kopijo baze (potrebuje pretvorbo stare strani,
ki je na strezniku ni, in dostop do Stupe), zato je postopek tak:

0. Spremembe morajo biti v repozitoriju (commit in push), da jih streznik v
   4. koraku dobi z `git pull`. Popravek pozeni z ISTO kodo, kot jo objavis:
   migracije V38-V40 se izvedejo ze nad kopijo.

1. Na strezniku ustavi aplikacijo, da se baza vmes ne spremeni, in naredi kopijo:

   ```bash
   cd /srv/turnirko && sudo docker compose stop app
   ./skripte/varnostna-kopija.sh
   ls -t kopije | head -1
   ```

2. Na svojem racunalniku kopijo prenesi in razsiri (skripta jo stisne v
   `.db.gz`; ime datoteke iz prejsnjega koraka):

   ```bash
   scp <uporabnik>@<streznik>:/srv/turnirko/kopije/turnirko-<cas>.db.gz .
   gunzip -c turnirko-<cas>.db.gz > produkcija.db
   ```

3. V mapi `zaledje` pozeni popravek nad to kopijo (traja ~30 minut, na koncu
   se ustavi sam in izpise povzetek; podrobno porocilo je v
   `../../popravek-porocilo.csv`, torej v mapi nad repozitorijem):

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=popravek "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:sqlite:<pot>/produkcija.db --server.port=0"
   ```

   Preden bazo nalozis nazaj, preveri: povzetek nima vrstic z `NAPAKA` ali
   `ZAVRNJENIH`, ob `produkcija.db` pa ni datoteke `produkcija.db-wal` (ce je,
   se zagon ni koncal pravilno - ne nalagaj, pozeni znova nad svezo kopijo).

4. Popravljeno bazo nalozi nazaj, staro shrani, nato posodobi in zazeni:

   ```bash
   scp produkcija.db <uporabnik>@<streznik>:/tmp/turnirko-popravljena.db
   # na strezniku:
   cd /srv/turnirko
   sudo mv podatki/turnirko.db podatki/turnirko.db.pred-popravkom
   sudo rm -f podatki/turnirko.db-wal podatki/turnirko.db-shm
   sudo mv /tmp/turnirko-popravljena.db podatki/turnirko.db
   sudo chown 10001:10001 podatki/turnirko.db
   git pull && sudo docker compose up -d --build
   ```

Ce gre kaj narobe, vrni `podatki/turnirko.db.pred-popravkom` na mesto in
zazeni prejsnjo razlicico. Aplikacija je med postopkom nedosegljiva (~45
minut), zato ga naredi takrat, ko se ne igra.

## Lokalni prevod brez Dockerja

```bash
cd zaledje && ./mvnw -Psplet package
java -jar target/turnirko-zaledje-0.1.0.jar --spring.profiles.active=splet
```

Profil `splet` zgradi vmesnik in ga vlozi v isti `.jar`. Brez njega prevod
naredi samo API (za razvoj, kjer vmesnik tece na Vite, vrata 5173).
