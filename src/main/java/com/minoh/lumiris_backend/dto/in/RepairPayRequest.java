package com.minoh.lumiris_backend.dto.in;

import java.time.Instant;

// Le client règle le devis en prenant le rendez-vous. appointmentAt optionnel (peut être fixé
// dans la messagerie avec le retoucheur).
public record RepairPayRequest(Instant appointmentAt) {}
