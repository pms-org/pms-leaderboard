package com.pms.leaderboard.events;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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

    @KafkaListener(
        topics = "portfolio-metrics",
        groupId = "leaderboard-group",
        containerFactory = "batchKafkaListenerContainerFactory"
)
public void consume(List<String> messages) {
    List<MessageDTO> list = messages.stream()
            .map(m -> mapper.readValue(m, MessageDTO.class))
            .toList();

    leaderboardService.processBatch(list);
}

}
