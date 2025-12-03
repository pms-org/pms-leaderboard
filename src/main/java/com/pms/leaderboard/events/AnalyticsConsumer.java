package com.pms.leaderboard.events;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;

import tools.jackson.databind.ObjectMapper;

@Service
public class AnalyticsConsumer {
    
    @Autowired
    WebSocketHandler handler;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    LeaderboardService leaderboardService;

    @KafkaListener(topics = "portfolio-metrics", groupId = "leaderboard-group")
    public void listen(String payload) {
        try {
            MessageDTO message = mapper.readValue(payload, MessageDTO.class);
            System.out.println("Received: " + message);
            leaderboardService.enqueue(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
