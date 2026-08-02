/* Okno za prijavo in registracijo.

   Prijavijo se administrator (uporabniško ime) in igralci (e-pošta). Ker gre
   za isto polje na strežniku, je obrazec en sam. Registracija je namenjena
   igralcem: račun nastane v stanju »čaka na potrditev«, dostop do profila pa
   odobri administrator, ko ga poveže z zapisom igralca. */
import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'

import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { opisNapake } from '../api/odjemalec'
import { authApi, klubiApi } from '../api/zahteve'
import { ModalnoOkno } from './ModalnoOkno'

type Nacin = 'prijava' | 'registracija'

export function PrijavaOkno({
  onZapri,
  zacetniNacin = 'prijava',
}: {
  onZapri: () => void
  /* Zavihek, na katerem se okno odpre (npr. iz menija "Ustvari racun"). */
  zacetniNacin?: Nacin
}) {
  const [nacin, nastaviNacin] = useState<Nacin>(zacetniNacin)

  return (
    <ModalnoOkno
      nadnaslov="Turnirko"
      naslov={nacin === 'prijava' ? 'Prijava' : 'Registracija igralca'}
      onZapri={onZapri}
    >
      <div className="zavihki">
        <button
          className={'zavihki__gumb' + (nacin === 'prijava' ? ' zavihki__gumb--aktiven' : '')}
          onClick={() => nastaviNacin('prijava')}
        >
          Prijava
        </button>
        <button
          className={'zavihki__gumb' + (nacin === 'registracija' ? ' zavihki__gumb--aktiven' : '')}
          onClick={() => nastaviNacin('registracija')}
        >
          Nov račun
        </button>
      </div>

      {nacin === 'prijava' ? (
        <PrijavaObrazec onZapri={onZapri} />
      ) : (
        <RegistracijaObrazec onNazaj={() => nastaviNacin('prijava')} />
      )}
    </ModalnoOkno>
  )
}

function PrijavaObrazec({ onZapri }: { onZapri: () => void }) {
  const { prijava } = useAvtentikacija()
  const [uporabniskoIme, nastaviUporabniskoIme] = useState('')
  const [geslo, nastaviGeslo] = useState('')
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      await prijava(uporabniskoIme.trim(), geslo)
      onZapri()
    } catch (e) {
      // 401 pomeni napačne poverilnice; sicer splošno sporočilo
      nastaviNapako(
        (e as { stanje?: number })?.stanje === 401
          ? 'Napačno uporabniško ime oz. e-pošta ali geslo.'
          : opisNapake(e),
      )
    } finally {
      nastaviPoteka(false)
    }
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      <label className="obrazec__polje">
        <span>Uporabniško ime ali e-pošta</span>
        <input
          value={uporabniskoIme}
          onChange={(d) => nastaviUporabniskoIme(d.target.value)}
          autoFocus
          required
        />
      </label>
      <label className="obrazec__polje">
        <span>Geslo</span>
        <input
          type="password"
          value={geslo}
          onChange={(d) => nastaviGeslo(d.target.value)}
          required
        />
      </label>

      {napaka && <div className="napaka">{napaka}</div>}

      {/* Edino dejanje obrazca: gumb je neposreden otrok .obrazec (flex stolpec),
          zato se raztegne cez celo sirino okna. Okno se zapre z Escape ali krizem. */}
      <button type="submit" className="gumb gumb--glavni" disabled={poteka}>
        {poteka ? 'Prijavljam …' : 'Prijava'}
      </button>

      <p className="namig">
        Brez prijave si lahko ogledaš vse turnirje, lige, lestvice in rezultate.
        Igralci se prijavijo z e-pošto in vidijo svoj profil s statistiko,
        administrator ureja tekmovanja.
      </p>
    </form>
  )
}

