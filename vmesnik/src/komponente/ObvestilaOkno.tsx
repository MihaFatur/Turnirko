/* Nastavitev obvestil po kolu (meni računa → »Obvestila«).

   Po vsakem odigranem kolu dobi račun e-pošto z izidi, lestvico in svojimi
   tekmami - za lige, v katerih igra, in lige, ki jih spremlja (strežnik:
   ObvestilaPoKoluStoritev). Okno pove, za katere lige to velja, in obvestila
   vklopi oz. izklopi. Preklop je isti kvadratek kot pri spremljanju lig: oba
   sta »da / ne« in ne izbira med možnostmi. */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { obvestilaApi } from '../api/zahteve'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

export function ObvestilaOkno({ onZapri }: { onZapri: () => void }) {
  const odjemalec = useQueryClient()
  const stanje = useQuery({ queryKey: ['obvestila'], queryFn: obvestilaApi.nastavitve })
  const nastavi = useMutation({
    mutationFn: obvestilaApi.nastavi,
    onSuccess: (novo) => odjemalec.setQueryData(['obvestila'], novo),
  })

  const d = stanje.data
  const vklopljeno = nastavi.isPending ? nastavi.variables : d?.poKolu

  return (
    <ModalnoOkno nadnaslov="Račun" naslov="Obvestila" onZapri={onZapri}>
      <p className="obvestila__uvod">
        Po vsakem odigranem kolu ti pošljemo izide, lestvico in tvoje tekme s spremembo ratinga.
        Velja za lige, v katerih igraš, in lige, ki jih spremljaš.
      </p>

      {stanje.isPending && <p className="obvestilo">Nalaganje …</p>}
      <SporociloNapake napaka={stanje.error ?? nastavi.error} />

      {d && (
        <>
          <button
            type="button"
            className="spremljanje obvestila__preklop"
            aria-pressed={vklopljeno}
            disabled={nastavi.isPending}
            onClick={() => nastavi.mutate(!d.poKolu)}
          >
            <span className={'kljukica' + (vklopljeno ? ' kljukica--polna' : '')} />
            <span>E-pošta po vsakem kolu</span>
          </button>

          {!d.emailPotrjen && (
            <p className="obvestilo obvestila__opomba">
              Tvoja e-pošta še ni potrjena. Dokler ne vpišeš kode iz e-pošte (meni računa → »Vpiši
              kodo«), obvestil ne pošiljamo.
            </p>
          )}

          <div className="obvestila__lige">
            <span className="obvestila__oznaka">Lige</span>
            {d.lige.length === 0 ? (
              <p className="obvestila__prazno">
                Ne igraš v nobeni ligi, ki teče, in ne spremljaš nobene. Ligo spremljaš z gumbom
                »Spremljaj« na njeni strani.{' '}
                <Link to="/lige" onClick={onZapri}>
                  Lige →
                </Link>
              </p>
            ) : (
              <ul className="obvestila__seznam">
                {d.lige.map((l) => (
                  <li key={l.id} className="obvestila__liga">
                    <Link to={`/lige/${l.id}`} onClick={onZapri}>
                      {l.ime}
                    </Link>
                    <span className="obvestila__razlog">
                      {l.razlog === 'IGRA' ? 'igraš' : 'spremljaš'}
                      {l.sezona ? ` · ${l.sezona}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Zapri
        </button>
      </div>
    </ModalnoOkno>
  )
}
