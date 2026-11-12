# Pravila: narocnine in placila

> Naročnine, cene, Stripe, kode popusta, strani /narocnina.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Upravljanje obstoječe naročnine** (V35, `UpravljanjeNarocnineStoritev`,
  `StripeNarocnine`, `/api/v1/narocnina`): pregled (obdobje, cikel, cena, zabeležen
  preklop), preklic ob koncu obdobja, obnova preklicane in preklop mesečno ↔ letno
  ob NASLEDNJI obnovi. Nadgradnja Free → Premium ostane v `PlacilaStoritev`
  (Checkout); tu je samo, kar naročnina že ima.
  - **Stripe ostane vir resnice, vrstica pa je predpomnilnik.** Vsak klic vrne
    Stripovo stanje in `StripeNarocnine.uskladi` ga zapiše; webhook
    `customer.subscription.updated` isto stanje prebere še enkrat, zato zamik ali
    dvojna dostava ne pokvarita ničesar. Stripe klici so v `StripeNarocnine`, da
    jih test nadomesti z lažnim (`@MockitoBean`); brez tega bi test rabil pravi ključ.
  - **`naslednji_ciklus` je zabeležena NAMERA, `ciklus` je tisto, kar Stripe
    zaračunava zdaj.** Preklop je Stripe subscription schedule z dvema fazama
    (tekoča, nato nov cikel z `iterations 1`, `proration none`). Preverjeno na
    testnem načinu s testno uro: po koncu obdobja je račun natanko ena postavka
    po novi ceni (53,49 €), brez obračuna razlike.
  - **Urnik po izvedenem preklopu OSTANE priključen še cel novi cikel**, Stripe
    pa pri naročnini z urnikom zavrne `cancel_at_period_end` (»updating any
    cancelation behavior directly is not allowed«). Zato preklic in vsak nov
    preklop urnik najprej **sprostita** (`sprostiUrnik`), preklop pa naredi
    svež urnik namesto da bi posodabljal obstoječega (njegova prva faza je lahko
    že pretekla). Webhook urnik sprosti takoj po izvedenem preklopu — **samo
    takrat**: ob nastanku urnika pride isti dogodek in sprostitev bi odnesla
    ravnokar zabeleženo namero.
  - **Cena preklopa je po pasu ob sklenitvi** (`narocnina.starejsi_od_21`), ne po
    današnji starosti; tekoči cikel prikazuje ceno, ki jo Stripe res zaračunava
    (`cena_ob_sklenitvi`) — starejše letne naročnine so lahko po prejšnjem ceniku.
  - Datumi gredo navzven kot koledarski dnevi po **slovenskem** času
    (`Europe/Ljubljana`), ne po času strežnika: konec obdobja ob 00.30 po CEST je
    na strežniku v UTC še prejšnji dan (`datumiGredoNavzvenPoSlovenskemCasu`).
  - `GET /api/v1/narocnina` je GET, zato mora pravilo v `VarnostneNastavitve`
    stati pred splošnim »GET je javen« (varuje ga `NarocninaDostopTest`).
  - **Zamenjava organizatorskega paketa** (V37 `naslednji_paket`, `PUT
    /narocnina/paket` in `DELETE /narocnina/paket`): **nadgradnja velja takoj,
    znižanje ob obnovi** — strežnik loči po vrstnem redu paketov
    (`Paket.jeVisji`), vmesnik smeri ne pošilja.
    - **Nadgradnja** = posodobitev postavke narocnine (`price_data`, isti interval
      in obračunsko sidro) s `proration_behavior = always_invoice`: Stripe zaračuna
      sorazmerno razliko takoj (`SpremembaPaketaDto.doplacilo` je `total` tega
      računa), `payment_behavior = error_if_incomplete` pa pusti narocnino
      nespremenjeno, če kartica zavrne. Zabeleženo znižanje nadgradnja preglasi
      (urnik se sprosti).
    - **Znižanje** = isti urnik z dvema fazama kot preklop cikla, samo da druga
      faza nosi ceno in izdelek novega paketa. `paket` v vrstici ostane, dokler
      obdobje ne poteče; namera je `naslednji_paket`. Isti paket kot tekoči pomeni
      umik zabeleženega znižanja.
    - **Izdelek paketa v Stripu** (`StripeNarocnine.produkt`): eden na paket,
      najden po metapodatku `turnirko_paket` (Stripova iskalna poizvedba je
      »sčasoma dosledna«, izjemoma nastane dvojnik z istim imenom). Checkout za
      vsak nakup ustvari svojega (`product_data`), zato se ta ne najde.
    - **Webhook uveljavi znižanje, ko se začne NOVO obdobje** (`obdobjeOd` ≥
      prejšnji `trenutnoObdobjeDo` − 5 min, `PlacilaStoritev.jeZnizanjeIzvedeno`):
      paketa iz Stripa ne beremo, ker postavka nosi samo ceno, ta pa ni enolična, če
      se cenik kdaj spremeni. Po izvedbi se urnik sprosti (kot pri preklopu cikla).
    - **Obnova preklicane naročnine sprejme paket** (`NarocninaObnovaVnos`): drug
      paket je vedno prehod ob obnovi, tudi višji — obdobje je že plačano.
    - `NarocninaDto.upravljiva` pove, ali narocnino vodi Stripe: brezplačno leto Pro
      obstoječih organizatorjev (V34) ga nima in vmesnik dejanj ne ponudi.
    - **Preverjeno na Stripovem sandboxu (29. 9. 2026, testna ura, isti parametri kot
      Java koda):** nadgradnja 15 dni pred koncem obdobja zaračuna 3,28 € (formula
      3,29 €; Stripe računa po sekundah) in obdobje ostane isto; z zavrnjeno kartico
      Stripe nadgradnjo zavrne in nič ne ostane (ne postavka ne odprt račun) — to
      ujame `poStripu` (`CardException` → posebno sporočilo); znižanje: do obnove
      ostane visji paket, ob obnovi je en račun z eno postavko po novi ceni, urnik
      ostane priključen in ga je treba sprostiti. **Java klici niso tekli proti
      Stripu** (ni ključa v razvoju): preverjen je obseg zahtev, ne SDK.
    - **Kvota šteje USTVARJENO v sezoni, ne hkrati odprto** (`NarocninaStoritev`),
      zato po znižanju »novo ligo odpreš, ko ena konča« ne drži: vmesnik pove »novih
      do nove sezone ne ustvariš« (predaja je to trdila drugače; odločitev lastnika
      pri nadzorni plošči velja tudi tu).

