package com.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> dataSeeder(
            AppDbContext dbContext,
            TasksSeedData tasksSeedData) {
        return event -> {
            try {
                // Database migrations are handled by Flyway or Liquibase
                // Seed data
                tasksSeedData.seedAsync(dbContext, logger);
                logger.info("Database seeding completed successfully");
            } catch (Exception e) {
                logger.error("Error during database seeding", e);
            }
        };
    }
}