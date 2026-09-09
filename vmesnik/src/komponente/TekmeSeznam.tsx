/* Seznam tekem (krozni sistem, skupine): vsaka vrstica prikaze oba
   igralca in rezultat; ce je vnos smiseln in gre za administratorja,
   je vrstica klikljiva za vnos rezultata. */
import type { TekmaDto } from '../api/tipi'
import { OZNAKE_IZID, imeUdelezenca } from '../api/tipi'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
  /* Strnjena različica za ozek stolpec (odprta skupina): samo imeni in
     rezultat. Rating in sprememba ELO se v 300 px ne bereta - kdor ju išče,
     odpre profil igralca. */
  strnjen?: boolean
}

export function TekmeSeznam({ tekme, naKlikTekme, strnjen = false }: Lastnosti) {
  if (tekme.length === 0) {
    return <p className="obvestilo">Ni tekem.</p>
  }
  return (
    <ul className="tekme-seznam">
      {tekme.map((tekma) => (
        <Vrstica key={tekma.id} tekma={tekma} naKlik={naKlikTekme} strnjen={strnjen} />
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
  naKlik,
  strnjen,
}: {
  tekma: TekmaDto
  naKlik?: (t: TekmaDto) => void
  strnjen: boolean
}) {
  const koncana = tekma.status === 'KONCANA'
  const klikljiva =
    naKlik !== undefined && (tekma.status === 'PRIPRAVLJENA' || tekma.status === 'V_IGRI')

  const ime1 = imeUdelezenca(tekma.udelezenec1) ?? '—'
  const ime2 = imeUdelezenca(tekma.udelezenec2) ?? '—'
  const zmagovalec1 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec1?.idPrijave
  const zmagovalec2 = koncana && tekma.idZmagovalcaPrijave === tekma.udelezenec2?.idPrijave

  const posebni =
    koncana && tekma.izidTip && tekma.izidTip !== 'IGRANO' ? OZNAKE_IZID[tekma.izidTip] : null

  return (
    <li
      className={'tekme-seznam__vrstica' + (klikljiva ? ' tekme-seznam__vrstica--klikljiva' : '')}
      onClick={klikljiva ? () => naKlik!(tekma) : undefined}
      title={klikljiva ? 'Klikni za vnos rezultata' : undefined}
    >
      <span className={'tekme-seznam__igralec' + (zmagovalec1 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {ime1}
        {!strnjen && tekma.ratingPred1 !== null && (
          <span className="tekme-seznam__rating">{tekma.ratingPred1}</span>
        )}
        {!strnjen && koncana && <SpremembaElo vrednost={tekma.spremembaElo1} />}
      </span>
      {/* Rezultat pove stanje tekme: izid, "v igri", dejanje ali crtica.
          Neodigrana tekma je crtica v onemogoceni barvi - nic se ni zgodilo. */}
      <span className={'tekme-seznam__rezultat' + razredRezultata(tekma, klikljiva)}>
        {koncana
          ? `${tekma.dobljeniNizi1} : ${tekma.dobljeniNizi2}`
          : tekma.status === 'V_IGRI'
            ? 'v igri'
            : klikljiva
              ? 'vnesi'
              : '–'}
      </span>
      <span className={'tekme-seznam__igralec' + (zmagovalec2 ? ' tekme-seznam__igralec--zmaga' : '')}>
        {!strnjen && koncana && <SpremembaElo vrednost={tekma.spremembaElo2} />}
        {!strnjen && tekma.ratingPred2 !== null && (
          <span className="tekme-seznam__rating">{tekma.ratingPred2}</span>
        )}
        {ime2}
      </span>
      {posebni && <span className="tekme-seznam__izid">{posebni}</span>}
    </li>
  )
}
