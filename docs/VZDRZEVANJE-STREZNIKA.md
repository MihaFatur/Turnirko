# Vzdrževanje strežnika

Stanje in posegi po pregledu kakovosti (12. 11. 2026). Strežnik: `root@46.225.187.162`,
Ubuntu 26.04, 2 jedri, 3,8 GB RAM, disk 38 GB. Na njem tečejo produkcija
(`/srv/turnirko`), testno okolje (`/srv/turnirko-test`), statistika Umami in Pikado.

Posege iz tega dokumenta izvedeš **sam** (prijava z `ssh root@46.225.187.162`).
Spreminjajo nastavitve sistema in varnosti, zato jih Claude ne poganja; lahko pa
prebere izpis in pomaga, če se kaj zatakne.

## Kaj je pregled izmeril

| Kaj | Stanje 12. 11. 2026 | Zakaj je to težava |
|---|---|---|
| Disk | 88 % poln, 4,5 GB prostih | Ko disk zmanjka, se ustavi baza; skripta za kopije se ustavi že pod 1 GB. |
| Docker | 19 GB predpomnilnika gradnje, 19 GB starih slik | Vsaka objava pusti stare sloje; nihče jih ne čisti. |
| Posodobitve | 48 paketov čaka, ponovni zagon zahtevan, 9 tednov delovanja | Popravki jedra začnejo veljati šele po ponovnem zagonu. |
| SSH | 7.364 neuspelih prijav na dan, `fail2ban` ne teče, prijava z geslom dovoljena | Root se sicer prijavi samo s ključem, a vsak poskus troši vire in polni dnevnik. |
| Opozarjanje | ga ni | Če stran pade ali kopija spodleti, tega nihče ne izve. |

Kopije baze delujejo: urne in nočne nastajajo in se šifrirane odnašajo v oblak.

## 1. Sprosti disk (5 minut, brez izpada)

```bash
bash /srv/turnirko/skripte/vzdrzevanje/sprosti-disk.sh
```

Skripta pobriše predpomnilnik gradnje in slike, ki jih ne uporablja noben vsebnik in
so starejše od 7 dni. Baze se ne dotakne (zvezkov ne briše). Pričakuj okoli 20 GB
sproščenega prostora. Prva gradnja po čiščenju traja dlje.

Da se disk ne napolni znova, dodaj mesečno čiščenje (1. v mesecu ob 5.00 po uri strežnika):

```bash
echo '0 5 1 * * root /srv/turnirko/skripte/vzdrzevanje/sprosti-disk.sh >> /srv/turnirko/dnevniki/disk.log 2>&1' > /etc/cron.d/turnirko-disk
```

## 2. Posodobitve in ponovni zagon (10 minut, 1–2 minuti izpada)

Naredi ponoči ali zjutraj, ko nihče ne vnaša rezultatov. Vsebniki imajo
`restart: unless-stopped`, zato se po ponovnem zagonu zaženejo sami.

```bash
bash /srv/turnirko/skripte/varnostna-kopija.sh rocno
```

```bash
apt update && apt upgrade
```

```bash
reboot
```

Po dveh minutah se prijavi znova in preveri, da so vsi vsebniki zdravi:

```bash
cd /srv/turnirko && docker compose ps
```

`app` in `baza` morata biti `healthy`. Nato odpri https://turnirko-nt.si.

## 3. Utrdi SSH (10 minut, brez izpada)

Dva koraka: `fail2ban` naslov po nekaj neuspelih poskusih začasno zapre, izklop prijave
z geslom pa poskuse z gesli naredi brezpredmetne.

```bash
apt install fail2ban
```

```bash
systemctl enable --now fail2ban
```

Privzeta nastavitev že varuje SSH (5 neuspelih poskusov, zapora 10 minut). Stanje:

```bash
fail2ban-client status sshd
```

Izklop prijave z geslom. **Pred tem preveri, da se s ključem res prijaviš**, in med
spremembo pusti to sejo odprto:

```bash
echo 'PasswordAuthentication no' > /etc/ssh/sshd_config.d/10-brez-gesla.conf
```

```bash
sshd -t && systemctl reload ssh
```

Zdaj v **novem** oknu terminala preveri, da prijava še deluje (`ssh root@46.225.187.162`).
Šele ko deluje, zapri staro sejo. Če ne deluje, v stari seji izbriši datoteko
`/etc/ssh/sshd_config.d/10-brez-gesla.conf` in ponovi `systemctl reload ssh`.

## 4. Opozorilo ob izpadu in neuspeli kopiji (20 minut, brezplačno)

Potrebuješ dve stvari, obe pri zunanji storitvi (na istem strežniku nadzor nima smisla,
ker pade skupaj z njim).

**Ali stran deluje.** Pri storitvi za nadzor dosegljivosti (na primer UptimeRobot ali
Better Stack, oba imata brezplačen paket) ustvari preverbo za naslov
`https://turnirko-nt.si/api/v1/klubi` na 5 minut, z obvestilom na tvoj e-naslov. To je
isti naslov, ki ga za zdravje uporablja Docker.

**Ali kopije nastajajo.** Pri storitvi tipa »dead man's switch« (na primer
healthchecks.io) ustvari preverbo s periodo 1 dan in toleranco 2 uri. Dobiš naslov
oblike `https://hc-ping.com/<id>`. Vpiši ga v `/srv/turnirko/.env`:

```bash
nano /srv/turnirko/.env
```

in dodaj vrstico `TURNIRKO_PING_NOC=https://hc-ping.com/<id>`. Skripta za kopije ta
naslov pokliče po vsaki uspeli nočni kopiji; če klica en dan ni, dobiš e-pošto.
Urne kopije imajo svojo spremenljivko `TURNIRKO_PING_URA` (preverba z urnikom
`0 5-22 * * *` po UTC).

## 5. Kaj še ostane (zahteva spremembo `compose.yaml`)

Ti dve spremembi ob naslednji objavi znova ustvarita vse vsebnike, tudi bazo
(nekaj sekund izpada), zato gresta skupaj v eno načrtovano objavo:

- omejitev velikosti dnevnikov vsebnikov (`logging: max-size`), zdaj brez meje;
- omejitev pomnilnika produkcijskih vsebnikov, da ob uhajanju pomnilnika ne pade cel strežnik.

## Pot nazaj po neuspeli objavi

`skripte/test/v-produkcijo.sh` od novembra 2026 pred gradnjo označi tekočo sliko kot
`turnirko-app:prejsnja`, po zagonu počaka, da je aplikacija zdrava, in ob neuspehu konča
z napako. Vrnitev na prejšnjo sliko:

```bash
bash /srv/turnirko/skripte/test/vrni-prejsnjo.sh
```

Deluje samo, če objava ni prinesla migracije baze (stara koda nove sheme ne pozna);
takrat je pot nazaj nova objava s popravkom ali obnova kopije (`docs/OBJAVA.md`,
»Obnova v sili«).
