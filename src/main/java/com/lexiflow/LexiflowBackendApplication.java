package com.lexiflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.lexiflow.**.mapper")
@EnableScheduling
public class LexiflowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LexiflowBackendApplication.class, args);
    }

}
