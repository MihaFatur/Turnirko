/* Kaj popravek izida turnirske tekme naredi s tistim, kar je po njej ze teklo.

   Zmagovalec turnirske tekme je vozlisce tekmovanja: po njem je bilo napredovano
   po mrezi, iz njega so nastala mesta v skupinah. Zato se popravek najprej OCENI
   (brez zapisa; ista ocena gre v predogled in v preverbo pred zapisom), organizator
   pa se odloci, kadar je kaj ze odigrano naprej. Aplikacija tega ne more odlociti
   sama: ali so naslednje tekme res igrali pravi igralci, je znano samo v dvorani.

   MREZA OZ. ZREB (zamenjan zmagovalec):
   - naslednje tekme brez izida: igralec se zamenja, odlocitve ni,
   - naslednje tekme z izidom: IZBRISI_IZIDE (izidi gredo, cela veja se izprazni in
     organizator jih vnese znova) ali ZAMENJAJ_IGRALCA (izid ostane na svoji strani
     tekme, zato si igralca zamenjata mesti po celi veji).

   SKUPINA (spremenjen vrstni red, iz katerega je ze nastal naslednji del):
   - PODRI_NASLEDNJI_DEL (del gre, tudi z vnesenimi izidi) ali OHRANI_NASLEDNJI_DEL
     (popravi se samo tabela skupine). Vrstni red, ki naslednjega dela ne spremeni
     (pri izlocilnem delu 3. in 4. mesto), ne zahteva odlocitve.

   Dogodek, ki mu popravek vzame izide, spet tece (turnir se odpre), sicer vnos
   manjkajocih izidov ne bi bil mogoc. */
package si.turnirko.storitve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import si.turnirko.dto.PopravekPredogledDto;
import si.turnirko.dto.VrsticaLestviceDto;
import si.turnirko.izjeme.DomenskaIzjema;
import si.turnirko.modeli.Dogodek;
import si.turnirko.modeli.FazaTekme;
import si.turnirko.modeli.IzidTekme;
import si.turnirko.modeli.NacinPopravka;
import si.turnirko.modeli.Prijava;
import si.turnirko.modeli.RatingStanje;
import si.turnirko.modeli.SistemTekmovanja;
import si.turnirko.modeli.Skupina;
import si.turnirko.modeli.StatusTekme;
import si.turnirko.modeli.StatusTekmovanja;
import si.turnirko.modeli.Tekma;
import si.turnirko.modeli.VlogaIzvora;
import si.turnirko.modeli.Zreb;
import si.turnirko.repozitoriji.NizRepozitorij;
import si.turnirko.repozitoriji.PrijavaRepozitorij;
import si.turnirko.repozitoriji.RatingZgodovinaRepozitorij;
import si.turnirko.repozitoriji.SkupinaRepozitorij;
import si.turnirko.repozitoriji.TekmaRepozitorij;
import si.turnirko.repozitoriji.ZrebRepozitorij;

@Service
public class PoslediceIzidaStoritev {

    private final TekmaRepozitorij tekmaRepozitorij;
    private final NizRepozitorij nizRepozitorij;
    private final SkupinaRepozitorij skupinaRepozitorij;
    private final PrijavaRepozitorij prijavaRepozitorij;
    private final ZrebRepozitorij zrebRepozitorij;
    private final RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij;
    private final RazvrstitevStoritev razvrstitevStoritev;
    private final ZrebStoritev zrebStoritev;
    private final SvRegijaStoritev svRegija;
    private final RazveljavitevEkipneTekmeStoritev razveljavitev;

