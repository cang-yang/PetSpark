package com.petspark.auth;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

public class DemoUserInitializer implements ApplicationRunner, Ordered {

    static final String ADMIN_ROLE_ID = "00000000-0000-0000-0000-000000000102";

    private final DemoUserProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    DemoUserInitializer(
            DemoUserProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize();
    }

    @Override
    public int getOrder() {
        return 100;
    }

    void initialize() {
        String adminPassword = requiredPassword(
                properties.getAdminPassword(), "PETSPARK_DEMO_ADMIN_PASSWORD");
        String memberPassword = requiredPassword(
                properties.getMemberPassword(), "PETSPARK_DEMO_MEMBER_PASSWORD");
        passwordPolicy.validate(adminPassword);
        passwordPolicy.validate(memberPassword);

        if (!userRepository.existsByUsernameOrEmail(
                properties.getAdminUsername(), properties.getAdminEmail())) {
            userRepository.insertWithRole(user(
                    properties.getAdminUsername(),
                    properties.getAdminEmail(),
                    properties.getAdminNickname(),
                    adminPassword), ADMIN_ROLE_ID);
        }
        if (!userRepository.existsByUsernameOrEmail(
                properties.getMemberUsername(), properties.getMemberEmail())) {
            userRepository.insert(user(
                    properties.getMemberUsername(),
                    properties.getMemberEmail(),
                    properties.getMemberNickname(),
                    memberPassword));
        }
    }

    private SysUser user(String username, String email, String nickname, String password) {
        return new SysUser(
                UUID.randomUUID().toString(),
                username,
                email,
                passwordEncoder.encode(password),
                nickname,
                "ACTIVE",
                0);
    }

    private String requiredPassword(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "演示账号已启用，但缺少环境变量 " + environmentName);
        }
        return value;
    }
}
