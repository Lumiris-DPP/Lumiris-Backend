package com.minoh.lumiris_backend.entity;

// Nature d'une échéance de versement vendeur. SCHEDULED porte une date ; IMMINENT signifie que le
// versement est dû et rejoué par le balayage, sans jour promis ; ON_HOLD qu'un litige ou un retour
// le suspend jusqu'à décision humaine.
public enum PayoutExpectation {
    SCHEDULED,
    IMMINENT,
    ON_HOLD
}