function RegistracijaObrazec({ onNazaj }: { onNazaj: () => void }) {
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const [organizator, nastaviOrganizator] = useState(false)
  const [ime, nastaviIme] = useState('')
  const [priimek, nastaviPriimek] = useState('')
  const [idKlub, nastaviKlub] = useState('')
  const [email, nastaviEmail] = useState('')
  const [geslo, nastaviGeslo] = useState('')
  const [napaka, nastaviNapako] = useState<string | null>(null)
  const [poteka, nastaviPoteka] = useState(false)
  const [uspeh, nastaviUspeh] = useState(false)

  async function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapako(null)
    nastaviPoteka(true)
    try {
      await authApi.registracija({
        ime: ime.trim(),
        priimek: priimek.trim(),
        idKlub: idKlub ? Number(idKlub) : null,
        email: email.trim(),
        geslo,
        organizator,
      })
      nastaviUspeh(true)
    } catch (e) {
      nastaviNapako(opisNapake(e))
    } finally {
      nastaviPoteka(false)
    }
  }

  if (uspeh) {
    return (
      <div className="obrazec">
        <p className="obvestilo">
          Račun je ustvarjen in <strong>čaka na potrditev administratorja</strong>.{' '}
          {organizator
            ? 'Ko ti dodeli vlogo organizatorja (in klub), se prijavi in začni ustvarjati turnirje ter lige.'
            : 'Ko ga potrdi in poveže s tvojim zapisom v šifrantu igralcev, se prijavi z e-pošto in geslom ter si oglej svoj profil.'}
        </p>
        <button type="button" className="gumb gumb--glavni" onClick={onNazaj}>
          Nazaj na prijavo
        </button>
      </div>
    )
  }

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      {/* Izbira vrste racuna: igralec vidi svoj profil, organizator vodi
          tekmovanja. Oba potrdi administrator. */}
      <div className="zavihki">
        <button
          type="button"
          className={'zavihki__gumb' + (!organizator ? ' zavihki__gumb--aktiven' : '')}
          onClick={() => nastaviOrganizator(false)}
        >
          Igralec
        </button>
        <button
          type="button"
          className={'zavihki__gumb' + (organizator ? ' zavihki__gumb--aktiven' : '')}
          onClick={() => nastaviOrganizator(true)}
        >
          Organizator
        </button>
      </div>

      <p className="modal__podnaslov">
        {organizator
          ? 'Registracija organizatorja (klub oz. oseba, ki vodi tekmovanja). Vpiši kontaktno ime in klub, ki ga zastopaš; administrator ti po potrditvi dodeli vlogo in klub.'
          : 'Vpiši svoje ime, priimek in klub, da te administrator lahko poveže s pravim zapisom igralca. Dostop do profila dobiš po njegovi potrditvi.'}
      </p>

      <div className="obrazec__vrstica">
        <label className="obrazec__polje">
          <span>Ime *</span>
          <input value={ime} onChange={(d) => nastaviIme(d.target.value)} required />
        </label>
        <label className="obrazec__polje">
          <span>Priimek *</span>
          <input value={priimek} onChange={(d) => nastaviPriimek(d.target.value)} required />
        </label>
      </div>

      <label className="obrazec__polje">
        <span>{organizator ? 'Klub, ki ga zastopaš' : 'Klub'}</span>
        <select value={idKlub} onChange={(d) => nastaviKlub(d.target.value)}>
          <option value="">— brez oz. ne vem —</option>
          {klubi.data?.map((k) => (
            <option key={k.id} value={k.id}>{k.ime}</option>
          ))}
        </select>
      </label>

      <label className="obrazec__polje">
        <span>E-pošta * (z njo se prijaviš)</span>
        <input
          type="email"
          value={email}
          onChange={(d) => nastaviEmail(d.target.value)}
          required
        />
      </label>
      <label className="obrazec__polje">
        <span>Geslo * (vsaj 8 znakov)</span>
        <input
          type="password"
          value={geslo}
          onChange={(d) => nastaviGeslo(d.target.value)}
          minLength={8}
          required
        />
      </label>

      {napaka && <div className="napaka">{napaka}</div>}

      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onNazaj}>
          Prekliči
        </button>
        <button type="submit" className="gumb gumb--glavni" disabled={poteka}>
          {poteka ? 'Ustvarjam …' : 'Ustvari račun'}
        </button>
      </div>
    </form>
  )
}
