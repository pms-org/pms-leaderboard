package com.pms.leaderboard.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;
import com.pms.leaderboard.proto.RiskEvent;

@Service
public class AnalyticsConsumer {

    @Autowired
    WebSocketHandler handler;

    @Autowired
    LeaderboardService leaderboardService;

    @KafkaListener(topics = "portfolio-metrics", groupId = "leaderboard-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<RiskEvent> events) {

        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            List<MessageDTO> dtos = events.stream()
                    .map(this::toDto)
                    .toList();

            leaderboardService.processBatch(dtos);

        } catch (Exception e) {
            throw e;
        }
    }

    private MessageDTO toDto(RiskEvent e) {
        UUID pid = UUID.fromString(e.getPortfolioId());

        return new MessageDTO(
                pid,
                BigDecimal.valueOf(e.getSharpeRatio()),
                BigDecimal.valueOf(e.getSortinoRatio()),
                BigDecimal.valueOf(e.getAvgRateOfReturn()),
                LocalDateTime.now());
    }

}
