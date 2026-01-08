package com.pms.leaderboard.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaTopicConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaAdmin kafkaAdmin;
    private NewTopic riskTopicBean;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        log.info("✅ KafkaAdmin bean created with bootstrap servers: {}", bootstrapServers);
        this.kafkaAdmin = new KafkaAdmin(configs);
        return this.kafkaAdmin;
    }

    @Bean
    public NewTopic riskTopic() {
        log.info("✅ NewTopic bean definition - portfolio-risk-metrics with 8 partitions and 1 replica");
        this.riskTopicBean = TopicBuilder.name("portfolio-risk-metrics")
                .partitions(8)
                .replicas(1)
                .build();
        return this.riskTopicBean;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("✅ Application started - Kafka topic configuration event triggered");
        if (kafkaAdmin != null && riskTopicBean != null) {
            try {
                // Create the topic with specified partitions
                kafkaAdmin.createOrModifyTopics(riskTopicBean);
                log.info("✅ Topic creation initiated for portfolio-risk-metrics with 8 partitions");
            } catch (Exception e) {
                log.warn("⚠️ Error creating topic (may already exist): {}", e.getMessage());
            }
        } else {
            log.warn("⚠️ KafkaAdmin or riskTopicBean not initialized");
        }
    }
}
