package com.petspark.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class RegistrationEmailNotifierConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "petspark.auth.registration.mail", name = "enabled", havingValue = "true")
    public RegistrationEmailNotifier smtpRegistrationEmailNotifier(
            JavaMailSender mailSender,
            @Value("${petspark.auth.registration.mail.from:}") String from) {
        return new SmtpRegistrationEmailNotifier(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(RegistrationEmailNotifier.class)
    public RegistrationEmailNotifier unavailableRegistrationEmailNotifier() {
        return new NoopRegistrationEmailNotifier();
    }
}
