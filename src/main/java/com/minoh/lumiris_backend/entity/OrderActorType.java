package com.minoh.lumiris_backend.entity;

// Qui a provoqué la transition — distingue une action humaine d'une échéance automatique.
public enum OrderActorType {
    BUYER,
    SELLER,
    PLATFORM,
    SYSTEM
}
