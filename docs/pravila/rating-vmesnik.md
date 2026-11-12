# Pravila: rating vmesnik

> Rating v vmesniku: graf, profil, napoved tekme, /rating, /o-ratingu, lestvica na telefonu.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Točka grafa ratinga in vrstica v »Odigrane tekme« sta ista tekma.** Zato
  `TockaGrafa` nosi poleg nasprotnika tudi `tekmovanje` in `del` — isti zapis
  kot `TekmaProfila` (`ProfilStoritev.graf` ju prepiše iz istega `Nastopa`) —
  in klik na točko skoči na to vrstico. Brez imena tekmovanja skok rating ne
  pove ničesar: isti nasprotnik se v seznamu ponovi tudi desetkrat, ker igra
  v ligi in na turnirjih. Vez drži par `(idTekme, ligaska)`: turnirske in
  ligaške tekme imajo ločeni zaporedji id-jev, zato je ključ vrstice
  `tekma-t12` / `tekma-l7` (`kljucTekme` v `ProfilStran`). Točka brez
  `tekmovanja` para v seznamu nima (postavitveni rating) in ni klikljiva.
  Skok je **mehak samo na kratke razdalje** — pri uvoženi zgodovini je seznam
  dolg 80 000 px in mehko drsenje čez to je zabrisan blisk, ne pot.
  **Obdobje grafa krmili spustni meni** (30 dni, 3 meseci, 6 mesecev, 1 leto,
  vse; privzeto 3 meseci) — pas gumbov je z vsakim novim obdobjem rasel čez
  naslovno vrstico.
- **Graf ratinga ima osi** (okt 2026): navpično z okroglimi vrednostmi
  (`okrogleVrednosti`: korak 5, 10, 20, 25, 50, 100 …) in vodoravno z datumi
  nekaj tekem (`izbraneTekme`, prva, zadnja in enakomerno vmes — os ostane
  zaporedje tekem). Ploskev ima **mere okvirja** (ResizeObserver), ne
  raztegnjenega 1120 × 280: na telefonu je bil graf visok 84 px in brez
  oznak. Mere iz širine okvirja in ne iz `useTelefon` (ta je samo za izbiro
  drevesa). V seznamu »Odigrane tekme« je sprememba ratinga tudi na telefonu
  (pod izidom), obarvana po predznaku in ne po izidu.
- **Profil: mesto pove, na kateri lestvici je** (`ProfilDto.Uvrstitev.spol`,
  `rekreativec`): »81. od 144 · Lestvica Moški · Člani · boljši od 44 %« —
  ista imena kot izbira na strani Lestvica. Kolofon ima »Tekme na turnirjih«
  in »Ligaške tekme« v dveh vrsticah (prej »Turnirji / lige 0 / 4«, kar se je
  bralo kot štiri lige).
- **»← …« na profilu pelje tja, od koder je gledalec prišel** (`pomozno/izvor.ts`):
  `useBelezenjeIzvora` v `Postavitev` si ob vsakem PUSH zapomni prejšnjo pot
  pod ključem lokacije (sessionStorage, zato preživi osvežitev); povezava
  naredi korak nazaj v zgodovini (stanje prejšnje strani, npr. odprto okno
  ekipe, ostane), napis pa je ime lige/turnirja/igralca iz predpomnilnika
  (`oznakaPoti`). Kdor je prišel od zunaj, dobi »← Lestvica«.
- **Točka grafa ratinga nosi dva časa in nista isto.** `kdaj` je trenutek
  **obračuna ratinga** (`rating_zgodovina.ustvarjen_ob`) in po njem so točke
  urejene — vodoravna os je zaporedje obračunanih tekem, ne koledar. `datum`
  pa je dan **tekme**: datum turnirja oz. srečanja, isti kot v vrstici seznama
  tekem (`ProfilStoritev` ga vzame iz istega `Nastopa`). Izpisani datum in
  izbrano obdobje tečeta po `datum`, ker je pri uvoženi zgodovini vseh
  200 000 obračunov nastalo ob uvozu — po `kdaj` bi desetletje tekem padlo v
  en sam dan in nobeno obdobje ne bi odrezalo ničesar. Postavitev in odbitek
  dobita dan, ko je zapis začel veljati. Prazen je `datum` samo pri tekmi, ki
  ji vir datuma ne pove (`BREZ_DATUMA`, uvožene lige 2024/25 brez terminov):
  vmesnik izpiše »datum ni znan« in točko pokaže samo v obdobju »Vse«.
  **`kdaj` je NE sme nadomestiti** — pri preračunu je to današnji dan in tekma
  iz leta 2024 bi na grafu (in v privzetem obdobju treh mesecev) stala kot
  odigrana danes. Regresija je `tockaGrafaNosiDatumTekmeInNeDnevaObracuna`.
