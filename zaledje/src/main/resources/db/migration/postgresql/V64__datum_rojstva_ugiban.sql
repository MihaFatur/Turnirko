-- ============================================================================
-- Turnirko: znacka »datum rojstva ugiban« (V64, nov 2026)
-- Samo PostgreSQL (mapa sqlite/ je zamrznjena).
--
-- Igralec, ki mu pravega datuma rojstva ne poznamo (mladinec iz uvozenega
-- zapisnika turnirja, brez prijavnice), dobi OCENJEN datum - 1. 1. tistega
-- letnika, da ga starostni pas (U11 ... U19) uvrsti v kategorijo, v kateri
-- igra. Znacka pove, da je vpisani datum priblizek in ne podatek.
--
-- Razlika od 1. 1. 1900: 1900 pomeni »datum ni znan« in igralec velja za
-- odraslega; ugiban datum je namenoma izbran tako, da starost drzi.
-- Obstojeci igralci so vsi false (nobenemu datumu doslej nismo rekli, da je
-- priblizek), zato privzetek ne spremeni nicesar.
-- ============================================================================

ALTER TABLE igralec
    ADD COLUMN datum_rojstva_ugiban boolean NOT NULL DEFAULT false;
