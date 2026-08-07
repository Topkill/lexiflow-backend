package com.lexiflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * LexiFlow 后端应用启动类。
 * <p>启用配置属性扫描、MyBatis Mapper 扫描和定时任务调度。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.lexiflow.**.mapper")
@EnableScheduling
public class LexiflowBackendApplication {

    /** 应用入口方法。 */
    public static void main(String[] args) {
        SpringApplication.run(LexiflowBackendApplication.class, args);
    }

}
