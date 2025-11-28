package com.pms.leaderboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.MessageDTO;

import tools.jackson.databind.ObjectMapper;

@Service
public class AnalyticsProducer {
    
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper mapper;

    @Scheduled(fixedRate = 2000)
    public String sendMessage() throws Exception {
        MessageDTO content = new MessageDTO("P123");
        kafkaTemplate.send("test-topic", mapper.writeValueAsString(content));
        return "Message sent";
    }
}