- **Cene Premium so na dveh mestih in nikjer drugje**: `CenikStoritev` (zaledje,
  zadnja beseda) in `CENA_PREMIUM_*` v `api/tipi.ts` (predogled); od 21. 10. 2026
  1,99/2,99 € mesečno (U21/sicer), 9,99/19,99 € letno. Mesečna cena ne gre pod
  1,99 €: Stripe vzame 2,2 % + 0,25 € na plačilo (pri 0,99 € ~27 %). Vse strani,
  maskota in oglas cene berejo od tam. **Oznaka prihranka letnega plačila je
  izpeljana iz PRIKAZANIH cen** (`prihranekLetnoOdstotki(mesecno, letno)`, zaokroženo
  navzdol; zdaj »−58 %« U21, »−44 %« sicer), ker je vtipkana »−10 %« ob spremembi
  cenika obljubljala napačen popust.

- **Osebni podatki ne gredo v Stripe** (V48, `CakajocaRegistracija`, okt 2026).
  Stripove dogodke bere tudi Moj Račun (davčno potrjeni računi) in vsak skrbnik
  Stripovega računa, zato so v metapodatkih seje samo namen, naključen žeton
  (`registracija`), paket, cikel in cenovni pas; e-pošta gre kot `customer_email`, ker
  je račun potrebuje. Podatki obrazca plačljive registracije (zgoščeno geslo, datum
  rojstva, e-pošta skrbnika, ime, klub) čakajo na plačilo v naši bazi; webhook jih
  najde po žetonu, iz njih ustvari račun in vrstico zbriše (tudi če račun ne more
  nastati). Neplačane pobriše `CiscenjeRacunovStoritev` po 4 dneh (seja 24 ur +
  Stripovi ponovni poskusi webhooka do 3 dni). Novega podatka v metapodatke ne dodajaj
  brez tega premisleka; varuje `PlacilaStoritevTest.vStripeGreSamoZeton`. Postopek
  preklopa v živo je v `docs/NAROCNINE-V-ZIVO.md`.

