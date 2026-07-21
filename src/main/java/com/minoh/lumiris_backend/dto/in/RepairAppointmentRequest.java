package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RepairAppointmentRequest(
        @NotNull @Future Instant appointmentAt
) {}
