package com.pms.leaderboard.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.proto.RiskEvent;

@Service
public class AnalyticsProducer {
    
    @Autowired
    KafkaTemplate<String, RiskEvent> riskEventKafkaTemplate;

    private final UUID[] portfolioIds = {
        UUID.fromString("b3a1f250-0d4f-4b53-b0c9-651be64225f9"),
        UUID.fromString("c2b9f250-1a1d-4b53-c0c9-1111e64225f8"),
        UUID.fromString("a1b2c3d4-9876-4321-baaa-998877665544"),
        UUID.fromString("55bb22cc-1234-4a53-b0c9-222266442299"),
        UUID.fromString("66cc33dd-5678-4c53-c0c9-333377553311"),
        UUID.fromString("77dd44ee-1111-4d53-a0c9-444488664422"),
        UUID.fromString("88ee55ff-2222-4e53-b0c9-555599775533"),
        UUID.fromString("99ff66aa-3333-4f53-c0c9-666600886644"),

        UUID.fromString("wa51f250-0d4f-4b53-b0c9-651be64225f9"),
        UUID.fromString("v0b9f250-1a1d-4b53-c0c9-1111e64225f8"),
        UUID.fromString("x2b2c3d4-9876-4321-baaa-998877665544"),
        UUID.fromString("z7bb22cc-1234-4a53-b0c9-222266442299"),
        UUID.fromString("r4cc33dd-5678-4c53-c0c9-333377553311"),
        UUID.fromString("k77d44ee-1111-4d53-a0c9-444488664472"),
        UUID.fromString("p8ee55ff-2222-4e53-b0c9-555599775533"),
        UUID.fromString("g59f66aa-3333-4f53-c0c9-666600886644"),

        UUID.fromString("e331f250-0d4f-4b53-b0c9-651be64225f9"),
        UUID.fromString("m5b9f250-1a1d-4b53-c0c9-1111e64225f8"),
        UUID.fromString("l32b23d4-9876-4321-baaa-998877665544"),
        UUID.fromString("o7bb22cc-1234-4a53-b0c9-222266442299"),
        UUID.fromString("s4cc33dd-5678-4c53-c0c9-333377553311"),
        UUID.fromString("e77dd44e-1111-4d53-a0c9-444488664422"),
        UUID.fromString("h8ee55ff-2222-4e53-b0c9-555599775533"),
        UUID.fromString("q59ff66a-3333-4f53-c0c9-666600886644"),

        UUID.fromString("bna1f250-0d4f-4b53-b0c9-651be64225f9"),
        UUID.fromString("f4b9f250-1a1d-4b53-c0c9-1111e64225f8"),
        UUID.fromString("y7b2c3d4-9876-4321-baaa-998877665544"),
        UUID.fromString("r7bb22cc-1234-4a53-b0c9-222266442299"),
        UUID.fromString("l4cc33dd-5678-4c53-c0c9-333377553311"),
        UUID.fromString("z77dd44e-1111-4d53-a0c9-444488664422"),
        UUID.fromString("ll8e55ff-2222-4e53-b0c9-555599775533"),
        UUID.fromString("tt9f66aa-3333-4f53-c0c9-666600886644")
    };

    private int index = 0;

    @Scheduled(fixedRate = 200)
    public void sendMessage() throws Exception {

        UUID pid = portfolioIds[index % portfolioIds.length];
        index++;

        BigDecimal sharpe  = random(1.0, 5.0);
        BigDecimal sortino = random(0.1, 3.0);
        BigDecimal avgRor  = random(0.1, 1.5);

        // Build Protobuf event
        RiskEvent event = RiskEvent.newBuilder()
                .setPortfolioId(pid.toString())                // string UUID
                .setSharpeRatio(sharpe.doubleValue())
                .setSortinoRatio(sortino.doubleValue())
                .setAvgRateOfReturn(avgRor.doubleValue())
                .build();

        // riskEventKafkaTemplate.send("portfolio-metrics", pid.toString(), mapper.writeValueAsString(event));
        riskEventKafkaTemplate.send("portfolio-metrics", pid.toString(), event);
        System.out.println("Sent Protobuf RiskEvent: " + event);
    }

    private BigDecimal random(double min, double max) {
        return BigDecimal.valueOf(min + Math.random() * (max - min))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}
