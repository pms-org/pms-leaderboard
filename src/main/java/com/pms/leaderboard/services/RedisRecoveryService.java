package com.pms.leaderboard.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.entities.Leaderboard;
import com.pms.leaderboard.repositories.LeaderboardRepository;


@Service
public class RedisRecoveryService {

    private final Logger log = LoggerFactory.getLogger(RedisRecoveryService.class);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LeaderboardRepository currentRepo;

    private boolean redisWasDown = false;

    private final String zkey = "leaderboard:global:daily";

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        try {
            redis.opsForValue().get("ping"); // heartbeat check

            if (redisWasDown) {
                log.warn("🔄 Redis is back! Rebuilding leaderboard zset…");
                rebuild();
                redisWasDown = false;
            }

        } catch (Exception ex) {
            if (!redisWasDown) {
                log.error("❌ Redis connection lost!");
            }
            redisWasDown = true;
        }
    }

    public void markRedisDown() {
        redisWasDown = true;
    }

    private void rebuild() {
        try {
            ZSetOperations<String, String> zset = redis.opsForZSet();
            redis.delete(zkey);

            List<Leaderboard> all = currentRepo.findAll();
            for (Leaderboard lb : all) {
                double composite = lb.getPortfolioScore().doubleValue();
                zset.add(zkey, lb.getPortfolioId().toString(), composite);
            }

            log.info("✅ Redis leaderboard zset rebuilt, {} entries", all.size());

        } catch (Exception ex) {
            log.error("❌ Failed rebuilding Redis ZSET", ex);
        }
    }
}

