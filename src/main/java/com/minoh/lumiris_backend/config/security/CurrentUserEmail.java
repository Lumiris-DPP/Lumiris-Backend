package com.minoh.lumiris_backend.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injects the authenticated user's email (the JWT subject) into a controller method parameter,
// replacing the repeated "@AuthenticationPrincipal UserDetails principal" + principal.getUsername().
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserEmail {
}
