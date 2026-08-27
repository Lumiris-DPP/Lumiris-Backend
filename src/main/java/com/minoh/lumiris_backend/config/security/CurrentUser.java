package com.minoh.lumiris_backend.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injecte l'utilisateur authentifié (résolu depuis le token JWT) dans un paramètre de contrôleur
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
