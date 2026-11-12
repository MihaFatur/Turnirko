# Pravila: premium igralca

> Maskota in oglas Igralec Premium, preizkus Premium in poziv k računu.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Maskota »Igralec Premium« je namerna izjema od DESIGN.md** (razdelek 5b,
  odločitev lastnika 23. 9. 2026, razširitev na telefon 26. 9.): risan lik se ob
  naključnih trenutkih pojavi v glavi, na namizju IN na telefonu. Vsak nastop =
  PRIZOR (pet na namizju, štirje na telefonu) × NAPIS (pet). Prizori: `tabla` (lik z znakom nad glavo,
  poskakuje), `kukaj` (pokuka izza črte, se ozre, znak spusti na črto in se
  potopi; znak stoji, dokler ga ne povlečeta dve roki izza črte navzdol),
  `zogica` (DVE RAZLIČICI z istim `id`: na telefonu žogico odbija, pošlje iz
  platna in razgrne tablo; na namizju jo enkrat odbije, pošlje v oznako
  uporabnika »Gost · prijava«, ki se v trenutku zadetka spremeni v znak),
  `letalo` (izza logotipa vleče zastavico, ki plapola) in `lestev` (SAMO
  NAMIZJE: vrvna lestev z vrha strani nad »Lestvica«, tabla jo prekrije, lik
  pleza do sredine in pokaže palec; lastnik jo je 26. 9. na telefonu izločil,
  ker sega 176 px pod črto glave, na namizju pa ostane). Na željo lastnika
  ODSTRANJENI, ne vračaj jih: `padec`, `ples`, `prevrat`. Datoteke: `komponente/Maskota.tsx`
  (gostitelj, urnik), `MaskotaPrizori.tsx` (lik, tabla, letalo, izris),
  `pomozno/prizoriMaskote.ts` (prizori), `animatorMaskote.ts` (predvajalnik),
  `umestitevMaskote.ts` (merjenje glave, mere po napravi, poti, prileganje),
  `vrecaMaskote.ts` (izbira), `napisiMaskote.ts`, `urnikMaskote.ts`, razdelek
  »Maskota« na koncu `slog.css` (samo statični slogi). Pravila, ki jih ne
  razbij:
  - **Platno je VEDNO čez celo glavo** (namizni masthead ali 56 px visoka
    vrstica telefona; `.maskota` je absolutna, `top: 0; left: 0`; pri lestvi
    je višje in sega 192 px od vrha strani, torej čez vsebino pod glavo —
    dekorativno, ~3 s, samo namizje), lik pa stoji
    na tanki črti pod vrstico (`Glava.tla`; vse pod njo platno odreže, zato se
    lik »potopi« za črto). Nič ni vezano na fiksne koordinate: `izmeriGlavo()`
    prebere logotip (na telefonu tudi puščico nazaj), navigacijo, »Domov«,
    oznako uporabnika in črto; `sestaviNastop` vrne `null`, če se prizor ne
    prilega (preozko okno, predolg napis, druga naprava), in gostitelj izbere le
    med tistimi, ki se. Na telefonu `izmeriGlavo` vrne `null` tudi, ko glava ne
    kaže oznake uporabnika (podstran s puščico nazaj) ali ko stran vanjo vloži
    dejanja (`.glava-telefon__dejanja` ni prazen): tam je prostor zaseden.
  - **Mere lika po napravi** (`MERA_LIKA`): namizje merilo 0,92, napis 13 enot,
    tabla 19; telefon merilo 0,75, napis 16 enot, tabla 22 (glava je 56 px
    proti 68, besedilo pa je v obeh ~12 px — najmanjša velikost po DESIGN.md).
    Širina besedila se meri enkrat pri 13 enotah, na telefonu se preračuna.
    Zastavica letala je na obeh napravah 0,92 / 13.
  - **Na telefonu oznaka uporabnika med nastopom izgine**: sled `oznaka`
    (`opacity` + `visibility`, da skrita ne ujame klika) animira element IZVEN
    platna (`ZUNANJI` v animatorju: `.uporabnik-gumb`); `zOznako()` jo doda
    vsakemu prizoru na telefonu. Na namizju jo skrije le prizor `zogica`, in
    to v trenutku zadetka. Element se ob koncu (ali odmontiranju platna)
    sam vrne: `ustavi` prekliče vse animacije.
  - **Letalo**: okno je med desnim robom logotipa in levim robom »Domov«
    (telefon: do roba zaslona), odrezano s `clipPath` — zdi se, da se pokaže
    izza logotipa in izgine za povezavo. Če je okno široko za letalo IN
    zastavico, je letalo ves čas v celoti vidno; če le za zastavico (telefon, ozko
    namizno okno, pod ~1150 px), letalo, ko je zastavica cela vidna, že zapušča
    okno. Zastavica mora biti vsaj ~2,5 s cela vidna, sicer se napis ne prebere.
  - **Zastavica plapola s pasovi** (`val0…`, cikli): isti napis je izrezan na
    ozke pasove (~12 enot), vsak niha po višini z zamikom faze. Širši pasovi so
    ob valu videti kot stopnice v napisu.
  - **`lestev` (samo namizje)**: lik je otrok lestve (jo potegne s sabo pri
    dvigu), tabla je zunaj nje in pred njo, zato lik med plezanjem za njo
    izgine in izpod nje spet pride. `potLestve` vrne `null` na telefonu in
    ko tabla ne gre v glavo. Palec je PRAVOKOTEN na podlaket in roka
    iztegnjena vstran: nadaljevanje podlakti bi ob dvignjeni roki zašlo za
    tablo. Lestev je edini prizor, ki sega pod črto glave.
  - **`kukaj`: tabla ostane, ko se lik potopi.** Tabla je otrok telesa
    (`figura`), zato se njen odmik izniči z odmikom telesa (y table = 44,5 − y
    telesa). Ključi table in telesa v intervalu spusta in potopa morajo biti
    ISTI (isti časi in `e`): dodatni ključi na eni sledi bi pojemanje razrezali
    in tabla bi zdrsnila. Roki, ki jo povlečeta (`vlekL/D`), in tabla gresta
    navzdol z istimi ključi. `tablaNaprej`: tabla je izrisana za rokami, zato
    roke za njo izginejo, ko jo lik spusti.
  - **`zogica` na namizju**: pot žogice do sredine oznake je parabola,
    vzorčena v linearnih odsekih (ne eno pojemanje); znak (`znak`, desni rob in
    sredina = oznaka) ima do zadetka `visibility: hidden`, sicer bi ujel klik
    namesto oznake; lik stoji `LIK_LEVO_OD_ZNAKA` levo od znaka.
  - **Cikli** (`Cikel`): neskončna kratka gibanja (propeler, zastavica) so
    ločena od ključev, ker bi bili sto ključev na sekundo. Pojemanje je na
    ključih cikla, ne na možnosti `animate` (ta bi zgladila celoten cikel).
  - **Inline SVG + Web Animations API, brez knjižnice in brez keyframes v
    CSS.** Prizor je PODATEK: ključi po delih lika (`figura`, `rokaL`, `tabla`,
    `oko` …), čas 0–1; del, ki polje izpusti, obdrži prejšnjo vrednost. Ključi
    so po delih in ne skupne poze, sicer bi mežikanje vsiljevalo vmesne
    stopnje poskakovanju. Animira se samo `transform` in `opacity` (poleg tega
    `visibility` pri oznaki in znaku). Sklepi lika so vrtišča: zunanja skupina
    (`transform` kot atribut) postavi izhodišče v sklep, notranja (`data-del`)
    se vrti okoli svojega (0, 0).
  - **Noge ne dobijo lastnega sleda**: `izpeljiNoge(y)` jih naredi iz ključev
    telesa z istimi časi in pojemanjem, da stopala ostanejo na tleh (ključ
    telesa jih sme prepisati z `noge`). Nikoli ne preračunavaj nog na roke.
  - **Tabla se prilagodi napisu.** Širino izmeri `izmeriNapis` (canvas, po
    izrecnem `document.fonts.load` — šumniki so v podmnožici `latin-ext`;
    izmera je zapomnjena), nagib table pa omejen glede na širino in prostor
    nad likom (`sestaviNastop`): širša tabla ima pri istem kotu višji vogal in
    ta ne sme uiti čez rob platna.
  - **Napis se bere najmanj ~2 s** (dolg napis ~28 znakov), vsak prizor ostane
    pod 5 s. Ob novem prizoru to preveri po sličicah.
  - **Cena v napisu je `{cena}`**, izračunana iz `CENA_PREMIUM_MESECNO` po
    gledalcu (`cenaPremium`): mlajši od 21 let nižja (1,99 €), sicer višja (2,99 €), gost višja.
    Nikoli vtipkana številka.
  - **Izbira je VREČA, ne kocka** (`vrecaMaskote.ts`): prizori in napisi so v
    dveh premešanih vrečah in se vlečejo brez vračanja, zato se vsak pokaže
    enkrat na krog. Čista naključnost je statistično enakomerna, na oko pa ne
    (gledalec vidi 1–3 nastope: pri 12 zaporednih ogledih je razlika med
    najpogostejšim in najredkejšim prizorom v povprečju 3,6, pri vreči 0).
    Vreča se hrani PO NAPRAVI v `localStorage['turnirko-maskota-vreca-namizje'
    | '-telefon']` (nabora prizorov nista enaka: lestev je samo na namizju),
    zato krog teče prek nalaganj (osvežitev ne začne z novim naključnim
    prizorom); brez shrambe živi v pomnilniku. Kar se v ozko okno ne prilega, ostane v vreči za
    naslednjič in se ne izgubi (`izberiPar` išče prvi par, ki se prilega).
    Predogled (`?maskota`) vreče ne uporablja.
  - **Gost in igralec brez Premium, na namizju in telefonu, največ 3 nastopi
    na nalaganje strani** (prvi po 10–25 s, nato po 60–80 s; osvežitev
    zaporedje začne znova, števec je spremenljivka v modulu in ne
    `sessionStorage`), ne ob skritem zavihku, odprtem
    oknu (`#koren[inert]`), odprtem uporabniškem meniju ali kadar se prizor
    ne prilega glavi (`jePrimernTrenutek`, `sestaviNastop`). Ne dodajaj je na
    strani urejevalcev, dokler lastnik ne odloči drugače.
  - **`prefers-reduced-motion` nastopa NE ustavi** (odločitev lastnika,
    28. 9. 2026; velja za glavo in za lik v tabeli paketov). Windows jo
    brskalniku sporoči, kadar so izklopljeni »Učinki animacije« — privzetek
    šibkejših prenosnikov, šolskih računalnikov, RDP in virtualk —, zato
    maskote na mnogih računalnikih ni bilo nikoli, niti s `?maskota`. Pogoja
    ne vračaj; zato mora vsak prizor ostati pod 5 s (WCAG 2.2.2).
  - **Dekorativna**: `aria-hidden` in `tabIndex={-1}`; dostopna pot je meni
    »Naročnina«. **Klik gosta odpre registracijo NARAVNOST NA KORAKU »PAKET«**
    z izbranim Igralcem Premium za eno leto (`PrijavaOkno zacetniNacin=
    "registracija" naPaketu` → `RegistracijaTok naPaketu`; `/narocnina` gosta
    pošlje nazaj, prijava ni prvi korak). Prijavljen igralec brez Premium gre
    na `/narocnina`.
  - **Lik v tabeli paketov** (`komponente/MaskotaTabela.tsx`,
    `pomozno/prizorTabela.ts`): na koraku »Paket« (samo igralec, samo namizje)
    isti lik pade z vrha tabele, skače po vrsticah, ki jih ima le Premium, in
    ob vsaki pokaže z roko; okvir okoli celice Premium se pojavi z njim. Igra
    ga isti predvajalnik (`predvajaj<K>` je generičen po kontekstu), prizor je
    funkcija IZMERE (`izmeriTabelo`: tla vrstic, konec besedila vrstice, stolpec
    Premium) in se ne pokaže, če se ne prilega (prelomljena vrstica, preozko
    okno). Odmiki telesa so merjeni od tal PRVE vrstice; noge so na vsakem
    ključu izrecne (`noge`), ker izpeljava iz telesa zaradi velikih odmikov ne
    velja. En nastop na odprto okno, pod 5 s, platno `pointer-events: none`.
    Ob spremembi vrstic tabele preveri po sličicah (stopala na črti vrstice,
    okvir na celici, potop do konca izven spodnjega roba).
  - **Predogled brez čakanja: `/?maskota`**, `?maskota=<prizor>` in
    `&napis=<1..5>` (prizori: `tabla`, `kukaj`, `zogica`, `letalo`, `lestev`;
    neznan ali na napravi ne obstaja = katerikoli). V skritem zavihku urnik pravilno počaka (`document.hidden`),
    animacijska ura tam ne teče — za pregled po sličicah ustavi animacije
    (`getAnimations()`, `pause()`, `currentTime`); za pikslovne sličice glave
    (telefon 375 px, dpr 3) je najboljši brezglavi Chrome prek CDP.
  - Nov prizor = zapis v `PRIZORI` (ključi po delih, `trajanjeMs`, `naprave`);
    če rabi rekvizit, ga doda izris v `Lik`. Nov napis = zapis v `NAPISI`.
    Vreča se ob spremembi nabora sama začne znova.
