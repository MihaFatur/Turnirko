/* Turnir je prireditev (kraj, dvorana, datumi). Posamezna tekmovanja
   na turnirju so DOGODKI (npr. "clani posamicno") - igralci se
   prijavljajo na dogodke, ne na turnir. */
package si.turnirko.modeli;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "turnir")
public class Turnir {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ime", nullable = false)
    private String ime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postna_st")
    private Kraj kraj;

    @Column(name = "dvorana")
    private String dvorana;

    @Column(name = "datum_zacetka")
    private LocalDate datumZacetka;

    @Column(name = "datum_konca")
    private LocalDate datumKonca;

    /* Status vedno doloca streznik, nikoli odjemalec. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusTekmovanja status = StatusTekmovanja.PRIPRAVA;

    @Column(name = "opombe")
    private String opombe;

    /* Raven tekmovanja doloci tezo tekem v Turnirko ratingu (glej
       RavenTekmovanja). Privzeto KLUBSKO: turnir, ki nastane v aplikaciji, je
       klubski - uradnega statusa si klub ne more podeliti sam, uvoz NTZS pa
       raven nastavi izrecno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "raven", nullable = false)
    private RavenTekmovanja raven = RavenTekmovanja.KLUBSKO;

    /* Sodniki slikajo listke s svojim telefonom (S2, V75); null = izklopljeno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sodniki_listki")
    private SodniskaPotrditev sodnikiListki;

    /* Mize turnirja (M1, V76; docs/pravila/mize.md): stevilo (null = niso
       nastavljene), zacasno izklopljene (besedilo "3,7"), ali porazenec zadnje
       tekme na mizi sodi naslednjo, in pocitek, ki ga uposteva predlog tekem (M2). */
    @Column(name = "stevilo_miz")
    private Integer steviloMiz;

    @Column(name = "izklopljene_mize")
    private String izklopljeneMize;

    @Column(name = "porazenec_sodi", nullable = false)
    private boolean porazenecSodi = false;

    @Column(name = "pocitek_min", nullable = false)
    private int pocitekMin = 3;

    /* Rocni vrstni red vrste »Na vrsti« (V77): id-ji tekem "12,45,7", null = samodejni. */
    @Column(name = "vrstni_red_tekem")
    private String vrstniRedTekem;

    /* Skupne mize (V78): glavni turnir skupine, katere mize si ta turnir deli (null =
       sam ali glavni). Nastavitve miz in vrstni red skupine nosi glavni turnir. */
    @Column(name = "id_skupne_mize")
    private Long idSkupneMize;

    /* Vrstni red kategorij v kolu (V78): id-ji dogodkov "12,15,13", null = privzeti. */
    @Column(name = "vrstni_red_dogodkov")
    private String vrstniRedDogodkov;

    /* Od kod je turnir prisel (V27). Prazen = nastal je v Turnirku; sicer je
       uvozen in samo za branje (glej VirTekmovanja, LastnistvoStoritev). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vir")
    private VirTekmovanja vir;

    /* Lastnistvo: racun, ki je turnir ustvaril, in posnetek njegovega kluba ob
       nastanku. Po njiju storitve razsodijo, kdo sme urejati - organizator sme
       svoje turnirje in tiste svojega kluba. Adminovi turnirji nimajo lastnika
       (oba prazna) in jih upravlja le administrator. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ustvaril")
    private Uporabnik ustvaril;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_klub_lastnik")
    private Klub klubLastnik;

    /* Razdelki zavihka Zanimivosti, ki jih organizator skrije gledalcem (V51).
       Besedilo in ne tabela: nastavitev je del tekmovanja in se nikoli ne bere brez
       njega. Ne pravilo tekmovanja, zato se ureja tudi pri uvozenem turnirju. */
    @Column(name = "skrite_zanimivosti")
    private String skriteZanimivosti;

    /* Logotip (V54): prikaz, ne pravilo tekmovanja - ureja se v vsakem stanju
       in tudi pri uvozenem turnirju (SlikeStoritev). EAGER namenoma: TurnirDto
       nastane v kontrolerju IZVEN transakcije in logotip nosi; Slika je samo
       opis brez bajtov, turnirjev z logotipom pa je malo. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_logotip")
    private Slika logotip;

    /* Optimisticno zaklepanje - prepreci, da bi si dva socasna
       zapisa tiho prepisala podatke. */
    @Version
    @Column(name = "verzija", nullable = false)
    private long verzija;

