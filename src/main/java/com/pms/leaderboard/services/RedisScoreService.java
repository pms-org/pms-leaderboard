package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class RedisScoreService {

    public double compositeScore(
            BigDecimal baseScore,
            Instant eventTime,
            UUID portfolioId
    ) {
        double base = baseScore.doubleValue();
        long ts = eventTime.toEpochMilli();
        double stampFraction = (ts % 1000) / 1e9;
        int idHash = Math.abs(portfolioId.hashCode() % 1000);
        double hashFrac = idHash / 1e12;
        return base + stampFraction + hashFrac;
    }

}
