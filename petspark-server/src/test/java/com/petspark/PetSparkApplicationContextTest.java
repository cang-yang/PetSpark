package com.petspark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = PetSparkApplication.class,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
class PetSparkApplicationContextTest {

    @Test
    void applicationStartsWithoutDatabaseOrAiCredentials() {
        // Starting the Spring context is the assertion.
    }
}
