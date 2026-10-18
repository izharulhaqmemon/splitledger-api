package com.izhar.splitledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI splitLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SplitLedger API")
                .version("1.0.0")
                .description("Group expense splitting with exact-to-the-paisa math, idempotent writes, "
                        + "an append-only ledger and a smart settle-up planner."));
    }
}
