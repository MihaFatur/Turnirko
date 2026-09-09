-- ============================================================================
-- Turnirko: enakomerna razvrstitev ekip (zreb lige po parih)
--
-- Doslej je zreb lige tekel po cisti circle metodi nad ekipami v vrstnem redu
-- vpisa - kdo je mocan in kdo sibak, generatorja ni zanimalo. Posledica je bila
-- neenakomeren zacetek sezone: ena ekipa je v prvih kolih dobila same favorite,
-- druga same tekmece z dna lestvice.
--
-- Odslej sme organizator ligo oznaciti z ENAKOMERNO RAZVRSTITVIJO. Takrat
-- ekipam doloci jakostni vrstni red (st_nosilca, 1 = najmocnejsa), zreb pa jih
-- razdeli v PARE: i-ta ekipa zgornje polovice se zveze z i-to ekipo spodnje
-- polovice. Par nastopa kot celota - v vsakem krogu odigra oba svoja dvoboja
-- proti istemu nasprotnemu paru, torej vsaka njegova ekipa enkrat proti
-- zgornji in enkrat proti spodnji ekipi nasprotnega para; enkrat na sezono pa
-- para odigrata dvoboj med sabo.
--
-- st_nosilca je poimenovan enako kot prijava.st_nosilca (mesto na jakostni
-- lestvici) - isti pojem, druga vrsta tekmovanja.
--
-- Enolicnosti (id_liga, st_nosilca) namenoma NE vsiljuje indeks: vrstni red se
-- prestavlja z zamenjavo mest in se zato zapise za VSE ekipe lige naenkrat,
-- vmesna stanja pa bi ob preverjanju po vrsticah trcila. Enolicnost varuje
-- LigaStoritev, ki mesta vedno prestevilci od 1 naprej.
-- ============================================================================

ALTER TABLE liga ADD COLUMN enakomerna_razvrstitev INTEGER NOT NULL DEFAULT 0
    CHECK (enakomerna_razvrstitev IN (0, 1));

ALTER TABLE ekipa ADD COLUMN st_nosilca INTEGER
    CHECK (st_nosilca IS NULL OR st_nosilca >= 1);
