-- ============================================================================
-- Turnirko: kvalifikacije med ligami (oktober 2026)
--
-- Savinja liga A-D (in SNTL) ne menjata ekip samo neposredno: ekipe tik nad
-- izpadom visje lige in tik pod napredovanjem nizje igrajo kvalifikacije za
-- mesto v visji ligi (npr. 9. iz A proti 3. iz B).
--
-- 1. Liga pove, koliko ekip igra kvalifikacije - loceno navzgor (tik pod
--    neposrednim napredovanjem) in navzdol (tik nad neposrednim izpadom),
--    enako kot st_napreduje/st_izpade. Kot prehodi nista pravilo tekmovanja.
--
-- 2. Same kvalifikacije so svoja (majhna) liga - tako jih je ze uvozila stara
--    stran SNTL (npr. "I./II. SNTL MOSKI": dve ekipi, samo koncnica). Liga
--    kvalifikacij nosi obe ligi, med katerima se igra; ekipe so kopije ekip
--    obeh lig s kadri. Navadna liga ima oba stolpca prazna.
-- ============================================================================

ALTER TABLE liga ADD COLUMN st_kvalifikacije_gor INTEGER NOT NULL DEFAULT 0
    CHECK (st_kvalifikacije_gor >= 0);
ALTER TABLE liga ADD COLUMN st_kvalifikacije_dol INTEGER NOT NULL DEFAULT 0
    CHECK (st_kvalifikacije_dol >= 0);

ALTER TABLE liga ADD COLUMN id_kvalifikacije_visja INTEGER REFERENCES liga (id);
ALTER TABLE liga ADD COLUMN id_kvalifikacije_nizja INTEGER REFERENCES liga (id)
    CHECK ((id_kvalifikacije_nizja IS NULL) = (id_kvalifikacije_visja IS NULL)
           AND (id_kvalifikacije_nizja IS NULL OR id_kvalifikacije_nizja <> id_kvalifikacije_visja));

CREATE INDEX idx_liga_kvalifikacije ON liga (id_kvalifikacije_visja, id_kvalifikacije_nizja);
