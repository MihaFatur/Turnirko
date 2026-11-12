# Pravila: rezultati in zapisniki

> Popravek rezultata, razveljavitev zapisnika, vnos za nazaj, odstop, neodigrane tekme, točke po nizih.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Popravek že shranjenega rezultata**
  (`TekmaStoritev.popraviRezultat`, `SrecanjeStoritev.popraviRezultat`;
  `PUT /tekme/{id}/rezultat` in `PUT /srecanja/tekme/{id}/rezultat`).
  Rezultat se prepisuje s papirja in papir se bere z napako — del napak se
  pokaže šele čez nekaj dni, ko je turnir že zaključen. Zato **PUT in ne POST**:
  vnos ustvari izid, popravek ga zamenja; `vnesiRezultat` končano tekmo še
  naprej zavrne.
  - **Turnirska tekma ZMAGOVALCA SME spremeniti** (`PoslediceIzidaStoritev`,
    `POST /tekme/{id}/rezultat/predogled`, `PUT /tekme/{id}/rezultat?nacin=`,
    enum `NacinPopravka`; nov. 2026, SV regija: napačen zmagovalec zadnje tekme
    veje ni bil popravljiv, odločitve lastnika). Zmagovalec tam ni le podatek o
    tekmi, ampak vozlišče tekmovanja, zato se popravek najprej **oceni brez
    zapisa** (ista ocena gre v predogled in v preverbo pred zapisom), organizator
    pa se odloči, kjer je kaj že odigrano. Aplikacija tega ne more odločiti sama:
    ali so naslednje tekme res igrali pravi igralci, je znano samo v dvorani.
    Sme lastnik in admin, tudi po zaključku; uvoženo ostane samo za branje.
    - **Mreža oz. žreb.** Naslednja tekma brez izida samo dobi novega igralca
      (odločitve ni). Naslednje tekme z izidom zahtevajo izbiro (brez nje 409 in
      nič se ne spremeni): `IZBRISI_IZIDE` izprazni celo vejo (izidi, točke po
      nizih in obračuni gredo, igralci, ki prihajajo iz izpraznjene tekme, niso več
      znani), `ZAMENJAJ_IGRALCA` pusti izid na njegovi **strani** tekme, zato si
      igralca zamenjata mesti po celi veji, tudi po poti poraženih.
    - **Skupina.** Spremenjen vrstni red, iz katerega je že nastal naslednji del,
      zahteva izbiro: `PODRI_NASLEDNJI_DEL` (žrebi Skupine, izločilni del ali
      finalne skupine gredo, tudi z vnesenimi izidi; finalne nastanejo same,
      žrebe naredi organizator znova) ali `OHRANI_NASLEDNJI_DEL` (popravita se
      tabela in rating, del ostane in se s tabelo ne ujema več). Merilo je, ali
      nov vrstni red za del kaj pomeni (`delPrizadet`): izločilni del vzame prva
      dva, finalne skupine pasove po dva, žrebi SV regije vse. Odločitev velja za
      vsak popravek, ki tako spremeni vrstni red, tudi ob istem zmagovalcu (razlika
      nizov odloča krog). Lastnik je želel v oknu tudi tretjo možnost »podri samo,
      če še ni vnesene tekme«: ta ni svoja izbira, ker je pri 0 vnesenih enaka prvi,
      pri vnesenih pa preklicu, zato okno pove število izgubljenih tekem.
    - **Lestvica po popravku se izračuna pred zapisom** (`RazvrstitevStoritev.
      NadomestniIzid`), tudi s točkami po nizih, ker lahko odločijo krog.
    - **Obračun se izrecno pobriše** (`pobrisiObracune`, izprazni sejo, zato gre
      prvi in se tekma prebere znova). Preračun briše samo zapise tekem, ki jih
      odigra znova, zato bi tekma brez izida, izbrisana tekma ali tekma, ki postane
      brez boja, obdržala stari obračun (to je veljalo že prej za »odigrano → brez
      boja«). Izbris tekem žreba brez tega trči ob tuji ključ dnevnika.
    - **Dogodek, ki izgubi izide, spet teče** in turnir se odpre
      (`RazveljavitevEkipneTekmeStoritev.ponovnoOdpriDogodek`), mesta nastanejo ob
      novem zaključku. Pri SV regiji gredo samo mesta iz tekem žrebov: kdor je prišel
      do konca brez tekme (edini v žrebu), jih je dobil ob gradnji. Popravek brez
      izgubljenih izidov dogodek pusti zaključen in mesta dodeli znova; zato
      `dodeliMestaIzFinala` stara mesta najprej počisti (nekdanji finalist bi jih
      sicer obdržal).
    - **Prenesena tekma sledi izvorni** (`uskladiPrenesene`,
      `SkupineStoritev.prepisiIzid`), sicer finalna tabela šteje drug izid kot
      predtekmovalna.
    - Tekma v ekipni tekmi turnirja zmagovalca **ne spremeni** (izid srečanja je
      postal izid tekme v mreži); za to je razveljavitev zapisnika.
    - Vmesnik: `VnosRezultataOkno` pred shranjevanjem vpraša predogled in ob
      zamenjanem zmagovalcu ali spremenjenem vrstnem redu preide na
      `PotrditevPopravka` (v istem oknu). Regresije: `PopravekZmagovalcaTest`,
      `PopravekZmagovalcaDostopTest`, `LenoNalaganjeTest`.
  - **Ligaška tekma zmagovalca SME spremeniti, dokler ostane potek isti**
    (`SrecanjeStoritev.preveriZamenjavoZmagovalca`, september 2026 — v
    Savinja ligi sta se pri prepisu zamenjali imeni in zmagal je napačni).
    Pogoja: (1) **iste tekme so odigrane in iste neodigrane** — pri pragu
    zmag mora popravljen zapisnik dati isto točko odločitve
    (`preveriEnakPotek`); liga brez praga (vse tekme se igrajo) ta pogoj
    izpolni vedno; (2) **izid srečanja se ne obrne tam, kjer je že kaj teklo
    po njem**: tekma končnice (serija) in srečanje rednega dela, kadar je
    končnica že sestavljena iz lestvice (`KoncnicaStoritev.jeSestavljena`).
    Povzetek srečanja osveži `posodobiSrecanje` (ista pot kot vnos:
    srečanju v teku lahko prinese odločitev, pri končanem le prešteje),
    lestvica lige je izpeljanka. Vmesnik ob obrnjenem izidu v oknu popravka
    izpiše novi izid srečanja, preden organizator shrani.
  - **Zmagovalec se preveri PRED zapisom** (`predvidenZmagovalec`, oz.
    `preberiIzid` + `preveriZamenjavoZmagovalca` pri srečanju). Preklic po
    zapisu bi se zanašal na povrnitev transakcije, ta pa ob zunanji
    transakciji (test) ni takojšnja.
  - Turnirska tekma po popravku znova požene `posledice` (mesta v skupini in
    končna mesta se z razliko nizov lahko premaknejo; napredovanje je
    idempotentno, ker se v zaseden slot ne piše).
  - **Točke po nizih se zapišejo znova**: stare se pobrišejo in izbris se
    **izpere takoj** (`deleteAll` + `flush`), ker Hibernate vstavke izvede pred
    izbrisi in bi nov prvi niz trčil ob starega (`UNIQUE (id_tekma,
    zaporedna_st)`).
  - Popravek na koncu pokliče `preracunajPoPopravku` (= `preracunajOd` dneva
    tekme). Sam obračun te tekme ne zadošča — njen izid je vstopal v vse
    poznejše tekme obeh igralcev. Regresija je `PopravekRezultataTest`.
  - **Preračun teče samo za posamično tekmo tekmovanja, ki šteje** (dvojice v
    rating ne gredo, popravek njihovih točk nima kaj preračunati). Preračun
    izprazni sejo, `najdiZaObracun` pa drugega igralca para ne naloži, zato se
    tekma za odgovor prebere znova (kot ob vnosu). Brez tega je popravek točk
    dvojic v Savinja ligi B (okt 2026) vrnil 500 (`LazyInitializationException`);
    varuje ga `popravekTockDvojicVrneZapisnikZObemaIgralcemaPara`.
