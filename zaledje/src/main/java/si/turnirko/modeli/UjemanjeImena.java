/* Kako se ime in priimek vpisa ujemata z imenom obstojecega igralca
   (PodobnostImen). Vrstni red je pomemben: od najmocnejsega ujemanja do
   najsibkejsega, po njem se razvrscajo tudi predlogi v obrazcu. */
package si.turnirko.modeli;

public enum UjemanjeImena {

    /* Ime in priimek sta enaka, ce zanemarimo velikost crk, sumnike in
       locila (»Kovac« = »Kovač«, »Novak-Kos« = »Novak Kos«). */
    ISTO,

    /* Ime in priimek sta zamenjana (»Fatur Miha« proti »Miha Fatur«) - v
       slovenskih seznamih najpogostejsa napaka pri vpisu. */
    OBRNJENO,

    /* Ena beseda se razlikuje za crko ali dve (tipkarska napaka, »Horvat« /
       »Horvath«) ali je krajsa oblika imena (»Miha« / »Mihael«). */
    PODOBNO
}
