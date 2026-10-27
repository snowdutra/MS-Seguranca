package br.com.msav02.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI apiDocs() {
        return new OpenAPI()
                .info(new Info().title("MS-AV-02 - Seguranca e Observabilidade")
                        .version("1.0.0")
                        .description("Endpoints de login, usuarios e desbloqueio. ")
                )
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("basic").description("Autenticacao HTTP Basic com roles JDBC")));
    }
}
