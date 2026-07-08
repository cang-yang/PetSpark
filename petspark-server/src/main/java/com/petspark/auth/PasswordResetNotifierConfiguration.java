package com.petspark.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class PasswordResetNotifierConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "petspark.auth.password-reset.mail", name = "enabled", havingValue = "true")
    public PasswordResetNotifier smtpPasswordResetNotifier(
            JavaMailSender mailSender,
            @Value("${petspark.auth.password-reset.mail.from:}") String from) {
        return new SmtpPasswordResetNotifier(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordResetNotifier.class)
    public PasswordResetNotifier unavailablePasswordResetNotifier() {
        return new NoopPasswordResetNotifier();
    }
}
