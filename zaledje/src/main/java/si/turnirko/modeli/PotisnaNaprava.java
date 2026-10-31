/* Naprava (brskalnik), ki je dovolila potisna obvestila (V60): naslov potisne
   storitve in kljuca za sifriranje (PushSubscription). Brez povezave na racun
   kot SpremljanIgralec: tabela odgovarja na eno vprasanje ("kam poslati
   obvestilo racunu X"). */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "potisna_naprava")
public class PotisnaNaprava {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_racun", nullable = false)
    private Long idRacun;

    @Column(name = "endpoint", nullable = false, unique = true)
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth", nullable = false)
    private String auth;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "ustvarjena_ob", nullable = false, updatable = false)
    private LocalDateTime ustvarjenaOb;

    protected PotisnaNaprava() {}

    public PotisnaNaprava(Long idRacun, String endpoint, String p256dh, String auth, LocalDateTime ustvarjenaOb) {
        this.idRacun = idRacun;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.ustvarjenaOb = ustvarjenaOb;
    }

    public Long getId() { return id; }
    public Long getIdRacun() { return idRacun; }
    public void setIdRacun(Long idRacun) { this.idRacun = idRacun; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
    public LocalDateTime getUstvarjenaOb() { return ustvarjenaOb; }
}
