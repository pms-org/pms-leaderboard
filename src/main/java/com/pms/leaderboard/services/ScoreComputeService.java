package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.MessageDTO;

@Service
public class ScoreComputeService {

    public BigDecimal computeScore(MessageDTO e) {
        return e.getAvgRateOfReturn().multiply(BigDecimal.valueOf(100))
                .add(e.getSharpeRatio().multiply(BigDecimal.valueOf(50)))
                .add(e.getSortinoRatio().multiply(BigDecimal.valueOf(10)));
    }

    public double compositeRedisScore(BigDecimal score, Instant time, UUID pid) {
        double base = score.doubleValue();
        long ts = (time != null) ? time.toEpochMilli() : Instant.now().toEpochMilli();
        double stampFraction = (ts % 1000) / 1e9;
        int idHash = Math.abs(pid.hashCode() % 1000);
        return base + stampFraction + (idHash / 1e12);
    }
    
}