- **Kode popusta ob registraciji** (V53, okt 2026, `KodePopustaStoritev`, `koda_popusta`,
  `uporaba_kode_popusta`). Admin nastavi kodo in ugodnost (stran Dostopi → »Kode za popust«,
  `KodePopustaOkno`, `/api/v1/kode-popusta`, samo ADMIN); oseba jo vpiše v **povzetku**
  registracije (`RegistracijaVnos.kodaPopusta`, predogled `POST /api/v1/auth/koda-popusta`).
  Tri vrste, vsaka velja na SVOJI poti — koda na drugi poti se **zavrne z razlago**, ne tiho
  prezre (`razlogNeuporabnosti`):
  - `PREMIUM_MESECI`: brezplačna registracija igralca, preizkus Premium traja `meseci`
    namesto enega (`PreizkusPremiumStoritev.zacni`; mesec še vedno teče od POVEZAVE računa).
  - `ODSTOTEK` (1–75 %): plačljiva registracija (Premium ali organizator; neobvezno samo
    za eno vlogo), Stripov kupon `duration once` = popust samo na PRVO plačilo, obnova je po
    polni ceni (`StripeNarocnine.ustvariKupon`, kupon nastane ob prvem plačilu s kodo in se
    hrani v `stripe_kupon_id`). Meja 75 % je izračunana: Stripe ne sprejme plačila pod
    0,50 €, najcenejši paket je 1,99 € (`prvoPlacilo` zaokroži kot Stripe: popust se
    zaokroži, ostanek je plačilo). V Stripovih metapodatkih kode NI (`vStripeGreSamoZeton`
    ostane): kupon nosi samo ime kode; kodo po plačilu zapiše webhook iz čakajoče
    registracije (`cakajoca_registracija.id_koda`).
  - `ORGANIZATOR_MESECI`: organizator registracije brez plačila dobi `paket` za `meseci`;
    **teče od potrditve organizatorja** (`RacuniStoritev.potrdiOrganizatorja` →
    `uveljaviZaOrganizatorja`), ne od registracije. Vrstica `narocnina` je `PREIZKUS`
    (V53 sprosti CHECK: preizkus je lahko vsak paket razen BREZPLACNO), potek je samo datum
    (`Narocnina.jeVeljavna`). Organizator v tem času paketa ne more kupiti sam (stran
    Naročnina ga kaže kot brezplačno leto Pro iz V34); po poteku dobi običajno ponudbo.
  - **Ugodnost se po nastanku ne spreminja** (vrsta, meseci, odstotek, paket, za koga): kdor
    se je registriral, je dobil, kar je koda takrat obljubljala. Spremeni se opis, rok
    (`velja_do`, zadnji dan vključno), `najvec_uporab` in aktivnost. Izbriše se samo koda brez
    porab in brez čakajočih plačil, sicer se izklopi.
  - **Mesta pri `najvec_uporab`** drži račun (poraba) ali čakajoča plačljiva registracija
    (4 dni); nepotrjen račun se po 48 urah izbriše in z njim poraba (`ON DELETE CASCADE`).
    **Lasten ostanek istega naslova se ne šteje** (`zasedenihZ`), sicer bi kdor registracije ni
    dokončal in jo ponovi ostal pred zaprtimi vrati.
  - **Poraba kaže na račun samo s številko** (`UporabaKodePopusta.idUporabnik`), ne z
    entiteto: Hibernate odpove (`TransientPropertyValueException`), če v isti enoti dela
    izbrišeš račun, na katerega kaže naložena vrstica. Po tej poti `zasedenihZ` vrstice ne
    naloži (`existsBy…`): SQLite izbrisanemu id-ju da isti id novi vrstici in naložena stara bi
    se z njo ujela (`DuplicateKey`). Testi so v eni transakciji, zato morajo med dvema
    zahtevama `flush()` + `clear()` (`ponovnaRegistracijaIsteOsebeNeZasedeMesta`).
  - **Odgovor ne razkriva obstoječih kod**: neznana, ugasnjena, potekla in izčrpana koda
    dobijo isto sporočilo (`NEVELJAVNA_KODA`), ugibanje je omejeno na 30 preverjanj na uro na
    IP. Preverba kode je PRED omejitvijo pošiljanja e-pošte (napačna koda ne porabi minute
    za nov poskus) in ni odvisna od tega, ali naslov že obstaja.
  - Vmesnik: polje v povzetku (`RegistracijaTok`; »Imam kodo za popust« → Uporabi), cena in
    paket v povzetku sta že po kodi; vpisana a neuporabljena koda **zadrži gumb**, da oseba ne
    ustvari računa brez ugodnosti, ki jo pričakuje; sprememba vloge ali paketa kodo odstrani.
    Hitra registracija (brez koraka Paket) odstotka ne more uporabiti in to pove. Umami:
    `koda-popusta-uporabljena` (samo vrsta, kode same ne).
  - **Ni preverjeno proti živemu Stripu** (v razvoju ni ključa): obseg zahtev (kupon na seji,
    `toMap()`) je pod testom, ne pa Stripov odgovor ali kako Moj Račun izda račun za plačilo s
    kuponom — preveri na sandboxu pred živim preklopom (`docs/NAROCNINE-V-ZIVO.md`). Če
    kupon v Stripu izbrišeš, nastavi `koda_popusta.stripe_kupon_id` na NULL.
  - **Vnos kode pri že prijavljenem računu** (3. 11. 2026, `POST /api/v1/narocnina/koda`,
    `KodePopustaStoritev.uveljaviZaPrijavljenRacun`, stran Naročnina navijača: »Imam kodo za
    popust«). **Velja SAMO `NAVIJAC_MESECI`** na potrjenem računu navijača **brez vrstice v
    `narocnina`** in brez zabeležene porabe; mesec se šteje od vpisa (`zacniNavijacaBrezPlacila`
    je skupna s potrditvijo računa). Druge vrste se zavrnejo z razlago (`PREMIUM_MESECI` in
    `ORGANIZATOR_MESECI` tečeta od povezave oz. potrditve, ki sta tu že mimo; `ODSTOTEK` bi
    potreboval kupon v Checkoutu nadgradnje, kar ni zgrajeno). Pravila registracije držijo:
    ena koda na račun, mesto pri `najvec_uporab` pod zaklepom kode, isto sporočilo za
    neznano/ugasnjeno/potekle kode, isti omejevalnik ugibanja (30 na uro na IP); poleg tega
    zaklep računa (`Zaklepi.Vrsta.RACUN`) proti dvojnemu pošiljanju. Checkout je Stripov in
    polja za kodo nima (`allow_promotion_codes` ni vklopljen in naših kod Stripe ne pozna);
    vmesnik polje kaže samo, ko je `NarocninaDto.status === null` (račun brez vrstice), po
    uspehu pa razveljavi vse poizvedbe, ker paket odpre še spremljanje, koledar in družino.
  - Ni pokrito: koda `ODSTOTEK` pri nadgradnji, vnos kode pri igralcu in organizatorju ter
    opomnik ob koncu organizatorskega paketa brez plačila (obvestilo o koncu preizkusa
    navaja ceno Premium, zato `posljiOpomnike` organizatorja preskoči). Testi:
    `KodePopustaTest`, `KodePopustaDostopTest`, `KodaPriPrijavljenemRacunuTest`,
    `KodaPriNarocniniDostopTest`.
