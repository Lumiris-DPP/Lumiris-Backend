package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Porte l'adresse d'expédition fournie par l'atelier.
public record ShipFromAddressRequest(
        @NotBlank @Size(max = 300) String line1,
        @Size(max = 300) String line2,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 120) String city,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Le pays doit être un code ISO à deux lettres.")
        String country,
        @Size(max = 40) String phone
) {}