    public PoslediceIzidaStoritev(TekmaRepozitorij tekmaRepozitorij,
                                  NizRepozitorij nizRepozitorij,
                                  SkupinaRepozitorij skupinaRepozitorij,
                                  PrijavaRepozitorij prijavaRepozitorij,
                                  ZrebRepozitorij zrebRepozitorij,
                                  RatingZgodovinaRepozitorij ratingZgodovinaRepozitorij,
                                  RazvrstitevStoritev razvrstitevStoritev,
                                  ZrebStoritev zrebStoritev,
                                  SvRegijaStoritev svRegija,
                                  RazveljavitevEkipneTekmeStoritev razveljavitev) {
        this.tekmaRepozitorij = tekmaRepozitorij;
        this.nizRepozitorij = nizRepozitorij;
        this.skupinaRepozitorij = skupinaRepozitorij;
        this.prijavaRepozitorij = prijavaRepozitorij;
        this.zrebRepozitorij = zrebRepozitorij;
        this.ratingZgodovinaRepozitorij = ratingZgodovinaRepozitorij;
        this.razvrstitevStoritev = razvrstitevStoritev;
        this.zrebStoritev = zrebStoritev;
        this.svRegija = svRegija;
        this.razveljavitev = razveljavitev;
    }

    /* Del, ki je nastal iz vrstnega reda skupine in ga popravek spremeni. */
    public record NaslednjiDel(PopravekPredogledDto.VrstaDela vrsta, Integer nivo, List<Tekma> tekme,
                               List<String> vrstniRedPrej, List<String> vrstniRedPotem) {

        int vnesenihTekem() {
            return (int) tekme.stream()
                    .filter(t -> !t.jePrenesena() && imaIzid(t))
                    .count();
        }
    }

    /* Kaj bi popravek naredil. Nic ni zapisano. */
    public record Ocena(boolean zamenjanZmagovalec, List<Tekma> odvisneZIzidom, int odvisnihBrezIzida,
                        NaslednjiDel naslednjiDel) {

        public List<NacinPopravka> izbire() {
            if (!odvisneZIzidom.isEmpty()) {
                return List.of(NacinPopravka.IZBRISI_IZIDE, NacinPopravka.ZAMENJAJ_IGRALCA);
            }
            if (naslednjiDel != null) {
                return List.of(NacinPopravka.PODRI_NASLEDNJI_DEL, NacinPopravka.OHRANI_NASLEDNJI_DEL);
            }
            return List.of();
        }

        /* Nacin, ki velja za ta popravek. Brez izbire ga ni (zastarel nacin iz
           predogleda ne sme storiti nicesar), kjer je izbira, pa je obvezen. */
        public NacinPopravka veljavenNacin(NacinPopravka zahtevan) {
            List<NacinPopravka> izbire = izbire();
            if (izbire.isEmpty()) {
                return null;
            }
            // List.of(...).contains(null) vrze izjemo, zato null posebej
            if (zahtevan == null || !izbire.contains(zahtevan)) {
                throw new DomenskaIzjema(naslednjiDel != null
                        ? "Popravek spremeni vrstni red skupine, iz katerega je že nastal naslednji"
                                + " del turnirja. Izberi, ali naj se ta del podre ali ostane."
                        : "Naslednje tekme imajo že vnesen izid. Izberi, ali naj se izidi izbrišejo"
                                + " ali naj si igralca zamenjata mesti.");
            }
            return zahtevan;
        }
    }

    // ---------- Ocena ----------

    public Ocena oceni(Tekma tekma, RazvrstitevStoritev.NadomestniIzid nov) {
        List<Tekma> tekme = tekmaRepozitorij.najdiZaDogodek(tekma.getDogodek().getId());
        boolean zamenjan = !tekma.getZmagovalec().getId().equals(nov.idZmagovalca());

        Set<Tekma> zIzidom = new LinkedHashSet<>();
        int[] brezIzida = new int[1];
        if (zamenjan) {
            zberiOdvisne(tekma, otroci(tekme), zIzidom, brezIzida);
        }
        List<Tekma> urejene = zIzidom.stream()
                .sorted(Comparator.comparingInt(Tekma::getKolo).thenComparingInt(Tekma::getPozicija))
                .toList();
        return new Ocena(zamenjan, urejene, brezIzida[0], naslednjiDel(tekma, nov, tekme));
    }

