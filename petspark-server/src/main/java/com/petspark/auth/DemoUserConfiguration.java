package com.petspark.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoUserProperties.class)
public class DemoUserConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "petspark.demo-users", name = "enabled", havingValue = "true")
    DemoUserInitializer demoUserInitializer(
            DemoUserProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        return new DemoUserInitializer(properties, userRepository, passwordEncoder, passwordPolicy);
    }
}
