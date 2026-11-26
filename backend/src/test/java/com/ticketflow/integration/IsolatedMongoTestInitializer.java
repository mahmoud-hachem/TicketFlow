package com.ticketflow.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;

public class IsolatedMongoTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        IsolatedMongoSettings settings = IsolatedMongoSettings.require(System.getenv(),
                Boolean.getBoolean("ticketflow.integration.enabled"));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolatedMongoOnly", Map.of(
                "spring.mongodb.uri", settings.uri(), "spring.mongodb.database", settings.database(),
                "ticketflow.demo-seeding.enabled", "false", "logging.level.org.mongodb.driver", "OFF")));
    }
}
