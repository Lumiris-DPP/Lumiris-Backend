package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank String name,
        @NotNull UserRole role,
        // Optionnel (artisan) : vide ou exactement 14 chiffres. Aligné sur ArtisanRegisterRequest —
        // évite qu'un SIRET malformé parte tel quel à la SIRENE et fasse échouer toute l'inscription.
        @Pattern(regexp = "^(\\d{14})?$", message = "Le SIRET doit comporter 14 chiffres.") String siret
) {}
