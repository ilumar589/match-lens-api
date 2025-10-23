package org.jstats.matchlens_api.modules.football_data_org_gatherer.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("MatchLens Ingestion API")
                        .description("Endpoints to ingest football data from external sources.")
                        .version("v1")
                        .contact(new Contact().name("MatchLens").url("https://yourdomain.example"))
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project README")
                        .url("https://yourrepo.example"));
    }
}