- **Razveljavitev zapisnika ligaškega srečanja**
  (`SrecanjeStoritev.razveljaviZapisnik`, `DELETE /srecanja/{id}/zapisnik`;
  okt 2026 — v Savinja ligi C je organizator v postavo vpisal napačna igralca
  in srečanje vnesel do konca). Popravek zamenja izid, igralcev pa ne, postave
  po prvem izidu ni mogoče spremeniti — zato srečanje gre **nazaj v razpored,
  kot da ga nihče ni vpisal**: postava, tekme (z menjavami), točke po nizih,
  obračun v dnevniku in `odigran_ob` gredo, rating se preračuna od časa
  srečanja. Organizator nato postavo in izide vpiše znova.
  - Sme lastnik lige oz. turnirja (organizator ali klubski kolega) in admin, v
    vmesniku gumb »Razveljavi zapisnik« s `PotrditvenoOkno` na dnu zapisnika
    (`SrecanjeStran`; pri ekipni tekmi turnirja okno pove, kaj se vrne v turnirju).
  - **Zavrne se, kjer je po izidu že kaj teklo** (isto merilo kot obrnjen
    zmagovalec pri popravku): **končana** tekma končnice (srečanje končnice v
    teku se razveljavi), srečanje rednega dela, kadar sta iz končne lestvice že
    nastali končnica ali kvalifikacije, in ekipna tekma turnirja, po kateri je
    naslednji del turnirja že začet (spodaj). Srečanje brez zapisnika
    (`RAZPORED`) nima česa razveljaviti; uvoženo je samo za branje.
  - **Ekipna tekma turnirja** (`RazveljavitevEkipneTekmeStoritev`, okt 2026):
    izid srečanja je tam postal izid tekme v mreži ali skupini
    (`TekmaStoritev.posledice`), zato razveljavitev vrne tudi **turnir**, kot da
    tekme ni bilo: tekma je spet `PRIPRAVLJENA` (brez zmagovalca in izida),
    zmagovalec/poraženec gre iz naslednje tekme v mreži (ta spet `CAKA` in ji
    gre prazno srečanje; nastane znova, ko sta znani obe ekipi), skupina ni več
    odigrana (`mesto_v_skupini` gre), **kar je nastalo iz predtekmovalne
    skupine, gre**: finalne skupine za mesta (`SKUPINE_ZA_MESTA`, s prenesenimi
    tekmami in koncnimi mesti, ki so jih ekipe dobile ob nastanku) oz. izločilni
    del (`SKUPINE_IZLOCILNI`). **Dogodek, zaključen s to tekmo, spet teče**
    (končna mesta gredo; pri `SKUPINE_ZA_MESTA` samo mesta članov finalnih
    skupin) in **turnir, zaprt zaradi tega, se odpre** — organizator ga po
    novem vnosu zaključi znova. Srečanje v teku (`V_IGRI`) nima posledic in se
    razveljavi vedno.
    - **Zavrne se, ko je naslednji del že začet ali odigran** (naslednja tekma
      mreže, tekma finalne skupine, tekma izločilnega dela; prenesene tekme ne
      štejejo): organizator najprej razveljavi njen zapisnik, nato tega
      (obratni vrstni red od vnosa). Preverba teče PRED prvim zapisom
      (`preveri`), kot pri `preveriZamenjavoZmagovalca`.
    - Rating se preračuna od dneva turnirja (`RatingStoritev.casSrecanja`). Ponovni
      vnos ekipne tekme **ne sproži** `preracunajCeVnesenoZaNazaj` (kot ostale
      turnirske tekme): pri turnirju iz preteklosti, ko imajo igralci
      poznejše zapise, je treba rating po vnosu preračunati ročno.
    - Regresije: `RazveljavitevEkipneTekmeTest` (mreža, tekma za 3. mesto,
      zaključen dogodek + turnir, skupine z finalnimi skupinami, izločilni del,
      zavrnitve).
  - `odigran_ob` gre z zapisnikom namenoma: srečanje, razveljavljeno zato, ker
    je bilo vpisano v napačen par ekip, sicer obdrži čas tujega večera.
  - Regresiji: `RazveljavitevZapisnikaTest` (storitev) in
    `RazveljavitevZapisnikaDostopTest` (pot skozi varnostno verigo).
