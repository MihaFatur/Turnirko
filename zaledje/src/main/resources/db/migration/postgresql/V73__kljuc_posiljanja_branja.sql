-- Branje listkov F5.2: vrsta fotografij brez signala (docs/NACRT-BRANJE-LISTKOV.md).

-- Telefon fotografijo najprej shrani in jo posilja, dokler ne dobi odgovora. Ce
-- se odgovor izgubi (signal izgine po prejemu), jo poslje znova z istim nakljucnim
-- kljucem (glava Idempotency-Key): streznik tedaj vrne ze ustvarjeno branje in ne
-- naredi dvojnika. Kljuc velja za uporabnika, ki je nalozil (tuj uporabnik ga ne
-- more »zasesti«); nocno ciscenje ga po 24 urah pobrise, ker ga telefon po tem
-- casu ne poslje vec.
ALTER TABLE branje_listka ADD COLUMN kljuc_posiljanja varchar(64);

CREATE UNIQUE INDEX uq_branje_listka_kljuc_posiljanja
    ON branje_listka (id_nalozil, kljuc_posiljanja)
    WHERE kljuc_posiljanja IS NOT NULL;
