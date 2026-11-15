/* Meja napak: napaka med IZRISOM strani ne sme pustiti belega zaslona.

   React ob izjemi v izrisu odstrani celo drevo do najbližje meje. Brez nje je
   gledalec v dvorani ostal pred praznim zaslonom, brez glave in brez poti
   nazaj (pregled kakovosti, najdba G1). Podatki iz uvoza imajo robne primere
   (prazna polja, datumi 1. 1. 1900), zato se taka izjema prej ali slej zgodi.

   Stojita dve: ena okoli vsebine strani v `Postavitev` (glava in navigacija
   ostaneta, gledalec lahko gre drugam) in ena okoli cele aplikacije v
   `main.tsx` (strani za tisk, ki postavitve nimajo, in napaka v sami glavi).

   Razredna komponenta, ker React meje napak za funkcijske komponente nima.
   Sporočilo je namenoma brez usmerjevalnika (`<a>`, ne `<Link>`): zunanja meja
   stoji tudi tam, kjer je padlo prav drevo usmerjevalnika.

   Za napake pri BRANJU podatkov je NapakaPoizvedbe, za napake obrazcev
   SporociloNapake; ta ujame samo izjeme izrisa. */
import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Lastnosti {
  children: ReactNode
  /* Ko se vrednost spremeni (pot strani), meja napako pozabi in poskusi
     vsebino izrisati znova: klik v navigaciji mora gledalca pripeljati na
     drugo stran, ne nazaj na isto sporočilo. Brez napake sprememba ne naredi
     nič - otroci se ne odmontirajo. */
  ponastaviOb?: string
  /* Zunanja meja nima postavitve okoli sebe, zato sporočilo dobi odmik strani. */
  celaStran?: boolean
}

interface Stanje {
  napaka: boolean
}

export class MejaNapak extends Component<Lastnosti, Stanje> {
  state: Stanje = { napaka: false }

  static getDerivedStateFromError(): Stanje {
    return { napaka: true }
  }

  componentDidCatch(napaka: Error, podatki: ErrorInfo) {
    /* Sled za tistega, ki bo napako iskal: brskalnik jo pokaže v konzoli. */
    console.error('Napaka med izrisom strani:', napaka, podatki.componentStack)
  }

  componentDidUpdate(prej: Lastnosti) {
    if (this.state.napaka && prej.ponastaviOb !== this.props.ponastaviOb) {
      this.setState({ napaka: false })
    }
  }

  render() {
    if (!this.state.napaka) return this.props.children
    const sporocilo = (
      <div className="napaka" role="alert">
        <div className="napaka__vrsta">
          <span>
            Te strani ni bilo mogoče prikazati.
            <span className="napaka__pojasnilo">
              Napaka je v aplikaciji, ne pri tebi. Naloži stran znova; če se ponovi, se vrni na
              domačo stran.
            </span>
          </span>
          <button type="button" className="gumb gumb--majhen" onClick={() => window.location.reload()}>
            Naloži znova
          </button>
          <a href="/" className="gumb gumb--majhen">
            Na domačo stran
          </a>
        </div>
      </div>
    )
    return this.props.celaStran ? <main className="vsebina">{sporocilo}</main> : sporocilo
  }
}
