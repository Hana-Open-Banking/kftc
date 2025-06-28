package com.kftc.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value(value = "${swagger.server-url}")
    private String serverUrl;

    private final String JWT_TOKEN_HEADER = "BearerAuth";

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public OpenAPI springOpenAPI() {
        Info info = new Info()
                .title("KFTC 오픈뱅킹 OAuth API")
                .description("금융결제원 오픈뱅킹 OAuth 2.0 인증 서버 API")
                .version("v1.0.0");

        List<Server> servers = List.of(new Server().url(serverUrl));

        SecurityScheme bearerAuth = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER);

        Components components = new Components()
                .addSecuritySchemes(JWT_TOKEN_HEADER, bearerAuth);

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(JWT_TOKEN_HEADER);

        return new OpenAPI()
                .info(info)
                .servers(servers)
                .components(components)
                .addSecurityItem(securityRequirement);
    }

    @Bean
    public InternalResourceViewResolver defaultViewResolver() {
        return new InternalResourceViewResolver();
    }

}