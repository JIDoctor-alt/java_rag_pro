package com.ragpro.superagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 超级智能体 API")
                        .description("Spring AI + RAG + Tool Calling + MCP")
                        .version("1.0.0")
                        .contact(new Contact().name("RAG Pro")));
    }
}
