package com.petspark.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class NoopPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoopPasswordResetNotifier.class);

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void sendCode(String email, String code) {
        log.info("Password reset delivery requested; configure a mail adapter to deliver it");
    }
}
