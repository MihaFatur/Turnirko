/* En turnir v sklopu "Turnirji" domace strani igralca s Premium: kateri in
   zakaj stoji tam (IzborTurnirjev). Vmesnik ima seznam turnirjev ze nalozen,
   zato tu ni celega TurnirDto - samo izbor in razlog, ki ga izpise ob vrstici. */
package si.turnirko.dto;

public record DomovTurnirDto(Long idTurnir, Razlog razlog) {

    public enum Razlog {
        /* prihaja in igralec je ze prijavljen */
        PRIJAVLJEN,
        /* najblizji prihajajoci, ki mu utegne biti primeren */
        PRIHAJA_PRIMEREN,
        /* turnir, na katerem je nazadnje nastopil */
        ZADNJI,
        V_TEKU,
        /* igrajo (ali so igrali) igralci njegovega kluba */
        KOLEGI,
        /* primeren po ravni in razpisu */
        PRIMEREN,
        /* zapolnitev - vmesnik po datumu pove "prihaja" ali "nedavno" */
        OSTALO
    }
}
