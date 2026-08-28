# DESIGN.md — Turnirko

Zavezujoč oblikovalski sistem za vmesnik Turnirka. Velja za **vsako** novo stran,
komponento in popravek. Če predlog nasprotuje temu dokumentu, se popravi predlog,
ne dokument.

Značaj: **športni zapisnik, tiskan na papir.** Rezultati so veliki, vse drugo je
tipografija in črte. Nič ne lebdi, nič ne sveti, nič se ne blešči.

---

## 1. Pisave

Tri družine, nič več. Naložene iz Google Fonts v `index.html`.

| Vloga       | Družina                 | Teže                | Uporaba |
|-------------|-------------------------|---------------------|---------|
| **Display** | `Bricolage Grotesque`   | 200, 400, 600, 800  | `h1`–`h3`, rezultati, velike številke |
| **Body**    | `Karla`                 | 200, 300, 400, 600, 700 | odstavki, imena igralcev, gumbi, polja |
| **Mono**    | `IBM Plex Mono`         | 400, 500, 600       | oznake, ELO, datumi, tabelarične številke, značke |

```css
--pisava-display: 'Bricolage Grotesque', sans-serif;
--pisava-telo: Karla, sans-serif;
--pisava-mono: 'IBM Plex Mono', monospace;
```

### Nikoli ne uporabi

**Inter, Roboto, Open Sans, Arial, Helvetica, system-ui, -apple-system, Lato,
Montserrat, Poppins, Nunito, Segoe UI, sans-serif brez določene družine.**
Tudi ne kot »varnostno rezervo« v `font-family` — rezerva je `sans-serif` ali
`monospace`, brez vmesnih generičnih imen.

### Pravila tipografije

- **Teža je binarna: 200 ali 800.** 400 in 600 obstajata samo za telo besedila
  in gumbe. Naslov nikoli ni 600 — je 800, njegov nadnaslov je 200.
- **Skoki v velikosti so ~3×, ne 1,5×.** Nadnaslov 44–48 px (teža 200) stoji nad
  naslovom 80–104 px (teža 800). Meta vrstica je 12 px mono. Vmesnih stopenj
  (24, 28, 32 px za isti element) ne dodajaj.
- `letter-spacing: -0.03em` do `-0.05em` na vsem, kar je ≥ 40 px display.
  `letter-spacing: 0.08em`–`0.14em` in `text-transform: uppercase` na vsem
  12 px mono.
- Vse številke, ki se berejo v stolpcu: `font-variant-numeric: tabular-nums`.
- Odstavki: `line-height: 1.5`–`1.6`, `max-width: 46ch`–`74ch`, `text-wrap: pretty`.
- `line-height: 0.88` na dvovrstičnih naslovih, `0.82`–`0.85` na velikih številkah.

### Tipografska lestvica

```css
--tip-mono:      12px / 500 / 0.1em      /* oznake, značke, meta */
--tip-drobno:    14px / 300              /* pripis pod vrstico */
--tip-telo:      17px / 300              /* odstavek */
--tip-vrstica:   18px / 400 ali 600      /* ime v tabeli */
--tip-uvod:      20px / 300              /* podnaslov strani */
--tip-h3:        24px / 800              /* naslov skupine, modalno okno */
--tip-h2:        40px / 800              /* naslov sekcije */
--tip-h1-nad:    44–48px / 200           /* nadnaslov */
--tip-h1:        80–104px / 800          /* naslov strani */
--tip-rezultat:  96–112px / 800          /* ELO, izid srečanja */
```

Najmanjša velikost besedila v vmesniku je **12 px** (samo mono oznake), za
običajno besedilo **14 px**. Zadetkovna površina na dotik nikoli pod **44 px**.

---

## 2. Barve

**Ena dominantna + en poudarek.** Vse kot CSS spremenljivke v `slog.css`.