- **Sklop »Kaj prinese tekma« je napoved, ne obračun** (`NapovedTekme.tsx`,
  `NapovedTekmeStoritev`, `GET /igralci/{id}/profil/napoved?nasprotnik=`).
  Igralec izbere kateregakoli nasprotnika in vidi, koliko ratinga bi mu
  prinesla zmaga oz. vzel poraz, če bi tekmo odigrala **zdaj**. Pravila, ki
  jih ne razbij:
  - **Izida sta dva, ravni tri.** Sprememba je `K × teža × (izid −
    pričakovano)`; izid v nizih ne šteje (zmaga je zmaga), teža pa je odvisna
    od **ravni tekmovanja**, ki je pred tekmo ni. Zato `NapovedTekmeDto.Raven`
    nosi `zmaga` in `poraz`, vmesnik pa preklopnik ravni; povprečje čez
    ravni bi dalo številko, kakršne ne bi prinesla nobena prava tekma.
  - **Ravni izračuna strežnik, vmesnik ne množi s težo.** Vsak zmnožek je
    zaokrožen posebej (`Math.rint`), zato `0,75 × prikazana številka` ni to,
    kar bi tekma res prinesla.
  - **Napoved mora povzeti vse, kar `RatingStoritev` pripravi pred izračunom**
    — število tekem (K), sledilnik vrnitve in starostno sidro za igralca brez
    ratinga —, sicer napoved in obračun ne dasta iste številke. To drži test
    `napovedanaSpremembaJeTista_kiJoTekmaZaresPrinese`, ki isto tekmo najprej
    napove in nato zares odigra.
  - **Česar napoved ne posnema, pove naravnost.** Odbitka za neaktivnost ne
    uveljavlja (zapadle je že uveljavilo dnevno opravilo), uvrstitve novinca
    pa ne računa — kdor igra svoj prvi dan, dobi opozorilo `PRVI_DAN`, igralec
    brez ratinga pa `BREZ_RATINGA`. Opozorila poimenuje **strežnik**, vmesnik
    iz njih napiše poved (isto pravilo kot `ProfilDto.NacinSpremembe`).
  - **Sklop je zasebni del profila** (`Samo zate`): vidi ga igralec sam in
    admin. Lastništvo preveri `DostopDoProfila` — isto pravilo kot pri
    analizah, zato živi na **enem** mestu in ne v vsaki storitvi posebej.
- **Stran `/rating` (Rating, samo admin)** je vmesnik za obe vzdrževalni
  opravili ratinga: ponovni preračun (`POST /rating/preracun?od=`) in
  uveljavitev zapadlih odbitkov (`POST /rating/neaktivnost`). Dotlej sta
  obstajali samo kot končni točki — torej za nikogar, ki ne piše `curl`
  ukazov, preračun pa ni izjemen poseg: potreben je vsakič, ko se zaporedje
  **vnosov** razide s časom **tekem** (rezultat, vpisan za nazaj), in tega
  aplikacija ne opazi sama.
  - **Stran nosi razlago, ne samo gumba.** Pove, kaj se pokvari (napačni
    ratingi ob obračunu, vrednost na napačnem datumu v grafu, zamaknjena
    vrnitev in prvi dan novinca), kaj preračun naredi in — v svojem razdelku
    »Kdaj ga poženeš« — kdaj je potreben in kdaj ne (popravek tekme in uvoz ga
    poženeta sama). Gumb, ki rating vsem igralcem izračuna znova, je brez tega
    bolj nevaren kot koristen.
  - Prazno polje »Od dneva« pomeni **od začetka**; pred zagonom je
    `PotrditvenoOkno` z oceno trajanja, ker opravilo teče **sinhrono** (stran
    mora ostati odprta) in ob koncu razveljavi **ves** predpomnilnik poizvedb
    (`invalidateQueries()` brez ključa) — rating stoji na lestvici, v profilih,
    na karticah tekem in v napovedih.
