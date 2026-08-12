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

V `Caddyfile` zamenjaj `turnirko.si` s svojo domeno in vpisi svojo e-posto.

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

## Lokalni prevod brez Dockerja

```bash
cd zaledje && ./mvnw -Psplet package
java -jar target/turnirko-zaledje-0.1.0.jar --spring.profiles.active=splet
```

Profil `splet` zgradi vmesnik in ga vlozi v isti `.jar`. Brez njega prevod
naredi samo API (za razvoj, kjer vmesnik tece na Vite, vrata 5173).
