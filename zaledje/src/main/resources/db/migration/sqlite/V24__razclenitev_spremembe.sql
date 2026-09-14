-- ============================================================================
-- Turnirko rating: dnevnik zna razloziti svojo stevilko
--
-- Igralec, ki vidi "+27", ima pravico vedeti, od kod je prislo. Sprememba je
-- zmnozek stirih kolicin:
--
--     sprememba = K x margina x teza x (izid - pricakovano)
--
-- Nobene od njih doslej ni bilo mogoce prebrati nazaj: K je odvisen od stevila
-- tekem IN od tega, ali se je igralec takrat vracal po odsotnosti, margina od
-- razlike v nizih glede na pricakovano, teza od ravni tekmovanja, ki jo je kdo
-- lahko vmes spremenil. Poznejsi izracun bi torej dal danasnje stevilke za
-- tekmo iz leta 2019 in bi lahko protislovil zapisani spremembi.
--
-- Zato vrstica dnevnika zdaj nosi svoje sestavine. Vsaka je last TE vrstice in
-- ne tekme: K in pricakovano se med igralcema iste tekme razlikujeta (vsak ima
-- svoj K, pricakovani izid drugega je 1 minus prvega), margina in teza sta
-- skupni.
--
-- Stolpci so prazni (NULL), kadar sprememba ne nastane po tem obrazcu:
--   * postavitveni zapis in odbitek za neaktivnost (nimata tekme),
--   * UVRSTITEV NOVINCA - tam se rating ne sesteva po korakih, ampak se
--     izracuna znova iz vseh izidov prvega dne. Vrstica, ki je vezana na tekmo
--     in nima K, je torej natanko uvrstitev; vmesnik iz tega izpelje razlago.
--
-- Za nazaj se sestavine ne polnijo tu: dobi jih ponovni preracun, ki zaporedje
-- odigra znova in ob tem zapise tudi razclenitev.
-- ============================================================================

ALTER TABLE rating_zgodovina ADD COLUMN k INTEGER
    CHECK (k IS NULL OR k BETWEEN 1 AND 200);

ALTER TABLE rating_zgodovina ADD COLUMN margina REAL
    CHECK (margina IS NULL OR margina BETWEEN 0 AND 3);

ALTER TABLE rating_zgodovina ADD COLUMN teza REAL
    CHECK (teza IS NULL OR teza BETWEEN 0 AND 1);

ALTER TABLE rating_zgodovina ADD COLUMN pricakovano REAL
    CHECK (pricakovano IS NULL OR pricakovano BETWEEN 0 AND 1);
