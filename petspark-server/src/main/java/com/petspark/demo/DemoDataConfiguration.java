package com.petspark.demo;

import com.petspark.auth.DemoUserInitializer;
import com.petspark.auth.DemoUserProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DemoDataProperties.class, DemoUserProperties.class})
public class DemoDataConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "petspark.demo-data", name = "enabled", havingValue = "true")
    DemoDataInitializer demoDataInitializer(
            DemoDataProperties properties,
            DemoUserProperties demoUsers,
            JdbcTemplate jdbcTemplate,
            DemoUserInitializer demoUserInitializer) {
        // demoUserInitializer 是有意保留的依赖：只有显式创建完两组演示账号后才填充关联数据。
        return new DemoDataInitializer(properties, demoUsers, jdbcTemplate, Clock.systemUTC());
    }
}
