package com.pms.leaderboard.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;         
import org.springframework.stereotype.Component;

import com.pms.leaderboard.config.EventBuffer;
import com.pms.leaderboard.events.RedisDownEvent;
import com.pms.leaderboard.events.RedisUpEvent;


@Component
public class RedisRecoveryOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(RedisRecoveryOrchestrator.class);

    @Autowired
    private EventBuffer buffer;

    @Autowired
    private KafkaReplayService replayService;

    // REDIS DOWN
    @EventListener
    public void onRedisDown(RedisDownEvent ev) {

        log.error(" Redis DOWN — stopping Kafka consumers");

        replayService.stopConsumers();
    }

    // REDIS UP
    @EventListener
    public void onRedisUp(RedisUpEvent ev) {

        log.info(" Redis UP — waiting for buffer drain...");

        while (!buffer.isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }

        log.info(" Buffer drained — starting replay");

        replayService.replay();
    }
}
