-- Izrez celice ob polju pri pregledu listka (F4.1, docs/NACRT-BRANJE-LISTKOV.md):
-- bralnik shrani listek, poravnan v stalne koordinate (10 px/mm, sivine, JPEG
-- ~100 kB), in lego celic na njem. Vmesnik iz ene slike pokaze izrez vsake celice
-- ob njenem polju, zato organizator ne isce stevilke po celi fotografiji.
--
-- Poravnana slika je v isti vrstici kot fotografija, zato jo nocno brisanje po
-- 30 dneh (BranjeListkovStoritev.izbrisiStareSlike) izbrise hkrati z njo. Lega
-- celic so samo stevila (brez imen) in ostane ob branju.
ALTER TABLE branje_listka_slika ADD COLUMN poravnana bytea;
ALTER TABLE branje_listka ADD COLUMN celice text;
