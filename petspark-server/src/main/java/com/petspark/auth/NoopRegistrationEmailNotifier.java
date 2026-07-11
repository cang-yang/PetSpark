package com.petspark.auth;

public class NoopRegistrationEmailNotifier implements RegistrationEmailNotifier {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void sendCode(String email, String code) {
        throw new IllegalStateException("Registration email is not configured");
    }
}
