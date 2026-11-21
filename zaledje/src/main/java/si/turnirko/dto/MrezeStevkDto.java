/* Mreza stevk in njeno ucenje (F6.4, samo admin): katera razlicica bere listke,
   ali ucenje tece, koliko novih stevk caka in zgodovina razlicic. */
package si.turnirko.dto;

import java.time.LocalDateTime;
import java.util.List;

import si.turnirko.modeli.MrezaStevkZapis;

public record MrezeStevkDto(
        /* id razlicice v uporabi; null = vgrajena (naucena samo na MNIST) */
        Long vUporabi,
        boolean uciSe,
        LocalDateTime ucenjeOd,
        MrezaStevkZapis.Nacin nacinUcenja,
        /* zadnje sporocilo ucenja, ki ni v zgodovini (odlozeno, napaka) */
        String sporocilo,
        LocalDateTime sporociloOb,
        /* odobrene stevke od zadnjega ucenja in koliko jih ucenje potrebuje */
        long novihStevk,
        int najmanjNovih,
        /* vse odobrene stevke (z listkov in vadnice) in od tega z vadnice */
        long stevkVUcenju,
        long stevkVadnice,
        /* pred tem casom »Uci zdaj« ni mogoc (premor med zagoni); null = zdaj */
        LocalDateTime uciZdajOd,
        List<RazlicicaMrezeDto> razlicice
) {}
