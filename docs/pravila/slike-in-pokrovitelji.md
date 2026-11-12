# Pravila: slike in pokrovitelji

> Slike in logotip tekmovanja, profilna slika, pokrovitelji.
> Premaknjeno dobesedno iz `CLAUDE.md` (12. 11. 2026).

- **Slike in logotip tekmovanja** (V54, okt 2026; `SlikeStoritev`, `SlikeKontroler`,
  `pomozno/ObdelavaSlik`). Organizator ligi in turnirju doda logotip, ki stoji v glavi
  strani, na Turnirkovem ekipnem zapisniku, na žrebu skupin in mreže za tisk in v
  predogledu povezave. Pravila, ki jih ne razbij:
  - **Dva koraka: knjižnica, nato izbira.** `POST /slike` (multipart, polje `datoteka`)
    sliko shrani, `PUT /lige|turnirji/{id}/logotip {idSlika}` jo izbere, `DELETE` na isti
    poti odstrani. Liga je vsako sezono nov zapis — Savinja A–D logotip naloži enkrat.
    Knjižnica (`GET /slike`, ni javna: pravilo pred »GET je javen«) je adminu vse,
    organizatorju njegove slike in logotipi tekmovanj, ki jih sme urejati (tudi klubskega
    kolege). Izbris (`DELETE /slike/{id}`) samo svoje in samo, dokler je ne nosi nobeno
    tekmovanje.
  - **Logotip je prikaz, ne pravilo** (kot skriti razdelki Zanimivosti): lastnik in admin,
    v vsakem stanju in tudi pri uvoženem tekmovanju (`preveri…ZaPrikazPoId`). Kvalifikacije
    dobijo logotip višje lige ob nastanku.
  - **Tujo sliko sme organizator uporabiti samo, če je že javna** (nosi jo kakšno
    tekmovanje) — id-ji so zaporedni, ugibanje ne sme odpreti neobjavljene slike. Ista
    datoteka, naložena znova, je ista slika (zgostitev je UNIQUE) in gre v knjižnico
    zadnjega, ki jo je naložil.
  - **Strežnik vsako sliko prebere in zapiše znova** (`ObdelavaSlik`): vrsto določi
    vsebina (samo PNG in JPEG — SVG pretvori brskalnik, ker lahko nosi skript), meri se
    preverijo iz glave PRED dekodiranjem (bomba), metapodatki (EXIF, GPS) ne pridejo v
    bazo. Tri različice v `slika_razlicica`: TISK (≤ 1200 px), ZASLON (≤ 480 px),
    DELJENJE (1200 × 630 na papirju — prosojen PNG bi WhatsApp v temnem načinu pokazal na
    črnem). Neprosojna velika slika gre v JPEG.
  - **Bajti so v bazi, v ločeni tabeli** (`slika` je opis, `slika_razlicica` bajti):
    nočna kopija kopira samo `turnirko.db`, zato so slike v njej brez nove nastavitve na
    strežniku. Liga in turnir nosita `logotip` kot **EAGER** `Slika` (samo opis):
    `TurnirDto` nastane v kontrolerju izven transakcije. Ob prenovi na PostgreSQL gre
    `vsebina` v `bytea`.
  - **Javna pot je `/api/v1/slike/{zgostitev}?velikost=`** z `Cache-Control: immutable`
    za leto dni in ETag — ista zgostitev je vedno ista vsebina, zamenjava logotipa je nov
    naslov. Odgovor nosi `Content-Security-Policy: default-src 'none'; sandbox`. Ko bo
    stran dobila CSP (V12), mora `img-src` dovoliti `blob:` (predogled v oknu).
  - **Pripravo pred pošiljanjem dela brskalnik** (`pomozno/slike.ts`): obrez praznega
    roba (prosojnega ali skoraj belega), po izbiri odstranitev belega ozadja, povezanega z
    robom (bela v grbu ostane; mehki robovi dobijo delno prosojnost), pomanjšanje v
    korakih na 1200 px, PNG. Okno `LogotipOkno` pokaže logotip na papirju in v sivinah na
    belem (črno-beli tiskalnik) in opozori, ko bi bil na zapisniku pod 200 dpi
    (`dpiNaTisku`).
  - **Velikost na strani je po enaki ploščini** (`meraLogotipa`, `LogotipTekmovanja`):
    pri enaki višini bi bil širok napis trikrat večji od kvadratnega grba. Logotip je
    okras (`alt=""`), ime tekmovanja stoji ob njem.
  - Omejitev: 30 nalaganj na račun na uro, 8 MB na datoteko (413 s slovenskim
    sporočilom). Testi: `ObdelavaSlikTest`, `SlikeStoritevTest`, `SlikeDostopTest`,
    `PredogledStraniTest`, `PredogledStoritevTest`.

