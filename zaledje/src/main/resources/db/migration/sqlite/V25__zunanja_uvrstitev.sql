-- ============================================================================
-- Turnirko rating: zunanja uvrstitev za redke goste
--
-- Igralec, ki pri nas odigra dve tekmi na leto, ker sicer igra po svetu, ima
-- pri nas stevilko, ki o njem ne pove nicesar. Lestvica ga zato postavi
-- ocitno prenizko - ali pa ga po 18 mesecih sploh ne kaze vec. Njegova moc
-- pri tem ni neznana: zapisana je na ITTF svetovni lestvici, na tockah NTZS
-- ali na lestvici druge zveze.
--
-- ZUNANJA UVRSTITEV je zato zapis v dnevniku, s katerim administrator prenese
-- stevilko od zunaj. Od postavitve (POSTAVITEV) se locuje po treh stvareh:
--
--   1. Dovoljena je tudi igralcu, ki tekme ZE IMA - pri tem gre za popravek
--      obstojece stevilke in ne za vstopno vrednost. Prav zato obstaja.
--   2. Obvezna sta VIR in POJASNILO. Rocno vpisana stevilka na javni lestvici
--      brez zapisanega razloga je videti kot naklonjenost; z virom je trditev,
--      ki jo lahko vsak preveri. Oboje je vidno na profilu igralca.
--   3. V casovni vrsti VELJA OB SVOJEM CASU. Postavitev je IZHODISCE igralca
--      (pred prvo tekmo), zunanja uvrstitev pa popravek na dolocen dan - zato
--      jo mora ponovni preracun odigrati na istem mestu med tekmami, ne na
--      zacetku in ne na koncu.
--
-- Zakaj mora stanje nositi CAS zadnje zunanje uvrstitve
-- ----------------------------------------------------
-- Odbitek za neaktivnost in umik z javne lestvice po 18 mesecih merita, kdaj
-- smo o igralcu nazadnje kaj IZVEDELI. Doslej je bil edini vir tega tekma,
-- zato je zadostoval `zadnja_tekma_ob`. Zunanja uvrstitev je drugi tak vir in
-- je celo svezejsi: odbiti 40 tock stevilki, ki smo jo pravkar prepisali z
-- ITTF lestvice, pomeni dvakrat placati isto odsotnost, skriti takega igralca
-- z lestvice pa pomeni zavreci ravno tisto, kar smo hoteli povedati.
--
-- Zato stanje nosi oba casa, pravilo pa gleda poznejsega (RatingStanje.svezOb).
-- `zadnja_tekma_ob` ostane, kar pise: cas zadnje TEKME. Po njem se naprej
-- prepozna vrnitev po odsotnosti (visji K) - ta se namrec nanasa na igralca in
-- ne na svezino nase stevilke.
--
-- Zakaj se stolpec `razlog` ponovno ustvari
-- -----------------------------------------
-- V22 mu je dal CHECK z dvema vrednostma. sqlite omejitve stolpca ne zna
-- spremeniti, zna pa stolpec odstraniti (3.50) - zato vrednosti odlozimo,
-- stolpec postavimo znova s tremi vrednostmi in vrednosti vrnemo. Stolpec ni
-- v nobenem indeksu, zato je poseg varen.
-- ============================================================================

ALTER TABLE rating_zgodovina ADD COLUMN razlog_zacasno TEXT;
UPDATE rating_zgodovina SET razlog_zacasno = razlog WHERE razlog IS NOT NULL;

ALTER TABLE rating_zgodovina DROP COLUMN razlog;
ALTER TABLE rating_zgodovina ADD COLUMN razlog TEXT
    CHECK (razlog IS NULL
           OR razlog IN ('POSTAVITEV', 'NEAKTIVNOST', 'ZUNANJA_UVRSTITEV'));

UPDATE rating_zgodovina SET razlog = razlog_zacasno WHERE razlog_zacasno IS NOT NULL;
ALTER TABLE rating_zgodovina DROP COLUMN razlog_zacasno;

-- Vir stevilke ("ITTF svetovna lestvica, september 2026") in pojasnilo, zakaj
-- je bil poseg potreben. Pri zunanji uvrstitvi sta OBVEZNA - to je edino, kar
-- rocni poseg loci od samovolje.
ALTER TABLE rating_zgodovina ADD COLUMN vir TEXT
    CHECK (razlog <> 'ZUNANJA_UVRSTITEV' OR (vir IS NOT NULL AND TRIM(vir) <> ''));

ALTER TABLE rating_zgodovina ADD COLUMN pojasnilo TEXT
    CHECK (razlog <> 'ZUNANJA_UVRSTITEV'
           OR (pojasnilo IS NOT NULL AND TRIM(pojasnilo) <> ''));

-- Kdaj je bila stevilka nazadnje prepisana od zunaj; NULL pri vseh, ki takega
-- zapisa nimajo (torej doslej pri vseh).
ALTER TABLE rating_stanje ADD COLUMN zunanja_uvrstitev_ob TEXT;
