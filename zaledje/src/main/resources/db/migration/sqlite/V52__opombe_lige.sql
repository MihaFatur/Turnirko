-- ============================================================================
-- Turnirko: opombe lige (V52, okt 2026).
--
-- Turnir ima opombo ze od zacetka, liga je doslej nima. Organizator v njo zapise,
-- kar gledalec ne sme zgresiti (npr. dvorana se po dogovoru premakne). NULL = brez
-- opombe. Opomba ni pravilo tekmovanja, zato ne vpliva na razpored ali rating.
-- ============================================================================

ALTER TABLE liga ADD COLUMN opombe TEXT;
