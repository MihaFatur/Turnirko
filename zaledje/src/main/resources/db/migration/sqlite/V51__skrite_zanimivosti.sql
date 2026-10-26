-- ============================================================================
-- Turnirko: razdelki zavihka Zanimivosti, ki jih organizator skrije gledalcem
-- (V51, okt 2026).
--
-- Zapis je imena konstant PostavkaZanimivosti, locena z vejico (npr. "ZID,KLUBI").
-- NULL pomeni "vse vidno", kar je privzetek za vsa tekmovanja, ki so nastala pred
-- to migracijo. Skrivanje ne brise podatkov - razdelek je samo izpusten iz izpisa.
-- ============================================================================

ALTER TABLE turnir ADD COLUMN skrite_zanimivosti TEXT;
ALTER TABLE liga ADD COLUMN skrite_zanimivosti TEXT;