```css
:root {
  /* dominantna — modra: povezave, palice, primarni gumb, ELO blok, graf */
  --barva-glavna:        #0088CE;
  --barva-glavna-tekst:  #0071AB;  /* isti ton, berljiv na papirju */
  --barva-glavna-mehko:  #EAF3F9;  /* podlaga označene vrstice */

  /* poudarek — zelena: napredovanje, zmage, značke, poudarjene črte */
  --barva-poudarek:      #7BB900;
  --barva-poudarek-tekst:#4E7A00;  /* zelena za besedilo (na papirju) */
  --barva-poudarek-mehko:#EAF4D6;

  /* semantično negativno — edini tretji ton, samo za izgube/napake/brisanje */
  --barva-negativna:     #B23A1E;
  --barva-negativna-mehko:#F8E5DF;

  /* papir in črnilo */
  --barva-papir:         #FBF9F5;  /* podlaga vsebine */
  --barva-namizje:       #DCD8D1;  /* okoli vsebine (platno, ozadje okna) */
  --barva-crnilo:        #14110F;  /* besedilo, 3px črte */
  --barva-crnilo-2:      #332E29;  /* odstavki */
  --barva-crnilo-3:      #5B534D;  /* meta, oznake, drugotno */
  --barva-crnilo-4:      #6E655E;  /* onemogočeno, izgubljena stran dvoboja (AA 5.2:1) */
  --barva-crta:          #DED7CE;  /* hairline med vrsticami */
  --barva-polnilo:       #EDE7DF;  /* prazen del palice, žig izida */
  --barva-bela:          #FFFFFF;  /* polja za vnos, kartice mreže */
}
```

### Paleta koledarja — edina izjema

Koledar (sklop na domači strani in stran `/koledar`) barvo uporablja drugače kot
ves preostali vmesnik: tam ne pomeni **stanja**, ampak **vrsto tekmovanja**.
Razlog je vsebinski, ne okrasni — v isti soboti se igra pet lig in dva turnirja
in vprašanje »kaj od tega je turnir« z eno samo barvo nima odgovora, ker so
takrat vsi dnevi enaki.

```css
/* dva pomenska tona; samo koledar, nikjer drugje */
--barva-ton-1: #0071AB;  /* hišna modra  — turnir       */
--barva-ton-2: #5A8A00;  /* hišna zelena — ligaško kolo */
```

Pogoji izjeme — vsi veljajo, sicer izjema pade:

1. **Samo koledar.** Nove ploskve drugod se še naprej slikajo z modro, zeleno in
   rjasto; ton ni na voljo kot »tretja barva znamke«.
2. **Ton je vedno le ploskev** — pas čez dneve v mreži, levi rob vrstice,
   ploskev v ključu. Nikoli podlaga pod besedilom, zato je merilo kontrasta 3:1
   proti papirju (oba sta nad 3,7:1) in ne 4,5:1.
3. **Barva ni nikoli edini nosilec podatka** (WCAG 1.4.1): pod vsako mrežo stoji
   ključ (Turnir · Ligaško kolo · Odigrano), seznam ob mreži pa vsako
   tekmovanje izpiše z imenom.
4. **Tona sta dva in pomenita vrsto, ne identitete.** Prej jih je bilo šest in
   so se dodeljevali po vrstnem redu pojavitve — barva je bila last POGLEDA in
   ne lige, zato je pod mrežo morala stati legenda z imeni. Ta je rasla s
   pogledom (en mesec uvožene zgodovine ima tudi šestnajst tekmovanj) in je
   zavzela več prostora kot mreža sama. Katero tekmovanje je katero, odslej
   pove seznam ob mreži; mreža pove, kdaj se igra in kaj je to.
5. **Vijolične in indigo tudi tu ni.**
6. Tona sta hišna modra in zelena, da koledar ostane isti vmesnik.

Dodeljevanje tonov ni v CSS, ampak v `vmesnik/src/pomozno/koledar.ts`
(`tonVnosa`): `TURNIR` → ton 1, `LIGA` → ton 2. Ker je ton pomen in ne
identiteta, ga ni treba nikjer razlagati z imeni — ključ pod mrežo je stalen in
ne raste s številom tekmovanj.

**Odigrano zbledi, ne spremeni barve.** Trak tekmovanja, ki je mimo, ima
`opacity: 0.32`, njegova vrstica v seznamu `0.62`. To ni tretji ton: motnost
pove »to je isto, samo že za nami«, prihajajoče pa izstopi brez nove barve.

### Temna tema

Ohrani se prek `@media (prefers-color-scheme: dark)` — samo z **zamenjavo
vrednosti spremenljivk**, nikoli s podvojenimi pravili. Papir → `#12100F`,
črnilo → `#F2F0EA`, črta → `#2A2622`, glavna ostane `#0088CE`, glavna-tekst
posvetli na `#4FB3E8`, poudarek-tekst posvetli na `#9FD62E`.
**Vsaka barva besedila mora biti izrecna** — nikoli `color: inherit` na elementu,
ki stoji na drugačni podlagi kot koren.