- **Oglas »Igralec Premium« je celozaslonski oglas ob kliku na zaklenjeno funkcijo**
  (izjema od DESIGN.md, razdelek 5c, predaja `design_handoff_premium_popup`, 29. 9.
  2026): `komponente/PremiumOglas.tsx` (šest postavitev: 1a Zavesa, 1b Ključavnica,
  1c Končni izid × namizje/telefon pod 768 px), `PremiumOglasKontekst.tsx`
  (ponudnik v `main.tsx`: `usePremiumOglas().odpri('statistika')`, izbira
  različice z `Math.random()`, okno registracije/prijave za gosta živi tam),
  `PremiumOglasPrizori.tsx` (lik in prizor ključavnice), `pomozno/premiumOglas.ts`
  (besedila, cene), `pomozno/animacijaOglasa.ts` (gibanje), slogi v razdelku »Oglas
  Igralec Premium« na koncu `slog.css`. Pravila, ki jih ne razbij:
  - **Sproži ga samo klik**, nikoli urnik. Sprožilec je obvestilo na lastnem profilu
    (`lastnikBrezPremium`, kontekst »statistika« — edini, ki je ostal). Spremljanje
    lig je od 23. 10. 2026 brezplačno, zato kontekst »lige« in `ponudiPremium` v
    `useSpremljanjeLig` nista več; »Spremljaj« in »Sledi ligi →« gostu odpreta kratko
    registracijo (glej »Obvestila po kolu«).
  - **Gost**: »Ustvari račun · Premium …« odpre registracijo na koraku »Paket« z
    izbranim ciklom (`PrijavaOkno zacetniCiklus` → `RegistracijaTok`); povezava odpre
    prijavo. **Igralec**: »Nadgradi na Premium · …« gre v Stripe Checkout
    (`placilaApi.nadgradnja`, isto kot stran Naročnina), povezava na `/narocnina`.
  - **Stikalo »Mlajši od 21?« je samo pri gostu** (in pri računu brez znane starosti,
    `starejsiOd21 === null`): prijavljenemu igralcu pas določa račun in po njem Stripe
    računa, zato bi stikalo pokazalo ceno, ki je ne bo plačal.
  - **Socialni dokaz je pravo število** (`PremiumDokazStoritev`, `GET /premium/dokaz`,
    javna pot): veljavne Premium naročnine igralcev; klub prijavljenega ima prednost,
    sicer skupno število; pod `NAJMANJ` (3) ali brez števila vrstice sploh ni. Ime
    igralca s Premium ne gre navzven — kvadratki so brez črk (predaja jih ima z
    izmišljenimi začetnicami), glagol se ujema s števnikom (`besediloDokaza`).
  - **Merilo ujemanja je prototip v istem brezglavem Chromu, ne PNG iz predaje**: DOM
    škatle in besedila se ujemajo do 0,05 px (razlika v 1–2 pikslih besedila je
    anti-aliasing). Pasti, ki so jih odkrili prvi zagoni: prototip je `content-box`
    (višina glave 72 + 1 px črta = 73, kvadratek 20 + 2×2 = 24), razmik črk naslovov
    (`h2`) je v pikslih (`em` od privzete velikosti `h2`), `.premium-oglas button`
    ne sme postaviti `font-family` (bi povozil `font` razreda), `h1–h3` imata globalno
    `text-wrap: balance` (na telefonu ga prototip nima), drsno območje 1c na telefonu
    ne sme stiskati tabele (`flex: none`).
  - **Števec izida v 1c sešteva razmike** (400 ms, nato 4×210 in 3×170); prototip ima
    izraz `i * (i < 5 ? 210 : 170)`, ki šesto točko postavi 10 ms za peto.
  - **Pri `prefers-reduced-motion: reduce` je takoj končno stanje** (drugače kot
    maskota v glavi). `predvajajOglas` prekliče samo svoje elemente (`data-anim`), ker
    maskota in ključavnica zaženeta svoje učinke v otroku pred njim.
  - **Predogled**: `/?oglas=1a|1b|1c`; `&dokaz=412` nadomesti
    število samo v `npm run dev`.