    private static void zberiOdvisne(Tekma izvor, Map<Long, List<Tekma>> otroci,
                                     Set<Tekma> zIzidom, int[] brezIzida) {
        for (Tekma otrok : otroci.getOrDefault(izvor.getId(), List.of())) {
            if (imaIzid(otrok)) {
                if (zIzidom.add(otrok)) {
                    zberiOdvisne(otrok, otroci, zIzidom, brezIzida);
                }
            } else {
                brezIzida[0]++;
            }
        }
    }

    private static Map<Long, List<Tekma>> otroci(List<Tekma> tekme) {
        Map<Long, List<Tekma>> otroci = new HashMap<>();
        for (Tekma t : tekme) {
            if (t.getIdIzvorTekma1() != null) {
                otroci.computeIfAbsent(t.getIdIzvorTekma1(), k -> new ArrayList<>()).add(t);
            }
            if (t.getIdIzvorTekma2() != null && !t.getIdIzvorTekma2().equals(t.getIdIzvorTekma1())) {
                otroci.computeIfAbsent(t.getIdIzvorTekma2(), k -> new ArrayList<>()).add(t);
            }
        }
        return otroci;
    }

    /* Tekma z vnesenim izidom. Prosti prehod je koncan ob nastanku in ni vnos. */
    private static boolean imaIzid(Tekma tekma) {
        return tekma.getStatus() == StatusTekme.KONCANA && tekma.getIzidTip() != IzidTekme.PROSTO;
    }

    /* Del, ki ga popravek zadene: samo koncana predtekmovalna skupina, samo ce del
       ze obstaja in samo ce nov vrstni red zanj kaj pomeni. Vse drugo (skupina se
       ni odigrana, zreb se ni narejen) se uredi samo, ko del nastane. */
    private NaslednjiDel naslednjiDel(Tekma tekma, RazvrstitevStoritev.NadomestniIzid nov,
                                      List<Tekma> tekme) {
        if (tekma.getFaza() != FazaTekme.SKUPINA || tekma.getIdSkupina() == null) {
            return null;
        }
        Dogodek dogodek = tekma.getDogodek();
        List<Skupina> skupine = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(dogodek.getId());
        Skupina skupina = skupine.stream()
                .filter(s -> s.getId().equals(tekma.getIdSkupina()))
                .findFirst().orElse(null);
        if (skupina == null || skupina.getStopnja() != 1) {
            return null;
        }
        List<Tekma> tekmeSkupine = tekme.stream()
                .filter(t -> t.getFaza() == FazaTekme.SKUPINA && skupina.getId().equals(t.getIdSkupina()))
                .toList();
        if (!tekmeSkupine.stream().allMatch(t -> t.getStatus() == StatusTekme.KONCANA)) {
            return null;
        }

        PopravekPredogledDto.VrstaDela vrsta;
        Integer nivo = null;
        List<Tekma> tekmeDela;
        switch (dogodek.getSistemTekmovanja()) {
            case SKUPINE_IZLOCILNI -> {
                vrsta = PopravekPredogledDto.VrstaDela.IZLOCILNI;
                tekmeDela = tekme.stream()
                        .filter(t -> t.getFaza() == FazaTekme.GLAVNI || t.getFaza() == FazaTekme.TOLAZILNI)
                        .toList();
                if (tekmeDela.isEmpty()) {
                    return null;
                }
            }
            case SKUPINE_ZA_MESTA -> {
                vrsta = PopravekPredogledDto.VrstaDela.FINALNE_SKUPINE;
                Set<Long> finalne = skupine.stream().filter(s -> s.getStopnja() > 1)
                        .map(Skupina::getId).collect(Collectors.toSet());
                if (finalne.isEmpty()) {
                    return null;
                }
                tekmeDela = tekme.stream().filter(t -> finalne.contains(t.getIdSkupina())).toList();
            }
            case SV_REGIJA -> {
                vrsta = PopravekPredogledDto.VrstaDela.ZREBI;
                nivo = skupina.getNivo();
                List<Zreb> zrebi = zrebRepozitorij.findByDogodekIdAndNivoOrderByIndeksAsc(
                        dogodek.getId(), nivo);
                if (zrebi.stream().noneMatch(Zreb::isZgrajen)) {
                    return null;
                }
                Set<Long> idjiZrebov = zrebi.stream().map(Zreb::getId).collect(Collectors.toSet());
                tekmeDela = tekme.stream()
                        .filter(t -> t.getIdZreb() != null && idjiZrebov.contains(t.getIdZreb()))
                        .toList();
            }
            default -> {
                return null;
            }
        }

        List<Prijava> clani = SkupineStoritev.clani(
                skupina, prijavaRepozitorij.najdiZaDogodek(dogodek.getId()), tekme);
        List<VrsticaLestviceDto> prej = razvrstitevStoritev.lestvica(clani, tekmeSkupine);
        List<VrsticaLestviceDto> potem = razvrstitevStoritev.lestvica(clani, tekmeSkupine, nov);
        if (!delPrizadet(dogodek.getSistemTekmovanja(), prej, potem)) {
            return null;
        }
        return new NaslednjiDel(vrsta, nivo, tekmeDela, imena(prej), imena(potem));
    }

