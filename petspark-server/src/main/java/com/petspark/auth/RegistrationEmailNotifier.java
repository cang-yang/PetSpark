package com.petspark.auth;

public interface RegistrationEmailNotifier {

    boolean isAvailable();

    void sendCode(String email, String code);
}
