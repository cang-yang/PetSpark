package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoUserInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicy passwordPolicy;

    private DemoUserProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DemoUserProperties();
        properties.setAdminPassword("AdminPass123");
        properties.setMemberPassword("MemberPass123");
    }

    @Test
    void createsAdminAndMemberWithoutPersistingPlaintextPasswords() {
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        DemoUserInitializer initializer = new DemoUserInitializer(
                properties, userRepository, passwordEncoder, passwordPolicy);

        initializer.initialize();

        ArgumentCaptor<SysUser> member = ArgumentCaptor.forClass(SysUser.class);
        ArgumentCaptor<SysUser> admin = ArgumentCaptor.forClass(SysUser.class);
        verify(userRepository).insert(member.capture());
        verify(userRepository).insertWithRole(admin.capture(), org.mockito.Mockito.eq(DemoUserInitializer.ADMIN_ROLE_ID));
        org.assertj.core.api.Assertions.assertThat(java.util.List.of(member.getValue(), admin.getValue()))
                .allSatisfy(user -> org.assertj.core.api.Assertions.assertThat(user.passwordHash()).isEqualTo("bcrypt-hash"));
    }

    @Test
    void existingAccountsAreLeftUntouched() {
        when(userRepository.existsByUsernameOrEmail(properties.getAdminUsername(), properties.getAdminEmail())).thenReturn(true);
        when(userRepository.existsByUsernameOrEmail(properties.getMemberUsername(), properties.getMemberEmail())).thenReturn(true);

        new DemoUserInitializer(properties, userRepository, passwordEncoder, passwordPolicy).initialize();

        verify(userRepository, never()).insert(any());
        verify(userRepository, never()).insertWithRole(any(), any());
    }

    @Test
    void refusesToStartWhenARequiredPasswordIsMissing() {
        properties.setAdminPassword("");

        assertThatThrownBy(() -> new DemoUserInitializer(
                properties, userRepository, passwordEncoder, passwordPolicy).initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PETSPARK_DEMO_ADMIN_PASSWORD");
    }
}