- **Stran `/narocnina` za igralca** (`komponente/NarocninaIgralec`, razdelek
  »Naročnina igralca« v `slog.css`; organizatorski del je ostal v
  `strani/NarocninaStran`) je poustvarjena po `design_handoff_narocnina` in
  uporablja **iste razrede kot razdeljeno okno registracije** (`.registracija__*`):
  plošča je `.registracija` z obrobo, levi stolpec/trak, kolofon, znesek in
  primerjalna tabela pa so skupni. Tri stanja določa zaledje (`aktivna`,
  `preklicana`, sicer Free); preklop, preklic in obnova so mutacije, ki vrnejo
  posodobljeno naročnino.
  - **Merilo ujemanja je prototip, ne posnetek.** Posnetki iz predaje niso
    ponovljivi na piksel (stran je pri njih 7–9 px nižja); prototip `*.dc.html`
    v istem brezglavem Chromu in aplikacija se ujemata do anti-aliasinga pisave
    (namizje 6 stanj: 0–330 pikslov razlike od 2 milijonov; telefon enako,
    razen spodnje vrstice, ki ni del strani).
  - **Prototipove mere so posledica podedovanih velikosti:** razmik črk
    naslova (−1,19 px) in naslova plošče (−0,8925 px) je `-0.035em` od privzete
    velikosti `h1` (34 px) oz. `h2` (25,5 px), ki ga `span` podeduje kot dolžino.
    Ko izrecno zapišeš `-0.035em` na svojem elementu, je naslov ožji od posnetka.
    Znesek predogleda preklopa je 40 px na telefonu; `--tip-h2` se tam zmanjša na
    32 px, zato ga razred `.narocnina__znesek` postavi sam.
  - Vrnitev s Checkouta (`?stanje=uspeh`) potrdi plačilo šele, ko webhook zapiše
    naročnino, zato stran nekajkrat povpraša znova (`POSKUSI_PO_VRNITVI`) in
    parameter takoj pobriše iz naslova; to je skupen kavelj
    `komponente/useVrnitevSPlacila` (igralec in organizator).
  - Zabeležen preklop se da umakniti tudi po osvežitvi strani: obvestilo
    »Od 12. 10. 2026 plačuješ letno …« z gumbom »Razveljavi« se izpelje iz
    `naslednjiCiklus`, ne živi samo od potrditve.
