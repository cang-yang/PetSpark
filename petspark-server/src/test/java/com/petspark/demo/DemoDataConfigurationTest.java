package com.petspark.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoDataConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DemoDataConfiguration.class);

    @Test
    void demoDataIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DemoDataInitializer.class);
        });
    }

    @Test
    void enabledDemoDataFailsClosedWithoutDemoUsersInitializer() {
        contextRunner
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("petspark.demo-data.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("DemoUserInitializer");
                });
    }
}
