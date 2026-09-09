-- ============================================================================
-- Turnirko: izbor lig za domaco stran
--
-- Sklop "Lige" na domaci strani je doslej sam izbral, kaj pokaze: prijavljeni
-- je videl svoj izbor spremljanih lig, gost pa lige, ki jih je odprl nazadnje,
-- oziroma - ce ni bilo ne enega ne drugega - prve tri lige v teku po vrstnem
-- redu vpisa. Nihce torej ni mogel odlociti, katera liga je na vhodni strani
-- zveze; odlocil je vrstni red id-jev.
--
-- Odslej administrator dve ligi izrecno postavi na domaco stran. Zastavica je
-- UREDNISKA odlocitev in ne pravilo tekmovanja, zato:
--   - je ne ureja obrazec pravil lige (ta se ob generiranju razporeda zaklene,
--     ligo na domaci strani pa je treba zamenjati sredi sezone) - ima svojo
--     koncno tocko PUT /lige/{id}/na-domaci, kot prehodi in termini kol;
--   - jo sme nastaviti samo ADMIN in ne organizator: domaca stran je izlozba
--     zveze, ne posameznega kluba.
--
-- Omejitve "najvec dve" shema NE vsiljuje: pogoj cez vec vrstic bi v SQLite
-- terjal prozilec, meja pa je stvar predstavitve in se sme spremeniti brez
-- migracije. Varuje jo LigaStoritev.nastaviNaDomaci.
-- ============================================================================

ALTER TABLE liga ADD COLUMN na_domaci INTEGER NOT NULL DEFAULT 0
    CHECK (na_domaci IN (0, 1));
