package com.petspark.auth;

public interface PasswordResetNotifier {
    void sendCode(String email, String code);
}
