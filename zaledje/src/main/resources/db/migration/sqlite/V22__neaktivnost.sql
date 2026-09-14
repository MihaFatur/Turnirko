-- ============================================================================
-- Turnirko rating: odbitek za neaktivnost
--
-- Kdor dolgo ne igra, se praviloma poslabsa - njegova stevilka pa ostane taka,
-- kot je bila na zadnji tekmi, in na lestvici zaseda mesto, ki mu ne pripada
-- vec. Nemska andro-Rangliste to resuje z odbitkom; mi enako, le mnogo manjsim,
-- ker vecjega podatki ne podpirajo (izmerjeno: odsotnost res znizuje moc, a le
-- za okrog 10-30 tock, ne za 80).
--
--   po  6 mesecih   -10  (skupaj -10)
--   po 12 mesecih   -15  (skupaj -25)
--   po 24 mesecih   -15  (skupaj -40)
--   naprej          nic
--
-- Odbitek je navaden zapis v dnevniku z datumom, ko ZAPADE - ne z datumom,
-- ko ga je kdo vpisal. Samo tako ga zna ponovni preracun postaviti na isto
-- mesto v casovno vrsto in samo tako graf napredka pokaze, kdaj je padec
-- nastal.
--
-- Zato dnevnik potrebuje RAZLOG: doslej je bil zapis brez tekme nujno
-- postavitveni (administratorjeva odlocitev), zdaj pa je lahko tudi odbitek.
-- Razlikovati ju je treba, ker se obnasata nasprotno: postavitev se ob
-- ponovnem preracunu OHRANI (ni posledica rezultatov), odbitek pa se POBRISE
-- in izracuna znova (je izpeljanka iz zaporedja tekem).
-- ============================================================================

ALTER TABLE rating_zgodovina ADD COLUMN razlog TEXT
    CHECK (razlog IS NULL OR razlog IN ('POSTAVITEV', 'NEAKTIVNOST'));

-- vsi dosedanji zapisi brez tekme so postavitve
UPDATE rating_zgodovina
   SET razlog = 'POSTAVITEV'
 WHERE id_tekma IS NULL AND id_tekma_srecanja IS NULL;
