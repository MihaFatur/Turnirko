-- ============================================================================
-- Turnirko: poraz brez borbe v ligi
--
-- Pravila igranja v SNTL (2026/27, cleni 20-22, 26, 27 in 29): tekma, ki je
-- ekipa ne odigra (ne pride, zapusti tekmovalisce, nepopolna ekipa ...), se
-- registrira s 4 : 0 oz. 5 : 0 v skodo krsiteljice - "zmagovalna ekipa dobi
-- dve tocki, porazeni ekipi se odvzame se eno tocko (od osvojenega skupnega
-- stevila tock na lestvici se odsteje tocka)". Tako je tockovala tudi uradna
-- lestvica 1. SNTL zensk 2025/26 pri Stupi.
--
-- Doslej model tega ni poznal: srecanje brez borbe je bilo le srecanje z
-- izidom 5 : 0 in porazenec je dobil tocke navadnega poraza.
--
--   srecanje.brez_boja     srecanje ni bilo odigrano, izid je registriran
--   liga.odbitek_brez_boja koliko tock se porazenemu brez borbe odsteje od
--                          skupnega stevila (0 = pravila lige odbitka nimajo)
-- ============================================================================

ALTER TABLE srecanje ADD COLUMN brez_boja INTEGER NOT NULL DEFAULT 0 CHECK (brez_boja IN (0, 1));

ALTER TABLE liga ADD COLUMN odbitek_brez_boja INTEGER NOT NULL DEFAULT 0
    CHECK (odbitek_brez_boja BETWEEN 0 AND 5);
