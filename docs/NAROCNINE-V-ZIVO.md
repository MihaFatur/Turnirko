# Naročnine v živo: od testnega do pravega zaračunavanja

Kontrolni seznam za preklop plačil (Premium igralca, paketi organizatorja) iz
Stripovega **testnega** načina v **živi**. Stanje na dan 12. 10. 2026. Koraki so
razvrščeni tako, da se nobeno pravo plačilo ne zgodi, preden je urejen račun.

Vir resnice za cene in omejitve ostaja koda (`CenikStoritev`, `NarocninaStoritev`),
za delovanje plačil `PlacilaStoritev`. Ta dokument ne podvaja številk.

## 1. Odločitve

| Vprašanje | Odločitev | Stanje |
|---|---|---|
| Prodajalec naročnin | **Jaka Golavšek s.p.** (do 12. 10. je veljal NTK Žalec) | potrjeno 12. 10. 2026 |
| Program za račune | **Moj Račun** + dodatek »Integracija Stripe« | potrjeno |
| Stripe račun | **nov, na s.p.** (imetnik s.p., TRR s.p.); lastnik te kode je povabljen kot skrbnik | potrjeno |
| Zavezanec za DDV? | ni znano | **odprto – računovodkinja** |
| Dogovor o 4 % prometa za davke (med lastnikom Turnirka in imetnikom s.p.) | ni znano, kako je urejen | **odprto – računovodkinja** (glej razdelek 6) |
| Dostop med Stripovimi ponovnimi poskusi plačila | zdaj: izklop takoj ob prvem neuspehu | **odprto – odločitev lastnika** |

## 2. Kako teče plačilo in račun

```
kupec ── kartica ──► Stripe Checkout ──► Stripe ──► izplačilo na TRR s.p.
                         │                  │
                         │                  ├─► webhook ► Turnirko    (račun/naročnina, dostop)
                         │                  └─► webhook ► Moj Račun   (račun + FURS + e-pošta kupcu)
                         └─ vrnitev na turnirko-nt.si
```

- **Turnirko** skrbi samo za dostop (ali ima uporabnik veljaven paket). O denarju ne
  odloča, številke kartice ne vidi.
- **Stripe** pobere plačilo in ga ponavlja ob vsaki obnovi.
- **Moj Račun** iz vsakega Stripovega plačila naredi račun, ga davčno potrdi pri FURS
  (oznaki ZOI in EOR), PDF pošlje kupcu in ga hrani. Ob vračilu sam izda dobropis.
- **Računovodkinja** dobi v Moj Računu dostop z vlogo »Računovodja« (samo branje in izvozi).

## 3. Zakaj je račun obvezen in zakaj ne zadošča Stripov

