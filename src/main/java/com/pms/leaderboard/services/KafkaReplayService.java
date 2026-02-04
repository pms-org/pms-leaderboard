package com.pms.leaderboard.services;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
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

        log.warn("⚡ RESTARTING KAFKA CONSUMERS FROM EARLIEST");

        registry.getListenerContainers()
                .forEach(container -> {

                    container.getContainerProperties()
                            .setAckMode(ContainerProperties.AckMode.BATCH);

                    container.start();
                });
    }
}

// @Service
// public class KafkaReplayService {
//     private static final Logger log = LoggerFactory.getLogger(KafkaReplayService.class);
//     @Autowired
//     private KafkaListenerEndpointRegistry registry;
//     public void replay() {
//         log.info("⚡ STOPPING KAFKA CONSUMERS");
//         registry.getListenerContainers()
//                 .forEach(container -> {
//                     try {
//                         container.stop();
//                         container.getContainerProperties()
//                                  .setAckMode(ContainerProperties.AckMode.MANUAL);
//                         container.start();
//                     } catch (Exception e) {
//                         log.error("Error restarting container: {}", e.getMessage(), e);
//                     }
//                 });
//         log.info("⚡ SEEKING TO EARLIEST (attempting reflective call if supported)");
//         for (MessageListenerContainer container : registry.getListenerContainers()) {
//             try {
//                 // seekToBeginning is not part of the MessageListenerContainer interface in all versions.
//                 // Attempt reflective invocation if the container exposes it (some Kafka containers do).
//                 Method m = container.getClass().getMethod("seekToBeginning");
//                 if (m != null) {
//                     try {
//                         m.invoke(container);
//                         log.info("Invoked seekToBeginning on {}", container.getClass().getName());
//                     } catch (Exception ex) {
//                         log.warn("seekToBeginning invocation failed on {}: {}", container.getClass().getName(), ex.getMessage());
//                     }
//                 }
//             } catch (NoSuchMethodException nsme) {
//                 // container doesn't support seekToBeginning — ignore
//                 log.debug("Container {} does not support seekToBeginning", container.getClass().getName());
//             }
//         }
//     }
// }
