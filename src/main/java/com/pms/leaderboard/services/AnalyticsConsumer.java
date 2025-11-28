package com.pms.leaderboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;

@Service
public class AnalyticsConsumer {
    
    @Autowired
    WebSocketHandler handler;

    @KafkaListener(topics = "test-topic", groupId = "group1")
    public void consume(String message) throws Exception {
        System.out.println("Received from Kafka: " + message);

        handler.broadcast(message);
    }
}
