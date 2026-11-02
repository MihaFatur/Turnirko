-- ============================================================================
-- Socasnost (prenova baze, faza 2; docs/PRENOVA-BAZE.md, razdelek 5)
--
-- Stevilka 53.1 je namerna: pravila veljajo samo za PostgreSQL (SQLite jih ne
-- potrebuje - tam je pisec en sam), zato dvojnice v mapi sqlite/ nimajo, in
-- ne smejo zasesti stevilke, ki jo bo dobila naslednja skupna migracija (V54).
--
-- Tekma se v dnevnik ratinga obracuna enkrat na igralca. Pravilo varuje
-- zaklep ratinga (Zaklepi.zakleniRating) in preverba existsByTekmaId, ta
-- indeks pa je zadnja obramba: drugi obracun iste tekme se ne zapise tiho,
-- ampak pade (409). Produkcijska kopija (27. 10. 2026) nima nobenega dvojnika.
-- ============================================================================

CREATE UNIQUE INDEX uq_rating_zgodovina_tekma
    ON rating_zgodovina (id_tekma, id_igralec, sistem)
    WHERE id_tekma IS NOT NULL;

CREATE UNIQUE INDEX uq_rating_zgodovina_tekma_srecanja
    ON rating_zgodovina (id_tekma_srecanja, id_igralec, sistem)
    WHERE id_tekma_srecanja IS NOT NULL;
