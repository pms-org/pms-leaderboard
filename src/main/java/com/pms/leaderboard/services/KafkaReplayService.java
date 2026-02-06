package com.pms.leaderboard.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.stereotype.Service;

@Service
public class KafkaReplayService {

    private static final Logger log
            = LoggerFactory.getLogger(KafkaReplayService.class);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    public void stopConsumers() {
        registry.getListenerContainers()
                .forEach(container -> {

                    if (container.isRunning()) {
                        container.stop();
                        log.warn("Kafka container stopped");
                    }

                });
    }

    public void replay() {

        log.warn(" RESTARTING KAFKA CONSUMERS FROM EARLIEST");
        registry.getListenerContainers()
                .forEach(container -> {

                    container.getContainerProperties()
                            .setAckMode(ContainerProperties.AckMode.BATCH);

                    container.start();
                });
    }
}