- **Profilna slika igralca** (V56, okt 2026; `ProfilneSlikeStoritev`,
  `ProfilneSlikeKontroler`, `ProfilnaSlikaOkno`, `SlikaIgralca`). Fotografija igralca z
  Igralcem Premium stoji na profilu, v 1 na 1 in na stopničkah dogodka — nikjer
  drugje (lestvica, seznami, tisk). Pravila, ki jih ne razbij:
  - **Javna je samo, če so izpolnjeni vsi štirje pogoji hkrati**: stanje
    `OBJAVLJENA`, račun potrjen in aktiven, povezan z igralcem in **veljaven Premium**
    (`NarocninaStoritev.imaPremium`, tudi preizkus). Ob izteku se skrije, shranjena
    ostane; ob ponovnem Premiumu se spet pokaže. Pogoj se preverja pri BRANJU
    (`vidne`), ne ob nalaganju.
  - **Čaka na admina** (`CAKA`), kadar je igralec mlajši od 15 let
    (`RegistracijaStoritev.STAROST_SKRBNIKA`; 1. 1. 1900 = neznano = odrasel) ali kadar
    računa ni povezal admin (`VirPovezave != ADMIN`). To je namerna zapora namesto
    popravka V3 v registraciji (orakelj datuma rojstva + prevzem identitete): kdor bi
    prevzel tuj zapis, javne slike z njim ne dobi. Admin dobi e-pošto
    (`PostaStoritev`, povezava `/igralci?slike=1`) in slike potrdi ali zavrne v pregledu
    na strani Igralci (`PregledProfilnihSlik`: `GET /profilne-slike`, `POST
    /profilne-slike/{id}/objavi`, `DELETE /profilne-slike/{id}`). Objavljene slike zadnjih
    30 dni sme admin še odstraniti.
  - **Strežnik sliko vedno obdela sam** (`ObdelavaSlik`): le PNG/JPEG, dimenzije iz glave
    pred dekodiranjem, kvadrat (min. 160 px), EXIF in GPS ne pridejo v bazo, zapis v dveh
    JPEG velikostih (VELIKA 480, MALA 160) v `profilna_slika_vsebina`. Brskalnik izrez
    naredi sam (`pomozno/profilneSlike.izrezi`, 960 px). Ista datoteka na drugem računu
    se zavrne (zgostitev je UNIQUE). Omejitev: 20 nalaganj na uro na račun.
  - **Javne bajte streže `GET /api/v1/slike/profil/{zgostitev}?velikost=`** — zgostitev
    je dostop (SHA-256 vsebine, ne ugibljiva), a odgovor nosi `max-age` samo en dan, NE
    `immutable` kot logotipi: izbris in odstranitev se morata razširiti. Zgostitev
    čakajoče slike vidita samo lastnik in admin.
  - **Brisanje prek entitet in `flush()`** (`pobrisi`): množični JPQL `DELETE` pusti v
    seji zastarele entitete, SQLite pa izbrisanemu id-ju da isti id novi vrstici
    (`NonUniqueObjectException`). Isto velja za pokrovitelje.
  - Vmesnik: `useSlikeIgralcev(idji)` je **ena zahteva za vse** (`GET /igralci/slike?idji=`,
    največ 100); okvir dobijo vsi igralci v 1 na 1 in na stopničkah, kakor hitro ga ima
    eden (obris glave in ramen namesto slike). Stopničke imajo sliko samo pri
    posamičnih dogodkih (par in ekipa nimata ene fotografije). Testi:
    `ProfilneSlikeStoritevTest`, `SlikeDostopTest` (`profilnaSlikaVlogeInPredpomnjenje`).
- **Pokrovitelji lige in turnirja** (V56; `PokroviteljiStoritev`, `PokroviteljiKontroler`,
  `PokroviteljiOkno`, `PokroviteljiTrak`, `PokroviteljiTisk`). Največ 8, eden glavni,
  vrstni red seznama je vrstni red prikaza. `GET` je javen, `PUT
  /lige|turnirji/{id}/pokrovitelji` (`{pokrovitelji:[{idSlika, ime, povezava, glavni}]}`) sme
  lastnik in admin, tudi pri uvoženem tekmovanju (prikaz, ne pravilo).
  - **Seznam se shrani v celoti**: najprej se preveri vse (ime 1–80 znakov, ena sama
    glavna, logotip iz knjižnice), šele nato se stari izbrišejo in novi zapišejo — napaka
    ne sme pustiti tekmovanja brez pokroviteljev.
  - **Povezava je samo `http(s)`**: vnos brez sheme dobi `https://`, vse druge sheme
    (`javascript:`, `data:`) se zavrnejo (`SHEMA` proti `POVEZAVA`; test je ujel
    `javascript:alert(1)`, ki je prej dobil predpono). Vmesnik povezavo odpre z
    `rel="noopener noreferrer nofollow sponsored"`.
  - **Logotipi so iz iste knjižnice kot logotip tekmovanja** (`SlikeStoritev.zaUporabo`):
    tujo sliko sme organizator uporabiti samo, če je že javna; knjižnica zdaj vključuje
    tudi logotipe pokroviteljev tekmovanj, ki jih sme urejati. `steviloUporab` šteje tudi
    pokrovitelje — slike, ki jo nosi pokrovitelj, ni mogoče izbrisati.
  - Trak na strani je kolofon (pod vsebino, glavni večji); na tisku je vrstica v nogi
    Turnirkovega ekipnega zapisnika in žreba mreže (največ 12 mm, **list ostane ena
    ležeča stran A4**: mreža pri pokroviteljih skrči višino vrstice, zapisnik pa
    razdeli, kar ostane). Ni na uradnem obrazcu NTZS, sodniških listkih in listkih
    skupin. Testi: `PokroviteljiStoritevTest`, `SlikeDostopTest`
    (`pokroviteljiBranjeJavnoPisanjeLastnik`).

