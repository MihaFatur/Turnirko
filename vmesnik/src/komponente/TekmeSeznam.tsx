/* Seznam tekem (krozni sistem, skupine): vsaka vrstica prikaze oba
   udelezenca in rezultat. Katera vrstica je klikljiva in kaj klik pomeni
   (vnos rezultata ali zapisnik ekipnega srecanja), pove stran s KlikTekme. */
import type { TekmaDto } from '../api/tipi'
import { OZNAKE_IZID, imeUdelezenca } from '../api/tipi'
import { SpremembaRatinga } from './SpremembaRatinga'
import type { KlikTekme } from './TekmaKartica'

interface Lastnosti {
  tekme: TekmaDto[]
  klik?: KlikTekme
  /* Strnjena različica za ozek stolpec (odprta skupina): samo imeni in
     rezultat. Rating in sprememba ratinga se v 300 px ne bereta - kdor ju išče,
     odpre profil igralca. */
  strnjen?: boolean
}

export function TekmeSeznam({ tekme, klik, strnjen = false }: Lastnosti) {
  if (tekme.length === 0) {
    return <p className="obvestilo">Ni tekem.</p>
  }
  return (
    <ul className="tekme-seznam">
      {tekme.map((tekma) => (
        <Vrstica key={tekma.id} tekma={tekma} klik={klik} strnjen={strnjen} />
      ))}
    </ul>
  )
}

function razredRezultata(tekma: TekmaDto, klikljiva: boolean): string {
  if (tekma.status === 'KONCANA') return ''
  if (tekma.status === 'V_IGRI') return ' tekme-seznam__rezultat--v-igri'
  return klikljiva ? '' : ' tekme-seznam__rezultat--prazen'
}

function Vrstica({
  tekma,
  klik,
  strnjen,
}: {
  tekma: TekmaDto
  klik?: KlikTekme
  strnjen: boolean
}) {
  const koncana = tekma.status === 'KONCANA'
  const klikljiva = klik !== undefined && klik.klikljiva(tekma)

  const ime1 = imeUdelezenca(tekma.udelezenec1) ?? '—'
  const ime2 = imeUdelezenca(tekma.udelezenec2) ?? '—'
  const zmagovalec1 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec1?.idPrijave
  const zmagovalec2 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec2?.idPrijave

  /* Prenesen izid finalne skupine ni nova tekma: dvoboj je bil odigran v
     predtekmovanju in se ne igra znova (PST, 14. člen). Brez oznake bi bil
     videti kot tekma, ki sta jo ekipi odigrali dvakrat. */
  const prenesena = tekma.idPrenesena !== null
  const posebni = prenesena
    ? 'prenesen izid iz predtekmovanja'
    : koncana && tekma.izidTip && tekma.izidTip !== 'IGRANO'
      ? OZNAKE_IZID[tekma.izidTip]
      : null

  return (
    <li
      className={'tekme-seznam__vrstica' + (klikljiva ? ' tekme-seznam__vrstica--klikljiva' : '')}
      onClick={klikljiva ? () => klik.naKlik(tekma) : undefined}
      title={klikljiva ? klik.namig(tekma) : undefined}
    >
      <span className={'tekme-seznam__igralec' + (zmagovalec1 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {ime1}
        {!strnjen && tekma.ratingPred1 !== null && (
          <span className="tekme-seznam__rating">{tekma.ratingPred1}</span>
        )}
        {!strnjen && koncana && <SpremembaRatinga vrednost={tekma.spremembaElo1} />}
      </span>
      {/* Rezultat pove stanje tekme: izid, "v igri", dejanje ali crtica.
          Neodigrana tekma je crtica v onemogoceni barvi - nic se ni zgodilo. */}
      <span className={'tekme-seznam__rezultat' + razredRezultata(tekma, klikljiva)}>
        {koncana
          ? `${tekma.dobljeniNizi1} : ${tekma.dobljeniNizi2}`
          : tekma.status === 'V_IGRI'
            ? 'v igri'
            : klikljiva
              ? (tekma.idSrecanje !== null ? 'zapisnik' : 'vnesi')
              : '–'}
      </span>
      <span className={'tekme-seznam__igralec' + (zmagovalec2 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {!strnjen && koncana && <SpremembaRatinga vrednost={tekma.spremembaElo2} />}
        {!strnjen && tekma.ratingPred2 !== null && (
          <span className="tekme-seznam__rating">{tekma.ratingPred2}</span>
        )}
        {ime2}
      </span>
      {posebni && (
        <span className={'tekme-seznam__izid' + (prenesena ? ' tekme-seznam__izid--prenesen' : '')}>
          {posebni}
        </span>
      )}
    </li>
  )
}
