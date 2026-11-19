# Pravila: pomoč za organizatorje

> Gumbi »?« ob funkcijah, oblaček, predal »Preberi več«, stran `/pomoc`, vsebina navodil in narisani prikazi.
> Nastalo 16.–19. 11. 2026 po naročilu lastnika.

- **Kdo vidi:** samo potrjen organizator in admin (`PomocKontekst.vidna`).
  Gledalec ne vidi ne gumbov ne strani (`/pomoc` je za `SamoUrejevalec`).
  Poglavje »Administracija« (`samoAdmin`) vidi samo admin; organizatorjev
  članek nanj ne sme kazati (varuje test).
- **Vsebina je v kodi, ne v bazi** (odločitev lastnika): `vmesnik/src/pomoc/vsebina/*.ts`,
  en članek = `Clanek` z bloki (`tipi.ts`). Besedilo pozna `**krepko**` (ime gumba,
  kot ga organizator vidi) in `[besedilo](clanek#razdelek)`.
  **Ko spremeniš funkcijo organizatorja, popravi tudi njen članek** — ime gumba,
  korake in pravilo. Zastarela navodila so slabša od nobenih.
- **Gumb »?«:** `<Namig clanek="…" razdelek="…" />`. Kratko besedilo oblačka je
  `kratko` razdelka (če ga ima), sicer članka; `shemaNamiga` doda majhen prikaz.
  »Preberi več« odpre predal na tistem razdelku. Gumb ne sme stati v `<label>`:
  za polje obrazca uporabi `OznakaZNamigom` (glej DESIGN.md 5j). Na strani za tisk
  ga nosi `ZrebOkvir` prek lastnosti `pomoc`.
- **Escape lovi ponudnik v fazi zajema**, zato zapre najprej oblaček, nato predal,
  modalno okno pod njima pa ostane odprto. Oblaček in predal sta portala v `body`
  in ustavita širjenje klikov po drevesu Reacta (sicer bi klik v oblaček sprožil
  vrstico ali okno, v katerem gumb stoji).
- **Prikazi** (`pomoc/sheme/`) so komponente; ključ doda `sheme/kljuci.ts`, register
  `sheme/index.tsx` (tip Record zahteva prikaz za vsak ključ). Vsi prikazi na enem
  mestu: `/dev/pomoc-sheme` (samo razvoj), v širini predala in oblačka.
- **Test celovitosti** `pomoc/pomoc.test.ts`: vsaka povezava, razdelek in
  »Povezano« obstaja; vsak `<Namig>` in `pomoc="…"` v kodi kaže na obstoječ članek
  in razdelek; iskanje najde brez šumnikov in vodi v razdelek.
- **Iskanje** (`pomoc/iskanje.ts`) uporablja pravilo iskalnikov igralcev (brez
  šumnikov, besede v poljubnem vrstnem redu); teže: naslov 10, ključne besede 7,
  naslov razdelka 6, povzetki 4, telo 1. Izraze, ki jih v besedilu ni (»seed«,
  »bye«, »walkover«), dodaj v `kljucne`.
