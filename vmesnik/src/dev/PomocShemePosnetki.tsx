/* Razvojni pregled vseh narisanih prikazov pomoči (/dev/pomoc-sheme), v
   širini predala (460 px) in oblačka (340 px), da se vidi, ali se kak prikaz
   na ozkem prelomi narobe. Samo razvoj (App ga uvozi pod import.meta.env.DEV). */
import { KLJUCI_SHEM } from '../pomoc/sheme/kljuci'
import { Shema } from '../pomoc/sheme'

export default function PomocShemePosnetki() {
  return (
    <div style={{ padding: 16, display: 'grid', gap: 24, background: 'var(--barva-papir)' }}>
      {KLJUCI_SHEM.map((kljuc) => (
        <section key={kljuc} data-shema={kljuc} style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'start' }}>
          <h2 className="sh-napis" style={{ width: 140 }}>{kljuc}</h2>
          <div style={{ width: 412 }}>
            <Shema kljuc={kljuc} />
          </div>
          <div className="namig-oblacek" style={{ position: 'static', width: 340 }}>
            <Shema kljuc={kljuc} />
          </div>
        </section>
      ))}
    </div>
  )
}
