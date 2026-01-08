package com.pms.leaderboard.config;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

@Component
public class SchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchemaInitializer.class);

    @Value("${spring.kafka.properties.schema.registry.url:http://schema-registry:8081}")
    private String schemaRegistryUrl;

    @PostConstruct
    public void initializeSchemas() {
        logger.info("🔄 SchemaInitializer starting - Registry URL: {}", schemaRegistryUrl);
        
        // Wait for Schema Registry to be fully ready (health check + startup time)
        int maxRetries = 10;
        int delayMs = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("📋 Attempting to register schema (attempt {}/{})", attempt, maxRetries);
                
                if (attempt > 1) {
                    // Wait before retrying (skip on first attempt)
                    Thread.sleep(delayMs);
                }
                
                try (SchemaRegistryClient client = new CachedSchemaRegistryClient(
                        Collections.singletonList(schemaRegistryUrl),
                        100
                )) {
                    String subject = "portfolio-risk-metrics-value";
                    String protobufSchema = """
                            syntax = "proto3";
                            package pms.analytics;
                            
                            option java_package = "com.pms.proto.analytics";
                            option java_outer_classname = "RiskEventProto";
                            option java_multiple_files = true;
                            
                            message RiskEvent {
                              string portfolio_id = 1;
                              double avg_rate_of_return = 2;
                              double sharpe_ratio = 3;
                              double sortino_ratio = 4;
                            }
                            """;

                    try {
                        // Check if schema already exists
                        int schemaId = client.getLatestSchemaMetadata(subject).getId();
                        logger.info("✅ Schema already registered with ID: {}", schemaId);
                        return; // Success - exit
                    } catch (RestClientException e) {
                        if (e.getErrorCode() == 40401) {
                            // Schema not found, register it
                            ProtobufSchema schema = new ProtobufSchema(protobufSchema);
                            int schemaId = client.register(subject, schema);
                            logger.info("✅ Schema registered successfully with ID: {}", schemaId);
                            return; // Success - exit
                        } else {
                            logger.error("❌ Schema Registry error code {}: {}", e.getErrorCode(), e.getMessage());
                            throw e;
                        }
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("⚠️ Schema initialization interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | RestClientException e) {
                if (attempt < maxRetries) {
                    logger.warn("⚠️ Attempt {}/{} failed: {}. Retrying in {}ms...", 
                            attempt, maxRetries, e.getMessage(), delayMs);
                } else {
                    logger.error("❌ Schema initialization failed after {} attempts: {}", 
                            maxRetries, e.getMessage());
                }
            } catch (Exception e) {
                logger.error("❌ Unexpected error during schema initialization (attempt {}/{})", 
                        attempt, maxRetries, e);
                if (attempt >= maxRetries) {
                    break;
                }
            }
        }
        
        logger.warn("⚠️ Schema may not be registered. Relying on producer auto-registration.");
    }
}
