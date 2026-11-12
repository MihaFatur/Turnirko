# Pravila: uvoz

> Uvoz in sinhronizacija (Stupa, stara stran NTZS), uvoženo samo za branje, stran /uvoz, klubi.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Uvoženo tekmovanje je samo za branje — za vse, tudi za administratorja**
  (`turnir.vir`, `liga.vir`, V27; `STUPA` ali `STARA_NTZS`). Vir resnice je
  zveza: popravek v Turnirku bi naslednji uvoz povozil. Preverba vira stoji v
  `LastnistvoStoritev` **pred** preverbo vloge, sporočilo pa pove, kje se
  popravi. Izjemi sta opis lige v piramidi (prehodi) in uredniška izbira lig na
  domači strani — nista pravilo tekmovanja. Uvoz sam piše mimo storitev
  (repozitoriji), zato ga pravilo ne ustavi. Vmesnik dejanja skrije
  (`smem = … && vir == null`); oznake vira pod naslovom **ni** (odstranjena
  septembra 2026 na željo lastnika).
- **Sinhronizacija s Stupo je ročna in dokazana** (`si.turnirko.uvoz.stupa`,
  stran `/uvoz`, API `/api/v1/uvoz/stupa/**` samo za `ADMIN` — poročilo nosi
  datume rojstva). Potek za eno tekmovanje:
  1. **Predogled** (`UvozStupeStoritev.predogled`): posnetek dogodka pri viru
     (`StupaOdjemalec` → mapa JSON z oznako `{id}-{yyyyMMdd-HHmmss}` in
     zgostitvijo SHA-256), preslikava in uskladitev v transakciji, ki se
     **razveljavi**.
  2. **Uvoz** (`uvozi`): ista preslikava nad **istim posnetkom** z odločitvami
     admina; transakcija se potrdi samo, če poročilo `dovoljuje()` (brez napak,
     brez nerazrešenih odločitev, vse obvezne preverbe se ujemajo), sicer je
     zagon `ZAVRNJENO`. Po potrditvi `preracunajOd(dan tekmovanja)`. Vsak zagon
     ima vrstico v `uvoz_zagon`; enak posnetek kot pri zadnjem uspehu je
     `BREZ_SPREMEMB` (razen `vsili`).
  - **Ena preslikava za sprotni in zgodovinski uvoz** (`UvozStupeStoritev.izvedi`,
    `PreslikavaTurnirjaStupe`, `PreslikavaLigeStupe`, `SrecanjaStupe`,
    `IdentitetaStupe`). Dve kopiji bi isti dogodek zapisali različno.
  - **Ponovni uvoz obdrži id-je** (`zunanja_povezava`: vir + vrsta + id pri
    viru → id v Turnirku za igralca, klub, turnir, dogodek, ligo, ekipo,
    srečanje): turnir, dogodki, lige, ekipe lige in srečanja lige ostanejo,
    vsebina pod njimi se zbriše (`CiscenjeUvoza`, nativni SQL) in zapiše znova.
  - **Uskladitev po zapisu** (`UskladitevStupe`) bere bazo po izpraznjeni seji
    in primerja z virom **tekmo za tekmo** (status, zmagovalec, nizi, brez boja,
    preneseni izid, napredovanje v mreži, posamične tekme in igralci srečanj,
    točke lestvice, zmagovalci serij). Uradna mesta v skupinah in na lestvici
    so **neobvezna** preverba: pravilo izenačenih je v Turnirku zavestno drugo
    (razlika, ne količnik — glej `RazvrstitevStoritev` in lestvico lige).
  - **Istovetnost oseb** (`IdentitetaStupe`): odločitev admina → povezava →
    licenca NTZS **samo skupaj** z datumom rojstva in spolom (licenca ni ključ
    osebe) → ime in datum rojstva (pri strogem načinu odločitev) → nov
    igralec. Stupa hrani podatke osebe **po prijavah**, ne v profilu (isti
    igralec je v enem dogodku brez datuma rojstva, ime se sproti popravlja),
    zato zgodovinski uvoz poda `ZnaneOsebeStupe` iz vseh posnetkov, sprotni pa
    osebo brez imena in priimka, datuma ali spola da v odločitev s seznamom
    `manjka` — admin podatke vpiše (`UvozZahtevaDto.Odlocitev`) ali izbere
    igralca.
  - **Zapisan samo zmagovalec je odigrana tekma z izidom 0 : 0.** Stupa pri
    nekaterih srečanjih vpiše zmagovalce podtekem brez nizov (in izida
    srečanja ne sešteje — `SrecanjaStupe.izidEkipneTekme` ga prešteje iz
    podtekem). Rating šteje samo zmagovalca (zmaga je zmaga), zato je taka
    tekma obračunana kot vsaka druga. V aplikaciji take tekme ni mogoče
    vnesti.
  - **Točkovanje lige se prebere iz uradne lestvice** (`PreslikavaLigeStupe.
    tockovanje`): pravila SNTL se med sezonami razlikujejo (2024/25 točka za
    poraz, 2025/26 ne). Išče se zmaga/neodločeno/poraz in odbitek za poraz
    brez borbe, pri katerih izkupički srečanj dajo natanko uradne točke vseh
    ekip; kadar jih ni (vir sam s sabo ni skladen, kazenske točke), ostane
    2-1-0 z odbitkom 1 in preverba točk postane neobvezna. Pri sodem številu
    tekem (SNTL z dvojicami: 10) je neodločeno dovoljeno — vir 5 : 5 zapiše
    brez zmagovalca.
  - **Izbrisana ali izključena prijava brez tekem ni udeleženec**
    (`is_deleted`, `is_excluded`): 2. SNTL M 2025/26 ima izključeno podvojeno
    prijavo kluba, ki je stala na lestvici kot »NTK Vesna (2)« z 0 srečanji.
    Izključen udeleženec, ki je igral (ekipni DP U17 2026), ostane — njegove
    tekme so odigrane.
  - **Turnir, ki se še ni začel** (kategorije brez stopenj pri viru), se sme
    uvoziti kot turnir v pripravi brez dogodkov — tekmovanja prinese ponovni
    uvoz z istim id-jem. Poročilo to pove z opozorilom, stran pa namesto
    »ujema se v vseh preverbah« izpiše, da preverb ni.
  - **Prvi uvoz turnirja sme PREVZETI prazen turnir iz koledarja**
    (`PrevzemKoledarja`, `UvozZahtevaDto.prevzemiTurnir`, okt 2026). Koledar
    sezone vpiše uradne turnirje vnaprej, preden jih zveza objavi pri Stupi;
    brez prevzema bi uvoz ustvaril drugega. Kandidat: brez vira, `PRIPRAVA`,
    brez dogodkov, začetek ±3 dni od dni dogodka, še ni povezan. Predogled
    vrne kandidate in predlog po imenu (ista kategorija U-xx in zaporedna
    številka obvezni, vrsta OT/TOP/DP/ekipno/kvalifikacije/člani šteje; pri
    neločljivih ga ni). **Odloči admin** — vmesnik ob kandidatih zahteva izbiro
    (prevzemi ali »Ustvari nov turnir«), strežnik izbranega preveri po istem
    merilu pred zagonom in še enkrat v transakciji zapisa. Prevzem ohrani id
    in kraj iz koledarja, lastnika pobriše (uvoženo ni od nikogar in ne šteje
    v mejo paketa), vse drugo zapiše preslikava iz vira. Uvožen dogodek
    kandidatov nima več. Varujeta `PrevzemKoledarjaTest`, `UvozPrevzemKoledarjaTest`.
  - **Skupine za mesta pri viru so vsaka svoja stopnja** (»1.–4.« stopnja 2,
    »5.–8.« stopnja 3); `stopnja` ostane vrstni red vira (bere ga vrstni red
    ratinga v dnevu), vmesnik pa skupine s `prvoMesto` zbere pod en naslov
    »Finalne skupine«.
