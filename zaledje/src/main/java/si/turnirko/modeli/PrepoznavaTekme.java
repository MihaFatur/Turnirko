/* Kako je branje listka naslo tekmo (V68):
   KODA  - QR koda ali natisnjena stevilka tekme na listku (zanesljivo);
   IMENA - prebrana imena, ki se ujemajo z natanko eno tekmo turnirja, ki caka
           na izid (prazen listek, slaba fotografija kode);
   ROCNO - tekmo je izbral organizator. */
package si.turnirko.modeli;

public enum PrepoznavaTekme {
    KODA,
    IMENA,
    ROCNO
}
