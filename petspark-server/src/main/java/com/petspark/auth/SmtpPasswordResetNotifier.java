package com.petspark.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpPasswordResetNotifier implements PasswordResetNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetNotifier(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("PetSpark 密码重置验证码");
        message.setText("你的密码重置验证码是：" + code + "。验证码 10 分钟内有效，请勿转发。");
        mailSender.send(message);
    }
}
