import type { SrecanjeDto } from '../api/tipi'

/* Pod tem številom odigranih tekem zavihka »Zanimivosti« ne ponudimo — turnirju
   in ligi enako. Isti prag ima strežnik (StatistikaTekmovanjaStoritev.PRAG_TEKEM);
   tu je zato, da gumba, ki bi povedal samo »premalo podatkov«, sploh ni. */
export const PRAG_ZANIMIVOSTI = 9

/* Posamične tekme, odigrane v ligi. Vsota dobljenih tekem obeh ekip je število
   odigranih (tekma vedno dobi zmagovalca), srečanje v teku pa jo ima že
   vpisano. Srečanje brez borbe in prenesen izid nosita izid brez tekem
   (zaledje jih v statistiko ne šteje), zato ju izpustimo.

   Kolo ni merilo: ekipe se neuradno menjajo za termine, zato je lahko po več
   tednih v vsakem kolu še eno srečanje neodigrano in nobeno kolo ni »celo«,
   tekem pa je za zanimivosti že dovolj. */
export function odigranihTekemLige(srecanja: SrecanjeDto[]): number {
  return srecanja
    .filter((s) => !s.brezBoja && !s.prenesen)
    .reduce((vsota, s) => vsota + s.dobljeneDomaci + s.dobljeneGost, 0)
}
