package com.petspark.auth;

public interface PasswordResetNotifier {
    boolean isAvailable();

    void sendCode(String email, String code);
}
