-- Branje listkov S1: skrivnost v QR kodi sodniskega listka
-- (docs/NACRT-BRANJE-LISTKOV.md, faza S1).

-- Sodnik bo izid poslal s svojim telefonom brez prijave (S2). Pravico mu da
-- skrivnost v QR kodi listka: kdor ima listek v roki, sme poslati izid samo te
-- tekme. Skrivnost je HMAC id tekme s tajnim kljucem aplikacije, zato je ni treba
-- hraniti za vsako tekmo posebej.
--
-- Kljuc ustvari aplikacija ob prvi uporabi (SecureRandom) in ga zapise sem; v
-- migraciji ga ni, da ne pride v repozitorij ali dnevnik migracij. Testno okolje
-- ga dobi s kopijo produkcijske baze (listki, natisnjeni v produkciji, veljajo
-- tudi na testu - nic ne odpirajo, saj je test locena baza).
CREATE TABLE kljuc_aplikacije (
    ime varchar(32) PRIMARY KEY,
    vrednost bytea NOT NULL,
    ustvarjeno_ob timestamp(3) NOT NULL
);
