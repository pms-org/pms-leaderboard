package com.pms.leaderboard.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import com.pms.leaderboard.events.RedisDownEvent;
import com.pms.leaderboard.events.RedisUpEvent;

@Service
public class RedisRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RedisRecoveryService.class);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaReplayService kafkaReplayService;

    @EventListener
    public void onRedisDown(RedisDownEvent ev) {
        log.warn(" ⚠️⚠️⚠️ Redis reported DOWN — stopping Kafka listeners to avoid processing while infra is degraded");
        System.out.println(" ⚠️⚠️⚠️ Redis reported DOWN — stopping Kafka listeners to avoid processing while infra is degraded");
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            try {
                container.stop();
            } catch (Exception e) {
                log.error("Failed to stop listener container", e);
            }
        }
    }

    @EventListener
    public void onRedisUp(RedisUpEvent ev) {
        log.info(" 👍👍👍 Redis reported UP — triggering Kafka replay and resuming consumption");
        System.out.println(" 👍👍👍 Redis reported UP — triggering Kafka replay and resuming consumption");
        try {
            kafkaReplayService.replay();
        } catch (Exception e) {
            log.error("Kafka replay failed", e);
        }
    }
}
