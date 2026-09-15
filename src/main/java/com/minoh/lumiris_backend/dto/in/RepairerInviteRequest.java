package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RepairerInviteRequest(@NotBlank @Email String email) {}
