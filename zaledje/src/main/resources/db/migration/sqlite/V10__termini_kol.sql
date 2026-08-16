-- ============================================================================
-- Turnirko: termini kol lige
--
-- Stolpec srecanje.predviden_zacetek obstaja od V3, a ga doslej ni nihce
-- zapisal - razpored je zato pri vsakem neodigranem kolu izpisal le besedo
-- "razpored", ne pa dneva, ko se kolo igra.
--
-- Termini so pravilo lige in ne lastnost posameznega srecanja: kolo se odigra
-- na en dan (npr. vsako nedeljo ob 18.00). Zato liga dobi le SEME - zacetek
-- prvega kola in razmik v dnevih - iz katerega LigaStoritev ob generiranju
-- razporeda izracuna predviden_zacetek vsem srecanjem. Ta dva stolpca se
-- vpiseta ze ob ustvarjanju lige, ko ekip (in s tem stevila kol) se ni.
--
-- Izracunani datumi so od tam naprej samostojni: organizator jih sme po kolih
-- rocno popraviti (PUT /lige/{id}/termini), ne da bi se seme spremenilo -
-- prestavljeno kolo ne sme prestaviti vseh naslednjih.
--
-- Zacetek je cas in ne datum (isti tip kot predviden_zacetek): ura velja za
-- celo kolo. Ura 00:00 pomeni "ura ni dolocena" - vmesnik takrat izpise samo
-- datum.
-- ============================================================================

ALTER TABLE liga ADD COLUMN zacetek_prvega_kola TEXT;

ALTER TABLE liga ADD COLUMN razmik_dni INTEGER
    CHECK (razmik_dni IS NULL OR razmik_dni BETWEEN 1 AND 365);
