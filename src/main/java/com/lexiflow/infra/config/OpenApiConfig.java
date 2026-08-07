package com.lexiflow.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 文档配置类。
 * <p>
 * 配置 LexiFlow API 文档的基本信息，包括标题、描述和版本号。
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /** 创建 OpenAPI 文档实例。 */
    @Bean
    public OpenAPI lexiflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LexiFlow API")
                        .description("LexiFlow AI 背单词平台接口文档")
                        .version("0.1.0"));
    }
}