- **2. člen ZDavPR, 4. točka**: »plačilo z gotovino« so tudi »plačila s plačilno ali
  kreditno kartico«. Račun za gotovinski posel je treba davčno potrditi
  ([besedilo](https://zakonodaja.com/zakon/zdavpr/2-clen-pomen-izrazov), Ur. l. RS 57/15).
- **FURS, odgovor št. 307** (9. 2. 2016, spletna prodaja prek Braintree): kartica je gotovina
  tudi prek plačilne platforme, ker prodajalec prejme zbirni priliv
  ([zbirka odgovorov](https://www.racunovodja.com/clanki_test.asp?clanek=9117)). Stripe deluje
  enako. Starejši splošni odgovor št. 20 (16. 7. 2015) pravi za plačila prek ponudnika
  plačilnih storitev drugače; previdno velja, da potrjevanje je obvezno. **Končno potrdi
  računovodkinja.**
- Obveznost velja za **vsak račun, tudi vsako mesečno obnovo**, in za dobropise ob vračilih.
- Stripov račun ali potrdilo ni davčno potrjen račun.

## 4. Postopek po fazah

### Faza 1 – FURS in Moj Račun (imetnik s.p. ali računovodkinja)

Moj Račun je program za izdajanje računov; osnovni del je brezplačen in vključuje
davčno potrjevanje. Dodatek za Stripe stane 4,99 € na mesec (z DDV), odpoved kadar koli
([cenik](https://moj-racun.si/cenik)).

- [ ] **Potrdilo FURS** (namensko digitalno potrdilo za davčno potrjevanje): v eDavkih →
      Dokumenti → obrazec **DPR-PridobitevDP**. Po desetih minutah se v osebnem predalu
      pokažeta referenčna številka in geslo; v eDavkih potrdilo prevzameš (datoteka `.p12`)
      in si zanj izbereš geslo. Brezplačno, velja 5 let. **Osebnega potrdila (SIGEN-CA) ne
      nalagaj** – je samo za prijavo v eDavke ([navodilo](https://moj-racun.si/dokumentacija/kako-pridobim-certifikat-za-davcno-potrjevanje)).
- [ ] **Moj Račun**: registracija (brez kartice), zavihek *Profil*: ime in priimek, podjetje,
      naslov, davčna številka, matična številka, pravna oblika, IBAN, **DDV status**.
- [ ] *Nastavitve → Davčno potrjevanje*: naloži `.p12` in geslo; vpiši **poslovni prostor**
      (vrsta, oznaka do 4 znakov) in **elektronsko napravo** (oznaka do 4 znakov). Prijavo pri
      FURS odda Moj Račun sam. **Vrsto prostora za spletno prodajo določi računovodkinja**;
      zaprtje prostora je trajno, oznake ni mogoče znova uporabiti.
- [ ] **Interni akt** o izdajanju računov: v istem razdelku »Prenesi interni akt«, sprejme ga
      s.p., računovodkinja ga pregleda; ne pošilja se na FURS.
- [ ] *Nastavitve → Dodatni dostopi → Povabi osebo*: vloga **Računovodja** (prvi dodatni dostop
      je brezplačen).
- [ ] *Nastavitve računov*: jezik računa slovenščina; plačilne QR kode in opomnike za plačilo
      izklopi (plačilo je s kartico).
- [ ] Dodatek **Integracija Stripe** se naroči in poveže **šele v fazi 5**.

### Faza 2 – Stripe račun za s.p. (imetnik s.p.)

Imena menijev se v Stripe spreminjajo; iščite po pomenu.

- [ ] Nov Stripe račun: država **Slovenija**, vrsta **samostojni podjetnik**, podatki kot v
      poslovnem registru, TRR s.p., preverba identitete imetnika. Dvostopenjska prijava (2FA).
- [ ] Lastnika kode povabi kot **skrbnika** (Team members), da lahko upravlja ključe in webhook.
- [ ] *Public details*: javno ime, **naslov in e-pošta podpore**, spletna stran
      `https://turnirko-nt.si`, **povezava na pogoje `https://turnirko-nt.si/pogoji`**
      (brez nje Stripe ne ustvari seje s soglasjem – to je v kodi zahtevano), opis na izpisku
      kartice (npr. `TURNIRKO`).
- [ ] *Payment methods*: samo kartice (z Apple Pay in Google Pay). Koda to zahteva tudi sama
      (`osnovaSeje`); drugi načini bi terjali posebno presojo glede davčnega potrjevanja.
- [ ] *E-pošte kupcem*: **izklopi** potrdila o uspešnih plačilih in pošiljanje Stripovih
      računov (kupec naj dobi en dokument, davčno potrjen račun iz Moj Računa). **Pusti**
      e-pošte ob neuspelem plačilu in opomnik pred letno obnovo.
- [ ] *Ponovni poskusi (Smart Retries)*: nastavi izrecno in odloči, kaj se zgodi po zadnjem
      neuspehu (priporočeno: naročnino prekliče). Odločitev o dostopu med poskusi glej razdelek 1.
- [ ] *Customer portal*: v **živem** načinu ga je treba aktivirati posebej (testna nastavitev
      ne velja). Vklopi: posodobitev kartice; po želji zgodovina računov. Izklopi: zamenjavo
      paketa (ta gre prek aplikacije). Vpiši povezavi na pogoje in zasebnost.
- [ ] **Webhook** (živi način): `https://turnirko-nt.si/api/v1/placila/webhook`, dogodki:
      `checkout.session.completed`, `customer.subscription.updated`,
      `customer.subscription.deleted`, `invoice.payment_failed`. **Različica API-ja naj bo
      `2025-07-30.basil`** (enaka kot v knjižnici `stripe-java 29.4.0`): koda bere obdobje
      naročnine iz oblike te različice. Skrivnost podpisa (`whsec_…`) je za živi in testni način
      različna.
- [ ] **Ključ**: priporočeno omejen ključ (*restricted key*), a ga je treba najprej preizkusiti
      v testnem načinu (Checkout, naročnine, urniki, izdelki, računi, portal); če kak klic
      vrne napako zaradi pravic, jih dodaj. Če to ni izvedljivo, uporabi navaden tajni ključ.
      Ključa nikoli ne daj v git ali v klepet.

### Faza 3 – koda in besedila (opravljeno / odprto)

Opravljeno 12. 10. 2026 (v delovnem drevesu, še ni commitano):

- Checkout: slovenski jezik, samo kartica, obvezno soglasje s pogoji, besedilo pred potrditvijo
  (naročilo z obveznostjo plačila, obnova, preklic, račun po e-pošti), organizator vpiše naslov
  in davčno številko (`PlacilaStoritev.osnovaSeje`).
- Življenjski cikel: stanje naročnine se izpelje iz Stripovega statusa (`past_due`, `unpaid` →
  ZAPADLA; `active` → AKTIVNA; `canceled` → konec). Prej je vrstni red dveh Stripovih dogodkov
  ob neuspeli obnovi odločal, ali neplačnik obdrži dostop.
- Neuspelo doplačilo ob nadgradnji organizatorja naročnine ne ugasne.
- Zavora: druge naročnine ni mogoče kupiti ob živi prvi (preprečuje dvojno zaračunavanje).
- Stran Naročnina ima stanje »Plačilo ni uspelo« (igralec in organizator).
- Test `UpravljanjeNarocnineStoritevTest` je imel trdo vpisan datum, ki je potekel 12. 10. ob 10.00.

Odprto:

- [ ] `vmesnik/src/pomozno/pravno.ts`: podatki prodajalca (ime kot v poslovnem registru, naslov,
      matična, davčna, e-pošta). **Brez njih stran nima podatkov o ponudniku.**
- [ ] `PogojiStran.tsx`: prodajalec, DDV, račun po e-pošti, Moj Račun kot obdelovalec, odstop
      od pogodbe, rok hrambe računov. Besedilo naj pregleda pravnik (ZVPot-1, ZVOP-2).
- [ ] **Čakajoča registracija**: geslo (zgoščeno), datum rojstva in e-pošta skrbnika zdaj
      potujejo v Stripu kot metapodatki seje; Moj Račun bere Stripove dogodke. V Stripu naj
      ostane samo naključen žeton, podatki pa v naši bazi.
- [ ] Gumb za odstop od pogodbe (direktiva 2023/2673, od 19. 6. 2026): slovenska novela ZVPot-1
      je v pripravi; ob uveljavitvi dodati.

### Faza 4 – preizkus v testnem načinu (z novimi testnimi ključi s.p.)

Lokalno: `stripe listen --events checkout.session.completed,customer.subscription.updated,customer.subscription.deleted,invoice.payment_failed --forward-to localhost:8080/api/v1/placila/webhook`
in zaledje z `TURNIRKO_STRIPE_TAJNI_KLJUC`, `TURNIRKO_STRIPE_WEBHOOK_SKRIVNOST` v okolju.

- [ ] Brezplačna registracija deluje kot prej.
- [ ] Premium mesečno (kartica `4242 4242 4242 4242`): račun nastane, naročnina AKTIVNA, koda po pošti.
- [ ] Checkout je v slovenščini, kljukica s povezavo na pogoje je obvezna, besedilo pred
      potrditvijo je berljivo.
- [ ] Organizator letno: zahteva naslov in omogoča davčno številko.
- [ ] **Neuspela obnova** (testna ura, kartica `4000 0000 0000 0341`): naročnina ZAPADLA, stran
      pokaže »Plačilo ni uspelo«, ponudbe nakupa ni; po posodobitvi kartice v portalu spet AKTIVNA.
- [ ] Preklic, obnova, preklop cikla, nadgradnja in znižanje organizatorja (kot 29. 9.).
- [ ] V Stripu (Webhooks → Recent deliveries) vsi odgovori 2xx.

### Faza 5 – preklop v živo (zaporedje je pomembno)

1. [ ] Odločeno: DDV, dogovor o 4 %, računovodkinja potrdila pristop (razdelek 6).
2. [ ] Faze 1 in 2 opravljeni; `pravno.ts` in pogoji izpolnjeni in objavljeni.
3. [ ] **Moj Račun ↔ Stripe (živi način)**: Dodatki → Integracija Stripe → naročilo → *Poveži z
      Stripe*; vklopi scenarij naročnin/Checkouta, **»Vedno davčno potrdi račune iz Stripe plačil«**
      in pošiljanje računa kupcu. Moj Račun **ne uvozi plačil izpred povezave** in **preskoči
      testna plačila**, zato mora biti povezava narejena pred prvim pravim plačilom.
4. [ ] Na strežniku v `.env`: živi `TURNIRKO_STRIPE_TAJNI_KLJUC` in `TURNIRKO_STRIPE_WEBHOOK_SKRIVNOST`,
      nato `docker compose up -d` (navaden `restart` spremenljivk ne prebere). Preveri:
      `docker compose exec app printenv | grep STRIPE | sed 's/=.*/=…/'`.
5. [ ] **Prvi pravi nakup** (Premium mesečno, ~4 €, s kartico lastnika). Preveri po vrsti:
      naročnina v Turnirku AKTIVNA; Stripe webhook 2xx; v Moj Računu račun z ZOI, EOR in QR;
      e-pošta z računom na naslov kupca; računovodkinja ga vidi; številka EOR se ujema na
      strani FURS za preverjanje računov.
6. [ ] **Vračilo** istega nakupa in preklic: Moj Račun izda dobropis, naročnina v Turnirku se
      konča. Računa in dobropis sta pravna dokumenta – računovodkinja naj ve vnaprej.
7. [ ] Šele nato odpri nakupe javnosti.

### Faza 6 – redno delo

- Računovodkinja: mesečni izvoz računov iz Moj Računa (*Izvozi*), Stripovi izpiski izplačil in
  Stripovi računi za provizije; usklajevanje izplačil s TRR.
- Tedensko pregled Stripa: plačila brez računa v Moj Računu, neuspela plačila, spori.
- E-pošta Stripa o neuspešnih dostavah webhooka mora priti na naslov, ki ga nekdo bere.

## 5. Če gre kaj narobe

| Situacija | Ukrep |
|---|---|
| Plačilo je uspelo, računa v Moj Računu ni | Račun izdaj ročno v Moj Računu še isti dan (sam se davčno potrdi). |
| Odstop v 14 dneh | V Stripu vrni plačilo (zmanjšano za porabljeni del) in prekliči naročnino; dobropis nastane sam, `customer.subscription.deleted` konča dostop. |
| V dnevniku (`docker compose logs app`) opozorilo `ob zivi narocnini` | Dva Checkouta hkrati: staro ali novo naročnino prekliči v Stripu in vrni plačilo. |
| V dnevniku opozorilo `a racun ni nastal` | Plačilo je uspelo, e-poštni naslov pa se je medtem zasedel: ročno ustvari račun ali vrni plačilo. |
| Spor (chargeback) | Moj Račun ob odtegnitvi izda dobropis; odziv na spor v Stripu. |
| Moj Račun ne dosega Stripa (odvezano pooblastilo) | Integracija se izklopi: plačila brez računov; takoj ponovno poveži in račune za vmesni čas izdaj ročno. |

## 6. Odprta vprašanja za računovodkinjo

1. Je s.p. zavezanec za DDV in kakšna je oznaka na računih (prag 60.000 € na koledarsko leto;
   ves promet Turnirka šteje v promet s.p.)?
2. Vrsta poslovnega prostora za spletno prodajo in interni akt.
3. Kako knjižiti Stripova izplačila in Stripove račune za provizije?
4. **Dogovor o 4 % prometa**: ker so računi in izplačila na s.p., je ves promet davčno promet
   s.p. Kako denar pride do lastnika Turnirka (pogodba, račun, dohodek) ima davčne posledice za
   oba. Stripe zahteva, da je imetnik računa dejanski prodajalec storitve; pogodba naj to pokrije.
5. Rok hrambe računov in podatkov o kupcih (za stran Zasebnost).
6. Ali želi poleg dostopa v Moj Računu tudi kopije računov po e-pošti?
