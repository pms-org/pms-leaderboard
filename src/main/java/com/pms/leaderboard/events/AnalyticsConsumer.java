package com.pms.leaderboard.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;
import com.pms.proto.analytics.RiskEvent;

@Service
public class AnalyticsConsumer {

    private static final Logger log =
        LoggerFactory.getLogger(AnalyticsConsumer.class);


    @Value("${app.kafka.risk-topic}")
    private String riskTopicName;

    @Autowired
    WebSocketHandler handler;

    @Autowired
    LeaderboardService leaderboardService;

    @KafkaListener(topics = "${app.kafka.risk-topic}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<RiskEvent> events) {

        if (events == null || events.isEmpty()) {
            log.warn("Kafka batch EMPTY");
            return;
        }

        log.info("Kafka batch received size={}", events.size());

        events.forEach(e ->
            log.debug(
                "Kafka RiskEvent pid={} sharpe={} sortino={} avgReturn={}",
                e.getPortfolioId(),
                e.getSharpeRatio(),
                e.getSortinoRatio(),
                e.getAvgRateOfReturn()
            )
        );

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

        log.debug(
        "Mapping RiskEvent → DTO pid={} sharpe={} sortino={} avgReturn={}",
        e.getPortfolioId(),
        e.getSharpeRatio(),
        e.getSortinoRatio(),
        e.getAvgRateOfReturn()
    );
        // UUID pid = UUID.fromString(e.getPortfolioId());

        MessageDTO dto = new MessageDTO(
            UUID.fromString(e.getPortfolioId()),
            BigDecimal.valueOf(e.getSharpeRatio()),
            BigDecimal.valueOf(e.getSortinoRatio()),
            BigDecimal.valueOf(e.getAvgRateOfReturn()),
            LocalDateTime.now()
        );

        log.debug("DTO created {}", dto);
        return dto;
    }

}