- **Stran `/narocnina` za organizatorja** (`komponente/NarocninaOrganizator`, razdelek
  »Naročnina organizatorja« v `slog.css`; predaja
  `design_handoff_narocnina_organizatorja`, 29. 9. 2026) ponovi zgradbo strani
  igralca (`.registracija__*`, `.narocnina__*`), nova sta le primerjalna tabela s
  tremi stolpci (`.narocnina__tabela`) in okvir razlike (`.narocnina__razlika`).
  Vsi organizatorski paketi so letni: izbirnika Mesečno/Letno ni. Stanja: aktivna,
  preklicana, brez paketa (privzeto Plus); poraba paketa pride iz `GET
  /organizator/pregled` (isti vir kot palica na nadzorni plošči), zato `poDejanju`
  razveljavi tudi poizvedbo `organizator-pregled` (meje se s paketom spremenijo).
  - **Merilo ujemanja je prototip `*.dc.html` v istem brezglavem Chromu**: 7 stanj ×
    namizje/telefon, besedilni bloki (škatla + slog) in škatle s podlago/obrobo se
    ujemajo do 0,6 px. Razlike so le namerne: višina strani na telefonu (spodnja
    vrstica aplikacije rezervira 92 px namesto 32 px) in besedila opozoril ob
    znižanju (glej spodaj).
  - **Pasti prototipa:** razmik črk naslova je v em od VELIKOSTI SPANA (−3,08 px pri
    88 px, −1,44 px pri 48 px), ne podedovan z `h1` kot pri igralcu — zato ima stran
    svoja razreda `.narocnina__naslov-nad` in `__naslov-ime`, ne `.naslov-strani__*`;
    kolofon obdobja se lomi po vrednosti (brez `white-space: nowrap` igralca); vrstica
    obdobja ima razmik med vrsticama 4 px; `button` ne podeduje pisave
    (`.narocnina__stolp` jo postavi); pojasnilo pod tabelo se lomi brez
    `text-wrap: pretty`; levi stolpec ima `line-height: 1.35`.
  - **Besedila opozoril ob znižanju odstopajo od predaje na dveh mestih, obe
    zavestno:** »imaš 4 turnirje« (tožilnik, kot v PNG in README; prototipni izraz
    daje »4 turnirji«) in pri ligah »V tej sezoni imaš 2 ligi … novih do nove sezone
    ne ustvariš« namesto »Odprti imaš 2 tekoči ligi … novo odpreš, ko ena konča«
    (kvota šteje ustvarjeno v sezoni, ne hkrati odprto — glej zaledje).
  - **Brezplačno leto Pro** (`upravljiva = false`): stran pokaže paket in datum, brez
    izbire paketa, preklica in Stripe portala.
  - Popravljeno je tudi sklanjanje v predlogu: »preostalih 15 dni« / »preostala 2
    dneva« (`preostalihDni`), `sklonTurnirjevTozilnik`, `sklonLigTozilnik`.
