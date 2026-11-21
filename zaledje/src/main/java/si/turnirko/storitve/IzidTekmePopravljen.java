/* Dogodek: izid koncane tekme je bil popravljen (TekmaStoritev.popraviRezultat).
   Objavi se v transakciji popravka; poslusalci (ucenje iz listkov, F6.1) delajo
   po potrditvi transakcije, zato popravek, ki se razveljavi, nima posledic. */
package si.turnirko.storitve;

public record IzidTekmePopravljen(Long idTekme) {}
