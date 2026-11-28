import type { Clanek } from '../tipi'

export const REZULTATI: Clanek[] = [
  {
    id: 'vnos-rezultata',
    naslov: 'Vnos rezultata tekme',
    poglavje: 'rezultati',
    kratko:
      'Klikni tekmo, vpiši dobljene nize obeh igralcev in shrani. Točke po nizih so neobvezne. Za tekmo brez igre izberi način zaključka (brez boja, predaja, diskvalifikacija) in zmagovalca.',
    kljucne: ['rezultat', 'vnos', 'nizi', 'brez boja', 'predaja', 'diskvalifikacija', 'walkover', 'b. b.'],
    shemaNamiga: 'vnos-rezultata',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'Tekme ali mreža', 'klik na tekmo'] },
      { tip: 'shema', shema: 'vnos-rezultata', opis: 'Okno vnosa: nizi, neobvezne točke, Shrani rezultat.' },
      {
        tip: 'koraki',
        postavke: [
          'Klikni tekmo (v mreži, skupini ali seznamu tekem). Odpre se **Vnos rezultata**.',
          'Vpiši dobljene nize. Dovolj je poraženec: vpišeš 1, zmagovalec dobi 3 sam (pri tekmi na tri dobljene).',
          'Če imaš na listku točke, obkljukaj **Vnesi tudi točke po nizih** in jih prepiši. [Točke po nizih](tocke-po-nizih)',
          'Klikni **Shrani rezultat**. Zmagovalec gre samodejno v naslednje kolo mreže.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'brez-igre',
        naslov: 'Tekma brez igre',
        kratko:
          'Brez boja = nasprotnik ni nastopil (ne šteje v rating). Predaja med igro = začeta tekma, ki je igralec ni dokončal. Diskvalifikacija = odločil sodnik.',
      },
      {
        tip: 'tabela',
        glava: ['Način zaključka', 'Kdaj', 'Rating'],
        vrstice: [
          ['Odigrana tekma', 'Običajno.', 'šteje'],
          ['Brez boja (nasprotnik ni nastopil)', 'Igralec ni prišel k mizi.', 'ne šteje'],
          ['Predaja med igro', 'Tekma se je začela, igralec je ni dokončal (poškodba).', 'šteje'],
          ['Diskvalifikacija', 'Odločitev sodnika.', 'ne šteje'],
        ],
      },
      { tip: 'razdelek', id: 'nizi', naslov: 'Drugačno število nizov za eno tekmo' },
      {
        tip: 'odstavek',
        besedilo:
          'Polje **Prvi do (dobljenih nizov)** v oknu velja samo za to tekmo (npr. finale na 4 dobljene); kategorija ostane pri svoji nastavitvi.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Gledalci vidijo rezultat takoj. Stran kategorije med igro sama osvežuje mrežo in lestvice; čas zadnje osvežitve stoji ob statusu (»Osveženo ob …«).',
      },
    ],
    povezani: ['tocke-po-nizih', 'popravek-rezultata', 'odstop', 'branje-listkov'],
  },
  {
    id: 'branje-listkov',
    naslov: 'Branje listkov s fotografije',
    poglavje: 'rezultati',
    kratko:
      'Izpolnjen sodniški listek fotografiraš s telefonom, Turnirko prebere točke in predlaga izid. Ti ga pogledaš in potrdiš — izid se zapiše šele s tvojo potrditvijo.',
    kljucne: [
      'fotografija',
      'slika',
      'kamera',
      'telefon',
      'umetna inteligenca',
      'AI',
      'QR',
      'koda',
      'branje',
      'skeniraj',
      'signal',
      'brez interneta',
      'miza',
      'sodnik',
      'hrbtna stran',
      'obojestransko',
    ],
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'Branje listkov'] },
      {
        tip: 'koraki',
        postavke: [
          'Odpri **Branje listkov** na strani turnirja ali kategorije (vidno, ko kategorija teče).',
          'Ko sodnik prinese listek, pritisni **Fotografiraj listek** in ga posnemi od zgoraj, da sta na sliki cela tabela in QR koda v desnem kotu. En listek na fotografijo.',
          'Takoj lahko fotografiraš naslednjega: branje teče v ozadju nekaj sekund, listek se nato pojavi pod **Za potrditev**.',
          'Zanesljivi listki (brez opozoril) so na vrhu vrste in jih potrdiš z **Potrdi** ali vse hkrati s **Potrdi vse zanesljive**. Pri drugih odpri **Preglej**: pod vsakim nizom je izrez celic z listka, negotova celica je povečana in rjasto obrobljena. Primerjaj, po potrebi popravi, nato **Potrdi izid**.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'preverbe',
        naslov: 'Kdaj Turnirko listka ne potrdi sam',
        kratko:
          'Predlog z opozorilom ali negotovo številko moraš pregledati. Turnirko preveri pravila niza, število dobljenih nizov in ali se točke ujemajo s celico »Rezultat«.',
      },
      {
        tip: 'seznam',
        postavke: [
          'Številka je bila popravljena, prečrtana ali težko berljiva (rjasto obarvana). Pri prečrtani in prepisani celici je opomba »Popravljena celica«.',
          'Turnirko ni našel vseh vrstic listka (npr. prst ali senca čez celico Rezultat).',
          'Niz ni veljaven (npr. 11:10) ali tekma nima zmagovalca s pravim številom nizov.',
          'Točke dajo drug izid, kot ga je sodnik zapisal v celico **Rezultat**.',
          'Na listku je zapisan izid brez boja, predaja ali diskvalifikacija.',
          'Tekma ima izid že vpisan (listek je bil fotografiran dvakrat).',
          'Zapis je nepopoln: niz manjka, tekma nima zmagovalca (predaja, brez boja) — take izide vpišeš sam.',
          'Naključna kontrola: približno vsak dvajseti listek, ki bi ga sicer potrdil z enim dotikom, pregledaš v celoti in primerjaš vse številke s fotografijo. Tako Turnirko izmeri, kako pogosto se zmoti, ne da bi to opazil.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'tekma',
        naslov: 'Kako Turnirko ve, katera tekma je na listku',
        kratko:
          'Po QR kodi in številki tekme (T-…), ki sta na vsakem natisnjenem listku. Pri praznem listku (brez kode) tekmo izbereš sam s seznama tekem, ki čakajo na izid.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Listek poravnajo trije črni kvadrati v vogalih in QR koda. Listki, natisnjeni pred to funkcijo, jih nimajo — natisni nove ([Listki za tiskanje](listki)). Imen Turnirko ne bere: tekmo pove koda.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'V okence **Miza** v glavi listka vpiši številko mize, ko tekmo pokličeš (ali naj jo vpiše sodnik). Turnirko jo prebere in pokaže ob listku kot »Miza 7 (z listka)«; slabo berljivo številko obarva in pokaže izrez okenca. Na izid miza ne vpliva.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Isti listek lahko fotografiraš znova (npr. če je bila prva fotografija zamegljena). Velja novejša fotografija, starejša se umakne iz vrste z oznako »Nadomeščeno«. Če novejše ni bilo mogoče prebrati, ostane prebrana starejša.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Fotografijo potrjenega listka najdeš tudi pozneje: klikni končano tekmo in v oknu s točkami izberi **Listek** (30 dni po turnirju, nato se fotografija izbriše). Vidita jo samo organizator turnirja in administrator.',
      },
      {
        tip: 'razdelek',
        id: 'sodniki',
        naslov: 'Sodniki slikajo listke sami',
        kratko:
          'Na strani **Branje listkov** vklopi **Sodniki slikajo listke**. Sodnik po tekmi odpre stran za sodnike na svojem telefonu (brez prijave), fotografira listek, preveri točke in izid potrdi. Ti ne fotografiraš več vsakega listka sam.',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Predpregled**: sodnikova potrditev počaka tebe – v vrsti piše »Sodnik je izid potrdil«, potrdiš z enim dotikom. Za prvi turnir izberi to.',
          '**Velja takoj**: izid se vpiše, ko ga sodnik potrdi, mreža napreduje. Ti ga vidiš med zaključenimi (»potrdil sodnik«) in ga po potrebi popraviš. Če sodnik spremeni številko, ki jo je Turnirko prebral zanesljivo, ali Turnirko na fotografiji ne prebere cele tekme, izid vseeno počaka tebe.',
          'Listke natisni **po vklopu** stikala in **obojestransko**: vsak list dobi hrbtno stran s kodo, ki sodniku odpre stran za sodnike. Kodo na hrbtu lahko pred tiskom izklopiš, če tiskalnik ne tiska obojestransko.',
          'Sodnik lahko pošlje izid samo za listek, ki ga ima v roki: koda na listku nosi skrivnost tekme. Deluje samo z listki, natisnjenimi po 22. 11. 2026, in samo, dokler tekma nima izida. Listke natisni prijavljen.',
          'Predajo, brez boja, diskvalifikacijo in ekipne zapisnike sodnik ne vpiše – taki listki pridejo na pult kot doslej. Tudi papirni listek po tekmi vedno oddajo na pultu.',
          'Številke, ki jih Turnirko ni prebral zanesljivo, so sodniku močno obarvane, nad tabelo pa piše, koliko jih je. Sodnik jih primerja z listkom in izid potrdi z enim gumbom **Obarvane sem preveril · Potrdi izid**; vsake posebej ne potrjuje.',
          'Če sodnik spremeni prebrano številko, je listek označen, da ga pogledaš.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'signal',
        naslov: 'Ko v dvorani ni signala',
        kratko:
          'Fotografija se najprej shrani v telefon in se pošlje sama, ko je povezava spet na voljo. Fotografiraš lahko naprej; pod **Berem** piše, koliko fotografij čaka na signal.',
      },
      {
        tip: 'seznam',
        postavke: [
          'Stran **Branje listkov** pusti odprto: fotografije se pošiljajo, dokler je stran odprta. Če jo zapreš ali telefon ugasne brskalnik, se pošiljanje nadaljuje, ko stran spet odpreš.',
          'Fotografija, ki je prišla na strežnik dvakrat (signal je izginil sredi pošiljanja), se zapiše samo enkrat.',
          'Neposlane fotografije se iz telefona izbrišejo ob odjavi in po 24 urah. Tak listek fotografiraj znova.',
          'Dokler je stran odprta, zaslon telefona ne ugasne.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'ucenje',
        naslov: 'Turnirko se uči iz tvojih potrditev',
        kratko:
          'Števke potrjenih listkov (samo številke, brez imen) se shranijo. Ponoči se bralnik na njih dotrenira in bere pisavo vaših sodnikov vedno bolje. Vse teče na Turnirkovem strežniku in je brezplačno.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Ob potrditvi se zabeleži tudi, ali si listek potrdil z enim dotikom ali v oknu in koliko časa si porabil zanj. Iz tega se meri, ali branje res prihrani delo.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Na vrhu strani vidiš, koliko potrjenih listkov je bilo prebranih brez popravka. Če branje ne uspe, pritisni **Preberi znova** ali izid vpiši ročno ob fotografiji.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Branje listkov je v preizkusu. Če povezave **Branje listkov** ne vidiš, za tvoj račun še ni vklopljeno. Fotografije se po 30 dneh od turnirja izbrišejo.',
      },
    ],
    povezani: ['vnos-rezultata', 'listki', 'popravek-rezultata', 'mize'],
  },
  {
    id: 'mize',
    naslov: 'Mize in klicanje tekem',
    poglavje: 'rezultati',
    kratko:
      'Na strani **Mize** vidiš tloris dvorane: kje se igra, katera miza je prosta in kako dolgo traja tekma. Tekmo **povlečeš z vrste na prosto mizo** (ali klikneš mizo); klic se zabeleži, igralce pa pokličeš na glas. Vrstni red vrste lahko spremeniš z vlečenjem.',
    kljucne: [
      'miza',
      'klic',
      'pokliči',
      'prosta miza',
      'tloris',
      'povleci',
      'uredi dvorano',
      'zaslon za igralce',
      'listek',
      'razveljavi',
      'sodnik',
      'poraženec sodi',
      'počitek',
      'naslednja tekma',
      'dvorana',
      'razpored',
      'vrstni red',
      'tisk',
      'skupne mize',
      'dva turnirja',
      'ista dvorana',
      'vrstni red kategorij',
      'vrstni red kol',
      'listki vseh kategorij',
    ],
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'Mize'] },
      {
        tip: 'koraki',
        postavke: [
          'Pri prvem odprtju vpiši **Koliko miz imaš?** in pritisni **Postavi N miz**. Mize so oštevilčene od 1 naprej, tako kot jih sodnik vpiše v okence na listku.',
          'Ko kategorija teče, ima vsaka prosta miza (modro črtkana) predlog naslednje tekme. Pritisni mizo in **Pokliči**, ali pa tekmo **povleci z vrste »Na vrsti« na prosto mizo**. Igralca pokliči na glas.',
          'Spodaj se za 6 sekund pokaže sporočilo z gumbom **Razveljavi**. Listki so natisnjeni vnaprej (gumb **Listki za tiskanje** v glavi strani); če želiš listek z že vpisano mizo, v oknu klica odkljukaj **Natisni listek z vpisano mizo**.',
          'Na začetku turnirja s **Pokliči vse predloge** zasedeš vse proste mize naenkrat. Pri tem se nič ne tiska.',
          'Vrstni red vrste **Na vrsti** spremeniš tako, da tekmo povlečeš na drugo mesto v vrsti (s tipkovnico: Alt + puščica gor ali dol). Proste mize dobijo predloge po tvojem vrstnem redu. Tekme, ki se pripravijo pozneje, pridejo na konec. **Samodejni vrstni red** vrne vrsto, kot jo predlaga Turnirko.',
          'Ko je tekma končana, klikni mizo in izberi zmagovalca in izid v nizih; točke po nizih vpišeš v naslednjem oknu. Izid lahko pošlje tudi sodnik s telefonom. Miza se sprosti sama, stran kratko pisne in mizo 30 sekund obarva zeleno.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'tloris',
        naslov: 'Kaj kaže tloris',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Prosta** miza je modro črtkana in pove predlog. **Pravkar sproščena** je 30 sekund zelena.',
          '**V igri** je črna miza: igralca stojita vsak na svoji strani mreže, spodaj je modri trak časa. Ko tekma traja 30 minut ali več, ima rjasto črto in minute v rdeči znački.',
          '**Izklopljena** miza je šrafirana. Ekipno srečanje je narisano čez dve mizi.',
          'Zgoraj so števci (proste, v igri, na vrsti) in ura strežnika, po kateri se računajo minute.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'predlog',
        naslov: 'Kako Turnirko izbere naslednjo tekmo',
        kratko:
          'Igralec, ki igra ali sodi drugje, ni predlagan. Tekme gredo strogo po kolih: najprej 1. kolo vseh skupin vseh kategorij, nato 2. kolo. V kolu imajo prednost spočiti igralci, nato kategorije v tvojem vrstnem redu in skupine po vrsti.',
      },
      {
        tip: 'seznam',
        postavke: [
          'Ne predlaga tekme igralca, ki zdaj igra ali sodi na drugi mizi, tudi v drugi kategoriji ali v dvojicah.',
          'Tekme gredo strogo po kolih: najprej celo 1. kolo vseh skupin vseh kategorij, nato 2. kolo … Tekma, ki je zaostala (na primer 1. kolo, ko ostali že igrajo 2. kolo), gre naprej, tudi če igralec še ni počival.',
          'Skupina po 5 igra v vsakem kolu skupaj z ostalimi in konča zadnja (4. in 5. kolo igra sama).',
          'Izločilni del šteje kot naslednje kolo za skupinami svoje kategorije: pri treh kolih skupin je prvi krog mreže »4. kolo«.',
          'Kola skupin kategorije lahko razvrstiš sam, na primer 3., 1., 2. kolo, v **Uredi dvorano → Turnirji in kategorije → Vrstni red kol**. »Najprej 1. kolo« takrat pomeni prvo kolo po tvojem vrstnem redu (pri tej kategoriji 3. kolo), druge kategorije gredo po svojem. Po istem vrstnem redu so razvrščeni listki po kolih.',
          'V istem kolu ima prednost tekma, v kateri so vsi igralci počivali vsaj toliko minut, kot je nastavljeno pod **Počitek**. Nato gredo kategorije po vrstnem redu, ki ga nastaviš v **Uredi dvorano → Turnirji in kategorije**, v kategoriji pa skupine po vrsti (skupina 1, 2, 3 …).',
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Predlog je samo predlog, odločiš vedno ti: na prosto mizo lahko povlečeš katero koli tekmo z vrste (tudi zasedeno, s sporočilom, kdo igra drugje). Razlogi predloga, na primer »2. kolo« ali »zadnja tekma pred 1 min«, so v oknu klica. Desni stolpec **Na vrsti** tekme našteje po vrstnem redu predloga; številka ob tekmi je predlagana miza. Ko vrsto urediš sam, velja tvoj vrstni red, dokler ne izbereš **Samodejni vrstni red** (tudi počitek takrat ne premakne tekme; zasedena tekma ostane na svojem mestu, a mize ne dobi).',
      },
      {
        tip: 'razdelek',
        id: 'sodnik',
        naslov: 'Sodnik: poraženec sodi naslednjo tekmo',
        kratko:
          'Ko je vklopljeno **Poraženec sodi**, je ob klicu za sodnika predlagan poraženec zadnje tekme na tej mizi, tudi če je naslednja tekma iz drugega turnirja na teh mizah. Zamenjaš ga lahko s katerimkoli igralcem turnirja.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Nastavitev vklopiš v načinu **Uredi dvorano** (tam sta še **Počitek** in **Pisk ob prosti mizi**). Kdor sodi, ni predlagan za igro, dokler tekma ni končana. V oknu klica sodnika zamenjaš s katerimkoli igralcem turnirja ali polje izprazniš.',
      },
      {
        tip: 'seznam',
        postavke: [
          'Po tekmi dvojic sodnik ni predlagan.',
          'Po tekmi brez boja tudi ne, ker poraženca ni bilo.',
          'Poraženec, ki je od takrat že igral ali sodil, ne čaka več na sojenje.',
          'Tekma igralca, ki naj bi sodil, je v predlogu na koncu, z razlogom »naj sodi na mizi 3«.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'dejanja',
        naslov: 'Premik, preklic in urejanje dvorane',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Premik:** zasedeno mizo povleci na drugo prosto mizo; čas igre teče naprej. **Zamenjaj sodnika** je v oknu izida (klik na zasedeno mizo).',
          '**Prekliči klic** je prav tam in vrne tekmo med pripravljene. Ekipno srečanje, ki že ima postavo, se igra naprej in izgubi samo mizo.',
          '**Uredi dvorano:** klikni prosto mizo, da jo izklopiš (pokvarjena ali zasedena za drug namen), ali izklopljeno, da jo vklopiš. **+ Miza** in **− Odstrani zadnjo** spremenita število miz. Mize, na kateri se igra, ni mogoče izklopiti ali odstraniti.',
          'Ekipno srečanje pokličeš na dve mizi (druga je sosednja prosta). Izid se vpiše v zapisnik srečanja: klikni mizo in **Odpri zapisnik**.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'skupne',
        naslov: 'Dva turnirja na istih mizah',
        kratko:
          'Ko v isti dvorani na istih mizah igrata dva tvoja turnirja (na primer U15 in U21), ju povežeš v **Uredi dvorano → Turnirji in kategorije**. Stran Mize, tisk listkov, branje listkov in zaslon za igralce ju nato vodijo kot celoto, gledalci pa ju vidijo ločeno kot prej.',
      },
      {
        tip: 'koraki',
        postavke: [
          'Na strani **Mize** enega od turnirjev pritisni **Uredi dvorano** in nato **Turnirji in kategorije**.',
          'Pod **Deli mize s turnirjem** izberi drugi turnir in pritisni **Poveži**. Ponujeni so samo tvoji turnirji na isti dan.',
          'Pod **Vrstni red kategorij** s puščicama razvrsti kategorije (na primer U15 moški, U15 ženske, U21 moški, U21 ženske). Predlog gre po kolih, v kolu po tem vrstnem redu.',
          'Pod **Vrstni red kol** s puščicama ← in → razvrsti kola skupin vsake kategorije (levo se igra prej). **Po vrsti** vrne 1., 2., 3. kolo. To velja tudi za en sam turnir.',
          'Listke vseh kategorij natisneš naenkrat: **Listki za tiskanje → Vse kategorije · en tisk**. **Po kolih** da 1. kolo vseh kategorij v tvojem vrstnem redu, nato 2. kolo.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Mize, vrsta **Na vrsti**, klici in nastavitve (število miz, izklopljene mize, počitek, poraženec sodi) so skupne. Stran Mize kateregakoli od obeh turnirjev kaže isto.',
          'Za sodnika lahko izbereš igralca obeh turnirjev. Kategorija z enakim imenom v obeh turnirjih ima ob sebi ime turnirja.',
          'Na strani **Branje listkov** fotografiraš listke obeh turnirjev; stikalo **Sodniki slikajo listke** velja za oba.',
          '**Loči** v istem oknu turnir vrne na njegove mize; nastavitve miz obdrži, tekme, ki se igrajo, ostanejo na svojih mizah.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'zaslon',
        naslov: 'Zaslon za igralce',
        kratko:
          'Gumb **Zaslon za igralce ↗** odpre celozaslonsko stran za TV v dvorani: zadnji klici, naslednje tekme in zemljevid dvorane. Odpri jo na drugem zaslonu v istem brskalniku, kjer si prijavljen; osvežuje se sama.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Na telefonu je tloris pomanjšan: tapni mizo, nato tekmo (**Na N** ali **Pokliči na mizo N**). Vlečenja tam ni.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Ko je tekma poklicana, velja za začeto: žreba te kategorije ni več mogoče ponoviti. Če je bil klic pomota, ga najprej prekliči (ali pritisni **Razveljavi**).',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Če sodnik na listek vpiše drugo mizo, kot je bila poklicana, to vidi na telefonu, ti pa na strani **Branje listkov**.',
      },
    ],
    povezani: ['vnos-rezultata', 'branje-listkov'],
  },
  {
    id: 'tocke-po-nizih',
    naslov: 'Točke po nizih',
    poglavje: 'rezultati',
    kratko:
      'Neobvezno: točke vsakega niza, npr. 11:7. Najhitreje vpišeš samo točke poraženca niza — nasprotnik dobi 11 sam in kazalec skoči v naslednji niz. Aplikacija preveri, da se točke ujemajo z izidom.',
    kljucne: ['točke', 'nizi', 'set', 'potek tekme', '11:9'],
    shemaNamiga: 'tocke-po-nizih',
    vsebina: [
      { tip: 'shema', shema: 'tocke-po-nizih', opis: 'Vsak niz ima dve okenci; kazalec gre sam naprej.' },
      {
        tip: 'seznam',
        postavke: [
          'Nad stolpcem stoji priimek igralca — tako kot na listku.',
          '**Najhitreje: vpiši samo točke poraženca niza.** Vpišeš 7, nasprotnik dobi 11 sam in kazalec skoči v naslednji niz. Pri 10 dobi nasprotnik 12.',
          'Pri števki 1 okence počaka, ker lahko sledi še ena (11, 13 …). Podaljšek (13:11, 21:19) vpišeš z obema številkama.',
          'S puščicami ← → ↑ ↓ greš na katero koli okence.',
          'Niz mora biti veljaven (do 11, razlika 2, pri podaljšku natanko 2), izid nizov pa se mora ujemati z vpisanim zmagovalcem.',
          'Po odločitvi tekme ne sme slediti noben niz več.',
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Točke se izpišejo na profilu igralca (statistika nizov) in na natisnjenem zapisniku. Če jih ne vpišeš, ni nič narobe — lestvica jih potrebuje samo, kadar izenačene loči razlika točk.',
      },
    ],
    povezani: ['vnos-rezultata', 'razvrstitev'],
  },
  {
    id: 'popravek-rezultata',
    naslov: 'Popravek rezultata',
    poglavje: 'rezultati',
    kratko:
      'Klikni odigrano tekmo in popravi izid — tudi po zaključku turnirja. Če popravek zamenja zmagovalca, okno pokaže, katere naslednje tekme so prizadete, in te vpraša, kaj naj se zgodi z njimi.',
    kljucne: ['popravi', 'napaka', 'napačen zmagovalec', 'zamenjava zmagovalca', 'popravek'],
    shemaNamiga: 'popravek',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'klik na odigrano tekmo', 'Popravek rezultata'] },
      {
        tip: 'odstavek',
        besedilo:
          'Isti zmagovalec, drugi nizi ali točke: shrani in to je vse — lestvica, mesta in rating se preračunajo sami.',
      },
      {
        tip: 'razdelek',
        id: 'zmagovalec',
        naslov: 'Zamenjan zmagovalec v mreži',
        kratko:
          'Kadar ima naslednja tekma že izid, izbereš: »Izbriši njihove izide« (vneseš znova) ali »Zamenjaj igralca, izidi ostanejo« (igralca si zamenjata mesti po vsej veji).',
        shemaNamiga: 'popravek',
      },
      { tip: 'shema', shema: 'popravek', opis: 'Popravljena tekma in tekme, ki stojijo na njej.' },
      {
        tip: 'tabela',
        glava: ['Izbira', 'Kaj se zgodi', 'Kdaj'],
        vrstice: [
          ['Izbriši njihove izide', 'Naslednje tekme izgubijo izide, pravi igralec gre naprej, izide vpišeš znova.', 'Naslednje tekme je igral napačen igralec.'],
          ['Zamenjaj igralca, izidi ostanejo', 'Igralca si zamenjata mesti v vseh teh tekmah, izidi ostanejo.', 'V dvorani je naprej igral pravi igralec, narobe je bilo samo vpisano.'],
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Če naslednja tekma še nima izida, odločitve ni: v njej se samo zamenja igralec.',
      },
      {
        tip: 'razdelek',
        id: 'skupina',
        naslov: 'Spremenjen vrstni red skupine',
        kratko:
          'Če je iz skupine že nastal izločilni del (ali finalne skupine, žrebi), izbereš: podri naslednji del ali ga pusti, kot je. Okno pove, koliko vnesenih tekem bi se izgubilo.',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Podri izločilni del** — izločilni del izgine (tudi z vnesenimi izidi) in ga narediš znova.',
          '**Izločilni del ostane, kot je** — popravita se tabela in rating, mreža pa se s tabelo ne ujema več.',
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Popravek, ki izbriše izide, kategorijo spet odpre. Končna mesta nastanejo ob novem zaključku.',
      },
      { tip: 'razdelek', id: 'liga', naslov: 'Popravek v ligi' },
      {
        tip: 'odstavek',
        besedilo:
          'Ligaško tekmo popraviš v zapisniku srečanja. Zmagovalca lahko zamenjaš, dokler ostane potek srečanja isti (iste tekme odigrane). Napačno postavo popraviš z razveljavitvijo zapisnika. [Razveljavitev](razveljavitev-zapisnika)',
      },
    ],
    povezani: ['vnos-rezultata', 'razveljavitev-zapisnika', 'statusi'],
  },
  {
    id: 'odstop',
    naslov: 'Odstop igralca ali ekipe',
    poglavje: 'rezultati',
    kratko:
      'Igralec, ki med turnirjem odstopi: odigrane tekme obveljajo, vse preostale dobijo nasprotniki brez boja in ne štejejo v rating. Dejanja ni mogoče razveljaviti.',
    kljucne: ['odstop', 'poškodba', 'odšel domov', 'brez boja', 'odstopil'],
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'Udeleženci', 'Odstopil'] },
      {
        tip: 'koraki',
        postavke: [
          'Odpri zavihek **Udeleženci**.',
          'Ob igralcu, ki še igra, klikni **Odstopil** (pri ekipi **Odstopila**).',
          'Potrdi z **Potrdi odstop**.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Že odigrane tekme obveljajo.',
          'Preostale tekme dobijo nasprotniki **brez boja**; v lestvici skupine štejejo kot zmaga nasprotnika, v rating ne gredo.',
          'Pred žrebom ni odstopa — takrat igralca preprosto odjaviš. [Odjava](prijave#odjava)',
        ],
      },
    ],
    povezani: ['vnos-rezultata', 'prijave'],
  },
  {
    id: 'razvrstitev',
    naslov: 'Lestvica skupine in krog',
    poglavje: 'rezultati',
    kratko:
      'Najprej štejejo zmage. Ko sta dva ali več izenačenih, odloča krog: medsebojne zmage, nato razlika nizov in nazadnje razlika točk — samo iz tekem med izenačenimi.',
    kljucne: ['lestvica', 'krog', 'izenačeni', 'tie break', 'razlika nizov', 'medsebojno', 'mesto v skupini'],
    shemaNamiga: 'krog',
    vsebina: [
      { tip: 'shema', shema: 'krog', opis: 'Trije izenačeni z 2 zmagama: odloči razlika nizov med njimi.' },
      {
        tip: 'koraki',
        postavke: [
          'Več zmag je višje.',
          'Pri enakih zmagah: manj porazov.',
          'Še vedno izenačeni tvorijo **krog**. Šteje se samo tekme med njimi: medsebojne zmage, nato razlika nizov, nato razlika točk.',
          'Če krog razbije le del izenačenih, se pravilo ponovi za preostale.',
          'Kjer je še vse enako, odloči jakostni vrstni red (nosilec).',
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Pod lestvico skupine je gumb **Kako je bil izračunan krog?**, kadar je bil krog. Pokaže tabelo meril, ki so odločila — uporabno, ko te igralec vpraša, zakaj je tretji.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Točke po nizih odločijo samo, kadar je vse drugo enako. Če jih ne vpisuješ, tak krog odloči jakostni vrstni red.',
      },
    ],
    povezani: ['tocke-po-nizih', 'sistemi', 'lestvica-lige'],
  },
  {
    id: 'rating-tekmovanja',
    naslov: 'Turnirko rating in tvoje tekmovanje',
    poglavje: 'rezultati',
    kratko:
      'Rating se preračuna ob vsakem vnosu in popravku. Koliko tekmovanje šteje, določi raven. Dvojice in tekme brez boja ne štejejo. Novinec dobi začetni rating po starosti, rekreativec 800.',
    kljucne: ['rating', 'elo', 'točke', 'novinec', 'rekreativec', 'raven', 'teža'],
    shemaNamiga: 'raven',
    vsebina: [
      { tip: 'shema', shema: 'raven', opis: 'Teža po ravni tekmovanja.' },
      {
        tip: 'seznam',
        postavke: [
          'Teža tekme je odvisna od ravni turnirja ali lige (uradno 100 %, klubsko 75 %, rekreativno 50 %, ne šteje 0). [Raven](turnir-nov#raven)',
          'Zmaga je zmaga: izid v nizih na spremembo ne vpliva.',
          'Dvojice, ekipna tekma kot celota, brez boja in diskvalifikacija ne štejejo. Šteje predaja med igro.',
          'Vsaka sprememba je v dnevniku na profilu igralca, s pojasnilom, kako je nastala.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'novinci',
        naslov: 'Novinci in rekreativci',
        kratko:
          'Novinec začne pri ratingu svoje starosti in spola, po prvem turnirju pa se uvrsti po tem, koga je premagal. Rekreativec (kljukica pri igralcu) začne pri 800.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Novinec ne začne pri isti številki za vse: 12-letnik in 30-letnik sta različno močna. Po prvem dnevu igranja se uvrsti po rezultatih. Pri rekreativni ligi ali turnirju obkljukaj **Rekreativec** že ob vnosu igralca — sicer bi začetnik začel pri ratingu tekmovalca svojih let in z vsakim porazom izgubljal veliko točk.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Vpisuj tekme po vrsti. Rating teče po času tekem; srečanje, vneseno za nazaj, se sicer preračuna samo, a vrstni red vnosa vpliva na to, kdo velja za novinca.',
      },
    ],
    povezani: ['turnir-nov', 'prijave'],
  },
]