- **Srečanje, vneseno za nazaj, se ob koncu vnosa preračuna samo**
  (`SrecanjeStoritev.preracunajCeVnesenoZaNazaj`). Ko ligaško srečanje
  postane `KONCANO`, se preveri, ali ima kdo od njegovih igralcev v dnevniku
  zapis, ki velja POZNEJE od časa srečanja in ni iz tega srečanja
  (`RatingZgodovinaRepozitorij.steviloPoznejsihZapisov`; postavitve ne štejejo,
  ker so izhodišče). Če ga ima, teče `preracunajPoPopravku(čas srečanja)`.
  Brez tega bi bilo ponovno vneseno (razveljavljeno) srečanje obračunano proti
  številkam, ki že vsebujejo poznejše tekme — isto velja za večer, vnesen od
  zadnje ure proti prvi (Savinja D, okt 2026). **Enkrat na srečanje in ne ob
  vsaki tekmi**: preračun obnovi stanje vseh igralcev in na polni bazi traja
  nekaj sekund; pri sprotnem vnosu poznejših zapisov ni in ne teče. Ekipna
  tekma turnirja tega nima (kot turnirske tekme). Merilo:
  `ponovniVnosZaNazajDaIstiRatingKotPreracun`,
  `srecanjeVnesenoZaPoznejsimSeUrediSamo` (vnos mora dati isto kot preračun od
  začetka).
- **Odstop med tekmovanjem** (`TekmaStoritev.odstopiIgralca`): že odigrane
  tekme obveljajo, preostale dobijo nasprotniki kot `BREZ_BOJA` (in ne
  `PREDAJA`) — te tekme niso bile odigrane, zato se rating ne sme obračunati.
