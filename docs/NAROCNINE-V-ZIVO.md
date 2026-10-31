# Naročnine v živo: od testnega do pravega zaračunavanja

Kontrolni seznam za preklop plačil (Premium igralca, paketi organizatorja) iz
Stripovega **testnega** načina v **živi**. Stanje na dan 20. 10. 2026. Koraki so
razvrščeni tako, da se nobeno pravo plačilo ne zgodi, preden je urejen račun.

Vir resnice za cene in omejitve ostaja koda (`CenikStoritev`, `NarocninaStoritev`),
za delovanje plačil `PlacilaStoritev`. Ta dokument ne podvaja številk.

## 1. Odločitve

| Vprašanje | Odločitev | Stanje |
|---|---|---|
| Prodajalec naročnin | **Jaka Golavšek s.p.** (do 12. 10. je veljal NTK Žalec) | potrjeno 12. 10. 2026 |
| Program za račune | **Moj Račun** + dodatek »Integracija Stripe« | potrjeno |
| Stripe račun | **nov, na s.p.** (imetnik s.p., TRR s.p.); lastnik te kode je povabljen kot skrbnik | potrjeno |
| Zavezanec za DDV? | **ne**, tudi ne atipični zavezanec; cene so končne, na računih klavzula iz Moj Računa (1. odst. 94. čl. ZDDV-1) | potrdila računovodkinja 21. 10. 2026 |
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

### Faza 1 – FURS in Moj Račun (imetnik s.p. ali računovodkinja) – opravljeno 20. 10. 2026