### Prepovedano pri barvah

- **Vijolična in indigo v vseh odtenkih.** Prepovedan je tudi njun gradient
  na belem — najpogostejši znak generičnega vmesnika.
- Kakršenkoli **agresiven gradient** kot podlaga (`linear-gradient` čez ploskev,
  žareči robovi, mavrica). Gradient je dovoljen samo v grafu kot 8 % polnilo
  pod črto ali kot dvobarvna palica deleža (zelena/rjasta, brez prehoda).
- **Tretja barva v znamki.** `--barva-negativna` je semantična, ne dekorativna;
  ne uporabljaj je za poudarke, ki niso poraz, napaka ali brisanje.
- Barvanje »zaradi živosti«. Barva pomeni: modra = dejanje/podatek, zelena =
  uspeh/napredovanje, rjasta = izguba/nevarnost. Nič drugega. Edina izjema so
  toni koledarja (razdelek »Paleta koledarja«) in ta velja samo tam.
- Emoji kot ikone (🥇, 🎲, 🖨). Namesto njih beseda ali mesto v tipografiji.

---

## 3. Prostor in ritem

**8 px ritem. Vsaka mera je večkratnik 8.**

```
4 (samo hairline zamiki)  8  16  24  32  40  48  56  64
```

- Odmik vsebine strani: `40px` levo/desno, `48px` zgoraj.
- Presledek med sekcijami: `56px`. Znotraj sekcije: `24px` do naslova, `16px`
  do prve vrstice.
- Vrstica v tabeli/seznamu: `14px`–`20px` navpičnega odmika (edina izjema od
  8 px, ker gre za optično poravnavo besedila v vrstici).
- **Vsak klikljiv element je vsaj 44 × 44 px.** Gumbi v vrstici (Uredi, Izbriši,
  ↑, ↓, Odjavi) so `44px` visoki — `padding: 12px 16px` ali kvadrat `44×44`.
  Potrditvena polja so `28px` znotraj vrstice z `min-height: 44px`.
  Nikoli `padding: 6px` na dejanju.
- **Skupine sorodnih elementov vedno `display: flex` ali `grid` z `gap`** —
  nikoli presledki v izvorni kodi, nikoli `margin` na vsakem otroku.
- Širina vsebinskega okvirja: `1280px`, znotraj brez dodatnega centriranja
  z avtomatičnimi robovi na vsakem elementu.

---

## 4. Komponente — uporabi obstoječe, ne izmišljaj novih

V repozitoriju **ni** shadcn/ui, Tailwinda ali knjižnice komponent. Vmesnik je
navaden CSS z BEM razredi v slovenščini v `vmesnik/src/slog.css`. **Ti razredi so
primitivi sistema.** Preden napišeš nov razred, preveri, ali obstoječi zadošča.

### Primitivi v `vmesnik/src/slog.css`

| Razred | Vloga |
|---|---|
| `.plosca` | ploskev sekcije — v novem sistemu brez sence in radija, samo `border-top: 3px` |
| `.tabela`, `.tabela-ovoj` | podatkovna tabela, ovoj za vodoravni izpis |
| `.lestvica`, `.lestvica__mesto`, `.lestvica__stevilka`, `.lestvica__rating`, `.lestvica__klub` | lestvica igralcev in ekip |
| `.gumb`, `.gumb--glavni`, `.gumb--majhen`, `.gumb--nevaren` | gumbi; `--glavni` = polna modra, privzeti = 1px obroba, `--nevaren` = rjasto besedilo |
| `.obrazec`, `.obrazec__polje`, `.obrazec__vrstica`, `.obrazec__sklop`, `.obrazec__gumbi` | obrazci |
| `.znacka`, `.znacka--sistem`, `.znacka--opozorilo` | značke; `ZnackaStatusa` za status |
| `.naslovna-vrstica`, `.naslovna-vrstica--tesno`, `.naslovna-vrstica__desno` | glava sekcije z dejanji |
| `.podnaslov`, `.namig`, `.obvestilo`, `.obvestilo--opozorilo`, `.opombe` | pomožno besedilo |
| `.dvostolpicno`, `.dvostolpicno--lestvica`, `.skupine`, `.kartice`, `.kartica` | postavitve |
| `.izbor`, `.izbor__crta`, `.izbor__vrstica`, `.izbor__mesto`, `.izbor__skupina`, `.izbor__gumbi` | jakostni vrstni red s črto reza |
| `.mreza`, `.dvoboj`, `.dvoboj__oznaka` | izločilna mreža |
| `.postava`, `.postava__stran`, `.postava__mesto`, `.postava__oznaka`, `.postava__dvojice` | postava ekipnega srečanja |
| `.srecanje__glava`, `.srecanje__izid`, `.srecanje__tabela`, `.srecanje__zmaga` | ekipno srečanje |
| `.liga__kolo`, `.liga__srecanja`, `.liga__srecanje`, `.liga__ekipe`, `.opis-mreza` | liga |
| `.podij`, `.udelezenci`, `.seznam-izbire`, `.seznam-preprost`, `.kolo-skupina` | razvrstitev, udeleženci, seznami |
| `.koledar`, `.koledar__teden`, `.koledar__celica`, `.koledar__dan`, `.koledar__trakovi`, `.koledar__trak`, `.koledar-kljuc`, `.koledar-vnos` | koledar: mreža meseca, pasovi tekmovanj, ključ tonov, vrstica tekmovanja |