    @Column(name = "ustvarjen_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenOb;

    @PrePersist
    void obShranjevanju() {
        ustvarjenOb = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }

    public Kraj getKraj() { return kraj; }
    public void setKraj(Kraj kraj) { this.kraj = kraj; }

    public String getDvorana() { return dvorana; }
    public void setDvorana(String dvorana) { this.dvorana = dvorana; }

    public LocalDate getDatumZacetka() { return datumZacetka; }
    public void setDatumZacetka(LocalDate datumZacetka) { this.datumZacetka = datumZacetka; }

    public LocalDate getDatumKonca() { return datumKonca; }
    public void setDatumKonca(LocalDate datumKonca) { this.datumKonca = datumKonca; }

    public StatusTekmovanja getStatus() { return status; }
    public void setStatus(StatusTekmovanja status) { this.status = status; }

    public String getOpombe() { return opombe; }
    public void setOpombe(String opombe) { this.opombe = opombe; }

    public SodniskaPotrditev getSodnikiListki() { return sodnikiListki; }
    public void setSodnikiListki(SodniskaPotrditev sodnikiListki) { this.sodnikiListki = sodnikiListki; }

    public Integer getSteviloMiz() { return steviloMiz; }
    public void setSteviloMiz(Integer steviloMiz) { this.steviloMiz = steviloMiz; }

    /* Izklopljene mize po vrsti; zapis pise samo MizeStoritev, tuj del se preskoci. */
    public SortedSet<Integer> getIzklopljeneMize() {
        SortedSet<Integer> mize = new TreeSet<>();
        if (izklopljeneMize == null || izklopljeneMize.isBlank()) {
            return mize;
        }
        for (String del : izklopljeneMize.split(",")) {
            try {
                mize.add(Integer.parseInt(del.trim()));
            } catch (NumberFormatException e) {
                // ni stevilka - ne uposteva se
            }
        }
        return mize;
    }

    public void setIzklopljeneMize(Collection<Integer> mize) {
        this.izklopljeneMize = mize == null || mize.isEmpty() ? null
                : mize.stream().sorted().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean isPorazenecSodi() { return porazenecSodi; }
    public void setPorazenecSodi(boolean porazenecSodi) { this.porazenecSodi = porazenecSodi; }

    public int getPocitekMin() { return pocitekMin; }
    public void setPocitekMin(int pocitekMin) { this.pocitekMin = pocitekMin; }

    /* Rocni vrstni red tekem po vrsti; zapis pise samo MizeStoritev, tuj del se preskoci. */
    public List<Long> getVrstniRedTekem() {
        return idji(vrstniRedTekem);
    }

    public void setVrstniRedTekem(List<Long> red) {
        this.vrstniRedTekem = zapisIdjev(red);
    }

    public Long getIdSkupneMize() { return idSkupneMize; }
    public void setIdSkupneMize(Long idSkupneMize) { this.idSkupneMize = idSkupneMize; }

    /* Glavni turnir skupine skupnih miz: ta, ce ni clan druge skupine. */
    public Long idGlavnegaZaMize() {
        return idSkupneMize != null ? idSkupneMize : id;
    }

    /* Vrstni red kategorij v kolu (id-ji dogodkov); zapis pise samo MizeStoritev. */
    public List<Long> getVrstniRedDogodkov() {
        return idji(vrstniRedDogodkov);
    }

    public void setVrstniRedDogodkov(List<Long> red) {
        this.vrstniRedDogodkov = zapisIdjev(red);
    }

    private static List<Long> idji(String zapis) {
        List<Long> red = new ArrayList<>();
        if (zapis == null || zapis.isBlank()) {
            return red;
        }
        for (String del : zapis.split(",")) {
            try {
                red.add(Long.parseLong(del.trim()));
            } catch (NumberFormatException e) {
                // zapis je samo nas; poskodovan del ne sme podreti strani Mize
            }
        }
        return red;
    }

    private static String zapisIdjev(List<Long> red) {
        return red == null || red.isEmpty() ? null
                : red.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    public RavenTekmovanja getRaven() { return raven; }
    public void setRaven(RavenTekmovanja raven) { this.raven = raven; }

    public VirTekmovanja getVir() { return vir; }
    public void setVir(VirTekmovanja vir) { this.vir = vir; }

    /* Ali je turnir uvozen iz zunanjega vira in torej samo za branje. */
    public boolean jeUvozen() { return vir != null; }

    public Uporabnik getUstvaril() { return ustvaril; }
    public void setUstvaril(Uporabnik ustvaril) { this.ustvaril = ustvaril; }

    public Klub getKlubLastnik() { return klubLastnik; }
    public void setKlubLastnik(Klub klubLastnik) { this.klubLastnik = klubLastnik; }

    public Set<PostavkaZanimivosti> getSkriteZanimivosti() {
        return PostavkaZanimivosti.izZapisa(skriteZanimivosti);
    }

    public void setSkriteZanimivosti(Collection<PostavkaZanimivosti> skrite) {
        this.skriteZanimivosti = PostavkaZanimivosti.vZapis(skrite);
    }

    public Slika getLogotip() { return logotip; }
    public void setLogotip(Slika logotip) { this.logotip = logotip; }

    public LocalDateTime getUstvarjenOb() { return ustvarjenOb; }
}
