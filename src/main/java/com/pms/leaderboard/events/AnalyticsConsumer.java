package com.pms.leaderboard.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.config.EventBuffer;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;
import com.pms.leaderboard.services.RedisHealth;
import com.pms.proto.analytics.RiskEvent;

@Service
public class AnalyticsConsumer {

    private static final Logger log
            = LoggerFactory.getLogger(AnalyticsConsumer.class);

    @Value("${app.kafka.risk-topic}")
    private String riskTopicName;

    @Autowired
    EventBuffer eventBuffer;

    @Autowired
    private RedisHealth redisHealth;

    @Autowired
    LeaderboardService leaderboardService;

    @KafkaListener(topics = "${app.kafka.risk-topic}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<RiskEvent> events) {

        if (events == null || events.isEmpty()) {
            log.warn("Kafka batch EMPTY");
            return;
        }

        if (!redisHealth.isAvailable()) {

            log.error("🚨 Redis DOWN — rejecting Kafka batch");
            throw new RuntimeException("Redis unavailable — stop polling");
        }

        log.info("Kafka batch received size={}", events.size());
        log.info("Kafka received at {}", System.currentTimeMillis());
        System.out.println("Kafka received at " + System.currentTimeMillis());
        System.out.println("Kafka batch received size={}" + events.size());

        events.forEach(e
                -> log.debug(
                        "Kafka RiskEvent pid={} sharpe={} sortino={} avgReturn={}",
                        e.getPortfolioId(),
                        e.getSharpeRatio(),
                        e.getSortinoRatio(),
                        e.getAvgRateOfReturn()
                )
        );

        List<MessageDTO> dtos = new ArrayList<>();

        for (RiskEvent e : events) {

            UUID pid;

            try {
                pid = UUID.fromString(e.getPortfolioId());
            } catch (IllegalArgumentException ex) {
                log.error("Invalid UUID format portfolioId={}", e.getPortfolioId());
                continue;
            }
            MessageDTO dto = new MessageDTO(
                    pid,
                    BigDecimal.valueOf(e.getSharpeRatio()),
                    BigDecimal.valueOf(e.getSortinoRatio()),
                    BigDecimal.valueOf(e.getAvgRateOfReturn()),
                    LocalDateTime.now()
            );
            dtos.add(dto);
        }

        eventBuffer.addAll(dtos);

    }

    private MessageDTO toDto(RiskEvent e) {

        log.debug(
                "Mapping RiskEvent → DTO pid={} sharpe={} sortino={} avgReturn={}",
                e.getPortfolioId(),
                e.getSharpeRatio(),
                e.getSortinoRatio(),
                e.getAvgRateOfReturn()
        );

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


//test commit
