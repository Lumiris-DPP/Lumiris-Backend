package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Adresse d'enlèvement de l'atelier — expéditeur du bordereau. Distincte de la vitrine publique,
// qui ne porte qu'une ville : un atelier expose sa ville sans publier sa rue, et cette adresse-ci
// n'est jamais renvoyée sur un chemin public.
public record ShipFromAddressRequest(
        @NotBlank @Size(max = 300) String line1,
        @Size(max = 300) String line2,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 120) String city,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Le pays doit être un code ISO à deux lettres.")
        String country,
        @Size(max = 40) String phone
) {}
