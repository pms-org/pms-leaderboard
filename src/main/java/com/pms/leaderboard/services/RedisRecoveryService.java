package com.pms.leaderboard.services;

import java.time.Instant;
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

    @Autowired
    RedisScoreService redisScoreService;

    boolean redisDown = false;

    private final String zkey = "leaderboard:global:daily";

    @Scheduled(fixedDelay = 3000)
    public void heartbeat() {
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                connection.ping();
                return null;
            });

            if (redisDown) {
                log.warn(" Redis is back! Rebuilding leaderboard zset…");
                rebuild();
                redisDown = false;
            }

        } catch (Exception ex) {
            if (!redisDown) {
                log.error(" Redis connection lost!");
            }
            redisDown = true;
        }
    }

    public boolean isRedisHealthy() {
        return !redisDown;
    }

    public void markRedisDown() {
        redisDown = true;
    }

    private void rebuild() {
        try {
            ZSetOperations<String, String> zset = redis.opsForZSet();
            redis.delete(zkey);

            List<Leaderboard> all = currentRepo.findAll();
            Instant now = Instant.now();

            for (Leaderboard lb : all) {
                if (lb.getPortfolioScore() == null)
                    continue;

                double composite = redisScoreService.compositeScore(
                        lb.getPortfolioScore(),
                        now,
                        lb.getPortfolioId());

                zset.add(zkey, lb.getPortfolioId().toString(), composite);
            }

            log.info("Redis leaderboard zset rebuilt, {} entries", all.size());

        } catch (Exception ex) {
            log.error("Failed rebuilding Redis ZSET", ex);
        }
    }

}
