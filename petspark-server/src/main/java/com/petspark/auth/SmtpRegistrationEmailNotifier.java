package com.petspark.auth;

import org.springframework.mail.javamail.JavaMailSender;

public class SmtpRegistrationEmailNotifier implements RegistrationEmailNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpRegistrationEmailNotifier(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendCode(String email, String code) {
        VerificationEmailComposer.send(mailSender, from, email,
                "PetSpark 注册验证码", "注册 PetSpark 账号", code);
    }
}