- **Neodigrane tekme ne štejejo v statistiko igralca.** Merilo je
  `IzidTekme.jeOdigrana()` (le `IGRANO` in `PREDAJA`); poizvedbe v
  `TekmaRepozitorij`, ki hranijo statistiko, ga morajo upoštevati. V lestvici
  skupine pa `BREZ_BOJA` normalno šteje kot zmaga nasprotnika, ker odloča o
  uvrstitvi — to sta namerno ločeni poti.
- **Točke po nizih so povsod neobvezne in povsod po istih pravilih.** Vpisujejo
  se pri turnirskih *in* ligaških tekmah; pravila (veljaven niz do 11 z razliko
  2, ujemanje z izidom, **mogoč vrstni red**) so v enem samem razredu
  `NiziPravila`, ki ga kličeta `TekmaStoritev.shraniTockeNizov` in
  `SrecanjeStoritev.preberiIzid` — dve kopiji bi se sčasoma razšli in ligaški
  zapisnik bi sprejel vnos, ki ga turnirski zavrne. Vrstni red: tekma se konča v
  trenutku odločitve, zato noben niz ne sme slediti izidu, ko je zmagovalec že
  dosegel dovolj nizov. V vmesniku isto vlogo igra skupna komponenta
  `komponente/TockeNizov` (vrstice vnosa + `preveriNize`).
- **Vnos točk po nizih je narejen za prepis papirja z eno roko**
  (`komponente/TockeNizov`). Trije prijemi, vsi v isti komponenti:
  - **Okence se ob dokončani številki samo premakne naprej.** Dokončana je
    dvomestna (niz nad 99 točk ne obstaja) in enomestna, ki ne more biti prva
    števka dvomestne — torej vse razen `1` (10–19). Ničla je enako varna;
    rezultat se ne piše »05«. Z 2–9 se dvomestni izid (22:20) *lahko* začne, a
    tako redko, da je premik pri stotih vpisih prihranek, pri enem pa vrnitev
    s puščico levo. Izjema je podaljšek: enomestna 2–9 ostane v polju, kadar
    se z drugim stolpcem ujema v podaljšku (`zacetekPodaljska`: 19:21, 25:23);
    prej je prepisala prvi stolpec z 11 in podaljškov nad 21:19 ni bilo mogoče
    vnesti.
  - **Po mreži se hodi s puščicami**: levo/desno med stranema in čez konec
    vrstice (samo z roba vpisa, da ostane mogoče postaviti kazalec sredi
    dvomestne številke), gor/dol po istem stolpcu, `Backspace` iz praznega
    polja nazaj. Polja so v **enem seznamu** (niz × stran), zato je »naprej«
    povsod isti korak.
  - **Nad stolpcem stoji PRIIMEK igralca** (`priimek`/`priimek2` v
    `TekmaDto.Udelezenec`, `priimekDomaci…` v `TekmaSrecanjaDto`). To je edina
    kratka oblika imena v vmesniku in ne izjema od pravila »ime pred priimkom«:
    polna imena ostanejo »Ana Novak«, glava stolpca pa je široka kot polje pod
    njo. Priimek pride **iz baze in ne iz rezanja polnega imena** — pri »Ana
    Marija Novak« bi rezanje dalo napačno besedo. Isti priimek nosi oznaka
    polja »Dobljeni nizi — …« v zapisniku srečanja (prej »domači/gost«).
  - Postavitev je **mreža** (`.obrazec__nizi`, štirje stolpci), vrstica niza
    pa samo ovoj za ključ (`display: contents`) — drugače priimek v glavi ne
    stoji nad svojim stolpcem.
  Shrani jih **svoja tabela na vrsto tekme**: `niz` (`id_tekma`) in
  `niz_srecanja` (`id_tekma_srecanja`, V15). Loženi sta, ker je ločena že tekma
  sama in `niz.id_tekma` je `NOT NULL` — skupna tabela bi terjala prezidavo in
  vsaki poizvedbi dodala pogoj, kateri od obeh stolpcev je zapolnjen. Ceno
  plača profil: `ProfilStoritev.tocke` zaradi dveh id-prostorov bere iz obeh
  repozitorijev. Točke po nizih **ne** vplivajo na noben izid — lestvica lige
  in rating štejeta dobljene nize iz same tekme; edina izjema je krog v skupini
  turnirja (`RazvrstitevStoritev`), ki tam sešteje `niz`.
- **Vneseni nizi napolnijo natisnjeni zapisnik.**
  `ZapisnikEkipnegaDvoboja` je papirnati obrazec NTZS: kar je vpisano, izpiše,
  prazne celice pa pusti za ročni vpis (obrazec se natisne tudi pred
  srečanjem). Stolpcev za nize je pet kot na papirju, pri ligi na 7 nizov pa
  sedem, da natis vpisanih točk ne odreže.
