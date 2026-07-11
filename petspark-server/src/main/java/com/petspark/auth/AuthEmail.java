package com.petspark.auth;

import java.util.Locale;

final class AuthEmail {

    private AuthEmail() {}

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
