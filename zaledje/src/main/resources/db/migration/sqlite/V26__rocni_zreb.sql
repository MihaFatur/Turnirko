-- ============================================================================
-- Turnirko: rocni zreb lige (razpored, ki ga vpise organizator)
--
-- Doslej je razpored lige nastal samo en sam nacin: organizator je vpisal
-- ekipe in pritisnil "Generiraj razpored", zreb pa je pare sestavil sam
-- (RazporedStoritev - krozni sistem oz. razpored po parih).
--
-- Liga, ki se je doslej vodila na roke, tega ne more uporabiti: pare za novo
-- sezono je njen vodja ze razdelil na papirju in jih razposlal igralcem.
-- Naklucni zreb bi razpored, ki ga imajo ljudje ze v rokah, zavrgel. Odslej
-- sme organizator razpored VPISATI (POST /lige/{id}/razpored/rocni).
--
-- Zastavica pove, od kod razpored je, in ni pravilo tekmovanja:
--   - na potek lige ne vpliva nicesar - srecanja so ista vrsta zapisa kot pri
--     generiranem zrebu, zato lestvica, termini, zapisniki in rating tecejo
--     nespremenjeno;
--   - je JAVNA: stran lige jo izpise gostu ("rocni zreb"), da igralec, ki je
--     papirnati razpored prejel po posti, vidi, da gre za isti razpored in ne
--     za nov naklucni.
--
-- Pise jo samo LigaStoritev (rocniRazpored jo prizge, razveljaviRazpored in
-- generirajRazpored ugasneta) - vpisan razpored in naklucni zreb se tako ne
-- moreta razglasiti drug za drugega.
-- ============================================================================

ALTER TABLE liga ADD COLUMN rocni_zreb INTEGER NOT NULL DEFAULT 0
    CHECK (rocni_zreb IN (0, 1));
