-- Branje listkov S2: sodnik slika listek s svojim telefonom brez prijave in izid
-- potrdi sam (docs/NACRT-BRANJE-LISTKOV.md, faza S2).

-- Stikalo turnirja (organizator na strani Branje listkov): NULL = izklopljeno,
-- PREDPREGLED = sodnikova potrditev caka organizatorja (en dotik), VELJA_TAKOJ =
-- izid gre v tekmovanje ob sodnikovi potrditvi, organizator nadzira.
ALTER TABLE turnir ADD COLUMN sodniki_listki text
    CHECK (sodniki_listki IN ('PREDPREGLED', 'VELJA_TAKOJ'));

-- Sodnisko branje nima uporabnika (id_nalozil ostane NULL). Pravico mu da
-- skrivnost v QR kodi listka (S1), branje pa dobi enkraten zeton potrditve:
-- v bazi je samo SHA-256, nakljucni zeton ima samo telefon, ki je listek poslal.
--   poslal_sodnik      fotografija je prisla s strani za sodnika
--   zeton_sodnika      SHA-256 zetona potrditve; po potrditvi ali poteku NULL
--   zeton_velja_do     zeton ne velja vec po tem casu
--   sodnik_potrdil_ob  kdaj je sodnik izid potrdil (tudi pri predpregledu)
--   sodnikov_izid      izid, kot ga je potrdil sodnik (JSON, kot potrjeno)
--   sodnik_spremenil   sodnik je spremenil prebrano (organizator naj pogleda)
--   miza_sodnika       miza, kot jo je potrdil sodnik (M1 jo primerja s klicem)
ALTER TABLE branje_listka
    ADD COLUMN poslal_sodnik boolean NOT NULL DEFAULT false,
    ADD COLUMN zeton_sodnika bytea,
    ADD COLUMN zeton_velja_do timestamp(3),
    ADD COLUMN sodnik_potrdil_ob timestamp(3),
    ADD COLUMN sodnikov_izid text,
    ADD COLUMN sodnik_spremenil boolean,
    ADD COLUMN miza_sodnika integer CHECK (miza_sodnika BETWEEN 1 AND 99);

-- Nacin potrditve SODNIK: izid je potrdil sodnik (stopnja VELJA_TAKOJ).
ALTER TABLE branje_listka DROP CONSTRAINT branje_listka_nacin_potrditve_check;
ALTER TABLE branje_listka ADD CONSTRAINT branje_listka_nacin_potrditve_check
    CHECK (nacin_potrditve IN ('EN_DOTIK', 'OBRAZEC', 'SODNIK'));

-- Idempotenca sodniske poti (F5.2): sodnik nima uporabnika, zato kljuc posiljanja
-- velja za tekmo - drug telefon istega kljuca ne more »zasesti«, ker ne pozna
-- skrivnosti listka.
CREATE UNIQUE INDEX uq_branje_listka_kljuc_sodnika
    ON branje_listka (id_tekma, kljuc_posiljanja)
    WHERE poslal_sodnik AND kljuc_posiljanja IS NOT NULL;
