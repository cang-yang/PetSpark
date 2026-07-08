package com.petspark;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。{@code @MapperScan} 只注册明确标注 {@link Mapper} 的 MyBatis DAO，
 * 避免把 common 层端口接口（例如 OutboxRepository）误注册成 Mapper Bean。
 * mapper XML 由 {@code application.yml} 的 {@code mybatis-plus.mapper-locations} 扫描。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.petspark", annotationClass = Mapper.class)
public class PetSparkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetSparkApplication.class, args);
    }
}
