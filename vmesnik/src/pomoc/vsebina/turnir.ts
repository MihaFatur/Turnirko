import type { Clanek } from '../tipi'

export const TURNIR: Clanek[] = [
  {
    id: 'turnir-nov',
    naslov: 'Nov turnir',
    poglavje: 'turnir',
    kratko:
      'Turnir je ovoj za kategorije: ime, datum, kraj in raven tekmovanja. Raven pove, koliko rezultati štejejo v Turnirko rating.',
    kljucne: ['ustvari turnir', 'nov turnir', 'raven', 'datum', 'kraj', 'dvorana'],
    shemaNamiga: 'raven',
    vsebina: [
      { tip: 'kje', pot: ['Turnirji', '+ Nov turnir'] },
      {
        tip: 'koraki',
        postavke: [
          '**Ime turnirja** — kot ga bodo iskali gledalci, npr. »Odprto prvenstvo NTK Savinja 2026«.',
          '**Kraj** in **Dvorana** — kraj izbereš iz šifranta. Če ga ni, ga doda admin na strani Šifranti.',
          '**Datum začetka** in **Datum konca** — turnir brez datuma ni v koledarju.',
          '**Raven tekmovanja** — glej spodaj.',
          '**Opombe** — vidijo jih gledalci pod naslovom turnirja (urnik, prijavnina, kontakt).',
        ],
      },
      {
        tip: 'razdelek',
        id: 'raven',
        naslov: 'Raven tekmovanja',
        kratko:
          'Raven množi spremembo ratinga: uradno tekmovanje NTZS šteje v celoti, klubsko tri četrtine, rekreativno polovico. »Ne šteje v rating« rating izklopi.',
        shemaNamiga: 'raven',
      },
      { tip: 'shema', shema: 'raven', opis: 'Teža rezultatov za rating po ravni tekmovanja.' },
      {
        tip: 'tabela',
        glava: ['Raven', 'Teža', 'Kdaj'],
        vrstice: [
          ['Uradno tekmovanje (NTZS)', '100 %', 'Turnirji iz koledarja zveze.'],
          ['Klubsko tekmovanje', '75 %', 'Klubski turnirji in regionalne lige — privzeto.'],
          ['Rekreativno tekmovanje', '50 %', 'Rekreativci, veterani za zabavo, mešane ekipe.'],
          ['Ne šteje v rating', '0', 'Vaja, prijateljski turnir, ekshibicija.'],
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Na seznamu turnirjev gost ne vidi turnirjev, ki so več kot 7 dni daleč (koledar sezone bi seznam zapolnil). Na svoji strani in v koledarju so vidni vedno.',
      },
      { tip: 'razdelek', id: 'urejanje', naslov: 'Urejanje in dejanja turnirja' },
      {
        tip: 'odstavek',
        besedilo:
          'Podatke turnirja urediš z **Uredi turnir** na strani turnirja. Ob njem so še **Logotip**, **Pokrovitelji**, tisk žreba in **Zaključi turnir**. Na telefonu so ta dejanja pod tremi pikami v glavi.',
      },
    ],
    povezani: ['kategorija-nova', 'rating-tekmovanja', 'logotip'],
  },
  {
    id: 'kategorija-nova',
    naslov: 'Nova kategorija (dogodek)',
    poglavje: 'turnir',
    kratko:
      'Vsaka kategorija ima svoja pravila: spol, starost, disciplino (posamično, dvojice, ekipno), sistem tekmovanja in nize. Ko so na njej prijave, vrste ne moreš več spremeniti.',
    kljucne: ['dogodek', 'kategorija', 'nov dogodek', 'disciplina', 'nizi', 'starostna kategorija', 'prijavnina', 'rok prijave'],
    shemaNamiga: 'sistemi',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', '+ Dogodek'] },
      {
        tip: 'tabela',
        glava: ['Polje', 'Kaj pomeni'],
        vrstice: [
          ['Ime dogodka', 'Kar vidijo gledalci: »Člani«, »U15 dekleta«, »Dvojice odprto«.'],
          ['Kategorija', 'Moški, Ženske, Kdorkoli; pri dvojicah še Mešano (vsak par moški + ženska).'],
          ['Starostna kategorija', 'Pas NTZS (U11 … veterani). Neobvezno.'],
          ['Disciplina', 'Posamično, Dvojice ali Ekipno.'],
          ['Sistem tekmovanja', 'Kako se igra — glej [Sistemi tekmovanja](sistemi).'],
          ['Nizi (prvi do)', 'Do koliko dobljenih nizov se igra tekma (npr. 3 = na tri dobljene).'],
          ['Število skupin, igralcev v skupini', 'Samo pri sistemih s skupinami.'],
          ['Tekma za 3. mesto', 'Pri izločilnem delu: poraženca polfinalov igrata za 3. mesto.'],
          ['Rok prijave, Prijavnina', 'Informacija za igralce; aplikacija prijav po roku ne zaklene.'],
          ['Format srečanja', 'Samo pri ekipnem dogodku — vrstni red tekem srečanja.'],
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Disciplino lahko spremeniš samo, dokler na dogodku ni prijav. Ekipnega dogodka ni mogoče spremeniti v posamičnega ali obratno.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Dvojice igrajo isto izločilno mrežo kot posamično tekmovanje. Igralce prijaviš posamično in jih pred žrebom povežeš v pare. [Dvojice](dvojice)',
      },
    ],
    povezani: ['sistemi', 'prijave', 'dvojice', 'ekipni-dogodek'],
  },
  {
    id: 'sistemi',
    naslov: 'Sistemi tekmovanja',
    poglavje: 'turnir',
    kratko:
      'Izločilni, krožni, skupine + izločilni, TOP (skupine po jakosti), skupine za mesta in SV regija. Sistem določi, kako nastane žreb in kako se izračuna razvrstitev.',
    kljucne: ['sistem', 'izločilni', 'krožni', 'vsak z vsakim', 'skupine', 'TOP', 'single elimination', 'round robin'],
    shemaNamiga: 'sistemi',
    vsebina: [
      { tip: 'shema', shema: 'sistemi', opis: 'Šest sistemov tekmovanja na prvi pogled.' },
      {
        tip: 'tabela',
        glava: ['Sistem', 'Kako se igra', 'Kdaj ga izbrati'],
        vrstice: [
          ['Izločilni', 'Mreža, poraženec izpade. Nosilci so razporejeni tako, da se najboljši srečajo čim kasneje.', 'Veliko igralcev, malo časa. Tudi dvojice.'],
          ['Krožni (vsak z vsakim)', 'Vsak igra z vsakim, ena lestvica.', 'Do ~8 igralcev, kategorija z malo prijavami.'],
          ['Skupine + izločilni', 'Skupine vsak z vsakim, prva dva iz vsake gresta v izločilno mrežo.', 'Najpogostejši sistem odprtih turnirjev.'],
          ['Skupine po jakosti (TOP)', 'Igra najboljših N prijavljenih; skupina 1 so mesta 1–8, skupina 2 mesta 9–16 … Izločilnega dela ni.', 'TOP 12, TOP 24 — jakostne lige za en dan.'],
          ['Skupine + finalne skupine za mesta', 'Po predtekmovanju igrajo prvi in drugi finalno skupino za 1.–4., tretji in četrti za 5.–8. … Medsebojni izid se prenese.', 'Ekipna tekmovanja po PST 14. člen.'],
          ['SV regija', 'Skupine po 16 (Skupina 1, 2 …), v njih skupine po 4, nato žrebi za vsa mesta.', 'Regijski turnirji, kjer vsak igra do konca.'],
        ],
      },
      { tip: 'razdelek', id: 'izlocilni', naslov: 'Izločilni' },
      {
        tip: 'odstavek',
        besedilo:
          'Velikost mreže je naslednja potenca dvojke (12 igralcev → mreža 16). Prosta mesta dobijo najvišji nosilci, zato v prvem kolu ne igrajo. [Kako so postavljeni nosilci](zreb#mreza)',
      },
      { tip: 'razdelek', id: 'skupine', naslov: 'Skupine + izločilni' },
      {
        tip: 'odstavek',
        besedilo:
          'Skupine se polnijo po jakostnih pasovih, zato je v vsaki en močan igralec. Ko so skupine odigrane, izločilni del **ne nastane sam** — izbereš računalniški žreb ali ročno mrežo. [Izločilni del](izlocilni-del)',
      },
      { tip: 'razdelek', id: 'top', naslov: 'TOP (skupine po jakosti)' },
      {
        tip: 'odstavek',
        besedilo:
          'Igra le toliko najboljših, kolikor je mest (število skupin × velikost skupine); ostali postanejo rezerve. Jakostni vrstni red je edino merilo, zato ga pred žrebom skrbno preveri — v seznamu ga deli črta reza. [Nosilci](nosilci)',
      },
      { tip: 'razdelek', id: 'sv', naslov: 'SV regija' },
      {
        tip: 'odstavek',
        besedilo: 'Ima svoj potek in svoja okna. [SV regija](sv-regija)',
      },
    ],
    povezani: ['zreb', 'izlocilni-del', 'sv-regija', 'razvrstitev'],
  },
  {
    id: 'prijave',
    naslov: 'Prijava igralcev',
    poglavje: 'turnir',
    kratko:
      'Igralce prijaviš v kategoriji: z iskalnikom (Enter jih obkljuka po vrsti), s prilepljenim seznamom imen ali s prenosom z druge kategorije. Nato klikneš »Prijavi izbrane«.',
    kljucne: ['prijava', 'dodaj igralce', 'prilepi', 'seznam imen', 'prenesi', 'odjava', 'odjavi'],
    shemaNamiga: 'prijava',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'kategorija', 'Dodaj igralce'] },
      { tip: 'shema', shema: 'prijava', opis: 'Trije načini prijave in kje so na zaslonu.' },
      {
        tip: 'razdelek',
        id: 'iskalnik',
        naslov: 'Z iskalnikom',
        kratko:
          'Vpiši nekaj črk imena ali priimka (šumniki niso potrebni), izberi s puščicama in pritisni Enter. Igralec je obkljukan, iskalnik se izprazni za naslednjega.',
      },
      {
        tip: 'koraki',
        postavke: [
          'Vpiši del imena ali priimka — »krizan« najde Križana, »novak ana« Ano Novak.',
          'S puščicama ↑ ↓ izberi pravega in pritisni **Enter**. Obkljukan je, iskalnik se izprazni.',
          'Ponavljaj, dokler niso izbrani vsi, nato klikni **Prijavi izbrane (N)**.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'seznam',
        naslov: 'Prilepi seznam imen',
        kratko:
          'Prilepi seznam iz e-pošte ali Excela — vsako ime v svojo vrstico. Aplikacija poišče igralce in ti pokaže, koga je našla, koga ne in kje je več možnosti.',
        shemaNamiga: 'prilepi-seznam',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Vrstni red imena in priimka ni pomemben, številke in klub ob imenu se preskočijo (»3. Bor Novak, NTK Žalec«). Preden prijaviš, preveri vrstice z več možnostmi.',
      },
      { tip: 'shema', shema: 'prilepi-seznam', opis: 'Prilepljen seznam: najden, več možnosti, ni najden.' },
      {
        tip: 'razdelek',
        id: 'prenos',
        naslov: 'Prenesi z drugega dogodka',
        kratko: 'Prijave iz druge kategorije (tudi drugega turnirja) prenese naenkrat — npr. iz posamičnega v dvojice.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Izberi turnir in dogodek, odkljukaj tiste, ki ne igrajo, in potrdi. Igralci, ki so v tej kategoriji že prijavljeni, so izpuščeni.',
      },
      { tip: 'razdelek', id: 'nov-igralec', naslov: 'Igralca ni v registru' },
      {
        tip: 'odstavek',
        besedilo:
          'Dodaj ga na strani **Igralci → + Nov igralec** (ime, priimek, spol in datum rojstva so obvezni). Če datuma ne veš točno, obkljukaj **Datum rojstva je ugiban** — dovolj je za starostno kategorijo, ne poveže pa igralca z njegovim računom. Za rekreativca obkljukaj **Rekreativec**: začne z ratingom 800 in ne z ratingom novinca. [Rating](rating-tekmovanja#novinci)',
      },
      { tip: 'razdelek', id: 'odjava', naslov: 'Odjava pred žrebom' },
      {
        tip: 'odstavek',
        besedilo:
          'Gumb **×** ob igralcu v seznamu prijavljenih ga odjavi. Ponovna prijava istega igralca ga samo vrne na seznam. Po žrebu odjave ni več — takrat je to [odstop](odstop).',
      },
    ],
    povezani: ['nosilci', 'dvojice', 'zreb'],
  },
  {
    id: 'dvojice',
    naslov: 'Dvojice',
    poglavje: 'turnir',
    kratko:
      'Igralce prijaviš posamično, nato jih v razdelku »Poveži v par« povežeš v pare. Žreb zavrne prijavljenega brez soigralca. Dvojice ne štejejo v rating.',
    kljucne: ['par', 'pari', 'soigralec', 'mešane dvojice', 'poveži v par', 'razdruži'],
    shemaNamiga: 'par',
    vsebina: [
      { tip: 'shema', shema: 'par', opis: 'Dve posamični prijavi postaneta ena prijava para.' },
      {
        tip: 'koraki',
        postavke: [
          'Ustvari kategorijo z disciplino **Dvojice**. Pri kategoriji **Mešano** mora vsak par imeti moškega in žensko.',
          'Prijavi igralce posamično, kot pri posamičnem tekmovanju.',
          'V razdelku **Poveži v par** izberi dva igralca in klikni **Poveži v par**. Par se pojavi v seznamu **Pari**.',
          'Ko so vsi v parih, napis pove »Vsi prijavljeni so v parih — žreb je mogoč«.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Napačen par razdružiš v seznamu parov; oba igralca se vrneta med posamične prijave.',
          'Para ni mogoče odjaviti naenkrat — najprej ga razdruži.',
          'Dvojice se žrebajo naključno, brez nosilcev: rating para ne obstaja.',
          'Izidi dvojic ne gredo v rating in ne v posamično statistiko igralca; na profilu imajo svoj razdelek.',
        ],
      },
    ],
    povezani: ['prijave', 'zreb', 'kategorija-nova'],
  },
  {
    id: 'nosilci',
    naslov: 'Jakostni vrstni red in nosilci',
    poglavje: 'turnir',
    kratko:
      'Pred žrebom preveri vrstni red po jakosti. Predlog je po Turnirko ratingu, igralci brez ratinga so na vrhu, da jih zavestno uvrstiš. Prvi v seznamu je 1. nosilec.',
    kljucne: ['nosilec', 'nosilci', 'seed', 'vrstni red', 'jakost', 'rating', 'črta reza', 'rezerve'],
    shemaNamiga: 'vrstni-red',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'kategorija', 'Jakostni vrstni red'] },
      { tip: 'shema', shema: 'vrstni-red', opis: 'Igralca brez ratinga premakneš s puščicama, nato shraniš.' },
      {
        tip: 'koraki',
        postavke: [
          'Preglej seznam. Igralci **brez ratinga** (novinci, tujci) so na vrhu — brez popravka bi postali 1. nosilci.',
          'S puščicama ↑ ↓ premakni igralca na mesto, ki mu po jakosti pripada.',
          'Klikni **Shrani vrstni red**.',
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Žreb vrstni red zapiše vsem igralcem kot številko nosilca, tudi če ga nisi urejal. Po njem se polnijo skupine in mreža. [Kako žreb uporabi nosilce](zreb)',
      },
      {
        tip: 'razdelek',
        id: 'crta-reza',
        naslov: 'Črta reza (TOP)',
        kratko: 'Pri sistemu TOP igra samo toliko najboljših, kolikor je mest. Kdor je pod črto, postane rezerva in ne igra.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Pri sistemu TOP je vrstni red edino merilo: skupina 1 dobi mesta 1–8, skupina 2 mesta 9–16. Kdor je pod črto reza, postane **rezerva**. Pazi na igralce brez ratinga — na vrhu bi izrinili nekoga, ki mu mesto pripada.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Dvojice vrstnega reda nimajo: par se žreba naključno.',
      },
    ],
    povezani: ['zreb', 'sistemi', 'rocni-zreb'],
  },
  {
    id: 'zreb',
    naslov: 'Žreb',
    poglavje: 'turnir',
    kratko:
      'Žreb razporedi igralce v skupine ali mrežo po nosilcih in loči igralce istega kluba, kjer se da. Po žrebu prijav ni več mogoče spreminjati; ponovi ga lahko, dokler se ne začne prva tekma.',
    kljucne: ['žrebanje', 'izvedi žreb', 'ponovi žreb', 'klubska ločitev', 'skupine', 'mreža', 'prosti krog', 'bye'],
    shemaNamiga: 'pasovi',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'kategorija', 'Izvedi žreb'] },
      {
        tip: 'odstavek',
        besedilo:
          'Gumb **Izvedi žreb** je ugasnjen, dokler ni vsaj dveh prijav (pri dvojicah: dokler niso vsi v parih). Žreb vpraša za potrditev, ker za njim prijav in vrstnega reda ni več mogoče spreminjati.',
      },
      {
        tip: 'razdelek',
        id: 'skupine',
        naslov: 'Skupine: po jakostnih pasovih',
        kratko:
          'Prvi nosilci se ne žrebajo: 1. gre v skupino 1, 2. v skupino 2 … Vsak naslednji pas se žreba po enega v vsako skupino. Tako je v vsaki skupini en močan igralec.',
        shemaNamiga: 'pasovi',
      },
      { tip: 'shema', shema: 'pasovi', opis: 'Štiri skupine: prvi pas (1–4) stoji, drugi pas (5–8) se žreba.' },
      {
        tip: 'odstavek',
        besedilo:
          'Nepoln zadnji pas dobijo naključne skupine, ki so zato večje. V skupini nosilec začne z najšibkejšim, dvoboj prvega in drugega pa pride v zadnje kolo.',
      },
      {
        tip: 'razdelek',
        id: 'mreza',
        naslov: 'Mreža: nosilci čim dlje narazen',
        kratko:
          '1. nosilec je na vrhu mreže, 2. na dnu — srečata se šele v finalu. 3. in 4. sta vsak v svoji četrtini (polfinale), 5.–8. v svojih osminah (četrtfinale).',
        shemaNamiga: 'mreza-nosilcev',
      },
      { tip: 'shema', shema: 'mreza-nosilcev', opis: 'Mreža osmih: kje stojijo nosilci in kdaj se srečajo.' },
      {
        tip: 'odstavek',
        besedilo:
          'Prosta mesta (pri številu igralcev, ki ni potenca dvojke) dobijo najvišji nosilci; dve prosti mesti se nikoli ne srečata.',
      },
      {
        tip: 'razdelek',
        id: 'klubi',
        naslov: 'Ločitev igralcev istega kluba',
        kratko:
          'Žreb poskuša, da igralca istega kluba nista v isti skupini oz. se ne srečata v 1. kolu mreže. Če je enega kluba preveč, se trku ni mogoče izogniti.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Žreb naredi več naključnih poskusov in obdrži tistega z najmanj trki. Nosilci pri tem ostanejo na svojih mestih.',
      },
      {
        tip: 'razdelek',
        id: 'ponovi',
        naslov: 'Ponoven žreb in popravek',
        kratko:
          'Dokler se ne začne prva tekma, lahko skupine ali mrežo popraviš (»Popravi skupine«, »Popravi mrežo«) ali žreb ponoviš.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Prva začeta tekma žreb zaklene. Prosti prehod (igralec brez nasprotnika) se ne šteje za začeto tekmo.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Odsotne igralce odjavi **pred** žrebom. Po žrebu je edina pot odstop — njihove tekme dobijo nasprotniki brez boja.',
      },
    ],
    povezani: ['nosilci', 'rocni-zreb', 'izlocilni-del', 'zreb-skupin-tisk'],
  },
  {
    id: 'rocni-zreb',
    naslov: 'Ročni vpis skupin in ročna mreža',
    poglavje: 'turnir',
    kratko:
      'Če ste žrebali na papir, ga prepiši: »Ročni vpis skupin« ali »Ročna mreža«. Izhodišče je računalniški predlog, ki ga popraviš. Po shranjevanju se dogodek začne.',
    kljucne: ['ročno', 'ročni žreb', 'papir', 'vpis skupin', 'ročna mreža', 'popravi skupine', 'popravi mrežo', 'pozicija'],
    shemaNamiga: 'rocne-skupine',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'kategorija', 'Ročni vpis skupin / Ročna mreža'] },
      { tip: 'razdelek', id: 'skupine', naslov: 'Ročni vpis skupin' },
      {
        tip: 'koraki',
        postavke: [
          'Odpri **Ročni vpis skupin**. Okno naloži računalniški predlog in pokaže vse skupine naenkrat.',
          'Klikni igralca, nato drugega: **zamenjata se**. Prosto mesto v drugi skupini sprejme izbranega igralca (**+ Premakni sem**). Število skupin spremeniš s koračnikom, **Predlog žreba** naloži nov predlog, **Razveljavi spremembe** vrne izhodišče.',
          'Pozicija v skupini je številka pred imenom (1 = najmočnejši v svoji skupini); igralec, ki ga premakneš v drugo skupino, pride na njen konec.',
          'Spodaj te stanje opozori, kaj še manjka (»Razporedi še 3 igralce«, »Skupina 13 potrebuje vsaj 2 igralca«). Ko je vse v redu, klikni **Shrani in začni dogodek**.',
        ],
      },
      { tip: 'shema', shema: 'rocne-skupine', opis: 'Vse skupine so vidne naenkrat; klikneš dva igralca in zamenjata se.' },
      {
        tip: 'odstavek',
        besedilo:
          'Pozicija ni nosilec turnirja: vsaka skupina ima svojo 1., 2., 3. pozicijo, brez lukenj. Iz pozicij aplikacija izpelje vrstni red tekem v skupini.',
      },
      { tip: 'razdelek', id: 'mreza', naslov: 'Ročna mreža' },
      {
        tip: 'odstavek',
        besedilo:
          'Mreža je čez cel zaslon, po četrtinah. Igralca **povleci** na drugo mesto in zamenjata se; Enter ali preslednica na mestu ga izbere, drugo mesto ga zamenja. Če igralca povlečeš na **Odlagališče**, mesto ostane prazno in se ne da shraniti, dokler ga ne zapolniš. Na telefonu tapneš mesto in izbereš igralca s seznama. Sosednji mesti igrata v 1. kolu, zato dve prosti mesti ne smeta stati skupaj — v tem paru ne bi igral nihče. Shraniš z **Shrani in začni** (pri popravku **Shrani popravek**).',
      },
      { tip: 'razdelek', id: 'popravek', naslov: 'Popravek po žrebu' },
      {
        tip: 'odstavek',
        besedilo:
          'Po žrebu se gumba imenujeta **Popravi skupine** in **Popravi mrežo**. Na voljo sta, dokler se ne začne prva tekma kategorije.',
      },
    ],
    povezani: ['zreb', 'izlocilni-del', 'nosilci'],
  },
  {
    id: 'izlocilni-del',
    naslov: 'Izločilni del po skupinah',
    poglavje: 'turnir',
    kratko:
      'Ko so skupine odigrane, izločilni del ne nastane sam: izberi »Računalniški žreb« ali »Ročna mreža«. Zmagovalci skupin so nosilci, drugouvrščeni gredo v nasprotno polovico od svojega zmagovalca.',
    kljucne: ['izločilni del', 'mreža po skupinah', 'zmagovalci skupin', 'drugouvrščeni', 'računalniški žreb'],
    shemaNamiga: 'iz-skupin',
    vsebina: [
      {
        tip: 'odstavek',
        besedilo:
          'Na vrhu kategorije se prikaže napis »Vse skupine so odigrane. Izločilni del še ni žreban«. Dokler ga ne izbereš, kategorija ni zaključena.',
      },
      {
        tip: 'koraki',
        postavke: [
          '**Računalniški žreb** — mrežo sestavi po pravilih spodaj.',
          '**Ročna mreža** — mesta vpišeš sam (npr. po papirnatem žrebu).',
          'Dokler se ne začne prva tekma izločilnega dela, lahko **Ponoviš računalniški žreb** ali ga **Popraviš ročno**.',
        ],
      },
      { tip: 'shema', shema: 'iz-skupin', opis: 'Štiri skupine: zmagovalci na nosilska mesta, drugi v nasprotno polovico.' },
      {
        tip: 'seznam',
        postavke: [
          'Zmagovalec skupine 1 je 1. nosilec, skupine 2 2. … Skupine so nastale iz pasov, zato je to hkrati jakostni vrstni red.',
          'Drugouvrščeni gre v **nasprotno polovico** od zmagovalca svoje skupine — soigralca iz skupine se srečata šele v finalu.',
          'Nepoln pas zmagovalcev (npr. 5 skupin) se žreba na vsa mesta svojega pasu.',
          'Prosti krog gre za zmagovalcem skupine.',
          'Igralca istega kluba se v 1. kolu ločita, kjer se da.',
        ],
      },
    ],
    povezani: ['zreb', 'rocni-zreb', 'razvrstitev', 'zreb-mreze-tisk'],
  },
  {
    id: 'sv-regija',
    naslov: 'Sistem SV regija',
    poglavje: 'turnir',
    kratko:
      'Igralci so po jakosti razdeljeni v Skupine 1, 2 … (privzeto po 16). V vsaki igrajo skupine po 4, nato gredo po vrsti A1, B1 … v žrebe od četrtfinala naprej. Žreb se igra za vsa mesta.',
    kljucne: ['SV regija', 'severovzhodna', 'skupina 1', 'nivo', 'žreb za vsa mesta', 'A1 B1'],
    shemaNamiga: 'sv-regija',
    vsebina: [
      { tip: 'shema', shema: 'sv-regija', opis: 'Skupina 16 igralcev: štiri skupine po 4, nato žreb za mesta 1–8 in 9–16.' },
      { tip: 'razdelek', id: 'priprava', naslov: 'Priprava' },
      {
        tip: 'koraki',
        postavke: [
          'Prijavi igralce in uredi jakostni vrstni red — po njem se razdelijo v Skupine.',
          'Določi število Skupin in, če je treba, žrebe zadnje Skupine.',
          'Skupine po 4 lahko vpišeš ročno (svoje okno za SV regijo).',
        ],
      },
      { tip: 'razdelek', id: 'skupina-4', naslov: 'Vrstni red tekem v skupini štirih' },
      {
        tip: 'tabela',
        glava: ['Kolo', 'Tekmi'],
        vrstice: [
          ['1.', '1–4, 2–3'],
          ['2.', '4–3, 1–2'],
          ['3.', '2–4, 3–1'],
        ],
      },
      {
        tip: 'razdelek',
        id: 'zrebi',
        naslov: 'Žrebi za mesta',
        kratko:
          'Ko so skupine ene Skupine odigrane, gredo igralci po vrsti A1, B1 …, A2, B2 … v žrebe od četrtfinala. Vsak žreb se igra do konca, za vsa mesta.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Skupine tečejo neodvisno: žreb Skupine 1 nastane, ko so odigrane njene skupine, tudi če Skupina 2 še igra. Žrebe Skupine lahko spremeniš tudi po žrebu skupin, dokler se ne začne prva tekma žrebov.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Izpis rezultatov ima pri SV regiji še slog **SVR** — pokončni A4 kot v Excelu regije. [Izpis rezultatov](izpis-rezultatov#svr)',
      },
    ],
    povezani: ['sistemi', 'zreb', 'izpis-rezultatov'],
  },
  {
    id: 'ekipni-dogodek',
    naslov: 'Ekipni dogodek na turnirju',
    poglavje: 'turnir',
    kratko:
      'Ekipe s kadrom tekmujejo v mreži ali skupinah. Vsaka ekipna tekma ima svoje srečanje z zapisnikom (postava in posamične tekme), izid srečanja gre v mrežo oz. skupino.',
    kljucne: ['ekipno', 'ekipe', 'ekipni turnir', 'kader', 'ekipna tekma', 'DŠI', 'corbillon'],
    shemaNamiga: 'ekipna-tekma',
    vsebina: [
      {
        tip: 'koraki',
        postavke: [
          'Ustvari dogodek z disciplino **Ekipno** in izberi **Format srečanja** (vrstni red tekem).',
          'Dodaj ekipe in vsaki kader. Igralec sme biti v kadru ene same ekipe dogodka; kader mora imeti vsaj toliko igralcev, kot jih zahteva format.',
          'Izvedi žreb kot pri posamičnem tekmovanju.',
          'Klikni ekipno tekmo, da odpreš zapisnik srečanja: vpiši postavo, nato izide posamičnih tekem.',
        ],
      },
      { tip: 'shema', shema: 'ekipna-tekma', opis: 'Ekipna tekma v mreži je srečanje s posamičnimi tekmami.' },
      {
        tip: 'seznam',
        postavke: [
          'Neposredno na ekipni tekmi vpišeš samo izid **brez boja** ali **diskvalifikacijo**; vse drugo nastane iz zapisnika.',
          'V rating gredo posamične tekme srečanja, ekipna tekma nikoli.',
          'Kader lahko dopolniš tudi med tekmovanjem (rezerva), ime ekipe pa popraviš kadarkoli.',
          'Napačen zapisnik razveljaviš na dnu zapisnika. [Razveljavitev](razveljavitev-zapisnika)',
        ],
      },
    ],
    povezani: ['format-srecanja', 'zapisnik-srecanja', 'zapisnik-tisk'],
  },
  {
    id: 'zakljucek-turnirja',
    naslov: 'Zaključek kategorije in turnirja',
    poglavje: 'turnir',
    kratko:
      'Kategorija se zaključi sama ob zadnji tekmi in dodeli končna mesta. Turnir zaključiš sam z »Zaključi turnir«, ko so zaključene vse kategorije — tega ni mogoče preklicati.',
    kljucne: ['zaključi', 'konec', 'zaključek', 'končna mesta'],
    shemaNamiga: 'statusi',
    vsebina: [
      { tip: 'kje', pot: ['Turnir', 'Zaključi turnir'] },
      {
        tip: 'seznam',
        postavke: [
          'Kategorija s skupinami in izločilnim delom se zaključi šele, ko izbereš izločilni del in se ta odigra.',
          'Pri sistemu TOP vsak igralec dobi mesto v svoji skupini; skupne razvrstitve čez skupine ni.',
          'Zaključen turnir ostane popravljiv: popravek rezultata je mogoč tudi po zaključku. [Popravek](popravek-rezultata)',
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Po zaključku kategorije se pojavi gumb **Natisni rezultate** za oglasno desko ali zapisnik turnirja. [Izpis rezultatov](izpis-rezultatov)',
      },
    ],
    povezani: ['statusi', 'izpis-rezultatov', 'razvrstitev'],
  },
]