- **Javna razlaga ratinga `/o-ratingu`** (okvir `strani/RazlagaRatingaStran.tsx`,
  koraki `komponente/RazlagaKoraki.tsx`, čista logika `pomozno/razlagaRatinga.ts`,
  razdelek »Javna razlaga ratinga« v `slog.css`; predaja
  `design_handoff_razlaga_ratinga`, smer »1a Zapisnik«, 10. 10. 2026). Za
  gledalca, ki je po enem porazu izgubil 113 točk ali se ne najde na lestvici:
  **ena zgodba v desetih korakih** (01 napoved, 02 sprememba, 03 K, 04 teža, 05
  preizkus tekme, 06 razloži mojo spremembo, 07 prvi dan novinca, 08 ko ne igraš,
  09 na kateri lestvici si, 10 zakaj …?); vsak gradi na prejšnjem in ima svoj
  preizkus. Izjema od DESIGN.md je razdelek 5d (maskota na treh mestih,
  animirani grafi, vlečenje krivulje). Sidra korakov so `#k1` … `#k10`; povezave
  nanjo: modri blok ratinga na profilu, točka uvrstitve v grafu (`#k7`), dno
  zapisnika srečanja, dno lestvice in namig iskanja (`#k9`); stara `#prvi-dan`
  in `#lestvica` stran preusmeri (`VZDEVKI_SIDER`).
  - **Številke pravil pridejo s strežnika** (`GET /rating/pravila`,
    `PravilaRatingaDto`: K, pragovi, teže, odbitki, sidra po starosti) — iz
    istih konstant, ki jih bere obračun. Na roko zapisana številka bi se ob
    naslednji umeritvi razšla z obračunom. Za deset korakov so zraven še:
    **krivulja napovedi −600…+600 po 10 kot decimalna verjetnost** (`napovedi`;
    iz nje stran nariše krivuljo, 60 stolpcev in vse odstotke), **sidra za
    starosti 8–45** (drsnik novinca), **mladinski pasovi** (`kategorije` iz
    `StarostniPas.mlajsiOd()` in `veteraniOd()` — meje pasov ostanejo na enem
    mestu), `IzracunTekmeDto.pricakovano` (nezaokrožena verjetnost: račun v
    koraku 06 izpiše K × teža × (izid − verjetnost) natanko tako, kot ga je
    izračunal obračun) in `PrviDanNovincaDto.Korak.ratingSestevek` (črtkana črta
    v koraku 07: kje bi bil novinec, če bi se prvi dan seštevalo po tekmah).
  - **Preizkusa računa strežnik po istih razredih kot obračun**
    (`RazlagaRatingaStoritev`): tekma (`GET /rating/izracun`, vrstice ravni so
    `NapovedTekmeDto.Raven`) in prvi dan novinca (`GET /rating/prvi-dan`:
    prva tekma je korak od izhodišča, od druge naprej `UvrstitevNovinca` iz vseh
    izidov dneva — ista veja kot `RatingStoritev.uvrstiAliObdrzi`). Kdor
    spremeni uvrstitev novinca v obračunu, mora spremeniti tudi preizkus:
    `prviDanPreizkusaJeTisti_kiGaObracunZaresDa` isti dan najprej preizkusi in
    nato zares odigra. Poti so GET (gost sme samo GET), nič ne zapišejo in ne
    nosijo osebnih podatkov.
  - **Vmesnik ne računa ratinga.** Primeri v korakih 02–04 so ista zmaga, ki jo
    računa strežnik (`izracun`: rating 1000 + razlika proti 1000, `tekem` 30 oz.
    K iz koraka 03); korak 06 pokliče štiri izračune (K 48/58/68/78) in išče
    številko iz zapisnika med njimi. **Edina izjema je K po številu tekem**
    (`kZa`, 61 stolpcev v koraku 03): vsota osnove in pribitkov iz pravil, ki jo
    drži `kPoTekmahSledSestaviIzPravil`. Klici se ob vlečenju zakasnijo 120 ms
    (`useZakasnjeno`), do odgovora ostane prejšnji (`placeholderData`).
    Koraki 01–04 delijo stanje (razlika, K, vrnitev) v `RazlagaKoraki`.
  - **Opazovalec korakov** (`useOpazovalecKorakov`, `IntersectionObserver`,
    `rootMargin: '-25% 0px -55% 0px'`) pove, kateri korak gledalec bere (kazalo
    ob strani od 1100 px, trak napredka v lepljivi glavi telefona prek
    `GlavaNaslov`) in katere je že videl (`videni`): ob prvem prikazu zrastejo
    stolpci krivulje in K, palice teže in lik se dvigne izza črte.
  - **Maskota** je `LikSTablo` (`MaskotaPrizori.tsx`) — isti `Lik` in `Tabla` kot
    v glavi, nepremična poza prek `Lik poza`; da se ujema z maketo (tabla je tam
    HTML škatla), `Tabla` dobi `obroba` (obroba NOTRI v škatli, ne na sredini
    roba) in `opsz` (SVG besedilo vzame optično velikost pisave iz velikosti PRED
    merilom lika, zato bi bil napis ožji ali širši od HTML besedila).
  - **Merilo ujemanja je prototip `*.dc.html` v istem brezglavem Chromu, ne
    PNG iz predaje** (glej tudi »Posnetki vmesnika« in `docs`): namizje 0,09 %,
    telefon 0,24 % različnih pikslov (glava aplikacije, anti-aliasing besedila
    in tabla maskote). Pasti, ki jih je pokazala primerjava:
    - **Prototip postreži prek HTTP** (`node` strežnik na mapi `prototip/`):
      prek `file://` se `dc-import` (maskota) ne naloži in v prototipu stoji
      siv kvadrat — brez maskote višine strani ne ujameš (korak 07 in 10 sta
      za 7,5 oz. 8 px nižja).
    - **Vrstični `span` v maketi je vrstica po telesu (17 × 1,5 = 25,5 px)**,
      ne po oznaki (16,8 px): oznake nad izbirniki (korak 05, 06, 07, 09) so
      vrstične, blokovne samo z izrecnim zamikom (`--r16`, `--r24`). Brez tega
      je stran 8,7 px nižja na vsako takšno oznako. Isto velja za ovoj maskote
      (okvir 102 px, ovoj 109,5 px).
    - **Mreža v koraku 06 ima v maketi napako v `padding`** (»10px« + »10px 0« =
      trije zneski za K, neveljaven zapis za številke): vrstice so kompaktne, K
      zamaknjen 10 px, številke ob desnem robu. Posnetek kaže prav to, zato je
      v CSS **namenoma** tako (komentar nad `.razlaga__mreza-k`). Ne popravljaj,
      dokler lastnik ne odloči drugače.
    - **Mono 700 je v aplikaciji naložen, v maketi ne** (400–600): `<strong>` v
      mono besedilu je izrecno 600 (rating v seznamu tekem prvega dne, števec
      »Odigranih tekem: 4«). K ob vrnitvi (korak 08) ima v maketi proporcionalne
      števke (`--razmerna`), drugi odčitki tabularne.
    - **Naslov strani ima koren 34 px** (razmik črk −1,19 px je `-0.035em` od
      `h1` privzetih 2 × 17 px v maketi; v aplikaciji bi bil `h1` 40 px →
      −1,4 px).
    - **Gumba v nogi sta vrstici po telesu strani** (`line-height: 1.5` →
      48,5 px, ne 44 px); kljukica se na telefonu skrči skupaj z besedilom
      (`flex: 0 1 auto`, globalno pravilo je `flex: none`).
    - **Aktivni korak ob posnetku cele strani ni merilo**: pri visokem oknu
      (okno = cela stran) opazovalec ob preoblikovanju postavi `aktiven` drugam
      kot v maketi. Ob nalaganju (vidno okno) sta kazali identična.
    - **Posnetek `telefon-celota.png` / `telefon-prvi-zaslon.png` ima večji
      naslov** (nadnaslov ~37 px, »Kako deluje« v dveh vrsticah) kot README
      (`clamp(32px,4vw,48px)` in `clamp(56px,7.4vw,88px)`) in prototip, ki ju
      pri 390 px dasta 32 in 56 px. Stran sledi README in prototipu; posnetek
      je najverjetneje artefakt okolja, v katerem je nastal.
