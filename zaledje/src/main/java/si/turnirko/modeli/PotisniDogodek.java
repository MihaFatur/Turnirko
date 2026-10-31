/* Tekma, o kateri je potisno obvestilo ze poslano (V60): kljuc "T12:45" (turnirska
   tekma 12, igralec 45) ali "L7:45" (ligaska tekma srecanja 7). Preracun ratinga
   zapise dnevnika znova ustvari z novimi id-ji, zato bi brez te tabele isto tekmo
   obvestili dvakrat. */
package si.turnirko.modeli;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "potisni_dogodek")
public class PotisniDogodek {

    @Id
    @Column(name = "kljuc", nullable = false)
    private String kljuc;

    @Convert(converter = CasKotBesedilo.class)
    @Column(name = "poslano_ob", nullable = false)
    private LocalDateTime poslanoOb;

    protected PotisniDogodek() {}

    public PotisniDogodek(String kljuc, LocalDateTime poslanoOb) {
        this.kljuc = kljuc;
        this.poslanoOb = poslanoOb;
    }

    public String getKljuc() { return kljuc; }
    public LocalDateTime getPoslanoOb() { return poslanoOb; }
}
