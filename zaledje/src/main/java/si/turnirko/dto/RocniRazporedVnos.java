/* Rocno vpisan razpored lige - kdo s kom igra v katerem kolu.

   Locen od LigaVnos in od generiranja razporeda, ker ni pravilo tekmovanja,
   ampak sam zreb: liga, ki se je doslej vodila na roke, ima pare za novo
   sezono ze razdeljene in razposlane igralcem, zato jih naklucni zreb ne sme
   povoziti (glej migracijo V26).

   Vsebovati mora CEL razpored naenkrat - kola morajo teci od 1 naprej brez
   vrzeli. Delni vnos bi pomenil ligo, ki ji sredi sezone manjka kolo, sam pa
   ne bi vedel, ali gre za napako ali za namero. */
package si.turnirko.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RocniRazporedVnos(
        /* Zgornja meja je varovalka pred pokvarjenim odjemalcem in ne pravilo
           tekmovanja: dvajset ekip v treh krogih je 570 srecanj, vec kot tisoc
           pa ni razpored, ampak nesreca. */
        @NotEmpty(message = "razpored ne sme biti prazen")
        @Size(max = 1000, message = "razpored sme imeti najvec 1000 srecanj")
        @Valid List<ParVnos> srecanja
) {

    /* Eno srecanje: kolo (1..) ter domaca in gostujoca ekipa.

       uraVKolu (0 = prva ura lige) steje samo pri ligi z urami srecanj: pove,
       ob kateri uri kola se srecanje zacne; prazno je prva ura. Pri kolu
       kroznega sistema se vsa srecanja kola zacnejo hkrati in ga ni treba
       podati. */
    public record ParVnos(
            @NotNull(message = "stevilka kola je obvezna") Integer kolo,
            @NotNull(message = "domaca ekipa je obvezna") Long idDomaci,
            @NotNull(message = "gostujoca ekipa je obvezna") Long idGost,
            Integer uraVKolu
    ) {

        public ParVnos(Integer kolo, Long idDomaci, Long idGost) {
            this(kolo, idDomaci, idGost, null);
        }
    }
}