Moj Račun je program za izdajanje računov; osnovni del je brezplačen in vključuje
davčno potrjevanje. Dodatek za Stripe stane 4,99 € na mesec (z DDV), odpoved kadar koli
([cenik](https://moj-racun.si/cenik)).

- [x] **Potrdilo FURS** (namensko digitalno potrdilo za davčno potrjevanje): v eDavkih →
      Dokumenti → obrazec **DPR-PridobitevDP**. Po desetih minutah se v osebnem predalu
      pokažeta referenčna številka in geslo; v eDavkih potrdilo prevzameš (datoteka `.p12`)
      in si zanj izbereš geslo. Brezplačno, velja 5 let. **Osebnega potrdila (SIGEN-CA) ne
      nalagaj** – je samo za prijavo v eDavke ([navodilo](https://moj-racun.si/dokumentacija/kako-pridobim-certifikat-za-davcno-potrjevanje)).
      *Prevzeto 20. 10. 2026.* Datoteko `.p12` in geslo hrani kot ključ blagajne: ne v git, ne
      v klepet, ne po e-pošti; varnostna kopija na ključku ali v upravitelju gesel.
- [x] **Moj Račun**: registracija (brez kartice), zavihek *Profil*: ime in priimek, podjetje,
      naslov, davčna številka, matična številka, pravna oblika, IBAN, **Zavezanec za DDV**
      (če ni zavezanec, se pokaže še *Atipičen zavezanec*; obe nastavitvi določata davčno
      klavzulo na računu – izbere računovodkinja).
- [x] *Nastavitve → Davčno potrjevanje → Certifikat*: naloži `.p12` in vpiši geslo, ki si ga
      izbral ob prevzemu v eDavkih.
- [x] Isti zavihek, **Način potrjevanja**: *Vedno* (plačila so s kartico, torej gotovina) in
      stikalo **»Vedno davčno potrdi račune iz Stripe plačil«** (vidno šele, ko je certifikat
      naložen).
- [x] *Nastavitve → Davčno potrjevanje → Poslovni prostor*: vrsta je **nepremičnina**
      (oznaka do 4 znakov, naslov, katastrska številka, številka stavbe in dela stavbe, datum
      veljavnosti) ali **premični prostor** A (vozilo, stojnica), B (objekt na stalni lokaciji
      izven katastra) ali C (brez poslovnega prostora). Nato **oznaka elektronske naprave**
      (do 4 znake). Vsak par prostor + naprava ima svoje zaporedje številk računov od 1 naprej.
      Prijavo pri FURS odda Moj Račun sam. **Vrsto prostora za spletno prodajo določi
      računovodkinja**; zaprtje prostora je trajno, oznake ni mogoče znova uporabiti.
- [x] **Interni akt** o izdajanju računov: v istem razdelku »Prenesi interni akt«, sprejme ga
      s.p., računovodkinja ga pregleda; ne pošilja se na FURS.
- Če FURS ob izdaji ni dosegljiv, Moj Račun račun pošilja znova, dokler ga ne potrdi (zakonski
  rok sta dva delovna dneva od izdaje).
- [x] *Nastavitve → Dodatni dostopi → Povabi osebo*: vloga **Računovodja** (prvi dodatni dostop
      je brezplačen).
- [x] *Nastavitve računov*: jezik računa slovenščina; plačilne QR kode in opomnike za plačilo
      izklopi (plačilo je s kartico).
- [ ] Dodatek **Integracija Stripe** se naroči in poveže **šele v fazi 5**.

### Faza 2 – Stripe račun za s.p. (imetnik s.p.)

Nadzorna plošča je v angleščini; imena so zapisana tako, kot jih kaže (preverjeno po Stripovi
dokumentaciji 20. 10. 2026). Če se kak meni preimenuje, išči po pomenu. Nastavitve **živega
načina in sandboxa so ločene**: kar je označeno s *(oba)*, nastavi v obeh.

**A. Račun (imetnik s.p.)**

- [ ] Registracija na `dashboard.stripe.com/register`, država **Slovenia**. Ob aktivaciji
      (*Activate payments*): vrsta podjetja samostojni podjetnik (*Individual / Sole
      proprietorship*), podatki kot v poslovnem registru (ime, matična 7178638000, davčna
      19045301, naslov), dejavnost *Software / SaaS*, spletna stran `https://turnirko-nt.si`,
      opis npr. »Naročnine na spletno aplikacijo za namizni tenis: rezultati, lestvice,
      organizacija turnirjev in lig«. Zastopnik je imetnik s.p. (osebni dokument). Bančni račun:
      **TRR s.p. v EUR**. Vklopi dvostopenjsko prijavo.
- [ ] *Settings → Team and security → Team → Add member*: e-pošta lastnika kode, vloga
      **Administrator** (ključi, webhook, nastavitve Billing; vse spodaj lahko nato naredi on).
- [ ] *Settings → Business → Bank accounts and currencies*: urnik izplačil po dogovoru z
      računovodkinjo (mesečno je za knjiženje najlažje).

**B. Nastavitve**

- [ ] *(oba)* *Settings → Business → Business details → Public details*: javno ime `Turnirko`,
      *Statement descriptor* `TURNIRKO`, e-pošta podpore `info@turnirko-nt.si`, naslov podpore
      (naslov s.p.), spletna stran `https://turnirko-nt.si`, **Terms of service**
      `https://turnirko-nt.si/pogoji`, **Privacy policy** `https://turnirko-nt.si/pogoji#zasebnost`.
      **Brez povezave na pogoje Stripe seje ne ustvari** (koda zahteva soglasje s pogoji).
- [ ] *Settings → Business → Branding* (neobvezno): logotip, barva `#0088CE`.
- [ ] *Settings → Business → Customer emails*: pod *Payments* **izklopi** *Successful payments* in
      *Refunds*. Kupec dobi en dokument: davčno potrjen račun iz Moj Računa.
- [ ] *Settings → Billing → Subscriptions and emails*:
      - pošiljanje Stripovih računov kupcem (*Manage invoices sent to customers*, e-pošta s
        končnim oz. plačanim računom) **izklopi**;
      - **vklopi** *Send emails about upcoming renewals* (dni pred obnovo: 7) in *Send emails about
        expiring cards*;
      - **vklopi** *Send a Stripe-hosted link for customers to confirm their payments when
        required* (obnova s kartico, ki zahteva 3-D Secure, sicer propade);
      - povezava v teh e-poštah: *Link to a Stripe-hosted page*.
- [ ] *Billing → Revenue recovery → Retries*: **Smart Retries**, privzeto 8 poskusov v 2 tednih;
      po zadnjem neuspehu **Cancel the subscription** (koda takrat naročnino konča).
      *Revenue recovery → Emails*: **vklopi** *Send emails when card payments fail*. Dostop v
      Turnirku ugasne že ob prvem neuspehu in se vrne sam, ko plačilo uspe (razdelek 1).
- [ ] *(oba)* *Settings → Billing → Customer portal* – shrani v vsakem načinu posebej:
      - *Payment methods*: **vklopi** (posodobitev kartice – za to je gumb »Kartica v Stripe«);
      - *Invoice history*: **izklopi** (Stripov račun ni davčno potrjen; pravega pošlje Moj Račun);
      - *Customer information*: e-pošto **izklopi** (je prijava v Turnirko in naslov za račun),
        naslov za račun in davčno številko vklopi (organizatorji), ostalo izklopi;
      - *Cancel subscription* in *Switch plan*: **izklopi** (preklic in zamenjava paketa gresta
        prek strani Naročnina, ki uredi tudi urnik naročnine);
      - *Headline* npr. »Turnirko – kartica za naročnino«, povezavi na pogoje in zasebnost kot
        zgoraj, *Default redirect link* `https://turnirko-nt.si/narocnina`.
- Plačilnih načinov ni treba urejati: koda zahteva kartico (Apple Pay in Google Pay sta del nje).
  **Stripe Tax ne vklapljaj.**

**C. Ključ in webhook (lastnik kode, na strežniku)**

- [ ] *Workbench → API keys → Secret key* (`sk_test_…` v sandboxu, `sk_live_…` v živo). Živi
      ključ Stripe pokaže samo enkrat: kopiraj ga z gumbom (ne z označevanjem – 23. 9. je bil
      odrezan) naravnost v `.env` na strežniku kot `TURNIRKO_STRIPE_TAJNI_KLJUC`. Ne v klepet, ne
      v git. Omejen ključ (*restricted key*) je mogoč kasneje, ko so pravice preizkušene.
- [ ] **Webhooka ne ustvarjaj v nadzorni plošči.** Ta ponudi samo privzeto ali najnovejšo
      različico API-ja (zdaj `2026-09-30.endive`), `stripe-java 29.4.0` pa dogodke bere v obliki
      **`2025-07-30.basil`**; Stripe za Javo neujemanja odsvetuje. Na strežniku poženi
      `sudo sh ./skripte/stripe-webhook.sh`: ključ prebere iz `.env`, ustvari webhook na
      `https://turnirko-nt.si/api/v1/placila/webhook` s štirimi dogodki
      (`checkout.session.completed`, `customer.subscription.updated`,
      `customer.subscription.deleted`, `invoice.payment_failed`) in pravo različico, skrivnost
      (`whsec_…`) zapiše v `.env` sam in aplikacijo znova zažene. Ob preklopu na živi ključ ga
      poženi znova (živi način ima svoj webhook in svojo skrivnost).

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

Opravljeno 20. 10. 2026:

- [x] `vmesnik/src/pomozno/pravno.ts`: podatki prodajalca (Jaka Golavšek s.p., naslov, matična,
      davčna, e-pošta).
- [x] **Čakajoča registracija** (V48, `CakajocaRegistracija`): podatki obrazca plačljive
      registracije (zgoščeno geslo, datum rojstva, e-pošta skrbnika, ime, klub) do plačila čakajo
      v naši bazi, v Stripovih metapodatkih je samo naključen žeton (`registracija`) ter paket,
      cikel in cenovni pas. Moj Račun bere Stripove dogodke, zato osebnih podatkov tam ne sme
      biti. Vrstica gre, ko račun nastane; neplačana se pobriše po 4 dneh (seja 24 ur + Stripovi
      ponovni poskusi webhooka do 3 dni). Test `PlacilaStoritevTest.vStripeGreSamoZeton`.
      **Seja, začeta pred objavo V48, po plačilu računa ne ustvari** (opozorilo `a racun ni
      nastal`) – zato objava pred preizkusom v fazi 4, ne med njim.
- [x] `PogojiStran.tsx`: prodajalec, račun za vsako plačilo po e-pošti, Moj Račun kot obdelovalec
      (kaj dobi od Stripa), hramba računov po davčnih predpisih, izbris neplačane registracije.

Odprto:

- [x] `PogojiStran.tsx`: **besedilo o DDV** (21. 10. 2026): cene so končne, DDV ni obračunan
      (`PONUDNIK.zavezanecZaDdv` v `pravno.ts`); `POGOJI_VELJAJO_OD` = 21. 10. 2026.
- [ ] Besedilo pogojev naj pregleda pravnik (ZVPot-1, ZVOP-2).
- [ ] Gumb za odstop od pogodbe (direktiva 2023/2673, od 19. 6. 2026): slovenska novela ZVPot-1
      je v pripravi; ob uveljavitvi dodati.

### Faza 4 – preizkus v sandboxu (s ključi s.p.)

Na strežniku: objava kode z V48 (`git pull && sudo docker compose up -d --build`), v `.env`
sandbox ključ s.p. (`sk_test_…`), nato `sudo sh ./skripte/stripe-webhook.sh`. Nakup na
`https://turnirko-nt.si` s testno kartico. V sandboxu Stripe e-pošte kupcem pošilja samo na
naslove članov ekipe.

Lokalno (namesto strežnika): `stripe listen --events checkout.session.completed,customer.subscription.updated,customer.subscription.deleted,invoice.payment_failed --forward-to localhost:8080/api/v1/placila/webhook`
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

**Paket Navijač in ugodnosti (V58–V61, dodano 30. 10. 2026; nič od tega še ni teklo proti Stripu):**

- [ ] Navijač letno (19,99 €): registracija in nadgradnja brezplačnega navijača; izdelek »Turnirko
      Navijač« je v Stripu nov, **preveri, kaj Moj Račun izda zanj** (ime postavke, brez DDV).
- [ ] Mesečnega Navijača ni (ponudba, Checkout in strežnik ga zavrnejo).
- [ ] Koda popusta za navijača (`NAVIJAC_MESECI`) in odstotkovna koda pri plačljivi registraciji navijača.
- [ ] **Podpora klubu**: organizator kluba, ki ga podpira 10 plačnikov, pri prvem nakupu ali nadgradnji
      paketa dobi kupon (`once`); preveri znesek na Checkoutu in na računu v Moj Računu ter da obnova
      poteka po polni ceni (popusta ob obnovi namenoma ni).
- [ ] Preklic Navijača: družinski Premium igralca preneha, osebni koledar vrne 403, potisna obvestila
      prenehajo (podatki ostanejo za obnovo).
- [ ] **Potisna obvestila**: ustvari par ključev (`java -cp target/classes si.turnirko.pomozno.VapidKljuci`),
      nastavi `TURNIRKO_PUSH_JAVNI_KLJUC`, `TURNIRKO_PUSH_ZASEBNI_KLJUC`, `TURNIRKO_PUSH_SUBJEKT`
      (`mailto:info@turnirko-nt.si`), na dveh napravah (Android Chrome, iPhone z dodano stranjo na začetni
      zaslon) vklopi obvestila in pošlji preizkus. Šifriranje je preverjeno z uradnim testnim vektorjem
      RFC 8291, **dostava prek FCM/Apple še ni preverjena**.
- [ ] Osebni koledar: povezavo `.ics` dodaj v Google in Apple koledar, preveri, da se tekme pojavijo in da
      po zamenjavi povezave stara preneha delovati.

### Faza 5 – preklop v živo (zaporedje je pomembno)

1. [ ] Odločeno: DDV (opravljeno 21. 10.), dogovor o 4 %, računovodkinja potrdila pristop
      (razdelek 6).
2. [ ] Faze 1 in 2 opravljeni; `pravno.ts` in pogoji izpolnjeni in objavljeni. **Testne
      naročnine iz sandboxa pospravljene**: v živem načinu njihovih Stripovih id-jev ni, zato bi
      testni račun obdržal paket do konca obdobja (letni – leto dni) brez plačila. Seznam:
      `sqlite3 -header -column podatki/turnirko.db "SELECT u.id, u.uporabnisko_ime, u.vloga,
      n.paket, n.ciklus, n.status FROM narocnina n JOIN uporabnik u ON u.id = n.id_uporabnik
      WHERE n.stripe_narocnina_id IS NOT NULL"`; testni račun izbriši (Dostopi → kartica
      računa → »Izbriši račun«, naročnina gre z njim), pravemu računu zbriši samo vrstico naročnine.
3. [ ] **Moj Račun ↔ Stripe (živi način)**: Dodatki → Integracija Stripe → naročilo (4,99 € na
      mesec) → *Poveži z Stripe* (prijava v Stripe in »Connect«; Moj Račun dobi samo branje
      dogodkov, plačil, računov in strank, gesla ne vidi). Scenariji
      ([dokumentacija](https://moj-racun.si/dokumentacija/stripe-integracija)):
      - vklopi **Stripe Checkout in plačilne povezave** (prvi nakup) in **Naročnine (Stripe
        Billing)** (vsaka obnova, doplačilo ob nadgradnji organizatorja);
      - izklopi **Enkratna plačila**, **SEPA direktna obremenitev** in **Stripe lastni računi**
        (Turnirko jih ne uporablja; računi za naročnine so po dokumentaciji samo v scenariju
        Naročnine, zato se ne podvojijo);
      - *Ravnanje z vračili in spori*: **Samodejno (priporočeno)** – vračilo izda dobropis sam;
      - pošiljanje računa kupcu vklopi; »Dodaj gumb »Plačaj zdaj«« izklopi (plačano je že).
      Stikalo **»Vedno davčno potrdi račune iz Stripe plačil«** je v *Nastavitve → Davčno
      potrjevanje* (faza 1). Moj Račun **ne uvozi plačil izpred povezave** in **preskoči
      testna plačila**, zato mora biti povezava narejena pred prvim pravim plačilom.
4. [ ] Na strežniku v `.env` živi `TURNIRKO_STRIPE_TAJNI_KLJUC`, nato
      `sudo sh ./skripte/stripe-webhook.sh` (ustvari živi webhook, zapiše skrivnost, aplikacijo
      znova zažene). Preveri: `docker compose exec app printenv | grep STRIPE | sed 's/=.*/=…/'`.
      Sandboxov webhook lahko ostane – dogodki sandboxa gredo z drugo skrivnostjo in jih
      aplikacija zavrne.
5. [ ] **Prvi pravi nakup** (Premium mesečno, 2,99 €, s kartico lastnika). Preveri po vrsti:
      naročnina v Turnirku AKTIVNA; Stripe webhook 2xx; v Moj Računu **natanko en** račun z ZOI,
      EOR in QR (dva pomenita, da se scenarija prekrivata);
      e-pošta z računom na naslov kupca; računovodkinja ga vidi; številka EOR se ujema na
      strani FURS za preverjanje računov.
6. [ ] **Vračilo** istega nakupa in preklic: Moj Račun izda dobropis, naročnina v Turnirku se
      konča. Računa in dobropis sta pravna dokumenta – računovodkinja naj ve vnaprej. Pri sporih
      Moj Račun izvirnega računa naročnine ne najde; če ga tudi pri vračilu ne in pride le
      obvestilo »Stripe vračilo zahteva pregled«, je dobropis za naročnine vedno ročen – zapiši
      to v razdelek 5.
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
| Odstop v 14 dneh | V Stripu vrni plačilo (zmanjšano za porabljeni del) in prekliči naročnino; dobropis naj nastane sam (preveri v fazi 5, korak 6 – sicer ga izdaj ročno), `customer.subscription.deleted` konča dostop. |
| V dnevniku (`docker compose logs app`) opozorilo `ob zivi narocnini` | Dva Checkouta hkrati: staro ali novo naročnino prekliči v Stripu in vrni plačilo. |
| V dnevniku opozorilo `a racun ni nastal` | Plačilo je uspelo, a e-poštni naslov se je medtem zasedel ali čakajoče registracije ni več (plačilo po več kot 4 dneh, seja izpred V48): ročno ustvari račun ali vrni plačilo. |
| Spor (chargeback) | Moj Račun ob odtegnitvi izda dobropis; odziv na spor v Stripu. **Pri računu za naročnino izvirnika ne najde**: pošlje samo obvestilo, dobropis izdaj ročno. |
| Moj Račun ne dosega Stripa (odvezano pooblastilo) | Integracija se izklopi: plačila brez računov; takoj ponovno poveži in račune za vmesni čas izdaj ročno. |

## 6. Odprta vprašanja za računovodkinjo

1. ~~Je s.p. zavezanec za DDV in kakšna je oznaka na računih?~~ **Ne, tudi ne atipični**
   (21. 10. 2026). Prag 60.000 € na koledarsko leto spremljati: ves promet Turnirka šteje v
   promet s.p.
2. Vrsta poslovnega prostora za spletno prodajo in interni akt.
3. Kako knjižiti Stripova izplačila in Stripove račune za provizije?
4. **Dogovor o 4 % prometa**: ker so računi in izplačila na s.p., je ves promet davčno promet
   s.p. Kako denar pride do lastnika Turnirka (pogodba, račun, dohodek) ima davčne posledice za
   oba. Stripe zahteva, da je imetnik računa dejanski prodajalec storitve; pogodba naj to pokrije.
5. Rok hrambe računov in podatkov o kupcih (za stran Zasebnost).
6. Ali želi poleg dostopa v Moj Računu tudi kopije računov po e-pošti?
