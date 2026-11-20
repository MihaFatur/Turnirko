/* Ali sme prijavljeni uporabnik uporabljati branje listkov; razlog, kadar ne.
   Vmesnik po tem pokaze ali skrije povezavo »Branje listkov«. */
package si.turnirko.dto;

public record StanjeBranjaListkovDto(boolean omogoceno, String razlog) {}
