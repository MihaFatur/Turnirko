# Pravila: sv regija

> Sistem SV regija.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **SV regija** (V42, `SistemTekmovanja.SV_REGIJA`, `SvRegijaStoritev`; odločitev
  lastnika 5. 10. 2026): igralci so po jakosti razdeljeni v **nivoje** (težavnostne
  skupine), v vsakem nivoju igrajo skupine vsak z vsakim, nato gredo igralci v
  izločilne žrebe (glavni, tolažilni, 3. ...), žreb pa se igra za **vsa mesta**. **En
  dogodek z nivoji**: ena prijavna lista, ena končna razvrstitev (nivo 1 = mesta 1–16,
  nivo 2 = 17–32 ...), brez prehajanja med nivoji. Samo posamično (strežnik zavrne
  dvojice in ekipe).
  - **Poimenovanje (7. 11. 2026):** organizator in gledalec vidita **»Skupina 1, 2 …«**
    (oznaka S1, S2; zavihek »Skupine«), znotraj nje skupine A, B …; v kodi, API-ju in
    bazi ostane **nivo** (`nivoji`, `nivo`, `stevilo_nivojev`), da se ne pomeša s
    skupino A. Besedila za ljudi pišejo »Skupina« z veliko začetnico.
  - **Žrebi po ravni (V63, odločitev lastnika 7. 11. 2026):** igralci nivoja gredo v
    žrebe **zaporedoma po mestu v skupini in črki skupine** — A1, B1, …, E1, A2, B2 … —
    vsak žreb sprejme toliko igralcev, kolikor jih ima mreža **ravni** (`velikost_zrebov`,
    CSV po nivojih 2/4/8/16/32, prazno = 8 = četrtfinale), zadnji dobi ostanek (pet
    skupin po 4: glavni A1–E1, A2–C2; drugi D2, E2, A3–E3, A4; tretji B4–E4). Organizator
    lahko vpiše tudi **število žrebov** (`stevilo_zrebov`); če ne ustreza natanko
    (premalo mest ali prazen zadnji žreb), **sistem žreba ne dovoli** — `zrebiNivoja`
    vrže `DomenskaIzjema`, ki je v predogledu zadržek (Skupine se kljub temu pokažejo,
    `razrezZaPrikaz`). Udeležence zreba bere `udelezenci(zreb)` kot odsek zaporedja po
    **zapisanih** velikostih zrebov (`zreb.st_udelezencev`) — tako so dogodki, izžrebani
    po prejšnjem pravilu (`rangov_v_zreb`, V42; stolpec v V63 odstranjen), še vedno
    pravilni: njihovi žrebi so isti odseki istega zaporedja. Nosilci:
    `NosilciStoritev.vMrezoPoVrsti` — višje v zaporedju je višji nosilec, pasovi se
    žrebajo, igralca iz iste skupine se srečata čim pozneje (kazen po kolu srečanja +
    klubski trki, naključni poskusi z zamenjavami v pasovih). `ZrebDto.viri` (»A1« ...)
    pove gledalcu, od kod pridejo igralci; lestvica skupine obarva mesta po žrebih.
  - **Žrebi Skupine se smejo spremeniti tudi PO žrebu skupin** (`nastaviZrebeNivoja`,
    `PUT /dogodki/{id}/sv/nivoji/{nivo}/zrebi {steviloZrebov, velikostZreba}`, odločitev
    lastnika 8. 11. 2026): organizator pogosto šele med igranjem skupin vidi, da žrebe
    potrebuje drugače, razveljavitev žreba skupin pa takrat ne gre več (že **odstop
    igralca** zapiše tekme brez boja) in bi skupine izžrebala na novo. Zamenjajo se
    **samo** vrstice `zreb` tega nivoja (stare se izbrišejo in izperejo pred novimi —
    UNIQUE dogodek+nivo+indeks), skupine, tekme in izidi ostanejo, nastavitev se zapiše
    na mesto nivoja v `stevilo_zrebov`/`velikost_zrebov`. **Meja je prva začeta tekma
    žrebov tega nivoja** (ne skupin in ne drugih nivojev): zgrajen, a nezačet žreb se
    podre (tekme gredo, `koncno_mesto` članov nivoja se izprazni — ob gradnji ga dobi
    edini udeleženec žreba) in čaka na organizatorja kot ob koncu skupin; enake
    velikosti žrebov zgrajenih ne podrejo. Število preveri isti `zrebiNivoja` kot pred
    žrebom; velikosti skupin štejejo vse člane, tudi odstopljene (v zaporedju ostanejo).
    `NivoDto.zrebiUredljivi` pove vmesniku, kdaj ponudi gumb »Spremeni žrebe«
    (`SvUrejanjeZrebov` nad žrebi Skupine).
  - **Vrstni red tekem v skupini štirih** (`tekmeSkupine`, 7. 11. 2026): 1-4, 2-3 |
    4-3, 1-2 | 2-4, 3-1 (1 = nosilec skupine, prvi v paru je `prijava1`). Druge
    velikosti ostanejo pri krožni metodi (skupina treh: 2-3, 1-3, 1-2).
  - **Razrez je čista logika** (`SvRegijaStoritev.razrez`, `velikostiSkupin`,
    `velikostiZrebov`), da ga predogled in žreb ne razsodita različno. Poln nivo je
    `stevilo_skupin × velikost_skupine` (privzeto 4 × 4 = 16, v obstoječih stolpcih
    dogodka), **zadnji nivo dobi ostanek**; ostanek, manjši od ene skupine (< 4), se
    pridruži prejšnjemu nivoju (16 + 2 ni nivo, ampak 18). Z izbranim `stevilo_nivojev`
    so nivoji polni, dokler prijav zadošča, sicer enakomerni; `velikosti_nivojev`
    (CSV »16,16,12«) imajo prednost in se morajo ujemati s številom prijav. Nepopolna
    skupina je dovoljena (vsaj 3 igralci; nivo, ki ga ni mogoče razdeliti tako, je ena
    skupina in nima žrebov — mesta določi skupina). Ostanek odloči organizator: brez
    vnosa se ostanek pod 4 pridruži zadnjemu nivoju (67 = 16+16+16+19), z izbranim
    številom nivojev zadnji dobi vse preostale (60 v treh = 16+16+28).
  - **Žreb za vsa mesta je drevo** (`razdeli`): mreža za mesta p..p+n−1 igra n/2 tekem,
    zmagovalci gredo v mrežo n/2 za p..p+n/2−1, **poraženci v mrežo n/2 za naslednja
    mesta** — poraženca sosednjih parov se srečata med seboj. Mreža z 8 igralci ima 12
    tekem. Tekma nosi `id_zreb`, `razpon_od/do` (katera mesta odloča) in, če je zadnja
    na svoji poti, `mesto_zmagovalca/porazenca` (**absolutno** mesto na dogodku).
    **Končna mesta se berejo iz tekem** (`dodeliKoncnaMesta`), ne računajo; `zreb.
    prvo_mesto` je absolutno, zato žreb drugega nivoja stoji za zadnjim mestom prvega.
  - **Prosti prehod tekme NE ustvari** (druga pot kot izločilna mreža, ki ima tekme
    `PROSTO`): igralec gre naprej brez zapisa, **prazno ostane tudi mesto poraženca**,
    zato se slabši par v drevesu sreča samo, če sta oba poraženca resnična. Prazna mesta
    tako pristanejo na dnu razpona in končna mesta ostanejo strnjena (1..N). Udeležencev
    žreba ni nujno 8: `NosilciStoritev.vMrezoIzSkupin` prenese skupine brez 2. ali 4.
    mesta (drugouvrščeni `null`) in ob manjkajočih drugouvrščenih popusti pravilo
    nasprotne polovice. Žreb z enim igralcem tekem nima (`koncno_mesto` dobi takoj).
  - **Nivoji tečejo neodvisno**: žreb nivoja nastane, ko so odigrane skupine TEGA
    nivoja (`obKoncaniSkupinski`), ne celotnega dogodka. Tabela `zreb` (indeks 0 glavni,
    1 tolažilni ...) nastane ob zrebu skupin — takrat je znano, koliko igralcev pride
    vanjo — tekme pa šele po skupinah (`zgrajen`). **Dogodek se zaključi, ko so
    zgrajeni vsi žrebi** in odigrane vse tekme (`TekmaStoritev.zakljuciDogodekCeKoncan`).
    Faza tekme: glavni žreb `GLAVNI`, vsi drugi `TOLAZILNI`; `pozicija` šteje naprej
    čez žrebe in nivoje (UNIQUE dogodek+faza+kolo+pozicija), kolo je globina drevesa.
  - **Ročno je enakovredno žrebu in konča v isti točki**: `PUT /dogodki/{id}/sv/skupine`
    (`ZrebStoritev.izvediRocniZrebSv` → `SvRegijaStoritev.zapisiSkupine`, ki ga kliče tudi
    naključni žreb) velja tudi kot **popravek po žrebu, dokler nobena tekma ni začeta**
    (najprej razveljavi); `POST .../sv/razveljavi` vrne dogodek v pripravo. Razporeditev
    mest v mreži: `PUT /dogodki/sv/zrebi/{id}/mesta` (id prijav po mestih od vrha, `null` =
    prosto), `POST .../znova` (naključno), `POST .../predlog` (brez zapisa; predloga sta
    POST, ker vsak klic žreba znova) — vse samo, dokler se žreb ni začel. Razporeditev se
    hrani v `zreb.razpored` (CSV), ker se iz tekem ne da prebrati (prosti prehodi);
    `zreb.rocni` je **javna** oznaka (»razpored mest je vpisal organizator«). Nastavitve
    razreza `PUT .../sv/nastavitve` (samo v pripravi; žrebi ene Skupine po žrebu skupin
    gredo prek `PUT .../sv/nivoji/{nivo}/zrebi`).
  - **Pasti**: brisanje tekem zreba mora najprej pretrgati povezave (`id_izvor_tekma_*`) in
    izprati (`flush`) PRED novimi vstavki — Hibernate izvede vstavke pred izbrisi in
    UNIQUE trči ob stare vrstice. Test, ki preverja `GET /dogodki/{id}` izven transakcije,
    je `LenoNalaganjeSvRegijaTest`, pravila drevesa in razreza `SvRegijaTest`.
  - Vmesnik: pogled »Nivoji« (`SvRegija.tsx`: skupine in žrebi nivoja kot zložljive vrstice),
    **drevo za vsa mesta** (`SvDrevo.tsx`: odsek = tekme z istim `razpon_od`, torej glavna pot
    1.–8./1.–4./1.–2., pot poražencev 5.–8./5.–6., samostojni tekmi 3.–4. in 7.–8.; črte iz
    `idIzvorZmagovalca1/2` v `TekmaDto`, mere v CSS spremenljivkah `.sv-drevo`, JS računa le
    stolpec in višino), plošča razreza v pripravi (`SvPriprava.tsx`), urejevalnika
    `SvSkupineOkno` in `SvZrebOkno`; imena v `pomozno/svRegija.ts`. Končna razvrstitev ima pri
    SV regiji nove stopničke, seznam ostalih pa po nivojih (zložljivo), kot doslej (glej »Končna
    razvrstitev: stopničke«). `kratekKlub` (`pomozno/oblikovanje.ts`)
    odstrani splošne predpone (»Namiznoteniški klub«) v lestvici, kartici tekme in razvrstitvi.