- **Preizkus Premium, poziv k računu in »To si ti?«** (odločitev lastnika 23. 10. 2026,
  DESIGN.md 5f). Gostje si računa niso delali, ker brezplačen račun ni dal nič več kot
  gostovanje; zdaj ima prvi mesec Premium brez plačila.
  - **Preizkus je status naročnine `PREIZKUS`** (V49; prezidava tabele zaradi CHECK):
    Premium brez Stripa, velja do `trenutno_obdobje_do` (`Narocnina.jeVeljavna`), potek
    ne potrebuje opravila. **Začne se ob POVEZAVI računa z igralcem**
    (`RegistracijaStoritev.poskusiSamodejnoPovezavo` in `RacuniStoritev.potrdi` →
    `PreizkusPremiumStoritev.zacni`), ne ob registraciji: brez igralca Premium nima česa
    pokazati in mesec bi tekel med čakanjem na admina. **Enkrat na račun** — kdor vrstico
    v `narocnina` že ima (tudi potekel preizkus ali plačilo), ga ne dobi, tudi po
    razvezavi. Nakup med preizkusom je dovoljen (zavora `zavrniCeJeNarocninaZiva` gleda
    samo Stripove naročnine) in vrstico prepiše v AKTIVNA; plačano obdobje teče takoj.
    **Stripovega `trial_end` namenoma ne uporabljamo**: Moj Račun iz vsakega Stripovega
    plačila izda račun in ni preverjeno, kaj stori z 0 € računom ob začetku preizkusa.
  - **Socialni dokaz preizkusov ne šteje** (`najdiPremiumIgralce` gleda AKTIVNA in
    PREKLICANA): število pove, koliko jih plačuje.
  - **Obvestilo o koncu** (`posljiOpomnike`, dnevno ob 10.00, `opomnik_poslan_ob`):
    enkrat, ko so do konca ≤ 3 dnevi; račun z naslovom skrbnika (mlajši od 15 let) ga ne
    dobi (sporočilo navaja ceno), zabeleži pa se, da se ne preverja vsak dan.
  - **Testi, ki po povezavi ročno podelijo Premium**, vrstico preizkusa prepišejo
    (`findByUporabnikId(...).orElseGet(...)`), ne ustvarijo druge — `id_uporabnik` je
    UNIQUE. Regresije: `PreizkusPremiumTest`.
  - **Poziv** (`komponente/PozivRacuna.tsx`, pravila `pomozno/pozivRacuna.ts`, živi v
    `PremiumOglasPonudnik` skupaj z oglasom in okni gosta): gost, 30 s od nalaganja,
    pokaže se ob NASLEDNJI spremembi `pathname` (ne iskalnih parametrov — filtri jih
    spreminjajo) po ~0,7 s; nikoli ne ujame klika. Premor 7 dni po prvem zaprtju, nato
    30; enkrat na sejo; ne na `/narocnina`, `/registracija`, `/pogoji`, upravnih in
    tiskanih straneh. Po pozivu maskota do konca seje ne nastopi (`urnikMaskote`
    `jePrimernTrenutek`). Predogled: `/?poziv`.
  - **Poskus A/B**: `skupinaPoziva()` (`pomozno/statistika.ts`, `localStorage`) — okno
    dobi `DELEZ_Z_OKNOM` (0,5) gostov. Seja v Umamiju nosi skupino (`identify`), vsak
    dogodek lastnost `poziv`.
  - **Kratka registracija** (`RegistracijaTok kratko`): brez »Vloga« in »Paket«; v
    povzetku vrstica Paket nima »Uredi«. Izjema je izvor `spremljanje`: tam ima korak
    »Vloga« z izbiro Igralec / Navijač (ligo spremlja tudi, kdor v njej ne igra), polna
    registracija pa ponudi tri vloge (`MOZNOSTI_VLOGE`, seznam namesto mreže). Navijač
    ima korake vloga → podatki → [skrbnik] → povzetek → koda: brez paketa, kluba,
    zapisa in kode popusta, besedilo o mesecu Premium mu ne pripada. »To si ti?« (`ProfilStran`, samo gost) poda še
    `predizbran` (ime, priimek, ime kluba → id iz šifranta, enkrat). Okno odpre
    `usePremiumOglas().odpriRegistracijo({ izvor, predizbran })`.
  - **Dogodki Umami** (`zabelezi`, brez osebnih podatkov; vrsta počaka na `defer`
    skript do 10 s): `poziv-prikazan`, `poziv-izid`, `to-si-ti-klik`,
    `registracija-zacetek|korak|placilo|potrjena|opuscena` (lastnost `izvor`: meni,
    prijava, maskota, oglas, poziv, profil), `premium-nakup-zacetek`. Nakup sam je ogled
    `/narocnina?stanje=uspeh`.
