/* Prikazni harness za posnetke končne razvrstitve (design_handoff_koncna_razvrstitev,
   razdelek 9.1). Samo za razvoj: /dev/koncna-razvrstitev, naloži se leno in samo,
   kadar import.meta.env.DEV (glej App.tsx). Brez prijave in omrežja; podatki so
   fiksni (primerPodatki.json iz predaje).

   Parametri:
     primer=izlocilni|krozni|ekipni
     stanje=zaprto|odprto     seznam ostalih za gumbom »Še N« razgrnjen ali ne
     naprava=namizje|telefon  ovoj 1200 px oz. 390 px z 20 px odmikom (okno mora
                              biti res široko 390 px: telefonska postavitev sledi
                              medijski poizvedbi)
     prikaz=zaslon|tisk       razdelek na zaslonu ali list A4 za tisk (794 px) */
import { useSearchParams } from 'react-router-dom'

import { KoncnaRazvrstitev } from '../komponente/KoncnaRazvrstitev'
import { IzpisListi } from '../komponente/IzpisListi'
import { listiRazvrstitve, visinaVsebine } from '../pomozno/izpisRezultatov'
import { SlikaIgralca } from '../komponente/SlikaIgralca'
import type { UdelezenecRazvrstitve } from '../pomozno/koncnaRazvrstitev'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import { type FotoPrimera, PRIMERI } from './koncnaRazvrstitevPodatki'

/* Razvojni nadomestek fotografije (Foto.dc.html): kvadrat z 1 px črnila, bela
   podlaga, poševne proge 45° (8 × 8, proga 3 px) in »FOTO« levo spodaj. */
function FotoNadomestek({ razred }: { razred: string }) {
  return (
    <div className={`slika-igralca ${razred}`} style={{ position: 'relative', overflow: 'hidden' }}>
      <svg
        width="100%"
        height="100%"
        style={{ position: 'absolute', left: 0, top: 0, display: 'block' }}
        aria-hidden="true"
      >
        <defs>
          <pattern id="foto-pruge" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="3" height="8" fill="#ede7df" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#foto-pruge)" />
      </svg>
      <span
        style={{
          position: 'absolute',
          left: 6,
          bottom: 5,
          font: '500 10px/1 var(--pisava-mono)',
          letterSpacing: '0.1em',
          color: 'var(--barva-crnilo-3)',
        }}
      >
        FOTO
      </span>
    </div>
  )
}

function Okvir() {
  const [iskanje] = useSearchParams()
  const kljucPrimera = iskanje.get('primer')
  const primer = PRIMERI[kljucPrimera === 'krozni' || kljucPrimera === 'ekipni' ? kljucPrimera : 'izlocilni']
  const telefon = iskanje.get('naprava') === 'telefon'
  const tisk = iskanje.get('prikaz') === 'tisk'

  const fotoPoId = new Map<number, FotoPrimera>()
  const udelezenci: UdelezenecRazvrstitve[] = primer.udelezenci.map((u, i) => {
    fotoPoId.set(i, u.foto)
    const k = u.steviloNaMestu
    return {
      id: i,
      mesto: u.koncnoMesto,
      oznaka: k > 1 ? `${u.koncnoMesto}.–${u.koncnoMesto + k - 1}.` : `${u.koncnoMesto}.`,
      ime: u.ime,
      priimek: u.priimek,
      klub: u.klub,
      izid: u.izid ?? '',
      /* Izbira slike v primeru: »slika« in »obris« sta obe okvir, razlikuje ju izris. */
      slika: u.foto === 'slika' ? 'dev' : null,
    }
  })
  const zSlikami = primer.udelezenci.some((u) => u.koncnoMesto <= 3 && u.foto !== 'brez')
  const enota = primer.ekipno ? sklonEkip : sklonIgralcev

  if (tisk) {
    return (
      <div id="posnetek" className="zreb-stran--izpis" style={{ width: 794, background: '#fff' }}>
        <IzpisListi
          listi={listiRazvrstitve(udelezenci, 2, visinaVsebine('pokoncni', false))}
          glava={{
            naslov: primer.naslov,
            lastnosti: `${primer.lastnosti} · ${udelezenci.length} ${enota(udelezenci.length)}`,
            kdajKje: null,
          }}
          logotip={null}
          pokrovitelji={[]}
        />
      </div>
    )
  }

  return (
    <div
      style={{
        background: 'var(--barva-papir)',
        padding: telefon ? '16px 0 40px' : '40px 40px 56px',
        minHeight: '100vh',
      }}
    >
      <div
        id="posnetek"
        style={telefon ? { width: 390, padding: '20px 20px 0', boxSizing: 'border-box' } : { width: 1200 }}
      >
        <KoncnaRazvrstitev
          key={iskanje.toString()}
          udelezenci={udelezenci}
          ekipno={primer.ekipno}
          jeTelefon={telefon}
          zSlikami={zSlikami}
          kljucVstopa={iskanje.toString()}
          povezavaTisk="/dev/koncna-razvrstitev?prikaz=tisk"
          razgrnjeno={iskanje.get('stanje') === 'odprto'}
          izrisiSliko={(u, razred) =>
            fotoPoId.get(u.id) === 'slika' ? (
              <FotoNadomestek razred={razred} />
            ) : (
              <SlikaIgralca zgostitev={null} velikost="VELIKA" razred={razred} />
            )
          }
        />
      </div>
    </div>
  )
}

export default Okvir
