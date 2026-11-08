/* Izracun lestvice za krozni sistem in za skupine (tudi format TOP).

   Merila razvrstitve (po vrsti):
     1. vec zmag,
     2. manj porazov (loci tistega, ki je odigral manj tekem, od kroga),
     3. KROG - kdor je izenacen po zmagah IN porazih, se razvrsti SAMO po
        tekmah, ki so jih clani kroga odigrali med seboj (PST 20. clen:
        "odloca rezultat medsebojnih dvobojev"):
          a) vec zmag nad clani kroga,
          b) ce je zmag enako, boljsa razlika nizov iz teh tekem,
          c) ce je enaka tudi ta, boljsa razlika tock (le tam, kjer so tocke
             po nizih vnesene),
        po vsakem uspesnem razrezu se postopek PONOVI znotraj vsakega
        nastalega (manjsega) kroga - dvojico tako vedno razsodi njuna
        medsebojna tekma, ker je razlika nizov v njej nujno na eno stran;
     4. sele ce medsebojne tekme ne razsodijo nicesar (npr. sredi skupine, ko
        se se niso igrali): boljsa razlika nizov v celi skupini, vec dobljenih
        nizov, jakostno mesto z zreba (Prijava.stNosilca), abecedno po imenu
        (da je vrstni red vedno enolicen).

   Primer kroga (skupina A, B, C, D; A dobi vse tri tekme, ostali po eno):
     B-C 3:0, C-D 3:1, D-B 3:0  ->  zmage v krogu povsod 1, razlika nizov pa
     B 0, C -1, D +1  ->  vrstni red A, D, B, C.
   Skupna razlika nizov tu NE odloca - steje samo, kar se je zgodilo med
   clani kroga. Zato je lahko drugouvrsceni po SKUPNI razliki nizov slabsi od
   tretjeuvrscenega; to je pravilno in ne napaka izpisa. Prav zato vrstica
   lestvice nosi se `krog`: kdor je izenacen z igralci, s katerimi je odigral
   vsaj eno tekmo, dobi njegovo zaporedno stevilko, da prikaz to lahko pojasni.

   Razvrscanje sproti zapise, kaj je v krogu odlocalo (KrogDto, ZapisKroga):
   gledalec si izracun odpre z gumbom pod lestvico, zato mora razlaga povedati
   natanko tisto, kar je vrstni red dolocilo, in ne svoje kopije pravil.

   Jakostno mesto je predzadnje merilo in ne okras: dokler skupina ni zaceta,
   so vsi izenaceni na 0 in lestvica je hkrati IZPIS SKUPINE. Po abecedi bi
   nosilec skupine pristal sredi seznama, pa je po pravilih zreba prvi
   zapisani. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import si.turnirko.dto.KrogDto;
import si.turnirko.dto.NizVnos;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.Tekma;
import si.turnirko.pomozno.SlovenskaAbeceda;
import si.turnirko.repozitoriji.NizRepozitorij;

@Service
public class RazvrstitevStoritev {

    /* Merila znotraj kroga, po vrsti. */
    private static final int MERILO_ZMAGE = 0;
    private static final int MERILO_NIZI = 1;
    private static final int MERILO_TOCKE = 2;
    private static final KrogDto.Merilo[] IMENA_MERIL = {
            KrogDto.Merilo.ZMAGE, KrogDto.Merilo.NIZI, KrogDto.Merilo.TOCKE};

    private final NizRepozitorij nizRepozitorij;

    public RazvrstitevStoritev(NizRepozitorij nizRepozitorij) {
        this.nizRepozitorij = nizRepozitorij;
    }

    /* Urejena lestvica in izracun krogov, ki so jo dolocili. */
    public record Razvrstitev(List<VrsticaLestviceDto> vrstice, List<KrogDto> krogi) {}

    /* Vmesni sestevek uspeha enega udelezenca v CELI skupini. */
    private static final class Vmesni {
        final Prijava prijava;
        int odigrane, zmage, porazi, niziZa, niziProti;
        Vmesni(Prijava prijava) { this.prijava = prijava; }
        Long id() { return prijava.getId(); }
    }

    /* Ena upostevana medsebojna tekma, zozena na to, kar rabi razvrstitev.
       prvi/nizi1 pripadata prijavi1 tekme, drugi/nizi2 pa prijavi2.
       Zmagovalec je zapisan posebej: pri PREDAJI ga iz nizov ni mogoce
       izpeljati, ker ostane delni rezultat (zmagovalec ima lahko manj nizov). */
    private record Dvoboj(Long idTekme, Long prvi, Long drugi,
                          int nizi1, int nizi2, Long idZmagovalca, IzidTekme izid) {}

    /* Zadnja merila - v igro pridejo sele, ko medsebojne tekme ne razsodijo. */
    private static final Comparator<Vmesni> ZADNJA_MERILA = (a, b) -> {
        int razA = a.niziZa - a.niziProti;
        int razB = b.niziZa - b.niziProti;
        if (razA != razB) return razB - razA;
        if (a.niziZa != b.niziZa) return b.niziZa - a.niziZa;
        Integer nosilecA = a.prijava.getStNosilca();
        Integer nosilecB = b.prijava.getStNosilca();
        if (nosilecA != null && nosilecB != null && !nosilecA.equals(nosilecB)) {
            return nosilecA - nosilecB;
        }
        return SlovenskaAbeceda.primerjaj(abecedno(a.prijava), abecedno(b.prijava));
    };

    /* Urejevalni kljuc po imenu: igralec po priimku, ekipa po imenu (V28). */
    private static String abecedno(Prijava p) {
        return p.jeEkipa() ? p.getEkipa().prikazanoIme() : p.getIgralec().abecedno();
    }

    /* Izid, ki pri izracunu velja namesto shranjenega - kaksna bi bila lestvica po
       popravku, preden se popravek zapise. Tocke so null, kadar niso vpisane. */
    public record NadomestniIzid(Long idTekme, int nizi1, int nizi2, Long idZmagovalca,
                                 List<NizVnos> tocke) {}

    /* Urejena lestvica danih udelezencev iz njihovih KONCANIH medsebojnih
       tekem. Uposteva samo tekme, kjer sta oba udelezenca s tega seznama. */
    public List<VrsticaLestviceDto> lestvica(List<Prijava> udelezenci, List<Tekma> tekme) {
        return lestvica(udelezenci, tekme, null);
    }

    public List<VrsticaLestviceDto> lestvica(List<Prijava> udelezenci, List<Tekma> tekme,
                                             NadomestniIzid nadomestni) {
        return razvrsti(udelezenci, tekme, nadomestni).vrstice();
    }

    /* Ista lestvica skupaj z izracunom krogov - za prikaz gledalcu. */
    public Razvrstitev razvrsti(List<Prijava> udelezenci, List<Tekma> tekme) {
        return razvrsti(udelezenci, tekme, null);
    }

    private Razvrstitev razvrsti(List<Prijava> udelezenci, List<Tekma> tekme,
                                 NadomestniIzid nadomestni) {
        Map<Long, Vmesni> po = new LinkedHashMap<>();
        for (Prijava p : udelezenci) {
            po.put(p.getId(), new Vmesni(p));
        }

        List<Dvoboj> dvoboji = new ArrayList<>();
        boolean vseTekmeOdigrane = true;

        for (Tekma t : tekme) {
            if (t.getPrijava1() == null || t.getPrijava2() == null) continue;
            Vmesni v1 = po.get(t.getPrijava1().getId());
            Vmesni v2 = po.get(t.getPrijava2().getId());
            if (v1 == null || v2 == null) continue; // tekma ni znotraj te lestvice
            if (t.getStatus() != StatusTekme.KONCANA || t.getZmagovalec() == null) {
                vseTekmeOdigrane = false;
                continue;
            }

            boolean nadomescena = nadomestni != null && nadomestni.idTekme().equals(t.getId());
            int nizi1 = nadomescena ? nadomestni.nizi1() : t.getDobljeniNizi1();
            int nizi2 = nadomescena ? nadomestni.nizi2() : t.getDobljeniNizi2();
            Long idZmagovalca = nadomescena ? nadomestni.idZmagovalca() : t.getZmagovalec().getId();

            v1.odigrane++;
            v2.odigrane++;
            v1.niziZa += nizi1;
            v1.niziProti += nizi2;
            v2.niziZa += nizi2;
            v2.niziProti += nizi1;

            boolean prviZmagal = idZmagovalca.equals(t.getPrijava1().getId());
            (prviZmagal ? v1 : v2).zmage++;
            (prviZmagal ? v2 : v1).porazi++;

            dvoboji.add(new Dvoboj(t.getId(), v1.id(), v2.id(), nizi1, nizi2,
                    prviZmagal ? v1.id() : v2.id(), t.getIzidTip()));
        }

        // 1. korak: zmage, nato porazi. Kdor je izenacen v obojem, je "v krogu"
        // in ga naprej razvrsti le se medsebojni izkupicek.
        List<Vmesni> poIzkupicku = new ArrayList<>(po.values());
        poIzkupicku.sort(Comparator.comparingInt((Vmesni v) -> -v.zmage)
                .thenComparingInt(v -> v.porazi));

        Kontekst kontekst = new Kontekst(dvoboji, nadomestni);
        List<Vmesni> urejeni = new ArrayList<>(poIzkupicku.size());
        Map<Long, Integer> stevilkeKrogov = new HashMap<>();
        List<KrogDto> krogi = new ArrayList<>();
        int zacetek = 0;
        while (zacetek < poIzkupicku.size()) {
            int konec = zacetek + 1;
            while (konec < poIzkupicku.size()
                    && vIstemKrogu(poIzkupicku.get(zacetek), poIzkupicku.get(konec))) {
                konec++;
            }
            ZapisKroga zapis = new ZapisKroga(kontekst);
            List<Vmesni> krog = razvrstiKrog(
                    new ArrayList<>(poIzkupicku.subList(zacetek, konec)),
                    kontekst, MERILO_ZMAGE, zapis, true);
            // Krog je vreden oznake in izracuna, cim so se izenaceni igrali med
            // seboj - tudi ce je med njimi vse enako in odloci zadnje merilo, ker
            // gledalec prav tam najbolj sprasuje, zakaj je vrstni red tak.
            // Izenaceni, ki se se niso srecali (zacetek skupine), kroga nimajo.
            if (krog.size() > 1 && kontekst.tekemMed(krog) > 0) {
                int stevilka = krogi.size() + 1;
                for (Vmesni v : krog) {
                    stevilkeKrogov.put(v.id(), stevilka);
                }
                krogi.add(zapis.dto(krog, zacetek + 1, vseTekmeOdigrane));
            }
            urejeni.addAll(krog);
            zacetek = konec;
        }

        List<VrsticaLestviceDto> vrstice = new ArrayList<>();
        int mesto = 1;
        for (Vmesni v : urejeni) {
            vrstice.add(new VrsticaLestviceDto(
                    v.prijava.getId(),
                    v.prijava.jeEkipa() ? null : v.prijava.getIgralec().getId(),
                    v.prijava.prikazanoIme(),
                    v.prijava.getKlubObPrijavi() != null ? v.prijava.getKlubObPrijavi().getIme() : null,
                    v.odigrane, v.zmage, v.porazi, v.niziZa, v.niziProti, mesto++,
                    stevilkeKrogov.get(v.id())));
        }
        return new Razvrstitev(vrstice, krogi);
    }

    /* Krog: enako zmag IN enako porazov. */
    private static boolean vIstemKrogu(Vmesni a, Vmesni b) {
        return a.zmage == b.zmage && a.porazi == b.porazi;
    }

    /* Razvrsti clane enega kroga po merilih iz njihovih MEDSEBOJNIH tekem.
       Ko merilo krog razbije, se za vsak nastali manjsi krog postopek zacne
       znova pri prvem merilu (v manjsem krogu so vrednosti druge!). Ko merilo
       ne razbije nicesar, gre na naslednje merilo, na koncu na ZADNJA_MERILA.
       Rekurzija se vedno konca: razbiti kos je strogo manjsi od kroga.
       koren pove, da je to se cel krog in ne kos po razrezu. */
    private List<Vmesni> razvrstiKrog(List<Vmesni> krog, Kontekst kontekst, int merilo,
                                      ZapisKroga zapis, boolean koren) {
        if (krog.size() <= 1) return krog;
        if (merilo > MERILO_TOCKE) {
            krog.sort(ZADNJA_MERILA);
            zapis.zadnjaMerila(krog);
            return krog;
        }

        Izkupicki izkupicki = izkupickiVKrogu(krog, kontekst, merilo);

        // kosi po enaki vrednosti, od najboljse navzdol
        TreeMap<Integer, List<Vmesni>> kosi = new TreeMap<>(Comparator.reverseOrder());
        for (Vmesni v : krog) {
            kosi.computeIfAbsent(izkupicki.vrednost(v.id(), merilo), k -> new ArrayList<>()).add(v);
        }
        boolean razbil = kosi.size() > 1;
        zapis.korak(merilo, krog, izkupicki, razbil, koren);
        if (!razbil) {
            return razvrstiKrog(krog, kontekst, merilo + 1, zapis, koren); // merilo ni razsodilo
        }

        List<Vmesni> urejeni = new ArrayList<>(krog.size());
        for (List<Vmesni> kos : kosi.values()) {
            urejeni.addAll(razvrstiKrog(kos, kontekst, MERILO_ZMAGE, zapis, false));
        }
        return urejeni;
    }

    /* Izkupicek vsakega clana kroga, izracunan SAMO iz tekem med clani kroga:
       zmage in porazi, dobljeni in izgubljeni nizi ali tocke. Tekma brez
       vnesenih tock po nizih pri merilu tock odpade - upostevajo se tiste, ki
       jih imajo. */
    private Izkupicki izkupickiVKrogu(List<Vmesni> krog, Kontekst kontekst, int merilo) {
        Map<Long, int[]> poClanu = new HashMap<>();
        for (Vmesni v : krog) {
            poClanu.put(v.id(), new int[2]);
        }
        int tekem = 0;
        int brezTock = 0;
        for (Dvoboj d : kontekst.dvoboji) {
            int[] prvi = poClanu.get(d.prvi());
            int[] drugi = poClanu.get(d.drugi());
            if (prvi == null || drugi == null) continue;
            tekem++;
            if (merilo == MERILO_ZMAGE) {
                boolean prviZmagal = d.idZmagovalca().equals(d.prvi());
                (prviZmagal ? prvi : drugi)[0]++;
                (prviZmagal ? drugi : prvi)[1]++;
                continue;
            }
            int za1 = d.nizi1();
            int za2 = d.nizi2();
            if (merilo == MERILO_TOCKE) {
                int[] tocke = kontekst.tocke().get(d.idTekme());
                if (tocke == null) { // tocke po nizih niso vnesene
                    brezTock++;
                    continue;
                }
                za1 = tocke[0];
                za2 = tocke[1];
            }
            prvi[0] += za1;
            prvi[1] += za2;
            drugi[0] += za2;
            drugi[1] += za1;
        }
        return new Izkupicki(poClanu, tekem, brezTock);
    }

    /* Rezultat enega merila v enem krogu. poClanu: id prijave -> [za, proti]. */
    private record Izkupicki(Map<Long, int[]> poClanu, int tekem, int brezTock) {

        /* Vrednost merila, po kateri se razvrsca (vec je bolje). */
        int vrednost(Long id, int merilo) {
            int[] izkupicek = poClanu.get(id);
            return merilo == MERILO_ZMAGE ? izkupicek[0] : izkupicek[0] - izkupicek[1];
        }

        KrogDto.Izkupicek zaClana(Long id) {
            int[] izkupicek = poClanu.get(id);
            return new KrogDto.Izkupicek(izkupicek[0], izkupicek[1]);
        }
    }

    /* Sproti zapisuje, kaj je pri razvrscanju enega kroga odlocalo.

       Prikaz je ena sama tabela, zato ima vsak clan za vsako merilo eno celico.
       Prva pojavitev merila gre v njegovo celico, ponovitev (ko se postopek
       ponovi v manjsem krogu, ki je ze imel to merilo) pa v `medNjimi`. Zmage,
       ki ne razbijejo nicesar, se ne zapisejo: to pove en stavek nad tabelo.
       Nizi se zapisejo tudi, ko so enaki, ker so razlog, da stejejo tocke. */
    private static final class ZapisKroga {

        private static final class Celice {
            Integer zmage;
            KrogDto.Izkupicek nizi;
            KrogDto.Izkupicek tocke;
            final List<KrogDto.Korak> medNjimi = new ArrayList<>();
        }

        private final Kontekst kontekst;
        private final Map<Long, Celice> celice = new HashMap<>();
        private final List<KrogDto.Opomba> opombe = new ArrayList<>();
        private KrogDto.Merilo odlocilo;
        private Integer enakeZmage;
        private boolean tockeManjkajo;

        ZapisKroga(Kontekst kontekst) {
            this.kontekst = kontekst;
        }

        private Celice celice(Vmesni v) {
            return celice.computeIfAbsent(v.id(), k -> new Celice());
        }

        void korak(int merilo, List<Vmesni> krog, Izkupicki izkupicki, boolean razbil, boolean koren) {
            if (izkupicki.tekem() == 0) return; // se niso igrali, stevilke bi bile same nicle
            if (koren && razbil) {
                odlocilo = IMENA_MERIL[merilo];
            }
            if (merilo == MERILO_ZMAGE) {
                zmage(krog, izkupicki, razbil, koren);
                return;
            }
            if (merilo == MERILO_TOCKE) {
                tockeManjkajo |= izkupicki.brezTock() > 0;
                if (izkupicki.brezTock() == izkupicki.tekem()) return; // nicesar za pokazati
            }
            for (Vmesni v : krog) {
                Celice c = celice(v);
                KrogDto.Izkupicek izkupicek = izkupicki.zaClana(v.id());
                if (merilo == MERILO_NIZI && c.nizi == null) {
                    c.nizi = izkupicek;
                } else if (merilo == MERILO_TOCKE && c.tocke == null) {
                    c.tocke = izkupicek;
                } else {
                    c.medNjimi.add(new KrogDto.Korak(IMENA_MERIL[merilo],
                            izkupicek.za(), izkupicek.proti(), null));
                }
            }
        }

        private void zmage(List<Vmesni> krog, Izkupicki izkupicki, boolean razbil, boolean koren) {
            if (!razbil) {
                if (koren) enakeZmage = izkupicki.vrednost(krog.get(0).id(), MERILO_ZMAGE);
                return;
            }
            for (Vmesni v : krog) {
                Celice c = celice(v);
                KrogDto.Izkupicek izkupicek = izkupicki.zaClana(v.id());
                if (koren) {
                    c.zmage = izkupicek.za();
                } else if (krog.size() == 2) {
                    // Dvojico razsodi njuna tekma: zmage 1 : 0 ne povedo nicesar,
                    // izid tekme pa pove vse in ga gledalec lahko preveri.
                    c.medNjimi.add(njunaTekma(v, krog));
                } else {
                    c.medNjimi.add(new KrogDto.Korak(KrogDto.Merilo.ZMAGE,
                            izkupicek.za(), izkupicek.proti(), null));
                }
            }
        }

        private KrogDto.Korak njunaTekma(Vmesni v, List<Vmesni> par) {
            int za = 0;
            int proti = 0;
            IzidTekme izid = null;
            for (Dvoboj d : kontekst.dvobojiMed(par)) {
                boolean jePrvi = d.prvi().equals(v.id());
                za += jePrvi ? d.nizi1() : d.nizi2();
                proti += jePrvi ? d.nizi2() : d.nizi1();
                izid = neobicajenIzid(d);
            }
            return new KrogDto.Korak(KrogDto.Merilo.TEKMA, za, proti, izid);
        }

        void zadnjaMerila(List<Vmesni> urejeni) {
            List<KrogDto.Odlocitev> odlocitve = new ArrayList<>();
            razloziZadnja(urejeni, 0, odlocitve);
            opombe.add(new KrogDto.Opomba(kontekst.tekemMed(urejeni) == 0, odlocitve));
        }

        /* Za urejene clane zapise vsako zadnje merilo, ki je koga res locilo:
           razlika nizov -1, -1, -2 se zapise in znotraj prvih dveh se naslednje. */
        private static void razloziZadnja(List<Vmesni> urejeni, int raven,
                                          List<KrogDto.Odlocitev> izhod) {
            KrogDto.ZadnjeMerilo[] merila = KrogDto.ZadnjeMerilo.values();
            if (urejeni.size() < 2 || raven >= merila.length) return;
            KrogDto.ZadnjeMerilo merilo = merila[raven];

            List<List<Vmesni>> kosi = new ArrayList<>();
            Vmesni prejsnji = null;
            for (Vmesni v : urejeni) {
                if (prejsnji == null || !enakaPoZadnjem(prejsnji, v, merilo)) {
                    kosi.add(new ArrayList<>());
                }
                kosi.get(kosi.size() - 1).add(v);
                prejsnji = v;
            }
            if (kosi.size() > 1) {
                izhod.add(new KrogDto.Odlocitev(merilo, urejeni.stream()
                        .map(v -> new KrogDto.Vrednost(v.prijava.prikazanoIme(), vrednostZadnjega(v, merilo)))
                        .toList()));
            }
            for (List<Vmesni> kos : kosi) {
                razloziZadnja(kos, raven + 1, izhod);
            }
        }

        private static boolean enakaPoZadnjem(Vmesni a, Vmesni b, KrogDto.ZadnjeMerilo merilo) {
            return switch (merilo) {
                case ABECEDA -> false;
                // isto kot ZADNJA_MERILA: brez jakostnega mesta odloci abeceda
                case JAKOSTNO_MESTO -> a.prijava.getStNosilca() == null || b.prijava.getStNosilca() == null
                        || a.prijava.getStNosilca().equals(b.prijava.getStNosilca());
                default -> vrednostZadnjega(a, merilo).equals(vrednostZadnjega(b, merilo));
            };
        }

        private static Integer vrednostZadnjega(Vmesni v, KrogDto.ZadnjeMerilo merilo) {
            return switch (merilo) {
                case RAZLIKA_NIZOV -> v.niziZa - v.niziProti;
                case DOBLJENI_NIZI -> v.niziZa;
                case JAKOSTNO_MESTO -> v.prijava.getStNosilca();
                case ABECEDA -> null;
            };
        }

        KrogDto dto(List<Vmesni> krog, int odMesta, boolean vseTekmeOdigrane) {
            List<KrogDto.Clan> clani = new ArrayList<>(krog.size());
            Map<Long, String> imena = new HashMap<>();
            int mesto = odMesta;
            for (Vmesni v : krog) {
                Celice c = celice(v);
                clani.add(new KrogDto.Clan(v.id(), v.prijava.prikazanoIme(), mesto++,
                        c.zmage, c.nizi, c.tocke, List.copyOf(c.medNjimi)));
                imena.put(v.id(), v.prijava.prikazanoIme());
            }
            List<KrogDto.TekmaKroga> tekme = kontekst.dvobojiMed(krog).stream()
                    .map(d -> new KrogDto.TekmaKroga(imena.get(d.prvi()), imena.get(d.drugi()),
                            d.nizi1(), d.nizi2(), d.idZmagovalca().equals(d.prvi()), neobicajenIzid(d)))
                    .toList();
            return new KrogDto(odMesta, mesto - 1, odlocilo, enakeZmage, vseTekmeOdigrane,
                    tockeManjkajo, clani, List.copyOf(opombe), tekme);
        }

        /* Izid, ki ga je treba zapisati ob nizih (predaja, brez boja ...). */
        private static IzidTekme neobicajenIzid(Dvoboj d) {
            return d.izid() == IzidTekme.IGRANO ? null : d.izid();
        }
    }

    /* Podatki ene lestvice. Tocke po nizih se naloze sele, ce jih razvrstitev
       res potrebuje - razlika nizov v krogu skoraj vedno ze razsodi, zato
       vecina lestvic te poizvedbe sploh ne sprozi. */
    private final class Kontekst {
        final List<Dvoboj> dvoboji;
        private final NadomestniIzid nadomestni;
        private Map<Long, int[]> tockePoTekmi;

        Kontekst(List<Dvoboj> dvoboji, NadomestniIzid nadomestni) {
            this.dvoboji = dvoboji;
            this.nadomestni = nadomestni;
        }

        /* Tekme, ki so jih dani udelezenci odigrali med seboj. */
        List<Dvoboj> dvobojiMed(List<Vmesni> clani) {
            Set<Long> idji = new HashSet<>();
            for (Vmesni v : clani) {
                idji.add(v.id());
            }
            return dvoboji.stream()
                    .filter(d -> idji.contains(d.prvi()) && idji.contains(d.drugi()))
                    .toList();
        }

        int tekemMed(List<Vmesni> clani) {
            return dvobojiMed(clani).size();
        }

        /* id tekme -> [tocke prvega, tocke drugega], sesteto cez vse nize. */
        Map<Long, int[]> tocke() {
            if (tockePoTekmi == null) {
                tockePoTekmi = new HashMap<>();
                List<Long> idji = dvoboji.stream().map(Dvoboj::idTekme).toList();
                if (!idji.isEmpty()) {
                    for (Object[] vrstica : nizRepozitorij.tockeZaTekme(idji)) {
                        int[] sestevek = tockePoTekmi.computeIfAbsent(
                                ((Number) vrstica[0]).longValue(), k -> new int[2]);
                        sestevek[0] += ((Number) vrstica[1]).intValue();
                        sestevek[1] += ((Number) vrstica[2]).intValue();
                    }
                }
                if (nadomestni != null) {
                    tockePoTekmi.remove(nadomestni.idTekme());
                    if (nadomestni.tocke() != null && !nadomestni.tocke().isEmpty()) {
                        int[] sestevek = new int[2];
                        for (NizVnos niz : nadomestni.tocke()) {
                            sestevek[0] += niz.tocke1();
                            sestevek[1] += niz.tocke2();
                        }
                        tockePoTekmi.put(nadomestni.idTekme(), sestevek);
                    }
                }
            }
            return tockePoTekmi;
        }
    }
}
