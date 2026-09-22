/* Kdo je racun igralca povezal z zapisom v sifrantu.
   ADMIN     - administrator ob potrditvi (rocna izbira igralca)
   SAMODEJNO - ob potrjeni e-posti so se ime, priimek in datum rojstva
               ujemali z natanko enim igralcem brez racuna */
package si.turnirko.modeli;

public enum VirPovezave {
    ADMIN,
    SAMODEJNO
}
