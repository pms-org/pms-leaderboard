package com.pms.leaderboard.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

//     @Scheduled(fixedRate = 2000)
//     public String sendMessage() throws Exception {
//     MessageDTO event = new MessageDTO(
//     UUID.fromString("b3a1f250-0d4f-4b53-b0c9-651be64225f9"),
//     new BigDecimal("1.85"),
//     new BigDecimal("2.40"),
//     new BigDecimal("0.17"),
//     LocalDateTime.now()
// );
//     kafkaTemplate.send("portfolio-metrics", mapper.writeValueAsString(event));
//         return "Message sent";
//     }


private final UUID[] portfolioIds = {
        UUID.fromString("b3a1f250-0d4f-4b53-b0c9-651be64225f9"),
        UUID.fromString("c2b9f250-1a1d-4b53-c0c9-1111e64225f8"),
        UUID.fromString("a1b2c3d4-9876-4321-baaa-998877665544")
    };

    private int index = 0;

    @Scheduled(fixedRate = 2000)
    public void sendMessage() throws Exception {

        UUID pid = portfolioIds[index % portfolioIds.length];
        index++;

        MessageDTO event = new MessageDTO(
            pid,
            random(1.0, 5.0),   // return rate
            random(0.1, 3.0),   // sharpe
            random(0.1, 1.5),   // sortino
            LocalDateTime.now()
        );

        kafkaTemplate.send("portfolio-metrics", mapper.writeValueAsString(event));
        System.out.println("Sent Kafka Event: " + event);
    }

    private BigDecimal random(double min, double max) {
        return BigDecimal.valueOf(min + Math.random() * (max - min))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}