- **Stran `/uvoz` (Uvoz NTZS, samo admin)** vodi sinhronizacijo s Stupo:
  sezona → tekmovanja s stanjem zadnjega uvoza → predogled (preverbe, napake,
  odločitve o istovetnosti z vpisom manjkajočih podatkov, novi igralci s
  popravkom imena, opozorila) → uvoz → dnevnik zagonov. **Dovoljenje za uvoz
  velja samo za predogled z istimi odločitvami**: vsaka sprememba odločitev
  gumb »Uvozi« ugasne, dokler se predogled ne ponovi (strežnik bi uvoz sicer
  zavrnil).
- **Klubi**: V43 je združila klube, ki sta jih uvoza zapisala dvakrat (stara
  stran in Stupa, npr. Krka in KRKA NOVO MESTO) — par je določen z
  identifikatorjema pri virih, povezava Stupe kaže na ohranjeni zapis, zato
  sinhronizacija drugega ne ustvari znova. »NAMIZNOTENIŠKI KLUB SAVINJA« iz
  Stupe NI združen (igralce deli s Savinjo Luče in Savinjo Žalec).
- **Uvoz zgodovine NTZS teče iz DVEH virov v enem zagonu** (`UvozUkaz` pod
  profilom `uvoz`; ob navadnem zagonu se ne sproži, zahteva prazno bazo):
  stara stran `stara.ntzs.si` za sezone 2012/13–2023/24
  (`si.turnirko.uvoz.stara`, navodilo `../uvoz-stara-ntzs/README.md`) in Stupa
  Events od 2024/25 naprej (posnetek `../uvoz-stupa/posnetek.ps1`, navodilo
  `../uvoz-stupa/README.md`). Vira se ne prekrivata.
  - **Stupa gre skozi ISTO preslikavo kot sinhronizacija med sezono**
    (`UvozStupeStoritev.izvedi`, samodejni način istovetnosti, `ZnaneOsebeStupe`
    iz vseh posnetkov) — dogodek za dogodkom, vsak v svoji transakciji in z
    uskladitvijo. Dnevnik `uvoz_zagon` po izgradnji pozna vse dogodke; `NAPAKA`
    pomeni, da je dogodek zapisan, a se z virom ne ujema (vredno ga je uvoziti
    znova na strani `/uvoz`).
  - **Skupen šifrant je pogoj, ne podrobnost.** Ista oseba nastopa v obeh
    virih; `SifrantiUvoz` osebe stare strani združi **po licenci NTZS** (v
    obeh virih ista oblika `059/15/16`), `IdentitetaStupe` pa osebe iz Stupe
    poveže z njimi po licenci **in** datumu rojstva. Po imenu se samodejno ne
    združuje — zlepilo bi soimenjake. Stara stran hrani ime in priimek
    **ločeno**, zato razdelitev iz registra povozi ugibanje `RazdelitevImena`.
  - **Tekmovanja se uvozijo po datumu, v enem koledarju** — turnir, kolo lige,
    turnir, kot so si sledili; ne najprej vsi turnirji in nato lige.
  - **Rating se na koncu preračuna v celoti** (`PreracunRatingaStoritev`),
    vrstni red tekem (datum in ura, faza) pa je v `VrstaRatinskeTekme` — isto
    pravilo kot pri vsakem preračunu.
  - **Datumi, ki jih ima Stupa narobe, se popravijo v KODI, ne v bazi**
    (`PopravkiStupe`, zapis `src/main/resources/uvoz/stupa-popravki.json`).
    Uvoženo tekmovanje je samo za branje in vsak ponovni uvoz vsebino zapiše
    znova iz vira — ročni popravek v bazi bi prvi uvoz povozil, napačen datum
    pa v ratingu ni kozmetika (tekma je obračunana proti napačnim številkam).
    Dve vrsti: začetek/konec **dogodka** (Stupa je ob naknadnem vnosu zapisala
    dan vnosa ali teden prej) in čas **tekme** (lige 2024/25 so bile v Stupo
    vnesene novembra 2025 brez terminov; njihovi »krogi« niso kola in ne
    tečejo po času, zato se čas ne da izpeljati iz kroga — niti iz vrstnega
    reda vnosa ne: pri 4. SNTL je ta izpeljava dala napačne pare). Za lige
    2024/25 je vir **uradni koledar NTZS** (»KONČNI KOLEDAR SNTL 2024-2025«,
    vsaka tekma s krogom in uro); natančnejši vir (zapisnik, poročilo o
    prestavitvi) ima prednost. Popravek tekme nosi tudi **kolo = uradni krog**
    (`PosnetekDogodka.koloTekme`): krog vira pri teh ligah ni kolo in stran
    lige bi v »kolu« združevala tekme z različnih datumov; prestavljena tekma
    ostane v svojem krogu z dejanskim časom. Tekma s pravim časom pri viru
    (1. SNTL moški 2024/25) dobi samo kolo. Bere ju
    `PosnetekDogodka` (`zacetek`, `konec`, `casTekme`), zato veljata pri
    zgodovinskem in sprotnem uvozu. **Vsak popravek ima obvezen `vir`**
    (koledar ali novica ntzs.si, zapisnik) — brez njega se zapis ne naloži in
    zagon pade; popravek brez vira je ugibanje. Drži `PopravkiStupeTest`.
  - **Licenca ne loči dvojčkov — ime jih.** Stupa je dvojčkoma Adam (isti
    datum rojstva, spol in klub) v treh dogodkih zamenjala licenci in uvoz ju
    je povezal navzkriž. Kadar licenca pokaže na igralca z DRUGIM imenom, ime
    z istim datumom rojstva pa natanko na drugega, `IdentitetaStupe` obvelja
    ime (strogi način da odločitev adminu); neujemanje imena pri ujemanju
    licence in datuma gre v poročilo.
  - **Stara stran: vse podlige in vse discipline.** Pretvorba
    (`pretvori.mjs`) prinese tudi pokal (PTRS), končnice (`_po`) in
    kvalifikacije (`_q`) SNTL, točke nizov ligaških tekem, srečanja brez
    borbe (`brezBoja`, izid brez tekem), tekme srečanja brez boja (rating jih
    ne šteje) in **dvojice turnirjev** — par je ena prijava (`igralec` +
    `igralec2`), kot v aplikaciji, in v rating ne gre. Starostna kategorija
    dogodka je kategorija **discipline** (`kategorijaDiscipline`: Masters ima
    pod »člani« tudi discipline mladih).
  - **Prenesen izid ekipnega DP je srečanje BREZ posamičnih tekem**
    (`srecanje.prenesen`, V39). Finalna skupina prevzame dvoboj iz
    predtekmovanja; vir ga pokaže z istim zapisnikom in uvoz ga je zapisal
    dvakrat s tekmami vred — 1.168 tekem je bilo obračunanih dvakrat. Zdaj
    šteje v lestvico skupine, tekme pa ostanejo samo tam, kjer so bile
    odigrane. Razpored lige ga označi »prenesen«.
  - **Popravek že uvožene zgodovine teče NA MESTU** (`PopravekZgodovineUkaz`,
    profil `popravek`; nad kopijo baze). Baza nosi račune, naročnine,
    spremljane lige in lastna tekmovanja, ki jih noben vir ne prinese znova,
    zato igralci, klubi, turnirji in lige obdržijo id-je, vsebina pod njimi
    pa se zapiše znova (`CiscenjeStare` za staro stran, `CiscenjeUvoza` za
    Stupo; šifrant stare strani se naloži iz povezav —
    `SifrantiUvoz.naloziObstojece`). Koraki so ponovljivi: združitve
    podvojenih igralcev (potrdi jih lastnik), povezave dvojčkov, stara stran,
    izbrani dogodki Stupe iz svežega posnetka (strogi način, potrdi se samo
    uvoz, ki ga uskladitev dovoli) in na koncu en preračun ratinga. Testi
    uvoza stare strani so v `StaraUvozTest`.