- **Profil: »Odigrane tekme« so po tekmovanjih** (`TekmePoTekmovanjih`,
  `ProfilDto.tekmovanja`, `TekmaProfila.tekmovanjeKljuc`): kategorija turnirja
  (ključ `d<idDogodek>`, tudi ekipni dogodek) oz. liga (`l<idLiga>`) je vrstica z
  izkupičkom, spremembo ratinga in končnim mestom »5. mesto od 32« (uvrščeni v
  kategoriji, `PrijavaRepozitorij.mestaPoDogodkih`), tekme se razprejo pod njo.
  Nad seznamom izbira sezone in odličja (prva tri mesta kategorij turnirjev).
  Skok s točke grafa tekmovanje odpre (in po potrebi pokaže vse sezone) ter
  vrstico poišče šele po izrisu (`skok` + učinek). Pri ligi mesta ni (mesto je
  ekipe). Glava profila ima poleg mesta na lestvici spola še **mesto v
  mladinskem pasu** (»12. od 80 · Lestvica Moški · U19«, `mestoVPasu`, isto
  merilo kot kategorija na strani Lestvica); značka mesta ni več zelena
  (zelena je uspeh, mesto je podatek).
- **Lestvica na telefonu: ime in klub v isti vrsti, izkupička in premika ni**
  (`VrsticaLestviceMobi`, `.lestvica-mobi__igralec`; odločitev lastnika, okt
  2026 — prej sta stala »449–43 · ↑2« v drugi mono vrstici pred klubom). Ko
  vse ne gre v vrsto, popusti klub, **ime se nikoli ne odreže**: klub odreže
  tropičje, dokler mu ostane vsaj 10 znakov (`flex-basis: 10ch`), sicer gre cel
  pod ime; ime, daljše od vrste, se prelomi. Igralec brez kluba ima samo ime.
  Vrstica je visoka 49 px (`contain-intrinsic-size` na
  `.lestvica-mobi--globalna`). Lestvici lige posameznikov in dvojic ostaneta z
  mono vrstico pod imenom.
