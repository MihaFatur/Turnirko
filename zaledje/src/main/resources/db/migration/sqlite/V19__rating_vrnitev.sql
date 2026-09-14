-- ============================================================================
-- Turnirko rating: stanje igralca ve, kdaj je nazadnje igral
--
-- Nova formula ima pribitek k faktorju K za igralca, ki se vrne po vec kot
-- letu dni odsotnosti (prvih 15 tekem). Za to je treba ob vsaki tekmi vedeti,
-- kdaj je igralec nazadnje igral - podatek, ki ga tabela stanja doslej ni
-- imela. Isti stolpec bo kasneje nosil se odbitek za neaktivnost (korak 5)
-- in skrivanje z javne lestvice po 18 mesecih.
--
--   zadnja_tekma_ob    - cas (velja_ob) zadnje obracunane tekme igralca;
--                        NULL, dokler igralec ni odigral nobene.
--   preostanek_vrnitve - koliko tekem ima se povisan K zaradi vrnitve.
--
-- Polnjenje za nazaj: zadnja_tekma_ob se da tocno prebrati iz dnevnika
-- (najvecji velja_ob med zapisi, vezanimi na tekmo). Preostanka vrnitve pa iz
-- zadnjega stanja ni mogoce uganiti - dobi ga ponovni preracun, ki zaporedje
-- odigra znova in pri tem zazna vsako vrnitev. Do takrat je 0 (brez pribitka),
-- kar je konzervativna izbira: raje nihce nima pribitka, kot da bi ga dobil
-- kdo, ki mu ne pripada.
-- ============================================================================

ALTER TABLE rating_stanje ADD COLUMN zadnja_tekma_ob TEXT;
ALTER TABLE rating_stanje ADD COLUMN preostanek_vrnitve INTEGER NOT NULL DEFAULT 0;

UPDATE rating_stanje
   SET zadnja_tekma_ob = (
        SELECT MAX(z.velja_ob)
          FROM rating_zgodovina z
         WHERE z.id_igralec = rating_stanje.id_igralec
           AND z.sistem = rating_stanje.sistem
           AND (z.id_tekma IS NOT NULL OR z.id_tekma_srecanja IS NOT NULL));