### React komponente — uvozi jih, ne pisati znova

```tsx
import { Postavitev }             from '../komponente/Postavitev'
import { ModalnoOkno }            from '../komponente/ModalnoOkno'
import { PotrditvenoOkno }        from '../komponente/PotrditvenoOkno'
import { PrijavaOkno }            from '../komponente/PrijavaOkno'
import { VnosRezultataOkno }      from '../komponente/VnosRezultataOkno'
import { LigaObrazecOkno }        from '../komponente/LigaObrazecOkno'
import { SporociloNapake }        from '../komponente/SporociloNapake'
import { ZnackaStatusa }          from '../komponente/Znacka'
import { Lestvica }               from '../komponente/Lestvica'
import { Mreza }                  from '../komponente/Mreza'
import { TekmeSeznam }            from '../komponente/TekmeSeznam'
import { TekmaKartica }           from '../komponente/TekmaKartica'
import { EnaNaEna }               from '../komponente/EnaNaEna'
import { MrezaMeseca }            from '../komponente/Koledar'
import { KljucKoledarja }        from '../komponente/Koledar'
import { VrsticaKoledarja }       from '../komponente/Koledar'
import { GrafElo }                from '../komponente/GrafElo'
import { SpremembaElo }           from '../komponente/SpremembaElo'
import { UporabniskiMeni }        from '../komponente/UporabniskiMeni'
import { Listek }                 from '../komponente/Listek'
import { ZapisnikEkipnegaDvoboja } from '../komponente/ZapisnikEkipnegaDvoboja'
import { useAvtentikacija }       from '../avtentikacija/AvtentikacijaKontekst'
import { oblikujDatum, oblikujObdobje } from '../pomozno/oblikovanje'
```

Podatkovni sloj: `../api/zahteve` (`turnirjiApi`, `dogodkiApi`, `ligeApi`,
`srecanjaApi`, `igralciApi`, `klubiApi`, `statistikaApi`), tipi in oznake
(`OZNAKE_SISTEM`, `OZNAKE_SPOL_KATEGORIJA`, `OZNAKE_FORMAT`, `OZNAKE_STATUS_PRIJAVE`)
iz `../api/tipi`. Poizvedbe vedno prek `@tanstack/react-query`.

### Vzorci, ki jih nova stran ponovi

1. **Glava strani** — masthead: logotip 24 px display 800 + navigacija 15 px
   Karla + kontekst desno mono, ločeno s `1px` in nato `3px` polno črto.
2. **Naslov strani** — nadnaslov 200 / naslov 800 v dveh vrsticah, levo;
   desno bodisi blok rezultata (polna modra) bodisi kolofon.
3. **Kolofon** — 4–5 vrstic `oznaka mono ↔ vrednost mono`, ločenih s hairline.
   Nadomešča pas kazalnikov; nikoli mreža kartic s številkami.
4. **Naslov sekcije** — `border-top: 3px solid var(--barva-crnilo)`,
   `padding-top: 16px`, naslov 40 px 800 levo, mono meta ali segmentirani
   izbirnik desno.
5. **Podnaslov v sekciji** — 12 px mono uppercase, `border-bottom: 1px`.
6. **Vrstica podatkov** — `grid` s stalnimi širinami stolpcev, `border-bottom: 1px`,
   označena vrstica dobi `border-left: 4px` v glavni barvi in mehko podlago.
7. **Napredek** — `6px` visoka palica: polnilo `--barva-polnilo`, izpolnjeno
   `--barva-glavna`. Brez radija.
