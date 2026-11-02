/* Izbira logotipa tekmovanja (PUT /lige/{id}/logotip, /turnirji/{id}/logotip):
   ze nalozena slika iz knjiznice. Odstranitev je DELETE na isti poti. */
package si.turnirko.dto;

import jakarta.validation.constraints.NotNull;

public record LogotipVnos(@NotNull Long idSlika) {}