    /* Ali nov vrstni red pomeni kaj za naslednji del. Izlocilni del vzame prva dva;
       finalne skupine vzamejo pasove po dva (znotraj pasu se tekme ne spremenijo,
       preneseni izid pa sledi - uskladiPrenesene); zrebi SV regije vzamejo vse. */
    private static boolean delPrizadet(SistemTekmovanja sistem, List<VrsticaLestviceDto> prej,
                                       List<VrsticaLestviceDto> potem) {
        List<Long> idPrej = prej.stream().map(VrsticaLestviceDto::idPrijave).toList();
        List<Long> idPotem = potem.stream().map(VrsticaLestviceDto::idPrijave).toList();
        return switch (sistem) {
            case SKUPINE_IZLOCILNI -> !idPrej.subList(0, Math.min(2, idPrej.size()))
                    .equals(idPotem.subList(0, Math.min(2, idPotem.size())));
            case SKUPINE_ZA_MESTA -> {
                for (int i = 0; i < idPrej.size(); i += SkupineStoritev.PAS_FINALNE_SKUPINE) {
                    int konec = Math.min(i + SkupineStoritev.PAS_FINALNE_SKUPINE, idPrej.size());
                    if (!new HashSet<>(idPrej.subList(i, konec))
                            .equals(new HashSet<>(idPotem.subList(i, konec)))) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> !idPrej.equals(idPotem);
        };
    }

    private static List<String> imena(List<VrsticaLestviceDto> lestvica) {
        return lestvica.stream().map(VrsticaLestviceDto::polnoIme).toList();
    }

    // ---------- Predogled ----------

    public PopravekPredogledDto predogled(Tekma tekma, Ocena ocena, Prijava novZmagovalec, boolean stejeVRating) {
        int zadnjeKolo = tekmaRepozitorij.najdiZaDogodek(tekma.getDogodek().getId()).stream()
                .filter(t -> t.getFaza() == FazaTekme.GLAVNI)
                .mapToInt(Tekma::getKolo).max().orElse(0);
        NaslednjiDel del = ocena.naslednjiDel();
        return new PopravekPredogledDto(
                ocena.zamenjanZmagovalec(),
                tekma.getZmagovalec().prikazanoIme(),
                novZmagovalec.prikazanoIme(),
                ocena.izbire(),
                ocena.odvisneZIzidom().stream().map(t -> new PopravekPredogledDto.TekmaPosledice(
                        t.getId(), oznakaTekme(t, zadnjeKolo),
                        t.getPrijava1().prikazanoIme(), t.getPrijava2().prikazanoIme(),
                        t.getDobljeniNizi1(), t.getDobljeniNizi2(), t.getIzidTip())).toList(),
                ocena.odvisnihBrezIzida(),
                del == null ? null : new PopravekPredogledDto.NaslednjiDel(
                        del.vrsta(), del.nivo(), del.vnesenihTekem(), del.vrstniRedPrej(), del.vrstniRedPotem()),
                tekma.getDogodek().getStatus() == StatusTekmovanja.ZAKLJUCEN,
                stejeVRating);
    }

    private static String oznakaTekme(Tekma tekma, int zadnjeKolo) {
        if (tekma.getRazponOd() != null) {
            return "Za " + tekma.getRazponOd() + ".–" + tekma.getRazponDo() + ". mesto";
        }
        if (tekma.getFaza() == FazaTekme.TOLAZILNI) {
            return "Za 3. mesto";
        }
        return switch (zadnjeKolo - tekma.getKolo()) {
            case 0 -> "Finale";
            case 1 -> "Polfinale";
            case 2 -> "Četrtfinale";
            default -> tekma.getKolo() + ". kolo";
        };
    }

    // ---------- Izvedba ----------

    /* Obracuni, ki jih preracun sam ne bi pobral. Preracun brise samo zapise tekem,
       ki jih bo odigral znova: tekma, ki je ostala brez izida ali izgine (ali postane
       brez boja), bi obdrzala stari obracun. Poizvedba izprazni sejo, zato jo klicalec
       poklice pred prvo spremembo in tekmo prebere znova. Vrne stevilo zapisov. */
    public int pobrisiObracune(Tekma tekma, Ocena ocena, NacinPopravka nacin) {
        Set<Long> idji = new HashSet<>();
        idji.add(tekma.getId());
        if (nacin == NacinPopravka.IZBRISI_IZIDE) {
            ocena.odvisneZIzidom().forEach(t -> idji.add(t.getId()));
        } else if (nacin == NacinPopravka.PODRI_NASLEDNJI_DEL) {
            ocena.naslednjiDel().tekme().forEach(t -> idji.add(t.getId()));
        }
        return ratingZgodovinaRepozitorij.pobrisiZaTekme(RatingStanje.SISTEM_TURNIRKO, idji);
    }

    /* Kar mora biti storjeno PRED zapisom izida: dogodek spet tece in naslednji del
       gre. Mesta gredo pred praznjenjem tekem, ker se berejo iz njih. */
    public void pripravi(Tekma tekma, Ocena ocena, NacinPopravka nacin) {
        if (nacin != NacinPopravka.IZBRISI_IZIDE && nacin != NacinPopravka.PODRI_NASLEDNJI_DEL) {
            return;
        }
        Long idDogodka = tekma.getDogodek().getId();
        razveljavitev.ponovnoOdpriDogodek(idDogodka);
        if (nacin == NacinPopravka.PODRI_NASLEDNJI_DEL) {
            podri(idDogodka, tekma.getDogodek().getSistemTekmovanja(), ocena.naslednjiDel());
        }
    }

    private void podri(Long idDogodka, SistemTekmovanja sistem, NaslednjiDel del) {
        switch (sistem) {
            case SKUPINE_IZLOCILNI -> zrebStoritev.izbrisiTekme(tekmeDela(idDogodka, del));
            case SKUPINE_ZA_MESTA -> {
                zrebStoritev.izbrisiTekme(tekmeDela(idDogodka, del));
                List<Skupina> finalne = skupinaRepozitorij.findByDogodekIdOrderByOznakaAsc(idDogodka)
                        .stream().filter(s -> s.getStopnja() > 1).toList();
                skupinaRepozitorij.deleteAll(finalne);
                skupinaRepozitorij.flush();
                // kdor je bil sam v pasu, je mesto dobil ob nastanku; nastane znova
                prijavaRepozitorij.najdiZaDogodek(idDogodka).forEach(p -> p.setKoncnoMesto(null));
            }
            case SV_REGIJA -> svRegija.podriZrebeNivoja(idDogodka, del.nivo());
            default -> throw new IllegalStateException("Sistem " + sistem + " nima naslednjega dela.");
        }
    }

    /* Tekme dela, sveze: obracuni so sejo izpraznili, ocena nosi odklopljene. */
    private List<Tekma> tekmeDela(Long idDogodka, NaslednjiDel del) {
        Set<Long> idji = del.tekme().stream().map(Tekma::getId).collect(Collectors.toSet());
        return tekmaRepozitorij.najdiZaDogodek(idDogodka).stream()
                .filter(t -> idji.contains(t.getId()))
                .toList();
    }

    /* Zamenjan zmagovalec gre po povezavah naprej. Klicalec je tekmi ze zapisal nov
       izid. Tekme se pisejo neposredno: atomarni vpis v slot (NapredovanjeStoritev) ne
       prepise zasedenega mesta in je tu brez pomena, ker zaklep turnirja drzi popravek. */
    public void preusmeriNaslednje(Tekma izvor, NacinPopravka nacin) {
        Map<Long, List<Tekma>> otroci = otroci(tekmaRepozitorij.najdiZaDogodek(izvor.getDogodek().getId()));
        preusmeri(izvor, otroci, nacin);
        tekmaRepozitorij.flush();
        nizRepozitorij.flush();
    }

    private void preusmeri(Tekma izvor, Map<Long, List<Tekma>> otroci, NacinPopravka nacin) {
        for (Tekma otrok : otroci.getOrDefault(izvor.getId(), List.of())) {
            boolean imelIzid = imaIzid(otrok);
            // izid je last STRANI tekme, zato se strani zapomni pred zamenjavo imena
            boolean zmagalPrvi = imelIzid
                    && otrok.getZmagovalec().getId().equals(otrok.getPrijava1().getId());
            if (izvor.getId().equals(otrok.getIdIzvorTekma1())) {
                otrok.setPrijava1(udelezenec(izvor, otrok.getVlogaIzvora1()));
            }
            if (izvor.getId().equals(otrok.getIdIzvorTekma2())) {
                otrok.setPrijava2(udelezenec(izvor, otrok.getVlogaIzvora2()));
            }
            if (!imelIzid) {
                continue;
            }
            if (nacin == NacinPopravka.ZAMENJAJ_IGRALCA) {
                otrok.setZmagovalec(zmagalPrvi ? otrok.getPrijava1() : otrok.getPrijava2());
                preusmeri(otrok, otroci, nacin);
            } else {
                izbrisiIzid(otrok);
                otrok.setStatus(otrok.getPrijava1() != null && otrok.getPrijava2() != null
                        ? StatusTekme.PRIPRAVLJENA : StatusTekme.CAKA);
                izprazniNaslednje(otrok, otroci);
            }
        }
    }

    /* Tekma je ostala brez izida, zato tisti, ki so prisli iz nje, se niso znani. */
    private void izprazniNaslednje(Tekma izvor, Map<Long, List<Tekma>> otroci) {
        for (Tekma otrok : otroci.getOrDefault(izvor.getId(), List.of())) {
            boolean imelIzid = imaIzid(otrok);
            if (izvor.getId().equals(otrok.getIdIzvorTekma1())) {
                otrok.setPrijava1(null);
            }
            if (izvor.getId().equals(otrok.getIdIzvorTekma2())) {
                otrok.setPrijava2(null);
            }
            if (imelIzid) {
                izbrisiIzid(otrok);
                izprazniNaslednje(otrok, otroci);
            }
            otrok.setStatus(StatusTekme.CAKA);
        }
    }

    private void izbrisiIzid(Tekma tekma) {
        tekma.setZmagovalec(null);
        tekma.setIzidTip(null);
        tekma.setDobljeniNizi1(0);
        tekma.setDobljeniNizi2(0);
        nizRepozitorij.deleteAll(nizRepozitorij.findByTekmaIdOrderByZaporednaStAsc(tekma.getId()));
    }

    private static Prijava udelezenec(Tekma izvor, VlogaIzvora vloga) {
        return vloga == VlogaIzvora.PORAZENEC ? izvor.porazenec() : izvor.getZmagovalec();
    }

    /* Dvoboj, prenesen v finalno skupino, je kopija izida izvorne tekme. Ostati mora
       enak, sicer finalna tabela steje drug izid kot predtekmovalna. */
    public void uskladiPrenesene(Tekma izvor) {
        for (Tekma kopija : tekmaRepozitorij.findByIdPrenesena(izvor.getId())) {
            SkupineStoritev.prepisiIzid(izvor, kopija);
        }
    }
}