8. **Značka statusa** — 11–12 px mono uppercase, `6px 8px`, polna podlaga.
   V teku = modra/bela, priprava = polnilo/tercialno, zaključeno = zelena mehka.

---

## 5. Prepovedano

Trdo prepovedano; nič od tega ne sme priti v vmesnik.

1. **Tri zaobljene kartice v vrsti.** Nobene mreže enakih kartic s
   `border-radius` in senco — ne za kazalnike, ne za dogodke, ne za povzetke.
   Podatke nosijo vrstice s črtami in stolpci, ločeni z `border-left: 1px`.
2. **Sence z motnostjo okoli 0.1 na vsem.** `box-shadow` je prepovedan kot
   dekoracija. Edina dovoljena uporaba: `box-shadow: 0 6px 0 -4px` kot podčrtaj
   aktivne navigacijske postavke (polna barva, brez zabrisa).
3. **Vse centrirano.** Naslovi strani so levo poravnani. `text-align: center`
   je dovoljen le v treh primerih: rezultat med dvema ekipama, številka v
   ozkem stolpcu, vsebina listka za tisk.
4. **Zaobljeni vogali.** `border-radius` je `0` povsod. Brez izjem — tudi
   gumbi, polja, značke in modalna okna so pravokotni.
5. **Vijolični ali indigo gradient na belem** (in gradienti kot podlaga nasploh).
6. **Generične pisave** iz seznama v razdelku 1.
7. **Pilulasti gumbi in značke** (`border-radius: 999px`).
8. **Emoji** v vmesniku.
9. **Ikone, ki nadomeščajo besedo.** Edina ikona v vsebini vmesnika je logotip.
   Namesto ikone uporabi besedo ali mono oznako.
   *Edina odobrena izjema: spodnja navigacijska vrstica na telefonu*
   (`.spodnja-vrstica`, ≤ 640 px). Tam so postavke ikone brez oznak — pet mono
   besed je pri 390 px zaseglo cel stolpec, ikona pa je v pasu, ki ga palec
   bere v pol sekunde, hitrejša od branja. Oznake ostanejo v drevesu
   (`.samo-za-bralnik`). Ikone so iz zbirke Lucide, 22 px, poteza 1.75,
   `currentColor`. Nikjer drugje — v glavi, gumbih, menijih, predalu »Več« —
   ikon ni.
10. **`color: inherit` na obarvani podlagi** — barvo besedila zapiši izrecno.
11. **Novi razredi za obstoječ vzorec.** Najprej poglej razdelek 4.
12. **Animacije stanja »zaradi lepšega«.** Dovoljen je samo `transition` barve
    ali obrobe do `120ms`, in premik podčrtaja navigacije.
    *Izjema, vezana na točko 9:* v spodnji vrstici na telefonu modra poteza
    **zdrsne** s prejšnjega stolpca na novega — `transform` 260 ms
    `cubic-bezier(.2,.8,.2,1)`, z njo ploskev in barva postavke (260 ms `ease`)
    in dvig aktivne ikone za 2 px. Daljši čas je tu premik podčrtaja, ki ga
    točka že dovoljuje; viden je samo zato, ker je pot dolga cel stolpec.

---

## 6. Tisk (listki, zapisnik NTZS)

Tisk je **črno-bel** in nosi isto tipografijo: display 800 za imena, mono za
oznake in številke. Brez podlag, brez sivin pod 10 %, samo `1px` in `2px` črne
črte. Polja za vpis rezultata so prazni pravokotniki `40px` višine, mreža
`repeat(5, 1fr)` z `4px` vrzeljo. Vsak listek ima glavo (miza, faza), dve
vrstici igralcev z klubom v mono in nogo z opombo o številu nizov in podpisu.

---

## 7. Jezik

Vse slovensko: vsebina, imena razredov, imena spremenljivk (`--barva-…`,
`.plosca`, `.gumb--glavni`), imena komponent in datotek. Brez šumnikov v
identifikatorjih (`sifranti`, ne `šifranti`), s šumniki v vsem, kar uporabnik
vidi. Sklanjaj pravilno (»3 ekipe«, »2 ekipi«, »10 ekip«) — za to že obstaja
vzorec v `DomacaStran.tsx`.

Ton: kratek, zapisniški, brez marketinga. »Igra 12 od 14 prijavljenih«, ne
»Odlična novica! Pripravili smo …«.
