/* Pogoji uporabe in varstvo osebnih podatkov (/pogoji, zasebnost je
   /pogoji#zasebnost).

   Registracija zahteva kljukico »Strinjam se s pogoji uporabe in obdelavo
   podatkov« - brez te strani se je strinjala z nečim, česar ni bilo (glej
   docs/VARNOSTNI-PREGLED.md, V18). Povezave nanjo stojijo tam, kjer se človek
   za kaj odloči: ob registraciji, ob prijavi in ob nakupu naročnine.

   Besedilo opisuje, kar aplikacija res počne (kateri podatki, kje se
   prikažejo, kdo jih obdeluje, kdaj se brišejo). Podatki o ponudniku so v
   pomozno/pravno.ts. Preden se na besedilo zanesemo, naj ga pregleda nekdo,
   ki pozna ZVOP-2 in ZVPot-1 - Turnirko ni pravnik. */
import { useEffect, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { useNaslovStrani } from '../pomozno/naslovStrani'
import { oblikujDatum } from '../pomozno/oblikovanje'
import { PONUDNIK, POGOJI_VELJAJO_OD } from '../pomozno/pravno'

export function PogojiStran() {
  useNaslovStrani('Pogoji uporabe')
  const lokacija = useLocation()

  /* Povezava z registracije pride s sidrom (#zasebnost). */
  useEffect(() => {
    if (lokacija.hash) document.getElementById(lokacija.hash.slice(1))?.scrollIntoView()
  }, [lokacija.hash])

  return (
    <section className="razlaga pogoji">
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Turnirko</span>
            <span className="naslov-strani__glavni">Pogoji in zasebnost</span>
          </h1>
          <p className="uvod">
            Turnirko je spletna stran za namizni tenis: rezultati turnirjev in lig, koledar,
            lestvica in profili igralcev. Upravlja jo {PONUDNIK.ime}.
            {POGOJI_VELJAJO_OD && ` Pogoji veljajo od ${oblikujDatum(POGOJI_VELJAJO_OD)}.`}
          </p>
        </div>
      </div>

      <nav className="razlaga__kazalo" aria-label="Vsebina strani">
        <a href="#pogoji">Pogoji uporabe</a>
        <a href="#zasebnost">Varstvo osebnih podatkov</a>
        <a href="#kontakt">Kontakt</a>
      </nav>

      <Sekcija id="pogoji" naslov="Pogoji uporabe">
        <Odstavek naslov="Kaj je javno">
          Rezultati tekmovanj, lige, koledar, lestvica in profili igralcev so dostopni vsem, brez
          prijave. Na profilu in v rezultatih se prikažejo ime, priimek, klub, starostna
          kategorija, odigrane tekme in Turnirko rating. Rezultati uradnih tekmovanj so
          prevzeti iz javnih objav Namiznoteniške zveze Slovenije (NTZS); uradni rezultati in
          jakostne lestvice so pri NTZS. Turnirko rating je lastna ocena Turnirka in ni uradna
          lestvica NTZS (<Link to="/o-ratingu">kako deluje</Link>).
        </Odstavek>
        <Odstavek naslov="Račun">
          Za račun potrebuješ e-poštni naslov, ki ga potrdiš s kodo. Vpiši resnične podatke in
          gesla ne deli z drugimi. Mlajši od 15 let potrebujejo soglasje starša ali skrbnika, ki
          ga ta da s kodo, poslano na svoj naslov. Račun, ki se uporablja v nasprotju s temi
          pogoji, lahko onemogočimo.
        </Odstavek>
        <Odstavek naslov="Naročnina Premium in paketi organizatorjev">
          Cena je izpisana pred nakupom. Plačilo obdela Stripe. Naročnina se ob koncu obdobja
          (mesec ali leto) samodejno obnovi, dokler je ne prekličeš. Prekličeš jo kadar koli na
          strani <Link to="/narocnina">Naročnina</Link>; velja do konca že plačanega obdobja.
          Preklop med mesečnim in letnim plačilom velja ob naslednji obnovi. Kot potrošnik lahko
          od pogodbe odstopiš v 14 dneh od nakupa brez navedbe razloga — piši nam; vrnemo
          plačilo, zmanjšano za že porabljeni del obdobja.
        </Odstavek>
        <Odstavek naslov="Česa ne smeš">
          Samodejno zajemati podatkov s strani (programi, ki strani prenašajo v velikem
          obsegu), obremenjevati ali ovirati delovanja strani, se predstavljati kot kdo drug ali
          vpisovati rezultatov, ki se niso zgodili.
        </Odstavek>
        <Odstavek naslov="Točnost in odgovornost">
          Rezultate vnašajo organizatorji tekmovanj in jih prepisujejo s papirja, zato se lahko
          zgodi napaka. Če jo opaziš, jo sporoči organizatorju tekmovanja ali nam — popravek se
          upošteva tudi v ratingu. Stran se trudimo imeti vedno dosegljivo, a za izpade in
          posledice napak v podatkih ne odgovarjamo.
        </Odstavek>
        <Odstavek naslov="Spremembe pogojev">
          Spremembe objavimo na tej strani. O bistvenih spremembah imetnike računov obvestimo po
          e-pošti.
        </Odstavek>
      </Sekcija>

      <Sekcija id="zasebnost" naslov="Varstvo osebnih podatkov">
        <Odstavek naslov="Kdo je upravljavec">
          Upravljavec osebnih podatkov je {PONUDNIK.ime}
          {PONUDNIK.naslov && `, ${PONUDNIK.naslov}`}.
          {PONUDNIK.eposta && (
            <>
              {' '}Kontakt: <a href={`mailto:${PONUDNIK.eposta}`}>{PONUDNIK.eposta}</a>.
            </>
          )}
        </Odstavek>
        <Odstavek naslov="Igralci na tekmovanjih">
          Hranimo ime, priimek, spol, klub, datum rojstva in rezultate tekem. Podatke vpišejo
          organizatorji tekmovanj oziroma so prevzeti iz javnih rezultatov NTZS. Javno se
          prikažejo samo ime, priimek, klub, starostna kategorija, rezultati in rating.{' '}
          <strong>Datum rojstva ni javen</strong>: uporablja se le za starostno kategorijo in
          za ločevanje igralcev z enakim imenom. Podlaga je zakoniti interes vodenja javne
          zgodovine tekmovanj.
        </Odstavek>
        <Odstavek naslov="Imetniki računov">
          Ob registraciji hranimo e-poštni naslov, geslo (samo kot zgostitev — prebrati ga ne
          more nihče), ime, priimek, klub in datum rojstva, ki služi povezavi računa z zapisom
          igralca. Pri mlajših od 15 let hranimo še e-poštni naslov starša ali skrbnika. Podlaga
          je pogodba (uporaba računa).
        </Odstavek>
        <Odstavek naslov="Plačila">
          Plačilo s kartico obdela Stripe; številke kartice Turnirko ne vidi in ne hrani. Hranimo
          stanje naročnine (paket, obdobje, cena) in podatke, ki jih zahtevajo računovodski
          predpisi.
        </Odstavek>
        <Odstavek naslov="Obisk strani">
          Piškotkov ne uporabljamo. Statistika obiska teče na našem strežniku, brez piškotkov in
          brez osebnih podatkov; izklopiš jo z obiskom naslova{' '}
          <a href="/?statistika=izklop">turnirko-nt.si/?statistika=izklop</a>. Strežnik zaradi
          varnosti kratek čas hrani dnevnik zahtev z naslovi IP, nato se prepiše. V brskalniku
          ostanejo le nastavitve (npr. nazadnje ogledane lige) in prijava za trenutno sejo.
        </Odstavek>
        <Odstavek naslov="Kdo še obdeluje podatke">
          Stripe (plačila), Brevo (pošiljanje e-pošte s potrditvenimi kodami)
          {PONUDNIK.gostovanje ? ` in ${PONUDNIK.gostovanje} (strežnik)` : ' in ponudnik strežnika'}.
          Podatkov ne prodajamo in jih ne posredujemo za oglaševanje.
        </Odstavek>
        <Odstavek naslov="Koliko časa">
          Račun, katerega e-pošta ni potrjena, se izbriše po 48 urah; račun mlajšega od 15 let
          brez soglasja skrbnika po 30 dneh. Račun izbrišemo na tvojo zahtevo. Rezultati tekmovanj
          ostanejo kot zapis zgodovine tekmovanja.
        </Odstavek>
        <Odstavek naslov="Tvoje pravice">
          Imaš pravico do vpogleda v svoje podatke, popravka, izbrisa, omejitve obdelave in
          ugovora. Zahtevo pošlji na kontakt spodaj; pri rezultatih tekmovanj jo presodimo
          posebej, ker so del javne zgodovine tekmovanja. Pritožbo lahko vložiš pri
          Informacijskem pooblaščencu (<a href="https://www.ip-rs.si">www.ip-rs.si</a>).
        </Odstavek>
      </Sekcija>

      <Sekcija id="kontakt" naslov="Kontakt">
        <ul className="seznam-preprost">
          <li>{PONUDNIK.ime}</li>
          {PONUDNIK.naslov && <li>{PONUDNIK.naslov}</li>}
          {PONUDNIK.maticna && <li>Matična številka: {PONUDNIK.maticna}</li>}
          {PONUDNIK.davcna && <li>Davčna številka: {PONUDNIK.davcna}</li>}
          {PONUDNIK.eposta && (
            <li>
              E-pošta: <a href={`mailto:${PONUDNIK.eposta}`}>{PONUDNIK.eposta}</a>
            </li>
          )}
        </ul>
      </Sekcija>
    </section>
  )
}

function Sekcija({ id, naslov, children }: { id: string; naslov: string; children: ReactNode }) {
  return (
    <section id={id} className="razlaga__sekcija" aria-labelledby={`${id}-naslov`}>
      <div className="naslovna-vrstica">
        <h2 id={`${id}-naslov`}>{naslov}</h2>
      </div>
      {children}
    </section>
  )
}

function Odstavek({ naslov, children }: { naslov: string; children: ReactNode }) {
  return (
    <>
      <h3 className="pogoji__naslov">{naslov}</h3>
      <p className="pogoji__odstavek">{children}</p>
    </>
  )
}
